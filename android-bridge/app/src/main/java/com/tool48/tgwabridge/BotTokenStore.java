package com.tool48.tgwabridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class BotTokenStore {
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "tgwa_bot_token_v1";
    private static final String PREFERENCES = "tgwa_secure";
    private static final String VALUE_IV = "bot_token_iv";
    private static final String VALUE_DATA = "bot_token_data";

    private BotTokenStore() {
    }

    static void save(Context context, String token) throws IOException {
        String clean = token == null ? "" : token.trim();
        if (clean.isEmpty()) {
            clear(context);
            return;
        }
        try {
            Cipher cipher = Cipher.getInstance(
                KeyProperties.KEY_ALGORITHM_AES
                    + "/"
                    + KeyProperties.BLOCK_MODE_GCM
                    + "/"
                    + KeyProperties.ENCRYPTION_PADDING_NONE
            );
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] encrypted = cipher.doFinal(
                clean.getBytes(StandardCharsets.UTF_8)
            );
            preferences(context).edit()
                .putString(
                    VALUE_IV,
                    Base64.encodeToString(
                        cipher.getIV(),
                        Base64.NO_WRAP
                    )
                )
                .putString(
                    VALUE_DATA,
                    Base64.encodeToString(encrypted, Base64.NO_WRAP)
                )
                .apply();
        } catch (GeneralSecurityException error) {
            throw new IOException(
                "Android could not protect the Telegram Bot Token.",
                error
            );
        }
    }

    static String load(Context context) throws IOException {
        SharedPreferences values = preferences(context);
        String ivValue = values.getString(VALUE_IV, "");
        String dataValue = values.getString(VALUE_DATA, "");
        if (ivValue == null || dataValue == null
            || ivValue.isEmpty() || dataValue.isEmpty()) {
            return "";
        }
        try {
            byte[] iv = Base64.decode(ivValue, Base64.NO_WRAP);
            byte[] encrypted = Base64.decode(dataValue, Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance(
                KeyProperties.KEY_ALGORITHM_AES
                    + "/"
                    + KeyProperties.BLOCK_MODE_GCM
                    + "/"
                    + KeyProperties.ENCRYPTION_PADDING_NONE
            );
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                new GCMParameterSpec(128, iv)
            );
            return new String(
                cipher.doFinal(encrypted),
                StandardCharsets.UTF_8
            );
        } catch (GeneralSecurityException | IllegalArgumentException error) {
            clear(context);
            throw new IOException(
                "The saved Bot Token could not be unlocked. Enter it again.",
                error
            );
        }
    }

    static void clear(Context context) {
        preferences(context).edit()
            .remove(VALUE_IV)
            .remove(VALUE_DATA)
            .apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(
            PREFERENCES,
            Context.MODE_PRIVATE
        );
    }

    private static SecretKey key()
        throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance(KEYSTORE);
        store.load(null);
        java.security.Key existing = store.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) {
            return (SecretKey) existing;
        }
        KeyGenerator generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE
        );
        generator.init(
            new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT
                    | KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .setRandomizedEncryptionRequired(true)
                .build()
        );
        return generator.generateKey();
    }
}
