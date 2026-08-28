package com.tool48.tgwabridge;

import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

final class TelegramVp9Encoder {
    interface ProgressListener {
        void onProgress(int percent);
    }

    static final class Packet {
        final byte[] data;
        final long presentationTimeUs;
        final boolean keyFrame;

        Packet(byte[] data, long presentationTimeUs, boolean keyFrame) {
            this.data = data;
            this.presentationTimeUs = presentationTimeUs;
            this.keyFrame = keyFrame;
        }
    }

    static final class Stream {
        final List<Packet> packets;
        final int durationMs;

        Stream(List<Packet> packets, int durationMs) {
            this.packets = packets;
            this.durationMs = durationMs;
        }
    }

    private static final class RawEncoder implements AutoCloseable {
        private final MediaCodec codec;
        private final MediaCodec.BufferInfo info =
            new MediaCodec.BufferInfo();
        private final List<Packet> packets = new ArrayList<>();
        private boolean closed;

        RawEncoder(
            int width,
            int height,
            int bitrate,
            int colorFormat
        ) throws IOException {
            codec = MediaCodec.createEncoderByType(MIME);
            MediaFormat format = MediaFormat.createVideoFormat(
                MIME,
                width,
                height
            );
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            codec.configure(
                format,
                null,
                null,
                MediaCodec.CONFIGURE_FLAG_ENCODE
            );
            codec.start();
        }

        void queue(byte[] data, long ptsUs, int flags) throws IOException {
            for (int retry = 0; retry < 200; retry++) {
                int index = codec.dequeueInputBuffer(10_000);
                if (index < 0) {
                    drain(false);
                    continue;
                }
                ByteBuffer input = codec.getInputBuffer(index);
                if (input == null || input.capacity() < data.length) {
                    throw new IOException(
                        "VP9 encoder input buffer is too small."
                    );
                }
                input.clear();
                input.put(data);
                codec.queueInputBuffer(
                    index,
                    0,
                    data.length,
                    ptsUs,
                    flags
                );
                drain(false);
                return;
            }
            throw new IOException("VP9 encoder stopped accepting frames.");
        }

        List<Packet> finish(long ptsUs) throws IOException {
            queue(new byte[0], ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            drain(true);
            if (packets.size() < 2) {
                throw new IOException(
                    "VP9 encoder returned fewer than two video frames."
                );
            }
            return new ArrayList<>(packets);
        }

        private void drain(boolean waitForEnd) throws IOException {
            int idle = 0;
            int maximumIdle = waitForEnd ? 500 : 1;
            while (idle < maximumIdle) {
                int index = codec.dequeueOutputBuffer(info, 10_000);
                if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    idle++;
                    continue;
                }
                idle = 0;
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    continue;
                }
                if (index < 0) {
                    continue;
                }
                ByteBuffer output = codec.getOutputBuffer(index);
                if (
                    output != null
                    && info.size > 0
                    && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                ) {
                    byte[] bytes = new byte[info.size];
                    output.position(info.offset);
                    output.limit(info.offset + info.size);
                    output.get(bytes);
                    packets.add(
                        new Packet(
                            bytes,
                            info.presentationTimeUs,
                            (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                        )
                    );
                }
                boolean end = (
                    info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM
                ) != 0;
                codec.releaseOutputBuffer(index, false);
                if (end) {
                    return;
                }
                if (!waitForEnd) {
                    return;
                }
            }
            if (waitForEnd) {
                throw new IOException(
                    "VP9 encoder did not finish the animation."
                );
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                codec.stop();
            } catch (RuntimeException ignored) {
                // Keep the original encoder result.
            }
            codec.release();
        }
    }

    private static final String MIME = "video/x-vnd.on2.vp9";
    private static final int TELEGRAM_LIMIT = 256 * 1024;
    private static final int[] COLOR_BITRATES = {
        430_000,
        320_000,
        230_000,
        160_000
    };
    private static final int[] ALPHA_BITRATES = {
        130_000,
        95_000,
        68_000,
        48_000
    };

    private TelegramVp9Encoder() {
    }

