#!/usr/bin/env python3
"""Offline Grounding DINO pilot; manual polygons are report-only references.

This detects prompted regions, not wine presence or complete label visibility.
The target rule is an explicit centrality/size heuristic with no centre-crop
fallback. Source images and existing manual annotations are never modified.
"""

from __future__ import annotations

import argparse
import hashlib
import html
import importlib.metadata
import inspect
import json
import math
import os
from pathlib import Path
import re
import time
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT = ROOT / "data/audit/label_detection/pilot"
DEFAULT_MODEL = ROOT / "weights/research/label_detection/grounding-dino-tiny"
IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".avif"}


def sha256(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def image_rgb(image, alpha_background="discard"):
    from PIL import Image
    if alpha_background == "white":
        rgba = image.convert("RGBA")
        return Image.alpha_composite(Image.new("RGBA", rgba.size, "white"), rgba).convert("RGB")
    return image.convert("RGB")


def json_digest(value: Any) -> str:
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False).encode()).hexdigest()


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")
    temporary.replace(path)


def validate_existing(path: Path, expected: Any) -> None:
    if path.exists() and json.loads(path.read_text()) != expected:
        raise ValueError(f"Provenance differs; use a new output directory: {path}")


def box_iou(first: list[float], second: list[float]) -> float:
    intersection = max(0, min(first[2], second[2]) - max(first[0], second[0])) * max(
        0, min(first[3], second[3]) - max(first[1], second[1]))
    first_area = max(0, first[2] - first[0]) * max(0, first[3] - first[1])
    second_area = max(0, second[2] - second[0]) * max(0, second[3] - second[1])
    union = first_area + second_area - intersection
    return intersection / union if union else 0.0


def nms_indices(candidates: list[dict], iou_threshold: float) -> list[int]:
    """Stable score-ordered, class-agnostic NMS for the one-label concept."""
    pending = sorted(range(len(candidates)), key=lambda index: (-candidates[index]["score"], index))
    selected: list[int] = []
    while pending:
        best = pending.pop(0)
        selected.append(best)
        pending = [index for index in pending if box_iou(candidates[best]["box_xyxy"],
                                                        candidates[index]["box_xyxy"]) <= iou_threshold]
    return selected


def select_target(candidates: list[dict], width: int, height: int, edge_fraction: float = 0.01) -> dict:
    """Prefer a non-edge box, then horizontal centrality and relative area.

    Edge contact is only a warning heuristic. Occlusion, precise segmentation,
    semantic wine identity and full visibility cannot be inferred by this rule.
    """
    if width <= 0 or height <= 0:
        raise ValueError("Image dimensions must be positive")
    ranked = []
    for index, candidate in enumerate(candidates):
        x1, y1, x2, y2 = candidate["box_xyxy"]
        edge_touch = (x1 <= width * edge_fraction or y1 <= height * edge_fraction
                      or x2 >= width * (1 - edge_fraction) or y2 >= height * (1 - edge_fraction))
        centrality = max(0.0, 1.0 - abs((x1 + x2) / 2 - width / 2) / (width / 2))
        area_fraction = max(0.0, (x2 - x1) * (y2 - y1) / (width * height))
        target_score = 0.7 * centrality + 0.3 * math.sqrt(min(1.0, area_fraction))
        ranked.append({"candidate_index": index, "horizontal_centrality": centrality,
                       "area_fraction": area_fraction, "edge_touch": edge_touch,
                       "target_score": target_score})
    if not ranked:
        return {"status": "no_candidate", "selected_index": None, "ranking": []}
    interior = [item for item in ranked if not item["edge_touch"]]
    if not interior:
        return {"status": "needs_review", "selected_index": None, "ranking": ranked}
    best = max(interior, key=lambda item: (item["target_score"], candidates[item["candidate_index"]]["score"]))
    return {"status": "selected_by_heuristic", "selected_index": best["candidate_index"], "ranking": ranked}


