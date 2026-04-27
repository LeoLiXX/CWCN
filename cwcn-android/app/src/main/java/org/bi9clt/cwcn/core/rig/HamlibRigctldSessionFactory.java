package org.bi9clt.cwcn.core.rig;

import java.io.IOException;

public interface HamlibRigctldSessionFactory {
    HamlibRigctldSession open(String host, int port) throws IOException;
}
