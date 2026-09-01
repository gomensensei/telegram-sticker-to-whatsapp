package com.tool48.tgwabridge;

final class FrameSamplingPlan {
    private FrameSamplingPlan() {
    }

    static int[] sourceIndexes(
        long sourceDurationMs,
        int sourceFrameCount,
        long startMs,
        long clipDurationMs,
        int outputFrameCount
    ) {
        if (
            sourceDurationMs <= 0
            || sourceFrameCount < 2
            || startMs < 0
            || clipDurationMs <= 0
            || outputFrameCount < 2
        ) {
            throw new IllegalArgumentException(
                "Frame sampling metadata is invalid."
            );
        }
        long safeStart = Math.min(
            startMs,
            Math.max(0, sourceDurationMs - 1)
        );
        long safeEnd = Math.min(
            sourceDurationMs,
            safeStart + clipDurationMs
        );
        int first = (int) Math.floor(
            safeStart * sourceFrameCount / (double) sourceDurationMs
        );
        int endExclusive = (int) Math.ceil(
            safeEnd * sourceFrameCount / (double) sourceDurationMs
        );
        first = clamp(first, 0, sourceFrameCount - 1);
        endExclusive = clamp(
            endExclusive,
            first + 1,
            sourceFrameCount
        );
        int available = Math.max(1, endExclusive - first);
        int[] result = new int[outputFrameCount];
        for (int index = 0; index < outputFrameCount; index++) {
            int sourceOffset = (int) Math.floor(
                index * available / (double) outputFrameCount
            );
            result[index] = Math.min(
                endExclusive - 1,
                first + sourceOffset
            );
        }
        return result;
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }
}
