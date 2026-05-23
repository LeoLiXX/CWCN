package org.bi9clt.cwcn.core.audio;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;
import android.os.SystemClock;

import androidx.core.content.ContextCompat;

import org.bi9clt.cwcn.core.app.RxInputSettingsStore;

import java.util.Arrays;

public final class MicrophoneRxAudioSource implements RxAudioSource {
    private static final int SAMPLE_RATE_HZ = 16000;
    private static final int CHANNEL_COUNT = 1;
    private static final int FRAME_SIZE_SAMPLES = 256;
    private static final int CLIPPING_SAMPLE_THRESHOLD = 32700;

    private final Context appContext;
    private final RxInputSettingsStore.MicSourceMode sourceMode;

    private volatile Callback callback;
    private volatile State state = State.IDLE;
    private volatile boolean running;

    private AudioRecord audioRecord;
    private Thread workerThread;
    private NoiseSuppressor noiseSuppressor;
    private AutomaticGainControl automaticGainControl;
    private AcousticEchoCanceler acousticEchoCanceler;

    public MicrophoneRxAudioSource(Context context, RxInputSettingsStore.MicSourceMode sourceMode) {
        this.appContext = context.getApplicationContext();
        this.sourceMode = sourceMode == null
                ? RxInputSettingsStore.MicSourceMode.UNPROCESSED
                : sourceMode;
    }

    @Override
    public String id() {
        return "phone-microphone";
    }

    @Override
    public String displayName() {
        return "手机麦克风 (" + sourceMode.displayName(appContext) + ")";
    }

    @Override
    public boolean isAvailable() {
        return appContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE);
    }

    @Override
    public State state() {
        return state;
    }

    @Override
    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    @Override
    public synchronized void start() {
        if (state == State.RUNNING || state == State.STARTING) {
            return;
        }

        if (!isAvailable()) {
            setErrorState("设备没有可用麦克风。", null);
            return;
        }

        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            setErrorState("还没有授予麦克风权限。", null);
            return;
        }

        int minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        if (minBufferSize <= 0) {
            setErrorState("无法获取 AudioRecord 最小缓冲区。", null);
            return;
        }

        int bufferSize = Math.max(minBufferSize, FRAME_SIZE_SAMPLES * 4);
        try {
            audioRecord = new AudioRecord(
                    resolveAudioSource(),
                    SAMPLE_RATE_HZ,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
            );
        } catch (IllegalArgumentException exception) {
            setErrorState("AudioRecord 初始化失败。", exception);
            return;
        }

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            setErrorState("AudioRecord 未能正常初始化。", null);
            releaseRecorder();
            return;
        }

        attachMinimalAudioEffectsPolicy();
        running = true;
        updateState(State.STARTING, "正在准备麦克风采集。");
        workerThread = new Thread(this::captureLoop, "cwcn-mic-source");
        workerThread.start();
    }

    @Override
    public synchronized void stop() {
        if (state == State.IDLE || state == State.STOPPING) {
            return;
        }
        running = false;
        updateState(State.STOPPING, "正在停止采集。");
        stopRecorderSafely();
        joinWorkerThread();
        releaseRecorder();
        updateState(State.IDLE, "采集已停止。");
    }

    @Override
    public synchronized void release() {
        stop();
    }

    private void captureLoop() {
        try {
            audioRecord.startRecording();
            updateState(State.RUNNING, "麦克风采集已启动。");

            short[] readBuffer = new short[FRAME_SIZE_SAMPLES];
            long captureStartedAtMs = SystemClock.elapsedRealtime();
            long emittedSampleCount = 0L;
            while (running) {
                int readCount = audioRecord.read(readBuffer, 0, readBuffer.length);
                if (readCount <= 0) {
                    continue;
                }

                short[] samples = Arrays.copyOf(readBuffer, readCount);
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

                double rms = Math.sqrt(sumSquares / readCount);
                long capturedAtMs = captureStartedAtMs
                        + Math.round(emittedSampleCount * 1000.0d / SAMPLE_RATE_HZ);
                emittedSampleCount += readCount;
                Callback currentCallback = callback;
                if (currentCallback != null) {
                    currentCallback.onAudioFrame(new AudioFrame(
                            samples,
                            SAMPLE_RATE_HZ,
                            CHANNEL_COUNT,
                            peak,
                            rms,
                            clippedSampleCount,
                            capturedAtMs
                    ));
                }
            }
        } catch (IllegalStateException exception) {
            setErrorState("麦克风采集过程中发生错误。", exception);
        } finally {
            stopRecorderSafely();
            releaseRecorder();
            if (state != State.ERROR) {
                updateState(State.IDLE, "采集线程已退出。");
            }
        }
    }

    private void updateState(State newState, String detail) {
        state = newState;
        Callback currentCallback = callback;
        if (currentCallback != null) {
            currentCallback.onStateChanged(newState, detail);
        }
    }

    private void setErrorState(String message, Throwable throwable) {
        state = State.ERROR;
        Callback currentCallback = callback;
        if (currentCallback != null) {
            currentCallback.onStateChanged(State.ERROR, message);
            currentCallback.onError(message, throwable);
        }
    }

    private void stopRecorderSafely() {
        if (audioRecord == null) {
            return;
        }
        try {
            if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord.stop();
            }
        } catch (IllegalStateException ignored) {
        }
    }

    private void releaseRecorder() {
        releaseEffects();
        if (audioRecord != null) {
            audioRecord.release();
            audioRecord = null;
        }
    }

    private void joinWorkerThread() {
        if (workerThread == null) {
            return;
        }
        try {
            workerThread.join(500);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        } finally {
            workerThread = null;
        }
    }

    private int resolveAudioSource() {
        switch (sourceMode) {
            case UNPROCESSED:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    return MediaRecorder.AudioSource.UNPROCESSED;
                }
                return MediaRecorder.AudioSource.VOICE_RECOGNITION;
            case VOICE_RECOGNITION:
                return MediaRecorder.AudioSource.VOICE_RECOGNITION;
            case MIC:
            default:
                return MediaRecorder.AudioSource.MIC;
        }
    }

    private void attachMinimalAudioEffectsPolicy() {
        if (audioRecord == null) {
            return;
        }
        int audioSessionId = audioRecord.getAudioSessionId();
        if (audioSessionId == AudioRecord.ERROR || audioSessionId == AudioRecord.ERROR_BAD_VALUE) {
            return;
        }
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(audioSessionId);
            if (noiseSuppressor != null) {
                try {
                    noiseSuppressor.setEnabled(false);
                } catch (IllegalStateException ignored) {
                }
            }
        }
        if (AutomaticGainControl.isAvailable()) {
            automaticGainControl = AutomaticGainControl.create(audioSessionId);
            if (automaticGainControl != null) {
                try {
                    automaticGainControl.setEnabled(false);
                } catch (IllegalStateException ignored) {
                }
            }
        }
        if (AcousticEchoCanceler.isAvailable()) {
            acousticEchoCanceler = AcousticEchoCanceler.create(audioSessionId);
            if (acousticEchoCanceler != null) {
                try {
                    acousticEchoCanceler.setEnabled(false);
                } catch (IllegalStateException ignored) {
                }
            }
        }
    }

    private void releaseEffects() {
        if (noiseSuppressor != null) {
            noiseSuppressor.release();
            noiseSuppressor = null;
        }
        if (automaticGainControl != null) {
            automaticGainControl.release();
            automaticGainControl = null;
        }
        if (acousticEchoCanceler != null) {
            acousticEchoCanceler.release();
            acousticEchoCanceler = null;
        }
    }
}
