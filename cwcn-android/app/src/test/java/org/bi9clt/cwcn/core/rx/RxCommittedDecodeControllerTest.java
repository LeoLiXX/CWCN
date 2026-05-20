package org.bi9clt.cwcn.core.rx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel;
import org.junit.Test;

import java.util.List;

public final class RxCommittedDecodeControllerTest {
    @Test
    public void admitDropsEventsWhileTurnControllerIsIdle() {
        RxTurnController turnController = new RxTurnController();
        RxCommittedDecodeController controller = new RxCommittedDecodeController(
                new CwSignalProcessor(),
                new CwHybridTimingModel(),
                new LiveRxWpmGuard(),
                turnController,
                new TimingAnchorController(),
                null
        );

        List<CwDecodeEvent> admittedEvents = controller.admit(
                decodedCharacterEvent(1000L, "A"),
                true
        );

        assertTrue(admittedEvents.isEmpty());
    }

    @Test
    public void admitPassesEventsWithoutTurnGate() {
        RxCommittedDecodeController controller = new RxCommittedDecodeController(
                new CwSignalProcessor(),
                new CwHybridTimingModel(),
                new LiveRxWpmGuard(),
                null,
                new TimingAnchorController(),
                null
        );

        List<CwDecodeEvent> admittedEvents = controller.admit(
                decodedCharacterEvent(1000L, "A"),
                false
        );

        assertEquals(1, admittedEvents.size());
        assertEquals("A", admittedEvents.get(0).emittedValue());
    }

    private CwDecodeEvent decodedCharacterEvent(long timestampMs, String emittedValue) {
        return new CwDecodeEvent(
                CwDecodeEvent.Type.CHARACTER_DECODED,
                timestampMs,
                ".-",
                emittedValue,
                emittedValue,
                ".-",
                false
        );
    }
}
