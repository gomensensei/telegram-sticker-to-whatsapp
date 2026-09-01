package com.tool48.tgwabridge;

final class FrameTimePlan {
    private FrameTimePlan() {
    }

    static long[] sourceTimesUs(
        long startMs,
        long durationMs,
        int outputFrameCount
    ) {
        if (startMs < 0 || durationMs <= 0 || outputFrameCount < 2) {
            throw new IllegalArgumentException(
                "Frame timing settings are invalid."
            );
        }
        long[] result = new long[outputFrameCount];
        for (int index = 0; index < outputFrameCount; index++) {
            long offsetUs = (
                index * durationMs * 1_000L / outputFrameCount
            );
            result[index] = startMs * 1_000L + offsetUs;
        }
        return result;
    }
}
