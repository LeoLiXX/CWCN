package org.bi9clt.cwcn.core.signal;

import org.bi9clt.cwcn.core.audio.AudioFrame;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class CwSignalProcessorTest {
    private static final int SAMPLE_RATE_HZ = 16000;
    private static final int FRAME_SIZE = 256;
    private static final int[] FRONT_END_MATRIX_TONES_HZ = new int[]{600, 650, 700, 750, 800};
    private static final int DEFAULT_SQL_PERCENT = 55;

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
    public void fixedToneBootstrapCanEscapeTowardStrongFarBelowPreferredTone() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setRxToneMode(CwSignalProcessor.RxToneMode.FIXED_TONE);
        processor.setPreferredToneFrequencyHz(700);

        processFrames(processor, 8, 0.0d, 0.0d);
        processFrames(processor, 12, 550.0d, 17000.0d);

        CwSignalSnapshot snapshot = processor.snapshot();
        String debug = "target=" + snapshot.targetToneFrequencyHz()
                + " effective=" + snapshot.effectiveTrackedToneFrequencyHz()
                + " final=" + snapshot.finalAdoptedFrequencyHz()
                + " winner=" + snapshot.acquisitionWinnerFrequencyHz()
                + " source=" + snapshot.acquisitionWinnerSource()
                + " detail=" + snapshot.acquisitionDecisionDetail();
        assertTrue(debug, snapshot.toneActive());
        assertTrue(debug, snapshot.targetToneLocked());
        assertTrue(debug, Math.abs(snapshot.targetToneFrequencyHz() - 550) <= 20);
        assertTrue(debug, Math.abs(snapshot.effectiveTrackedToneFrequencyHz() - 550) <= 20);
        assertTrue(debug, Math.abs(snapshot.finalAdoptedFrequencyHz() - 550) <= 20);
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
        assertTrue(summary, enabledSnapshot.hypothesisGuardApplyCount() > 0);
        assertTrue(
                summary,
                "APPLIED:LOCKED_CONSENSUS_RETUNE".equals(enabledSnapshot.hypothesisGuardDecision())
                        || "BLOCKED:TARGET_ALREADY_NEAR_HYP".equals(enabledSnapshot.hypothesisGuardDecision())
        );
        assertTrue(summary, Math.abs(disabledSnapshot.targetToneFrequencyHz() - 530) <= 20);
        assertTrue(summary, Math.abs(enabledSnapshot.targetToneFrequencyHz() - 600) <= 20);
        assertTrue(
                summary,
                Math.abs(enabledSnapshot.targetToneFrequencyHz() - enabledSnapshot.toneHypothesisFrequencyHz()) <= 20
        );
        assertTrue(summary, Math.abs(enabledSnapshot.toneHypothesisFrequencyHz() - 600) <= 20);
        assertTrue(
                summary,
                Math.abs(
                        enabledSnapshot.targetToneFrequencyHz()
                                - enabledSnapshot.representativeLockedToneFrequencyHz()
                ) <= 20
        );
    }

    @Test
    public void hypothesisGuardPrototypeStaysIdleWithoutEnoughStableConsensusHistory() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(450);
        processor.setExperimentalHypothesisGuardEnabled(true);

        processFramesCollecting(processor, 8, 0.0d, 0.0d, 0);
        processFramesCollecting(processor, 4, 600.0d, 17000.0d, 8);
        processFramesCollecting(processor, 4, 600.0d, 3200.0d, 540.0d, 18000.0d, 12);

        CwSignalSnapshot snapshot = processor.snapshot();
        String summary = "target=" + snapshot.targetToneFrequencyHz()
                + " final=" + snapshot.finalAdoptedFrequencyHz()
                + " decision=" + snapshot.hypothesisGuardDecision()
                + " applyCount=" + snapshot.hypothesisGuardApplyCount()
                + " hyp=" + snapshot.toneHypothesisFrequencyHz()
                + " rep=" + snapshot.representativeLockedToneFrequencyHz()
                + " repFrames=" + snapshot.representativeLockedToneFrameCount();

        assertEquals(summary, 0, snapshot.hypothesisGuardApplyCount());
        assertTrue(summary, !snapshot.hypothesisGuardDecision().startsWith("APPLIED:"));
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
                            + " act=" + snapshot.activeAcquisitionCenterFrequencyHz()
                            + " actHit=" + snapshot.activeAcquisitionCenterHitCount()
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
                            + " act=" + snapshot.activeAcquisitionCenterFrequencyHz()
                            + " actHit=" + snapshot.activeAcquisitionCenterHitCount()
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
                    + " effective=" + snapshot.effectiveTrackedToneFrequencyHz()
                    + " final=" + snapshot.finalAdoptedFrequencyHz()
                    + " winner=" + snapshot.acquisitionWinnerFrequencyHz()
                    + " locked=" + snapshot.targetToneLocked();
            assertTrue(debug, snapshot.toneActive());
            assertTrue(debug, snapshot.targetToneLocked());
            assertTrue(debug, Math.abs(snapshot.targetToneFrequencyHz() - toneHz) <= 20);
            assertTrue(debug, Math.abs(snapshot.effectiveTrackedToneFrequencyHz() - toneHz) <= 20);
            assertTrue(debug, Math.abs(snapshot.finalAdoptedFrequencyHz() - toneHz) <= 20);
            assertTrue(debug, Math.abs(snapshot.acquisitionWinnerFrequencyHz() - toneHz) <= 20);
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
                    + " effective=" + snapshot.effectiveTrackedToneFrequencyHz()
                    + " final=" + snapshot.finalAdoptedFrequencyHz()
                    + " pref=" + snapshot.preferredToneFrequencyHz()
                    + " winner=" + snapshot.acquisitionWinnerFrequencyHz();
            assertTrue(debug, snapshot.toneActive());
            assertTrue(debug, snapshot.targetToneLocked());
            assertTrue(debug, Math.abs(snapshot.targetToneFrequencyHz() - toneHz) <= 20);
            assertTrue(debug, Math.abs(snapshot.effectiveTrackedToneFrequencyHz() - toneHz) <= 20);
            assertTrue(debug, Math.abs(snapshot.finalAdoptedFrequencyHz() - toneHz) <= 20);
            assertTrue(debug, Math.abs(snapshot.acquisitionWinnerFrequencyHz() - toneHz) <= 20);
        }
    }

    @Test
    public void sqlThresholdMonotonicallyTracksConfiguredLevelUnderSameNoiseFloor() {
        CwSignalSnapshot lowSql = runNoiseOnlySnapshot(650, 25, 1800.0d, 16);
        CwSignalSnapshot defaultSql = runNoiseOnlySnapshot(650, DEFAULT_SQL_PERCENT, 1800.0d, 16);
        CwSignalSnapshot highSql = runNoiseOnlySnapshot(650, 85, 1800.0d, 16);

        String debug = "low thr=" + lowSql.currentThreshold()
                + " noise=" + lowSql.noiseFloorEstimate()
                + " default thr=" + defaultSql.currentThreshold()
                + " noise=" + defaultSql.noiseFloorEstimate()
                + " high thr=" + highSql.currentThreshold()
                + " noise=" + highSql.noiseFloorEstimate();
        assertTrue(debug, lowSql.currentThreshold() < defaultSql.currentThreshold());
        assertTrue(debug, defaultSql.currentThreshold() < highSql.currentThreshold());
    }

    @Test
    public void sqlZeroCanLowerSafetyFloorBelowLegacyMinimum() {
        int attackThreshold = SqlThresholdModel.effectiveAttackThreshold(0, 0.0d, 0.0d, 0.0d);
        int releaseThreshold = SqlThresholdModel.effectiveReleaseThreshold(0, attackThreshold, 0.0d);

        assertTrue("attackThreshold=" + attackThreshold, attackThreshold < SqlThresholdModel.SAFETY_FLOOR_THRESHOLD);
        assertTrue("releaseThreshold=" + releaseThreshold, releaseThreshold < SqlThresholdModel.SAFETY_FLOOR_THRESHOLD);
        assertEquals(60, attackThreshold);
        assertEquals(60, releaseThreshold);
    }

    @Test
    public void probeSqlSensitiveWeakNoisyFrontEnd700HzCharacterization() {
        int[] sqlLevels = new int[]{25, DEFAULT_SQL_PERCENT, 70, 85};
        for (int sqlLevel : sqlLevels) {
            CwSignalSnapshot snapshot = runNoisyFrontEndSnapshot(650, sqlLevel, 700.0d, 12000.0d, 4200.0d);
            System.out.println(
                    "sql=" + sqlLevel
                            + " target=" + snapshot.targetToneFrequencyHz()
                            + " effective=" + snapshot.effectiveTrackedToneFrequencyHz()
                            + " final=" + snapshot.finalAdoptedFrequencyHz()
                            + " winner=" + snapshot.acquisitionWinnerFrequencyHz()
                            + " toneActive=" + snapshot.toneActive()
                            + " locked=" + snapshot.targetToneLocked()
                            + " thr=" + snapshot.currentThreshold() + "/" + snapshot.releaseThreshold()
                            + " floor=" + snapshot.noiseFloorEstimate() + "/" + snapshot.signalFloorEstimate()
                            + " toneRms=" + String.format(java.util.Locale.US, "%.1f", snapshot.lastToneRmsAmplitude())
                            + " frameRms=" + String.format(java.util.Locale.US, "%.1f", snapshot.lastRmsAmplitude())
            );
        }
    }

    @Test
    public void probeStrongNoisyFrontEndToneOnGate() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(650);
        processor.setSqlPercent(25);

        processNoisyFrames(processor, 8, 0.0d, 0.0d, 1800.0d, 0);
        processNoisyFrames(processor, 14, 700.0d, 12000.0d, 4200.0d, 8);

        CwSignalSnapshot snapshot = processor.snapshot();
        System.out.println(
                "probe strong noisy tone-on gate "
                        + "target=" + snapshot.targetToneFrequencyHz()
                        + " effective=" + snapshot.effectiveTrackedToneFrequencyHz()
                        + " final=" + snapshot.finalAdoptedFrequencyHz()
                        + " winner=" + snapshot.acquisitionWinnerFrequencyHz()
                        + " toneActive=" + snapshot.toneActive()
                        + " locked=" + snapshot.targetToneLocked()
                        + " attackQualified=" + processor.lastAttackQualified()
                        + " toneOnDecision=" + processor.lastToneOnDecision()
                        + " farDelay=" + processor.lastFarAttackDelayDecision()
                        + " postRelease=" + processor.lastPostReleaseSuppressionDecision()
                        + " localContrast=" + String.format(java.util.Locale.US, "%.3f", processor.lastLocalContrastRatio())
                        + " isolation=" + String.format(java.util.Locale.US, "%.3f", snapshot.narrowbandIsolationRatio())
                        + " dominance=" + String.format(java.util.Locale.US, "%.3f", snapshot.toneDominanceRatio())
                        + " toneRms=" + String.format(java.util.Locale.US, "%.1f", snapshot.lastToneRmsAmplitude())
                        + " frameRms=" + String.format(java.util.Locale.US, "%.1f", snapshot.lastRmsAmplitude())
                        + " thr=" + snapshot.currentThreshold() + "/" + snapshot.releaseThreshold()
                        + " toneOnThr=" + processor.lastToneOnThreshold()
        );
    }

    @Test
    public void practicalSqlRangeStillLocksStrongNoisy700HzSignal() {
        int[] sqlLevels = new int[]{25, DEFAULT_SQL_PERCENT, 70, 85};
        for (int sqlLevel : sqlLevels) {
            CwSignalSnapshot snapshot = runNoisyFrontEndSnapshot(650, sqlLevel, 700.0d, 12000.0d, 4200.0d);
            String debug = "sql=" + sqlLevel
                    + " target=" + snapshot.targetToneFrequencyHz()
                    + " effective=" + snapshot.effectiveTrackedToneFrequencyHz()
                    + " final=" + snapshot.finalAdoptedFrequencyHz()
                    + " winner=" + snapshot.acquisitionWinnerFrequencyHz()
                    + " toneActive=" + snapshot.toneActive()
                    + " locked=" + snapshot.targetToneLocked()
                    + " thr=" + snapshot.currentThreshold() + "/" + snapshot.releaseThreshold();
            assertTrue(debug, snapshot.toneActive());
            assertTrue(debug, snapshot.targetToneLocked());
            assertTrue(debug, Math.abs(snapshot.targetToneFrequencyHz() - 700) <= 20);
            assertTrue(debug, Math.abs(snapshot.effectiveTrackedToneFrequencyHz() - 700) <= 20);
        }
    }

    @Test
    public void practicalSqlRangeStillLocksNoisyFrontEndToneMatrix() {
        int[] sqlLevels = new int[]{25, DEFAULT_SQL_PERCENT, 70, 85};
        for (int sqlLevel : sqlLevels) {
            for (int toneHz : FRONT_END_MATRIX_TONES_HZ) {
                CwSignalSnapshot snapshot = runNoisyFrontEndSnapshot(650, sqlLevel, toneHz, 15000.0d, 4200.0d);
                String debug = "sql=" + sqlLevel
                        + " tone=" + toneHz
                        + " target=" + snapshot.targetToneFrequencyHz()
                        + " effective=" + snapshot.effectiveTrackedToneFrequencyHz()
                        + " final=" + snapshot.finalAdoptedFrequencyHz()
                        + " winner=" + snapshot.acquisitionWinnerFrequencyHz()
                        + " toneActive=" + snapshot.toneActive()
                        + " locked=" + snapshot.targetToneLocked()
                        + " thr=" + snapshot.currentThreshold() + "/" + snapshot.releaseThreshold();
                assertTrue(debug, snapshot.toneActive());
                assertTrue(debug, snapshot.targetToneLocked());
                assertTrue(debug, Math.abs(snapshot.targetToneFrequencyHz() - toneHz) <= 20);
                assertTrue(debug, Math.abs(snapshot.effectiveTrackedToneFrequencyHz() - toneHz) <= 20);
                assertTrue(debug, Math.abs(snapshot.finalAdoptedFrequencyHz() - toneHz) <= 20);
            }
        }
    }

    @Test
    public void practicalSqlRangeStillSuppressesBroadbandNoiseOnlyFalseToneOn() {
        int[] sqlLevels = new int[]{DEFAULT_SQL_PERCENT, 70, 85};
        for (int sqlLevel : sqlLevels) {
            CwSignalSnapshot snapshot = runNoiseOnlySnapshot(650, sqlLevel, 12000.0d, 16);
            String debug = "sql=" + sqlLevel
                    + " toneActive=" + snapshot.toneActive()
                    + " locked=" + snapshot.targetToneLocked()
                    + " on=" + snapshot.totalToneOnEvents()
                    + " thr=" + snapshot.currentThreshold() + "/" + snapshot.releaseThreshold()
                    + " noise=" + snapshot.noiseFloorEstimate()
                    + " signal=" + snapshot.signalFloorEstimate()
                    + " frameRms=" + snapshot.lastRmsAmplitude()
                    + " toneRms=" + snapshot.lastToneRmsAmplitude();
            assertFalse(debug, snapshot.toneActive());
            assertFalse(debug, snapshot.targetToneLocked());
            assertEquals(debug, 0, snapshot.totalToneOnEvents());
        }
    }

    @Test
    public void probeSqlSensitiveBroadbandNoiseOnlyCharacterization() {
        int[] sqlLevels = new int[]{25, DEFAULT_SQL_PERCENT, 70, 85};
        for (int sqlLevel : sqlLevels) {
            CwSignalSnapshot snapshot = runNoiseOnlySnapshot(650, sqlLevel, 12000.0d, 16);
            System.out.println(
                    "noiseOnly sql=" + sqlLevel
                            + " toneActive=" + snapshot.toneActive()
                            + " locked=" + snapshot.targetToneLocked()
                            + " on=" + snapshot.totalToneOnEvents()
                            + " target=" + snapshot.targetToneFrequencyHz()
                            + " winner=" + snapshot.acquisitionWinnerFrequencyHz()
                            + " thr=" + snapshot.currentThreshold() + "/" + snapshot.releaseThreshold()
                            + " floor=" + snapshot.noiseFloorEstimate() + "/" + snapshot.signalFloorEstimate()
                            + " toneRms=" + String.format(java.util.Locale.US, "%.1f", snapshot.lastToneRmsAmplitude())
                            + " frameRms=" + String.format(java.util.Locale.US, "%.1f", snapshot.lastRmsAmplitude())
            );
        }
    }

    @Test
    public void probeSqlSensitiveNearThresholdWeak700HzCharacterization() {
        int[] sqlLevels = new int[]{25, 40, DEFAULT_SQL_PERCENT, 70, 85};
        double[] toneAmplitudes = new double[]{2500.0d, 3000.0d, 3500.0d, 4000.0d, 4500.0d};
        double[] noiseAmplitudes = new double[]{4200.0d, 5200.0d, 6200.0d};
        for (double noiseAmplitude : noiseAmplitudes) {
            for (double toneAmplitude : toneAmplitudes) {
                for (int sqlLevel : sqlLevels) {
                    CwSignalSnapshot snapshot = runNoisyFrontEndSnapshot(650, sqlLevel, 700.0d, toneAmplitude, noiseAmplitude);
                    System.out.println(
                            "nearThreshold sql=" + sqlLevel
                                    + " noiseAmp=" + Math.round(noiseAmplitude)
                                    + " toneAmp=" + Math.round(toneAmplitude)
                                    + " target=" + snapshot.targetToneFrequencyHz()
                                    + " effective=" + snapshot.effectiveTrackedToneFrequencyHz()
                                    + " final=" + snapshot.finalAdoptedFrequencyHz()
                                    + " winner=" + snapshot.acquisitionWinnerFrequencyHz()
                                    + " toneActive=" + snapshot.toneActive()
                                    + " locked=" + snapshot.targetToneLocked()
                                    + " on=" + snapshot.totalToneOnEvents()
                                    + " thr=" + snapshot.currentThreshold() + "/" + snapshot.releaseThreshold()
                                    + " floor=" + snapshot.noiseFloorEstimate() + "/" + snapshot.signalFloorEstimate()
                                    + " toneRms=" + String.format(java.util.Locale.US, "%.1f", snapshot.lastToneRmsAmplitude())
                                    + " frameRms=" + String.format(java.util.Locale.US, "%.1f", snapshot.lastRmsAmplitude())
                    );
                }
            }
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
                        + " effective=" + snapshot.effectiveTrackedToneFrequencyHz()
                        + " final=" + snapshot.finalAdoptedFrequencyHz()
                        + " winner=" + snapshot.acquisitionWinnerFrequencyHz()
                        + " source=" + snapshot.acquisitionWinnerSource(),
                Math.abs(snapshot.targetToneFrequencyHz() - 670) <= 20);
        assertTrue("effective=" + snapshot.effectiveTrackedToneFrequencyHz(), Math.abs(snapshot.effectiveTrackedToneFrequencyHz() - 670) <= 20);
        assertTrue("final=" + snapshot.finalAdoptedFrequencyHz(), Math.abs(snapshot.finalAdoptedFrequencyHz() - 670) <= 20);
        assertTrue("winner=" + snapshot.acquisitionWinnerFrequencyHz(), Math.abs(snapshot.acquisitionWinnerFrequencyHz() - 670) <= 20);
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
    public void fixedToneLearningWindowIsClampedIntoSupportedWindow() {
        CwSignalProcessor processor = new CwSignalProcessor();

        assertEquals(
                CwSignalProcessor.DEFAULT_FIXED_TONE_LEARNING_WINDOW_HZ,
                processor.fixedToneLearningWindowHz()
        );

        processor.setFixedToneLearningWindowHz(0);
        assertEquals(
                CwSignalProcessor.MIN_FIXED_TONE_LEARNING_WINDOW_HZ,
                processor.fixedToneLearningWindowHz()
        );

        processor.setFixedToneLearningWindowHz(999);
        assertEquals(
                CwSignalProcessor.MAX_FIXED_TONE_LEARNING_WINDOW_HZ,
                processor.fixedToneLearningWindowHz()
        );
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
        processFramesCollecting(processor, 2, 670.0d, 15000.0d, 930.0d, 7000.0d, 8);
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
        List<CwSignalSnapshot> snapshots = new java.util.ArrayList<>();
        StringBuilder earlyTargetsDebug = new StringBuilder();
        boolean earlyTargetStayedNearActual = true;
        int earlyFrameCount = Math.min(4, buildSweepingNearbyCarrierFrames().size());
        CwSignalSnapshot firstUsableLockSnapshot = null;
        int frameIndex = 0;
        for (AudioFrame frame : buildSweepingNearbyCarrierFrames()) {
            processor.process(frame);
            CwSignalSnapshot frameSnapshot = processor.snapshot();
            snapshots.add(frameSnapshot);
            if (frameIndex < earlyFrameCount) {
                if (frameIndex > 0) {
                    earlyTargetsDebug.append(", ");
                }
                earlyTargetsDebug.append(frameIndex).append(':').append(frameSnapshot.targetToneFrequencyHz());
                if (Math.abs(frameSnapshot.targetToneFrequencyHz() - 670) > 20) {
                    earlyTargetStayedNearActual = false;
                }
            }
            if (firstUsableLockSnapshot == null
                    && frameSnapshot.toneActive()
                    && frameSnapshot.targetToneLocked()
                    && Math.abs(frameSnapshot.targetToneFrequencyHz() - 670) <= 40) {
                firstUsableLockSnapshot = frameSnapshot;
            }
            frameIndex += 1;
        }

        CwSignalSnapshot snapshot = processor.snapshot();
        CwSignalSnapshot firstAcquisitionSnapshot = snapshots.get(0);
        String firstAcquisitionDebug = "firstTarget=" + firstAcquisitionSnapshot.targetToneFrequencyHz()
                + " firstFinal=" + firstAcquisitionSnapshot.finalAdoptedFrequencyHz()
                + " firstAcq=" + firstAcquisitionSnapshot.acquisitionWinnerFrequencyHz()
                + " firstSource=" + firstAcquisitionSnapshot.acquisitionWinnerSource()
                + " firstDetail=" + firstAcquisitionSnapshot.acquisitionDecisionDetail()
                + " firstPref=" + firstAcquisitionSnapshot.preferredWindowWinnerFrequencyHz()
                + " firstWide=" + firstAcquisitionSnapshot.wideScanWinnerFrequencyHz()
                + " firstPrefTop=" + firstAcquisitionSnapshot.preferredWindowTopCandidatesSummary()
                + " firstWideTop=" + firstAcquisitionSnapshot.wideScanTopCandidatesSummary();
        String firstLockDebug = firstUsableLockSnapshot == null
                ? "no usable lock snapshot"
                : "lockTarget=" + firstUsableLockSnapshot.targetToneFrequencyHz()
                + " lockEffective=" + firstUsableLockSnapshot.effectiveTrackedToneFrequencyHz()
                + " lockFinal=" + firstUsableLockSnapshot.finalAdoptedFrequencyHz()
                + " lockAcq=" + firstUsableLockSnapshot.acquisitionWinnerFrequencyHz()
                + " lockSource=" + firstUsableLockSnapshot.acquisitionWinnerSource()
                + " lockToneActive=" + firstUsableLockSnapshot.toneActive()
                + " lockLocked=" + firstUsableLockSnapshot.targetToneLocked();
        String debug = "target=" + snapshot.targetToneFrequencyHz()
                + " effective=" + snapshot.effectiveTrackedToneFrequencyHz()
                + " rep=" + snapshot.representativeLockedToneFrequencyHz()
                + " final=" + snapshot.finalAdoptedFrequencyHz()
                + " acq=" + snapshot.acquisitionWinnerFrequencyHz()
                + " acqScore=" + snapshot.acquisitionWinnerSelectionScore()
                + " acqLocked=" + snapshot.acquisitionWinnerLocked()
                + " source=" + snapshot.acquisitionWinnerSource()
                + " detail=" + snapshot.acquisitionDecisionDetail()
                + " pref=" + snapshot.preferredWindowWinnerFrequencyHz()
                + " wide=" + snapshot.wideScanWinnerFrequencyHz()
                + " lock=" + snapshot.targetToneLocked()
                + " toneActive=" + snapshot.toneActive()
                + " earlyTargets=" + earlyTargetsDebug;
        assertTrue(firstAcquisitionDebug, Math.abs(firstAcquisitionSnapshot.targetToneFrequencyHz() - 670) <= 20);
        assertTrue(firstAcquisitionDebug, Math.abs(firstAcquisitionSnapshot.acquisitionWinnerFrequencyHz() - 670) <= 20);
        assertTrue(debug, earlyTargetStayedNearActual);
        assertTrue(firstLockDebug, firstUsableLockSnapshot != null);
        assertTrue(debug, Math.abs(snapshot.effectiveTrackedToneFrequencyHz() - 670) <= 40);
        assertTrue(debug, Math.abs(snapshot.representativeLockedToneFrequencyHz() - 670) <= 40);
    }

    @Test
    public void probeSweepingNearbyCarrierFrameByFrameStateTransitions() {
        probeFrameByFrameStateTransitions("startup-sweep", 650, buildSweepingNearbyCarrierFrames());
    }

    @Test
    public void probeSweepingNearbyCarrierAfterSilentWarmupFrameByFrameStateTransitions() {
        probeFrameByFrameStateTransitions("startup-sweep-after-silence", 650, 8, buildSweepingNearbyCarrierFrames());
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
        String debug = "target=" + snapshot.targetToneFrequencyHz()
                + " effective=" + snapshot.effectiveTrackedToneFrequencyHz()
                + " final=" + snapshot.finalAdoptedFrequencyHz()
                + " winner=" + snapshot.acquisitionWinnerFrequencyHz()
                + " source=" + snapshot.acquisitionWinnerSource();
        assertTrue(debug, snapshot.toneActive());
        assertTrue(debug, snapshot.targetToneLocked());
        assertTrue(debug, Math.abs(snapshot.targetToneFrequencyHz() - 700) <= 20);
        assertTrue(debug, Math.abs(snapshot.effectiveTrackedToneFrequencyHz() - 700) <= 20);
        assertTrue(debug, Math.abs(snapshot.finalAdoptedFrequencyHz() - 700) <= 20);
        assertEquals(debug, "PREFERRED_WINDOW", snapshot.acquisitionWinnerSource());
        assertTrue(debug, Math.abs(snapshot.acquisitionWinnerFrequencyHz() - snapshot.preferredToneFrequencyHz()) <= 40);
        assertTrue(debug, Math.abs(snapshot.acquisitionWinnerFrequencyHz() - snapshot.targetToneFrequencyHz()) >= 100);
        assertEquals(450, snapshot.preferredToneFrequencyHz());
    }

    @Test
    public void cleanActual700HzBeatsFarHigherPreferred850Hz() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(850);

        processFrames(processor, 8, 0.0d, 0.0d);
        processFrames(processor, 12, 700.0d, 17000.0d);

        CwSignalSnapshot snapshot = processor.snapshot();
        String debug = "target=" + snapshot.targetToneFrequencyHz()
                + " effective=" + snapshot.effectiveTrackedToneFrequencyHz()
                + " final=" + snapshot.finalAdoptedFrequencyHz()
                + " winner=" + snapshot.acquisitionWinnerFrequencyHz()
                + " source=" + snapshot.acquisitionWinnerSource();
        assertTrue(debug, snapshot.toneActive());
        assertTrue(debug, snapshot.targetToneLocked());
        assertTrue(debug, Math.abs(snapshot.targetToneFrequencyHz() - 700) <= 20);
        assertTrue(debug, Math.abs(snapshot.effectiveTrackedToneFrequencyHz() - 700) <= 20);
        assertTrue(debug, Math.abs(snapshot.finalAdoptedFrequencyHz() - 700) <= 20);
        assertTrue(debug, Math.abs(snapshot.acquisitionWinnerFrequencyHz() - 700) <= 20);
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
        List<CwToneEvent> toneOffEvents = processFramesCollecting(processor, 1, 0.0d, 0.0d, 12);
        List<CwToneEvent> nextFrameEvents = processFramesCollecting(processor, 1, 0.0d, 0.0d, 13);

        assertEquals(1, toneOffEvents.size());
        CwToneEvent toneOff = toneOffEvents.get(0);
        assertEquals(CwToneEvent.Type.TONE_OFF, toneOff.type());
        assertTrue(toneOff.timestampMs() < (13 * frameDurationMs()));
        assertTrue(toneOff.toneDurationMs() < (5 * frameDurationMs()));
        assertTrue(nextFrameEvents.isEmpty());
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
    public void nearTargetPostReleaseSoftOnsetCanReacquireBeforeFullAttackThreshold() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(700);

        processFrames(processor, 8, 0.0d, 0.0d);
        processFrames(processor, 8, 700.0d, 18000.0d);
        processFramesCollecting(processor, 2, 0.0d, 0.0d, 16);

        AudioFrame softReacquireFrame = buildPartialFrame(
                18 * frameDurationMs(),
                18 * FRAME_SIZE,
                700.0d,
                11000.0d,
                FRAME_SIZE * 3 / 4,
                FRAME_SIZE
        );

        List<CwToneEvent> events = processor.process(softReacquireFrame);
        CwSignalSnapshot snapshot = processor.snapshot();
        String debug = "events=" + renderToneEvents(events)
                + " target=" + snapshot.targetToneFrequencyHz()
                + " locked=" + snapshot.targetToneLocked()
                + " toneActive=" + snapshot.toneActive()
                + " thr=" + snapshot.currentThreshold() + "/" + snapshot.releaseThreshold()
                + " tone=" + snapshot.lastToneRmsAmplitude()
                + " dom=" + snapshot.toneDominanceRatio()
                + " iso=" + snapshot.narrowbandIsolationRatio()
                + " last=" + (snapshot.lastEvent() == null
                ? "none"
                : snapshot.lastEvent().type() + "@" + snapshot.lastEvent().timestampMs());

        assertEquals(debug, 1, events.size());
        assertEquals(debug, CwToneEvent.Type.TONE_ON, events.get(0).type());
        assertTrue(debug, events.get(0).timestampMs() >= (18 * frameDurationMs()));
        assertTrue(debug, events.get(0).timestampMs() < (19 * frameDurationMs()));
    }

    @Test
    public void nearTargetPostReleaseSoftOnsetStillWorksAfterSeveralCompletedTones() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(700);

        processFrames(processor, 8, 0.0d, 0.0d);
        processFrames(processor, 8, 700.0d, 18000.0d);

        int nextFrameIndex = 16;
        processFramesCollecting(processor, 2, 0.0d, 0.0d, nextFrameIndex);
        nextFrameIndex += 2;
        for (int cycle = 0; cycle < 6; cycle++) {
            processFramesCollecting(processor, 4, 700.0d, 18000.0d, nextFrameIndex);
            nextFrameIndex += 4;
            processFramesCollecting(processor, 2, 0.0d, 0.0d, nextFrameIndex);
            nextFrameIndex += 2;
        }

        AudioFrame softReacquireFrame = buildPartialFrame(
                nextFrameIndex * frameDurationMs(),
                nextFrameIndex * FRAME_SIZE,
                700.0d,
                11000.0d,
                FRAME_SIZE * 3 / 4,
                FRAME_SIZE
        );

        List<CwToneEvent> events = processor.process(softReacquireFrame);
        CwSignalSnapshot snapshot = processor.snapshot();
        String debug = "events=" + renderToneEvents(events)
                + " on=" + snapshot.totalToneOnEvents()
                + " off=" + snapshot.totalToneOffEvents()
                + " target=" + snapshot.targetToneFrequencyHz()
                + " locked=" + snapshot.targetToneLocked()
                + " toneActive=" + snapshot.toneActive()
                + " thr=" + snapshot.currentThreshold() + "/" + snapshot.releaseThreshold()
                + " tone=" + snapshot.lastToneRmsAmplitude()
                + " dom=" + snapshot.toneDominanceRatio()
                + " iso=" + snapshot.narrowbandIsolationRatio()
                + " last=" + (snapshot.lastEvent() == null
                ? "none"
                : snapshot.lastEvent().type() + "@" + snapshot.lastEvent().timestampMs());

        assertTrue(debug, snapshot.totalToneOnEvents() > 6);
        assertEquals(debug, 1, events.size());
        assertEquals(debug, CwToneEvent.Type.TONE_ON, events.get(0).type());
        assertTrue(debug, events.get(0).timestampMs() >= (nextFrameIndex * frameDurationMs()));
        assertTrue(debug, events.get(0).timestampMs() < ((nextFrameIndex + 1) * frameDurationMs()));
    }

    @Test
    public void microGapFrameLocalOnsetDoesNotRestartToneImmediatelyAfterRelease() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setRxToneMode(CwSignalProcessor.RxToneMode.FIXED_TONE);
        processor.setPreferredToneFrequencyHz(700);

        processFramesCollecting(processor, 8, 0.0d, 0.0d, 0);
        processFramesCollecting(processor, 6, 700.0d, 18000.0d, 8);

        AudioFrame releaseFrame = buildPartialFrame(
                14 * frameDurationMs(),
                14 * FRAME_SIZE,
                700.0d,
                18000.0d,
                0,
                FRAME_SIZE / 16
        );
        List<CwToneEvent> releaseEvents = new java.util.ArrayList<>(processor.process(releaseFrame));
        List<CwToneEvent> microGapEvents = processFramesCollecting(processor, 1, 700.0d, 6000.0d, 15);
        CwSignalSnapshot snapshot = processor.snapshot();
        String debug = "release=" + renderToneEvents(releaseEvents)
                + " micro=" + renderToneEvents(microGapEvents)
                + " decision=" + processor.lastToneOnDecision()
                + " suppress=" + processor.lastPostReleaseSuppressionDecision()
                + " toneActive=" + snapshot.toneActive()
                + " locked=" + snapshot.targetToneLocked()
                + " last=" + (snapshot.lastEvent() == null
                ? "none"
                : snapshot.lastEvent().type() + "@" + snapshot.lastEvent().timestampMs()
                + "/" + snapshot.lastEvent().toneDurationMs());

        assertEquals(debug, 1, releaseEvents.size());
        assertEquals(debug, CwToneEvent.Type.TONE_OFF, releaseEvents.get(0).type());
        assertTrue(debug, microGapEvents.isEmpty());
        assertEquals(debug, "BLOCKED:MICRO_GAP_TONE_ON", processor.lastToneOnDecision());
        assertEquals(debug, "SUPPRESS:MICRO_GAP_TONE_ON", processor.lastPostReleaseSuppressionDecision());
        assertFalse(debug, snapshot.toneActive());
    }

    @Test
    public void shortButNormalGapCanStillRestartToneAfterRelease() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setRxToneMode(CwSignalProcessor.RxToneMode.FIXED_TONE);
        processor.setPreferredToneFrequencyHz(700);

        processFramesCollecting(processor, 8, 0.0d, 0.0d, 0);
        processFramesCollecting(processor, 6, 700.0d, 18000.0d, 8);

        AudioFrame releaseFrame = buildPartialFrame(
                14 * frameDurationMs(),
                14 * FRAME_SIZE,
                700.0d,
                18000.0d,
                0,
                FRAME_SIZE / 16
        );
        List<CwToneEvent> releaseEvents = new java.util.ArrayList<>(processor.process(releaseFrame));
        processFramesCollecting(processor, 1, 0.0d, 0.0d, 15);
        List<CwToneEvent> restartEvents = processFramesCollecting(processor, 1, 700.0d, 6000.0d, 16);
        CwSignalSnapshot snapshot = processor.snapshot();
        String debug = "release=" + renderToneEvents(releaseEvents)
                + " restart=" + renderToneEvents(restartEvents)
                + " decision=" + processor.lastToneOnDecision()
                + " suppress=" + processor.lastPostReleaseSuppressionDecision()
                + " toneActive=" + snapshot.toneActive()
                + " locked=" + snapshot.targetToneLocked()
                + " last=" + (snapshot.lastEvent() == null
                ? "none"
                : snapshot.lastEvent().type() + "@" + snapshot.lastEvent().timestampMs()
                + "/" + snapshot.lastEvent().toneDurationMs());

        assertEquals(debug, 1, releaseEvents.size());
        assertEquals(debug, CwToneEvent.Type.TONE_OFF, releaseEvents.get(0).type());
        assertEquals(debug, 1, restartEvents.size());
        assertEquals(debug, CwToneEvent.Type.TONE_ON, restartEvents.get(0).type());
        assertTrue(
                debug,
                "ALLOW:ATTACK_THRESHOLD".equals(processor.lastToneOnDecision())
                        || "ALLOW:POST_RELEASE_RESCUE".equals(processor.lastToneOnDecision())
        );
        assertTrue(debug, snapshot.toneActive());
    }

    @Test
    public void stableCurrentRunCanHoldReleaseTailEvenWhenRecentTrustWindowIsStillThin() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(700);

        processFramesCollecting(processor, 20, 0.0d, 0.0d, 0);
        processFramesCollecting(processor, 8, 700.0d, 450.0d, 20);

        List<CwToneEvent> events = processor.process(buildFrame(
                28L * frameDurationMs(),
                28 * FRAME_SIZE,
                700.0d,
                300.0d,
                0.0d,
                0.0d
        ));
        CwSignalSnapshot snapshot = processor.snapshot();
        String debug = "events=" + renderToneEvents(events)
                + " toneActive=" + snapshot.toneActive()
                + " locked=" + snapshot.targetToneLocked()
                + " thr=" + snapshot.currentThreshold() + "/" + snapshot.releaseThreshold()
                + " tone=" + String.format("%.1f", snapshot.lastToneRmsAmplitude())
                + " rms=" + String.format("%.1f", snapshot.lastRmsAmplitude())
                + " maxLock=" + snapshot.maxConsecutiveLockedFrames()
                + " recent=" + snapshot.recentHistoryFrameCount()
                + " tail=" + processor.lastReleaseTailHoldDecision()
                + " last=" + (snapshot.lastEvent() == null
                ? "none"
                : snapshot.lastEvent().type() + "@" + snapshot.lastEvent().timestampMs()
                + "/" + snapshot.lastEvent().toneDurationMs());

        assertTrue(debug, snapshot.toneActive());
        assertEquals(debug, "HOLD:APPLIED", processor.lastReleaseTailHoldDecision());
        assertTrue(debug, events.isEmpty());
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
                8
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
        assertTrue(events.get(0).timestampMs() >= (12 * frameDurationMs()) - 1L);
        assertTrue(events.get(0).timestampMs() <= (13 * frameDurationMs()) + 6L);
    }

    @Test
    public void longSameFrameSilenceCanEmitToneOffWithoutWaitingForNextFrame() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setRxToneMode(CwSignalProcessor.RxToneMode.FIXED_TONE);
        processor.setPreferredToneFrequencyHz(650);

        processFrames(processor, 8, 0.0d, 0.0d);
        processFramesCollecting(processor, 4, 670.0d, 18000.0d, 8);
        AudioFrame transitionFrame = buildPartialFrame(
                12 * frameDurationMs(),
                12 * FRAME_SIZE,
                670.0d,
                18000.0d,
                0,
                FRAME_SIZE / 16
        );

        List<CwToneEvent> transitionEvents = new java.util.ArrayList<>(processor.process(transitionFrame));
        List<CwToneEvent> nextFrameEvents = processor.process(buildFrame(
                13 * frameDurationMs(),
                13 * FRAME_SIZE,
                0.0d,
                0.0d,
                0.0d,
                0.0d
        ));

        assertEquals(1, transitionEvents.size());
        assertEquals(CwToneEvent.Type.TONE_OFF, transitionEvents.get(0).type());
        assertTrue(transitionEvents.get(0).timestampMs() >= (12 * frameDurationMs()));
        assertTrue(transitionEvents.get(0).timestampMs() < (13 * frameDurationMs()));
        assertTrue(nextFrameEvents.isEmpty());
    }

    @Test
    public void eighthFrameTailStillNeedsNextFrameToEmitToneOff() {
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

        List<CwToneEvent> transitionEvents = new java.util.ArrayList<>(processor.process(transitionFrame));
        List<CwToneEvent> nextFrameEvents = processor.process(buildFrame(
                13 * frameDurationMs(),
                13 * FRAME_SIZE,
                0.0d,
                0.0d,
                0.0d,
                0.0d
        ));

        assertTrue(transitionEvents.isEmpty());
        assertEquals(1, nextFrameEvents.size());
        assertEquals(CwToneEvent.Type.TONE_OFF, nextFrameEvents.get(0).type());
        assertTrue(nextFrameEvents.get(0).timestampMs() >= (12 * frameDurationMs()));
        assertTrue(nextFrameEvents.get(0).timestampMs() <= (13 * frameDurationMs()) + 6L);
    }

    @Test
    public void probeSameFrameToneOffBoundaryWithinTransitionFrame() {
        int[] toneEndSamples = new int[]{FRAME_SIZE / 2, FRAME_SIZE / 4, FRAME_SIZE / 8, FRAME_SIZE / 16, 8, 4};
        for (int toneEndSample : toneEndSamples) {
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
                    toneEndSample
            );

            List<CwToneEvent> transitionEvents = new java.util.ArrayList<>(processor.process(transitionFrame));
            CwSignalSnapshot transitionSnapshot = processor.snapshot();
            List<CwToneEvent> nextFrameEvents = processor.process(buildFrame(
                    13 * frameDurationMs(),
                    13 * FRAME_SIZE,
                    0.0d,
                    0.0d,
                    0.0d,
                    0.0d
            ));

            System.out.println(
                    "toneEndSample=" + toneEndSample
                            + " transitionEvents=" + transitionEvents.size()
                            + " nextFrameEvents=" + nextFrameEvents.size()
                            + " toneActive=" + transitionSnapshot.toneActive()
                            + " thr=" + transitionSnapshot.currentThreshold() + "/" + transitionSnapshot.releaseThreshold()
                            + " toneRms=" + String.format("%.1f", transitionSnapshot.lastToneRmsAmplitude())
                            + " rms=" + String.format("%.1f", transitionSnapshot.lastRmsAmplitude())
                            + " last="
                            + (transitionSnapshot.lastEvent() == null
                            ? "-"
                            : transitionSnapshot.lastEvent().type() + "@"
                            + transitionSnapshot.lastEvent().timestampMs() + "/"
                            + transitionSnapshot.lastEvent().toneDurationMs())
            );
        }
    }

    @Test
    public void probeWeakValleyMergeBoundary() {
        double[] valleyAmplitudes = new double[]{
                0.0d, 300.0d, 600.0d, 900.0d, 1200.0d, 1500.0d, 1800.0d, 2400.0d, 2700.0d, 3000.0d, 3600.0d
        };
        for (double valleyAmplitude : valleyAmplitudes) {
            CwSignalSnapshot snapshot = runWeakValleySequence(valleyAmplitude);
            System.out.println(
                    "valleyAmp=" + valleyAmplitude
                            + " on=" + snapshot.totalToneOnEvents()
                            + " off=" + snapshot.totalToneOffEvents()
                            + " toneActive=" + snapshot.toneActive()
                            + " lock=" + snapshot.targetToneLocked()
                            + " thr=" + snapshot.currentThreshold() + "/" + snapshot.releaseThreshold()
                            + " floor=" + snapshot.noiseFloorEstimate() + "/" + snapshot.signalFloorEstimate()
                            + " toneRms=" + String.format("%.1f", snapshot.lastToneRmsAmplitude())
                            + " rms=" + String.format("%.1f", snapshot.lastRmsAmplitude())
                            + " last="
                            + (snapshot.lastEvent() == null
                            ? "-"
                            : snapshot.lastEvent().type() + "@" + snapshot.lastEvent().timestampMs()
                            + "/" + snapshot.lastEvent().toneDurationMs())
            );
        }
    }

    @Test
    public void weakValleyAt1800StillSplitsIntoTwoToneRuns() {
        CwSignalSnapshot snapshot = runWeakValleySequence(1800.0d);

        String debug = "on=" + snapshot.totalToneOnEvents()
                + " off=" + snapshot.totalToneOffEvents()
                + " last=" + (snapshot.lastEvent() == null
                ? "-"
                : snapshot.lastEvent().type() + "@" + snapshot.lastEvent().timestampMs()
                + "/" + snapshot.lastEvent().toneDurationMs());
        assertEquals(debug, 2, snapshot.totalToneOnEvents());
        assertEquals(debug, 2, snapshot.totalToneOffEvents());
        assertTrue(debug, snapshot.lastEvent() != null);
        assertEquals(debug, CwToneEvent.Type.TONE_OFF, snapshot.lastEvent().type());
        assertTrue(debug, snapshot.lastEvent().toneDurationMs() <= 90L);
    }

    @Test
    public void shallowValleyAt2400NowSplitsIntoTwoToneRuns() {
        CwSignalSnapshot snapshot = runWeakValleySequence(2400.0d);

        String debug = "on=" + snapshot.totalToneOnEvents()
                + " off=" + snapshot.totalToneOffEvents()
                + " last=" + (snapshot.lastEvent() == null
                ? "-"
                : snapshot.lastEvent().type() + "@" + snapshot.lastEvent().timestampMs()
                + "/" + snapshot.lastEvent().toneDurationMs());
        assertEquals(debug, 2, snapshot.totalToneOnEvents());
        assertEquals(debug, 2, snapshot.totalToneOffEvents());
        assertTrue(debug, snapshot.lastEvent() != null);
        assertEquals(debug, CwToneEvent.Type.TONE_OFF, snapshot.lastEvent().type());
        assertTrue(debug, snapshot.lastEvent().toneDurationMs() <= 90L);
    }

    @Test
    public void autoTrackKeepsSingleToneRunAcrossShortWeakValleyThatUsedToSplit() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setRxToneMode(CwSignalProcessor.RxToneMode.AUTO_TRACK);
        processor.setPreferredToneFrequencyHz(700);

        processFrames(processor, 8, 0.0d, 0.0d);
        processFramesCollecting(processor, 6, 700.0d, 18000.0d, 8);
        processFramesCollecting(processor, 1, 700.0d, 2400.0d, 14);
        processFramesCollecting(processor, 6, 700.0d, 18000.0d, 15);
        processFramesCollecting(processor, 4, 0.0d, 0.0d, 21);

        CwSignalSnapshot snapshot = processor.snapshot();
        String debug = "on=" + snapshot.totalToneOnEvents()
                + " off=" + snapshot.totalToneOffEvents()
                + " last=" + (snapshot.lastEvent() == null
                ? "-"
                : snapshot.lastEvent().type() + "@" + snapshot.lastEvent().timestampMs()
                + "/" + snapshot.lastEvent().toneDurationMs());
        assertEquals(debug, 1, snapshot.totalToneOnEvents());
        assertEquals(debug, 1, snapshot.totalToneOffEvents());
        assertTrue(debug, snapshot.lastEvent() != null);
        assertEquals(debug, CwToneEvent.Type.TONE_OFF, snapshot.lastEvent().type());
        assertTrue(debug, snapshot.lastEvent().toneDurationMs() >= 150L);
    }

    @Test
    public void autoTrackWeakValleyBridgeStillWorksBeforeRepresentativeLockFullyMatures() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setRxToneMode(CwSignalProcessor.RxToneMode.AUTO_TRACK);
        processor.setPreferredToneFrequencyHz(700);

        processFrames(processor, 8, 0.0d, 0.0d);
        processFramesCollecting(processor, 4, 700.0d, 18000.0d, 8);
        processFramesCollecting(processor, 1, 700.0d, 2400.0d, 12);
        processFramesCollecting(processor, 6, 700.0d, 18000.0d, 13);
        processFramesCollecting(processor, 4, 0.0d, 0.0d, 19);

        CwSignalSnapshot snapshot = processor.snapshot();
        String debug = "on=" + snapshot.totalToneOnEvents()
                + " off=" + snapshot.totalToneOffEvents()
                + " active=" + snapshot.toneActive()
                + " locked=" + snapshot.targetToneLocked()
                + " rep=" + snapshot.representativeLockedToneFrameCount()
                + " maxLock=" + snapshot.maxConsecutiveLockedFrames()
                + " last=" + (snapshot.lastEvent() == null
                ? "-"
                : snapshot.lastEvent().type() + "@" + snapshot.lastEvent().timestampMs()
                + "/" + snapshot.lastEvent().toneDurationMs());
        assertEquals(debug, 1, snapshot.totalToneOnEvents());
        assertEquals(debug, 1, snapshot.totalToneOffEvents());
        assertTrue(debug, snapshot.lastEvent() != null);
        assertEquals(debug, CwToneEvent.Type.TONE_OFF, snapshot.lastEvent().type());
        assertTrue(debug, snapshot.lastEvent().toneDurationMs() >= 130L);
    }

    @Test
    public void autoTrackWeakValleyWithNearbyStrongerCompetitorDoesNotSplitEarlyToneRun() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setRxToneMode(CwSignalProcessor.RxToneMode.AUTO_TRACK);
        processor.setPreferredToneFrequencyHz(700);

        processFrames(processor, 8, 0.0d, 0.0d);
        processFramesCollecting(processor, 4, 700.0d, 18000.0d, 8);
        processFramesCollecting(processor, 2, 700.0d, 2400.0d, 760.0d, 19000.0d, 12);
        processFramesCollecting(processor, 6, 700.0d, 18000.0d, 14);
        processFramesCollecting(processor, 4, 0.0d, 0.0d, 20);

        CwSignalSnapshot snapshot = processor.snapshot();
        String debug = "on=" + snapshot.totalToneOnEvents()
                + " off=" + snapshot.totalToneOffEvents()
                + " active=" + snapshot.toneActive()
                + " locked=" + snapshot.targetToneLocked()
                + " target=" + snapshot.targetToneFrequencyHz()
                + " final=" + snapshot.finalAdoptedFrequencyHz()
                + " rep=" + snapshot.representativeLockedToneFrameCount()
                + " maxLock=" + snapshot.maxConsecutiveLockedFrames()
                + " guard=" + snapshot.lockedRetuneGuardHolding()
                + " last=" + (snapshot.lastEvent() == null
                ? "-"
                : snapshot.lastEvent().type() + "@" + snapshot.lastEvent().timestampMs()
                + "/" + snapshot.lastEvent().toneDurationMs());
        assertEquals(debug, 1, snapshot.totalToneOnEvents());
        assertEquals(debug, 1, snapshot.totalToneOffEvents());
        assertTrue(debug, snapshot.lastEvent() != null);
        assertEquals(debug, CwToneEvent.Type.TONE_OFF, snapshot.lastEvent().type());
        assertTrue(debug, snapshot.lastEvent().toneDurationMs() >= 130L);
    }

    @Test
    public void autoTrackWeakValleyBridgeDoesNotRearmForeverAcrossExtendedWeakPlateau() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setRxToneMode(CwSignalProcessor.RxToneMode.AUTO_TRACK);
        processor.setPreferredToneFrequencyHz(700);

        processFrames(processor, 8, 0.0d, 0.0d);
        processFramesCollecting(processor, 6, 700.0d, 18000.0d, 8);
        processFramesCollecting(processor, 6, 700.0d, 2400.0d, 14);

        CwSignalSnapshot snapshot = processor.snapshot();
        String debug = "on=" + snapshot.totalToneOnEvents()
                + " off=" + snapshot.totalToneOffEvents()
                + " toneActive=" + snapshot.toneActive()
                + " locked=" + snapshot.targetToneLocked()
                + " last=" + (snapshot.lastEvent() == null
                ? "-"
                : snapshot.lastEvent().type() + "@" + snapshot.lastEvent().timestampMs()
                + "/" + snapshot.lastEvent().toneDurationMs());
        assertEquals(debug, 1, snapshot.totalToneOnEvents());
        assertEquals(debug, 1, snapshot.totalToneOffEvents());
        assertTrue(debug, !snapshot.toneActive());
        assertTrue(debug, snapshot.lastEvent() != null);
        assertEquals(debug, CwToneEvent.Type.TONE_OFF, snapshot.lastEvent().type());
    }

    @Test
    public void valleyAt3000NowSplitsIntoTwoToneRuns() {
        CwSignalSnapshot snapshot = runWeakValleySequence(3000.0d);

        String debug = "on=" + snapshot.totalToneOnEvents()
                + " off=" + snapshot.totalToneOffEvents()
                + " last=" + (snapshot.lastEvent() == null
                ? "-"
                : snapshot.lastEvent().type() + "@" + snapshot.lastEvent().timestampMs()
                + "/" + snapshot.lastEvent().toneDurationMs());
        assertEquals(debug, 2, snapshot.totalToneOnEvents());
        assertEquals(debug, 2, snapshot.totalToneOffEvents());
        assertTrue(debug, snapshot.lastEvent() != null);
        assertEquals(debug, CwToneEvent.Type.TONE_OFF, snapshot.lastEvent().type());
        assertTrue(debug, snapshot.lastEvent().toneDurationMs() <= 90L);
    }

    @Test
    public void valleyAt2700NowSplitsIntoTwoToneRuns() {
        CwSignalSnapshot snapshot = runWeakValleySequence(2700.0d);

        String debug = "on=" + snapshot.totalToneOnEvents()
                + " off=" + snapshot.totalToneOffEvents()
                + " last=" + (snapshot.lastEvent() == null
                ? "-"
                : snapshot.lastEvent().type() + "@" + snapshot.lastEvent().timestampMs()
                + "/" + snapshot.lastEvent().toneDurationMs());
        assertEquals(debug, 2, snapshot.totalToneOnEvents());
        assertEquals(debug, 2, snapshot.totalToneOffEvents());
        assertTrue(debug, snapshot.lastEvent() != null);
        assertEquals(debug, CwToneEvent.Type.TONE_OFF, snapshot.lastEvent().type());
        assertTrue(debug, snapshot.lastEvent().toneDurationMs() <= 90L);
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
                        + " effective=" + snapshot.effectiveTrackedToneFrequencyHz()
                        + " final=" + snapshot.finalAdoptedFrequencyHz()
                        + " prefRunner=" + snapshot.preferredWindowRunnerUpFrequencyHz(),
                Math.abs(snapshot.targetToneFrequencyHz() - 700) <= 20);
        assertTrue("effective=" + snapshot.effectiveTrackedToneFrequencyHz(), Math.abs(snapshot.effectiveTrackedToneFrequencyHz() - 700) <= 20);
        assertTrue("final=" + snapshot.finalAdoptedFrequencyHz(), Math.abs(snapshot.finalAdoptedFrequencyHz() - 700) <= 20);
    }

    @Test
    public void stableLockedToneDoesNotImmediatelyRetuneToFarStrongerCandidate() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(650);

        processFramesCollecting(processor, 8, 0.0d, 0.0d, 0);
        processFramesCollecting(processor, 10, 700.0d, 15000.0d, 8);
        processFramesCollecting(processor, 1, 700.0d, 15000.0d, 620.0d, 22000.0d, 18);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue(snapshot.toneActive());
        assertTrue(snapshot.targetToneLocked());
        assertTrue(snapshot.lockedRetuneGuardHolding());
        assertEquals("MID", snapshot.lockedRetuneGuardBand());
        assertTrue(snapshot.lockedRetuneGuardRemainingScans() > 0);
        assertTrue("target=" + snapshot.targetToneFrequencyHz()
                        + " final=" + snapshot.finalAdoptedFrequencyHz()
                        + " winner=" + snapshot.acquisitionWinnerFrequencyHz()
                        + " source=" + snapshot.acquisitionWinnerSource(),
                Math.abs(snapshot.targetToneFrequencyHz() - 700) <= 20);
        assertTrue("final=" + snapshot.finalAdoptedFrequencyHz(), Math.abs(snapshot.finalAdoptedFrequencyHz() - 700) <= 20);
    }

    @Test
    public void collapsedWeakValleyAllowsImmediateDecisiveFarRetune() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(670);

        processFramesCollecting(processor, 8, 0.0d, 0.0d, 0);
        processFramesCollecting(processor, 10, 670.0d, 16000.0d, 8);
        processFramesCollecting(processor, 1, 670.0d, 2400.0d, 740.0d, 19000.0d, 18);

        CwSignalSnapshot snapshot = processor.snapshot();
        String debug = "target=" + snapshot.targetToneFrequencyHz()
                + " effective=" + snapshot.effectiveTrackedToneFrequencyHz()
                + " final=" + snapshot.finalAdoptedFrequencyHz()
                + " prev=" + snapshot.previousTargetBeforeScanFrequencyHz()
                + " prevTone=" + snapshot.previousTargetBeforeScanToneRms()
                + " prevScore=" + snapshot.previousTargetBeforeScanSelectionScore()
                + " prevLocked=" + snapshot.previousTargetBeforeScanLocked()
                + " acq=" + snapshot.acquisitionWinnerFrequencyHz()
                + " acqScore=" + snapshot.acquisitionWinnerSelectionScore()
                + " acqLocked=" + snapshot.acquisitionWinnerLocked()
                + " finalLocked=" + snapshot.finalAdoptedLocked()
                + " consecLocked=" + snapshot.consecutiveLockedFrames()
                + " guard=" + snapshot.lockedRetuneGuardHolding()
                + " guardDrift=" + snapshot.lockedRetuneGuardDriftHz()
                + " scans=" + snapshot.lockedRetuneGuardObservedScans()
                + "/" + snapshot.lockedRetuneGuardRequiredScans()
                + " source=" + snapshot.finalAdoptedSource();

        assertTrue(debug, snapshot.toneActive());
        assertTrue(debug, snapshot.targetToneLocked());
        assertTrue(debug, Math.abs(snapshot.targetToneFrequencyHz() - snapshot.acquisitionWinnerFrequencyHz()) <= 20);
        assertTrue(debug, Math.abs(snapshot.finalAdoptedFrequencyHz() - snapshot.acquisitionWinnerFrequencyHz()) <= 20);
        assertTrue(debug, snapshot.acquisitionWinnerFrequencyHz() >= 710);
        assertTrue(debug, !snapshot.lockedRetuneGuardHolding());
        assertEquals(debug, "LOCKED_RETUNE", snapshot.finalAdoptedSource());
    }

    @Test
    public void probeToneActiveWeakValleyFarRetuneFrameState() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(670);

        processFramesCollecting(processor, 8, 0.0d, 0.0d, 0);
        processFramesCollecting(processor, 10, 670.0d, 16000.0d, 8);
        AudioFrame frame = buildFrame(
                18 * frameDurationMs(),
                18 * FRAME_SIZE,
                670.0d,
                2400.0d,
                740.0d,
                19000.0d
        );

        CwSignalSnapshot before = processor.snapshot();
        List<CwToneEvent> events = processor.process(frame);
        CwSignalSnapshot after = processor.snapshot();

        System.out.println(
                "before target=" + before.targetToneFrequencyHz()
                        + " final=" + before.finalAdoptedFrequencyHz()
                        + " source=" + before.finalAdoptedSource()
                        + " locked=" + before.targetToneLocked()
                        + " consec=" + before.consecutiveLockedFrames()
                        + " prevLocked=" + before.previousTargetBeforeScanLocked()
                        + " guard=" + before.lockedRetuneGuardHolding()
                        + " events=" + renderToneEvents(events)
                        + "\nafter target=" + after.targetToneFrequencyHz()
                        + " final=" + after.finalAdoptedFrequencyHz()
                        + " source=" + after.finalAdoptedSource()
                        + " locked=" + after.targetToneLocked()
                        + " consec=" + after.consecutiveLockedFrames()
                        + " prev=" + after.previousTargetBeforeScanFrequencyHz()
                        + " prevTone=" + after.previousTargetBeforeScanToneRms()
                        + " prevScore=" + after.previousTargetBeforeScanSelectionScore()
                        + " guard=" + after.lockedRetuneGuardHolding()
                        + " guardDrift=" + after.lockedRetuneGuardDriftHz()
                        + " scans=" + after.lockedRetuneGuardObservedScans()
                        + "/" + after.lockedRetuneGuardRequiredScans()
                        + " acq=" + after.acquisitionWinnerFrequencyHz()
                        + " acqScore=" + after.acquisitionWinnerSelectionScore()
                        + " finalLocked=" + after.finalAdoptedLocked()
        );
    }

    @Test
    public void lockedToneCanReleaseAndRetuneAfterPreviousSignalDisappears() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(650);

        processFramesCollecting(processor, 8, 0.0d, 0.0d, 0);
        processFramesCollecting(processor, 10, 700.0d, 15000.0d, 8);
        processFramesCollecting(processor, 1, 700.0d, 15000.0d, 620.0d, 22000.0d, 18);
        processFramesCollecting(processor, 6, 620.0d, 22000.0d, 19);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue(snapshot.toneActive());
        assertTrue("target=" + snapshot.targetToneFrequencyHz()
                        + " effective=" + snapshot.effectiveTrackedToneFrequencyHz()
                        + " final=" + snapshot.finalAdoptedFrequencyHz()
                        + " source=" + snapshot.finalAdoptedSource(),
                snapshot.targetToneLocked());
        assertTrue(!snapshot.lockedRetuneGuardHolding());
        assertTrue("target=" + snapshot.targetToneFrequencyHz(), Math.abs(snapshot.targetToneFrequencyHz() - 620) <= 20);
        assertTrue("final=" + snapshot.finalAdoptedFrequencyHz(), Math.abs(snapshot.finalAdoptedFrequencyHz() - 620) <= 20);
        assertTrue("effective=" + snapshot.effectiveTrackedToneFrequencyHz(), Math.abs(snapshot.effectiveTrackedToneFrequencyHz() - 620) <= 20);
    }

    @Test
    public void releaseRetuneDoesNotGetReanchoredBackToContinuityAnchorOnNextFrame() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(650);

        processFramesCollecting(processor, 8, 0.0d, 0.0d, 0);
        processFramesCollecting(processor, 10, 700.0d, 15000.0d, 8);

        List<CwToneEvent> overlapEvents = processor.process(buildFrame(
                18 * frameDurationMs(),
                18 * FRAME_SIZE,
                700.0d,
                15000.0d,
                620.0d,
                22000.0d
        ));
        CwSignalSnapshot overlapSnapshot = processor.snapshot();

        List<CwToneEvent> retuneEvents = processor.process(buildFrame(
                19 * frameDurationMs(),
                19 * FRAME_SIZE,
                620.0d,
                22000.0d,
                0.0d,
                0.0d
        ));
        CwSignalSnapshot retuneSnapshot = processor.snapshot();

        String debug = "overlap target=" + overlapSnapshot.targetToneFrequencyHz()
                + " final=" + overlapSnapshot.finalAdoptedFrequencyHz()
                + " effective=" + overlapSnapshot.effectiveTrackedToneFrequencyHz()
                + " acq=" + overlapSnapshot.acquisitionWinnerFrequencyHz()
                + " source=" + overlapSnapshot.finalAdoptedSource()
                + " guard=" + overlapSnapshot.lockedRetuneGuardHolding()
                + " scans=" + overlapSnapshot.lockedRetuneGuardObservedScans()
                + "/" + overlapSnapshot.lockedRetuneGuardRequiredScans()
                + " overlapEvents=" + renderToneEvents(overlapEvents)
                + "\nretune target=" + retuneSnapshot.targetToneFrequencyHz()
                + " final=" + retuneSnapshot.finalAdoptedFrequencyHz()
                + " effective=" + retuneSnapshot.effectiveTrackedToneFrequencyHz()
                + " prev=" + retuneSnapshot.previousTargetBeforeScanFrequencyHz()
                + " prevTone=" + retuneSnapshot.previousTargetBeforeScanToneRms()
                + " acq=" + retuneSnapshot.acquisitionWinnerFrequencyHz()
                + " source=" + retuneSnapshot.finalAdoptedSource()
                + " guard=" + retuneSnapshot.lockedRetuneGuardHolding()
                + " scans=" + retuneSnapshot.lockedRetuneGuardObservedScans()
                + "/" + retuneSnapshot.lockedRetuneGuardRequiredScans()
                + " retuneEvents=" + renderToneEvents(retuneEvents);

        assertTrue(debug, overlapSnapshot.toneActive());
        assertTrue(debug, overlapSnapshot.lockedRetuneGuardHolding());
        assertTrue(debug, Math.abs(overlapSnapshot.targetToneFrequencyHz() - 690) <= 20);
        assertTrue(debug, Math.abs(overlapSnapshot.acquisitionWinnerFrequencyHz() - 620) <= 20);
        assertTrue(debug, overlapEvents.isEmpty());

        assertTrue(debug, retuneSnapshot.toneActive());
        assertTrue(debug, !retuneSnapshot.lockedRetuneGuardHolding());
        assertTrue(debug, Math.abs(retuneSnapshot.targetToneFrequencyHz() - 620) <= 20);
        assertTrue(debug, Math.abs(retuneSnapshot.finalAdoptedFrequencyHz() - 620) <= 20);
        assertTrue(debug, Math.abs(retuneSnapshot.effectiveTrackedToneFrequencyHz() - 620) <= 20);
        assertTrue(debug, retuneEvents.isEmpty());
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
        assertTrue("target=" + snapshot.targetToneFrequencyHz()
                        + " effective=" + snapshot.effectiveTrackedToneFrequencyHz()
                        + " final=" + snapshot.finalAdoptedFrequencyHz(),
                Math.abs(snapshot.targetToneFrequencyHz() - 710) <= 20);
        assertTrue("effective=" + snapshot.effectiveTrackedToneFrequencyHz(), Math.abs(snapshot.effectiveTrackedToneFrequencyHz() - 710) <= 20);
        assertTrue("final=" + snapshot.finalAdoptedFrequencyHz(), Math.abs(snapshot.finalAdoptedFrequencyHz() - 710) <= 20);
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
        assertTrue("final=" + snapshot.finalAdoptedFrequencyHz(), Math.abs(snapshot.finalAdoptedFrequencyHz() - 690) <= 20);
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
                        + " effective=" + snapshot.effectiveTrackedToneFrequencyHz()
                        + " final=" + snapshot.finalAdoptedFrequencyHz()
                        + " source=" + snapshot.finalAdoptedSource(),
                snapshot.targetToneLocked());
        assertTrue("target=" + snapshot.targetToneFrequencyHz(), Math.abs(snapshot.targetToneFrequencyHz() - 720) <= 20);
        assertTrue("effective=" + snapshot.effectiveTrackedToneFrequencyHz(), Math.abs(snapshot.effectiveTrackedToneFrequencyHz() - 720) <= 20);
        assertTrue("final=" + snapshot.finalAdoptedFrequencyHz(), Math.abs(snapshot.finalAdoptedFrequencyHz() - 720) <= 20);
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
                        + " effective=" + snapshot.effectiveTrackedToneFrequencyHz()
                        + " final=" + snapshot.finalAdoptedFrequencyHz()
                        + " wide=" + snapshot.wideScanWinnerFrequencyHz(),
                snapshot.targetToneLocked());
        assertTrue("target=" + snapshot.targetToneFrequencyHz(), Math.abs(snapshot.targetToneFrequencyHz() - 700) <= 20);
        assertTrue("effective=" + snapshot.effectiveTrackedToneFrequencyHz(), Math.abs(snapshot.effectiveTrackedToneFrequencyHz() - 700) <= 20);
        assertTrue("final=" + snapshot.finalAdoptedFrequencyHz(), Math.abs(snapshot.finalAdoptedFrequencyHz() - 700) <= 20);
    }

    @Test
    public void intermittentTrueToneWithContinuousFarInterfererDoesNotPromoteFarCarrierAsEffectiveLock() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(670);

        processNoisyFrames(processor, 8, 0.0d, 0.0d, 2100.0d, 0);

        int absoluteFrameIndex = 8;
        for (int cycle = 0; cycle < 6; cycle++) {
            processFramesCollecting(processor, 4, 670.0d, 16500.0d, 850.0d, 2100.0d, absoluteFrameIndex);
            absoluteFrameIndex += 4;
            processFramesCollecting(processor, 2, 0.0d, 0.0d, 850.0d, 2100.0d, absoluteFrameIndex);
            absoluteFrameIndex += 2;
        }
        processFramesCollecting(processor, 4, 670.0d, 16500.0d, 850.0d, 2100.0d, absoluteFrameIndex);

        CwSignalSnapshot snapshot = processor.snapshot();
        String debug = "target=" + snapshot.targetToneFrequencyHz()
                + " effective=" + snapshot.effectiveTrackedToneFrequencyHz()
                + " rep=" + snapshot.representativeLockedToneFrequencyHz()
                + " final=" + snapshot.finalAdoptedFrequencyHz()
                + " lock=" + snapshot.targetToneLocked()
                + " acq=" + snapshot.acquisitionWinnerFrequencyHz()
                + " src=" + snapshot.acquisitionWinnerSource()
                + " detail=" + snapshot.acquisitionDecisionDetail();

        assertTrue(debug, Math.abs(snapshot.effectiveTrackedToneFrequencyHz() - 670) <= 30);
        assertTrue(debug, Math.abs(snapshot.representativeLockedToneFrequencyHz() - 670) <= 30);
    }

    @Test
    public void startupContinuousNearbyInterfererDoesNotPreemptLaterTrueTargetLock() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(670);

        processFramesCollecting(processor, 8, 0.0d, 0.0d, 470.0d, 500.0d, 0);
        processFramesCollecting(processor, 8, 0.0d, 0.0d, 810.0d, 700.0d, 8);
        processFramesCollecting(processor, 6, 670.0d, 17800.0d, 810.0d, 700.0d, 16);

        CwSignalSnapshot snapshot = processor.snapshot();
        String debug = "target=" + snapshot.targetToneFrequencyHz()
                + " effective=" + snapshot.effectiveTrackedToneFrequencyHz()
                + " final=" + snapshot.finalAdoptedFrequencyHz()
                + " acq=" + snapshot.acquisitionWinnerFrequencyHz()
                + " src=" + snapshot.acquisitionWinnerSource()
                + " detail=" + snapshot.acquisitionDecisionDetail()
                + " lock=" + snapshot.targetToneLocked()
                + " tone=" + snapshot.lastToneRmsAmplitude();

        assertTrue(debug, snapshot.toneActive());
        assertTrue(debug, snapshot.targetToneLocked());
        assertTrue(debug, Math.abs(snapshot.targetToneFrequencyHz() - 670) <= 20);
        assertTrue(debug, Math.abs(snapshot.effectiveTrackedToneFrequencyHz() - 670) <= 30);
    }

    @Test
    public void postReleaseContinuousFarCarrierDoesNotFalseTriggerNewToneOn() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(670);

        processFramesCollecting(processor, 8, 0.0d, 0.0d, 0);
        processFramesCollecting(processor, 12, 670.0d, 17000.0d, 810.0d, 700.0d, 8);
        processFramesCollecting(processor, 3, 0.0d, 0.0d, 810.0d, 700.0d, 20);

        CwSignalSnapshot snapshot = processor.snapshot();
        String debug = "on=" + snapshot.totalToneOnEvents()
                + " off=" + snapshot.totalToneOffEvents()
                + " target=" + snapshot.targetToneFrequencyHz()
                + " effective=" + snapshot.effectiveTrackedToneFrequencyHz()
                + " final=" + snapshot.finalAdoptedFrequencyHz()
                + " acq=" + snapshot.acquisitionWinnerFrequencyHz()
                + " src=" + snapshot.acquisitionWinnerSource()
                + " detail=" + snapshot.acquisitionDecisionDetail()
                + " toneActive=" + snapshot.toneActive()
                + " lock=" + snapshot.targetToneLocked()
                + " last=" + (snapshot.lastEvent() == null
                ? "-"
                : snapshot.lastEvent().type() + "@" + snapshot.lastEvent().timestampMs()
                + "/" + snapshot.lastEvent().toneDurationMs());

        assertEquals(debug, 1, snapshot.totalToneOnEvents());
        assertEquals(debug, 1, snapshot.totalToneOffEvents());
        assertFalse(debug, snapshot.toneActive());
        assertTrue(debug, Math.abs(snapshot.effectiveTrackedToneFrequencyHz() - 670) <= 30);
    }

    @Test
    public void interWordFarCarrierDoesNotPreemptTrackedToneMemoryBeforeNextTrueTargetRun() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(670);

        processFramesCollecting(processor, 8, 0.0d, 0.0d, 0);
        processFramesCollecting(processor, 12, 670.0d, 17000.0d, 8);
        processFramesCollecting(processor, 14, 0.0d, 0.0d, 780.0d, 5600.0d, 20);
        processFramesCollecting(processor, 12, 670.0d, 17000.0d, 780.0d, 5600.0d, 34);

        CwSignalSnapshot snapshot = processor.snapshot();
        String debug = "on=" + snapshot.totalToneOnEvents()
                + " off=" + snapshot.totalToneOffEvents()
                + " target=" + snapshot.targetToneFrequencyHz()
                + " effective=" + snapshot.effectiveTrackedToneFrequencyHz()
                + " final=" + snapshot.finalAdoptedFrequencyHz()
                + " acq=" + snapshot.acquisitionWinnerFrequencyHz()
                + " src=" + snapshot.acquisitionWinnerSource()
                + " detail=" + snapshot.acquisitionDecisionDetail()
                + " toneActive=" + snapshot.toneActive()
                + " lock=" + snapshot.targetToneLocked()
                + " rep=" + snapshot.representativeLockedToneFrequencyHz()
                + "/" + snapshot.representativeLockedToneFrameCount()
                + " last=" + (snapshot.lastEvent() == null
                ? "-"
                : snapshot.lastEvent().type() + "@" + snapshot.lastEvent().timestampMs()
                + "/" + snapshot.lastEvent().toneDurationMs());

        assertTrue(debug, snapshot.toneActive());
        assertTrue(debug, snapshot.targetToneLocked());
        assertTrue(debug, Math.abs(snapshot.targetToneFrequencyHz() - 670) <= 20);
        assertTrue(debug, Math.abs(snapshot.effectiveTrackedToneFrequencyHz() - 670) <= 30);
        assertTrue(debug, snapshot.totalToneOnEvents() <= 2);
    }

    @Test
    public void coldStartFarBurstyCarrierNeedsConfirmationBeforeToneOn() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(670);

        processFramesCollecting(processor, 1, 0.0d, 0.0d, 820.0d, 1100.0d, 0);

        CwSignalSnapshot firstSnapshot = processor.snapshot();
        String firstDebug = "on=" + firstSnapshot.totalToneOnEvents()
                + " off=" + firstSnapshot.totalToneOffEvents()
                + " target=" + firstSnapshot.targetToneFrequencyHz()
                + " eff=" + firstSnapshot.effectiveTrackedToneFrequencyHz()
                + " tone=" + firstSnapshot.lastToneRmsAmplitude();
        assertEquals(firstDebug, 0, firstSnapshot.totalToneOnEvents());
        assertFalse(firstDebug, firstSnapshot.toneActive());

        processFramesCollecting(processor, 1, 0.0d, 0.0d, 820.0d, 1100.0d, 1);

        CwSignalSnapshot secondSnapshot = processor.snapshot();
        String secondDebug = "on=" + secondSnapshot.totalToneOnEvents()
                + " target=" + secondSnapshot.targetToneFrequencyHz()
                + " eff=" + secondSnapshot.effectiveTrackedToneFrequencyHz()
                + " tone=" + secondSnapshot.lastToneRmsAmplitude();
        assertTrue(secondDebug, secondSnapshot.totalToneOnEvents() >= 1);
    }

    @Test
    public void continuityModeDoesNotSoftSearchRetargetToFarWeakCarrier() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(670);

        processFramesCollecting(processor, 8, 0.0d, 0.0d, 0);
        processFramesCollecting(processor, 12, 670.0d, 17000.0d, 8);
        processFramesCollecting(processor, 6, 0.0d, 0.0d, 820.0d, 1100.0d, 20);

        CwSignalSnapshot snapshot = processor.snapshot();
        String debug = "target=" + snapshot.targetToneFrequencyHz()
                + " eff=" + snapshot.effectiveTrackedToneFrequencyHz()
                + " rep=" + snapshot.representativeLockedToneFrequencyHz()
                + "/" + snapshot.representativeLockedToneFrameCount()
                + " final=" + snapshot.finalAdoptedFrequencyHz()
                + " src=" + snapshot.finalAdoptedSource()
                + " detail=" + snapshot.finalAdoptionDetail();

        assertTrue(debug, Math.abs(snapshot.effectiveTrackedToneFrequencyHz() - 670) <= 30);
        assertTrue(debug, Math.abs(snapshot.targetToneFrequencyHz() - 670) <= 30);
    }

    @Test
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
                        + " effective=" + snapshot.effectiveTrackedToneFrequencyHz()
                        + " final=" + snapshot.finalAdoptedFrequencyHz()
                        + " winner=" + snapshot.acquisitionWinnerFrequencyHz()
                        + " source=" + snapshot.acquisitionWinnerSource(),
                snapshot.targetToneLocked());
        assertTrue("target=" + snapshot.targetToneFrequencyHz(), Math.abs(snapshot.targetToneFrequencyHz() - 800) <= 20);
        assertTrue("final=" + snapshot.finalAdoptedFrequencyHz(), Math.abs(snapshot.finalAdoptedFrequencyHz() - 800) <= 20);
        assertTrue("effective=" + snapshot.effectiveTrackedToneFrequencyHz(), Math.abs(snapshot.effectiveTrackedToneFrequencyHz() - 800) <= 20);
        assertTrue(snapshot.maxConsecutiveLockedFrames() >= 4);
    }

    @Test
    public void probeToneEventTimelineImpactWhenEffectiveTrackedLagsBehindTarget() {
        probeForcedToneTimelineComparison(
                "release-retune",
                650,
                buildReleaseAndRetuneFrames()
        );
        probeForcedToneTimelineComparison(
                "step-sweep",
                700,
                buildStepSweepFrames()
        );
    }

    @Test
    public void probeFrameByFrameRetuneAndSweepStateTransitions() {
        probeFrameByFrameStateTransitions("release-retune", 650, buildReleaseAndRetuneFrames());
        probeFrameByFrameStateTransitions("step-sweep", 700, buildStepSweepFrames());
    }

    @Test
    public void lockedRetuneCanMoveTargetAheadOfEffectiveWithoutExtraBoundaryChurn() {
        BaselineToneReplay baseline = collectBaselineToneReplay(650, buildReleaseAndRetuneFrames());

        assertEquals(renderToneEvents(baseline.events), 1, baseline.events.size());
        assertEquals(CwToneEvent.Type.TONE_ON, baseline.events.get(0).type());
        assertTrue("target=" + baseline.finalSnapshot.targetToneFrequencyHz(),
                Math.abs(baseline.finalSnapshot.targetToneFrequencyHz() - 620) <= 20);
        assertTrue("final=" + baseline.finalSnapshot.finalAdoptedFrequencyHz(),
                Math.abs(baseline.finalSnapshot.finalAdoptedFrequencyHz() - 620) <= 20);
        assertTrue("effective=" + baseline.finalSnapshot.effectiveTrackedToneFrequencyHz(),
                Math.abs(baseline.finalSnapshot.effectiveTrackedToneFrequencyHz() - 620) <= 20);
    }

    @Test
    public void stepSweepCanAdvanceTargetWhileEffectiveRemainsStableWithoutBoundaryChurn() {
        BaselineToneReplay baseline = collectBaselineToneReplay(700, buildStepSweepFrames());

        assertEquals(renderToneEvents(baseline.events), 1, baseline.events.size());
        assertEquals(CwToneEvent.Type.TONE_ON, baseline.events.get(0).type());
        assertTrue("target=" + baseline.finalSnapshot.targetToneFrequencyHz(),
                Math.abs(baseline.finalSnapshot.targetToneFrequencyHz() - 800) <= 20);
        assertTrue("final=" + baseline.finalSnapshot.finalAdoptedFrequencyHz(),
                Math.abs(baseline.finalSnapshot.finalAdoptedFrequencyHz() - 800) <= 20);
        assertTrue("effective=" + baseline.finalSnapshot.effectiveTrackedToneFrequencyHz(),
                Math.abs(baseline.finalSnapshot.effectiveTrackedToneFrequencyHz() - 800) <= 20);
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

    private CwSignalSnapshot runWeakValleySequence(double valleyAmplitude) {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setRxToneMode(CwSignalProcessor.RxToneMode.FIXED_TONE);
        processor.setPreferredToneFrequencyHz(650);

        processFramesCollecting(processor, 8, 0.0d, 0.0d, 0);
        processFramesCollecting(processor, 4, 670.0d, 16000.0d, 8);
        processFramesCollecting(processor, 2, 670.0d, valleyAmplitude, 12);
        processFramesCollecting(processor, 4, 670.0d, 16000.0d, 14);
        processFramesCollecting(processor, 4, 0.0d, 0.0d, 18);

        return processor.snapshot();
    }

    private CwSignalSnapshot runNoiseOnlySnapshot(
            int preferredToneHz,
            int sqlPercent,
            double noiseAmplitude,
            int frameCount
    ) {
        CwSignalProcessor processor = createProcessor(preferredToneHz, sqlPercent);
        processNoisyFrames(processor, frameCount, 0.0d, 0.0d, noiseAmplitude, 0);
        return processor.snapshot();
    }

    private CwSignalSnapshot runNoisyFrontEndSnapshot(
            int preferredToneHz,
            int sqlPercent,
            double toneHz,
            double toneAmplitude,
            double noiseAmplitude
    ) {
        CwSignalProcessor processor = createProcessor(preferredToneHz, sqlPercent);
        processNoisyFrames(processor, 8, 0.0d, 0.0d, 1800.0d, 0);
        processNoisyFrames(processor, 14, toneHz, toneAmplitude, noiseAmplitude, 8);
        return processor.snapshot();
    }

    private CwSignalProcessor createProcessor(int preferredToneHz, int sqlPercent) {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(preferredToneHz);
        processor.setSqlPercent(sqlPercent);
        return processor;
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

    private void probeForcedToneTimelineComparison(
            String label,
            int preferredToneHz,
            List<AudioFrame> frames
    ) {
        BaselineToneReplay baseline = collectBaselineToneReplay(preferredToneHz, frames);
        List<CwToneEvent> forcedTrackedEvents = replayForcedToneEvents(baseline, ForcedSnapshotTone.TRK);
        List<CwToneEvent> forcedEffectiveEvents = replayForcedToneEvents(baseline, ForcedSnapshotTone.EFF);

        int trackedMismatch = toneEventMismatchScore(baseline.events, forcedTrackedEvents);
        int effectiveMismatch = toneEventMismatchScore(baseline.events, forcedEffectiveEvents);

        System.out.println(
                "scenario=" + label
                        + "\nfinal target=" + baseline.finalSnapshot.targetToneFrequencyHz()
                        + " effective=" + baseline.finalSnapshot.effectiveTrackedToneFrequencyHz()
                        + " final=" + baseline.finalSnapshot.finalAdoptedFrequencyHz()
                        + " source=" + baseline.finalSnapshot.finalAdoptedSource()
                        + "\nactual=" + renderToneEvents(baseline.events)
                        + "\ntrk=" + renderToneEvents(forcedTrackedEvents)
                        + "\neff=" + renderToneEvents(forcedEffectiveEvents)
                        + "\ntrkMismatch=" + trackedMismatch
                        + " effMismatch=" + effectiveMismatch
        );

        assertTrue(label, !baseline.events.isEmpty());
        assertTrue(label + " trackedMismatch=" + trackedMismatch + " effMismatch=" + effectiveMismatch,
                effectiveMismatch <= trackedMismatch);
    }

    private void probeFrameByFrameStateTransitions(
            String label,
            int preferredToneHz,
            List<AudioFrame> frames
    ) {
        probeFrameByFrameStateTransitions(label, preferredToneHz, 0, frames);
    }

    private void probeFrameByFrameStateTransitions(
            String label,
            int preferredToneHz,
            int silentWarmupFrames,
            List<AudioFrame> frames
    ) {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(preferredToneHz);
        if (silentWarmupFrames > 0) {
            processFrames(processor, silentWarmupFrames, 0.0d, 0.0d);
        }

        System.out.println("scenario=" + label + " frame-by-frame");
        for (int index = 0; index < frames.size(); index++) {
            AudioFrame frame = frames.get(index);
            CwSignalSnapshot before = processor.snapshot();
            List<CwToneEvent> events = processor.process(frame);
            CwSignalSnapshot after = processor.snapshot();
            System.out.println(
                    "f=" + index
                            + " ts=" + frame.capturedAtMs()
                            + " before[target=" + before.targetToneFrequencyHz()
                            + " eff=" + before.effectiveTrackedToneFrequencyHz()
                            + " rep=" + before.representativeLockedToneFrequencyHz()
                            + " repFrames=" + before.representativeLockedToneFrameCount()
                            + " toneActive=" + before.toneActive()
                            + " locked=" + before.targetToneLocked()
                            + " prev=" + before.previousTargetBeforeScanFrequencyHz()
                            + " prevTone=" + before.previousTargetBeforeScanToneRms()
                            + " prevScore=" + before.previousTargetBeforeScanSelectionScore()
                            + "] after[target=" + after.targetToneFrequencyHz()
                            + " eff=" + after.effectiveTrackedToneFrequencyHz()
                            + " rep=" + after.representativeLockedToneFrequencyHz()
                            + " repFrames=" + after.representativeLockedToneFrameCount()
                            + " final=" + after.finalAdoptedFrequencyHz()
                            + " source=" + after.finalAdoptedSource()
                            + " acq=" + after.acquisitionWinnerFrequencyHz()
                            + " acqScore=" + after.acquisitionWinnerSelectionScore()
                            + " acqLocked=" + after.acquisitionWinnerLocked()
                            + " pref=" + after.preferredWindowWinnerFrequencyHz()
                            + " wide=" + after.wideScanWinnerFrequencyHz()
                            + " prefTop=" + after.preferredWindowTopCandidatesSummary()
                            + " wideTop=" + after.wideScanTopCandidatesSummary()
                            + " prev=" + after.previousTargetBeforeScanFrequencyHz()
                            + " prevTone=" + after.previousTargetBeforeScanToneRms()
                            + " prevScore=" + after.previousTargetBeforeScanSelectionScore()
                            + " finalScore=" + after.finalAdoptedSelectionScore()
                            + " guard=" + after.lockedRetuneGuardHolding()
                            + " guardDrift=" + after.lockedRetuneGuardDriftHz()
                            + " scans=" + after.lockedRetuneGuardObservedScans()
                            + "/" + after.lockedRetuneGuardRequiredScans()
                            + " toneActive=" + after.toneActive()
                            + " locked=" + after.targetToneLocked()
                            + "] events=" + renderToneEvents(events)
            );
        }
    }

    private BaselineToneReplay collectBaselineToneReplay(
            int preferredToneHz,
            List<AudioFrame> frames
    ) {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(preferredToneHz);

        List<CwSignalSnapshot> snapshotsBeforeFrame = new java.util.ArrayList<>();
        List<CwToneEvent> events = new java.util.ArrayList<>();
        for (AudioFrame frame : frames) {
            snapshotsBeforeFrame.add(processor.snapshot());
            events.addAll(processor.process(frame));
        }
        return new BaselineToneReplay(frames, snapshotsBeforeFrame, events, processor.snapshot());
    }

    private List<CwToneEvent> replayForcedToneEvents(
            BaselineToneReplay baseline,
            ForcedSnapshotTone mode
    ) {
        CwSignalProcessor processor = new CwSignalProcessor();
        int lastForcedToneHz = 650;
        List<CwToneEvent> events = new java.util.ArrayList<>();
        for (int index = 0; index < baseline.frames.size(); index++) {
            CwSignalSnapshot snapshot = baseline.snapshotsBeforeFrame.get(index);
            int forcedToneHz;
            if (mode == ForcedSnapshotTone.EFF) {
                forcedToneHz = snapshot.effectiveTrackedToneFrequencyHz() > 0
                        ? snapshot.effectiveTrackedToneFrequencyHz()
                        : (snapshot.targetToneFrequencyHz() > 0
                        ? snapshot.targetToneFrequencyHz()
                        : lastForcedToneHz);
            } else {
                forcedToneHz = snapshot.targetToneFrequencyHz() > 0
                        ? snapshot.targetToneFrequencyHz()
                        : lastForcedToneHz;
            }
            lastForcedToneHz = forcedToneHz;
            events.addAll(processor.processForcedToneForTesting(baseline.frames.get(index), forcedToneHz));
        }
        return events;
    }

    private List<AudioFrame> buildReleaseAndRetuneFrames() {
        List<AudioFrame> frames = new java.util.ArrayList<>();
        appendFrames(frames, 8, 0.0d, 0.0d, 0.0d, 0.0d, 0);
        appendFrames(frames, 10, 700.0d, 15000.0d, 0.0d, 0.0d, 8);
        appendFrames(frames, 1, 700.0d, 15000.0d, 620.0d, 22000.0d, 18);
        appendFrames(frames, 6, 620.0d, 22000.0d, 0.0d, 0.0d, 19);
        return frames;
    }

    private List<AudioFrame> buildStepSweepFrames() {
        List<AudioFrame> frames = new java.util.ArrayList<>();
        appendFrames(frames, 8, 0.0d, 0.0d, 0.0d, 0.0d, 0);
        appendFrames(frames, 4, 700.0d, 15000.0d, 0.0d, 0.0d, 8);
        appendFrames(frames, 4, 600.0d, 15000.0d, 0.0d, 0.0d, 12);
        appendFrames(frames, 4, 650.0d, 15000.0d, 0.0d, 0.0d, 16);
        appendFrames(frames, 4, 750.0d, 15000.0d, 0.0d, 0.0d, 20);
        appendFrames(frames, 4, 800.0d, 15000.0d, 0.0d, 0.0d, 24);
        return frames;
    }

    private List<AudioFrame> buildSweepingNearbyCarrierFrames() {
        List<AudioFrame> frames = new java.util.ArrayList<>();
        for (int frameIndex = 0; frameIndex < 12; frameIndex++) {
            double sweepingFrequencyHz = 820.0d - (frameIndex * 10.0d);
            frames.add(buildFrame(
                    (8 + frameIndex) * frameDurationMs(),
                    (8 + frameIndex) * FRAME_SIZE,
                    670.0d,
                    15000.0d,
                    sweepingFrequencyHz,
                    18000.0d
            ));
        }
        return frames;
    }

    private void appendFrames(
            List<AudioFrame> frames,
            int frameCount,
            double primaryFrequencyHz,
            double primaryAmplitude,
            double secondaryFrequencyHz,
            double secondaryAmplitude,
            int frameStartIndex
    ) {
        for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
            int absoluteFrameIndex = frameStartIndex + frameIndex;
            frames.add(buildFrame(
                    absoluteFrameIndex * frameDurationMs(),
                    absoluteFrameIndex * FRAME_SIZE,
                    primaryFrequencyHz,
                    primaryAmplitude,
                    secondaryFrequencyHz,
                    secondaryAmplitude
            ));
        }
    }

    private int toneEventMismatchScore(List<CwToneEvent> expected, List<CwToneEvent> actual) {
        int score = Math.abs(expected.size() - actual.size()) * 1000;
        int pairCount = Math.min(expected.size(), actual.size());
        for (int index = 0; index < pairCount; index++) {
            CwToneEvent left = expected.get(index);
            CwToneEvent right = actual.get(index);
            if (left.type() != right.type()) {
                score += 500;
            }
            score += Math.abs((int) (left.timestampMs() - right.timestampMs()));
            score += Math.abs((int) (left.toneDurationMs() - right.toneDurationMs()));
        }
        return score;
    }

    private String renderToneEvents(List<CwToneEvent> events) {
        if (events.isEmpty()) {
            return "(none)";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < events.size(); index++) {
            if (index > 0) {
                builder.append(" | ");
            }
            CwToneEvent event = events.get(index);
            builder.append(event.type())
                    .append("@")
                    .append(event.timestampMs())
                    .append("/")
                    .append(event.toneDurationMs());
        }
        return builder.toString();
    }

    private enum ForcedSnapshotTone {
        TRK,
        EFF
    }

    private static final class BaselineToneReplay {
        private final List<AudioFrame> frames;
        private final List<CwSignalSnapshot> snapshotsBeforeFrame;
        private final List<CwToneEvent> events;
        private final CwSignalSnapshot finalSnapshot;

        private BaselineToneReplay(
                List<AudioFrame> frames,
                List<CwSignalSnapshot> snapshotsBeforeFrame,
                List<CwToneEvent> events,
                CwSignalSnapshot finalSnapshot
        ) {
            this.frames = frames;
            this.snapshotsBeforeFrame = snapshotsBeforeFrame;
            this.events = events;
            this.finalSnapshot = finalSnapshot;
        }
    }
}
