package org.bi9clt.cwcn.core.rx;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.audio.AudioInputHealthSnapshot;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;

public final class CwFrontEndLearningGate {
    private static final double LEARN_BLOCK_LOCKED_RATIO_MAX = 0.18d;
    private static final double LEARN_BLOCK_NEAR_TARGET_RATIO_MAX = 0.45d;
    private static final double LEARN_BLOCK_ACTIVE_UNLOCKED_RATIO_MIN = 0.38d;
    private static final double LEARN_BLOCK_TONE_DOMINANCE_MAX = 0.22d;
    private static final double LEARN_BLOCK_ISOLATION_MAX = 0.24d;
    private static final double HOT_FRAME_RATIO_MAX = 0.45d;
    private static final double HOT_FRAME_EVENT_OVERRIDE_MAX = 0.80d;
    private static final double CLIPPING_FRAME_RATIO_MAX = 0.08d;
    private static final double PRE_TRUST_EVENT_LOCKED_RATIO_MIN = 0.75d;
    private static final double HOT_EVENT_LOCKED_RATIO_MIN = 0.54d;
    private static final double HOT_EVENT_NEAR_TARGET_RATIO_MIN = 0.85d;
    private static final double HOT_EVENT_ACTIVE_UNLOCKED_RATIO_MAX = 0.08d;
    private static final double HOT_EVENT_TONE_DOMINANCE_MIN = 0.75d;
    private static final double HOT_EVENT_ISOLATION_MIN = 0.65d;

    public boolean shouldAllowTimingLearning(
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot
    ) {
        if (!signalHealthyForGate(signalSnapshot, inputHealthSnapshot)) {
            return false;
        }
        return !shouldBlockTimingLearning(signalSnapshot);
    }

    public boolean shouldAllowTimingLearningForEvent(
            @Nullable CwToneEvent toneEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot,
            boolean trustedTimingEstablished
    ) {
        if (!trustedTimingEstablished
                && shouldAllowPreTrustLockedEventLearning(
                toneEvent,
                signalSnapshot,
                inputHealthSnapshot
        )) {
            return true;
        }
        if (signalHealthyForGate(signalSnapshot, inputHealthSnapshot)) {
            return !shouldBlockTimingLearning(signalSnapshot);
        }
        if (!trustedTimingEstablished
                || toneEvent == null
                || signalSnapshot == null
                || inputHealthSnapshot == null
                || inputHealthSnapshot.recentClippingFrameRatio() > CLIPPING_FRAME_RATIO_MAX) {
            return false;
        }
        double hotRatio = inputHealthSnapshot.recentHotFrameRatio();
        if (hotRatio <= HOT_FRAME_RATIO_MAX || hotRatio > HOT_FRAME_EVENT_OVERRIDE_MAX) {
            return false;
        }
        return shouldAllowTrustedHotInputEventLearning(toneEvent, signalSnapshot);
    }

    public boolean shouldAllowStableAnchorUpdate(
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot
    ) {
        return shouldAllowStableAnchorUpdate(signalSnapshot, inputHealthSnapshot, true);
    }

    public boolean shouldAllowStableAnchorUpdate(
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot,
            boolean trustedTimingEstablished
    ) {
        if (signalHealthyForGate(signalSnapshot, inputHealthSnapshot)) {
            return RxStableDecodeDecider.passesStableDecodeShape(
                    signalSnapshot,
                    trustedTimingEstablished
            );
        }
        if (!trustedTimingEstablished
                || signalSnapshot == null
                || inputHealthSnapshot == null
                || inputHealthSnapshot.recentClippingFrameRatio() > CLIPPING_FRAME_RATIO_MAX) {
            return false;
        }
        double hotRatio = inputHealthSnapshot.recentHotFrameRatio();
        if (hotRatio <= HOT_FRAME_RATIO_MAX || hotRatio > HOT_FRAME_EVENT_OVERRIDE_MAX) {
            return false;
        }
        return RxStableDecodeDecider.passesStableDecodeShape(signalSnapshot, true);
    }

