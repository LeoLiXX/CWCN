package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertTrue;

public final class CwReleasePressureHoldRepresentativeProbeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;

    @Test
    public void printRepresentativeReleasePressureHoldSweep() throws Exception {
        List<CaseSpec> cases = new ArrayList<>();
        cases.add(new CaseSpec(
                "recording16",
                requireWav("(16).wav"),
                "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 700 24WPM PSE K."
        ));
        cases.add(new CaseSpec(
                "recording(7)",
                requireWav("(7).wav"),
                "CQ CQ DE BG1XXX K BG1XXX DE JA1ABC K JA1ABC DE BG1XXX TNX FER CALL UR RST 599 QTH BEIJING NAME LEO HW? K BG1XXX DE JA1ABC FB OM UR RST 579 QTH TOKYO NAME KEN BK TNX QSO 73 DE BG1XXX SK"
        ));
        cases.add(new CaseSpec(
                "recording(12)",
                requireWav("(12).wav"),
                "CQ CQ CQ DE BI9CMS BI9CMS BI9CMS IN 700 PSE K."
        ));
        cases.add(new CaseSpec(
                "recording(15)",
                requireWav("(15).wav"),
                "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 800 24WPM PSE K."
        ));

        Path captureWav = LocalAudioDecodeTestSupport.findTraceDir().resolve("capture.wav");
        if (Files.isRegularFile(captureWav)) {
            cases.add(new CaseSpec(
                    "capture.wav",
                    captureWav,
                    "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 700 24WPM PSE K."
                            + "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 700 24WPM PSE K."
                            + "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 700 24WPM PSE K."
            ));
        }

        System.out.println("==== release-pressure-hold representative sweep ====");
        for (CaseSpec spec : cases) {
            List<AudioFrame> frames = loadNormalizedFrames(spec.wavFile());
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult baseline =
                    LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                            spec.alias() + "-baseline",
                            frames,
                            PREFERRED_TONE_HZ,
                            SEED_WPM,
                            SQL_PERCENT,
                            false,
                            false,
                            false,
                            false,
                            false,
                            CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                    );
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult holdEnabled =
                    LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                            spec.alias() + "-hold",
                            frames,
                            PREFERRED_TONE_HZ,
                            SEED_WPM,
                            SQL_PERCENT,
                            false,
                            false,
                            false,
                            false,
                            true,
                            CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                    );
            printSummary(spec, baseline, holdEnabled);
        }

        assertTrue(true);
    }

    private static void printSummary(
            CaseSpec spec,
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult baseline,
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult holdEnabled
    ) {
        String baselineText = sanitize(baseline.probeResult().decodedText());
        String holdText = sanitize(holdEnabled.probeResult().decodedText());
        double baselineRecall = charRecall(spec.expectedText(), baselineText);
        double holdRecall = charRecall(spec.expectedText(), holdText);
        int baselineKeyHits = countKeyHits(spec.alias(), baselineText);
        int holdKeyHits = countKeyHits(spec.alias(), holdText);

        System.out.println(String.format(
                Locale.US,
                "%s base=%.3f key=%d hold=%.3f key=%d delta=%.3f",
                spec.alias(),
                baselineRecall,
                baselineKeyHits,
                holdRecall,
                holdKeyHits,
                holdRecall - baselineRecall
        ));
        System.out.println("baseline=" + baselineText);
        System.out.println("hold=" + holdText);
    }

    private static int countKeyHits(String alias, String text) {
        String canonical = canonicalize(text);
        if ("recording16".equals(alias) || "capture.wav".equals(alias)) {
            return countOccurrences(canonical, "DEBI9CXC")
                    + countOccurrences(canonical, "24WPM")
                    + countOccurrences(canonical, "PSEK");
        }
        if ("recording(7)".equals(alias)) {
            return countOccurrences(canonical, "BG1XXX")
                    + countOccurrences(canonical, "JA1ABC")
                    + countOccurrences(canonical, "RST")
                    + countOccurrences(canonical, "SK");
        }
        if ("recording(12)".equals(alias)) {
            return countOccurrences(canonical, "BI9CMS")
                    + countOccurrences(canonical, "700")
                    + countOccurrences(canonical, "PSEK");
        }
        if ("recording(15)".equals(alias)) {
            return countOccurrences(canonical, "DEBI9CXC")
                    + countOccurrences(canonical, "24WPM")
                    + countOccurrences(canonical, "PSEK");
        }
        return 0;
    }

    private static List<AudioFrame> loadNormalizedFrames(Path wavFile) throws Exception {
        return LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );
    }

    private static Path requireWav(String suffix) throws Exception {
        return LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith(suffix))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for " + suffix));
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

    private static int countOccurrences(String text, String needle) {
        if (text == null || text.isEmpty() || needle == null || needle.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while (index >= 0) {
            index = text.indexOf(needle, index);
            if (index >= 0) {
                count += 1;
                index += needle.length();
            }
        }
        return count;
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
        }
        return previous[right.length()];
    }

    private static final class CaseSpec {
        private final String alias;
        private final Path wavFile;
        private final String expectedText;

        private CaseSpec(String alias, Path wavFile, String expectedText) {
            this.alias = alias;
            this.wavFile = wavFile;
            this.expectedText = expectedText;
        }

        private String alias() {
            return alias;
        }

        private Path wavFile() {
            return wavFile;
        }

        private String expectedText() {
            return expectedText;
        }
    }
}