    static void encode(
        File animatedWebp,
        File output,
        ProgressListener listener
    ) throws IOException {
        byte[] source;
        try (FileInputStream input = new FileInputStream(animatedWebp)) {
            source = PackStore.readFully(input, 600 * 1024);
        }
        WebpInspector.Result inspection = WebpInspector.inspect(source);
        if (!inspection.animated) {
            throw new IOException(
                "Telegram export needs a real multi-frame animation."
            );
        }
        IOException last = null;
        for (int attempt = 0; attempt < COLOR_BITRATES.length; attempt++) {
            final int attemptIndex = attempt;
            File candidate = new File(
                output.getParentFile(),
                output.getName() + ".attempt"
            );
            if (candidate.exists() && !candidate.delete()) {
                throw new IOException("Cannot replace Telegram video attempt.");
            }
            try {
                Stream color = encodeStream(
                    source,
                    COLOR_BITRATES[attempt],
                    false,
                    progress -> {
                        if (listener != null) {
                            int base = attemptIndex * 100
                                / COLOR_BITRATES.length;
                            int span = 100 / COLOR_BITRATES.length;
                            listener.onProgress(
                                Math.min(99, base + progress * span / 200)
                            );
                        }
                    }
                );
                Stream alpha = encodeStream(
                    source,
                    ALPHA_BITRATES[attempt],
                    true,
                    progress -> {
                        if (listener != null) {
                            int base = attemptIndex * 100
                                / COLOR_BITRATES.length;
                            int span = 100 / COLOR_BITRATES.length;
                            listener.onProgress(
                                Math.min(
                                    99,
                                    base + span / 2 + progress * span / 200
                                )
                            );
                        }
                    }
                );
                TelegramAlphaWebmMuxer.write(candidate, color, alpha);
                if (candidate.length() <= TELEGRAM_LIMIT) {
                    if (output.exists() && !output.delete()) {
                        throw new IOException(
                            "Cannot replace Telegram video sticker."
                        );
                    }
                    if (!candidate.renameTo(output)) {
                        throw new IOException(
                            "Cannot finish Telegram video sticker."
                        );
                    }
                    if (listener != null) {
                        listener.onProgress(100);
                    }
                    return;
                }
                last = new IOException(
                    "Telegram alpha video sticker is still over 256 KB."
                );
            } catch (IOException error) {
                last = error;
            } catch (RuntimeException error) {
                last = new IOException(
                    "This phone could not encode a Telegram VP9 sticker: "
                        + friendly(error),
                    error
                );
            } finally {
                if (candidate.exists()) {
                    candidate.delete();
                }
            }
        }
        throw last == null
            ? new IOException("Cannot create a Telegram video sticker.")
            : last;
    }

    private static Stream encodeStream(
        byte[] source,
        int bitrate,
        boolean alphaOnly,
        ProgressListener listener
    ) throws IOException {
        try (
            NativeAnimatedWebpDecoder decoder =
                new NativeAnimatedWebpDecoder(source)
        ) {
            int width = decoder.width();
            int height = decoder.height();
            if (width != 512 || height != 512) {
                throw new IOException(
                    "Telegram video export expects a 512 x 512 sticker."
                );
            }
            MediaCodecInfo.CodecCapabilities capabilities;
            MediaCodec probe = MediaCodec.createEncoderByType(MIME);
            try {
                capabilities = probe.getCodecInfo().getCapabilitiesForType(
                    MIME
                );
            } finally {
                probe.release();
            }
            int colorFormat = chooseColorFormat(capabilities);
            boolean semiPlanar = colorFormat
                == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
            Bitmap frame = Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
            );
            byte[] yuv = new byte[width * height * 3 / 2];
            int previousEndMs = 0;
            int lastQueuedMs = -1000;
            int queued = 0;
            try (
                RawEncoder encoder = new RawEncoder(
                    width,
                    height,
                    bitrate,
                    colorFormat
                )
            ) {
                for (int index = 0; index < decoder.frameCount(); index++) {
                    int endMs = decoder.nextFrame(frame);
                    int startMs = previousEndMs;
                    previousEndMs = endMs;
                    if (startMs >= 3_000) {
                        break;
                    }
                    if (queued > 0 && startMs - lastQueuedMs < 33) {
                        continue;
                    }
                    if (!NativePixelConverter.bitmapToYuv420(
                        frame,
                        yuv,
                        semiPlanar,
                        alphaOnly
                    )) {
                        throw new IOException(
                            "Cannot convert an animation frame to VP9."
                        );
                    }
                    encoder.queue(yuv, startMs * 1000L, 0);
                    lastQueuedMs = startMs;
                    queued++;
                    if (listener != null) {
                        listener.onProgress(
                            Math.min(
                                95,
                                (index + 1) * 95 / decoder.frameCount()
                            )
                        );
                    }
                }
                if (queued < 2) {
                    throw new IOException(
                        "Telegram export decoded fewer than two frames."
                    );
                }
                int durationMs = Math.max(
                    lastQueuedMs + 33,
                    Math.min(3_000, previousEndMs)
                );
                List<Packet> packets = encoder.finish(durationMs * 1000L);
                if (listener != null) {
                    listener.onProgress(100);
                }
                return new Stream(packets, durationMs);
            } finally {
                frame.recycle();
            }
        }
    }

    private static int chooseColorFormat(
        MediaCodecInfo.CodecCapabilities capabilities
    ) throws IOException {
        for (int value : capabilities.colorFormats) {
            if (
                value
                    == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
                || value
                    == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            ) {
                return value;
            }
        }
        for (int value : capabilities.colorFormats) {
            if (
                value
                    == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
            ) {
                return value;
            }
        }
        throw new IOException(
            "This phone has no compatible VP9 YUV encoder format."
        );
    }

    private static String friendly(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
            ? error.getClass().getSimpleName()
            : message.trim();
    }
}
