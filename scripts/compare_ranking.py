"""Replay frozen image features; use manual labels only for evaluation."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import sys

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from worker.pipeline.hybrid_search import HybridIndex, VERSION


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inputs', type=Path, default=ROOT/'data/audit/organizers_real_photos/ranking_inputs_v4')
    parser.add_argument('--bundle', type=Path, default=ROOT/'weights/wine-recognizer-v4-memory-layout-release')
    parser.add_argument('--annotations', type=Path, default=ROOT/'data/audit/orgs_checks/organizers_annotations.json')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    receipt = json.loads((args.inputs/'receipt.json').read_text())
    annotations = json.loads(args.annotations.read_text())
    labels = {r['image_id']: r for r in annotations['images']}
    manifest = json.loads((args.bundle/'manifest.json').read_text())
    for name in ('catalog.jsonl', 'features.npy', 'views.json'):
        if digest(args.bundle/name) != manifest['files_sha256'][name]:
            raise ValueError('Gallery artifact changed: '+name)
    if annotations['catalog_sha256'] != manifest['catalog_sha256']:
        raise ValueError('Annotation catalogue mismatch')
    if len(receipt['images']) != len(labels) or {r['image_id'] for r in receipt['images']} != labels.keys():
        raise ValueError('Captured inputs must cover exactly the annotated batch')
    if not all(r['same_top5'] and r['same_observations'] for r in receipt['images']):
        raise ValueError('Capture differs from frozen baseline')
    cards = [json.loads(line) for line in (args.bundle/'catalog.jsonl').read_text().splitlines()]
    lookup = {r['slug']:r for r in cards}
    index = HybridIndex(np.load(args.bundle/'features.npy', allow_pickle=False),
                        json.loads((args.bundle/'views.json').read_text()), cards)
    rows = []
    for item in receipt['images']:
        path = args.inputs/(item['image_id']+'.json')
        if digest(path) != item['json_sha256'] or digest(path.with_suffix('.npy')) != item['vectors_sha256']:
            raise ValueError('Frozen retrieval inputs changed: '+item['image_id'])
        captured = json.loads(path.read_text())
        label = labels[item['image_id']]
        if captured['sha256'] != label['sha256']:
            raise ValueError('Annotation photo differs: '+item['image_id'])
        result = index.search(np.load(path.with_suffix('.npy'), allow_pickle=False),
                              captured.get('views', []), captured.get('observations', []),
                              target_detected=bool(captured.get('views')), limit=10)
        before = captured['result']['candidates'][:5]
        after = result['candidates'][:5]
        def rank(candidates):
            return next((i for i,c in enumerate(candidates,1) if c['slug'] == label['expected_slug']), None)
        winery = result.get('producer', {}).get('winery')
        rows.append(dict(image_id=item['image_id'], filename=label['filename'], status=label['status'],
                         expected_slug=label['expected_slug'], before_rank=rank(before), after_rank=rank(after),
                         producer=result.get('producer'),
                         before=[dict(slug=c['slug'], winery=lookup[c['slug']]['winery']) for c in before],
                         after=[dict(**c, winery=lookup[c['slug']]['winery']) for c in after],
                         before_same_producer=sum(lookup[c['slug']]['winery'] == winery for c in before) if winery else None,
                         after_same_producer=sum(lookup[c['slug']]['winery'] == winery for c in after) if winery else None))
    known = [r for r in rows if r['status'] == 'matched']
    metrics = {stage: dict(n=len(known), top1=sum(r[stage+'_rank'] == 1 for r in known),
                          top5=sum(r[stage+'_rank'] is not None for r in known)) for stage in ('before','after')}
    result = dict(version=VERSION, metrics=metrics,
                  top1_changed=sum(r['before'][0]['slug'] != r['after'][0]['slug'] for r in rows),
                  top5_changed=sum([c['slug'] for c in r['before']] != [c['slug'] for c in r['after']] for r in rows),
                  recognized_producer=sum(bool(r.get('producer', {}).get('winery')) for r in rows),
                  regression_ids=[r['image_id'] for r in known if r['before_rank']==1 and r['after_rank']!=1],
                  annotations_sha256=digest(args.annotations), capture_receipt_sha256=digest(args.inputs/'receipt.json'),
                  code_sha256={name:digest(ROOT/'worker/pipeline'/name) for name in ('hybrid_search.py','producer_search.py','text_search.py','visual_search.py')},
                  note='Development comparison on fixed image-derived features; human labels never enter search.', rows=rows)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n')
    print(json.dumps({key:value for key,value in result.items() if key!='rows'}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
