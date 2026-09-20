import json
import os
import sys
import time

import pika
import psycopg
import requests
from minio import Minio
from psycopg.types.json import Json

from config import BACKEND_URL, BUCKET, SCAN_QUEUE
from pipeline import run

STUB_RESULT = {
    "wineId": "fanagoria-cabernet",
    "confidence": 0.0,
    "alternativeIds": ["abrau-durso-brut", "massaandra-muscat", "lefkadia-sauvignon"],
}


def log(message: str) -> None:
    print(message, file=sys.stderr, flush=True)


def rabbit_params() -> pika.ConnectionParameters:
    return pika.ConnectionParameters(
        host=os.environ.get("RABBITMQ_HOST", "rabbitmq"),
        port=int(os.environ.get("RABBITMQ_PORT", "5672")),
        virtual_host=os.environ.get("RABBITMQ_VHOST", "/"),
        credentials=pika.PlainCredentials(
            os.environ.get("RABBITMQ_USER", "user"),
            os.environ.get("RABBITMQ_PASS", "password"),
        ),
        heartbeat=30,
        blocked_connection_timeout=10,
    )


def database_url() -> str:
    return os.environ.get(
        "DATABASE_URL",
        "postgresql://postgres:postgres@db:5432/champions",
    )


def s3_client() -> Minio:
    return Minio(
        os.environ.get("S3_ENDPOINT", "s3:9000"),
        access_key=os.environ.get("S3_ACCESS_KEY", "minioadmin"),
        secret_key=os.environ.get("S3_SECRET_KEY", "minioadmin"),
        secure=False,
    )


def wait_for_backend() -> None:
    for attempt in range(30):
        try:
            response = requests.get(BACKEND_URL, timeout=3)
            if response.ok:
                log(f"backend is ready: {response.json()}")
                return
        except requests.RequestException as exc:
            log(f"waiting for backend ({attempt + 1}/30): {exc}")
        time.sleep(2)
    raise RuntimeError("backend is not reachable")


def wait_for_rabbit() -> pika.BlockingConnection:
    last_error = None
    for attempt in range(30):
        try:
            connection = pika.BlockingConnection(rabbit_params())
            log("connected to rabbitmq")
            return connection
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            log(f"waiting for rabbitmq ({attempt + 1}/30): {exc}")
            time.sleep(2)
    raise RuntimeError(f"rabbitmq is not reachable: {last_error}")


def wait_for_s3() -> None:
    last_error = None
    for attempt in range(30):
        try:
            client = s3_client()
            if not client.bucket_exists(BUCKET):
                client.make_bucket(BUCKET)
            log("connected to s3")
            return
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            log(f"waiting for s3 ({attempt + 1}/30): {exc}")
            time.sleep(2)
    raise RuntimeError(f"s3 is not reachable: {last_error}")


def update_scan(scan_id: str, status: str, result: dict | None = None, error: str | None = None) -> None:
    with psycopg.connect(database_url()) as conn:
        conn.execute(
            """
            UPDATE scans
            SET status = %s, result = %s, error = %s, updated_at = NOW()
            WHERE id = %s
            """,
            (status, Json(result) if result is not None else None, error, scan_id),
        )
        conn.commit()


def load_image(image_key: str | None) -> bytes:
    if not image_key:
        return b""
    client = s3_client()
    response = client.get_object(BUCKET, image_key)
    try:
        return response.read()
    finally:
        response.close()
        response.release_conn()


def process_scan(payload: dict) -> None:
    scan_id = payload["scanId"]
    include_alternatives = bool(payload.get("includeAlternatives", True))
    update_scan(scan_id, "processing")
    image = load_image(payload.get("imageKey"))
    run(image)
    result = dict(STUB_RESULT)
    if not include_alternatives:
        result["alternativeIds"] = []
    update_scan(scan_id, "done", result=result)


def main() -> None:
    wait_for_backend()
    wait_for_s3()
    connection = wait_for_rabbit()
    channel = connection.channel()
    channel.queue_declare(queue=SCAN_QUEUE, durable=True)

    def on_scan(ch, method, properties, body: bytes) -> None:
        scan_id = None
        try:
            payload = json.loads(body.decode("utf-8"))
            scan_id = payload.get("scanId")
            process_scan(payload)
            ch.basic_ack(delivery_tag=method.delivery_tag)
        except Exception as exc:  # noqa: BLE001
            log(f"scan failed: {exc}")
            if scan_id:
                try:
                    update_scan(scan_id, "failed", error=str(exc))
                except Exception as db_exc:  # noqa: BLE001
                    log(f"failed to mark scan: {db_exc}")
            ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)

    channel.basic_qos(prefetch_count=1)
    channel.basic_consume(queue=SCAN_QUEUE, on_message_callback=on_scan)
    log(f"worker ready, queue={SCAN_QUEUE}")

    try:
        channel.start_consuming()
    finally:
        if connection.is_open:
            connection.close()


if __name__ == "__main__":
    main()
