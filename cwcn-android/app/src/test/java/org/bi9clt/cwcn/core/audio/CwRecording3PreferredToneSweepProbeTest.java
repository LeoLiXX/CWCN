package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertTrue;

public final class CwRecording3PreferredToneSweepProbeTest {
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;
    private static final int[] PREFERRED_TONES_HZ = new int[]{520, 550, 580, 600, 650, 700};
    private static final String EXPECTED_TEXT =
            "BI9CMS BI9CMS BI9CMS DE BI9CLT BI8DLT BI9CLT UR 599 5NN BK.";

    @Test
    public void printRecording3PreferredToneSweep() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(3).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (3)"));

        System.out.println("==== recording3 preferred-tone sweep ====");
        for (int preferredToneHz : PREFERRED_TONES_HZ) {
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                    LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                            wavFile,
                            preferredToneHz,
                            SEED_WPM,
                            SQL_PERCENT,
                            false,
                            CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                    );

            String finalText = sanitize(detailed.probeResult().decodedText());
            String stableText = sanitize(textAtOrBefore(
                    detailed.stableAcceptedDecodeEvents(),
                    detailed.flushTimestampMs()
            ));
            double recall = charRecall(EXPECTED_TEXT, finalText);
            String canonical = canonicalize(finalText);

            System.out.println(String.format(
                    Locale.US,
                    "pref=%d recall=%.4f chars=%d turns=%d trust=%s dot=%s tone=%d/%d/%d hyp=%d conf=%.2f sup=%d on=%d off=%d bi9cms=%d bi9clt=%d ur=%d rst=%d bk=%d rejects=%s",
                    preferredToneHz,
                    recall,
                    detailed.probeResult().decoderSnapshot().totalCharacters(),
                    countTurns(detailed.turnTransitionTraces()),
                    firstTrustedOffsetMs(detailed) < 0L ? "-" : firstTrustedOffsetMs(detailed) + "ms",
                    firstTrustedDotMs(detailed) <= 0.0d ? "-" : String.format(Locale.US, "%.1f", firstTrustedDotMs(detailed)),
                    detailed.probeResult().signalSnapshot().targetToneFrequencyHz(),
                    detailed.probeResult().signalSnapshot().effectiveAcquisitionWinnerFrequencyHz(),
                    detailed.probeResult().signalSnapshot().effectiveFinalAdoptedFrequencyHz(),
                    detailed.probeResult().signalSnapshot().toneHypothesisFrequencyHz(),
                    detailed.probeResult().signalSnapshot().toneHypothesisConfidence(),
                    detailed.probeResult().signalSnapshot().toneHypothesisSupportFrames(),
                    detailed.probeResult().signalSnapshot().totalToneOnEvents(),
                    detailed.probeResult().signalSnapshot().totalToneOffEvents(),
                    countOccurrences(canonical, "BI9CMS"),
                    countOccurrences(canonical, "BI9CLT"),
                    countOccurrences(canonical, "UR"),
                    countOccurrences(canonical, "RST"),
                    countOccurrences(canonical, "BK"),
                    detailed.stableRejectCounts()
            ));
            System.out.println("final=" + finalText);
            System.out.println("stable=" + stableText);
        }

        assertTrue(true);
    }

    private static int countTurns(List<LocalAudioDecodeTestSupport.TurnTransitionTrace> traces) {
        int count = 0;
        for (LocalAudioDecodeTestSupport.TurnTransitionTrace trace : traces) {
            if (trace != null
                    && trace.kind() == LocalAudioDecodeTestSupport.TurnTransitionTrace.Kind.START) {
                count += 1;
            }
        }
        return count;
    }

    private static long firstTrustedOffsetMs(LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed) {
        if (detailed.frames().isEmpty()) {
            return -1L;
        }
        long firstFrameTimestampMs = detailed.frames().get(0).capturedAtMs();
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : detailed.timingStateTraces()) {
            if (trace == null
                    || trace.debugSnapshot() == null
                    || trace.debugSnapshot().trustedDotEstimateMs() <= 0.0d) {
                continue;
            }
            return Math.max(0L, trace.timestampMs() - firstFrameTimestampMs);
        }
        return -1L;
    }

    private static double firstTrustedDotMs(LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed) {
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : detailed.timingStateTraces()) {
            if (trace == null
                    || trace.debugSnapshot() == null
                    || trace.debugSnapshot().trustedDotEstimateMs() <= 0.0d) {
                continue;
            }
            return trace.debugSnapshot().trustedDotEstimateMs();
        }
        return 0.0d;
    }

    private static String textAtOrBefore(List<CwDecodeEvent> decodeEvents, long timestampMs) {
        String latestText = "";
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            if (decodeEvent != null && decodeEvent.timestampMs() <= timestampMs) {
                latestText = decodeEvent.outputText();
            } else if (decodeEvent != null && decodeEvent.timestampMs() > timestampMs) {
                break;
            }
        }
        return latestText == null ? "" : latestText;
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "(empty)";
        }
        String normalized = text.replace('\u25A1', '?').replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.isEmpty() ? "(empty)" : normalized;
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

    private static double charRecall(String expectedText, String actualText) {
        String expected = canonicalize(expectedText);
        String actual = canonicalize(actualText);
        if (expected.isEmpty()) {
            return actual.isEmpty() ? 1.0d : 0.0d;
        }
        int lcs = longestCommonSubsequenceLength(expected, actual);
        return lcs / (double) expected.length();
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

    private static int countOccurrences(String text, String fragment) {
        if (text == null || text.isEmpty() || fragment == null || fragment.isEmpty()) {
            return 0;
        }
        int count = 0;
        int offset = 0;
        while (offset <= text.length() - fragment.length()) {
            int index = text.indexOf(fragment, offset);
            if (index < 0) {
                break;
            }
            count += 1;
            offset = index + fragment.length();
        }
        return count;
    }
}
