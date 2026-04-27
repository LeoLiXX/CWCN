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

public final class CwUserCaptureCoverageTest {
    @Test
    public void userRecordedStyleCoverageCase_range10wpm700hz_staysDecodable() {
        assertCoverageCase("user_range_cq_10wpm_700hz", 700, 0.50d, true, true, 18);
    }

    @Test
    public void userRecordedStyleCoverageCase_range15wpm700hz_staysDecodable() {
        assertCoverageCase("user_range_cq_15wpm_700hz", 700, 0.60d, true, true, 18);
    }

    @Test
    public void userRecordedStyleCoverageCase_qsb18wpm700hz_staysDecodable() {
        assertCoverageCase("user_qsb_cq_18wpm_700hz", 700, 0.30d, true, true, 8);
    }

    @Test
    public void userRecordedStyleCoverageCase_qsb18wpm600hz_staysDecodable() {
        assertCoverageCase("user_qsb_cq_18wpm_600hz", 600, 0.30d, true, true, 8);
    }

    @Test
    public void userRecordedStyleCoverageCase_qsb18wpm800hz_staysDecodable() {
        assertCoverageCase("user_qsb_cq_18wpm_800hz", 800, 0.30d, true, true, 8);
    }

    @Test
    public void userRecordedStyleCoverageCase_lightQsb18wpm700hz_staysStronglyDecodable() {
        assertCoverageCase("user_light_qsb_cq_18wpm_700hz", 700, 0.75d, true, true, 18);
    }

    @Test
    public void userRecordedStyleCoverageCase_lightQsb18wpm600hz_staysStronglyDecodable() {
        assertCoverageCase("user_light_qsb_cq_18wpm_600hz", 600, 0.75d, true, true, 18);
    }

    @Test
    public void userRecordedStyleCoverageCase_lightQsb18wpm800hz_staysStronglyDecodable() {
        assertCoverageCase("user_light_qsb_cq_18wpm_800hz", 800, 0.75d, true, true, 18);
    }

    @Test
    public void usbAudioCoverageCase_nominal18wpm700hz_staysStronglyDecodable() {
        assertCoverageCase("usb_nominal_cq_18wpm_700hz", 700, 0.85d, true, true, 22);
    }

    @Test
    public void usbAudioCoverageCase_lowLevel18wpm700hz_staysStronglyDecodable() {
        assertCoverageCase("usb_low_level_cq_18wpm_700hz", 700, 0.75d, true, true, 20);
    }

    @Test
    public void usbAudioCoverageCase_hotLevel18wpm700hz_staysStronglyDecodable() {
        assertCoverageCase("usb_hot_level_cq_18wpm_700hz", 700, 0.75d, true, true, 20);
    }

    @Test
    public void usbAudioCoverageCase_offset20wpm600hz_staysDecodable() {
        assertCoverageCase("usb_freq_offset_cq_20wpm_600hz", 600, 0.60d, true, true, 18);
    }

    @Test
    public void usbAudioCoverageCase_offset20wpm800hz_staysDecodable() {
        assertCoverageCase("usb_freq_offset_cq_20wpm_800hz", 800, 0.60d, true, true, 18);
    }

    @Test
    public void usbAudioCoverageCase_nearbyTone18wpm700hz_staysDecodable() {
        assertCoverageCase("usb_nearby_tone_cq_18wpm_700hz", 700, 0.55d, true, true, 16);
    }

    @Test
    public void usbAudioCoverageCase_hum18wpm700hz_staysStronglyDecodable() {
        assertCoverageCase("usb_hum_cq_18wpm_700hz", 700, 0.70d, true, true, 18);
    }

    @Test
    public void usbAudioCoverageCase_softClip18wpm700hz_staysMostlyDecodable() {
        OfflineEvalBundle bundle = evaluateOfflineBundle("usb_soft_clip_cq_18wpm_700hz");
        CwFixtureEvaluationResult result = bundle.result;
        String summary = renderDebugSummary(result, bundle);
        String decodedText = bundle.decoderSnapshot.decodedText() == null
                ? ""
                : bundle.decoderSnapshot.decodedText().replace('\u25A1', '?');

        assertNotNull(summary, result);
        assertNotEquals(summary, "RUN", result.likelyBottleneckCode());
        assertTrue(summary, Math.abs(bundle.signalSnapshot.targetToneFrequencyHz() - 700) <= 40);
        assertTrue(summary, result.textTokenRecall() >= 0.45d);
        assertTrue(summary, bundle.decoderSnapshot.totalCharacters() >= 16);
        assertTrue(summary, decodedText.contains("CQ"));
        assertTrue(summary, containsCallsignCore(decodedText));
        assertTrue(summary, bundle.clippedSampleRatio > 0.0d);
    }

