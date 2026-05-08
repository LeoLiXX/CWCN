package org.bi9clt.cwcn.core.rig;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.tx.CwTxPlaybackSnapshot;

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

    default boolean stopTextTransmission() {
        return keyUp();
    }

    default boolean supportsConfigurableTextToCwProfile() {
        return false;
    }

    default boolean configureTextToCwProfile(int wpm, int toneFrequencyHz) {
        return false;
    }

    default boolean usesWpmForTextToCwProfile() {
        return supportsConfigurableTextToCwProfile();
    }

    default boolean usesToneFrequencyForTextToCwProfile() {
        return supportsConfigurableTextToCwProfile();
    }

    @Nullable
    default CwTxPlaybackSnapshot currentTxPlaybackSnapshot() {
        return null;
    }
}
