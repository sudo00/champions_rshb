"""CLI example for the reusable scanner; initialization happens once per process."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from worker.pipeline.wine_recognizer import WineRecognizer
from backend.worker.catalog_refusal import CatalogRefusal, apply_refusal
from backend.worker.candidate_scoring import CandidateScorer, apply_candidate_scores
from backend.worker.sweetness import refine_sweetness


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("photos", nargs="+", type=Path)
    parser.add_argument("--bundle", type=Path, default=ROOT/"weights/wine-recognizer-v5-muscat-release")
    parser.add_argument("--ocr-python", type=Path, default=ROOT/".venv-ocr-gpu/bin/python")
    parser.add_argument("--ocr-device", default="gpu:0", help="gpu:N or cpu; match the selected OCR environment")
    parser.add_argument("--memory-policy", choices=["release", "retain"], default="release")
    parser.add_argument("--top-k", type=int, default=10)
    parser.add_argument("--output", type=Path, help="JSON file; otherwise write JSON to stdout")
    parser.add_argument("--legacy-ranking", action="store_true")
    args = parser.parse_args()
    with WineRecognizer(args.bundle, ocr_python=args.ocr_python, ocr_device=args.ocr_device,
                        memory_policy=args.memory_policy) as recognizer:
        policy = CatalogRefusal.from_bundle(recognizer.bundle, recognizer.manifest)
        scorer = CandidateScorer.from_bundle(recognizer.bundle, recognizer.manifest)
        results = []
        for path in args.photos:
            result = recognizer.predict(path.read_bytes(), top_k=max(5,args.top_k))
            decision = policy.evaluate(result, recognizer.lookup) if policy is not None else None
            rejects = bool(decision and decision["reject"])
            if not args.legacy_ranking and not rejects:
                result = refine_sweetness(result, recognizer.lookup)
            result = apply_candidate_scores(result, scorer, recognizer.lookup, refusal_rejects=rejects)
            result = apply_refusal(result, policy, recognizer.lookup, decision=decision)
            result = {**result, "candidates": result["candidates"][:args.top_k]}
            results.append(result)
    encoded = json.dumps(results[0] if len(results) == 1 else results, ensure_ascii=False, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(encoded+"\n")
    else:
        print(encoded)


if __name__ == "__main__":
    main()
