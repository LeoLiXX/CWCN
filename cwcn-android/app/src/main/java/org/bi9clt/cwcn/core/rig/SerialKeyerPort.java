package org.bi9clt.cwcn.core.rig;

public interface SerialKeyerPort {
    String id();

    String displayName();

    boolean isOpen();

    String describeAvailability();

    boolean setRts(boolean enabled);

    boolean setDtr(boolean enabled);

    void close();
}
