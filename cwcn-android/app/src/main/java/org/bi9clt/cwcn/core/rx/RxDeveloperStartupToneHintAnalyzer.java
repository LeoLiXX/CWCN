package org.bi9clt.cwcn.core.rx;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.audio.AudioFrame;
import org.bi9clt.cwcn.core.spectrum.AudioSpectrumAnalyzer;
import org.bi9clt.cwcn.core.spectrum.AudioSpectrumSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Developer-only startup hint analyzer.
 *
 * <p>This helper intentionally stays outside shared replay semantics. It inspects only the
 * startup raw spectrum and may offer a conservative fixed-tone suggestion for human debugging.
 * It must not be treated as verified RX owner logic.</p>
 */
public final class RxDeveloperStartupToneHintAnalyzer {
    private static final long STARTUP_WINDOW_MS = 2500L;
    private static final int FAR_CANDIDATE_MIN_OFFSET_HZ = 20;
    private static final int FAR_CANDIDATE_MAX_ABSOLUTE_OFFSET_HZ = 120;
    private static final int ACCEPTED_MIN_UPSIDE_OFFSET_HZ = 30;
    private static final int ACCEPTED_MAX_UPSIDE_OFFSET_HZ = 70;
    private static final int CLUSTER_WINDOW_HZ = 20;
    private static final int MAX_CLUSTER_SPAN_HZ = 30;
    private static final int MIN_SUPPORT_FRAMES = 3;
    private static final double MIN_PEAK_OVER_NOISE_RATIO = 2.10d;
    private static final double MIN_PEAK_OVER_PREFERRED_RATIO = 1.08d;
    private static final double MIN_PEAK_SHARE_OF_GLOBAL = 0.90d;
    private static final double MIN_CLUSTER_DOMINANCE_RATIO = 1.12d;

    private final AudioSpectrumAnalyzer spectrumAnalyzer = new AudioSpectrumAnalyzer();

    public Result analyze(@Nullable List<AudioFrame> frames, int preferredToneHz) {
        if (preferredToneHz <= 0) {
            return Result.rejected("REJECT_INVALID_PREFERRED_TONE");
        }
        if (frames == null || frames.isEmpty()) {
            return Result.rejected("REJECT_NO_FRAMES");
        }

        spectrumAnalyzer.reset();
        Map<Integer, Double> weightByFrequencyHz = new TreeMap<>();
        Map<Integer, Integer> supportByFrequencyHz = new TreeMap<>();
        long firstTimestampMs = Long.MIN_VALUE;

        for (AudioFrame frame : frames) {
            if (frame == null) {
                continue;
            }
            if (firstTimestampMs == Long.MIN_VALUE) {
                firstTimestampMs = frame.capturedAtMs();
            }
            if ((frame.capturedAtMs() - firstTimestampMs) > STARTUP_WINDOW_MS) {
                break;
            }
            AudioSpectrumSnapshot snapshot = spectrumAnalyzer.process(
                    frame,
                    preferredToneHz,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    "NONE",
                    "NONE",
                    false,
                    false,
                    0,
                    "NONE"
            );
            FrameCandidate candidate = extractFrameCandidate(snapshot, preferredToneHz);
            if (candidate == null) {
                continue;
            }
            weightByFrequencyHz.merge(candidate.frequencyHz, candidate.weight, Double::sum);
            supportByFrequencyHz.merge(candidate.frequencyHz, 1, Integer::sum);
        }

        if (weightByFrequencyHz.isEmpty()) {
            return Result.rejected("REJECT_NO_DOMINANT_STARTUP_CLUSTER");
        }

        List<FrequencyCluster> clusters = buildClusters(weightByFrequencyHz, supportByFrequencyHz);
        FrequencyCluster bestCluster = clusters.stream()
                .max(Comparator
                        .comparingDouble(FrequencyCluster::totalWeight)
                        .thenComparingInt(FrequencyCluster::supportFrames))
                .orElse(null);
        if (bestCluster == null) {
            return Result.rejected("REJECT_NO_DOMINANT_STARTUP_CLUSTER");
        }

        FrequencyCluster runnerUpCluster = clusters.stream()
                .filter(cluster -> cluster != bestCluster)
                .max(Comparator
                        .comparingDouble(FrequencyCluster::totalWeight)
                        .thenComparingInt(FrequencyCluster::supportFrames))
                .orElse(null);

        int suggestedToneHz = roundToNearest10Hz(bestCluster.weightedCenterHz());
        int offsetHz = suggestedToneHz - preferredToneHz;
        double dominanceRatio = runnerUpCluster == null || runnerUpCluster.totalWeight() <= 0.0d
                ? Double.POSITIVE_INFINITY
                : bestCluster.totalWeight() / runnerUpCluster.totalWeight();

        Result base = new Result(
                false,
                suggestedToneHz,
                bestCluster.supportFrames(),
                bestCluster.totalWeight(),
                bestCluster.minFrequencyHz(),
                bestCluster.maxFrequencyHz(),
                dominanceRatio,
                bestCluster.describe()
        );

        if (bestCluster.supportFrames() < MIN_SUPPORT_FRAMES) {
            return base.withDecision("REJECT_INSUFFICIENT_SUPPORT");
        }
        if ((bestCluster.maxFrequencyHz() - bestCluster.minFrequencyHz()) > MAX_CLUSTER_SPAN_HZ) {
            return base.withDecision("REJECT_CLUSTER_TOO_WIDE");
        }
        if (runnerUpCluster != null && dominanceRatio < MIN_CLUSTER_DOMINANCE_RATIO) {
            return base.withDecision("REJECT_AMBIGUOUS_CLUSTER");
        }
        if (offsetHz < ACCEPTED_MIN_UPSIDE_OFFSET_HZ || offsetHz > ACCEPTED_MAX_UPSIDE_OFFSET_HZ) {
            return base.withDecision("REJECT_NON_UPPER_SIDE_OFFSET");
        }
        return base.withDecision("ACCEPT_CONSERVATIVE_UPPER_SIDE_CLUSTER").acceptedCopy();
    }

