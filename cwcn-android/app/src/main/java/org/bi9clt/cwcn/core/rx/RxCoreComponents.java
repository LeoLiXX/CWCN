package org.bi9clt.cwcn.core.rx;

import org.bi9clt.cwcn.core.decoder.CwDecoder;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel;

/**
 * Shared assembly for the core RX runtime used by both Operate and replay tools.
 *
 * <p>This class only owns the common RX processing components and their shared
 * lifecycle. Callers still remain responsible for app-specific configuration
 * such as SQL, preferred tone, trace recording, semantic interpretation, and
 * experimental front-end helpers.</p>
 */
public final class RxCoreComponents {
    private final CwSignalProcessor signalProcessor = new CwSignalProcessor();
    private final CwHybridTimingModel timingModel = new CwHybridTimingModel();
    private final LiveRxWpmGuard liveRxWpmGuard = new LiveRxWpmGuard();
    private final LiveRxToneEventStabilizer toneEventStabilizer = new LiveRxToneEventStabilizer();
    private final CwFrontEndLearningGate frontEndLearningGate = new CwFrontEndLearningGate();
    private final RxTurnController turnController = new RxTurnController();
    private final TimingAnchorController timingAnchorController = new TimingAnchorController();
    private final RxRawCommitGate rawCommitGate = new RxRawCommitGate();
    private final RxTurnTailRepairController turnTailRepairController = new RxTurnTailRepairController();
    private final CwDecoder decoder = new CwDecoder();
    private final RxTimingDecodeRunner timingDecodeRunner = new RxTimingDecodeRunner(decoder);
    private final RxToneTimingRunner toneTimingRunner = new RxToneTimingRunner(timingDecodeRunner);
    private final CwInterpreter rawInterpreter =
            new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);

    public RxCoreComponents() {
        // RX mainline already has explicit turn/session ownership. Keep the
        // timing model from silently demoting trust mid-turn during long but
        // still-continuous pauses.
        timingModel.setIdleResetEnabled(false);
    }

    public CwSignalProcessor signalProcessor() {
        return signalProcessor;
    }

    public CwHybridTimingModel timingModel() {
        return timingModel;
    }

    public LiveRxWpmGuard liveRxWpmGuard() {
        return liveRxWpmGuard;
    }

    public LiveRxToneEventStabilizer toneEventStabilizer() {
        return toneEventStabilizer;
    }

    public CwFrontEndLearningGate frontEndLearningGate() {
        return frontEndLearningGate;
    }

    public RxTurnController turnController() {
        return turnController;
    }

    public TimingAnchorController timingAnchorController() {
        return timingAnchorController;
    }

    public RxRawCommitGate rawCommitGate() {
        return rawCommitGate;
    }

    public RxTurnTailRepairController turnTailRepairController() {
        return turnTailRepairController;
    }

    public CwDecoder decoder() {
        return decoder;
    }

    public RxTimingDecodeRunner timingDecodeRunner() {
        return timingDecodeRunner;
    }

    public RxToneTimingRunner toneTimingRunner() {
        return toneTimingRunner;
    }

    public CwInterpreter rawInterpreter() {
        return rawInterpreter;
    }

    public void applySeedWpm(int seedWpm) {
        int clampedSeedWpm = Math.max(0, seedWpm);
        timingModel.setSeedWpm(clampedSeedWpm);
        liveRxWpmGuard.setSeedWpm(clampedSeedWpm);
        turnController.setTxSeedWpm(clampedSeedWpm);
        timingAnchorController.setSeedWpm(clampedSeedWpm);
    }

    public void resetRuntimeState(int seedWpm) {
        signalProcessor.reset();
        timingModel.reset();
        decoder.reset();
        rawInterpreter.reset();
        liveRxWpmGuard.reset();
        toneEventStabilizer.reset();
        timingAnchorController.reset();
        turnController.reset();
        rawCommitGate.reset();
        turnTailRepairController.reset();
        applySeedWpm(seedWpm);
    }
}
