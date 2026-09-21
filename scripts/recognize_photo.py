"""CLI example for the reusable scanner; initialization happens once per process."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT/"worker"))
from pipeline.wine_recognizer import WineRecognizer


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("photos", nargs="+", type=Path)
    parser.add_argument("--bundle", type=Path, default=ROOT/"weights/wine-recognizer-v5-worker-layout-release")
    parser.add_argument("--ocr-python", type=Path, default=ROOT/".venv-ocr-gpu/bin/python")
    parser.add_argument("--ocr-device", default="gpu:0", help="gpu:N or cpu; match the selected OCR environment")
    parser.add_argument("--memory-policy", choices=["release", "retain"], default="release")
    parser.add_argument("--top-k", type=int, default=10)
    parser.add_argument("--output", type=Path, help="JSON file; otherwise write JSON to stdout")
    args = parser.parse_args()
    with WineRecognizer(args.bundle, ocr_python=args.ocr_python, ocr_device=args.ocr_device,
                        memory_policy=args.memory_policy) as recognizer:
        results = [recognizer.predict(path.read_bytes(), top_k=args.top_k) for path in args.photos]
    encoded = json.dumps(results[0] if len(results) == 1 else results, ensure_ascii=False, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(encoded+"\n")
    else:
        print(encoded)


if __name__ == "__main__":
    main()
