package org.bi9clt.cwcn.core.rx;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.decoder.CwDecoder;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class RxUnknownFallbackResolverTest {
    @Test
    public void gluedCqSequenceCanBeRecoveredFromPureMorseSplit() {
        assertEquals("CQ", RxUnknownFallbackResolver.resolveUnknownSequence("-.-.--.-"));
    }

    @Test
    public void gluedTripleVSequenceCanBeRecoveredFromPureMorseSplit() {
        assertEquals("VVV", RxUnknownFallbackResolver.resolveUnknownSequence("...-...-...-"));
    }

    @Test
    public void ambiguousSplitProducesNoFallbackGuess() {
        assertNull(RxUnknownFallbackResolver.resolveUnknownSequence("...-..."));
    }

    @Test
    public void bestEffortFallbackCanRecoverGluedKbSequence() {
        assertEquals("KB", RxUnknownFallbackResolver.resolveUnknownSequenceBestEffort("-.--..."));
    }

    @Test
    public void trackerBuildsSuggestedTextWithoutChangingRawText() {
        RxUnknownFallbackTracker tracker = new RxUnknownFallbackTracker();

        tracker.process(new CwDecodeEvent(
                CwDecodeEvent.Type.CHARACTER_DECODED,
                100L,
                "",
                CwDecoder.UNKNOWN_CHARACTER,
                CwDecoder.UNKNOWN_CHARACTER,
                "-.-.--.-",
                true
        ));
        tracker.process(new CwDecodeEvent(
                CwDecodeEvent.Type.WORD_BREAK,
                200L,
                "",
                CwDecoder.UNKNOWN_CHARACTER + " ",
                " "
        ));
        tracker.process(new CwDecodeEvent(
                CwDecodeEvent.Type.CHARACTER_DECODED,
                300L,
                "",
                CwDecoder.UNKNOWN_CHARACTER + " K",
                "K",
                "-.-",
                false
        ));

        RxUnknownFallbackSuggestion suggestion = tracker.snapshot();
        assertEquals("? K", suggestion.rawText());
        assertEquals("CQ K", suggestion.suggestedText());
        assertFalse(suggestion.hasNotes());
    }

    @Test
    public void trackerKeepsAmbiguousBestEffortOnlyAsNote() {
        RxUnknownFallbackTracker tracker = new RxUnknownFallbackTracker();

        tracker.process(new CwDecodeEvent(
                CwDecodeEvent.Type.CHARACTER_DECODED,
                100L,
                "",
                CwDecoder.UNKNOWN_CHARACTER,
                CwDecoder.UNKNOWN_CHARACTER,
                "-.--...",
                true
        ));

        RxUnknownFallbackSuggestion suggestion = tracker.snapshot();
        assertEquals("?", suggestion.rawText());
        assertEquals("?", suggestion.suggestedText());
        assertFalse(suggestion.hasSuggestion());
        assertTrue(suggestion.hasNotes());
        assertEquals("?(-.--...) -> KB", suggestion.notesText());
    }
}
