#!/usr/bin/env python3
"""Local DINO/SAM label experiment with preserved inputs, masks and provenance."""
from __future__ import annotations

import argparse
import html
import importlib.metadata
import json
import math
import os
from pathlib import Path
import sys
import time
import shutil

sys.dont_write_bytecode = True
from detect_labels_pilot import ROOT, image_rgb, inspect_source, json_digest, result_path, sha256, validate_existing, write_json

BASE = ROOT / "data/audit/label_segmentation"


def area(box):
    return max(0, box[2] - box[0]) * max(0, box[3] - box[1])


def coverage(inner, outer):
    overlap = max(0, min(inner[2], outer[2]) - max(inner[0], outer[0])) * max(
        0, min(inner[3], outer[3]) - max(inner[1], outer[1]))
    return overlap / area(inner) if area(inner) else 0.0


def choose_label(labels, bottles, width, height):
    """Bottle context is optional. A cropped neck does not disqualify a bottle.

    Reject near-whole-bottle label proposals, rank remaining labels by DINO score.
    This does not prove full visibility or wine presence, especially on packages.
    """
    def centrality(box):
        return max(0, 1 - abs((box[0] + box[2]) / 2 - width / 2) / (width / 2))
    bottle_index = max(range(len(bottles)), key=lambda i: (
        centrality(bottles[i]["box_xyxy"]), bottles[i]["score"]), default=None)
    bottle = bottles[bottle_index]["box_xyxy"] if bottle_index is not None else None
    ranked = []
    for index, label in enumerate(labels):
        box = label["box_xyxy"]
        contained = coverage(box, bottle) if bottle else None
        ratio = area(box) / area(bottle) if bottle and area(bottle) else None
        eligible = bottle is None or (contained >= 0.8 and ratio < 0.85)
        ranked.append({"index": index, "bottle_coverage": contained, "bottle_area_ratio": ratio,
                       "eligible": eligible, "centrality": centrality(box),
                       "edge_touch": box[0] <= .01*width or box[2] >= .99*width
                                     or box[1] <= .01*height or box[3] >= .99*height})
    eligible = [x for x in ranked if x["eligible"]]
    if not eligible:
        return {"status": "no_label_candidate" if not labels else "bottle_label_conflict",
                "bottle_index": bottle_index, "label_index": None, "ranking": ranked}
    if bottle is not None:
        best = max(eligible, key=lambda x: (labels[x["index"]]["score"], x["centrality"]))
    else:
        best = max(eligible, key=lambda x: (x["centrality"], labels[x["index"]]["score"]))
    return {"status": "selected_with_bottle_context" if bottle else "selected_without_bottle",
            "bottle_index": bottle_index, "label_index": best["index"], "ranking": ranked}


def choose_main_label(candidates, width, height):
    """Prefer central, substantial regions over tiny centered neck/emblem labels."""
    ranking = []
    for i, candidate in enumerate(candidates):
        box = candidate["box_xyxy"]
        centrality = max(0, 1-abs((box[0]+box[2])/2-width/2)/(width/2))
        score = .7*centrality + .3*math.sqrt(min(1, area(box)/(width*height)))
        ranking.append({"index": i, "target_score": score})
    best = max(ranking, key=lambda r: (r["target_score"], candidates[r["index"]]["score"]), default=None)
    return {"status": "selected_by_centrality_and_area" if best else "no_label_candidate",
            "label_index": best["index"] if best else None, "ranking": ranking}


