package org.bi9clt.cwcn.core.tx;

public enum CwTxState {
    IDLE("空闲"),
    PLAYING("发射中"),
    PAUSED("已暂停"),
    COMPLETED("已完成"),
    STOPPED("已停止"),
    ERROR("错误");

    private final String displayName;

    CwTxState(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
