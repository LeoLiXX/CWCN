package org.bi9clt.cwcn.core.rig;

import java.util.Locale;

public final class UsbSerialDeviceOption {
    private final boolean auto;
    private final String deviceName;
    private final int vendorId;
    private final int productId;

    public static UsbSerialDeviceOption autoOption() {
        return new UsbSerialDeviceOption(true, null, 0, 0);
    }

    public UsbSerialDeviceOption(String deviceName, int vendorId, int productId) {
        this(false, deviceName, vendorId, productId);
    }

    private UsbSerialDeviceOption(boolean auto, String deviceName, int vendorId, int productId) {
        this.auto = auto;
        this.deviceName = deviceName;
        this.vendorId = vendorId;
        this.productId = productId;
    }

    public boolean isAuto() {
        return auto;
    }

    public String deviceName() {
        return deviceName;
    }

    public int vendorId() {
        return vendorId;
    }

    public int productId() {
        return productId;
    }

    public String displayLabel() {
        if (auto) {
            return "Auto / first available";
        }
        return deviceName
                + " (VID:PID "
                + String.format(Locale.US, "%04X:%04X", vendorId, productId)
                + ")";
    }

    @Override
    public String toString() {
        return displayLabel();
    }
}
