package org.bi9clt.cwcn.core.audio;

import org.junit.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CwLocalAudioForcedToneDecodeProbeTest {
    private static final Map<String, LocalAudioDecodeTestSupport.OfflineDetailedProbeResult> DETAILED_RESULTS =
            loadDetailedResults();

    @Test
    public void printForcedTrackedAndHypothesisDecodeForKeyLocalRecordings() {
        printComparison("\u5f55\u97f3 (2)");
        printComparison("20260427_222505");
        printComparison("\u5f55\u97f3 (8)");
    }

    @Test
    public void printForcedTrackedAndHypothesisDecodeForAllLocalRecordings() {
        for (LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed : DETAILED_RESULTS.values()) {
            printComparison(detailed);
        }
    }

    @Test
    public void printOnlyDifferingForcedTrackedAndHypothesisDecodeForLocalRecordings() {
        for (LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed : DETAILED_RESULTS.values()) {
            LocalAudioDecodeTestSupport.ForcedToneReplayResult trackedReplay =
                    LocalAudioDecodeTestSupport.replayForcedTrackedToneDecode(detailed);
            LocalAudioDecodeTestSupport.ForcedToneReplayResult hypothesisReplay =
                    LocalAudioDecodeTestSupport.replayForcedHypothesisToneDecode(detailed);
            if (!trackedReplay.decodedText().equals(hypothesisReplay.decodedText())) {
                printComparison(detailed, trackedReplay, hypothesisReplay);
            }
        }
    }

    private static void printComparison(String sourceLabel) {
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed = requireResult(sourceLabel);
        printComparison(detailed);
    }

    private static void printComparison(LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed) {
        LocalAudioDecodeTestSupport.ForcedToneReplayResult trackedReplay =
                LocalAudioDecodeTestSupport.replayForcedTrackedToneDecode(detailed);
        LocalAudioDecodeTestSupport.ForcedToneReplayResult hypothesisReplay =
                LocalAudioDecodeTestSupport.replayForcedHypothesisToneDecode(detailed);
        printComparison(detailed, trackedReplay, hypothesisReplay);
    }

    private static void printComparison(
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed,
            LocalAudioDecodeTestSupport.ForcedToneReplayResult trackedReplay,
            LocalAudioDecodeTestSupport.ForcedToneReplayResult hypothesisReplay
    ) {
        System.out.println("==== " + detailed.probeResult().sourceLabel() + " ====");
        System.out.println("base=" + detailed.probeResult().decodedText());
        System.out.println(trackedReplay.renderSummary());
        System.out.println(hypothesisReplay.renderSummary());
    }

    private static Map<String, LocalAudioDecodeTestSupport.OfflineDetailedProbeResult> loadDetailedResults() {
        try {
            Map<String, LocalAudioDecodeTestSupport.OfflineDetailedProbeResult> results = new LinkedHashMap<>();
            for (Path wavFile : LocalAudioDecodeTestSupport.listConvertedWavFiles()) {
                LocalAudioDecodeTestSupport.OfflineDetailedProbeResult result =
                        LocalAudioDecodeTestSupport.decodeWavFileDetailed(wavFile);
                results.put(result.probeResult().sourceLabel(), result);
            }
            return results;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to decode detailed local TestAudio WAV fixtures", exception);
        }
    }

    private static LocalAudioDecodeTestSupport.OfflineDetailedProbeResult requireResult(String sourceLabel) {
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult result = DETAILED_RESULTS.get(sourceLabel);
        if (result == null && sourceLabel != null) {
            for (Map.Entry<String, LocalAudioDecodeTestSupport.OfflineDetailedProbeResult> entry : DETAILED_RESULTS.entrySet()) {
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
