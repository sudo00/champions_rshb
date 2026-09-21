"""Summarize completed encoders and diagnostic ranks without tuning retrieval."""
from __future__ import annotations

import argparse
from collections import Counter
import json
from pathlib import Path
import statistics
import sys

import numpy as np

from label_rectification_pilot import ROOT, digest, read, write
from visual_query_review import reviewed_query

sys.path.insert(0, str(ROOT / "worker"))
from pipeline.visual_search import VisualIndex

BASE = ROOT / "data/audit/visual_search"


def summarize(model: str, cards: list[dict]) -> dict:
    base = BASE / (model + "_v1")
    results, encoding = read(base / "results.json"), read(base / "encoding.json")
    if results["catalog_sha256"] != digest(ROOT / "data/catalog/curated/catalog.jsonl"):
        raise ValueError("Catalogue changed")
    if results["encoding_sha256"] != digest(base / "encoding.json"):
        raise ValueError("Encoding changed")
    for filename, key in [("features.npy", "features_sha256"), ("views.json", "views_sha256"),
                          ("config.json", "config_sha256")]:
        if digest(base / filename) != encoding[key]:
            raise ValueError("Encoding artifact changed: " + filename)
    views = read(base / "views.json")
    vectors = np.load(base / "features.npy", allow_pickle=False)
    if not np.isfinite(vectors).all() or not np.allclose(np.linalg.norm(vectors, axis=1), 1., atol=1e-5):
        raise ValueError("Invalid normalized embeddings")
    gi = [i for i, view in enumerate(views) if view["role"] == "gallery"]
    index = VisualIndex(vectors[gi], [views[i] for i in gi], cards)
    reviews = []
    for row in results["images"]:
        review = reviewed_query(row["id"], row["source_sha256"],
                                {mode: value["candidates"] for mode, value in row["results"].items()})
        if review is None:
            continue
        full_ranks = {}
        if review["slug"]:
            qi = [i for i, view in enumerate(views) if view["record_id"] == row["id"]]
            for augment in (False, True):
                # Wider limit only diagnoses branch rank. Production Top-10 is unchanged.
                found = index.search(vectors[qi], [views[i] for i in qi], augment=augment, limit=len(cards))
                full_ranks["augmented" if augment else "baseline"] = {
                    branch: next((i + 1 for i, c in enumerate(candidates) if c["slug"] == review["slug"]), None)
                    for branch, candidates in found["branches"].items()
                }
        reviews.append({"id": row["id"], **review, "full_catalog_branch_ranks": full_ranks,
                        "first_candidates": {mode: value["candidates"][0]["slug"] if value["candidates"] else None
                                             for mode, value in row["results"].items()}})
    batches = encoding["measured_batches_this_run"]
    total_views = sum(batch["count"] for batch in batches)
    changed = []
    for row in results["images"]:
        before = [c["slug"] for c in row["results"]["baseline"]["candidates"]]
        after = [c["slug"] for c in row["results"]["augmented"]["candidates"]]
        if before != after:
            changed.append(row["id"])
    return {
        "results_sha256": digest(base / "results.json"),
        "shape": list(vectors.shape), "gallery_views": len(gi), "query_views": len(views) - len(gi),
        "families": dict(Counter(view["family"] for view in views)),
        "encoding_seconds": encoding["elapsed_seconds_this_run"],
        "gpu_forward_ms_per_view_in_batch": sum(b["gpu_forward_seconds"] for b in batches) * 1000 / total_views if total_views else None,
        "measured_views_this_run": total_views,
        "batch_size": read(base / "config.json")["batch_size"],
        "peak_allocated_gib": encoding["peak_allocated_bytes"] / 2**30,
        "all_modes_search_median_ms": statistics.median(r["all_modes_search_ms"] for r in results["images"]),
        "self_image_diagnostics_not_accuracy": results["self_image_diagnostics_not_accuracy"],
        "augmentation_changed_ordered_top10": changed,
        "controls": {r["id"]: r["results"]["augmented"]["status"] for r in results["images"] if r["group"] == "control"},
        "reviewed_queries": reviews,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--models", nargs="+", default=["dinov3_timm", "dinov3_large", "siglip2"])
    parser.add_argument("--output", type=Path, default=BASE / "siglip_experiment_v1/summary.json")
    args = parser.parse_args()
    cards = [json.loads(line) for line in (ROOT / "data/catalog/curated/catalog.jsonl").read_text().splitlines()]
    value = {"script_sha256": digest(Path(__file__)),
             "manual_review_code_sha256": digest(ROOT / "scripts/visual_query_review.py"),
             "search_code_sha256": digest(ROOT / "worker/pipeline/visual_search.py"),
             "note": "Self-images are not real-world accuracy. Full branch ranks are diagnostics only; output remains Top-10.",
             "models": {model: summarize(model, cards) for model in args.models}}
    write(args.output, value)
    print(args.output)


if __name__ == "__main__":
    main()
