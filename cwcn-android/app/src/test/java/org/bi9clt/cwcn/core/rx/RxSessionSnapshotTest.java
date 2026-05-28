package org.bi9clt.cwcn.core.rx;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class RxSessionSnapshotTest {
    @Test
    public void hasDeveloperFrontEndSummaryIsFalseForEmptyValue() {
        RxSessionSnapshot snapshot = new RxSessionSnapshot(
                1L,
                "mic",
                "active",
                true,
                700,
                700,
                700,
                20,
                20,
                "CQ",
                "CQ",
                "CQ",
                "BI3TUK",
                "",
                "",
                false,
                false,
                "",
                "",
                ""
        );

        assertFalse(snapshot.hasDeveloperFrontEndSummary());
    }

    @Test
    public void hasDeveloperFrontEndSummaryIsTrueForMeaningfulValue() {
        RxSessionSnapshot snapshot = new RxSessionSnapshot(
                1L,
                "mic",
                "active",
                true,
                700,
                700,
                700,
                20,
                20,
                "CQ",
                "CQ",
                "CQ",
                "BI3TUK",
                "",
                "",
                false,
                false,
                "",
                "",
                "tm hybrid | turn active"
        );

        assertTrue(snapshot.hasDeveloperFrontEndSummary());
    }

    @Test
    public void exposesCurrentTurnAndRawGatePresenceFlags() {
        RxSessionSnapshot emptySummarySnapshot = new RxSessionSnapshot(
                1L,
                "mic",
                "active",
                true,
                700,
                700,
                700,
                20,
                20,
                "CQ",
                "CQ",
                "CQ",
                "BI3TUK",
                "",
                "",
                false,
                false,
                "",
                "",
                ""
        );
        RxSessionSnapshot summarySnapshot = new RxSessionSnapshot(
                1L,
                "mic",
                "active",
                true,
                700,
                700,
                700,
                20,
                20,
                "CQ",
                "CQ",
                "CQ DE BI3TUK",
                "BI3TUK",
                "healthy",
                "",
                false,
                false,
                "Turn 4 active",
                "SQL open / tone stable",
                ""
        );

        assertTrue(emptySummarySnapshot.hasRawText());
        assertFalse(emptySummarySnapshot.hasCurrentTurnSummary());
        assertFalse(emptySummarySnapshot.hasRawGateSummary());
        assertTrue(summarySnapshot.hasCurrentTurnSummary());
        assertTrue(summarySnapshot.hasRawGateSummary());
    }

    @Test
    public void treatsBlankRawTextAsAbsent() {
        RxSessionSnapshot snapshot = new RxSessionSnapshot(
                1L,
                "mic",
                "active",
                true,
                700,
                700,
                700,
                20,
                20,
                "   ",
                "   ",
                "",
                "",
                "",
                "",
                false,
                false,
                "",
                "",
                ""
        );

        assertFalse(snapshot.hasRawText());
        assertFalse(snapshot.hasPreviewRawText());
    }

    @Test
    public void exposesNormalizedAndInputHintPresenceFlags() {
        RxSessionSnapshot snapshot = new RxSessionSnapshot(
                1L,
                "mic",
                "active",
                true,
                700,
                700,
                700,
                20,
                20,
                "CQ",
                "CQ",
                "CQ DE BI3TUK",
                "BI3TUK",
                "",
                "move mic away",
                false,
                false,
                "",
                "",
                ""
        );
        RxSessionSnapshot blankSnapshot = new RxSessionSnapshot(
                1L,
                "mic",
                "active",
                true,
                700,
                700,
                700,
                20,
                20,
                "CQ",
                "CQ",
                "CQ",
                "BI3TUK",
                "",
                "   ",
                false,
                false,
                "",
                "",
                ""
        );

        assertTrue(snapshot.hasNormalizedText());
        assertTrue(snapshot.hasDistinctNormalizedText());
        assertTrue(snapshot.hasInputHealthHint());
        assertTrue(blankSnapshot.hasNormalizedText());
        assertFalse(blankSnapshot.hasDistinctNormalizedText());
        assertFalse(blankSnapshot.hasInputHealthHint());
    }
}
