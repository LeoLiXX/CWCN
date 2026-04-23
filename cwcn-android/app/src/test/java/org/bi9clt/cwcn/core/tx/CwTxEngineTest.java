package org.bi9clt.cwcn.core.tx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CwTxEngineTest {
    @Test
    public void buildPlanEncodesNormalizedMorseWithExpectedDotDuration() {
        CwTxEngine engine = new CwTxEngine();

        CwTxPlan plan = engine.buildPlan("cq", 20, 700);

        assertEquals("CQ", plan.normalizedText());
        assertEquals("-.-. --.-", plan.morsePreview());
        assertEquals(60, plan.dotDurationMs());
        assertEquals(700, plan.toneFrequencyHz());
        assertFalse(plan.elements().isEmpty());
        assertEquals(CwTxElement.Kind.KEY_DOWN, plan.elements().get(0).kind());
    }

    @Test
    public void buildPlanCollapsesWhitespaceAndDropsUnsupportedCharacters() {
        CwTxEngine engine = new CwTxEngine();

        CwTxPlan plan = engine.buildPlan("  cq   de  bi9clt #1  ", 18, 650);

        assertEquals("CQ DE BI9CLT 1", plan.normalizedText());
        assertTrue(plan.morsePreview().contains("/"));
        assertTrue(plan.totalDurationMs() > 0);
    }

    @Test
    public void buildPlanMergesWordGapIntoSingleKeyUpElement() {
        CwTxEngine engine = new CwTxEngine();

        CwTxPlan plan = engine.buildPlan("E E", 20, 650);

        boolean foundWordGap = false;
        for (CwTxElement element : plan.elements()) {
            if (element.kind() == CwTxElement.Kind.KEY_UP && element.durationMs() == 420) {
                foundWordGap = true;
                break;
            }
        }

        assertTrue(foundWordGap);
    }
}
