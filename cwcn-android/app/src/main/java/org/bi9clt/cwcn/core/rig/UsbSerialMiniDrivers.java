package org.bi9clt.cwcn.core.rig;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CdcAcmSerialDriver implements UsbSerialDriver {
    private static final String TAG = "CdcAcmSerialDriver";

    private final UsbDevice device;
    private final List<UsbSerialPort> ports;

    CdcAcmSerialDriver(UsbDevice device) {
        this.device = device;
        this.ports = new ArrayList<>();

        int controlInterfaceCount = 0;
        int dataInterfaceCount = 0;
        for (int index = 0; index < device.getInterfaceCount(); index++) {
            if (device.getInterface(index).getInterfaceClass() == UsbConstants.USB_CLASS_COMM) {
                controlInterfaceCount++;
            }
            if (device.getInterface(index).getInterfaceClass() == UsbConstants.USB_CLASS_CDC_DATA) {
                dataInterfaceCount++;
            }
        }
        for (int port = 0; port < Math.min(controlInterfaceCount, dataInterfaceCount); port++) {
            ports.add(new Port(device, port));
        }
        if (ports.isEmpty()) {
            ports.add(new Port(device, -1));
        }
    }

    @Override
    public UsbDevice getDevice() {
        return device;
    }

    @Override
    public List<UsbSerialPort> getPorts() {
        return ports;
    }

    private final class Port extends CommonUsbSerialPort {
        private static final int USB_RECIP_INTERFACE = 0x01;
        private static final int USB_RT_ACM = UsbConstants.USB_TYPE_CLASS | USB_RECIP_INTERFACE;
        private static final int SET_LINE_CODING = 0x20;
        private static final int SET_CONTROL_LINE_STATE = 0x22;
        private static final int SEND_BREAK = 0x23;

        private UsbInterface controlInterface;
        private UsbInterface dataInterface;
        private UsbEndpoint controlEndpoint;
        private int controlIndex;
        private boolean rts;
        private boolean dtr;

        private Port(UsbDevice device, int portNumber) {
            super(device, portNumber);
        }

        @Override
        public UsbSerialDriver getDriver() {
            return CdcAcmSerialDriver.this;
        }

        @Override
        protected void openInt(UsbDeviceConnection connection) throws IOException {
            if (portNumber == -1) {
                Log.d(TAG, "device might be castrated ACM device, trying single interface logic");
                openSingleInterface();
            } else {
                Log.d(TAG, "trying default interface logic");
                openInterface();
            }
        }

        private void claimInterfaceSafely(UsbInterface usbInterface, String name) throws IOException {
            if (usbInterface == null) {
                throw new IOException("Interface is null: " + name);
            }
            if (!this.connection.claimInterface(usbInterface, true)) {
                throw new IOException("Could not claim " + name);
            }
            Log.d(TAG, "claimInterface(" + name + ") succeeded with force");
        }

        private void openSingleInterface() throws IOException {
            controlIndex = 0;
            controlInterface = device.getInterface(0);
            dataInterface = device.getInterface(0);
            claimInterfaceSafely(controlInterface, "shared control/data interface");

            for (int index = 0; index < controlInterface.getEndpointCount(); index++) {
                UsbEndpoint endpoint = controlInterface.getEndpoint(index);
                if (endpoint.getDirection() == UsbConstants.USB_DIR_IN
                        && endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_INT) {
                    controlEndpoint = endpoint;
                } else if (endpoint.getDirection() == UsbConstants.USB_DIR_IN
                        && endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    readEndpoint = endpoint;
                } else if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT
                        && endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    writeEndpoint = endpoint;
                }
            }
            if (controlEndpoint == null) {
                throw new IOException("No control endpoint");
            }
        }

        private void openInterface() throws IOException {
            Log.d(TAG, "claiming interfaces, count=" + device.getInterfaceCount());

            int controlInterfaceCount = 0;
            int dataInterfaceCount = 0;
            controlInterface = null;
            dataInterface = null;
            for (int index = 0; index < device.getInterfaceCount(); index++) {
                UsbInterface usbInterface = device.getInterface(index);
                if (usbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_COMM) {
                    if (controlInterfaceCount == portNumber) {
                        controlIndex = index;
                        controlInterface = usbInterface;
                    }
                    controlInterfaceCount++;
                }
                if (usbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_CDC_DATA) {
                    if (dataInterfaceCount == portNumber) {
                        dataInterface = usbInterface;
                    }
                    dataInterfaceCount++;
                }
            }

            if (controlInterface == null) {
                throw new IOException("No control interface");
            }
            Log.d(TAG, "Control iface=" + controlInterface);
            claimInterfaceSafely(controlInterface, "control interface");

            controlEndpoint = controlInterface.getEndpoint(0);
            if (controlEndpoint.getDirection() != UsbConstants.USB_DIR_IN
                    || controlEndpoint.getType() != UsbConstants.USB_ENDPOINT_XFER_INT) {
                throw new IOException("Invalid control endpoint");
            }

            if (dataInterface == null) {
                throw new IOException("No data interface");
            }
            Log.d(TAG, "data iface=" + dataInterface);
            claimInterfaceSafely(dataInterface, "data interface");

            for (int index = 0; index < dataInterface.getEndpointCount(); index++) {
                UsbEndpoint endpoint = dataInterface.getEndpoint(index);
                if (endpoint.getDirection() == UsbConstants.USB_DIR_IN
                        && endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    readEndpoint = endpoint;
                }
                if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT
                        && endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    writeEndpoint = endpoint;
                }
            }
        }

        private int sendAcmControlMessage(int request, int value, byte[] buffer) throws IOException {
            int length = connection.controlTransfer(
                    USB_RT_ACM,
                    request,
                    value,
                    controlIndex,
                    buffer,
                    buffer != null ? buffer.length : 0,
                    5000
            );
            if (length < 0) {
                throw new IOException("controlTransfer failed");
            }
            return length;
        }

        @Override
        protected void closeInt() {
            try {
                connection.releaseInterface(controlInterface);
                connection.releaseInterface(dataInterface);
            } catch (Exception ignored) {
            }
        }

        @Override
        public void setParameters(int baudRate, int dataBits, int stopBits, @Parity int parity)
                throws IOException {
            if (baudRate <= 0) {
                throw new IllegalArgumentException("Invalid baud rate: " + baudRate);
            }
            if (dataBits < DATABITS_5 || dataBits > DATABITS_8) {
                throw new IllegalArgumentException("Invalid data bits: " + dataBits);
            }

            byte stopBitsByte;
            switch (stopBits) {
                case STOPBITS_1:
                    stopBitsByte = 0;
                    break;
                case STOPBITS_1_5:
                    stopBitsByte = 1;
                    break;
                case STOPBITS_2:
                    stopBitsByte = 2;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid stop bits: " + stopBits);
            }

            byte parityBitsByte;
            switch (parity) {
                case PARITY_NONE:
                    parityBitsByte = 0;
                    break;
                case PARITY_ODD:
                    parityBitsByte = 1;
                    break;
                case PARITY_EVEN:
                    parityBitsByte = 2;
                    break;
                case PARITY_MARK:
                    parityBitsByte = 3;
                    break;
                case PARITY_SPACE:
                    parityBitsByte = 4;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid parity: " + parity);
            }

            byte[] message = new byte[] {
                    (byte) (baudRate & 0xff),
                    (byte) ((baudRate >> 8) & 0xff),
                    (byte) ((baudRate >> 16) & 0xff),
                    (byte) ((baudRate >> 24) & 0xff),
                    stopBitsByte,
                    parityBitsByte,
                    (byte) dataBits
            };
            sendAcmControlMessage(SET_LINE_CODING, 0, message);
        }

        @Override
        public boolean getDTR() {
            return dtr;
        }

        @Override
        public void setDTR(boolean value) throws IOException {
            dtr = value;
            setDtrRts();
        }

        @Override
        public boolean getRTS() {
            return rts;
        }

        @Override
        public void setRTS(boolean value) throws IOException {
            rts = value;
            setDtrRts();
        }

        private void setDtrRts() throws IOException {
            int value = (rts ? 0x2 : 0) | (dtr ? 0x1 : 0);
            sendAcmControlMessage(SET_CONTROL_LINE_STATE, value, null);
        }

        @Override
        public EnumSet<ControlLine> getControlLines() {
            EnumSet<ControlLine> set = EnumSet.noneOf(ControlLine.class);
            if (rts) {
                set.add(ControlLine.RTS);
            }
            if (dtr) {
                set.add(ControlLine.DTR);
            }
            return set;
        }

        @Override
        public EnumSet<ControlLine> getSupportedControlLines() {
            return EnumSet.of(ControlLine.RTS, ControlLine.DTR);
        }

        @Override
        public void setBreak(boolean value) throws IOException {
            sendAcmControlMessage(SEND_BREAK, value ? 0xffff : 0, null);
        }
    }

    public static Map<Integer, int[]> getSupportedDevices() {
        Map<Integer, int[]> supportedDevices = new LinkedHashMap<>();
        supportedDevices.put(UsbId.VENDOR_ARDUINO, new int[] {
                UsbId.ARDUINO_UNO,
                UsbId.ARDUINO_UNO_R3,
                UsbId.ARDUINO_MEGA_2560,
                UsbId.ARDUINO_MEGA_2560_R3,
                UsbId.ARDUINO_SERIAL_ADAPTER,
                UsbId.ARDUINO_SERIAL_ADAPTER_R3,
                UsbId.ARDUINO_MEGA_ADK,
                UsbId.ARDUINO_MEGA_ADK_R3,
                UsbId.ARDUINO_LEONARDO,
                UsbId.ARDUINO_MICRO
        });
        supportedDevices.put(UsbId.VENDOR_VAN_OOIJEN_TECH, new int[] {
                UsbId.VAN_OOIJEN_TECH_TEENSYDUINO_SERIAL
        });
        supportedDevices.put(UsbId.VENDOR_ATMEL, new int[] {
                UsbId.ATMEL_LUFA_CDC_DEMO_APP
        });
        supportedDevices.put(UsbId.VENDOR_LEAFLABS, new int[] {
                UsbId.LEAFLABS_MAPLE
        });
        supportedDevices.put(UsbId.VENDOR_ARM, new int[] {
                UsbId.ARM_MBED
        });
        supportedDevices.put(UsbId.VENDOR_ST, new int[] {
                UsbId.ST_CDC,
                UsbId.ST_CDC2,
                UsbId.ST_CDC3,
                UsbId.CDC_WOLF_PID
        });
        supportedDevices.put(UsbId.VENDOR_RASPBERRY_PI, new int[] {
                UsbId.RASPBERRY_PI_PICO_MICROPYTHON
        });
        supportedDevices.put(UsbId.VENDOR_ICOM, new int[] {
                UsbId.IC_R30,
                UsbId.IC_705,
                UsbId.ICOM_USB_SERIAL_CONTROL
        });
        supportedDevices.put(UsbId.VENDOR_QINHENG, new int[] {
                UsbId.XIEGU_X6100
        });
        return supportedDevices;
    }
}

