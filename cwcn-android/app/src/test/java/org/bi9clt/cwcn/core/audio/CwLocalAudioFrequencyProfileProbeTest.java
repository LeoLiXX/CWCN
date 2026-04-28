package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;

public final class CwLocalAudioFrequencyProfileProbeTest {
    private static final int[] PROBE_FREQUENCIES_HZ = new int[]{450, 460, 470, 650, 660, 670, 680, 700};

    @Test
    public void printRecording2EarlyFrequencyProfiles() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(2).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture with suffix (2).wav"));
        LocalAudioDecodeTestSupport.WaveDataProbe waveData = LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);

        CwSignalProcessor signalProcessor = new CwSignalProcessor();
        StringBuilder trace = new StringBuilder();
        long sampleOffset = 0L;
        int interestingFramesPrinted = 0;
        for (int offset = 0, frameIndex = 0; offset < waveData.samples().length; offset += 256, frameIndex++) {
            int frameLength = Math.min(256, waveData.samples().length - offset);
            short[] frameSamples = new short[frameLength];
            System.arraycopy(waveData.samples(), offset, frameSamples, 0, frameLength);
            AudioFrame frame = LocalAudioDecodeTestSupport.buildFrameForProbe(
                    frameSamples,
                    waveData.sampleRateHz(),
                    sampleOffset
            );
            if (frameIndex < 12 || (frameIndex < 48 && frameIndex % 4 == 0)) {
                trace.append("FRAME ")
                        .append(frameIndex)
                        .append(" @").append(frame.capturedAtMs()).append("ms")
                        .append(" peak=").append(frame.peakAmplitude())
                        .append(" rms=").append(String.format(java.util.Locale.US, "%.1f", frame.rmsAmplitude()))
                        .append('\n')
                        .append(signalProcessor.debugAcquisitionProfile(frame, PROBE_FREQUENCIES_HZ))
                        .append("\n\n");
                interestingFramesPrinted += 1;
            }
            List<CwToneEvent> toneEvents = signalProcessor.process(frame);
            if (!toneEvents.isEmpty() && frameIndex < 64) {
                trace.append("EVENTS frame ").append(frameIndex).append(": ");
                for (int index = 0; index < toneEvents.size(); index++) {
                    if (index > 0) {
                        trace.append(", ");
                    }
                    trace.append(toneEvents.get(index).type())
                            .append('@')
                            .append(toneEvents.get(index).timestampMs());
                }
                trace.append("\n\n");
            }
            sampleOffset += frameLength;
        }

        trace.append(signalProcessor.debugActiveLeaderSummary()).append('\n');
        System.out.println(trace);
    }
}
