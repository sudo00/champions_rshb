"""Submit one image to the async recognition API and save its full result."""
import argparse
import base64
import json
from pathlib import Path
import time
import urllib.request


def request(base: str, route: str, payload: dict | None = None) -> dict:
    data = None if payload is None else json.dumps(payload).encode()
    req = urllib.request.Request(base+route, data=data, headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(req, timeout=30) as response:
        return json.load(response)


def main():
    parser=argparse.ArgumentParser(__doc__)
    parser.add_argument('image',type=Path)
    parser.add_argument('--base',default='http://127.0.0.1:8000')
    parser.add_argument('--output',type=Path)
    parser.add_argument('--no-alternatives',action='store_true')
    args=parser.parse_args()
    request(args.base,'/ready')
    accepted=request(args.base,'/v1/wines/scan',{
        'imageBase64':base64.b64encode(args.image.read_bytes()).decode(),
        'includeAlternatives':not args.no_alternatives})
    if not accepted.get('success'): raise RuntimeError(accepted)
    deadline=time.monotonic()+120
    while time.monotonic()<deadline:
        result=request(args.base,'/v1/wines/scan/'+accepted['scanId'])
        if result['status']=='failed': raise RuntimeError(result)
        if result['status']=='done': break
        time.sleep(.2)
    else: raise TimeoutError(f"Scan {accepted['scanId']} is still pending; poll it later")
    text=json.dumps(result,ensure_ascii=False,indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True,exist_ok=True)
        args.output.write_text(text+'\n')
    else: print(text)


if __name__=='__main__': main()
