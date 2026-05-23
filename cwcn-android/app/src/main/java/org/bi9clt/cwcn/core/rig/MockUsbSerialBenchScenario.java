package org.bi9clt.cwcn.core.rig;

public enum MockUsbSerialBenchScenario {
    NO_DEVICE(
            "未连接设备",
            "usb-serial-no-device",
            "模拟基线状态：当前没有连接任何 USB 设备。"
    ),
    NO_PERMISSION(
            "设备已连但无权限",
            "usb-serial-no-permission",
            "模拟目标设备可见，但 Android USB 权限尚未授予。"
    ),
    READY(
            "已就绪",
            "usb-serial-ready",
            "模拟设备连接正常、权限已授予且控制线可正常访问。"
    ),
    OPEN_FAILED(
            "打开失败",
            "usb-serial-open-failed",
            "模拟目标设备能被发现，但 Android 打开设备时失败。"
    ),
    CLAIM_FAILED(
            "声明失败",
            "usb-serial-claim-failed",
            "模拟目标设备的控制接口无法声明。"
    ),
    NO_CONTROL_INTERFACE(
            "缺少控制接口",
            "usb-serial-no-control-interface",
            "模拟设备存在，但缺少预期的 CDC 控制接口。"
    ),
    TARGET_MISSING(
            "锁定目标缺失",
            "usb-serial-target-missing",
            "模拟之前选定的目标设备当前已经断开。"
    ),
    NO_CDC(
            "没有 CDC 候选",
            "usb-serial-no-cdc",
            "模拟存在非 CDC USB 设备，但不能用于 RTS/DTR 键控。"
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
