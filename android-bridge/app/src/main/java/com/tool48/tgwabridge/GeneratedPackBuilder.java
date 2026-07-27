package com.tool48.tgwabridge;

import android.content.Context;
import android.graphics.BitmapFactory;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class GeneratedPackBuilder {
    static final class Item {
        final File file;
        final List<String> emojis;
        final String accessibilityText;

        Item(
            File file,
            List<String> emojis,
            String accessibilityText
        ) {
            this.file = file;
            this.emojis = emojis == null || emojis.isEmpty()
                ? Collections.singletonList("\uD83D\uDE00")
                : new ArrayList<>(emojis);
            this.accessibilityText = accessibilityText == null
                ? ""
                : accessibilityText;
        }
    }

    private GeneratedPackBuilder() {
    }

    static List<Pack> buildSplit(
        Context context,
        List<Item> staticItems,
        List<Item> animatedItems,
        String title,
        String publisher
    ) throws IOException, JSONException {
        validateMinimum(staticItems, "static");
        validateMinimum(animatedItems, "animated");
        if (staticItems.isEmpty() && animatedItems.isEmpty()) {
            throw new IOException("There are no stickers to build.");
        }
        String cleanTitle = clean(title, "Telegram Sticker Pack", 128);
        String cleanPublisher = clean(publisher, "TGWA Maker", 128);
        boolean mixed = !staticItems.isEmpty() && !animatedItems.isEmpty();
        List<Pack> result = new ArrayList<>();
        List<String> created = new ArrayList<>();
        try {
            buildGroup(
                context,
                staticItems,
                cleanTitle,
                cleanPublisher,
                false,
                mixed,
                result,
                created
            );
            buildGroup(
                context,
                animatedItems,
                cleanTitle,
                cleanPublisher,
                true,
                mixed,
                result,
                created
            );
        } catch (IOException | JSONException | RuntimeException error) {
            for (String identifier : created) {
                try {
                    PackStore.deleteTree(
                        PackStore.packDirectory(context, identifier)
                    );
                } catch (IOException ignored) {
                    // Keep the original build error.
                }
            }
            throw error;
        }
        context.getContentResolver().notifyChange(
            StickerContentProvider.AUTHORITY_URI,
            null
        );
        return result;
    }

    static Pack buildAnimated(
        Context context,
        List<Item> items,
        String title,
        String publisher
    ) throws IOException, JSONException {
        List<Pack> packs = buildSplit(
            context,
            Collections.emptyList(),
            items,
            title,
            publisher
        );
        if (packs.size() != 1) {
            throw new IOException(
                "Maker queue must produce exactly one animated pack."
            );
        }
        return packs.get(0);
    }

    private static void buildGroup(
        Context context,
        List<Item> items,
        String baseTitle,
        String publisher,
        boolean animated,
        boolean mixed,
        List<Pack> result,
        List<String> created
    ) throws IOException, JSONException {
        if (items.isEmpty()) {
            return;
        }
        List<Integer> sizes = PackPartitioner.sizes(items.size());
        String typeName = animated ? "Animated" : "Static";
        String groupTitle = mixed
            ? baseTitle + " - " + typeName
            : baseTitle;
        int offset = 0;
        for (int partIndex = 0; partIndex < sizes.size(); partIndex++) {
            int size = sizes.get(partIndex);
            String partTitle = sizes.size() > 1
                ? groupTitle + " - Part " + (partIndex + 1)
                : groupTitle;
            Pack pack = writePack(
                context,
                new ArrayList<>(
                    items.subList(offset, offset + size)
                ),
                partTitle,
                publisher,
                animated
            );
            result.add(pack);
            created.add(pack.identifier);
            offset += size;
        }
    }

    private static Pack writePack(
        Context context,
        List<Item> items,
        String title,
        String publisher,
        boolean animated
    ) throws IOException, JSONException {
        String identifier = identifier(title);
        File staging = new File(
            PackStore.root(context),
            ".generated-" + UUID.randomUUID().toString()
        );
        File destination = PackStore.packDirectory(context, identifier);
        if (!staging.mkdirs()) {
            throw new IOException("Cannot create a temporary pack folder.");
        }
        try {
            List<Sticker> stickers = new ArrayList<>();
            byte[] first = null;
            for (int index = 0; index < items.size(); index++) {
                Item item = items.get(index);
                byte[] data = read(item.file, animated ? 500 : 100);
                validate(data, animated, index + 1);
                if (first == null) {
                    first = data;
                }
                String fileName = String.format(
                    Locale.ROOT,
                    "%03d.webp",
                    index + 1
                );
                write(new File(staging, fileName), data);
                stickers.add(
                    new Sticker(
                        fileName,
                        item.emojis,
                        item.accessibilityText.isEmpty()
                            ? title + " sticker " + (index + 1)
                            : item.accessibilityText
                    )
                );
            }
            PackImporter.writeTray(
                new File(staging, "cover.png"),
                null,
                first
            );
            Pack pack = new Pack(
                identifier,
                title,
                publisher,
                "cover.png",
                Long.toString(System.currentTimeMillis()),
                animated,
                stickers
            );
            PackStore.writePack(staging, pack);
            if (!staging.renameTo(destination)) {
                throw new IOException("Cannot finish the generated pack.");
            }
            return pack;
        } catch (IOException | JSONException | RuntimeException error) {
            try {
                PackStore.deleteTree(staging);
            } catch (IOException ignored) {
                // Keep the original build error.
            }
            throw error;
        }
    }

    private static void validate(
        byte[] data,
        boolean expectedAnimated,
        int index
    ) throws IOException {
        int limit = expectedAnimated ? 500 * 1024 : 100 * 1024;
        if (data.length > limit) {
            throw new IOException(
                "Sticker " + index + " exceeds the WhatsApp size limit."
            );
        }
        WebpInspector.Result webp = WebpInspector.inspect(data);
        if (webp.animated != expectedAnimated) {
            throw new IOException(
                "Sticker "
                    + index
                    + " does not match the pack animation type."
            );
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (bounds.outWidth != 512 || bounds.outHeight != 512) {
            throw new IOException(
                "Sticker " + index + " is not exactly 512 x 512."
            );
        }
    }

    private static void validateMinimum(List<Item> items, String type)
        throws IOException {
        if (!items.isEmpty() && items.size() < 3) {
            throw new IOException(
                "After separating static and animated stickers, the "
                    + type
                    + " pack has only "
                    + items.size()
                    + ". WhatsApp requires at least 3; TGWA Maker will not "
                    + "duplicate stickers."
            );
        }
    }

    private static byte[] read(File file, int maximumKb)
        throws IOException {
        int maximum = maximumKb * 1024;
        try (
            FileInputStream input = new FileInputStream(file);
            ByteArrayOutputStream output =
                new ByteArrayOutputStream(
                    (int) Math.min(file.length(), maximum)
                )
        ) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > maximum) {
                    throw new IOException(
                        file.getName() + " exceeds the size limit."
                    );
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static void write(File destination, byte[] data)
        throws IOException {
        try (FileOutputStream output = new FileOutputStream(destination)) {
            output.write(data);
        }
    }

    private static String clean(
        String value,
        String fallback,
        int maximum
    ) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty()) {
            clean = fallback;
        }
        return clean.substring(0, Math.min(maximum, clean.length()));
    }

    private static String identifier(String title) {
        String base = title.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_.-]+", "_")
            .replaceAll("^_+|_+$", "");
        if (base.isEmpty()) {
            base = "tgwa_maker";
        }
        return base.substring(0, Math.min(72, base.length()))
            + "_"
            + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 10);
    }
}
