package org.bi9clt.cwcn.core.rig;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class SerialCatProbeTest {
    @Test
    public void usbSerialDriverDiscoveryMethodsStayPublicForReflection() throws Exception {
        assertTrue(java.lang.reflect.Modifier.isPublic(
                CdcAcmSerialDriver.class.getMethod("getSupportedDevices").getModifiers()
        ));
        assertTrue(java.lang.reflect.Modifier.isPublic(
                Cp21xxSerialDriver.class.getMethod("getSupportedDevices").getModifiers()
        ));
        assertTrue(java.lang.reflect.Modifier.isPublic(
                FtdiSerialDriver.class.getMethod("getSupportedDevices").getModifiers()
        ));
        assertTrue(java.lang.reflect.Modifier.isPublic(
                ProlificSerialDriver.class.getMethod("getSupportedDevices").getModifiers()
        ));
        assertTrue(java.lang.reflect.Modifier.isPublic(
                Ch34xSerialDriver.class.getMethod("getSupportedDevices").getModifiers()
        ));
    }

    @Test
    public void yaesuProbeReturnsSuccessWhenFaResponds() {
        SerialCatProbe.ProbeResult result = SerialCatProbe.probeConfiguration(
                RigProfileCatalog.findById("yaesu-cat-serial-generic"),
                new RigProfileSettings(
                        18,
                        650,
                        SerialKeyerTxOutput.KeyLine.RTS,
                        null,
                        CatProtocolFamily.YAESU_STYLE,
                        38400,
                        "USB0",
                        null,
                        CatProtocolFamily.HAMLIB_RIGCTLD,
                        null,
                        4532,
                        null
                ),
                new FakeSerialCatSessionFactory("FA00014074000;".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
        );

        assertTrue(result.success());
        assertTrue(result.message().contains("Serial CAT responded"));
    }

    @Test
    public void icomProbeReturnsSuccessWhenCivResponds() {
        SerialCatProbe.ProbeResult result = SerialCatProbe.probeConfiguration(
                RigProfileCatalog.findById("icom-civ-serial-generic"),
                new RigProfileSettings(
                        18,
                        650,
                        SerialKeyerTxOutput.KeyLine.RTS,
                        null,
                        CatProtocolFamily.ICOM_CIV,
                        19200,
                        "USB1",
                        "94",
                        CatProtocolFamily.HAMLIB_RIGCTLD,
                        null,
                        4532,
                        null
                ),
                new FakeSerialCatSessionFactory(new byte[] {
                        (byte) 0xFE, (byte) 0xFE, (byte) 0xE0, (byte) 0x94, 0x19, 0x00, 0x70, (byte) 0xFD
                })
        );

        assertTrue(result.success());
        assertTrue(result.message().contains("CI-V responded"));
        assertTrue(result.message().contains("FE FE E0 94 19 00 70 FD"));
    }

    @Test
    public void probeSurfacesNoResponseClearly() {
        SerialCatProbe.ProbeResult result = SerialCatProbe.probeConfiguration(
                RigProfileCatalog.findById("yaesu-cat-serial-generic"),
                new RigProfileSettings(
                        18,
                        650,
                        SerialKeyerTxOutput.KeyLine.RTS,
                        null,
                        CatProtocolFamily.YAESU_STYLE,
                        38400,
                        "USB0",
                        null,
                        CatProtocolFamily.HAMLIB_RIGCTLD,
                        null,
                        4532,
                        null
                ),
                new FakeSerialCatSessionFactory(new byte[0])
        );

        assertFalse(result.success());
        assertTrue(result.message().contains("returned no readable response"));
    }

    @Test
    public void icomProbeRequiresCivAddress() {
        SerialCatProbe.ProbeResult result = SerialCatProbe.probeConfiguration(
                RigProfileCatalog.findById("icom-civ-serial-generic"),
                new RigProfileSettings(
                        18,
                        650,
                        SerialKeyerTxOutput.KeyLine.RTS,
                        null,
                        CatProtocolFamily.ICOM_CIV,
                        19200,
                        "USB1",
                        null,
                        CatProtocolFamily.HAMLIB_RIGCTLD,
                        null,
                        4532,
                        null
                ),
                new FakeSerialCatSessionFactory(new byte[] { 0x00 })
        );

        assertFalse(result.success());
        assertTrue(result.message().contains("CI-V address"));
    }

    @Test
    public void kenwoodProbeReturnsSuccessWhenIdResponds() {
        SerialCatProbe.ProbeResult result = SerialCatProbe.probeConfiguration(
                RigProfileCatalog.findById("kenwood-cat-serial-generic"),
                new RigProfileSettings(
                        18,
                        650,
                        SerialKeyerTxOutput.KeyLine.RTS,
                        null,
                        CatProtocolFamily.KENWOOD_STYLE,
                        57600,
                        "USB2",
                        null,
                        CatProtocolFamily.HAMLIB_RIGCTLD,
                        null,
                        4532,
                        null
                ),
                new FakeSerialCatSessionFactory("ID019;".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
        );

        assertTrue(result.success());
        assertTrue(result.message().contains("Kenwood-style CAT responded"));
    }

    @Test
    public void probeReturnsFailureWhenSessionFactoryThrowsRuntime() {
        SerialCatProbe.ProbeResult result = SerialCatProbe.probeConfiguration(
                RigProfileCatalog.findById("yaesu-cat-serial-generic"),
                new RigProfileSettings(
                        18,
                        650,
                        SerialKeyerTxOutput.KeyLine.RTS,
                        null,
                        CatProtocolFamily.YAESU_STYLE,
                        38400,
                        "USB0",
                        null,
                        CatProtocolFamily.HAMLIB_RIGCTLD,
                        null,
                        4532,
                        null
                ),
                new SerialCatSessionFactory() {
                    @Override
                    public String describeAvailability(String portHint) {
                        return "fake";
                    }

                    @Override
                    public boolean requestPermission(String portHint, android.app.PendingIntent pendingIntent) {
                        return true;
                    }

                    @Override
                    public SerialCatSession openSession(String portHint, int baudRate) {
                        throw new RuntimeException("boom");
                    }
                }
        );

        assertFalse(result.success());
        assertTrue(result.message().contains("boom"));
    }

    private static final class FakeSerialCatSessionFactory implements SerialCatSessionFactory {
        private final byte[] response;

        private FakeSerialCatSessionFactory(byte[] response) {
            this.response = response;
        }

        @Override
        public String describeAvailability(String portHint) {
            return "fake";
        }

        @Override
        public boolean requestPermission(String portHint, android.app.PendingIntent pendingIntent) {
            return true;
        }

        @Override
        public SerialCatSession openSession(String portHint, int baudRate) {
            return new SerialCatSession() {
                @Override
                public boolean isOpen() {
                    return true;
                }

                @Override
                public String describeAvailability() {
                    return "fake session";
                }

                @Override
                public void send(byte[] command, int timeoutMs) {
                }

                @Override
                public byte[] transact(byte[] command, int timeoutMs) throws IOException {
                    return response;
                }

                @Override
                public void close() {
                }
            };
        }
    }
}
