package com.tool48.tgwabridge;

final class VideoStickerSettings {
    enum Background {
        TRANSPARENT,
        BLACK,
        WHITE
    }

    final long startMs;
    final long durationMs;
    final float playbackSpeed;
    final float scale;
    final float offsetX;
    final float offsetY;
    final Background background;

    VideoStickerSettings(
        long startMs,
        long durationMs,
        float playbackSpeed,
        float scale,
        float offsetX,
        float offsetY,
        Background background
    ) {
        if (startMs < 0) {
            throw new IllegalArgumentException("Start time cannot be negative.");
        }
        if (playbackSpeed < 0.125f || playbackSpeed > 8.0f) {
            throw new IllegalArgumentException(
                "Playback speed must be between 1/8x and 8x."
            );
        }
        long outputDurationMs = Math.round(durationMs / playbackSpeed);
        if (outputDurationMs < 200 || outputDurationMs > 3_000) {
            throw new IllegalArgumentException(
                "The adjusted clip must be between 0.2 and 3 seconds."
            );
        }
        if (scale < 0.25f || scale > 4.0f) {
            throw new IllegalArgumentException(
                "Scale must be between 25% and 400%."
            );
        }
        if (
            offsetX < -1.5f
            || offsetX > 1.5f
            || offsetY < -1.5f
            || offsetY > 1.5f
        ) {
            throw new IllegalArgumentException(
                "Sticker position is outside the editor canvas."
            );
        }
        if (background == null) {
            throw new IllegalArgumentException("Background is required.");
        }
        this.startMs = startMs;
        this.durationMs = durationMs;
        this.playbackSpeed = playbackSpeed;
        this.scale = scale;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.background = background;
    }

    VideoStickerSettings(
        long startMs,
        long durationMs,
        float scale,
        float offsetX,
        float offsetY,
        Background background
    ) {
        this(
            startMs,
            durationMs,
            1f,
            scale,
            offsetX,
            offsetY,
            background
        );
    }

    long outputDurationMs() {
        return Math.round(durationMs / playbackSpeed);
    }
}
