package org.bi9clt.cwcn.core.rx;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.bi9clt.cwcn.core.audio.AudioInputHealthSnapshot;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.junit.Test;

public final class CwFrontEndLearningGateTest {
    private final CwFrontEndLearningGate gate = new CwFrontEndLearningGate();

    @Test
    public void shouldAllowTimingLearningForEvent_allowsTrustedHotToneOnWhenShapeIsStrong() {
        CwSignalSnapshot signalSnapshot = signalSnapshot(
                true,
                true,
                0.99d,
                0.86d,
                repeatedHistory('L', 14, '.', 10)
        );
        AudioInputHealthSnapshot inputHealthSnapshot = inputHealthSnapshot(repeatedHistory('H', 12, 'G', 12));
        CwToneEvent toneEvent = new CwToneEvent(CwToneEvent.Type.TONE_ON, 18651L, 21014, 12531.2d, 0L);

        assertFalse(gate.shouldAllowTimingLearning(signalSnapshot, inputHealthSnapshot));
        assertTrue(gate.shouldAllowTimingLearningForEvent(
                toneEvent,
                signalSnapshot,
                inputHealthSnapshot,
                true
        ));
    }

    @Test
    public void shouldAllowTimingLearningForEvent_allowsTrustedHotToneOffWhenRecentLockRemainsStrong() {
        CwSignalSnapshot signalSnapshot = signalSnapshot(
                false,
                false,
                0.11d,
                0.10d,
                repeatedHistory('L', 15, '.', 9)
        );
        AudioInputHealthSnapshot inputHealthSnapshot = inputHealthSnapshot(repeatedHistory('H', 13, 'G', 11));
        CwToneEvent toneEvent = new CwToneEvent(CwToneEvent.Type.TONE_OFF, 19458L, 3196, 196.2d, 140L);

        assertFalse(gate.shouldAllowTimingLearning(signalSnapshot, inputHealthSnapshot));
        assertTrue(gate.shouldAllowTimingLearningForEvent(
                toneEvent,
                signalSnapshot,
                inputHealthSnapshot,
                true
        ));
    }

    @Test
    public void shouldAllowTimingLearningForEvent_blocksHotOverrideBeforeTrust() {
        CwSignalSnapshot signalSnapshot = signalSnapshot(
                true,
                true,
                0.99d,
                0.86d,
                repeatedHistory('L', 14, '.', 10)
        );
        AudioInputHealthSnapshot inputHealthSnapshot = inputHealthSnapshot(repeatedHistory('H', 12, 'G', 12));
        CwToneEvent toneEvent = new CwToneEvent(CwToneEvent.Type.TONE_ON, 18651L, 21014, 12531.2d, 0L);

        assertFalse(gate.shouldAllowTimingLearningForEvent(
                toneEvent,
                signalSnapshot,
                inputHealthSnapshot,
                false
        ));
    }

    @Test
    public void shouldAllowTimingLearningForEvent_blocksHotOverrideWhenClippingPresent() {
        CwSignalSnapshot signalSnapshot = signalSnapshot(
                true,
                true,
                0.99d,
                0.86d,
                repeatedHistory('L', 14, '.', 10)
        );
        AudioInputHealthSnapshot inputHealthSnapshot = inputHealthSnapshot(
                repeatedHistory('H', 11, 'C', 3, 'G', 10)
        );
        CwToneEvent toneEvent = new CwToneEvent(CwToneEvent.Type.TONE_ON, 18651L, 21014, 12531.2d, 0L);

        assertFalse(gate.shouldAllowTimingLearningForEvent(
                toneEvent,
                signalSnapshot,
                inputHealthSnapshot,
                true
        ));
    }

    @Test
    public void shouldAllowTimingLearningForEvent_allowsPreTrustLockedHealthyEventLearning() {
        CwSignalSnapshot signalSnapshot = signalSnapshot(
                true,
                true,
                0.99d,
                0.86d,
                repeatedHistory('L', 18, '.', 6)
        );
        AudioInputHealthSnapshot inputHealthSnapshot = inputHealthSnapshot(repeatedHistory('H', 12, 'G', 12));
        CwToneEvent toneEvent = new CwToneEvent(CwToneEvent.Type.TONE_ON, 4493L, 6400, 1320.0d, 0L);

        assertFalse(gate.shouldAllowTimingLearning(signalSnapshot, inputHealthSnapshot));
        assertTrue(gate.shouldAllowTimingLearningForEvent(
                toneEvent,
                signalSnapshot,
                inputHealthSnapshot,
                false
        ));
    }

