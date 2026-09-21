#!/usr/bin/env python3
"""Prepare a browsable wine-reference folder from existing audit reports.

Uses only stdlib. Original media is never copied or modified. Review notes are
created once and preserved on later runs. Run after scripts/audit_catalog.py.
"""

import argparse
import csv
import json
import os
import re
from pathlib import Path


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def write_rows(path: Path, rows: list[dict[str, str]], fields: list[str]) -> None:
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)


def link_reference(source: Path, link: Path) -> None:
    if not source.is_file():
        raise FileNotFoundError(source)
    if link.is_symlink() or link.exists():
        if link.is_symlink() and link.resolve() == source.resolve():
            return
        raise FileExistsError(f"Refusing to replace existing review file: {link}")
    link.symlink_to(os.path.relpath(source, link.parent))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--audit", type=Path, default=Path("data/audit"))
    parser.add_argument("--extracted", type=Path, default=Path("data/extracted"))
    parser.add_argument("--output", type=Path, default=Path("data/catalog"))
    args = parser.parse_args()
    catalog = read_rows(args.audit / "catalog_unique.csv")
    matches = {row["Slug"]: row for row in read_rows(args.audit / "catalog_image_matches.csv")}
    collisions = {}
    for row in read_rows(args.audit / "catalog_image_identity_collisions.csv"):
        slugs = json.loads(row["slugs_json"])
        for slug in slugs:
            collisions[slug] = slugs
    references = args.output / "reference_images"
    priority = args.output / "priority_shared_images"
    references.mkdir(parents=True, exist_ok=True)
    priority.mkdir(parents=True, exist_ok=True)
    output_rows = []
    for row in catalog:
        slug = row["Slug"]
        if not re.fullmatch(r"[A-Za-z0-9_-]+", slug):
            raise ValueError(f"Unexpected filename characters in slug: {slug}")
        match = matches[slug]
        paths = json.loads(match["paths_json"])
        image_path = ""
        source_path = ""
        if match["status"] == "mapped" and len(paths) == 1:
            source = (args.extracted / paths[0]).resolve()
            source.relative_to(args.extracted.resolve())
            link = references / (slug + source.suffix.lower())
            link_reference(source, link)
            image_path = str(link.relative_to(args.output))
            source_path = str(source.relative_to(args.extracted.resolve()))
            if slug in collisions:
                link_reference(source, priority / link.name)
        review_priority = "1_shared_reference" if slug in collisions else (
            "2_unresolved_mapping" if not image_path else "3_regular")
        output_rows.append({
            "slug": slug, "title": row["Название вина"].strip(),
            "winery": row["Винодельня"].strip(), "category": row["Категория"],
            "region": row["Регион"], "grapes": row["Сорт винограда"],
            "description": row["Описание"].strip(), "mapping_status": match["status"],
            "review_priority": review_priority, "image_path": image_path,
            "source_path": source_path,
            "same_image_slugs": json.dumps(collisions.get(slug, []), ensure_ascii=False),
            "candidate_paths": match["candidate_paths_json"],
            "mapping_source": match["mapping_source"],
        })
    output_rows.sort(key=lambda row: (row["review_priority"], row["winery"], row["title"], row["slug"]))
    write_rows(args.output / "review.csv", output_rows, list(output_rows[0]))
    notes_path = args.output / "review_notes.csv"
    if not notes_path.exists():
        notes = [{"slug": row["slug"], "review_status": "not_reviewed", "issue": "",
                  "notes": "", "corrected_reference_path": ""} for row in output_rows]
        write_rows(notes_path, notes, list(notes[0]))
    summary = {
        "catalog_rows": len(output_rows),
        "linked_cards": sum(bool(row["image_path"]) for row in output_rows),
        "unique_linked_paths": len({row["source_path"] for row in output_rows if row["source_path"]}),
        "shared_reference_cards": len(collisions),
        "unresolved_cards": sum(not row["image_path"] for row in output_rows),
        "warning": "Mapped means an explicit source link, not human-verified label correctness.",
    }
    (args.output / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
