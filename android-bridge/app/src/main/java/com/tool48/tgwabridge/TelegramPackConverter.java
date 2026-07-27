package com.tool48.tgwabridge;

import android.content.Context;
import android.net.Uri;

import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class TelegramPackConverter {
    enum Stage {
        READING_PACK,
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

        Result(
            TelegramApiClient.StickerSet source,
            List<Pack> packs,
            int staticCount,
            int animatedCount
        ) {
            this.source = source;
            this.packs = packs;
            this.staticCount = staticCount;
            this.animatedCount = animatedCount;
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
        update(
            listener,
            1,
            Stage.READING_PACK,
            0,
            0,
            0
        );
        TelegramApiClient.StickerSet set =
            client.getStickerSet(shortName);
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
        validateMinimum(staticCount, "static");
        validateMinimum(animatedCount, "animated");

        File work = new File(
            context.getCacheDir(),
            "telegram-" + UUID.randomUUID().toString()
        );
        if (!work.mkdirs()) {
            throw new IOException(
                "Cannot create Telegram conversion workspace."
            );
        }
        List<GeneratedPackBuilder.Item> staticItems =
            new ArrayList<>();
        List<GeneratedPackBuilder.Item> animatedItems =
            new ArrayList<>();
        try {
            int total = set.stickers.size();
            for (int index = 0; index < total; index++) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Telegram conversion was cancelled.");
                }
                TelegramApiClient.RemoteSticker sticker =
                    set.stickers.get(index);
                update(
                    listener,
                    ConversionProgress.sticker(index, total, 0),
                    Stage.DOWNLOADING_STICKER,
                    index + 1,
                    total,
                    0
                );
                final int stickerIndex = index;
                byte[] source = client.download(
                    sticker,
                    downloadPercent -> {
                        int itemPercent = downloadPercent * 15 / 100;
                        update(
                            listener,
                            ConversionProgress.sticker(
                                stickerIndex,
                                total,
                                itemPercent
                            ),
                            Stage.DOWNLOADING_STICKER,
                            stickerIndex + 1,
                            total,
                            downloadPercent
                        );
                    }
                );
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
                    output = StaticStickerRenderer.render(source);
                } else if (sticker.kind == TelegramApiClient.Kind.TGS) {
                    output = TgsStickerRenderer.render(
                        source,
                        (progress, message) -> update(
                            listener,
                            ConversionProgress.sticker(
                                stickerIndex,
                                total,
                                15 + progress * 85 / 100
                            ),
                            Stage.CONVERTING_STICKER,
                            stickerIndex + 1,
                            total,
                            progress
                        )
                    );
                } else {
                    File webm = new File(
                        work,
                        String.format(
                            Locale.ROOT,
                            "%03d-source.webm",
                            index + 1
                        )
                    );
                    write(webm, source);
                    Uri uri = Uri.fromFile(webm);
                    VideoStickerRenderer.Probe probe =
                        VideoStickerRenderer.probe(context, uri);
                    probe.preview.recycle();
                    long durationMs = Math.min(
                        3_000,
                        probe.durationMs
                    );
                    VideoStickerSettings settings =
                        new VideoStickerSettings(
                            0,
                            durationMs,
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
                                stickerIndex,
                                total,
                                15 + progress * 85 / 100
                            ),
                            Stage.CONVERTING_STICKER,
                            stickerIndex + 1,
                            total,
                            progress
                        )
                    ).data;
                }
                update(
                    listener,
                    ConversionProgress.sticker(index, total, 100),
                    Stage.CONVERTING_STICKER,
                    index + 1,
                    total,
                    100
                );
                File rendered = new File(
                    work,
                    String.format(
                        Locale.ROOT,
                        "%03d.webp",
                        index + 1
                    )
                );
                write(rendered, output);
                GeneratedPackBuilder.Item item =
                    new GeneratedPackBuilder.Item(
                        rendered,
                        Collections.singletonList(
                            cleanEmoji(sticker.emoji)
                        ),
                        set.title + " sticker " + (index + 1)
                    );
                if (sticker.kind == TelegramApiClient.Kind.STATIC) {
                    staticItems.add(item);
                } else {
                    animatedItems.add(item);
                }
            }
            update(
                listener,
                93,
                Stage.BUILDING_PACKS,
                0,
                0,
                0
            );
            List<Pack> packs = GeneratedPackBuilder.buildSplit(
                context,
                staticItems,
                animatedItems,
                set.title,
                cleanPublisher(publisher)
            );
            update(
                listener,
                100,
                Stage.COMPLETE,
                0,
                0,
                100
            );
            return new Result(
                set,
                packs,
                staticCount,
                animatedCount
            );
        } finally {
            try {
                PackStore.deleteTree(work);
            } catch (IOException ignored) {
                // Android will clear cache even if one temporary file is busy.
            }
        }
    }

    private static void validateMinimum(int count, String type)
        throws IOException {
        if (count > 0 && count < 3) {
            throw new IOException(
                "After separating static and animated stickers, this pack "
                    + "has only "
                    + count
                    + " "
                    + type
                    + " sticker"
                    + (count == 1 ? "" : "s")
                    + ". WhatsApp requires at least 3; stickers will not "
                    + "be duplicated."
            );
        }
    }

    private static String cleanPublisher(String value) {
        String result = value == null ? "" : value.trim();
        return result.isEmpty() ? "TGWA Maker" : result;
    }

    private static String cleanEmoji(String value) {
        String result = value == null ? "" : value.trim();
        return result.isEmpty() ? "\uD83D\uDE00" : result;
    }

    private static void write(File file, byte[] data)
        throws IOException {
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
