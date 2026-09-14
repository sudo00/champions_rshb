import os

os.environ.setdefault("TESTING", "1")

from app import app


def test_ready():
    client = app.test_client()
    response = client.get("/")
    assert response.status_code == 200
    body = response.get_json()
    assert body["status"] == "ready"
    assert body["service"] == "backend"
