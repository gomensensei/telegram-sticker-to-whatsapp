package com.tool48.tgwabridge;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

public final class TelegramExportProvider extends ContentProvider {
    static final String AUTHORITY =
        "com.tool48.tgwabridge.telegramexportprovider";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode)
        throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("Telegram exports are read-only.");
        }
        File file = resolve(uri);
        if (!file.isFile()) {
            throw new FileNotFoundException("Telegram export expired.");
        }
        return ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_READ_ONLY
        );
    }

    @Override
    public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name != null && name.endsWith(".tgs")) {
            return "application/x-tgsticker";
        }
        if (name != null && name.endsWith(".webm")) {
            return "video/webm";
        }
        return "image/webp";
    }

    @Override
    public Cursor query(
        Uri uri,
        String[] projection,
        String selection,
        String[] selectionArgs,
        String sortOrder
    ) {
        File file;
        try {
            file = resolve(uri);
        } catch (FileNotFoundException error) {
            return null;
        }
        String[] columns = projection == null
            ? new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
            : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            if (OpenableColumns.DISPLAY_NAME.equals(column)) {
                row.add(file.getName());
            } else if (OpenableColumns.SIZE.equals(column)) {
                row.add(file.length());
            } else {
                row.add(null);
            }
        }
        return cursor;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(
        Uri uri,
        ContentValues values,
        String selection,
        String[] selectionArgs
    ) {
        return 0;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        if (
            getContext() == null
            || !AUTHORITY.equals(uri.getAuthority())
            || uri.getPathSegments().size() != 2
        ) {
            throw new FileNotFoundException("Invalid Telegram export URI.");
        }
        String session = uri.getPathSegments().get(0);
        String name = uri.getPathSegments().get(1);
        if (
            !session.matches("[A-Za-z0-9_-]{8,64}")
            || !name.matches("[A-Za-z0-9_.-]{1,128}")
        ) {
            throw new FileNotFoundException("Unsafe Telegram export URI.");
        }
        File root = new File(getContext().getCacheDir(), "telegram-export");
        return new File(new File(root, session), name);
    }
}
