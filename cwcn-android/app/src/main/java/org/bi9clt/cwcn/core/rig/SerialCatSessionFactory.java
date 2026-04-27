package org.bi9clt.cwcn.core.rig;

import android.app.PendingIntent;

import java.io.IOException;

public interface SerialCatSessionFactory {
    String describeAvailability(String portHint);

    boolean requestPermission(String portHint, PendingIntent pendingIntent);

    SerialCatSession openSession(String portHint, int baudRate) throws IOException;
}