    @Test
    public void userRecordedStyleCoverageCase_noise30wpm700hz_staysTrackedAsFastBoundaryCase() {
        assertCoverageCase("user_noise_cq_30wpm_700hz", 700, 0.0d, false, false, 6);
    }

    @Test
    public void userRecordedStyleCoverageCase_noise25wpm700hz_staysDecodable() {
        assertCoverageCase("user_noise_cq_25wpm_700hz", 700, 0.30d, true, true, 8);
    }

    @Test
    public void userRecordedStyleCoverageCase_noise20wpm700hz_staysDecodable() {
        assertCoverageCase("user_noise_cq_20wpm_700hz", 700, 0.30d, true, true, 8);
    }

    @Test
    public void userRecordedStyleCoverageCase_operatingSweetSpotCentersOn18to20wpm() {
        OfflineEvalBundle lowBundle = evaluateOfflineBundle("user_range_cq_10wpm_700hz");
        OfflineEvalBundle midBundle = evaluateOfflineBundle("user_range_cq_15wpm_700hz");
        OfflineEvalBundle sweetBundle = evaluateOfflineBundle("user_light_qsb_cq_18wpm_700hz");
        OfflineEvalBundle upperSweetBundle = evaluateOfflineBundle("user_noise_cq_20wpm_700hz");
        OfflineEvalBundle upperBundle = evaluateOfflineBundle("user_noise_cq_25wpm_700hz");

        String summary = "10WPM=" + lowBundle.result.renderSummary()
                + "\n15WPM=" + midBundle.result.renderSummary()
                + "\n18WPM=" + sweetBundle.result.renderSummary()
                + "\n20WPM=" + upperSweetBundle.result.renderSummary()
                + "\n25WPM=" + upperBundle.result.renderSummary();

        assertTrue(summary, sweetBundle.result.textTokenRecall() >= lowBundle.result.textTokenRecall());
        assertTrue(summary, sweetBundle.result.textTokenRecall() >= midBundle.result.textTokenRecall());
        assertTrue(summary, sweetBundle.result.textTokenRecall() >= upperBundle.result.textTokenRecall());
        assertTrue(summary, upperSweetBundle.result.textTokenRecall() >= upperBundle.result.textTokenRecall());
        assertTrue(summary, sweetBundle.result.textTokenRecall() >= 0.75d);
        assertTrue(summary, upperSweetBundle.result.textTokenRecall() >= 0.30d);
    }

    @Test
    public void usbAudioSweetSpotCentersOn18to20wpm() {
        OfflineEvalBundle nominal18 = evaluateOfflineBundle("usb_nominal_cq_18wpm_700hz");
        OfflineEvalBundle low15 = evaluateOfflineBundle("user_range_cq_15wpm_700hz");
        OfflineEvalBundle nominal20 = evaluateOfflineBundle("usb_freq_offset_cq_20wpm_600hz");
        OfflineEvalBundle upper25 = evaluateOfflineBundle("user_noise_cq_25wpm_700hz");

        String summary = "15WPM=" + low15.result.renderSummary()
                + "\n18WPM USB=" + nominal18.result.renderSummary()
                + "\n20WPM USB=" + nominal20.result.renderSummary()
                + "\n25WPM=" + upper25.result.renderSummary();

        assertTrue(summary, nominal18.result.textTokenRecall() >= low15.result.textTokenRecall());
        assertTrue(summary, nominal18.result.textTokenRecall() >= upper25.result.textTokenRecall());
        assertTrue(summary, nominal20.result.textTokenRecall() >= upper25.result.textTokenRecall());
        assertTrue(summary, nominal18.result.textTokenRecall() >= 0.85d);
        assertTrue(summary, nominal20.result.textTokenRecall() >= 0.60d);
    }

    @Test
    public void userRecordedStyleCoverageCase_speedSweep700hz_staysBenchUseful() {
        OfflineEvalBundle bundle = evaluateOfflineBundle("user_speed_sweep_vvv_700hz");
        CwFixtureEvaluationResult result = bundle.result;
        String summary = renderDebugSummary(result, bundle);
        String decodedText = bundle.decoderSnapshot.decodedText() == null
                ? ""
                : bundle.decoderSnapshot.decodedText().replace('\u25A1', '?');

        assertNotNull(summary, result);
        assertNotEquals(summary, "RUN", result.likelyBottleneckCode());
        assertTrue(summary, Math.abs(bundle.signalSnapshot.targetToneFrequencyHz() - 700) <= 30);
        assertTrue(summary, bundle.signalSnapshot.peakToneRmsAmplitude() >= 2500.0d);
        assertTrue(summary, bundle.signalSnapshot.totalToneOnEvents() >= 12);
        assertTrue(summary, bundle.signalSnapshot.totalToneOffEvents() >= 12);
        assertTrue(summary, bundle.decoderSnapshot.totalCharacters() >= 12);
        assertTrue(summary, countCharacter(decodedText, 'V') >= 4);
        assertTrue(summary, decodedText.contains("DE") || decodedText.contains("SE"));
        assertTrue(summary, containsNearCallsignCore(decodedText));
        assertTrue(summary, decodedText.contains("BI9XXX") || decodedText.contains("I9XXX"));
    }

