package org.bi9clt.cwcn.core.rig;

import android.app.PendingIntent;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class SerialCatRigControlAdapterTest {
    @Test
    public void describeAvailabilityIncludesFamilyAndTransportState() {
        RigProfile profile = RigProfileCatalog.findById("yaesu-cat-serial-generic");
        RigProfileSettings settings = new RigProfileSettings(
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
        );
        SerialCatRigControlAdapter adapter = new SerialCatRigControlAdapter(
                () -> new SerialCatRigControlAdapter.ActiveConfiguration(profile, settings),
                new FakeSerialCatSessionFactory("USB CDC/ACM serial CAT device is attached and permission is available.")
        );

        String availability = adapter.describeAvailability();

        assertTrue(availability.contains("Generic Yaesu Serial CAT"));
        assertTrue(availability.contains("Yaesu-style CAT"));
        assertTrue(availability.contains("permission is available"));
        assertTrue(availability.contains("probe-ready"));
    }

    @Test
    public void adapterIsReadyWhenSupportedFamilyAndTransportLooksUsable() {
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
        SerialCatRigControlAdapter adapter = new SerialCatRigControlAdapter(
                () -> new SerialCatRigControlAdapter.ActiveConfiguration(profile, settings),
                new FakeSerialCatSessionFactory("USB CDC/ACM serial CAT device is attached and permission is available.")
        );

        assertTrue(adapter.isReady());
    }

    @Test
    public void adapterIsNotReadyForUnsupportedCatFamily() {
        RigProfile profile = RigProfileCatalog.findById("generic-cat-serial");
        RigProfileSettings settings = new RigProfileSettings(
                18,
                650,
                SerialKeyerTxOutput.KeyLine.RTS,
                null,
                CatProtocolFamily.GENERIC,
                9600,
                null,
                null,
                CatProtocolFamily.HAMLIB_RIGCTLD,
                null,
                4532,
                null
        );
        SerialCatRigControlAdapter adapter = new SerialCatRigControlAdapter(
                () -> new SerialCatRigControlAdapter.ActiveConfiguration(profile, settings),
                new FakeSerialCatSessionFactory("USB CDC/ACM serial CAT device is attached and permission is available.")
        );

        assertFalse(adapter.isReady());
        assertTrue(adapter.describeAvailability().contains("not attached"));
    }

    private static final class FakeSerialCatSessionFactory implements SerialCatSessionFactory {
        private final String availability;

        private FakeSerialCatSessionFactory(String availability) {
            this.availability = availability;
        }

        @Override
        public String describeAvailability(String portHint) {
            return availability;
        }

        @Override
        public boolean requestPermission(String portHint, PendingIntent pendingIntent) {
            return true;
        }

        @Override
        public SerialCatSession openSession(String portHint, int baudRate) throws IOException {
            throw new IOException("not used");
        }
    }
}
