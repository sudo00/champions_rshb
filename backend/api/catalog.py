"""Reviewed canonical catalogue, shared with the visual/text retrieval index."""
from __future__ import annotations

from functools import lru_cache
import hashlib
import json
import os
from pathlib import Path

from api.contracts import RegionDto, StyleDto, VarietyDto, WineDto, WineryDto

ROOT = Path(__file__).resolve().parents[2]
CATALOG_PATH = Path(os.environ.get("WINE_CATALOG_PATH", str(ROOT/"backend/catalog/catalog.jsonl")))
IMAGE_ROOT = Path(os.environ.get("WINE_CATALOG_IMAGES", str(ROOT/"data/catalog/curated/images")))


@lru_cache(maxsize=1)
def catalog_data() -> tuple[dict[str, dict], str]:
    raw = CATALOG_PATH.read_bytes()
    checksum = hashlib.sha256(raw).hexdigest()
    manifest = CATALOG_PATH.with_name("manifest.json")
    if manifest.exists() and json.loads(manifest.read_text())["catalog_sha256"] != checksum:
        raise RuntimeError("Catalogue checksum mismatch")
    rows = [json.loads(line) for line in raw.decode().splitlines() if line.strip()]
    cards = {r["slug"]:r for r in rows}
    if not cards or len(cards) != len(rows):
        raise RuntimeError("Empty catalogue or duplicate slug")
    return cards, checksum


def image_path(slug: str) -> Path | None:
    raw = catalog_data()[0].get(slug)
    if raw is None or not raw.get("reference_path"):
        return None
    path = IMAGE_ROOT/Path(raw["reference_path"]).name
    return path if path.is_file() else None


def get_wine(wine_id: str) -> WineDto | None:
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


def search_wines(query: str, page: int, per_page: int) -> tuple[list[WineDto], int, bool]:
    query = query.strip().casefold()
    matched = [r["slug"] for r in catalog_data()[0].values() if not query or query in " ".join(
        str(r.get(k) or "") for k in ("slug", "title", "winery", "region", "grapes", "category")).casefold()]
    start = (page-1)*per_page
    return [get_wine(slug) for slug in matched[start:start+per_page]], len(matched), start+per_page < len(matched)
