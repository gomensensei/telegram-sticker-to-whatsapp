import io
import gzip
import json
import subprocess
import sys
import tempfile
import time
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

from PIL import Image, ImageDraw
from tgwa.core import (
    ConfigStore,
    JobManager,
    inspect_video_sticker,
    save_video_upload,
)


LOTTIE_SAMPLE = {
    "v": "5.7.4",
    "fr": 15,
    "ip": 0,
    "op": 15,
    "w": 512,
    "h": 512,
    "nm": "TGWA smoke animation",
    "ddd": 0,
    "assets": [],
    "layers": [
        {
            "ddd": 0,
            "ind": 1,
            "ty": 4,
            "nm": "Pulse",
            "sr": 1,
            "ks": {
                "o": {"a": 0, "k": 100},
                "r": {"a": 0, "k": 0},
                "p": {"a": 0, "k": [256, 256, 0]},
                "a": {"a": 0, "k": [0, 0, 0]},
                "s": {
                    "a": 1,
                    "k": [
                        {
                            "t": 0,
                            "s": [60, 60, 100],
                            "e": [100, 100, 100],
                        },
                        {"t": 7, "s": [100, 100, 100], "e": [60, 60, 100]},
                        {"t": 15, "s": [60, 60, 100]},
                    ],
                },
            },
            "ao": 0,
            "shapes": [
                {
                    "ty": "gr",
                    "it": [
                        {
                            "d": 1,
                            "ty": "el",
                            "s": {"a": 0, "k": [240, 240]},
                            "p": {"a": 0, "k": [0, 0]},
                            "nm": "Ellipse",
                        },
                        {
                            "ty": "fl",
                            "c": {"a": 0, "k": [0.14, 0.83, 0.4, 1]},
                            "o": {"a": 0, "k": 100},
                            "r": 1,
                            "nm": "Fill",
                        },
                        {
                            "ty": "tr",
                            "p": {"a": 0, "k": [0, 0]},
                            "a": {"a": 0, "k": [0, 0]},
                            "s": {"a": 0, "k": [100, 100]},
                            "r": {"a": 0, "k": 0},
                            "o": {"a": 0, "k": 100},
                            "sk": {"a": 0, "k": 0},
                            "sa": {"a": 0, "k": 0},
                            "nm": "Transform",
                        },
                    ],
                    "nm": "Circle",
                }
            ],
            "ip": 0,
            "op": 15,
            "st": 0,
            "bm": 0,
        }
    ],
}


class ConversionSmokeTest(unittest.TestCase):
    def test_static_and_tgs_to_wastickers(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source = root / "source"
            output = root / "ready"
            source.mkdir()
            output.mkdir()

            for index, color in enumerate(("#2b9ef4", "#25d366"), start=1):
                image = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
                draw = ImageDraw.Draw(image)
                margin = 90 + (index * 8)
                draw.rounded_rectangle(
                    (margin, margin, 512 - margin, 512 - margin),
                    radius=54,
                    fill=color,
                )
                image.save(source / f"{index:03d}.png")

            (source / "003.tgs").write_bytes(
                gzip.compress(
                    json.dumps(LOTTIE_SAMPLE, separators=(",", ":")).encode("utf-8")
                )
            )

            command = [
                sys.executable,
                "-m",
                "sticker_convert",
                "--lang",
                "en_US",
                "--no-confirm",
                "--no-progress",
                "--input-dir",
                str(source),
                "--output-dir",
                str(output),
                "--preset",
                "whatsapp",
                "--export-whatsapp",
                "--title",
                "TGWA Smoke",
                "--author",
                "Codex",
                "--steps",
                "3",
                "--processes",
                "1",
            ]
            result = subprocess.run(
                command,
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                timeout=120,
            )
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

            packages = list(output.glob("*.wastickers"))
            self.assertEqual(len(packages), 1)
            with zipfile.ZipFile(packages[0]) as archive:
                sticker_files = [
                    name
                    for name in archive.namelist()
                    if Path(name).suffix.lower() in {".png", ".webp"}
                    and Path(name).name.lower() != "cover.png"
                ]
                self.assertEqual(len(sticker_files), 3)
                for name in sticker_files:
                    self.assertLessEqual(len(archive.read(name)), 500_000)

    def test_video_to_telegram_and_whatsapp_stickers(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            frames = []
            for index in range(10):
                image = Image.new("RGB", (320, 180), "#0b1716")
                draw = ImageDraw.Draw(image)
                left = 20 + index * 18
                draw.ellipse(
                    (left, 48, left + 84, 132),
                    fill="#25d366",
                    outline="#ffffff",
                    width=4,
                )
                frames.append(image)
            source_buffer = io.BytesIO()
            frames[0].save(
                source_buffer,
                format="GIF",
                save_all=True,
                append_images=frames[1:],
                duration=100,
                loop=0,
            )
            for frame in frames:
                frame.close()

            upload_root = root / "uploads"
            output_root = root / "output"
            with (
                patch("tgwa.core.UPLOAD_ROOT", upload_root),
                patch("tgwa.core.OUTPUT_ROOT", output_root),
            ):
                source_bytes = source_buffer.getvalue()
                upload = save_video_upload(
                    io.BytesIO(source_bytes),
                    len(source_bytes),
                    "motion.gif",
                )
                manager = JobManager(
                    ConfigStore(root / "config.json"),
                    lambda: 4173,
                )
                job = manager.start_video(
                    {
                        "upload_id": upload["upload_id"],
                        "title": "Motion Test",
                        "start": 0.1,
                        "duration": 0.6,
                        "scale": 0.9,
                        "offset_x": 12,
                        "offset_y": -8,
                        "background": "transparent",
                    }
                )
                deadline = time.monotonic() + 120
                while time.monotonic() < deadline:
                    snapshot = manager.snapshot(job["id"])
                    if snapshot["status"] in {"done", "error", "cancelled"}:
                        break
                    time.sleep(0.1)
                else:
                    self.fail("Video conversion timed out")

                self.assertEqual(
                    snapshot["status"],
                    "done",
                    "\n".join(snapshot.get("logs", []))
                    + "\n"
                    + str(snapshot.get("error")),
                )
                self.assertEqual(len(snapshot["outputs"]), 3)
                ready = Path(snapshot["output_dir"])
                telegram = next(ready.glob("*.webm"))
                whatsapp = next(ready.glob("*.webp"))
                helper = next(ready.glob("*.mp4"))
                telegram_info = inspect_video_sticker(telegram, "Telegram")
                whatsapp_info = inspect_video_sticker(whatsapp, "WhatsApp")
                self.assertLessEqual(telegram_info["size"], 256_000)
                self.assertLessEqual(whatsapp_info["size"], 500_000)
                self.assertAlmostEqual(telegram_info["duration"], 0.6, delta=0.1)
                self.assertAlmostEqual(whatsapp_info["duration"], 0.6, delta=0.1)
                self.assertGreater(helper.stat().st_size, 0)


if __name__ == "__main__":
    unittest.main()
