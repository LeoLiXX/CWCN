package org.bi9clt.cwcn.core.rig;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;

final class AndroidUsbCdcAcmSerialKeyerPort implements SerialKeyerPort {
    private static final int USB_TYPE_CLASS = 0x20;
    private static final int USB_RECIP_INTERFACE = 0x01;
    private static final int USB_DIR_OUT = 0x00;
    private static final int CDC_SET_CONTROL_LINE_STATE = 0x22;
    private static final int CONTROL_LINE_DTR = 0x1;
    private static final int CONTROL_LINE_RTS = 0x2;

    private final UsbDevice device;
    private final UsbInterface controlInterface;
    private final UsbDeviceConnection connection;

    private boolean dtrEnabled;
    private boolean rtsEnabled;
    private boolean closed;

    AndroidUsbCdcAcmSerialKeyerPort(
            UsbDevice device,
            UsbInterface controlInterface,
            UsbDeviceConnection connection
    ) {
        this.device = device;
        this.controlInterface = controlInterface;
        this.connection = connection;
    }

    @Override
    public String id() {
        return "usb-cdc:" + device.getVendorId() + ":" + device.getProductId();
    }

    @Override
    public String displayName() {
        return device.getDeviceName();
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    @Override
    public String describeAvailability() {
        return "USB CDC/ACM keyer port is open on " + device.getDeviceName() + ".";
    }

    @Override
    public boolean setRts(boolean enabled) {
        rtsEnabled = enabled;
        return applyControlLineState();
    }

    @Override
    public boolean setDtr(boolean enabled) {
        dtrEnabled = enabled;
        return applyControlLineState();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            connection.releaseInterface(controlInterface);
        } catch (Throwable ignored) {
        }
        connection.close();
    }

    private boolean applyControlLineState() {
        if (closed) {
            return false;
        }
        int value = 0;
        if (dtrEnabled) {
            value |= CONTROL_LINE_DTR;
        }
        if (rtsEnabled) {
            value |= CONTROL_LINE_RTS;
        }
        int requestType = USB_DIR_OUT | USB_TYPE_CLASS | USB_RECIP_INTERFACE;
        int result = connection.controlTransfer(
                requestType,
                CDC_SET_CONTROL_LINE_STATE,
                value,
                controlInterface.getId(),
                null,
                0,
                1000
        );
        return result >= 0;
    }
}
