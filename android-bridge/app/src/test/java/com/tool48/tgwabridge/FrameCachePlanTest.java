package com.tool48.tgwabridge;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class FrameCachePlanTest {
    @Test
    public void reusesEvenlySpacedFramesForLowerFpsProfiles() {
        assertEquals(0, FrameCachePlan.sourceIndex(60, 30, 0));
        assertEquals(2, FrameCachePlan.sourceIndex(60, 30, 1));
        assertEquals(58, FrameCachePlan.sourceIndex(60, 30, 29));
    }

    @Test
    public void neverRunsPastLastCachedFrame() {
        assertEquals(0, FrameCachePlan.sourceIndex(2, 60, 0));
        assertEquals(1, FrameCachePlan.sourceIndex(2, 60, 59));
    }
}
