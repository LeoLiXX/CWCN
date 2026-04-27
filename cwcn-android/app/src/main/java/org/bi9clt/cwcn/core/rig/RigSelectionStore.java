package org.bi9clt.cwcn.core.rig;

import android.content.Context;
import android.content.SharedPreferences;

public final class RigSelectionStore {
    private static final String PROFILE_KEY_PREFIX = "profile:";
    private static final String PREFS_NAME = "rig_setup";
    private static final String KEY_SELECTED_PROFILE_ID = "selected_profile_id";
    private static final String KEY_DEFAULT_WPM = "default_wpm";
    private static final String KEY_DEFAULT_TONE_FREQUENCY_HZ = "default_tone_frequency_hz";
    private static final String KEY_USB_KEY_LINE = "usb_key_line";
    private static final String KEY_USB_PREFERRED_DEVICE_NAME = "usb_preferred_device_name";
    private static final String KEY_SERIAL_CAT_PROTOCOL_FAMILY = "serial_cat_protocol_family";
    private static final String KEY_SERIAL_CAT_BAUD_RATE = "serial_cat_baud_rate";
    private static final String KEY_SERIAL_CAT_PORT_HINT = "serial_cat_port_hint";
    private static final String KEY_SERIAL_CAT_KEY_LINE = "serial_cat_key_line";
    private static final String KEY_SERIAL_CAT_KEYING_PORT_HINT = "serial_cat_keying_port_hint";
    private static final String KEY_SERIAL_CAT_KEYING_POLARITY = "serial_cat_keying_polarity";
    private static final String KEY_SERIAL_CAT_ASSERT_RTS_DURING_KEYING = "serial_cat_assert_rts_during_keying";
    private static final String KEY_SERIAL_CAT_ASSERT_DTR_DURING_KEYING = "serial_cat_assert_dtr_during_keying";
    private static final String KEY_SERIAL_CAT_CIV_ADDRESS_HEX = "serial_cat_civ_address_hex";
    private static final String KEY_NETWORK_CAT_PROTOCOL_FAMILY = "network_cat_protocol_family";
    private static final String KEY_NETWORK_HOST = "network_host";
    private static final String KEY_NETWORK_PORT = "network_port";
    private static final String KEY_BLUETOOTH_DEVICE_HINT = "bluetooth_device_hint";
    private static final String[] CONFIG_KEYS = {
            KEY_DEFAULT_WPM,
            KEY_DEFAULT_TONE_FREQUENCY_HZ,
            KEY_USB_KEY_LINE,
            KEY_USB_PREFERRED_DEVICE_NAME,
            KEY_SERIAL_CAT_PROTOCOL_FAMILY,
            KEY_SERIAL_CAT_BAUD_RATE,
            KEY_SERIAL_CAT_PORT_HINT,
            KEY_SERIAL_CAT_KEY_LINE,
            KEY_SERIAL_CAT_KEYING_PORT_HINT,
            KEY_SERIAL_CAT_KEYING_POLARITY,
            KEY_SERIAL_CAT_ASSERT_RTS_DURING_KEYING,
            KEY_SERIAL_CAT_ASSERT_DTR_DURING_KEYING,
            KEY_SERIAL_CAT_CIV_ADDRESS_HEX,
            KEY_NETWORK_CAT_PROTOCOL_FAMILY,
            KEY_NETWORK_HOST,
            KEY_NETWORK_PORT,
            KEY_BLUETOOTH_DEVICE_HINT
    };

    private final SharedPreferences preferences;

