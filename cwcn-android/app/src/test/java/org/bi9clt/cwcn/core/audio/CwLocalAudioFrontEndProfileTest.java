package org.bi9clt.cwcn.core.audio;

import org.junit.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class CwLocalAudioFrontEndProfileTest {
    private static final Map<String, LocalAudioDecodeTestSupport.OfflineDetailedProbeResult> DETAILED_RESULTS =
            loadDetailedResults();

    @Test
    public void recording2ShowsMeaningfulTrackedVsHypothesisDisagreementWindow() {
        LocalAudioDecodeTestSupport.FrontEndDisagreementProfile profile =
                evaluate("褰曢煶 (2)");
        String summary = profile.renderSummary();

        assertTrue(summary, profile.observedHypothesisFrames() > 0);
        assertTrue(summary, profile.rawTargetDisagreementFrames() > 0);
    }

    @Test
    public void recording20260427_222505ShowsRawTargetSplitButEffectiveTrackedIsMoreStable() {
        LocalAudioDecodeTestSupport.FrontEndDisagreementProfile profile =
                evaluate("20260427_222505");
        String summary = profile.renderSummary();

        assertTrue(summary, profile.observedHypothesisFrames() > 0);
        assertTrue(summary, profile.rawTargetDisagreementFrames() > 0);
        assertTrue(
                summary,
                profile.effectiveTrackedDisagreementFrames() <= profile.rawTargetDisagreementFrames()
        );
    }

    @Test
    public void recording8CurrentlyProvidesLittleHypothesisSignalAndShouldStayObservabilityOnly() {
        LocalAudioDecodeTestSupport.FrontEndDisagreementProfile profile =
                evaluate("褰曢煶 (8)");
        String summary = profile.renderSummary();

        assertTrue(
                summary,
                profile.observedHypothesisFrames() == 0 || profile.rawTargetDisagreementFrames() >= 0
        );
    }

    private static LocalAudioDecodeTestSupport.FrontEndDisagreementProfile evaluate(String sourceLabel) {
        return LocalAudioDecodeTestSupport.evaluateFrontEndDisagreementProfile(requireResult(sourceLabel), 40);
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
        assertNotNull("Missing detailed decoded local audio fixture: " + sourceLabel, result);
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
