package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertTrue;

public final class CwDirtyCaseToneWpmTightStrategyProbeTest {
    private static final int SQL_PERCENT = 55;
    private static final int[] PREFERRED_TONES_HZ =
            new int[]{640, 660, 680, 700, 720, 740, 760};
    private static final int[] SEED_WPMS =
            new int[]{6, 8, 10, 12, 14, 15, 16, 18};
    private static final int TIGHT_FIXED_WINDOW_HZ = 30;
    private static final long TIGHT_FIXED_HOLD_MS = 960L;
    private static final int TOP_CONFIG_COUNT = 8;

    @Test
    public void printToneAndWpmTightStrategyMatrixForDirtyCases() throws Exception {
        printCase(
                "20260427_222505",
                "BI9CLT BI9CLT DE BI9CMS BI9CMS PSE K"
        );
        printCase(
                "20260427_224524",
                "CP CP DE B6 B6 LZ HOT LZ HOT KN"
        );
        assertTrue(true);
    }

    private static void printCase(String sourceLabel, String expectedText) throws Exception {
        Path wavFile = requireWavFile(sourceLabel);
        List<AudioFrame> frames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );

        ArrayList<ProbeSummary> allResults = new ArrayList<>();
        for (int preferredToneHz : PREFERRED_TONES_HZ) {
            for (int seedWpm : SEED_WPMS) {
                allResults.add(runStrategy(
                        "HYBRID",
                        sourceLabel,
                        expectedText,
                        frames,
                        preferredToneHz,
                        seedWpm
                ));
                allResults.add(runStrategy(
                        "FIXED_UNTIL_TRUST_WIN30",
                        sourceLabel,
                        expectedText,
                        frames,
                        preferredToneHz,
                        seedWpm
                ));
                allResults.add(runStrategy(
                        "FIXED_HOLD_960_WIN30",
                        sourceLabel,
                        expectedText,
                        frames,
                        preferredToneHz,
                        seedWpm
                ));
                allResults.add(runStrategy(
                        "STATIC_FIXED_WIN30",
                        sourceLabel,
                        expectedText,
                        frames,
                        preferredToneHz,
                        seedWpm
                ));
            }
        }

        ArrayList<ProbeSummary> sorted = new ArrayList<>(allResults);
        sorted.sort(PROBE_ORDER);

        ArrayList<ProbeSummary> tightOnly = new ArrayList<>();
        for (ProbeSummary summary : sorted) {
            if (!"HYBRID".equals(summary.strategyLabel)) {
                tightOnly.add(summary);
            }
        }