    @Test
    public void userRecordedStyleCoverageCase_toneSweep18wpm_remainsObservableWithoutFrontEndCollapse() {
        OfflineEvalBundle bundle = evaluateOfflineBundle("user_tone_sweep_vvv_18wpm");
        CwFixtureEvaluationResult result = bundle.result;
        String summary = renderDebugSummary(result, bundle);
        String decodedText = bundle.decoderSnapshot.decodedText() == null
                ? ""
                : bundle.decoderSnapshot.decodedText().replace('\u25A1', '?');
        String acquisitionSource = bundle.signalSnapshot.acquisitionWinnerSource();
        String adoptedSource = bundle.signalSnapshot.finalAdoptedSource();

        assertNotNull(summary, result);
        assertNotEquals(summary, "RUN", result.likelyBottleneckCode());
        assertTrue(summary, bundle.signalSnapshot.peakToneRmsAmplitude() >= 2500.0d);
        assertTrue(summary, bundle.signalSnapshot.totalToneOnEvents() >= 12);
        assertTrue(summary, bundle.signalSnapshot.totalToneOffEvents() >= 12);
        assertTrue(summary, bundle.decoderSnapshot.totalCharacters() >= 12);
        assertTrue(summary, countCharacter(decodedText, 'V') >= 4);
        assertTrue(summary, decodedText.contains("DE"));
        assertTrue(summary, containsNearCallsignCore(decodedText));
        assertTrue(summary, decodedText.contains("BI9XXX") || decodedText.contains("I9XXX"));
        assertTrue(summary, decodedText.contains("BI9CXX") || decodedText.contains("I9CXX"));
        assertTrue(summary, bundle.signalSnapshot.acquisitionWinnerFrequencyHz() > 0
                || bundle.signalSnapshot.finalAdoptedFrequencyHz() > 0
                || bundle.signalSnapshot.targetToneFrequencyHz() > 0);
        assertTrue(summary, !"NONE".equals(acquisitionSource) || !"NONE".equals(adoptedSource));
    }

