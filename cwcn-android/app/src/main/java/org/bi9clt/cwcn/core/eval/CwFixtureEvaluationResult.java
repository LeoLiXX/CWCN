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

    public String likelyBottleneckCode() {
        if (passed) {
            return "OK";
        }
        if (!completed) {
            return "RUN";
        }
        if (textTokenRecall < 0.45d
                && callsignRecall < 0.5d
                && qsoSemanticScore < 0.5d
                && hintRecall < 0.5d) {
            return "SIG";
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
        if (textTokenRecall < 0.45d
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
