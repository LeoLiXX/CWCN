package org.bi9clt.cwcn.core.eval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class CwFixtureEvaluationResult {
    private final String scenarioId;
    private final String scenarioDisplayName;
    private final long evaluatedAtEpochMs;
    private final boolean completed;
    private final boolean passed;
    private boolean exactTextMatch;
    private final boolean exactRawTextMatch;
    private final boolean exactNormalizedTextMatch;
    private final double primaryCallsignScore;
    private double textTokenRecall;
    private final double rawTextTokenRecall;
    private final double normalizedTextTokenRecall;
    private final double callsignRecall;
    private final double hintRecall;
    private final double qsoSemanticScore;
    private final String expectedRawText;
    private final String actualRawText;
    private final String expectedNormalizedText;
    private final String actualNormalizedText;
    private final String expectedPhase;
    private final String actualPhase;
    private final String expectedRstSent;
    private final String actualRstSent;
    private final String expectedRstRcvd;
    private final String actualRstRcvd;
    private final List<String> actualCallsigns;
    private final List<String> actualHints;
    private List<String> missingTextTokens;
    private final List<String> missingRawTextTokens;
    private final List<String> missingNormalizedTextTokens;
    private final List<String> missingCallsigns;
    private final List<String> missingHints;
    private final List<String> failureReasons;
    private final int normalizedTokenCount;
    private final int totalTokenCount;
    private final List<String> normalizedTokenPairs;
    private final boolean finalToneLocked;
    private final boolean endedOnToneOffEvent;
    private final double peakToneRmsAmplitude;
    private final double peakNarrowbandIsolationRatio;
    private final double lockedFrameRatio;
    private final int maxConsecutiveLockedFrames;
    private final double toneActiveUnlockedFrameRatio;
    private final int maxConsecutiveToneActiveUnlockedFrames;
    private final int preferredToneFrequencyHz;
    private final int trackedToneFrequencyHz;
    private final double lastRmsAmplitude;
    private final double lastToneRmsAmplitude;
    private final double lastWidebandResidualRmsAmplitude;
    private final double toneDominanceRatio;
    private final double narrowbandIsolationRatio;
    private final int currentThreshold;
    private final int releaseThreshold;
    private final int noiseFloorEstimate;
    private final int signalFloorEstimate;
    private final int totalToneOnEvents;
    private final int totalToneOffEvents;
    private final int frameGapResetCount;
    private final long worstFrameGapMs;

    public CwFixtureEvaluationResult(
            String scenarioId,
            String scenarioDisplayName,
            long evaluatedAtEpochMs,
            boolean completed,
            boolean passed,
            boolean exactTextMatch,
            double primaryCallsignScore,
            double textTokenRecall,
            double callsignRecall,
            double hintRecall,
            double qsoSemanticScore,
            String expectedNormalizedText,
            String actualNormalizedText,
            String expectedPhase,
            String actualPhase,
            String expectedRstSent,
            String actualRstSent,
            String expectedRstRcvd,
            String actualRstRcvd,
            List<String> actualCallsigns,
            List<String> actualHints,
            List<String> missingTextTokens,
            List<String> missingCallsigns,
            List<String> missingHints,
            List<String> failureReasons
    ) {
        this(
                scenarioId,
                scenarioDisplayName,
                evaluatedAtEpochMs,
                completed,
                passed,
                exactTextMatch,
                exactTextMatch,
                primaryCallsignScore,
                textTokenRecall,
                textTokenRecall,
                callsignRecall,
                hintRecall,
                qsoSemanticScore,
                expectedNormalizedText,
                actualNormalizedText,
                expectedNormalizedText,
                actualNormalizedText,
                expectedPhase,
                actualPhase,
                expectedRstSent,
                actualRstSent,
                expectedRstRcvd,
                actualRstRcvd,
                actualCallsigns,
                actualHints,
                missingTextTokens,
                missingTextTokens,
                missingCallsigns,
                missingHints,
                failureReasons,
                0,
                0,
                Collections.emptyList(),
                false,
                false,
                0.0d,
                0.0d,
                0.0d,
                0,
                0.0d,
                0,
                0,
                0,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0L
        );
    }

    public CwFixtureEvaluationResult(
            String scenarioId,
            String scenarioDisplayName,
            long evaluatedAtEpochMs,
            boolean completed,
            boolean passed,
            boolean exactTextMatch,
            double primaryCallsignScore,
            double textTokenRecall,
            double callsignRecall,
            double hintRecall,
            double qsoSemanticScore,
            String expectedNormalizedText,
            String actualNormalizedText,
            String expectedPhase,
            String actualPhase,
            String expectedRstSent,
            String actualRstSent,
            String expectedRstRcvd,
            String actualRstRcvd,
            List<String> actualCallsigns,
            List<String> actualHints,
            List<String> missingTextTokens,
            List<String> missingCallsigns,
            List<String> missingHints,
            List<String> failureReasons,
            boolean finalToneLocked,
            boolean endedOnToneOffEvent,
            double peakToneRmsAmplitude,
            double peakNarrowbandIsolationRatio,
            double lockedFrameRatio,
            int maxConsecutiveLockedFrames,
            double toneActiveUnlockedFrameRatio,
            int maxConsecutiveToneActiveUnlockedFrames,
            int preferredToneFrequencyHz,
            int trackedToneFrequencyHz
    ) {
        this(
                scenarioId,
                scenarioDisplayName,
                evaluatedAtEpochMs,
                completed,
                passed,
                exactTextMatch,
                exactTextMatch,
                primaryCallsignScore,
                textTokenRecall,
                textTokenRecall,
                callsignRecall,
                hintRecall,
                qsoSemanticScore,
                expectedNormalizedText,
                actualNormalizedText,
                expectedNormalizedText,
                actualNormalizedText,
                expectedPhase,
                actualPhase,
                expectedRstSent,
                actualRstSent,
                expectedRstRcvd,
                actualRstRcvd,
                actualCallsigns,
                actualHints,
                missingTextTokens,
                missingTextTokens,
                missingCallsigns,
                missingHints,
                failureReasons,
                0,
                0,
                Collections.emptyList(),
                finalToneLocked,
                endedOnToneOffEvent,
                peakToneRmsAmplitude,
                peakNarrowbandIsolationRatio,
                lockedFrameRatio,
                maxConsecutiveLockedFrames,
                toneActiveUnlockedFrameRatio,
                maxConsecutiveToneActiveUnlockedFrames,
                preferredToneFrequencyHz,
                trackedToneFrequencyHz,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0L
        );
    }

    public CwFixtureEvaluationResult(
            String scenarioId,
            String scenarioDisplayName,
            long evaluatedAtEpochMs,
            boolean completed,
            boolean passed,
            boolean exactTextMatch,
            double primaryCallsignScore,
            double textTokenRecall,
            double callsignRecall,
            double hintRecall,
            double qsoSemanticScore,
            String expectedNormalizedText,
            String actualNormalizedText,
            String expectedPhase,
            String actualPhase,
            String expectedRstSent,
            String actualRstSent,
            String expectedRstRcvd,
            String actualRstRcvd,
            List<String> actualCallsigns,
            List<String> actualHints,
            List<String> missingTextTokens,
            List<String> missingCallsigns,
            List<String> missingHints,
            List<String> failureReasons,
            int normalizedTokenCount,
            int totalTokenCount,
            List<String> normalizedTokenPairs,
            boolean finalToneLocked,
            boolean endedOnToneOffEvent,
            double peakToneRmsAmplitude,
            double peakNarrowbandIsolationRatio,
            double lockedFrameRatio,
            int maxConsecutiveLockedFrames,
            double toneActiveUnlockedFrameRatio,
            int maxConsecutiveToneActiveUnlockedFrames,
            int preferredToneFrequencyHz,
            int trackedToneFrequencyHz
    ) {
        this(
                scenarioId,
                scenarioDisplayName,
                evaluatedAtEpochMs,
                completed,
                passed,
                exactTextMatch,
                exactTextMatch,
                primaryCallsignScore,
                textTokenRecall,
                textTokenRecall,
                callsignRecall,
                hintRecall,
                qsoSemanticScore,
                expectedNormalizedText,
                actualNormalizedText,
                expectedNormalizedText,
                actualNormalizedText,
                expectedPhase,
                actualPhase,
                expectedRstSent,
                actualRstSent,
                expectedRstRcvd,
                actualRstRcvd,
                actualCallsigns,
                actualHints,
                missingTextTokens,
                missingTextTokens,
                missingCallsigns,
                missingHints,
                failureReasons,
                normalizedTokenCount,
                totalTokenCount,
                normalizedTokenPairs,
                finalToneLocked,
                endedOnToneOffEvent,
                peakToneRmsAmplitude,
                peakNarrowbandIsolationRatio,
                lockedFrameRatio,
                maxConsecutiveLockedFrames,
                toneActiveUnlockedFrameRatio,
                maxConsecutiveToneActiveUnlockedFrames,
                preferredToneFrequencyHz,
                trackedToneFrequencyHz,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0L
        );
    }

    public CwFixtureEvaluationResult(
            String scenarioId,
            String scenarioDisplayName,
            long evaluatedAtEpochMs,
            boolean completed,
            boolean passed,
            boolean exactTextMatch,
            double primaryCallsignScore,
            double textTokenRecall,
            double callsignRecall,
            double hintRecall,
            double qsoSemanticScore,
            String expectedNormalizedText,
            String actualNormalizedText,
            String expectedPhase,
            String actualPhase,
            String expectedRstSent,
            String actualRstSent,
            String expectedRstRcvd,
            String actualRstRcvd,
            List<String> actualCallsigns,
            List<String> actualHints,
            List<String> missingTextTokens,
            List<String> missingCallsigns,
            List<String> missingHints,
            List<String> failureReasons,
            int normalizedTokenCount,
            int totalTokenCount,
            List<String> normalizedTokenPairs,
            boolean finalToneLocked,
            boolean endedOnToneOffEvent,
            double peakToneRmsAmplitude,
            double peakNarrowbandIsolationRatio,
            double lockedFrameRatio,
            int maxConsecutiveLockedFrames,
            double toneActiveUnlockedFrameRatio,
            int maxConsecutiveToneActiveUnlockedFrames,
            int preferredToneFrequencyHz,
            int trackedToneFrequencyHz,
            double lastRmsAmplitude,
            double lastToneRmsAmplitude,
            double lastWidebandResidualRmsAmplitude,
            double toneDominanceRatio,
            double narrowbandIsolationRatio,
            int currentThreshold,
            int releaseThreshold,
            int noiseFloorEstimate,
            int signalFloorEstimate,
            int totalToneOnEvents,
            int totalToneOffEvents,
            int frameGapResetCount,
            long worstFrameGapMs
    ) {
        this(
                scenarioId,
                scenarioDisplayName,
                evaluatedAtEpochMs,
                completed,
                passed,
                exactTextMatch,
                exactTextMatch,
                primaryCallsignScore,
                textTokenRecall,
                textTokenRecall,
                callsignRecall,
                hintRecall,
                qsoSemanticScore,
                expectedNormalizedText,
                actualNormalizedText,
                expectedNormalizedText,
                actualNormalizedText,
                expectedPhase,
                actualPhase,
                expectedRstSent,
                actualRstSent,
                expectedRstRcvd,
                actualRstRcvd,
                actualCallsigns,
                actualHints,
                missingTextTokens,
                missingTextTokens,
                missingCallsigns,
                missingHints,
                failureReasons,
                normalizedTokenCount,
                totalTokenCount,
                normalizedTokenPairs,
                finalToneLocked,
                endedOnToneOffEvent,
                peakToneRmsAmplitude,
                peakNarrowbandIsolationRatio,
                lockedFrameRatio,
                maxConsecutiveLockedFrames,
                toneActiveUnlockedFrameRatio,
                maxConsecutiveToneActiveUnlockedFrames,
                preferredToneFrequencyHz,
                trackedToneFrequencyHz,
                lastRmsAmplitude,
                lastToneRmsAmplitude,
                lastWidebandResidualRmsAmplitude,
                toneDominanceRatio,
                narrowbandIsolationRatio,
                currentThreshold,
                releaseThreshold,
                noiseFloorEstimate,
                signalFloorEstimate,
                totalToneOnEvents,
                totalToneOffEvents,
                frameGapResetCount,
                worstFrameGapMs
        );
    }

    public CwFixtureEvaluationResult(
            String scenarioId,
            String scenarioDisplayName,
            long evaluatedAtEpochMs,
            boolean completed,
            boolean passed,
            boolean exactRawTextMatch,
            boolean exactNormalizedTextMatch,
            double primaryCallsignScore,
            double rawTextTokenRecall,
            double normalizedTextTokenRecall,
            double callsignRecall,
            double hintRecall,
            double qsoSemanticScore,
            String expectedRawText,
            String actualRawText,
            String expectedNormalizedText,
            String actualNormalizedText,
            String expectedPhase,
            String actualPhase,
            String expectedRstSent,
            String actualRstSent,
            String expectedRstRcvd,
            String actualRstRcvd,
            List<String> actualCallsigns,
            List<String> actualHints,
            List<String> missingRawTextTokens,
            List<String> missingNormalizedTextTokens,
            List<String> missingCallsigns,
            List<String> missingHints,
            List<String> failureReasons,
            int normalizedTokenCount,
            int totalTokenCount,
            List<String> normalizedTokenPairs,
            boolean finalToneLocked,
            boolean endedOnToneOffEvent,
            double peakToneRmsAmplitude,
            double peakNarrowbandIsolationRatio,
            double lockedFrameRatio,
            int maxConsecutiveLockedFrames,
            double toneActiveUnlockedFrameRatio,
            int maxConsecutiveToneActiveUnlockedFrames,
            int preferredToneFrequencyHz,
            int trackedToneFrequencyHz,
            double lastRmsAmplitude,
            double lastToneRmsAmplitude,
            double lastWidebandResidualRmsAmplitude,
            double toneDominanceRatio,
            double narrowbandIsolationRatio,
            int currentThreshold,
            int releaseThreshold,
            int noiseFloorEstimate,
            int signalFloorEstimate,
            int totalToneOnEvents,
            int totalToneOffEvents,
            int frameGapResetCount,
            long worstFrameGapMs
    ) {
        this.scenarioId = scenarioId;
        this.scenarioDisplayName = scenarioDisplayName;
        this.evaluatedAtEpochMs = evaluatedAtEpochMs;
        this.completed = completed;
        this.passed = passed;
        this.exactTextMatch = exactRawTextMatch;
        this.exactRawTextMatch = exactRawTextMatch;
        this.exactNormalizedTextMatch = exactNormalizedTextMatch;
        this.primaryCallsignScore = primaryCallsignScore;
        this.textTokenRecall = rawTextTokenRecall;
        this.rawTextTokenRecall = rawTextTokenRecall;
        this.normalizedTextTokenRecall = normalizedTextTokenRecall;
        this.callsignRecall = callsignRecall;
        this.hintRecall = hintRecall;
        this.qsoSemanticScore = qsoSemanticScore;
        this.expectedRawText = expectedRawText;
        this.actualRawText = actualRawText;
        this.expectedNormalizedText = expectedNormalizedText;
        this.actualNormalizedText = actualNormalizedText;
        this.expectedPhase = expectedPhase;
        this.actualPhase = actualPhase;
        this.expectedRstSent = expectedRstSent;
        this.actualRstSent = actualRstSent;
        this.expectedRstRcvd = expectedRstRcvd;
        this.actualRstRcvd = actualRstRcvd;
        this.actualCallsigns = new ArrayList<>(actualCallsigns);
        this.actualHints = new ArrayList<>(actualHints);
        this.missingTextTokens = new ArrayList<>(missingRawTextTokens);
        this.missingRawTextTokens = new ArrayList<>(missingRawTextTokens);
        this.missingNormalizedTextTokens = new ArrayList<>(missingNormalizedTextTokens);
        this.missingCallsigns = new ArrayList<>(missingCallsigns);
        this.missingHints = new ArrayList<>(missingHints);
        this.failureReasons = new ArrayList<>(failureReasons);
        this.normalizedTokenCount = normalizedTokenCount;
        this.totalTokenCount = totalTokenCount;
        this.normalizedTokenPairs = new ArrayList<>(normalizedTokenPairs);
        this.finalToneLocked = finalToneLocked;
        this.endedOnToneOffEvent = endedOnToneOffEvent;
        this.peakToneRmsAmplitude = peakToneRmsAmplitude;
        this.peakNarrowbandIsolationRatio = peakNarrowbandIsolationRatio;
        this.lockedFrameRatio = lockedFrameRatio;
        this.maxConsecutiveLockedFrames = maxConsecutiveLockedFrames;
        this.toneActiveUnlockedFrameRatio = toneActiveUnlockedFrameRatio;
        this.maxConsecutiveToneActiveUnlockedFrames = maxConsecutiveToneActiveUnlockedFrames;
        this.preferredToneFrequencyHz = preferredToneFrequencyHz;
        this.trackedToneFrequencyHz = trackedToneFrequencyHz;
        this.lastRmsAmplitude = lastRmsAmplitude;
        this.lastToneRmsAmplitude = lastToneRmsAmplitude;
        this.lastWidebandResidualRmsAmplitude = lastWidebandResidualRmsAmplitude;
        this.toneDominanceRatio = toneDominanceRatio;
        this.narrowbandIsolationRatio = narrowbandIsolationRatio;
        this.currentThreshold = currentThreshold;
        this.releaseThreshold = releaseThreshold;
        this.noiseFloorEstimate = noiseFloorEstimate;
        this.signalFloorEstimate = signalFloorEstimate;
        this.totalToneOnEvents = totalToneOnEvents;
        this.totalToneOffEvents = totalToneOffEvents;
        this.frameGapResetCount = frameGapResetCount;
        this.worstFrameGapMs = worstFrameGapMs;
    }

    public String scenarioId() {
        return scenarioId;
    }

    public String scenarioDisplayName() {
        return scenarioDisplayName;
    }

    public long evaluatedAtEpochMs() {
        return evaluatedAtEpochMs;
    }

    public boolean passed() {
        return passed;
    }

    public boolean completed() {
        return completed;
    }

    public boolean exactTextMatch() {
        return exactRawTextMatch;
    }

    public boolean exactRawTextMatch() {
        return exactRawTextMatch;
    }

    public boolean exactNormalizedTextMatch() {
        return exactNormalizedTextMatch;
    }

    public double primaryCallsignScore() {
        return primaryCallsignScore;
    }

    public double textTokenRecall() {
        return rawTextTokenRecall;
    }

    public double rawTextTokenRecall() {
        return rawTextTokenRecall;
    }

    public double normalizedTextTokenRecall() {
        return normalizedTextTokenRecall;
    }

    public double callsignRecall() {
        return callsignRecall;
    }

    public double hintRecall() {
        return hintRecall;
    }

    public double qsoSemanticScore() {
        return qsoSemanticScore;
    }

    public String expectedRawText() {
        return expectedRawText;
    }

    public String actualRawText() {
        return actualRawText;
    }

    public String expectedNormalizedText() {
        return expectedNormalizedText;
    }

    public String actualNormalizedText() {
        return actualNormalizedText;
    }

    public String expectedPhase() {
        return expectedPhase;
    }

    public String actualPhase() {
        return actualPhase;
    }

    public String expectedRstSent() {
        return expectedRstSent;
    }

    public String actualRstSent() {
        return actualRstSent;
    }

    public String expectedRstRcvd() {
        return expectedRstRcvd;
    }

    public String actualRstRcvd() {
        return actualRstRcvd;
    }

    public List<String> actualCallsigns() {
        return new ArrayList<>(actualCallsigns);
    }

    public List<String> actualHints() {
        return new ArrayList<>(actualHints);
    }

    public List<String> missingTextTokens() {
        return new ArrayList<>(missingRawTextTokens);
    }

    public List<String> missingRawTextTokens() {
        return new ArrayList<>(missingRawTextTokens);
    }

    public List<String> missingNormalizedTextTokens() {
        return new ArrayList<>(missingNormalizedTextTokens);
    }

    public List<String> missingCallsigns() {
        return new ArrayList<>(missingCallsigns);
    }

    public List<String> missingHints() {
        return new ArrayList<>(missingHints);
    }

    public List<String> failureReasons() {
        return new ArrayList<>(failureReasons);
    }

    public int normalizedTokenCount() {
        return normalizedTokenCount;
    }

    public int totalTokenCount() {
        return totalTokenCount;
    }

    public double normalizedTokenRatio() {
        if (totalTokenCount <= 0) {
            return 0.0d;
        }
        return normalizedTokenCount / (double) totalTokenCount;
    }

    public List<String> normalizedTokenPairs() {
        return new ArrayList<>(normalizedTokenPairs);
    }

    public boolean finalToneLocked() {
        return finalToneLocked;
    }

    public boolean endedOnToneOffEvent() {
        return endedOnToneOffEvent;
    }

    public double peakToneRmsAmplitude() {
        return peakToneRmsAmplitude;
    }

    public double peakNarrowbandIsolationRatio() {
        return peakNarrowbandIsolationRatio;
    }

    public double lockedFrameRatio() {
        return lockedFrameRatio;
    }

    public int maxConsecutiveLockedFrames() {
        return maxConsecutiveLockedFrames;
    }

    public double toneActiveUnlockedFrameRatio() {
        return toneActiveUnlockedFrameRatio;
    }

    public int maxConsecutiveToneActiveUnlockedFrames() {
        return maxConsecutiveToneActiveUnlockedFrames;
    }

    public int preferredToneFrequencyHz() {
        return preferredToneFrequencyHz;
    }

    public int trackedToneFrequencyHz() {
        return trackedToneFrequencyHz;
    }

    public int trackingErrorHz() {
        return trackedToneFrequencyHz - preferredToneFrequencyHz;
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

    public int totalToneOnEvents() {
        return totalToneOnEvents;
    }

    public int totalToneOffEvents() {
        return totalToneOffEvents;
    }

    public int frameGapResetCount() {
        return frameGapResetCount;
    }

    public long worstFrameGapMs() {
        return worstFrameGapMs;
    }

    private boolean frontEndHistorySuggestsSignalLoss() {
        return CwFrontEndHealthClassifier.suggestsSignalLoss(
                peakToneRmsAmplitude,
                peakNarrowbandIsolationRatio,
                lockedFrameRatio,
                maxConsecutiveLockedFrames
        );
    }

    private boolean frontEndHistorySuggestsEarlierHealthyLock() {
        return CwFrontEndHealthClassifier.suggestsEarlierHealthyLock(
                peakNarrowbandIsolationRatio,
                lockedFrameRatio,
                maxConsecutiveLockedFrames
        );
    }

    private boolean frontEndHistorySuggestsCleanRelease() {
        return CwFrontEndHealthClassifier.suggestsCleanRelease(
                finalToneLocked,
                endedOnToneOffEvent,
                peakNarrowbandIsolationRatio,
                lockedFrameRatio,
                maxConsecutiveLockedFrames,
                toneActiveUnlockedFrameRatio,
                maxConsecutiveToneActiveUnlockedFrames
        );
    }

    private boolean frontEndHistorySuggestsWrongToneLock() {
        return CwFrontEndHealthClassifier.suggestsWrongToneLock(
                finalToneLocked,
                peakNarrowbandIsolationRatio,
                lockedFrameRatio,
                maxConsecutiveLockedFrames,
                trackingErrorHz()
        );
    }

    public String frontEndQualityCode() {
        return CwFrontEndHealthClassifier.qualityCode(
                finalToneLocked,
                endedOnToneOffEvent,
                peakToneRmsAmplitude,
                peakNarrowbandIsolationRatio,
                lockedFrameRatio,
                maxConsecutiveLockedFrames,
                toneActiveUnlockedFrameRatio,
                maxConsecutiveToneActiveUnlockedFrames,
                trackingErrorHz()
        );
    }

    public String frontEndQualityLabel() {
        return CwFrontEndHealthClassifier.qualityLabel(
                frontEndQualityCode(),
                frontEndHistorySuggestsCleanRelease()
        );
    }

    public String likelyBottleneckCode() {
        if (passed) {
            return "OK";
        }
        if (!completed) {
            return "RUN";
        }
        if (frontEndHistorySuggestsWrongToneLock()) {
            return "TRK";
        }
        if (frontEndHistorySuggestsSignalLoss()) {
            return "SIG";
        }
        if (textTokenRecall < 0.45d
                && callsignRecall < 0.5d
                && qsoSemanticScore < 0.5d
                && hintRecall < 0.5d) {
            return "SIG";
        }
        if (frontEndHistorySuggestsEarlierHealthyLock()) {
            if (qsoSemanticScore < 1.0d && textTokenRecall >= 0.75d) {
                return "QSO";
            }
            if (primaryCallsignScore < 1.0d || callsignRecall < 1.0d || hintRecall < 1.0d) {
                return "INT";
            }
            if (textTokenRecall < 0.75d) {
                return "DEC";
            }
        }
        if (textTokenRecall < 0.75d && qsoSemanticScore < 1.0d) {
            return "DEC";
        }
        if (qsoSemanticScore < 1.0d
                && primaryCallsignScore >= 1.0d
                && callsignRecall >= 1.0d
                && textTokenRecall >= 0.75d) {
            return "QSO";
        }
        if (primaryCallsignScore < 1.0d || callsignRecall < 1.0d || hintRecall < 1.0d) {
            return "INT";
        }
        if (qsoSemanticScore < 1.0d) {
            return "QSO";
        }
        return "MIX";
    }

    public String likelyBottleneckLabel() {
        switch (likelyBottleneckCode()) {
            case "OK":
                return "Baseline pass";
            case "RUN":
                return "Replay did not complete";
            case "TRK":
                return "Wrong-tone acquisition / tracking";
            case "SIG":
                return "Front-end signal/timing acquisition";
            case "DEC":
                return "Timing or symbol decoding";
            case "INT":
                return "Interpreter / callsign extraction";
            case "QSO":
                return "QSO state / semantic mapping";
            default:
                return "Mixed pipeline drift";
        }
    }

    public String recoveryPressureCode() {
        double ratio = normalizedTokenRatio();
        if (normalizedTokenCount <= 0 || totalTokenCount <= 0) {
            return "NONE";
        }
        if (normalizedTokenCount >= 4 || ratio >= 0.30d) {
            return "HIGH";
        }
        if (normalizedTokenCount >= 2 || ratio >= 0.15d) {
            return "MED";
        }
        return "LOW";
    }

    public String recoveryPressureLabel() {
        switch (recoveryPressureCode()) {
            case "HIGH":
                return "Heavy best-effort normalization";
            case "MED":
                return "Moderate best-effort normalization";
            case "LOW":
                return "Light best-effort normalization";
            default:
                return "No explicit token recovery";
        }
    }

    public List<String> diagnosticNotes() {
        ArrayList<String> notes = new ArrayList<>();
        if (!completed) {
            notes.add("Replay ended before a full evaluation window completed.");
        }
        if (frontEndHistorySuggestsSignalLoss()) {
            notes.add("Front-end history never formed a convincing narrow-band lock, so this looks more like acquisition loss than late-stage interpretation drift.");
        } else if (frontEndHistorySuggestsWrongToneLock()) {
            notes.add("Front-end formed a strong stable lock, but the tracked tone stayed meaningfully offset from the preferred target, which suggests wrong-tone acquisition rather than ordinary weak copy.");
        } else if (frontEndHistorySuggestsCleanRelease()) {
            notes.add("Front-end held a healthy lock during active tone windows and then ended on a clean tone-off release, so the final unlocked state looks like normal tail silence rather than a true late drop.");
        } else if (frontEndHistorySuggestsEarlierHealthyLock()) {
            notes.add("Front-end history shows a usable earlier lock window, so the latest mismatch likely happened after acquisition rather than before it.");
        } else if (textTokenRecall < 0.45d
                && callsignRecall < 0.5d
                && qsoSemanticScore < 0.5d
                && hintRecall < 0.5d) {
            notes.add("Text and callsigns both collapsed while semantic recovery stayed weak, which points more to front-end loss than late-stage semantics.");
        } else if (textTokenRecall < 0.75d) {
            notes.add("Text recall dropped below baseline before semantics fully engaged, suggesting timing or decoder boundary drift.");
        }
        if (primaryCallsignScore < 1.0d || callsignRecall < 1.0d) {
            notes.add("Callsign extraction lagged behind raw text recovery.");
        }
        if (hintRecall < 1.0d && textTokenRecall >= 0.75d) {
            notes.add("Core text survived, but phrase interpretation missed expected operating intent.");
        }
        if (qsoSemanticScore < 1.0d && textTokenRecall >= 0.75d) {
            notes.add("Decoded content was mostly present, but QSO phase/report mapping drifted.");
        }
        if ("HIGH".equals(recoveryPressureCode()) && frontEndHistorySuggestsEarlierHealthyLock()) {
            notes.add("Downstream output depended heavily on best-effort token normalization despite a usable earlier front-end lock window.");
        } else if (!"NONE".equals(recoveryPressureCode()) && qsoSemanticScore >= 1.0d) {
            notes.add("Semantic/QSO outcome survived with visible best-effort token normalization pressure.");
        }
        if (notes.isEmpty()) {
            notes.add("Scores suggest a mixed but non-catastrophic mismatch.");
        }
        return notes;
    }

    public String renderCompactSummary() {
        return scenarioDisplayName
                + " ["
                + scenarioId
                + "] "
                + (passed ? "PASS" : "CHECK")
                + " P:"
                + percent(primaryCallsignScore)
                + " R:"
                + percent(rawTextTokenRecall)
                + " N:"
                + percent(normalizedTextTokenRecall)
                + " C:"
                + percent(callsignRecall)
                + " Q:"
                + percent(qsoSemanticScore)
                + " H:"
                + percent(hintRecall)
                + " R:"
                + recoveryPressureCode()
                + " F:"
                + frontEndQualityCode()
                + " D:"
                + likelyBottleneckCode();
    }

    public String renderSummary() {
        StringBuilder builder = new StringBuilder();
        builder.append("Fixture: ").append(scenarioDisplayName).append(" [").append(scenarioId).append("]");
        builder.append("\nRun status: ").append(completed ? "completed" : "stopped early");
        builder.append("\nOverall: ").append(passed ? "PASS" : "CHECK");
        builder.append("\nLikely bottleneck: ").append(likelyBottleneckLabel());
        builder.append("\nPrimary callsign: ").append(percent(primaryCallsignScore));
        builder.append("\nRaw text recall: ").append(percent(rawTextTokenRecall))
                .append(exactRawTextMatch ? " (exact)" : " (approx)");
        builder.append("\nNormalized text recall: ").append(percent(normalizedTextTokenRecall))
                .append(exactNormalizedTextMatch ? " (exact)" : " (approx)");
        builder.append("\nCallsign recall: ").append(percent(callsignRecall));
        builder.append("\nQSO semantics: ").append(percent(qsoSemanticScore));
        builder.append("\nHint recall: ").append(percent(hintRecall));
        builder.append("\nRecovery pressure: ").append(recoveryPressureLabel())
                .append(" (")
                .append(normalizedTokenCount)
                .append("/")
                .append(totalTokenCount)
                .append(" token(s)");
        if (!normalizedTokenPairs.isEmpty()) {
            builder.append("; ").append(renderList(normalizedTokenPairs));
        }
        builder.append(")");
        builder.append("\nFront-end quality: ").append(frontEndQualityLabel());
        if (peakToneRmsAmplitude > 0.0d || peakNarrowbandIsolationRatio > 0.0d || lockedFrameRatio > 0.0d) {
            builder.append("\nFront-end history: finalLock=").append(finalToneLocked ? "yes" : "no")
                    .append(", cleanRelease=").append(frontEndHistorySuggestsCleanRelease() ? "yes" : "no")
                    .append(", wrongTone=").append(frontEndHistorySuggestsWrongToneLock() ? "yes" : "no")
                    .append(", pref=").append(preferredToneFrequencyHz).append("Hz")
                    .append(", tracked=").append(trackedToneFrequencyHz).append("Hz")
                    .append(", error=").append(String.format(Locale.US, "%+d", trackingErrorHz())).append("Hz")
                    .append(", peakToneRms=").append(String.format(Locale.US, "%.0f", peakToneRmsAmplitude))
                    .append(", peakIsolation=").append(percent(peakNarrowbandIsolationRatio))
                    .append(", lockCoverage=").append(percent(lockedFrameRatio))
                    .append(", toneActiveUnlock=").append(percent(toneActiveUnlockedFrameRatio))
                    .append(", bestLockRun=").append(maxConsecutiveLockedFrames).append(" frame(s)")
                    .append(", worstToneActiveGap=").append(maxConsecutiveToneActiveUnlockedFrames).append(" frame(s)");
        }
        if (hasDetailedSignalMetrics()) {
            builder.append("\nFront-end levels: lastRms=").append(String.format(Locale.US, "%.0f", lastRmsAmplitude))
                    .append(", lastToneRms=").append(String.format(Locale.US, "%.0f", lastToneRmsAmplitude))
                    .append(", residual=").append(String.format(Locale.US, "%.0f", lastWidebandResidualRmsAmplitude))
                    .append(", dominance=").append(percent(toneDominanceRatio))
                    .append(", isolation=").append(percent(narrowbandIsolationRatio))
                    .append(", threshold=").append(currentThreshold)
                    .append(", release=").append(releaseThreshold)
                    .append(", noiseFloor=").append(noiseFloorEstimate)
                    .append(", signalFloor=").append(signalFloorEstimate)
                    .append(", toneOnOff=").append(totalToneOnEvents).append("/").append(totalToneOffEvents)
                    .append(", frameGapResets=").append(frameGapResetCount)
                    .append(", worstFrameGapMs=").append(worstFrameGapMs);
        }
        builder.append("\nExpected raw text: ").append(safeText(expectedRawText));
        builder.append("\nActual raw text: ").append(safeText(actualRawText));
        builder.append("\nExpected normalized text: ").append(safeText(expectedNormalizedText));
        builder.append("\nActual normalized text: ").append(safeText(actualNormalizedText));
        if (expectedPhase != null || actualPhase != null) {
            builder.append("\nExpected phase: ").append(safeText(expectedPhase));
            builder.append("\nActual phase: ").append(safeText(actualPhase));
        }
        if (expectedRstSent != null || actualRstSent != null) {
            builder.append("\nExpected RST sent: ").append(safeText(expectedRstSent));
            builder.append("\nActual RST sent: ").append(safeText(actualRstSent));
        }
        if (expectedRstRcvd != null || actualRstRcvd != null) {
            builder.append("\nExpected RST rcvd: ").append(safeText(expectedRstRcvd));
            builder.append("\nActual RST rcvd: ").append(safeText(actualRstRcvd));
        }
        if (!missingRawTextTokens.isEmpty()) {
            builder.append("\nMissing raw text tokens: ").append(renderList(missingRawTextTokens));
        }
        if (!missingNormalizedTextTokens.isEmpty()) {
            builder.append("\nMissing normalized text tokens: ").append(renderList(missingNormalizedTextTokens));
        }
        builder.append("\nActual callsigns: ").append(renderList(actualCallsigns));
        if (!missingCallsigns.isEmpty()) {
            builder.append("\nMissing callsigns: ").append(renderList(missingCallsigns));
        }
        builder.append("\nActual hints: ").append(renderList(actualHints));
        if (!missingHints.isEmpty()) {
            builder.append("\nMissing hints: ").append(renderList(missingHints));
        }
        builder.append("\nDiagnostic notes: ").append(renderList(diagnosticNotes()));
        if (!failureReasons.isEmpty()) {
            builder.append("\nFailure reasons: ").append(renderList(failureReasons));
        }
        return builder.toString();
    }

    private boolean hasDetailedSignalMetrics() {
        return lastRmsAmplitude > 0.0d
                || lastToneRmsAmplitude > 0.0d
                || lastWidebandResidualRmsAmplitude > 0.0d
                || toneDominanceRatio > 0.0d
                || narrowbandIsolationRatio > 0.0d
                || currentThreshold > 0
                || releaseThreshold > 0
                || noiseFloorEstimate > 0
                || signalFloorEstimate > 0
                || totalToneOnEvents > 0
                || totalToneOffEvents > 0
                || frameGapResetCount > 0
                || worstFrameGapMs > 0L;
    }

    private String percent(double value) {
        return String.format(Locale.US, "%.0f%%", value * 100.0d);
    }

    private String renderList(List<String> values) {
        return values.isEmpty() ? "(none)" : String.join(", ", values);
    }

    private String safeText(String value) {
        return value == null || value.isEmpty() ? "(none)" : value;
    }
}
