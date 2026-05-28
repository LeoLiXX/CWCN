package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertTrue;

public final class CwTrustedSlowUpdateAbProbeTest {
    private static final int SQL_PERCENT = 55;
    private static final int DIRTY_CASE_PREFERRED_TONE_HZ = 760;
    private static final int REGULAR_CASE_PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;

    @Test
    public void printTrustedSlowUpdateAb() throws Exception {
        printCase(
                "20260427_222505",
                "BI9CLT BI9CLT DE BI9CMS BI9CMS PSE K",
                DIRTY_CASE_PREFERRED_TONE_HZ
        );
        printCase(
                "录音 (16)",
                "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 700 24WPM PSE K.",
                REGULAR_CASE_PREFERRED_TONE_HZ
        );
        assertTrue(true);
    }

    private static void printCase(
            String sourceLabel,
            String expectedText,
            int preferredToneHz
    ) throws Exception {
        Path wavFile = requireWavFile(sourceLabel);
        List<AudioFrame> frames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult baseline =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeHybridBootstrapExperimental(
                        sourceLabel + "-baseline",
                        frames,
                        preferredToneHz,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        96L,
                        null,
                        null,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult blockBoundarySlow =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeHybridBootstrapExperimental(
                        sourceLabel + "-block-boundary-slow",
                        frames,
                        preferredToneHz,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        96L,
                        null,
                        new CwHybridTimingModel.ExperimentalTrustedSlowUpdateTuning(
                                false,
                                0.0d,
                                true,
                                0.08d,
                                1.12d
                        ),
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult lightSlowCap =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeHybridBootstrapExperimental(
                        sourceLabel + "-light-slow-cap",
                        frames,
                        preferredToneHz,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        96L,
                        null,
                        new CwHybridTimingModel.ExperimentalTrustedSlowUpdateTuning(
                                true,
                                0.05d,
                                true,
                                0.06d,
                                1.10d
                        ),
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        System.out.println("==== " + sourceLabel + " trusted slow-update AB ====");
        System.out.println(formatLine("baseline", expectedText, baseline));
        System.out.println(formatLine("block-boundary-slow", expectedText, blockBoundarySlow));
        System.out.println(formatLine("light-slow-cap", expectedText, lightSlowCap));
    }

    private static String formatLine(
            String label,
            String expectedText,
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed
    ) {
        return String.format(
                Locale.US,
                "%s recall=%.3f trust=%d trustedDot=%.1f wpm=%.1f tone=%d/%d/%d tail=%s text=%s",
                label,
                charRecall(expectedText, detailed.probeResult().decodedText()),
                firstTrustTimestampMs(detailed),
                lastTrustedDotMs(detailed),
                detailed.probeResult().timingSnapshot().estimatedWpmPrecise(),
                detailed.probeResult().signalSnapshot().targetToneFrequencyHz(),
                detailed.probeResult().signalSnapshot().effectiveAcquisitionWinnerFrequencyHz(),
                detailed.probeResult().signalSnapshot().effectiveFinalAdoptedFrequencyHz(),
                sanitize(lastTrustedReason(detailed)),
                sanitize(detailed.probeResult().decodedText())
        );
    }

    private static Path requireWavFile(String sourceLabel) throws Exception {
        return LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> matchesSourceLabel(fileNameWithoutExtension(path), sourceLabel))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for " + sourceLabel));
    }

    private static boolean matchesSourceLabel(String candidateLabel, String sourceLabel) {
        if (candidateLabel == null || sourceLabel == null) {
            return false;
        }
        return candidateLabel.equalsIgnoreCase(sourceLabel)
                || candidateLabel.endsWith(sourceLabel)
                || sourceLabel.endsWith(candidateLabel);
    }

    private static String fileNameWithoutExtension(Path wavFile) {
        String fileName = wavFile.getFileName().toString();
        return fileName.toLowerCase(Locale.US).endsWith(".wav")
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;
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

    private static double lastTrustedDotMs(LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed) {
        double lastTrustedDotMs = 0.0d;
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : detailed.timingStateTraces()) {
            if (trace == null || trace.debugSnapshot() == null) {
                continue;
            }
            if (trace.debugSnapshot().trustedDotEstimateMs() > 0.0d) {
                lastTrustedDotMs = trace.debugSnapshot().trustedDotEstimateMs();
            }
        }
        return lastTrustedDotMs;
    }

    private static String lastTrustedReason(LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed) {
        String reason = "(none)";
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : detailed.timingStateTraces()) {
            if (trace == null || trace.debugSnapshot() == null) {
                continue;
            }
            reason = trace.debugSnapshot().lastTrustedUpdateReason();
        }
        return reason;
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
