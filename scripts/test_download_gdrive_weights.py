"""Checks for Google Drive unpacking without network or real weights."""
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
from download_gdrive_weights import (
    confirmation_url, extraction_root, gdrive_file_id, load_env_file, looks_like_archive,
    resource_url, safe_extract,
)

WARNING = """<!DOCTYPE html><html><body>
<form id="download-form" action="https://drive.usercontent.google.com/download" method="get">
<input type="submit" id="uc-download-link" value="Download anyway"/>
<input type="hidden" name="id" value="FILE">
<input type="hidden" name="export" value="download">
<input type="hidden" name="confirm" value="t">
<input type="hidden" name="uuid" value="uuid-1">
</form>
<p class="uc-warning-caption">Google Drive can't scan this file for viruses.</p>
</body></html>"""


class DownloadWeightsTest(unittest.TestCase):
    def test_env_file_skips_comments(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "gdrive.env"
            path.write_text("# comment\n\nGDRIVE_RECOGNIZER_URL=https://drive.google.com/file/d/abc/view\n")
            self.assertEqual(
                load_env_file(path),
                {"GDRIVE_RECOGNIZER_URL": "https://drive.google.com/file/d/abc/view"},
            )

    def test_url_prefers_environment_over_file_and_manifest(self):
        item = {"url": "https://drive.google.com/file/d/from-manifest/view"}
        with unittest.mock.patch.dict(os.environ, {"GDRIVE_RECOGNIZER_URL": "https://drive.google.com/file/d/env/view"}):
            self.assertEqual(
                resource_url("GDRIVE_RECOGNIZER_URL", {"GDRIVE_RECOGNIZER_URL": "https://drive.google.com/file/d/file/view"}, item),
                "https://drive.google.com/file/d/env/view",
            )
        with unittest.mock.patch.dict(os.environ):
            os.environ.pop("GDRIVE_RECOGNIZER_URL", None)
            self.assertEqual(
                resource_url("GDRIVE_RECOGNIZER_URL", {"GDRIVE_RECOGNIZER_URL": "https://drive.google.com/file/d/file/view"}, item),
                "https://drive.google.com/file/d/file/view",
            )
            self.assertIsNone(resource_url("GDRIVE_RECOGNIZER_URL", {}, {"url": None}))

    def test_file_id_from_share_link(self):
        self.assertEqual(
            gdrive_file_id("https://drive.google.com/file/d/1UxZS4mxsFoLbzn0kYlqVCFYVDylGH6eu/view?usp=drive_link"),
            "1UxZS4mxsFoLbzn0kYlqVCFYVDylGH6eu",
        )
        self.assertEqual(
            gdrive_file_id("https://drive.google.com/uc?id=1IOhFNwSN3DrTZGStjkQt8yM3HswRywyJ&export=download"),
            "1IOhFNwSN3DrTZGStjkQt8yM3HswRywyJ",
        )
        with self.assertRaises(SystemExit):
            gdrive_file_id("https://docs.google.com/document/d/12pu8f6JP6tPiznArGW3J53WpngQIiMYc/edit")

    def test_confirmation_form_becomes_download_url(self):
        url = confirmation_url(WARNING)
        self.assertTrue(url.startswith("https://drive.usercontent.google.com/download?"))
        self.assertIn("id=FILE", url)
        self.assertIn("confirm=t", url)
        self.assertIn("uuid=uuid-1", url)
        self.assertNotIn("Download+anyway", url)

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
