package org.bi9clt.cwcn.core.rx;

import org.bi9clt.cwcn.core.audio.AudioInputHealthSnapshot;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LiveRxToneEventStabilizerTest {
    @Test
    public void shortSplitToneGetsBridgedIntoSingleLongerTone() {
        LiveRxToneEventStabilizer stabilizer = new LiveRxToneEventStabilizer();
        CwSignalSnapshot weakSignal = signalSnapshot(
                false,
                "uuuuuuuuuuuu........LLLL",
                60,
                0.20d,
                0.26d
        );

        List<CwToneEvent> delayedOff = stabilizer.process(toneOff(100L, 18L), weakSignal, null, 48L);
        assertTrue(delayedOff.isEmpty());

        List<CwToneEvent> swallowedResume = stabilizer.process(toneOn(112L), weakSignal, null, 48L);
        assertTrue(swallowedResume.isEmpty());

        List<CwToneEvent> mergedOff = stabilizer.process(toneOff(140L, 24L), weakSignal, null, 48L);
        assertEquals(1, mergedOff.size());
        assertEquals(CwToneEvent.Type.TONE_OFF, mergedOff.get(0).type());
        assertEquals(58L, mergedOff.get(0).toneDurationMs());
    }

    @Test
    public void cleanLockedSignalDoesNotDelayShortToneOff() {
        LiveRxToneEventStabilizer stabilizer = new LiveRxToneEventStabilizer();
        CwSignalSnapshot strongSignal = signalSnapshot(
                true,
                "LLLLLLLLLLLLLLLLLLLLLLLL",
                8,
                0.62d,
                0.70d
        );

        List<CwToneEvent> events = stabilizer.process(toneOff(100L, 18L), strongSignal, quietHealth(), 48L);
        assertEquals(1, events.size());
        assertEquals(18L, events.get(0).toneDurationMs());
    }

    @Test
    public void weakShortToneGetsDroppedWhenResumeNeverArrives() {
        LiveRxToneEventStabilizer stabilizer = new LiveRxToneEventStabilizer();
        CwSignalSnapshot weakSignal = signalSnapshot(
                false,
                "uuuuuuuuuuuu........LLLL",
                60,
                0.20d,
                0.26d
        );

        List<CwToneEvent> delayedOff = stabilizer.process(toneOff(100L, 18L), weakSignal, null, 48L);
        assertTrue(delayedOff.isEmpty());

        List<CwToneEvent> flushedOff = stabilizer.flush(130L);
        assertTrue(flushedOff.isEmpty());
    }

    @Test
    public void confidentShortStandaloneToneSurvivesFlushAfterPlausibleGap() {
        LiveRxToneEventStabilizer stabilizer = new LiveRxToneEventStabilizer();
        CwSignalSnapshot strongOnsetSignal = signalSnapshot(
                true,
                "LLLLLLLLLLLLLLLLLLLLLLLL",
                8,
                0.70d,
                0.74d
        );
        CwSignalSnapshot weakReleaseSignal = signalSnapshot(
                false,
                "LLLLLLLLLLLLLL..........",
                8,
                0.43d,
                0.32d
        );

        List<CwToneEvent> priorToneOff = stabilizer.process(toneOff(80L, 64L), weakReleaseSignal, quietHealth(), 48L);
        assertEquals(1, priorToneOff.size());
        assertEquals(CwToneEvent.Type.TONE_OFF, priorToneOff.get(0).type());

        List<CwToneEvent> confidentToneOn = stabilizer.process(toneOn(140L), strongOnsetSignal, quietHealth(), 48L);
        assertEquals(1, confidentToneOn.size());
        assertEquals(CwToneEvent.Type.TONE_ON, confidentToneOn.get(0).type());

        List<CwToneEvent> delayedShortOff = stabilizer.process(toneOff(161L, 21L), weakReleaseSignal, quietHealth(), 48L);
        assertTrue(delayedShortOff.isEmpty());

        List<CwToneEvent> flushedShortOff = stabilizer.flush(190L);
        assertEquals(1, flushedShortOff.size());
        assertEquals(CwToneEvent.Type.TONE_OFF, flushedShortOff.get(0).type());
        assertEquals(21L, flushedShortOff.get(0).toneDurationMs());
        assertFalse(stabilizer.shouldSuppressShortTone(
                flushedShortOff.get(0),
                weakReleaseSignal,
                quietHealth(),
                48L
        ));

        LiveRxToneEventStabilizerStats stats = stabilizer.stats();
        assertEquals(1, stats.delayedToneOffCount());
        assertEquals(0, stats.droppedIsolatedToneOffCount());
    }

    @Test
    public void hotButNotClippingInputStillPreservesConfidentShortStandaloneTone() {
        LiveRxToneEventStabilizer stabilizer = new LiveRxToneEventStabilizer();
        CwSignalSnapshot strongOnsetSignal = signalSnapshot(
                true,
                "LLLLLLLLLLLLLLLLLLLLLLLL",
                8,
                0.89d,
                0.66d
        );
        CwSignalSnapshot weakReleaseSignal = signalSnapshot(
                true,
                "LLLLLLLLLLLLLL..........",
                8,
                0.60d,
                0.51d
        );

        List<CwToneEvent> priorToneOff = stabilizer.process(toneOff(2816L, 140L), weakReleaseSignal, hotHealth(), 55L);
        assertEquals(1, priorToneOff.size());
        assertEquals(CwToneEvent.Type.TONE_OFF, priorToneOff.get(0).type());

        List<CwToneEvent> confidentToneOn = stabilizer.process(toneOn(2868L), strongOnsetSignal, hotHealth(), 55L);
        assertEquals(1, confidentToneOn.size());
        assertEquals(CwToneEvent.Type.TONE_ON, confidentToneOn.get(0).type());

        List<CwToneEvent> delayedShortOff = stabilizer.process(toneOff(2902L, 34L), weakReleaseSignal, hotHealth(), 55L);
        assertTrue(delayedShortOff.isEmpty());

        List<CwToneEvent> releasedShortOff = stabilizer.process(toneOn(3060L), strongOnsetSignal, hotHealth(), 55L);
        assertEquals(2, releasedShortOff.size());
        assertEquals(CwToneEvent.Type.TONE_OFF, releasedShortOff.get(0).type());
        assertEquals(2902L, releasedShortOff.get(0).timestampMs());
        assertEquals(34L, releasedShortOff.get(0).toneDurationMs());
        assertEquals(CwToneEvent.Type.TONE_ON, releasedShortOff.get(1).type());
        assertEquals(3060L, releasedShortOff.get(1).timestampMs());
    }

    @Test
    public void marginallyLockedButVeryCleanShortStandaloneToneStillSurvives() {
        LiveRxToneEventStabilizer stabilizer = new LiveRxToneEventStabilizer();
        CwSignalSnapshot cleanMarginalStartSignal = signalSnapshot(
                true,
                "LLLLLLLLLLLL............",
                8,
                0.96d,
                0.78d
        );
        CwSignalSnapshot weakReleaseSignal = signalSnapshot(
                false,
                "LLLLLLLL........uuuu....",
                8,
                0.30d,
                0.24d
        );

        List<CwToneEvent> priorToneOff = stabilizer.process(toneOff(80L, 126L), weakReleaseSignal, quietHealth(), 52L);
        assertEquals(1, priorToneOff.size());
        assertEquals(CwToneEvent.Type.TONE_OFF, priorToneOff.get(0).type());

        List<CwToneEvent> confidentToneOn = stabilizer.process(toneOn(140L), cleanMarginalStartSignal, quietHealth(), 52L);
        assertEquals(1, confidentToneOn.size());
        assertEquals(CwToneEvent.Type.TONE_ON, confidentToneOn.get(0).type());

        List<CwToneEvent> delayedShortOff = stabilizer.process(toneOff(166L, 26L), weakReleaseSignal, quietHealth(), 52L);
        assertTrue(delayedShortOff.isEmpty());

        List<CwToneEvent> flushedShortOff = stabilizer.flush(200L);
        assertEquals(1, flushedShortOff.size());
        assertEquals(CwToneEvent.Type.TONE_OFF, flushedShortOff.get(0).type());
        assertEquals(26L, flushedShortOff.get(0).toneDurationMs());
    }

    @Test
    public void nonFragmentWeakTonePassesThroughImmediately() {
        LiveRxToneEventStabilizer stabilizer = new LiveRxToneEventStabilizer();
        CwSignalSnapshot weakSignal = signalSnapshot(
                false,
                "uuuuuuuuuuuu........LLLL",
                60,
                0.20d,
                0.26d
        );

        List<CwToneEvent> events = stabilizer.process(toneOff(100L, 32L), weakSignal, null, 96L);
        assertEquals(1, events.size());
        assertEquals(32L, events.get(0).toneDurationMs());
    }

    @Test
    public void hotInputCanStillMarkShortToneAsFragmentCandidate() {
        LiveRxToneEventStabilizer stabilizer = new LiveRxToneEventStabilizer();
        CwSignalSnapshot strongSignal = signalSnapshot(
                true,
                "LLLLLLLLLLLLLLLLLLLLLLLL",
                8,
                0.64d,
                0.72d
        );

        assertTrue(stabilizer.shouldSuppressShortTone(
                toneOff(100L, 18L),
                strongSignal,
                hotHealth(),
                48L
        ));
    }

    @Test
    public void hotInputUsesWiderFragmentAndBridgeWindows() {
        LiveRxToneEventStabilizer stabilizer = new LiveRxToneEventStabilizer();
        CwSignalSnapshot strongSignal = signalSnapshot(
                true,
                "LLLLLLLLLLLLLLLLLLLLLLLL",
                8,
                0.64d,
                0.72d
        );

        List<CwToneEvent> delayedOff = stabilizer.process(toneOff(100L, 30L), strongSignal, hotHealth(), 48L);
        assertTrue(delayedOff.isEmpty());

        List<CwToneEvent> swallowedResume = stabilizer.process(toneOn(126L), strongSignal, hotHealth(), 48L);
        assertTrue(swallowedResume.isEmpty());

        List<CwToneEvent> mergedOff = stabilizer.process(toneOff(162L, 36L), strongSignal, hotHealth(), 48L);
        assertEquals(1, mergedOff.size());
        assertEquals(CwToneEvent.Type.TONE_OFF, mergedOff.get(0).type());
        assertEquals(92L, mergedOff.get(0).toneDurationMs());
    }

    @Test
    public void weakShortOnsetFragmentGetsDroppedAsWholePulse() {
        LiveRxToneEventStabilizer stabilizer = new LiveRxToneEventStabilizer();
        CwSignalSnapshot weakSignal = signalSnapshot(
                false,
                "uuuuuuuuuuuu........LLLL",
                60,
                0.20d,
                0.26d
        );

        List<CwToneEvent> delayedOn = stabilizer.process(toneOn(100L), weakSignal, null, 48L);
        assertTrue(delayedOn.isEmpty());

        List<CwToneEvent> droppedPulse = stabilizer.process(toneOff(114L, 14L), weakSignal, null, 48L);
        assertTrue(droppedPulse.isEmpty());

        List<CwToneEvent> flushed = stabilizer.flush(140L);
        assertTrue(flushed.isEmpty());

        LiveRxToneEventStabilizerStats stats = stabilizer.stats();
        assertEquals(1, stats.delayedToneOnCount());
        assertEquals(0, stats.confirmedToneOnCount());
        assertEquals(1, stats.droppedToneOnFragmentCount());
    }

    @Test
    public void weakOnsetGetsReleasedAfterConfirmWindowIfPulsePersists() {
        LiveRxToneEventStabilizer stabilizer = new LiveRxToneEventStabilizer();
        CwSignalSnapshot weakSignal = signalSnapshot(
                false,
                "uuuuuuuuuuuu........LLLL",
                60,
                0.20d,
                0.26d
        );

        List<CwToneEvent> delayedOn = stabilizer.process(toneOn(100L), weakSignal, null, 48L);
        assertTrue(delayedOn.isEmpty());

        List<CwToneEvent> confirmedOn = stabilizer.flush(121L);
        assertEquals(1, confirmedOn.size());
        assertEquals(CwToneEvent.Type.TONE_ON, confirmedOn.get(0).type());
        assertEquals(100L, confirmedOn.get(0).timestampMs());

        LiveRxToneEventStabilizerStats stats = stabilizer.stats();
        assertEquals(1, stats.delayedToneOnCount());
        assertEquals(1, stats.confirmedToneOnCount());
        assertEquals(0, stats.droppedToneOnFragmentCount());
    }

    @Test
    public void cleanLockedSignalDoesNotDelayToneOn() {
        LiveRxToneEventStabilizer stabilizer = new LiveRxToneEventStabilizer();
        CwSignalSnapshot strongSignal = signalSnapshot(
                true,
                "LLLLLLLLLLLLLLLLLLLLLLLL",
                8,
                0.62d,
                0.70d
        );

        List<CwToneEvent> events = stabilizer.process(toneOn(100L), strongSignal, quietHealth(), 48L);
        assertEquals(1, events.size());
        assertEquals(CwToneEvent.Type.TONE_ON, events.get(0).type());
    }

    @Test
    public void statsTrackDelayedDroppedAndBridgedToneOffBehavior() {
        LiveRxToneEventStabilizer stabilizer = new LiveRxToneEventStabilizer();
        CwSignalSnapshot weakSignal = signalSnapshot(
                false,
                "uuuuuuuuuuuu........LLLL",
                60,
                0.20d,
                0.26d
        );

        assertTrue(stabilizer.process(toneOff(100L, 18L), weakSignal, null, 48L).isEmpty());
        assertTrue(stabilizer.flush(130L).isEmpty());

        assertTrue(stabilizer.process(toneOff(200L, 18L), weakSignal, null, 48L).isEmpty());
        assertTrue(stabilizer.process(toneOn(212L), weakSignal, null, 48L).isEmpty());
        List<CwToneEvent> mergedOff = stabilizer.process(toneOff(240L, 24L), weakSignal, null, 48L);
        assertEquals(1, mergedOff.size());
        assertEquals(CwToneEvent.Type.TONE_OFF, mergedOff.get(0).type());

        LiveRxToneEventStabilizerStats stats = stabilizer.stats();
        assertEquals(2, stats.delayedToneOffCount());
        assertEquals(1, stats.droppedIsolatedToneOffCount());
        assertEquals(1, stats.bridgedToneOffCount());
    }

    private AudioInputHealthSnapshot quietHealth() {
        return new AudioInputHealthSnapshot(
                10,
                "GGGGGGGGGG".toCharArray(),
                10,
                8000,
                3200.0d,
                0.0d
        );
    }

    private AudioInputHealthSnapshot hotHealth() {
        return new AudioInputHealthSnapshot(
                10,
                "HHHHGGGGGG".toCharArray(),
                10,
                26000,
                11000.0d,
                0.0d
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
