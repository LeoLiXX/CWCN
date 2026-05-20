package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.eval.CwFixtureEvaluationResult;
import org.bi9clt.cwcn.core.eval.CwFixtureEvaluator;
import org.bi9clt.cwcn.core.eval.CwFixtureLibrary;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public final class CwPreferredOffsetMatrixRegressionTest {
    // Stress/audit matrix only. Large preferred-tone offsets are useful to expose
    // robustness cliffs, but they are not the main RX release gate for real-world use.
    private static final int[] PREFERRED_MATRIX = new int[]{450, 500, 650, 800};

    @Test
    public void hybridPathPreferredOffsetMatrixRemainsObservableAcrossCoreFixtures() {
        List<MatrixExpectation> expectations = new ArrayList<>();

        addObservabilityMatrix(expectations, "user_qsb_cq_18wpm_600hz", PREFERRED_MATRIX, 24, 0.65d);
        addObservabilityMatrix(expectations, "user_qsb_cq_18wpm_700hz", PREFERRED_MATRIX, 24, 0.66d);
        addObservabilityMatrix(expectations, "user_qsb_cq_18wpm_800hz", PREFERRED_MATRIX, 24, 0.70d);
        addObservabilityMatrix(expectations, "user_tone_sweep_vvv_18wpm", PREFERRED_MATRIX, 20, 0.66d);
        addObservabilityMatrix(expectations, "user_speed_sweep_vvv_700hz", PREFERRED_MATRIX, 18, 0.45d);

        // Hard-lock the combinations we already validated as healthy on the main hybrid path.
        expectations.add(MatrixExpectation.robust("user_qsb_cq_18wpm_600hz", 500, 590, 20, 0.88d, 30));
        expectations.add(MatrixExpectation.robust("user_qsb_cq_18wpm_600hz", 650, 590, 20, 0.88d, 30));
        expectations.add(MatrixExpectation.robust("user_qsb_cq_18wpm_700hz", 450, 690, 30, 0.77d, 27));
        expectations.add(MatrixExpectation.robust("user_qsb_cq_18wpm_700hz", 500, 690, 30, 0.77d, 27));
        expectations.add(MatrixExpectation.robust("user_qsb_cq_18wpm_700hz", 650, 680, 30, 0.66d, 29));
        expectations.add(MatrixExpectation.robust("user_qsb_cq_18wpm_700hz", 800, 710, 30, 0.77d, 29));
        expectations.add(MatrixExpectation.robust("user_qsb_cq_18wpm_800hz", 450, 800, 30, 0.77d, 29));
        expectations.add(MatrixExpectation.robust("user_qsb_cq_18wpm_800hz", 800, 800, 30, 0.77d, 29));
        expectations.add(MatrixExpectation.robust("user_tone_sweep_vvv_18wpm", 450, 610, 30, 1.0d, 22));
        expectations.add(MatrixExpectation.robust("user_tone_sweep_vvv_18wpm", 500, 600, 30, 1.0d, 22));
        expectations.add(MatrixExpectation.robust("user_tone_sweep_vvv_18wpm", 650, 610, 30, 1.0d, 22));
        expectations.add(MatrixExpectation.robust("user_tone_sweep_vvv_18wpm", 800, 800, 60, 0.66d, 20));

        StringBuilder summary = new StringBuilder();
        for (MatrixExpectation expectation : expectations) {
            OfflineEvalBundle bundle = evaluateOfflineBundle(expectation.scenarioId, expectation.preferredToneHz);
            String decodedText = bundle.decodedText == null
                    ? ""
                    : bundle.decodedText.replace('\u25A1', '?');
            summary.append(expectation.scenarioId)
                    .append(" pref=").append(expectation.preferredToneHz)
                    .append(" tracked=").append(bundle.signalSnapshot.targetToneFrequencyHz())
                    .append(" EFF=").append(bundle.signalSnapshot.effectiveTrackedToneFrequencyHz())
                    .append(" REP=").append(bundle.signalSnapshot.representativeLockedToneFrequencyHz())
                    .append(" AQ=").append(bundle.signalSnapshot.acquisitionWinnerFrequencyHz())
                    .append(" AD=").append(bundle.signalSnapshot.finalAdoptedFrequencyHz())
                    .append(" AQsrc=").append(bundle.signalSnapshot.acquisitionWinnerSource())
                    .append(" ADsrc=").append(bundle.signalSnapshot.finalAdoptedSource())
                    .append(" recall=").append(bundle.result.textTokenRecall())
                    .append(" chars=").append(bundle.decoderCharacters)
                    .append(" text=").append(decodedText)
                    .append('\n');

            assertTrue(summary.toString(), bundle.result.completed());
            assertNotEquals(summary.toString(), "RUN", bundle.result.likelyBottleneckCode());
            assertTrue(summary.toString(), bundle.decoderCharacters >= expectation.minCharacters);
            assertTrue(summary.toString(), bundle.result.textTokenRecall() >= expectation.minRecall);

            if (expectation.robust) {
                assertRobustToneAnchor(summary.toString(), expectation, bundle.signalSnapshot);
            }
        }
    }

    private void addObservabilityMatrix(
            List<MatrixExpectation> expectations,
            String scenarioId,
            int[] preferredMatrix,
            int minCharacters,
            double minRecall
    ) {
        for (int preferredToneHz : preferredMatrix) {
            expectations.add(MatrixExpectation.observable(scenarioId, preferredToneHz, minRecall, minCharacters));
        }
    }

    private void assertWithinTolerance(String summary, int expected, int actual, int toleranceHz) {
        assertTrue(
                summary + "\nexpected=" + expected + " actual=" + actual + " tol=" + toleranceHz,
                Math.abs(actual - expected) <= toleranceHz
        );
    }

    private void assertRobustToneAnchor(
            String summary,
            MatrixExpectation expectation,
            CwSignalSnapshot signalSnapshot
    ) {
        boolean representativeAnchorHealthy = signalSnapshot.representativeLockedToneFrameCount() > 0
                && Math.abs(
                signalSnapshot.representativeLockedToneFrequencyHz() - expectation.expectedTrackedToneHz
        ) <= expectation.toleranceHz;
        boolean effectiveAnchorHealthy = signalSnapshot.effectiveTrackedToneFrequencyHz() > 0
                && Math.abs(
                signalSnapshot.effectiveTrackedToneFrequencyHz() - expectation.expectedTrackedToneHz
        ) <= expectation.toleranceHz;
        boolean targetAnchorHealthy = signalSnapshot.targetToneFrequencyHz() > 0
                && Math.abs(
                signalSnapshot.targetToneFrequencyHz() - expectation.expectedTrackedToneHz
        ) <= expectation.toleranceHz + 20;
        boolean adoptedAnchorHealthy = signalSnapshot.finalAdoptedFrequencyHz() > 0
                && Math.abs(
                signalSnapshot.finalAdoptedFrequencyHz() - expectation.expectedTrackedToneHz
        ) <= expectation.toleranceHz + 20;
        boolean robustAnchorHealthy = representativeAnchorHealthy
                || effectiveAnchorHealthy
                || targetAnchorHealthy
                || adoptedAnchorHealthy;

        if ("SEARCH_FALLBACK".equals(signalSnapshot.finalAdoptedSource())) {
            assertTrue(
                    summary + "\nsearch fallback should only be tolerated when effective lock stayed healthy",
                    representativeAnchorHealthy || effectiveAnchorHealthy
            );
            return;
        }

        assertNotEquals(summary, "SEARCH_FALLBACK", signalSnapshot.finalAdoptedSource());
        assertTrue(
                summary + "\nrobust anchor should be reflected by representative/effective or target/adopted lock",
                robustAnchorHealthy
        );
        if (targetAnchorHealthy) {
            assertWithinTolerance(
                    summary,
                    expectation.expectedTrackedToneHz,
                    signalSnapshot.targetToneFrequencyHz(),
                    expectation.toleranceHz + 20
            );
        }
    }

    private OfflineEvalBundle evaluateOfflineBundle(String scenarioId, int preferredToneHz) {
        CwFixtureScenario scenario = findScenario(scenarioId);
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        List<AudioFrame> frames = source.renderFramesForTesting(scenario);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailedProbeResult =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        scenario.id(),
                        frames,
                        preferredToneHz,
                        scenario.wpm(),
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        return new OfflineEvalBundle(
                CwFixtureEvaluator.evaluate(
                        scenario,
                        detailedProbeResult.probeResult().interpreterSnapshot(),
                        detailedProbeResult.probeResult().qsoDraftSnapshot(),
                        detailedProbeResult.probeResult().signalSnapshot(),
                        true
                ),
                detailedProbeResult.probeResult().signalSnapshot(),
                detailedProbeResult.probeResult().timingSnapshot(),
                detailedProbeResult.probeResult().decoderSnapshot().decodedText(),
                detailedProbeResult.probeResult().decoderSnapshot().totalCharacters()
        );
    }

    private CwFixtureScenario findScenario(String scenarioId) {
        for (CwFixtureScenario scenario : CwFixtureLibrary.scenarios()) {
            if (scenario.id().equals(scenarioId)) {
                return scenario;
            }
        }
        throw new IllegalArgumentException("Unknown scenario: " + scenarioId);
    }

    private static final class MatrixExpectation {
        private final String scenarioId;
        private final int preferredToneHz;
        private final boolean robust;
        private final int expectedTrackedToneHz;
        private final int toleranceHz;
        private final double minRecall;
        private final int minCharacters;

        private MatrixExpectation(
                String scenarioId,
                int preferredToneHz,
                boolean robust,
                int expectedTrackedToneHz,
                int toleranceHz,
                double minRecall,
                int minCharacters
        ) {
            this.scenarioId = scenarioId;
            this.preferredToneHz = preferredToneHz;
            this.robust = robust;
            this.expectedTrackedToneHz = expectedTrackedToneHz;
            this.toleranceHz = toleranceHz;
            this.minRecall = minRecall;
            this.minCharacters = minCharacters;
        }

        private static MatrixExpectation observable(
                String scenarioId,
                int preferredToneHz,
                double minRecall,
                int minCharacters
        ) {
            return new MatrixExpectation(scenarioId, preferredToneHz, false, 0, 0, minRecall, minCharacters);
        }

        private static MatrixExpectation robust(
                String scenarioId,
                int preferredToneHz,
                int expectedTrackedToneHz,
                int toleranceHz,
                double minRecall,
                int minCharacters
        ) {
            return new MatrixExpectation(
                    scenarioId,
                    preferredToneHz,
                    true,
                    expectedTrackedToneHz,
                    toleranceHz,
                    minRecall,
                    minCharacters
            );
        }
    }

    private static final class OfflineEvalBundle {
        private final CwFixtureEvaluationResult result;
        private final CwSignalSnapshot signalSnapshot;
        private final CwTimingSnapshot timingSnapshot;
        private final String decodedText;
        private final int decoderCharacters;

        private OfflineEvalBundle(
                CwFixtureEvaluationResult result,
                CwSignalSnapshot signalSnapshot,
                CwTimingSnapshot timingSnapshot,
                String decodedText,
                int decoderCharacters
        ) {
            this.result = result;
            this.signalSnapshot = signalSnapshot;
            this.timingSnapshot = timingSnapshot;
            this.decodedText = decodedText;
            this.decoderCharacters = decoderCharacters;
        }
    }
}
