"""Exact, multi-view retrieval. Scores rank candidates; they are not probabilities."""
from __future__ import annotations

import re
import numpy as np

VERSION = "visual-retrieval-v1"
PACKAGING = {"bottle", "box", "can", "carton", "pouch"}


def normalize_vectors(vectors: np.ndarray) -> np.ndarray:
    values = np.asarray(vectors, dtype=np.float32)
    if values.ndim != 2 or not np.isfinite(values).all():
        raise ValueError("Expected finite matrix of vectors")
    norms = np.linalg.norm(values, axis=1, keepdims=True)
    if np.any(norms < 1e-8):
        raise ValueError("Zero embedding")
    return values / norms


def packaging_hint(row: dict) -> dict:
    patterns = {
        "box": r"б[эе]г.?ин.?бокс|beg-in-boks|bag.in.box",
        "can": r"в банке|v-banke|алюминиев\w* банк\w*|жестян\w* банк\w*",
        "carton": r"тетрапак|tetra.?pak",
        "pouch": r"дой.?пак|doy.?pack",
        "bottle": r"стеклянн\w* бутылк\w*|glass bottle",
    }
    for source, text in [
        ("title_or_slug", str(row.get("title", "")) + " " + str(row.get("slug", ""))),
        ("description", str(row.get("description", ""))),
    ]:
        hits = [kind for kind, pattern in patterns.items() if re.search(pattern, text.lower())]
        if len(hits) == 1:
            # Restrict prose to an explicit packaging context. Serving suggestions are not metadata.
            if source == "description" and not re.search(r"упаков|фасов|разлит|поставля|выпуска|прода[её]т", text.lower()):
                continue
            return {"kind": hits[0], "source": source}
        if len(hits) > 1:
            return {"kind": "unknown", "source": "ambiguous_metadata"}
    return {"kind": "unknown", "source": None}


def packaging_adjustment(query: dict | None, card: dict) -> dict:
    hint = packaging_hint(card)
    if not query or query.get("kind") not in PACKAGING or not query.get("reliable") or hint["kind"] == "unknown":
        return {"factor": 1., "relation": "unknown", "catalog": hint}
    match = query["kind"] == hint["kind"]
    return {"factor": 1.03 if match else .90, "relation": "match" if match else "conflict", "catalog": hint}


def fuse_rankings(rankings: dict[str, list[dict]], limit: int = 10) -> list[dict]:
    """RRF over fixed sources, at most one contribution per slug per source."""
    scores = {}
    for source, candidates in rankings.items():
        seen = set()
        previous_cosine = None
        tied_rank = 1
        for candidate in candidates:
            slug = candidate["slug"]
            if slug in seen:
                continue
            seen.add(slug)
            row = scores.setdefault(slug, {"slug": slug, "score": 0., "sources": {}})
            rank = len(seen)
            # Identical shared references must not acquire artificial evidence from slug order.
            if "cosine" in candidate:
                if previous_cosine is None or abs(candidate["cosine"] - previous_cosine) > 1e-7:
                    tied_rank = rank
                previous_cosine = candidate["cosine"]
                rank = tied_rank
            row["score"] += 1 / (60 + rank)
            row["sources"][source] = {"rank": rank, **candidate}
    return sorted(scores.values(), key=lambda row: (-row["score"], row["slug"]))[:limit]


class VisualIndex:
    def __init__(self, vectors: np.ndarray, views: list[dict], cards: list[dict]):
        self.vectors = normalize_vectors(vectors)
        if len(self.vectors) != len(views) or not views:
            raise ValueError("Vectors and gallery views must be nonempty and aligned")
        self.views = views
        self.cards = {row["slug"]: row for row in cards}
        if any(not v.get("slugs") or any(s not in self.cards for s in v["slugs"]) for v in views):
            raise ValueError("Unmapped gallery view")

    def search(self, vectors: np.ndarray, views: list[dict], *, augment: bool = False,
               target_detected: bool = True, packaging: dict | None = None, limit: int = 10) -> dict:
        if not target_detected:
            return {"status": "no_target", "candidates": [], "branches": {}, "score_is_probability": False}
        query = normalize_vectors(vectors)
        if len(query) != len(views) or query.shape[1] != self.vectors.shape[1] or not views:
            raise ValueError("Query features do not match index")
        similarities = query @ self.vectors.T
        # Whole image is a fallback only when no segmented object was available.
        query_families = {v["family"] for v in views}
        branches = {}
        for branch in ("body", "label", "flat"):
            qfamily = "reference" if branch == "body" and "body" not in query_families else branch
            qi = [i for i, v in enumerate(views) if v["family"] == qfamily]
            if not qi:
                continue
            available_body = {v["record_id"] for v in self.views if v["family"] == "body"}
            families = {"label", "flat"} | ({"augmented"} if augment else set())
            gi = [i for i, v in enumerate(self.views) if (
                v["family"] in families if branch != "body" else
                (v["family"] == "body" or (v["family"] == "reference" and v["record_id"] not in available_body)))]
            if not gi:
                continue
            local = similarities[np.ix_(qi, gi)]
            winners = local.argmax(axis=0)
            unique = {}
            for j, g in enumerate(gi):
                q = qi[int(winners[j])]
                for slug in self.views[g]["slugs"]:
                    score = float(similarities[q, g])
                    if slug not in unique or score > unique[slug]["cosine"]:
                        unique[slug] = {"slug": slug, "cosine": score,
                            "query_view": views[q], "gallery_view": self.views[g]}
            branches[branch] = sorted(unique.values(), key=lambda row: (-row["cosine"], row["slug"]))[:limit]
        fused = fuse_rankings(branches, limit=len(self.cards))
        for candidate in fused:
            adjustment = packaging_adjustment(packaging, self.cards[candidate["slug"]])
            candidate["visual_score"] = candidate["score"]
            candidate["packaging"] = adjustment
            candidate["score"] *= adjustment["factor"]
        fused.sort(key=lambda row: (-row["score"], row["slug"]))
        return {"version": VERSION, "status": "candidates_unverified", "score_is_probability": False,
                "candidates": fused[:limit], "branches": branches, "augmentation": augment}
