package org.bi9clt.cwcn.core.spectrum;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SpectrumHistoryStore {
    private static final String PREFS_NAME = "cwcn_spectrum_history";
    private static final String KEY_HISTORY_JSON = "history_json";
    private static final int MAX_HISTORY = 72;

    private final SharedPreferences preferences;

    public SpectrumHistoryStore(Context context) {
        Context appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public synchronized void append(SpectrumSnapshotData snapshot) {
        if (snapshot == null) {
            return;
        }
        ArrayList<SpectrumSnapshotData> history = new ArrayList<>(loadHistory());
        history.add(snapshot);
        while (history.size() > MAX_HISTORY) {
            history.remove(0);
        }
        preferences.edit().putString(KEY_HISTORY_JSON, encodeHistory(history).toString()).apply();
    }

    public synchronized List<SpectrumSnapshotData> loadHistory() {
        String rawJson = preferences.getString(KEY_HISTORY_JSON, null);
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            JSONArray array = new JSONArray(rawJson);
            ArrayList<SpectrumSnapshotData> result = new ArrayList<>();
            for (int index = 0; index < array.length(); index++) {
                JSONObject object = array.optJSONObject(index);
                SpectrumSnapshotData item = decodeItem(object);
                if (item != null) {
                    result.add(item);
                }
            }
            return result;
        } catch (JSONException ignored) {
            return Collections.emptyList();
        }
    }

    public synchronized void clear() {
        preferences.edit().remove(KEY_HISTORY_JSON).apply();
    }

    private JSONArray encodeHistory(List<SpectrumSnapshotData> history) {
        JSONArray array = new JSONArray();
        if (history == null) {
            return array;
        }
        for (SpectrumSnapshotData item : history) {
            if (item == null) {
                continue;
            }
            array.put(encodeItem(item));
        }
        return array;
    }

    private JSONObject encodeItem(SpectrumSnapshotData item) {
        JSONObject object = new JSONObject();
        try {
            object.put("capturedAtEpochMs", item.capturedAtEpochMs());
            object.put("frequenciesHz", encodeIntArray(item.frequenciesHz()));
            object.put("magnitudes", encodeFloatArray(item.magnitudes()));
            object.put("peakFrequencyHz", item.peakFrequencyHz());
            object.put("peakMagnitude", item.peakMagnitude());
            object.put("noiseFloorMagnitude", item.noiseFloorMagnitude());
            object.put("preferredToneHz", item.preferredToneHz());
            object.put("trackedToneHz", item.trackedToneHz());
            object.put("hypothesisToneHz", item.hypothesisToneHz());
            object.put("preferredWindowWinnerToneHz", item.preferredWindowWinnerToneHz());
            object.put("wideScanWinnerToneHz", item.wideScanWinnerToneHz());
            object.put("acquisitionWinnerToneHz", item.acquisitionWinnerToneHz());
            object.put("finalAdoptedToneHz", item.finalAdoptedToneHz());
            object.put("acquisitionWinnerSource", item.acquisitionWinnerSource());
            object.put("finalAdoptedSource", item.finalAdoptedSource());
            object.put("hypothesisGuardEnabled", item.hypothesisGuardEnabled());
            object.put("hypothesisGuardApplied", item.hypothesisGuardApplied());
            object.put("hypothesisGuardAppliedToneHz", item.hypothesisGuardAppliedToneHz());
            object.put("hypothesisGuardDecision", item.hypothesisGuardDecision());
            object.put("syntheticFallback", item.syntheticFallback());
        } catch (JSONException exception) {
            throw new IllegalStateException("Failed to serialize spectrum snapshot", exception);
        }
        return object;
    }

    private SpectrumSnapshotData decodeItem(JSONObject object) {
        if (object == null) {
            return null;
        }
        return new SpectrumSnapshotData(
                object.optLong("capturedAtEpochMs", 0L),
                decodeIntArray(object.optJSONArray("frequenciesHz")),
                decodeFloatArray(object.optJSONArray("magnitudes")),
                object.optInt("peakFrequencyHz", 0),
                (float) object.optDouble("peakMagnitude", 0.0d),
                (float) object.optDouble("noiseFloorMagnitude", 0.0d),
                object.optInt("preferredToneHz", 0),
                object.optInt("trackedToneHz", 0),
                object.optInt("hypothesisToneHz", 0),
                object.optInt("preferredWindowWinnerToneHz", 0),
                object.optInt("wideScanWinnerToneHz", 0),
                object.optInt("acquisitionWinnerToneHz", 0),
                object.optInt("finalAdoptedToneHz", 0),
                object.optString("acquisitionWinnerSource", "NONE"),
                object.optString("finalAdoptedSource", "NONE"),
                object.optBoolean("hypothesisGuardEnabled", false),
                object.optBoolean("hypothesisGuardApplied", false),
                object.optInt("hypothesisGuardAppliedToneHz", 0),
                object.optString("hypothesisGuardDecision", "NONE"),
                object.optBoolean("syntheticFallback", false)
        );
    }

    private JSONArray encodeIntArray(int[] values) {
        JSONArray array = new JSONArray();
        if (values == null) {
            return array;
        }
        for (int value : values) {
            array.put(value);
        }
        return array;
    }

    private JSONArray encodeFloatArray(float[] values) {
        JSONArray array = new JSONArray();
        if (values == null) {
            return array;
        }
        for (float value : values) {
            array.put(Float.valueOf(value));
        }
        return array;
    }

    private int[] decodeIntArray(JSONArray array) {
        if (array == null || array.length() == 0) {
            return new int[0];
        }
        int[] values = new int[array.length()];
        for (int index = 0; index < array.length(); index++) {
            values[index] = array.optInt(index, 0);
        }
        return values;
    }

    private float[] decodeFloatArray(JSONArray array) {
        if (array == null || array.length() == 0) {
            return new float[0];
        }
        float[] values = new float[array.length()];
        for (int index = 0; index < array.length(); index++) {
            values[index] = (float) array.optDouble(index, 0.0d);
        }
        return values;
    }
}
