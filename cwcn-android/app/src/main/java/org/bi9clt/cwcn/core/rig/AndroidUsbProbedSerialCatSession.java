package org.bi9clt.cwcn.core.rig;

import android.hardware.usb.UsbDevice;

import java.io.IOException;

final class AndroidUsbProbedSerialCatSession implements SerialCatSession {
    private final UsbDevice device;
    private final UsbSerialPort port;
    private final String driverName;

    private boolean closed;

    AndroidUsbProbedSerialCatSession(UsbDevice device, UsbSerialPort port, String driverName) {
        this.device = device;
        this.port = port;
        this.driverName = driverName == null ? "USB 串口" : driverName;
    }

    @Override
    public boolean isOpen() {
        return !closed && port != null && port.isOpen();
    }

    @Override
    public String describeAvailability() {
        return driverName
                + " 串口 CAT 会话已打开："
                + device.getDeviceName()
                + " 端口 "
                + port.getPortNumber()
                + "。";
    }

    @Override
    public void send(byte[] command, int timeoutMs) throws IOException {
        if (!isOpen()) {
            throw new IOException("串口 CAT 会话已经关闭。");
        }
        if (command == null || command.length == 0) {
            throw new IOException("串口 CAT 指令为空。");
        }
        port.write(command, Math.max(500, timeoutMs));
    }

    @Override
    public byte[] transact(byte[] command, int timeoutMs) throws IOException {
        send(command, timeoutMs);
        byte[] collected = new byte[0];
        byte[] buffer = new byte[512];
        long deadlineMs = System.currentTimeMillis() + Math.max(500, timeoutMs);
        while (System.currentTimeMillis() < deadlineMs) {
            int chunkTimeoutMs = (int) Math.max(50, Math.min(250, deadlineMs - System.currentTimeMillis()));
            int read = port.read(buffer, chunkTimeoutMs);
            if (read <= 0) {
                continue;
            }
            byte[] next = new byte[collected.length + read];
            System.arraycopy(collected, 0, next, 0, collected.length);
            System.arraycopy(buffer, 0, next, collected.length, read);
            collected = next;
            if (containsTerminator(collected)) {
                break;
            }
        }
        return collected;
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
    }

    private boolean containsTerminator(byte[] bytes) {
        for (byte value : bytes) {
            int unsigned = value & 0xFF;
            if (value == ';' || value == '\n' || value == '\r' || unsigned == 0xFD) {
                return true;
            }
        }
        return false;
    }
}
