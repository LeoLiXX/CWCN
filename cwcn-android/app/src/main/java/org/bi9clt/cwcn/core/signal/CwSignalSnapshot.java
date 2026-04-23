package org.bi9clt.cwcn.core.signal;

public final class CwSignalSnapshot {
    private final int recentHistoryFrameCount;
    private final char[] recentFrontEndStateHistory;
    private final int[] recentTrackingOffsetHistoryHz;
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
    private final double lastWidebandResidualRmsAmplitude;
    private final double toneDominanceRatio;
    private final double narrowbandIsolationRatio;
    private final double peakToneRmsAmplitude;
    private final double peakNarrowbandIsolationRatio;
    private final int processedFrameCount;
    private final int lockedFrameCount;
    private final int toneActiveFrameCount;
    private final int toneActiveUnlockedFrameCount;
    private final int consecutiveLockedFrames;
    private final int maxConsecutiveLockedFrames;
    private final int consecutiveToneActiveUnlockedFrames;
    private final int maxConsecutiveToneActiveUnlockedFrames;
    private final int pendingRetuneCandidateFrequencyHz;
    private final int pendingRetuneCandidateStableScans;
    private final int totalToneOnEvents;
    private final int totalToneOffEvents;
    private final CwToneEvent lastEvent;

    public CwSignalSnapshot(
            int recentHistoryFrameCount,
            char[] recentFrontEndStateHistory,
            int[] recentTrackingOffsetHistoryHz,
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
            double lastWidebandResidualRmsAmplitude,
            double toneDominanceRatio,
            double narrowbandIsolationRatio,
            double peakToneRmsAmplitude,
            double peakNarrowbandIsolationRatio,
            int processedFrameCount,
            int lockedFrameCount,
            int toneActiveFrameCount,
            int toneActiveUnlockedFrameCount,
            int consecutiveLockedFrames,
            int maxConsecutiveLockedFrames,
            int consecutiveToneActiveUnlockedFrames,
            int maxConsecutiveToneActiveUnlockedFrames,
            int pendingRetuneCandidateFrequencyHz,
            int pendingRetuneCandidateStableScans,
            int totalToneOnEvents,
            int totalToneOffEvents,
            CwToneEvent lastEvent
    ) {
        this.recentHistoryFrameCount = recentHistoryFrameCount;
        this.recentFrontEndStateHistory = recentFrontEndStateHistory == null
                ? new char[0]
                : recentFrontEndStateHistory.clone();
        this.recentTrackingOffsetHistoryHz = recentTrackingOffsetHistoryHz == null
                ? new int[0]
                : recentTrackingOffsetHistoryHz.clone();
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
        this.lastWidebandResidualRmsAmplitude = lastWidebandResidualRmsAmplitude;
        this.toneDominanceRatio = toneDominanceRatio;
        this.narrowbandIsolationRatio = narrowbandIsolationRatio;
        this.peakToneRmsAmplitude = peakToneRmsAmplitude;
        this.peakNarrowbandIsolationRatio = peakNarrowbandIsolationRatio;
        this.processedFrameCount = processedFrameCount;
        this.lockedFrameCount = lockedFrameCount;
        this.toneActiveFrameCount = toneActiveFrameCount;
        this.toneActiveUnlockedFrameCount = toneActiveUnlockedFrameCount;
        this.consecutiveLockedFrames = consecutiveLockedFrames;
        this.maxConsecutiveLockedFrames = maxConsecutiveLockedFrames;
        this.consecutiveToneActiveUnlockedFrames = consecutiveToneActiveUnlockedFrames;
        this.maxConsecutiveToneActiveUnlockedFrames = maxConsecutiveToneActiveUnlockedFrames;
        this.pendingRetuneCandidateFrequencyHz = pendingRetuneCandidateFrequencyHz;
        this.pendingRetuneCandidateStableScans = pendingRetuneCandidateStableScans;
        this.totalToneOnEvents = totalToneOnEvents;
        this.totalToneOffEvents = totalToneOffEvents;
        this.lastEvent = lastEvent;
    }

    public int recentHistoryFrameCount() {
        return recentHistoryFrameCount;
    }

    public char[] recentFrontEndStateHistory() {
        return recentFrontEndStateHistory.clone();
    }

