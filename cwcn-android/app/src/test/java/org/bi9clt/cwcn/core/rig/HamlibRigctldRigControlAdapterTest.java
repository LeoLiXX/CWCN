package org.bi9clt.cwcn.core.rig;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class HamlibRigctldRigControlAdapterTest {
    @Test
    public void adapterIsNotReadyWithoutHamlibNetworkConfiguration() {
        HamlibRigctldRigControlAdapter adapter = new HamlibRigctldRigControlAdapter(
                () -> null,
                (host, port) -> {
                    throw new AssertionError("Session should not be opened when configuration is missing.");
                },
                18,
                650
        );

        assertFalse(adapter.isReady());
        assertTrue(adapter.describeAvailability().contains("Rig Setup"));
    }

    @Test
    public void adapterSendsMorseThroughRigctldSession() {
        FakeHamlibSession session = new FakeHamlibSession();
        HamlibRigctldRigControlAdapter adapter = new HamlibRigctldRigControlAdapter(
                () -> new HamlibRigctldRigControlAdapter.ActiveConfiguration(
                        RigProfileCatalog.findById("hamlib-rigctld-network-generic"),
                        "192.168.1.8",
                        4532
                ),
                (host, port) -> {
                    assertEquals("192.168.1.8", host);
                    assertEquals(4532, port);
                    return session;
                },
                18,
                650
        );

        adapter.configureTextToCwProfile(22, 700);
        boolean sent = adapter.sendText("CQ TEST");

        assertTrue(sent);
        assertEquals(22, session.keySpeedWpm);
        assertEquals(700, session.cwPitchHz);
        assertTrue(session.sentMorse.contains("-.-."));
        assertTrue(session.closed);
    }

    @Test
    public void adapterCanTogglePttThroughRigctldSession() {
        FakeHamlibSession session = new FakeHamlibSession();
        HamlibRigctldRigControlAdapter adapter = new HamlibRigctldRigControlAdapter(
                () -> new HamlibRigctldRigControlAdapter.ActiveConfiguration(
                        RigProfileCatalog.findById("hamlib-rigctld-network-generic"),
                        "127.0.0.1",
                        4532
                ),
                (host, port) -> session,
                18,
                650
        );

        assertTrue(adapter.keyDown());
        assertTrue(session.pttEnabled);
        session.closed = false;
        assertTrue(adapter.keyUp());
        assertFalse(session.pttEnabled);
    }

    @Test
    public void probeConfigurationReturnsRigInfoWhenRigctldResponds() {
        FakeHamlibSession session = new FakeHamlibSession();
        session.info = "Hamlib Dummy Rig";

        HamlibRigctldRigControlAdapter.ProbeResult result =
                HamlibRigctldRigControlAdapter.probeConfiguration(
                        RigProfileCatalog.findById("hamlib-rigctld-network-generic"),
                        new RigProfileSettings(
                                18,
                                650,
                                SerialKeyerTxOutput.KeyLine.RTS,
                                null,
                                CatProtocolFamily.GENERIC,
                                9600,
                                null,
                                CatProtocolFamily.HAMLIB_RIGCTLD,
                                "127.0.0.1",
                                4532,
                                null
                        ),
                        (host, port) -> session
                );

        assertTrue(result.success());
        assertTrue(result.message().contains("Hamlib Dummy Rig"));
    }

    @Test
    public void yaesuProbeAddsShortBenchGuidance() {
        FakeHamlibSession session = new FakeHamlibSession();
        session.info = "Yaesu FT-710";

        HamlibRigctldRigControlAdapter.ProbeResult result =
                HamlibRigctldRigControlAdapter.probeConfiguration(
                        RigProfileCatalog.findById("yaesu-rigctld-network-family"),
                        new RigProfileSettings(
                                18,
                                650,
                                SerialKeyerTxOutput.KeyLine.RTS,
                                null,
                                CatProtocolFamily.YAESU_STYLE,
                                38400,
                                null,
                                CatProtocolFamily.HAMLIB_RIGCTLD,
                                "127.0.0.1",
                                4532,
                                null
                        ),
                        (host, port) -> session
                );

        assertTrue(result.success());
        assertTrue(result.message().contains("Yaesu note"));
        assertTrue(result.message().contains("short DIT or VVV"));
    }

    @Test
    public void icomProbeAddsShortBenchGuidance() {
        FakeHamlibSession session = new FakeHamlibSession();
        session.info = "Icom IC-7300";

        HamlibRigctldRigControlAdapter.ProbeResult result =
                HamlibRigctldRigControlAdapter.probeConfiguration(
                        RigProfileCatalog.findById("icom-rigctld-network-family"),
                        new RigProfileSettings(
                                18,
                                650,
                                SerialKeyerTxOutput.KeyLine.RTS,
                                null,
                                CatProtocolFamily.ICOM_CIV,
                                19200,
                                null,
                                CatProtocolFamily.HAMLIB_RIGCTLD,
                                "127.0.0.1",
                                4532,
                                null
                        ),
                        (host, port) -> session
                );

        assertTrue(result.success());
        assertTrue(result.message().contains("Icom note"));
        assertTrue(result.message().contains("short DIT or VVV"));
    }

    @Test
    public void probeConfigurationFailsForNonHamlibFamily() {
        HamlibRigctldRigControlAdapter.ProbeResult result =
                HamlibRigctldRigControlAdapter.probeConfiguration(
                        RigProfileCatalog.findById("generic-network-cat"),
                        new RigProfileSettings(
                                18,
                                650,
                                SerialKeyerTxOutput.KeyLine.RTS,
                                null,
                                CatProtocolFamily.GENERIC,
                                9600,
                                null,
                                CatProtocolFamily.GENERIC,
                                "127.0.0.1",
                                4532,
                                null
                        ),
                        (host, port) -> {
                            throw new AssertionError("Session should not open for non-Hamlib probe.");
                        }
                );

        assertFalse(result.success());
        assertTrue(result.message().contains("Hamlib rigctld"));
    }

    private static final class FakeHamlibSession implements HamlibRigctldSession {
        private int keySpeedWpm;
        private int cwPitchHz;
        private boolean pttEnabled;
        private String sentMorse;
        private String info;
        private boolean closed;

        @Override
        public boolean setPtt(boolean enabled) {
            this.pttEnabled = enabled;
            return true;
        }

        @Override
        public boolean setKeySpeedWpm(int wpm) {
            this.keySpeedWpm = wpm;
            return true;
        }

        @Override
        public boolean setCwPitchHz(int toneFrequencyHz) {
            this.cwPitchHz = toneFrequencyHz;
            return true;
        }

        @Override
        public boolean sendMorse(String morse) throws IOException {
            this.sentMorse = morse;
            return true;
        }

        @Override
        public String getInfo() {
            return info;
        }

        @Override
        public void close() {
            this.closed = true;
        }
    }
}
