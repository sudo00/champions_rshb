import base64
import time

from fastapi.testclient import TestClient

from api.main import app

client = TestClient(app)


def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ready", "service": "api"}


def test_search_pagination():
    response = client.get("/v1/wines/search", params={"q": "каберне", "page": 1, "per_page": 20})
    body = response.json()
    assert response.status_code == 200
    assert body["totalCount"] >= 1
    assert body["page"] == 1
    assert body["wines"][0]["id"] == "fanagoria-cabernet"
    assert "reviewsCount" in body["wines"][0]


def test_wine_detail():
    found = client.get("/v1/wines/abrau-durso-brut")
    assert found.json()["wine"]["name"].startswith("Абрау")
    missing = client.get("/v1/wines/unknown")
    assert missing.json()["wine"] is None


def test_scan_enqueue_and_poll():
    image = base64.b64encode(b"fake-jpeg").decode()
    created = client.post(
        "/v1/wines/scan",
        json={"imageBase64": image, "includeAlternatives": True},
    )
    body = created.json()
    assert created.status_code == 200
    assert body["success"] is True
    assert body["status"] == "pending"
    scan_id = body["scanId"]
    assert scan_id

    status = None
    for _ in range(20):
        polled = client.get(f"/v1/wines/scan/{scan_id}")
        assert polled.status_code == 200
        status = polled.json()
        if status["status"] == "done":
            break
        time.sleep(0.5)

    assert status is not None
    assert status["scanId"] == scan_id
    assert status["status"] == "done"
    assert status["wine"]["id"] == "fanagoria-cabernet"
    assert len(status["alternatives"]) == 3

    missing = client.get("/v1/wines/scan/00000000-0000-0000-0000-000000000000")
    assert missing.status_code == 404


def test_scan_bad_image():
    response = client.post("/v1/wines/scan", json={"imageBase64": ""})
    assert response.json()["success"] is False


def test_sommelier():
    response = client.post(
        "/v1/sommelier/chat",
        json={
            "messages": [{"role": "user", "content": "С чем подавать?"}],
            "wineContext": {
                "wineId": "fanagoria-cabernet",
                "wineName": "Фанагория Каберне",
                "style": "Dry Red",
            },
        },
    )
    body = response.json()
    assert body["success"] is True
    assert body["message"]["role"] == "assistant"
    assert "Фанагория" in body["message"]["content"]


def test_openapi_and_swagger():
    spec = client.get("/openapi.json")
    assert spec.status_code == 200
    paths = spec.json()["paths"]
    assert "/v1/wines/scan" in paths
    assert "/v1/wines/scan/{scan_id}" in paths
    assert "/v1/wines/search" in paths
    assert "/v1/wines/{wine_id}" in paths
    assert "/v1/sommelier/chat" in paths
    docs = client.get("/docs")
    assert docs.status_code == 200
