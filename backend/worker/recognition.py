"""Image bytes -> Top-5 and image evidence. One initialized instance per GPU."""
from __future__ import annotations

import os
from pathlib import Path
import sys
import threading
import time

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
from worker.pipeline.wine_recognizer import WineRecognizer
from backend.worker.catalog_refusal import CatalogRefusal, apply_refusal
from backend.worker.candidate_scoring import CandidateScorer, apply_candidate_scores
from backend.worker.sweetness import refine_sweetness

_recognizer: WineRecognizer | None = None
_refusal: CatalogRefusal | None = None
_scorer: CandidateScorer | None = None
_initialization_lock = threading.Lock()


def initialize() -> WineRecognizer:
    global _recognizer, _refusal, _scorer
    with _initialization_lock:
        if _recognizer is None:
            recognizer = WineRecognizer(
                os.environ.get("WINE_BUNDLE_DIR", str(ROOT/"weights/wine-recognizer-v5-muscat-release")),
                ocr_python=os.environ.get("WINE_OCR_PYTHON", str(ROOT/".venv-ocr-gpu/bin/python")),
                device=os.environ.get("WINE_DEVICE", "cuda:0"),
                ocr_device=os.environ.get("WINE_OCR_DEVICE", "gpu:0"),
                memory_policy="release",
            )
            try:
                refusal = CatalogRefusal.from_bundle(recognizer.bundle, recognizer.manifest)
                scorer = CandidateScorer.from_bundle(recognizer.bundle, recognizer.manifest)
            except Exception:
                recognizer.close()
                raise
            _recognizer, _refusal, _scorer = recognizer, refusal, scorer
        return _recognizer


def shutdown() -> None:
    global _recognizer, _refusal, _scorer
    with _initialization_lock:
        if _recognizer is not None:
            _recognizer.close()
            _recognizer = None
            _refusal = None
            _scorer = None


def run(image: bytes, includeAlternatives: bool = True, *, applyCatalogRefusal: bool = True,
        useReviewedSweetness: bool = True) -> dict:
    if not isinstance(image, bytes) or not image:
        raise ValueError("image must contain encoded image bytes")
    if not isinstance(includeAlternatives, bool):
        raise ValueError("includeAlternatives must be bool")
    if not isinstance(applyCatalogRefusal, bool):
        raise ValueError("applyCatalogRefusal must be bool")
    if not isinstance(useReviewedSweetness, bool):
        raise ValueError("useReviewedSweetness must be bool")
    # Retrieval always has the same candidate pool. The flag only limits output.
    recognizer = initialize()
    result = recognizer.predict(image, top_k=10)
    # Presence decisions keep the validated legacy feature distribution.
    decision = (_refusal.evaluate(result, recognizer.lookup)
                if _refusal is not None and (_scorer is not None or applyCatalogRefusal) else None)
    if useReviewedSweetness and not (decision and decision["reject"]):
        started = time.perf_counter()
        result = refine_sweetness(result, recognizer.lookup)
        elapsed = time.perf_counter() - started
        result["timings_seconds"] = {**result["timings_seconds"], "sweetness_ranking": elapsed,
                                     "total": result["timings_seconds"].get("total", 0.) + elapsed}
    else:
        result = {**result, "sweetness_ranking": {"applied": False,
                  "reason": "disabled_by_caller" if not useReviewedSweetness else "catalog_refusal"}}
    if _scorer is not None:
        started = time.perf_counter()
        # Score the full shortlist before refusal/truncation, including eval mode.
        # Low matchScore never changes ranking or causes an additional refusal.
        rejects = bool(decision and decision["reject"])
        result = apply_candidate_scores(result, _scorer, recognizer.lookup, refusal_rejects=rejects)
        elapsed = time.perf_counter() - started
        result["timings_seconds"] = {**result["timings_seconds"], "candidate_scoring": elapsed,
                                     "total": result["timings_seconds"].get("total", 0.) + elapsed}
    if _refusal is not None and applyCatalogRefusal:
        started = time.perf_counter()
        result = apply_refusal(result, _refusal, recognizer.lookup, decision=decision)
        elapsed = time.perf_counter() - started
        result["timings_seconds"] = {**result["timings_seconds"], "catalog_refusal": elapsed,
                                     "total": result["timings_seconds"].get("total", 0.) + elapsed}
    elif not applyCatalogRefusal:
        result = {**result, "catalog_refusal": {"reject": False, "reason": "disabled_for_evaluation",
                                               "score_is_probability": False}}
    candidates = [{"rank": i+1, **candidate} for i,candidate in enumerate(result["candidates"][:5 if includeAlternatives else 1])]
    return {
        "slug": candidates[0]["slug"] if candidates else "unknown",
        "candidates": candidates,
        "recognitionStatus": result["status"],
        "catalogRefusal": result.get("catalog_refusal", {}),
        "candidateScoring": result.get("candidate_scoring", {}),
        "sweetnessRanking": result.get("sweetness_ranking", {}),
        "scoreIsProbability": False,
        "observations": result["observations"],
        "observedFields": result["observed_fields"],
        "regions": result["regions"],
        "imageSize": result["image_size"],
        "coordinateSystem": result["coordinate_system"],
        "target": result["target"],
        "warnings": result["warnings"],
        "message": result.get("message"),
        "cylinder": result.get("cylinder", {}),
        "version": result["version"],
        "catalogSha256": result["catalog_sha256"],
        "timingsSeconds": result["timings_seconds"],
    }
