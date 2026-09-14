import os
import sys
import time

import pika
import requests
from minio import Minio

BUCKET = os.environ.get("S3_BUCKET_NAME", "storage")
BACKEND_URL = os.environ.get("BACKEND_URL", "http://backend:4000")


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


def s3_client() -> Minio:
    return Minio(
        os.environ.get("S3_ENDPOINT", "s3:9000"),
        access_key=os.environ.get("S3_ACCESS_KEY", "minioadmin"),
        secret_key=os.environ.get("S3_SECRET_KEY", "minioadmin"),
        secure=False,
    )


def wait_for_backend() -> None:
    url = f"{BACKEND_URL}/"
    for attempt in range(30):
        try:
            response = requests.get(url, timeout=3)
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
    log("worker ready")
    # тут код Лехи




    try:
        while connection.is_open:
            connection.process_data_events(time_limit=1)
    finally:
        if connection.is_open:
            connection.close()


if __name__ == "__main__":
    main()
