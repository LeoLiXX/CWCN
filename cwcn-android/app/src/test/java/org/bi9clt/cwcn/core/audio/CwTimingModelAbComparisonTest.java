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
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.bi9clt.cwcn.core.timing.CwTimingModel;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertTrue;

public final class CwTimingModelAbComparisonTest {
    @Test
    public void abComparisonCharacterizesFastUserCoverageFixtures() {
        // This A/B suite is a characterization probe, not a stricter gate than the
        // mainline raw-copy coverage tests. Keep it aligned with bench-useful raw recall.
        assertComparisonStaysHealthy("user_noise_cq_20wpm_700hz", 0.90d, 30, 0.90d, 30);
        assertComparisonStaysHealthy("user_noise_cq_25wpm_700hz", 0.95d, 30, 0.85d, 28);
        assertComparisonStaysFastCqSkeletonHealthy("user_noise_cq_30wpm_700hz", 0.80d, 24, 0.95d, 24);
        assertComparisonStaysSpeedSweepBenchObservable("user_speed_sweep_vvv_700hz", 22, 18);
        assertComparisonStaysBenchUsefulSpeedShiftContinuity("user_speed_shift_jv3vv_700hz", 40, 40);
    }

    private void assertComparisonStaysHealthy(
            String scenarioId,
            double minBaselineRecall,
            int minBaselineCharacters,
            double minAdaptiveRecall,
            int minAdaptiveCharacters
    ) {
        OfflineEvalBundle baseline = evaluateOfflineBundle(scenarioId, false);
        OfflineEvalBundle adaptive = evaluateOfflineBundle(scenarioId, true);
        String summary = scenarioId
                + "\nA recall=" + baseline.result.textTokenRecall()
                + " chars=" + baseline.decoderCharacters
                + " wpm=" + baseline.timingSnapshot.estimatedWpm()
                + " text=" + baseline.decodedText
                + "\nB recall=" + adaptive.result.textTokenRecall()
                + " chars=" + adaptive.decoderCharacters
                + " wpm=" + adaptive.timingSnapshot.estimatedWpm()
                + " text=" + adaptive.decodedText;

        System.out.println(summary);
        assertTrue(summary, baseline.result.completed());
        assertTrue(summary, adaptive.result.completed());
        assertTrue(summary, !"RUN".equals(baseline.result.likelyBottleneckCode()));
        assertTrue(summary, !"RUN".equals(adaptive.result.likelyBottleneckCode()));
        assertTrue(summary, baseline.result.textTokenRecall() >= minBaselineRecall);
        assertTrue(summary, baseline.decoderCharacters >= minBaselineCharacters);
        assertTrue(summary, adaptive.result.textTokenRecall() >= minAdaptiveRecall);
        assertTrue(summary, adaptive.decoderCharacters >= minAdaptiveCharacters);
    }

    private void assertComparisonStaysBenchObservable(
            String scenarioId,
            int minBaselineCharacters,
            int minAdaptiveCharacters
    ) {
        OfflineEvalBundle baseline = evaluateOfflineBundle(scenarioId, false);
        OfflineEvalBundle adaptive = evaluateOfflineBundle(scenarioId, true);
        String summary = scenarioId
                + "\nA recall=" + baseline.result.textTokenRecall()
                + " chars=" + baseline.decoderCharacters
                + " wpm=" + baseline.timingSnapshot.estimatedWpm()
                + " text=" + baseline.decodedText
                + "\nB recall=" + adaptive.result.textTokenRecall()
                + " chars=" + adaptive.decoderCharacters
                + " wpm=" + adaptive.timingSnapshot.estimatedWpm()
                + " text=" + adaptive.decodedText;

        System.out.println(summary);
        assertTrue(summary, baseline.result.completed());
        assertTrue(summary, adaptive.result.completed());
        assertTrue(summary, !"RUN".equals(baseline.result.likelyBottleneckCode()));
        assertTrue(summary, !"RUN".equals(adaptive.result.likelyBottleneckCode()));
        assertTrue(summary, baseline.decoderCharacters >= minBaselineCharacters);
        assertTrue(summary, adaptive.decoderCharacters >= minAdaptiveCharacters);
    }

