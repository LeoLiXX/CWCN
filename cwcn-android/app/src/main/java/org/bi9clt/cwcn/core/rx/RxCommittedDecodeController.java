package org.bi9clt.cwcn.core.rx;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;

import java.util.Collections;
import java.util.List;

/**
 * Shared committed-raw admission controller that sits after decode event creation.
 */
public final class RxCommittedDecodeController {
    @Nullable private final CwSignalProcessor signalProcessor;
    @Nullable private final CwHybridTimingModel timingModel;
    @Nullable private final LiveRxWpmGuard wpmGuard;
    @Nullable private final RxTurnController turnController;
    @Nullable private final TimingAnchorController timingAnchorController;
    @Nullable private final RxRawCommitGate rawCommitGate;

    public RxCommittedDecodeController(
            @Nullable CwSignalProcessor signalProcessor,
            @Nullable CwHybridTimingModel timingModel,
            @Nullable LiveRxWpmGuard wpmGuard,
            @Nullable RxTurnController turnController,
            @Nullable TimingAnchorController timingAnchorController,
            @Nullable RxRawCommitGate rawCommitGate
    ) {
        this.signalProcessor = signalProcessor;
        this.timingModel = timingModel;
        this.wpmGuard = wpmGuard;
        this.turnController = turnController;
        this.timingAnchorController = timingAnchorController;
        this.rawCommitGate = rawCommitGate;
    }

    public List<CwDecodeEvent> admit(
            @Nullable CwDecodeEvent decodeEvent,
            boolean stableDecodeAccepted
    ) {
        if (decodeEvent == null) {
            return Collections.emptyList();
        }
        if (turnController != null && turnController.phase() != RxTurnController.Phase.ACTIVE) {
            return Collections.emptyList();
        }
        if (stableDecodeAccepted && timingModel != null) {
            timingModel.notifyStableDecode(decodeEvent.timestampMs());
            CwTimingSnapshot timingSnapshot = timingModel.rawSnapshot();
            CwSignalSnapshot signalSnapshot = signalProcessor == null
                    ? null
                    : signalProcessor.snapshot();
            if (timingAnchorController != null && signalSnapshot != null) {
                timingAnchorController.noteStableDecode(
                        signalSnapshot,
                        timingSnapshot,
                        decodeEvent.timestampMs()
                );
            }
            if (turnController != null && timingSnapshot != null) {
                int anchorWpm = timingSnapshot.estimatedWpm() > 0
                        ? timingSnapshot.estimatedWpm()
                        : (int) Math.round(Math.max(0.0d, timingSnapshot.estimatedWpmPrecise()));
                boolean carryEligible = timingModel.debugSnapshot().trustedDotEstimateMs() > 0.0d;
                turnController.noteStableDecode(
                        decodeEvent.timestampMs(),
                        anchorWpm,
                        carryEligible
                );
            }
        }

        boolean trustedTimingEstablished = RxStableDecodeDecider.hasTrustedTiming(
                timingAnchorController
        );
        List<CwDecodeEvent> admittedEvents = rawCommitGate == null
                ? Collections.singletonList(decodeEvent)
                : rawCommitGate.admit(
                        decodeEvent,
                        trustedTimingEstablished,
                        timingAnchorController == null
                                ? TimingAnchorController.TrustOrigin.NONE
                                : timingAnchorController.trustOrigin(),
                        timingAnchorController == null
                                ? 0L
                                : timingAnchorController.trustedDotEstimateMs(),
                        timingAnchorController == null
                                ? -1L
                                : timingAnchorController.lastTrustedUpdateTimestampMs()
                );
        if (admittedEvents == null || admittedEvents.isEmpty()) {
            return Collections.emptyList();
        }
        if (wpmGuard == null) {
            return admittedEvents;
        }
        for (CwDecodeEvent admittedEvent : admittedEvents) {
            if (admittedEvent == null || admittedEvent.type() != CwDecodeEvent.Type.CHARACTER_DECODED) {
                continue;
            }
            wpmGuard.noteDecodedCharacter(
                    admittedEvent.unknownCharacter(),
                    signalProcessor == null ? null : signalProcessor.snapshot(),
                    timingModel == null ? null : timingModel.rawSnapshot(),
                    admittedEvent.timestampMs()
            );
        }
        return admittedEvents;
    }
}
