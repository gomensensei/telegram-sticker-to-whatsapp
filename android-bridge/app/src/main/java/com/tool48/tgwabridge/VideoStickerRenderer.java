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
import android.os.Build;
import android.os.SystemClock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class VideoStickerRenderer {
    private static final class NonAnimatedDecodeException
        extends IOException {
        NonAnimatedDecodeException(String message) {
            super(message);
        }
    }

    private static final class CacheCapacityException
        extends IOException {
        CacheCapacityException(String message) {
            super(message);
        }
    }

    private static final class ColorMismatchException
        extends IOException {
        ColorMismatchException(String message) {
            super(message);
        }
    }

    private static final class FrameCache implements AutoCloseable {
        final List<Bitmap> frames;

        FrameCache(List<Bitmap> frames) {
            this.frames = frames;
        }

        @Override
        public void close() {
            for (Bitmap frame : frames) {
                if (frame != null && !frame.isRecycled()) {
                    frame.recycle();
                }
            }
            frames.clear();
        }
    }

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
    private static final long CACHE_HEAP_RESERVE = 24L * 1024L * 1024L;
    private static final long MAX_RENDER_MS = 5L * 60L * 1_000L;
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
        long startedAt = SystemClock.elapsedRealtime();
        FrameCache cache = null;
        IOException sequentialCacheError = null;
        try {
            cache = decodeOnce(
                context,
                uri,
                settings,
                listener,
                startedAt
            );
        } catch (CacheCapacityException error) {
            if (listener != null) {
                listener.onProgress(
                    0,
                    "Using the low-memory compatibility encoder..."
                );
            }
        } catch (IOException error) {
            sequentialCacheError = error;
            if (listener != null) {
                listener.onProgress(
                    0,
                    error instanceof ColorMismatchException
                        ? "Raw video colors looked corrupted; using the safe RGB decoder..."
                        : "Using the Android compatibility decoder..."
                );
            }
        }

        IOException lastError = null;
        try {
            for (
                int profileIndex = 0;
                profileIndex < PROFILES.length;
                profileIndex++
            ) {
                checkRenderDeadline(startedAt);
                int fps = PROFILES[profileIndex][0];
                int quality = PROFILES[profileIndex][1];
                int startPercent = profileStart(
                    profileIndex,
                    cache != null
                );
                if (listener != null) {
                    listener.onProgress(
                        startPercent,
                        "Encoding "
                            + fps
                            + " FPS at quality "
                            + quality
                            + "..."
                    );
                }
                try {
                    Result result = cache == null
                        ? encode(
                            context,
                            uri,
                            settings,
                            fps,
                            quality,
                            listener,
                            profileIndex,
                            sequentialCacheError
                        )
                        : encodeFromCache(
                            cache,
                            settings,
                            fps,
                            quality,
                            listener,
                            profileIndex,
                            startedAt
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
                    if (error instanceof NonAnimatedDecodeException) {
                        throw error;
                    }
                }
            }
        } finally {
            if (cache != null) {
                cache.close();
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
        int profileIndex,
        IOException priorSequentialError
    ) throws IOException {
        IOException sequentialError = priorSequentialError;
        if (sequentialError == null) {
            try {
                Result sequential = encodeBySequentialCodec(
                    context,
                    uri,
                    settings,
                    fps,
                    quality,
                    listener,
                    profileIndex
                );
                requireMultiFrame(sequential);
                return sequential;
            } catch (IOException error) {
                sequentialError = error;
            }
        }

        IOException indexedError = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                Result indexed = encodeByFrameIndex(
                    context,
                    uri,
                    settings,
                    fps,
                    quality,
                    listener,
                    profileIndex
                );
                requireMultiFrame(indexed);
                return indexed;
            } catch (IOException error) {
                indexedError = error;
                if (sequentialError != null) {
                    error.addSuppressed(sequentialError);
                }
            }
        }
        try {
            Result timed = encodeByTimestamp(
                context,
                uri,
                settings,
                fps,
                quality,
                listener,
                profileIndex
            );
            requireMultiFrame(timed);
            return timed;
        } catch (IOException error) {
            if (sequentialError != null) {
                error.addSuppressed(sequentialError);
            }
            if (indexedError != null) {
                error.addSuppressed(indexedError);
            }
            if (
                error instanceof NonAnimatedDecodeException
                && sequentialError != null
            ) {
                NonAnimatedDecodeException combined =
                    new NonAnimatedDecodeException(
                        "Android sequential WEBM decoding failed: "
                            + friendly(sequentialError)
                            + " The device fallbacks also exposed only "
                            + "one video frame."
                    );
                combined.addSuppressed(error);
                throw combined;
            }
            throw error;
        }
    }

    private static FrameCache decodeOnce(
        Context context,
        Uri uri,
        VideoStickerSettings settings,
        ProgressListener listener,
        long startedAt
    ) throws IOException {
        int frameCount = outputFrameCount(
            settings,
            PROFILES[0][0]
        );
        ensureCacheCapacity(frameCount);
        long[] targetTimesUs = FrameTimePlan.sourceTimesUs(
            settings.startMs,
            settings.durationMs,
            frameCount
        );
        List<Bitmap> frames = new ArrayList<>(frameCount);
        try {
            if (listener != null) {
                listener.onProgress(
                    0,
                    "Decoding the clip once for all quality profiles..."
                );
            }
            SequentialVideoDecoder.decode(
                context,
                uri,
                targetTimesUs,
                (source, frameIndex) -> {
                    checkRenderDeadline(startedAt);
                    frames.add(compose(source, settings));
                    if (listener != null) {
                        listener.onProgress(
                            Math.min(
                                10,
                                (frameIndex + 1) * 10 / frameCount
                            ),
                            "Decoded frame "
                                + (frameIndex + 1)
                                + " of "
                                + frameCount
                                + " once."
                        );
                    }
                }
            );
            if (frames.size() != frameCount) {
                throw new IOException(
                    "Android cached only "
                        + frames.size()
                        + " of "
                        + frameCount
                        + " required frames."
                );
            }
            verifyCachedColors(
                context,
                uri,
                settings,
                frames.get(0)
            );
            return new FrameCache(frames);
        } catch (IOException | RuntimeException error) {
            new FrameCache(frames).close();
            throw error;
        }
    }

    private static void verifyCachedColors(
        Context context,
        Uri uri,
        VideoStickerSettings settings,
        Bitmap decoded
    ) throws IOException {
        Bitmap referenceSource = previewAt(
            context,
            uri,
            settings.startMs
        );
        Bitmap reference = null;
        Bitmap adjacentSource = null;
        Bitmap adjacent = null;
        try {
            reference = compose(referenceSource, settings);
            int delta = sampledRgbDelta(decoded, reference);
            long adjacentMs = Math.min(
                settings.startMs + settings.durationMs - 1L,
                settings.startMs + 50L
            );
            if (adjacentMs > settings.startMs) {
                try {
                    adjacentSource = previewAt(context, uri, adjacentMs);
                    adjacent = compose(adjacentSource, settings);
                    delta = Math.min(
                        delta,
                        sampledRgbDelta(decoded, adjacent)
                    );
                } catch (IOException ignored) {
                    // The exact start frame remains a valid reference. Some
                    // retrievers cannot seek a second time near a clip edge.
                }
            }
            if (delta > 48) {
                throw new ColorMismatchException(
                    "Android raw YUV differs from its RGB preview by "
                        + delta
                        + " levels; refusing the grey/magenta-green frame."
                );
            }
        } finally {
            referenceSource.recycle();
            if (reference != null) {
                reference.recycle();
            }
            if (adjacentSource != null && adjacentSource != referenceSource) {
                adjacentSource.recycle();
            }
            if (adjacent != null && adjacent != reference) {
                adjacent.recycle();
            }
        }
    }

    static int sampledRgbDelta(Bitmap first, Bitmap second) {
        if (
            first == null
            || second == null
            || first.getWidth() != second.getWidth()
            || first.getHeight() != second.getHeight()
        ) {
            return 255;
        }
        int width = first.getWidth();
        int height = first.getHeight();
        int step = Math.max(1, Math.min(width, height) / 16);
        long difference = 0L;
        int samples = 0;
        for (int y = step / 2; y < height; y += step) {
            for (int x = step / 2; x < width; x += step) {
                int one = first.getPixel(x, y);
                int two = second.getPixel(x, y);
                difference += Math.abs(Color.red(one) - Color.red(two));
                difference += Math.abs(Color.green(one) - Color.green(two));
                difference += Math.abs(Color.blue(one) - Color.blue(two));
                samples += 3;
            }
        }
        return samples == 0
            ? 255
            : (int) Math.min(255L, difference / samples);
    }

    private static Result encodeFromCache(
        FrameCache cache,
        VideoStickerSettings settings,
        int fps,
        int quality,
        ProgressListener listener,
        int profileIndex,
        long startedAt
    ) throws IOException {
        int frameCount = outputFrameCount(settings, fps);
        try (
            NativeWebpEncoder encoder = new NativeWebpEncoder(
                SIZE,
                SIZE,
                quality
            )
        ) {
            for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
                checkRenderDeadline(startedAt);
                int sourceIndex = FrameCachePlan.sourceIndex(
                    cache.frames.size(),
                    frameCount,
                    frameIndex
                );
                int timestampMs = outputTimestampMs(
                    settings,
                    frameIndex,
                    frameCount
                );
                encoder.addFrame(
                    cache.frames.get(sourceIndex),
                    timestampMs
                );
                reportCachedFrameProgress(
                    listener,
                    profileIndex,
                    frameIndex,
                    frameCount
                );
            }
            long outputDurationMs = settings.outputDurationMs();
            byte[] data = encoder.finish((int) outputDurationMs);
            Result result = new Result(
                data,
                fps,
                quality,
                frameCount,
                outputDurationMs
            );
            requireMultiFrame(result);
            return result;
        } catch (RuntimeException error) {
            throw new IOException(
                "Cached video encoding failed: " + friendly(error),
                error
            );
        }
    }

    private static Result encodeBySequentialCodec(
        Context context,
        Uri uri,
        VideoStickerSettings settings,
        int fps,
        int quality,
        ProgressListener listener,
        int profileIndex
    ) throws IOException {
        int frameCount = outputFrameCount(settings, fps);
        long[] targetTimesUs = FrameTimePlan.sourceTimesUs(
            settings.startMs,
            settings.durationMs,
            frameCount
        );
        try (
            NativeWebpEncoder encoder = new NativeWebpEncoder(
                SIZE,
                SIZE,
                quality
            )
        ) {
            SequentialVideoDecoder.decode(
                context,
                uri,
                targetTimesUs,
                (source, frameIndex) -> addOutputFrame(
                    encoder,
                    source,
                    settings,
                    frameIndex,
                    frameCount,
                    listener,
                    profileIndex
                )
            );
            long outputDurationMs = settings.outputDurationMs();
            byte[] data = encoder.finish((int) outputDurationMs);
            return new Result(
                data,
                fps,
                quality,
                frameCount,
                outputDurationMs
            );
        } catch (IllegalArgumentException error) {
            throw new IOException(
                "Android cannot calculate sequential frame times: "
                    + friendly(error),
                error
            );
        } catch (RuntimeException error) {
            throw new IOException(
                "Sequential video encoding failed: " + friendly(error),
                error
            );
        }
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.P)
    private static Result encodeByFrameIndex(
        Context context,
        Uri uri,
        VideoStickerSettings settings,
        int fps,
        int quality,
        ProgressListener listener,
        int profileIndex
    ) throws IOException {
        int outputFrameCount = outputFrameCount(settings, fps);
        MediaMetadataRetriever retriever = retriever(context, uri);
        try (
            NativeWebpEncoder encoder = new NativeWebpEncoder(
                SIZE,
                SIZE,
                quality
            )
        ) {
            long sourceDurationMs = parseLong(
                retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                ),
                0
            );
            int sourceFrameCount = (int) parseLong(
                retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT
                ),
                0
            );
            int[] sourceIndexes = FrameSamplingPlan.sourceIndexes(
                sourceDurationMs,
                sourceFrameCount,
                settings.startMs,
                settings.durationMs,
                outputFrameCount
            );
            int outputIndex = 0;
            while (outputIndex < outputFrameCount) {
                int batchStart = sourceIndexes[outputIndex];
                int batchCount = Math.min(
                    8,
                    sourceFrameCount - batchStart
                );
                List<Bitmap> batch;
                try {
                    batch = retriever.getFramesAtIndex(
                        batchStart,
                        batchCount
                    );
                } catch (RuntimeException error) {
                    throw new IOException(
                        "Android indexed WEBM decoding failed: "
                            + friendly(error),
                        error
                    );
                }
                if (batch == null || batch.isEmpty()) {
                    throw new IOException(
                        "Android returned no indexed WEBM frames."
                    );
                }
                int before = outputIndex;
                try {
                    while (outputIndex < outputFrameCount) {
                        int localIndex =
                            sourceIndexes[outputIndex] - batchStart;
                        if (localIndex < 0 || localIndex >= batch.size()) {
                            break;
                        }
                        Bitmap source = batch.get(localIndex);
                        if (source == null || source.isRecycled()) {
                            throw new IOException(
                                "Android returned an empty indexed WEBM "
                                    + "frame."
                            );
                        }
                        addOutputFrame(
                            encoder,
                            source,
                            settings,
                            outputIndex,
                            outputFrameCount,
                            listener,
                            profileIndex
                        );
                        outputIndex++;
                    }
                } finally {
                    for (Bitmap frame : batch) {
                        if (frame != null && !frame.isRecycled()) {
                            frame.recycle();
                        }
                    }
                }
                if (outputIndex == before) {
                    throw new IOException(
                        "Android indexed WEBM decoding did not advance."
                    );
                }
            }
            long outputDurationMs = settings.outputDurationMs();
            byte[] data = encoder.finish((int) outputDurationMs);
            return new Result(
                data,
                fps,
                quality,
                outputFrameCount,
                outputDurationMs
            );
        } catch (IllegalArgumentException error) {
            throw new IOException(
                "Android cannot map WEBM frame indexes: "
                    + friendly(error),
                error
            );
        } catch (RuntimeException error) {
            throw new IOException(
                "Indexed video encoding failed: " + friendly(error),
                error
            );
        } finally {
            retriever.release();
        }
    }

    private static Result encodeByTimestamp(
        Context context,
        Uri uri,
        VideoStickerSettings settings,
        int fps,
        int quality,
        ProgressListener listener,
        int profileIndex
    ) throws IOException {
        int frameCount = outputFrameCount(settings, fps);
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
                try {
                    addOutputFrame(
                        encoder,
                        source,
                        settings,
                        frameIndex,
                        frameCount,
                        listener,
                        profileIndex
                    );
                } finally {
                    source.recycle();
                }
            }
            long outputDurationMs = settings.outputDurationMs();
            byte[] data = encoder.finish((int) outputDurationMs);
            return new Result(
                data,
                fps,
                quality,
                frameCount,
                outputDurationMs
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

    private static int outputFrameCount(
        VideoStickerSettings settings,
        int fps
    ) {
        return Math.max(
            2,
            (int) Math.ceil(
                settings.outputDurationMs() * fps / 1_000.0
            )
        );
    }

    private static void addOutputFrame(
        NativeWebpEncoder encoder,
        Bitmap source,
        VideoStickerSettings settings,
        int frameIndex,
        int frameCount,
        ProgressListener listener,
        int profileIndex
    ) throws IOException {
        Bitmap frame = compose(source, settings);
        int timestampMs = outputTimestampMs(
            settings,
            frameIndex,
            frameCount
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

    private static int outputTimestampMs(
        VideoStickerSettings settings,
        int frameIndex,
        int frameCount
    ) {
        return (int) (
            frameIndex * settings.outputDurationMs() / frameCount
        );
    }

    private static void reportCachedFrameProgress(
        ProgressListener listener,
        int profileIndex,
        int frameIndex,
        int frameCount
    ) {
        if (listener == null) {
            return;
        }
        int profileBase = profileStart(profileIndex, true);
        int profileEnd = 10
            + (profileIndex + 1) * 90 / PROFILES.length;
        int profileRange = Math.max(1, profileEnd - profileBase);
        listener.onProgress(
            Math.min(
                99,
                profileBase
                    + (frameIndex + 1) * profileRange / frameCount
            ),
            "Encoded cached frame "
                + (frameIndex + 1)
                + " of "
                + frameCount
                + "."
        );
    }

    private static int profileStart(
        int profileIndex,
        boolean cached
    ) {
        return cached
            ? 10 + profileIndex * 90 / PROFILES.length
            : profileIndex * 100 / PROFILES.length;
    }

    private static void ensureCacheCapacity(int frameCount)
        throws CacheCapacityException {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long available = Math.max(0L, runtime.maxMemory() - used);
        long required = (
            (long) frameCount * SIZE * SIZE * 4L
        );
        if (required + CACHE_HEAP_RESERVE > available) {
            throw new CacheCapacityException(
                "Not enough free app memory for the single-decode "
                    + "frame cache."
            );
        }
    }

    private static void checkRenderDeadline(long startedAt)
        throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new IOException("Video conversion was cancelled.");
        }
        if (
            SystemClock.elapsedRealtime() - startedAt
                > MAX_RENDER_MS
        ) {
            throw new IOException(
                "Video conversion exceeded the five-minute safety "
                    + "limit on this device."
            );
        }
    }

    private static void requireMultiFrame(Result result)
        throws IOException {
        WebpInspector.Result animation = WebpInspector.inspect(result.data);
        if (!animation.animated || animation.frameCount < 2) {
            throw new NonAnimatedDecodeException(
                "Android decoded only one unique video frame. This device "
                    + "could not expose the Telegram WEBM animation frames."
            );
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
