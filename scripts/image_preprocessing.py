"""Reproducible client-side resize candidates; no production default is implied."""
from __future__ import annotations

from io import BytesIO
import time

import cv2
import numpy as np
from PIL import Image, ImageOps


PROFILES = {
    "original": None,
    "jpeg90_full": dict(max_side=None, method="lanczos", quality=90),
    "lanczos2560_q90": dict(max_side=2560, method="lanczos", quality=90),
    "lanczos2048_q90": dict(max_side=2048, method="lanczos", quality=90),
    "lanczos1600_q90": dict(max_side=1600, method="lanczos", quality=90),
    "lanczos1280_q90": dict(max_side=1280, method="lanczos", quality=90),
    "area2048_q90": dict(max_side=2048, method="area", quality=90),
    "bilinear2048_q90": dict(max_side=2048, method="bilinear", quality=90),
    "lanczos2048_q80": dict(max_side=2048, method="lanczos", quality=80),
}


def prepare_image(raw: bytes, profile: str) -> tuple[bytes, dict]:
    """Apply all EXIF transforms, retain aspect ratio, never crop or upscale.

    Output JPEG has physically oriented pixels and no copied EXIF. Coordinates
    returned by the recognition API consequently refer to this output image.
    """
    started = time.perf_counter()
    config = PROFILES[profile]
    if config is None:
        return raw, {"seconds": 0., "bytes": len(raw), "transformed": False}
    with Image.open(BytesIO(raw)) as opened:
        if opened.width * opened.height > 25_000_000 or getattr(opened, "n_frames", 1) != 1:
            raise ValueError("Expected a single image up to 25 megapixels")
        image = ImageOps.exif_transpose(opened).convert("RGBA")
        background = Image.new("RGBA", image.size, "white")
        background.alpha_composite(image)
        image = background.convert("RGB")
    original_size = image.size
    side = config["max_side"]
    if side and max(image.size) > side:
        ratio = side / max(image.size)
        size = tuple(max(1, round(value * ratio)) for value in image.size)
        if config["method"] == "area":
            image = Image.fromarray(cv2.resize(np.asarray(image), size, interpolation=cv2.INTER_AREA))
        else:
            method = {"lanczos": Image.Resampling.LANCZOS, "bilinear": Image.Resampling.BILINEAR}[config["method"]]
            image = image.resize(size, resample=method)
    stream = BytesIO()
    image.save(stream, format="JPEG", quality=config["quality"], subsampling=2, optimize=False)
    payload = stream.getvalue()
    return payload, {"seconds": time.perf_counter()-started, "bytes": len(payload),
                     "transformed": True, "original_oriented_size": list(original_size),
                     "output_size": list(image.size), "scale_xy": [image.width/original_size[0], image.height/original_size[1]],
                     **config, "jpeg_subsampling": "4:2:0"}
