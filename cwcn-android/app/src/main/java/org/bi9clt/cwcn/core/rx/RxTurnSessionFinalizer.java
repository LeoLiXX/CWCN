package org.bi9clt.cwcn.core.rx;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.signal.CwToneEvent;

import java.util.Collections;
import java.util.List;

/**
 * Shared owner for turn-scoped committed output tracking and tail repair finalization.
 *
 * <p>This controller keeps the turn-local repair tracker and the committed output side
 * effects in sync while admitted decode events arrive, then applies an optional
 * trailing-window repair before a turn is closed.</p>
 */
public final class RxTurnSessionFinalizer {
    @Nullable private final RxTurnTailRepairController turnTailRepairController;
    @Nullable private final RxCommittedOutputController committedOutputController;

    public RxTurnSessionFinalizer(
            @Nullable RxTurnTailRepairController turnTailRepairController,
            @Nullable RxCommittedOutputController committedOutputController
    ) {
        this.turnTailRepairController = turnTailRepairController;
        this.committedOutputController = committedOutputController;
    }

    public void beginNewTurn() {
        if (turnTailRepairController != null) {
            turnTailRepairController.beginNewTurn();
        }
    }

    public void endTurn() {
        if (turnTailRepairController != null) {
            turnTailRepairController.endTurn();
        }
    }

    public boolean turnActive() {
        return turnTailRepairController != null && turnTailRepairController.turnActive();
    }

    public boolean currentTurnHasCommittedDecodeEvents() {
        return turnTailRepairController != null
                && turnTailRepairController.currentTurnHasCommittedDecodeEvents();
    }

    public void noteToneEvent(@Nullable CwToneEvent toneEvent) {
        if (turnTailRepairController != null) {
            turnTailRepairController.noteToneEvent(toneEvent);
        }
    }

    public void processCommittedDecodeEvent(@Nullable CwDecodeEvent decodeEvent) {
        if (turnTailRepairController != null) {
            turnTailRepairController.noteCommittedDecodeEvent(decodeEvent);
        }
        if (committedOutputController != null) {
            committedOutputController.processCommittedDecodeEvent(decodeEvent);
        }
    }

    public void processCommittedDecodeEvents(@Nullable List<CwDecodeEvent> decodeEvents) {
        if (decodeEvents == null || decodeEvents.isEmpty()) {
            return;
        }
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            processCommittedDecodeEvent(decodeEvent);
        }
    }

    @Nullable
    public TurnFinalization finalizeCurrentTurn(long flushTimestampMs) {
        if (turnTailRepairController == null) {
            return null;
        }
        RxTurnTailRepairController.RepairApplication repairApplication =
                turnTailRepairController.maybeRepairCurrentTurn(flushTimestampMs);
        if (repairApplication == null) {
            return null;
        }
        if (committedOutputController != null) {
            committedOutputController.rebuildFromCommittedDecodeEvents(
                    repairApplication.sessionDecodeEvents()
            );
        }
        return new TurnFinalization(repairApplication);
    }

    public List<CwDecodeEvent> sessionDecodeEventsSnapshot() {
        if (turnTailRepairController == null) {
            return Collections.emptyList();
        }
        return turnTailRepairController.sessionDecodeEventsSnapshot();
    }

    public static final class TurnFinalization {
        private final RxTurnTailRepairController.RepairApplication repairApplication;

        private TurnFinalization(RxTurnTailRepairController.RepairApplication repairApplication) {
            this.repairApplication = repairApplication;
        }

        public RxTurnTailRepairController.RepairApplication repairApplication() {
            return repairApplication;
        }

        public RxTrailingWindowRepair.RepairResult repairResult() {
            return repairApplication.repairResult();
        }

        public List<CwDecodeEvent> sessionDecodeEvents() {
            return repairApplication.sessionDecodeEvents();
        }
    }
}
