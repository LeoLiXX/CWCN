package org.bi9clt.cwcn.core.signal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class CwSignalSnapshotTest {
    @Test
    public void recentWindowRatiosReflectHistoryCodesAndOffsets() {
        CwSignalSnapshot snapshot = snapshot(
                6,
                new char[]{'L', 'L', 'u', '.', 'l', '.'},
                new int[]{0, 50, 20, 0, -5, 0}
        );

        assertEquals(3, snapshot.recentLockedFrameCount());
        assertEquals(2, snapshot.recentActiveLockedFrameCount());
        assertEquals(1, snapshot.recentQuietLockedFrameCount());
        assertEquals(1, snapshot.recentActiveUnlockedFrameCount());
        assertEquals(2, snapshot.recentSearchFrameCount());
        assertEquals(0.50d, snapshot.recentLockedFrameRatio(), 0.0001d);
        assertEquals(1.0d / 6.0d, snapshot.recentActiveUnlockedFrameRatio(), 0.0001d);
        assertEquals(2.0d / 6.0d, snapshot.recentSearchFrameRatio(), 0.0001d);
        assertEquals(2, snapshot.recentNearTargetLockedFrameCount());
        assertEquals(1, snapshot.recentFarOffTargetLockedFrameCount());
        assertEquals(2.0d / 3.0d, snapshot.recentNearTargetLockedFrameRatio(), 0.0001d);
        assertEquals(1.0d / 3.0d, snapshot.recentFarOffTargetLockedFrameRatio(), 0.0001d);
    }

    @Test
    public void recentWindowRatiosStayZeroWhenHistoryIsEmpty() {
        CwSignalSnapshot snapshot = snapshot(
                0,
                new char[0],
                new int[0]
        );

        assertEquals(0, snapshot.recentLockedFrameCount());
        assertEquals(0, snapshot.recentActiveUnlockedFrameCount());
        assertEquals(0, snapshot.recentSearchFrameCount());
        assertEquals(0.0d, snapshot.recentLockedFrameRatio(), 0.0001d);
        assertEquals(0.0d, snapshot.recentActiveUnlockedFrameRatio(), 0.0001d);
        assertEquals(0.0d, snapshot.recentSearchFrameRatio(), 0.0001d);
        assertEquals(0.0d, snapshot.recentNearTargetLockedFrameRatio(), 0.0001d);
        assertEquals(0.0d, snapshot.recentFarOffTargetLockedFrameRatio(), 0.0001d);
    }

    @Test
    public void effectiveDisplayFrequenciesPreferRepresentativeTrackedToneWhenTailFallsBack() {
        CwSignalSnapshot snapshot = new CwSignalSnapshot(
                6,
                new char[]{'L', 'L', 'L', 'u', '.', '.'},
                new int[]{0, 10, -10, 0, 0, 0},
                false,
                false,
                650,
                650,
                650,
                6,
                1200,
                800,
                600,
                1800,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                100,
                50,
                100,
                10,
                3,
                6,
                1,
                2,
                650,
                0,
                450,
                0,
                450,
                450,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                false,
                false,
                false,
                false,
                "PREFERRED_WINDOW",
                "SEARCH_FALLBACK",
                0,
                0,
                0,
                0L,
                0L,
                0L,
                -1L,
                null
        );

        assertEquals(650, snapshot.effectiveTrackedToneFrequencyHz());
        assertEquals(650, snapshot.effectiveAcquisitionWinnerFrequencyHz());
        assertEquals(650, snapshot.effectiveFinalAdoptedFrequencyHz());
    }

    private static CwSignalSnapshot snapshot(
            int recentHistoryFrameCount,
            char[] recentFrontEndStateHistory,
            int[] recentTrackingOffsetHistoryHz
    ) {
        return new CwSignalSnapshot(
                recentHistoryFrameCount,
                recentFrontEndStateHistory,
                recentTrackingOffsetHistoryHz,
                false,
                false,
                650,
                650,
                650,
                0,
                1200,
                800,
                600,
                1800,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                650,
                0,
                650,
                650,
                650,
                650,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                false,
                false,
                false,
                false,
                "NONE",
                "NONE",
                0,
                0,
                0,
                0L,
                0L,
                0L,
                -1L,
                null
        );
    }
}
