package org.bi9clt.cwcn.core.rig;

import android.app.PendingIntent;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class MockUsbSerialKeyerPortFactory implements MockUsbSerialBenchFactory {
    private static final String ATTACHED_DEVICE_NAME = "mock-usb-keyer-1";
    private static final String ALT_DEVICE_NAME = "mock-usb-keyer-2";
    private static final String MISSING_DEVICE_NAME = "mock-usb-keyer-missing";

    private volatile MockUsbSerialBenchScenario selectedScenario = MockUsbSerialBenchScenario.NO_DEVICE;
    private volatile String preferredDeviceName;

    @Override
    public String describeAvailability() {
        switch (selectedScenario) {
            case NO_DEVICE:
                return "Mock mode: no USB device is attached.";
            case NO_PERMISSION:
                return "Mock mode: target device is attached, but Android USB permission has not been granted yet.";
            case READY:
                return "Mock mode: mock USB CDC/ACM device is attached and permission is available.";
            case OPEN_FAILED:
                return "Mock mode: target device is present, but opening the device fails.";
            case CLAIM_FAILED:
                return "Mock mode: target device opens, but control interface claim fails.";
            case NO_CONTROL_INTERFACE:
                return "Mock mode: device exists, but the expected CDC control interface is missing.";
            case TARGET_MISSING:
                return "Mock mode: the preferred target is no longer attached.";
            case NO_CDC:
                return "Mock mode: a USB device exists, but no CDC/ACM candidate is usable for RTS/DTR keying.";
            default:
                return "Mock mode: USB route state is unavailable.";
        }
    }

    @Override
    public boolean canOpenPort() {
        return selectedScenario == MockUsbSerialBenchScenario.READY;
    }

    @Override
    public SerialKeyerPort openPort() {
        switch (selectedScenario) {
            case READY:
                return new MockOpenSerialKeyerPort(activeDeviceName(), selectedScenario);
            case OPEN_FAILED:
            case CLAIM_FAILED:
            case NO_CONTROL_INTERFACE:
                return new DisconnectedSerialKeyerPort(
                        selectedScenario.diagnosticCode(),
                        activeDeviceName(),
                        describeAvailability()
                );
            case NO_PERMISSION:
                return new DisconnectedSerialKeyerPort(
                        "usb-serial-no-permission",
                        activeDeviceName(),
                        describeAvailability()
                );
            case TARGET_MISSING:
                return new DisconnectedSerialKeyerPort(
                        "usb-serial-target-missing",
                        MISSING_DEVICE_NAME,
                        describeAvailability()
                );
            case NO_CDC:
                return new DisconnectedSerialKeyerPort(
                        "usb-serial-no-cdc",
                        "mock-non-cdc-device",
                        describeAvailability()
                );
            case NO_DEVICE:
            default:
                return new DisconnectedSerialKeyerPort(
                        "usb-serial-no-device",
                        "Mock USB Serial Keyer Port",
                        describeAvailability()
                );
        }
    }

    @Override
    public String describeMatchedDevice() {
        if (selectedScenario == MockUsbSerialBenchScenario.NO_DEVICE) {
            return "Mock route: no USB device attached";
        }
        if (selectedScenario == MockUsbSerialBenchScenario.NO_CDC) {
            return "Mock route: USB device exists, but no CDC/ACM keyer candidate";
        }
        if (selectedScenario == MockUsbSerialBenchScenario.TARGET_MISSING) {
            return "Mock route: preferred device missing: " + preferredDeviceName();
        }
        return activeDeviceName() + " (VID:PID 1D50:60C7)";
    }

    @Override
    public boolean requestPermission(PendingIntent pendingIntent) {
        if (!hasTargetDevice()) {
            return false;
        }
        if (selectedScenario == MockUsbSerialBenchScenario.NO_PERMISSION) {
            selectedScenario = MockUsbSerialBenchScenario.READY;
        }
        return true;
    }

    @Override
    public boolean hasPreferredDeviceSelection() {
        return preferredDeviceName != null && !preferredDeviceName.trim().isEmpty();
    }

    @Override
    public boolean hasAnyCandidateDevice() {
        return selectedScenario != MockUsbSerialBenchScenario.NO_DEVICE;
    }

    @Override
    public boolean hasTargetDevice() {
        return selectedScenario != MockUsbSerialBenchScenario.NO_DEVICE
                && selectedScenario != MockUsbSerialBenchScenario.NO_CDC
                && selectedScenario != MockUsbSerialBenchScenario.TARGET_MISSING;
    }

    @Override
    public boolean isPreferredDeviceMissing() {
        return selectedScenario == MockUsbSerialBenchScenario.TARGET_MISSING && hasPreferredDeviceSelection();
    }

    @Override
    public List<UsbSerialDeviceOption> availableDevices() {
        if (selectedScenario == MockUsbSerialBenchScenario.NO_DEVICE
                || selectedScenario == MockUsbSerialBenchScenario.NO_CDC) {
            return Collections.emptyList();
        }
        return Arrays.asList(
                new UsbSerialDeviceOption(ATTACHED_DEVICE_NAME, 0x1D50, 0x60C7),
                new UsbSerialDeviceOption(ALT_DEVICE_NAME, 0x1D50, 0x60C8)
        );
    }

    @Override
    public String preferredDeviceName() {
        if (selectedScenario == MockUsbSerialBenchScenario.TARGET_MISSING) {
            return hasPreferredDeviceSelection() ? preferredDeviceName : MISSING_DEVICE_NAME;
        }
        return preferredDeviceName;
    }

    @Override
    public boolean selectDevice(String deviceName) {
        preferredDeviceName = deviceName == null || deviceName.trim().isEmpty()
                ? null
                : deviceName.trim();
        return true;
    }

    @Override
    public String diagnosticStageCode() {
        return selectedScenario.diagnosticCode();
    }

    @Override
    public List<MockUsbSerialBenchScenario> availableBenchScenarios() {
        return Arrays.asList(MockUsbSerialBenchScenario.values());
    }

    @Override
    public MockUsbSerialBenchScenario selectedBenchScenario() {
        return selectedScenario;
    }

    @Override
    public boolean selectBenchScenario(MockUsbSerialBenchScenario scenario) {
        if (scenario == null) {
            return false;
        }
        selectedScenario = scenario;
        if (scenario == MockUsbSerialBenchScenario.TARGET_MISSING && !hasPreferredDeviceSelection()) {
            preferredDeviceName = MISSING_DEVICE_NAME;
        }
        return true;
    }

    private String activeDeviceName() {
        if (preferredDeviceName != null && !preferredDeviceName.trim().isEmpty()) {
            return preferredDeviceName;
        }
        return ATTACHED_DEVICE_NAME;
    }

    private static final class MockOpenSerialKeyerPort implements SerialKeyerPort {
        private final String deviceName;
        private final MockUsbSerialBenchScenario scenario;
        private boolean closed;

        private MockOpenSerialKeyerPort(String deviceName, MockUsbSerialBenchScenario scenario) {
            this.deviceName = deviceName;
            this.scenario = scenario;
        }

        @Override
        public String id() {
            return "mock-open-serial-port";
        }

        @Override
        public String displayName() {
            return deviceName;
        }

        @Override
        public boolean isOpen() {
            return !closed;
        }

        @Override
        public String describeAvailability() {
            return "Mock mode: " + scenario.displayName() + ".";
        }

        @Override
        public boolean setRts(boolean enabled) {
            return !closed;
        }

        @Override
        public boolean setDtr(boolean enabled) {
            return !closed;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
