package org.bi9clt.cwcn.core.qso;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class CwTxReportExtractorTest {
    @Test
    public void extractsReportAfterRstLabel() {
        assertEquals("579", CwTxReportExtractor.extractSentRst("UR RST 579 NAME LEO BK"));
    }

    @Test
    public void extractsReportAfterUrShortcut() {
        assertEquals("599", CwTxReportExtractor.extractSentRst("UR 5NN BK"));
    }

    @Test
    public void extractsReportFromLeadingStandaloneReport() {
        assertEquals("599", CwTxReportExtractor.extractSentRst("599 TU EE"));
    }

    @Test
    public void ignoresNonReportNumbers() {
        assertNull(CwTxReportExtractor.extractSentRst("PSE QSY 7030 BK"));
    }

    @Test
    public void returnsNullWhenNoReportPresent() {
        assertNull(CwTxReportExtractor.extractSentRst("TNX FER CALL BK"));
    }
}
