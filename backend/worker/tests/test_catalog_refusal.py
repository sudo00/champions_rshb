import unittest
from unittest.mock import patch

from backend.worker.catalog_refusal import CatalogRefusal, FEATURES, VERSION, apply_refusal


class RefusalSafetyTests(unittest.TestCase):
    def setUp(self):
        self.config = dict(version=VERSION, catalog_sha256="catalog", features=list(FEATURES),
                           mean=[0.] * len(FEATURES), scale=[1.] * len(FEATURES),
                           coefficients=[0.] * len(FEATURES), intercept=-10., reject_below=.1)
        self.policy = CatalogRefusal(self.config, "catalog")
        self.cards = {f"wine-{i}": dict(slug=f"wine-{i}", title="riesling", winery="winery", grapes="riesling")
                      for i in range(5)}
        self.result = dict(status="candidates_unverified", observations=[{"text": "Рислинг", "confidence": .99}],
                           candidates=[dict(slug=s, visual_similarity=.5, score=.5) for s in self.cards],
                           warnings=[], regions=[{"kind": "label"}])

    def test_missing_ocr_never_becomes_absence(self):
        self.result["observations"] = []
        decision = self.policy.evaluate(self.result, self.cards)
        self.assertFalse(decision["reject"])
        self.assertEqual(decision["reason"], "no_reliable_ocr")

    def test_incomplete_or_duplicate_shortlist_keeps_candidates(self):
        for shortlist in (self.result["candidates"][:1], [self.result["candidates"][0]] * 5):
            decision = self.policy.evaluate({**self.result, "candidates": shortlist}, self.cards)
            self.assertFalse(decision["reject"])

    def test_strong_identity_for_any_candidate_overrides_low_model_score(self):
        candidate = self.result["candidates"][-1]
        candidate["evidence"] = [{"field": "title", "term": "riesling", "confidence": 1., "match_strength": 1.}]
        candidate["producer_evidence"] = [{"confidence": 1., "similarity": 1.}]
        with patch("backend.worker.catalog_refusal.title_terms", return_value={"riesling"}):
            decision = self.policy.evaluate(self.result, self.cards)
        self.assertFalse(decision["reject"])
        self.assertEqual(decision["reason"], "positive_identity_evidence")

    def test_invalid_evidence_does_not_clear_results(self):
        self.result["candidates"][0]["visual_similarity"] = float("nan")
        result = apply_refusal(self.result, self.policy, self.cards)
        self.assertEqual(result["candidates"], self.result["candidates"])
        self.assertFalse(result["catalog_refusal"]["reject"])

    def test_refusal_keeps_ocr_and_regions_and_does_not_mutate_input(self):
        result = apply_refusal(self.result, self.policy, self.cards)
        self.assertEqual(result["status"], "not_in_catalog")
        self.assertEqual(result["candidates"], [])
        self.assertEqual(len(self.result["candidates"]), 5)
        self.assertEqual(result["observations"], self.result["observations"])
        self.assertEqual(result["regions"], self.result["regions"])

    def test_wrong_catalogue_cannot_activate_policy(self):
        with self.assertRaises(ValueError):
            CatalogRefusal(self.config, "another-catalogue")


if __name__ == "__main__":
    unittest.main()
