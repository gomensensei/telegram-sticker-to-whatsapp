package com.tool48.tgwabridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;

public final class TelegramPackLinkTest {
    @Test
    public void acceptsFullLinkAndShortName() throws Exception {
        assertEquals(
            "Kosaki_by_MyBot",
            TelegramPackLink.shortName(
                "https://t.me/addstickers/Kosaki_by_MyBot"
            )
        );
        assertEquals(
            "Kosaki_by_MyBot",
            TelegramPackLink.shortName("Kosaki_by_MyBot")
        );
        assertEquals(
            "Kosaki_by_MyBot",
            TelegramPackLink.shortName(
                "https://t.me/addstickers/Kosaki_by_MyBot/"
            )
        );
        assertEquals(
            "Kosaki_by_MyBot",
            TelegramPackLink.shortName(
                "tg://addstickers?set=Kosaki_by_MyBot"
            )
        );
        assertEquals(
            "Kosaki_by_MyBot",
            TelegramPackLink.shortName(
                "Try this pack:\n"
                    + "https://t.me/addstickers/Kosaki_by_MyBot"
            )
        );
    }

    @Test
    public void stripsQueryAndFragment() throws Exception {
        assertEquals(
            "Kosaki",
            TelegramPackLink.shortName(
                "https://telegram.me/addstickers/Kosaki?start=1#pack"
            )
        );
    }

    @Test
    public void rejectsUnsafeShortName() throws Exception {
        try {
            TelegramPackLink.shortName("../../bad-pack");
            fail("Expected an unsafe name to be rejected.");
        } catch (IOException error) {
            assertTrue(error.getMessage().contains("valid Telegram"));
        }
    }
}
