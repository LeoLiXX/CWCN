package org.bi9clt.cwcn.core.timing;

public final class CwTimingEvent {
    public enum Kind {
        TONE,
        GAP
    }

    public enum Classification {
        DIT,
        DAH,
        INTRA_SYMBOL_GAP,
        LETTER_GAP,
        WORD_GAP,
        UNKNOWN
    }

    private final Kind kind;
    private final Classification classification;
    private final long timestampMs;
    private final long durationMs;
    private final long dotEstimateMs;

    public CwTimingEvent(
            Kind kind,
            Classification classification,
            long timestampMs,
            long durationMs,
            long dotEstimateMs
    ) {
        this.kind = kind;
        this.classification = classification;
        this.timestampMs = timestampMs;
        this.durationMs = durationMs;
        this.dotEstimateMs = dotEstimateMs;
    }

    public Kind kind() {
        return kind;
    }

    public Classification classification() {
        return classification;
    }

    public long timestampMs() {
        return timestampMs;
    }

    public long durationMs() {
        return durationMs;
    }

    public long dotEstimateMs() {
        return dotEstimateMs;
    }
}
