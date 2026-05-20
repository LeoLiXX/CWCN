package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertTrue;

public final class CwDirtyCaseMidstreamPreferredToneSwitchProbeTest {
    private static final int BASE_PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;
    private static final long[] SWITCH_AT_MS = new long[]{576L, 896L, 2000L, 4000L};
    private static final int[] SWITCH_TO_HZ = new int[]{640, 740, 760};

    @Test
    public void printDirtyCaseMidstreamPreferredToneSwitchReplay() throws Exception {
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
                        sourceLabel + "-midstream-switch-baseline",
                        frames,
                        BASE_PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        System.out.println("==== " + sourceLabel + " midstream preferred-tone switch ====");
        printDetailedResult("BASELINE_HYBRID", BASE_PREFERRED_TONE_HZ, expectedText, baselineHybrid);

        for (long switchAtMs : SWITCH_AT_MS) {
            for (int switchToHz : SWITCH_TO_HZ) {
                LocalAudioDecodeTestSupport.OfflineDetailedProbeResult switchedHybrid =
                        LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeWithFrameHook(
                                sourceLabel + "-switch-" + switchAtMs + "-" + switchToHz,
                                frames,
                                BASE_PREFERRED_TONE_HZ,
                                SEED_WPM,
                                SQL_PERCENT,
                                false,
                                new PreferredToneSwitchFrameHook(switchAtMs, switchToHz),
                                CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                        );
                printDetailedResult(
                        "SWITCH@" + switchAtMs + "MS",
                        switchToHz,
                        expectedText,
                        switchedHybrid
                );
            }
        }
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

    private static final class PreferredToneSwitchFrameHook
            implements LocalAudioDecodeTestSupport.LiveLikeFrameHook {
        private final long switchAtMs;
        private final int switchToHz;
        private boolean switched;

        private PreferredToneSwitchFrameHook(long switchAtMs, int switchToHz) {
            this.switchAtMs = switchAtMs;
            this.switchToHz = switchToHz;
        }

        @Override
        public void beforeProcessFrame(AudioFrame frame, CwSignalProcessor signalProcessor, int frameIndex) {
            if (switched || frame == null || signalProcessor == null) {
                return;
            }
            if (frame.capturedAtMs() >= switchAtMs) {
                signalProcessor.setPreferredToneFrequencyHz(switchToHz);
                switched = true;
            }
        }
    }
}
