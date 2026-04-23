package org.bi9clt.cwcn.core.rig;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;
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

    @Test
    public void selectDeviceDelegatesToSelectableFactoryAndClosesCurrentPort() {
        RecordingSerialKeyerPort port = new RecordingSerialKeyerPort(true);
        SelectableRecordingPortFactory factory = new SelectableRecordingPortFactory(port);
        UsbSerialKeyerRigControlAdapter adapter = new UsbSerialKeyerRigControlAdapter(
                factory,
                SerialKeyerTxOutput.KeyLine.RTS,
                20,
                650
        );

        assertTrue(adapter.keyDown());
        assertTrue(adapter.selectDevice("usb-keyer-2"));

        assertEquals("usb-keyer-2", factory.preferredDeviceName());
        assertTrue(port.closed);
    }

    @Test
    public void selectDeviceSupportsAutoModeByPassingNull() {
        RecordingSerialKeyerPort port = new RecordingSerialKeyerPort(true);
        SelectableRecordingPortFactory factory = new SelectableRecordingPortFactory(port);
        UsbSerialKeyerRigControlAdapter adapter = new UsbSerialKeyerRigControlAdapter(
                factory,
                SerialKeyerTxOutput.KeyLine.RTS,
                20,
                650
        );

        assertTrue(adapter.selectDevice(null));

        assertNull(factory.preferredDeviceName());
    }

    @Test
    public void refreshRouteStateClosesOpenPort() {
        RecordingSerialKeyerPort port = new RecordingSerialKeyerPort(true);
        SelectableRecordingPortFactory factory = new SelectableRecordingPortFactory(port);
        UsbSerialKeyerRigControlAdapter adapter = new UsbSerialKeyerRigControlAdapter(
                factory,
                SerialKeyerTxOutput.KeyLine.RTS,
                20,
                650
        );

        assertTrue(adapter.keyDown());
        adapter.refreshRouteState();

        assertTrue(port.closed);
    }

    @Test
    public void diagnosticStageUsesDisconnectedPortCodeAfterOpenFailure() {
        UsbSerialKeyerRigControlAdapter adapter = new UsbSerialKeyerRigControlAdapter(
                new FixedSerialKeyerPortFactory(new DisconnectedSerialKeyerPort(
                        "usb-serial-open-failed",
                        "USB Serial Keyer Port",
                        "USB device permission exists, but opening the device failed."
                )),
                SerialKeyerTxOutput.KeyLine.RTS,
                20,
                650
        );

        assertFalse(adapter.sendText("E"));
        assertEquals("usb-serial-open-failed", adapter.diagnosticStageCode());
        assertEquals("Open failed", adapter.diagnosticStageLabel());
    }

    @Test
    public void diagnosticStageUsesFactoryAvailabilityWhenNoPortHasBeenOpened() {
        UsbSerialKeyerRigControlAdapter adapter = new UsbSerialKeyerRigControlAdapter(
                new DiagnosticOnlyPortFactory("usb-serial-no-permission"),
                SerialKeyerTxOutput.KeyLine.RTS,
                20,
                650
        );

        assertEquals("usb-serial-no-permission", adapter.diagnosticStageCode());
        assertEquals("Permission missing", adapter.diagnosticStageLabel());
    }

    private static final class RecordingSerialKeyerPort implements SerialKeyerPort {
        private final boolean open;
        private final List<String> events = new ArrayList<>();
        private boolean closed;

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
            return open && !closed;
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
            closed = true;
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

        @Override
        public String diagnosticStageCode() {
            return port.isOpen() ? "usb-serial-ready" : "usb-serial-unavailable";
        }
    }

    private static final class SelectableRecordingPortFactory implements SelectableSerialKeyerPortFactory {
        private final RecordingSerialKeyerPort port;
        private String preferredDeviceName;

        private SelectableRecordingPortFactory(RecordingSerialKeyerPort port) {
            this.port = port;
        }

        @Override
        public List<UsbSerialDeviceOption> availableDevices() {
            List<UsbSerialDeviceOption> devices = new ArrayList<>();
            devices.add(new UsbSerialDeviceOption("usb-keyer-1", 0x1234, 0x5678));
            devices.add(new UsbSerialDeviceOption("usb-keyer-2", 0x1111, 0x2222));
            return devices;
        }

        @Override
        public String preferredDeviceName() {
            return preferredDeviceName;
        }

        @Override
        public boolean selectDevice(String deviceName) {
            preferredDeviceName = deviceName;
            return true;
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

    private static final class DiagnosticOnlyPortFactory implements SerialKeyerPortFactory {
        private final String diagnosticCode;

        private DiagnosticOnlyPortFactory(String diagnosticCode) {
            this.diagnosticCode = diagnosticCode;
        }

        @Override
        public String describeAvailability() {
            return diagnosticCode;
        }

        @Override
        public boolean canOpenPort() {
            return false;
        }

        @Override
        public SerialKeyerPort openPort() {
            return new DisconnectedSerialKeyerPort(
                    diagnosticCode,
                    "USB Serial Keyer Port",
                    diagnosticCode
            );
        }

        @Override
        public String diagnosticStageCode() {
            return diagnosticCode;
        }
    }
}
