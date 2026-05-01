package org.bi9clt.cwcn.core.eval;

import org.bi9clt.cwcn.core.interpreter.CwInterpreterSnapshot;
import org.bi9clt.cwcn.core.interpreter.CwInterpretedToken;
import org.bi9clt.cwcn.core.qso.QsoDraftSnapshot;
import org.bi9clt.cwcn.core.qso.QsoPhase;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CwFixtureEvaluatorTest {
    @Test
    public void evaluate_prefersRawInterpreterTextOverNormalizedTextForRecall() {
        CwFixtureScenario scenario = findScenario("user_qsb_cq_18wpm_700hz")
                .withExpectedRawText("CQ CQ CQ DE BI9CLT BI9CLT BI9CLT PSE K");
        CwInterpreterSnapshot snapshot = new CwInterpreterSnapshot(
                "CQ CQ CQ DE BI9CLT BI9CLT BI9CLT PSE K",
                "CQ CQ CQ DE BI9CLT BI9CLT BI9CLT PAGE K",
                Collections.emptyList(),
                "BI9CLT",
                Collections.singletonList("BI9CLT"),
                Collections.emptyList(),
                null
        );

        CwFixtureEvaluationResult result = CwFixtureEvaluator.evaluate(
                scenario,
                snapshot,
                null,
                true
        );

        assertTrue(result.exactTextMatch());
        assertFalse(result.exactNormalizedTextMatch());
        assertEquals(1.0d, result.textTokenRecall(), 0.0001d);
        assertEquals(0.8889d, result.normalizedTextTokenRecall(), 0.001d);
        assertEquals(
                "CQ CQ CQ DE BI9CLT BI9CLT BI9CLT PSE K",
                result.actualRawText()
        );
        assertEquals(
                "CQ CQ CQ DE BI9CLT BI9CLT BI9CLT PAGE K",
                result.actualNormalizedText()
        );
    }

    @Test
    public void evaluate_doesNotCountSemanticResidueUnderstandingAsVisibleRecoveryPressure() {
        CwFixtureScenario scenario = findScenario("human_damaged_report_residue_exchange");
        CwInterpreterSnapshot interpreterSnapshot = new CwInterpreterSnapshot(
                "BI9CLT DE BG7YOZ UR ?NN B",
                "BI9CLT DE BG7YOZ UR ?NN B",
                Arrays.asList(
                        new CwInterpretedToken("BI9CLT", "BI9CLT", CwInterpretedToken.Type.CALLSIGN_CANDIDATE),
                        new CwInterpretedToken("DE", "DE", CwInterpretedToken.Type.DE),
                        new CwInterpretedToken("BG7YOZ", "BG7YOZ", CwInterpretedToken.Type.CALLSIGN_CANDIDATE),
                        new CwInterpretedToken("UR", "UR", CwInterpretedToken.Type.FREE_TEXT),
                        new CwInterpretedToken("?NN", "?NN", CwInterpretedToken.Type.REPORT),
                        new CwInterpretedToken("B", "B", CwInterpretedToken.Type.CONTROL)
                ),
                "BG7YOZ",
                Arrays.asList("BI9CLT", "BG7YOZ"),
                Arrays.asList(
                        "Station identification / callsign exchange",
                        "Report exchange",
                        "Directed report to called station",
                        "Turn handoff / over"
                ),
                null
        );
        QsoDraftSnapshot qsoSnapshot = new QsoDraftSnapshot(
                QsoPhase.REPORT_EXCHANGE,
                "BI9CLT",
                "BG7YOZ",
                null,
                "599",
                null,
                null,
                false,
                false,
                false,
                false,
                false,
                false,
                "BI9CLT DE BG7YOZ UR ?NN B",
                Arrays.asList(
                        "Station identification / callsign exchange",
                        "Report exchange",
                        "Directed report to called station",
                        "Turn handoff / over"
                ),
                false,
                false,
                0L,
                null
        );

        CwFixtureEvaluationResult result = CwFixtureEvaluator.evaluate(
                scenario,
                interpreterSnapshot,
                qsoSnapshot,
                true
        );

        assertEquals(0, result.normalizedTokenCount());
        assertTrue(result.normalizedTokenPairs().isEmpty());
        assertEquals("NONE", result.recoveryPressureCode());
        assertEquals("No explicit token recovery", result.recoveryPressureLabel());
        assertEquals("BI9CLT DE BG7YOZ UR ?NN B", result.actualNormalizedText());
    }

    private CwFixtureScenario findScenario(String scenarioId) {
        for (CwFixtureScenario scenario : CwFixtureLibrary.scenarios()) {
            if (scenario.id().equals(scenarioId)) {
                return scenario;
            }
        }
        throw new IllegalArgumentException("Unknown scenario: " + scenarioId);
    }
}
