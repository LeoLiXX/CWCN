package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.eval.CwFixtureLibrary;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario;
import org.junit.Test;

import java.util.Locale;

public final class CwCompetitionAllFixtureProbeTest {
    @Test
    public void printCompetitionSweepAcrossAllFixtures() {
        for (CwFixtureScenario scenario : CwFixtureLibrary.scenarios()) {
            CwToneTruthEvaluationSupport.ToneTruthComparison comparison =
                    CwToneTruthEvaluationSupport.evaluateFixture(scenario, 450);
            if (comparison.activeTruthFrameCount() <= 0) {
                System.out.println("SKIP " + scenario.id() + " no active truth frames");
                continue;
            }
            System.out.println(renderCompetitionSummary(comparison));
        }
    }

    private static String renderCompetitionSummary(CwToneTruthEvaluationSupport.ToneTruthComparison comparison) {
        return String.format(
                Locale.US,
                "%s | winner=%s proto=%s repComp(obs=%d trk=%d hyp=%d maxHyp=%d) actComp(obs=%d trk=%d hyp=%d maxHyp=%d) centers(acq=%d hyp=%d hypObs=%d)",
                comparison.scenarioId(),
                comparison.winner(),
                comparison.prototypeRecommendation(),
                comparison.representativeCompetitionObservationCount(),
                comparison.representativeCompetitionTrackedWinFrames(),
                comparison.representativeCompetitionHypothesisWinFrames(),
                comparison.representativeCompetitionHypothesisMaxWinStreak(),
                comparison.activeCenterCompetitionObservationCount(),
                comparison.activeCenterCompetitionTrackedWinFrames(),
                comparison.activeCenterCompetitionHypothesisWinFrames(),
                comparison.activeCenterCompetitionHypothesisMaxWinStreak(),
                comparison.activeAcquisitionCenterFrequencyHz(),
                comparison.activeHypothesisCenterFrequencyHz(),
                comparison.activeHypothesisObservationCount()
        );
    }
}
