package org.bi9clt.cwcn.core.app;

import android.content.Context;
import android.content.SharedPreferences;

public final class StationProfileStore {
    private static final String PREFS_NAME = "cwcn_station_profile";
    private static final String KEY_STATION_CALLSIGN = "station_callsign";
    private static final String KEY_OPERATOR_NAME = "operator_name";
    private static final String KEY_QTH = "station_qth";
    private static final String KEY_MAIDENHEAD_GRID = "maidenhead_grid";
    private static final String KEY_RIG_DESCRIPTION = "rig_description";
    private static final String KEY_ANTENNA_DESCRIPTION = "antenna_description";
    private static final String KEY_WEATHER_DESCRIPTION = "weather_description";

    private final SharedPreferences preferences;

    public StationProfileStore(Context context) {
        Context appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String stationCallsign() {
        return normalize(preferences.getString(KEY_STATION_CALLSIGN, null));
    }

    public String operatorName() {
        return normalize(preferences.getString(KEY_OPERATOR_NAME, null));
    }

    public String qth() {
        return normalize(preferences.getString(KEY_QTH, null));
    }

    public String maidenheadGrid() {
        return normalize(preferences.getString(KEY_MAIDENHEAD_GRID, null));
    }

    public String rigDescription() {
        return normalize(preferences.getString(KEY_RIG_DESCRIPTION, null));
    }

    public String antennaDescription() {
        return normalize(preferences.getString(KEY_ANTENNA_DESCRIPTION, null));
    }

    public String weatherDescription() {
        return normalize(preferences.getString(KEY_WEATHER_DESCRIPTION, null));
    }

    public void save(
            String stationCallsign,
            String operatorName,
            String qth,
            String maidenheadGrid,
            String rigDescription,
            String antennaDescription,
            String weatherDescription
    ) {
        SharedPreferences.Editor editor = preferences.edit();
        putOptionalString(editor, KEY_STATION_CALLSIGN, stationCallsign);
        putOptionalString(editor, KEY_OPERATOR_NAME, operatorName);
        putOptionalString(editor, KEY_QTH, qth);
        putOptionalString(editor, KEY_MAIDENHEAD_GRID, maidenheadGrid);
        putOptionalString(editor, KEY_RIG_DESCRIPTION, rigDescription);
        putOptionalString(editor, KEY_ANTENNA_DESCRIPTION, antennaDescription);
        putOptionalString(editor, KEY_WEATHER_DESCRIPTION, weatherDescription);
        editor.apply();
    }

    public void clear() {
        preferences.edit()
                .remove(KEY_STATION_CALLSIGN)
                .remove(KEY_OPERATOR_NAME)
                .remove(KEY_QTH)
                .remove(KEY_MAIDENHEAD_GRID)
                .remove(KEY_RIG_DESCRIPTION)
                .remove(KEY_ANTENNA_DESCRIPTION)
                .remove(KEY_WEATHER_DESCRIPTION)
                .apply();
    }

    private void putOptionalString(SharedPreferences.Editor editor, String key, String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            editor.remove(key);
            return;
        }
        editor.putString(key, normalized);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
