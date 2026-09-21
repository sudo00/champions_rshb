#!/usr/bin/env python3
"""Audit the supplied wine catalog and evaluation manifest using only stdlib.

Run from the repository root: python3 scripts/audit_catalog.py
Name similarity reports identify review candidates, not equivalent products.
Image links use exact sitemap slug and filename matches. Sanitized filename
candidates are never accepted automatically. If images_manifest.csv exists in
the output directory (from audit_images.py), summarize mapped image quality too.
The original catalog and images are never modified.
"""

import argparse
import csv
import hashlib
import json
import re
import unicodedata
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from difflib import SequenceMatcher
from itertools import combinations
from pathlib import Path
from typing import Any
from urllib.parse import unquote, urlsplit


NAME = "Название вина"
WINERY = "Винодельня"
PHOTO = "Название фото"
SLUG = "Slug"
CATEGORY = "Категория"
YEAR = re.compile(r"\b(?:19|20)\d{2}\b")
IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".webp", ".avif", ".tif", ".tiff"}


def normalize(value: str) -> str:
    value = unicodedata.normalize("NFKC", value).casefold().replace("ё", "е")
    return re.sub(r"[^\w]+", " ", value).strip()


def file_hash(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_csv(path: Path, rows: list[dict[str, Any]], fields: list[str]) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)


def grouped_candidates(
    rows: list[dict[str, str]], fields: list[str], remove_year: bool = False
) -> list[list[dict[str, str]]]:
    groups: dict[tuple[str, ...], list[dict[str, str]]] = defaultdict(list)
    for row in rows:
        values = [YEAR.sub("", row[key]) if remove_year and key == NAME
                  else row[key] for key in fields]
        groups[tuple(normalize(value) for value in values)].append(row)
    return [group for _, group in sorted(groups.items()) if len(group) > 1]


def group_report(groups: list[list[dict[str, str]]]) -> list[dict[str, Any]]:
    return [
        {"group_id": index, "group_size": len(group), SLUG: row[SLUG],
         NAME: row[NAME], WINERY: row[WINERY], CATEGORY: row[CATEGORY],
         PHOTO: row[PHOTO]}
        for index, group in enumerate(groups, start=1)
        for row in sorted(group, key=lambda item: item[SLUG])
    ]


def audit_eval(eval_dir: Path) -> dict[str, Any]:
    manifest = eval_dir / "queries.tsv"
    if not manifest.exists():
        return {"present": False}
    with manifest.open(encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle, delimiter="\t")
        fields = reader.fieldnames or []
        queries = list(reader)
    checks = []
    checksum_file = eval_dir / "checksums.sha256"
    if checksum_file.exists():
        for line in checksum_file.read_text(encoding="utf-8").splitlines():
            expected, relative = line.split(maxsplit=1)
            path = eval_dir / relative.lstrip("*")
            checks.append({"path": relative, "exists": path.is_file(),
                           "matches": path.is_file() and file_hash(path) == expected})
    missing = [row["image_path"] for row in queries
               if not (eval_dir / "queries" / row["image_path"]).is_file()]
    return {
        "present": True, "manifest_fields": fields, "query_count": len(queries),
        "unique_query_ids": len({row["query_id"] for row in queries}),
        "missing_images": missing, "checksums": checks,
        "has_ground_truth_slug_column": any("slug" in key.casefold() for key in fields),
        "queries": queries,
    }


