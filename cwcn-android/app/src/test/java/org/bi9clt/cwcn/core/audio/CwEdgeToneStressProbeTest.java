package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.decoder.CwDecoder;
import org.bi9clt.cwcn.core.decoder.CwDecoderSnapshot;
import org.bi9clt.cwcn.core.eval.CwFixtureEvaluationResult;
import org.bi9clt.cwcn.core.eval.CwFixtureEvaluator;
import org.bi9clt.cwcn.core.eval.CwFixtureLibrary;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.qso.QsoStateMachine;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public final class CwEdgeToneStressProbeTest {
    @Test
    public void stressProbe460And840hzLongTextRemainObservableEvenWhenDecodeDegrades() {
        probe("user_long_qso_edge_low_460hz_stress", 700);
        probe("user_long_qso_edge_high_840hz_stress", 450);
    }

    private void probe(String scenarioId, int preferredToneHz) {
        OfflineEvalBundle bundle = evaluateOfflineBundle(scenarioId, preferredToneHz);
        String decodedText = sanitizeText(bundle.decoderSnapshot.decodedText());
        String summary = scenarioId
                + " pref=" + preferredToneHz
                + "\nresult=" + bundle.result.renderSummary()
                + "\ntracked=" + bundle.signalSnapshot.targetToneFrequencyHz()
                + " AQ=" + bundle.signalSnapshot.acquisitionWinnerFrequencyHz()
                + " AD=" + bundle.signalSnapshot.finalAdoptedFrequencyHz()
                + " ADsrc=" + bundle.signalSnapshot.finalAdoptedSource()
                + " lockCov=" + bundle.signalSnapshot.lockedFrameRatio()
                + " peakToneRms=" + bundle.signalSnapshot.peakToneRmsAmplitude()
                + "\nchars=" + bundle.decoderSnapshot.totalCharacters()
                + " recall=" + bundle.result.textTokenRecall()
                + " wpm=" + bundle.timingSnapshot.estimatedWpm()
                + "\ntext=" + decodedText;

        System.out.println(summary);
        assertTrue(summary, bundle.result.completed());
        assertNotEquals(summary, "RUN", bundle.result.likelyBottleneckCode());
        assertTrue(summary, bundle.signalSnapshot.peakToneRmsAmplitude() >= 2500.0d);
        assertTrue(summary, bundle.signalSnapshot.totalToneOnEvents() >= 120);
        assertTrue(summary, bundle.signalSnapshot.totalToneOffEvents() >= 120);
        assertTrue(summary, bundle.decoderSnapshot.totalCharacters() >= 20);
    }

    private OfflineEvalBundle evaluateOfflineBundle(String scenarioId, int preferredToneHz) {
        CwFixtureScenario scenario = findScenario(scenarioId);
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        CwSignalProcessor signalProcessor = new CwSignalProcessor();
        signalProcessor.setPreferredToneFrequencyHz(preferredToneHz);
        CwHybridTimingModel timingModel = new CwHybridTimingModel();
        CwDecoder decoder = new CwDecoder();
        CwInterpreter interpreter = new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);
        QsoStateMachine qsoStateMachine = new QsoStateMachine();

        List<AudioFrame> frames = source.renderFramesForTesting(scenario);
        AudioFrame lastFrame = null;
        for (AudioFrame frame : frames) {
            lastFrame = frame;
            List<CwToneEvent> toneEvents = signalProcessor.process(frame);
            for (CwToneEvent toneEvent : toneEvents) {
                drainTimingEvents(timingModel.process(toneEvent), decoder, interpreter, qsoStateMachine);
            }
        }

        if (lastFrame != null) {
            long frameDurationMs = Math.max(
                    1L,
                    Math.round(lastFrame.sampleCount() * 1000.0d / lastFrame.sampleRateHz())
            );
            long flushTimestampMs = lastFrame.capturedAtMs() + frameDurationMs;
            drainTimingEvents(timingModel.flushPendingGap(flushTimestampMs), decoder, interpreter, qsoStateMachine);
            drainDecodeEvents(decoder.flushPendingCharacter(flushTimestampMs), interpreter, qsoStateMachine);
        }

        return new OfflineEvalBundle(
                CwFixtureEvaluator.evaluate(
                        scenario,
                        interpreter.snapshot(),
                        qsoStateMachine.snapshot(),
                        signalProcessor.snapshot(),
                        true
                ),
                signalProcessor.snapshot(),
                timingModel.snapshot(),
                decoder.snapshot()
        );
    }

    private void drainTimingEvents(
            List<CwTimingEvent> timingEvents,
            CwDecoder decoder,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine
    ) {
        for (CwTimingEvent timingEvent : timingEvents) {
            List<CwDecodeEvent> decodeEvents = decoder.process(timingEvent);
            for (CwDecodeEvent decodeEvent : decodeEvents) {
                interpreter.process(decodeEvent);
                qsoStateMachine.process(interpreter.snapshot(), decodeEvent.timestampMs());
            }
        }
    }

    private void drainDecodeEvents(
            List<CwDecodeEvent> decodeEvents,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine
    ) {
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            interpreter.process(decodeEvent);
            qsoStateMachine.process(interpreter.snapshot(), decodeEvent.timestampMs());
        }
    }

    private CwFixtureScenario findScenario(String scenarioId) {
        for (CwFixtureScenario scenario : CwFixtureLibrary.scenarios()) {
            if (scenario.id().equals(scenarioId)) {
                return scenario;
            }
        }
        throw new IllegalArgumentException("Unknown scenario: " + scenarioId);
    }

    private String sanitizeText(String text) {
        return text == null ? "" : text.replace('\u25A1', '?');
    }

    private static final class OfflineEvalBundle {
        private final CwFixtureEvaluationResult result;
        private final CwSignalSnapshot signalSnapshot;
        private final CwTimingSnapshot timingSnapshot;
        private final CwDecoderSnapshot decoderSnapshot;

        private OfflineEvalBundle(
                CwFixtureEvaluationResult result,
                CwSignalSnapshot signalSnapshot,
                CwTimingSnapshot timingSnapshot,
                CwDecoderSnapshot decoderSnapshot
        ) {
            this.result = result;
            this.signalSnapshot = signalSnapshot;
            this.timingSnapshot = timingSnapshot;
            this.decoderSnapshot = decoderSnapshot;
        }
    }
}
