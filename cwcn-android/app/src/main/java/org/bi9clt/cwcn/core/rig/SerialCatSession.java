package org.bi9clt.cwcn.core.rig;

import java.io.IOException;

public interface SerialCatSession extends AutoCloseable {
    boolean isOpen();

    String describeAvailability();

    void send(byte[] command, int timeoutMs) throws IOException;

    default void send(String command, int timeoutMs) throws IOException {
        if (command == null) {
            throw new IOException("Serial CAT command is empty.");
        }
        send(command.getBytes(java.nio.charset.StandardCharsets.US_ASCII), timeoutMs);
    }

    byte[] transact(byte[] command, int timeoutMs) throws IOException;

    default String transact(String command, int timeoutMs) throws IOException {
        if (command == null) {
            return "";
        }
        byte[] response = transact(command.getBytes(java.nio.charset.StandardCharsets.US_ASCII), timeoutMs);
        return new String(response, java.nio.charset.StandardCharsets.US_ASCII).trim();
    }

    @Override
    void close();
}
