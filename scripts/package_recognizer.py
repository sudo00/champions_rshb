"""Package the tested component and resource bundle without datasets or credentials."""
from __future__ import annotations

import argparse
import io
import json
from pathlib import Path
import sys
import tarfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT/"worker"))
from pipeline.wine_recognizer import file_hash


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle", type=Path, default=ROOT/"data/deployment/wine-recognizer-v4-memory-release")
    parser.add_argument("--output", type=Path, default=ROOT/"data/deployment/wine-recognizer-v4-memory-handoff.tar")
    args = parser.parse_args()
    if args.output.exists():
        raise ValueError("Archive already exists; use a new output")
    manifest = json.loads((args.bundle/"manifest.json").read_text())
    for name, expected in manifest["files_sha256"].items():
        if file_hash(args.bundle/name) != expected:
            raise ValueError("Bundle changed: " + name)
    for name, expected in manifest["code_sha256"].items():
        if file_hash(ROOT/name) != expected:
            raise ValueError("Code changed: " + name)
    sources = list((ROOT/"worker/pipeline").glob("*.py"))
    sources += [ROOT/name for name in (
        "scripts/cylinder_geometry.py", "scripts/label_rectification_pilot.py", "scripts/recognize_photo.py",
        "scripts/benchmark_wine_recognizer.py", "scripts/export_recognizer_bundle.py", "scripts/package_recognizer.py",
        "requirements-vision.lock.txt", "requirements-ocr.lock.txt", "requirements-ocr-gpu.lock.txt", "docs/RECOGNIZER_HANDOFF.md",
        "docs/GPU_OCR_EXPERIMENT.md", "docs/LOCAL_MATCHING_AND_TARGET_SELECTION.md", "scripts/evaluate_gpu_ocr.py",
        "docs/TARGET_SELECTION_V3.md", "docs/VRAM_PLAN.md", "scripts/evaluate_target_selection.py",
        "docs/MEMORY_OPTIMIZATION_V4.md", "docs/SOMMELIER_CAPACITY.md", "scripts/evaluate_memory_policy.py",
        "docs/LIVE_SHOP_EXPERIMENT.md", "tests/test_wine_recognizer.py", "tests/test_hybrid_search.py",
        "tests/test_text_search.py", "tests/test_visual_search.py", "tests/test_label_observations.py",
        "data/audit/recognizer/release_v1/benchmark.json", "data/audit/recognizer/gpu_v2/benchmark.json",
        "data/audit/ocr_gpu/shop76_v1/comparison.json", "data/audit/target_selection/v3_final/comparison.json",
        "data/audit/target_selection/v3_final/068_regions.jpg", "data/audit/target_selection/packaging_replay.json",
        "data/audit/memory_v4/retain/benchmark.json", "data/audit/memory_v4/release/benchmark.json",
        "data/audit/memory_v4/paired_outputs.json", "data/audit/memory_v4/shop76/comparison.json")]
    readme = ("# Wine recognizer prototype\n\n"
              "See docs/RECOGNIZER_HANDOFF.md for setup, measured latency, JSON contract and integration.\n"
              f"Assets: data/deployment/{args.bundle.name}/. Python 3.12, NVIDIA CUDA, two virtualenvs.\n"
              "Run scripts/recognize_photo.py with your own photograph. No datasets or reference image assets included.\n")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tarfile.open(args.output, "w") as archive:
        info = tarfile.TarInfo("wine-recognizer/README.md")
        raw = readme.encode()
        info.size = len(raw)
        archive.addfile(info, io.BytesIO(raw))
        for source in sources:
            archive.add(source, arcname="wine-recognizer/"+str(source.relative_to(ROOT)))
        for relative in [*manifest["files_sha256"], "manifest.json"]:
            archive.add(args.bundle/relative, arcname=f"wine-recognizer/data/deployment/{args.bundle.name}/"+relative)
    checksum = file_hash(args.output)
    args.output.with_suffix(".tar.sha256").write_text(checksum+"  "+args.output.name+"\n")
    print(json.dumps({"path": str(args.output), "bytes": args.output.stat().st_size, "sha256": checksum}, indent=2))


if __name__ == "__main__":
    main()
