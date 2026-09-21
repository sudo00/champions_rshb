#!/usr/bin/env python3
"""Download the public Grounding DINO Tiny checkpoint into this repository.

Only model files are requested; no input photographs are uploaded.
The resolved Hub revision and local file hashes are retained for reproducibility.
"""

from __future__ import annotations

import hashlib
import importlib.metadata
import json
import os
from pathlib import Path
import sys

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "data/audit/label_detection"
MODEL_ID = "IDEA-Research/grounding-dino-tiny"
MODEL_DIR = ROOT / "weights/research/label_detection/grounding-dino-tiny"
os.environ.setdefault("HF_HOME", str(ROOT / "weights/cache/label_detection/huggingface"))
os.environ.setdefault("HF_HUB_DISABLE_IMPLICIT_TOKEN", "1")
os.environ.setdefault("HF_HUB_DOWNLOAD_TIMEOUT", "60")
os.environ.setdefault("HF_HUB_ETAG_TIMEOUT", "20")


def main() -> None:
    from huggingface_hub import HfApi, snapshot_download

    manifest_path = OUTPUT / "model_download.json"
    previous = json.loads(manifest_path.read_text()) if manifest_path.exists() else None
    revision = previous["revision"] if previous else HfApi().model_info(MODEL_ID, token=False).sha
    print(f"Downloading {MODEL_ID} at {revision} to {MODEL_DIR}", flush=True)
    snapshot_download(
        repo_id=MODEL_ID,
        revision=revision,
        local_dir=MODEL_DIR,
        allow_patterns=["*.json", "*.txt", "*.safetensors"],
        token=False,
        max_workers=2,
    )
    files = {}
    for path in sorted(MODEL_DIR.iterdir()):
        if path.is_file() and path.suffix in {".json", ".txt", ".safetensors"}:
            with path.open("rb") as stream:
                files[path.name] = hashlib.file_digest(stream, "sha256").hexdigest()
    if not any(name.endswith(".safetensors") for name in files):
        raise RuntimeError("No safetensors weights downloaded")
    manifest = {
        "model_id": MODEL_ID,
        "revision": revision,
        "source": f"https://huggingface.co/{MODEL_ID}/tree/{revision}",
        "files_sha256": files,
        "huggingface_hub_version": importlib.metadata.version("huggingface_hub"),
    }
    if previous and (previous["revision"] != revision or previous["files_sha256"] != files):
        raise RuntimeError("Model provenance changed")
    temporary = manifest_path.with_suffix(".json.tmp")
    temporary.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    temporary.replace(manifest_path)
    print(f"Verified {len(files)} model files; metadata: {manifest_path}", flush=True)


if __name__ == "__main__":
    main()
