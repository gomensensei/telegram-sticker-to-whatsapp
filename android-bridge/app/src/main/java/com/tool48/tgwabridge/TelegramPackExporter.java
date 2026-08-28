package com.tool48.tgwabridge;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

final class TelegramPackExporter {
    interface ProgressListener {
        void onProgress(int percent, int stickerNumber, int stickerCount);
    }

    static final class Result {
        final ArrayList<Uri> uris;
        final ArrayList<String> emojis;
        final ArrayList<File> files;
        final ArrayList<String> formats;
        final boolean recreatedVideo;

        Result(
            ArrayList<Uri> uris,
            ArrayList<String> emojis,
            ArrayList<File> files,
            ArrayList<String> formats,
            boolean recreatedVideo
        ) {
            this.uris = uris;
            this.emojis = emojis;
            this.files = files;
            this.formats = formats;
            this.recreatedVideo = recreatedVideo;
        }
    }

    private TelegramPackExporter() {
    }

    static Result prepare(
        Context context,
        Pack pack,
        ProgressListener listener
    ) throws IOException {
        if (pack.stickers.isEmpty() || pack.stickers.size() > 120) {
            throw new IOException(
                "Telegram export needs between 1 and 120 stickers."
            );
        }
        File exportRoot = new File(context.getCacheDir(), "telegram-export");
        if (!exportRoot.isDirectory() && !exportRoot.mkdirs()) {
            throw new IOException("Cannot create Telegram export cache.");
        }
        purgeOld(exportRoot);
        String session = UUID.randomUUID().toString().replace("-", "");
        File directory = new File(exportRoot, session);
        if (!directory.mkdirs()) {
            throw new IOException("Cannot create Telegram export folder.");
        }
        File packDirectory = PackStore.packDirectory(
            context,
            pack.identifier
        );
        ArrayList<Uri> uris = new ArrayList<>();
        ArrayList<String> emojis = new ArrayList<>();
        ArrayList<File> files = new ArrayList<>();
        ArrayList<String> formats = new ArrayList<>();
        boolean recreatedVideo = false;
        try {
            for (int index = 0; index < pack.stickers.size(); index++) {
                Sticker sticker = pack.stickers.get(index);
                File current = child(packDirectory, sticker.fileName);
                File original = sticker.telegramSourceFile.isEmpty()
                    ? null
                    : child(packDirectory, sticker.telegramSourceFile);
                String extension;
                File destination;
                if (
                    original != null
                    && original.isFile()
                    && validOriginal(sticker.telegramSourceFormat)
                ) {
                    extension = extension(sticker.telegramSourceFormat);
                    destination = new File(
                        directory,
                        String.format(java.util.Locale.ROOT, "%03d.%s", index + 1, extension)
                    );
                    copy(original, destination, 8 * 1024 * 1024);
                } else if (!pack.animated) {
                    extension = "webp";
                    destination = new File(
                        directory,
                        String.format(java.util.Locale.ROOT, "%03d.webp", index + 1)
                    );
                    copy(current, destination, 128 * 1024);
                } else {
                    recreatedVideo = true;
                    extension = "webm";
                    destination = new File(
                        directory,
                        String.format(java.util.Locale.ROOT, "%03d.webm", index + 1)
                    );
                    final int stickerIndex = index;
                    TelegramVp9Encoder.encode(
                        current,
                        destination,
                        percent -> {
                            if (listener != null) {
                                listener.onProgress(
                                    percent,
                                    stickerIndex + 1,
                                    pack.stickers.size()
                                );
                            }
                        }
                    );
                }
                uris.add(
                    new Uri.Builder()
                        .scheme("content")
                        .authority(TelegramExportProvider.AUTHORITY)
                        .appendPath(session)
                        .appendPath(destination.getName())
                        .build()
                );
                files.add(destination);
                formats.add(
                    "tgs".equals(extension)
                        ? "animated"
                        : ("webm".equals(extension) ? "video" : "static")
                );
                emojis.add(
                    sticker.emojis.isEmpty()
                        ? "\uD83D\uDE00"
                        : sticker.emojis.get(0)
                );
                if (listener != null) {
                    listener.onProgress(
                        100,
                        index + 1,
                        pack.stickers.size()
                    );
                }
            }
            return new Result(
                uris,
                emojis,
                files,
                formats,
                recreatedVideo
            );
        } catch (IOException | RuntimeException error) {
            try {
                PackStore.deleteTree(directory);
            } catch (IOException ignored) {
                // Preserve the export error.
            }
            throw error;
        }
    }

    private static File child(File directory, String name) throws IOException {
        if (name == null || !name.matches("[A-Za-z0-9_.-]{1,128}")) {
            throw new IOException("Sticker pack contains an unsafe file name.");
        }
        File result = new File(directory, name);
        if (!result.isFile()) {
            throw new IOException("Sticker file is missing: " + name);
        }
        return result;
    }

    private static boolean validOriginal(String format) {
        return "static".equals(format)
            || "animated".equals(format)
            || "video".equals(format);
    }

    private static String extension(String format) {
        if ("animated".equals(format)) {
            return "tgs";
        }
        if ("video".equals(format)) {
            return "webm";
        }
        return "webp";
    }

    private static void copy(File source, File target, int maximum)
        throws IOException {
        try (
            FileInputStream input = new FileInputStream(source);
            FileOutputStream output = new FileOutputStream(target)
        ) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > maximum) {
                    throw new IOException("Telegram export file is too large.");
                }
                output.write(buffer, 0, count);
            }
        }
    }

    private static void purgeOld(File root) {
        File[] children = root.listFiles(File::isDirectory);
        if (children == null) {
            return;
        }
        long cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L;
        for (File child : children) {
            if (child.lastModified() < cutoff) {
                try {
                    PackStore.deleteTree(child);
                } catch (IOException ignored) {
                    // A Telegram client may still be reading an older export.
                }
            }
        }
    }
}
