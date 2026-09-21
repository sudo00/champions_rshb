"""Checks for coordinate/provenance errors without downloading OCR models."""

from pathlib import Path
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from ocr_pilot import normalized_image, observations, render_ocr_evidence, stable_key


class OcrPilotTests(unittest.TestCase):
    def test_transparent_background_is_white_and_input_is_preserved(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "reference.png"
            image = Image.new("RGBA", (20, 40), (0, 0, 0, 0))
            image.putpixel((10, 20), (20, 30, 40, 255))
            image.save(path)
            before = path.read_bytes()
            normalized, transform = normalized_image(path, max_side=40)
            self.assertEqual(normalized.getpixel((0, 0)), (255, 255, 255))
            self.assertEqual(normalized.getpixel((10, 20)), (20, 30, 40))
            self.assertEqual(transform["scale_x"], 1.0)
            self.assertEqual(path.read_bytes(), before)

    def test_exif_orientation_is_applied_before_recording_coordinates(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "reference.jpg"
            image = Image.new("RGB", (20, 40), "white")
            exif = Image.Exif()
            exif[274] = 6
            image.save(path, exif=exif)
            normalized, transform = normalized_image(path, max_side=80)
            self.assertEqual(transform["oriented_original_size"], [40, 20])
            self.assertEqual(normalized.size, (80, 40))
            self.assertEqual(transform["scale_x"], 2.0)

    def test_text_and_polygon_mismatch_is_not_silently_truncated(self):
        with self.assertRaises(ValueError):
            observations({"res": {"rec_texts": ["Шардоне"], "rec_scores": [], "rec_polys": []}})

    def test_low_confidence_observation_is_preserved_without_verdict(self):
        polygon = [[1, 2], [30, 2], [30, 12], [1, 12]]
        result = observations({"res": {"rec_texts": ["Шардоне"], "rec_scores": [0.1],
                                       "rec_polys": [polygon]}})
        self.assertEqual(result, [{"text": "Шардоне", "confidence": 0.1, "polygon": polygon}])

    def test_seeded_order_is_independent_of_input_order(self):
        entries = ["c", "b", "a"]
        forward = sorted(entries, key=lambda value: stable_key(value, 17))
        backward = sorted(reversed(entries), key=lambda value: stable_key(value, 17))
        self.assertEqual(forward, backward)

    def evidence_result(self, text="Шардоне", width=640):
        return {"preprocessing": {"ocr_size": [width, 480],
                                  "bbox_coordinate_system": "normalized_ocr_image_pixels"},
                "lines": [{"text": text, "confidence": 0.92,
                           "polygon": [[12.5, 30], [190, 31.5], [189.25, 58], [11, 55]]}]}

    def test_overlay_preserves_polygon_and_uses_same_image_coordinate_system(self):
        figure, panels = render_ocr_evidence("prepared/photo.png", {"russian": self.evidence_result()})
        root = ET.fromstring("<div>" + figure + panels + "</div>")
        svg = root.find("svg")
        self.assertEqual(svg.attrib["viewBox"], "0 0 640 480")
        self.assertEqual(svg.attrib["preserveAspectRatio"], "xMidYMid meet")
        self.assertEqual(svg.find("image").attrib["width"], "640")
        self.assertEqual(svg.find("image").attrib["height"], "480")
        self.assertEqual(svg.find(".//polygon").attrib["points"], "12.5,30 190,31.5 189.25,58 11,55")
        keys = [node.attrib["data-region-key"] for node in root.iter() if "data-region-key" in node.attrib]
        self.assertEqual(keys, ["russian:1", "russian:1"])

    def test_overlay_escapes_recognized_text_and_image_path(self):
        text = '</title><script>alert("wine")</script>&'
        figure, panels = render_ocr_evidence('prepared/a"b&c.png', {"russian": self.evidence_result(text)})
        root = ET.fromstring("<div>" + figure + panels + "</div>")
        self.assertIsNone(root.find(".//script"))
        self.assertEqual(root.find(".//image").attrib["href"], 'prepared/a"b&c.png')
        self.assertIn(text, root.find(".//title").text)
        self.assertEqual(root.find(".//td[@class='ocr-text']").text, text)

    def test_overlay_rejects_profiles_with_different_dimensions(self):
        with self.assertRaisesRegex(ValueError, "share"):
            render_ocr_evidence("photo.png", {"russian": self.evidence_result(),
                                               "latin": self.evidence_result(width=800)})

    def test_overlay_rejects_nonfinite_polygon_coordinates(self):
        result = self.evidence_result()
        result["lines"][0]["polygon"][0][0] = float("nan")
        with self.assertRaisesRegex(ValueError, "finite"):
            render_ocr_evidence("photo.png", {"russian": result})


if __name__ == "__main__":
    unittest.main()
