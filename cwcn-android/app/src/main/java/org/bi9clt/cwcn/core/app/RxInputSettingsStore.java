package org.bi9clt.cwcn.core.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.StringRes;

import org.bi9clt.cwcn.R;

public final class RxInputSettingsStore {
    private static final String PREFS_NAME = "cwcn_rx_input_settings";
    private static final String KEY_RX_INPUT_MODE = "rx_input_mode";
    private static final String KEY_MIC_SOURCE_MODE = "mic_source_mode";
    private static final String KEY_RX_TONE_MODE = "rx_tone_mode";
    private static final String KEY_FIXED_TONE_LEARNING_WINDOW_HZ = "fixed_tone_learning_window_hz";

    public enum RxInputMode {
        AUTO(
                R.string.settings_rx_input_mode_auto,
                R.string.settings_rx_input_mode_auto_description
        ),
        PHONE_MICROPHONE(
                R.string.settings_rx_input_mode_phone_microphone,
                R.string.settings_rx_input_mode_phone_microphone_description
        ),
        USB_EXTERNAL_AUDIO(
                R.string.settings_rx_input_mode_usb_external_audio,
                R.string.settings_rx_input_mode_usb_external_audio_description
        );

        private final int displayNameResId;
        private final int descriptionResId;

        RxInputMode(@StringRes int displayNameResId, @StringRes int descriptionResId) {
            this.displayNameResId = displayNameResId;
            this.descriptionResId = descriptionResId;
        }

        @StringRes
        public int displayNameResId() {
            return displayNameResId;
        }

        @StringRes
        public int descriptionResId() {
            return descriptionResId;
        }

        public String displayName(Context context) {
            return context == null ? name() : context.getString(displayNameResId);
        }
    }

    public enum MicSourceMode {
        UNPROCESSED(
                R.string.settings_mic_source_mode_unprocessed,
                R.string.settings_mic_source_mode_unprocessed_description
        ),
        VOICE_RECOGNITION(
                R.string.settings_mic_source_mode_voice_recognition,
                R.string.settings_mic_source_mode_voice_recognition_description
        ),
        MIC(
                R.string.settings_mic_source_mode_mic,
                R.string.settings_mic_source_mode_mic_description
        );

        private final int displayNameResId;
        private final int descriptionResId;

        MicSourceMode(@StringRes int displayNameResId, @StringRes int descriptionResId) {
            this.displayNameResId = displayNameResId;
            this.descriptionResId = descriptionResId;
        }

        @StringRes
        public int displayNameResId() {
            return displayNameResId;
        }

        @StringRes
        public int descriptionResId() {
            return descriptionResId;
        }

        public String displayName(Context context) {
            return context == null ? name() : context.getString(displayNameResId);
        }
    }

    public enum RxToneMode {
        FIXED_TONE(
                R.string.settings_rx_tone_mode_fixed_tone,
                R.string.settings_rx_tone_mode_fixed_tone_description
        ),
        AUTO_TRACK(
                R.string.settings_rx_tone_mode_auto_track,
                R.string.settings_rx_tone_mode_auto_track_description
        );

        private final int displayNameResId;
        private final int descriptionResId;

        RxToneMode(@StringRes int displayNameResId, @StringRes int descriptionResId) {
            this.displayNameResId = displayNameResId;
            this.descriptionResId = descriptionResId;
        }

        @StringRes
        public int displayNameResId() {
            return displayNameResId;
        }

        @StringRes
        public int descriptionResId() {
            return descriptionResId;
        }

        public String displayName(Context context) {
            return context == null ? name() : context.getString(displayNameResId);
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

    public int fixedToneLearningWindowHz() {
        return org.bi9clt.cwcn.core.signal.CwSignalProcessor.clampFixedToneLearningWindowHz(
                preferences.getInt(
                        KEY_FIXED_TONE_LEARNING_WINDOW_HZ,
                        org.bi9clt.cwcn.core.signal.CwSignalProcessor.DEFAULT_FIXED_TONE_LEARNING_WINDOW_HZ
                )
        );
    }

    public void setFixedToneLearningWindowHz(int windowHz) {
        preferences.edit()
                .putInt(
                        KEY_FIXED_TONE_LEARNING_WINDOW_HZ,
                        org.bi9clt.cwcn.core.signal.CwSignalProcessor.clampFixedToneLearningWindowHz(windowHz)
                )
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
