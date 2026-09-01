package com.tool48.tgwabridge;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import java.util.Locale;

final class AppLocale {
    private static final String PREFS = "tgwa_ui";
    private static final String KEY_LANGUAGE = "language";
    private static final String ENGLISH = "en";
    private static final String CHINESE = "zh-Hant-HK";

    private AppLocale() {
    }

    static Context wrap(Context base) {
        Locale locale = locale(base);
        Configuration configuration = new Configuration(
            base.getResources().getConfiguration()
        );
        configuration.setLocale(locale);
        configuration.setLayoutDirection(locale);
        return base.createConfigurationContext(configuration);
    }

    static boolean isChinese(Context context) {
        return language(context).startsWith("zh");
    }

    static void toggle(Activity activity) {
        String next = isChinese(activity) ? ENGLISH : CHINESE;
        preferences(activity).edit()
            .putString(KEY_LANGUAGE, next)
            .apply();
        activity.recreate();
    }

    private static Locale locale(Context context) {
        return Locale.forLanguageTag(language(context));
    }

    private static String language(Context context) {
        String saved = preferences(context).getString(KEY_LANGUAGE, "");
        if (saved != null && !saved.isEmpty()) {
            return saved;
        }
        String device = Locale.getDefault().getLanguage();
        return "zh".equalsIgnoreCase(device) ? CHINESE : ENGLISH;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
