package org.bi9clt.cwcn.core.rig;

import android.app.PendingIntent;
import android.content.Context;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class AndroidUsbSerialKeyerPortFactory implements UsbSerialRouteFactory {
    private static final int USB_CLASS_COMM = 2;
    private static final int USB_CDC_SUBCLASS_ACM = 2;

    private final Context appContext;
    private volatile String preferredDeviceName;

    public AndroidUsbSerialKeyerPortFactory(Context context) {
        this.appContext = context == null ? null : context.getApplicationContext();
    }

    @Override
    public String describeAvailability() {
        if (appContext == null) {
            return "USB 适配器注册表创建时缺少 Android Context。";
        }
        UsbManager usbManager = usbManager();
        if (usbManager == null) {
            return "当前设备无法获取 USB 管理器。";
        }
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        if (deviceList.isEmpty()) {
            return "当前没有连接 USB 设备。";
        }
        UsbDevice matchedDevice = matchedDevice(deviceList);
        if (matchedDevice == null) {
            if (preferredDeviceName != null && !preferredDeviceName.isEmpty()) {
                return "已指定的 USB CDC/ACM 设备当前未连接：" + preferredDeviceName;
            }
            return "已检测到 USB 设备，但没有找到可用于 RTS/DTR 键控的 CDC/ACM 串口接口。";
        }
        if (!usbManager.hasPermission(matchedDevice)) {
            return "USB CDC/ACM 设备已找到，但应用尚未获得权限。";
        }
        return "USB CDC/ACM 设备已连接，权限已就绪。";
    }

    @Override
    public boolean canOpenPort() {
        UsbManager usbManager = usbManager();
        if (usbManager == null) {
            return false;
        }
        UsbDevice matchedDevice = matchedDevice(usbManager.getDeviceList());
        return matchedDevice != null && usbManager.hasPermission(matchedDevice);
    }

    @Override
    public SerialKeyerPort openPort() {
        if (appContext == null) {
            return new DisconnectedSerialKeyerPort(
                    "usb-serial-no-context",
                    "USB 串口键控口",
                    "USB 适配器注册表创建时缺少 Android Context。"
            );
        }
        UsbManager usbManager = usbManager();
        if (usbManager == null) {
            return new DisconnectedSerialKeyerPort(
                    "usb-serial-no-manager",
                    "USB 串口键控口",
                    "当前设备无法获取 USB 管理器。"
            );
        }

        UsbDevice device = matchedDevice(usbManager.getDeviceList());
        if (device == null) {
            return new DisconnectedSerialKeyerPort(
                    "usb-serial-no-cdc",
                    "USB 串口键控口",
                    preferredDeviceName != null && !preferredDeviceName.isEmpty()
                            ? "已指定的 USB CDC/ACM 设备当前未连接：" + preferredDeviceName
                            : "当前没有连接 CDC/ACM 串口设备。"
            );
        }
        if (!usbManager.hasPermission(device)) {
            return new DisconnectedSerialKeyerPort(
                    "usb-serial-no-permission",
                    device.getDeviceName(),
                    "USB CDC/ACM 设备已连接，但应用尚未获得权限。"
            );
        }

        UsbInterface controlInterface = findCdcControlInterface(device);
        if (controlInterface == null) {
            return new DisconnectedSerialKeyerPort(
                    "usb-serial-no-control-interface",
                    device.getDeviceName(),
                    "在 USB 设备上没有找到 CDC/ACM 控制接口。"
            );
        }

        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) {
            return new DisconnectedSerialKeyerPort(
                    "usb-serial-open-failed",
                    device.getDeviceName(),
                    "USB 权限已存在，但打开设备失败。"
            );
        }
        if (!connection.claimInterface(controlInterface, true)) {
            connection.close();
            return new DisconnectedSerialKeyerPort(
                    "usb-serial-claim-failed",
                    device.getDeviceName(),
                    "无法声明 USB CDC/ACM 控制接口。"
            );
        }
        return new AndroidUsbCdcAcmSerialKeyerPort(device, controlInterface, connection);
    }

    public String describeMatchedDevice() {
        UsbManager usbManager = usbManager();
        if (usbManager == null) {
            return "没有 USB 管理器";
        }
        UsbDevice matchedDevice = matchedDevice(usbManager.getDeviceList());
        if (matchedDevice == null) {
            if (preferredDeviceName != null && !preferredDeviceName.isEmpty()) {
                return "已指定设备缺失：" + preferredDeviceName;
            }
            return "没有 CDC/ACM 串口设备";
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
        UsbDevice matchedDevice = matchedDevice(usbManager.getDeviceList());
        if (matchedDevice == null) {
            return false;
        }
        if (usbManager.hasPermission(matchedDevice)) {
            return true;
        }
        usbManager.requestPermission(matchedDevice, pendingIntent);
        return true;
    }

    @Override
    public List<UsbSerialDeviceOption> availableDevices() {
        UsbManager usbManager = usbManager();
        List<UsbSerialDeviceOption> options = new ArrayList<>();
        if (usbManager == null) {
            return options;
        }
        for (UsbDevice device : candidateDevices(usbManager.getDeviceList())) {
            options.add(new UsbSerialDeviceOption(
                    device.getDeviceName(),
                    device.getVendorId(),
                    device.getProductId()
            ));
        }
        return options;
    }

    @Override
    public String preferredDeviceName() {
        return preferredDeviceName;
    }

    @Override
    public boolean selectDevice(String deviceName) {
        preferredDeviceName = deviceName == null || deviceName.trim().isEmpty()
                ? null
                : deviceName.trim();
        return true;
    }

    public boolean hasPreferredDeviceSelection() {
        return preferredDeviceName != null && !preferredDeviceName.isEmpty();
    }

    public boolean hasAnyCandidateDevice() {
        UsbManager usbManager = usbManager();
        return usbManager != null && !candidateDevices(usbManager.getDeviceList()).isEmpty();
    }

    public boolean hasTargetDevice() {
        UsbManager usbManager = usbManager();
        return usbManager != null && matchedDevice(usbManager.getDeviceList()) != null;
    }

    public boolean isPreferredDeviceMissing() {
        UsbManager usbManager = usbManager();
        return usbManager != null
                && hasPreferredDeviceSelection()
                && matchedDevice(usbManager.getDeviceList()) == null;
    }

    @Override
    public String diagnosticStageCode() {
        if (appContext == null) {
            return "usb-serial-no-context";
        }
        UsbManager usbManager = usbManager();
        if (usbManager == null) {
            return "usb-serial-no-manager";
        }
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        if (deviceList.isEmpty()) {
            return "usb-serial-no-device";
        }
        UsbDevice matchedDevice = matchedDevice(deviceList);
        if (matchedDevice == null) {
            return hasPreferredDeviceSelection()
                    ? "usb-serial-target-missing"
                    : "usb-serial-no-cdc";
        }
        if (!usbManager.hasPermission(matchedDevice)) {
            return "usb-serial-no-permission";
        }
        return "usb-serial-ready";
    }

    private UsbManager usbManager() {
        return appContext == null
                ? null
                : ContextCompat.getSystemService(appContext, UsbManager.class);
    }

    private UsbDevice matchedDevice(HashMap<String, UsbDevice> deviceList) {
        List<UsbDevice> candidates = candidateDevices(deviceList);
        if (candidates.isEmpty()) {
            return null;
        }
        if (preferredDeviceName != null && !preferredDeviceName.isEmpty()) {
            for (UsbDevice device : candidates) {
                if (preferredDeviceName.equals(device.getDeviceName())) {
                    return device;
                }
            }
            return null;
        }
        return candidates.get(0);
    }

    private List<UsbDevice> candidateDevices(HashMap<String, UsbDevice> deviceList) {
        List<UsbDevice> devices = new ArrayList<>();
        for (UsbDevice device : deviceList.values()) {
            if (findCdcControlInterface(device) != null) {
                devices.add(device);
            }
        }
        return devices;
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
