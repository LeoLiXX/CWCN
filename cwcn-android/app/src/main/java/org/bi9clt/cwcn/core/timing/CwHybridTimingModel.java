package org.bi9clt.cwcn.core.timing;

import org.bi9clt.cwcn.core.signal.CwToneEvent;

import java.util.List;
import java.util.Locale;

public final class CwHybridTimingModel {
    private static final int ADAPTIVE_PROMOTION_MIN_WPM = 19;
    private static final int ADAPTIVE_PROMOTION_WPM_LEAD = 7;
    private static final int ADAPTIVE_PROMOTION_BASELINE_MAX_WPM = 20;
    private static final int BASELINE_RESTORE_MARGIN_WPM = 2;
    private static final long TRUSTED_IDLE_RELEASE_MS = 4200L;
    private static final double TRUSTED_FAST_FLOOR_RATIO = 0.92d;
    private static final double TRUSTED_FAST_DIRECT_UPDATE_RATIO = 0.97d;
    private static final double TRUSTED_FAST_REANCHOR_RATIO = 0.92d;
    private static final double TRUSTED_FAST_DIRECT_UPDATE_BLEND = 0.08d;
    private static final double TRUSTED_FAST_REANCHOR_BLEND = 0.10d;
    private static final double TRUSTED_SLOW_UPDATE_BLEND = 0.18d;
    private static final double TRUSTED_FLOOR_RAW_DASH_PRESERVE_MIN_RATIO = 1.55d;
    private static final double TRUSTED_FLOOR_RAW_LETTER_GAP_PRESERVE_MIN_RATIO = 1.65d;
    private static final int TRUSTED_FAST_REANCHOR_REQUIRED_STABLE_DECODES = 6;
    private static final double TRUSTED_FAST_REANCHOR_MATCH_RATIO = 0.05d;
    private static final int INITIAL_TRUSTED_REQUIRED_STABLE_DECODES = 3;
    private static final long INITIAL_TRUSTED_CANDIDATE_WINDOW_MS = 2600L;
    private static final double INITIAL_TRUSTED_BOOTSTRAP_MAX_SPREAD_WPM = 4.5d;
    private static final int BOUNDARY_BOOTSTRAP_REQUIRED_STABLE_DECODES = 2;
    private static final long BOUNDARY_BOOTSTRAP_CANDIDATE_WINDOW_MS = 1800L;
    private static final double BOUNDARY_BOOTSTRAP_MAX_SPREAD_WPM = 5.0d;
    private static final int MIXED_BOUNDARY_STABLE_REQUIRED_BOUNDARY_OBSERVATIONS = 1;
    private static final int MIXED_BOUNDARY_STABLE_REQUIRED_STABLE_OBSERVATIONS = 1;
    private static final long MIXED_BOUNDARY_STABLE_CANDIDATE_WINDOW_MS = 1800L;
    private static final double MIXED_BOUNDARY_STABLE_MAX_SPREAD_WPM = 4.0d;
    private static final long MIXED_BOUNDARY_STABLE_MIN_DOT_MS = 48L;
    private static final double BOUNDARY_BOOTSTRAP_STABLE_FLOOR_RATIO = 0.72d;
    private static final int CADENCE_BOOTSTRAP_REQUIRED_OBSERVATIONS = 3;
    private static final long CADENCE_BOOTSTRAP_CANDIDATE_WINDOW_MS = 1400L;
    private static final double CADENCE_BOOTSTRAP_MAX_SPREAD_WPM = 3.0d;
    private static final int CADENCE_OPENING_RELAX_WINDOW_DOTS = 24;
    private static final int MAX_PENDING_BOOTSTRAP_CANDIDATES = 3;
    private static final long PRE_TRUST_BOOTSTRAP_WINDOW_MS = 2200L;
    private static final int PRE_TRUST_BOOTSTRAP_MAX_EVENT_COUNT = 8;
    private static final double PRE_TRUST_BOOTSTRAP_BOUNDARY_WEIGHT = 1.15d;
    private static final double PRE_TRUST_BOOTSTRAP_CADENCE_WEIGHT = 1.00d;
    private static final double PRE_TRUST_BOOTSTRAP_STABLE_WEIGHT = 0.80d;
    private static final double PRE_TRUST_BOOTSTRAP_SEED_WEIGHT = 0.22d;
    private static final double BOOTSTRAP_SEED_SLOW_EXCESS_BLEND = 0.15d;
    private static final double PRE_TRUST_OPENING_TONE_OUTLIER_RATIO = 5.2d;
    private static final int PRE_TRUST_OPENING_TONE_GUARD_MAX_EVENT_COUNT = 8;
    private static final double PRE_TRUST_OPENING_TONE_GUARD_RELEASE_RATIO = 1.25d;
    private static final long FAST_DOT_THRESHOLD_MS = 55L;
    private static final double HYBRID_WORD_GAP_INTRA_RATIO_FALLBACK = 5.0d;
    private static final double HYBRID_WORD_GAP_DOT_RATIO_FALLBACK_MIN = 4.55d;
    private static final long RETAINED_IDLE_RELEASE_MS = 30000L;

    private enum Strategy {
        BASELINE,
        ADAPTIVE
    }

    private enum TrustOrigin {
        NONE,
        STABLE,
        BOUNDARY,
        CADENCE
    }

    public static final class ExperimentalTrustedSlowUpdateTuning {
        private final boolean boundarySlowUpdateEnabled;
        private final double boundarySlowUpdateBlend;
        private final boolean stableSlowUpdateEnabled;
        private final double stableSlowUpdateBlend;
        private final double maxPostTrustSlowUpRatio;

        public ExperimentalTrustedSlowUpdateTuning(
                boolean boundarySlowUpdateEnabled,
                double boundarySlowUpdateBlend,
                boolean stableSlowUpdateEnabled,
                double stableSlowUpdateBlend,
                double maxPostTrustSlowUpRatio
        ) {
            this.boundarySlowUpdateEnabled = boundarySlowUpdateEnabled;
            this.boundarySlowUpdateBlend = clampUnitInterval(boundarySlowUpdateBlend);
            this.stableSlowUpdateEnabled = stableSlowUpdateEnabled;
            this.stableSlowUpdateBlend = clampUnitInterval(stableSlowUpdateBlend);
            this.maxPostTrustSlowUpRatio = maxPostTrustSlowUpRatio <= 1.0d
                    ? Double.POSITIVE_INFINITY
                    : maxPostTrustSlowUpRatio;
        }

        public boolean boundarySlowUpdateEnabled() {
            return boundarySlowUpdateEnabled;
        }

        public double boundarySlowUpdateBlend() {
            return boundarySlowUpdateBlend;
        }

        public boolean stableSlowUpdateEnabled() {
            return stableSlowUpdateEnabled;
        }

        public double stableSlowUpdateBlend() {
            return stableSlowUpdateBlend;
        }

        public double maxPostTrustSlowUpRatio() {
            return maxPostTrustSlowUpRatio;
        }

        private static double clampUnitInterval(double value) {
            if (Double.isNaN(value)) {
                return 0.0d;
            }
            return Math.max(0.0d, Math.min(1.0d, value));
        }
    }

    public static final class DebugSnapshot {
        private final String activeStrategyName;
        private final String lastEmissionStrategyName;
        private final CwTimingSnapshot baselineSnapshot;
        private final CwTimingSnapshot adaptiveSnapshot;
        private final CwTimingSnapshot emissionSnapshot;
        private final int seedWpm;
        private final int adaptivePromotionCount;
        private final int adaptiveEmissionBatches;
        private final double retainedDotEstimateMs;
        private final double trustedDotEstimateMs;
        private final double pendingFastTrustedDotEstimateMs;
        private final int pendingFastTrustedEvidenceCount;
        private final long lastStableDecodeTimestampMs;
        private final long lastObservationTimestampMs;
        private final long lastTimingActivityTimestampMs;
        private final long lastRetainedUpdateTimestampMs;
        private final long lastTrustedUpdateTimestampMs;
        private final long lastResetTimestampMs;
        private final long lastObservationSummaryTimestampMs;
        private final String lastStrategyDecision;
        private final String lastTrustedUpdateReason;
        private final String lastResetReason;
        private final String lastObservationSummary;

        private DebugSnapshot(
                String activeStrategyName,
                String lastEmissionStrategyName,
                CwTimingSnapshot baselineSnapshot,
                CwTimingSnapshot adaptiveSnapshot,
                CwTimingSnapshot emissionSnapshot,
                int seedWpm,
                int adaptivePromotionCount,
                int adaptiveEmissionBatches,
                double retainedDotEstimateMs,
                double trustedDotEstimateMs,
                double pendingFastTrustedDotEstimateMs,
                int pendingFastTrustedEvidenceCount,
                long lastStableDecodeTimestampMs,
                long lastObservationTimestampMs,
                long lastTimingActivityTimestampMs,
                long lastRetainedUpdateTimestampMs,
                long lastTrustedUpdateTimestampMs,
                long lastResetTimestampMs,
                long lastObservationSummaryTimestampMs,
                String lastStrategyDecision,
                String lastTrustedUpdateReason,
                String lastResetReason,
                String lastObservationSummary
        ) {
            this.activeStrategyName = activeStrategyName;
            this.lastEmissionStrategyName = lastEmissionStrategyName;
            this.baselineSnapshot = baselineSnapshot;
            this.adaptiveSnapshot = adaptiveSnapshot;
            this.emissionSnapshot = emissionSnapshot;
            this.seedWpm = seedWpm;
            this.adaptivePromotionCount = adaptivePromotionCount;
            this.adaptiveEmissionBatches = adaptiveEmissionBatches;
            this.retainedDotEstimateMs = retainedDotEstimateMs;
            this.trustedDotEstimateMs = trustedDotEstimateMs;
            this.pendingFastTrustedDotEstimateMs = pendingFastTrustedDotEstimateMs;
            this.pendingFastTrustedEvidenceCount = pendingFastTrustedEvidenceCount;
            this.lastStableDecodeTimestampMs = lastStableDecodeTimestampMs;
            this.lastObservationTimestampMs = lastObservationTimestampMs;
            this.lastTimingActivityTimestampMs = lastTimingActivityTimestampMs;
            this.lastRetainedUpdateTimestampMs = lastRetainedUpdateTimestampMs;
            this.lastTrustedUpdateTimestampMs = lastTrustedUpdateTimestampMs;
            this.lastResetTimestampMs = lastResetTimestampMs;
            this.lastObservationSummaryTimestampMs = lastObservationSummaryTimestampMs;
            this.lastStrategyDecision = lastStrategyDecision;
            this.lastTrustedUpdateReason = lastTrustedUpdateReason;
            this.lastResetReason = lastResetReason;
            this.lastObservationSummary = lastObservationSummary;
        }