final class Cp21xxSerialDriver implements UsbSerialDriver {
    private final UsbDevice device;
    private final List<UsbSerialPort> ports;

    Cp21xxSerialDriver(UsbDevice device) {
        this.device = device;
        this.ports = new ArrayList<>();
        for (int port = 0; port < device.getInterfaceCount(); port++) {
            ports.add(new Port(device, port));
        }
    }

    @Override
    public UsbDevice getDevice() {
        return device;
    }

    @Override
    public List<UsbSerialPort> getPorts() {
        return ports;
    }

    public static Map<Integer, int[]> getSupportedDevices() {
        Map<Integer, int[]> supportedDevices = new LinkedHashMap<>();
        supportedDevices.put(UsbId.VENDOR_SILABS, new int[] {
                UsbId.SILABS_CP2102,
                UsbId.SILABS_CP2105,
                UsbId.SILABS_CP2108
        });
        return supportedDevices;
    }

    private final class Port extends CommonUsbSerialPort {
        private static final int USB_WRITE_TIMEOUT_MILLIS = 5000;
        private static final int REQTYPE_HOST_TO_DEVICE = 0x41;
        private static final int REQTYPE_DEVICE_TO_HOST = 0xc1;
        private static final int SILABSER_IFC_ENABLE_REQUEST_CODE = 0x00;
        private static final int SILABSER_SET_LINE_CTL_REQUEST_CODE = 0x03;
        private static final int SILABSER_SET_BREAK_REQUEST_CODE = 0x05;
        private static final int SILABSER_SET_MHS_REQUEST_CODE = 0x07;
        private static final int SILABSER_SET_BAUDRATE = 0x1E;
        private static final int SILABSER_FLUSH_REQUEST_CODE = 0x12;
        private static final int SILABSER_GET_MDMSTS_REQUEST_CODE = 0x08;

        private static final int FLUSH_READ_CODE = 0x0a;
        private static final int FLUSH_WRITE_CODE = 0x05;

        private static final int UART_ENABLE = 0x0001;
        private static final int UART_DISABLE = 0x0000;

        private static final int DTR_ENABLE = 0x101;
        private static final int DTR_DISABLE = 0x100;
        private static final int RTS_ENABLE = 0x202;
        private static final int RTS_DISABLE = 0x200;

        private static final int STATUS_CTS = 0x10;
        private static final int STATUS_DSR = 0x20;
        private static final int STATUS_RI = 0x40;
        private static final int STATUS_CD = 0x80;

        private boolean dtr;
        private boolean rts;
        private boolean restrictedPort;

        private Port(UsbDevice device, int portNumber) {
            super(device, portNumber);
        }

        @Override
        public UsbSerialDriver getDriver() {
            return Cp21xxSerialDriver.this;
        }

        private void setConfigSingle(int request, int value) throws IOException {
            int result = connection.controlTransfer(
                    REQTYPE_HOST_TO_DEVICE,
                    request,
                    value,
                    portNumber,
                    null,
                    0,
                    USB_WRITE_TIMEOUT_MILLIS
            );
            if (result != 0) {
                throw new IOException("Control transfer failed: " + request + " / " + value + " -> " + result);
            }
        }

        private byte getStatus() throws IOException {
            byte[] buffer = new byte[1];
            int result = connection.controlTransfer(
                    REQTYPE_DEVICE_TO_HOST,
                    SILABSER_GET_MDMSTS_REQUEST_CODE,
                    0,
                    portNumber,
                    buffer,
                    buffer.length,
                    USB_WRITE_TIMEOUT_MILLIS
            );
            if (result != 1) {
                throw new IOException(
                        "Control transfer failed: " + SILABSER_GET_MDMSTS_REQUEST_CODE + " / 0 -> " + result
                );
            }
            return buffer[0];
        }

        @Override
        protected void openInt(UsbDeviceConnection connection) throws IOException {
            restrictedPort = device.getInterfaceCount() == 2 && portNumber == 1;
            if (portNumber >= device.getInterfaceCount()) {
                throw new IOException("Unknown port number");
            }
            UsbInterface dataInterface = device.getInterface(portNumber);
            if (!this.connection.claimInterface(dataInterface, true)) {
                throw new IOException("Could not claim interface " + portNumber);
            }
            for (int index = 0; index < dataInterface.getEndpointCount(); index++) {
                UsbEndpoint endpoint = dataInterface.getEndpoint(index);
                if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                        readEndpoint = endpoint;
                    } else {
                        writeEndpoint = endpoint;
                    }
                }
            }

