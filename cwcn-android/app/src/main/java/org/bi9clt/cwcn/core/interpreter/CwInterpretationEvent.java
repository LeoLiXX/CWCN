package org.bi9clt.cwcn.core.interpreter;

public final class CwInterpretationEvent {
    private final long timestampMs;
    private final String rawText;
    private final String normalizedText;
    private final String latestTokenSummary;

    public CwInterpretationEvent(long timestampMs, String rawText, String normalizedText, String latestTokenSummary) {
        this.timestampMs = timestampMs;
        this.rawText = rawText;
        this.normalizedText = normalizedText;
        this.latestTokenSummary = latestTokenSummary;
    }

    public long timestampMs() {
        return timestampMs;
    }

    public String rawText() {
        return rawText;
    }

    public String normalizedText() {
        return normalizedText;
    }

    public String latestTokenSummary() {
        return latestTokenSummary;
    }
}
