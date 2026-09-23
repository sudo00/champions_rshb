"""Prepare a phone scan (default: EXIF -> Lanczos 2560 -> JPEG 90).

Evaluation clients must send original files directly, without this step.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

from image_preprocessing import PROFILES, prepare_image


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("image", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--profile", choices=[p for p in PROFILES if p != "original"],
                        default="lanczos2560_q90",
                        help="Phone preprocessing profile (default: lanczos2560_q90)")
    args = parser.parse_args()
    metadata = args.output.with_suffix(args.output.suffix + ".json")
    if args.output.exists() or metadata.exists():
        raise FileExistsError("Choose fresh output paths; source images are never overwritten")
    data, info = prepare_image(args.image.read_bytes(), args.profile)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("xb") as stream:
        stream.write(data)
    with metadata.open("x") as stream:
        json.dump({"profile": args.profile, **info}, stream, ensure_ascii=False, indent=2)
        stream.write("\n")
    print(json.dumps({"image": str(args.output), "metadata": str(metadata), **info}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
