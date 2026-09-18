import os


def env(name: str, default: str | None = None) -> str:
    value = os.environ.get(name, default)
    if value is None:
        raise RuntimeError(f"Missing env var {name}")
    return value


BUCKET = env("S3_BUCKET_NAME", "storage")
BACKEND_URL = env("BACKEND_URL", "http://app:3000/api/ready")
SCAN_QUEUE = env("RABBITMQ_QUEUE_SCAN", "scan")