        public String activeStrategyName() {
            return activeStrategyName;
        }

        public String lastEmissionStrategyName() {
            return lastEmissionStrategyName;
        }

        public CwTimingSnapshot baselineSnapshot() {
            return baselineSnapshot;
        }

        public CwTimingSnapshot adaptiveSnapshot() {
            return adaptiveSnapshot;
        }

        public CwTimingSnapshot emissionSnapshot() {
            return emissionSnapshot;
        }

        public int seedWpm() {
            return seedWpm;
        }

        public int adaptivePromotionCount() {
            return adaptivePromotionCount;
        }

        public int adaptiveEmissionBatches() {
            return adaptiveEmissionBatches;
        }

        public double retainedDotEstimateMs() {
            return retainedDotEstimateMs;
        }

        public double trustedDotEstimateMs() {
            return trustedDotEstimateMs;
        }

        public double pendingFastTrustedDotEstimateMs() {
            return pendingFastTrustedDotEstimateMs;
        }

        public int pendingFastTrustedEvidenceCount() {
            return pendingFastTrustedEvidenceCount;
        }

        public long lastStableDecodeTimestampMs() {
            return lastStableDecodeTimestampMs;
        }

        public long lastObservationTimestampMs() {
            return lastObservationTimestampMs;
        }

        public long lastTimingActivityTimestampMs() {
            return lastTimingActivityTimestampMs;
        }

        public long lastRetainedUpdateTimestampMs() {
            return lastRetainedUpdateTimestampMs;
        }

        public long lastTrustedUpdateTimestampMs() {
            return lastTrustedUpdateTimestampMs;
        }

        public long lastResetTimestampMs() {
            return lastResetTimestampMs;
        }

        public long lastObservationSummaryTimestampMs() {
            return lastObservationSummaryTimestampMs;
        }

        public String lastStrategyDecision() {
            return lastStrategyDecision;
        }

        public String lastTrustedUpdateReason() {
            return lastTrustedUpdateReason;
        }

        public String lastResetReason() {
            return lastResetReason;
        }

        public String lastObservationSummary() {
            return lastObservationSummary;
        }

        public double trustedFastFloorMs() {
            if (trustedDotEstimateMs <= 0.0d) {
                return 0.0d;
            }
            return trustedDotEstimateMs * TRUSTED_FAST_FLOOR_RATIO;
        }

        public boolean trustedFastFloorActive() {
            if (emissionSnapshot == null || emissionSnapshot.dotEstimateMs() <= 0L) {
                return false;
            }
            double trustedFastFloorMs = trustedFastFloorMs();
            return trustedFastFloorMs > 0.0d && emissionSnapshot.dotEstimateMs() < trustedFastFloorMs;
        }
    }

    private final CwTimingModel baseline = new CwTimingModel();
    private final CwAdaptiveTimingModel adaptive = new CwAdaptiveTimingModel();
    private Strategy activeStrategy = Strategy.BASELINE;
    private Strategy lastEmissionStrategy = Strategy.BASELINE;
    private int adaptivePromotionCount;
    private int adaptiveEmissionBatches;
    private int seedWpm;
    private double retainedDotEstimateMs;
    private double trustedDotEstimateMs;
    private long lastStableDecodeTimestampMs = -1L;
    private long lastObservationTimestampMs = -1L;
    private long lastTimingActivityTimestampMs = -1L;
    private long lastRetainedUpdateTimestampMs = -1L;
    private final PendingBootstrapCandidates pendingStableTrustedCandidates =
            new PendingBootstrapCandidates(MAX_PENDING_BOOTSTRAP_CANDIDATES);
    private final PendingBootstrapCandidates pendingSoftStableTrustedCandidates =
            new PendingBootstrapCandidates(MAX_PENDING_BOOTSTRAP_CANDIDATES);
    private final PendingBootstrapCandidates pendingBoundaryTrustedCandidates =
            new PendingBootstrapCandidates(MAX_PENDING_BOOTSTRAP_CANDIDATES);
    private final PendingBootstrapCandidates pendingCadenceTrustedCandidates =
            new PendingBootstrapCandidates(MAX_PENDING_BOOTSTRAP_CANDIDATES);
    private double pendingFastTrustedDotEstimateMs;
    private int pendingFastTrustedEvidenceCount;
    private String lastStrategyDecision = "init";
    private String lastTrustedUpdateReason = "none";
    private long lastTrustedUpdateTimestampMs = -1L;
    private String lastResetReason = "none";
    private long lastResetTimestampMs = -1L;
    private String lastObservationSummary = "none";
    private long lastObservationSummaryTimestampMs = -1L;
    private boolean preTrustOpeningToneGuardActive;
    private TrustOrigin trustOrigin = TrustOrigin.NONE;
    private int postTrustStableDecodeCount;
    private boolean idleResetEnabled = true;
    private ExperimentalTrustedSlowUpdateTuning experimentalTrustedSlowUpdateTuning;

    public synchronized List<CwTimingEvent> process(CwToneEvent toneEvent) {
        return process(toneEvent, true);
    }

    public synchronized List<CwTimingEvent> process(CwToneEvent toneEvent, boolean allowLearning) {
        lastObservationTimestampMs = Math.max(lastObservationTimestampMs, toneEvent.timestampMs());
        maybeResetAfterExtendedIdle(toneEvent.timestampMs());
        List<CwTimingEvent> baselineEvents = baseline.process(toneEvent, allowLearning);
        List<CwTimingEvent> adaptiveEvents = adaptive.process(toneEvent, allowLearning);
        refreshStrategy();
        List<CwTimingEvent> outputEvents = selectOutputEvents(baselineEvents, adaptiveEvents);
        noteTimingActivity(outputEvents);
        List<CwTimingEvent> stabilizedEvents = stabilizeOutputEvents(
                outputEvents,
                emissionStrategySnapshot(),
                toneEvent.timestampMs()
        );
        noteObservation(toneEvent, allowLearning, stabilizedEvents);
        return stabilizedEvents;
    }

    public synchronized List<CwTimingEvent> flushPendingGap(long timestampMs) {
        return flushPendingGap(timestampMs, true);
    }

    public synchronized List<CwTimingEvent> flushPendingGap(long timestampMs, boolean allowLearning) {
        lastObservationTimestampMs = Math.max(lastObservationTimestampMs, timestampMs);
        maybeResetAfterExtendedIdle(timestampMs);
        List<CwTimingEvent> baselineEvents = baseline.flushPendingGap(timestampMs, allowLearning);
        List<CwTimingEvent> adaptiveEvents = adaptive.flushPendingGap(timestampMs, allowLearning);
        refreshStrategy();
        List<CwTimingEvent> outputEvents = selectOutputEvents(baselineEvents, adaptiveEvents);
        noteTimingActivity(outputEvents);
        List<CwTimingEvent> stabilizedEvents = stabilizeOutputEvents(
                outputEvents,
                emissionStrategySnapshot(),
                timestampMs
        );
        noteFlushObservation(timestampMs, allowLearning, stabilizedEvents);
        return stabilizedEvents;
    }

    public synchronized void observeClock(long timestampMs) {
        if (timestampMs <= 0L) {
            return;
        }
        lastObservationTimestampMs = Math.max(lastObservationTimestampMs, timestampMs);
        maybeResetAfterExtendedIdle(timestampMs);
    }

    /**
     * Controls whether this model may demote its trusted timing state after a
     * long silent span without any explicit external turn/session reset.
     *
     * <p>Turn-scoped RX pipelines should generally disable this and let their
     * turn controller own lifecycle resets. That avoids conflicting "mid-turn"
     * trust drops during long but still-continuous within-turn pauses.</p>
     */
    public synchronized void setIdleResetEnabled(boolean enabled) {
        idleResetEnabled = enabled;
    }

    public synchronized void setExperimentalTrustedSlowUpdateTuning(
            ExperimentalTrustedSlowUpdateTuning tuning
    ) {
        experimentalTrustedSlowUpdateTuning = tuning;
    }

    public synchronized void reset() {
        long resetTimestampMs = effectiveTimestamp();
        baseline.reset();
        adaptive.reset();
        activeStrategy = Strategy.BASELINE;
        lastEmissionStrategy = Strategy.BASELINE;
        adaptivePromotionCount = 0;
        adaptiveEmissionBatches = 0;
        retainedDotEstimateMs = 0.0d;
        trustedDotEstimateMs = 0.0d;
        lastStableDecodeTimestampMs = -1L;
        lastObservationTimestampMs = -1L;
        lastTimingActivityTimestampMs = -1L;
        lastRetainedUpdateTimestampMs = -1L;
        clearPendingBootstrapCandidates();
        pendingFastTrustedDotEstimateMs = 0.0d;
        pendingFastTrustedEvidenceCount = 0;
        preTrustOpeningToneGuardActive = false;
        trustOrigin = TrustOrigin.NONE;
        postTrustStableDecodeCount = 0;
        recordReset("external-reset", resetTimestampMs);
        lastStrategyDecision = "reset:external-reset";
        recordTrustedUpdate("cleared:external-reset", resetTimestampMs);
        recordObservation("reset:external-reset", resetTimestampMs);
    }

    public synchronized void softReset() {
        long resetTimestampMs = effectiveTimestamp();
        baseline.reset();
        adaptive.reset();
        activeStrategy = Strategy.BASELINE;
        lastEmissionStrategy = Strategy.BASELINE;
        adaptivePromotionCount = 0;
        adaptiveEmissionBatches = 0;
        if (trustedDotEstimateMs > 0.0d) {
            rememberRetainedDotEstimate(trustedDotEstimateMs, resetTimestampMs);
        }
        lastTimingActivityTimestampMs = -1L;
        clearPendingBootstrapCandidates();
        clearPendingFastReanchor();
        preTrustOpeningToneGuardActive = false;
        recordReset("soft-reset", resetTimestampMs);
        lastStrategyDecision = "reset:soft-reset";
        recordObservation("reset:soft-reset", resetTimestampMs);
    }

    public synchronized void beginNewTurn(int turnSeedWpm, long timestampMs) {
        seedWpm = Math.max(0, turnSeedWpm);
        long resetTimestampMs = timestampMs > 0L ? timestampMs : effectiveTimestamp();
        baseline.reset();
        adaptive.reset();
        activeStrategy = Strategy.BASELINE;
        lastEmissionStrategy = Strategy.BASELINE;
        adaptivePromotionCount = 0;
        adaptiveEmissionBatches = 0;
        retainedDotEstimateMs = 0.0d;
        trustedDotEstimateMs = 0.0d;
        lastStableDecodeTimestampMs = -1L;
        lastTimingActivityTimestampMs = -1L;
        lastRetainedUpdateTimestampMs = -1L;
        clearPendingBootstrapCandidates();
        pendingFastTrustedDotEstimateMs = 0.0d;
        pendingFastTrustedEvidenceCount = 0;
        preTrustOpeningToneGuardActive = false;
        trustOrigin = TrustOrigin.NONE;
        postTrustStableDecodeCount = 0;
        recordReset("turn-reset", resetTimestampMs);
        lastStrategyDecision = "reset:turn-reset";
        recordTrustedUpdate("cleared:turn-reset", resetTimestampMs);
        recordObservation("reset:turn-reset", resetTimestampMs);
    }

