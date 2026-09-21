"""Compare whole-object selection against the frozen GPU v2 shop responses."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import statistics
import sys

from PIL import Image, ImageDraw, ImageOps

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT/"worker"))
from pipeline.wine_recognizer import WineRecognizer, file_hash


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle", type=Path, default=ROOT/"data/deployment/wine-recognizer-v4-memory-release")
    parser.add_argument("--output", type=Path, default=ROOT/"data/audit/target_selection/v4")
    args = parser.parse_args()
    if args.output.exists():
        raise ValueError("Choose a fresh output directory")
    args.output.mkdir(parents=True)
    annotations = ROOT/"data/audit/live_shop/v1/annotations.json"
    rows = json.loads(annotations.read_text())["images"]
    baseline = ROOT/"data/audit/ocr_gpu/shop76_v1"
    receipt = dict(bundle_sha256=file_hash(args.bundle/"manifest.json"), annotations_sha256=file_hash(annotations),
                   script_sha256=file_hash(Path(__file__)), baseline_sha256={r["id"]:file_hash(baseline/(r["id"]+".json")) for r in rows})
    (args.output/"provenance.json").write_text(json.dumps(receipt, indent=2))
    comparisons = []
    # Diagnose the reported failure first; ordering does not enter model inputs.
    rows = sorted(rows, key=lambda a: (a["number"] != 68, a["number"]))
    with WineRecognizer(args.bundle, ocr_python=ROOT/".venv-ocr-gpu/bin/python") as scanner:
        for row in rows:
            path = ROOT/"data/live_shop_photos"/row["filename"]
            if file_hash(path) != row["source_sha256"]:
                raise ValueError("Query changed")
            before = json.loads((baseline/(row["id"]+".json")).read_text())
            after = scanner.predict(path.read_bytes())
            (args.output/(row["id"]+".json")).write_text(json.dumps(after, ensure_ascii=False, indent=2))
            def rank(result):
                return next((i+1 for i,c in enumerate(result["candidates"]) if c["slug"] == row["expected_slug"]), None)
            def boxes(result):
                return {r["kind"]: r["box_xyxy"] for r in result["regions"]}
            changed = boxes(before) != boxes(after)
            entry = dict(number=row["number"], id=row["id"], expected_slug=row["expected_slug"],
                         before_rank=rank(before), after_rank=rank(after), changed_regions=changed,
                         before_boxes=boxes(before), after_boxes=boxes(after),
                         before_top1=next((c["slug"] for c in before["candidates"]), None),
                         after_top1=next((c["slug"] for c in after["candidates"]), None),
                         seconds=after["timings_seconds"]["total"])
            comparisons.append(entry)
            if changed or row["number"] == 68:
                with Image.open(path) as opened:
                    original = ImageOps.exif_transpose(opened).convert("RGB")
                original.thumbnail((600, 800))
                canvas = Image.new("RGB", (original.width*2, original.height+28), "white")
                for col, result in enumerate((before, after)):
                    panel = original.copy()
                    draw = ImageDraw.Draw(panel)
                    sx, sy = panel.width/result["image_size"][0], panel.height/result["image_size"][1]
                    for region in result["regions"]:
                        color = "lime" if region["kind"] == "object" else "cyan"
                        for polygon in region["polygons"]:
                            points = [(x*sx, y*sy) for x,y in polygon]
                            if points:
                                draw.line(points+points[:1], fill=color, width=3)
                    canvas.paste(panel, (col*original.width, 28))
                ImageDraw.Draw(canvas).text((10, 6), "Before v2                                      After v3", fill="black")
                canvas.save(args.output/f'{row["number"]:03d}_regions.jpg')
            print(row["number"], "changed" if changed else "same", entry["before_rank"], entry["after_rank"], flush=True)
        blank = scanner.predict(Image.new("RGB", (1200, 1600), "white"))
        (args.output/"blank.json").write_text(json.dumps(blank, ensure_ascii=False, indent=2))
    known = [r for r in comparisons if r["expected_slug"]]
    report = dict(rows=comparisons, changed_regions=sum(r["changed_regions"] for r in comparisons),
                  same_top1=sum(r["before_top1"] == r["after_top1"] for r in comparisons),
                  metrics={stage:dict(n=len(known), top1=sum(r[stage+"_rank"] == 1 for r in known),
                                     top10=sum(r[stage+"_rank"] is not None for r in known)) for stage in ("before", "after")},
                  blank_status=blank["status"], median_seconds=statistics.median(r["seconds"] for r in comparisons),
                  note="Development regression, not held-out accuracy; model inputs contain image bytes only.")
    (args.output/"comparison.json").write_text(json.dumps(report, ensure_ascii=False, indent=2))
    print(json.dumps({k:v for k,v in report.items() if k != "rows"}, indent=2), flush=True)


if __name__ == "__main__":
    main()
