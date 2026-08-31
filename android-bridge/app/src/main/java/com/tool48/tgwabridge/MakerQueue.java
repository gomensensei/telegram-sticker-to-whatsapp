package com.tool48.tgwabridge;

import android.content.Context;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

final class MakerQueue {
    private static final int MAX_ITEMS = 30;
    private static final int MAX_FILE_BYTES = 500 * 1024;

    private MakerQueue() {
    }

    static List<File> list(Context context) {
        return list(context, true);
    }

    static List<File> list(Context context, boolean animated) {
        File[] files = root(context, animated).listFiles(
            file -> file.isFile() && file.getName().endsWith(".webp")
        );
        if (files == null) {
            return new ArrayList<>();
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        return new ArrayList<>(Arrays.asList(files));
    }

    static File add(Context context, byte[] data) throws IOException {
        return add(context, data, true);
    }

    static File add(Context context, byte[] data, boolean animated)
        throws IOException {
        List<File> current = list(context, animated);
        if (current.size() >= MAX_ITEMS) {
            throw new IOException("The maker queue already has 30 stickers.");
        }
        validate(data, animated);
        File directory = root(context, animated);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Cannot create the maker queue.");
        }
        File destination = new File(
            directory,
            String.format(
                java.util.Locale.ROOT,
                "%013d-%s.webp",
                System.currentTimeMillis(),
                UUID.randomUUID().toString().substring(0, 8)
            )
        );
        try (FileOutputStream output = new FileOutputStream(destination)) {
            output.write(data);
        }
        return destination;
    }

    static void removeLast(Context context) throws IOException {
        removeLast(context, true);
    }

    static void removeLast(Context context, boolean animated)
        throws IOException {
        List<File> items = list(context, animated);
        if (items.isEmpty()) {
            return;
        }
        File target = items.get(items.size() - 1);
        if (!target.delete()) {
            throw new IOException("Cannot remove the last queued sticker.");
        }
    }

    static void clear(Context context) throws IOException {
        clear(context, true);
    }

    static void clear(Context context, boolean animated)
        throws IOException {
        for (File file : list(context, animated)) {
            if (!file.delete()) {
                throw new IOException(
                    "Cannot clear queued sticker " + file.getName() + "."
                );
            }
        }
    }

    private static File root(Context context, boolean animated) {
        return new File(
            context.getFilesDir(),
            animated ? "maker_queue" : "static_maker_queue"
        );
    }

    private static void validate(byte[] data, boolean animated)
        throws IOException {
        if (data == null || data.length == 0) {
            throw new IOException("WebP output is empty.");
        }
        int limit = animated ? MAX_FILE_BYTES : 100 * 1024;
        if (data.length > limit) {
            throw new IOException(
                animated
                    ? "Animated WebP exceeds 500 KB."
                    : "Static WebP exceeds 100 KB."
            );
        }
        WebpInspector.Result animation = WebpInspector.inspect(data);
        if (animated && (!animation.animated || animation.frameCount < 2)) {
            throw new IOException(
                "Animated WebP does not contain multiple real frames."
            );
        }
        if (!animated && animation.animated) {
            throw new IOException("Static WebP unexpectedly contains animation.");
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (bounds.outWidth != 512 || bounds.outHeight != 512) {
            throw new IOException("WebP must be exactly 512 x 512.");
        }
    }
}
