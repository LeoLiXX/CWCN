package org.bi9clt.cwcn.core.bootstrap;

public enum ModuleStatus {
    IN_PROGRESS("进行中"),
    NEXT_UP("下一步"),
    PLANNED("已建模");

    private final String displayName;

    ModuleStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
