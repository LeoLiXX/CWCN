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
import org.bi9clt.cwcn.core.timing.CwAdaptiveTimingModel;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.bi9clt.cwcn.core.timing.CwTimingModel;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertTrue;

public final class CwQsb700StrategyProbeTest {
    private static final int[] PREFERRED_MATRIX = new int[]{450, 500, 650, 800};

    @Test
    public void compareStrategiesOnQsb700AcrossPreferredOffsets() {
        for (int preferredToneHz : PREFERRED_MATRIX) {
            probe(preferredToneHz);
        }
    }

    private void probe(int preferredToneHz) {
        OfflineEvalBundle baseline = evaluateOfflineBundle(preferredToneHz, Strategy.BASELINE);
        OfflineEvalBundle adaptive = evaluateOfflineBundle(preferredToneHz, Strategy.ADAPTIVE);
        OfflineEvalBundle hybrid = evaluateOfflineBundle(preferredToneHz, Strategy.HYBRID);

        String summary = "scenario=user_qsb_cq_18wpm_700hz pref=" + preferredToneHz
                + "\nA baseline recall=" + baseline.result.textTokenRecall()
                + " chars=" + baseline.decoderCharacters
                + " wpm=" + baseline.timingSnapshot.estimatedWpm()
                + " tracked=" + baseline.signalSnapshot.targetToneFrequencyHz()
                + " text=" + normalize(baseline.decodedText)
                + "\nB adaptive recall=" + adaptive.result.textTokenRecall()
                + " chars=" + adaptive.decoderCharacters
                + " wpm=" + adaptive.timingSnapshot.estimatedWpm()
                + " tracked=" + adaptive.signalSnapshot.targetToneFrequencyHz()
                + " text=" + normalize(adaptive.decodedText)
                + "\nC hybrid recall=" + hybrid.result.textTokenRecall()
                + " chars=" + hybrid.decoderCharacters
                + " wpm=" + hybrid.timingSnapshot.estimatedWpm()
                + " tracked=" + hybrid.signalSnapshot.targetToneFrequencyHz()
                + " mode=" + hybrid.strategyName
                + " text=" + normalize(hybrid.decodedText);

        System.out.println(summary);
        assertTrue(summary, baseline.result.completed());
        assertTrue(summary, adaptive.result.completed());
        assertTrue(summary, hybrid.result.completed());
    }

    private String normalize(String decodedText) {
        return decodedText == null ? "" : decodedText.replace('\u25A1', '?');
    }

    private OfflineEvalBundle evaluateOfflineBundle(int preferredToneHz, Strategy strategy) {
        CwFixtureScenario scenario = findScenario("user_qsb_cq_18wpm_700hz");
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        CwSignalProcessor signalProcessor = new CwSignalProcessor();
        signalProcessor.setPreferredToneFrequencyHz(preferredToneHz);
        TimingAdapter timingAdapter = createTimingAdapter(strategy);
        CwDecoder decoder = new CwDecoder();
        CwInterpreter interpreter = new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);
        QsoStateMachine qsoStateMachine = new QsoStateMachine();

        List<AudioFrame> frames = source.renderFramesForTesting(scenario);
        AudioFrame lastFrame = null;
        for (AudioFrame frame : frames) {
            lastFrame = frame;
            List<CwToneEvent> toneEvents = signalProcessor.process(frame);
            for (CwToneEvent toneEvent : toneEvents) {
                drainTimingEvents(timingAdapter.process(toneEvent), decoder, interpreter, qsoStateMachine);
            }
        }

        if (lastFrame != null) {
            long frameDurationMs = Math.max(
                    1L,
                    Math.round(lastFrame.sampleCount() * 1000.0d / lastFrame.sampleRateHz())
            );
            long flushTimestampMs = lastFrame.capturedAtMs() + frameDurationMs;
            drainTimingEvents(timingAdapter.flushPendingGap(flushTimestampMs), decoder, interpreter, qsoStateMachine);
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
                timingAdapter.snapshot(),
                decoder.snapshot().decodedText(),
                decoder.snapshot().totalCharacters(),
                timingAdapter.strategyName()
        );
    }

    private TimingAdapter createTimingAdapter(Strategy strategy) {
        switch (strategy) {
            case ADAPTIVE:
                return new AdaptiveTimingAdapter();
            case HYBRID:
                return new HybridTimingAdapter();
            case BASELINE:
            default:
                return new BaselineTimingAdapter();
        }
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

    private enum Strategy {
        BASELINE,
        ADAPTIVE,
        HYBRID
    }

    private interface TimingAdapter {
        List<CwTimingEvent> process(CwToneEvent toneEvent);

        List<CwTimingEvent> flushPendingGap(long timestampMs);

        CwTimingSnapshot snapshot();

        String strategyName();
    }

    private static final class BaselineTimingAdapter implements TimingAdapter {
        private final CwTimingModel delegate = new CwTimingModel();

        @Override
        public List<CwTimingEvent> process(CwToneEvent toneEvent) {
            return delegate.process(toneEvent);
        }

        @Override
        public List<CwTimingEvent> flushPendingGap(long timestampMs) {
            return delegate.flushPendingGap(timestampMs);
        }

        @Override
        public CwTimingSnapshot snapshot() {
            return delegate.snapshot();
        }

        @Override
        public String strategyName() {
            return "BASELINE";
        }
    }

    private static final class AdaptiveTimingAdapter implements TimingAdapter {
        private final CwAdaptiveTimingModel delegate = new CwAdaptiveTimingModel();

        @Override
        public List<CwTimingEvent> process(CwToneEvent toneEvent) {
            return delegate.process(toneEvent);
        }

        @Override
        public List<CwTimingEvent> flushPendingGap(long timestampMs) {
            return delegate.flushPendingGap(timestampMs);
        }

        @Override
        public CwTimingSnapshot snapshot() {
            return delegate.snapshot();
        }

        @Override
        public String strategyName() {
            return "ADAPTIVE";
        }
    }

    private static final class HybridTimingAdapter implements TimingAdapter {
        private final CwHybridTimingModel delegate = new CwHybridTimingModel();

        @Override
        public List<CwTimingEvent> process(CwToneEvent toneEvent) {
            return delegate.process(toneEvent);
        }

        @Override
        public List<CwTimingEvent> flushPendingGap(long timestampMs) {
            return delegate.flushPendingGap(timestampMs);
        }

        @Override
        public CwTimingSnapshot snapshot() {
            return delegate.snapshot();
        }

        @Override
        public String strategyName() {
            return delegate.activeStrategyName();
        }
    }

    private static final class OfflineEvalBundle {
        private final CwFixtureEvaluationResult result;
        private final CwSignalSnapshot signalSnapshot;
        private final CwTimingSnapshot timingSnapshot;
        private final String decodedText;
        private final int decoderCharacters;
        private final String strategyName;

        private OfflineEvalBundle(
                CwFixtureEvaluationResult result,
                CwSignalSnapshot signalSnapshot,
                CwTimingSnapshot timingSnapshot,
                String decodedText,
                int decoderCharacters,
                String strategyName
        ) {
            this.result = result;
            this.signalSnapshot = signalSnapshot;
            this.timingSnapshot = timingSnapshot;
            this.decodedText = decodedText;
            this.decoderCharacters = decoderCharacters;
            this.strategyName = strategyName;
        }
    }
}
