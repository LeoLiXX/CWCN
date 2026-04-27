package org.bi9clt.cwcn.core.rig;

public enum RigCapability {
    TEXT_TO_CW("Text-to-CW"),
    PTT_CONTROL("PTT control"),
    KEY_LINE_CONTROL("RTS/DTR key line"),
    LIVE_PROFILE_UPDATE("Live WPM/tone profile"),
    AUDIO_VOX("Audio VOX"),
    USB_DEVICE_SELECTION("USB device selection"),
    SERIAL_CAT("Serial CAT"),
    NETWORK_CAT("Network CAT"),
    BLUETOOTH_SERIAL("Bluetooth serial"),
    FREQUENCY_READ("Frequency read"),
    FREQUENCY_SET("Frequency set"),
    MODE_READ("Mode read"),
    MODE_SET("Mode set");

    private final String displayName;

    RigCapability(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
