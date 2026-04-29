package org.bi9clt.cwcn.ui.debug;

import org.bi9clt.cwcn.core.audio.AudioFrame;

import java.util.Arrays;

public final class AudioSpectrumAnalyzer {
    private static final int MIN_FREQUENCY_HZ = 350;
    private static final int MAX_FREQUENCY_HZ = 950;
    private static final int STEP_FREQUENCY_HZ = 10;
    private static final float SMOOTHING = 0.35f;
    private static final int BIN_COUNT = ((MAX_FREQUENCY_HZ - MIN_FREQUENCY_HZ) / STEP_FREQUENCY_HZ) + 1;

    private final int[] frequenciesHz = new int[BIN_COUNT];
    private final float[] smoothedMagnitudes = new float[BIN_COUNT];

    public AudioSpectrumAnalyzer() {
        for (int index = 0; index < BIN_COUNT; index++) {
            frequenciesHz[index] = MIN_FREQUENCY_HZ + (index * STEP_FREQUENCY_HZ);
        }
    }

    public void reset() {
        Arrays.fill(smoothedMagnitudes, 0.0f);
    }

    public AudioSpectrumSnapshot process(
            AudioFrame frame,
            int preferredToneHz,
            int trackedToneHz,
            int hypothesisToneHz,
            int preferredWindowWinnerToneHz,
            int wideScanWinnerToneHz,
            int acquisitionWinnerToneHz,
            int finalAdoptedToneHz,
            String acquisitionWinnerSource,
            String finalAdoptedSource,
            boolean hypothesisGuardEnabled,
            boolean hypothesisGuardApplied,
            int hypothesisGuardAppliedToneHz,
            String hypothesisGuardDecision
    ) {
        if (frame == null || frame.samples() == null || frame.samples().length == 0 || frame.sampleRateHz() <= 0) {
            return snapshot(
                    preferredToneHz,
                    trackedToneHz,
                    hypothesisToneHz,
                    preferredWindowWinnerToneHz,
                    wideScanWinnerToneHz,
                    acquisitionWinnerToneHz,
                    finalAdoptedToneHz,
                    acquisitionWinnerSource,
                    finalAdoptedSource,
                    hypothesisGuardEnabled,
                    hypothesisGuardApplied,
                    hypothesisGuardAppliedToneHz,
                    hypothesisGuardDecision
            );
        }

        for (int index = 0; index < frequenciesHz.length; index++) {
            float currentMagnitude = (float) estimateToneRms(
                    frame.samples(),
                    frame.sampleRateHz(),
                    frequenciesHz[index]
            );
            smoothedMagnitudes[index] = smoothedMagnitudes[index]
                    + ((currentMagnitude - smoothedMagnitudes[index]) * SMOOTHING);
        }
        return snapshot(
                preferredToneHz,
                trackedToneHz,
                hypothesisToneHz,
                preferredWindowWinnerToneHz,
                wideScanWinnerToneHz,
                acquisitionWinnerToneHz,
                finalAdoptedToneHz,
                acquisitionWinnerSource,
                finalAdoptedSource,
                hypothesisGuardEnabled,
                hypothesisGuardApplied,
                hypothesisGuardAppliedToneHz,
                hypothesisGuardDecision
        );
    }

    private AudioSpectrumSnapshot snapshot(
            int preferredToneHz,
            int trackedToneHz,
            int hypothesisToneHz,
            int preferredWindowWinnerToneHz,
            int wideScanWinnerToneHz,
            int acquisitionWinnerToneHz,
            int finalAdoptedToneHz,
            String acquisitionWinnerSource,
            String finalAdoptedSource,
            boolean hypothesisGuardEnabled,
            boolean hypothesisGuardApplied,
            int hypothesisGuardAppliedToneHz,
            String hypothesisGuardDecision
    ) {
        int peakIndex = 0;
        float peakMagnitude = 0.0f;
        for (int index = 0; index < smoothedMagnitudes.length; index++) {
            if (smoothedMagnitudes[index] > peakMagnitude) {
                peakMagnitude = smoothedMagnitudes[index];
                peakIndex = index;
            }
        }

        float[] ordered = Arrays.copyOf(smoothedMagnitudes, smoothedMagnitudes.length);
        Arrays.sort(ordered);
        int baselineCount = Math.max(1, ordered.length / 3);
        float noiseFloorMagnitude = 0.0f;
        for (int index = 0; index < baselineCount; index++) {
            noiseFloorMagnitude += ordered[index];
        }
        noiseFloorMagnitude /= baselineCount;

        return new AudioSpectrumSnapshot(
                Arrays.copyOf(frequenciesHz, frequenciesHz.length),
                Arrays.copyOf(smoothedMagnitudes, smoothedMagnitudes.length),
                frequenciesHz[peakIndex],
                peakMagnitude,
                noiseFloorMagnitude,
                preferredToneHz,
                trackedToneHz,
                hypothesisToneHz,
                preferredWindowWinnerToneHz,
                wideScanWinnerToneHz,
                acquisitionWinnerToneHz,
                finalAdoptedToneHz,
                acquisitionWinnerSource,
                finalAdoptedSource,
                hypothesisGuardEnabled,
                hypothesisGuardApplied,
                hypothesisGuardAppliedToneHz,
                hypothesisGuardDecision
        );
    }

    private double estimateToneRms(short[] samples, int sampleRateHz, int targetFrequencyHz) {
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
