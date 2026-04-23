package org.bi9clt.cwcn.core.rig;

import android.app.PendingIntent;

import org.bi9clt.cwcn.core.tx.CwTxEngine;
import org.bi9clt.cwcn.core.tx.CwTxPlaybackSnapshot;
import org.bi9clt.cwcn.core.tx.CwTxPlan;
import org.bi9clt.cwcn.core.tx.CwTxRunner;
import org.bi9clt.cwcn.core.tx.CwTxState;

public final class UsbSerialKeyerRigControlAdapter implements RigControlAdapter {
    private static final int DEFAULT_WPM = 18;
    private static final int DEFAULT_TONE_FREQUENCY_HZ = 650;

    private final SerialKeyerPortFactory portFactory;
    private final CwTxEngine txEngine;

    private volatile SerialKeyerTxOutput.KeyLine keyLine;
    private volatile int wpm;
    private volatile int toneFrequencyHz;
    private volatile CwTxPlaybackSnapshot lastSnapshot;
    private volatile SerialKeyerPort openPort;
    private volatile CwTxRunner txRunner;

    UsbSerialKeyerRigControlAdapter(
            SerialKeyerPortFactory portFactory,
            SerialKeyerTxOutput.KeyLine keyLine,
            int wpm,
            int toneFrequencyHz
    ) {
        this.portFactory = portFactory;
        this.keyLine = keyLine;
        this.txEngine = new CwTxEngine();
        this.wpm = Math.max(5, wpm);
        this.toneFrequencyHz = Math.max(200, toneFrequencyHz);
    }

    @Override
    public String id() {
        return "usb-serial-keyer";
    }

    @Override
    public String displayName() {
        return "USB Serial Keyer Adapter";
    }

    @Override
    public String describeCapabilities() {
        return "Drive a simple serial keyer by toggling the " + keyLine + " control line according to CW timing.";
    }

    @Override
    public String describeAvailability() {
        SerialKeyerPort port = openPort;
        return port != null && port.isOpen()
                ? port.describeAvailability()
                : portFactory.describeAvailability();
    }

    @Override
    public boolean supportsTextToCw() {
        return true;
    }

    @Override
    public boolean supportsPttControl() {
        return false;
    }

    @Override
    public boolean isReady() {
        SerialKeyerPort port = openPort;
        return (port != null && port.isOpen()) || portFactory.canOpenPort();
    }

    @Override
    public boolean supportsConfigurableTextToCwProfile() {
        return true;
    }

    @Override
    public boolean configureTextToCwProfile(int wpm, int toneFrequencyHz) {
        this.wpm = Math.max(5, wpm);
        this.toneFrequencyHz = Math.max(200, toneFrequencyHz);
        return true;
    }

    @Override
    public boolean keyDown() {
        SerialKeyerPort port = ensureOpenPort();
        if (port == null || !port.isOpen()) {
            return false;
        }
        if (keyLine == SerialKeyerTxOutput.KeyLine.RTS) {
            return port.setRts(true);
        }
        return port.setDtr(true);
    }

    @Override
    public boolean keyUp() {
        if (txRunner != null) {
            txRunner.stop();
        }
        SerialKeyerPort port = openPort;
        if (port == null || !port.isOpen()) {
            return false;
        }
        if (keyLine == SerialKeyerTxOutput.KeyLine.RTS) {
            return port.setRts(false);
        }
        return port.setDtr(false);
    }

    @Override
    public boolean sendText(String text) {
        SerialKeyerPort port = ensureOpenPort();
        if (port == null || !port.isOpen()) {
            return false;
        }
        if (txRunner != null && txRunner.isRunning()) {
            return false;
        }
        CwTxPlan plan = txEngine.buildPlan(text, wpm, toneFrequencyHz);
        if (plan.elements().isEmpty()) {
            return false;
        }
        lastSnapshot = null;
        txRunner = new CwTxRunner(new SerialKeyerTxOutput(port, keyLine));
        txRunner.runPlanBlocking(plan, snapshot -> lastSnapshot = snapshot);
        return lastSnapshot != null && lastSnapshot.state() == CwTxState.COMPLETED;
    }

    CwTxPlaybackSnapshot lastSnapshot() {
        return lastSnapshot;
    }

    public SerialKeyerTxOutput.KeyLine keyLine() {
        return keyLine;
    }

    public boolean setKeyLine(SerialKeyerTxOutput.KeyLine keyLine) {
        if (keyLine == null) {
            return false;
        }
        CwTxRunner runner = txRunner;
        if (runner != null && runner.isRunning()) {
            return false;
        }
        keyUp();
        this.keyLine = keyLine;
        return true;
    }

    public String describeMatchedDevice() {
        if (portFactory instanceof AndroidUsbSerialKeyerPortFactory) {
            return ((AndroidUsbSerialKeyerPortFactory) portFactory).describeMatchedDevice();
        }
        return "USB device details are unavailable in this environment.";
    }

    public boolean requestUsbPermission(PendingIntent pendingIntent) {
        if (portFactory instanceof AndroidUsbSerialKeyerPortFactory) {
            return ((AndroidUsbSerialKeyerPortFactory) portFactory).requestPermission(pendingIntent);
        }
        return false;
    }

    private SerialKeyerPort ensureOpenPort() {
        SerialKeyerPort port = openPort;
        if (port != null && port.isOpen()) {
            return port;
        }
        SerialKeyerPort opened = portFactory.openPort();
        openPort = opened;
        return opened != null && opened.isOpen() ? opened : null;
    }
}
