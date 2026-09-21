"""Paired SAM3 crop/deskew/homography OCR experiment; local, reproducible artifacts."""
from __future__ import annotations

import argparse
import hashlib
import html
import importlib.metadata
import json
import math
import os
from pathlib import Path
import time
import unicodedata

import cv2
import numpy as np
from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1]
STUDY = ROOT / "data/audit/sam3_gpu"
DEFAULT_OUTPUT = ROOT / "data/audit/label_rectification/pilot12_v4"
DETECTOR = "PP-OCRv5_server_det"
RECOGNIZERS = {"russian": "eslav_PP-OCRv5_mobile_rec", "latin": "latin_PP-OCRv5_mobile_rec"}
CASES = Path(__file__).with_name("label_rectification_cases.json")
GEOMETRY = {"margin_fraction": 0.04, "max_side": 1600, "minimum_angle_deg": 0.5,
            "maximum_angle_deg": 35, "max_fit_residual_fraction": 0.045,
            "min_component_fraction": 0.97, "max_local_anisotropy": 2.5,
            "local_scale_range": [0.45, 2.2], "max_perturbation_fraction": 0.035}


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path: Path) -> dict:
    return json.loads(path.read_text())


def write(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix(path.suffix + ".tmp")
    temp.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")
    temp.replace(path)


def immutable(path: Path, value: dict) -> None:
    if path.exists() and read(path) != value:
        raise ValueError(f"Configuration changed; choose a new --output: {path}")
    write(path, value)


def rgb_oriented(path: Path) -> tuple[Image.Image, dict]:
    with Image.open(path) as opened:
        info = {"raw_size": list(opened.size), "exif_orientation": opened.getexif().get(274, 1)}
        image = ImageOps.exif_transpose(opened).convert("RGBA")
    white = Image.new("RGBA", image.size, "white")
    white.alpha_composite(image)
    return white.convert("RGB"), info


class GeometryRejected(ValueError):
    """An expected geometric degeneracy, not a programming/runtime failure."""


def points_h(points: np.ndarray, matrix: np.ndarray) -> np.ndarray:
    points = np.asarray(points, dtype=np.float64).reshape(-1, 2)
    homogeneous = np.c_[points, np.ones(len(points))] @ matrix.T
    if np.any(np.abs(homogeneous[:, 2]) < 1e-8):
        raise GeometryRejected("homography_horizon")
    return homogeneous[:, :2] / homogeneous[:, 2:]


def scaled_contour(mask: np.ndarray, source_size: tuple[int, int]) -> tuple[np.ndarray, float]:
    contours, _ = cv2.findContours(mask.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    if not contours:
        raise GeometryRejected("empty_mask")
    largest = max(contours, key=cv2.contourArea)
    filled = np.zeros(mask.shape, dtype=np.uint8)
    cv2.drawContours(filled, [largest], -1, 1, -1)
    fraction = float(np.count_nonzero(filled & (mask > 0)) / np.count_nonzero(mask))
    # Pixel-centre mapping; x/y factors differ when the short dimension is rounded.
    scale = np.array(source_size, dtype=float) / np.array(mask.shape[::-1])
    contour = (largest[:, 0, :].astype(float) + 0.5) * scale - 0.5
    return contour, fraction


def robust_line(points: np.ndarray, independent: int) -> tuple[np.ndarray, float]:
    x, y = points[:, independent], points[:, 1 - independent]
    if len(x) < 10 or np.ptp(x) < 5:
        raise GeometryRejected("insufficient_edge_support")
    selected = np.ones(len(x), dtype=bool)
    for _ in range(3):
        a, b = np.polyfit(x[selected], y[selected], 1)
        residual = np.abs(y - (a * x + b))
        threshold = max(1.0, float(np.median(residual) * 2.5))
        selected = residual <= threshold
    # Residual includes the full edge support, so trimming cannot hide curvature.
    error = float(np.quantile(np.abs(y - (a * x + b)), 0.9))
    return (np.array([a, -1., b]) if independent == 0 else np.array([-1., a, b])), error


def boundary_lines(contour: np.ndarray) -> tuple[list[np.ndarray], list[float]]:
    low, high = contour.min(axis=0), contour.max(axis=0)
    size = high - low
    lines, errors = [], []
    for axis, upper in [(0, False), (1, True), (0, True), (1, False)]:
        # Outer envelope in 32 bins; omit corners, retain the middle 70% of each edge.
        samples = []
        for center in np.linspace(low[axis] + size[axis] * .15, high[axis] - size[axis] * .15, 32):
            part = contour[np.abs(contour[:, axis] - center) <= max(1.5, size[axis] / 55)]
            if len(part):
                samples.append(part[np.argmax(part[:, 1-axis]) if upper else np.argmin(part[:, 1-axis])])
        line, error = robust_line(np.array(samples), axis)
        lines.append(line)
        errors.append(error / max(1, size[1-axis]))
    return lines, errors


def quad_check(quad: np.ndarray) -> None:
    if not np.isfinite(quad).all() or not cv2.isContourConvex(quad.astype(np.float32)):
        raise GeometryRejected("nonconvex_quad")
    u, v = quad[1]-quad[0], quad[2]-quad[1]
    cross = u[0]*v[1] - u[1]*v[0]
    if cross <= 0 or cv2.contourArea(quad.astype(np.float32)) < 400:
        raise GeometryRejected("degenerate_or_reversed_quad")
    lengths = np.linalg.norm(np.roll(quad, -1, axis=0) - quad, axis=1)
    if min(lengths) < 20 or max(lengths) / min(lengths) > 10:
        raise GeometryRejected("extreme_quad_aspect")


def quad_homography(quad: np.ndarray) -> np.ndarray:
    quad_check(quad)
    edges = np.linalg.norm(np.roll(quad, -1, axis=0) - quad, axis=1)
    width, height = float((edges[0]+edges[2])/2), float((edges[1]+edges[3])/2)
    return cv2.getPerspectiveTransform(quad.astype(np.float32),
        np.array([[0, 0], [width, 0], [width, height], [0, height]], dtype=np.float32))


def validate_warp(matrix: np.ndarray, contour: np.ndarray, quad: np.ndarray) -> dict:
    minimum, maximum = contour.min(axis=0), contour.max(axis=0)
    samples = np.array([[x, y] for x in np.linspace(*[minimum[0], maximum[0]], 5)
                        for y in np.linspace(*[minimum[1], maximum[1]], 5)])
    denominators = np.c_[samples, np.ones(len(samples))] @ matrix[2]
    if np.min(denominators) * np.max(denominators) <= 0:
        raise GeometryRejected("homography_horizon")
    projected = points_h(samples, matrix)
    dx = points_h(samples + [1, 0], matrix) - projected
    dy = points_h(samples + [0, 1], matrix) - projected
    determinants = np.linalg.det(np.stack([dx, dy], axis=2))
    singular = np.linalg.svd(np.stack([dx, dy], axis=2), compute_uv=False)
    lo, hi = GEOMETRY["local_scale_range"]
    if min(determinants) <= 0 or singular.min() < lo or singular.max() > hi:
        raise GeometryRejected("excessive_or_negative_local_scale")
    anisotropy = float(max(singular[:, 0] / singular[:, 1]))
    if anisotropy > GEOMETRY["max_local_anisotropy"]:
        raise GeometryRejected("excessive_anisotropy")
    diagonal = np.linalg.norm(maximum-minimum)
    delta = min(maximum-minimum) * .005
    worst = 0.
    for i in range(4):
        for axis in range(2):
            for sign in (-1, 1):
                perturbed = quad.copy()
                perturbed[i, axis] += sign * delta
                mapped = points_h(samples, quad_homography(perturbed))
                worst = max(worst, float(np.max(np.linalg.norm(mapped-projected, axis=1))/diagonal))
    if worst > GEOMETRY["max_perturbation_fraction"]:
        raise GeometryRejected("unstable_corners")
    return {"local_scale_min": float(singular.min()), "local_scale_max": float(singular.max()),
            "max_anisotropy": anisotropy, "corner_perturbation_max_fraction": worst}


def geometry(contour: np.ndarray, fraction: float) -> dict:
    answer = {"C": {"status": "skipped"}, "D": {"status": "skipped"}}
    try:
        if fraction < GEOMETRY["min_component_fraction"]:
            raise GeometryRejected("disconnected_mask")
        if len(contour) < 30 or min(np.ptp(contour, axis=0)) < 20:
            raise GeometryRejected("small_mask")
        rect = cv2.boxPoints(cv2.minAreaRect(contour.astype(np.float32)))
        edges = np.roll(rect, -1, axis=0) - rect
        angles = (np.degrees(np.arctan2(edges[:, 1], edges[:, 0])) + 45) % 90 - 45
        angle = float(np.median(angles))
        if abs(angle) > GEOMETRY["maximum_angle_deg"]:
            raise GeometryRejected("ambiguous_orientation")
        initial = np.vstack([cv2.getRotationMatrix2D(tuple(contour.mean(axis=0)), angle, 1), [0, 0, 1]])
        aligned = points_h(contour, initial)
        lines, errors = boundary_lines(aligned)
        # Top/bottom robust lines refine the minimum rectangle's initial angle.
        slopes = [math.degrees(math.atan(lines[i][0])) for i in (0, 2)]
        if abs(slopes[0] - slopes[1]) > 12:
            raise GeometryRejected("inconsistent_top_bottom_directions")
        angle += sum(slopes)/2
        answer["C"]["angle_deg"] = angle
        if abs(angle) <= GEOMETRY["maximum_angle_deg"] and abs(angle) >= GEOMETRY["minimum_angle_deg"]:
            matrix = np.vstack([cv2.getRotationMatrix2D(tuple(contour.mean(axis=0)), angle, 1), [0, 0, 1]])
            answer["C"].update(status="applied", matrix=matrix.tolist(), reason="boundary_deskew")
        else:
            answer["C"]["reason"] = "near_upright" if abs(angle) < .5 else "ambiguous_orientation"
        answer["D"]["edge_residual_fractions"] = errors
        if max(errors) > GEOMETRY["max_fit_residual_fraction"]:
            raise GeometryRejected("curved_or_irregular_boundary")
        corners = []
        for a, b in [(3, 0), (0, 1), (1, 2), (2, 3)]:
            intersection = np.cross(lines[a], lines[b])
            if abs(intersection[2]) < 1e-8:
                raise GeometryRejected("parallel_adjacent_edges")
            corners.append(intersection[:2] / intersection[2])
        quad = points_h(np.array(corners), np.linalg.inv(initial))
        matrix = quad_homography(quad)
        checks = validate_warp(matrix, contour, quad)
        answer["D"].update(status="applied", reason="four_fitted_boundary_lines", matrix=matrix.tolist(),
                           quad_original=quad.tolist(), checks=checks)
    except GeometryRejected as exc:
        for value in answer.values():
            if value["status"] == "skipped" and "reason" not in value:
                value["reason"] = str(exc)
    return answer


def warp_crop(image: np.ndarray, crop: list[int], matrix: np.ndarray, max_side: int) -> tuple[np.ndarray, np.ndarray]:
    x0, y0, x1, y1 = crop
    # Preserve the entire RGB crop, including margin; do not clip to the fitted quad/mask.
    corners = np.array([[x0, y0], [x1-1, y0], [x1-1, y1-1], [x0, y1-1]], dtype=float)
    den = np.c_[corners, np.ones(4)] @ matrix[2]
    if min(den) * max(den) <= 0:
        raise GeometryRejected("crop_crosses_horizon")
    mapped = points_h(corners, matrix)
    lower, upper = np.floor(mapped.min(axis=0)), np.ceil(mapped.max(axis=0))
    size = upper - lower + 1
    factor = min(1., max_side / max(size))
    shift = np.array([[1, 0, -lower[0]], [0, 1, -lower[1]], [0, 0, 1]], dtype=float)
    scale = np.diag([factor, factor, 1])
    transform = scale @ shift @ matrix
    # Avoid 1600.0000000000002 rounding up to 1601.
    output_size = tuple(np.minimum(max_side, np.ceil(size * factor - 1e-9)).astype(int))
    # One interpolation, directly from original pixels.
    crop_to_original = np.array([[1., 0, x0], [0, 1., y0], [0, 0, 1.]])
    result = cv2.warpPerspective(image[y0:y1, x0:x1], transform @ crop_to_original, output_size, flags=cv2.INTER_LINEAR,
                                 borderMode=cv2.BORDER_CONSTANT, borderValue=(255, 255, 255))
    return result, transform


def prepare(args: argparse.Namespace) -> None:
    if (args.output / "manifest.json").exists():
        raise ValueError("Prepared run exists: use run/report, or a new --output")
    entries = {e["id"]: e for e in read(STUDY / "manifest.json")["entries"]}
    cases = read(CASES)
    prepared = []
    for case in cases["cases"]:
        identifier = case["id"]
        entry = entries[identifier]
        source = Path(entry["path"])
        result_path = STUDY / "bf16_v1/results" / f"{identifier}__label.json"
        sam = read(result_path)
        mask_path = STUDY / "bf16_v1" / sam["mask_path"]
        if digest(source) != sam["source_sha256"] or digest(mask_path) != sam["mask_sha256"]:
            raise ValueError("Source or mask changed")
        image, exif = rgb_oriented(source)
        if list(image.size) != sam["source_size"]:
            raise ValueError("EXIF/source coordinate mismatch")
        with Image.open(mask_path) as opened:
            mask = np.array(opened.convert("L")) > 0
        if list(mask.shape[::-1]) != sam["mask_size"]:
            raise ValueError("Mask metadata mismatch")
        array = np.array(image)
        start = time.perf_counter()
        contour, fraction = scaled_contour(mask, image.size)
        lower, upper = contour.min(axis=0), contour.max(axis=0)
        margin = max(8., float(min(upper-lower) * GEOMETRY["margin_fraction"]))
        crop = [int(max(0, np.floor(lower[0]-margin))), int(max(0, np.floor(lower[1]-margin))),
                int(min(image.width, np.ceil(upper[0]+margin+1))), int(min(image.height, np.ceil(upper[1]+margin+1)))]
        decisions = geometry(contour, fraction)
        geometry_ms = (time.perf_counter()-start)*1000
        fields = []
        for text, box in case["fields"]:
            a = lower + np.array(box[:2])*(upper-lower)
            b = lower + np.array(box[2:])*(upper-lower)
            cyrillic = any('а' <= c.lower() <= 'я' or c.lower() == 'ё' for c in text)
            latin = any('a' <= c.lower() <= 'z' for c in text)
            language = "russian" if cyrillic or (not latin and (entry['group']=='eval' or identifier=='cat_beloe-polusladkoe')) else "latin"
            fields.append({"text": text, "language": language, "box_original_xyxy": [*a.tolist(), *b.tolist()]})
        record = {"id": identifier, "group": entry["group"], "source_path": str(source.relative_to(ROOT)),
                  "source_sha256": digest(source), "sam_result_path": str(result_path.relative_to(ROOT)),
                  "sam_result_sha256": digest(result_path), "sam_config_sha256": sam["config_sha256"],
                  "mask_path": str(mask_path.relative_to(ROOT)), "mask_sha256": digest(mask_path),
                  "source_size": list(image.size), "mask_size": sam["mask_size"], "coordinate_system": "exif_oriented_original_pixels",
                  **exif, "crop_box": crop, "mask_box": [*lower.tolist(), *upper.tolist()], "component_fraction": fraction,
                  "note": case["note"], "localization_failure": case.get("localization_failure", False),
                  "fields": fields, "geometry_ms": geometry_ms, "variants": []}
        for name in ("B", "C", "D"):
            decision = {"status": "baseline", "reason": "rgb_crop"} if name == 'B' else decisions[name]
            matrix = np.array(decision.get("matrix", np.eye(3)))
            start = time.perf_counter()
            output, transform = warp_crop(array, crop, matrix, GEOMETRY["max_side"])
            render_ms = (time.perf_counter()-start)*1000
            path = args.output / "images" / f"{identifier}_{name}.png"
            path.parent.mkdir(parents=True, exist_ok=True)
            Image.fromarray(output).save(path)
            record["variants"].append({"name": name, **decision, "path": str(path.relative_to(args.output)),
                "sha256": digest(path), "size": list(output.shape[1::-1]), "render_ms": render_ms,
                "original_to_input": transform.tolist(), "input_to_original": np.linalg.inv(transform).tolist()})
        preview = image.copy()
        preview.thumbnail((1000, 1000))
        preview_path = args.output / "images" / f"{identifier}_source.jpg"
        preview.save(preview_path, quality=90)
        record["source_preview"] = str(preview_path.relative_to(args.output))
        prepared.append(record)
        print(identifier, {k: (v['status'], v.get('reason'), round(v.get('angle_deg', 0), 2)) for k,v in decisions.items()}, flush=True)
    manifest = {"schema_version": 1, "geometry_config": GEOMETRY, "annotation_method": cases['annotation_method'],
                "cases_sha256": digest(CASES), "prepare_script_sha256": digest(Path(__file__)),
                "versions": {name: importlib.metadata.version(name) for name in ['numpy', 'Pillow', 'opencv-python-headless']},
                "images": prepared}
    write(args.output / "manifest.json", manifest)
    (args.output / "provenance").mkdir(exist_ok=True)
    (args.output / "provenance/prepare_script.py").write_bytes(Path(__file__).read_bytes())
    (args.output / "provenance/cases.json").write_bytes(CASES.read_bytes())
    report(args)


def run(args: argparse.Namespace) -> None:
    manifest_path = args.output / "manifest.json"
    manifest = read(manifest_path)
    cache = ROOT / "weights/cache/ocr"
    for name, value in {"PADDLE_PDX_CACHE_HOME": cache/'paddlex', 'PADDLE_HOME':cache/'paddle',
                        'HF_HOME':cache/'huggingface', 'XDG_CACHE_HOME':cache/'xdg'}.items():
        os.environ.setdefault(name, str(value))
    os.environ.setdefault('PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK', 'True')
    models = cache / "paddlex/official_models"
    for entry in manifest['images']:
        for key in ['source', 'mask', 'sam_result']:
            if digest(ROOT / entry[key+'_path']) != entry[key+'_sha256']:
                raise ValueError(f"Changed {key}: {entry['id']}")
        for item in entry['variants']:
            if digest(args.output / item['path']) != item['sha256']:
                raise ValueError('Changed prepared input')
    weights = {}
    for model in [DETECTOR, *RECOGNIZERS.values()]:
        files = {str(p.relative_to(models/model)):digest(p) for p in sorted((models/model).rglob('*')) if p.is_file()}
        if not files:
            raise ValueError(f'Missing model {model}')
        weights[model] = files
    config = {'manifest_sha256':digest(manifest_path), 'models_sha256': weights, 'detector':DETECTOR,
        'recognizers':RECOGNIZERS, 'device':'cpu', 'cpu_threads':4, 'enable_mkldnn':False,
        'doc_unwarping':False, 'doc_orientation':False, 'textline_orientation':False,
        'max_side':GEOMETRY['max_side'], 'color_order':'BGR', 'recognition_threshold':0.,
        'versions':{n:importlib.metadata.version(n) for n in ['paddleocr','paddlepaddle','paddlex','numpy','Pillow']}}
    config_path = args.output / 'ocr_config.json'
    immutable(config_path, config)
    from paddleocr import PaddleOCR
    for language, model in RECOGNIZERS.items():
        all_items = [(e,v,args.output/'results'/f"{e['id']}_{v['name']}_{language}.json")
                     for e in manifest['images'] for v in e['variants']]
        cached = {}
        pending = []
        for entry, item, path in all_items:
            if path.exists():
                value = read(path)
                if value['config_sha256'] != digest(config_path) or value['input_sha256'] != item['sha256']:
                    raise ValueError('Stale OCR cache')
                cached[item['sha256']] = value
            else:
                pending.append((entry,item,path))
        if not pending:
            continue
        started = time.perf_counter()
        engine = PaddleOCR(text_detection_model_name=DETECTOR, text_detection_model_dir=str(models/DETECTOR),
            text_recognition_model_name=model, text_recognition_model_dir=str(models/model),
            use_doc_orientation_classify=False, use_doc_unwarping=False, use_textline_orientation=False,
            text_rec_score_thresh=0., text_det_limit_side_len=GEOMETRY['max_side'], text_det_limit_type='max',
            device='cpu', cpu_threads=4, enable_mkldnn=False)
        initialization = time.perf_counter()-started
        # One unscored warmup for each recognizer, recorded separately.
        sample = np.array(Image.open(args.output/pending[0][1]['path']).convert('RGB'))[:,:,::-1].copy()
        started = time.perf_counter()
        list(engine.predict(sample))
        warmup = time.perf_counter()-started
        write(args.output/'timing'/f'{language}.json', {'model_initialization_seconds':initialization,'warmup_seconds':warmup})
        for entry, item, path in pending:
            previous = cached.get(item['sha256'])
            if previous:
                raw, duration, reused = previous['raw'], None, previous['image_id']+'_'+previous['variant']
            else:
                with Image.open(args.output/item['path']) as opened:
                    pixels = np.array(opened.convert('RGB'))[:,:,::-1].copy()
                started = time.perf_counter()
                predictions = list(engine.predict(pixels))
                duration, reused = time.perf_counter()-started, None
                if len(predictions) != 1:
                    raise ValueError('Expected one OCR result')
                raw = predictions[0].json
                raw = json.loads(raw) if isinstance(raw,str) else raw
            data = raw.get('res',raw)
            if not len(data['rec_texts']) == len(data['rec_scores']) == len(data['rec_polys']):
                raise ValueError('Inconsistent OCR arrays')
            lines = []
            for text, score, polygon in zip(data['rec_texts'],data['rec_scores'],data['rec_polys']):
                original = points_h(polygon,np.array(item['input_to_original']))
                lines.append({'text':text,'confidence':float(score),'polygon_input':polygon,'polygon_original':original.tolist()})
            value = {'image_id':entry['id'],'variant':item['name'],'language':language,'config_sha256':digest(config_path),
                     'input_sha256':item['sha256'],'inference_seconds':duration,'reused_identical_input':reused,'lines':lines,'raw':raw}
            write(path,value)
            cached[item['sha256']] = value
            print(f"{entry['id']} {item['name']} {language}: {len(lines)} lines, {duration if duration is not None else 'cached'} s",flush=True)
        del engine
    report(args)


def normalized(text: str) -> str:
    return ''.join(c for c in unicodedata.normalize('NFC',text).casefold().replace('ё','е') if c.isalnum())


def edit_distance(a: str, b: str) -> int:
    row = list(range(len(b)+1))
    for i, char in enumerate(a,1):
        new = [i]
        for j, other in enumerate(b,1):
            new.append(min(new[-1]+1,row[j]+1,row[j-1]+(char!=other)))
        row = new
    return row[-1]


def field_prediction(field: dict, lines: list[dict]) -> str:
    x0,y0,x1,y1 = field['box_original_xyxy']
    selected = []
    for line in lines:
        x,y = np.mean(line['polygon_original'],axis=0)
        if x0 <= x <= x1 and y0 <= y <= y1:
            selected.append(line['text'])
    # Keep OCR's reading order; this order is part of recognition quality.
    return ' '.join(selected)


def svg(path: str, size: list[int], polygons: list[tuple[list, str, str]]) -> str:
    shapes = ''.join('<polygon points="'+ ' '.join(f'{x:.2f},{y:.2f}' for x,y in points)+'" fill="none" stroke="'+color+'" stroke-width="2" vector-effect="non-scaling-stroke"><title>'+html.escape(text)+'</title></polygon>' for points,text,color in polygons)
    return f'<svg viewBox="0 0 {size[0]} {size[1]}"><image href="{html.escape(path)}" width="{size[0]}" height="{size[1]}"/>{shapes}</svg>'


def report(args: argparse.Namespace) -> None:
    manifest = read(args.output/'manifest.json')
    annotation_path = args.output / 'evaluation_annotations.json'
    annotation_audit = None
    if annotation_path.exists():
        audit = read(annotation_path)
        if audit['manifest_sha256'] != digest(args.output/'manifest.json'):
            raise ValueError('Annotation audit belongs to another experiment')
        for entry in manifest['images']:
            if entry['id'] in audit['corrected_fields']:
                entry['fields'] = audit['corrected_fields'][entry['id']]
        annotation_audit = {'sha256': digest(annotation_path), 'notes': audit['notes']}
    sections, rows, times = [], [], []
    result_count = 0
    for entry in manifest['images']:
        cards = []
        for item in entry['variants']:
            results = {}
            for language in RECOGNIZERS:
                path = args.output/'results'/f"{entry['id']}_{item['name']}_{language}.json"
                if path.exists():
                    results[language] = read(path)
                    result_count += 1
                    if results[language]['inference_seconds'] is not None:
                        times.append({'language':language,'variant':item['name'],'seconds':results[language]['inference_seconds']})
            polygons = []
            source_polygons = []
            for field in entry['fields']:
                x0,y0,x1,y1 = field['box_original_xyxy']
                original = np.array([[x0,y0],[x1,y0],[x1,y1],[x0,y1]])
                polygons.append((points_h(original,np.array(item['original_to_input'])).tolist(),field['text'],'#168640'))
                source_polygons.append((original.tolist(),field['text'],'#168640'))
            field_rows = []
            for index,field in enumerate(entry['fields']):
                if field['language'] not in results:
                    continue
                prediction = field_prediction(field,results[field['language']]['lines'])
                truth, guessed = normalized(field['text']), normalized(prediction)
                row = {'id':entry['id'],'group':entry['group'],'variant':item['name'],'field':index,
                       'truth':field['text'],'prediction':prediction,'language':field['language'],
                       'edits':edit_distance(truth,guessed),'characters':len(truth),'exact':truth==guessed}
                rows.append(row)
                field_rows.append(f'<tr class="{"good" if row["exact"] else "bad"}"><td>{html.escape(field["text"])}</td><td>{html.escape(prediction) or "∅"}</td></tr>')
            details = []
            for language,result in results.items():
                ocr_shapes = [(line['polygon_input'],line['text'],'#346fe1') for line in result['lines']]
                orig_shapes = [(line['polygon_original'],line['text'],'#346fe1') for line in result['lines']]
                words = '<br>'.join(html.escape(line['text']) for line in result['lines'])
                details.append(f'<details><summary>Все строки OCR: {language}</summary>{svg(item["path"],item["size"],ocr_shapes)}<p>{words}</p><details><summary>Области OCR на исходном кадре</summary>{svg(entry["source_preview"],entry["source_size"],orig_shapes)}</details></details>')
            quad = item.get('quad_original')
            geo = svg(entry['source_preview'],entry['source_size'],[(quad,'Опорные точки','#e56112')]) if quad else ''
            cards.append(f'<article><h3>{item["name"]}: {item["status"]}</h3><p>{html.escape(item["reason"])} · {item["size"][0]} × {item["size"][1]}</p>{svg(item["path"],item["size"],polygons)}<table><tr><th>Видимый эталон</th><th>OCR</th></tr>{"".join(field_rows)}</table>{"".join(details)}<details><summary>Геометрия</summary><pre>{html.escape(json.dumps({k:v for k,v in item.items() if k not in ["path","sha256"]},ensure_ascii=False,indent=2))}</pre>{geo}</details></article>')
        sections.append(f'<section id="{entry["id"]}"><h2>{entry["id"]}</h2><p>{html.escape(entry["note"])}</p><details><summary>Исходный кадр и контрольные текстовые области</summary>{svg(entry["source_preview"],entry["source_size"],source_polygons)}</details><div class="grid">{"".join(cards)}</div></section>')
    totals = {}
    for group in ['all','eval','catalog','grain_labels']:
        totals[group] = {}
        for variant in 'BCD':
            part = [r for r in rows if r['variant']==variant and (group=='all' or r['group']==group)]
            chars = sum(r['characters'] for r in part)
            totals[group][variant] = {'fields':len(part),'exact':sum(r['exact'] for r in part),
                'edits':sum(r['edits'] for r in part),'characters':chars,
                'cer':sum(r['edits'] for r in part)/chars if chars else None}
    paired = {}
    baseline = {(r['id'],r['field']):r for r in rows if r['variant']=='B'}
    for variant in 'CD':
        matches = [(r,baseline[(r['id'],r['field'])]) for r in rows if r['variant']==variant and (r['id'],r['field']) in baseline]
        paired[variant] = {'better_fields':sum(a['edits']<b['edits'] for a,b in matches),
            'worse_fields':sum(a['edits']>b['edits'] for a,b in matches),'equal_fields':sum(a['edits']==b['edits'] for a,b in matches)}
    summary = {'complete':result_count == len(manifest['images'])*3*len(RECOGNIZERS),
        'result_count': result_count, 'expected_result_count': len(manifest['images'])*3*len(RECOGNIZERS),
        'annotation_audit': annotation_audit,
        'method':'CER in manually annotated key-text ROIs, including any documented annotation audit; fixed recognizer per field, missing text counts as deletions; punctuation/spaces ignored, accents retained. Not full-label CER or catalogue accuracy.',
        'report_script_sha256': digest(Path(__file__)),
        'manifest_sha256':digest(args.output/'manifest.json'), 'totals':totals,'paired_vs_B':paired,'fields':rows,
        'applied':{v:sum(item['status']=='applied' for e in manifest['images'] for item in e['variants'] if item['name']==v) for v in 'CD'},
        'timing_seconds':{lang:{'p50':float(np.percentile([t['seconds'] for t in times if t['language']==lang],50)),
            'p95':float(np.percentile([t['seconds'] for t in times if t['language']==lang],95)),
            'measured_inputs':sum(t['language']==lang for t in times)} for lang in RECOGNIZERS if any(t['language']==lang for t in times)},
        'geometry_ms_p50':float(np.median([e['geometry_ms'] for e in manifest['images']]))}
    write(args.output/'metrics.json',summary)
    (args.output/'provenance').mkdir(exist_ok=True)
    (args.output/'provenance/report_script.py').write_bytes(Path(__file__).read_bytes())
    summary_rows = []
    for variant, label in [('B', 'Кроп'), ('C', 'Поворот'), ('D', 'Перспектива')]:
        stats = totals['all'][variant]
        cer = f"{stats['cer']:.1%}" if stats['cer'] is not None else '—'
        summary_rows.append(f'<tr><td>{variant}: {label}</td><td>{stats["exact"]} / {stats["fields"]}</td><td>{cer}</td></tr>')
    status = 'Прогон завершён' if summary['complete'] else f'Прогон продолжается: {result_count} / {summary["expected_result_count"]}. Частичные метрики пока несопоставимы.'
    overview = f'<h2>{status}</h2><table><tr><th>Вариант</th><th>Надпись прочитана целиком</th><th>Ошибки символов, CER ↓</th></tr>{"".join(summary_rows)}</table>'
    stat = html.escape(json.dumps({k:summary[k] for k in ['complete','totals','paired_vs_B','applied','timing_seconds','geometry_ms_p50']},ensure_ascii=False,indent=2))
    page = '''<!doctype html><html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Ректификация: 12 примеров</title><style>body{font:16px system-ui;margin:24px;color:#213047}section{margin-top:40px;border-top:2px solid #b9cadb;padding-top:20px}h2{overflow-wrap:anywhere;font-size:21px}.grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:18px}article{min-width:0;padding:12px;background:#f6f8fa;border-radius:8px}svg{display:block;width:100%;max-height:640px}table{width:100%;border-collapse:collapse;font-size:14px}td,th{padding:8px;border-bottom:1px solid #ccd;overflow-wrap:anywhere;text-align:left}.bad{background:#ffebe6}.good{background:#e5f6e9}details{margin:14px 0}summary{cursor:pointer;font-weight:600}pre{white-space:pre-wrap;overflow-wrap:anywhere;font-size:12px}@media(max-width:850px){.grid{grid-template-columns:1fr}}</style><h1>Ректификация этикеток: первый диагностический эксперимент</h1><p>B — RGB-кроп; C — поворот; D — гомография по четырём пересечениям границ маски SAM 3. Skipped означает точную копию B. Зелёные рамки — заранее размеченные области ключевого текста; синие в раскрываемых блоках — результат OCR. Все координаты возвращаются в исходный кадр.</p><p>12 кадров: 3 eval, 5 каталог, 4 GRAIN. В двух случаях маска неполная: они показаны, но исключены из CER. Это отладочная выборка, не независимый тест. CER измеряется только в ключевых текстовых областях; язык распознавания фиксирован заранее. Для каждого поля видны и эталон, и полный OCR-ответ. Каталожный поиск и исправление цилиндрического изгиба здесь не проверяются.</p>'''
    audit_html = ('<p>После проверки наложенных областей исправлены две детали разметки: рамка производителя «Массандры» исключает отдельное слово «КРЫМ», а эталон Campo Viejo включает видимое TM. Правки одинаковы для B/C/D; исходная разметка сохранена. <a href="evaluation_annotations.json">История исправлений</a>.</p>' if annotation_audit else '')
    page += overview + audit_html + f'<details><summary>Метрики и время CPU (без загрузки и прогрева)</summary><pre>{stat}</pre></details>' + ''.join(sections) + '</html>'
    (args.output/'review.html').write_text(page)
    print('Report:',args.output/'review.html',flush=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command',choices=['prepare','run','report'])
    parser.add_argument('--output',type=Path,default=DEFAULT_OUTPUT)
    args = parser.parse_args()
    args.output = args.output.resolve()
    cv2.setNumThreads(1)
    {'prepare':prepare,'run':run,'report':report}[args.command](args)


if __name__ == '__main__':
    main()
