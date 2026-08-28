package com.tool48.tgwabridge;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class TelegramAlphaWebmMuxer {
    private static final int EBML = 0x1A45DFA3;
    private static final int SEGMENT = 0x18538067;
    private static final int INFO = 0x1549A966;
    private static final int TRACKS = 0x1654AE6B;
    private static final int CLUSTER = 0x1F43B675;

    private TelegramAlphaWebmMuxer() {
    }

    static void write(
        File output,
        TelegramVp9Encoder.Stream color,
        TelegramVp9Encoder.Stream alpha
    ) throws IOException {
        validate(color, alpha);
        ByteArrayOutputStream document = new ByteArrayOutputStream();
        element(document, EBML, ebmlHeader());

        ByteArrayOutputStream segment = new ByteArrayOutputStream();
        element(segment, INFO, info(color.durationMs));
        element(segment, TRACKS, tracks());
        element(segment, CLUSTER, cluster(color, alpha));
        element(document, SEGMENT, segment.toByteArray());

        try (FileOutputStream file = new FileOutputStream(output)) {
            document.writeTo(file);
        }
    }

    private static void validate(
        TelegramVp9Encoder.Stream color,
        TelegramVp9Encoder.Stream alpha
    ) throws IOException {
        if (
            color == null
            || alpha == null
            || color.packets.size() < 2
            || alpha.packets.size() < 2
            || color.durationMs <= 0
            || color.durationMs > 3_000
        ) {
            throw new IOException("VP9 alpha streams are incomplete.");
        }
        Map<Long, TelegramVp9Encoder.Packet> alphaTimes = new HashMap<>();
        for (TelegramVp9Encoder.Packet packet : alpha.packets) {
            alphaTimes.put(packet.presentationTimeUs, packet);
        }
        for (TelegramVp9Encoder.Packet packet : color.packets) {
            if (!alphaTimes.containsKey(packet.presentationTimeUs)) {
                throw new IOException(
                    "VP9 color and alpha frame timestamps do not match."
                );
            }
        }
    }

    private static byte[] ebmlHeader() throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        uintElement(body, 0x4286, 1);
        uintElement(body, 0x42F7, 1);
        uintElement(body, 0x42F2, 4);
        uintElement(body, 0x42F3, 8);
        stringElement(body, 0x4282, "webm");
        uintElement(body, 0x4287, 4);
        uintElement(body, 0x4285, 2);
        return body.toByteArray();
    }

    private static byte[] info(int durationMs) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        uintElement(body, 0x2AD7B1, 1_000_000);
        stringElement(body, 0x4D80, "TGWA Maker");
        stringElement(body, 0x5741, "TGWA Maker Android");
        element(
            body,
            0x4489,
            ByteBuffer.allocate(8)
                .order(ByteOrder.BIG_ENDIAN)
                .putDouble(durationMs)
                .array()
        );
        return body.toByteArray();
    }

    private static byte[] tracks() throws IOException {
        ByteArrayOutputStream entry = new ByteArrayOutputStream();
        uintElement(entry, 0xD7, 1);
        uintElement(entry, 0x73C5, 1);
        uintElement(entry, 0x83, 1);
        uintElement(entry, 0x9C, 0);
        stringElement(entry, 0x86, "V_VP9");
        stringElement(entry, 0x258688, "VP9 with alpha");

        ByteArrayOutputStream video = new ByteArrayOutputStream();
        uintElement(video, 0xB0, 512);
        uintElement(video, 0xBA, 512);
        uintElement(video, 0x53C0, 1);
        element(entry, 0xE0, video.toByteArray());

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        element(body, 0xAE, entry.toByteArray());
        return body.toByteArray();
    }

    private static byte[] cluster(
        TelegramVp9Encoder.Stream color,
        TelegramVp9Encoder.Stream alpha
    ) throws IOException {
        Map<Long, TelegramVp9Encoder.Packet> alphaTimes = new HashMap<>();
        for (TelegramVp9Encoder.Packet packet : alpha.packets) {
            alphaTimes.put(packet.presentationTimeUs, packet);
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        uintElement(body, 0xE7, 0);
        List<TelegramVp9Encoder.Packet> packets = color.packets;
        for (int index = 0; index < packets.size(); index++) {
            TelegramVp9Encoder.Packet packet = packets.get(index);
            TelegramVp9Encoder.Packet alphaPacket = alphaTimes.get(
                packet.presentationTimeUs
            );
            int timestampMs = (int) Math.max(
                0,
                Math.min(32_767, packet.presentationTimeUs / 1000L)
            );
            int nextTimestampMs = index + 1 < packets.size()
                ? (int) (packets.get(index + 1).presentationTimeUs / 1000L)
                : color.durationMs;
            int durationMs = Math.max(1, nextTimestampMs - timestampMs);

            ByteArrayOutputStream group = new ByteArrayOutputStream();
            ByteArrayOutputStream block = new ByteArrayOutputStream();
            block.write(0x81);
            block.write((timestampMs >>> 8) & 0xff);
            block.write(timestampMs & 0xff);
            block.write(0x00);
            block.write(packet.data);
            element(group, 0xA1, block.toByteArray());
            uintElement(group, 0x9B, durationMs);

            ByteArrayOutputStream more = new ByteArrayOutputStream();
            uintElement(more, 0xEE, 1);
            element(more, 0xA5, alphaPacket.data);
            ByteArrayOutputStream additions = new ByteArrayOutputStream();
            element(additions, 0xA6, more.toByteArray());
            element(group, 0x75A1, additions.toByteArray());

            if (!packet.keyFrame && index > 0) {
                int reference = timestampMs
                    - (int) (packets.get(index - 1).presentationTimeUs / 1000L);
                element(
                    group,
                    0xFB,
                    new byte[] {
                        (byte) ((-reference >>> 8) & 0xff),
                        (byte) (-reference & 0xff)
                    }
                );
            }
            element(body, 0xA0, group.toByteArray());
        }
        return body.toByteArray();
    }

    private static void uintElement(
        ByteArrayOutputStream output,
        int id,
        long value
    ) throws IOException {
        int length = 1;
        while (length < 8 && value >= (1L << (length * 8))) {
            length++;
        }
        byte[] bytes = new byte[length];
        long remaining = value;
        for (int index = length - 1; index >= 0; index--) {
            bytes[index] = (byte) (remaining & 0xff);
            remaining >>>= 8;
        }
        element(output, id, bytes);
    }

    private static void stringElement(
        ByteArrayOutputStream output,
        int id,
        String value
    ) throws IOException {
        element(output, id, value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void element(
        ByteArrayOutputStream output,
        int id,
        byte[] data
    ) throws IOException {
        writeId(output, id);
        writeSize(output, data.length);
        output.write(data);
    }

    private static void writeId(ByteArrayOutputStream output, int id) {
        int length = id > 0x00ffffff
            ? 4
            : (id > 0x0000ffff ? 3 : (id > 0x000000ff ? 2 : 1));
        for (int shift = (length - 1) * 8; shift >= 0; shift -= 8) {
            output.write((id >>> shift) & 0xff);
        }
    }

    private static void writeSize(
        ByteArrayOutputStream output,
        long value
    ) throws IOException {
        int length = 1;
        while (
            length < 8
            && value > ((1L << (length * 7)) - 2L)
        ) {
            length++;
        }
        if (length == 8 && value > 0x00fffffffffffffeL) {
            throw new IOException("WebM element is too large.");
        }
        long encoded = value | (1L << (length * 7));
        for (int shift = (length - 1) * 8; shift >= 0; shift -= 8) {
            output.write((int) ((encoded >>> shift) & 0xff));
        }
    }
}
