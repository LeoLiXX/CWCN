package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.eval.CwFixtureLibrary;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario;
import org.junit.Test;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public final class CwToneTruthAllFixtureProbeTest {
    @Test
    public void printToneTruthSweepAcrossAllFixtures() {
        Map<CwToneTruthEvaluationSupport.GroundTruthToneMode, Integer> truthModeCounts =
                new EnumMap<>(CwToneTruthEvaluationSupport.GroundTruthToneMode.class);
        Map<CwToneTruthEvaluationSupport.Winner, Integer> winnerCounts =
                new EnumMap<>(CwToneTruthEvaluationSupport.Winner.class);
        Map<CwToneTruthEvaluationSupport.PrototypeRecommendation, Integer> recommendationCounts =
                new EnumMap<>(CwToneTruthEvaluationSupport.PrototypeRecommendation.class);

        int evaluated = 0;
        int skipped = 0;
        for (CwFixtureScenario scenario : CwFixtureLibrary.scenarios()) {
            CwToneTruthEvaluationSupport.ToneTruthComparison comparison =
                    CwToneTruthEvaluationSupport.evaluateFixture(scenario, 450);
            if (comparison.activeTruthFrameCount() <= 0) {
                skipped += 1;
                System.out.println("SKIP " + scenario.id() + " no active truth frames");
                continue;
            }
            evaluated += 1;
            increment(truthModeCounts, comparison.groundTruthToneMode());
            increment(winnerCounts, comparison.winner());
            increment(recommendationCounts, comparison.prototypeRecommendation());
            System.out.println(renderCompactSummary(scenario, comparison));
        }

        System.out.println("==== tone truth sweep summary ====");
        System.out.println("evaluated=" + evaluated + ", skipped=" + skipped);
        System.out.println("truthModeCounts=" + truthModeCounts);
        System.out.println("winnerCounts=" + winnerCounts);
        System.out.println("recommendationCounts=" + recommendationCounts);
    }

    private static String renderCompactSummary(
            CwFixtureScenario scenario,
            CwToneTruthEvaluationSupport.ToneTruthComparison comparison
    ) {
        return String.format(
                Locale.US,
                "%s | pref=450 truthMode=%s span=%.1f winner=%s proto=%s disagreement=%d trk/hyp=%d/%d mae=%.1f/%.1f hit=%.2f/%.2f repComp=%d/%d/%d maxHyp=%d actComp=%d/%d/%d maxHyp=%d",
                scenario.id(),
                comparison.groundTruthToneMode(),
                comparison.truthToneSpanHz(),
                comparison.winner(),
                comparison.prototypeRecommendation(),
                comparison.disagreementObservedFrameCount(),
                comparison.disagreementTrackedCloserFrameCount(),
                comparison.disagreementHypothesisCloserFrameCount(),
                comparison.trackedMetrics().meanAbsoluteErrorHz(),
                comparison.hypothesisMetrics().meanAbsoluteErrorHz(),
                comparison.trackedMetrics().hitRate(),
                comparison.hypothesisMetrics().hitRate(),
                comparison.representativeCompetitionObservationCount(),
                comparison.representativeCompetitionTrackedWinFrames(),
                comparison.representativeCompetitionHypothesisWinFrames(),
                comparison.representativeCompetitionHypothesisMaxWinStreak(),
                comparison.activeCenterCompetitionObservationCount(),
                comparison.activeCenterCompetitionTrackedWinFrames(),
                comparison.activeCenterCompetitionHypothesisWinFrames(),
                comparison.activeCenterCompetitionHypothesisMaxWinStreak()
        );
    }

    private static <T> void increment(Map<T, Integer> counts, T key) {
        Integer current = counts.get(key);
        counts.put(key, current == null ? 1 : current + 1);
    }
}