    private void assertCoverageCase(
            String scenarioId,
            int expectedToneHz,
            double minTextTokenRecall,
            boolean requiresCq,
            boolean requiresCallsignCore,
            int minDecodedCharacters
    ) {
        OfflineEvalBundle bundle = evaluateOfflineBundle(scenarioId);
        CwFixtureEvaluationResult result = bundle.result;
        String summary = renderDebugSummary(result, bundle);
        String decodedText = bundle.decoderSnapshot.decodedText() == null
                ? ""
                : bundle.decoderSnapshot.decodedText().replace('\u25A1', '?');

        assertNotNull(summary, result);
        assertNotEquals(summary, "RUN", result.likelyBottleneckCode());
        assertTrue(summary, Math.abs(bundle.signalSnapshot.targetToneFrequencyHz() - expectedToneHz) <= 40);
        assertTrue(summary, bundle.signalSnapshot.peakToneRmsAmplitude() >= 2500.0d);
        assertTrue(summary, bundle.signalSnapshot.totalToneOnEvents() >= 8);
        assertTrue(summary, bundle.signalSnapshot.totalToneOffEvents() >= 8);
        assertTrue(summary, result.textTokenRecall() >= minTextTokenRecall);
        assertTrue(summary, bundle.decoderSnapshot.totalCharacters() >= minDecodedCharacters);
        if (requiresCq) {
            assertTrue(summary, decodedText.contains("CQ"));
        }
        if (requiresCallsignCore) {
            assertTrue(summary, containsCallsignCore(decodedText));
        }
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
        AudioFrame lastFrame = null;
        for (AudioFrame frame : frames) {
            lastFrame = frame;
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

        if (lastFrame != null) {
            long frameDurationMs = Math.max(
                    1L,
                    Math.round(lastFrame.sampleCount() * 1000.0d / lastFrame.sampleRateHz())
            );
            long flushTimestampMs = lastFrame.capturedAtMs() + frameDurationMs;
            List<CwTimingEvent> timingEvents = timingModel.flushPendingGap(flushTimestampMs);
            for (CwTimingEvent timingEvent : timingEvents) {
                List<CwDecodeEvent> decodeEvents = decoder.process(timingEvent);
                for (CwDecodeEvent decodeEvent : decodeEvents) {
                    interpreter.process(decodeEvent);
                    qsoStateMachine.process(interpreter.snapshot(), decodeEvent.timestampMs());
                }
            }
        }

        CwFixtureEvaluationResult result = CwFixtureEvaluator.evaluate(
                scenario,
                interpreter.snapshot(),
                qsoStateMachine.snapshot(),
                signalProcessor.snapshot(),
                true
        );

        return new OfflineEvalBundle(
                scenario,
                result,
                signalProcessor.snapshot(),
                timingModel.snapshot(),
                decoder.snapshot(),
                computeClippedSampleRatio(frames)
        );
    }

    private double computeClippedSampleRatio(List<AudioFrame> frames) {
        if (frames == null || frames.isEmpty()) {
            return 0.0d;
        }
        long clippedSamples = 0L;
        long totalSamples = 0L;
        for (AudioFrame frame : frames) {
            if (frame == null) {
                continue;
            }
            clippedSamples += frame.clippedSampleCount();
            totalSamples += frame.sampleCount();
        }
        if (totalSamples <= 0L) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, clippedSamples / (double) totalSamples));
    }

    private CwFixtureScenario findScenario(String scenarioId) {
        for (CwFixtureScenario scenario : CwFixtureLibrary.scenarios()) {
            if (scenario.id().equals(scenarioId)) {
                return scenario;
            }
        }
        throw new IllegalArgumentException("Unknown scenario: " + scenarioId);
    }

    private String renderDebugSummary(CwFixtureEvaluationResult result, OfflineEvalBundle bundle) {
        return result.renderSummary()
                + "\nSignal target: pref=" + bundle.signalSnapshot.preferredToneFrequencyHz()
                + "Hz, tracked=" + bundle.signalSnapshot.targetToneFrequencyHz()
                + "Hz, lock=" + bundle.signalSnapshot.targetToneLocked()
                + ", toneRms=" + Math.round(bundle.signalSnapshot.lastToneRmsAmplitude())
                + ", peakToneRms=" + Math.round(bundle.signalSnapshot.peakToneRmsAmplitude())
                + ", residual=" + Math.round(bundle.signalSnapshot.lastWidebandResidualRmsAmplitude())
                + ", dom=" + Math.round(bundle.signalSnapshot.toneDominanceRatio() * 100.0d) + "%"
                + ", iso=" + Math.round(bundle.signalSnapshot.narrowbandIsolationRatio() * 100.0d) + "%"
                + ", peakIso=" + Math.round(bundle.signalSnapshot.peakNarrowbandIsolationRatio() * 100.0d) + "%"
                + ", lockCov=" + Math.round(bundle.signalSnapshot.lockedFrameRatio() * 100.0d) + "%"
                + "\nSignal: threshold=" + bundle.signalSnapshot.currentThreshold()
                + ", release=" + bundle.signalSnapshot.releaseThreshold()
                + ", noise=" + bundle.signalSnapshot.noiseFloorEstimate()
                + ", signal=" + bundle.signalSnapshot.signalFloorEstimate()
                + ", bestLockRun=" + bundle.signalSnapshot.maxConsecutiveLockedFrames()
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
                + ", text=" + bundle.decoderSnapshot.decodedText()
                + "\nAudio: clipped="
                + Math.round(bundle.clippedSampleRatio * 100.0d)
                + "%";
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

    private boolean containsNearCallsignCore(String decodedText) {
        if (decodedText == null || decodedText.isEmpty()) {
            return false;
        }
        return decodedText.contains("BI9")
                || decodedText.contains("I9")
                || decodedText.contains("9X")
                || decodedText.contains("9C")
                || decodedText.contains("XXX")
                || decodedText.contains("CXX");
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

    private static final class OfflineEvalBundle {
        private final CwFixtureScenario scenario;
        private final CwFixtureEvaluationResult result;
        private final CwSignalSnapshot signalSnapshot;
        private final CwTimingSnapshot timingSnapshot;
        private final CwDecoderSnapshot decoderSnapshot;
        private final double clippedSampleRatio;

        private OfflineEvalBundle(
                CwFixtureScenario scenario,
                CwFixtureEvaluationResult result,
                CwSignalSnapshot signalSnapshot,
                CwTimingSnapshot timingSnapshot,
                CwDecoderSnapshot decoderSnapshot,
                double clippedSampleRatio
        ) {
            this.scenario = scenario;
            this.result = result;
            this.signalSnapshot = signalSnapshot;
            this.timingSnapshot = timingSnapshot;
            this.decoderSnapshot = decoderSnapshot;
            this.clippedSampleRatio = clippedSampleRatio;
        }
    }
}
