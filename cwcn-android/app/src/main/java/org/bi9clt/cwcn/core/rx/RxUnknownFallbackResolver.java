package org.bi9clt.cwcn.core.rx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RxUnknownFallbackResolver {
    private static final Map<String, String> MORSE_TABLE = buildMorseTable();
    private static final int MAX_SEGMENTS_PER_UNKNOWN = 3;
    private static final int MAX_SYMBOLS_PER_SEGMENT = 6;
    private static final double EXTRA_SEGMENT_PENALTY = 1.15d;
    private static final double AMBIGUITY_MARGIN = 0.10d;

    private RxUnknownFallbackResolver() {
    }

    public static String resolveUnknownSequence(String sourceSequence) {
        String normalizedSequence = normalizeSequence(sourceSequence);
        if (normalizedSequence.isEmpty() || normalizedSequence.length() < 2) {
            return null;
        }

        ArrayList<Candidate> candidates = collectRankedCandidates(normalizedSequence);
        if (candidates.isEmpty()) {
            return null;
        }

        Candidate best = candidates.get(0);
        Candidate runnerUp = candidates.size() > 1 ? candidates.get(1) : null;

        if (runnerUp != null && Math.abs(best.score() - runnerUp.score()) < AMBIGUITY_MARGIN) {
            return null;
        }
        return best.decodedText();
    }

    public static String resolveUnknownSequenceBestEffort(String sourceSequence) {
        String normalizedSequence = normalizeSequence(sourceSequence);
        if (normalizedSequence.isEmpty() || normalizedSequence.length() < 2) {
            return null;
        }

        ArrayList<Candidate> candidates = collectRankedCandidates(normalizedSequence);
        return candidates.isEmpty() ? null : candidates.get(0).decodedText();
    }

    private static ArrayList<Candidate> collectRankedCandidates(String normalizedSequence) {
        ArrayList<Candidate> candidates = new ArrayList<>();
        collectCandidates(normalizedSequence, 0, new ArrayList<>(), candidates);
        Collections.sort(candidates, Comparator
                .comparingDouble(Candidate::score).reversed()
                .thenComparingInt(Candidate::segmentCount)
                .thenComparing(Candidate::segmentLengthKey)
                .thenComparing(Candidate::decodedText));
        return candidates;
    }

    private static void collectCandidates(
            String sourceSequence,
            int startIndex,
            List<String> segments,
            List<Candidate> candidates
    ) {
        if (segments.size() > MAX_SEGMENTS_PER_UNKNOWN) {
            return;
        }
        if (startIndex >= sourceSequence.length()) {
            if (segments.size() >= 2) {
                candidates.add(new Candidate(
                        decodeSegments(segments),
                        scoreSegments(segments),
                        buildSegmentLengthKey(segments),
                        segments.size()
                ));
            }
            return;
        }

        int maxEnd = Math.min(sourceSequence.length(), startIndex + MAX_SYMBOLS_PER_SEGMENT);
        for (int endIndex = startIndex + 1; endIndex <= maxEnd; endIndex++) {
            String segment = sourceSequence.substring(startIndex, endIndex);
            if (!MORSE_TABLE.containsKey(segment)) {
                continue;
            }
            segments.add(segment);
            collectCandidates(sourceSequence, endIndex, segments, candidates);
            segments.remove(segments.size() - 1);
        }
    }

    private static String decodeSegments(List<String> segments) {
        StringBuilder builder = new StringBuilder();
        for (String segment : segments) {
            builder.append(MORSE_TABLE.get(segment));
        }
        return builder.toString();
    }

    private static double scoreSegments(List<String> segments) {
        double score = 0.0d;
        int minLength = Integer.MAX_VALUE;
        int maxLength = 0;
        for (String segment : segments) {
            int length = segment.length();
            score += scoreSegmentLength(length);
            minLength = Math.min(minLength, length);
            maxLength = Math.max(maxLength, length);
        }
        score -= Math.max(0, segments.size() - 2) * EXTRA_SEGMENT_PENALTY;
        if (maxLength > 0 && minLength != Integer.MAX_VALUE) {
            score -= (maxLength - minLength) * 0.05d;
        }
        return score;
    }

    private static double scoreSegmentLength(int length) {
        switch (length) {
            case 1:
                return 0.18d;
            case 2:
                return 0.92d;
            case 3:
                return 1.00d;
            case 4:
                return 0.96d;
            case 5:
                return 0.70d;
            case 6:
                return 0.44d;
            default:
                return 0.0d;
        }
    }

    private static String normalizeSequence(String sourceSequence) {
        if (sourceSequence == null) {
            return "";
        }
        String normalized = sourceSequence.trim().replaceAll("[^.-]", "");
        return normalized.toUpperCase(Locale.US);
    }

    private static String buildSegmentLengthKey(List<String> segments) {
        StringBuilder builder = new StringBuilder();
        for (String segment : segments) {
            if (builder.length() > 0) {
                builder.append('-');
            }
            builder.append(segment.length());
        }
        return builder.toString();
    }

    private static Map<String, String> buildMorseTable() {
        HashMap<String, String> table = new HashMap<>();
        table.put(".-", "A");
        table.put("-...", "B");
        table.put("-.-.", "C");
        table.put("-..", "D");
        table.put(".", "E");
        table.put("..-.", "F");
        table.put("--.", "G");
        table.put("....", "H");
        table.put("..", "I");
        table.put(".---", "J");
        table.put("-.-", "K");
        table.put(".-..", "L");
        table.put("--", "M");
        table.put("-.", "N");
        table.put("---", "O");
        table.put(".--.", "P");
        table.put("--.-", "Q");
        table.put(".-.", "R");
        table.put("...", "S");
        table.put("-", "T");
        table.put("..-", "U");
        table.put("...-", "V");
        table.put(".--", "W");
        table.put("-..-", "X");
        table.put("-.--", "Y");
        table.put("--..", "Z");

        table.put(".----", "1");
        table.put("..---", "2");
        table.put("...--", "3");
        table.put("....-", "4");
        table.put(".....", "5");
        table.put("-....", "6");
        table.put("--...", "7");
        table.put("---..", "8");
        table.put("----.", "9");
        table.put("-----", "0");

        table.put(".-.-.-", ".");
        table.put("--..--", ",");
        table.put("..--..", "?");
        table.put("-..-.", "/");
        table.put(".--.-.", "@");
        table.put("-...-", "=");
        return table;
    }

    private static final class Candidate {
        private final String decodedText;
        private final double score;
        private final String segmentLengthKey;
        private final int segmentCount;

        private Candidate(
                String decodedText,
                double score,
                String segmentLengthKey,
                int segmentCount
        ) {
            this.decodedText = decodedText;
            this.score = score;
            this.segmentLengthKey = segmentLengthKey;
            this.segmentCount = segmentCount;
        }

        private String decodedText() {
            return decodedText;
        }

        private double score() {
            return score;
        }

        private String segmentLengthKey() {
            return segmentLengthKey;
        }

        private int segmentCount() {
            return segmentCount;
        }
    }
}
