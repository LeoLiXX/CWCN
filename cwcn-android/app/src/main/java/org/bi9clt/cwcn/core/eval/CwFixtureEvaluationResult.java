package org.bi9clt.cwcn.core.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CwFixtureEvaluationResult {
    private final String scenarioId;
    private final String scenarioDisplayName;
    private final long evaluatedAtEpochMs;
    private final boolean completed;
    private final boolean passed;
    private final boolean exactTextMatch;
    private final double primaryCallsignScore;
    private final double textTokenRecall;
    private final double callsignRecall;
    private final double hintRecall;
    private final double qsoSemanticScore;
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
    private final List<String> missingTextTokens;
    private final List<String> missingCallsigns;
    private final List<String> missingHints;
    private final List<String> failureReasons;
    private final boolean finalToneLocked;
    private final boolean endedOnToneOffEvent;
    private final double peakToneRmsAmplitude;
    private final double peakNarrowbandIsolationRatio;
    private final double lockedFrameRatio;
    private final int maxConsecutiveLockedFrames;
    private final double toneActiveUnlockedFrameRatio;
    private final int maxConsecutiveToneActiveUnlockedFrames;

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
                primaryCallsignScore,
                textTokenRecall,
                callsignRecall,
                hintRecall,
                qsoSemanticScore,
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
                missingCallsigns,
                missingHints,
                failureReasons,
                false,
                false,
                0.0d,
                0.0d,
                0.0d,
                0,
                0.0d,
                0
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
            int maxConsecutiveToneActiveUnlockedFrames
    ) {
        this.scenarioId = scenarioId;
        this.scenarioDisplayName = scenarioDisplayName;
        this.evaluatedAtEpochMs = evaluatedAtEpochMs;
        this.completed = completed;
        this.passed = passed;
        this.exactTextMatch = exactTextMatch;
        this.primaryCallsignScore = primaryCallsignScore;
        this.textTokenRecall = textTokenRecall;
        this.callsignRecall = callsignRecall;
        this.hintRecall = hintRecall;
        this.qsoSemanticScore = qsoSemanticScore;
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
        this.missingTextTokens = new ArrayList<>(missingTextTokens);
        this.missingCallsigns = new ArrayList<>(missingCallsigns);
        this.missingHints = new ArrayList<>(missingHints);
        this.failureReasons = new ArrayList<>(failureReasons);
        this.finalToneLocked = finalToneLocked;
        this.endedOnToneOffEvent = endedOnToneOffEvent;
        this.peakToneRmsAmplitude = peakToneRmsAmplitude;
        this.peakNarrowbandIsolationRatio = peakNarrowbandIsolationRatio;
        this.lockedFrameRatio = lockedFrameRatio;
        this.maxConsecutiveLockedFrames = maxConsecutiveLockedFrames;
        this.toneActiveUnlockedFrameRatio = toneActiveUnlockedFrameRatio;
        this.maxConsecutiveToneActiveUnlockedFrames = maxConsecutiveToneActiveUnlockedFrames;
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
        return exactTextMatch;
    }

    public double primaryCallsignScore() {
        return primaryCallsignScore;
    }

    public double textTokenRecall() {
        return textTokenRecall;
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
        return new ArrayList<>(missingTextTokens);
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

    private boolean frontEndHistorySuggestsSignalLoss() {
        return peakToneRmsAmplitude > 0.0d
                && peakNarrowbandIsolationRatio < 0.35d
                && lockedFrameRatio < 0.08d
                && maxConsecutiveLockedFrames < 2;
    }

    private boolean frontEndHistorySuggestsEarlierHealthyLock() {
        return peakNarrowbandIsolationRatio >= 0.55d
                && lockedFrameRatio >= 0.15d
                && maxConsecutiveLockedFrames >= 3;
    }

    private boolean frontEndHistorySuggestsCleanRelease() {
        return !finalToneLocked
                && endedOnToneOffEvent
                && frontEndHistorySuggestsEarlierHealthyLock()
                && toneActiveUnlockedFrameRatio <= 0.16d
                && maxConsecutiveToneActiveUnlockedFrames <= 1;
    }

    public String frontEndQualityCode() {
        boolean hasFrontEndHistory = peakToneRmsAmplitude > 0.0d
                || peakNarrowbandIsolationRatio > 0.0d
                || lockedFrameRatio > 0.0d
                || maxConsecutiveLockedFrames > 0;
        if (!hasFrontEndHistory) {
            return "NA";
        }
        if ((finalToneLocked || frontEndHistorySuggestsCleanRelease())
                && peakNarrowbandIsolationRatio >= 0.55d
                && lockedFrameRatio >= 0.25d
                && maxConsecutiveLockedFrames >= 4) {
            return "GOOD";
        }
        if (!finalToneLocked && frontEndHistorySuggestsEarlierHealthyLock()) {
            return "DROP";
        }
        if (frontEndHistorySuggestsSignalLoss()) {
            return "MISS";
        }
        return "WEAK";
    }

    public String frontEndQualityLabel() {
        switch (frontEndQualityCode()) {
            case "GOOD":
                return frontEndHistorySuggestsCleanRelease()
                        ? "Healthy lock with clean release"
                        : "Healthy lock retained";
            case "DROP":
                return "Earlier lock, later drop";
            case "MISS":
                return "No convincing lock formed";
            case "WEAK":
                return "Partial / unstable acquisition";
            default:
                return "No front-end history available";
        }
    }

    public String likelyBottleneckCode() {
        if (passed) {
            return "OK";
        }
        if (!completed) {
            return "RUN";
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

    public List<String> diagnosticNotes() {
        ArrayList<String> notes = new ArrayList<>();
        if (!completed) {
            notes.add("Replay ended before a full evaluation window completed.");
        }
        if (frontEndHistorySuggestsSignalLoss()) {
            notes.add("Front-end history never formed a convincing narrow-band lock, so this looks more like acquisition loss than late-stage interpretation drift.");
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
                + " T:"
                + percent(textTokenRecall)
                + " C:"
                + percent(callsignRecall)
                + " Q:"
                + percent(qsoSemanticScore)
                + " H:"
                + percent(hintRecall)
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
        builder.append("\nText recall: ").append(percent(textTokenRecall))
                .append(exactTextMatch ? " (exact)" : " (approx)");
        builder.append("\nCallsign recall: ").append(percent(callsignRecall));
        builder.append("\nQSO semantics: ").append(percent(qsoSemanticScore));
        builder.append("\nHint recall: ").append(percent(hintRecall));
        builder.append("\nFront-end quality: ").append(frontEndQualityLabel());
        if (peakToneRmsAmplitude > 0.0d || peakNarrowbandIsolationRatio > 0.0d || lockedFrameRatio > 0.0d) {
            builder.append("\nFront-end history: finalLock=").append(finalToneLocked ? "yes" : "no")
                    .append(", cleanRelease=").append(frontEndHistorySuggestsCleanRelease() ? "yes" : "no")
                    .append(", peakToneRms=").append(String.format(Locale.US, "%.0f", peakToneRmsAmplitude))
                    .append(", peakIsolation=").append(percent(peakNarrowbandIsolationRatio))
                    .append(", lockCoverage=").append(percent(lockedFrameRatio))
                    .append(", toneActiveUnlock=").append(percent(toneActiveUnlockedFrameRatio))
                    .append(", bestLockRun=").append(maxConsecutiveLockedFrames).append(" frame(s)")
                    .append(", worstToneActiveGap=").append(maxConsecutiveToneActiveUnlockedFrames).append(" frame(s)");
        }
        builder.append("\nExpected text: ").append(safeText(expectedNormalizedText));
        builder.append("\nActual text: ").append(safeText(actualNormalizedText));
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
        if (!missingTextTokens.isEmpty()) {
            builder.append("\nMissing text tokens: ").append(renderList(missingTextTokens));
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
