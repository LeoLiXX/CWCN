package org.bi9clt.cwcn.core.app;

import android.content.Context;
import android.content.SharedPreferences;

public final class RxInputSettingsStore {
    private static final String PREFS_NAME = "cwcn_rx_input_settings";
    private static final String KEY_RX_INPUT_MODE = "rx_input_mode";
    private static final String KEY_MIC_SOURCE_MODE = "mic_source_mode";
    private static final String KEY_RX_TONE_MODE = "rx_tone_mode";

    public enum RxInputMode {
        AUTO("自动", "无电台时优先走手机麦克风；有正式路由时优先尝试外部音频输入。"),
        PHONE_MICROPHONE("手机麦克风", "始终使用手机麦克风作为接收输入。"),
        USB_EXTERNAL_AUDIO("USB/外部音频", "优先选择 Android 已路由的 USB 外部音频输入。");

        private final String displayName;
        private final String description;

        RxInputMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String displayName() {
            return displayName;
        }

        public String description() {
            return description;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public enum MicSourceMode {
        UNPROCESSED("原始输入", "优先绕开系统语音增强，适合固定侧音测试。"),
        VOICE_RECOGNITION("语音识别", "部分手机会关闭一部分通话类处理，适合作为折中模式。"),
        MIC("标准麦克风", "兼容性最高，但最可能带入系统降噪和语音处理。");

        private final String displayName;
        private final String description;

        MicSourceMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String displayName() {
            return displayName;
        }

        public String description() {
            return description;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public enum RxToneMode {
        FIXED_TONE("固定音调", "围绕设定音调做窄窗口锁定，优先减少误跟踪。"),
        AUTO_TRACK("自动跟踪", "允许在更大范围内搜索峰值，更适合未知音调。");

        private final String displayName;
        private final String description;

        RxToneMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String displayName() {
            return displayName;
        }

        public String description() {
            return description;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private final SharedPreferences preferences;

    public RxInputSettingsStore(Context context) {
        Context appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public RxInputMode rxInputMode() {
        return parseMode(
                preferences.getString(KEY_RX_INPUT_MODE, RxInputMode.AUTO.name()),
                RxInputMode.AUTO,
                RxInputMode.class
        );
    }

    public void setRxInputMode(RxInputMode mode) {
        preferences.edit()
                .putString(KEY_RX_INPUT_MODE, mode == null ? RxInputMode.AUTO.name() : mode.name())
                .apply();
    }

    public MicSourceMode micSourceMode() {
        return parseMode(
                preferences.getString(KEY_MIC_SOURCE_MODE, MicSourceMode.MIC.name()),
                MicSourceMode.MIC,
                MicSourceMode.class
        );
    }

    public void setMicSourceMode(MicSourceMode mode) {
        preferences.edit()
                .putString(KEY_MIC_SOURCE_MODE, mode == null ? MicSourceMode.MIC.name() : mode.name())
                .apply();
    }

    public RxToneMode rxToneMode() {
        return parseMode(
                preferences.getString(KEY_RX_TONE_MODE, RxToneMode.AUTO_TRACK.name()),
                RxToneMode.AUTO_TRACK,
                RxToneMode.class
        );
    }

    public void setRxToneMode(RxToneMode mode) {
        preferences.edit()
                .putString(KEY_RX_TONE_MODE, mode == null ? RxToneMode.AUTO_TRACK.name() : mode.name())
                .apply();
    }

    private <T extends Enum<T>> T parseMode(String stored, T fallback, Class<T> enumType) {
        if (stored == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(enumType, stored);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
