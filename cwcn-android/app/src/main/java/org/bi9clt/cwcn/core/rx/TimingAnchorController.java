package org.bi9clt.cwcn.core.rx;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;

import java.util.Locale;

/**
 * Local turn timing negative feedback.
 *
 * <p>This controller does not own turn boundaries. It only stabilizes timing
 * inside the current turn once a trusted dot estimate exists.</p>
 */
public final class TimingAnchorController {
    public enum TrustOrigin {
        NONE,
        STABLE,
        BOUNDARY,
        CADENCE
    }

    private static final double STRONG_LOCKED_RATIO_MIN = 0.58d;
    private static final double STRONG_NEAR_TARGET_RATIO_MIN = 0.62d;
    private static final double STRONG_ACTIVE_UNLOCKED_RATIO_MAX = 0.26d;
    private static final double STRONG_TONE_DOMINANCE_MIN = 0.42d;
    private static final double STRONG_ISOLATION_MIN = 0.50d;
    private static final double FAST_DRIFT_FLOOR_RATIO = 0.92d;
    private static final double FAST_DRIFT_RESCUE_FLOOR_RATIO = 0.90d;
    private static final double BOUNDARY_OPENING_FAST_RESCUE_FLOOR_RATIO = 0.84d;
    private static final double BOUNDARY_OPENING_FAST_RESCUE_BLEND = 0.18d;
    private static final double CADENCE_OPENING_EVENT_FAST_FLOOR_RATIO = 0.72d;
    private static final double CADENCE_OPENING_EVENT_LOCKED_RATIO_MIN = 0.45d;
    private static final double CADENCE_OPENING_REANCHOR_SLOW_RATIO = 1.60d;
    private static final double REANCHOR_RAW_DASH_PRESERVE_MIN_RATIO = 1.55d;
    private static final double REANCHOR_RAW_LETTER_GAP_PRESERVE_MIN_RATIO = 1.65d;
    private static final double REANCHOR_RAW_LETTER_GAP_WORD_PROMOTE_MAX_RATIO = 5.05d;
    private static final double REANCHOR_RAW_WORD_GAP_UNKNOWN_MAX_RATIO = 13.25d;
    private static final double NEAR_ANCHOR_LOW_RATIO = 0.96d;
    private static final double NEAR_ANCHOR_HIGH_RATIO = 1.18d;
    private static final double DEBT_FAST_WEAK_INCREASE = 0.45d;
    private static final double DEBT_FAST_STRONG_INCREASE = 0.18d;
    private static final double DEBT_WEAK_INCREASE = 0.06d;
    private static final double DEBT_STRONG_DECAY = 0.22d;
    private static final double FREEZE_DEBT_THRESHOLD = 0.55d;
    private static final double REANCHOR_DEBT_THRESHOLD = 0.85d;
    private static final double MAX_LEARNING_DEBT = 2.0d;
    private static final double TRUSTED_INIT_CONFIDENCE = 0.45d;
    private static final double TRUSTED_CONFIDENCE_INCREMENT = 0.12d;
    private static final double TRUSTED_SLOW_BLEND = 0.12d;
    private static final double TRUSTED_FAST_BLEND = 0.05d;
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
    private static final double BOUNDARY_OPENING_LETTER_GAP_MIN_RATIO = 2.38d;
    private static final double BOUNDARY_OPENING_WORD_GAP_MAX_RATIO = 5.45d;
    private static final int BOUNDARY_OPENING_RELAX_WINDOW_DOTS = 48;
    private static final int BOUNDARY_OPENING_MAX_AMBIGUOUS_GAP_HOLDS = 2;
    private static final int BOUNDARY_OPENING_MAX_FAST_RESCUE_STABLE_DECODES = 10;
    private static final double EVENT_RESCUE_LOCKED_RATIO_MIN = 0.54d;
    private static final double EVENT_RESCUE_NEAR_TARGET_RATIO_MIN = 0.85d;
    private static final double EVENT_RESCUE_ACTIVE_UNLOCKED_RATIO_MAX = 0.08d;
    private static final double TRUSTED_TONE_DASH_LIKE_MIN_RATIO = 1.55d;

    private int seedWpm;
    private double trustedDotEstimateMs;
    private double anchorConfidence;
    private double learningDebt;
    private final PendingBootstrapCandidates pendingStableTrustedCandidates =
            new PendingBootstrapCandidates(MAX_PENDING_BOOTSTRAP_CANDIDATES);
    private final PendingBootstrapCandidates pendingSoftStableTrustedCandidates =
            new PendingBootstrapCandidates(MAX_PENDING_BOOTSTRAP_CANDIDATES);
    private final PendingBootstrapCandidates pendingBoundaryTrustedCandidates =
            new PendingBootstrapCandidates(MAX_PENDING_BOOTSTRAP_CANDIDATES);
    private final PendingBootstrapCandidates pendingCadenceTrustedCandidates =
            new PendingBootstrapCandidates(MAX_PENDING_BOOTSTRAP_CANDIDATES);
    private long lastTrustedUpdateTimestampMs = -1L;
    private long lastObservationTimestampMs = -1L;
    private String lastDecision = "init";
    private TrustOrigin trustOrigin = TrustOrigin.NONE;
    private int postTrustStableDecodeCount;
    private int postTrustAmbiguousGapHoldCount;

