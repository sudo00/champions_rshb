"""Read-only, precomputed text embeddings. No torch, GPU or network in the API."""
from array import array
from functools import lru_cache
import hashlib
import json
import logging
import math
import os
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]


class TextIndex:
    def __init__(self, directory: Path, cards: dict, catalog_sha256: str):
        manifest = json.loads((directory / "manifest.json").read_text())
        if manifest.get("version") != "wine-text-embeddings-v1" or manifest["catalog_sha256"] != catalog_sha256:
            raise ValueError("Text index schema/catalogue mismatch")
        metadata_raw = (ROOT / "backend/catalog/recommendation_metadata.json")
        # The Docker catalogue is at /app/catalog, next to WINE_CATALOG_PATH.
        metadata_path = Path(os.environ.get("WINE_CATALOG_PATH", str(ROOT / "backend/catalog/catalog.jsonl"))).parent / "recommendation_metadata.json"
        if not metadata_path.is_file():
            metadata_path = metadata_raw
        if hashlib.sha256(metadata_path.read_bytes()).hexdigest() != manifest["metadata_sha256"]:
            raise ValueError("Recommendation metadata changed; rebuild text index")
        self.metadata = json.loads(metadata_path.read_text())["items"]
        self.dim = int(manifest["dimensions"])
        self.model = manifest["model"]
        self.slugs = manifest["slugs"]
        if set(self.slugs) != set(cards) or len(self.slugs) != len(cards) or self.dim != 384:
            raise ValueError("Invalid text index shape/slug inventory")
        self.positions = {s: i for i, s in enumerate(self.slugs)}
        raw = (directory / "vectors.f32").read_bytes()
        if hashlib.sha256(raw).hexdigest() != manifest["vectors_sha256"] or len(raw) != 4 * self.dim * len(cards):
            raise ValueError("Text vector checksum/size mismatch")
        values = array("f"); values.frombytes(raw)
        if sys.byteorder != "little": values.byteswap()
        self.vectors = [values[i:i + self.dim] for i in range(0, len(values), self.dim)]
        for vector in self.vectors:
            norm = sum(x * x for x in vector)
            if not math.isfinite(norm) or abs(norm - 1.) > 1e-4:
                raise ValueError("Text vectors must be finite and normalized")

    @lru_cache(maxsize=128)
    def scores(self, anchor: str) -> dict[str, float]:
        if anchor not in self.positions:
            return {}
        query = self.vectors[self.positions[anchor]]
        return {slug: max(-1., min(1., sum(a * b for a, b in zip(query, vector))))
                for slug, vector in zip(self.slugs, self.vectors)}


def load_text_index(cards: dict, catalog_sha256: str) -> TextIndex | None:
    directory = Path(os.environ.get("WINE_RECOMMENDATIONS_DIR", str(ROOT / "weights/recommendations-v1")))
    try:
        return TextIndex(directory, cards, catalog_sha256)
    except (OSError, ValueError, KeyError, TypeError):
        logging.getLogger(__name__).warning("Text recommendation index unavailable; using attribute-only recommendations", exc_info=True)
        return None
