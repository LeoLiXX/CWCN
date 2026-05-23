package org.bi9clt.cwcn.core.tx;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

public class AudioTrackTxAudioOutput implements CwTxAudioOutput {
    private static final int SAMPLE_RATE_HZ = 48000;
    private static final int CHANNEL_MASK = AudioFormat.CHANNEL_OUT_MONO;
    private static final int PCM_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int CHUNK_DURATION_MS = 20;
    private static final double SIDETONE_GAIN = 0.45d;
    private static final int EDGE_RAMP_MS = 4;
    private static final int FINISH_TAIL_SILENCE_MS = 12;
    private static final int FINISH_SETTLE_MS = 18;

    private final Object lock = new Object();

    private AudioTrack audioTrack;
    private long framesWritten;
    private volatile boolean stopRequested;

    @Override
    public void playTone(int frequencyHz, int durationMs) throws InterruptedException {
        if (durationMs <= 0) {
            return;
        }
        AudioTrack track = ensureAudioTrack();
        short[] pcm = TxPcmToneRenderer.buildTonePcm(
                SAMPLE_RATE_HZ,
                frequencyHz,
                durationMs,
                SIDETONE_GAIN,
                EDGE_RAMP_MS
        );
        writePcm(track, pcm);
    }

    @Override
    public void playSilence(int durationMs) throws InterruptedException {
        if (durationMs <= 0) {
            return;
        }
        AudioTrack track = ensureAudioTrack();
        short[] pcm = TxPcmToneRenderer.buildSilencePcm(SAMPLE_RATE_HZ, durationMs);
        writePcm(track, pcm);
    }

    @Override
    public void stop() {
        stopRequested = true;
        synchronized (lock) {
            releaseAudioTrack(true);
        }
    }

    @Override
    public void finish() throws InterruptedException {
        AudioTrack track;
        synchronized (lock) {
            track = audioTrack;
        }
        if (track == null) {
            synchronized (lock) {
                releaseAudioTrack(false);
            }
            return;
        }
        writePcm(track, TxPcmToneRenderer.buildSilencePcm(SAMPLE_RATE_HZ, FINISH_TAIL_SILENCE_MS));
        long targetFrames;
        synchronized (lock) {
            targetFrames = framesWritten;
        }
        if (targetFrames <= 0L) {
            synchronized (lock) {
                releaseAudioTrack(false);
            }
            return;
        }
        long deadlineMs = System.currentTimeMillis() + 1500L;
        while (System.currentTimeMillis() < deadlineMs) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("TX 音频排空等待被中断");
            }
            int playedFrames;
            try {
                playedFrames = track.getPlaybackHeadPosition();
            } catch (IllegalStateException ignored) {
                break;
            }
            if (playedFrames >= targetFrames) {
                break;
            }
            Thread.sleep(5L);
        }
        Thread.sleep(FINISH_SETTLE_MS);
        synchronized (lock) {
            releaseAudioTrack(false);
        }
    }

    private AudioTrack ensureAudioTrack() {
        synchronized (lock) {
            if (audioTrack != null) {
                return audioTrack;
            }
            stopRequested = false;
            int minBufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE_HZ,
                    CHANNEL_MASK,
                    PCM_ENCODING
            );
            int halfSecondBufferBytes = SAMPLE_RATE_HZ * Short.BYTES / 2;
            int bufferSize = Math.max(minBufferSize, halfSecondBufferBytes);
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
            framesWritten = 0L;
            audioTrack.play();
            return audioTrack;
        }
    }

    private void writePcm(AudioTrack track, short[] pcm) throws InterruptedException {
        int chunkSamples = Math.max(1, SAMPLE_RATE_HZ * CHUNK_DURATION_MS / 1000);
        int offset = 0;
        while (offset < pcm.length) {
            if (stopRequested || Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("TX 音频播放被中断");
            }
            int count = Math.min(chunkSamples, pcm.length - offset);
            int written;
            try {
                written = track.write(pcm, offset, count);
            } catch (IllegalStateException exception) {
                if (stopRequested || Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("TX 音频播放被中断");
                }
                throw exception;
            }
            if (written <= 0) {
                throw new IllegalStateException("AudioTrack 写入失败：" + written);
            }
            offset += written;
            synchronized (lock) {
                framesWritten += written;
            }
        }
    }

    private void releaseAudioTrack(boolean flushBeforeRelease) {
        if (audioTrack == null) {
            framesWritten = 0L;
            return;
        }
        try {
            if (!flushBeforeRelease) {
                audioTrack.pause();
            } else {
                audioTrack.stop();
                audioTrack.flush();
            }
        } catch (IllegalStateException ignored) {
        }
        audioTrack.release();
        audioTrack = null;
        framesWritten = 0L;
    }
}
