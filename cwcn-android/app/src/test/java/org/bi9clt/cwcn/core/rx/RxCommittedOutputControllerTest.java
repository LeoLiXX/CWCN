package org.bi9clt.cwcn.core.rx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.decoder.CwDecoder;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.interpreter.CwInterpreterSnapshot;
import org.junit.Test;

import java.util.Arrays;

public final class RxCommittedOutputControllerTest {
    @Test
    public void processCommittedDecodeEventUpdatesRawAndFallbackState() {
        RxCommittedOutputController controller = new RxCommittedOutputController(
                new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS),
                new RxUnknownFallbackTracker(),
                null,
                null,
                null
        );

        controller.processCommittedDecodeEvent(unknownCharacterEvent(100L, "-.-.--.-"));
        controller.processCommittedDecodeEvent(wordBreakEvent(200L));
        controller.processCommittedDecodeEvent(decodedCharacterEvent(300L, "? K", "K", "-.-"));

        CwInterpreterSnapshot rawSnapshot = controller.rawSnapshot();
        RxUnknownFallbackSuggestion fallbackSuggestion = controller.fallbackSuggestion();

        assertEquals("? K", rawSnapshot == null ? "" : rawSnapshot.rawText());
        assertEquals("? K", fallbackSuggestion.rawText());
        assertEquals("CQ K", fallbackSuggestion.suggestedText());
        assertFalse(fallbackSuggestion.hasNotes());
    }

    @Test
    public void rebuildFromCommittedDecodeEventsResetsPriorStateAndSynchronizesCommitGate() {
        RxRawCommitGate gate = new RxRawCommitGate();
        RxCommittedOutputController controller = new RxCommittedOutputController(
                new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS),
                new RxUnknownFallbackTracker(),
                null,
                null,
                gate
        );

        controller.processCommittedDecodeEvent(decodedCharacterEvent(100L, "X", "X", "-..-"));
        assertEquals("X", controller.rawSnapshot() == null ? "" : controller.rawSnapshot().rawText());

        controller.rebuildFromCommittedDecodeEvents(Arrays.asList(
                decodedCharacterEvent(200L, "C", "C", "-.-."),
                decodedCharacterEvent(300L, "CQ", "Q", "--.-")
        ));

        CwInterpreterSnapshot rebuiltSnapshot = controller.rawSnapshot();
        assertEquals("CQ", rebuiltSnapshot == null ? "" : rebuiltSnapshot.rawText());

        gate.beginNewTurn();
        CwDecodeEvent committedEvent = gate.admit(
                decodedCharacterEvent(400L, "CQK", "K", "-.-"),
                true,
                TimingAnchorController.TrustOrigin.STABLE,
                60L,
                400L
        ).get(0);

        assertEquals("CQK", committedEvent.outputText());
    }

    @Test
    public void rebuildFromCommittedDecodeEventsKeepsAmbiguousBestEffortOnlyAsNote() {
        RxCommittedOutputController controller = new RxCommittedOutputController(
                new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS),
                new RxUnknownFallbackTracker(),
                null,
                null,
                null
        );

        controller.rebuildFromCommittedDecodeEvents(Arrays.asList(
                unknownCharacterEvent(100L, "-.--...")
        ));

        RxUnknownFallbackSuggestion suggestion = controller.fallbackSuggestion();
        assertEquals("?", suggestion.rawText());
        assertEquals("?", suggestion.suggestedText());
        assertFalse(suggestion.hasSuggestion());
        assertTrue(suggestion.hasNotes());
        assertEquals("?(-.--...) -> KB", suggestion.notesText());
    }

    private static CwDecodeEvent decodedCharacterEvent(
            long timestampMs,
            String outputText,
            String emittedValue,
            String sourceSequence
    ) {
        return new CwDecodeEvent(
                CwDecodeEvent.Type.CHARACTER_DECODED,
                timestampMs,
                emittedValue,
                outputText,
                emittedValue,
                sourceSequence,
                false
        );
    }

    private static CwDecodeEvent unknownCharacterEvent(long timestampMs, String sourceSequence) {
        return new CwDecodeEvent(
                CwDecodeEvent.Type.CHARACTER_DECODED,
                timestampMs,
                CwDecoder.UNKNOWN_CHARACTER,
                CwDecoder.UNKNOWN_CHARACTER,
                CwDecoder.UNKNOWN_CHARACTER,
                sourceSequence,
                true
        );
    }

    private static CwDecodeEvent wordBreakEvent(long timestampMs) {
        return new CwDecodeEvent(
                CwDecodeEvent.Type.WORD_BREAK,
                timestampMs,
                "",
                " ",
                " "
        );
    }
}
