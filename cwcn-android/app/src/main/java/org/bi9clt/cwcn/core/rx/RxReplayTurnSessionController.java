package org.bi9clt.cwcn.core.rx;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.decoder.CwDecoder;
import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared replay-side owner for turn-aware session reconstruction.
 *
 * <p>This controller lets offline replay consumers reuse the same turn observe /
 * committed-output / tail-repair semantics as the live Operate path while also
 * capturing turn transition traces and turn windows for diagnostics.</p>
 */
public final class RxReplayTurnSessionController {
    public enum TransitionKind {
        START,
        END
    }

    public static final class TransitionTrace {
        private final TransitionKind kind;
        private final long timestampMs;
        private final int turnIndex;
        private final int turnSeedWpm;
        private final int referenceWpm;
        private final int currentTurnAnchorWpm;
        private final int retainedTurnAnchorWpm;
        private final String reason;

        private TransitionTrace(
                TransitionKind kind,
                long timestampMs,
                int turnIndex,
                int turnSeedWpm,
                int referenceWpm,
                int currentTurnAnchorWpm,
                int retainedTurnAnchorWpm,
                String reason
        ) {
            this.kind = kind == null ? TransitionKind.START : kind;
            this.timestampMs = Math.max(0L, timestampMs);
            this.turnIndex = Math.max(0, turnIndex);
            this.turnSeedWpm = Math.max(0, turnSeedWpm);
            this.referenceWpm = Math.max(0, referenceWpm);
            this.currentTurnAnchorWpm = Math.max(0, currentTurnAnchorWpm);
            this.retainedTurnAnchorWpm = Math.max(0, retainedTurnAnchorWpm);
            this.reason = reason == null ? "" : reason;
        }

        public TransitionKind kind() {
            return kind;
        }

        public long timestampMs() {
            return timestampMs;
        }

        public int turnIndex() {
            return turnIndex;
        }

        public int turnSeedWpm() {
            return turnSeedWpm;
        }

        public int referenceWpm() {
            return referenceWpm;
        }

        public int currentTurnAnchorWpm() {
            return currentTurnAnchorWpm;
        }

        public int retainedTurnAnchorWpm() {
            return retainedTurnAnchorWpm;
        }

        public String reason() {
            return reason;
        }
    }

    public static final class TurnWindow {
        private final int turnIndex;
        private final long turnStartTimestampMs;
        private final long turnEndTimestampMs;

        private TurnWindow(
                int turnIndex,
                long turnStartTimestampMs,
                long turnEndTimestampMs
        ) {
            this.turnIndex = Math.max(0, turnIndex);
            this.turnStartTimestampMs = Math.max(0L, turnStartTimestampMs);
            this.turnEndTimestampMs = Math.max(turnStartTimestampMs, turnEndTimestampMs);
        }

        public int turnIndex() {
            return turnIndex;
        }

        public long turnStartTimestampMs() {
            return turnStartTimestampMs;
        }

        public long turnEndTimestampMs() {
            return turnEndTimestampMs;
        }
    }

    @Nullable private final CwSignalProcessor signalProcessor;
    @Nullable private final CwHybridTimingModel timingModel;
    @Nullable private final CwDecoder decoder;
    @Nullable private final LiveRxWpmGuard wpmGuard;
    @Nullable private final RxTurnController turnController;
    @Nullable private final RxTurnSessionCoordinator turnSessionCoordinator;
    @Nullable private final RxTurnSessionFinalizer turnSessionFinalizer;
    private final ArrayList<TransitionTrace> transitionTraces = new ArrayList<>();
    private int tailRepairCount;

    public RxReplayTurnSessionController(
            @Nullable CwSignalProcessor signalProcessor,
            @Nullable CwHybridTimingModel timingModel,
            @Nullable CwDecoder decoder,
            @Nullable LiveRxWpmGuard wpmGuard,
            @Nullable RxTurnController turnController,
            @Nullable RxTurnSessionCoordinator turnSessionCoordinator,
            @Nullable RxTurnSessionFinalizer turnSessionFinalizer
    ) {
        this.signalProcessor = signalProcessor;
        this.timingModel = timingModel;
        this.decoder = decoder;
        this.wpmGuard = wpmGuard;
        this.turnController = turnController;
        this.turnSessionCoordinator = turnSessionCoordinator;
        this.turnSessionFinalizer = turnSessionFinalizer;
    }

    public RxTurnSessionCoordinator.Observation observeFrameEnd(long timestampMs) {
        return observe(null, timestampMs);
    }

