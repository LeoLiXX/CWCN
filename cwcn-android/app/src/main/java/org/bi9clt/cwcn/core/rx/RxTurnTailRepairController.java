package org.bi9clt.cwcn.core.rx;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.signal.CwToneEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tracks current-turn tone/final-decode buffers so a conservative tail repair
 * can be attempted at turn end or explicit RX stop.
 */
public final class RxTurnTailRepairController {
    private static final int DEFAULT_TRAILING_WORD_COUNT = 2;

    private final ArrayList<CwDecodeEvent> sessionCommittedDecodeEvents = new ArrayList<>();
    private final ArrayList<CwToneEvent> currentTurnToneEvents = new ArrayList<>();
    private int currentTurnDecodeStartIndex;
    private boolean turnActive;

    public void reset() {
        sessionCommittedDecodeEvents.clear();
        currentTurnToneEvents.clear();
        currentTurnDecodeStartIndex = 0;
        turnActive = false;
    }

    public void beginNewTurn() {
        currentTurnToneEvents.clear();
        currentTurnDecodeStartIndex = sessionCommittedDecodeEvents.size();
        turnActive = true;
    }

    public void endTurn() {
        currentTurnToneEvents.clear();
        currentTurnDecodeStartIndex = sessionCommittedDecodeEvents.size();
        turnActive = false;
    }

    public boolean turnActive() {
        return turnActive;
    }

    public boolean currentTurnHasCommittedDecodeEvents() {
        return turnActive && sessionCommittedDecodeEvents.size() > currentTurnDecodeStartIndex;
    }

    public void noteToneEvent(@Nullable CwToneEvent toneEvent) {
        if (!turnActive || toneEvent == null) {
            return;
        }
        currentTurnToneEvents.add(toneEvent);
    }

    public void noteCommittedDecodeEvent(@Nullable CwDecodeEvent decodeEvent) {
        if (decodeEvent == null || !isFinalDecodeEvent(decodeEvent)) {
            return;
        }
        sessionCommittedDecodeEvents.add(decodeEvent);
    }

    @Nullable
    public RepairApplication maybeRepairCurrentTurn(long flushTimestampMs) {
        if (!turnActive
                || currentTurnToneEvents.isEmpty()
                || currentTurnDecodeStartIndex < 0
                || currentTurnDecodeStartIndex >= sessionCommittedDecodeEvents.size()) {
            return null;
        }

        ArrayList<CwDecodeEvent> currentTurnDecodeEvents = new ArrayList<>(
                sessionCommittedDecodeEvents.subList(
                        currentTurnDecodeStartIndex,
                        sessionCommittedDecodeEvents.size()
                )
        );
        RxTrailingWindowRepair.RepairResult repairResult =
                RxTrailingWindowRepair.repairTrailingWordsIfBeneficial(
                        currentTurnToneEvents,
                        currentTurnDecodeEvents,
                        flushTimestampMs,
                        DEFAULT_TRAILING_WORD_COUNT
                );
        if (repairResult == null) {
            return null;
        }

        while (sessionCommittedDecodeEvents.size() > currentTurnDecodeStartIndex) {
            sessionCommittedDecodeEvents.remove(sessionCommittedDecodeEvents.size() - 1);
        }
        sessionCommittedDecodeEvents.addAll(repairResult.repairedDecodeEvents());
        return new RepairApplication(
                repairResult,
                new ArrayList<>(sessionCommittedDecodeEvents)
        );
    }

    public List<CwDecodeEvent> sessionDecodeEventsSnapshot() {
        return Collections.unmodifiableList(new ArrayList<>(sessionCommittedDecodeEvents));
    }

    private static boolean isFinalDecodeEvent(CwDecodeEvent decodeEvent) {
        return decodeEvent.type() == CwDecodeEvent.Type.CHARACTER_DECODED
                || decodeEvent.type() == CwDecodeEvent.Type.WORD_BREAK;
    }

    public static final class RepairApplication {
        private final RxTrailingWindowRepair.RepairResult repairResult;
        private final List<CwDecodeEvent> sessionDecodeEvents;

        private RepairApplication(
                RxTrailingWindowRepair.RepairResult repairResult,
                List<CwDecodeEvent> sessionDecodeEvents
        ) {
            this.repairResult = repairResult;
            this.sessionDecodeEvents = Collections.unmodifiableList(sessionDecodeEvents);
        }

        public RxTrailingWindowRepair.RepairResult repairResult() {
            return repairResult;
        }

        public List<CwDecodeEvent> sessionDecodeEvents() {
            return sessionDecodeEvents;
        }
    }
}
