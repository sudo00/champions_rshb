"""Image bytes -> Top-5 and image evidence. One initialized instance per GPU."""
from __future__ import annotations

import os
from pathlib import Path
import sys
import threading

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
from worker.pipeline.wine_recognizer import WineRecognizer

_recognizer: WineRecognizer | None = None
_initialization_lock = threading.Lock()


def initialize() -> WineRecognizer:
    global _recognizer
    with _initialization_lock:
        if _recognizer is None:
            _recognizer = WineRecognizer(
                os.environ.get("WINE_BUNDLE_DIR", str(ROOT/"weights/wine-recognizer-v4-memory-layout-release")),
                ocr_python=os.environ.get("WINE_OCR_PYTHON", str(ROOT/".venv-ocr-gpu/bin/python")),
                device=os.environ.get("WINE_DEVICE", "cuda:0"),
                ocr_device=os.environ.get("WINE_OCR_DEVICE", "gpu:0"),
                memory_policy="release",
            )
        return _recognizer


def shutdown() -> None:
    global _recognizer
    with _initialization_lock:
        if _recognizer is not None:
            _recognizer.close()
            _recognizer = None


def run(image: bytes, includeAlternatives: bool = True) -> dict:
    if not isinstance(image, bytes) or not image:
        raise ValueError("image must contain encoded image bytes")
    if not isinstance(includeAlternatives, bool):
        raise ValueError("includeAlternatives must be bool")
    # Retrieval always has the same candidate pool. The flag only limits output.
    result = initialize().predict(image, top_k=10)
    candidates = [{"rank": i+1, **candidate} for i,candidate in enumerate(result["candidates"][:5 if includeAlternatives else 1])]
    return {
        "slug": candidates[0]["slug"] if candidates else "unknown",
        "candidates": candidates,
        "recognitionStatus": result["status"],
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
