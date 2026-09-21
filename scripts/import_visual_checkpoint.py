"""Validate the user-supplied timm DINOv3 Large state dict and store safetensors."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def digest(path: Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('checkpoint', type=Path)
    args = parser.parse_args()
    source = args.checkpoint.resolve()
    output = ROOT / 'weights/research/visual_search/dinov3_large'
    receipt = output.parent / 'dinov3_large_download.json'
    checksum = digest(source)
    if receipt.exists():
        old = json.loads(receipt.read_text())
        if old['source_sha256'] != checksum:
            raise ValueError('Different source checkpoint; use a separately versioned experiment')
        if any(digest(output / name) != sha for name, sha in old['files_sha256'].items()):
            raise ValueError('Stored weights changed')
        print('Existing verified import:', receipt)
        return
    import torch
    import timm
    from safetensors.torch import save_file
    torch.set_num_threads(4)
    state = torch.load(source, map_location='cpu', weights_only=True, mmap=True)
    model = timm.create_model('vit_large_patch16_dinov3', pretrained=False, num_classes=0, global_pool='token')
    model.load_state_dict(state, strict=True)
    output.mkdir(parents=True, exist_ok=True)
    save_file(state, str(output / 'model.safetensors'))
    value = {'model_id': 'local/dinov3_vitl16', 'architecture': 'vit_large_patch16_dinov3', 'revision': None,
             'origin': 'user_download_from_kaggle; upstream equivalence not independently verified',
             'source_path': str(source), 'source_sha256': checksum,
             'files_sha256': {'model.safetensors': digest(output / 'model.safetensors')}}
    receipt.write_text(json.dumps(value, indent=2) + '\n')
    print('Imported', sum(p.numel() for p in model.parameters()), 'parameters:', receipt)


if __name__ == '__main__':
    main()
