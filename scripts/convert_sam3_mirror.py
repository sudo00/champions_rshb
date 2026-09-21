#!/usr/bin/env python3
"""Convert the user-selected SAM3 safetensors mirror with HF's pinned key mapping.

Only the image detector is needed; video tracker tensors are excluded explicitly.
No remote code or pickled checkpoint is executed. The downloaded HF conversion
module is inspected source pinned by SHA, used for key mapping and QKV splitting.
"""
from __future__ import annotations

import gc
import importlib.util
import json
import os
from pathlib import Path
import sys

sys.dont_write_bytecode = True
from detect_labels_pilot import ROOT, sha256, write_json

BASE = ROOT / "data/audit/label_segmentation"


def main():
    os.environ["HF_HUB_OFFLINE"] = "1"
    os.environ["TRANSFORMERS_OFFLINE"] = "1"
    import torch
    from safetensors import safe_open
    from transformers import CLIPTokenizerFast, Sam3Config, Sam3ImageProcessor, Sam3Model, Sam3Processor

    torch.set_num_threads(4)
    mirror = json.loads((BASE / "sam3_mirror_download.json").read_text())
    source = BASE / "models/sam3-mirror/sam3.safetensors"
    if sha256(source) != mirror["files_sha256"]["sam3.safetensors"]:
        raise ValueError("Mirror checkpoint hash differs")
    provenance = json.loads((BASE / "conversion_source/provenance.json").read_text())
    converter_path = BASE / "conversion_source/convert_sam3_to_hf.py"
    if sha256(converter_path) != provenance["converter_sha256"]:
        raise ValueError("Converter source hash differs")
    output = BASE / "models/sam3-converted"
    if (BASE / "sam3_download.json").exists():
        raise ValueError("Existing converted model preserved; verify or use a separate location")
    spec = importlib.util.spec_from_file_location("pinned_hf_sam3_converter", converter_path)
    converter = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(converter)
    state, excluded = {}, []
    with safe_open(source, framework="pt", device="cpu") as checkpoint:
        for name in checkpoint.keys():
            if name.startswith("detector."):
                state[name.removeprefix("detector.")] = checkpoint.get_tensor(name)
            else:
                excluded.append(name)
    if any(not name.startswith("tracker.") for name in excluded):
        raise ValueError("Unrecognized non-detector tensors")
    mapping = converter.convert_old_keys_to_new_keys(list(state))
    converted = {}
    for name, tensor in state.items():
        new_name = mapping[name]
        if new_name == "vision_encoder.backbone.embeddings.position_embeddings":
            tensor = tensor[:, 1:, :]
        if new_name == "text_encoder.text_projection.weight":
            tensor = tensor.T
        converted[new_name] = tensor
    del state
    converted = converter.split_qkv(converted)
    model = Sam3Model(Sam3Config()).eval()
    expected = model.state_dict()
    missing = sorted(set(expected) - set(converted))
    unexpected = sorted(set(converted) - set(expected))
    mismatched = [k for k in expected if k in converted and expected[k].shape != converted[k].shape]
    print(json.dumps({"missing": missing, "unexpected": unexpected, "shape_mismatch": mismatched}, indent=2), flush=True)
    # HF's Sam3Model implements text/box concept segmentation. The checkpoint
    # also carries tracker feature pyramids and point-prompt projections, which
    # are not modules of this implementation; do not leave random model weights.
    allowed_extra = all(k.endswith("rotary_emb.rope_embeddings")
                        or k.startswith("backbone.vision_backbone.sam2_convs.")
                        or k.startswith(("geometry_encoder.points_direct_project.",
                                         "geometry_encoder.points_pool_project.",
                                         "geometry_encoder.points_pos_enc_project.")) for k in unexpected)
    if missing or mismatched or not allowed_extra:
        raise ValueError("Conversion must account for every inference parameter")
    model.load_state_dict({k: converted[k] for k in expected}, strict=True)
    del converted, expected
    gc.collect()
    model.save_pretrained(output)
    tokenizer = CLIPTokenizerFast.from_pretrained(BASE / "conversion_source/tokenizer", local_files_only=True,
                                                  max_length=32, model_max_length=32)
    processor = Sam3Processor(image_processor=Sam3ImageProcessor(), tokenizer=tokenizer)
    processor.save_pretrained(output)
    manifest = {"model_id": "1038lab/sam3", "revision": mirror["revision"],
                "local_dir": "models/sam3-converted", "conversion": provenance,
                "original_safetensors_sha256": mirror["files_sha256"]["sam3.safetensors"],
                "excluded_video_tracker_keys": excluded,
                "excluded_recomputed_buffers": [k for k in unexpected if k.endswith("rotary_emb.rope_embeddings")],
                "excluded_unused_tracker_pyramid_and_point_prompt_parameters": [k for k in unexpected if not k.endswith("rotary_emb.rope_embeddings")],
                "strict_parameter_load": True, "files_sha256": {p.name: sha256(p) for p in sorted(output.iterdir()) if p.is_file()}}
    write_json(BASE / "sam3_download.json", manifest)
    print(f"Converted and strictly loaded {len(model.state_dict())} tensors: {output}", flush=True)


if __name__ == "__main__":
    main()
