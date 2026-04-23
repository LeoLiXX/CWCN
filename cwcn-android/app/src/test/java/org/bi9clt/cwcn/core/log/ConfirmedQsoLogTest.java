package org.bi9clt.cwcn.core.log;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.bi9clt.cwcn.core.qso.QsoDraftSnapshot;
import org.bi9clt.cwcn.core.qso.QsoPhase;
import org.bi9clt.cwcn.core.qso.QsoStateEvent;

import org.junit.Test;

import java.util.Collections;

public final class ConfirmedQsoLogTest {
    @Test
    public void withDraftEditsPreservesIdentityAndUtcFieldsWhileUpdatingEditableFields() {
        ConfirmedQsoLog log = new ConfirmedQsoLog(
                42L,
                "BG7YOZ",
                "20260423",
                "101530",
                "CW",
                "599",
                "579",
                "OLD",
                "BEIJING",
                "BI9CLT",
                "completed",
                "OLD TEXT",
                true,
                9999L
        );

        QsoDraftSnapshot editedDraft = new QsoDraftSnapshot(
                QsoPhase.COMPLETED,
                "BI9CLT",
                "BG7YOZ/P",
                "5NN",
                "5TT",
                "LEO",
                "SHANGHAI",
                true,
                true,
                true,
                true,
                true,
                true,
                "NEW TEXT",
                Collections.singletonList("manual"),
                true,
                false,
                1234L,
                new QsoStateEvent(1234L, QsoPhase.COMPLETED, "edited")
        );

        ConfirmedQsoLog updated = log.withDraftEdits(editedDraft);

        assertEquals(42L, updated.id());
        assertEquals("20260423", updated.qsoDateUtc());
        assertEquals("101530", updated.timeOnUtc());
        assertEquals("CW", updated.mode());
        assertEquals(9999L, updated.confirmedAtEpochMs());
        assertEquals("BG7YOZ/P", updated.remoteCallsign());
        assertEquals("599", updated.rstSent());
        assertEquals("500", updated.rstRcvd());
        assertEquals("LEO", updated.name());
        assertEquals("SHANGHAI", updated.qth());
        assertEquals("BI9CLT", updated.stationCallsign());
        assertEquals("completed", updated.phase());
        assertEquals("NEW TEXT", updated.normalizedText());
        assertFalse(updated.needManualReview());
    }
}
