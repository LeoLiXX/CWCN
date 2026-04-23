package org.bi9clt.cwcn.core.rig;

import android.app.PendingIntent;
import android.content.Context;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import androidx.core.content.ContextCompat;

import java.util.HashMap;

public final class AndroidUsbSerialKeyerPortFactory implements SerialKeyerPortFactory {
    private static final int USB_CLASS_COMM = 2;
    private static final int USB_CDC_SUBCLASS_ACM = 2;

    private final Context appContext;

    public AndroidUsbSerialKeyerPortFactory(Context context) {
        this.appContext = context == null ? null : context.getApplicationContext();
    }

    @Override
    public String describeAvailability() {
        if (appContext == null) {
            return "USB adapter registry was created without an Android context.";
        }
        UsbManager usbManager = usbManager();
        if (usbManager == null) {
            return "USB manager is unavailable on this device.";
        }
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        if (deviceList.isEmpty()) {
            return "No USB device is attached.";
        }
        UsbDevice matchedDevice = firstCdcAcmDevice(deviceList);
        if (matchedDevice == null) {
            return "USB device detected, but no CDC/ACM serial interface was found for RTS/DTR keying.";
        }
        if (!usbManager.hasPermission(matchedDevice)) {
            return "USB CDC/ACM device found, but app permission has not been granted yet.";
        }
        return "USB CDC/ACM device is attached and permission is available.";
    }

    @Override
    public boolean canOpenPort() {
        UsbManager usbManager = usbManager();
        if (usbManager == null) {
            return false;
        }
        UsbDevice matchedDevice = firstCdcAcmDevice(usbManager.getDeviceList());
        return matchedDevice != null && usbManager.hasPermission(matchedDevice);
    }

    @Override
    public SerialKeyerPort openPort() {
        if (appContext == null) {
            return new DisconnectedSerialKeyerPort(
                    "usb-serial-no-context",
                    "USB Serial Keyer Port",
                    "USB adapter registry was created without an Android context."
            );
        }
        UsbManager usbManager = usbManager();
        if (usbManager == null) {
            return new DisconnectedSerialKeyerPort(
                    "usb-serial-no-manager",
                    "USB Serial Keyer Port",
                    "USB manager is unavailable on this device."
            );
        }

        UsbDevice device = firstCdcAcmDevice(usbManager.getDeviceList());
        if (device == null) {
            return new DisconnectedSerialKeyerPort(
                    "usb-serial-no-cdc",
                    "USB Serial Keyer Port",
                    "No CDC/ACM serial device is attached."
            );
        }
        if (!usbManager.hasPermission(device)) {
            return new DisconnectedSerialKeyerPort(
                    "usb-serial-no-permission",
                    device.getDeviceName(),
                    "USB CDC/ACM device is attached, but app permission has not been granted yet."
            );
        }

        UsbInterface controlInterface = findCdcControlInterface(device);
        if (controlInterface == null) {
            return new DisconnectedSerialKeyerPort(
                    "usb-serial-no-control-interface",
                    device.getDeviceName(),
                    "CDC/ACM control interface was not found on the USB device."
            );
        }

        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) {
            return new DisconnectedSerialKeyerPort(
                    "usb-serial-open-failed",
                    device.getDeviceName(),
                    "USB device permission exists, but opening the device failed."
            );
        }
        if (!connection.claimInterface(controlInterface, true)) {
            connection.close();
            return new DisconnectedSerialKeyerPort(
                    "usb-serial-claim-failed",
                    device.getDeviceName(),
                    "USB CDC/ACM control interface could not be claimed."
            );
        }
        return new AndroidUsbCdcAcmSerialKeyerPort(device, controlInterface, connection);
    }

    public String describeMatchedDevice() {
        UsbManager usbManager = usbManager();
        if (usbManager == null) {
            return "No USB manager";
        }
        UsbDevice matchedDevice = firstCdcAcmDevice(usbManager.getDeviceList());
        if (matchedDevice == null) {
            return "No CDC/ACM serial device";
        }
        return matchedDevice.getDeviceName()
                + " (VID:PID "
                + String.format("%04X:%04X", matchedDevice.getVendorId(), matchedDevice.getProductId())
                + ")";
    }

    public boolean requestPermission(PendingIntent pendingIntent) {
        UsbManager usbManager = usbManager();
        if (usbManager == null || pendingIntent == null) {
            return false;
        }
        UsbDevice matchedDevice = firstCdcAcmDevice(usbManager.getDeviceList());
        if (matchedDevice == null) {
            return false;
        }
        if (usbManager.hasPermission(matchedDevice)) {
            return true;
        }
        usbManager.requestPermission(matchedDevice, pendingIntent);
        return true;
    }

    private UsbManager usbManager() {
        return appContext == null
                ? null
                : ContextCompat.getSystemService(appContext, UsbManager.class);
    }

    private UsbDevice firstCdcAcmDevice(HashMap<String, UsbDevice> deviceList) {
        for (UsbDevice device : deviceList.values()) {
            if (findCdcControlInterface(device) != null) {
                return device;
            }
        }
        return null;
    }

    private UsbInterface findCdcControlInterface(UsbDevice device) {
        for (int interfaceIndex = 0; interfaceIndex < device.getInterfaceCount(); interfaceIndex++) {
            UsbInterface usbInterface = device.getInterface(interfaceIndex);
            if (usbInterface.getInterfaceClass() == USB_CLASS_COMM
                    && usbInterface.getInterfaceSubclass() == USB_CDC_SUBCLASS_ACM) {
                return usbInterface;
            }
            if (usbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_COMM) {
                return usbInterface;
            }
        }
        return null;
    }
}
