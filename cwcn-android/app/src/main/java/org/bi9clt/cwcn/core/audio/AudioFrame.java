package org.bi9clt.cwcn.core.audio;

public final class AudioFrame {
    private final short[] samples;
    private final int sampleRateHz;
    private final int channelCount;
    private final int peakAmplitude;
    private final double rmsAmplitude;
    private final long capturedAtMs;

    public AudioFrame(
            short[] samples,
            int sampleRateHz,
            int channelCount,
            int peakAmplitude,
            double rmsAmplitude,
            long capturedAtMs
    ) {
        this.samples = samples;
        this.sampleRateHz = sampleRateHz;
        this.channelCount = channelCount;
        this.peakAmplitude = peakAmplitude;
        this.rmsAmplitude = rmsAmplitude;
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

    public long capturedAtMs() {
        return capturedAtMs;
    }

    public int sampleCount() {
        return samples.length;
    }
}
