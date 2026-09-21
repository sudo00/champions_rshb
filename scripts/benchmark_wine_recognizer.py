"""Measure cold initialization and actual uncached requests with co-resident models."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import resource
import subprocess
import sys
import time
import threading

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT/"worker"))
from pipeline.wine_recognizer import WineRecognizer


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle", type=Path, default=ROOT/"weights/wine-recognizer-v4-memory-layout-release")
    parser.add_argument("--output", type=Path, default=ROOT/"data/audit/recognizer/memory_v4")
    parser.add_argument("--ocr-python", type=Path, default=ROOT/".venv-ocr-gpu/bin/python")
    parser.add_argument("--ocr-device", default="gpu:0")
    parser.add_argument("--memory-policy", choices=["release", "retain"], default="release")
    parser.add_argument("--rounds", type=int, default=2)
    parser.add_argument("--numbers", nargs="+", type=int, default=[1, 14, 27, 63, 64])
    args = parser.parse_args()
    if (args.output/"benchmark.json").exists():
        raise ValueError("Choose a fresh benchmark output")
    args.output.mkdir(parents=True, exist_ok=True)
    annotations = json.loads((ROOT/"data/audit/live_shop/v1/annotations.json").read_text())["images"]
    selected = [r for r in annotations if r["number"] in args.numbers]
    rows = []
    memory_samples = []
    sampling_done = threading.Event()
    def sample_gpu():
        while not sampling_done.is_set():
            raw = subprocess.check_output(["nvidia-smi", "--query-gpu=memory.used", "--format=csv,noheader,nounits"], text=True)
            memory_samples.append({"time": time.monotonic(), "gpu_memory_mib": int(raw.splitlines()[0])})
            sampling_done.wait(.25)
    sampler = threading.Thread(target=sample_gpu, daemon=True)
    sampler.start()
    before = subprocess.check_output(["nvidia-smi", "--query-gpu=memory.used,memory.total,name", "--format=csv,noheader,nounits"], text=True).strip()
    with WineRecognizer(args.bundle, ocr_python=args.ocr_python, ocr_device=args.ocr_device,
                        memory_policy=args.memory_policy) as scanner:
        print("Models loaded", scanner.load_seconds, flush=True)
        cold = scanner.predict((ROOT/"data/live_shop_photos"/selected[0]["filename"]).read_bytes())
        (args.output/"first_request.json").write_text(json.dumps(cold, ensure_ascii=False, indent=2))
        print("First request", cold["timings_seconds"], cold["gpu_memory_bytes"], flush=True)
        for repeat in range(args.rounds):
            for annotation in selected:
                # Bytes are decoded and processed again; no cached masks/OCR/embeddings.
                payload = (ROOT/"data/live_shop_photos"/annotation["filename"]).read_bytes()
                started = time.perf_counter()
                result = scanner.predict(payload)
                wall = time.perf_counter()-started
                target = args.output/f'{annotation["number"]:03d}_{repeat}.json'
                target.write_text(json.dumps(result, ensure_ascii=False, indent=2))
                rank = next((i+1 for i, c in enumerate(result["candidates"]) if c["slug"] == annotation["expected_slug"]), None)
                rows.append(dict(number=annotation["number"], repeat=repeat, wall_seconds=wall,
                                 timings=result["timings_seconds"], gpu_memory_bytes=result["gpu_memory_bytes"],
                                 ocr_gpu_memory_bytes=result.get("ocr_gpu_memory_bytes", {}),
                                 expected_rank=rank, top_slug=result["candidates"][0]["slug"] if result["candidates"] else None))
                print(annotation["number"], repeat, round(wall, 3), rank, result["timings_seconds"], flush=True)
        blank = scanner.predict(Image.new("RGB", (1200, 1600), "white"))
        (args.output/"no_target.json").write_text(json.dumps(blank, ensure_ascii=False, indent=2))
        after = subprocess.check_output(["nvidia-smi", "--query-gpu=memory.used,memory.total,name", "--format=csv,noheader,nounits"], text=True).strip()
        sampling_done.set()
        sampler.join(timeout=3)
        values = [r["wall_seconds"] for r in rows]
        report = dict(bundle_id=scanner.manifest["bundle_id"], load_seconds=scanner.load_seconds,
                      memory_policy=args.memory_policy,
                      ocr_runtime=scanner.ocr.runtime,
                      model_parameter_bytes=scanner.model_parameter_bytes,
                      first_request_seconds=cold["timings_seconds"]["total"], requests=len(rows), rows=rows,
                      latency_seconds=dict(median=float(np.median(values)), p95=float(np.percentile(values, 95)),
                                           minimum=min(values), maximum=max(values)),
                      gpu_before=before, gpu_after=after,
                      gpu_system_peak_mib=max(r["gpu_memory_mib"] for r in memory_samples),
                      gpu_sampling_interval_seconds=.25,
                      ocr_process_memory=[line.strip() for line in Path(f"/proc/{scanner.ocr.process.pid}/status").read_text().splitlines() if line.startswith(("VmRSS:", "VmHWM:"))],
                      process_peak_rss_kib=resource.getrusage(resource.RUSAGE_SELF).ru_maxrss,
                      no_target_status=blank["status"],
                      note="Serialized warm requests including decode, SAM3, geometry, SigLIP, OCR and search. Excludes network/queue. Inspected images are not an independent accuracy benchmark.")
        (args.output/"benchmark.json").write_text(json.dumps(report, ensure_ascii=False, indent=2))
        print(json.dumps({k: v for k, v in report.items() if k != "rows"}, ensure_ascii=False, indent=2), flush=True)


if __name__ == "__main__":
    main()
