package org.bi9clt.cwcn.core.rig;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;

import java.io.IOException;

final class AndroidUsbProbedSerialKeyerPort implements SerialKeyerPort {
    private final UsbDevice device;
    private final UsbSerialPort port;
    private final UsbDeviceConnection connection;
    private final String driverLabel;

    private boolean closed;

    AndroidUsbProbedSerialKeyerPort(
            UsbDevice device,
            UsbSerialPort port,
            UsbDeviceConnection connection,
            String driverLabel
    ) {
        this.device = device;
        this.port = port;
        this.connection = connection;
        this.driverLabel = driverLabel == null ? "USB serial" : driverLabel;
    }

    @Override
    public String id() {
        return "usb-probed-keyer:" + device.getDeviceName() + "#" + port.getPortNumber();
    }

    @Override
    public String displayName() {
        return device.getDeviceName() + "#" + port.getPortNumber();
    }

    @Override
    public boolean isOpen() {
        return !closed && port.isOpen();
    }

    @Override
    public String describeAvailability() {
        return driverLabel + " keying port is open on " + displayName() + ".";
    }

    @Override
    public boolean setRts(boolean enabled) {
        if (!isOpen()) {
            return false;
        }
        try {
            port.setRTS(enabled);
            return true;
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    @Override
    public boolean setDtr(boolean enabled) {
        if (!isOpen()) {
            return false;
        }
        try {
            port.setDTR(enabled);
            return true;
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            port.close();
        } catch (IOException ignored) {
        }
        try {
            connection.close();
        } catch (RuntimeException ignored) {
        }
    }
}
