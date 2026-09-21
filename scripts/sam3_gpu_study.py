#!/usr/bin/env python3
"""Reproducible GPU label/packaging study. Sources and previous runs are immutable."""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import html
import importlib.metadata
import json
import os
from pathlib import Path
import random
import subprocess
import time

from detect_labels_pilot import ROOT, box_iou, image_rgb, json_digest, sha256, validate_existing, write_json
from label_segmentation_pilot import choose_main_label

BASE = ROOT / "data/audit/sam3_gpu"
MODEL = ROOT / "weights/research/label_segmentation/sam3-converted"
PACK_PROMPTS = ["label", "wine label", "wine packaging", "wine box",
                "front panel of a wine box", "wine carton", "wine pouch", "beverage can"]


def prepare():
    from PIL import Image, ImageOps
    rng = random.Random(20260917)
    catalog = [json.loads(x) for x in (ROOT / "data/catalog/curated/catalog.jsonl").read_text().splitlines()]
    old = json.loads((ROOT / "data/audit/label_segmentation/sample_manifest.json").read_text())["images"]
    entries, selected = [], {}
    by_slug = {r["slug"]: r for r in catalog}

    def packaging(row):
        text = (row["slug"] + " " + row["title"]).lower()
        return any(k in text for k in ("boks", "banke", "пакет", "короб", "brule-frizzante"))

    def add(row, reason):
        selected.setdefault(row["reference_sha256"], (row, reason))

    for entry in old:
        if entry["kind"] == "catalog":
            add(by_slug[entry["slug"]], "previous pilot")
        else:
            entries.append({**entry, "group": "eval", "gt_boxes": []})
    for row in catalog:
        if packaging(row):
            add(row, "packaging keyword; verify visually")
    ranked = sorted(catalog, key=lambda r: r["image_size"][0] * r["image_size"][1])
    for row in ranked[:12] + ranked[-12:]:
        add(row, "resolution extreme")
    pool = sorted(catalog, key=lambda r: r["slug"])
    rng.shuffle(pool)
    for row in pool:
        if len(selected) >= 256:
            break
        add(row, "fixed-seed catalogue sample")
    controls = 0
    for row, reason in selected.values():
        is_pack = packaging(row)
        control = not is_pack and reason == "previous pilot" and controls < 4
        controls += int(control)
        entries.append({"id": "cat_" + row["slug"], "group": "catalog", "slug": row["slug"],
                        "title": row["title"], "path": str(ROOT / row["reference_path"]),
                        "reason": reason, "packaging": is_pack, "prompt_control": control, "gt_boxes": []})
    audit = []
    for task in ("Wine Labels", "Products", "Text fields"):
        for annotation_file in sorted((ROOT / "zenodo_grain" / task).glob("*/annotations/instances.json")):
            data = json.loads(annotation_file.read_text())
            annotations = defaultdict(list)
            for a in data["annotations"]:
                annotations[a["image_id"]].append(a)
            light = annotation_file.parent.parent.name
            missing = [i["file_name"] for i in data["images"] if not (annotation_file.parent.parent / "images" / i["file_name"]).is_file()]
            audit.append({"task": task, "lighting": light, "images": len(data["images"]),
                          "annotations": len(data["annotations"]), "nonempty_segmentations": sum(bool(a.get("segmentation")) for a in data["annotations"]),
                          "multi_object_images": sum(len(a) > 1 for a in annotations.values()),
                          "missing_files": missing, "categories": data["categories"],
                          "annotation_sha256": sha256(annotation_file)})
            if task == "Wine Labels":
                multi = sorted((i for i in data["images"] if len(annotations[i["id"]]) > 1), key=lambda i: i["file_name"])
                single = sorted((i for i in data["images"] if len(annotations[i["id"]]) == 1), key=lambda i: i["file_name"])
                rng.shuffle(multi); rng.shuffle(single)
                chosen = multi[:15] + single[:30-min(15, len(multi))]
            elif task == "Products" and light == "Good Lighting":
                chosen = sorted((i for i in data["images"] if annotations[i["id"]]
                                 and all(a["category_id"] != 5 for a in annotations[i["id"]])), key=lambda i: i["file_name"])
                rng.shuffle(chosen)
                chosen = chosen[:20]
            else:
                continue
            for item in chosen:
                boxes = []
                if task == "Wine Labels":
                    for a in annotations[item["id"]]:
                        x, y, w, h = a["bbox"]
                        boxes.append([x, y, x+w, y+h])
                entries.append({"id": "grain_" + ("labels_" if task == "Wine Labels" else "negative_") + light.replace(" ", "_") + "_" + str(item["id"]),
                                "group": "grain_labels" if boxes else "grain_negative", "lighting": light,
                                "title": item["file_name"], "path": str(annotation_file.parent.parent / "images" / item["file_name"]),
                                "reason": "lighting/multiplicity stratification" if boxes else "no wine_bottle annotation; visually verify negative",
                                "gt_boxes": boxes, "annotation_file": str(annotation_file), "annotation_image_id": item["id"],
                                "annotation_size": [item["width"], item["height"]]})
    for entry in entries:
        entry["sha256"] = sha256(Path(entry["path"]))
        with Image.open(entry["path"]) as im:
            oriented = ImageOps.exif_transpose(im)
            entry["size"] = list(oriented.size)
            entry["has_alpha"] = "A" in im.getbands() or "transparency" in im.info
            if entry.get("annotation_size") and (list(im.size) != entry["annotation_size"] or im.getexif().get(274, 1) != 1):
                raise ValueError("Annotation coordinates require explicit orientation handling")
        entry["prompts"] = PACK_PROMPTS if entry.get("packaging") or entry.get("prompt_control") else (["label", "wine label"] if entry["group"] != "catalog" else ["label"])
    manifest = {"seed": 20260917, "selection": "256 unique catalogue references including stress cases; 3 eval; 30 GRAIN labels per lighting; 20 candidate negatives. Exploratory, not representative accuracy.",
                "entries": entries}
    validate_existing(BASE / "manifest.json", manifest)
    write_json(BASE / "manifest.json", manifest)
    write_json(BASE / "grain_audit.json", audit)
    print(Counter(e["group"] for e in entries), "tasks", sum(len(e["prompts"]) for e in entries), flush=True)


