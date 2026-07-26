import json
import tempfile
import threading
import unittest
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

from tgwa.server import AppServer


class HttpSmokeTest(unittest.TestCase):
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
                    self.assertGreater(len(response.read()), 100)

                with urllib.request.urlopen(
                    f"{base}/share/{share_token}/{job_id}",
                    timeout=5,
                ) as response:
                    share_page = response.read().decode("utf-8")
                self.assertIn("測試貼圖", share_page)
                self.assertIn("3 張", share_page)

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
                    self.assertEqual(response.read(), b"package-data")
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


if __name__ == "__main__":
    unittest.main()
