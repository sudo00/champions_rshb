import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from sam3_gpu_study import choose_packaging_result, match_boxes, primary_scenario_reasons


class BoxEvaluationTests(unittest.TestCase):
    def test_primary_scenario_uses_only_annotation_geometry(self):
        self.assertEqual(primary_scenario_reasons({"size": [1000,1000], "gt_boxes": [[300,300,700,800]]}), [])
        self.assertIn("off_center", primary_scenario_reasons({"size": [1000,1000], "gt_boxes": [[20,300,220,800]]}))
        self.assertIn("small_target", primary_scenario_reasons({"size": [1000,1000], "gt_boxes": [[450,450,550,550]]}))
        self.assertEqual(primary_scenario_reasons({"size": [1000,1000], "gt_boxes": [[100,100,300,600],[600,100,800,600]]}), ["multiple_annotated_targets"])

    def test_packaging_fallback_preserves_good_label(self):
        label = {"status": "mask", "mask_area_fraction": .2}
        box = {"status": "mask", "mask_area_fraction": .9}
        self.assertIs(choose_packaging_result(label, box), label)

    def test_packaging_fallback_replaces_barcode_with_large_box(self):
        label = {"status": "mask", "mask_area_fraction": .016}
        box = {"status": "mask", "mask_area_fraction": .85}
        self.assertIs(choose_packaging_result(label, box), box)

    def test_packaging_fallback_does_not_invent_region(self):
        label = {"status": "no_detection", "mask_area_fraction": 0}
        self.assertIs(choose_packaging_result(label, None), label)

    def test_duplicate_predictions_do_not_count_twice(self):
        predictions = [{"box_xyxy": [0, 0, 100, 100], "score": score} for score in (.7, .9)]
        result = match_boxes(predictions, [[0, 0, 100, 100]])
        self.assertEqual((result["tp"], result["fp"], result["fn"]), (1, 1, 0))
        self.assertEqual(result["matches"][0]["prediction"], 1)

    def test_missing_and_spurious_boxes_are_separate(self):
        predictions = [{"box_xyxy": [200, 200, 300, 300], "score": .99}]
        result = match_boxes(predictions, [[0, 0, 100, 100]])
        self.assertEqual((result["tp"], result["fp"], result["fn"]), (0, 1, 1))

    def test_empty_predictions_keep_false_negatives(self):
        result = match_boxes([], [[0, 0, 100, 100]])
        self.assertEqual((result["tp"], result["fp"], result["fn"]), (0, 0, 1))


if __name__ == "__main__":
    unittest.main()
