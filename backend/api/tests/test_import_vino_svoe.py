from unittest.mock import MagicMock, patch
import json

from api.catalog import load_catalog_rows, search_wines, wine_from_row
from api.import_vino_svoe import (
    catalog_image_key,
    fetch_list_page,
    image_source_url,
    import_catalog,
    iter_list_pages,
    map_wine,
)


LIST_ITEM = {
    "category": "Красное сухое",
    "manufacturer": "WINEPARK",
    "region": "Крым",
    "title": "Симбиоз",
    "slug": "simbioz",
    "publicRating": 4,
    "image": {"altText": "симбиоз", "url": "/uploads/simbioz_2021_975b321bc5.webp"},
    "color": "Темно-рубиновый",
}

DETAIL = {
    "alcohol": 135,
    "category": {"name": "Красное сухое"},
    "description": "В аромате красные ягоды.",
    "dishes": [{"name": "BBQ"}, {"name": "Сыры"}],
    "grapes": [{"name": "Каберне Совиньон"}],
    "image": {"url": "/uploads/simbioz_2021_975b321bc5.webp"},
    "manufacturer": {"name": "WINEPARK", "slug": "winepark"},
    "publicRating": 4,
    "region": {"name": "Крым"},
    "slug": "simbioz",
    "title": "Симбиоз",
}


def test_map_wine_from_list_and_detail():
    wine = map_wine(LIST_ITEM, DETAIL, "http://localhost:9006/storage/wines/simbioz.webp")
    assert wine.id == wine.slug == "simbioz"
    assert wine.name == "Симбиоз"
    assert wine.rating == 4
    assert wine.alcoholPercentage == 13.5
    assert wine.region.name == "Крым"
    assert wine.country.code == "RU"
    assert wine.grapes == "Каберне Совиньон"
    assert wine.category == "Красное сухое"
    assert wine.winery.name == "WINEPARK"
    assert wine.foodPairing == ["BBQ", "Сыры"]
    assert wine.imageUrl.endswith("simbioz.webp")


def test_map_wine_without_rating():
    wine = map_wine({"slug": "no-rating", "title": "Без рейтинга", "region": "Кубань"}, None, None)
    assert wine.rating is None
    assert wine.reviewsCount is None
    assert wine.variety is None


def test_image_helpers():
    url = image_source_url(LIST_ITEM, DETAIL)
    assert url == (
        "https://api.vino-svoe.ru/v1/img/str-api/1160/1160/resize"
        "/uploads/simbioz_2021_975b321bc5.webp"
    )
    assert catalog_image_key("simbioz", url) == "wines/simbioz.webp"


def test_iter_list_pages_stops_at_total():
    client = MagicMock()
    client.get.side_effect = [
        _json_response({"currentPage": 1, "totalPages": 2, "items": [LIST_ITEM]}),
        _json_response({"currentPage": 2, "totalPages": 2, "items": []}),
    ]
    pages = list(iter_list_pages(client, per_page=20))
    assert len(pages) == 2
    assert client.get.call_count == 2
    assert client.get.call_args.kwargs["params"]["perPage"] == 20


def test_list_page_caps_per_page_at_api_limit():
    client = MagicMock()
    client.get.return_value = _json_response({"currentPage": 1, "totalPages": 1, "items": []})
    list(iter_list_pages(client, per_page=50))
    assert client.get.call_args.kwargs["params"]["perPage"] == 20


@patch("api.import_vino_svoe.time.sleep")
def test_fetch_list_page_retries_empty_body(_sleep):
    empty = MagicMock()
    empty.status_code = 200
    empty.content = b""
    empty.raise_for_status.return_value = None
    client = MagicMock()
    client.get.side_effect = [empty, _json_response({"currentPage": 74, "items": [LIST_ITEM]})]
    payload = fetch_list_page(client, page=74, per_page=20)
    assert payload["currentPage"] == 74
    assert client.get.call_count == 2
    _sleep.assert_called()


def test_load_catalog_rows_json_and_jsonl():
    rows = load_catalog_rows(b'[{"slug":"a","title":"A"},{"slug":"b","title":"B"}]')
    assert [row["slug"] for row in rows] == ["a", "b"]
    wrapped = load_catalog_rows(b'{"items":[{"slug":"c","title":"C"}]}')
    assert wrapped[0]["slug"] == "c"
    jsonl = load_catalog_rows(b'{"slug":"d","title":"D"}\n{"slug":"e","title":"E"}\n')
    assert [row["slug"] for row in jsonl] == ["d", "e"]


