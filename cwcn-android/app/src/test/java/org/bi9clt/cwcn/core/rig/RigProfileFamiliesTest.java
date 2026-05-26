package org.bi9clt.cwcn.core.rig;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class RigProfileFamiliesTest {
    @Test
    public void detectsXieguProfilesByVendorAndModel() {
        assertTrue(RigProfileFamilies.isXieguFamily(RigProfileCatalog.findById("xiegu-x6100-serial")));
        assertTrue(RigProfileFamilies.isXieguFamily(RigProfileCatalog.findById("xiegu-x6200-serial")));
        assertTrue(RigProfileFamilies.isXieguFamily(RigProfileCatalog.findById("xiegu-g90-serial")));
    }

    @Test
    public void splitsXieguPortableAndG90Lines() {
        assertTrue(RigProfileFamilies.isXieguPortableUsbFamily(RigProfileCatalog.findById("xiegu-x6100-serial")));
        assertTrue(RigProfileFamilies.isXieguPortableUsbFamily(RigProfileCatalog.findById("xiegu-x6200-serial")));
        assertFalse(RigProfileFamilies.isXieguPortableUsbFamily(RigProfileCatalog.findById("xiegu-g90-serial")));

        assertFalse(RigProfileFamilies.isXieguG90Line(RigProfileCatalog.findById("xiegu-x6100-serial")));
        assertTrue(RigProfileFamilies.isXieguG90Line(RigProfileCatalog.findById("xiegu-g90-serial")));
    }

    @Test
    public void doesNotMisclassifyExistingFamiliesAsXiegu() {
        assertFalse(RigProfileFamilies.isXieguFamily(RigProfileCatalog.findById("yaesu-cat-serial-generic")));
        assertFalse(RigProfileFamilies.isXieguFamily(RigProfileCatalog.findById("icom-civ-serial-generic")));
        assertFalse(RigProfileFamilies.isXieguFamily(RigProfileCatalog.findById("kenwood-cat-serial-generic")));
    }
}