def inspect_source(path: Path) -> dict:
    from PIL import Image, ImageOps

    with Image.open(path) as opened:
        raw_size = list(opened.size)
        orientation = opened.getexif().get(274)
        oriented_size = list(ImageOps.exif_transpose(opened).size)
    return {"id": path.stem, "path": str(path.resolve()), "sha256": sha256(path),
            "raw_size": raw_size, "exif_orientation": orientation, "oriented_size": oriented_size}


def prompt_id(prompt: str) -> str:
    readable = re.sub(r"[^a-z0-9]+", "_", prompt.lower()).strip("_")[:40] or "prompt"
    return f"{readable}_{hashlib.sha256(prompt.encode()).hexdigest()[:8]}"


def result_path(output: Path, source: dict, prompt: str) -> Path:
    return output / "results" / f"{source['id']}_{prompt_id(prompt)}.json"


def validate_result(value: dict, config_sha256: str, source: dict, prompt: str) -> None:
    if (value.get("config_sha256") != config_sha256 or value.get("source") != source
            or value.get("prompt") != prompt):
        raise ValueError("Stored result does not match the current configuration/source/prompt")


def run(args: argparse.Namespace) -> None:
    cache = ROOT / "data/audit/label_detection/cache"
    for name, path in {"HF_HOME": cache / "huggingface", "TORCH_HOME": cache / "torch",
                       "XDG_CACHE_HOME": cache / "xdg"}.items():
        os.environ[name] = str(path)
    os.environ["HF_HUB_OFFLINE"] = "1"
    os.environ["TRANSFORMERS_OFFLINE"] = "1"
    from PIL import Image, ImageOps
    import torch
    from transformers import AutoModelForZeroShotObjectDetection, AutoProcessor

    sources = [inspect_source(path) for path in sorted(args.input.iterdir())
               if path.is_file() and path.suffix.lower() in IMAGE_EXTENSIONS]
    if not sources or len({item["id"] for item in sources}) != len(sources):
        raise ValueError("Need input images with unique filename stems")
    if not args.model_path.is_dir():
        raise ValueError(f"Local model directory is missing: {args.model_path}")
    model_files = {str(path.relative_to(args.model_path)): sha256(path)
                   for path in sorted(args.model_path.rglob("*")) if path.is_file()
                   and not any(part.startswith(".") for part in path.relative_to(args.model_path).parts)}
    if not any(name.endswith((".safetensors", ".bin")) for name in model_files):
        raise ValueError("No local model weights found")
    versions = {name: importlib.metadata.version(name) for name in
                ("torch", "torchvision", "transformers", "Pillow", "numpy", "huggingface-hub", "tokenizers")}
    config = {"schema_version": 1, "model_path": str(args.model_path.resolve()),
              "model_files_sha256": model_files, "versions": versions, "device": args.device,
              "cpu_threads": args.cpu_threads, "prompts": args.prompts, "threshold": args.threshold,
              "text_threshold": args.text_threshold, "nms_iou": args.nms_iou,
              "coordinate_system": "exif_oriented_original_pixels", "inputs": sources,
              "target_rule": {"version": 1, "horizontal_centrality_weight": 0.7,
                              "sqrt_area_weight": 0.3, "edge_fraction": args.edge_fraction,
                              "all_edge_action": "needs_review", "missing_detection_action": "no_candidate"},
              "manual_annotations_used_for_inference": False, "local_files_only": True}
    if args.alpha_background != "discard":
        config["alpha_background"] = args.alpha_background
    config_hash = json_digest(config)
    validate_existing(args.output / "run_config.json", config)
    for source in sources:
        for prompt in args.prompts:
            path = result_path(args.output, source, prompt)
            if path.exists():
                validate_result(json.loads(path.read_text()), config_hash, source, prompt)
    args.output.mkdir(parents=True, exist_ok=True)
    write_json(args.output / "run_config.json", config)
    pending = [(source, prompt) for source in sources for prompt in args.prompts
               if not result_path(args.output, source, prompt).exists()]
    if pending:
        torch.set_num_threads(args.cpu_threads)
        load_started = time.perf_counter()
        processor = AutoProcessor.from_pretrained(args.model_path, local_files_only=True)
        model = AutoModelForZeroShotObjectDetection.from_pretrained(args.model_path, local_files_only=True)
        model = model.to(args.device).eval()
        model_load_seconds = time.perf_counter() - load_started
        print(f"Loaded model in {model_load_seconds:.2f}s; pending passes: {len(pending)}", flush=True)
        for source, prompt in pending:
            source_path = Path(source["path"])
            if sha256(source_path) != source["sha256"]:
                raise ValueError(f"Source changed during execution: {source_path}")
            with Image.open(source_path) as opened:
                image = image_rgb(ImageOps.exif_transpose(opened), args.alpha_background)
            started = time.perf_counter()
            inputs = processor(images=image, text=prompt, return_tensors="pt").to(args.device)
            if args.device.startswith("cuda"):
                torch.cuda.synchronize()
            forward_started = time.perf_counter()
            with torch.inference_mode():
                outputs = model(**inputs)
            if args.device.startswith("cuda"):
                torch.cuda.synchronize()
            forward_seconds = time.perf_counter() - forward_started
            postprocess = processor.post_process_grounded_object_detection
            threshold_argument = "threshold" if "threshold" in inspect.signature(postprocess).parameters else "box_threshold"
            parsed = postprocess(outputs, inputs.input_ids, **{threshold_argument: args.threshold},
                                 text_threshold=args.text_threshold, target_sizes=[(image.height, image.width)])[0]
            labels = parsed.get("text_labels", parsed.get("labels", []))
            raw = [{"box_xyxy": box, "score": score, "text_label": str(label)}
                   for box, score, label in zip(parsed["boxes"].detach().cpu().tolist(),
                                                parsed["scores"].detach().cpu().tolist(), labels)]
            clamped = []
            for raw_index, candidate in enumerate(raw):
                x1, y1, x2, y2 = candidate["box_xyxy"]
                box = [max(0.0, min(image.width, x1)), max(0.0, min(image.height, y1)),
                       max(0.0, min(image.width, x2)), max(0.0, min(image.height, y2))]
                if box[2] > box[0] and box[3] > box[1]:
                    clamped.append({**candidate, "box_xyxy": box, "raw_detection_index": raw_index})
            candidates = [clamped[index] for index in nms_indices(clamped, args.nms_iou)]
            selection = select_target(candidates, image.width, image.height, args.edge_fraction)
            elapsed = time.perf_counter() - started
            value = {"config_sha256": config_hash, "source": source, "prompt": prompt,
                     "raw_post_threshold_detections": raw, "candidates_after_nms": candidates,
                     "selection": selection, "model_load_seconds_this_process": model_load_seconds,
                     "forward_seconds": forward_seconds, "inference_seconds_including_prepost": elapsed,
                     "processor_pixel_values_shape": list(inputs.pixel_values.shape),
                     "visibility_and_wine_presence_verified": False}
            write_json(result_path(args.output, source, prompt), value)
            image_dir = args.output / "images"
            image_dir.mkdir(exist_ok=True)
            image.save(image_dir / f"{source['id']}.jpg", quality=92)
            print(f"{source['id']} / {prompt}: {len(candidates)} boxes, {selection['status']}, {elapsed:.2f}s", flush=True)
    report(args)


