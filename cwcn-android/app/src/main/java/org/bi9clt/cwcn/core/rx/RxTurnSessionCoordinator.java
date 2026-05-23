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
    private static final long MIN_STALE_ACTIVE_WITH_COMMITTED_DECODE_MS = 2600L;
    private static final double STALE_ACTIVE_WITH_COMMITTED_DECODE_DOT_RATIO = 44.0d;

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
        if (turnController.phase() == RxTurnController.Phase.ACTIVE
                && shouldForceEndStaleCommittedTurn(signalSnapshot, turnController, timestampMs)) {
            RxTurnController.Transition forcedEndTransition = turnController.forceEnd(
                    timestampMs,
                    "turn-end(stale-active-after-decode)"
            );
            TurnEndResult turnEndResult = endCurrentTurn(
                    signalSnapshot,
                    forcedEndTransition,
                    timestampMs
            );
            return new Observation(
                    forcedEndTransition,
                    turnEndResult.frontEndResetApplied,
                    turnEndResult.turnFinalization
            );
        }
        boolean turnActivity;
        if (turnController.phase() == RxTurnController.Phase.ACTIVE) {
            boolean hasCommittedDecodeThisTurn = turnSessionFinalizer != null
                    && turnSessionFinalizer.currentTurnHasCommittedDecodeEvents();
            turnActivity = !hasCommittedDecodeThisTurn && !hasPendingCharacter
                    ? RxTurnActivityDecider.isMeaningfulTurnBootstrapContinuation(signalSnapshot)
                    : RxTurnActivityDecider.isMeaningfulTurnPostDecodeContinuation(signalSnapshot);
        } else {
            turnActivity = turnController.lastTurnEndedAtMs() > 0L
                    ? RxTurnActivityDecider.isMeaningfulTurnRestartActivity(signalSnapshot)
                    : RxTurnActivityDecider.isMeaningfulTurnActivity(signalSnapshot);
        }
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

    private boolean shouldForceEndStaleCommittedTurn(
            CwSignalSnapshot signalSnapshot,
            RxTurnController turnController,
            long timestampMs
    ) {
        if (signalSnapshot == null
                || turnController == null
                || turnSessionFinalizer == null
                || !turnSessionFinalizer.currentTurnHasCommittedDecodeEvents()) {
            return false;
        }
        long decodeIdleMs = turnController.decodeIdleMs(timestampMs);
        long stableDecodeIdleMs = turnController.stableDecodeIdleMs(timestampMs);
        long staleCommittedTurnThresholdMs =
                resolveStaleCommittedTurnThresholdMs(turnController);
        if (decodeIdleMs < staleCommittedTurnThresholdMs) {
            return false;
        }
        if (stableDecodeIdleMs < staleCommittedTurnThresholdMs) {
            return false;
        }
        if (signalSnapshot.targetToneLocked()) {
            return false;
        }
        if (signalSnapshot.consecutiveLockedFrames() >= 2) {
            return false;
        }
        if (signalSnapshot.recentLockedFrameRatio() >= 0.08d) {
            return false;
        }
        if (signalSnapshot.toneDominanceRatio() >= 0.18d
                && signalSnapshot.narrowbandIsolationRatio() >= 0.18d) {
            return false;
        }
        return true;
    }

    private long resolveStaleCommittedTurnThresholdMs(RxTurnController turnController) {
        if (turnController == null) {
            return MIN_STALE_ACTIVE_WITH_COMMITTED_DECODE_MS;
        }
        int anchorWpm = turnController.currentTurnAnchorWpm();
        anchorWpm = Math.max(anchorWpm, turnController.retainedTurnAnchorWpm());
        if (anchorWpm <= 0) {
            return MIN_STALE_ACTIVE_WITH_COMMITTED_DECODE_MS;
        }
        long dotEstimateMs = Math.max(1L, Math.round(1200.0d / anchorWpm));
        return Math.max(
                MIN_STALE_ACTIVE_WITH_COMMITTED_DECODE_MS,
                Math.round(dotEstimateMs * STALE_ACTIVE_WITH_COMMITTED_DECODE_DOT_RATIO)
        );
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
