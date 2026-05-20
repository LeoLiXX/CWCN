package org.bi9clt.cwcn.core.rx;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public final class RxTurnTailRepairControllerTest {
    private static final long DOT_MS = 60L;

    @Test
    public void maybeRepairCurrentTurn_replacesOnlyCurrentTurnTail() {
        RxTurnTailRepairController controller = new RxTurnTailRepairController();
        controller.beginNewTurn();
        noteDecodeEvents(controller, buildFinalEvents("", "VVV", "VVV"));
        controller.endTurn();

        controller.beginNewTurn();
        noteDecodeEvents(controller, buildFinalEvents("VVV VVV ", "CQ", "DE", "HI", "?"));
        List<CwToneEvent> toneEvents = encodeTextAsToneEvents("HI SK", 1000L, DOT_MS);
        noteToneEvents(controller, toneEvents);

        RxTurnTailRepairController.RepairApplication repairApplication =
                controller.maybeRepairCurrentTurn(lastToneTimestamp(toneEvents) + (DOT_MS * 10L));

        assertNotNull(repairApplication);
        assertEquals("HI ?", repairApplication.repairResult().baseTailText());
        assertEquals("HI SK", repairApplication.repairResult().repairedTailText());
        assertEquals(
                "VVV VVV CQ DE HI SK",
                repairApplication.sessionDecodeEvents()
                        .get(repairApplication.sessionDecodeEvents().size() - 1)
                        .outputText()
                        .trim()
        );
    }

    @Test
    public void maybeRepairCurrentTurn_returnsNullWithoutUnknownTail() {
        RxTurnTailRepairController controller = new RxTurnTailRepairController();
        controller.beginNewTurn();
        noteDecodeEvents(controller, buildFinalEvents("", "CQ", "DE", "HI", "SK"));
        List<CwToneEvent> toneEvents = encodeTextAsToneEvents("HI SK", 1000L, DOT_MS);
        noteToneEvents(controller, toneEvents);

        RxTurnTailRepairController.RepairApplication repairApplication =
                controller.maybeRepairCurrentTurn(lastToneTimestamp(toneEvents) + (DOT_MS * 10L));

        assertNull(repairApplication);
    }

    private static void noteDecodeEvents(
            RxTurnTailRepairController controller,
            List<CwDecodeEvent> decodeEvents
    ) {
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            controller.noteCommittedDecodeEvent(decodeEvent);
        }
    }

    private static void noteToneEvents(
            RxTurnTailRepairController controller,
            List<CwToneEvent> toneEvents
    ) {
        for (CwToneEvent toneEvent : toneEvents) {
            controller.noteToneEvent(toneEvent);
        }
    }

    private static List<CwDecodeEvent> buildFinalEvents(String initialOutputText, String... words) {
        ArrayList<CwDecodeEvent> decodeEvents = new ArrayList<>();
        long timestampMs = 100L;
        StringBuilder outputText = new StringBuilder(initialOutputText == null ? "" : initialOutputText);
        for (int wordIndex = 0; wordIndex < words.length; wordIndex++) {
            String word = words[wordIndex];
            for (int charIndex = 0; charIndex < word.length(); charIndex++) {
                char ch = word.charAt(charIndex);
                outputText.append(ch);
                boolean unknownCharacter = ch == '?';
                decodeEvents.add(new CwDecodeEvent(
                        CwDecodeEvent.Type.CHARACTER_DECODED,
                        timestampMs,
                        "",
                        outputText.toString(),
                        String.valueOf(ch),
                        unknownCharacter ? "..--.." : "",
                        unknownCharacter
                ));
                timestampMs += 100L;
            }
            if (wordIndex < words.length - 1) {
                outputText.append(' ');
                decodeEvents.add(new CwDecodeEvent(
                        CwDecodeEvent.Type.WORD_BREAK,
                        timestampMs,
                        "",
                        outputText.toString(),
                        " ",
                        "",
                        false
                ));
                timestampMs += 50L;
            }
        }
        return decodeEvents;
    }

    private static List<CwToneEvent> encodeTextAsToneEvents(String text, long startTimestampMs, long dotMs) {
        ArrayList<CwToneEvent> toneEvents = new ArrayList<>();
        long cursorMs = startTimestampMs;
        String[] words = text.trim().split("\\s+");
        for (int wordIndex = 0; wordIndex < words.length; wordIndex++) {
            char[] characters = words[wordIndex].toCharArray();
            for (int charIndex = 0; charIndex < characters.length; charIndex++) {
                String pattern = morsePatternFor(characters[charIndex]);
                for (int symbolIndex = 0; symbolIndex < pattern.length(); symbolIndex++) {
                    toneEvents.add(toneOn(cursorMs));
                    long toneDurationMs = pattern.charAt(symbolIndex) == '-' ? dotMs * 3L : dotMs;
                    cursorMs += toneDurationMs;
                    toneEvents.add(toneOff(cursorMs, toneDurationMs));
                    if (symbolIndex < pattern.length() - 1) {
                        cursorMs += dotMs;
                    }
                }
                if (charIndex < characters.length - 1) {
                    cursorMs += dotMs * 3L;
                }
            }
            if (wordIndex < words.length - 1) {
                cursorMs += dotMs * 7L;
            }
        }
        return toneEvents;
    }

    private static long lastToneTimestamp(List<CwToneEvent> toneEvents) {
        return toneEvents.isEmpty() ? 0L : toneEvents.get(toneEvents.size() - 1).timestampMs();
    }

    private static CwToneEvent toneOn(long timestampMs) {
        return new CwToneEvent(CwToneEvent.Type.TONE_ON, timestampMs, 16000, 12000.0d, 0L);
    }

    private static CwToneEvent toneOff(long timestampMs, long durationMs) {
        return new CwToneEvent(CwToneEvent.Type.TONE_OFF, timestampMs, 16000, 12000.0d, durationMs);
    }

    private static String morsePatternFor(char ch) {
        switch (Character.toUpperCase(ch)) {
            case 'C':
                return "-.-.";
            case 'D':
                return "-..";
            case 'E':
                return ".";
            case 'H':
                return "....";
            case 'I':
                return "..";
            case 'K':
                return "-.-";
            case 'Q':
                return "--.-";
            case 'S':
                return "...";
            case 'V':
                return "...-";
            default:
                throw new IllegalArgumentException("Unsupported test character: " + ch);
        }
    }
}