def match_boxes(predictions, truth, threshold=.5):
    """Score-ordered one-to-one matches, a fixed-threshold diagnostic, not COCO AP."""
    unused = set(range(len(truth)))
    matches = []
    for i in sorted(range(len(predictions)), key=lambda i: -predictions[i]["score"]):
        j = max(unused, key=lambda j: box_iou(predictions[i]["box_xyxy"], truth[j]), default=None)
        overlap = box_iou(predictions[i]["box_xyxy"], truth[j]) if j is not None else 0
        if overlap >= threshold:
            unused.remove(j)
            matches.append({"prediction": i, "truth": j, "iou": overlap})
    return {"tp": len(matches), "fp": len(predictions)-len(matches), "fn": len(truth)-len(matches), "matches": matches}


def gpu_status():
    result = subprocess.run(["nvidia-smi", "--query-gpu=name,driver_version,memory.used,memory.total,utilization.gpu", "--format=csv,noheader"], capture_output=True, text=True, check=True)
    return result.stdout.strip()


def load_runtime(precision):
    import torch
    from transformers import Sam3Model, Sam3Processor
    if not torch.cuda.is_available():
        raise RuntimeError("CUDA unavailable. Run with GPU access; CPU fallback is intentionally disabled.")
    torch.set_num_threads(4)
    torch.backends.cuda.matmul.allow_tf32 = False
    torch.backends.cudnn.allow_tf32 = False
    os.environ["HF_HUB_OFFLINE"] = "1"
    free, total = torch.cuda.mem_get_info()
    if free < 7 * 1024**3:
        raise RuntimeError(f"Only {free/1024**3:.1f} GiB GPU memory free; wait for other jobs, do not benchmark contention")
    manifest = json.loads((ROOT / "data/audit/label_segmentation/sam3_download.json").read_text())
    for name, digest in manifest["files_sha256"].items():
        if sha256(MODEL / name) != digest:
            raise ValueError("Checkpoint provenance mismatch")
    start = time.perf_counter()
    processor = Sam3Processor.from_pretrained(MODEL, local_files_only=True)
    model = Sam3Model.from_pretrained(MODEL, local_files_only=True).eval().to("cuda")
    torch.cuda.synchronize()
    return model, processor, {"load_seconds": time.perf_counter()-start, "gpu": gpu_status(), "precision": precision,
                              "weights": manifest["files_sha256"], "versions": {k: importlib.metadata.version(k) for k in ("torch", "transformers", "Pillow", "numpy")}}


