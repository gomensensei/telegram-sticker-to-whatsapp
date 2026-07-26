from __future__ import annotations

import base64
import ctypes
import html
import json
import os
import re
import secrets
import subprocess
import sys
import threading
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zipfile
from ctypes import wintypes
from datetime import datetime
from pathlib import Path
from typing import Any

from sticker_convert.utils.files.sanitize_filename import sanitize_filename


ROOT = Path(__file__).resolve().parents[1]
LOCAL_DIR = ROOT / ".local"
OUTPUT_ROOT = ROOT / "output"
ANSI_ESCAPE = re.compile(r"\x1B(?:[@-Z\\-_]|\[[0-?]*[ -/]*[@-~])")
PACK_LINK = re.compile(
    r"^(?:https?://)?(?:(?:www\.)?t\.me|telegram\.me)/"
    r"(?P<kind>addstickers|addemoji)/(?P<slug>[A-Za-z0-9_]{1,128})/?"
    r"(?:\?.*)?$",
    re.IGNORECASE,
)
TOKEN_FORMAT = re.compile(r"^\d{5,20}:[A-Za-z0-9_-]{20,}$")


class UserInputError(ValueError):
    """An error that should be shown directly to the user."""


class _DataBlob(ctypes.Structure):
    _fields_ = [
        ("cbData", wintypes.DWORD),
        ("pbData", ctypes.POINTER(ctypes.c_ubyte)),
    ]


def _make_blob(data: bytes) -> tuple[_DataBlob, Any]:
    buffer = (ctypes.c_ubyte * len(data)).from_buffer_copy(data)
    return _DataBlob(len(data), buffer), buffer


def protect_secret(value: str) -> str:
    raw = value.encode("utf-8")
    if os.name != "nt":
        return "plain:" + base64.urlsafe_b64encode(raw).decode("ascii")

    in_blob, in_buffer = _make_blob(raw)
    out_blob = _DataBlob()
    crypt_protect = ctypes.windll.crypt32.CryptProtectData
    crypt_protect.argtypes = [
        ctypes.POINTER(_DataBlob),
        wintypes.LPCWSTR,
        ctypes.POINTER(_DataBlob),
        ctypes.c_void_p,
        ctypes.c_void_p,
        wintypes.DWORD,
        ctypes.POINTER(_DataBlob),
    ]
    crypt_protect.restype = wintypes.BOOL
    ok = crypt_protect(
        ctypes.byref(in_blob),
        "Telegram to WhatsApp token",
        None,
        None,
        None,
        0,
        ctypes.byref(out_blob),
    )
    del in_buffer
    if not ok:
        raise ctypes.WinError()
    try:
        encrypted = ctypes.string_at(out_blob.pbData, out_blob.cbData)
    finally:
        ctypes.windll.kernel32.LocalFree(out_blob.pbData)
    return "dpapi:" + base64.urlsafe_b64encode(encrypted).decode("ascii")


def unprotect_secret(value: str) -> str:
    if value.startswith("plain:"):
        return base64.urlsafe_b64decode(value[6:].encode("ascii")).decode("utf-8")
    if not value.startswith("dpapi:") or os.name != "nt":
        return ""

    encrypted = base64.urlsafe_b64decode(value[6:].encode("ascii"))
    in_blob, in_buffer = _make_blob(encrypted)
    out_blob = _DataBlob()
    crypt_unprotect = ctypes.windll.crypt32.CryptUnprotectData
    crypt_unprotect.argtypes = [
        ctypes.POINTER(_DataBlob),
        ctypes.POINTER(wintypes.LPWSTR),
        ctypes.POINTER(_DataBlob),
        ctypes.c_void_p,
        ctypes.c_void_p,
        wintypes.DWORD,
        ctypes.POINTER(_DataBlob),
    ]
    crypt_unprotect.restype = wintypes.BOOL
    ok = crypt_unprotect(
        ctypes.byref(in_blob),
        None,
        None,
        None,
        None,
        0,
        ctypes.byref(out_blob),
    )
    del in_buffer
    if not ok:
        return ""
    try:
        raw = ctypes.string_at(out_blob.pbData, out_blob.cbData)
    finally:
        ctypes.windll.kernel32.LocalFree(out_blob.pbData)
    return raw.decode("utf-8")


