package org.bi9clt.cwcn.core.eval;

import org.bi9clt.cwcn.core.interpreter.CwInterpreterSnapshot;
import org.junit.Test;

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
                "CQ CQ CQ DE BI9CLT BI9CLT BI9CLT PLEASE K",
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
                "CQ CQ CQ DE BI9CLT BI9CLT BI9CLT PLEASE K",
                result.actualNormalizedText()
        );
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
