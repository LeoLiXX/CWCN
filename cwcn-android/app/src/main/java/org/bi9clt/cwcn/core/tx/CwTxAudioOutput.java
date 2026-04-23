package org.bi9clt.cwcn.core.tx;

public interface CwTxAudioOutput {
    void playTone(int frequencyHz, int durationMs) throws InterruptedException;

    void playSilence(int durationMs) throws InterruptedException;

    void stop();
}
