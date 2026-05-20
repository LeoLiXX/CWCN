package org.bi9clt.cwcn.core.rx;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class RxTurnControllerTest {
    @Test
    public void firstIncomingActivityStartsNewTurnFromTxSeed() {
        RxTurnController controller = new RxTurnController();
        controller.setTxSeedWpm(24);

        RxTurnController.Transition transition = controller.observe(
                true,
                false,
                100L,
                0
        );

        assertTrue(transition.startedNewTurn());
        assertTrue(transition.shouldSoftResetLearner());
        assertEquals(24, transition.turnSeedWpm());
        assertEquals(RxTurnController.Phase.ACTIVE, controller.phase());
        assertEquals(1, controller.turnIndex());
    }

    @Test
    public void firstIncomingActivityDoesNotUseExternalReferenceAsImplicitSeed() {
        RxTurnController controller = new RxTurnController();

        RxTurnController.Transition transition = controller.observe(
                true,
                false,
                100L,
                24
        );

        assertTrue(transition.startedNewTurn());
        assertEquals(0, transition.turnSeedWpm());
    }

    @Test
    public void turnEndAfterLongSilenceDropsLearnedAnchorBeforeNextTurn() {
        RxTurnController controller = new RxTurnController();
        controller.setTxSeedWpm(18);

        RxTurnController.Transition startTransition = controller.observe(
                true,
                false,
                100L,
                18
        );
        assertTrue(startTransition.startedNewTurn());

        controller.noteStableDecode(260L, 24, true);
        controller.observe(true, false, 500L, 24);

        RxTurnController.Transition noEndYet = controller.observe(
                false,
                false,
                1400L,
                24
        );
        assertFalse(noEndYet.endedTurn());

        RxTurnController.Transition endTransition = controller.observe(
                false,
                false,
                3000L,
                24
        );
        assertTrue(endTransition.endedTurn());
        assertEquals(RxTurnController.Phase.IDLE, controller.phase());
        assertEquals(0, controller.retainedTurnAnchorWpm());

        RxTurnController.Transition nextStart = controller.observe(
                true,
                false,
                2600L,
                0
        );
        assertTrue(nextStart.startedNewTurn());
        assertEquals(18, nextStart.turnSeedWpm());
    }

    @Test
    public void turnEndAfterLongSilenceMayCarryLearnedAnchorWhenEnabled() {
        RxTurnController controller = new RxTurnController();
        controller.setTxSeedWpm(18);
        controller.setCrossTurnCarryEnabled(true);

        RxTurnController.Transition startTransition = controller.observe(
                true,
                false,
                100L,
                18
        );
        assertTrue(startTransition.startedNewTurn());

        controller.noteStableDecode(260L, 24, true);
        controller.observe(true, false, 500L, 24);

        RxTurnController.Transition endTransition = controller.observe(
                false,
                false,
                3000L,
                24
        );
        assertTrue(endTransition.endedTurn());
        assertEquals(24, controller.retainedTurnAnchorWpm());

        RxTurnController.Transition nextStart = controller.observe(
                true,
                false,
                2600L,
                0
        );
        assertTrue(nextStart.startedNewTurn());
        assertEquals(24, nextStart.turnSeedWpm());
    }

    @Test
    public void pendingCharacterKeepsTurnAliveDuringSilence() {
        RxTurnController controller = new RxTurnController();
        controller.setTxSeedWpm(20);

        assertTrue(controller.observe(true, false, 100L, 20).startedNewTurn());
        controller.observe(true, false, 400L, 20);

        RxTurnController.Transition heldByPendingCharacter = controller.observe(
                false,
                true,
                2600L,
                20
        );
        assertFalse(heldByPendingCharacter.endedTurn());
        assertEquals(RxTurnController.Phase.ACTIVE, controller.phase());

        RxTurnController.Transition ended = controller.observe(
                false,
                false,
                5000L,
                20
        );
        assertTrue(ended.endedTurn());
    }

    @Test
    public void dynamicTurnEndSilenceDoesNotShrinkBelowDefaultFloor() {
        RxTurnController controller = new RxTurnController();
        controller.setTxSeedWpm(18);

        assertTrue(controller.observe(true, false, 100L, 18).startedNewTurn());
        controller.observe(true, false, 500L, 18);

        RxTurnController.Transition stillActive = controller.observe(
                false,
                false,
                2200L,
                24
        );
        assertFalse(stillActive.endedTurn());

        RxTurnController.Transition ended = controller.observe(
                false,
                false,
                3000L,
                24
        );
        assertTrue(ended.endedTurn());
    }

    @Test
    public void laterTurnWithoutTrustedCarryDoesNotUseStaleExternalReferenceAsSeed() {
        RxTurnController controller = new RxTurnController();
        controller.setTxSeedWpm(18);

        assertTrue(controller.observe(true, false, 100L, 40).startedNewTurn());
        RxTurnController.Transition ended = controller.observe(false, false, 2500L, 0);
        assertTrue(ended.endedTurn());

        RxTurnController.Transition nextStart = controller.observe(true, false, 2600L, 40);

        assertTrue(nextStart.startedNewTurn());
        assertEquals(18, nextStart.turnSeedWpm());
    }

    @Test
    public void turnWithoutLearnedAnchorStillFallsBackToTxSeed() {
        RxTurnController controller = new RxTurnController();
        controller.setTxSeedWpm(18);

        assertTrue(controller.observe(true, false, 100L, 18).startedNewTurn());
        RxTurnController.Transition ended = controller.observe(false, false, 2500L, 0);
        assertTrue(ended.endedTurn());
        assertEquals(0, controller.retainedTurnAnchorWpm());

        RxTurnController.Transition nextStart = controller.observe(true, false, 2600L, 0);
        assertTrue(nextStart.startedNewTurn());
        assertEquals(18, nextStart.turnSeedWpm());
    }
}
