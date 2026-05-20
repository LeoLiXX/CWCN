package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class LiveLikeBootstrapDecisionCompatibilityAdapterTest {
    @Test
    public void boundaryAuthorityOverlayIsSeparatedFromVerifiedSharedDecision() {
        LiveLikeBootstrapDecisionCompatibilityAdapter.DecisionOutcome outcome =
                LiveLikeBootstrapDecisionCompatibilityAdapter.diagnoseTimingBoundaryDecision(
                        gapEvent(200L, 150L, 50L, CwTimingEvent.Classification.LETTER_GAP),
                        strongSignal(),
                        timingSnapshotFromDotMs(50L),
                        null,
                        null,
                        (signalSnapshot, timestampMs) -> false,
                        null
                );

        assertEquals("front-end-authority", outcome.compatibleDecision());
        assertEquals("pass", outcome.verifiedDecision());
    }

    @Test
    public void cadenceAuthorityOverlayIsSeparatedFromVerifiedSharedDecision() {
        LiveLikeBootstrapDecisionCompatibilityAdapter.DecisionOutcome outcome =
                LiveLikeBootstrapDecisionCompatibilityAdapter.diagnoseCadenceDecision(
                        gapEvent(
                                200L,
                                55L,
                                55L,
                                CwTimingEvent.Classification.INTRA_SYMBOL_GAP
                        ),
                        cadenceSignal(),
                        timingSnapshotFromDotMs(55L),
                        null,
                        null,
                        (signalSnapshot, timestampMs) -> false,
                        null
                );

        assertEquals("front-end-authority", outcome.compatibleDecision());
        assertEquals("pass", outcome.verifiedDecision());
    }

    private static CwTimingSnapshot timingSnapshotFromDotMs(long dotMs) {
        return new CwTimingSnapshot(
                dotMs,
                dotMs * 3L,
                dotMs,
                (int) Math.round(1200.0d / dotMs),
                1200.0d / dotMs,
                0,
                0,
                null
        );
    }

    private static CwTimingEvent gapEvent(
            long timestampMs,
            long durationMs,
            long dotEstimateMs,
            CwTimingEvent.Classification classification
    ) {
        return new CwTimingEvent(
                CwTimingEvent.Kind.GAP,
                classification,
                timestampMs,
                durationMs,
                dotEstimateMs,
                dotEstimateMs
        );
    }

    private static CwSignalSnapshot strongSignal() {
        return signalSnapshot(true, "LLLLLLLLLLLLLLLLLLLLLLLL", 6, 0.58d, 0.66d);
    }

    private static CwSignalSnapshot cadenceSignal() {
        return signalSnapshot(true, "LLLLLLLLLLLLLLLLLLLLLLLL", 60, 0.46d, 0.36d);
    }

    private static CwSignalSnapshot signalSnapshot(
            boolean targetToneLocked,
            String recentStates,
            int lockedOffsetHz,
            double toneDominanceRatio,
            double narrowbandIsolationRatio
    ) {
        char[] stateHistory = recentStates.toCharArray();
        int[] offsetHistory = new int[stateHistory.length];
        for (int index = 0; index < stateHistory.length; index++) {
            char state = stateHistory[index];
            offsetHistory[index] = (state == 'L' || state == 'l') ? lockedOffsetHz : 80;
        }
        int recentLockedCount = countStates(stateHistory, 'L', 'l');
        int recentUnlockedCount = countStates(stateHistory, 'u');
        return new CwSignalSnapshot(
                stateHistory.length,
                stateHistory,
                offsetHistory,
                false,
                targetToneLocked,
                700,
                700,
                700,
                recentLockedCount,
                120,
                85,
                80,
                100,
                140.0d,
                90.0d,
                40.0d,
                toneDominanceRatio,
                narrowbandIsolationRatio,
                100.0d,
                narrowbandIsolationRatio,
                stateHistory.length,
                recentLockedCount,
                Math.max(1, recentLockedCount + recentUnlockedCount),
                recentUnlockedCount,
                recentLockedCount,
                recentLockedCount,
                recentUnlockedCount,
                recentUnlockedCount,
                700,
                0,
                700,
                700,
                700,
                700,
                90.0d,
                90.0d,
                90.0d,
                90.0d,
                90.0d,
                90.0d,
                90.0d,
                90.0d,
                true,
                true,
                true,
                true,
                "TRACK",
                "TRACK",
                1,
                1,
                0,
                0L,
                0L,
                0L,
                0L,
                null
        );
    }

    private static int countStates(char[] states, char primary, char secondary) {
        int count = 0;
        for (char state : states) {
            if (state == primary || state == secondary) {
                count += 1;
            }
        }
        return count;
    }

    private static int countStates(char[] states, char primary) {
        int count = 0;
        for (char state : states) {
            if (state == primary) {
                count += 1;
            }
        }
        return count;
    }
}
