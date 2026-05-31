package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.eval.CwFixtureLibrary;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class CwToneTruthEvaluatorTest {
    @Test
    public void syntheticTruthFramesExposeToneSweepExtremes() {
        CwFixtureScenario scenario = requireScenario("user_tone_sweep_vvv_18wpm");
        SyntheticFixtureRxAudioSource audioSource = new SyntheticFixtureRxAudioSource();
        SyntheticFixtureRxAudioSource.RenderedFixtureFrames rendered =
                audioSource.renderFramesWithTruthForTesting(scenario);
        List<SyntheticFixtureRxAudioSource.FrameToneTruth> truths = rendered.toneTruths();

        assertEquals(rendered.frames().size(), truths.size());

        double minActiveTruthHz = Double.POSITIVE_INFINITY;
        double maxActiveTruthHz = Double.NEGATIVE_INFINITY;
        int activeTruthFrames = 0;
        for (SyntheticFixtureRxAudioSource.FrameToneTruth truth : truths) {
            if (!truth.toneActive()) {
                continue;
            }
            activeTruthFrames += 1;
            minActiveTruthHz = Math.min(minActiveTruthHz, truth.expectedToneFrequencyHz());
            maxActiveTruthHz = Math.max(maxActiveTruthHz, truth.expectedToneFrequencyHz());
        }

        assertTrue(activeTruthFrames > 20);
        assertTrue("min truth tone should reach the 400Hz segment: " + minActiveTruthHz, minActiveTruthHz <= 430.0d);
        assertTrue("max truth tone should reach the 800Hz segment: " + maxActiveTruthHz, maxActiveTruthHz >= 770.0d);
    }

    @Test
    public void toneTruthEvaluatorProducesUsableComparisonForStableFixture() {
        CwToneTruthEvaluationSupport.ToneTruthComparison comparison =
                CwToneTruthEvaluationSupport.evaluateFixture("user_light_qsb_cq_18wpm_700hz", 450);
        String summary = comparison.renderSummary();

        assertTrue(summary, comparison.activeTruthFrameCount() > 20);
        assertTrue(summary, comparison.trackedMetrics().availableFrameCount() == comparison.activeTruthFrameCount());
        assertTrue(summary, comparison.coObservedFrameCount() > 10);
        assertTrue(summary, comparison.hypothesisMetrics().availableFrameCount() > 10);
        assertTrue(summary, comparison.winner() != CwToneTruthEvaluationSupport.Winner.INSUFFICIENT);
        assertTrue(summary, comparison.trackedMetrics().meanAbsoluteErrorHz() < 80.0d);
        assertTrue(summary, comparison.hypothesisMetrics().meanAbsoluteErrorHz() < 80.0d);
        assertEquals(
                summary,
                CwToneTruthEvaluationSupport.GroundTruthToneMode.STABLE_SINGLE_PEAK,
                comparison.groundTruthToneMode()
        );
        assertEquals(
                summary,
                CwToneTruthEvaluationSupport.PrototypeRecommendation.STAY_TRACKED,
                comparison.prototypeRecommendation()
        );
        assertTrue(summary, comparison.disagreementObservedFrameCount() > 0);
        assertTrue(
                summary,
                comparison.hypothesisMetrics().meanAbsoluteErrorHz()
                        <= comparison.trackedMetrics().meanAbsoluteErrorHz() + 1.0d
        );
        assertTrue(
                summary,
                comparison.disagreementHypothesisCloserFrameCount()
                        > comparison.disagreementTrackedCloserFrameCount()
        );
    }

    @Test
    public void toneTruthEvaluatorSurfacesMeaningfulTrackedVsHypothesisDisagreement() {
        CwToneTruthEvaluationSupport.ToneTruthComparison comparison =
                CwToneTruthEvaluationSupport.evaluateFixture("user_tone_sweep_vvv_18wpm", 450);
        String summary = comparison.renderSummary();

        assertTrue(summary, comparison.activeTruthFrameCount() > 20);
        assertTrue(summary, comparison.coObservedFrameCount() > 10);
        assertTrue(summary, comparison.disagreementFrameCount() > 0);
        assertTrue(summary, comparison.trackedCloserFrameCount() + comparison.hypothesisCloserFrameCount() > 0);
        assertTrue(summary, comparison.winner() != CwToneTruthEvaluationSupport.Winner.INSUFFICIENT);
        assertEquals(summary, CwToneTruthEvaluationSupport.Winner.TRACKED, comparison.winner());
        assertEquals(
                summary,
                CwToneTruthEvaluationSupport.GroundTruthToneMode.VARIABLE_OR_SWEEP,
                comparison.groundTruthToneMode()
        );
        assertEquals(
                summary,
                CwToneTruthEvaluationSupport.PrototypeRecommendation.STAY_TRACKED,
                comparison.prototypeRecommendation()
        );
        assertTrue(summary, comparison.disagreementObservedFrameCount() > 10);
        assertTrue(
                summary,
                comparison.disagreementTrackedCloserFrameCount()
                        > comparison.disagreementHypothesisCloserFrameCount()
        );
    }

    @Test
    public void toneTruthEvaluatorKeepsQsb600BenchUsefulWithoutForcingGuardRecommendation() {
        CwToneTruthEvaluationSupport.ToneTruthComparison comparison =
                CwToneTruthEvaluationSupport.evaluateFixture("user_qsb_cq_18wpm_600hz", 450);
        String summary = comparison.renderSummary();

        assertTrue(summary, comparison.winner() != CwToneTruthEvaluationSupport.Winner.INSUFFICIENT);
        assertEquals(
                summary,
                CwToneTruthEvaluationSupport.GroundTruthToneMode.STABLE_SINGLE_PEAK,
                comparison.groundTruthToneMode()
        );
        assertEquals(
                summary,
                CwToneTruthEvaluationSupport.PrototypeRecommendation.STAY_TRACKED,
                comparison.prototypeRecommendation()
        );
        assertTrue(
                summary,
                comparison.hypothesisMetrics().meanAbsoluteErrorHz()
                        <= comparison.trackedMetrics().meanAbsoluteErrorHz() + 1.0d
        );
        assertTrue(summary, comparison.disagreementObservedFrameCount() > 0);
        assertTrue(summary, comparison.disagreementTrackedCloserFrameCount() == 0);
        assertTrue(
                summary,
                comparison.hypothesisMetrics().largeErrorFrameCount()
                        <= comparison.trackedMetrics().largeErrorFrameCount()
        );
    }

    @Test
    public void toneTruthEvaluatorKeepsFixedToneSpeedShiftBenchUsableWithoutGuardBias() {
        CwToneTruthEvaluationSupport.ToneTruthComparison comparison =
                CwToneTruthEvaluationSupport.evaluateFixture("user_speed_shift_jv3vv_700hz", 450);
        String summary = comparison.renderSummary();

        assertEquals(
                summary,
                CwToneTruthEvaluationSupport.GroundTruthToneMode.STABLE_SINGLE_PEAK,
                comparison.groundTruthToneMode()
        );
        assertTrue(summary, comparison.winner() != CwToneTruthEvaluationSupport.Winner.INSUFFICIENT);
        assertEquals(
                summary,
                CwToneTruthEvaluationSupport.PrototypeRecommendation.STAY_TRACKED,
                comparison.prototypeRecommendation()
        );
        assertTrue(
                summary,
                comparison.hypothesisMetrics().meanAbsoluteErrorHz()
                        <= comparison.trackedMetrics().meanAbsoluteErrorHz() + 1.0d
        );
        assertTrue(
                summary,
                comparison.hypothesisMetrics().largeErrorFrameCount()
                        <= comparison.trackedMetrics().largeErrorFrameCount()
        );
        assertTrue(summary, comparison.disagreementHypothesisCloserFrameCount() > 0);
    }

    private static CwFixtureScenario requireScenario(String scenarioId) {
        for (CwFixtureScenario scenario : CwFixtureLibrary.scenarios()) {
            if (scenario.id().equals(scenarioId)) {
                return scenario;
            }
        }
        throw new IllegalArgumentException("Unknown fixture scenario: " + scenarioId);
    }
}
