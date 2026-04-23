package org.bi9clt.cwcn.core.tx;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

public class AudioTrackTxAudioOutput implements CwTxAudioOutput {
    private static final int SAMPLE_RATE_HZ = 16000;
    private static final int CHANNEL_MASK = AudioFormat.CHANNEL_OUT_MONO;
    private static final int PCM_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int CHUNK_DURATION_MS = 20;
    private static final double SIDETONE_GAIN = 0.35d;

    private final Object lock = new Object();

    private AudioTrack audioTrack;

    @Override
    public void playTone(int frequencyHz, int durationMs) throws InterruptedException {
        if (durationMs <= 0) {
            return;
        }
        AudioTrack track = ensureAudioTrack();
        int remainingMs = durationMs;
        int sampleCursor = 0;
        while (remainingMs > 0) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("TX tone playback interrupted");
            }
            int chunkMs = Math.min(CHUNK_DURATION_MS, remainingMs);
            short[] pcm = buildToneChunk(frequencyHz, chunkMs, sampleCursor);
            track.write(pcm, 0, pcm.length);
            sampleCursor += pcm.length;
            remainingMs -= chunkMs;
        }
    }

    @Override
    public void playSilence(int durationMs) throws InterruptedException {
        int remainingMs = durationMs;
        while (remainingMs > 0) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("TX silence interrupted");
            }
            int chunkMs = Math.min(CHUNK_DURATION_MS, remainingMs);
            Thread.sleep(chunkMs);
            remainingMs -= chunkMs;
        }
    }

    @Override
    public void stop() {
        synchronized (lock) {
            if (audioTrack != null) {
                try {
                    audioTrack.pause();
                    audioTrack.flush();
                } catch (IllegalStateException ignored) {
                }
                audioTrack.release();
                audioTrack = null;
            }
        }
    }

    private AudioTrack ensureAudioTrack() {
        synchronized (lock) {
            if (audioTrack != null) {
                return audioTrack;
            }
            int minBufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE_HZ,
                    CHANNEL_MASK,
                    PCM_ENCODING
            );
            int bufferSize = Math.max(minBufferSize, SAMPLE_RATE_HZ / 2);
            audioTrack = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(PCM_ENCODING)
                            .setSampleRate(SAMPLE_RATE_HZ)
                            .setChannelMask(CHANNEL_MASK)
                            .build())
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(bufferSize)
                    .build();
            audioTrack.play();
            return audioTrack;
        }
    }

    private short[] buildToneChunk(int frequencyHz, int durationMs, int sampleCursor) {
        int sampleCount = Math.max(1, SAMPLE_RATE_HZ * durationMs / 1000);
        short[] pcm = new short[sampleCount];
        for (int index = 0; index < sampleCount; index++) {
            double angle = 2.0d * Math.PI * frequencyHz * (sampleCursor + index) / SAMPLE_RATE_HZ;
            pcm[index] = (short) Math.round(Math.sin(angle) * Short.MAX_VALUE * SIDETONE_GAIN);
        }
        return pcm;
    }
}
