package com.tool48.tgwabridge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class WebpInspector {
    static final class Result {
        final boolean animated;
        final int frameCount;
        final long durationMs;
        final int minimumFrameDurationMs;

        Result(
            boolean animated,
            int frameCount,
            long durationMs,
            int minimumFrameDurationMs
        ) {
            this.animated = animated;
            this.frameCount = frameCount;
            this.durationMs = durationMs;
            this.minimumFrameDurationMs = minimumFrameDurationMs;
        }
    }

    private WebpInspector() {
    }

    static Result inspect(byte[] data) throws IOException {
        if (
            data == null
            || data.length < 20
            || !fourCc(data, 0).equals("RIFF")
            || !fourCc(data, 8).equals("WEBP")
        ) {
            throw new IOException("A sticker is not a valid WebP file.");
        }
        boolean animationChunk = false;
        int frameCount = 0;
        long durationMs = 0;
        int minimumDuration = Integer.MAX_VALUE;
        int offset = 12;
        while (offset + 8 <= data.length) {
            String type = fourCc(data, offset);
            long chunkSizeLong = unsignedLittleEndian32(data, offset + 4);
            if (chunkSizeLong > Integer.MAX_VALUE) {
                throw new IOException("A WebP chunk is too large.");
            }
            int chunkSize = (int) chunkSizeLong;
            int payload = offset + 8;
            long endLong = (long) payload + chunkSize;
            if (endLong > data.length) {
                throw new IOException("A WebP chunk is truncated.");
            }
            if (type.equals("ANIM")) {
                animationChunk = true;
            } else if (type.equals("ANMF")) {
                if (chunkSize < 16) {
                    throw new IOException("An animated WebP frame is invalid.");
                }
                frameCount++;
                int frameDuration = unsignedLittleEndian24(
                    data,
                    payload + 12
                );
                durationMs += frameDuration;
                minimumDuration = Math.min(
                    minimumDuration,
                    frameDuration
                );
            }
            offset = (int) endLong + (chunkSize & 1);
        }
        boolean animated = animationChunk || frameCount > 0;
        if (animated) {
            if (!animationChunk || frameCount < 2) {
                throw new IOException(
                    "Animated WebP must contain ANIM and at least 2 frames."
                );
            }
            if (minimumDuration < 8) {
                throw new IOException(
                    "Every animated WebP frame must last at least 8 ms."
                );
            }
            if (durationMs > 10_000) {
                throw new IOException(
                    "Animated WebP duration must not exceed 10 seconds."
                );
            }
        }
        return new Result(
            animated,
            frameCount,
            durationMs,
            minimumDuration == Integer.MAX_VALUE ? 0 : minimumDuration
        );
    }

    private static String fourCc(byte[] data, int offset)
        throws IOException {
        if (offset < 0 || offset + 4 > data.length) {
            throw new IOException("A WebP FourCC is truncated.");
        }
        return new String(
            data,
            offset,
            4,
            StandardCharsets.US_ASCII
        );
    }

    private static long unsignedLittleEndian32(byte[] data, int offset)
        throws IOException {
        if (offset < 0 || offset + 4 > data.length) {
            throw new IOException("A WebP size field is truncated.");
        }
        return ((long) data[offset] & 0xff)
            | (((long) data[offset + 1] & 0xff) << 8)
            | (((long) data[offset + 2] & 0xff) << 16)
            | (((long) data[offset + 3] & 0xff) << 24);
    }

    private static int unsignedLittleEndian24(byte[] data, int offset)
        throws IOException {
        if (offset < 0 || offset + 3 > data.length) {
            throw new IOException("A WebP duration field is truncated.");
        }
        return (data[offset] & 0xff)
            | ((data[offset + 1] & 0xff) << 8)
            | ((data[offset + 2] & 0xff) << 16);
    }
}
