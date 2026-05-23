package org.bi9clt.cwcn.core.rig;

public enum KeyingPolarity {
    ACTIVE_HIGH("正常极性（高电平拉起）"),
    ACTIVE_LOW("反相极性（低电平拉起）");

    private final String displayName;

    KeyingPolarity(String displayName) {
        this.displayName = displayName;
    }

    public boolean assertedLevel() {
        return this == ACTIVE_HIGH;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
