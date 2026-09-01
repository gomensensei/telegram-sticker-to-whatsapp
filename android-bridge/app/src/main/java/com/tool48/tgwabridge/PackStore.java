package com.tool48.tgwabridge;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

final class PackStore {
    private static final String PACKS_DIRECTORY = "packs";
    private static final String METADATA_FILE = "pack.json";

    private PackStore() {
    }

    static File root(Context context) {
        File root = new File(context.getFilesDir(), PACKS_DIRECTORY);
        if (!root.isDirectory() && !root.mkdirs()) {
            throw new IllegalStateException("Cannot create the sticker storage.");
        }
        return root;
    }

    static File packDirectory(Context context, String identifier) {
        return new File(root(context), identifier);
    }

    static synchronized List<Pack> list(Context context) {
        List<Pack> result = new ArrayList<>();
        File[] directories = root(context).listFiles(
            file -> file.isDirectory() && !file.getName().startsWith(".")
        );
        if (directories == null) {
            return result;
        }
        Arrays.sort(
            directories,
            Comparator.comparingLong(File::lastModified).reversed()
        );
        for (File directory : directories) {
            try {
                Pack pack = readPack(directory);
                if (pack != null) {
                    result.add(pack);
                }
            } catch (IOException | JSONException ignored) {
                // Hide an incomplete import instead of breaking every pack.
            }
        }
        return result;
    }

    static synchronized Pack find(Context context, String identifier) {
        if (!safeName(identifier)) {
            return null;
        }
        try {
            return readPack(packDirectory(context, identifier));
        } catch (IOException | JSONException error) {
            return null;
        }
    }

    static synchronized List<Pack> findTelegramSource(
        Context context,
        String sourceName
    ) {
        List<Pack> result = new ArrayList<>();
        String clean = sourceName == null ? "" : sourceName.trim();
        if (clean.isEmpty()) {
            return result;
        }
        for (Pack pack : list(context)) {
            if (clean.equals(pack.telegramSourceName)) {
                result.add(pack);
            }
        }
        result.sort(
            Comparator
                .comparing((Pack pack) -> pack.animated)
                .thenComparingInt(pack -> pack.telegramPartIndex)
        );
        return result;
    }

    static synchronized void writePack(File directory, Pack pack)
        throws IOException, JSONException {
        File metadata = new File(directory, METADATA_FILE);
        try (FileOutputStream output = new FileOutputStream(metadata)) {
            output.write(
                pack.toJson().toString(2).getBytes(StandardCharsets.UTF_8)
            );
        }
        directory.setLastModified(System.currentTimeMillis());
    }

    static synchronized void delete(Context context, String identifier)
        throws IOException {
        if (!safeName(identifier)) {
            throw new IOException("Invalid pack identifier.");
        }
        deleteTree(packDirectory(context, identifier));
        context.getContentResolver().notifyChange(
            StickerContentProvider.AUTHORITY_URI,
            null
        );
    }

    static void deleteTree(File path) throws IOException {
        if (!path.exists()) {
            return;
        }
        File[] children = path.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteTree(child);
            }
        }
        if (!path.delete()) {
            throw new IOException("Cannot delete " + path.getName());
        }
    }

    static boolean safeName(String value) {
        return value != null && value.matches("[A-Za-z0-9_.-]{1,128}");
    }

    static byte[] readFully(InputStream input, int maximum)
        throws IOException {
        byte[] buffer = new byte[8192];
        java.io.ByteArrayOutputStream output =
            new java.io.ByteArrayOutputStream();
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (output.size() + read > maximum) {
                throw new IOException("The selected file is too large.");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static Pack readPack(File directory)
        throws IOException, JSONException {
        File metadata = new File(directory, METADATA_FILE);
        if (!metadata.isFile()) {
            return null;
        }
        byte[] bytes;
        try (FileInputStream input = new FileInputStream(metadata)) {
            bytes = readFully(input, 512 * 1024);
        }
        return Pack.fromJson(
            new JSONObject(new String(bytes, StandardCharsets.UTF_8))
        );
    }
}
