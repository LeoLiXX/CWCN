package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.decoder.CwDecoder;
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

import static org.junit.Assert.assertTrue;

public final class CwToneSweepPreferredOffsetProbeTest {
    @Test
    public void probeToneSweepAcrossPreferredOffsetsUsingHybridTimingPath() {
        probePreferredOffset(450);
        probePreferredOffset(500);
        probePreferredOffset(650);
        probePreferredOffset(800);
    }

    private void probePreferredOffset(int preferredToneHz) {
        OfflineEvalBundle bundle = evaluateOfflineBundle("user_tone_sweep_vvv_18wpm", preferredToneHz);
        String decodedText = bundle.decodedText == null
                ? ""
                : bundle.decodedText.replace('\u25A1', '?');
        String summary = "scenario=user_tone_sweep_vvv_18wpm"
                + "\npref=" + preferredToneHz
                + " peakTone=" + Math.round(bundle.signalSnapshot.peakToneRmsAmplitude())
                + " tracked=" + bundle.signalSnapshot.targetToneFrequencyHz()
                + " lock=" + bundle.signalSnapshot.targetToneLocked()
                + "\nPW=" + bundle.signalSnapshot.preferredWindowWinnerFrequencyHz()
                + " WS=" + bundle.signalSnapshot.wideScanWinnerFrequencyHz()
                + " AQ=" + bundle.signalSnapshot.acquisitionWinnerFrequencyHz()
                + " AD=" + bundle.signalSnapshot.finalAdoptedFrequencyHz()
                + " TRK=" + bundle.signalSnapshot.targetToneFrequencyHz()
                + "\nAQsrc=" + bundle.signalSnapshot.acquisitionWinnerSource()
                + " ADsrc=" + bundle.signalSnapshot.finalAdoptedSource()
                + " AQconf=" + bundle.signalSnapshot.acquisitionWinnerConfidence()
                + " ADconf=" + bundle.signalSnapshot.finalAdoptedConfidence()
                + "\nrecall=" + bundle.result.textTokenRecall()
                + " chars=" + bundle.decoderCharacters
                + " wpm=" + bundle.timingSnapshot.estimatedWpm()
                + "\ntext=" + decodedText;

        System.out.println(summary);
        assertTrue(summary, bundle.result.completed());
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

        CwSignalSnapshot signalSnapshot = signalProcessor.snapshot();
        CwTimingSnapshot timingSnapshot = timingModel.snapshot();
        CwFixtureEvaluationResult result = CwFixtureEvaluator.evaluate(
                scenario,
                interpreter.snapshot(),
                qsoStateMachine.snapshot(),
                signalSnapshot,
                true
        );

        return new OfflineEvalBundle(
                result,
                signalSnapshot,
                timingSnapshot,
                decoder.snapshot().decodedText(),
                decoder.snapshot().totalCharacters()
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

    private static final class OfflineEvalBundle {
        private final CwFixtureEvaluationResult result;
        private final CwSignalSnapshot signalSnapshot;
        private final CwTimingSnapshot timingSnapshot;
        private final String decodedText;
        private final int decoderCharacters;

        private OfflineEvalBundle(
                CwFixtureEvaluationResult result,
                CwSignalSnapshot signalSnapshot,
                CwTimingSnapshot timingSnapshot,
                String decodedText,
                int decoderCharacters
        ) {
            this.result = result;
            this.signalSnapshot = signalSnapshot;
            this.timingSnapshot = timingSnapshot;
            this.decodedText = decodedText;
            this.decoderCharacters = decoderCharacters;
        }
    }
}