    public synchronized CwTimingSnapshot snapshot() {
        return stabilizedSnapshot(snapshotSource(), effectiveTimestamp());
    }

    public synchronized CwTimingSnapshot rawSnapshot() {
        return activeStrategy == Strategy.ADAPTIVE
                ? adaptive.snapshot()
                : baseline.snapshot();
    }

    public synchronized String activeStrategyName() {
        return activeStrategy.name();
    }

    public synchronized DebugSnapshot debugSnapshot() {
        return new DebugSnapshot(
                activeStrategy.name(),
                lastEmissionStrategy.name(),
                baseline.snapshot(),
                adaptive.snapshot(),
                emissionStrategySnapshot(),
                seedWpm,
                adaptivePromotionCount,
                adaptiveEmissionBatches,
                retainedDotEstimateMs,
                trustedDotEstimateMs,
                pendingFastTrustedDotEstimateMs,
                pendingFastTrustedEvidenceCount,
                lastStableDecodeTimestampMs,
                lastObservationTimestampMs,
                lastTimingActivityTimestampMs,
                lastRetainedUpdateTimestampMs,
                lastTrustedUpdateTimestampMs,
                lastResetTimestampMs,
                lastObservationSummaryTimestampMs,
                lastStrategyDecision,
                lastTrustedUpdateReason,
                lastResetReason,
                lastObservationSummary
        );
    }

    public synchronized String debugStrategySummary() {
        CwTimingSnapshot baselineSnapshot = baseline.snapshot();
        CwTimingSnapshot adaptiveSnapshot = adaptive.snapshot();
        long referenceTimestampMs = effectiveTimestamp();
        return "act="
                + activeStrategy.name()
                + " last="
                + lastEmissionStrategy.name()
                + " b="
                + formatWpm(snapshotWpm(baselineSnapshot))
                + " a="
                + formatWpm(snapshotWpm(adaptiveSnapshot))
                + " sd="
                + seedWpm
                + " pr="
                + adaptivePromotionCount
                + " ab="
                + adaptiveEmissionBatches
                + " tr="
                + renderTrustedSummary()
                + " p="
                + renderPendingFastReanchorSummary()
                + " tu="
                + renderEventSummary(lastTrustedUpdateReason, lastTrustedUpdateTimestampMs, referenceTimestampMs)
                + " rs="
                + renderEventSummary(lastResetReason, lastResetTimestampMs, referenceTimestampMs)
                + " sw="
                + lastStrategyDecision
                + " obs="
                + renderEventSummary(lastObservationSummary, lastObservationSummaryTimestampMs, referenceTimestampMs);
    }

    public synchronized void setSeedWpm(int wpm) {
        seedWpm = Math.max(0, wpm);
    }

    public synchronized void notifyStableDecode(long timestampMs) {
        boolean trustedBefore = trustedDotEstimateMs > 0.0d;
        noteStableObservation(
                timestampMs,
                pendingStableTrustedCandidates,
                INITIAL_TRUSTED_REQUIRED_STABLE_DECODES,
                INITIAL_TRUSTED_CANDIDATE_WINDOW_MS,
                INITIAL_TRUSTED_BOOTSTRAP_MAX_SPREAD_WPM,
                "pending-init",
                "init",
                TrustOrigin.STABLE
        );
        if (trustedBefore && trustedDotEstimateMs > 0.0d) {
            postTrustStableDecodeCount += 1;
        }
    }

    public synchronized void noteBootstrapBoundaryObservation(long timestampMs) {
        noteStableObservation(
                timestampMs,
                pendingBoundaryTrustedCandidates,
                BOUNDARY_BOOTSTRAP_REQUIRED_STABLE_DECODES,
                BOUNDARY_BOOTSTRAP_CANDIDATE_WINDOW_MS,
                BOUNDARY_BOOTSTRAP_MAX_SPREAD_WPM,
                "pending-boundary",
                "init-boundary",
                TrustOrigin.BOUNDARY
        );
    }

    public synchronized void noteBootstrapBoundaryObservation(
            long candidateDotEstimateMs,
            long timestampMs
    ) {
        if (trustedDotEstimateMs > 0.0d) {
            if (candidateDotEstimateMs > trustedDotEstimateMs) {
                if (!shouldAllowBoundarySlowUpdate(candidateDotEstimateMs)) {
                    recordTrustedUpdate(
                            "boundary-slow-block(" + Math.round(trustedDotEstimateMs) + "->"
                                    + candidateDotEstimateMs + "ms)",
                            timestampMs
                    );
                    return;
                }
                double previousTrustedDotEstimateMs = trustedDotEstimateMs;
                trustedDotEstimateMs = blend(
                        trustedDotEstimateMs,
                        candidateDotEstimateMs,
                        resolveBoundarySlowUpdateBlend()
                );
                rememberRetainedDotEstimate(trustedDotEstimateMs, timestampMs);
                clearPendingFastReanchor();
                recordTrustedUpdate(
                        "boundary-slow-up(" + Math.round(previousTrustedDotEstimateMs) + "->"
                                + Math.round(trustedDotEstimateMs) + "ms)",
                        timestampMs
                );
            } else {
                recordTrustedUpdate("skip-explicit-trusted", timestampMs);
            }
            return;
        }
        if (violatesStableBootstrapBoundaryFloor(candidateDotEstimateMs)) {
            recordTrustedUpdate(
                    "reject-boundary-fast("
                            + candidateDotEstimateMs
                            + "ms<"
                            + Math.round(resolveStableBootstrapBoundaryFloorDotEstimateMs())
                            + "ms)",
                    timestampMs
            );
            return;
        }
        noteExplicitBootstrapObservation(
                candidateDotEstimateMs,
                timestampMs,
                pendingBoundaryTrustedCandidates,
                BOUNDARY_BOOTSTRAP_REQUIRED_STABLE_DECODES,
                BOUNDARY_BOOTSTRAP_CANDIDATE_WINDOW_MS,
                BOUNDARY_BOOTSTRAP_MAX_SPREAD_WPM,
                "pending-boundary",
                "init-boundary",
                TrustOrigin.BOUNDARY
        );
    }

    public synchronized void noteBootstrapCadenceObservation(long candidateDotEstimateMs, long timestampMs) {
        noteExplicitBootstrapObservation(
                candidateDotEstimateMs,
                timestampMs,
                pendingCadenceTrustedCandidates,
                CADENCE_BOOTSTRAP_REQUIRED_OBSERVATIONS,
                CADENCE_BOOTSTRAP_CANDIDATE_WINDOW_MS,
                CADENCE_BOOTSTRAP_MAX_SPREAD_WPM,
                "pending-cadence",
                "init-cadence",
                TrustOrigin.CADENCE
        );
    }

    public synchronized void noteBootstrapSoftStableObservation(
            long candidateDotEstimateMs,
            long timestampMs
    ) {
        if (candidateDotEstimateMs <= 0L) {
            recordTrustedUpdate("skip-soft-empty", timestampMs);
            return;
        }
        lastObservationTimestampMs = Math.max(lastObservationTimestampMs, timestampMs);
        maybeResetAfterExtendedIdle(timestampMs);
        maybeExpirePendingBootstrapCandidates(
                pendingSoftStableTrustedCandidates,
                timestampMs,
                MIXED_BOUNDARY_STABLE_CANDIDATE_WINDOW_MS
        );
        if (trustedDotEstimateMs > 0.0d) {
            recordTrustedUpdate("skip-soft-trusted", timestampMs);
            return;
        }
        pendingSoftStableTrustedCandidates.append(candidateDotEstimateMs, timestampMs);
        if (tryInitializeMixedBoundaryStableBootstrap(timestampMs)) {
            return;
        }
        recordTrustedUpdate("pending-soft-stable(" + candidateDotEstimateMs + "ms)", timestampMs);
    }