    public void reset() {
        trustedDotEstimateMs = 0.0d;
        anchorConfidence = 0.0d;
        learningDebt = 0.0d;
        clearPendingBootstrapCandidates();
        lastTrustedUpdateTimestampMs = -1L;
        lastObservationTimestampMs = -1L;
        lastDecision = "reset";
        trustOrigin = TrustOrigin.NONE;
        postTrustStableDecodeCount = 0;
        postTrustAmbiguousGapHoldCount = 0;
    }

    public void setSeedWpm(int seedWpm) {
        this.seedWpm = Math.max(0, seedWpm);
    }

    public void beginNewTurn(int turnSeedWpm, long timestampMs) {
        seedWpm = Math.max(0, turnSeedWpm);
        trustedDotEstimateMs = 0.0d;
        anchorConfidence = 0.0d;
        learningDebt = 0.0d;
        clearPendingBootstrapCandidates();
        lastTrustedUpdateTimestampMs = -1L;
        lastObservationTimestampMs = timestampMs;
        lastDecision = "turn-reset";
        trustOrigin = TrustOrigin.NONE;
        postTrustStableDecodeCount = 0;
        postTrustAmbiguousGapHoldCount = 0;
    }

    public void noteStableDecode(
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            long timestampMs
    ) {
        boolean trustedBefore = trustedDotEstimateMs > 0.0d;
        noteStableObservation(
                signalSnapshot,
                timingSnapshot,
                timestampMs,
                pendingStableTrustedCandidates,
                INITIAL_TRUSTED_REQUIRED_STABLE_DECODES,
                INITIAL_TRUSTED_CANDIDATE_WINDOW_MS,
                INITIAL_TRUSTED_BOOTSTRAP_MAX_SPREAD_WPM,
                "stable-pending",
                "stable-init",
                TrustOrigin.STABLE
        );
        if (trustedBefore && trustedDotEstimateMs > 0.0d) {
            postTrustStableDecodeCount += 1;
        }
    }

    public void noteBootstrapBoundaryObservation(
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            long timestampMs
    ) {
        noteStableObservation(
                signalSnapshot,
                timingSnapshot,
                timestampMs,
                pendingBoundaryTrustedCandidates,
                BOUNDARY_BOOTSTRAP_REQUIRED_STABLE_DECODES,
                BOUNDARY_BOOTSTRAP_CANDIDATE_WINDOW_MS,
                BOUNDARY_BOOTSTRAP_MAX_SPREAD_WPM,
                "boundary-pending",
                "boundary-init",
                TrustOrigin.BOUNDARY
        );
    }

    public void noteBootstrapBoundaryObservation(long candidateDotEstimateMs, long timestampMs) {
        if (trustedDotEstimateMs > 0.0d) {
            if (candidateDotEstimateMs > trustedDotEstimateMs) {
                trustedDotEstimateMs = blend(
                        trustedDotEstimateMs,
                        candidateDotEstimateMs,
                        TRUSTED_SLOW_BLEND
                );
                anchorConfidence = Math.min(1.0d, anchorConfidence + TRUSTED_CONFIDENCE_INCREMENT);
                learningDebt = Math.max(0.0d, learningDebt - DEBT_STRONG_DECAY);
                lastTrustedUpdateTimestampMs = timestampMs;
                lastDecision = "boundary-slow-update";
            } else {
                lastDecision = "boundary-skip-trusted";
            }
            return;
        }
        if (violatesStableBootstrapBoundaryFloor(candidateDotEstimateMs)) {
            lastDecision = "boundary-reject-fast";
            return;
        }
        noteExplicitBootstrapObservation(
                candidateDotEstimateMs,
                timestampMs,
                pendingBoundaryTrustedCandidates,
                BOUNDARY_BOOTSTRAP_REQUIRED_STABLE_DECODES,
                BOUNDARY_BOOTSTRAP_CANDIDATE_WINDOW_MS,
                BOUNDARY_BOOTSTRAP_MAX_SPREAD_WPM,
                "boundary-pending",
                "boundary-init",
                TrustOrigin.BOUNDARY
        );
    }

    public void noteBootstrapCadenceObservation(long candidateDotEstimateMs, long timestampMs) {
        noteExplicitBootstrapObservation(
                candidateDotEstimateMs,
                timestampMs,
                pendingCadenceTrustedCandidates,
                CADENCE_BOOTSTRAP_REQUIRED_OBSERVATIONS,
                CADENCE_BOOTSTRAP_CANDIDATE_WINDOW_MS,
                CADENCE_BOOTSTRAP_MAX_SPREAD_WPM,
                "cadence-pending",
                "cadence-init",
                TrustOrigin.CADENCE
        );
    }

    public void noteBootstrapSoftStableObservation(long candidateDotEstimateMs, long timestampMs) {
        if (candidateDotEstimateMs <= 0L) {
            lastDecision = "soft-stable-skip-empty";
            return;
        }
        lastObservationTimestampMs = Math.max(lastObservationTimestampMs, timestampMs);
        maybeExpirePendingBootstrapCandidates(
                pendingSoftStableTrustedCandidates,
                timestampMs,
                MIXED_BOUNDARY_STABLE_CANDIDATE_WINDOW_MS
        );
        if (trustedDotEstimateMs > 0.0d) {
            lastDecision = "soft-stable-skip-trusted";
            return;
        }
        pendingSoftStableTrustedCandidates.append(candidateDotEstimateMs, timestampMs);
        if (tryInitializeMixedBoundaryStableBootstrap(timestampMs)) {
            return;
        }
        lastDecision = "soft-stable-pending";
    }

