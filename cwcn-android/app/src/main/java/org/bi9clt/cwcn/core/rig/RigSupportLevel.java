package org.bi9clt.cwcn.core.rig;

public enum RigSupportLevel {
    DEBUG_ONLY("Debug-only"),
    BENCH_READY("Bench-ready"),
    PLANNED("Planned");

    private final String displayName;

    RigSupportLevel(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