    private void assertComparisonStaysFastCqSkeletonHealthy(
            String scenarioId,
            double minBaselineRecall,
            int minBaselineCharacters,
            double minAdaptiveRecall,
            int minAdaptiveCharacters
    ) {
        OfflineEvalBundle baseline = evaluateOfflineBundle(scenarioId, false);
        OfflineEvalBundle adaptive = evaluateOfflineBundle(scenarioId, true);
        String baselineText = sanitize(baseline.decodedText);
        String adaptiveText = sanitize(adaptive.decodedText);
        String summary = scenarioId
                + "\nA recall=" + baseline.result.textTokenRecall()
                + " chars=" + baseline.decoderCharacters
                + " wpm=" + baseline.timingSnapshot.estimatedWpm()
                + " text=" + baselineText
                + "\nB recall=" + adaptive.result.textTokenRecall()
                + " chars=" + adaptive.decoderCharacters
                + " wpm=" + adaptive.timingSnapshot.estimatedWpm()
                + " text=" + adaptiveText;

        System.out.println(summary);
        assertTrue(summary, baseline.result.completed());
        assertTrue(summary, adaptive.result.completed());
        assertTrue(summary, !"RUN".equals(baseline.result.likelyBottleneckCode()));
        assertTrue(summary, !"RUN".equals(adaptive.result.likelyBottleneckCode()));
        assertTrue(summary, baseline.result.textTokenRecall() >= minBaselineRecall);
        assertTrue(summary, baseline.decoderCharacters >= minBaselineCharacters);
        assertTrue(summary, adaptive.result.textTokenRecall() >= minAdaptiveRecall);
        assertTrue(summary, adaptive.decoderCharacters >= minAdaptiveCharacters);
        assertTrue(summary, countSubstring(baselineText, "CQ") >= 2);
        assertTrue(summary, countSubstring(adaptiveText, "CQ") >= 3);
        assertTrue(summary, baselineText.contains("DE"));
        assertTrue(summary, adaptiveText.contains("DE"));
        assertTrue(summary, countSubstring(baselineText, "BI9CLT") >= 2);
        assertTrue(summary, countSubstring(adaptiveText, "BI9CLT") >= 3);
        assertTrue(summary, baselineText.contains("PSE"));
        assertTrue(summary, adaptiveText.contains("PSE"));
        assertTrue(summary, baselineText.contains("K"));
        assertTrue(summary, adaptiveText.contains("K"));
    }

    private void assertComparisonStaysBenchObservableWithRecall(
            String scenarioId,
            int minBaselineCharacters,
            double minBaselineRecall,
            int minAdaptiveCharacters,
            double minAdaptiveRecall
    ) {
        OfflineEvalBundle baseline = evaluateOfflineBundle(scenarioId, false);
        OfflineEvalBundle adaptive = evaluateOfflineBundle(scenarioId, true);
        String summary = scenarioId
                + "\nA recall=" + baseline.result.textTokenRecall()
                + " chars=" + baseline.decoderCharacters
                + " wpm=" + baseline.timingSnapshot.estimatedWpm()
                + " text=" + baseline.decodedText
                + "\nB recall=" + adaptive.result.textTokenRecall()
                + " chars=" + adaptive.decoderCharacters
                + " wpm=" + adaptive.timingSnapshot.estimatedWpm()
                + " text=" + adaptive.decodedText;

        System.out.println(summary);
        assertTrue(summary, baseline.result.completed());
        assertTrue(summary, adaptive.result.completed());
        assertTrue(summary, !"RUN".equals(baseline.result.likelyBottleneckCode()));
        assertTrue(summary, !"RUN".equals(adaptive.result.likelyBottleneckCode()));
        assertTrue(summary, baseline.decoderCharacters >= minBaselineCharacters);
        assertTrue(summary, baseline.result.textTokenRecall() >= minBaselineRecall);
        assertTrue(summary, adaptive.decoderCharacters >= minAdaptiveCharacters);
        assertTrue(summary, adaptive.result.textTokenRecall() >= minAdaptiveRecall);
    }

