package org.bi9clt.cwcn.core.rx;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.audio.AudioInputHealthSnapshot;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;

/**
 * Shared bootstrap observation helper for early timing trust formation.
 */
public final class RxBootstrapTimingObserver {
    private static final long BOOTSTRAP_CADENCE_MIN_DOT_MS = 45L;
    private static final long BOOTSTRAP_CADENCE_MIN_GAP_MS = 24L;
    private static final long BOOTSTRAP_CADENCE_MAX_GAP_MS = 85L;
    private static final double BOOTSTRAP_CADENCE_MIN_GAP_TO_DOT_RATIO = 0.78d;
    private static final double BOOTSTRAP_CADENCE_MAX_GAP_TO_DOT_RATIO = 1.22d;

    private RxBootstrapTimingObserver() {
    }

    public static boolean maybeNoteBootstrapTimingBoundary(
            @Nullable CwTimingEvent timingEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot,
            @Nullable CwHybridTimingModel timingModel,
            @Nullable LiveRxWpmGuard wpmGuard,
            @Nullable TimingAnchorController timingAnchorController,
            @Nullable CwFrontEndLearningGate frontEndLearningGate,
            @Nullable RxTurnController turnController
    ) {
        if (!shouldTreatAsBootstrapTimingBoundary(
                timingEvent,
                signalSnapshot,
                timingSnapshot,
                inputHealthSnapshot,
                frontEndLearningGate,
                wpmGuard,
                timingAnchorController
        ) || timingModel == null) {
            return false;
        }
        long candidateDotEstimateMs = inferBootstrapBoundaryDotEstimateMs(
                timingEvent,
                timingSnapshot
        );
        if (candidateDotEstimateMs <= 0L) {
            return false;
        }
        applyBootstrapTimingBoundaryObservation(
                candidateDotEstimateMs,
                timingEvent.timestampMs(),
                timingModel,
                timingAnchorController,
                turnController
        );
        return true;
    }

    public static void applyBootstrapTimingBoundaryObservation(
            long candidateDotEstimateMs,
            long timestampMs,
            @Nullable CwHybridTimingModel timingModel,
            @Nullable TimingAnchorController timingAnchorController,
            @Nullable RxTurnController turnController
    ) {
        if (candidateDotEstimateMs <= 0L || timingModel == null) {
            return;
        }
        timingModel.noteBootstrapBoundaryObservation(
                candidateDotEstimateMs,
                timestampMs
        );
        CwTimingSnapshot updatedTimingSnapshot = timingModel.rawSnapshot();
        if (timingAnchorController != null) {
            timingAnchorController.noteBootstrapBoundaryObservation(
                    candidateDotEstimateMs,
                    timestampMs
            );
        }
        if (turnController != null && updatedTimingSnapshot != null) {
            int anchorWpm = updatedTimingSnapshot.estimatedWpm() > 0
                    ? updatedTimingSnapshot.estimatedWpm()
                    : (int) Math.round(Math.max(
                    0.0d,
                    updatedTimingSnapshot.estimatedWpmPrecise()
            ));
            boolean carryEligible = timingModel.debugSnapshot().trustedDotEstimateMs() > 0.0d;
            turnController.noteStableDecode(
                    timestampMs,
                    anchorWpm,
                    carryEligible
            );
        }
    }

    public static boolean maybeNoteBootstrapCadenceObservation(
            @Nullable CwTimingEvent timingEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot,
            @Nullable CwHybridTimingModel timingModel,
            @Nullable LiveRxWpmGuard wpmGuard,
            @Nullable TimingAnchorController timingAnchorController,
            @Nullable CwFrontEndLearningGate frontEndLearningGate
    ) {
        if (!shouldTreatAsBootstrapCadenceObservation(
                timingEvent,
                signalSnapshot,
                timingSnapshot,
                inputHealthSnapshot,
                frontEndLearningGate,
                wpmGuard,
                timingAnchorController
        ) || timingModel == null) {
            return false;
        }
        applyBootstrapCadenceObservation(
                Math.max(0L, timingEvent.durationMs()),
                timingEvent.timestampMs(),
                timingModel,
                timingAnchorController
        );
        return true;
    }

    public static void applyBootstrapCadenceObservation(
            long candidateDotEstimateMs,
            long timestampMs,
            @Nullable CwHybridTimingModel timingModel,
            @Nullable TimingAnchorController timingAnchorController
    ) {
        if (candidateDotEstimateMs <= 0L || timingModel == null) {
            return;
        }
        timingModel.noteBootstrapCadenceObservation(
                candidateDotEstimateMs,
                timestampMs
        );
        if (timingAnchorController != null) {
            timingAnchorController.noteBootstrapCadenceObservation(
                    candidateDotEstimateMs,
                    timestampMs
            );
        }
    }

    public static boolean shouldTreatAsBootstrapTimingBoundary(
            @Nullable CwTimingEvent timingEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot,
            @Nullable CwFrontEndLearningGate frontEndLearningGate,
            @Nullable LiveRxWpmGuard wpmGuard,
            @Nullable TimingAnchorController timingAnchorController
    ) {
        return "pass".equals(diagnoseBootstrapTimingBoundaryDecision(
                timingEvent,
                signalSnapshot,
                timingSnapshot,
                inputHealthSnapshot,
                frontEndLearningGate,
                wpmGuard,
                timingAnchorController
        ));
    }

