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

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public final class CwFastPreferredOffsetMatrixRegressionTest {
    private static final int[] PREFERRED_MATRIX = new int[]{450, 500, 650, 800};

    @Test
    public void hybridPathKeepsFastAndNoisyFixturesObservableAcrossPreferredOffsets() {
        List<MatrixExpectation> expectations = new ArrayList<>();

        addExpectations(expectations, "user_noise_cq_20wpm_700hz", PREFERRED_MATRIX, 0.40d, 14, true, true);
        addExpectations(expectations, "user_noise_cq_25wpm_700hz", PREFERRED_MATRIX, 0.35d, 12, true, true);
        addExpectations(expectations, "user_noise_cq_30wpm_700hz", PREFERRED_MATRIX, 0.95d, 24, true, true);
        addExpectations(expectations, "user_speed_sweep_vvv_700hz", PREFERRED_MATRIX, 0.45d, 18, false, false);

        StringBuilder summary = new StringBuilder();
        for (MatrixExpectation expectation : expectations) {
            OfflineEvalBundle bundle = evaluateOfflineBundle(expectation.scenarioId, expectation.preferredToneHz);
            String decodedText = sanitizeText(bundle.decoderSnapshot.decodedText());

            summary.append(expectation.scenarioId)
                    .append(" pref=").append(expectation.preferredToneHz)
                    .append(" tracked=").append(bundle.signalSnapshot.targetToneFrequencyHz())
                    .append(" AQ=").append(bundle.signalSnapshot.acquisitionWinnerFrequencyHz())
                    .append(" AD=").append(bundle.signalSnapshot.finalAdoptedFrequencyHz())
                    .append(" ADsrc=").append(bundle.signalSnapshot.finalAdoptedSource())
                    .append(" recall=").append(bundle.result.textTokenRecall())
                    .append(" chars=").append(bundle.decoderSnapshot.totalCharacters())
                    .append(" wpm=").append(bundle.timingSnapshot.estimatedWpm())
                    .append(" text=").append(decodedText)
                    .append('\n');

            assertTrue(summary.toString(), bundle.result.completed());
            assertNotEquals(summary.toString(), "RUN", bundle.result.likelyBottleneckCode());
            assertTrue(summary.toString(), bundle.decoderSnapshot.totalCharacters() >= expectation.minCharacters);
            assertTrue(summary.toString(), bundle.result.textTokenRecall() >= expectation.minRecall);
            assertTrue(summary.toString(), bundle.signalSnapshot.totalToneOnEvents() >= 8);
            assertTrue(summary.toString(), bundle.signalSnapshot.totalToneOffEvents() >= 8);

            if (expectation.requiresCq) {
                assertTrue(summary.toString(), decodedText.contains("CQ"));
            }
            if (expectation.requiresCallsignCore) {
                assertTrue(summary.toString(), containsCallsignCore(decodedText));
            }
            if ("user_noise_cq_30wpm_700hz".equals(expectation.scenarioId)) {
                assertTrue(summary.toString(), countSubstring(decodedText, "CQ") >= 3);
                assertTrue(summary.toString(), decodedText.contains("DE"));
                assertTrue(summary.toString(), countSubstring(decodedText, "BI9CLT") >= 3);
                assertTrue(summary.toString(), decodedText.contains("PSE"));
                assertTrue(summary.toString(), decodedText.contains("K"));
            }
            if ("user_speed_sweep_vvv_700hz".equals(expectation.scenarioId)) {
                assertTrue(summary.toString(), countCharacter(decodedText, 'V') >= 3);
                assertTrue(summary.toString(), decodedText.contains("BI9XXX") || decodedText.contains("9XXX"));
            }
        }
    }

    private void addExpectations(
            List<MatrixExpectation> expectations,
            String scenarioId,
            int[] preferredMatrix,
            double minRecall,
            int minCharacters,
            boolean requiresCq,
            boolean requiresCallsignCore
    ) {
        for (int preferredToneHz : preferredMatrix) {
            expectations.add(new MatrixExpectation(
                    scenarioId,
                    preferredToneHz,
                    minRecall,
                    minCharacters,
                    requiresCq,
                    requiresCallsignCore
            ));
        }
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
        if (text == null) {
            return "";
        }
        return text.replace('\u25A1', '?');
    }

    private boolean containsCallsignCore(String decodedText) {
        if (decodedText == null || decodedText.isEmpty()) {
            return false;
        }
        return decodedText.contains("BI9CLT")
                || decodedText.contains("I9CLT")
                || decodedText.contains("9CLT")
                || decodedText.contains("CLT");
    }

    private int countCharacter(String text, char target) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int index = 0; index < text.length(); index++) {
            if (Character.toUpperCase(text.charAt(index)) == Character.toUpperCase(target)) {
                count += 1;
            }
        }
        return count;
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

    private static final class MatrixExpectation {
        private final String scenarioId;
        private final int preferredToneHz;
        private final double minRecall;
        private final int minCharacters;
        private final boolean requiresCq;
        private final boolean requiresCallsignCore;

        private MatrixExpectation(
                String scenarioId,
                int preferredToneHz,
                double minRecall,
                int minCharacters,
                boolean requiresCq,
                boolean requiresCallsignCore
        ) {
            this.scenarioId = scenarioId;
            this.preferredToneHz = preferredToneHz;
            this.minRecall = minRecall;
            this.minCharacters = minCharacters;
            this.requiresCq = requiresCq;
            this.requiresCallsignCore = requiresCallsignCore;
        }
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
