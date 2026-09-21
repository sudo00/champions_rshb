"""Export a portable, gallery-only resource bundle for WineRecognizer."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import sys

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "worker"))
from pipeline.wine_recognizer import VERSION, file_hash

CODE_FILES = [ROOT/"worker/pipeline"/n for n in (
    "wine_recognizer.py", "prototype_ocr.py", "hybrid_search.py", "producer_search.py",
    "text_search.py", "visual_search.py", "label_observations.py", "rectification.py",
    "cylinder_geometry.py")]


def repackage(source: Path, output: Path) -> None:
    """Reuse verified gallery/model assets when only inference code changes."""
    if output.exists():
        raise ValueError("Choose a new bundle directory; existing bundles are immutable")
    previous = json.loads((source/"manifest.json").read_text())
    for relative, expected in previous["files_sha256"].items():
        path = (source/relative).resolve()
        if not path.is_relative_to(source.resolve()) or file_hash(path) != expected:
            raise ValueError("Source bundle integrity mismatch: " + relative)
    if file_hash(source/"catalog.jsonl") != previous["catalog_sha256"]:
        raise ValueError("Source catalogue checksum mismatch")
    output.mkdir(parents=True)
    for relative in previous["files_sha256"]:
        target = output/relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source/relative, target)
        if file_hash(target) != previous["files_sha256"][relative]:
            raise ValueError("Copied resource checksum mismatch: " + relative)
    manifest = {**previous, "version": VERSION,
                "code_sha256": {str(p.relative_to(ROOT)): file_hash(p) for p in CODE_FILES},
                "parent_manifest_sha256": file_hash(source/"manifest.json"),
                "note": "Verified gallery/model assets reused; versioned inference code updated. No evaluation photos or labels."}
    identity = {key: manifest[key] for key in ("version", "files_sha256", "code_sha256")}
    manifest["bundle_id"] = hashlib.sha256(json.dumps(identity, sort_keys=True).encode()).hexdigest()
    (output/"manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2)+"\n")
    print(json.dumps({"path": str(output), "bundle_id": manifest["bundle_id"], "version": VERSION}, indent=2))


def export(output: Path) -> None:
    if output.exists():
        raise ValueError("Choose a new bundle directory; existing bundles are immutable")
    gallery = ROOT / "data/audit/visual_search/siglip2_v1"
    catalog = ROOT / "data/catalog/curated/catalog.jsonl"
    receipt = json.loads((gallery/"encoding.json").read_text())
    for name in ("features.npy", "views.json", "config.json"):
        key = {"features.npy": "features", "views.json": "views", "config.json": "config"}[name]+"_sha256"
        if file_hash(gallery/name) != receipt[key]:
            raise ValueError("Changed index artifact: " + name)
    config = json.loads((gallery/"config.json").read_text())
    if config["model"] != "siglip2" or config["side"] != 384 or config["resize"] != "letterbox_white_bicubic_no_crop":
        raise ValueError("Unsupported encoder configuration")
    baseline = json.loads((ROOT/"data/audit/live_shop/v1/improved/results.json").read_text())
    if file_hash(catalog) != baseline["catalog_sha256"]:
        raise ValueError("Catalogue no longer matches the validated baseline")
    models = {
        "sam3": ROOT / "weights/research/label_segmentation/sam3-converted",
        "siglip2": ROOT / "weights/research/visual_search/siglip2",
    }
    receipts = {"sam3": ROOT/"data/audit/label_segmentation/sam3_download.json",
                "siglip2": ROOT/"weights/research/visual_search/siglip2_download.json"}
    if file_hash(receipts["siglip2"]) != config["model_download_sha256"]:
        raise ValueError("Encoder weights and index differ")
    for kind, path in receipts.items():
        for name, sha in json.loads(path.read_text())["files_sha256"].items():
            if file_hash(models[kind]/name) != sha:
                raise ValueError("Changed weights: " + kind + "/" + name)
    output.mkdir(parents=True)
    try:
        shutil.copyfile(catalog, output/"catalog.jsonl")
        views = json.loads((gallery/"views.json").read_text())
        indices = [i for i, v in enumerate(views) if v["role"] == "gallery" and v["family"] != "augmented"]
        minimal = [{k: views[i][k] for k in ("record_id", "family", "slugs")} for i in indices]
        (output/"views.json").write_text(json.dumps(minimal, ensure_ascii=False))
        np.save(output/"features.npy", np.load(gallery/"features.npy", allow_pickle=False)[indices], allow_pickle=False)
        for kind, source in models.items():
            for name in json.loads(receipts[kind].read_text())["files_sha256"]:
                target = output/kind/name
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(source/name, target)
        ocr_config = json.loads((ROOT/"data/audit/live_shop/v1/ocr_fast/ocr_config.json").read_text())
        for model, files in ocr_config["weights_sha256"].items():
            source = ROOT/"weights/cache/ocr/paddlex/official_models"/model
            for name, sha in files.items():
                if file_hash(source/name) != sha:
                    raise ValueError("Changed OCR weights: " + model)
            for name in files:
                if ".cache" in Path(name).parts:
                    continue
                target = output/"ocr"/model/name
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(source/name, target)
        files = {str(p.relative_to(output)): file_hash(p) for p in sorted(output.rglob("*")) if p.is_file()}
        bundle_id = hashlib.sha256(json.dumps(files, sort_keys=True).encode()).hexdigest()
        manifest = dict(version=VERSION, bundle_id=bundle_id, catalog_sha256=file_hash(catalog),
                        catalog_count=2103, view_count=len(minimal), files_sha256=files,
                        source_encoding_sha256=file_hash(gallery/"encoding.json"), preprocessing=config,
                        code_sha256={str(p.relative_to(ROOT)): file_hash(p) for p in CODE_FILES},
                        ocr_versions=ocr_config["versions"], note="Gallery-only; no phone photos or evaluation labels. Reference photos are optional UI assets and are not included.")
        (output/"manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2)+"\n")
        print(json.dumps({"path": str(output), "bundle_id": bundle_id, "views": len(minimal),
                          "bytes": sum(p.stat().st_size for p in output.rglob("*") if p.is_file())}, indent=2))
    except Exception:
        # An incomplete directory has no valid manifest; preserve it for inspection.
        print("Export incomplete; do not deploy " + str(output), file=sys.stderr)
        raise


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT/"weights/wine-recognizer-v5-worker-layout-release")
    parser.add_argument("--source-bundle", type=Path, help="Reuse verified model/index files for a code-only release")
    args = parser.parse_args()
    if args.source_bundle:
        repackage(args.source_bundle.resolve(), args.output.resolve())
    else:
        export(args.output.resolve())
