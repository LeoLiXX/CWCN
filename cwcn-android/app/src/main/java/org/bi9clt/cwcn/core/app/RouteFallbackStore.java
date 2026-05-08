package org.bi9clt.cwcn.core.app;

import android.content.Context;
import android.content.SharedPreferences;

public final class RouteFallbackStore {
    private static final String PREFS_NAME = "cwcn_route_fallback";
    private static final String KEY_MODE = "route_fallback_mode";

    public enum Mode {
        AUTO_PHONE_FALLBACK("Auto: Mic / Audio", "When no radio is pinned, use the phone microphone for RX and phone audio for TX."),
        RADIO_ONLY("Radio only", "Do not use the phone fallback route when no rig is pinned.");

        private final String displayName;
        private final String description;

        Mode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String displayName() {
            return displayName;
        }

        public String description() {
            return description;
        }

        @Override
        public String toString() {
            return displayName;
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
