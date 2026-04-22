package org.bi9clt.cwcn.core.signal;

public final class CwSignalSnapshot {
    private final boolean toneActive;
    private final boolean targetToneLocked;
    private final int preferredToneFrequencyHz;
    private final int targetToneFrequencyHz;
    private final int currentThreshold;
    private final int releaseThreshold;
    private final int noiseFloorEstimate;
    private final int signalFloorEstimate;
    private final double lastRmsAmplitude;
    private final double lastToneRmsAmplitude;
    private final double toneDominanceRatio;
    private final int totalToneOnEvents;
    private final int totalToneOffEvents;
    private final CwToneEvent lastEvent;

    public CwSignalSnapshot(
            boolean toneActive,
            boolean targetToneLocked,
            int preferredToneFrequencyHz,
            int targetToneFrequencyHz,
            int currentThreshold,
            int releaseThreshold,
            int noiseFloorEstimate,
            int signalFloorEstimate,
            double lastRmsAmplitude,
            double lastToneRmsAmplitude,
            double toneDominanceRatio,
            int totalToneOnEvents,
            int totalToneOffEvents,
            CwToneEvent lastEvent
    ) {
        this.toneActive = toneActive;
        this.targetToneLocked = targetToneLocked;
        this.preferredToneFrequencyHz = preferredToneFrequencyHz;
        this.targetToneFrequencyHz = targetToneFrequencyHz;
        this.currentThreshold = currentThreshold;
        this.releaseThreshold = releaseThreshold;
        this.noiseFloorEstimate = noiseFloorEstimate;
        this.signalFloorEstimate = signalFloorEstimate;
        this.lastRmsAmplitude = lastRmsAmplitude;
        this.lastToneRmsAmplitude = lastToneRmsAmplitude;
        this.toneDominanceRatio = toneDominanceRatio;
        this.totalToneOnEvents = totalToneOnEvents;
        this.totalToneOffEvents = totalToneOffEvents;
        this.lastEvent = lastEvent;
    }

    public boolean toneActive() {
        return toneActive;
    }

    public boolean targetToneLocked() {
        return targetToneLocked;
    }

    public int preferredToneFrequencyHz() {
        return preferredToneFrequencyHz;
    }

    public int targetToneFrequencyHz() {
        return targetToneFrequencyHz;
    }

    public int currentThreshold() {
        return currentThreshold;
    }

    public int releaseThreshold() {
        return releaseThreshold;
    }

    public int noiseFloorEstimate() {
        return noiseFloorEstimate;
    }

    public int signalFloorEstimate() {
        return signalFloorEstimate;
    }

    public double lastRmsAmplitude() {
        return lastRmsAmplitude;
    }

    public double lastToneRmsAmplitude() {
        return lastToneRmsAmplitude;
    }

    public double toneDominanceRatio() {
        return toneDominanceRatio;
    }

    public int totalToneOnEvents() {
        return totalToneOnEvents;
    }

    public int totalToneOffEvents() {
        return totalToneOffEvents;
    }

    public CwToneEvent lastEvent() {
        return lastEvent;
    }
}