    private void noteStableObservation(
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            long timestampMs,
            PendingBootstrapCandidates pendingCandidates,
            int requiredStableDecodes,
            long candidateWindowMs,
            double maxSpreadWpm,
            String pendingReasonPrefix,
            String initReason,
            TrustOrigin initOrigin
    ) {
        if (timingSnapshot == null || timingSnapshot.dotEstimateMs() <= 0L) {
            lastDecision = "stable-skip-empty";
            return;
        }
        lastObservationTimestampMs = Math.max(lastObservationTimestampMs, timestampMs);
        double candidateDotMs = timingSnapshot.dotEstimateMs();
        maybeExpirePendingBootstrapCandidates(pendingCandidates, timestampMs, candidateWindowMs);
        if (trustedDotEstimateMs <= 0.0d) {
            pendingCandidates.append(candidateDotMs, timestampMs);
            if (pendingCandidates == pendingStableTrustedCandidates
                    && tryInitializeMixedBoundaryStableBootstrap(timestampMs)) {
                return;
            }
            if (pendingCandidates.count() < requiredStableDecodes
                    || !pendingCandidates.clustered(requiredStableDecodes, maxSpreadWpm)) {
                lastDecision = pendingReasonPrefix
                        + "-"
                        + Math.min(pendingCandidates.count(), requiredStableDecodes)
                        + "/"
                        + requiredStableDecodes;
                return;
            }
            trustedDotEstimateMs = pendingCandidates.median(requiredStableDecodes);
            anchorConfidence = TRUSTED_INIT_CONFIDENCE;
            learningDebt = Math.max(0.0d, learningDebt - DEBT_STRONG_DECAY);
            clearPendingBootstrapCandidates();
            lastTrustedUpdateTimestampMs = timestampMs;
            lastDecision = initReason;
            trustOrigin = initOrigin == null ? TrustOrigin.NONE : initOrigin;
            postTrustStableDecodeCount = 0;
            postTrustAmbiguousGapHoldCount = 0;
            return;
        }

        double fastFloorMs = trustedDotEstimateMs * FAST_DRIFT_FLOOR_RATIO;
        if (candidateDotMs < fastFloorMs && !isRescuableFastDriftCandidate(signalSnapshot, candidateDotMs)) {
            increaseDebt(isStrongSignal(signalSnapshot)
                    ? DEBT_FAST_STRONG_INCREASE
                    : DEBT_FAST_WEAK_INCREASE);
            lastDecision = "stable-reject-fast";
            return;
        }
        boolean boundaryOpeningFastRescue = isBoundaryOpeningFastRescueCandidate(
                signalSnapshot,
                candidateDotMs
        );
        if (candidateDotMs < fastFloorMs) {
            lastDecision = boundaryOpeningFastRescue
                    ? "stable-rescue-boundary-opening"
                    : "stable-rescue-fast";
        }

        double blend = candidateDotMs >= trustedDotEstimateMs
                ? TRUSTED_SLOW_BLEND
                : TRUSTED_FAST_BLEND;
        if (boundaryOpeningFastRescue) {
            blend = Math.max(blend, BOUNDARY_OPENING_FAST_RESCUE_BLEND);
        }
        trustedDotEstimateMs = blend(trustedDotEstimateMs, candidateDotMs, blend);
        anchorConfidence = Math.min(1.0d, anchorConfidence + TRUSTED_CONFIDENCE_INCREMENT);
        learningDebt = Math.max(0.0d, learningDebt - DEBT_STRONG_DECAY);
        lastTrustedUpdateTimestampMs = timestampMs;
        lastDecision = "stable-update";
    }

    private void noteExplicitBootstrapObservation(
            long candidateDotMs,
            long timestampMs,
            PendingBootstrapCandidates pendingCandidates,
            int requiredObservations,
            long candidateWindowMs,
            double maxSpreadWpm,
            String pendingReasonPrefix,
            String initReason,
            TrustOrigin initOrigin
    ) {
        if (candidateDotMs <= 0L) {
            lastDecision = "stable-skip-empty";
            return;
        }
        lastObservationTimestampMs = Math.max(lastObservationTimestampMs, timestampMs);
        maybeExpirePendingBootstrapCandidates(pendingCandidates, timestampMs, candidateWindowMs);
        if (trustedDotEstimateMs > 0.0d) {
            lastDecision = "stable-skip-trusted";
            return;
        }
        pendingCandidates.append(candidateDotMs, timestampMs);
        if (pendingCandidates == pendingBoundaryTrustedCandidates
                && tryInitializeMixedBoundaryStableBootstrap(timestampMs)) {
            return;
        }
        if (pendingCandidates.count() < requiredObservations
                || !pendingCandidates.clustered(requiredObservations, maxSpreadWpm)) {
            lastDecision = pendingReasonPrefix
                    + "-"
                    + Math.min(pendingCandidates.count(), requiredObservations)
                    + "/"
                    + requiredObservations;
            return;
        }
        trustedDotEstimateMs = pendingCandidates.median(requiredObservations);
        anchorConfidence = TRUSTED_INIT_CONFIDENCE;
        learningDebt = Math.max(0.0d, learningDebt - DEBT_STRONG_DECAY);
        clearPendingBootstrapCandidates();
        lastTrustedUpdateTimestampMs = timestampMs;
        lastDecision = initReason;
        trustOrigin = initOrigin == null ? TrustOrigin.NONE : initOrigin;
        postTrustStableDecodeCount = 0;
        postTrustAmbiguousGapHoldCount = 0;
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
        anchorConfidence = TRUSTED_INIT_CONFIDENCE;
        learningDebt = Math.max(0.0d, learningDebt - DEBT_STRONG_DECAY);
        clearPendingBootstrapCandidates();
        lastTrustedUpdateTimestampMs = timestampMs;
        lastDecision = mixedPair.softStableCandidate
                ? "boundary-soft-stable-init"
                : "boundary-stable-init";
        trustOrigin = TrustOrigin.BOUNDARY;
        postTrustStableDecodeCount = 0;
        postTrustAmbiguousGapHoldCount = 0;
        return true;
    }

