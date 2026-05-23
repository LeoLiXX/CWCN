package org.bi9clt.cwcn.core.rig;

public enum CatProtocolFamily {
    GENERIC("通用 CAT", "当具体电台协议还不明确时，这是最稳妥的起点。"),
    YAESU_STYLE("Yaesu 风格 CAT", "典型 FT 系列串口 CAT 的帧格式与命令语义。"),
    ICOM_CIV("Icom CI-V", "Icom CI-V 命令族，后续通常还需要补充电台地址。"),
    KENWOOD_STYLE("Kenwood 风格 CAT", "TS 系列风格的 ASCII 命令族，若干兼容机也沿用这一语义。"),
    HAMLIB_RIGCTLD("Hamlib rigctld", "面向通过 rigctld 暴露的电台或桥接设备的网络 CAT 协议族。");

    private final String displayName;
    private final String summary;

    CatProtocolFamily(String displayName, String summary) {
        this.displayName = displayName;
        this.summary = summary;
    }

    public String displayName() {
        return displayName;
    }

    public String summary() {
        return summary;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
