package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertTrue;

public final class CwToneStartupHoldRetuneSweepProbeTest {
    private static final int SQL_PERCENT = 55;
    private static final long OPENING_WINDOW_MS = 1200L;
    private static final int OPENING_STABLE_BAND_HZ = 25;
    private static final int PREFERRED_TONE_DIRTY_CASE_HZ = 760;
    private static final int PREFERRED_TONE_AUTO_CASE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final long[] FALLBACK_DELAYS_MS = new long[]{0L, 48L, 96L, 160L, 240L, 320L};
    private static final long[] FIXED_HOLDS_MS = new long[]{0L, 96L, 240L, 480L, 960L};
    private static final int[] FIXED_WINDOWS_HZ = new int[]{30, 50};

    @Test
    public void printToneStartupHoldRetuneSweep() throws Exception {
        printHybridCase(
                "20260427_222505",
                "BI9CLT BI9CLT DE BI9CMS BI9CMS PSE K",
                PREFERRED_TONE_DIRTY_CASE_HZ
        );
        printHybridCase(
                "20260427_224524",
                "CP CP DE B6 B6 LZ HOT LZ HOT KN",
                PREFERRED_TONE_DIRTY_CASE_HZ
        );
        printHybridCase(
                "录音 (12)",
                "",
                PREFERRED_TONE_AUTO_CASE_HZ
        );

        printFixedHoldCase(
                "20260427_222505",
                "BI9CLT BI9CLT DE BI9CMS BI9CMS PSE K",
                PREFERRED_TONE_DIRTY_CASE_HZ
        );
        printFixedHoldCase(
                "20260427_224524",
                "CP CP DE B6 B6 LZ HOT LZ HOT KN",
                PREFERRED_TONE_DIRTY_CASE_HZ
        );
        printFixedHoldCase(
                "录音 (12)",
                "",
                PREFERRED_TONE_AUTO_CASE_HZ
        );
        assertTrue(true);
    }

    private static void printHybridCase(
            String sourceLabel,
            String expectedText,
            int preferredToneHz
    ) throws Exception {
        Path wavFile = requireWavFile(sourceLabel);
        List<AudioFrame> frames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );
        CwSignalProcessor.ExperimentalLockedRetuneGuardTuning baseline = null;
        CwSignalProcessor.ExperimentalLockedRetuneGuardTuning softer =
                new CwSignalProcessor.ExperimentalLockedRetuneGuardTuning(45, 30, 90, 3, 4, 2);
        CwSignalProcessor.ExperimentalLockedRetuneGuardTuning tighter =
                new CwSignalProcessor.ExperimentalLockedRetuneGuardTuning(30, 20, 70, 1, 2, 1);
        List<GuardProfile> profiles = Arrays.asList(
                new GuardProfile("BASE", baseline),
                new GuardProfile("SOFT", softer),
                new GuardProfile("TIGHT", tighter)
        );

        ArrayList<ProbeLine> lines = new ArrayList<>();
        for (long fallbackDelayMs : FALLBACK_DELAYS_MS) {
            for (GuardProfile profile : profiles) {
                LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                        LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeHybridBootstrapExperimental(
                                sourceLabel + "-hybrid-fb-" + fallbackDelayMs + "-" + profile.label,
                                frames,
                                preferredToneHz,
                                SEED_WPM,
                                SQL_PERCENT,
                                false,
                                fallbackDelayMs,
                                profile.tuning,
                                CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                        );
                lines.add(ProbeLine.hybrid(
                        fallbackDelayMs,
                        profile.label,
                        expectedText,
                        preferredToneHz,
                        detailed
                ));
            }
        }

