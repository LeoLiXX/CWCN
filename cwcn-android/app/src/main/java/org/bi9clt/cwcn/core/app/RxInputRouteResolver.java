package org.bi9clt.cwcn.core.app;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.rig.RigProfile;

/**
 * Resolves the effective formal RX input route from the configured policy and
 * current device availability.
 */
public final class RxInputRouteResolver {
    public enum Source {
        NONE,
        PHONE_MICROPHONE,
        USB_EXTERNAL_AUDIO
    }

    private RxInputRouteResolver() {
    }

    public static Source resolve(
            @Nullable RxInputSettingsStore.RxInputMode configuredMode,
            boolean phoneFallbackEnabled,
            boolean usbExternalAudioAvailable,
            @Nullable RigProfile selectedProfile
    ) {
        RxInputSettingsStore.RxInputMode safeMode = configuredMode == null
                ? RxInputSettingsStore.RxInputMode.AUTO
                : configuredMode;
        if (safeMode == RxInputSettingsStore.RxInputMode.PHONE_MICROPHONE) {
            return Source.PHONE_MICROPHONE;
        }
        if (safeMode == RxInputSettingsStore.RxInputMode.USB_EXTERNAL_AUDIO) {
            return Source.USB_EXTERNAL_AUDIO;
        }
        if (selectedProfile != null && usbExternalAudioAvailable) {
            return Source.USB_EXTERNAL_AUDIO;
        }
        if (phoneFallbackEnabled) {
            return Source.PHONE_MICROPHONE;
        }
        return Source.NONE;
    }
}
