package com.tool48.tgwabridge;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

final class TelegramApiClient {
    enum Kind {
        STATIC,
        TGS,
        WEBM
    }

    static final class RemoteSticker {
        final String fileId;
        final String emoji;
        final Kind kind;

        RemoteSticker(String fileId, String emoji, Kind kind) {
            this.fileId = fileId;
            this.emoji = emoji;
            this.kind = kind;
        }
    }

    static final class StickerSet {
        final String name;
        final String title;
        final List<RemoteSticker> stickers;

        StickerSet(
            String name,
            String title,
            List<RemoteSticker> stickers
        ) {
            this.name = name;
            this.title = title;
            this.stickers = stickers;
        }
    }

    private static final int CONNECT_TIMEOUT_MS = 20_000;
    private static final int READ_TIMEOUT_MS = 45_000;
    private static final int MAX_JSON_BYTES = 2 * 1024 * 1024;
    private static final int MAX_STICKER_BYTES = 8 * 1024 * 1024;
    private final String token;

    TelegramApiClient(String token) throws IOException {
        this.token = token == null ? "" : token.trim();
        if (!this.token.matches("[0-9]{5,20}:[A-Za-z0-9_-]{20,}")) {
            throw new IOException(
                "Telegram Bot Token format is invalid."
            );
        }
    }

    StickerSet getStickerSet(String shortName)
        throws IOException, JSONException {
        JSONObject result = api(
            "getStickerSet?name=" + encode(shortName)
        );
        JSONArray values = result.getJSONArray("stickers");
        List<RemoteSticker> stickers = new ArrayList<>();
        for (int index = 0; index < values.length(); index++) {
            JSONObject value = values.getJSONObject(index);
            Kind kind = value.optBoolean("is_animated", false)
                ? Kind.TGS
                : (
                    value.optBoolean("is_video", false)
                        ? Kind.WEBM
                        : Kind.STATIC
                );
            stickers.add(
                new RemoteSticker(
                    value.getString("file_id"),
                    value.optString("emoji", "\uD83D\uDE00"),
                    kind
                )
            );
        }
        if (stickers.isEmpty()) {
            throw new IOException("This Telegram sticker pack is empty.");
        }
        return new StickerSet(
            result.optString("name", shortName),
            result.optString("title", shortName),
            stickers
        );
    }

    byte[] download(RemoteSticker sticker)
        throws IOException, JSONException {
        JSONObject file = api(
            "getFile?file_id=" + encode(sticker.fileId)
        );
        String path = file.getString("file_path");
        if (
            path.contains("..")
            || !path.matches("[A-Za-z0-9_./-]{1,512}")
        ) {
            throw new IOException("Telegram returned an unsafe file path.");
        }
        URL url = new URL(
            "https://api.telegram.org/file/bot" + token + "/" + path
        );
        HttpsURLConnection connection =
            (HttpsURLConnection) url.openConnection();
        configure(connection);
        try {
            int responseCode = connection.getResponseCode();
            if (
                responseCode < HttpURLConnection.HTTP_OK
                || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE
            ) {
                throw new IOException(
                    "Telegram sticker download failed (HTTP "
                        + responseCode
                        + ")."
                );
            }
            return read(
                connection.getInputStream(),
                MAX_STICKER_BYTES
            );
        } finally {
            connection.disconnect();
        }
    }

    private JSONObject api(String method)
        throws IOException, JSONException {
        URL url = new URL(
            "https://api.telegram.org/bot" + token + "/" + method
        );
        HttpsURLConnection connection =
            (HttpsURLConnection) url.openConnection();
        configure(connection);
        try {
            int responseCode = connection.getResponseCode();
            InputStream stream = responseCode >= 400
                ? connection.getErrorStream()
                : connection.getInputStream();
            byte[] bytes = stream == null
                ? new byte[0]
                : read(stream, MAX_JSON_BYTES);
            JSONObject response = new JSONObject(
                new String(bytes, StandardCharsets.UTF_8)
            );
            if (!response.optBoolean("ok", false)) {
                String description = response.optString(
                    "description",
                    "Telegram API request failed."
                );
                throw new IOException(description);
            }
            return response.getJSONObject("result");
        } finally {
            connection.disconnect();
        }
    }

    private static void configure(HttpsURLConnection connection)
        throws IOException {
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setUseCaches(false);
        connection.setRequestMethod("GET");
        connection.setRequestProperty(
            "Accept",
            "application/json, application/octet-stream"
        );
        connection.setRequestProperty(
            "User-Agent",
            "TGWA-Maker-Android/2.0"
        );
    }

    private static byte[] read(InputStream raw, int maximum)
        throws IOException {
        try (
            InputStream input = raw;
            ByteArrayOutputStream output = new ByteArrayOutputStream()
        ) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > maximum) {
                    throw new IOException(
                        "A Telegram response exceeds the safe size limit."
                    );
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static String encode(String value) throws IOException {
        return URLEncoder.encode(
            value,
            StandardCharsets.UTF_8.name()
        );
    }
}
