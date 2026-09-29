"""Download deployment archives from public Yandex Disk links and unpack them.

Links come from build_env/yandex.env, environment variables, or the url fields
in build_env/artifacts.json. A public page link is not a direct file URL:
the script exchanges it for a temporary href via the Yandex Disk public API.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tarfile
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "build_env/artifacts.json"
ENV_FILE = ROOT / "build_env/yandex.env"
API = "https://cloud-api.yandex.net/v1/disk/public/resources/download"

# archive: path relative to the repo. dest: directory that must appear after unpack.
# ready: marker that the unpack finished. Recognizer manifest is written last.
RESOURCES = (
    {
        "key": "recognizer",
        "env": "YANDEX_RECOGNIZER_URL",
        "title": "веса распознавания",
    },
    {
        "key": "catalog_images",
        "env": "YANDEX_CATALOG_IMAGES_URL",
        "title": "изображения каталога",
    },
    {
        "key": "recommendations",
        "env": "YANDEX_RECOMMENDATIONS_URL",
        "title": "текстовый индекс рекомендаций",
    },
)


def archive_rel(key: str, item: dict) -> str:
    archive = item["archive"]
    if key == "catalog_images" and "/" not in archive:
        return "data/deployment/" + archive
    return archive


def dest_rel(key: str, item: dict) -> str:
    if key == "recognizer":
        return item["bundle_dir"]
    return item["directory"]


def ready_path(key: str, item: dict) -> Path:
    if key == "catalog_images":
        return ROOT / item["directory"] / "images"
    if key == "recognizer":
        return ROOT / item["bundle_dir"] / "manifest.json"
    return ROOT / item["directory"] / "manifest.json"


def is_ready(path: Path) -> bool:
    if path.is_file():
        return True
    if path.is_dir():
        return any(path.iterdir())
    return False


def load_env_file(path: Path) -> dict[str, str]:
    if not path.is_file():
        return {}
    values: dict[str, str] = {}
    for raw in path.read_text().splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip('"').strip("'")
    return values


def resource_url(env_name: str, file_values: dict[str, str], item: dict) -> str | None:
    for source in (os.environ.get(env_name), file_values.get(env_name), item.get("url")):
        if source and str(source).strip() and str(source).strip().lower() != "null":
            return str(source).strip()
    return None


def yandex_href(public_url: str) -> str:
    host = urllib.parse.urlparse(public_url).hostname or ""
    if host.endswith("downloader.disk.yandex.ru") or host.endswith("downloader.disk.yandex.com"):
        return public_url
    query = urllib.parse.urlencode({"public_key": public_url})
    request = urllib.request.Request(API + "?" + query, headers={"User-Agent": "wine-deploy/1.0"})
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            payload = json.load(response)
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise SystemExit(
            f"Яндекс Диск отклонил ссылку ({exc.code}): {public_url}\n{detail}\n"
            "Нужна публичная ссылка на файл, вида https://disk.yandex.ru/d/..."
        ) from exc
    href = payload.get("href")
    if not href:
        raise SystemExit(f"В ответе Яндекс Диска нет ссылки на скачивание: {public_url}")
    return href


def curl_download(href: str, partial: Path) -> None:
    partial.parent.mkdir(parents=True, exist_ok=True)
    command = [
        "curl", "--fail", "--location", "--retry", "5", "--retry-all-errors",
        "--retry-delay", "2", "--output", str(partial),
    ]
    if partial.exists() and partial.stat().st_size > 0:
        resumed = subprocess.run(command + ["--continue-at", "-", href])
        if resumed.returncode == 0:
            return
        partial.unlink()
    subprocess.run(command + [href], check=True)


def file_sha256(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def looks_like_archive(path: Path) -> bool:
    with path.open("rb") as stream:
        magic = stream.read(262)
    if magic.startswith((b"PK\x03\x04", b"<!DOC", b"<html", b"<HTML", b"<!doctype", b"{")):
        return False
    if magic[:2] == b"\x1f\x8b" or (len(magic) >= 262 and magic[257:262] == b"ustar"):
        return True
    try:
        with tarfile.open(path, "r:*") as archive:
            archive.getmembers()
    except tarfile.TarError:
        return False
    return True


def archive_members(path: Path) -> list[str]:
    with tarfile.open(path, "r:*") as archive:
        return [member.name for member in archive.getmembers()]


def extraction_root(members: list[str], dest: str) -> str:
    """'repo' when names already include dest; otherwise unpack inside dest."""
    prefix = dest.strip("/") + "/"
    names = [name.lstrip("./") for name in members if name and name not in (".", "./")]
    if not names:
        raise ValueError("пустой архив")
    if all(name == dest.strip("/") or name.startswith(prefix) for name in names):
        return "repo"
    return "dest"


def safe_extract(archive_path: Path, destination: Path) -> None:
    destination.mkdir(parents=True, exist_ok=True)
    with tarfile.open(archive_path, "r:*") as archive:
        archive.extractall(destination, filter="data")


def download_archive(public_url: str, dest: Path, expected_bytes: int | None) -> None:
    partial = Path(str(dest) + ".partial")
    error: Exception | None = None
    for _ in range(3):
        try:
            curl_download(yandex_href(public_url), partial)
            error = None
            break
        except subprocess.CalledProcessError as exc:
            error = exc
    if error is not None:
        raise SystemExit(f"Не удалось скачать {public_url}") from error
    if expected_bytes and partial.stat().st_size != expected_bytes:
        partial.unlink(missing_ok=True)
        raise SystemExit(
            f"Размер {dest.name} не совпал: ожидалось {expected_bytes} байт"
        )
    if not looks_like_archive(partial):
        partial.unlink(missing_ok=True)
        raise SystemExit(
            f"{dest.name}: это не tar/tar.gz. Ссылка должна вести на файл архива, не на папку."
        )
    partial.replace(dest)


def verify_sha256(path: Path, expected: str) -> None:
    actual = file_sha256(path)
    if actual != expected:
        path.unlink(missing_ok=True)
        raise SystemExit(f"SHA-256 {path.name} не совпал.\nожидалось {expected}\nполучено  {actual}")


def unpack(archive_path: Path, dest: str) -> None:
    members = archive_members(archive_path)
    if extraction_root(members, dest) == "repo":
        safe_extract(archive_path, ROOT)
    else:
        safe_extract(archive_path, ROOT / dest)


def ensure_resource(spec: dict, item: dict, file_values: dict[str, str], force: bool) -> None:
    key = spec["key"]
    marker = ready_path(key, item)
    if is_ready(marker) and not force:
        print(f"{spec['title']}: уже на месте ({marker.relative_to(ROOT)})")
        return
    archive_path = ROOT / archive_rel(key, item)
    expected = item.get("sha256")
    expected_bytes = item.get("bytes")
    if archive_path.is_file() and not force and (not expected or file_sha256(archive_path) == expected):
        print(f"{spec['title']}: архив уже скачан, распаковка")
        unpack(archive_path, dest_rel(key, item))
        if not is_ready(marker):
            raise SystemExit(f"После распаковки нет {marker.relative_to(ROOT)}")
        return
    if archive_path.is_file() and not force:
        print(f"{spec['title']}: контрольная сумма архива не совпала, скачиваю заново")
        archive_path.unlink()
    url = resource_url(spec["env"], file_values, item)
    if not url:
        raise SystemExit(
            f"Нет {spec['title']}: {marker.relative_to(ROOT)}\n"
            f"Укажите публичную ссылку Яндекс Диска в {ENV_FILE.relative_to(ROOT)} "
            f"как {spec['env']}=https://disk.yandex.ru/d/...\n"
            f"Образец: build_env/yandex.env.example"
        )
    print(f"{spec['title']}: скачивание {url}")
    download_archive(url, archive_path, expected_bytes if isinstance(expected_bytes, int) else None)
    if expected:
        verify_sha256(archive_path, expected)
    unpack(archive_path, dest_rel(key, item))
    if not is_ready(marker):
        raise SystemExit(f"После распаковки нет {marker.relative_to(ROOT)}")
    print(f"{spec['title']}: готово")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--force", action="store_true", help="скачать архивы заново")
    args = parser.parse_args()
    inventory = json.loads(MANIFEST.read_text())
    file_values = load_env_file(ENV_FILE)
    for spec in RESOURCES:
        ensure_resource(spec, inventory[spec["key"]], file_values, args.force)


if __name__ == "__main__":
    main()
