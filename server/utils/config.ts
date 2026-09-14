export function env(name: string, fallback?: string): string {
  const value = process.env[name] ?? fallback;
  if (value === undefined) {
    throw new Error(`Missing env var ${name}`);
  }
  return value;
}

export function s3Config() {
  const raw = env("S3_ENDPOINT", "s3:9000");
  const [endPoint, portValue] = raw.split(":");
  return {
    endPoint,
    port: Number(portValue ?? "9000"),
    useSSL: false,
    accessKey: env("S3_ACCESS_KEY", "minioadmin"),
    secretKey: env("S3_SECRET_KEY", "minioadmin"),
    bucket: env("S3_BUCKET_NAME", "storage"),
  };
}

export function rabbitConfig() {
  return {
    hostname: env("RABBITMQ_HOST", "rabbitmq"),
    port: Number(env("RABBITMQ_PORT", "5672")),
    username: env("RABBITMQ_USER", "user"),
    password: env("RABBITMQ_PASS", "password"),
    vhost: env("RABBITMQ_VHOST", "/"),
  };
}

export function readyPayload() {
  return {
    status: "ready",
    service: "backend",
  } as const;
}
