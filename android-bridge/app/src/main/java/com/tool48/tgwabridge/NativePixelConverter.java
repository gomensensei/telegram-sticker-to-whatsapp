package com.tool48.tgwabridge;

import android.graphics.Bitmap;

import java.nio.ByteBuffer;

final class NativePixelConverter {
    static {
        System.loadLibrary("tgwa_maker");
    }

    private NativePixelConverter() {
    }

    static native boolean bitmapToYuv420(
        Bitmap bitmap,
        byte[] output,
        boolean semiPlanar,
        boolean alphaOnly
    );

    static native boolean yuv420ToBitmap(
        ByteBuffer y,
        int yBase,
        int yLimit,
        int yRowStride,
        int yPixelStride,
        ByteBuffer u,
        int uBase,
        int uLimit,
        int uRowStride,
        int uPixelStride,
        ByteBuffer v,
        int vBase,
        int vLimit,
        int vRowStride,
        int vPixelStride,
        int left,
        int top,
        Bitmap output
    );
}
