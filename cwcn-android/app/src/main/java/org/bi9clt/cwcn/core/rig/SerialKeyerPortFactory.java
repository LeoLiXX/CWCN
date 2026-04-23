package org.bi9clt.cwcn.core.rig;

public interface SerialKeyerPortFactory {
    String describeAvailability();

    boolean canOpenPort();

    SerialKeyerPort openPort();

    default String diagnosticStageCode() {
        return canOpenPort() ? "usb-serial-ready" : "usb-serial-unavailable";
    }
}
