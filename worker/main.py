import sys
import time

import pika
import requests
from minio import Minio

from config import BACKEND_URL, BUCKET, SCAN_QUEUE
from pipeline import run


def log(message: str) -> None:
    print(message, file=sys.stderr, flush=True)


def rabbit_params() -> pika.ConnectionParameters:
    import os

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


def s3_client() -> Minio:
    import os

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


def main() -> None:
    wait_for_backend()
    wait_for_s3()
    connection = wait_for_rabbit()
    channel = connection.channel()
    channel.queue_declare(queue=SCAN_QUEUE, durable=True)

    def on_scan(ch, method, properties, body: bytes) -> None:
        try:
            run(body)
            ch.basic_ack(delivery_tag=method.delivery_tag)
        except Exception as exc:  # noqa: BLE001
            log(f"scan failed: {exc}")
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
