package org.bi9clt.cwcn.core.rx;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;

/**
 * Resolves when hybrid RX tone mode should fall back from fixed-tone bootstrap
 * to auto-track before trusted timing exists.
 */
public final class RxToneModeBootstrapDecider {
    private static final long PRE_TRUST_AUTOTRACK_FALLBACK_MS = 48L;
    private static final double FIXED_BOOTSTRAP_PROGRESS_LOCKED_RATIO_MIN = 0.30d;
    private static final int FIXED_BOOTSTRAP_PROGRESS_CONSECUTIVE_LOCKED_FRAMES_MIN = 4;
    private static final int POST_TRUST_PENDING_RETUNE_DRIFT_MIN_HZ = 25;
    private static final int POST_TRUST_FIXED_HOLD_MIN_OFFSET_HZ = 60;
    private static final long POST_TRUST_FIXED_GRACE_MS = 320L;
    private static final double POST_TRUST_FIXED_GRACE_LOCKED_RATIO_MIN = 0.45d;
    private static final int POST_TRUST_FIXED_GRACE_CONSECUTIVE_LOCKED_FRAMES_MIN = 2;
    private static final double POST_TRUST_FIXED_GRACE_NEAR_TARGET_LOCKED_RATIO_MIN = 0.68d;

    private RxToneModeBootstrapDecider() {
    }

    public static CwSignalProcessor.RxToneMode resolveHybridBootstrapMode(
            boolean trustedTimingEstablished,
            @Nullable RxTurnController turnController,
            @Nullable CwSignalSnapshot signalSnapshot,
            long nowTimestampMs
    ) {
        if (turnController != null && showsUsefulFixedToneBootstrapProgress(signalSnapshot)) {
            turnController.noteBootstrapFixedProgressObserved();
        }
        if (turnController != null && turnController.bootstrapAutoTrackFallbackLatched()) {
            return CwSignalProcessor.RxToneMode.AUTO_TRACK;
        }
        if (trustedTimingEstablished) {
            if (turnController != null) {
                turnController.noteTrustedTimingEstablished(nowTimestampMs);
            }
            return shouldKeepFixedToneAfterTrust(turnController, signalSnapshot, nowTimestampMs)
                    ? CwSignalProcessor.RxToneMode.FIXED_TONE
                    : CwSignalProcessor.RxToneMode.AUTO_TRACK;
        }
        if (shouldUseAutoTrackBeforeTrust(turnController, signalSnapshot, nowTimestampMs)) {
            if (turnController != null) {
                turnController.latchBootstrapAutoTrackFallback();
            }
            return CwSignalProcessor.RxToneMode.AUTO_TRACK;
        }
        return CwSignalProcessor.RxToneMode.FIXED_TONE;
    }

    public static boolean shouldUseAutoTrackBeforeTrust(
            @Nullable RxTurnController turnController,
            @Nullable CwSignalSnapshot signalSnapshot,
            long nowTimestampMs
    ) {
        if (turnController == null
                || turnController.phase() != RxTurnController.Phase.ACTIVE
                || nowTimestampMs <= 0L) {
            return false;
        }
        long turnStartedAtMs = turnController.currentTurnStartedAtMs();
        if (turnStartedAtMs <= 0L || nowTimestampMs < turnStartedAtMs) {
            return false;
        }
        if ((nowTimestampMs - turnStartedAtMs) < PRE_TRUST_AUTOTRACK_FALLBACK_MS) {
            return false;
        }
        if (turnController.bootstrapFixedProgressObservedThisTurn()) {
            return false;
        }
        return !showsUsefulFixedToneBootstrapProgress(signalSnapshot);
    }

    private static boolean showsUsefulFixedToneBootstrapProgress(@Nullable CwSignalSnapshot signalSnapshot) {
        if (signalSnapshot == null) {
            return false;
        }
        return signalSnapshot.targetToneLocked()
                || signalSnapshot.consecutiveLockedFrames()
                >= FIXED_BOOTSTRAP_PROGRESS_CONSECUTIVE_LOCKED_FRAMES_MIN
                || signalSnapshot.recentLockedFrameRatio() >= FIXED_BOOTSTRAP_PROGRESS_LOCKED_RATIO_MIN;
    }

