package org.bi9clt.cwcn.core.audio;

public final class AudioInputHealthSnapshot {
    private final int recentHistoryFrameCount;
    private final char[] recentStateHistory;
    private final int totalFrames;
    private final int lastPeakAmplitude;
    private final double lastRmsAmplitude;
    private final double lastClippedSampleRatio;

    public AudioInputHealthSnapshot(
            int recentHistoryFrameCount,
            char[] recentStateHistory,
            int totalFrames,
            int lastPeakAmplitude,
            double lastRmsAmplitude,
            double lastClippedSampleRatio
    ) {
        this.recentHistoryFrameCount = recentHistoryFrameCount;
        this.recentStateHistory = recentStateHistory == null ? new char[0] : recentStateHistory.clone();
        this.totalFrames = totalFrames;
        this.lastPeakAmplitude = lastPeakAmplitude;
        this.lastRmsAmplitude = lastRmsAmplitude;
        this.lastClippedSampleRatio = lastClippedSampleRatio;
    }

    public int recentHistoryFrameCount() {
        return recentHistoryFrameCount;
    }

    public char[] recentStateHistory() {
        return recentStateHistory.clone();
    }

    public int totalFrames() {
        return totalFrames;
    }

    public int lastPeakAmplitude() {
        return lastPeakAmplitude;
    }

    public double lastRmsAmplitude() {
        return lastRmsAmplitude;
    }

    public double lastClippedSampleRatio() {
        return lastClippedSampleRatio;
    }

    public int recentClippingFrameCount() {
        return countRecentState('C');
    }

    public int recentQuietFrameCount() {
        return countRecentState('Q');
    }

    public int recentHotFrameCount() {
        return countRecentState('H');
    }

    public int recentUsableFrameCount() {
        return countRecentState('G');
    }

    public double recentClippingFrameRatio() {
        return ratio(recentClippingFrameCount());
    }

    public double recentQuietFrameRatio() {
        return ratio(recentQuietFrameCount());
    }

    public double recentHotFrameRatio() {
        return ratio(recentHotFrameCount());
    }

    public double recentUsableFrameRatio() {
        return ratio(recentUsableFrameCount());
    }

    private int countRecentState(char acceptedState) {
        int limit = Math.min(recentHistoryFrameCount, recentStateHistory.length);
        int count = 0;
        for (int index = 0; index < limit; index++) {
            if (recentStateHistory[index] == acceptedState) {
                count += 1;
            }
        }
        return count;
    }

    private double ratio(int count) {
        if (recentHistoryFrameCount <= 0) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, count / (double) recentHistoryFrameCount));
    }
}
