package com.tool48.tgwabridge;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;

final class TelegramBotPackUploader {
    interface ProgressListener {
        void onProgress(int percent);
    }

    static final class Result {
        final long ownerId;
        final String packName;
        final String link;

        Result(long ownerId, String packName) {
            this.ownerId = ownerId;
            this.packName = packName;
            this.link = "https://t.me/addstickers/" + packName;
        }
    }

    private static final int CONNECT_TIMEOUT_MS = 20_000;
    private static final int READ_TIMEOUT_MS = 120_000;
    private final String token;

    private TelegramBotPackUploader(String token) throws IOException {
        new TelegramApiClient(token);
        this.token = token.trim();
    }

    static Result upload(
        String token,
        String title,
        List<File> files,
        List<String> formats,
        List<String> emojis,
        ProgressListener listener
    ) throws IOException, JSONException {
        TelegramBotPackUploader uploader =
            new TelegramBotPackUploader(token);
        if (
            files == null
            || files.isEmpty()
            || files.size() > 50
            || formats == null
            || formats.size() != files.size()
            || emojis == null
            || emojis.size() != files.size()
        ) {
            throw new IOException(
                "Telegram Bot API export needs 1 to 50 matching stickers."
            );
        }
        String format = formats.get(0);
        if (!validFormat(format)) {
            throw new IOException("Telegram sticker format is invalid.");
        }
        for (String item : formats) {
            if (!format.equals(item)) {
                throw new IOException(
                    "Telegram does not allow TGS and WEBM in the same pack."
                );
            }
        }
        if (listener != null) {
            listener.onProgress(2);
        }
        JSONObject me = uploader.get("getMe");
        String username = me.optString("username", "").trim();
        if (!username.matches("[A-Za-z0-9_]{5,32}")) {
            throw new IOException("Telegram bot username is unavailable.");
        }
        long ownerId = uploader.resolveOwnerId();
        if (listener != null) {
            listener.onProgress(8);
        }
        String packName = packName(title, username);
        uploader.create(
            ownerId,
            cleanTitle(title),
            packName,
            files,
            format,
            emojis,
            listener
        );
        if (listener != null) {
            listener.onProgress(100);
        }
        return new Result(ownerId, packName);
    }

    private long resolveOwnerId() throws IOException, JSONException {
        JSONArray updates = getArray("getUpdates?limit=100&timeout=0");
        Set<Long> owners = new LinkedHashSet<>();
        for (int index = updates.length() - 1; index >= 0; index--) {
            JSONObject update = updates.optJSONObject(index);
            JSONObject message = update == null
                ? null
                : update.optJSONObject("message");
            JSONObject from = message == null
                ? null
                : message.optJSONObject("from");
            JSONObject chat = message == null
                ? null
                : message.optJSONObject("chat");
            if (
                from != null
                && chat != null
                && !from.optBoolean("is_bot", false)
                && "private".equals(chat.optString("type", ""))
            ) {
                long id = from.optLong("id", 0L);
                if (id > 0) {
                    owners.add(id);
                }
            }
        }
        if (owners.isEmpty()) {
            throw new IOException(
                "Open your Telegram bot, send /start, then retry. "
                    + "The bot needs one private message to identify you as "
                    + "the sticker-pack owner."
            );
        }
        return owners.iterator().next();
    }

    private void create(
        long ownerId,
        String title,
        String packName,
        List<File> files,
        String format,
        List<String> emojis,
        ProgressListener listener
    ) throws IOException, JSONException {
        JSONArray stickers = new JSONArray();
        for (int index = 0; index < files.size(); index++) {
            JSONObject sticker = new JSONObject();
            sticker.put("sticker", "attach://sticker" + index);
            sticker.put("format", format);
            JSONArray emojiList = new JSONArray();
            emojiList.put(cleanEmoji(emojis.get(index)));
            sticker.put("emoji_list", emojiList);
            stickers.put(sticker);
        }
        String boundary = "TGWA" + UUID.randomUUID().toString().replace("-", "");
        URL url = new URL(
            "https://api.telegram.org/bot" + token + "/createNewStickerSet"
        );
        HttpsURLConnection connection =
            (HttpsURLConnection) url.openConnection();
        configure(connection);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setChunkedStreamingMode(8192);
        connection.setRequestProperty(
            "Content-Type",
            "multipart/form-data; boundary=" + boundary
        );
        try (
            OutputStream output = new BufferedOutputStream(
                connection.getOutputStream()
            )
        ) {
            field(output, boundary, "user_id", Long.toString(ownerId));
            field(output, boundary, "name", packName);
            field(output, boundary, "title", title);
            field(output, boundary, "sticker_type", "regular");
            field(output, boundary, "stickers", stickers.toString());
            for (int index = 0; index < files.size(); index++) {
                file(
                    output,
                    boundary,
                    "sticker" + index,
                    files.get(index),
                    mime(format)
                );
                if (listener != null) {
                    listener.onProgress(
                        8 + (index + 1) * 85 / files.size()
                    );
                }
            }
            output.write(("--" + boundary + "--\r\n").getBytes(
                StandardCharsets.UTF_8
            ));
        }
        readResponse(connection);
    }

