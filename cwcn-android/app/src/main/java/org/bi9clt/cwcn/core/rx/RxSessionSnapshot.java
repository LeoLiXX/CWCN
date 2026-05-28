package org.bi9clt.cwcn.core.rx;

public final class RxSessionSnapshot {
    private final long updatedAtEpochMs;
    private final String sourceLabel;
    private final String captureState;
    private final boolean captureActive;
    private final int preferredToneFrequencyHz;
    private final int targetToneFrequencyHz;
    private final int effectiveToneFrequencyHz;
    private final int estimatedWpm;
    private final int stableEstimatedWpm;
    private final String rawText;
    private final String previewRawText;
    private final String normalizedText;
    private final String primaryCallsignCandidate;
    private final String inputHealthLabel;
    private final String inputHealthHint;
    private final boolean inputLevelHot;
    private final boolean inputLevelClipping;
    private final String currentTurnSummary;
    private final String rawGateSummary;
    private final String developerFrontEndSummary;

    public RxSessionSnapshot(
            long updatedAtEpochMs,
            String sourceLabel,
            String captureState,
            boolean captureActive,
            int preferredToneFrequencyHz,
            int targetToneFrequencyHz,
            int effectiveToneFrequencyHz,
            int estimatedWpm,
            int stableEstimatedWpm,
            String rawText,
            String previewRawText,
            String normalizedText,
            String primaryCallsignCandidate,
            String inputHealthLabel,
            String inputHealthHint,
            boolean inputLevelHot,
            boolean inputLevelClipping,
            String currentTurnSummary,
            String rawGateSummary,
            String developerFrontEndSummary
    ) {
        this.updatedAtEpochMs = updatedAtEpochMs;
        this.sourceLabel = safeText(sourceLabel);
        this.captureState = safeText(captureState);
        this.captureActive = captureActive;
        this.preferredToneFrequencyHz = preferredToneFrequencyHz;
        this.targetToneFrequencyHz = targetToneFrequencyHz;
        this.effectiveToneFrequencyHz = effectiveToneFrequencyHz;
        this.estimatedWpm = estimatedWpm;
        this.stableEstimatedWpm = stableEstimatedWpm;
        this.rawText = safeText(rawText);
        this.previewRawText = safeText(previewRawText);
        this.normalizedText = safeText(normalizedText);
        this.primaryCallsignCandidate = safeText(primaryCallsignCandidate);
        this.inputHealthLabel = safeText(inputHealthLabel);
        this.inputHealthHint = safeText(inputHealthHint);
        this.inputLevelHot = inputLevelHot;
        this.inputLevelClipping = inputLevelClipping;
        this.currentTurnSummary = safeText(currentTurnSummary);
        this.rawGateSummary = safeText(rawGateSummary);
        this.developerFrontEndSummary = safeText(developerFrontEndSummary);
    }

    public long updatedAtEpochMs() {
        return updatedAtEpochMs;
    }

    public String sourceLabel() {
        return sourceLabel;
    }

    public String captureState() {
        return captureState;
    }

    public boolean captureActive() {
        return captureActive;
    }

    public int preferredToneFrequencyHz() {
        return preferredToneFrequencyHz;
    }

    public int targetToneFrequencyHz() {
        return targetToneFrequencyHz;
    }

    public int effectiveToneFrequencyHz() {
        return effectiveToneFrequencyHz;
    }

    public int estimatedWpm() {
        return estimatedWpm;
    }

    public int stableEstimatedWpm() {
        return stableEstimatedWpm;
    }

    public String rawText() {
        return rawText;
    }

    public boolean hasRawText() {
        return !rawText.trim().isEmpty();
    }

    public String previewRawText() {
        return previewRawText;
    }

    public boolean hasPreviewRawText() {
        return !previewRawText.trim().isEmpty();
    }

    public String normalizedText() {
        return normalizedText;
    }

    public boolean hasNormalizedText() {
        return !normalizedText.trim().isEmpty();
    }

    public boolean hasDistinctNormalizedText() {
        return hasNormalizedText()
                && !normalizedText.trim().equals(rawText.trim());
    }

    public String primaryCallsignCandidate() {
        return primaryCallsignCandidate;
    }

    public String inputHealthLabel() {
        return inputHealthLabel;
    }

    public String inputHealthHint() {
        return inputHealthHint;
    }

    public boolean hasInputHealthHint() {
        return !inputHealthHint.trim().isEmpty();
    }

    public boolean inputLevelHot() {
        return inputLevelHot;
    }

    public boolean inputLevelClipping() {
        return inputLevelClipping;
    }

    public String currentTurnSummary() {
        return currentTurnSummary;
    }

    public boolean hasCurrentTurnSummary() {
        return !currentTurnSummary.trim().isEmpty();
    }

    public String rawGateSummary() {
        return rawGateSummary;
    }

    public boolean hasRawGateSummary() {
        return !rawGateSummary.trim().isEmpty();
    }

    public String developerFrontEndSummary() {
        return developerFrontEndSummary;
    }

    public boolean hasDeveloperFrontEndSummary() {
        return !developerFrontEndSummary.isEmpty();
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }
}