            setConfigSingle(SILABSER_IFC_ENABLE_REQUEST_CODE, UART_ENABLE);
            setConfigSingle(
                    SILABSER_SET_MHS_REQUEST_CODE,
                    (dtr ? DTR_ENABLE : DTR_DISABLE) | (rts ? RTS_ENABLE : RTS_DISABLE)
            );
        }

        @Override
        protected void closeInt() {
            try {
                setConfigSingle(SILABSER_IFC_ENABLE_REQUEST_CODE, UART_DISABLE);
            } catch (Exception ignored) {
            }
            try {
                connection.releaseInterface(device.getInterface(portNumber));
            } catch (Exception ignored) {
            }
        }

        private void setBaudRate(int baudRate) throws IOException {
            byte[] data = new byte[] {
                    (byte) (baudRate & 0xff),
                    (byte) ((baudRate >> 8) & 0xff),
                    (byte) ((baudRate >> 16) & 0xff),
                    (byte) ((baudRate >> 24) & 0xff)
            };
            int result = connection.controlTransfer(
                    REQTYPE_HOST_TO_DEVICE,
                    SILABSER_SET_BAUDRATE,
                    0,
                    portNumber,
                    data,
                    4,
                    USB_WRITE_TIMEOUT_MILLIS
            );
            if (result < 0) {
                throw new IOException("Error setting baud rate");
            }
        }

        @Override
        public void setParameters(int baudRate, int dataBits, int stopBits, @Parity int parity)
                throws IOException {
            if (baudRate <= 0) {
                throw new IllegalArgumentException("Invalid baud rate: " + baudRate);
            }
            setBaudRate(baudRate);

            int configDataBits = 0;
            switch (dataBits) {
                case DATABITS_5:
                    if (restrictedPort) {
                        throw new UnsupportedOperationException("Unsupported data bits: " + dataBits);
                    }
                    configDataBits |= 0x0500;
                    break;
                case DATABITS_6:
                    if (restrictedPort) {
                        throw new UnsupportedOperationException("Unsupported data bits: " + dataBits);
                    }
                    configDataBits |= 0x0600;
                    break;
                case DATABITS_7:
                    if (restrictedPort) {
                        throw new UnsupportedOperationException("Unsupported data bits: " + dataBits);
                    }
                    configDataBits |= 0x0700;
                    break;
                case DATABITS_8:
                    configDataBits |= 0x0800;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid data bits: " + dataBits);
            }

            switch (parity) {
                case PARITY_NONE:
                    break;
                case PARITY_ODD:
                    configDataBits |= 0x0010;
                    break;
                case PARITY_EVEN:
                    configDataBits |= 0x0020;
                    break;
                case PARITY_MARK:
                    if (restrictedPort) {
                        throw new UnsupportedOperationException("Unsupported parity: mark");
                    }
                    configDataBits |= 0x0030;
                    break;
                case PARITY_SPACE:
                    if (restrictedPort) {
                        throw new UnsupportedOperationException("Unsupported parity: space");
                    }
                    configDataBits |= 0x0040;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid parity: " + parity);
            }

            switch (stopBits) {
                case STOPBITS_1:
                    break;
                case STOPBITS_1_5:
                    throw new UnsupportedOperationException("Unsupported stop bits: 1.5");
                case STOPBITS_2:
                    if (restrictedPort) {
                        throw new UnsupportedOperationException("Unsupported stop bits: 2");
                    }
                    configDataBits |= 2;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid stop bits: " + stopBits);
            }
            setConfigSingle(SILABSER_SET_LINE_CTL_REQUEST_CODE, configDataBits);
        }

        @Override
        public boolean getCD() throws IOException {
            return (getStatus() & STATUS_CD) != 0;
        }

        @Override
        public boolean getCTS() throws IOException {
            return (getStatus() & STATUS_CTS) != 0;
        }

        @Override
        public boolean getDSR() throws IOException {
            return (getStatus() & STATUS_DSR) != 0;
        }

        @Override
        public boolean getDTR() {
            return dtr;
        }

        @Override
        public void setDTR(boolean value) throws IOException {
            dtr = value;
            setConfigSingle(SILABSER_SET_MHS_REQUEST_CODE, dtr ? DTR_ENABLE : DTR_DISABLE);
        }

        @Override
        public boolean getRI() throws IOException {
            return (getStatus() & STATUS_RI) != 0;
        }

        @Override
        public boolean getRTS() {
            return rts;
        }

        @Override
        public void setRTS(boolean value) throws IOException {
            rts = value;
            setConfigSingle(SILABSER_SET_MHS_REQUEST_CODE, rts ? RTS_ENABLE : RTS_DISABLE);
        }

        @Override
        public EnumSet<ControlLine> getControlLines() throws IOException {
            byte status = getStatus();
            EnumSet<ControlLine> set = EnumSet.noneOf(ControlLine.class);
            if (rts) {
                set.add(ControlLine.RTS);
            }
            if ((status & STATUS_CTS) != 0) {
                set.add(ControlLine.CTS);
            }
            if (dtr) {
                set.add(ControlLine.DTR);
            }
            if ((status & STATUS_DSR) != 0) {
                set.add(ControlLine.DSR);
            }
            if ((status & STATUS_CD) != 0) {
                set.add(ControlLine.CD);
            }
            if ((status & STATUS_RI) != 0) {
                set.add(ControlLine.RI);
            }
            return set;
        }

        @Override
        public EnumSet<ControlLine> getSupportedControlLines() {
            return EnumSet.allOf(ControlLine.class);
        }

        @Override
        public void purgeHwBuffers(boolean purgeWriteBuffers, boolean purgeReadBuffers) throws IOException {
            int value = (purgeReadBuffers ? FLUSH_READ_CODE : 0)
                    | (purgeWriteBuffers ? FLUSH_WRITE_CODE : 0);
            if (value != 0) {
                setConfigSingle(SILABSER_FLUSH_REQUEST_CODE, value);
            }
        }

        @Override
        public void setBreak(boolean value) throws IOException {
            setConfigSingle(SILABSER_SET_BREAK_REQUEST_CODE, value ? 1 : 0);
        }
    }
}

final class FtdiSerialDriver implements UsbSerialDriver {
    private static final String TAG = "FtdiSerialDriver";

    private final UsbDevice device;
    private final List<UsbSerialPort> ports;

    FtdiSerialDriver(UsbDevice device) {
        this.device = device;
        this.ports = new ArrayList<>();
        for (int port = 0; port < device.getInterfaceCount(); port++) {
            ports.add(new Port(device, port));
        }
    }

    @Override
    public UsbDevice getDevice() {
        return device;
    }

    @Override
    public List<UsbSerialPort> getPorts() {
        return ports;
    }

    public static Map<Integer, int[]> getSupportedDevices() {
        Map<Integer, int[]> supportedDevices = new LinkedHashMap<>();
        supportedDevices.put(UsbId.VENDOR_FTDI, new int[] {
                UsbId.FTDI_FT232R,
                UsbId.FTDI_ZERO,
                UsbId.FTDI_FT232H,
                UsbId.FTDI_FT2232H,
                UsbId.FTDI_FT4232H,
                UsbId.FTDI_FT231X
        });
        return supportedDevices;
    }

    private final class Port extends CommonUsbSerialPort {
        private static final int USB_WRITE_TIMEOUT_MILLIS = 5000;
        private static final int READ_HEADER_LENGTH = 2;

        private static final int REQTYPE_HOST_TO_DEVICE =
                UsbConstants.USB_TYPE_VENDOR | UsbConstants.USB_DIR_OUT;
        private static final int REQTYPE_DEVICE_TO_HOST =
                UsbConstants.USB_TYPE_VENDOR | UsbConstants.USB_DIR_IN;

        private static final int RESET_REQUEST = 0;
        private static final int MODEM_CONTROL_REQUEST = 1;
        private static final int SET_BAUD_RATE_REQUEST = 3;
        private static final int SET_DATA_REQUEST = 4;
        private static final int GET_MODEM_STATUS_REQUEST = 5;

        private static final int MODEM_CONTROL_DTR_ENABLE = 0x0101;
        private static final int MODEM_CONTROL_DTR_DISABLE = 0x0100;
        private static final int MODEM_CONTROL_RTS_ENABLE = 0x0202;
        private static final int MODEM_CONTROL_RTS_DISABLE = 0x0200;
        private static final int MODEM_STATUS_CTS = 0x10;
        private static final int MODEM_STATUS_DSR = 0x20;
        private static final int MODEM_STATUS_RI = 0x40;
        private static final int MODEM_STATUS_CD = 0x80;
        private static final int RESET_ALL = 0;
        private static final int RESET_PURGE_RX = 1;
        private static final int RESET_PURGE_TX = 2;

        private boolean baudRateWithPort;
        private boolean dtr;
        private boolean rts;
        private int breakConfig;

        private Port(UsbDevice device, int portNumber) {
            super(device, portNumber);
        }

        @Override
        public UsbSerialDriver getDriver() {
            return FtdiSerialDriver.this;
        }

        @Override
        protected void openInt(UsbDeviceConnection connection) throws IOException {
            if (!this.connection.claimInterface(device.getInterface(portNumber), true)) {
                throw new IOException("Could not claim interface " + portNumber);
            }
            if (device.getInterface(portNumber).getEndpointCount() < 2) {
                throw new IOException("Not enough endpoints");
            }
            readEndpoint = device.getInterface(portNumber).getEndpoint(0);
            writeEndpoint = device.getInterface(portNumber).getEndpoint(1);

            int result = this.connection.controlTransfer(
                    REQTYPE_HOST_TO_DEVICE,
                    RESET_REQUEST,
                    RESET_ALL,
                    portNumber + 1,
                    null,
                    0,
                    USB_WRITE_TIMEOUT_MILLIS
            );
            if (result != 0) {
                throw new IOException("Reset failed: result=" + result);
            }
            result = this.connection.controlTransfer(
                    REQTYPE_HOST_TO_DEVICE,
                    MODEM_CONTROL_REQUEST,
                    (dtr ? MODEM_CONTROL_DTR_ENABLE : MODEM_CONTROL_DTR_DISABLE)
                            | (rts ? MODEM_CONTROL_RTS_ENABLE : MODEM_CONTROL_RTS_DISABLE),
                    portNumber + 1,
                    null,
                    0,
                    USB_WRITE_TIMEOUT_MILLIS
            );
            if (result != 0) {
                throw new IOException("Init RTS,DTR failed: result=" + result);
            }

            byte[] rawDescriptors = connection.getRawDescriptors();
            if (rawDescriptors == null || rawDescriptors.length < 14) {
                throw new IOException("Could not get device descriptors");
            }
            int deviceType = rawDescriptors[13];
            baudRateWithPort = deviceType == 7 || deviceType == 8 || deviceType == 9
                    || device.getInterfaceCount() > 1;
        }