    public RigSelectionStore(Context context) {
        Context appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String selectedProfileId() {
        return preferences.getString(KEY_SELECTED_PROFILE_ID, null);
    }

    public RigProfile selectedProfile() {
        return RigProfileCatalog.findById(selectedProfileId());
    }

    public RigProfileSettings loadSettings() {
        return loadSettings(selectedProfileId());
    }

    public RigProfileSettings loadSettings(RigProfile profile) {
        return loadSettings(profile == null ? null : profile.id());
    }

    public RigProfileSettings loadSettings(String profileId) {
        RigProfile profile = RigProfileCatalog.findById(profileId);
        RigProfileSettings defaults = profile == null
                ? RigProfileSettings.defaults()
                : profile.defaultSettings();
        String usbKeyLineName = getStoredString(profileId, KEY_USB_KEY_LINE, defaults.usbKeyLine().name());
        String serialCatProtocolFamilyName = getStoredString(
                profileId,
                KEY_SERIAL_CAT_PROTOCOL_FAMILY,
                defaults.serialCatProtocolFamily().name()
        );
        String serialCatKeyLineName = getStoredString(
                profileId,
                KEY_SERIAL_CAT_KEY_LINE,
                defaults.serialCatKeyLine().name()
        );
        String networkCatProtocolFamilyName = getStoredString(
                profileId,
                KEY_NETWORK_CAT_PROTOCOL_FAMILY,
                defaults.networkCatProtocolFamily().name()
        );
        String serialCatKeyingPolarityName = getStoredString(
                profileId,
                KEY_SERIAL_CAT_KEYING_POLARITY,
                defaults.serialCatKeyingPolarity().name()
        );
        boolean serialCatAssertRtsDuringKeying = getStoredBoolean(
                profileId,
                KEY_SERIAL_CAT_ASSERT_RTS_DURING_KEYING,
                defaults.serialCatAssertRtsDuringKeying()
        );
        boolean serialCatAssertDtrDuringKeying = getStoredBoolean(
                profileId,
                KEY_SERIAL_CAT_ASSERT_DTR_DURING_KEYING,
                defaults.serialCatAssertDtrDuringKeying()
        );
        SerialKeyerTxOutput.KeyLine usbKeyLine = defaults.usbKeyLine();
        if (usbKeyLineName != null) {
            try {
                usbKeyLine = SerialKeyerTxOutput.KeyLine.valueOf(usbKeyLineName);
            } catch (IllegalArgumentException ignored) {
            }
        }
        CatProtocolFamily serialCatProtocolFamily = defaults.serialCatProtocolFamily();
        if (serialCatProtocolFamilyName != null) {
            try {
                serialCatProtocolFamily = CatProtocolFamily.valueOf(serialCatProtocolFamilyName);
            } catch (IllegalArgumentException ignored) {
            }
        }
        CatProtocolFamily networkCatProtocolFamily = defaults.networkCatProtocolFamily();
        if (networkCatProtocolFamilyName != null) {
            try {
                networkCatProtocolFamily = CatProtocolFamily.valueOf(networkCatProtocolFamilyName);
            } catch (IllegalArgumentException ignored) {
            }
        }
        SerialKeyerTxOutput.KeyLine serialCatKeyLine = defaults.serialCatKeyLine();
        if (serialCatKeyLineName != null) {
            try {
                serialCatKeyLine = SerialKeyerTxOutput.KeyLine.valueOf(serialCatKeyLineName);
            } catch (IllegalArgumentException ignored) {
            }
        }
        KeyingPolarity serialCatKeyingPolarity = defaults.serialCatKeyingPolarity();
        if (serialCatKeyingPolarityName != null) {
            try {
                serialCatKeyingPolarity = KeyingPolarity.valueOf(serialCatKeyingPolarityName);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return new RigProfileSettings(
                getStoredInt(profileId, KEY_DEFAULT_WPM, defaults.defaultWpm()),
                getStoredInt(profileId, KEY_DEFAULT_TONE_FREQUENCY_HZ, defaults.defaultToneFrequencyHz()),
                usbKeyLine,
                getStoredString(profileId, KEY_USB_PREFERRED_DEVICE_NAME, defaults.usbPreferredDeviceName()),
                serialCatProtocolFamily,
                getStoredInt(profileId, KEY_SERIAL_CAT_BAUD_RATE, defaults.serialCatBaudRate()),
                getStoredString(profileId, KEY_SERIAL_CAT_PORT_HINT, defaults.serialCatPortHint()),
                serialCatKeyLine,
                getStoredString(profileId, KEY_SERIAL_CAT_KEYING_PORT_HINT, defaults.serialCatKeyingPortHint()),
                serialCatKeyingPolarity,
                serialCatAssertRtsDuringKeying,
                serialCatAssertDtrDuringKeying,
                getStoredString(profileId, KEY_SERIAL_CAT_CIV_ADDRESS_HEX, defaults.serialCatCivAddressHex()),
                networkCatProtocolFamily,
                getStoredString(profileId, KEY_NETWORK_HOST, defaults.networkHost()),
                getStoredInt(profileId, KEY_NETWORK_PORT, defaults.networkPort()),
                getStoredString(profileId, KEY_BLUETOOTH_DEVICE_HINT, defaults.bluetoothDeviceHint())
        );
    }

    public void saveSettings(RigProfileSettings settings) {
        saveSettings(selectedProfileId(), settings);
    }

    public void saveSettings(RigProfile profile, RigProfileSettings settings) {
        saveSettings(profile == null ? null : profile.id(), settings);
    }

    public void saveSettings(String profileId, RigProfileSettings settings) {
        RigProfileSettings safeSettings = settings == null ? RigProfileSettings.defaults() : settings;
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(scopedKey(profileId, KEY_DEFAULT_WPM), safeSettings.defaultWpm());
        editor.putInt(scopedKey(profileId, KEY_DEFAULT_TONE_FREQUENCY_HZ), safeSettings.defaultToneFrequencyHz());
        editor.putString(scopedKey(profileId, KEY_USB_KEY_LINE), safeSettings.usbKeyLine().name());
        putOptionalString(editor, scopedKey(profileId, KEY_USB_PREFERRED_DEVICE_NAME), safeSettings.usbPreferredDeviceName());
        editor.putString(scopedKey(profileId, KEY_SERIAL_CAT_PROTOCOL_FAMILY), safeSettings.serialCatProtocolFamily().name());
        editor.putInt(scopedKey(profileId, KEY_SERIAL_CAT_BAUD_RATE), safeSettings.serialCatBaudRate());
        putOptionalString(editor, scopedKey(profileId, KEY_SERIAL_CAT_PORT_HINT), safeSettings.serialCatPortHint());
        editor.putString(scopedKey(profileId, KEY_SERIAL_CAT_KEY_LINE), safeSettings.serialCatKeyLine().name());
        putOptionalString(editor, scopedKey(profileId, KEY_SERIAL_CAT_KEYING_PORT_HINT), safeSettings.serialCatKeyingPortHint());
        editor.putString(scopedKey(profileId, KEY_SERIAL_CAT_KEYING_POLARITY), safeSettings.serialCatKeyingPolarity().name());
        editor.putBoolean(scopedKey(profileId, KEY_SERIAL_CAT_ASSERT_RTS_DURING_KEYING), safeSettings.serialCatAssertRtsDuringKeying());
        editor.putBoolean(scopedKey(profileId, KEY_SERIAL_CAT_ASSERT_DTR_DURING_KEYING), safeSettings.serialCatAssertDtrDuringKeying());
        putOptionalString(editor, scopedKey(profileId, KEY_SERIAL_CAT_CIV_ADDRESS_HEX), safeSettings.serialCatCivAddressHex());
        editor.putString(scopedKey(profileId, KEY_NETWORK_CAT_PROTOCOL_FAMILY), safeSettings.networkCatProtocolFamily().name());
        putOptionalString(editor, scopedKey(profileId, KEY_NETWORK_HOST), safeSettings.networkHost());
        editor.putInt(scopedKey(profileId, KEY_NETWORK_PORT), safeSettings.networkPort());
        putOptionalString(editor, scopedKey(profileId, KEY_BLUETOOTH_DEVICE_HINT), safeSettings.bluetoothDeviceHint());
        editor.apply();
    }

    public void saveSelectedProfileId(String profileId) {
        if (profileId == null || profileId.trim().isEmpty()) {
            preferences.edit().remove(KEY_SELECTED_PROFILE_ID).apply();
            return;
        }
        preferences.edit().putString(KEY_SELECTED_PROFILE_ID, profileId).apply();
    }

    public boolean hasSavedSettings(RigProfile profile) {
        return hasSavedSettings(profile == null ? null : profile.id());
    }

    public boolean hasSavedSettings(String profileId) {
        if (profileId == null || profileId.trim().isEmpty()) {
            return false;
        }
        for (String baseKey : CONFIG_KEYS) {
            if (preferences.contains(scopedKey(profileId, baseKey))) {
                return true;
            }
        }
        return false;
    }

    public void clearSettings(RigProfile profile) {
        clearSettings(profile == null ? null : profile.id());
    }

    public void clearSettings(String profileId) {
        if (profileId == null || profileId.trim().isEmpty()) {
            return;
        }
        SharedPreferences.Editor editor = preferences.edit();
        for (String baseKey : CONFIG_KEYS) {
            editor.remove(scopedKey(profileId, baseKey));
        }
        editor.apply();
    }

    private void putOptionalString(SharedPreferences.Editor editor, String key, String value) {
        if (value == null || value.trim().isEmpty()) {
            editor.remove(key);
        } else {
            editor.putString(key, value.trim());
        }
    }

    private int getStoredInt(String profileId, String baseKey, int fallback) {
        String scopedKey = scopedKey(profileId, baseKey);
        if (preferences.contains(scopedKey)) {
            return preferences.getInt(scopedKey, fallback);
        }
        return preferences.getInt(baseKey, fallback);
    }

    private String getStoredString(String profileId, String baseKey, String fallback) {
        String scopedKey = scopedKey(profileId, baseKey);
        if (preferences.contains(scopedKey)) {
            return preferences.getString(scopedKey, fallback);
        }
        return preferences.getString(baseKey, fallback);
    }

    private boolean getStoredBoolean(String profileId, String baseKey, boolean fallback) {
        String scopedKey = scopedKey(profileId, baseKey);
        if (preferences.contains(scopedKey)) {
            return preferences.getBoolean(scopedKey, fallback);
        }
        return preferences.getBoolean(baseKey, fallback);
    }

    private String scopedKey(String profileId, String baseKey) {
        if (profileId == null || profileId.trim().isEmpty()) {
            return baseKey;
        }
        return PROFILE_KEY_PREFIX + profileId.trim() + ":" + baseKey;
    }
}
