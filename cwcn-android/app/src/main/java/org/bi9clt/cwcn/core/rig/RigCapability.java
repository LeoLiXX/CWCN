package org.bi9clt.cwcn.core.rig;

public enum RigCapability {
    TEXT_TO_CW("文本转 CW"),
    PTT_CONTROL("PTT 控制"),
    KEY_LINE_CONTROL("RTS/DTR 键控线"),
    LIVE_PROFILE_UPDATE("实时 WPM / Tone 配置"),
    AUDIO_VOX("音频 VOX"),
    USB_DEVICE_SELECTION("USB 设备选择"),
    SERIAL_CAT("串口 CAT"),
    NETWORK_CAT("网络 CAT"),
    BLUETOOTH_SERIAL("蓝牙串口"),
    FREQUENCY_READ("读取频率"),
    FREQUENCY_SET("设置频率"),
    MODE_READ("读取模式"),
    MODE_SET("设置模式");

    private final String displayName;

    RigCapability(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
