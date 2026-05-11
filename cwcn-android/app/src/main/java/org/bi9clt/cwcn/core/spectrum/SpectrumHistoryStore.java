package org.bi9clt.cwcn.core.spectrum;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Process-local live spectrum cache shared across pages.
 * This data is intentionally transient and should not be persisted.
 */
public final class SpectrumHistoryStore {
    private static final int MAX_HISTORY = 72;
    private static final Object LOCK = new Object();
    private static final ArrayList<SpectrumSnapshotData> HISTORY = new ArrayList<>();

    public SpectrumHistoryStore(Context context) {
        // Kept for call-site compatibility; no persistent state is needed.
    }

    public void append(SpectrumSnapshotData snapshot) {
        if (snapshot == null) {
            return;
        }
        synchronized (LOCK) {
            HISTORY.add(snapshot);
            while (HISTORY.size() > MAX_HISTORY) {
                HISTORY.remove(0);
            }
        }
    }

    public List<SpectrumSnapshotData> loadHistory() {
        synchronized (LOCK) {
            if (HISTORY.isEmpty()) {
                return Collections.emptyList();
            }
            return new ArrayList<>(HISTORY);
        }
    }

    public void clear() {
        synchronized (LOCK) {
            HISTORY.clear();
        }
    }
}
