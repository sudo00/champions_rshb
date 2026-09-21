import os


def env(name: str, default: str | None = None) -> str:
    value = os.environ.get(name, default)
    if value is None:
        raise RuntimeError(f"Missing env var {name}")
    return value


def s3_config() -> dict:
    endpoint = env("S3_ENDPOINT", "s3:9000")
    host, port_raw = endpoint.split(":", 1)
    return {
        "endPoint": host,
        "port": int(port_raw),
        "bucket": env("S3_BUCKET_NAME", "storage"),
        "accessKey": env("S3_ACCESS_KEY", "minioadmin"),
        "secretKey": env("S3_SECRET_KEY", "minioadmin"),
        "useSSL": False,
        "publicBaseUrl": env("S3_PUBLIC_BASE_URL", "http://localhost:9006"),
    }


def s3_public_url(key: str) -> str:
    s3 = s3_config()
    return f"{s3['publicBaseUrl'].rstrip('/')}/{s3['bucket']}/{key.lstrip('/')}"


def rabbit_url() -> str:
    user = env("RABBITMQ_USER", "user")
    password = env("RABBITMQ_PASS", "password")
    host = env("RABBITMQ_HOST", "rabbitmq")
    port = env("RABBITMQ_PORT", "5672")
    vhost = env("RABBITMQ_VHOST", "/")
    vhost_enc = vhost.replace("/", "%2F") if vhost != "/" else "%2F"
    return f"amqp://{user}:{password}@{host}:{port}/{vhost_enc}"


SCAN_QUEUE = env("RABBITMQ_QUEUE_SCAN", "scan")
DATABASE_URL = os.environ.get(
    "DATABASE_URL",
    "postgresql://postgres:postgres@db:5432/champions",
)
