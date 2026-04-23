package org.bi9clt.cwcn.core.rig;

import org.bi9clt.cwcn.core.tx.CwTxAudioOutput;

public final class SerialKeyerTxOutput implements CwTxAudioOutput {
    public enum KeyLine {
        RTS,
        DTR
    }

    private final SerialKeyerPort port;
    private final KeyLine keyLine;

    SerialKeyerTxOutput(SerialKeyerPort port, KeyLine keyLine) {
        this.port = port;
        this.keyLine = keyLine;
    }

    @Override
    public void playTone(int frequencyHz, int durationMs) throws InterruptedException {
        if (!setLine(true)) {
            throw new IllegalStateException("Unable to assert " + keyLine + " on serial keyer port.");
        }
        sleepInterruptibly(durationMs);
    }

    @Override
    public void playSilence(int durationMs) throws InterruptedException {
        if (!setLine(false)) {
            throw new IllegalStateException("Unable to release " + keyLine + " on serial keyer port.");
        }
        sleepInterruptibly(durationMs);
    }

    @Override
    public void stop() {
        setLine(false);
    }

    private boolean setLine(boolean enabled) {
        if (keyLine == KeyLine.RTS) {
            return port.setRts(enabled);
        }
        return port.setDtr(enabled);
    }

    private void sleepInterruptibly(int durationMs) throws InterruptedException {
        int remainingMs = durationMs;
        while (remainingMs > 0) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Serial keyer timing interrupted");
            }
            int chunkMs = Math.min(20, remainingMs);
            Thread.sleep(chunkMs);
            remainingMs -= chunkMs;
        }
    }
}
