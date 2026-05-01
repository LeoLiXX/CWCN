package org.bi9clt.cwcn.core.audio;

import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class CwLocalAudioHypothesisGuardPrototypeProbeTest {
    @Test
    public void printPrototypeComparisonForKeyLocalRecordings() {
        printComparison("褰曢煶 (2)");
        printComparison("20260427_222505");
        printComparison("褰曢煶 (8)");
    }

    private static void printComparison(String sourceLabel) {
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult baseline =
                loadDetailedResult(sourceLabel, false);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult experiment =
                loadDetailedResult(sourceLabel, true);
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

    private static LocalAudioDecodeTestSupport.OfflineDetailedProbeResult loadDetailedResult(
            String sourceLabel,
            boolean experimentalHypothesisGuardEnabled
    ) {
        try {
            return LocalAudioDecodeTestSupport.decodeWavFileDetailed(
                    findWavFile(sourceLabel),
                    experimentalHypothesisGuardEnabled
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to decode local TestAudio WAV fixture: " + sourceLabel, exception);
        }
    }

    private static Path findWavFile(String sourceLabel) {
        try {
            List<Path> wavFiles = LocalAudioDecodeTestSupport.listConvertedWavFiles();
            for (Path wavFile : wavFiles) {
                if (matchesSourceLabel(fileNameWithoutExtension(wavFile), sourceLabel)) {
                    return wavFile;
                }
            }
            throw new IllegalArgumentException("Missing detailed decoded local audio fixture: " + sourceLabel);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to locate local TestAudio WAV fixture: " + sourceLabel, exception);
        }
    }

    private static String fileNameWithoutExtension(Path wavFile) {
        String fileName = wavFile.getFileName().toString();
        return fileName.toLowerCase(Locale.US).endsWith(".wav")
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;
    }

    private static boolean matchesSourceLabel(String candidateLabel, String sourceLabel) {
        if (candidateLabel == null || sourceLabel == null) {
            return false;
        }
        return candidateLabel.equalsIgnoreCase(sourceLabel)
                || candidateLabel.endsWith(sourceLabel)
                || sourceLabel.endsWith(candidateLabel)
                || shareTrailingRecordingSuffix(candidateLabel, sourceLabel);
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