    private JSONObject get(String method) throws IOException, JSONException {
        Object result = request(method);
        if (!(result instanceof JSONObject)) {
            throw new IOException("Telegram returned an unexpected object.");
        }
        return (JSONObject) result;
    }

    private JSONArray getArray(String method)
        throws IOException, JSONException {
        Object result = request(method);
        if (!(result instanceof JSONArray)) {
            throw new IOException("Telegram returned an unexpected list.");
        }
        return (JSONArray) result;
    }

    private Object request(String method) throws IOException, JSONException {
        URL url = new URL("https://api.telegram.org/bot" + token + "/" + method);
        HttpsURLConnection connection =
            (HttpsURLConnection) url.openConnection();
        configure(connection);
        connection.setRequestMethod("GET");
        return readResponse(connection);
    }

    private static Object readResponse(HttpsURLConnection connection)
        throws IOException, JSONException {
        try {
            int status = connection.getResponseCode();
            InputStream raw = status >= 400
                ? connection.getErrorStream()
                : connection.getInputStream();
            byte[] bytes;
            if (raw == null) {
                bytes = new byte[0];
            } else {
                try (InputStream input = raw) {
                    bytes = PackStore.readFully(input, 2 * 1024 * 1024);
                }
            }
            JSONObject response = new JSONObject(
                new String(bytes, StandardCharsets.UTF_8)
            );
            if (!response.optBoolean("ok", false)) {
                throw new IOException(
                    response.optString(
                        "description",
                        "Telegram Bot API export failed."
                    )
                );
            }
            return response.get("result");
        } finally {
            connection.disconnect();
        }
    }

    private static void field(
        OutputStream output,
        String boundary,
        String name,
        String value
    ) throws IOException {
        output.write((
            "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n"
        ).getBytes(StandardCharsets.UTF_8));
    }

    private static void file(
        OutputStream output,
        String boundary,
        String fieldName,
        File source,
        String contentType
    ) throws IOException {
        if (!source.isFile() || source.length() > 8 * 1024 * 1024) {
            throw new IOException("Telegram sticker upload file is invalid.");
        }
        output.write((
            "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + fieldName
                + "\"; filename=\"" + source.getName() + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n"
        ).getBytes(StandardCharsets.UTF_8));
        try (FileInputStream input = new FileInputStream(source)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
        }
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static void configure(HttpsURLConnection connection) {
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setUseCaches(false);
        connection.setRequestProperty(
            "User-Agent",
            "TGWA-Maker-Android/" + BuildConfig.VERSION_NAME
        );
    }

    private static boolean validFormat(String value) {
        return "static".equals(value)
            || "animated".equals(value)
            || "video".equals(value);
    }

    private static String mime(String format) {
        if ("video".equals(format)) {
            return "video/webm";
        }
        if ("animated".equals(format)) {
            return "application/x-tgsticker";
        }
        return "image/webp";
    }

    static String packName(String title, String username) {
        String suffix = "_by_" + username;
        int maximumBase = Math.max(1, 64 - suffix.length());
        String base = title == null ? "" : title
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_]+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_+|_+$", "");
        if (base.isEmpty() || !Character.isLetter(base.charAt(0))) {
            base = "tgwa_" + base;
        }
        String stamp = Long.toString(System.currentTimeMillis(), 36);
        int titleLength = Math.max(1, maximumBase - stamp.length() - 1);
        base = base.substring(0, Math.min(titleLength, base.length()));
        return base + "_" + stamp + suffix;
    }

    private static String cleanTitle(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty()) {
            clean = "TGWA Maker Pack";
        }
        return clean.substring(0, Math.min(64, clean.length()));
    }

    private static String cleanEmoji(String value) {
        String clean = value == null ? "" : value.trim();
        return clean.isEmpty() ? "\uD83D\uDE00" : clean;
    }
}
