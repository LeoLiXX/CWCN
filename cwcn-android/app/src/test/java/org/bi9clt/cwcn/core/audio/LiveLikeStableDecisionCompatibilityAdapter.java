package org.bi9clt.cwcn.core.audio;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.rx.CwFrontEndLearningGate;
import org.bi9clt.cwcn.core.rx.RxStableDecodeClassifier;
import org.bi9clt.cwcn.core.rx.RxStableDecodeDecider;
import org.bi9clt.cwcn.core.rx.TimingAnchorController;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;

/**
 * Test-only compatibility overlay that preserves historical live-like stable
 * decode decision ordering while also surfacing the verified shared-base
 * decision.
 */
final class LiveLikeStableDecisionCompatibilityAdapter {
    private LiveLikeStableDecisionCompatibilityAdapter() {
    }

    interface StableAuthorityGate {
        boolean shouldAllowStableAnchorUpdate(
                CwSignalSnapshot signalSnapshot,
                long timestampMs
        );

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

    static DecisionOutcome diagnoseDecision(
            @Nullable CwDecodeEvent decodeEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot,
            @Nullable CwFrontEndLearningGate frontEndLearningGate,
            @Nullable StableAuthorityGate authorityGate,
            @Nullable TimingAnchorController timingAnchorController
    ) {
        String verifiedDecision = RxStableDecodeClassifier.diagnoseSimpleStableDecodeDecision(
                decodeEvent,
                signalSnapshot,
                timingSnapshot,
                inputHealthSnapshot,
                frontEndLearningGate,
                timingAnchorController
        );
        String compatibleDecision = diagnoseCompatibleDecision(
                decodeEvent,
                signalSnapshot,
                timingSnapshot,
                authorityGate,
                timingAnchorController,
                verifiedDecision
        );
        return new DecisionOutcome(compatibleDecision, verifiedDecision);
    }

    private static String diagnoseCompatibleDecision(
            @Nullable CwDecodeEvent decodeEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            @Nullable StableAuthorityGate authorityGate,
            @Nullable TimingAnchorController timingAnchorController,
            String verifiedDecision
    ) {
        if (decodeEvent == null
                || decodeEvent.type() != CwDecodeEvent.Type.CHARACTER_DECODED
                || signalSnapshot == null
                || timingSnapshot == null
                || timingSnapshot.estimatedWpm() <= 0
                || timingSnapshot.dotEstimateMs() <= 0L) {
            return verifiedDecision;
        }
        boolean trustedTimingEstablished = RxStableDecodeDecider.hasTrustedTiming(
                timingAnchorController
        );
        if (decodeEvent.unknownCharacter() && trustedTimingEstablished) {
            return verifiedDecision;
        }
        if (authorityGate != null
                && !(trustedTimingEstablished
                ? authorityGate.shouldAllowStableAnchorUpdate(
                        signalSnapshot,
                        decodeEvent.timestampMs()
                )
                : authorityGate.shouldAllowBootstrapStableAnchorUpdate(
                        signalSnapshot,
                        decodeEvent.timestampMs()
                ))) {
            return "front-end-authority";
        }
        return verifiedDecision;
    }
}
