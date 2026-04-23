package org.bi9clt.cwcn.core.rig;

import java.util.List;

public interface SelectableSerialKeyerPortFactory extends SerialKeyerPortFactory {
    List<UsbSerialDeviceOption> availableDevices();

    String preferredDeviceName();

    boolean selectDevice(String deviceName);
}
