package org.bi9clt.cwcn.core.tx;

public enum CwTxState {
    IDLE("idle"),
    PLAYING("playing"),
    COMPLETED("completed"),
    STOPPED("stopped"),
    ERROR("error");

    private final String displayName;

    CwTxState(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
