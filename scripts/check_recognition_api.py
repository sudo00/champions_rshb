"""Compare real queued API results with an existing recognizer regression run."""
import argparse
import base64
import json
from pathlib import Path
import statistics
import time
import urllib.request


def request(base, route, payload=None):
    data = None if payload is None else json.dumps(payload).encode()
    req = urllib.request.Request(base+route, data=data, headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(req, timeout=30) as response:
        return json.load(response)


def canonical_candidates(items):
    # Equally weighted textual evidence may arrive in a different order between
    # Python processes. Candidate order, scores and each evidence object matter.
    return [{**item, 'evidence':sorted(item.get('evidence', []),
             key=lambda value:json.dumps(value,sort_keys=True,ensure_ascii=False))} for item in items]


def main():
    parser=argparse.ArgumentParser(__doc__)
    parser.add_argument('--base',default='http://127.0.0.1:3000')
    parser.add_argument('--manifest',type=Path,default=Path('data/audit/live_shop/v1/gallery/manifest.json'))
    parser.add_argument('--baseline',type=Path,default=Path('data/audit/memory_v4/shop76'))
    parser.add_argument('--output',type=Path,default=Path('data/audit/api_integration/shop76'))
    args=parser.parse_args()
    args.output.mkdir(parents=True,exist_ok=True)
    assert request(args.base,'/ready')['status']=='ready'
    report=[]
    entries=json.loads(args.manifest.read_text())['entries']
    for item in entries:
        if item.get('role')!='query': continue
        raw=json.loads((args.baseline/(item['id']+'.json')).read_text())
        payload={'imageBase64':base64.b64encode(Path(item['path']).read_bytes()).decode(),'includeAlternatives':True}
        started=time.monotonic()
        job=request(args.base,'/v1/wines/scan',payload)
        while time.monotonic()-started<60:
            result=request(args.base,'/v1/wines/scan/'+job['scanId'])
            if result['status'] in ('done','failed'): break
            time.sleep(.1)
        elapsed=time.monotonic()-started
        (args.output/(item['id']+'.json')).write_text(json.dumps(result,ensure_ascii=False,indent=2))
        assert result['status']=='done',result
        candidates=[{k:v for k,v in c.items() if k not in ('rank','wine')} for c in result['candidates']]
        same=canonical_candidates(candidates)==canonical_candidates(raw['candidates'][:5]) and result['observations']==raw['observations'] and result['observedFields']==raw['observed_fields'] and result['regions']==raw['regions']
        report.append({'id':item['id'],'seconds':elapsed,'same_semantics':same,'slug':result['slug']})
        print(f"{len(report)}/{len(entries)} {item['id']} parity={same} {elapsed:.2f}s",flush=True)
    summary={'count':len(report),'same_semantics':sum(r['same_semantics'] for r in report),
             'median_seconds':statistics.median(r['seconds'] for r in report),
             'max_seconds':max(r['seconds'] for r in report),'results':report}
    (args.output/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2))
    assert summary['same_semantics']==summary['count'],summary


if __name__=='__main__': main()
