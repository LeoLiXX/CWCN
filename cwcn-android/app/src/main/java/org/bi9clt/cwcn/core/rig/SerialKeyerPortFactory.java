package org.bi9clt.cwcn.core.rig;

public interface SerialKeyerPortFactory {
    String describeAvailability();

    boolean canOpenPort();

    SerialKeyerPort openPort();
}
