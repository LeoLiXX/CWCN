package org.bi9clt.cwcn.core.rig;

public interface RigControlAdapter {
    String id();

    String displayName();

    String describeCapabilities();

    String describeAvailability();

    boolean supportsTextToCw();

    boolean supportsPttControl();

    boolean isReady();

    boolean keyDown();

    boolean keyUp();

    boolean sendText(String text);

    default boolean supportsConfigurableTextToCwProfile() {
        return false;
    }

    default boolean configureTextToCwProfile(int wpm, int toneFrequencyHz) {
        return false;
    }
}
