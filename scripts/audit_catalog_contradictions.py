#!/usr/bin/env python3
"""Report metadata disagreements without changing catalog data or review notes.

Uses only the standard library. Rules produce review candidates, not corrections.
Run from the repository root: python3 scripts/audit_catalog_contradictions.py
"""

import argparse
import csv
import hashlib
import json
import re
import unicodedata
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
COLORS = {"Белое": "белое", "Красное": "красное", "Розовое": "розовое", "Оранжевое": "оранжевое"}
SLUG_COLORS = {"beloe": "Белое", "krasnoe": "Красное", "rozovoe": "Розовое", "oranzhevoe": "Оранжевое"}
# Explicit product adjectives only: masculine "Белый" can be part of a grape name.
COLOR_WORDS = re.compile(r"\b(белое|красное|розовое|оранжевое)\b")
GRAPES = {
    "Каберне Совиньон": ["каберне совиньон", "cabernet sauvignon"],
    "Каберне Фран": ["каберне фран", "cabernet franc"],
    "Совиньон Блан": ["совиньон блан", "sauvignon blanc"],
    "Шардоне": ["шардоне", "chardonnay"],
    "Пино Нуар": ["пино нуар", "pinot noir"],
    "Пино Блан": ["пино блан", "pinot blanc"],
    "Пино Гри": ["пино гри", "pinot gris", "пино гриджо", "pinot grigio"],
    "Рислинг": ["рислинг", "riesling"],
    "Саперави": ["саперави", "saperavi"],
    "Мерло": ["мерло", "merlot"],
    "Ркацители": ["ркацители", "rkatsiteli"],
    "Сира / Шираз": ["сира", "syrah", "шираз", "shiraz"],
    "Алиготе": ["алиготе", "aligote"],
    "Вионье": ["вионье", "viognier"],
    "Кокур": ["кокур", "kokur"],
    "Мальбек": ["мальбек", "malbec"],
    "Марселан": ["марселан", "marselan"],
    "Темпранильо": ["темпранильо", "tempranillo"],
    "Санджовезе": ["санджовезе", "sangiovese"],
    "Пти Вердо": ["пти вердо", "petit verdot"],
    "Мускат": ["мускат", "muscat", "moscato"],
    "Красностоп": ["красностоп", "krasnostop"],
    "Ребо": ["ребо", "rebo"],
}
SUGAR_PATTERNS = [
    ("полусухое", r"\b(?:полусухое|polusuhoe|semi dry|semidry)\b"),
    ("полусладкое", r"\b(?:полусладкое|polusladkoe|demi sec)\b"),
    ("экстра брют", r"\b(?:экстра брют|экстра брут|ekstra bryut|extra brut)\b"),
    ("брют натюр", r"\b(?:брют натюр|brut nature|bryut natyur)\b"),
    ("брют", r"\b(?:брют|брут|brut|bryut)\b"),
    ("сухое", r"\b(?:сухое|suhoe)\b"),
    ("сладкое", r"\b(?:сладкое|sladkoe|doux)\b"),
]
RULES = {
    "title_category": "Цвет в названии и категория",
    "description_category": "Явный цвет вина в описании и категория",
    "shade_category": "Оттенок и категория",
    "title_grapes": "Сорт в названии и поле сортов",
    "slug_category": "Цвет в slug и категория",
    "title_slug_sugar": "Сахар в названии и slug",
    "title_slug_year": "Год в названии и slug",
}


def normalize(value: str) -> str:
    value = unicodedata.normalize("NFKC", value).casefold().replace("ё", "е")
    return re.sub(r"[\W_]+", " ", value).strip()


def mentioned_grapes(value: str) -> set[str]:
    # Fix visual Latin/Cyrillic mixtures only inside words containing Latin text.
    # E.g. source "Сabernet" starts with Cyrillic С, not Latin C.
    confusables = str.maketrans("асеорхуі", "aceopxyi")
    words = normalize(value).split()
    words = [word.translate(confusables) if re.search("[a-z]", word) else word for word in words]
    value = " " + " ".join(words) + " "
    return {grape for grape, aliases in GRAPES.items()
            if any(" " + alias + " " in value for alias in aliases)}


