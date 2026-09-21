import sys
import unittest
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from label_segmentation_pilot import choose_label, choose_main_label
from detect_labels_pilot import image_rgb


def candidate(box, score):
    return {"box_xyxy": box, "score": score}


class TargetSelectionTests(unittest.TestCase):
    def test_main_region_beats_tiny_more_centered_emblem(self):
        candidates = [candidate([490, 200, 510, 220], .98), candidate([350, 400, 690, 800], .8)]
        self.assertEqual(choose_main_label(candidates, 1000, 1000)["label_index"], 1)

    def test_large_side_neighbor_does_not_replace_central_label(self):
        candidates = [candidate([0, 100, 300, 950], .99), candidate([350, 400, 650, 750], .7)]
        self.assertEqual(choose_main_label(candidates, 1000, 1000)["label_index"], 1)

    def test_transparent_hidden_rgb_does_not_become_visible_background(self):
        try:
            from PIL import Image
        except ImportError:
            self.skipTest("Pillow is required for image preprocessing")
        image = Image.new("RGBA", (2, 1))
        image.putdata([(255, 0, 0, 0), (3, 7, 11, 255)])
        converted = image_rgb(image, "white")
        self.assertEqual(converted.size, image.size)
        self.assertEqual(converted.getpixel((0, 0)), (255, 255, 255))
        self.assertEqual(converted.getpixel((1, 0)), (3, 7, 11))

    def test_central_bottle_with_cropped_neck_beats_confident_neighbor(self):
        bottles = [candidate([320, 0, 680, 950], .7), candidate([700, 0, 990, 900], .95)]
        labels = [candidate([710, 300, 980, 650], .9), candidate([330, 300, 670, 650], .5)]
        selected = choose_label(labels, bottles, 1000, 1000)
        self.assertEqual(selected["bottle_index"], 0)
        self.assertEqual(selected["label_index"], 1)

    def test_whole_bottle_proposal_does_not_beat_contained_label(self):
        bottle = candidate([300, 0, 700, 950], .9)
        labels = [candidate([300, 0, 700, 950], .95), candidate([310, 400, 690, 800], .4)]
        self.assertEqual(choose_label(labels, [bottle], 1000, 1000)["label_index"], 1)

    def test_label_only_closeup_is_allowed_without_bottle(self):
        result = choose_label([candidate([0, 0, 999, 999], .6)], [], 1000, 1000)
        self.assertEqual(result["status"], "selected_without_bottle")
        self.assertEqual(result["label_index"], 0)

    def test_conflict_does_not_silently_choose_neighbor(self):
        result = choose_label([candidate([700, 300, 950, 600], .9)],
                              [candidate([300, 0, 600, 900], .8)], 1000, 1000)
        self.assertEqual(result["status"], "bottle_label_conflict")
        self.assertIsNone(result["label_index"])

    def test_missing_label_does_not_create_center_crop(self):
        result = choose_label([], [candidate([300, 0, 600, 900], .8)], 1000, 1000)
        self.assertEqual(result["status"], "no_label_candidate")
        self.assertIsNone(result["label_index"])


if __name__ == "__main__":
    unittest.main()