    private void noteStableObservation(
            long timestampMs,
            PendingBootstrapCandidates pendingCandidates,
            int initialTrustedRequiredStableDecodes,
            long initialTrustedCandidateWindowMs,
            double initialTrustedBootstrapMaxSpreadWpm,
            String pendingReasonPrefix,
            String initReasonPrefix,
            TrustOrigin initOrigin
    ) {
        lastObservationTimestampMs = Math.max(lastObservationTimestampMs, timestampMs);
        maybeResetAfterExtendedIdle(timestampMs);
        CwTimingSnapshot snapshot = stabilizedSnapshot(emissionStrategySnapshot(), timestampMs);
        if (snapshot == null || snapshot.dotEstimateMs() <= 0L) {
            recordTrustedUpdate("skip-empty", timestampMs);
            return;
        }

        long candidateDotEstimateMs = applySeedBootstrapPrior(snapshot.dotEstimateMs());
        maybeExpirePendingBootstrapCandidates(
                pendingCandidates,
                timestampMs,
                initialTrustedCandidateWindowMs
        );
        if (trustedDotEstimateMs <= 0.0d) {
            pendingCandidates.append(candidateDotEstimateMs, timestampMs);
            lastStableDecodeTimestampMs = timestampMs;
            if (pendingCandidates == pendingStableTrustedCandidates
                    && tryInitializeMixedBoundaryStableBootstrap(timestampMs)) {
                return;
            }
            if (pendingCandidates.count() < initialTrustedRequiredStableDecodes
                    || !pendingCandidates.clustered(
                    initialTrustedRequiredStableDecodes,
                    initialTrustedBootstrapMaxSpreadWpm
            )) {
                recordTrustedUpdate(
                        pendingReasonPrefix
                                + "("
                                + Math.min(pendingCandidates.count(), initialTrustedRequiredStableDecodes)
                                + "/"
                                + initialTrustedRequiredStableDecodes
                                + ","
                                + candidateDotEstimateMs
                                + "ms)",
                        timestampMs
                );
                return;
            }
            trustedDotEstimateMs = pendingCandidates.median(initialTrustedRequiredStableDecodes);
            rememberRetainedDotEstimate(trustedDotEstimateMs, timestampMs);
            clearPendingBootstrapCandidates();
            clearPendingFastReanchor();
            trustOrigin = initOrigin == null ? TrustOrigin.NONE : initOrigin;
            postTrustStableDecodeCount = 0;
            recordTrustedUpdate(initReasonPrefix + "(" + Math.round(trustedDotEstimateMs) + "ms)", timestampMs);
            return;
        }

        if (candidateDotEstimateMs >= trustedDotEstimateMs) {
            if (!shouldAllowStableSlowUpdate(candidateDotEstimateMs)) {
                lastStableDecodeTimestampMs = timestampMs;
                recordTrustedUpdate(
                        "slow-block(" + Math.round(trustedDotEstimateMs) + "->"
                                + candidateDotEstimateMs + "ms)",
                        timestampMs
                );
                return;
            }
            double previousTrustedDotEstimateMs = trustedDotEstimateMs;
            trustedDotEstimateMs = blend(
                    trustedDotEstimateMs,
                    candidateDotEstimateMs,
                    resolveStableSlowUpdateBlend()
            );
            rememberRetainedDotEstimate(trustedDotEstimateMs, timestampMs);
            clearPendingFastReanchor();
            lastStableDecodeTimestampMs = timestampMs;
            recordTrustedUpdate(
                    "slow-up(" + Math.round(previousTrustedDotEstimateMs) + "->"
                            + Math.round(trustedDotEstimateMs) + "ms)",
                    timestampMs
            );
            return;
        }

        double directFastFloor = trustedDotEstimateMs * TRUSTED_FAST_DIRECT_UPDATE_RATIO;
        if (candidateDotEstimateMs >= directFastFloor) {
            double previousTrustedDotEstimateMs = trustedDotEstimateMs;
            trustedDotEstimateMs = blend(
                    trustedDotEstimateMs,
                    candidateDotEstimateMs,
                    TRUSTED_FAST_DIRECT_UPDATE_BLEND
            );
            rememberRetainedDotEstimate(trustedDotEstimateMs, timestampMs);
            clearPendingFastReanchor();
            lastStableDecodeTimestampMs = timestampMs;
            recordTrustedUpdate(
                    "fast-direct(" + Math.round(previousTrustedDotEstimateMs) + "->"
                            + Math.round(trustedDotEstimateMs) + "ms)",
                    timestampMs
            );
            return;
        }

        long reanchorFastFloorMs = Math.max(
                1L,
                Math.round(trustedDotEstimateMs * TRUSTED_FAST_REANCHOR_RATIO)
        );
        if (candidateDotEstimateMs < reanchorFastFloorMs) {
            clearPendingFastReanchor();
            lastStableDecodeTimestampMs = timestampMs;
            recordTrustedUpdate(
                    "reject-fast(" + candidateDotEstimateMs + "ms<" + reanchorFastFloorMs + "ms)",
                    timestampMs
            );
            return;
        }

        if (!matchesPendingFastReanchor(candidateDotEstimateMs)) {
            pendingFastTrustedDotEstimateMs = candidateDotEstimateMs;
            pendingFastTrustedEvidenceCount = 1;
            lastStableDecodeTimestampMs = timestampMs;
            recordTrustedUpdate("pending-start(" + candidateDotEstimateMs + "ms)", timestampMs);
            return;
        }

        pendingFastTrustedEvidenceCount += 1;
        if (pendingFastTrustedEvidenceCount >= TRUSTED_FAST_REANCHOR_REQUIRED_STABLE_DECODES) {
            double previousTrustedDotEstimateMs = trustedDotEstimateMs;
            trustedDotEstimateMs = blend(
                    trustedDotEstimateMs,
                    pendingFastTrustedDotEstimateMs,
                    TRUSTED_FAST_REANCHOR_BLEND
            );
            rememberRetainedDotEstimate(trustedDotEstimateMs, timestampMs);
            clearPendingFastReanchor();
            recordTrustedUpdate(
                    "reanchor(" + Math.round(previousTrustedDotEstimateMs) + "->"
                            + Math.round(trustedDotEstimateMs) + "ms)",
                    timestampMs
            );
        } else {
            recordTrustedUpdate(
                    "pending-"
                            + pendingFastTrustedEvidenceCount
                            + "/"
                            + TRUSTED_FAST_REANCHOR_REQUIRED_STABLE_DECODES
                            + "("
                            + Math.round(pendingFastTrustedDotEstimateMs)
                            + "ms)",
                    timestampMs
            );
        }
        lastStableDecodeTimestampMs = timestampMs;
    }

    private void noteExplicitBootstrapObservation(
            long candidateDotEstimateMs,
            long timestampMs,
            PendingBootstrapCandidates pendingCandidates,
            int requiredObservations,
            long candidateWindowMs,
            double maxSpreadWpm,
            String pendingReasonPrefix,
            String initReasonPrefix,
            TrustOrigin initOrigin
    ) {
        lastObservationTimestampMs = Math.max(lastObservationTimestampMs, timestampMs);
        maybeResetAfterExtendedIdle(timestampMs);
        if (candidateDotEstimateMs <= 0L) {
            recordTrustedUpdate("skip-empty", timestampMs);
            return;
        }
        candidateDotEstimateMs = applySeedBootstrapPrior(candidateDotEstimateMs);
        maybeExpirePendingBootstrapCandidates(pendingCandidates, timestampMs, candidateWindowMs);
        if (trustedDotEstimateMs > 0.0d) {
            recordTrustedUpdate("skip-explicit-trusted", timestampMs);
            return;
        }
        pendingCandidates.append(candidateDotEstimateMs, timestampMs);
        lastStableDecodeTimestampMs = timestampMs;
        if (pendingCandidates == pendingBoundaryTrustedCandidates
                && tryInitializeMixedBoundaryStableBootstrap(timestampMs)) {
            return;
        }
        if (pendingCandidates.count() < requiredObservations
                || !pendingCandidates.clustered(requiredObservations, maxSpreadWpm)) {
            recordTrustedUpdate(
                    pendingReasonPrefix
                            + "("
                            + Math.min(pendingCandidates.count(), requiredObservations)
                            + "/"
                            + requiredObservations
                            + ","
                            + candidateDotEstimateMs
                            + "ms)",
                    timestampMs
            );
            return;
        }
        trustedDotEstimateMs = pendingCandidates.median(requiredObservations);
        rememberRetainedDotEstimate(trustedDotEstimateMs, timestampMs);
        clearPendingBootstrapCandidates();
        clearPendingFastReanchor();
        trustOrigin = initOrigin == null ? TrustOrigin.NONE : initOrigin;
        postTrustStableDecodeCount = 0;
        recordTrustedUpdate(initReasonPrefix + "(" + Math.round(trustedDotEstimateMs) + "ms)", timestampMs);
    }

    private boolean tryInitializeMixedBoundaryStableBootstrap(long timestampMs) {
        if (trustedDotEstimateMs > 0.0d) {
            return false;
        }
        maybeExpirePendingBootstrapCandidates(
                pendingBoundaryTrustedCandidates,
                timestampMs,
                MIXED_BOUNDARY_STABLE_CANDIDATE_WINDOW_MS
        );
        maybeExpirePendingBootstrapCandidates(
                pendingStableTrustedCandidates,
                timestampMs,
                MIXED_BOUNDARY_STABLE_CANDIDATE_WINDOW_MS
        );
        maybeExpirePendingBootstrapCandidates(
                pendingSoftStableTrustedCandidates,
                timestampMs,
                MIXED_BOUNDARY_STABLE_CANDIDATE_WINDOW_MS
        );
        if (pendingBoundaryTrustedCandidates.count() < MIXED_BOUNDARY_STABLE_REQUIRED_BOUNDARY_OBSERVATIONS) {
            return false;
        }
        MixedBoundaryStablePair mixedPair = tryBuildMixedBoundaryStablePair(
                pendingStableTrustedCandidates,
                false
        );
        if (mixedPair == null) {
            mixedPair = tryBuildMixedBoundaryStablePair(
                    pendingSoftStableTrustedCandidates,
                    true
            );
        }
        if (mixedPair == null) {
            return false;
        }
        double mixedTrustedDotEstimateMs = mixedPair.mixedDotEstimateMs;
        if (mixedTrustedDotEstimateMs < MIXED_BOUNDARY_STABLE_MIN_DOT_MS) {
            return false;
        }
        trustedDotEstimateMs = mixedTrustedDotEstimateMs;
        rememberRetainedDotEstimate(trustedDotEstimateMs, timestampMs);
        clearPendingBootstrapCandidates();
        clearPendingFastReanchor();
        trustOrigin = TrustOrigin.BOUNDARY;
        postTrustStableDecodeCount = 0;
        recordTrustedUpdate(
                (mixedPair.softStableCandidate
                        ? "init-boundary-soft-stable("
                        : "init-boundary-stable(")
                        + Math.round(trustedDotEstimateMs)
                        + "ms)",
                timestampMs
        );
        return true;
    }

    private MixedBoundaryStablePair tryBuildMixedBoundaryStablePair(
            PendingBootstrapCandidates stableCandidates,
            boolean softStableCandidate
    ) {
        if (stableCandidates == null
                || pendingBoundaryTrustedCandidates.count() < MIXED_BOUNDARY_STABLE_REQUIRED_BOUNDARY_OBSERVATIONS
                || stableCandidates.count() < MIXED_BOUNDARY_STABLE_REQUIRED_STABLE_OBSERVATIONS) {
            return null;
        }
        MixedBoundaryStablePair bestPair = null;
        for (int boundaryIndex = 0; boundaryIndex < pendingBoundaryTrustedCandidates.count(); boundaryIndex++) {
            long boundaryDotEstimateMs = pendingBoundaryTrustedCandidates.candidateAt(boundaryIndex);
            if (boundaryDotEstimateMs <= 0L) {
                continue;
            }
            for (int stableIndex = 0; stableIndex < stableCandidates.count(); stableIndex++) {
                long stableDotEstimateMs = stableCandidates.candidateAt(stableIndex);
                if (stableDotEstimateMs <= 0L
                        || !withinBootstrapSpreadWpm(
                        boundaryDotEstimateMs,
                        stableDotEstimateMs,
                        MIXED_BOUNDARY_STABLE_MAX_SPREAD_WPM
                )) {
                    continue;
                }
                double mixedDotEstimateMs = softStableCandidate
                        ? boundaryDotEstimateMs
                        : (boundaryDotEstimateMs + stableDotEstimateMs) / 2.0d;
                if (mixedDotEstimateMs < MIXED_BOUNDARY_STABLE_MIN_DOT_MS) {
                    continue;
                }
                double spreadWpm = Math.abs(
                        candidateDotEstimateToWpm(boundaryDotEstimateMs)
                                - candidateDotEstimateToWpm(stableDotEstimateMs)
                );
                MixedBoundaryStablePair candidatePair = new MixedBoundaryStablePair(
                        mixedDotEstimateMs,
                        spreadWpm,
                        softStableCandidate
                );
                if (candidatePair.isBetterThan(bestPair)) {
                    bestPair = candidatePair;
                }
            }
        }
        return bestPair;
    }

