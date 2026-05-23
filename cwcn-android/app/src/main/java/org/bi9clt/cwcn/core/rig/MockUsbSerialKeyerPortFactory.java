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
                return "模拟模式：当前没有连接 USB 设备。";
            case NO_PERMISSION:
                return "模拟模式：目标设备已连接，但 Android USB 权限尚未授予。";
            case READY:
                return "模拟模式：模拟 USB CDC/ACM 设备已连接，权限已就绪。";
            case OPEN_FAILED:
                return "模拟模式：目标设备存在，但打开设备失败。";
            case CLAIM_FAILED:
                return "模拟模式：目标设备可以打开，但控制接口声明失败。";
            case NO_CONTROL_INTERFACE:
                return "模拟模式：设备存在，但缺少期望的 CDC 控制接口。";
            case TARGET_MISSING:
                return "模拟模式：已指定目标当前不再连接。";
            case NO_CDC:
                return "模拟模式：存在 USB 设备，但没有可用于 RTS/DTR 键控的 CDC/ACM 候选。";
            default:
                return "模拟模式：USB 路由状态当前不可用。";
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
                        "模拟 USB 串口键控口",
                        describeAvailability()
                );
        }
    }

    @Override
    public String describeMatchedDevice() {
        if (selectedScenario == MockUsbSerialBenchScenario.NO_DEVICE) {
            return "模拟路由：没有连接 USB 设备";
        }
        if (selectedScenario == MockUsbSerialBenchScenario.NO_CDC) {
            return "模拟路由：存在 USB 设备，但没有 CDC/ACM 键控候选";
        }
        if (selectedScenario == MockUsbSerialBenchScenario.TARGET_MISSING) {
            return "模拟路由：已指定设备缺失：" + preferredDeviceName();
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
            return "模拟模式：" + scenario.displayName() + "。";
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