        @Override
        protected void closeInt() {
            try {
                connection.releaseInterface(device.getInterface(portNumber));
            } catch (Exception ignored) {
            }
        }

        @Override
        public int read(byte[] dest, int timeout) throws IOException {
            if (dest.length <= READ_HEADER_LENGTH) {
                throw new IllegalArgumentException("Read buffer too small");
            }
            int readLength;
            if (timeout != 0) {
                long endTime = MonotonicClock.millis() + timeout;
                do {
                    readLength = connection.bulkTransfer(
                            readEndpoint,
                            dest,
                            Math.min(dest.length, 16 * 1024),
                            Math.max(1, (int) (endTime - MonotonicClock.millis()))
                    );
                } while (readLength == READ_HEADER_LENGTH && MonotonicClock.millis() < endTime);
                if (readLength <= 0 && MonotonicClock.millis() < endTime) {
                    testConnection();
                }
            } else {
                do {
                    readLength = super.read(dest, timeout);
                } while (readLength == READ_HEADER_LENGTH);
            }
            return readFilter(dest, readLength);
        }

        private int readFilter(byte[] buffer, int totalBytesRead) throws IOException {
            int maxPacketSize = readEndpoint.getMaxPacketSize();
            int destPos = 0;
            for (int srcPos = 0; srcPos < totalBytesRead; srcPos += maxPacketSize) {
                int length = Math.min(srcPos + maxPacketSize, totalBytesRead) - (srcPos + READ_HEADER_LENGTH);
                if (length < 0) {
                    throw new IOException("Expected at least " + READ_HEADER_LENGTH + " bytes");
                }
                System.arraycopy(buffer, srcPos + READ_HEADER_LENGTH, buffer, destPos, length);
                destPos += length;
            }
            return destPos;
        }

        private void setBaudRate(int baudRate) throws IOException {
            int divisor;
            int subdivisor;
            int effectiveBaudRate;
            if (baudRate > 3500000) {
                throw new UnsupportedOperationException("Baud rate too high");
            } else if (baudRate >= 2500000) {
                divisor = 0;
                subdivisor = 0;
                effectiveBaudRate = 3000000;
            } else if (baudRate >= 1750000) {
                divisor = 1;
                subdivisor = 0;
                effectiveBaudRate = 2000000;
            } else {
                divisor = (24000000 << 1) / baudRate;
                divisor = (divisor + 1) >> 1;
                subdivisor = divisor & 0x07;
                divisor >>= 3;
                if (divisor > 0x3fff) {
                    throw new UnsupportedOperationException("Baud rate too low");
                }
                effectiveBaudRate = (24000000 << 1) / ((divisor << 3) + subdivisor);
                effectiveBaudRate = (effectiveBaudRate + 1) >> 1;
            }
            double baudRateError = Math.abs(1.0 - (effectiveBaudRate / (double) baudRate));
            if (baudRateError >= 0.031) {
                throw new UnsupportedOperationException("Baud rate deviation too high");
            }
            int value = divisor;
            int index = 0;
            switch (subdivisor) {
                case 0:
                    break;
                case 4:
                    value |= 0x4000;
                    break;
                case 2:
                    value |= 0x8000;
                    break;
                case 1:
                    value |= 0xc000;
                    break;
                case 3:
                    index |= 1;
                    break;
                case 5:
                    value |= 0x4000;
                    index |= 1;
                    break;
                case 6:
                    value |= 0x8000;
                    index |= 1;
                    break;
                case 7:
                    value |= 0xc000;
                    index |= 1;
                    break;
                default:
                    break;
            }
            if (baudRateWithPort) {
                index <<= 8;
                index |= portNumber + 1;
            }
            Log.d(TAG, "FTDI baud=" + baudRate + " value=0x" + Integer.toHexString(value)
                    + " index=0x" + Integer.toHexString(index));
            int result = connection.controlTransfer(
                    REQTYPE_HOST_TO_DEVICE,
                    SET_BAUD_RATE_REQUEST,
                    value,
                    index,
                    null,
                    0,
                    USB_WRITE_TIMEOUT_MILLIS
            );
            if (result != 0) {
                throw new IOException("Setting baudrate failed: result=" + result);
            }
        }

        @Override
        public void setParameters(int baudRate, int dataBits, int stopBits, @Parity int parity)
                throws IOException {
            if (baudRate <= 0) {
                throw new IllegalArgumentException("Invalid baud rate: " + baudRate);
            }
            setBaudRate(baudRate);

            int config = 0;
            switch (dataBits) {
                case DATABITS_7:
                case DATABITS_8:
                    config |= dataBits;
                    break;
                case DATABITS_5:
                case DATABITS_6:
                    throw new UnsupportedOperationException("Unsupported data bits: " + dataBits);
                default:
                    throw new IllegalArgumentException("Invalid data bits: " + dataBits);
            }
            switch (parity) {
                case PARITY_NONE:
                    break;
                case PARITY_ODD:
                    config |= 0x100;
                    break;
                case PARITY_EVEN:
                    config |= 0x200;
                    break;
                case PARITY_MARK:
                    config |= 0x300;
                    break;
                case PARITY_SPACE:
                    config |= 0x400;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid parity: " + parity);
            }
            switch (stopBits) {
                case STOPBITS_1:
                    break;
                case STOPBITS_1_5:
                    throw new UnsupportedOperationException("Unsupported stop bits: 1.5");
                case STOPBITS_2:
                    config |= 0x1000;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid stop bits: " + stopBits);
            }

            int result = connection.controlTransfer(
                    REQTYPE_HOST_TO_DEVICE,
                    SET_DATA_REQUEST,
                    config,
                    portNumber + 1,
                    null,
                    0,
                    USB_WRITE_TIMEOUT_MILLIS
            );
            if (result != 0) {
                throw new IOException("Setting parameters failed: result=" + result);
            }
            breakConfig = config;
        }

        private int getStatus() throws IOException {
            byte[] data = new byte[2];
            int result = connection.controlTransfer(
                    REQTYPE_DEVICE_TO_HOST,
                    GET_MODEM_STATUS_REQUEST,
                    0,
                    portNumber + 1,
                    data,
                    data.length,
                    USB_WRITE_TIMEOUT_MILLIS
            );
            if (result != 2) {
                throw new IOException("Get modem status failed: result=" + result);
            }
            return data[0];
        }

        @Override
        public boolean getCD() throws IOException {
            return (getStatus() & MODEM_STATUS_CD) != 0;
        }

        @Override
        public boolean getCTS() throws IOException {
            return (getStatus() & MODEM_STATUS_CTS) != 0;
        }

        @Override
        public boolean getDSR() throws IOException {
            return (getStatus() & MODEM_STATUS_DSR) != 0;
        }

        @Override
        public boolean getDTR() {
            return dtr;
        }

        @Override
        public void setDTR(boolean value) throws IOException {
            int result = connection.controlTransfer(
                    REQTYPE_HOST_TO_DEVICE,
                    MODEM_CONTROL_REQUEST,
                    value ? MODEM_CONTROL_DTR_ENABLE : MODEM_CONTROL_DTR_DISABLE,
                    portNumber + 1,
                    null,
                    0,
                    USB_WRITE_TIMEOUT_MILLIS
            );
            if (result != 0) {
                throw new IOException("Set DTR failed: result=" + result);
            }
            dtr = value;
        }

        @Override
        public boolean getRI() throws IOException {
            return (getStatus() & MODEM_STATUS_RI) != 0;
        }

        @Override
        public boolean getRTS() {
            return rts;
        }

        @Override
        public void setRTS(boolean value) throws IOException {
            int result = connection.controlTransfer(
                    REQTYPE_HOST_TO_DEVICE,
                    MODEM_CONTROL_REQUEST,
                    value ? MODEM_CONTROL_RTS_ENABLE : MODEM_CONTROL_RTS_DISABLE,
                    portNumber + 1,
                    null,
                    0,
                    USB_WRITE_TIMEOUT_MILLIS
            );
            if (result != 0) {
                throw new IOException("Set RTS failed: result=" + result);
            }
            rts = value;
        }

