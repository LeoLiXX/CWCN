package org.bi9clt.cwcn.core.rig;

import android.app.PendingIntent;

import java.io.IOException;

public interface SerialCatSessionFactory {
    PortAvailability availability(String portHint);

    default String describeAvailability(String portHint) {
        return availability(portHint).message();
    }

    default boolean isReady(String portHint) {
        return availability(portHint).isReady();
    }

    boolean requestPermission(String portHint, PendingIntent pendingIntent);

    SerialCatSession openSession(String portHint, int baudRate) throws IOException;
}
