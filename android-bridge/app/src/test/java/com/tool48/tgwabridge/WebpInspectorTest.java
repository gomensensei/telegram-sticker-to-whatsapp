package com.tool48.tgwabridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class WebpInspectorTest {
    @Test
    public void detectsStaticWebp() throws Exception {
        WebpInspector.Result result = WebpInspector.inspect(
            webp(chunk("VP8 ", new byte[0]))
        );
        assertFalse(result.animated);
        assertEquals(0, result.frameCount);
    }

    @Test
    public void requiresMultipleRealAnimationFrames() throws Exception {
        WebpInspector.Result result = WebpInspector.inspect(
            animatedWebp(80, 90)
        );
        assertTrue(result.animated);
        assertEquals(2, result.frameCount);
        assertEquals(170, result.durationMs);
        assertEquals(80, result.minimumFrameDurationMs);
    }

    @Test
    public void rejectsFrameBelowEightMilliseconds() throws Exception {
        assertRejected(animatedWebp(7, 80), "at least 8 ms");
    }

    @Test
    public void rejectsAnimationLongerThanTenSeconds() throws Exception {
        assertRejected(animatedWebp(5_001, 5_001), "10 seconds");
    }

    @Test
    public void rejectsAnimHeaderWithoutTwoFrames() throws Exception {
        assertRejected(animatedWebp(80), "at least 2 frames");
    }

    private static void assertRejected(byte[] data, String message)
        throws Exception {
        try {
            WebpInspector.inspect(data);
            fail("Expected WebP validation to fail.");
        } catch (IOException error) {
            assertTrue(error.getMessage().contains(message));
        }
    }

    private static byte[] animatedWebp(int... durations)
        throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(chunk("ANIM", new byte[6]));
        for (int duration : durations) {
            byte[] frame = new byte[16];
            frame[12] = (byte) (duration & 0xff);
            frame[13] = (byte) ((duration >>> 8) & 0xff);
            frame[14] = (byte) ((duration >>> 16) & 0xff);
            body.write(chunk("ANMF", frame));
        }
        return webp(body.toByteArray());
    }

    private static byte[] webp(byte[] chunks) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(ascii("RIFF"));
        writeLe32(result, chunks.length + 4);
        result.write(ascii("WEBP"));
        result.write(chunks);
        return result.toByteArray();
    }

    private static byte[] chunk(String type, byte[] payload)
        throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(ascii(type));
        writeLe32(result, payload.length);
        result.write(payload);
        if ((payload.length & 1) != 0) {
            result.write(0);
        }
        return result.toByteArray();
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static void writeLe32(ByteArrayOutputStream output, int value) {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write((value >>> 16) & 0xff);
        output.write((value >>> 24) & 0xff);
    }
}
