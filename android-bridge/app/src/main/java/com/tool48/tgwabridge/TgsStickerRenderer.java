package com.tool48.tgwabridge;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;

import com.airbnb.lottie.LottieComposition;
import com.airbnb.lottie.LottieCompositionFactory;
import com.airbnb.lottie.LottieDrawable;
import com.airbnb.lottie.LottieResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

final class TgsStickerRenderer {
    private static final int SIZE = 512;
    private static final int LIMIT = 500 * 1024;
    private static final int MAX_TGS_BYTES = 1024 * 1024;
    private static final int MAX_JSON_BYTES = 4 * 1024 * 1024;
    private static final int[][] PROFILES = {
        {30, 88},
        {24, 84},
        {20, 82},
        {18, 76},
        {15, 72},
        {12, 68},
        {10, 60},
        {8, 52}
    };

    private TgsStickerRenderer() {
    }

    static byte[] render(
        byte[] tgsData,
        VideoStickerRenderer.ProgressListener listener
    ) throws IOException {
        if (tgsData == null || tgsData.length == 0) {
            throw new IOException("Telegram returned an empty TGS sticker.");
        }
        if (tgsData.length > MAX_TGS_BYTES) {
            throw new IOException("The TGS sticker exceeds 1 MB.");
        }
        LottieComposition composition = parse(tgsData);
        long durationMs = Math.max(
            200,
            Math.min(3_000, Math.round(composition.getDuration()))
        );
        IOException lastError = null;
        for (int profileIndex = 0;
             profileIndex < PROFILES.length;
             profileIndex++) {
            int fps = PROFILES[profileIndex][0];
            int quality = PROFILES[profileIndex][1];
            if (listener != null) {
                listener.onProgress(
                    profileIndex * 100 / PROFILES.length,
                    "Rendering TGS at "
                        + fps
                        + " FPS, quality "
                        + quality
                        + "..."
                );
            }
            try {
                byte[] output = encode(
                    composition,
                    durationMs,
                    fps,
                    quality,
                    profileIndex,
                    listener
                );
                if (output.length <= LIMIT) {
                    validate(output);
                    return output;
                }
                lastError = new IOException(
                    "TGS profile exceeds 500 KB."
                );
            } catch (IOException error) {
                lastError = error;
            }
        }
        throw new IOException(
            "Cannot convert this TGS below WhatsApp's 500 KB limit."
                + (
                    lastError == null
                        ? ""
                        : " " + lastError.getMessage()
                ),
            lastError
        );
    }

    private static LottieComposition parse(byte[] data)
        throws IOException {
        byte[] json;
        try (
            GZIPInputStream gzip = new GZIPInputStream(
                new ByteArrayInputStream(data)
            );
            ByteArrayOutputStream output =
                new ByteArrayOutputStream(data.length * 2)
        ) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = gzip.read(buffer)) != -1) {
                if (output.size() + count > MAX_JSON_BYTES) {
                    throw new IOException(
                        "The expanded TGS JSON exceeds 4 MB."
                    );
                }
                output.write(buffer, 0, count);
            }
            json = output.toByteArray();
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(json)) {
            LottieResult<LottieComposition> result =
                LottieCompositionFactory.fromJsonInputStreamSync(
                    input,
                    null
                );
            if (result.getValue() == null) {
                Throwable error = result.getException();
                throw new IOException(
                    "Android cannot parse this Telegram TGS sticker."
                        + (
                            error == null
                                ? ""
                                : " " + friendly(error)
                        ),
                    error
                );
            }
            return result.getValue();
        }
    }

    private static byte[] encode(
        LottieComposition composition,
        long durationMs,
        int fps,
        int quality,
        int profileIndex,
        VideoStickerRenderer.ProgressListener listener
    ) throws IOException {
        int frameCount = Math.max(
            2,
            (int) Math.ceil(durationMs * fps / 1_000.0)
        );
        LottieDrawable drawable = new LottieDrawable();
        drawable.setComposition(composition);
        drawable.setBounds(0, 0, SIZE, SIZE);
        try (
            NativeWebpEncoder encoder =
                new NativeWebpEncoder(SIZE, SIZE, quality)
        ) {
            for (int index = 0; index < frameCount; index++) {
                float progress = (float) index / frameCount;
                drawable.setProgress(progress);
                Bitmap frame = Bitmap.createBitmap(
                    SIZE,
                    SIZE,
                    Bitmap.Config.ARGB_8888
                );
                try {
                    Canvas canvas = new Canvas(frame);
                    canvas.drawColor(Color.TRANSPARENT);
                    drawable.draw(canvas);
                    encoder.addFrame(
                        frame,
                        (int) (index * durationMs / frameCount)
                    );
                } finally {
                    frame.recycle();
                }
                if (listener != null) {
                    int base = profileIndex * 100 / PROFILES.length;
                    int range = 100 / PROFILES.length;
                    listener.onProgress(
                        Math.min(
                            99,
                            base + (index + 1) * range / frameCount
                        ),
                        "Rendered TGS frame "
                            + (index + 1)
                            + " of "
                            + frameCount
                            + "."
                    );
                }
            }
            return encoder.finish((int) durationMs);
        } catch (RuntimeException error) {
            throw new IOException(
                "TGS animation rendering failed: " + friendly(error),
                error
            );
        }
    }

    private static void validate(byte[] data) throws IOException {
        WebpInspector.Result webp = WebpInspector.inspect(data);
        if (!webp.animated || webp.frameCount < 2) {
            throw new IOException(
                "TGS output is not a real multi-frame Animated WebP."
            );
        }
    }

    private static String friendly(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
            ? error.getClass().getSimpleName()
            : message.trim();
    }
}