    public void shouldAllowStableAnchorUpdate_allowsTrustedQuietGapEdgeCarry() {
        CwSignalSnapshot signalSnapshot = signalSnapshot(
                false,
                false,
                0.04d,
                0.04d,
                repeatedHistory('L', 14, '.', 10)
        );

        assertTrue(gate.shouldAllowStableAnchorUpdate(
                signalSnapshot,
                inputHealthSnapshot(repeatedHistory('G', 24, '\0', 0)),
                true
        ));
    }

    @Test
    public void shouldAllowStableAnchorUpdate_allowsTrustedHotCurrentLockWhenShapeStillPasses() {
        CwSignalSnapshot signalSnapshot = signalSnapshot(
                false,
                true,
                0.87d,
                0.64d,
                repeatedHistory('L', 14, '.', 10)
        );

        assertTrue(gate.shouldAllowStableAnchorUpdate(
                signalSnapshot,
                inputHealthSnapshot(repeatedHistory('H', 12, 'G', 12)),
                true
        ));
    }

    private static CwSignalSnapshot signalSnapshot(
            boolean toneActive,
            boolean targetToneLocked,
            double toneDominanceRatio,
            double narrowbandIsolationRatio,
            char[] recentFrontEndStateHistory
    ) {
        int recentHistoryFrameCount = recentFrontEndStateHistory.length;
        int[] recentTrackingOffsetHistoryHz = new int[recentHistoryFrameCount];
        int recentLockedFrameCount = 0;
        int recentActiveUnlockedFrameCount = 0;
        for (char stateCode : recentFrontEndStateHistory) {
            if (stateCode == 'L' || stateCode == 'l') {
                recentLockedFrameCount += 1;
            } else if (stateCode == 'u') {
                recentActiveUnlockedFrameCount += 1;
            }
        }
        return new CwSignalSnapshot(
                recentHistoryFrameCount,
                recentFrontEndStateHistory,
                recentTrackingOffsetHistoryHz,
                toneActive,
                targetToneLocked,
                700,
                700,
                700,
                Math.max(4, recentLockedFrameCount),
                1200,
                800,
                600,
                1800,
                7800.0d,
                6200.0d,
                1100.0d,
                toneDominanceRatio,
                narrowbandIsolationRatio,
                7800.0d,
                narrowbandIsolationRatio,
                100,
                Math.max(0, recentLockedFrameCount * 10),
                100,
                Math.max(0, recentActiveUnlockedFrameCount * 10),
                Math.max(0, recentLockedFrameCount - 1),
                Math.max(4, recentLockedFrameCount),
                Math.max(0, recentActiveUnlockedFrameCount - 1),
                Math.max(0, recentActiveUnlockedFrameCount),
                700,
                0,
                700,
                0,
                700,
                700,
                7000.0d,
                0.0d,
                7800.0d,
                7800.0d,
                6500.0d,
                0.0d,
                7800.0d,
                7800.0d,
                targetToneLocked,
                false,
                targetToneLocked,
                targetToneLocked,
                "PREFERRED_WINDOW",
                "LOCKED_RETUNE",
                3,
                3,
                0,
                0L,
                0L,
                0L,
                -1L,
                null
        );
    }

    private static AudioInputHealthSnapshot inputHealthSnapshot(char[] recentStateHistory) {
        return new AudioInputHealthSnapshot(
                recentStateHistory.length,
                recentStateHistory,
                100,
                12000,
                4200.0d,
                0.0d
        );
    }

    private static char[] repeatedHistory(char stateCode, int count, char trailingCode, int trailingCount) {
        return repeatedHistory(stateCode, count, trailingCode, trailingCount, '\0', 0);
    }

    private static char[] repeatedHistory(
            char firstCode,
            int firstCount,
            char secondCode,
            int secondCount,
            char thirdCode,
            int thirdCount
    ) {
        char[] history = new char[firstCount + secondCount + thirdCount];
        int index = 0;
        for (int remaining = 0; remaining < firstCount; remaining++) {
            history[index++] = firstCode;
        }
        for (int remaining = 0; remaining < secondCount; remaining++) {
            history[index++] = secondCode;
        }
        for (int remaining = 0; remaining < thirdCount; remaining++) {
            history[index++] = thirdCode;
        }
        return history;
    }
}
