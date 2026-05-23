package org.bi9clt.cwcn.core.rig;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;

import java.io.IOException;

final class AndroidUsbCdcAcmSerialCatSession implements SerialCatSession {
    private final UsbDevice device;
    private final UsbInterface controlInterface;
    private final UsbInterface dataInterface;
    private final UsbEndpoint inputEndpoint;
    private final UsbEndpoint outputEndpoint;
    private final UsbDeviceConnection connection;

    private boolean closed;

    AndroidUsbCdcAcmSerialCatSession(
            UsbDevice device,
            UsbInterface controlInterface,
            UsbInterface dataInterface,
            UsbEndpoint inputEndpoint,
            UsbEndpoint outputEndpoint,
            UsbDeviceConnection connection
    ) {
        this.device = device;
        this.controlInterface = controlInterface;
        this.dataInterface = dataInterface;
        this.inputEndpoint = inputEndpoint;
        this.outputEndpoint = outputEndpoint;
        this.connection = connection;
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    @Override
    public String describeAvailability() {
        return "USB CDC/ACM 串口 CAT 会话已打开：" + device.getDeviceName() + "。";
    }

    @Override
    public void send(byte[] command, int timeoutMs) throws IOException {
        if (closed) {
            throw new IOException("串口 CAT 会话已经关闭。");
        }
        if (command == null || command.length == 0) {
            throw new IOException("串口 CAT 指令为空。");
        }
        int written = connection.bulkTransfer(outputEndpoint, command, command.length, Math.max(500, timeoutMs));
        if (written < 0) {
            throw new IOException("串口 CAT 指令的 USB bulk OUT 发送失败。");
        }
    }

    @Override
    public byte[] transact(byte[] command, int timeoutMs) throws IOException {
        send(command, timeoutMs);
        byte[] collected = new byte[0];
        byte[] buffer = new byte[Math.max(64, inputEndpoint.getMaxPacketSize())];
        long deadlineMs = System.currentTimeMillis() + Math.max(500, timeoutMs);
        while (System.currentTimeMillis() < deadlineMs) {
            int chunkTimeoutMs = (int) Math.max(50, Math.min(250, deadlineMs - System.currentTimeMillis()));
            int read = connection.bulkTransfer(inputEndpoint, buffer, buffer.length, chunkTimeoutMs);
            if (read > 0) {
                byte[] next = new byte[collected.length + read];
                System.arraycopy(collected, 0, next, 0, collected.length);
                System.arraycopy(buffer, 0, next, collected.length, read);
                collected = next;
                if (containsTerminator(collected)) {
                    break;
                }
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
            connection.releaseInterface(dataInterface);
        } catch (Throwable ignored) {
        }
        try {
            connection.releaseInterface(controlInterface);
        } catch (Throwable ignored) {
        }
        connection.close();
    }

    private boolean containsTerminator(byte[] bytes) {
        for (byte value : bytes) {
            if (value == ';' || value == '\n' || value == '\r' || (value & 0xFF) == 0xFD) {
                return true;
            }
        }
        return false;
    }
}
