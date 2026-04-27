package org.bi9clt.cwcn.core.rig;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;

import androidx.annotation.IntDef;

import java.io.Closeable;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.util.EnumSet;

interface UsbSerialPort extends Closeable {
    int DATABITS_5 = 5;
    int DATABITS_6 = 6;
    int DATABITS_7 = 7;
    int DATABITS_8 = 8;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({PARITY_NONE, PARITY_ODD, PARITY_EVEN, PARITY_MARK, PARITY_SPACE})
    @interface Parity {}

    int PARITY_NONE = 0;
    int PARITY_ODD = 1;
    int PARITY_EVEN = 2;
    int PARITY_MARK = 3;
    int PARITY_SPACE = 4;

    int STOPBITS_1 = 1;
    int STOPBITS_1_5 = 3;
    int STOPBITS_2 = 2;

    enum ControlLine { RTS, CTS, DTR, DSR, CD, RI }

    UsbSerialDriver getDriver();

    UsbDevice getDevice();

    int getPortNumber();

    UsbEndpoint getWriteEndpoint();

    UsbEndpoint getReadEndpoint();

    String getSerial();

    void open(UsbDeviceConnection connection) throws IOException;

    @Override
    void close() throws IOException;

    int read(byte[] dest, int timeout) throws IOException;

    void write(byte[] src, int timeout) throws IOException;

    void setParameters(int baudRate, int dataBits, int stopBits, @Parity int parity) throws IOException;

    boolean getCD() throws IOException;

    boolean getCTS() throws IOException;

    boolean getDSR() throws IOException;

    boolean getDTR() throws IOException;

    void setDTR(boolean value) throws IOException;

    boolean getRI() throws IOException;

    boolean getRTS() throws IOException;

    void setRTS(boolean value) throws IOException;

    EnumSet<ControlLine> getControlLines() throws IOException;

    EnumSet<ControlLine> getSupportedControlLines() throws IOException;

    void purgeHwBuffers(boolean purgeWriteBuffers, boolean purgeReadBuffers) throws IOException;

    void setBreak(boolean value) throws IOException;

    boolean isOpen();
}

interface UsbSerialDriver {
    UsbDevice getDevice();

    java.util.List<UsbSerialPort> getPorts();
}

final class SerialTimeoutException extends InterruptedIOException {
    SerialTimeoutException(String message) {
        super(message);
    }
}

final class MonotonicClock {
    private static final long NS_PER_MS = 1_000_000L;

    private MonotonicClock() {
    }

    static long millis() {
        return System.nanoTime() / NS_PER_MS;
    }
}

abstract class CommonUsbSerialPort implements UsbSerialPort {
    private static final int MAX_READ_SIZE = 16 * 1024;

    protected final UsbDevice device;
    protected final int portNumber;

    protected UsbDeviceConnection connection;
    protected UsbEndpoint readEndpoint;
    protected UsbEndpoint writeEndpoint;
    protected UsbRequest usbRequest;

    protected byte[] writeBuffer;
    protected final Object writeBufferLock = new Object();

    CommonUsbSerialPort(UsbDevice device, int portNumber) {
        this.device = device;
        this.portNumber = portNumber;
    }

    @Override
    public UsbDevice getDevice() {
        return device;
    }

    @Override
    public int getPortNumber() {
        return portNumber;
    }

    @Override
    public UsbEndpoint getWriteEndpoint() {
        return writeEndpoint;
    }

    @Override
    public UsbEndpoint getReadEndpoint() {
        return readEndpoint;
    }

    @Override
    public String getSerial() {
        return connection.getSerial();
    }

    @Override
    public void open(UsbDeviceConnection connection) throws IOException {
        if (this.connection != null) {
            throw new IOException("Already open");
        }
        if (connection == null) {
            throw new IllegalArgumentException("Connection is null");
        }
        this.connection = connection;
        try {
            openInt(connection);
            if (readEndpoint == null || writeEndpoint == null) {
                throw new IOException("Could not get read & write endpoints");
            }
            usbRequest = new UsbRequest();
            usbRequest.initialize(this.connection, readEndpoint);
        } catch (Exception exception) {
            try {
                close();
            } catch (Exception ignored) {
            }
            throw exception;
        }
    }