        @Override
        public EnumSet<ControlLine> getControlLines() throws IOException {
            int status = getStatus();
            EnumSet<ControlLine> set = EnumSet.noneOf(ControlLine.class);
            if (rts) {
                set.add(ControlLine.RTS);
            }
            if ((status & MODEM_STATUS_CTS) != 0) {
                set.add(ControlLine.CTS);
            }
            if (dtr) {
                set.add(ControlLine.DTR);
            }
            if ((status & MODEM_STATUS_DSR) != 0) {
                set.add(ControlLine.DSR);
            }
            if ((status & MODEM_STATUS_CD) != 0) {
                set.add(ControlLine.CD);
            }
            if ((status & MODEM_STATUS_RI) != 0) {
                set.add(ControlLine.RI);
            }
            return set;
        }

        @Override
        public EnumSet<ControlLine> getSupportedControlLines() {
            return EnumSet.allOf(ControlLine.class);
        }

        @Override
        public void purgeHwBuffers(boolean purgeWriteBuffers, boolean purgeReadBuffers) throws IOException {
            if (purgeWriteBuffers) {
                int result = connection.controlTransfer(
                        REQTYPE_HOST_TO_DEVICE,
                        RESET_REQUEST,
                        RESET_PURGE_RX,
                        portNumber + 1,
                        null,
                        0,
                        USB_WRITE_TIMEOUT_MILLIS
                );
                if (result != 0) {
                    throw new IOException("Purge write buffer failed: result=" + result);
                }
            }
            if (purgeReadBuffers) {
                int result = connection.controlTransfer(
                        REQTYPE_HOST_TO_DEVICE,
                        RESET_REQUEST,
                        RESET_PURGE_TX,
                        portNumber + 1,
                        null,
                        0,
                        USB_WRITE_TIMEOUT_MILLIS
                );
                if (result != 0) {
                    throw new IOException("Purge read buffer failed: result=" + result);
                }
            }
        }

        @Override
        public void setBreak(boolean value) throws IOException {
            int config = breakConfig;
            if (value) {
                config |= 0x4000;
            }
            int result = connection.controlTransfer(
                    REQTYPE_HOST_TO_DEVICE,
                    SET_DATA_REQUEST,
                    config,
                    portNumber + 1,
                    null,
                    0,
                    USB_WRITE_TIMEOUT_MILLIS
            );
            if (result != 0) {
                throw new IOException("Setting BREAK failed: result=" + result);
            }
        }
    }
}

final class Ch34xSerialDriver implements UsbSerialDriver {
    private final UsbDevice device;
    private final UsbSerialPort port;

    Ch34xSerialDriver(UsbDevice device) {
        this.device = device;
        this.port = new Port(device, 0);
    }

    @Override
    public UsbDevice getDevice() {
        return device;
    }

    @Override
    public List<UsbSerialPort> getPorts() {
        return java.util.Collections.singletonList(port);
    }

    public static Map<Integer, int[]> getSupportedDevices() {
        Map<Integer, int[]> supportedDevices = new LinkedHashMap<>();
        supportedDevices.put(UsbId.VENDOR_QINHENG, new int[] {
                UsbId.QINHENG_CH340,
                UsbId.QINHENG_CH341A,
                UsbId.QINHENG_CH341K
        });
        return supportedDevices;
    }

    private final class Port extends CommonUsbSerialPort {
        private static final String TAG = "Ch34xSerialDriver";
        private static final int USB_TIMEOUT_MILLIS = 5000;
        private static final int DEFAULT_BAUD_RATE = 9600;

        private static final int LCR_ENABLE_RX = 0x80;
        private static final int LCR_ENABLE_TX = 0x40;
        private static final int LCR_MARK_SPACE = 0x20;
        private static final int LCR_PAR_EVEN = 0x10;
        private static final int LCR_ENABLE_PAR = 0x08;
        private static final int LCR_STOP_BITS_2 = 0x04;
        private static final int LCR_CS8 = 0x03;
        private static final int LCR_CS7 = 0x02;
        private static final int LCR_CS6 = 0x01;
        private static final int LCR_CS5 = 0x00;

        private static final int GCL_CTS = 0x01;
        private static final int GCL_DSR = 0x02;
        private static final int GCL_RI = 0x04;
        private static final int GCL_CD = 0x08;
        private static final int SCL_DTR = 0x20;
        private static final int SCL_RTS = 0x40;

        private boolean dtr;
        private boolean rts;

        private Port(UsbDevice device, int portNumber) {
            super(device, portNumber);
        }

        @Override
        public UsbSerialDriver getDriver() {
            return Ch34xSerialDriver.this;
        }

