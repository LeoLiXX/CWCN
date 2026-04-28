package org.bi9clt.cwcn.core.decoder;

import org.bi9clt.cwcn.core.timing.CwTimingEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CwDecoder {
    private static final Map<String, String> MORSE_TABLE = buildMorseTable();
    public static final String UNKNOWN_CHARACTER = "\u25A1";
    private static final double SOFT_WORD_BREAK_GAP_RATIO = 3.6d;

    private final StringBuilder currentSequence = new StringBuilder();
    private final StringBuilder decodedText = new StringBuilder();

    private int totalSymbols;
    private int totalCharacters;
    private CwDecodeEvent lastDecodeEvent;

    public synchronized List<CwDecodeEvent> process(CwTimingEvent timingEvent) {
        ArrayList<CwDecodeEvent> events = new ArrayList<>(2);

        if (timingEvent.kind() == CwTimingEvent.Kind.TONE) {
            if (timingEvent.classification() == CwTimingEvent.Classification.DIT) {
                appendSymbol(".", timingEvent.timestampMs(), events);
            } else if (timingEvent.classification() == CwTimingEvent.Classification.DAH) {
                appendSymbol("-", timingEvent.timestampMs(), events);
            }
            return events;
        }

        if (timingEvent.kind() == CwTimingEvent.Kind.GAP) {
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
            return;
        }

        String sequence = currentSequence.toString();
        String decodedCharacter = MORSE_TABLE.getOrDefault(sequence, UNKNOWN_CHARACTER);
        boolean unknownCharacter = UNKNOWN_CHARACTER.equals(decodedCharacter);
        decodedText.append(decodedCharacter);
        totalCharacters += 1;
        currentSequence.setLength(0);

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
