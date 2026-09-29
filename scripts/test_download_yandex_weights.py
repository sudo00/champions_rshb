"""Checks for Yandex Disk unpacking without network or real weights."""
import hashlib
import io
import os
import sys
import tarfile
import tempfile
import unittest
import unittest.mock
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from download_yandex_weights import extraction_root, load_env_file, looks_like_archive, resource_url, safe_extract


class DownloadWeightsTest(unittest.TestCase):
    def test_env_file_skips_comments(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "yandex.env"
            path.write_text("# comment\n\nYANDEX_RECOGNIZER_URL=https://disk.yandex.ru/d/abc\n")
            self.assertEqual(
                load_env_file(path),
                {"YANDEX_RECOGNIZER_URL": "https://disk.yandex.ru/d/abc"},
            )

    def test_url_prefers_environment_over_file_and_manifest(self):
        item = {"url": "https://disk.yandex.ru/d/from-manifest"}
        with unittest.mock.patch.dict(os.environ, {"YANDEX_RECOGNIZER_URL": "https://disk.yandex.ru/d/env"}):
            self.assertEqual(
                resource_url("YANDEX_RECOGNIZER_URL", {"YANDEX_RECOGNIZER_URL": "https://disk.yandex.ru/d/file"}, item),
                "https://disk.yandex.ru/d/env",
            )
        with unittest.mock.patch.dict(os.environ):
            os.environ.pop("YANDEX_RECOGNIZER_URL", None)
            self.assertEqual(
                resource_url("YANDEX_RECOGNIZER_URL", {"YANDEX_RECOGNIZER_URL": "https://disk.yandex.ru/d/file"}, item),
                "https://disk.yandex.ru/d/file",
            )
            self.assertEqual(resource_url("YANDEX_RECOGNIZER_URL", {}, item), "https://disk.yandex.ru/d/from-manifest")
            self.assertIsNone(resource_url("YANDEX_RECOGNIZER_URL", {}, {"url": None}))

    def test_archive_with_repo_prefix_unpacks_at_root(self):
        self.assertEqual(
            extraction_root(["weights/recommendations-v2/manifest.json"], "weights/recommendations-v2"),
            "repo",
        )
        self.assertEqual(extraction_root(["images/a.jpg"], "data/deployment/catalog-images-v2"), "dest")

    def test_extract_keeps_members_inside_destination(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive_path = root / "sample.tar"
            _write_tar(archive_path, {"images/a.txt": b"ok"})
            destination = root / "out"
            safe_extract(archive_path, destination)
            self.assertEqual((destination / "images/a.txt").read_bytes(), b"ok")
            self.assertTrue(looks_like_archive(archive_path))
            digest = hashlib.sha256(archive_path.read_bytes()).hexdigest()
            self.assertEqual(len(digest), 64)

    def test_extract_rejects_path_escape(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive_path = root / "bad.tar"
            _write_tar(archive_path, {"../outside.txt": b"no"})
            with self.assertRaises(tarfile.FilterError):
                safe_extract(archive_path, root / "out")
            self.assertFalse((root / "outside.txt").exists())

    def test_zip_and_html_are_not_archives(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            zip_path = root / "folder.zip"
            zip_path.write_bytes(b"PK\x03\x04not-a-tar")
            html_path = root / "page.html"
            html_path.write_bytes(b"<!DOCTYPE html>")
            self.assertFalse(looks_like_archive(zip_path))
            self.assertFalse(looks_like_archive(html_path))


def _write_tar(path: Path, files: dict[str, bytes]) -> None:
    with tarfile.open(path, "w") as archive:
        for name, payload in files.items():
            info = tarfile.TarInfo(name)
            info.size = len(payload)
            archive.addfile(info, io.BytesIO(payload))


if __name__ == "__main__":
    unittest.main()
