package org.bi9clt.cwcn.core.audio;

import java.util.Arrays;

public final class AudioFrame {
    private final short[] samples;
    private final int sampleRateHz;
    private final int channelCount;
    private final int peakAmplitude;
    private final double rmsAmplitude;
    private final int clippedSampleCount;
    private final long capturedAtMs;

    public AudioFrame(
            short[] samples,
            int sampleRateHz,
            int channelCount,
            int peakAmplitude,
            double rmsAmplitude,
            long capturedAtMs
    ) {
        this(samples, sampleRateHz, channelCount, peakAmplitude, rmsAmplitude, 0, capturedAtMs);
    }

    public AudioFrame(
            short[] samples,
            int sampleRateHz,
            int channelCount,
            int peakAmplitude,
            double rmsAmplitude,
            int clippedSampleCount,
            long capturedAtMs
    ) {
        this.samples = samples;
        this.sampleRateHz = sampleRateHz;
        this.channelCount = channelCount;
        this.peakAmplitude = peakAmplitude;
        this.rmsAmplitude = rmsAmplitude;
        this.clippedSampleCount = Math.max(0, clippedSampleCount);
        this.capturedAtMs = capturedAtMs;
    }

    public short[] samples() {
        return samples;
    }

    public int sampleRateHz() {
        return sampleRateHz;
    }

    public int channelCount() {
        return channelCount;
    }

    public int peakAmplitude() {
        return peakAmplitude;
    }

    public double rmsAmplitude() {
        return rmsAmplitude;
    }

    public int clippedSampleCount() {
        return clippedSampleCount;
    }

    public double clippedSampleRatio() {
        int sampleCount = sampleCount();
        if (sampleCount <= 0) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, clippedSampleCount / (double) sampleCount));
    }

    public boolean likelyClipping() {
        return clippedSampleCount > 0;
    }

    public long capturedAtMs() {
        return capturedAtMs;
    }

    public int sampleCount() {
        return samples.length;
    }

    public AudioFrame scaled(double linearGain) {
        if (samples.length == 0) {
            return this;
        }
        if (Math.abs(linearGain - 1.0d) < 0.0001d) {
            return this;
        }
        if (linearGain <= 0.0d) {
            return new AudioFrame(
                    new short[samples.length],
                    sampleRateHz,
                    channelCount,
                    0,
                    0.0d,
                    0,
                    capturedAtMs
            );
        }
        short[] scaledSamples = Arrays.copyOf(samples, samples.length);
        int peak = 0;
        int clippedSampleCount = 0;
        double sumSquares = 0.0d;
        for (int index = 0; index < scaledSamples.length; index++) {
            double scaledSample = scaledSamples[index] * linearGain;
            int rounded = (int) Math.round(scaledSample);
            if (rounded > Short.MAX_VALUE) {
                rounded = Short.MAX_VALUE;
                clippedSampleCount += 1;
            } else if (rounded < Short.MIN_VALUE) {
                rounded = Short.MIN_VALUE;
                clippedSampleCount += 1;
            }
            short stored = (short) rounded;
            scaledSamples[index] = stored;
            int absolute = Math.abs((int) stored);
            if (absolute > peak) {
                peak = absolute;
            }
            sumSquares += (double) stored * stored;
        }
        double rms = Math.sqrt(sumSquares / scaledSamples.length);
        return new AudioFrame(
                scaledSamples,
                sampleRateHz,
                channelCount,
                peak,
                rms,
                clippedSampleCount,
                capturedAtMs
        );
    }
}
