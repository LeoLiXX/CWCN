package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertTrue;

public final class CwRecording10And11SweepProbeTest {
    private static final int DEFAULT_SQL_PERCENT = 55;
    private static final int DEFAULT_SEED_WPM = 15;
    private static final int[] RECORDING10_PREFERRED_TONES = new int[]{450, 500, 550, 600, 650, 700};
    private static final int[] RECORDING11_SEED_WPMS = new int[]{12, 15, 18, 20, 22, 24, 26, 28};
    private static final int[] RECORDING11_PREFERRED_TONES = new int[]{700, 740, 760, 780, 790, 800};
    private static final String EXPECTED_LONG_QSO =
            "CQ CQ DE BG1XXX K BG1XXX DE JA1ABC K JA1ABC DE BG1XXX TNX FER CALL UR RST 599 QTH BEIJING NAME LEO HW? K BG1XXX DE JA1ABC FB OM UR RST 579 QTH TOKYO NAME KEN BK TNX QSO 73 DE BG1XXX SK";

    @Test
    public void printRecording10PreferredToneSweep() throws Exception {
        Path wavFile = requireWav("(10).wav");
        System.out.println("==== recording10 preferred-tone sweep ====");
        for (int preferredToneHz : RECORDING10_PREFERRED_TONES) {
            printSummary(
                    "tone=" + preferredToneHz,
                    LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                            wavFile,
                            preferredToneHz,
                            DEFAULT_SEED_WPM,
                            DEFAULT_SQL_PERCENT,
                            false,
                            CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                    )
            );
        }

        assertTrue(true);
    }

    @Test
    public void printRecording10ModeComparison() throws Exception {
        Path wavFile = requireWav("(10).wav");
        List<AudioFrame> frames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );

        System.out.println("==== recording10 mode comparison ====");
        printSummary(
                "hybrid@700",
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "recording10-hybrid-700",
                        frames,
                        700,
                        DEFAULT_SEED_WPM,
                        DEFAULT_SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                )
        );
        printSummary(
                "hybrid@500",
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "recording10-hybrid-500",
                        frames,
                        500,
                        DEFAULT_SEED_WPM,
                        DEFAULT_SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                )
        );
        printSummary(
                "fixed@700",
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeWithTurnCarry(
                        "recording10-fixed-700",
                        frames,
                        700,
                        DEFAULT_SEED_WPM,
                        DEFAULT_SQL_PERCENT,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        CwSignalProcessor.RxToneMode.FIXED_TONE,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                )
        );
        printSummary(
                "fixed@500",
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeWithTurnCarry(
                        "recording10-fixed-500",
                        frames,
                        500,
                        DEFAULT_SEED_WPM,
                        DEFAULT_SQL_PERCENT,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        CwSignalProcessor.RxToneMode.FIXED_TONE,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                )
        );

        assertTrue(true);
    }

    @Test
    public void printRecording11SeedSweep() throws Exception {
        Path wavFile = requireWav("(11).wav");
        System.out.println("==== recording11 seed sweep ====");
        for (int seedWpm : RECORDING11_SEED_WPMS) {
            printSummary(
                    "seed=" + seedWpm,
                    LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                            wavFile,
                            700,
                            seedWpm,
                            DEFAULT_SQL_PERCENT,
                            false,
                            CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                    )
            );
        }

        assertTrue(true);
    }

    @Test
    public void printRecording11PreferredToneAndModeSweep() throws Exception {
        Path wavFile = requireWav("(11).wav");
        List<AudioFrame> frames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );

        System.out.println("==== recording11 preferred-tone sweep ====");
        for (int preferredToneHz : RECORDING11_PREFERRED_TONES) {
            printSummary(
                    "tone=" + preferredToneHz,
                    LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                            "recording11-hybrid-" + preferredToneHz,
                            frames,
                            preferredToneHz,
                            DEFAULT_SEED_WPM,
                            DEFAULT_SQL_PERCENT,
                            false,
                            CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                    )
            );
        }

        System.out.println("==== recording11 mode comparison ====");
        printSummary(
                "hybrid@700",
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "recording11-hybrid-700",
                        frames,
                        700,
                        DEFAULT_SEED_WPM,
                        DEFAULT_SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                )
        );
        printSummary(
                "hybrid@790",
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "recording11-hybrid-790",
                        frames,
                        790,
                        DEFAULT_SEED_WPM,
                        DEFAULT_SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                )
        );
        printSummary(
                "fixed@700",
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeWithTurnCarry(
                        "recording11-fixed-700",
                        frames,
                        700,
                        DEFAULT_SEED_WPM,
                        DEFAULT_SQL_PERCENT,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        CwSignalProcessor.RxToneMode.FIXED_TONE,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                )
        );
        printSummary(
                "fixed@790",
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeWithTurnCarry(
                        "recording11-fixed-790",
                        frames,
                        790,
                        DEFAULT_SEED_WPM,
                        DEFAULT_SQL_PERCENT,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        CwSignalProcessor.RxToneMode.FIXED_TONE,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                )
        );

        assertTrue(true);
    }

    private static void printSummary(LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed) {
        printSummary("(case)", detailed);
    }

    private static void printSummary(
            String label,
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed
    ) {
        String finalText = sanitize(detailed.probeResult().decodedText());
        double recall = charRecall(EXPECTED_LONG_QSO, finalText);
        String canonical = canonicalize(finalText);
        System.out.println(String.format(
                Locale.US,
                "%s recall=%.4f chars=%d trust=%s dot=%s tone=%d/%d/%d wpm=%d bg1=%d ja1=%d rst=%d qth=%d sk=%d rejects=%s",
                label,
                recall,
                detailed.probeResult().decoderSnapshot().totalCharacters(),
                firstTrustedOffsetMs(detailed) < 0L ? "-" : firstTrustedOffsetMs(detailed) + "ms",
                firstTrustedDotMs(detailed) <= 0.0d
                        ? "-"
                        : String.format(Locale.US, "%.1f", firstTrustedDotMs(detailed)),
                detailed.probeResult().signalSnapshot().targetToneFrequencyHz(),
                detailed.probeResult().signalSnapshot().effectiveAcquisitionWinnerFrequencyHz(),
                detailed.probeResult().signalSnapshot().effectiveFinalAdoptedFrequencyHz(),
                detailed.probeResult().timingSnapshot().estimatedWpm(),
                countOccurrences(canonical, "BG1XXX"),
                countOccurrences(canonical, "JA1ABC"),
                countOccurrences(canonical, "RST"),
                countOccurrences(canonical, "QTH"),
                countOccurrences(canonical, "SK"),
                detailed.stableRejectCounts()
        ));
        System.out.println("final=" + finalText);
    }

    private static Path requireWav(String suffix) throws Exception {
        return LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith(suffix))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for " + suffix));
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
