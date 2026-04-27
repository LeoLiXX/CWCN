package org.bi9clt.cwcn.core.tx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TxPcmToneRendererTest {
    @Test
    public void buildTonePcmOnlyAppliesEnvelopeAtElementEdges() {
        short[] pcm = TxPcmToneRenderer.buildTonePcm(48000, 731, 100, 0.45d, 4);

        double openingAverage = averageAbs(pcm, 0, 64);
        double chunkBoundaryAverage = averageAbs(pcm, 960 - 64, 128);
        double steadyStateAverage = averageAbs(pcm, 1440, 128);
        double endingAverage = averageAbs(pcm, pcm.length - 64, 64);

        assertTrue(chunkBoundaryAverage > openingAverage * 2.5d);
        assertTrue(chunkBoundaryAverage > steadyStateAverage * 0.7d);
        assertTrue(steadyStateAverage > endingAverage * 2.5d);
    }

    @Test
    public void buildSilencePcmReturnsZeros() {
        short[] pcm = TxPcmToneRenderer.buildSilencePcm(48000, 25);

        assertEquals(1200, pcm.length);
        for (short sample : pcm) {
            assertEquals(0, sample);
        }
    }

    private double averageAbs(short[] pcm, int start, int length) {
        int end = Math.min(pcm.length, start + length);
        long total = 0L;
        for (int index = Math.max(0, start); index < end; index++) {
            total += Math.abs((int) pcm[index]);
        }
        return total / (double) Math.max(1, end - Math.max(0, start));
    }
}
