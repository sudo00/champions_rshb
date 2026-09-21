"""Download https://vino-svoe.ru catalog into PostgreSQL and MinIO."""

from __future__ import annotations

import argparse
import json
import logging
import mimetypes
import sys
import time
from pathlib import PurePosixPath
from typing import Any, Iterable

import httpx

from api.catalog import existing_wine_ids, insert_wine_if_absent, set_image_key_if_absent
from api.config import s3_public_url
from api.contracts import CountryDto, RegionDto, StyleDto, VarietyDto, WineDto, WineryDto
from api.infra import ensure_bucket, init_infra, minio_client, object_exists, upload_bytes

log = logging.getLogger("import_vino_svoe")

CATALOG_BASE = "https://vino-svoe.ru"
LIST_URL = f"{CATALOG_BASE}/api/wines"
IMAGE_RESIZE_BASE = "https://api.vino-svoe.ru/v1/img/str-api/1160/1160/resize"
USER_AGENT = "Mozilla/5.0 (compatible; champions-rshb-catalog-importer/1.0)"
MAX_PER_PAGE = 20
HTTP_RETRIES = 6
RETRY_STATUSES = {408, 425, 429, 500, 502, 503, 504}
COUNTRY = CountryDto(name="Россия", code="RU")


def map_wine(item: dict[str, Any], detail: dict[str, Any] | None, image_url: str | None) -> WineDto:
    data = {**item, **(detail or {})}
    wine_id = str(data.get("slug") or "").strip()
    if not wine_id:
        raise ValueError("wine without slug")
    grapes = _names(data.get("grapes"))
    variety_name = " / ".join(grapes) if grapes else _text(data.get("variety"))
    category = _text(data.get("category"))
    return WineDto(
        id=wine_id,
        slug=wine_id,
        name=_text(data.get("title")) or wine_id,
        vintage=_vintage(data),
        rating=_optional_float(data.get("publicRating")),
        reviewsCount=int(data.get("reviewsCount") or 0) or None,
        category=category or None,
        grapes=variety_name or None,
        colorShade=_text(data.get("color")) or None,
        price=_optional_float(data.get("price")),
        currency=data.get("currency"),
        region=RegionDto(name=_text(data.get("region")) or "Россия", country=COUNTRY),
        country=COUNTRY,
        variety=VarietyDto(name=variety_name) if variety_name else None,
        style=StyleDto(name=category) if category else None,
        alcoholPercentage=_alcohol(data.get("alcohol")),
        imageUrl=image_url,
        description=_text(data.get("description")) or None,
        foodPairing=_names(data.get("dishes")),
        winery=WineryDto(name=_text(data.get("manufacturer"))) if _text(data.get("manufacturer")) else None,
    )


def image_source_url(item: dict[str, Any], detail: dict[str, Any] | None = None) -> str | None:
    raw = (detail or {}).get("image") or item.get("image") or {}
    path = raw.get("url") if isinstance(raw, dict) else None
    if not path:
        return None
    value = str(path)
    if value.startswith("http://") or value.startswith("https://"):
        return value
    if not value.startswith("/"):
        value = "/" + value
    return IMAGE_RESIZE_BASE + value


def catalog_image_key(wine_id: str, source_url: str) -> str:
    suffix = PurePosixPath(source_url.split("?", 1)[0]).suffix.lower() or ".webp"
    if suffix not in {".webp", ".jpg", ".jpeg", ".png", ".gif"}:
        suffix = ".webp"
    return f"wines/{wine_id}{suffix}"


def content_type_for(key: str) -> str:
    guessed, _ = mimetypes.guess_type(key)
    return guessed or "application/octet-stream"


def iter_list_pages(
    client: httpx.Client,
    *,
    per_page: int,
    start_page: int = 1,
    max_pages: int | None = None,
) -> Iterable[dict[str, Any]]:
    page = start_page
    pages_seen = 0
    while True:
        payload = fetch_list_page(client, page=page, per_page=per_page)
        pages_seen += 1
        yield payload
        total_pages = int(payload.get("totalPages") or page)
        if page >= total_pages:
            break
        if max_pages is not None and pages_seen >= max_pages:
            break
        page += 1


