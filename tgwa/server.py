from __future__ import annotations

import base64
import io
import json
import mimetypes
import os
import secrets
import threading
import urllib.parse
import urllib.request
import webbrowser
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any

import qrcode

from tgwa import __version__
from tgwa.core import (
    ConfigStore,
    JobManager,
    LOCAL_DIR,
    ROOT,
    UserInputError,
    mobile_share_page,
    save_video_upload,
)


STATIC_DIR = ROOT / "static"
SERVER_STATE = LOCAL_DIR / "server.json"
BRIDGE_APK = ROOT / "android-bridge" / "dist" / "TGWA-Bridge.apk"


class AppServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(
        self,
        address: tuple[str, int],
        app_key: str,
        instance_id: str,
    ) -> None:
        super().__init__(address, RequestHandler)
        self.app_key = app_key
        self.instance_id = instance_id
        self.manager = JobManager(ConfigStore(), lambda: self.server_port)


class RequestHandler(BaseHTTPRequestHandler):
    server: AppServer
    protocol_version = "HTTP/1.1"

    def log_message(self, _format: str, *_args: Any) -> None:
        return

    def _common_headers(self, content_type: str, length: int) -> None:
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(length))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header(
            "Content-Security-Policy",
            "default-src 'self'; img-src 'self' data: blob:; "
            "media-src 'self' blob:; "
            "style-src 'self' 'unsafe-inline'; script-src 'self'; "
            "connect-src 'self'; frame-ancestors 'none'",
        )

    def _send_bytes(
        self,
        body: bytes,
        content_type: str,
        status: HTTPStatus = HTTPStatus.OK,
        extra_headers: dict[str, str] | None = None,
    ) -> None:
        self.send_response(status)
        self._common_headers(content_type, len(body))
        for key, value in (extra_headers or {}).items():
            self.send_header(key, value)
        self.end_headers()
        self.wfile.write(body)

    def _send_json(
        self,
        payload: Any,
        status: HTTPStatus = HTTPStatus.OK,
    ) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self._send_bytes(body, "application/json; charset=utf-8", status)

    def _authorized(self) -> bool:
        supplied = self.headers.get("X-App-Key", "")
        return bool(supplied) and secrets.compare_digest(
            supplied, self.server.app_key
        )

    def _require_authorized(self) -> bool:
        if self._authorized():
            return True
        self._send_json(
            {"error": "呢個操作只可以喺本機轉換介面使用。"},
            HTTPStatus.FORBIDDEN,
        )
        return False

    def _read_json(self) -> dict[str, Any]:
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            raise UserInputError("請求格式錯誤。")
        if length <= 0 or length > 64 * 1024:
            raise UserInputError("請求內容過大或為空。")
        try:
            body = self.rfile.read(length)
            payload = json.loads(body.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            raise UserInputError("請求格式錯誤。")
        if not isinstance(payload, dict):
            raise UserInputError("請求格式錯誤。")
        return payload

    def do_GET(self) -> None:  # noqa: N802
        parsed = urllib.parse.urlparse(self.path)
        path = urllib.parse.unquote(parsed.path)

        if path == "/api/health":
            self._send_json(
                {
                    "ok": True,
                    "version": __version__,
                    "instance": self.server.instance_id,
                }
            )
            return

        if path == "/api/config":
            if not self._require_authorized():
                return
            self._send_json(self.server.manager.config.public())
            return

        if path == "/api/video-pack":
            if not self._require_authorized():
                return
            self._send_json(self.server.manager.video_pack_snapshot())
            return

        if path == "/bridge-apk":
            self._serve_bridge_apk()
            return

        if path.startswith("/api/jobs/"):
            if not self._require_authorized():
                return
            parts = path.strip("/").split("/")
            if len(parts) == 3:
                try:
                    self._send_json(self.server.manager.snapshot(parts[2]))
                except KeyError:
                    self._send_json(
                        {"error": "搵唔到呢個轉換工作。"},
                        HTTPStatus.NOT_FOUND,
                    )
                return
            if len(parts) == 4 and parts[3] == "qr":
                self._serve_qr(parts[2])
                return
            if len(parts) == 5 and parts[3] == "download":
                self._serve_local_download(parts[2], parts[4])
                return

        if path.startswith("/share/"):
            self._serve_share(path)
            return

        if path.startswith("/download/"):
            self._serve_download(path)
            return

        self._serve_static(path)

    def do_POST(self) -> None:  # noqa: N802
        if not self._require_authorized():
            return
        path = urllib.parse.urlparse(self.path).path
        try:
            if path == "/api/video/upload":
                length_header = self.headers.get("Content-Length", "")
                try:
                    length = int(length_header)
                except ValueError:
                    raise UserInputError("影片上載格式錯誤。")
                filename = urllib.parse.unquote(
                    self.headers.get("X-File-Name", "")
                )
                info = save_video_upload(self.rfile, length, filename)
                self._send_json(info, HTTPStatus.CREATED)
                return

            if path == "/api/video/convert":
                job = self.server.manager.start_video(self._read_json())
                self._send_json(job, HTTPStatus.ACCEPTED)
                return

            if path == "/api/telegram/connect":
                connection = self.server.manager.connect_telegram(
                    self._read_json()
                )
                self._send_json(connection)
                return

            if path == "/api/convert":
                job = self.server.manager.start(self._read_json())
                self._send_json(job, HTTPStatus.ACCEPTED)
                return

            if path == "/api/forget-token":
                self.server.manager.config.forget_token()
                self._send_json({"ok": True})
                return

            if path == "/api/video-pack/remove":
                payload = self._read_json()
                self._send_json(
                    self.server.manager.remove_video_from_pack(
                        str(payload.get("item_id", ""))
                    )
                )
                return

            if path == "/api/video-pack/clear":
                self._read_json()
                self._send_json(self.server.manager.clear_video_pack())
                return

            if path == "/api/video-pack/build":
                job = self.server.manager.build_video_pack(self._read_json())
                self._send_json(job, HTTPStatus.CREATED)
                return

            if path.startswith("/api/jobs/"):
                parts = path.strip("/").split("/")
                if len(parts) == 4 and parts[3] == "cancel":
                    self._send_json(self.server.manager.cancel(parts[2]))
                    return
                if len(parts) == 4 and parts[3] == "open":
                    self.server.manager.open_output(parts[2])
                    self._send_json({"ok": True})
                    return
                if len(parts) == 4 and parts[3] == "telegram":
                    result = self.server.manager.publish_telegram(
                        parts[2],
                        self._read_json(),
                    )
                    self._send_json(result)
                    return
                if len(parts) == 4 and parts[3] == "video-pack":
                    self._read_json()
                    self._send_json(
                        self.server.manager.add_video_to_pack(parts[2])
                    )
                    return

            if path == "/api/shutdown":
                self._send_json({"ok": True})
                threading.Thread(
                    target=self.server.shutdown,
                    daemon=True,
                    name="server-shutdown",
                ).start()
                return

            self._send_json({"error": "搵唔到操作。"}, HTTPStatus.NOT_FOUND)
        except UserInputError as error:
            self._send_json({"error": str(error)}, HTTPStatus.BAD_REQUEST)
        except KeyError:
            self._send_json(
                {"error": "搵唔到呢個轉換工作。"},
                HTTPStatus.NOT_FOUND,
            )

    def _serve_static(self, path: str) -> None:
        relative = "index.html" if path in {"", "/"} else path.lstrip("/")
        candidate = (STATIC_DIR / relative).resolve()
        try:
            candidate.relative_to(STATIC_DIR.resolve())
        except ValueError:
            self._send_json({"error": "路徑無效。"}, HTTPStatus.NOT_FOUND)
            return
        if not candidate.is_file():
            self._send_json({"error": "搵唔到頁面。"}, HTTPStatus.NOT_FOUND)
            return
        mime = mimetypes.guess_type(candidate.name)[0] or "application/octet-stream"
        if mime.startswith("text/") or mime in {
            "application/javascript",
            "image/svg+xml",
        }:
            mime += "; charset=utf-8"
        self._send_bytes(candidate.read_bytes(), mime)

    def _serve_bridge_apk(self) -> None:
        if not BRIDGE_APK.is_file():
            self._send_json(
                {"error": "TGWA Bridge APK 尚未編譯完成。"},
                HTTPStatus.NOT_FOUND,
            )
            return
        self._send_bytes(
            BRIDGE_APK.read_bytes(),
            "application/vnd.android.package-archive",
            extra_headers={
                "Content-Disposition": (
                    'attachment; filename="TGWA-Bridge.apk"'
                )
            },
        )

    def _serve_qr(self, job_id: str) -> None:
        try:
            job = self.server.manager.snapshot(job_id)
        except KeyError:
            self._send_json({"error": "搵唔到呢個轉換工作。"}, HTTPStatus.NOT_FOUND)
            return
        share_url = str(job.get("share_url", ""))
        if not share_url:
            self._send_json({"error": "分享連結尚未準備好。"}, HTTPStatus.CONFLICT)
            return
        qr = qrcode.QRCode(
            version=None,
            error_correction=qrcode.constants.ERROR_CORRECT_M,
            box_size=8,
            border=2,
        )
        qr.add_data(share_url)
        qr.make(fit=True)
        image = qr.make_image(fill_color="#0b1716", back_color="#ffffff")
        buffer = io.BytesIO()
        image.save(buffer, format="PNG")
        self._send_bytes(buffer.getvalue(), "image/png")

    def _serve_share(self, path: str) -> None:
        parts = path.strip("/").split("/")
        if len(parts) != 3:
            self._send_json({"error": "分享連結無效。"}, HTTPStatus.NOT_FOUND)
            return
        _, token, job_id = parts
        job = self.server.manager.shared_job(token, job_id)
        if not job:
            self._send_json(
                {"error": "分享連結無效或已失效。"},
                HTTPStatus.NOT_FOUND,
            )
            return
        self._send_bytes(
            mobile_share_page(job, token),
            "text/html; charset=utf-8",
        )

    def _serve_download(self, path: str) -> None:
        parts = path.strip("/").split("/", 3)
        if len(parts) != 4:
            self._send_json({"error": "下載連結無效。"}, HTTPStatus.NOT_FOUND)
            return
        _, token, job_id, filename = parts
        filename = Path(filename).name
        job = self.server.manager.shared_job(token, job_id)
        if not job:
            self._send_json(
                {"error": "下載連結無效或已失效。"},
                HTTPStatus.NOT_FOUND,
            )
            return
        self._send_package_file(job, filename)

    def _serve_local_download(self, job_id: str, filename: str) -> None:
        try:
            job = self.server.manager.snapshot(job_id)
        except KeyError:
            self._send_json(
                {"error": "搵唔到呢個轉換工作。"},
                HTTPStatus.NOT_FOUND,
            )
            return
        if job.get("status") != "done":
            self._send_json(
                {"error": "貼圖包尚未準備好。"},
                HTTPStatus.CONFLICT,
            )
            return
        self._send_package_file(job, Path(filename).name)

    def _send_package_file(
        self,
        job: dict[str, Any],
        filename: str,
    ) -> None:
        allowed_items = [
            *job.get("packages", []),
            *job.get("outputs", []),
        ]
        allowed = {item["filename"] for item in allowed_items}
        if filename not in allowed:
            self._send_json({"error": "搵唔到輸出檔案。"}, HTTPStatus.NOT_FOUND)
            return
        candidate = (Path(job["output_dir"]) / filename).resolve()
        if not candidate.is_file() or candidate.parent != Path(
            job["output_dir"]
        ).resolve():
            self._send_json({"error": "搵唔到輸出檔案。"}, HTTPStatus.NOT_FOUND)
            return
        extension = candidate.suffix.lower()
        if extension == ".webm":
            ascii_name = "telegram-video-sticker.webm"
            content_type = "video/webm"
        elif extension == ".webp":
            ascii_name = "whatsapp-animated-sticker.webp"
            content_type = "image/webp"
        elif extension == ".mp4":
            ascii_name = "sticker-maker-import.mp4"
            content_type = "video/mp4"
        else:
            ascii_name = "telegram-whatsapp-stickers.wastickers"
            content_type = "application/x-wastickers"
        encoded_name = urllib.parse.quote(filename)
        self._send_bytes(
            candidate.read_bytes(),
            content_type,
            extra_headers={
                "Content-Disposition": (
                    f'attachment; filename="{ascii_name}"; '
                    f"filename*=UTF-8''{encoded_name}"
                )
            },
        )


def _existing_instance() -> dict[str, Any] | None:
    if not SERVER_STATE.is_file():
        return None
    try:
        state = json.loads(SERVER_STATE.read_text(encoding="utf-8"))
        port = int(state["port"])
        instance = str(state["instance"])
        with urllib.request.urlopen(
            f"http://127.0.0.1:{port}/api/health",
            timeout=1.5,
        ) as response:
            health = json.loads(response.read().decode("utf-8"))
        if health.get("ok") and health.get("instance") == instance:
            return state
    except (OSError, ValueError, KeyError, json.JSONDecodeError):
        return None
    return None


def _open_app(port: int, app_key: str) -> None:
    encoded_key = urllib.parse.quote(app_key)
    webbrowser.open(f"http://127.0.0.1:{port}/?key={encoded_key}")


def run() -> None:
    LOCAL_DIR.mkdir(parents=True, exist_ok=True)
    existing = _existing_instance()
    if existing:
        if os.environ.get("TGWA_NO_BROWSER") != "1":
            _open_app(int(existing["port"]), str(existing["app_key"]))
        return

    app_key = secrets.token_urlsafe(28)
    instance_id = secrets.token_urlsafe(18)
    server = AppServer(("0.0.0.0", 0), app_key, instance_id)
    state = {
        "pid": os.getpid(),
        "port": server.server_port,
        "app_key": app_key,
        "instance": instance_id,
    }
    SERVER_STATE.write_text(
        json.dumps(state, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    if os.environ.get("TGWA_NO_BROWSER") != "1":
        threading.Timer(
            0.35,
            _open_app,
            args=(server.server_port, app_key),
        ).start()
    try:
        server.serve_forever(poll_interval=0.25)
    finally:
        server.server_close()
        try:
            current = json.loads(SERVER_STATE.read_text(encoding="utf-8"))
            if current.get("instance") == instance_id:
                SERVER_STATE.unlink(missing_ok=True)
        except (OSError, json.JSONDecodeError):
            pass
