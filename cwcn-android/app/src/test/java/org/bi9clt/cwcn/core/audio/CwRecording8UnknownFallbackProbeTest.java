package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.rx.RxUnknownFallbackResolver;
import org.bi9clt.cwcn.core.rx.RxUnknownFallbackSuggestion;
import org.bi9clt.cwcn.core.rx.RxUnknownFallbackTracker;
import org.junit.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertTrue;

public final class CwRecording8UnknownFallbackProbeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;
    private static final String EXPECTED_TEXT =
            "CQ CQ DE BG1XXX K BG1XXX DE JA1ABC K JA1ABC DE BG1XXX TNX FER CALL UR RST 599 QTH BEIJING NAME LEO HW? K BG1XXX DE JA1ABC FB OM UR RST 579 QTH TOKYO NAME KEN BK TNX QSO 73 DE BG1XXX SK";

    @Test
    public void printRecording8UnknownFallbackCoverage() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(8).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (8)"));

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                        wavFile,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        RxUnknownFallbackTracker tracker = new RxUnknownFallbackTracker();
        LinkedHashMap<String, SequenceStats> sequenceStats = new LinkedHashMap<>();
        int unknownOccurrences = 0;
        int resolvedOccurrences = 0;

        for (CwDecodeEvent decodeEvent : detailed.decodeEvents()) {
            tracker.process(decodeEvent);
            if (decodeEvent.type() != CwDecodeEvent.Type.CHARACTER_DECODED
                    || !decodeEvent.unknownCharacter()) {
                continue;
            }

            unknownOccurrences += 1;
            String sequence = sanitize(decodeEvent.sourceSequence());
            String fallback = sanitizeNullable(RxUnknownFallbackResolver.resolveUnknownSequenceBestEffort(sequence));
            if (fallback != null) {
                resolvedOccurrences += 1;
            }
            SequenceStats stats = sequenceStats.computeIfAbsent(
                    sequence,
                    ignored -> new SequenceStats(sequence)
            );
            stats.occurrences += 1;
            if (fallback != null) {
                stats.resolvedOccurrences += 1;
                if (stats.fallbackSuggestion == null) {
                    stats.fallbackSuggestion = fallback;
                }
            }
        }

        RxUnknownFallbackSuggestion suggestion = tracker.snapshot();
        String rawText = sanitize(suggestion.rawText());
        String fallbackText = sanitize(suggestion.hasSuggestion() ? suggestion.suggestedText() : suggestion.rawText());
        double rawRecall = charRecall(EXPECTED_TEXT, rawText);
        double fallbackRecall = charRecall(EXPECTED_TEXT, fallbackText);

        System.out.println("==== recording8 unknown fallback coverage ====");
        System.out.println(String.format(
                Locale.US,
                "unknownOccurrences=%d resolvedOccurrences=%d uniqueUnknownSeq=%d rawRecall=%.4f fallbackRecall=%.4f delta=%.4f",
                unknownOccurrences,
                resolvedOccurrences,
                sequenceStats.size(),
                rawRecall,
                fallbackRecall,
                fallbackRecall - rawRecall
        ));
        System.out.println("raw=" + rawText);
        System.out.println("fallback=" + fallbackText);

        System.out.println("---- unknown sequence coverage ----");
        for (Map.Entry<String, SequenceStats> entry : sequenceStats.entrySet()) {
            SequenceStats stats = entry.getValue();
            System.out.println(String.format(
                    Locale.US,
                    "seq=%s count=%d resolved=%d suggestion=%s",
                    stats.sequence,
                    stats.occurrences,
                    stats.resolvedOccurrences,
                    stats.fallbackSuggestion == null ? "-" : stats.fallbackSuggestion
            ));
        }

        assertTrue(true);
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

    private static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace('\u25A1', '?').trim();
        return normalized.isEmpty() ? "(empty)" : normalized;
    }

    private static String sanitizeNullable(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.replace('\u25A1', '?').trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static final class SequenceStats {
        private final String sequence;
        private int occurrences;
        private int resolvedOccurrences;
        private String fallbackSuggestion;

        private SequenceStats(String sequence) {
            this.sequence = sequence;
        }
    }
}
