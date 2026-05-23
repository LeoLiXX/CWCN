package org.bi9clt.cwcn.core.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.StringRes;

import org.bi9clt.cwcn.R;

public final class RouteFallbackStore {
    private static final String PREFS_NAME = "cwcn_route_fallback";
    private static final String KEY_MODE = "route_fallback_mode";

    public enum Mode {
        AUTO_PHONE_FALLBACK(
                R.string.settings_route_fallback_mode_auto_phone_fallback,
                R.string.settings_route_fallback_mode_auto_phone_fallback_description
        ),
        RADIO_ONLY(
                R.string.settings_route_fallback_mode_radio_only,
                R.string.settings_route_fallback_mode_radio_only_description
        );

        private final int displayNameResId;
        private final int descriptionResId;

        Mode(@StringRes int displayNameResId, @StringRes int descriptionResId) {
            this.displayNameResId = displayNameResId;
            this.descriptionResId = descriptionResId;
        }

        @StringRes
        public int displayNameResId() {
            return displayNameResId;
        }

        @StringRes
        public int descriptionResId() {
            return descriptionResId;
        }
    }

    private final SharedPreferences preferences;

    public RouteFallbackStore(Context context) {
        Context appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public Mode mode() {
        String stored = preferences.getString(KEY_MODE, Mode.AUTO_PHONE_FALLBACK.name());
        try {
            return Mode.valueOf(stored);
        } catch (IllegalArgumentException exception) {
            return Mode.AUTO_PHONE_FALLBACK;
        }
    }

    public void setMode(Mode mode) {
        preferences.edit().putString(KEY_MODE, mode == null ? Mode.AUTO_PHONE_FALLBACK.name() : mode.name()).apply();
    }

    public boolean usePhoneFallback() {
        return mode() == Mode.AUTO_PHONE_FALLBACK;
    }
}
