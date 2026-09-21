import importlib.util
from pathlib import Path
import json
import tempfile
import unittest


SPEC = importlib.util.spec_from_file_location("label_geometry_pilot", Path(__file__).resolve().parents[1] / "scripts/label_geometry_pilot.py")
PILOT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PILOT)


class GeometryTests(unittest.TestCase):
    def test_ocr_polygons_map_to_source_after_crop_and_anisotropic_resize(self):
        self.assertEqual(PILOT.to_original([[0, 0], [50, 25]], [100, 200, 400, 700], [0.5, 0.25]),
                         [[100, 200], [200, 300]])

    def test_neighbor_text_is_not_attributed_to_target(self):
        annotation = {"target_polygon": [[20, 0], [30, 0], [30, 10], [20, 10]],
                      "neighbor_polygons": [[[0, 0], [10, 0], [10, 10], [0, 10]]]}
        self.assertEqual(PILOT.region_for_line([[1, 1], [9, 1], [9, 9], [1, 9]], annotation), "neighbor_1")
        self.assertEqual(PILOT.region_for_line([[21, 1], [29, 1], [29, 9], [21, 9]], annotation), "target")
        self.assertEqual(PILOT.region_for_line([[41, 1], [49, 1], [49, 9], [41, 9]], annotation), "outside_annotations")

    def test_zero_scale_is_rejected(self):
        with self.assertRaises(ValueError):
            PILOT.to_original([[1, 1]], [0, 0, 10, 10], [0, 1])

    def test_existing_model_provenance_cannot_be_silently_replaced(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "model.json"
            original = {"files_sha256": {"weights": "old"}}
            path.write_text(json.dumps(original))
            with self.assertRaises(ValueError):
                PILOT.validate_existing_json(path, {"files_sha256": {"weights": "new"}})
            self.assertEqual(json.loads(path.read_text()), original)

    def test_changed_preparation_keeps_previous_crop_and_manifest(self):
        with tempfile.TemporaryDirectory() as directory:
            manifest = Path(directory) / "annotations.json"
            crop = Path(directory) / "crop.png"
            original = {"max_side": 1600, "source_sha256": "source"}
            manifest.write_text(json.dumps(original))
            crop.write_bytes(b"old crop")
            with self.assertRaises(ValueError):
                PILOT.save_prepared(manifest, {**original, "max_side": 800}, [(crop, b"new crop")])
            self.assertEqual(crop.read_bytes(), b"old crop")
            self.assertEqual(json.loads(manifest.read_text()), original)


if __name__ == "__main__":
    unittest.main()
