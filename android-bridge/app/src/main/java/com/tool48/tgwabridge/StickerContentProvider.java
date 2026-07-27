package com.tool48.tgwabridge;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import org.json.JSONArray;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Locale;

public final class StickerContentProvider extends ContentProvider {
    static final String AUTHORITY = BuildConfig.CONTENT_PROVIDER_AUTHORITY;
    static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);

    private static final String[] PACK_COLUMNS = {
        "sticker_pack_identifier",
        "sticker_pack_name",
        "sticker_pack_publisher",
        "sticker_pack_icon",
        "android_play_store_link",
        "ios_app_download_link",
        "sticker_pack_publisher_email",
        "sticker_pack_publisher_website",
        "sticker_pack_privacy_policy_website",
        "sticker_pack_license_agreement_website",
        "image_data_version",
        "whatsapp_will_not_cache_stickers",
        "animated_sticker_pack"
    };

    private static final String[] STICKER_COLUMNS = {
        "sticker_file_name",
        "sticker_emoji",
        "sticker_accessibility_text"
    };

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(
        Uri uri,
        String[] projection,
        String selection,
        String[] selectionArgs,
        String sortOrder
    ) {
        Context context = attachedContext();
        List<String> segments = uri.getPathSegments();
        MatrixCursor cursor;
        if (segments.size() == 1 && segments.get(0).equals("metadata")) {
            cursor = new MatrixCursor(PACK_COLUMNS);
            for (Pack pack : PackStore.list(context)) {
                addPack(cursor, pack);
            }
        } else if (
            segments.size() == 2
            && segments.get(0).equals("metadata")
        ) {
            cursor = new MatrixCursor(PACK_COLUMNS);
            Pack pack = PackStore.find(context, segments.get(1));
            if (pack != null) {
                addPack(cursor, pack);
            }
        } else if (
            segments.size() == 2
            && segments.get(0).equals("stickers")
        ) {
            cursor = new MatrixCursor(STICKER_COLUMNS);
            Pack pack = PackStore.find(context, segments.get(1));
            if (pack != null) {
                for (Sticker sticker : pack.stickers) {
                    cursor.addRow(new Object[] {
                        sticker.fileName,
                        new JSONArray(sticker.emojis).toString(),
                        sticker.accessibilityText
                    });
                }
            }
        } else {
            throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
        cursor.setNotificationUri(context.getContentResolver(), uri);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        List<String> segments = uri.getPathSegments();
        if (
            !segments.isEmpty()
            && segments.get(0).equals("stickers_asset")
        ) {
            String name = segments.get(
                segments.size() - 1
            ).toLowerCase(Locale.ROOT);
            return name.endsWith(".png") ? "image/png" : "image/webp";
        }
        if (!segments.isEmpty() && segments.get(0).equals("metadata")) {
            return "vnd.android.cursor.dir/vnd."
                + AUTHORITY
                + ".metadata";
        }
        return "vnd.android.cursor.dir/vnd."
            + AUTHORITY
            + ".stickers";
    }

    @Override
    public AssetFileDescriptor openAssetFile(Uri uri, String mode)
        throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("Read-only provider.");
        }
        List<String> segments = uri.getPathSegments();
        if (
            segments.size() != 3
            || !segments.get(0).equals("stickers_asset")
        ) {
            throw new FileNotFoundException("Unsupported asset URI.");
        }
        Pack pack = PackStore.find(attachedContext(), segments.get(1));
        if (pack == null) {
            throw new FileNotFoundException("Unknown sticker pack.");
        }
        String fileName = segments.get(2);
        boolean allowed = fileName.equals(pack.trayImageFile);
        if (!allowed) {
            for (Sticker sticker : pack.stickers) {
                if (fileName.equals(sticker.fileName)) {
                    allowed = true;
                    break;
                }
            }
        }
        if (!allowed || !PackStore.safeName(fileName)) {
            throw new FileNotFoundException("Unknown sticker asset.");
        }
        File file = new File(
            PackStore.packDirectory(attachedContext(), pack.identifier),
            fileName
        );
        if (!file.isFile()) {
            throw new FileNotFoundException("Sticker asset is missing.");
        }
        ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_READ_ONLY
        );
        return new AssetFileDescriptor(
            descriptor,
            0,
            AssetFileDescriptor.UNKNOWN_LENGTH
        );
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only provider.");
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Read-only provider.");
    }

    @Override
    public int update(
        Uri uri,
        ContentValues values,
        String selection,
        String[] selectionArgs
    ) {
        throw new UnsupportedOperationException("Read-only provider.");
    }

    private Context attachedContext() {
        Context context = getContext();
        if (context == null) {
            throw new IllegalStateException("Provider is not attached.");
        }
        return context;
    }

    private static void addPack(MatrixCursor cursor, Pack pack) {
        cursor.addRow(new Object[] {
            pack.identifier,
            pack.name,
            pack.publisher,
            pack.trayImageFile,
            "",
            "",
            "",
            "",
            "",
            "",
            pack.imageDataVersion,
            0,
            pack.animated ? 1 : 0
        });
    }
}
