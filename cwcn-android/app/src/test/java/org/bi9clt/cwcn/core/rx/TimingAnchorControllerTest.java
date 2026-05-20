package org.bi9clt.cwcn.core.rx;

import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class TimingAnchorControllerTest {
    @Test
    public void stableDecodeInitializesAnchor() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(24, 100L);

        controller.noteStableDecode(strongSignal(), timingSnapshot(24.0d), 200L);
        assertEquals(0L, controller.trustedDotEstimateMs());
        controller.noteStableDecode(strongSignal(), timingSnapshot(24.0d), 320L);
        assertEquals(0L, controller.trustedDotEstimateMs());
        controller.noteStableDecode(strongSignal(), timingSnapshot(24.0d), 440L);

        assertTrue(controller.trustedDotEstimateMs() >= 49L);
        assertTrue(controller.trustedDotEstimateMs() <= 51L);
        assertTrue(controller.learningDebt() <= 0.01d);
    }

    @Test
    public void bootstrapBoundaryObservationInitializesAnchorAfterTwoClusteredCandidates() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(24, 100L);

        controller.noteBootstrapBoundaryObservation(strongSignal(), timingSnapshot(24.0d), 200L);
        assertEquals(0L, controller.trustedDotEstimateMs());
        controller.noteBootstrapBoundaryObservation(strongSignal(), timingSnapshot(24.0d), 320L);

        assertTrue(controller.trustedDotEstimateMs() >= 49L);
        assertTrue(controller.trustedDotEstimateMs() <= 51L);
    }

    @Test
    public void boundaryBootstrapCanUseGapDerivedDotCandidatesDirectly() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(15, 100L);

        controller.noteBootstrapBoundaryObservation(47L, 200L);
        assertEquals(0L, controller.trustedDotEstimateMs());
        controller.noteBootstrapBoundaryObservation(44L, 420L);

        assertTrue(controller.trustedDotEstimateMs() >= 44L);
        assertTrue(controller.trustedDotEstimateMs() <= 47L);
    }

    @Test
    public void boundaryBootstrapToleratesMicGapCandidateJitterWithinGapDerivedBand() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(15, 100L);

        controller.noteBootstrapBoundaryObservation(44L, 200L);
        assertEquals(0L, controller.trustedDotEstimateMs());
        controller.noteBootstrapBoundaryObservation(53L, 420L);

        assertTrue(controller.trustedDotEstimateMs() >= 44L);
        assertTrue(controller.trustedDotEstimateMs() <= 53L);
    }

    @Test
    public void boundaryAndStableBootstrapCanInitializeTrustedAnchorTogetherForMidSpeedOpening() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(15, 100L);

        controller.noteBootstrapBoundaryObservation(50L, 200L);
        assertEquals(0L, controller.trustedDotEstimateMs());

        controller.noteStableDecode(strongSignal(), timingSnapshotFromDotMs(52L), 320L);

        assertTrue(controller.trustedDotEstimateMs() >= 50L);
        assertTrue(controller.trustedDotEstimateMs() <= 52L);
        assertEquals(TimingAnchorController.TrustOrigin.BOUNDARY, controller.trustOrigin());
    }

    @Test
    public void boundaryAndStableBootstrapDoesNotTriggerForFastOpeningCandidate() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(15, 100L);

        controller.noteBootstrapBoundaryObservation(45L, 200L);
        controller.noteStableDecode(strongSignal(), timingSnapshotFromDotMs(47L), 320L);

        assertEquals(0L, controller.trustedDotEstimateMs());
    }

    @Test
    public void boundaryAndSoftStableBootstrapCanInitializeTrustedAnchorTogether() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(15, 100L);

        controller.noteBootstrapBoundaryObservation(68L, 200L);
        assertEquals(0L, controller.trustedDotEstimateMs());

        controller.noteBootstrapSoftStableObservation(58L, 320L);

        assertTrue(controller.trustedDotEstimateMs() >= 67L);
        assertTrue(controller.trustedDotEstimateMs() <= 69L);
        assertEquals(TimingAnchorController.TrustOrigin.BOUNDARY, controller.trustOrigin());
    }

    @Test
    public void softStableBootstrapCandidateAloneDoesNotInitializeTrustedAnchor() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(15, 100L);

        controller.noteBootstrapSoftStableObservation(58L, 200L);
        controller.noteBootstrapSoftStableObservation(57L, 320L);
        controller.noteBootstrapSoftStableObservation(59L, 440L);

        assertEquals(0L, controller.trustedDotEstimateMs());
    }

    public void cadenceBootstrapInitializesAnchorAfterThreeClusteredCandidates() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(15, 100L);

        controller.noteBootstrapCadenceObservation(55L, 200L);
        assertEquals(0L, controller.trustedDotEstimateMs());
        controller.noteBootstrapCadenceObservation(54L, 420L);
        assertEquals(0L, controller.trustedDotEstimateMs());
        controller.noteBootstrapCadenceObservation(56L, 640L);

        assertTrue(controller.trustedDotEstimateMs() >= 54L);
        assertTrue(controller.trustedDotEstimateMs() <= 56L);
        assertEquals(TimingAnchorController.TrustOrigin.CADENCE, controller.trustOrigin());
    }

    @Test
    public void boundaryBootstrapCandidatesDoNotPolluteCadenceBootstrapCluster() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(15, 100L);

        controller.noteBootstrapBoundaryObservation(62L, 180L);
        controller.noteBootstrapCadenceObservation(55L, 260L);
        controller.noteBootstrapCadenceObservation(54L, 420L);
        controller.noteBootstrapCadenceObservation(56L, 580L);

        assertTrue(controller.trustedDotEstimateMs() >= 54L);
        assertTrue(controller.trustedDotEstimateMs() <= 56L);
    }

    @Test
    public void weakFastDriftFreezesLearning() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(24, 100L);
        primeTrustedAnchor(controller);

        boolean allow = true;
        for (int index = 0; index < 2; index++) {
            allow = controller.shouldAllowTimingLearning(
                    weakSignal(),
                    timingSnapshot(34.0d),
                    true,
                    300L + (index * 40L)
            );
        }

        assertFalse(allow);
        assertTrue(controller.learningDebt() >= 0.55d);
    }

    @Test
    public void highDebtReanchorsFastTimingEventToTrustedDot() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(24, 100L);
        primeTrustedAnchor(controller);
        controller.shouldAllowTimingLearning(weakSignal(), timingSnapshot(34.0d), true, 300L);
        controller.shouldAllowTimingLearning(weakSignal(), timingSnapshot(34.0d), true, 340L);

        CwTimingEvent adapted = controller.adaptTimingEvent(
                new CwTimingEvent(
                        CwTimingEvent.Kind.TONE,
                        CwTimingEvent.Classification.DIT,
                        360L,
                        35L,
                        35L,
                        35L
                ),
                weakSignal(),
                timingSnapshot(34.0d),
                360L
        );

        assertTrue(adapted.dotEstimateMs() >= 49L);
        assertEquals(CwTimingEvent.Classification.DIT, adapted.classification());
    }

    @Test
    public void strongNearAnchorEvidenceDecaysDebt() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(24, 100L);
        primeTrustedAnchor(controller);
        controller.shouldAllowTimingLearning(weakSignal(), timingSnapshot(34.0d), true, 300L);
        controller.shouldAllowTimingLearning(weakSignal(), timingSnapshot(34.0d), true, 340L);

        boolean allow = controller.shouldAllowTimingLearning(
                strongSignal(),
                timingSnapshot(24.2d),
                true,
                500L
        );

        assertTrue(allow);
        assertTrue(controller.learningDebt() < 0.80d);
    }

    @Test
    public void strongNearTargetFastDriftInsideRescueBandCanStillUpdateTrustedAnchor() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(24, 100L);
        primeTrustedAnchor(controller);

        CwSignalSnapshot rescueSignal = strongNearTargetRescueSignal();
        CwTimingSnapshot rescueTiming = timingSnapshotFromDotMs(45L);

        assertTrue(controller.shouldAcceptStableAnchorUpdate(
                rescueSignal,
                rescueTiming,
                true,
                500L
        ));
        controller.noteStableDecode(rescueSignal, rescueTiming, 500L);
        controller.noteStableDecode(rescueSignal, rescueTiming, 560L);
        controller.noteStableDecode(rescueSignal, rescueTiming, 620L);

        assertTrue(controller.trustedDotEstimateMs() <= 49L);
        assertTrue(controller.trustedDotEstimateMs() >= 45L);
        assertTrue(controller.learningDebt() <= 0.01d);
    }

    @Test
    public void strongNearTargetFastDriftBelowRescueBandIsStillRejected() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(24, 100L);
        primeTrustedAnchor(controller);

        boolean allow = controller.shouldAcceptStableAnchorUpdate(
                strongNearTargetRescueSignal(),
                timingSnapshotFromDotMs(44L),
                true,
                500L
        );

        assertFalse(allow);
        assertTrue(controller.learningDebt() > 0.0d);
    }

    @Test
    public void boundaryOpeningQuietGapEdgeCanRescueInitiallySlowBoundaryTrust() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(15, 100L);
        controller.noteBootstrapBoundaryObservation(52L, 200L);
        controller.noteBootstrapBoundaryObservation(51L, 360L);

        CwSignalSnapshot quietGapEdgeSignal = quietGapEdgeSignal();
        CwTimingSnapshot fastTiming = timingSnapshotFromDotMs(45L);

        assertTrue(controller.shouldAcceptStableAnchorUpdate(
                quietGapEdgeSignal,
                fastTiming,
                true,
                500L
        ));

        controller.noteStableDecode(quietGapEdgeSignal, fastTiming, 500L);
        controller.noteStableDecode(quietGapEdgeSignal, fastTiming, 560L);
        controller.noteStableDecode(quietGapEdgeSignal, fastTiming, 620L);
        controller.noteStableDecode(quietGapEdgeSignal, fastTiming, 680L);

        assertTrue(controller.trustedDotEstimateMs() <= 49L);
        assertTrue(controller.trustedDotEstimateMs() >= 45L);
    }

    @Test
    public void boundaryOpeningQuietGapEdgeStillRejectsCandidateBelowRescueFloor() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(15, 100L);
        controller.noteBootstrapBoundaryObservation(52L, 200L);
        controller.noteBootstrapBoundaryObservation(51L, 360L);

        boolean allow = controller.shouldAcceptStableAnchorUpdate(
                quietGapEdgeSignal(),
                timingSnapshotFromDotMs(43L),
                true,
                500L
        );

        assertFalse(allow);
    }

    @Test
    public void turnResetClearsAnchorAndDebt() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(24, 100L);
        primeTrustedAnchor(controller);
        controller.shouldAllowTimingLearning(weakSignal(), timingSnapshot(34.0d), true, 300L);

        controller.beginNewTurn(18, 1000L);

        assertEquals(0L, controller.trustedDotEstimateMs());
        assertEquals(0.0d, controller.learningDebt(), 0.001d);
    }

    @Test
    public void fastToneEventFreezesLearningBeforeModelConsumesIt() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(24, 100L);
        primeTrustedAnchor(controller);

        boolean allow = controller.shouldAllowTimingLearningForEvent(
                toneOff(300L, 35L),
                weakSignal(),
                timingSnapshot(24.0d),
                true,
                300L
        );

        assertFalse(allow);
    }

    @Test
    public void strongNearAnchorToneOnEventCanBypassFreezeDebt() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(24, 100L);
        primeTrustedAnchor(controller);
        controller.shouldAllowTimingLearning(weakSignal(), timingSnapshot(34.0d), true, 300L);
        controller.shouldAllowTimingLearning(weakSignal(), timingSnapshot(34.0d), true, 340L);

        boolean allow = controller.shouldAllowTimingLearningForEvent(
                toneOn(500L),
                strongSignal(),
                timingSnapshotFromDotMs(47L),
                true,
                500L
        );

        assertTrue(allow);
    }

    @Test
    public void strongRecentLockToneOffEventCanBypassFreezeDebt() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(24, 100L);
        primeTrustedAnchor(controller);
        controller.shouldAllowTimingLearning(weakSignal(), timingSnapshot(34.0d), true, 300L);
        controller.shouldAllowTimingLearning(weakSignal(), timingSnapshot(34.0d), true, 340L);

        boolean allow = controller.shouldAllowTimingLearningForEvent(
                toneOff(500L, 140L),
                strongToneOffSignal(),
                timingSnapshotFromDotMs(47L),
                true,
                500L
        );

        assertTrue(allow);
    }

    @Test
    public void cadenceOpeningShortToneOffDoesNotFreezeImmediatelyAfterInit() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(15, 100L);
        primeCadenceTrustedAnchor(controller);

        boolean allow = controller.shouldAllowTimingLearningForEvent(
                toneOff(500L, 35L),
                cadenceOpeningSignal(),
                timingSnapshotFromDotMs(107L),
                true,
                500L
        );

        assertTrue(allow);
    }

    @Test
    public void cadenceOpeningTimingLearningDoesNotFreezeBeforeFirstStableDecode() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(15, 100L);
        primeCadenceTrustedAnchor(controller);

        boolean allow = controller.shouldAllowTimingLearning(
                weakSignal(),
                timingSnapshotFromDotMs(34L),
                true,
                500L
        );

        assertTrue(allow);
    }

    @Test
    public void cadenceOpeningDashLikeToneOffCanPassWithModerateRecentLock() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(15, 100L);
        primeCadenceTrustedAnchor(controller);

        boolean allow = controller.shouldAllowTimingLearningForEvent(
                toneOff(500L, 115L),
                cadenceOpeningModerateLockSignal(),
                timingSnapshotFromDotMs(99L),
                true,
                500L
        );

        assertTrue(allow);
    }

    @Test
    public void cadenceOpeningSlowRawToneEventReanchorsToTrustedDot() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(15, 100L);
        primeCadenceTrustedAnchor(controller);
        controller.noteStableDecode(strongSignal(), timingSnapshotFromDotMs(45L), 420L);

        CwTimingEvent adapted = controller.adaptTimingEvent(
                new CwTimingEvent(
                        CwTimingEvent.Kind.TONE,
                        CwTimingEvent.Classification.DIT,
                        520L,
                        115L,
                        99L,
                        99L
                ),
                cadenceOpeningModerateLockSignal(),
                timingSnapshotFromDotMs(99L),
                520L
        );

        assertEquals(CwTimingEvent.Classification.DAH, adapted.classification());
        assertEquals(45L, adapted.dotEstimateMs());
    }

    public void cadenceOpeningSlowRawToneEventWaitsForStableDecodeBeforeReanchor() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(15, 100L);
        primeCadenceTrustedAnchor(controller);

        CwTimingEvent adapted = controller.adaptTimingEvent(
                new CwTimingEvent(
                        CwTimingEvent.Kind.TONE,
                        CwTimingEvent.Classification.DIT,
                        520L,
                        115L,
                        99L,
                        99L
                ),
                cadenceOpeningModerateLockSignal(),
                timingSnapshotFromDotMs(99L),
                520L
        );

        assertEquals(CwTimingEvent.Classification.DIT, adapted.classification());
        assertEquals(99L, adapted.dotEstimateMs());
    }

    @Test
    public void cadenceOpeningShortToneOffRelaxStopsAfterFirstStableDecode() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(15, 100L);
        primeCadenceTrustedAnchor(controller);
        controller.noteStableDecode(strongSignal(), timingSnapshotFromDotMs(45L), 420L);

        boolean allow = controller.shouldAllowTimingLearningForEvent(
                toneOff(500L, 35L),
                cadenceOpeningSignal(),
                timingSnapshotFromDotMs(107L),
                true,
                500L
        );

        assertFalse(allow);
    }

    @Test
    public void boundaryOpeningShortLetterGapIsHeldAsIntraSymbolGap() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(15, 100L);
        primeBoundaryTrustedAnchor(controller);

        CwTimingEvent adapted = controller.adaptTimingEvent(
                gapEvent(520L, 106L, 35L),
                weakSignal(),
                timingSnapshot(34.0d),
                520L
        );

        assertEquals(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, adapted.classification());
    }

    @Test
    public void boundaryOpeningLetterGapJustAboveHoldFloorRemainsLetterGap() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(15, 100L);
        controller.noteBootstrapBoundaryObservation(76L, 200L);
        controller.noteBootstrapBoundaryObservation(76L, 360L);

        CwTimingEvent adapted = controller.adaptTimingEvent(
                new CwTimingEvent(
                        CwTimingEvent.Kind.GAP,
                        CwTimingEvent.Classification.LETTER_GAP,
                        520L,
                        182L,
                        66L,
                        66L
                ),
                weakSignal(),
                timingSnapshotFromDotMs(66L),
                520L
        );

        assertEquals(CwTimingEvent.Classification.LETTER_GAP, adapted.classification());
        assertEquals(76L, adapted.dotEstimateMs());
    }

    @Test
    public void boundaryOpeningAmbiguousWordGapIsHeldAsIntraSymbolGap() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(15, 100L);
        primeBoundaryTrustedAnchor(controller);

        CwTimingEvent adapted = controller.adaptTimingEvent(
                gapEvent(520L, 243L, 35L),
                weakSignal(),
                timingSnapshot(34.0d),
                520L
        );

        assertEquals(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, adapted.classification());
    }

    @Test
    public void boundaryOpeningGapHoldStopsAfterStableDecode() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(15, 100L);
        primeBoundaryTrustedAnchor(controller);
        controller.noteStableDecode(strongSignal(), timingSnapshot(24.0d), 480L);

        CwTimingEvent adapted = controller.adaptTimingEvent(
                gapEvent(520L, 243L, 35L),
                weakSignal(),
                timingSnapshot(34.0d),
                520L
        );

        assertEquals(CwTimingEvent.Classification.WORD_GAP, adapted.classification());
    }

    @Test
    public void boundaryOpeningGapHoldHasSmallBudget() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(15, 100L);
        primeBoundaryTrustedAnchor(controller);

        CwTimingEvent first = controller.adaptTimingEvent(
                gapEvent(520L, 106L, 35L),
                weakSignal(),
                timingSnapshot(34.0d),
                520L
        );
        CwTimingEvent second = controller.adaptTimingEvent(
                gapEvent(620L, 106L, 35L),
                weakSignal(),
                timingSnapshot(34.0d),
                620L
        );
        CwTimingEvent third = controller.adaptTimingEvent(
                gapEvent(720L, 106L, 35L),
                weakSignal(),
                timingSnapshot(34.0d),
                720L
        );

        assertEquals(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, first.classification());
        assertEquals(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, second.classification());
        assertEquals(CwTimingEvent.Classification.LETTER_GAP, third.classification());
    }

    @Test
    public void bootstrapCandidatesMustClusterBeforeAnchorInitializes() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(24, 100L);

        controller.noteStableDecode(strongSignal(), timingSnapshot(24.0d), 200L);
        controller.noteStableDecode(strongSignal(), timingSnapshot(19.0d), 320L);
        controller.noteStableDecode(strongSignal(), timingSnapshot(24.0d), 440L);

        assertEquals(0L, controller.trustedDotEstimateMs());
    }

    @Test
    public void widelySeparatedBootstrapCandidatesExpireInsteadOfInitializingAnchor() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(24, 100L);

        controller.noteStableDecode(strongSignal(), timingSnapshot(24.0d), 200L);
        controller.noteStableDecode(strongSignal(), timingSnapshot(24.0d), 320L);
        controller.noteStableDecode(strongSignal(), timingSnapshot(24.0d), 3200L);

        assertEquals(0L, controller.trustedDotEstimateMs());
    }

    @Test
    public void trustedAnchorRetargetKeepsNearDashRawToneAsDash() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(20, 100L);
        controller.noteBootstrapBoundaryObservation(83L, 200L);
        controller.noteBootstrapBoundaryObservation(83L, 320L);

        CwTimingEvent adapted = controller.adaptTimingEvent(
                new CwTimingEvent(
                        CwTimingEvent.Kind.TONE,
                        CwTimingEvent.Classification.DAH,
                        520L,
                        139L,
                        76L,
                        76L
                ),
                strongSignal(),
                timingSnapshotFromDotMs(76L),
                520L
        );

        assertEquals(CwTimingEvent.Classification.DAH, adapted.classification());
        assertEquals(83L, adapted.dotEstimateMs());
    }

    @Test
    public void trustedAnchorRetargetKeepsNearLetterGapAsLetterGap() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(20, 100L);
        controller.noteStableDecode(strongSignal(), timingSnapshotFromDotMs(83L), 200L);
        controller.noteStableDecode(strongSignal(), timingSnapshotFromDotMs(83L), 320L);
        controller.noteStableDecode(strongSignal(), timingSnapshotFromDotMs(83L), 440L);

        CwTimingEvent adapted = controller.adaptTimingEvent(
                new CwTimingEvent(
                        CwTimingEvent.Kind.GAP,
                        CwTimingEvent.Classification.LETTER_GAP,
                        520L,
                        139L,
                        76L,
                        76L
                ),
                strongSignal(),
                timingSnapshotFromDotMs(76L),
                520L
        );

        assertEquals(CwTimingEvent.Classification.LETTER_GAP, adapted.classification());
        assertEquals(83L, adapted.dotEstimateMs());
    }

    @Test
    public void trustedAnchorRetargetKeepsMarginalLetterGapFromPromotingToWordGap() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(15, 100L);
        controller.noteStableDecode(strongSignal(), timingSnapshotFromDotMs(77L), 200L);
        controller.noteStableDecode(strongSignal(), timingSnapshotFromDotMs(77L), 320L);
        controller.noteStableDecode(strongSignal(), timingSnapshotFromDotMs(77L), 440L);
        primeHighDebt(controller);

        CwTimingEvent adapted = controller.adaptTimingEvent(
                new CwTimingEvent(
                        CwTimingEvent.Kind.GAP,
                        CwTimingEvent.Classification.LETTER_GAP,
                        520L,
                        374L,
                        87L,
                        87L
                ),
                weakSignal(),
                timingSnapshotFromDotMs(87L),
                520L
        );

        assertEquals(CwTimingEvent.Classification.LETTER_GAP, adapted.classification());
        assertEquals(77L, adapted.dotEstimateMs());
    }

    @Test
    public void trustedAnchorRetargetStillPromotesClearWordLikeGap() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(15, 100L);
        controller.noteStableDecode(strongSignal(), timingSnapshotFromDotMs(77L), 200L);
        controller.noteStableDecode(strongSignal(), timingSnapshotFromDotMs(77L), 320L);
        controller.noteStableDecode(strongSignal(), timingSnapshotFromDotMs(77L), 440L);
        primeHighDebt(controller);

        CwTimingEvent adapted = controller.adaptTimingEvent(
                new CwTimingEvent(
                        CwTimingEvent.Kind.GAP,
                        CwTimingEvent.Classification.LETTER_GAP,
                        520L,
                        430L,
                        92L,
                        92L
                ),
                weakSignal(),
                timingSnapshotFromDotMs(92L),
                520L
        );

        assertEquals(CwTimingEvent.Classification.WORD_GAP, adapted.classification());
        assertEquals(77L, adapted.dotEstimateMs());
    }

    @Test
    public void trustedAnchorRetargetKeepsMarginalWordGapFromPromotingToUnknown() {
        TimingAnchorController controller = new TimingAnchorController();
        controller.beginNewTurn(15, 100L);
        controller.noteStableDecode(strongSignal(), timingSnapshotFromDotMs(77L), 200L);
        controller.noteStableDecode(strongSignal(), timingSnapshotFromDotMs(77L), 320L);
        controller.noteStableDecode(strongSignal(), timingSnapshotFromDotMs(77L), 440L);
        primeHighDebt(controller);

        CwTimingEvent adapted = controller.adaptTimingEvent(
                new CwTimingEvent(
                        CwTimingEvent.Kind.GAP,
                        CwTimingEvent.Classification.WORD_GAP,
                        520L,
                        997L,
                        91L,
                        91L
                ),
                weakSignal(),
                timingSnapshotFromDotMs(91L),
                520L
        );

        assertEquals(CwTimingEvent.Classification.WORD_GAP, adapted.classification());
        assertEquals(77L, adapted.dotEstimateMs());
    }

    private void primeTrustedAnchor(TimingAnchorController controller) {
        controller.noteStableDecode(strongSignal(), timingSnapshot(24.0d), 200L);
        controller.noteStableDecode(strongSignal(), timingSnapshot(24.0d), 320L);
        controller.noteStableDecode(strongSignal(), timingSnapshot(24.0d), 440L);
    }

    private void primeHighDebt(TimingAnchorController controller) {
        controller.shouldAllowTimingLearning(weakSignal(), timingSnapshot(34.0d), true, 300L);
        controller.shouldAllowTimingLearning(weakSignal(), timingSnapshot(34.0d), true, 340L);
    }

    private void primeBoundaryTrustedAnchor(TimingAnchorController controller) {
        controller.noteBootstrapBoundaryObservation(45L, 200L);
        controller.noteBootstrapBoundaryObservation(46L, 360L);
    }

    private void primeCadenceTrustedAnchor(TimingAnchorController controller) {
        controller.noteBootstrapCadenceObservation(45L, 200L);
        controller.noteBootstrapCadenceObservation(45L, 320L);
        controller.noteBootstrapCadenceObservation(45L, 440L);
    }

    private CwTimingSnapshot timingSnapshot(double wpm) {
        return timingSnapshotFromDotMs(Math.max(1L, Math.round(1200.0d / wpm)));
    }

    private CwTimingSnapshot timingSnapshotFromDotMs(long dotMs) {
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

    private CwToneEvent toneOff(long timestampMs, long durationMs) {
        return new CwToneEvent(
                CwToneEvent.Type.TONE_OFF,
                timestampMs,
                16000,
                12000.0d,
                durationMs
        );
    }

    private CwToneEvent toneOn(long timestampMs) {
        return new CwToneEvent(
                CwToneEvent.Type.TONE_ON,
                timestampMs,
                16000,
                12000.0d,
                0L
        );
    }

    private CwTimingEvent gapEvent(long timestampMs, long durationMs, long dotEstimateMs) {
        return new CwTimingEvent(
                CwTimingEvent.Kind.GAP,
                CwTimingEvent.Classification.UNKNOWN,
                timestampMs,
                durationMs,
                dotEstimateMs,
                dotEstimateMs
        );
    }

    private CwSignalSnapshot strongSignal() {
        return signalSnapshot(true, "LLLLLLLLLLLLLLLLLLLLLLLL", 6, 0.58d, 0.66d);
    }

    private CwSignalSnapshot strongNearTargetRescueSignal() {
        return signalSnapshot(true, "LLLLLLLLLLLL............", 6, 0.88d, 0.72d);
    }

    private CwSignalSnapshot strongToneOffSignal() {
        return signalSnapshot(false, "LLLLLLLLLLLLLLLLLLLLLLLL", 6, 0.11d, 0.10d);
    }

    private CwSignalSnapshot quietGapEdgeSignal() {
        return signalSnapshot(false, "LLLLLLLLLLLLLL..........", 6, 0.04d, 0.04d);
    }

    private CwSignalSnapshot weakSignal() {
        return signalSnapshot(false, "uuuuuuuuuuuu........LLLL", 60, 0.18d, 0.22d);
    }

    private CwSignalSnapshot cadenceOpeningSignal() {
        return signalSnapshot(true, "LLLLLLLLLLLLLLLLLLLLLLLL", 60, 0.43d, 0.32d);
    }

    private CwSignalSnapshot cadenceOpeningModerateLockSignal() {
        return signalSnapshot(true, "LLLLLLLLLLL.............", 60, 0.44d, 0.33d);
    }

    private CwSignalSnapshot signalSnapshot(
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

    private int countStates(char[] states, char primary, char secondary) {
        int count = 0;
        for (char state : states) {
            if (state == primary || state == secondary) {
                count += 1;
            }
        }
        return count;
    }

    private int countStates(char[] states, char primary) {
        int count = 0;
        for (char state : states) {
            if (state == primary) {
                count += 1;
            }
        }
        return count;
    }
}
