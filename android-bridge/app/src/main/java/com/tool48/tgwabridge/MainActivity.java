package com.tool48.tgwabridge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int PICK_ARCHIVE = 48;
    private static final int PICK_VIDEO = 49;
    private static final int ENABLE_PACK = 200;
    private static final String WHATSAPP = "com.whatsapp";
    private static final String WHATSAPP_BUSINESS = "com.whatsapp.w4b";

    private final ExecutorService executor =
        Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private LinearLayout packList;
    private TextView status;
    private Button importButton;
    private Button telegramConvertButton;
    private Button clearBotTokenButton;
    private Button chooseVideoButton;
    private Button renderVideoButton;
    private Button buildMakerPackButton;
    private VideoPreviewView videoPreview;
    private TextView videoInfo;
    private TextView makerStatus;
    private TextView makerQueueCount;
    private TextView startValue;
    private TextView durationValue;
    private TextView scaleValue;
    private TextView positionValue;
    private SeekBar startSeek;
    private SeekBar durationSeek;
    private SeekBar scaleSeek;
    private SeekBar positionXSeek;
    private SeekBar positionYSeek;
    private Spinner backgroundSpinner;
    private ProgressBar makerProgress;
    private EditText makerPackTitle;
    private EditText makerPackPublisher;
    private EditText telegramLink;
    private EditText telegramToken;
    private EditText telegramPublisher;
    private ProgressBar telegramProgress;
    private TextView telegramStatus;
    private Uri selectedVideoUri;
    private long selectedVideoDurationMs;
    private int previewGeneration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildInterface();
        refreshPacks();
        consumeIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        consumeIntent(intent);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(
        int requestCode,
        int resultCode,
        Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);
        if (
            requestCode == PICK_VIDEO
            && resultCode == RESULT_OK
            && data != null
            && data.getData() != null
        ) {
            Uri uri = data.getData();
            takeReadPermission(uri, data);
            selectedVideoUri = uri;
            loadSelectedVideo();
            return;
        }
        if (requestCode == ENABLE_PACK) {
            String validationError = data == null
                ? ""
                : data.getStringExtra("validation_error");
            if (validationError != null && !validationError.trim().isEmpty()) {
                status.setText(
                    "WhatsApp rejected this pack: " + validationError.trim()
                );
            } else if (resultCode == RESULT_OK) {
                status.setText("Sticker pack added to WhatsApp.");
            } else {
                status.setText(
                    "WhatsApp did not add the pack. No sticker files were "
                        + "changed."
                );
            }
            refreshPacks();
            return;
        }
        if (
            requestCode == PICK_ARCHIVE
            && resultCode == RESULT_OK
            && data != null
            && data.getData() != null
        ) {
            Uri uri = data.getData();
            takeReadPermission(uri, data);
            importArchive(uri);
        }
    }

    private void buildInterface() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(247, 248, 250));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(32));
        scroll.addView(
            root,
            new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        );

        TextView title = text(
            "TGWA Maker",
            28,
            Color.rgb(26, 35, 50)
        );
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView intro = text(
            "Convert a whole Telegram sticker pack or make real Animated "
                + "WebP stickers from video, entirely on your phone.",
            16,
            Color.rgb(79, 89, 105)
        );
        LinearLayout.LayoutParams introParams = matchWrap();
        introParams.topMargin = dp(8);
        root.addView(intro, introParams);

        buildTelegramInterface(root);
        buildMakerInterface(root);

        TextView importTitle = text(
            "Import an existing pack",
            20,
            Color.rgb(26, 35, 50)
        );
        importTitle.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams importTitleParams = matchWrap();
        importTitleParams.topMargin = dp(30);
        root.addView(importTitle, importTitleParams);

        importButton = new Button(this);
        importButton.setText("Import .wastickers");
        importButton.setAllCaps(false);
        importButton.setTextSize(17);
        importButton.setOnClickListener(view -> pickArchive());
        LinearLayout.LayoutParams buttonParams = matchWrap();
        buttonParams.topMargin = dp(20);
        root.addView(importButton, buttonParams);

        status = text("", 14, Color.rgb(79, 89, 105));
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(12);
        root.addView(status, statusParams);

        TextView savedTitle = text(
            "Packs ready for WhatsApp",
            20,
            Color.rgb(26, 35, 50)
        );
        savedTitle.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams savedParams = matchWrap();
        savedParams.topMargin = dp(24);
        root.addView(savedTitle, savedParams);

        packList = new LinearLayout(this);
        packList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listParams = matchWrap();
        listParams.topMargin = dp(8);
        root.addView(packList, listParams);
        setContentView(scroll);
        refreshMakerQueue();
    }

    private void buildTelegramInterface(LinearLayout root) {
        TextView sectionTitle = text(
            "Convert a Telegram sticker pack",
            22,
            Color.rgb(26, 35, 50)
        );
        sectionTitle.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams sectionParams = matchWrap();
        sectionParams.topMargin = dp(26);
        root.addView(sectionTitle, sectionParams);

        TextView hint = text(
            "Paste a t.me/addstickers link. Static, TGS and WEBM stickers "
                + "are converted on this phone, separated by type, and "
                + "automatically split into packs of up to 30.",
            14,
            Color.rgb(79, 89, 105)
        );
        root.addView(hint, matchWrap());

        telegramLink = new EditText(this);
        telegramLink.setHint(
            "https://t.me/addstickers/PackName"
        );
        telegramLink.setSingleLine(true);
        LinearLayout.LayoutParams linkParams = matchWrap();
        linkParams.topMargin = dp(8);
        root.addView(telegramLink, linkParams);

        telegramToken = new EditText(this);
        telegramToken.setHint("Telegram Bot Token");
        telegramToken.setSingleLine(true);
        telegramToken.setInputType(
            InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD
        );
        root.addView(telegramToken, matchWrap());

        TextView tokenHint = text(
            "Create one once at https://t.me/BotFather using /newbot. The "
                + "token is encrypted with Android Keystore, stays on this "
                + "phone, and is used only for Telegram Bot API downloads.",
            12,
            Color.rgb(105, 113, 126)
        );
        tokenHint.setAutoLinkMask(android.text.util.Linkify.WEB_URLS);
        tokenHint.setLinksClickable(true);
        root.addView(tokenHint, matchWrap());

        telegramPublisher = new EditText(this);
        telegramPublisher.setHint("Publisher");
        telegramPublisher.setSingleLine(true);
        telegramPublisher.setText("TGWA Maker");
        root.addView(telegramPublisher, matchWrap());

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        telegramConvertButton = addButton(
            "Convert whole pack",
            view -> convertTelegramPack()
        );
        clearBotTokenButton = addButton(
            "Forget token",
            view -> clearTelegramToken()
        );
        buttons.addView(
            telegramConvertButton,
            new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        );
        LinearLayout.LayoutParams clearParams =
            new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
        clearParams.leftMargin = dp(6);
        buttons.addView(clearBotTokenButton, clearParams);
        root.addView(buttons, matchWrap());

        telegramProgress = new ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        );
        telegramProgress.setMax(100);
        telegramProgress.setVisibility(View.GONE);
        root.addView(telegramProgress, matchWrap());

        telegramStatus = text(
            "WhatsApp packs will never mix static and animated stickers.",
            14,
            Color.rgb(79, 89, 105)
        );
        root.addView(telegramStatus, matchWrap());
        try {
            telegramToken.setText(BotTokenStore.load(this));
        } catch (IOException error) {
            telegramStatus.setText(friendly(error));
        }
    }

    private void convertTelegramPack() {
        String link = telegramLink.getText().toString().trim();
        String token = telegramToken.getText().toString().trim();
        String publisher =
            telegramPublisher.getText().toString().trim();
        try {
            TelegramPackLink.shortName(link);
            if (token.isEmpty()) {
                throw new IOException("Enter a Telegram Bot Token.");
            }
            BotTokenStore.save(this, token);
        } catch (IOException error) {
            telegramStatus.setText(friendly(error));
            return;
        }
        setTelegramBusy(true);
        telegramProgress.setProgress(0);
        telegramStatus.setText("Starting Telegram conversion...");
        executor.execute(() -> {
            try {
                TelegramPackConverter.Result result =
                    TelegramPackConverter.convert(
                        this,
                        link,
                        token,
                        publisher,
                        (progress, message) -> mainHandler.post(() -> {
                            telegramProgress.setProgress(progress);
                            telegramStatus.setText(message);
                        })
                    );
                mainHandler.post(() -> {
                    setTelegramBusy(false);
                    telegramStatus.setText(
                        "Converted "
                            + result.source.title
                            + ": "
                            + result.staticCount
                            + " static + "
                            + result.animatedCount
                            + " animated stickers into "
                            + result.packs.size()
                            + " WhatsApp pack"
                            + (result.packs.size() == 1 ? "." : "s.")
                    );
                    refreshPacks();
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    setTelegramBusy(false);
                    telegramStatus.setText(
                        "Telegram conversion failed: " + friendly(error)
                    );
                });
            }
        });
    }

    private void clearTelegramToken() {
        BotTokenStore.clear(this);
        telegramToken.setText("");
        telegramStatus.setText(
            "Saved Telegram Bot Token removed from this phone."
        );
    }

    private void setTelegramBusy(boolean busy) {
        telegramLink.setEnabled(!busy);
        telegramToken.setEnabled(!busy);
        telegramPublisher.setEnabled(!busy);
        telegramConvertButton.setEnabled(!busy);
        clearBotTokenButton.setEnabled(!busy);
        telegramProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void buildMakerInterface(LinearLayout root) {
        TextView sectionTitle = text(
            "Make animated stickers",
            22,
            Color.rgb(26, 35, 50)
        );
        sectionTitle.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams sectionParams = matchWrap();
        sectionParams.topMargin = dp(26);
        root.addView(sectionTitle, sectionParams);

        TextView sectionHint = text(
            "Choose a video, drag to move, pinch to resize, then render. "
                + "Each result is added to the on-phone pack queue.",
            14,
            Color.rgb(79, 89, 105)
        );
        LinearLayout.LayoutParams hintParams = matchWrap();
        hintParams.topMargin = dp(4);
        root.addView(sectionHint, hintParams);

        chooseVideoButton = new Button(this);
        chooseVideoButton.setText("Choose video");
        chooseVideoButton.setAllCaps(false);
        chooseVideoButton.setOnClickListener(view -> pickVideo());
        LinearLayout.LayoutParams chooseParams = matchWrap();
        chooseParams.topMargin = dp(12);
        root.addView(chooseVideoButton, chooseParams);

        videoInfo = text(
            "No video selected.",
            14,
            Color.rgb(79, 89, 105)
        );
        root.addView(videoInfo);

        videoPreview = new VideoPreviewView(this);
        LinearLayout.LayoutParams previewParams =
            new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(320)
            );
        previewParams.topMargin = dp(10);
        root.addView(videoPreview, previewParams);

        startSeek = new SeekBar(this);
        startValue = addSeekControl(
            root,
            "Start time",
            startSeek,
            "0.00 s"
        );
        durationSeek = new SeekBar(this);
        durationSeek.setMax(2_800);
        durationSeek.setProgress(2_800);
        durationValue = addSeekControl(
            root,
            "Clip duration",
            durationSeek,
            "3.00 s"
        );
        scaleSeek = new SeekBar(this);
        scaleSeek.setMax(375);
        scaleSeek.setProgress(75);
        scaleValue = addSeekControl(
            root,
            "Sticker size",
            scaleSeek,
            "100%"
        );
        positionXSeek = new SeekBar(this);
        positionXSeek.setMax(300);
        positionXSeek.setProgress(150);
        positionValue = addSeekControl(
            root,
            "Horizontal position",
            positionXSeek,
            "X 0% · Y 0%"
        );
        positionYSeek = new SeekBar(this);
        positionYSeek.setMax(300);
        positionYSeek.setProgress(150);
        addSeekControl(
            root,
            "Vertical position",
            positionYSeek,
            ""
        );

        TextView backgroundLabel = text(
            "Background",
            14,
            Color.rgb(79, 89, 105)
        );
        LinearLayout.LayoutParams backgroundLabelParams = matchWrap();
        backgroundLabelParams.topMargin = dp(8);
        root.addView(backgroundLabel, backgroundLabelParams);
        backgroundSpinner = new Spinner(this);
        ArrayAdapter<String> backgrounds = new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_item,
            new String[] {"Transparent", "Black", "White"}
        );
        backgrounds.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        );
        backgroundSpinner.setAdapter(backgrounds);
        root.addView(backgroundSpinner, matchWrap());

        makerProgress = new ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        );
        makerProgress.setMax(100);
        makerProgress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = matchWrap();
        progressParams.topMargin = dp(8);
        root.addView(makerProgress, progressParams);

        renderVideoButton = new Button(this);
        renderVideoButton.setText("Render and add to pack queue");
        renderVideoButton.setAllCaps(false);
        renderVideoButton.setEnabled(false);
        renderVideoButton.setOnClickListener(view -> renderSelectedVideo());
        root.addView(renderVideoButton, matchWrap());

        makerStatus = text(
            "Output must contain real ANIM/ANMF frames before it is accepted.",
            14,
            Color.rgb(79, 89, 105)
        );
        root.addView(makerStatus, matchWrap());

        makerQueueCount = text(
            "Pack queue: 0 / 30",
            18,
            Color.rgb(26, 35, 50)
        );
        makerQueueCount.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams queueParams = matchWrap();
        queueParams.topMargin = dp(20);
        root.addView(makerQueueCount, queueParams);

        LinearLayout queueButtons = new LinearLayout(this);
        queueButtons.setOrientation(LinearLayout.HORIZONTAL);
        Button removeLast = addButton(
            "Remove last",
            view -> removeLastMakerItem()
        );
        Button clear = addButton(
            "Clear queue",
            view -> confirmClearMakerQueue()
        );
        queueButtons.addView(removeLast);
        LinearLayout.LayoutParams clearParams =
            new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
        clearParams.leftMargin = dp(6);
        queueButtons.addView(clear, clearParams);
        root.addView(queueButtons, matchWrap());

        makerPackTitle = new EditText(this);
        makerPackTitle.setHint("Pack name");
        makerPackTitle.setSingleLine(true);
        makerPackTitle.setText("TGWA Animated Pack");
        root.addView(makerPackTitle, matchWrap());

        makerPackPublisher = new EditText(this);
        makerPackPublisher.setHint("Publisher");
        makerPackPublisher.setSingleLine(true);
        makerPackPublisher.setText("TGWA Maker");
        root.addView(makerPackPublisher, matchWrap());

        buildMakerPackButton = new Button(this);
        buildMakerPackButton.setText("Build WhatsApp animated pack");
        buildMakerPackButton.setAllCaps(false);
        buildMakerPackButton.setEnabled(false);
        buildMakerPackButton.setOnClickListener(
            view -> buildMakerPack()
        );
        root.addView(buildMakerPackButton, matchWrap());

        bindMakerControls();
    }

    private TextView addSeekControl(
        LinearLayout root,
        String label,
        SeekBar seek,
        String initialValue
    ) {
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        TextView name = text(label, 14, Color.rgb(79, 89, 105));
        TextView value = text(
            initialValue,
            14,
            Color.rgb(26, 35, 50)
        );
        value.setGravity(Gravity.END);
        heading.addView(
            name,
            new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        );
        heading.addView(
            value,
            new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        );
        LinearLayout.LayoutParams headingParams = matchWrap();
        headingParams.topMargin = dp(8);
        root.addView(heading, headingParams);
        root.addView(seek, matchWrap());
        return value;
    }

    private void bindMakerControls() {
        startSeek.setOnSeekBarChangeListener(
            new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(
                    SeekBar seekBar,
                    int progress,
                    boolean fromUser
                ) {
                    updateDurationLimit();
                    updateMakerTransform();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    requestPreviewFrame();
                }
            }
        );
        durationSeek.setOnSeekBarChangeListener(simpleSeekListener());
        scaleSeek.setOnSeekBarChangeListener(simpleSeekListener());
        positionXSeek.setOnSeekBarChangeListener(simpleSeekListener());
        positionYSeek.setOnSeekBarChangeListener(simpleSeekListener());
        backgroundSpinner.setOnItemSelectedListener(
            new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(
                    AdapterView<?> parent,
                    View view,
                    int position,
                    long id
                ) {
                    updateMakerTransform();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            }
        );
        videoPreview.setTransformListener((scale, x, y) -> {
            scaleSeek.setProgress(
                Math.max(0, Math.min(375, Math.round(scale * 100f) - 25))
            );
            positionXSeek.setProgress(
                Math.max(0, Math.min(300, Math.round(x * 100f) + 150))
            );
            positionYSeek.setProgress(
                Math.max(0, Math.min(300, Math.round(y * 100f) + 150))
            );
        });
        updateMakerTransform();
    }

    private SeekBar.OnSeekBarChangeListener simpleSeekListener() {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(
                SeekBar seekBar,
                int progress,
                boolean fromUser
            ) {
                updateMakerTransform();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };
    }

    private void updateMakerTransform() {
        float scale = (scaleSeek.getProgress() + 25) / 100f;
        float offsetX = (positionXSeek.getProgress() - 150) / 100f;
        float offsetY = (positionYSeek.getProgress() - 150) / 100f;
        startValue.setText(formatSeconds(startSeek.getProgress()));
        durationValue.setText(
            formatSeconds(durationSeek.getProgress() + 200L)
        );
        scaleValue.setText(Math.round(scale * 100f) + "%");
        positionValue.setText(
            "X "
                + Math.round(offsetX * 100f)
                + "% \u00B7 Y "
                + Math.round(offsetY * 100f)
                + "%"
        );
        videoPreview.setTransform(scale, offsetX, offsetY);
        videoPreview.setPreviewBackground(selectedBackground());
    }

    private void updateDurationLimit() {
        if (selectedVideoDurationMs <= 0) {
            return;
        }
        int maximumDuration = (int) Math.min(
            3_000,
            selectedVideoDurationMs - startSeek.getProgress()
        );
        maximumDuration = Math.max(200, maximumDuration);
        durationSeek.setMax(maximumDuration - 200);
        if (durationSeek.getProgress() > durationSeek.getMax()) {
            durationSeek.setProgress(durationSeek.getMax());
        }
    }

    private VideoStickerSettings currentMakerSettings() {
        return new VideoStickerSettings(
            startSeek.getProgress(),
            durationSeek.getProgress() + 200L,
            (scaleSeek.getProgress() + 25) / 100f,
            (positionXSeek.getProgress() - 150) / 100f,
            (positionYSeek.getProgress() - 150) / 100f,
            selectedBackground()
        );
    }

    private VideoStickerSettings.Background selectedBackground() {
        int selected = backgroundSpinner.getSelectedItemPosition();
        if (selected == 1) {
            return VideoStickerSettings.Background.BLACK;
        }
        if (selected == 2) {
            return VideoStickerSettings.Background.WHITE;
        }
        return VideoStickerSettings.Background.TRANSPARENT;
    }

    private void pickVideo() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_VIDEO);
    }

    private void takeReadPermission(Uri uri, Intent data) {
        if (
            (
                data.getFlags()
                    & Intent.FLAG_GRANT_READ_URI_PERMISSION
            ) == 0
        ) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (SecurityException ignored) {
            // The immediate operation still has a temporary read grant.
        }
    }

    private void loadSelectedVideo() {
        if (selectedVideoUri == null) {
            return;
        }
        Uri uri = selectedVideoUri;
        setMakerBusy(true);
        makerStatus.setText("Reading video metadata and first frame...");
        executor.execute(() -> {
            try {
                VideoStickerRenderer.Probe probe =
                    VideoStickerRenderer.probe(this, uri);
                mainHandler.post(() -> {
                    if (!uri.equals(selectedVideoUri)) {
                        probe.preview.recycle();
                        return;
                    }
                    selectedVideoDurationMs = probe.durationMs;
                    startSeek.setMax(
                        (int) Math.max(0, probe.durationMs - 200)
                    );
                    startSeek.setProgress(0);
                    updateDurationLimit();
                    durationSeek.setProgress(durationSeek.getMax());
                    videoPreview.setSource(probe.preview);
                    videoInfo.setText(
                        probe.width
                            + " \u00D7 "
                            + probe.height
                            + " \u00B7 "
                            + formatSeconds(probe.durationMs)
                    );
                    makerStatus.setText(
                        "Drag the preview to move it, or pinch to resize."
                    );
                    setMakerBusy(false);
                    renderVideoButton.setEnabled(true);
                    updateMakerTransform();
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    makerStatus.setText(
                        "Video failed: " + friendly(error)
                    );
                    setMakerBusy(false);
                    renderVideoButton.setEnabled(false);
                });
            }
        });
    }

    private void requestPreviewFrame() {
        if (selectedVideoUri == null) {
            return;
        }
        Uri uri = selectedVideoUri;
        long timeMs = startSeek.getProgress();
        int generation = ++previewGeneration;
        executor.execute(() -> {
            try {
                Bitmap frame = VideoStickerRenderer.previewAt(
                    this,
                    uri,
                    timeMs
                );
                mainHandler.post(() -> {
                    if (
                        generation != previewGeneration
                        || !uri.equals(selectedVideoUri)
                    ) {
                        frame.recycle();
                        return;
                    }
                    videoPreview.setSource(frame);
                });
            } catch (IOException error) {
                mainHandler.post(() ->
                    makerStatus.setText(
                        "Preview failed: " + friendly(error)
                    )
                );
            }
        });
    }

    private void renderSelectedVideo() {
        if (selectedVideoUri == null) {
            makerStatus.setText("Choose a video first.");
            return;
        }
        Uri uri = selectedVideoUri;
        VideoStickerSettings settings;
        try {
            settings = currentMakerSettings();
        } catch (IllegalArgumentException error) {
            makerStatus.setText(error.getMessage());
            return;
        }
        setMakerBusy(true);
        makerProgress.setProgress(0);
        makerProgress.setVisibility(View.VISIBLE);
        makerStatus.setText("Starting native Animated WebP encoder...");
        executor.execute(() -> {
            try {
                VideoStickerRenderer.Result result =
                    VideoStickerRenderer.render(
                        this,
                        uri,
                        settings,
                        (percent, message) -> mainHandler.post(() -> {
                            makerProgress.setProgress(percent);
                            makerStatus.setText(message);
                        })
                    );
                MakerQueue.add(this, result.data);
                mainHandler.post(() -> {
                    makerProgress.setProgress(100);
                    makerStatus.setText(
                        "Added real Animated WebP \u00B7 "
                            + result.frameCount
                            + " frames \u00B7 "
                            + result.fps
                            + " FPS \u00B7 Q"
                            + result.quality
                            + " \u00B7 "
                            + result.data.length / 1024
                            + " KB"
                    );
                    setMakerBusy(false);
                    refreshMakerQueue();
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    makerStatus.setText(
                        "Render failed: " + friendly(error)
                    );
                    setMakerBusy(false);
                });
            }
        });
    }

    private void refreshMakerQueue() {
        List<File> items = MakerQueue.list(this);
        makerQueueCount.setText(
            "Pack queue: " + items.size() + " / 30"
        );
        buildMakerPackButton.setEnabled(
            items.size() >= 3 && items.size() <= 30
        );
    }

    private void removeLastMakerItem() {
        executor.execute(() -> {
            try {
                MakerQueue.removeLast(this);
                mainHandler.post(() -> {
                    makerStatus.setText("Removed the last queued sticker.");
                    refreshMakerQueue();
                });
            } catch (IOException error) {
                mainHandler.post(() ->
                    makerStatus.setText(
                        "Queue update failed: " + friendly(error)
                    )
                );
            }
        });
    }

    private void confirmClearMakerQueue() {
        new AlertDialog.Builder(this)
            .setTitle("Clear maker queue?")
            .setMessage(
                "This removes every rendered sticker that has not been built "
                    + "into a pack."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear", (dialog, which) ->
                executor.execute(() -> {
                    try {
                        MakerQueue.clear(this);
                        mainHandler.post(() -> {
                            makerStatus.setText("Maker queue cleared.");
                            refreshMakerQueue();
                        });
                    } catch (IOException error) {
                        mainHandler.post(() ->
                            makerStatus.setText(
                                "Clear failed: " + friendly(error)
                            )
                        );
                    }
                })
            )
            .show();
    }

    private void buildMakerPack() {
        List<File> sources = MakerQueue.list(this);
        if (sources.size() < 3 || sources.size() > 30) {
            makerStatus.setText(
                "Add 3 to 30 animated stickers before building."
            );
            return;
        }
        String title = makerPackTitle.getText().toString();
        String publisher = makerPackPublisher.getText().toString();
        setMakerBusy(true);
        makerStatus.setText("Building and validating animated pack...");
        executor.execute(() -> {
            try {
                Pack pack = MakerPackBuilder.build(
                    this,
                    sources,
                    title,
                    publisher
                );
                MakerQueue.clear(this);
                mainHandler.post(() -> {
                    makerStatus.setText(
                        "Built "
                            + pack.name
                            + " with "
                            + pack.stickers.size()
                            + " animated stickers."
                    );
                    setMakerBusy(false);
                    refreshMakerQueue();
                    refreshPacks();
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    makerStatus.setText(
                        "Pack build failed: " + friendly(error)
                    );
                    setMakerBusy(false);
                });
            }
        });
    }

    private void setMakerBusy(boolean busy) {
        chooseVideoButton.setEnabled(!busy);
        startSeek.setEnabled(!busy);
        durationSeek.setEnabled(!busy);
        scaleSeek.setEnabled(!busy);
        positionXSeek.setEnabled(!busy);
        positionYSeek.setEnabled(!busy);
        backgroundSpinner.setEnabled(!busy);
        makerPackTitle.setEnabled(!busy);
        makerPackPublisher.setEnabled(!busy);
        renderVideoButton.setEnabled(!busy && selectedVideoUri != null);
        if (!busy) {
            refreshMakerQueue();
            makerProgress.setVisibility(View.GONE);
        }
    }

    private static String formatSeconds(long milliseconds) {
        return String.format(
            java.util.Locale.ROOT,
            "%.2f s",
            milliseconds / 1_000.0
        );
    }

    private void pickArchive() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(
            Intent.EXTRA_MIME_TYPES,
            new String[] {
                "application/x-wastickers",
                "application/octet-stream",
                "application/zip"
            }
        );
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, PICK_ARCHIVE);
    }

    private void consumeIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        Uri uri = null;
        String sharedText = "";
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            uri = intent.getData();
        } else if (Intent.ACTION_SEND.equals(intent.getAction())) {
            @SuppressWarnings("deprecation")
            Uri shared = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            uri = shared;
            sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedText == null) {
                sharedText = "";
            }
        }
        if (!sharedText.trim().isEmpty()) {
            if (acceptTelegramLink(sharedText)) {
                intent.setAction(null);
                return;
            }
        }
        if (uri != null) {
            String scheme = uri.getScheme();
            if (
                (
                    "http".equalsIgnoreCase(scheme)
                    || "https".equalsIgnoreCase(scheme)
                    || "tg".equalsIgnoreCase(scheme)
                )
                && acceptTelegramLink(uri.toString())
            ) {
                intent.setAction(null);
                return;
            }
            String type = intent.getType();
            if (type != null && type.startsWith("video/")) {
                selectedVideoUri = uri;
                loadSelectedVideo();
            } else {
                importArchive(uri);
            }
            intent.setAction(null);
        }
    }

    private boolean acceptTelegramLink(String value) {
        try {
            TelegramPackLink.shortName(value);
            telegramLink.setText(value.trim());
            telegramStatus.setText(
                "Telegram pack link received. Press Convert whole pack."
            );
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private void importArchive(Uri uri) {
        importButton.setEnabled(false);
        status.setText("Importing and validating...");
        executor.execute(() -> {
            try {
                List<Pack> importedPacks = PackImporter.importUri(this, uri);
                mainHandler.post(() -> {
                    importButton.setEnabled(true);
                    if (importedPacks.size() == 1) {
                        status.setText(
                            "Imported "
                                + importedPacks.get(0).name
                                + " successfully."
                        );
                    } else {
                        status.setText(
                            "Imported and separated into "
                                + importedPacks.size()
                                + " valid packs."
                        );
                    }
                    refreshPacks();
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    importButton.setEnabled(true);
                    status.setText("Import failed: " + friendly(error));
                });
            }
        });
    }

    private void refreshPacks() {
        List<Pack> packs = PackStore.list(this);
        packList.removeAllViews();
        if (packs.isEmpty()) {
            TextView empty = text(
                "No packs yet. Convert a Telegram pack or make one above, "
                    + "or open a desktop .wastickers file with TGWA Maker.",
                15,
                Color.rgb(105, 113, 126)
            );
            packList.addView(empty);
            return;
        }
        for (Pack pack : packs) {
            packList.addView(packCard(pack));
        }
    }

    private View packCard(Pack pack) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        android.graphics.drawable.GradientDrawable background =
            new android.graphics.drawable.GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), Color.rgb(220, 225, 232));
        card.setBackground(background);

        TextView name = text(pack.name, 18, Color.rgb(26, 35, 50));
        name.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(name);
        String kind = pack.animated ? "animated" : "static";
        TextView details = text(
            pack.stickers.size()
                + " "
                + kind
                + " stickers \u00B7 "
                + pack.publisher,
            14,
            Color.rgb(79, 89, 105)
        );
        card.addView(details);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.START);
        LinearLayout.LayoutParams buttonsParams = matchWrap();
        buttonsParams.topMargin = dp(10);
        card.addView(buttons, buttonsParams);

        if (isInstalled(WHATSAPP)) {
            if (WhitelistCheck.isWhitelisted(this, pack, WHATSAPP)) {
                buttons.addView(addedLabel("Added to WhatsApp"));
            } else {
                buttons.addView(addButton(
                    "Add to WhatsApp",
                    view -> enablePack(pack, WHATSAPP)
                ));
            }
        }
        if (isInstalled(WHATSAPP_BUSINESS)) {
            View business = WhitelistCheck.isWhitelisted(
                this,
                pack,
                WHATSAPP_BUSINESS
            )
                ? addedLabel("Added to Business")
                : addButton(
                    "Add to Business",
                    view -> enablePack(pack, WHATSAPP_BUSINESS)
                );
            LinearLayout.LayoutParams businessParams =
                new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                );
            businessParams.leftMargin = dp(6);
            buttons.addView(business, businessParams);
        }
        if (
            !isInstalled(WHATSAPP)
            && !isInstalled(WHATSAPP_BUSINESS)
        ) {
            TextView missing = text(
                "WhatsApp is not installed.",
                14,
                Color.rgb(170, 60, 60)
            );
            buttons.addView(missing);
        }

        Button delete = addButton(
            "Delete",
            view -> confirmDelete(pack)
        );
        LinearLayout.LayoutParams deleteParams =
            new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
        deleteParams.leftMargin = dp(6);
        buttons.addView(delete, deleteParams);

        LinearLayout.LayoutParams cardParams = matchWrap();
        cardParams.bottomMargin = dp(10);
        card.setLayoutParams(cardParams);
        return card;
    }

    private Button addButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        button.setOnClickListener(listener);
        return button;
    }

    private TextView addedLabel(String label) {
        TextView value = text(label, 14, Color.rgb(28, 130, 82));
        value.setGravity(Gravity.CENTER_VERTICAL);
        value.setPadding(dp(8), dp(8), dp(8), dp(8));
        return value;
    }

    private void enablePack(Pack pack, String targetPackage) {
        Intent intent = new Intent(
            "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
        );
        intent.setPackage(targetPackage);
        intent.putExtra("sticker_pack_id", pack.identifier);
        intent.putExtra(
            "sticker_pack_authority",
            BuildConfig.CONTENT_PROVIDER_AUTHORITY
        );
        intent.putExtra("sticker_pack_name", pack.name);
        try {
            startActivityForResult(intent, ENABLE_PACK);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(
                this,
                "WhatsApp could not open this sticker pack.",
                Toast.LENGTH_LONG
            ).show();
        }
    }

    private void confirmDelete(Pack pack) {
        new AlertDialog.Builder(this)
            .setTitle("Delete " + pack.name + "?")
            .setMessage(
                "This removes the imported copy from TGWA Maker. "
                    + "A pack already added to WhatsApp is not removed."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete", (dialog, which) -> {
                executor.execute(() -> {
                    try {
                        PackStore.delete(this, pack.identifier);
                        mainHandler.post(() -> {
                            status.setText("Deleted " + pack.name + ".");
                            refreshPacks();
                        });
                    } catch (IOException error) {
                        mainHandler.post(() ->
                            status.setText(
                                "Delete failed: " + friendly(error)
                            )
                        );
                    }
                });
            })
            .show();
    }

    @SuppressWarnings("deprecation")
    private boolean isInstalled(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (android.content.pm.PackageManager.NameNotFoundException error) {
            return false;
        }
    }

    private TextView text(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setLineSpacing(0, 1.12f);
        return text;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(
            value * getResources().getDisplayMetrics().density
        );
    }

    private static String friendly(Exception error) {
        String value = error.getMessage();
        return value == null || value.trim().isEmpty()
            ? error.getClass().getSimpleName()
            : value;
    }
}
