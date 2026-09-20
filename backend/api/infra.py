import json
import time
from io import BytesIO

import pika
import psycopg
from minio import Minio

from api.config import DATABASE_URL, SCAN_QUEUE, rabbit_url, s3_config


def init_infra(retries: int = 20, delay_s: float = 1.5) -> None:
    last_error: Exception | None = None
    for _ in range(retries):
        try:
            with psycopg.connect(DATABASE_URL) as conn:
                conn.execute("SELECT 1")
                conn.execute("CREATE EXTENSION IF NOT EXISTS vector")
                conn.execute(
                    """
                    CREATE TABLE IF NOT EXISTS wines (
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        payload JSONB NOT NULL DEFAULT '{}'::jsonb,
                        image_key TEXT
                    )
                    """
                )
                conn.execute(
                    """
                    CREATE TABLE IF NOT EXISTS scans (
                        id UUID PRIMARY KEY,
                        status TEXT NOT NULL,
                        include_alternatives BOOLEAN NOT NULL DEFAULT TRUE,
                        image_key TEXT,
                        result JSONB,
                        error TEXT,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )
                    """
                )
                conn.commit()

            s3 = s3_config()
            minio = Minio(
                f"{s3['endPoint']}:{s3['port']}",
                access_key=s3["accessKey"],
                secret_key=s3["secretKey"],
                secure=s3["useSSL"],
            )
            if not minio.bucket_exists(s3["bucket"]):
                minio.make_bucket(s3["bucket"])

            connection = pika.BlockingConnection(pika.URLParameters(rabbit_url()))
            channel = connection.channel()
            channel.queue_declare(queue=SCAN_QUEUE, durable=True)
            connection.close()
            return
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            time.sleep(delay_s)
    raise RuntimeError(f"Infrastructure is not ready: {last_error}")


def upload_scan_image(scan_id: str, image: bytes) -> str:
    s3 = s3_config()
    minio = Minio(
        f"{s3['endPoint']}:{s3['port']}",
        access_key=s3["accessKey"],
        secret_key=s3["secretKey"],
        secure=s3["useSSL"],
    )
    if not minio.bucket_exists(s3["bucket"]):
        minio.make_bucket(s3["bucket"])
    key = f"scans/{scan_id}.jpg"
    minio.put_object(
        s3["bucket"],
        key,
        BytesIO(image),
        length=len(image),
        content_type="image/jpeg",
    )
    return key


def publish_scan(payload: dict) -> None:
    connection = pika.BlockingConnection(pika.URLParameters(rabbit_url()))
    try:
        channel = connection.channel()
        channel.queue_declare(queue=SCAN_QUEUE, durable=True)
        channel.basic_publish(
            exchange="",
            routing_key=SCAN_QUEUE,
            body=json.dumps(payload).encode("utf-8"),
            properties=pika.BasicProperties(delivery_mode=2, content_type="application/json"),
        )
    finally:
        connection.close()
