package com.tool48.tgwabridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class FrameSamplingPlanTest {
    @Test
    public void samplesAcrossSingleKeyframeTelegramWebm() {
        int[] indexes = FrameSamplingPlan.sourceIndexes(
            2_033,
            61,
            0,
            2_033,
            41
        );
        assertEquals(41, indexes.length);
        assertEquals(0, indexes[0]);
        assertTrue(indexes[indexes.length - 1] >= 59);
        for (int index = 1; index < indexes.length; index++) {
            assertTrue(indexes[index] >= indexes[index - 1]);
        }
    }

    @Test
    public void respectsTrimmedClipRange() {
        int[] indexes = FrameSamplingPlan.sourceIndexes(
            3_000,
            90,
            1_000,
            1_000,
            20
        );
        assertEquals(20, indexes.length);
        assertEquals(30, indexes[0]);
        assertTrue(indexes[indexes.length - 1] < 60);
    }
}