def reference_box(annotation: dict) -> list[float]:
    polygon = annotation["target_polygon"]
    return [min(point[0] for point in polygon), min(point[1] for point in polygon),
            max(point[0] for point in polygon), max(point[1] for point in polygon)]


def report(args: argparse.Namespace) -> None:
    config_path = args.output / "run_config.json"
    config = json.loads(config_path.read_text())
    config_hash = json_digest(config)
    annotations = {}
    reference_provenance = None
    if args.annotations and args.annotations.exists():
        annotation_data = json.loads(args.annotations.read_text())
        annotations = {item["id"]: item for item in annotation_data["images"]}
        reference_provenance = {"path": str(args.annotations.resolve()), "sha256": sha256(args.annotations),
                                "usage": "report-only comparison after inference"}
    parts = []
    summary = []
    for source in config["inputs"]:
        width, height = source["oriented_size"]
        annotation = annotations.get(source["id"])
        if annotation and (annotation["source_sha256"] != source["sha256"]
                           or annotation["oriented_size"] != source["oriented_size"]):
            raise ValueError(f"Manual reference belongs to a different source: {source['id']}")
        figures = []
        for prompt in config["prompts"]:
            path = result_path(args.output, source, prompt)
            if not path.exists():
                figures.append(f"<article><h3>{html.escape(prompt)}</h3><p>Ещё не обработано.</p></article>")
                continue
            result = json.loads(path.read_text())
            validate_result(result, config_hash, source, prompt)
            selected = result["selection"]["selected_index"]
            candidates = result["candidates_after_nms"]
            manual_svg = ""
            if annotation:
                polygons = [annotation["target_polygon"], *annotation["neighbor_polygons"]]
                manual_svg = "".join(f'<polygon points="{" ".join(f"{x},{y}" for x, y in polygon)}"/>'
                                     for polygon in polygons)
            boxes_svg, rows = [], []
            rankings = {item["candidate_index"]: item for item in result["selection"]["ranking"]}
            for index, candidate in enumerate(candidates):
                x1, y1, x2, y2 = candidate["box_xyxy"]
                color = "#16a34a" if index == selected else "#f97316"
                boxes_svg.append(f'<g class="automatic"><rect x="{x1}" y="{y1}" width="{x2-x1}" height="{y2-y1}" '
                                 f'fill="none" stroke="{color}" stroke-width="10"/>'
                                 f'<text x="{x1+12}" y="{max(60,y1+55)}" fill="{color}" stroke="#fff" stroke-width="2" '
                                 f'paint-order="stroke" font-size="56">#{index+1} {candidate["score"]:.3f}</text></g>')
                ranking = rankings[index]
                rows.append(f'<tr><td>#{index+1}{" ✓" if index == selected else ""}</td><td>{candidate["score"]:.3f}</td>'
                            f'<td>{ranking["target_score"]:.3f}</td><td>{"да" if ranking["edge_touch"] else "нет"}</td>'
                            f'<td>{html.escape(candidate["text_label"])}</td></tr>')
            target_iou = (box_iou(candidates[selected]["box_xyxy"], reference_box(annotation))
                          if selected is not None and annotation else None)
            item = {"source_id": source["id"], "prompt": prompt, "candidate_count": len(candidates),
                    "status": result["selection"]["status"], "selected_index": selected,
                    "inference_seconds": result["inference_seconds_including_prepost"],
                    "selected_vs_manual_target_bounding_box_iou": target_iou}
            summary.append(item)
            comparison = f"IoU с прямоугольником ручной целевой области: {target_iou:.3f}." if target_iou is not None else ""
            figures.append(f'<article><h3>{html.escape(prompt)}</h3><svg viewBox="0 0 {width} {height}" role="img" '
                           f'aria-label="Автоматические области {html.escape(source["id"])}">'
                           f'<image href="images/{html.escape(source["id"])}.jpg" width="{width}" height="{height}"/>'
                           f'<g class="manual">{manual_svg}</g>{"".join(boxes_svg)}</svg>'
                           f'<p><strong>{html.escape(item["status"])}</strong>; {item["inference_seconds"]:.2f} с. {comparison}</p>'
                           '<table><thead><tr><th>Область</th><th>Score модели</th><th>Цель</th><th>Край</th><th>Текст</th></tr></thead>'
                           f'<tbody>{"".join(rows) or "<tr><td colspan=5>Нет кандидатов — резервная обрезка не создаётся.</td></tr>"}</tbody></table></article>')
        parts.append(f'<section><h2>{html.escape(source["id"])}</h2><div class="comparisons">{"".join(figures)}</div></section>')
    summary_value = {"config_sha256": config_hash, "manual_reference": reference_provenance, "results": summary,
                     "disclaimer": "Three supplied photos are a smoke test, not detector accuracy; no negative benchmark."}
    write_json(args.output / "summary.json", summary_value)
    document = '''<!doctype html><html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Автоматическое выделение этикеток — Grounding DINO</title>
<style>body{font:16px/1.5 system-ui,sans-serif;margin:24px auto;padding:0 20px;max-width:1400px;color:#172033;background:#f6f8fb}h1,h2,h3{line-height:1.2}.intro,article{background:white;padding:20px;border:1px solid #dce2eb;border-radius:12px}.comparisons{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:20px}svg{display:block;width:100%;max-height:680px;background:#e7ebf0}table{border-collapse:collapse;font-size:14px;width:100%}td,th{padding:6px;text-align:left;border-bottom:1px solid #ddd}.manual{fill:none;stroke:#a855f7;stroke-width:8;stroke-dasharray:25 20;display:none}.show-manual .manual{display:block}small{color:#4b5563}label{display:block;padding:10px 0}section{margin-top:28px}@media(max-width:850px){.comparisons{grid-template-columns:1fr}}</style>
<h1>Автоматическое выделение этикеток</h1><div class="intro"><p>Grounding DINO Tiny без дообучения. Каждый запрос независимо получает полный исходный кадр. Оранжевый — кандидаты после NMS; зелёный — область, выбранная эвристикой центральности и размера.</p>
<p>Score модели не является калиброванной вероятностью. Выбор центральной области не доказывает, что этикетка полностью видна или принадлежит вину. <code>no_candidate</code> означает лишь отсутствие детекций при данном запросе и пороге.</p>
<p>При касании края у всех кандидатов выдаётся <code>needs_review</code>. Скрытые перекрытия эта эвристика не определяет. Три снимка — техническая проверка, не оценка качества; отрицательная выборка ещё нужна.</p>
<label><input id="manual" type="checkbox"> Показать ручные границы фиолетовым пунктиром — только для сравнения после детекции</label>
<small>Координаты в исходном размере после EXIF-поворота. IoU сравнивает прямоугольники, а не маски. Время включает подготовку входа, модель и постобработку, без загрузки модели и сохранения отчёта.</small></div>
'''+ "".join(parts) + '''<script>document.getElementById('manual').addEventListener('change',e=>document.body.classList.toggle('show-manual',e.target.checked));</script></html>'''
    (args.output / "review.html").write_text(document)
    print(f"Report: {args.output / 'review.html'}", flush=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("run", "report"))
    parser.add_argument("--input", type=Path, default=ROOT / "data/eval/queries")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--model-path", type=Path, default=DEFAULT_MODEL)
    parser.add_argument("--prompts", nargs="+", default=["wine label.", "bottle label."])
    parser.add_argument("--threshold", type=float, default=0.25)
    parser.add_argument("--text-threshold", type=float, default=0.2)
    parser.add_argument("--nms-iou", type=float, default=0.5)
    parser.add_argument("--edge-fraction", type=float, default=0.01)
    parser.add_argument("--device", default="cpu")
    parser.add_argument("--cpu-threads", type=int, default=4)
    parser.add_argument("--alpha-background", choices=("discard", "white"), default="discard")
    parser.add_argument("--annotations", type=Path, default=ROOT / "data/audit/label_geometry/annotations.json",
                        help="Used only to overlay/compare manual references when rendering the report")
    args = parser.parse_args()
    for name in ("threshold", "text_threshold", "nms_iou", "edge_fraction"):
        if not 0 <= getattr(args, name) <= 1:
            parser.error(f"{name} must be between 0 and 1")
    if args.cpu_threads <= 0 or not args.prompts or len(set(args.prompts)) != len(args.prompts):
        parser.error("Need positive CPU thread count and distinct prompts")
    if args.command == "run":
        run(args)
    else:
        report(args)


if __name__ == "__main__":
    main()
