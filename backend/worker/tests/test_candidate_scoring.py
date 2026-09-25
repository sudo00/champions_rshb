import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import MagicMock, patch

from backend.worker import recognition
from backend.worker.candidate_scoring import CandidateScorer, FEATURES, VERSION, apply_candidate_scores


class CandidateScoringTests(unittest.TestCase):
    def setUp(self):
        self.config = dict(version=VERSION, catalog_sha256="catalog", features=list(FEATURES),
                           mean=[0.] * len(FEATURES), scale=[1.] * len(FEATURES),
                           coefficients=[8.] + [0.] * (len(FEATURES) - 1), intercept=2., refusal_cap=.1)
        self.scorer = CandidateScorer(self.config, "catalog")
        self.cards = {f"wine-{i}": dict(slug=f"wine-{i}", title="riesling", winery="winery", grapes="riesling")
                      for i in range(5)}
        self.result = dict(candidates=[dict(slug=s, score=1.-i/10, visual_similarity=.5)
                                      for i, s in enumerate(self.cards)],
                           status="candidates_unverified", observations=[{"text": "Рислинг", "confidence": .9}],
                           observed_fields={}, regions=[], image_size=[100, 200], coordinate_system="original",
                           target={}, warnings=[], version="v5", catalog_sha256="catalog", timings_seconds={})

    def test_absolute_scores_preserve_ranking_raw_scores_and_equal_candidates(self):
        self.result["candidates"][1]["score"] = self.result["candidates"][0]["score"]
        result = apply_candidate_scores(self.result, self.scorer, self.cards)
        scores = [c["matchScore"] for c in result["candidates"]]
        self.assertEqual(scores, sorted(scores, reverse=True))
        self.assertEqual(scores[0], scores[1])
        self.assertNotAlmostEqual(sum(scores), 1.)
        for before, after in zip(self.result["candidates"], result["candidates"]):
            self.assertEqual(before, {k: after[k] for k in before})
            self.assertNotIn("matchScore", before)
        self.assertEqual(result["observations"], self.result["observations"])

    def test_all_five_can_be_low_without_changing_recognition_status(self):
        scorer = CandidateScorer({**self.config, "intercept": -10.}, "catalog")
        result = apply_candidate_scores(self.result, scorer, self.cards)
        self.assertLess(max(c["matchScore"] for c in result["candidates"]), .001)
        self.assertEqual(result["status"], "candidates_unverified")

    def test_invalid_or_incomplete_evidence_is_not_fabricated_zero(self):
        for candidates in (self.result["candidates"][:1], [self.result["candidates"][0]] * 5,
                           [{**c, "score": float("nan")} for c in self.result["candidates"]]):
            result = apply_candidate_scores({**self.result, "candidates": candidates}, self.scorer, self.cards)
            self.assertEqual(result["candidate_scoring"]["status"], "unavailable")
            self.assertTrue(all("matchScore" not in c for c in result["candidates"]))

    def test_model_validates_catalogue_monotonicity_and_bundle_checksum(self):
        with self.assertRaises(ValueError):
            CandidateScorer(self.config, "other")
        with self.assertRaises(ValueError):
            CandidateScorer({**self.config, "coefficients": [-1.] + [0.] * (len(FEATURES) - 1)}, "catalog")
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "scorer.json"
            path.write_text(json.dumps(self.config))
            manifest = dict(candidate_scoring="scorer.json", catalog_sha256="catalog",
                            files_sha256={"scorer.json": hashlib.sha256(path.read_bytes()).hexdigest()})
            self.assertIsInstance(CandidateScorer.from_bundle(Path(directory), manifest), CandidateScorer)
            path.write_text("{}")
            with self.assertRaises(ValueError):
                CandidateScorer.from_bundle(Path(directory), manifest)
        self.assertIsNone(CandidateScorer.from_bundle(Path("."), {}))

    def test_adapter_scores_before_refusal_or_output_limit_and_eval_keeps_top1(self):
        model = MagicMock(lookup=self.cards)
        model.predict.side_effect = lambda *a, **kw: {**self.result}
        policy = MagicMock()
        with patch.object(recognition, "initialize", return_value=model), \
             patch.object(recognition, "_refusal", policy), patch.object(recognition, "_scorer", self.scorer):
            policy.evaluate.return_value = {"reject": False, "reason": "keep_candidates"}
            top5 = recognition.run(b"image", True)
            top1 = recognition.run(b"image", False)
            self.assertEqual(top1["candidates"], top5["candidates"][:1])
            self.assertEqual(top1["candidateScoring"], top5["candidateScoring"])
            policy.evaluate.return_value = {"reject": True, "reason": "below_threshold"}
            mobile = recognition.run(b"image")
            evaluation = recognition.run(b"image", False, applyCatalogRefusal=False)
        self.assertEqual(mobile["slug"], "unknown")
        self.assertEqual(mobile["candidates"], [])
        self.assertEqual(len(mobile["candidateScoring"]["candidates"]), 5)
        self.assertLessEqual(max(c["matchScore"] for c in mobile["candidateScoring"]["candidates"]), .1)
        self.assertEqual(evaluation["slug"], "wine-0")
        self.assertEqual(evaluation["catalogRefusal"]["reason"], "disabled_for_evaluation")
        self.assertEqual(evaluation["candidateScoring"], mobile["candidateScoring"])


if __name__ == "__main__":
    unittest.main()
