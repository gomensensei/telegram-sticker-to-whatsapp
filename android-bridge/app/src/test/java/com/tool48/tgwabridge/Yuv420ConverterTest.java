package com.tool48.tgwabridge;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.nio.ByteBuffer;

public final class Yuv420ConverterTest {
    @Test
    public void readsPaddedYuvPlanesUsingTheirOwnStrides() {
        ByteBuffer y = ByteBuffer.allocate(12);
        ByteBuffer u = ByteBuffer.allocate(6);
        ByteBuffer v = ByteBuffer.allocate(6);
        for (int column = 0; column < 4; column++) {
            y.put(column, (byte) 16);
            y.put(6 + column, (byte) 235);
        }
        u.put(0, (byte) 128);
        u.put(2, (byte) 128);
        v.put(0, (byte) 128);
        v.put(2, (byte) 128);

        int[] pixels = Yuv420Converter.toArgb(
            4,
            2,
            0,
            0,
            y,
            6,
            1,
            u,
            6,
            2,
            v,
            6,
            2
        );

        for (int index = 0; index < 4; index++) {
            assertEquals(0xff000000, pixels[index]);
        }
        for (int index = 4; index < 8; index++) {
            assertEquals(0xffffffff, pixels[index]);
        }
    }

    @Test
    public void honoursBufferPositionAndCropOffset() {
        ByteBuffer y = ByteBuffer.allocate(8);
        ByteBuffer u = ByteBuffer.allocate(4);
        ByteBuffer v = ByteBuffer.allocate(4);
        y.position(2);
        u.position(1);
        v.position(1);
        y.put(3, (byte) 235);
        u.put(1, (byte) 128);
        v.put(1, (byte) 128);

        int[] pixels = Yuv420Converter.toArgb(
            1,
            1,
            1,
            0,
            y,
            4,
            1,
            u,
            2,
            1,
            v,
            2,
            1
        );

        assertEquals(0xffffffff, pixels[0]);
    }
}
