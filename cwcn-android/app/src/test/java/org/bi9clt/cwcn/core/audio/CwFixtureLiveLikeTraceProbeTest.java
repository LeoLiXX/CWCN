package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.decoder.CwDecoder;
import org.bi9clt.cwcn.core.decoder.CwDecoderSnapshot;
import org.bi9clt.cwcn.core.eval.CwFixtureLibrary;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.qso.QsoStateMachine;
import org.bi9clt.cwcn.core.rx.LiveRxToneEventStabilizer;
import org.bi9clt.cwcn.core.rx.LiveRxWpmGuard;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;
import org.junit.Test;

import java.util.List;
import java.util.Locale;

public final class CwFixtureLiveLikeTraceProbeTest {
    private static final double LIVE_CHARACTER_FLUSH_GAP_RATIO = 3.35d;

    @Test
    public void traceNearFrequencyNarrowbandNoiseLiveLike() {
        traceScenario("near_frequency_narrowband_noise_report", 670, 15);
    }

    @Test
    public void traceDriftingNearbyInterfererLiveLike() {
        traceScenario("drifting_nearby_interferer_directed_report", 670, 15);
    }

    @Test
    public void traceModerateDualInterfererLiveLike() {
        traceScenario("moderate_dual_interferer_directed_report", 670, 15);
    }

    @Test
    public void traceBurstyInterfererLiveLike() {
        traceScenario("bursty_interferer_directed_report", 670, 15);
    }

    @Test
    public void traceWobblyDualInterfererBoundaryLiveLike() {
        traceScenario("wobbly_dual_interferer_boundary_report", 670, 15);
    }

    @Test
    public void traceBurstyDualInterfererBoundaryLiveLike() {
        traceScenario("bursty_dual_interferer_boundary_report", 670, 15);
    }

    @Test
    public void traceSweepingBoundaryInterfererLiveLike() {
        traceScenario("sweeping_boundary_interferer_directed_report", 670, 15);
    }

    private void traceScenario(String scenarioId, int preferredToneHz, int seedWpm) {
        CwFixtureScenario scenario = findScenario(scenarioId);
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        List<AudioFrame> frames = source.renderFramesForTesting(scenario);

        CwSignalProcessor signalProcessor = new CwSignalProcessor();
        signalProcessor.setPreferredToneFrequencyHz(preferredToneHz);

        CwHybridTimingModel timingModel = new CwHybridTimingModel();
        timingModel.setSeedWpm(seedWpm);
        CwDecoder decoder = new CwDecoder();
        CwInterpreter interpreter = new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);
        QsoStateMachine qsoStateMachine = new QsoStateMachine();
        AudioInputHealthTracker inputHealthTracker = new AudioInputHealthTracker();
        LiveRxWpmGuard wpmGuard = new LiveRxWpmGuard();
        wpmGuard.setSeedWpm(seedWpm);
        LiveRxToneEventStabilizer toneEventStabilizer = new LiveRxToneEventStabilizer();

