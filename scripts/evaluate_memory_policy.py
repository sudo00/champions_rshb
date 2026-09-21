"""Measure full GPU memory and compare release-cache inference to frozen v3."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import statistics
import subprocess
import sys
import threading
import time

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT/"worker"))
from pipeline.wine_recognizer import WineRecognizer, file_hash


def used_mib() -> int:
    value = subprocess.check_output(["nvidia-smi", "--query-gpu=memory.used", "--format=csv,noheader,nounits"], text=True)
    return int(value.splitlines()[0])


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle", type=Path, default=ROOT/"data/deployment/wine-recognizer-v4-memory-release")
    parser.add_argument("--output", type=Path, default=ROOT/"data/audit/memory_v4/shop76")
    args = parser.parse_args()
    if args.output.exists():
        raise ValueError("Choose a fresh output")
    args.output.mkdir(parents=True)
    annotations = ROOT/"data/audit/live_shop/v1/annotations.json"
    images = json.loads(annotations.read_text())["images"]
    baseline = ROOT/"data/audit/target_selection/v3_final"
    receipt = dict(bundle_sha256=file_hash(args.bundle/"manifest.json"), script_sha256=file_hash(Path(__file__)),
                   annotations_sha256=file_hash(annotations), baseline_sha256={a["id"]:file_hash(baseline/(a["id"]+".json")) for a in images})
    (args.output/"provenance.json").write_text(json.dumps(receipt, indent=2))
    samples, sample_errors, rows = [], [], []
    stop = threading.Event()
    def sample():
        while not stop.is_set():
            try:
                samples.append(dict(t=time.monotonic(), mib=used_mib()))
            except Exception as exc:
                sample_errors.append(str(exc))
            stop.wait(.1)
    before_mib = used_mib()
    thread = threading.Thread(target=sample, daemon=True)
    thread.start()
    try:
        with WineRecognizer(args.bundle, ocr_python=ROOT/".venv-ocr-gpu/bin/python", memory_policy="release") as scanner:
            loaded_at = time.monotonic()
            loaded_mib = used_mib()
            for a in images:
                path = ROOT/"data/live_shop_photos"/a["filename"]
                if file_hash(path) != a["source_sha256"]:
                    raise ValueError("Query changed")
                old = json.loads((baseline/(a["id"]+".json")).read_text())
                new = scanner.predict(path.read_bytes())
                (args.output/(a["id"]+".json")).write_text(json.dumps(new, ensure_ascii=False, indent=2))
                keys = ("status", "target", "regions", "observations", "observed_fields", "candidates", "cylinder", "warnings", "message")
                differences = [k for k in keys if old.get(k) != new.get(k)]
                def rank(result):
                    return next((i+1 for i,c in enumerate(result["candidates"]) if c["slug"] == a["expected_slug"]), None)
                entry = dict(id=a["id"], number=a["number"], expected_slug=a["expected_slug"], differences=differences,
                             same_top10=[c["slug"] for c in old["candidates"]] == [c["slug"] for c in new["candidates"]],
                             before_rank=rank(old), after_rank=rank(new), seconds=new["timings_seconds"]["total"],
                             idle_gpu_mib=used_mib())
                rows.append(entry)
                print(a["number"], differences or "identical", round(entry["seconds"], 3), entry["idle_gpu_mib"], flush=True)
            blank = scanner.predict(Image.new("RGB", (1200, 1600), "white"))
            (args.output/"blank.json").write_text(json.dumps(blank, ensure_ascii=False, indent=2))
            idle_mib = used_mib()
    finally:
        stop.set()
        thread.join(timeout=5)
        (args.output/"memory_samples.json").write_text(json.dumps(dict(samples=samples, errors=sample_errors)))
    known = [r for r in rows if r["expected_slug"]]
    report = dict(rows=rows, identical_semantic_outputs=sum(not r["differences"] for r in rows),
                  same_top10=sum(r["same_top10"] for r in rows),
                  metrics={stage:dict(n=len(known), top1=sum(r[stage+"_rank"] == 1 for r in known),
                                     top10=sum(r[stage+"_rank"] is not None for r in known)) for stage in ("before", "after")},
                  median_seconds=statistics.median(r["seconds"] for r in rows), gpu_before_mib=before_mib,
                  gpu_loaded_mib=loaded_mib, gpu_idle_end_mib=idle_mib,
                  gpu_system_peak_mib=max(s["mib"] for s in samples),
                  gpu_inference_peak_mib=max(s["mib"] for s in samples if s["t"] >= loaded_at),
                  max_idle_mib=max(r["idle_gpu_mib"] for r in rows), blank_status=blank["status"],
                  note="Sampled whole-GPU memory includes desktop; 0.1s wait plus nvidia-smi time. Known development photos, not held-out accuracy.")
    (args.output/"comparison.json").write_text(json.dumps(report, ensure_ascii=False, indent=2))
    print(json.dumps({k:v for k,v in report.items() if k != "rows"}, indent=2), flush=True)


if __name__ == "__main__":
    main()