    private long applySeedBootstrapPrior(long candidateDotEstimateMs) {
        if (candidateDotEstimateMs <= 0L || seedWpm <= 0 || trustedDotEstimateMs > 0.0d) {
            return candidateDotEstimateMs;
        }
        long seedDotEstimateMs = wpmToDotEstimateMs(seedWpm);
        if (seedDotEstimateMs <= 0L || candidateDotEstimateMs <= seedDotEstimateMs) {
            return candidateDotEstimateMs;
        }
        double blendedDotEstimateMs = seedDotEstimateMs
                + ((candidateDotEstimateMs - seedDotEstimateMs) * BOOTSTRAP_SEED_SLOW_EXCESS_BLEND);
        return Math.max(1L, Math.round(blendedDotEstimateMs));
    }

    private void refreshStrategy() {
        CwTimingSnapshot baselineSnapshot = baseline.snapshot();
        CwTimingSnapshot adaptiveSnapshot = adaptive.snapshot();
        double baselineWpm = snapshotWpm(baselineSnapshot);
        double adaptiveWpm = snapshotWpm(adaptiveSnapshot);
        if (shouldHoldBaselineAgainstTrustedFloor(baselineSnapshot, adaptiveSnapshot)) {
            activeStrategy = Strategy.BASELINE;
            lastStrategyDecision = String.format(
                    Locale.US,
                    "trusted-hold-baseline(b=%.1f,a=%.1f)",
                    baselineWpm,
                    adaptiveWpm
            );
            return;
        }
        if (shouldHoldBaselineBeforeTrust(baselineSnapshot, adaptiveSnapshot)) {
            activeStrategy = Strategy.BASELINE;
            lastStrategyDecision = String.format(
                    Locale.US,
                    "pretrust-hold-baseline(b=%.1f,a=%.1f)",
                    baselineWpm,
                    adaptiveWpm
            );
            return;
        }
        if (shouldPromoteAdaptive(baselineSnapshot, adaptiveSnapshot)) {
            if (activeStrategy != Strategy.ADAPTIVE) {
                adaptivePromotionCount += 1;
            }
            activeStrategy = Strategy.ADAPTIVE;
            lastStrategyDecision = String.format(
                    Locale.US,
                    "promote-adaptive(b=%.1f,a=%.1f)",
                    baselineWpm,
                    adaptiveWpm
            );
            return;
        }
        if (shouldRestoreBaseline(baselineSnapshot, adaptiveSnapshot)) {
            activeStrategy = Strategy.BASELINE;
            lastStrategyDecision = String.format(
                    Locale.US,
                    "restore-baseline(b=%.1f,a=%.1f)",
                    baselineWpm,
                    adaptiveWpm
            );
            return;
        }
        lastStrategyDecision = String.format(
                Locale.US,
                "hold-%s(b=%.1f,a=%.1f)",
                strategyToken(activeStrategy),
                baselineWpm,
                adaptiveWpm
        );
    }

    private boolean shouldPromoteAdaptive(
            CwTimingSnapshot baselineSnapshot,
            CwTimingSnapshot adaptiveSnapshot
    ) {
        return adaptiveSnapshot.estimatedWpm() >= ADAPTIVE_PROMOTION_MIN_WPM
                && baselineSnapshot.estimatedWpm() <= ADAPTIVE_PROMOTION_BASELINE_MAX_WPM
                && adaptiveSnapshot.estimatedWpm() >= baselineSnapshot.estimatedWpm() + ADAPTIVE_PROMOTION_WPM_LEAD;
    }

    private boolean shouldHoldBaselineBeforeTrust(
            CwTimingSnapshot baselineSnapshot,
            CwTimingSnapshot adaptiveSnapshot
    ) {
        if (trustedDotEstimateMs > 0.0d) {
            return false;
        }
        long stableFloorDotEstimateMs = resolveStableBootstrapBoundaryFloorDotEstimateMs();
        long baselineDotEstimateMs = resolveBootstrapLaneDotEstimateMs(baselineSnapshot);
        long adaptiveDotEstimateMs = resolveBootstrapLaneDotEstimateMs(adaptiveSnapshot);
        return stableFloorDotEstimateMs > 0L
                && baselineDotEstimateMs >= stableFloorDotEstimateMs
                && adaptiveDotEstimateMs > 0L
                && adaptiveDotEstimateMs < stableFloorDotEstimateMs;
    }

    private boolean shouldHoldBaselineAgainstTrustedFloor(
            CwTimingSnapshot baselineSnapshot,
            CwTimingSnapshot adaptiveSnapshot
    ) {
        if (trustedDotEstimateMs <= 0.0d
                || shouldDelayCadenceOpeningTrustedFloor(lastObservationTimestampMs)) {
            return false;
        }
        long trustedFastFloorDotEstimateMs = Math.max(
                1L,
                Math.round(trustedDotEstimateMs * TRUSTED_FAST_FLOOR_RATIO)
        );
        long baselineDotEstimateMs = resolveBootstrapLaneDotEstimateMs(baselineSnapshot);
        long adaptiveDotEstimateMs = resolveBootstrapLaneDotEstimateMs(adaptiveSnapshot);
        return baselineDotEstimateMs >= trustedFastFloorDotEstimateMs
                && adaptiveDotEstimateMs > 0L
                && adaptiveDotEstimateMs < trustedFastFloorDotEstimateMs;
    }

    private boolean shouldRestoreBaseline(
            CwTimingSnapshot baselineSnapshot,
            CwTimingSnapshot adaptiveSnapshot
    ) {
        if (activeStrategy != Strategy.ADAPTIVE) {
            return false;
        }
        return baselineSnapshot.estimatedWpm() >= adaptiveSnapshot.estimatedWpm() - BASELINE_RESTORE_MARGIN_WPM
                || adaptiveSnapshot.estimatedWpm() < ADAPTIVE_PROMOTION_MIN_WPM;
    }

    private List<CwTimingEvent> selectOutputEvents(
            List<CwTimingEvent> baselineEvents,
            List<CwTimingEvent> adaptiveEvents
    ) {
        if (activeStrategy == Strategy.ADAPTIVE) {
            lastEmissionStrategy = Strategy.ADAPTIVE;
            adaptiveEmissionBatches += 1;
            return adaptiveEvents;
        }
        lastEmissionStrategy = Strategy.BASELINE;
        return baselineEvents;
    }

    private CwTimingSnapshot snapshotSource() {
        return lastObservationTimestampMs > 0L ? emissionStrategySnapshot() : rawSnapshot();
    }

    private CwTimingSnapshot emissionStrategySnapshot() {
        return lastEmissionStrategy == Strategy.ADAPTIVE
                ? adaptive.snapshot()
                : baseline.snapshot();
    }

    private long effectiveTimestamp() {
        if (lastObservationTimestampMs > 0L) {
            return lastObservationTimestampMs;
        }
        if (lastStableDecodeTimestampMs > 0L) {
            return lastStableDecodeTimestampMs;
        }
        return 0L;
    }

    private List<CwTimingEvent> stabilizeOutputEvents(
            List<CwTimingEvent> outputEvents,
            CwTimingSnapshot rawSnapshot,
            long timestampMs
    ) {
        if (outputEvents == null || outputEvents.isEmpty()) {
            return outputEvents;
        }
        long effectiveDotEstimateMs = resolveEffectiveDotEstimateMs(rawSnapshot, timestampMs);
        long effectiveIntraGapEstimateMs = resolveEffectiveIntraGapEstimateMs(rawSnapshot, effectiveDotEstimateMs);
        java.util.ArrayList<CwTimingEvent> stabilizedEvents = new java.util.ArrayList<>(outputEvents.size());
        for (CwTimingEvent outputEvent : outputEvents) {
            if (outputEvent == null) {
                continue;
            }
            stabilizedEvents.add(new CwTimingEvent(
                    outputEvent.kind(),
                    reclassify(outputEvent, effectiveDotEstimateMs, effectiveIntraGapEstimateMs),
                    outputEvent.timestampMs(),
                    outputEvent.durationMs(),
                    effectiveDotEstimateMs,
                    effectiveIntraGapEstimateMs
            ));
        }
        return stabilizedEvents;
    }

    private CwTimingSnapshot stabilizedSnapshot(
            CwTimingSnapshot rawSnapshot,
            long timestampMs
    ) {
        maybeResetAfterExtendedIdle(timestampMs);
        if (rawSnapshot == null) {
            return syntheticSnapshot(resolveFallbackDotEstimateMs(), 0L, 0L, 0, 0, null);
        }
        long effectiveDotEstimateMs = resolveEffectiveDotEstimateMs(rawSnapshot, timestampMs);
        long effectiveIntraGapEstimateMs = resolveEffectiveIntraGapEstimateMs(rawSnapshot, effectiveDotEstimateMs);
        long effectiveDashEstimateMs = Math.max(
                Math.max(1L, rawSnapshot.dashEstimateMs()),
                effectiveDotEstimateMs * 3L
        );
        return syntheticSnapshot(
                effectiveDotEstimateMs,
                effectiveDashEstimateMs,
                effectiveIntraGapEstimateMs,
                rawSnapshot.totalToneEvents(),
                rawSnapshot.totalGapEvents(),
                restyleLastTimingEvent(rawSnapshot.lastTimingEvent(), effectiveDotEstimateMs, effectiveIntraGapEstimateMs)
        );
    }

    private CwTimingSnapshot syntheticSnapshot(
            long dotEstimateMs,
            long dashEstimateMs,
            long intraGapEstimateMs,
            int totalToneEvents,
            int totalGapEvents,
            CwTimingEvent lastTimingEvent
    ) {
        long safeDotEstimateMs = Math.max(1L, dotEstimateMs);
        long safeDashEstimateMs = Math.max(safeDotEstimateMs * 3L, dashEstimateMs);
        long safeIntraGapEstimateMs = Math.max(safeDotEstimateMs, intraGapEstimateMs);
        double estimatedWpmPrecise = 1200.0d / safeDotEstimateMs;
        return new CwTimingSnapshot(
                safeDotEstimateMs,
                safeDashEstimateMs,
                safeIntraGapEstimateMs,
                (int) Math.round(estimatedWpmPrecise),
                estimatedWpmPrecise,
                totalToneEvents,
                totalGapEvents,
                lastTimingEvent
        );
    }

