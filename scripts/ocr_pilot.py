#!/usr/bin/env python3
"""Prepare a deterministic OCR pilot and retain raw, attributable observations.

Nothing in this script edits catalog records or assigns catalog correctness.
Paddle imports are deferred so preparing/reviewing a manifest needs only Pillow.
"""

from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import html
import importlib.metadata
import json
import math
import os
from pathlib import Path
import platform
import re
import sys
import time
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT = ROOT / "data/audit/ocr/pilot64_eslav_latin"
MODELS = {
    "russian": "eslav_PP-OCRv5_mobile_rec",
    "latin": "latin_PP-OCRv5_mobile_rec",
}
DETECTION_MODEL = "PP-OCRv5_server_det"


def sha256(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")
    temporary.replace(path)


def stable_key(value: str, seed: int) -> str:
    return hashlib.sha256(f"{seed}:{value}".encode()).hexdigest()


def prepare(args: argparse.Namespace) -> None:
    from PIL import Image

    catalog = args.catalog.resolve()
    rows = [json.loads(line) for line in catalog.read_text().splitlines() if line]
    by_hash: dict[str, dict[str, Any]] = {}
    fields = ("slug", "title", "winery", "category", "grapes", "review_action")
    for row in rows:
        for reference in row.get("references", []):
            digest = reference["reference_sha256"]
            entry = by_hash.setdefault(digest, {
                "sha256": digest,
                "reference_path": reference["reference_path"],
                "source_path": reference["reference_source_path"],
                "provenance": reference["reference_provenance"],
                "image_size": reference["image_size"],
                "cards": [],
            })
            card = {key: row.get(key) for key in fields}
            if card not in entry["cards"]:
                entry["cards"].append(card)
    pool = sorted(by_hash.values(), key=lambda item: stable_key(item["sha256"], args.seed))
    if not 1 <= args.size <= len(pool):
        raise ValueError("Pilot size must be within unique reference count")
    selected: dict[str, dict[str, Any]] = {}

    def take(entries: list[dict], count: int, reason: str) -> None:
        added = 0
        for entry in entries:
            if len(selected) >= args.size or added >= count:
                break
            if entry["sha256"] in selected:
                continue
            selected[entry["sha256"]] = {**entry, "selection_reason": reason}
            added += 1

    take([item for item in pool if len(item["cards"]) > 1], 8, "shared_reference")
    for provenance in sorted({item["provenance"] for item in pool}):
        take([item for item in pool if item["provenance"] == provenance], 3,
             f"provenance:{provenance}")
    for category in sorted({card["category"] for item in pool for card in item["cards"]}):
        take([item for item in pool if any(card["category"] == category for card in item["cards"])],
             2, f"category:{category}")
    take(sorted(pool, key=lambda item: max(item["image_size"])), 6, "small_resolution")
    take([item for item in pool if any(re.search(r"[A-Za-z]{4}", card["title"])
                                      for card in item["cards"])], 6, "latin_in_title")
    take([item for item in pool if any(re.search(r"пакет|короб|тетрапак|bag.?in.?box", card["title"], re.I)
                                      for card in item["cards"])], 3, "packaging_in_title")
    seen_wineries = {card["winery"] for item in selected.values() for card in item["cards"]}
    for item in pool:
        wineries = {card["winery"] for card in item["cards"]}
        if wineries - seen_wineries:
            take([item], 1, "additional_winery")
            seen_wineries.update(wineries)
    take(pool, args.size, "seeded_fill")
    # A short smoke run should exercise different cases, not just the first
    # shared-reference selection bucket. Membership of the 64-image set stays
    # independent from this deterministic ordering.
    ordered = []
    prefixes = ["shared_reference", "provenance:user_supplied_replacement",
                "small_resolution", "latin_in_title", "category:Оранжевое",
                "provenance:user_confirmed_renamed_archive", "additional_winery",
                "category:Белое"]
    for prefix in prefixes:
        match = next((entry for entry in selected.values()
                      if entry["selection_reason"] == prefix and entry not in ordered), None)
        if match:
            ordered.append(match)
    ordered.extend(entry for entry in selected.values() if entry not in ordered)
    for item in selected.values():
        source = ROOT / item["reference_path"]
        if sha256(source) != item["sha256"]:
            raise ValueError(f"Reference hash changed: {source}")
        with Image.open(source) as image:
            item["image_mode"] = image.mode
            item["has_alpha"] = "A" in image.getbands() or "transparency" in image.info
    manifest = {
        "schema_version": 1,
        "catalog_path": str(catalog.relative_to(ROOT)),
        "catalog_sha256": sha256(catalog),
        "catalog_cards": len(rows),
        "catalog_unique_references": len(pool),
        "seed": args.seed,
        "selection": "purposive_diverse_sample_not_an_error_prevalence_estimate",
        "size": len(selected),
        "images": ordered,
    }
    path = args.output / "manifest.json"
    if path.exists() and json.loads(path.read_text()) != manifest:
        raise ValueError("Existing pilot manifest differs; use a new output directory")
    write_json(path, manifest)
    print(f"Prepared {len(selected)} unique references: {path}")


def normalized_image(path: Path, max_side: int = 1600) -> tuple[Any, dict]:
    from PIL import Image, ImageOps

    with Image.open(path) as opened:
        image = ImageOps.exif_transpose(opened).convert("RGBA")
        oriented_size = image.size
        background = Image.new("RGBA", image.size, (255, 255, 255, 255))
        image = Image.alpha_composite(background, image).convert("RGB")
    # Upscale small originals at most 2x; cap large inputs for a bounded pilot.
    scale = min(2.0, max_side / max(image.size))
    size = tuple(max(1, round(value * scale)) for value in image.size)
    image = image.resize(size, Image.Resampling.LANCZOS)
    return image, {
        "operation": "exif_transpose_alpha_on_white_resize",
        "oriented_original_size": list(oriented_size),
        "ocr_size": list(size),
        "scale_x": size[0] / oriented_size[0],
        "scale_y": size[1] / oriented_size[1],
        "bbox_coordinate_system": "normalized_ocr_image_pixels",
    }


def observations(raw: dict) -> list[dict]:
    data = raw.get("res", raw)
    texts, scores = data.get("rec_texts", []), data.get("rec_scores", [])
    polygons = data.get("rec_polys", data.get("dt_polys", []))
    if not (len(texts) == len(scores) == len(polygons)):
        raise ValueError("OCR texts, scores and polygons have inconsistent lengths")
    return [{"text": text, "confidence": float(score), "polygon": polygon}
            for text, score, polygon in zip(texts, scores, polygons)]


def run(args: argparse.Namespace) -> None:
    output = args.output.resolve()
    manifest_path = output / "manifest.json"
    manifest = json.loads(manifest_path.read_text())
    catalog_path = ROOT / manifest["catalog_path"]
    if sha256(catalog_path) != manifest["catalog_sha256"]:
        raise ValueError("Catalog changed since pilot preparation")
    cache = ROOT / "weights/cache/ocr"
    os.environ.setdefault("PADDLE_PDX_CACHE_HOME", str(cache / "paddlex"))
    os.environ.setdefault("PADDLE_HOME", str(cache / "paddle"))
    os.environ.setdefault("HF_HOME", str(cache / "huggingface"))
    os.environ.setdefault("XDG_CACHE_HOME", str(cache / "xdg"))
    if args.models_dir:
        os.environ.setdefault("PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK", "True")
    from paddleocr import PaddleOCR
    import numpy as np

    config = {
        "schema_version": 1,
        "manifest_sha256": sha256(manifest_path),
        "device": args.device,
        "detector": DETECTION_MODEL,
        "recognizers": MODELS,
        "max_side": args.max_side,
        "cpu_threads": args.cpu_threads,
        "enable_mkldnn": False,
        "ndarray_color_order": "BGR",
        "minimum_recognition_confidence": 0.0,
        "use_doc_orientation_classify": False,
        "use_doc_unwarping": False,
        "use_textline_orientation": False,
        "python": sys.version,
        "platform": platform.platform(),
        "versions": {name: importlib.metadata.version(name)
                     for name in ["paddleocr", "paddlepaddle", "paddlex", "numpy", "Pillow"]},
    }
    config_path = output / "run_config.json"
    if config_path.exists() and json.loads(config_path.read_text()) != config:
        raise ValueError("Run configuration changed; use a separate output directory")
    write_json(config_path, config)
    images = manifest["images"][:args.limit] if args.limit else manifest["images"]
    for engine_name, recognition_model in MODELS.items():
        pending = [entry for entry in images
                   if not (output / "results" / engine_name / (entry["sha256"] + ".json")).exists()]
        if not pending:
            continue
        options = {
            "text_detection_model_name": DETECTION_MODEL,
            "text_recognition_model_name": recognition_model,
            "use_doc_orientation_classify": False,
            "use_doc_unwarping": False,
            "use_textline_orientation": False,
            "text_rec_score_thresh": 0.0,
            "text_det_limit_side_len": args.max_side,
            "text_det_limit_type": "max",
            "device": args.device,
            "cpu_threads": args.cpu_threads,
            "enable_mkldnn": False,
        }
        if args.models_dir:
            options["text_detection_model_dir"] = str(args.models_dir / DETECTION_MODEL)
            options["text_recognition_model_dir"] = str(args.models_dir / recognition_model)
        engine = PaddleOCR(**options)
        for model_name in [DETECTION_MODEL, recognition_model]:
            model_dir = ((args.models_dir or cache / "paddlex/official_models") / model_name)
            weight_files = {str(path.relative_to(model_dir)): sha256(path)
                            for path in sorted(model_dir.rglob("*"))
                            if path.is_file() and path.suffix in {".json", ".yml", ".yaml", ".pdiparams"}}
            if not weight_files:
                raise ValueError(f"Could not record model files: {model_dir}")
            weights_path = output / "models" / (model_name + ".json")
            model_provenance = {"model": model_name, "files_sha256": weight_files}
            if weights_path.exists() and json.loads(weights_path.read_text()) != model_provenance:
                raise ValueError(f"Model weights changed: {model_name}")
            write_json(weights_path, model_provenance)
        for number, entry in enumerate(pending, 1):
            source = ROOT / entry["reference_path"]
            if sha256(source) != entry["sha256"]:
                raise ValueError(f"Reference hash changed: {source}")
            image, preprocessing = normalized_image(source, args.max_side)
            prepared_path = output / "prepared" / (entry["sha256"] + ".png")
            prepared_path.parent.mkdir(parents=True, exist_ok=True)
            image.save(prepared_path)
            started = time.perf_counter()
            # Paddle's ndarray input follows OpenCV's BGR convention.
            predictions = list(engine.predict(np.asarray(image)[:, :, ::-1].copy()))
            elapsed = time.perf_counter() - started
            if len(predictions) != 1:
                raise ValueError("Expected one OCR result per reference")
            raw = predictions[0].json
            if isinstance(raw, str):
                raw = json.loads(raw)
            lines = observations(raw)
            result = {
                "sha256": entry["sha256"],
                "reference_path": entry["reference_path"],
                "slugs": [card["slug"] for card in entry["cards"]],
                "engine": engine_name,
                "recognizer": recognition_model,
                "run_config_sha256": sha256(config_path),
                "preprocessing": preprocessing,
                "inference_seconds": elapsed,
                "lines": lines,
                "raw": raw,
                "catalog_verdict": "not_assessed",
            }
            write_json(output / "results" / engine_name / (entry["sha256"] + ".json"), result)
            print(f"{engine_name} {number}/{len(pending)} {entry['cards'][0]['slug']} "
                  f"{len(lines)} lines, {elapsed:.2f}s", flush=True)
        del engine
    report(args)


def render_ocr_evidence(image_link: str, results: dict[str, dict]) -> tuple[str, str]:
    """Render image and OCR polygons in one SVG coordinate system.

    Numeric labels are display-only. Source polygons and text are never edited.
    Both profiles must refer to the exact same prepared-image geometry.
    """
    sizes = {tuple(result["preprocessing"]["ocr_size"]) for result in results.values()}
    if len(sizes) != 1:
        raise ValueError("OCR profiles must share the prepared-image dimensions")
    width, height = next(iter(sizes))
    if not all(isinstance(value, int) and value > 0 for value in (width, height)):
        raise ValueError("OCR dimensions must be positive integers")
    layers, panels = [], []
    label_size = width / 27
    for engine, result in results.items():
        if result["preprocessing"]["bbox_coordinate_system"] != "normalized_ocr_image_pixels":
            raise ValueError("OCR polygons must use prepared-image pixel coordinates")
        escaped_engine = html.escape(engine, quote=True)
        regions, rows = [], []
        for number, line in enumerate(result["lines"], 1):
            polygon = line["polygon"]
            if len(polygon) != 4 or any(len(point) != 2 for point in polygon):
                raise ValueError("An OCR polygon must contain four (x, y) points")
            coordinates = [value for point in polygon for value in point]
            if not all(isinstance(value, (float, int)) and not isinstance(value, bool)
                       and math.isfinite(value) for value in coordinates):
                raise ValueError("OCR polygon coordinates must be finite numbers")
            confidence = float(line["confidence"])
            if not math.isfinite(confidence) or not 0 <= confidence <= 1:
                raise ValueError("OCR confidence must be finite and within [0, 1]")
            band = "high" if confidence >= 0.9 else "medium" if confidence >= 0.6 else "low"
            key = html.escape(f"{engine}:{number}", quote=True)
            text = html.escape(line["text"] or "(пустая строка)", quote=True)
            points = " ".join(f"{x},{y}" for x, y in polygon)
            label_x = min(point[0] for point in polygon)
            label_y = max(label_size, min(point[1] for point in polygon) - label_size / 4)
            regions.append(
                f'<g class="region confidence-{band}" data-region-key="{key}" tabindex="0" '
                f'role="button" aria-label="Строка {number}: {text}; confidence {confidence:.3f}">'
                f'<title>#{number}: {text} · confidence {confidence:.3f}</title>'
                f'<polygon points="{points}" vector-effect="non-scaling-stroke"/>'
                f'<text x="{label_x}" y="{label_y}" font-size="{label_size}">{number}</text></g>'
            )
            rows.append(
                f'<tr class="evidence-row confidence-{band}" data-region-key="{key}" '
                f'tabindex="0" role="button" aria-label="Показать строку {number}: {text}">'
                f'<td class="number">{number}</td><td class="ocr-text">{text}</td>'
                f'<td class="confidence-value">{confidence:.3f}</td></tr>'
            )
        layers.append(f'<g data-ocr-engine="{escaped_engine}" data-role="ocr-layer">'
                      + "".join(regions) + "</g>")
        panels.append(
            f'<div class="ocr-panel" data-ocr-engine="{escaped_engine}" data-role="ocr-panel">'
            '<table><thead><tr><th>№</th><th>Распознанная строка</th><th>Confidence</th></tr></thead>'
            '<tbody>' + "".join(rows) + '</tbody></table>'
            + ('<p>Текстовых строк не найдено.</p>' if not rows else '') + '</div>'
        )
    escaped_link = html.escape(image_link, quote=True)
    figure = (f'<svg class="evidence-image" viewBox="0 0 {width} {height}" '
              'preserveAspectRatio="xMidYMid meet" role="group" aria-label="Фото и области строк OCR">'
              f'<image href="{escaped_link}" width="{width}" height="{height}"/>'
              + "".join(layers) + '</svg>'
              f'<a class="original-link" href="{escaped_link}" target="_blank" rel="noopener">'
              'Открыть фото в полном размере ↗</a>')
    return figure, "".join(panels)


def report(args: argparse.Namespace) -> None:
    output = args.output.resolve()
    manifest = json.loads((output / "manifest.json").read_text())
    configuration = output / "run_config.json"
    engine_names = (json.loads(configuration.read_text())["recognizers"]
                    if configuration.exists() else MODELS)
    sections, pending_sections = [], []
    completed = Counter({name: 0 for name in engine_names})
    line_counts = Counter()
    timing = Counter()
    for entry in manifest["images"]:
        results = {}
        for engine_name in engine_names:
            path = output / "results" / engine_name / (entry["sha256"] + ".json")
            if not path.exists():
                continue
            result = json.loads(path.read_text())
            completed[engine_name] += 1
            line_counts[engine_name] += len(result["lines"])
            timing[engine_name] += result["inference_seconds"]
            results[engine_name] = result
        cards = "".join("<p><b>" + html.escape(card["title"]) + "</b><br>" +
                        html.escape(card["slug"]) + "<br>" +
                        html.escape(f"{card['winery']} · {card['category']} · {card['grapes']}") + "</p>"
                        for card in entry["cards"])
        prepared = output / "prepared" / (entry["sha256"] + ".png")
        image_link = os.path.relpath(prepared if prepared.exists() else ROOT / entry["reference_path"], output)
        if results:
            if not prepared.exists():
                raise ValueError(f"Cannot overlay OCR polygons without prepared image: {prepared}")
            from PIL import Image

            with Image.open(prepared) as prepared_image:
                if any(tuple(value["preprocessing"]["ocr_size"]) != prepared_image.size
                       for value in results.values()):
                    raise ValueError(f"Prepared image dimensions do not match OCR: {prepared}")
            figure, panels = render_ocr_evidence(image_link, results)
            sections.append(f'<section class="evidence-card"><div class="figure">{figure}</div>'
                            f'<div class="details">{cards}<small>{html.escape(entry["selection_reason"])}</small>'
                            f'<p class="frame-note">Рамки показывают строки OCR, не границы этикетки.</p>'
                            f'{panels}</div></section>')
        else:
            pending_sections.append(f'<section class="pending-card"><img loading="lazy" '
                                    f'src="{html.escape(image_link, quote=True)}" alt="Эталон">'
                                    f'<div>{cards}<p>OCR ещё не выполнен.</p></div></section>')
    buttons = []
    labels = {"russian": "RU", "latin": "Latin", "cyrillic": "Cyrillic"}
    for engine in engine_names:
        buttons.append(f'<button type="button" data-engine-button="{html.escape(engine, quote=True)}">'
                       f'{html.escape(labels.get(engine, engine))}</button>')
    document = """<!doctype html><html lang="ru"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>OCR: текст и области на фотографии</title><style>
:root{color-scheme:light;--ink:#172126;--muted:#586970;--accent:#2563eb}
*{box-sizing:border-box}body{font:16px/1.5 system-ui,sans-serif;color:var(--ink);margin:0;
background:#f5f6f3}main{max-width:1260px;margin:auto;padding:32px 24px}h1{font-size:32px;
line-height:1.2;margin:0 0 14px}header p{max-width:900px}.toolbar{position:sticky;top:0;
z-index:10;display:flex;align-items:center;gap:8px;flex-wrap:wrap;padding:12px 24px;
border-bottom:1px solid #d6ddd7;background:#ffffffed;backdrop-filter:blur(10px)}button{
font:inherit;cursor:pointer;border:1px solid #a5b2ac;border-radius:6px;padding:7px 16px;
background:white;color:var(--ink)}button[aria-pressed=true]{background:#172126;color:white}
.toolbar-status{color:var(--muted);font-size:14px;margin-left:8px}.legend{display:flex;
flex-wrap:wrap;gap:16px;font-size:14px;color:var(--muted)}.dot{display:inline-block;width:9px;
height:9px;border-radius:50%;background:currentColor;margin-right:5px}.confidence-high{
--confidence:#087b62}.confidence-medium{--confidence:#a36305}.confidence-low{--confidence:#bb3659}
.legend .dot{color:var(--confidence)}.evidence-card{display:grid;grid-template-columns:minmax(250px,38%) 1fr;
gap:28px;border:1px solid #dae0da;border-radius:12px;background:white;padding:24px;margin:24px 0}
.figure{min-width:0}.evidence-image{display:block;width:100%;height:auto;max-height:780px;
background:#fbfbf8;border:1px solid #e5e8e2;border-radius:6px}.original-link{display:block;
font-size:13px;text-align:center;margin-top:10px;color:#295580}.details{min-width:0}
.details p{overflow-wrap:anywhere;margin:0 0 16px}.details small,.frame-note{font-size:13px;
color:var(--muted)}.frame-note{margin-top:12px!important}.ocr-panel{overflow:auto;max-height:600px;
border:1px solid #e0e5df;border-radius:6px}table{border-collapse:collapse;width:100%;font-size:14px}
th{position:sticky;top:0;background:#edf1ec;text-align:left;font-weight:600;padding:10px 8px}
td{padding:9px 8px;border-top:1px solid #edf0eb;vertical-align:top}.number{color:var(--confidence);
font-weight:650;width:40px}.confidence-value{color:var(--confidence);font-variant-numeric:tabular-nums}
.ocr-text{overflow-wrap:anywhere;min-width:100px}.evidence-row,.region{cursor:pointer}
.evidence-row.is-active{background:#dbeafe;box-shadow:inset 3px 0 var(--accent)}
.region polygon{stroke:var(--confidence);stroke-width:1.6;fill:var(--confidence);fill-opacity:.05}
.region text{fill:var(--confidence);font-weight:700;paint-order:stroke;stroke:white;
stroke-width:3;stroke-linejoin:round;pointer-events:none}.region.is-active polygon{
stroke:var(--accent);stroke-width:3;fill:var(--accent);fill-opacity:.2}.region.is-active text{
fill:var(--accent)}.region:focus-visible polygon{stroke:var(--accent);stroke-width:3}
.evidence-row:focus-visible{outline:2px solid var(--accent);outline-offset:-2px}
[hidden]{display:none!important}summary{cursor:pointer;padding:14px 0;font-weight:600}
.pending-card{display:flex;gap:24px;padding:20px;border-top:1px solid #ddd}.pending-card img{
width:80px;height:180px;object-fit:contain}.pending-card p{overflow-wrap:anywhere}
@media(max-width:750px){main{padding:24px 12px}h1{font-size:27px}.toolbar{padding:10px 12px}
.evidence-card{grid-template-columns:1fr;padding:16px;gap:20px}.evidence-image{max-height:620px}
.ocr-panel{max-height:460px}.toolbar-status{flex-basis:100%;margin:0}}
</style></head><body><div class="toolbar" role="group" aria-label="Профиль OCR и рамки">"""
    document += "".join(buttons) + '<button type="button" id="frames-off">Без рамок</button>'
    document += """<span class="toolbar-status" id="toolbar-status" aria-live="polite"></span></div>
<main><header><h1>OCR: откуда прочитан текст</h1><p>Наведите на строку или рамку — соответствующая
область подсветится. Нажмите, чтобы закрепить выделение; повторное нажатие его снимет.
Клавиши Tab, Enter и пробел также работают. RU и Latin — разные профили распознавания.</p>
<p><strong>Рамки относятся к отдельным строкам OCR, а не ко всей этикетке.</strong>
Текст и confidence — наблюдения модели; они не подтверждают правильность карточки вина.</p>
<div class="legend"><span>Цвет = confidence OCR:</span>
<span class="confidence-high"><i class="dot"></i>≥ 0.90</span>
<span class="confidence-medium"><i class="dot"></i>0.60–0.89</span>
<span class="confidence-low"><i class="dot"></i>&lt; 0.60</span></div>"""
    document += f'<p>Показаны результаты для <strong>{len(sections)} из {manifest["size"]}</strong> эталонов.</p></header>'
    document += "".join(sections)
    if pending_sections:
        document += f'<details><summary>Ещё не обработаны: {len(pending_sections)}</summary>'
        document += "".join(pending_sections) + '</details>'
    document += """</main><script>
(() => {
  const buttons = Array.from(document.querySelectorAll('[data-engine-button]'));
  let engine = buttons[0]?.dataset.engineButton;
  let frames = true;
  const off = document.getElementById('frames-off');
  function selectKey(section, key) {
    section.querySelectorAll('[data-region-key]').forEach(node => {
      node.classList.toggle('is-active', Boolean(key) && node.dataset.regionKey === key);
    });
  }
  function update() {
    document.querySelectorAll('[data-ocr-engine]').forEach(node => {
      node.toggleAttribute('hidden', node.dataset.ocrEngine !== engine || (node.dataset.role === 'ocr-layer' && !frames));
    });
    buttons.forEach(button => button.setAttribute('aria-pressed', String(button.dataset.engineButton === engine)));
    off.setAttribute('aria-pressed', String(!frames));
    document.getElementById('toolbar-status').textContent = frames
      ? 'Рамки строк видны. Наведите или нажмите для связи с текстом.'
      : 'Рамки скрыты; показан текст выбранного профиля.';
    document.querySelectorAll('.evidence-card').forEach(section => {
      delete section.dataset.selectedKey;
      selectKey(section, null);
    });
  }
  buttons.forEach(button => button.addEventListener('click', () => {
    engine = button.dataset.engineButton;
    frames = true;
    update();
  }));
  off.addEventListener('click', () => { frames = !frames; update(); });
  function activate(target) {
    const section = target.closest('.evidence-card');
    if (!section) return;
    const key = target.dataset.regionKey;
    section.dataset.selectedKey = section.dataset.selectedKey === key ? '' : key;
    selectKey(section, section.dataset.selectedKey);
    if (target.classList.contains('region')) {
      const row = Array.from(section.querySelectorAll('.evidence-row'))
        .find(node => node.dataset.regionKey === key);
      row?.scrollIntoView({block: 'nearest', inline: 'nearest'});
    }
  }
  document.addEventListener('click', event => {
    const target = event.target.closest('[data-region-key]');
    if (target) activate(target);
  });
  document.addEventListener('keydown', event => {
    const target = event.target.closest('[data-region-key]');
    if (target && (event.key === 'Enter' || event.key === ' ')) {
      event.preventDefault();
      activate(target);
    }
  });
  ['pointerover', 'focusin'].forEach(name => document.addEventListener(name, event => {
    const target = event.target.closest('[data-region-key]');
    if (target) selectKey(target.closest('.evidence-card'), target.dataset.regionKey);
  }));
  ['pointerout', 'focusout'].forEach(name => document.addEventListener(name, event => {
    const target = event.target.closest('[data-region-key]');
    if (!target) return;
    const related = event.relatedTarget?.closest?.('[data-region-key]');
    if (related?.dataset.regionKey === target.dataset.regionKey) return;
    const section = target.closest('.evidence-card');
    selectKey(section, section.dataset.selectedKey);
  }));
  update();
})();
</script></body></html>"""
    (output / "review.html").write_text(document)
    summary = {"selected_images": manifest["size"], "completed_by_engine": dict(completed),
               "text_lines_by_engine": dict(line_counts), "inference_seconds_by_engine": dict(timing),
               "catalog_correctness_assessed": False,
               "selection_reasons": dict(Counter(item["selection_reason"] for item in manifest["images"]))}
    write_json(output / "summary.json", summary)
    print(json.dumps(summary, ensure_ascii=False, indent=2))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["prepare", "run", "report"])
    parser.add_argument("--catalog", type=Path, default=ROOT / "data/catalog/curated/catalog.jsonl")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--size", type=int, default=64)
    parser.add_argument("--seed", type=int, default=20260917)
    parser.add_argument("--limit", type=int, help="Process only first N selected images; resume later without limit")
    parser.add_argument("--device", default="cpu")
    parser.add_argument("--cpu-threads", type=int, default=4)
    parser.add_argument("--max-side", type=int, default=1600)
    parser.add_argument("--models-dir", type=Path)
    args = parser.parse_args()
    if args.limit is not None and args.limit < 1:
        parser.error("--limit must be positive")
    if args.max_side < 32 or args.cpu_threads < 1:
        parser.error("--max-side must be >= 32 and --cpu-threads >= 1")
    {"prepare": prepare, "run": run, "report": report}[args.command](args)


if __name__ == "__main__":
    main()
