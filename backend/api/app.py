import os
import time

import pika
import psycopg2
from flask import Flask, jsonify
from flask_cors import CORS
from minio import Minio

app = Flask(__name__)
CORS(app)

BUCKET = os.environ.get("S3_BUCKET_NAME", "storage")
TESTING = os.environ.get("TESTING", "") == "1"


def _env(name: str, default: str | None = None) -> str:
    value = os.environ.get(name, default)
    if value is None:
        raise RuntimeError(f"Missing env var {name}")
    return value


def db_conn():
    return psycopg2.connect(_env("DATABASE_URL"))


def rabbit_params() -> pika.ConnectionParameters:
    return pika.ConnectionParameters(
        host=_env("RABBITMQ_HOST", "rabbitmq"),
        port=int(_env("RABBITMQ_PORT", "5672")),
        virtual_host=_env("RABBITMQ_VHOST", "/"),
        credentials=pika.PlainCredentials(
            _env("RABBITMQ_USER", "user"),
            _env("RABBITMQ_PASS", "password"),
        ),
        heartbeat=30,
        blocked_connection_timeout=10,
    )


def s3_client() -> Minio:
    return Minio(
        _env("S3_ENDPOINT", "s3:9000"),
        access_key=_env("S3_ACCESS_KEY", "minioadmin"),
        secret_key=_env("S3_SECRET_KEY", "minioadmin"),
        secure=False,
    )


def init_infra(retries: int = 20, delay: float = 1.5) -> None:
    last_error = None
    for _ in range(retries):
        try:
            with db_conn() as conn:
                with conn.cursor() as cur:
                    cur.execute("SELECT 1")

            client = s3_client()
            if not client.bucket_exists(BUCKET):
                client.make_bucket(BUCKET)

            connection = pika.BlockingConnection(rabbit_params())
            connection.close()
            return
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            time.sleep(delay)
    raise RuntimeError(f"Infrastructure is not ready: {last_error}")


_initialized = False


@app.before_request
def startup() -> None:
    global _initialized
    if TESTING or _initialized:
        return
    init_infra()
    _initialized = True


@app.get("/")
def ready():
    return jsonify({"status": "ready", "service": "backend"})