def infer(model, processor, entry, prompt, precision):
    import torch
    import numpy as np
    from PIL import Image, ImageOps
    from contextlib import nullcontext
    torch.cuda.synchronize()
    start = time.perf_counter()
    with Image.open(entry["path"]) as source:
        image = image_rgb(ImageOps.exif_transpose(source), "white")
    w, h = image.size
    scale = min(1, 1024/max(w, h))
    mw, mh = max(1, round(w*scale)), max(1, round(h*scale))
    inputs = processor(images=image, text=prompt, return_tensors="pt").to("cuda")
    torch.cuda.synchronize()
    forward_start = time.perf_counter()
    with torch.inference_mode(), (torch.autocast("cuda", dtype=torch.bfloat16) if precision == "bf16" else nullcontext()):
        outputs = model(**inputs)
    torch.cuda.synchronize()
    forward_end = time.perf_counter()
    parsed = processor.post_process_instance_segmentation(outputs, threshold=.5, mask_threshold=.5, target_sizes=[[mh, mw]])[0]
    boxes, scores = parsed["boxes"].float().cpu().tolist(), parsed["scores"].float().cpu().tolist()
    candidates = [{"box_xyxy": [b[0]*w/mw, b[1]*h/mh, b[2]*w/mw, b[3]*h/mh], "score": s} for b, s in zip(boxes, scores)]
    choice = choose_main_label(candidates, w, h)
    index = choice["label_index"]
    mask = parsed["masks"][index].to(torch.uint8).cpu().numpy() if index is not None else np.zeros((mh, mw), dtype=np.uint8)
    torch.cuda.synchronize()
    finish = time.perf_counter()
    result = {"id": entry["id"], "prompt": prompt, "status": "mask" if index is not None else "no_detection",
              "candidates": candidates, "selected_index": index, "mask_size": [mw, mh], "source_size": [w, h],
              "mask_area_fraction": float(mask.mean()), "timing_ms": {"decode_preprocess_transfer": (forward_start-start)*1000,
              "forward": (forward_end-forward_start)*1000, "postprocess_select_transfer": (finish-forward_end)*1000, "total": (finish-start)*1000}}
    if entry["gt_boxes"]:
        result["box_metrics"] = match_boxes(candidates, entry["gt_boxes"])
        result["selected_best_gt_iou"] = max((box_iou(candidates[index]["box_xyxy"], b) for b in entry["gt_boxes"]), default=0) if index is not None else 0
    return result, mask, image


def task_key(entry, prompt):
    return entry["id"] + "__" + prompt.replace(" ", "_")


def needs_packaging_probe(result):
    return result["status"] == "no_detection" or result["mask_area_fraction"] < .03


def choose_packaging_result(label, box):
    """Exploratory fallback; it selects a crop, it does not certify wine presence."""
    if (needs_packaging_probe(label) and box and box["status"] == "mask"
            and box["mask_area_fraction"] > max(.1, 2 * label["mask_area_fraction"])):
        return box
    return label


def primary_scenario_reasons(entry):
    """Annotation-only proxy for one large central target; never uses predictions."""
    if len(entry["gt_boxes"]) != 1:
        return ["multiple_annotated_targets"]
    x, y, x2, y2 = entry["gt_boxes"][0]
    w, h = entry["size"]
    reasons = []
    if abs((x+x2)/(2*w)-.5) > .15:
        reasons.append("off_center")
    if (x2-x)*(y2-y)/(w*h) < .06:
        reasons.append("small_target")
    if min(x/w, y/h, 1-x2/w, 1-y2/h) < .01:
        reasons.append("edge_touch")
    return reasons


