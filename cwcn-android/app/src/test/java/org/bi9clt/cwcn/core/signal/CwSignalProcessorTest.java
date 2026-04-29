package org.bi9clt.cwcn.core.signal;

import org.bi9clt.cwcn.core.audio.AudioFrame;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class CwSignalProcessorTest {
    private static final int SAMPLE_RATE_HZ = 16000;
    private static final int FRAME_SIZE = 256;
    private static final int[] FRONT_END_MATRIX_TONES_HZ = new int[]{600, 650, 700, 750, 800};

    @Test
    public void locksNearPreferredToneFrequencyWhenTargetToneAppears() {
        CwSignalProcessor processor = new CwSignalProcessor();

        processFrames(processor, 8, 0.0d, 0.0d);
        processFrames(processor, 12, 670.0d, 18000.0d);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue(snapshot.targetToneLocked());
        assertTrue(snapshot.toneActive());
        assertTrue(Math.abs(snapshot.targetToneFrequencyHz() - 670) <= 20);
        assertTrue(snapshot.lastToneRmsAmplitude() > 5000.0d);
        assertTrue(snapshot.totalToneOnEvents() >= 1);
    }

    @Test
    public void unlockedAcquisitionCanFindCleanToneFarAbovePreferredFrequency() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(600);

        processFrames(processor, 8, 0.0d, 0.0d);
        processFrames(processor, 12, 790.0d, 17000.0d);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue(snapshot.targetToneLocked());
        assertTrue(snapshot.toneActive());
        assertTrue("target=" + snapshot.targetToneFrequencyHz(), Math.abs(snapshot.targetToneFrequencyHz() - 790) <= 20);
        assertTrue(snapshot.toneDominanceRatio() >= 0.24d);
    }

    @Test
    public void unlockedAcquisitionCanFindCleanToneFarBelowPreferredFrequency() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(760);

        processFrames(processor, 8, 0.0d, 0.0d);
        processFrames(processor, 12, 520.0d, 17000.0d);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue(snapshot.targetToneLocked());
        assertTrue(snapshot.toneActive());
        assertTrue("target=" + snapshot.targetToneFrequencyHz(), Math.abs(snapshot.targetToneFrequencyHz() - 520) <= 20);
        assertTrue(snapshot.toneDominanceRatio() >= 0.24d);
    }

    @Test
    public void toneHypothesisConvergesTowardTruePeakWithoutChangingMainTrackingContract() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(450);

        processFrames(processor, 8, 0.0d, 0.0d);
        processFrames(processor, 14, 700.0d, 17000.0d);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue(snapshot.targetToneLocked());
        assertTrue(Math.abs(snapshot.targetToneFrequencyHz() - 700) <= 20);
        assertTrue("hyp=" + snapshot.toneHypothesisFrequencyHz()
                        + " conf=" + snapshot.toneHypothesisConfidence()
                        + " src=" + snapshot.toneHypothesisSource(),
                Math.abs(snapshot.toneHypothesisFrequencyHz() - 700) <= 20);
        assertTrue("conf=" + snapshot.toneHypothesisConfidence(), snapshot.toneHypothesisConfidence() >= 0.30d);
        assertTrue(snapshot.toneHypothesisSupportFrames() > 0);
    }

    @Test
    public void toneHypothesisDecaysAwayAfterExtendedSilence() {
        CwSignalProcessor processor = new CwSignalProcessor();

        processFrames(processor, 8, 0.0d, 0.0d);
        processFrames(processor, 14, 700.0d, 17000.0d);
        CwSignalSnapshot activeSnapshot = processor.snapshot();
        assertTrue(activeSnapshot.toneHypothesisSupportFrames() > 0);

        processFrames(processor, 40, 0.0d, 0.0d);

        CwSignalSnapshot idleSnapshot = processor.snapshot();
        assertTrue(idleSnapshot.toneHypothesisIdleFrames() > 0);
        assertTrue("src=" + idleSnapshot.toneHypothesisSource(),
                "IDLE".equals(idleSnapshot.toneHypothesisSource())
                        || "NONE".equals(idleSnapshot.toneHypothesisSource()));
        assertTrue(idleSnapshot.toneHypothesisSupportFrames() <= activeSnapshot.toneHypothesisSupportFrames());
    }

    @Test
    public void hypothesisGuardPrototypeCanArmUnderStableSinglePeakDisagreement() {
        CwSignalSnapshot disabledSnapshot = runHypothesisGuardDriftSequence(false);
        CwSignalSnapshot enabledSnapshot = runHypothesisGuardDriftSequence(true);

        String summary = "disabled target=" + disabledSnapshot.targetToneFrequencyHz()
                + " final=" + disabledSnapshot.finalAdoptedFrequencyHz()
                + " source=" + disabledSnapshot.finalAdoptedSource()
                + " decision=" + disabledSnapshot.hypothesisGuardDecision()
                + "\nenabled target=" + enabledSnapshot.targetToneFrequencyHz()
                + " final=" + enabledSnapshot.finalAdoptedFrequencyHz()
                + " source=" + enabledSnapshot.finalAdoptedSource()
                + " decision=" + enabledSnapshot.hypothesisGuardDecision()
                + " applyCount=" + enabledSnapshot.hypothesisGuardApplyCount()
                + " acqDetail=" + enabledSnapshot.acquisitionDecisionDetail()
                + " hyp=" + enabledSnapshot.toneHypothesisFrequencyHz();

        assertTrue(summary, enabledSnapshot.hypothesisGuardExperimentEnabled());
        assertTrue(summary, enabledSnapshot.hypothesisGuardEligible());
        assertEquals(summary, 0, enabledSnapshot.hypothesisGuardApplyCount());
        assertEquals(summary, "ELIGIBLE:OBSERVE_ONLY", enabledSnapshot.hypothesisGuardDecision());
        assertTrue(summary, Math.abs(enabledSnapshot.toneHypothesisFrequencyHz() - 600) <= 20);
        assertTrue(summary, enabledSnapshot.representativeCompetitionHypothesisWinFrames() > 0);
        assertTrue(summary, enabledSnapshot.representativeCompetitionHypothesisMaxWinStreak() > 0);
    }

    @Test
    public void probeHypothesisGuardPrototypeDriftSequence() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(450);
        processor.setExperimentalHypothesisGuardEnabled(true);

        processFramesCollecting(processor, 8, 0.0d, 0.0d, 0);
        processFramesCollecting(processor, 12, 600.0d, 17000.0d, 8);
        for (int frameIndex = 20; frameIndex <= 25; frameIndex++) {
            processFramesCollecting(processor, 1, 600.0d, 3200.0d, 540.0d, 18000.0d, frameIndex);
            CwSignalSnapshot snapshot = processor.snapshot();
            System.out.println(
                    "frame=" + frameIndex
                            + " target=" + snapshot.targetToneFrequencyHz()
                            + " final=" + snapshot.finalAdoptedFrequencyHz()
                            + " finalSource=" + snapshot.finalAdoptedSource()
                            + " acq=" + snapshot.acquisitionWinnerFrequencyHz()
                            + " acqSource=" + snapshot.acquisitionWinnerSource()
                            + " acqDetail=" + snapshot.acquisitionDecisionDetail()
                            + " decision=" + snapshot.hypothesisGuardDecision()
                            + " applyCount=" + snapshot.hypothesisGuardApplyCount()
                            + " eligible=" + snapshot.hypothesisGuardEligible()
                            + " applied=" + snapshot.hypothesisGuardApplied()
                            + " hyp=" + snapshot.toneHypothesisFrequencyHz()
                            + " hypConf=" + snapshot.toneHypothesisConfidence()
                            + " hypSupport=" + snapshot.toneHypothesisSupportFrames()
                            + " rep=" + snapshot.representativeLockedToneFrequencyHz()
                            + " repFrames=" + snapshot.representativeLockedToneFrameCount()
            );
        }
    }

    @Test
    public void probeHypothesisGuardPrototypeAmplitudeSweep() {
        double[] protectedAmplitudes = new double[]{3200.0d, 4800.0d, 6400.0d, 8000.0d, 9600.0d, 12000.0d};
        for (double protectedAmplitude : protectedAmplitudes) {
            CwSignalProcessor processor = new CwSignalProcessor();
            processor.setPreferredToneFrequencyHz(450);
            processor.setExperimentalHypothesisGuardEnabled(true);

            processFramesCollecting(processor, 8, 0.0d, 0.0d, 0);
            processFramesCollecting(processor, 12, 600.0d, 17000.0d, 8);
            for (int frameIndex = 20; frameIndex <= 25; frameIndex++) {
                processFramesCollecting(processor, 1, 600.0d, protectedAmplitude, 540.0d, 18000.0d, frameIndex);
            }

            CwSignalSnapshot snapshot = processor.snapshot();
            System.out.println(
                    "protectedAmp=" + protectedAmplitude
                            + " target=" + snapshot.targetToneFrequencyHz()
                            + " final=" + snapshot.finalAdoptedFrequencyHz()
                            + " finalSource=" + snapshot.finalAdoptedSource()
                            + " acq=" + snapshot.acquisitionWinnerFrequencyHz()
                            + " acqSource=" + snapshot.acquisitionWinnerSource()
                            + " acqDetail=" + snapshot.acquisitionDecisionDetail()
                            + " decision=" + snapshot.hypothesisGuardDecision()
                            + " applyCount=" + snapshot.hypothesisGuardApplyCount()
                            + " hyp=" + snapshot.toneHypothesisFrequencyHz()
                            + " hypConf=" + snapshot.toneHypothesisConfidence()
            );
        }
    }

    @Test
    public void frontEndToneMatrixLocksAcrossCommonCwToneRange() {
        for (int toneHz : FRONT_END_MATRIX_TONES_HZ) {
            CwSignalProcessor processor = new CwSignalProcessor();
            processor.setPreferredToneFrequencyHz(650);

            processFrames(processor, 8, 0.0d, 0.0d);
            processFrames(processor, 12, toneHz, 17000.0d);

            CwSignalSnapshot snapshot = processor.snapshot();
            String debug = "tone=" + toneHz
                    + " target=" + snapshot.targetToneFrequencyHz()
                    + " final=" + snapshot.finalAdoptedFrequencyHz()
                    + " locked=" + snapshot.targetToneLocked();
            assertTrue(debug, snapshot.toneActive());
            assertTrue(debug, snapshot.targetToneLocked());
            assertTrue(debug, Math.abs(snapshot.targetToneFrequencyHz() - toneHz) <= 20);
            assertTrue(debug, Math.abs(snapshot.finalAdoptedFrequencyHz() - toneHz) <= 20);
        }
    }

    @Test
    public void noisyFrontEndToneMatrixLocksAcrossCommonCwToneRange() {
        for (int toneHz : FRONT_END_MATRIX_TONES_HZ) {
            CwSignalProcessor processor = new CwSignalProcessor();
            processor.setPreferredToneFrequencyHz(650);

            processNoisyFrames(processor, 8, 0.0d, 0.0d, 1800.0d, 0);
            processNoisyFrames(processor, 14, toneHz, 15000.0d, 4200.0d, 8);

            CwSignalSnapshot snapshot = processor.snapshot();
            String debug = "tone=" + toneHz
                    + " target=" + snapshot.targetToneFrequencyHz()
                    + " final=" + snapshot.finalAdoptedFrequencyHz()
                    + " pref=" + snapshot.preferredToneFrequencyHz()
                    + " winner=" + snapshot.acquisitionWinnerFrequencyHz();
            assertTrue(debug, snapshot.toneActive());
            assertTrue(debug, snapshot.targetToneLocked());
            assertTrue(debug, Math.abs(snapshot.targetToneFrequencyHz() - toneHz) <= 20);
            assertTrue(debug, Math.abs(snapshot.finalAdoptedFrequencyHz() - toneHz) <= 20);
        }
    }

    @Test
    public void strongerOutOfBandToneDoesNotPullTrackingAwayFromPreferredWindow() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(650);

        processFrames(processor, 8, 0.0d, 0.0d);
        processFrames(processor, 12, 670.0d, 11000.0d, 930.0d, 19000.0d);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue(snapshot.targetToneLocked());
        assertTrue("target=" + snapshot.targetToneFrequencyHz()
                        + " winner=" + snapshot.acquisitionWinnerFrequencyHz()
                        + " source=" + snapshot.acquisitionWinnerSource(),
                Math.abs(snapshot.targetToneFrequencyHz() - 670) <= 20);
        assertTrue(snapshot.toneDominanceRatio() >= 0.25d);
        assertEquals(650, snapshot.preferredToneFrequencyHz());
    }

    @Test
    public void preferredToneFrequencyIsClampedIntoSupportedWindow() {
        CwSignalProcessor processor = new CwSignalProcessor();

        processor.setPreferredToneFrequencyHz(1200);
        assertEquals(850, processor.snapshot().preferredToneFrequencyHz());

        processor.setPreferredToneFrequencyHz(200);
        assertEquals(450, processor.snapshot().preferredToneFrequencyHz());
    }

    @Test
    public void changingPreferredToneAfterProcessingStartsDoesNotRewriteCurrentTarget() {
        CwSignalProcessor processor = new CwSignalProcessor();

        processFrames(processor, 8, 0.0d, 0.0d);
        processFrames(processor, 12, 700.0d, 17000.0d);
        int targetBeforePreferenceChange = processor.snapshot().targetToneFrequencyHz();

        processor.setPreferredToneFrequencyHz(500);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue(snapshot.targetToneLocked());
        assertTrue(Math.abs(targetBeforePreferenceChange - 700) <= 20);
        assertTrue(Math.abs(snapshot.targetToneFrequencyHz() - targetBeforePreferenceChange) <= 10);
        assertEquals(500, snapshot.preferredToneFrequencyHz());
    }

    @Test
    public void materiallyStrongerInWindowToneCanBeatCloserPreferredCandidateDuringAcquisition() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(650);

        processFrames(processor, 8, 0.0d, 0.0d);
        processFrames(processor, 12, 670.0d, 15000.0d, 800.0d, 21000.0d);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue(snapshot.targetToneLocked());
        assertTrue("target=" + snapshot.targetToneFrequencyHz()
                        + " winner=" + snapshot.acquisitionWinnerFrequencyHz()
                        + " source=" + snapshot.acquisitionWinnerSource(),
                Math.abs(snapshot.targetToneFrequencyHz() - 800) <= 20);
    }

    @Test
    public void strongerNearbyInWindowToneStillDoesNotBeatCloserPreferredCandidate() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(650);

        processFrames(processor, 8, 0.0d, 0.0d);
        processFrames(processor, 12, 670.0d, 15000.0d, 740.0d, 21000.0d);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue(snapshot.targetToneLocked());
        assertTrue(Math.abs(snapshot.targetToneFrequencyHz() - 670) <= 20);
    }

    @Test
    public void continuousOutOfBandCarrierDoesNotFalseTriggerToneActivity() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(650);

        processFrames(processor, 8, 0.0d, 0.0d);
        processFrames(processor, 16, 0.0d, 0.0d, 930.0d, 19000.0d);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue(!snapshot.toneActive());
        assertTrue(!snapshot.targetToneLocked());
        assertEquals(0, snapshot.totalToneOnEvents());
        assertTrue(snapshot.toneDominanceRatio() < 0.30d);
    }

    @Test
    public void moderateNearbyInterfererStillLetsTargetToneActivate() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(650);

        processFrames(processor, 8, 0.0d, 0.0d);
        processFrames(processor, 12, 670.0d, 15000.0d, 930.0d, 7000.0d);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue(snapshot.toneActive());
        assertTrue(Math.abs(snapshot.targetToneFrequencyHz() - 670) <= 20);
        assertTrue(snapshot.lastToneRmsAmplitude() > 4000.0d);
        assertTrue(snapshot.toneDominanceRatio() >= 0.15d);
    }

    @Test
    public void activeToneLockCanRideThroughTemporaryModerateInterfererPressure() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(650);

        processFrames(processor, 8, 0.0d, 0.0d);
        processFrames(processor, 2, 670.0d, 15000.0d, 930.0d, 7000.0d);
        processFramesCollecting(processor, 2, 670.0d, 2500.0d, 930.0d, 7000.0d, 10);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue(snapshot.toneActive());
        assertTrue(snapshot.targetToneLocked());
        assertTrue(Math.abs(snapshot.targetToneFrequencyHz() - 670) <= 20);
        assertTrue(snapshot.toneActiveUnlockedFrameRatio() <= 0.25d);
    }

    @Test
    public void lockedTargetResistsPeriodicRetuneTowardFartherNearbyCarrier() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(650);

        processFrames(processor, 8, 0.0d, 0.0d);
        processFrames(processor, 4, 670.0d, 15000.0d);
        processFrames(processor, 12, 670.0d, 15000.0d, 740.0d, 18000.0d);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue(snapshot.toneActive());
        assertTrue(snapshot.targetToneLocked());
        assertTrue(Math.abs(snapshot.targetToneFrequencyHz() - 670) <= 20);
    }

    @Test
    public void sweepingNearbyCarrierDoesNotImmediatelyHijackAcquisition() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(650);

        processFrames(processor, 8, 0.0d, 0.0d);
        for (int frameIndex = 0; frameIndex < 12; frameIndex++) {
            double sweepingFrequencyHz = 820.0d - (frameIndex * 10.0d);
            AudioFrame frame = buildFrame(
                    (8 + frameIndex) * frameDurationMs(),
                    (8 + frameIndex) * FRAME_SIZE,
                    670.0d,
                    15000.0d,
                    sweepingFrequencyHz,
                    18000.0d
            );
            processor.process(frame);
        }

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue(snapshot.toneActive());
        assertTrue(snapshot.targetToneLocked());
        assertTrue(Math.abs(snapshot.targetToneFrequencyHz() - 670) <= 20);
    }

    @Test
    public void broadbandNoiseOnlyDoesNotCreateStableTargetLock() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(650);

        processNoisyFrames(processor, 16, 0.0d, 0.0d, 12000.0d);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue(!snapshot.toneActive());
        assertTrue(!snapshot.targetToneLocked());
        assertEquals(0, snapshot.totalToneOnEvents());
        assertTrue(snapshot.narrowbandIsolationRatio() < 0.20d);
    }

    @Test
    public void targetToneCanStillActivateUnderDeterministicBroadbandNoise() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(650);

        processNoisyFrames(processor, 8, 0.0d, 0.0d, 1800.0d);
        processNoisyFrames(processor, 12, 670.0d, 15000.0d, 4200.0d, 8);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue(snapshot.toneActive());
        assertTrue(snapshot.targetToneLocked());
        assertTrue(Math.abs(snapshot.targetToneFrequencyHz() - 670) <= 20);
        assertTrue(snapshot.narrowbandIsolationRatio() >= 0.45d);
        assertTrue(snapshot.lastWidebandResidualRmsAmplitude() > 0.0d);
    }

    @Test
    public void offPreferredLowerToneCanStillLockUnderDeterministicBroadbandNoise() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(650);

        processNoisyFrames(processor, 8, 0.0d, 0.0d, 1800.0d);
        processNoisyFrames(processor, 14, 600.0d, 15000.0d, 4200.0d, 8);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue("target=" + snapshot.targetToneFrequencyHz(), snapshot.toneActive());
        assertTrue("target=" + snapshot.targetToneFrequencyHz(), snapshot.targetToneLocked());
        assertTrue("target=" + snapshot.targetToneFrequencyHz(), Math.abs(snapshot.targetToneFrequencyHz() - 600) <= 20);
    }

    @Test
    public void offPreferredHigherToneCanStillLockUnderDeterministicBroadbandNoise() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(650);

        processNoisyFrames(processor, 8, 0.0d, 0.0d, 1800.0d);
        processNoisyFrames(processor, 14, 800.0d, 15000.0d, 4200.0d, 8);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue("target=" + snapshot.targetToneFrequencyHz(), snapshot.toneActive());
        assertTrue("target=" + snapshot.targetToneFrequencyHz(), snapshot.targetToneLocked());
        assertTrue("target=" + snapshot.targetToneFrequencyHz(), Math.abs(snapshot.targetToneFrequencyHz() - 800) <= 20);
    }

    @Test
    public void cleanActual700HzBeatsFarLowerPreferred450Hz() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(450);

        processFrames(processor, 8, 0.0d, 0.0d);
        processFrames(processor, 12, 700.0d, 17000.0d);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue("target=" + snapshot.targetToneFrequencyHz(), snapshot.toneActive());
        assertTrue("target=" + snapshot.targetToneFrequencyHz(), snapshot.targetToneLocked());
        assertTrue("target=" + snapshot.targetToneFrequencyHz(), Math.abs(snapshot.targetToneFrequencyHz() - 700) <= 20);
        assertEquals(450, snapshot.preferredToneFrequencyHz());
    }

    @Test
    public void cleanActual700HzBeatsFarHigherPreferred850Hz() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(850);

        processFrames(processor, 8, 0.0d, 0.0d);
        processFrames(processor, 12, 700.0d, 17000.0d);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue("target=" + snapshot.targetToneFrequencyHz(), snapshot.toneActive());
        assertTrue("target=" + snapshot.targetToneFrequencyHz(), snapshot.targetToneLocked());
        assertTrue("target=" + snapshot.targetToneFrequencyHz(), Math.abs(snapshot.targetToneFrequencyHz() - 700) <= 20);
        assertEquals(850, snapshot.preferredToneFrequencyHz());
    }

    @Test
    public void noisyActual700HzStillBeatsPreferred450HzPrior() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(450);

        processNoisyFrames(processor, 8, 0.0d, 0.0d, 1800.0d);
        processNoisyFrames(processor, 14, 700.0d, 15000.0d, 4200.0d, 8);

        CwSignalSnapshot snapshot = processor.snapshot();
        String debug = "target=" + snapshot.targetToneFrequencyHz()
                + " winner=" + snapshot.acquisitionWinnerFrequencyHz()
                + " source=" + snapshot.acquisitionWinnerSource()
                + " pref=" + snapshot.preferredWindowWinnerFrequencyHz()
                + " prefConf=" + snapshot.preferredWindowWinnerConfidence()
                + " prefRunner=" + snapshot.preferredWindowRunnerUpFrequencyHz()
                + " prefRunnerScore=" + snapshot.preferredWindowRunnerUpSelectionScore()
                + " wide=" + snapshot.wideScanWinnerFrequencyHz()
                + " wideConf=" + snapshot.wideScanWinnerConfidence()
                + " wideRunner=" + snapshot.wideScanRunnerUpFrequencyHz()
                + " wideRunnerScore=" + snapshot.wideScanRunnerUpSelectionScore()
                + " locked=" + snapshot.targetToneLocked()
                + " toneActive=" + snapshot.toneActive();
        assertTrue(debug, snapshot.toneActive());
        assertTrue(debug, snapshot.targetToneLocked());
        assertTrue(debug, Math.abs(snapshot.targetToneFrequencyHz() - 700) <= 20);
        assertTrue("final=" + snapshot.finalAdoptedFrequencyHz(), Math.abs(snapshot.finalAdoptedFrequencyHz() - 700) <= 20);
    }

    @Test
    public void preferredWindowAcquisitionDoesNotStayPinnedExactlyOnPreferred450WhenActual700IsInsideWindow() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(450);

        processNoisyFrames(processor, 8, 0.0d, 0.0d, 1800.0d);
        processNoisyFrames(processor, 14, 700.0d, 15000.0d, 4200.0d, 8);

        CwSignalSnapshot snapshot = processor.snapshot();
        String debug = "pref=" + snapshot.preferredToneFrequencyHz()
                + " prefWinner=" + snapshot.preferredWindowWinnerFrequencyHz()
                + " prefConf=" + snapshot.preferredWindowWinnerConfidence()
                + " wideWinner=" + snapshot.wideScanWinnerFrequencyHz()
                + " acqWinner=" + snapshot.acquisitionWinnerFrequencyHz()
                + " final=" + snapshot.finalAdoptedFrequencyHz()
                + " target=" + snapshot.targetToneFrequencyHz();
        assertTrue(debug, Math.abs(snapshot.preferredWindowWinnerFrequencyHz() - 450) >= 20);
        assertTrue(debug, Math.abs(snapshot.finalAdoptedFrequencyHz() - 700) <= 20);
        assertTrue(debug, Math.abs(snapshot.targetToneFrequencyHz() - 700) <= 20);
    }

    @Test
    public void noisyActual700HzStillBeatsPreferred850HzPrior() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(850);

        processNoisyFrames(processor, 8, 0.0d, 0.0d, 1800.0d);
        processNoisyFrames(processor, 14, 700.0d, 15000.0d, 4200.0d, 8);

        CwSignalSnapshot snapshot = processor.snapshot();
        String debug = "target=" + snapshot.targetToneFrequencyHz()
                + " final=" + snapshot.finalAdoptedFrequencyHz()
                + " pref=" + snapshot.preferredToneFrequencyHz()
                + " winner=" + snapshot.acquisitionWinnerFrequencyHz()
                + " source=" + snapshot.acquisitionWinnerSource();
        assertTrue(debug, snapshot.toneActive());
        assertTrue(debug, snapshot.targetToneLocked());
        assertTrue(debug, Math.abs(snapshot.targetToneFrequencyHz() - 700) <= 20);
        assertTrue(debug, Math.abs(snapshot.finalAdoptedFrequencyHz() - 700) <= 20);
    }

    @Test
    public void preferredWindowObservationDoesNotForceFinalAdoptionBackToPreferred() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(450);

        processNoisyFrames(processor, 8, 0.0d, 0.0d, 1800.0d);
        processNoisyFrames(processor, 14, 700.0d, 15000.0d, 4200.0d, 8);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue(snapshot.targetToneLocked());
        assertTrue(Math.abs(snapshot.finalAdoptedFrequencyHz() - snapshot.targetToneFrequencyHz()) <= 10);
        assertTrue(Math.abs(snapshot.finalAdoptedFrequencyHz() - 700) <= 20);
        assertTrue(Math.abs(snapshot.finalAdoptedFrequencyHz() - snapshot.preferredToneFrequencyHz()) >= 120);
        assertTrue(snapshot.finalAdoptedConfidence() > 0.0d);
    }

    @Test
    public void peakLockStatsRemainAvailableAfterRunFallsBackToSearch() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(650);

        processFrames(processor, 8, 0.0d, 0.0d);
        processFrames(processor, 10, 670.0d, 16000.0d);
        processNoisyFrames(processor, 10, 0.0d, 0.0d, 9000.0d, 18);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue(!snapshot.targetToneLocked());
        assertTrue(snapshot.processedFrameCount() >= 28);
        assertTrue(snapshot.lockedFrameCount() > 0);
        assertTrue(snapshot.lockedFrameRatio() >= 0.20d);
        assertTrue(snapshot.peakToneRmsAmplitude() >= snapshot.lastToneRmsAmplitude());
        assertTrue(snapshot.peakNarrowbandIsolationRatio() >= 0.50d);
        assertTrue(snapshot.maxConsecutiveLockedFrames() >= 4);
    }

    @Test
    public void snapshotExposesCurrentLockStreakDuringStableRun() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(650);

        processFrames(processor, 8, 0.0d, 0.0d);
        processFrames(processor, 10, 670.0d, 16000.0d);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue(snapshot.targetToneLocked());
        assertTrue(snapshot.consecutiveLockedFrames() >= 4);
        assertTrue(snapshot.maxConsecutiveLockedFrames() >= snapshot.consecutiveLockedFrames());
        assertTrue(Math.abs(snapshot.pendingRetuneCandidateFrequencyHz() - snapshot.targetToneFrequencyHz()) <= 20);
        assertTrue(snapshot.recentHistoryFrameCount() >= 12);
        assertEquals(snapshot.recentHistoryFrameCount(), snapshot.recentFrontEndStateHistory().length);
        assertEquals(snapshot.recentHistoryFrameCount(), snapshot.recentTrackingOffsetHistoryHz().length);
        assertTrue(new String(snapshot.recentFrontEndStateHistory()).contains("L"));
    }

    @Test
    public void cleanToneRunKeepsToneActiveUnlockedRatioNearZero() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(650);

        processFrames(processor, 8, 0.0d, 0.0d);
        processFrames(processor, 10, 670.0d, 16000.0d);
        processFrames(processor, 6, 0.0d, 0.0d);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue(snapshot.toneActiveFrameCount() > 0);
        assertTrue(snapshot.toneActiveUnlockedFrameCount() <= 1);
        assertTrue(snapshot.toneActiveUnlockedFrameRatio() <= 0.12d);
        assertTrue(snapshot.maxConsecutiveToneActiveUnlockedFrames() <= 1);
        assertTrue(snapshot.recentHistoryFrameCount() > 0);
    }

    @Test
    public void toneOnTimestampCanBePlacedBeforeQualifiedFrameBoundary() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(650);

        processFrames(processor, 8, 0.0d, 0.0d);
        List<CwToneEvent> toneOnEvents = processFramesCollecting(processor, 1, 670.0d, 16000.0d);

        assertEquals(1, toneOnEvents.size());
        CwToneEvent toneOn = toneOnEvents.get(0);
        assertEquals(CwToneEvent.Type.TONE_ON, toneOn.type());
        assertTrue(toneOn.timestampMs() < (8 * frameDurationMs()));
    }

    @Test
    public void toneOffTimestampUsesEstimatedSilenceStartInsteadOfConfirmationFrame() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(650);

        processFrames(processor, 8, 0.0d, 0.0d);
        processFrames(processor, 4, 670.0d, 16000.0d);
        processFrames(processor, 1, 0.0d, 0.0d);
        List<CwToneEvent> toneOffEvents = processFramesCollecting(processor, 1, 0.0d, 0.0d, 13);

        assertEquals(1, toneOffEvents.size());
        CwToneEvent toneOff = toneOffEvents.get(0);
        assertEquals(CwToneEvent.Type.TONE_OFF, toneOff.type());
        assertTrue(toneOff.timestampMs() < (13 * frameDurationMs()));
        assertTrue(toneOff.toneDurationMs() < (5 * frameDurationMs()));
    }

    @Test
    public void frameLocalRisingEdgeCanPlaceToneOnInsideCurrentFrame() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(650);

        processFrames(processor, 8, 0.0d, 0.0d);
        AudioFrame transitionFrame = buildPartialFrame(
                8 * frameDurationMs(),
                8 * FRAME_SIZE,
                670.0d,
                18000.0d,
                FRAME_SIZE / 2,
                FRAME_SIZE
        );

        List<CwToneEvent> events = processor.process(transitionFrame);

        assertEquals(1, events.size());
        assertEquals(CwToneEvent.Type.TONE_ON, events.get(0).type());
        assertTrue(events.get(0).timestampMs() >= (8 * frameDurationMs()) + 4L);
        assertTrue(events.get(0).timestampMs() <= (8 * frameDurationMs()) + 11L);
    }

    @Test
    public void weakSameToneUnderlayRemainsObservabilityOnlyForNow() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(700);

        processFrames(processor, 8, 0.0d, 0.0d);
        processFramesCollecting(processor, 8, 700.0d, 18000.0d, 8);
        List<CwToneEvent> weakTailEvents = processFramesCollecting(processor, 4, 700.0d, 5000.0d, 16);
        weakTailEvents.addAll(processFramesCollecting(processor, 4, 700.0d, 5000.0d, 20));

        CwSignalSnapshot snapshot = processor.snapshot();

        assertTrue("events=" + weakTailEvents, snapshot.totalToneOnEvents() >= 1);
        assertTrue("events=" + weakTailEvents, snapshot.maxConsecutiveLockedFrames() >= 4);
        assertTrue("events=" + weakTailEvents, Math.abs(snapshot.targetToneFrequencyHz() - 700) <= 20);
    }

    @Test
    public void moderateLockedToneFadeCanBoundaryOnceButMustRemainUsableOverall() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(700);

        processFrames(processor, 8, 0.0d, 0.0d);
        processFrames(processor, 8, 700.0d, 18000.0d);
        List<CwToneEvent> fadeEvents = processFramesCollecting(processor, 6, 700.0d, 15000.0d, 16);

        CwSignalSnapshot snapshot = processor.snapshot();

        assertTrue("moderate fade should keep lock", snapshot.targetToneLocked());
        assertTrue("moderate fade should not churn repeatedly", snapshot.totalToneOnEvents() <= 2);
        assertTrue("moderate fade should not churn repeatedly", snapshot.totalToneOffEvents() <= 1);
    }

    @Test
    public void frameLocalFallingEdgeCanPreserveTailWithinTransitionFrame() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(650);

        processFrames(processor, 8, 0.0d, 0.0d);
        processFramesCollecting(processor, 4, 670.0d, 18000.0d, 8);
        AudioFrame transitionFrame = buildPartialFrame(
                12 * frameDurationMs(),
                12 * FRAME_SIZE,
                670.0d,
                18000.0d,
                0,
                FRAME_SIZE / 8
        );
        List<CwToneEvent> events = new java.util.ArrayList<>(processor.process(transitionFrame));

        events.addAll(processor.process(buildFrame(
                13 * frameDurationMs(),
                13 * FRAME_SIZE,
                0.0d,
                0.0d,
                0.0d,
                0.0d
        )));
        events.addAll(processor.process(buildFrame(
                14 * frameDurationMs(),
                14 * FRAME_SIZE,
                0.0d,
                0.0d,
                0.0d,
                0.0d
        )));

        assertEquals(1, events.size());
        assertEquals(CwToneEvent.Type.TONE_OFF, events.get(0).type());
        assertTrue(events.get(0).timestampMs() >= (12 * frameDurationMs()));
        assertTrue(events.get(0).timestampMs() <= (13 * frameDurationMs()) + 6L);
    }

    @Test
    public void longFrameGapForcesToneOffAndClearsLockState() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(650);

        processFrames(processor, 8, 0.0d, 0.0d);
        processFrames(processor, 6, 670.0d, 16000.0d);

        List<CwToneEvent> events = processor.process(buildFrame(
                200L,
                20 * FRAME_SIZE,
                0.0d,
                0.0d,
                0.0d,
                0.0d
        ));

        assertEquals(1, events.size());
        assertEquals(CwToneEvent.Type.TONE_OFF, events.get(0).type());
        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue(!snapshot.toneActive());
        assertTrue(!snapshot.targetToneLocked());
        assertEquals(1, snapshot.frameGapResetCount());
        assertTrue(snapshot.lastFrameGapMs() >= 104L);
        assertTrue(snapshot.lastFrameGapResetThresholdMs() >= 40L);
        assertTrue(snapshot.worstFrameGapMs() >= snapshot.lastFrameGapMs());
        assertEquals(200L, snapshot.lastFrameGapResetAtMs());
    }

    @Test
    public void longFrameGapAllowsCleanReacquisitionWithoutStaleCarryover() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(650);

        processFrames(processor, 8, 0.0d, 0.0d);
        processFrames(processor, 6, 670.0d, 16000.0d);
        processor.process(buildFrame(
                200L,
                20 * FRAME_SIZE,
                0.0d,
                0.0d,
                0.0d,
                0.0d
        ));

        for (int index = 0; index < 8; index++) {
            processor.process(buildFrame(
                    220L + (index * frameDurationMs()),
                    (30 + index) * FRAME_SIZE,
                    670.0d,
                    16000.0d,
                    0.0d,
                    0.0d
            ));
        }

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue(snapshot.toneActive());
        assertTrue(snapshot.targetToneLocked());
        assertTrue(Math.abs(snapshot.targetToneFrequencyHz() - 670) <= 20);
        assertEquals(1, snapshot.frameGapResetCount());
    }

    @Test
    public void keepsTrackedToneNearOffPreferredSignalAcrossShortSilence() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(600);

        processFramesCollecting(processor, 8, 0.0d, 0.0d, 0);
        processFramesCollecting(processor, 10, 780.0d, 16000.0d, 8);
        processFramesCollecting(processor, 8, 0.0d, 0.0d, 18);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue("target=" + snapshot.targetToneFrequencyHz(), Math.abs(snapshot.targetToneFrequencyHz() - 780) <= 20);
        assertEquals(600, snapshot.preferredToneFrequencyHz());
    }

    @Test
    public void trackedToneDoesNotSnapBackToPreferredAfterLongIdleStretch() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(600);

        processFramesCollecting(processor, 8, 0.0d, 0.0d, 0);
        processFramesCollecting(processor, 10, 780.0d, 16000.0d, 8);
        processFramesCollecting(processor, 70, 0.0d, 0.0d, 18);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue("target=" + snapshot.targetToneFrequencyHz(), Math.abs(snapshot.targetToneFrequencyHz() - 780) <= 20);
        assertTrue(!snapshot.targetToneLocked());
    }

    @Test
    public void changingPreferredToneDuringUnlockedIdleDoesNotRewriteTrackedTarget() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(600);

        processFramesCollecting(processor, 8, 0.0d, 0.0d, 0);
        processFramesCollecting(processor, 10, 780.0d, 16000.0d, 8);
        processFramesCollecting(processor, 70, 0.0d, 0.0d, 18);

        int targetBeforePreferenceChange = processor.snapshot().targetToneFrequencyHz();
        processor.setPreferredToneFrequencyHz(500);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue(!snapshot.targetToneLocked());
        assertTrue(Math.abs(targetBeforePreferenceChange - 780) <= 20);
        assertTrue(Math.abs(snapshot.targetToneFrequencyHz() - targetBeforePreferenceChange) <= 10);
        assertEquals(500, snapshot.preferredToneFrequencyHz());
    }

    @Test
    public void offPreferredToneBurstReentryKeepsTrackingOnActualTone() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(600);

        processFramesCollecting(processor, 8, 0.0d, 0.0d, 0);
        processFramesCollecting(processor, 4, 760.0d, 14500.0d, 8);
        processFramesCollecting(processor, 4, 0.0d, 0.0d, 12);
        processFramesCollecting(processor, 4, 760.0d, 14500.0d, 16);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue(snapshot.targetToneLocked());
        assertTrue("target=" + snapshot.targetToneFrequencyHz(), Math.abs(snapshot.targetToneFrequencyHz() - 760) <= 20);
        assertEquals(600, snapshot.preferredToneFrequencyHz());
    }

    @Test
    public void offPreferredToneReentryAfterPreferredShiftStillReturnsToActualTone() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(600);

        processFramesCollecting(processor, 8, 0.0d, 0.0d, 0);
        processFramesCollecting(processor, 4, 760.0d, 14500.0d, 8);
        processFramesCollecting(processor, 8, 0.0d, 0.0d, 12);
        processor.setPreferredToneFrequencyHz(500);
        processFramesCollecting(processor, 4, 760.0d, 14500.0d, 20);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue(snapshot.targetToneLocked());
        assertTrue("target=" + snapshot.targetToneFrequencyHz(), Math.abs(snapshot.targetToneFrequencyHz() - 760) <= 20);
        assertEquals(500, snapshot.preferredToneFrequencyHz());
    }

    @Test
    public void gradualOffPreferredDriftMaintainsLockNearLatestActualTone() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(650);

        processFramesCollecting(processor, 8, 0.0d, 0.0d, 0);
        processDriftFrames(processor, new double[]{700.0d, 690.0d, 680.0d, 670.0d}, 3, 15000.0d, 8);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue(snapshot.toneActive());
        assertTrue(snapshot.targetToneLocked());
        assertTrue("target=" + snapshot.targetToneFrequencyHz(), Math.abs(snapshot.targetToneFrequencyHz() - 670) <= 20);
        assertTrue(snapshot.finalAdoptedConfidence() > 0.0d);
    }

    @Test
    public void strongNearbyInterfererSweepDoesNotStealLockFromStableActualTone() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(650);

        processFramesCollecting(processor, 8, 0.0d, 0.0d, 0);
        processFramesCollecting(processor, 4, 700.0d, 15000.0d, 10);

        for (int frameIndex = 0; frameIndex < 10; frameIndex++) {
            double interfererHz = 760.0d - (frameIndex * 10.0d);
            AudioFrame frame = buildFrame(
                    (14 + frameIndex) * frameDurationMs(),
                    (14 + frameIndex) * FRAME_SIZE,
                    700.0d,
                    15000.0d,
                    interfererHz,
                    17500.0d
            );
            List<CwToneEvent> events = processor.process(frame);
            assertNotNull(events);
        }

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue(snapshot.toneActive());
        assertTrue(snapshot.targetToneLocked());
        assertTrue("target=" + snapshot.targetToneFrequencyHz()
                        + " final=" + snapshot.finalAdoptedFrequencyHz()
                        + " prefRunner=" + snapshot.preferredWindowRunnerUpFrequencyHz(),
                Math.abs(snapshot.targetToneFrequencyHz() - 700) <= 20);
    }

    @Test
    public void driftReentryAfterPreferredShiftReturnsToLatestActualTone() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(620);

        processFramesCollecting(processor, 8, 0.0d, 0.0d, 0);
        processDriftFrames(processor, new double[]{740.0d, 730.0d}, 3, 14500.0d, 8);
        processFramesCollecting(processor, 8, 0.0d, 0.0d, 14);
        processor.setPreferredToneFrequencyHz(500);
        processDriftFrames(processor, new double[]{720.0d, 710.0d}, 3, 14500.0d, 22);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue(snapshot.targetToneLocked());
        assertTrue("target=" + snapshot.targetToneFrequencyHz(), Math.abs(snapshot.targetToneFrequencyHz() - 710) <= 20);
        assertEquals(500, snapshot.preferredToneFrequencyHz());
    }

    @Test
    public void noisyGradualOffPreferredDriftMaintainsLockNearLatestActualTone() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(650);

        processNoisyFrames(processor, 8, 0.0d, 0.0d, 1800.0d, 0);
        processNoisyDriftFrames(processor, new double[]{720.0d, 710.0d, 700.0d, 690.0d}, 3, 15000.0d, 4200.0d, 8);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue(snapshot.toneActive());
        assertTrue("target=" + snapshot.targetToneFrequencyHz()
                        + " final=" + snapshot.finalAdoptedFrequencyHz()
                        + " conf=" + snapshot.finalAdoptedConfidence(),
                snapshot.targetToneLocked());
        assertTrue("target=" + snapshot.targetToneFrequencyHz(), Math.abs(snapshot.targetToneFrequencyHz() - 690) <= 20);
        assertTrue(snapshot.finalAdoptedConfidence() > 0.0d);
    }

    @Test
    public void noisyDriftReentryAfterPreferredShiftReturnsToLatestActualTone() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(620);

        processNoisyFrames(processor, 8, 0.0d, 0.0d, 1800.0d, 0);
        processNoisyDriftFrames(processor, new double[]{750.0d, 740.0d}, 3, 14500.0d, 4200.0d, 8);
        processNoisyFrames(processor, 8, 0.0d, 0.0d, 1800.0d, 14);
        processor.setPreferredToneFrequencyHz(500);
        processNoisyDriftFrames(processor, new double[]{730.0d, 720.0d}, 3, 14500.0d, 4200.0d, 22);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue("target=" + snapshot.targetToneFrequencyHz()
                        + " final=" + snapshot.finalAdoptedFrequencyHz()
                        + " source=" + snapshot.finalAdoptedSource(),
                snapshot.targetToneLocked());
        assertTrue("target=" + snapshot.targetToneFrequencyHz(), Math.abs(snapshot.targetToneFrequencyHz() - 720) <= 20);
        assertEquals(500, snapshot.preferredToneFrequencyHz());
    }

    @Test
    public void noisyStrongNearbyInterfererSweepDoesNotStealLockFromStableActualTone() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(650);

        processNoisyFrames(processor, 8, 0.0d, 0.0d, 1800.0d, 0);
        processNoisyFrames(processor, 4, 700.0d, 15000.0d, 2800.0d, 8);

        for (int frameIndex = 0; frameIndex < 10; frameIndex++) {
            double interfererHz = 760.0d - (frameIndex * 10.0d);
            AudioFrame frame = buildFrame(
                    (12 + frameIndex) * frameDurationMs(),
                    (12 + frameIndex) * FRAME_SIZE,
                    700.0d,
                    15000.0d,
                    interfererHz,
                    17500.0d,
                    3200.0d
            );
            List<CwToneEvent> events = processor.process(frame);
            assertNotNull(events);
        }

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue(snapshot.toneActive());
        assertTrue("target=" + snapshot.targetToneFrequencyHz()
                        + " final=" + snapshot.finalAdoptedFrequencyHz()
                        + " wide=" + snapshot.wideScanWinnerFrequencyHz(),
                snapshot.targetToneLocked());
        assertTrue("target=" + snapshot.targetToneFrequencyHz(), Math.abs(snapshot.targetToneFrequencyHz() - 700) <= 20);
    }

    public void stepToneSweepAcrossCommonCwRangeKeepsReacquiringLatestActualTone() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(700);

        processFramesCollecting(processor, 8, 0.0d, 0.0d, 0);
        processFramesCollecting(processor, 4, 700.0d, 15000.0d, 8);
        processFramesCollecting(processor, 4, 600.0d, 15000.0d, 12);
        processFramesCollecting(processor, 4, 650.0d, 15000.0d, 16);
        processFramesCollecting(processor, 4, 750.0d, 15000.0d, 20);
        processFramesCollecting(processor, 4, 800.0d, 15000.0d, 24);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue("target=" + snapshot.targetToneFrequencyHz()
                        + " final=" + snapshot.finalAdoptedFrequencyHz()
                        + " winner=" + snapshot.acquisitionWinnerFrequencyHz()
                        + " source=" + snapshot.acquisitionWinnerSource(),
                snapshot.targetToneLocked());
        assertTrue("target=" + snapshot.targetToneFrequencyHz(), Math.abs(snapshot.targetToneFrequencyHz() - 800) <= 20);
        assertTrue("final=" + snapshot.finalAdoptedFrequencyHz(), Math.abs(snapshot.finalAdoptedFrequencyHz() - 800) <= 20);
        assertTrue(snapshot.maxConsecutiveLockedFrames() >= 4);
    }

    private void processFrames(
            CwSignalProcessor processor,
            int frameCount,
            double primaryFrequencyHz,
            double primaryAmplitude
    ) {
        processFrames(processor, frameCount, primaryFrequencyHz, primaryAmplitude, 0.0d, 0.0d);
    }

    private void processFrames(
            CwSignalProcessor processor,
            int frameCount,
            double primaryFrequencyHz,
            double primaryAmplitude,
            double secondaryFrequencyHz,
            double secondaryAmplitude
    ) {
        processFramesCollecting(processor, frameCount, primaryFrequencyHz, primaryAmplitude, secondaryFrequencyHz, secondaryAmplitude, 0);
    }

    private CwSignalSnapshot runHypothesisGuardDriftSequence(boolean guardEnabled) {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(450);
        processor.setExperimentalHypothesisGuardEnabled(guardEnabled);

        processFramesCollecting(processor, 8, 0.0d, 0.0d, 0);
        processFramesCollecting(processor, 12, 600.0d, 17000.0d, 8);
        processFramesCollecting(processor, 1, 600.0d, 3200.0d, 540.0d, 18000.0d, 20);
        processFramesCollecting(processor, 1, 600.0d, 3200.0d, 540.0d, 18000.0d, 21);
        processFramesCollecting(processor, 1, 600.0d, 3200.0d, 540.0d, 18000.0d, 22);
        processFramesCollecting(processor, 1, 600.0d, 3200.0d, 540.0d, 18000.0d, 23);
        return processor.snapshot();
    }

    private void processDriftFrames(
            CwSignalProcessor processor,
            double[] frequenciesHz,
            int framesPerStep,
            double amplitude,
            int frameStartIndex
    ) {
        int nextFrameIndex = frameStartIndex;
        for (double frequencyHz : frequenciesHz) {
            processFramesCollecting(processor, framesPerStep, frequencyHz, amplitude, nextFrameIndex);
            nextFrameIndex += framesPerStep;
        }
    }

    private void processNoisyDriftFrames(
            CwSignalProcessor processor,
            double[] frequenciesHz,
            int framesPerStep,
            double amplitude,
            double noiseAmplitude,
            int frameStartIndex
    ) {
        int nextFrameIndex = frameStartIndex;
        for (double frequencyHz : frequenciesHz) {
            processNoisyFrames(processor, framesPerStep, frequencyHz, amplitude, noiseAmplitude, nextFrameIndex);
            nextFrameIndex += framesPerStep;
        }
    }

    private void processNoisyFrames(
            CwSignalProcessor processor,
            int frameCount,
            double primaryFrequencyHz,
            double primaryAmplitude,
            double noiseAmplitude
    ) {
        processNoisyFrames(processor, frameCount, primaryFrequencyHz, primaryAmplitude, noiseAmplitude, 0);
    }

    private void processNoisyFrames(
            CwSignalProcessor processor,
            int frameCount,
            double primaryFrequencyHz,
            double primaryAmplitude,
            double noiseAmplitude,
            int frameStartIndex
    ) {
        for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
            int absoluteFrameIndex = frameStartIndex + frameIndex;
            AudioFrame frame = buildFrame(
                    absoluteFrameIndex * frameDurationMs(),
                    absoluteFrameIndex * FRAME_SIZE,
                    primaryFrequencyHz,
                    primaryAmplitude,
                    0.0d,
                    0.0d,
                    noiseAmplitude
            );
            List<CwToneEvent> events = processor.process(frame);
            assertNotNull(events);
        }
    }

    private List<CwToneEvent> processFramesCollecting(
            CwSignalProcessor processor,
            int frameCount,
            double primaryFrequencyHz,
            double primaryAmplitude
    ) {
        return processFramesCollecting(processor, frameCount, primaryFrequencyHz, primaryAmplitude, 0.0d, 0.0d, 0);
    }

    private List<CwToneEvent> processFramesCollecting(
            CwSignalProcessor processor,
            int frameCount,
            double primaryFrequencyHz,
            double primaryAmplitude,
            int frameStartIndex
    ) {
        return processFramesCollecting(processor, frameCount, primaryFrequencyHz, primaryAmplitude, 0.0d, 0.0d, frameStartIndex);
    }

    private List<CwToneEvent> processFramesCollecting(
            CwSignalProcessor processor,
            int frameCount,
            double primaryFrequencyHz,
            double primaryAmplitude,
            double secondaryFrequencyHz,
            double secondaryAmplitude,
            int frameStartIndex
    ) {
        List<CwToneEvent> allEvents = new java.util.ArrayList<>();
        for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
            int absoluteFrameIndex = frameStartIndex + frameIndex;
            AudioFrame frame = buildFrame(
                    absoluteFrameIndex * frameDurationMs(),
                    absoluteFrameIndex * FRAME_SIZE,
                    primaryFrequencyHz,
                    primaryAmplitude,
                    secondaryFrequencyHz,
                    secondaryAmplitude
            );
            List<CwToneEvent> events = processor.process(frame);
            assertNotNull(events);
            allEvents.addAll(events);
        }
        return allEvents;
    }

    private AudioFrame buildFrame(
            long capturedAtMs,
            int sampleOffset,
            double primaryFrequencyHz,
            double primaryAmplitude,
            double secondaryFrequencyHz,
            double secondaryAmplitude
    ) {
        return buildFrame(
                capturedAtMs,
                sampleOffset,
                primaryFrequencyHz,
                primaryAmplitude,
                secondaryFrequencyHz,
                secondaryAmplitude,
                0.0d
        );
    }

    private AudioFrame buildFrame(
            long capturedAtMs,
            int sampleOffset,
            double primaryFrequencyHz,
            double primaryAmplitude,
            double secondaryFrequencyHz,
            double secondaryAmplitude,
            double noiseAmplitude
    ) {
        short[] samples = new short[FRAME_SIZE];
        int peak = 0;
        double sumSquares = 0.0d;
        for (int index = 0; index < FRAME_SIZE; index++) {
            int absoluteIndex = sampleOffset + index;
            double sampleValue = 0.0d;
            if (primaryFrequencyHz > 0.0d && primaryAmplitude > 0.0d) {
                sampleValue += Math.sin((2.0d * Math.PI * absoluteIndex * primaryFrequencyHz) / SAMPLE_RATE_HZ)
                        * primaryAmplitude;
            }
            if (secondaryFrequencyHz > 0.0d && secondaryAmplitude > 0.0d) {
                sampleValue += Math.sin((2.0d * Math.PI * absoluteIndex * secondaryFrequencyHz) / SAMPLE_RATE_HZ)
                        * secondaryAmplitude;
            }
            if (noiseAmplitude > 0.0d) {
                sampleValue += deterministicNoise(absoluteIndex) * noiseAmplitude;
            }
            short sample = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(sampleValue)));
            samples[index] = sample;
            int absolute = Math.abs((int) sample);
            if (absolute > peak) {
                peak = absolute;
            }
            sumSquares += (double) sample * sample;
        }
        double rms = Math.sqrt(sumSquares / FRAME_SIZE);
        return new AudioFrame(samples, SAMPLE_RATE_HZ, 1, peak, rms, capturedAtMs);
    }

    private double deterministicNoise(int absoluteIndex) {
        int value = absoluteIndex * 1103515245 + 12345;
        value ^= (value >>> 13);
        value *= 1597334677;
        int bucket = value & 0x7fff;
        return (bucket / 16383.5d) - 1.0d;
    }

    private AudioFrame buildPartialFrame(
            long capturedAtMs,
            int sampleOffset,
            double primaryFrequencyHz,
            double primaryAmplitude,
            int toneStartSample,
            int toneEndSample
    ) {
        short[] samples = new short[FRAME_SIZE];
        int clampedToneStart = Math.max(0, Math.min(FRAME_SIZE, toneStartSample));
        int clampedToneEnd = Math.max(clampedToneStart, Math.min(FRAME_SIZE, toneEndSample));
        int peak = 0;
        double sumSquares = 0.0d;
        for (int index = 0; index < FRAME_SIZE; index++) {
            int absoluteIndex = sampleOffset + index;
            double sampleValue = 0.0d;
            if (index >= clampedToneStart && index < clampedToneEnd) {
                sampleValue += Math.sin((2.0d * Math.PI * absoluteIndex * primaryFrequencyHz) / SAMPLE_RATE_HZ)
                        * primaryAmplitude;
            }
            short sample = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(sampleValue)));
            samples[index] = sample;
            int absolute = Math.abs((int) sample);
            if (absolute > peak) {
                peak = absolute;
            }
            sumSquares += (double) sample * sample;
        }
        double rms = Math.sqrt(sumSquares / FRAME_SIZE);
        return new AudioFrame(samples, SAMPLE_RATE_HZ, 1, peak, rms, capturedAtMs);
    }

    private long frameDurationMs() {
        return Math.round(FRAME_SIZE * 1000.0d / SAMPLE_RATE_HZ);
    }
}
