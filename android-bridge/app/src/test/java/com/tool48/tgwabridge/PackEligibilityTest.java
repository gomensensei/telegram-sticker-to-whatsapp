package com.tool48.tgwabridge;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PackEligibilityTest {
    @Test
    public void telegramOnlyPackUnlocksWhatsappAtThree() {
        assertFalse(pack(1).whatsappEligible());
        assertFalse(pack(2).whatsappEligible());
        assertTrue(pack(3).whatsappEligible());
        assertTrue(pack(30).whatsappEligible());
    }

    private static Pack pack(int count) {
        List<Sticker> stickers = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            stickers.add(
                new Sticker(
                    String.format("%03d.webp", index + 1),
                    Collections.singletonList("✨"),
                    "test"
                )
            );
        }
        return new Pack(
            "test_pack",
            "Test",
            "ゴメン先生",
            "cover.png",
            "1",
            false,
            stickers
        );
    }
}