    @Nullable
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

    public boolean shouldAcceptStableAnchorUpdate(
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            boolean baseAccept,
            long timestampMs
    ) {
        if (!baseAccept) {
            return false;
        }
        if (trustedDotEstimateMs <= 0.0d
                || timingSnapshot == null
                || timingSnapshot.dotEstimateMs() <= 0L) {
            return true;
        }
        lastObservationTimestampMs = Math.max(lastObservationTimestampMs, timestampMs);
        if (violatesFastDriftFloor(signalSnapshot, timingSnapshot.dotEstimateMs())) {
            increaseDebt(isStrongSignal(signalSnapshot)
                    ? DEBT_FAST_STRONG_INCREASE
                    : DEBT_FAST_WEAK_INCREASE);
            lastDecision = "stable-block-fast";
            return false;
        }
        if (timingSnapshot.dotEstimateMs() < trustedDotEstimateMs * FAST_DRIFT_FLOOR_RATIO) {
            lastDecision = "stable-allow-fast-rescue";
        }
        return true;
    }

    public boolean shouldAllowTimingLearning(
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            boolean baseAllow,
            long timestampMs
    ) {
        updateDebtFromSnapshot(signalSnapshot, timingSnapshot, timestampMs);
        if (!baseAllow) {
            return false;
        }
        if (trustedDotEstimateMs <= 0.0d) {
            return true;
        }
        if (shouldDelayCadenceOpeningNegativeFeedback(timestampMs)) {
            lastDecision = "allow-cadence-opening-hold";
            return true;
        }
        if (isStrongSignal(signalSnapshot) && isNearTrustedAnchor(timingSnapshot)) {
            lastDecision = "allow-near-anchor";
            return true;
        }
        if (learningDebt >= FREEZE_DEBT_THRESHOLD) {
            lastDecision = "freeze-debt";
            return false;
        }
        if (timingSnapshot != null
                && timingSnapshot.dotEstimateMs() > 0L
                && violatesFastDriftFloor(signalSnapshot, timingSnapshot.dotEstimateMs())) {
            lastDecision = "freeze-fast";
            return false;
        }
        if (timingSnapshot != null
                && timingSnapshot.dotEstimateMs() > 0L
                && timingSnapshot.dotEstimateMs() < trustedDotEstimateMs * FAST_DRIFT_FLOOR_RATIO) {
            lastDecision = "allow-fast-rescue";
        }
        return true;
    }

    public boolean shouldAllowTimingLearningForEvent(
            @Nullable CwToneEvent toneEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            boolean baseAllow,
            long timestampMs
    ) {
        if (trustedDotEstimateMs > 0.0d && shouldDelayCadenceOpeningNegativeFeedback(timestampMs)) {
            if (!baseAllow) {
                return false;
            }
            lastDecision = "allow-event-cadence-opening-hold";
            return true;
        }
        if (trustedDotEstimateMs > 0.0d && toneEvent != null) {
            long candidateDotMs = inferEventDotCandidateMs(toneEvent, trustedDotEstimateMs);
            if (candidateDotMs > 0L && violatesFastDriftFloor(signalSnapshot, candidateDotMs)) {
                if (shouldAllowCadenceOpeningFastEventLearning(
                        toneEvent,
                        signalSnapshot,
                        candidateDotMs,
                        timestampMs
                )) {
                    lastDecision = "allow-event-cadence-opening";
                } else {
                    increaseDebt(isStrongSignal(signalSnapshot)
                            ? DEBT_FAST_STRONG_INCREASE
                            : DEBT_FAST_WEAK_INCREASE);
                    lastDecision = "freeze-event-fast";
                    return false;
                }
            }
            if (candidateDotMs > 0L && candidateDotMs < trustedDotEstimateMs * FAST_DRIFT_FLOOR_RATIO) {
                lastDecision = "allow-event-fast-rescue";
            }
        }
        if (baseAllow && shouldAllowDebtRescueForEvent(toneEvent, signalSnapshot, timingSnapshot)) {
            lastDecision = "allow-event-debt-rescue";
            return true;
        }
        return shouldAllowTimingLearning(signalSnapshot, timingSnapshot, baseAllow, timestampMs);
    }

