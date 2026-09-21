"""Paired GPU OCR regression on all shop photos with unchanged retrieval/geometry."""
from __future__ import annotations

import argparse
from collections import Counter
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT/"worker"))
from pipeline.wine_recognizer import WineRecognizer, file_hash


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle", type=Path, default=ROOT/"data/deployment/wine-recognizer-v4-memory-release")
    parser.add_argument("--output", type=Path, default=ROOT/"data/audit/ocr_gpu/shop76_v4")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    base = ROOT/"data/audit/live_shop/v1"
    annotations = json.loads((base/"annotations.json").read_text())["images"]
    cpu = {r["id"]: r for r in json.loads((base/"improved/results.json").read_text())["images"]}
    cpu_ocr = {r["id"]: r for r in json.loads((base/"baseline/ocr.json").read_text())["images"]}
    receipt = dict(bundle_manifest_sha256=file_hash(args.bundle/"manifest.json"),
                   annotations_sha256=file_hash(base/"annotations.json"),
                   baseline_sha256=file_hash(base/"improved/results.json"),
                   script_sha256=file_hash(Path(__file__)))
    path = args.output/"run.json"
    if path.exists() and json.loads(path.read_text()) != receipt:
        raise ValueError("Run inputs changed; use a new output")
    path.write_text(json.dumps(receipt, indent=2))
    rows = []
    with WineRecognizer(args.bundle, ocr_python=ROOT/".venv-ocr-gpu/bin/python", ocr_device="gpu:0") as scanner:
        for a in annotations:
            output = args.output/(a["id"]+".json")
            image = ROOT/"data/live_shop_photos"/a["filename"]
            if file_hash(image) != a["source_sha256"]:
                raise ValueError("Query changed")
            if output.exists():
                result = json.loads(output.read_text())
            else:
                result = scanner.predict(image.read_bytes())
                output.write_text(json.dumps(result, ensure_ascii=False, indent=2))
            before = [c["slug"] for c in cpu[a["id"]]["candidates"]["hybrid_evidence"]]
            after = [c["slug"] for c in result["candidates"]]
            expected = a["expected_slug"]
            old_rank = before.index(expected)+1 if expected in before else None
            new_rank = after.index(expected)+1 if expected in after else None
            old_lines = Counter((l["source"], l["text"]) for l in cpu_ocr[a["id"]]["observations"] if l.get("on_target_bottle") is not False)
            new_lines = Counter((l["source"], l["text"]) for l in result["observations"] if l.get("on_target_bottle") is not False)
            rows.append(dict(id=a["id"], number=a["number"], expected_slug=expected,
                             cpu_rank=old_rank, gpu_rank=new_rank, same_top10=before == after,
                             same_top1=before[:1] == after[:1], same_ocr_text=old_lines == new_lines,
                             removed_ocr=list((old_lines-new_lines).elements()), added_ocr=list((new_lines-old_lines).elements()),
                             status=result["status"], elapsed=result["timings_seconds"]["total"]))
            print(a["number"], a["id"], f"{old_rank}->{new_rank}", round(result["timings_seconds"]["total"], 3), flush=True)
    known = [r for r in rows if r["expected_slug"]]
    metrics = {device: dict(n=len(known), top1=sum(r[device+"_rank"] == 1 for r in known),
                            top10=sum(r[device+"_rank"] is not None for r in known)) for device in ("cpu", "gpu")}
    report = dict(provenance=receipt, metrics=metrics, rows=rows,
                  same_top1=sum(r["same_top1"] for r in rows), same_top10=sum(r["same_top10"] for r in rows),
                  same_ocr_text=sum(r["same_ocr_text"] for r in rows),
                  note="Paired regression on the same inspected development photos; not held-out accuracy. Unresolved identities excluded from rank metrics.")
    (args.output/"comparison.json").write_text(json.dumps(report, ensure_ascii=False, indent=2))
    print(json.dumps({k: v for k, v in report.items() if k != "rows"}, indent=2), flush=True)


if __name__ == "__main__":
    main()