def sugar_terms(value: str) -> set[str]:
    value = normalize(value)
    found = set()
    for label, pattern in SUGAR_PATTERNS:
        if re.search(pattern, value):
            found.add(label)
            value = re.sub(pattern, " ", value)
    return found


def check_row(row: dict[str, str]) -> list[dict]:
    findings = []
    category = row["Категория"]

    def add(rule: str, priority: str, fields: list[str], evidence: str) -> None:
        findings.append({
            "id": row["Slug"] + ":" + rule, "slug": row["Slug"],
            "rule": rule, "priority": priority, "reason": RULES[rule],
            "evidence": evidence, "fields": {key: row[key] for key in fields},
            "status": "needs_review",
        })

    title_colors = {key for key, word in COLORS.items()
                    if word in COLOR_WORDS.findall(normalize(row["Название вина"]))}
    if len(title_colors) == 1 and category not in title_colors:
        add("title_category", "1_explicit", ["Название вина", "Категория"],
            f"В названии: {next(iter(title_colors))}; категория: {category}.")

    description = normalize(row["Описание"])
    # Exclude red/white fruit and serving suggestions; only explicit wine clauses.
    color_matches = re.findall(r"\b(?:это|данное|наше) (белое|красное|розовое|оранжевое) вино\b|\bвино (белое|красное|розовое|оранжевое)\b", description)
    desc_colors = {key for match in color_matches for word in match
                   for key, adjective in COLORS.items() if word == adjective}
    if len(desc_colors) == 1 and category not in desc_colors:
        add("description_category", "1_explicit", ["Описание", "Категория"],
            f"В явной характеристике вина: {next(iter(desc_colors))}; категория: {category}.")

    shade = normalize(row["Цвет"])
    red = bool(re.search(r"\b(?:рубинов\w*|гранатов\w*|красн\w*)", shade))
    light = bool(re.search(r"\b(?:соломен\w*|лимон\w*|желт\w*)", shade))
    pink = bool(re.search(r"\b(?:розов\w*|лосос\w*)", shade))
    if (category == "Красное" and light and not red and not pink) or (
        category in {"Белое", "Оранжевое"} and red and not light and not pink
    ):
        add("shade_category", "2_semantic", ["Цвет", "Категория"],
            f"Оттенок «{row['Цвет'].strip()}» требует сверки с категорией «{category}».")

    title_grapes = mentioned_grapes(row["Название вина"])
    field_grapes = mentioned_grapes(row["Сорт винограда"])
    if title_grapes and field_grapes and title_grapes.isdisjoint(field_grapes):
        add("title_grapes", "2_semantic", ["Название вина", "Сорт винограда"],
            f"Распознано в названии: {', '.join(sorted(title_grapes))}; "
            f"в поле сортов: {', '.join(sorted(field_grapes))}. Возможна неполнота состава.")

    slug_colors = {SLUG_COLORS[token] for token in normalize(row["Slug"]).split() if token in SLUG_COLORS}
    if len(slug_colors) == 1 and category not in slug_colors:
        add("slug_category", "3_slug_hint", ["Slug", "Категория"],
            f"В slug: {next(iter(slug_colors))}; категория: {category}. Slug мог устареть.")
    title_sugar, slug_sugar = sugar_terms(row["Название вина"]), sugar_terms(row["Slug"])
    sugar_differences = slug_sugar - title_sugar
    # Coarse screening only. Do not treat broad "dry/brut" wording as proof of
    # a sugar discrepancy; precise categories require the label/specification.
    dry_family = {"сухое", "брют", "экстра брют", "брют натюр"}
    if title_sugar <= dry_family:
        sugar_differences -= dry_family
    if len(title_sugar) == 1 and sugar_differences:
        add("title_slug_sugar", "3_slug_hint", ["Название вина", "Slug"],
            f"В названии: {next(iter(title_sugar))}; в slug также найдено: {', '.join(sorted(sugar_differences))}. Slug мог устареть.")
    # Only a trailing modern year is treated as a vintage candidate, not a brand's founding date.
    title_year = re.search(r"\b(20\d{2})\s*(?:г(?:ода|од)?\.?)?$", row["Название вина"].strip())
    slug_years = set(re.findall(r"\b20\d{2}\b", normalize(row["Slug"])))
    title_years = set(re.findall(r"\b20\d{2}\b", normalize(row["Название вина"])))
    if title_year and len(title_years) == len(slug_years) == 1 and title_year[1] not in slug_years:
        add("title_slug_year", "3_slug_hint", ["Название вина", "Slug"],
            f"В конце названия: {title_year[1]}; в slug: {next(iter(slug_years))}.")
    return findings