    public static String diagnoseBootstrapTimingBoundaryDecision(
            @Nullable CwTimingEvent timingEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot,
            @Nullable CwFrontEndLearningGate frontEndLearningGate,
            @Nullable LiveRxWpmGuard wpmGuard,
            @Nullable TimingAnchorController timingAnchorController
    ) {
        if (timingEvent == null) {
            return "null-event";
        }
        if (timingEvent.kind() != CwTimingEvent.Kind.GAP) {
            return "not-gap";
        }
        if (signalSnapshot == null || timingSnapshot == null) {
            return "missing-snapshot";
        }
        if (timingSnapshot.estimatedWpm() <= 0) {
            return "no-wpm";
        }
        if (timingSnapshot.dotEstimateMs() <= 0L) {
            return "no-dot";
        }
        long trustedDotEstimateMs = timingAnchorController == null
                ? 0L
                : timingAnchorController.trustedDotEstimateMs();
        long candidateDotEstimateMs = inferBootstrapBoundaryDotEstimateMs(
                timingEvent,
                timingSnapshot
        );
        if (trustedDotEstimateMs > 0L && candidateDotEstimateMs <= trustedDotEstimateMs) {
            return "already-trusted";
        }
        boolean trustedSlowBoundaryCandidate = trustedDotEstimateMs > 0L
                && candidateDotEstimateMs > (trustedDotEstimateMs + 2L)
                && candidateDotEstimateMs <= Math.round(trustedDotEstimateMs * 1.25d);
        if (!isBootstrapCadenceGap(timingEvent, timingSnapshot)) {
            return "not-bootstrap-gap";
        }
        if (!trustedSlowBoundaryCandidate) {
            if (wpmGuard != null
                    && !wpmGuard.shouldAcceptTimingAnchorUpdate(
                    signalSnapshot,
                    timingSnapshot,
                    timingEvent.timestampMs()
            )) {
                return "wpm-anchor-guard";
            }
            if (timingAnchorController != null
                    && !timingAnchorController.shouldAcceptStableAnchorUpdate(
                    signalSnapshot,
                    timingSnapshot,
                    true,
                    timingEvent.timestampMs()
            )) {
                return "anchor-guard";
            }
        }
        if (frontEndLearningGate != null
                && !frontEndLearningGate.shouldAllowBootstrapBoundaryAnchorUpdate(
                signalSnapshot,
                inputHealthSnapshot
        )) {
            return "front-end-learning";
        }
        if (!trustedSlowBoundaryCandidate
                && wpmGuard != null
                && !wpmGuard.shouldAcceptBootstrapBoundaryAnchorUpdate(
                signalSnapshot,
                timingSnapshot,
                timingEvent.timestampMs()
        )) {
            return "wpm-boundary-guard";
        }
        return RxStableDecodeDecider.passesBootstrapBoundaryShape(
                signalSnapshot.targetToneLocked(),
                signalSnapshot.recentLockedFrameRatio(),
                signalSnapshot.recentNearTargetLockedFrameRatio(),
                signalSnapshot.recentActiveUnlockedFrameRatio(),
                signalSnapshot.toneDominanceRatio(),
                signalSnapshot.narrowbandIsolationRatio()
        ) ? "pass" : "shape";
    }

    public static boolean shouldTreatAsBootstrapCadenceObservation(
            @Nullable CwTimingEvent timingEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot,
            @Nullable CwFrontEndLearningGate frontEndLearningGate,
            @Nullable LiveRxWpmGuard wpmGuard,
            @Nullable TimingAnchorController timingAnchorController
    ) {
        return "pass".equals(diagnoseBootstrapCadenceDecision(
                timingEvent,
                signalSnapshot,
                timingSnapshot,
                inputHealthSnapshot,
                frontEndLearningGate,
                wpmGuard,
                timingAnchorController
        ));
    }

