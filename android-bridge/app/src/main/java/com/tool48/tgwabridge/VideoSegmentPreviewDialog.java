package com.tool48.tgwabridge;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.VideoView;

import java.util.Locale;

final class VideoSegmentPreviewDialog {
    private static final int INK = Color.rgb(8, 18, 17);
    private static final int SURFACE = Color.rgb(16, 28, 27);
    private static final int TEXT = Color.rgb(241, 250, 247);
    private static final int MUTED = Color.rgb(145, 170, 164);
    private static final int MINT = Color.rgb(112, 239, 189);

    private VideoSegmentPreviewDialog() {
    }

    static void show(
        Context context,
        Uri uri,
        VideoStickerSettings settings,
        float previewSpeed,
        String title
    ) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 18), dp(context, 18), dp(context, 18), dp(context, 14));
        root.setBackground(rounded(SURFACE, dp(context, 22)));

        TextView heading = label(context, title, 19, TEXT);
        heading.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(heading, matchWrap());

        long endMs = settings.startMs + settings.durationMs;
        long previewDurationMs = Math.round(settings.durationMs / previewSpeed);
        TextView range = label(
            context,
            context.getString(
                R.string.preview_range_value,
                formatTime(settings.startMs),
                formatTime(endMs),
                speedLabel(previewSpeed),
                formatTime(previewDurationMs)
            ),
            13,
            MUTED
        );
        range.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams rangeParams = matchWrap();
        rangeParams.topMargin = dp(context, 6);
        root.addView(range, rangeParams);

        FrameLayout viewport = new FrameLayout(context);
        viewport.setClipChildren(true);
        viewport.setClipToPadding(true);
        viewport.setBackgroundColor(backgroundColor(settings.background));
        int previewSide = Math.min(
            context.getResources().getDisplayMetrics().widthPixels - dp(context, 72),
            dp(context, 420)
        );
        LinearLayout.LayoutParams viewportParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            previewSide
        );
        viewportParams.topMargin = dp(context, 14);
        root.addView(viewport, viewportParams);

        VideoView video = new VideoView(context);
        video.setScaleX(settings.scale);
        video.setScaleY(settings.scale);
        viewport.addView(
            video,
            new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        );
        viewport.post(() -> {
            video.setTranslationX(settings.offsetX * viewport.getWidth() / 2f);
            video.setTranslationY(settings.offsetY * viewport.getHeight() / 2f);
        });

        TextView state = label(
            context,
            context.getString(R.string.preview_loading),
            13,
            MUTED
        );
        state.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams stateParams = matchWrap();
        stateParams.topMargin = dp(context, 8);
        root.addView(state, stateParams);

        Button close = new Button(context);
        close.setAllCaps(false);
        close.setText(R.string.close_preview);
        close.setTextColor(INK);
        close.setBackground(rounded(MINT, dp(context, 14)));
        close.setOnClickListener(view -> dialog.dismiss());
        LinearLayout.LayoutParams closeParams = matchWrap();
        closeParams.topMargin = dp(context, 12);
        root.addView(close, closeParams);

        Handler handler = new Handler(Looper.getMainLooper());
        Runnable[] loop = new Runnable[1];
        loop[0] = () -> {
            if (!video.isPlaying()) {
                handler.postDelayed(loop[0], 40);
                return;
            }
            if (video.getCurrentPosition() >= endMs - 20) {
                video.seekTo((int) settings.startMs);
            }
            handler.postDelayed(loop[0], 40);
        };

        video.setOnPreparedListener(player -> {
            player.setVolume(0f, 0f);
            try {
                player.setPlaybackParams(
                    player.getPlaybackParams().setSpeed(previewSpeed)
                );
            } catch (IllegalArgumentException error) {
                state.setText(R.string.preview_speed_unsupported);
            }
            video.seekTo((int) settings.startMs);
            video.start();
            state.setText(R.string.preview_looping);
            handler.post(loop[0]);
        });
        video.setOnErrorListener((player, what, extra) -> {
            state.setText(R.string.preview_video_error);
            return true;
        });
        dialog.setOnDismissListener(value -> {
            handler.removeCallbacksAndMessages(null);
            video.stopPlayback();
        });

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
        video.setVideoURI(uri);
        dialog.show();
        if (window != null) {
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }

    private static int backgroundColor(
        VideoStickerSettings.Background background
    ) {
        if (background == VideoStickerSettings.Background.BLACK) {
            return Color.BLACK;
        }
        if (background == VideoStickerSettings.Background.WHITE) {
            return Color.WHITE;
        }
        return Color.rgb(20, 36, 33);
    }

    private static String formatTime(long milliseconds) {
        long minutes = milliseconds / 60_000L;
        long seconds = (milliseconds / 1_000L) % 60L;
        long millis = milliseconds % 1_000L;
        return String.format(Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, millis);
    }

    private static String speedLabel(float speed) {
        if (speed == 0.125f) {
            return "⅛×";
        }
        if (speed == 0.25f) {
            return "¼×";
        }
        if (speed == 0.5f) {
            return "½×";
        }
        return String.format(Locale.ROOT, "%.0f×", speed);
    }

    private static TextView label(
        Context context,
        String value,
        int sp,
        int color
    ) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private static GradientDrawable rounded(int color, int radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private static int dp(Context context, int value) {
        return Math.round(
            value * context.getResources().getDisplayMetrics().density
        );
    }
}
