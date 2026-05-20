package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CwRecording12GateSweepProbeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;
    private static final String EXPECTED_TEXT = "CQ CQ CQ DE BI9CMS BI9CMS IN 700 PSE K.";

    @Test
    public void printRecording12GateSweep() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(12).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for recording (12)"));
        List<AudioFrame> frames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );

        System.out.println("==== recording(12) gate sweep ====");
        System.out.println("expected=" + EXPECTED_TEXT);
        printCase("BASE", frames, false, false, false, false);
        printCase("AUTH", frames, true, false, false, false);
        printCase("ONSET", frames, false, true, false, false);
        printCase("MERGE", frames, false, false, true, false);
        printCase("HOLD", frames, false, false, false, true);
        printCase("AUTH+ONSET", frames, true, true, false, false);
        printCase("ONSET+MERGE", frames, false, true, true, false);
        printCase("ONSET+HOLD", frames, false, true, false, true);
        printCase("MERGE+HOLD", frames, false, false, true, true);
        printCase("ALL", frames, true, true, true, true);
    }

    private static void printCase(
            String label,
            List<AudioFrame> frames,
            boolean authority,
            boolean onset,
            boolean merge,
            boolean hold
    ) {
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult result =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "recording12-" + label.toLowerCase(Locale.US),
                        frames,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        authority,
                        onset,
                        merge,
                        hold,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
        String finalText = sanitize(result.probeResult().decodedText());
        System.out.println(String.format(
                Locale.US,
                "%s trust=%dms chars=%d recall=%.3f tone=%d/%d/%d rejects=%s text=%s",
                label,
                firstTrustTimestampMs(result),
                result.probeResult().decoderSnapshot().totalCharacters(),
                charRecall(EXPECTED_TEXT, finalText),
                result.probeResult().signalSnapshot().targetToneFrequencyHz(),
                result.probeResult().signalSnapshot().effectiveTrackedToneFrequencyHz(),
                result.probeResult().signalSnapshot().effectiveFinalAdoptedFrequencyHz(),
                renderRejects(result.stableRejectCounts()),
                finalText
        ));
    }

    private static long firstTrustTimestampMs(LocalAudioDecodeTestSupport.OfflineDetailedProbeResult result) {
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : result.timingStateTraces()) {
            if (trace != null
                    && trace.debugSnapshot() != null
                    && trace.debugSnapshot().trustedDotEstimateMs() > 0.0d) {
                return trace.timestampMs();
            }
        }
        return -1L;
    }

    private static String renderRejects(Map<String, Integer> rejects) {
        return rejects == null ? "{}" : rejects.toString();
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.replace('\u25A1', '?');
    }

    private static double charRecall(String expectedText, String actualText) {
        String expected = canonicalize(expectedText);
        String actual = canonicalize(actualText);
        if (expected.isEmpty()) {
            return actual.isEmpty() ? 1.0d : 0.0d;
        }
        return longestCommonSubsequenceLength(expected, actual) / (double) expected.length();
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
