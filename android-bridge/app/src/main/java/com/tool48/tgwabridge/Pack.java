package com.tool48.tgwabridge;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class Pack {
    final String identifier;
    final String name;
    final String publisher;
    final String trayImageFile;
    final String imageDataVersion;
    final boolean animated;
    final List<Sticker> stickers;
    final String telegramSourceName;
    final int telegramPartIndex;

    Pack(
        String identifier,
        String name,
        String publisher,
        String trayImageFile,
        String imageDataVersion,
        boolean animated,
        List<Sticker> stickers
    ) {
        this(
            identifier,
            name,
            publisher,
            trayImageFile,
            imageDataVersion,
            animated,
            stickers,
            "",
            -1
        );
    }

    Pack(
        String identifier,
        String name,
        String publisher,
        String trayImageFile,
        String imageDataVersion,
        boolean animated,
        List<Sticker> stickers,
        String telegramSourceName,
        int telegramPartIndex
    ) {
        this.identifier = identifier;
        this.name = name;
        this.publisher = publisher;
        this.trayImageFile = trayImageFile;
        this.imageDataVersion = imageDataVersion;
        this.animated = animated;
        this.stickers = stickers;
        this.telegramSourceName = telegramSourceName == null
            ? ""
            : telegramSourceName.trim();
        this.telegramPartIndex = telegramPartIndex;
    }

    JSONObject toJson() throws JSONException {
        JSONObject result = new JSONObject();
        result.put("identifier", identifier);
        result.put("name", name);
        result.put("publisher", publisher);
        result.put("tray_image_file", trayImageFile);
        result.put("image_data_version", imageDataVersion);
        result.put("animated_sticker_pack", animated);
        if (!telegramSourceName.isEmpty()) {
            result.put("telegram_source_name", telegramSourceName);
            result.put("telegram_part_index", telegramPartIndex);
        }
        JSONArray stickerValues = new JSONArray();
        for (Sticker sticker : stickers) {
            stickerValues.put(sticker.toJson());
        }
        result.put("stickers", stickerValues);
        return result;
    }

    static Pack fromJson(JSONObject source) throws JSONException {
        List<Sticker> stickers = new ArrayList<>();
        JSONArray values = source.getJSONArray("stickers");
        for (int index = 0; index < values.length(); index++) {
            stickers.add(Sticker.fromJson(values.getJSONObject(index)));
        }
        return new Pack(
            source.getString("identifier"),
            source.getString("name"),
            source.getString("publisher"),
            source.getString("tray_image_file"),
            source.getString("image_data_version"),
            source.optBoolean("animated_sticker_pack", false),
            stickers,
            source.optString("telegram_source_name", ""),
            source.optInt("telegram_part_index", -1)
        );
    }
}