def fetch_list_page(client: httpx.Client, *, page: int, per_page: int) -> dict[str, Any]:
    return _get_json(
        client,
        LIST_URL,
        params={"page": page, "perPage": min(max(per_page, 1), MAX_PER_PAGE)},
    )


def fetch_detail(client: httpx.Client, slug: str) -> dict[str, Any] | None:
    try:
        return _get_json(client, f"{LIST_URL}/{slug}")
    except httpx.HTTPStatusError as exc:
        if exc.response is not None and exc.response.status_code == 404:
            return None
        raise


def download_bytes(client: httpx.Client, url: str) -> bytes:
    return _get_bytes(client, url)


def _get_json(client: httpx.Client, url: str, params: dict[str, Any] | None = None) -> dict[str, Any]:
    last_error: Exception | None = None
    for attempt in range(1, HTTP_RETRIES + 1):
        try:
            response = client.get(url, params=params)
            if response.status_code in RETRY_STATUSES:
                raise httpx.HTTPStatusError(
                    f"{response.status_code} for {url}",
                    request=response.request,
                    response=response,
                )
            if response.status_code == 404:
                response.raise_for_status()
            response.raise_for_status()
            body = response.content.strip()
            if not body or body[:1] not in (b"{", b"["):
                raise ValueError(f"non-json response for {url}: {body[:120]!r}")
            return json.loads(body)
        except httpx.HTTPStatusError as exc:
            if exc.response is not None and exc.response.status_code == 404:
                raise
            last_error = exc
        except Exception as exc:  # noqa: BLE001
            last_error = exc
        log.warning("retry %s/%s %s: %s", attempt, HTTP_RETRIES, url, last_error)
        if attempt < HTTP_RETRIES:
            time.sleep(min(2 ** attempt, 30))
    raise RuntimeError(f"failed {url}: {last_error}")


def _get_bytes(client: httpx.Client, url: str) -> bytes:
    last_error: Exception | None = None
    for attempt in range(1, HTTP_RETRIES + 1):
        try:
            response = client.get(url)
            if response.status_code in RETRY_STATUSES:
                raise httpx.HTTPStatusError(
                    f"{response.status_code} for {url}",
                    request=response.request,
                    response=response,
                )
            response.raise_for_status()
            if not response.content:
                raise ValueError(f"empty image for {url}")
            return response.content
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            log.warning("retry %s/%s %s: %s", attempt, HTTP_RETRIES, url, exc)
            if attempt < HTTP_RETRIES:
                time.sleep(min(2 ** attempt, 30))
    raise RuntimeError(f"failed {url}: {last_error}")


def import_catalog(
    *,
    per_page: int = 20,
    start_page: int = 1,
    max_pages: int | None = None,
    skip_details: bool = False,
    skip_images: bool = False,
    force_images: bool = False,
    skip_existing: bool = True,
    ensure_infra: bool = True,
    client: httpx.Client | None = None,
) -> dict[str, int]:
    if ensure_infra:
        init_infra()
    stats = {"pages": 0, "upserted": 0, "images": 0, "skipped": 0, "failed": 0}
    known = existing_wine_ids()
    owns_client = client is None
    http = client or httpx.Client(
        headers={"User-Agent": USER_AGENT, "Accept": "application/json"},
        timeout=httpx.Timeout(30.0),
        follow_redirects=True,
    )
    s3 = minio_client()
    ensure_bucket(s3)
    try:
        for page_payload in iter_list_pages(
            http,
            per_page=per_page,
            start_page=start_page,
            max_pages=max_pages,
        ):
            stats["pages"] += 1
            items = page_payload.get("items") or []
            log.info(
                "page %s/%s (%s items)",
                page_payload.get("currentPage"),
                page_payload.get("totalPages"),
                len(items),
            )
            for item in items:
                slug = str(item.get("slug") or "").strip()
                if skip_existing and slug in known:
                    stats["skipped"] += 1
                    continue
                try:
                    imported = _import_item(
                        http,
                        s3,
                        item,
                        skip_details=skip_details,
                        skip_images=skip_images,
                        force_images=force_images,
                    )
                    if imported.get("skipped"):
                        stats["skipped"] += 1
                        known.add(slug)
                        continue
                    stats["upserted"] += 1
                    known.add(slug)
                    if imported.get("uploaded_image"):
                        stats["images"] += 1
                except Exception as exc:  # noqa: BLE001
                    stats["failed"] += 1
                    log.warning("skip %s: %s", item.get("slug") or item.get("title"), exc)
    finally:
        if owns_client:
            http.close()
    return stats


