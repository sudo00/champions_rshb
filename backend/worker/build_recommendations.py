"""Build offline text vectors: .venv/bin/python -m backend.worker.build_recommendations."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "backend"))
from api.recommendations import RecommendationIndex

MODEL = "intfloat/multilingual-e5-small"
REVISION = "614241f622f53c4eeff9890bdc4f31cfecc418b3"


def sha(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model-dir", type=Path, default=ROOT / "weights/source/multilingual-e5-small")
    parser.add_argument("--output", type=Path, default=ROOT / "weights/recommendations-v1")
    parser.add_argument("--download", action="store_true")
    args = parser.parse_args()
    if args.output.exists():
        raise ValueError("Preserve released index: select a new --output")
    if args.download:
        from huggingface_hub import snapshot_download
        snapshot_download(MODEL, revision=REVISION, local_dir=args.model_dir,
                          allow_patterns=["config.json", "model.safetensors", "pytorch_model.bin", "tokenizer.json",
                                          "tokenizer_config.json", "special_tokens_map.json", "sentencepiece.bpe.model"])
    import numpy as np
    import torch
    import transformers
    from transformers import AutoModel, AutoTokenizer
    torch.set_num_threads(4)
    started = time.perf_counter()
    catalog = ROOT / "backend/catalog/catalog.jsonl"
    metadata = ROOT / "backend/catalog/recommendation_metadata.json"
    rows = [json.loads(line) for line in catalog.read_text().splitlines()]
    extras = json.loads(metadata.read_text())["items"]
    index = RecommendationIndex({r["slug"]: r for r in rows})
    documents = []
    for card in rows:
        extra = extras.get(card["slug"], {})
        attributes = index.cards[card["slug"]]
        description = extra.get("description") or card.get("description") or ""
        parts = ["Цвет: " + (attributes["color"] or ""), "Сладость: " + (attributes["sweetness"] or ""),
                 "Виноград: " + str(card.get("grapes") or ""), "Описание: " + description]
        if extra.get("food_pairing"):
            parts.append("Сочетания с едой: " + ", ".join(extra["food_pairing"]))
        temperature = extra.get("serving_temperature")
        if temperature:
            parts.append(f"Температура подачи: {temperature['min']}–{temperature['max']} °C")
        documents.append({"slug": card["slug"], "text": "query: " + "\n".join(parts), "supplement": extra})
    tokenizer = AutoTokenizer.from_pretrained(args.model_dir, local_files_only=True)
    model = AutoModel.from_pretrained(args.model_dir, local_files_only=True).eval()
    batches = []
    truncated = 0
    with torch.inference_mode():
        for start in range(0, len(documents), 16):
            texts = [d["text"] for d in documents[start:start + 16]]
            truncated += sum(len(t) > 512 for t in tokenizer(texts, truncation=False)["input_ids"])
            batch = tokenizer(texts, max_length=512, truncation=True, padding=True, return_tensors="pt")
            hidden = model(**batch).last_hidden_state
            hidden = hidden.masked_fill(~batch["attention_mask"][..., None].bool(), 0.)
            pooled = hidden.sum(dim=1) / batch["attention_mask"].sum(dim=1)[..., None]
            batches.append(torch.nn.functional.normalize(pooled, p=2, dim=1).cpu().numpy())
            if start % 320 == 0:
                print(f"Encoded {start + len(texts)}/{len(documents)}", flush=True)
    vectors = np.concatenate(batches).astype("<f4")
    assert vectors.shape == (len(rows), 384) and np.isfinite(vectors).all()
    args.output.mkdir(parents=True)
    vectors.tofile(args.output / "vectors.f32")
    (args.output / "documents.json").write_text(json.dumps(documents, ensure_ascii=False, indent=2) + "\n")
    manifest = dict(version="wine-text-embeddings-v1", model=MODEL, revision=REVISION, dimensions=384,
                    catalog_sha256=sha(catalog), metadata_sha256=sha(metadata),
                    vectors_sha256=sha(args.output / "vectors.f32"), documents_sha256=sha(args.output / "documents.json"),
                    slugs=[r["slug"] for r in rows], prefix="query: ", pooling="attention_mask_mean_l2", max_tokens=512,
                    source_code_sha256={"backend/worker/build_recommendations.py": sha(Path(__file__)),
                                        "backend/api/recommendations.py": sha(ROOT / "backend/api/recommendations.py")},
                    model_files_sha256={p.name: sha(p) for p in args.model_dir.iterdir() if p.is_file()},
                    versions=dict(torch=torch.__version__, transformers=transformers.__version__, numpy=np.__version__),
                    documents_truncated=truncated, seconds=time.perf_counter() - started)
    (args.output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"cards": len(rows), "seconds": manifest["seconds"], "truncated": truncated}), flush=True)


if __name__ == "__main__":
    main()
