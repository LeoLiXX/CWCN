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
                "",
                "",
                "CQ",
                "BI3TUK",
                "",
                "",
                false,
                false,
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
                "fallback",
                "notes",
                "CQ",
                "BI3TUK",
                "",
                "",
                false,
                false,
                "tm hybrid | turn active"
        );

        assertTrue(snapshot.hasDeveloperFrontEndSummary());
    }

    @Test
    public void exposesFallbackPresenceFlags() {
        RxSessionSnapshot emptyFallbackSnapshot = new RxSessionSnapshot(
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
                "",
                "",
                "CQ",
                "BI3TUK",
                "",
                "",
                false,
                false,
                ""
        );
        RxSessionSnapshot fallbackSnapshot = new RxSessionSnapshot(
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
                "DE BI3TUK",
                "CQ? / DE?",
                "CQ",
                "BI3TUK",
                "",
                "",
                false,
                false,
                ""
        );

        assertFalse(emptyFallbackSnapshot.hasFallbackSuggestedText());
        assertFalse(emptyFallbackSnapshot.hasFallbackNotesText());
        assertTrue(emptyFallbackSnapshot.hasRawText());
        assertFalse(emptyFallbackSnapshot.hasDistinctFallbackSuggestedText());
        assertTrue(fallbackSnapshot.hasFallbackSuggestedText());
        assertTrue(fallbackSnapshot.hasFallbackNotesText());
        assertTrue(fallbackSnapshot.hasDistinctFallbackSuggestedText());
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
                "",
                "",
                "",
                "",
                "",
                "",
                false,
                false,
                ""
        );

        assertFalse(snapshot.hasRawText());
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
                "",
                "",
                "CQ DE BI3TUK",
                "BI3TUK",
                "",
                "move mic away",
                false,
                false,
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
                "",
                "",
                "CQ",
                "BI3TUK",
                "",
                "   ",
                false,
                false,
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
