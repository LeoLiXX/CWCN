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

public final class CwTimingModelStrategyProbeTest {
    @Test
    public void probeBaselineAdaptiveAndHybridOnFastBoundaryFixtures() {
        assertHybridHealthyAt25Wpm();
        assertHybridRecoversFast30Wpm();
        assertHybridKeepsSpeedSweepStable();
    }

    private void assertHybridHealthyAt25Wpm() {
        String scenarioId = "user_noise_cq_25wpm_700hz";
        OfflineEvalBundle baseline = evaluateOfflineBundle(scenarioId, Strategy.BASELINE);
        OfflineEvalBundle adaptive = evaluateOfflineBundle(scenarioId, Strategy.ADAPTIVE);
        OfflineEvalBundle hybrid = evaluateOfflineBundle(scenarioId, Strategy.HYBRID);

        String summary = scenarioId
                + "\nA baseline recall=" + baseline.result.textTokenRecall()
                + " chars=" + baseline.decoderCharacters
                + " wpm=" + baseline.timingSnapshot.estimatedWpm()
                + " text=" + baseline.decodedText
                + "\nB adaptive recall=" + adaptive.result.textTokenRecall()
                + " chars=" + adaptive.decoderCharacters
                + " wpm=" + adaptive.timingSnapshot.estimatedWpm()
                + " text=" + adaptive.decodedText
                + "\nC hybrid recall=" + hybrid.result.textTokenRecall()
                + " chars=" + hybrid.decoderCharacters
                + " wpm=" + hybrid.timingSnapshot.estimatedWpm()
                + " mode=" + hybrid.strategyName
                + " text=" + hybrid.decodedText;

        System.out.println(summary);
        assertTrue(summary, baseline.result.completed());
        assertTrue(summary, adaptive.result.completed());
        assertTrue(summary, hybrid.result.completed());
        assertTrue(summary, hybrid.decoderCharacters >= 30);
        assertTrue(summary, hybrid.result.textTokenRecall() >= 0.66d);
    }

    private void assertHybridRecoversFast30Wpm() {
        String scenarioId = "user_noise_cq_30wpm_700hz";
        OfflineEvalBundle baseline = evaluateOfflineBundle(scenarioId, Strategy.BASELINE);
        OfflineEvalBundle adaptive = evaluateOfflineBundle(scenarioId, Strategy.ADAPTIVE);
        OfflineEvalBundle hybrid = evaluateOfflineBundle(scenarioId, Strategy.HYBRID);

        String summary = scenarioId
                + "\nA baseline recall=" + baseline.result.textTokenRecall()
                + " chars=" + baseline.decoderCharacters
                + " wpm=" + baseline.timingSnapshot.estimatedWpm()
                + " text=" + baseline.decodedText
                + "\nB adaptive recall=" + adaptive.result.textTokenRecall()
                + " chars=" + adaptive.decoderCharacters
                + " wpm=" + adaptive.timingSnapshot.estimatedWpm()
                + " text=" + adaptive.decodedText
                + "\nC hybrid recall=" + hybrid.result.textTokenRecall()
                + " chars=" + hybrid.decoderCharacters
                + " wpm=" + hybrid.timingSnapshot.estimatedWpm()
                + " mode=" + hybrid.strategyName
                + " text=" + hybrid.decodedText;

        System.out.println(summary);
        assertTrue(summary, baseline.result.completed());
        assertTrue(summary, adaptive.result.completed());
        assertTrue(summary, hybrid.result.completed());
        assertTrue(summary, baseline.decoderCharacters >= 24);
        assertTrue(summary, adaptive.decoderCharacters >= 24);
        assertTrue(summary, hybrid.decoderCharacters >= 30);
        assertTrue(summary, baseline.result.textTokenRecall() >= 0.80d);
        assertTrue(summary, adaptive.result.textTokenRecall() >= 0.95d);
        assertTrue(summary, hybrid.result.textTokenRecall() >= 0.99d);
        assertTrue(summary, hybrid.result.textTokenRecall() >= baseline.result.textTokenRecall());
        assertTrue(summary, countSubstring(baseline.decodedText, "CQ") >= 2);
        assertTrue(summary, countSubstring(adaptive.decodedText, "CQ") >= 3);
        assertTrue(summary, countSubstring(hybrid.decodedText, "CQ") >= 3);
        assertTrue(summary, countSubstring(baseline.decodedText, "BI9CLT") >= 2);
        assertTrue(summary, countSubstring(adaptive.decodedText, "BI9CLT") >= 3);
        assertTrue(summary, countSubstring(hybrid.decodedText, "BI9CLT") >= 3);
        assertTrue(summary, baseline.decodedText.contains("PSE"));
        assertTrue(summary, adaptive.decodedText.contains("PSE"));
        assertTrue(summary, hybrid.decodedText.contains("PSE"));
    }

