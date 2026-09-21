#!/usr/bin/env python3
"""Inventory supplied uploads, validate raster decoding, and inspect query overlap.

Requires Pillow. Run from the repository root. Inputs are never modified.
Strapi naming families and byte duplicates do not imply equivalent wine SKUs.
"""

import argparse
import csv
import hashlib
import json
import math
import random
import re
import time
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from typing import Any

from PIL import Image, ImageDraw, ImageFont, ImageOps, JpegImagePlugin, PngImagePlugin, UnidentifiedImageError


VARIANT = re.compile(r"^(large|medium|small|thumbnail)_")
EXPECTED_FORMATS = {
    ".webp": "WEBP", ".jpg": "JPEG", ".jpeg": "JPEG", ".jfif": "JPEG",
    ".png": "PNG", ".tif": "TIFF", ".tiff": "TIFF", ".heic": "HEIF",
}
FIELDS = [
    "path", "name", "variant", "family", "extension", "bytes", "sha256",
    "format", "width", "height", "display_width", "display_height", "mode",
    "has_alpha", "frames", "exif_orientation", "decode_status", "error",
    "pixel_sha256", "extension_matches_format",
]


def file_hash(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def pixel_hash(image: Image.Image) -> str:
    """Hash EXIF-transposed RGBA dimensions and pixels; no ICC conversion."""
    rgba = ImageOps.exif_transpose(image).convert("RGBA")
    digest = hashlib.sha256(f"RGBA:{rgba.width}x{rgba.height}:".encode("ascii"))
    digest.update(rgba.tobytes())
    return digest.hexdigest()


def non_raster_format(path: Path) -> str:
    with path.open("rb") as handle:
        head = handle.read(8192)
    if head.startswith(b"%PDF-"):
        return "PDF"
    if b"<svg" in head:
        return "SVG"
    if len(head) > 12 and head[4:8] == b"ftyp":
        if any(brand in head[8:64] for brand in (b"heic", b"heix", b"mif1")):
            return "HEIF"
    if head.lstrip().startswith((b"<?xml", b"<urlset", b"<sitemapindex")):
        return "XML"
    if path.suffix.lower() == ".geojson":
        try:
            data = json.loads(path.read_text(encoding="utf-8-sig"))
            if data.get("type") in {"FeatureCollection", "Feature", "Polygon", "MultiPolygon"}:
                return "GeoJSON"
        except (ValueError, AttributeError):
            pass
    return "UNKNOWN"


def inspect_image(path: Path, root: Path, query_sizes: set[tuple[int, int]]) -> dict[str, Any]:
    relative = path.relative_to(root)
    match = VARIANT.match(path.name)
    family_name = path.name
    while VARIANT.match(family_name):
        family_name = VARIANT.sub("", family_name, count=1)
    row: dict[str, Any] = dict.fromkeys(FIELDS, "")
    row.update({
        "path": str(relative), "name": path.name,
        "variant": match.group(1) if match else "original",
        "family": str(relative.with_name(family_name)),
        "extension": path.suffix.lower(), "bytes": path.stat().st_size,
        "sha256": file_hash(path),
    })
    try:
        with Image.open(path) as image:
            row.update({"format": image.format, "width": image.width, "height": image.height,
                        "mode": image.mode, "has_alpha": "A" in image.mode or "transparency" in image.info,
                        "frames": getattr(image, "n_frames", 1)})
            orientation = image.getexif().get(274, 1)
            row["exif_orientation"] = orientation
            display_size = image.size[::-1] if orientation in {5, 6, 7, 8} else image.size
            row["display_width"], row["display_height"] = display_size
            image.load()
            row["decode_status"] = "decoded_first_frame"
            if display_size in query_sizes:
                row["pixel_sha256"] = pixel_hash(image)
    except Image.DecompressionBombError as exc:
        # Inspect only container headers, without allocating or decoding pixels.
        with path.open("rb") as handle:
            signature = handle.read(8)
        decoder = (PngImagePlugin.PngImageFile if signature == b"\x89PNG\r\n\x1a\n"
                   else JpegImagePlugin.JpegImageFile if signature.startswith(b"\xff\xd8") else None)
        if decoder is not None:
            with decoder(path) as image:
                row.update({"format": image.format, "width": image.width, "height": image.height,
                            "mode": image.mode, "has_alpha": "A" in image.mode or "transparency" in image.info})
        row["decode_status"] = "skipped_pixel_limit"
        row["error"] = f"Pixel decoding skipped: {exc}"
    except UnidentifiedImageError:
        row["format"] = non_raster_format(path)
        row["decode_status"] = "unsupported_raster" if row["format"] == "HEIF" else "non_raster"
        if row["format"] == "UNKNOWN":
            row["decode_status"] = "error"
            row["error"] = "Unidentified file content"
    except Exception as exc:
        row["decode_status"] = "error"
        row["error"] = f"{type(exc).__name__}: {exc}"
    expected = EXPECTED_FORMATS.get(row["extension"])
    if expected and row["format"]:
        row["extension_matches_format"] = expected == row["format"]
    return row


def write_csv(path: Path, rows: list[dict[str, Any]], fields: list[str]) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)


