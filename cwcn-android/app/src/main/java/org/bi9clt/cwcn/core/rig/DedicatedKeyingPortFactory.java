package org.bi9clt.cwcn.core.rig;

interface DedicatedKeyingPortFactory {
    String describeAvailability(String portHint);

    boolean canOpenPort(String portHint);

    SerialKeyerPort openPort(String portHint);
}
