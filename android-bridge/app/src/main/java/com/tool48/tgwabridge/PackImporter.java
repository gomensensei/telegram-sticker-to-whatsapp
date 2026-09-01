package com.tool48.tgwabridge;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class PackImporter {
    private static final int MAX_ARCHIVE_BYTES = 35 * 1024 * 1024;
    private static final int MAX_ENTRY_BYTES = 1024 * 1024;
    private static final int MAX_ENTRIES = 80;
    private static final int STATIC_STICKER_LIMIT = 100 * 1024;
    private static final int ANIMATED_STICKER_LIMIT = 500 * 1024;
    private static final String DEFAULT_EMOJI = "\uD83D\uDE00";

    private PackImporter() {
    }

    static List<Pack> importUri(Context context, Uri uri)
        throws IOException, JSONException {
        Map<String, byte[]> entries = readArchive(context, uri);
        JSONObject metadata = firstPackMetadata(entries.get("contents.json"));
        String title = cleanText(
            metadata.optString("name", textEntry(entries, "title.txt")),
            "TGWA Sticker Pack",
            128
        );
        String publisher = cleanText(
            metadata.optString(
                "publisher",
                textEntry(entries, "author.txt")
            ),
            "TGWA",
            128
        );

        String configuredTray = metadata.optString(
            "tray_image_file",
            ""
        );
        List<String> stickerNames = new ArrayList<>();
        for (String name : entries.keySet()) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (
                lower.endsWith(".webp")
                && !isTrayName(lower)
                && !name.equals(configuredTray)
            ) {
                stickerNames.add(name);
            }
        }
        Collections.sort(stickerNames, Comparator.naturalOrder());
        if (stickerNames.size() < 3 || stickerNames.size() > 60) {
            throw new IOException(
                "An import must contain between 3 and 60 WebP stickers. "
                    + "TGWA Maker will split them into valid packs."
            );
        }

        List<String> animatedNames = new ArrayList<>();
        List<String> staticNames = new ArrayList<>();
        for (String name : stickerNames) {
            byte[] data = entries.get(name);
            WebpInspector.Result webp = WebpInspector.inspect(data);
            int limit = webp.animated
                ? ANIMATED_STICKER_LIMIT
                : STATIC_STICKER_LIMIT;
            if (data.length > limit) {
                throw new IOException(
                    name + " exceeds the WhatsApp file-size limit."
                );
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, options);
            if (options.outWidth != 512 || options.outHeight != 512) {
                throw new IOException(name + " must be exactly 512 x 512.");
            }
            if (webp.animated) {
                animatedNames.add(name);
            } else {
                staticNames.add(name);
            }
        }

        boolean mixed = !animatedNames.isEmpty() && !staticNames.isEmpty();
        validateGroupMinimum(animatedNames, "animated");
        validateGroupMinimum(staticNames, "static");
        Map<String, Sticker> sourceMetadata = stickerMetadata(metadata);
        byte[] tray = findTray(entries, metadata);
        List<Pack> imported = new ArrayList<>();
        List<String> importedIdentifiers = new ArrayList<>();
        try {
            if (!animatedNames.isEmpty()) {
                imported.addAll(
                    importGroup(
                        context,
                        entries,
                        sourceMetadata,
                        tray,
                        title,
                        publisher,
                        animatedNames,
                        true,
                        mixed,
                        importedIdentifiers
                    )
                );
            }
            if (!staticNames.isEmpty()) {
                imported.addAll(
                    importGroup(
                        context,
                        entries,
                        sourceMetadata,
                        tray,
                        title,
                        publisher,
                        staticNames,
                        false,
                        mixed,
                        importedIdentifiers
                    )
                );
            }
        } catch (IOException | JSONException | RuntimeException error) {
            for (String identifier : importedIdentifiers) {
                try {
                    PackStore.deleteTree(
                        PackStore.packDirectory(context, identifier)
                    );
                } catch (IOException ignored) {
                    // Preserve the original import error.
                }
            }
            throw error;
        }
        context.getContentResolver().notifyChange(
            StickerContentProvider.AUTHORITY_URI,
            null
        );
        return imported;
    }

    private static List<Pack> importGroup(
        Context context,
        Map<String, byte[]> entries,
        Map<String, Sticker> sourceMetadata,
        byte[] tray,
        String baseTitle,
        String publisher,
        List<String> names,
        boolean animated,
        boolean mixed,
        List<String> importedIdentifiers
    ) throws IOException, JSONException {
        List<Integer> partSizes = PackPartitioner.sizes(names.size());
        List<Pack> result = new ArrayList<>();
        int offset = 0;
        String typeName = animated ? "Animated" : "Static";
        String groupTitle = mixed
            ? baseTitle + " - " + typeName
            : baseTitle;
        for (int partIndex = 0; partIndex < partSizes.size(); partIndex++) {
            int partSize = partSizes.get(partIndex);
            String partTitle = partSizes.size() > 1
                ? groupTitle + " - Part " + (partIndex + 1)
                : groupTitle;
            List<String> partNames = new ArrayList<>(
                names.subList(offset, offset + partSize)
            );
            Pack pack = writePack(
                context,
                entries,
                sourceMetadata,
                tray,
                partTitle,
                publisher,
                partNames,
                animated
            );
            result.add(pack);
            importedIdentifiers.add(pack.identifier);
            offset += partSize;
        }
        return result;
    }

    private static Pack writePack(
        Context context,
        Map<String, byte[]> entries,
        Map<String, Sticker> sourceMetadata,
        byte[] tray,
        String title,
        String publisher,
        List<String> names,
        boolean animated
    ) throws IOException, JSONException {
        String identifier = makeIdentifier(title);
        File finalDirectory = PackStore.packDirectory(context, identifier);
        File staging = new File(
            PackStore.root(context),
            ".import-" + UUID.randomUUID().toString()
        );
        if (!staging.mkdirs()) {
            throw new IOException("Cannot create a temporary import folder.");
        }

        try {
            List<Sticker> stickers = new ArrayList<>();
            for (int index = 0; index < names.size(); index++) {
                String oldName = names.get(index);
                byte[] data = entries.get(oldName);
                WebpInspector.Result webp = WebpInspector.inspect(data);
                if (webp.animated != animated) {
                    throw new IOException(
                        oldName + " changed animation type during import."
                    );
                }
                String newName = String.format(
                    Locale.ROOT,
                    "%03d.webp",
                    index + 1
                );
                writeBytes(new File(staging, newName), data);
                Sticker source = sourceMetadata.get(oldName);
                List<String> emojis = source == null
                    ? Collections.singletonList(DEFAULT_EMOJI)
                    : source.emojis;
                String accessibility = source == null
                    ? title + " sticker " + (index + 1)
                    : source.accessibilityText;
                stickers.add(new Sticker(newName, emojis, accessibility));
            }

            writeTray(
                new File(staging, "cover.png"),
                tray,
                entries.get(names.get(0))
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
            if (finalDirectory.exists()) {
                PackStore.deleteTree(finalDirectory);
            }
            if (!staging.renameTo(finalDirectory)) {
                throw new IOException("Cannot finish importing the pack.");
            }
            return pack;
        } catch (IOException | JSONException | RuntimeException error) {
            try {
                PackStore.deleteTree(staging);
            } catch (IOException ignored) {
                // Keep the original error.
            }
            throw error;
        }
    }

    private static void validateGroupMinimum(
        List<String> names,
        String type
    ) throws IOException {
        if (!names.isEmpty() && names.size() < 3) {
            throw new IOException(
                "After separating static and animated stickers, the "
                    + type
                    + " pack has only "
                    + names.size()
                    + ". WhatsApp requires at least 3; stickers are never "
                    + "duplicated just to reach the minimum."
            );
        }
    }

    private static Map<String, byte[]> readArchive(Context context, Uri uri)
        throws IOException {
        InputStream raw = context.getContentResolver().openInputStream(uri);
        if (raw == null) {
            throw new IOException("Cannot open the selected file.");
        }
        Map<String, byte[]> result = new LinkedHashMap<>();
        int total = 0;
        int count = 0;
        try (InputStream input = raw;
             ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                count++;
                if (count > MAX_ENTRIES) {
                    throw new IOException("The archive has too many files.");
                }
                String name = entry.getName();
                if (
                    name == null
                    || name.contains("/")
                    || name.contains("\\")
                    || name.equals(".")
                    || name.equals("..")
                ) {
                    throw new IOException("The archive contains an unsafe path.");
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    if (output.size() + read > MAX_ENTRY_BYTES) {
                        throw new IOException(name + " is too large.");
                    }
                    output.write(buffer, 0, read);
                }
                byte[] data = output.toByteArray();
                total += data.length;
                if (total > MAX_ARCHIVE_BYTES) {
                    throw new IOException("The archive is too large.");
                }
                result.put(name, data);
            }
        }
        return result;
    }

    private static JSONObject firstPackMetadata(byte[] data)
        throws JSONException {
        if (data == null) {
            return new JSONObject();
        }
        JSONObject root = new JSONObject(
            new String(data, StandardCharsets.UTF_8)
        );
        JSONArray packs = root.optJSONArray("sticker_packs");
        if (packs != null && packs.length() > 0) {
            return packs.getJSONObject(0);
        }
        return root;
    }

    private static Map<String, Sticker> stickerMetadata(JSONObject metadata) {
        Map<String, Sticker> result = new HashMap<>();
        JSONArray values = metadata.optJSONArray("stickers");
        if (values == null) {
            return result;
        }
        for (int index = 0; index < values.length(); index++) {
            JSONObject value = values.optJSONObject(index);
            if (value == null) {
                continue;
            }
            String fileName = value.optString(
                "image_file",
                value.optString("file_name", "")
            );
            if (fileName.isEmpty()) {
                continue;
            }
            List<String> emojis = new ArrayList<>();
            JSONArray emojiValues = value.optJSONArray("emojis");
            if (emojiValues != null) {
                for (
                    int emojiIndex = 0;
                    emojiIndex < emojiValues.length();
                    emojiIndex++
                ) {
                    String emoji = emojiValues.optString(emojiIndex, "");
                    if (!emoji.isEmpty()) {
                        emojis.add(emoji);
                    }
                }
            }
            if (emojis.isEmpty()) {
                emojis.add(DEFAULT_EMOJI);
            }
            result.put(
                fileName,
                new Sticker(
                    fileName,
                    emojis,
                    value.optString("accessibility_text", "")
                )
            );
        }
        return result;
    }

    private static byte[] findTray(
        Map<String, byte[]> entries,
        JSONObject metadata
    ) {
        String configured = metadata.optString("tray_image_file", "");
        if (!configured.isEmpty() && entries.containsKey(configured)) {
            return entries.get(configured);
        }
        for (String candidate : new String[] {
            "cover.png", "tray.png", "tray.webp"
        }) {
            if (entries.containsKey(candidate)) {
                return entries.get(candidate);
            }
        }
        return null;
    }

    private static boolean isTrayName(String lower) {
        return lower.equals("cover.webp")
            || lower.equals("tray.webp")
            || lower.equals("tray_image.webp");
    }

    static void writeTray(
        File destination,
        byte[] preferred,
        byte[] fallback
    ) throws IOException {
        Bitmap source = decodeBitmap(preferred);
        if (source == null) {
            source = decodeBitmap(fallback);
        }
        if (source == null) {
            throw new IOException("Cannot create the tray icon.");
        }
        Bitmap tray = Bitmap.createBitmap(
            96,
            96,
            Bitmap.Config.ARGB_8888
        );
        Canvas canvas = new Canvas(tray);
        Paint paint = new Paint(
            Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG
        );
        float scale = Math.min(
            88f / source.getWidth(),
            88f / source.getHeight()
        );
        float width = source.getWidth() * scale;
        float height = source.getHeight() * scale;
        canvas.drawBitmap(
            source,
            null,
            new android.graphics.RectF(
                (96f - width) / 2f,
                (96f - height) / 2f,
                (96f + width) / 2f,
                (96f + height) / 2f
            ),
            paint
        );
        try (FileOutputStream output = new FileOutputStream(destination)) {
            if (!tray.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IOException("Cannot encode the tray icon.");
            }
        } finally {
            source.recycle();
            tray.recycle();
        }
        if (destination.length() > 50 * 1024) {
            throw new IOException("The generated tray icon exceeds 50 KB.");
        }
    }

    private static Bitmap decodeBitmap(byte[] data) {
        if (data == null) {
            return null;
        }
        return BitmapFactory.decodeStream(new ByteArrayInputStream(data));
    }

    private static void writeBytes(File destination, byte[] data)
        throws IOException {
        try (FileOutputStream output = new FileOutputStream(destination)) {
            output.write(data);
        }
    }

    private static String textEntry(
        Map<String, byte[]> entries,
        String name
    ) {
        byte[] data = entries.get(name);
        return data == null
            ? ""
            : new String(data, StandardCharsets.UTF_8).trim();
    }

    private static String cleanText(
        String value,
        String fallback,
        int maximum
    ) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.isEmpty()) {
            cleaned = fallback;
        }
        return cleaned.substring(0, Math.min(maximum, cleaned.length()));
    }

    private static String makeIdentifier(String value) {
        String cleaned = value == null
            ? ""
            : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]+", "_")
                .replaceAll("^_+|_+$", "");
        if (cleaned.isEmpty()) {
            cleaned = "tgwa_pack";
        }
        if (cleaned.length() > 72) {
            cleaned = cleaned.substring(0, 72);
        }
        return cleaned + "_" + UUID.randomUUID().toString()
            .replace("-", "")
            .substring(0, 10);
    }
}