class ConfigStore:
    def __init__(self, path: Path | None = None) -> None:
        self.path = path or LOCAL_DIR / "config.json"
        self._lock = threading.Lock()

    def _load(self) -> dict[str, Any]:
        if not self.path.is_file():
            return {}
        try:
            return json.loads(self.path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            return {}

    def public(self) -> dict[str, Any]:
        with self._lock:
            data = self._load()
            encrypted = str(data.get("telegram_token", ""))
            try:
                has_token = bool(encrypted and unprotect_secret(encrypted))
            except (ValueError, OSError):
                has_token = False
            return {
                "has_token": has_token,
                "author": str(data.get("author", "Telegram 轉換")),
            }

    def token(self) -> str:
        with self._lock:
            encrypted = str(self._load().get("telegram_token", ""))
            try:
                return unprotect_secret(encrypted) if encrypted else ""
            except (ValueError, OSError):
                return ""

    def save(self, author: str, token: str | None = None) -> None:
        with self._lock:
            data = self._load()
            data["author"] = author
            if token:
                data["telegram_token"] = protect_secret(token)
            self.path.parent.mkdir(parents=True, exist_ok=True)
            self.path.write_text(
                json.dumps(data, ensure_ascii=False, indent=2),
                encoding="utf-8",
            )

    def forget_token(self) -> None:
        with self._lock:
            data = self._load()
            data.pop("telegram_token", None)
            self.path.parent.mkdir(parents=True, exist_ok=True)
            self.path.write_text(
                json.dumps(data, ensure_ascii=False, indent=2),
                encoding="utf-8",
            )


def normalize_pack_link(value: str) -> tuple[str, str, str]:
    value = value.strip()
    match = PACK_LINK.fullmatch(value)
    if not match:
        raise UserInputError(
            "請貼上有效 Telegram 貼圖包連結，例如 "
            "https://t.me/addstickers/PackName"
        )
    kind = match.group("kind").lower()
    slug = match.group("slug")
    return f"https://t.me/{kind}/{slug}", kind, slug


def _telegram_api(
    token: str, method: str, params: dict[str, str] | None = None
) -> dict[str, Any]:
    query = urllib.parse.urlencode(params or {})
    url = f"https://api.telegram.org/bot{token}/{method}"
    if query:
        url += "?" + query
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "TGWA-Converter/1.0"},
    )
    try:
        with urllib.request.urlopen(request, timeout=25) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        if error.code == 401:
            raise UserInputError("Telegram Bot Token 無效，請向 @BotFather 重新複製。")
        if error.code == 404:
            raise UserInputError("搵唔到呢個 Telegram 貼圖包，請檢查連結。")
        raise UserInputError(f"Telegram 暫時未能回應（HTTP {error.code}）。")
    except (urllib.error.URLError, TimeoutError):
        raise UserInputError("連唔到 Telegram，請檢查網絡後再試。")
    except json.JSONDecodeError:
        raise UserInputError("Telegram 回傳格式異常，請稍後再試。")

    if not payload.get("ok"):
        description = str(payload.get("description", "未知錯誤"))
        if "sticker" in description.lower() and "not found" in description.lower():
            raise UserInputError("搵唔到呢個 Telegram 貼圖包，請檢查連結。")
        raise UserInputError(f"Telegram 拒絕請求：{description}")
    return payload.get("result", {})


def get_pack_info(token: str, slug: str) -> dict[str, Any]:
    if not TOKEN_FORMAT.fullmatch(token):
        raise UserInputError("Telegram Bot Token 格式唔正確。")
    result = _telegram_api(token, "getStickerSet", {"name": slug})
    stickers = result.get("stickers", [])
    if not stickers:
        raise UserInputError("呢個 Telegram 貼圖包入面冇可轉換貼圖。")
    formats = {
        "animated" if sticker.get("is_animated") else
        "video" if sticker.get("is_video") else
        "static"
        for sticker in stickers
    }
    return {
        "name": str(result.get("name", slug)),
        "title": str(result.get("title", slug)),
        "count": len(stickers),
        "formats": sorted(formats),
    }


def safe_display_size(size: int) -> str:
    if size < 1024 * 1024:
        return f"{size / 1024:.1f} KB"
    return f"{size / (1024 * 1024):.1f} MB"


