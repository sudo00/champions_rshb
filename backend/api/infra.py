import json
import time
from io import BytesIO

import pika
import psycopg
from minio import Minio
from minio.error import S3Error

from api.config import DATABASE_URL, SCAN_QUEUE, rabbit_url, s3_config


def minio_client() -> Minio:
    s3 = s3_config()
    return Minio(
        f"{s3['endPoint']}:{s3['port']}",
        access_key=s3["accessKey"],
        secret_key=s3["secretKey"],
        secure=s3["useSSL"],
    )


def ensure_bucket(client: Minio | None = None) -> Minio:
    s3 = s3_config()
    minio = client or minio_client()
    if not minio.bucket_exists(s3["bucket"]):
        minio.make_bucket(s3["bucket"])
    policy = {
        "Version": "2012-10-17",
        "Statement": [
            {
                "Effect": "Allow",
                "Principal": {"AWS": ["*"]},
                "Action": ["s3:GetObject"],
                "Resource": [f"arn:aws:s3:::{s3['bucket']}/*"],
            }
        ],
    }
    minio.set_bucket_policy(s3["bucket"], json.dumps(policy))
    return minio


def object_exists(key: str, client: Minio | None = None) -> bool:
    s3 = s3_config()
    minio = client or minio_client()
    try:
        minio.stat_object(s3["bucket"], key)
        return True
    except S3Error:
        return False


def upload_bytes(key: str, data: bytes, content_type: str, client: Minio | None = None) -> str:
    s3 = s3_config()
    minio = ensure_bucket(client)
    minio.put_object(
        s3["bucket"],
        key,
        BytesIO(data),
        length=len(data),
        content_type=content_type,
    )
    return key


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
                conn.execute("""CREATE TABLE IF NOT EXISTS recognition_worker (
                    name TEXT PRIMARY KEY, ready BOOLEAN NOT NULL,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), metadata JSONB NOT NULL
                )""")
                from api.catalog import seed_catalog_file
                inserted, skipped = seed_catalog_file(conn)
                conn.commit()
                print(f"catalog file seed: inserted={inserted} skipped={skipped}", flush=True)

            ensure_bucket()

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
    return upload_bytes(f"scans/{scan_id}/image", image, "application/octet-stream")


def recognition_ready() -> bool:
    from api.catalog import catalog_data
    with psycopg.connect(DATABASE_URL) as conn:
        row = conn.execute("""SELECT metadata FROM recognition_worker WHERE name='recognizer'
            AND ready AND updated_at > NOW() - INTERVAL '20 seconds'""").fetchone()
    return bool(row and row[0].get("catalogSha256") == catalog_data()[1])


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
