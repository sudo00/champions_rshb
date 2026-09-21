"""Measure a warm SigLIP query encoder on the three eval images, without SAM/OCR."""
from __future__ import annotations

import argparse
from pathlib import Path
import statistics
import time

import numpy as np
from PIL import Image, ImageOps

from label_rectification_pilot import ROOT, digest, read, write

BASE = ROOT / "data/audit/visual_search"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repeats", type=int, default=10)
    args = parser.parse_args()
    if args.repeats < 1:
        parser.error("repeats must be positive")
    import torch
    from transformers import SiglipVisionModel

    base = BASE / "siglip2_v1"
    encoding, config = read(base / "encoding.json"), read(base / "config.json")
    for filename, key in [("features.npy", "features_sha256"), ("views.json", "views_sha256"),
                          ("config.json", "config_sha256")]:
        if digest(base / filename) != encoding[key]:
            raise ValueError("Changed embedding artifact: " + filename)
    model_dir = BASE / "models/siglip2"
    receipt = BASE / "models/siglip2_download.json"
    if digest(receipt) != config["model_download_sha256"]:
        raise ValueError("Different model weights")
    for filename, checksum in read(receipt)["files_sha256"].items():
        if digest(model_dir / filename) != checksum:
            raise ValueError("Changed model file: " + filename)
    if not torch.cuda.is_available():
        raise RuntimeError("CUDA required")
    torch.set_num_threads(4)
    torch.backends.cuda.matmul.allow_tf32 = False
    model, loading = SiglipVisionModel.from_pretrained(model_dir, local_files_only=True, output_loading_info=True)
    if loading.get("missing_keys") or loading.get("mismatched_keys") or loading.get("error_msgs"):
        raise ValueError("Incomplete vision weights")
    model = model.eval().cuda()
    views = read(base / "views.json")
    reference = np.load(base / "features.npy", allow_pickle=False)

    def prepare(selected: list[dict]):
        batch = []
        for view in selected:
            path = BASE / "gallery_v2" / view["path"]
            if digest(path) != view["sha256"]:
                raise ValueError("Query crop changed")
            with Image.open(path) as opened:
                image = ImageOps.pad(opened.convert("RGB"), (384, 384), method=Image.Resampling.BICUBIC, color="white")
                tensor = torch.from_numpy(np.array(image).copy()).permute(2, 0, 1).float() / 255
                batch.append((tensor - .5) / .5)
        return torch.stack(batch).cuda()

    results = []
    for identifier in ("query_019c68d0", "query_02eef911", "query_096ca74e"):
        indices = [i for i, view in enumerate(views) if view["record_id"] == identifier]
        if not indices:
            raise ValueError("Missing query")
        selected = [views[i] for i in indices]
        batch = prepare(selected)
        with torch.inference_mode(), torch.autocast("cuda", dtype=torch.bfloat16):
            for _ in range(3):
                features = model(pixel_values=batch).pooler_output.float()
        torch.cuda.synchronize()
        vectors = torch.nn.functional.normalize(features, dim=-1).cpu().numpy()
        cosines = np.sum(vectors * reference[indices], axis=1)
        if min(cosines) < .999:
            raise ValueError("Query embeddings differ from indexed reference")
        torch.cuda.reset_peak_memory_stats()
        measures = []
        for _ in range(args.repeats):
            torch.cuda.synchronize()
            started = time.perf_counter()
            batch = prepare(selected)
            torch.cuda.synchronize()
            prepared = time.perf_counter()
            with torch.inference_mode(), torch.autocast("cuda", dtype=torch.bfloat16):
                vectors = torch.nn.functional.normalize(model(pixel_values=batch).pooler_output.float(), dim=-1)
            torch.cuda.synchronize()
            forwarded = time.perf_counter()
            vectors.cpu().numpy()
            finished = time.perf_counter()
            measures.append({"prepare_and_upload_ms": (prepared - started) * 1000,
                             "gpu_forward_and_normalize_ms": (forwarded - prepared) * 1000,
                             "total_encoder_ms": (finished - started) * 1000})
        results.append({"id": identifier, "views": len(indices), "families": [v["family"] for v in selected],
                        "min_cosine_against_index": float(min(cosines)),
                        "median_ms": {key: statistics.median(m[key] for m in measures) for key in measures[0]},
                        "measurements": measures, "peak_allocated_bytes": torch.cuda.max_memory_allocated()})
    path = base / "query_latency.json"
    write(path, {"script_sha256": digest(Path(__file__)), "encoding_sha256": digest(base / "encoding.json"),
                 "gpu": torch.cuda.get_device_name(), "repeats": args.repeats,
                 "vision_parameters": sum(parameter.numel() for parameter in model.parameters()),
                 "precision": "FP32 weights, BF16 autocast; TF32 disabled",
                 "scope": "Warm encoder only: saved crop decoding, SHA, resize, upload, forward and download. Excludes SAM, geometry, OCR, search and model loading.",
                 "images": results})
    print(path)
    for row in results:
        print(row["id"], row["views"], row["median_ms"])


if __name__ == "__main__":
    main()
