package com.tool48.tgwabridge;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class StaticStickerRenderer {
    private static final int SIZE = 512;
    private static final int LIMIT = 100 * 1024;
    private static final int[] QUALITIES = {
        95, 90, 85, 80, 75, 70, 65, 60, 55, 50, 45, 40, 35, 30, 25, 20
    };

    private StaticStickerRenderer() {
    }

    static byte[] render(byte[] sourceData) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(
            sourceData,
            0,
            sourceData.length,
            bounds
        );
        if (
            bounds.outWidth <= 0
            || bounds.outHeight <= 0
            || bounds.outWidth > 2_048
            || bounds.outHeight > 2_048
        ) {
            throw new IOException(
                "Telegram static sticker dimensions are invalid."
            );
        }
        Bitmap source = BitmapFactory.decodeByteArray(
            sourceData,
            0,
            sourceData.length
        );
        if (source == null) {
            throw new IOException(
                "Android cannot decode a Telegram static sticker."
            );
        }
        try {
            return renderBitmap(
                source,
                1f,
                0f,
                0f,
                VideoStickerSettings.Background.TRANSPARENT
            );
        } finally {
            source.recycle();
        }
    }

    static Bitmap preview(Context context, Uri uri) throws IOException {
        return decode(context, uri);
    }

    static byte[] render(
        Context context,
        Uri uri,
        float scale,
        float offsetX,
        float offsetY,
        VideoStickerSettings.Background background
    ) throws IOException {
        Bitmap source = decode(context, uri);
        try {
            return renderBitmap(
                source,
                scale,
                offsetX,
                offsetY,
                background
            );
        } finally {
            source.recycle();
        }
    }

    private static byte[] renderBitmap(
        Bitmap source,
        float scale,
        float offsetX,
        float offsetY,
        VideoStickerSettings.Background background
    ) throws IOException {
        VideoStickerSettings settings = new VideoStickerSettings(
            0,
            200,
            1f,
            scale,
            offsetX,
            offsetY,
            background
        );
        Bitmap canvasBitmap = VideoStickerRenderer.compose(source, settings);
        try {
            for (int quality : QUALITIES) {
                ByteArrayOutputStream output =
                    new ByteArrayOutputStream(128 * 1024);
                if (
                    !canvasBitmap.compress(
                        Bitmap.CompressFormat.WEBP,
                        quality,
                        output
                    )
                ) {
                    throw new IOException(
                        "Android cannot encode a static WebP sticker."
                    );
                }
                byte[] result = output.toByteArray();
                if (result.length <= LIMIT) {
                    validate(result);
                    return result;
                }
            }
            throw new IOException(
                "A static sticker cannot be compressed below 100 KB."
            );
        } finally {
            canvasBitmap.recycle();
        }
    }

    private static Bitmap decode(Context context, Uri uri)
        throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IOException("Cannot open the selected image.");
            }
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("Android cannot read the selected image.");
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = 1;
        int longest = Math.max(bounds.outWidth, bounds.outHeight);
        while (longest / options.inSampleSize > 2_048) {
            options.inSampleSize *= 2;
        }
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IOException("Cannot reopen the selected image.");
            }
            Bitmap result = BitmapFactory.decodeStream(input, null, options);
            if (result == null) {
                throw new IOException("Android cannot decode the selected image.");
            }
            return result;
        }
    }

    private static void validate(byte[] data) throws IOException {
        WebpInspector.Result webp = WebpInspector.inspect(data);
        if (webp.animated) {
            throw new IOException(
                "Static WebP output unexpectedly contains animation."
            );
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (bounds.outWidth != SIZE || bounds.outHeight != SIZE) {
            throw new IOException(
                "Static WebP output is not exactly 512 x 512."
            );
        }
    }
}
