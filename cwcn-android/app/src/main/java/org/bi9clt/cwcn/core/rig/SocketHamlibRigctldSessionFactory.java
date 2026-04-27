package org.bi9clt.cwcn.core.rig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

final class SocketHamlibRigctldSessionFactory implements HamlibRigctldSessionFactory {
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 5000;

    @Override
    public HamlibRigctldSession open(String host, int port) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        socket.setSoTimeout(READ_TIMEOUT_MS);
        return new SocketHamlibRigctldSession(socket);
    }
}
