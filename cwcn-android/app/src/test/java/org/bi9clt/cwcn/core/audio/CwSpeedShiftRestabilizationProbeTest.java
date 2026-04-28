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

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

public final class CwSpeedShiftRestabilizationProbeTest {
    @Test
    public void speedShiftProbeShowsSlowerDotEstimateInSecondHalf() {
        probe(Strategy.BASELINE, false);
        probe(Strategy.ADAPTIVE, false);
        probe(Strategy.HYBRID, true);
    }

    private void probe(Strategy strategy, boolean requireRestabilization) {
        ProbeBundle bundle = evaluateProbeBundle("user_speed_shift_jv3vv_700hz", strategy);
        double earlyDotMs = averageDotEstimateMsByTimestamp(bundle.timingEvents, 0.10d, 0.40d);
        double lateDotMs = averageDotEstimateMsByTimestamp(bundle.timingEvents, 0.60d, 0.95d);
        String summary = "strategy=" + strategy
                + "\nresult=" + bundle.result.renderSummary()
                + "\ntracked=" + bundle.signalSnapshot.targetToneFrequencyHz()
                + " wpm=" + bundle.timingSnapshot.estimatedWpm()
                + " chars=" + bundle.decoderCharacters
                + " recall=" + bundle.result.textTokenRecall()
                + "\nearlyDotMs=" + earlyDotMs
                + " lateDotMs=" + lateDotMs
                + " timingEvents=" + bundle.timingEvents.size()
                + " text=" + bundle.decodedText;

        System.out.println(summary);
        assertTrue(summary, bundle.result.completed());
        assertTrue(summary, bundle.timingEvents.size() >= 40);
        assertTrue(summary, bundle.decoderCharacters >= 24);
        assertTrue(summary, bundle.result.textTokenRecall() >= 0.45d);
        if (requireRestabilization) {
            assertTrue(summary, lateDotMs >= earlyDotMs + 4.0d);
            assertTrue(summary, lateDotMs >= earlyDotMs * 1.08d);
        }
    }

    private ProbeBundle evaluateProbeBundle(String scenarioId, Strategy strategy) {
        CwFixtureScenario scenario = findScenario(scenarioId);
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        CwSignalProcessor signalProcessor = new CwSignalProcessor();
        signalProcessor.setPreferredToneFrequencyHz(scenario.toneFrequencyHz());
        TimingAdapter timingAdapter = createTimingAdapter(strategy);
        CwDecoder decoder = new CwDecoder();
        CwInterpreter interpreter = new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);
        QsoStateMachine qsoStateMachine = new QsoStateMachine();
        ArrayList<CwTimingEvent> timingEvents = new ArrayList<>();

        List<AudioFrame> frames = source.renderFramesForTesting(scenario);
        AudioFrame lastFrame = null;
        for (AudioFrame frame : frames) {
            lastFrame = frame;
            List<CwToneEvent> toneEvents = signalProcessor.process(frame);
            for (CwToneEvent toneEvent : toneEvents) {
                List<CwTimingEvent> emitted = timingAdapter.process(toneEvent);
                timingEvents.addAll(emitted);
                drainTimingEvents(emitted, decoder, interpreter, qsoStateMachine);
            }
        }

        if (lastFrame != null) {
            long frameDurationMs = Math.max(
                    1L,
                    Math.round(lastFrame.sampleCount() * 1000.0d / lastFrame.sampleRateHz())
            );
            long flushTimestampMs = lastFrame.capturedAtMs() + frameDurationMs;
            List<CwTimingEvent> flushed = timingAdapter.flushPendingGap(flushTimestampMs);
            timingEvents.addAll(flushed);
            drainTimingEvents(flushed, decoder, interpreter, qsoStateMachine);
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

        return new ProbeBundle(
                result,
                signalSnapshot,
                timingAdapter.snapshot(),
                sanitizeText(decoder.snapshot().decodedText()),
                decoder.snapshot().totalCharacters(),
                timingEvents
        );
    }

    private double averageDotEstimateMsByTimestamp(List<CwTimingEvent> timingEvents, double startFraction, double endFraction) {
        if (timingEvents == null || timingEvents.isEmpty()) {
            return 0.0d;
        }
        long firstTimestampMs = timingEvents.get(0).timestampMs();
        long lastTimestampMs = timingEvents.get(timingEvents.size() - 1).timestampMs();
        long spanMs = Math.max(1L, lastTimestampMs - firstTimestampMs);
        long startTimestampMs = firstTimestampMs + Math.round(spanMs * startFraction);
        long endTimestampMs = firstTimestampMs + Math.round(spanMs * endFraction);
        double sum = 0.0d;
        int count = 0;
        for (CwTimingEvent timingEvent : timingEvents) {
            if (timingEvent == null || timingEvent.dotEstimateMs() <= 0L) {
                continue;
            }
            if (timingEvent.timestampMs() < startTimestampMs || timingEvent.timestampMs() > endTimestampMs) {
                continue;
            }
            sum += timingEvent.dotEstimateMs();
            count += 1;
        }
        return count == 0 ? 0.0d : sum / count;
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

    private String sanitizeText(String text) {
        return text == null ? "" : text.replace('\u25A1', '?');
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
    }

    private static final class ProbeBundle {
        private final CwFixtureEvaluationResult result;
        private final CwSignalSnapshot signalSnapshot;
        private final CwTimingSnapshot timingSnapshot;
        private final String decodedText;
        private final int decoderCharacters;
        private final List<CwTimingEvent> timingEvents;

        private ProbeBundle(
                CwFixtureEvaluationResult result,
                CwSignalSnapshot signalSnapshot,
                CwTimingSnapshot timingSnapshot,
                String decodedText,
                int decoderCharacters,
                List<CwTimingEvent> timingEvents
        ) {
            this.result = result;
            this.signalSnapshot = signalSnapshot;
            this.timingSnapshot = timingSnapshot;
            this.decodedText = decodedText;
            this.decoderCharacters = decoderCharacters;
            this.timingEvents = timingEvents;
        }
    }
}
