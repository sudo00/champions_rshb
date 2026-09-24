"""Use reviewed sugar categories to resolve near-tied variants of one wine line."""
from __future__ import annotations

import math
import re

VERSION = "reviewed-sweetness-v1"
BONUS = .04
MAX_GAP = .03
PATTERNS = [
    ("Экстра брют", r"экстра[\s-]*брют|extra[\s-]+brut"),
    ("Полусладкое", r"полу[\s-]*сладк(?:ое|ий|ая)|semi[\s-]*sweet"),
    ("Полусухое", r"полу[\s-]*сух(?:ое|ой|ая)|semi[\s-]*dry"),
    ("Брют", r"брют|brut"), ("Сладкое", r"сладк(?:ое|ий|ая)|sweet"),
    ("Сухое", r"сух(?:ое|ой|ая)|dry"),
]


def reviewed_sweetness(card: dict) -> str | None:
    review = card.get("metadata_review") or {}
    if review.get("status") not in ("confirmed", "applied"):
        return None
    value = review.get("confirmed_attributes", {}).get("sweetness")
    return value if value in {p[0] for p in PATTERNS} else None


def observed_sweetness(observations: list[dict]) -> dict:
    readings = {}
    for observation in observations:
        try:
            confidence = float(observation.get("confidence", 0))
        except (TypeError, ValueError):
            continue
        if observation.get("on_target_bottle") is False or not math.isfinite(confidence) or not .9 <= confidence <= 1:
            continue
        text = str(observation.get("text") or "").casefold().replace("ё", "е")
        for value, pattern in PATTERNS:
            pattern = r"(?<!\w)(?:" + pattern + r")(?!\w)"
            if not re.search(pattern, text):
                continue
            text = re.sub(pattern, " ", text)
            if confidence > readings.get(value, {}).get("confidence", 0):
                readings[value] = {k: observation.get(k) for k in ("text", "confidence", "source", "polygon_original")}
    return dict(value=next(iter(readings)) if len(readings) == 1 else None,
                status="read" if len(readings) == 1 else "ambiguous" if readings else "missing",
                readings=[dict(value=k, **v) for k, v in readings.items()])


def family(card: dict) -> tuple[str, ...] | None:
    values = tuple(" ".join(str(card.get(k) or "").casefold().replace("ё", "е").split())
                   for k in ("title", "winery", "grapes", "category"))
    return values if all(values) else None


def refine_sweetness(result: dict, cards: dict) -> dict:
    diagnostic = dict(version=VERSION, applied=False, reason="insufficient_evidence")
    candidates = result.get("candidates", [])
    if not candidates or result.get("status") == "no_target":
        return result
    observed = observed_sweetness(result.get("observations", []))
    diagnostic["observed"] = observed
    if observed["value"] is None:
        return {**result, "sweetness_ranking": {**diagnostic, "reason": observed["status"]}}
    first = candidates[0]
    key = family(cards.get(first["slug"], {}))
    relatives = [c for c in candidates[:5] if key and family(cards.get(c["slug"], {})) == key]
    sugars = {reviewed_sweetness(cards[c["slug"]]) for c in relatives} - {None}
    matches = [c for c in relatives if reviewed_sweetness(cards[c["slug"]]) == observed["value"]
               and 0 <= float(first["score"]) - float(c["score"]) <= MAX_GAP]
    if len(sugars) < 2 or not matches:
        return {**result, "sweetness_ranking": {**diagnostic, "reason": "no_reviewed_near_tied_variants"}}
    strength = float(observed["readings"][0]["confidence"])
    supported = {c["slug"] for c in matches}
    updated = []
    for c in candidates:
        item = dict(c)
        if c["slug"] in supported:
            item.update(base_score=c["score"], score=float(c["score"]) + BONUS * strength,
                        sweetness=observed["value"], sweetness_bonus=BONUS * strength,
                        sweetness_evidence=observed["readings"])
        updated.append(item)
    updated.sort(key=lambda c: (-c["score"], c["slug"]))
    # Saved API snapshots carry ranks; maintain them when replaying those results.
    updated = [{**c, "rank": i + 1} if "rank" in c else c for i, c in enumerate(updated)]
    diagnostic.update(applied=True, reason="reviewed_sugar_matches_label", previous_top1=first["slug"],
                      top1=updated[0]["slug"], bonus_scale=BONUS, max_base_gap=MAX_GAP)
    return {**result, "candidates": updated, "sweetness_ranking": diagnostic}