    @Nullable
    public CwTimingEvent adaptTimingEvent(
            @Nullable CwTimingEvent timingEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            long timestampMs
    ) {
        if (timingEvent == null || trustedDotEstimateMs <= 0.0d) {
            return timingEvent;
        }
        updateDebtFromSnapshot(signalSnapshot, timingSnapshot, timestampMs);
        long anchorDotMs = Math.max(1L, Math.round(trustedDotEstimateMs));
        if (!shouldReanchorTimingEvent(
                timingEvent,
                signalSnapshot,
                timingSnapshot,
                anchorDotMs,
                timestampMs
        )) {
            return timingEvent;
        }
        CwTimingEvent.Classification classification = timingEvent.kind() == CwTimingEvent.Kind.TONE
                ? classifyTone(timingEvent.durationMs(), anchorDotMs)
                : classifyGap(timingEvent.durationMs(), anchorDotMs);
        if (shouldPreserveRawDashClassification(timingEvent, classification, anchorDotMs)) {
            classification = CwTimingEvent.Classification.DAH;
        } else if (shouldPreserveRawLetterGapClassification(timingEvent, classification, anchorDotMs)) {
            classification = CwTimingEvent.Classification.LETTER_GAP;
        } else if (shouldPreserveRawWordGapClassification(timingEvent, classification, anchorDotMs)) {
            classification = CwTimingEvent.Classification.WORD_GAP;
        }
        if (shouldHoldBoundaryOpeningGap(
                timingEvent,
                classification,
                anchorDotMs,
                timestampMs
        )) {
            classification = CwTimingEvent.Classification.INTRA_SYMBOL_GAP;
            postTrustAmbiguousGapHoldCount += 1;
            lastDecision = "reanchor-boundary-gap-hold";
        } else if (shouldPreferCadenceOpeningAnchor(
                timingEvent,
                signalSnapshot,
                timingSnapshot,
                anchorDotMs,
                timestampMs
        )) {
            lastDecision = "reanchor-cadence-opening";
        } else {
            lastDecision = "reanchor-event";
        }
        return new CwTimingEvent(
                timingEvent.kind(),
                classification,
                timingEvent.timestampMs(),
                timingEvent.durationMs(),
                anchorDotMs,
                anchorDotMs
        );
    }

    private boolean shouldHoldBoundaryOpeningGap(
            @Nullable CwTimingEvent timingEvent,
            CwTimingEvent.Classification classification,
            long anchorDotMs,
            long timestampMs
    ) {
        if (timingEvent == null
                || trustOrigin != TrustOrigin.BOUNDARY
                || (classification != CwTimingEvent.Classification.LETTER_GAP
                && classification != CwTimingEvent.Classification.WORD_GAP)
                || anchorDotMs <= 0L
                || lastTrustedUpdateTimestampMs <= 0L
                || postTrustStableDecodeCount > 0
                || postTrustAmbiguousGapHoldCount >= BOUNDARY_OPENING_MAX_AMBIGUOUS_GAP_HOLDS) {
            return false;
        }
        long relaxWindowMs = anchorDotMs * BOUNDARY_OPENING_RELAX_WINDOW_DOTS;
        if (timestampMs - lastTrustedUpdateTimestampMs > relaxWindowMs) {
            return false;
        }
        double gapRatio = timingEvent.durationMs() / (double) anchorDotMs;
        if (classification == CwTimingEvent.Classification.LETTER_GAP) {
            return gapRatio < BOUNDARY_OPENING_LETTER_GAP_MIN_RATIO;
        }
        return gapRatio < BOUNDARY_OPENING_WORD_GAP_MAX_RATIO;
    }

    private boolean shouldPreserveRawDashClassification(
            @Nullable CwTimingEvent timingEvent,
            CwTimingEvent.Classification classification,
            long anchorDotMs
    ) {
        if (timingEvent == null
                || timingEvent.kind() != CwTimingEvent.Kind.TONE
                || timingEvent.classification() != CwTimingEvent.Classification.DAH
                || classification != CwTimingEvent.Classification.DIT) {
            return false;
        }
        long rawDotMs = Math.max(1L, timingEvent.dotEstimateMs());
        if (anchorDotMs <= rawDotMs) {
            return false;
        }
        double ratioToAnchorDot = timingEvent.durationMs() / (double) Math.max(1L, anchorDotMs);
        return ratioToAnchorDot >= REANCHOR_RAW_DASH_PRESERVE_MIN_RATIO;
    }

    private boolean shouldPreserveRawLetterGapClassification(
            @Nullable CwTimingEvent timingEvent,
            CwTimingEvent.Classification classification,
            long anchorDotMs
    ) {
        if (timingEvent == null
                || timingEvent.kind() != CwTimingEvent.Kind.GAP
                || timingEvent.classification() != CwTimingEvent.Classification.LETTER_GAP
                || (classification != CwTimingEvent.Classification.INTRA_SYMBOL_GAP
                && classification != CwTimingEvent.Classification.WORD_GAP)) {
            return false;
        }
        long rawDotMs = Math.max(1L, timingEvent.dotEstimateMs());
        double ratioToAnchorDot = timingEvent.durationMs() / (double) Math.max(1L, anchorDotMs);
        if (classification == CwTimingEvent.Classification.INTRA_SYMBOL_GAP) {
            if (anchorDotMs <= rawDotMs) {
                return false;
            }
            return ratioToAnchorDot >= REANCHOR_RAW_LETTER_GAP_PRESERVE_MIN_RATIO;
        }
        if (anchorDotMs >= rawDotMs) {
            return false;
        }
        return ratioToAnchorDot <= REANCHOR_RAW_LETTER_GAP_WORD_PROMOTE_MAX_RATIO;
    }

