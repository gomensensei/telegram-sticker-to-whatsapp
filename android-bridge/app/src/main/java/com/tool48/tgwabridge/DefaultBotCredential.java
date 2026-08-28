package com.tool48.tgwabridge;

import android.util.Base64;

import java.nio.charset.StandardCharsets;

final class DefaultBotCredential {
    private static final byte[] KEY =
        "tgwa-maker-shared-bot-v1".getBytes(StandardCharsets.UTF_8);

    private DefaultBotCredential() {
    }

    static String value() {
        String encoded = BuildConfig.DEFAULT_BOT_TOKEN_DATA;
        if (encoded == null || encoded.isEmpty()) {
            return "";
        }
        try {
            byte[] masked = Base64.decode(encoded, Base64.NO_WRAP);
            byte[] plain = new byte[masked.length];
            for (int index = 0; index < masked.length; index++) {
                plain[index] = (byte) (
                    masked[index] ^ KEY[index % KEY.length]
                );
            }
            return new String(plain, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException error) {
            return "";
        }
    }

    static boolean available() {
        return !value().isEmpty();
    }
}
