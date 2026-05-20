package org.bi9clt.cwcn.core.decoder;

import org.bi9clt.cwcn.core.timing.CwTimingEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CwDecoder {
    private static final Map<String, String> MORSE_TABLE = buildMorseTable();
    public static final String UNKNOWN_CHARACTER = "\u25A1";
    private static final double SOFT_WORD_BREAK_GAP_RATIO = 4.55d;
    private static final int TAIL_GAP_PROMOTION_MIN_TONES = 5;
    private static final double TAIL_GAP_PROMOTION_MAX_RATIO = 0.42d;
    private static final double TAIL_GAP_PROMOTION_LAST_TONE_MAX_RATIO = 0.12d;
    private static final int TAIL_DIT_REPAIR_MIN_TONES = 2;
    private static final int TAIL_DIT_REPAIR_MAX_TONES = 5;
    private static final double TAIL_DIT_REPAIR_LAST_TONE_MAX_RATIO = 0.72d;
    private static final double TAIL_DIT_REPAIR_LAST_GAP_MAX_RATIO = 0.45d;
    private static final double TAIL_DIT_REPAIR_PRIOR_DIT_MAX_RATIO = 0.72d;
    private static final double TAIL_DIT_REPAIR_LOOSE_LAST_GAP_MAX_RATIO = 0.90d;
    private static final double TAIL_DIT_REPAIR_LOOSE_LAST_TONE_MAX_RATIO = 0.50d;
    private static final double TAIL_DIT_REPAIR_LOOSE_PRIOR_DIT_MAX_RATIO = 0.45d;
    private static final double TAIL_DIT_REPAIR_TWO_TONE_LAST_GAP_MAX_RATIO = 0.90d;
    private static final double TAIL_DIT_REPAIR_TWO_TONE_LAST_TONE_MAX_RATIO = 0.60d;
    private static final double TAIL_DIT_REPAIR_TWO_TONE_PRIOR_DIT_MAX_RATIO = 0.72d;
    private static final int INTERNAL_MICRO_DIT_SPLIT_MIN_TONES = 6;
    private static final double INTERNAL_MICRO_DIT_MAX_RATIO = 0.12d;
    private static final double INTERNAL_MICRO_DIT_BOUNDARY_GAP_MIN_RATIO = 1.25d;

    private final StringBuilder currentSequence = new StringBuilder();
    private final StringBuilder decodedText = new StringBuilder();
    private final ArrayList<CwTimingEvent> currentCharacterTimingEvents = new ArrayList<>();

    private int totalSymbols;
    private int totalCharacters;
    private CwDecodeEvent lastDecodeEvent;

    public synchronized List<CwDecodeEvent> process(CwTimingEvent timingEvent) {
        ArrayList<CwDecodeEvent> events = new ArrayList<>(2);

        if (timingEvent == null) {
            return events;
        }

        if (timingEvent.kind() == CwTimingEvent.Kind.TONE) {
            if (timingEvent.durationMs() <= 0L) {
                return events;
            }
            if (timingEvent.classification() == CwTimingEvent.Classification.DIT) {
                rememberCharacterTimingEvent(timingEvent);
                appendSymbol(".", timingEvent.timestampMs(), events);
            } else if (timingEvent.classification() == CwTimingEvent.Classification.DAH) {
                rememberCharacterTimingEvent(timingEvent);
                appendSymbol("-", timingEvent.timestampMs(), events);
            }
            return events;
        }

        if (timingEvent.kind() == CwTimingEvent.Kind.GAP) {
            if (timingEvent.classification() == CwTimingEvent.Classification.INTRA_SYMBOL_GAP) {
                rememberCharacterTimingEvent(timingEvent);
            }
            if (timingEvent.classification() == CwTimingEvent.Classification.LETTER_GAP
                    || timingEvent.classification() == CwTimingEvent.Classification.WORD_GAP
                    || timingEvent.classification() == CwTimingEvent.Classification.UNKNOWN) {
                emitCharacter(timingEvent.timestampMs(), events);
            }

            if (timingEvent.classification() == CwTimingEvent.Classification.WORD_GAP) {
                appendWordBreak(timingEvent.timestampMs(), events);
            } else if (timingEvent.classification() == CwTimingEvent.Classification.LETTER_GAP
                    && shouldPromoteLetterGapToWordBreak(timingEvent)) {
                appendWordBreak(timingEvent.timestampMs(), events);
            }
        }

        return events;
    }

    public synchronized List<CwDecodeEvent> flushPendingCharacter(long timestampMs) {
        ArrayList<CwDecodeEvent> events = new ArrayList<>(1);
        emitCharacter(timestampMs, events);
        return events;
    }

    public synchronized void reset() {
        currentSequence.setLength(0);
        decodedText.setLength(0);
        currentCharacterTimingEvents.clear();
        totalSymbols = 0;
        totalCharacters = 0;
        lastDecodeEvent = null;
    }

    public synchronized CwDecoderSnapshot snapshot() {
        return new CwDecoderSnapshot(
                currentSequence.toString(),
                decodedText.toString(),
                totalSymbols,
                totalCharacters,
                lastDecodeEvent
        );
    }

    public synchronized boolean hasPendingCharacter() {
        return currentSequence.length() > 0;
    }

    private void appendSymbol(String symbol, long timestampMs, List<CwDecodeEvent> events) {
        currentSequence.append(symbol);
        totalSymbols += 1;
        CwDecodeEvent event = new CwDecodeEvent(
                CwDecodeEvent.Type.SYMBOL_APPENDED,
                timestampMs,
                currentSequence.toString(),
                decodedText.toString(),
                symbol
        );
        lastDecodeEvent = event;
        events.add(event);
    }

    private void emitCharacter(long timestampMs, List<CwDecodeEvent> events) {
        if (currentSequence.length() == 0) {
            currentCharacterTimingEvents.clear();
            return;
        }

        String sequence = currentSequence.toString();
        currentSequence.setLength(0);
        List<String> repairedSequences = repairSequence(sequence, currentCharacterTimingEvents);
        currentCharacterTimingEvents.clear();
        for (String repairedSequence : repairedSequences) {
            emitDecodedSequence(repairedSequence, timestampMs, events);
        }
    }

    private void emitDecodedSequence(String sequence, long timestampMs, List<CwDecodeEvent> events) {
        String decodedCharacter = MORSE_TABLE.getOrDefault(sequence, UNKNOWN_CHARACTER);
        boolean unknownCharacter = UNKNOWN_CHARACTER.equals(decodedCharacter);
        decodedText.append(decodedCharacter);
        totalCharacters += 1;

        CwDecodeEvent event = new CwDecodeEvent(
                CwDecodeEvent.Type.CHARACTER_DECODED,
                timestampMs,
                currentSequence.toString(),
                decodedText.toString(),
                decodedCharacter,
                sequence,
                unknownCharacter
        );
        lastDecodeEvent = event;
        events.add(event);
    }

    private void rememberCharacterTimingEvent(CwTimingEvent timingEvent) {
        if (timingEvent == null) {
            return;
        }
        currentCharacterTimingEvents.add(timingEvent);
    }

    private List<String> repairSequence(String sequence, List<CwTimingEvent> timingEvents) {
        List<String> repaired = splitSequenceForTailGapPromotion(sequence, timingEvents);
        if (repaired.size() != 1 || !sequence.equals(repaired.get(0))) {
            return repaired;
        }
        repaired = trimSequenceForTailDitRepair(sequence, timingEvents);
        if (repaired.size() != 1 || !sequence.equals(repaired.get(0))) {
            return repaired;
        }
        return splitUnknownSequenceForInternalMicroDit(sequence, timingEvents);
    }

    private List<String> splitSequenceForTailGapPromotion(
            String sequence,
            List<CwTimingEvent> timingEvents
    ) {
        ArrayList<String> single = new ArrayList<>(1);
        single.add(sequence);
        if (sequence == null
                || timingEvents == null
                || timingEvents.isEmpty()
                || sequence.length() < TAIL_GAP_PROMOTION_MIN_TONES
                || !MORSE_TABLE.containsKey(sequence)) {
            return single;
        }

        ArrayList<CwTimingEvent> tones = new ArrayList<>();
        ArrayList<CwTimingEvent> intraGaps = new ArrayList<>();
        for (CwTimingEvent timingEvent : timingEvents) {
            if (timingEvent == null) {
                continue;
            }
            if (timingEvent.kind() == CwTimingEvent.Kind.TONE) {
                tones.add(timingEvent);
            } else if (timingEvent.kind() == CwTimingEvent.Kind.GAP
                    && timingEvent.classification() == CwTimingEvent.Classification.INTRA_SYMBOL_GAP) {
                intraGaps.add(timingEvent);
            }
        }
        if (tones.size() != sequence.length() || intraGaps.size() != sequence.length() - 1 || intraGaps.size() < 2) {
            return single;
        }

        CwTimingEvent lastTone = tones.get(tones.size() - 1);
        if (lastTone.ratioToDotEstimate() > TAIL_GAP_PROMOTION_LAST_TONE_MAX_RATIO) {
            return single;
        }
        for (int index = intraGaps.size() - 2; index < intraGaps.size(); index++) {
            if (intraGaps.get(index).ratioToIntraGapEstimate() > TAIL_GAP_PROMOTION_MAX_RATIO) {
                return single;
            }
        }

        int firstSplitIndex = intraGaps.size() - 1;
        int secondSplitIndex = intraGaps.size();
        ArrayList<String> repaired = new ArrayList<>(3);
        repaired.add(sequence.substring(0, firstSplitIndex));
        repaired.add(sequence.substring(firstSplitIndex, secondSplitIndex));
        repaired.add(sequence.substring(secondSplitIndex));
        if (!allKnownSequences(repaired)) {
            return single;
        }
        return repaired;
    }

    private List<String> splitUnknownSequenceForInternalMicroDit(
            String sequence,
            List<CwTimingEvent> timingEvents
    ) {
        ArrayList<String> single = new ArrayList<>(1);
        single.add(sequence);
        if (sequence == null
                || timingEvents == null
                || timingEvents.isEmpty()
                || sequence.length() < INTERNAL_MICRO_DIT_SPLIT_MIN_TONES
                || MORSE_TABLE.containsKey(sequence)) {
            return single;
        }

        ArrayList<CwTimingEvent> tones = new ArrayList<>();
        ArrayList<CwTimingEvent> intraGaps = new ArrayList<>();
        for (CwTimingEvent timingEvent : timingEvents) {
            if (timingEvent == null) {
                continue;
            }
            if (timingEvent.kind() == CwTimingEvent.Kind.TONE) {
                tones.add(timingEvent);
            } else if (timingEvent.kind() == CwTimingEvent.Kind.GAP
                    && timingEvent.classification() == CwTimingEvent.Classification.INTRA_SYMBOL_GAP) {
                intraGaps.add(timingEvent);
            }
        }
        if (tones.size() != sequence.length() || intraGaps.size() != sequence.length() - 1) {
            return single;
        }

        for (int toneIndex = 1; toneIndex < tones.size() - 1 && toneIndex < intraGaps.size(); toneIndex++) {
            CwTimingEvent tone = tones.get(toneIndex);
            if (tone.classification() != CwTimingEvent.Classification.DIT
                    || tone.ratioToDotEstimate() > INTERNAL_MICRO_DIT_MAX_RATIO) {
                continue;
            }
            CwTimingEvent followingGap = intraGaps.get(toneIndex);
            if (followingGap.ratioToIntraGapEstimate() < INTERNAL_MICRO_DIT_BOUNDARY_GAP_MIN_RATIO) {
                continue;
            }

            String left = sequence.substring(0, toneIndex);
            String right = sequence.substring(toneIndex + 1);
            if (!allKnownAlphaNumericSequences(left, right)) {
                continue;
            }

            ArrayList<String> repaired = new ArrayList<>(2);
            repaired.add(left);
            repaired.add(right);
            return repaired;
        }
        return single;
    }

    private List<String> trimSequenceForTailDitRepair(
            String sequence,
            List<CwTimingEvent> timingEvents
    ) {
        ArrayList<String> single = new ArrayList<>(1);
        single.add(sequence);
        if (sequence == null
                || timingEvents == null
                || timingEvents.isEmpty()
                || sequence.length() < TAIL_DIT_REPAIR_MIN_TONES
                || sequence.length() > TAIL_DIT_REPAIR_MAX_TONES) {
            return single;
        }

        String trimmedSequence = sequence.substring(0, sequence.length() - 1);
        if (!isKnownAlphaNumericSequence(trimmedSequence)) {
            return single;
        }

        ArrayList<CwTimingEvent> tones = new ArrayList<>();
        ArrayList<CwTimingEvent> intraGaps = new ArrayList<>();
        for (CwTimingEvent timingEvent : timingEvents) {
            if (timingEvent == null) {
                continue;
            }
            if (timingEvent.kind() == CwTimingEvent.Kind.TONE) {
                tones.add(timingEvent);
            } else if (timingEvent.kind() == CwTimingEvent.Kind.GAP
                    && timingEvent.classification() == CwTimingEvent.Classification.INTRA_SYMBOL_GAP) {
                intraGaps.add(timingEvent);
            }
        }
        if (tones.size() != sequence.length() || intraGaps.size() != sequence.length() - 1 || intraGaps.isEmpty()) {
            return single;
        }

        CwTimingEvent tailTone = tones.get(tones.size() - 1);
        if (tailTone.classification() != CwTimingEvent.Classification.DIT
                || tailTone.ratioToDotEstimate() > TAIL_DIT_REPAIR_LAST_TONE_MAX_RATIO) {
            return single;
        }

        CwTimingEvent lastGap = intraGaps.get(intraGaps.size() - 1);
        long priorDitMedianDurationMs = medianPriorDitDurationMs(tones);
        if (priorDitMedianDurationMs <= 0L) {
            return single;
        }
        double priorDitRatio = tailTone.durationMs() / (double) Math.max(1L, priorDitMedianDurationMs);
        double lastGapRatio = lastGap.ratioToIntraGapEstimate();
        if (!shouldRepairTailDit(sequence.length(), tailTone.ratioToDotEstimate(), priorDitRatio, lastGapRatio)) {
            return single;
        }

        ArrayList<String> repaired = new ArrayList<>(1);
        repaired.add(trimmedSequence);
        return repaired;
    }

    private boolean shouldRepairTailDit(
            int sequenceLength,
            double tailToneRatio,
            double priorDitRatio,
            double lastGapRatio
    ) {
        if (lastGapRatio <= TAIL_DIT_REPAIR_LAST_GAP_MAX_RATIO
                && tailToneRatio <= TAIL_DIT_REPAIR_LAST_TONE_MAX_RATIO
                && priorDitRatio <= TAIL_DIT_REPAIR_PRIOR_DIT_MAX_RATIO) {
            return true;
        }
        if (sequenceLength == 2) {
            return lastGapRatio <= TAIL_DIT_REPAIR_TWO_TONE_LAST_GAP_MAX_RATIO
                    && tailToneRatio <= TAIL_DIT_REPAIR_TWO_TONE_LAST_TONE_MAX_RATIO
                    && priorDitRatio <= TAIL_DIT_REPAIR_TWO_TONE_PRIOR_DIT_MAX_RATIO;
        }
        if (sequenceLength >= 4) {
            return lastGapRatio <= TAIL_DIT_REPAIR_LOOSE_LAST_GAP_MAX_RATIO
                    && tailToneRatio <= TAIL_DIT_REPAIR_LOOSE_LAST_TONE_MAX_RATIO
                    && priorDitRatio <= TAIL_DIT_REPAIR_LOOSE_PRIOR_DIT_MAX_RATIO;
        }
        return false;
    }

    private boolean allKnownSequences(List<String> sequences) {
        if (sequences == null || sequences.isEmpty()) {
            return false;
        }
        for (String sequence : sequences) {
            if (sequence == null || sequence.isEmpty() || !MORSE_TABLE.containsKey(sequence)) {
                return false;
            }
        }
        return true;
    }

    private boolean isKnownAlphaNumericSequence(String sequence) {
        if (sequence == null || sequence.isEmpty()) {
            return false;
        }
        String decoded = MORSE_TABLE.get(sequence);
        return decoded != null && decoded.length() == 1 && Character.isLetterOrDigit(decoded.charAt(0));
    }

    private boolean allKnownAlphaNumericSequences(String... sequences) {
        if (sequences == null || sequences.length == 0) {
            return false;
        }
        for (String sequence : sequences) {
            String decoded = MORSE_TABLE.get(sequence);
            if (decoded == null || decoded.length() != 1 || !Character.isLetterOrDigit(decoded.charAt(0))) {
                return false;
            }
        }
        return true;
    }

    private long medianPriorDitDurationMs(List<CwTimingEvent> tones) {
        if (tones == null || tones.size() < 2) {
            return 0L;
        }
        ArrayList<Long> priorDits = new ArrayList<>();
        for (int index = 0; index < tones.size() - 1; index++) {
            CwTimingEvent tone = tones.get(index);
            if (tone != null && tone.classification() == CwTimingEvent.Classification.DIT) {
                priorDits.add(tone.durationMs());
            }
        }
        if (priorDits.isEmpty()) {
            return 0L;
        }
        priorDits.sort(Long::compareTo);
        return priorDits.get(priorDits.size() / 2);
    }

    private void appendWordBreak(long timestampMs, List<CwDecodeEvent> events) {
        if (decodedText.length() == 0) {
            return;
        }

        if (decodedText.charAt(decodedText.length() - 1) == ' ') {
            return;
        }

        decodedText.append(' ');
        CwDecodeEvent event = new CwDecodeEvent(
                CwDecodeEvent.Type.WORD_BREAK,
                timestampMs,
                currentSequence.toString(),
                decodedText.toString(),
                " "
        );
        lastDecodeEvent = event;
        events.add(event);
    }

    private boolean shouldPromoteLetterGapToWordBreak(CwTimingEvent timingEvent) {
        if (timingEvent == null || timingEvent.dotEstimateMs() <= 0L) {
            return false;
        }
        double gapRatio = timingEvent.durationMs() / (double) Math.max(1L, timingEvent.dotEstimateMs());
        return gapRatio >= SOFT_WORD_BREAK_GAP_RATIO;
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
}