    @Nullable
    private FrameCandidate extractFrameCandidate(
            @Nullable AudioSpectrumSnapshot snapshot,
            int preferredToneHz
    ) {
        if (snapshot == null) {
            return null;
        }
        int[] frequenciesHz = snapshot.frequenciesHz();
        float[] magnitudes = snapshot.magnitudes();
        if (frequenciesHz == null || magnitudes == null || frequenciesHz.length == 0) {
            return null;
        }
        int candidateFrequencyHz = 0;
        float candidateMagnitude = 0.0f;
        for (int index = 0; index < frequenciesHz.length && index < magnitudes.length; index++) {
            int offsetHz = frequenciesHz[index] - preferredToneHz;
            if (offsetHz < FAR_CANDIDATE_MIN_OFFSET_HZ
                    || offsetHz > FAR_CANDIDATE_MAX_ABSOLUTE_OFFSET_HZ) {
                continue;
            }
            float magnitude = magnitudes[index];
            if (magnitude > candidateMagnitude) {
                candidateMagnitude = magnitude;
                candidateFrequencyHz = frequenciesHz[index];
            }
        }
        if (candidateFrequencyHz <= 0 || candidateMagnitude <= 0.0f) {
            return null;
        }

        float preferredMagnitude = magnitudeAtFrequency(snapshot, preferredToneHz);
        float globalPeakMagnitude = snapshot.peakMagnitude();
        float noiseFloorMagnitude = Math.max(1.0f, snapshot.noiseFloorMagnitude());
        if (candidateMagnitude < (noiseFloorMagnitude * MIN_PEAK_OVER_NOISE_RATIO)) {
            return null;
        }
        if (preferredMagnitude > 0.0f
                && candidateMagnitude < (preferredMagnitude * MIN_PEAK_OVER_PREFERRED_RATIO)) {
            return null;
        }
        if (globalPeakMagnitude > 0.0f
                && candidateMagnitude < (globalPeakMagnitude * MIN_PEAK_SHARE_OF_GLOBAL)) {
            return null;
        }
        return new FrameCandidate(
                candidateFrequencyHz,
                candidateMagnitude * (candidateMagnitude / noiseFloorMagnitude)
        );
    }

    private float magnitudeAtFrequency(AudioSpectrumSnapshot snapshot, int targetFrequencyHz) {
        int[] frequenciesHz = snapshot.frequenciesHz();
        float[] magnitudes = snapshot.magnitudes();
        if (frequenciesHz == null || magnitudes == null || frequenciesHz.length == 0) {
            return 0.0f;
        }
        int bestIndex = 0;
        int smallestOffsetHz = Integer.MAX_VALUE;
        for (int index = 0; index < frequenciesHz.length && index < magnitudes.length; index++) {
            int offsetHz = Math.abs(frequenciesHz[index] - targetFrequencyHz);
            if (offsetHz < smallestOffsetHz) {
                smallestOffsetHz = offsetHz;
                bestIndex = index;
            }
        }
        return magnitudes[bestIndex];
    }