        System.out.println("==== fixture live-like trace: " + scenarioId + " ====");
        for (int frameIndex = 0; frameIndex < frames.size(); frameIndex++) {
            AudioFrame frame = frames.get(frameIndex);
            inputHealthTracker.process(frame);
            AudioInputHealthSnapshot healthSnapshot = inputHealthTracker.snapshot();

            List<CwToneEvent> rawToneEvents = signalProcessor.process(frame);
            CwSignalSnapshot signalSnapshot = signalProcessor.snapshot();
            long frameEndTimestampMs = estimateFrameEndTimestampMs(frame);
            int beforeToneCount = signalSnapshot.totalToneOnEvents() + signalSnapshot.totalToneOffEvents();
            int beforeTimingCount = timingModel.rawSnapshot().totalToneEvents() + timingModel.rawSnapshot().totalGapEvents();
            int beforeDecodedChars = decoder.snapshot().totalCharacters();

            if (frameIndex < 80 || !rawToneEvents.isEmpty()) {
                System.out.println(renderFrameHeader(frameIndex, frame, signalSnapshot, healthSnapshot, wpmGuard, timingModel));
            }

            for (CwToneEvent rawToneEvent : rawToneEvents) {
                routeRawToneEvent(
                        rawToneEvent,
                        healthSnapshot,
                        signalProcessor,
                        timingModel,
                        decoder,
                        interpreter,
                        qsoStateMachine,
                        wpmGuard,
                        toneEventStabilizer
                );
            }

            flushStabilizer(
                    frameEndTimestampMs,
                    healthSnapshot,
                    signalProcessor,
                    timingModel,
                    decoder,
                    interpreter,
                    qsoStateMachine,
                    wpmGuard,
                    toneEventStabilizer
            );

            maybeFlushPendingCharacterDuringSilence(
                    frame,
                    signalProcessor,
                    timingModel,
                    decoder,
                    interpreter,
                    qsoStateMachine,
                    wpmGuard
            );

            CwSignalSnapshot afterSignalSnapshot = signalProcessor.snapshot();
            CwTimingSnapshot afterTimingSnapshot = timingModel.rawSnapshot();
            CwDecoderSnapshot afterDecoderSnapshot = decoder.snapshot();
            int afterToneCount = afterSignalSnapshot.totalToneOnEvents() + afterSignalSnapshot.totalToneOffEvents();
            int afterTimingCount = afterTimingSnapshot.totalToneEvents() + afterTimingSnapshot.totalGapEvents();
            int afterDecodedChars = afterDecoderSnapshot.totalCharacters();
            if (afterToneCount != beforeToneCount
                    || afterTimingCount != beforeTimingCount
                    || afterDecodedChars != beforeDecodedChars
                    || !rawToneEvents.isEmpty()) {
                System.out.println(String.format(
                        Locale.US,
                        "    ==> toneEv=%d timingEv=%d chars=%d decoded=\"%s\" seq=\"%s\" displayWpm=%d hold=%s lock=%.0f%% near=%.0f%% unl=%.0f%% target=%d eff=%d last=%s",
                        afterToneCount,
                        afterTimingCount,
                        afterDecodedChars,
                        sanitize(afterDecoderSnapshot.decodedText()),
                        afterDecoderSnapshot.currentSequence(),
                        wpmGuard.resolveDisplayWpm(afterSignalSnapshot, afterTimingSnapshot, frameEndTimestampMs),
                        wpmGuard.holding(),
                        afterSignalSnapshot.recentLockedFrameRatio() * 100.0d,
                        afterSignalSnapshot.recentNearTargetLockedFrameRatio() * 100.0d,
                        afterSignalSnapshot.recentActiveUnlockedFrameRatio() * 100.0d,
                        afterSignalSnapshot.targetToneFrequencyHz(),
                        afterSignalSnapshot.effectiveTrackedToneFrequencyHz(),
                        renderLastEvent(afterSignalSnapshot)
                ));
            }
        }

        long flushTimestampMs = frames.isEmpty() ? 0L : estimateFrameEndTimestampMs(frames.get(frames.size() - 1));
        flushStabilizer(
                flushTimestampMs,
                inputHealthTracker.snapshot(),
                signalProcessor,
                timingModel,
                decoder,
                interpreter,
                qsoStateMachine,
                wpmGuard,
                toneEventStabilizer
        );
        flushPendingDecode(
                flushTimestampMs,
                signalProcessor,
                timingModel,
                decoder,
                interpreter,
                qsoStateMachine,
                wpmGuard
        );

