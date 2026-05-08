package org.bi9clt.cwcn.ui.debug;

import org.bi9clt.cwcn.core.audio.AudioFrame;

import java.util.Arrays;

public final class AudioSpectrumAnalyzer {
    private static final int MIN_FREQUENCY_HZ = 0;
    private static final int MAX_FREQUENCY_HZ = 3000;
    private static final int STEP_FREQUENCY_HZ = 5;
    private static final float SMOOTHING = 0.22f;
    private static final int ANALYSIS_WINDOW_SAMPLES = 2048;
    private static final int FFT_SIZE = 4096;
    private static final int BIN_COUNT = ((MAX_FREQUENCY_HZ - MIN_FREQUENCY_HZ) / STEP_FREQUENCY_HZ) + 1;

    private final int[] frequenciesHz = new int[BIN_COUNT];
    private final float[] smoothedMagnitudes = new float[BIN_COUNT];
    private final short[] rollingSamples = new short[ANALYSIS_WINDOW_SAMPLES];
    private final double[] fftReal = new double[FFT_SIZE];
    private final double[] fftImag = new double[FFT_SIZE];
    private int rollingCount;

    public AudioSpectrumAnalyzer() {
        for (int index = 0; index < BIN_COUNT; index++) {
            frequenciesHz[index] = MIN_FREQUENCY_HZ + (index * STEP_FREQUENCY_HZ);
        }
    }

    public void reset() {
        Arrays.fill(smoothedMagnitudes, 0.0f);
        Arrays.fill(rollingSamples, (short) 0);
        Arrays.fill(fftReal, 0.0d);
        Arrays.fill(fftImag, 0.0d);
        rollingCount = 0;
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

        appendSamples(frame.samples());
        updateSpectrumMagnitudes(frame.sampleRateHz());
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

    private void appendSamples(short[] samples) {
        if (samples == null || samples.length == 0) {
            return;
        }
        if (samples.length >= ANALYSIS_WINDOW_SAMPLES) {
            System.arraycopy(
                    samples,
                    samples.length - ANALYSIS_WINDOW_SAMPLES,
                    rollingSamples,
                    0,
                    ANALYSIS_WINDOW_SAMPLES
            );
            rollingCount = ANALYSIS_WINDOW_SAMPLES;
            return;
        }

        if (rollingCount < ANALYSIS_WINDOW_SAMPLES) {
            int copyCount = Math.min(samples.length, ANALYSIS_WINDOW_SAMPLES - rollingCount);
            System.arraycopy(samples, 0, rollingSamples, rollingCount, copyCount);
            rollingCount += copyCount;
            if (copyCount == samples.length) {
                return;
            }

            int remaining = samples.length - copyCount;
            System.arraycopy(rollingSamples, remaining, rollingSamples, 0, ANALYSIS_WINDOW_SAMPLES - remaining);
            System.arraycopy(samples, copyCount, rollingSamples, ANALYSIS_WINDOW_SAMPLES - remaining, remaining);
            rollingCount = ANALYSIS_WINDOW_SAMPLES;
            return;
        }

        int shift = samples.length;
        System.arraycopy(rollingSamples, shift, rollingSamples, 0, ANALYSIS_WINDOW_SAMPLES - shift);
        System.arraycopy(samples, 0, rollingSamples, ANALYSIS_WINDOW_SAMPLES - shift, shift);
    }

    private void updateSpectrumMagnitudes(int sampleRateHz) {
        if (sampleRateHz <= 0 || rollingCount <= 0) {
            return;
        }
        Arrays.fill(fftReal, 0.0d);
        Arrays.fill(fftImag, 0.0d);

        int missing = ANALYSIS_WINDOW_SAMPLES - rollingCount;
        for (int index = 0; index < rollingCount; index++) {
            int sampleIndex = missing + index;
            double window = hannWindow(sampleIndex, ANALYSIS_WINDOW_SAMPLES);
            fftReal[sampleIndex] = rollingSamples[index] * window;
        }

        fft(fftReal, fftImag);

        for (int index = 0; index < frequenciesHz.length; index++) {
            float currentMagnitude = estimateMagnitudeAtFrequency(sampleRateHz, frequenciesHz[index]);
            smoothedMagnitudes[index] = smoothedMagnitudes[index]
                    + ((currentMagnitude - smoothedMagnitudes[index]) * SMOOTHING);
        }
    }

    private float estimateMagnitudeAtFrequency(int sampleRateHz, int targetFrequencyHz) {
        if (targetFrequencyHz < 0 || sampleRateHz <= 0) {
            return 0.0f;
        }
        double binPosition = (targetFrequencyHz * FFT_SIZE) / (double) sampleRateHz;
        int lowerBin = (int) Math.floor(binPosition);
        int upperBin = Math.min((FFT_SIZE / 2) - 1, lowerBin + 1);
        lowerBin = Math.max(0, Math.min((FFT_SIZE / 2) - 1, lowerBin));
        double ratio = Math.max(0.0d, Math.min(1.0d, binPosition - lowerBin));
        double lowerMagnitude = magnitudeAtBin(lowerBin);
        double upperMagnitude = magnitudeAtBin(upperBin);
        return (float) (lowerMagnitude + ((upperMagnitude - lowerMagnitude) * ratio));
    }

    private double magnitudeAtBin(int binIndex) {
        if (binIndex < 0 || binIndex >= fftReal.length) {
            return 0.0d;
        }
        double real = fftReal[binIndex];
        double imaginary = fftImag[binIndex];
        return Math.sqrt((real * real) + (imaginary * imaginary));
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
        int baselineCount = Math.max(1, ordered.length / 4);
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

    private double hannWindow(int index, int length) {
        if (length <= 1) {
            return 1.0d;
        }
        return 0.5d * (1.0d - Math.cos((2.0d * Math.PI * index) / (length - 1)));
    }

    private void fft(double[] real, double[] imaginary) {
        int size = real.length;
        int levels = Integer.numberOfTrailingZeros(size);
        if ((1 << levels) != size) {
            throw new IllegalArgumentException("FFT size must be a power of two");
        }

        for (int index = 0; index < size; index++) {
            int reversed = Integer.reverse(index) >>> (32 - levels);
            if (reversed > index) {
                double tempReal = real[index];
                real[index] = real[reversed];
                real[reversed] = tempReal;

                double tempImag = imaginary[index];
                imaginary[index] = imaginary[reversed];
                imaginary[reversed] = tempImag;
            }
        }

        for (int blockSize = 2; blockSize <= size; blockSize <<= 1) {
            double angleStep = (-2.0d * Math.PI) / blockSize;
            int halfBlock = blockSize >>> 1;
            for (int blockStart = 0; blockStart < size; blockStart += blockSize) {
                for (int pair = 0; pair < halfBlock; pair++) {
                    double angle = angleStep * pair;
                    double twiddleReal = Math.cos(angle);
                    double twiddleImag = Math.sin(angle);
                    int evenIndex = blockStart + pair;
                    int oddIndex = evenIndex + halfBlock;

                    double oddReal = (real[oddIndex] * twiddleReal) - (imaginary[oddIndex] * twiddleImag);
                    double oddImag = (real[oddIndex] * twiddleImag) + (imaginary[oddIndex] * twiddleReal);

                    real[oddIndex] = real[evenIndex] - oddReal;
                    imaginary[oddIndex] = imaginary[evenIndex] - oddImag;
                    real[evenIndex] += oddReal;
                    imaginary[evenIndex] += oddImag;
                }
            }
        }
    }
}
