package org.bi9clt.cwcn.core.audio;

import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class CwRecording12OpeningMicroWindowProbeTest {
    private static final long[] TARGET_TIMESTAMPS_MS = new long[] {1952L, 2000L, 2016L, 2032L, 2048L};
    private static final int[] TARGET_FREQUENCIES_HZ = new int[] {700, 730};
    private static final int[] WINDOW_SIZES = new int[] {256, 224, 192, 160, 128, 96, 64};

    @Test
    public void printRecording12OpeningMicroWindowToneRms() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(12).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for recording (12)"));
        List<AudioFrame> frames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );

        System.out.println("==== recording(12) opening micro-window tone rms ====");
        for (long timestampMs : TARGET_TIMESTAMPS_MS) {
            AudioFrame frame = frameAtOrAfter(frames, timestampMs);
            if (frame == null || frame.samples() == null) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "@%d frameTs=%d sampleRate=%d samples=%d frameRms=%.1f peak=%d",
                    timestampMs,
                    frame.capturedAtMs(),
                    frame.sampleRateHz(),
                    frame.sampleCount(),
                    frame.rmsAmplitude(),
                    frame.peakAmplitude()
            ));
            for (int frequencyHz : TARGET_FREQUENCIES_HZ) {
                StringBuilder builder = new StringBuilder();
                builder.append("  f=").append(frequencyHz).append("Hz");
                for (int windowSize : WINDOW_SIZES) {
                    short[] window = suffixWindow(frame.samples(), windowSize);
                    builder.append(" w").append(window.length).append("=")
                            .append(String.format(Locale.US, "%.1f", estimateToneRms(window, frame.sampleRateHz(), frequencyHz)));
                }
                System.out.println(builder);
            }
        }
    }

    private static AudioFrame frameAtOrAfter(List<AudioFrame> frames, long timestampMs) {
        for (AudioFrame frame : frames) {
            if (frame != null && frame.capturedAtMs() >= timestampMs) {
                return frame;
            }
        }
        return null;
    }

    private static short[] suffixWindow(short[] samples, int requestedWindowSize) {
        if (samples == null || samples.length == 0) {
            return new short[0];
        }
        int windowSize = Math.max(1, Math.min(samples.length, requestedWindowSize));
        short[] window = new short[windowSize];
        System.arraycopy(samples, samples.length - windowSize, window, 0, windowSize);
        return window;
    }

    private static double estimateToneRms(short[] samples, int sampleRateHz, int targetFrequencyHz) {
        if (samples == null || samples.length == 0 || sampleRateHz <= 0 || targetFrequencyHz <= 0) {
            return 0.0d;
        }
        double omega = (2.0d * Math.PI * targetFrequencyHz) / sampleRateHz;
        double coeff = 2.0d * Math.cos(omega);
        double q0 = 0.0d;
        double q1 = 0.0d;
        double q2 = 0.0d;
        for (short sample : samples) {
            q0 = coeff * q1 - q2 + sample;
            q2 = q1;
            q1 = q0;
        }
        double power = (q1 * q1) + (q2 * q2) - (coeff * q1 * q2);
        if (power <= 0.0d) {
            return 0.0d;
        }
        double magnitude = Math.sqrt(power);
        return (magnitude * Math.sqrt(2.0d)) / samples.length;
    }
}