    private void assertComparisonStaysSpeedSweepBenchObservable(
            String scenarioId,
            int minBaselineCharacters,
            int minAdaptiveCharacters
    ) {
        OfflineEvalBundle baseline = evaluateOfflineBundle(scenarioId, false);
        OfflineEvalBundle adaptive = evaluateOfflineBundle(scenarioId, true);
        String baselineText = sanitize(baseline.decodedText);
        String adaptiveText = sanitize(adaptive.decodedText);
        String summary = scenarioId
                + "\nA recall=" + baseline.result.textTokenRecall()
                + " chars=" + baseline.decoderCharacters
                + " wpm=" + baseline.timingSnapshot.estimatedWpm()
                + " text=" + baselineText
                + "\nB recall=" + adaptive.result.textTokenRecall()
                + " chars=" + adaptive.decoderCharacters
                + " wpm=" + adaptive.timingSnapshot.estimatedWpm()
                + " text=" + adaptiveText;

        System.out.println(summary);
        assertTrue(summary, baseline.result.completed());
        assertTrue(summary, adaptive.result.completed());
        assertTrue(summary, !"RUN".equals(baseline.result.likelyBottleneckCode()));
        assertTrue(summary, !"RUN".equals(adaptive.result.likelyBottleneckCode()));
        assertTrue(summary, baseline.decoderCharacters >= minBaselineCharacters);
        assertTrue(summary, adaptive.decoderCharacters >= minAdaptiveCharacters);
        assertTrue(summary, baseline.result.textTokenRecall() >= 0.60d);
        assertTrue(summary, adaptive.result.textTokenRecall() >= 0.45d);
        assertTrue(summary, countSubstring(baselineText, "VVV") >= 2);
        assertTrue(summary, countSubstring(adaptiveText, "VVV") >= 2);
        assertTrue(summary, baselineText.contains("BI9CXX"));
        assertTrue(summary, adaptiveText.contains("BI9CXX"));
        assertTrue(summary, baselineText.contains("SK"));
        assertTrue(summary, adaptiveText.contains("SK"));
        assertTrue(summary, baselineText.contains("XXX"));
        assertTrue(summary, adaptiveText.contains("XXX"));
    }

    private void assertComparisonStaysStrongSpeedShiftContinuity(
            String scenarioId,
            int minBaselineCharacters,
            double minBaselineRecall,
            int minAdaptiveCharacters,
            double minAdaptiveRecall
    ) {
        OfflineEvalBundle baseline = evaluateOfflineBundle(scenarioId, false);
        OfflineEvalBundle adaptive = evaluateOfflineBundle(scenarioId, true);
        String baselineText = sanitize(baseline.decodedText);
        String adaptiveText = sanitize(adaptive.decodedText);
        String summary = scenarioId
                + "\nA recall=" + baseline.result.textTokenRecall()
                + " chars=" + baseline.decoderCharacters
                + " wpm=" + baseline.timingSnapshot.estimatedWpm()
                + " text=" + baselineText
                + "\nB recall=" + adaptive.result.textTokenRecall()
                + " chars=" + adaptive.decoderCharacters
                + " wpm=" + adaptive.timingSnapshot.estimatedWpm()
                + " text=" + adaptiveText;

        System.out.println(summary);
        assertTrue(summary, baseline.result.completed());
        assertTrue(summary, adaptive.result.completed());
        assertTrue(summary, !"RUN".equals(baseline.result.likelyBottleneckCode()));
        assertTrue(summary, !"RUN".equals(adaptive.result.likelyBottleneckCode()));
        assertTrue(summary, baseline.decoderCharacters >= minBaselineCharacters);
        assertTrue(summary, baseline.result.textTokenRecall() >= minBaselineRecall);
        assertTrue(summary, adaptive.decoderCharacters >= minAdaptiveCharacters);
        assertTrue(summary, adaptive.result.textTokenRecall() >= minAdaptiveRecall);
        assertTrue(summary, countSubstring(baselineText, "JV3VV") >= 3);
        assertTrue(summary, countSubstring(adaptiveText, "JV3VV") >= 3);
        assertTrue(summary, countSubstring(baselineText, "DX") >= 2);
        assertTrue(summary, countSubstring(adaptiveText, "DX") >= 2);
        assertTrue(summary, countSubstring(baselineText, "CQ") >= 3);
        assertTrue(summary, countSubstring(adaptiveText, "CQ") >= 3);
        assertTrue(summary, baselineText.contains("PAGE"));
        assertTrue(summary, adaptiveText.contains("PAGE"));
    }

