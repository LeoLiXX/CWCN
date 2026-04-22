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
    public void strongerOutOfBandToneDoesNotPullTrackingAwayFromPreferredWindow() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(650);

        processFrames(processor, 8, 0.0d, 0.0d);
        processFrames(processor, 12, 670.0d, 11000.0d, 930.0d, 19000.0d);

        CwSignalSnapshot snapshot = processor.snapshot();
        assertTrue(snapshot.targetToneLocked());
        assertTrue(Math.abs(snapshot.targetToneFrequencyHz() - 670) <= 20);
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
    public void closerInWindowToneWinsOverStrongerFarEdgeTone() {
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(650);

        processFrames(processor, 8, 0.0d, 0.0d);
        processFrames(processor, 12, 670.0d, 15000.0d, 800.0d, 21000.0d);

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
        assertTrue(snapshot.toneDominanceRatio() < 0.15d);
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
