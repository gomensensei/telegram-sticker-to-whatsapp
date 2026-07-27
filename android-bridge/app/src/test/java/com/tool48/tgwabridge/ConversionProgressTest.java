package com.tool48.tgwabridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ConversionProgressTest {
    @Test
    public void firstStickerStartsAtVisibleProgress() {
        assertEquals(5, ConversionProgress.sticker(0, 40, 0));
    }

    @Test
    public void longPacksAdvanceWithinFirstSticker() {
        assertTrue(
            ConversionProgress.sticker(0, 40, 50)
                >= ConversionProgress.sticker(0, 40, 0)
        );
        assertTrue(
            ConversionProgress.sticker(1, 40, 0)
                > ConversionProgress.sticker(0, 40, 0)
        );
    }

    @Test
    public void lastStickerEndsAtPackingBoundary() {
        assertEquals(92, ConversionProgress.sticker(39, 40, 100));
    }
}
