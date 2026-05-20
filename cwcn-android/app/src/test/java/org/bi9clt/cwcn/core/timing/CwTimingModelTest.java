package org.bi9clt.cwcn.core.timing;

import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class CwTimingModelTest {
    @Test
    public void firstLongToneBootstrapsAsDashInsteadOfInflatingDotEstimate() {
        CwTimingModel model = new CwTimingModel();

        List<CwTimingEvent> events = model.process(toneOff(180L, 180L));

        assertEquals(1, events.size());
        assertEquals(CwTimingEvent.Classification.DAH, events.get(0).classification());
        assertTrue(events.get(0).dotEstimateMs() >= 55L);
        assertTrue(events.get(0).dotEstimateMs() <= 85L);
        assertTrue(model.snapshot().estimatedWpm() >= 14);
    }

    @Test
    public void firstFast30WpmDashStillBootstrapsAsDash() {
        CwTimingModel model = new CwTimingModel();

        List<CwTimingEvent> events = model.process(toneOff(118L, 118L));

        assertEquals(1, events.size());
        assertEquals(CwTimingEvent.Classification.DAH, events.get(0).classification());
        assertTrue("dot=" + events.get(0).dotEstimateMs(), events.get(0).dotEstimateMs() >= 43L);
        assertTrue("dot=" + events.get(0).dotEstimateMs(), events.get(0).dotEstimateMs() <= 48L);
        assertTrue("wpm=" + model.snapshot().estimatedWpm(), model.snapshot().estimatedWpm() >= 25);
    }

    @Test
    public void firstLetterGapAfterLeadingDashStillClassifiesAsLetterGap() {
        CwTimingModel model = new CwTimingModel();

        model.process(toneOff(180L, 180L));
        List<CwTimingEvent> gapEvents = model.process(toneOn(360L));

        assertEquals(1, gapEvents.size());
        assertEquals(CwTimingEvent.Classification.LETTER_GAP, gapEvents.get(0).classification());
        assertTrue(gapEvents.get(0).dotEstimateMs() >= 55L);
        assertTrue(gapEvents.get(0).dotEstimateMs() <= 85L);
    }

    @Test
    public void moderatelyCompressedWordGapStillClassifiesAsWordGap() {
        CwTimingModel model = new CwTimingModel();

        model.process(toneOff(60L, 60L));
        List<CwTimingEvent> gapEvents = model.process(toneOn(380L));

        assertEquals(1, gapEvents.size());
        assertEquals(CwTimingEvent.Classification.WORD_GAP, gapEvents.get(0).classification());
    }

    @Test
    public void longButStillLetterSizedGapRemainsLetterGap() {
        CwTimingModel model = new CwTimingModel();

        model.process(toneOff(60L, 60L));
        List<CwTimingEvent> gapEvents = model.process(toneOn(300L));

        assertEquals(1, gapEvents.size());
        assertEquals(CwTimingEvent.Classification.LETTER_GAP, gapEvents.get(0).classification());
    }

    @Test
    public void firstFastDotBootstrapsCloserToActualFastSpeed() {
        CwTimingModel model = new CwTimingModel();

        List<CwTimingEvent> events = model.process(toneOff(40L, 40L));

        assertEquals(1, events.size());
        assertEquals(CwTimingEvent.Classification.DIT, events.get(0).classification());
        assertTrue(events.get(0).dotEstimateMs() >= 42L);
        assertTrue(events.get(0).dotEstimateMs() <= 50L);
        assertTrue(model.snapshot().estimatedWpm() >= 24);
    }

    @Test
    public void repeatedFast30WpmDotsConvergeTowardHighSpeedInsteadOfStayingSlow() {
        CwTimingModel model = new CwTimingModel();

        model.process(toneOff(40L, 40L));
        model.process(toneOn(80L));
        model.process(toneOff(120L, 40L));
        model.process(toneOn(160L));
        model.process(toneOff(200L, 40L));

        CwTimingSnapshot snapshot = model.snapshot();
        assertTrue("dot=" + snapshot.dotEstimateMs(), snapshot.dotEstimateMs() <= 46L);
        assertTrue("wpm=" + snapshot.estimatedWpm(), snapshot.estimatedWpm() >= 26);
    }

    @Test
    public void singleStretchedGapDoesNotImmediatelyDragFastTimingModelBackToSlowSpeed() {
        CwTimingModel model = new CwTimingModel();

        model.process(toneOff(40L, 40L));
        model.process(toneOn(80L));
        model.process(toneOff(120L, 40L));
        model.process(toneOn(280L));
        model.process(toneOff(320L, 40L));

        CwTimingSnapshot snapshot = model.snapshot();
        assertTrue("dot=" + snapshot.dotEstimateMs(), snapshot.dotEstimateMs() <= 52L);
        assertTrue("wpm=" + snapshot.estimatedWpm(), snapshot.estimatedWpm() >= 23);
    }

    @Test
    public void fastLetterGapDoesNotGetMisclassifiedAsIntraSymbolGap() {
        CwTimingModel model = new CwTimingModel();

        model.process(toneOff(40L, 40L));
        model.process(toneOn(80L));
        model.process(toneOff(120L, 40L));
        List<CwTimingEvent> gapEvents = model.process(toneOn(230L));

        assertEquals(1, gapEvents.size());
        assertEquals(CwTimingEvent.Classification.LETTER_GAP, gapEvents.get(0).classification());
    }

    @Test
    public void compressedWordGapCanStillPromoteUsingIntraGapEstimateWhenDotRunsSlow() throws Exception {
        CwTimingModel model = new CwTimingModel();
        setDoubleField(model, "dotEstimateMs", 90.0d);
        setDoubleField(model, "intraGapEstimateMs", 60.0d);
        setBooleanField(model, "initialized", true);
        setLongField(model, "lastToneOffTimestampMs", 100L);

        List<CwTimingEvent> gapEvents = model.process(toneOn(460L));

        assertEquals(1, gapEvents.size());
        assertEquals(CwTimingEvent.Classification.WORD_GAP, gapEvents.get(0).classification());
        assertTrue(gapEvents.get(0).ratioToDotEstimate() >= 3.15d);
        assertTrue(gapEvents.get(0).ratioToIntraGapEstimate() >= 5.0d);
    }

    @Test
    public void stretchedLetterGapDoesNotPromoteToWordGapWithoutStrongIntraGapEvidence() throws Exception {
        CwTimingModel model = new CwTimingModel();
        setDoubleField(model, "dotEstimateMs", 42.0d);
        setDoubleField(model, "intraGapEstimateMs", 38.0d);
        setDoubleField(model, "toneDotReferenceMs", 42.0d);
        setBooleanField(model, "initialized", true);
        setLongField(model, "lastToneOffTimestampMs", 100L);

        List<CwTimingEvent> gapEvents = model.process(toneOn(240L));

        assertEquals(1, gapEvents.size());
        assertEquals(CwTimingEvent.Classification.LETTER_GAP, gapEvents.get(0).classification());
        assertTrue(gapEvents.get(0).ratioToDotEstimate() > 3.15d);
        assertTrue(gapEvents.get(0).ratioToIntraGapEstimate() < 5.0d);
        assertTrue("dot=" + model.snapshot().dotEstimateMs(), model.snapshot().dotEstimateMs() <= 42L);
        assertTrue("intra=" + model.snapshot().intraGapEstimateMs(), model.snapshot().intraGapEstimateMs() <= 38L);
    }

    @Test
    public void longHandoffSizedGapStillClassifiesAsWordGap() throws Exception {
        CwTimingModel model = new CwTimingModel();
        setDoubleField(model, "dotEstimateMs", 67.0d);
        setDoubleField(model, "intraGapEstimateMs", 67.0d);
        setDoubleField(model, "toneDotReferenceMs", 67.0d);
        setBooleanField(model, "initialized", true);
        setLongField(model, "lastToneOffTimestampMs", 100L);

        List<CwTimingEvent> gapEvents = model.process(toneOn(920L));

        assertEquals(1, gapEvents.size());
        assertEquals(CwTimingEvent.Classification.WORD_GAP, gapEvents.get(0).classification());
        assertTrue(gapEvents.get(0).ratioToDotEstimate() > 10.0d);
    }

    @Test
    public void borderlineLetterGapDoesNotImmediatelyPullDotEstimateFaster() throws Exception {
        CwTimingModel model = new CwTimingModel();
        setDoubleField(model, "dotEstimateMs", 49.0d);
        setDoubleField(model, "intraGapEstimateMs", 49.0d);
        setDoubleField(model, "toneDotReferenceMs", 49.0d);
        setDoubleField(model, "dashEstimateMs", 147.0d);
        setBooleanField(model, "initialized", true);
        setLongField(model, "lastToneOffTimestampMs", 100L);

        List<CwTimingEvent> gapEvents = model.process(toneOn(180L));

        assertEquals(1, gapEvents.size());
        assertEquals(CwTimingEvent.Classification.LETTER_GAP, gapEvents.get(0).classification());
        assertTrue("dot=" + model.snapshot().dotEstimateMs(), model.snapshot().dotEstimateMs() >= 48L);
    }

    @Test
    public void toneReferenceKeepsGapClassificationFromCollapsingIntoIntraGap() throws Exception {
        CwTimingModel model = new CwTimingModel();
        setDoubleField(model, "dotEstimateMs", 42.0d);
        setDoubleField(model, "intraGapEstimateMs", 42.0d);
        setDoubleField(model, "toneDotReferenceMs", 49.0d);
        setDoubleField(model, "dashEstimateMs", 147.0d);
        setBooleanField(model, "initialized", true);
        setLongField(model, "lastToneOffTimestampMs", 100L);

        List<CwTimingEvent> gapEvents = model.process(toneOn(180L));

        assertEquals(1, gapEvents.size());
        assertEquals(CwTimingEvent.Classification.LETTER_GAP, gapEvents.get(0).classification());
    }

    @Test
    public void hybridTimingUsesSeedWpmAsWeakBootstrapFloor() {
        CwHybridTimingModel model = new CwHybridTimingModel();
        model.setSeedWpm(24);

        CwTimingSnapshot snapshot = model.snapshot();

        assertTrue("wpm=" + snapshot.estimatedWpmPrecise(), snapshot.estimatedWpmPrecise() >= 20.5d);
        assertTrue("wpm=" + snapshot.estimatedWpmPrecise(), snapshot.estimatedWpmPrecise() <= 24.5d);
    }

    @Test
    public void hybridTimingStableDecodeAnchorLimitsSuddenWpmJump() {
        CwHybridTimingModel model = new CwHybridTimingModel();
        model.setSeedWpm(24);

        model.process(toneOff(50L, 50L));
        model.notifyStableDecode(50L);
        model.process(toneOn(100L));
        model.process(toneOff(150L, 50L));
        model.notifyStableDecode(150L);
        model.process(toneOn(200L));
        model.process(toneOff(250L, 50L));
        model.notifyStableDecode(250L);

        for (int index = 0; index < 6; index++) {
            long toneOnTimestampMs = 300L + (index * 34L);
            model.process(toneOn(toneOnTimestampMs));
            model.process(toneOff(toneOnTimestampMs + 12L, 12L));
        }

        CwTimingSnapshot snapshot = model.snapshot();
        assertTrue("wpm=" + snapshot.estimatedWpmPrecise(), snapshot.estimatedWpmPrecise() <= 29.0d);
    }

    @Test
    public void hybridTimingStableDecodeAnchorDoesNotReanchorToTransientFastBurst() {
        CwHybridTimingModel model = new CwHybridTimingModel();
        model.setSeedWpm(24);

        model.process(toneOff(50L, 50L));
        model.notifyStableDecode(50L);
        model.process(toneOn(100L));
        model.process(toneOff(150L, 50L));
        model.notifyStableDecode(150L);
        model.process(toneOn(200L));
        model.process(toneOff(250L, 50L));
        model.notifyStableDecode(250L);

        for (int index = 0; index < 6; index++) {
            long toneOnTimestampMs = 300L + (index * 34L);
            model.process(toneOn(toneOnTimestampMs));
            model.process(toneOff(toneOnTimestampMs + 12L, 12L));
        }
        model.notifyStableDecode(520L);

        CwTimingSnapshot snapshot = model.snapshot();
        assertTrue("wpm=" + snapshot.estimatedWpmPrecise(), snapshot.estimatedWpmPrecise() <= 30.5d);
    }

    @Test
    public void hybridTimingDropsStableAnchorAfterLongIdleGap() {
        CwHybridTimingModel model = new CwHybridTimingModel();
        model.setSeedWpm(24);

        model.process(toneOff(50L, 50L));
        model.notifyStableDecode(50L);
        model.process(toneOn(100L));
        model.process(toneOff(150L, 50L));
        model.notifyStableDecode(150L);
        model.process(toneOn(200L));
        model.process(toneOff(250L, 50L));
        model.notifyStableDecode(250L);

        model.flushPendingGap(14000L);
        model.process(toneOn(14040L));
        model.process(toneOff(14052L, 12L));

        CwTimingSnapshot snapshot = model.snapshot();
        assertTrue("wpm=" + snapshot.estimatedWpmPrecise(), snapshot.estimatedWpmPrecise() >= 18.0d);
    }

    @Test
    public void hybridTimingLongIdleResetClearsRunawayFastStateBackTowardSeed() {
        CwHybridTimingModel model = new CwHybridTimingModel();
        model.setSeedWpm(24);

        model.process(toneOff(50L, 50L));
        model.notifyStableDecode(50L);
        model.process(toneOn(100L));
        model.process(toneOff(150L, 50L));
        model.notifyStableDecode(150L);
        model.process(toneOn(200L));
        model.process(toneOff(250L, 50L));
        model.notifyStableDecode(250L);

        for (int index = 0; index < 12; index++) {
            long toneOnTimestampMs = 300L + (index * 30L);
            model.process(toneOn(toneOnTimestampMs));
            model.process(toneOff(toneOnTimestampMs + 10L, 10L));
        }

        CwTimingSnapshot fastSnapshot = model.snapshot();
        assertTrue("fast wpm=" + fastSnapshot.estimatedWpmPrecise(), fastSnapshot.estimatedWpmPrecise() <= 26.5d);

        model.flushPendingGap(14000L);

        CwTimingSnapshot idleSnapshot = model.snapshot();
        assertTrue("idle wpm=" + idleSnapshot.estimatedWpmPrecise(), idleSnapshot.estimatedWpmPrecise() <= 24.5d);
        assertTrue("idle wpm=" + idleSnapshot.estimatedWpmPrecise(), idleSnapshot.estimatedWpmPrecise() >= 20.5d);
    }

    @Test
    public void hybridTimingTurnResetClearsInternalCarryAndUsesTurnSeedOnly() {
        CwHybridTimingModel model = new CwHybridTimingModel();
        model.setSeedWpm(24);

        model.process(toneOff(50L, 50L));
        model.notifyStableDecode(50L);
        model.process(toneOn(100L));
        model.process(toneOff(150L, 50L));
        model.notifyStableDecode(150L);
        model.process(toneOn(200L));
        model.process(toneOff(250L, 50L));
        model.notifyStableDecode(250L);
        assertTrue(model.debugSnapshot().trustedDotEstimateMs() > 0.0d);
        assertTrue(model.debugSnapshot().retainedDotEstimateMs() > 0.0d);

        model.beginNewTurn(18, 2000L);

        CwHybridTimingModel.DebugSnapshot debug = model.debugSnapshot();
        assertEquals(18, debug.seedWpm());
        assertEquals(0.0d, debug.trustedDotEstimateMs(), 0.001d);
        assertEquals(0.0d, debug.retainedDotEstimateMs(), 0.001d);
        assertEquals("turn-reset", debug.lastResetReason());
        CwTimingSnapshot snapshot = model.snapshot();
        assertTrue("wpm=" + snapshot.estimatedWpmPrecise(), snapshot.estimatedWpmPrecise() >= 17.0d);
        assertTrue("wpm=" + snapshot.estimatedWpmPrecise(), snapshot.estimatedWpmPrecise() <= 18.5d);
    }

    @Test
    public void hybridTimingInitialTrustedAnchorRequiresClusteredStableDecodeRun() {
        CwHybridTimingModel model = new CwHybridTimingModel();
        model.setSeedWpm(24);

        model.process(toneOff(50L, 50L));
        model.notifyStableDecode(50L);
        assertEquals(0.0d, model.debugSnapshot().trustedDotEstimateMs(), 0.001d);

        model.process(toneOn(100L));
        model.process(toneOff(150L, 50L));
        model.notifyStableDecode(150L);
        assertEquals(0.0d, model.debugSnapshot().trustedDotEstimateMs(), 0.001d);

        model.process(toneOn(200L));
        model.process(toneOff(250L, 50L));
        model.notifyStableDecode(250L);

        assertTrue(model.debugSnapshot().trustedDotEstimateMs() > 0.0d);
    }

    @Test
    public void hybridTimingBoundaryBootstrapInitializesTrustedAnchorAfterTwoClusteredObservations() {
        CwHybridTimingModel model = new CwHybridTimingModel();
        model.setSeedWpm(24);

        model.process(toneOff(50L, 50L));
        model.noteBootstrapBoundaryObservation(50L);
        assertEquals(0.0d, model.debugSnapshot().trustedDotEstimateMs(), 0.001d);

        model.process(toneOn(100L));
        model.process(toneOff(150L, 50L));
        model.noteBootstrapBoundaryObservation(150L);

        assertTrue(model.debugSnapshot().trustedDotEstimateMs() > 0.0d);
        assertTrue(model.debugSnapshot().lastTrustedUpdateReason().startsWith("init-boundary("));
    }

    @Test
    public void hybridTimingBoundaryBootstrapCanUseGapDerivedDotCandidatesDirectly() {
        CwHybridTimingModel model = new CwHybridTimingModel();
        model.setSeedWpm(15);

        model.noteBootstrapBoundaryObservation(47L, 200L);
        assertEquals(0.0d, model.debugSnapshot().trustedDotEstimateMs(), 0.001d);

        model.noteBootstrapBoundaryObservation(44L, 420L);

        assertTrue(model.debugSnapshot().trustedDotEstimateMs() > 0.0d);
        assertTrue(model.debugSnapshot().lastTrustedUpdateReason().startsWith("init-boundary("));
    }

    @Test
    public void hybridTimingBoundaryBootstrapToleratesMicGapCandidateJitterWithinGapDerivedBand() {
        CwHybridTimingModel model = new CwHybridTimingModel();
        model.setSeedWpm(15);

        model.noteBootstrapBoundaryObservation(44L, 200L);
        assertEquals(0.0d, model.debugSnapshot().trustedDotEstimateMs(), 0.001d);

        model.noteBootstrapBoundaryObservation(53L, 420L);

        assertTrue(model.debugSnapshot().trustedDotEstimateMs() > 0.0d);
        assertTrue(model.debugSnapshot().lastTrustedUpdateReason().startsWith("init-boundary("));
    }

    @Test
    public void hybridTimingBoundaryAndStableBootstrapCanInitializeTrustedAnchorTogetherForMidSpeedOpening() throws Exception {
        CwHybridTimingModel model = new CwHybridTimingModel();
        model.setSeedWpm(15);

        model.noteBootstrapBoundaryObservation(50L, 200L);
        assertEquals(0.0d, model.debugSnapshot().trustedDotEstimateMs(), 0.001d);

        setHybridBaselineDotEstimate(model, 52.0d, 1);
        setHybridAdaptiveDotEstimate(model, 52.0d, 1);
        model.notifyStableDecode(320L);

        assertTrue(model.debugSnapshot().trustedDotEstimateMs() > 0.0d);
        assertTrue(model.debugSnapshot().lastTrustedUpdateReason().startsWith("init-boundary-stable("));
    }

    @Test
    public void hybridTimingBoundaryAndStableBootstrapDoesNotTriggerForFastOpeningCandidate() throws Exception {
        CwHybridTimingModel model = new CwHybridTimingModel();
        model.setSeedWpm(15);

        model.noteBootstrapBoundaryObservation(45L, 200L);
        assertEquals(0.0d, model.debugSnapshot().trustedDotEstimateMs(), 0.001d);

        setHybridBaselineDotEstimate(model, 47.0d, 1);
        setHybridAdaptiveDotEstimate(model, 47.0d, 1);
        model.notifyStableDecode(320L);

        assertEquals(0.0d, model.debugSnapshot().trustedDotEstimateMs(), 0.001d);
    }

    @Test
    public void hybridTimingBoundaryAndSoftStableBootstrapCanInitializeTrustedAnchorTogether() {
        CwHybridTimingModel model = new CwHybridTimingModel();
        model.setSeedWpm(15);

        model.noteBootstrapBoundaryObservation(68L, 200L);
        assertEquals(0.0d, model.debugSnapshot().trustedDotEstimateMs(), 0.001d);

        model.noteBootstrapSoftStableObservation(58L, 320L);

        assertTrue(model.debugSnapshot().trustedDotEstimateMs() >= 67.0d);
        assertTrue(model.debugSnapshot().trustedDotEstimateMs() <= 69.0d);
        assertTrue(model.debugSnapshot().lastTrustedUpdateReason().startsWith("init-boundary-soft-stable("));
    }

    @Test
    public void hybridTimingSoftStableBootstrapCandidateAloneDoesNotInitializeTrustedAnchor() {
        CwHybridTimingModel model = new CwHybridTimingModel();
        model.setSeedWpm(15);

        model.noteBootstrapSoftStableObservation(58L, 200L);
        model.noteBootstrapSoftStableObservation(57L, 320L);
        model.noteBootstrapSoftStableObservation(59L, 440L);

        assertEquals(0.0d, model.debugSnapshot().trustedDotEstimateMs(), 0.001d);
    }

    @Test
    public void hybridTimingCadenceBootstrapInitializesTrustedAnchorAfterThreeClusteredCandidates() {
        CwHybridTimingModel model = new CwHybridTimingModel();
        model.setSeedWpm(15);

        model.noteBootstrapCadenceObservation(55L, 200L);
        assertEquals(0.0d, model.debugSnapshot().trustedDotEstimateMs(), 0.001d);

        model.noteBootstrapCadenceObservation(54L, 420L);
        assertEquals(0.0d, model.debugSnapshot().trustedDotEstimateMs(), 0.001d);

        model.noteBootstrapCadenceObservation(56L, 640L);

        assertTrue(model.debugSnapshot().trustedDotEstimateMs() > 0.0d);
        assertTrue(model.debugSnapshot().lastTrustedUpdateReason().startsWith("init-cadence("));
    }

    @Test
    public void hybridTimingBoundaryCandidatesDoNotPolluteCadenceBootstrapCluster() {
        CwHybridTimingModel model = new CwHybridTimingModel();
        model.setSeedWpm(15);

        model.noteBootstrapBoundaryObservation(62L, 180L);
        model.noteBootstrapCadenceObservation(55L, 260L);
        model.noteBootstrapCadenceObservation(54L, 420L);
        model.noteBootstrapCadenceObservation(56L, 580L);

        assertTrue(model.debugSnapshot().trustedDotEstimateMs() > 0.0d);
        assertTrue(model.debugSnapshot().lastTrustedUpdateReason().startsWith("init-cadence("));
    }

    @Test
    public void hybridTimingCadenceOpeningTrustedFloorWaitsUntilStableDecode() throws Exception {
        CwHybridTimingModel model = new CwHybridTimingModel();
        model.setSeedWpm(15);
        model.noteBootstrapCadenceObservation(55L, 200L);
        model.noteBootstrapCadenceObservation(54L, 420L);
        model.noteBootstrapCadenceObservation(56L, 640L);

        CwTimingEvent rawTone = new CwTimingEvent(
                CwTimingEvent.Kind.TONE,
                CwTimingEvent.Classification.DIT,
                700L,
                45L,
                45L,
                45L
        );
        CwTimingSnapshot rawSnapshot = new CwTimingSnapshot(
                45L,
                135L,
                45L,
                27,
                26.67d,
                4,
                3,
                rawTone
        );

        List<CwTimingEvent> openingEvents = invokeStabilizeOutputEvents(
                model,
                Collections.singletonList(rawTone),
                rawSnapshot,
                700L
        );
        CwTimingSnapshot openingSnapshot = invokeStabilizedSnapshot(model, rawSnapshot, 700L);

        assertEquals(1, openingEvents.size());
        assertEquals(45L, openingEvents.get(0).dotEstimateMs());
        assertEquals(CwTimingEvent.Classification.DIT, openingEvents.get(0).classification());
        assertEquals(45L, openingSnapshot.dotEstimateMs());

        setHybridBaselineDotEstimate(model, 45.0d, 4);
        setHybridAdaptiveDotEstimate(model, 45.0d, 4);
        model.notifyStableDecode(820L);

        List<CwTimingEvent> postStableEvents = invokeStabilizeOutputEvents(
                model,
                Collections.singletonList(rawTone),
                rawSnapshot,
                860L
        );
        CwTimingSnapshot postStableSnapshot = invokeStabilizedSnapshot(model, rawSnapshot, 860L);

        assertEquals(1, postStableEvents.size());
        assertEquals(51L, postStableEvents.get(0).dotEstimateMs());
        assertEquals(51L, postStableSnapshot.dotEstimateMs());
    }

    @Test
    public void hybridTimingPreTrustBootstrapCanOverrideSlowEmissionLaneUsingEarlyEvidence() throws Exception {
        CwHybridTimingModel model = new CwHybridTimingModel();
        model.beginNewTurn(24, 100L);
        model.noteBootstrapBoundaryObservation(50L, 140L);

        setHybridBaselineDotEstimate(model, 80.0d, 1);
        setHybridAdaptiveDotEstimate(model, 50.0d, 1);

        CwTimingSnapshot snapshot = model.snapshot();

        assertTrue("dot=" + snapshot.dotEstimateMs(), snapshot.dotEstimateMs() <= 52L);
        assertTrue("wpm=" + snapshot.estimatedWpmPrecise(), snapshot.estimatedWpmPrecise() >= 22.5d);
    }

    @Test
    public void hybridTimingPreTrustBootstrapDoesNotLetOpeningCarrierOutlierDragSeedDotSlow() {
        CwHybridTimingModel model = new CwHybridTimingModel();
        model.beginNewTurn(15, 1L);

        List<CwTimingEvent> firstToneEvents = model.process(toneOff(464L, 464L));
        assertEquals(1, firstToneEvents.size());
        assertEquals(CwTimingEvent.Classification.UNKNOWN, firstToneEvents.get(0).classification());
        assertTrue("dot=" + firstToneEvents.get(0).dotEstimateMs(), firstToneEvents.get(0).dotEstimateMs() <= 82L);

        List<CwTimingEvent> firstGapEvents = model.process(toneOn(508L));
        assertEquals(1, firstGapEvents.size());
        assertEquals(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, firstGapEvents.get(0).classification());
        assertTrue("gapDot=" + firstGapEvents.get(0).dotEstimateMs(), firstGapEvents.get(0).dotEstimateMs() <= 82L);

        List<CwTimingEvent> secondToneEvents = model.process(toneOff(592L, 84L));
        assertEquals(1, secondToneEvents.size());
        assertEquals(CwTimingEvent.Classification.DIT, secondToneEvents.get(0).classification());
        assertTrue("toneDot=" + secondToneEvents.get(0).dotEstimateMs(), secondToneEvents.get(0).dotEstimateMs() <= 82L);
    }

    @Test
    public void hybridTimingInitialTrustedAnchorExpiresSeparatedBootstrapRun() {
        CwHybridTimingModel model = new CwHybridTimingModel();
        model.setSeedWpm(24);

        model.process(toneOff(50L, 50L));
        model.notifyStableDecode(50L);
        model.process(toneOn(100L));
        model.process(toneOff(150L, 50L));
        model.notifyStableDecode(150L);
        model.process(toneOn(4000L));
        model.process(toneOff(4050L, 50L));
        model.notifyStableDecode(4050L);

        assertEquals(0.0d, model.debugSnapshot().trustedDotEstimateMs(), 0.001d);
    }

    @Test
    public void hybridTimingFrozenLearningDoesNotLetFastFragmentsRunAway() {
        CwHybridTimingModel model = new CwHybridTimingModel();
        model.setSeedWpm(24);

        model.process(toneOff(50L, 50L));
        model.notifyStableDecode(50L);
        model.process(toneOn(100L));
        model.process(toneOff(150L, 50L));
        model.notifyStableDecode(150L);
        model.process(toneOn(200L));
        model.process(toneOff(250L, 50L));
        model.notifyStableDecode(250L);

        for (int index = 0; index < 12; index++) {
            long toneOnTimestampMs = 300L + (index * 30L);
            model.process(toneOn(toneOnTimestampMs), false);
            model.process(toneOff(toneOnTimestampMs + 10L, 10L), false);
        }

        CwTimingSnapshot snapshot = model.rawSnapshot();
        assertTrue("wpm=" + snapshot.estimatedWpmPrecise(), snapshot.estimatedWpmPrecise() <= 25.0d);
    }

    @Test
    public void hybridTimingFastReanchorUsesRoundedFloorSoIntegerCandidateAtBoundaryCanAccumulate() throws Exception {
        CwHybridTimingModel model = new CwHybridTimingModel();
        model.setSeedWpm(20);
        setDoubleField(CwHybridTimingModel.class, model, "trustedDotEstimateMs", 83.0d);
        setDoubleField(CwHybridTimingModel.class, model, "retainedDotEstimateMs", 83.0d);
        setHybridBaselineDotEstimate(model, 76.0d, 4);
        setHybridAdaptiveDotEstimate(model, 76.0d, 4);

        for (int index = 0; index < 6; index++) {
            model.notifyStableDecode(500L + (index * 40L));
        }

        CwHybridTimingModel.DebugSnapshot debugSnapshot = model.debugSnapshot();
        assertTrue("trusted=" + debugSnapshot.trustedDotEstimateMs(), debugSnapshot.trustedDotEstimateMs() < 83.0d);
        assertTrue("reason=" + debugSnapshot.lastTrustedUpdateReason(),
                debugSnapshot.lastTrustedUpdateReason().startsWith("reanchor("));
    }

    @Test
    public void hybridTimingTrustedFloorDoesNotDowngradeNearDashRawTone() throws Exception {
        CwHybridTimingModel model = new CwHybridTimingModel();
        setDoubleField(CwHybridTimingModel.class, model, "trustedDotEstimateMs", 89.0d);

        CwTimingEvent rawTone = new CwTimingEvent(
                CwTimingEvent.Kind.TONE,
                CwTimingEvent.Classification.DAH,
                1000L,
                139L,
                76L,
                76L
        );
        CwTimingSnapshot rawSnapshot = new CwTimingSnapshot(
                76L,
                228L,
                76L,
                16,
                15.79d,
                4,
                3,
                rawTone
        );

        List<CwTimingEvent> stabilizedEvents = invokeStabilizeOutputEvents(
                model,
                Collections.singletonList(rawTone),
                rawSnapshot,
                1000L
        );
        CwTimingSnapshot stabilizedSnapshot = invokeStabilizedSnapshot(model, rawSnapshot, 1000L);

        assertEquals(1, stabilizedEvents.size());
        assertEquals(CwTimingEvent.Classification.DAH, stabilizedEvents.get(0).classification());
        assertEquals(82L, stabilizedEvents.get(0).dotEstimateMs());
        assertEquals(CwTimingEvent.Classification.DAH, stabilizedSnapshot.lastTimingEvent().classification());
        assertEquals(82L, stabilizedSnapshot.dotEstimateMs());
    }

    @Test
    public void hybridTimingTrustedFloorStillRejectsClearlyShortRawDashCandidate() throws Exception {
        CwHybridTimingModel model = new CwHybridTimingModel();
        setDoubleField(CwHybridTimingModel.class, model, "trustedDotEstimateMs", 89.0d);

        CwTimingEvent rawTone = new CwTimingEvent(
                CwTimingEvent.Kind.TONE,
                CwTimingEvent.Classification.DAH,
                1000L,
                124L,
                68L,
                68L
        );
        CwTimingSnapshot rawSnapshot = new CwTimingSnapshot(
                68L,
                204L,
                68L,
                4,
                17.65d,
                4,
                3,
                rawTone
        );

        List<CwTimingEvent> stabilizedEvents = invokeStabilizeOutputEvents(
                model,
                Collections.singletonList(rawTone),
                rawSnapshot,
                1000L
        );

        assertEquals(1, stabilizedEvents.size());
        assertEquals(CwTimingEvent.Classification.DIT, stabilizedEvents.get(0).classification());
        assertEquals(82L, stabilizedEvents.get(0).dotEstimateMs());
    }

    @Test
    public void hybridTimingTrustedFloorDoesNotCollapseNearLetterGapIntoIntraSymbolGap() throws Exception {
        CwHybridTimingModel model = new CwHybridTimingModel();
        setDoubleField(CwHybridTimingModel.class, model, "trustedDotEstimateMs", 89.0d);

        CwTimingEvent rawGap = new CwTimingEvent(
                CwTimingEvent.Kind.GAP,
                CwTimingEvent.Classification.LETTER_GAP,
                1000L,
                139L,
                76L,
                76L
        );
        CwTimingSnapshot rawSnapshot = new CwTimingSnapshot(
                76L,
                228L,
                76L,
                16,
                15.79d,
                4,
                3,
                rawGap
        );

        List<CwTimingEvent> stabilizedEvents = invokeStabilizeOutputEvents(
                model,
                Collections.singletonList(rawGap),
                rawSnapshot,
                1000L
        );

        assertEquals(1, stabilizedEvents.size());
        assertEquals(CwTimingEvent.Classification.LETTER_GAP, stabilizedEvents.get(0).classification());
        assertEquals(82L, stabilizedEvents.get(0).dotEstimateMs());
    }

    @Test
    public void hybridTimingFastBorderlineGapStillClassifiesAsIntraSymbolGap() throws Exception {
        CwHybridTimingModel model = new CwHybridTimingModel();

        CwTimingEvent rawGap = new CwTimingEvent(
                CwTimingEvent.Kind.GAP,
                CwTimingEvent.Classification.LETTER_GAP,
                1000L,
                96L,
                54L,
                54L
        );
        CwTimingSnapshot rawSnapshot = new CwTimingSnapshot(
                54L,
                162L,
                54L,
                22,
                22.22d,
                8,
                8,
                rawGap
        );

        List<CwTimingEvent> stabilizedEvents = invokeStabilizeOutputEvents(
                model,
                Collections.singletonList(rawGap),
                rawSnapshot,
                1000L
        );

        assertEquals(1, stabilizedEvents.size());
        assertEquals(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, stabilizedEvents.get(0).classification());
        assertEquals(54L, stabilizedEvents.get(0).dotEstimateMs());
    }

    private void setBooleanField(CwTimingModel model, String name, boolean value) throws Exception {
        Field field = CwTimingModel.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(model, value);
    }

    private void setLongField(CwTimingModel model, String name, long value) throws Exception {
        Field field = CwTimingModel.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setLong(model, value);
    }

    private void setDoubleField(CwTimingModel model, String name, double value) throws Exception {
        Field field = CwTimingModel.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setDouble(model, value);
    }

    private void setHybridBaselineDotEstimate(
            CwHybridTimingModel model,
            double dotEstimateMs,
            int totalToneEvents
    ) throws Exception {
        Object baseline = getFieldValue(CwHybridTimingModel.class, model, "baseline");
        setBooleanField(CwTimingModel.class, baseline, "initialized", true);
        setDoubleField(CwTimingModel.class, baseline, "dotEstimateMs", dotEstimateMs);
        setDoubleField(CwTimingModel.class, baseline, "dashEstimateMs", dotEstimateMs * 3.0d);
        setDoubleField(CwTimingModel.class, baseline, "intraGapEstimateMs", dotEstimateMs);
        setDoubleField(CwTimingModel.class, baseline, "toneDotReferenceMs", dotEstimateMs);
        setIntField(CwTimingModel.class, baseline, "totalToneEvents", totalToneEvents);
    }

    private void setHybridAdaptiveDotEstimate(
            CwHybridTimingModel model,
            double dotEstimateMs,
            int totalToneEvents
    ) throws Exception {
        Object adaptive = getFieldValue(CwHybridTimingModel.class, model, "adaptive");
        setBooleanField(CwAdaptiveTimingModel.class, adaptive, "initialized", true);
        setDoubleField(CwAdaptiveTimingModel.class, adaptive, "dotEstimateMs", dotEstimateMs);
        setDoubleField(CwAdaptiveTimingModel.class, adaptive, "dashEstimateMs", dotEstimateMs * 3.0d);
        setDoubleField(CwAdaptiveTimingModel.class, adaptive, "intraGapEstimateMs", dotEstimateMs);
        setIntField(CwAdaptiveTimingModel.class, adaptive, "totalToneEvents", totalToneEvents);
    }

    private Object getFieldValue(Class<?> owner, Object target, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private void setBooleanField(Class<?> owner, Object target, String name, boolean value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private void setDoubleField(Class<?> owner, Object target, String name, double value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.setDouble(target, value);
    }

    private void setIntField(Class<?> owner, Object target, String name, int value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    @SuppressWarnings("unchecked")
    private List<CwTimingEvent> invokeStabilizeOutputEvents(
            CwHybridTimingModel model,
            List<CwTimingEvent> outputEvents,
            CwTimingSnapshot rawSnapshot,
            long timestampMs
    ) throws Exception {
        Method method = CwHybridTimingModel.class.getDeclaredMethod(
                "stabilizeOutputEvents",
                List.class,
                CwTimingSnapshot.class,
                long.class
        );
        method.setAccessible(true);
        return (List<CwTimingEvent>) method.invoke(model, outputEvents, rawSnapshot, timestampMs);
    }

    private CwTimingSnapshot invokeStabilizedSnapshot(
            CwHybridTimingModel model,
            CwTimingSnapshot rawSnapshot,
            long timestampMs
    ) throws Exception {
        Method method = CwHybridTimingModel.class.getDeclaredMethod(
                "stabilizedSnapshot",
                CwTimingSnapshot.class,
                long.class
        );
        method.setAccessible(true);
        return (CwTimingSnapshot) method.invoke(model, rawSnapshot, timestampMs);
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
}
