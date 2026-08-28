package com.tool48.tgwabridge;

import android.graphics.Bitmap;

import java.io.Closeable;
import java.io.IOException;

final class NativeAnimatedWebpDecoder implements Closeable {
    static {
        System.loadLibrary("tgwa_maker");
    }

    private long handle;

    NativeAnimatedWebpDecoder(byte[] data) throws IOException {
        handle = nativeCreate(data);
        if (handle == 0) {
            throw new IOException("Cannot decode this Animated WebP.");
        }
    }

    int width() {
        return nativeWidth(handle);
    }

    int height() {
        return nativeHeight(handle);
    }

    int frameCount() {
        return nativeFrameCount(handle);
    }

    int nextFrame(Bitmap target) throws IOException {
        int timestamp = nativeNextFrame(handle, target);
        if (timestamp < 0) {
            throw new IOException("Animated WebP frame decode failed.");
        }
        return timestamp;
    }

    void reset() throws IOException {
        if (!nativeReset(handle)) {
            throw new IOException("Cannot reset Animated WebP decoder.");
        }
    }

    @Override
    public void close() {
        if (handle != 0) {
            nativeDestroy(handle);
            handle = 0;
        }
    }

    private static native long nativeCreate(byte[] data);
    private static native int nativeWidth(long handle);
    private static native int nativeHeight(long handle);
    private static native int nativeFrameCount(long handle);
    private static native int nativeNextFrame(long handle, Bitmap target);
    private static native boolean nativeReset(long handle);
    private static native void nativeDestroy(long handle);
}
