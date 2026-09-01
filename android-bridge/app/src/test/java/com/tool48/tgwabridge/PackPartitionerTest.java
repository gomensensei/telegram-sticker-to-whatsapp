package com.tool48.tgwabridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public final class PackPartitionerTest {
    @Test
    public void allowsTelegramOnlySmallParts() {
        assertEquals(Arrays.asList(1), PackPartitioner.sizes(1));
        assertEquals(Arrays.asList(2), PackPartitioner.sizes(2));
    }

    @Test
    public void keepsSinglePackAtThirty() {
        assertEquals(
            Arrays.asList(30),
            PackPartitioner.sizes(30)
        );
    }

    @Test
    public void keepsExistingThirtyAndCreatesTelegramOnlyRemainder() {
        assertEquals(
            Arrays.asList(30, 1),
            PackPartitioner.sizes(31)
        );
    }

    @Test
    public void partitionsLargePacksWithoutMovingEarlierParts() {
        for (int total = 1; total <= 200; total++) {
            List<Integer> parts = PackPartitioner.sizes(total);
            int sum = 0;
            for (int part : parts) {
                assertTrue(part >= 1);
                assertTrue(part <= 30);
                sum += part;
            }
            assertEquals(total, sum);
        }
    }

    @Test
    public void neverRebalancesACompletedPart() {
        assertEquals(
            Arrays.asList(30, 30, 1),
            PackPartitioner.sizes(61)
        );
    }
}