def quantiles(values: list[int]) -> dict[str, int]:
    ordered = sorted(values)
    if not ordered:
        return {}
    return {label: ordered[round(fraction * (len(ordered) - 1))]
            for label, fraction in (("min", 0), ("p25", .25), ("median", .5), ("p75", .75), ("p95", .95), ("max", 1))}


def contact_sheet(paths: list[Path], output: Path, title: str, columns: int = 6) -> None:
    tile_width, tile_height = 220, 275
    sheet = Image.new("RGB", (columns * tile_width, 45 + math.ceil(len(paths) / columns) * tile_height), "#e6e8eb")
    draw = ImageDraw.Draw(sheet)
    font_path = Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
    font = ImageFont.truetype(str(font_path), 12) if font_path.exists() else ImageFont.load_default()
    draw.text((12, 14), title, fill="black", font=font)
    for index, path in enumerate(paths):
        x, y = (index % columns) * tile_width, 45 + (index // columns) * tile_height
        draw.rectangle((x + 5, y + 3, x + tile_width - 5, y + 218), fill="white")
        with Image.open(path) as image:
            image = ImageOps.exif_transpose(image).convert("RGBA")
            image.thumbnail((tile_width - 16, 208))
            sheet.paste(image, (x + (tile_width - image.width) // 2, y + 8 + (208 - image.height) // 2), image)
        label = f"{index + 1:02d} {path.name}"
        for line in range(3):
            end = len(label)
            while end > 0 and draw.textlength(label[:end], font=font) > tile_width - 14:
                end -= 1
            chunk, label = label[:end], label[end:]
            if line == 2 and label:
                chunk = chunk[:-3] + "..."
            draw.text((x + 7, y + 222 + 15 * line), chunk, fill="black", font=font)
            if not label:
                break
    sheet.save(output)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path("data/extracted"))
    parser.add_argument("--queries", type=Path, default=Path("data/eval/queries"))
    parser.add_argument("--output", type=Path, default=Path("data/audit"))
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--seed", type=int, default=20260916)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    start = time.monotonic()

    queries = [inspect_image(path, args.queries, set()) for path in sorted(args.queries.iterdir()) if path.is_file()]
    query_sizes = {(row["display_width"], row["display_height"]) for row in queries if row["decode_status"] == "decoded_first_frame"}
    for row in queries:
        if row["decode_status"] == "decoded_first_frame":
            with Image.open(args.queries / row["path"]) as image:
                row["pixel_sha256"] = pixel_hash(image)

    files = sorted(path for path in args.root.rglob("*") if path.is_file())
    rows = []
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        for index, row in enumerate(pool.map(lambda path: inspect_image(path, args.root, query_sizes), files), 1):
            rows.append(row)
            if index % 1000 == 0 or index == len(files):
                print(f"Inspected {index}/{len(files)} files in {time.monotonic() - start:.1f}s", flush=True)
    write_csv(args.output / "images_manifest.csv", rows, FIELDS)

    hashes: dict[str, list[dict[str, Any]]] = defaultdict(list)
    families: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        hashes[row["sha256"]].append(row)
        if row["decode_status"] in {"decoded_first_frame", "unsupported_raster", "skipped_pixel_limit"}:
            families[row["family"]].append(row)
    duplicate_groups = [group for group in hashes.values() if len(group) > 1]
    duplicates = [{"group_id": index, "group_size": len(group), "sha256": row["sha256"],
                   "path": row["path"], "variant": row["variant"], "bytes": row["bytes"]}
                  for index, group in enumerate(duplicate_groups, 1) for row in group]
    write_csv(args.output / "images_exact_duplicates.csv", duplicates,
              ["group_id", "group_size", "sha256", "path", "variant", "bytes"])
    family_rows = [{"family": name, "file_count": len(group),
                    "variants": ",".join(sorted(row["variant"] for row in group)),
                    "has_original": any(row["variant"] == "original" for row in group),
                    "paths_json": json.dumps([row["path"] for row in group], ensure_ascii=False)}
                   for name, group in sorted(families.items())]
    write_csv(args.output / "images_families.csv", family_rows,
              ["family", "file_count", "variants", "has_original", "paths_json"])

    for query in queries:
        query["archive_byte_matches"] = [row["path"] for row in hashes.get(query["sha256"], [])]
        query["archive_pixel_matches"] = [row["path"] for row in rows
                                            if query["pixel_sha256"] and row["pixel_sha256"] == query["pixel_sha256"]]
    (args.output / "images_query_matches.json").write_text(json.dumps(queries, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    rasters = [row for row in rows if row["decode_status"] == "decoded_first_frame"]
    originals = [row for row in rasters if row["variant"] == "original"]
    by_variant = {}
    for variant in ("original", "large", "medium", "small", "thumbnail"):
        group = [row for row in rasters if row["variant"] == variant]
        by_variant[variant] = {
            "files": len(group), "bytes": sum(row["bytes"] for row in group),
            "width": quantiles([row["width"] for row in group]),
            "height": quantiles([row["height"] for row in group]),
            "max_side": quantiles([max(row["width"], row["height"]) for row in group]),
            "max_side_below_224": sum(max(row["width"], row["height"]) < 224 for row in group),
            "max_side_below_384": sum(max(row["width"], row["height"]) < 384 for row in group),
            "min_side_below_128": sum(min(row["width"], row["height"]) < 128 for row in group),
            "alpha_or_transparency_files": sum(bool(row["has_alpha"]) for row in group),
            "common_dimensions": Counter(f'{row["width"]}x{row["height"]}' for row in group).most_common(12),
        }
    summary = {
        "root": str(args.root), "files": len(rows), "bytes": sum(row["bytes"] for row in rows),
        "extensions": dict(Counter(row["extension"] for row in rows)),
        "actual_formats": dict(Counter(row["format"] for row in rows)),
        "decode_status": dict(Counter(row["decode_status"] for row in rows)),
        "format_extension_mismatches": [row["path"] for row in rows if row["extension_matches_format"] is False],
        "decode_errors": [{"path": row["path"], "error": row["error"]} for row in rows if row["decode_status"] == "error"],
        "skipped_pixel_limit": [{key: row[key] for key in ["path", "format", "width", "height", "error"]}
                                for row in rows if row["decode_status"] == "skipped_pixel_limit"],
        "multiple_frame_files": [{"path": row["path"], "frames": row["frames"]} for row in rasters if row["frames"] > 1],
        "rgba_or_transparency_files": sum(bool(row["has_alpha"]) for row in rasters),
        "byte_duplicate_groups": len(duplicate_groups), "byte_duplicate_files": len(duplicates),
        "redundant_byte_duplicate_files": sum(len(group) - 1 for group in duplicate_groups),
        "redundant_byte_duplicate_bytes": sum((len(group) - 1) * group[0]["bytes"] for group in duplicate_groups),
        "unique_file_sha256": len(hashes), "raster_naming_families": len(families),
        "families_without_original": [row["family"] for row in family_rows if not row["has_original"]],
        "family_variant_combinations": dict(Counter(row["variants"] for row in family_rows)),
        "rasters_by_variant": by_variant,
        "query_dimension_archive_candidates": sum(bool(row["pixel_sha256"]) for row in rows),
        "queries": [{key: row[key] for key in ["path", "format", "width", "height", "extension_matches_format",
                                               "archive_byte_matches", "archive_pixel_matches"]} for row in queries],
        "method": {
            "inventory": "Every extracted file is SHA256-hashed. Pillow fully decodes the first frame of supported rasters within its pixel limit; oversized JPEG/PNG files receive header-only inspection.",
            "pixel_comparison": "All archive rasters matching a query's EXIF-oriented dimensions are hashed as RGBA dimensions+pixels, without resizing or ICC conversion.",
            "limitations": "HEIF content detected from ftyp is unsupported by this Pillow build; SVG is not rasterized. Oversized files are not decoded. Only first frames are decoded. Different resolution/compression/crop is not exact pixel equality. No perceptual matching or SKU inference.",
            "sample": f"36 distinct naming originals sampled uniformly with random.Random({args.seed}); this is an archive sample, not a sample of catalog-mapped wines.",
        },
    }
    (args.output / "images_summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    sample = random.Random(args.seed).sample(originals, min(36, len(originals)))
    write_csv(args.output / "images_original_sample.csv", [{"tile": index, "path": row["path"]} for index, row in enumerate(sample, 1)], ["tile", "path"])
    contact_sheet([args.root / row["path"] for row in sample], args.output / "images_originals_contact_sheet.jpg", f"Archive original sample: seed {args.seed} (not catalog-filtered)")
    contact_sheet([args.queries / row["path"] for row in queries], args.output / "images_queries_contact_sheet.jpg", "All 3 supplied evaluation queries", columns=3)
    print(json.dumps({"files": len(rows), "raster_images": len(rasters), "duplicate_groups": len(duplicate_groups),
                      "families": len(families), "elapsed_seconds": round(time.monotonic() - start, 1)}, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()
