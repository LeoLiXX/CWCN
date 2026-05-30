package org.bi9clt.cwcn.core.rx;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

public final class LiveRxTraceStore {
    private static final String PREFERENCES_NAME = "cwcn_live_rx_trace";
    private static final String KEY_LATEST_TRACE_JSON = "latest_trace_json";

    private final SharedPreferences preferences;

    public LiveRxTraceStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public synchronized void saveLatest(LiveRxTraceArtifact artifact) {
        if (artifact == null) {
            clear();
            return;
        }
        JSONObject object = new JSONObject();
        try {
            object.put("capturedAtEpochMs", artifact.capturedAtEpochMs());
            object.put("sessionLabel", artifact.sessionLabel());
            object.put("sourceLabel", artifact.sourceLabel());
            object.put("wavFilePath", artifact.wavFilePath());
            object.put("logFilePath", artifact.logFilePath());
            object.put("durationMs", artifact.durationMs());
            object.put("sampleRateHz", artifact.sampleRateHz());
            object.put("sampleCount", artifact.sampleCount());
            object.put("preferredToneFrequencyHz", artifact.preferredToneFrequencyHz());
            object.put("sqlLevel", artifact.sqlLevel());
        } catch (JSONException exception) {
            throw new IllegalStateException("Failed to serialize live RX trace artifact", exception);
        }
        preferences.edit().putString(KEY_LATEST_TRACE_JSON, object.toString()).apply();
    }

    public synchronized LiveRxTraceArtifact loadLatest() {
        String rawJson = preferences.getString(KEY_LATEST_TRACE_JSON, null);
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return null;
        }
        try {
            JSONObject object = new JSONObject(rawJson);
            return new LiveRxTraceArtifact(
                    object.optLong("capturedAtEpochMs", 0L),
                    object.optString("sessionLabel", ""),
                    object.optString("sourceLabel", ""),
                    object.optString("wavFilePath", ""),
                    object.optString("logFilePath", ""),
                    object.optLong("durationMs", 0L),
                    object.optInt("sampleRateHz", 0),
                    object.optLong("sampleCount", 0L),
                    object.has("preferredToneFrequencyHz")
                            ? object.optInt("preferredToneFrequencyHz", -1)
                            : -1,
                    object.has("sqlLevel")
                            ? object.optInt("sqlLevel", -1)
                            : object.has("sqlPercent")
                            ? object.optInt("sqlPercent", -1)
                            : -1
            );
        } catch (JSONException ignored) {
            return null;
        }
    }

    public synchronized void clear() {
        preferences.edit().remove(KEY_LATEST_TRACE_JSON).apply();
    }
}
