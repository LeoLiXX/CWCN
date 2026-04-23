package org.bi9clt.cwcn.core.tx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CwTxPresetTest {
    @Test
    public void generalCqPresetInjectsStationCallsign() {
        String rendered = CwTxPreset.GENERAL_CQ.render("bi9clt");

        assertEquals("CQ CQ CQ DE BI9CLT BI9CLT K", rendered);
    }

    @Test
    public void presetFallsBackToPlaceholderWhenStationCallsignMissing() {
        String rendered = CwTxPreset.TEST_CALL.render(" ");

        assertTrue(rendered.contains("MYCALL"));
    }
}
