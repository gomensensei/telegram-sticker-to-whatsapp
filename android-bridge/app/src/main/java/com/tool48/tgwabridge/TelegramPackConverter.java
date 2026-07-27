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
    interface ProgressListener {
        void onProgress(int percent, String message);
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
        update(listener, 0, "Reading Telegram sticker pack...");
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
                int base = index * 90 / total;
                update(
                    listener,
                    base,
                    "Downloading sticker "
                        + (index + 1)
                        + " of "
                        + total
                        + "..."
                );
                byte[] source = client.download(sticker);
                byte[] output;
                if (sticker.kind == TelegramApiClient.Kind.STATIC) {
                    output = StaticStickerRenderer.render(source);
                } else if (sticker.kind == TelegramApiClient.Kind.TGS) {
                    final int stickerIndex = index;
                    output = TgsStickerRenderer.render(
                        source,
                        (progress, message) -> update(
                            listener,
                            (
                                stickerIndex * 90
                                    + progress * 90 / 100
                            ) / total,
                            "Sticker "
                                + (stickerIndex + 1)
                                + "/"
                                + total
                                + ": "
                                + message
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
                    final int stickerIndex = index;
                    output = VideoStickerRenderer.render(
                        context,
                        uri,
                        settings,
                        (progress, message) -> update(
                            listener,
                            (
                                stickerIndex * 90
                                    + progress * 90 / 100
                            ) / total,
                            "Sticker "
                                + (stickerIndex + 1)
                                + "/"
                                + total
                                + ": "
                                + message
                        )
                    ).data;
                }
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
                92,
                "Separating static and animated packs..."
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
                "Telegram pack converted and validated."
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
        String message
    ) {
        if (listener != null) {
            listener.onProgress(
                Math.max(0, Math.min(100, percent)),
                message
            );
        }
    }
}
