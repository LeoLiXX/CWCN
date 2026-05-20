package org.bi9clt.cwcn.core.rx;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel;

/**
 * Shared turn/session lifecycle fan-out that sits around {@link RxTurnController}.
 *
 * <p>This owner coordinates the shared runtime state transitions that should happen
 * when a turn starts or ends, including optional turn-end finalization such as
 * tail repair and committed-output rebuild. UI-specific side effects still remain
 * with callers.</p>
 */
public final class RxTurnSessionCoordinator {
    @Nullable private final CwSignalProcessor signalProcessor;
    @Nullable private final CwHybridTimingModel timingModel;
    @Nullable private final LiveRxWpmGuard wpmGuard;
    @Nullable private final RxTurnController turnController;
    @Nullable private final TimingAnchorController timingAnchorController;
    @Nullable private final RxRawCommitGate rawCommitGate;
    @Nullable private final RxTurnSessionFinalizer turnSessionFinalizer;
    @Nullable private final LiveRxToneEventStabilizer toneEventStabilizer;
    @Nullable private final TurnEndListener turnEndListener;

    public interface TurnEndListener {
        void beforeTurnEnd(RxTurnController.Transition transition, long timestampMs);
    }

    public static final class Observation {
        private static final Observation NONE = new Observation(RxTurnController.Transition.none(), false);

        private final RxTurnController.Transition transition;
        private final boolean frontEndResetApplied;
        @Nullable private final RxTurnSessionFinalizer.TurnFinalization turnFinalization;

        private Observation(
                RxTurnController.Transition transition,
                boolean frontEndResetApplied
        ) {
            this(transition, frontEndResetApplied, null);
        }

        private Observation(
                RxTurnController.Transition transition,
                boolean frontEndResetApplied,
                @Nullable RxTurnSessionFinalizer.TurnFinalization turnFinalization
        ) {
            this.transition = transition == null ? RxTurnController.Transition.none() : transition;
            this.frontEndResetApplied = frontEndResetApplied;
            this.turnFinalization = turnFinalization;
        }

        public static Observation none() {
            return NONE;
        }

        public RxTurnController.Transition transition() {
            return transition;
        }

        public boolean startedNewTurn() {
            return transition.startedNewTurn();
        }

        public boolean endedTurn() {
            return transition.endedTurn();
        }

        public boolean frontEndResetApplied() {
            return frontEndResetApplied;
        }

        public boolean tailRepairApplied() {
            return turnFinalization != null;
        }

        @Nullable
        public RxTurnSessionFinalizer.TurnFinalization turnFinalization() {
            return turnFinalization;
        }

        public String reason() {
            return transition.reason();
        }

        public int turnSeedWpm() {
            return transition.turnSeedWpm();
        }
    }

    public RxTurnSessionCoordinator(
            @Nullable CwSignalProcessor signalProcessor,
            @Nullable CwHybridTimingModel timingModel,
            @Nullable LiveRxWpmGuard wpmGuard,
            @Nullable RxTurnController turnController,
            @Nullable TimingAnchorController timingAnchorController,
            @Nullable RxRawCommitGate rawCommitGate,
            @Nullable RxTurnSessionFinalizer turnSessionFinalizer,
            @Nullable LiveRxToneEventStabilizer toneEventStabilizer,
            @Nullable TurnEndListener turnEndListener
    ) {
        this.signalProcessor = signalProcessor;
        this.timingModel = timingModel;
        this.wpmGuard = wpmGuard;
        this.turnController = turnController;
        this.timingAnchorController = timingAnchorController;
        this.rawCommitGate = rawCommitGate;
        this.turnSessionFinalizer = turnSessionFinalizer;
        this.toneEventStabilizer = toneEventStabilizer;
        this.turnEndListener = turnEndListener;
    }

    public Observation observe(
            @Nullable CwSignalSnapshot signalSnapshot,
            boolean hasPendingCharacter,
            long timestampMs,
            int referenceWpm
    ) {
        if (signalSnapshot == null
                || timingModel == null
                || turnController == null
                || timestampMs <= 0L) {
            return Observation.none();
        }
        boolean turnActivity = turnController.phase() == RxTurnController.Phase.ACTIVE
                ? RxTurnActivityDecider.isMeaningfulTurnContinuation(signalSnapshot)
                : RxTurnActivityDecider.isMeaningfulTurnActivity(signalSnapshot);
        RxTurnController.Transition transition = turnController.observe(
                turnActivity,
                hasPendingCharacter,
                timestampMs,
                Math.max(0, referenceWpm)
        );
        if (transition.startedNewTurn()) {
            if (transition.shouldSoftResetLearner()) {
                beginNewTurn(transition.turnSeedWpm(), timestampMs);
            }
            return new Observation(transition, false);
        }
        if (transition.endedTurn()) {
            TurnEndResult turnEndResult = endCurrentTurn(signalSnapshot, transition, timestampMs);
            return new Observation(
                    transition,
                    turnEndResult.frontEndResetApplied,
                    turnEndResult.turnFinalization
            );
        }
        return Observation.none();
    }

    private void beginNewTurn(int turnSeedWpm, long timestampMs) {
        timingModel.beginNewTurn(turnSeedWpm, timestampMs);
        if (wpmGuard != null) {
            wpmGuard.beginNewTurn(turnSeedWpm, timestampMs);
        }
        if (timingAnchorController != null) {
            timingAnchorController.beginNewTurn(turnSeedWpm, timestampMs);
        }
        if (rawCommitGate != null) {
            rawCommitGate.beginNewTurn();
        }
        if (turnSessionFinalizer != null) {
            turnSessionFinalizer.beginNewTurn();
        }
        if (toneEventStabilizer != null) {
            toneEventStabilizer.reset();
        }
    }

    private TurnEndResult endCurrentTurn(
            CwSignalSnapshot signalSnapshot,
            RxTurnController.Transition transition,
            long timestampMs
    ) {
        if (turnEndListener != null) {
            turnEndListener.beforeTurnEnd(transition, timestampMs);
        }
        RxTurnSessionFinalizer.TurnFinalization turnFinalization = turnSessionFinalizer == null
                ? null
                : turnSessionFinalizer.finalizeCurrentTurn(timestampMs);
        boolean frontEndResetApplied = false;
        if (signalProcessor != null
                && RxTurnActivityDecider.shouldResetFrontEndOnTurnEnd(signalSnapshot)) {
            signalProcessor.reset();
            frontEndResetApplied = true;
        }
        if (toneEventStabilizer != null) {
            toneEventStabilizer.reset();
        }
        if (rawCommitGate != null) {
            rawCommitGate.endTurn();
        }
        if (turnSessionFinalizer != null) {
            turnSessionFinalizer.endTurn();
        }
        return new TurnEndResult(frontEndResetApplied, turnFinalization);
    }

    private static final class TurnEndResult {
        private final boolean frontEndResetApplied;
        @Nullable private final RxTurnSessionFinalizer.TurnFinalization turnFinalization;

        private TurnEndResult(
                boolean frontEndResetApplied,
                @Nullable RxTurnSessionFinalizer.TurnFinalization turnFinalization
        ) {
            this.frontEndResetApplied = frontEndResetApplied;
            this.turnFinalization = turnFinalization;
        }
    }
}