    private List<FrequencyCluster> buildClusters(
            Map<Integer, Double> weightByFrequencyHz,
            Map<Integer, Integer> supportByFrequencyHz
    ) {
        ArrayList<FrequencyCluster> clusters = new ArrayList<>();
        FrequencyCluster current = null;
        for (Map.Entry<Integer, Double> entry : weightByFrequencyHz.entrySet()) {
            int frequencyHz = entry.getKey();
            double weight = entry.getValue();
            int supportFrames = supportByFrequencyHz.getOrDefault(frequencyHz, 0);
            if (current == null
                    || Math.abs(frequencyHz - current.lastFrequencyHz()) > CLUSTER_WINDOW_HZ) {
                current = new FrequencyCluster();
                clusters.add(current);
            }
            current.add(frequencyHz, weight, supportFrames);
        }
        return clusters;
    }

    private int roundToNearest10Hz(double frequencyHz) {
        return (int) (Math.round(frequencyHz / 10.0d) * 10L);
    }

    private static final class FrameCandidate {
        private final int frequencyHz;
        private final double weight;

        private FrameCandidate(int frequencyHz, double weight) {
            this.frequencyHz = frequencyHz;
            this.weight = Math.max(0.0d, weight);
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

        private int minFrequencyHz() {
            return minFrequencyHz;
        }

        private int maxFrequencyHz() {
            return maxFrequencyHz;
        }

        private int lastFrequencyHz() {
            return lastFrequencyHz;
        }

        private int supportFrames() {
            return supportFrames;
        }

        private double totalWeight() {
            return totalWeight;
        }

        private double weightedCenterHz() {
            if (totalWeight <= 0.0d) {
                return 0.0d;
            }
            return weightedFrequencySum / totalWeight;
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

    public static final class Result {
        private final boolean accepted;
        private final int suggestedToneHz;
        private final int supportFrames;
        private final double totalWeight;
        private final int minClusterFrequencyHz;
        private final int maxClusterFrequencyHz;
        private final double dominanceRatio;
        private final String clusterSummary;
        private final String decisionCode;

        private Result(
                boolean accepted,
                int suggestedToneHz,
                int supportFrames,
                double totalWeight,
                int minClusterFrequencyHz,
                int maxClusterFrequencyHz,
                double dominanceRatio,
                String clusterSummary
        ) {
            this(
                    accepted,
                    suggestedToneHz,
                    supportFrames,
                    totalWeight,
                    minClusterFrequencyHz,
                    maxClusterFrequencyHz,
                    dominanceRatio,
                    clusterSummary,
                    "REJECT_UNKNOWN"
            );
        }

        private Result(
                boolean accepted,
                int suggestedToneHz,
                int supportFrames,
                double totalWeight,
                int minClusterFrequencyHz,
                int maxClusterFrequencyHz,
                double dominanceRatio,
                String clusterSummary,
                String decisionCode
        ) {
            this.accepted = accepted;
            this.suggestedToneHz = Math.max(0, suggestedToneHz);
            this.supportFrames = Math.max(0, supportFrames);
            this.totalWeight = Math.max(0.0d, totalWeight);
            this.minClusterFrequencyHz = Math.max(0, minClusterFrequencyHz);
            this.maxClusterFrequencyHz = Math.max(0, maxClusterFrequencyHz);
            this.dominanceRatio = dominanceRatio;
            this.clusterSummary = clusterSummary == null ? "" : clusterSummary;
            this.decisionCode = decisionCode == null ? "REJECT_UNKNOWN" : decisionCode;
        }

        public static Result rejected(String decisionCode) {
            return new Result(
                    false,
                    0,
                    0,
                    0.0d,
                    0,
                    0,
                    0.0d,
                    "",
                    decisionCode
            );
        }

        private Result withDecision(String decisionCode) {
            return new Result(
                    accepted,
                    suggestedToneHz,
                    supportFrames,
                    totalWeight,
                    minClusterFrequencyHz,
                    maxClusterFrequencyHz,
                    dominanceRatio,
                    clusterSummary,
                    decisionCode
            );
        }

        private Result acceptedCopy() {
            return new Result(
                    true,
                    suggestedToneHz,
                    supportFrames,
                    totalWeight,
                    minClusterFrequencyHz,
                    maxClusterFrequencyHz,
                    dominanceRatio,
                    clusterSummary,
                    decisionCode
            );
        }

        public boolean accepted() {
            return accepted;
        }

        public int suggestedToneHz() {
            return suggestedToneHz;
        }

        public int supportFrames() {
            return supportFrames;
        }

        public double totalWeight() {
            return totalWeight;
        }

        public int minClusterFrequencyHz() {
            return minClusterFrequencyHz;
        }

        public int maxClusterFrequencyHz() {
            return maxClusterFrequencyHz;
        }

        public double dominanceRatio() {
            return dominanceRatio;
        }

        public String clusterSummary() {
            return clusterSummary;
        }

        public String decisionCode() {
            return decisionCode;
        }

        public boolean hasCandidate() {
            return suggestedToneHz > 0 && !clusterSummary.isEmpty();
        }
    }
}