def inspect_wastickers(path: Path) -> dict[str, Any]:
    with zipfile.ZipFile(path) as archive:
        names = [
            name
            for name in archive.namelist()
            if Path(name).suffix.lower() in {".webp", ".png"}
            and Path(name).name.lower() != "cover.png"
        ]
        animated = 0
        for name in names:
            if Path(name).suffix.lower() == ".webp":
                data = archive.read(name)
                if b"ANIM" in data[:64]:
                    animated += 1
    return {
        "filename": path.name,
        "size": path.stat().st_size,
        "size_label": safe_display_size(path.stat().st_size),
        "sticker_count": len(names),
        "animated_count": animated,
    }


def _ordered_engine_packages(
    paths: list[Path],
    title: str,
) -> list[Path]:
    remaining = {path.name: path for path in paths}
    ordered: list[Path] = []
    for index in range(len(paths)):
        suffix = "" if index == 0 else f"-{index}"
        expected = sanitize_filename(f"{title}{suffix}.wastickers")
        path = remaining.pop(expected, None)
        if path is not None:
            ordered.append(path)
    ordered.extend(remaining[name] for name in sorted(remaining))
    return ordered


def whatsapp_part_sizes(sticker_count: int) -> list[int]:
    if sticker_count <= 30:
        return [sticker_count] if sticker_count else []

    part_count = (sticker_count + 29) // 30
    remaining = sticker_count
    sizes: list[int] = []
    for index in range(part_count):
        parts_after = part_count - index - 1
        current = min(30, remaining - (parts_after * 3))
        sizes.append(current)
        remaining -= current
    return sizes


def label_split_packages(
    output_dir: Path,
    title: str,
    paths: list[Path] | None = None,
) -> list[Path]:
    packages = paths or sorted(output_dir.glob("*.wastickers"))
    if len(packages) <= 1:
        return packages

    ordered = _ordered_engine_packages(packages, title)
    cover_data: bytes | None = None
    author_data = b""
    stickers: list[tuple[str, bytes]] = []
    for source in ordered:
        with zipfile.ZipFile(source, "r") as source_archive:
            for info in source_archive.infolist():
                name = Path(info.filename).name
                lowered = name.lower()
                data = source_archive.read(info.filename)
                if lowered == "cover.png" and cover_data is None:
                    cover_data = data
                elif lowered == "author.txt" and not author_data:
                    author_data = data
                elif (
                    Path(name).suffix.lower() in {".png", ".webp"}
                    and lowered != "cover.png"
                ):
                    stickers.append((Path(name).suffix.lower(), data))

    part_sizes = whatsapp_part_sizes(len(stickers))
    created: list[Path] = []
    sticker_offset = 0
    for part_number, part_size in enumerate(part_sizes, start=1):
        part_title = f"{title} - Part {part_number}"
        destination = output_dir / sanitize_filename(
            f"{part_title}.wastickers"
        )
        temporary = output_dir / f".tgwa-{uuid.uuid4().hex}.tmp"
        try:
            with zipfile.ZipFile(
                temporary,
                "w",
                compression=zipfile.ZIP_DEFLATED,
            ) as destination_archive:
                if cover_data is not None:
                    destination_archive.writestr("cover.png", cover_data)
                destination_archive.writestr("author.txt", author_data)
                destination_archive.writestr(
                    "title.txt",
                    part_title + "\n",
                )
                current_stickers = stickers[
                    sticker_offset : sticker_offset + part_size
                ]
                for local_index, (extension, data) in enumerate(
                    current_stickers,
                    start=1,
                ):
                    destination_archive.writestr(
                        f"{local_index:03d}{extension}",
                        data,
                    )
            os.replace(temporary, destination)
            created.append(destination)
            sticker_offset += part_size
        finally:
            temporary.unlink(missing_ok=True)

    created_set = {path.resolve() for path in created}
    for source in ordered:
        if source.resolve() not in created_set:
            source.unlink(missing_ok=True)
    return created


