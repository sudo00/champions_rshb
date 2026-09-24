"""Runtime catalogue-presence features; numerical parity with the offline pilot.

Inputs contain image-derived evidence and catalogue metadata, never labels or
filenames. The target is catalogue presence, not correctness of a particular slug.
"""
from __future__ import annotations

import re

import numpy as np

from worker.pipeline.text_search import COLORS, SWEETNESS, tokens

YEAR = re.compile(r"(?<!\d)(?:19|20)\d{2}(?!\d)")


ACCEPTED_VARIANTS = frozenset({
    "abrau-dyurso-risling-beloe-suhoe-12",
    "abrau-dyurso-risling-beloe-suhoe-13",
})


def product_key(card: dict) -> str:
    """Conservative equivalence: only year tokens removed from otherwise same slug.

    Winery, grapes and colour must also agree. Reserve/line/sweetness tokens
    stay in the slug. General same-winery/same-grape matches are not equivalent.
    """
    slug = str(card["slug"])
    if slug in ACCEPTED_VARIANTS:
        return "explicit:abrau-riesling-12-13"
    slug = re.sub(r"-+", "-", YEAR.sub("", slug)).strip("-")
    metadata = [" ".join(tokens(str(card.get(k, ""))))
                for k in ("winery", "grapes", "category")]
    return "|".join([slug, *metadata])


VERSION = "catalog-membership-v2"
FEATURES = (
    "top_visual", "best_visual", "visual_margin_other_product",
    "top_producer_support", "top_title_support", "top_distinctive_title_support",
    "distinctive_title_available", "top_grape_support", "text_margin_other_product",
    "producer_conflict", "grape_conflict", "color_conflict", "sweetness_conflict",
    "best_title_support", "best_producer_support", "visual_text_product_agreement",
    "ocr_available", "title_terms_available",
)


def evidence_strength(evidence: dict) -> float:
    confidence = float(evidence.get("confidence", 0.))
    similarity = float(evidence.get("match_strength", evidence.get("similarity", 0.)))
    if not np.isfinite([confidence, similarity]).all():
        raise ValueError("Non-finite OCR evidence")
    return float(np.clip(confidence, 0, 1) * np.clip(similarity, 0, 1))


def producer_support(candidate: dict) -> float:
    # Both paths are necessary: producer_evidence does not have field/match_strength.
    evidence = [e for e in candidate.get("evidence", []) if e.get("field") in {"winery", "winery_alias"}]
    evidence += candidate.get("producer_evidence", [])
    return max((evidence_strength(e) for e in evidence), default=0.)


def title_terms(card: dict) -> set[str]:
    return (set(tokens(str(card.get("title", "")))) - set(tokens(str(card.get("winery", ""))))
            - COLORS - SWEETNESS)


def coverage(candidate: dict, terms: set[str]) -> float:
    # Repeated OCR views never count as independent votes.
    strengths = {term: max((evidence_strength(e) for e in candidate.get("evidence", [])
                            if e.get("term") == term), default=0.) for term in terms}
    return sum(strengths.values()) / max(1, len(terms))


def query_features(result: dict, cards: dict[str, dict]) -> np.ndarray:
    candidates = result.get("candidates", [])
    if len(candidates) != 5:
        raise ValueError("Membership v2 requires the full frozen five-candidate shortlist")
    if len({c["slug"] for c in candidates}) != len(candidates):
        raise ValueError("Duplicate candidate slug")
    first = candidates[0]
    card = cards[first["slug"]]
    titles = [title_terms(cards[c["slug"]]) for c in candidates]
    supports = [coverage(c, t) for c, t in zip(candidates, titles)]
    producers = [producer_support(c) for c in candidates]
    other_indices = [i for i, c in enumerate(candidates) if product_key(cards[c["slug"]]) != product_key(card)]
    others = [candidates[i] for i in other_indices]
    other_terms = set().union(*(titles[i] for i in other_indices))
    distinct = titles[0] - other_terms
    visual = [float(c.get("visual_similarity", 0.)) for c in candidates]
    text = [max(0., float(c.get("text_score") or 0.)) for c in candidates]
    text_margin = (text[0] - max((text[i] for i in other_indices), default=text[0])) / max(1., max(text))
    conflicts = {e.get("field") for e in first.get("conflicts", [])}
    producer_mismatch = "winery" in conflicts or any(c.get("producer_match") and
                         cards[c["slug"]].get("winery") != card.get("winery") for c in candidates)
    visual_product = product_key(cards[candidates[int(np.argmax(visual))]["slug"]])
    text_product = product_key(cards[candidates[int(np.argmax(text))]["slug"]])
    ocr_available = any(e.get("on_target_bottle") is not False and float(e.get("confidence", 0)) >= .65
                        and bool(tokens(str(e.get("text", "")))) for e in result.get("observations", []))
    values = [visual[0], max(visual), visual[0] - max((float(c.get("visual_similarity", 0)) for c in others), default=visual[0]),
              producers[0], supports[0], coverage(first, distinct), float(bool(distinct)),
              coverage(first, set(tokens(str(card.get("grapes", ""))))), text_margin,
              float(producer_mismatch), float("grapes" in conflicts), float("category" in conflicts),
              float("sweetness" in conflicts), max(supports), max(producers),
              float(max(text) > 0 and visual_product == text_product), float(ocr_available), float(bool(titles[0]))]
    array = np.asarray(values, dtype=np.float64)
    if not np.isfinite(array).all():
        raise ValueError("Non-finite membership features")
    return array
