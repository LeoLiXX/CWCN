package org.bi9clt.cwcn.core.app;

import android.content.Context;
import android.content.SharedPreferences;

public final class SqlLevelStore {
    public static final int DEFAULT_SQL_LEVEL = 600;
    public static final int MIN_SQL_LEVEL = 100;
    public static final int DEFAULT_SQL_DISPLAY_MAX = 1200;
    public static final int LEGACY_SQL_DISPLAY_MAX = 2000;
    public static final int MAX_SQL_LEVEL = 20000;
    private static final double SQL_DISPLAY_EXPAND_TRIGGER_RATIO = 0.80d;
    private static final double SQL_DISPLAY_SCALE_FACTOR = 1.20d;
    private static final String PREFS_NAME = "operate_ui";
    private static final String KEY_SQL_LEVEL = "sql_level";
    private static final String KEY_SQL_DISPLAY_MAX = "sql_display_max";

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

    public int loadDisplayMax(int sqlLevel) {
        int storedDisplayMax = preferences.getInt(KEY_SQL_DISPLAY_MAX, DEFAULT_SQL_DISPLAY_MAX);
        if (storedDisplayMax == LEGACY_SQL_DISPLAY_MAX) {
            storedDisplayMax = DEFAULT_SQL_DISPLAY_MAX;
        }
        return ensureDisplayMaxCoversLevel(storedDisplayMax, sqlLevel);
    }

    public void saveDisplayMax(int displayMax) {
        preferences.edit().putInt(KEY_SQL_DISPLAY_MAX, clampDisplayMax(displayMax)).apply();
    }

    private int clamp(int sqlLevel) {
        return Math.max(MIN_SQL_LEVEL, Math.min(MAX_SQL_LEVEL, sqlLevel));
    }

    public static int clampDisplayMax(int displayMax) {
        return Math.max(DEFAULT_SQL_DISPLAY_MAX, Math.min(MAX_SQL_LEVEL, displayMax));
    }

    public static int ensureDisplayMaxCoversLevel(int displayMax, int sqlLevel) {
        int adjustedDisplayMax = clampDisplayMax(displayMax);
        int clampedSqlLevel = Math.max(MIN_SQL_LEVEL, Math.min(MAX_SQL_LEVEL, sqlLevel));
        while (clampedSqlLevel > adjustedDisplayMax && adjustedDisplayMax < MAX_SQL_LEVEL) {
            int nextDisplayMax = expandDisplayMax(adjustedDisplayMax);
            if (nextDisplayMax == adjustedDisplayMax) {
                break;
            }
            adjustedDisplayMax = nextDisplayMax;
        }
        return adjustedDisplayMax;
    }

    public static int adjustDisplayMaxForReleasedLevel(int currentDisplayMax, int releasedSqlLevel) {
        int adjustedDisplayMax = ensureDisplayMaxCoversLevel(currentDisplayMax, releasedSqlLevel);
        int clampedSqlLevel = Math.max(MIN_SQL_LEVEL, Math.min(MAX_SQL_LEVEL, releasedSqlLevel));
        int expandTriggerLevel = (int) Math.floor(adjustedDisplayMax * SQL_DISPLAY_EXPAND_TRIGGER_RATIO);
        if (clampedSqlLevel >= expandTriggerLevel && adjustedDisplayMax < MAX_SQL_LEVEL) {
            return expandDisplayMax(adjustedDisplayMax);
        }
        while (adjustedDisplayMax > DEFAULT_SQL_DISPLAY_MAX) {
            int smallerDisplayMax = shrinkDisplayMax(adjustedDisplayMax);
            if (smallerDisplayMax >= adjustedDisplayMax) {
                break;
            }
            int smallerExpandTriggerLevel =
                    (int) Math.floor(smallerDisplayMax * SQL_DISPLAY_EXPAND_TRIGGER_RATIO);
            if (clampedSqlLevel <= smallerExpandTriggerLevel) {
                adjustedDisplayMax = smallerDisplayMax;
                continue;
            }
            break;
        }
        return adjustedDisplayMax;
    }

    private static int expandDisplayMax(int displayMax) {
        int clampedDisplayMax = clampDisplayMax(displayMax);
        if (clampedDisplayMax >= MAX_SQL_LEVEL) {
            return MAX_SQL_LEVEL;
        }
        return clampDisplayMax((int) Math.ceil(clampedDisplayMax * SQL_DISPLAY_SCALE_FACTOR));
    }

    private static int shrinkDisplayMax(int displayMax) {
        int clampedDisplayMax = clampDisplayMax(displayMax);
        if (clampedDisplayMax <= DEFAULT_SQL_DISPLAY_MAX) {
            return DEFAULT_SQL_DISPLAY_MAX;
        }
        return clampDisplayMax((int) Math.floor(clampedDisplayMax / SQL_DISPLAY_SCALE_FACTOR));
    }
}
