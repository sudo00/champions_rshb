"""Conservative, catalogue-independent fields supported by actual OCR lines."""
from __future__ import annotations

from datetime import date
import re

GRAPES = {
    "Каберне Совиньон": r"каберне\s*совиньон|cabernet\s*sauvignon",
    "Каберне Фран": r"каберне\s*фран\b|cabernet\s*franc\b",
    "Совиньон Блан": r"совиньон\s*блан|sauvignon\s*blanc",
    "Шардоне": r"шардоне|chardonnay",
    "Рислинг": r"рислинг|riesling",
    "Ркацители": r"ркацители|rkatsiteli",
    "Вионье": r"вионье|viognier",
    "Мерло": r"мерло\b|merlot",
    "Пино Нуар": r"пино\s*нуар|pinot\s*noir",
    "Пино Блан": r"пино\s*блан|pinot\s*blanc",
    "Пино Гриджио": r"пино\s*гриджио|pinot\s*grigio",
    "Пино Гри": r"пино\s*гри\b|pinot\s*gris\b",
    "Мускат": r"мускат\b|muscat\b|moscato",
    "Саперави": r"саперави|saperavi",
    "Зинфандель": r"зинфандель|zinfandel",
    "Траминер": r"\bтраминер|\btraminer",
    "Гевюрцтраминер": r"гевюрцтраминер|gew[uü]rztraminer",
    "Алиготе": r"алиготе|aligot[eé]",
    "Сира / Шираз": r"\bсира\b|шираз|\bsyrah\b|\bshiraz\b",
}
COLORS = {"Белое": r"белое|белый|\bwhite\b", "Красное": r"красное|красный|\bred\b",
          "Розовое": r"розовое|розовый|\bros[eé]\b", "Оранжевое": r"оранжевое|\borange\b"}


def extract_observed_fields(observations: list[dict]) -> dict:
    lines = {}
    for observation in observations:
        if observation.get("on_target_bottle") is False or observation.get("confidence", 0) < .65:
            continue
        text = observation["text"].strip()
        key = re.sub(r"\s+", " ", text.casefold())
        if key and (key not in lines or observation["confidence"] > lines[key]["confidence"]):
            lines[key] = observation
    groups = {"grapes": {}, "colors": {}, "printed_year_candidates": {}}
    for line in lines.values():
        text = line["text"].casefold().replace("ё", "е")
        evidence = {k: line.get(k) for k in ("text", "confidence", "source", "polygon_original")}
        for group, patterns in (("grapes", GRAPES), ("colors", COLORS)):
            for value, pattern in patterns.items():
                if re.search(pattern, text):
                    groups[group].setdefault(value, []).append(evidence)
        if not re.search(r"основан|\bосн\.?|establish|founded|\bsince\b", text):
            for year in re.findall(r"(?<!\d)(?:19|20)\d{2}(?!\d)", text):
                if int(year) <= date.today().year:
                    groups["printed_year_candidates"].setdefault(year, []).append(evidence)
    return {"raw_label_lines": list(lines.values()),
            **{key: [{"value": value, "evidence": evidence} for value, evidence in values.items()]
               for key, values in groups.items()},
            "product_name": None,
            "note": "Only OCR-supported hypotheses. Printed year is not necessarily vintage. No field is copied from nearest catalogue card; product-name extraction is not implemented."}