    public RxTurnSessionCoordinator.Observation observe(
            @Nullable CwSignalSnapshot signalSnapshot,
            long timestampMs
    ) {
        if ((signalSnapshot == null && signalProcessor == null)
                || timingModel == null
                || turnController == null
                || turnSessionCoordinator == null
                || timestampMs <= 0L) {
            return RxTurnSessionCoordinator.Observation.none();
        }
        CwSignalSnapshot effectiveSignalSnapshot = signalSnapshot == null
                ? signalProcessor.snapshot()
                : signalSnapshot;
        CwTimingSnapshot timingSnapshot = timingModel.rawSnapshot();
        int referenceWpm = resolveReferenceWpm(timingSnapshot);
        RxTurnSessionCoordinator.Observation observation = turnSessionCoordinator.observe(
                effectiveSignalSnapshot,
                decoder != null && decoder.hasPendingCharacter(),
                timestampMs,
                referenceWpm
        );
        if (observation.startedNewTurn() || observation.endedTurn()) {
            transitionTraces.add(new TransitionTrace(
                    observation.startedNewTurn() ? TransitionKind.START : TransitionKind.END,
                    timestampMs,
                    turnController.turnIndex(),
                    observation.turnSeedWpm(),
                    referenceWpm,
                    turnController.currentTurnAnchorWpm(),
                    turnController.retainedTurnAnchorWpm(),
                    observation.reason()
            ));
        }
        if (observation.turnFinalization() != null) {
            tailRepairCount += 1;
        }
        return observation;
    }

    public void noteToneEvent(@Nullable CwToneEvent toneEvent) {
        if (turnSessionFinalizer != null) {
            turnSessionFinalizer.noteToneEvent(toneEvent);
        }
    }

    public void processCommittedDecodeEvents(@Nullable List<CwDecodeEvent> decodeEvents) {
        if (turnSessionFinalizer != null) {
            turnSessionFinalizer.processCommittedDecodeEvents(decodeEvents);
        }
    }

    @Nullable
    public RxTurnSessionFinalizer.TurnFinalization finalizeAtStop(long flushTimestampMs) {
        if (turnSessionFinalizer == null
                || !turnSessionFinalizer.turnActive()
                || flushTimestampMs <= 0L) {
            return null;
        }
        RxTurnSessionFinalizer.TurnFinalization turnFinalization =
                turnSessionFinalizer.finalizeCurrentTurn(flushTimestampMs);
        if (turnFinalization != null) {
            tailRepairCount += 1;
        }
        return turnFinalization;
    }

    public int turnCount() {
        int count = 0;
        for (TransitionTrace transitionTrace : transitionTraces) {
            if (transitionTrace != null && transitionTrace.kind() == TransitionKind.START) {
                count += 1;
            }
        }
        return count;
    }

    public int tailRepairCount() {
        return tailRepairCount;
    }

    public List<TransitionTrace> transitionTracesSnapshot() {
        return Collections.unmodifiableList(new ArrayList<>(transitionTraces));
    }

    public List<TurnWindow> turnWindowsSnapshot(long sessionEndTimestampMs) {
        if (transitionTraces.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<TurnWindow> turnWindows = new ArrayList<>();
        for (TransitionTrace transitionTrace : transitionTraces) {
            if (transitionTrace == null || transitionTrace.kind() != TransitionKind.START) {
                continue;
            }
            TransitionTrace endTrace = findTurnEndTrace(
                    transitionTrace.turnIndex(),
                    transitionTrace.timestampMs()
            );
            turnWindows.add(new TurnWindow(
                    transitionTrace.turnIndex(),
                    transitionTrace.timestampMs(),
                    endTrace == null ? Math.max(transitionTrace.timestampMs(), sessionEndTimestampMs) : endTrace.timestampMs()
            ));
        }
        return Collections.unmodifiableList(turnWindows);
    }

    @Nullable
    private TransitionTrace findTurnEndTrace(int turnIndex, long turnStartTimestampMs) {
        for (TransitionTrace transitionTrace : transitionTraces) {
            if (transitionTrace == null
                    || transitionTrace.kind() != TransitionKind.END
                    || transitionTrace.turnIndex() != turnIndex
                    || transitionTrace.timestampMs() < turnStartTimestampMs) {
                continue;
            }
            return transitionTrace;
        }
        return null;
    }

    private int resolveReferenceWpm(@Nullable CwTimingSnapshot timingSnapshot) {
        if (wpmGuard != null) {
            int referenceWpm = wpmGuard.resolveReferenceWpm(timingSnapshot);
            if (referenceWpm > 0) {
                return referenceWpm;
            }
        }
        if (timingSnapshot != null && timingSnapshot.estimatedWpm() > 0) {
            return timingSnapshot.estimatedWpm();
        }
        return timingSnapshot == null
                ? 0
                : (int) Math.round(Math.max(0.0d, timingSnapshot.estimatedWpmPrecise()));
    }
}