    public int[] recentTrackingOffsetHistoryHz() {
        return recentTrackingOffsetHistoryHz.clone();
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

    public double lastWidebandResidualRmsAmplitude() {
        return lastWidebandResidualRmsAmplitude;
    }

    public double toneDominanceRatio() {
        return toneDominanceRatio;
    }

    public double narrowbandIsolationRatio() {
        return narrowbandIsolationRatio;
    }

    public double peakToneRmsAmplitude() {
        return peakToneRmsAmplitude;
    }

    public double peakNarrowbandIsolationRatio() {
        return peakNarrowbandIsolationRatio;
    }

    public int processedFrameCount() {
        return processedFrameCount;
    }

    public int lockedFrameCount() {
        return lockedFrameCount;
    }

    public int toneActiveFrameCount() {
        return toneActiveFrameCount;
    }

    public int toneActiveUnlockedFrameCount() {
        return toneActiveUnlockedFrameCount;
    }

    public int consecutiveLockedFrames() {
        return consecutiveLockedFrames;
    }

    public int maxConsecutiveLockedFrames() {
        return maxConsecutiveLockedFrames;
    }

    public int consecutiveToneActiveUnlockedFrames() {
        return consecutiveToneActiveUnlockedFrames;
    }

    public int maxConsecutiveToneActiveUnlockedFrames() {
        return maxConsecutiveToneActiveUnlockedFrames;
    }

    public int pendingRetuneCandidateFrequencyHz() {
        return pendingRetuneCandidateFrequencyHz;
    }

    public int pendingRetuneCandidateStableScans() {
        return pendingRetuneCandidateStableScans;
    }

    public double lockedFrameRatio() {
        if (processedFrameCount <= 0) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, lockedFrameCount / (double) processedFrameCount));
    }

    public double toneActiveUnlockedFrameRatio() {
        if (toneActiveFrameCount <= 0) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, toneActiveUnlockedFrameCount / (double) toneActiveFrameCount));
    }

    public int recentLockedFrameCount() {
        return countRecentStateCodes('L', 'l');
    }

    public int recentActiveLockedFrameCount() {
        return countRecentStateCodes('L');
    }

    public int recentQuietLockedFrameCount() {
        return countRecentStateCodes('l');
    }

    public int recentActiveUnlockedFrameCount() {
        return countRecentStateCodes('u');
    }

    public int recentSearchFrameCount() {
        return countRecentStateCodes('.');
    }

    public double recentLockedFrameRatio() {
        return ratioWithinRecentHistory(recentLockedFrameCount());
    }

    public double recentActiveUnlockedFrameRatio() {
        return ratioWithinRecentHistory(recentActiveUnlockedFrameCount());
    }

    public double recentSearchFrameRatio() {
        return ratioWithinRecentHistory(recentSearchFrameCount());
    }

    public int recentNearTargetLockedFrameCount() {
        return countRecentLockedFramesWithinOffset(15, true);
    }

    public int recentFarOffTargetLockedFrameCount() {
        return countRecentLockedFramesWithinOffset(45, false);
    }

    public double recentNearTargetLockedFrameRatio() {
        return ratioWithinRecentLockedFrames(recentNearTargetLockedFrameCount());
    }

    public double recentFarOffTargetLockedFrameRatio() {
        return ratioWithinRecentLockedFrames(recentFarOffTargetLockedFrameCount());
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

    private int countRecentStateCodes(char... acceptedCodes) {
        if (acceptedCodes == null || acceptedCodes.length == 0 || recentHistoryFrameCount <= 0) {
            return 0;
        }
        int count = 0;
        int limit = Math.min(recentHistoryFrameCount, recentFrontEndStateHistory.length);
        for (int index = 0; index < limit; index++) {
            char stateCode = recentFrontEndStateHistory[index];
            for (char acceptedCode : acceptedCodes) {
                if (stateCode == acceptedCode) {
                    count += 1;
                    break;
                }
            }
        }
        return count;
    }

    private int countRecentLockedFramesWithinOffset(int thresholdHz, boolean withinThreshold) {
        if (recentHistoryFrameCount <= 0 || thresholdHz < 0) {
            return 0;
        }
        int count = 0;
        for (int index = 0; index < recentHistoryFrameCount; index++) {
            char stateCode = recentFrontEndStateHistory[index];
            if (stateCode != 'L' && stateCode != 'l') {
                continue;
            }
            int absoluteOffsetHz = Math.abs(recentTrackingOffsetHistoryHz[index]);
            if (withinThreshold ? absoluteOffsetHz <= thresholdHz : absoluteOffsetHz >= thresholdHz) {
                count += 1;
            }
        }
        return count;
    }

    private double ratioWithinRecentHistory(int count) {
        if (recentHistoryFrameCount <= 0) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, count / (double) recentHistoryFrameCount));
    }

    private double ratioWithinRecentLockedFrames(int count) {
        int recentLockedFrameCount = recentLockedFrameCount();
        if (recentLockedFrameCount <= 0) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, count / (double) recentLockedFrameCount));
    }
}
