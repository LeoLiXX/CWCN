package org.bi9clt.cwcn.core.eval;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class CwFixtureEvaluationResultTest {
    @Test
    public void compactSummaryMarksFrontEndLossAsSig() {
        CwFixtureEvaluationResult result = new CwFixtureEvaluationResult(
                "fixture",
                "Fixture",
                1L,
                true,
                false,
                false,
                0.0d,
                0.32d,
                0.25d,
                0.20d,
                0.0d,
                "CQ CQ DE BI9CLT",
                "CQ",
                "CALLING_CQ",
                "REPLY_DETECTED",
                "",
                "",
                "",
                "",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("BI9CLT"),
                Collections.singletonList("BI9CLT"),
                Collections.singletonList("CQ / calling flow"),
                Collections.singletonList("Text token recall below baseline"),
                false,
                false,
                120.0d,
                0.18d,
                0.02d,
                1,
                0.80d,
                6,
                650,
                650
        );

        assertEquals("SIG", result.likelyBottleneckCode());
        assertEquals("MISS", result.frontEndQualityCode());
        assertTrue(result.renderCompactSummary().contains("D:SIG"));
        assertTrue(result.renderCompactSummary().contains("F:MISS"));
        assertTrue(result.renderSummary().contains("Likely bottleneck: Front-end signal/timing acquisition"));
    }

    @Test
    public void diagnosticNotesMarkLateSemanticDriftAsQso() {
        CwFixtureEvaluationResult result = new CwFixtureEvaluationResult(
                "fixture",
                "Fixture",
                1L,
                true,
                false,
                true,
                1.0d,
                1.0d,
                1.0d,
                1.0d,
                0.0d,
                "BI9CLT DE BG7YOZ UR 599 BK",
                "BI9CLT DE BG7YOZ UR 599 BK",
                "REPORT_EXCHANGE",
                "REPLY_DETECTED",
                "",
                "",
                "599",
                "",
                Collections.singletonList("BG7YOZ"),
                Collections.singletonList("Directed report to called station"),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("Phase mismatch"),
                false,
                false,
                6200.0d,
                0.74d,
                0.38d,
                8,
                0.28d,
                4,
                650,
                650
        );

        assertEquals("QSO", result.likelyBottleneckCode());
        assertEquals("DROP", result.frontEndQualityCode());
        assertTrue(result.diagnosticNotes().get(0).contains("Decoded content was mostly present")
                || result.renderSummary().contains("QSO state / semantic mapping"));
    }

    @Test
    public void earlierHealthyLockShiftsCollapseAwayFromSigWhenInterpreterLooksWeaker() {
        CwFixtureEvaluationResult result = new CwFixtureEvaluationResult(
                "fixture",
                "Fixture",
                1L,
                true,
                false,
                false,
                0.0d,
                0.62d,
                0.40d,
                0.30d,
                1.0d,
                "BI9CLT DE BG7YOZ UR 599 BK",
                "BI9CLT DE BG7YOZ UR 599 B",
                "REPORT_EXCHANGE",
                "REPORT_EXCHANGE",
                "",
                "",
                "599",
                "599",
                Collections.singletonList("BG7YOZ"),
                Collections.singletonList("Directed report to called station"),
                Collections.singletonList("BK"),
                Collections.singletonList("BI9CLT"),
                Collections.emptyList(),
                Collections.singletonList("Callsign candidate loss"),
                false,
                false,
                7800.0d,
                0.72d,
                0.32d,
                7,
                0.24d,
                5,
                650,
                650
        );

        assertEquals("INT", result.likelyBottleneckCode());
        assertEquals("DROP", result.frontEndQualityCode());
        assertTrue(result.diagnosticNotes().get(0).contains("usable earlier lock window"));
        assertTrue(result.renderSummary().contains("Front-end history"));
    }

    @Test
    public void retainedHealthyLockIsMarkedAsGoodFrontEndQuality() {
        CwFixtureEvaluationResult result = new CwFixtureEvaluationResult(
                "fixture",
                "Fixture",
                1L,
                true,
                true,
                true,
                1.0d,
                1.0d,
                1.0d,
                1.0d,
                1.0d,
                "BI9CLT DE BG7YOZ UR 599 BK",
                "BI9CLT DE BG7YOZ UR 599 BK",
                "REPORT_EXCHANGE",
                "REPORT_EXCHANGE",
                "",
                "",
                "599",
                "599",
                Collections.singletonList("BG7YOZ"),
                Collections.singletonList("Directed report to called station"),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                true,
                false,
                8200.0d,
                0.78d,
                0.42d,
                9,
                0.0d,
                0,
                650,
                650
        );

        assertEquals("GOOD", result.frontEndQualityCode());
        assertTrue(result.renderSummary().contains("Front-end quality: Healthy lock retained"));
        assertTrue(result.renderCompactSummary().contains("F:GOOD"));
    }

    @Test
    public void healthyLockThatEndsOnCleanToneOffIsAlsoMarkedGood() {
        CwFixtureEvaluationResult result = new CwFixtureEvaluationResult(
                "fixture",
                "Fixture",
                1L,
                true,
                true,
                true,
                1.0d,
                1.0d,
                1.0d,
                1.0d,
                1.0d,
                "BI9CLT DE BG7YOZ UR 599 BK",
                "BI9CLT DE BG7YOZ UR 599 BK",
                "REPORT_EXCHANGE",
                "REPORT_EXCHANGE",
                "",
                "",
                "599",
                "599",
                Collections.singletonList("BG7YOZ"),
                Collections.singletonList("Directed report to called station"),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                false,
                true,
                7900.0d,
                0.74d,
                0.39d,
                8,
                0.05d,
                1,
                650,
                650
        );

        assertEquals("GOOD", result.frontEndQualityCode());
        assertTrue(result.renderSummary().contains("Healthy lock with clean release"));
        assertTrue(result.diagnosticNotes().get(0).contains("clean tone-off release"));
    }

    @Test
    public void strongLockedOffsetToneIsClassifiedAsWrongToneTracking() {
        CwFixtureEvaluationResult result = new CwFixtureEvaluationResult(
                "fixture",
                "Fixture",
                1L,
                true,
                false,
                false,
                0.0d,
                0.10d,
                0.0d,
                0.0d,
                0.0d,
                "BI9CLT DE BG7YOZ UR 599 BK",
                "BI9CL",
                "REPORT_EXCHANGE",
                "REPLY_DETECTED",
                "",
                "",
                "599",
                "",
                Collections.singletonList("BI9CL"),
                Collections.emptyList(),
                Collections.singletonList("BI9CLT"),
                Collections.singletonList("BG7YOZ"),
                Collections.singletonList("Directed report to called station"),
                Collections.singletonList("Text token recall below baseline"),
                true,
                false,
                14000.0d,
                0.88d,
                0.85d,
                12,
                0.06d,
                1,
                650,
                740
        );

        assertEquals("WRONG", result.frontEndQualityCode());
        assertEquals("TRK", result.likelyBottleneckCode());
        assertTrue(result.renderSummary().contains("wrongTone=yes"));
        assertTrue(result.renderSummary().contains("Wrong-tone acquisition / tracking"));
    }

    @Test
    public void recoveryPressureDistinguishesHeavyBestEffortNormalization() {
        CwFixtureEvaluationResult result = new CwFixtureEvaluationResult(
                "fixture",
                "Fixture",
                1L,
                true,
                false,
                false,
                1.0d,
                0.70d,
                1.0d,
                1.0d,
                1.0d,
                "BI9CLT DE BG7YOZ UR 599 BK",
                "BI9CLT DE BG7YOZ UR 599 BK",
                "REPORT_EXCHANGE",
                "REPORT_EXCHANGE",
                "",
                "",
                "599",
                "599",
                Collections.singletonList("BG7YOZ"),
                Collections.singletonList("Directed report to called station"),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                4,
                9,
                java.util.Arrays.asList("?NN->599", "EB->BK"),
                false,
                false,
                8200.0d,
                0.74d,
                0.40d,
                8,
                0.08d,
                1,
                650,
                650
        );

        assertEquals("HIGH", result.recoveryPressureCode());
        assertTrue(result.renderCompactSummary().contains("R:HIGH"));
        assertTrue(result.renderSummary().contains("Recovery pressure: Heavy best-effort normalization"));
        assertTrue(result.renderSummary().contains("?NN->599"));
    }
}
