package org.bi9clt.cwcn.core.audio;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class CwFrontEndDisagreementEvaluationTest {
    @Test
    public void stableQsb600DisagreementWindowsClearlyFavorHypothesis() {
        CwToneTruthEvaluationSupport.ToneTruthComparison comparison =
                CwToneTruthEvaluationSupport.evaluateFixture("user_qsb_cq_18wpm_600hz", 450);
        String summary = comparison.renderSummary();

        assertEquals(summary, CwToneTruthEvaluationSupport.Winner.HYPOTHESIS, comparison.winner());
        assertTrue(summary, comparison.disagreementObservedFrameCount() >= 20);
        assertTrue(summary, comparison.hypothesisRescueFrameCount() >= 3);
        assertTrue(
                summary,
                comparison.hypothesisRescueFrameCount() > comparison.trackedRescueFrameCount()
        );
        assertTrue(
                summary,
                comparison.representativeCompetitionHypothesisWinFrames()
                        > comparison.representativeCompetitionTrackedWinFrames()
        );
        assertTrue(summary, comparison.representativeCompetitionHypothesisMaxWinStreak() >= 10);
        assertTrue(
                summary,
                comparison.hypothesisMetrics().largeErrorFrameCount()
                        <= comparison.trackedMetrics().largeErrorFrameCount()
        );
    }

    @Test
    public void stableSpeedShift700StillLooksLikeHypothesisFriendlyDisagreementClass() {
        CwToneTruthEvaluationSupport.ToneTruthComparison comparison =
                CwToneTruthEvaluationSupport.evaluateFixture("user_speed_shift_jv3vv_700hz", 450);
        String summary = comparison.renderSummary();

        assertEquals(
                summary,
                CwToneTruthEvaluationSupport.GroundTruthToneMode.STABLE_SINGLE_PEAK,
                comparison.groundTruthToneMode()
        );
        assertEquals(summary, CwToneTruthEvaluationSupport.Winner.HYPOTHESIS, comparison.winner());
        assertTrue(
                summary,
                comparison.hypothesisMetrics().meanAbsoluteErrorHz()
                        <= comparison.trackedMetrics().meanAbsoluteErrorHz()
        );
        assertTrue(
                summary,
                comparison.hypothesisMetrics().largeErrorFrameCount()
                        <= comparison.trackedMetrics().largeErrorFrameCount()
        );
        assertTrue(
                summary,
                comparison.representativeCompetitionHypothesisWinFrames()
                        >= comparison.representativeCompetitionTrackedWinFrames()
        );
    }

    @Test
    public void toneSweepDisagreementWindowsStillFavorTracked() {
        CwToneTruthEvaluationSupport.ToneTruthComparison comparison =
                CwToneTruthEvaluationSupport.evaluateFixture("user_tone_sweep_vvv_18wpm", 450);
        String summary = comparison.renderSummary();

        assertEquals(summary, CwToneTruthEvaluationSupport.Winner.TRACKED, comparison.winner());
        assertTrue(summary, comparison.disagreementObservedFrameCount() > 10);
        assertTrue(
                summary,
                comparison.trackedRescueFrameCount() >= comparison.hypothesisRescueFrameCount()
        );
        assertTrue(
                summary,
                comparison.trackedMetrics().meanAbsoluteErrorHz()
                        <= comparison.hypothesisMetrics().meanAbsoluteErrorHz() + 2.0d
        );
        assertTrue(summary, comparison.activeCenterCompetitionObservationCount() > 0);
        assertTrue(summary, comparison.representativeCompetitionObservationCount() > 0);
    }

    @Test
    public void fast30wpmNoiseDoesNotLookLikeAnObviousHypothesisGuardCase() {
        CwToneTruthEvaluationSupport.ToneTruthComparison comparison =
                CwToneTruthEvaluationSupport.evaluateFixture("user_noise_cq_30wpm_700hz", 450);
        String summary = comparison.renderSummary();

        assertTrue(summary, comparison.activeTruthFrameCount() > 20);
        assertTrue(
                summary,
                comparison.prototypeRecommendation() == CwToneTruthEvaluationSupport.PrototypeRecommendation.STAY_TRACKED
                        || comparison.winner() != CwToneTruthEvaluationSupport.Winner.HYPOTHESIS
        );
        assertTrue(
                summary,
                comparison.trackedMetrics().meanAbsoluteErrorHz()
                        <= comparison.hypothesisMetrics().meanAbsoluteErrorHz() + 12.0d
        );
        assertTrue(summary, comparison.activeCenterCompetitionObservationCount() > 0);
        assertTrue(summary, comparison.representativeCompetitionObservationCount() > 0);
    }
}
