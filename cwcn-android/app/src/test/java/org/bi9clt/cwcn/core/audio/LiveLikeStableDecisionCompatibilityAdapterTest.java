package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.rx.RxStableDecodeDecider;
import org.bi9clt.cwcn.core.rx.TimingAnchorController;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class LiveLikeStableDecisionCompatibilityAdapterTest {
    @Test
    public void bootstrapAuthorityOverlayIsSeparatedFromVerifiedSharedDecision() {
        LiveLikeStableDecisionCompatibilityAdapter.DecisionOutcome outcome =
                LiveLikeStableDecisionCompatibilityAdapter.diagnoseDecision(
                        characterEvent(200L, "A", ".-", false),
                        strongSignal(),
                        timingSnapshotFromDotMs(50L),
                        null,
                        null,
                        denyAuthorityGate(),
                        null
                );

        assertEquals("front-end-authority", outcome.compatibleDecision());
        assertEquals("pass", outcome.verifiedDecision());
    }

    @Test
    public void stableAuthorityOverlayIsSeparatedFromVerifiedSharedDecision() {
        CwSignalSnapshot signalSnapshot = strongSignal();
        CwTimingSnapshot timingSnapshot = timingSnapshotFromDotMs(50L);
        TimingAnchorController timingAnchorController = trustedTimingController(
                signalSnapshot,
                timingSnapshot
        );

        assertTrue(RxStableDecodeDecider.hasTrustedTiming(timingAnchorController));

        LiveLikeStableDecisionCompatibilityAdapter.DecisionOutcome outcome =
                LiveLikeStableDecisionCompatibilityAdapter.diagnoseDecision(
                        characterEvent(560L, "A", ".-", false),
                        signalSnapshot,
                        timingSnapshot,
                        null,
                        null,
                        denyAuthorityGate(),
                        timingAnchorController
                );

        assertEquals("front-end-authority", outcome.compatibleDecision());
        assertEquals("pass", outcome.verifiedDecision());
    }

    private static LiveLikeStableDecisionCompatibilityAdapter.StableAuthorityGate denyAuthorityGate() {
        return new LiveLikeStableDecisionCompatibilityAdapter.StableAuthorityGate() {
            @Override
            public boolean shouldAllowStableAnchorUpdate(
                    CwSignalSnapshot signalSnapshot,
                    long timestampMs
            ) {
                return false;
            }

            @Override
            public boolean shouldAllowBootstrapStableAnchorUpdate(
                    CwSignalSnapshot signalSnapshot,
                    long timestampMs
            ) {
                return false;
            }
        };
    }

    private static TimingAnchorController trustedTimingController(
            CwSignalSnapshot signalSnapshot,
            CwTimingSnapshot timingSnapshot
    ) {
        TimingAnchorController controller = new TimingAnchorController();
        controller.noteStableDecode(signalSnapshot, timingSnapshot, 200L);
        controller.noteStableDecode(signalSnapshot, timingSnapshot, 320L);
        controller.noteStableDecode(signalSnapshot, timingSnapshot, 440L);
        return controller;
    }

    private static CwDecodeEvent characterEvent(
            long timestampMs,
            String emittedValue,
            String sourceSequence,
            boolean unknownCharacter
    ) {
        return new CwDecodeEvent(
                CwDecodeEvent.Type.CHARACTER_DECODED,
                timestampMs,
                sourceSequence,
                emittedValue,
                emittedValue,
                sourceSequence,
                unknownCharacter
        );
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

    private static CwSignalSnapshot strongSignal() {
        return signalSnapshot(true, "LLLLLLLLLLLLLLLLLLLLLLLL", 6, 0.58d, 0.66d);
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
