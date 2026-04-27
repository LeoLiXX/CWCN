package org.bi9clt.cwcn.core.rig;

public enum MockUsbSerialBenchScenario {
    NO_DEVICE(
            "No Device Attached",
            "usb-serial-no-device",
            "Simulate the baseline state where no USB device is attached."
    ),
    NO_PERMISSION(
            "Device Attached, No Permission",
            "usb-serial-no-permission",
            "Simulate a visible target device before Android USB permission is granted."
    ),
    READY(
            "Ready",
            "usb-serial-ready",
            "Simulate a healthy attached device with permission and working control-line access."
    ),
    OPEN_FAILED(
            "Open Failed",
            "usb-serial-open-failed",
            "Simulate a target device that is found but fails when Android tries to open it."
    ),
    CLAIM_FAILED(
            "Claim Failed",
            "usb-serial-claim-failed",
            "Simulate a target device whose control interface cannot be claimed."
    ),
    NO_CONTROL_INTERFACE(
            "No Control Interface",
            "usb-serial-no-control-interface",
            "Simulate a device without the expected CDC control interface."
    ),
    TARGET_MISSING(
            "Locked Target Missing",
            "usb-serial-target-missing",
            "Simulate a previously selected target device that is no longer attached."
    ),
    NO_CDC(
            "No CDC Candidate",
            "usb-serial-no-cdc",
            "Simulate a non-CDC USB device that cannot be used for RTS/DTR keying."
    );

    private final String displayName;
    private final String diagnosticCode;
    private final String description;

    MockUsbSerialBenchScenario(String displayName, String diagnosticCode, String description) {
        this.displayName = displayName;
        this.diagnosticCode = diagnosticCode;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String diagnosticCode() {
        return diagnosticCode;
    }

    public String description() {
        return description;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