class JobManager:
    def __init__(self, config: ConfigStore, port_getter: Any) -> None:
        self.config = config
        self.port_getter = port_getter
        self.jobs: dict[str, dict[str, Any]] = {}
        self.processes: dict[str, subprocess.Popen[str]] = {}
        self._lock = threading.RLock()

    def _set(self, job_id: str, **updates: Any) -> None:
        with self._lock:
            self.jobs[job_id].update(updates)

    def _log(self, job_id: str, message: str, token: str = "") -> None:
        message = ANSI_ESCAPE.sub("", message).strip()
        if token:
            message = message.replace(token, "••••••••")
        if not message:
            return
        with self._lock:
            logs = self.jobs[job_id]["logs"]
            logs.append(message)
            if len(logs) > 180:
                del logs[:-180]
            self.jobs[job_id]["message"] = self._friendly_message(message)
            phase, progress = self._phase_from_line(
                message,
                int(self.jobs[job_id].get("progress", 0)),
            )
            if phase:
                self.jobs[job_id]["phase"] = phase
                self.jobs[job_id]["progress"] = progress

    @staticmethod
    def _friendly_message(message: str) -> str:
        lowered = message.lower()
        if "downloading" in lowered:
            return "正在下載 Telegram 貼圖…"
        if "compressing" in lowered or "converting" in lowered:
            return "正在轉成 WhatsApp 規格…"
        if "verifying" in lowered:
            return "正在檢查檔案大小與格式…"
        if ".wastickers" in lowered:
            return "正在封裝 WhatsApp 貼圖包…"
        return message[-160:]

    @staticmethod
    def _phase_from_line(message: str, current: int) -> tuple[str, int]:
        lowered = message.lower()
        if "download" in lowered:
            return "download", max(current, 18)
        if "compress" in lowered or "convert" in lowered:
            return "convert", max(current, 42)
        if "verifying" in lowered:
            return "package", max(current, 74)
        if ".wastickers" in lowered:
            return "package", max(current, 84)
        return "", current

    def start(self, payload: dict[str, Any]) -> dict[str, Any]:
        url, kind, slug = normalize_pack_link(str(payload.get("url", "")))
        token = str(payload.get("token", "")).strip() or self.config.token()
        if not token:
            raise UserInputError("第一次使用要輸入 Telegram Bot Token。")
        if not TOKEN_FORMAT.fullmatch(token):
            raise UserInputError("Telegram Bot Token 格式唔正確。")

        author = str(payload.get("author", "")).strip()
        if not author:
            raise UserInputError("請輸入貼圖作者名稱。")
        if len(author) > 128:
            raise UserInputError("作者名稱最多 128 個字。")

        title = str(payload.get("title", "")).strip()
        if len(title) > 128:
            raise UserInputError("貼圖包名稱最多 128 個字。")

        remember = bool(payload.get("remember", True))
        self.config.save(author)

        job_id = uuid.uuid4().hex
        timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
        job_root = OUTPUT_ROOT / f"{slug}-{timestamp}-{job_id[:5]}"
        share_token = secrets.token_urlsafe(22)
        job = {
            "id": job_id,
            "status": "queued",
            "phase": "validate",
            "progress": 4,
            "message": "正在檢查 Telegram 連結…",
            "url": url,
            "kind": kind,
            "slug": slug,
            "author": author,
            "remember": remember,
            "title": title,
            "logs": [],
            "packages": [],
            "share_token": share_token,
            "job_root": str(job_root),
            "output_dir": str(job_root / "ready"),
            "pack_info": None,
            "error": None,
        }
        with self._lock:
            if any(
                item["status"] in {"queued", "running"}
                for item in self.jobs.values()
            ):
                raise UserInputError("上一個貼圖包仍在轉換，完成後先再開下一個。")
            self.jobs[job_id] = job

        thread = threading.Thread(
            target=self._run,
            args=(job_id, token),
            daemon=True,
            name=f"convert-{job_id[:6]}",
        )
        thread.start()
        return self.snapshot(job_id)

    def _run(self, job_id: str, token: str) -> None:
        with self._lock:
            job = dict(self.jobs[job_id])
        job_root = Path(job["job_root"])
        input_dir = job_root / "source"
        output_dir = Path(job["output_dir"])
        self._set(job_id, status="running")

        try:
            pack_info = get_pack_info(token, job["slug"])
            if job["remember"]:
                self.config.save(job["author"], token)
            input_dir.mkdir(parents=True, exist_ok=False)
            output_dir.mkdir(parents=True, exist_ok=False)
            title = job["title"] or pack_info["title"]
            part_count = (int(pack_info["count"]) + 29) // 30
            pack_info["part_count"] = part_count
            split_message = (
                f"，會自動分成 {part_count} 個 Part"
                if part_count > 1
                else ""
            )
            self._set(
                job_id,
                pack_info=pack_info,
                title=title,
                phase="download",
                progress=12,
                message=(
                    f"找到「{title}」，共 {pack_info['count']} 張貼圖"
                    f"{split_message}。"
                ),
            )

            command = [
                sys.executable,
                "-m",
                "sticker_convert",
                "--lang",
                "zh_TW",
                "--no-confirm",
                "--no-progress",
                "--download-telegram",
                job["url"],
                "--input-dir",
                str(input_dir),
                "--output-dir",
                str(output_dir),
                "--preset",
                "whatsapp",
                "--export-whatsapp",
                "--telegram-token",
                token,
                "--author",
                job["author"],
                "--title",
                title,
                "--processes",
                str(min(4, max(1, (os.cpu_count() or 2) // 2))),
            ]
            environment = os.environ.copy()
            environment["PYTHONUTF8"] = "1"
            environment["PYTHONIOENCODING"] = "utf-8"
            creation_flags = (
                subprocess.CREATE_NO_WINDOW
                if os.name == "nt" and hasattr(subprocess, "CREATE_NO_WINDOW")
                else 0
            )
            process = subprocess.Popen(
                command,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                encoding="utf-8",
                errors="replace",
                bufsize=1,
                env=environment,
                cwd=str(ROOT),
                creationflags=creation_flags,
            )
            with self._lock:
                self.processes[job_id] = process
            assert process.stdout is not None
            for line in process.stdout:
                self._log(job_id, line, token)
            return_code = process.wait()
            with self._lock:
                self.processes.pop(job_id, None)

            with self._lock:
                if self.jobs[job_id]["status"] == "cancelled":
                    return
            package_paths = label_split_packages(output_dir, title)
            packages = [inspect_wastickers(path) for path in package_paths]
            if return_code != 0 or not packages:
                logs = self.snapshot(job_id)["logs"]
                detail = logs[-1] if logs else f"轉換程序結束代碼 {return_code}"
                raise RuntimeError(detail)

            port = int(self.port_getter())
            share_url = (
                f"http://{local_ip_address()}:{port}/share/"
                f"{job['share_token']}/{job_id}"
            )
            self._set(
                job_id,
                status="done",
                phase="share",
                progress=100,
                message=f"完成！已製作 {len(packages)} 個 WhatsApp 貼圖包。",
                packages=packages,
                share_url=share_url,
            )
        except UserInputError as error:
            self._set(
                job_id,
                status="error",
                progress=0,
                error=str(error),
                message=str(error),
            )
        except Exception as error:  # conversion errors are surfaced as job errors
            self._set(
                job_id,
                status="error",
                progress=0,
                error=f"轉換失敗：{error}",
                message=f"轉換失敗：{error}",
            )

    def snapshot(self, job_id: str) -> dict[str, Any]:
        with self._lock:
            if job_id not in self.jobs:
                raise KeyError(job_id)
            job = self.jobs[job_id]
            return {
                key: value
                for key, value in job.items()
                if key not in {"share_token", "job_root"}
            }

    def cancel(self, job_id: str) -> dict[str, Any]:
        with self._lock:
            if job_id not in self.jobs:
                raise KeyError(job_id)
            process = self.processes.get(job_id)
            if process and process.poll() is None:
                process.terminate()
            self.jobs[job_id].update(
                status="cancelled",
                message="已取消轉換。",
                error=None,
            )
        return self.snapshot(job_id)

    def open_output(self, job_id: str) -> None:
        with self._lock:
            if job_id not in self.jobs:
                raise KeyError(job_id)
            path = Path(self.jobs[job_id]["output_dir"]).resolve()
        output_root = OUTPUT_ROOT.resolve()
        if output_root not in path.parents:
            raise UserInputError("輸出路徑不安全。")
        if not path.is_dir():
            raise UserInputError("輸出資料夾尚未建立。")
        if os.name == "nt":
            os.startfile(path)  # type: ignore[attr-defined]
        elif sys.platform == "darwin":
            subprocess.Popen(["open", str(path)])
        else:
            subprocess.Popen(["xdg-open", str(path)])

    def shared_job(self, share_token: str, job_id: str) -> dict[str, Any] | None:
        with self._lock:
            job = self.jobs.get(job_id)
            if (
                not job
                or job.get("status") != "done"
                or not secrets.compare_digest(
                    str(job.get("share_token", "")), share_token
                )
            ):
                return None
            return dict(job)


def local_ip_address() -> str:
    import socket

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.connect(("8.8.8.8", 80))
        return str(sock.getsockname()[0])
    except OSError:
        try:
            return socket.gethostbyname(socket.gethostname())
        except OSError:
            return "127.0.0.1"
    finally:
        sock.close()


def mobile_share_page(job: dict[str, Any], token: str) -> bytes:
    title = html.escape(str(job.get("title") or job.get("slug") or "貼圖包"))
    package_links = []
    for package in job.get("packages", []):
        filename = str(package["filename"])
        quoted_name = urllib.parse.quote(filename)
        label = html.escape(filename)
        meta = (
            f"{int(package['sticker_count'])} 張 · "
            f"{html.escape(str(package['size_label']))}"
        )
        href = (
            f"/download/{urllib.parse.quote(token)}/{job['id']}/{quoted_name}"
        )
        package_links.append(
            f'<a class="download" href="{href}">'
            f'<span><strong>{label}</strong><small>{meta}</small></span>'
            "<b>下載</b></a>"
        )
    links = "".join(package_links)
    document = f"""<!doctype html>
<html lang="zh-HK"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="theme-color" content="#0c1515">
<title>{title} · WhatsApp 貼圖</title>
<style>
*{{box-sizing:border-box}}body{{margin:0;background:#081111;color:#eefaf7;
font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","Noto Sans TC",sans-serif}}
main{{max-width:560px;margin:auto;padding:30px 20px 48px}}
.brand{{display:flex;align-items:center;gap:10px;color:#76efc0;font-weight:800}}
.logo{{width:34px;height:34px;border-radius:11px;background:#25d366;
display:grid;place-items:center;color:#062d1b;font-size:20px}}
h1{{font-size:30px;line-height:1.18;margin:30px 0 8px}}
.lead{{color:#a9bfba;margin:0 0 24px;line-height:1.6}}
.card{{background:#111d1d;border:1px solid #263a37;border-radius:22px;padding:18px}}
.download{{display:flex;align-items:center;justify-content:space-between;gap:16px;
text-decoration:none;color:#f4fffc;background:#182826;border:1px solid #2d4742;
padding:16px;border-radius:16px;margin-bottom:11px}}
.download:last-child{{margin-bottom:0}}.download span{{min-width:0}}
.download strong{{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}}
.download small{{display:block;color:#93aaa5;margin-top:5px}}
.download b{{background:#25d366;color:#052b19;padding:10px 14px;border-radius:12px}}
.steps{{counter-reset:s;margin:26px 0 0;padding:0;list-style:none}}
.steps li{{position:relative;padding:0 0 20px 42px;color:#b8cbc7;line-height:1.5}}
.steps li:before{{counter-increment:s;content:counter(s);position:absolute;left:0;top:-2px;
width:28px;height:28px;border-radius:50%;background:#1b3330;color:#79ebc0;
display:grid;place-items:center;font-weight:800}}
.note{{font-size:13px;color:#78908b;margin-top:20px;line-height:1.55}}
</style></head><body><main>
<div class="brand"><span class="logo">✓</span> TG → WA</div>
<h1>{title}</h1>
<p class="lead">WhatsApp 相容貼圖包已經準備好。</p>
<section class="card">{links}</section>
<ol class="steps">
<li>按上面「下載」，完成後開啟 <strong>.wastickers</strong> 檔案。</li>
<li>選擇 Sticker Maker／WAStickerApps 開啟。</li>
<li>按「Add to WhatsApp」完成匯入。</li>
</ol>
<p class="note">首次使用需要先安裝支援 .wastickers 的貼圖匯入 app。
下載連結只在你部電腦嘅轉換工具開住時有效。</p>
</main></body></html>"""
    return document.encode("utf-8")
