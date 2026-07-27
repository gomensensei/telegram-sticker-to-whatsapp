package com.tool48.tgwabridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public final class PackPartitionerTest {
    @Test
    public void keepsSinglePackAtThirty() {
        assertEquals(
            Arrays.asList(30),
            PackPartitioner.sizes(30)
        );
    }

    @Test
    public void avoidsInvalidOneStickerRemainder() {
        assertEquals(
            Arrays.asList(28, 3),
            PackPartitioner.sizes(31)
        );
    }

    @Test
    public void partitionsLargePacksWithinWhatsappBounds() {
        for (int total = 3; total <= 200; total++) {
            List<Integer> parts = PackPartitioner.sizes(total);
            int sum = 0;
            for (int part : parts) {
                assertTrue(part >= 3);
                assertTrue(part <= 30);
                sum += part;
            }
            assertEquals(total, sum);
        }
    }
}
