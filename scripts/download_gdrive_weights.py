"""Download deployment archives from public Google Drive links and unpack them.

Links come from build_env/gdrive.env, build_env/gdrive.env.example, environment
variables, or the url fields in build_env/artifacts.json. A share link is not a
direct file URL: large files answer with a virus-scan page, and the script
submits that form.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tarfile
import urllib.parse

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "build_env/artifacts.json"
ENV_FILE = ROOT / "build_env/gdrive.env"
ENV_EXAMPLE = ROOT / "build_env/gdrive.env.example"
USER_AGENT = "Mozilla/5.0 (compatible; wine-deploy/1.0)"

RESOURCES = (
    {
        "key": "recognizer",
        "env": "GDRIVE_RECOGNIZER_URL",
        "title": "веса распознавания",
    },
    {
        "key": "catalog_images",
        "env": "GDRIVE_CATALOG_IMAGES_URL",
        "title": "изображения каталога",
    },
    {
        "key": "recommendations",
        "env": "GDRIVE_RECOMMENDATIONS_URL",
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


def config_values() -> dict[str, str]:
    values = load_env_file(ENV_EXAMPLE)
    values.update(load_env_file(ENV_FILE))
    return values


def resource_url(env_name: str, file_values: dict[str, str], item: dict) -> str | None:
    for source in (os.environ.get(env_name), file_values.get(env_name), item.get("url")):
        if source and str(source).strip() and str(source).strip().lower() != "null":
            return str(source).strip()
    return None


def gdrive_file_id(public_url: str) -> str:
    parsed = urllib.parse.urlparse(public_url.strip())
    host = (parsed.hostname or "").lower()
    if host.endswith("docs.google.com") and "/document/" in parsed.path:
        raise SystemExit(
            "Это ссылка на Google Документ. Нужна ссылка на файл:\n"
            "https://drive.google.com/file/d/FILE_ID/view"
        )
    match = re.search(r"/file/d/([a-zA-Z0-9_-]+)", parsed.path)
    if match:
        return match.group(1)
    file_id = urllib.parse.parse_qs(parsed.query).get("id", [""])[0]
    if file_id:
        return file_id
    if re.fullmatch(r"[a-zA-Z0-9_-]{20,}", public_url.strip()):
        return public_url.strip()
    raise SystemExit(
        f"Не удалось прочитать id файла Google Диска: {public_url}\n"
        "Нужна ссылка вида https://drive.google.com/file/d/FILE_ID/view"
    )


def confirmation_url(html: str) -> str | None:
    action = re.search(r'<form[^>]*id="download-form"[^>]*action="([^"]+)"', html)
    if not action:
        action = re.search(r'<form[^>]*action="([^"]+)"[^>]*id="download-form"', html)
    if not action:
        href = re.search(r'href="(/uc\?export=download[^"]+)"', html)
        if not href:
            return None
        return "https://drive.google.com" + href.group(1).replace("&amp;", "&")
    fields: dict[str, str] = {}
    for tag in re.findall(r"<input\b[^>]*>", html):
        name = re.search(r'\bname="([^"]+)"', tag)
        value = re.search(r'\bvalue="([^"]*)"', tag)
        if name and value:
            fields[name.group(1)] = value.group(1).replace("&amp;", "&")
    base = action.group(1).replace("&amp;", "&")
    if not fields:
        return base
    return base + "?" + urllib.parse.urlencode(fields)


def page_message(html: str) -> str:
    lowered = html.lower()
    if "quota exceeded" in lowered or "too many users have viewed or downloaded" in lowered:
        return "Google Drive временно ограничил скачивание этого файла. Повторите позже."
    match = re.search(r'class="uc-(?:warning|error)-caption">([^<]+)', html)
    if match:
        return "Google Drive: " + match.group(1).strip()
    return "Google Drive вернул страницу вместо файла."


def is_html_response(path: Path) -> bool:
    if not path.is_file() or path.stat().st_size == 0:
        return False
    with path.open("rb") as stream:
        magic = stream.read(64).lstrip().lower()
    return magic.startswith((b"<!doctype", b"<html", b"<head"))


def curl_download(href: str, partial: Path, cookie_jar: Path, resume: bool) -> None:
    partial.parent.mkdir(parents=True, exist_ok=True)
    command = [
        "curl", "--fail", "--location", "--retry", "5", "--retry-all-errors",
        "--retry-delay", "2", "--user-agent", USER_AGENT,
        "--cookie", str(cookie_jar), "--cookie-jar", str(cookie_jar),
        "--output", str(partial),
    ]
    if resume:
        resumed = subprocess.run(command + ["--continue-at", "-", href])
        if resumed.returncode == 0:
            return
        partial.unlink(missing_ok=True)
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
    file_id = gdrive_file_id(public_url)
    partial = Path(str(dest) + ".partial")
    cookies = Path(str(dest) + ".cookies")
    url = "https://drive.google.com/uc?export=download&id=" + urllib.parse.quote(file_id)
    last_html = ""
    downloaded = False
    for _ in range(4):
        resume = partial.is_file() and partial.stat().st_size > 0 and not is_html_response(partial)
        try:
            curl_download(url, partial, cookies, resume)
        except subprocess.CalledProcessError as exc:
            partial.unlink(missing_ok=True)
            raise SystemExit(f"Не удалось скачать {public_url}") from exc
        if not is_html_response(partial):
            downloaded = True
            break
        last_html = partial.read_text(errors="replace")
        partial.unlink()
        nxt = confirmation_url(last_html)
        if not nxt or nxt == url:
            raise SystemExit(page_message(last_html))
        url = nxt
    if not downloaded:
        raise SystemExit(page_message(last_html) if last_html else "Google Drive не отдал файл.")
    cookies.unlink(missing_ok=True)
    if expected_bytes and partial.stat().st_size != expected_bytes:
        partial.unlink(missing_ok=True)
        raise SystemExit(f"Размер {dest.name} не совпал: ожидалось {expected_bytes} байт")
    if not looks_like_archive(partial):
        partial.unlink(missing_ok=True)
        raise SystemExit(f"{dest.name}: это не tar/tar.gz. Ссылка должна вести на файл архива.")
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
            f"Укажите публичную ссылку Google Диска в {ENV_FILE.relative_to(ROOT)} "
            f"как {spec['env']}=https://drive.google.com/file/d/.../view\n"
            f"Образец: {ENV_EXAMPLE.relative_to(ROOT)}"
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
    file_values = config_values()
    for spec in RESOURCES:
        ensure_resource(spec, inventory[spec["key"]], file_values, args.force)


if __name__ == "__main__":
    main()
