package org.bi9clt.cwcn.core.rx;

import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class RxBootstrapTimingObserverTest {
    @Test
    public void boundaryObservationCanInitializeAnchorAndTurnSeedTogether() {
        CwHybridTimingModel timingModel = new CwHybridTimingModel();
        timingModel.setSeedWpm(24);
        TimingAnchorController timingAnchorController = new TimingAnchorController();
        timingAnchorController.beginNewTurn(24, 100L);
        RxTurnController turnController = new RxTurnController();
        turnController.setTxSeedWpm(24);
        turnController.observe(true, false, 120L, 24);

        CwSignalSnapshot signalSnapshot = strongSignal();
        CwTimingSnapshot timingSnapshot = timingSnapshotFromDotMs(50L);

        RxBootstrapTimingObserver.maybeNoteBootstrapTimingBoundary(
                gapEvent(200L, 150L, 50L, CwTimingEvent.Classification.LETTER_GAP),
                signalSnapshot,
                timingSnapshot,
                null,
                timingModel,
                null,
                timingAnchorController,
                null,
                turnController
        );
        RxBootstrapTimingObserver.maybeNoteBootstrapTimingBoundary(
                gapEvent(320L, 150L, 50L, CwTimingEvent.Classification.LETTER_GAP),
                signalSnapshot,
                timingSnapshot,
                null,
                timingModel,
                null,
                timingAnchorController,
                null,
                turnController
        );

        assertTrue(timingAnchorController.trustedDotEstimateMs() >= 49L);
        assertTrue(timingAnchorController.trustedDotEstimateMs() <= 51L);
        assertTrue(turnController.currentTurnAnchorWpm() > 0);
    }

    @Test
    public void cadenceObservationCanInitializeCadenceTrust() {
        CwHybridTimingModel timingModel = new CwHybridTimingModel();
        timingModel.setSeedWpm(22);
        TimingAnchorController timingAnchorController = new TimingAnchorController();
        timingAnchorController.beginNewTurn(22, 100L);

        CwSignalSnapshot signalSnapshot = cadenceSignal();
        CwTimingSnapshot timingSnapshot = timingSnapshotFromDotMs(55L);

        RxBootstrapTimingObserver.maybeNoteBootstrapCadenceObservation(
                gapEvent(200L, 55L, 55L, CwTimingEvent.Classification.INTRA_SYMBOL_GAP),
                signalSnapshot,
                timingSnapshot,
                null,
                timingModel,
                null,
                timingAnchorController,
                null
        );
        RxBootstrapTimingObserver.maybeNoteBootstrapCadenceObservation(
                gapEvent(420L, 54L, 55L, CwTimingEvent.Classification.INTRA_SYMBOL_GAP),
                signalSnapshot,
                timingSnapshot,
                null,
                timingModel,
                null,
                timingAnchorController,
                null
        );
        RxBootstrapTimingObserver.maybeNoteBootstrapCadenceObservation(
                gapEvent(640L, 56L, 55L, CwTimingEvent.Classification.INTRA_SYMBOL_GAP),
                signalSnapshot,
                timingSnapshot,
                null,
                timingModel,
                null,
                timingAnchorController,
                null
        );

        assertTrue(timingAnchorController.trustedDotEstimateMs() >= 54L);
        assertTrue(timingAnchorController.trustedDotEstimateMs() <= 56L);
        assertEquals(TimingAnchorController.TrustOrigin.CADENCE, timingAnchorController.trustOrigin());
    }

    @Test
    public void applyBoundaryObservationCanDriveSharedSideEffectsWithoutRedoingDecisionLogic() {
        CwHybridTimingModel timingModel = new CwHybridTimingModel();
        timingModel.setSeedWpm(24);
        TimingAnchorController timingAnchorController = new TimingAnchorController();
        timingAnchorController.beginNewTurn(24, 100L);
        RxTurnController turnController = new RxTurnController();
        turnController.setTxSeedWpm(24);
        turnController.observe(true, false, 120L, 24);

        RxBootstrapTimingObserver.applyBootstrapTimingBoundaryObservation(
                50L,
                200L,
                timingModel,
                timingAnchorController,
                turnController
        );
        RxBootstrapTimingObserver.applyBootstrapTimingBoundaryObservation(
                50L,
                320L,
                timingModel,
                timingAnchorController,
                turnController
        );

        assertTrue(timingAnchorController.trustedDotEstimateMs() >= 49L);
        assertTrue(timingAnchorController.trustedDotEstimateMs() <= 51L);
        assertTrue(turnController.currentTurnAnchorWpm() > 0);
    }

    @Test
    public void applyCadenceObservationCanDriveSharedCadenceSideEffectsWithoutRedoingDecisionLogic() {
        CwHybridTimingModel timingModel = new CwHybridTimingModel();
        timingModel.setSeedWpm(22);
        TimingAnchorController timingAnchorController = new TimingAnchorController();
        timingAnchorController.beginNewTurn(22, 100L);

        RxBootstrapTimingObserver.applyBootstrapCadenceObservation(
                55L,
                200L,
                timingModel,
                timingAnchorController
        );
        RxBootstrapTimingObserver.applyBootstrapCadenceObservation(
                54L,
                420L,
                timingModel,
                timingAnchorController
        );
        RxBootstrapTimingObserver.applyBootstrapCadenceObservation(
                56L,
                640L,
                timingModel,
                timingAnchorController
        );

        assertTrue(timingAnchorController.trustedDotEstimateMs() >= 54L);
        assertTrue(timingAnchorController.trustedDotEstimateMs() <= 56L);
        assertEquals(TimingAnchorController.TrustOrigin.CADENCE, timingAnchorController.trustOrigin());
    }

    @Test
    public void boundaryDecisionRejectsCandidateAlreadyBelowTrustedAnchor() {
        TimingAnchorController timingAnchorController = new TimingAnchorController();
        timingAnchorController.beginNewTurn(24, 100L);
        timingAnchorController.noteBootstrapBoundaryObservation(50L, 200L);
        timingAnchorController.noteBootstrapBoundaryObservation(50L, 320L);

        String decision = RxBootstrapTimingObserver.diagnoseBootstrapTimingBoundaryDecision(
                gapEvent(480L, 120L, 50L, CwTimingEvent.Classification.LETTER_GAP),
                strongSignal(),
                timingSnapshotFromDotMs(50L),
                null,
                null,
                null,
                timingAnchorController
        );

        assertEquals("already-trusted", decision);
    }

    @Test
    public void structuredLetterGapCanStillBootstrapBoundaryTrust() {
        String decision = RxBootstrapTimingObserver.diagnoseBootstrapTimingBoundaryDecision(
                gapEvent(200L, 150L, 50L, 50L, CwTimingEvent.Classification.LETTER_GAP),
                strongSignal(),
                timingSnapshotFromDotMs(50L),
                null,
                null,
                null,
                null
        );

        assertEquals("pass", decision);
    }

    @Test
    public void raggedLetterGapDoesNotBootstrapBoundaryTrust() {
        String decision = RxBootstrapTimingObserver.diagnoseBootstrapTimingBoundaryDecision(
                gapEvent(200L, 260L, 50L, 50L, CwTimingEvent.Classification.LETTER_GAP),
                strongSignal(),
                timingSnapshotFromDotMs(50L),
                null,
                null,
                null,
                null
        );

        assertEquals("not-bootstrap-gap", decision);
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
        return gapEvent(
                timestampMs,
                durationMs,
                dotEstimateMs,
                dotEstimateMs,
                classification
        );
    }

    private static CwTimingEvent gapEvent(
            long timestampMs,
            long durationMs,
            long dotEstimateMs,
            long intraGapEstimateMs,
            CwTimingEvent.Classification classification
    ) {
        return new CwTimingEvent(
                CwTimingEvent.Kind.GAP,
                classification,
                timestampMs,
                durationMs,
                dotEstimateMs,
                intraGapEstimateMs
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
