package org.bi9clt.cwcn.core.rig;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class UsbSerialKeyerRigControlAdapterTest {
    @Test
    public void sendTextTogglesConfiguredKeyLine() {
        RecordingSerialKeyerPort port = new RecordingSerialKeyerPort(true);
        UsbSerialKeyerRigControlAdapter adapter = new UsbSerialKeyerRigControlAdapter(
                new FixedSerialKeyerPortFactory(port),
                SerialKeyerTxOutput.KeyLine.RTS,
                20,
                650
        );

        boolean sent = adapter.sendText("EE");

        assertTrue(sent);
        assertTrue(port.events.contains("RTS:true"));
        assertTrue(port.events.contains("RTS:false"));
    }

    @Test
    public void sendTextFailsWhenPortNotOpen() {
        RecordingSerialKeyerPort port = new RecordingSerialKeyerPort(false);
        UsbSerialKeyerRigControlAdapter adapter = new UsbSerialKeyerRigControlAdapter(
                new FixedSerialKeyerPortFactory(port),
                SerialKeyerTxOutput.KeyLine.DTR,
                20,
                650
        );

        boolean sent = adapter.sendText("EE");

        assertFalse(sent);
        assertTrue(port.events.isEmpty());
    }

    @Test
    public void configuredProfileIsAcceptedEvenThoughToneIsNotUsedByKeyLine() {
        RecordingSerialKeyerPort port = new RecordingSerialKeyerPort(true);
        UsbSerialKeyerRigControlAdapter adapter = new UsbSerialKeyerRigControlAdapter(
                new FixedSerialKeyerPortFactory(port),
                SerialKeyerTxOutput.KeyLine.DTR,
                20,
                650
        );

        adapter.configureTextToCwProfile(10, 900);
        boolean sent = adapter.sendText("E");

        assertTrue(sent);
        assertTrue(port.events.contains("DTR:true"));
        assertTrue(port.events.contains("DTR:false"));
    }

    private static final class RecordingSerialKeyerPort implements SerialKeyerPort {
        private final boolean open;
        private final List<String> events = new ArrayList<>();

        private RecordingSerialKeyerPort(boolean open) {
            this.open = open;
        }

        @Override
        public String id() {
            return "recording-port";
        }

        @Override
        public String displayName() {
            return "Recording Port";
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public String describeAvailability() {
            return open ? "Open" : "Closed";
        }

        @Override
        public boolean setRts(boolean enabled) {
            if (!open) {
                return false;
            }
            events.add("RTS:" + enabled);
            return true;
        }

        @Override
        public boolean setDtr(boolean enabled) {
            if (!open) {
                return false;
            }
            events.add("DTR:" + enabled);
            return true;
        }

        @Override
        public void close() {
        }
    }

    private static final class FixedSerialKeyerPortFactory implements SerialKeyerPortFactory {
        private final SerialKeyerPort port;

        private FixedSerialKeyerPortFactory(SerialKeyerPort port) {
            this.port = port;
        }

        @Override
        public String describeAvailability() {
            return port.describeAvailability();
        }

        @Override
        public boolean canOpenPort() {
            return port.isOpen();
        }

        @Override
        public SerialKeyerPort openPort() {
            return port;
        }
    }
}
