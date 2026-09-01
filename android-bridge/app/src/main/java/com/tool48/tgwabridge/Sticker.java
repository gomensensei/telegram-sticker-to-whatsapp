package com.tool48.tgwabridge;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class Sticker {
    final String fileName;
    final List<String> emojis;
    final String accessibilityText;
    final String telegramSourceId;
    final String telegramSourceFile;
    final String telegramSourceFormat;

    Sticker(String fileName, List<String> emojis, String accessibilityText) {
        this(fileName, emojis, accessibilityText, "", "", "");
    }

    Sticker(
        String fileName,
        List<String> emojis,
        String accessibilityText,
        String telegramSourceId,
        String telegramSourceFile,
        String telegramSourceFormat
    ) {
        this.fileName = fileName;
        this.emojis = emojis;
        this.accessibilityText = accessibilityText;
        this.telegramSourceId = clean(telegramSourceId);
        this.telegramSourceFile = clean(telegramSourceFile);
        this.telegramSourceFormat = clean(telegramSourceFormat);
    }

    JSONObject toJson() throws JSONException {
        JSONObject result = new JSONObject();
        result.put("file_name", fileName);
        result.put("emojis", new JSONArray(emojis));
        result.put("accessibility_text", accessibilityText);
        if (!telegramSourceId.isEmpty()) {
            result.put("telegram_source_id", telegramSourceId);
        }
        if (!telegramSourceFile.isEmpty()) {
            result.put("telegram_source_file", telegramSourceFile);
            result.put("telegram_source_format", telegramSourceFormat);
        }
        return result;
    }

    static Sticker fromJson(JSONObject source) throws JSONException {
        List<String> emojis = new ArrayList<>();
        JSONArray values = source.optJSONArray("emojis");
        if (values != null) {
            for (int index = 0; index < values.length(); index++) {
                String value = values.optString(index, "").trim();
                if (!value.isEmpty()) {
                    emojis.add(value);
                }
            }
        }
        if (emojis.isEmpty()) {
            emojis.add("\uD83D\uDE00");
        }
        return new Sticker(
            source.getString("file_name"),
            emojis,
            source.optString("accessibility_text", ""),
            source.optString("telegram_source_id", ""),
            source.optString("telegram_source_file", ""),
            source.optString("telegram_source_format", "")
        );
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
