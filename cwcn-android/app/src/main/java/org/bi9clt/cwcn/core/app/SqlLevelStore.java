package org.bi9clt.cwcn.core.app;

import android.content.Context;
import android.content.SharedPreferences;

public final class SqlLevelStore {
    public static final int DEFAULT_SQL_LEVEL = 55;
    private static final String PREFS_NAME = "operate_ui";
    private static final String KEY_SQL_LEVEL = "sql_level";

    private final SharedPreferences preferences;

    public SqlLevelStore(Context context) {
        Context appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public int load() {
        return clamp(preferences.getInt(KEY_SQL_LEVEL, DEFAULT_SQL_LEVEL));
    }

    public void save(int sqlLevel) {
        preferences.edit().putInt(KEY_SQL_LEVEL, clamp(sqlLevel)).apply();
    }

    private int clamp(int sqlLevel) {
        return Math.max(0, Math.min(100, sqlLevel));
    }
}
