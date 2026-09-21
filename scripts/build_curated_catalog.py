#!/usr/bin/env python3
"""Build a derived catalog using explicitly reviewed reference decisions.

Requires Pillow and ffmpeg for AVIF when Pillow cannot decode it. Source CSVs,
manual notes, downloaded images and extracted originals are never modified.
"""

import csv
import hashlib
import json
import os
import subprocess
from collections import Counter, defaultdict
from pathlib import Path

from PIL import Image, UnidentifiedImageError

from catalog_metadata import apply_metadata, load_metadata_decisions, write_metadata_review_report


ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "data/catalog"
OUTPUT = CATALOG / "curated"


def sha256(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def relative(path: Path) -> str:
    return str(path.relative_to(ROOT))


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def safe_source(value: str) -> Path:
    source = (ROOT / value).resolve()
    source.relative_to((ROOT / "data").resolve())
    if not source.is_file():
        raise FileNotFoundError(source)
    return source


def materialize(source: Path, name: str, previous_paths: dict[str, str]) -> dict:
    """Decode a reference and maintain only links owned by the previous manifest."""
    usable = source
    try:
        with Image.open(source) as image:
            image.load()
    except UnidentifiedImageError:
        if source.suffix.lower() != ".avif":
            raise
        usable = OUTPUT / "decoded" / (name + ".png")
        subprocess.run([
            "ffmpeg", "-v", "error", "-i", str(source),
            "-frames:v", "1", "-y", str(usable),
        ], check=True)
    with Image.open(usable) as image:
        image.load()
        result = {"image_format": image.format, "image_size": list(image.size)}
    link = OUTPUT / "images" / (name + usable.suffix.lower())
    if link.is_symlink() and link.resolve() != usable.resolve():
        previous = previous_paths.get(relative(link))
        if previous is None or link.resolve() != (ROOT / previous).resolve():
            raise FileExistsError(f"Refusing to replace untracked link: {link}")
        link.unlink()
    if link.exists() or link.is_symlink():
        if not link.is_symlink() or link.resolve() != usable.resolve():
            raise FileExistsError(f"Refusing to replace existing image: {link}")
    else:
        link.symlink_to(os.path.relpath(usable, link.parent))
    result.update({
        "reference_path": relative(link), "reference_source_path": relative(source),
        "reference_sha256": sha256(source),
    })
    return result


def main() -> None:
    decisions_path = CATALOG / "curation_decisions.json"
    document = json.loads(decisions_path.read_text())
    for value in document["inputs"]:
        source = safe_source(value["path"])
        if sha256(source) != value["sha256"]:
            raise ValueError(f"Reviewed input has changed: {value['path']}")
    catalog = read_csv(CATALOG / "review.csv")
    metadata_document = load_metadata_decisions(ROOT)
    metadata_decisions = {d["slug"]: d for d in metadata_document["decisions"]} if metadata_document else {}
    if set(metadata_decisions) - {row["slug"] for row in catalog}:
        raise ValueError("Unknown metadata review slug")
    raw_catalog = {r["Slug"]: r for r in read_csv(ROOT / "data/strapi_output0709.csv")}
    decisions = {row["slug"]: row for row in document["decisions"]}
    if len(decisions) != len(document["decisions"]):
        raise ValueError("Duplicate reviewed slug")
    if set(decisions) - {row["slug"] for row in catalog}:
        raise ValueError("Unknown reviewed slug")
    relocations = {}
    for item in document.get("source_relocations", []):
        source = safe_source(item["new_path"])
        if sha256(source) != item["sha256"]:
            raise ValueError(f"Relocated source changed: {source}")
        relocations[item["old_path"]] = source
    previous_paths = {}
    previous_manifest = OUTPUT / "catalog.jsonl"
    if previous_manifest.exists():
        for line in previous_manifest.read_text().splitlines():
            previous = json.loads(line)
            for ref in previous.get("references", [previous]):
                if ref.get("reference_path"):
                    previous_paths[ref["reference_path"]] = ref["reference_source_path"]
    sources = {}
    additional_sources = {}
    for slug, decision in decisions.items():
        if decision["action"] not in {"replace", "accept_candidate", "keep", "ambiguous", "missing_correct_reference"}:
            raise ValueError(f"Unsupported action for {slug}")
        if decision["action"] in {"replace", "accept_candidate"}:
            prefix = "replacement" if decision["action"] == "replace" else "accepted"
            source = safe_source(decision[prefix + "_path"])
            if sha256(source) != decision[prefix + "_sha256"]:
                raise ValueError(f"Replacement identity changed: {slug}")
            if decision["action"] == "replace" and source.stem != slug:
                raise ValueError(f"Replacement slug mismatch: {slug}")
            sources[slug] = source
        additional_sources[slug] = []
        for ref in decision.get("additional_references", []):
            source = safe_source(ref["path"])
            if sha256(source) != ref["sha256"]:
                raise ValueError(f"Additional reference changed: {slug}")
            additional_sources[slug].append((source, ref["provenance"]))
    (OUTPUT / "images").mkdir(parents=True, exist_ok=True)
    (OUTPUT / "decoded").mkdir(exist_ok=True)
    rows = []
    for original in catalog:
        slug = original["slug"]
        decision = decisions.get(slug)
        action = decision["action"] if decision else "not_reviewed"
        source = None
        provenance = "none"
        if action in {"replace", "accept_candidate"}:
            source = sources[slug]
            provenance = decision.get("reference_provenance", "user_supplied_replacement")
        elif action != "missing_correct_reference" and original["source_path"]:
            original_path = "data/extracted/" + original["source_path"]
            source = relocations.get(original_path)
            if source is None:
                source = safe_source(original_path)
            provenance = "original_sitemap"
        row = {
            "slug": slug, "title": original["title"], "winery": original["winery"],
            "category": original["category"], "region": original["region"],
            "grapes": original["grapes"], "description": original["description"],
            "color_shade": raw_catalog[slug]["Цвет"],
            "original_reference_path": "data/extracted/" + original["source_path"] if original["source_path"] else None,
            "review_action": action,
            "review_note": decision["user_text"] if decision else None,
            "reference_provenance": provenance,
            "source_url": None,
            "reference_usable": source is not None,
            "reference_source_path": relative(source) if source else None,
            "reference_path": None, "reference_sha256": None,
            "original_identity_group": json.loads(original["same_image_slugs"]),
            "exact_slug_ambiguity": action == "ambiguous",
            "references": [],
        }
        if slug in metadata_decisions:
            apply_metadata(row, metadata_decisions[slug])
        if source:
            primary = materialize(source, slug, previous_paths)
            primary["reference_provenance"] = provenance
            row.update(primary)
            row["references"].append(primary)
            for index, (extra, extra_provenance) in enumerate(additional_sources.get(slug, []), 2):
                ref = materialize(extra, f"{slug}__{index}", previous_paths)
                ref["reference_provenance"] = extra_provenance
                row["references"].append(ref)
        rows.append(row)
    groups = defaultdict(set)
    for row in rows:
        for ref in row["references"]:
            groups[ref["reference_sha256"]].add(row["slug"])
    collisions = [sorted(slugs) for slugs in groups.values() if len(slugs) > 1]
    for row in rows:
        peers = set().union(*(groups[ref["reference_sha256"]] for ref in row["references"]))
        row["same_reference_slugs"] = sorted(peers) if len(peers) > 1 else []
        row["exact_slug_ambiguity"] |= len(peers) > 1
    with (OUTPUT / "catalog.jsonl").open("w", encoding="utf-8") as stream:
        for row in rows:
            stream.write(json.dumps(row, ensure_ascii=False) + "\n")
    summary = {
        "catalog_cards": len(rows), "reviewed_cards": len(decisions),
        "review_actions": dict(Counter(row["review_action"] for row in rows)),
        "usable_reference_cards": sum(row["reference_usable"] for row in rows),
        "reference_files": sum(len(row["references"]) for row in rows),
        "unique_reference_images": len(groups),
        "missing_sitemap_review": document.get("missing_sitemap_review_summary", {}),
        "cards_without_usable_reference": [row["slug"] for row in rows if not row["reference_usable"]],
        "remaining_identical_reference_groups": collisions,
        "exact_slug_ambiguous_cards": sum(row["exact_slug_ambiguity"] for row in rows),
        "decision_file": relative(decisions_path), "decision_sha256": sha256(decisions_path),
        "metadata_review": {
            "reviewed_cards": len(metadata_decisions),
            "changed_cards": sum(bool(d["updates"]) for d in metadata_decisions.values()),
            "changed_fields": sum(len(d["updates"]) for d in metadata_decisions.values()),
            "open_questions": {slug: d["open_questions"] for slug, d in metadata_decisions.items() if d["open_questions"]},
            "decision_file": "data/catalog/metadata_decisions.json" if metadata_document else None,
            "decision_sha256": sha256(CATALOG / "metadata_decisions.json") if metadata_document else None,
        },
        "limitations": [
            "Unreviewed originals retain source mapping; image-label correctness is not fully verified.",
            "Identical-reference cards retain distinct slugs and remain ambiguous.",
            "Internet source URLs were not supplied; source_url remains null.",
            "Manual review decisions reflect user notes; all attributes and vintages were not independently verified.",
        ],
    }
    (OUTPUT / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n")
    if metadata_document:
        write_metadata_review_report(ROOT, metadata_document)
    print(json.dumps({key: summary[key] for key in ["catalog_cards", "reviewed_cards", "review_actions", "usable_reference_cards", "exact_slug_ambiguous_cards"]}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
