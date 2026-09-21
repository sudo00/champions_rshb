"""Continuous visual similarity plus discriminating OCR evidence; no label/file priors."""
from __future__ import annotations

import numpy as np

from .text_search import CatalogTextIndex, COLORS, SWEETNESS, TECHNICAL_TERMS, tokens
from .visual_search import VisualIndex

VERSION = "hybrid-evidence-v1"
BRANCH_WEIGHTS = {"body": .5, "label": .3, "flat": .2}
TEXT_BONUS = .15


def text_reliability(candidate: dict, card: dict) -> float:
    producer = set(tokens(str(card.get("winery", ""))))
    evidence = candidate.get("evidence", [])
    identity = [e["strength"] for e in evidence if e["field"] in ("title", "grapes")
                and e["term"] not in producer | COLORS | SWEETNESS | TECHNICAL_TERMS
                and len(e["term"]) >= 4 and not e["term"].isdigit()]
    if not identity:
        return 0.
    producer_seen = any(e["field"] in ("winery", "winery_alias") and e["strength"] >= .7 for e in evidence)
    return max(identity) * (1. if producer_seen else .35)


def rerank_candidates(visual_scores: dict[str, float], text_candidates: list[dict],
                      cards: dict[str, dict], *, limit: int = 10) -> list[dict]:
    if limit < 1:
        raise ValueError("limit must be positive")
    if not visual_scores:
        return []
    if any(not np.isfinite(value) for value in visual_scores.values()):
        raise ValueError("Nonfinite visual similarity")
    text = {c["slug"]: c for c in text_candidates}
    scale = max(1., max((c["score"] for c in text_candidates), default=0.))
    rows = []
    for slug, similarity in visual_scores.items():
        candidate = text.get(slug)
        reliability = text_reliability(candidate, cards[slug]) if candidate else 0.
        normalized_text = max(0., candidate["score"])/scale if candidate else 0.
        bonus = TEXT_BONUS * reliability * normalized_text
        rows.append({"slug": slug, "score": float(similarity + bonus), "visual_similarity": float(similarity),
                     "text_score": candidate["score"] if candidate else None, "text_reliability": reliability,
                     "text_bonus": bonus, "evidence": candidate.get("evidence", []) if candidate else [],
                     "conflicts": candidate.get("conflicts", []) if candidate else [],
                     "score_is_probability": False})
    return sorted(rows, key=lambda r: (-r["score"], r["slug"]))[:limit]


class HybridIndex:
    def __init__(self, vectors: np.ndarray, views: list[dict], cards: list[dict]):
        self.visual = VisualIndex(vectors, views, cards)
        self.text = CatalogTextIndex(cards)
        self.cards = {r["slug"]: r for r in cards}

    def search(self, vectors: np.ndarray, views: list[dict], observations: list[dict], *,
               target_detected: bool = True, limit: int = 10) -> dict:
        if not target_detected:
            return {"version": VERSION, "status": "no_target", "candidates": [], "score_is_probability": False}
        # Score all catalogue cards continuously. Truncation occurs after evidence fusion.
        # Synthetic gallery poses remain a separate ablation; primary views are preserved.
        visual = self.visual.search(vectors, views, augment=False, limit=len(self.cards))
        branches = visual["branches"]
        scores = {slug: 0. for slug in self.cards}
        weights = sum(BRANCH_WEIGHTS[branch] for branch in branches)
        if not weights:
            return {"version": VERSION, "status": "no_target", "candidates": [], "score_is_probability": False}
        for branch, candidates in branches.items():
            for candidate in candidates:
                scores[candidate["slug"]] += BRANCH_WEIGHTS[branch] * candidate["cosine"] / weights
        text = self.text.search(observations, limit=len(self.cards))
        candidates = rerank_candidates(scores, text["candidates"], self.cards, limit=limit)
        return {"version": VERSION, "status": "candidates_unverified", "score_is_probability": False,
                "candidates": candidates, "visual_continuous": [
                    {"slug": slug, "score": float(value)} for slug, value in
                    sorted(scores.items(), key=lambda x: (-x[1], x[0]))[:limit]],
                "text_candidates": text["candidates"][:limit],
                "branch_weights": BRANCH_WEIGHTS, "text_bonus_scale": TEXT_BONUS,
                "limitations": ["Development heuristic; unknown rejection not calibrated.",
                                "Year and file/annotation metadata are not identity inputs."]}
