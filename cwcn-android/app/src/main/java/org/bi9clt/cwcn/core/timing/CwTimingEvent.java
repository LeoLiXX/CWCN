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
    private final long intraGapEstimateMs;

    public CwTimingEvent(
            Kind kind,
            Classification classification,
            long timestampMs,
            long durationMs,
            long dotEstimateMs
    ) {
        this(kind, classification, timestampMs, durationMs, dotEstimateMs, 0L);
    }

    public CwTimingEvent(
            Kind kind,
            Classification classification,
            long timestampMs,
            long durationMs,
            long dotEstimateMs,
            long intraGapEstimateMs
    ) {
        this.kind = kind;
        this.classification = classification;
        this.timestampMs = timestampMs;
        this.durationMs = durationMs;
        this.dotEstimateMs = dotEstimateMs;
        this.intraGapEstimateMs = intraGapEstimateMs;
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

    public long intraGapEstimateMs() {
        return intraGapEstimateMs;
    }

    public double ratioToDotEstimate() {
        return durationMs / (double) Math.max(1L, dotEstimateMs);
    }

    public double ratioToIntraGapEstimate() {
        return durationMs / (double) Math.max(1L, intraGapEstimateMs);
    }
}
