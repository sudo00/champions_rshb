"""Reproducible live-photo evaluation; filenames/annotations never enter retrieval."""
from __future__ import annotations

import argparse
from collections import Counter
import json
from pathlib import Path
import re
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageOps

from label_rectification_pilot import ROOT, digest, immutable, read, rgb_oriented, write

BASE = ROOT / "data/audit/live_shop/v1"
CATALOG = ROOT / "data/catalog/curated/catalog.jsonl"


def plan(args: argparse.Namespace) -> None:
    cards = {r["slug"]: r for r in (json.loads(line) for line in CATALOG.read_text().splitlines())}
    entries, annotations = [], []
    for number, path in enumerate(sorted(args.input.iterdir()), 1):
        if path.suffix.lower() not in (".jpg", ".jpeg", ".png", ".webp"):
            continue
        checksum = digest(path)
        identifier = "shop_" + checksum[:20]
        stem = re.sub(r"[-_]second[-_]view$", "", path.stem)
        shelf = "polka" in stem
        expected = stem if stem in cards and not shelf else None
        entries.append({"id": identifier, "role": "query", "path": str(path.resolve()),
                        "source_sha256": checksum, "slugs": [], "packaging_hints": [],
                        "source_card_for_diagnostic_only": None, "group": "live_shop",
                        "origin": "user_phone_original"})
        annotations.append({"id": identifier, "number": number, "filename": path.name,
                            "source_sha256": checksum, "expected_slug": expected,
                            "status": "user_filename_catalog_match" if expected else "unresolved",
                            "scene": "shelf_stress" if shelf else "single_target_unchecked",
                            "session": "live_shop_first_visit", "group": stem,
                            "note": "Filename is evaluation metadata only; unmatched names do not prove absence."})
    if len({e["id"] for e in entries}) != len(entries):
        raise ValueError("Duplicate image bytes; group duplicates before evaluation")
    immutable(args.output / "gallery/manifest.json", {
        "catalog_sha256": digest(CATALOG), "catalog_cards": len(cards), "gallery_references": 0,
        "query_count": len(entries), "entries": entries})
    immutable(args.output / "initial_annotations.json", {"catalog_sha256": digest(CATALOG),
              "use": "evaluation_only", "split": "diagnostic_development; independent photos required after tuning",
              "images": annotations})
    font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 14)
    for offset in range(0, len(entries), 20):
        sheet = Image.new("RGB", (1400, 1600), "#eeeeee")
        draw = ImageDraw.Draw(sheet)
        for local, (entry, annotation) in enumerate(zip(entries[offset:offset+20], annotations[offset:offset+20])):
            x, y = (local % 4) * 350, (local // 4) * 320
            image, _ = rgb_oriented(Path(entry["path"]))
            image.thumbnail((340, 260), Image.Resampling.LANCZOS)
            sheet.paste(image, (x + (350-image.width)//2, y))
            name = annotation["filename"]
            draw.text((x+5, y+263), f'{annotation["number"]}: {name[:39]}', fill="black", font=font)
            draw.text((x+5, y+283), name[39:80], fill="black", font=font)
            draw.text((x+5, y+302), "CATALOG" if annotation["expected_slug"] else "CHECK", fill="black", font=font)
        path = args.output / f"contact_{offset//20+1}.jpg"
        sheet.save(path, quality=90)
    print(Counter(a["status"] for a in annotations), "images", len(entries), flush=True)


def prepare_ocr(args: argparse.Namespace) -> None:
    from worker.pipeline.cylinder_geometry import rectify
    gallery = args.output / "gallery"
    output = args.output / "ocr"
    entries = []
    for source in read(gallery / "manifest.json")["entries"]:
        row = read(gallery / "records" / (source["id"] + ".json"))
        if digest(Path(row["path"])) != row["source_sha256"]:
            raise ValueError("Source changed")
        image, _ = rgb_oriented(Path(row["path"]))
        variants = []
        for view in row["views"]:
            if view["family"] not in ("body", "label", "flat"):
                continue
            name = view["family"]
            if name == "flat":
                pixels, _ = rectify(np.array(image), row["cylinder"]["model"], "F", max_side=1600)
                crop = Image.fromarray(pixels)
                mapping = {"cylinder_model": row["cylinder"]["model"]}
            else:
                box = view["crop_box_original"]
                crop = image.crop(box)
                crop.thumbnail((1600, 1600), Image.Resampling.LANCZOS)
                sx, sy = (box[2]-box[0])/crop.width, (box[3]-box[1])/crop.height
                mapping = {"input_to_original": [[sx, 0, box[0]], [0, sy, box[1]], [0, 0, 1]]}
            path = output / "images" / f'{row["id"]}_{name}.png'
            path.parent.mkdir(parents=True, exist_ok=True)
            crop.save(path)
            variants.append({"name": name, "path": str(path.resolve()), "sha256": digest(path),
                             "size": list(crop.size), **mapping})
        mask = row["masks"].get("object") or row["masks"].get("label")
        entries.append({"id": row["id"], "source_path": row["path"], "source_sha256": row["source_sha256"],
                        "source_size": row["source_size"], "variants": variants,
                        "mask_path": str((gallery / mask["path"]).resolve()) if mask else None,
                        "mask_sha256": mask["sha256"] if mask else None})
    immutable(output / "manifest.json", {"gallery_manifest_sha256": digest(gallery / "manifest.json"),
              "gallery_config_sha256": digest(gallery / "config.json"), "images": entries})
    print("OCR inputs:", sum(len(e["variants"]) for e in entries), "x 2 languages", flush=True)


def ocr(args: argparse.Namespace) -> None:
    import importlib.metadata
    import os
    import time
    import cv2
    from bottle_ocr_pilot import DETECTOR, RECOGNIZERS, in_bottle
    from worker.pipeline.cylinder_geometry import input_to_original, densify_polygon
    from worker.pipeline.rectification import points_h
    cv2.setNumThreads(1)
    output = args.output / args.ocr_dir
    manifest = read(output / "manifest.json")
    cache = ROOT / "weights/cache/ocr"
    for key, value in {"PADDLE_PDX_CACHE_HOME": cache/"paddlex", "PADDLE_HOME": cache/"paddle",
                       "HF_HOME": cache/"huggingface", "XDG_CACHE_HOME": cache/"xdg"}.items():
        os.environ.setdefault(key, str(value))
    os.environ.setdefault("PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK", "True")
    models = cache / "paddlex/official_models"
    weights = {name: {str(p.relative_to(models/name)): digest(p) for p in sorted((models/name).rglob("*")) if p.is_file()}
               for name in [DETECTOR, *RECOGNIZERS.values()]}
    if any(not files for files in weights.values()):
        raise ValueError("Missing OCR weights")
    for entry in manifest["images"]:
        if digest(Path(entry["source_path"])) != entry["source_sha256"]:
            raise ValueError("Changed source")
        if entry["mask_path"] and digest(Path(entry["mask_path"])) != entry["mask_sha256"]:
            raise ValueError("Changed target mask")
        for view in entry["variants"]:
            if digest(Path(view["path"])) != view["sha256"]:
                raise ValueError("Changed OCR input")
    config = {"manifest_sha256": digest(output/"manifest.json"), "weights_sha256": weights,
              "max_side": 1600, "device": "cpu", "threads": 4, "mkldnn": args.mkldnn,
              "doc_unwarp": False, "doc_orientation": False, "line_orientation": False,
              "color": "BGR", "threshold": 0., "mapping": "homography_or_dense_cylinder",
              "versions": {n: importlib.metadata.version(n) for n in ["paddleocr", "paddlepaddle", "numpy", "Pillow"]}}
    immutable(output/"ocr_config.json", config)
    # Freeze implementation used for OCR even if report-only code is extended later.
    snapshot = output/"ocr_script.py"
    if not snapshot.exists():
        snapshot.write_bytes(Path(__file__).read_bytes())
    from paddleocr import PaddleOCR
    for language, model_name in RECOGNIZERS.items():
        pending = []
        ordinal = -1
        for entry in manifest["images"]:
            for view in entry["variants"]:
                ordinal += 1
                if ordinal % args.shards != args.shard:
                    continue
                path = output/"results"/f'{entry["id"]}_{view["name"]}_{language}.json'
                if path.exists():
                    old = read(path)
                    if old["input_sha256"] != view["sha256"] or old["config_sha256"] != digest(output/"ocr_config.json"):
                        raise ValueError("Stale OCR result")
                else:
                    pending.append((entry, view, path))
        if not pending:
            continue
        engine = PaddleOCR(text_detection_model_name=DETECTOR, text_detection_model_dir=str(models/DETECTOR),
            text_recognition_model_name=model_name, text_recognition_model_dir=str(models/model_name),
            use_doc_orientation_classify=False, use_doc_unwarping=False, use_textline_orientation=False,
            text_rec_score_thresh=0., text_det_limit_side_len=1600, text_det_limit_type="max",
            device="cpu", cpu_threads=4, enable_mkldnn=args.mkldnn)
        sample = np.array(Image.open(pending[0][1]["path"]).convert("RGB"))[:, :, ::-1].copy()
        list(engine.predict(sample))
        for number, (entry, view, path) in enumerate(pending, 1):
            mask = np.array(Image.open(entry["mask_path"]).convert("L")) > 0
            mask = cv2.dilate(mask.astype(np.uint8), np.ones((7, 7), np.uint8))
            pixels = np.array(Image.open(view["path"]).convert("RGB"))[:, :, ::-1].copy()
            started = time.perf_counter()
            predictions = list(engine.predict(pixels))
            elapsed = time.perf_counter() - started
            if len(predictions) != 1:
                raise ValueError("Expected one OCR result")
            raw = predictions[0].json
            raw = json.loads(raw) if isinstance(raw, str) else raw
            data = raw.get("res", raw)
            if not len(data["rec_texts"]) == len(data["rec_scores"]) == len(data["rec_polys"]):
                raise ValueError("OCR arrays disagree")
            lines = []
            for text, score, polygon in zip(data["rec_texts"], data["rec_scores"], data["rec_polys"]):
                if "cylinder_model" in view:
                    original = input_to_original(densify_polygon(polygon), view["cylinder_model"], "F", view["size"])
                else:
                    original = points_h(polygon, np.array(view["input_to_original"]))
                lines.append({"text": text, "confidence": float(score), "polygon_input": polygon,
                              "polygon_original": original.tolist(),
                              "on_target_bottle": in_bottle(original, mask, entry["source_size"])})
            write(path, {"image_id": entry["id"], "variant": view["name"], "language": language,
                         "input_sha256": view["sha256"], "source_sha256": entry["source_sha256"],
                         "config_sha256": digest(output/"ocr_config.json"),
                         "inference_seconds": elapsed, "lines": lines})
            print(language, number, "/", len(pending), entry["id"], view["name"], len(lines), round(elapsed, 2), flush=True)
        del engine


def load_embeddings(base: Path) -> tuple[np.ndarray, list[dict], dict]:
    receipt = read(base / "encoding.json")
    for name, key in [("features.npy", "features_sha256"), ("views.json", "views_sha256"), ("config.json", "config_sha256")]:
        if digest(base/name) != receipt[key]:
            raise ValueError("Changed encoding artifact: " + str(base/name))
    return np.load(base/"features.npy", allow_pickle=False), read(base/"views.json"), read(base/"config.json")


def search_visual(args: argparse.Namespace) -> None:
    import time
    sys.path.insert(0, str(ROOT / "worker"))
    from pipeline.visual_search import VisualIndex
    model = args.model
    stored = ROOT / "data/audit/visual_search" / (model + "_v1")
    query_dir = args.output / "encoders" / model
    vectors, views, config = load_embeddings(stored)
    queries, qviews, qconfig = load_embeddings(query_dir)
    for key in ("model", "model_download_sha256", "side", "resize", "precision", "pooling", "rope_periods"):
        if config[key] != qconfig[key]:
            raise ValueError("Encoder configuration differs: " + key)
    old_gallery = ROOT / "data/audit/visual_search/gallery_v2"
    manifest = read(args.output / "gallery/manifest.json")
    if digest(CATALOG) != manifest["catalog_sha256"] or digest(CATALOG) != read(old_gallery/"manifest.json")["catalog_sha256"]:
        raise ValueError("Catalogue changed")
    if digest(args.output/"gallery/config.json") != qconfig["gallery_config_sha256"]:
        raise ValueError("Changed query preparation")
    cards = [json.loads(line) for line in CATALOG.read_text().splitlines()]
    gi = [i for i, v in enumerate(views) if v["role"] == "gallery"]
    index = VisualIndex(vectors[gi], [views[i] for i in gi], cards)
    rows = []
    for number, entry in enumerate(manifest["entries"], 1):
        prepared = read(args.output/"gallery/records"/(entry["id"]+".json"))
        indices = [i for i, v in enumerate(qviews) if v["record_id"] == entry["id"]]
        selected = [qviews[i] for i in indices]
        started = time.perf_counter()
        modes = {mode: index.search(queries[indices], selected, augment=augment, target_detected=prepared["target_detected"])
                 for mode, augment in [("baseline", False), ("augmented", True)]}
        rows.append({"id": entry["id"], "source_sha256": entry["source_sha256"],
                     "results": modes, "search_ms": (time.perf_counter()-started)*1000})
        if number % 20 == 0:
            print(model, number, "/", len(manifest["entries"]), flush=True)
    output = args.output / "baseline"
    immutable(output / (model+".json"), {"catalog_sha256": digest(CATALOG),
        "gallery_encoding_sha256": digest(stored/"encoding.json"), "query_encoding_sha256": digest(query_dir/"encoding.json"),
        "algorithm_sha256": digest(ROOT/"worker/pipeline/visual_search.py"), "images": rows})
    (output/"provenance").mkdir(exist_ok=True)
    (output/"provenance/visual_search.py").write_bytes((ROOT/"worker/pipeline/visual_search.py").read_bytes())
    print("Saved", output / (model+".json"), flush=True)


def search_text(args: argparse.Namespace) -> None:
    sys.path.insert(0, str(ROOT / "worker"))
    from pipeline.text_search import CatalogTextIndex
    output = args.output / args.ocr_dir
    manifest = read(output/"manifest.json")
    if read(output/"ocr_config.json")["manifest_sha256"] != digest(output/"manifest.json"):
        raise ValueError("OCR manifest changed")
    index = CatalogTextIndex.from_file(CATALOG)
    rows, provenance = [], {}
    for entry in manifest["images"]:
        observations = []
        groups = {}
        for view in entry["variants"]:
            for language in ("russian", "latin"):
                path = output/"results"/f'{entry["id"]}_{view["name"]}_{language}.json'
                if not path.exists():
                    raise ValueError("OCR incomplete: " + str(path))
                value = read(path)
                if (value["input_sha256"] != view["sha256"] or
                    value["source_sha256"] != entry["source_sha256"] or
                    value["config_sha256"] != digest(output/"ocr_config.json")):
                    raise ValueError("Stale OCR")
                provenance[str(path)] = digest(path)
                lines = [{**line, "source": view["name"]+":"+language} for line in value["lines"]]
                observations.extend(lines)
                groups.setdefault(view["name"], []).extend(lines)
        searches = {"combined": index.search(observations)}
        searches.update({key: index.search(lines) for key, lines in groups.items()})
        rows.append({"id": entry["id"], "source_sha256": entry["source_sha256"],
                     "observations": observations, "searches": searches})
    target = args.output / "baseline/ocr.json"
    immutable(target, {"catalog_sha256": digest(CATALOG), "ocr_inputs_sha256": provenance,
        "algorithm_sha256": digest(ROOT/"worker/pipeline/text_search.py"), "images": rows})
    (target.parent/"provenance/text_search.py").write_bytes((ROOT/"worker/pipeline/text_search.py").read_bytes())
    print("Saved", target, flush=True)


def evaluate(args: argparse.Namespace) -> None:
    sys.path.insert(0, str(ROOT / "worker"))
    from pipeline.visual_search import fuse_rankings
    annotations = read(args.output/"annotations.json")
    documents = {m: read(args.output/"baseline"/(m+".json")) for m in ("siglip2", "dinov3_timm", "dinov3_large", "ocr")}
    if any(d["catalog_sha256"] != digest(CATALOG) for d in documents.values()):
        raise ValueError("Changed catalogue")
    by_id = {name: {r["id"]: r for r in doc["images"]} for name, doc in documents.items()}
    rows = []
    for annotation in annotations["images"]:
        identifier = annotation["id"]
        inputs = {name: values[identifier] for name, values in by_id.items()}
        if any(r["source_sha256"] != annotation["source_sha256"] for r in inputs.values()):
            raise ValueError("Annotation source differs")
        modes = {"ocr": inputs["ocr"]["searches"]["combined"]["candidates"]}
        for model in ("siglip2", "dinov3_timm", "dinov3_large"):
            visual = inputs[model]["results"]
            for mode in ("baseline", "augmented"):
                modes[model+"_"+mode] = visual[mode]["candidates"]
            for branch, candidates in visual["baseline"]["branches"].items():
                modes[model+"_"+branch] = candidates
            modes[model+"_ocr_rrf"] = fuse_rankings({"visual": modes[model+"_augmented"], "ocr": modes["ocr"]})
        modes["ensemble"] = fuse_rankings({m: modes[m+"_augmented"] for m in ("siglip2", "dinov3_timm", "dinov3_large")})
        modes["ensemble_ocr_rrf"] = fuse_rankings({"visual": modes["ensemble"], "ocr": modes["ocr"]})
        expected = annotation["expected_slug"]
        ranks = {mode: next((i+1 for i, c in enumerate(candidates) if c["slug"] == expected), None)
                 for mode, candidates in modes.items()} if expected else {}
        rows.append({"id": identifier, "annotation": annotation, "candidates": modes, "ranks": ranks,
                     "observations": inputs["ocr"]["observations"],
                     "ocr_status": inputs["ocr"]["searches"]["combined"]["status"]})
    modes = sorted(set(k for r in rows for k in r["candidates"]))
    metrics = {}
    for group in ("all_reviewed", "user_catalog_names", "additional_reviewed"):
        known = [r for r in rows if r["annotation"]["expected_slug"] and
                 (group == "all_reviewed" or bool(r["annotation"].get("user_expected_slug")) == (group == "user_catalog_names"))]
        metrics[group] = {mode: {"n": len(known), "top1": sum(r["ranks"].get(mode) == 1 for r in known),
                    "top10": sum(r["ranks"].get(mode) is not None for r in known)} for mode in modes}
    output = args.output / "baseline/evaluation.json"
    value = {"annotations_sha256": digest(args.output/"annotations.json"),
             "inputs_sha256": {m: digest(args.output/"baseline"/(m+".json")) for m in documents},
             "split": "development_diagnostic; no independent post-tuning accuracy claim", "metrics": metrics, "images": rows}
    write(output, value)
    print(json.dumps(metrics["all_reviewed"], indent=2), flush=True)


def report(args: argparse.Namespace) -> None:
    import os
    sys.path.insert(0, str(ROOT / "worker"))
    from pipeline.label_observations import extract_observed_fields
    baseline = read(args.output/"baseline/evaluation.json")
    if baseline["annotations_sha256"] != digest(args.output/"annotations.json"):
        raise ValueError("Annotations changed; rerun evaluation before reporting")
    improved_path = args.output/"improved/results.json"
    improved = {r["id"]: r for r in read(improved_path)["images"]} if improved_path.exists() else {}
    if improved and read(improved_path)["annotations_sha256"] != baseline["annotations_sha256"]:
        raise ValueError("Improved results used different annotations")
    cards = {r["slug"]: r for r in (json.loads(line) for line in CATALOG.read_text().splitlines())}
    body_views = {v["record_id"]: v for v in read(args.output/"encoders/siglip2/views.json") if v["family"] == "body"}
    def url(path):
        return os.path.relpath(path, args.output)
    rows = []
    for result in baseline["images"]:
        identifier = result["id"]
        prepared = read(args.output/"gallery/records"/(identifier+".json"))
        reference = next(v for v in prepared["views"] if v["family"] == "reference")
        views = []
        for view in prepared["views"]:
            if view["family"] not in ("body", "label", "flat"):
                continue
            path = body_views[identifier]["path"] if view["family"] == "body" and identifier in body_views else args.output/"gallery"/view["path"]
            views.append({"name": view["family"], "url": url(path)})
        candidates = {mode: [{"slug": c["slug"], "score": c.get("score", c.get("cosine")),
                              "evidence": c.get("evidence", []), "conflicts": c.get("conflicts", [])}
                             for c in items] for mode, items in result["candidates"].items()}
        ranks = dict(result["ranks"])
        if identifier in improved:
            item = improved[identifier]
            for mode, values in item["candidates"].items():
                candidates[mode] = values
                if result["annotation"]["expected_slug"]:
                    ranks[mode] = next((i+1 for i, c in enumerate(values) if c["slug"] == result["annotation"]["expected_slug"]), None)
        observed = extract_observed_fields(result["observations"])
        rows.append({"id": identifier, "annotation": result["annotation"], "source_size": prepared["source_size"],
                     "image": url(args.output/"gallery"/reference["path"]), "views": views,
                     "candidates": candidates, "ranks": ranks, "observed": observed,
                     "target_detected": prepared["target_detected"]})
    used = {c["slug"] for r in rows for values in r["candidates"].values() for c in values}
    used.update(r["annotation"]["expected_slug"] for r in rows if r["annotation"]["expected_slug"])
    lookup = {slug: {"title": cards[slug]["title"], "winery": cards[slug].get("winery"),
                     "grapes": cards[slug].get("grapes"), "image": url(ROOT/cards[slug]["reference_path"])} for slug in used}
    payload = {"images": rows, "cards": lookup, "metrics": baseline["metrics"],
               "improved_metrics": read(improved_path).get("metrics") if improved else None}
    write(args.output/"review_data.json", payload)
    encoded = json.dumps(payload, ensure_ascii=False).replace("<", "\\u003c").replace("&", "\\u0026")
    template = '''<!doctype html><html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Магазинные фотографии — проверка поиска</title><style>
body{font:16px system-ui;color:#172b30;background:#f4f5f1;margin:24px}h1{font-size:28px}h2{font-size:19px;overflow-wrap:anywhere}header{position:sticky;top:0;background:#f4f5f1;padding:12px 0;z-index:2}select,input,button{font:inherit;padding:8px;margin:3px}section{border-top:2px solid #bdcbc5;padding:20px 0}.layout{display:grid;grid-template-columns:330px 1fr;gap:20px}.photo{width:100%;max-height:470px}.views,.cards{display:flex;gap:12px;overflow:auto}.views img{height:180px;max-width:200px;object-fit:contain}.card{background:white;padding:10px;min-width:170px;max-width:170px;overflow-wrap:anywhere}.card img{height:200px;width:160px;object-fit:contain}.correct{outline:3px solid #249466}.bad{color:#a32d22}.good{color:#13744a}small{font-size:12px}svg{background:#ddd}table{border-collapse:collapse}th,td{padding:8px;border-bottom:1px solid #ccd;text-align:left}.note{max-width:1000px}.readings{line-height:1.7}@media(max-width:850px){.layout{grid-template-columns:1fr}.photo{max-height:400px}header{position:static}}
</style><h1>Магазинные фотографии: распознавание и поиск</h1>
<p class="note">Все 76 снимков обработаны по изображениям. Имена файлов и ручная разметка используются только для оценки. Поиск идёт по всему каталогу. Это диагностическая выборка: улучшения на ней требуют проверки на новых фотографиях.</p>
<details><summary>Метрики и ограничения</summary><div id="metrics"></div><p>Top-1 — правильная карточка первая; Top-10 — среди десяти. Непроверенные и отсутствующие позиции не включены в точность. Несколько кадров одного вина зависимы. Оценки моделей не являются вероятностями.</p></details>
<header><label>Метод <select id="mode"></select></label><label>Снимки <select id="filter"><option value="all">Все</option><option value="known">С проверенным slug</option><option value="wrong">Ошибка первого места</option><option value="review">Без проверенного slug</option><option value="absent">Соответствие не найдено вручную</option><option value="shelf">Полки / несколько целей</option></select></label><input id="query" placeholder="Файл или название"><span id="count"></span></header><main id="rows"></main>
<script id="data" type="application/json">PAYLOAD</script><script>
const data=JSON.parse(document.getElementById('data').textContent);const $=id=>document.getElementById(id);
const esc=x=>String(x??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const modes=[...new Set(data.images.flatMap(r=>Object.keys(r.candidates)))];$('mode').innerHTML=modes.map(m=>`<option>${esc(m)}</option>`).join('');$('mode').value=modes.includes('hybrid_evidence')?'hybrid_evidence':'siglip2_ocr_rrf';
const metricRows=Object.entries(data.metrics.all_reviewed);if(data.improved_metrics)metricRows.push(...Object.entries(data.improved_metrics.all_reviewed));$('metrics').innerHTML='<table><tr><th>Метод</th><th>Top-1</th><th>Top-10</th></tr>'+metricRows.map(([m,v])=>`<tr><td>${esc(m)}</td><td>${v.top1}/${v.n}</td><td>${v.top10}/${v.n}</td></tr>`).join('')+'</table>';
function render(){const mode=$('mode').value,filter=$('filter').value,query=$('query').value.toLocaleLowerCase();const rows=data.images.filter(r=>{const a=r.annotation;if(query&&!JSON.stringify([a.filename,a.expected_slug]).toLocaleLowerCase().includes(query))return false;return filter==='all'||filter==='known'&&a.expected_slug||filter==='wrong'&&a.expected_slug&&r.ranks[mode]!==1||filter==='review'&&!a.expected_slug||filter==='absent'&&a.status==='no_correspondence_found'||filter==='shelf'&&(a.scene==='shelf_stress'||a.scene==='multiple_targets_unresolved');});$('count').textContent=rows.length+' / '+data.images.length;
$('rows').innerHTML=rows.map(r=>{const a=r.annotation,expected=a.expected_slug,rank=r.ranks[mode],candidates=r.candidates[mode]||[];const fields=['grapes','colors','printed_year_candidates'].map(k=>`${({grapes:'Сорта',colors:'Цвет',printed_year_candidates:'Напечатанные годы (не обязательно урожай)'})[k]}: ${(r.observed[k]||[]).map(v=>esc(v.value)).join(', ')||'не прочитано'}`).join('<br>');return `<section id="${r.id}"><h2>${a.number}. ${esc(a.filename)}</h2><p class="${expected?(rank===1?'good':'bad'):''}">${expected?'Проверенная карточка: '+esc(expected)+' · место: '+(rank||'вне Top-10'):'Соответствие не установлено; кандидаты не подтверждают идентичность'}</p><p><small>${esc(a.note)}</small></p><div class="layout"><div><svg class="photo" viewBox="0 0 ${r.source_size.join(' ')}"><image href="${esc(r.image)}" width="${r.source_size[0]}" height="${r.source_size[1]}"/><g class="evidence"></g></svg><button class="ocr" data-id="${r.id}">Показать области OCR</button><p>${fields}</p></div><div><div class="views">${r.views.map(v=>`<figure><img loading="lazy" src="${esc(v.url)}"><figcaption>${esc(v.name)}</figcaption></figure>`).join('')}</div><div class="cards">${candidates.map((c,i)=>{const card=data.cards[c.slug];return `<article class="card ${c.slug===expected?'correct':''}"><b>${i+1}. ${esc(card.title)}</b><p>${esc(card.winery)}</p><img loading="lazy" src="${esc(card.image)}"><small>${esc(c.slug)}</small></article>`;}).join('')||'Кандидатов нет'}</div></div></div><details><summary>Прочитанные надписи и источники (${r.observed.raw_label_lines.length})</summary><div class="readings">${r.observed.raw_label_lines.map(l=>`<div>${esc(l.text)} <small>· ${esc(l.source)} · ${Number(l.confidence).toFixed(2)}</small></div>`).join('')}</div><p>Название нового товара не заимствуется у ближайшей карточки. Автоматический отказ и точное выделение названия пока требуют отдельной проверки.</p></details></section>`;}).join('');
document.querySelectorAll('button.ocr').forEach(b=>b.onclick=()=>{const r=data.images.find(r=>r.id===b.dataset.id);const layer=document.querySelector('#'+r.id+' g.evidence');layer.replaceChildren();r.observed.raw_label_lines.forEach(l=>{if(!l.polygon_original)return;const p=document.createElementNS('http://www.w3.org/2000/svg','polygon');p.setAttribute('points',l.polygon_original.map(x=>x.join(',')).join(' '));p.setAttribute('fill','#3373df15');p.setAttribute('stroke','#3373df');p.setAttribute('stroke-width','2');p.setAttribute('vector-effect','non-scaling-stroke');const t=document.createElementNS('http://www.w3.org/2000/svg','title');t.textContent=l.text;p.appendChild(t);layer.appendChild(p);});});}
['mode','filter'].forEach(id=>$(id).addEventListener('change',render));$('query').addEventListener('input',render);render();
</script></html>'''
    (args.output/"review.html").write_text(template.replace("PAYLOAD", encoded))
    print(args.output/"review.html", flush=True)


def improve(args: argparse.Namespace) -> None:
    import time
    sys.path.insert(0, str(ROOT/"worker"))
    from pipeline.hybrid_search import HybridIndex, VERSION, BRANCH_WEIGHTS, TEXT_BONUS
    stored = ROOT/"data/audit/visual_search/siglip2_v1"
    query_dir = args.output/"encoders/siglip2"
    vectors, views, config = load_embeddings(stored)
    queries, qviews, qconfig = load_embeddings(query_dir)
    for key in ("model", "model_download_sha256", "side", "resize", "precision", "pooling", "rope_periods"):
        if config[key] != qconfig[key]:
            raise ValueError("Encoder configuration differs: " + key)
    cards = [json.loads(line) for line in CATALOG.read_text().splitlines()]
    gi = [i for i, view in enumerate(views) if view["role"] == "gallery"]
    index = HybridIndex(vectors[gi], [views[i] for i in gi], cards)
    source = read(args.output/"baseline/ocr.json")
    if source["catalog_sha256"] != digest(CATALOG):
        raise ValueError("Catalogue changed")
    rows = []
    for row in source["images"]:
        indices = [i for i, view in enumerate(qviews) if view["record_id"] == row["id"]]
        prepared = read(args.output/"gallery/records"/(row["id"]+".json"))
        if prepared["source_sha256"] != row["source_sha256"]:
            raise ValueError("OCR and query images differ")
        started = time.perf_counter()
        result = index.search(queries[indices], [qviews[i] for i in indices], row["observations"],
                              target_detected=prepared["target_detected"])
        rows.append({"id": row["id"], "source_sha256": row["source_sha256"],
                     "candidates": {"hybrid_evidence": result["candidates"],
                                    "visual_continuous": result.get("visual_continuous", []),
                                    "ocr_v2": result.get("text_candidates", [])},
                     "status": result["status"], "search_ms": (time.perf_counter()-started)*1000})
    # Evaluation annotations are first accessed after every query has been ranked.
    annotations = {r["id"]: r for r in read(args.output/"annotations.json")["images"]}
    metrics = {}
    for group in ("all_reviewed", "user_catalog_names", "additional_reviewed"):
        known = [r for r in rows if annotations[r["id"]]["expected_slug"] and
                 (group == "all_reviewed" or bool(annotations[r["id"]].get("user_expected_slug")) == (group == "user_catalog_names"))]
        metrics[group] = {}
        for mode in ("hybrid_evidence", "visual_continuous", "ocr_v2"):
            ranks = [next((i+1 for i, c in enumerate(r["candidates"][mode]) if c["slug"] == annotations[r["id"]]["expected_slug"]), None) for r in known]
            metrics[group][mode] = {"n": len(ranks), "top1": sum(rank == 1 for rank in ranks),
                                    "top10": sum(rank is not None for rank in ranks)}
    output = args.output / "improved"
    immutable(output/"results.json", {"version": VERSION, "catalog_sha256": digest(CATALOG),
              "annotations_sha256": digest(args.output/"annotations.json"),
              "ocr_inputs_sha256": digest(args.output/"baseline/ocr.json"),
              "gallery_encoding_sha256": digest(stored/"encoding.json"),
              "query_encoding_sha256": digest(query_dir/"encoding.json"),
              "branch_weights": BRANCH_WEIGHTS, "text_bonus_scale": TEXT_BONUS,
              "code_sha256": {name: digest(ROOT/"worker/pipeline"/name) for name in ("hybrid_search.py", "text_search.py", "visual_search.py")},
              "metrics": metrics, "images": rows,
              "evaluation_warning": "Same development photos inspected before improvements; not held-out accuracy."})
    (output/"provenance").mkdir(exist_ok=True)
    for name in ("hybrid_search.py", "text_search.py", "visual_search.py"):
        (output/"provenance"/name).write_bytes((ROOT/"worker/pipeline"/name).read_bytes())
    print(json.dumps(metrics, indent=2), flush=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["plan", "prepare_ocr", "ocr", "search_visual", "search_text", "evaluate", "report", "improve"])
    parser.add_argument("--input", type=Path, default=ROOT / "data/live_shop_photos")
    parser.add_argument("--output", type=Path, default=BASE)
    parser.add_argument("--model", choices=["siglip2", "dinov3_timm", "dinov3_large"], default="siglip2")
    parser.add_argument("--ocr-dir", default="ocr_fast")
    parser.add_argument("--mkldnn", action="store_true")
    parser.add_argument("--shards", type=int, default=1)
    parser.add_argument("--shard", type=int, default=0)
    args = parser.parse_args()
    if args.shards < 1 or not 0 <= args.shard < args.shards:
        parser.error("Invalid OCR shard")
    globals()[args.command](args)


if __name__ == "__main__":
    main()
