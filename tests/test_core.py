import io
import json
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

from PIL import Image, ImageDraw
from tgwa.core import (
    build_animated_wastickers,
    ConfigStore,
    JobManager,
    UserInputError,
    inspect_sticker_maker_video,
    inspect_wastickers,
    label_split_packages,
    normalize_pack_link,
    probe_video,
    render_video_clip,
    save_video_upload,
    telegram_bot_connection,
    telegram_sticker_set_name,
    whatsapp_part_sizes,
)
from sticker_convert.utils.files.sanitize_filename import sanitize_filename


def sample_video_bytes() -> bytes:
    frames = []
    for index in range(10):
        image = Image.new("RGBA", (320, 180), (0, 0, 0, 0))
        draw = ImageDraw.Draw(image)
        left = 18 + index * 18
        draw.rounded_rectangle(
            (left, 44, left + 72, 136),
            radius=18,
            fill=(43, 158, 244, 255),
        )
        frames.append(image)
    buffer = io.BytesIO()
    frames[0].save(
        buffer,
        format="GIF",
        save_all=True,
        append_images=frames[1:],
        duration=100,
        loop=0,
        disposal=2,
    )
    for frame in frames:
        frame.close()
    return buffer.getvalue()


def animated_webp_marker() -> bytes:
    chunks = bytearray()
    chunks.extend(b"ANIM")
    chunks.extend((6).to_bytes(4, "little"))
    chunks.extend(b"\x00" * 6)
    for _ in range(2):
        frame = bytearray(16)
        frame[12:15] = (80).to_bytes(3, "little")
        chunks.extend(b"ANMF")
        chunks.extend(len(frame).to_bytes(4, "little"))
        chunks.extend(frame)
    return (
        b"RIFF"
        + (len(chunks) + 4).to_bytes(4, "little")
        + b"WEBP"
        + bytes(chunks)
    )


def sticker_webp(*, animated: bool) -> bytes:
    buffer = io.BytesIO()
    first = Image.new("RGBA", (512, 512), "#25d366")
    if animated:
        second = Image.new("RGBA", (512, 512), "#2b9ef4")
        first.save(
            buffer,
            format="WEBP",
            save_all=True,
            append_images=[second],
            duration=80,
            loop=0,
            lossless=True,
        )
        second.close()
    else:
        first.save(buffer, format="WEBP", lossless=True)
    first.close()
    return buffer.getvalue()


