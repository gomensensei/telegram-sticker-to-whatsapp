package com.tool48.tgwabridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TelegramApiClientTest {
    @Test
    public void keepsStableUniqueIdForIncrementalSync() {
        TelegramApiClient.RemoteSticker sticker =
            new TelegramApiClient.RemoteSticker(
                "download-id",
                "stable-id",
                "😀",
                TelegramApiClient.Kind.WEBM
            );

        assertEquals("stable-id", sticker.uniqueId);
        assertEquals(TelegramApiClient.Kind.WEBM, sticker.kind);
    }

    @Test
    public void fallsBackToFileIdWhenUniqueIdIsMissing() {
        TelegramApiClient.RemoteSticker sticker =
            new TelegramApiClient.RemoteSticker(
                "download-id",
                " ",
                "😀",
                TelegramApiClient.Kind.STATIC
            );

        assertEquals("download-id", sticker.uniqueId);
    }

    @Test
    public void generatedBotPackNameMeetsTelegramContract() {
        String name = TelegramBotPackUploader.packName(
            "測試 very long Telegram video sticker pack title that keeps going",
            "ExampleMakerBot"
        );

        assertTrue(name.length() <= 64);
        assertTrue(name.matches("[A-Za-z][A-Za-z0-9_]*"));
        assertTrue(!name.contains("__"));
        assertTrue(name.endsWith("_by_ExampleMakerBot"));
    }
}
