package org.bi9clt.cwcn.core.rx;

import java.util.Locale;

/**
 * Turn-based local learner boundary for RX.
 *
 * <p>RX should not hold one global timing context across the whole session.
 * Instead, it should learn within one incoming turn, drop that local memory
 * when the turn ends, and start the next turn from a fresh weak seed.</p>
 *
 * <p>The controller may still observe a stable anchor inside the active turn
 * for diagnostics, but that anchor must not leak into the next turn.</p>
 */
public final class RxTurnController {
    private static final long DEFAULT_TURN_END_SILENCE_MS = 2400L;
    private static final long MIN_TURN_END_SILENCE_MS = 1200L;
    private static final double TURN_END_DOT_RATIO = 24.0d;
    private static final double TURN_ANCHOR_BLEND = 0.30d;
    private static final double TURN_ANCHOR_MAX_UP_RATIO = 1.08d;
    private static final double TURN_ANCHOR_MAX_UP_WPM = 1.50d;
    private static final double TURN_ANCHOR_MAX_DOWN_RATIO = 0.84d;
    private static final double TURN_ANCHOR_MAX_DOWN_WPM = 3.00d;

    public enum Phase {
        IDLE,
        ACTIVE
    }

    public static final class Transition {
        private static final Transition NONE = new Transition(false, false, false, 0, "none");

        private final boolean startedNewTurn;
        private final boolean endedTurn;
        private final boolean shouldSoftResetLearner;
        private final int turnSeedWpm;
        private final String reason;

        private Transition(
                boolean startedNewTurn,
                boolean endedTurn,
                boolean shouldSoftResetLearner,
                int turnSeedWpm,
                String reason
        ) {
            this.startedNewTurn = startedNewTurn;
            this.endedTurn = endedTurn;
            this.shouldSoftResetLearner = shouldSoftResetLearner;
            this.turnSeedWpm = Math.max(0, turnSeedWpm);
            this.reason = reason == null ? "" : reason;
        }

        public static Transition none() {
            return NONE;
        }

        public boolean startedNewTurn() {
            return startedNewTurn;
        }

        public boolean endedTurn() {
            return endedTurn;
        }

        public boolean shouldSoftResetLearner() {
            return shouldSoftResetLearner;
        }

        public int turnSeedWpm() {
            return turnSeedWpm;
        }

        public String reason() {
            return reason;
        }
    }

    private Phase phase = Phase.IDLE;
    private int turnIndex;
    private int txSeedWpm;
    private boolean crossTurnCarryEnabled;
    private double retainedTurnAnchorWpm;
    private double currentTurnAnchorWpm;
    private int currentTurnStableDecodeCount;
    private int currentTurnRejectedAnchorCount;
    private boolean bootstrapAutoTrackFallbackLatched;
    private boolean bootstrapFixedProgressObservedThisTurn;
    private long trustedTimingEstablishedAtMs = -1L;
    private long currentTurnStartedAtMs = -1L;
    private long lastTurnActivityTimestampMs = -1L;
    private long lastTurnStableDecodeTimestampMs = -1L;
    private long lastTurnEndedAtMs = -1L;
    private String lastTransitionReason = "init";

    public void reset() {
        phase = Phase.IDLE;
        turnIndex = 0;
        retainedTurnAnchorWpm = 0.0d;
        currentTurnAnchorWpm = 0.0d;
        currentTurnStableDecodeCount = 0;
        currentTurnRejectedAnchorCount = 0;
        bootstrapAutoTrackFallbackLatched = false;
        bootstrapFixedProgressObservedThisTurn = false;
        trustedTimingEstablishedAtMs = -1L;
        currentTurnStartedAtMs = -1L;
        lastTurnActivityTimestampMs = -1L;
        lastTurnStableDecodeTimestampMs = -1L;
        lastTurnEndedAtMs = -1L;
        lastTransitionReason = "reset";
    }

    public void setTxSeedWpm(int wpm) {
        txSeedWpm = Math.max(0, wpm);
    }

    public void setCrossTurnCarryEnabled(boolean enabled) {
        crossTurnCarryEnabled = enabled;
        if (!enabled) {
            retainedTurnAnchorWpm = 0.0d;
        }
    }

