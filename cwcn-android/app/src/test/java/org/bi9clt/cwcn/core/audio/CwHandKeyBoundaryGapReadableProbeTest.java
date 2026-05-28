package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.eval.CwFixtureEvaluator;
import org.bi9clt.cwcn.core.eval.CwFixtureEvaluationResult;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario.PartTimingProfile;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.qso.QsoPhase;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class CwHandKeyBoundaryGapReadableProbeTest {
    private static final int DEFAULT_SQL_PERCENT = 55;

    @Test
    public void printHandKeyBoundaryGapReadableBench() {
        for (CwFixtureScenario scenario : handKeyBoundaryGapScenarios()) {
            EvaluationSummary summary = evaluateScenario(scenario);
            System.out.println(renderSummary(summary));
        }
    }

    private List<CwFixtureScenario> handKeyBoundaryGapScenarios() {
        return Arrays.asList(
                buildHandKeyRaggedCqScenario(),
                buildHandKeyRaggedReportScenario(),
                buildHandKeyClarifyThenReportScenario()
        );
    }

    private CwFixtureScenario buildHandKeyRaggedCqScenario() {
        return new CwFixtureScenario(
                "hand_key_ragged_cq_call",
                "Hand Key Ragged CQ",
                "CQ CQ CQ DE BI9CLT BI9CLT PSE K",
                Collections.singletonList("CQ CQ CQ DE BI9CLT BI9CLT PSE K"),
                1800,
                18,
                700,
                18200,
                320,
                0.0d,
                0,
                0.03d,
                0.00d,
                4,
                4,
                Collections.singletonList(
                        new PartTimingProfile(
                                1.00d,
                                1.02d,
                                0.05d,
                                0.00d,
                                0.00d,
                                1.35d,
                                2.10d,
                                1.70d,
                                0,
                                1.8d,
                                Arrays.asList(2, 5, 9, 12, 15)
                        )
                ),
                220,
                420,
                "CQ CQ CQ DE BI9CLT BI9CLT PSE K",
                Collections.singletonList("BI9CLT"),
                Arrays.asList(
                        "CQ / calling flow",
                        "Station identification / callsign exchange",
                        "Turn handoff / over"
                ),
                QsoPhase.CALLING_CQ,
                null,
                null,
                "Boundary-gap heavy hand-key style fixture: symbol timing stays mostly stable while letter and word spacing hesitates noticeably."
        ).withExpectedRawText("CQ CQ CQ DE BI9CLT BI9CLT PSE K");
    }

    private CwFixtureScenario buildHandKeyRaggedReportScenario() {
        return new CwFixtureScenario(
                "hand_key_ragged_report_exchange",
                "Hand Key Ragged Report",
                "BI9CLT DE BG7YOZ UR 5NN BK",
                Collections.singletonList("BI9CLT DE BG7YOZ UR 5NN BK"),
                1800,
                18,
                670,
                18000,
                360,
                0.0d,
                0,
                0.04d,
                0.00d,
                4,
                4,
                Collections.singletonList(
                        new PartTimingProfile(
                                1.00d,
                                1.03d,
                                0.05d,
                                0.00d,
                                0.00d,
                                1.32d,
                                2.25d,
                                2.40d,
                                0,
                                2.2d,
                                Arrays.asList(6, 8, 11, 15)
                        )
                ),
                220,
                420,
                "BI9CLT DE BG7YOZ UR 5NN BK",
                Arrays.asList("BI9CLT", "BG7YOZ"),
                Arrays.asList(
                        "Station identification / callsign exchange",
                        "Report exchange",
                        "Directed report to called station",
                        "Turn handoff / over"
                ),
                QsoPhase.REPORT_EXCHANGE,
                null,
                "599",
                "Boundary-gap heavy hand-key report fixture with stretched hesitations before and after word boundaries, but no deliberate intra-symbol corruption."
        ).withExpectedRawText("BI9CLT DE BG7YOZ UR 5NN BK");
    }

    private CwFixtureScenario buildHandKeyClarifyThenReportScenario() {
        return new CwFixtureScenario(
                "hand_key_ragged_clarify_then_report",
                "Hand Key Clarify Then Report",
                "BI9??Z AGN PSE K / BI9CLT DE BG7YOZ UR 5NN BK",
                Arrays.asList(
                        "BI9??Z AGN PSE K",
                        "BI9CLT DE BG7YOZ UR 5NN BK"
                ),
                2200,
                17,
                650,
                18000,
                420,
                0.0d,
                0,
                0.05d,
                0.00d,
                4,
                4,
                Arrays.asList(
                        new PartTimingProfile(
                                0.98d,
                                1.00d,
                                0.06d,
                                0.00d,
                                0.00d,
                                1.42d,
                                2.35d,
                                1.85d,
                                2,
                                2.2d
                        ),
                        new PartTimingProfile(
                                1.00d,
                                1.03d,
                                0.04d,
                                0.00d,
                                0.00d,
                                1.24d,
                                1.85d,
                                2.10d,
                                0,
                                1.0d,
                                Arrays.asList(5, 10, 14)
                        )
                ),
                220,
                420,
                "BI9??Z AGN PSE K BI9CLT DE BG7YOZ UR 5NN BK",
                Arrays.asList("BI9CLT", "BG7YOZ"),
                Arrays.asList(
                        "Repeat / clarification request",
                        "Partial callsign / uncertain copy",
                        "Callsign confirmation cycle",
                        "Report exchange",
                        "Directed report to called station",
                        "Turn handoff / over"
                ),
                QsoPhase.REPORT_EXCHANGE,
                null,
                "599",
                "Two-part hand-key style fixture where the first round hesitates heavily at boundaries and the second round settles into a cleaner report."
        ).withExpectedRawText("BI9??Z AGN PSE K BI9CLT DE BG7YOZ UR 5NN BK");
    }

    private EvaluationSummary evaluateScenario(CwFixtureScenario scenario) {
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        List<AudioFrame> frames = source.renderFramesForTesting(scenario);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailedProbeResult =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        scenario.id(),
                        frames,
                        scenario.toneFrequencyHz(),
                        scenario.wpm(),
                        DEFAULT_SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.SEMANTIC_RECOVERY
                );
        CwFixtureEvaluationResult result = CwFixtureEvaluator.evaluate(
                scenario,
                detailedProbeResult.probeResult().interpreterSnapshot(),
                detailedProbeResult.probeResult().qsoDraftSnapshot(),
                detailedProbeResult.probeResult().signalSnapshot(),
                true
        );
        String actualRawText = sanitize(result.actualRawText());
        String actualNormalizedText = sanitize(result.actualNormalizedText());
        String expectedRawText = sanitize(result.expectedRawText());
        return new EvaluationSummary(
                scenario,
                result,
                actualRawText,
                actualNormalizedText,
                expectedRawText,
                charRecall(expectedRawText, actualRawText),
                charWindowRecall(expectedRawText, actualRawText, 0.0d, 0.5d),
                charWindowRecall(expectedRawText, actualRawText, 0.5d, 1.0d),
                charRecall(result.expectedNormalizedText(), actualNormalizedText),
                countTurnTransitions(detailedProbeResult.turnTransitionTraces(), LocalAudioDecodeTestSupport.TurnTransitionTrace.Kind.START),
                countTurnTransitions(detailedProbeResult.turnTransitionTraces(), LocalAudioDecodeTestSupport.TurnTransitionTrace.Kind.END),
                detailedProbeResult.probeResult().timingSnapshot().estimatedWpm()
        );
    }

    private int countTurnTransitions(
            List<LocalAudioDecodeTestSupport.TurnTransitionTrace> traces,
            LocalAudioDecodeTestSupport.TurnTransitionTrace.Kind kind
    ) {
        int count = 0;
        for (LocalAudioDecodeTestSupport.TurnTransitionTrace trace : traces) {
            if (trace != null && trace.kind() == kind) {
                count += 1;
            }
        }
        return count;
    }

    private double charRecall(String expectedText, String actualText) {
        String expected = canonicalize(expectedText);
        String actual = canonicalize(actualText);
        if (expected.isEmpty()) {
            return actual.isEmpty() ? 1.0d : 0.0d;
        }
        return charLcsLength(expected, actual) / (double) expected.length();
    }

    private double charWindowRecall(String expectedText, String actualText, double startFraction, double endFraction) {
        String expected = canonicalize(expectedText);
        String actual = canonicalize(actualText);
        if (expected.isEmpty()) {
            return actual.isEmpty() ? 1.0d : 0.0d;
        }
        int startIndex = Math.max(0, Math.min(expected.length(), (int) Math.floor(expected.length() * startFraction)));
        int endIndex = Math.max(startIndex, Math.min(expected.length(), (int) Math.ceil(expected.length() * endFraction)));
        String window = expected.substring(startIndex, endIndex);
        if (window.isEmpty()) {
            return 1.0d;
        }
        return charLcsLength(window, actual) / (double) window.length();
    }

    private int charLcsLength(String expected, String actual) {
        int[] previous = new int[actual.length() + 1];
        int[] current = new int[actual.length() + 1];
        for (int expectedIndex = 1; expectedIndex <= expected.length(); expectedIndex++) {
            char expectedChar = expected.charAt(expectedIndex - 1);
            for (int actualIndex = 1; actualIndex <= actual.length(); actualIndex++) {
                if (expectedChar == actual.charAt(actualIndex - 1)) {
                    current[actualIndex] = previous[actualIndex - 1] + 1;
                } else {
                    current[actualIndex] = Math.max(previous[actualIndex], current[actualIndex - 1]);
                }
            }
            int[] swap = previous;
            previous = current;
            current = swap;
            Arrays.fill(current, 0);
        }
        return previous[actual.length()];
    }

    private String canonicalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String upper = text.toUpperCase(Locale.US).replace('\u25A1', '?');
        StringBuilder canonical = new StringBuilder(upper.length());
        for (int index = 0; index < upper.length(); index++) {
            char ch = upper.charAt(index);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '?') {
                canonical.append(ch);
            }
        }
        return canonical.toString();
    }

    private String renderSummary(EvaluationSummary summary) {
        return String.format(
                Locale.US,
                "==== %s (%s) ====%n"
                        + "timing=%s%n"
                        + "expected=%s%n"
                        + "actualRaw=%s%n"
                        + "actualNorm=%s%n"
                        + "rawTokenRecall=%.3f rawCharRecall=%.3f firstHalf=%.3f secondHalf=%.3f normCharRecall=%.3f%n"
                        + "turns start=%d end=%d estWpm=%d bottleneck=%s%n",
                summary.scenario.id(),
                summary.scenario.displayName(),
                summary.scenario.timingProfileSummary(),
                summary.expectedRawText,
                summary.actualRawText,
                summary.actualNormalizedText,
                summary.result.textTokenRecall(),
                summary.rawCharRecall,
                summary.firstHalfRawCharRecall,
                summary.secondHalfRawCharRecall,
                summary.normalizedCharRecall,
                summary.turnStartCount,
                summary.turnEndCount,
                summary.estimatedWpm,
                summary.result.likelyBottleneckCode()
        );
    }

    private String sanitize(String value) {
        return value == null ? "" : value.replace('\u25A1', '?').trim();
    }

    private static final class EvaluationSummary {
        private final CwFixtureScenario scenario;
        private final CwFixtureEvaluationResult result;
        private final String actualRawText;
        private final String actualNormalizedText;
        private final String expectedRawText;
        private final double rawCharRecall;
        private final double firstHalfRawCharRecall;
        private final double secondHalfRawCharRecall;
        private final double normalizedCharRecall;
        private final int turnStartCount;
        private final int turnEndCount;
        private final int estimatedWpm;

        private EvaluationSummary(
                CwFixtureScenario scenario,
                CwFixtureEvaluationResult result,
                String actualRawText,
                String actualNormalizedText,
                String expectedRawText,
                double rawCharRecall,
                double firstHalfRawCharRecall,
                double secondHalfRawCharRecall,
                double normalizedCharRecall,
                int turnStartCount,
                int turnEndCount,
                int estimatedWpm
        ) {
            this.scenario = scenario;
            this.result = result;
            this.actualRawText = actualRawText;
            this.actualNormalizedText = actualNormalizedText;
            this.expectedRawText = expectedRawText;
            this.rawCharRecall = rawCharRecall;
            this.firstHalfRawCharRecall = firstHalfRawCharRecall;
            this.secondHalfRawCharRecall = secondHalfRawCharRecall;
            this.normalizedCharRecall = normalizedCharRecall;
            this.turnStartCount = turnStartCount;
            this.turnEndCount = turnEndCount;
            this.estimatedWpm = estimatedWpm;
        }
    }
}
