package com.tool48.tgwabridge;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

final class WhitelistCheck {
    private static final String WHATSAPP = "com.whatsapp";

    private WhitelistCheck() {
    }

    static boolean isWhitelisted(
        Context context,
        Pack pack,
        String targetPackage
    ) {
        String provider = WHATSAPP.equals(targetPackage)
            ? "com.whatsapp.provider.sticker_whitelist_check"
            : "com.whatsapp.w4b.provider.sticker_whitelist_check";
        Uri uri = new Uri.Builder()
            .scheme("content")
            .authority(provider)
            .appendPath("is_whitelisted")
            .appendQueryParameter(
                "authority",
                BuildConfig.CONTENT_PROVIDER_AUTHORITY
            )
            .appendQueryParameter("identifier", pack.identifier)
            .build();
        try (
            Cursor cursor = context.getContentResolver().query(
                uri,
                null,
                null,
                null,
                null
            )
        ) {
            if (cursor == null || !cursor.moveToFirst()) {
                return false;
            }
            int resultColumn = cursor.getColumnIndex("result");
            return resultColumn >= 0 && cursor.getInt(resultColumn) == 1;
        } catch (RuntimeException ignored) {
            // Old WhatsApp versions may not expose the optional provider.
            return false;
        }
    }
}