    public Transition observe(
            boolean toneActive,
            boolean hasPendingCharacter,
            long timestampMs,
            int currentReferenceWpm
    ) {
        if (timestampMs <= 0L) {
            return Transition.none();
        }
        if (phase == Phase.IDLE) {
            if (!toneActive && !hasPendingCharacter) {
                return Transition.none();
            }
            phase = Phase.ACTIVE;
            turnIndex += 1;
            currentTurnStartedAtMs = timestampMs;
            lastTurnActivityTimestampMs = timestampMs;
            lastTurnStableDecodeTimestampMs = -1L;
            currentTurnAnchorWpm = 0.0d;
            currentTurnStableDecodeCount = 0;
            currentTurnRejectedAnchorCount = 0;
            bootstrapAutoTrackFallbackLatched = false;
            bootstrapFixedProgressObservedThisTurn = false;
            trustedTimingEstablishedAtMs = -1L;
            int turnSeedWpm = deriveTurnSeedWpm(currentReferenceWpm);
            lastTransitionReason = "turn-start(seed=" + turnSeedWpm
                    + ",retained=" + formatWpm(retainedTurnAnchorWpm)
                    + ",tx=" + txSeedWpm + ")";
            return new Transition(
                    true,
                    false,
                    true,
                    turnSeedWpm,
                    lastTransitionReason
            );
        }

        if (toneActive || hasPendingCharacter) {
            lastTurnActivityTimestampMs = timestampMs;
            return Transition.none();
        }

        long silenceMs = lastTurnActivityTimestampMs <= 0L
                ? Long.MAX_VALUE
                : Math.max(0L, timestampMs - lastTurnActivityTimestampMs);
        long turnEndSilenceMs = resolveTurnEndSilenceMs(currentReferenceWpm);
        if (silenceMs < turnEndSilenceMs) {
            return Transition.none();
        }

        retainedTurnAnchorWpm = deriveRetainedTurnAnchorWpm();
        phase = Phase.IDLE;
        lastTurnEndedAtMs = timestampMs;
        lastTransitionReason = "turn-end(carry=" + formatWpm(retainedTurnAnchorWpm)
                + ",silence=" + silenceMs + "ms)";
        currentTurnStartedAtMs = -1L;
        lastTurnActivityTimestampMs = -1L;
        lastTurnStableDecodeTimestampMs = -1L;
        currentTurnAnchorWpm = 0.0d;
        currentTurnStableDecodeCount = 0;
        currentTurnRejectedAnchorCount = 0;
        bootstrapAutoTrackFallbackLatched = false;
        bootstrapFixedProgressObservedThisTurn = false;
        trustedTimingEstablishedAtMs = -1L;
        return new Transition(
                false,
                true,
                false,
                0,
                lastTransitionReason
        );
    }

    public void noteStableDecode(long timestampMs, int anchorWpm, boolean carryEligible) {
        if (phase != Phase.ACTIVE || timestampMs <= 0L || anchorWpm <= 0) {
            return;
        }
        lastTurnActivityTimestampMs = timestampMs;
        lastTurnStableDecodeTimestampMs = timestampMs;
        if (!carryEligible) {
            return;
        }
        if (currentTurnAnchorWpm <= 0.0d) {
            currentTurnAnchorWpm = anchorWpm;
            currentTurnStableDecodeCount = 1;
            return;
        }
        double anchorUpperBound = Math.min(
                currentTurnAnchorWpm + TURN_ANCHOR_MAX_UP_WPM,
                currentTurnAnchorWpm * TURN_ANCHOR_MAX_UP_RATIO
        );
        double anchorLowerBound = Math.max(
                currentTurnAnchorWpm - TURN_ANCHOR_MAX_DOWN_WPM,
                currentTurnAnchorWpm * TURN_ANCHOR_MAX_DOWN_RATIO
        );
        if (anchorWpm > anchorUpperBound || anchorWpm < anchorLowerBound) {
            currentTurnRejectedAnchorCount += 1;
            return;
        }
        currentTurnAnchorWpm = blend(
                currentTurnAnchorWpm,
                anchorWpm,
                TURN_ANCHOR_BLEND
        );
        currentTurnStableDecodeCount += 1;
    }