    protected abstract void openInt(UsbDeviceConnection connection) throws IOException;

    @Override
    public void close() throws IOException {
        if (connection == null) {
            throw new IOException("Already closed");
        }
        try {
            usbRequest.cancel();
        } catch (Exception ignored) {
        }
        usbRequest = null;
        try {
            closeInt();
        } catch (Exception ignored) {
        }
        try {
            connection.close();
        } catch (Exception ignored) {
        }
        connection = null;
    }

    protected abstract void closeInt();

    protected void testConnection() throws IOException {
        byte[] buffer = new byte[2];
        int length = connection.controlTransfer(0x80, 0, 0, 0, buffer, buffer.length, 200);
        if (length < 0) {
            throw new IOException("USB get_status request failed");
        }
    }

    @Override
    public int read(byte[] dest, int timeout) throws IOException {
        return read(dest, timeout, true);
    }

    private int read(byte[] dest, int timeout, boolean testConnection) throws IOException {
        if (connection == null) {
            throw new IOException("Connection closed");
        }
        if (dest.length <= 0) {
            throw new IllegalArgumentException("Read buffer too small");
        }
        final int readLength;
        if (timeout != 0) {
            long endTime = testConnection ? MonotonicClock.millis() + timeout : 0L;
            int readMax = Math.min(dest.length, MAX_READ_SIZE);
            readLength = connection.bulkTransfer(readEndpoint, dest, readMax, timeout);
            if (readLength == -1 && testConnection && MonotonicClock.millis() < endTime) {
                testConnection();
            }
        } else {
            ByteBuffer buffer = ByteBuffer.wrap(dest);
            if (!usbRequest.queue(buffer, dest.length)) {
                throw new IOException("Queueing USB request failed");
            }
            UsbRequest response = connection.requestWait();
            if (response == null) {
                throw new IOException("Waiting for USB request failed");
            }
            readLength = buffer.position();
            if (readLength == 0) {
                testConnection();
            }
        }
        return Math.max(readLength, 0);
    }

    @Override
    public void write(byte[] src, int timeout) throws IOException {
        if (connection == null) {
            throw new IOException("Connection closed");
        }
        int offset = 0;
        long endTime = timeout == 0 ? 0L : MonotonicClock.millis() + timeout;
        while (offset < src.length) {
            int requestLength;
            int requestTimeout;
            int actualLength;
            synchronized (writeBufferLock) {
                if (writeBuffer == null) {
                    writeBuffer = new byte[writeEndpoint.getMaxPacketSize()];
                }
                requestLength = Math.min(src.length - offset, writeBuffer.length);
                byte[] writeTarget;
                if (offset == 0) {
                    writeTarget = src;
                } else {
                    System.arraycopy(src, offset, writeBuffer, 0, requestLength);
                    writeTarget = writeBuffer;
                }
                if (timeout == 0 || offset == 0) {
                    requestTimeout = timeout;
                } else {
                    requestTimeout = (int) (endTime - MonotonicClock.millis());
                    if (requestTimeout == 0) {
                        requestTimeout = -1;
                    }
                }
                if (requestTimeout < 0) {
                    actualLength = -2;
                } else {
                    actualLength = connection.bulkTransfer(writeEndpoint, writeTarget, requestLength, requestTimeout);
                }
            }
            if (actualLength <= 0) {
                if (timeout != 0 && MonotonicClock.millis() >= endTime) {
                    SerialTimeoutException exception = new SerialTimeoutException(
                            "Error writing " + requestLength + " bytes at offset " + offset + " of total " + src.length
                    );
                    exception.bytesTransferred = offset;
                    throw exception;
                }
                throw new IOException(
                        "Error writing " + requestLength + " bytes at offset " + offset + " of total " + src.length
                );
            }
            offset += actualLength;
        }
    }

