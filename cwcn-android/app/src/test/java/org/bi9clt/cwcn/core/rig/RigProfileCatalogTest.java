package org.bi9clt.cwcn.core.rig;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class RigProfileCatalogTest {
    @Test
    public void defaultProfilesExposeBenchReadyFamiliesFirst() {
        List<RigProfile> profiles = RigProfileCatalog.defaultProfiles();

        assertNotNull(profiles);
        assertTrue(profiles.size() >= 4);
        assertEquals("audio-vox-generic", profiles.get(0).id());
        assertEquals(RigSupportLevel.BENCH_READY, profiles.get(0).supportLevel());
        assertEquals("usb-serial-keyer-generic", profiles.get(1).id());
        assertEquals(RigSupportLevel.BENCH_READY, profiles.get(1).supportLevel());
    }

    @Test
    public void genericUsbSerialKeyerProfileCarriesExpectedCapabilities() {
        RigProfile profile = RigProfileCatalog.findById("usb-serial-keyer-generic");

        assertNotNull(profile);
        assertEquals(RigTransport.TransportKind.USB_SERIAL, profile.transportKind());
        assertTrue(profile.hasCapability(RigCapability.TEXT_TO_CW));
        assertTrue(profile.hasCapability(RigCapability.KEY_LINE_CONTROL));
        assertTrue(profile.hasCapability(RigCapability.USB_DEVICE_SELECTION));
        assertTrue(profile.capabilitySummary().contains("RTS/DTR key line"));
    }

    @Test
    public void plannedCatProfileStaysMarkedAsPlanned() {
        RigProfile profile = RigProfileCatalog.findById("generic-cat-serial");

        assertNotNull(profile);
        assertEquals(RigSupportLevel.PLANNED, profile.supportLevel());
        assertTrue(profile.hasCapability(RigCapability.SERIAL_CAT));
        assertTrue(profile.hasCapability(RigCapability.FREQUENCY_READ));
        assertTrue(profile.hasCapability(RigCapability.MODE_SET));
    }

    @Test
    public void concreteCatFamiliesAreExposedForLaterVendorSpecificWork() {
        RigProfile yaesu = RigProfileCatalog.findById("yaesu-cat-serial-generic");
        RigProfile yaesuRigctld = RigProfileCatalog.findById("yaesu-rigctld-network-family");
        RigProfile icomRigctld = RigProfileCatalog.findById("icom-rigctld-network-family");
        RigProfile icom = RigProfileCatalog.findById("icom-civ-serial-generic");
        RigProfile kenwoodRigctld = RigProfileCatalog.findById("kenwood-rigctld-network-family");
        RigProfile kenwood = RigProfileCatalog.findById("kenwood-cat-serial-generic");
        RigProfile rigctld = RigProfileCatalog.findById("hamlib-rigctld-network-generic");

        assertNotNull(yaesu);
        assertNotNull(yaesuRigctld);
        assertNotNull(icomRigctld);
        assertNotNull(icom);
        assertNotNull(kenwoodRigctld);
        assertNotNull(kenwood);
        assertNotNull(rigctld);
        assertEquals(RigTransport.TransportKind.USB_SERIAL, yaesu.transportKind());
        assertEquals(RigTransport.TransportKind.NETWORK_CAT, yaesuRigctld.transportKind());
        assertEquals(RigTransport.TransportKind.NETWORK_CAT, icomRigctld.transportKind());
        assertEquals(RigTransport.TransportKind.USB_SERIAL, icom.transportKind());
        assertEquals(RigTransport.TransportKind.NETWORK_CAT, kenwoodRigctld.transportKind());
        assertEquals(RigTransport.TransportKind.USB_SERIAL, kenwood.transportKind());
        assertEquals(RigTransport.TransportKind.NETWORK_CAT, rigctld.transportKind());
        assertTrue(icomRigctld.summary().contains("Icom-family"));
        assertTrue(icom.summary().contains("CI-V"));
        assertTrue(kenwoodRigctld.summary().contains("Kenwood-family"));
        assertTrue(yaesuRigctld.summary().contains("Yaesu-family"));
        assertTrue(rigctld.summary().contains("rigctld"));
    }

    @Test
    public void concreteCatFamiliesCarryRecommendedDefaultSettings() {
        RigProfile yaesu = RigProfileCatalog.findById("yaesu-cat-serial-generic");
        RigProfile yaesuRigctld = RigProfileCatalog.findById("yaesu-rigctld-network-family");
        RigProfile icomRigctld = RigProfileCatalog.findById("icom-rigctld-network-family");
        RigProfile icom = RigProfileCatalog.findById("icom-civ-serial-generic");
        RigProfile kenwoodRigctld = RigProfileCatalog.findById("kenwood-rigctld-network-family");
        RigProfile kenwood = RigProfileCatalog.findById("kenwood-cat-serial-generic");
        RigProfile rigctld = RigProfileCatalog.findById("hamlib-rigctld-network-generic");

        assertNotNull(yaesu);
        assertNotNull(yaesuRigctld);
        assertNotNull(icomRigctld);
        assertNotNull(icom);
        assertNotNull(kenwoodRigctld);
        assertNotNull(kenwood);
        assertNotNull(rigctld);
        assertEquals(CatProtocolFamily.YAESU_STYLE, yaesu.defaultSettings().serialCatProtocolFamily());
        assertEquals(38400, yaesu.defaultSettings().serialCatBaudRate());
        assertEquals(RigSupportLevel.BENCH_READY, yaesuRigctld.supportLevel());
        assertEquals("hamlib-rigctld", yaesuRigctld.adapterId());
        assertEquals(CatProtocolFamily.HAMLIB_RIGCTLD, yaesuRigctld.defaultSettings().networkCatProtocolFamily());
        assertEquals(RigSupportLevel.BENCH_READY, icomRigctld.supportLevel());
        assertEquals(CatProtocolFamily.HAMLIB_RIGCTLD, icomRigctld.defaultSettings().networkCatProtocolFamily());
        assertEquals(CatProtocolFamily.ICOM_CIV, icom.defaultSettings().serialCatProtocolFamily());
        assertEquals(19200, icom.defaultSettings().serialCatBaudRate());
        assertEquals(RigSupportLevel.BENCH_READY, kenwoodRigctld.supportLevel());
        assertEquals(CatProtocolFamily.HAMLIB_RIGCTLD, kenwoodRigctld.defaultSettings().networkCatProtocolFamily());
        assertEquals(CatProtocolFamily.KENWOOD_STYLE, kenwood.defaultSettings().serialCatProtocolFamily());
        assertEquals(57600, kenwood.defaultSettings().serialCatBaudRate());
        assertEquals(CatProtocolFamily.HAMLIB_RIGCTLD, rigctld.defaultSettings().networkCatProtocolFamily());
    }
}
