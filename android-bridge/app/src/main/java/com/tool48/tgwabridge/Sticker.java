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

    Sticker(String fileName, List<String> emojis, String accessibilityText) {
        this.fileName = fileName;
        this.emojis = emojis;
        this.accessibilityText = accessibilityText;
    }

    JSONObject toJson() throws JSONException {
        JSONObject result = new JSONObject();
        result.put("file_name", fileName);
        result.put("emojis", new JSONArray(emojis));
        result.put("accessibility_text", accessibilityText);
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
            source.optString("accessibility_text", "")
        );
    }
}
