"""Freeze an unlabelled photo batch and save resumable Top-5 API predictions.

Only image bytes are sent to the recognizer; filenames are review metadata.
Predictions never populate the independent human annotations.
"""
from __future__ import annotations

import argparse
import base64
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import statistics
import subprocess
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")
    temporary.replace(path)


def request(base: str, route: str, payload: dict | None = None) -> dict:
    data = None if payload is None else json.dumps(payload).encode()
    req = urllib.request.Request(base.rstrip("/") + route, data=data,
                                 headers={"Content-Type": "application/json"})
    # This local/bespoke API must not accidentally be routed through an HTTP proxy.
    with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(req, timeout=30) as response:
        return json.load(response)


def run(args: argparse.Namespace) -> None:
    ready = request(args.base, "/ready")
    if ready.get("status") != "ready":
        raise RuntimeError(ready)
    files = sorted(p for p in args.input.rglob("*") if p.is_file()
                   and p.suffix.lower() in {".jpg", ".jpeg", ".png", ".webp", ".avif"})
    if not files:
        raise ValueError("No input photographs")
    images = []
    for index, path in enumerate(files, 1):
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        images.append({"image_id": f"org_{index:03d}_{digest[:12]}",
                       "filename": path.relative_to(args.input).as_posix(),
                       "sha256": digest, "bytes": path.stat().st_size})
    manifest_path = args.output / "manifest.json"
    if manifest_path.exists():
        manifest = json.loads(manifest_path.read_text())
        if manifest["images"] != images or manifest["catalog_sha256"] != ready["catalogSha256"]:
            raise ValueError("Inputs/catalogue changed; use a new output directory")
    else:
        manifest = {"schema_version": 1, "created_at": datetime.now(timezone.utc).isoformat(),
                    "input_dir": args.input.resolve().relative_to(ROOT).as_posix(),
                    "git_commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
                    "runner_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                    "catalog_sha256": ready["catalogSha256"], "catalog_count": ready["catalogCount"],
                    "includeAlternatives": True, "images": images}
        write_json(manifest_path, manifest)
        (args.output / "catalog.jsonl").write_bytes((ROOT / "backend/catalog/catalog.jsonl").read_bytes())
    annotation_path = args.output / "annotations.template.json"
    if not annotation_path.exists():
        write_json(annotation_path, {"schema_version": 1, "catalog_sha256": manifest["catalog_sha256"],
            "images": [{"image_id": r["image_id"], "filename": r["filename"], "sha256": r["sha256"],
                        "status": "not_reviewed", "expected_slug": "", "acceptable_slugs": [],
                        "notes": ""} for r in images]})
    for index, item in enumerate(images, 1):
        output = args.output / "predictions" / (item["image_id"] + ".json")
        state = json.loads(output.read_text()) if output.exists() else {}
        if state.get("result", {}).get("status") in {"done", "failed"}:
            print(f"{index}/{len(images)} {item['image_id']} saved", flush=True)
            continue
        started = time.monotonic()
        if not state:
            raw = (args.input / item["filename"]).read_bytes()
            job = request(args.base, "/v1/wines/scan", {
                "imageBase64": base64.b64encode(raw).decode(), "includeAlternatives": True})
            if not job.get("success") or not job.get("scanId"):
                raise RuntimeError(job)
            state = {"image_id": item["image_id"], "sha256": item["sha256"],
                     "scan_id": job["scanId"], "submitted_at": datetime.now(timezone.utc).isoformat()}
            write_json(output, state)
        else:
            state["resumed"] = True
        while time.monotonic() - started < args.timeout:
            result = request(args.base, "/v1/wines/scan/" + state["scan_id"])
            if result["status"] in {"done", "failed"}:
                state["result"] = result
                state["seconds_this_session"] = time.monotonic() - started
                write_json(output, state)
                if result["status"] == "done" and result["catalogSha256"] != manifest["catalog_sha256"]:
                    raise RuntimeError("Runtime catalogue changed during batch")
                print(f"{index}/{len(images)} {item['filename']} {result['status']} "
                      f"{state['seconds_this_session']:.2f}s {result.get('slug')}", flush=True)
                break
            time.sleep(.25)
        else:
            raise TimeoutError(f"Pending scan {state['scan_id']}; rerun to resume polling without resubmission")
    records = [json.loads((args.output / "predictions" / (r["image_id"] + ".json")).read_text()) for r in images]
    times = [r["seconds_this_session"] for r in records if not r.get("resumed")]
    write_json(args.output / "summary.json", {
        "images": len(records), "done": sum(r["result"]["status"] == "done" for r in records),
        "failed": sum(r["result"]["status"] == "failed" for r in records),
        "no_target": sum(r["result"].get("recognitionStatus") == "no_target" for r in records),
        "versions": sorted({r["result"]["version"] for r in records if r["result"].get("version")}),
        "timing_samples": len(times), "median_seconds": statistics.median(times) if times else None,
        "max_seconds": max(times) if times else None,
        "accuracy": None, "note": "Unlabelled batch: candidates are predictions, not verified identities."})


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, default=ROOT / "data/real_photos_from_orgs")
    parser.add_argument("--output", type=Path, default=ROOT / "data/audit/organizers_real_photos/v1")
    parser.add_argument("--base", default="http://127.0.0.1:8000")
    parser.add_argument("--timeout", type=float, default=180)
    run(parser.parse_args())
