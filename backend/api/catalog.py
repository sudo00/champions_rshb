"""Reviewed canonical catalogue, shared with the visual/text retrieval index."""
from __future__ import annotations

from functools import lru_cache
import hashlib
import json
import os
from pathlib import Path

import psycopg
from psycopg.types.json import Json, Jsonb

from api.config import DATABASE_URL, s3_public_url
from api.contracts import RegionDto, StyleDto, VarietyDto, WineDto, WineryDto

ROOT = Path(__file__).resolve().parents[2]
IMAGE_ROOT = Path(os.environ.get("WINE_CATALOG_IMAGES", str(ROOT/"data/catalog/curated/images")))


def catalog_path() -> Path:
    candidates: list[Path] = []
    env_path = os.environ.get("WINE_CATALOG_PATH")
    if env_path:
        configured = Path(env_path)
        candidates.append(configured)
        if configured.suffix == ".json":
            candidates.append(configured.with_suffix(".jsonl"))
        elif configured.suffix == ".jsonl":
            candidates.append(configured.with_suffix(".json"))
    catalog_dir = ROOT / "backend" / "catalog"
    candidates.extend([catalog_dir / "catalog.jsonl", catalog_dir / "catalog.json"])
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    return candidates[0] if candidates else catalog_dir / "catalog.jsonl"


CATALOG_PATH = catalog_path()


def load_catalog_rows(raw: bytes) -> list[dict]:
    text = raw.decode()
    try:
        parsed = json.loads(text)
    except json.JSONDecodeError:
        parsed = None
    if isinstance(parsed, list):
        return parsed
    if isinstance(parsed, dict):
        for key in ("items", "wines", "cards"):
            if isinstance(parsed.get(key), list):
                return parsed[key]
        if parsed.get("slug"):
            return [parsed]
    return [json.loads(line) for line in text.splitlines() if line.strip()]


@lru_cache(maxsize=1)
def catalog_data() -> tuple[dict[str, dict], str]:
    path = catalog_path()
    raw = path.read_bytes()
    checksum = hashlib.sha256(raw).hexdigest()
    manifest = path.with_name("manifest.json")
    if path.suffix == ".jsonl" and manifest.exists() and json.loads(manifest.read_text())["catalog_sha256"] != checksum:
        raise RuntimeError("Catalogue checksum mismatch")
    rows = load_catalog_rows(raw)
    cards = {r["slug"]: r for r in rows}
    if not cards or len(cards) != len(rows):
        raise RuntimeError("Empty catalogue or duplicate slug")
    return cards, checksum


def image_path(slug: str) -> Path | None:
    raw = catalog_data()[0].get(slug)
    if raw is None or not raw.get("reference_path"):
        return None
    path = IMAGE_ROOT/Path(raw["reference_path"]).name
    return path if path.is_file() else None


def wine_from_file(wine_id: str) -> WineDto | None:
    raw = catalog_data()[0].get(wine_id)
    if raw is None:
        return None
    return WineDto(
        id=raw["slug"], slug=raw["slug"], name=raw["title"], vintage=None,
        rating=None, reviewsCount=None, price=None, currency=None,
        region=RegionDto(name=raw["region"]) if raw.get("region") else None,
        country=None, variety=VarietyDto(name=raw["grapes"]) if raw.get("grapes") else None,
        style=StyleDto(name=raw["category"]) if raw.get("category") else None,
        alcoholPercentage=None, imageUrl=f'/v1/wines/{raw["slug"]}/image' if image_path(wine_id) else None,
        description=raw.get("description"), foodPairing=[],
        winery=WineryDto(name=raw["winery"]) if raw.get("winery") else None,
        category=raw.get("category"), grapes=raw.get("grapes"), colorShade=raw.get("color_shade"),
    )


def wine_from_row(row: tuple) -> WineDto:
    payload = dict(row[2] or {})
    payload.setdefault("id", row[0])
    payload.setdefault("slug", payload.get("id") or row[0])
    payload.setdefault("name", row[1])
    wine = WineDto.model_validate(payload)
    if wine.imageUrl:
        return wine
    if row[3]:
        return wine.model_copy(update={"imageUrl": s3_public_url(row[3])})
    if image_path(wine.id):
        return wine.model_copy(update={"imageUrl": f"/v1/wines/{wine.id}/image"})
    return wine


def get_wine(wine_id: str) -> WineDto | None:
    found = _get_from_db(wine_id)
    if found is not None:
        return found
    return wine_from_file(wine_id)


def search_wines(query: str, page: int, per_page: int) -> tuple[list[WineDto], int, bool]:
    page = max(page, 1)
    per_page = max(per_page, 1)
    from_db = _search_db(query, page, per_page)
    if from_db is not None:
        return from_db
    return _search_file(query, page, per_page)


