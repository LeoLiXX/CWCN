package org.bi9clt.cwcn.core.audio;

public final class AudioInputHealthTracker {
    private static final int RECENT_HISTORY_WINDOW_FRAMES = 24;
    private static final int QUIET_PEAK_THRESHOLD = 1200;
    private static final double QUIET_RMS_THRESHOLD = 180.0d;
    private static final int HOT_PEAK_THRESHOLD = 25000;
    private static final double HOT_RMS_THRESHOLD = 9000.0d;

    private final char[] recentStateHistory = new char[RECENT_HISTORY_WINDOW_FRAMES];

    private int recentHistoryFrameCount;
    private int recentHistoryNextIndex;
    private int totalFrames;
    private int lastPeakAmplitude;
    private double lastRmsAmplitude;
    private double lastClippedSampleRatio;

    public synchronized void process(AudioFrame frame) {
        if (frame == null) {
            return;
        }
        totalFrames += 1;
        lastPeakAmplitude = frame.peakAmplitude();
        lastRmsAmplitude = frame.rmsAmplitude();
        lastClippedSampleRatio = frame.clippedSampleRatio();

        recentStateHistory[recentHistoryNextIndex] = classifyFrame(frame);
        recentHistoryNextIndex = (recentHistoryNextIndex + 1) % RECENT_HISTORY_WINDOW_FRAMES;
        recentHistoryFrameCount = Math.min(RECENT_HISTORY_WINDOW_FRAMES, recentHistoryFrameCount + 1);
    }

    public synchronized void reset() {
        recentHistoryFrameCount = 0;
        recentHistoryNextIndex = 0;
        totalFrames = 0;
        lastPeakAmplitude = 0;
        lastRmsAmplitude = 0.0d;
        lastClippedSampleRatio = 0.0d;
    }

    public synchronized AudioInputHealthSnapshot snapshot() {
        return new AudioInputHealthSnapshot(
                recentHistoryFrameCount,
                orderedRecentStateHistory(),
                totalFrames,
                lastPeakAmplitude,
                lastRmsAmplitude,
                lastClippedSampleRatio
        );
    }

    private char[] orderedRecentStateHistory() {
        char[] ordered = new char[recentHistoryFrameCount];
        if (recentHistoryFrameCount <= 0) {
            return ordered;
        }
        int startIndex = recentHistoryFrameCount < RECENT_HISTORY_WINDOW_FRAMES ? 0 : recentHistoryNextIndex;
        for (int index = 0; index < recentHistoryFrameCount; index++) {
            ordered[index] = recentStateHistory[(startIndex + index) % RECENT_HISTORY_WINDOW_FRAMES];
        }
        return ordered;
    }

    private char classifyFrame(AudioFrame frame) {
        if (frame.likelyClipping()) {
            return 'C';
        }
        if (frame.peakAmplitude() < QUIET_PEAK_THRESHOLD && frame.rmsAmplitude() < QUIET_RMS_THRESHOLD) {
            return 'Q';
        }
        if (frame.peakAmplitude() >= HOT_PEAK_THRESHOLD || frame.rmsAmplitude() >= HOT_RMS_THRESHOLD) {
            return 'H';
        }
        return 'G';
    }
}