def markdown(value: str) -> str:
    return value.replace("|", "\\|").replace("\n", " ")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, default=ROOT / "data/audit/contradictions")
    parser.add_argument("--catalog-view", choices=["raw", "curated"], default="raw")
    args = parser.parse_args()
    source = ROOT / "data/strapi_output0709.csv"
    curated_path = ROOT / "data/catalog/curated/catalog.jsonl"
    with source.open(encoding="utf-8-sig", newline="") as stream:
        raw_rows = list(csv.DictReader(stream))
    rows = {}
    for row in raw_rows:
        if row["Slug"] in rows and rows[row["Slug"]] != row:
            raise ValueError(f"Conflicting source rows for {row['Slug']}")
        rows[row["Slug"]] = row
    curated = {r["slug"]: r for r in map(json.loads, curated_path.read_text().splitlines())}
    if set(rows) != set(curated):
        raise ValueError("Source and curated catalog slug sets differ")
    if args.catalog_view == "curated":
        if args.output_dir.resolve() == (ROOT / "data/audit/contradictions").resolve():
            raise ValueError("Use a separate --output-dir to preserve the original review report")
        fields = {"Название вина": "title", "Категория": "category", "Цвет": "color_shade",
                  "Сорт винограда": "grapes", "Описание": "description", "Винодельня": "winery", "Регион": "region"}
        for slug, row in rows.items():
            for raw_field, field in fields.items():
                if field in curated[slug]:
                    row[raw_field] = curated[slug][field] or ""
    findings = [finding for row in rows.values() for finding in check_row(row)]
    findings.sort(key=lambda r: (r["priority"], r["slug"], r["rule"]))
    for finding in findings:
        card = curated[finding["slug"]]
        finding.update({"title": card["title"], "winery": card["winery"],
                        "reference_path": card["reference_path"],
                        "previous_image_review": card["review_action"]})
        if args.catalog_view == "curated":
            review = card.get("metadata_review", {})
            accepted = review.get("accepted_disagreements", {}).get(finding["rule"])
            if accepted and finding["fields"] == accepted["fields"]:
                finding["status"] = "reviewed_explained"
                finding["review_explanation"] = accepted["reason"]
    summary = {
        "source_rows": len(raw_rows), "catalog_cards": len(rows),
        "catalog_view": args.catalog_view,
        "unresolved_findings": sum(r["status"] == "needs_review" for r in findings),
        "cards_flagged": len({r["slug"] for r in findings}), "findings": len(findings),
        "by_rule": {rule: sum(r["rule"] == rule for r in findings) for rule in RULES},
        "by_priority": dict(Counter(r["priority"] for r in findings)),
        "flagged_cards_by_winery": dict(Counter(curated[slug]["winery"] for slug in sorted({r["slug"] for r in findings}))),
        "inputs": [{"path": str(p.relative_to(ROOT)), "sha256": hashlib.sha256(p.read_bytes()).hexdigest()}
                   for p in (source, curated_path)],
        "limitations": [
            "Flags are disagreements, not corrected ground truth. No source or user notes are modified.",
            "Absence of a flag does not verify a card; these are conservative, incomplete text rules.",
            "No OCR, visual image analysis, or external source verification is performed.",
            "Slug is a weak historical hint and is never used to overwrite metadata.",
            "Grape aliases are a limited hand-written vocabulary; partial blends can be missed.",
            "Sugar screening suppresses differences within dry/brut wording; it does not validate precise sugar categories.",
            "White grapes do not imply white wine; golden/amber shades do not establish orange wine.",
            "Region and winery correctness require independent sources and are not inferred here.",
        ],
    }
    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir / "findings.json").write_text(json.dumps({"summary": summary, "findings": findings}, ensure_ascii=False, indent=2) + "\n")
    lines = ["# Противоречия в метаданных каталога", "",
             f"Проверено карточек: **{len(rows)}**. Сигналов: **{len(findings)}**. Карточек для ревью: **{summary['cards_flagged']}**.", "",
             ("Проверен рабочий каталог после исправлений. Необъяснённых сигналов: " + str(summary["unresolved_findings"]) + ". Объяснённые расхождения сохраняются в отчёте с основаниями."
              if args.catalog_view == "curated" else "Это очередь для проверки. Правильное значение не назначено; исходные данные и предыдущие отметки не изменены."), "",
             "## Правила и результаты", "", "| Проверка | Сигналов |", "| --- | ---: |"]
    lines += [f"| {RULES[rule]} | {count} |" for rule, count in summary["by_rule"].items()]
    lines += ["", "## Как проверять", "",
              "Откройте фото по ссылке и сравните спорные поля с этикеткой. Если нужный текст не читается, отметьте «не удалось проверить». Для решения по метаданным используйте этикетку или источник производителя; совпадение со slug само по себе недостаточно.", "",
              ("Применённые решения — в `../applied_review.md`, исходные заметки — в `../review_notes.md`. Объяснённые сигналы повторного решения не требуют."
               if args.catalog_view == "curated" else "Решения записывайте в `review_notes.md`: поле, правильное значение и источник/обоснование. Файл создаётся один раз, повторный запуск его не перезаписывает. Текущие решения об изображениях остаются в силе."), "",
              "Приоритет 1 — явное текстовое расхождение. Приоритет 2 — смысловая проверка оттенка или сорта. Приоритет 3 — слабая подсказка из slug.", "",
              "## Карточки для ревью", ""]
    for slug in dict.fromkeys(r["slug"] for r in findings):
        card, original = curated[slug], rows[slug]
        lines += [f"### {card['title']} — {card['winery']}", "", f"`{slug}`", "",
                  f"[Открыть фото]({(ROOT / card['reference_path']).as_posix()})", "",
                  "| Поле | " + ("Рабочее значение" if args.catalog_view == "curated" else "Исходное значение") + " |", "| --- | --- |"]
        lines += [f"| {field} | {markdown(original[field])} |" for field in ["Название вина", "Категория", "Цвет", "Сорт винограда"]]
        lines += [""]
        for finding in findings:
            if finding["slug"] == slug:
                lines += [f"- **{finding['priority'][0]}. {finding['reason']}:** {finding['evidence']}"]
                if finding["status"] == "reviewed_explained":
                    lines += [f"  Уже объяснено при ревью: {finding['review_explanation']}"]
        lines += [""]
    lines += ["## Ограничения", "",
              "OCR и внешние источники в этом проходе не использовались. Отсутствие сигнала не подтверждает правильность карточки. Цвет вина не выводится из сорта винограда. Золотистый или янтарный оттенок не определяет категорию. Словарь сортов ограничен, а неполный состав купажа требует отдельной проверки. Годы проверяются только при единственном явном годе в конце названия и единственном годе в slug. Различия внутри формулировок «сухое/брют/экстра брют/брют натюр» не сигнализируются: точные категории сахара здесь не проверяются. Регион и производитель по этим полям независимо не проверяются.", "",
              "## Воспроизведение", "", "```bash",
              ("python3 scripts/audit_catalog_contradictions.py --catalog-view curated --output-dir data/audit/contradictions/after_review"
               if args.catalog_view == "curated" else "python3 scripts/audit_catalog_contradictions.py"), "```", "",
              "Машиночитаемый отчёт: `findings.json`. Он содержит точные значения полей, причины, пути фотографий и контрольные суммы входов.", ""]
    (args.output_dir / "README.md").write_text("\n".join(lines))
    notes_path = args.output_dir / "review_notes.md"
    if args.catalog_view == "raw" and not notes_path.exists():
        notes = ["# Решения по противоречиям", "",
                 "Описание полей и фотографии — в `README.md`. Заполняйте решение свободным текстом, например: «Категория → Белое; на этикетке белое сухое». Если подтвердить не удалось, так и напишите. Отдельно укажите источник. Slug не изменяйте.", ""]
        for slug in dict.fromkeys(r["slug"] for r in findings):
            notes += [f"## {slug}", "", f"{curated[slug]['title']} — {curated[slug]['winery']}", "",
                      "Решение: ", "", "Источник / обоснование: ", ""]
        notes_path.write_text("\n".join(notes))
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
