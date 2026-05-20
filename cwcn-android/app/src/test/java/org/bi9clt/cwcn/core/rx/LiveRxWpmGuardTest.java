package org.bi9clt.cwcn.core.rx;

import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LiveRxWpmGuardTest {
    @Test
    public void startupCanMoveAwayFromSeedBeforeTrustedExists() {
        LiveRxWpmGuard guard = new LiveRxWpmGuard();
        guard.setSeedWpm(24);

        CwSignalSnapshot strongSignal = signalSnapshot(
                true,
                "LLLLLLLLLLLLLLLLLLLLLLLL",
                6,
                0.58d,
                0.66d
        );
        CwTimingSnapshot rawTiming = timingSnapshot(33.0d);

        int displayWpm = guard.resolveDisplayWpm(strongSignal, rawTiming, 1200L);
        assertEquals(33, displayWpm);
        assertFalse(guard.holding());
    }

    @Test
    public void startupWeakConfidenceCanFollowRawTimingBeforeTrustExists() {
        LiveRxWpmGuard guard = new LiveRxWpmGuard();
        guard.setSeedWpm(24);

        CwSignalSnapshot weakSignal = signalSnapshot(
                false,
                "uuuuuuuuuuuu........LLLL",
                60,
                0.20d,
                0.26d
        );
        CwTimingSnapshot runawayTiming = timingSnapshot(40.0d);

        int displayWpm = guard.resolveDisplayWpm(weakSignal, runawayTiming, 1200L);
        assertEquals(40, displayWpm);
        assertFalse(guard.holding());
    }

    @Test
    public void startupStrongConfidenceDoesNotCapRawTimingBeforeTrustExists() {
        LiveRxWpmGuard guard = new LiveRxWpmGuard();
        guard.setSeedWpm(15);

        CwSignalSnapshot strongSignal = signalSnapshot(
                true,
                "LLLLLLLLLLLLLLLLLLLLLLLL",
                6,
                0.58d,
                0.66d
        );

        int displayWpm = guard.resolveDisplayWpm(strongSignal, timingSnapshot(31.0d), 1200L);
        assertEquals(31, displayWpm);
    }

    @Test
    public void startupBootstrapAnchorUpdateUsesRawWpmBeforeTrustExists() {
        LiveRxWpmGuard guard = new LiveRxWpmGuard();
        guard.setSeedWpm(15);

        CwSignalSnapshot strongSignal = signalSnapshot(
                true,
                "LLLLLLLLLLLLLLLLLLLLLLLL",
                6,
                0.58d,
                0.66d
        );

        assertTrue(guard.shouldAcceptTimingAnchorUpdate(strongSignal, timingSnapshot(31.0d), 1200L));

        guard.noteDecodedCharacter(false, strongSignal, timingSnapshot(31.0d), 1000L);
        guard.noteDecodedCharacter(false, strongSignal, timingSnapshot(30.5d), 1200L);
        guard.noteDecodedCharacter(false, strongSignal, timingSnapshot(31.0d), 1400L);

        assertEquals(31, guard.resolveReferenceWpm(timingSnapshot(31.0d)));
    }

    @Test
    public void startupBootstrapCanUseUnknownCharactersBeforeTrustExists() {
        LiveRxWpmGuard guard = new LiveRxWpmGuard();
        guard.setSeedWpm(15);

        CwSignalSnapshot strongSignal = signalSnapshot(
                true,
                "LLLLLLLLLLLLLLLLLLLLLLLL",
                6,
                0.58d,
                0.66d
        );

        guard.noteDecodedCharacter(true, strongSignal, timingSnapshot(26.0d), 1000L);
        guard.noteDecodedCharacter(true, strongSignal, timingSnapshot(25.5d), 1200L);
        guard.noteDecodedCharacter(true, strongSignal, timingSnapshot(26.0d), 1400L);

        assertTrue(guard.resolveReferenceWpm(timingSnapshot(26.0d)) >= 25);
    }

    @Test
    public void startupTimingEventIsNotRetargetedBeforeTrustedWpmExists() {
        LiveRxWpmGuard guard = new LiveRxWpmGuard();
        guard.setSeedWpm(24);

        CwSignalSnapshot strongSignal = signalSnapshot(
                true,
                "LLLLLLLLLLLLLLLLLLLLLLLL",
                6,
                0.58d,
                0.66d
        );
        CwTimingSnapshot slowTiming = timingSnapshot(9.3d);
        CwTimingEvent rawTimingEvent = new CwTimingEvent(
                CwTimingEvent.Kind.TONE,
                CwTimingEvent.Classification.UNKNOWN,
                464L,
                464L,
                80L,
                80L
        );

        CwTimingEvent adaptedTimingEvent = guard.adaptTimingEvent(
                rawTimingEvent,
                strongSignal,
                slowTiming,
                464L
        );

        assertEquals(rawTimingEvent.classification(), adaptedTimingEvent.classification());
        assertEquals(rawTimingEvent.dotEstimateMs(), adaptedTimingEvent.dotEstimateMs());
        assertEquals(rawTimingEvent.intraGapEstimateMs(), adaptedTimingEvent.intraGapEstimateMs());
    }

    @Test
    public void trustedWpmBuildsFromStrongDecodeRun() {
        LiveRxWpmGuard guard = new LiveRxWpmGuard();
        guard.setSeedWpm(24);

        CwSignalSnapshot strongSignal = signalSnapshot(
                true,
                "LLLLLLLLLLLLLLLLLLLLLLLL",
                6,
                0.58d,
                0.66d
        );
        CwTimingSnapshot trustedTiming = timingSnapshot(24.0d);

        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1000L);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1200L);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1400L);

        int displayWpm = guard.resolveDisplayWpm(strongSignal, trustedTiming, 1500L);
        assertTrue(displayWpm >= 23);
        assertTrue(displayWpm <= 25);
        assertFalse(guard.holding());
    }

    @Test
    public void initialTrustedWpmUsesMedianAcrossFirstStrongRun() {
        LiveRxWpmGuard guard = new LiveRxWpmGuard();
        guard.setSeedWpm(24);

        CwSignalSnapshot strongSignal = signalSnapshot(
                true,
                "LLLLLLLLLLLLLLLLLLLLLLLL",
                8,
                0.58d,
                0.66d
        );

        guard.noteDecodedCharacter(false, strongSignal, timingSnapshot(24.0d), 1000L);
        guard.noteDecodedCharacter(false, strongSignal, timingSnapshot(31.0d), 1200L);
        guard.noteDecodedCharacter(false, strongSignal, timingSnapshot(24.0d), 1400L);

        int referenceWpm = guard.resolveReferenceWpm(timingSnapshot(31.0d));
        assertEquals(24, referenceWpm);
    }

    @Test
    public void weakConfidenceRunawayFastWpmGetsHeldNearTrustedValue() {
        LiveRxWpmGuard guard = new LiveRxWpmGuard();
        guard.setSeedWpm(24);

        CwSignalSnapshot strongSignal = signalSnapshot(
                true,
                "LLLLLLLLLLLLLLLLLLLLLLLL",
                8,
                0.58d,
                0.66d
        );
        CwTimingSnapshot trustedTiming = timingSnapshot(24.0d);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1000L);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1200L);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1400L);

        CwSignalSnapshot weakSignal = signalSnapshot(
                false,
                "uuuuuuuuuuuu........LLLL",
                60,
                0.18d,
                0.22d
        );
        CwTimingSnapshot runawayTiming = timingSnapshot(31.0d);

        int displayWpm = guard.resolveDisplayWpm(weakSignal, runawayTiming, 1500L);
        assertTrue(displayWpm <= 26);
        assertTrue(guard.holding());

        CwTimingEvent adapted = guard.adaptTimingEvent(
                new CwTimingEvent(
                        CwTimingEvent.Kind.TONE,
                        CwTimingEvent.Classification.DIT,
                        1500L,
                        42L,
                        39L,
                        39L
                ),
                weakSignal,
                runawayTiming,
                1500L
        );
        assertTrue(adapted.dotEstimateMs() >= 45L);
    }

    @Test
    public void strongConfidenceWithinBandCanReleaseHold() {
        LiveRxWpmGuard guard = new LiveRxWpmGuard();
        guard.setSeedWpm(24);

        CwSignalSnapshot strongSignal = signalSnapshot(
                true,
                "LLLLLLLLLLLLLLLLLLLLLLLL",
                6,
                0.58d,
                0.66d
        );
        CwTimingSnapshot trustedTiming = timingSnapshot(24.0d);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1000L);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1200L);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1400L);

        CwSignalSnapshot weakSignal = signalSnapshot(
                false,
                "uuuuuuuuuuuu........LLLL",
                60,
                0.18d,
                0.22d
        );
        guard.resolveDisplayWpm(weakSignal, timingSnapshot(31.0d), 1500L);
        assertTrue(guard.holding());

        int releasedWpm = guard.resolveDisplayWpm(strongSignal, timingSnapshot(25.0d), 1800L);
        assertFalse(guard.holding());
        assertEquals(24, releasedWpm);
    }

    @Test
    public void trustedWpmFallsBackAfterLongIdle() {
        LiveRxWpmGuard guard = new LiveRxWpmGuard();
        guard.setSeedWpm(24);

        CwSignalSnapshot strongSignal = signalSnapshot(
                true,
                "LLLLLLLLLLLLLLLLLLLLLLLL",
                8,
                0.58d,
                0.66d
        );
        CwTimingSnapshot trustedTiming = timingSnapshot(24.0d);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1000L);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1200L);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1400L);

        int afterIdle = guard.resolveDisplayWpm(null, null, 10000L);
        assertEquals(24, afterIdle);
        assertFalse(guard.holding());
    }

    @Test
    public void trustedWpmDoesNotExpireDuringActiveWeakSignal() {
        LiveRxWpmGuard guard = new LiveRxWpmGuard();
        guard.setSeedWpm(24);

        CwSignalSnapshot strongSignal = signalSnapshot(
                true,
                "LLLLLLLLLLLLLLLLLLLLLLLL",
                8,
                0.58d,
                0.66d
        );
        CwTimingSnapshot trustedTiming = timingSnapshot(24.0d);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1000L);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1200L);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1400L);

        CwSignalSnapshot activeWeakSignal = signalSnapshot(
                false,
                "uuuuLLLLuuuuLLLLuuuuLLLL",
                28,
                0.24d,
                0.30d
        );

        int displayWpm = guard.resolveDisplayWpm(activeWeakSignal, timingSnapshot(43.0d), 9500L);
        assertTrue(displayWpm <= 25);
        assertTrue(guard.holding());
    }

    @Test
    public void trustedWpmDoesNotRatchetUpWithinSameActiveRun() {
        LiveRxWpmGuard guard = new LiveRxWpmGuard();
        guard.setSeedWpm(24);

        CwSignalSnapshot strongSignal = signalSnapshot(
                true,
                "LLLLLLLLLLLLLLLLLLLLLLLL",
                8,
                0.58d,
                0.66d
        );
        CwTimingSnapshot trustedTiming = timingSnapshot(24.0d);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1000L);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1200L);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1400L);

        for (int index = 0; index < 8; index++) {
            guard.noteDecodedCharacter(false, strongSignal, timingSnapshot(25.4d), 1600L + (index * 180L));
        }

        int referenceWpm = guard.resolveReferenceWpm(timingSnapshot(25.4d));
        assertEquals(24, referenceWpm);
        assertTrue(guard.resolveDisplayWpm(strongSignal, timingSnapshot(25.4d), 3400L) <= 24);
    }

    @Test
    public void trustedWpmRetargetKeepsNearDashRawToneAsDash() {
        LiveRxWpmGuard guard = new LiveRxWpmGuard();
        guard.setSeedWpm(20);

        CwSignalSnapshot strongSignal = signalSnapshot(
                true,
                "LLLLLLLLLLLLLLLLLLLLLLLL",
                8,
                0.58d,
                0.66d
        );
        CwTimingSnapshot trustedTiming = timingSnapshot(14.5d);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1000L);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1200L);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1400L);

        CwTimingEvent adapted = guard.adaptTimingEvent(
                new CwTimingEvent(
                        CwTimingEvent.Kind.TONE,
                        CwTimingEvent.Classification.DAH,
                        1600L,
                        139L,
                        76L,
                        76L
                ),
                strongSignal,
                timingSnapshotFromDotMs(76L),
                1600L
        );

        assertEquals(CwTimingEvent.Classification.DAH, adapted.classification());
        assertTrue(adapted.dotEstimateMs() >= 82L);
    }

    @Test
    public void trustedWpmRetargetKeepsNearLetterGapAsLetterGap() {
        LiveRxWpmGuard guard = new LiveRxWpmGuard();
        guard.setSeedWpm(20);

        CwSignalSnapshot strongSignal = signalSnapshot(
                true,
                "LLLLLLLLLLLLLLLLLLLLLLLL",
                8,
                0.58d,
                0.66d
        );
        CwTimingSnapshot trustedTiming = timingSnapshot(14.5d);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1000L);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1200L);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1400L);

        CwTimingEvent adapted = guard.adaptTimingEvent(
                new CwTimingEvent(
                        CwTimingEvent.Kind.GAP,
                        CwTimingEvent.Classification.LETTER_GAP,
                        1600L,
                        139L,
                        76L,
                        76L
                ),
                strongSignal,
                timingSnapshotFromDotMs(76L),
                1600L
        );

        assertEquals(CwTimingEvent.Classification.LETTER_GAP, adapted.classification());
        assertTrue(adapted.dotEstimateMs() >= 82L);
    }

    @Test
    public void turnResetClearsTrustedAndRetainedWpmBeforeUsingTurnSeed() {
        LiveRxWpmGuard guard = new LiveRxWpmGuard();
        guard.setSeedWpm(24);

        CwSignalSnapshot strongSignal = signalSnapshot(
                true,
                "LLLLLLLLLLLLLLLLLLLLLLLL",
                8,
                0.58d,
                0.66d
        );
        guard.noteDecodedCharacter(false, strongSignal, timingSnapshot(24.0d), 1000L);
        guard.noteDecodedCharacter(false, strongSignal, timingSnapshot(24.0d), 1200L);
        guard.noteDecodedCharacter(false, strongSignal, timingSnapshot(24.0d), 1400L);
        assertEquals(24, guard.resolveReferenceWpm(timingSnapshot(24.0d)));

        guard.beginNewTurn(18, 3000L);

        assertEquals(18, guard.resolveReferenceWpm(null));
        assertEquals(18, guard.resolveDisplayWpm(null, null, 3000L));
        assertFalse(guard.holding());
        assertEquals(18, guard.resolveDisplayWpm(null, null, 12000L));
    }

    @Test
    public void trustedWpmFreezesTimingLearningDuringWeakRunaway() {
        LiveRxWpmGuard guard = new LiveRxWpmGuard();
        guard.setSeedWpm(24);

        CwSignalSnapshot strongSignal = signalSnapshot(
                true,
                "LLLLLLLLLLLLLLLLLLLLLLLL",
                8,
                0.58d,
                0.66d
        );
        CwTimingSnapshot trustedTiming = timingSnapshot(24.0d);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1000L);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1200L);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1400L);

        CwSignalSnapshot weakSignal = signalSnapshot(
                false,
                "uuuuuuuuuuuu........LLLL",
                60,
                0.18d,
                0.22d
        );

        assertFalse(guard.shouldAllowTimingLearning(weakSignal, timingSnapshot(31.0d), 1800L));
    }

    @Test
    public void trustedWpmAllowsStrongCoherentFastLearningWithinLocalTurnBand() {
        LiveRxWpmGuard guard = new LiveRxWpmGuard();
        guard.setSeedWpm(20);

        CwSignalSnapshot strongSignal = signalSnapshot(
                true,
                "LLLLLLLLLLLLLLLLLLLLLLLL",
                8,
                0.58d,
                0.66d
        );
        CwTimingSnapshot trustedTiming = timingSnapshot(14.5d);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1000L);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1200L);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1400L);

        assertTrue(guard.shouldAllowTimingLearning(
                strongSignal,
                timingSnapshotFromDotMs(76L),
                1800L
        ));
    }

    @Test
    public void trustedWpmStillBlocksExtremeFastLearningBurstAfterTrust() {
        LiveRxWpmGuard guard = new LiveRxWpmGuard();
        guard.setSeedWpm(20);

        CwSignalSnapshot strongSignal = signalSnapshot(
                true,
                "LLLLLLLLLLLLLLLLLLLLLLLL",
                8,
                0.58d,
                0.66d
        );
        CwTimingSnapshot trustedTiming = timingSnapshot(14.5d);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1000L);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1200L);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1400L);

        assertFalse(guard.shouldAllowTimingLearning(
                strongSignal,
                timingSnapshot(24.0d),
                1800L
        ));
    }

    @Test
    public void trustedWpmAllowsStrongGapEventToContributeFastTimingLearning() {
        LiveRxWpmGuard guard = new LiveRxWpmGuard();
        guard.setSeedWpm(20);

        CwSignalSnapshot strongSignal = signalSnapshot(
                true,
                "LLLLLLLLLLLLLLLLLLLLLLLL",
                8,
                0.58d,
                0.66d
        );
        CwTimingSnapshot trustedTiming = timingSnapshot(14.5d);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1000L);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1200L);
        guard.noteDecodedCharacter(false, strongSignal, trustedTiming, 1400L);

        CwTimingSnapshot gapTiming = timingSnapshotWithLastToneEvent(76L, 17618L, 136L);

        assertTrue(guard.shouldAllowTimingLearningForEvent(
                new org.bi9clt.cwcn.core.signal.CwToneEvent(
                        org.bi9clt.cwcn.core.signal.CwToneEvent.Type.TONE_ON,
                        17800L,
                        16000,
                        12000.0d,
                        0L
                ),
                strongSignal,
                gapTiming,
                17800L
        ));
    }

    @Test
    public void trustedWpmBlocksDashLikeToneOffEventFromSlowingTrustedTiming() {
        LiveRxWpmGuard guard = new LiveRxWpmGuard();
        guard.setSeedWpm(20);

        CwSignalSnapshot trustSignal = signalSnapshot(
                true,
                "LLLLLLLLLLLLLLLLLLLLLLLL",
                8,
                0.58d,
                0.66d
        );
        CwTimingSnapshot trustedTiming = timingSnapshot(14.5d);
        guard.noteDecodedCharacter(false, trustSignal, trustedTiming, 1000L);
        guard.noteDecodedCharacter(false, trustSignal, trustedTiming, 1200L);
        guard.noteDecodedCharacter(false, trustSignal, trustedTiming, 1400L);

        CwSignalSnapshot toneOffSignal = signalSnapshot(
                false,
                "LLLLLLLLLLLLLLLLLLLLLLLL",
                8,
                0.11d,
                0.10d
        );

        assertFalse(guard.shouldAllowTimingLearningForEvent(
                new org.bi9clt.cwcn.core.signal.CwToneEvent(
                        org.bi9clt.cwcn.core.signal.CwToneEvent.Type.TONE_OFF,
                        19458L,
                        3196,
                        196.2d,
                        140L
                ),
                toneOffSignal,
                timingSnapshotFromDotMs(76L),
                19458L
        ));
    }

    @Test
    public void startupStillAllowsTimingLearningBeforeTrustedWpmExists() {
        LiveRxWpmGuard guard = new LiveRxWpmGuard();
        guard.setSeedWpm(15);

        CwSignalSnapshot weakSignal = signalSnapshot(
                false,
                "uuuuuuuuuuuu........LLLL",
                60,
                0.18d,
                0.22d
        );

        assertTrue(guard.shouldAllowTimingLearning(weakSignal, timingSnapshot(24.0d), 800L));
    }

    @Test
    public void startupAllowsTimingLearningAcrossFastRawWpmBeforeTrustedWpmExists() {
        LiveRxWpmGuard guard = new LiveRxWpmGuard();
        guard.setSeedWpm(15);

        CwSignalSnapshot weakSignal = signalSnapshot(
                false,
                "uuuuuuuuuuuu........LLLL",
                60,
                0.18d,
                0.22d
        );

        assertTrue(guard.shouldAllowTimingLearning(weakSignal, timingSnapshot(27.0d), 800L));
    }

    private CwTimingSnapshot timingSnapshot(double wpm) {
        long dotMs = Math.max(1L, Math.round(1200.0d / wpm));
        return timingSnapshotFromDotMs(dotMs);
    }

    private CwTimingSnapshot timingSnapshotFromDotMs(long dotMs) {
        double wpm = 1200.0d / dotMs;
        return new CwTimingSnapshot(
                dotMs,
                dotMs * 3L,
                dotMs,
                (int) Math.round(wpm),
                wpm,
                0,
                0,
                null
        );
    }

    private CwTimingSnapshot timingSnapshotWithLastToneEvent(long dotMs, long lastToneTimestampMs, long lastToneDurationMs) {
        double wpm = 1200.0d / dotMs;
        return new CwTimingSnapshot(
                dotMs,
                dotMs * 3L,
                dotMs,
                (int) Math.round(wpm),
                wpm,
                0,
                0,
                new CwTimingEvent(
                        CwTimingEvent.Kind.TONE,
                        CwTimingEvent.Classification.DAH,
                        lastToneTimestampMs,
                        lastToneDurationMs,
                        dotMs,
                        dotMs
                )
        );
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
