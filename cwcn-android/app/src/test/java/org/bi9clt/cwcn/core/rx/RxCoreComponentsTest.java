package org.bi9clt.cwcn.core.rx;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class RxCoreComponentsTest {
    @Test
    public void applySeedWpmUpdatesTimingModelAndTurnSeed() {
        RxCoreComponents components = new RxCoreComponents();

        components.applySeedWpm(22);
        RxTurnController.Transition transition = components.turnController().observe(
                true,
                false,
                100L,
                0
        );

        assertEquals(22, components.timingModel().debugSnapshot().seedWpm());
        assertTrue(transition.startedNewTurn());
        assertEquals(22, transition.turnSeedWpm());
    }

    @Test
    public void resetRuntimeStateReturnsTurnControllerToIdleAndReappliesSeed() {
        RxCoreComponents components = new RxCoreComponents();
        components.applySeedWpm(20);
        components.turnController().observe(true, false, 100L, 0);

        components.resetRuntimeState(17);

        assertEquals(RxTurnController.Phase.IDLE, components.turnController().phase());
        assertEquals(17, components.timingModel().debugSnapshot().seedWpm());

        RxTurnController.Transition nextTransition = components.turnController().observe(
                true,
                false,
                200L,
                0
        );

        assertTrue(nextTransition.startedNewTurn());
        assertEquals(17, nextTransition.turnSeedWpm());
    }
}
