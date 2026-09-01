package com.tool48.tgwabridge;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.Image;

import java.io.IOException;
import java.nio.ByteBuffer;

final class Yuv420Converter {
    private static final class PlaneData {
        final ByteBuffer buffer;
        final int rowStride;
        final int pixelStride;

        PlaneData(
            ByteBuffer buffer,
            int rowStride,
            int pixelStride
        ) {
            this.buffer = buffer;
            this.rowStride = rowStride;
            this.pixelStride = pixelStride;
        }
    }

    private static final class PlaneReader {
        final ByteBuffer buffer;
        final int base;
        final int limit;
        final int rowStride;
        final int pixelStride;

        PlaneReader(
            ByteBuffer source,
            int rowStride,
            int pixelStride,
            boolean useBufferPosition
        ) {
            if (
                source == null
                || rowStride <= 0
                || pixelStride <= 0
            ) {
                throw new IllegalArgumentException(
                    "YUV plane buffer or strides are invalid."
                );
            }
            buffer = source.duplicate();
            base = useBufferPosition ? buffer.position() : 0;
            limit = buffer.limit();
            this.rowStride = rowStride;
            this.pixelStride = pixelStride;
            if (base < 0 || base >= limit) {
                throw new IllegalArgumentException(
                    "YUV plane contains no readable samples."
                );
            }
        }

        long overflow(int row, int column) {
            long index = base
                + (long) Math.max(0, row) * rowStride
                + (long) Math.max(0, column) * pixelStride;
            return Math.max(0L, index - (limit - 1L));
        }

        int sample(int row, int column) {
            int safeRow = Math.max(0, row);
            int maxRow = Math.max(
                0,
                (limit - 1 - base) / rowStride
            );
            safeRow = Math.min(safeRow, maxRow);
            int rowStart = base + safeRow * rowStride;
            int maxColumn = Math.max(
                0,
                (limit - 1 - rowStart) / pixelStride
            );
            int safeColumn = Math.min(
                Math.max(0, column),
                maxColumn
            );
            return (
                buffer.get(
                    rowStart + safeColumn * pixelStride
                ) & 0xff
            );
        }
    }

    private static final class Layout {
        final PlaneReader y;
        final PlaneReader u;
        final PlaneReader v;
        final int left;
        final int top;
        final long overflow;

        Layout(
            PlaneData[] planes,
            int left,
            int top,
            int width,
            int height,
            boolean useBufferPosition
        ) {
            y = reader(planes[0], useBufferPosition);
            u = reader(planes[1], useBufferPosition);
            v = reader(planes[2], useBufferPosition);
            this.left = left;
            this.top = top;
            int lastX = left + width - 1;
            int lastY = top + height - 1;
            overflow = y.overflow(lastY, lastX)
                + u.overflow(lastY / 2, lastX / 2)
                + v.overflow(lastY / 2, lastX / 2);
        }

        private static PlaneReader reader(
            PlaneData plane,
            boolean useBufferPosition
        ) {
            return new PlaneReader(
                plane.buffer,
                plane.rowStride,
                plane.pixelStride,
                useBufferPosition
            );
        }
    }

    private Yuv420Converter() {
    }

    static Bitmap toBitmap(Image image) throws IOException {
        try {
            Image.Plane[] planes = image.getPlanes();
            if (planes == null || planes.length < 3) {
                throw new IOException(
                    "Android decoder did not return three YUV planes. "
                        + describe(image)
                );
            }
            Rect crop = image.getCropRect();
            int width = crop.width();
            int height = crop.height();
            if (width <= 0 || height <= 0) {
                throw new IOException(
                    "Android decoder returned an empty video frame. "
                    + describe(image)
                );
            }
            if (isTenBitP010(image, planes)) {
                throw new IOException(
                    "Android exposed this frame as 10-bit P010. "
                        + "Use the RGB compatibility decoder so HDR/10-bit "
                        + "video cannot turn grey or magenta-green. "
                        + describe(image)
                );
            }
            PlaneData[] data = new PlaneData[] {
                data(planes[0]),
                data(planes[1]),
                data(planes[2])
            };
            Layout layout = chooseLayout(
                data,
                width,
                height,
                crop.left,
                crop.top
            );
            // Some vendor MediaCodec buffers expose plane origins that differ
            // between Java absolute reads and JNI direct-buffer addresses.
            // Keep the trusted stride-aware reads here so chroma cannot shift
            // into green/magenta blocks on those devices.
            int[] pixels = toArgb(width, height, data, layout);
            return Bitmap.createBitmap(
                pixels,
                width,
                height,
                Bitmap.Config.ARGB_8888
            );
        } catch (IOException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new IOException(
                "Android returned an unsupported YUV frame layout ("
                    + error.getClass().getSimpleName()
                    + ": "
                    + friendly(error)
                    + "). "
                    + describe(image),
                error
            );
        }
    }

