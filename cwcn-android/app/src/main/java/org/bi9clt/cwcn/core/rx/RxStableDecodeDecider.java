package org.bi9clt.cwcn.core.rx;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;

/**
 * Shared stable-decode admission for turn bootstrap and steady-state phases.
 *
 * <p>Before a turn has established trusted timing, RX should accept a slightly
 * weaker but still well-formed decode window so the turn can bootstrap
 * quickly. Once trusted timing exists, the stricter steady-state gate applies.</p>
 */
public final class RxStableDecodeDecider {
    private static final double STEADY_LOCKED_RATIO_MIN = 0.60d;
    private static final double STEADY_NEAR_TARGET_RATIO_MIN = 0.64d;
    private static final double STEADY_ACTIVE_UNLOCKED_RATIO_MAX = 0.24d;
    private static final double STEADY_TONE_DOMINANCE_MIN = 0.44d;
    private static final double STEADY_ISOLATION_MIN = 0.54d;
    private static final double STEADY_STRONG_CURRENT_LOCKED_RATIO_MIN = 0.44d;
    private static final double STEADY_STRONG_CURRENT_ACTIVE_UNLOCKED_RATIO_MAX = 0.08d;
    private static final double STEADY_STRONG_CURRENT_TONE_DOMINANCE_MIN = 0.78d;
    private static final double STEADY_STRONG_CURRENT_ISOLATION_MIN = 0.55d;
    private static final double STEADY_STRONG_NEAR_TARGET_LOCKED_RATIO_MIN = 0.45d;
    private static final double STEADY_STRONG_NEAR_TARGET_RATIO_MIN = 0.90d;
    private static final double STEADY_STRONG_NEAR_TARGET_TONE_DOMINANCE_MIN = 0.80d;
    private static final double STEADY_STRONG_NEAR_TARGET_ISOLATION_MIN = 0.60d;
    private static final double STEADY_GAP_EDGE_LOCKED_RATIO_MIN = 0.32d;
    private static final double STEADY_GAP_EDGE_ACTIVE_UNLOCKED_RATIO_MAX = 0.02d;
    private static final double STEADY_GAP_EDGE_TONE_DOMINANCE_MAX = 0.10d;
    private static final double STEADY_GAP_EDGE_ISOLATION_MAX = 0.10d;

    private static final double BOOTSTRAP_ACQUIRE_LOCKED_RATIO_MIN = 0.18d;
    private static final double BOOTSTRAP_ACQUIRE_NEAR_TARGET_RATIO_MIN = 0.45d;
    private static final double BOOTSTRAP_ACQUIRE_ACTIVE_UNLOCKED_RATIO_MAX = 0.42d;
    private static final double BOOTSTRAP_ACQUIRE_TONE_DOMINANCE_MIN = 0.18d;
    private static final double BOOTSTRAP_ACQUIRE_ISOLATION_MIN = 0.12d;

    private RxStableDecodeDecider() {
    }

    public static boolean hasTrustedTiming(@Nullable TimingAnchorController timingAnchorController) {
        return timingAnchorController != null && timingAnchorController.trustedDotEstimateMs() > 0L;
    }

    public static boolean passesStableDecodeShape(
            @Nullable CwSignalSnapshot signalSnapshot,
            boolean trustedTimingEstablished
    ) {
        if (signalSnapshot == null) {
            return false;
        }
        return trustedTimingEstablished
                ? passesSteadyDecodeShape(
                signalSnapshot.targetToneLocked(),
                signalSnapshot.recentLockedFrameRatio(),
                signalSnapshot.recentNearTargetLockedFrameRatio(),
                signalSnapshot.recentActiveUnlockedFrameRatio(),
                signalSnapshot.toneDominanceRatio(),
                signalSnapshot.narrowbandIsolationRatio()
        )
                : passesBootstrapDecodeShape(
                signalSnapshot.targetToneLocked(),
                signalSnapshot.recentLockedFrameRatio(),
                signalSnapshot.recentNearTargetLockedFrameRatio(),
                signalSnapshot.recentActiveUnlockedFrameRatio(),
                signalSnapshot.toneDominanceRatio(),
                signalSnapshot.narrowbandIsolationRatio()
        );
    }

