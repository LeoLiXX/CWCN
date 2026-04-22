package org.bi9clt.cwcn.core.rig;

import android.content.Context;

public interface RigTransport {
    enum TransportKind {
        USB_SERIAL,
        BLUETOOTH_SERIAL,
        NETWORK_CAT,
        AUDIO_VOX
    }

    String id();

    String displayName();

    TransportKind kind();

    boolean isReady(Context context);

    String describeAvailability(Context context);
}