    private void assertHybridKeepsSpeedSweepStable() {
        String scenarioId = "user_speed_sweep_vvv_700hz";
        OfflineEvalBundle baseline = evaluateOfflineBundle(scenarioId, Strategy.BASELINE);
        OfflineEvalBundle adaptive = evaluateOfflineBundle(scenarioId, Strategy.ADAPTIVE);
        OfflineEvalBundle hybrid = evaluateOfflineBundle(scenarioId, Strategy.HYBRID);

        String summary = scenarioId
                + "\nA baseline recall=" + baseline.result.textTokenRecall()
                + " chars=" + baseline.decoderCharacters
                + " wpm=" + baseline.timingSnapshot.estimatedWpm()
                + " text=" + baseline.decodedText
                + "\nB adaptive recall=" + adaptive.result.textTokenRecall()
                + " chars=" + adaptive.decoderCharacters
                + " wpm=" + adaptive.timingSnapshot.estimatedWpm()
                + " text=" + adaptive.decodedText
                + "\nC hybrid recall=" + hybrid.result.textTokenRecall()
                + " chars=" + hybrid.decoderCharacters
                + " wpm=" + hybrid.timingSnapshot.estimatedWpm()
                + " mode=" + hybrid.strategyName
                + " text=" + hybrid.decodedText;

        System.out.println(summary);
        assertTrue(summary, baseline.result.completed());
        assertTrue(summary, adaptive.result.completed());
        assertTrue(summary, hybrid.result.completed());
        assertTrue(summary, hybrid.decoderCharacters >= 22);
        assertTrue(summary, hybrid.result.textTokenRecall() >= 0.80d);
        assertTrue(summary, hybrid.result.textTokenRecall() >= adaptive.result.textTokenRecall());
    }

    private OfflineEvalBundle evaluateOfflineBundle(String scenarioId, Strategy strategy) {
        CwFixtureScenario scenario = findScenario(scenarioId);
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        CwSignalProcessor signalProcessor = new CwSignalProcessor();
        signalProcessor.setPreferredToneFrequencyHz(scenario.toneFrequencyHz());
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

        CwSignalSnapshot signalSnapshot = signalProcessor.snapshot();
        CwFixtureEvaluationResult result = CwFixtureEvaluator.evaluate(
                scenario,
                interpreter.snapshot(),
                qsoStateMachine.snapshot(),
                signalSnapshot,
                true
        );

        return new OfflineEvalBundle(
                result,
                timingAdapter.snapshot(),
                sanitize(decoder.snapshot().decodedText()),
                decoder.snapshot().totalCharacters(),
                timingAdapter.strategyName()
        );
    }

    private String sanitize(String text) {
        return text == null ? "" : text.replace('\u25A1', '?');
    }

    private int countSubstring(String text, String fragment) {
        if (text == null || text.isEmpty() || fragment == null || fragment.isEmpty()) {
            return 0;
        }
        int count = 0;
        int searchFrom = 0;
        while (searchFrom >= 0 && searchFrom < text.length()) {
            int foundAt = text.indexOf(fragment, searchFrom);
            if (foundAt < 0) {
                break;
            }
            count += 1;
            searchFrom = foundAt + fragment.length();
        }
        return count;
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
        private final CwTimingSnapshot timingSnapshot;
        private final String decodedText;
        private final int decoderCharacters;
        private final String strategyName;

        private OfflineEvalBundle(
                CwFixtureEvaluationResult result,
                CwTimingSnapshot timingSnapshot,
                String decodedText,
                int decoderCharacters,
                String strategyName
        ) {
            this.result = result;
            this.timingSnapshot = timingSnapshot;
            this.decodedText = decodedText;
            this.decoderCharacters = decoderCharacters;
            this.strategyName = strategyName;
        }
    }
}
