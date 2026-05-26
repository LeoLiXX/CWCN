package org.bi9clt.cwcn.core.rig;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class RigRouteStatusFormatterTest {
    @Test
    public void xieguSerialProfilesUseXieguCatLabel() {
        RigProfile profile = RigProfileCatalog.findById("xiegu-x6100-serial");

        assertEquals("Xiegu CAT", RigRouteStatusFormatter.describeCatRouteLabel(profile, profile.defaultSettings()));
    }

    @Test
    public void xieguSerialProfilesExposeMixedRxHint() {
        RigProfile profile = RigProfileCatalog.findById("xiegu-x6200-serial");

        assertTrue(RigRouteStatusFormatter.describeOperateRxHint(profile, false, true).contains("Xiegu"));
        assertTrue(RigRouteStatusFormatter.describeOperateRxHint(profile, false, true).contains("USB"));
    }

    @Test
    public void xieguReadyStatusUsesDedicatedFamilyCopy() {
        RigProfile profile = RigProfileCatalog.findById("xiegu-g90-serial");
        RigControlAdapter adapter = new SerialCatRigControlAdapter(
                () -> new SerialCatRigControlAdapter.ActiveConfiguration(
                        profile,
                        profile.defaultSettings()
                ),
                new ReadySessionFactory(),
                new UnusedDedicatedKeyingPortFactory()
        );
        String status = RigRouteStatusFormatter.describeOperateStatusMain(
                profile,
                false,
                true,
                adapter
        );

        assertEquals("Xiegu CAT 已就绪 | RX 待接入", status);
    }

    @Test
    public void xieguSettingsSummaryUsesDeviceAddressLabel() {
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

        String summary = RigRouteStatusFormatter.describeSettingsCatKeyingSummary(profile, settings);

        assertTrue(summary.contains("Xiegu 串口 CAT"));
        assertTrue(summary.contains("设备地址: A4"));
    }

    private static final class ReadySessionFactory implements SerialCatSessionFactory {
        @Override
        public PortAvailability availability(String portHint) {
            return new PortAvailability(PortAvailability.Stage.READY, "ready");
        }

        @Override
        public String describeAvailability(String portHint) {
            return "ready";
        }

        @Override
        public boolean requestPermission(String portHint, android.app.PendingIntent pendingIntent) {
            return true;
        }

        @Override
        public SerialCatSession openSession(String portHint, int baudRate) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class UnusedDedicatedKeyingPortFactory implements DedicatedKeyingPortFactory {
        @Override
        public PortAvailability availability(String portHint) {
            return new PortAvailability(PortAvailability.Stage.UNAVAILABLE, "unused");
        }

        @Override
        public SerialKeyerPort openPort(String portHint) {
            throw new UnsupportedOperationException();
        }
    }
}
