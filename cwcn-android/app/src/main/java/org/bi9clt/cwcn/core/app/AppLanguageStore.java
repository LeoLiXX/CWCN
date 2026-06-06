package org.bi9clt.cwcn.core.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

public final class AppLanguageStore {
    private static final String PREFS_NAME = "cwcn_app_language";
    private static final String KEY_LANGUAGE_MODE = "language_mode";

    public enum LanguageMode {
        FOLLOW_SYSTEM("system", null),
        ENGLISH("en", "en"),
        SIMPLIFIED_CHINESE("zh-Hans", "zh-Hans");

        private final String persistedValue;
        @Nullable
        private final String localeTag;

        LanguageMode(String persistedValue, @Nullable String localeTag) {
            this.persistedValue = persistedValue;
            this.localeTag = localeTag;
        }

        @NonNull
        public String persistedValue() {
            return persistedValue;
        }

        @Nullable
        public String localeTag() {
            return localeTag;
        }

        @NonNull
        public static LanguageMode fromPersistedValue(@Nullable String rawValue) {
            if (rawValue != null) {
                for (LanguageMode mode : values()) {
                    if (mode.persistedValue.equalsIgnoreCase(rawValue.trim())) {
                        return mode;
                    }
                }
            }
            return FOLLOW_SYSTEM;
        }
    }

    private final SharedPreferences preferences;

    public AppLanguageStore(Context context) {
        Context appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    public LanguageMode languageMode() {
        return LanguageMode.fromPersistedValue(preferences.getString(KEY_LANGUAGE_MODE, null));
    }

    public void setLanguageMode(@NonNull LanguageMode mode) {
        preferences.edit().putString(KEY_LANGUAGE_MODE, mode.persistedValue()).apply();
    }

    public boolean applyLanguageMode() {
        return applyLanguageMode(languageMode());
    }

    public boolean applyLanguageMode(@NonNull LanguageMode mode) {
        LocaleListCompat currentLocales = AppCompatDelegate.getApplicationLocales();
        LocaleListCompat targetLocales = mode.localeTag() == null
                ? LocaleListCompat.getEmptyLocaleList()
                : LocaleListCompat.forLanguageTags(mode.localeTag());
        if (currentLocales.equals(targetLocales)) {
            return false;
        }
        AppCompatDelegate.setApplicationLocales(targetLocales);
        return true;
    }
}