        System.out.println("==== " + sourceLabel + " tone/wpm tight-strategy matrix ====");
        printBaselineReference(sorted);
        printBestPerStrategy(sorted);
        printRecommendedRange("overall-top", sorted);
        printRecommendedRange("tight-top", tightOnly);
        printTopConfigs("top-overall", sorted);
        printTopConfigs("top-tight", tightOnly);
    }

    private static ProbeSummary runStrategy(
            String strategyLabel,
            String sourceLabel,
            String expectedText,
            List<AudioFrame> frames,
            int preferredToneHz,
            int seedWpm
    ) {
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed;
        switch (strategyLabel) {
            case "HYBRID":
                detailed = LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        sourceLabel + "-hybrid-pref-" + preferredToneHz + "-seed-" + seedWpm,
                        frames,
                        preferredToneHz,
                        seedWpm,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
                break;
            case "FIXED_UNTIL_TRUST_WIN30":
                detailed =
                        LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeFixedUntilTrustThenAutoWithFixedToneLearningWindow(
                                sourceLabel + "-fut-win30-pref-" + preferredToneHz + "-seed-" + seedWpm,
                                frames,
                                preferredToneHz,
                                seedWpm,
                                SQL_PERCENT,
                                false,
                                TIGHT_FIXED_WINDOW_HZ,
                                CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                        );
                break;
            case "FIXED_HOLD_960_WIN30":
                detailed =
                        LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeFixedHoldThenAutoWithFixedToneLearningWindow(
                                sourceLabel + "-hold960-win30-pref-" + preferredToneHz + "-seed-" + seedWpm,
                                frames,
                                preferredToneHz,
                                seedWpm,
                                SQL_PERCENT,
                                false,
                                TIGHT_FIXED_HOLD_MS,
                                TIGHT_FIXED_WINDOW_HZ,
                                CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                        );
                break;
            case "STATIC_FIXED_WIN30":
                detailed = LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeWithFixedToneLearningWindow(
                        sourceLabel + "-static-fixed-win30-pref-" + preferredToneHz + "-seed-" + seedWpm,
                        frames,
                        preferredToneHz,
                        seedWpm,
                        SQL_PERCENT,
                        false,
                        TIGHT_FIXED_WINDOW_HZ,
                        CwSignalProcessor.RxToneMode.FIXED_TONE,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
                break;
            default:
                throw new IllegalArgumentException("Unknown strategy: " + strategyLabel);
        }
        String finalText = sanitize(detailed.probeResult().decodedText());
        return new ProbeSummary(
                strategyLabel,
                preferredToneHz,
                seedWpm,
                charRecall(expectedText, finalText),
                tokenCoverage(expectedText, finalText),
                firstTrustTimestampMs(detailed),
                detailed.probeResult().timingSnapshot().estimatedWpm(),
                detailed.probeResult().signalSnapshot().targetToneFrequencyHz(),
                detailed.probeResult().signalSnapshot().effectiveAcquisitionWinnerFrequencyHz(),
                detailed.probeResult().signalSnapshot().effectiveFinalAdoptedFrequencyHz(),
                detailed.probeResult().decoderSnapshot().totalCharacters(),
                finalText
        );
    }

    private static void printBaselineReference(List<ProbeSummary> sorted) {
        ProbeSummary baseline = findExact(sorted, "HYBRID", 700, 15);
        ProbeSummary fixedBaseline = findExact(sorted, "STATIC_FIXED_WIN30", 700, 15);
        if (baseline != null) {
            System.out.println("baseline-hybrid: " + baseline.renderLine());
        }
        if (fixedBaseline != null) {
            System.out.println("baseline-static-fixed-win30: " + fixedBaseline.renderLine());
        }
    }

    private static void printBestPerStrategy(List<ProbeSummary> sorted) {
        String[] strategyOrder = new String[]{
                "HYBRID",
                "FIXED_UNTIL_TRUST_WIN30",
                "FIXED_HOLD_960_WIN30",
                "STATIC_FIXED_WIN30"
        };
        System.out.println("-- best-per-strategy --");
        for (String strategyLabel : strategyOrder) {
            ProbeSummary best = null;
            for (ProbeSummary summary : sorted) {
                if (strategyLabel.equals(summary.strategyLabel)) {
                    best = summary;
                    break;
                }
            }
            if (best != null) {
                System.out.println(best.renderLine());
            }
        }
    }

    private static void printRecommendedRange(String label, List<ProbeSummary> sorted) {
        if (sorted == null || sorted.isEmpty()) {
            return;
        }
        int limit = Math.min(TOP_CONFIG_COUNT, sorted.size());
        int minToneHz = Integer.MAX_VALUE;
        int maxToneHz = Integer.MIN_VALUE;
        int minSeedWpm = Integer.MAX_VALUE;
        int maxSeedWpm = Integer.MIN_VALUE;
        double avgToneHz = 0.0d;
        double avgSeedWpm = 0.0d;
        double avgFinalWpm = 0.0d;
        for (int index = 0; index < limit; index++) {
            ProbeSummary summary = sorted.get(index);
            minToneHz = Math.min(minToneHz, summary.preferredToneHz);
            maxToneHz = Math.max(maxToneHz, summary.preferredToneHz);
            minSeedWpm = Math.min(minSeedWpm, summary.seedWpm);
            maxSeedWpm = Math.max(maxSeedWpm, summary.seedWpm);
            avgToneHz += summary.preferredToneHz;
            avgSeedWpm += summary.seedWpm;
            avgFinalWpm += summary.finalEstimatedWpm;
        }
        avgToneHz /= limit;
        avgSeedWpm /= limit;
        avgFinalWpm /= limit;
        System.out.println(String.format(
                Locale.US,
                "%s toneRange=%d-%d avgTone=%.1f seedRange=%d-%d avgSeed=%.1f avgFinalWpm=%.1f",
                label,
                minToneHz,
                maxToneHz,
                avgToneHz,
                minSeedWpm,
                maxSeedWpm,
                avgSeedWpm,
                avgFinalWpm
        ));
    }

    private static void printTopConfigs(String label, List<ProbeSummary> sorted) {
        if (sorted == null || sorted.isEmpty()) {
            return;
        }
        System.out.println("-- " + label + " --");
        int limit = Math.min(TOP_CONFIG_COUNT, sorted.size());
        for (int index = 0; index < limit; index++) {
            System.out.println(sorted.get(index).renderLine());
        }
    }

    private static ProbeSummary findExact(
            List<ProbeSummary> sorted,
            String strategyLabel,
            int preferredToneHz,
            int seedWpm
    ) {
        for (ProbeSummary summary : sorted) {
            if (strategyLabel.equals(summary.strategyLabel)
                    && summary.preferredToneHz == preferredToneHz
                    && summary.seedWpm == seedWpm) {
                return summary;
            }
        }
        return null;
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

    private static String sanitize(String text) {
        if (text == null) {
            return "(empty)";
        }
        String normalized = text.replace('\u25A1', '?').replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.isEmpty() ? "(empty)" : normalized;
    }

    private static double tokenCoverage(String expectedText, String actualText) {
        if (expectedText == null || expectedText.trim().isEmpty()) {
            return 0.0d;
        }
        String actual = canonicalize(actualText);
        String[] expectedTokens = expectedText.trim().split("\\s+");
        int hits = 0;
        int total = 0;
        for (String token : expectedTokens) {
            String canonicalToken = canonicalize(token);
            if (canonicalToken.isEmpty()) {
                continue;
            }
            total += 1;
            if (actual.contains(canonicalToken)) {
                hits += 1;
            }
        }
        return total == 0 ? 0.0d : hits / (double) total;
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

    private static final Comparator<ProbeSummary> PROBE_ORDER =
            Comparator.comparingDouble(ProbeSummary::recall).reversed()
                    .thenComparing(Comparator.comparingDouble(ProbeSummary::tokenCoverage).reversed())
                    .thenComparingLong(ProbeSummary::trustTimestampMs)
                    .thenComparingInt(ProbeSummary::preferredToneHz)
                    .thenComparingInt(ProbeSummary::seedWpm)
                    .thenComparing(ProbeSummary::strategyLabel);

    private static final class ProbeSummary {
        private final String strategyLabel;
        private final int preferredToneHz;
        private final int seedWpm;
        private final double recall;
        private final double tokenCoverage;
        private final long trustTimestampMs;
        private final int finalEstimatedWpm;
        private final int targetToneHz;
        private final int acquisitionToneHz;
        private final int finalAdoptedToneHz;
        private final int totalCharacters;
        private final String finalText;

        private ProbeSummary(
                String strategyLabel,
                int preferredToneHz,
                int seedWpm,
                double recall,
                double tokenCoverage,
                long trustTimestampMs,
                int finalEstimatedWpm,
                int targetToneHz,
                int acquisitionToneHz,
                int finalAdoptedToneHz,
                int totalCharacters,
                String finalText
        ) {
            this.strategyLabel = strategyLabel;
            this.preferredToneHz = preferredToneHz;
            this.seedWpm = seedWpm;
            this.recall = recall;
            this.tokenCoverage = tokenCoverage;
            this.trustTimestampMs = trustTimestampMs;
            this.finalEstimatedWpm = finalEstimatedWpm;
            this.targetToneHz = targetToneHz;
            this.acquisitionToneHz = acquisitionToneHz;
            this.finalAdoptedToneHz = finalAdoptedToneHz;
            this.totalCharacters = totalCharacters;
            this.finalText = finalText;
        }

        private double recall() {
            return recall;
        }

        private double tokenCoverage() {
            return tokenCoverage;
        }

        private long trustTimestampMs() {
            return trustTimestampMs < 0L ? Long.MAX_VALUE : trustTimestampMs;
        }

        private int preferredToneHz() {
            return preferredToneHz;
        }

        private int seedWpm() {
            return seedWpm;
        }

        private String strategyLabel() {
            return strategyLabel;
        }

        private String renderLine() {
            return String.format(
                    Locale.US,
                    "%s pref=%d seed=%d recall=%.3f token=%.3f trust=%d finalWpm=%d tone=%d/%d/%d chars=%d final=%s",
                    strategyLabel,
                    preferredToneHz,
                    seedWpm,
                    recall,
                    tokenCoverage,
                    trustTimestampMs,
                    finalEstimatedWpm,
                    targetToneHz,
                    acquisitionToneHz,
                    finalAdoptedToneHz,
                    totalCharacters,
                    finalText
            );
        }
    }
}
