"""Apply explicit, versioned metadata review decisions to derived catalog rows."""

import hashlib
import json
from pathlib import Path


FIELDS = {"title", "category", "color_shade", "grapes", "region", "winery", "description"}
CATEGORIES = {"Белое", "Красное", "Розовое", "Оранжевое"}


def load_metadata_decisions(root: Path) -> dict | None:
    path = root / "data/catalog/metadata_decisions.json"
    if not path.exists():
        return None
    document = json.loads(path.read_text())
    for item in document["inputs"]:
        source = (root / item["path"]).resolve()
        source.relative_to((root / "data").resolve())
        if hashlib.sha256(source.read_bytes()).hexdigest() != item["sha256"]:
            raise ValueError(f"Metadata review input changed: {item['path']}")
    slugs = [row["slug"] for row in document["decisions"]]
    if len(slugs) != len(set(slugs)):
        raise ValueError("Duplicate metadata decision")
    return document


def apply_metadata(row: dict, decision: dict) -> None:
    if row["slug"] != decision["slug"]:
        raise ValueError("Metadata decision slug mismatch")
    updates = decision["updates"]
    if set(updates) - FIELDS:
        raise ValueError("Metadata review cannot change identity or image fields")
    for field, update in updates.items():
        if row[field] != update["before"]:
            raise ValueError(f"Original metadata changed: {row['slug']} {field}")
        if field == "category" and update["after"] not in CATEGORIES:
            raise ValueError("Invalid reviewed category")
        if update["after"] is None and field != "color_shade":
            raise ValueError(f"Unsupported empty field: {field}")
    row["original_metadata"] = {field: row[field] for field in updates}
    row.update({field: update["after"] for field, update in updates.items()})
    row["metadata_review"] = {
        key: decision[key] for key in ["status", "user_decision", "user_basis", "interpretation",
                                      "confirmed_attributes", "open_questions", "accepted_disagreements"]
    }


def write_metadata_review_report(root: Path, document: dict) -> None:
    decisions = document["decisions"]
    field_names = {"title": "Название", "category": "Категория", "color_shade": "Оттенок", "grapes": "Сорта"}
    changed = sum(bool(d["updates"]) for d in decisions)
    count = sum(len(d["updates"]) for d in decisions)
    lines = ["# Результат ревью метаданных", "",
             f"Обработано карточек: {len(decisions)}. Изменено полей: {count}, затронуто карточек: {changed}.", "",
             "Изменения применены в `data/catalog/curated/catalog.jsonl`. Исходный CSV, заметки, slug и изображения сохранены.", "",
             "## Открытые вопросы", ""]
    for d in decisions:
        for question in d["open_questions"]:
            lines.append(f"- `{d['slug']}`: {question}")
    if not any(d["open_questions"] for d in decisions):
        lines.append("Нет незакрытых вопросов из этого прохода.")
    lines += ["", "## Изменения и подтверждения", ""]
    for d in decisions:
        lines += [f"### {d['slug']}", "", f"Решение пользователя: {d['user_decision']}", ""]
        for field, update in d["updates"].items():
            after = update["after"] if update["after"] is not None else "не установлен (null)"
            lines.append(f"- {field_names.get(field, field)}: **{update['before']} → {after}**.")
        if d["confirmed_attributes"]:
            lines.append("- Дополнительные уточнения пользователя: " + json.dumps(d["confirmed_attributes"], ensure_ascii=False) + ".")
        if not d["updates"]:
            lines.append("- Основные поля сохранены.")
        lines += ["", d["interpretation"], ""]
        for accepted in d["accepted_disagreements"].values():
            lines += ["Объяснённое расхождение: " + accepted["reason"], ""]
    lines += ["## Источники и повторная проверка", "",
              "Точные пользовательские формулировки, исходные значения и контрольные суммы сохранены в `data/catalog/metadata_decisions.json`. Ссылки на сторонние сайты в заметках не указаны; они не выдуманы. `metadata_review.confirmed_attributes` содержит пользовательские уточнения, а `original_metadata` — значения изменённых полей до ревью.", "",
              "Для Эндемы Бианка удалён противоречащий белому вину розовый оттенок, новый оттенок не выдуман. У Мысхако категория по этикетке «Белое», стиль «Оранж» сохранён отдельно. Подтверждённые расхождения со slug отмечаются объяснёнными только при точном совпадении проверенных полей.", "",
              "```bash", "python3 scripts/build_curated_catalog.py",
              "python3 scripts/audit_catalog_contradictions.py --catalog-view curated --output-dir data/audit/contradictions/after_review", "```", ""]
    (root / "data/audit/contradictions/applied_review.md").write_text("\n".join(lines))