    private CwTimingEvent restyleLastTimingEvent(
            CwTimingEvent lastTimingEvent,
            long effectiveDotEstimateMs,
            long effectiveIntraGapEstimateMs
    ) {
        if (lastTimingEvent == null) {
            return null;
        }
        return new CwTimingEvent(
                lastTimingEvent.kind(),
                reclassify(lastTimingEvent, effectiveDotEstimateMs, effectiveIntraGapEstimateMs),
                lastTimingEvent.timestampMs(),
                lastTimingEvent.durationMs(),
                effectiveDotEstimateMs,
                effectiveIntraGapEstimateMs
        );
    }

    private long resolveEffectiveDotEstimateMs(CwTimingSnapshot rawSnapshot, long timestampMs) {
        maybeResetAfterExtendedIdle(timestampMs);
        long rawDotEstimateMs = resolveRawDotEstimateMs(rawSnapshot);
        long bootstrapReferenceDotEstimateMs = resolvePreTrustBootstrapReferenceDotEstimateMs();
        updatePreTrustOpeningToneGuard(rawSnapshot, bootstrapReferenceDotEstimateMs, timestampMs);
        long bootstrapDotEstimateMs = resolvePreTrustBootstrapDotEstimateMs(timestampMs);
        double effectiveDotEstimateMs = bootstrapDotEstimateMs > 0L
                ? bootstrapDotEstimateMs
                : rawDotEstimateMs > 0L
                ? rawDotEstimateMs
                : resolveFallbackDotEstimateMs();
        if (preTrustOpeningToneGuardActive && bootstrapReferenceDotEstimateMs > 0L) {
            effectiveDotEstimateMs = bootstrapReferenceDotEstimateMs;
        }
        if (trustedDotEstimateMs > 0.0d
                && !shouldDelayCadenceOpeningTrustedFloor(timestampMs)) {
            double trustedFastFloor = trustedDotEstimateMs * TRUSTED_FAST_FLOOR_RATIO;
            if (effectiveDotEstimateMs < trustedFastFloor) {
                effectiveDotEstimateMs = trustedFastFloor;
            }
        }
        return Math.max(1L, Math.round(effectiveDotEstimateMs));
    }

    private long resolveRawDotEstimateMs(CwTimingSnapshot rawSnapshot) {
        if (rawSnapshot == null) {
            return 0L;
        }
        if (rawSnapshot.lastTimingEvent() == null
                && rawSnapshot.totalToneEvents() == 0
                && rawSnapshot.totalGapEvents() == 0) {
            return resolveFallbackDotEstimateMs();
        }
        return Math.max(1L, rawSnapshot.dotEstimateMs());
    }

    private long resolveFallbackDotEstimateMs() {
        if (trustedDotEstimateMs > 0.0d) {
            return Math.max(1L, Math.round(trustedDotEstimateMs));
        }
        if (retainedDotEstimateMs > 0.0d) {
            return Math.max(1L, Math.round(retainedDotEstimateMs));
        }
        if (seedWpm > 0) {
            return wpmToDotEstimateMs(seedWpm);
        }
        return Math.max(1L, baseline.snapshot().dotEstimateMs());
    }

    private long resolvePreTrustBootstrapDotEstimateMs(long timestampMs) {
        if (!shouldUsePreTrustBootstrap(timestampMs)) {
            return 0L;
        }
        long referenceDotEstimateMs = resolvePreTrustBootstrapReferenceDotEstimateMs();
        if (referenceDotEstimateMs <= 0L) {
            return 0L;
        }
        long baselineDotEstimateMs = resolveBootstrapLaneDotEstimateMs(baseline.snapshot());
        long adaptiveDotEstimateMs = resolveBootstrapLaneDotEstimateMs(adaptive.snapshot());
        if (baselineDotEstimateMs <= 0L && adaptiveDotEstimateMs <= 0L) {
            return 0L;
        }
        double baselineScore = bootstrapLaneScore(baselineDotEstimateMs, referenceDotEstimateMs);
        double adaptiveScore = bootstrapLaneScore(adaptiveDotEstimateMs, referenceDotEstimateMs);
        if (adaptiveScore + 0.0001d < baselineScore) {
            return adaptiveDotEstimateMs;
        }
        return baselineDotEstimateMs > 0L ? baselineDotEstimateMs : adaptiveDotEstimateMs;
    }

    private boolean shouldUsePreTrustBootstrap(long timestampMs) {
        if (trustedDotEstimateMs > 0.0d) {
            return false;
        }
        if (timestampMs > 0L
                && lastResetTimestampMs > 0L
                && (timestampMs - lastResetTimestampMs) > PRE_TRUST_BOOTSTRAP_WINDOW_MS) {
            return false;
        }
        return Math.max(observationCount(baseline.snapshot()), observationCount(adaptive.snapshot()))
                <= PRE_TRUST_BOOTSTRAP_MAX_EVENT_COUNT;
    }

    private long resolvePreTrustBootstrapReferenceDotEstimateMs() {
        double weightedDotEstimateMs = 0.0d;
        double totalWeight = 0.0d;

        if (pendingBoundaryTrustedCandidates.count() > 0) {
            double weight = PRE_TRUST_BOOTSTRAP_BOUNDARY_WEIGHT * pendingBoundaryTrustedCandidates.count();
            weightedDotEstimateMs += pendingBoundaryTrustedCandidates.median(
                    pendingBoundaryTrustedCandidates.count()
            ) * weight;
            totalWeight += weight;
        }
        if (pendingCadenceTrustedCandidates.count() > 0) {
            double weight = PRE_TRUST_BOOTSTRAP_CADENCE_WEIGHT * pendingCadenceTrustedCandidates.count();
            weightedDotEstimateMs += pendingCadenceTrustedCandidates.median(
                    pendingCadenceTrustedCandidates.count()
            ) * weight;
            totalWeight += weight;
        }
        if (pendingStableTrustedCandidates.count() > 0) {
            double weight = PRE_TRUST_BOOTSTRAP_STABLE_WEIGHT * pendingStableTrustedCandidates.count();
            weightedDotEstimateMs += pendingStableTrustedCandidates.median(
                    pendingStableTrustedCandidates.count()
            ) * weight;
            totalWeight += weight;
        }
        if (seedWpm > 0) {
            weightedDotEstimateMs += wpmToDotEstimateMs(seedWpm) * PRE_TRUST_BOOTSTRAP_SEED_WEIGHT;
            totalWeight += PRE_TRUST_BOOTSTRAP_SEED_WEIGHT;
        }
        if (totalWeight <= 0.0d) {
            return 0L;
        }
        return Math.max(1L, Math.round(weightedDotEstimateMs / totalWeight));
    }

    private long resolveBootstrapLaneDotEstimateMs(CwTimingSnapshot snapshot) {
        if (snapshot == null || observationCount(snapshot) <= 0 || snapshot.dotEstimateMs() <= 0L) {
            return 0L;
        }
        return Math.max(1L, snapshot.dotEstimateMs());
    }

    private int observationCount(CwTimingSnapshot snapshot) {
        if (snapshot == null) {
            return 0;
        }
        return snapshot.totalToneEvents() + snapshot.totalGapEvents();
    }

    private double bootstrapLaneScore(long candidateDotEstimateMs, long referenceDotEstimateMs) {
        if (candidateDotEstimateMs <= 0L || referenceDotEstimateMs <= 0L) {
            return Double.MAX_VALUE;
        }
        return Math.abs(candidateDotEstimateMs - referenceDotEstimateMs)
                / (double) Math.max(1L, referenceDotEstimateMs);
    }


    private boolean violatesStableBootstrapBoundaryFloor(long candidateDotEstimateMs) {
        long floorDotEstimateMs = resolveStableBootstrapBoundaryFloorDotEstimateMs();
        return floorDotEstimateMs > 0L && candidateDotEstimateMs > 0L && candidateDotEstimateMs < floorDotEstimateMs;
    }

    private long resolveStableBootstrapBoundaryFloorDotEstimateMs() {
        if (trustedDotEstimateMs > 0.0d || pendingStableTrustedCandidates.count() <= 0) {
            return 0L;
        }
        long stableMedianDotEstimateMs = Math.max(
                1L,
                Math.round(pendingStableTrustedCandidates.median(pendingStableTrustedCandidates.count()))
        );
        return Math.max(
                1L,
                Math.round(stableMedianDotEstimateMs * BOUNDARY_BOOTSTRAP_STABLE_FLOOR_RATIO)
        );
    }

    private void updatePreTrustOpeningToneGuard(
            CwTimingSnapshot rawSnapshot,
            long referenceDotEstimateMs,
            long timestampMs
    ) {
        if (trustedDotEstimateMs > 0.0d || referenceDotEstimateMs <= 0L || rawSnapshot == null) {
            preTrustOpeningToneGuardActive = false;
            return;
        }
        int eventCount = observationCount(rawSnapshot);
        if (eventCount <= 0 || eventCount > PRE_TRUST_OPENING_TONE_GUARD_MAX_EVENT_COUNT) {
            preTrustOpeningToneGuardActive = false;
            return;
        }
        CwTimingEvent lastTimingEvent = rawSnapshot.lastTimingEvent();
        if (lastTimingEvent == null) {
            preTrustOpeningToneGuardActive = false;
            return;
        }
        if (!preTrustOpeningToneGuardActive) {
            preTrustOpeningToneGuardActive = eventCount == 1
                    && lastTimingEvent.kind() == CwTimingEvent.Kind.TONE
                    && lastTimingEvent.durationMs()
                    > Math.round(referenceDotEstimateMs * PRE_TRUST_OPENING_TONE_OUTLIER_RATIO);
            return;
        }
        if (timestampMs > 0L
                && lastResetTimestampMs > 0L
                && (timestampMs - lastResetTimestampMs) > PRE_TRUST_BOOTSTRAP_WINDOW_MS) {
            preTrustOpeningToneGuardActive = false;
            return;
        }
        preTrustOpeningToneGuardActive = rawSnapshot.dotEstimateMs()
                > Math.round(referenceDotEstimateMs * PRE_TRUST_OPENING_TONE_GUARD_RELEASE_RATIO);
    }

    private long resolveEffectiveIntraGapEstimateMs(
            CwTimingSnapshot rawSnapshot,
            long effectiveDotEstimateMs
    ) {
        long rawIntraGapEstimateMs = rawSnapshot == null
                ? 0L
                : Math.max(1L, rawSnapshot.intraGapEstimateMs());
        return Math.max(effectiveDotEstimateMs, rawIntraGapEstimateMs);
    }

    private void maybeResetAfterExtendedIdle(long timestampMs) {
        maybeReleaseRetainedAfterExtendedIdle(timestampMs);
        if (!idleResetEnabled) {
            return;
        }
        if (timestampMs <= 0L
                || lastTimingActivityTimestampMs <= 0L
                || (timestampMs - lastTimingActivityTimestampMs) < TRUSTED_IDLE_RELEASE_MS) {
            return;
        }
        resetAfterExtendedIdle(timestampMs);
    }

