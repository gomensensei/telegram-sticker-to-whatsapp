package com.tool48.tgwabridge;

import android.content.Context;
import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class MakerPackBuilder {
    private static final String DEFAULT_EMOJI = "\u2728";

    private MakerPackBuilder() {
    }

    static Pack build(
        Context context,
        List<File> sources,
        String title,
        String publisher
    ) throws IOException, JSONException {
        if (sources.size() < 3 || sources.size() > 30) {
            throw new IOException(
                "A WhatsApp animated pack needs 3 to 30 stickers."
            );
        }
        List<GeneratedPackBuilder.Item> items = new ArrayList<>();
        for (int index = 0; index < sources.size(); index++) {
            items.add(
                new GeneratedPackBuilder.Item(
                    sources.get(index),
                    Collections.singletonList(DEFAULT_EMOJI),
                    "Maker sticker " + (index + 1)
                )
            );
        }
        return GeneratedPackBuilder.buildAnimated(
            context,
            items,
            title,
            publisher
        );
    }
}
