package org.bi9clt.cwcn.core.rx;

import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class RxToneModeBootstrapDeciderTest {
    @Test
    public void preTrustFallbackDoesNotTriggerAfterFixedProgressWasAlreadyObservedInTurn() {
        RxTurnController controller = new RxTurnController();
        assertTrue(controller.observe(true, false, 100L, 0).startedNewTurn());

        CwSignalProcessor.RxToneMode progressMode = RxToneModeBootstrapDecider.resolveHybridBootstrapMode(
                false,
                controller,
                snapshot(true, 4, new char[]{'L', 'L', 'L', 'L', '.', '.', '.', '.', '.', '.'}),
                180L
        );
        CwSignalProcessor.RxToneMode degradedMode = RxToneModeBootstrapDecider.resolveHybridBootstrapMode(
                false,
                controller,
                snapshot(false, 0, new char[]{'L', 'L', '.', '.', '.', '.', '.', '.', '.', '.'}),
                260L
        );

        assertEquals(CwSignalProcessor.RxToneMode.FIXED_TONE, progressMode);
        assertEquals(CwSignalProcessor.RxToneMode.FIXED_TONE, degradedMode);
        assertTrue(controller.bootstrapFixedProgressObservedThisTurn());
        assertFalse(controller.bootstrapAutoTrackFallbackLatched());
    }

    @Test
    public void preTrustFallbackStillTriggersWhenTurnNeverBuiltFixedProgress() {
        RxTurnController controller = new RxTurnController();
        assertTrue(controller.observe(true, false, 100L, 0).startedNewTurn());

        CwSignalProcessor.RxToneMode mode = RxToneModeBootstrapDecider.resolveHybridBootstrapMode(
                false,
                controller,
                snapshot(false, 0, new char[]{'L', 'L', '.', '.', '.', '.', '.', '.', '.', '.'}),
                260L
        );

        assertEquals(CwSignalProcessor.RxToneMode.AUTO_TRACK, mode);
        assertTrue(controller.bootstrapAutoTrackFallbackLatched());
    }

    @Test
    public void postTrustKeepsFixedAcrossLaterValleyAfterHealthyBootstrapEvenWhenOnPreferred() {
        RxTurnController controller = new RxTurnController();
        assertTrue(controller.observe(true, false, 100L, 0).startedNewTurn());
        controller.noteBootstrapFixedProgressObserved();

        CwSignalProcessor.RxToneMode modeAtTrust = RxToneModeBootstrapDecider.resolveHybridBootstrapMode(
                true,
                controller,
                snapshot(true, 3, history('L', 'L', 'L', 'L', 'L', 'L', 'L', 'L', '.', '.')),
                260L
        );
        CwSignalProcessor.RxToneMode modeAfterGrace = RxToneModeBootstrapDecider.resolveHybridBootstrapMode(
                true,
                controller,
                snapshot(false, 0, history('.', '.', '.', '.', '.', '.', '.', '.', '.', '.')),
                620L
        );

        assertEquals(CwSignalProcessor.RxToneMode.FIXED_TONE, modeAtTrust);
        assertEquals(CwSignalProcessor.RxToneMode.FIXED_TONE, modeAfterGrace);
        assertEquals(260L, controller.trustedTimingEstablishedAtMs());
    }

    @Test
    public void postTrustFallsBackToAutoWhenTurnNeverBuiltFixedProgress() {
        RxTurnController controller = new RxTurnController();
        assertTrue(controller.observe(true, false, 100L, 0).startedNewTurn());

        CwSignalProcessor.RxToneMode mode = RxToneModeBootstrapDecider.resolveHybridBootstrapMode(
                true,
                controller,
                snapshot(false, 0, history('.', '.', '.', '.', '.', '.', '.', '.', '.', '.')),
                260L
        );

        assertEquals(CwSignalProcessor.RxToneMode.AUTO_TRACK, mode);
    }

    @Test
    public void postTrustGraceYieldsToExplicitRetunePressure() {
        RxTurnController controller = new RxTurnController();
        assertTrue(controller.observe(true, false, 100L, 0).startedNewTurn());
        controller.noteBootstrapFixedProgressObserved();

        CwSignalProcessor.RxToneMode mode = RxToneModeBootstrapDecider.resolveHybridBootstrapMode(
                true,
                controller,
                snapshot(
                        true,
                        3,
                        history('L', 'L', 'L', 'L', 'L', 'L', 'L', 'L', '.', '.'),
                        new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
                        700,
                        740,
                        1
                ),
                260L
        );

        assertEquals(CwSignalProcessor.RxToneMode.AUTO_TRACK, mode);
    }

    private static CwSignalSnapshot snapshot(
            boolean targetToneLocked,
            int consecutiveLockedFrames,
            char[] recentFrontEndStateHistory
    ) {
        int[] recentTrackingOffsetHistoryHz = new int[recentFrontEndStateHistory.length];
        for (int i = 0; i < recentTrackingOffsetHistoryHz.length; i++) {
            recentTrackingOffsetHistoryHz[i] = 0;
        }
        return snapshot(
                targetToneLocked,
                consecutiveLockedFrames,
                recentFrontEndStateHistory,
                recentTrackingOffsetHistoryHz,
                700,
                700,
                0
        );
    }

    private static CwSignalSnapshot snapshot(
            boolean targetToneLocked,
            int consecutiveLockedFrames,
            char[] recentFrontEndStateHistory,
            int[] recentTrackingOffsetHistoryHz,
            int targetToneFrequencyHz,
            int pendingRetuneCandidateFrequencyHz,
            int pendingRetuneCandidateStableScans
    ) {
        return new CwSignalSnapshot(
                recentFrontEndStateHistory.length,
                recentFrontEndStateHistory,
                recentTrackingOffsetHistoryHz,
                false,
                targetToneLocked,
                700,
                targetToneFrequencyHz,
                700,
                0,
                1200,
                800,
                600,
                1800,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                100,
                40,
                0,
                0,
                consecutiveLockedFrames,
                consecutiveLockedFrames,
                0,
                0,
                pendingRetuneCandidateFrequencyHz,
                pendingRetuneCandidateStableScans,
                700,
                700,
                700,
                700,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                false,
                false,
                false,
                false,
                "NONE",
                "NONE",
                0,
                0,
                0,
                0L,
                0L,
                0L,
                -1L,
                null
        );
    }

    private static char[] history(char... states) {
        return states;
    }
}
