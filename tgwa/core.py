from __future__ import annotations

import base64
import ctypes
import html
import io
import json
import os
import re
import secrets
import shutil
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
from typing import Any, BinaryIO

from sticker_convert.utils.files.sanitize_filename import sanitize_filename


ROOT = Path(__file__).resolve().parents[1]
LOCAL_DIR = ROOT / ".local"
OUTPUT_ROOT = ROOT / "output"
UPLOAD_ROOT = LOCAL_DIR / "uploads"
MAX_VIDEO_UPLOAD = 512 * 1024 * 1024
VIDEO_EXTENSIONS = {
    ".avi",
    ".gif",
    ".m4v",
    ".mkv",
    ".mov",
    ".mp4",
    ".webm",
}
ANSI_ESCAPE = re.compile(r"\x1B(?:[@-Z\\-_]|\[[0-?]*[ -/]*[@-~])")
UPLOAD_ID = re.compile(r"^[a-f0-9]{32}$")
PACK_LINK = re.compile(
    r"^(?:https?://)?(?:(?:www\.)?t\.me|telegram\.me)/"
    r"(?P<kind>addstickers|addemoji)/(?P<slug>[A-Za-z0-9_]{1,128})/?"
    r"(?:\?.*)?$",
    re.IGNORECASE,
)
TOKEN_FORMAT = re.compile(r"^\d{5,20}:[A-Za-z0-9_-]{20,}$")
STICKER_SET_NAME = re.compile(r"^[A-Za-z][A-Za-z0-9_]{0,63}$")


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
            result = {
                "has_token": has_token,
                "author": str(data.get("author", "Telegram 轉換")),
            }
            linked_bot = str(data.get("telegram_bot_username", "")).strip()
            linked_user_id = data.get("telegram_user_id")
            if linked_bot and isinstance(linked_user_id, int):
                result["telegram_connection"] = {
                    "bot_username": linked_bot,
                    "user_id": linked_user_id,
                    "user_label": str(
                        data.get("telegram_user_label", "已連接帳戶")
                    ),
                }
            return result

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

    def save_token(self, token: str) -> None:
        with self._lock:
            data = self._load()
            data["telegram_token"] = protect_secret(token)
            self.path.parent.mkdir(parents=True, exist_ok=True)
            self.path.write_text(
                json.dumps(data, ensure_ascii=False, indent=2),
                encoding="utf-8",
            )

    def save_telegram_connection(
        self,
        bot_username: str,
        user_id: int,
        user_label: str,
    ) -> None:
        with self._lock:
            data = self._load()
            data["telegram_bot_username"] = bot_username
            data["telegram_user_id"] = user_id
            data["telegram_user_label"] = user_label
            self.path.parent.mkdir(parents=True, exist_ok=True)
            self.path.write_text(
                json.dumps(data, ensure_ascii=False, indent=2),
                encoding="utf-8",
            )

    def forget_token(self) -> None:
        with self._lock:
            data = self._load()
            data.pop("telegram_token", None)
            data.pop("telegram_bot_username", None)
            data.pop("telegram_user_id", None)
            data.pop("telegram_user_label", None)
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
) -> Any:
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
        description = ""
        try:
            error_payload = json.loads(error.read().decode("utf-8"))
            description = str(error_payload.get("description", ""))
        except (OSError, UnicodeDecodeError, json.JSONDecodeError):
            pass
        if error.code == 401:
            raise UserInputError(
                "Telegram Bot Token 無效，請向 @BotFather 重新複製。"
            )
        if error.code == 409 and "webhook" in description.lower():
            raise UserInputError(
                "呢個 Bot 正使用 Webhook，暫時讀唔到 /start。"
                "請改用一個新 Bot Token，或者先移除該 Bot 嘅 Webhook。"
            )
        if error.code == 404 and method == "getStickerSet":
            raise UserInputError("搵唔到呢個 Telegram 貼圖包，請檢查連結。")
        if description:
            raise UserInputError(f"Telegram：{description}")
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


