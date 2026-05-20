package org.bi9clt.cwcn.core.rx;

import org.bi9clt.cwcn.core.audio.AudioFrame;
import org.bi9clt.cwcn.core.audio.AudioInputHealthTracker;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class RxFrameSignalRunnerTest {
    @Test
    public void processFrameCapturesHealthSnapshotsAndSignalBeforeAfterState() {
        RxFrameSignalRunner runner = new RxFrameSignalRunner(
                new AudioInputHealthTracker(),
                new CwSignalProcessor()
        );
        AudioFrame frame = new AudioFrame(new short[80], 8000, 1, 0, 0.0d, 1000L);

        RxFrameSignalRunner.Result result = runner.processFrame(frame, 7L);

        assertNotNull(result);
        assertEquals(1010L, result.frameEndTimestampMs());
        assertNotNull(result.inputHealthSnapshot());
        assertEquals(1, result.inputHealthSnapshot().totalFrames());
        assertNotNull(result.signalSnapshotBeforeProcess());
        assertNotNull(result.signalSnapshotAfterProcess());
        assertEquals(0, result.signalSnapshotBeforeProcess().processedFrameCount());
        assertEquals(1, result.signalSnapshotAfterProcess().processedFrameCount());
        assertTrue(result.toneEvents().isEmpty());
    }
}
