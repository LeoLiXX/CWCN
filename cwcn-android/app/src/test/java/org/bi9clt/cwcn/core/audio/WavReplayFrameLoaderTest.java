package org.bi9clt.cwcn.core.audio;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WavReplayFrameLoaderTest {
    @Test
    public void loadSplitsMonoWaveIntoZeroBasedFrames() throws Exception {
        Path wavFile = Files.createTempFile("cwcn-wav-loader-mono", ".wav");
        try {
            short[] monoSamples = new short[300];
            for (int index = 0; index < monoSamples.length; index++) {
                monoSamples[index] = (short) (index * 100);
            }
            writePcm16Wave(wavFile, 8000, 1, monoSamples);

            WavReplayFrameLoader.LoadedWav loadedWav =
                    new WavReplayFrameLoader().load(wavFile.toFile());

            assertEquals(8000, loadedWav.sampleRateHz());
            assertEquals(1, loadedWav.channelCount());
            assertEquals(300L, loadedWav.sampleCount());
            assertEquals(38L, loadedWav.durationMs());
            assertEquals(2, loadedWav.frames().size());
            assertEquals(256, loadedWav.frames().get(0).sampleCount());
            assertEquals(44, loadedWav.frames().get(1).sampleCount());
            assertEquals(0L, loadedWav.frames().get(0).capturedAtMs());
            assertEquals(32L, loadedWav.frames().get(1).capturedAtMs());
            assertEquals(25500, loadedWav.frames().get(0).peakAmplitude());
            assertEquals(29900, loadedWav.frames().get(1).peakAmplitude());
            assertEquals(0, loadedWav.frames().get(0).samples()[0]);
            assertEquals(25500, loadedWav.frames().get(0).samples()[255]);
            assertEquals(25600, loadedWav.frames().get(1).samples()[0]);
            assertEquals(29900, loadedWav.frames().get(1).samples()[43]);
        } finally {
            Files.deleteIfExists(wavFile);
        }
    }

    @Test
    public void loadMixesStereoWaveToMono() throws Exception {
        Path wavFile = Files.createTempFile("cwcn-wav-loader-stereo", ".wav");
        try {
            short[] interleavedStereoSamples = new short[]{
                    1000, 3000,
                    2000, -2000,
                    -3000, -1000,
                    500, 1500
            };
            writePcm16Wave(wavFile, 8000, 2, interleavedStereoSamples);

            WavReplayFrameLoader.LoadedWav loadedWav =
                    new WavReplayFrameLoader().load(wavFile.toFile(), 8);

            assertEquals(8000, loadedWav.sampleRateHz());
            assertEquals(2, loadedWav.channelCount());
            assertEquals(4L, loadedWav.sampleCount());
            assertEquals(1, loadedWav.frames().size());
            assertArrayEquals(
                    new short[]{2000, 0, -2000, 1000},
                    loadedWav.frames().get(0).samples()
            );
        } finally {
            Files.deleteIfExists(wavFile);
        }
    }

    @Test
    public void loadReadsWaveFromInputStream() throws Exception {
        byte[] wavBytes;
        short[] monoSamples = new short[]{100, 200, -300, 400};
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            writePcm16Wave(outputStream, 16000, 1, monoSamples);
            wavBytes = outputStream.toByteArray();
        }

        WavReplayFrameLoader.LoadedWav loadedWav = new WavReplayFrameLoader().load(
                new ByteArrayInputStream(wavBytes),
                "memory://fixture.wav",
                8
        );

        assertEquals(16000, loadedWav.sampleRateHz());
        assertEquals(1, loadedWav.channelCount());
        assertEquals(4L, loadedWav.sampleCount());
        assertEquals(1, loadedWav.frames().size());
        assertArrayEquals(monoSamples, loadedWav.frames().get(0).samples());
    }

    private void writePcm16Wave(
            Path wavFile,
            int sampleRateHz,
            int channelCount,
            short[] interleavedSamples
    ) throws IOException {
        int safeChannelCount = Math.max(1, channelCount);
        short[] safeSamples = interleavedSamples == null ? new short[0] : interleavedSamples;
        int dataSizeBytes = safeSamples.length * 2;
        try (OutputStream outputStream = new FileOutputStream(wavFile.toFile())) {
            writePcm16Wave(outputStream, sampleRateHz, safeChannelCount, safeSamples);
        }
    }

    private void writePcm16Wave(
            OutputStream outputStream,
            int sampleRateHz,
            int channelCount,
            short[] interleavedSamples
    ) throws IOException {
        int safeChannelCount = Math.max(1, channelCount);
        short[] safeSamples = interleavedSamples == null ? new short[0] : interleavedSamples;
        int dataSizeBytes = safeSamples.length * 2;
        try {
            writeAscii(outputStream, "RIFF");
            writeLittleEndianInt(outputStream, 36 + dataSizeBytes);
            writeAscii(outputStream, "WAVE");
            writeAscii(outputStream, "fmt ");
            writeLittleEndianInt(outputStream, 16);
            writeLittleEndianShort(outputStream, 1);
            writeLittleEndianShort(outputStream, safeChannelCount);
            writeLittleEndianInt(outputStream, sampleRateHz);
            writeLittleEndianInt(outputStream, sampleRateHz * safeChannelCount * 2);
            writeLittleEndianShort(outputStream, safeChannelCount * 2);
            writeLittleEndianShort(outputStream, 16);
            writeAscii(outputStream, "data");
            writeLittleEndianInt(outputStream, dataSizeBytes);
            for (short sample : safeSamples) {
                writeLittleEndianShort(outputStream, sample);
            }
        } finally {
            outputStream.flush();
        }
    }

    private void writeAscii(OutputStream outputStream, String text) throws IOException {
        outputStream.write(text.getBytes("US-ASCII"));
    }

    private void writeLittleEndianInt(OutputStream outputStream, int value) throws IOException {
        outputStream.write(value & 0xff);
        outputStream.write((value >>> 8) & 0xff);
        outputStream.write((value >>> 16) & 0xff);
        outputStream.write((value >>> 24) & 0xff);
    }

    private void writeLittleEndianShort(OutputStream outputStream, int value) throws IOException {
        outputStream.write(value & 0xff);
        outputStream.write((value >>> 8) & 0xff);
    }
}