    public boolean shouldAllowBootstrapBoundaryAnchorUpdate(
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot
    ) {
        if (!signalHealthyForGate(signalSnapshot, inputHealthSnapshot)) {
            return false;
        }
        return signalSnapshot != null && RxStableDecodeDecider.passesBootstrapBoundaryShape(
                signalSnapshot.targetToneLocked(),
                signalSnapshot.recentLockedFrameRatio(),
                signalSnapshot.recentNearTargetLockedFrameRatio(),
                signalSnapshot.recentActiveUnlockedFrameRatio(),
                signalSnapshot.toneDominanceRatio(),
                signalSnapshot.narrowbandIsolationRatio()
        );
    }

    private boolean shouldBlockTimingLearning(CwSignalSnapshot signalSnapshot) {
        if (signalSnapshot == null) {
            return true;
        }
        boolean weakLock = !signalSnapshot.targetToneLocked()
                && signalSnapshot.recentLockedFrameRatio() <= LEARN_BLOCK_LOCKED_RATIO_MAX
                && signalSnapshot.recentNearTargetLockedFrameRatio() <= LEARN_BLOCK_NEAR_TARGET_RATIO_MAX;
        boolean poorShape = signalSnapshot.recentActiveUnlockedFrameRatio() >= LEARN_BLOCK_ACTIVE_UNLOCKED_RATIO_MIN
                && signalSnapshot.toneDominanceRatio() <= LEARN_BLOCK_TONE_DOMINANCE_MAX
                && signalSnapshot.narrowbandIsolationRatio() <= LEARN_BLOCK_ISOLATION_MAX;
        return weakLock || poorShape;
    }

    private boolean shouldAllowTrustedHotInputEventLearning(
            CwToneEvent toneEvent,
            CwSignalSnapshot signalSnapshot
    ) {
        boolean hasTrustedHotLockContext = signalSnapshot.recentLockedFrameRatio() >= HOT_EVENT_LOCKED_RATIO_MIN
                && signalSnapshot.recentActiveUnlockedFrameRatio() <= HOT_EVENT_ACTIVE_UNLOCKED_RATIO_MAX
                && (signalSnapshot.targetToneLocked()
                || signalSnapshot.recentNearTargetLockedFrameRatio() >= HOT_EVENT_NEAR_TARGET_RATIO_MIN);
        if (!hasTrustedHotLockContext) {
            return false;
        }
        if (toneEvent.type() == CwToneEvent.Type.TONE_OFF) {
            return true;
        }
        return signalSnapshot.targetToneLocked()
                && signalSnapshot.toneDominanceRatio() >= HOT_EVENT_TONE_DOMINANCE_MIN
                && signalSnapshot.narrowbandIsolationRatio() >= HOT_EVENT_ISOLATION_MIN;
    }

    private boolean shouldAllowPreTrustLockedEventLearning(
            @Nullable CwToneEvent toneEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot
    ) {
        if (toneEvent == null || signalSnapshot == null || !signalSnapshot.targetToneLocked()) {
            return false;
        }
        if (inputHealthSnapshot != null) {
            if (inputHealthSnapshot.recentClippingFrameRatio() > CLIPPING_FRAME_RATIO_MAX
                    || inputHealthSnapshot.recentHotFrameRatio() > HOT_FRAME_EVENT_OVERRIDE_MAX) {
                return false;
            }
        }
        if (signalSnapshot.recentLockedFrameRatio() < PRE_TRUST_EVENT_LOCKED_RATIO_MIN
                || signalSnapshot.recentActiveUnlockedFrameRatio() > HOT_EVENT_ACTIVE_UNLOCKED_RATIO_MAX) {
            return false;
        }
        if (toneEvent.type() == CwToneEvent.Type.TONE_OFF) {
            return true;
        }
        return signalSnapshot.toneDominanceRatio() >= HOT_EVENT_TONE_DOMINANCE_MIN
                && signalSnapshot.narrowbandIsolationRatio() >= HOT_EVENT_ISOLATION_MIN;
    }

    private boolean signalHealthyForGate(
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot
    ) {
        if (signalSnapshot == null) {
            return false;
        }
        if (inputHealthSnapshot == null) {
            return true;
        }
        return inputHealthSnapshot.recentHotFrameRatio() <= HOT_FRAME_RATIO_MAX
                && inputHealthSnapshot.recentClippingFrameRatio() <= CLIPPING_FRAME_RATIO_MAX;
    }
}
