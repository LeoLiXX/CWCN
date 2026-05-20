package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertTrue;

public final class CwRecording12FixedToneSqlProbeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int[] SQL_VALUES = new int[]{55, 15};
    private static final int[] FIXED_WINDOWS_HZ = new int[]{30, 50, 70};
    private static final String EXPECTED_TEXT = "CQ CQ CQ DE BI9CMS BI9CMS IN 700 PSE K.";
    private static final String[] PRIMARY_TOKENS = new String[]{
            "DEBI9CMS",
            "BI9CMS",
            "700",
            "PSEK"
    };
    private static final String[] SECONDARY_TOKENS = new String[]{
            "CQCQCQ"
    };

    @Test
    public void printRecording12FixedToneSqlComparison() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(12).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (12)"));
        List<AudioFrame> frames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );

        System.out.println("==== recording(12) fixed-tone + sql comparison ====");
        System.out.println("expected=" + EXPECTED_TEXT);
        for (int sqlPercent : SQL_VALUES) {
            printCase(
                    "HYBRID_DEFAULT",
                    sqlPercent,
                    LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                            "recording12-hybrid-sql-" + sqlPercent,
                            frames,
                            PREFERRED_TONE_HZ,
                            SEED_WPM,
                            sqlPercent,
                            false,
                            CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                    )
            );
            for (int windowHz : FIXED_WINDOWS_HZ) {
                printCase(
                        "STATIC_FIXED_WIN_" + windowHz,
                        sqlPercent,
                        LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeWithFixedToneLearningWindow(
                                "recording12-fixed-win-" + windowHz + "-sql-" + sqlPercent,
                                frames,
                                PREFERRED_TONE_HZ,
                                SEED_WPM,
                                sqlPercent,
                                false,
                                windowHz,
                                CwSignalProcessor.RxToneMode.FIXED_TONE,
                                CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                        )
                );
            }
            System.out.println();
        }
        assertTrue(true);
    }

    private static void printCase(
            String label,
            int sqlPercent,
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed
    ) {
        String decodedText = sanitize(detailed.probeResult().decodedText());
        String canonicalText = canonicalize(decodedText);
        double recall = charRecall(EXPECTED_TEXT, decodedText);
        int primaryMatches = countMatchedTokens(canonicalText, PRIMARY_TOKENS);
        int secondaryMatches = countMatchedTokens(canonicalText, SECONDARY_TOKENS);
        long trustTimestampMs = firstTrustTimestampMs(detailed);

        System.out.println(String.format(
                Locale.US,
                "%s sql=%d recall=%.3f primary=%d/%d secondary=%d/%d trust=%d tone=%d/%d/%d wpm=%d chars=%d final=%s",
                label,
                sqlPercent,
                recall,
                primaryMatches,
                PRIMARY_TOKENS.length,
                secondaryMatches,
                SECONDARY_TOKENS.length,
                trustTimestampMs,
                detailed.probeResult().signalSnapshot().targetToneFrequencyHz(),
                detailed.probeResult().signalSnapshot().effectiveAcquisitionWinnerFrequencyHz(),
                detailed.probeResult().signalSnapshot().effectiveFinalAdoptedFrequencyHz(),
                detailed.probeResult().timingSnapshot().estimatedWpm(),
                detailed.probeResult().decoderSnapshot().totalCharacters(),
                decodedText
        ));
    }

    private static int countMatchedTokens(String canonicalText, String[] tokens) {
        int matches = 0;
        for (String token : tokens) {
            if (token != null && !token.isEmpty() && canonicalText.contains(token)) {
                matches += 1;
            }
        }
        return matches;
    }

    private static long firstTrustTimestampMs(LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed) {
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : detailed.timingStateTraces()) {
            if (trace != null
                    && trace.debugSnapshot() != null
                    && trace.debugSnapshot().trustedDotEstimateMs() > 0.0d) {
                return trace.timestampMs();
            }
        }
        return -1L;
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "(empty)";
        }
        String normalized = text.replace('\u25A1', '?').replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.isEmpty() ? "(empty)" : normalized;
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
