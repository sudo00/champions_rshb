"""Absolute Top-5 match scores, separate from ranking and the refusal policy."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path

import numpy as np

from backend.worker.catalog_refusal_features import FEATURES as QUERY_FEATURES, query_features

VERSION = "candidate-match-score-v1"
FEATURES = ("retrieval_gap_to_best", *QUERY_FEATURES, "top_retrieval_score")


def candidate_features(result: dict, cards: dict) -> np.ndarray:
    candidates = result.get("candidates", [])[:5]
    if len(candidates) != 5:
        raise ValueError("Full Top-5 required before output truncation")
    if len({c["slug"] for c in candidates}) != 5 or any(c["slug"] not in cards for c in candidates):
        raise ValueError("Top-5 must contain distinct catalogue candidates")
    context = query_features({**result, "candidates": candidates}, cards)
    scores = np.asarray([c["score"] for c in candidates], dtype=float)
    if not np.isfinite(scores).all():
        raise ValueError("Non-finite retrieval score")
    best = float(scores.max())
    return np.stack([np.r_[value - best, context, best] for value in scores])


class CandidateScorer:
    def __init__(self, configuration: dict, catalog_sha256: str):
        if configuration.get("version") != VERSION or configuration.get("catalog_sha256") != catalog_sha256:
            raise ValueError("Candidate scorer version/catalogue mismatch")
        if configuration.get("features") != list(FEATURES):
            raise ValueError("Candidate scorer feature schema mismatch")
        self.mean = np.asarray(configuration["mean"], dtype=float)
        self.scale = np.asarray(configuration["scale"], dtype=float)
        self.coefficients = np.asarray(configuration["coefficients"], dtype=float)
        self.intercept = float(configuration["intercept"])
        self.refusal_cap = float(configuration["refusal_cap"])
        for vector in (self.mean, self.scale, self.coefficients):
            if vector.shape != (len(FEATURES),) or not np.isfinite(vector).all():
                raise ValueError("Invalid candidate scorer vector")
        if (self.scale <= 0).any() or self.coefficients[0] < 0 or not np.isfinite(self.intercept):
            raise ValueError("Invalid candidate scorer normalization/monotonicity")
        if not 0 <= self.refusal_cap <= 1:
            raise ValueError("Invalid refusal score cap")

    @classmethod
    def from_bundle(cls, bundle: Path, manifest: dict) -> CandidateScorer | None:
        relative = manifest.get("candidate_scoring")
        if relative is None:
            return None
        path = (Path(bundle) / relative).resolve()
        if not path.is_relative_to(Path(bundle).resolve()):
            raise ValueError("Invalid candidate scorer path")
        with path.open("rb") as stream:
            digest = hashlib.file_digest(stream, "sha256").hexdigest()
        if manifest["files_sha256"].get(relative) != digest:
            raise ValueError("Candidate scorer checksum mismatch")
        return cls(json.loads(path.read_text()), manifest["catalog_sha256"])

    def evaluate(self, result: dict, cards: dict, *, refusal_rejects: bool = False) -> dict:
        x = candidate_features(result, cards)
        logits = ((x - self.mean) / self.scale) @ self.coefficients + self.intercept
        if not np.isfinite(logits).all():
            raise ValueError("Non-finite candidate score")
        scores = np.exp(-np.logaddexp(0., -logits))
        if refusal_rejects:
            scores = np.minimum(scores, self.refusal_cap)
        return {
            "version": VERSION, "status": "scored", "scoreIsProbability": False,
            "scope": "same_product_ignoring_vintage", "refusalCapApplied": bool(refusal_rejects),
            "top1Gap": float(scores[0] - max(scores[1:])),
            "candidates": [
                {"slug": c["slug"], "rank": i + 1, "matchScore": float(value), "retrievalScore": c["score"]}
                for i, (c, value) in enumerate(zip(result["candidates"][:5], scores))
            ],
        }


def apply_candidate_scores(result: dict, scorer: CandidateScorer | None, cards: dict,
                           *, refusal_rejects: bool = False) -> dict:
    if scorer is None or result.get("status") == "no_target":
        return result
    try:
        diagnostic = scorer.evaluate(result, cards, refusal_rejects=refusal_rejects)
    except (KeyError, TypeError, ValueError, OverflowError):
        return {**result, "candidate_scoring": {"version": VERSION, "status": "unavailable",
                                                "reason": "invalid_or_incomplete_evidence", "scoreIsProbability": False}}
    candidates = [{**c, "matchScore": diagnostic["candidates"][i]["matchScore"], "matchScoreIsProbability": False}
                  if i < 5 else dict(c) for i, c in enumerate(result["candidates"])]
    return {**result, "candidates": candidates, "candidate_scoring": diagnostic}
