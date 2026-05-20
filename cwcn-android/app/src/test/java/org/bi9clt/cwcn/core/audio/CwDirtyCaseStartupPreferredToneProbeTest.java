package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertTrue;

public final class CwDirtyCaseStartupPreferredToneProbeTest {
    private static final int BASE_PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;
    private static final long[] LEARNING_WINDOWS_MS = new long[]{2500L, 4000L, 6000L};
    private static final int MIN_FAR_OFFSET_HZ = 30;
    private static final int CLUSTER_WINDOW_HZ = 20;
    private static final double MIN_WIDE_SCORE_LEAD_RATIO = 1.05d;
    private static final double MIN_WIDE_TONE_LEAD_RATIO = 1.08d;
    private static final int MIN_SUPPORT_FRAMES = 3;

    @Test
    public void printDirtyCaseStartupPreferredToneReplay() throws Exception {
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
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult baselineHybrid =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        sourceLabel + "-startup-learn-baseline-hybrid",
                        frames,
                        BASE_PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        System.out.println("==== " + sourceLabel + " startup preferred-tone replay ====");
        printDetailedResult("BASELINE_HYBRID", BASE_PREFERRED_TONE_HZ, expectedText, baselineHybrid);

        for (long learningWindowMs : LEARNING_WINDOWS_MS) {
            LearnedToneCandidate learned = learnStrongFarWideTone(
                    baselineHybrid.frameSignalTraces(),
                    BASE_PREFERRED_TONE_HZ,
                    learningWindowMs
            );
            if (learned == null) {
                System.out.println(String.format(
                        Locale.US,
                        "LEARN window=%dms status=NO_DOMINANT_FAR_WIDE_TONE",
                        learningWindowMs
                ));
                continue;
            }

            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult learnedHybrid =
                    LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                            sourceLabel + "-startup-learn-hybrid-" + learningWindowMs,
                            frames,
                            learned.frequencyHz,
                            SEED_WPM,
                            SQL_PERCENT,
                            false,
                            CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                    );
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult learnedFixedUntilTrust =
                    LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeFixedUntilTrustThenAuto(
                            sourceLabel + "-startup-learn-fixed-until-trust-" + learningWindowMs,
                            frames,
                            learned.frequencyHz,
                            SEED_WPM,
                            SQL_PERCENT,
                            false,
                            CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                    );
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult learnedStaticFixed =
                    LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeWithTurnCarry(
                            sourceLabel + "-startup-learn-static-fixed-" + learningWindowMs,
                            frames,
                            learned.frequencyHz,
                            SEED_WPM,
                            SQL_PERCENT,
                            false,
                            false,
                            false,
                            false,
                            false,
                            false,
                            CwSignalProcessor.RxToneMode.FIXED_TONE,
                            CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                    );

            System.out.println(String.format(
                    Locale.US,
                    "LEARN window=%dms learned=%dHz support=%d weight=%.1f dominant=%s",
                    learningWindowMs,
                    learned.frequencyHz,
                    learned.supportFrames,
                    learned.totalWeight,
                    learned.summary
            ));
            printDetailedResult("LEARNED_HYBRID", learned.frequencyHz, expectedText, learnedHybrid);
            printDetailedResult("LEARNED_FIXED_UNTIL_TRUST", learned.frequencyHz, expectedText, learnedFixedUntilTrust);
            printDetailedResult("LEARNED_STATIC_FIXED", learned.frequencyHz, expectedText, learnedStaticFixed);
        }
    }

    private static LearnedToneCandidate learnStrongFarWideTone(
            List<LocalAudioDecodeTestSupport.FrameSignalTrace> traces,
            int basePreferredToneHz,
            long learningWindowMs
    ) {
        if (traces == null || traces.isEmpty()) {
            return null;
        }
        long startTimestampMs = traces.get(0).timestampMs();
        Map<Integer, Double> weightByFrequencyHz = new TreeMap<>();
        Map<Integer, Integer> supportByFrequencyHz = new TreeMap<>();
        for (LocalAudioDecodeTestSupport.FrameSignalTrace trace : traces) {
            if (trace == null || trace.snapshot() == null) {
                continue;
            }
            if ((trace.timestampMs() - startTimestampMs) > learningWindowMs) {
                break;
            }
            CwSignalSnapshot snapshot = trace.snapshot();
            int wideFrequencyHz = snapshot.wideScanWinnerFrequencyHz();
            if (wideFrequencyHz <= 0 || Math.abs(wideFrequencyHz - basePreferredToneHz) < MIN_FAR_OFFSET_HZ) {
                continue;
            }
            double wideScore = snapshot.wideScanWinnerSelectionScore();
            double preferredScore = snapshot.preferredWindowWinnerSelectionScore();
            double wideToneRms = snapshot.wideScanWinnerToneRms();
            double preferredToneRms = snapshot.preferredWindowWinnerToneRms();
            boolean explicitWideWinner = "WIDE_SCAN".equals(snapshot.acquisitionWinnerSource());
            boolean strongWideLead = wideScore > 0.0d
                    && wideScore >= (preferredScore * MIN_WIDE_SCORE_LEAD_RATIO);
            boolean strongWideTone = wideToneRms > 0.0d
                    && wideToneRms >= (preferredToneRms * MIN_WIDE_TONE_LEAD_RATIO);
            if (!explicitWideWinner && !strongWideLead && !strongWideTone) {
                continue;
            }
            double weight = Math.max(
                    Math.max(wideScore, wideToneRms),
                    snapshot.wideScanWinnerConfidence() * 100.0d
            );
            if (weight <= 0.0d) {
                continue;
            }
            weightByFrequencyHz.merge(wideFrequencyHz, weight, Double::sum);
            supportByFrequencyHz.merge(wideFrequencyHz, 1, Integer::sum);
        }

        if (weightByFrequencyHz.isEmpty()) {
            return null;
        }
        List<FrequencyCluster> clusters = buildClusters(weightByFrequencyHz, supportByFrequencyHz);
        FrequencyCluster best = clusters.stream()
                .filter(cluster -> cluster.supportFrames >= MIN_SUPPORT_FRAMES)
                .max(Comparator
                        .comparingDouble(FrequencyCluster::totalWeight)
                        .thenComparingInt(FrequencyCluster::supportFrames))
                .orElse(null);
        if (best == null) {
            return null;
        }
        int learnedFrequencyHz = (int) (Math.round(best.weightedCenterHz() / 10.0d) * 10L);
        return new LearnedToneCandidate(
                learnedFrequencyHz,
                best.supportFrames,
                best.totalWeight,
                best.describe()
        );
    }

    private static List<FrequencyCluster> buildClusters(
            Map<Integer, Double> weightByFrequencyHz,
            Map<Integer, Integer> supportByFrequencyHz
    ) {
        List<FrequencyCluster> clusters = new ArrayList<>();
        FrequencyCluster current = null;
        for (Map.Entry<Integer, Double> entry : weightByFrequencyHz.entrySet()) {
            int frequencyHz = entry.getKey();
            double weight = entry.getValue();
            int supportFrames = supportByFrequencyHz.getOrDefault(frequencyHz, 0);
            if (current == null
                    || Math.abs(frequencyHz - current.lastFrequencyHz) > CLUSTER_WINDOW_HZ) {
                current = new FrequencyCluster();
                clusters.add(current);
            }
            current.add(frequencyHz, weight, supportFrames);
        }
        return clusters;
    }

    private static void printDetailedResult(
            String label,
            int preferredToneHz,
            String expectedText,
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed
    ) {
        double recall = charRecall(expectedText, detailed.probeResult().decodedText());
        long trustTimestampMs = firstTrustTimestampMs(detailed);
        System.out.println(String.format(
                Locale.US,
                "%s pref=%d recall=%.3f trust=%d tone=%d/%d/%d wpm=%d chars=%d src=%s/%s final=%s",
                label,
                preferredToneHz,
                recall,
                trustTimestampMs,
                detailed.probeResult().signalSnapshot().targetToneFrequencyHz(),
                detailed.probeResult().signalSnapshot().effectiveAcquisitionWinnerFrequencyHz(),
                detailed.probeResult().signalSnapshot().effectiveFinalAdoptedFrequencyHz(),
                detailed.probeResult().timingSnapshot().estimatedWpm(),
                detailed.probeResult().decoderSnapshot().totalCharacters(),
                sanitizeInline(detailed.probeResult().signalSnapshot().acquisitionWinnerSource())
                        + ":" + sanitizeInline(detailed.probeResult().signalSnapshot().acquisitionDecisionDetail()),
                sanitizeInline(detailed.probeResult().signalSnapshot().finalAdoptedSource())
                        + ":" + sanitizeInline(detailed.probeResult().signalSnapshot().finalAdoptionDetail()),
                sanitize(detailed.probeResult().decodedText())
        ));
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

    private static String sanitizeInline(String text) {
        if (text == null) {
            return "-";
        }
        String normalized = text.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.isEmpty() ? "-" : normalized;
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

    private static final class LearnedToneCandidate {
        private final int frequencyHz;
        private final int supportFrames;
        private final double totalWeight;
        private final String summary;

        private LearnedToneCandidate(int frequencyHz, int supportFrames, double totalWeight, String summary) {
            this.frequencyHz = frequencyHz;
            this.supportFrames = supportFrames;
            this.totalWeight = totalWeight;
            this.summary = summary;
        }
    }

    private static final class FrequencyCluster {
        private int minFrequencyHz;
        private int maxFrequencyHz;
        private int lastFrequencyHz;
        private int supportFrames;
        private double totalWeight;
        private double weightedFrequencySum;

        private void add(int frequencyHz, double weight, int frequencySupportFrames) {
            if (supportFrames == 0) {
                minFrequencyHz = frequencyHz;
            }
            maxFrequencyHz = frequencyHz;
            lastFrequencyHz = frequencyHz;
            supportFrames += frequencySupportFrames;
            totalWeight += weight;
            weightedFrequencySum += frequencyHz * weight;
        }

        private double weightedCenterHz() {
            if (totalWeight <= 0.0d) {
                return 0.0d;
            }
            return weightedFrequencySum / totalWeight;
        }

        private double totalWeight() {
            return totalWeight;
        }

        private int supportFrames() {
            return supportFrames;
        }

        private String describe() {
            return String.format(
                    Locale.US,
                    "%d-%dHz center=%.1fHz",
                    minFrequencyHz,
                    maxFrequencyHz,
                    weightedCenterHz()
            );
        }
    }
}