    private static int[] toArgb(
        int width,
        int height,
        int cropLeft,
        int cropTop,
        PlaneData[] planes
    ) {
        if (
            width <= 0
            || height <= 0
            || cropLeft < 0
            || cropTop < 0
            || planes == null
            || planes.length < 3
        ) {
            throw new IllegalArgumentException(
                "YUV frame dimensions or planes are invalid."
            );
        }
        Layout layout = chooseLayout(
            planes,
            width,
            height,
            cropLeft,
            cropTop
        );
        return toArgb(width, height, planes, layout);
    }

    private static int[] toArgb(
        int width,
        int height,
        PlaneData[] planes,
        Layout layout
    ) {
        int[] pixels = new int[width * height];
        for (int row = 0; row < height; row++) {
            int sourceY = layout.top + row;
            int uvRow = sourceY / 2;
            for (int column = 0; column < width; column++) {
                int sourceX = layout.left + column;
                int uvColumn = sourceX / 2;
                pixels[row * width + column] = toColor(
                    layout.y.sample(sourceY, sourceX),
                    layout.u.sample(uvRow, uvColumn),
                    layout.v.sample(uvRow, uvColumn)
                );
            }
        }
        return pixels;
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
        PlaneData[] planes = {
            new PlaneData(yBuffer, yRowStride, yPixelStride),
            new PlaneData(uBuffer, uRowStride, uPixelStride),
            new PlaneData(vBuffer, vRowStride, vPixelStride)
        };
        return toArgb(
            width,
            height,
            cropLeft,
            cropTop,
            planes
        );
    }

    private static Layout chooseLayout(
        PlaneData[] planes,
        int width,
        int height,
        int cropLeft,
        int cropTop
    ) {
        int[][] origins = {
            {cropLeft, cropTop},
            {0, 0}
        };
        Layout best = null;
        for (boolean usePosition : new boolean[] {true, false}) {
            for (int[] origin : origins) {
                Layout candidate;
                try {
                    candidate = new Layout(
                        planes,
                        origin[0],
                        origin[1],
                        width,
                        height,
                        usePosition
                    );
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                if (
                    best == null
                    || candidate.overflow < best.overflow
                ) {
                    best = candidate;
                }
                if (candidate.overflow == 0) {
                    return candidate;
                }
            }
        }
        if (best == null) {
            throw new IllegalArgumentException(
                "No readable YUV plane layout was found."
            );
        }
        return best;
    }

    private static PlaneData data(Image.Plane plane) {
        return new PlaneData(
            plane.getBuffer(),
            plane.getRowStride(),
            plane.getPixelStride()
        );
    }

    private static boolean isTenBitP010(
        Image image,
        Image.Plane[] planes
    ) {
        return isTenBitLayout(
            image.getFormat(),
            image.getWidth(),
            planes[0].getRowStride(),
            planes[0].getPixelStride()
        );
    }

    static boolean isTenBitLayout(
        int format,
        int width,
        int yRowStride,
        int yPixelStride
    ) {
        if (format == ImageFormat.YCBCR_P010) {
            return true;
        }
        // YUV_420_888 guarantees an 8-bit Y plane with pixel stride 1.
        // A few vendor codecs label P010 as flexible YUV; detect the 16-bit
        // luma layout as well so it falls back before bytes are misread.
        return width > 0
            && yPixelStride >= 2
            && yRowStride >= width * 2;
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

    private static String describe(Image image) {
        try {
            StringBuilder result = new StringBuilder();
            Rect crop = image.getCropRect();
            result.append("format=")
                .append(image.getFormat())
                .append(", image=")
                .append(image.getWidth())
                .append('x')
                .append(image.getHeight())
                .append(", crop=")
                .append(crop.left)
                .append(',')
                .append(crop.top)
                .append('-')
                .append(crop.right)
                .append(',')
                .append(crop.bottom);
            Image.Plane[] planes = image.getPlanes();
            result.append(", planes=").append(planes.length);
            for (int index = 0; index < planes.length; index++) {
                ByteBuffer buffer = planes[index].getBuffer();
                result.append(" [")
                    .append(index)
                    .append(":row=")
                    .append(planes[index].getRowStride())
                    .append(",pixel=")
                    .append(planes[index].getPixelStride())
                    .append(",position=")
                    .append(buffer.position())
                    .append(",limit=")
                    .append(buffer.limit())
                    .append(']');
            }
            return result.toString();
        } catch (RuntimeException ignored) {
            return "frame diagnostics unavailable";
        }
    }

    private static String friendly(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
            ? "no detail"
            : message.trim();
    }

}