    public static String diagnoseBootstrapCadenceDecision(
            @Nullable CwTimingEvent timingEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot,
            @Nullable CwFrontEndLearningGate frontEndLearningGate,
            @Nullable LiveRxWpmGuard wpmGuard,
            @Nullable TimingAnchorController timingAnchorController
    ) {
        if (timingEvent == null) {
            return "null-event";
        }
        if (timingEvent.kind() != CwTimingEvent.Kind.GAP) {
            return "not-gap";
        }
        if (timingEvent.classification() != CwTimingEvent.Classification.INTRA_SYMBOL_GAP) {
            return "not-intra-gap";
        }
        if (signalSnapshot == null || timingSnapshot == null) {
            return "missing-snapshot";
        }
        if (timingSnapshot.dotEstimateMs() < BOOTSTRAP_CADENCE_MIN_DOT_MS) {
            return "dot-too-small";
        }
        long minCadenceGapMs = Math.max(
                BOOTSTRAP_CADENCE_MIN_GAP_MS,
                Math.round(timingSnapshot.dotEstimateMs() * BOOTSTRAP_CADENCE_MIN_GAP_TO_DOT_RATIO)
        );
        if (timingEvent.durationMs() < minCadenceGapMs) {
            return "gap-too-short";
        }
        if (timingEvent.durationMs() > BOOTSTRAP_CADENCE_MAX_GAP_MS) {
            return "gap-too-long";
        }
        if (timingEvent.durationMs() > Math.round(
                timingSnapshot.dotEstimateMs() * BOOTSTRAP_CADENCE_MAX_GAP_TO_DOT_RATIO
        )) {
            return "gap-too-large-vs-dot";
        }
        if (RxStableDecodeDecider.hasTrustedTiming(timingAnchorController)) {
            return "already-trusted";
        }
        boolean hasCadenceLockContext = signalSnapshot.recentLockedFrameRatio() >= 0.35d
                && (signalSnapshot.targetToneLocked()
                || signalSnapshot.recentNearTargetLockedFrameRatio() >= 0.80d);
        if (!hasCadenceLockContext
                || signalSnapshot.recentActiveUnlockedFrameRatio() > 0.20d
                || signalSnapshot.toneDominanceRatio() < 0.45d
                || signalSnapshot.narrowbandIsolationRatio() < 0.35d) {
            return "shape";
        }
        if (wpmGuard != null
                && !wpmGuard.shouldAcceptTimingAnchorUpdate(
                signalSnapshot,
                timingSnapshot,
                timingEvent.timestampMs()
        )) {
            return "wpm-guard";
        }
        if (timingAnchorController != null
                && !timingAnchorController.shouldAcceptStableAnchorUpdate(
                signalSnapshot,
                timingSnapshot,
                true,
                timingEvent.timestampMs()
        )) {
            return "anchor-guard";
        }
        return frontEndLearningGate == null
                || frontEndLearningGate.shouldAllowStableAnchorUpdate(
                signalSnapshot,
                inputHealthSnapshot,
                false
        ) ? "pass" : "front-end-learning";
    }

    public static boolean isBootstrapCadenceGap(
            @Nullable CwTimingEvent timingEvent,
            @Nullable CwTimingSnapshot timingSnapshot
    ) {
        if (timingEvent == null
                || timingSnapshot == null
                || timingSnapshot.dotEstimateMs() <= 0L) {
            return false;
        }
        if (timingEvent.classification() == CwTimingEvent.Classification.LETTER_GAP
                || timingEvent.classification() == CwTimingEvent.Classification.WORD_GAP) {
            return true;
        }
        if (timingEvent.classification() != CwTimingEvent.Classification.UNKNOWN) {
            return false;
        }
        long dotEstimateMs = timingSnapshot.dotEstimateMs();
        long durationMs = Math.max(0L, timingEvent.durationMs());
        long minimumBoundaryLikeGapMs = Math.max(1L, Math.round(dotEstimateMs * 2.2d));
        long maximumBoundaryLikeGapMs = Math.max(
                minimumBoundaryLikeGapMs,
                Math.round(dotEstimateMs * 12.5d)
        );
        return durationMs >= minimumBoundaryLikeGapMs && durationMs <= maximumBoundaryLikeGapMs;
    }

    public static long inferBootstrapBoundaryDotEstimateMs(
            @Nullable CwTimingEvent timingEvent,
            @Nullable CwTimingSnapshot timingSnapshot
    ) {
        if (timingEvent == null) {
            return 0L;
        }
        long durationMs = Math.max(0L, timingEvent.durationMs());
        if (durationMs <= 0L) {
            return 0L;
        }
        long candidateDotEstimateMs;
        if (timingEvent.classification() == CwTimingEvent.Classification.LETTER_GAP) {
            candidateDotEstimateMs = Math.max(1L, Math.round(durationMs / 3.0d));
        } else if (timingEvent.classification() == CwTimingEvent.Classification.WORD_GAP) {
            candidateDotEstimateMs = Math.max(1L, Math.round(durationMs / 7.0d));
        } else if (timingEvent.classification() != CwTimingEvent.Classification.UNKNOWN
                || timingSnapshot == null
                || timingSnapshot.dotEstimateMs() <= 0L) {
            return 0L;
        } else {
            double ratio = durationMs / (double) Math.max(1L, timingSnapshot.dotEstimateMs());
            if (ratio >= 5.0d) {
                candidateDotEstimateMs = Math.max(1L, Math.round(durationMs / 7.0d));
            } else {
                candidateDotEstimateMs = Math.max(1L, Math.round(durationMs / 3.0d));
            }
        }
        if (timingSnapshot == null || timingSnapshot.dotEstimateMs() <= 0L) {
            return candidateDotEstimateMs;
        }
        double candidateRatio = candidateDotEstimateMs / (double) Math.max(
                1L,
                timingSnapshot.dotEstimateMs()
        );
        if (candidateRatio < 0.68d || candidateRatio > 1.95d) {
            return 0L;
        }
        return candidateDotEstimateMs;
    }
}
