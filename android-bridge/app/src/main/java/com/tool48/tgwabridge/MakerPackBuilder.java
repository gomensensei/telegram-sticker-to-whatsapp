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
        String publisher,
        boolean animated,
        Pack existing
    ) throws IOException, JSONException {
        int existingCount = existing == null ? 0 : existing.stickers.size();
        if (
            sources.isEmpty()
            || sources.size() + existingCount > 30
            || (existing != null && existing.animated != animated)
        ) {
            throw new IOException(
                "A sticker pack needs 1 to 30 stickers of one type."
            );
        }
        List<GeneratedPackBuilder.Item> items = new ArrayList<>();
        if (existing != null) {
            File directory = PackStore.packDirectory(
                context,
                existing.identifier
            );
            for (Sticker sticker : existing.stickers) {
                File rendered = new File(directory, sticker.fileName);
                File telegramSource = sticker.telegramSourceFile.isEmpty()
                    ? null
                    : new File(directory, sticker.telegramSourceFile);
                items.add(
                    new GeneratedPackBuilder.Item(
                        rendered,
                        sticker.emojis,
                        sticker.accessibilityText,
                        sticker.telegramSourceId,
                        telegramSource,
                        sticker.telegramSourceFormat
                    )
                );
            }
        }
        for (int index = 0; index < sources.size(); index++) {
            items.add(
                new GeneratedPackBuilder.Item(
                    sources.get(index),
                    Collections.singletonList(DEFAULT_EMOJI),
                    "Maker sticker " + (existingCount + index + 1)
                )
            );
        }
        return GeneratedPackBuilder.buildSingle(
            context,
            items,
            title,
            publisher,
            animated,
            existing
        );
    }
}