        @Override
        protected void openInt(UsbDeviceConnection connection) throws IOException {
            for (int index = 0; index < device.getInterfaceCount(); index++) {
                UsbInterface usbInterface = device.getInterface(index);
                if (!this.connection.claimInterface(usbInterface, true)) {
                    throw new IOException("Could not claim data interface");
                }
            }
            UsbInterface dataInterface = device.getInterface(device.getInterfaceCount() - 1);
            for (int index = 0; index < dataInterface.getEndpointCount(); index++) {
                UsbEndpoint endpoint = dataInterface.getEndpoint(index);
                if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                        readEndpoint = endpoint;
                    } else {
                        writeEndpoint = endpoint;
                    }
                }
            }
            initialize();
            setBaudRate(DEFAULT_BAUD_RATE);
        }

        @Override
        protected void closeInt() {
            try {
                for (int index = 0; index < device.getInterfaceCount(); index++) {
                    connection.releaseInterface(device.getInterface(index));
                }
            } catch (Exception ignored) {
            }
        }

        private int controlOut(int request, int value, int index) {
            int requestType = UsbConstants.USB_TYPE_VENDOR | UsbConstants.USB_DIR_OUT;
            return connection.controlTransfer(requestType, request, value, index, null, 0, USB_TIMEOUT_MILLIS);
        }

        private int controlIn(int request, int value, int index, byte[] buffer) {
            int requestType = UsbConstants.USB_TYPE_VENDOR | UsbConstants.USB_DIR_IN;
            return connection.controlTransfer(
                    requestType,
                    request,
                    value,
                    index,
                    buffer,
                    buffer.length,
                    USB_TIMEOUT_MILLIS
            );
        }

        private void checkState(String message, int request, int value, int[] expected) throws IOException {
            byte[] buffer = new byte[expected.length];
            int result = controlIn(request, value, 0, buffer);
            if (result < 0) {
                throw new IOException("Failed send cmd [" + message + "]");
            }
            if (result != expected.length) {
                throw new IOException("Expected " + expected.length + " bytes but got " + result + " [" + message + "]");
            }
            for (int index = 0; index < expected.length; index++) {
                if (expected[index] == -1) {
                    continue;
                }
                int current = buffer[index] & 0xff;
                if (expected[index] != current) {
                    throw new IOException("Expected 0x" + Integer.toHexString(expected[index])
                            + " but got 0x" + Integer.toHexString(current) + " [" + message + "]");
                }
            }
        }

        private void setControlLines() throws IOException {
            if (controlOut(0xa4, ~((dtr ? SCL_DTR : 0) | (rts ? SCL_RTS : 0)), 0) < 0) {
                throw new IOException("Failed to set control lines");
            }
        }

        private byte getStatus() throws IOException {
            byte[] buffer = new byte[2];
            int result = controlIn(0x95, 0x0706, 0, buffer);
            if (result < 0) {
                throw new IOException("Error getting control lines");
            }
            return buffer[0];
        }

        private void initialize() throws IOException {
            checkState("init #1", 0x5f, 0, new int[] { -1, 0x00 });
            if (controlOut(0xa1, 0, 0) < 0) {
                throw new IOException("Init failed: #2");
            }
            setBaudRate(DEFAULT_BAUD_RATE);
            checkState("init #4", 0x95, 0x2518, new int[] { -1, 0x00 });
            if (controlOut(0x9a, 0x2518, LCR_ENABLE_RX | LCR_ENABLE_TX | LCR_CS8) < 0) {
                throw new IOException("Init failed: #5");
            }
            checkState("init #6", 0x95, 0x0706, new int[] { -1, -1 });
            if (controlOut(0xa1, 0x501f, 0xd90a) < 0) {
                throw new IOException("Init failed: #7");
            }
            setBaudRate(DEFAULT_BAUD_RATE);
            setControlLines();
            checkState("init #10", 0x95, 0x0706, new int[] { -1, -1 });
        }

        private void setBaudRate(int baudRate) throws IOException {
            long factor;
            long divisor;
            if (baudRate == 921600) {
                divisor = 7;
                factor = 0xf300;
            } else {
                long baudbaseFactor = 1532620800L;
                int baudbaseDivMax = 3;
                factor = baudbaseFactor / baudRate;
                divisor = baudbaseDivMax;
                while ((factor > 0xfff0) && divisor > 0) {
                    factor >>= 3;
                    divisor--;
                }
                if (factor > 0xfff0) {
                    throw new UnsupportedOperationException("Unsupported baud rate: " + baudRate);
                }
                factor = 0x10000 - factor;
            }
            divisor |= 0x0080;
            int value1 = (int) ((factor & 0xff00) | divisor);
            int value2 = (int) (factor & 0xff);
            Log.d(TAG, "baud rate=" + baudRate + ", 0x1312=0x" + Integer.toHexString(value1)
                    + ", 0x0f2c=0x" + Integer.toHexString(value2));
            if (controlOut(0x9a, 0x1312, value1) < 0) {
                throw new IOException("Error setting baud rate: #1");
            }
            if (controlOut(0x9a, 0x0f2c, value2) < 0) {
                throw new IOException("Error setting baud rate: #2");
            }
        }

        @Override
        public void setParameters(int baudRate, int dataBits, int stopBits, @Parity int parity)
                throws IOException {
            if (baudRate <= 0) {
                throw new IllegalArgumentException("Invalid baud rate: " + baudRate);
            }
            setBaudRate(baudRate);

            int lcr = LCR_ENABLE_RX | LCR_ENABLE_TX;
            switch (dataBits) {
                case DATABITS_5:
                    lcr |= LCR_CS5;
                    break;
                case DATABITS_6:
                    lcr |= LCR_CS6;
                    break;
                case DATABITS_7:
                    lcr |= LCR_CS7;
                    break;
                case DATABITS_8:
                    lcr |= LCR_CS8;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid data bits: " + dataBits);
            }
            switch (parity) {
                case PARITY_NONE:
                    break;
                case PARITY_ODD:
                    lcr |= LCR_ENABLE_PAR;
                    break;
                case PARITY_EVEN:
                    lcr |= LCR_ENABLE_PAR | LCR_PAR_EVEN;
                    break;
                case PARITY_MARK:
                    lcr |= LCR_ENABLE_PAR | LCR_MARK_SPACE;
                    break;
                case PARITY_SPACE:
                    lcr |= LCR_ENABLE_PAR | LCR_MARK_SPACE | LCR_PAR_EVEN;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid parity: " + parity);
            }
            switch (stopBits) {
                case STOPBITS_1:
                    break;
                case STOPBITS_1_5:
                    throw new UnsupportedOperationException("Unsupported stop bits: 1.5");
                case STOPBITS_2:
                    lcr |= LCR_STOP_BITS_2;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid stop bits: " + stopBits);
            }
            if (controlOut(0x9a, 0x2518, lcr) < 0) {
                throw new IOException("Error setting control byte");
            }
        }

        @Override
        public boolean getCD() throws IOException {
            return (getStatus() & GCL_CD) == 0;
        }

        @Override
        public boolean getCTS() throws IOException {
            return (getStatus() & GCL_CTS) == 0;
        }

        @Override
        public boolean getDSR() throws IOException {
            return (getStatus() & GCL_DSR) == 0;
        }

        @Override
        public boolean getDTR() {
            return dtr;
        }

        @Override
        public void setDTR(boolean value) throws IOException {
            dtr = value;
            setControlLines();
        }

        @Override
        public boolean getRI() throws IOException {
            return (getStatus() & GCL_RI) == 0;
        }

        @Override
        public boolean getRTS() {
            return rts;
        }

        @Override
        public void setRTS(boolean value) throws IOException {
            rts = value;
            setControlLines();
        }

        @Override
        public EnumSet<ControlLine> getControlLines() throws IOException {
            int status = getStatus();
            EnumSet<ControlLine> set = EnumSet.noneOf(ControlLine.class);
            if (rts) {
                set.add(ControlLine.RTS);
            }
            if ((status & GCL_CTS) == 0) {
                set.add(ControlLine.CTS);
            }
            if (dtr) {
                set.add(ControlLine.DTR);
            }
            if ((status & GCL_DSR) == 0) {
                set.add(ControlLine.DSR);
            }
            if ((status & GCL_CD) == 0) {
                set.add(ControlLine.CD);
            }
            if ((status & GCL_RI) == 0) {
                set.add(ControlLine.RI);
            }
            return set;
        }

        @Override
        public EnumSet<ControlLine> getSupportedControlLines() {
            return EnumSet.allOf(ControlLine.class);
        }

        @Override
        public void setBreak(boolean value) throws IOException {
            byte[] request = new byte[2];
            if (controlIn(0x95, 0x1805, 0, request) < 0) {
                throw new IOException("Error getting BREAK condition");
            }
            if (value) {
                request[0] &= ~1;
                request[1] &= ~0x40;
            } else {
                request[0] |= 1;
                request[1] |= 0x40;
            }
            int encoded = (request[1] & 0xff) << 8 | (request[0] & 0xff);
            if (controlOut(0x9a, 0x1805, encoded) < 0) {
                throw new IOException("Error setting BREAK condition");
            }
        }
    }
}

final class ProlificSerialDriver implements UsbSerialDriver {
    private final UsbDevice device;
    private final UsbSerialPort port;

    ProlificSerialDriver(UsbDevice device) {
        this.device = device;
        this.port = new Port(device, 0);
    }

    @Override
    public UsbDevice getDevice() {
        return device;
    }

    @Override
    public List<UsbSerialPort> getPorts() {
        return java.util.Collections.singletonList(port);
    }

    public static Map<Integer, int[]> getSupportedDevices() {
        Map<Integer, int[]> supportedDevices = new LinkedHashMap<>();
        supportedDevices.put(UsbId.VENDOR_PROLIFIC, new int[] {
                UsbId.PROLIFIC_PL2303,
                UsbId.PROLIFIC_PL2303GC,
                UsbId.PROLIFIC_PL2303GB,
                UsbId.PROLIFIC_PL2303GT,
                UsbId.PROLIFIC_PL2303GL,
                UsbId.PROLIFIC_PL2303GE,
                UsbId.PROLIFIC_PL2303GS
        });
        return supportedDevices;
    }

    private enum DeviceType { DEVICE_TYPE_01, DEVICE_TYPE_T, DEVICE_TYPE_HX, DEVICE_TYPE_HXN }

    private final class Port extends CommonUsbSerialPort {
        private static final String TAG = "ProlificSerialDriver";
        private static final int USB_READ_TIMEOUT_MILLIS = 1000;
        private static final int USB_WRITE_TIMEOUT_MILLIS = 5000;
        private static final int USB_RECIP_INTERFACE = 0x01;

        private static final int VENDOR_READ_REQUEST = 0x01;
        private static final int VENDOR_WRITE_REQUEST = 0x01;
        private static final int VENDOR_READ_HXN_REQUEST = 0x81;
        private static final int VENDOR_WRITE_HXN_REQUEST = 0x80;

        private static final int VENDOR_OUT_REQTYPE = UsbConstants.USB_DIR_OUT | UsbConstants.USB_TYPE_VENDOR;
        private static final int VENDOR_IN_REQTYPE = UsbConstants.USB_DIR_IN | UsbConstants.USB_TYPE_VENDOR;
        private static final int CTRL_OUT_REQTYPE =
                UsbConstants.USB_DIR_OUT | UsbConstants.USB_TYPE_CLASS | USB_RECIP_INTERFACE;

        private static final int WRITE_ENDPOINT = 0x02;
        private static final int READ_ENDPOINT = 0x83;
        private static final int INTERRUPT_ENDPOINT = 0x81;

        private static final int RESET_HXN_REQUEST = 0x07;
        private static final int FLUSH_RX_REQUEST = 0x08;
        private static final int FLUSH_TX_REQUEST = 0x09;
        private static final int SET_LINE_REQUEST = 0x20;
        private static final int SET_CONTROL_REQUEST = 0x22;
        private static final int SEND_BREAK_REQUEST = 0x23;
        private static final int GET_CONTROL_HXN_REQUEST = 0x80;
        private static final int GET_CONTROL_REQUEST = 0x87;
        private static final int STATUS_NOTIFICATION = 0xa1;

        private static final int RESET_HXN_RX_PIPE = 1;
        private static final int RESET_HXN_TX_PIPE = 2;
        private static final int CONTROL_DTR = 0x01;
        private static final int CONTROL_RTS = 0x02;

