package org.bi9clt.cwcn.core.rx;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.audio.AudioInputHealthSnapshot;
import org.bi9clt.cwcn.core.audio.AudioInputHealthTracker;
import org.bi9clt.cwcn.core.audio.AudioFrame;
import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;

import java.util.List;

/**
 * Small shared offline replay analysis entry point built on top of the shared RX runners.
 */
public final class RxReplayAnalysisRunner {
    public RxReplayAnalysisResult analyze(
            @Nullable List<AudioFrame> frames,
            int preferredToneFrequencyHz,
            int sqlLevel,
            int seedWpm
    ) {
        RxCoreComponents rxCoreComponents = new RxCoreComponents();
        int clampedSeedWpm = Math.max(0, seedWpm);
        rxCoreComponents.resetRuntimeState(clampedSeedWpm);

        CwSignalProcessor signalProcessor = rxCoreComponents.signalProcessor();
        if (preferredToneFrequencyHz > 0) {
            signalProcessor.setPreferredToneFrequencyHz(preferredToneFrequencyHz);
        }
        if (sqlLevel >= 0) {
            signalProcessor.setManualSqlThreshold(sqlLevel);
        }

        final int[] toneEventCount = {0};
        final int[] timingEventCount = {0};
        final int[] decodeEventCount = {0};
        final AudioInputHealthSnapshot[] latestInputHealthSnapshot = {null};
        AudioInputHealthTracker inputHealthTracker = new AudioInputHealthTracker();
        RxCommittedOutputController committedOutputController = new RxCommittedOutputController(
                rxCoreComponents.rawInterpreter(),
                new RxUnknownFallbackTracker(),
                null,
                null,
                rxCoreComponents.rawCommitGate()
        );
        RxTurnSessionFinalizer turnSessionFinalizer = new RxTurnSessionFinalizer(
                rxCoreComponents.turnTailRepairController(),
                committedOutputController
        );
        RxTurnSessionCoordinator turnSessionCoordinator = new RxTurnSessionCoordinator(
                signalProcessor,
                rxCoreComponents.timingModel(),
                rxCoreComponents.liveRxWpmGuard(),
                rxCoreComponents.turnController(),
                rxCoreComponents.timingAnchorController(),
                rxCoreComponents.rawCommitGate(),
                turnSessionFinalizer,
                null,
                null
        );
        RxReplayTurnSessionController replayTurnSessionController =
                new RxReplayTurnSessionController(
                        signalProcessor,
                        rxCoreComponents.timingModel(),
                        rxCoreComponents.decoder(),
                        rxCoreComponents.liveRxWpmGuard(),
                        rxCoreComponents.turnController(),
                        turnSessionCoordinator,
                        turnSessionFinalizer
                );
        RxCommittedDecodeController committedDecodeController = new RxCommittedDecodeController(
                signalProcessor,
                rxCoreComponents.timingModel(),
                rxCoreComponents.liveRxWpmGuard(),
                rxCoreComponents.turnController(),
                rxCoreComponents.timingAnchorController(),
                rxCoreComponents.rawCommitGate()
        );

        RxFrameSignalRunner frameSignalRunner = new RxFrameSignalRunner(inputHealthTracker, signalProcessor);
        RxReplaySessionRunner replaySessionRunner = new RxReplaySessionRunner(
                frameSignalRunner,
                rxCoreComponents.toneTimingRunner(),
                rxCoreComponents.timingDecodeRunner()
        );
        RxReplaySessionRunner.Result replayResult = replaySessionRunner.replayFrames(
                frames,
                toneEvent -> {
                    replayTurnSessionController.noteToneEvent(toneEvent);
                    return rxCoreComponents.timingModel().process(toneEvent);
                },
                (frame, frameResult) -> {
                    toneEventCount[0] += safeSize(frameResult.toneEvents());
                    latestInputHealthSnapshot[0] = frameResult.inputHealthSnapshot();
                    replayTurnSessionController.observeFrameEnd(frameResult.frameEndTimestampMs());
                },
                (toneEvent, timingEvents) -> timingEventCount[0] += safeSize(timingEvents),
                rxCoreComponents.timingModel()::flushPendingGap,
                (flushTimestampMs, timingEvents) -> timingEventCount[0] += safeSize(timingEvents),
                null,
                decodeEvent -> {
                    CwSignalSnapshot signalSnapshot = signalProcessor.snapshot();
                    CwTimingSnapshot timingSnapshot = rxCoreComponents.timingModel().rawSnapshot();
                    boolean stableDecodeAccepted = RxStableDecodeClassifier.passesSimpleStableDecode(
                            decodeEvent,
                            signalSnapshot,
                            timingSnapshot,
                            latestInputHealthSnapshot[0],
                            rxCoreComponents.frontEndLearningGate(),
                            rxCoreComponents.timingAnchorController()
                    );
                    consumeCommittedDecodeEvents(
                            committedDecodeController.admit(decodeEvent, stableDecodeAccepted),
                            replayTurnSessionController,
                            decodeEventCount
                    );
                }
        );
        replayTurnSessionController.finalizeAtStop(replayResult.flushTimestampMs());
        int appliedPreferredToneFrequencyHz = signalProcessor.snapshot().preferredToneFrequencyHz();
        int appliedSqlLevel = sqlLevel < 0 ? -1 : sqlLevel;

        return new RxReplayAnalysisResult(
                appliedPreferredToneFrequencyHz,
                appliedSqlLevel,
                clampedSeedWpm,
                replayResult.processedFrameCount(),
                replayResult.flushTimestampMs(),
                toneEventCount[0],
                timingEventCount[0],
                decodeEventCount[0],
                replayTurnSessionController.turnCount(),
                replayTurnSessionController.tailRepairCount(),
                replayTurnSessionController.transitionTracesSnapshot(),
                replayTurnSessionController.turnWindowsSnapshot(replayResult.flushTimestampMs()),
                signalProcessor.snapshot(),
                rxCoreComponents.timingModel().snapshot(),
                rxCoreComponents.decoder().snapshot(),
                committedOutputController.rawSnapshot()
        );
    }

    private void consumeCommittedDecodeEvents(
            @Nullable List<CwDecodeEvent> decodeEvents,
            RxReplayTurnSessionController replayTurnSessionController,
            int[] decodeEventCount
    ) {
        if (decodeEvents == null
                || decodeEvents.isEmpty()
                || replayTurnSessionController == null
                || decodeEventCount == null) {
            return;
        }
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            if (decodeEvent == null) {
                continue;
            }
            decodeEventCount[0] += 1;
        }
        replayTurnSessionController.processCommittedDecodeEvents(decodeEvents);
    }

    private int safeSize(@Nullable List<?> items) {
        return items == null ? 0 : items.size();
    }
}
