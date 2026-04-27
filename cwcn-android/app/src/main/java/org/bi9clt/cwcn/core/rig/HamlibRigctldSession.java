package org.bi9clt.cwcn.core.rig;

import java.io.IOException;

public interface HamlibRigctldSession extends AutoCloseable {
    boolean setPtt(boolean enabled) throws IOException;

    boolean setKeySpeedWpm(int wpm) throws IOException;

    boolean setCwPitchHz(int toneFrequencyHz) throws IOException;

    boolean sendMorse(String morse) throws IOException;

    String getInfo() throws IOException;

    @Override
    void close() throws IOException;
}
