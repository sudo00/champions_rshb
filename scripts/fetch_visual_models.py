"""Fetch official visual encoders at pinned revisions; never print credentials."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'weights/research/visual_search'
MODELS={'dinov3':'facebook/dinov3-vitb16-pretrain-lvd1689m',
        'dinov3_timm':'timm/vit_base_patch16_dinov3_qkvb.lvd1689m',
        'siglip2':'google/siglip2-so400m-patch14-384'}


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('model',choices=MODELS)
    parser.add_argument('--http',action='store_true',help='Use resumable HTTP if Xet stalls')
    args=parser.parse_args()
    if args.http:os.environ['HF_HUB_DISABLE_XET']='1'
    from huggingface_hub import HfApi,get_token,snapshot_download
    from huggingface_hub.errors import GatedRepoError
    token=get_token() or False;repo=MODELS[args.model];BASE.mkdir(parents=True,exist_ok=True)
    record=BASE/(args.model+'_download.json');old=json.loads(record.read_text()) if record.exists() else None
    revision=old['revision'] if old else HfApi(token=token).model_info(repo).sha
    print('Official model:',repo,'revision:',revision,flush=True)
    try:
        snapshot_download(repo,revision=revision,local_dir=BASE/args.model,cache_dir=BASE/'cache',token=token,
                          allow_patterns=['*.json','*.safetensors','*.model','*.txt','README.md'],max_workers=2)
    except GatedRepoError:
        (BASE/(args.model+'_access.json')).write_text(json.dumps({'repo':repo,'revision':revision,'status':'official_access_required'},indent=2)+'\n')
        print('Official access required:',repo,flush=True);raise SystemExit(2)
    files={}
    for p in sorted((BASE/args.model).rglob('*')):
        if p.is_file() and not any(x.startswith('.') for x in p.relative_to(BASE/args.model).parts):
            with p.open('rb') as f:files[str(p.relative_to(BASE/args.model))]=hashlib.file_digest(f,'sha256').hexdigest()
    if not any(p.endswith('.safetensors') for p in files):raise ValueError('No weights')
    value={'model_id':repo,'revision':revision,'files_sha256':files}
    if old and old!=value:raise ValueError('Existing model manifest differs')
    record.write_text(json.dumps(value,indent=2)+'\n');print('Verified',record,flush=True)


if __name__=='__main__':main()