    @Override
    public boolean isOpen() {
        return connection != null;
    }

    @Override
    public boolean getCD() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getCTS() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getDSR() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getDTR() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setDTR(boolean value) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getRI() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getRTS() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setRTS(boolean value) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public EnumSet<ControlLine> getControlLines() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public EnumSet<ControlLine> getSupportedControlLines() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void purgeHwBuffers(boolean purgeWriteBuffers, boolean purgeReadBuffers) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setBreak(boolean value) throws IOException {
        throw new UnsupportedOperationException();
    }
}

final class UsbId {
    static final int VENDOR_FTDI = 0x0403;
    static final int FTDI_FT232R = 0x6001;
    static final int FTDI_ZERO = 0x0000;
    static final int FTDI_FT2232H = 0x6010;
    static final int FTDI_FT4232H = 0x6011;
    static final int FTDI_FT232H = 0x6014;
    static final int FTDI_FT231X = 0x6015;

    static final int VENDOR_PROLIFIC = 0x067b;
    static final int PROLIFIC_PL2303 = 0x2303;
    static final int PROLIFIC_PL2303GC = 0x23a3;
    static final int PROLIFIC_PL2303GB = 0x23b3;
    static final int PROLIFIC_PL2303GT = 0x23c3;
    static final int PROLIFIC_PL2303GL = 0x23d3;
    static final int PROLIFIC_PL2303GE = 0x23e3;
    static final int PROLIFIC_PL2303GS = 0x23f3;

    static final int VENDOR_ARDUINO = 0x2341;
    static final int ARDUINO_UNO = 0x0001;
    static final int ARDUINO_MEGA_2560 = 0x0010;
    static final int ARDUINO_SERIAL_ADAPTER = 0x003b;
    static final int ARDUINO_MEGA_ADK = 0x003f;
    static final int ARDUINO_MEGA_2560_R3 = 0x0042;
    static final int ARDUINO_UNO_R3 = 0x0043;
    static final int ARDUINO_MEGA_ADK_R3 = 0x0044;
    static final int ARDUINO_SERIAL_ADAPTER_R3 = 0x0044;
    static final int ARDUINO_LEONARDO = 0x8036;
    static final int ARDUINO_MICRO = 0x8037;

    static final int VENDOR_VAN_OOIJEN_TECH = 0x16c0;
    static final int VAN_OOIJEN_TECH_TEENSYDUINO_SERIAL = 0x0483;

    static final int VENDOR_ATMEL = 0x03EB;
    static final int ATMEL_LUFA_CDC_DEMO_APP = 0x2044;

    static final int VENDOR_LEAFLABS = 0x1eaf;
    static final int LEAFLABS_MAPLE = 0x0004;

    static final int VENDOR_SILABS = 0x10c4;
    static final int SILABS_CP2102 = 0xea60;
    static final int SILABS_CP2105 = 0xea70;
    static final int SILABS_CP2108 = 0xea71;

    static final int VENDOR_ARM = 0x0d28;
    static final int ARM_MBED = 0x0204;

    static final int VENDOR_ST = 0x0483;
    static final int ST_CDC = 0x5740;
    static final int ST_CDC2 = 0xA34C;
    static final int ST_CDC3 = 0x5732;
    static final int CDC_WOLF_PID = 0xF001;

    static final int VENDOR_RASPBERRY_PI = 0x2e8a;
    static final int RASPBERRY_PI_PICO_MICROPYTHON = 0x0005;

    static final int VENDOR_QINHENG = 0x1a86;
    static final int QINHENG_CH340 = 0x7523;
    static final int QINHENG_CH341A = 0x5523;
    static final int QINHENG_CH341K = 29986;
    static final int XIEGU_X6100 = 0x55D2;

    static final int VENDOR_ICOM = 0x0c26;
    static final int IC_R30 = 0x002b;
    static final int IC_705 = 0x0036;
    static final int ICOM_USB_SERIAL_CONTROL = 0x0018;

    private UsbId() {
        throw new IllegalAccessError("Non-instantiable class");
    }
}
