package org.bi9clt.cwcn.core.rx;

import org.bi9clt.cwcn.core.audio.AudioFrame;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class RxPendingCharacterFlushDeciderTest {
    @Test
    public void resolveFrameEndTimestampUsesFrameDurationOrFallback() {
        AudioFrame frame = new AudioFrame(new short[80], 8000, 1, 0, 0.0d, 1000L);

        assertEquals(1010L, RxPendingCharacterFlushDecider.resolveFrameEndTimestampMs(frame, 7L));
        assertEquals(7L, RxPendingCharacterFlushDecider.resolveFrameEndTimestampMs(null, 7L));
    }

    @Test
    public void flushesWhenTrailingToneOffHasEnoughSilence() {
        AudioFrame frame = new AudioFrame(new short[800], 8000, 1, 0, 0.0d, 1000L);
        CwSignalSnapshot snapshot = signalSnapshot(
                false,
                false,
                0.08d,
                0.18d,
                540.0d,
                800,
                600,
                700,
                0,
                0,
                new CwToneEvent(CwToneEvent.Type.TONE_OFF, 900L, 0, 0.0d, 60L)
        );

        RxPendingCharacterFlushDecider.Decision decision = RxPendingCharacterFlushDecider.evaluate(
                frame,
                0L,
                snapshot,
                120L,
                RxPendingCharacterFlushDecider.ActivityPolicy.TONE_ACTIVE
        );

        assertTrue(decision.shouldFlush());
        assertEquals(1100L, decision.flushTimestampMs());
        assertEquals(200L, decision.silentGapMs());
    }

    @Test
    public void meaningfulTurnPolicyCanIgnoreWeakBridgedToneActiveState() {
        AudioFrame frame = new AudioFrame(new short[800], 8000, 1, 0, 0.0d, 1000L);
        CwSignalSnapshot weakBridgedSnapshot = signalSnapshot(
                true,
                false,
                0.05d,
                0.10d,
                520.0d,
                900,
                500,
                700,
                0,
                0,
                new CwToneEvent(CwToneEvent.Type.TONE_OFF, 900L, 0, 0.0d, 60L)
        );

        RxPendingCharacterFlushDecider.Decision toneActiveDecision = RxPendingCharacterFlushDecider.evaluate(
                frame,
                1100L,
                weakBridgedSnapshot,
                120L,
                RxPendingCharacterFlushDecider.ActivityPolicy.TONE_ACTIVE
        );
        RxPendingCharacterFlushDecider.Decision meaningfulDecision = RxPendingCharacterFlushDecider.evaluate(
                frame,
                1100L,
                weakBridgedSnapshot,
                120L,
                RxPendingCharacterFlushDecider.ActivityPolicy.MEANINGFUL_TURN_ACTIVITY
        );

        assertFalse(toneActiveDecision.shouldFlush());
        assertEquals("signal-active", toneActiveDecision.reason());
        assertTrue(meaningfulDecision.shouldFlush());
    }

    private static CwSignalSnapshot signalSnapshot(
            boolean toneActive,
            boolean targetToneLocked,
            double toneDominanceRatio,
            double narrowbandIsolationRatio,
            double lastToneRmsAmplitude,
            int releaseThreshold,
            int noiseFloorEstimate,
            int signalFloorEstimate,
            int consecutiveLockedFrames,
            int lockedFrameCount,
            CwToneEvent lastEvent
    ) {
        int recentHistoryFrameCount = 10;
        char[] recentFrontEndStateHistory = new char[recentHistoryFrameCount];
        int[] recentTrackingOffsetHistoryHz = new int[recentHistoryFrameCount];
        for (int index = 0; index < recentHistoryFrameCount; index++) {
            recentFrontEndStateHistory[index] = toneActive ? 'u' : '-';
            recentTrackingOffsetHistoryHz[index] = 0;
        }
        int toneActiveFrameCount = toneActive ? 100 : 0;
        int toneActiveUnlockedFrameCount = toneActive ? 20 : 0;
        return new CwSignalSnapshot(
                recentHistoryFrameCount,
                recentFrontEndStateHistory,
                recentTrackingOffsetHistoryHz,
                toneActive,
                targetToneLocked,
                700,
                700,
                700,
                Math.max(4, lockedFrameCount),
                1200,
                releaseThreshold,
                noiseFloorEstimate,
                signalFloorEstimate,
                2000.0d,
                lastToneRmsAmplitude,
                900.0d,
                toneDominanceRatio,
                narrowbandIsolationRatio,
                lastToneRmsAmplitude,
                narrowbandIsolationRatio,
                100,
                lockedFrameCount,
                toneActiveFrameCount,
                toneActiveUnlockedFrameCount,
                consecutiveLockedFrames,
                Math.max(consecutiveLockedFrames, lockedFrameCount),
                toneActive ? 2 : 0,
                toneActive ? 4 : 0,
                700,
                0,
                700,
                700,
                700,
                700,
                7000.0d,
                7000.0d,
                7000.0d,
                7000.0d,
                6500.0d,
                6500.0d,
                6500.0d,
                6500.0d,
                targetToneLocked,
                false,
                targetToneLocked,
                targetToneLocked,
                "PREFERRED_WINDOW",
                "LOCKED_RETUNE",
                toneActive ? 3 : 0,
                3,
                0,
                0L,
                0L,
                0L,
                -1L,
                lastEvent
        );
    }
}
