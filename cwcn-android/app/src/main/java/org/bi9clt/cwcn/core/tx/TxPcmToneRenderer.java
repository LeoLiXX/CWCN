package org.bi9clt.cwcn.core.tx;

final class TxPcmToneRenderer {
    private TxPcmToneRenderer() {
    }

    static short[] buildTonePcm(
            int sampleRateHz,
            int frequencyHz,
            int durationMs,
            double gain,
            int edgeRampMs
    ) {
        int sampleCount = Math.max(1, sampleRateHz * durationMs / 1000);
        short[] pcm = new short[sampleCount];
        int rampSamples = Math.min(sampleCount / 2, Math.max(1, sampleRateHz * edgeRampMs / 1000));
        double clampedGain = Math.max(0.0d, Math.min(1.0d, gain));
        for (int index = 0; index < sampleCount; index++) {
            double angle = 2.0d * Math.PI * frequencyHz * index / sampleRateHz;
            double envelope = 1.0d;
            if (index < rampSamples) {
                envelope = index / (double) rampSamples;
            } else if (index >= sampleCount - rampSamples) {
                envelope = (sampleCount - 1 - index) / (double) rampSamples;
            }
            envelope = Math.max(0.0d, Math.min(1.0d, envelope));
            pcm[index] = (short) Math.round(Math.sin(angle) * Short.MAX_VALUE * clampedGain * envelope);
        }
        return pcm;
    }

    static short[] buildSilencePcm(int sampleRateHz, int durationMs) {
        int sampleCount = Math.max(1, sampleRateHz * durationMs / 1000);
        return new short[sampleCount];
    }
}
