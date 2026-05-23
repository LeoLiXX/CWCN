package org.bi9clt.cwcn.core.rig;

interface DedicatedKeyingPortFactory {
    PortAvailability availability(String portHint);

    default String describeAvailability(String portHint) {
        return availability(portHint).message();
    }

    default boolean canOpenPort(String portHint) {
        return availability(portHint).isReady();
    }

    SerialKeyerPort openPort(String portHint);
}
