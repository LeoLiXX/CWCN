package org.bi9clt.cwcn.core.decoder;

import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CwDecoderTest {
    @Test
    public void realQuestionMarkRemainsQuestionMark() {
        CwDecoder decoder = new CwDecoder();

        CwDecodeEvent event = emitCharacter(decoder, "..--..");

        assertEquals("?", event.emittedValue());
        assertEquals("..--..", event.sourceSequence());
        assertFalse(event.unknownCharacter());
        assertEquals("?", decoder.snapshot().decodedText());
    }

    @Test
    public void unknownSequenceUsesSquarePlaceholderAndRetainsRawSequence() {
        CwDecoder decoder = new CwDecoder();

        CwDecodeEvent event = emitCharacter(decoder, ".-.-");

        assertEquals(CwDecoder.UNKNOWN_CHARACTER, event.emittedValue());
        assertEquals(".-.-", event.sourceSequence());
        assertTrue(event.unknownCharacter());
        assertEquals(CwDecoder.UNKNOWN_CHARACTER, decoder.snapshot().decodedText());
    }

    @Test
    public void veryLongLetterGapCanStillInsertSoftWordBreak() {
        CwDecoder decoder = new CwDecoder();

        emitCharacter(decoder, "..."); // S
        List<CwDecodeEvent> events = decoder.process(new CwTimingEvent(
                CwTimingEvent.Kind.GAP,
                CwTimingEvent.Classification.LETTER_GAP,
                2000L,
                300L,
                60L
        ));
        emitCharacter(decoder, ".");

        assertTrue(events.stream().anyMatch(event -> event.type() == CwDecodeEvent.Type.WORD_BREAK));
        assertEquals("S E", decoder.snapshot().decodedText());
    }

    @Test
    public void midSizedLetterGapDoesNotInsertSoftWordBreak() {
        CwDecoder decoder = new CwDecoder();

        emitCharacter(decoder, "..."); // S
        List<CwDecodeEvent> events = decoder.process(new CwTimingEvent(
                CwTimingEvent.Kind.GAP,
                CwTimingEvent.Classification.LETTER_GAP,
                2000L,
                240L,
                60L
        ));
        emitCharacter(decoder, ".");

        assertTrue(events.stream().noneMatch(event -> event.type() == CwDecodeEvent.Type.WORD_BREAK));
        assertEquals("SE", decoder.snapshot().decodedText());
    }

    @Test
    public void nearWordSizedLetterGapCanInsertSoftWordBreak() {
        CwDecoder decoder = new CwDecoder();

        emitCharacter(decoder, "..."); // S
        List<CwDecodeEvent> events = decoder.process(new CwTimingEvent(
                CwTimingEvent.Kind.GAP,
                CwTimingEvent.Classification.LETTER_GAP,
                2000L,
                273L,
                60L
        ));
        emitCharacter(decoder, ".");

        assertTrue(events.stream().anyMatch(event -> event.type() == CwDecodeEvent.Type.WORD_BREAK));
        assertEquals("S E", decoder.snapshot().decodedText());
    }

    @Test
    public void endOfStreamFlushEmitsTrailingCharacterWithoutExplicitLetterGap() {
        CwDecoder decoder = new CwDecoder();
        long timestampMs = 1000L;

        char[] sequence = "-.-".toCharArray();
        for (int index = 0; index < sequence.length; index++) {
            char symbol = sequence[index];
            decoder.process(new CwTimingEvent(
                    CwTimingEvent.Kind.TONE,
                    symbol == '.' ? CwTimingEvent.Classification.DIT : CwTimingEvent.Classification.DAH,
                    timestampMs,
                    symbol == '.' ? 60L : 180L,
                    60L
            ));
            timestampMs += 60L;
            if (index < sequence.length - 1) {
                decoder.process(new CwTimingEvent(
                        CwTimingEvent.Kind.GAP,
                        CwTimingEvent.Classification.INTRA_SYMBOL_GAP,
                        timestampMs,
                        60L,
                        60L
                ));
                timestampMs += 60L;
            }
        }

        List<CwDecodeEvent> events = decoder.flushPendingCharacter(timestampMs);

        assertEquals(1, events.size());
        assertEquals(CwDecodeEvent.Type.CHARACTER_DECODED, events.get(0).type());
        assertEquals("K", events.get(0).emittedValue());
        assertEquals("K", decoder.snapshot().decodedText());
    }

    @Test
    public void endOfStreamFlushDoesNotDuplicateAlreadyDecodedCharacter() {
        CwDecoder decoder = new CwDecoder();

        emitCharacter(decoder, "-.-");
        List<CwDecodeEvent> events = decoder.flushPendingCharacter(2000L);

        assertTrue(events.isEmpty());
        assertEquals("K", decoder.snapshot().decodedText());
    }

    @Test
    public void zeroDurationToneIsIgnoredInsteadOfExtendingCharacter() {
        CwDecoder decoder = new CwDecoder();
        List<CwDecodeEvent> events = new ArrayList<>();

        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DIT, 1000L, 60L, 60L, 60L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 1060L, 60L, 60L, 60L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DIT, 1120L, 0L, 60L, 60L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.LETTER_GAP, 1180L, 180L, 60L, 60L)));

        List<String> emittedCharacters = decodedCharacters(events);

        assertEquals(1, emittedCharacters.size());
        assertEquals("E", emittedCharacters.get(0));
        assertEquals("E", decoder.snapshot().decodedText());
    }

    @Test
    public void ultraShortTailMicroGapsCanSplitIntoMultipleValidCharacters() {
        CwDecoder decoder = new CwDecoder();
        List<CwDecodeEvent> events = new ArrayList<>();

        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DAH, 9036L, 192L, 66L, 71L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 9066L, 30L, 66L, 71L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DIT, 9088L, 22L, 66L, 71L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 9113L, 25L, 66L, 71L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DIT, 9182L, 69L, 66L, 71L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 9209L, 27L, 66L, 71L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DIT, 9315L, 106L, 66L, 71L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 9340L, 25L, 66L, 71L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DIT, 9344L, 4L, 66L, 71L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.LETTER_GAP, 9513L, 169L, 66L, 71L)));

        List<String> emittedCharacters = decodedCharacters(events);

        assertEquals(3, emittedCharacters.size());
        assertEquals("D", emittedCharacters.get(0));
        assertEquals("E", emittedCharacters.get(1));
        assertEquals("E", emittedCharacters.get(2));
        assertEquals("DEE", decoder.snapshot().decodedText());
    }

    @Test
    public void normalTailDoesNotSplitKnownFiveToneCharacter() {
        CwDecoder decoder = new CwDecoder();
        List<CwDecodeEvent> events = new ArrayList<>();

        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DAH, 1000L, 180L, 60L, 60L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 1060L, 60L, 60L, 60L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DIT, 1120L, 60L, 60L, 60L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 1180L, 60L, 60L, 60L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DIT, 1240L, 60L, 60L, 60L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 1300L, 60L, 60L, 60L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DIT, 1360L, 60L, 60L, 60L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 1420L, 60L, 60L, 60L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DIT, 1480L, 60L, 60L, 60L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.LETTER_GAP, 1540L, 180L, 60L, 60L)));

        List<String> emittedCharacters = decodedCharacters(events);

        assertEquals(1, emittedCharacters.size());
        assertEquals("6", emittedCharacters.get(0));
        assertEquals("6", decoder.snapshot().decodedText());
    }

    @Test
    public void unknownMicroDitBridgeCanSplitIntoTwoAlphaNumericCharacters() {
        CwDecoder decoder = new CwDecoder();
        List<CwDecodeEvent> events = new ArrayList<>();

        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DAH, 17697L, 185L, 102L, 102L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 17771L, 74L, 102L, 102L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DIT, 17843L, 72L, 102L, 102L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 17911L, 68L, 102L, 102L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DAH, 18098L, 187L, 102L, 102L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 18171L, 73L, 102L, 102L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DIT, 18242L, 71L, 102L, 102L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 18295L, 53L, 102L, 102L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DIT, 18304L, 9L, 102L, 102L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 18444L, 140L, 102L, 102L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DAH, 18646L, 202L, 102L, 102L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 18712L, 66L, 102L, 102L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DAH, 18898L, 186L, 102L, 102L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.LETTER_GAP, 19112L, 214L, 102L, 102L)));

        List<String> emittedCharacters = decodedCharacters(events);

        assertEquals(2, emittedCharacters.size());
        assertEquals("C", emittedCharacters.get(0));
        assertEquals("M", emittedCharacters.get(1));
        assertEquals("CM", decoder.snapshot().decodedText());
    }

    @Test
    public void normalDitDoesNotTriggerUnknownMicroDitSplit() {
        CwDecoder decoder = new CwDecoder();
        List<CwDecodeEvent> events = new ArrayList<>();

        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DAH, 17697L, 185L, 102L, 102L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 17771L, 74L, 102L, 102L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DIT, 17843L, 72L, 102L, 102L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 17911L, 68L, 102L, 102L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DAH, 18098L, 187L, 102L, 102L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 18171L, 73L, 102L, 102L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DIT, 18242L, 71L, 102L, 102L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 18295L, 53L, 102L, 102L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DIT, 18304L, 61L, 102L, 102L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 18444L, 140L, 102L, 102L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DAH, 18646L, 202L, 102L, 102L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 18712L, 66L, 102L, 102L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DAH, 18898L, 186L, 102L, 102L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.LETTER_GAP, 19112L, 214L, 102L, 102L)));

        List<String> emittedCharacters = decodedCharacters(events);

        assertEquals(1, emittedCharacters.size());
        assertEquals(CwDecoder.UNKNOWN_CHARACTER, emittedCharacters.get(0));
        assertEquals(CwDecoder.UNKNOWN_CHARACTER, decoder.snapshot().decodedText());
    }

    @Test
    public void shortTailDitCanRepairQLikeOpeningCharacter() {
        CwDecoder decoder = new CwDecoder();
        List<CwDecodeEvent> events = new ArrayList<>();

        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DAH, 5608L, 178L, 67L, 67L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 5641L, 33L, 67L, 67L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DAH, 5856L, 215L, 66L, 66L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 5881L, 25L, 66L, 66L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DIT, 5960L, 79L, 68L, 68L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 6027L, 67L, 68L, 68L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DAH, 6208L, 181L, 67L, 67L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 6234L, 26L, 66L, 66L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DIT, 6272L, 38L, 58L, 58L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.LETTER_GAP, 6389L, 117L, 58L, 58L)));

        List<String> emittedCharacters = decodedCharacters(events);

        assertEquals(1, emittedCharacters.size());
        assertEquals("Q", emittedCharacters.get(0));
        assertEquals("Q", decoder.snapshot().decodedText());
    }

    @Test
    public void shortTailDitCanRepairRLikeOpeningCharacter() {
        CwDecoder decoder = new CwDecoder();
        List<CwDecodeEvent> events = new ArrayList<>();

        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DIT, 6464L, 75L, 61L, 61L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 6508L, 44L, 62L, 62L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DAH, 6688L, 180L, 63L, 63L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 6716L, 28L, 63L, 63L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DIT, 6816L, 100L, 69L, 69L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 6843L, 27L, 66L, 66L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DIT, 6880L, 37L, 57L, 57L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.LETTER_GAP, 6988L, 108L, 57L, 57L)));

        List<String> emittedCharacters = decodedCharacters(events);

        assertEquals(1, emittedCharacters.size());
        assertEquals("R", emittedCharacters.get(0));
        assertEquals("R", decoder.snapshot().decodedText());
    }

    @Test
    public void shortTailDitCanRepairDWithLongerTerminalGap() {
        CwDecoder decoder = new CwDecoder();
        List<CwDecodeEvent> events = new ArrayList<>();

        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DAH, 9328L, 180L, 56L, 56L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 9388L, 60L, 56L, 56L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DIT, 9456L, 68L, 56L, 56L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 9483L, 27L, 56L, 56L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DIT, 9560L, 77L, 56L, 56L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 9608L, 48L, 56L, 56L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DIT, 9632L, 24L, 56L, 56L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.LETTER_GAP, 9749L, 117L, 56L, 56L)));

        List<String> emittedCharacters = decodedCharacters(events);

        assertEquals(1, emittedCharacters.size());
        assertEquals("D", emittedCharacters.get(0));
        assertEquals("D", decoder.snapshot().decodedText());
    }

    @Test
    public void shortTailDitCanRepairEWithLongerTerminalGap() {
        CwDecoder decoder = new CwDecoder();
        List<CwDecodeEvent> events = new ArrayList<>();

        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DIT, 9794L, 45L, 56L, 56L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 9841L, 47L, 56L, 56L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DIT, 9872L, 31L, 56L, 56L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.WORD_GAP, 10228L, 356L, 56L, 56L)));

        List<String> emittedCharacters = decodedCharacters(events);

        assertEquals(1, emittedCharacters.size());
        assertEquals("E", emittedCharacters.get(0));
        assertEquals("E ", decoder.snapshot().decodedText());
    }

    @Test
    public void normalLDoesNotLoseItsTailDit() {
        CwDecoder decoder = new CwDecoder();
        List<CwDecodeEvent> events = new ArrayList<>();

        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DIT, 1000L, 60L, 60L, 60L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 1060L, 60L, 60L, 60L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DAH, 1120L, 180L, 60L, 60L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 1180L, 60L, 60L, 60L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DIT, 1240L, 60L, 60L, 60L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.INTRA_SYMBOL_GAP, 1300L, 60L, 60L, 60L)));
        events.addAll(decoder.process(tone(CwTimingEvent.Classification.DIT, 1360L, 60L, 60L, 60L)));
        events.addAll(decoder.process(gap(CwTimingEvent.Classification.LETTER_GAP, 1420L, 180L, 60L, 60L)));

        List<String> emittedCharacters = decodedCharacters(events);

        assertEquals(1, emittedCharacters.size());
        assertEquals("L", emittedCharacters.get(0));
        assertEquals("L", decoder.snapshot().decodedText());
    }

    private CwDecodeEvent emitCharacter(CwDecoder decoder, String sequence) {
        long timestampMs = 1000L;
        List<CwDecodeEvent> events = new ArrayList<>();
        for (int index = 0; index < sequence.length(); index++) {
            char symbol = sequence.charAt(index);
            events.addAll(decoder.process(new CwTimingEvent(
                    CwTimingEvent.Kind.TONE,
                    symbol == '.' ? CwTimingEvent.Classification.DIT : CwTimingEvent.Classification.DAH,
                    timestampMs,
                    symbol == '.' ? 60L : 180L,
                    60L
            )));
            timestampMs += 60L;
            if (index < sequence.length() - 1) {
                events.addAll(decoder.process(new CwTimingEvent(
                        CwTimingEvent.Kind.GAP,
                        CwTimingEvent.Classification.INTRA_SYMBOL_GAP,
                        timestampMs,
                        60L,
                        60L
                )));
                timestampMs += 60L;
            }
        }
        events.addAll(decoder.process(new CwTimingEvent(
                CwTimingEvent.Kind.GAP,
                CwTimingEvent.Classification.LETTER_GAP,
                timestampMs,
                180L,
                60L
        )));

        return events.get(events.size() - 1);
    }

    private static CwTimingEvent tone(
            CwTimingEvent.Classification classification,
            long timestampMs,
            long durationMs,
            long dotEstimateMs,
            long intraGapEstimateMs
    ) {
        return new CwTimingEvent(
                CwTimingEvent.Kind.TONE,
                classification,
                timestampMs,
                durationMs,
                dotEstimateMs,
                intraGapEstimateMs
        );
    }

    private static CwTimingEvent gap(
            CwTimingEvent.Classification classification,
            long timestampMs,
            long durationMs,
            long dotEstimateMs,
            long intraGapEstimateMs
    ) {
        return new CwTimingEvent(
                CwTimingEvent.Kind.GAP,
                classification,
                timestampMs,
                durationMs,
                dotEstimateMs,
                intraGapEstimateMs
        );
    }

    private static List<String> decodedCharacters(List<CwDecodeEvent> events) {
        ArrayList<String> decoded = new ArrayList<>();
        for (CwDecodeEvent event : events) {
            if (event.type() == CwDecodeEvent.Type.CHARACTER_DECODED) {
                decoded.add(event.emittedValue());
            }
        }
        return decoded;
    }
}
