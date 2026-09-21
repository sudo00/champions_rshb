#!/usr/bin/env python3
"""Inspect manually selected eval labels and spatially attributable OCR.

This is a geometry/OCR experiment, not an automatic label detector or an eval
accuracy measurement. Input photographs and catalog records are never edited.
"""

from __future__ import annotations

import argparse
import hashlib
import html
import importlib.metadata
from io import BytesIO
import json
import os
from pathlib import Path
import time
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT = ROOT / "data/audit/label_geometry"
DETECTOR = "PP-OCRv5_server_det"
RECOGNIZERS = {"russian": "eslav_PP-OCRv5_mobile_rec", "latin": "latin_PP-OCRv5_mobile_rec"}

# Manually traced on 1368 x 1824 previews; converted to oriented original pixels.
# These polygons describe visible label boundaries, not calibrated 3D landmarks.
ANNOTATIONS = [
    {"file": "019c68d0.jpg", "reading": "Табия / Пино Нуар / полусухое / 2025",
     "target": [[419, 645], [710, 670], [1073, 738], [1003, 1240], [973, 1426],
                [944, 1472], [791, 1494], [621, 1480], [435, 1427], [347, 1347]],
     "neighbors": [[[0, 87], [280, 148], [337, 201], [302, 1138], [263, 1191], [0, 1251]],
                   [[1368, 805], [1368, 1615], [1252, 1535]]],
     "note": "Слева обрезанная этикетка другого розового вина; справа небольшой фрагмент. Видимая центральная этикетка целиком в кадре, но её края изогнуты."},
    {"file": "02eef911.webp", "reading": "Массандра / Мускатель Массандра Белый / год урожая 2023",
     "target": [[362, 392], [413, 366], [639, 349], [831, 350], [965, 384],
                [947, 1365], [883, 1403], [747, 1437], [570, 1423], [449, 1388], [410, 1344]],
     "neighbors": [[[0, 376], [241, 388], [304, 1381], [223, 1421], [0, 1436]],
                   [[1003, 389], [1368, 342], [1368, 1444], [1124, 1401], [983, 1318]]],
     "note": "Центральная этикетка целиком видима. Справа другое вино с текстом «красного…»; OCR всего кадра может смешать белое и красное. 1894 подписано как год основания, 2023 — год урожая."},
    {"file": "096ca74e.jpg", "reading": "ARISTOV / DONUM XXIV / БРЮТ / 2023 / выдержка 24 месяца",
     "target": [[289, 459], [556, 447], [839, 448], [1111, 446], [1069, 1237],
                [981, 1284], [800, 1314], [635, 1316], [465, 1294], [356, 1255]],
     "neighbors": [],
     "note": "Одна крупная бутылка: наклон и цилиндрический изгиб строк видны даже при хорошем качестве снимка."},
]