        lines.sort(ProbeLine.ORDER);
        System.out.println("==== " + sourceLabel + " tone-startup hybrid sweep ====");
        printTopLines(lines, 10);
        printBaselineLine(lines, 48L, "BASE");
    }

    private static void printFixedHoldCase(
            String sourceLabel,
            String expectedText,
            int preferredToneHz
    ) throws Exception {
        Path wavFile = requireWavFile(sourceLabel);
        List<AudioFrame> frames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );
        CwSignalProcessor.ExperimentalLockedRetuneGuardTuning baseline = null;
        CwSignalProcessor.ExperimentalLockedRetuneGuardTuning tighter =
                new CwSignalProcessor.ExperimentalLockedRetuneGuardTuning(30, 20, 70, 1, 2, 1);
        List<GuardProfile> profiles = Arrays.asList(
                new GuardProfile("BASE", baseline),
                new GuardProfile("TIGHT", tighter)
        );

        ArrayList<ProbeLine> lines = new ArrayList<>();
        for (int fixedWindowHz : FIXED_WINDOWS_HZ) {
            for (long fixedHoldMs : FIXED_HOLDS_MS) {
                for (GuardProfile profile : profiles) {
                    LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                            LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeFixedHoldThenAutoExperimental(
                                    sourceLabel
                                            + "-hold-"
                                            + fixedHoldMs
                                            + "-win-"
                                            + fixedWindowHz
                                            + "-"
                                            + profile.label,
                                    frames,
                                    preferredToneHz,
                                    SEED_WPM,
                                    SQL_PERCENT,
                                    false,
                                    fixedHoldMs,
                                    fixedWindowHz,
                                    profile.tuning,
                                    CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                            );
                    lines.add(ProbeLine.fixedHold(
                            fixedHoldMs,
                            fixedWindowHz,
                            profile.label,
                            expectedText,
                            preferredToneHz,
                            detailed
                    ));
                }
            }
        }

        lines.sort(ProbeLine.ORDER);
        System.out.println("==== " + sourceLabel + " tone-startup fixed-hold sweep ====");
        printTopLines(lines, 10);
        printFixedBaselineLine(lines, 960L, 30, "BASE");
    }

    private static void printTopLines(List<ProbeLine> lines, int limit) {
        int count = Math.min(limit, lines.size());
        for (int index = 0; index < count; index++) {
            System.out.println(lines.get(index).render());
        }
    }

    private static void printBaselineLine(List<ProbeLine> lines, long fallbackDelayMs, String guardLabel) {
        for (ProbeLine line : lines) {
            if ("HYBRID".equals(line.modeLabel)
                    && line.fallbackDelayMs == fallbackDelayMs
                    && guardLabel.equals(line.guardLabel)) {
                System.out.println("baseline=" + line.render());
                return;
            }
        }
    }

    private static void printFixedBaselineLine(
            List<ProbeLine> lines,
            long fixedHoldMs,
            int fixedWindowHz,
            String guardLabel
    ) {
        for (ProbeLine line : lines) {
            if ("FIXED_HOLD".equals(line.modeLabel)
                    && line.fixedHoldMs == fixedHoldMs
                    && line.fixedWindowHz == fixedWindowHz
                    && guardLabel.equals(line.guardLabel)) {
                System.out.println("baseline=" + line.render());
                return;
            }
        }
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
            Arrays.fill(current, 0);
        }
        return previous[right.length()];
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

    private static long firstTurnStartTimestampMs(LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed) {
        for (LocalAudioDecodeTestSupport.TurnTransitionTrace trace : detailed.turnTransitionTraces()) {
            if (trace != null
                    && trace.kind() == LocalAudioDecodeTestSupport.TurnTransitionTrace.Kind.START) {
                return trace.timestampMs();
            }
        }
        return -1L;
    }

    private static OpeningToneMetrics computeOpeningToneMetrics(
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed,
            int preferredToneHz
    ) {
        long turnStartMs = firstTurnStartTimestampMs(detailed);
        long windowStartMs = turnStartMs >= 0L ? turnStartMs : 0L;
        long windowEndMs = windowStartMs + OPENING_WINDOW_MS;
        long firstUsableMs = -1L;
        long firstLockMs = -1L;
        int stableFrames = 0;
        int totalFrames = 0;
        int openingToneHzSum = 0;
        int firstUsableToneHz = 0;
        int lastOpeningToneHz = 0;

        for (LocalAudioDecodeTestSupport.FrameSignalTrace trace : detailed.frameSignalTraces()) {
            if (trace == null || trace.snapshot() == null) {
                continue;
            }
            long timestampMs = trace.timestampMs();
            if (timestampMs < windowStartMs) {
                continue;
            }
            if (timestampMs > windowEndMs) {
                break;
            }
            CwSignalSnapshot snapshot = trace.snapshot();
            int effectiveToneHz = snapshot.effectiveTrackedToneFrequencyHz();
            boolean usable = isUsableTone(snapshot, effectiveToneHz);
            if (usable) {
                if (firstUsableMs < 0L) {
                    firstUsableMs = timestampMs;
                    firstUsableToneHz = effectiveToneHz;
                }
                totalFrames += 1;
                openingToneHzSum += effectiveToneHz;
                if (Math.abs(effectiveToneHz - preferredToneHz) <= OPENING_STABLE_BAND_HZ) {
                    stableFrames += 1;
                }
                lastOpeningToneHz = effectiveToneHz;
            }
            if (firstLockMs < 0L && snapshot.targetToneLocked()) {
                firstLockMs = timestampMs;
            }
        }

        int openingAverageToneHz = totalFrames == 0 ? 0 : Math.round(openingToneHzSum / (float) totalFrames);
        double stableRatio = totalFrames == 0 ? 0.0d : stableFrames / (double) totalFrames;
        return new OpeningToneMetrics(
                firstUsableMs,
                firstLockMs,
                firstUsableToneHz,
                openingAverageToneHz,
                lastOpeningToneHz,
                stableRatio,
                totalFrames
        );
    }

    private static boolean isUsableTone(CwSignalSnapshot snapshot, int effectiveToneHz) {
        if (snapshot == null || effectiveToneHz <= 0) {
            return false;
        }
        return snapshot.targetToneLocked()
                || snapshot.consecutiveLockedFrames() >= 2
                || snapshot.recentLockedFrameRatio() >= 0.18d
                || snapshot.toneHypothesisConfidence() >= 0.26d;
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "(empty)";
        }
        String normalized = text.replace('\u25A1', '?').replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.isEmpty() ? "(empty)" : normalized;
    }

    private static final class GuardProfile {
        private final String label;
        private final CwSignalProcessor.ExperimentalLockedRetuneGuardTuning tuning;

        private GuardProfile(
                String label,
                CwSignalProcessor.ExperimentalLockedRetuneGuardTuning tuning
        ) {
            this.label = label;
            this.tuning = tuning;
        }
    }

    private static final class OpeningToneMetrics {
        private final long firstUsableMs;
        private final long firstLockMs;
        private final int firstUsableToneHz;
        private final int openingAverageToneHz;
        private final int lastOpeningToneHz;
        private final double stableRatio;
        private final int usableFrames;

        private OpeningToneMetrics(
                long firstUsableMs,
                long firstLockMs,
                int firstUsableToneHz,
                int openingAverageToneHz,
                int lastOpeningToneHz,
                double stableRatio,
                int usableFrames
        ) {
            this.firstUsableMs = firstUsableMs;
            this.firstLockMs = firstLockMs;
            this.firstUsableToneHz = firstUsableToneHz;
            this.openingAverageToneHz = openingAverageToneHz;
            this.lastOpeningToneHz = lastOpeningToneHz;
            this.stableRatio = stableRatio;
            this.usableFrames = usableFrames;
        }
    }

    private static final class ProbeLine {
        private static final Comparator<ProbeLine> ORDER = Comparator
                .comparingDouble(ProbeLine::score).reversed()
                .thenComparingLong(line -> line.metrics.firstUsableMs < 0L ? Long.MAX_VALUE : line.metrics.firstUsableMs)
                .thenComparingDouble(line -> line.recall).reversed();

        private final String modeLabel;
        private final long fallbackDelayMs;
        private final long fixedHoldMs;
        private final int fixedWindowHz;
        private final String guardLabel;
        private final double recall;
        private final long trustMs;
        private final int finalWpm;
        private final int finalTargetToneHz;
        private final int finalAcquisitionToneHz;
        private final int finalAdoptedToneHz;
        private final String finalText;
        private final OpeningToneMetrics metrics;

        private ProbeLine(
                String modeLabel,
                long fallbackDelayMs,
                long fixedHoldMs,
                int fixedWindowHz,
                String guardLabel,
                double recall,
                long trustMs,
                int finalWpm,
                int finalTargetToneHz,
                int finalAcquisitionToneHz,
                int finalAdoptedToneHz,
                String finalText,
                OpeningToneMetrics metrics
        ) {
            this.modeLabel = modeLabel;
            this.fallbackDelayMs = fallbackDelayMs;
            this.fixedHoldMs = fixedHoldMs;
            this.fixedWindowHz = fixedWindowHz;
            this.guardLabel = guardLabel;
            this.recall = recall;
            this.trustMs = trustMs;
            this.finalWpm = finalWpm;
            this.finalTargetToneHz = finalTargetToneHz;
            this.finalAcquisitionToneHz = finalAcquisitionToneHz;
            this.finalAdoptedToneHz = finalAdoptedToneHz;
            this.finalText = finalText;
            this.metrics = metrics;
        }

        private static ProbeLine hybrid(
                long fallbackDelayMs,
                String guardLabel,
                String expectedText,
                int preferredToneHz,
                LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed
        ) {
            return build(
                    "HYBRID",
                    fallbackDelayMs,
                    0L,
                    0,
                    guardLabel,
                    expectedText,
                    preferredToneHz,
                    detailed
            );
        }

        private static ProbeLine fixedHold(
                long fixedHoldMs,
                int fixedWindowHz,
                String guardLabel,
                String expectedText,
                int preferredToneHz,
                LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed
        ) {
            return build(
                    "FIXED_HOLD",
                    -1L,
                    fixedHoldMs,
                    fixedWindowHz,
                    guardLabel,
                    expectedText,
                    preferredToneHz,
                    detailed
            );
        }

        private static ProbeLine build(
                String modeLabel,
                long fallbackDelayMs,
                long fixedHoldMs,
                int fixedWindowHz,
                String guardLabel,
                String expectedText,
                int preferredToneHz,
                LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed
        ) {
            String finalText = sanitize(detailed.probeResult().decodedText());
            return new ProbeLine(
                    modeLabel,
                    fallbackDelayMs,
                    fixedHoldMs,
                    fixedWindowHz,
                    guardLabel,
                    charRecall(expectedText, finalText),
                    firstTrustTimestampMs(detailed),
                    detailed.probeResult().timingSnapshot().estimatedWpm(),
                    detailed.probeResult().signalSnapshot().targetToneFrequencyHz(),
                    detailed.probeResult().signalSnapshot().effectiveAcquisitionWinnerFrequencyHz(),
                    detailed.probeResult().signalSnapshot().effectiveFinalAdoptedFrequencyHz(),
                    finalText,
                    computeOpeningToneMetrics(detailed, preferredToneHz)
            );
        }

        private double score() {
            double usableBonus = metrics.firstUsableMs < 0L ? 0.0d : Math.max(0.0d, 1600.0d - metrics.firstUsableMs) / 1600.0d;
            double lockBonus = metrics.firstLockMs < 0L ? 0.0d : Math.max(0.0d, 2000.0d - metrics.firstLockMs) / 2000.0d;
            return (recall * 3.0d)
                    + (metrics.stableRatio * 2.0d)
                    + usableBonus
                    + (lockBonus * 0.7d)
                    + Math.min(metrics.usableFrames / 24.0d, 1.0d) * 0.5d;
        }

        private String render() {
            String paramSummary = "HYBRID".equals(modeLabel)
                    ? String.format(Locale.US, "fb=%d guard=%s", fallbackDelayMs, guardLabel)
                    : String.format(Locale.US, "hold=%d win=%d guard=%s", fixedHoldMs, fixedWindowHz, guardLabel);
            return String.format(
                    Locale.US,
                    "%s %s score=%.3f usable=%d@%d lock=%d avg=%d last=%d stable=%.2f frames=%d recall=%.3f trust=%d wpm=%d tone=%d/%d/%d text=%s",
                    modeLabel,
                    paramSummary,
                    score(),
                    metrics.firstUsableToneHz,
                    metrics.firstUsableMs,
                    metrics.firstLockMs,
                    metrics.openingAverageToneHz,
                    metrics.lastOpeningToneHz,
                    metrics.stableRatio,
                    metrics.usableFrames,
                    recall,
                    trustMs,
                    finalWpm,
                    finalTargetToneHz,
                    finalAcquisitionToneHz,
                    finalAdoptedToneHz,
                    finalText
            );
        }
    }
}
