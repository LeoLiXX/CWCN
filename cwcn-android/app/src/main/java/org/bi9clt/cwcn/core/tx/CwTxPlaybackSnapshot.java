package org.bi9clt.cwcn.core.tx;

public final class CwTxPlaybackSnapshot {
    private final CwTxState state;
    private final String normalizedText;
    private final String morsePreview;
    private final int completedElementCount;
    private final int totalElementCount;
    private final int elapsedMs;
    private final int totalDurationMs;
    private final String currentElementLabel;
    private final boolean toneActive;
    private final String statusMessage;

    public CwTxPlaybackSnapshot(
            CwTxState state,
            String normalizedText,
            String morsePreview,
            int completedElementCount,
            int totalElementCount,
            int elapsedMs,
            int totalDurationMs,
            String currentElementLabel,
            boolean toneActive,
            String statusMessage
    ) {
        this.state = state;
        this.normalizedText = normalizedText == null ? "" : normalizedText;
        this.morsePreview = morsePreview == null ? "" : morsePreview;
        this.completedElementCount = Math.max(0, completedElementCount);
        this.totalElementCount = Math.max(0, totalElementCount);
        this.elapsedMs = Math.max(0, elapsedMs);
        this.totalDurationMs = Math.max(0, totalDurationMs);
        this.currentElementLabel = currentElementLabel == null ? "" : currentElementLabel;
        this.toneActive = toneActive;
        this.statusMessage = statusMessage == null ? "" : statusMessage;
    }

    public CwTxState state() {
        return state;
    }

    public String normalizedText() {
        return normalizedText;
    }

    public String morsePreview() {
        return morsePreview;
    }

    public int completedElementCount() {
        return completedElementCount;
    }

    public int totalElementCount() {
        return totalElementCount;
    }

    public int elapsedMs() {
        return elapsedMs;
    }

    public int totalDurationMs() {
        return totalDurationMs;
    }

    public String currentElementLabel() {
        return currentElementLabel;
    }

    public boolean toneActive() {
        return toneActive;
    }

    public String statusMessage() {
        return statusMessage;
    }

    public double completionRatio() {
        if (totalDurationMs <= 0) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, elapsedMs / (double) totalDurationMs));
    }
}