        private static final int GET_CONTROL_FLAG_CD = 0x02;
        private static final int GET_CONTROL_FLAG_DSR = 0x04;
        private static final int GET_CONTROL_FLAG_RI = 0x01;
        private static final int GET_CONTROL_FLAG_CTS = 0x08;

        private static final int GET_CONTROL_HXN_FLAG_CD = 0x40;
        private static final int GET_CONTROL_HXN_FLAG_DSR = 0x20;
        private static final int GET_CONTROL_HXN_FLAG_RI = 0x80;
        private static final int GET_CONTROL_HXN_FLAG_CTS = 0x08;

        private static final int STATUS_FLAG_CD = 0x01;
        private static final int STATUS_FLAG_DSR = 0x02;
        private static final int STATUS_FLAG_RI = 0x08;
        private static final int STATUS_FLAG_CTS = 0x80;

        private static final int STATUS_BUFFER_SIZE = 10;
        private static final int STATUS_BYTE_INDEX = 8;

        private DeviceType deviceType = DeviceType.DEVICE_TYPE_HX;
        private UsbEndpoint interruptEndpoint;
        private int controlLinesValue;
        private int baudRate = -1;
        private int dataBits = -1;
        private int stopBits = -1;
        private int parity = -1;

        private int status;
        private volatile Thread readStatusThread;
        private final Object readStatusThreadLock = new Object();
        private boolean stopReadStatusThread;
        private IOException readStatusException;

        private Port(UsbDevice device, int portNumber) {
            super(device, portNumber);
        }

        @Override
        public UsbSerialDriver getDriver() {
            return ProlificSerialDriver.this;
        }

        private byte[] inControlTransfer(int requestType, int request, int value, int index, int length)
                throws IOException {
            byte[] buffer = new byte[length];
            int result = connection.controlTransfer(
                    requestType,
                    request,
                    value,
                    index,
                    buffer,
                    length,
                    USB_READ_TIMEOUT_MILLIS
            );
            if (result != length) {
                throw new IOException("ControlTransfer failed: " + result);
            }
            return buffer;
        }

        private void outControlTransfer(int requestType, int request, int value, int index, byte[] data)
                throws IOException {
            int length = data == null ? 0 : data.length;
            int result = connection.controlTransfer(
                    requestType,
                    request,
                    value,
                    index,
                    data,
                    length,
                    USB_WRITE_TIMEOUT_MILLIS
            );
            if (result != length) {
                throw new IOException("ControlTransfer failed: " + result);
            }
        }

        private byte[] vendorIn(int value, int index, int length) throws IOException {
            int request = deviceType == DeviceType.DEVICE_TYPE_HXN
                    ? VENDOR_READ_HXN_REQUEST
                    : VENDOR_READ_REQUEST;
            return inControlTransfer(VENDOR_IN_REQTYPE, request, value, index, length);
        }

        private void vendorOut(int value, int index, byte[] data) throws IOException {
            int request = deviceType == DeviceType.DEVICE_TYPE_HXN
                    ? VENDOR_WRITE_HXN_REQUEST
                    : VENDOR_WRITE_REQUEST;
            outControlTransfer(VENDOR_OUT_REQTYPE, request, value, index, data);
        }

        private void ctrlOut(int request, int value, int index, byte[] data) throws IOException {
            outControlTransfer(CTRL_OUT_REQTYPE, request, value, index, data);
        }

        private boolean testHxStatus() {
            try {
                inControlTransfer(VENDOR_IN_REQTYPE, VENDOR_READ_REQUEST, 0x8080, 0, 1);
                return true;
            } catch (IOException ignored) {
                return false;
            }
        }

        private void doBlackMagic() throws IOException {
            if (deviceType == DeviceType.DEVICE_TYPE_HXN) {
                return;
            }
            vendorIn(0x8484, 0, 1);
            vendorOut(0x0404, 0, null);
            vendorIn(0x8484, 0, 1);
            vendorIn(0x8383, 0, 1);
            vendorIn(0x8484, 0, 1);
            vendorOut(0x0404, 1, null);
            vendorIn(0x8484, 0, 1);
            vendorIn(0x8383, 0, 1);
            vendorOut(0, 1, null);
            vendorOut(1, 0, null);
            vendorOut(2, deviceType == DeviceType.DEVICE_TYPE_01 ? 0x24 : 0x44, null);
        }

        private void setControlLines(int newControlLinesValue) throws IOException {
            ctrlOut(SET_CONTROL_REQUEST, newControlLinesValue, 0, null);
            controlLinesValue = newControlLinesValue;
        }

        private void readStatusThreadFunction() {
            try {
                while (!stopReadStatusThread) {
                    byte[] buffer = new byte[STATUS_BUFFER_SIZE];
                    long endTime = MonotonicClock.millis() + 500;
                    int readBytesCount = connection.bulkTransfer(interruptEndpoint, buffer, STATUS_BUFFER_SIZE, 500);
                    if (readBytesCount == -1 && MonotonicClock.millis() < endTime) {
                        testConnection();
                    }
                    if (readBytesCount > 0) {
                        if (readBytesCount != STATUS_BUFFER_SIZE) {
                            throw new IOException("Invalid status notification length");
                        }
                        if (buffer[0] != (byte) STATUS_NOTIFICATION) {
                            throw new IOException("Invalid status notification request");
                        }
                        status = buffer[STATUS_BYTE_INDEX] & 0xff;
                    }
                }
            } catch (IOException exception) {
                readStatusException = exception;
            }
        }

        private int getStatus() throws IOException {
            if (readStatusThread == null && readStatusException == null) {
                synchronized (readStatusThreadLock) {
                    if (readStatusThread == null) {
                        status = 0;
                        if (deviceType == DeviceType.DEVICE_TYPE_HXN) {
                            byte[] data = vendorIn(GET_CONTROL_HXN_REQUEST, 0, 1);
                            if ((data[0] & GET_CONTROL_HXN_FLAG_CTS) == 0) status |= STATUS_FLAG_CTS;
                            if ((data[0] & GET_CONTROL_HXN_FLAG_DSR) == 0) status |= STATUS_FLAG_DSR;
                            if ((data[0] & GET_CONTROL_HXN_FLAG_CD) == 0) status |= STATUS_FLAG_CD;
                            if ((data[0] & GET_CONTROL_HXN_FLAG_RI) == 0) status |= STATUS_FLAG_RI;
                        } else {
                            byte[] data = vendorIn(GET_CONTROL_REQUEST, 0, 1);
                            if ((data[0] & GET_CONTROL_FLAG_CTS) == 0) status |= STATUS_FLAG_CTS;
                            if ((data[0] & GET_CONTROL_FLAG_DSR) == 0) status |= STATUS_FLAG_DSR;
                            if ((data[0] & GET_CONTROL_FLAG_CD) == 0) status |= STATUS_FLAG_CD;
                            if ((data[0] & GET_CONTROL_FLAG_RI) == 0) status |= STATUS_FLAG_RI;
                        }
                        readStatusThread = new Thread(this::readStatusThreadFunction, "cwcn-prolific-status");
                        readStatusThread.setDaemon(true);
                        readStatusThread.start();
                    }
                }
            }
            IOException exception = readStatusException;
            if (exception != null) {
                readStatusException = null;
                throw new IOException(exception);
            }
            return status;
        }

        private boolean testStatusFlag(int flag) throws IOException {
            return (getStatus() & flag) == flag;
        }

        private void resetDevice() throws IOException {
            purgeHwBuffers(true, true);
        }

        @Override
        protected void openInt(UsbDeviceConnection connection) throws IOException {
            UsbInterface usbInterface = device.getInterface(0);
            if (!this.connection.claimInterface(usbInterface, true)) {
                throw new IOException("Error claiming Prolific interface 0");
            }

            for (int index = 0; index < usbInterface.getEndpointCount(); index++) {
                UsbEndpoint currentEndpoint = usbInterface.getEndpoint(index);
                switch (currentEndpoint.getAddress()) {
                    case READ_ENDPOINT:
                        readEndpoint = currentEndpoint;
                        break;
                    case WRITE_ENDPOINT:
                        writeEndpoint = currentEndpoint;
                        break;
                    case INTERRUPT_ENDPOINT:
                        interruptEndpoint = currentEndpoint;
                        break;
                    default:
                        break;
                }
            }

            byte[] rawDescriptors = connection.getRawDescriptors();
            if (rawDescriptors == null || rawDescriptors.length < 14) {
                throw new IOException("Could not get device descriptors");
            }
            int usbVersion = (rawDescriptors[3] << 8) + rawDescriptors[2];
            int deviceVersion = (rawDescriptors[13] << 8) + rawDescriptors[12];
            byte maxPacketSize0 = rawDescriptors[7];
            if (device.getDeviceClass() == 0x02 || maxPacketSize0 != 64) {
                deviceType = DeviceType.DEVICE_TYPE_01;
            } else if (deviceVersion == 0x300 && usbVersion == 0x200) {
                deviceType = DeviceType.DEVICE_TYPE_T;
            } else if (deviceVersion == 0x500) {
                deviceType = DeviceType.DEVICE_TYPE_T;
            } else if (usbVersion == 0x200 && !testHxStatus()) {
                deviceType = DeviceType.DEVICE_TYPE_HXN;
            } else {
                deviceType = DeviceType.DEVICE_TYPE_HX;
            }
            Log.d(TAG, "Prolific deviceType=" + deviceType.name());
            resetDevice();
            doBlackMagic();
            setControlLines(controlLinesValue);
        }

