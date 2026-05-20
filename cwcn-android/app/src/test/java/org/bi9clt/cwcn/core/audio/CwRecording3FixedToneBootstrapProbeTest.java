package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertTrue;

public final class CwRecording3FixedToneBootstrapProbeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int[] PROFILE_FREQUENCIES_HZ = new int[]{520, 550, 580, 600, 650, 700, 730, 770, 790};

    @Test
    public void printRecording3FixedToneBootstrapProfiles() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(3).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (3)"));
        LocalAudioDecodeTestSupport.WaveDataProbe waveData = LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);

        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(PREFERRED_TONE_HZ);
        processor.setRxToneMode(CwSignalProcessor.RxToneMode.FIXED_TONE);

        StringBuilder trace = new StringBuilder();
        long sampleOffset = 0L;
        for (int offset = 0, frameIndex = 0; offset < waveData.samples().length; offset += 256, frameIndex++) {
            int frameLength = Math.min(256, waveData.samples().length - offset);
            short[] frameSamples = new short[frameLength];
            System.arraycopy(waveData.samples(), offset, frameSamples, 0, frameLength);
            AudioFrame frame = LocalAudioDecodeTestSupport.buildFrameForProbe(
                    frameSamples,
                    waveData.sampleRateHz(),
                    sampleOffset
            );
            if (frameIndex < 16 || (frameIndex < 84 && frameIndex % 4 == 0)) {
                CwSignalSnapshot before = processor.snapshot();
                trace.append("---- F").append(frameIndex)
                        .append(" @").append(frame.capturedAtMs()).append("ms")
                        .append(" peak=").append(frame.peakAmplitude())
                        .append(" rms=").append(String.format(Locale.US, "%.1f", frame.rmsAmplitude()))
                        .append('\n')
                        .append("before target=").append(before.targetToneFrequencyHz())
                        .append(" eff=").append(before.effectiveTrackedToneFrequencyHz())
                        .append(" lock=").append(before.targetToneLocked())
                        .append(" aq=").append(before.acquisitionWinnerFrequencyHz()).append('/').append(before.acquisitionWinnerSource())
                        .append(" final=").append(before.finalAdoptedFrequencyHz()).append('/').append(before.finalAdoptedSource())
                        .append('\n')
                        .append(processor.debugAcquisitionProfile(frame, PROFILE_FREQUENCIES_HZ))
                        .append('\n');
            }
            List<CwToneEvent> toneEvents = processor.process(frame);
            if (!toneEvents.isEmpty() && frameIndex < 96) {
                trace.append("events F").append(frameIndex).append(": ").append(renderEvents(toneEvents)).append('\n');
            }
            sampleOffset += frameLength;
        }

        System.out.println("==== recording3 fixed-tone bootstrap profiles ====");
        System.out.println(trace);
        assertTrue(true);
    }

    private static String renderEvents(List<CwToneEvent> events) {
        if (events == null || events.isEmpty()) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < events.size(); index++) {
            if (index > 0) {
                builder.append(" | ");
            }
            CwToneEvent event = events.get(index);
            builder.append(event.type())
                    .append('@')
                    .append(event.timestampMs())
                    .append('/')
                    .append(event.toneDurationMs());
        }
        return builder.toString();
    }
}
