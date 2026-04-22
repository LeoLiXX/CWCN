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
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.bi9clt.cwcn.core.timing.CwTimingModel;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class CwFixturePipelineRegressionTest {
    @Test
    public void driftingHandoffReportSurvivesFrontEndAndYieldsUsefulDiagnostics() {
        OfflineEvalBundle bundle = evaluateOfflineBundle("drifting_handoff_report");
        CwFixtureEvaluationResult result = bundle.result;

        assertNotNull(result);
        String summary = renderDebugSummary(result, bundle);
        assertNotEquals(summary, "RUN", result.likelyBottleneckCode());
        assertNotEquals(summary, "SIG", result.likelyBottleneckCode());
        assertTrue(summary, result.textTokenRecall() >= 0.30d);
        assertTrue(summary, result.qsoSemanticScore() >= 0.50d);
    }

    @Test
    public void nearbyInterfererFixtureCurrentlyExposesFrontEndLimit() {
        OfflineEvalBundle bundle = evaluateOfflineBundle("nearby_interferer_directed_report");
        CwFixtureEvaluationResult result = bundle.result;

        assertNotNull(result);
        String summary = renderDebugSummary(result, bundle);
        assertTrue(summary, Math.abs(bundle.signalSnapshot.targetToneFrequencyHz() - 670) <= 20);
        assertTrue(summary, bundle.signalSnapshot.lastToneRmsAmplitude() > 0.0d);
        assertNotEquals(summary, "RUN", result.likelyBottleneckCode());
        assertNotEquals(summary, "SIG", result.likelyBottleneckCode());
        assertTrue(summary, result.textTokenRecall() >= 0.60d);
        assertTrue(summary, result.qsoSemanticScore() >= 1.0d);
    }

    @Test
    public void moderateInterfererFixtureStaysOutOfSignalFailureBucket() {
        OfflineEvalBundle bundle = evaluateOfflineBundle("moderate_interferer_directed_report");
        CwFixtureEvaluationResult result = bundle.result;

        assertNotNull(result);
        String summary = renderDebugSummary(result, bundle);
        assertNotEquals(summary, "RUN", result.likelyBottleneckCode());
        assertNotEquals(summary, "SIG", result.likelyBottleneckCode());
        assertTrue(summary, Math.abs(bundle.signalSnapshot.targetToneFrequencyHz() - 670) <= 20);
        assertTrue(summary, bundle.signalSnapshot.lastToneRmsAmplitude() > 0.0d);
        assertTrue(summary, result.textTokenRecall() >= 0.25d);
        assertTrue(summary, result.qsoSemanticScore() >= 0.50d);
    }

    @Test
    public void humanOpTimingFixtureDoesNotCollapseIntoFrontEndFailure() {
        OfflineEvalBundle bundle = evaluateOfflineBundle("human_op_timing_full_qso");
        CwFixtureEvaluationResult result = bundle.result;

        assertNotNull(result);
        String summary = renderDebugSummary(result, bundle);
        assertNotEquals(summary, "RUN", result.likelyBottleneckCode());
        assertNotEquals(summary, "SIG", result.likelyBottleneckCode());
        assertTrue(summary, result.textTokenRecall() >= 0.20d);
        assertTrue(summary, result.qsoSemanticScore() >= 1.0d);
    }

    @Test
    public void humanRaggedClarificationFixtureStillConvergesIntoDirectedReport() {
        OfflineEvalBundle bundle = evaluateOfflineBundle("human_ragged_clarification_report");
        CwFixtureEvaluationResult result = bundle.result;

        assertNotNull(result);
        String summary = renderDebugSummary(result, bundle);
        assertNotEquals(summary, "RUN", result.likelyBottleneckCode());
        assertNotEquals(summary, "SIG", result.likelyBottleneckCode());
        assertTrue(summary, result.textTokenRecall() >= 0.50d);
        assertTrue(summary, result.qsoSemanticScore() >= 1.0d);
        assertTrue(summary, result.hintRecall() >= 0.60d);
    }

    @Test
    public void humanCompactReportTailFixtureStillRecoversDirectedReportSemantics() {
        OfflineEvalBundle bundle = evaluateOfflineBundle("human_compact_report_tail_callsign");
        CwFixtureEvaluationResult result = bundle.result;

        assertNotNull(result);
        String summary = renderDebugSummary(result, bundle);
        assertNotEquals(summary, "RUN", result.likelyBottleneckCode());
        assertNotEquals(summary, "SIG", result.likelyBottleneckCode());
        assertTrue(summary, result.textTokenRecall() >= 0.50d);
        assertTrue(summary, result.qsoSemanticScore() >= 1.0d);
        assertTrue(summary, result.hintRecall() >= 1.0d);
    }

    @Test
    public void humanCompactDeClosingFixtureStillRecoversClosingFlow() {
        OfflineEvalBundle bundle = evaluateOfflineBundle("human_compact_de_closing_chain");
        CwFixtureEvaluationResult result = bundle.result;

        assertNotNull(result);
        String summary = renderDebugSummary(result, bundle);
        assertNotEquals(summary, "RUN", result.likelyBottleneckCode());
        assertNotEquals(summary, "SIG", result.likelyBottleneckCode());
        assertTrue(summary, result.textTokenRecall() >= 0.15d);
        assertTrue(summary, result.qsoSemanticScore() >= 1.0d);
        assertTrue(summary, result.hintRecall() >= 0.25d);
    }

    @Test
    public void humanCompactAckClosingFixtureStillRecoversSentReportAndCompletion() {
        OfflineEvalBundle bundle = evaluateOfflineBundle("human_compact_ack_closing_chain");
        CwFixtureEvaluationResult result = bundle.result;

        assertNotNull(result);
        String summary = renderDebugSummary(result, bundle);
        assertNotEquals(summary, "RUN", result.likelyBottleneckCode());
        assertNotEquals(summary, "SIG", result.likelyBottleneckCode());
        assertTrue(summary, result.textTokenRecall() >= 0.45d);
        assertTrue(summary, result.qsoSemanticScore() >= 1.0d);
        assertTrue(summary, result.hintRecall() >= 0.50d);
    }

    @Test
    public void fullyGluedAckClosingFixtureStillPreservesCompletedQsoSemantics() {
        OfflineEvalBundle bundle = evaluateOfflineBundle("fully_glued_ack_closing_chain");
        CwFixtureEvaluationResult result = bundle.result;

        assertNotNull(result);
        String summary = renderDebugSummary(result, bundle);
        assertNotEquals(summary, "RUN", result.likelyBottleneckCode());
        assertNotEquals(summary, "SIG", result.likelyBottleneckCode());
        assertTrue(summary, bundle.timingSnapshot.dotEstimateMs() >= 45L);
        assertTrue(summary, bundle.timingSnapshot.dotEstimateMs() <= 90L);
        assertTrue(summary, result.textTokenRecall() >= 0.40d);
        assertTrue(summary, result.qsoSemanticScore() >= 1.0d);
        assertTrue(summary, result.hintRecall() >= 0.60d);
    }

    @Test
    public void humanCompactFollowupFixtureUsesRememberedCallsignRoles() {
        OfflineEvalBundle bundle = evaluateOfflineBundle("human_compact_report_tail_followup");
        CwFixtureEvaluationResult result = bundle.result;

        assertNotNull(result);
        String summary = renderDebugSummary(result, bundle);
        assertNotEquals(summary, "RUN", result.likelyBottleneckCode());
        assertNotEquals(summary, "SIG", result.likelyBottleneckCode());
        assertTrue(summary, result.textTokenRecall() >= 0.45d);
        assertTrue(summary, result.qsoSemanticScore() >= 1.0d);
        assertTrue(summary, result.hintRecall() >= 0.75d);
    }

    private CwFixtureEvaluationResult evaluateOffline(String scenarioId) {
        return evaluateOfflineBundle(scenarioId).result;
    }

    private String renderDebugSummary(CwFixtureEvaluationResult result, OfflineEvalBundle bundle) {
        return result.renderSummary()
                + "\nSignal target: pref=" + bundle.signalSnapshot.preferredToneFrequencyHz()
                + "Hz, tracked=" + bundle.signalSnapshot.targetToneFrequencyHz()
                + "Hz, lock=" + bundle.signalSnapshot.targetToneLocked()
                + ", toneRms=" + Math.round(bundle.signalSnapshot.lastToneRmsAmplitude())
                + ", dom=" + Math.round(bundle.signalSnapshot.toneDominanceRatio() * 100.0d) + "%"
                + "\nSignal: threshold=" + bundle.signalSnapshot.currentThreshold()
                + ", release=" + bundle.signalSnapshot.releaseThreshold()
                + ", noise=" + bundle.signalSnapshot.noiseFloorEstimate()
                + ", signal=" + bundle.signalSnapshot.signalFloorEstimate()
                + ", toneOn=" + bundle.signalSnapshot.totalToneOnEvents()
                + ", toneOff=" + bundle.signalSnapshot.totalToneOffEvents()
                + "\nTiming: dot=" + bundle.timingSnapshot.dotEstimateMs()
                + ", dash=" + bundle.timingSnapshot.dashEstimateMs()
                + ", intra=" + bundle.timingSnapshot.intraGapEstimateMs()
                + ", wpm=" + bundle.timingSnapshot.estimatedWpm()
                + ", toneEvents=" + bundle.timingSnapshot.totalToneEvents()
                + ", gapEvents=" + bundle.timingSnapshot.totalGapEvents()
                + "\nDecoder: symbols=" + bundle.decoderSnapshot.totalSymbols()
                + ", chars=" + bundle.decoderSnapshot.totalCharacters()
                + ", text=" + bundle.decoderSnapshot.decodedText();
    }

    private OfflineEvalBundle evaluateOfflineBundle(String scenarioId) {
        CwFixtureScenario scenario = findScenario(scenarioId);
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        CwSignalProcessor signalProcessor = new CwSignalProcessor();
        signalProcessor.setPreferredToneFrequencyHz(scenario.toneFrequencyHz());
        CwTimingModel timingModel = new CwTimingModel();
        CwDecoder decoder = new CwDecoder();
        CwInterpreter interpreter = new CwInterpreter();
        QsoStateMachine qsoStateMachine = new QsoStateMachine();

        List<AudioFrame> frames = source.renderFramesForTesting(scenario);
        for (AudioFrame frame : frames) {
            List<CwToneEvent> toneEvents = signalProcessor.process(frame);
            for (CwToneEvent toneEvent : toneEvents) {
                List<CwTimingEvent> timingEvents = timingModel.process(toneEvent);
                for (CwTimingEvent timingEvent : timingEvents) {
                    List<CwDecodeEvent> decodeEvents = decoder.process(timingEvent);
                    for (CwDecodeEvent decodeEvent : decodeEvents) {
                        interpreter.process(decodeEvent);
                        qsoStateMachine.process(interpreter.snapshot(), decodeEvent.timestampMs());
                    }
                }
            }
        }

        return new OfflineEvalBundle(
                CwFixtureEvaluator.evaluate(
                        scenario,
                        interpreter.snapshot(),
                        qsoStateMachine.snapshot(),
                        true
                ),
                signalProcessor.snapshot(),
                timingModel.snapshot(),
                decoder.snapshot()
        );
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
