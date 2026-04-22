package org.bi9clt.cwcn.core.signal;

public final class CwToneEvent {
    public enum Type {
        TONE_ON,
        TONE_OFF
    }

    private final Type type;
    private final long timestampMs;
    private final int peakAmplitude;
    private final double rmsAmplitude;
    private final long toneDurationMs;

    public CwToneEvent(Type type, long timestampMs, int peakAmplitude, double rmsAmplitude, long toneDurationMs) {
        this.type = type;
        this.timestampMs = timestampMs;
        this.peakAmplitude = peakAmplitude;
        this.rmsAmplitude = rmsAmplitude;
        this.toneDurationMs = toneDurationMs;
    }

    public Type type() {
        return type;
    }

    public long timestampMs() {
        return timestampMs;
    }

    public int peakAmplitude() {
        return peakAmplitude;
    }

    public double rmsAmplitude() {
        return rmsAmplitude;
    }

    public long toneDurationMs() {
        return toneDurationMs;
    }
}
