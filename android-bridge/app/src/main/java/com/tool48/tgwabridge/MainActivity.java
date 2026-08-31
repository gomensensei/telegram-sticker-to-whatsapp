package com.tool48.tgwabridge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int PICK_ARCHIVE = 48;
    private static final int PICK_VIDEO = 49;
    private static final int PICK_IMAGE = 50;
    private static final int ENABLE_PACK = 200;
    private static final String WHATSAPP = "com.whatsapp";
    private static final String WHATSAPP_BUSINESS = "com.whatsapp.w4b";
    private static final int INK = Color.rgb(8, 18, 17);
    private static final int SURFACE = Color.rgb(16, 28, 27);
    private static final int SURFACE_2 = Color.rgb(20, 36, 33);
    private static final int FIELD = Color.rgb(10, 21, 20);
    private static final int LINE = Color.rgb(38, 59, 55);
    private static final int TEXT = Color.rgb(241, 250, 247);
    private static final int MUTED = Color.rgb(145, 170, 164);
    private static final int DIM = Color.rgb(97, 123, 117);
    private static final int MINT = Color.rgb(112, 239, 189);
    private static final int MINT_STRONG = Color.rgb(37, 211, 102);
    private static final int DANGER = Color.rgb(255, 127, 127);
    private static final String STATE_VIDEO = "state_video";
    private static final String STATE_IMAGE = "state_image";
    private static final String STATE_MAKER_MODE = "state_maker_mode";
    private static final String STATE_MAKER_TARGET = "state_maker_target";
    private static final String STATE_LINK = "state_link";
    private static final String STATE_TOKEN = "state_token";
    private static final String STATE_TG_PUBLISHER = "state_tg_publisher";
    private static final String STATE_MAKER_TITLE = "state_maker_title";
    private static final String STATE_MAKER_PUBLISHER =
        "state_maker_publisher";
    private static final String STATE_SELECTED_TAB = "state_selected_tab";
    private static final String STATE_AUTO_ADD_IDS = "state_auto_add_ids";
    private static final String STATE_AUTO_ADD_ACTIVE =
        "state_auto_add_active";
    private static final String STATE_AUTO_ADD_TOTAL =
        "state_auto_add_total";
    private static final String STATE_AUTO_ADD_COMPLETED =
        "state_auto_add_completed";
    private static final String STATE_AUTO_ADD_TARGET =
        "state_auto_add_target";

    private final ExecutorService executor =
        Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private LinearLayout packList;
    private TextView status;
    private Button importButton;
    private Button telegramConvertButton;
    private Button clearBotTokenButton;
    private Button tokenVisibilityButton;
    private Button customizeTokenButton;
    private Button telegramTabButton;
    private Button videoTabButton;
    private Button packTabButton;
    private Button languageButton;
    private Button chooseVideoButton;
    private Button renderVideoButton;
    private Button previewClipButton;
    private Button previewFinalButton;
    private Button buildMakerPackButton;
    private VideoPreviewView videoPreview;
    private TextView videoInfo;
    private TextView makerStatus;
    private TextView makerQueueCount;
    private TextView startValue;
    private TextView endValue;
    private TextView selectionRange;
    private TextView adjustedDuration;
    private TextView scaleValue;
    private TextView positionValue;
    private SeekBar startSeek;
    private SeekBar endSeek;
    private SeekBar scaleSeek;
    private SeekBar positionXSeek;
    private SeekBar positionYSeek;
    private Spinner backgroundSpinner;
    private Spinner speedSpinner;
    private Spinner makerModeSpinner;
    private Spinner makerTargetSpinner;
    private ImageView startThumbnail;
    private ImageView endThumbnail;
    private ProgressBar makerProgress;
    private EditText makerPackTitle;
    private EditText makerPackPublisher;
    private EditText telegramLink;
    private EditText telegramToken;
    private EditText telegramPublisher;
    private ProgressBar telegramProgress;
    private TextView telegramStatus;
    private TextView tokenSavedStatus;
    private TextView telegramProgressStage;
    private TextView telegramProgressValue;
    private LinearLayout telegramProgressPanel;
    private LinearLayout tokenCustomizationPanel;
    private LinearLayout telegramPanel;
    private LinearLayout makerPanel;
    private LinearLayout packPanel;
    private LinearLayout makerVideoControls;
    private Uri selectedVideoUri;
    private Uri selectedImageUri;
    private long selectedVideoDurationMs;
    private int previewGeneration;
    private int thumbnailGeneration;
    private int selectedTab;
    private boolean tokenVisible;
    private boolean tokenCustomizationVisible;
    private boolean adjustingTrimControls;
    private Bitmap startThumbnailBitmap;
    private Bitmap endThumbnailBitmap;
    private final Runnable thumbnailRefresh = this::requestTrimThumbnails;
    private static final float[] PLAYBACK_SPEEDS = {
        0.125f,
        0.25f,
        0.5f,
        1f,
        2f,
        4f,
        8f
    };
    private final ArrayList<String> pendingAutoAddPackIds =
        new ArrayList<>();
    private boolean autoAddingPacks;
    private int autoAddTotal;
    private int autoAddCompleted;
    private String autoAddTargetPackage = WHATSAPP;
    private final ArrayList<Pack> makerTargetPacks = new ArrayList<>();
    private String selectedMakerTargetId = "";

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppLocale.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(INK);
        getWindow().setNavigationBarColor(INK);
        getWindow().getDecorView().setSystemUiVisibility(0);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.view.WindowInsetsController controller =
                getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(
                    0,
                    android.view.WindowInsetsController
                        .APPEARANCE_LIGHT_STATUS_BARS
                        | android.view.WindowInsetsController
                            .APPEARANCE_LIGHT_NAVIGATION_BARS
                );
            }
        }
        buildInterface();
        restoreInterfaceState(savedInstanceState);
        refreshPacks();
        consumeIntent(getIntent());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (selectedVideoUri != null) {
            outState.putString(STATE_VIDEO, selectedVideoUri.toString());
        }
        if (selectedImageUri != null) {
            outState.putString(STATE_IMAGE, selectedImageUri.toString());
        }
        outState.putInt(
            STATE_MAKER_MODE,
            makerModeSpinner == null
                ? 0
                : makerModeSpinner.getSelectedItemPosition()
        );
        outState.putString(STATE_MAKER_TARGET, selectedMakerTargetId);
        outState.putString(STATE_LINK, valueOf(telegramLink));
        outState.putString(STATE_TOKEN, valueOf(telegramToken));
        outState.putString(
            STATE_TG_PUBLISHER,
            valueOf(telegramPublisher)
        );
        outState.putString(STATE_MAKER_TITLE, valueOf(makerPackTitle));
        outState.putString(
            STATE_MAKER_PUBLISHER,
            valueOf(makerPackPublisher)
        );
        outState.putInt(STATE_SELECTED_TAB, selectedTab);
        outState.putStringArrayList(
            STATE_AUTO_ADD_IDS,
            new ArrayList<>(pendingAutoAddPackIds)
        );
        outState.putBoolean(STATE_AUTO_ADD_ACTIVE, autoAddingPacks);
        outState.putInt(STATE_AUTO_ADD_TOTAL, autoAddTotal);
        outState.putInt(STATE_AUTO_ADD_COMPLETED, autoAddCompleted);
        outState.putString(STATE_AUTO_ADD_TARGET, autoAddTargetPackage);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        consumeIntent(intent);
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacks(thumbnailRefresh);
        recycleThumbnailBitmaps();
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
            selectedVideoDurationMs = 0;
            loadSelectedVideo();
            return;
        }
        if (
            requestCode == PICK_IMAGE
            && resultCode == RESULT_OK
            && data != null
            && data.getData() != null
        ) {
            Uri uri = data.getData();
            takeReadPermission(uri, data);
            selectedImageUri = uri;
            loadSelectedImage();
            return;
        }
        if (requestCode == ENABLE_PACK) {
            String validationError = data == null
                ? ""
                : data.getStringExtra("validation_error");
            if (autoAddingPacks) {
                handleAutomaticAddResult(
                    resultCode,
                    validationError == null ? "" : validationError.trim()
                );
                return;
            }
            if (validationError != null && !validationError.trim().isEmpty()) {
                setPackStatus(
                    getString(
                        R.string.whatsapp_rejected,
                        validationError.trim()
                    )
                );
            } else if (resultCode == RESULT_OK) {
                setPackStatus(getString(R.string.whatsapp_added));
            } else {
                setPackStatus(getString(R.string.whatsapp_not_added));
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
        scroll.setBackgroundColor(INK);
        scroll.setClipToPadding(true);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(
                0,
                insets.getSystemWindowInsetTop(),
                0,
                insets.getSystemWindowInsetBottom()
            );
            return insets;
        });
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(36));
        scroll.addView(
            root,
            new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        );

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView brandMark = text("↗", 20, INK);
        brandMark.setGravity(Gravity.CENTER);
        brandMark.setTypeface(Typeface.DEFAULT_BOLD);
        brandMark.setBackground(rounded(MINT, 12, MINT));
        topBar.addView(
            brandMark,
            new LinearLayout.LayoutParams(dp(42), dp(42))
        );

        LinearLayout brandCopy = new LinearLayout(this);
        brandCopy.setOrientation(LinearLayout.VERTICAL);
        brandCopy.setPadding(dp(10), 0, 0, 0);
        TextView brand = text(
            getString(R.string.brand_name),
            17,
            TEXT
        );
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        brandCopy.addView(brand);
        TextView relay = text(
            getString(R.string.brand_subtitle),
            10,
            DIM
        );
        relay.setLetterSpacing(0.13f);
        brandCopy.addView(relay);
        topBar.addView(
            brandCopy,
            new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        );

        languageButton = addButton(
            getString(R.string.language_toggle),
            view -> AppLocale.toggle(this)
        );
        styleChip(languageButton);
        topBar.addView(languageButton);
        root.addView(topBar, matchWrap());

        TextView localOnly = text(
            getString(R.string.local_only),
            11,
            MINT
        );
        localOnly.setTypeface(Typeface.DEFAULT_BOLD);
        localOnly.setLetterSpacing(0.08f);
        localOnly.setPadding(dp(11), dp(7), dp(11), dp(7));
        localOnly.setBackground(rounded(SURFACE_2, 999, LINE));
        LinearLayout.LayoutParams localParams = wrapWrap();
        localParams.topMargin = dp(28);
        root.addView(localOnly, localParams);

        TextView heroEyebrow = text(
            getString(R.string.hero_eyebrow),
            12,
            MINT
        );
        heroEyebrow.setTypeface(Typeface.DEFAULT_BOLD);
        heroEyebrow.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams heroEyebrowParams = matchWrap();
        heroEyebrowParams.topMargin = dp(18);
        root.addView(heroEyebrow, heroEyebrowParams);

        TextView title = text(
            getString(R.string.hero_title),
            34,
            TEXT
        );
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(10);
        root.addView(title, titleParams);

        TextView intro = text(
            getString(R.string.hero_subtitle),
            16,
            MUTED
        );
        LinearLayout.LayoutParams introParams = matchWrap();
        introParams.topMargin = dp(12);
        root.addView(intro, introParams);

        LinearLayout converterCard = card();
        LinearLayout.LayoutParams converterParams = matchWrap();
        converterParams.topMargin = dp(26);
        root.addView(converterCard, converterParams);

        TextView converterEyebrow = text(
            getString(R.string.new_conversion),
            11,
            MINT
        );
        converterEyebrow.setTypeface(Typeface.DEFAULT_BOLD);
        converterEyebrow.setLetterSpacing(0.09f);
        converterCard.addView(converterEyebrow);
        TextView converterTitle = text(
            getString(R.string.start_conversion),
            24,
            TEXT
        );
        converterTitle.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams converterTitleParams = matchWrap();
        converterTitleParams.topMargin = dp(6);
        converterCard.addView(converterTitle, converterTitleParams);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabs.setBackground(rounded(FIELD, 15, LINE));
        telegramTabButton = new Button(this);
        telegramTabButton.setText(R.string.tab_telegram);
        telegramTabButton.setAllCaps(false);
        telegramTabButton.setOnClickListener(view -> showTab(0));
        videoTabButton = new Button(this);
        videoTabButton.setText(R.string.tab_video);
        videoTabButton.setAllCaps(false);
        videoTabButton.setOnClickListener(view -> showTab(1));
        packTabButton = new Button(this);
        packTabButton.setText(R.string.tab_packs);
        packTabButton.setAllCaps(false);
        packTabButton.setOnClickListener(view -> showTab(2));
        tabs.addView(
            telegramTabButton,
            new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        );
        tabs.addView(
            videoTabButton,
            new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        );
        tabs.addView(
            packTabButton,
            new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        );
        LinearLayout.LayoutParams tabsParams = matchWrap();
        tabsParams.topMargin = dp(18);
        converterCard.addView(tabs, tabsParams);

        telegramPanel = new LinearLayout(this);
        telegramPanel.setOrientation(LinearLayout.VERTICAL);
        buildTelegramInterface(telegramPanel);
        converterCard.addView(telegramPanel, matchWrap());

        makerPanel = new LinearLayout(this);
        makerPanel.setOrientation(LinearLayout.VERTICAL);
        buildMakerInterface(makerPanel);
        converterCard.addView(makerPanel, matchWrap());

        packPanel = new LinearLayout(this);
        packPanel.setOrientation(LinearLayout.VERTICAL);
        buildPackManagerInterface(packPanel);
        converterCard.addView(packPanel, matchWrap());
        showTab(0);

        buildHandlesCard(root);

        setContentView(scroll);
        refreshMakerQueue();
    }

    private void buildPackManagerInterface(LinearLayout root) {
        TextView managerTitle = text(
            getString(R.string.pack_manager_title),
            22,
            TEXT
        );
        managerTitle.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams managerTitleParams = matchWrap();
        managerTitleParams.topMargin = dp(20);
        root.addView(managerTitle, managerTitleParams);
        TextView managerHint = text(
            getString(R.string.pack_manager_hint),
            14,
            MUTED
        );
        LinearLayout.LayoutParams managerHintParams = matchWrap();
        managerHintParams.topMargin = dp(6);
        root.addView(managerHint, managerHintParams);

        LinearLayout importCard = card();
        LinearLayout.LayoutParams importCardParams = matchWrap();
        importCardParams.topMargin = dp(16);
        root.addView(importCard, importCardParams);
        TextView importTitle = text(
            getString(R.string.import_title),
            20,
            TEXT
        );
        importTitle.setTypeface(Typeface.DEFAULT_BOLD);
        importCard.addView(importTitle);
        TextView importHint = text(
            getString(R.string.import_hint),
            14,
            MUTED
        );
        LinearLayout.LayoutParams importHintParams = matchWrap();
        importHintParams.topMargin = dp(6);
        importCard.addView(importHint, importHintParams);
        importButton = addButton(
            getString(R.string.import_wastickers),
            view -> pickArchive()
        );
        styleSecondary(importButton);
        LinearLayout.LayoutParams buttonParams = matchWrap();
        buttonParams.topMargin = dp(14);
        importCard.addView(importButton, buttonParams);
        status = text("", 14, MUTED);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(10);
        importCard.addView(status, statusParams);

        LinearLayout packsCard = card();
        LinearLayout.LayoutParams packsCardParams = matchWrap();
        packsCardParams.topMargin = dp(16);
        root.addView(packsCard, packsCardParams);
        TextView savedTitle = text(
            getString(R.string.packs_ready),
            20,
            TEXT
        );
        savedTitle.setTypeface(Typeface.DEFAULT_BOLD);
        packsCard.addView(savedTitle);
        packList = new LinearLayout(this);
        packList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listParams = matchWrap();
        listParams.topMargin = dp(12);
        packsCard.addView(packList, listParams);

    }

    private void buildTelegramInterface(LinearLayout root) {
        TextView sectionTitle = text(
            getString(R.string.telegram_title),
            21,
            TEXT
        );
        sectionTitle.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams sectionParams = matchWrap();
        sectionParams.topMargin = dp(22);
        root.addView(sectionTitle, sectionParams);

        TextView hint = text(
            getString(R.string.telegram_hint),
            14,
            MUTED
        );
        LinearLayout.LayoutParams hintParams = matchWrap();
        hintParams.topMargin = dp(6);
        root.addView(hint, hintParams);

        addFieldLabel(
            root,
            getString(R.string.telegram_link_label),
            16
        );
        LinearLayout linkRow = new LinearLayout(this);
        linkRow.setOrientation(LinearLayout.HORIZONTAL);
        linkRow.setGravity(Gravity.CENTER_VERTICAL);
        telegramLink = new EditText(this);
        telegramLink.setHint(R.string.telegram_link_hint);
        telegramLink.setSingleLine(true);
        styleInput(telegramLink);
        linkRow.addView(
            telegramLink,
            new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        );
        Button pasteLink = addButton(
            getString(R.string.paste),
            view -> pasteInto(telegramLink)
        );
        LinearLayout.LayoutParams pasteLinkParams = wrapWrap();
        pasteLinkParams.leftMargin = dp(8);
        linkRow.addView(pasteLink, pasteLinkParams);
        root.addView(linkRow, matchWrap());

        TextView linkHelp = text(
            getString(R.string.telegram_link_help),
            12,
            DIM
        );
        LinearLayout.LayoutParams linkHelpParams = matchWrap();
        linkHelpParams.topMargin = dp(6);
        root.addView(linkHelp, linkHelpParams);

        customizeTokenButton = addButton(
            getString(R.string.customize_token_api),
            view -> toggleTokenCustomization()
        );
        styleSecondary(customizeTokenButton);
        LinearLayout.LayoutParams customizeParams = matchWrap();
        customizeParams.topMargin = dp(14);
        root.addView(customizeTokenButton, customizeParams);

        tokenCustomizationPanel = new LinearLayout(this);
        tokenCustomizationPanel.setOrientation(LinearLayout.VERTICAL);
        tokenCustomizationPanel.setVisibility(View.GONE);
        addFieldLabel(
            tokenCustomizationPanel,
            getString(R.string.bot_token_label),
            14
        );
        LinearLayout tokenRow = new LinearLayout(this);
        tokenRow.setOrientation(LinearLayout.HORIZONTAL);
        tokenRow.setGravity(Gravity.CENTER_VERTICAL);
        telegramToken = new EditText(this);
        telegramToken.setHint(R.string.bot_token_hint);
        telegramToken.setSingleLine(true);
        telegramToken.setInputType(
            InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD
        );
        telegramToken.setTransformationMethod(
            PasswordTransformationMethod.getInstance()
        );
        styleInput(telegramToken);
        tokenRow.addView(
            telegramToken,
            new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        );
        tokenVisibilityButton = addButton(
            getString(R.string.show),
            view -> toggleTokenVisibility()
        );
        LinearLayout.LayoutParams visibilityParams = wrapWrap();
        visibilityParams.leftMargin = dp(8);
        tokenRow.addView(tokenVisibilityButton, visibilityParams);
        tokenCustomizationPanel.addView(tokenRow, matchWrap());

        TextView tokenHint = text(
            getString(R.string.bot_token_help),
            12,
            DIM
        );
        LinearLayout.LayoutParams tokenHintParams = matchWrap();
        tokenHintParams.topMargin = dp(6);
        tokenCustomizationPanel.addView(tokenHint, tokenHintParams);

        LinearLayout tokenActions = new LinearLayout(this);
        tokenActions.setOrientation(LinearLayout.HORIZONTAL);
        Button pasteToken = addButton(
            getString(R.string.paste),
            view -> pasteInto(telegramToken)
        );
        tokenActions.addView(pasteToken);
        Button botFather = addButton(
            getString(R.string.botfather),
            view -> openBotFather()
        );
        LinearLayout.LayoutParams botFatherParams = wrapWrap();
        botFatherParams.leftMargin = dp(6);
        tokenActions.addView(botFather, botFatherParams);
        clearBotTokenButton = addButton(
            getString(R.string.forget_token),
            view -> clearTelegramToken()
        );
        LinearLayout.LayoutParams clearTokenParams = wrapWrap();
        clearTokenParams.leftMargin = dp(6);
        tokenActions.addView(clearBotTokenButton, clearTokenParams);
        LinearLayout.LayoutParams tokenActionsParams = matchWrap();
        tokenActionsParams.topMargin = dp(8);
        tokenCustomizationPanel.addView(tokenActions, tokenActionsParams);
        root.addView(tokenCustomizationPanel, matchWrap());

        tokenSavedStatus = text("", 13, MINT);
        tokenSavedStatus.setTypeface(Typeface.DEFAULT_BOLD);
        tokenSavedStatus.setPadding(
            dp(12),
            dp(10),
            dp(12),
            dp(10)
        );
        tokenSavedStatus.setBackground(rounded(SURFACE_2, 12, LINE));
        tokenSavedStatus.setVisibility(View.VISIBLE);
        LinearLayout.LayoutParams savedTokenParams = matchWrap();
        savedTokenParams.topMargin = dp(8);
        root.addView(tokenSavedStatus, savedTokenParams);

        addFieldLabel(root, getString(R.string.publisher), 14);
        telegramPublisher = new EditText(this);
        telegramPublisher.setHint(R.string.publisher);
        telegramPublisher.setSingleLine(true);
        telegramPublisher.setText(R.string.publisher_default);
        styleInput(telegramPublisher);
        root.addView(telegramPublisher, matchWrap());

        telegramConvertButton = addButton(
            getString(R.string.convert_whole_pack),
            view -> convertTelegramPack()
        );
        stylePrimary(telegramConvertButton);
        LinearLayout.LayoutParams convertParams = matchWrap();
        convertParams.topMargin = dp(16);
        root.addView(telegramConvertButton, convertParams);
        TextView convertDetail = text(
            getString(R.string.convert_whole_pack_detail),
            11,
            DIM
        );
        convertDetail.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams convertDetailParams = matchWrap();
        convertDetailParams.topMargin = dp(5);
        root.addView(convertDetail, convertDetailParams);

        telegramProgressPanel = new LinearLayout(this);
        telegramProgressPanel.setOrientation(LinearLayout.VERTICAL);
        telegramProgressPanel.setPadding(
            dp(13),
            dp(12),
            dp(13),
            dp(12)
        );
        telegramProgressPanel.setBackground(
            rounded(SURFACE_2, 14, LINE)
        );
        telegramProgressPanel.setVisibility(View.GONE);

        LinearLayout progressHeading = new LinearLayout(this);
        progressHeading.setOrientation(LinearLayout.HORIZONTAL);
        progressHeading.setGravity(Gravity.CENTER_VERTICAL);
        telegramProgressStage = text(
            getString(R.string.telegram_starting),
            13,
            TEXT
        );
        telegramProgressStage.setTypeface(Typeface.DEFAULT_BOLD);
        progressHeading.addView(
            telegramProgressStage,
            new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        );
        telegramProgressValue = text(
            getString(R.string.progress_value, 0),
            13,
            MINT
        );
        telegramProgressValue.setTypeface(Typeface.DEFAULT_BOLD);
        progressHeading.addView(telegramProgressValue, wrapWrap());
        telegramProgressPanel.addView(progressHeading, matchWrap());

        telegramProgress = new ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        );
        telegramProgress.setMax(100);
        styleProgress(telegramProgress);
        LinearLayout.LayoutParams telegramProgressParams =
            new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(10)
            );
        telegramProgressParams.topMargin = dp(10);
        telegramProgressPanel.addView(
            telegramProgress,
            telegramProgressParams
        );
        LinearLayout.LayoutParams progressPanelParams = matchWrap();
        progressPanelParams.topMargin = dp(12);
        root.addView(telegramProgressPanel, progressPanelParams);

        telegramStatus = text(
            getString(R.string.telegram_initial_status),
            14,
            MUTED
        );
        LinearLayout.LayoutParams telegramStatusParams = matchWrap();
        telegramStatusParams.topMargin = dp(9);
        root.addView(telegramStatus, telegramStatusParams);
        try {
            String savedToken = BotTokenStore.load(this);
            telegramToken.setText(savedToken);
            updateTokenSavedState(!savedToken.isEmpty());
        } catch (IOException error) {
            telegramStatus.setText(friendly(error));
        }
    }

    private void convertTelegramPack() {
        String link = telegramLink.getText().toString().trim();
        String customToken = telegramToken.getText().toString().trim();
        String token = customToken.isEmpty()
            ? DefaultBotCredential.value()
            : customToken;
        String publisher =
            telegramPublisher.getText().toString().trim();
        try {
            TelegramPackLink.shortName(link);
        } catch (IOException error) {
            telegramStatus.setText(R.string.invalid_telegram_link);
            showTelegramProgressError(
                getString(R.string.invalid_telegram_link)
            );
            return;
        }
        if (token.isEmpty()) {
            telegramStatus.setText(R.string.enter_bot_token);
            showTelegramProgressError(
                getString(R.string.enter_bot_token)
            );
            return;
        }
        if (!customToken.isEmpty()) {
            try {
                BotTokenStore.save(this, customToken);
                updateTokenSavedState(true);
            } catch (IOException error) {
                telegramStatus.setText(friendly(error));
                showTelegramProgressError(friendly(error));
                return;
            }
        } else {
            BotTokenStore.clear(this);
            updateTokenSavedState(false);
        }
        setTelegramBusy(true);
        telegramStatus.setText(R.string.telegram_starting);
        showTelegramProgress(
            0,
            getString(R.string.telegram_starting),
            true
        );
        executor.execute(() -> {
            try {
                TelegramPackConverter.Result result =
                    TelegramPackConverter.convert(
                        this,
                        link,
                        token,
                        publisher,
                        progress -> mainHandler.post(() -> {
                            String stage =
                                telegramProgressStage(progress);
                            showTelegramProgress(
                                progress.percent,
                                stage,
                                progress.stage
                                    == TelegramPackConverter.Stage.READING_PACK
                            );
                            telegramStatus.setText(
                                getString(
                                    R.string.progress_status_detail,
                                    stage,
                                    progress.percent
                                )
                            );
                        })
                    );
                mainHandler.post(() -> {
                    setTelegramBusy(false);
                    showTelegramProgress(
                        100,
                        getString(R.string.conversion_ready),
                        false
                    );
                    telegramStatus.setText(
                        getString(
                            R.string.telegram_sync_done,
                            result.source.title,
                            result.staticCount,
                            result.animatedCount,
                            result.packs.size(),
                            result.newCount,
                            result.reusedCount
                        )
                    );
                    refreshPacks();
                    beginAutomaticWhatsAppAdd(result.packs);
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    setTelegramBusy(false);
                    String failure = getString(
                        R.string.telegram_failed,
                        friendly(error)
                    );
                    telegramStatus.setText(failure);
                    showTelegramProgressError(failure);
                });
            }
        });
    }

    private String telegramProgressStage(
        TelegramPackConverter.Progress progress
    ) {
        switch (progress.stage) {
            case READING_PACK:
                return getString(R.string.progress_reading_pack);
            case REUSING_STICKER:
                return getString(
                    R.string.progress_reusing_sticker,
                    progress.stickerNumber,
                    progress.stickerCount
                );
            case DOWNLOADING_STICKER:
                return getString(
                    R.string.progress_downloading_sticker,
                    progress.stickerNumber,
                    progress.stickerCount,
                    progress.stickerPercent
                );
            case CONVERTING_STICKER:
                return getString(
                    R.string.progress_converting_sticker,
                    progress.stickerNumber,
                    progress.stickerCount,
                    progress.stickerPercent
                );
            case BUILDING_PACKS:
                return getString(R.string.progress_building_packs);
            case COMPLETE:
            default:
                return getString(R.string.conversion_ready);
        }
    }

    private void clearTelegramToken() {
        BotTokenStore.clear(this);
        telegramToken.setText("");
        updateTokenSavedState(false);
        telegramStatus.setText(
            DefaultBotCredential.available()
                ? R.string.token_reverted_to_builtin
                : R.string.token_removed
        );
    }

    private void setTelegramBusy(boolean busy) {
        telegramLink.setEnabled(!busy);
        telegramToken.setEnabled(!busy);
        telegramPublisher.setEnabled(!busy);
        telegramConvertButton.setEnabled(!busy);
        clearBotTokenButton.setEnabled(!busy);
        tokenVisibilityButton.setEnabled(!busy);
        customizeTokenButton.setEnabled(!busy);
        languageButton.setEnabled(!busy);
        languageButton.setAlpha(busy ? 0.45f : 1f);
        telegramConvertButton.setAlpha(busy ? 0.45f : 1f);
        if (busy) {
            telegramProgressPanel.setVisibility(View.VISIBLE);
        }
    }

    private void showTelegramProgress(
        int percent,
        String stage,
        boolean indeterminate
    ) {
        int safePercent = Math.max(0, Math.min(100, percent));
        telegramProgressPanel.setVisibility(View.VISIBLE);
        telegramProgressStage.setText(stage);
        telegramProgressStage.setTextColor(TEXT);
        telegramProgressValue.setText(
            indeterminate
                ? getString(R.string.progress_waiting)
                : getString(R.string.progress_value, safePercent)
        );
        telegramProgressValue.setTextColor(MINT);
        telegramProgress.setIndeterminateTintList(
            ColorStateList.valueOf(MINT)
        );
        telegramProgress.setProgressTintList(
            ColorStateList.valueOf(MINT)
        );
        telegramProgress.setIndeterminate(indeterminate);
        if (!indeterminate) {
            telegramProgress.setProgress(safePercent, true);
        }
    }

    private void showTelegramProgressError(String message) {
        telegramProgressPanel.setVisibility(View.VISIBLE);
        telegramProgress.setIndeterminate(false);
        telegramProgress.setProgress(0);
        telegramProgress.setProgressTintList(
            ColorStateList.valueOf(DANGER)
        );
        telegramProgressStage.setText(message);
        telegramProgressStage.setTextColor(DANGER);
        telegramProgressValue.setText(R.string.progress_error_symbol);
        telegramProgressValue.setTextColor(DANGER);
    }

    private void beginAutomaticWhatsAppAdd(List<Pack> packs) {
        if (packs == null || packs.isEmpty()) {
            return;
        }
        ArrayList<Pack> eligible = new ArrayList<>();
        for (Pack pack : packs) {
            if (pack.whatsappEligible()) {
                eligible.add(pack);
            }
        }
        if (eligible.isEmpty()) {
            autoAddingPacks = false;
            pendingAutoAddPackIds.clear();
            String telegramOnly = getString(
                R.string.whatsapp_auto_none_eligible
            );
            showTelegramProgress(100, telegramOnly, false);
            setPackStatus(telegramOnly);
            return;
        }
        if (eligible.size() != packs.size()) {
            Toast.makeText(
                this,
                R.string.whatsapp_parts_skipped,
                Toast.LENGTH_LONG
            ).show();
        }
        if (isInstalled(WHATSAPP)) {
            autoAddTargetPackage = WHATSAPP;
        } else if (isInstalled(WHATSAPP_BUSINESS)) {
            autoAddTargetPackage = WHATSAPP_BUSINESS;
        } else {
            autoAddingPacks = false;
            pendingAutoAddPackIds.clear();
            String unavailable = getString(
                R.string.whatsapp_auto_unavailable
            );
            showTelegramProgress(100, unavailable, false);
            setPackStatus(unavailable);
            return;
        }
        pendingAutoAddPackIds.clear();
        for (Pack pack : eligible) {
            pendingAutoAddPackIds.add(pack.identifier);
        }
        autoAddTotal = pendingAutoAddPackIds.size();
        autoAddCompleted = 0;
        autoAddingPacks = true;
        launchNextAutomaticPack();
    }

    private void launchNextAutomaticPack() {
        Pack pack = currentAutomaticPack();
        while (pack == null && !pendingAutoAddPackIds.isEmpty()) {
            pendingAutoAddPackIds.remove(0);
            pack = currentAutomaticPack();
        }
        if (pack == null) {
            finishAutomaticAdd();
            return;
        }
        String target = automaticTargetName();
        int current = autoAddCompleted + 1;
        String opening = getString(
            R.string.whatsapp_auto_opening,
            target,
            pack.name,
            current,
            autoAddTotal
        );
        showTelegramProgress(100, opening, false);
        telegramStatus.setText(
            getString(R.string.whatsapp_auto_confirm, target)
        );
        if (!enablePack(pack, autoAddTargetPackage)) {
            stopAutomaticAdd(
                getString(R.string.whatsapp_open_failed),
                pack
            );
        }
    }

    private void handleAutomaticAddResult(
        int resultCode,
        String validationError
    ) {
        Pack pack = currentAutomaticPack();
        if (pack == null) {
            finishAutomaticAdd();
            return;
        }
        if (!validationError.isEmpty()) {
            stopAutomaticAdd(
                getString(
                    R.string.whatsapp_rejected,
                    validationError
                ),
                pack
            );
            return;
        }
        boolean whitelisted = WhitelistCheck.isWhitelisted(
            this,
            pack,
            autoAddTargetPackage
        );
        if (resultCode != RESULT_OK && !whitelisted) {
            stopAutomaticAdd(
                getString(
                    R.string.whatsapp_auto_stopped,
                    pack.name
                ),
                pack
            );
            return;
        }
        pendingAutoAddPackIds.remove(0);
        autoAddCompleted++;
        refreshPacks();
        if (pendingAutoAddPackIds.isEmpty()) {
            finishAutomaticAdd();
        } else {
            mainHandler.postDelayed(
                this::launchNextAutomaticPack,
                350
            );
        }
    }

    private void finishAutomaticAdd() {
        autoAddingPacks = false;
        pendingAutoAddPackIds.clear();
        String complete = getString(
            R.string.whatsapp_auto_complete,
            autoAddCompleted,
            automaticTargetName()
        );
        showTelegramProgress(100, complete, false);
        setPackStatus(complete);
        refreshPacks();
    }

    private void stopAutomaticAdd(String message, Pack pack) {
        autoAddingPacks = false;
        pendingAutoAddPackIds.clear();
        String value = message;
        if (
            value == null
            || value.trim().isEmpty()
        ) {
            value = getString(
                R.string.whatsapp_auto_stopped,
                pack == null ? "" : pack.name
            );
        }
        showTelegramProgress(100, value, false);
        setPackStatus(value);
        refreshPacks();
    }

    private Pack currentAutomaticPack() {
        if (pendingAutoAddPackIds.isEmpty()) {
            return null;
        }
        return PackStore.find(this, pendingAutoAddPackIds.get(0));
    }

    private String automaticTargetName() {
        return getString(
            WHATSAPP_BUSINESS.equals(autoAddTargetPackage)
                ? R.string.whatsapp_business_name
                : R.string.whatsapp_name
        );
    }

    private void setPackStatus(String value) {
        if (status != null) {
            status.setText(value);
        }
        if (telegramStatus != null) {
            telegramStatus.setText(value);
        }
    }

    private void buildMakerInterface(LinearLayout root) {
        TextView sectionTitle = text(
            getString(R.string.maker_title),
            21,
            TEXT
        );
        sectionTitle.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams sectionParams = matchWrap();
        sectionParams.topMargin = dp(22);
        root.addView(sectionTitle, sectionParams);

        TextView sectionHint = text(
            getString(R.string.maker_hint),
            14,
            MUTED
        );
        LinearLayout.LayoutParams hintParams = matchWrap();
        hintParams.topMargin = dp(6);
        root.addView(sectionHint, hintParams);

        addFieldLabel(root, getString(R.string.maker_type), 14);
        makerModeSpinner = new Spinner(this);
        makerModeSpinner.setAdapter(
            darkSpinnerAdapter(
                new String[] {
                    getString(R.string.maker_type_animated),
                    getString(R.string.maker_type_static)
                }
            )
        );
        makerModeSpinner.setPadding(dp(12), dp(4), dp(12), dp(4));
        makerModeSpinner.setBackground(rounded(FIELD, 13, LINE));
        root.addView(makerModeSpinner, matchWrap());

        chooseVideoButton = addButton(
            getString(R.string.choose_video),
            view -> pickMakerMedia()
        );
        styleSecondary(chooseVideoButton);
        LinearLayout.LayoutParams chooseParams = matchWrap();
        chooseParams.topMargin = dp(16);
        root.addView(chooseVideoButton, chooseParams);

        videoInfo = text(
            getString(R.string.no_video),
            14,
            MUTED
        );
        LinearLayout.LayoutParams videoInfoParams = matchWrap();
        videoInfoParams.topMargin = dp(8);
        root.addView(videoInfo, videoInfoParams);

        videoPreview = new VideoPreviewView(this);
        videoPreview.setBackground(rounded(FIELD, 16, LINE));
        LinearLayout.LayoutParams previewParams =
            new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(320)
            );
        previewParams.topMargin = dp(10);
        root.addView(videoPreview, previewParams);

        makerVideoControls = new LinearLayout(this);
        makerVideoControls.setOrientation(LinearLayout.VERTICAL);
        root.addView(makerVideoControls, matchWrap());

        startSeek = new SeekBar(this);
        startValue = addSeekControl(
            makerVideoControls,
            getString(R.string.start_time),
            startSeek,
            "00:00.000"
        );
        endSeek = new SeekBar(this);
        endSeek.setMax(3_000);
        endSeek.setProgress(3_000);
        endValue = addSeekControl(
            makerVideoControls,
            getString(R.string.end_time),
            endSeek,
            "00:03.000"
        );

        LinearLayout thumbnails = new LinearLayout(this);
        thumbnails.setOrientation(LinearLayout.HORIZONTAL);
        startThumbnail = addTrimThumbnail(
            thumbnails,
            getString(R.string.start_frame)
        );
        endThumbnail = addTrimThumbnail(
            thumbnails,
            getString(R.string.end_frame)
        );
        LinearLayout.LayoutParams thumbnailsParams = matchWrap();
        thumbnailsParams.topMargin = dp(8);
        makerVideoControls.addView(thumbnails, thumbnailsParams);

        selectionRange = text(
            getString(
                R.string.selected_range,
                "00:00.000",
                "00:03.000"
            ),
            14,
            TEXT
        );
        selectionRange.setTypeface(Typeface.DEFAULT_BOLD);
        selectionRange.setGravity(Gravity.CENTER_HORIZONTAL);
        selectionRange.setPadding(dp(10), dp(10), dp(10), dp(10));
        selectionRange.setBackground(rounded(SURFACE_2, 12, LINE));
        LinearLayout.LayoutParams rangeParams = matchWrap();
        rangeParams.topMargin = dp(8);
        makerVideoControls.addView(selectionRange, rangeParams);

        previewClipButton = addButton(
            getString(R.string.preview_selected_segment),
            view -> previewSelectedVideo(false)
        );
        styleSecondary(previewClipButton);
        previewClipButton.setEnabled(false);
        previewClipButton.setAlpha(0.45f);
        LinearLayout.LayoutParams clipPreviewParams = matchWrap();
        clipPreviewParams.topMargin = dp(8);
        makerVideoControls.addView(previewClipButton, clipPreviewParams);

        TextView speedLabel = text(
            getString(R.string.playback_speed),
            14,
            MUTED
        );
        LinearLayout.LayoutParams speedLabelParams = matchWrap();
        speedLabelParams.topMargin = dp(12);
        makerVideoControls.addView(speedLabel, speedLabelParams);
        speedSpinner = new Spinner(this);
        ArrayAdapter<String> speeds = darkSpinnerAdapter(
            new String[] {
                getString(R.string.speed_eighth),
                getString(R.string.speed_quarter),
                getString(R.string.speed_half),
                getString(R.string.speed_normal),
                getString(R.string.speed_double),
                getString(R.string.speed_quadruple),
                getString(R.string.speed_octuple)
            }
        );
        speedSpinner.setAdapter(speeds);
        speedSpinner.setSelection(3);
        speedSpinner.setPadding(dp(12), dp(4), dp(12), dp(4));
        speedSpinner.setBackground(rounded(FIELD, 13, LINE));
        makerVideoControls.addView(speedSpinner, matchWrap());
        adjustedDuration = text("", 13, MUTED);
        LinearLayout.LayoutParams adjustedParams = matchWrap();
        adjustedParams.topMargin = dp(6);
        makerVideoControls.addView(adjustedDuration, adjustedParams);

        scaleSeek = new SeekBar(this);
        scaleSeek.setMax(375);
        scaleSeek.setProgress(75);
        scaleValue = addSeekControl(
            root,
            getString(R.string.sticker_size),
            scaleSeek,
            "100%"
        );
        positionXSeek = new SeekBar(this);
        positionXSeek.setMax(300);
        positionXSeek.setProgress(150);
        positionValue = addSeekControl(
            root,
            getString(R.string.horizontal_position),
            positionXSeek,
            getString(R.string.position_value, 0, 0)
        );
        positionYSeek = new SeekBar(this);
        positionYSeek.setMax(300);
        positionYSeek.setProgress(150);
        addSeekControl(
            root,
            getString(R.string.vertical_position),
            positionYSeek,
            ""
        );

        TextView backgroundLabel = text(
            getString(R.string.background),
            14,
            MUTED
        );
        LinearLayout.LayoutParams backgroundLabelParams = matchWrap();
        backgroundLabelParams.topMargin = dp(8);
        root.addView(backgroundLabel, backgroundLabelParams);
        backgroundSpinner = new Spinner(this);
        ArrayAdapter<String> backgrounds = new ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            new String[] {
                getString(R.string.transparent),
                getString(R.string.black),
                getString(R.string.white)
            }
        ) {
            @Override
            public View getView(
                int position,
                View convertView,
                ViewGroup parent
            ) {
                TextView view = (TextView) super.getView(
                    position,
                    convertView,
                    parent
                );
                view.setTextColor(TEXT);
                return view;
            }

            @Override
            public View getDropDownView(
                int position,
                View convertView,
                ViewGroup parent
            ) {
                TextView view = (TextView) super.getDropDownView(
                    position,
                    convertView,
                    parent
                );
                view.setTextColor(TEXT);
                view.setBackgroundColor(SURFACE_2);
                view.setPadding(dp(12), dp(10), dp(12), dp(10));
                return view;
            }
        };
        backgrounds.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        );
        backgroundSpinner.setAdapter(backgrounds);
        backgroundSpinner.setPadding(dp(12), dp(4), dp(12), dp(4));
        backgroundSpinner.setBackground(rounded(FIELD, 13, LINE));
        root.addView(backgroundSpinner, matchWrap());

        previewFinalButton = addButton(
            getString(R.string.preview_final_result),
            view -> previewSelectedVideo(true)
        );
        styleSecondary(previewFinalButton);
        previewFinalButton.setEnabled(false);
        previewFinalButton.setAlpha(0.45f);
        LinearLayout.LayoutParams finalPreviewParams = matchWrap();
        finalPreviewParams.topMargin = dp(10);
        root.addView(previewFinalButton, finalPreviewParams);

        makerProgress = new ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        );
        makerProgress.setMax(100);
        makerProgress.setVisibility(View.GONE);
        styleProgress(makerProgress);
        LinearLayout.LayoutParams progressParams = matchWrap();
        progressParams.topMargin = dp(8);
        root.addView(makerProgress, progressParams);

        renderVideoButton = addButton(
            getString(R.string.render_queue),
            view -> renderSelectedMedia()
        );
        stylePrimary(renderVideoButton);
        renderVideoButton.setEnabled(false);
        renderVideoButton.setAlpha(0.45f);
        LinearLayout.LayoutParams renderParams = matchWrap();
        renderParams.topMargin = dp(8);
        root.addView(renderVideoButton, renderParams);

        makerStatus = text(
            getString(R.string.maker_initial_status),
            14,
            MUTED
        );
        LinearLayout.LayoutParams makerStatusParams = matchWrap();
        makerStatusParams.topMargin = dp(9);
        root.addView(makerStatus, makerStatusParams);

        makerQueueCount = text(
            getString(R.string.pack_queue, 0),
            18,
            TEXT
        );
        makerQueueCount.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams queueParams = matchWrap();
        queueParams.topMargin = dp(20);
        root.addView(makerQueueCount, queueParams);

        LinearLayout queueButtons = new LinearLayout(this);
        queueButtons.setOrientation(LinearLayout.HORIZONTAL);
        Button removeLast = addButton(
            getString(R.string.remove_last),
            view -> removeLastMakerItem()
        );
        Button clear = addButton(
            getString(R.string.clear_queue),
            view -> confirmClearMakerQueue()
        );
        queueButtons.addView(
            removeLast,
            new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        );
        LinearLayout.LayoutParams clearParams =
            new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT
                ,
                1f
            );
        clearParams.leftMargin = dp(6);
        queueButtons.addView(clear, clearParams);
        root.addView(queueButtons, matchWrap());

        addFieldLabel(root, getString(R.string.add_to_pack), 14);
        makerTargetSpinner = new Spinner(this);
        makerTargetSpinner.setPadding(dp(12), dp(4), dp(12), dp(4));
        makerTargetSpinner.setBackground(rounded(FIELD, 13, LINE));
        root.addView(makerTargetSpinner, matchWrap());

        addFieldLabel(root, getString(R.string.pack_name), 14);
        makerPackTitle = new EditText(this);
        makerPackTitle.setHint(R.string.pack_name);
        makerPackTitle.setSingleLine(true);
        makerPackTitle.setText(R.string.animated_pack_default);
        styleInput(makerPackTitle);
        root.addView(makerPackTitle, matchWrap());

        addFieldLabel(root, getString(R.string.publisher), 14);
        makerPackPublisher = new EditText(this);
        makerPackPublisher.setHint(R.string.publisher);
        makerPackPublisher.setSingleLine(true);
        makerPackPublisher.setText(R.string.publisher_default);
        styleInput(makerPackPublisher);
        root.addView(makerPackPublisher, matchWrap());

        buildMakerPackButton = addButton(
            getString(R.string.build_sticker_pack),
            view -> buildMakerPack()
        );
        stylePrimary(buildMakerPackButton);
        buildMakerPackButton.setEnabled(false);
        buildMakerPackButton.setAlpha(0.45f);
        LinearLayout.LayoutParams buildPackParams = matchWrap();
        buildPackParams.topMargin = dp(14);
        root.addView(buildMakerPackButton, buildPackParams);

        bindMakerControls();
        bindMakerModeAndTarget();
    }

    private TextView addSeekControl(
        LinearLayout root,
        String label,
        SeekBar seek,
        String initialValue
    ) {
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        TextView name = text(label, 14, MUTED);
        TextView value = text(
            initialValue,
            14,
            TEXT
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
        seek.setProgressTintList(ColorStateList.valueOf(MINT));
        seek.setThumbTintList(ColorStateList.valueOf(MINT));
        seek.setProgressBackgroundTintList(
            ColorStateList.valueOf(LINE)
        );
        root.addView(seek, matchWrap());
        return value;
    }

    private ImageView addTrimThumbnail(
        LinearLayout row,
        String label
    ) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(7), dp(7), dp(7), dp(7));
        card.setBackground(rounded(SURFACE_2, 13, LINE));
        TextView title = text(label, 12, MUTED);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        card.addView(title, matchWrap());
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(FIELD);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(112)
        );
        imageParams.topMargin = dp(6);
        card.addView(image, imageParams);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        );
        if (row.getChildCount() > 0) {
            cardParams.leftMargin = dp(8);
        }
        row.addView(card, cardParams);
        return image;
    }

    private ArrayAdapter<String> darkSpinnerAdapter(String[] items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            items
        ) {
            @Override
            public View getView(
                int position,
                View convertView,
                ViewGroup parent
            ) {
                TextView view = (TextView) super.getView(
                    position,
                    convertView,
                    parent
                );
                view.setTextColor(TEXT);
                return view;
            }

            @Override
            public View getDropDownView(
                int position,
                View convertView,
                ViewGroup parent
            ) {
                TextView view = (TextView) super.getDropDownView(
                    position,
                    convertView,
                    parent
                );
                view.setTextColor(TEXT);
                view.setBackgroundColor(SURFACE_2);
                view.setPadding(dp(12), dp(10), dp(12), dp(10));
                return view;
            }
        };
        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        );
        return adapter;
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
                    constrainTrimSelection();
                    updateMakerTransform();
                    scheduleTrimThumbnailRefresh();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    requestPreviewFrame();
                    requestTrimThumbnails();
                }
            }
        );
        endSeek.setOnSeekBarChangeListener(
            new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(
                    SeekBar seekBar,
                    int progress,
                    boolean fromUser
                ) {
                    constrainTrimSelection();
                    updateMakerTransform();
                    scheduleTrimThumbnailRefresh();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    requestTrimThumbnails();
                }
            }
        );
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
                    if (view instanceof TextView) {
                        ((TextView) view).setTextColor(TEXT);
                    }
                    updateMakerTransform();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            }
        );
        speedSpinner.setOnItemSelectedListener(
            new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(
                    AdapterView<?> parent,
                    View view,
                    int position,
                    long id
                ) {
                    if (view instanceof TextView) {
                        ((TextView) view).setTextColor(TEXT);
                    }
                    constrainTrimSelection();
                    updateMakerTransform();
                    scheduleTrimThumbnailRefresh();
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

    private void bindMakerModeAndTarget() {
        makerModeSpinner.setOnItemSelectedListener(
            new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(
                    AdapterView<?> parent,
                    View view,
                    int position,
                    long id
                ) {
                    if (view instanceof TextView) {
                        ((TextView) view).setTextColor(TEXT);
                    }
                    selectedMakerTargetId = "";
                    updateMakerMode();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            }
        );
        makerTargetSpinner.setOnItemSelectedListener(
            new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(
                    AdapterView<?> parent,
                    View view,
                    int position,
                    long id
                ) {
                    if (view instanceof TextView) {
                        ((TextView) view).setTextColor(TEXT);
                    }
                    Pack target = position > 0
                        && position - 1 < makerTargetPacks.size()
                        ? makerTargetPacks.get(position - 1)
                        : null;
                    selectedMakerTargetId = target == null
                        ? ""
                        : target.identifier;
                    if (target != null) {
                        makerPackTitle.setText(target.name);
                        makerPackPublisher.setText(target.publisher);
                    }
                    refreshMakerQueue();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            }
        );
        updateMakerMode();
    }

    private boolean makerAnimatedMode() {
        return makerModeSpinner == null
            || makerModeSpinner.getSelectedItemPosition() == 0;
    }

    private void updateMakerMode() {
        boolean animated = makerAnimatedMode();
        makerVideoControls.setVisibility(
            animated ? View.VISIBLE : View.GONE
        );
        previewFinalButton.setVisibility(
            animated ? View.VISIBLE : View.GONE
        );
        chooseVideoButton.setText(
            animated ? R.string.choose_video : R.string.choose_image
        );
        renderVideoButton.setText(
            animated
                ? R.string.render_queue
                : R.string.render_static_queue
        );
        makerPackTitle.setText(
            animated
                ? R.string.animated_pack_default
                : R.string.static_pack_default
        );
        videoInfo.setText(
            animated ? R.string.no_video : R.string.no_image
        );
        videoPreview.setEmptyMessage(
            animated ? R.string.no_video : R.string.no_image
        );
        refreshMakerTargets();
        refreshMakerQueue();
        setMakerBusy(false);
        videoPreview.setSource(null);
        if (animated && selectedVideoUri != null) {
            loadSelectedVideo();
        } else if (!animated && selectedImageUri != null) {
            loadSelectedImage();
        }
    }

    private void refreshMakerTargets() {
        if (makerTargetSpinner == null) {
            return;
        }
        boolean animated = makerAnimatedMode();
        String preserve = selectedMakerTargetId;
        makerTargetPacks.clear();
        ArrayList<String> labels = new ArrayList<>();
        labels.add(getString(R.string.create_new_pack));
        int selected = 0;
        for (Pack pack : PackStore.list(this)) {
            if (pack.animated != animated || pack.stickers.size() >= 30) {
                continue;
            }
            makerTargetPacks.add(pack);
            labels.add(
                getString(
                    R.string.existing_pack_option,
                    pack.name,
                    pack.stickers.size()
                )
            );
            if (pack.identifier.equals(preserve)) {
                selected = makerTargetPacks.size();
            }
        }
        makerTargetSpinner.setAdapter(
            darkSpinnerAdapter(labels.toArray(new String[0]))
        );
        makerTargetSpinner.setSelection(selected);
        if (selected == 0) {
            selectedMakerTargetId = "";
        }
    }

    private Pack selectedMakerTarget() {
        if (selectedMakerTargetId.isEmpty()) {
            return null;
        }
        Pack pack = PackStore.find(this, selectedMakerTargetId);
        return pack != null
            && pack.animated == makerAnimatedMode()
            && pack.stickers.size() < 30
            ? pack
            : null;
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
        long startMs = startSeek.getProgress();
        long endMs = Math.max(startMs, endSeek.getProgress());
        long sourceDurationMs = Math.max(0, endMs - startMs);
        float speed = selectedPlaybackSpeed();
        startValue.setText(formatTimestamp(startMs));
        endValue.setText(formatTimestamp(endMs));
        selectionRange.setText(
            getString(
                R.string.selected_range,
                formatTimestamp(startMs),
                formatTimestamp(endMs)
            )
        );
        adjustedDuration.setText(
            getString(
                R.string.adjusted_duration,
                formatTimestamp(Math.round(sourceDurationMs / speed)),
                speedLabel(speed)
            )
        );
        scaleValue.setText(
            getString(
                R.string.scale_percent,
                Math.round(scale * 100f)
            )
        );
        positionValue.setText(
            getString(
                R.string.position_value,
                Math.round(offsetX * 100f),
                Math.round(offsetY * 100f)
            )
        );
        videoPreview.setTransform(scale, offsetX, offsetY);
        videoPreview.setPreviewBackground(selectedBackground());
    }

    private void constrainTrimSelection() {
        if (adjustingTrimControls || selectedVideoDurationMs <= 0) {
            return;
        }
        adjustingTrimControls = true;
        try {
            float speed = selectedPlaybackSpeed();
            int minimumDuration = Math.max(1, (int) Math.ceil(200 * speed));
            int maximumDuration = Math.max(
                minimumDuration,
                (int) Math.floor(3_000 * speed)
            );
            int videoDuration = (int) Math.min(
                Integer.MAX_VALUE,
                selectedVideoDurationMs
            );
            startSeek.setMax(Math.max(0, videoDuration - minimumDuration));
            int start = Math.min(startSeek.getProgress(), startSeek.getMax());
            if (startSeek.getProgress() != start) {
                startSeek.setProgress(start);
            }
            endSeek.setMax(videoDuration);
            int minimumEnd = Math.min(videoDuration, start + minimumDuration);
            int maximumEnd = Math.min(videoDuration, start + maximumDuration);
            int end = Math.max(minimumEnd, endSeek.getProgress());
            end = Math.min(maximumEnd, end);
            if (endSeek.getProgress() != end) {
                endSeek.setProgress(end);
            }
        } finally {
            adjustingTrimControls = false;
        }
    }

    private float selectedPlaybackSpeed() {
        if (speedSpinner == null) {
            return 1f;
        }
        int position = Math.max(
            0,
            Math.min(
                PLAYBACK_SPEEDS.length - 1,
                speedSpinner.getSelectedItemPosition()
            )
        );
        return PLAYBACK_SPEEDS[position];
    }

    private void scheduleTrimThumbnailRefresh() {
        if (selectedVideoDurationMs <= 0) {
            return;
        }
        mainHandler.removeCallbacks(thumbnailRefresh);
        mainHandler.postDelayed(thumbnailRefresh, 180);
    }

    private VideoStickerSettings currentMakerSettings() {
        return new VideoStickerSettings(
            startSeek.getProgress(),
            endSeek.getProgress() - startSeek.getProgress(),
            selectedPlaybackSpeed(),
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

    private void pickMakerMedia() {
        if (makerAnimatedMode()) {
            pickVideo();
        } else {
            pickImage();
        }
    }

    private void pickVideo() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_VIDEO);
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_IMAGE);
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
        makerStatus.setText(R.string.reading_video);
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
                    startSeek.setProgress(0);
                    endSeek.setMax(
                        (int) Math.min(Integer.MAX_VALUE, probe.durationMs)
                    );
                    endSeek.setProgress(
                        (int) Math.min(3_000, probe.durationMs)
                    );
                    constrainTrimSelection();
                    videoPreview.setSource(probe.preview);
                    videoInfo.setText(
                        getString(
                            R.string.video_info,
                            probe.width,
                            probe.height,
                            formatSeconds(probe.durationMs)
                        )
                    );
                    makerStatus.setText(R.string.video_gesture_help);
                    setMakerBusy(false);
                    renderVideoButton.setEnabled(true);
                    renderVideoButton.setAlpha(1f);
                    updateMakerTransform();
                    requestTrimThumbnails();
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    makerStatus.setText(
                        getString(
                            R.string.video_failed,
                            friendly(error)
                        )
                    );
                    setMakerBusy(false);
                    renderVideoButton.setEnabled(false);
                    renderVideoButton.setAlpha(0.45f);
                });
            }
        });
    }

    private void loadSelectedImage() {
        if (selectedImageUri == null) {
            return;
        }
        Uri uri = selectedImageUri;
        setMakerBusy(true);
        makerStatus.setText(R.string.reading_image);
        executor.execute(() -> {
            try {
                Bitmap preview = StaticStickerRenderer.preview(this, uri);
                int width = preview.getWidth();
                int height = preview.getHeight();
                mainHandler.post(() -> {
                    if (!uri.equals(selectedImageUri)) {
                        preview.recycle();
                        return;
                    }
                    videoPreview.setSource(preview);
                    videoInfo.setText(
                        getString(R.string.image_info, width, height)
                    );
                    makerStatus.setText(R.string.image_gesture_help);
                    setMakerBusy(false);
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    makerStatus.setText(
                        getString(
                            R.string.image_failed,
                            friendly(error)
                        )
                    );
                    setMakerBusy(false);
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
                        getString(
                            R.string.preview_failed,
                            friendly(error)
                        )
                    )
                );
            }
        });
    }

    private void requestTrimThumbnails() {
        mainHandler.removeCallbacks(thumbnailRefresh);
        if (selectedVideoUri == null || selectedVideoDurationMs <= 0) {
            return;
        }
        Uri uri = selectedVideoUri;
        long startMs = startSeek.getProgress();
        long endMs = Math.max(startMs, endSeek.getProgress() - 1L);
        int generation = ++thumbnailGeneration;
        executor.execute(() -> {
            Bitmap startFrame = null;
            Bitmap endFrame = null;
            try {
                startFrame = VideoStickerRenderer.previewAt(
                    this,
                    uri,
                    startMs
                );
                endFrame = VideoStickerRenderer.previewAt(
                    this,
                    uri,
                    endMs
                );
                Bitmap readyStart = startFrame;
                Bitmap readyEnd = endFrame;
                mainHandler.post(() -> {
                    if (
                        generation != thumbnailGeneration
                        || !uri.equals(selectedVideoUri)
                    ) {
                        readyStart.recycle();
                        readyEnd.recycle();
                        return;
                    }
                    replaceTrimThumbnails(readyStart, readyEnd);
                });
            } catch (IOException error) {
                if (startFrame != null && !startFrame.isRecycled()) {
                    startFrame.recycle();
                }
                if (endFrame != null && !endFrame.isRecycled()) {
                    endFrame.recycle();
                }
                mainHandler.post(() -> makerStatus.setText(
                    getString(
                        R.string.preview_failed,
                        friendly(error)
                    )
                ));
            }
        });
    }

    private void replaceTrimThumbnails(Bitmap start, Bitmap end) {
        recycleThumbnailBitmaps();
        startThumbnailBitmap = start;
        endThumbnailBitmap = end;
        startThumbnail.setImageBitmap(start);
        endThumbnail.setImageBitmap(end);
    }

    private void recycleThumbnailBitmaps() {
        if (
            startThumbnailBitmap != null
            && !startThumbnailBitmap.isRecycled()
        ) {
            startThumbnailBitmap.recycle();
        }
        if (
            endThumbnailBitmap != null
            && endThumbnailBitmap != startThumbnailBitmap
            && !endThumbnailBitmap.isRecycled()
        ) {
            endThumbnailBitmap.recycle();
        }
        startThumbnailBitmap = null;
        endThumbnailBitmap = null;
    }

    private void previewSelectedVideo(boolean finalPreview) {
        if (selectedVideoUri == null) {
            makerStatus.setText(R.string.choose_video_first);
            return;
        }
        VideoStickerSettings settings;
        try {
            settings = currentMakerSettings();
        } catch (IllegalArgumentException error) {
            makerStatus.setText(error.getMessage());
            return;
        }
        VideoSegmentPreviewDialog.show(
            this,
            selectedVideoUri,
            settings,
            finalPreview ? settings.playbackSpeed : 1f,
            getString(
                finalPreview
                    ? R.string.preview_final_title
                    : R.string.preview_segment_title
            )
        );
    }

    private void renderSelectedMedia() {
        if (makerAnimatedMode()) {
            renderSelectedVideo();
        } else {
            renderSelectedImage();
        }
    }

    private void renderSelectedImage() {
        if (selectedImageUri == null) {
            makerStatus.setText(R.string.choose_image_first);
            return;
        }
        Uri uri = selectedImageUri;
        float scale = (scaleSeek.getProgress() + 25) / 100f;
        float offsetX = (positionXSeek.getProgress() - 150) / 100f;
        float offsetY = (positionYSeek.getProgress() - 150) / 100f;
        VideoStickerSettings.Background background = selectedBackground();
        setMakerBusy(true);
        makerProgress.setProgress(0);
        makerProgress.setVisibility(View.VISIBLE);
        makerStatus.setText(R.string.rendering_static);
        executor.execute(() -> {
            try {
                byte[] data = StaticStickerRenderer.render(
                    this,
                    uri,
                    scale,
                    offsetX,
                    offsetY,
                    background
                );
                MakerQueue.add(this, data, false);
                mainHandler.post(() -> {
                    makerProgress.setProgress(100);
                    makerStatus.setText(
                        getString(
                            R.string.static_render_done,
                            data.length / 1024
                        )
                    );
                    setMakerBusy(false);
                    refreshMakerQueue();
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    makerStatus.setText(
                        getString(
                            R.string.render_failed,
                            friendly(error)
                        )
                    );
                    setMakerBusy(false);
                });
            }
        });
    }

    private void renderSelectedVideo() {
        if (selectedVideoUri == null) {
            makerStatus.setText(R.string.choose_video_first);
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
        makerStatus.setText(R.string.encoder_starting);
        executor.execute(() -> {
            try {
                VideoStickerRenderer.Result result =
                    VideoStickerRenderer.render(
                        this,
                        uri,
                        settings,
                        (percent, message) -> mainHandler.post(() -> {
                            makerProgress.setProgress(percent);
                            makerStatus.setText(
                                getString(
                                    R.string.rendering_percent,
                                    percent
                                )
                            );
                        })
                    );
                MakerQueue.add(this, result.data);
                mainHandler.post(() -> {
                    makerProgress.setProgress(100);
                    makerStatus.setText(
                        getString(
                            R.string.render_done,
                            result.frameCount,
                            result.fps,
                            result.quality,
                            result.data.length / 1024
                        )
                    );
                    setMakerBusy(false);
                    refreshMakerQueue();
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    makerStatus.setText(
                        getString(
                            R.string.render_failed,
                            friendly(error)
                        )
                    );
                    setMakerBusy(false);
                });
            }
        });
    }

    private void refreshMakerQueue() {
        boolean animated = makerAnimatedMode();
        List<File> items = MakerQueue.list(this, animated);
        Pack target = selectedMakerTarget();
        int existingCount = target == null ? 0 : target.stickers.size();
        makerQueueCount.setText(
            getString(R.string.pack_queue, items.size())
        );
        boolean canBuild = !items.isEmpty()
            && items.size() + existingCount <= 30;
        buildMakerPackButton.setEnabled(canBuild);
        buildMakerPackButton.setAlpha(canBuild ? 1f : 0.45f);
        buildMakerPackButton.setText(
            target == null
                ? R.string.build_sticker_pack
                : R.string.add_to_selected_pack
        );
    }

    private void removeLastMakerItem() {
        boolean animated = makerAnimatedMode();
        executor.execute(() -> {
            try {
                MakerQueue.removeLast(this, animated);
                mainHandler.post(() -> {
                    makerStatus.setText(R.string.removed_last);
                    refreshMakerQueue();
                });
            } catch (IOException error) {
                mainHandler.post(() ->
                    makerStatus.setText(
                        getString(
                            R.string.queue_failed,
                            friendly(error)
                        )
                    )
                );
            }
        });
    }

    private void confirmClearMakerQueue() {
        boolean animated = makerAnimatedMode();
        new AlertDialog.Builder(this)
            .setTitle(R.string.clear_queue_title)
            .setMessage(R.string.clear_queue_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.clear, (dialog, which) ->
                executor.execute(() -> {
                    try {
                        MakerQueue.clear(this, animated);
                        mainHandler.post(() -> {
                            makerStatus.setText(R.string.queue_cleared);
                            refreshMakerQueue();
                        });
                    } catch (IOException error) {
                        mainHandler.post(() ->
                            makerStatus.setText(
                                getString(
                                    R.string.clear_failed,
                                    friendly(error)
                                )
                            )
                        );
                    }
                })
            )
            .show();
    }

    private void buildMakerPack() {
        boolean animated = makerAnimatedMode();
        List<File> sources = MakerQueue.list(this, animated);
        Pack target = selectedMakerTarget();
        int existingCount = target == null ? 0 : target.stickers.size();
        if (sources.isEmpty() || sources.size() + existingCount > 30) {
            makerStatus.setText(R.string.need_one_sticker);
            return;
        }
        String title = makerPackTitle.getText().toString();
        String publisher = makerPackPublisher.getText().toString();
        setMakerBusy(true);
        makerStatus.setText(R.string.building_pack);
        executor.execute(() -> {
            try {
                Pack pack = MakerPackBuilder.build(
                    this,
                    sources,
                    title,
                    publisher,
                    animated,
                    target
                );
                MakerQueue.clear(this, animated);
                mainHandler.post(() -> {
                    makerStatus.setText(
                        getString(
                            R.string.pack_built,
                            pack.name,
                            pack.stickers.size()
                        )
                    );
                    setMakerBusy(false);
                    selectedMakerTargetId = pack.identifier;
                    refreshMakerQueue();
                    refreshPacks();
                    refreshMakerTargets();
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    makerStatus.setText(
                        getString(
                            R.string.pack_build_failed,
                            friendly(error)
                        )
                    );
                    setMakerBusy(false);
                });
            }
        });
    }

    private void setMakerBusy(boolean busy) {
        boolean animated = makerAnimatedMode();
        boolean videoReady = animated && selectedVideoUri != null
            && selectedVideoDurationMs >= 200;
        boolean imageReady = !animated && selectedImageUri != null;
        boolean mediaReady = videoReady || imageReady;
        chooseVideoButton.setEnabled(!busy);
        makerModeSpinner.setEnabled(!busy);
        makerTargetSpinner.setEnabled(!busy);
        startSeek.setEnabled(!busy && animated);
        endSeek.setEnabled(!busy && animated);
        speedSpinner.setEnabled(!busy && animated);
        scaleSeek.setEnabled(!busy);
        positionXSeek.setEnabled(!busy);
        positionYSeek.setEnabled(!busy);
        backgroundSpinner.setEnabled(!busy);
        previewClipButton.setEnabled(!busy && videoReady);
        previewClipButton.setAlpha(!busy && videoReady ? 1f : 0.45f);
        previewFinalButton.setEnabled(!busy && videoReady);
        previewFinalButton.setAlpha(!busy && videoReady ? 1f : 0.45f);
        makerPackTitle.setEnabled(!busy);
        makerPackPublisher.setEnabled(!busy);
        renderVideoButton.setEnabled(!busy && mediaReady);
        renderVideoButton.setAlpha(
            !busy && mediaReady ? 1f : 0.45f
        );
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

    private static String formatTimestamp(long milliseconds) {
        long safe = Math.max(0, milliseconds);
        return String.format(
            java.util.Locale.ROOT,
            "%02d:%02d.%03d",
            safe / 60_000L,
            (safe / 1_000L) % 60L,
            safe % 1_000L
        );
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
        return String.format(java.util.Locale.ROOT, "%.0f×", speed);
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
                makerModeSpinner.setSelection(0);
                selectedVideoUri = uri;
                loadSelectedVideo();
            } else if (type != null && type.startsWith("image/")) {
                makerModeSpinner.setSelection(1);
                selectedImageUri = uri;
                loadSelectedImage();
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
            telegramStatus.setText(R.string.telegram_link_received);
            showTab(0);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private void importArchive(Uri uri) {
        showTab(2);
        importButton.setEnabled(false);
        status.setText(R.string.importing);
        executor.execute(() -> {
            try {
                List<Pack> importedPacks = PackImporter.importUri(this, uri);
                mainHandler.post(() -> {
                    importButton.setEnabled(true);
                    if (importedPacks.size() == 1) {
                        status.setText(
                            getString(
                                R.string.imported_one,
                                importedPacks.get(0).name
                            )
                        );
                    } else {
                        status.setText(
                            getString(
                                R.string.imported_many,
                                importedPacks.size()
                            )
                        );
                    }
                    refreshPacks();
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    importButton.setEnabled(true);
                    status.setText(
                        getString(
                            R.string.import_failed,
                            friendly(error)
                        )
                    );
                });
            }
        });
    }

    private void refreshPacks() {
        List<Pack> packs = PackStore.list(this);
        packList.removeAllViews();
        if (packs.isEmpty()) {
            TextView empty = text(
                getString(R.string.no_packs),
                15,
                MUTED
            );
            packList.addView(empty);
            refreshMakerTargets();
            return;
        }
        for (Pack pack : packs) {
            packList.addView(packCard(pack));
        }
        refreshMakerTargets();
    }

    private View packCard(Pack pack) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(rounded(SURFACE_2, 16, LINE));

        TextView name = text(pack.name, 18, TEXT);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(name);
        String kind = getString(
            pack.animated ? R.string.animated : R.string.static_kind
        );
        TextView details = text(
            getString(
                R.string.pack_details,
                pack.stickers.size(),
                kind,
                pack.publisher
            ),
            14,
            MUTED
        );
        card.addView(details);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.VERTICAL);
        buttons.setGravity(Gravity.START);
        LinearLayout.LayoutParams buttonsParams = matchWrap();
        buttonsParams.topMargin = dp(10);
        card.addView(buttons, buttonsParams);

        if (!pack.whatsappEligible()) {
            TextView warning = text(
                getString(
                    R.string.whatsapp_not_eligible,
                    Math.max(0, 3 - pack.stickers.size())
                ),
                14,
                DANGER
            );
            buttons.addView(warning, matchWrap());
            if (isInstalled(WHATSAPP)) {
                Button disabledWhatsapp = addButton(
                    getString(R.string.add_whatsapp),
                    view -> { }
                );
                styleSecondary(disabledWhatsapp);
                disabledWhatsapp.setEnabled(false);
                disabledWhatsapp.setAlpha(0.4f);
                LinearLayout.LayoutParams disabledParams = matchWrap();
                disabledParams.topMargin = dp(7);
                buttons.addView(disabledWhatsapp, disabledParams);
            }
            if (isInstalled(WHATSAPP_BUSINESS)) {
                Button disabledBusiness = addButton(
                    getString(R.string.add_business),
                    view -> { }
                );
                styleSecondary(disabledBusiness);
                disabledBusiness.setEnabled(false);
                disabledBusiness.setAlpha(0.4f);
                LinearLayout.LayoutParams disabledParams = matchWrap();
                disabledParams.topMargin = dp(7);
                buttons.addView(disabledBusiness, disabledParams);
            }
        } else {
            if (isInstalled(WHATSAPP)) {
                if (WhitelistCheck.isWhitelisted(this, pack, WHATSAPP)) {
                    buttons.addView(
                        addedLabel(getString(R.string.added_whatsapp)),
                        matchWrap()
                    );
                    Button updateWhatsapp = addButton(
                        getString(R.string.update_whatsapp),
                        view -> enablePack(pack, WHATSAPP)
                    );
                    styleSecondary(updateWhatsapp);
                    LinearLayout.LayoutParams updateParams = matchWrap();
                    updateParams.topMargin = dp(7);
                    buttons.addView(updateWhatsapp, updateParams);
                } else {
                    Button addWhatsapp = addButton(
                        getString(R.string.add_whatsapp),
                        view -> enablePack(pack, WHATSAPP)
                    );
                    stylePrimary(addWhatsapp);
                    buttons.addView(addWhatsapp, matchWrap());
                }
            }
            if (isInstalled(WHATSAPP_BUSINESS)) {
                boolean businessAdded = WhitelistCheck.isWhitelisted(
                    this,
                    pack,
                    WHATSAPP_BUSINESS
                );
                View business = businessAdded
                    ? addedLabel(getString(R.string.added_business))
                    : addButton(
                        getString(R.string.add_business),
                        view -> enablePack(pack, WHATSAPP_BUSINESS)
                    );
                LinearLayout.LayoutParams businessParams = matchWrap();
                businessParams.topMargin = dp(7);
                buttons.addView(business, businessParams);
                if (businessAdded) {
                    Button updateBusiness = addButton(
                        getString(R.string.update_business),
                        view -> enablePack(pack, WHATSAPP_BUSINESS)
                    );
                    styleSecondary(updateBusiness);
                    LinearLayout.LayoutParams updateParams = matchWrap();
                    updateParams.topMargin = dp(7);
                    buttons.addView(updateBusiness, updateParams);
                }
            }
        }
        if (
            !isInstalled(WHATSAPP)
            && !isInstalled(WHATSAPP_BUSINESS)
        ) {
            TextView missing = text(
                getString(R.string.whatsapp_missing),
                14,
                DANGER
            );
            buttons.addView(missing, matchWrap());
        }

        Button addTelegram = addButton(
            getString(R.string.add_telegram),
            view -> exportPackToTelegram(pack)
        );
        styleSecondary(addTelegram);
        LinearLayout.LayoutParams telegramParams = matchWrap();
        telegramParams.topMargin = dp(7);
        buttons.addView(addTelegram, telegramParams);

        Button delete = addButton(
            getString(R.string.delete),
            view -> confirmDelete(pack)
        );
        styleDanger(delete);
        LinearLayout.LayoutParams deleteParams = matchWrap();
        deleteParams.topMargin = dp(7);
        buttons.addView(delete, deleteParams);

        LinearLayout.LayoutParams cardParams = matchWrap();
        cardParams.bottomMargin = dp(10);
        card.setLayoutParams(cardParams);
        return card;
    }

    private void exportPackToTelegram(Pack pack) {
        showTab(2);
        setPackStatus(
            getString(R.string.telegram_export_starting, pack.name)
        );
        executor.execute(() -> {
            try {
                TelegramPackExporter.Result export =
                    TelegramPackExporter.prepare(
                        this,
                        pack,
                        (percent, number, count) -> mainHandler.post(() ->
                            setPackStatus(
                                getString(
                                    R.string.telegram_export_progress,
                                    number,
                                    count,
                                    percent
                                )
                            )
                        )
                );
                if (export.formats.contains("video")) {
                    String token = effectiveTelegramToken();
                    if (token.isEmpty()) {
                        throw new IOException(
                            getString(R.string.telegram_video_needs_token)
                        );
                    }
                    TelegramBotPackUploader.Result uploaded =
                        TelegramBotPackUploader.upload(
                            token,
                            pack.name,
                            export.files,
                            export.formats,
                            export.emojis,
                            percent -> mainHandler.post(() ->
                                setPackStatus(
                                    getString(
                                        R.string.telegram_bot_upload_progress,
                                        percent
                                    )
                                )
                            )
                        );
                    mainHandler.post(() ->
                        launchTelegramVideoPack(pack, export, uploaded)
                    );
                } else {
                    mainHandler.post(() -> launchTelegramImport(pack, export));
                }
            } catch (Exception error) {
                mainHandler.post(() ->
                    setPackStatus(
                        getString(
                            R.string.telegram_export_failed,
                            friendly(error)
                        )
                    )
                );
            }
        });
    }

    private void launchTelegramImport(
        Pack pack,
        TelegramPackExporter.Result export
    ) {
        Intent intent = new Intent(
            "org.telegram.messenger.CREATE_STICKER_PACK"
        );
        intent.setType(
            export.formats.contains("animated")
                ? "application/x-tgsticker"
                : "image/*"
        );
        intent.putParcelableArrayListExtra(
            Intent.EXTRA_STREAM,
            export.uris
        );
        intent.putStringArrayListExtra(
            "STICKER_EMOJIS",
            export.emojis
        );
        intent.putExtra("IMPORTER", getPackageName());
        ClipData clip = new ClipData(
            pack.name,
            new String[] {"application/octet-stream"},
            new ClipData.Item(export.uris.get(0))
        );
        for (int index = 1; index < export.uris.size(); index++) {
            clip.addItem(new ClipData.Item(export.uris.get(index)));
        }
        intent.setClipData(clip);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(intent);
            setPackStatus(
                getString(
                    R.string.telegram_export_opened,
                    pack.name
                )
            );
        } catch (ActivityNotFoundException error) {
            setPackStatus(getString(R.string.telegram_missing));
        }
    }

    private void launchTelegramVideoPack(
        Pack pack,
        TelegramPackExporter.Result export,
        TelegramBotPackUploader.Result uploaded
    ) {
        Intent intent = new Intent(
            Intent.ACTION_VIEW,
            Uri.parse(uploaded.link)
        );
        try {
            startActivity(intent);
            setPackStatus(
                getString(
                    R.string.telegram_video_pack_created,
                    pack.name
                )
            );
            if (export.recreatedVideo) {
                Toast.makeText(
                    this,
                    R.string.telegram_export_video_notice,
                    Toast.LENGTH_LONG
                ).show();
            }
        } catch (ActivityNotFoundException error) {
            setPackStatus(
                getString(
                    R.string.telegram_video_pack_link,
                    uploaded.link
                )
            );
        }
    }

    private void buildHandlesCard(LinearLayout root) {
        LinearLayout handles = card();
        LinearLayout.LayoutParams handlesParams = matchWrap();
        handlesParams.topMargin = dp(16);
        root.addView(handles, handlesParams);

        TextView eyebrow = text(
            getString(R.string.handles_eyebrow),
            11,
            MINT
        );
        eyebrow.setTypeface(Typeface.DEFAULT_BOLD);
        eyebrow.setLetterSpacing(0.09f);
        handles.addView(eyebrow);
        TextView title = text(
            getString(R.string.handles_title),
            22,
            TEXT
        );
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(6);
        handles.addView(title, titleParams);

        addHandleRow(handles, getString(R.string.handles_static));
        addHandleRow(handles, getString(R.string.handles_animated));
        addHandleRow(handles, getString(R.string.handles_split));
        addHandleRow(handles, getString(R.string.handles_limits));

        LinearLayout privacy = new LinearLayout(this);
        privacy.setOrientation(LinearLayout.VERTICAL);
        privacy.setPadding(dp(14), dp(12), dp(14), dp(12));
        privacy.setBackground(rounded(FIELD, 14, LINE));
        LinearLayout.LayoutParams privacyParams = matchWrap();
        privacyParams.topMargin = dp(16);
        handles.addView(privacy, privacyParams);
        TextView privacyTitle = text(
            getString(R.string.privacy_title),
            11,
            MINT
        );
        privacyTitle.setTypeface(Typeface.DEFAULT_BOLD);
        privacyTitle.setLetterSpacing(0.08f);
        privacy.addView(privacyTitle);
        TextView privacyDetail = text(
            getString(R.string.privacy_detail),
            13,
            MUTED
        );
        LinearLayout.LayoutParams detailParams = matchWrap();
        detailParams.topMargin = dp(5);
        privacy.addView(privacyDetail, detailParams);
    }

    private void addHandleRow(LinearLayout root, String value) {
        TextView row = text("✓  " + value, 14, TEXT);
        LinearLayout.LayoutParams rowParams = matchWrap();
        rowParams.topMargin = dp(10);
        root.addView(row, rowParams);
    }

    private void showTab(int tab) {
        selectedTab = Math.max(0, Math.min(2, tab));
        if (
            telegramPanel == null
            || makerPanel == null
            || packPanel == null
        ) {
            return;
        }
        telegramPanel.setVisibility(
            selectedTab == 0 ? View.VISIBLE : View.GONE
        );
        makerPanel.setVisibility(
            selectedTab == 1 ? View.VISIBLE : View.GONE
        );
        packPanel.setVisibility(
            selectedTab == 2 ? View.VISIBLE : View.GONE
        );
        styleTab(telegramTabButton, selectedTab == 0);
        styleTab(videoTabButton, selectedTab == 1);
        styleTab(packTabButton, selectedTab == 2);
    }

    private void restoreInterfaceState(Bundle state) {
        if (state == null) {
            return;
        }
        telegramLink.setText(state.getString(STATE_LINK, ""));
        telegramToken.setText(
            state.getString(STATE_TOKEN, valueOf(telegramToken))
        );
        telegramPublisher.setText(
            state.getString(
                STATE_TG_PUBLISHER,
                getString(R.string.publisher_default)
            )
        );
        makerModeSpinner.setSelection(
            Math.max(0, Math.min(1, state.getInt(STATE_MAKER_MODE, 0)))
        );
        selectedMakerTargetId = state.getString(STATE_MAKER_TARGET, "");
        refreshMakerTargets();
        makerPackTitle.setText(
            state.getString(
                STATE_MAKER_TITLE,
                getString(R.string.animated_pack_default)
            )
        );
        makerPackPublisher.setText(
            state.getString(
                STATE_MAKER_PUBLISHER,
                getString(R.string.publisher_default)
            )
        );
        showTab(state.getInt(STATE_SELECTED_TAB, 0));
        pendingAutoAddPackIds.clear();
        ArrayList<String> restoredPackIds =
            state.getStringArrayList(STATE_AUTO_ADD_IDS);
        if (restoredPackIds != null) {
            pendingAutoAddPackIds.addAll(restoredPackIds);
        }
        autoAddingPacks = state.getBoolean(
            STATE_AUTO_ADD_ACTIVE,
            false
        );
        autoAddTotal = state.getInt(STATE_AUTO_ADD_TOTAL, 0);
        autoAddCompleted = state.getInt(
            STATE_AUTO_ADD_COMPLETED,
            0
        );
        String restoredTarget = state.getString(
            STATE_AUTO_ADD_TARGET,
            WHATSAPP
        );
        autoAddTargetPackage = WHATSAPP_BUSINESS.equals(restoredTarget)
            ? WHATSAPP_BUSINESS
            : WHATSAPP;
        if (autoAddingPacks && !pendingAutoAddPackIds.isEmpty()) {
            showTelegramProgress(
                100,
                getString(R.string.whatsapp_auto_resume),
                false
            );
            telegramStatus.setText(R.string.whatsapp_auto_resume);
        }
        String video = state.getString(STATE_VIDEO, "");
        if (!video.isEmpty()) {
            selectedVideoUri = Uri.parse(video);
        }
        String image = state.getString(STATE_IMAGE, "");
        if (!image.isEmpty()) {
            selectedImageUri = Uri.parse(image);
        }
        if (makerAnimatedMode() && selectedVideoUri != null) {
            loadSelectedVideo();
        } else if (!makerAnimatedMode() && selectedImageUri != null) {
            loadSelectedImage();
        }
    }

    private void pasteInto(EditText target) {
        ClipboardManager clipboard =
            (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (
            clipboard == null
            || !clipboard.hasPrimaryClip()
            || clipboard.getPrimaryClip() == null
        ) {
            return;
        }
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            return;
        }
        CharSequence value = clip.getItemAt(0).coerceToText(this);
        if (value != null) {
            target.setText(value.toString().trim());
            target.setSelection(target.length());
        }
    }

    private void toggleTokenVisibility() {
        tokenVisible = !tokenVisible;
        telegramToken.setTransformationMethod(
            tokenVisible
                ? HideReturnsTransformationMethod.getInstance()
                : PasswordTransformationMethod.getInstance()
        );
        tokenVisibilityButton.setText(
            tokenVisible ? R.string.hide : R.string.show
        );
        telegramToken.setSelection(telegramToken.length());
    }

    private void toggleTokenCustomization() {
        tokenCustomizationVisible = !tokenCustomizationVisible;
        tokenCustomizationPanel.setVisibility(
            tokenCustomizationVisible ? View.VISIBLE : View.GONE
        );
        customizeTokenButton.setText(
            tokenCustomizationVisible
                ? R.string.hide_token_customization
                : R.string.customize_token_api
        );
        if (tokenCustomizationVisible) {
            telegramToken.requestFocus();
        }
    }

    private String effectiveTelegramToken() throws IOException {
        String custom = BotTokenStore.load(this).trim();
        return custom.isEmpty() ? DefaultBotCredential.value() : custom;
    }

    private void updateTokenSavedState(boolean saved) {
        if (tokenSavedStatus == null) {
            return;
        }
        tokenSavedStatus.setText(
            saved
                ? R.string.token_saved_message
                : (
                    DefaultBotCredential.available()
                        ? R.string.builtin_token_active
                        : R.string.no_builtin_token
                )
        );
        tokenSavedStatus.setVisibility(View.VISIBLE);
    }

    private void openBotFather() {
        try {
            startActivity(
                new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://t.me/BotFather")
                )
            );
        } catch (ActivityNotFoundException error) {
            Toast.makeText(
                this,
                getString(R.string.botfather),
                Toast.LENGTH_SHORT
            ).show();
        }
    }

    private LinearLayout card() {
        LinearLayout value = new LinearLayout(this);
        value.setOrientation(LinearLayout.VERTICAL);
        value.setPadding(dp(18), dp(18), dp(18), dp(18));
        value.setBackground(rounded(SURFACE, 24, LINE));
        return value;
    }

    private GradientDrawable rounded(
        int fill,
        int radiusDp,
        int stroke
    ) {
        GradientDrawable value = new GradientDrawable();
        value.setColor(fill);
        value.setCornerRadius(dp(radiusDp));
        if (stroke != fill) {
            value.setStroke(dp(1), stroke);
        }
        return value;
    }

    private void addFieldLabel(
        LinearLayout root,
        String label,
        int topMargin
    ) {
        TextView value = text(label, 13, MUTED);
        value.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(topMargin);
        params.bottomMargin = dp(6);
        root.addView(value, params);
    }

    private void styleInput(EditText input) {
        input.setTextColor(TEXT);
        input.setHintTextColor(DIM);
        input.setTextSize(15);
        input.setSelectAllOnFocus(false);
        input.setMinHeight(dp(52));
        input.setBackground(rounded(FIELD, 13, LINE));
        input.setPadding(dp(13), dp(10), dp(13), dp(10));
    }

    private void styleProgress(ProgressBar progress) {
        progress.setProgressTintList(ColorStateList.valueOf(MINT));
        progress.setProgressBackgroundTintList(
            ColorStateList.valueOf(LINE)
        );
    }

    private void stylePrimary(Button button) {
        styleButton(button);
        button.setTextColor(INK);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(rounded(MINT_STRONG, 14, MINT_STRONG));
        button.setMinHeight(dp(54));
    }

    private void styleSecondary(Button button) {
        styleButton(button);
        button.setTextColor(TEXT);
        button.setBackground(rounded(SURFACE_2, 12, LINE));
    }

    private void styleDanger(Button button) {
        styleButton(button);
        button.setTextColor(DANGER);
        button.setBackground(rounded(FIELD, 12, DANGER));
    }

    private void styleChip(Button button) {
        styleButton(button);
        button.setTextColor(MINT);
        button.setBackground(rounded(SURFACE_2, 999, LINE));
        button.setPadding(dp(12), dp(7), dp(12), dp(7));
    }

    private void styleTab(Button button, boolean selected) {
        styleButton(button);
        button.setTextSize(12);
        button.setTypeface(
            selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT
        );
        button.setTextColor(selected ? INK : MUTED);
        button.setBackground(
            rounded(
                selected ? MINT : FIELD,
                12,
                selected ? MINT : FIELD
            )
        );
    }

    private void styleButton(Button button) {
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(42));
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(13), dp(9), dp(13), dp(9));
    }

    private Button addButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(listener);
        styleSecondary(button);
        return button;
    }

    private TextView addedLabel(String label) {
        TextView value = text(label, 14, MINT);
        value.setGravity(Gravity.CENTER_VERTICAL);
        value.setPadding(dp(10), dp(10), dp(10), dp(10));
        value.setBackground(rounded(FIELD, 12, LINE));
        return value;
    }

    private boolean enablePack(Pack pack, String targetPackage) {
        if (!pack.whatsappEligible()) {
            Toast.makeText(
                this,
                getString(
                    R.string.whatsapp_not_eligible,
                    Math.max(0, 3 - pack.stickers.size())
                ),
                Toast.LENGTH_LONG
            ).show();
            return false;
        }
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
            return true;
        } catch (ActivityNotFoundException error) {
            Toast.makeText(
                this,
                getString(R.string.whatsapp_open_failed),
                Toast.LENGTH_LONG
            ).show();
            return false;
        }
    }

    private void confirmDelete(Pack pack) {
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_title, pack.name))
            .setMessage(R.string.delete_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete, (dialog, which) -> {
                executor.execute(() -> {
                    try {
                        PackStore.delete(this, pack.identifier);
                        mainHandler.post(() -> {
                            status.setText(
                                getString(R.string.deleted, pack.name)
                            );
                            refreshPacks();
                            refreshMakerTargets();
                        });
                    } catch (IOException error) {
                        mainHandler.post(() ->
                            status.setText(
                                getString(
                                    R.string.delete_failed,
                                    friendly(error)
                                )
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

    private LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private static String valueOf(EditText input) {
        return input == null ? "" : input.getText().toString();
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
