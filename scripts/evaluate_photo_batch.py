"""Evaluate frozen photo predictions against explicit human annotations."""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STATUSES = {"matched", "not_in_catalog", "uncertain", "not_reviewed"}


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def rate(count: int, denominator: int) -> dict:
    return {"count": count, "denominator": denominator,
            "percent": round(100 * count / denominator, 4) if denominator else None}


def evaluate(batch: Path, annotations_path: Path) -> tuple[dict, list[dict]]:
    manifest = read_json(batch / "manifest.json")
    provenance = read_json(batch / "review.manifest.json")
    annotations = read_json(annotations_path)
    catalog_hash = digest(batch / "catalog.jsonl")
    require(catalog_hash == manifest["catalog_sha256"] == annotations["catalog_sha256"]
            == provenance["catalog_sha256"], "Catalogue checksums differ")
    catalog = [json.loads(line) for line in (batch / "catalog.jsonl").read_text().splitlines() if line.strip()]
    known = {item["slug"] for item in catalog}
    require(len(known) == len(catalog) == manifest["catalog_count"], "Invalid catalogue size/slugs")
    expected = {item["image_id"]: item for item in manifest["images"]}
    labels = {item["image_id"]: item for item in annotations["images"]}
    require(len(expected) == len(manifest["images"]), "Duplicate manifest image IDs")
    require(len(labels) == len(annotations["images"]), "Duplicate annotation image IDs")
    require(expected.keys() == labels.keys(), "Annotations must cover exactly the frozen batch")
    require(set(provenance["predictions_sha256"]) == {key + ".json" for key in expected},
            "Prediction checksum list differs from batch")
    results = []
    for image_id, item in expected.items():
        label = labels[image_id]
        require(all(label[key] == item[key] for key in ("filename", "sha256")),
                f"Annotation identity mismatch: {image_id}")
        require(digest(ROOT / manifest["input_dir"] / item["filename"]) == item["sha256"],
                f"Source photograph changed: {image_id}")
        status, slug = label["status"], label["expected_slug"]
        alternatives = label["acceptable_slugs"]
        require(status in STATUSES, f"Invalid annotation status: {image_id}")
        require(isinstance(alternatives, list) and all(s in known for s in alternatives),
                f"Invalid acceptable slugs: {image_id}")
        require(slug in known if status == "matched" else not slug and not alternatives,
                f"Status/slug conflict: {image_id}")
        path = batch / "predictions" / (image_id + ".json")
        require(digest(path) == provenance["predictions_sha256"][path.name],
                f"Prediction changed since review: {image_id}")
        record = read_json(path)
        require(record["image_id"] == image_id and record["sha256"] == item["sha256"],
                f"Prediction identity mismatch: {image_id}")
        prediction = record["result"]
        require(prediction["status"] in {"done", "failed"}, f"Pending prediction: {image_id}")
        candidates = prediction.get("candidates", []) if prediction["status"] == "done" else []
        if prediction["status"] == "done":
            require(prediction["catalogSha256"] == catalog_hash, f"Prediction catalogue mismatch: {image_id}")
        slugs = [candidate["slug"] for candidate in candidates[:5]]
        require(len(slugs) == len(set(slugs)) and all(s in known for s in slugs),
                f"Invalid predicted slugs: {image_id}")
        require(all(candidate["rank"] == i for i, candidate in enumerate(candidates[:5], 1)),
                f"Invalid candidate ordering: {image_id}")
        require(not slugs or prediction["slug"] == slugs[0], f"Top-1 disagreement: {image_id}")
        accepted = {slug, *alternatives} if status == "matched" else set()
        results.append({**label, "prediction_status": prediction["status"],
                        "predicted_slug": prediction.get("slug"), "top5_slugs": slugs,
                        "strict_rank": slugs.index(slug) + 1 if slug in slugs else None,
                        "accepted_rank": next((i for i, s in enumerate(slugs, 1) if s in accepted), None)})
    counts = Counter(row["status"] for row in results)
    matched = [row for row in results if row["status"] == "matched"]
    scores = {}
    for mode in ("strict", "accepted"):
        for k in (1, 5):
            scores[f"{mode}_top{k}"] = rate(sum(row[f"{mode}_rank"] is not None
                                                    and row[f"{mode}_rank"] <= k for row in matched), len(matched))
    summary = {
        "schema_version": 1,
        "annotations_sha256": digest(annotations_path), "catalog_sha256": catalog_hash,
        "batch_manifest_sha256": digest(batch / "manifest.json"),
        "review_manifest_sha256": digest(batch / "review.manifest.json"),
        "evaluator_sha256": digest(Path(__file__)),
        "photos": len(results),
        "status_counts": {status: rate(counts[status], len(results)) for status in sorted(STATUSES)},
        "unique_expected_slugs": len({row["expected_slug"] for row in matched}),
        "matched_with_acceptable_alternatives": sum(bool(row["acceptable_slugs"]) for row in matched),
        "metrics": scores,
        "prediction_status_counts": dict(Counter(row["prediction_status"] for row in results)),
        "not_in_catalog_with_candidates": sum(row["status"] == "not_in_catalog" and bool(row["top5_slugs"]) for row in results),
        "method": "Photo-weighted exact slug comparison. Only status=matched enters retrieval accuracy; failures count as misses. Notes do not override labels. Accepted metrics additionally allow explicit acceptable_slugs.",
    }
    return summary, results