def scenario_report(args):
    entries = json.loads((BASE / "manifest.json").read_text())["entries"]
    selected, excluded = [], []
    for entry in entries:
        if entry["group"] != "grain_labels":
            continue
        reasons = primary_scenario_reasons(entry)
        if reasons:
            excluded.append({"id": entry["id"], "reasons": reasons})
        else:
            selected.append(entry)
    write_json(BASE / "primary_scenario.json", {"purpose": "User scenario: one foreground bottle approximately centered. Annotation-only proxy; contains front and back labels, occlusion and varied light. Not an independent benchmark.",
               "manifest_sha256": sha256(BASE / "manifest.json"), "rule": {"gt_count": 1, "horizontal_center_distance_max": .15, "bbox_area_fraction_min": .06, "border_margin_min": .01},
               "ids": [e["id"] for e in selected], "excluded": excluded})
    output = BASE / args.name
    summary, cards = [], []
    for prompt in ("label", "wine label"):
        rows = [json.loads((output / "results" / (task_key(e,prompt)+".json")).read_text()) for e in selected]
        summary.append({"prompt": prompt, "images": len(rows), "selected_matches_gt_iou_05": sum(r["selected_best_gt_iou"] >= .5 for r in rows),
                        "gt_detected": sum(r["box_metrics"]["tp"] for r in rows)})
    write_json(output / "primary_summary.json", summary)
    for entry in selected:
        key = task_key(entry, "label")
        cards.append(f'<article><h3>{html.escape(entry["id"])}</h3><img loading="lazy" src="review_images/{key}.jpg"></article>')
    style = '<style>body{font:16px system-ui;margin:24px;background:#f2f5f9}section{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:12px}article{background:white;padding:12px;overflow-wrap:anywhere}img{width:100%;height:470px;object-fit:contain}h3{font-size:14px}</style>'
    pages = (len(cards)+23)//24
    nav = ' · '.join(f'<a href="primary_{i+1}.html">{i+1}</a>' for i in range(pages))
    for i in range(pages):
        counts = '; '.join(f'{r["prompt"]}: {r["selected_matches_gt_iou_05"]}/{r["images"]}' for r in summary)
        (output / f"primary_{i+1}.html").write_text('<!doctype html><meta charset="utf-8">'+style+f'<a href="review.html">Весь эксперимент</a><h1>Целевой сценарий: крупная центральная этикетка</h1><p>{len(selected)} кадров GRAIN, выбранных по разметке без учёта предсказаний. Совпадение выбранной рамки с GT при IoU ≥0.5: {counts}. Это не точность по slug и не оценка пиксельной полноты маски. Есть фронтальные и задние этикетки, частичные перекрытия.</p>'+nav+'<section>'+''.join(cards[i*24:(i+1)*24])+'</section>')
    index = output / "review.html"
    page = index.read_text()
    if 'href="primary_1.html"' not in page:
        page = page.replace('<ul>', '<ul><li><a href="primary_1.html"><b>Основной сценарий: одна крупная бутылка по центру</b></a></li>', 1)
        index.write_text(page)
    print(json.dumps(summary))


