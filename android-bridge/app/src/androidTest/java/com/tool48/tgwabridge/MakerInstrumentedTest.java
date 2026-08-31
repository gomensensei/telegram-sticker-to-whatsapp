package com.tool48.tgwabridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPOutputStream;

@RunWith(AndroidJUnit4.class)
public final class MakerInstrumentedTest {
    @Test
    public void nativeEncoderProducesRealAnimatedWebp() throws Exception {
        byte[] data;
        try (
            NativeWebpEncoder encoder =
                new NativeWebpEncoder(512, 512, 80)
        ) {
            Bitmap first = square(Color.RED, 80);
            Bitmap second = square(Color.BLUE, 320);
            try {
                encoder.addFrame(first, 0);
                encoder.addFrame(second, 100);
                data = encoder.finish(200);
            } finally {
                first.recycle();
                second.recycle();
            }
        }
        WebpInspector.Result result = WebpInspector.inspect(data);
        assertTrue(result.animated);
        assertTrue(result.frameCount >= 2);
        assertEquals(200, result.durationMs);
        assertTrue(data.length <= 500 * 1024);
        Bitmap decoded = BitmapFactory.decodeByteArray(
            data,
            0,
            data.length
        );
        try {
            assertEquals(0, Color.alpha(decoded.getPixel(0, 0)));
        } finally {
            decoded.recycle();
        }
    }

    @Test
    public void animatedWebpExportsAsMultiFrameTelegramVp9()
        throws Exception {
        byte[] data;
        try (NativeWebpEncoder encoder = new NativeWebpEncoder(512, 512, 70)) {
            Bitmap first = square(Color.RED, 80);
            Bitmap second = square(Color.BLUE, 320);
            try {
                encoder.addFrame(first, 0);
                encoder.addFrame(second, 100);
                data = encoder.finish(200);
            } finally {
                first.recycle();
                second.recycle();
            }
        }
        try (
            NativeAnimatedWebpDecoder decoder =
                new NativeAnimatedWebpDecoder(data)
        ) {
            assertEquals(2, decoder.frameCount());
            Bitmap frame = Bitmap.createBitmap(
                512,
                512,
                Bitmap.Config.ARGB_8888
            );
            try {
                assertEquals(100, decoder.nextFrame(frame));
                assertEquals(200, decoder.nextFrame(frame));
            } finally {
                frame.recycle();
            }
        }

        Context context = InstrumentationRegistry
            .getInstrumentation()
            .getTargetContext();
        File input = new File(context.getCacheDir(), "telegram-input.webp");
        File output = new File(context.getCacheDir(), "telegram-output.webm");
        try {
            try (FileOutputStream stream = new FileOutputStream(input)) {
                stream.write(data);
            }
            TelegramVp9Encoder.encode(input, output, null);
            assertTrue(output.length() > 0);
            assertTrue(output.length() <= 256 * 1024);
            byte[] webm;
            try (FileInputStream stream = new FileInputStream(output)) {
                webm = PackStore.readFully(stream, 256 * 1024);
            }
            assertTrue(contains(webm, new byte[] {0x53, (byte) 0xc0, (byte) 0x81, 0x01}));
            assertTrue(contains(webm, new byte[] {0x75, (byte) 0xa1}));
            MediaExtractor extractor = new MediaExtractor();
            try {
                extractor.setDataSource(output.getAbsolutePath());
                int videoTrack = -1;
                for (int index = 0; index < extractor.getTrackCount(); index++) {
                    MediaFormat format = extractor.getTrackFormat(index);
                    String mime = format.getString(MediaFormat.KEY_MIME);
                    if (mime != null && mime.startsWith("video/")) {
                        videoTrack = index;
                        assertEquals("video/x-vnd.on2.vp9", mime);
                        break;
                    }
                }
                assertTrue(videoTrack >= 0);
                extractor.selectTrack(videoTrack);
                int samples = 0;
                while (extractor.getSampleTime() >= 0) {
                    samples++;
                    extractor.advance();
                }
                assertTrue(samples >= 2);
            } finally {
                extractor.release();
            }
        } finally {
            input.delete();
            output.delete();
        }
    }