def audit_images(
    rows: list[dict[str, str]], images_dir: Path, output_dir: Path
) -> dict[str, Any]:
    if not images_dir.exists():
        return {"present": False, "directory": str(images_dir)}
    files_by_name: dict[str, list[str]] = defaultdict(list)
    for path in sorted(images_dir.rglob("*")):
        if path.is_file() and path.suffix.lower() in IMAGE_SUFFIXES:
            if "__MACOSX" not in path.parts and not path.name.startswith("._"):
                files_by_name[path.name].append(str(path.relative_to(images_dir)))
    sitemap_namespace = "{http://www.sitemaps.org/schemas/sitemap/0.9}"
    image_namespace = "{http://www.google.com/schemas/sitemap-image/1.1}"
    sitemap_entries: dict[str, list[dict[str, str]]] = defaultdict(list)
    sitemap_sources = []
    for sitemap in sorted(images_dir.rglob("wines_sitemap*.xml")):
        sitemap_sources.append({"path": str(sitemap), "sha256": file_hash(sitemap)})
        for entry in ET.parse(sitemap).getroot():
            page_url = entry.findtext(sitemap_namespace + "loc", "")
            slug = unquote(urlsplit(page_url).path.rstrip("/").split("/")[-1])
            for element in entry.findall(image_namespace + "image"):
                image_url = element.findtext(image_namespace + "loc", "")
                filename = unquote(urlsplit(image_url).path.split("/")[-1])
                sitemap_entries[slug].append({
                    "page_url": page_url, "image_url": image_url, "filename": filename,
                    "lastmod": entry.findtext(sitemap_namespace + "lastmod", ""),
                    "source": str(sitemap),
                })

    def filename_key(value: str) -> str:
        return "".join(character for character in
                       unicodedata.normalize("NFKC", Path(value).stem).casefold()
                       if character.isalnum())

    # This transformation is deliberately only a candidate generator: the archive
    # does not include Strapi's original filename metadata or sanitizer version.
    original_names: dict[str, list[str]] = defaultdict(list)
    for name in files_by_name:
        if not re.match(r"^(thumbnail|small|medium|large)_", name):
            unhashed = re.sub(r"_[0-9a-f]{10}(?=\.[^.]+$)", "", name)
            if unhashed != name:
                original_names[filename_key(unhashed)].append(name)
    matches = []
    without_sitemap = []
    normalized_rule_validation = Counter()
    for row in rows:
        exact_paths = files_by_name.get(row[PHOTO], [])
        entries = sitemap_entries.get(row[SLUG], [])
        sitemap_names = sorted({entry["filename"] for entry in entries})
        sitemap_paths = sorted({path for name in sitemap_names for path in files_by_name.get(name, [])})
        candidate_names = sorted(original_names.get(filename_key(row[PHOTO]), []))
        candidates = sorted({path for name in candidate_names for path in files_by_name[name]})
        if entries:
            validation = "no_candidates" if not candidates else (
                "includes_sitemap" if set(sitemap_paths).issubset(candidates) else "conflicts_with_sitemap")
            normalized_rule_validation[validation] += 1
        conflicts = bool(exact_paths and entries and set(exact_paths) != set(sitemap_paths))
        if conflicts:
            status, paths, source = "conflicting_authoritative_sources", [], "conflict"
        elif entries:
            paths, source = sitemap_paths, "sitemap_exact_slug_and_filename"
            status = "mapped" if paths else "sitemap_file_missing"
        elif exact_paths:
            paths, source, status = exact_paths, "csv_exact_filename", "mapped"
        else:
            paths, source, status = [], "none", "unresolved"
        result = {
            SLUG: row[SLUG], PHOTO: row[PHOTO], "status": status, "mapping_source": source,
            "file_count": len(paths), "paths_json": json.dumps(paths, ensure_ascii=False),
            "csv_exact_paths_json": json.dumps(exact_paths, ensure_ascii=False),
            "sitemap_entries_json": json.dumps(entries, ensure_ascii=False),
            "sitemap_paths_json": json.dumps(sitemap_paths, ensure_ascii=False),
            "candidate_paths_json": json.dumps(candidates, ensure_ascii=False),
            "candidate_count": len(candidates),
        }
        matches.append(result)
        if not entries:
            without_sitemap.append({SLUG: row[SLUG], NAME: row[NAME], WINERY: row[WINERY],
                                    PHOTO: row[PHOTO], "candidate_count": len(candidates),
                                    "candidate_paths_json": json.dumps(candidates, ensure_ascii=False)})
    match_fields = [SLUG, PHOTO, "status", "mapping_source", "file_count", "paths_json",
                    "csv_exact_paths_json", "sitemap_entries_json", "sitemap_paths_json",
                    "candidate_paths_json", "candidate_count"]
    write_csv(output_dir / "catalog_image_matches.csv", matches, match_fields)
    write_csv(output_dir / "catalog_without_sitemap.csv", without_sitemap,
              [SLUG, NAME, WINERY, PHOTO, "candidate_count", "candidate_paths_json"])
    paths_to_slugs: dict[str, set[str]] = defaultdict(set)
    for match in matches:
        for path in json.loads(match["paths_json"]):
            paths_to_slugs[path].add(match[SLUG])
    shared = [{"path": path, "slug_count": len(slugs), "slugs_json": json.dumps(sorted(slugs))}
              for path, slugs in sorted(paths_to_slugs.items()) if len(slugs) > 1]
    write_csv(output_dir / "shared_mapped_image_files.csv", shared,
              ["path", "slug_count", "slugs_json"])
    variants = []
    for path, slugs in sorted(paths_to_slugs.items()):
        original = Path(path)
        for variant in ["original", "thumbnail", "small", "medium", "large"]:
            name = original.name if variant == "original" else f"{variant}_{original.name}"
            for variant_path in files_by_name.get(name, []):
                if Path(variant_path).parent != original.parent:
                    continue
                for slug in sorted(slugs):
                    variants.append({SLUG: slug, "original_path": path,
                                     "path": variant_path, "variant": variant,
                                     "original_shared_slug_count": len(slugs)})
    write_csv(output_dir / "catalog_image_variants.csv", variants,
              [SLUG, "original_path", "path", "variant", "original_shared_slug_count"])
    catalog_names = {row[PHOTO] for row in rows}
    unmatched = [{"filename": name, "paths_json": json.dumps(paths, ensure_ascii=False)}
                 for name, paths in sorted(files_by_name.items()) if name not in catalog_names]
    write_csv(output_dir / "images_without_catalog_filename.csv", unmatched,
              ["filename", "paths_json"])
    result = {
        "present": True, "directory": str(images_dir),
        "matching_rule": "Exact CSV slug to wines sitemap; image URL basename to exact archive filename. Exact CSV filenames retained separately. Conflicting exact sources remain unresolved.",
        "image_file_count": sum(map(len, files_by_name.values())),
        "unique_image_filenames": len(files_by_name),
        "catalog_slugs_with_matching_file": sum(row["file_count"] > 0 for row in matches),
        "catalog_slugs_without_matching_file": sum(row["file_count"] == 0 for row in matches),
        "catalog_slugs_with_multiple_paths": sum(row["file_count"] > 1 for row in matches),
        "image_filenames_without_exact_csv_filename_match": len(unmatched),
        "exact_csv_filename_matches": sum(bool(json.loads(row["csv_exact_paths_json"])) for row in matches),
        "sitemap_sources": sitemap_sources,
        "sitemap_unique_slugs": len(sitemap_entries),
        "sitemap_slugs_absent_from_catalog": sorted(set(sitemap_entries) - {row[SLUG] for row in rows}),
        "catalog_slugs_absent_from_sitemap": len(without_sitemap),
        "mapped_unique_image_files": len(paths_to_slugs),
        "mapped_originals_and_resize_variants_unique_files": len({row["path"] for row in variants}),
        "mapped_variant_file_counts": dict(Counter({row["path"]: row["variant"] for row in variants}.values())),
        "shared_mapped_image_file_groups": len(shared),
        "slugs_in_shared_mapped_image_file_groups": sum(row["slug_count"] for row in shared),
        "mapping_status_counts": dict(Counter(row["status"] for row in matches)),
        "unresolved_normalized_candidate_count_histogram": dict(sorted(Counter(row["candidate_count"] for row in without_sitemap).items())),
        "normalized_filename_rule": "Remove known resize prefix; remove terminal _10hex upload hash; compare casefolded NFKC alphanumeric stem. No transliteration or fuzzy matching. Candidates require review; never used as accepted mapping.",
        "normalized_filename_rule_validation_against_sitemap": dict(normalized_rule_validation),
    }
    manifest_path = output_dir / "images_manifest.csv"
    if manifest_path.exists():
        with manifest_path.open(encoding="utf-8", newline="") as handle:
            manifest = {row["path"]: row for row in csv.DictReader(handle)}
        selected = [manifest[path] for path in paths_to_slugs if path in manifest]
        result["image_manifest"] = {
            "source": str(manifest_path), "mapped_files_found": len(selected),
            "mapped_files_absent": sorted(set(paths_to_slugs) - set(manifest)),
            "decode_status_counts": dict(Counter(row["decode_status"] for row in selected)),
            "format_counts": dict(Counter(row["format"] for row in selected)),
            "mode_counts": dict(Counter(row["mode"] for row in selected)),
            "total_bytes": sum(int(row["bytes"]) for row in selected),
        }
        sizes = [(int(row["width"]), int(row["height"])) for row in selected if row["width"] and row["height"]]
        if sizes:
            result["image_manifest"]["dimensions"] = {
                "min_width": min(w for w, h in sizes), "max_width": max(w for w, h in sizes),
                "min_height": min(h for w, h in sizes), "max_height": max(h for w, h in sizes),
                "both_dimensions_at_least_224": sum(min(w, h) >= 224 for w, h in sizes),
                "short_side_below_100": sum(min(w, h) < 100 for w, h in sizes),
                "most_common_sizes": Counter(f"{w}x{h}" for w, h in sizes).most_common(10),
            }
        hashes: dict[str, list[str]] = defaultdict(list)
        for row in selected:
            if row["sha256"]:
                hashes[row["sha256"]].append(row["path"])
        duplicates = [{"sha256": sha256, "file_count": len(paths),
                       "paths_json": json.dumps(sorted(paths), ensure_ascii=False),
                       "slugs_json": json.dumps(sorted({slug for path in paths for slug in paths_to_slugs[path]}))}
                      for sha256, paths in sorted(hashes.items()) if len(paths) > 1]
        write_csv(output_dir / "catalog_image_byte_duplicates.csv", duplicates,
                  ["sha256", "file_count", "paths_json", "slugs_json"])
        result["image_manifest"]["distinct_file_hashes"] = len(hashes)
        result["image_manifest"]["byte_duplicate_file_groups"] = len(duplicates)
        result["image_manifest"]["files_in_byte_duplicate_groups"] = sum(row["file_count"] for row in duplicates)
        identities = []
        for sha256, paths in sorted(hashes.items()):
            slugs = {slug for path in paths for slug in paths_to_slugs[path]}
            if len(slugs) > 1:
                identities.append({"sha256": sha256, "slug_count": len(slugs),
                                   "file_count": len(paths), "slugs_json": json.dumps(sorted(slugs)),
                                   "paths_json": json.dumps(sorted(paths), ensure_ascii=False)})
        write_csv(output_dir / "catalog_image_identity_collisions.csv", identities,
                  ["sha256", "slug_count", "file_count", "slugs_json", "paths_json"])
        result["image_manifest"]["byte_identical_reference_slug_groups"] = len(identities)
        result["image_manifest"]["slugs_in_byte_identical_reference_groups"] = sum(row["slug_count"] for row in identities)
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", type=Path, default=Path("data/strapi_output0709.csv"))
    parser.add_argument("--eval-dir", type=Path, default=Path("data/eval"))
    parser.add_argument("--images-dir", type=Path, default=Path("data/extracted"))
    parser.add_argument("--output-dir", type=Path, default=Path("data/audit"))
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    with args.catalog.open(encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        fields = reader.fieldnames or []
        rows = list(reader)
    if not rows or any(set(row) != set(fields) or None in row.values() for row in rows):
        raise ValueError("Catalog is empty or has malformed rows")
    for field in [SLUG, NAME, WINERY, PHOTO, CATEGORY]:
        if field not in fields:
            raise ValueError(f"Missing required column: {field}")
    row_counts = Counter(tuple(row[field] for field in fields) for row in rows)
    unique = [dict(zip(fields, values)) for values in row_counts]
    unique.sort(key=lambda row: row[SLUG])
    by_slug: dict[str, list[dict[str, str]]] = defaultdict(list)
    by_photo: dict[str, set[str]] = defaultdict(set)
    for row in unique:
        by_slug[row[SLUG]].append(row)
        by_photo[row[PHOTO]].add(row[SLUG])
    write_csv(args.output_dir / "catalog_unique.csv", unique, fields)
    duplicate_rows = [
        {SLUG: values[fields.index(SLUG)], "occurrences": count,
         "extra_copies": count - 1}
        for values, count in row_counts.items() if count > 1
    ]
    write_csv(args.output_dir / "duplicate_catalog_rows.csv", duplicate_rows,
              [SLUG, "occurrences", "extra_copies"])
    photo_collisions = [
        {PHOTO: photo, "slug_count": len(slugs), "slugs_json": json.dumps(sorted(slugs))}
        for photo, slugs in sorted(by_photo.items()) if len(slugs) > 1
    ]
    write_csv(args.output_dir / "shared_photo_filenames.csv", photo_collisions,
              [PHOTO, "slug_count", "slugs_json"])
    group_specs = {
        "same_normalized_name": ([NAME], False),
        "same_winery_name": ([WINERY, NAME], False),
        "same_winery_name_category": ([WINERY, NAME, CATEGORY], False),
        "same_winery_name_without_year_category": ([WINERY, NAME, CATEGORY], True),
        "same_normalized_business_fields": ([key for key in fields if key not in {SLUG, PHOTO}], False),
    }
    group_summary = {}
    for name, (group_fields, remove_year) in group_specs.items():
        groups = grouped_candidates(unique, group_fields, remove_year)
        group_summary[name] = {"groups": len(groups), "rows": sum(map(len, groups)),
                               "fields": group_fields, "remove_year_from_name": remove_year}
        write_csv(args.output_dir / f"{name}.csv", group_report(groups),
                  ["group_id", "group_size", SLUG, NAME, WINERY, CATEGORY, PHOTO])
    blocks: dict[tuple[str, str], list[dict[str, str]]] = defaultdict(list)
    for row in unique:
        blocks[(normalize(row[WINERY]), normalize(row[CATEGORY]))].append(row)
    near_pairs = []
    for block in blocks.values():
        for left, right in combinations(block, 2):
            a, b = normalize(left[NAME]), normalize(right[NAME])
            if a == b or min(len(a), len(b)) < 12:
                continue
            matcher = SequenceMatcher(None, a, b, autojunk=False)
            if matcher.quick_ratio() < 0.92:
                continue
            score = matcher.ratio()
            if score >= 0.92:
                near_pairs.append({"slug_a": left[SLUG], "slug_b": right[SLUG],
                                   "name_a": left[NAME], "name_b": right[NAME],
                                   WINERY: left[WINERY], "similarity": round(score, 4)})
    write_csv(args.output_dir / "similar_name_candidates.csv", near_pairs,
              ["slug_a", "slug_b", "name_a", "name_b", WINERY, "similarity"])
    summary = {
        "catalog": str(args.catalog), "catalog_sha256": file_hash(args.catalog),
        "raw_rows": len(rows), "unique_exact_rows": len(unique),
        "exact_duplicate_extra_rows": len(rows) - len(unique),
        "unique_slugs": len(by_slug), "unique_photo_filenames": len(by_photo),
        "slug_raw_occurrence_histogram": dict(sorted(Counter(Counter(row[SLUG] for row in rows).values()).items())),
        "slugs_with_conflicting_rows": [slug for slug, group in by_slug.items() if len(group) > 1],
        "slugs_with_multiple_photo_filenames": [slug for slug, group in by_slug.items()
                                                if len({row[PHOTO] for row in group}) > 1],
        "shared_photo_filename_groups": len(photo_collisions),
        "slugs_in_shared_photo_filename_groups": sum(row["slug_count"] for row in photo_collisions),
        "fields": {field: {
            "missing_raw_rows": sum(not row[field].strip() for row in rows),
            "missing_unique_rows": sum(not row[field].strip() for row in unique),
            "unique_values": len({row[field] for row in unique}),
            "leading_or_trailing_whitespace_unique_rows": sum(row[field] != row[field].strip() for row in unique),
        } for field in fields},
        "missing_values": [{SLUG: row[SLUG], "field": field} for row in unique
                           for field in fields if not row[field].strip()],
        "categories_unique_rows": dict(Counter(row[CATEGORY] for row in unique)),
        "regions_unique_rows": dict(Counter(row["Регион"] for row in unique)),
        "year_in_name_unique_rows": sum(bool(YEAR.search(row[NAME])) for row in unique),
        "duplicate_candidate_groups": group_summary,
        "similar_name_candidate_pairs": len(near_pairs),
        "similar_name_rule": "Same normalized winery and category; normalized names >= 12 characters; SequenceMatcher ratio >= 0.92; unequal names only. Candidates require review.",
        "eval": audit_eval(args.eval_dir),
        "image_mapping": audit_images(unique, args.images_dir, args.output_dir),
    }
    (args.output_dir / "catalog_summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: summary[key] for key in ["raw_rows", "unique_exact_rows",
                     "exact_duplicate_extra_rows", "unique_slugs", "unique_photo_filenames",
                     "shared_photo_filename_groups", "similar_name_candidate_pairs"]},
                     ensure_ascii=False, indent=2))
    print(f"Reports: {args.output_dir}")


if __name__ == "__main__":
    main()
