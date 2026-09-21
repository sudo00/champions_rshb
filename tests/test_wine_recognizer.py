import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch, MagicMock

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1]/"worker"))
from pipeline.wine_recognizer import VERSION, OCRProcess, WineRecognizer, crop_box, file_hash, load_image, select_region


class WineRecognizerTests(unittest.TestCase):
    def test_whole_bottle_replaces_central_nested_fragment(self):
        bottle = np.zeros((100, 100), dtype=np.uint8)
        bottle[10:85, 25:55] = 1
        fragment = np.zeros_like(bottle)
        fragment[20:50, 47:53] = 1
        candidates = [dict(mask=bottle, box=[25, 10, 55, 85], score=.97),
                      dict(mask=fragment, box=[47, 20, 53, 50], score=.75)]
        _, old = select_region(candidates, (100, 100), whole_object=False)
        mask, new = select_region(candidates, (100, 100), whole_object=True)
        self.assertEqual(old["selected_index"], 1)
        self.assertEqual(new["selected_index"], 0)
        np.testing.assert_array_equal(mask, bottle)

    def test_overlapping_boxes_do_not_suppress_adjacent_masks(self):
        left = np.zeros((100, 100), dtype=np.uint8)
        left[10:90, 15:45] = 1
        right = np.zeros_like(left)
        right[30:60, 45:55] = 1
        candidates = [dict(mask=left, box=[10, 5, 60, 95], score=.98),
                      dict(mask=right, box=[45, 30, 55, 60], score=.75)]
        _, detail = select_region(candidates, (100, 100), whole_object=True)
        self.assertEqual(detail["suppressed_fragments"], [])

    def test_uncertain_larger_mask_cannot_suppress_confident_small_object(self):
        large = np.ones((100, 100), dtype=np.uint8)
        small = np.zeros_like(large)
        small[30:60, 45:55] = 1
        _, detail = select_region([dict(mask=large, box=[0, 0, 100, 100], score=.5),
                                  dict(mask=small, box=[45, 30, 55, 60], score=.95)],
                                 (100, 100), whole_object=True)
        self.assertEqual(detail["suppressed_fragments"], [])

    def test_empty_detection_has_no_target(self):
        mask, detail = select_region([], (100, 100), whole_object=True)
        self.assertIsNone(mask)
        self.assertIsNone(detail["selected_index"])

    def test_confident_whole_object_beats_disjoint_central_sliver(self):
        bottle = np.zeros((100, 100), dtype=np.uint8)
        bottle[10:85, 25:48] = 1
        sliver = np.zeros_like(bottle)
        sliver[20:50, 49:51] = 1
        candidates = [dict(mask=bottle, box=[25, 10, 55, 85], score=.97),
                      dict(mask=sliver, box=[47, 20, 53, 50], score=.75)]
        _, old = select_region(candidates, (100, 100), whole_object=False)
        _, new = select_region(candidates, (100, 100), whole_object=True)
        self.assertEqual(old["selected_index"], 1)
        self.assertEqual(new["selected_index"], 0)
        self.assertEqual(new["suppressed_fragments"], [])

    def test_gpu_ocr_preserves_device_visibility_and_cpu_hides_gpu(self):
        for device in ("cpu", "gpu:0"):
            process = MagicMock()
            process.poll.return_value = 0
            with patch.dict("os.environ", {"CUDA_VISIBLE_DEVICES": "2"}), \
                 patch("pipeline.wine_recognizer.subprocess.Popen", return_value=process) as popen, \
                 patch.object(OCRProcess, "receive", return_value={"ready": True, "device": device}):
                instance = OCRProcess(Path(".venv-ocr-gpu/bin/python"), Path("models"), 4, device=device)
                try:
                    self.assertEqual(popen.call_args.args[0][-1], device)
                    env = popen.call_args.kwargs["env"]
                    self.assertEqual(env["CUDA_VISIBLE_DEVICES"], "" if device == "cpu" else "2")
                    self.assertEqual(env["FLAGS_allocator_strategy"], "auto_growth")
                finally:
                    instance.close()

    def test_bad_ocr_device_fails_before_loading_resources(self):
        with self.assertRaisesRegex(ValueError, "ocr_device"):
            WineRecognizer("missing", ocr_python="unused", ocr_device="cuda:0")

    def test_bad_memory_policy_fails_before_loading_resources(self):
        with self.assertRaisesRegex(ValueError, "memory_policy"):
            WineRecognizer("missing", ocr_python="unused", memory_policy="guess")

    def test_ocr_cache_policy_is_explicit_in_child_environment(self):
        for release in (False, True):
            process = MagicMock()
            process.poll.return_value = 0
            with patch("pipeline.wine_recognizer.subprocess.Popen", return_value=process) as popen, \
                 patch.object(OCRProcess, "receive", return_value={"ready": True}):
                instance = OCRProcess(Path("python"), Path("models"), 4, release_cache=release)
                try:
                    self.assertEqual(popen.call_args.kwargs["env"]["WINE_OCR_RELEASE_CACHE"], "1" if release else "0")
                finally:
                    instance.close()

    def test_exif_coordinates_and_transparency(self):
        image = Image.new("RGB", (10, 20), "red")
        exif = image.getexif()
        exif[274] = 6
        data = io.BytesIO()
        image.save(data, format="JPEG", exif=exif)
        self.assertEqual(load_image(data.getvalue()).size, (20, 10))
        transparent = Image.new("RGBA", (4, 4), (255, 0, 0, 0))
        self.assertEqual(load_image(transparent).getpixel((0, 0)), (255, 255, 255))
        self.assertEqual(transparent.mode, "RGBA")

    def test_invalid_and_oversized_input(self):
        with self.assertRaises(Exception):
            load_image(b"not an image")
        with self.assertRaises(ValueError):
            load_image(Image.new("RGB", (20, 20)), max_pixels=100)

    def test_region_box_clips_to_image_and_scales_mask(self):
        mask = np.zeros((10, 10), dtype=np.uint8)
        mask[0:5, 0:5] = 1
        self.assertEqual(crop_box(mask, (100, 200)), [0, 0, 53, 103])
        with self.assertRaises(ValueError):
            crop_box(np.zeros((10, 10), dtype=np.uint8), (100, 100))

    def test_bundle_corruption_fails_before_loading_models(self):
        with tempfile.TemporaryDirectory() as root:
            p = Path(root)
            (p/"weights").write_bytes(b"original")
            checksum = file_hash(p/"weights")
            (p/"weights").write_bytes(b"changed")
            (p/"manifest.json").write_text(json.dumps({"version": VERSION, "files_sha256": {"weights": checksum}}))
            with self.assertRaisesRegex(ValueError, "integrity mismatch"):
                WineRecognizer(p, ocr_python="unused")

    def test_ocr_protocol_handles_logs_and_buffered_messages(self):
        # Real pipe: two JSON messages and a dependency log may arrive in one read.
        instance = OCRProcess.__new__(OCRProcess)
        instance.timeout = 3
        instance.buffer = b""
        instance.stderr = tempfile.TemporaryFile(mode="w+")
        instance.process = subprocess.Popen([sys.executable, "-u", "-c",
            'print(\'dependency log\\nWINE_OCR_JSON:{"ready":true}\\nWINE_OCR_JSON:{"lines":[]}\', flush=True)'],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=instance.stderr, bufsize=0)
        try:
            self.assertEqual(instance.receive(), {"ready": True})
            self.assertEqual(instance.receive(), {"lines": []})
            with self.assertRaises(RuntimeError):
                instance.receive()
        finally:
            instance.close()

    def test_top_k_validation_does_not_run_inference(self):
        instance = WineRecognizer.__new__(WineRecognizer)
        for limit in (0, 11):
            with self.assertRaises(ValueError):
                instance.predict(b"", top_k=limit)


if __name__ == "__main__":
    unittest.main()
