import amqplib from "amqplib";
import { Client as MinioClient } from "minio";
import postgres from "postgres";
import { env, rabbitConfig, s3Config } from "./config";

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

export async function initInfra(retries = 20, delayMs = 1500): Promise<void> {
  let lastError: unknown;

  for (let attempt = 0; attempt < retries; attempt += 1) {
    try {
      const sql = postgres(env("DATABASE_URL"), { max: 1 });
      await sql`SELECT 1`;
      await sql`CREATE EXTENSION IF NOT EXISTS vector`;
      await sql.end({ timeout: 5 });

      const s3 = s3Config();
      const minio = new MinioClient({
        endPoint: s3.endPoint,
        port: s3.port,
        useSSL: s3.useSSL,
        accessKey: s3.accessKey,
        secretKey: s3.secretKey,
      });
      const exists = await minio.bucketExists(s3.bucket);
      if (!exists) {
        await minio.makeBucket(s3.bucket);
      }

      const rabbit = rabbitConfig();
      const connection = await amqplib.connect(rabbit);
      const channel = await connection.createChannel();
      await channel.assertQueue("scan", { durable: true });
      await channel.close();
      await connection.close();
      return;
    } catch (error) {
      lastError = error;
      await sleep(delayMs);
    }
  }

  throw new Error(`Infrastructure is not ready: ${String(lastError)}`);
}
