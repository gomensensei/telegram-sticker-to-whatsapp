import tempfile
import unittest
import zipfile
from pathlib import Path

from tgwa.core import (
    ConfigStore,
    UserInputError,
    inspect_wastickers,
    label_split_packages,
    normalize_pack_link,
    whatsapp_part_sizes,
)
from sticker_convert.utils.files.sanitize_filename import sanitize_filename


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

    def test_inspect_wastickers(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            package = Path(temp_dir) / "test.wastickers"
            with zipfile.ZipFile(package, "w") as archive:
                archive.writestr("cover.png", b"cover")
                archive.writestr("title.txt", "Test")
                archive.writestr("author.txt", "Codex")
                archive.writestr("001.png", b"static")
                archive.writestr("002.webp", b"RIFFxxxxWEBPANIM")
            info = inspect_wastickers(package)
            self.assertEqual(info["sticker_count"], 2)
            self.assertEqual(info["animated_count"], 1)

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


if __name__ == "__main__":
    unittest.main()
