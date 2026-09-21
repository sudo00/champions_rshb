"""Persistent CPU/GPU PaddleOCR subprocess. Internal JSON-lines protocol."""
from __future__ import annotations

import base64
import io
import json
import os
from pathlib import Path
import sys

PREFIX = "WINE_OCR_JSON:"
DETECTOR = "PP-OCRv5_server_det"
RECOGNIZERS = {"russian": "eslav_PP-OCRv5_mobile_rec", "latin": "latin_PP-OCRv5_mobile_rec"}


def send(value: dict) -> None:
    print(PREFIX + json.dumps(value, ensure_ascii=False), flush=True)


def main() -> None:
    models = Path(sys.argv[1])
    device = sys.argv[3] if len(sys.argv) > 3 else "cpu"
    release_cache = os.environ.get("WINE_OCR_RELEASE_CACHE", "0") == "1"
    os.environ["PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK"] = "True"
    if device == "cpu":
        os.environ["CUDA_VISIBLE_DEVICES"] = ""
    # Paddle's allocator must grow on demand alongside the resident Torch models.
    os.environ.setdefault("FLAGS_allocator_strategy", "auto_growth")
    import cv2
    import numpy as np
    from PIL import Image
    from paddleocr import PaddleOCR
    import paddle
    if device.startswith("gpu"):
        if not paddle.is_compiled_with_cuda() or paddle.device.cuda.device_count() < 1:
            raise RuntimeError("GPU OCR requested but Paddle CUDA is unavailable")
        paddle.set_device(device)
    cv2.setNumThreads(1)
    engines = {}
    for language, model in RECOGNIZERS.items():
        engines[language] = PaddleOCR(
            text_detection_model_name=DETECTOR, text_detection_model_dir=str(models / DETECTOR),
            text_recognition_model_name=model, text_recognition_model_dir=str(models / model),
            use_doc_orientation_classify=False, use_doc_unwarping=False, use_textline_orientation=False,
            text_rec_score_thresh=0., text_det_limit_side_len=1600, text_det_limit_type="max",
            device=device, cpu_threads=int(sys.argv[2]), enable_mkldnn=device == "cpu")
    if release_cache and device.startswith("gpu"):
        paddle.device.cuda.empty_cache()
    send({"ready": True, "device": device, "release_cache": release_cache, "paddle_version": paddle.__version__,
          "cuda_compiled": paddle.is_compiled_with_cuda()})
    for line in sys.stdin:
        try:
            request = json.loads(line)
            gpu = device.startswith("gpu")
            if gpu:
                paddle.device.cuda.reset_max_memory_allocated()
                paddle.device.cuda.reset_max_memory_reserved()
            rows = []
            for view in request["views"]:
                pixels = np.asarray(Image.open(io.BytesIO(base64.b64decode(view["png"]))).convert("RGB"))[:, :, ::-1].copy()
                for language, engine in engines.items():
                    prediction = list(engine.predict(pixels))
                    if len(prediction) != 1:
                        raise ValueError("Expected one OCR prediction")
                    data = prediction[0].json
                    data = json.loads(data) if isinstance(data, str) else data
                    data = data.get("res", data)
                    if not len(data["rec_texts"]) == len(data["rec_scores"]) == len(data["rec_polys"]):
                        raise ValueError("Misaligned OCR output")
                    for text, score, polygon in zip(data["rec_texts"], data["rec_scores"], data["rec_polys"]):
                        rows.append(dict(text=text, confidence=float(score), source=view["name"]+":"+language,
                                         view=view["name"], polygon_input=np.asarray(polygon).tolist()))
            memory = {}
            if gpu:
                paddle.device.cuda.synchronize()
                if release_cache:
                    paddle.device.cuda.empty_cache()
                memory = dict(allocated=paddle.device.cuda.memory_allocated(),
                              reserved=paddle.device.cuda.memory_reserved(),
                              peak_allocated=paddle.device.cuda.max_memory_allocated(),
                              peak_reserved=paddle.device.cuda.max_memory_reserved())
            send({"lines": rows, "gpu_memory_bytes": memory})
        except Exception as exc:
            send({"error": f"{type(exc).__name__}: {exc}"})


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        send({"error": f"{type(exc).__name__}: {exc}"})
        raise
