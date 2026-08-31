package com.tool48.tgwabridge;

import android.graphics.Bitmap;

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

}
