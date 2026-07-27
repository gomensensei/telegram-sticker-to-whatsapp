package com.tool48.tgwabridge;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TelegramPackLink {
    private static final Pattern WEB_LINK = Pattern.compile(
        "(?i)(?:https?://)?(?:t\\.me|telegram\\.me)"
            + "/addstickers/([A-Za-z0-9_]{1,64})"
    );

    private TelegramPackLink() {
    }

    static String shortName(String input) throws IOException {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) {
            throw new IOException("Enter a Telegram sticker pack link.");
        }
        value = value.replace('\\', '/');
        Matcher webLink = WEB_LINK.matcher(value);
        if (webLink.find()) {
            return webLink.group(1);
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("tg://addstickers?")) {
            value = telegramSetParameter(value);
            lower = value.toLowerCase(Locale.ROOT);
        }
        int addIndex = lower.indexOf("/addstickers/");
        if (addIndex >= 0) {
            value = value.substring(addIndex + "/addstickers/".length());
        } else {
            if (value.contains("/")) {
                value = value.substring(value.lastIndexOf('/') + 1);
            }
        }
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        int fragment = value.indexOf('#');
        if (fragment >= 0) {
            value = value.substring(0, fragment);
        }
        value = value.trim().replaceAll("/+$", "");
        if (!value.matches("[A-Za-z0-9_]{1,64}")) {
            throw new IOException(
                "This is not a valid Telegram sticker pack link or short name."
            );
        }
        return value;
    }

    private static String telegramSetParameter(String uri)
        throws IOException {
        int query = uri.indexOf('?');
        String[] values = query < 0
            ? new String[0]
            : uri.substring(query + 1).split("&");
        for (String value : values) {
            int equals = value.indexOf('=');
            String key = equals < 0
                ? value
                : value.substring(0, equals);
            if ("set".equalsIgnoreCase(key)) {
                String encoded = equals < 0
                    ? ""
                    : value.substring(equals + 1);
                try {
                    return URLDecoder.decode(
                        encoded,
                        StandardCharsets.UTF_8.name()
                    );
                } catch (IllegalArgumentException error) {
                    throw new IOException(
                        "Telegram sticker link encoding is invalid.",
                        error
                    );
                }
            }
        }
        throw new IOException(
            "Telegram sticker link does not contain a pack name."
        );
    }
}