def _telegram_api_multipart(
    token: str,
    method: str,
    fields: dict[str, str],
    file_field: str,
    file_path: Path,
) -> Any:
    boundary = f"----TGWA{secrets.token_hex(18)}"
    chunks: list[bytes] = []
    for name, value in fields.items():
        chunks.extend(
            [
                f"--{boundary}\r\n".encode("ascii"),
                (
                    f'Content-Disposition: form-data; name="{name}"\r\n\r\n'
                ).encode("ascii"),
                value.encode("utf-8"),
                b"\r\n",
            ]
        )
    chunks.extend(
        [
            f"--{boundary}\r\n".encode("ascii"),
            (
                f'Content-Disposition: form-data; name="{file_field}"; '
                'filename="sticker.webm"\r\n'
            ).encode("ascii"),
            b"Content-Type: video/webm\r\n\r\n",
            file_path.read_bytes(),
            b"\r\n",
            f"--{boundary}--\r\n".encode("ascii"),
        ]
    )
    request = urllib.request.Request(
        f"https://api.telegram.org/bot{token}/{method}",
        data=b"".join(chunks),
        method="POST",
        headers={
            "User-Agent": "TGWA-Converter/1.0",
            "Content-Type": f"multipart/form-data; boundary={boundary}",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=40) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        description = ""
        try:
            error_payload = json.loads(error.read().decode("utf-8"))
            description = str(error_payload.get("description", ""))
        except (OSError, UnicodeDecodeError, json.JSONDecodeError):
            pass
        if error.code == 401:
            raise UserInputError(
                "Telegram Bot Token 無效，請向 @BotFather 重新複製。"
            )
        if description:
            raise UserInputError(f"Telegram：{description}")
        raise UserInputError(f"Telegram 上載失敗（HTTP {error.code}）。")
    except (urllib.error.URLError, TimeoutError):
        raise UserInputError("連接唔到 Telegram，請檢查網絡後再試。")
    except json.JSONDecodeError:
        raise UserInputError("Telegram 回傳格式異常，請稍後再試。")
    if not payload.get("ok"):
        raise UserInputError(
            f"Telegram：{str(payload.get('description', '未知錯誤'))}"
        )
    return payload.get("result", {})


def telegram_bot_connection(token: str) -> dict[str, Any]:
    if not TOKEN_FORMAT.fullmatch(token):
        raise UserInputError("Telegram Bot Token 格式唔正確。")
    bot = _telegram_api(token, "getMe")
    username = str(bot.get("username", "")).strip()
    if not username:
        raise UserInputError("呢個 Telegram Bot 冇可用 username。")
    updates = _telegram_api(
        token,
        "getUpdates",
        {
            "limit": "100",
            "timeout": "0",
            "allowed_updates": json.dumps(["message"]),
        },
    )
    users_by_id: dict[int, dict[str, Any]] = {}
    for update in updates if isinstance(updates, list) else []:
        message = update.get("message") or {}
        chat = message.get("chat") or {}
        sender = message.get("from") or {}
        if chat.get("type") != "private" or sender.get("is_bot"):
            continue
        try:
            user_id = int(sender["id"])
        except (KeyError, TypeError, ValueError):
            continue
        display_name = " ".join(
            part
            for part in (
                str(sender.get("first_name", "")).strip(),
                str(sender.get("last_name", "")).strip(),
            )
            if part
        )
        sender_username = str(sender.get("username", "")).strip()
        label = display_name or (
            f"@{sender_username}" if sender_username else str(user_id)
        )
        users_by_id[user_id] = {
            "id": user_id,
            "label": label,
            "username": sender_username,
        }
    return {
        "bot_username": username,
        "bot_name": str(bot.get("first_name", username)),
        "start_url": f"https://t.me/{username}?start=tgwa",
        "users": list(reversed(list(users_by_id.values()))),
    }


def telegram_sticker_set_name(
    value: str,
    bot_username: str,
    fallback: str,
) -> str:
    suffix = f"_by_{bot_username}"
    candidate = value.strip()
    match = PACK_LINK.fullmatch(candidate)
    if match:
        if match.group("kind").lower() != "addstickers":
            raise UserInputError("請輸入一般貼圖包連結，而唔係 emoji 包。")
        candidate = match.group("slug")
    if candidate.lower().endswith(suffix.lower()):
        base = candidate[: -len(suffix)]
    else:
        base = candidate
    base = re.sub(r"[^A-Za-z0-9_]+", "_", base)
    base = re.sub(r"_+", "_", base).strip("_")
    if not base:
        base = re.sub(r"[^A-Za-z0-9_]+", "_", fallback).strip("_")
    if not base:
        base = "tgwa"
    if not base[0].isalpha():
        base = f"pack_{base}"
    max_base = 64 - len(suffix)
    if max_base < 1:
        raise UserInputError("Bot username 太長，無法建立貼圖包名稱。")
    base = base[:max_base].rstrip("_") or "p"
    result = f"{base}{suffix}"
    if not STICKER_SET_NAME.fullmatch(result) or "__" in result:
        raise UserInputError(
            "貼圖包短名只可以用英文字母、數字同底線，並要以字母開頭。"
        )
    return result


def upload_telegram_video_sticker(
    token: str,
    user_id: int,
    path: Path,
) -> str:
    result = _telegram_api_multipart(
        token,
        "uploadStickerFile",
        {
            "user_id": str(user_id),
            "sticker_format": "video",
        },
        "sticker",
        path,
    )
    file_id = str(result.get("file_id", "")).strip()
    if not file_id:
        raise UserInputError("Telegram 已接收檔案，但冇回傳 sticker file id。")
    return file_id


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


def probe_video(path: Path) -> dict[str, Any]:
    import av

    try:
        with av.open(str(path)) as container:
            if not container.streams.video:
                raise UserInputError("檔案入面搵唔到影片畫面。")
            stream = container.streams.video[0]
            if container.duration is not None:
                duration = float(container.duration / av.time_base)
            elif stream.duration is not None:
                duration = float(stream.duration * stream.time_base)
            else:
                duration = 0.0
            rate = stream.average_rate or stream.base_rate or stream.guessed_rate
            fps = float(rate) if rate else 0.0
            rotation = int(float(stream.metadata.get("rotate", "0") or 0))
            width = int(stream.width)
            height = int(stream.height)
            if rotation % 180:
                width, height = height, width
    except UserInputError:
        raise
    except Exception as error:
        raise UserInputError(f"讀取唔到影片：{error}") from error

    if width <= 0 or height <= 0:
        raise UserInputError("影片尺寸無效。")
    if duration <= 0:
        raise UserInputError("讀取唔到影片長度。")
    return {
        "width": width,
        "height": height,
        "duration": round(duration, 3),
        "fps": round(fps, 3),
        "rotation": rotation,
        "size": path.stat().st_size,
        "size_label": safe_display_size(path.stat().st_size),
    }


def save_video_upload(
    stream: BinaryIO,
    length: int,
    filename: str,
) -> dict[str, Any]:
    if length <= 0:
        raise UserInputError("請先選擇影片。")
    if length > MAX_VIDEO_UPLOAD:
        raise UserInputError("影片最多 512 MB。")

    original_name = Path(filename).name.strip()
    extension = Path(original_name).suffix.lower()
    if extension not in VIDEO_EXTENSIONS:
        raise UserInputError("支援 MP4、MOV、M4V、MKV、WEBM、AVI 或 GIF。")

    upload_id = uuid.uuid4().hex
    upload_dir = UPLOAD_ROOT / upload_id
    upload_dir.mkdir(parents=True, exist_ok=False)
    safe_name = sanitize_filename(original_name) or f"video{extension}"
    if Path(safe_name).suffix.lower() != extension:
        safe_name += extension
    destination = upload_dir / safe_name
    temporary = upload_dir / f".{safe_name}.part"
    remaining = length

    try:
        with temporary.open("wb") as output:
            while remaining:
                chunk = stream.read(min(1024 * 1024, remaining))
                if not chunk:
                    raise UserInputError("影片上載中斷，請重新選擇檔案。")
                output.write(chunk)
                remaining -= len(chunk)
        os.replace(temporary, destination)
        info = probe_video(destination)
    except Exception:
        temporary.unlink(missing_ok=True)
        destination.unlink(missing_ok=True)
        try:
            upload_dir.rmdir()
        except OSError:
            pass
        raise

    return {
        "upload_id": upload_id,
        "filename": destination.name,
        **info,
    }


def uploaded_video_path(upload_id: str) -> Path:
    if not UPLOAD_ID.fullmatch(upload_id):
        raise UserInputError("影片識別碼無效，請重新選擇影片。")
    upload_dir = (UPLOAD_ROOT / upload_id).resolve()
    try:
        upload_dir.relative_to(UPLOAD_ROOT.resolve())
    except ValueError:
        raise UserInputError("影片路徑無效。")
    files = [
        path
        for path in upload_dir.iterdir()
        if path.is_file() and not path.name.endswith(".part")
    ] if upload_dir.is_dir() else []
    if len(files) != 1:
        raise UserInputError("搵唔到已上載影片，請重新選擇。")
    return files[0]


def _video_frame_time(frame: Any, stream: Any) -> float | None:
    if frame.time is not None:
        return float(frame.time)
    if frame.pts is not None:
        return float(frame.pts * stream.time_base)
    return None


def render_video_clip(
    source: Path,
    destination: Path,
    *,
    start: float,
    duration: float,
    scale: float,
    offset_x: float,
    offset_y: float,
    background: str,
    fps: int,
    helper_destination: Path | None = None,
) -> dict[str, Any]:
    import av
    from PIL import Image

    colors = {
        "transparent": (0, 0, 0, 0),
        "black": (0, 0, 0, 255),
        "white": (255, 255, 255, 255),
    }
    if background not in colors:
        raise UserInputError("背景選項無效。")

    target_count = max(2, int(round(duration * fps)))
    frames: list[Image.Image] = []
    with av.open(str(source)) as container:
        stream = container.streams.video[0]
        rotation = int(float(stream.metadata.get("rotate", "0") or 0)) % 360
        try:
            seek_point = int(start / float(stream.time_base))
            container.seek(max(0, seek_point), stream=stream, backward=True)
        except (ValueError, TypeError, av.error.FFmpegError):
            container.seek(max(0, int(start * av.time_base)), backward=True)

        next_sample = start
        frame_interval = 1 / fps
        for frame in container.decode(stream):
            timestamp = _video_frame_time(frame, stream)
            if timestamp is None or timestamp + frame_interval < start:
                continue
            if timestamp > start + duration + frame_interval:
                break
            if timestamp + (frame_interval / 2) < next_sample:
                continue

            image = frame.to_image().convert("RGBA")
            if rotation:
                image = image.rotate(-rotation, expand=True)
            width, height = image.size
            base_scale = min(512 / width, 512 / height)
            scaled_width = max(1, int(round(width * base_scale * scale)))
            scaled_height = max(1, int(round(height * base_scale * scale)))
            resized = image.resize(
                (scaled_width, scaled_height),
                resample=Image.Resampling.LANCZOS,
            )
            left = int(round((512 - scaled_width) / 2 + offset_x * 2.56))
            top = int(round((512 - scaled_height) / 2 + offset_y * 2.56))
            canvas = Image.new("RGBA", (512, 512), colors[background])
            canvas.paste(resized, (left, top), resized)

            while (
                len(frames) < target_count
                and timestamp + (frame_interval / 2) >= next_sample
            ):
                frames.append(canvas.copy())
                next_sample = start + (len(frames) / fps)
            if len(frames) >= target_count:
                break

    if not frames:
        raise UserInputError("指定時間範圍內讀取唔到畫面。")
    while len(frames) < target_count:
        frames.append(frames[-1].copy())

    destination.parent.mkdir(parents=True, exist_ok=True)
    frame_duration = max(8, int(round(1000 / fps)))
    frames[0].save(
        destination,
        format="WEBP",
        save_all=True,
        append_images=frames[1:],
        duration=frame_duration,
        loop=0,
        lossless=True,
        method=6,
    )
    if helper_destination is not None:
        encode_sticker_maker_video(
            frames,
            helper_destination,
            fps=fps,
            background=background,
        )
    for frame in frames:
        frame.close()
    return {
        "frames": target_count,
        "fps": fps,
        "duration": round(target_count / fps, 3),
    }


def encode_sticker_maker_video(
    frames: list[Any],
    destination: Path,
    *,
    fps: int,
    background: str,
) -> None:
    import av
    from PIL import Image

    if not frames:
        raise UserInputError("製作 Sticker Maker 匯入片時讀取唔到畫面。")
    fill = (255, 255, 255) if background == "white" else (0, 0, 0)
    destination.parent.mkdir(parents=True, exist_ok=True)
    with av.open(
        str(destination),
        mode="w",
        format="mp4",
        options={"movflags": "+faststart"},
    ) as container:
        stream = container.add_stream("libx264", rate=fps)
        stream.width = 512
        stream.height = 512
        stream.pix_fmt = "yuv420p"
        stream.options = {"crf": "18", "preset": "medium"}
        stream.codec_context.gop_size = max(1, fps)
        for image in frames:
            rgba = image.convert("RGBA")
            rgb = Image.new("RGB", rgba.size, fill)
            rgb.paste(rgba, (0, 0), rgba)
            frame = av.VideoFrame.from_image(rgb)
            for packet in stream.encode(frame):
                container.mux(packet)
            rgba.close()
            rgb.close()
        for packet in stream.encode():
            container.mux(packet)


def _webp_animation_durations(path: Path) -> list[int]:
    data = path.read_bytes()
    if len(data) < 12 or data[:4] != b"RIFF" or data[8:12] != b"WEBP":
        return []

    durations: list[int] = []
    offset = 12
    while offset + 8 <= len(data):
        chunk_type = data[offset : offset + 4]
        chunk_size = int.from_bytes(data[offset + 4 : offset + 8], "little")
        payload_start = offset + 8
        payload_end = payload_start + chunk_size
        if payload_end > len(data):
            break
        if chunk_type == b"ANMF" and chunk_size >= 16:
            durations.append(
                int.from_bytes(
                    data[payload_start + 12 : payload_start + 15],
                    "little",
                )
            )
        offset = payload_end + (chunk_size & 1)
    return durations


def inspect_sticker_maker_video(path: Path) -> dict[str, Any]:
    import av

    with av.open(str(path)) as container:
        if not container.streams.video:
            raise RuntimeError("Sticker Maker MP4 入面冇影片。")
        stream = container.streams.video[0]
        duration = (
            float(container.duration / av.time_base)
            if container.duration is not None
            else float(stream.duration * stream.time_base)
            if stream.duration is not None
            else 0.0
        )
        fps_value = stream.average_rate or stream.base_rate
        fps = float(fps_value) if fps_value else 0.0
        codec = stream.codec_context.name
        width, height = int(stream.width), int(stream.height)
        audio_count = len(container.streams.audio)
    if (
        codec != "h264"
        or audio_count
        or width != 512
        or height != 512
        or duration <= 0
        or duration > 3.05
        or fps > 30.1
    ):
        raise RuntimeError("Sticker Maker MP4 尺寸、編碼、長度或 FPS 異常。")
    return {
        "filename": path.name,
        "platform": "Sticker Maker",
        "size": path.stat().st_size,
        "size_label": safe_display_size(path.stat().st_size),
        "sticker_count": 1,
        "animated_count": 1,
        "format": "MP4 · H.264",
        "dimensions": f"{width}×{height}",
        "duration": round(duration, 2),
        "fps": round(fps, 2),
        "mime_type": "video/mp4",
        "share_label": "分享 MP4",
        "label": f"Sticker Maker 匯入片 · MP4 · {duration:.1f} 秒",
        "import_hint": "在 Sticker Maker 揀「影片／GIF」匯入，確保貼圖識郁。",
    }


def inspect_video_sticker(path: Path, platform: str) -> dict[str, Any]:
    from PIL import Image

    size = path.stat().st_size
    common = {
        "filename": path.name,
        "platform": platform,
        "size": size,
        "size_label": safe_display_size(size),
        "sticker_count": 1,
        "animated_count": 1,
    }
    if platform == "Telegram":
        import av

        with av.open(str(path)) as container:
            stream = container.streams.video[0]
            duration = (
                float(container.duration / av.time_base)
                if container.duration is not None
                else float(stream.duration * stream.time_base)
                if stream.duration is not None
                else 0.0
            )
            fps_value = stream.average_rate or stream.base_rate
            fps = float(fps_value) if fps_value else 0.0
            codec = stream.codec_context.name
            width, height = int(stream.width), int(stream.height)
            audio_count = len(container.streams.audio)
        if size > 256000:
            raise RuntimeError("Telegram WEBM 超過 256 KB。")
        if codec != "vp9" or audio_count:
            raise RuntimeError("Telegram 輸出唔係無聲 VP9 WEBM。")
        if width != 512 or height != 512 or duration > 3.05 or fps > 30.1:
            raise RuntimeError("Telegram 輸出尺寸、長度或 FPS 不合規格。")
        return {
            **common,
            "format": "WEBM · VP9",
            "dimensions": f"{width}×{height}",
            "duration": round(duration, 2),
            "fps": round(fps, 2),
            "mime_type": "video/webm",
            "share_label": "分享 WEBM",
            "label": f"Telegram · WEBM · {duration:.1f} 秒",
            "import_hint": (
                "建議用完成頁嘅「直接加入 Telegram」；"
                "相片／媒體選擇器唔會顯示呢個 WEBM。"
            ),
        }

    with Image.open(path) as image:
        width, height = image.size
        frames = int(getattr(image, "n_frames", 1))
    frame_durations = _webp_animation_durations(path)
    duration_ms = sum(frame_durations)
    if size > 500000:
        raise RuntimeError("WhatsApp WebP 超過 500 KB。")
    if (
        width != 512
        or height != 512
        or frames < 2
        or len(frame_durations) != frames
        or not duration_ms
        or duration_ms > 10000
        or min(frame_durations) < 8
    ):
        raise RuntimeError("WhatsApp 輸出尺寸、動畫或長度不合規格。")
    return {
        **common,
        "format": "Animated WebP",
        "dimensions": f"{width}×{height}",
        "duration": round(duration_ms / 1000, 2),
        "fps": round(frames / max(duration_ms / 1000, 0.001), 2),
        "mime_type": "image/webp",
        "label": (
            "WhatsApp 成品檔 · Animated WebP · "
            f"{duration_ms / 1000:.1f} 秒"
        ),
        "import_hint": (
            "呢個係最終成品，唔好當圖片手動加入；"
            "Sticker Maker 請改用上面 MP4 匯入片。"
        ),
    }


def inspect_webp_animation_bytes(data: bytes) -> dict[str, Any]:
    if (
        len(data) < 20
        or data[:4] != b"RIFF"
        or data[8:12] != b"WEBP"
    ):
        return {
            "has_animation": False,
            "animated": False,
            "frame_count": 0,
            "duration_ms": 0,
            "minimum_frame_duration_ms": 0,
            "error": "不是有效的 WebP 檔案。",
        }
    animation_header = False
    frame_count = 0
    duration_ms = 0
    minimum_duration: int | None = None
    malformed = False
    offset = 12
    while offset + 8 <= len(data):
        chunk_type = data[offset : offset + 4]
        chunk_size = int.from_bytes(
            data[offset + 4 : offset + 8],
            "little",
        )
        payload = offset + 8
        chunk_end = payload + chunk_size
        if chunk_end > len(data):
            malformed = True
            break
        if chunk_type == b"ANIM":
            animation_header = True
        elif chunk_type == b"ANMF":
            if chunk_size < 16:
                malformed = True
                break
            frame_count += 1
            frame_duration = int.from_bytes(
                data[payload + 12 : payload + 15],
                "little",
            )
            duration_ms += frame_duration
            minimum_duration = (
                frame_duration
                if minimum_duration is None
                else min(minimum_duration, frame_duration)
            )
        offset = chunk_end + (chunk_size & 1)

    has_animation = animation_header or frame_count > 0
    error = ""
    if malformed:
        error = "Animated WebP chunk 已損壞或不完整。"
    elif has_animation and (
        not animation_header
        or frame_count < 2
    ):
        error = "Animated WebP 必須有 ANIM 及最少 2 個真正影格。"
    elif has_animation and (
        minimum_duration is None
        or minimum_duration < 8
    ):
        error = "Animated WebP 每格最少要維持 8 ms。"
    elif has_animation and duration_ms > 10_000:
        error = "Animated WebP 總長度不可超過 10 秒。"
    return {
        "has_animation": has_animation,
        "animated": has_animation and not error,
        "frame_count": frame_count,
        "duration_ms": duration_ms,
        "minimum_frame_duration_ms": minimum_duration or 0,
        "error": error,
    }


def is_animated_webp_bytes(data: bytes) -> bool:
    return bool(inspect_webp_animation_bytes(data)["animated"])


def inspect_wastickers(path: Path) -> dict[str, Any]:
    with zipfile.ZipFile(path) as archive:
        names = [
            name
            for name in archive.namelist()
            if Path(name).suffix.lower() in {".webp", ".png"}
            and Path(name).name.lower() != "cover.png"
        ]
        animated = 0
        invalid_animated = 0
        for name in names:
            if Path(name).suffix.lower() == ".webp":
                data = archive.read(name)
                animation = inspect_webp_animation_bytes(data)
                if animation["animated"]:
                    animated += 1
                elif animation["has_animation"]:
                    invalid_animated += 1
        declared_animated: bool | None = None
        try:
            contents = json.loads(
                archive.read("contents.json").decode("utf-8")
            )
            packs = contents.get("sticker_packs", [])
            if packs:
                declared_animated = bool(
                    packs[0].get("animated_sticker_pack", False)
                )
        except (
            KeyError,
            UnicodeDecodeError,
            json.JSONDecodeError,
            AttributeError,
        ):
            pass
    static = len(names) - animated
    if invalid_animated:
        kind = "invalid"
    elif animated and static:
        kind = "mixed"
    elif animated:
        kind = "animated"
    else:
        kind = "static"
    metadata_matches = (
        declared_animated is None
        or (
            kind != "mixed"
            and declared_animated == (kind == "animated")
        )
    )
    return {
        "filename": path.name,
        "size": path.stat().st_size,
        "size_label": safe_display_size(path.stat().st_size),
        "sticker_count": len(names),
        "animated_count": animated,
        "invalid_animated_count": invalid_animated,
        "static_count": static,
        "kind": kind,
        "declared_animated": declared_animated,
        "metadata_matches": metadata_matches,
    }


def build_animated_wastickers(
    sources: list[Path],
    destination: Path,
    *,
    title: str,
    author: str,
) -> dict[str, Any]:
    from PIL import Image

    if not 3 <= len(sources) <= 30:
        raise UserInputError("WhatsApp 動態貼圖包需要 3 至 30 張貼圖。")
    for source in sources:
        inspect_video_sticker(source, "WhatsApp")

    with Image.open(sources[0]) as image:
        image.seek(0)
        first_frame = image.convert("RGBA")
    first_frame.thumbnail((88, 88), Image.Resampling.LANCZOS)
    cover = Image.new("RGBA", (96, 96), (0, 0, 0, 0))
    cover.alpha_composite(
        first_frame,
        (
            (96 - first_frame.width) // 2,
            (96 - first_frame.height) // 2,
        ),
    )
    cover_buffer = io.BytesIO()
    cover.save(cover_buffer, format="PNG", optimize=True)
    first_frame.close()
    cover.close()
    cover_data = cover_buffer.getvalue()
    if len(cover_data) > 50_000:
        raise RuntimeError("WhatsApp 貼圖包封面超過 50 KB。")

    identifier_base = re.sub(r"[^a-z0-9_.-]+", "_", title.lower()).strip("_")
    identifier = (
        (identifier_base[:80] or "tgwa_pack")
        + "_"
        + uuid.uuid4().hex[:10]
    )
    stickers = [
        {
            "image_file": f"{index:03d}.webp",
            "emojis": ["✨"],
            "accessibility_text": f"{title} sticker {index}",
        }
        for index in range(1, len(sources) + 1)
    ]
    contents = {
        "sticker_packs": [
            {
                "identifier": identifier,
                "name": title,
                "publisher": author,
                "tray_image_file": "cover.png",
                "image_data_version": str(int(datetime.now().timestamp())),
                "animated_sticker_pack": True,
                "stickers": stickers,
            }
        ]
    }

    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.parent / f".tgwa-{uuid.uuid4().hex}.tmp"
    try:
        with zipfile.ZipFile(temporary, "w") as archive:
            archive.writestr(
                "cover.png",
                cover_data,
                compress_type=zipfile.ZIP_DEFLATED,
            )
            archive.writestr(
                "title.txt",
                title + "\n",
                compress_type=zipfile.ZIP_DEFLATED,
            )
            archive.writestr(
                "author.txt",
                author + "\n",
                compress_type=zipfile.ZIP_DEFLATED,
            )
            archive.writestr(
                "contents.json",
                json.dumps(contents, ensure_ascii=False, indent=2),
                compress_type=zipfile.ZIP_DEFLATED,
            )
            for index, source in enumerate(sources, start=1):
                archive.writestr(
                    f"{index:03d}.webp",
                    source.read_bytes(),
                    compress_type=zipfile.ZIP_STORED,
                )
        os.replace(temporary, destination)
    finally:
        temporary.unlink(missing_ok=True)

    info = inspect_wastickers(destination)
    if (
        info["sticker_count"] != len(sources)
        or info["animated_count"] != len(sources)
    ):
        raise RuntimeError("WhatsApp 動態貼圖包內容驗證失敗。")
    return {
        **info,
        "label": f"WhatsApp 動態貼圖包 · {len(sources)} 張",
        "platform": "WhatsApp",
        "import_hint": (
            "在 Android 用 TGWA Maker 開啟，再按 Add to WhatsApp。"
        ),
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
    if not packages:
        return []

    ordered = _ordered_engine_packages(packages, title)
    cover_data: bytes | None = None
    author_data = b""
    stickers: list[tuple[str, bytes, bool]] = []
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
                    extension = Path(name).suffix.lower()
                    animation = (
                        inspect_webp_animation_bytes(data)
                        if extension == ".webp"
                        else None
                    )
                    if (
                        animation is not None
                        and animation["has_animation"]
                        and not animation["animated"]
                    ):
                        raise UserInputError(
                            f"{name} 動態 WebP 驗證失敗："
                            f"{animation['error']}"
                        )
                    stickers.append(
                        (
                            extension,
                            data,
                            bool(
                                animation is not None
                                and animation["animated"]
                            ),
                        )
                    )

    grouped = [
        (
            "Animated",
            True,
            [item for item in stickers if item[2]],
        ),
        (
            "Static",
            False,
            [item for item in stickers if not item[2]],
        ),
    ]
    grouped = [group for group in grouped if group[2]]
    too_small = [
        f"{group_name} {len(group_stickers)} 張"
        for group_name, _, group_stickers in grouped
        if len(group_stickers) < 3
    ]
    if too_small:
        detail = "、".join(too_small)
        raise UserInputError(
            "WhatsApp 每種貼圖最少要 3 張；分開靜態／動態後，"
            f"{detail}，因此無法建立合規貼圖包。"
        )

    mixed = len(grouped) > 1
    created: list[Path] = []
    for group_name, animated, group_stickers in grouped:
        part_sizes = whatsapp_part_sizes(len(group_stickers))
        sticker_offset = 0
        group_title = (
            f"{title} - {group_name}"
            if mixed
            else title
        )
        for part_number, part_size in enumerate(part_sizes, start=1):
            part_title = (
                f"{group_title} - Part {part_number}"
                if len(part_sizes) > 1
                else group_title
            )
            destination = output_dir / sanitize_filename(
                f"{part_title}.wastickers"
            )
            temporary = output_dir / f".tgwa-{uuid.uuid4().hex}.tmp"
            current_stickers = group_stickers[
                sticker_offset : sticker_offset + part_size
            ]
            sticker_entries = [
                {
                    "image_file": f"{index:03d}{extension}",
                    "emojis": ["✨"],
                    "accessibility_text": (
                        f"{part_title} sticker {index}"
                    ),
                }
                for index, (extension, _, _) in enumerate(
                    current_stickers,
                    start=1,
                )
            ]
            identifier_base = re.sub(
                r"[^a-z0-9_.-]+",
                "_",
                part_title.lower(),
            ).strip("_")
            contents = {
                "sticker_packs": [
                    {
                        "identifier": (
                            (identifier_base[:80] or "tgwa_pack")
                            + "_"
                            + uuid.uuid4().hex[:10]
                        ),
                        "name": part_title,
                        "publisher": (
                            author_data.decode(
                                "utf-8",
                                errors="replace",
                            ).strip()
                            or "TGWA"
                        ),
                        "tray_image_file": "cover.png",
                        "image_data_version": str(
                            int(datetime.now().timestamp())
                        ),
                        "animated_sticker_pack": animated,
                        "stickers": sticker_entries,
                    }
                ]
            }
            try:
                with zipfile.ZipFile(
                    temporary,
                    "w",
                    compression=zipfile.ZIP_DEFLATED,
                ) as destination_archive:
                    if cover_data is not None:
                        destination_archive.writestr(
                            "cover.png",
                            cover_data,
                        )
                    destination_archive.writestr(
                        "author.txt",
                        author_data,
                    )
                    destination_archive.writestr(
                        "title.txt",
                        part_title + "\n",
                    )
                    destination_archive.writestr(
                        "contents.json",
                        json.dumps(
                            contents,
                            ensure_ascii=False,
                            indent=2,
                        ),
                    )
                    for local_index, (
                        extension,
                        data,
                        item_animated,
                    ) in enumerate(current_stickers, start=1):
                        if item_animated != animated:
                            raise RuntimeError(
                                "貼圖分包時動畫類型驗證失敗。"
                            )
                        destination_archive.writestr(
                            f"{local_index:03d}{extension}",
                            data,
                            compress_type=zipfile.ZIP_STORED,
                        )
                os.replace(temporary, destination)
                package_info = inspect_wastickers(destination)
                if (
                    package_info["kind"]
                    != ("animated" if animated else "static")
                    or not package_info["metadata_matches"]
                ):
                    raise RuntimeError(
                        "WhatsApp 貼圖包動畫 metadata 驗證失敗。"
                    )
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
        self.video_pack_items: list[dict[str, Any]] = []
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

    def start_video(self, payload: dict[str, Any]) -> dict[str, Any]:
        source = uploaded_video_path(str(payload.get("upload_id", "")))
        source_info = probe_video(source)

        try:
            start = float(payload.get("start", 0))
            duration = float(payload.get("duration", 3))
            scale = float(payload.get("scale", 1))
            offset_x = float(payload.get("offset_x", 0))
            offset_y = float(payload.get("offset_y", 0))
        except (TypeError, ValueError):
            raise UserInputError("影片剪輯設定格式錯誤。")
        if start < 0 or start >= float(source_info["duration"]):
            raise UserInputError("開始時間超出影片範圍。")
        if duration < 0.2 or duration > 3:
            raise UserInputError("同時兼容 Telegram／WhatsApp 時，剪輯長度要 0.2–3 秒。")
        if start + duration > float(source_info["duration"]) + 0.05:
            raise UserInputError("剪輯終點超出影片長度。")
        if not 0.25 <= scale <= 4:
            raise UserInputError("縮放要在 25% 至 400% 之間。")
        if not -100 <= offset_x <= 100 or not -100 <= offset_y <= 100:
            raise UserInputError("影片位置超出可調校範圍。")

        background = str(payload.get("background", "transparent"))
        if background not in {"transparent", "black", "white"}:
            raise UserInputError("背景選項無效。")
        title = str(payload.get("title", "")).strip() or source.stem
        if len(title) > 128:
            raise UserInputError("輸出名稱最多 128 個字。")
        fps = min(30, max(12, int(round(float(source_info.get("fps") or 30)))))

        job_id = uuid.uuid4().hex
        timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
        job_root = OUTPUT_ROOT / f"video-{timestamp}-{job_id[:5]}"
        share_token = secrets.token_urlsafe(22)
        job = {
            "id": job_id,
            "kind": "video",
            "status": "queued",
            "phase": "validate",
            "progress": 4,
            "message": "正在檢查影片設定…",
            "title": title,
            "source_path": str(source),
            "source_info": source_info,
            "start": start,
            "duration": duration,
            "scale": scale,
            "offset_x": offset_x,
            "offset_y": offset_y,
            "background": background,
            "fps": fps,
            "logs": [],
            "packages": [],
            "outputs": [],
            "share_token": share_token,
            "job_root": str(job_root),
            "output_dir": str(job_root / "ready"),
            "error": None,
        }
        with self._lock:
            if any(
                item["status"] in {"queued", "running"}
                for item in self.jobs.values()
            ):
                raise UserInputError("上一個轉換仍在處理，完成後先再開始。")
            self.jobs[job_id] = job

        thread = threading.Thread(
            target=self._run_video,
            args=(job_id,),
            daemon=True,
            name=f"video-{job_id[:6]}",
        )
        thread.start()
        return self.snapshot(job_id)

    def connect_telegram(self, payload: dict[str, Any]) -> dict[str, Any]:
        supplied_token = str(payload.get("token", "")).strip()
        token = supplied_token or self.config.token()
        if not token:
            raise UserInputError(
                "請先輸入 Telegram Bot Token；只需第一次設定。"
            )
        connection = telegram_bot_connection(token)
        if supplied_token and bool(payload.get("remember", True)):
            self.config.save_token(token)

        saved = self.config.public().get("telegram_connection")
        saved_user_id = (
            saved.get("user_id")
            if isinstance(saved, dict)
            and str(saved.get("bot_username", "")).lower()
            == str(connection["bot_username"]).lower()
            else None
        )
        if isinstance(saved_user_id, int) and not any(
            user["id"] == saved_user_id for user in connection["users"]
        ):
            connection["users"].append(
                {
                    "id": saved_user_id,
                    "label": str(saved.get("user_label", "已連接帳戶")),
                    "username": "",
                }
            )
        connection["has_saved_token"] = bool(self.config.token())
        return connection

    def publish_telegram(
        self,
        job_id: str,
        payload: dict[str, Any],
    ) -> dict[str, Any]:
        supplied_token = str(payload.get("token", "")).strip()
        token = supplied_token or self.config.token()
        if not token:
            raise UserInputError("請先連接 Telegram Bot。")
        if not TOKEN_FORMAT.fullmatch(token):
            raise UserInputError("Telegram Bot Token 格式唔正確。")
        try:
            user_id = int(payload.get("user_id"))
        except (TypeError, ValueError):
            raise UserInputError("請先選擇你嘅 Telegram 帳戶。")
        if user_id <= 0:
            raise UserInputError("Telegram 帳戶資料無效。")

        with self._lock:
            if job_id not in self.jobs:
                raise KeyError(job_id)
            job = dict(self.jobs[job_id])
        if job.get("status") != "done" or job.get("kind") != "video":
            raise UserInputError("只可以發佈已完成嘅影片貼圖。")
        telegram_item = next(
            (
                item
                for item in job.get("outputs", [])
                if item.get("platform") == "Telegram"
                and Path(str(item.get("filename", ""))).suffix.lower()
                == ".webm"
            ),
            None,
        )
        if not telegram_item:
            raise UserInputError("搵唔到 Telegram WEBM 成品。")
        output_dir = Path(str(job["output_dir"])).resolve()
        sticker_path = (
            output_dir / Path(str(telegram_item["filename"])).name
        ).resolve()
        if sticker_path.parent != output_dir or not sticker_path.is_file():
            raise UserInputError("Telegram WEBM 成品路徑無效。")

        action = str(payload.get("action", "create")).strip().lower()
        if action not in {"create", "add", "send"}:
            raise UserInputError("Telegram 發佈方式無效。")
        emoji = str(payload.get("emoji", "✨")).strip()
        if not emoji or len(emoji) > 32:
            raise UserInputError("請輸入 1 個貼圖 emoji。")

        bot = _telegram_api(token, "getMe")
        bot_username = str(bot.get("username", "")).strip()
        if not bot_username:
            raise UserInputError("呢個 Telegram Bot 冇可用 username。")

        set_name = ""
        title = ""
        if action == "create":
            title = str(payload.get("title", "")).strip()
            if not title or len(title) > 64:
                raise UserInputError("貼圖包名稱需要 1 至 64 個字。")
            set_name = telegram_sticker_set_name(
                str(payload.get("name", "")),
                bot_username,
                f"tgwa_{job_id[:10]}",
            )
        elif action == "add":
            value = str(payload.get("name", "")).strip()
            match = PACK_LINK.fullmatch(value)
            if match:
                if match.group("kind").lower() != "addstickers":
                    raise UserInputError("請輸入一般貼圖包連結。")
                value = match.group("slug")
            if (
                not STICKER_SET_NAME.fullmatch(value)
                or "__" in value
                or not value.lower().endswith(
                    f"_by_{bot_username}".lower()
                )
            ):
                raise UserInputError(
                    "只可加入由同一個 Bot 建立、名稱以 "
                    f"_by_{bot_username} 結尾嘅貼圖包。"
                )
            set_name = value

        file_id = upload_telegram_video_sticker(token, user_id, sticker_path)
        if action == "send":
            _telegram_api(
                token,
                "sendSticker",
                {"chat_id": str(user_id), "sticker": file_id},
            )
            result = {
                "ok": True,
                "action": "send",
                "message": "動態貼圖已傳送到你同 Bot 嘅對話。",
                "bot_username": bot_username,
            }
        else:
            input_sticker = json.dumps(
                {
                    "sticker": file_id,
                    "format": "video",
                    "emoji_list": [emoji],
                },
                ensure_ascii=False,
                separators=(",", ":"),
            )
            try:
                if action == "create":
                    _telegram_api(
                        token,
                        "createNewStickerSet",
                        {
                            "user_id": str(user_id),
                            "name": set_name,
                            "title": title,
                            "stickers": json.dumps(
                                [json.loads(input_sticker)],
                                ensure_ascii=False,
                                separators=(",", ":"),
                            ),
                            "sticker_type": "regular",
                        },
                    )
                else:
                    _telegram_api(
                        token,
                        "addStickerToSet",
                        {
                            "user_id": str(user_id),
                            "name": set_name,
                            "sticker": input_sticker,
                        },
                    )
            except UserInputError as error:
                if "occupied" in str(error).lower():
                    raise UserInputError(
                        "呢個貼圖包短名已有人使用，請換一個英文短名。"
                    )
                raise
            result = {
                "ok": True,
                "action": action,
                "message": (
                    "已建立 Telegram 動態貼圖包。"
                    if action == "create"
                    else "已加入 Telegram 動態貼圖包。"
                ),
                "set_name": set_name,
                "set_url": f"https://t.me/addstickers/{set_name}",
                "bot_username": bot_username,
            }

        if supplied_token and bool(payload.get("remember", True)):
            self.config.save_token(token)
        self.config.save_telegram_connection(
            bot_username,
            user_id,
            str(payload.get("user_label", "已連接帳戶")).strip()
            or "已連接帳戶",
        )
        return result

    def video_pack_snapshot(self) -> dict[str, Any]:
        with self._lock:
            retained: list[dict[str, Any]] = []
            public_items: list[dict[str, Any]] = []
            for item in self.video_pack_items:
                source = Path(str(item["path"]))
                if not source.is_file():
                    continue
                retained.append(item)
                public_items.append(
                    {
                        "id": item["id"],
                        "job_id": item["job_id"],
                        "title": item["title"],
                        "filename": source.name,
                        "size": source.stat().st_size,
                        "size_label": safe_display_size(source.stat().st_size),
                    }
                )
            self.video_pack_items = retained
        return {
            "items": public_items,
            "count": len(public_items),
            "minimum": 3,
            "maximum": 30,
            "can_build": 3 <= len(public_items) <= 30,
        }

    def add_video_to_pack(self, job_id: str) -> dict[str, Any]:
        with self._lock:
            if job_id not in self.jobs:
                raise KeyError(job_id)
            job = dict(self.jobs[job_id])
            if any(
                item["job_id"] == job_id for item in self.video_pack_items
            ):
                return self.video_pack_snapshot()
            if len(self.video_pack_items) >= 30:
                raise UserInputError("一個 WhatsApp 貼圖包最多 30 張。")
        if job.get("status") != "done" or job.get("kind") != "video":
            raise UserInputError("只可以加入已完成嘅影片貼圖。")
        whatsapp_item = next(
            (
                item
                for item in job.get("outputs", [])
                if item.get("platform") == "WhatsApp"
                and Path(str(item.get("filename", ""))).suffix.lower()
                == ".webp"
            ),
            None,
        )
        if not whatsapp_item:
            raise UserInputError("搵唔到 WhatsApp Animated WebP 成品。")
        output_dir = Path(str(job["output_dir"])).resolve()
        source = (
            output_dir / Path(str(whatsapp_item["filename"])).name
        ).resolve()
        if source.parent != output_dir or not source.is_file():
            raise UserInputError("WhatsApp 成品路徑無效。")
        inspect_video_sticker(source, "WhatsApp")
        with self._lock:
            self.video_pack_items.append(
                {
                    "id": uuid.uuid4().hex,
                    "job_id": job_id,
                    "title": str(job.get("title") or source.stem),
                    "path": str(source),
                }
            )
        return self.video_pack_snapshot()

    def remove_video_from_pack(self, item_id: str) -> dict[str, Any]:
        with self._lock:
            before = len(self.video_pack_items)
            self.video_pack_items = [
                item
                for item in self.video_pack_items
                if item["id"] != item_id
            ]
            if len(self.video_pack_items) == before:
                raise UserInputError("搵唔到要移除嘅貼圖。")
        return self.video_pack_snapshot()

    def clear_video_pack(self) -> dict[str, Any]:
        with self._lock:
            self.video_pack_items.clear()
        return self.video_pack_snapshot()

    def build_video_pack(self, payload: dict[str, Any]) -> dict[str, Any]:
        title = str(payload.get("title", "")).strip()
        author = str(payload.get("author", "")).strip()
        if not title or len(title) > 128:
            raise UserInputError("貼圖包名稱需要 1 至 128 個字。")
        if not author or len(author) > 128:
            raise UserInputError("作者名稱需要 1 至 128 個字。")
        state = self.video_pack_snapshot()
        if not state["can_build"]:
            raise UserInputError("請先加入最少 3 張、最多 30 張動態貼圖。")
        with self._lock:
            items = [dict(item) for item in self.video_pack_items]

        job_id = uuid.uuid4().hex
        timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
        job_root = OUTPUT_ROOT / f"whatsapp-pack-{timestamp}-{job_id[:5]}"
        output_dir = job_root / "ready"
        filename = sanitize_filename(f"{title}.wastickers")
        destination = output_dir / filename
        package = build_animated_wastickers(
            [Path(str(item["path"])) for item in items],
            destination,
            title=title,
            author=author,
        )
        share_token = secrets.token_urlsafe(22)
        port = int(self.port_getter())
        job = {
            "id": job_id,
            "kind": "whatsapp_pack",
            "status": "done",
            "phase": "share",
            "progress": 100,
            "message": "WhatsApp 動態貼圖包已完成。",
            "title": title,
            "author": author,
            "packages": [package],
            "outputs": [],
            "logs": [],
            "share_token": share_token,
            "job_root": str(job_root),
            "output_dir": str(output_dir),
            "share_url": (
                f"http://{local_ip_address()}:{port}/share/"
                f"{share_token}/{job_id}"
            ),
            "source_job_ids": [item["job_id"] for item in items],
            "error": None,
        }
        with self._lock:
            self.jobs[job_id] = job
            self.video_pack_items.clear()
        return self.snapshot(job_id)

    def _run_command(
        self,
        job_id: str,
        command: list[str],
        token: str = "",
    ) -> int:
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
        try:
            for line in process.stdout:
                self._log(job_id, line, token)
            return_code = process.wait()
        finally:
            process.stdout.close()
        with self._lock:
            self.processes.pop(job_id, None)
        return return_code

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
            return_code = self._run_command(job_id, command, token)

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

    def _run_video(self, job_id: str) -> None:
        with self._lock:
            job = dict(self.jobs[job_id])
        job_root = Path(job["job_root"])
        work_dir = job_root / "work"
        input_dir = work_dir / "input"
        telegram_dir = work_dir / "telegram"
        whatsapp_dir = work_dir / "whatsapp"
        output_dir = Path(job["output_dir"])
        self._set(job_id, status="running")

        try:
            for directory in (input_dir, telegram_dir, whatsapp_dir, output_dir):
                directory.mkdir(parents=True, exist_ok=False)
            self._set(
                job_id,
                phase="download",
                progress=14,
                message="正在擷取指定影片片段…",
            )
            intermediate = input_dir / "clip-source.webp"
            helper_source = work_dir / "sticker-maker-import.mp4"
            clip_info = render_video_clip(
                Path(job["source_path"]),
                intermediate,
                start=float(job["start"]),
                duration=float(job["duration"]),
                scale=float(job["scale"]),
                offset_x=float(job["offset_x"]),
                offset_y=float(job["offset_y"]),
                background=str(job["background"]),
                fps=int(job["fps"]),
                helper_destination=helper_source,
            )
            self._log(
                job_id,
                (
                    f"Rendered {clip_info['frames']} frames at "
                    f"{clip_info['fps']} FPS."
                ),
            )
            with self._lock:
                if self.jobs[job_id]["status"] == "cancelled":
                    return

            base_command = [
                sys.executable,
                "-m",
                "sticker_convert",
                "--lang",
                "zh_TW",
                "--no-confirm",
                "--no-progress",
                "--input-dir",
                str(input_dir),
                "--processes",
                "1",
            ]
            self._set(
                job_id,
                phase="convert",
                progress=38,
                message="正在最佳化 Telegram WEBM 畫質…",
            )
            telegram_code = self._run_command(
                job_id,
                [
                    *base_command,
                    "--output-dir",
                    str(telegram_dir),
                    "--preset",
                    "telegram",
                ],
            )
            with self._lock:
                if self.jobs[job_id]["status"] == "cancelled":
                    return
            if telegram_code != 0:
                raise RuntimeError("Telegram WEBM 編碼失敗。")

            self._set(
                job_id,
                phase="convert",
                progress=68,
                message="正在最佳化 WhatsApp 動態 WebP 畫質…",
            )
            whatsapp_code = self._run_command(
                job_id,
                [
                    *base_command,
                    "--output-dir",
                    str(whatsapp_dir),
                    "--preset",
                    "whatsapp",
                ],
            )
            with self._lock:
                if self.jobs[job_id]["status"] == "cancelled":
                    return
            if whatsapp_code != 0:
                raise RuntimeError("WhatsApp 動態 WebP 編碼失敗。")

            telegram_sources = sorted(telegram_dir.glob("*.webm"))
            whatsapp_sources = sorted(whatsapp_dir.glob("*.webp"))
            if len(telegram_sources) != 1 or len(whatsapp_sources) != 1:
                raise RuntimeError("輸出檔案數量異常。")

            self._set(
                job_id,
                phase="package",
                progress=90,
                message="正在核對尺寸、時長、FPS 與檔案大小…",
            )
            safe_title = sanitize_filename(str(job["title"])) or "video-sticker"
            telegram_output = output_dir / f"{safe_title} - Telegram.webm"
            whatsapp_output = output_dir / f"{safe_title} - WhatsApp.webp"
            helper_output = (
                output_dir / f"{safe_title} - Sticker Maker Import.mp4"
            )
            shutil.copy2(telegram_sources[0], telegram_output)
            shutil.copy2(whatsapp_sources[0], whatsapp_output)
            shutil.copy2(helper_source, helper_output)
            outputs = [
                inspect_video_sticker(telegram_output, "Telegram"),
                inspect_sticker_maker_video(helper_output),
                inspect_video_sticker(whatsapp_output, "WhatsApp"),
            ]

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
                message=(
                    "完成！Telegram、WhatsApp 成品同 "
                    "Sticker Maker 匯入片已準備好。"
                ),
                outputs=outputs,
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
        except Exception as error:
            self._set(
                job_id,
                status="error",
                progress=0,
                error=f"影片轉換失敗：{error}",
                message=f"影片轉換失敗：{error}",
            )

    def snapshot(self, job_id: str) -> dict[str, Any]:
        with self._lock:
            if job_id not in self.jobs:
                raise KeyError(job_id)
            job = self.jobs[job_id]
            return {
                key: value
                for key, value in job.items()
                if key not in {"share_token", "job_root", "source_path"}
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
    items = job.get("outputs") or job.get("packages", [])
    for package in items:
        filename = str(package["filename"])
        quoted_name = urllib.parse.quote(filename)
        label = html.escape(filename)
        detail = str(
            package.get("label")
            or f"{int(package.get('sticker_count', 1))} 張"
        )
        meta = f"{html.escape(detail)} · {html.escape(str(package['size_label']))}"
        hint = html.escape(str(package.get("import_hint", "")))
        href = (
            f"/download/{urllib.parse.quote(token)}/{job['id']}/{quoted_name}"
        )
        share_label = str(package.get("share_label", ""))
        share_button = ""
        if share_label:
            share_button = (
                '<button class="share-file" type="button" data-share-file '
                f'data-url="{html.escape(href, quote=True)}" '
                f'data-name="{html.escape(filename, quote=True)}" '
                f'data-mime="{html.escape(str(package.get("mime_type", "")), quote=True)}">'
                f"{html.escape(share_label)}</button>"
            )
        hint_html = f"<em>{hint}</em>" if hint else ""
        package_links.append(
            '<div class="file-card">'
            f'<a class="download" href="{href}">'
            f'<span><strong>{label}</strong><small>{meta}</small>{hint_html}</span>'
            "<b>下載</b></a>"
            f"{share_button}</div>"
        )
    links = "".join(package_links)
    is_video = job.get("kind") == "video"
    is_whatsapp_pack = job.get("kind") == "whatsapp_pack"
    lead = (
        "Telegram 同 WhatsApp 動態貼圖已經準備好。"
        if is_video
        else "WhatsApp 動態貼圖包已經準備好。"
        if is_whatsapp_pack
        else "WhatsApp 相容貼圖包已經準備好。"
    )
    if is_video:
        steps = """
<li>Telegram 請按「分享 WEBM」揀 Telegram，再送去 <strong>@Stickers</strong>；
手動上載時要揀「檔案／File」，唔好揀相片庫。</li>
<li>Telegram 唔支援 Animated WebP；將 WhatsApp WebP 加入 Telegram
只會變成靜態貼圖。</li>
<li>Sticker Maker 如將 WebP 當成靜態，請用「分享 MP4」或下載
<strong>Sticker Maker Import.mp4</strong>，再以影片方式匯入。</li>
<li>WhatsApp 貼圖包最少需要 3 張，請在 Sticker Maker 加入足夠貼圖後匯入。</li>
"""
    else:
        steps = """
<li>首次使用先下載並安裝下面嘅 <strong>TGWA Maker</strong>。</li>
<li>按上面「下載」，完成後用 TGWA Maker 開啟
<strong>.wastickers</strong> 檔案。</li>
<li>在 Bridge 按「Add to WhatsApp」，再由 WhatsApp 確認加入。</li>
"""
    bridge_link = (
        '<a class="bridge-download" href="/bridge-apk">'
        "下載 TGWA Maker.apk（只需安裝一次）</a>"
        if not is_video
        else ""
    )
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
.file-card{{padding-bottom:13px;margin-bottom:13px;border-bottom:1px solid #263a37}}
.file-card:last-child{{padding-bottom:0;margin-bottom:0;border-bottom:0}}
.download{{display:flex;align-items:center;justify-content:space-between;gap:16px;
text-decoration:none;color:#f4fffc;background:#182826;border:1px solid #2d4742;
padding:16px;border-radius:16px;margin-bottom:8px}}.download span{{min-width:0}}
.download strong{{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}}
.download small{{display:block;color:#93aaa5;margin-top:5px}}
.download em{{display:block;color:#b9ccc7;margin-top:8px;font-size:12px;line-height:1.45;
font-style:normal;white-space:normal}}
.download b{{background:#25d366;color:#052b19;padding:10px 14px;border-radius:12px}}
.share-file{{width:100%;min-height:44px;border:1px solid #3a5c56;border-radius:13px;
background:#17332e;color:#8df4cb;font-family:inherit;font-size:14px;font-weight:800}}
.share-file:disabled{{opacity:.65}}
.bridge-download{{display:flex;min-height:50px;align-items:center;justify-content:center;
margin-top:14px;padding:12px;border:1px solid #4f97e8;border-radius:15px;
background:#2096dc;color:#fff;font-size:14px;font-weight:900;text-decoration:none;
text-align:center}}
.share-status{{min-height:20px;margin:12px 2px 0;color:#90aaa4;font-size:13px}}
.steps{{counter-reset:s;margin:26px 0 0;padding:0;list-style:none}}
.steps li{{position:relative;padding:0 0 20px 42px;color:#b8cbc7;line-height:1.5}}
.steps li:before{{counter-increment:s;content:counter(s);position:absolute;left:0;top:-2px;
width:28px;height:28px;border-radius:50%;background:#1b3330;color:#79ebc0;
display:grid;place-items:center;font-weight:800}}
.note{{font-size:13px;color:#78908b;margin-top:20px;line-height:1.55}}
</style><script src="/share.js" defer></script></head><body><main>
<div class="brand"><span class="logo">✓</span> TG → WA</div>
<h1>{title}</h1>
<p class="lead">{lead}</p>
<section class="card">{links}</section>
{bridge_link}
<p class="share-status" id="shareStatus" role="status"></p>
<ol class="steps">
{steps}
</ol>
<p class="note">首次使用需要先安裝相容貼圖匯入 app。
下載連結只在你部電腦嘅轉換工具開住時有效。</p>
</main></body></html>"""
    return document.encode("utf-8")
