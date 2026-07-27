import io
import json
import tempfile
import threading
import unittest
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from unittest.mock import patch

from PIL import Image
from tgwa.server import AppServer


class HttpSmokeTest(unittest.TestCase):
    def test_telegram_connect_and_publish_routes(self) -> None:
        app_key = "telegram-app-key"
        server = AppServer(("127.0.0.1", 0), app_key, "telegram-instance")
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        base = f"http://127.0.0.1:{server.server_port}"
        try:
            with patch.object(
                server.manager,
                "connect_telegram",
                return_value={
                    "bot_username": "RelayBot",
                    "start_url": "https://t.me/RelayBot?start=tgwa",
                    "users": [{"id": 456, "label": "測試"}],
                    "has_saved_token": True,
                },
            ) as connect:
                result = self._post_json(
                    f"{base}/api/telegram/connect",
                    {"token": "", "remember": True},
                    app_key,
                )
            self.assertEqual(result["bot_username"], "RelayBot")
            connect.assert_called_once()

            with patch.object(
                server.manager,
                "publish_telegram",
                return_value={
                    "ok": True,
                    "set_url": (
                        "https://t.me/addstickers/test_by_RelayBot"
                    ),
                },
            ) as publish:
                result = self._post_json(
                    f"{base}/api/jobs/video123/telegram",
                    {
                        "action": "create",
                        "user_id": 456,
                        "title": "Test",
                    },
                    app_key,
                )
            self.assertTrue(result["ok"])
            publish.assert_called_once()
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=5)

    def test_health_auth_share_qr_and_download(self) -> None:
        app_key = "test-app-key"
        server = AppServer(("127.0.0.1", 0), app_key, "test-instance")
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        base = f"http://127.0.0.1:{server.server_port}"

        try:
            with tempfile.TemporaryDirectory() as temp_dir:
                output = Path(temp_dir)
                package = output / "測試.wastickers"
                package.write_bytes(b"package-data")
                job_id = "job123"
                share_token = "share-secret"
                server.manager.jobs[job_id] = {
                    "id": job_id,
                    "status": "done",
                    "share_token": share_token,
                    "title": "測試貼圖",
                    "slug": "TestPack",
                    "output_dir": str(output),
                    "packages": [
                        {
                            "filename": package.name,
                            "size": package.stat().st_size,
                            "size_label": "0.0 KB",
                            "sticker_count": 3,
                            "animated_count": 0,
                        }
                    ],
                    "logs": [],
                    "share_url": (
                        f"{base}/share/{share_token}/{job_id}"
                    ),
                }

                health = self._json(f"{base}/api/health")
                self.assertTrue(health["ok"])

                with self.assertRaises(urllib.error.HTTPError) as unauthorized:
                    self._json(f"{base}/api/config")
                self.assertEqual(unauthorized.exception.code, 403)

                config = self._json(
                    f"{base}/api/config",
                    headers={"X-App-Key": app_key},
                )
                self.assertIn("has_token", config)

                with urllib.request.urlopen(
                    f"{base}/bridge-apk",
                    timeout=10,
                ) as response:
                    self.assertEqual(
                        response.headers.get_content_type(),
                        "application/vnd.android.package-archive",
                    )
                    self.assertIn(
                        "TGWA-Maker.apk",
                        response.headers["Content-Disposition"],
                    )
                    self.assertEqual(response.read(2), b"PK")

                qr_request = urllib.request.Request(
                    f"{base}/api/jobs/{job_id}/qr",
                    headers={"X-App-Key": app_key},
                )
                with urllib.request.urlopen(qr_request, timeout=5) as response:
                    self.assertEqual(response.headers.get_content_type(), "image/png")
                    self.assertIn(
                        "blob:",
                        response.headers["Content-Security-Policy"],
                    )
                    self.assertIn(
                        "media-src 'self' blob:",
                        response.headers["Content-Security-Policy"],
                    )
                    self.assertGreater(len(response.read()), 100)

                with urllib.request.urlopen(
                    f"{base}/share/{share_token}/{job_id}",
                    timeout=5,
                ) as response:
                    share_page = response.read().decode("utf-8")
                self.assertIn("測試貼圖", share_page)
                self.assertIn("3 張", share_page)

                self.assertIn("/bridge-apk", share_page)

                encoded_name = urllib.parse.quote(package.name)
                with urllib.request.urlopen(
                    f"{base}/download/{share_token}/{job_id}/{encoded_name}",
                    timeout=5,
                ) as response:
                    self.assertEqual(response.read(), b"package-data")

                local_request = urllib.request.Request(
                    f"{base}/api/jobs/{job_id}/download/{encoded_name}",
                    headers={"X-App-Key": app_key},
                )
                with urllib.request.urlopen(local_request, timeout=5) as response:
                    self.assertEqual(
                        response.headers.get_content_type(),
                        "application/x-wastickers",
                    )
                    self.assertEqual(response.read(), b"package-data")
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=5)

    def test_video_upload_share_and_download_types(self) -> None:
        app_key = "video-app-key"
        server = AppServer(("127.0.0.1", 0), app_key, "video-instance")
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        base = f"http://127.0.0.1:{server.server_port}"

        try:
            with tempfile.TemporaryDirectory() as temp_dir:
                root = Path(temp_dir)
                frames = [
                    Image.new("RGB", (64, 48), color)
                    for color in ("#25d366", "#2b9ef4", "#ffffff")
                ]
                buffer = io.BytesIO()
                frames[0].save(
                    buffer,
                    format="GIF",
                    save_all=True,
                    append_images=frames[1:],
                    duration=100,
                    loop=0,
                )
                for frame in frames:
                    frame.close()
                source = buffer.getvalue()

                with patch("tgwa.core.UPLOAD_ROOT", root / "uploads"):
                    request = urllib.request.Request(
                        f"{base}/api/video/upload",
                        data=source,
                        method="POST",
                        headers={
                            "X-App-Key": app_key,
                            "X-File-Name": urllib.parse.quote("測試.gif"),
                            "Content-Type": "application/octet-stream",
                        },
                    )
                    with urllib.request.urlopen(request, timeout=10) as response:
                        upload = json.loads(response.read().decode("utf-8"))
                    self.assertEqual(upload["width"], 64)
                    self.assertEqual(upload["height"], 48)
                    self.assertTrue(upload["upload_id"])

                output = root / "ready"
                output.mkdir()
                telegram = output / "測試 - Telegram.webm"
                whatsapp = output / "測試 - WhatsApp.webp"
                helper = output / "測試 - Sticker Maker Import.mp4"
                telegram.write_bytes(b"webm-data")
                whatsapp.write_bytes(b"webp-data")
                helper.write_bytes(b"mp4-data")
                job_id = "video123"
                share_token = "video-share"
                server.manager.jobs[job_id] = {
                    "id": job_id,
                    "kind": "video",
                    "status": "done",
                    "share_token": share_token,
                    "title": "影片貼圖",
                    "output_dir": str(output),
                    "packages": [],
                    "outputs": [
                        {
                            "filename": telegram.name,
                            "size_label": "0.0 KB",
                            "label": "Telegram · WEBM",
                            "mime_type": "video/webm",
                            "share_label": "分享 WEBM",
                            "import_hint": "用檔案上載",
                        },
                        {
                            "filename": whatsapp.name,
                            "size_label": "0.0 KB",
                            "label": "WhatsApp · Animated WebP",
                        },
                        {
                            "filename": helper.name,
                            "size_label": "0.0 KB",
                            "label": "Sticker Maker · MP4",
                            "mime_type": "video/mp4",
                            "share_label": "分享 MP4",
                            "import_hint": "用影片方式匯入",
                        },
                    ],
                    "logs": [],
                    "share_url": f"{base}/share/{share_token}/{job_id}",
                }

                with urllib.request.urlopen(
                    f"{base}/share/{share_token}/{job_id}",
                    timeout=5,
                ) as response:
                    share_page = response.read().decode("utf-8")
                self.assertIn("Telegram", share_page)
                self.assertIn(".webm", share_page)
                self.assertIn(".webp", share_page)
                self.assertIn("分享 WEBM", share_page)
                self.assertIn("分享 MP4", share_page)
                self.assertIn('/share.js', share_page)
                with urllib.request.urlopen(
                    f"{base}/share.js",
                    timeout=5,
                ) as response:
                    self.assertIn(
                        response.headers.get_content_type(),
                        {"application/javascript", "text/javascript"},
                    )
                    self.assertIn(
                        "data-share-file",
                        response.read().decode("utf-8"),
                    )

                for path, expected_type, expected_body in (
                    (telegram, "video/webm", b"webm-data"),
                    (whatsapp, "image/webp", b"webp-data"),
                    (helper, "video/mp4", b"mp4-data"),
                ):
                    encoded_name = urllib.parse.quote(path.name)
                    request = urllib.request.Request(
                        f"{base}/api/jobs/{job_id}/download/{encoded_name}",
                        headers={"X-App-Key": app_key},
                    )
                    with urllib.request.urlopen(request, timeout=5) as response:
                        self.assertEqual(
                            response.headers.get_content_type(),
                            expected_type,
                        )
                        self.assertEqual(response.read(), expected_body)
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=5)

    @staticmethod
    def _json(
        url: str,
        headers: dict[str, str] | None = None,
    ) -> dict:
        request = urllib.request.Request(url, headers=headers or {})
        with urllib.request.urlopen(request, timeout=5) as response:
            return json.loads(response.read().decode("utf-8"))

    @staticmethod
    def _post_json(url: str, payload: dict, app_key: str) -> dict:
        request = urllib.request.Request(
            url,
            data=json.dumps(payload).encode("utf-8"),
            method="POST",
            headers={
                "X-App-Key": app_key,
                "Content-Type": "application/json",
            },
        )
        with urllib.request.urlopen(request, timeout=5) as response:
            return json.loads(response.read().decode("utf-8"))


if __name__ == "__main__":
    unittest.main()
