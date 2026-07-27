package com.tool48.tgwabridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public final class VideoStickerSettingsTest {
    @Test
    public void acceptsWhatsappClipBounds() {
        VideoStickerSettings value = new VideoStickerSettings(
            100,
            3_000,
            1.25f,
            -0.5f,
            0.5f,
            VideoStickerSettings.Background.TRANSPARENT
        );
        assertEquals(3_000, value.durationMs);
        assertEquals(1.25f, value.scale, 0.001f);
    }

    @Test
    public void rejectsOverlongClip() {
        try {
            new VideoStickerSettings(
                0,
                3_001,
                1f,
                0f,
                0f,
                VideoStickerSettings.Background.TRANSPARENT
            );
            fail("Expected overlong duration to fail.");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
