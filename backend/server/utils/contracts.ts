export const EVAL_UNKNOWN_SLUG = "unknown";

export function evalPayload(slug = EVAL_UNKNOWN_SLUG) {
  return { slug };
}

export function scanPayload(slug = EVAL_UNKNOWN_SLUG) {
  return {
    slug,
    confidence: {
      f1_top1: 0,
      f1_top5: 0,
    },
    wine: null as Record<string, unknown> | null,
  };
}

export const queues = {
  scan: "scan",
} as const;