    private void resetAfterExtendedIdle(long timestampMs) {
        baseline.reset();
        adaptive.reset();
        activeStrategy = Strategy.BASELINE;
        lastEmissionStrategy = Strategy.BASELINE;
        if (trustedDotEstimateMs > 0.0d) {
            rememberRetainedDotEstimate(trustedDotEstimateMs, timestampMs);
        }
        trustedDotEstimateMs = 0.0d;
        lastStableDecodeTimestampMs = -1L;
        lastTimingActivityTimestampMs = -1L;
        clearPendingBootstrapCandidates();
        clearPendingFastReanchor();
        trustOrigin = TrustOrigin.NONE;
        postTrustStableDecodeCount = 0;
        recordReset("trusted-idle-reset", timestampMs);
        lastStrategyDecision = "reset:trusted-idle-reset";
        recordTrustedUpdate("demote:trusted-idle-reset", timestampMs);
        recordObservation("reset:trusted-idle-reset", timestampMs);
    }

    private boolean shouldDelayCadenceOpeningTrustedFloor(long timestampMs) {
        if (trustedDotEstimateMs <= 0.0d
                || trustOrigin != TrustOrigin.CADENCE
                || postTrustStableDecodeCount > 0
                || lastTrustedUpdateTimestampMs <= 0L) {
            return false;
        }
        long referenceTimestampMs = timestampMs > 0L ? timestampMs : effectiveTimestamp();
        if (referenceTimestampMs <= 0L) {
            return false;
        }
        long relaxWindowMs = Math.max(
                1L,
                Math.round(trustedDotEstimateMs * CADENCE_OPENING_RELAX_WINDOW_DOTS)
        );
        return (referenceTimestampMs - lastTrustedUpdateTimestampMs) <= relaxWindowMs;
    }

    private void maybeReleaseRetainedAfterExtendedIdle(long timestampMs) {
        if (timestampMs <= 0L
                || lastRetainedUpdateTimestampMs <= 0L
                || retainedDotEstimateMs <= 0.0d
                || (timestampMs - lastRetainedUpdateTimestampMs) < RETAINED_IDLE_RELEASE_MS
                || (lastTimingActivityTimestampMs > 0L
                && (timestampMs - lastTimingActivityTimestampMs) < RETAINED_IDLE_RELEASE_MS)) {
            return;
        }
        retainedDotEstimateMs = 0.0d;
        lastRetainedUpdateTimestampMs = -1L;
    }

    private void noteTimingActivity(List<CwTimingEvent> outputEvents) {
        if (outputEvents == null || outputEvents.isEmpty()) {
            return;
        }
        for (int index = outputEvents.size() - 1; index >= 0; index--) {
            CwTimingEvent timingEvent = outputEvents.get(index);
            if (timingEvent == null) {
                continue;
            }
            lastTimingActivityTimestampMs = Math.max(lastTimingActivityTimestampMs, timingEvent.timestampMs());
            return;
        }
    }

    private void clearPendingFastReanchor() {
        pendingFastTrustedDotEstimateMs = 0.0d;
        pendingFastTrustedEvidenceCount = 0;
    }

    private void maybeExpirePendingBootstrapCandidates(
            PendingBootstrapCandidates pendingCandidates,
            long timestampMs,
            long candidateWindowMs
    ) {
        if (pendingCandidates == null || timestampMs <= 0L) {
            return;
        }
        pendingCandidates.expireIfStale(timestampMs, candidateWindowMs);
    }

    private void clearPendingBootstrapCandidates() {
        pendingStableTrustedCandidates.clear();
        pendingSoftStableTrustedCandidates.clear();
        pendingBoundaryTrustedCandidates.clear();
        pendingCadenceTrustedCandidates.clear();
    }

    private void rememberRetainedDotEstimate(double candidateDotEstimateMs, long timestampMs) {
        if (candidateDotEstimateMs <= 0.0d) {
            return;
        }
        retainedDotEstimateMs = candidateDotEstimateMs;
        if (timestampMs > 0L) {
            lastRetainedUpdateTimestampMs = timestampMs;
        }
    }

    private boolean matchesPendingFastReanchor(long candidateDotEstimateMs) {
        if (pendingFastTrustedEvidenceCount <= 0 || pendingFastTrustedDotEstimateMs <= 0.0d) {
            return false;
        }
        double absoluteDeltaMs = Math.abs(candidateDotEstimateMs - pendingFastTrustedDotEstimateMs);
        double maxDeltaMs = Math.max(3.0d, pendingFastTrustedDotEstimateMs * TRUSTED_FAST_REANCHOR_MATCH_RATIO);
        return absoluteDeltaMs <= maxDeltaMs;
    }

    private double blend(double currentValue, double newValue, double ratio) {
        return currentValue + ((newValue - currentValue) * ratio);
    }

    private boolean shouldAllowBoundarySlowUpdate(double candidateDotEstimateMs) {
        if (candidateDotEstimateMs <= trustedDotEstimateMs) {
            return true;
        }
        if (experimentalTrustedSlowUpdateTuning == null) {
            return true;
        }
        if (!experimentalTrustedSlowUpdateTuning.boundarySlowUpdateEnabled()) {
            return false;
        }
        return candidateDotEstimateMs <= maximumAllowedSlowUpdateDotMs();
    }

    private boolean shouldAllowStableSlowUpdate(double candidateDotEstimateMs) {
        if (candidateDotEstimateMs <= trustedDotEstimateMs) {
            return true;
        }
        if (experimentalTrustedSlowUpdateTuning == null) {
            return true;
        }
        if (!experimentalTrustedSlowUpdateTuning.stableSlowUpdateEnabled()) {
            return false;
        }
        return candidateDotEstimateMs <= maximumAllowedSlowUpdateDotMs();
    }

    private double resolveBoundarySlowUpdateBlend() {
        if (experimentalTrustedSlowUpdateTuning == null) {
            return TRUSTED_SLOW_UPDATE_BLEND;
        }
        return experimentalTrustedSlowUpdateTuning.boundarySlowUpdateBlend();
    }

    private double resolveStableSlowUpdateBlend() {
        if (experimentalTrustedSlowUpdateTuning == null) {
            return TRUSTED_SLOW_UPDATE_BLEND;
        }
        return experimentalTrustedSlowUpdateTuning.stableSlowUpdateBlend();
    }

    private double maximumAllowedSlowUpdateDotMs() {
        if (experimentalTrustedSlowUpdateTuning == null
                || !Double.isFinite(experimentalTrustedSlowUpdateTuning.maxPostTrustSlowUpRatio())) {
            return Double.POSITIVE_INFINITY;
        }
        return trustedDotEstimateMs * experimentalTrustedSlowUpdateTuning.maxPostTrustSlowUpRatio();
    }

    private double trustedWpm() {
        if (trustedDotEstimateMs <= 0.0d) {
            return seedWpm > 0 ? seedWpm : 0.0d;
        }
        return 1200.0d / trustedDotEstimateMs;
    }

    private static double candidateDotEstimateToWpm(long candidateDotEstimateMs) {
        return 1200.0d / Math.max(1L, candidateDotEstimateMs);
    }

    private static boolean withinBootstrapSpreadWpm(
            double leftDotEstimateMs,
            double rightDotEstimateMs,
            double maxSpreadWpm
    ) {
        if (leftDotEstimateMs <= 0.0d || rightDotEstimateMs <= 0.0d) {
            return false;
        }
        double leftWpm = candidateDotEstimateToWpm(Math.max(1L, Math.round(leftDotEstimateMs)));
        double rightWpm = candidateDotEstimateToWpm(Math.max(1L, Math.round(rightDotEstimateMs)));
        return Math.abs(leftWpm - rightWpm) <= maxSpreadWpm;
    }

    private long wpmToDotEstimateMs(int wpm) {
        return Math.max(1L, Math.round(1200.0d / Math.max(1, wpm)));
    }

    private CwTimingEvent.Classification reclassify(
            CwTimingEvent timingEvent,
            long effectiveDotEstimateMs,
            long effectiveIntraGapEstimateMs
    ) {
        if (timingEvent.kind() == CwTimingEvent.Kind.TONE) {
            CwTimingEvent.Classification reclassified =
                    classifyTone(timingEvent.durationMs(), effectiveDotEstimateMs);
            if (shouldPreserveRawDashClassification(timingEvent, reclassified, effectiveDotEstimateMs)) {
                return CwTimingEvent.Classification.DAH;
            }
            return reclassified;
        }
        CwTimingEvent.Classification reclassified = classifyGap(
                timingEvent.durationMs(),
                effectiveDotEstimateMs,
                effectiveIntraGapEstimateMs
        );
        if (shouldPreserveRawLetterGapClassification(timingEvent, reclassified, effectiveDotEstimateMs)) {
            return CwTimingEvent.Classification.LETTER_GAP;
        }
        return reclassified;
    }

    private boolean shouldPreserveRawDashClassification(
            CwTimingEvent timingEvent,
            CwTimingEvent.Classification reclassified,
            long effectiveDotEstimateMs
    ) {
        if (trustedDotEstimateMs <= 0.0d
                || timingEvent == null
                || timingEvent.kind() != CwTimingEvent.Kind.TONE
                || timingEvent.classification() != CwTimingEvent.Classification.DAH
                || reclassified != CwTimingEvent.Classification.DIT) {
            return false;
        }
        long rawDotEstimateMs = Math.max(1L, timingEvent.dotEstimateMs());
        if (effectiveDotEstimateMs <= rawDotEstimateMs) {
            return false;
        }
        double ratioToEffectiveDot = timingEvent.durationMs() / (double) Math.max(1L, effectiveDotEstimateMs);
        return ratioToEffectiveDot >= TRUSTED_FLOOR_RAW_DASH_PRESERVE_MIN_RATIO;
    }

    private boolean shouldPreserveRawLetterGapClassification(
            CwTimingEvent timingEvent,
            CwTimingEvent.Classification reclassified,
            long effectiveDotEstimateMs
    ) {
        if (trustedDotEstimateMs <= 0.0d
                || timingEvent == null
                || timingEvent.kind() != CwTimingEvent.Kind.GAP
                || timingEvent.classification() != CwTimingEvent.Classification.LETTER_GAP
                || reclassified != CwTimingEvent.Classification.INTRA_SYMBOL_GAP) {
            return false;
        }
        long rawDotEstimateMs = Math.max(1L, timingEvent.dotEstimateMs());
        if (effectiveDotEstimateMs <= rawDotEstimateMs) {
            return false;
        }
        double ratioToEffectiveDot = timingEvent.durationMs() / (double) Math.max(1L, effectiveDotEstimateMs);
        return ratioToEffectiveDot >= TRUSTED_FLOOR_RAW_LETTER_GAP_PRESERVE_MIN_RATIO;
    }

