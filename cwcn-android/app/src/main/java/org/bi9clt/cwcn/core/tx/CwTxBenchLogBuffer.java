package org.bi9clt.cwcn.core.tx;

import java.util.ArrayDeque;

public final class CwTxBenchLogBuffer {
    private final int maxEntries;
    private final ArrayDeque<String> entries;

    public CwTxBenchLogBuffer(int maxEntries) {
        this.maxEntries = Math.max(1, maxEntries);
        this.entries = new ArrayDeque<>(this.maxEntries);
    }

    public void append(String timestampLabel, String category, String detail) {
        String entry = buildEntry(timestampLabel, category, detail);
        if (entries.size() >= maxEntries) {
            entries.removeFirst();
        }
        entries.addLast(entry);
    }

    public void clear() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public String renderMultiline() {
        if (entries.isEmpty()) {
            return "No bench events yet.";
        }
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (String entry : entries) {
            if (!first) {
                builder.append('\n');
            }
            builder.append(entry);
            first = false;
        }
        return builder.toString();
    }

    private String buildEntry(String timestampLabel, String category, String detail) {
        String normalizedTimestamp = normalizePart(timestampLabel, "--:--:--");
        String normalizedCategory = normalizePart(category, "INFO");
        String normalizedDetail = normalizePart(detail, "(no detail)");
        return "[" + normalizedTimestamp + "] " + normalizedCategory + "  " + normalizedDetail;
    }

    private String normalizePart(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();
        return normalized.isEmpty() ? fallback : normalized;
    }
}
