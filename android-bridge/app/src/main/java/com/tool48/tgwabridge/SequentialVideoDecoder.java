package com.tool48.tgwabridge;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.SystemClock;

import java.io.IOException;
import java.nio.ByteBuffer;

final class SequentialVideoDecoder {
    interface FrameConsumer {
        void onFrame(Bitmap frame, int targetIndex) throws IOException;
    }

    private static final long DEQUEUE_TIMEOUT_US = 10_000L;
    private static final int MAX_IDLE_ROUNDS = 1_000;
    private static final long MAX_DECODE_MS = 2L * 60L * 1_000L;

    private SequentialVideoDecoder() {
    }

    static void decode(
        Context context,
        Uri uri,
        long[] targetTimesUs,
        FrameConsumer consumer
    ) throws IOException {
        if (targetTimesUs == null || targetTimesUs.length < 2) {
            throw new IOException("At least two output frames are required.");
        }
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        boolean decoderStarted = false;
        try {
            extractor.setDataSource(context, uri, null);
            int trackIndex = findVideoTrack(extractor);
            if (trackIndex < 0) {
                throw new IOException(
                    "The selected file does not contain a video track."
                );
            }
            extractor.selectTrack(trackIndex);
            MediaFormat format = extractor.getTrackFormat(trackIndex);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime == null || !mime.startsWith("video/")) {
                throw new IOException(
                    "Android could not identify the video codec."
                );
            }
            int rotation = readRotation(format);
            format.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities
                    .COLOR_FormatYUV420Flexible
            );
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(format, null, null, 0);
            decoder.start();
            decoderStarted = true;
            drain(
                extractor,
                decoder,
                rotation,
                targetTimesUs,
                consumer
            );
        } catch (IOException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new IOException(
                "Android sequential video decoder failed: "
                    + friendly(error),
                error
            );
        } finally {
            if (decoder != null) {
                if (decoderStarted) {
                    try {
                        decoder.stop();
                    } catch (RuntimeException ignored) {
                        // Release below is still required after codec failure.
                    }
                }
                decoder.release();
            }
            extractor.release();
        }
    }

    private static void drain(
        MediaExtractor extractor,
        MediaCodec decoder,
        int rotation,
        long[] targetTimesUs,
        FrameConsumer consumer
    ) throws IOException {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean inputEnded = false;
        boolean outputEnded = false;
        int targetIndex = 0;
        int idleRounds = 0;
        long startedAt = SystemClock.elapsedRealtime();
        while (!outputEnded && targetIndex < targetTimesUs.length) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IOException(
                    "Android video decoding was cancelled."
                );
            }
            if (
                SystemClock.elapsedRealtime() - startedAt
                    > MAX_DECODE_MS
            ) {
                throw new IOException(
                    "Android video decoding exceeded the two-minute "
                        + "safety limit."
                );
            }
            boolean advanced = false;
            if (!inputEnded) {
                int inputIndex = decoder.dequeueInputBuffer(
                    DEQUEUE_TIMEOUT_US
                );
                if (inputIndex >= 0) {
                    ByteBuffer input = decoder.getInputBuffer(inputIndex);
                    if (input == null) {
                        throw new IOException(
                            "Android decoder returned no input buffer."
                        );
                    }
                    input.clear();
                    int sampleSize = extractor.readSampleData(input, 0);
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        );
                        inputEnded = true;
                    } else {
                        decoder.queueInputBuffer(
                            inputIndex,
                            0,
                            sampleSize,
                            extractor.getSampleTime(),
                            0
                        );
                        extractor.advance();
                    }
                    advanced = true;
                }
            }

            int outputIndex = decoder.dequeueOutputBuffer(
                info,
                DEQUEUE_TIMEOUT_US
            );
            if (outputIndex >= 0) {
                try {
                    boolean codecConfig = (
                        info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                    ) != 0;
                    if (
                        !codecConfig
                        && targetIndex < targetTimesUs.length
                        && info.presentationTimeUs
                            >= targetTimesUs[targetIndex]
                    ) {
                        Image image = decoder.getOutputImage(outputIndex);
                        if (image == null) {
                            throw new IOException(
                                "Android decoder did not expose its raw "
                                    + "video frame."
                            );
                        }
                        Bitmap decoded = null;
                        Bitmap source = null;
                        try {
                            decoded = Yuv420Converter.toBitmap(image);
                            source = rotate(decoded, rotation);
                            while (
                                targetIndex < targetTimesUs.length
                                && info.presentationTimeUs
                                    >= targetTimesUs[targetIndex]
                            ) {
                                consumer.onFrame(source, targetIndex);
                                targetIndex++;
                            }
                        } finally {
                            image.close();
                            if (
                                source != null
                                && source != decoded
                                && !source.isRecycled()
                            ) {
                                source.recycle();
                            }
                            if (
                                decoded != null
                                && !decoded.isRecycled()
                            ) {
                                decoded.recycle();
                            }
                        }
                    }
                } finally {
                    decoder.releaseOutputBuffer(outputIndex, false);
                }
                outputEnded = (
                    info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM
                ) != 0;
                advanced = true;
            } else if (
                outputIndex != MediaCodec.INFO_TRY_AGAIN_LATER
                && outputIndex != MediaCodec.INFO_OUTPUT_FORMAT_CHANGED
                && outputIndex != MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED
            ) {
                throw new IOException(
                    "Android decoder returned an unknown output state."
                );
            } else if (
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED
            ) {
                advanced = true;
            }

            if (advanced) {
                idleRounds = 0;
            } else {
                idleRounds++;
                if (idleRounds >= MAX_IDLE_ROUNDS) {
                    throw new IOException(
                        "Android video decoder stopped making progress."
                    );
                }
            }
        }
        if (targetIndex < targetTimesUs.length) {
            throw new IOException(
                "Android sequential decoder produced only "
                    + targetIndex
                    + " of "
                    + targetTimesUs.length
                    + " required frames."
            );
        }
    }

    private static int findVideoTrack(MediaExtractor extractor) {
        for (int index = 0; index < extractor.getTrackCount(); index++) {
            MediaFormat format = extractor.getTrackFormat(index);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("video/")) {
                return index;
            }
        }
        return -1;
    }

    private static int readRotation(MediaFormat format) {
        if (!format.containsKey(MediaFormat.KEY_ROTATION)) {
            return 0;
        }
        int value = format.getInteger(MediaFormat.KEY_ROTATION) % 360;
        return value < 0 ? value + 360 : value;
    }

    private static Bitmap rotate(Bitmap source, int degrees) {
        if (degrees == 0) {
            return source;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(
            source,
            0,
            0,
            source.getWidth(),
            source.getHeight(),
            matrix,
            true
        );
    }

    private static String friendly(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
            ? error.getClass().getSimpleName()
            : error.getClass().getSimpleName() + ": " + message.trim();
    }
}
