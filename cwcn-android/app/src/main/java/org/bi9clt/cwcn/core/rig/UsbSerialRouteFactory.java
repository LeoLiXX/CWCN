package org.bi9clt.cwcn.core.rig;

import android.app.PendingIntent;

public interface UsbSerialRouteFactory extends SelectableSerialKeyerPortFactory {
    String describeMatchedDevice();

    boolean requestPermission(PendingIntent pendingIntent);

    boolean hasPreferredDeviceSelection();

    boolean hasAnyCandidateDevice();

    boolean hasTargetDevice();

    boolean isPreferredDeviceMissing();
}