    private void assertComparisonStaysBenchUsefulSpeedShiftContinuity(
            String scenarioId,
            int minBaselineCharacters,
            int minAdaptiveCharacters
    ) {
        OfflineEvalBundle baseline = evaluateOfflineBundle(scenarioId, false);
        OfflineEvalBundle adaptive = evaluateOfflineBundle(scenarioId, true);
        String baselineText = sanitize(baseline.decodedText);
        String adaptiveText = sanitize(adaptive.decodedText);
        String summary = scenarioId
                + "\nA recall=" + baseline.result.textTokenRecall()
                + " chars=" + baseline.decoderCharacters
                + " wpm=" + baseline.timingSnapshot.estimatedWpm()
                + " text=" + baselineText
                + "\nB recall=" + adaptive.result.textTokenRecall()
                + " chars=" + adaptive.decoderCharacters
                + " wpm=" + adaptive.timingSnapshot.estimatedWpm()
                + " text=" + adaptiveText;

        System.out.println(summary);
        assertTrue(summary, baseline.result.completed());
        assertTrue(summary, adaptive.result.completed());
        assertTrue(summary, !"RUN".equals(baseline.result.likelyBottleneckCode()));
        assertTrue(summary, !"RUN".equals(adaptive.result.likelyBottleneckCode()));
        assertTrue(summary, baseline.decoderCharacters >= minBaselineCharacters);
        assertTrue(summary, adaptive.decoderCharacters >= minAdaptiveCharacters);
        assertTrue(summary, baseline.result.textTokenRecall() >= 0.80d);
        assertTrue(summary, adaptive.result.textTokenRecall() >= 0.95d);
        assertTrue(summary, countSubstring(baselineText, "JV3VV") >= 3);
        assertTrue(summary, countSubstring(adaptiveText, "JV3VV") >= 3);
        assertTrue(summary, countSubstring(baselineText, "DX") >= 2);
        assertTrue(summary, countSubstring(adaptiveText, "DX") >= 2);
        assertTrue(summary, countSubstring(baselineText, "CQ") >= 2);
        assertTrue(summary, countSubstring(adaptiveText, "CQ") >= 3);
        assertTrue(summary, baselineText.contains("PAGE"));
        assertTrue(summary, adaptiveText.contains("PAGE"));
    }

    private OfflineEvalBundle evaluateOfflineBundle(String scenarioId, boolean adaptiveTiming) {
        CwFixtureScenario scenario = findScenario(scenarioId);
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        CwSignalProcessor signalProcessor = new CwSignalProcessor();
        signalProcessor.setPreferredToneFrequencyHz(scenario.toneFrequencyHz());
        TimingAdapter timingAdapter = adaptiveTiming
                ? new AdaptiveTimingAdapter()
                : new BaselineTimingAdapter();
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
        CwTimingSnapshot timingSnapshot = timingAdapter.snapshot();
        CwFixtureEvaluationResult result = CwFixtureEvaluator.evaluate(
                scenario,
                interpreter.snapshot(),
                qsoStateMachine.snapshot(),
                signalSnapshot,
                true
        );

        return new OfflineEvalBundle(
                result,
                timingSnapshot,
                decoder.snapshot().decodedText() == null ? "" : decoder.snapshot().decodedText(),
                decoder.snapshot().totalCharacters()
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

    private CwFixtureScenario findScenario(String scenarioId) {
        for (CwFixtureScenario scenario : CwFixtureLibrary.scenarios()) {
            if (scenario.id().equals(scenarioId)) {
                return scenario;
            }
        }
        throw new IllegalArgumentException("Unknown scenario: " + scenarioId);
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

    private static final class OfflineEvalBundle {
        private final CwFixtureEvaluationResult result;
        private final CwTimingSnapshot timingSnapshot;
        private final String decodedText;
        private final int decoderCharacters;

        private OfflineEvalBundle(
                CwFixtureEvaluationResult result,
                CwTimingSnapshot timingSnapshot,
                String decodedText,
                int decoderCharacters
        ) {
            this.result = result;
            this.timingSnapshot = timingSnapshot;
            this.decodedText = decodedText;
            this.decoderCharacters = decoderCharacters;
        }
    }
}