    public static boolean passesSteadyDecodeShape(
            boolean targetToneLocked,
            double recentLockedFrameRatio,
            double recentNearTargetLockedFrameRatio,
            double recentActiveUnlockedFrameRatio,
            double toneDominanceRatio,
            double narrowbandIsolationRatio
    ) {
        if (passesSteadyGapEdgeCarryShape(
                targetToneLocked,
                recentLockedFrameRatio,
                recentActiveUnlockedFrameRatio,
                toneDominanceRatio,
                narrowbandIsolationRatio
        )) {
            return true;
        }
        if (!targetToneLocked
                || recentActiveUnlockedFrameRatio > STEADY_ACTIVE_UNLOCKED_RATIO_MAX) {
            return false;
        }
        if (recentLockedFrameRatio >= STEADY_STRONG_CURRENT_LOCKED_RATIO_MIN
                && recentActiveUnlockedFrameRatio <= STEADY_STRONG_CURRENT_ACTIVE_UNLOCKED_RATIO_MAX
                && toneDominanceRatio >= STEADY_STRONG_CURRENT_TONE_DOMINANCE_MIN
                && narrowbandIsolationRatio >= STEADY_STRONG_CURRENT_ISOLATION_MIN) {
            return true;
        }
        boolean passesPrimaryShape = recentLockedFrameRatio >= STEADY_LOCKED_RATIO_MIN
                && recentNearTargetLockedFrameRatio >= STEADY_NEAR_TARGET_RATIO_MIN
                && toneDominanceRatio >= STEADY_TONE_DOMINANCE_MIN
                && narrowbandIsolationRatio >= STEADY_ISOLATION_MIN;
        if (passesPrimaryShape) {
            return true;
        }
        return recentLockedFrameRatio >= STEADY_STRONG_NEAR_TARGET_LOCKED_RATIO_MIN
                && recentNearTargetLockedFrameRatio >= STEADY_STRONG_NEAR_TARGET_RATIO_MIN
                && toneDominanceRatio >= STEADY_STRONG_NEAR_TARGET_TONE_DOMINANCE_MIN
                && narrowbandIsolationRatio >= STEADY_STRONG_NEAR_TARGET_ISOLATION_MIN;
    }

    private static boolean passesSteadyGapEdgeCarryShape(
            boolean targetToneLocked,
            double recentLockedFrameRatio,
            double recentActiveUnlockedFrameRatio,
            double toneDominanceRatio,
            double narrowbandIsolationRatio
    ) {
        return !targetToneLocked
                && recentLockedFrameRatio >= STEADY_GAP_EDGE_LOCKED_RATIO_MIN
                && recentActiveUnlockedFrameRatio <= STEADY_GAP_EDGE_ACTIVE_UNLOCKED_RATIO_MAX
                && toneDominanceRatio <= STEADY_GAP_EDGE_TONE_DOMINANCE_MAX
                && narrowbandIsolationRatio <= STEADY_GAP_EDGE_ISOLATION_MAX;
    }

    public static boolean passesBootstrapDecodeShape(
            boolean targetToneLocked,
            double recentLockedFrameRatio,
            double recentNearTargetLockedFrameRatio,
            double recentActiveUnlockedFrameRatio,
            double toneDominanceRatio,
            double narrowbandIsolationRatio
    ) {
        return passesBootstrapAcquireShape(
                targetToneLocked,
                recentLockedFrameRatio,
                recentNearTargetLockedFrameRatio,
                recentActiveUnlockedFrameRatio,
                toneDominanceRatio,
                narrowbandIsolationRatio
        );
    }

    public static boolean passesBootstrapBoundaryShape(
            boolean targetToneLocked,
            double recentLockedFrameRatio,
            double recentNearTargetLockedFrameRatio,
            double recentActiveUnlockedFrameRatio,
            double toneDominanceRatio,
            double narrowbandIsolationRatio
    ) {
        return passesBootstrapAcquireShape(
                targetToneLocked,
                recentLockedFrameRatio,
                recentNearTargetLockedFrameRatio,
                recentActiveUnlockedFrameRatio,
                toneDominanceRatio,
                narrowbandIsolationRatio
        );
    }

    private static boolean passesBootstrapAcquireShape(
            boolean targetToneLocked,
            double recentLockedFrameRatio,
            double recentNearTargetLockedFrameRatio,
            double recentActiveUnlockedFrameRatio,
            double toneDominanceRatio,
            double narrowbandIsolationRatio
    ) {
        boolean hasLockContext = targetToneLocked
                || recentLockedFrameRatio >= BOOTSTRAP_ACQUIRE_LOCKED_RATIO_MIN
                || recentNearTargetLockedFrameRatio >= BOOTSTRAP_ACQUIRE_NEAR_TARGET_RATIO_MIN;
        if (!hasLockContext || recentActiveUnlockedFrameRatio > BOOTSTRAP_ACQUIRE_ACTIVE_UNLOCKED_RATIO_MAX) {
            return false;
        }
        return toneDominanceRatio >= BOOTSTRAP_ACQUIRE_TONE_DOMINANCE_MIN
                || narrowbandIsolationRatio >= BOOTSTRAP_ACQUIRE_ISOLATION_MIN;
    }
}