        CwSignalSnapshot finalSignalSnapshot = signalProcessor.snapshot();
        CwTimingSnapshot finalTimingSnapshot = timingModel.rawSnapshot();
        CwDecoderSnapshot finalDecoderSnapshot = decoder.snapshot();
        System.out.println(String.format(
                Locale.US,
                "FINAL decoded=\"%s\" chars=%d toneOn=%d toneOff=%d timingTone=%d timingGap=%d displayWpm=%d rawWpm=%d rawWpmPrecise=%.2f hold=%s target=%d eff=%d lock=%.0f%% near=%.0f%% unl=%.0f%% last=%s",
                sanitize(finalDecoderSnapshot.decodedText()),
                finalDecoderSnapshot.totalCharacters(),
                finalSignalSnapshot.totalToneOnEvents(),
                finalSignalSnapshot.totalToneOffEvents(),
                finalTimingSnapshot.totalToneEvents(),
                finalTimingSnapshot.totalGapEvents(),
                wpmGuard.resolveDisplayWpm(finalSignalSnapshot, finalTimingSnapshot, flushTimestampMs),
                finalTimingSnapshot.estimatedWpm(),
                finalTimingSnapshot.estimatedWpmPrecise(),
                wpmGuard.holding(),
                finalSignalSnapshot.targetToneFrequencyHz(),
                finalSignalSnapshot.effectiveTrackedToneFrequencyHz(),
                finalSignalSnapshot.recentLockedFrameRatio() * 100.0d,
                finalSignalSnapshot.recentNearTargetLockedFrameRatio() * 100.0d,
                finalSignalSnapshot.recentActiveUnlockedFrameRatio() * 100.0d,
                renderLastEvent(finalSignalSnapshot)
        ));
    }

    private void routeRawToneEvent(
            CwToneEvent rawToneEvent,
            AudioInputHealthSnapshot healthSnapshot,
            CwSignalProcessor signalProcessor,
            CwHybridTimingModel timingModel,
            CwDecoder decoder,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            LiveRxWpmGuard wpmGuard,
            LiveRxToneEventStabilizer toneEventStabilizer
    ) {
        CwSignalSnapshot signalSnapshot = signalProcessor.snapshot();
        CwTimingSnapshot timingSnapshot = timingModel.rawSnapshot();
        long referenceDotEstimateMs = resolveReferenceDotEstimateMs(signalSnapshot, timingSnapshot, rawToneEvent.timestampMs(), wpmGuard);
        List<CwToneEvent> stabilizedEvents = toneEventStabilizer.process(
                rawToneEvent,
                signalSnapshot,
                healthSnapshot,
                referenceDotEstimateMs
        );
        System.out.println(String.format(
                Locale.US,
                "    RAW   %s refDot=%d target=%d eff=%d hold=%s -> %s",
                renderToneEvent(rawToneEvent),
                referenceDotEstimateMs,
                signalSnapshot.targetToneFrequencyHz(),
                signalSnapshot.effectiveTrackedToneFrequencyHz(),
                wpmGuard.holding(),
                renderToneEvents(stabilizedEvents)
        ));
        for (CwToneEvent stabilizedEvent : stabilizedEvents) {
            dispatchStabilizedToneEvent(
                    stabilizedEvent,
                    rawToneEvent.timestampMs(),
                    healthSnapshot,
                    signalProcessor,
                    timingModel,
                    decoder,
                    interpreter,
                    qsoStateMachine,
                    wpmGuard,
                    toneEventStabilizer
            );
        }
    }

    private void flushStabilizer(
            long nowTimestampMs,
            AudioInputHealthSnapshot healthSnapshot,
            CwSignalProcessor signalProcessor,
            CwHybridTimingModel timingModel,
            CwDecoder decoder,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            LiveRxWpmGuard wpmGuard,
            LiveRxToneEventStabilizer toneEventStabilizer
    ) {
        List<CwToneEvent> flushedEvents = toneEventStabilizer.flush(nowTimestampMs);
        if (!flushedEvents.isEmpty()) {
            System.out.println(String.format(
                    Locale.US,
                    "    FLUSH stabilizer @%d -> %s",
                    nowTimestampMs,
                    renderToneEvents(flushedEvents)
            ));
        }
        for (CwToneEvent stabilizedEvent : flushedEvents) {
            dispatchStabilizedToneEvent(
                    stabilizedEvent,
                    nowTimestampMs,
                    healthSnapshot,
                    signalProcessor,
                    timingModel,
                    decoder,
                    interpreter,
                    qsoStateMachine,
                    wpmGuard,
                    toneEventStabilizer
            );
        }
    }

    private void dispatchStabilizedToneEvent(
            CwToneEvent stabilizedEvent,
            long nowTimestampMs,
            AudioInputHealthSnapshot healthSnapshot,
            CwSignalProcessor signalProcessor,
            CwHybridTimingModel timingModel,
            CwDecoder decoder,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            LiveRxWpmGuard wpmGuard,
            LiveRxToneEventStabilizer toneEventStabilizer
    ) {
        CwSignalSnapshot signalSnapshot = signalProcessor.snapshot();
        CwTimingSnapshot timingSnapshot = timingModel.rawSnapshot();
        long referenceDotEstimateMs = resolveReferenceDotEstimateMs(signalSnapshot, timingSnapshot, nowTimestampMs, wpmGuard);
        boolean suppressed = referenceDotEstimateMs > 0L
                && toneEventStabilizer.shouldSuppressShortTone(
                stabilizedEvent,
                signalSnapshot,
                healthSnapshot,
                referenceDotEstimateMs
        );
        System.out.println(String.format(
                Locale.US,
                "      STAB %s suppress=%s refDot=%d dispWpm=%d hold=%s",
                renderToneEvent(stabilizedEvent),
                suppressed,
                referenceDotEstimateMs,
                wpmGuard.resolveDisplayWpm(signalSnapshot, timingSnapshot, nowTimestampMs),
                wpmGuard.holding()
        ));
        if (suppressed) {
            return;
        }

        boolean allowTimingLearning = wpmGuard.shouldAllowTimingLearningForEvent(
                stabilizedEvent,
                signalSnapshot,
                timingSnapshot,
                nowTimestampMs
        );
        List<CwTimingEvent> timingEvents = timingModel.process(stabilizedEvent, allowTimingLearning);
        if (!timingEvents.isEmpty()) {
            System.out.println("        TIMR " + renderTimingEvents(timingEvents));
        }
        CwSignalSnapshot currentSignalSnapshot = signalProcessor.snapshot();
        CwTimingSnapshot currentTimingSnapshot = timingModel.rawSnapshot();
        for (CwTimingEvent timingEvent : timingEvents) {
            CwTimingEvent adaptedTimingEvent = wpmGuard.adaptTimingEvent(
                    timingEvent,
                    currentSignalSnapshot,
                    currentTimingSnapshot,
                    nowTimestampMs
            );
            System.out.println(String.format(
                    Locale.US,
                    "        TIMG raw=%s adapted=%s dispWpm=%d hold=%s",
                    renderTimingEvent(timingEvent),
                    renderTimingEvent(adaptedTimingEvent),
                    wpmGuard.resolveDisplayWpm(currentSignalSnapshot, currentTimingSnapshot, nowTimestampMs),
                    wpmGuard.holding()
            ));
            processTimingEvent(
                    adaptedTimingEvent,
                    signalProcessor,
                    timingModel,
                    decoder,
                    interpreter,
                    qsoStateMachine,
                    wpmGuard
            );
        }
    }

    private void processTimingEvent(
            CwTimingEvent timingEvent,
            CwSignalProcessor signalProcessor,
            CwHybridTimingModel timingModel,
            CwDecoder decoder,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            LiveRxWpmGuard wpmGuard
    ) {
        if (timingEvent == null) {
            return;
        }
        List<CwDecodeEvent> decodeEvents = decoder.process(timingEvent);
        if (!decodeEvents.isEmpty()) {
            System.out.println("          DECR " + renderDecodeEvents(decodeEvents));
        }
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            boolean stableDecode = shouldTreatAsStableDecode(
                    decodeEvent,
                    signalProcessor.snapshot(),
                    timingModel.rawSnapshot()
            );
            if (stableDecode) {
                timingModel.notifyStableDecode(decodeEvent.timestampMs());
            }
            if (decodeEvent.type() == CwDecodeEvent.Type.CHARACTER_DECODED) {
                int beforeDisplayWpm = wpmGuard.resolveDisplayWpm(
                        signalProcessor.snapshot(),
                        timingModel.snapshot(),
                        decodeEvent.timestampMs()
                );
                wpmGuard.noteDecodedCharacter(
                        decodeEvent.unknownCharacter(),
                        signalProcessor.snapshot(),
                        timingModel.rawSnapshot(),
                        decodeEvent.timestampMs()
                );
                int afterDisplayWpm = wpmGuard.resolveDisplayWpm(
                        signalProcessor.snapshot(),
                        timingModel.snapshot(),
                        decodeEvent.timestampMs()
                );
                System.out.println(String.format(
                        Locale.US,
                        "          WPMG stable=%s unknown=%s wpm=%d->%d hold=%s emitted=%s seq=%s",
                        stableDecode,
                        decodeEvent.unknownCharacter(),
                        beforeDisplayWpm,
                        afterDisplayWpm,
                        wpmGuard.holding(),
                        decodeEvent.emittedValue(),
                        decodeEvent.sourceSequence()
                ));
            }
            interpreter.process(decodeEvent);
            qsoStateMachine.process(interpreter.snapshot(), decodeEvent.timestampMs());
        }
    }

    private void maybeFlushPendingCharacterDuringSilence(
            AudioFrame frame,
            CwSignalProcessor signalProcessor,
            CwHybridTimingModel timingModel,
            CwDecoder decoder,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            LiveRxWpmGuard wpmGuard
    ) {
        if (frame == null || !decoder.hasPendingCharacter()) {
            return;
        }
        CwSignalSnapshot signalSnapshot = signalProcessor.snapshot();
        if (signalSnapshot.toneActive()) {
            return;
        }
        CwToneEvent lastSignalEvent = signalSnapshot.lastEvent();
        if (lastSignalEvent == null || lastSignalEvent.type() != CwToneEvent.Type.TONE_OFF) {
            return;
        }

        long flushTimestampMs = estimateFrameEndTimestampMs(frame);
        long silentGapMs = Math.max(0L, flushTimestampMs - lastSignalEvent.timestampMs());
        long minFlushGapMs = minimumLiveCharacterFlushGapMs(
                signalSnapshot,
                timingModel.rawSnapshot(),
                flushTimestampMs,
                wpmGuard
        );
        if (silentGapMs < minFlushGapMs) {
            return;
        }

        List<CwDecodeEvent> trailingDecodeEvents = decoder.flushPendingCharacter(flushTimestampMs);
        System.out.println(String.format(
                Locale.US,
                "    SILENCE flush gap=%d min=%d -> %s",
                silentGapMs,
                minFlushGapMs,
                renderDecodeEvents(trailingDecodeEvents)
        ));
        for (CwDecodeEvent decodeEvent : trailingDecodeEvents) {
            boolean stableDecode = shouldTreatAsStableDecode(
                    decodeEvent,
                    signalProcessor.snapshot(),
                    timingModel.rawSnapshot()
            );
            if (stableDecode) {
                timingModel.notifyStableDecode(decodeEvent.timestampMs());
            }
            if (decodeEvent.type() == CwDecodeEvent.Type.CHARACTER_DECODED) {
                wpmGuard.noteDecodedCharacter(
                        decodeEvent.unknownCharacter(),
                        signalProcessor.snapshot(),
                        timingModel.rawSnapshot(),
                        decodeEvent.timestampMs()
                );
            }
            interpreter.process(decodeEvent);
            qsoStateMachine.process(interpreter.snapshot(), decodeEvent.timestampMs());
        }
    }

    private void flushPendingDecode(
            long timestampMs,
            CwSignalProcessor signalProcessor,
            CwHybridTimingModel timingModel,
            CwDecoder decoder,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            LiveRxWpmGuard wpmGuard
    ) {
        CwSignalSnapshot currentSignalSnapshot = signalProcessor.snapshot();
        CwTimingSnapshot currentTimingSnapshot = timingModel.rawSnapshot();
        boolean allowTimingLearning = wpmGuard.shouldAllowTimingLearning(
                currentSignalSnapshot,
                currentTimingSnapshot,
                timestampMs
        );
        List<CwTimingEvent> timingEvents = timingModel.flushPendingGap(timestampMs, allowTimingLearning);
        if (!timingEvents.isEmpty()) {
            System.out.println("FINAL flush timing raw " + renderTimingEvents(timingEvents));
        }
        currentSignalSnapshot = signalProcessor.snapshot();
        currentTimingSnapshot = timingModel.rawSnapshot();
        for (CwTimingEvent timingEvent : timingEvents) {
            CwTimingEvent adaptedTimingEvent = wpmGuard.adaptTimingEvent(
                    timingEvent,
                    currentSignalSnapshot,
                    currentTimingSnapshot,
                    timestampMs
            );
            System.out.println(String.format(
                    Locale.US,
                    "FINAL flush adapted raw=%s adapted=%s",
                    renderTimingEvent(timingEvent),
                    renderTimingEvent(adaptedTimingEvent)
            ));
            processTimingEvent(
                    adaptedTimingEvent,
                    signalProcessor,
                    timingModel,
                    decoder,
                    interpreter,
                    qsoStateMachine,
                    wpmGuard
            );
        }
        List<CwDecodeEvent> trailingDecodeEvents = decoder.flushPendingCharacter(timestampMs);
        if (!trailingDecodeEvents.isEmpty()) {
            System.out.println("FINAL char flush " + renderDecodeEvents(trailingDecodeEvents));
        }
        for (CwDecodeEvent decodeEvent : trailingDecodeEvents) {
            boolean stableDecode = shouldTreatAsStableDecode(
                    decodeEvent,
                    signalProcessor.snapshot(),
                    timingModel.rawSnapshot()
            );
            if (stableDecode) {
                timingModel.notifyStableDecode(decodeEvent.timestampMs());
            }
            if (decodeEvent.type() == CwDecodeEvent.Type.CHARACTER_DECODED) {
                wpmGuard.noteDecodedCharacter(
                        decodeEvent.unknownCharacter(),
                        signalProcessor.snapshot(),
                        timingModel.rawSnapshot(),
                        decodeEvent.timestampMs()
                );
            }
            interpreter.process(decodeEvent);
            qsoStateMachine.process(interpreter.snapshot(), decodeEvent.timestampMs());
        }
    }

    private boolean shouldTreatAsStableDecode(
            CwDecodeEvent decodeEvent,
            CwSignalSnapshot signalSnapshot,
            CwTimingSnapshot timingSnapshot
    ) {
        return decodeEvent != null
                && decodeEvent.type() == CwDecodeEvent.Type.CHARACTER_DECODED
                && !decodeEvent.unknownCharacter()
                && signalSnapshot != null
                && timingSnapshot != null
                && timingSnapshot.estimatedWpm() > 0
                && timingSnapshot.dotEstimateMs() > 0L
                && signalSnapshot.targetToneLocked()
                && signalSnapshot.recentLockedFrameRatio() >= 0.60d
                && signalSnapshot.recentNearTargetLockedFrameRatio() >= 0.64d
                && signalSnapshot.recentActiveUnlockedFrameRatio() <= 0.24d
                && signalSnapshot.toneDominanceRatio() >= 0.44d
                && signalSnapshot.narrowbandIsolationRatio() >= 0.54d;
    }

    private long resolveReferenceDotEstimateMs(
            CwSignalSnapshot signalSnapshot,
            CwTimingSnapshot timingSnapshot,
            long nowTimestampMs,
            LiveRxWpmGuard wpmGuard
    ) {
        long referenceDotEstimateMs = wpmGuard.resolveReferenceDotEstimateMs(timingSnapshot);
        if (referenceDotEstimateMs > 0L) {
            return referenceDotEstimateMs;
        }
        return wpmGuard.resolveEffectiveDotEstimateMs(signalSnapshot, timingSnapshot, nowTimestampMs);
    }

    private long minimumLiveCharacterFlushGapMs(
            CwSignalSnapshot signalSnapshot,
            CwTimingSnapshot timingSnapshot,
            long nowTimestampMs,
            LiveRxWpmGuard wpmGuard
    ) {
        long dotEstimateMs = Math.max(
                1L,
                wpmGuard.resolveEffectiveDotEstimateMs(signalSnapshot, timingSnapshot, nowTimestampMs)
        );
        return Math.max(1L, Math.round(dotEstimateMs * LIVE_CHARACTER_FLUSH_GAP_RATIO));
    }

    private long estimateFrameEndTimestampMs(AudioFrame frame) {
        long frameDurationMs = Math.max(
                1L,
                Math.round(frame.sampleCount() * 1000.0d / Math.max(1, frame.sampleRateHz()))
        );
        return frame.capturedAtMs() + frameDurationMs;
    }

    private CwFixtureScenario findScenario(String scenarioId) {
        for (CwFixtureScenario scenario : CwFixtureLibrary.scenarios()) {
            if (scenario.id().equals(scenarioId)) {
                return scenario;
            }
        }
        throw new IllegalArgumentException("Unknown scenario: " + scenarioId);
    }

    private String renderFrameHeader(
            int frameIndex,
            AudioFrame frame,
            CwSignalSnapshot signalSnapshot,
            AudioInputHealthSnapshot healthSnapshot,
            LiveRxWpmGuard wpmGuard,
            CwHybridTimingModel timingModel
    ) {
        CwTimingSnapshot timingSnapshot = timingModel.rawSnapshot();
        return String.format(
                Locale.US,
                "F%03d @%d peak=%d rms=%.1f hot=%.0f%% clip=%.0f%% act=%s lock=%s target=%d eff=%d thr=%d/%d dom=%.0f%% iso=%.0f%% displayWpm=%d rawWpm=%d hold=%s last=%s",
                frameIndex,
                frame.capturedAtMs(),
                frame.peakAmplitude(),
                frame.rmsAmplitude(),
                healthSnapshot.recentHotFrameRatio() * 100.0d,
                healthSnapshot.recentClippingFrameRatio() * 100.0d,
                signalSnapshot.toneActive(),
                signalSnapshot.targetToneLocked(),
                signalSnapshot.targetToneFrequencyHz(),
                signalSnapshot.effectiveTrackedToneFrequencyHz(),
                signalSnapshot.currentThreshold(),
                signalSnapshot.releaseThreshold(),
                signalSnapshot.toneDominanceRatio() * 100.0d,
                signalSnapshot.narrowbandIsolationRatio() * 100.0d,
                wpmGuard.resolveDisplayWpm(signalSnapshot, timingSnapshot, frame.capturedAtMs()),
                timingSnapshot.estimatedWpm(),
                wpmGuard.holding(),
                renderLastEvent(signalSnapshot)
        );
    }

    private String renderLastEvent(CwSignalSnapshot snapshot) {
        if (snapshot.lastEvent() == null) {
            return "-";
        }
        return snapshot.lastEvent().type()
                + "@"
                + snapshot.lastEvent().timestampMs()
                + "/"
                + snapshot.lastEvent().toneDurationMs();
    }

    private String renderToneEvents(List<CwToneEvent> events) {
        if (events == null || events.isEmpty()) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < events.size(); index++) {
            if (index > 0) {
                builder.append(" | ");
            }
            builder.append(renderToneEvent(events.get(index)));
        }
        return builder.toString();
    }

    private String renderToneEvent(CwToneEvent event) {
        if (event == null) {
            return "-";
        }
        return event.type()
                + "@"
                + event.timestampMs()
                + "/"
                + event.toneDurationMs();
    }

    private String renderTimingEvents(List<CwTimingEvent> events) {
        if (events == null || events.isEmpty()) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < events.size(); index++) {
            if (index > 0) {
                builder.append(" | ");
            }
            builder.append(renderTimingEvent(events.get(index)));
        }
        return builder.toString();
    }

    private String renderTimingEvent(CwTimingEvent event) {
        if (event == null) {
            return "-";
        }
        return event.kind()
                + "/"
                + event.classification()
                + "@"
                + event.timestampMs()
                + "/"
                + event.durationMs()
                + " dot="
                + event.dotEstimateMs();
    }

    private String renderDecodeEvents(List<CwDecodeEvent> events) {
        if (events == null || events.isEmpty()) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < events.size(); index++) {
            if (index > 0) {
                builder.append(" | ");
            }
            CwDecodeEvent event = events.get(index);
            builder.append(event.type())
                    .append('@')
                    .append(event.timestampMs())
                    .append('/')
                    .append(event.emittedValue())
                    .append('/')
                    .append(event.sourceSequence())
                    .append('/')
                    .append(event.unknownCharacter() ? "?" : "ok");
        }
        return builder.toString();
    }

    private String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace(CwDecoder.UNKNOWN_CHARACTER, "?").trim();
        return normalized.isEmpty() ? "(empty)" : normalized;
    }
}
