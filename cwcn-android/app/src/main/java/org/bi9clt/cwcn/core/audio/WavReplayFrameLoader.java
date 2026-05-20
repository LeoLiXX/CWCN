package org.bi9clt.cwcn.core.audio;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loads a PCM16 WAV file into zero-based mono {@link AudioFrame} replay frames.
 */
public final class WavReplayFrameLoader {
    public static final int DEFAULT_FRAME_SIZE_SAMPLES = 256;

    private static final int CLIPPING_SAMPLE_THRESHOLD = 32700;

    public LoadedWav load(File wavFile) throws IOException {
        return load(wavFile, DEFAULT_FRAME_SIZE_SAMPLES);
    }

    public LoadedWav load(File wavFile, int frameSizeSamples) throws IOException {
        if (wavFile == null) {
            throw new IllegalArgumentException("wavFile == null");
        }
        if (!wavFile.exists() || !wavFile.isFile()) {
            throw new IOException("WAV file does not exist: " + wavFile.getAbsolutePath());
        }
        try (InputStream inputStream = new FileInputStream(wavFile)) {
            return load(inputStream, wavFile.getAbsolutePath(), frameSizeSamples);
        }
    }

    public LoadedWav load(InputStream inputStream, String sourceLabel) throws IOException {
        return load(inputStream, sourceLabel, DEFAULT_FRAME_SIZE_SAMPLES);
    }

