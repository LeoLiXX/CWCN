package org.bi9clt.cwcn.core.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AudioInputHealthTrackerTest {
    @Test
    public void trackerSeparatesQuietUsableHotAndClippingFrames() {
        AudioInputHealthTracker tracker = new AudioInputHealthTracker();

        tracker.process(frame(500, 100.0d, 0));
        tracker.process(frame(7000, 600.0d, 0));
        tracker.process(frame(26000, 9500.0d, 0));
        tracker.process(frame(32760, 10000.0d, 8));

        AudioInputHealthSnapshot snapshot = tracker.snapshot();

        assertEquals(4, snapshot.totalFrames());
        assertEquals(1, snapshot.recentQuietFrameCount());
        assertEquals(1, snapshot.recentUsableFrameCount());
        assertEquals(1, snapshot.recentHotFrameCount());
        assertEquals(1, snapshot.recentClippingFrameCount());
        assertEquals("QGHC", AudioInputHealthFormatter.stateHistory(snapshot));
        assertTrue(AudioInputHealthFormatter.summaryLabel(snapshot).contains("clipping"));
    }

    @Test
    public void resetClearsRecentHistoryAndCounters() {
        AudioInputHealthTracker tracker = new AudioInputHealthTracker();
        tracker.process(frame(7000, 600.0d, 0));

        tracker.reset();
        AudioInputHealthSnapshot snapshot = tracker.snapshot();

        assertEquals(0, snapshot.totalFrames());
        assertEquals(0, snapshot.recentHistoryFrameCount());
        assertEquals("(empty)", AudioInputHealthFormatter.stateHistory(snapshot));
        assertTrue(AudioInputHealthFormatter.summaryLabel(snapshot).contains("No microphone input"));
    }

    private AudioFrame frame(int peakAmplitude, double rmsAmplitude, int clippedSampleCount) {
        return new AudioFrame(
                new short[256],
                16000,
                1,
                peakAmplitude,
                rmsAmplitude,
                clippedSampleCount,
                1234L
        );
    }
}