def upload_catalog_file_images() -> dict[str, int]:
    from api.catalog import catalog_data, image_path

    stats = {"images": 0, "skipped": 0}
    s3 = minio_client()
    ensure_bucket(s3)
    for slug in catalog_data()[0]:
        path = image_path(slug)
        if path is None:
            stats["skipped"] += 1
            continue
        key = f"wines/{path.name}"
        if object_exists(key, s3):
            stats["skipped"] += 1
        else:
            upload_bytes(key, path.read_bytes(), content_type_for(key), s3)
            stats["images"] += 1
        set_image_key_if_absent(slug, key)
    return stats


def startup_catalog_load() -> dict[str, dict[str, int]]:
    file_images = upload_catalog_file_images()
    remote = import_catalog(ensure_infra=False, skip_existing=True)
    log.info("startup catalog load file_images=%s remote=%s", file_images, remote)
    return {"file_images": file_images, "remote": remote}


def _import_item(
    http: httpx.Client,
    s3,
    item: dict[str, Any],
    *,
    skip_details: bool,
    skip_images: bool,
    force_images: bool,
) -> dict[str, bool]:
    slug = str(item.get("slug") or "").strip()
    if not slug:
        raise ValueError("empty slug")
    detail = None if skip_details else fetch_detail(http, slug)
    source_url = image_source_url(item, detail)
    image_key = None
    image_url = source_url
    uploaded = False
    if source_url and not skip_images:
        image_key = catalog_image_key(slug, source_url)
        try:
            if force_images or not object_exists(image_key, s3):
                upload_bytes(
                    image_key,
                    download_bytes(http, source_url),
                    content_type_for(image_key),
                    s3,
                )
                uploaded = True
            image_url = s3_public_url(image_key)
        except Exception as exc:  # noqa: BLE001
            log.warning("image failed %s: %s", slug, exc)
            image_key = None
            image_url = source_url
    wine = map_wine(item, detail, image_url)
    if not insert_wine_if_absent(wine, image_key):
        return {"skipped": True, "uploaded_image": uploaded}
    return {"uploaded_image": uploaded}


def _text(value: Any) -> str:
    if isinstance(value, dict):
        return str(value.get("name") or "").strip()
    if value is None:
        return ""
    return str(value).strip()


def _names(value: Any) -> list[str]:
    if not isinstance(value, list):
        return []
    names = [_text(item) for item in value]
    return [name for name in names if name]


def _optional_float(value: Any) -> float | None:
    try:
        return float(value) if value is not None else None
    except (TypeError, ValueError):
        return None


def _alcohol(value: Any) -> float | None:
    number = _optional_float(value)
    if number is None:
        return None
    if number > 30:
        return number / 10.0
    return number


def _vintage(data: dict[str, Any]) -> int | None:
    raw = data.get("vintage") or data.get("year")
    try:
        year = int(raw)
    except (TypeError, ValueError):
        return None
    return year if 1900 <= year <= 2100 else None


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Import vino-svoe.ru wines into Postgres and MinIO")
    parser.add_argument("--per-page", type=int, default=20)
    parser.add_argument("--start-page", type=int, default=1)
    parser.add_argument("--max-pages", type=int, default=None)
    parser.add_argument("--skip-details", action="store_true")
    parser.add_argument("--skip-images", action="store_true")
    parser.add_argument("--force-images", action="store_true")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    args = parse_args(argv)
    stats = import_catalog(
        per_page=args.per_page,
        start_page=args.start_page,
        max_pages=args.max_pages,
        skip_details=args.skip_details,
        skip_images=args.skip_images,
        force_images=args.force_images,
    )
    log.info("done: %s", stats)
    return 0 if stats["failed"] == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
