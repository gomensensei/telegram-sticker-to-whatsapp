package com.tool48.tgwabridge;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import java.io.IOException;

final class VideoStickerRenderer {
    interface ProgressListener {
        void onProgress(int percent, String message);
    }

    static final class Probe {
        final long durationMs;
        final int width;
        final int height;
        final Bitmap preview;

        Probe(long durationMs, int width, int height, Bitmap preview) {
            this.durationMs = durationMs;
            this.width = width;
            this.height = height;
            this.preview = preview;
        }
    }

    static final class Result {
        final byte[] data;
        final int fps;
        final int quality;
        final int frameCount;
        final long durationMs;

        Result(
            byte[] data,
            int fps,
            int quality,
            int frameCount,
            long durationMs
        ) {
            this.data = data;
            this.fps = fps;
            this.quality = quality;
            this.frameCount = frameCount;
            this.durationMs = durationMs;
        }
    }

    private static final int SIZE = 512;
    private static final int WHATSAPP_LIMIT = 500 * 1024;
    private static final int[][] PROFILES = {
        {20, 88},
        {18, 82},
        {15, 80},
        {15, 70},
        {12, 72},
        {12, 62},
        {10, 62},
        {8, 55},
        {6, 45}
    };

    private VideoStickerRenderer() {
    }

