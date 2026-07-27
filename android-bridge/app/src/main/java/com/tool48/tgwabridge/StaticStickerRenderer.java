package com.tool48.tgwabridge;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

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
        Bitmap canvasBitmap = Bitmap.createBitmap(
            SIZE,
            SIZE,
            Bitmap.Config.ARGB_8888
        );
        try {
            Canvas canvas = new Canvas(canvasBitmap);
            canvas.drawColor(Color.TRANSPARENT);
            float scale = Math.min(
                (float) SIZE / source.getWidth(),
                (float) SIZE / source.getHeight()
            );
            float width = source.getWidth() * scale;
            float height = source.getHeight() * scale;
            Paint paint = new Paint(
                Paint.ANTI_ALIAS_FLAG
                    | Paint.FILTER_BITMAP_FLAG
                    | Paint.DITHER_FLAG
            );
            canvas.drawBitmap(
                source,
                null,
                new RectF(
                    (SIZE - width) / 2f,
                    (SIZE - height) / 2f,
                    (SIZE + width) / 2f,
                    (SIZE + height) / 2f
                ),
                paint
            );
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
            source.recycle();
            canvasBitmap.recycle();
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
