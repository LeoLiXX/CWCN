package org.bi9clt.cwcn.core.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.bi9clt.cwcn.R;
import org.bi9clt.cwcn.core.rig.RigCapability;
import org.bi9clt.cwcn.core.rig.RigProfile;
import org.bi9clt.cwcn.core.rig.RigTransport;

public final class OperateRouteModeStore {
    private static final String PREFS_NAME = "cwcn_operate_route_mode";
    private static final String PROFILE_KEY_PREFIX = "profile:";
    private static final String KEY_MODE = "operate_route_mode";

    public enum Mode {
        STANDARD_AUTO(
                R.string.rig_setup_route_mode_standard_auto,
                R.string.rig_setup_route_mode_standard_auto_description
        ),
        HYBRID_PHONE_RX(
                R.string.rig_setup_route_mode_hybrid_phone_rx,
                R.string.rig_setup_route_mode_hybrid_phone_rx_description
        );

        private final int displayNameResId;
        private final int descriptionResId;

        Mode(@StringRes int displayNameResId, @StringRes int descriptionResId) {
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
    }

    private final SharedPreferences preferences;

    public OperateRouteModeStore(Context context) {
        Context appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public Mode mode(@Nullable RigProfile profile) {
        return mode(profile == null ? null : profile.id(), profile);
    }

    public Mode mode(@Nullable String profileId) {
        return mode(profileId, null);
    }

    public void setMode(@Nullable RigProfile profile, @Nullable Mode mode) {
        setMode(profile == null ? null : profile.id(), profile, mode);
    }

    public void setMode(@Nullable String profileId, @Nullable Mode mode) {
        setMode(profileId, null, mode);
    }

    public boolean supportsHybridPhoneRx(@Nullable RigProfile profile) {
        return profile != null
                && profile.transportKind() == RigTransport.TransportKind.USB_SERIAL
                && profile.hasCapability(RigCapability.SERIAL_CAT);
    }

    public Mode sanitize(@Nullable RigProfile profile, @Nullable Mode mode) {
        Mode safeMode = mode == null ? Mode.STANDARD_AUTO : mode;
        if (safeMode == Mode.HYBRID_PHONE_RX && !supportsHybridPhoneRx(profile)) {
            return Mode.STANDARD_AUTO;
        }
        return safeMode;
    }

    private Mode mode(@Nullable String profileId, @Nullable RigProfile profile) {
        String stored = preferences.getString(scopedKey(profileId), Mode.STANDARD_AUTO.name());
        Mode parsed = parseMode(stored);
        return sanitize(profile, parsed);
    }

    private void setMode(
            @Nullable String profileId,
            @Nullable RigProfile profile,
            @Nullable Mode mode
    ) {
        if (profileId == null || profileId.trim().isEmpty()) {
            return;
        }
        preferences.edit()
                .putString(scopedKey(profileId), sanitize(profile, mode).name())
                .apply();
    }

    private Mode parseMode(@Nullable String stored) {
        if (stored == null) {
            return Mode.STANDARD_AUTO;
        }
        try {
            return Mode.valueOf(stored);
        } catch (IllegalArgumentException ignored) {
            return Mode.STANDARD_AUTO;
        }
    }

    private String scopedKey(@Nullable String profileId) {
        if (profileId == null || profileId.trim().isEmpty()) {
            return KEY_MODE;
        }
        return PROFILE_KEY_PREFIX + profileId.trim() + ":" + KEY_MODE;
    }
}
