package org.bi9clt.cwcn.core.rig;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Pair;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ProbeTable {
    private final Map<Pair<Integer, Integer>, Class<? extends UsbSerialDriver>> probeTable =
            new LinkedHashMap<>();

    ProbeTable addProduct(
            int vendorId,
            int productId,
            Class<? extends UsbSerialDriver> driverClass
    ) {
        probeTable.put(Pair.create(vendorId, productId), driverClass);
        return this;
    }

    @SuppressWarnings("unchecked")
    ProbeTable addDriver(Class<? extends UsbSerialDriver> driverClass) {
        final Method method;
        try {
            method = driverClass.getMethod("getSupportedDevices");
        } catch (SecurityException | NoSuchMethodException exception) {
            throw new RuntimeException(exception);
        }

        final Map<Integer, int[]> devices;
        try {
            devices = (Map<Integer, int[]>) method.invoke(null);
        } catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException exception) {
            throw new RuntimeException(exception);
        }

        for (Map.Entry<Integer, int[]> entry : devices.entrySet()) {
            for (int productId : entry.getValue()) {
                addProduct(entry.getKey(), productId, driverClass);
            }
        }
        return this;
    }

    Class<? extends UsbSerialDriver> findDriver(int vendorId, int productId) {
        return probeTable.get(Pair.create(vendorId, productId));
    }
}

final class UsbSerialProber {
    private final ProbeTable probeTable;

    UsbSerialProber(ProbeTable probeTable) {
        this.probeTable = probeTable;
    }

    static UsbSerialProber defaultProber() {
        ProbeTable table = new ProbeTable();
        table.addDriver(CdcAcmSerialDriver.class);
        table.addDriver(Cp21xxSerialDriver.class);
        table.addDriver(FtdiSerialDriver.class);
        table.addDriver(ProlificSerialDriver.class);
        table.addDriver(Ch34xSerialDriver.class);
        return new UsbSerialProber(table);
    }

    List<UsbSerialDriver> findAllDrivers(UsbManager usbManager) {
        List<UsbSerialDriver> result = new ArrayList<>();
        if (usbManager == null) {
            return result;
        }
        for (UsbDevice usbDevice : usbManager.getDeviceList().values()) {
            UsbSerialDriver driver = probeDevice(usbDevice);
            if (driver != null) {
                result.add(driver);
            }
        }
        return result;
    }

    UsbSerialDriver probeDevice(UsbDevice usbDevice) {
        if (usbDevice == null) {
            return null;
        }
        Class<? extends UsbSerialDriver> driverClass =
                probeTable.findDriver(usbDevice.getVendorId(), usbDevice.getProductId());
        if (driverClass == null) {
            return null;
        }
        if (driverClass == CdcAcmSerialDriver.class) {
            return new CdcAcmSerialDriver(usbDevice);
        }
        if (driverClass == Cp21xxSerialDriver.class) {
            return new Cp21xxSerialDriver(usbDevice);
        }
        if (driverClass == FtdiSerialDriver.class) {
            return new FtdiSerialDriver(usbDevice);
        }
        if (driverClass == ProlificSerialDriver.class) {
            return new ProlificSerialDriver(usbDevice);
        }
        if (driverClass == Ch34xSerialDriver.class) {
            return new Ch34xSerialDriver(usbDevice);
        }
        return null;
    }
}