def run(args):
    import torch
    import numpy as np
    from PIL import Image, ImageDraw
    manifest = json.loads((BASE / "manifest.json").read_text())
    output = BASE / args.name
    entries = [e for e in manifest["entries"] if args.group == "all" or e["group"] == args.group]
    if args.command == "fallback":
        chosen = []
        for entry in entries:
            label = json.loads((output / "results" / (task_key(entry, "label") + ".json")).read_text())
            if needs_packaging_probe(label):
                chosen.append({**entry, "prompts": ["wine box"]})
        entries = chosen
        plan = {"manifest_sha256": sha256(BASE / "manifest.json"), "trigger": "no label or selected mask area fraction < .03",
                "accept": "wine box detected and mask area > max(.1, 2*label area)",
                "tuned_on_observed_box_failures": True, "ids": [e["id"] for e in entries]}
        validate_existing(output / "fallback_plan.json", plan)
        write_json(output / "fallback_plan.json", plan)
    if args.limit:
        entries = entries[:args.limit]
    model, processor, runtime = load_runtime(args.precision)
    config = {"manifest_sha256": sha256(BASE / "manifest.json"), "precision": args.precision, "model_weights": runtime["weights"],
              "versions": runtime["versions"], "device": torch.cuda.get_device_name(), "alpha_background": "white",
              "threshold": .5, "mask_threshold": .5, "max_mask_side": 1024, "selection": "unchanged .7 horizontal centrality + .3 sqrt box area fraction",
              "timing": "synchronized wall time, includes decode/preprocess/transfers/forward/postprocess/selection; excludes loading, warmup, PNG/report writing and HTTP"}
    validate_existing(output / "config.json", config)
    write_json(output / "config.json", config)
    write_json(output / f"runtime_{time.time_ns()}.json", runtime)
    warmup = []
    for _ in range(3):
        result, _, _ = infer(model, processor, entries[0], "label", args.precision)
        warmup.append(result["timing_ms"])
    write_json(output / f"warmup_{time.time_ns()}.json", warmup)
    torch.cuda.reset_peak_memory_stats()
    count = 0
    for entry in entries:
        if sha256(Path(entry["path"])) != entry["sha256"]:
            raise ValueError("Input changed")
        for prompt in entry["prompts"]:
            key = task_key(entry, prompt)
            destination = output / "results" / (key + ".json")
            if destination.exists():
                previous = json.loads(destination.read_text())
                if previous["config_sha256"] != json_digest(config) or previous["source_sha256"] != entry["sha256"] or sha256(output / previous["mask_path"]) != previous["mask_sha256"]:
                    raise ValueError("Stored result provenance differs")
                continue
            result, mask, image = infer(model, processor, entry, prompt, args.precision)
            result.update({"config_sha256": json_digest(config), "source_sha256": entry["sha256"], "gpu_status": gpu_status()})
            asset = output / "masks" / (key + ".png")
            asset.parent.mkdir(parents=True, exist_ok=True)
            Image.fromarray(mask*255).save(asset)
            result.update({"mask_path": str(asset.relative_to(output)), "mask_sha256": sha256(asset)})
            result["peak_allocated_mib"] = torch.cuda.max_memory_allocated()/1024**2
            result["peak_reserved_mib"] = torch.cuda.max_memory_reserved()/1024**2
            cpu = ROOT / "data/audit/label_segmentation/sam3_text_main_white/masks" / (entry["id"] + ".png")
            if prompt == "label" and cpu.exists():
                original = np.asarray(Image.open(cpu).resize((mask.shape[1], mask.shape[0]), Image.Resampling.NEAREST)) > 0
                binary = mask > 0
                result["cpu_fp32_mask_iou"] = float((original & binary).sum()/max(1, (original | binary).sum()))
            write_json(destination, result)
            count += 1
            print(f"{count} {entry['group']} {key}: {result['status']}, {len(result['candidates'])} candidates, {result['timing_ms']['total']:.0f}ms", flush=True)
    write_json(output / ("fallback_complete.json" if args.command == "fallback" else "complete.json"), {"requested_group": args.group, "limit": args.limit, "new_results": count, "gpu_end": gpu_status()})


def benchmark(args):
    import torch
    import numpy as np
    manifest = json.loads((BASE / "manifest.json").read_text())
    entries = [e for e in manifest["entries"] if e["group"] == "eval"]
    model, processor, runtime = load_runtime(args.precision)
    cold, _, _ = infer(model, processor, entries[0], "label", args.precision)
    for _ in range(3):
        infer(model, processor, entries[0], "label", args.precision)
    torch.cuda.reset_peak_memory_stats()
    rows = []
    for _ in range(10):
        for entry in entries:
            row, _, _ = infer(model, processor, entry, "label", args.precision)
            rows.append({"id": entry["id"], **row["timing_ms"]})
    stats = {key: {"p50": float(np.percentile([r[key] for r in rows], 50)), "p95": float(np.percentile([r[key] for r in rows], 95))} for key in rows[0] if key != "id"}
    write_json(BASE / args.name / "benchmark.json", {"runtime": runtime, "gpu_end": gpu_status(), "cold": cold["timing_ms"], "warmup_excluded": 3,
               "repeats_per_image": 10, "rows": rows, "summary_ms": stats, "peak_allocated_mib": torch.cuda.max_memory_allocated()/1024**2,
               "peak_reserved_mib": torch.cuda.max_memory_reserved()/1024**2, "timing_scope": "same infer as study; batch 1; disk warm cache; no HTTP, model loading or output serialization"})
    print(json.dumps(stats, indent=2))


