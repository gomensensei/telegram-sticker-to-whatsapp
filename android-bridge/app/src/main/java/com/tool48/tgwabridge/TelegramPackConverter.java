package com.tool48.tgwabridge;

import android.content.Context;
import android.net.Uri;

import org.json.JSONException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class TelegramPackConverter {
    enum Stage {
        READING_PACK,
        REUSING_STICKER,
        DOWNLOADING_STICKER,
        CONVERTING_STICKER,
        BUILDING_PACKS,
        COMPLETE
    }

    static final class Progress {
        final int percent;
        final Stage stage;
        final int stickerNumber;
        final int stickerCount;
        final int stickerPercent;

        Progress(
            int percent,
            Stage stage,
            int stickerNumber,
            int stickerCount,
            int stickerPercent
        ) {
            this.percent = percent;
            this.stage = stage;
            this.stickerNumber = stickerNumber;
            this.stickerCount = stickerCount;
            this.stickerPercent = stickerPercent;
        }
    }

    interface ProgressListener {
        void onProgress(Progress progress);
    }

    static final class Result {
        final TelegramApiClient.StickerSet source;
        final List<Pack> packs;
        final int staticCount;
        final int animatedCount;
        final int newCount;
        final int reusedCount;

        Result(
            TelegramApiClient.StickerSet source,
            List<Pack> packs,
            int staticCount,
            int animatedCount,
            int newCount,
            int reusedCount
        ) {
            this.source = source;
            this.packs = packs;
            this.staticCount = staticCount;
            this.animatedCount = animatedCount;
            this.newCount = newCount;
            this.reusedCount = reusedCount;
        }
    }

    private static final class ExistingSticker {
        final File rendered;
        final File telegramSource;
        final String telegramFormat;
        final boolean animated;

        ExistingSticker(
            File rendered,
            File telegramSource,
            String telegramFormat,
            boolean animated
        ) {
            this.rendered = rendered;
            this.telegramSource = telegramSource;
            this.telegramFormat = telegramFormat;
            this.animated = animated;
        }
    }

    private TelegramPackConverter() {
    }

    static Result convert(
        Context context,
        String link,
        String token,
        String publisher,
        ProgressListener listener
    ) throws IOException, JSONException {
        String shortName = TelegramPackLink.shortName(link);
        TelegramApiClient client = new TelegramApiClient(token);
        update(listener, 1, Stage.READING_PACK, 0, 0, 0);
        TelegramApiClient.StickerSet set = client.getStickerSet(shortName);
        if (set.stickers.size() > 200) {
            throw new IOException(
                "This pack has more than 200 stickers and is too large "
                    + "for a single mobile conversion."
            );
        }
        int staticCount = 0;
        int animatedCount = 0;
        for (TelegramApiClient.RemoteSticker sticker : set.stickers) {
            if (sticker.kind == TelegramApiClient.Kind.STATIC) {
                staticCount++;
            } else {
                animatedCount++;
            }
        }
        List<Pack> sourcePacks = PackStore.findTelegramSource(
            context,
            set.name
        );
        if (sourcePacks.isEmpty()) {
            sourcePacks = migrateLegacyPacks(context, set);
        }
        Map<String, ExistingSticker> existing = existingStickers(
            context,
            sourcePacks
        );

        File work = new File(
            context.getCacheDir(),
            "telegram-" + UUID.randomUUID().toString()
        );
        if (!work.mkdirs()) {
            throw new IOException(
                "Cannot create Telegram conversion workspace."
            );
        }
        List<GeneratedPackBuilder.Item> staticItems = new ArrayList<>();
        List<GeneratedPackBuilder.Item> animatedItems = new ArrayList<>();
        int reusedCount = 0;
        int newCount = 0;
        try {
            int total = set.stickers.size();
            for (int index = 0; index < total; index++) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Telegram conversion was cancelled.");
                }
                TelegramApiClient.RemoteSticker sticker = set.stickers.get(index);
                ExistingSticker previous = existing.get(sticker.uniqueId);
                boolean animated = sticker.kind != TelegramApiClient.Kind.STATIC;
                if (previous != null && previous.animated != animated) {
                    previous = null;
                }
                String format = telegramFormat(sticker.kind);
                File sourceFile = new File(
                    work,
                    String.format(
                        Locale.ROOT,
                        "%03d-source.%s",
                        index + 1,
                        telegramExtension(format)
                    )
                );
                File rendered = new File(
                    work,
                    String.format(Locale.ROOT, "%03d.webp", index + 1)
                );

                if (previous != null && previous.rendered.isFile()) {
                    update(
                        listener,
                        ConversionProgress.sticker(index, total, 15),
                        Stage.REUSING_STICKER,
                        index + 1,
                        total,
                        100
                    );
                    copy(previous.rendered, rendered, 600 * 1024);
                    if (
                        previous.telegramSource != null
                        && previous.telegramSource.isFile()
                        && format.equals(previous.telegramFormat)
                    ) {
                        copy(previous.telegramSource, sourceFile, 8 * 1024 * 1024);
                    } else {
                        downloadSource(
                            client,
                            sticker,
                            sourceFile,
                            listener,
                            index,
                            total
                        );
                    }
                    reusedCount++;
                } else {
                    downloadSource(
                        client,
                        sticker,
                        sourceFile,
                        listener,
                        index,
                        total
                    );
                    renderSticker(
                        context,
                        sticker,
                        sourceFile,
                        rendered,
                        listener,
                        index,
                        total
                    );
                    newCount++;
                }

                GeneratedPackBuilder.Item item =
                    new GeneratedPackBuilder.Item(
                        rendered,
                        Collections.singletonList(cleanEmoji(sticker.emoji)),
                        set.title + " sticker " + (index + 1),
                        sticker.uniqueId,
                        sourceFile,
                        format
                    );
                if (sticker.kind == TelegramApiClient.Kind.STATIC) {
                    staticItems.add(item);
                } else {
                    animatedItems.add(item);
                }
            }
            appendLocalItems(
                context,
                sourcePacks,
                staticItems,
                animatedItems
            );
            update(listener, 93, Stage.BUILDING_PACKS, 0, 0, 0);
            List<Pack> packs = GeneratedPackBuilder.buildSplit(
                context,
                staticItems,
                animatedItems,
                set.title,
                cleanPublisher(publisher),
                set.name
            );
            update(listener, 100, Stage.COMPLETE, 0, 0, 100);
            return new Result(
                set,
                packs,
                staticCount,
                animatedCount,
                newCount,
                reusedCount
            );
        } finally {
            try {
                PackStore.deleteTree(work);
            } catch (IOException ignored) {
                // Android clears cache even if one temporary file is busy.
            }
        }
    }

    private static void downloadSource(
        TelegramApiClient client,
        TelegramApiClient.RemoteSticker sticker,
        File sourceFile,
        ProgressListener listener,
        int index,
        int total
    ) throws IOException, JSONException {
        update(
            listener,
            ConversionProgress.sticker(index, total, 0),
            Stage.DOWNLOADING_STICKER,
            index + 1,
            total,
            0
        );
        byte[] source = client.download(
            sticker,
            downloadPercent -> {
                int itemPercent = downloadPercent * 15 / 100;
                update(
                    listener,
                    ConversionProgress.sticker(index, total, itemPercent),
                    Stage.DOWNLOADING_STICKER,
                    index + 1,
                    total,
                    downloadPercent
                );
            }
        );
        write(sourceFile, source);
    }

    private static void renderSticker(
        Context context,
        TelegramApiClient.RemoteSticker sticker,
        File sourceFile,
        File rendered,
        ProgressListener listener,
        int index,
        int total
    ) throws IOException {
        update(
            listener,
            ConversionProgress.sticker(index, total, 15),
            Stage.CONVERTING_STICKER,
            index + 1,
            total,
            0
        );
        byte[] output;
        if (sticker.kind == TelegramApiClient.Kind.STATIC) {
            output = StaticStickerRenderer.render(read(sourceFile, 8 * 1024 * 1024));
        } else if (sticker.kind == TelegramApiClient.Kind.TGS) {
            output = TgsStickerRenderer.render(
                read(sourceFile, 8 * 1024 * 1024),
                (progress, message) -> update(
                    listener,
                    ConversionProgress.sticker(
                        index,
                        total,
                        15 + progress * 85 / 100
                    ),
                    Stage.CONVERTING_STICKER,
                    index + 1,
                    total,
                    progress
                )
            );
        } else {
            Uri uri = Uri.fromFile(sourceFile);
            VideoStickerRenderer.Probe probe =
                VideoStickerRenderer.probe(context, uri);
            probe.preview.recycle();
            VideoStickerSettings settings = new VideoStickerSettings(
                0,
                Math.min(3_000, probe.durationMs),
                1f,
                0f,
                0f,
                VideoStickerSettings.Background.TRANSPARENT
            );
            output = VideoStickerRenderer.render(
                context,
                uri,
                settings,
                (progress, message) -> update(
                    listener,
                    ConversionProgress.sticker(
                        index,
                        total,
                        15 + progress * 85 / 100
                    ),
                    Stage.CONVERTING_STICKER,
                    index + 1,
                    total,
                    progress
                )
            ).data;
        }
        write(rendered, output);
        update(
            listener,
            ConversionProgress.sticker(index, total, 100),
            Stage.CONVERTING_STICKER,
            index + 1,
            total,
            100
        );
    }

    private static Map<String, ExistingSticker> existingStickers(
        Context context,
        List<Pack> packs
    ) {
        Map<String, ExistingSticker> result = new HashMap<>();
        for (Pack pack : packs) {
            File directory = PackStore.packDirectory(context, pack.identifier);
            for (Sticker sticker : pack.stickers) {
                if (sticker.telegramSourceId.isEmpty()) {
                    continue;
                }
                File rendered = safeChild(directory, sticker.fileName);
                File source = sticker.telegramSourceFile.isEmpty()
                    ? null
                    : safeChild(directory, sticker.telegramSourceFile);
                if (rendered != null && rendered.isFile()) {
                    result.put(
                        sticker.telegramSourceId,
                        new ExistingSticker(
                            rendered,
                            source,
                            sticker.telegramSourceFormat,
                            pack.animated
                        )
                    );
                }
            }
        }
        return result;
    }

    private static void appendLocalItems(
        Context context,
        List<Pack> packs,
        List<GeneratedPackBuilder.Item> staticItems,
        List<GeneratedPackBuilder.Item> animatedItems
    ) {
        for (Pack pack : packs) {
            File directory = PackStore.packDirectory(
                context,
                pack.identifier
            );
            for (Sticker sticker : pack.stickers) {
                if (!sticker.telegramSourceId.isEmpty()) {
                    continue;
                }
                File rendered = safeChild(directory, sticker.fileName);
                if (rendered == null || !rendered.isFile()) {
                    continue;
                }
                File telegramSource = sticker.telegramSourceFile.isEmpty()
                    ? null
                    : safeChild(directory, sticker.telegramSourceFile);
                GeneratedPackBuilder.Item item =
                    new GeneratedPackBuilder.Item(
                        rendered,
                        sticker.emojis,
                        sticker.accessibilityText,
                        "",
                        telegramSource,
                        sticker.telegramSourceFormat
                    );
                if (pack.animated) {
                    animatedItems.add(item);
                } else {
                    staticItems.add(item);
                }
            }
        }
    }

    private static List<Pack> migrateLegacyPacks(
        Context context,
        TelegramApiClient.StickerSet set
    ) throws IOException, JSONException {
        List<Pack> all = PackStore.list(context);
        int staticTotal = count(set, false);
        int animatedTotal = count(set, true);
        boolean mixed = staticTotal > 0 && animatedTotal > 0;
        List<Pack> migrated = new ArrayList<>();
        migrateLegacyType(
            context,
            all,
            set,
            false,
            mixed ? set.title + " - Static" : set.title,
            migrated
        );
        migrateLegacyType(
            context,
            all,
            set,
            true,
            mixed ? set.title + " - Animated" : set.title,
            migrated
        );
        return migrated;
    }

    private static void migrateLegacyType(
        Context context,
        List<Pack> all,
        TelegramApiClient.StickerSet set,
        boolean animated,
        String baseName,
        List<Pack> migrated
    ) throws IOException, JSONException {
        List<Pack> candidates = new ArrayList<>();
        for (Pack pack : all) {
            if (
                pack.telegramSourceName.isEmpty()
                && pack.animated == animated
                && legacyPart(pack.name, baseName) >= 0
            ) {
                candidates.add(pack);
            }
        }
        candidates.sort((left, right) -> Integer.compare(
            legacyPart(left.name, baseName),
            legacyPart(right.name, baseName)
        ));
        int legacyCount = 0;
        for (Pack pack : candidates) {
            legacyCount += pack.stickers.size();
        }
        int remoteCount = count(set, animated);
        if (legacyCount == 0 || legacyCount > remoteCount) {
            return;
        }
        List<TelegramApiClient.RemoteSticker> remote = new ArrayList<>();
        for (TelegramApiClient.RemoteSticker sticker : set.stickers) {
            if ((sticker.kind != TelegramApiClient.Kind.STATIC) == animated) {
                remote.add(sticker);
            }
        }
        int offset = 0;
        for (int partIndex = 0; partIndex < candidates.size(); partIndex++) {
            Pack old = candidates.get(partIndex);
            List<Sticker> stickers = new ArrayList<>();
            for (Sticker sticker : old.stickers) {
                TelegramApiClient.RemoteSticker source = remote.get(offset++);
                stickers.add(
                    new Sticker(
                        sticker.fileName,
                        sticker.emojis,
                        sticker.accessibilityText,
                        source.uniqueId,
                        "",
                        telegramFormat(source.kind)
                    )
                );
            }
            Pack replacement = new Pack(
                old.identifier,
                old.name,
                old.publisher,
                old.trayImageFile,
                old.imageDataVersion,
                old.animated,
                stickers,
                set.name,
                partIndex
            );
            PackStore.writePack(
                PackStore.packDirectory(context, old.identifier),
                replacement
            );
            migrated.add(replacement);
        }
        context.getContentResolver().notifyChange(
            StickerContentProvider.AUTHORITY_URI,
            null
        );
    }

    private static int legacyPart(String name, String baseName) {
        if (name.equals(baseName)) {
            return 0;
        }
        String prefix = baseName + " - Part ";
        if (!name.startsWith(prefix)) {
            return -1;
        }
        try {
            int value = Integer.parseInt(name.substring(prefix.length()));
            return value > 0 ? value - 1 : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static int count(
        TelegramApiClient.StickerSet set,
        boolean animated
    ) {
        int result = 0;
        for (TelegramApiClient.RemoteSticker sticker : set.stickers) {
            if ((sticker.kind != TelegramApiClient.Kind.STATIC) == animated) {
                result++;
            }
        }
        return result;
    }

    private static File safeChild(File directory, String name) {
        if (name == null || !name.matches("[A-Za-z0-9_.-]{1,128}")) {
            return null;
        }
        return new File(directory, name);
    }

    private static String telegramFormat(TelegramApiClient.Kind kind) {
        if (kind == TelegramApiClient.Kind.TGS) {
            return "animated";
        }
        if (kind == TelegramApiClient.Kind.WEBM) {
            return "video";
        }
        return "static";
    }

    private static String telegramExtension(String format) {
        if ("animated".equals(format)) {
            return "tgs";
        }
        if ("video".equals(format)) {
            return "webm";
        }
        return "webp";
    }

    private static String cleanPublisher(String value) {
        String result = value == null ? "" : value.trim();
        return result.isEmpty() ? "ゴメン先生" : result;
    }

    private static String cleanEmoji(String value) {
        String result = value == null ? "" : value.trim();
        return result.isEmpty() ? "\uD83D\uDE00" : result;
    }

    private static byte[] read(File file, int maximum) throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {
            return PackStore.readFully(input, maximum);
        }
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
                    throw new IOException("Stored sticker exceeds the safe limit.");
                }
                output.write(buffer, 0, count);
            }
        }
    }

    private static void write(File file, byte[] data) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(data);
        }
    }

    private static void update(
        ProgressListener listener,
        int percent,
        Stage stage,
        int stickerNumber,
        int stickerCount,
        int stickerPercent
    ) {
        if (listener != null) {
            listener.onProgress(
                new Progress(
                    Math.max(0, Math.min(100, percent)),
                    stage,
                    stickerNumber,
                    stickerCount,
                    Math.max(0, Math.min(100, stickerPercent))
                )
            );
        }
    }
}