    @Test
    public void tgsRendererProducesRealAnimatedWebp() throws Exception {
        byte[] data = TgsStickerRenderer.render(
            gzip(simpleMovingCircleLottie()),
            null
        );
        WebpInspector.Result result = WebpInspector.inspect(data);
        assertTrue(result.animated);
        assertTrue(result.frameCount >= 2);
        assertTrue(data.length <= 500 * 1024);
    }

    @Test
    public void staticRendererProducesValidStaticWebp() throws Exception {
        Bitmap source = Bitmap.createBitmap(
            300,
            180,
            Bitmap.Config.ARGB_8888
        );
        source.eraseColor(Color.GREEN);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        try {
            assertTrue(
                source.compress(Bitmap.CompressFormat.PNG, 100, png)
            );
        } finally {
            source.recycle();
        }
        byte[] data = StaticStickerRenderer.render(png.toByteArray());
        assertFalse(WebpInspector.inspect(data).animated);
        assertTrue(data.length <= 100 * 1024);
    }

    @Test
    public void makerAppendsWithoutReplacingThePackIdentity()
        throws Exception {
        Context context = InstrumentationRegistry
            .getInstrumentation()
            .getTargetContext();
        Bitmap source = Bitmap.createBitmap(
            300,
            180,
            Bitmap.Config.ARGB_8888
        );
        source.eraseColor(Color.MAGENTA);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        try {
            assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, png));
        } finally {
            source.recycle();
        }
        byte[] sticker = StaticStickerRenderer.render(png.toByteArray());
        File queued = new File(context.getCacheDir(), "append-static.webp");
        try (FileOutputStream output = new FileOutputStream(queued)) {
            output.write(sticker);
        }
        Pack first = null;
        try {
            first = MakerPackBuilder.build(
                context,
                Collections.singletonList(queued),
                "Append Test " + System.currentTimeMillis(),
                "ゴメン先生",
                false,
                null
            );
            assertEquals(1, first.stickers.size());
            assertFalse(first.whatsappEligible());
            Pack second = MakerPackBuilder.build(
                context,
                Collections.singletonList(queued),
                first.name,
                first.publisher,
                false,
                first
            );
            assertEquals(first.identifier, second.identifier);
            assertEquals(2, second.stickers.size());
            assertFalse(second.whatsappEligible());
            Pack third = MakerPackBuilder.build(
                context,
                Collections.singletonList(queued),
                second.name,
                second.publisher,
                false,
                second
            );
            assertEquals(first.identifier, third.identifier);
            assertEquals(3, third.stickers.size());
            assertTrue(third.whatsappEligible());
        } finally {
            queued.delete();
            if (first != null) {
                PackStore.delete(context, first.identifier);
            }
        }
    }

    @Test
    public void telegramIncrementOnlyCreatesOrUpdatesTheTailPart()
        throws Exception {
        Context context = InstrumentationRegistry
            .getInstrumentation()
            .getTargetContext();
        Bitmap source = Bitmap.createBitmap(
            256,
            256,
            Bitmap.Config.ARGB_8888
        );
        source.eraseColor(Color.CYAN);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        try {
            assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, png));
        } finally {
            source.recycle();
        }
        byte[] sticker = StaticStickerRenderer.render(png.toByteArray());
        File rendered = new File(
            context.getCacheDir(),
            "increment-static.webp"
        );
        try (FileOutputStream output = new FileOutputStream(rendered)) {
            output.write(sticker);
        }
        String sourceName = "increment_" + System.currentTimeMillis();
        try {
            List<Pack> first = GeneratedPackBuilder.buildSplit(
                context,
                telegramItems(rendered, 30),
                Collections.emptyList(),
                "Increment Test",
                "ゴメン先生",
                sourceName
            );
            assertEquals(1, first.size());
            Pack stable = first.get(0);
            List<Pack> second = GeneratedPackBuilder.buildSplit(
                context,
                telegramItems(rendered, 34),
                Collections.emptyList(),
                "Increment Test",
                "ゴメン先生",
                sourceName
            );
            assertEquals(2, second.size());
            assertEquals(stable.identifier, second.get(0).identifier);
            assertEquals(
                stable.imageDataVersion,
                second.get(0).imageDataVersion
            );
            assertEquals(30, second.get(0).stickers.size());
            assertEquals(4, second.get(1).stickers.size());
            assertTrue(second.get(1).whatsappEligible());
        } finally {
            rendered.delete();
            for (Pack pack : PackStore.findTelegramSource(context, sourceName)) {
                PackStore.delete(context, pack.identifier);
            }
        }
    }

    private static List<GeneratedPackBuilder.Item> telegramItems(
        File rendered,
        int count
    ) {
        List<GeneratedPackBuilder.Item> items = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            items.add(
                new GeneratedPackBuilder.Item(
                    rendered,
                    Collections.singletonList("✨"),
                    "Telegram sticker " + (index + 1),
                    "unique-" + index,
                    null,
                    "static"
                )
            );
        }
        return items;
    }

    @Test
    public void botTokenRoundTripsThroughAndroidKeystore()
        throws Exception {
        Context context = InstrumentationRegistry
            .getInstrumentation()
            .getTargetContext();
        String token = "123456789:"
            + "abcdefghijklmnopqrstuvwxyz"
            + "_ABC123";
        try {
            BotTokenStore.save(context, token);
            assertEquals(token, BotTokenStore.load(context));
        } finally {
            BotTokenStore.clear(context);
        }
    }

    private static Bitmap square(int color, int left) {
        Bitmap bitmap = Bitmap.createBitmap(
            512,
            512,
            Bitmap.Config.ARGB_8888
        );
        bitmap.eraseColor(Color.TRANSPARENT);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        canvas.drawRect(left, 206, left + 100, 306, paint);
        return bitmap;
    }

    private static byte[] gzip(String value) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(value.getBytes(StandardCharsets.UTF_8));
        }
        return output.toByteArray();
    }

    private static boolean contains(byte[] source, byte[] needle) {
        for (int index = 0; index <= source.length - needle.length; index++) {
            boolean matches = true;
            for (int offset = 0; offset < needle.length; offset++) {
                if (source[index + offset] != needle[offset]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return true;
            }
        }
        return false;
    }

    private static String simpleMovingCircleLottie() {
        return "{"
            + "\"v\":\"5.7.4\",\"fr\":30,\"ip\":0,\"op\":30,"
            + "\"w\":512,\"h\":512,\"nm\":\"moving-dot\","
            + "\"ddd\":0,\"assets\":[],\"layers\":[{"
            + "\"ddd\":0,\"ind\":1,\"ty\":4,\"nm\":\"dot\",\"sr\":1,"
            + "\"ks\":{"
            + "\"o\":{\"a\":0,\"k\":100},"
            + "\"r\":{\"a\":0,\"k\":0},"
            + "\"p\":{\"a\":1,\"k\":["
            + "{\"t\":0,\"s\":[64,256,0],\"e\":[448,256,0]},"
            + "{\"t\":30,\"s\":[448,256,0]}]},"
            + "\"a\":{\"a\":0,\"k\":[0,0,0]},"
            + "\"s\":{\"a\":0,\"k\":[100,100,100]}},"
            + "\"ao\":0,\"shapes\":["
            + "{\"ty\":\"el\",\"p\":{\"a\":0,\"k\":[0,0]},"
            + "\"s\":{\"a\":0,\"k\":[100,100]},\"nm\":\"Ellipse\"},"
            + "{\"ty\":\"fl\",\"c\":{\"a\":0,\"k\":[1,0,0,1]},"
            + "\"o\":{\"a\":0,\"k\":100},\"r\":1,\"nm\":\"Fill\"}],"
            + "\"ip\":0,\"op\":30,\"st\":0,\"bm\":0}]}";
    }
}
