package com.tool48.tgwabridge;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class FrameTimePlanTest {
    @Test
    public void producesMonotonicPresentationTargets() {
        long[] times = FrameTimePlan.sourceTimesUs(
            0,
            2_033,
            41
        );
        assertTrue(times.length == 41);
        assertTrue(times[0] == 0);
        assertTrue(times[times.length - 1] < 2_033_000L);
        for (int index = 1; index < times.length; index++) {
            assertTrue(times[index] > times[index - 1]);
        }
    }

    @Test
    public void respectsTrimStartAndDuration() {
        assertArrayEquals(
            new long[] {
                1_000_000L,
                1_250_000L,
                1_500_000L,
                1_750_000L
            },
            FrameTimePlan.sourceTimesUs(1_000, 1_000, 4)
        );
    }
}
