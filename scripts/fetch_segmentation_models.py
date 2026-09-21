#!/usr/bin/env python3
"""Download official SAM weights, pin revision and record hashes; no images uploaded."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import sys

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "data/audit/label_segmentation"
MODELS = {"sam2": "facebook/sam2.1-hiera-tiny", "sam3": "facebook/sam3", "sam3_mirror": "1038lab/sam3"}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("model", choices=MODELS)
    args = parser.parse_args()
    if args.model == "sam3_mirror":
        os.environ["HF_HUB_DISABLE_XET"] = "1"
    from huggingface_hub import HfApi, get_token, snapshot_download
    from huggingface_hub.errors import GatedRepoError

    # Use standard local login if available; never print or persist credentials.
    token = get_token() or False
    repo = MODELS[args.model]
    destination = ROOT / "weights/research/label_segmentation" / ("sam3-mirror" if args.model == "sam3_mirror" else repo.split("/")[-1])
    manifest_path = BASE / f"{args.model}_download.json"
    previous = json.loads(manifest_path.read_text()) if manifest_path.exists() else None
    api = HfApi(token=token)
    revision = previous["revision"] if previous else api.model_info(repo).sha
    print(f"Downloading {repo} at {revision}", flush=True)
    try:
        snapshot_download(repo, revision=revision, local_dir=destination,
                          cache_dir=ROOT / "weights/cache/label_segmentation", token=token, max_workers=2,
                          allow_patterns=(["sam3.safetensors", "README.md"] if args.model == "sam3_mirror"
                                          else ["*.json", "*.txt", "*.safetensors", "*.model"]))
    except GatedRepoError:
        BASE.mkdir(parents=True, exist_ok=True)
        (BASE / f"{args.model}_access_status.json").write_text(json.dumps({
            "model_id": repo, "status": "access_required", "revision": revision,
            "action": f"Request official access at https://huggingface.co/{repo} and authenticate locally",
        }, indent=2) + "\n")
        print(f"Official model access required: https://huggingface.co/{repo}", flush=True)
        raise SystemExit(2)
    files = {}
    for path in sorted(destination.rglob("*")):
        if path.is_file() and not any(p.startswith(".") for p in path.relative_to(destination).parts):
            with path.open("rb") as stream:
                files[str(path.relative_to(destination))] = hashlib.file_digest(stream, "sha256").hexdigest()
    if not any(name.endswith(".safetensors") for name in files):
        raise RuntimeError("No model weights downloaded")
    manifest = {"model_id": repo, "revision": revision, "files_sha256": files}
    if previous and previous != manifest:
        raise ValueError("Downloaded model differs from preserved manifest")
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"Verified {len(files)} files: {manifest_path}", flush=True)
    if args.model == "sam3_mirror":
        # Fetch conversion source for inspection, not execution, and tokenizer assets.
        import urllib.request
        source_dir = BASE / "conversion_source"
        source_dir.mkdir(exist_ok=True)
        api_url = "https://api.github.com/repos/huggingface/transformers/commits?path=src/transformers/models/sam3/convert_sam3_to_hf.py&per_page=1"
        with urllib.request.urlopen(api_url) as response:
            commit = json.load(response)[0]["sha"]
        url = f"https://raw.githubusercontent.com/huggingface/transformers/{commit}/src/transformers/models/sam3/convert_sam3_to_hf.py"
        with urllib.request.urlopen(url) as response:
            contents = response.read()
        (source_dir / "convert_sam3_to_hf.py").write_bytes(contents)
        tokenizer_repo = "openai/clip-vit-base-patch32"
        tokenizer_revision = api.model_info(tokenizer_repo).sha
        snapshot_download(tokenizer_repo, revision=tokenizer_revision, local_dir=source_dir / "tokenizer",
                          cache_dir=ROOT / "weights/cache/label_segmentation", token=False,
                          allow_patterns=["tokenizer*", "vocab.json", "merges.txt", "special_tokens_map.json"])
        (source_dir / "provenance.json").write_text(json.dumps({"converter_url": url,
            "converter_sha256": hashlib.sha256(contents).hexdigest(), "tokenizer_repo": tokenizer_repo,
            "tokenizer_revision": tokenizer_revision}, indent=2) + "\n")


if __name__ == "__main__":
    main()