    private boolean shouldPreserveRawWordGapClassification(
            @Nullable CwTimingEvent timingEvent,
            CwTimingEvent.Classification classification,
            long anchorDotMs
    ) {
        if (timingEvent == null
                || timingEvent.kind() != CwTimingEvent.Kind.GAP
                || timingEvent.classification() != CwTimingEvent.Classification.WORD_GAP
                || classification != CwTimingEvent.Classification.UNKNOWN) {
            return false;
        }
        long rawDotMs = Math.max(1L, timingEvent.dotEstimateMs());
        if (anchorDotMs >= rawDotMs) {
            return false;
        }
        double ratioToAnchorDot = timingEvent.durationMs() / (double) Math.max(1L, anchorDotMs);
        return ratioToAnchorDot <= REANCHOR_RAW_WORD_GAP_UNKNOWN_MAX_RATIO;
    }

    public long trustedDotEstimateMs() {
        return trustedDotEstimateMs <= 0.0d ? 0L : Math.round(trustedDotEstimateMs);
    }

    public TrustOrigin trustOrigin() {
        return trustedDotEstimateMs() > 0L ? trustOrigin : TrustOrigin.NONE;
    }

    public long lastTrustedUpdateTimestampMs() {
        return trustedDotEstimateMs() > 0L ? lastTrustedUpdateTimestampMs : -1L;
    }

    public double learningDebt() {
        return learningDebt;
    }

    public String compactDebugSummary() {
        return "anc dot="
                + positiveOrDash(trustedDotEstimateMs())
                + " debt="
                + String.format(Locale.US, "%.2f", learningDebt)
                + " conf="
                + String.format(Locale.US, "%.2f", anchorConfidence)
                + " "
                + lastDecision;
    }

    private void updateDebtFromSnapshot(
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            long timestampMs
    ) {
        lastObservationTimestampMs = Math.max(lastObservationTimestampMs, timestampMs);
        if (trustedDotEstimateMs <= 0.0d || timingSnapshot == null || timingSnapshot.dotEstimateMs() <= 0L) {
            return;
        }
        if (shouldDelayCadenceOpeningNegativeFeedback(timestampMs)) {
            lastDecision = "debt-cadence-opening-hold";
            return;
        }
        double rawDotMs = timingSnapshot.dotEstimateMs();
        boolean strongSignal = isStrongSignal(signalSnapshot);
        if (violatesFastDriftFloor(signalSnapshot, rawDotMs)) {
            increaseDebt(strongSignal ? DEBT_FAST_STRONG_INCREASE : DEBT_FAST_WEAK_INCREASE);
            lastDecision = "debt-fast";
            return;
        }
        if (rawDotMs < trustedDotEstimateMs * FAST_DRIFT_FLOOR_RATIO) {
            learningDebt = Math.max(0.0d, learningDebt - DEBT_STRONG_DECAY);
            lastDecision = "debt-fast-rescue";
            return;
        }
        double ratio = rawDotMs / Math.max(1.0d, trustedDotEstimateMs);
        if (strongSignal && ratio >= NEAR_ANCHOR_LOW_RATIO && ratio <= NEAR_ANCHOR_HIGH_RATIO) {
            learningDebt = Math.max(0.0d, learningDebt - DEBT_STRONG_DECAY);
            lastDecision = "debt-decay";
        } else if (!strongSignal) {
            increaseDebt(DEBT_WEAK_INCREASE);
            lastDecision = "debt-weak";
        }
    }

    private boolean shouldReanchorTimingEvent(
            CwTimingEvent timingEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            long anchorDotMs,
            long timestampMs
    ) {
        if (shouldPreferCadenceOpeningAnchor(
                timingEvent,
                signalSnapshot,
                timingSnapshot,
                anchorDotMs,
                timestampMs
        )) {
            return true;
        }
        if (learningDebt >= REANCHOR_DEBT_THRESHOLD) {
            return true;
        }
        long eventDotMs = timingEvent.dotEstimateMs();
        if (eventDotMs > 0L && eventDotMs < anchorDotMs * FAST_DRIFT_FLOOR_RATIO) {
            return true;
        }
        return timingSnapshot != null
                && timingSnapshot.dotEstimateMs() > 0L
                && timingSnapshot.dotEstimateMs() < anchorDotMs * FAST_DRIFT_FLOOR_RATIO;
    }

