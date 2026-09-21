import json
import os
import sys
import time
import threading
from concurrent.futures import ThreadPoolExecutor

import pika
import psycopg
import requests
from minio import Minio
from psycopg.types.json import Json

from backend.worker.config import BACKEND_URL, BUCKET, SCAN_QUEUE
from backend.worker.recognition import initialize, shutdown, run


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
        raise ValueError("Missing uploaded image key")
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
    result = run(image, includeAlternatives=include_alternatives)
    update_scan(scan_id, "done", result=result)


def main() -> None:
    wait_for_backend()
    wait_for_s3()
    scanner = initialize()
    metadata = {"catalogSha256":scanner.manifest["catalog_sha256"], "version":scanner.manifest["version"]}
    def report_ready(ready):
        with psycopg.connect(database_url()) as conn:
            conn.execute("""INSERT INTO recognition_worker (name,ready,metadata) VALUES ('recognizer',%s,%s)
                ON CONFLICT (name) DO UPDATE SET ready=EXCLUDED.ready, metadata=EXCLUDED.metadata, updated_at=NOW()""",
                (ready, Json(metadata)))
    stop = threading.Event()
    def heartbeat():
        while not stop.wait(5):
            try:
                report_ready(not scanner.closed)
            except Exception as exc:
                log(f"worker readiness heartbeat failed: {exc}")
    connection = wait_for_rabbit()
    channel = connection.channel()
    channel.queue_declare(queue=SCAN_QUEUE, durable=True)
    executor = ThreadPoolExecutor(max_workers=1)

    def on_scan(ch, method, properties, body: bytes) -> None:
        scan_id = None
        try:
            payload = json.loads(body.decode("utf-8"))
            scan_id = payload.get("scanId")
            def work():
                try:
                    process_scan(payload)
                except Exception as exc:
                    if scan_id:
                        update_scan(scan_id, "failed", error=str(exc))
                    raise
            future = executor.submit(work)
            def finish():
                try:
                    future.result()
                    ch.basic_ack(delivery_tag=method.delivery_tag)
                except Exception as exc:
                    log(f"scan failed: {exc}")
                    ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)
            # RabbitMQ I/O and acknowledgements stay on the connection thread.
            future.add_done_callback(lambda _: connection.add_callback_threadsafe(finish))
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
    report_ready(True)
    heartbeat_thread = threading.Thread(target=heartbeat, daemon=True)
    heartbeat_thread.start()
    log(f"worker ready, queue={SCAN_QUEUE}")

    try:
        channel.start_consuming()
    finally:
        stop.set()
        heartbeat_thread.join(timeout=6)
        try:
            report_ready(False)
        finally:
            executor.shutdown(wait=True)
            shutdown()
            if connection.is_open:
                connection.close()


if __name__ == "__main__":
    main()