    public LoadedWav load(InputStream inputStream, String sourceLabel, int frameSizeSamples) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("inputStream == null");
        }
        int safeFrameSizeSamples = Math.max(1, frameSizeSamples);
        try (DataInputStream dataInputStream = new DataInputStream(
                new BufferedInputStream(inputStream)
        )) {
            byte[] riffHeader = new byte[12];
            dataInputStream.readFully(riffHeader);
            String riff = new String(riffHeader, 0, 4, StandardCharsets.US_ASCII);
            String wave = new String(riffHeader, 8, 4, StandardCharsets.US_ASCII);
            if (!"RIFF".equals(riff) || !"WAVE".equals(wave)) {
                throw new IOException("Unsupported WAV header in " + safeSourceLabel(sourceLabel));
            }

            Integer sampleRateHz = null;
            Integer channelCount = null;
            Integer bitsPerSample = null;
            Integer audioFormat = null;
            byte[] dataBytes = null;

            while (true) {
                byte[] chunkHeader = new byte[8];
                try {
                    dataInputStream.readFully(chunkHeader);
                } catch (EOFException eofException) {
                    break;
                }
                String chunkId = new String(chunkHeader, 0, 4, StandardCharsets.US_ASCII);
                int chunkSize = littleEndianInt(chunkHeader, 4);
                if ("fmt ".equals(chunkId)) {
                    byte[] fmtBytes = new byte[chunkSize];
                    dataInputStream.readFully(fmtBytes);
                    audioFormat = littleEndianShort(fmtBytes, 0);
                    channelCount = littleEndianShort(fmtBytes, 2);
                    sampleRateHz = littleEndianInt(fmtBytes, 4);
                    bitsPerSample = chunkSize >= 16 ? littleEndianShort(fmtBytes, 14) : 0;
                } else if ("data".equals(chunkId)) {
                    dataBytes = new byte[chunkSize];
                    dataInputStream.readFully(dataBytes);
                } else {
                    skipFully(dataInputStream, chunkSize);
                }
                if ((chunkSize & 1) != 0) {
                    skipFully(dataInputStream, 1);
                }
            }

            if (sampleRateHz == null
                    || channelCount == null
                    || bitsPerSample == null
                    || audioFormat == null
                    || dataBytes == null) {
                throw new IOException("Incomplete WAV structure in " + safeSourceLabel(sourceLabel));
            }
            if (audioFormat != 1 || bitsPerSample != 16) {
                throw new IOException("Only PCM16 WAV is supported: " + safeSourceLabel(sourceLabel));
            }

            short[] monoSamples = mixToMono(dataBytes, channelCount);
            List<AudioFrame> frames = buildFrames(monoSamples, sampleRateHz, safeFrameSizeSamples);
            long durationMs = sampleRateHz <= 0
                    ? 0L
                    : Math.round(monoSamples.length * 1000.0d / sampleRateHz);
            return new LoadedWav(
                    sampleRateHz,
                    channelCount,
                    monoSamples.length,
                    durationMs,
                    frames
            );
        }
    }

    private short[] mixToMono(byte[] dataBytes, int channelCount) {
        int safeChannelCount = Math.max(1, channelCount);
        int totalShortCount = dataBytes.length / 2;
        if (safeChannelCount == 1) {
            short[] mono = new short[totalShortCount];
            for (int index = 0; index < totalShortCount; index++) {
                mono[index] = (short) littleEndianShort(dataBytes, index * 2);
            }
            return mono;
        }
        int frameCount = totalShortCount / safeChannelCount;
        short[] mono = new short[frameCount];
        for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
            int sum = 0;
            for (int channelIndex = 0; channelIndex < safeChannelCount; channelIndex++) {
                int sampleIndex = (frameIndex * safeChannelCount) + channelIndex;
                sum += (short) littleEndianShort(dataBytes, sampleIndex * 2);
            }
            mono[frameIndex] = (short) Math.max(
                    Short.MIN_VALUE,
                    Math.min(Short.MAX_VALUE, Math.round(sum / (float) safeChannelCount))
            );
        }
        return mono;
    }

    private List<AudioFrame> buildFrames(short[] monoSamples, int sampleRateHz, int frameSizeSamples) {
        ArrayList<AudioFrame> frames = new ArrayList<>();
        long sampleOffset = 0L;
        for (int offset = 0; offset < monoSamples.length; offset += frameSizeSamples) {
            int frameLength = Math.min(frameSizeSamples, monoSamples.length - offset);
            short[] frameSamples = new short[frameLength];
            System.arraycopy(monoSamples, offset, frameSamples, 0, frameLength);
            frames.add(buildFrame(frameSamples, sampleRateHz, sampleOffset));
            sampleOffset += frameLength;
        }
        return Collections.unmodifiableList(frames);
    }

    private AudioFrame buildFrame(short[] samples, int sampleRateHz, long sampleOffset) {
        int peak = 0;
        int clippedSampleCount = 0;
        double sumSquares = 0.0d;
        for (short sample : samples) {
            int absolute = Math.abs((int) sample);
            if (absolute > peak) {
                peak = absolute;
            }
            if (absolute >= CLIPPING_SAMPLE_THRESHOLD) {
                clippedSampleCount += 1;
            }
            sumSquares += (double) sample * sample;
        }
        double rms = samples.length == 0 ? 0.0d : Math.sqrt(sumSquares / samples.length);
        long capturedAtMs = sampleRateHz <= 0
                ? 0L
                : Math.round(sampleOffset * 1000.0d / sampleRateHz);
        return new AudioFrame(
                samples,
                sampleRateHz,
                1,
                peak,
                rms,
                clippedSampleCount,
                capturedAtMs
        );
    }

    private void skipFully(DataInputStream inputStream, int byteCount) throws IOException {
        int remaining = Math.max(0, byteCount);
        while (remaining > 0) {
            int skipped = inputStream.skipBytes(remaining);
            if (skipped <= 0) {
                throw new IOException("Unexpected end of WAV chunk.");
            }
            remaining -= skipped;
        }
    }

    private static int littleEndianInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff)
                | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16)
                | ((bytes[offset + 3] & 0xff) << 24);
    }

    private static int littleEndianShort(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    private static String safeSourceLabel(String sourceLabel) {
        return sourceLabel == null || sourceLabel.trim().isEmpty()
                ? "(unknown source)"
                : sourceLabel.trim();
    }

    public static final class LoadedWav {
        private final int sampleRateHz;
        private final int channelCount;
        private final long sampleCount;
        private final long durationMs;
        private final List<AudioFrame> frames;

        private LoadedWav(
                int sampleRateHz,
                int channelCount,
                long sampleCount,
                long durationMs,
                List<AudioFrame> frames
        ) {
            this.sampleRateHz = Math.max(0, sampleRateHz);
            this.channelCount = Math.max(0, channelCount);
            this.sampleCount = Math.max(0L, sampleCount);
            this.durationMs = Math.max(0L, durationMs);
            this.frames = frames == null ? Collections.emptyList() : frames;
        }

        public int sampleRateHz() {
            return sampleRateHz;
        }

        public int channelCount() {
            return channelCount;
        }

        public long sampleCount() {
            return sampleCount;
        }

        public long durationMs() {
            return durationMs;
        }

        public List<AudioFrame> frames() {
            return frames;
        }
    }
}