    private boolean shouldPreferCadenceOpeningAnchor(
            @Nullable CwTimingEvent timingEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            long anchorDotMs,
            long timestampMs
    ) {
        if (trustedDotEstimateMs <= 0.0d
                || trustOrigin != TrustOrigin.CADENCE
                || postTrustStableDecodeCount <= 0
                || signalSnapshot == null
                || anchorDotMs <= 0L
                || lastTrustedUpdateTimestampMs <= 0L) {
            return false;
        }
        long relaxWindowMs = Math.max(1L, anchorDotMs * CADENCE_OPENING_RELAX_WINDOW_DOTS);
        if ((timestampMs - lastTrustedUpdateTimestampMs) > relaxWindowMs
                || !signalSnapshot.targetToneLocked()
                || signalSnapshot.recentLockedFrameRatio() < CADENCE_OPENING_EVENT_LOCKED_RATIO_MIN
                || signalSnapshot.recentActiveUnlockedFrameRatio() > EVENT_RESCUE_ACTIVE_UNLOCKED_RATIO_MAX) {
            return false;
        }
        long referenceRawDotMs = timingEvent != null && timingEvent.dotEstimateMs() > 0L
                ? timingEvent.dotEstimateMs()
                : timingSnapshot == null ? 0L : timingSnapshot.dotEstimateMs();
        return referenceRawDotMs >= Math.round(anchorDotMs * CADENCE_OPENING_REANCHOR_SLOW_RATIO);
    }

    private boolean violatesFastDriftFloor(
            @Nullable CwSignalSnapshot signalSnapshot,
            double candidateDotMs
    ) {
        return trustedDotEstimateMs > 0.0d
                && candidateDotMs > 0.0d
                && candidateDotMs < (trustedDotEstimateMs * FAST_DRIFT_FLOOR_RATIO)
                && !isRescuableFastDriftCandidate(signalSnapshot, candidateDotMs);
    }

    private boolean isRescuableFastDriftCandidate(
            @Nullable CwSignalSnapshot signalSnapshot,
            double candidateDotMs
    ) {
        return trustedDotEstimateMs > 0.0d
                && candidateDotMs > 0.0d
                && ((candidateDotMs >= (trustedDotEstimateMs * FAST_DRIFT_RESCUE_FLOOR_RATIO)
                && RxStableDecodeDecider.passesStableDecodeShape(signalSnapshot, true))
                || isBoundaryOpeningFastRescueCandidate(signalSnapshot, candidateDotMs));
    }

    private boolean isBoundaryOpeningFastRescueCandidate(
            @Nullable CwSignalSnapshot signalSnapshot,
            double candidateDotMs
    ) {
        if (trustedDotEstimateMs <= 0.0d
                || trustOrigin != TrustOrigin.BOUNDARY
                || signalSnapshot == null
                || candidateDotMs <= 0.0d
                || postTrustStableDecodeCount >= BOUNDARY_OPENING_MAX_FAST_RESCUE_STABLE_DECODES
                || lastTrustedUpdateTimestampMs <= 0L) {
            return false;
        }
        long relaxWindowMs = Math.max(1L, Math.round(trustedDotEstimateMs * BOUNDARY_OPENING_RELAX_WINDOW_DOTS));
        if ((lastObservationTimestampMs - lastTrustedUpdateTimestampMs) > relaxWindowMs) {
            return false;
        }
        return candidateDotMs >= (trustedDotEstimateMs * BOUNDARY_OPENING_FAST_RESCUE_FLOOR_RATIO)
                && RxStableDecodeDecider.passesStableDecodeShape(signalSnapshot, true);
    }

    private boolean isNearTrustedAnchor(@Nullable CwTimingSnapshot timingSnapshot) {
        if (trustedDotEstimateMs <= 0.0d || timingSnapshot == null || timingSnapshot.dotEstimateMs() <= 0L) {
            return false;
        }
        double ratio = timingSnapshot.dotEstimateMs() / Math.max(1.0d, trustedDotEstimateMs);
        return ratio >= NEAR_ANCHOR_LOW_RATIO && ratio <= NEAR_ANCHOR_HIGH_RATIO;
    }

    private boolean shouldAllowDebtRescueForEvent(
            @Nullable CwToneEvent toneEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot
    ) {
        if (trustedDotEstimateMs <= 0.0d
                || toneEvent == null
                || timingSnapshot == null
                || timingSnapshot.dotEstimateMs() <= 0L
                || !passesTrustedEventRescueShape(toneEvent, signalSnapshot)) {
            return false;
        }
        double ratio = timingSnapshot.dotEstimateMs() / Math.max(1.0d, trustedDotEstimateMs);
        return ratio >= FAST_DRIFT_FLOOR_RATIO && ratio <= NEAR_ANCHOR_HIGH_RATIO;
    }

    private boolean shouldAllowCadenceOpeningFastEventLearning(
            @Nullable CwToneEvent toneEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            long candidateDotMs,
            long timestampMs
    ) {
        if (trustedDotEstimateMs <= 0.0d
                || trustOrigin != TrustOrigin.CADENCE
                || postTrustStableDecodeCount > 0
                || toneEvent == null
                || toneEvent.type() != CwToneEvent.Type.TONE_OFF
                || signalSnapshot == null
                || candidateDotMs <= 0L
                || lastTrustedUpdateTimestampMs <= 0L) {
            return false;
        }
        long relaxWindowMs = Math.max(1L, Math.round(trustedDotEstimateMs * CADENCE_OPENING_RELAX_WINDOW_DOTS));
        if ((timestampMs - lastTrustedUpdateTimestampMs) > relaxWindowMs) {
            return false;
        }
        return candidateDotMs >= Math.round(trustedDotEstimateMs * CADENCE_OPENING_EVENT_FAST_FLOOR_RATIO)
                && signalSnapshot.targetToneLocked()
                && signalSnapshot.recentLockedFrameRatio() >= CADENCE_OPENING_EVENT_LOCKED_RATIO_MIN
                && signalSnapshot.recentActiveUnlockedFrameRatio() <= EVENT_RESCUE_ACTIVE_UNLOCKED_RATIO_MAX;
    }

