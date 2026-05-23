package org.bi9clt.cwcn.core.rig;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

final class SocketHamlibRigctldSession implements HamlibRigctldSession {
    private static final String RESPONSE_PREFIX = "RPRT ";

    private final Socket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;

    SocketHamlibRigctldSession(Socket socket) throws IOException {
        this.socket = socket;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));
    }

    @Override
    public boolean setPtt(boolean enabled) throws IOException {
        return executeStatusOnlyCommand("T " + (enabled ? "1" : "0"));
    }

    @Override
    public boolean setKeySpeedWpm(int wpm) throws IOException {
        return executeStatusOnlyCommand("L KEYSPD " + Math.max(5, wpm));
    }

    @Override
    public boolean setCwPitchHz(int toneFrequencyHz) throws IOException {
        return executeStatusOnlyCommand("L CWPITCH " + Math.max(200, toneFrequencyHz));
    }

    @Override
    public boolean sendMorse(String morse) throws IOException {
        if (morse == null || morse.trim().isEmpty()) {
            return false;
        }
        return executeStatusOnlyCommand("b " + morse.trim());
    }

    @Override
    public String getInfo() throws IOException {
        writer.write("_");
        writer.write('\n');
        writer.flush();

        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith(RESPONSE_PREFIX)) {
                if (parseRigctldStatus(line) != 0) {
                    return null;
                }
                String info = builder.toString().trim();
                return info.isEmpty() ? null : info;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(line);
        }
        throw new IOException("rigctld 在返回电台信息前关闭了连接。");
    }

    @Override
    public String transact(String command) throws IOException {
        if (command == null || command.trim().isEmpty()) {
            throw new IOException("rigctld 指令为空。");
        }
        writer.write(command.trim());
        writer.write('\n');
        writer.flush();

        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith(RESPONSE_PREFIX)) {
                if (parseRigctldStatus(line) != 0) {
                    return null;
                }
                String response = builder.toString().trim();
                return response.isEmpty() ? null : response;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(line);
        }
        throw new IOException("rigctld 在返回指令响应前关闭了连接：" + command);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    private boolean executeStatusOnlyCommand(String command) throws IOException {
        writer.write(command);
        writer.write('\n');
        writer.flush();

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith(RESPONSE_PREFIX)) {
                return parseRigctldStatus(line) == 0;
            }
        }
        throw new IOException("rigctld 在返回指令状态前关闭了连接：" + command);
    }

    private int parseRigctldStatus(String line) throws IOException {
        try {
            return Integer.parseInt(line.substring(RESPONSE_PREFIX.length()).trim());
        } catch (RuntimeException exception) {
            throw new IOException("收到异常的 rigctld 状态行：" + line, exception);
        }
    }
}