class CoreTests(unittest.TestCase):
    def test_normalize_pack_link(self) -> None:
        self.assertEqual(
            normalize_pack_link("https://t.me/addstickers/HotCherry"),
            (
                "https://t.me/addstickers/HotCherry",
                "addstickers",
                "HotCherry",
            ),
        )
        self.assertEqual(
            normalize_pack_link("telegram.me/addemoji/My_Emoji/"),
            (
                "https://t.me/addemoji/My_Emoji",
                "addemoji",
                "My_Emoji",
            ),
        )
        with self.assertRaises(UserInputError):
            normalize_pack_link("https://example.com/not-a-pack")

    def test_config_token_round_trip(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            store = ConfigStore(Path(temp_dir) / "config.json")
            token = "123456789:ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghi"
            store.save("測試作者", token)
            self.assertEqual(store.token(), token)
            self.assertEqual(
                store.public(),
                {"has_token": True, "author": "測試作者"},
            )
            self.assertNotIn(
                token,
                (Path(temp_dir) / "config.json").read_text(encoding="utf-8"),
            )
            store.forget_token()
            self.assertFalse(store.public()["has_token"])

    def test_telegram_connection_and_sticker_set_name(self) -> None:
        token = "123456789:ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghi"

        def fake_api(_token: str, method: str, _params=None):
            self.assertEqual(_token, token)
            if method == "getMe":
                return {
                    "id": 99,
                    "is_bot": True,
                    "first_name": "Sticker Relay",
                    "username": "RelayBot",
                }
            if method == "getUpdates":
                return [
                    {
                        "update_id": 1,
                        "message": {
                            "chat": {"id": 456, "type": "private"},
                            "from": {
                                "id": 456,
                                "is_bot": False,
                                "first_name": "測試",
                                "username": "tester",
                            },
                        },
                    }
                ]
            raise AssertionError(method)

        with patch("tgwa.core._telegram_api", side_effect=fake_api):
            connection = telegram_bot_connection(token)
        self.assertEqual(connection["bot_username"], "RelayBot")
        self.assertEqual(connection["users"][0]["id"], 456)
        self.assertEqual(
            telegram_sticker_set_name(
                "Kosaki Motion",
                "RelayBot",
                "fallback",
            ),
            "Kosaki_Motion_by_RelayBot",
        )
        self.assertEqual(
            telegram_sticker_set_name(
                "https://t.me/addstickers/Kosaki_by_RelayBot",
                "RelayBot",
                "fallback",
            ),
            "Kosaki_by_RelayBot",
        )

    def test_publish_video_sticker_creates_telegram_pack(self) -> None:
        token = "123456789:ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghi"
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            config = ConfigStore(root / "config.json")
            config.save_token(token)
            manager = JobManager(config, lambda: 8000)
            output = root / "ready"
            output.mkdir()
            sticker = output / "動畫 - Telegram.webm"
            sticker.write_bytes(b"valid-webm-placeholder")
            manager.jobs["videojob"] = {
                "id": "videojob",
                "kind": "video",
                "status": "done",
                "title": "動畫貼圖",
                "output_dir": str(output),
                "outputs": [
                    {
                        "filename": sticker.name,
                        "platform": "Telegram",
                    }
                ],
            }
            api_calls = []

            def fake_api(_token: str, method: str, params=None):
                api_calls.append((method, params))
                if method == "getMe":
                    return {"username": "RelayBot"}
                return True

            with (
                patch("tgwa.core._telegram_api", side_effect=fake_api),
                patch(
                    "tgwa.core.upload_telegram_video_sticker",
                    return_value="telegram-file-id",
                ) as upload,
            ):
                result = manager.publish_telegram(
                    "videojob",
                    {
                        "action": "create",
                        "user_id": 456,
                        "user_label": "測試",
                        "title": "動畫貼圖",
                        "name": "kosaki_motion",
                        "emoji": "✨",
                    },
                )

            upload.assert_called_once_with(token, 456, sticker)
            create_call = next(
                call for call in api_calls if call[0] == "createNewStickerSet"
            )
            self.assertEqual(
                create_call[1]["name"],
                "kosaki_motion_by_RelayBot",
            )
            initial = json.loads(create_call[1]["stickers"])[0]
            self.assertEqual(initial["format"], "video")
            self.assertEqual(initial["sticker"], "telegram-file-id")
            self.assertEqual(
                result["set_url"],
                "https://t.me/addstickers/kosaki_motion_by_RelayBot",
            )
            self.assertEqual(
                config.public()["telegram_connection"]["user_id"],
                456,
            )

    def test_inspect_wastickers(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            package = Path(temp_dir) / "test.wastickers"
            with zipfile.ZipFile(package, "w") as archive:
                archive.writestr("cover.png", b"cover")
                archive.writestr("title.txt", "Test")
                archive.writestr("author.txt", "Codex")
                archive.writestr("001.png", b"static")
                archive.writestr("002.webp", animated_webp_marker())
            info = inspect_wastickers(package)
            self.assertEqual(info["sticker_count"], 2)
            self.assertEqual(info["animated_count"], 1)
            self.assertEqual(info["static_count"], 1)
            self.assertEqual(info["kind"], "mixed")

    def test_invalid_animated_webp_is_not_accepted_as_motion(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            package = Path(temp_dir) / "invalid.wastickers"
            invalid = (
                b"RIFF"
                + (18).to_bytes(4, "little")
                + b"WEBP"
                + b"ANIM"
                + (6).to_bytes(4, "little")
                + b"\x00" * 6
            )
            with zipfile.ZipFile(package, "w") as archive:
                archive.writestr("001.webp", invalid)
            info = inspect_wastickers(package)
            self.assertEqual(info["animated_count"], 0)
            self.assertEqual(info["invalid_animated_count"], 1)
            self.assertEqual(info["kind"], "invalid")

    def test_build_animated_wastickers(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            sources = []
            for index, color in enumerate(
                ("#25d366", "#2b9ef4", "#ffcc00"),
                start=1,
            ):
                frames = [
                    Image.new("RGBA", (512, 512), color),
                    Image.new("RGBA", (512, 512), "#ffffff"),
                ]
                source = root / f"source-{index}.webp"
                frames[0].save(
                    source,
                    format="WEBP",
                    save_all=True,
                    append_images=frames[1:],
                    duration=120,
                    loop=0,
                    lossless=True,
                    method=4,
                )
                for frame in frames:
                    frame.close()
                sources.append(source)

            package = root / "Motion Pack.wastickers"
            info = build_animated_wastickers(
                sources,
                package,
                title="Motion Pack",
                author="Tool48",
            )

            self.assertEqual(info["sticker_count"], 3)
            self.assertEqual(info["animated_count"], 3)
            with zipfile.ZipFile(package) as archive:
                self.assertEqual(
                    {
                        "cover.png",
                        "title.txt",
                        "author.txt",
                        "contents.json",
                        "001.webp",
                        "002.webp",
                        "003.webp",
                    },
                    set(archive.namelist()),
                )
                contents = json.loads(
                    archive.read("contents.json").decode("utf-8")
                )
                pack = contents["sticker_packs"][0]
                self.assertTrue(pack["animated_sticker_pack"])
                self.assertEqual(pack["name"], "Motion Pack")
                self.assertEqual(len(pack["stickers"]), 3)

    def test_video_pack_queue_and_build(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            manager = JobManager(
                ConfigStore(root / "config.json"),
                lambda: 8123,
            )
            for index in range(3):
                frames = [
                    Image.new("RGBA", (512, 512), "#25d366"),
                    Image.new("RGBA", (512, 512), "#ffffff"),
                ]
                source = root / f"motion-{index}.webp"
                frames[0].save(
                    source,
                    format="WEBP",
                    save_all=True,
                    append_images=frames[1:],
                    duration=120,
                    loop=0,
                    lossless=True,
                    method=4,
                )
                for frame in frames:
                    frame.close()
                manager.jobs[f"job-{index}"] = {
                    "id": f"job-{index}",
                    "kind": "video",
                    "status": "done",
                    "title": f"Motion {index + 1}",
                    "output_dir": str(root),
                    "outputs": [
                        {
                            "filename": source.name,
                            "platform": "WhatsApp",
                        }
                    ],
                }
                manager.add_video_to_pack(f"job-{index}")

            self.assertTrue(manager.video_pack_snapshot()["can_build"])
            with patch("tgwa.core.OUTPUT_ROOT", root / "output"):
                result = manager.build_video_pack(
                    {"title": "Motion Pack", "author": "Tool48"}
                )
            self.assertEqual(result["kind"], "whatsapp_pack")
            self.assertEqual(result["packages"][0]["sticker_count"], 3)
            self.assertEqual(manager.video_pack_snapshot()["count"], 0)

    def test_mixed_pack_splits_static_and_animated(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            output = Path(temp_dir)
            title = "Mixed Pack"
            source = output / f"{title}.wastickers"
            animated_data = sticker_webp(animated=True)
            static_data = sticker_webp(animated=False)
            with zipfile.ZipFile(source, "w") as archive:
                archive.writestr("title.txt", title)
                archive.writestr("author.txt", "Tool48")
                archive.writestr("cover.png", b"cover")
                for index in range(4):
                    archive.writestr(
                        f"animated-{index}.webp",
                        animated_data,
                    )
                for index in range(5):
                    archive.writestr(
                        f"static-{index}.webp",
                        static_data,
                    )

            packages = label_split_packages(output, title, [source])

            self.assertEqual(
                [path.name for path in packages],
                [
                    "Mixed Pack - Animated.wastickers",
                    "Mixed Pack - Static.wastickers",
                ],
            )
            infos = [inspect_wastickers(path) for path in packages]
            self.assertEqual(
                [
                    (
                        info["kind"],
                        info["sticker_count"],
                        info["metadata_matches"],
                    )
                    for info in infos
                ],
                [
                    ("animated", 4, True),
                    ("static", 5, True),
                ],
            )
            for path, expected_flag in zip(
                packages,
                (True, False),
            ):
                with zipfile.ZipFile(path) as archive:
                    contents = json.loads(
                        archive.read("contents.json").decode("utf-8")
                    )
                    sticker_bytes = [
                        archive.read(name)
                        for name in archive.namelist()
                        if name.endswith(".webp")
                    ]
                self.assertEqual(
                    contents["sticker_packs"][0][
                        "animated_sticker_pack"
                    ],
                    expected_flag,
                )
                expected_bytes = (
                    animated_data
                    if expected_flag
                    else static_data
                )
                self.assertTrue(sticker_bytes)
                self.assertTrue(
                    all(data == expected_bytes for data in sticker_bytes)
                )

    def test_mixed_pack_rejects_group_below_three(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            output = Path(temp_dir)
            source = output / "Too Small.wastickers"
            with zipfile.ZipFile(source, "w") as archive:
                archive.writestr("author.txt", "Tool48")
                archive.writestr("cover.png", b"cover")
                for index in range(3):
                    archive.writestr(
                        f"animated-{index}.webp",
                        animated_webp_marker(),
                    )
                for index in range(2):
                    archive.writestr(
                        f"static-{index}.webp",
                        b"RIFF\x04\x00\x00\x00WEBP",
                    )

            with self.assertRaisesRegex(
                UserInputError,
                "Static 2",
            ):
                label_split_packages(
                    output,
                    "Too Small",
                    [source],
                )

    def test_large_pack_gets_numbered_parts(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            output = Path(temp_dir)
            title = "大型貼圖包"
            counts = (30, 30, 5)
            source_paths = []
            for index, count in enumerate(counts):
                suffix = "" if index == 0 else f"-{index}"
                package = output / sanitize_filename(
                    f"{title}{suffix}.wastickers"
                )
                source_paths.append(package)
                with zipfile.ZipFile(package, "w") as archive:
                    archive.writestr("title.txt", title)
                    archive.writestr("author.txt", "Codex")
                    archive.writestr("cover.png", b"cover")
                    for sticker_index in range(count):
                        archive.writestr(
                            f"{sticker_index:03d}.png",
                            b"sticker",
                        )

            parts = label_split_packages(output, title, source_paths)

            self.assertEqual(
                [path.name for path in parts],
                [
                    sanitize_filename(f"{title} - Part 1.wastickers"),
                    sanitize_filename(f"{title} - Part 2.wastickers"),
                    sanitize_filename(f"{title} - Part 3.wastickers"),
                ],
            )
            self.assertEqual(
                [inspect_wastickers(path)["sticker_count"] for path in parts],
                [30, 30, 5],
            )
            for part_number, package in enumerate(parts, start=1):
                with zipfile.ZipFile(package) as archive:
                    self.assertEqual(
                        archive.read("title.txt").decode("utf-8").strip(),
                        f"{title} - Part {part_number}",
                    )
            for source in source_paths:
                self.assertFalse(source.exists())

    def test_last_part_is_rebalanced_to_whatsapp_minimum(self) -> None:
        self.assertEqual(whatsapp_part_sizes(31), [28, 3])
        self.assertEqual(whatsapp_part_sizes(32), [29, 3])
        self.assertEqual(whatsapp_part_sizes(33), [30, 3])
        self.assertEqual(whatsapp_part_sizes(61), [30, 28, 3])

        with tempfile.TemporaryDirectory() as temp_dir:
            output = Path(temp_dir)
            title = "Thirty One"
            source_paths = []
            for index, count in enumerate((30, 1)):
                suffix = "" if index == 0 else f"-{index}"
                package = output / f"{title}{suffix}.wastickers"
                source_paths.append(package)
                with zipfile.ZipFile(package, "w") as archive:
                    archive.writestr("title.txt", title)
                    archive.writestr("author.txt", "Codex")
                    archive.writestr("cover.png", b"cover")
                    for sticker_index in range(count):
                        archive.writestr(
                            f"{sticker_index:03d}.webp",
                            b"RIFFxxxxWEBP",
                        )

            parts = label_split_packages(output, title, source_paths)
            self.assertEqual(
                [inspect_wastickers(path)["sticker_count"] for path in parts],
                [28, 3],
            )

    def test_video_upload_probe_and_render(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            upload_root = root / "uploads"
            source_bytes = sample_video_bytes()
            with patch("tgwa.core.UPLOAD_ROOT", upload_root):
                upload = save_video_upload(
                    io.BytesIO(source_bytes),
                    len(source_bytes),
                    "../sample.gif",
                )
                source = upload_root / upload["upload_id"] / upload["filename"]

            info = probe_video(source)
            self.assertEqual((info["width"], info["height"]), (320, 180))
            self.assertGreaterEqual(info["duration"], 0.9)
            self.assertGreater(info["fps"], 0)

            output = root / "clip.webp"
            helper = root / "clip-import.mp4"
            rendered = render_video_clip(
                source,
                output,
                start=0.1,
                duration=0.6,
                scale=0.8,
                offset_x=25,
                offset_y=-20,
                background="transparent",
                fps=10,
                helper_destination=helper,
            )
            self.assertEqual(rendered["frames"], 6)
            with Image.open(output) as image:
                self.assertEqual(image.size, (512, 512))
                self.assertEqual(image.n_frames, 6)
                self.assertEqual(image.info.get("loop"), 0)
            helper_info = inspect_sticker_maker_video(helper)
            self.assertEqual(helper_info["dimensions"], "512×512")
            self.assertAlmostEqual(helper_info["duration"], 0.6, delta=0.1)


if __name__ == "__main__":
    unittest.main()
