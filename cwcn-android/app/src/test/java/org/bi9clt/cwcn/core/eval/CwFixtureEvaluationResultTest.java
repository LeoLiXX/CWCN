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
                Collections.singletonList("Text token recall below baseline")
        );

        assertEquals("SIG", result.likelyBottleneckCode());
        assertTrue(result.renderCompactSummary().contains("D:SIG"));
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
                Collections.singletonList("Phase mismatch")
        );

        assertEquals("QSO", result.likelyBottleneckCode());
        assertTrue(result.diagnosticNotes().get(0).contains("Decoded content was mostly present")
                || result.renderSummary().contains("QSO state / semantic mapping"));
    }
}