def _search_file(query: str, page: int, per_page: int) -> tuple[list[WineDto], int, bool]:
    needle = query.strip().casefold()
    matched = [r["slug"] for r in catalog_data()[0].values() if not needle or needle in " ".join(
        str(r.get(k) or "") for k in ("slug", "title", "winery", "region", "grapes", "category")).casefold()]
    start = (page - 1) * per_page
    wines = [wine for slug in matched[start:start + per_page] if (wine := wine_from_file(slug)) is not None]
    return wines, len(matched), start + per_page < len(matched)


def _escape_like(value: str) -> str:
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")


def _search_db(query: str, page: int, per_page: int) -> tuple[list[WineDto], int, bool] | None:
    needle = query.strip()
    where = ""
    params: list = []
    if needle:
        pattern = f"%{_escape_like(needle)}%"
        where = """
            WHERE id ILIKE %s ESCAPE '\\'
               OR name ILIKE %s ESCAPE '\\'
               OR COALESCE(payload->>'slug', '') ILIKE %s ESCAPE '\\'
               OR COALESCE(payload->>'description', '') ILIKE %s ESCAPE '\\'
               OR COALESCE(payload->>'category', '') ILIKE %s ESCAPE '\\'
               OR COALESCE(payload->>'grapes', '') ILIKE %s ESCAPE '\\'
               OR COALESCE(payload->'winery'->>'name', '') ILIKE %s ESCAPE '\\'
               OR COALESCE(payload->'region'->>'name', '') ILIKE %s ESCAPE '\\'
        """
        params = [pattern] * 8
    offset = (page - 1) * per_page
    try:
        with psycopg.connect(DATABASE_URL) as conn:
            total_row = conn.execute(f"SELECT COUNT(*) FROM wines {where}", params).fetchone()
            rows = conn.execute(
                f"""
                SELECT id, name, payload, image_key
                FROM wines
                {where}
                ORDER BY name, id
                LIMIT %s OFFSET %s
                """,
                [*params, per_page, offset],
            ).fetchall()
    except Exception:  # noqa: BLE001
        return None
    total = int(total_row[0]) if total_row else 0
    wines = [wine_from_row(row) for row in rows]
    return wines, total, offset + len(wines) < total


def _get_from_db(wine_id: str) -> WineDto | None:
    try:
        with psycopg.connect(DATABASE_URL) as conn:
            row = conn.execute(
                "SELECT id, name, payload, image_key FROM wines WHERE id = %s",
                (wine_id,),
            ).fetchone()
    except Exception:  # noqa: BLE001
        return None
    if row is None:
        return None
    return wine_from_row(row)


def existing_wine_ids(conn: psycopg.Connection | None = None) -> set[str]:
    def _load(db: psycopg.Connection) -> set[str]:
        return {row[0] for row in db.execute("SELECT id FROM wines")}

    if conn is not None:
        return _load(conn)
    with psycopg.connect(DATABASE_URL) as owned:
        return _load(owned)


def insert_wine_if_absent(wine: WineDto, image_key: str | None, conn: psycopg.Connection | None = None) -> bool:
    sql = """
        INSERT INTO wines (id, name, payload, image_key)
        VALUES (%s, %s, %s, %s)
        ON CONFLICT (id) DO NOTHING
        RETURNING id
    """
    params = (wine.id, wine.name, Json(wine.model_dump(mode="json")), image_key)
    if conn is not None:
        return conn.execute(sql, params).fetchone() is not None
    with psycopg.connect(DATABASE_URL) as owned:
        inserted = owned.execute(sql, params).fetchone() is not None
        owned.commit()
        return inserted


def set_image_key_if_absent(wine_id: str, image_key: str) -> None:
    with psycopg.connect(DATABASE_URL) as conn:
        conn.execute(
            "UPDATE wines SET image_key = %s WHERE id = %s AND image_key IS NULL",
            (image_key, wine_id),
        )
        conn.commit()


def seed_catalog_file(conn: psycopg.Connection) -> tuple[int, int]:
    before = conn.execute("SELECT COUNT(*) FROM wines").fetchone()[0]
    rows = [
        (slug, raw["title"], Jsonb(wine_from_file(slug).model_dump()), None)
        for slug, raw in catalog_data()[0].items()
    ]
    with conn.cursor() as cursor:
        cursor.executemany(
            """
            INSERT INTO wines (id, name, payload, image_key)
            VALUES (%s, %s, %s, %s)
            ON CONFLICT (id) DO NOTHING
            """,
            rows,
        )
    after = conn.execute("SELECT COUNT(*) FROM wines").fetchone()[0]
    inserted = int(after) - int(before)
    return inserted, len(rows) - inserted