    static Probe probe(Context context, Uri uri) throws IOException {
        MediaMetadataRetriever retriever = retriever(context, uri);
        try {
            long durationMs = parseLong(
                retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                ),
                0
            );
            if (durationMs < 200) {
                throw new IOException("The selected video is shorter than 0.2 seconds.");
            }
            Bitmap preview = retriever.getFrameAtTime(
                0,
                MediaMetadataRetriever.OPTION_CLOSEST
            );
            if (preview == null) {
                throw new IOException("Android cannot decode this video.");
            }
            return new Probe(
                durationMs,
                preview.getWidth(),
                preview.getHeight(),
                preview
            );
        } catch (RuntimeException error) {
            throw new IOException(
                "Android cannot read the selected video: "
                    + friendly(error),
                error
            );
        } finally {
            retriever.release();
        }
    }

    static Bitmap previewAt(
        Context context,
        Uri uri,
        long timeMs
    ) throws IOException {
        MediaMetadataRetriever retriever = retriever(context, uri);
        try {
            Bitmap result = retriever.getFrameAtTime(
                timeMs * 1_000L,
                MediaMetadataRetriever.OPTION_CLOSEST
            );
            if (result == null) {
                throw new IOException("Cannot decode the selected video frame.");
            }
            return result;
        } catch (RuntimeException error) {
            throw new IOException(
                "Cannot decode preview frame: " + friendly(error),
                error
            );
        } finally {
            retriever.release();
        }
    }

    static Result render(
        Context context,
        Uri uri,
        VideoStickerSettings settings,
        ProgressListener listener
    ) throws IOException {
        IOException lastError = null;
        for (int profileIndex = 0; profileIndex < PROFILES.length; profileIndex++) {
            int fps = PROFILES[profileIndex][0];
            int quality = PROFILES[profileIndex][1];
            int startPercent = profileIndex * 100 / PROFILES.length;
            if (listener != null) {
                listener.onProgress(
                    startPercent,
                    "Encoding " + fps + " FPS at quality " + quality + "..."
                );
            }
            try {
                Result result = encode(
                    context,
                    uri,
                    settings,
                    fps,
                    quality,
                    listener,
                    profileIndex
                );
                if (result.data.length <= WHATSAPP_LIMIT) {
                    validate(result);
                    if (listener != null) {
                        listener.onProgress(
                            100,
                            "Animated WebP passed WhatsApp validation."
                        );
                    }
                    return result;
                }
                lastError = new IOException(
                    "Profile output is "
                        + result.data.length
                        + " bytes, above 500 KB."
                );
            } catch (IOException error) {
                lastError = error;
            }
        }
        throw new IOException(
            "Cannot keep this clip below WhatsApp's 500 KB limit. "
                + (
                    lastError == null
                        ? "Try a shorter clip."
                        : lastError.getMessage()
                ),
            lastError
        );
    }

    private static Result encode(
        Context context,
        Uri uri,
        VideoStickerSettings settings,
        int fps,
        int quality,
        ProgressListener listener,
        int profileIndex
    ) throws IOException {
        int frameCount = Math.max(
            2,
            (int) Math.ceil(settings.durationMs * fps / 1_000.0)
        );
        MediaMetadataRetriever retriever = retriever(context, uri);
        try (
            NativeWebpEncoder encoder = new NativeWebpEncoder(
                SIZE,
                SIZE,
                quality
            )
        ) {
            for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
                long sourceOffsetMs = (
                    frameIndex * settings.durationMs / frameCount
                );
                Bitmap source = retriever.getFrameAtTime(
                    (settings.startMs + sourceOffsetMs) * 1_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST
                );
                if (source == null) {
                    throw new IOException(
                        "Android could not decode video frame "
                            + (frameIndex + 1)
                            + "."
                    );
                }
                Bitmap frame = compose(source, settings);
                source.recycle();
                int timestampMs = (int) (
                    frameIndex * settings.durationMs / frameCount
                );
                try {
                    encoder.addFrame(frame, timestampMs);
                } finally {
                    frame.recycle();
                }
                if (listener != null) {
                    int profileBase = profileIndex * 100 / PROFILES.length;
                    int profileRange = 100 / PROFILES.length;
                    listener.onProgress(
                        Math.min(
                            99,
                            profileBase
                                + (
                                    (frameIndex + 1)
                                    * profileRange
                                    / frameCount
                                )
                        ),
                        "Rendered frame "
                            + (frameIndex + 1)
                            + " of "
                            + frameCount
                            + "."
                    );
                }
            }
            byte[] data = encoder.finish((int) settings.durationMs);
            return new Result(
                data,
                fps,
                quality,
                frameCount,
                settings.durationMs
            );
        } catch (RuntimeException error) {
            throw new IOException(
                "Video encoding failed: " + friendly(error),
                error
            );
        } finally {
            retriever.release();
        }
    }

    static Bitmap compose(
        Bitmap source,
        VideoStickerSettings settings
    ) {
        Bitmap output = Bitmap.createBitmap(
            SIZE,
            SIZE,
            Bitmap.Config.ARGB_8888
        );
        Canvas canvas = new Canvas(output);
        if (settings.background == VideoStickerSettings.Background.BLACK) {
            canvas.drawColor(Color.BLACK);
        } else if (
            settings.background == VideoStickerSettings.Background.WHITE
        ) {
            canvas.drawColor(Color.WHITE);
        } else {
            canvas.drawColor(Color.TRANSPARENT);
        }

        float baseScale = Math.min(
            (float) SIZE / source.getWidth(),
            (float) SIZE / source.getHeight()
        );
        float scale = baseScale * settings.scale;
        float renderedWidth = source.getWidth() * scale;
        float renderedHeight = source.getHeight() * scale;
        float left = (
            (SIZE - renderedWidth) / 2f
                + settings.offsetX * SIZE / 2f
        );
        float top = (
            (SIZE - renderedHeight) / 2f
                + settings.offsetY * SIZE / 2f
        );
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        matrix.postTranslate(left, top);
        Paint paint = new Paint(
            Paint.ANTI_ALIAS_FLAG
                | Paint.FILTER_BITMAP_FLAG
                | Paint.DITHER_FLAG
        );
        canvas.drawBitmap(source, matrix, paint);
        return output;
    }

    private static void validate(Result result) throws IOException {
        WebpInspector.Result animation = WebpInspector.inspect(result.data);
        if (!animation.animated || animation.frameCount < 2) {
            throw new IOException(
                "Encoder output does not contain a real multi-frame animation."
            );
        }
        if (animation.durationMs > 10_000) {
            throw new IOException("Animation exceeds 10 seconds.");
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(
            result.data,
            0,
            result.data.length,
            bounds
        );
        if (bounds.outWidth != SIZE || bounds.outHeight != SIZE) {
            throw new IOException(
                "Encoded animation is not exactly 512 x 512."
            );
        }
    }

    private static MediaMetadataRetriever retriever(
        Context context,
        Uri uri
    ) {
        MediaMetadataRetriever result = new MediaMetadataRetriever();
        result.setDataSource(context, uri);
        return result;
    }

    private static long parseLong(String value, long fallback) {
        try {
            return value == null ? fallback : Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String friendly(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
            ? error.getClass().getSimpleName()
            : message.trim();
    }
}