        @Override
        protected void closeInt() {
            try {
                synchronized (readStatusThreadLock) {
                    if (readStatusThread != null) {
                        stopReadStatusThread = true;
                        readStatusThread.join();
                        stopReadStatusThread = false;
                        readStatusThread = null;
                        readStatusException = null;
                    }
                }
                resetDevice();
            } catch (Exception ignored) {
            }
            try {
                connection.releaseInterface(device.getInterface(0));
            } catch (Exception ignored) {
            }
        }

        private int filterBaudRate(int baudRate) {
            if (baudRate <= 0) {
                throw new IllegalArgumentException("Invalid baud rate: " + baudRate);
            }
            if (deviceType == DeviceType.DEVICE_TYPE_HXN) {
                return baudRate;
            }
            final int[] standardBaudRates = new int[] {
                    75, 150, 300, 600, 1200, 1800, 2400, 3600, 4800, 7200, 9600, 14400, 19200,
                    28800, 38400, 57600, 115200, 128000, 134400, 161280, 201600, 230400, 268800,
                    403200, 460800, 614400, 806400, 921600, 1228800, 2457600, 3000000, 6000000
            };
            for (int standardBaudRate : standardBaudRates) {
                if (standardBaudRate == baudRate) {
                    return baudRate;
                }
            }

            int baseline = 12000000 * 32;
            int mantissa = baseline / baudRate;
            if (mantissa == 0) {
                throw new UnsupportedOperationException("Baud rate too high");
            }
            int exponent = 0;
            int encoded;
            int effectiveBaudRate;
            if (deviceType == DeviceType.DEVICE_TYPE_T) {
                while (mantissa >= 2048) {
                    if (exponent < 15) {
                        mantissa >>= 1;
                        exponent++;
                    } else {
                        throw new UnsupportedOperationException("Baud rate too low");
                    }
                }
                encoded = mantissa + ((exponent & ~1) << 12) + ((exponent & 1) << 16) + (1 << 31);
                effectiveBaudRate = (baseline / mantissa) >> exponent;
            } else {
                while (mantissa >= 512) {
                    if (exponent < 7) {
                        mantissa >>= 2;
                        exponent++;
                    } else {
                        throw new UnsupportedOperationException("Baud rate too low");
                    }
                }
                encoded = mantissa + (exponent << 9) + (1 << 31);
                effectiveBaudRate = (baseline / mantissa) >> (exponent << 1);
            }
            double baudRateError = Math.abs(1.0 - (effectiveBaudRate / (double) baudRate));
            if (baudRateError >= 0.031) {
                throw new UnsupportedOperationException("Baud rate deviation too high");
            }
            return encoded;
        }

        @Override
        public void setParameters(int baudRate, int dataBits, int stopBits, @Parity int parity)
                throws IOException {
            int filteredBaudRate = filterBaudRate(baudRate);
            if (this.baudRate == filteredBaudRate
                    && this.dataBits == dataBits
                    && this.stopBits == stopBits
                    && this.parity == parity) {
                return;
            }

            byte[] lineRequestData = new byte[7];
            lineRequestData[0] = (byte) (filteredBaudRate & 0xff);
            lineRequestData[1] = (byte) ((filteredBaudRate >> 8) & 0xff);
            lineRequestData[2] = (byte) ((filteredBaudRate >> 16) & 0xff);
            lineRequestData[3] = (byte) ((filteredBaudRate >> 24) & 0xff);

            switch (stopBits) {
                case STOPBITS_1:
                    lineRequestData[4] = 0;
                    break;
                case STOPBITS_1_5:
                    lineRequestData[4] = 1;
                    break;
                case STOPBITS_2:
                    lineRequestData[4] = 2;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid stop bits: " + stopBits);
            }
            switch (parity) {
                case PARITY_NONE:
                    lineRequestData[5] = 0;
                    break;
                case PARITY_ODD:
                    lineRequestData[5] = 1;
                    break;
                case PARITY_EVEN:
                    lineRequestData[5] = 2;
                    break;
                case PARITY_MARK:
                    lineRequestData[5] = 3;
                    break;
                case PARITY_SPACE:
                    lineRequestData[5] = 4;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid parity: " + parity);
            }
            if (dataBits < DATABITS_5 || dataBits > DATABITS_8) {
                throw new IllegalArgumentException("Invalid data bits: " + dataBits);
            }
            lineRequestData[6] = (byte) dataBits;

            ctrlOut(SET_LINE_REQUEST, 0, 0, lineRequestData);
            resetDevice();

            this.baudRate = filteredBaudRate;
            this.dataBits = dataBits;
            this.stopBits = stopBits;
            this.parity = parity;
        }

        @Override
        public boolean getCD() throws IOException {
            return testStatusFlag(STATUS_FLAG_CD);
        }

        @Override
        public boolean getCTS() throws IOException {
            return testStatusFlag(STATUS_FLAG_CTS);
        }

        @Override
        public boolean getDSR() throws IOException {
            return testStatusFlag(STATUS_FLAG_DSR);
        }

        @Override
        public boolean getDTR() {
            return (controlLinesValue & CONTROL_DTR) != 0;
        }

        @Override
        public void setDTR(boolean value) throws IOException {
            int newControlLinesValue = value
                    ? controlLinesValue | CONTROL_DTR
                    : controlLinesValue & ~CONTROL_DTR;
            setControlLines(newControlLinesValue);
        }

        @Override
        public boolean getRI() throws IOException {
            return testStatusFlag(STATUS_FLAG_RI);
        }

        @Override
        public boolean getRTS() {
            return (controlLinesValue & CONTROL_RTS) != 0;
        }

        @Override
        public void setRTS(boolean value) throws IOException {
            int newControlLinesValue = value
                    ? controlLinesValue | CONTROL_RTS
                    : controlLinesValue & ~CONTROL_RTS;
            setControlLines(newControlLinesValue);
        }

        @Override
        public EnumSet<ControlLine> getControlLines() throws IOException {
            int status = getStatus();
            EnumSet<ControlLine> set = EnumSet.noneOf(ControlLine.class);
            if ((controlLinesValue & CONTROL_RTS) != 0) set.add(ControlLine.RTS);
            if ((status & STATUS_FLAG_CTS) != 0) set.add(ControlLine.CTS);
            if ((controlLinesValue & CONTROL_DTR) != 0) set.add(ControlLine.DTR);
            if ((status & STATUS_FLAG_DSR) != 0) set.add(ControlLine.DSR);
            if ((status & STATUS_FLAG_CD) != 0) set.add(ControlLine.CD);
            if ((status & STATUS_FLAG_RI) != 0) set.add(ControlLine.RI);
            return set;
        }

        @Override
        public EnumSet<ControlLine> getSupportedControlLines() {
            return EnumSet.allOf(ControlLine.class);
        }

        @Override
        public void purgeHwBuffers(boolean purgeWriteBuffers, boolean purgeReadBuffers) throws IOException {
            if (deviceType == DeviceType.DEVICE_TYPE_HXN) {
                int index = 0;
                if (purgeWriteBuffers) index |= RESET_HXN_RX_PIPE;
                if (purgeReadBuffers) index |= RESET_HXN_TX_PIPE;
                if (index != 0) {
                    vendorOut(RESET_HXN_REQUEST, index, null);
                }
            } else {
                if (purgeWriteBuffers) {
                    vendorOut(FLUSH_RX_REQUEST, 0, null);
                }
                if (purgeReadBuffers) {
                    vendorOut(FLUSH_TX_REQUEST, 0, null);
                }
            }
        }

        @Override
        public void setBreak(boolean value) throws IOException {
            ctrlOut(SEND_BREAK_REQUEST, value ? 0xffff : 0, 0, null);
        }
    }
}