def report(args):
    import numpy as np
    from PIL import Image, ImageOps, ImageDraw
    output = BASE / args.name
    entries = {e["id"]: e for e in json.loads((BASE / "manifest.json").read_text())["entries"]}
    rows = [json.loads(p.read_text()) for p in sorted((output / "results").glob("*.json"))]
    assets = output / "review_images"
    assets.mkdir(exist_ok=True)
    groups = defaultdict(list)
    metrics = defaultdict(lambda: Counter(tp=0, fp=0, fn=0, images=0, selected_gt_match=0, no_detection=0))
    flags = []
    for row in rows:
        entry = entries[row["id"]]
        key = task_key(entry, row["prompt"])
        with Image.open(entry["path"]) as im:
            image = image_rgb(ImageOps.exif_transpose(im), "white").resize(tuple(row["mask_size"])).convert("RGBA")
        mask = Image.open(output / row["mask_path"]).convert("L")
        tint = Image.new("RGBA", image.size, (25, 200, 80, 0)); tint.putalpha(mask.point(lambda p: p//3))
        image = Image.alpha_composite(image, tint)
        draw = ImageDraw.Draw(image)
        sx, sy = image.width/entry["size"][0], image.height/entry["size"][1]
        for i, candidate in enumerate(row["candidates"]):
            b = candidate["box_xyxy"]
            draw.rectangle([b[0]*sx,b[1]*sy,b[2]*sx,b[3]*sy], outline="#ff8800" if i == row["selected_index"] else "#4477ff", width=2)
        for b in entry["gt_boxes"]:
            draw.rectangle([b[0]*sx,b[1]*sy,b[2]*sx,b[3]*sy], outline="#e040d0", width=2)
        image.thumbnail((500, 650)); image.convert("RGB").save(assets / (key + ".jpg"), quality=85)
        warnings = []
        if row["status"] == "no_detection": warnings.append("no_detection")
        if row["selected_index"] is not None:
            if row["mask_area_fraction"] < .01: warnings.append("tiny_selected_region")
            if row["mask_area_fraction"] > .85: warnings.append("almost_whole_image")
            b = row["candidates"][row["selected_index"]]["box_xyxy"]
            w,h = entry["size"]
            if b[0] < .01*w or b[1] < .01*h or b[2] > .99*w or b[3] > .99*h: warnings.append("touches_edge")
        if len(row["candidates"]) > 1: warnings.append("multiple_candidates")
        if entry["gt_boxes"] and row.get("selected_best_gt_iou",0) < .5: warnings.append("selected_gt_mismatch")
        if entry["group"] == "grain_negative" and row["candidates"]: warnings.append("candidate_negative_has_detection")
        if warnings: flags.append({"id": row["id"], "prompt": row["prompt"], "flags": warnings})
        metric = metrics[(entry["group"], entry.get("lighting",""), row["prompt"])]
        metric["images"] += 1
        metric["no_detection"] += row["status"] == "no_detection"
        if "box_metrics" in row:
            for k in ("tp", "fp", "fn"): metric[k] += row["box_metrics"][k]
            metric["selected_gt_match"] += row["selected_best_gt_iou"] >= .5
        card = f'<article><h3>{html.escape(entry["title"])}</h3><p>{html.escape(row["id"])} · <b>{html.escape(row["prompt"])}</b></p><img loading="lazy" src="review_images/{key}.jpg"><p>{len(row["candidates"])} областей · {row["timing_ms"]["total"]:.0f} мс · площадь маски {row["mask_area_fraction"]:.1%}</p><p>{html.escape(", ".join(warnings))}</p></article>'
        group = "packaging_prompts" if entry.get("packaging") or entry.get("prompt_control") else entry["group"]
        groups[group].append(card)
    links = []
    style = '<style>body{font:16px system-ui;margin:24px;background:#f2f5f9}section{display:grid;grid-template-columns:repeat(auto-fit,minmax(270px,1fr));gap:12px}article{background:white;padding:12px;overflow-wrap:anywhere}img{width:100%;height:470px;object-fit:contain}h3{font-size:15px}</style>'
    for group, cards in groups.items():
        for offset in range(0,len(cards),30):
            name = f"{group}_{offset//30+1}.html"
            links.append(f'<li><a href="{name}">{group}: {offset+1}–{min(offset+30,len(cards))}</a></li>')
            (output / name).write_text('<!doctype html><meta charset="utf-8">'+style+'<a href="review.html">Все разделы</a><p>Зелёный — выбранная маска; оранжевая рамка — выбор; синие — остальные кандидаты; розовые — рамки GRAIN. Флаги — поводы для ревью, не доказанные ошибки.</p><section>'+''.join(cards[offset:offset+30])+'</section>')
    notes_file = BASE / "review_notes.json"
    if notes_file.exists():
        cards = []
        for note in json.loads(notes_file.read_text()):
            key = task_key(entries[note["id"]], note.get("prompt", "label"))
            if (assets / (key + ".jpg")).exists():
                cards.append(f'<article><h3>{html.escape(note["type"])}</h3><p>{html.escape(note["id"])}</p><img src="review_images/{key}.jpg"><p>{html.escape(note["note"])}</p></article>')
        (output / "findings.html").write_text('<!doctype html><meta charset="utf-8">'+style+'<a href="review.html">Все разделы</a><h1>Разобранные случаи</h1><section>'+''.join(cards)+'</section>')
        links.insert(0, '<li><a href="findings.html"><b>Начать здесь: разобранные ошибки и ограничения</b></a></li>')
    fallback_file = output / "fallback_plan.json"
    if fallback_file.exists():
        indexed = {(r["id"],r["prompt"]): r for r in rows}
        decisions = []
        for entry in entries.values():
            label = indexed.get((entry["id"], "label"))
            if label is None:
                continue
            box = indexed.get((entry["id"], "wine box"))
            selected = choose_packaging_result(label, box)
            decisions.append({"id": entry["id"], "group": entry["group"], "triggered": needs_packaging_probe(label),
                              "chosen_prompt": selected["prompt"], "changed": selected is not label,
                              "baseline_mask_path": label["mask_path"], "chosen_mask_path": selected["mask_path"],
                              "total_ms_measured_separate_passes": label["timing_ms"]["total"] + (box["timing_ms"]["total"] if needs_packaging_probe(label) and box else 0)})
        write_json(output / "fallback_decisions.json", decisions)
        cards = []
        for decision in decisions:
            if not decision["changed"]:
                continue
            entry = entries[decision["id"]]
            for prompt in ("label", "wine box"):
                key = task_key(entry, prompt)
                cards.append(f'<article><h3>{html.escape(entry["title"])}</h3><p>{prompt}</p><img src="review_images/{key}.jpg"></article>')
        (output / "fallback.html").write_text('<!doctype html><meta charset="utf-8">'+style+'<style>section{grid-template-columns:repeat(2,minmax(0,1fr))}</style><a href="review.html">Все разделы</a><h1>Условный запасной запрос: до / после</h1><p>На 34 кадрах проверен wine box. Изменились только четыре коробки; остальные базовые результаты сохранены. Это отладка на наблюдавшихся случаях, не независимая валидация.</p><section>'+''.join(cards)+'</section>')
        links.insert(1, '<li><a href="fallback.html"><b>Коробки: результат условного запасного запроса</b></a></li>')
    (output / "review.html").write_text('<!doctype html><meta charset="utf-8">'+style+'<h1>SAM 3: GPU, расширенная выборка и упаковка</h1><p>Разведочная проверка. Каталог не имеет эталонных масок; GRAIN размечен рамками. Наличие области label не доказывает наличие вина. Изображения отчёта уменьшены; JSON хранит рамки в исходных координатах.</p><ul>'+''.join(links)+'</ul>')
    summary = [{"group": k[0], "lighting": k[1], "prompt": k[2], **v} for k,v in metrics.items()]
    write_json(output / "summary.json", summary)
    write_json(output / "review_flags.json", flags)
    print(f"{len(rows)} results; report {output / 'review.html'}")


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("command", choices=("prepare","run","fallback","benchmark","report","scenario"))
    p.add_argument("--name", default="bf16_v1")
    p.add_argument("--precision", choices=("bf16","fp32"), default="bf16")
    p.add_argument("--group", choices=("all","eval","catalog","grain_labels","grain_negative"), default="all")
    p.add_argument("--limit", type=int)
    args = p.parse_args()
    {"prepare": lambda: prepare(), "run": lambda: run(args), "fallback": lambda: run(args), "benchmark": lambda: benchmark(args), "report": lambda: report(args), "scenario": lambda: scenario_report(args)}[args.command]()


if __name__ == "__main__":
    main()
