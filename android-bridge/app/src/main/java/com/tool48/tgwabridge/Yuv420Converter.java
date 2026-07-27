package com.tool48.tgwabridge;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.Image;

import java.io.IOException;
import java.nio.ByteBuffer;

final class Yuv420Converter {
    private Yuv420Converter() {
    }

    static Bitmap toBitmap(Image image) throws IOException {
        Image.Plane[] planes = image.getPlanes();
        if (planes == null || planes.length < 3) {
            throw new IOException(
                "Android decoder did not return a YUV video frame."
            );
        }
        Rect crop = image.getCropRect();
        int width = crop.width();
        int height = crop.height();
        if (width <= 0 || height <= 0) {
            throw new IOException(
                "Android decoder returned an empty video frame."
            );
        }
        int[] pixels;
        try {
            pixels = toArgb(
                width,
                height,
                crop.left,
                crop.top,
                planes[0].getBuffer(),
                planes[0].getRowStride(),
                planes[0].getPixelStride(),
                planes[1].getBuffer(),
                planes[1].getRowStride(),
                planes[1].getPixelStride(),
                planes[2].getBuffer(),
                planes[2].getRowStride(),
                planes[2].getPixelStride()
            );
        } catch (RuntimeException error) {
            throw new IOException(
                "Android returned an unsupported YUV frame layout.",
                error
            );
        }
        return Bitmap.createBitmap(
            pixels,
            width,
            height,
            Bitmap.Config.ARGB_8888
        );
    }

    static int[] toArgb(
        int width,
        int height,
        int cropLeft,
        int cropTop,
        ByteBuffer yBuffer,
        int yRowStride,
        int yPixelStride,
        ByteBuffer uBuffer,
        int uRowStride,
        int uPixelStride,
        ByteBuffer vBuffer,
        int vRowStride,
        int vPixelStride
    ) {
        if (
            width <= 0
            || height <= 0
            || cropLeft < 0
            || cropTop < 0
            || yPixelStride <= 0
            || uPixelStride <= 0
            || vPixelStride <= 0
        ) {
            throw new IllegalArgumentException(
                "YUV frame dimensions or strides are invalid."
            );
        }
        ByteBuffer y = yBuffer.duplicate();
        ByteBuffer u = uBuffer.duplicate();
        ByteBuffer v = vBuffer.duplicate();
        int yBase = y.position();
        int uBase = u.position();
        int vBase = v.position();
        int[] pixels = new int[width * height];
        for (int row = 0; row < height; row++) {
            int sourceY = cropTop + row;
            int yRow = yBase + sourceY * yRowStride;
            int uvRow = (cropTop + row) / 2;
            int uRow = uBase + uvRow * uRowStride;
            int vRow = vBase + uvRow * vRowStride;
            for (int column = 0; column < width; column++) {
                int sourceX = cropLeft + column;
                int yValue = (
                    y.get(yRow + sourceX * yPixelStride) & 0xff
                );
                int uvColumn = sourceX / 2;
                int uValue = (
                    u.get(uRow + uvColumn * uPixelStride) & 0xff
                );
                int vValue = (
                    v.get(vRow + uvColumn * vPixelStride) & 0xff
                );
                pixels[row * width + column] = toColor(
                    yValue,
                    uValue,
                    vValue
                );
            }
        }
        return pixels;
    }

    private static int toColor(int y, int u, int v) {
        int luminance = Math.max(0, y - 16);
        int blueDifference = u - 128;
        int redDifference = v - 128;
        int red = (
            298 * luminance + 409 * redDifference + 128
        ) >> 8;
        int green = (
            298 * luminance
                - 100 * blueDifference
                - 208 * redDifference
                + 128
        ) >> 8;
        int blue = (
            298 * luminance + 516 * blueDifference + 128
        ) >> 8;
        return 0xff000000
            | clamp(red) << 16
            | clamp(green) << 8
            | clamp(blue);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
