package org.bi9clt.cwcn.core.eval;

import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class CwFrontEndHealthClassifierTest {
    @Test
    public void classifiesWrongToneLockAsTrackingProblem() {
        CwSignalSnapshot snapshot = snapshot(
                true,
                true,
                650,
                730,
                8200.0d,
                0.72d,
                0.34d,
                7,
                0.02d,
                1,
                null
        );

        assertEquals("WRONG", CwFrontEndHealthClassifier.qualityCode(snapshot));
        assertEquals("TRK", CwFrontEndHealthClassifier.bottleneckCode(snapshot));
        assertTrue(CwFrontEndHealthClassifier.reason(snapshot).contains("wrong-tone"));
    }

    @Test
    public void classifiesHealthyLockedFrontEndAsGood() {
        CwSignalSnapshot snapshot = snapshot(
                true,
                true,
                650,
                660,
                7800.0d,
                0.68d,
                0.28d,
                6,
                0.0d,
                0,
                null
        );

        assertEquals("GOOD", CwFrontEndHealthClassifier.qualityCode(snapshot));
        assertEquals("OK", CwFrontEndHealthClassifier.bottleneckCode(snapshot));
        assertEquals("Healthy lock retained", CwFrontEndHealthClassifier.qualityLabel(snapshot));
    }

    @Test
    public void classifiesCleanReleaseAsGood() {
        CwSignalSnapshot snapshot = snapshot(
                false,
                false,
                650,
                650,
                7600.0d,
                0.66d,
                0.26d,
                5,
                0.05d,
                1,
                new CwToneEvent(CwToneEvent.Type.TONE_OFF, 120L, 80, 4200.0d, 120L)
        );

        assertEquals("GOOD", CwFrontEndHealthClassifier.qualityCode(snapshot));
        assertEquals("Healthy lock with clean release", CwFrontEndHealthClassifier.qualityLabel(snapshot));
    }

    @Test
    public void classifiesEarlierLockThenDropAsSignalProblem() {
        CwSignalSnapshot snapshot = snapshot(
                false,
                false,
                650,
                655,
                7900.0d,
                0.60d,
                0.18d,
                4,
                0.22d,
                3,
                new CwToneEvent(CwToneEvent.Type.TONE_ON, 100L, 80, 4100.0d, 100L)
        );

        assertEquals("DROP", CwFrontEndHealthClassifier.qualityCode(snapshot));
        assertEquals("SIG", CwFrontEndHealthClassifier.bottleneckCode(snapshot));
    }

    @Test
    public void classifiesNoConvincingLockAsMiss() {
        CwSignalSnapshot snapshot = snapshot(
                false,
                false,
                650,
                650,
                3400.0d,
                0.22d,
                0.04d,
                1,
                0.0d,
                0,
                null
        );

        assertEquals("MISS", CwFrontEndHealthClassifier.qualityCode(snapshot));
        assertEquals("SIG", CwFrontEndHealthClassifier.bottleneckCode(snapshot));
    }

    @Test
    public void reportsNoHistoryWhenSnapshotIsStillEmpty() {
        CwSignalSnapshot snapshot = snapshot(
                false,
                false,
                650,
                650,
                0.0d,
                0.0d,
                0.0d,
                0,
                0.0d,
                0,
                null
        );

        assertEquals("NA", CwFrontEndHealthClassifier.qualityCode(snapshot));
        assertEquals("NA", CwFrontEndHealthClassifier.bottleneckCode(snapshot));
        assertEquals("No front-end history available", CwFrontEndHealthClassifier.qualityLabel(snapshot));
    }

    private static CwSignalSnapshot snapshot(
            boolean toneActive,
            boolean targetToneLocked,
            int preferredToneFrequencyHz,
            int targetToneFrequencyHz,
            double peakToneRmsAmplitude,
            double peakNarrowbandIsolationRatio,
            double lockedFrameRatio,
            int maxConsecutiveLockedFrames,
            double toneActiveUnlockedFrameRatio,
            int maxConsecutiveToneActiveUnlockedFrames,
            CwToneEvent lastEvent
    ) {
        int processedFrameCount = 100;
        int toneActiveFrameCount = 100;
        return new CwSignalSnapshot(
                toneActive,
                targetToneLocked,
                preferredToneFrequencyHz,
                targetToneFrequencyHz,
                1200,
                800,
                600,
                1800,
                peakToneRmsAmplitude,
                peakToneRmsAmplitude * 0.75d,
                peakToneRmsAmplitude * 0.15d,
                0.55d,
                peakNarrowbandIsolationRatio * 0.8d,
                peakToneRmsAmplitude,
                peakNarrowbandIsolationRatio,
                processedFrameCount,
                (int) Math.round(processedFrameCount * lockedFrameRatio),
                toneActiveFrameCount,
                (int) Math.round(toneActiveFrameCount * toneActiveUnlockedFrameRatio),
                Math.max(0, maxConsecutiveLockedFrames - 1),
                maxConsecutiveLockedFrames,
                Math.max(0, maxConsecutiveToneActiveUnlockedFrames - 1),
                maxConsecutiveToneActiveUnlockedFrames,
                targetToneFrequencyHz,
                0,
                3,
                3,
                lastEvent
        );
    }
}
