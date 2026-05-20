package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertTrue;

public final class CwManualFixedToneRepresentativeProbeTest {
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;
    private static final int[] FIXED_TONE_LEARNING_WINDOWS_HZ = new int[]{30, 50, 70};

    @Test
    public void printRepresentativeManualFixedToneWindowRegression() throws Exception {
        ArrayList<CaseProbe> cases = new ArrayList<>();
        cases.add(new CaseProbe(
                "recording(7)@700",
                "recording(7)",
                700,
                "QRZ? DE BI3TUK KN.",
                "DEBI3TUK",
                "BI3TUK",
                "KN"
        ));
        cases.add(new CaseProbe(
                "recording(7)@600",
                "recording(7)",
                600,
                "QRZ? DE BI3TUK KN.",
                "DEBI3TUK",
                "BI3TUK",
                "KN"
        ));
        cases.add(new CaseProbe(
                "recording(10)@500",
                "recording(10)",
                500,
                "CQ CQ DE BG1XXX K BG1XXX DE JA1ABC K JA1ABC DE BG1XXX TNX FER CALL UR RST 599 QTH BEIJING NAME LEO HW? K BG1XXX DE JA1ABC FB OM UR RST 579 QTH TOKYO NAME KEN BK TNX QSO 73 DE BG1XXX SK",
                "BG1XXX",
                "JA1ABC",
                "599",
                "BEIJING",
                "TOKYO",
                "73"
        ));
        cases.add(new CaseProbe(
                "recording(14)@800",
                "recording(14)",
                800,
                "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 800 PSE K.",
                "DEBI9CXC",
                "800",
                "PSEK"
        ));
        cases.add(new CaseProbe(
                "recording(15)@800",
                "recording(15)",
                800,
                "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 800 24WPM PSE K.",
                "DEBI9CXC",
                "800",
                "24WPM",
                "PSEK"
        ));

        for (CaseProbe probe : cases) {
            printCase(probe);
        }
        assertTrue(true);
    }

    private static void printCase(CaseProbe probe) throws Exception {
        Path wavFile = requireWavFile(probe.fixtureLabel);
        List<AudioFrame> frames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );

        System.out.println("==== " + probe.displayLabel + " manual-fixed representative sweep ====");
        printDetailedResult(
                "HYBRID_DEFAULT",
                probe,
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        probe.displayLabel + "-hybrid-default",
                        frames,
                        probe.preferredToneHz,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                )
        );
        for (int learningWindowHz : FIXED_TONE_LEARNING_WINDOWS_HZ) {
            printDetailedResult(
                    "STATIC_FIXED_WIN_" + learningWindowHz,
                    probe,
                    LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeWithFixedToneLearningWindow(
                            probe.displayLabel + "-static-fixed-win-" + learningWindowHz,
                            frames,
                            probe.preferredToneHz,
                            SEED_WPM,
                            SQL_PERCENT,
                            false,
                            learningWindowHz,
                            CwSignalProcessor.RxToneMode.FIXED_TONE,
                            CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                    )
            );
        }
    }

    private static void printDetailedResult(
            String label,
            CaseProbe probe,
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed
    ) {
        String decodedText = sanitize(detailed.probeResult().decodedText());
        String canonicalOutput = canonicalize(decodedText);
        double recall = charRecall(probe.expectedText, decodedText);
        int matchedCriticalTokens = countMatchedCriticalTokens(canonicalOutput, probe.criticalTokens);
        System.out.println(String.format(
                Locale.US,
                "%s pref=%d recall=%.3f key=%d/%d tone=%d/%d/%d wpm=%d chars=%d final=%s",
                label,
                probe.preferredToneHz,
                recall,
                matchedCriticalTokens,
                probe.criticalTokens.length,
                detailed.probeResult().signalSnapshot().targetToneFrequencyHz(),
                detailed.probeResult().signalSnapshot().effectiveAcquisitionWinnerFrequencyHz(),
                detailed.probeResult().signalSnapshot().effectiveFinalAdoptedFrequencyHz(),
                detailed.probeResult().timingSnapshot().estimatedWpm(),
                detailed.probeResult().decoderSnapshot().totalCharacters(),
                decodedText
        ));
    }

    private static int countMatchedCriticalTokens(String canonicalOutput, String[] criticalTokens) {
        int matched = 0;
        for (String token : criticalTokens) {
            if (token != null && !token.isEmpty() && canonicalOutput.contains(token)) {
                matched++;
            }
        }
        return matched;
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
        String normalizedCandidate = normalizeSourceLabel(candidateLabel);
        String normalizedSource = normalizeSourceLabel(sourceLabel);
        return normalizedCandidate.equals(normalizedSource)
                || normalizedCandidate.endsWith(normalizedSource)
                || normalizedSource.endsWith(normalizedCandidate);
    }

    private static String fileNameWithoutExtension(Path wavFile) {
        String fileName = wavFile.getFileName().toString();
        return fileName.toLowerCase(Locale.US).endsWith(".wav")
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;
    }

    private static String normalizeSourceLabel(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.US);
        normalized = normalized.replace("录音", "recording");
        normalized = normalized.replace(" ", "");
        normalized = normalized.replace("recording(", "recording");
        normalized = normalized.replace(")", "");
        normalized = normalized.replace("(", "");
        return normalized;
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

    private static final class CaseProbe {
        private final String displayLabel;
        private final String fixtureLabel;
        private final int preferredToneHz;
        private final String expectedText;
        private final String[] criticalTokens;

        private CaseProbe(
                String displayLabel,
                String fixtureLabel,
                int preferredToneHz,
                String expectedText,
                String... criticalTokens
        ) {
            this.displayLabel = displayLabel;
            this.fixtureLabel = fixtureLabel;
            this.preferredToneHz = preferredToneHz;
            this.expectedText = expectedText;
            this.criticalTokens = criticalTokens;
        }
    }
}
