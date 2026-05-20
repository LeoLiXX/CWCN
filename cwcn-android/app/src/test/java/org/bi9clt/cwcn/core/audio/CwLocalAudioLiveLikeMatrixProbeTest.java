package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

public final class CwLocalAudioLiveLikeMatrixProbeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;
    private static final Pattern RECORDING_SUFFIX_PATTERN = Pattern.compile("\\((\\d+)\\)$");

    private static final Map<String, CaseExpectation> EXPECTATIONS = buildExpectations();

    @Test
    public void printCurrentLiveLikeMatrixForAllLocalAudioCases() throws Exception {
        List<CaseSummary> summaries = new ArrayList<>();
        for (Path wavFile : LocalAudioDecodeTestSupport.listConvertedWavFiles()) {
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                    LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                            wavFile,
                            PREFERRED_TONE_HZ,
                            SEED_WPM,
                            SQL_PERCENT,
                            false,
                            CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                    );
            summaries.add(summarizeCase(caseAlias(detailed.probeResult().sourceLabel()), detailed));
        }

        Path captureWav = LocalAudioDecodeTestSupport.findTraceDir().resolve("capture.wav");
        if (Files.isRegularFile(captureWav)) {
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                    LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                            "capture.wav",
                            LocalAudioDecodeTestSupport.normalizeFramesToZero(
                                    LocalAudioDecodeTestSupport.loadFramesFromWavFile(captureWav)
                            ),
                            PREFERRED_TONE_HZ,
                            SEED_WPM,
                            SQL_PERCENT,
                            false,
                            CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                    );
            summaries.add(0, summarizeCase("capture.wav", detailed));
        }

        System.out.println("==== live-like local audio matrix ====");
        for (CaseSummary summary : summaries) {
            System.out.println(summary.renderCompactLine());
        }

        System.out.println();
        System.out.println("==== grouped by current acceptability ====");
        printBucket("exact-clean", summaries, CaseStatus.EXACT, CaseStatus.CLEAN);
        printBucket("usable", summaries, CaseStatus.USABLE);
        printBucket("dirty", summaries, CaseStatus.DIRTY);
        printBucket("bad", summaries, CaseStatus.BAD);
        printBucket("unscoped", summaries, CaseStatus.UNSCOPED);

        System.out.println();
        System.out.println("==== per-case detail ====");
        for (CaseSummary summary : summaries) {
            System.out.println(summary.renderDetailBlock());
        }

        assertTrue("Expected at least one live-like case", !summaries.isEmpty());
    }

    private static CaseSummary summarizeCase(
            String alias,
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed
    ) {
        CaseExpectation expectation = EXPECTATIONS.get(alias);
        String finalText = sanitize(detailed.probeResult().decodedText());
        String rawText = sanitize(textAtOrBefore(detailed.rawDecodeEvents(), detailed.flushTimestampMs()));
        String stableText = sanitize(textAtOrBefore(detailed.stableAcceptedDecodeEvents(), detailed.flushTimestampMs()));
        double recall = expectation == null || expectation.expectedText == null
                ? -1.0d
                : charRecall(expectation.expectedText, finalText);
        boolean exact = expectation != null
                && expectation.expectedText != null
                && canonicalize(expectation.expectedText).equals(canonicalize(finalText));
        int turnCount = countTurns(detailed.turnTransitionTraces());
        long firstTrustOffsetMs = firstTrustedOffsetMs(detailed);
        double trustedDotMs = firstTrustedDotMs(detailed);
        CaseStatus status = classify(expectation, recall, exact);

        return new CaseSummary(
                alias,
                expectation,
                status,
                recall,
                exact,
                turnCount,
                firstTrustOffsetMs,
                trustedDotMs,
                detailed.probeResult().decoderSnapshot().totalCharacters(),
                detailed.probeResult().signalSnapshot().targetToneFrequencyHz(),
                detailed.probeResult().signalSnapshot().effectiveAcquisitionWinnerFrequencyHz(),
                detailed.probeResult().signalSnapshot().effectiveFinalAdoptedFrequencyHz(),
                detailed.probeResult().timingSnapshot().estimatedWpm(),
                detailed.stableRejectCounts(),
                finalText,
                stableText,
                rawText
        );
    }

    private static void printBucket(String label, List<CaseSummary> summaries, CaseStatus... statuses) {
        List<String> names = new ArrayList<>();
        for (CaseSummary summary : summaries) {
            for (CaseStatus status : statuses) {
                if (summary.status == status) {
                    names.add(summary.alias);
                    break;
                }
            }
        }
        System.out.println(label + ": " + (names.isEmpty() ? "(none)" : String.join(", ", names)));
    }

    private static CaseStatus classify(CaseExpectation expectation, double recall, boolean exact) {
        if (exact) {
            return CaseStatus.EXACT;
        }
        if (expectation == null || expectation.expectedText == null) {
            return CaseStatus.UNSCOPED;
        }
        if (recall >= 0.90d) {
            return CaseStatus.CLEAN;
        }
        if (recall >= 0.75d) {
            return CaseStatus.USABLE;
        }
        if (recall >= 0.55d) {
            return CaseStatus.DIRTY;
        }
        return CaseStatus.BAD;
    }

    private static int countTurns(List<LocalAudioDecodeTestSupport.TurnTransitionTrace> traces) {
        int count = 0;
        for (LocalAudioDecodeTestSupport.TurnTransitionTrace trace : traces) {
            if (trace != null
                    && trace.kind() == LocalAudioDecodeTestSupport.TurnTransitionTrace.Kind.START) {
                count += 1;
            }
        }
        return count;
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

    private static String textAtOrBefore(List<CwDecodeEvent> decodeEvents, long timestampMs) {
        String latestText = "";
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            if (decodeEvent != null && decodeEvent.timestampMs() <= timestampMs) {
                latestText = decodeEvent.outputText();
            } else if (decodeEvent != null && decodeEvent.timestampMs() > timestampMs) {
                break;
            }
        }
        return latestText == null ? "" : latestText;
    }

    private static String caseAlias(String sourceLabel) {
        if (sourceLabel == null || sourceLabel.trim().isEmpty()) {
            return "(unknown)";
        }
        String trimmed = sourceLabel.trim();
        String lower = trimmed.toLowerCase(Locale.US);
        if (lower.contains("capture.wav")) {
            return "capture.wav";
        }
        Matcher matcher = RECORDING_SUFFIX_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return "recording(" + matcher.group(1) + ")";
        }
        if (containsNonAscii(trimmed)) {
            return "recording";
        }
        return trimmed;
    }

    private static boolean containsNonAscii(String text) {
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) > 127) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, CaseExpectation> buildExpectations() {
        LinkedHashMap<String, CaseExpectation> expectations = new LinkedHashMap<>();
        expectations.put(
                "capture.wav",
                new CaseExpectation(
                        "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 700 24WPM PSE K."
                                + "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 700 24WPM PSE K."
                                + "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 700 24WPM PSE K.",
                        "three natural turns"
                )
        );
        expectations.put("20260427_222505", new CaseExpectation(
                "BI9CLT BI9CLT DE BI9CMS BI9CMS PSE K",
                "known weak similarity/collision sample"
        ));
        expectations.put("20260427_224524", new CaseExpectation(
                "CP CP DE B6 B6 LZ HOT LZ HOT KN",
                "known noisy short sample"
        ));
        expectations.put("recording", new CaseExpectation(
                "QRZ? DE BI3TUK KN.",
                "short QRZ sample"
        ));
        expectations.put("recording(2)", new CaseExpectation(
                "CQ DX CQ DX DE JV3VV JV3VV PAGE K. CQ DX CQ DX DE JV3VV JV3VV PAGE K.",
                "two-turn CQ DX sample"
        ));
        expectations.put("recording(3)", new CaseExpectation(
                "BI9CMS BI9CMS BI9CMS DE BI9CLT BI8DLT BI9CLT UR 599 5NN BK.",
                "similar-callsign exchange"
        ));
        expectations.put("recording(4)", new CaseExpectation(
                "CQ CQ CQ DE BI9CLT BI9CLT BI9CLT PSE K. CQ CQ CQ DE BI9CLT BI9CLT PSE K. CQ CQ CQ DE BI9CLT BI9CLT PSE K.",
                "repeated CQ loop"
        ));
        expectations.put("recording(5)", new CaseExpectation(
                "Q DE BI9",
                "very short fragment"
        ));
        expectations.put("recording(6)", new CaseExpectation(
                "CQ CQ CQ DE BI9CLT BI9CLT BI9CLT PSE K.",
                "clean CQ reference"
        ));
        expectations.put("recording(7)", new CaseExpectation(
                "QRZ? DE BI3TUK KN.",
                "short QRZ reference"
        ));
        expectations.put("recording(8)", new CaseExpectation(
                "CQ CQ DE BG1XXX K BG1XXX DE JA1ABC K JA1ABC DE BG1XXX TNX FER CALL UR RST 599 QTH BEIJING NAME LEO HW? K BG1XXX DE JA1ABC FB OM UR RST 579 QTH TOKYO NAME KEN BK TNX QSO 73 DE BG1XXX SK",
                "fast noisy long QSO"
        ));
        expectations.put("recording(9)", new CaseExpectation(
                "CQ CQ DE BG1XXX K BG1XXX DE JA1ABC K JA1ABC DE BG1XXX TNX FER CALL UR RST 599 QTH BEIJING NAME LEO HW? K BG1XXX DE JA1ABC FB OM UR RST 579 QTH TOKYO NAME KEN BK TNX QSO 73 DE BG1XXX SK",
                "long QSO variant"
        ));
        expectations.put("recording(10)", new CaseExpectation(
                "CQ CQ DE BG1XXX K BG1XXX DE JA1ABC K JA1ABC DE BG1XXX TNX FER CALL UR RST 599 QTH BEIJING NAME LEO HW? K BG1XXX DE JA1ABC FB OM UR RST 579 QTH TOKYO NAME KEN BK TNX QSO 73 DE BG1XXX SK",
                "long QSO variant"
        ));
        expectations.put("recording(11)", new CaseExpectation(
                "CQ CQ DE BG1XXX K BG1XXX DE JA1ABC K JA1ABC DE BG1XXX TNX FER CALL UR RST 599 QTH BEIJING NAME LEO HW? K BG1XXX DE JA1ABC FB OM UR RST 579 QTH TOKYO NAME KEN BK TNX QSO 73 DE BG1XXX SK",
                "long QSO strong variant"
        ));
        expectations.put("recording(12)", new CaseExpectation(
                "CQ CQ CQ DE BI9CMS BI9CMS IN 700 PSE K.",
                "single CQ with 700"
        ));
        expectations.put("recording(13)", new CaseExpectation(
                "CQ CQ CQ DE BI9CLT BI9CLT BI9CLT IN 600 PSE K.",
                "single CQ with 600"
        ));
        expectations.put("recording(14)", new CaseExpectation(
                "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 800 PSE K.",
                "single CQ with 800"
        ));
        expectations.put("recording(15)", new CaseExpectation(
                "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 800 24WPM PSE K.",
                "single CQ with 800 / 24WPM"
        ));
        expectations.put("recording(16)", new CaseExpectation(
                "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 700 24WPM PSE K.",
                "single CQ with 700 / 24WPM"
        ));
        return expectations;
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

    private enum CaseStatus {
        EXACT,
        CLEAN,
        USABLE,
        DIRTY,
        BAD,
        UNSCOPED
    }

    private static final class CaseExpectation {
        private final String expectedText;
        private final String note;

        private CaseExpectation(String expectedText, String note) {
            this.expectedText = expectedText;
            this.note = note;
        }
    }

    private static final class CaseSummary {
        private final String alias;
        private final CaseExpectation expectation;
        private final CaseStatus status;
        private final double recall;
        private final boolean exact;
        private final int turnCount;
        private final long firstTrustOffsetMs;
        private final double trustedDotMs;
        private final int characterCount;
        private final int trackedToneHz;
        private final int acquisitionToneHz;
        private final int finalToneHz;
        private final int estimatedWpm;
        private final Map<String, Integer> stableRejectCounts;
        private final String finalText;
        private final String stableText;
        private final String rawText;

        private CaseSummary(
                String alias,
                CaseExpectation expectation,
                CaseStatus status,
                double recall,
                boolean exact,
                int turnCount,
                long firstTrustOffsetMs,
                double trustedDotMs,
                int characterCount,
                int trackedToneHz,
                int acquisitionToneHz,
                int finalToneHz,
                int estimatedWpm,
                Map<String, Integer> stableRejectCounts,
                String finalText,
                String stableText,
                String rawText
        ) {
            this.alias = alias;
            this.expectation = expectation;
            this.status = status;
            this.recall = recall;
            this.exact = exact;
            this.turnCount = turnCount;
            this.firstTrustOffsetMs = firstTrustOffsetMs;
            this.trustedDotMs = trustedDotMs;
            this.characterCount = characterCount;
            this.trackedToneHz = trackedToneHz;
            this.acquisitionToneHz = acquisitionToneHz;
            this.finalToneHz = finalToneHz;
            this.estimatedWpm = estimatedWpm;
            this.stableRejectCounts = stableRejectCounts;
            this.finalText = finalText;
            this.stableText = stableText;
            this.rawText = rawText;
        }

        private String renderCompactLine() {
            return String.format(
                    Locale.US,
                    "%-16s status=%-8s recall=%-5s turns=%d trust=%-8s dot=%-6s chars=%-4d tone=%3d/%3d/%3d wpm=%-3d note=%s",
                    alias,
                    status.name(),
                    recall < 0.0d ? "-" : String.format(Locale.US, "%.3f", recall),
                    turnCount,
                    firstTrustOffsetMs < 0L ? "-" : firstTrustOffsetMs + "ms",
                    trustedDotMs <= 0.0d ? "-" : String.format(Locale.US, "%.1f", trustedDotMs),
                    characterCount,
                    trackedToneHz,
                    acquisitionToneHz,
                    finalToneHz,
                    estimatedWpm,
                    expectation == null ? "-" : expectation.note
            );
        }

        private String renderDetailBlock() {
            StringBuilder builder = new StringBuilder();
            builder.append("---- ").append(alias).append(" [").append(status.name()).append("] ----\n");
            if (expectation != null) {
                builder.append("expected=").append(sanitize(expectation.expectedText)).append('\n');
            }
            builder.append("final=").append(finalText).append('\n');
            if (!stableText.equals(finalText)) {
                builder.append("stable=").append(stableText).append('\n');
            }
            if (!rawText.equals(finalText) && !rawText.equals(stableText)) {
                builder.append("raw=").append(rawText).append('\n');
            }
            builder.append(
                    String.format(
                            Locale.US,
                            "metrics: recall=%s exact=%s turns=%d trust=%s dot=%s chars=%d tone=%d/%d/%d wpm=%d rejects=%s\n",
                            recall < 0.0d ? "-" : String.format(Locale.US, "%.3f", recall),
                            exact,
                            turnCount,
                            firstTrustOffsetMs < 0L ? "-" : firstTrustOffsetMs + "ms",
                            trustedDotMs <= 0.0d ? "-" : String.format(Locale.US, "%.1fms", trustedDotMs),
                            characterCount,
                            trackedToneHz,
                            acquisitionToneHz,
                            finalToneHz,
                            estimatedWpm,
                            stableRejectCounts
                    )
            );
            return builder.toString();
        }
    }
}
