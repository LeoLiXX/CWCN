package org.bi9clt.cwcn.ui.debug;

public enum InputSourceOption {
    SYNTHETIC_FIXTURE(
            "基准夹具回放",
            true,
            "用确定性的合成 CW 样本跑完整条接收链路，适合作为算法基线。"
    ),
    PHONE_MICROPHONE(
            "手机麦克风",
            true,
            "通过 AudioRecord 采集手机现场声音，用来排查真机 RX 行为。"
    ),
    LOCAL_FILE_REPLAY(
            "本地音频回放",
            true,
            "回放手机本地 WAV 或兼容的 M4A/AAC 文件，避开现场麦克风噪声做对照实验。"
    ),
    BLUETOOTH_LINK(
            "蓝牙链路（预留）",
            false,
            "预留给后续蓝牙音频或与 CAT 联动的接收链路实验。"
    ),
    USB_EXTERNAL(
            "USB / 外部音频（预留）",
            false,
            "预留给后续 USB 音频或外部接口接收链路实验。"
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
