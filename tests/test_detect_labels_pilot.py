import importlib.util
import json
from pathlib import Path
import tempfile
import unittest


SPEC = importlib.util.spec_from_file_location("detect_labels_pilot", Path(__file__).resolve().parents[1] / "scripts/detect_labels_pilot.py")
PILOT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PILOT)


class AutomaticLabelTests(unittest.TestCase):
    def test_nms_keeps_separate_neighbor_and_highest_duplicate(self):
        candidates = [{"box_xyxy": [10, 10, 40, 80], "score": 0.6},
                      {"box_xyxy": [11, 11, 41, 81], "score": 0.8},
                      {"box_xyxy": [60, 10, 90, 80], "score": 0.7}]
        self.assertEqual(PILOT.nms_indices(candidates, 0.5), [1, 2])

    def test_no_detection_does_not_invent_central_crop(self):
        selection = PILOT.select_target([], 100, 200)
        self.assertEqual(selection, {"status": "no_candidate", "selected_index": None, "ranking": []})

    def test_all_edge_candidates_require_review_without_selection(self):
        candidates = [{"box_xyxy": [0, 20, 70, 150], "score": 0.99}]
        selection = PILOT.select_target(candidates, 100, 200)
        self.assertEqual(selection["status"], "needs_review")
        self.assertIsNone(selection["selected_index"])

    def test_central_interior_label_preferred_to_confident_edge_neighbor(self):
        candidates = [{"box_xyxy": [0, 10, 35, 190], "score": 0.99},
                      {"box_xyxy": [35, 40, 65, 160], "score": 0.40},
                      {"box_xyxy": [70, 30, 95, 170], "score": 0.90}]
        selection = PILOT.select_target(candidates, 100, 200)
        self.assertEqual(selection["selected_index"], 1)
        self.assertEqual(selection["status"], "selected_by_heuristic")

    def test_changed_config_is_rejected_without_overwriting(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "config.json"
            path.write_text(json.dumps({"threshold": 0.25}))
            with self.assertRaises(ValueError):
                PILOT.validate_existing(path, {"threshold": 0.3})
            self.assertEqual(json.loads(path.read_text()), {"threshold": 0.25})

    def test_resume_validates_source_and_prompt_not_only_config_digest(self):
        source = {"id": "query", "sha256": "old"}
        result = {"config_sha256": "config", "source": source, "prompt": "wine label."}
        PILOT.validate_result(result, "config", source, "wine label.")
        with self.assertRaises(ValueError):
            PILOT.validate_result(result, "config", {**source, "sha256": "changed"}, "wine label.")
        with self.assertRaises(ValueError):
            PILOT.validate_result(result, "config", source, "bottle label.")


if __name__ == "__main__":
    unittest.main()
