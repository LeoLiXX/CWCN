package org.bi9clt.cwcn.core.audio;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.rx.CwFrontEndLearningGate;
import org.bi9clt.cwcn.core.rx.LiveRxWpmGuard;
import org.bi9clt.cwcn.core.rx.RxBootstrapTimingObserver;
import org.bi9clt.cwcn.core.rx.RxStableDecodeDecider;
import org.bi9clt.cwcn.core.rx.TimingAnchorController;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;

/**
 * Test-only compatibility overlay that preserves historical live-like bootstrap
 * decision ordering while also surfacing the verified shared-base decision.
 */
final class LiveLikeBootstrapDecisionCompatibilityAdapter {
    private static final long BOOTSTRAP_CADENCE_MIN_DOT_MS = 45L;
    private static final long BOOTSTRAP_CADENCE_MIN_GAP_MS = 24L;
    private static final long BOOTSTRAP_CADENCE_MAX_GAP_MS = 85L;
    private static final double BOOTSTRAP_CADENCE_MIN_GAP_TO_DOT_RATIO = 0.78d;
    private static final double BOOTSTRAP_CADENCE_MAX_GAP_TO_DOT_RATIO = 1.22d;

    private LiveLikeBootstrapDecisionCompatibilityAdapter() {
    }

    @FunctionalInterface
    interface BootstrapAuthorityGate {
        boolean shouldAllowBootstrapStableAnchorUpdate(
                CwSignalSnapshot signalSnapshot,
                long timestampMs
        );
    }

    static final class DecisionOutcome {
        private final String compatibleDecision;
        private final String verifiedDecision;

        private DecisionOutcome(String compatibleDecision, String verifiedDecision) {
            this.compatibleDecision = compatibleDecision;
            this.verifiedDecision = verifiedDecision;
        }

        String compatibleDecision() {
            return compatibleDecision;
        }

        String verifiedDecision() {
            return verifiedDecision;
        }
    }

    static DecisionOutcome diagnoseTimingBoundaryDecision(
            @Nullable CwTimingEvent timingEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            @Nullable CwFrontEndLearningGate frontEndLearningGate,
            @Nullable LiveRxWpmGuard wpmGuard,
            @Nullable BootstrapAuthorityGate authorityGate,
            @Nullable TimingAnchorController timingAnchorController
    ) {
        String compatibleDecision = diagnoseCompatibleTimingBoundaryDecision(
                timingEvent,
                signalSnapshot,
                timingSnapshot,
                frontEndLearningGate,
                wpmGuard,
                authorityGate,
                timingAnchorController
        );
        String verifiedDecision = RxBootstrapTimingObserver.diagnoseBootstrapTimingBoundaryDecision(
                timingEvent,
                signalSnapshot,
                timingSnapshot,
                null,
                frontEndLearningGate,
                wpmGuard,
                timingAnchorController
        );
        return new DecisionOutcome(compatibleDecision, verifiedDecision);
    }

    static DecisionOutcome diagnoseCadenceDecision(
            @Nullable CwTimingEvent timingEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            @Nullable CwFrontEndLearningGate frontEndLearningGate,
            @Nullable LiveRxWpmGuard wpmGuard,
            @Nullable BootstrapAuthorityGate authorityGate,
            @Nullable TimingAnchorController timingAnchorController
    ) {
        String compatibleDecision = diagnoseCompatibleCadenceDecision(
                timingEvent,
                signalSnapshot,
                timingSnapshot,
                frontEndLearningGate,
                wpmGuard,
                authorityGate,
                timingAnchorController
        );
        String verifiedDecision = RxBootstrapTimingObserver.diagnoseBootstrapCadenceDecision(
                timingEvent,
                signalSnapshot,
                timingSnapshot,
                null,
                frontEndLearningGate,
                wpmGuard,
                timingAnchorController
        );
        return new DecisionOutcome(compatibleDecision, verifiedDecision);
    }

    private static String diagnoseCompatibleTimingBoundaryDecision(
            @Nullable CwTimingEvent timingEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            @Nullable CwFrontEndLearningGate frontEndLearningGate,
            @Nullable LiveRxWpmGuard wpmGuard,
            @Nullable BootstrapAuthorityGate authorityGate,
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
        long candidateDotEstimateMs = RxBootstrapTimingObserver.inferBootstrapBoundaryDotEstimateMs(
                timingEvent,
                timingSnapshot
        );
        if (trustedDotEstimateMs > 0L && candidateDotEstimateMs <= trustedDotEstimateMs) {
            return "already-trusted";
        }
        boolean trustedSlowBoundaryCandidate = trustedDotEstimateMs > 0L
                && candidateDotEstimateMs > (trustedDotEstimateMs + 2L)
                && candidateDotEstimateMs <= Math.round(trustedDotEstimateMs * 1.25d);
        if (!RxBootstrapTimingObserver.isBootstrapCadenceGap(timingEvent, timingSnapshot)) {
            return "not-bootstrap-gap";
        }
        if (authorityGate != null
                && !authorityGate.shouldAllowBootstrapStableAnchorUpdate(
                signalSnapshot,
                timingEvent.timestampMs()
        )) {
            return "front-end-authority";
        }
        if (frontEndLearningGate != null
                && !frontEndLearningGate.shouldAllowBootstrapBoundaryAnchorUpdate(
                signalSnapshot,
                null
        )) {
            return "front-end-learning";
        }
        if (!trustedSlowBoundaryCandidate) {
            if (wpmGuard != null
                    && !wpmGuard.shouldAcceptBootstrapBoundaryAnchorUpdate(
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

    private static String diagnoseCompatibleCadenceDecision(
            @Nullable CwTimingEvent timingEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            @Nullable CwFrontEndLearningGate frontEndLearningGate,
            @Nullable LiveRxWpmGuard wpmGuard,
            @Nullable BootstrapAuthorityGate authorityGate,
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
        if (authorityGate != null
                && !authorityGate.shouldAllowBootstrapStableAnchorUpdate(
                signalSnapshot,
                timingEvent.timestampMs()
        )) {
            return "front-end-authority";
        }
        if (frontEndLearningGate != null
                && !frontEndLearningGate.shouldAllowStableAnchorUpdate(
                signalSnapshot,
                null,
                false
        )) {
            return "front-end-learning";
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
        return "pass";
    }
}
