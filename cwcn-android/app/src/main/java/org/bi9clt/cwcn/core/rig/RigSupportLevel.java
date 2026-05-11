package org.bi9clt.cwcn.core.rig;

public enum RigSupportLevel {
    DEBUG_ONLY("开发调试"),
    BENCH_READY("可验证"),
    PLANNED("规划中");

    private final String displayName;

    RigSupportLevel(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