    private CwTimingEvent.Classification classifyTone(long toneDurationMs, long dotEstimateMs) {
        double ratio = toneDurationMs / (double) Math.max(1L, dotEstimateMs);
        if (ratio <= 1.8d) {
            return CwTimingEvent.Classification.DIT;
        }
        if (ratio <= 4.8d) {
            return CwTimingEvent.Classification.DAH;
        }
        return CwTimingEvent.Classification.UNKNOWN;
    }

    private CwTimingEvent.Classification classifyGap(
            long gapDurationMs,
            long dotEstimateMs,
            long intraGapEstimateMs
    ) {
        double ratio = gapDurationMs / (double) Math.max(1L, dotEstimateMs);
        double intraRatio = gapDurationMs / (double) Math.max(1L, intraGapEstimateMs);
        boolean fastTimingContext = dotEstimateMs <= FAST_DOT_THRESHOLD_MS
                || intraGapEstimateMs <= FAST_DOT_THRESHOLD_MS;
        // Fast live audio can stretch true intra-symbol gaps close to 1.8 dot
        // without actually indicating a character boundary. Keep the fast path
        // aligned with the baseline intra-gap ceiling so borderline gaps like
        // the "24WPM" tail in recording16 do not split into two characters.
        double intraGapMaxRatio = fastTimingContext ? 1.8d : 1.8d;
        double letterGapMaxRatio = fastTimingContext ? 3.95d : 4.70d;
        double wordGapMaxRatio = fastTimingContext ? 11.8d : 12.8d;
        if (ratio <= intraGapMaxRatio) {
            return CwTimingEvent.Classification.INTRA_SYMBOL_GAP;
        }
        if (ratio >= HYBRID_WORD_GAP_DOT_RATIO_FALLBACK_MIN
                && intraRatio >= HYBRID_WORD_GAP_INTRA_RATIO_FALLBACK) {
            return CwTimingEvent.Classification.WORD_GAP;
        }
        if (ratio <= letterGapMaxRatio) {
            return CwTimingEvent.Classification.LETTER_GAP;
        }
        if (ratio <= wordGapMaxRatio) {
            return CwTimingEvent.Classification.WORD_GAP;
        }
        return CwTimingEvent.Classification.UNKNOWN;
    }

    private void noteObservation(
            CwToneEvent toneEvent,
            boolean allowLearning,
            List<CwTimingEvent> stabilizedEvents
    ) {
        if (toneEvent == null) {
            recordObservation("event:none", effectiveTimestamp());
            return;
        }
        String eventLabel = toneEvent.type() == CwToneEvent.Type.TONE_ON ? "tone-on" : "tone-off";
        recordObservation(
                eventLabel + ":" + renderTimingEventSummary(stabilizedEvents) + "/" + yesNo(allowLearning),
                toneEvent.timestampMs()
        );
    }

    private void noteFlushObservation(
            long timestampMs,
            boolean allowLearning,
            List<CwTimingEvent> stabilizedEvents
    ) {
        recordObservation(
                "flush:" + renderTimingEventSummary(stabilizedEvents) + "/" + yesNo(allowLearning),
                timestampMs
        );
    }

    private void recordTrustedUpdate(String reason, long timestampMs) {
        lastTrustedUpdateReason = reason == null || reason.trim().isEmpty() ? "none" : reason;
        lastTrustedUpdateTimestampMs = timestampMs;
    }

    private void recordReset(String reason, long timestampMs) {
        lastResetReason = reason == null || reason.trim().isEmpty() ? "none" : reason;
        lastResetTimestampMs = timestampMs;
    }

    private void recordObservation(String summary, long timestampMs) {
        lastObservationSummary = summary == null || summary.trim().isEmpty() ? "none" : summary;
        lastObservationSummaryTimestampMs = timestampMs;
    }

    private String renderTrustedSummary() {
        if (trustedDotEstimateMs <= 0.0d) {
            return "-";
        }
        return formatWpm(1200.0d / trustedDotEstimateMs)
                + "/"
                + Math.round(trustedDotEstimateMs)
                + "ms";
    }

    private String renderPendingFastReanchorSummary() {
        if (pendingFastTrustedEvidenceCount <= 0 || pendingFastTrustedDotEstimateMs <= 0.0d) {
            return "0";
        }
        return Math.round(pendingFastTrustedDotEstimateMs) + "x" + pendingFastTrustedEvidenceCount;
    }

    private String renderEventSummary(String reason, long eventTimestampMs, long referenceTimestampMs) {
        String safeReason = reason == null || reason.trim().isEmpty() ? "none" : reason;
        if (eventTimestampMs <= 0L || referenceTimestampMs <= 0L || referenceTimestampMs < eventTimestampMs) {
            return safeReason;
        }
        return safeReason + "+" + (referenceTimestampMs - eventTimestampMs) + "ms";
    }

    private String renderTimingEventSummary(List<CwTimingEvent> stabilizedEvents) {
        if (stabilizedEvents == null || stabilizedEvents.isEmpty()) {
            return "none";
        }
        for (int index = stabilizedEvents.size() - 1; index >= 0; index--) {
            CwTimingEvent timingEvent = stabilizedEvents.get(index);
            if (timingEvent == null) {
                continue;
            }
            return eventKindToken(timingEvent.kind())
                    + "/"
                    + classificationToken(timingEvent.classification())
                    + "/"
                    + timingEvent.durationMs()
                    + "ms";
        }
        return "none";
    }

    private double snapshotWpm(CwTimingSnapshot snapshot) {
        if (snapshot == null) {
            return 0.0d;
        }
        if (snapshot.estimatedWpmPrecise() > 0.0d) {
            return snapshot.estimatedWpmPrecise();
        }
        return Math.max(0, snapshot.estimatedWpm());
    }

    private String formatWpm(double value) {
        if (value <= 0.0d) {
            return "-";
        }
        return String.format(Locale.US, "%.1f", value);
    }

    private String strategyToken(Strategy strategy) {
        if (strategy == Strategy.ADAPTIVE) {
            return "adaptive";
        }
        return "baseline";
    }

    private String eventKindToken(CwTimingEvent.Kind kind) {
        return kind == CwTimingEvent.Kind.GAP ? "gap" : "tone";
    }

    private String classificationToken(CwTimingEvent.Classification classification) {
        if (classification == null) {
            return "none";
        }
        switch (classification) {
            case DIT:
                return "dit";
            case DAH:
                return "dah";
            case INTRA_SYMBOL_GAP:
                return "intra";
            case LETTER_GAP:
                return "letter";
            case WORD_GAP:
                return "word";
            case UNKNOWN:
            default:
                return "unk";
        }
    }

    private String yesNo(boolean value) {
        return value ? "Y" : "N";
    }

    private static final class PendingBootstrapCandidates {
        private final long[] dotCandidatesMs;
        private int count;
        private long lastTimestampMs = -1L;

        private PendingBootstrapCandidates(int capacity) {
            dotCandidatesMs = new long[Math.max(1, capacity)];
        }

        private int count() {
            return count;
        }

        private long candidateAt(int index) {
            if (index < 0 || index >= count) {
                return 0L;
            }
            return dotCandidatesMs[index];
        }

        private void expireIfStale(long timestampMs, long candidateWindowMs) {
            if (count <= 0 || lastTimestampMs <= 0L || timestampMs <= 0L) {
                return;
            }
            if ((timestampMs - lastTimestampMs) >= candidateWindowMs) {
                clear();
            }
        }

        private void append(long candidateDotEstimateMs, long timestampMs) {
            if (candidateDotEstimateMs <= 0L) {
                return;
            }
            if (count < dotCandidatesMs.length) {
                dotCandidatesMs[count] = candidateDotEstimateMs;
                count += 1;
            } else {
                System.arraycopy(dotCandidatesMs, 1, dotCandidatesMs, 0, dotCandidatesMs.length - 1);
                dotCandidatesMs[dotCandidatesMs.length - 1] = candidateDotEstimateMs;
            }
            lastTimestampMs = timestampMs;
        }

        private boolean clustered(int requiredCandidateCount, double maxSpreadWpm) {
            if (count < requiredCandidateCount) {
                return false;
            }
            int startIndex = Math.max(0, count - requiredCandidateCount);
            double minWpm = candidateDotEstimateToWpm(dotCandidatesMs[startIndex]);
            double maxWpm = minWpm;
            for (int index = startIndex + 1; index < count; index++) {
                double candidateWpm = candidateDotEstimateToWpm(dotCandidatesMs[index]);
                minWpm = Math.min(minWpm, candidateWpm);
                maxWpm = Math.max(maxWpm, candidateWpm);
            }
            return (maxWpm - minWpm) <= maxSpreadWpm;
        }

        private double median(int requiredCandidateCount) {
            if (count <= 0 || requiredCandidateCount <= 0) {
                return 0.0d;
            }
            int candidateCount = Math.min(requiredCandidateCount, count);
            long[] sortedCandidates = new long[candidateCount];
            int startIndex = Math.max(0, count - candidateCount);
            System.arraycopy(dotCandidatesMs, startIndex, sortedCandidates, 0, candidateCount);
            java.util.Arrays.sort(sortedCandidates);
            if (candidateCount == 2) {
                return (sortedCandidates[0] + sortedCandidates[1]) / 2.0d;
            }
            return sortedCandidates[candidateCount / 2];
        }

        private void clear() {
            count = 0;
            java.util.Arrays.fill(dotCandidatesMs, 0L);
            lastTimestampMs = -1L;
        }
    }

    private static final class MixedBoundaryStablePair {
        private final double mixedDotEstimateMs;
        private final double spreadWpm;
        private final boolean softStableCandidate;

        private MixedBoundaryStablePair(
                double mixedDotEstimateMs,
                double spreadWpm,
                boolean softStableCandidate
        ) {
            this.mixedDotEstimateMs = mixedDotEstimateMs;
            this.spreadWpm = spreadWpm;
            this.softStableCandidate = softStableCandidate;
        }

        private boolean isBetterThan(MixedBoundaryStablePair other) {
            if (other == null) {
                return true;
            }
            if (spreadWpm < other.spreadWpm) {
                return true;
            }
            if (spreadWpm > other.spreadWpm) {
                return false;
            }
            if (softStableCandidate != other.softStableCandidate) {
                return !softStableCandidate;
            }
            return mixedDotEstimateMs > other.mixedDotEstimateMs;
        }
    }
}
