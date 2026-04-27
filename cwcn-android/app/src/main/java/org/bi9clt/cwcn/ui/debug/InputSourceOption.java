package org.bi9clt.cwcn.ui.debug;

public enum InputSourceOption {
    SYNTHETIC_FIXTURE(
            "Synthetic Fixture",
            true,
            "Replay a deterministic generated CW sample through the full decode chain."
    ),
    PHONE_MICROPHONE(
            "Phone Microphone",
            true,
            "Capture live microphone audio through AudioRecord for on-device decoding."
    ),
    LOCAL_FILE_REPLAY(
            "Local File Replay",
            true,
            "Replay a phone-local WAV or compatible M4A/AAC file through the same decode chain without live microphone noise."
    ),
    BLUETOOTH_LINK(
            "Bluetooth Link",
            false,
            "Placeholder for future Bluetooth audio or CAT-integrated receive paths."
    ),
    USB_EXTERNAL(
            "USB / External Audio",
            false,
            "Placeholder for future USB audio or external interface receive paths."
    );

    private final String displayName;
    private final boolean implemented;
    private final String description;

    InputSourceOption(String displayName, boolean implemented, String description) {
        this.displayName = displayName;
        this.implemented = implemented;
        this.description = description;
    }

    public boolean implemented() {
        return implemented;
    }

    public String description() {
        return description;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
