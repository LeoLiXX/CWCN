package org.bi9clt.cwcn.core.rx;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

public final class RxSessionStore {
    private static final String PREFERENCES_NAME = "cwcn_rx_session";
    private static final String KEY_SNAPSHOT_JSON = "snapshot_json";

    private final SharedPreferences preferences;

    public RxSessionStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public synchronized void save(RxSessionSnapshot snapshot) {
        if (snapshot == null) {
            clear();
            return;
        }
        JSONObject object = new JSONObject();
        try {
            object.put("updatedAtEpochMs", snapshot.updatedAtEpochMs());
            object.put("sourceLabel", snapshot.sourceLabel());
            object.put("captureState", snapshot.captureState());
            object.put("captureActive", snapshot.captureActive());
            object.put("preferredToneFrequencyHz", snapshot.preferredToneFrequencyHz());
            object.put("targetToneFrequencyHz", snapshot.targetToneFrequencyHz());
            object.put("effectiveToneFrequencyHz", snapshot.effectiveToneFrequencyHz());
            object.put("estimatedWpm", snapshot.estimatedWpm());
            object.put("stableEstimatedWpm", snapshot.stableEstimatedWpm());
            object.put("rawText", snapshot.rawText());
            object.put("normalizedText", snapshot.normalizedText());
            object.put("phaseDisplayName", snapshot.phaseDisplayName());
            object.put("remoteCallsign", snapshot.remoteCallsign());
            object.put("readyForDraftConfirmation", snapshot.readyForDraftConfirmation());
            object.put("needManualReview", snapshot.needManualReview());
            object.put("inputHealthLabel", snapshot.inputHealthLabel());
            object.put("inputHealthHint", snapshot.inputHealthHint());
            object.put("inputLevelHot", snapshot.inputLevelHot());
            object.put("inputLevelClipping", snapshot.inputLevelClipping());
            object.put("developerFrontEndSummary", snapshot.developerFrontEndSummary());
        } catch (JSONException exception) {
            throw new IllegalStateException("Failed to serialize RX session snapshot", exception);
        }
        preferences.edit().putString(KEY_SNAPSHOT_JSON, object.toString()).apply();
    }

    public synchronized RxSessionSnapshot load() {
        String rawJson = preferences.getString(KEY_SNAPSHOT_JSON, null);
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return null;
        }
        try {
            JSONObject object = new JSONObject(rawJson);
            return new RxSessionSnapshot(
                    object.optLong("updatedAtEpochMs", 0L),
                    object.optString("sourceLabel", ""),
                    object.optString("captureState", ""),
                    object.optBoolean("captureActive", false),
                    object.optInt("preferredToneFrequencyHz", 0),
                    object.optInt("targetToneFrequencyHz", 0),
                    object.optInt("effectiveToneFrequencyHz", 0),
                    object.optInt("estimatedWpm", 0),
                    object.optInt("stableEstimatedWpm", object.optInt("estimatedWpm", 0)),
                    object.optString("rawText", ""),
                    object.optString("normalizedText", ""),
                    object.optString("phaseDisplayName", ""),
                    object.optString("remoteCallsign", ""),
                    object.optBoolean("readyForDraftConfirmation", false),
                    object.optBoolean("needManualReview", false),
                    object.optString("inputHealthLabel", ""),
                    object.optString("inputHealthHint", ""),
                    object.optBoolean("inputLevelHot", false),
                    object.optBoolean("inputLevelClipping", false),
                    object.optString("developerFrontEndSummary", "")
            );
        } catch (JSONException ignored) {
            return null;
        }
    }

    public synchronized void clear() {
        preferences.edit().remove(KEY_SNAPSHOT_JSON).apply();
    }
}
