package org.bi9clt.cwcn.core.rig;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public final class RigFrequencyResolverTest {
    @Test
    public void icomCivFrequencyResponseParsesToHz() {
        RigProfile profile = RigProfileCatalog.findById("icom-civ-serial-generic");
        RigProfileSettings settings = new RigProfileSettings(
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
        );
        RigFrequencyResolver resolver = new RigFrequencyResolver(
                new FixedSelectionSource(profile, settings),
                new FakeHamlibSessionFactory("0"),
                new FakeSerialCatSessionFactory(new byte[] {
                        (byte) 0xFE, (byte) 0xFE, (byte) 0xE0, (byte) 0x94, 0x03,
                        0x00, 0x40, 0x07, 0x14, (byte) 0xFD
                }, "")
        );

        assertEquals(14_074_000L, resolver.readCurrentFrequencyHz());
    }

    @Test
    public void kenwoodAsciiFrequencyFallsBackToIf() {
        RigProfile profile = RigProfileCatalog.findById("kenwood-cat-serial-generic");
        RigProfileSettings settings = new RigProfileSettings(
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
        );
        RigFrequencyResolver resolver = new RigFrequencyResolver(
                new FixedSelectionSource(profile, settings),
                new FakeHamlibSessionFactory("0"),
                new FakeSerialCatSessionFactory(new byte[0], "IF00014074000;")
        );

        assertEquals(14_074_000L, resolver.readCurrentFrequencyHz());
    }

    @Test
    public void xieguProfilesReuseCivFrequencyPathInPhaseOne() {
        RigProfile profile = RigProfileCatalog.findById("xiegu-x6100-serial");
        RigProfileSettings settings = new RigProfileSettings(
                18,
                650,
                SerialKeyerTxOutput.KeyLine.RTS,
                null,
                CatProtocolFamily.ICOM_CIV,
                19200,
                "USB3",
                "A4",
                CatProtocolFamily.HAMLIB_RIGCTLD,
                null,
                4532,
                null
        );
        RigFrequencyResolver resolver = new RigFrequencyResolver(
                new FixedSelectionSource(profile, settings),
                new FakeHamlibSessionFactory("0"),
                new FakeSerialCatSessionFactory(new byte[] {
                        (byte) 0xFE, (byte) 0xFE, (byte) 0xE0, (byte) 0xA4, 0x03,
                        0x00, 0x40, 0x07, 0x14, (byte) 0xFD
                }, "")
        );

        assertEquals(14_074_000L, resolver.readCurrentFrequencyHz());
    }

    private static final class FixedSelectionSource implements RigFrequencyResolver.SelectionSource {
        private final RigProfile profile;
        private final RigProfileSettings settings;

        private FixedSelectionSource(RigProfile profile, RigProfileSettings settings) {
            this.profile = profile;
            this.settings = settings;
        }

        @Override
        public RigProfile selectedProfile() {
            return profile;
        }

        @Override
        public RigProfileSettings loadSettings(RigProfile profile) {
            return settings;
        }
    }

    private static final class FakeHamlibSessionFactory implements HamlibRigctldSessionFactory {
        private final String response;

        private FakeHamlibSessionFactory(String response) {
            this.response = response;
        }

        @Override
        public HamlibRigctldSession open(String host, int port) {
            return new HamlibRigctldSession() {
                @Override
                public boolean setPtt(boolean enabled) {
                    return false;
                }

                @Override
                public boolean setKeySpeedWpm(int wpm) {
                    return false;
                }

                @Override
                public boolean setCwPitchHz(int toneFrequencyHz) {
                    return false;
                }

                @Override
                public boolean sendMorse(String morse) {
                    return false;
                }

                @Override
                public String getInfo() {
                    return "";
                }

                @Override
                public String transact(String command) {
                    return response;
                }

                @Override
                public void close() {
                }
            };
        }
    }

    private static final class FakeSerialCatSessionFactory implements SerialCatSessionFactory {
        private final byte[] binaryResponse;
        private final String asciiResponse;

        private FakeSerialCatSessionFactory(byte[] binaryResponse, String asciiResponse) {
            this.binaryResponse = binaryResponse;
            this.asciiResponse = asciiResponse;
        }

        @Override
        public PortAvailability availability(String portHint) {
            return new PortAvailability(PortAvailability.Stage.READY, "fake");
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
                    return "fake";
                }

                @Override
                public void send(byte[] command, int timeoutMs) {
                }

                @Override
                public byte[] transact(byte[] command, int timeoutMs) throws IOException {
                    String asciiCommand = new String(command, java.nio.charset.StandardCharsets.US_ASCII);
                    if ("FA;".equals(asciiCommand)) {
                        return new byte[0];
                    }
                    if ("IF;".equals(asciiCommand)) {
                        return asciiResponse.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                    }
                    return binaryResponse;
                }

                @Override
                public void close() {
                }
            };
        }
    }
}
