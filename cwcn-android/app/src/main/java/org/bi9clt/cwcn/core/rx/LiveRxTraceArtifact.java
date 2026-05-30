package org.bi9clt.cwcn.core.rx;

public final class LiveRxTraceArtifact {
    private final long capturedAtEpochMs;
    private final String sessionLabel;
    private final String sourceLabel;
    private final String wavFilePath;
    private final String logFilePath;
    private final long durationMs;
    private final int sampleRateHz;
    private final long sampleCount;
    private final int preferredToneFrequencyHz;
    private final int sqlLevel;

    public LiveRxTraceArtifact(
            long capturedAtEpochMs,
            String sessionLabel,
            String sourceLabel,
            String wavFilePath,
            String logFilePath,
            long durationMs,
            int sampleRateHz,
            long sampleCount
    ) {
        this(
                capturedAtEpochMs,
                sessionLabel,
                sourceLabel,
                wavFilePath,
                logFilePath,
                durationMs,
                sampleRateHz,
                sampleCount,
                -1,
                -1
        );
    }

    public LiveRxTraceArtifact(
            long capturedAtEpochMs,
            String sessionLabel,
            String sourceLabel,
            String wavFilePath,
            String logFilePath,
            long durationMs,
            int sampleRateHz,
            long sampleCount,
            int preferredToneFrequencyHz,
            int sqlLevel
    ) {
        this.capturedAtEpochMs = capturedAtEpochMs;
        this.sessionLabel = safeText(sessionLabel);
        this.sourceLabel = safeText(sourceLabel);
        this.wavFilePath = safeText(wavFilePath);
        this.logFilePath = safeText(logFilePath);
        this.durationMs = Math.max(0L, durationMs);
        this.sampleRateHz = Math.max(0, sampleRateHz);
        this.sampleCount = Math.max(0L, sampleCount);
        this.preferredToneFrequencyHz = sanitizePreferredToneFrequencyHz(preferredToneFrequencyHz);
        this.sqlLevel = sanitizeSqlLevel(sqlLevel);
    }

    public long capturedAtEpochMs() {
        return capturedAtEpochMs;
    }

    public String sessionLabel() {
        return sessionLabel;
    }

    public String sourceLabel() {
        return sourceLabel;
    }

    public String wavFilePath() {
        return wavFilePath;
    }

    public String logFilePath() {
        return logFilePath;
    }

    public long durationMs() {
        return durationMs;
    }

    public int sampleRateHz() {
        return sampleRateHz;
    }

    public long sampleCount() {
        return sampleCount;
    }

    public int preferredToneFrequencyHz() {
        return preferredToneFrequencyHz;
    }

    public int sqlLevel() {
        return sqlLevel;
    }

    public boolean hasPreferredToneFrequency() {
        return preferredToneFrequencyHz > 0;
    }

    public boolean hasSqlLevel() {
        return sqlLevel >= 0;
    }

    public boolean hasReplayableAudio() {
        return !wavFilePath.isEmpty();
    }

    public boolean hasTraceLog() {
        return !logFilePath.isEmpty();
    }

    public String analysisKey() {
        return sessionLabel
                + "|"
                + wavFilePath
                + "|"
                + preferredToneFrequencyHz
                + "|"
                + sqlLevel
                + "|"
                + sampleCount;
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }

    private static int sanitizePreferredToneFrequencyHz(int value) {
        return value > 0 ? value : -1;
    }

    private static int sanitizeSqlLevel(int value) {
        if (value < 0) {
            return -1;
        }
        return value;
    }
}