    public Phase phase() {
        return phase;
    }

    public int turnIndex() {
        return turnIndex;
    }

    public int retainedTurnAnchorWpm() {
        return (int) Math.round(Math.max(0.0d, retainedTurnAnchorWpm));
    }

    public int currentTurnAnchorWpm() {
        return (int) Math.round(Math.max(0.0d, currentTurnAnchorWpm));
    }

    public long currentTurnStartedAtMs() {
        return currentTurnStartedAtMs;
    }

    public long lastTurnEndedAtMs() {
        return lastTurnEndedAtMs;
    }

    public boolean bootstrapAutoTrackFallbackLatched() {
        return phase == Phase.ACTIVE && bootstrapAutoTrackFallbackLatched;
    }

    public void latchBootstrapAutoTrackFallback() {
        if (phase == Phase.ACTIVE) {
            bootstrapAutoTrackFallbackLatched = true;
        }
    }

    public boolean bootstrapFixedProgressObservedThisTurn() {
        return phase == Phase.ACTIVE && bootstrapFixedProgressObservedThisTurn;
    }

    public void noteBootstrapFixedProgressObserved() {
        if (phase == Phase.ACTIVE) {
            bootstrapFixedProgressObservedThisTurn = true;
        }
    }

    public void noteTrustedTimingEstablished(long timestampMs) {
        if (phase != Phase.ACTIVE || timestampMs <= 0L) {
            return;
        }
        if (trustedTimingEstablishedAtMs <= 0L) {
            trustedTimingEstablishedAtMs = timestampMs;
        }
    }

    public long trustedTimingEstablishedAtMs() {
        return phase == Phase.ACTIVE ? trustedTimingEstablishedAtMs : -1L;
    }

    public String lastTransitionReason() {
        return lastTransitionReason;
    }

    public String compactDebugSummary() {
        return "turn "
                + phase.name().toLowerCase(Locale.US)
                + "#"
                + turnIndex
                + " cur="
                + positiveOrDash(currentTurnAnchorWpm())
                + " ret="
                + positiveOrDash(retainedTurnAnchorWpm())
                + " tx="
                + positiveOrDash(txSeedWpm);
    }

    private int deriveTurnSeedWpm(int currentReferenceWpm) {
        if (crossTurnCarryEnabled && retainedTurnAnchorWpm > 0.0d) {
            return (int) Math.round(retainedTurnAnchorWpm);
        }
        if (txSeedWpm > 0) {
            return txSeedWpm;
        }
        return 0;
    }

    private double deriveRetainedTurnAnchorWpm() {
        if (crossTurnCarryEnabled
                && currentTurnAnchorWpm > 0.0d
                && currentTurnStableDecodeCount > 0) {
            return currentTurnAnchorWpm;
        }
        return 0.0d;
    }

    private long resolveTurnEndSilenceMs(int currentReferenceWpm) {
        long candidateMs = DEFAULT_TURN_END_SILENCE_MS;
        if (currentReferenceWpm > 0) {
            long dotEstimateMs = Math.max(1L, Math.round(1200.0d / currentReferenceWpm));
            candidateMs = Math.max(
                    MIN_TURN_END_SILENCE_MS,
                    Math.round(dotEstimateMs * TURN_END_DOT_RATIO)
            );
        }
        return Math.max(DEFAULT_TURN_END_SILENCE_MS, candidateMs);
    }

    private static double blend(double currentValue, double candidateValue, double amount) {
        double boundedAmount = Math.max(0.0d, Math.min(1.0d, amount));
        return (currentValue * (1.0d - boundedAmount)) + (candidateValue * boundedAmount);
    }

    private static String formatWpm(double value) {
        if (value <= 0.0d) {
            return "-";
        }
        return String.format(Locale.US, "%.1f", value);
    }

    private static String positiveOrDash(int value) {
        return value > 0 ? String.valueOf(value) : "-";
    }
}
