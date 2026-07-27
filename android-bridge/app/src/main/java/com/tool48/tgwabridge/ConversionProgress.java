package com.tool48.tgwabridge;

final class ConversionProgress {
    private static final int STICKER_START = 5;
    private static final int STICKER_END = 92;

    private ConversionProgress() {
    }

    static int sticker(
        int stickerIndex,
        int stickerCount,
        int stickerPercent
    ) {
        if (stickerCount < 1) {
            throw new IllegalArgumentException(
                "Sticker count must be positive."
            );
        }
        int safeIndex = Math.max(
            0,
            Math.min(stickerCount - 1, stickerIndex)
        );
        int safePercent = Math.max(
            0,
            Math.min(100, stickerPercent)
        );
        double completed = (
            safeIndex + safePercent / 100.0
        ) / stickerCount;
        return Math.min(
            STICKER_END,
            STICKER_START
                + (int) Math.round(
                    (STICKER_END - STICKER_START) * completed
                )
        );
    }
}
