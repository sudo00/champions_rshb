"""Package only versioned inference assets; deploy with the matching Git checkout."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import tarfile

ROOT = Path(__file__).resolve().parents[1]


def file_hash(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle", type=Path,
                        default=ROOT / "weights/wine-recognizer-v5-worker-layout-release")
    parser.add_argument("--output", type=Path,
                        default=ROOT / "weights/artifacts/wine-recognizer-v5-worker-layout.tar")
    args = parser.parse_args()
    manifest = json.loads((args.bundle / "manifest.json").read_text())
    for name, expected in manifest["files_sha256"].items():
        if file_hash(args.bundle / name) != expected:
            raise ValueError("Bundle changed: " + name)
    for name, expected in manifest["code_sha256"].items():
        if file_hash(ROOT / name) != expected:
            raise ValueError("Code changed: " + name)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    # Exclusive creation preserves existing releases. No photos, tests or source code.
    with tarfile.open(args.output, "x", dereference=True) as archive:
        for relative in [*manifest["files_sha256"], "manifest.json"]:
            archive.add(args.bundle / relative,
                        arcname=f"weights/{args.bundle.name}/{relative}", recursive=False)
    checksum = file_hash(args.output)
    args.output.with_suffix(".tar.sha256").write_text(checksum + "  " + args.output.name + "\n")
    print(json.dumps({"path": str(args.output), "bytes": args.output.stat().st_size,
                      "sha256": checksum}, indent=2))


if __name__ == "__main__":
    main()
