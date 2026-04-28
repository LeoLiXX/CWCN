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
    public void longLetterGapCanStillInsertSoftWordBreak() {
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

        assertTrue(events.stream().anyMatch(event -> event.type() == CwDecodeEvent.Type.WORD_BREAK));
        assertEquals("S E", decoder.snapshot().decodedText());
    }

    @Test
    public void normalLetterGapDoesNotInsertSoftWordBreak() {
        CwDecoder decoder = new CwDecoder();

        emitCharacter(decoder, "..."); // S
        List<CwDecodeEvent> events = decoder.process(new CwTimingEvent(
                CwTimingEvent.Kind.GAP,
                CwTimingEvent.Classification.LETTER_GAP,
                2000L,
                180L,
                60L
        ));
        emitCharacter(decoder, ".");

        assertTrue(events.stream().noneMatch(event -> event.type() == CwDecodeEvent.Type.WORD_BREAK));
        assertEquals("SE", decoder.snapshot().decodedText());
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
}
