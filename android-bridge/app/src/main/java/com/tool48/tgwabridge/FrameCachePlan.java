package com.tool48.tgwabridge;

final class FrameCachePlan {
    private FrameCachePlan() {
    }

    static int sourceIndex(
        int sourceFrameCount,
        int outputFrameCount,
        int outputIndex
    ) {
        if (sourceFrameCount < 1 || outputFrameCount < 1) {
            throw new IllegalArgumentException(
                "Frame counts must be positive."
            );
        }
        if (outputIndex < 0 || outputIndex >= outputFrameCount) {
            throw new IllegalArgumentException(
                "Output frame index is outside the plan."
            );
        }
        return Math.min(
            sourceFrameCount - 1,
            (int) (
                (long) outputIndex
                    * sourceFrameCount
                    / outputFrameCount
            )
        );
    }
}
