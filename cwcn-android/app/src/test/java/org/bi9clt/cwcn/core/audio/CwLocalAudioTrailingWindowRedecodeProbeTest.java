package org.bi9clt.cwcn.core.audio;

import org.junit.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class CwLocalAudioTrailingWindowRedecodeProbeTest {
    private static final Map<String, LocalAudioDecodeTestSupport.OfflineDetailedProbeResult> DETAILED_RESULTS =
            loadDetailedResults();

    @Test
    public void recording2_recentWordPrototype_printsBaselineVsLocalRedecode() {
        probeBySuffix("(2)", "CQ DX CQ DX DE JV3VV JV3VV PAGE K. CQ DX CQ DX DE JV3VV JV3VV PAGE K.");
    }

    @Test
    public void highWpmGuard_recentWordPrototype_staysObservable() {
        probeBySuffix(
                "(11)",
                "CQ CQ DE BG1XXX K BG1XXX DE JA1ABC K JA1ABC DE BG1XXX TNX FER CALL UR RST 599 QTH BEIJING NAME LEO HW? K BG1XXX DE JA1ABC FB OM UR RST 579 QTH TOKYO NAME KEN BK TNX QSO 73 DE BG1XXX SK"
        );
    }

    @Test
    public void hypothesisProbe_printsProblemRecordingToneBeliefState() {
        printHypothesisBySuffix("(2)");
        printHypothesisBySuffix("(3)");
        printHypothesisBySuffix("(8)");
    }

    private void probeBySuffix(String sourceLabelSuffix, String expectedText) {
        probe(findSourceLabelBySuffix(sourceLabelSuffix), expectedText);
    }

    private void printHypothesisBySuffix(String sourceLabelSuffix) {
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailedResult = requireResult(findSourceLabelBySuffix(sourceLabelSuffix));
        LocalAudioDecodeTestSupport.OfflineProbeResult baseline = detailedResult.probeResult();
        String summary = baseline.sourceLabel()
                + "\ntrackedTone=" + baseline.signalSnapshot().targetToneFrequencyHz()
                + " trackedDisplay=" + baseline.signalSnapshot().effectiveTrackedToneFrequencyHz()
                + " hypTone=" + baseline.signalSnapshot().toneHypothesisFrequencyHz()
                + " hypConf=" + String.format(Locale.US, "%.2f", baseline.signalSnapshot().toneHypothesisConfidence())
                + " hypFrames=" + baseline.signalSnapshot().toneHypothesisSupportFrames()
                + " hypIdle=" + baseline.signalSnapshot().toneHypothesisIdleFrames()
                + " hypSource=" + baseline.signalSnapshot().toneHypothesisSource()
                + "\nprefWinner=" + baseline.signalSnapshot().preferredWindowWinnerFrequencyHz()
                + " wideWinner=" + baseline.signalSnapshot().wideScanWinnerFrequencyHz()
                + " acqWinner=" + baseline.signalSnapshot().acquisitionWinnerFrequencyHz()
                + " acqSource=" + baseline.signalSnapshot().acquisitionWinnerSource()
                + "\nacqDetail=" + baseline.signalSnapshot().acquisitionDecisionDetail()
                + "\nprefTop=" + baseline.signalSnapshot().preferredWindowTopCandidatesSummary()
                + "\nwideTop=" + baseline.signalSnapshot().wideScanTopCandidatesSummary();
        System.out.println(summary);
        assertTrue(summary,
                baseline.signalSnapshot().toneHypothesisSupportFrames() > 0
                        || ("NONE".equals(baseline.signalSnapshot().toneHypothesisSource())
                        && baseline.signalSnapshot().effectiveTrackedToneFrequencyHz() >= 560));
    }

    private void probe(String sourceLabel, String expectedText) {
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailedResult = requireResult(sourceLabel);
        LocalAudioDecodeTestSupport.OfflineProbeResult baseline = detailedResult.probeResult();
        LocalAudioDecodeTestSupport.TrailingWindowRedecodeResult recentOneWord =
                LocalAudioDecodeTestSupport.redecodeTrailingWords(detailedResult, 1);
        LocalAudioDecodeTestSupport.TrailingWindowRedecodeResult recentTwoWords =
                LocalAudioDecodeTestSupport.redecodeTrailingWords(detailedResult, 2);

        String summary = sourceLabel
                + "\nexpected=" + expectedText
                + "\nbaseline=" + baseline.decodedText()
                + "\nrecent1=" + recentOneWord.decodedText()
                + "\nrecent2=" + recentTwoWords.decodedText()
                + "\nbaselineRecall=" + formatRecall(charRecall(expectedText, baseline.decodedText()))
                + "\nrecent1Recall=" + formatRecall(charRecall(expectedText, recentOneWord.decodedText()))
                + "\nrecent2Recall=" + formatRecall(charRecall(expectedText, recentTwoWords.decodedText()))
                + "\nbaselineChars=" + baseline.decoderSnapshot().totalCharacters()
                + " recent1Chars=" + recentOneWord.decoderSnapshot().totalCharacters()
                + " recent2Chars=" + recentTwoWords.decoderSnapshot().totalCharacters()
                + "\nbaselineWpm=" + baseline.timingSnapshot().estimatedWpm()
                + " recent1Wpm=" + recentOneWord.timingSnapshot().estimatedWpm()
                + " recent2Wpm=" + recentTwoWords.timingSnapshot().estimatedWpm()
                + "\nrecent1StartMs=" + recentOneWord.windowStartTimestampMs()
                + " recent2StartMs=" + recentTwoWords.windowStartTimestampMs()
                + "\nrecent1TimingEvents=" + recentOneWord.timingEvents().size()
                + " recent2TimingEvents=" + recentTwoWords.timingEvents().size();

        System.out.println(summary);

        assertNotNull(summary, baseline.decodedText());
        assertTrue(summary, !"(empty)".equals(recentOneWord.decodedText()));
        assertTrue(summary, recentOneWord.decoderSnapshot().totalCharacters() >= 1);
        assertTrue(summary, recentOneWord.timingEvents().size() >= 2);
        assertTrue(summary, recentTwoWords.windowStartTimestampMs() <= recentOneWord.windowStartTimestampMs());
    }

    private static Map<String, LocalAudioDecodeTestSupport.OfflineDetailedProbeResult> loadDetailedResults() {
        try {
            Map<String, LocalAudioDecodeTestSupport.OfflineDetailedProbeResult> results = new LinkedHashMap<>();
            List<Path> wavFiles = LocalAudioDecodeTestSupport.listConvertedWavFiles();
            for (Path wavFile : wavFiles) {
                LocalAudioDecodeTestSupport.OfflineDetailedProbeResult result =
                        LocalAudioDecodeTestSupport.decodeWavFileDetailed(wavFile);
                results.put(result.probeResult().sourceLabel(), result);
            }
            return results;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to decode local TestAudio WAV fixtures in detailed mode", exception);
        }
    }

    private LocalAudioDecodeTestSupport.OfflineDetailedProbeResult requireResult(String sourceLabel) {
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult result = DETAILED_RESULTS.get(sourceLabel);
        assertNotNull("Missing decoded local audio fixture: " + sourceLabel, result);
        return result;
    }

    private String findSourceLabelBySuffix(String sourceLabelSuffix) {
        for (String sourceLabel : DETAILED_RESULTS.keySet()) {
            if (sourceLabel.endsWith(sourceLabelSuffix)) {
                return sourceLabel;
            }
        }
        throw new AssertionError("Missing decoded local audio fixture with suffix: " + sourceLabelSuffix);
    }

    private static String formatRecall(double recall) {
        return String.format(Locale.US, "%.3f", recall);
    }

    private static double charRecall(String expectedText, String actualText) {
        String expected = canonicalize(expectedText);
        String actual = canonicalize(actualText);
        if (expected.isEmpty()) {
            return actual.isEmpty() ? 1.0d : 0.0d;
        }
        int lcs = longestCommonSubsequenceLength(expected, actual);
        return lcs / (double) expected.length();
    }

    private static String canonicalize(String text) {
        if (text == null) {
            return "";
        }
        String upper = text.toUpperCase(Locale.US).replace('\u25A1', '?');
        StringBuilder builder = new StringBuilder(upper.length());
        for (int index = 0; index < upper.length(); index++) {
            char ch = upper.charAt(index);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '?') {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static int longestCommonSubsequenceLength(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
            char leftChar = left.charAt(leftIndex - 1);
            for (int rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                if (leftChar == right.charAt(rightIndex - 1)) {
                    current[rightIndex] = previous[rightIndex - 1] + 1;
                } else {
                    current[rightIndex] = Math.max(previous[rightIndex], current[rightIndex - 1]);
                }
            }
            int[] swap = previous;
            previous = current;
            current = swap;
            java.util.Arrays.fill(current, 0);
        }
        return previous[right.length()];
    }
}
