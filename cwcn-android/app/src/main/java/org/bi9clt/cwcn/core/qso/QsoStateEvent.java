package org.bi9clt.cwcn.core.qso;

public final class QsoStateEvent {
    private final long timestampMs;
    private final QsoPhase phase;
    private final String summary;

    public QsoStateEvent(long timestampMs, QsoPhase phase, String summary) {
        this.timestampMs = timestampMs;
        this.phase = phase;
        this.summary = summary;
    }

    public long timestampMs() {
        return timestampMs;
    }

    public QsoPhase phase() {
        return phase;
    }

    public String summary() {
        return summary;
    }
}
