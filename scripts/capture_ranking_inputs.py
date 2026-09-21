"""Freeze image-derived retrieval inputs for CPU ranking comparisons."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from worker.pipeline.wine_recognizer import WineRecognizer, file_hash


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--batch', type=Path, default=ROOT/'data/audit/organizers_real_photos/v1')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--bundle', type=Path, default=ROOT/'weights/wine-recognizer-v5-worker-layout-release')
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=False)
    manifest = json.loads((args.batch/'manifest.json').read_text())
    receipt = dict(batch_manifest_sha256=file_hash(args.batch/'manifest.json'),
                   bundle_manifest_sha256=file_hash(args.bundle/'manifest.json'),
                   script_sha256=file_hash(Path(__file__)), images=[])
    try:
        with WineRecognizer(args.bundle, ocr_python=ROOT/'.venv-ocr-gpu/bin/python') as scanner:
            search = scanner.index.search
            captured = {}
            def capture(vectors, views, observations, **kwargs):
                captured.update(vectors=vectors.copy(), views=views, observations=observations)
                return search(vectors, views, observations, **kwargs)
            scanner.index.search = capture
            for i, item in enumerate(manifest['images'], 1):
                path = ROOT/manifest['input_dir']/item['filename']
                if file_hash(path) != item['sha256']:
                    raise ValueError('Photograph changed: ' + str(path))
                captured.clear()
                result = scanner.predict(path.read_bytes())
                old = json.loads((args.batch/'predictions'/(item['image_id']+'.json')).read_text())['result']
                np.save(args.output/(item['image_id']+'.npy'), captured.pop('vectors', np.empty((0, 0))), allow_pickle=False)
                record = dict(image_id=item['image_id'], sha256=item['sha256'], **captured, result=result)
                output = args.output/(item['image_id']+'.json')
                output.write_text(json.dumps(record, ensure_ascii=False))
                entry = dict(image_id=item['image_id'], json_sha256=file_hash(output),
                             vectors_sha256=file_hash(args.output/(item['image_id']+'.npy')),
                             same_top5=[c['slug'] for c in result['candidates'][:5]] == [c['slug'] for c in old['candidates']],
                             same_observations=result['observations'] == old['observations'])
                receipt['images'].append(entry)
                print(i, item['image_id'], entry['same_top5'], entry['same_observations'], flush=True)
    finally:
        (args.output/'receipt.json').write_text(json.dumps(receipt, indent=2))


if __name__ == '__main__':
    main()
