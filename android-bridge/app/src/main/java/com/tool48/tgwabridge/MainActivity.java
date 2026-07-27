package com.tool48.tgwabridge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int PICK_ARCHIVE = 48;
    private static final int ENABLE_PACK = 200;
    private static final String WHATSAPP = "com.whatsapp";
    private static final String WHATSAPP_BUSINESS = "com.whatsapp.w4b";

    private final ExecutorService executor =
        Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private LinearLayout packList;
    private TextView status;
    private Button importButton;

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
            try {
                getContentResolver().takePersistableUriPermission(
                    uri,
                    data.getFlags()
                        & (
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                );
            } catch (SecurityException ignored) {
                // The immediate import still has a temporary read grant.
            }
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
            "TGWA Bridge",
            28,
            Color.rgb(26, 35, 50)
        );
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView intro = text(
            "Open a .wastickers file, then tap Add to WhatsApp. "
                + "The file stays on this phone.",
            16,
            Color.rgb(79, 89, 105)
        );
        LinearLayout.LayoutParams introParams = matchWrap();
        introParams.topMargin = dp(8);
        root.addView(intro, introParams);

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
            "Imported packs",
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
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            uri = intent.getData();
        } else if (Intent.ACTION_SEND.equals(intent.getAction())) {
            @SuppressWarnings("deprecation")
            Uri shared = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            uri = shared;
        }
        if (uri != null) {
            importArchive(uri);
            intent.setAction(null);
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
                "No packs yet. Download one from the desktop tool and open it "
                    + "with TGWA Bridge.",
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
                "This removes the imported copy from TGWA Bridge. "
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
