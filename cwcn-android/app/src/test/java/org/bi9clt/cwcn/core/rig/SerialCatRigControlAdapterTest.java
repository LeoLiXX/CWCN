package org.bi9clt.cwcn.core.rig;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class SerialCatRigControlAdapterTest {
    @Test
    public void yaesuDedicatedDtrKeyingBypassesCatSession() {
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        FakeDedicatedKeyingPortFactory keyingFactory = new FakeDedicatedKeyingPortFactory();
        SerialCatRigControlAdapter adapter = new SerialCatRigControlAdapter(
                () -> new SerialCatRigControlAdapter.ActiveConfiguration(
                        RigProfileCatalog.findById("yaesu-cat-serial-generic"),
                        new RigProfileSettings(
                                18,
                                650,
                                SerialKeyerTxOutput.KeyLine.RTS,
                                null,
                                CatProtocolFamily.YAESU_STYLE,
                                38400,
                                "/dev/bus/usb/002/003#0",
                                SerialKeyerTxOutput.KeyLine.DTR,
                                "/dev/bus/usb/002/003#1",
                                null,
                                CatProtocolFamily.HAMLIB_RIGCTLD,
                                null,
                                4532,
                                null
                        )
                ),
                sessionFactory,
                keyingFactory
        );

        assertTrue(adapter.keyDown());
        assertTrue(adapter.keyUp());
        assertEquals(0, sessionFactory.openCount);
        assertEquals(1, keyingFactory.openCount);
        assertTrue(keyingFactory.port.dtrHighSeen);
        assertTrue(keyingFactory.port.dtrLowSeen);
        assertFalse(keyingFactory.port.rtsHighSeen);
    }

    @Test
    public void yaesuDedicatedRtsKeyingUsesRtsLine() {
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        FakeDedicatedKeyingPortFactory keyingFactory = new FakeDedicatedKeyingPortFactory();
        SerialCatRigControlAdapter adapter = new SerialCatRigControlAdapter(
                () -> new SerialCatRigControlAdapter.ActiveConfiguration(
                        RigProfileCatalog.findById("yaesu-cat-serial-generic"),
                        new RigProfileSettings(
                                18,
                                650,
                                SerialKeyerTxOutput.KeyLine.RTS,
                                null,
                                CatProtocolFamily.YAESU_STYLE,
                                38400,
                                "/dev/bus/usb/002/003#0",
                                SerialKeyerTxOutput.KeyLine.RTS,
                                "/dev/bus/usb/002/003#1",
                                null,
                                CatProtocolFamily.HAMLIB_RIGCTLD,
                                null,
                                4532,
                                null
                        )
                ),
                sessionFactory,
                keyingFactory
        );

        assertTrue(adapter.keyDown());
        assertTrue(adapter.keyUp());
        assertEquals(0, sessionFactory.openCount);
        assertTrue(keyingFactory.port.rtsHighSeen);
        assertTrue(keyingFactory.port.rtsLowSeen);
        assertFalse(keyingFactory.port.dtrHighSeen);
    }

    @Test
    public void dedicatedKeyingPulseUsesSelectedLineWithoutCatSession() {
        FakeDedicatedKeyingPortFactory keyingFactory = new FakeDedicatedKeyingPortFactory();

        SerialCatRigControlAdapter.ControlResult result = SerialCatRigControlAdapter.testDedicatedKeyingPulse(
                RigProfileCatalog.findById("yaesu-cat-serial-generic"),
                new RigProfileSettings(
                        18,
                        650,
                        SerialKeyerTxOutput.KeyLine.RTS,
                        null,
                        CatProtocolFamily.YAESU_STYLE,
                        38400,
                        "/dev/bus/usb/002/003#0",
                        SerialKeyerTxOutput.KeyLine.DTR,
                        "/dev/bus/usb/002/003#1",
                        null,
                        CatProtocolFamily.HAMLIB_RIGCTLD,
                        null,
                        4532,
                        null
                ),
                keyingFactory,
                50
        );

        assertTrue(result.success());
        assertEquals(1, keyingFactory.openCount);
        assertTrue(keyingFactory.port.dtrHighSeen);
        assertTrue(keyingFactory.port.dtrLowSeen);
    }

    @Test
    public void yaesuDedicatedDualLineKeyingCanAssertRtsAndDtrTogether() {
        FakeSessionFactory sessionFactory = new FakeSessionFactory();
        FakeDedicatedKeyingPortFactory keyingFactory = new FakeDedicatedKeyingPortFactory();
        SerialCatRigControlAdapter adapter = new SerialCatRigControlAdapter(
                () -> new SerialCatRigControlAdapter.ActiveConfiguration(
                        RigProfileCatalog.findById("yaesu-cat-serial-generic"),
                        new RigProfileSettings(
                                18,
                                650,
                                SerialKeyerTxOutput.KeyLine.RTS,
                                null,
                                CatProtocolFamily.YAESU_STYLE,
                                38400,
                                "/dev/bus/usb/002/003#0",
                                SerialKeyerTxOutput.KeyLine.RTS,
                                "/dev/bus/usb/002/003#1",
                                KeyingPolarity.ACTIVE_HIGH,
                                true,
                                true,
                                null,
                                CatProtocolFamily.HAMLIB_RIGCTLD,
                                null,
                                4532,
                                null
                        )
                ),
                sessionFactory,
                keyingFactory
        );

        assertTrue(adapter.keyDown());
        assertTrue(adapter.keyUp());
        assertEquals(0, sessionFactory.openCount);
        assertTrue(keyingFactory.port.rtsHighSeen);
        assertTrue(keyingFactory.port.rtsLowSeen);
        assertTrue(keyingFactory.port.dtrHighSeen);
        assertTrue(keyingFactory.port.dtrLowSeen);
    }

    @Test
    public void dedicatedTimingLabCanSequenceRtsThenDtrAndReleaseDtrFirst() {
        FakeDedicatedKeyingPortFactory keyingFactory = new FakeDedicatedKeyingPortFactory();

        SerialCatRigControlAdapter.ControlResult result = SerialCatRigControlAdapter.testDedicatedKeyingTimingLab(
                RigProfileCatalog.findById("yaesu-cat-serial-generic"),
                new RigProfileSettings(
                        18,
                        650,
                        SerialKeyerTxOutput.KeyLine.RTS,
                        null,
                        CatProtocolFamily.YAESU_STYLE,
                        38400,
                        "/dev/bus/usb/002/003#0",
                        SerialKeyerTxOutput.KeyLine.RTS,
                        "/dev/bus/usb/002/003#1",
                        KeyingPolarity.ACTIVE_HIGH,
                        true,
                        true,
                        null,
                        CatProtocolFamily.HAMLIB_RIGCTLD,
                        null,
                        4532,
                        null
                ),
                keyingFactory,
                new SerialCatRigControlAdapter.TimingLabPlan(
                        SerialCatRigControlAdapter.TimingLabOrder.RTS_THEN_DTR,
                        SerialCatRigControlAdapter.TimingLabReleaseOrder.RELEASE_DTR_FIRST,
                        0,
                        0,
                        0,
                        0
                )
        );

        assertTrue(result.success());
        assertEquals(
                List.of(
                        "RTS=false",
                        "DTR=false",
                        "RTS=true",
                        "DTR=true",
                        "DTR=false",
                        "RTS=false"
                ),
                keyingFactory.port.events
        );
    }

    @Test
    public void shortPulseLabSingleECanAddTailHoldAndReleaseGap() {
        FakeDedicatedKeyingPortFactory keyingFactory = new FakeDedicatedKeyingPortFactory();

        SerialCatRigControlAdapter.ControlResult result = SerialCatRigControlAdapter.testDedicatedKeyingShortPulseLab(
                RigProfileCatalog.findById("yaesu-cat-serial-generic"),
                new RigProfileSettings(
                        12,
                        650,
                        SerialKeyerTxOutput.KeyLine.RTS,
                        null,
                        CatProtocolFamily.YAESU_STYLE,
                        38400,
                        "/dev/bus/usb/002/003#0",
                        SerialKeyerTxOutput.KeyLine.RTS,
                        "/dev/bus/usb/002/003#1",
                        KeyingPolarity.ACTIVE_HIGH,
                        true,
                        false,
                        null,
                        CatProtocolFamily.HAMLIB_RIGCTLD,
                        null,
                        4532,
                        null
                ),
                keyingFactory,
                new SerialCatRigControlAdapter.ShortPulseLabPlan(
                        SerialCatRigControlAdapter.ShortPulseLabPreset.SINGLE_E,
                        12,
                        SerialCatRigControlAdapter.TimingLabOrder.SIMULTANEOUS,
                        SerialCatRigControlAdapter.TimingLabReleaseOrder.TOGETHER,
                        0,
                        10,
                        15,
                        0,
                        0
                )
        );

        assertTrue(result.success());
        assertEquals(
                List.of(
                        "RTS=false",
                        "RTS=true",
                        "RTS=false"
                ),
                keyingFactory.port.events
        );
    }

    private static final class FakeSessionFactory implements SerialCatSessionFactory {
        int openCount;

        @Override
        public String describeAvailability(String portHint) {
            return "permission is available";
        }

        @Override
        public boolean requestPermission(String portHint, android.app.PendingIntent pendingIntent) {
            return true;
        }

        @Override
        public SerialCatSession openSession(String portHint, int baudRate) {
            openCount += 1;
            return new SerialCatSession() {
                @Override
                public boolean isOpen() {
                    return true;
                }

                @Override
                public String describeAvailability() {
                    return "fake";
                }

                @Override
                public void send(byte[] command, int timeoutMs) {
                }

                @Override
                public byte[] transact(byte[] command, int timeoutMs) {
                    return new byte[0];
                }

                @Override
                public void close() {
                }
            };
        }
    }

    private static final class FakeDedicatedKeyingPortFactory implements DedicatedKeyingPortFactory {
        int openCount;
        final FakeSerialKeyerPort port = new FakeSerialKeyerPort();

        @Override
        public String describeAvailability(String portHint) {
            return "permission is available";
        }

        @Override
        public boolean canOpenPort(String portHint) {
            return true;
        }

        @Override
        public SerialKeyerPort openPort(String portHint) {
            openCount += 1;
            return port;
        }
    }

    private static final class FakeSerialKeyerPort implements SerialKeyerPort {
        boolean dtrHighSeen;
        boolean dtrLowSeen;
        boolean rtsHighSeen;
        boolean rtsLowSeen;
        final List<String> events = new ArrayList<>();

        @Override
        public String id() {
            return "fake";
        }

        @Override
        public String displayName() {
            return "fake";
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public String describeAvailability() {
            return "fake";
        }

        @Override
        public boolean setRts(boolean enabled) {
            events.add("RTS=" + enabled);
            if (enabled) {
                rtsHighSeen = true;
            } else {
                rtsLowSeen = true;
            }
            return true;
        }

        @Override
        public boolean setDtr(boolean enabled) {
            events.add("DTR=" + enabled);
            if (enabled) {
                dtrHighSeen = true;
            } else {
                dtrLowSeen = true;
            }
            return true;
        }

        @Override
        public void close() {
        }
    }
}
