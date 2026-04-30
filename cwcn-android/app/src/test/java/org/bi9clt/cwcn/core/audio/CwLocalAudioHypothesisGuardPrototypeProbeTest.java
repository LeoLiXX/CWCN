package org.bi9clt.cwcn.core.audio;

import org.junit.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CwLocalAudioHypothesisGuardPrototypeProbeTest {
    private static final Map<String, LocalAudioDecodeTestSupport.OfflineDetailedProbeResult> BASELINE_RESULTS =
            loadDetailedResults(false);
    private static final Map<String, LocalAudioDecodeTestSupport.OfflineDetailedProbeResult> EXPERIMENT_RESULTS =
            loadDetailedResults(true);

    @Test
    public void printPrototypeComparisonForKeyLocalRecordings() {
        printComparison("录音 (2)");
        printComparison("20260427_222505");
        printComparison("录音 (8)");
    }

    private static void printComparison(String sourceLabel) {
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult baseline = requireResult(BASELINE_RESULTS, sourceLabel);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult experiment = requireResult(EXPERIMENT_RESULTS, sourceLabel);
        LocalAudioDecodeTestSupport.FrontEndDisagreementProfile baselineProfile =
                LocalAudioDecodeTestSupport.evaluateFrontEndDisagreementProfile(baseline, 40);
        LocalAudioDecodeTestSupport.FrontEndDisagreementProfile experimentProfile =
                LocalAudioDecodeTestSupport.evaluateFrontEndDisagreementProfile(experiment, 40);

        System.out.println("==== " + baseline.probeResult().sourceLabel() + " OFF ====");
        printDecodeSummary(baseline);
        System.out.println(baselineProfile.renderSummary());
        printForcedSummary(baseline);

        System.out.println("==== " + experiment.probeResult().sourceLabel() + " ON ====");
        printDecodeSummary(experiment);
        System.out.println(experimentProfile.renderSummary());
        printForcedSummary(experiment);
    }

    private static void printDecodeSummary(LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed) {
        System.out.println(
                "base=" + detailed.probeResult().decodedText()
                        + " target=" + detailed.probeResult().signalSnapshot().targetToneFrequencyHz()
                        + " eff=" + detailed.probeResult().signalSnapshot().effectiveTrackedToneFrequencyHz()
                        + " hyp=" + detailed.probeResult().signalSnapshot().toneHypothesisFrequencyHz()
                        + " rep=" + detailed.probeResult().signalSnapshot().representativeLockedToneFrequencyHz()
                        + " finalSrc=" + detailed.probeResult().signalSnapshot().finalAdoptedSource()
                        + " decision=" + detailed.probeResult().signalSnapshot().hypothesisGuardDecision()
                        + " applyCount=" + detailed.probeResult().signalSnapshot().hypothesisGuardApplyCount()
        );
    }

    private static void printForcedSummary(LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed) {
        System.out.println(LocalAudioDecodeTestSupport.replayForcedTrackedToneDecode(detailed).renderSummary());
        System.out.println(LocalAudioDecodeTestSupport.replayForcedEffectiveTrackedToneDecode(detailed).renderSummary());
        System.out.println(LocalAudioDecodeTestSupport.replayForcedHypothesisToneDecode(detailed).renderSummary());
    }

    private static Map<String, LocalAudioDecodeTestSupport.OfflineDetailedProbeResult> loadDetailedResults(
            boolean experimentalHypothesisGuardEnabled
    ) {
        try {
            Map<String, LocalAudioDecodeTestSupport.OfflineDetailedProbeResult> results = new LinkedHashMap<>();
            for (Path wavFile : LocalAudioDecodeTestSupport.listConvertedWavFiles()) {
                LocalAudioDecodeTestSupport.OfflineDetailedProbeResult result =
                        LocalAudioDecodeTestSupport.decodeWavFileDetailed(
                                wavFile,
                                experimentalHypothesisGuardEnabled
                        );
                results.put(result.probeResult().sourceLabel(), result);
            }
            return results;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to decode local TestAudio WAV fixtures", exception);
        }
    }

    private static LocalAudioDecodeTestSupport.OfflineDetailedProbeResult requireResult(
            Map<String, LocalAudioDecodeTestSupport.OfflineDetailedProbeResult> results,
            String sourceLabel
    ) {
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult result = results.get(sourceLabel);
        if (result == null && sourceLabel != null) {
            for (Map.Entry<String, LocalAudioDecodeTestSupport.OfflineDetailedProbeResult> entry : results.entrySet()) {
                String candidateLabel = entry.getKey();
                if (candidateLabel.equalsIgnoreCase(sourceLabel)
                        || candidateLabel.endsWith(sourceLabel)
                        || sourceLabel.endsWith(candidateLabel)
                        || shareTrailingRecordingSuffix(candidateLabel, sourceLabel)) {
                    result = entry.getValue();
                    break;
                }
            }
        }
        if (result == null) {
            throw new IllegalArgumentException("Missing detailed decoded local audio fixture: " + sourceLabel);
        }
        return result;
    }

    private static boolean shareTrailingRecordingSuffix(String left, String right) {
        int leftOpen = left == null ? -1 : left.lastIndexOf('(');
        int rightOpen = right == null ? -1 : right.lastIndexOf('(');
        int leftClose = left == null ? -1 : left.lastIndexOf(')');
        int rightClose = right == null ? -1 : right.lastIndexOf(')');
        if (leftOpen < 0 || rightOpen < 0 || leftClose <= leftOpen || rightClose <= rightOpen) {
            return false;
        }
        String leftSuffix = left.substring(leftOpen, leftClose + 1);
        String rightSuffix = right.substring(rightOpen, rightClose + 1);
        return leftSuffix.equals(rightSuffix);
    }
}
