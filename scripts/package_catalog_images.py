"""Export the reviewed catalogue's reference images for API card display."""
import argparse
import hashlib
import json
from pathlib import Path
import tarfile

ROOT=Path(__file__).resolve().parents[1]


def main():
    parser=argparse.ArgumentParser(__doc__)
    parser.add_argument('--output',type=Path,default=ROOT/'data/deployment/catalog-images-v1.tar')
    args=parser.parse_args()
    if args.output.exists(): raise FileExistsError(args.output)
    catalog=ROOT/'backend/catalog/catalog.jsonl'
    rows=[json.loads(line) for line in catalog.read_text().splitlines()]
    paths={Path(row['reference_path']).name for row in rows}
    images=ROOT/'data/catalog/curated/images'
    for name in paths:
        if not (images/name).is_file(): raise FileNotFoundError(images/name)
    for row in rows:
        # Catalogue checksum describes the source; two AVIF sources have PNG display copies.
        with (ROOT/row['reference_source_path']).open('rb') as stream:
            if hashlib.file_digest(stream,'sha256').hexdigest() != row['reference_sha256']:
                raise ValueError('Reference image changed: '+row['slug'])
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with tarfile.open(args.output,'w',dereference=True) as archive:
        for name in sorted(paths): archive.add(images/name,arcname='images/'+name)
    with args.output.open('rb') as stream:
        digest=hashlib.file_digest(stream,'sha256').hexdigest()
    file_hashes={}
    for name in sorted(paths):
        with (images/name).open('rb') as stream:
            file_hashes[name]=hashlib.file_digest(stream,'sha256').hexdigest()
    receipt={'files_sha256':file_hashes,'archive':args.output.name,'sha256':digest,'catalog_sha256':hashlib.sha256(catalog.read_bytes()).hexdigest(),
             'cards':len(rows),'images':len(paths),'bytes':args.output.stat().st_size}
    args.output.with_suffix('.manifest.json').write_text(json.dumps(receipt,indent=2)+'\n')
    print(json.dumps({k:v for k,v in receipt.items() if k!='files_sha256'},indent=2))


if __name__=='__main__': main()
