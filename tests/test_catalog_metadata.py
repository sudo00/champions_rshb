import copy
import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from catalog_metadata import apply_metadata, load_metadata_decisions


class MetadataReviewTests(unittest.TestCase):
    def decision(self, updates):
        return {"slug": "wine", "updates": updates, "status": "applied", "user_decision": "Белое",
                "user_basis": "Этикетка", "interpretation": "User decision", "confirmed_attributes": {},
                "open_questions": [], "accepted_disagreements": {}}

    def test_applies_only_reviewed_fields_and_retains_originals(self):
        row = {"slug": "wine", "category": "Красное", "reference_path": "image.webp"}
        apply_metadata(row, self.decision({"category": {"before": "Красное", "after": "Белое"}}))
        self.assertEqual(row["category"], "Белое")
        self.assertEqual(row["original_metadata"], {"category": "Красное"})
        self.assertEqual(row["reference_path"], "image.webp")

    def test_identity_and_images_cannot_be_changed(self):
        for field in ["slug", "reference_path"]:
            row = {"slug": "wine", "reference_path": "image.webp"}
            with self.assertRaises(ValueError):
                apply_metadata(row, self.decision({field: {"before": row[field], "after": "different"}}))

    def test_invalid_review_fails_before_any_mutation(self):
        row = {"slug": "wine", "title": "Old", "category": "Красное"}
        original = copy.deepcopy(row)
        with self.assertRaises(ValueError):
            apply_metadata(row, self.decision({"title": {"before": "Old", "after": "New"},
                                               "category": {"before": "Белое", "after": "Розовое"}}))
        self.assertEqual(row, original)

    def test_unknown_shade_is_not_invented(self):
        row = {"slug": "wine", "color_shade": "Розовый"}
        apply_metadata(row, self.decision({"color_shade": {"before": "Розовый", "after": None}}))
        self.assertIsNone(row["color_shade"])

    def test_changed_notes_block_stale_decisions(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td); (root / "data/catalog").mkdir(parents=True)
            notes = root / "data/notes.md"; notes.write_text("reviewed")
            doc = {"inputs": [{"path": "data/notes.md", "sha256": hashlib.sha256(notes.read_bytes()).hexdigest()}],
                   "decisions": []}
            (root / "data/catalog/metadata_decisions.json").write_text(json.dumps(doc))
            self.assertEqual(load_metadata_decisions(root), doc)
            notes.write_text("new decision")
            with self.assertRaises(ValueError):
                load_metadata_decisions(root)


if __name__ == "__main__":
    unittest.main()
