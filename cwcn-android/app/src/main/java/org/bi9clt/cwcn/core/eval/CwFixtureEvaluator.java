package org.bi9clt.cwcn.core.eval;

import org.bi9clt.cwcn.core.interpreter.CwInterpreterSnapshot;
import org.bi9clt.cwcn.core.qso.QsoDraftSnapshot;
import org.bi9clt.cwcn.core.qso.QsoPhase;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CwFixtureEvaluator {
    private CwFixtureEvaluator() {
    }

    public static CwFixtureEvaluationResult evaluate(
            CwFixtureScenario scenario,
            CwInterpreterSnapshot interpreterSnapshot,
            QsoDraftSnapshot qsoSnapshot,
            boolean completed
    ) {
        return evaluate(scenario, interpreterSnapshot, qsoSnapshot, null, completed);
    }

    public static CwFixtureEvaluationResult evaluate(
            CwFixtureScenario scenario,
            CwInterpreterSnapshot interpreterSnapshot,
            QsoDraftSnapshot qsoSnapshot,
            CwSignalSnapshot signalSnapshot,
            boolean completed
    ) {
        String expectedText = normalizeText(scenario.expectedNormalizedText());
        String actualText = normalizeText(interpreterSnapshot.normalizedText());
        boolean exactTextMatch = expectedText.equals(actualText);
        List<String> missingTextTokens = computeMissingTextTokens(expectedText, actualText);
        double primaryCallsignScore = computePrimaryCallsignScore(
                scenario.expectedCallsigns(),
                interpreterSnapshot.primaryCallsignCandidate()
        );

        double textTokenRecall = computeTokenRecall(expectedText, actualText);
        double callsignRecall = computeValueRecall(
                scenario.expectedCallsigns(),
                interpreterSnapshot.callsignCandidates()
        );
        double hintRecall = computeValueRecall(
                scenario.expectedHints(),
                interpreterSnapshot.phraseHints()
        );
        String actualPhase = qsoSnapshot == null || qsoSnapshot.phase() == null
                ? null
                : qsoSnapshot.phase().name();
        String actualRstSent = qsoSnapshot == null ? null : qsoSnapshot.rstSentCandidate();
        String actualRstRcvd = qsoSnapshot == null ? null : qsoSnapshot.rstRcvdCandidate();
        double qsoSemanticScore = computeQsoSemanticScore(
                scenario.expectedPhase(),
                scenario.expectedRstSent(),
                scenario.expectedRstRcvd(),
                actualPhase,
                actualRstSent,
                actualRstRcvd
        );

        List<String> missingCallsigns = computeMissingValues(
                scenario.expectedCallsigns(),
                interpreterSnapshot.callsignCandidates()
        );
        List<String> missingHints = computeMissingValues(
                scenario.expectedHints(),
                interpreterSnapshot.phraseHints()
        );

        List<String> failureReasons = buildFailureReasons(
                completed,
                exactTextMatch,
                primaryCallsignScore,
                textTokenRecall,
                callsignRecall,
                hintRecall,
                qsoSemanticScore,
                missingTextTokens,
                missingCallsigns,
                missingHints,
                scenario.expectedPhase(),
                actualPhase,
                scenario.expectedRstSent(),
                actualRstSent,
                scenario.expectedRstRcvd(),
                actualRstRcvd
        );

        boolean passed = completed
                && primaryCallsignScore >= 1.0d
                && textTokenRecall >= 0.75d
                && callsignRecall >= 1.0d
                && qsoSemanticScore >= 1.0d
                && hintRecall >= 1.0d;

        return new CwFixtureEvaluationResult(
                scenario.id(),
                scenario.displayName(),
                System.currentTimeMillis(),
                completed,
                passed,
                exactTextMatch,
                primaryCallsignScore,
                textTokenRecall,
                callsignRecall,
                hintRecall,
                qsoSemanticScore,
                expectedText,
                actualText,
                scenario.expectedPhase() == null ? null : scenario.expectedPhase().name(),
                actualPhase,
                normalizeOptionalValue(scenario.expectedRstSent()),
                normalizeOptionalValue(actualRstSent),
                normalizeOptionalValue(scenario.expectedRstRcvd()),
                normalizeOptionalValue(actualRstRcvd),
                normalizeValueList(interpreterSnapshot.callsignCandidates()),
                normalizeValueList(interpreterSnapshot.phraseHints()),
                missingTextTokens,
                missingCallsigns,
                missingHints,
                failureReasons,
                signalSnapshot != null && signalSnapshot.targetToneLocked(),
                signalSnapshot != null
                        && signalSnapshot.lastEvent() != null
                        && signalSnapshot.lastEvent().type() == CwToneEvent.Type.TONE_OFF,
                signalSnapshot == null ? 0.0d : signalSnapshot.peakToneRmsAmplitude(),
                signalSnapshot == null ? 0.0d : signalSnapshot.peakNarrowbandIsolationRatio(),
                signalSnapshot == null ? 0.0d : signalSnapshot.lockedFrameRatio(),
                signalSnapshot == null ? 0 : signalSnapshot.maxConsecutiveLockedFrames(),
                signalSnapshot == null ? 0.0d : signalSnapshot.toneActiveUnlockedFrameRatio(),
                signalSnapshot == null ? 0 : signalSnapshot.maxConsecutiveToneActiveUnlockedFrames()
        );
    }

    private static double computeTokenRecall(String expectedText, String actualText) {
        List<String> expectedTokens = tokenize(expectedText);
        if (expectedTokens.isEmpty()) {
            return 1.0d;
        }

        Map<String, Integer> actualCounts = countTokens(tokenize(actualText));
        int matched = 0;
        for (String expectedToken : expectedTokens) {
            Integer available = actualCounts.get(expectedToken);
            if (available != null && available > 0) {
                matched += 1;
                actualCounts.put(expectedToken, available - 1);
            }
        }
        return matched / (double) expectedTokens.size();
    }

    private static double computeQsoSemanticScore(
            QsoPhase expectedPhase,
            String expectedRstSent,
            String expectedRstRcvd,
            String actualPhase,
            String actualRstSent,
            String actualRstRcvd
    ) {
        int checks = 0;
        int matched = 0;

        if (expectedPhase != null) {
            checks += 1;
            if (expectedPhase.name().equals(normalizeOptionalValue(actualPhase))) {
                matched += 1;
            }
        }
        if (!normalizeOptionalValue(expectedRstSent).isEmpty()) {
            checks += 1;
            if (normalizeOptionalValue(expectedRstSent).equals(normalizeOptionalValue(actualRstSent))) {
                matched += 1;
            }
        }
        if (!normalizeOptionalValue(expectedRstRcvd).isEmpty()) {
            checks += 1;
            if (normalizeOptionalValue(expectedRstRcvd).equals(normalizeOptionalValue(actualRstRcvd))) {
                matched += 1;
            }
        }

        return checks == 0 ? 1.0d : matched / (double) checks;
    }

    private static double computePrimaryCallsignScore(List<String> expectedCallsigns, String actualPrimaryCallsign) {
        List<String> normalizedExpected = normalizeValueList(expectedCallsigns);
        if (normalizedExpected.isEmpty()) {
            return 1.0d;
        }
        String normalizedPrimary = normalizeText(actualPrimaryCallsign);
        if (normalizedPrimary.isEmpty()) {
            return 0.0d;
        }
        for (String expected : normalizedExpected) {
            if (expected.equals(normalizedPrimary)) {
                return 1.0d;
            }
        }
        return 0.0d;
    }

    private static double computeValueRecall(List<String> expectedValues, List<String> actualValues) {
        List<String> normalizedExpected = normalizeValueList(expectedValues);
        if (normalizedExpected.isEmpty()) {
            return 1.0d;
        }
        Set<String> actualSet = new LinkedHashSet<>(normalizeValueList(actualValues));
        int matched = 0;
        for (String expectedValue : normalizedExpected) {
            if (actualSet.contains(expectedValue)) {
                matched += 1;
            }
        }
        return matched / (double) normalizedExpected.size();
    }

    private static List<String> computeMissingValues(List<String> expectedValues, List<String> actualValues) {
        List<String> normalizedExpected = normalizeValueList(expectedValues);
        Set<String> actualSet = new LinkedHashSet<>(normalizeValueList(actualValues));
        ArrayList<String> missing = new ArrayList<>();
        for (String expectedValue : normalizedExpected) {
            if (!actualSet.contains(expectedValue)) {
                missing.add(expectedValue);
            }
        }
        return missing;
    }

    private static List<String> computeMissingTextTokens(String expectedText, String actualText) {
        List<String> expectedTokens = tokenize(expectedText);
        Map<String, Integer> actualCounts = countTokens(tokenize(actualText));
        ArrayList<String> missing = new ArrayList<>();
        for (String expectedToken : expectedTokens) {
            Integer available = actualCounts.get(expectedToken);
            if (available != null && available > 0) {
                actualCounts.put(expectedToken, available - 1);
            } else {
                missing.add(expectedToken);
            }
        }
        return missing;
    }

    private static List<String> buildFailureReasons(
            boolean completed,
            boolean exactTextMatch,
            double primaryCallsignScore,
            double textTokenRecall,
            double callsignRecall,
            double hintRecall,
            double qsoSemanticScore,
            List<String> missingTextTokens,
            List<String> missingCallsigns,
            List<String> missingHints,
            QsoPhase expectedPhase,
            String actualPhase,
            String expectedRstSent,
            String actualRstSent,
            String expectedRstRcvd,
            String actualRstRcvd
    ) {
        ArrayList<String> reasons = new ArrayList<>();
        if (!completed) {
            reasons.add("Replay did not complete");
        }
        if (primaryCallsignScore < 1.0d) {
            reasons.add("Primary callsign candidate mismatch");
        }
        if (!exactTextMatch && textTokenRecall < 1.0d) {
            reasons.add("Text drift / partial copy");
        }
        if (textTokenRecall < 0.75d) {
            reasons.add("Text token recall below baseline");
        }
        if (!missingTextTokens.isEmpty()) {
            reasons.add("Missing text tokens: " + String.join(" ", dedupeValues(missingTextTokens)));
        }
        if (callsignRecall < 1.0d) {
            reasons.add("Callsign candidate loss");
        }
        if (!missingCallsigns.isEmpty()) {
            reasons.add("Missing callsigns: " + String.join(", ", missingCallsigns));
        }
        if (qsoSemanticScore < 1.0d) {
            reasons.add("QSO state semantics drift");
        }
        if (expectedPhase != null && !expectedPhase.name().equals(normalizeOptionalValue(actualPhase))) {
            reasons.add("Phase mismatch: expected " + expectedPhase.name()
                    + ", actual " + safeValue(actualPhase));
        }
        if (!normalizeOptionalValue(expectedRstSent).isEmpty()
                && !normalizeOptionalValue(expectedRstSent).equals(normalizeOptionalValue(actualRstSent))) {
            reasons.add("RST sent mismatch: expected " + normalizeOptionalValue(expectedRstSent)
                    + ", actual " + safeValue(actualRstSent));
        }
        if (!normalizeOptionalValue(expectedRstRcvd).isEmpty()
                && !normalizeOptionalValue(expectedRstRcvd).equals(normalizeOptionalValue(actualRstRcvd))) {
            reasons.add("RST rcvd mismatch: expected " + normalizeOptionalValue(expectedRstRcvd)
                    + ", actual " + safeValue(actualRstRcvd));
        }
        if (hintRecall < 1.0d) {
            reasons.add("Phrase hint loss");
        }
        if (!missingHints.isEmpty()) {
            reasons.add("Missing hints: " + String.join(", ", missingHints));
        }
        return reasons;
    }

    private static List<String> tokenize(String text) {
        ArrayList<String> tokens = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return tokens;
        }
        String[] parts = text.split("\\s+");
        for (String part : parts) {
            String normalized = normalizeText(part);
            if (!normalized.isEmpty()) {
                tokens.add(normalized);
            }
        }
        return tokens;
    }

    private static Map<String, Integer> countTokens(List<String> tokens) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (String token : tokens) {
            Integer current = counts.get(token);
            counts.put(token, current == null ? 1 : current + 1);
        }
        return counts;
    }

    private static List<String> normalizeValueList(List<String> values) {
        ArrayList<String> normalized = new ArrayList<>();
        if (values == null) {
            return normalized;
        }
        for (String value : values) {
            String cleaned = normalizeText(value);
            if (!cleaned.isEmpty()) {
                normalized.add(cleaned);
            }
        }
        return normalized;
    }

    private static List<String> dedupeValues(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.US).replaceAll("\\s+", " ");
    }

    private static String normalizeOptionalValue(String value) {
        return normalizeText(value);
    }

    private static String safeValue(String value) {
        String normalized = normalizeOptionalValue(value);
        return normalized.isEmpty() ? "(none)" : normalized;
    }
}
