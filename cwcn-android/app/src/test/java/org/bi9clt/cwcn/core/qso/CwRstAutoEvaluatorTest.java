package org.bi9clt.cwcn.core.qso;

import org.bi9clt.cwcn.core.rx.RxSessionSnapshot;
import org.bi9clt.cwcn.core.spectrum.SpectrumSnapshotData;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CwRstAutoEvaluatorTest {
    @Test
    public void emptySessionDoesNotProduceSuggestion() {
        CwRstAutoEvaluator.Assessment assessment = CwRstAutoEvaluator.evaluateSentSuggestion(
                session(false, "", "", "", ""),
                spectrum(0, 0, 0, 0, 0)
        );

        assertFalse(assessment.hasSuggestion());
        assertEquals(CwRstAutoEvaluator.Source.NONE, assessment.source());
        assertEquals(0, assessment.confidence());
    }

    @Test
    public void strongReadableSessionSuggests599() {
        CwRstAutoEvaluator.Assessment assessment = CwRstAutoEvaluator.evaluateSentSuggestion(
                session(true, "BI9CLT DE BG7YOZ UR 599 BK", "BI9CLT DE BG7YOZ UR 599 BK", "", "BG7YOZ"),
                spectrum(500, 450, 0, 1100, 1300)
        );

        assertTrue(assessment.debugSummary(), assessment.hasSuggestion());
        assertEquals("599", assessment.suggestedRstSent());
        assertEquals(CwRstAutoEvaluator.Source.LIVE_RX_AUDIO, assessment.source());
        assertTrue(assessment.debugSummary(), assessment.confidence() >= 75);
    }

    @Test
    public void readableMidSignalSessionSuggests579() {
        CwRstAutoEvaluator.Assessment assessment = CwRstAutoEvaluator.evaluateSentSuggestion(
                session(true, "BG7YOZ DE BI9CLT K", "BG7YOZ DE BI9CLT K", "", "BI9CLT"),
                spectrum(650, 600, 0, 700, 1500)
        );

        assertTrue(assessment.debugSummary(), assessment.hasSuggestion());
        assertEquals("579", assessment.suggestedRstSent());
        assertTrue(assessment.debugSummary(), assessment.signalScore() >= 50);
        assertTrue(assessment.debugSummary(), assessment.readabilityScore() >= 70);
    }

    private static RxSessionSnapshot session(
            boolean captureActive,
            String rawText,
            String normalizedText,
            String previewRawText,
            String primaryCallsign
    ) {
        return new RxSessionSnapshot(
                System.currentTimeMillis(),
                "Mic",
                captureActive ? "ACTIVE" : "IDLE",
                captureActive,
                700,
                700,
                700,
                18,
                18,
                rawText,
                previewRawText,
                normalizedText,
                primaryCallsign,
                "OK",
                "",
                false,
                false,
                "",
                "",
                ""
        );
    }

    private static SpectrumSnapshotData spectrum(
            int manualThreshold,
            int noiseFloorEstimate,
            int recommendedThreshold,
            float toneRmsAmplitude,
            float frameRmsAmplitude
    ) {
        return new SpectrumSnapshotData(
                System.currentTimeMillis(),
                new int[0],
                new float[0],
                700,
                1.0f,
                0.0f,
                700,
                700,
                700,
                700,
                700,
                700,
                700,
                "TRACKED_TARGET",
                "TRACKED_TARGET",
                false,
                false,
                0,
                "NONE",
                manualThreshold,
                manualThreshold,
                noiseFloorEstimate,
                noiseFloorEstimate,
                recommendedThreshold,
                manualThreshold,
                toneRmsAmplitude,
                frameRmsAmplitude,
                false
        );
    }
}