def markdown(summary: dict, rows: list[dict]) -> str:
    lines = ["# Результаты проверки фотографий организаторов", "",
             "Расчёт по ручной разметке и сохранённым предсказаниям; повторный инференс не запускался.", "",
             "## Состав набора", "", "| Статус | Фото | Доля всех фото |", "|---|---:|---:|"]
    names = {"matched": "Есть в каталоге", "not_in_catalog": "Нет в каталоге",
             "not_reviewed": "Статус не выставлен", "uncertain": "Спорные"}
    for status, name in names.items():
        value = summary["status_counts"][status]
        lines.append(f"| {name} | {value['count']} | {value['percent']}% |")
    lines += ["", f"Различных подтверждённых slug: **{summary['unique_expected_slugs']}**. Метрики считаются по фотографиям, включая повторные снимки вина.",
              "", "## Точность среди фотографий вин из каталога", "",
              "| Метрика | Верных / проверенных | Процент |", "|---|---:|---:|"]
    for key, value in summary["metrics"].items():
        lines.append(f"| {key} | {value['count']} / {value['denominator']} | {value['percent']}% |")
    lines += ["", "Strict — точное совпадение с выбранным slug; accepted дополнительно учитывает явно указанные acceptable_slugs.",
              "Свободные заметки не меняют статус или правильный slug автоматически. Различия этикетки и года оцениваются согласно выбранной пользователем карточке.",
              "", "## Ошибки и незавершённая разметка", ""]
    exceptions = [row for row in rows if row["status"] in {"not_reviewed", "uncertain"}
                  or row["status"] == "matched" and row["strict_rank"] != 1]
    for row in exceptions:
        lines += [f"- `{row['filename']}`: статус `{row['status']}`, ожидаемый slug `{row['expected_slug']}`, позиция {row['strict_rank']}. Заметка: {row['notes']}"]
    if not exceptions:
        lines.append("Ошибок Top-1 и незавершённой разметки нет.")
    lines += ["", "## Границы результата", "",
              "Это результат на данном вручную проверенном наборе, не оценка точности на любых новых фотографиях. Разметка выполнялась с показом кандидатов; независимая проверка без подсказок ещё не проводилась.",
              f"Из отсутствующих в каталоге фотографий кандидаты выданы для {summary['not_in_catalog_with_candidates']}. Корректный отказ от сопоставления отсутствующего вина — отдельная задача; эти фотографии не входят в знаменатель Top-1/Top-5.",
              "", "## Все заметки пользователя", ""]
    for row in rows:
        if row.get("notes"):
            lines += [f"### {row['filename']}", "", f"Статус: `{row['status']}`. Slug: `{row['expected_slug']}`.", "", row["notes"].strip(), ""]
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--batch", type=Path, default=ROOT / "data/audit/organizers_real_photos/v1")
    parser.add_argument("--annotations", type=Path, default=ROOT / "data/audit/orgs_checks/organizers_annotations.json")
    parser.add_argument("--output", type=Path, default=ROOT / "data/audit/orgs_checks/evaluation_v1")
    args = parser.parse_args()
    summary, rows = evaluate(args.batch, args.annotations)
    args.output.mkdir(parents=True, exist_ok=True)
    for name, value in (("metrics.json", summary), ("per_image.json", rows)):
        (args.output / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (args.output / "report.md").write_text(markdown(summary, rows), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
