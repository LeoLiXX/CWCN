package org.bi9clt.cwcn.core.app;

import android.content.Context;
import android.content.SharedPreferences;

public final class DeveloperModeStore {
    private static final String PREFS_NAME = "cwcn_app_mode";
    private static final String KEY_DEVELOPER_MODE = "developer_mode_enabled";

    private final SharedPreferences preferences;

    public DeveloperModeStore(Context context) {
        Context appContext = context.getApplicationContext();
        this.preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isEnabled() {
        return preferences.getBoolean(KEY_DEVELOPER_MODE, false);
    }

    public void setEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_DEVELOPER_MODE, enabled).apply();
    }

    public boolean toggle() {
        boolean enabled = !isEnabled();
        setEnabled(enabled);
        return enabled;
    }
}