    private static boolean shouldKeepFixedToneAfterTrust(
            @Nullable RxTurnController turnController,
            @Nullable CwSignalSnapshot signalSnapshot,
            long nowTimestampMs
    ) {
        if (!showsUsefulFixedToneBootstrapProgress(signalSnapshot)
                && (turnController == null
                || !turnController.bootstrapFixedProgressObservedThisTurn())) {
            return false;
        }
        if (showsExplicitRetunePressure(signalSnapshot)) {
            return false;
        }
        if (turnController != null && turnController.bootstrapFixedProgressObservedThisTurn()) {
            return true;
        }
        return showsMaterialOffPreferredTracking(signalSnapshot)
                || shouldApplyShortPostTrustFixedGrace(turnController, signalSnapshot, nowTimestampMs);
    }

    private static boolean shouldApplyShortPostTrustFixedGrace(
            @Nullable RxTurnController turnController,
            @Nullable CwSignalSnapshot signalSnapshot,
            long nowTimestampMs
    ) {
        if (turnController == null
                || signalSnapshot == null
                || !turnController.bootstrapFixedProgressObservedThisTurn()) {
            return false;
        }
        long trustedTimingEstablishedAtMs = turnController.trustedTimingEstablishedAtMs();
        if (trustedTimingEstablishedAtMs <= 0L
                || nowTimestampMs < trustedTimingEstablishedAtMs
                || (nowTimestampMs - trustedTimingEstablishedAtMs) > POST_TRUST_FIXED_GRACE_MS) {
            return false;
        }
        if (!signalSnapshot.targetToneLocked()
                && signalSnapshot.consecutiveLockedFrames()
                < POST_TRUST_FIXED_GRACE_CONSECUTIVE_LOCKED_FRAMES_MIN) {
            return false;
        }
        if (signalSnapshot.recentLockedFrameRatio() < POST_TRUST_FIXED_GRACE_LOCKED_RATIO_MIN) {
            return false;
        }
        return signalSnapshot.recentNearTargetLockedFrameRatio()
                >= POST_TRUST_FIXED_GRACE_NEAR_TARGET_LOCKED_RATIO_MIN;
    }

    private static boolean showsExplicitRetunePressure(@Nullable CwSignalSnapshot signalSnapshot) {
        if (signalSnapshot == null) {
            return false;
        }
        int pendingCandidateHz = signalSnapshot.pendingRetuneCandidateFrequencyHz();
        int targetToneHz = signalSnapshot.targetToneFrequencyHz();
        boolean materiallyDifferentPendingCandidate = pendingCandidateHz > 0
                && targetToneHz > 0
                && Math.abs(pendingCandidateHz - targetToneHz) >= POST_TRUST_PENDING_RETUNE_DRIFT_MIN_HZ;
        return (signalSnapshot.pendingRetuneCandidateStableScans() > 0
                && materiallyDifferentPendingCandidate)
                || signalSnapshot.lockedRetuneGuardHolding()
                || signalSnapshot.lockedRetuneGuardObservedScans() > 0;
    }

    private static boolean showsMaterialOffPreferredTracking(@Nullable CwSignalSnapshot signalSnapshot) {
        if (signalSnapshot == null) {
            return false;
        }
        int preferredToneHz = signalSnapshot.preferredToneFrequencyHz();
        if (preferredToneHz <= 0) {
            return false;
        }
        return Math.abs(signalSnapshot.effectiveTrackedToneFrequencyHz() - preferredToneHz)
                >= POST_TRUST_FIXED_HOLD_MIN_OFFSET_HZ
                || Math.abs(signalSnapshot.toneHypothesisFrequencyHz() - preferredToneHz)
                >= POST_TRUST_FIXED_HOLD_MIN_OFFSET_HZ;
    }
}
