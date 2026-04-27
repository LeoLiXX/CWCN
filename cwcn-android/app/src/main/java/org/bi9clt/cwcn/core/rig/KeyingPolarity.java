package org.bi9clt.cwcn.core.rig;

public enum KeyingPolarity {
    ACTIVE_HIGH("Normal (assert on high)"),
    ACTIVE_LOW("Inverted (assert on low)");

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
