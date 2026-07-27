package com.tool48.tgwabridge;

import android.graphics.Bitmap;

import java.io.Closeable;
import java.io.IOException;

final class NativeWebpEncoder implements Closeable {
    static {
        System.loadLibrary("tgwa_maker");
    }

    private long handle;

    NativeWebpEncoder(int width, int height, float quality)
        throws IOException {
        handle = nativeCreate(width, height, quality);
        if (handle == 0) {
            throw new IOException(
                "Cannot start the native Animated WebP encoder."
            );
        }
    }

    void addFrame(Bitmap frame, int timestampMs) throws IOException {
        requireOpen();
        if (!nativeAddFrame(handle, frame, timestampMs)) {
            throw new IOException(nativeError(handle));
        }
    }

    byte[] finish(int finalTimestampMs) throws IOException {
        requireOpen();
        byte[] result = nativeFinish(handle, finalTimestampMs);
        if (result == null || result.length == 0) {
            throw new IOException(nativeError(handle));
        }
        return result;
    }

    @Override
    public void close() {
        if (handle != 0) {
            nativeDestroy(handle);
            handle = 0;
        }
    }

    private void requireOpen() {
        if (handle == 0) {
            throw new IllegalStateException("Encoder is already closed.");
        }
    }

    private static native long nativeCreate(
        int width,
        int height,
        float quality
    );

    private static native boolean nativeAddFrame(
        long handle,
        Bitmap frame,
        int timestampMs
    );

    private static native byte[] nativeFinish(
        long handle,
        int finalTimestampMs
    );

    private static native String nativeError(long handle);

    private static native void nativeDestroy(long handle);
}
