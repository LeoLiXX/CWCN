package org.bi9clt.cwcn.core.timing;

public final class CwTimingSnapshot {
    private final long dotEstimateMs;
    private final long dashEstimateMs;
    private final long intraGapEstimateMs;
    private final int estimatedWpm;
    private final int totalToneEvents;
    private final int totalGapEvents;
    private final CwTimingEvent lastTimingEvent;

    public CwTimingSnapshot(
            long dotEstimateMs,
            long dashEstimateMs,
            long intraGapEstimateMs,
            int estimatedWpm,
            int totalToneEvents,
            int totalGapEvents,
            CwTimingEvent lastTimingEvent
    ) {
        this.dotEstimateMs = dotEstimateMs;
        this.dashEstimateMs = dashEstimateMs;
        this.intraGapEstimateMs = intraGapEstimateMs;
        this.estimatedWpm = estimatedWpm;
        this.totalToneEvents = totalToneEvents;
        this.totalGapEvents = totalGapEvents;
        this.lastTimingEvent = lastTimingEvent;
    }

    public long dotEstimateMs() {
        return dotEstimateMs;
    }

    public long dashEstimateMs() {
        return dashEstimateMs;
    }

    public long intraGapEstimateMs() {
        return intraGapEstimateMs;
    }

    public int estimatedWpm() {
        return estimatedWpm;
    }

    public int totalToneEvents() {
        return totalToneEvents;
    }

    public int totalGapEvents() {
        return totalGapEvents;
    }

    public CwTimingEvent lastTimingEvent() {
        return lastTimingEvent;
    }
}
