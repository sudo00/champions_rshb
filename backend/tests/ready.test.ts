import { describe, expect, it } from "vitest";
import { evalPayload, scanPayload } from "../server/utils/contracts";
import { readyPayload, s3Config } from "../server/utils/config";

describe("ready payload", () => {
  it("returns the backend ready contract", () => {
    expect(readyPayload()).toEqual({
      status: "ready",
      service: "backend",
    });
  });
});

describe("eval contract", () => {
  it("returns a flat slug object for the organizer script", () => {
    expect(evalPayload()).toEqual({ slug: "unknown" });
  });
});

describe("scan contract", () => {
  it("includes top-1 and top-5 confidence placeholders", () => {
    const body = scanPayload();
    expect(body.slug).toBe("unknown");
    expect(body.confidence).toEqual({ f1_top1: 0, f1_top5: 0 });
  });
});

describe("s3 config", () => {
  it("parses host and port from S3_ENDPOINT", () => {
    process.env.S3_ENDPOINT = "s3:9000";
    process.env.S3_BUCKET_NAME = "storage";
    expect(s3Config()).toMatchObject({
      endPoint: "s3",
      port: 9000,
      bucket: "storage",
      useSSL: false,
    });
  });
});