def prepare():
    from PIL import Image, ImageDraw, ImageOps
    rows = [json.loads(line) for line in (ROOT / "data/catalog/curated/catalog.jsonl").read_text().splitlines()]
    by_slug = {r["slug"]: r for r in rows}
    named = ["beloe-polusladkoe", "agora-yachting-cabernet-sauvignon",
             "abrau-dyurso-az-abrau-bayanshira-beloe-suhoe-12",
             "soyuz-vino-soyuz-vino-muskat-beg-in-boks-beloe-polusladkoe-11",
             "soyuz-vino-zelyonaya-dolina-pino-blan-v-banke-beloe-polusladkoe-115",
             "chateau-le-grand-vostock-krasnostop-rezerv-krasnoe-suhoe-145"]
    selected = [(by_slug[slug], "named stress case: packaging, resolution or previous discussion") for slug in named]
    seen = {r["reference_sha256"] for r, _ in selected}
    pool = sorted(rows, key=lambda r: r["reference_sha256"])
    for fraction in (0.07, 0.23, 0.39, 0.55, 0.71, 0.87):
        start = int(len(pool) * fraction)
        r = next(r for r in pool[start:] + pool[:start] if r["reference_sha256"] not in seen)
        seen.add(r["reference_sha256"])
        selected.append((r, f"deterministic SHA order quantile {fraction}; not random validation"))
    entries = []
    for path in sorted((ROOT / "data/eval/queries").iterdir()):
        if path.suffix.lower() not in {".jpg", ".jpeg", ".png", ".webp"}:
            continue
        entries.append({"id": path.stem, "kind": "eval", "slug": None,
                        "title": path.stem, "path": str(path.resolve()), "reason": "organizer example"})
    for r, reason in selected:
        entries.append({"id": "cat_" + r["slug"], "kind": "catalog", "slug": r["slug"],
                        "title": r["title"], "path": str((ROOT / r["reference_path"]).resolve()), "reason": reason})
    image_dir = BASE / "inputs"
    image_dir.mkdir(parents=True, exist_ok=True)
    sheet = Image.new("RGB", (4 * 300, 4 * 360), "white")
    draw = ImageDraw.Draw(sheet)
    for i, entry in enumerate(entries):
        path = Path(entry["path"])
        entry["sha256"] = sha256(path)
        target = image_dir / (entry["id"] + path.suffix.lower())
        if target.exists():
            if target.resolve() != path or sha256(target) != entry["sha256"]:
                raise ValueError("Existing input differs")
        else:
            target.symlink_to(path)
        with Image.open(path) as opened:
            image = ImageOps.exif_transpose(opened).convert("RGBA")
            entry["size"] = list(image.size)
            image.thumbnail((280, 310))
            x, y = (i % 4)*300, (i // 4)*360
            sheet.paste(image, (x+(300-image.width)//2, y+10), image)
            draw.text((x+5, y+325), entry["id"][:40], fill="black")
    manifest = {"selection": "3 eval + 6 named catalogue cases + 6 fixed SHA quantiles; exploratory", "images": entries}
    validate_existing(BASE / "sample_manifest.json", manifest)
    write_json(BASE / "sample_manifest.json", manifest)
    sheet.save(BASE / "sample_contact_sheet.jpg")
    print(f"Prepared {len(entries)} images", flush=True)


def model_config(model_name, mode, alpha_white=False):
    import cv2
    download_path = BASE / f"{model_name}_download.json"
    manifest = json.loads(download_path.read_text())
    model_dir = BASE / manifest.get("local_dir", "models/" + manifest["model_id"].split("/")[-1])
    for name, expected in manifest["files_sha256"].items():
        if sha256(model_dir / name) != expected:
            raise ValueError("Model hash mismatch")
    config = {"model": manifest, "mode": mode, "device": "cpu", "threads": 4,
              "mask_threshold": 0.0 if model_name == "sam2" else 0.5,
              "sam3_detection_threshold": 0.5, "sam2_multimask_output": False,
              "versions": {n: importlib.metadata.version(n) for n in
                           ("torch", "transformers", "Pillow", "numpy")},
              "coordinate_system": "exif_oriented_original_pixels", "manual_regions_used": False,
              "target_rule": "central_bottle_then_label_score; coverage>=.8; area_ratio<.85; no-bottle central-label",
              "dino_config_sha256": sha256(BASE / ("dino_white" if alpha_white else "dino") / "run_config.json"),
              "sample_manifest_sha256": sha256(BASE / "sample_manifest.json")}
    config["versions"]["opencv"] = cv2.__version__
    if alpha_white:
        config["alpha_background"] = "white"
    if mode == "sam3_text_main":
        config["target_rule"] = "sam3 only: .7*horizontal_centrality+.3*sqrt(area_fraction); score breaks ties"
    return config, model_dir


def save_mask(mask, output, source_id):
    import cv2
    import numpy as np
    from PIL import Image
    binary = np.asarray(mask, dtype=np.uint8)
    path = output / "masks" / f"{source_id}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(binary * 255).save(path)
    contours, _ = cv2.findContours(binary, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    # Save all components; don't silently discard islands or smooth the mask.
    polygons = [c[:, 0, :].tolist() for c in contours if len(c) >= 3]
    ys, xs = np.nonzero(binary)
    bbox = [int(xs.min()), int(ys.min()), int(xs.max())+1, int(ys.max())+1] if len(xs) else None
    return {"mask_path": str(path.relative_to(output)), "mask_sha256": sha256(path),
            "area_pixels": int(binary.sum()), "bbox_xyxy": bbox, "contours": polygons,
            "external_contour_count": len(contours), "contours_are_mask_boundaries_not_rectification": True}


def run(mode, alpha_white=False):
    import numpy as np
    import torch
    from PIL import Image, ImageOps
    from transformers import Sam2Model, Sam2Processor, Sam3Model, Sam3Processor
    os.environ["HF_HUB_OFFLINE"] = "1"
    os.environ["TRANSFORMERS_OFFLINE"] = "1"
    torch.set_num_threads(4)
    model_name = "sam2" if mode == "dino_sam2" else "sam3"
    text_mode = mode in ("sam3_text", "sam3_text_main")
    config, model_dir = model_config(model_name, mode, alpha_white)
    output = BASE / (mode + ("_white" if alpha_white else ""))
    validate_existing(output / "run_config.json", config)
    write_json(output / "run_config.json", config)
    config_hash = json_digest(config)
    dino_dir = BASE / ("dino_white" if alpha_white else "dino")
    dino_config = json.loads((dino_dir / "run_config.json").read_text())
    entries = {x["id"]: x for x in json.loads((BASE / "sample_manifest.json").read_text())["images"]}
    model, processor = None, None
    for source in dino_config["inputs"]:
        path = Path(source["path"])
        if sha256(path) != source["sha256"] or source["sha256"] != entries[source["id"]]["sha256"]:
            raise ValueError("Input changed")
        destination = output / "results" / f"{source['id']}.json"
        if destination.exists():
            previous = json.loads(destination.read_text())
            if previous["config_sha256"] != config_hash or previous["source"] != source:
                raise ValueError("Stored inference differs")
            if previous.get("mask") and sha256(output / previous["mask"]["mask_path"]) != previous["mask"]["mask_sha256"]:
                raise ValueError("Stored mask differs")
            continue
        if mode == "sam3_text_main":
            original_file = BASE / ("sam3_text" + ("_white" if alpha_white else "")) / "results" / destination.name
            original = json.loads(original_file.read_text())
            original_config = json.loads((original_file.parent.parent / "run_config.json").read_text())
            expected_original_config = {**config, "mode": "sam3_text", "target_rule": "central_bottle_then_label_score; coverage>=.8; area_ratio<.85; no-bottle central-label"}
            if original["config_sha256"] != json_digest(original_config) or original["source"] != source:
                raise ValueError("Baseline inference provenance differs")
            selection_main = choose_main_label(original.get("sam3_candidates", []), *source["oriented_size"])
            can_reuse = original_config == expected_original_config
            if not can_reuse:
                print(f"{mode} {source['id']}: settings/environment changed; running fresh inference", flush=True)
            if can_reuse and selection_main["label_index"] == original.get("sam3_selected_index"):
                if original.get("mask"):
                    old_mask = original_file.parent.parent / original["mask"]["mask_path"]
                    if sha256(old_mask) != original["mask"]["mask_sha256"]:
                        raise ValueError("Baseline mask differs")
                    new_mask = output / original["mask"]["mask_path"]
                    new_mask.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copyfile(old_mask, new_mask)
                original.update({"config_sha256": config_hash, "mode": mode, "sam3_selection": selection_main,
                                 "reused_inference_from": str(original_file), "reused_result_sha256": sha256(original_file)})
                write_json(destination, original)
                print(f"{mode} {source['id']}: unchanged selection, reused original inference", flush=True)
                continue
        detections = {}
        for prompt in ("label.", "wine bottle."):
            result = json.loads(result_path(dino_dir, source, prompt).read_text())
            if result["source"] != source or result["config_sha256"] != json_digest(dino_config):
                raise ValueError("DINO result provenance differs")
            detections[prompt] = result["candidates_after_nms"]
        width, height = source["oriented_size"]
        selection = choose_label(detections["label."], detections["wine bottle."], width, height)
        record = {"config_sha256": config_hash, "source": source, "mode": mode,
                  "dino_detections": detections, "dino_selection": selection,
                  "selected_box": None, "mask": None, "status": selection["status"]}
        if not text_mode and selection["label_index"] is None:
            write_json(destination, record)
            print(f"{mode} {source['id']}: {record['status']}", flush=True)
            continue
        if model is None:
            cls_model, cls_processor = (Sam2Model, Sam2Processor) if model_name == "sam2" else (Sam3Model, Sam3Processor)
            processor = cls_processor.from_pretrained(model_dir, local_files_only=True)
            model = cls_model.from_pretrained(model_dir, local_files_only=True).eval()
        with Image.open(path) as opened:
            image = image_rgb(ImageOps.exif_transpose(opened), "white" if alpha_white else "discard")
        started = time.perf_counter()
        if not text_mode:
            box = detections["label."][selection["label_index"]]["box_xyxy"]
            record["selected_box"] = box
        if mode == "dino_sam2":
            inputs = processor(images=image, input_boxes=[[box]], return_tensors="pt")
            with torch.inference_mode():
                predictions = model(**inputs, multimask_output=False)
            mask = processor.post_process_masks(predictions.pred_masks.cpu(), inputs["original_sizes"])[0][0, 0].numpy()
            record["predicted_mask_quality_not_measured_iou"] = float(predictions.iou_scores[0, 0, 0])
        else:
            kwargs = {"text": "label"} if text_mode else {"input_boxes": [[box]], "input_boxes_labels": [[1]]}
            inputs = processor(images=image, return_tensors="pt", **kwargs)
            with torch.inference_mode():
                predictions = model(**inputs)
            parsed = processor.post_process_instance_segmentation(predictions, threshold=0.5, mask_threshold=0.5,
                                                                 target_sizes=inputs["original_sizes"].tolist())[0]
            candidates = [{"box_xyxy": b, "score": s} for b, s in zip(parsed["boxes"].tolist(), parsed["scores"].tolist())]
            # Standalone SAM3 does not use DINO for selecting its result.
            own_selection = (choose_main_label(candidates, width, height) if mode == "sam3_text_main"
                             else choose_label(candidates, [], width, height)) if text_mode else None
            index = own_selection["label_index"] if own_selection else max(
                range(len(candidates)), key=lambda i: coverage(box, candidates[i]["box_xyxy"])*coverage(candidates[i]["box_xyxy"], box), default=None)
            record["sam3_candidates"] = candidates
            record["sam3_selection"] = own_selection
            if index is None:
                record["status"] = "no_sam3_mask"
                record["inference_seconds_including_prepost"] = time.perf_counter()-started
                write_json(destination, record)
                continue
            mask = parsed["masks"][index].cpu().numpy()
            record["selected_box"] = candidates[index]["box_xyxy"]
            record["sam3_selected_index"] = index
        record["inference_seconds_including_prepost"] = time.perf_counter() - started
        record["mask"] = save_mask(mask, output, source["id"])
        record["status"] = "mask_generated" if record["mask"]["area_pixels"] else "empty_mask"
        write_json(destination, record)
        print(f"{mode} {source['id']}: {record['status']} {record['inference_seconds_including_prepost']:.2f}s", flush=True)


def report(alpha_white=False):
    from PIL import Image, ImageDraw, ImageOps
    import numpy as np
    entries = json.loads((BASE / "sample_manifest.json").read_text())["images"]
    annotations = {x["id"]: x for x in json.loads((ROOT / "data/audit/label_geometry/annotations.json").read_text())["images"]}
    parts, summary = [], []
    assets = BASE / "report_images"
    assets.mkdir(exist_ok=True)
    standalone = "sam3_text_main" if (BASE / ("sam3_text_main" + ("_white" if alpha_white else "")) / "run_config.json").exists() else "sam3_text"
    modes = ("dino_sam2", "dino_sam3", standalone)
    sheets = {mode: Image.new("RGB", (1080, 5*400), "white") for mode in modes}
    for entry in entries:
        with Image.open(entry["path"]) as opened:
            image = image_rgb(ImageOps.exif_transpose(opened), "white" if alpha_white else "discard")
        width, height = image.size
        preview = image.copy()
        preview.thumbnail((900, 1100))
        preview.save(assets / f"{entry['id']}.jpg")
        figures = []
        for mode in modes:
            mode_dir = BASE / (mode + ("_white" if alpha_white else ""))
            result_file = mode_dir / "results" / f"{entry['id']}.json"
            if not result_file.exists():
                figures.append(f"<article><h3>{mode}</h3><p>Результат пока отсутствует.</p></article>")
                continue
            result = json.loads(result_file.read_text())
            tile = image.copy()
            tile.thumbnail((330, 350))
            index = entries.index(entry)
            x, y = (index%3)*360, (index//3)*400
            sheets[mode].paste(tile, (x+(360-tile.width)//2, y))
            sheet_draw = ImageDraw.Draw(sheets[mode])
            sheet_draw.text((x+5,y+355), entry["id"][:48], fill="black")
            sheet_draw.text((x+5,y+374), result["status"], fill="black")
            polygons, rectangles = [], []
            metrics = {"id": entry["id"], "kind": entry["kind"], "mode": mode, "status": result["status"]}
            if not mode.startswith("sam3_text"):
                for bottle in result["dino_detections"]["wine bottle."]:
                    x1, y1, x2, y2 = bottle["box_xyxy"]
                    rectangles.append(f'<rect class="bottles" x="{x1}" y="{y1}" width="{x2-x1}" height="{y2-y1}"/>')
            if result["selected_box"]:
                x1, y1, x2, y2 = result["selected_box"]
                rectangles.append(f'<rect class="label-box" x="{x1}" y="{y1}" width="{x2-x1}" height="{y2-y1}"/>')
            extra = ""
            if result["mask"]:
                mask_path = mode_dir / result["mask"]["mask_path"]
                binary = np.asarray(Image.open(mask_path)) > 0
                overlay = image.convert("RGBA")
                tint = Image.new("RGBA", image.size, (22, 190, 75, 0))
                tint.putalpha(Image.fromarray(binary.astype("uint8")*85))
                overlay = Image.alpha_composite(overlay, tint)
                painter = ImageDraw.Draw(overlay)
                for contour in result["mask"]["contours"]:
                    if len(contour) > 2:
                        painter.line([tuple(p) for p in contour] + [tuple(contour[0])], fill=(0, 160, 40), width=max(1,width//500))
                overlay.thumbnail((330, 350))
                index = entries.index(entry)
                x, y = (index%3)*360, (index//3)*400
                sheets[mode].paste(overlay.convert("RGB"), (x+(360-overlay.width)//2, y))
                ImageDraw.Draw(sheets[mode]).text((x+5,y+355), entry["id"][:48], fill="black")
                for polygon in result["mask"]["contours"]:
                    points = " ".join(f"{x},{y}" for x, y in polygon)
                    polygons.append(f'<polygon class="contour" points="{points}"/>')
                rgba = image.convert("RGBA")
                rgba.putalpha(Image.fromarray(binary.astype("uint8")*255))
                crop_path = assets / f"{entry['id']}_{mode}_cutout.png"
                if result["mask"]["bbox_xyxy"]:
                    rgba = rgba.crop(tuple(result["mask"]["bbox_xyxy"]))
                rgba.save(crop_path)
                extra = f'<details><summary>Вырезка по маске</summary><img class="cutout" src="report_images/{crop_path.name}"/></details>'
                metrics["area_fraction"] = result["mask"]["area_pixels"]/(width*height)
                metrics["seconds"] = result.get("inference_seconds_including_prepost")
                annotation = annotations.get(entry["id"])
                if annotation:
                    if entry["sha256"] != annotation["source_sha256"]:
                        raise ValueError("Reference/source mismatch")
                    reference = Image.new("1", image.size)
                    ImageDraw.Draw(reference).polygon([tuple(p) for p in annotation["target_polygon"]], fill=1)
                    truth = np.asarray(reference, dtype=bool)
                    metrics["rough_polygon_iou"] = float((binary & truth).sum()/max(1, (binary | truth).sum()))
                    points = " ".join(f"{x},{y}" for x, y in annotation["target_polygon"])
                    polygons.append(f'<polygon class="manual" points="{points}"/>')
            caption = html.escape(result["status"])
            if metrics.get("seconds") is not None:
                caption += f'; SAM {metrics["seconds"]:.2f} с CPU'
            if "rough_polygon_iou" in metrics:
                caption += f'; IoU с приблизительным ручным полигоном {metrics["rough_polygon_iou"]:.3f}'
            figures.append(f'<article><h3>{mode}</h3><svg viewBox="0 0 {width} {height}"><image href="report_images/{entry["id"]}.jpg" width="{width}" height="{height}"/>'+
                           ''.join(rectangles+polygons)+f'</svg><p>{caption}</p>{extra}</article>')
            summary.append(metrics)
        parts.append(f'<section><h2>{html.escape(entry["title"])}</h2><p>{html.escape(entry["kind"]+": "+entry["id"])}</p><div class="grid">'+''.join(figures)+'</div></section>')
    page = '''<!doctype html><html lang="ru"><meta charset="utf-8"><title>Контуры этикеток</title>
<style>body{font:16px system-ui;margin:24px;background:#f4f7fb;color:#172033}.grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px}article{background:white;padding:14px;border:1px solid #dce3eb;border-radius:10px}svg{width:100%;max-height:680px}.bottles{fill:none;stroke:#2563eb;stroke-width:5;vector-effect:non-scaling-stroke}.label-box{fill:none;stroke:#f97316;stroke-width:2;vector-effect:non-scaling-stroke}.contour{fill:#22c55e33;stroke:#16a34a;stroke-width:2;vector-effect:non-scaling-stroke}.manual{display:none;fill:none;stroke:#d946ef;stroke-width:2;stroke-dasharray:6;vector-effect:non-scaling-stroke}body.show-manual .manual{display:block}body.hide-bottles .bottles{display:none}body.hide-mask .contour{display:none}.cutout{max-width:100%;max-height:600px;background:repeating-conic-gradient(#ddd 0 25%,white 0 50%) 50%/20px 20px}section{margin-top:30px}h2,p{overflow-wrap:anywhere}@media(max-width:900px){.grid{grid-template-columns:1fr}}</style>
<h1>Контуры этикеток: DINO → SAM 2 / SAM 3</h1><p>Синие рамки — бутылки DINO; оранжевая — область для сегментации; зелёный — контур и заливка маски. Все координаты относятся к исходнику после EXIF. Каталог: разведочный набор, без ручной истины контуров.</p>
<p>Ручные полигоны eval приблизительные и используются только после inference. IoU с ними не является точностью по большой выборке. Score SAM — предсказание качества маски, не измеренная точность.</p>
<label><input type="checkbox" onchange="document.body.classList.toggle('show-manual',this.checked)"> Ручной полигон</label>
<label><input type="checkbox" checked onchange="document.body.classList.toggle('hide-bottles',!this.checked)"> Бутылки</label>
<label><input type="checkbox" checked onchange="document.body.classList.toggle('hide-mask',!this.checked)"> Маски</label>'''+''.join(parts)+"</html>"
    (BASE / "review.html").write_text(page)
    for mode, sheet in sheets.items():
        sheet.save(BASE / f"{mode}_contact_sheet.jpg")
    write_json(BASE / "summary.json", summary)
    print(f"Report: {BASE / 'review.html'}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("prepare", "dino_sam2", "dino_sam3", "sam3_text", "sam3_text_main", "report"))
    parser.add_argument("--alpha-white", action="store_true", help="Composite transparent inputs onto white; keep baseline outputs separately")
    args = parser.parse_args()
    if args.command == "prepare":
        prepare()
    elif args.command == "report":
        report(args.alpha_white)
    else:
        run(args.command, args.alpha_white)


if __name__ == "__main__":
    main()
