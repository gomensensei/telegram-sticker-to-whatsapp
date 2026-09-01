package com.tool48.tgwabridge;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

final class VideoPreviewView extends View {
    interface TransformListener {
        void onTransform(float scale, float offsetX, float offsetY);
    }

    private final ScaleGestureDetector scaleDetector;
    private final Paint backgroundPaint = new Paint();
    private final Paint imagePaint = new Paint(
        Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG
    );
    private final Paint messagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF imageRect = new RectF();
    private Bitmap source;
    private float scale = 1f;
    private float offsetX;
    private float offsetY;
    private float lastX;
    private float lastY;
    private VideoStickerSettings.Background background =
        VideoStickerSettings.Background.TRANSPARENT;
    private TransformListener listener;
    private int emptyMessageRes = R.string.no_video;

    VideoPreviewView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(10, 21, 20));
        messagePaint.setColor(Color.rgb(145, 170, 164));
        messagePaint.setTextAlign(Paint.Align.CENTER);
        scaleDetector = new ScaleGestureDetector(
            context,
            new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    scale = clamp(
                        scale * detector.getScaleFactor(),
                        0.25f,
                        4f
                    );
                    notifyTransform();
                    invalidate();
                    return true;
                }
            }
        );
    }

    void setSource(Bitmap value) {
        if (source != null && source != value && !source.isRecycled()) {
            source.recycle();
        }
        source = value;
        invalidate();
    }

    void setTransform(float valueScale, float x, float y) {
        scale = clamp(valueScale, 0.25f, 4f);
        offsetX = clamp(x, -1.5f, 1.5f);
        offsetY = clamp(y, -1.5f, 1.5f);
        invalidate();
    }

    void setPreviewBackground(VideoStickerSettings.Background value) {
        background = value;
        invalidate();
    }

    void setTransformListener(TransformListener value) {
        listener = value;
    }

    void setEmptyMessage(int stringResource) {
        emptyMessageRes = stringResource;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float side = Math.min(getWidth(), getHeight());
        float left = (getWidth() - side) / 2f;
        float top = (getHeight() - side) / 2f;
        drawCanvasBackground(canvas, left, top, side);
        if (source == null || source.isRecycled()) {
            messagePaint.setTextSize(side / 18f);
            canvas.drawText(
                getContext().getString(emptyMessageRes),
                getWidth() / 2f,
                getHeight() / 2f,
                messagePaint
            );
            return;
        }

        float baseScale = Math.min(
            side / source.getWidth(),
            side / source.getHeight()
        );
        float actualScale = baseScale * scale;
        float width = source.getWidth() * actualScale;
        float height = source.getHeight() * actualScale;
        float x = left
            + (side - width) / 2f
            + offsetX * side / 2f;
        float y = top
            + (side - height) / 2f
            + offsetY * side / 2f;
        imageRect.set(x, y, x + width, y + height);
        canvas.save();
        canvas.clipRect(left, top, left + side, top + side);
        canvas.drawBitmap(
            source,
            null,
            imageRect,
            imagePaint
        );
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!scaleDetector.isInProgress()) {
                    float side = Math.max(
                        1f,
                        Math.min(getWidth(), getHeight())
                    );
                    offsetX = clamp(
                        offsetX + (event.getX() - lastX) * 2f / side,
                        -1.5f,
                        1.5f
                    );
                    offsetY = clamp(
                        offsetY + (event.getY() - lastY) * 2f / side,
                        -1.5f,
                        1.5f
                    );
                    notifyTransform();
                    invalidate();
                }
                lastX = event.getX();
                lastY = event.getY();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                performClick();
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (source != null && !source.isRecycled()) {
            source.recycle();
        }
        source = null;
        super.onDetachedFromWindow();
    }

    private void drawCanvasBackground(
        Canvas canvas,
        float left,
        float top,
        float side
    ) {
        if (background == VideoStickerSettings.Background.BLACK) {
            backgroundPaint.setColor(Color.BLACK);
            canvas.drawRect(
                left,
                top,
                left + side,
                top + side,
                backgroundPaint
            );
        } else if (
            background == VideoStickerSettings.Background.WHITE
        ) {
            backgroundPaint.setColor(Color.WHITE);
            canvas.drawRect(
                left,
                top,
                left + side,
                top + side,
                backgroundPaint
            );
        } else {
            int cells = 12;
            float cell = side / cells;
            for (int row = 0; row < cells; row++) {
                for (int column = 0; column < cells; column++) {
                    backgroundPaint.setColor(
                        ((row + column) & 1) == 0
                            ? Color.rgb(20, 36, 33)
                            : Color.rgb(38, 59, 55)
                    );
                    canvas.drawRect(
                        left + column * cell,
                        top + row * cell,
                        left + (column + 1) * cell,
                        top + (row + 1) * cell,
                        backgroundPaint
                    );
                }
            }
        }
    }

    private void notifyTransform() {
        if (listener != null) {
            listener.onTransform(scale, offsetX, offsetY);
        }
    }

    private static float clamp(float value, float low, float high) {
        return Math.max(low, Math.min(high, value));
    }
}