def test_wine_from_row_reads_payload_and_s3_key():
    wine = wine_from_row((
        "simbioz",
        "Симбиоз",
        {
            "id": "simbioz",
            "slug": "simbioz",
            "name": "Симбиоз",
            "rating": 4,
            "category": "Красное сухое",
            "winery": {"name": "WINEPARK"},
        },
        "wines/simbioz.webp",
    ))
    assert wine.id == "simbioz"
    assert wine.rating == 4
    assert wine.winery.name == "WINEPARK"
    assert wine.imageUrl.endswith("/storage/wines/simbioz.webp")


@patch("api.catalog._search_file")
@patch("api.catalog.psycopg.connect")
def test_search_wines_reads_database(connect, search_file):
    conn = MagicMock()
    connect.return_value.__enter__.return_value = conn
    count = MagicMock()
    count.fetchone.return_value = (1,)
    select = MagicMock()
    select.fetchall.return_value = [
        ("simbioz", "Симбиоз", {"id": "simbioz", "slug": "simbioz", "name": "Симбиоз"}, None)
    ]
    conn.execute.side_effect = [count, select]
    wines, total, has_more = search_wines("симбиоз", 1, 20)
    assert total == 1
    assert wines[0].id == "simbioz"
    assert has_more is False
    search_file.assert_not_called()


@patch("api.import_vino_svoe.insert_wine_if_absent", return_value=True)
@patch("api.import_vino_svoe.existing_wine_ids", return_value=set())
@patch("api.import_vino_svoe.upload_bytes", return_value="wines/simbioz.webp")
@patch("api.import_vino_svoe.object_exists", return_value=False)
@patch("api.import_vino_svoe.ensure_bucket")
@patch("api.import_vino_svoe.minio_client")
@patch("api.import_vino_svoe.init_infra")
def test_import_catalog_upserts_and_uploads(_init, _minio, _bucket, _exists, _upload, _known, _insert):
    http = MagicMock()
    http.get.side_effect = [
        _json_response({"currentPage": 1, "totalPages": 1, "items": [LIST_ITEM]}),
        _json_response(DETAIL),
        _bytes_response(b"image-bytes"),
    ]
    stats = import_catalog(client=http, per_page=20, ensure_infra=False)
    assert stats["upserted"] == 1
    assert stats["images"] == 1
    assert stats["skipped"] == 0
    assert stats["failed"] == 0
    _upload.assert_called_once()
    wine = _insert.call_args.args[0]
    assert wine.id == "simbioz"
    assert wine.alcoholPercentage == 13.5


@patch("api.import_vino_svoe.insert_wine_if_absent", return_value=True)
@patch("api.import_vino_svoe.existing_wine_ids", return_value=set())
@patch("api.import_vino_svoe.upload_bytes", side_effect=RuntimeError("404"))
@patch("api.import_vino_svoe.object_exists", return_value=False)
@patch("api.import_vino_svoe.ensure_bucket")
@patch("api.import_vino_svoe.minio_client")
@patch("api.import_vino_svoe.init_infra")
def test_import_keeps_wine_if_image_missing(_init, _minio, _bucket, _exists, _upload, _known, _insert):
    http = MagicMock()
    http.get.side_effect = [
        _json_response({"currentPage": 1, "totalPages": 1, "items": [LIST_ITEM]}),
        _json_response(DETAIL),
        _bytes_response(b"unused"),
    ]
    stats = import_catalog(client=http, per_page=20, ensure_infra=False)
    assert stats["upserted"] == 1
    assert stats["images"] == 0
    assert stats["failed"] == 0
    wine = _insert.call_args.args[0]
    assert wine.id == "simbioz"
    assert "str-api" in (wine.imageUrl or "")


@patch("api.import_vino_svoe.insert_wine_if_absent")
@patch("api.import_vino_svoe.existing_wine_ids", return_value={"simbioz"})
@patch("api.import_vino_svoe.ensure_bucket")
@patch("api.import_vino_svoe.minio_client")
@patch("api.import_vino_svoe.init_infra")
def test_import_catalog_skips_duplicates(_init, _minio, _bucket, _known, _insert):
    http = MagicMock()
    http.get.return_value = _json_response({"currentPage": 1, "totalPages": 1, "items": [LIST_ITEM]})
    stats = import_catalog(client=http, per_page=20, ensure_infra=False)
    assert stats["skipped"] == 1
    assert stats["upserted"] == 0
    _insert.assert_not_called()
    assert http.get.call_count == 1


def _json_response(payload: dict) -> MagicMock:
    response = MagicMock()
    response.status_code = 200
    response.content = json.dumps(payload).encode()
    response.json.return_value = payload
    response.raise_for_status.return_value = None
    return response


def _bytes_response(payload: bytes) -> MagicMock:
    response = MagicMock()
    response.status_code = 200
    response.content = payload
    response.raise_for_status.return_value = None
    return response
