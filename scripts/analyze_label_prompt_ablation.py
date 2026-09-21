#!/usr/bin/env python3
"""Compare detector prompts after inference against existing manual regions.

This diagnostic never feeds reference regions into a detector or target selector.
An oversized region is a geometric proxy, not a verified bottle annotation.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from detect_labels_pilot import box_iou, json_digest, reference_box, result_path, sha256, validate_result, write_json

ROOT = Path(__file__).resolve().parents[1]


def area(box: list[float]) -> float:
    return max(0, box[2] - box[0]) * max(0, box[3] - box[1])


def coverage(box: list[float], target: list[float]) -> float:
    intersection = max(0, min(box[2], target[2]) - max(box[0], target[0])) * max(
        0, min(box[3], target[3]) - max(box[1], target[1]))
    return intersection / area(target)


def diagnostic(candidate: dict, annotation: dict) -> dict:
    box = candidate["box_xyxy"]
    target = reference_box(annotation)
    target_iou = box_iou(box, target)
    target_coverage = coverage(box, target)
    relative_area = area(box) / area(target)
    neighbor_ious = [box_iou(box, reference_box({"target_polygon": polygon}))
                     for polygon in annotation["neighbor_polygons"]]
    oversized = target_coverage >= 0.9 and relative_area >= 1.7
    if oversized:
        category = "oversized_target_region"
    elif target_iou >= 0.5:
        category = "target_label_bbox_match"
    elif max(neighbor_ious, default=0.0) >= 0.5:
        category = "neighbor_label_bbox_match"
    else:
        category = "other_region"
    return {**candidate, "diagnostic_category": category, "target_bbox_iou": target_iou,
            "target_bbox_coverage": target_coverage, "area_relative_to_target_bbox": relative_area,
            "neighbor_bbox_ious": neighbor_ious}


def run(output: Path, annotations_path: Path) -> None:
    config = json.loads((output / "run_config.json").read_text())
    annotations = {item["id"]: item for item in json.loads(annotations_path.read_text())["images"]}
    comparisons = []
    for source in config["inputs"]:
        annotation = annotations[source["id"]]
        if source["sha256"] != annotation["source_sha256"]:
            raise ValueError(f"Source/reference mismatch: {source['id']}")
        for prompt in config["prompts"]:
            path = result_path(output, source, prompt)
            result = json.loads(path.read_text())
            validate_result(result, json_digest(config), source, prompt)
            candidates = [diagnostic(candidate, annotation) for candidate in result["candidates_after_nms"]]
            raw = [diagnostic(candidate, annotation) for candidate in result["raw_post_threshold_detections"]]
            top = max(candidates, key=lambda candidate: candidate["score"], default=None)
            target_matches = [candidate for candidate in candidates
                              if candidate["diagnostic_category"] == "target_label_bbox_match"]
            oversized = [candidate for candidate in candidates
                         if candidate["diagnostic_category"] == "oversized_target_region"]
            selected_index = result["selection"]["selected_index"]
            selected = candidates[selected_index] if selected_index is not None else None
            comparisons.append({"source_id": source["id"], "prompt": prompt,
                                "raw_count": len(raw), "nms_count": len(candidates),
                                "top_score_box": top, "selected_box": selected,
                                "selection_status": result["selection"]["status"],
                                "best_target_label_score": max((item["score"] for item in target_matches), default=None),
                                "best_oversized_target_score": max((item["score"] for item in oversized), default=None),
                                "oversized_target_count": len(oversized),
                                "elapsed_seconds": result["inference_seconds_including_prepost"],
                                "all_raw_post_threshold_detections": raw, "all_candidates_after_nms": candidates,
                                "result_path": str(path.relative_to(output))})
    aggregates = []
    for prompt in config["prompts"]:
        group = [item for item in comparisons if item["prompt"] == prompt]
        aggregates.append({"prompt": prompt, "image_count": len(group),
                           "target_label_found": sum(item["best_target_label_score"] is not None for item in group),
                           "top_score_is_target_label": sum(bool(item["top_score_box"] and item["top_score_box"]["diagnostic_category"] == "target_label_bbox_match") for item in group),
                           "selected_is_target_label": sum(bool(item["selected_box"] and item["selected_box"]["diagnostic_category"] == "target_label_bbox_match") for item in group),
                           "has_target_proposal_iou_at_least_075": sum(any(candidate["target_bbox_iou"] >= 0.75 for candidate in item["all_candidates_after_nms"]) for item in group),
                           "top_score_target_iou_at_least_075": sum(bool(item["top_score_box"] and item["top_score_box"]["target_bbox_iou"] >= 0.75) for item in group),
                           "selected_target_iou_at_least_075": sum(bool(item["selected_box"] and item["selected_box"]["target_bbox_iou"] >= 0.75) for item in group),
                           "images_with_oversized_region": sum(item["oversized_target_count"] > 0 for item in group),
                           "candidate_count": sum(item["nms_count"] for item in group),
                           "mean_seconds": sum(item["elapsed_seconds"] for item in group) / len(group)})
    analysis = {"config_sha256": json_digest(config), "annotations_sha256": sha256(annotations_path),
                "reference_usage": "post-inference diagnostics only",
                "definitions": {"target_label_bbox_match": "IoU >= 0.5 with manually drawn target bounding box; not oversized",
                                "oversized_target_region": "Covers >= 90% of target bbox and area >= 1.7x target bbox; geometric proxy, not a bottle annotation",
                                "neighbor_label_bbox_match": "IoU >= 0.5 with a manual neighbor bbox, after target tests",
                                "scores": "Raw prompt-specific model scores, not calibrated probabilities or globally comparable likelihoods"},
                "aggregates": aggregates, "comparisons": comparisons}
    write_json(output / "prompt_analysis.json", analysis)
    lines = ["# Сравнение запросов Grounding DINO", "",
             "Три предоставленных снимка; пороги 0.25 / 0.2, NMS 0.5 и выбор цели не менялись. Ручные области использованы только после детекции.", "",
             "`Целевая область` ниже — IoU прямоугольников ≥ 0.5. `Увеличенная область` покрывает ≥ 90% цели и занимает ≥ 1.7 её площади; это геометрический признак, не разметка целой бутылки.", "",
             "**Важное ограничение:** `sticker.` на Табии выделяет лишь нижний рисунок этикетки с IoU 0.525. По слабому порогу 0.5 это формально совпадение, но для OCR потеряны название и сорт. Поэтому отдельно приводим более строгую геометрическую проверку IoU ≥ 0.75.", "",
             "| Запрос | Цель найдена | Самый высокий score — цель | Выбор эвристики — цель | Снимки с увеличенными областями | Всего областей после NMS |", "|---|---:|---:|---:|---:|---:|"]
    for item in aggregates:
        lines.append(f"| `{item['prompt']}` | {item['target_label_found']}/3 | {item['top_score_is_target_label']}/3 | {item['selected_is_target_label']}/3 | {item['images_with_oversized_region']}/3 | {item['candidate_count']} |")
    lines += ["", "## Более строгая локализация: IoU ≥ 0.75", "",
              "Пороги 0.5 и 0.75 используются только для сравнения результатов, не меняют детектор или эвристику выбора.", "",
              "| Запрос | Есть подходящая область | Самая высокая score | Выбор эвристики |", "|---|---:|---:|---:|"]
    for item in aggregates:
        lines.append(f"| `{item['prompt']}` | {item['has_target_proposal_iou_at_least_075']}/3 | {item['top_score_target_iou_at_least_075']}/3 | {item['selected_target_iou_at_least_075']}/3 |")
    lines += ["", "## По каждому снимку", "",
              "Score показывает выход модели для данного запроса. Изменение score между запросами не является измерением вероятности или качества.", "",
              "| Снимок | Запрос | Raw/NMS | Score цели | Score увеличенной области | IoU области с max score | IoU выбора |", "|---|---|---:|---:|---:|---:|---:|"]
    def number(value: float | None) -> str:
        return "—" if value is None else f"{value:.3f}"
    for item in comparisons:
        top_iou = item["top_score_box"]["target_bbox_iou"] if item["top_score_box"] else None
        selected_iou = item["selected_box"]["target_bbox_iou"] if item["selected_box"] else None
        lines.append(f"| {item['source_id']} | `{item['prompt']}` | {item['raw_count']}/{item['nms_count']} | {number(item['best_target_label_score'])} | {number(item['best_oversized_target_score'])} | {number(top_iou)} | {number(selected_iou)} |")
    lines += ["", "Все прямоугольники, тексты, scores и диагностические величины сохранены в `prompt_analysis.json`. Исходные результаты — в `results/`, визуальный отчёт — `review.html`.", "",
              "Это абляция формулировок на трёх известных примерах, не валидация и не основание для выбора универсального порога. Отрицательные снимки не проверялись."]
    (output / "prompt_analysis.md").write_text("\n".join(lines) + "\n")
    print(json.dumps(aggregates, ensure_ascii=False, indent=2))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "data/audit/label_detection/prompt_ablation")
    parser.add_argument("--annotations", type=Path, default=ROOT / "data/audit/label_geometry/annotations.json")
    args = parser.parse_args()
    run(args.output, args.annotations)


if __name__ == "__main__":
    main()