def sha256(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")
    temporary.replace(path)


def validate_existing_json(path: Path, expected: Any) -> None:
    if path.exists() and json.loads(path.read_text()) != expected:
        raise ValueError(f"Stored provenance differs; use a new output directory: {path}")


def save_prepared(manifest_path: Path, manifest: dict, pending_writes: list[tuple[Path, bytes]]) -> None:
    """Reject changed geometry before touching any previously prepared input."""
    validate_existing_json(manifest_path, manifest)
    for path, encoded in pending_writes:
        path.write_bytes(encoded)
    write_json(manifest_path, manifest)


def point_in_polygon(point: list[float], polygon: list[list[float]]) -> bool:
    """Ray casting used only for diagnostic OCR region attribution."""
    x, y = point
    inside = False
    previous = polygon[-1]
    for current in polygon:
        x1, y1 = previous
        x2, y2 = current
        if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
            inside = not inside
        previous = current
    return inside


def to_original(polygon: list[list[float]], crop_box: list[int], scale: list[float]) -> list[list[float]]:
    """Map OCR input pixel coordinates into the EXIF-oriented source frame."""
    if min(scale) <= 0:
        raise ValueError("Resize scales must be positive")
    return [[round(x / scale[0] + crop_box[0], 3), round(y / scale[1] + crop_box[1], 3)]
            for x, y in polygon]


def region_for_line(polygon: list[list[float]], annotation: dict) -> str:
    # The centre heuristic is transparent and imperfect near boundaries.
    center = [sum(point[axis] for point in polygon) / len(polygon) for axis in (0, 1)]
    if point_in_polygon(center, annotation["target_polygon"]):
        return "target"
    for index, neighbor in enumerate(annotation["neighbor_polygons"], 1):
        if point_in_polygon(center, neighbor):
            return f"neighbor_{index}"
    return "outside_annotations"


def prepare(args: argparse.Namespace) -> None:
    from PIL import Image, ImageOps

    args.output.mkdir(parents=True, exist_ok=True)
    images = []
    pending_writes: list[tuple[Path, bytes]] = []
    for manual in ANNOTATIONS:
        source = ROOT / "data/eval/queries" / manual["file"]
        with Image.open(source) as opened:
            raw_size = list(opened.size)
            orientation = opened.getexif().get(274)
            image = ImageOps.exif_transpose(opened).convert("RGB")
        width, height = image.size
        if (width, height) != (3024, 4032):
            raise ValueError("Manual annotation belongs to the supplied 3024 x 4032 image")
        convert = lambda polygon: [[round(x * width / 1368), round(y * height / 1824)] for x, y in polygon]
        polygon = convert(manual["target"])
        margin = 20
        box = [max(0, min(p[0] for p in polygon) - margin), max(0, min(p[1] for p in polygon) - margin),
               min(width, max(p[0] for p in polygon) + margin), min(height, max(p[1] for p in polygon) + margin)]
        identifier = source.stem
        image_dir = args.output / "images"
        image_dir.mkdir(exist_ok=True)
        # A display copy preserves geometry; original inputs are untouched.
        frame_path = image_dir / f"{identifier}_frame.jpg"
        frame_bytes = BytesIO()
        image.save(frame_bytes, format="JPEG", quality=92)
        pending_writes.append((frame_path, frame_bytes.getvalue()))
        items = [("crop", box)]
        if identifier == "02eef911":
            items.append(("full", [0, 0, width, height]))
        prepared = []
        for name, crop_box in items:
            cropped = image.crop(tuple(crop_box))
            factor = min(1.0, args.max_side / max(cropped.size))
            resized = cropped.resize(tuple(round(n * factor) for n in cropped.size), Image.Resampling.LANCZOS)
            path = image_dir / f"{identifier}_{name}.png"
            crop_bytes = BytesIO()
            resized.save(crop_bytes, format="PNG")
            encoded = crop_bytes.getvalue()
            pending_writes.append((path, encoded))
            prepared.append({"name": name, "path": str(path.relative_to(args.output)), "sha256": hashlib.sha256(encoded).hexdigest(),
                             "crop_box_original_xyxy": crop_box, "ocr_size": list(resized.size),
                             "scale_xy": [resized.width / cropped.width, resized.height / cropped.height]})
        images.append({"id": identifier, "source_path": str(source.relative_to(ROOT)), "source_sha256": sha256(source),
                       "raw_size": raw_size, "exif_orientation": orientation, "oriented_size": [width, height],
                       "annotation_method": "manual_visual_polygon", "coordinate_system": "exif_oriented_original_pixels",
                       "target_polygon": polygon, "neighbor_polygons": [convert(p) for p in manual["neighbors"]],
                       "target_visible_label_inside_frame": True, "catalog_slug": None,
                       "human_reading_not_catalog_ground_truth": manual["reading"], "note": manual["note"],
                       "display_path": str(frame_path.relative_to(args.output)), "prepared": prepared})
    manifest = {"schema_version": 1, "max_side": args.max_side, "automatic_detection": False,
                "rectification": "none", "images": images}
    manifest_path = args.output / "annotations.json"
    # Compare all geometry/configuration/source hashes BEFORE replacing images.
    save_prepared(manifest_path, manifest, pending_writes)
    report(args)


def run(args: argparse.Namespace) -> None:
    manifest_path = args.output / "annotations.json"
    manifest = json.loads(manifest_path.read_text())
    cache = ROOT / "data/audit/ocr/cache"
    for name, value in {"PADDLE_PDX_CACHE_HOME": cache / "paddlex", "PADDLE_HOME": cache / "paddle",
                        "HF_HOME": cache / "huggingface", "XDG_CACHE_HOME": cache / "xdg"}.items():
        os.environ.setdefault(name, str(value))
    os.environ.setdefault("PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK", "True")
    from paddleocr import PaddleOCR
    from PIL import Image
    import numpy as np

    config = {"manifest_sha256": sha256(manifest_path), "device": args.device, "cpu_threads": args.cpu_threads,
              "detector": DETECTOR, "recognizers": RECOGNIZERS, "max_side": manifest["max_side"],
              "color_order": "BGR", "unwarping": False, "textline_orientation": False, "enable_mkldnn": False,
              "recognition_threshold": 0.0,
              "versions": {name: importlib.metadata.version(name) for name in ["paddleocr", "paddlepaddle", "paddlex", "numpy", "Pillow"]}}
    config_path = args.output / "run_config.json"
    validate_existing_json(config_path, config)
    write_json(config_path, config)
    model_provenance = {}
    for model_name in [DETECTOR, *RECOGNIZERS.values()]:
        model_dir = args.models_dir / model_name
        files = {str(p.relative_to(model_dir)): sha256(p) for p in sorted(model_dir.rglob("*")) if p.is_file()}
        if not files:
            raise ValueError(f"Model files missing: {model_dir}")
        provenance = {"name": model_name, "files_sha256": files}
        model_path = args.output / "models" / f"{model_name}.json"
        validate_existing_json(model_path, provenance)
        model_provenance[model_path] = provenance
    # Validate every cached model before writing any provenance or inferring.
    for model_path, provenance in model_provenance.items():
        write_json(model_path, provenance)
    for language, model in RECOGNIZERS.items():
        pending = [(entry, item) for entry in manifest["images"] for item in entry["prepared"]
                   if not (args.output / "results" / f"{entry['id']}_{item['name']}_{language}.json").exists()]
        if not pending:
            continue
        engine = PaddleOCR(text_detection_model_name=DETECTOR, text_detection_model_dir=str(args.models_dir / DETECTOR),
                           text_recognition_model_name=model, text_recognition_model_dir=str(args.models_dir / model),
                           use_doc_orientation_classify=False, use_doc_unwarping=False, use_textline_orientation=False,
                           text_rec_score_thresh=0.0, text_det_limit_side_len=manifest["max_side"], text_det_limit_type="max",
                           device=args.device, cpu_threads=args.cpu_threads, enable_mkldnn=False)
        for entry, item in pending:
            source = ROOT / entry["source_path"]
            input_path = args.output / item["path"]
            if sha256(source) != entry["source_sha256"] or sha256(input_path) != item["sha256"]:
                raise ValueError("Input photograph or crop changed")
            with Image.open(input_path) as opened:
                array = np.asarray(opened.convert("RGB"))[:, :, ::-1].copy()
            started = time.perf_counter()
            predictions = list(engine.predict(array))
            duration = time.perf_counter() - started
            if len(predictions) != 1:
                raise ValueError("Expected one OCR result")
            raw = predictions[0].json
            raw = json.loads(raw) if isinstance(raw, str) else raw
            data = raw.get("res", raw)
            texts, scores, polygons = data["rec_texts"], data["rec_scores"], data["rec_polys"]
            if not len(texts) == len(scores) == len(polygons):
                raise ValueError("Inconsistent OCR observations")
            lines = []
            for text, score, polygon in zip(texts, scores, polygons):
                original = to_original(polygon, item["crop_box_original_xyxy"], item["scale_xy"])
                lines.append({"text": text, "confidence": float(score), "polygon_ocr": polygon,
                              "polygon_original": original, "region_by_polygon_center": region_for_line(original, entry)})
            result = {"image_id": entry["id"], "variant": item["name"], "engine": language,
                      "config_sha256": sha256(config_path), "input_sha256": sha256(input_path),
                      "inference_seconds": duration, "lines": lines, "raw": raw}
            write_json(args.output / "results" / f"{entry['id']}_{item['name']}_{language}.json", result)
            print(f"{entry['id']} {item['name']} {language}: {len(lines)} lines, {duration:.2f}s", flush=True)
        del engine
    report(args)


def svg_image(path: str, size: list[int], polygons: list[dict], prefix: str) -> str:
    shapes = []
    for index, polygon in enumerate(polygons):
        points = " ".join(f"{x},{y}" for x, y in polygon["points"])
        label = html.escape(polygon["label"])
        key = f"{prefix}_{index}"
        shape = f'<polygon data-key="{key}" points="{points}" class="{polygon["class"]}"><title>{label}</title></polygon>'
        shapes.append(f'<a href="#{key}">{shape}</a>' if polygon.get("number") is not None else shape)
        x, y = polygon["points"][0]
        if polygon.get("number") is not None:
            shapes.append(f'<text x="{x}" y="{max(18, y - 5)}" font-size="{max(size) / 65}" fill="#071b35" stroke="white" stroke-width="1">{polygon["number"]}</text>')
    return f'<svg viewBox="0 0 {size[0]} {size[1]}" role="img" aria-label="Области изображения"><image href="{html.escape(path)}" width="{size[0]}" height="{size[1]}"/>{"".join(shapes)}</svg>'


def report(args: argparse.Namespace) -> None:
    manifest = json.loads((args.output / "annotations.json").read_text())
    sections = []
    summary = []
    for entry in manifest["images"]:
        regions = [{"points": entry["target_polygon"], "label": "Ручная целевая область", "class": "target"}]
        regions += [{"points": p, "label": f"Сосед {i}", "class": "neighbor"} for i, p in enumerate(entry["neighbor_polygons"], 1)]
        frame = svg_image(entry["display_path"], entry["oriented_size"], regions, entry["id"] + "_manual")
        details = []
        for item in entry["prepared"]:
            for language in RECOGNIZERS:
                result_path = args.output / "results" / f"{entry['id']}_{item['name']}_{language}.json"
                if not result_path.exists():
                    continue
                result = json.loads(result_path.read_text())
                prefix = f"{entry['id']}_{item['name']}_{language}"
                polygons = [{"points": line["polygon_ocr"], "label": line["text"], "class": "ocr " + line["region_by_polygon_center"], "number": i}
                            for i, line in enumerate(result["lines"], 1)]
                annotated = svg_image(item["path"], item["ocr_size"], polygons, prefix)
                if item["name"] == "crop":
                    original_polygons = [{**polygon, "points": line["polygon_original"]}
                                         for polygon, line in zip(polygons, result["lines"])]
                    original_svg = svg_image(entry["display_path"], entry["oriented_size"], original_polygons, prefix)
                    annotated = (f'<div class="visual"><p><button type="button" data-view="input" aria-pressed="true">На входе OCR</button> '
                                 f'<button type="button" data-view="original" aria-pressed="false">На исходном кадре</button></p>'
                                 f'<div data-layer="input">{annotated}</div><div data-layer="original" hidden>{original_svg}</div></div>')
                rows = "".join(f'<tr id="{prefix}_{i}" data-key="{prefix}_{i}"><td>{i + 1}</td><td>{html.escape(line["text"])}</td>'
                               f'<td>{line["confidence"]:.3f}</td><td>{line["region_by_polygon_center"]}</td></tr>' for i, line in enumerate(result["lines"]))
                details.append(f'<details open><summary>{item["name"]} · {language} · {result["inference_seconds"]:.1f} с (CPU)</summary>'
                               f'<div class="comparison"><div>{annotated}</div><div class="text"><table><tr><th>#</th><th>OCR</th><th>Score</th><th>Область</th></tr>{rows}</table>'
                               f'<a href="{result_path.relative_to(args.output)}">JSON: OCR + координаты исходного кадра</a></div></div></details>')
                summary.append({"image": entry["id"], "variant": item["name"], "engine": language,
                                "lines": len(result["lines"]), "outside_target": sum(l["region_by_polygon_center"] != "target" for l in result["lines"]),
                                "inference_seconds": result["inference_seconds"]})
        crop = entry["prepared"][0]
        sections.append(f'<section><h2>{entry["id"]}</h2><p>{html.escape(entry["human_reading_not_catalog_ground_truth"])}</p>'
                        f'<p>{html.escape(entry["note"])}</p><div class="overview"><div><h3>Ручные области исходного кадра</h3>{frame}</div>'
                        f'<div><h3>Вырезка без ректификации</h3><img src="{crop["path"]}" alt="Целевая этикетка"/></div></div>{"".join(details)}</section>')
    document = '''<!doctype html><html lang="ru"><meta charset="utf-8"><title>Eval: области этикеток и OCR</title>
<style>body{font:16px system-ui;max-width:1500px;margin:2rem auto;padding:0 1rem;color:#142030}h1,h2{line-height:1.2}section{border-top:2px solid #ccd7e2;margin:2rem 0;padding-top:1rem}.overview,.comparison{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:1.5rem}.overview img{max-width:100%;max-height:650px;object-fit:contain}svg{width:100%;max-height:700px}polygon{stroke-width:3;vector-effect:non-scaling-stroke;fill-opacity:.07}.target{stroke:#00bd82;fill:#00bd82}.neighbor{stroke:#f29924;fill:#f29924}.ocr{stroke:#176ce7;fill:#176ce7}.ocr[class*=neighbor]{stroke:#d55e00;fill:#d55e00}.ocr.outside_annotations{stroke:#b236ba;fill:#b236ba}polygon.active{stroke:#ff194c;fill:#ff194c;fill-opacity:.35;stroke-width:5}table{border-collapse:collapse;width:100%;font-size:14px}td,th{padding:7px;border-bottom:1px solid #ddd;text-align:left}tr.active,tr:target{background:#ffe8ed}.text{overflow:auto}summary{cursor:pointer;font-weight:700;padding:12px;background:#f0f4f9}details{margin:18px 0}a{color:#195da7}@media(max-width:800px){.overview,.comparison{grid-template-columns:1fr}}</style>
<h1>Три реальные фотографии: целевая этикетка и источники OCR</h1>
<p><b>Области этикеток размечены вручную. Автоматического детектора и ректификации в этом пилоте нет.</b> Зелёный — выбранная центральная этикетка; оранжевый — соседи. Видимые границы приблизительны и не являются 3D-ориентирами. Названия прочитаны человеком и не подтверждают каталоговый slug.</p>
<p>Наведите на строку таблицы или OCR-полигон, чтобы увидеть связанную область; нажатие на полигон ведёт к строке. Синий — текст целевой этикетки, оранжевый — соседней, фиолетовый — вне размеченных областей. Принадлежность определяется центром текстового полигона, поэтому на границах возможны ошибки.</p>
<p>Вариант full на Массандре демонстрирует OCR всего кадра. Оба входа ограничены 1600 пикселями по длинной стороне, поэтому crop одновременно исключает соседей и увеличивает эффективный масштаб этикетки. Это иллюстрация, а не изолированная оценка влияния обрезки. Score OCR не означает вероятность правильного вина; скорость CPU не представляет будущий API.</p>
''' + "".join(sections) + '''<script>document.querySelectorAll('[data-key]').forEach(el=>{const mark=on=>document.querySelectorAll('[data-key="'+el.dataset.key+'"]').forEach(item=>item.classList.toggle('active',on));el.addEventListener('mouseenter',()=>mark(true));el.addEventListener('mouseleave',()=>mark(false));});document.querySelectorAll('[data-view]').forEach(button=>button.addEventListener('click',()=>{const group=button.closest('.visual');group.querySelectorAll('[data-layer]').forEach(layer=>layer.toggleAttribute('hidden',layer.dataset.layer!==button.dataset.view));group.querySelectorAll('[data-view]').forEach(option=>option.setAttribute('aria-pressed',String(option===button)));}));</script></html>'''
    (args.output / "review.html").write_text(document)
    write_json(args.output / "summary.json", {"automatic_detection": False, "rectification": False, "results": summary})
    print(f"Report: {args.output / 'review.html'}", flush=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["prepare", "run", "report"])
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--max-side", type=int, default=1600)
    parser.add_argument("--models-dir", type=Path, default=ROOT / "data/audit/ocr/cache/paddlex/official_models")
    parser.add_argument("--device", default="cpu")
    parser.add_argument("--cpu-threads", type=int, default=4)
    args = parser.parse_args()
    args.output = args.output.resolve()
    if args.max_side < 32 or args.cpu_threads < 1:
        parser.error("--max-side must be >=32; --cpu-threads must be >=1")
    {"prepare": prepare, "run": run, "report": report}[args.command](args)


if __name__ == "__main__":
    main()