    private boolean shouldDelayCadenceOpeningNegativeFeedback(long timestampMs) {
        if (trustedDotEstimateMs <= 0.0d
                || trustOrigin != TrustOrigin.CADENCE
                || postTrustStableDecodeCount > 0
                || lastTrustedUpdateTimestampMs <= 0L
                || timestampMs <= 0L) {
            return false;
        }
        long relaxWindowMs = Math.max(1L, Math.round(trustedDotEstimateMs * CADENCE_OPENING_RELAX_WINDOW_DOTS));
        return (timestampMs - lastTrustedUpdateTimestampMs) <= relaxWindowMs;
    }

    private long inferEventDotCandidateMs(CwToneEvent toneEvent, double anchorDotMs) {
        if (toneEvent.type() == CwToneEvent.Type.TONE_OFF) {
            long toneDurationMs = Math.max(0L, toneEvent.toneDurationMs());
            if (toneDurationMs <= 0L) {
                return 0L;
            }
            double toneRatio = toneDurationMs / Math.max(1.0d, anchorDotMs);
            if (toneRatio >= TRUSTED_TONE_DASH_LIKE_MIN_RATIO && toneRatio <= 5.2d) {
                return Math.max(1L, Math.round(toneDurationMs / 3.0d));
            }
            return toneDurationMs;
        }
        return 0L;
    }

    private boolean passesTrustedEventRescueShape(
            CwToneEvent toneEvent,
            @Nullable CwSignalSnapshot signalSnapshot
    ) {
        if (signalSnapshot == null) {
            return false;
        }
        if (toneEvent.type() == CwToneEvent.Type.TONE_ON) {
            return isStrongSignal(signalSnapshot);
        }
        return passesTrustedToneOffRescueShape(signalSnapshot);
    }

    private boolean passesTrustedToneOffRescueShape(@Nullable CwSignalSnapshot signalSnapshot) {
        return signalSnapshot != null
                && signalSnapshot.recentLockedFrameRatio() >= EVENT_RESCUE_LOCKED_RATIO_MIN
                && signalSnapshot.recentActiveUnlockedFrameRatio() <= EVENT_RESCUE_ACTIVE_UNLOCKED_RATIO_MAX
                && (signalSnapshot.targetToneLocked()
                || signalSnapshot.recentNearTargetLockedFrameRatio() >= EVENT_RESCUE_NEAR_TARGET_RATIO_MIN);
    }

    private boolean isStrongSignal(@Nullable CwSignalSnapshot signalSnapshot) {
        if (signalSnapshot == null) {
            return false;
        }
        boolean hasStrongLockContext = signalSnapshot.recentLockedFrameRatio() >= STRONG_LOCKED_RATIO_MIN
                && (signalSnapshot.targetToneLocked()
                || signalSnapshot.recentNearTargetLockedFrameRatio() >= STRONG_NEAR_TARGET_RATIO_MIN);
        return hasStrongLockContext
                && signalSnapshot.recentActiveUnlockedFrameRatio() <= STRONG_ACTIVE_UNLOCKED_RATIO_MAX
                && signalSnapshot.toneDominanceRatio() >= STRONG_TONE_DOMINANCE_MIN
                && signalSnapshot.narrowbandIsolationRatio() >= STRONG_ISOLATION_MIN;
    }

    private void increaseDebt(double amount) {
        learningDebt = Math.min(MAX_LEARNING_DEBT, learningDebt + Math.max(0.0d, amount));
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

        private boolean isBetterThan(@Nullable MixedBoundaryStablePair other) {
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


    private double blend(double currentValue, double newValue, double ratio) {
        return currentValue + ((newValue - currentValue) * ratio);
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

        private void append(double candidateDotMs, long timestampMs) {
            long roundedCandidateDotMs = Math.max(1L, Math.round(candidateDotMs));
            if (count < dotCandidatesMs.length) {
                dotCandidatesMs[count] = roundedCandidateDotMs;
                count += 1;
            } else {
                System.arraycopy(dotCandidatesMs, 1, dotCandidatesMs, 0, dotCandidatesMs.length - 1);
                dotCandidatesMs[dotCandidatesMs.length - 1] = roundedCandidateDotMs;
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

    private CwTimingEvent.Classification classifyGap(long gapDurationMs, long dotEstimateMs) {
        double ratio = gapDurationMs / (double) Math.max(1L, dotEstimateMs);
        if (ratio <= 1.8d) {
            return CwTimingEvent.Classification.INTRA_SYMBOL_GAP;
        }
        if (ratio <= 4.70d) {
            return CwTimingEvent.Classification.LETTER_GAP;
        }
        if (ratio <= 12.8d) {
            return CwTimingEvent.Classification.WORD_GAP;
        }
        return CwTimingEvent.Classification.UNKNOWN;
    }

    private String positiveOrDash(long value) {
        return value > 0L ? String.valueOf(value) : "-";
    }
}
