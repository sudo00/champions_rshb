"""CPU-only, refusal-only guard. Keeping candidates does not confirm identity."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path

import numpy as np

from backend.worker.catalog_refusal_features import (
    FEATURES, coverage, producer_support, query_features, title_terms,
)

VERSION = "conservative-refusal-v1"


class CatalogRefusal:
    def __init__(self, configuration: dict, catalog_sha256: str):
        if configuration.get("version") != VERSION:
            raise ValueError("Unsupported refusal policy")
        if configuration.get("catalog_sha256") != catalog_sha256:
            raise ValueError("Refusal model/catalogue mismatch")
        if configuration.get("features") != list(FEATURES):
            raise ValueError("Refusal feature schema mismatch")
        self.mean = np.asarray(configuration["mean"], dtype=float)
        self.scale = np.asarray(configuration["scale"], dtype=float)
        self.coefficients = np.asarray(configuration["coefficients"], dtype=float)
        self.intercept = float(configuration["intercept"])
        self.threshold = float(configuration["reject_below"])
        for vector in (self.mean, self.scale, self.coefficients):
            if vector.shape != (len(FEATURES),) or not np.isfinite(vector).all():
                raise ValueError("Invalid refusal model vector")
        if (self.scale <= 0).any() or not np.isfinite(self.intercept):
            raise ValueError("Invalid refusal model normalization")
        if not 0 < self.threshold < 1:
            raise ValueError("Invalid refusal threshold")

    @classmethod
    def from_bundle(cls, bundle: Path, manifest: dict) -> CatalogRefusal | None:
        relative = manifest.get("refusal_policy")
        if relative is None:
            return None  # Older bundles retain their existing behaviour.
        path = (Path(bundle) / relative).resolve()
        if not path.is_relative_to(Path(bundle).resolve()):
            raise ValueError("Invalid refusal policy path")
        with path.open("rb") as stream:
            digest = hashlib.file_digest(stream, "sha256").hexdigest()
        if manifest["files_sha256"].get(relative) != digest:
            raise ValueError("Refusal policy checksum mismatch")
        return cls(json.loads(path.read_text()), manifest["catalog_sha256"])

    def evaluate(self, result: dict, cards: dict[str, dict]) -> dict:
        decision = {"version": VERSION, "reject": False, "reason": "insufficient_evidence",
                    "score_is_probability": False, "reject_below": self.threshold}
        candidates = result.get("candidates", [])[:5]
        if len(candidates) != 5 or len({c.get("slug") for c in candidates}) != 5:
            return {**decision, "reason": "complete_top5_required"}
        if result.get("status") == "no_target":
            return {**decision, "reason": "no_target"}
        try:
            shortlist = {**result, "candidates": candidates}
            feature = query_features(shortlist, cards)
            protected = any(producer_support(c) >= .8 and
                            coverage(c, title_terms(cards[c["slug"]])) >= .8
                            for c in candidates)
            logit = float(((feature - self.mean) / self.scale) @ self.coefficients + self.intercept)
            if not np.isfinite(logit):
                raise ValueError("Non-finite refusal score")
            score = float(1 / (1 + np.exp(-np.clip(logit, -700, 700))))
            decision.update(score=score, identity_support=protected)
            if not feature[FEATURES.index("ocr_available")]:
                return {**decision, "reason": "no_reliable_ocr"}
            if protected:
                return {**decision, "reason": "positive_identity_evidence"}
            if score < self.threshold:
                return {**decision, "reject": True, "reason": "below_conservative_presence_threshold"}
            return {**decision, "reason": "keep_candidates"}
        except (KeyError, TypeError, ValueError, OverflowError):
            # Incomplete evidence must never become an invented absence verdict.
            return {**decision, "reason": "invalid_evidence_keep_candidates"}


def apply_refusal(result: dict, policy: CatalogRefusal | None, cards: dict) -> dict:
    """Apply before includeAlternatives truncation; preserve ranking on fallback."""
    if policy is None or result.get("status") == "no_target":
        return result
    decision = policy.evaluate(result, cards)
    updated = {**result, "catalog_refusal": decision}
    warnings = [w for w in result.get("warnings", []) if w != "unknown_rejection_not_calibrated"]
    updated["warnings"] = warnings + ["catalog_refusal_development_validated"]
    if decision["reject"]:
        updated.update(status="not_in_catalog", candidates=[],
                       message="Вино не найдено в каталоге.")
    return updated
