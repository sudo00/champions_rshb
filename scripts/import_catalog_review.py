#!/usr/bin/env python3
"""Import the reviewed missing-sitemap CSV without changing source data.

Only explicit OK, renamed-file and saved-file notes are accepted. New wording
or ambiguous file choices fail rather than silently selecting an image.
"""

import json
from collections import Counter
from pathlib import Path

from build_curated_catalog import CATALOG, ROOT, read_csv, relative, safe_source, sha256


def main() -> None:
    review_path = ROOT / "data/audit/catalog_without_sitemap.csv"
    rows = read_csv(review_path)
    document_path = CATALOG / "curation_decisions.json"
    document = json.loads(document_path.read_text())
    for item in document["inputs"]:
        if item["path"] != relative(review_path):
            if sha256(safe_source(item["path"])) != item["sha256"]:
                raise ValueError(f"Previous review input changed: {item['path']}")
    catalog = {row["slug"]: row for row in read_csv(CATALOG / "review.csv")}
    expected = {slug for slug, row in catalog.items() if not row["source_path"]}
    if len(rows) != len(expected) or {r["Slug"] for r in rows} != expected:
        raise ValueError("Missing-sitemap review does not cover the expected slugs")
    manifest = {r["path"]: r for r in read_csv(ROOT / "data/audit/images_manifest.csv")}
    uploads = ROOT / "data/extracted/prod-svoe-vino-strapi/prod-svoe-vino/strapi/uploads"
    decisions = {row["slug"]: row for row in document["decisions"]}
    relocations = {r["old_path"]: r for r in document.get("source_relocations", [])}
    counts = Counter()
    for row in rows:
        slug = row["Slug"]
        note = row["Соответствует"].strip()
        candidates = json.loads(row["candidate_paths_json"])
        renamed = "переименовал" in note.casefold()
        if note.casefold() in {"ок", "ok"}:
            if len(candidates) != 1:
                raise ValueError(f"OK requires exactly one candidate: {slug}")
            paths = [safe_source("data/extracted/" + candidates[0])]
            action, provenance = "accept_candidate", "user_confirmed_filename_candidate"
            counts["confirmed_candidates"] += 1
        elif renamed:
            paths = [safe_source(relative(uploads / (slug + ".webp")))]
            if slug == "beloe-polusladkoe" and "beloe-polusladkoe2.webp" in note:
                paths.append(safe_source(relative(uploads / "beloe-polusladkoe2.webp")))
            for path in paths:
                digest = sha256(path)
                old_paths = [p for p in candidates if manifest[p]["sha256"] == digest]
                if not old_paths:
                    raise ValueError(f"Renamed file does not match original bytes: {path}")
                for old in old_paths:
                    old_path = "data/extracted/" + old
                    relocations.setdefault(old_path, {
                        "old_path": old_path, "new_path": relative(path), "sha256": digest,
                    })
            action, provenance = "accept_candidate", "user_confirmed_renamed_archive"
            counts["renamed_archive_cards"] += 1
        elif slug in note and any(word in note.casefold() for word in ("сохранил", "соханил", "сохрпанил")):
            paths = [p for p in (CATALOG / "internet_images").iterdir() if p.is_file() and p.stem == slug]
            if len(paths) != 1:
                raise ValueError(f"Expected one exact-slug download: {slug}, found {paths}")
            action, provenance = "replace", "user_supplied_replacement"
            counts["downloaded_reference_cards"] += 1
        else:
            raise ValueError(f"Unrecognized review note for {slug}: {note}")
        previous = decisions.get(slug)
        if previous and previous.get("review_source") != relative(review_path):
            raise ValueError(f"Refusing to replace a different review: {slug}")
        decision = {
            "slug": slug, "action": action, "user_text": row["Соответствует"],
            "raw_user_fields": row, "review_source": relative(review_path),
            "reference_provenance": provenance, "source_url": None,
            "interpretation": "Applied the user's explicit selection; no independent label verification.",
        }
        prefix = "replacement" if action == "replace" else "accepted"
        decision[prefix + "_path"] = relative(paths[0])
        decision[prefix + "_sha256"] = sha256(paths[0])
        decision["additional_references"] = [
            {"path": relative(p), "sha256": sha256(p), "provenance": provenance}
            for p in paths[1:]
        ]
        decisions[slug] = decision
    document["schema_version"] = 2
    document["inputs"] = [i for i in document["inputs"] if i["path"] != relative(review_path)]
    document["inputs"].append({"path": relative(review_path), "sha256": sha256(review_path)})
    document["decisions"] = sorted(decisions.values(), key=lambda row: row["slug"])
    document["source_relocations"] = sorted(relocations.values(), key=lambda row: row["old_path"])
    document["missing_sitemap_review_summary"] = dict(counts)
    document_path.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(counts, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
