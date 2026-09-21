#!/usr/bin/env python3
"""Inspect Grounding DINO token scores; no manual ROI is used for inference."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import sys
import time

sys.dont_write_bytecode = True

from detect_labels_pilot import ROOT, DEFAULT_MODEL, IMAGE_EXTENSIONS, inspect_source, sha256, write_json


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, default=ROOT / "data/eval/queries")
    parser.add_argument("--output", type=Path,
                        default=ROOT / "data/audit/label_detection/token_diagnostic/results.json")
    parser.add_argument("--model-path", type=Path, default=DEFAULT_MODEL)
    args = parser.parse_args()
    if args.output.exists():
        raise ValueError(f"Choose a new output path; existing diagnostic preserved: {args.output}")
    os.environ["HF_HUB_OFFLINE"] = "1"
    os.environ["TRANSFORMERS_OFFLINE"] = "1"
    os.environ["HF_HOME"] = str(ROOT / "data/audit/label_detection/cache/huggingface")
    from PIL import Image, ImageOps
    import torch
    import transformers
    from transformers import AutoModelForZeroShotObjectDetection, AutoProcessor

    torch.set_num_threads(4)
    processor = AutoProcessor.from_pretrained(args.model_path, local_files_only=True)
    model = AutoModelForZeroShotObjectDetection.from_pretrained(args.model_path, local_files_only=True).eval()
    result = {"model_path": str(args.model_path.resolve()),
              "model_files_sha256": {str(p.relative_to(args.model_path)): sha256(p)
                                     for p in sorted(args.model_path.rglob("*")) if p.is_file()
                                     and not any(part.startswith(".") for part in p.relative_to(args.model_path).parts)},
              "torch": torch.__version__, "transformers": transformers.__version__,
              "device": "cpu", "threads": 4, "threshold": 0.25,
              "manual_annotations_used_for_inference": False,
              "coordinate_system": "exif_oriented_original_pixels", "passes": []}
    for path in sorted(args.input.iterdir()):
        if not path.is_file() or path.suffix.lower() not in IMAGE_EXTENSIONS:
            continue
        source = inspect_source(path)
        with Image.open(path) as opened:
            image = ImageOps.exif_transpose(opened).convert("RGB")
        for prompt in ("wine label.", "bottle label."):
            started = time.perf_counter()
            inputs = processor(images=image, text=prompt, return_tensors="pt")
            with torch.inference_mode():
                outputs = model(**inputs)
            probs = outputs.logits[0].sigmoid()
            maxima = probs.max(dim=-1).values
            tokens = processor.tokenizer.convert_ids_to_tokens(inputs.input_ids[0].tolist())
            boxes = []
            for query_index in (maxima > result["threshold"]).nonzero().flatten().tolist():
                cx, cy, width, height = outputs.pred_boxes[0, query_index].tolist()
                boxes.append({"query_index": query_index,
                              "box_xyxy": [(cx-width/2)*image.width, (cy-height/2)*image.height,
                                           (cx+width/2)*image.width, (cy+height/2)*image.height],
                              "max_score": maxima[query_index].item(),
                              "token_scores": [{"index": i, "token": token,
                                                "score": probs[query_index, i].item()}
                                               for i, token in enumerate(tokens)]})
            result["passes"].append({"source": source, "prompt": prompt,
                                     "inference_seconds_including_prepost": time.perf_counter()-started,
                                     "candidates_before_nms": boxes})
            print(f"{path.name}: {prompt} {len(boxes)} candidates", flush=True)
    write_json(args.output, result)
    print(json.dumps({"output": str(args.output), "passes": len(result["passes"])}, ensure_ascii=False))


if __name__ == "__main__":
    main()
