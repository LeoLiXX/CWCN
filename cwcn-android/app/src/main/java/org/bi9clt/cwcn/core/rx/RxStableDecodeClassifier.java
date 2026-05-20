package org.bi9clt.cwcn.core.rx;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.audio.AudioInputHealthSnapshot;
import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;

/**
 * Shared stable-decode admission helper for raw-commit discipline.
 */
public final class RxStableDecodeClassifier {
    private RxStableDecodeClassifier() {
    }

    public static boolean passesSimpleStableDecode(
            @Nullable CwDecodeEvent decodeEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot,
            @Nullable CwFrontEndLearningGate frontEndLearningGate,
            @Nullable TimingAnchorController timingAnchorController
    ) {
        return "pass".equals(diagnoseSimpleStableDecodeDecision(
                decodeEvent,
                signalSnapshot,
                timingSnapshot,
                inputHealthSnapshot,
                frontEndLearningGate,
                timingAnchorController
        ));
    }

    public static String diagnoseSimpleStableDecodeDecision(
            @Nullable CwDecodeEvent decodeEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot,
            @Nullable CwFrontEndLearningGate frontEndLearningGate,
            @Nullable TimingAnchorController timingAnchorController
    ) {
        if (decodeEvent == null
                || decodeEvent.type() != CwDecodeEvent.Type.CHARACTER_DECODED
                || signalSnapshot == null
                || timingSnapshot == null
                || timingSnapshot.estimatedWpm() <= 0
                || timingSnapshot.dotEstimateMs() <= 0L) {
            if (decodeEvent == null) {
                return "null-decode";
            }
            if (decodeEvent.type() != CwDecodeEvent.Type.CHARACTER_DECODED) {
                return "non-character";
            }
            if (signalSnapshot == null || timingSnapshot == null) {
                return "missing-snapshot";
            }
            if (timingSnapshot.estimatedWpm() <= 0) {
                return "no-wpm";
            }
            return "no-dot";
        }
        boolean trustedTimingEstablished = RxStableDecodeDecider.hasTrustedTiming(
                timingAnchorController
        );
        if (decodeEvent.unknownCharacter() && trustedTimingEstablished) {
            return "unknown-after-trust";
        }
        if (!trustedTimingEstablished) {
            if (frontEndLearningGate != null
                    && !frontEndLearningGate.shouldAllowTimingLearning(
                    signalSnapshot,
                    inputHealthSnapshot
            )) {
                return "front-end-learning";
            }
            return "pass";
        }
        if (timingAnchorController != null
                && !timingAnchorController.shouldAcceptStableAnchorUpdate(
                signalSnapshot,
                timingSnapshot,
                true,
                decodeEvent.timestampMs()
        )) {
            return "anchor-guard";
        }
        if (frontEndLearningGate != null
                && !frontEndLearningGate.shouldAllowStableAnchorUpdate(
                signalSnapshot,
                inputHealthSnapshot,
                true
        )) {
            return "front-end-learning";
        }
        return RxStableDecodeDecider.passesStableDecodeShape(
                signalSnapshot,
                trustedTimingEstablished
        ) ? "pass" : "shape";
    }
}
