"""Build an offline photo/Top-5 review with independent human annotations."""
from __future__ import annotations

import argparse
import base64
import hashlib
import io
import json
import os
from pathlib import Path
from urllib.parse import quote

from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1]


def write_report(directory: Path, payload: dict) -> None:
    template_path = ROOT / "scripts/templates/photo_batch_review.html"
    autosave_path = ROOT / "scripts/templates/photo_batch_autosave.js"
    template = template_path.read_text().replace("__AUTOSAVE__", autosave_path.read_text())
    encoded = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).replace("<", "\\u003c").replace("&", "\\u0026")
    output = directory / "review.html"
    temporary = directory / "review.html.tmp"
    temporary.write_text(template.replace("__PAYLOAD__", encoded))
    temporary.replace(output)
    def digest(path: Path) -> str:
        return hashlib.sha256(path.read_bytes()).hexdigest()
    provenance = {"html_sha256": digest(output), "images": len(payload["images"]),
                  "catalog_sha256": payload["manifest"]["catalog_sha256"],
                  "predictions_sha256": {p.name: digest(p) for p in sorted((directory / "predictions").glob("*.json"))},
                  "builder_sha256": digest(Path(__file__)), "template_sha256": digest(template_path),
                  "autosave_sha256": digest(autosave_path)}
    (directory / "review.manifest.json").write_text(json.dumps(provenance, indent=2) + "\n")
    print(json.dumps({"report": str(output), "bytes": output.stat().st_size,
                      "images": len(payload["images"]), "catalogue_cards": len(payload["cards"])}, indent=2))


def refresh_template(directory: Path) -> None:
    """Update only UI code, retaining embedded images and frozen predictions."""
    page = (directory / "review.html").read_text()
    encoded = page.split('<script id="payload" type="application/json">', 1)[1].split('</script>', 1)[0]
    payload = json.loads(encoded)
    manifest = json.loads((directory / "manifest.json").read_text())
    if payload["manifest"] != manifest:
        raise ValueError("Embedded batch differs from manifest")
    previous = json.loads((directory / "review.manifest.json").read_text())
    for name, expected in previous["predictions_sha256"].items():
        if hashlib.sha256((directory / "predictions" / name).read_bytes()).hexdigest() != expected:
            raise ValueError("Prediction changed; rebuild report: " + name)
    write_report(directory, payload)


def thumbnail(path: Path, size: tuple[int, int], quality: int = 82) -> tuple[str, list[int]]:
    with Image.open(path) as source:
        image = ImageOps.exif_transpose(source).convert("RGBA")
        background = Image.new("RGBA", image.size, "white")
        background.alpha_composite(image)
        image = background.convert("RGB")
        dimensions = list(image.size)
        image.thumbnail(size, Image.Resampling.LANCZOS)
        output = io.BytesIO()
        image.save(output, format="JPEG", quality=quality, optimize=True)
        return "data:image/jpeg;base64," + base64.b64encode(output.getvalue()).decode(), dimensions


def build(directory: Path) -> None:
    manifest = json.loads((directory / "manifest.json").read_text())
    catalog_path = directory / "catalog.jsonl"
    if hashlib.sha256(catalog_path.read_bytes()).hexdigest() != manifest["catalog_sha256"]:
        raise ValueError("Frozen catalogue checksum mismatch")
    catalogue = [json.loads(line) for line in catalog_path.read_text().splitlines() if line.strip()]
    known = {r["slug"] for r in catalogue}
    rows, photos, used = [], {}, set()
    for item in manifest["images"]:
        path = directory / "predictions" / (item["image_id"] + ".json")
        if not path.exists():
            raise ValueError("Batch is not finished: " + item["image_id"])
        record = json.loads(path.read_text())
        result = record.get("result")
        if not result or result["status"] not in {"done", "failed"}:
            raise ValueError("Pending prediction: " + item["image_id"])
        if result["status"] == "done" and result["catalogSha256"] != manifest["catalog_sha256"]:
            raise ValueError("Prediction catalogue mismatch")
        source = ROOT / manifest["input_dir"] / item["filename"]
        if hashlib.sha256(source.read_bytes()).hexdigest() != item["sha256"] or record["sha256"] != item["sha256"]:
            raise ValueError("Photograph changed: " + item["filename"])
        photo, size = thumbnail(source, (1600, 1600), quality=88)
        photos[item["image_id"]] = photo
        candidates = result.get("candidates", [])[:5]
        if result.get("imageSize") and result["imageSize"] != size:
            raise ValueError("Image coordinate dimensions differ")
        for candidate in candidates:
            if candidate["slug"] not in known:
                raise ValueError("Unknown candidate slug")
            used.add(candidate["slug"])
        rows.append({**item, "image_size": size, "source_url": quote(os.path.relpath(source, directory), safe="/"),
                     "result_url": quote(os.path.relpath(path, directory), safe="/"),
                     "status": result["status"], "recognition_status": result.get("recognitionStatus"),
                     "error": result.get("error"), "warnings": result.get("warnings", []),
                     "target": result.get("target", {}), "seconds": record["seconds_this_session"],
                     "regions": [{"kind": r["kind"], "box": r["box_xyxy"]} for r in result.get("regions", [])],
                     "observations": [{k: o.get(k) for k in ("text", "source", "confidence", "on_target_bottle")}
                                      for o in result.get("observations", [])],
                     "observed_fields": result.get("observedFields", {}),
                     "candidates": [{k: c.get(k) for k in ("rank", "slug", "score", "conflicts")}
                                    for c in candidates]})
    cards, image_cache = [], {}
    for raw in catalogue:
        filename = Path(raw["reference_path"]).name
        source = ROOT / "data/deployment/catalog-images/images" / filename
        if not source.is_file():
            raise FileNotFoundError(source)
        # Full catalogue search remains available even when the correct wine missed Top-5.
        if filename not in image_cache:
            image_cache[filename] = "catalog_" + str(len(image_cache))
            photos[image_cache[filename]] = thumbnail(source, (320, 480), quality=78)[0]
        cards.append({**{k: raw.get(k) or "" for k in ("slug", "title", "winery", "grapes", "category", "region")},
                      "photo": image_cache[filename], "ambiguous": bool(raw.get("exact_slug_ambiguity"))})
    payload = {"manifest": manifest, "images": rows, "cards": cards, "photos": photos,
               "summary": json.loads((directory / "summary.json").read_text())}
    write_report(directory, payload)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, default=ROOT / "data/audit/organizers_real_photos/v1")
    parser.add_argument("--refresh-template", action="store_true", help="Refresh UI without re-encoding images")
    args = parser.parse_args()
    (refresh_template if args.refresh_template else build)(args.directory.resolve())
