package org.bi9clt.cwcn.core.audio;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.bi9clt.cwcn.core.app.RxInputSettingsStore;

import java.util.Arrays;

public final class UsbExternalRxAudioSource implements RxAudioSource {
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
    private String activeDeviceLabel = "USB External Audio";

    public UsbExternalRxAudioSource(Context context, RxInputSettingsStore.MicSourceMode sourceMode) {
        this.appContext = context.getApplicationContext();
        this.sourceMode = sourceMode == null
                ? RxInputSettingsStore.MicSourceMode.UNPROCESSED
                : sourceMode;
    }

    @Override
    public String id() {
        return "usb-external-audio";
    }

    @Override
    public String displayName() {
        return activeDeviceLabel + " (" + sourceMode.displayName() + ")";
    }

    @Override
    public boolean isAvailable() {
        return findUsbInputDevice() != null;
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
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            setErrorState("还没有授予录音权限。", null);
            return;
        }

        AudioDeviceInfo usbDevice = findUsbInputDevice();
        if (usbDevice == null) {
            setErrorState("没有检测到可用的 USB 音频输入设备。", null);
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
        activeDeviceLabel = buildDeviceLabel(usbDevice);
        try {
            AudioFormat audioFormat = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE_HZ)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build();
            audioRecord = new AudioRecord.Builder()
                    .setAudioSource(resolveAudioSource())
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(bufferSize)
                    .build();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioRecord.setPreferredDevice(usbDevice);
            }
        } catch (IllegalArgumentException exception) {
            setErrorState("USB 音频输入初始化失败。", exception);
            return;
        }

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            setErrorState("USB 音频输入未能正常初始化。", null);
            releaseRecorder();
            return;
        }

        AudioDeviceInfo routedDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? audioRecord.getRoutedDevice()
                : null;
        if (routedDevice != null && isUsbInputDevice(routedDevice)) {
            activeDeviceLabel = buildDeviceLabel(routedDevice);
        }

        attachMinimalAudioEffectsPolicy();
        running = true;
        updateState(State.STARTING, "正在准备外部音频输入。");
        workerThread = new Thread(this::captureLoop, "cwcn-usb-rx-source");
        workerThread.start();
    }

    @Override
    public synchronized void stop() {
        if (state == State.IDLE || state == State.STOPPING) {
            return;
        }
        running = false;
        updateState(State.STOPPING, "正在停止外部音频输入。");
        stopRecorderSafely();
        joinWorkerThread();
        releaseRecorder();
        updateState(State.IDLE, "外部音频输入已停止。");
    }

    @Override
    public synchronized void release() {
        stop();
    }

    private void captureLoop() {
        try {
            audioRecord.startRecording();
            AudioDeviceInfo routedDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    ? audioRecord.getRoutedDevice()
                    : null;
            if (routedDevice != null && isUsbInputDevice(routedDevice)) {
                activeDeviceLabel = buildDeviceLabel(routedDevice);
            }
            updateState(State.RUNNING, activeDeviceLabel + " 已启动。");

            short[] readBuffer = new short[FRAME_SIZE_SAMPLES];
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
                Callback currentCallback = callback;
                if (currentCallback != null) {
                    currentCallback.onAudioFrame(new AudioFrame(
                            samples,
                            SAMPLE_RATE_HZ,
                            CHANNEL_COUNT,
                            peak,
                            rms,
                            clippedSampleCount,
                            SystemClock.elapsedRealtime()
                    ));
                }
            }
        } catch (IllegalStateException exception) {
            setErrorState("外部音频输入过程中发生错误。", exception);
        } finally {
            stopRecorderSafely();
            releaseRecorder();
            if (state != State.ERROR) {
                updateState(State.IDLE, "外部音频输入线程已退出。");
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

    private void setErrorState(String message, @Nullable Throwable throwable) {
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

    @Nullable
    private AudioDeviceInfo findUsbInputDevice() {
        AudioManager audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            return null;
        }
        AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
        AudioDeviceInfo headsetCandidate = null;
        for (AudioDeviceInfo device : devices) {
            if (!device.isSource()) {
                continue;
            }
            if (device.getType() == AudioDeviceInfo.TYPE_USB_DEVICE) {
                return device;
            }
            if (device.getType() == AudioDeviceInfo.TYPE_USB_HEADSET && headsetCandidate == null) {
                headsetCandidate = device;
            }
        }
        return headsetCandidate;
    }

    private boolean isUsbInputDevice(@Nullable AudioDeviceInfo device) {
        if (device == null || !device.isSource()) {
            return false;
        }
        int type = device.getType();
        return type == AudioDeviceInfo.TYPE_USB_DEVICE || type == AudioDeviceInfo.TYPE_USB_HEADSET;
    }

    private String buildDeviceLabel(@Nullable AudioDeviceInfo device) {
        if (device == null) {
            return "USB External Audio";
        }
        CharSequence productName = device.getProductName();
        if (productName == null || productName.toString().trim().isEmpty()) {
            return "USB External Audio";
        }
        return "USB " + productName.toString().trim();
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
