package org.bi9clt.cwcn.core.rx;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class RxRawCommitGateTest {
    @Test
    public void holdsFinalDecodeEventsUntilTrustedTimingExists() {
        RxRawCommitGate gate = new RxRawCommitGate();
        gate.beginNewTurn();

        List<CwDecodeEvent> firstResult = gate.admit(
                character(100L, "?", true),
                false,
                TimingAnchorController.TrustOrigin.NONE,
                0L,
                -1L
        );
        List<CwDecodeEvent> secondResult = gate.admit(
                wordBreak(120L, "? "),
                false,
                TimingAnchorController.TrustOrigin.NONE,
                0L,
                -1L
        );

        assertTrue(firstResult.isEmpty());
        assertTrue(secondResult.isEmpty());
        assertEquals(2, gate.pendingFinalEventCount());
        assertFalse(gate.gateOpenInCurrentTurn());
    }

    @Test
    public void boundaryTrustDropsBufferedSpeculationWhenGateOpens() {
        RxRawCommitGate gate = new RxRawCommitGate();
        gate.beginNewTurn();

        gate.admit(character(100L, "?", true), false, TimingAnchorController.TrustOrigin.NONE, 0L, -1L);
        gate.admit(wordBreak(120L, "? "), false, TimingAnchorController.TrustOrigin.NONE, 0L, -1L);

        List<CwDecodeEvent> openResult = gate.admit(
                character(180L, "C", false),
                true,
                TimingAnchorController.TrustOrigin.BOUNDARY,
                0L,
                -1L
        );
        List<CwDecodeEvent> followResult = gate.admit(
                character(240L, "Q", false),
                false,
                TimingAnchorController.TrustOrigin.BOUNDARY,
                0L,
                -1L
        );

        assertEquals(1, openResult.size());
        assertEquals("C", openResult.get(0).emittedValue());
        assertEquals(1, followResult.size());
        assertEquals("Q", followResult.get(0).emittedValue());
        assertEquals(0, gate.pendingFinalEventCount());
        assertTrue(gate.gateOpenInCurrentTurn());
    }

    @Test
    public void resetClearsGateStateAcrossTurns() {
        RxRawCommitGate gate = new RxRawCommitGate();
        gate.beginNewTurn();
        gate.admit(character(100L, "C", false), true, TimingAnchorController.TrustOrigin.BOUNDARY, 0L, -1L);
        assertTrue(gate.gateOpenInCurrentTurn());

        gate.endTurn();
        List<CwDecodeEvent> nextTurnResult = gate.admit(
                character(200L, "Z", false),
                false,
                TimingAnchorController.TrustOrigin.NONE,
                0L,
                -1L
        );

        assertTrue(nextTurnResult.isEmpty());
        assertFalse(gate.gateOpenInCurrentTurn());
        assertEquals(1, gate.pendingFinalEventCount());
    }

    @Test
    public void suppressesLeadingWordBreakUntilFirstCharacterInTurn() {
        RxRawCommitGate gate = new RxRawCommitGate();
        gate.beginNewTurn();

        List<CwDecodeEvent> leadingWordBreak = gate.admit(
                wordBreak(100L, " "),
                true,
                TimingAnchorController.TrustOrigin.BOUNDARY,
                0L,
                -1L
        );
        List<CwDecodeEvent> firstCharacter = gate.admit(
                character(140L, "C", false),
                false,
                TimingAnchorController.TrustOrigin.BOUNDARY,
                0L,
                -1L
        );
        List<CwDecodeEvent> laterWordBreak = gate.admit(
                wordBreak(220L, "C "),
                false,
                TimingAnchorController.TrustOrigin.BOUNDARY,
                0L,
                -1L
        );

        assertTrue(leadingWordBreak.isEmpty());
        assertEquals(1, firstCharacter.size());
        assertEquals("C", firstCharacter.get(0).emittedValue());
        assertEquals(1, laterWordBreak.size());
        assertEquals(CwDecodeEvent.Type.WORD_BREAK, laterWordBreak.get(0).type());
        assertTrue(gate.gateOpenInCurrentTurn());
    }

    @Test
    public void cadenceTrustWaitsForSecondAlignedCharacterBeforeOpening() {
        RxRawCommitGate gate = new RxRawCommitGate();
        gate.beginNewTurn();

        gate.admit(character(100L, "?", true), false, TimingAnchorController.TrustOrigin.NONE, 0L, -1L);
        List<CwDecodeEvent> preBoundaryTrusted = gate.admit(
                character(120L, "Z", false),
                true,
                TimingAnchorController.TrustOrigin.CADENCE,
                54L,
                120L
        );
        gate.noteTimingEvent(letterGap(180L), true, TimingAnchorController.TrustOrigin.CADENCE, 54L, 120L);
        List<CwDecodeEvent> boundaryCharacter = gate.admit(
                character(180L, "Q", false),
                true,
                TimingAnchorController.TrustOrigin.CADENCE,
                54L,
                120L
        );
        List<CwDecodeEvent> boundaryWordBreak = gate.admit(
                wordBreak(180L, "Q "),
                true,
                TimingAnchorController.TrustOrigin.CADENCE,
                54L,
                120L
        );
        List<CwDecodeEvent> firstAlignedCharacter = gate.admit(
                character(240L, "C", false),
                true,
                TimingAnchorController.TrustOrigin.CADENCE,
                54L,
                120L
        );
        List<CwDecodeEvent> secondAlignedCharacter = gate.admit(
                character(300L, "Q", false),
                true,
                TimingAnchorController.TrustOrigin.CADENCE,
                54L,
                120L
        );

        assertTrue(preBoundaryTrusted.isEmpty());
        assertTrue(boundaryCharacter.isEmpty());
        assertTrue(boundaryWordBreak.isEmpty());
        assertTrue(firstAlignedCharacter.isEmpty());
        assertEquals(1, secondAlignedCharacter.size());
        assertEquals("Q", secondAlignedCharacter.get(0).emittedValue());
        assertTrue(gate.gateOpenInCurrentTurn());
    }

    @Test
    public void boundaryTrustCanRecoverBufferedPrefixFromTrustedDotReplay() {
        RxRawCommitGate gate = new RxRawCommitGate();
        gate.beginNewTurn();

        gate.noteTimingEvent(dit(100L), false, TimingAnchorController.TrustOrigin.NONE, 0L, -1L);
        gate.noteTimingEvent(letterGap(160L), false, TimingAnchorController.TrustOrigin.NONE, 0L, -1L);
        gate.noteTimingEvent(dah(340L), false, TimingAnchorController.TrustOrigin.NONE, 0L, -1L);
        gate.noteTimingEvent(letterGap(520L), false, TimingAnchorController.TrustOrigin.NONE, 0L, -1L);

        List<CwDecodeEvent> openResult = gate.admit(
                character(520L, "T", false),
                true,
                TimingAnchorController.TrustOrigin.BOUNDARY,
                60L,
                520L
        );

        assertEquals(2, openResult.size());
        assertEquals("E", openResult.get(0).emittedValue());
        assertEquals("T", openResult.get(1).emittedValue());
        assertTrue(gate.gateOpenInCurrentTurn());
    }

    @Test
    public void preTrustStableCharacterStaysBufferedUntilTrustedReplayOpensGate() {
        RxRawCommitGate gate = new RxRawCommitGate();
        gate.beginNewTurn();

        List<CwDecodeEvent> provisional = gate.admit(
                character(100L, "C", false),
                false,
                TimingAnchorController.TrustOrigin.NONE,
                0L,
                -1L
        );
        List<CwDecodeEvent> unknownStable = gate.admit(
                character(160L, "?", true),
                false,
                TimingAnchorController.TrustOrigin.NONE,
                0L,
                -1L
        );
        List<CwDecodeEvent> wordBreak = gate.admit(
                wordBreak(220L, "C "),
                false,
                TimingAnchorController.TrustOrigin.NONE,
                0L,
                -1L
        );

        assertTrue(provisional.isEmpty());
        assertTrue(unknownStable.isEmpty());
        assertTrue(wordBreak.isEmpty());
        assertFalse(gate.gateOpenInCurrentTurn());
        assertEquals(3, gate.pendingFinalEventCount());
    }

    @Test
    public void cadenceReplayCanRecoverOpeningFromEarliestBufferedTimingWhenNothingWasCommittedYet() {
        RxRawCommitGate gate = new RxRawCommitGate();
        gate.beginNewTurn();

        gate.noteTimingEvent(dit(100L), false, TimingAnchorController.TrustOrigin.NONE, 0L, -1L);
        gate.noteTimingEvent(letterGap(160L), false, TimingAnchorController.TrustOrigin.NONE, 0L, -1L);

        List<CwDecodeEvent> bufferedOpening = gate.admit(
                character(160L, "E", false),
                false,
                TimingAnchorController.TrustOrigin.NONE,
                0L,
                -1L
        );
        gate.noteTimingEvent(letterGap(500L), true, TimingAnchorController.TrustOrigin.CADENCE, 54L, 500L);
        List<CwDecodeEvent> firstAlignedCharacter = gate.admit(
                character(520L, "Q", false),
                true,
                TimingAnchorController.TrustOrigin.CADENCE,
                54L,
                500L
        );
        List<CwDecodeEvent> secondAlignedCharacter = gate.admit(
                character(580L, "Q", false),
                true,
                TimingAnchorController.TrustOrigin.CADENCE,
                54L,
                500L
        );

        assertTrue(bufferedOpening.isEmpty());
        assertTrue(firstAlignedCharacter.isEmpty());
        assertEquals(1, secondAlignedCharacter.size());
        assertEquals("E", secondAlignedCharacter.get(0).emittedValue());
        assertEquals("E", secondAlignedCharacter.get(0).outputText());
    }

    @Test
    public void cadenceReplayKeepsWideOpeningWordGapWhenForcedReplayDotIsTrusted() {
        RxRawCommitGate gate = new RxRawCommitGate();
        gate.beginNewTurn();

        gate.noteTimingEvent(customTone(
                100L,
                CwTimingEvent.Classification.DIT,
                83L,
                83L,
                83L
        ), false, TimingAnchorController.TrustOrigin.NONE, 0L, -1L);
        gate.noteTimingEvent(customGap(
                1094L,
                CwTimingEvent.Classification.WORD_GAP,
                994L,
                83L,
                83L
        ), false, TimingAnchorController.TrustOrigin.NONE, 0L, -1L);
        gate.noteTimingEvent(customTone(
                1180L,
                CwTimingEvent.Classification.DIT,
                83L,
                83L,
                83L
        ), false, TimingAnchorController.TrustOrigin.NONE, 0L, -1L);

        List<CwDecodeEvent> bufferedOpeningCharacter = gate.admit(
                character(1094L, "E", false),
                false,
                TimingAnchorController.TrustOrigin.NONE,
                0L,
                -1L
        );
        List<CwDecodeEvent> bufferedOpeningWordBreak = gate.admit(
                wordBreak(1094L, "E "),
                false,
                TimingAnchorController.TrustOrigin.NONE,
                0L,
                -1L
        );
        gate.noteTimingEvent(letterGap(1500L), true, TimingAnchorController.TrustOrigin.CADENCE, 83L, 1500L);

        List<CwDecodeEvent> firstAlignedCharacter = gate.admit(
                character(1600L, "Q", false),
                true,
                TimingAnchorController.TrustOrigin.CADENCE,
                83L,
                1500L
        );
        List<CwDecodeEvent> secondAlignedCharacter = gate.admit(
                character(1700L, "Q", false),
                true,
                TimingAnchorController.TrustOrigin.CADENCE,
                83L,
                1500L
        );

        assertTrue(bufferedOpeningCharacter.isEmpty());
        assertTrue(bufferedOpeningWordBreak.isEmpty());
        assertTrue(firstAlignedCharacter.isEmpty());
        assertEquals(3, secondAlignedCharacter.size());
        assertEquals(CwDecodeEvent.Type.CHARACTER_DECODED, secondAlignedCharacter.get(0).type());
        assertEquals("E", secondAlignedCharacter.get(0).emittedValue());
        assertEquals("E", secondAlignedCharacter.get(0).outputText());
        assertEquals(CwDecodeEvent.Type.WORD_BREAK, secondAlignedCharacter.get(1).type());
        assertEquals("E ", secondAlignedCharacter.get(1).outputText());
        assertEquals(CwDecodeEvent.Type.CHARACTER_DECODED, secondAlignedCharacter.get(2).type());
        assertEquals("E", secondAlignedCharacter.get(2).emittedValue());
        assertEquals("E E", secondAlignedCharacter.get(2).outputText());
    }

    @Test
    public void cadenceReplayDoesNotPromoteRawLetterGapIntoWordGap() {
        RxRawCommitGate gate = new RxRawCommitGate();
        gate.beginNewTurn();

        gate.noteTimingEvent(customTone(
                100L,
                CwTimingEvent.Classification.DIT,
                106L,
                102L,
                104L
        ), false, TimingAnchorController.TrustOrigin.NONE, 0L, -1L);
        gate.noteTimingEvent(customGap(
                474L,
                CwTimingEvent.Classification.LETTER_GAP,
                374L,
                102L,
                104L
        ), false, TimingAnchorController.TrustOrigin.NONE, 0L, -1L);
        gate.noteTimingEvent(customTone(
                550L,
                CwTimingEvent.Classification.DIT,
                106L,
                103L,
                104L
        ), false, TimingAnchorController.TrustOrigin.NONE, 0L, -1L);

        List<CwDecodeEvent> bufferedOpeningCharacter = gate.admit(
                character(474L, "E", false),
                false,
                TimingAnchorController.TrustOrigin.NONE,
                0L,
                -1L
        );
        gate.noteTimingEvent(letterGap(900L), true, TimingAnchorController.TrustOrigin.CADENCE, 83L, 900L);

        List<CwDecodeEvent> firstAlignedCharacter = gate.admit(
                character(960L, "Q", false),
                true,
                TimingAnchorController.TrustOrigin.CADENCE,
                83L,
                900L
        );
        List<CwDecodeEvent> secondAlignedCharacter = gate.admit(
                character(1020L, "Q", false),
                true,
                TimingAnchorController.TrustOrigin.CADENCE,
                83L,
                900L
        );

        assertTrue(bufferedOpeningCharacter.isEmpty());
        assertTrue(firstAlignedCharacter.isEmpty());
        assertEquals(2, secondAlignedCharacter.size());
        assertEquals(CwDecodeEvent.Type.CHARACTER_DECODED, secondAlignedCharacter.get(0).type());
        assertEquals("E", secondAlignedCharacter.get(0).emittedValue());
        assertEquals("E", secondAlignedCharacter.get(0).outputText());
        assertEquals(CwDecodeEvent.Type.CHARACTER_DECODED, secondAlignedCharacter.get(1).type());
        assertEquals("E", secondAlignedCharacter.get(1).emittedValue());
        assertEquals("EE", secondAlignedCharacter.get(1).outputText());
    }

    @Test
    public void replayWordGapDoesNotInjectSyntheticBreakAfterReplayAlreadyRecoveredCharacter() {
        RxRawCommitGate gate = new RxRawCommitGate();
        gate.beginNewTurn();

        gate.noteTimingEvent(dit(100L), false, TimingAnchorController.TrustOrigin.NONE, 0L, -1L);
        gate.noteTimingEvent(letterGap(160L), false, TimingAnchorController.TrustOrigin.NONE, 0L, -1L);
        gate.noteTimingEvent(dah(340L), false, TimingAnchorController.TrustOrigin.NONE, 0L, -1L);
        gate.noteTimingEvent(wordGap(700L), false, TimingAnchorController.TrustOrigin.NONE, 0L, -1L);

        List<CwDecodeEvent> openResult = gate.admit(
                wordBreak(700L, "ET "),
                true,
                TimingAnchorController.TrustOrigin.BOUNDARY,
                60L,
                700L
        );

        assertEquals(3, openResult.size());
        assertEquals(CwDecodeEvent.Type.CHARACTER_DECODED, openResult.get(0).type());
        assertEquals("E", openResult.get(0).emittedValue());
        assertEquals("E", openResult.get(0).outputText());
        assertEquals(CwDecodeEvent.Type.CHARACTER_DECODED, openResult.get(1).type());
        assertEquals("T", openResult.get(1).emittedValue());
        assertEquals("ET", openResult.get(1).outputText());
        assertEquals(CwDecodeEvent.Type.WORD_BREAK, openResult.get(2).type());
        assertEquals("ET ", openResult.get(2).outputText());
    }

    @Test
    public void replacedCommittedOutputTextCarriesIntoNextTurnOutputPrefix() {
        RxRawCommitGate gate = new RxRawCommitGate();
        gate.beginNewTurn();

        List<CwDecodeEvent> firstTurn = gate.admit(
                character(100L, "C", false),
                true,
                TimingAnchorController.TrustOrigin.BOUNDARY,
                0L,
                -1L
        );
        assertEquals(1, firstTurn.size());
        assertEquals("C", firstTurn.get(0).outputText());

        gate.replaceCommittedOutputText("CQ DE HI SK");
        gate.endTurn();
        gate.beginNewTurn();

        List<CwDecodeEvent> nextTurn = gate.admit(
                character(200L, "Q", false),
                true,
                TimingAnchorController.TrustOrigin.BOUNDARY,
                0L,
                -1L
        );

        assertEquals(1, nextTurn.size());
        assertEquals("CQ DE HI SKQ", nextTurn.get(0).outputText());
    }

    private static CwDecodeEvent character(long timestampMs, String value, boolean unknown) {
        return new CwDecodeEvent(
                CwDecodeEvent.Type.CHARACTER_DECODED,
                timestampMs,
                value,
                value,
                value,
                value,
                unknown
        );
    }

    private static CwDecodeEvent wordBreak(long timestampMs, String outputText) {
        return new CwDecodeEvent(
                CwDecodeEvent.Type.WORD_BREAK,
                timestampMs,
                "",
                outputText,
                " ",
                "",
                false
        );
    }

    private static CwTimingEvent letterGap(long timestampMs) {
        return new CwTimingEvent(
                CwTimingEvent.Kind.GAP,
                CwTimingEvent.Classification.LETTER_GAP,
                timestampMs,
                180L,
                60L,
                60L
        );
    }

    private static CwTimingEvent dit(long timestampMs) {
        return new CwTimingEvent(
                CwTimingEvent.Kind.TONE,
                CwTimingEvent.Classification.DIT,
                timestampMs,
                60L,
                60L,
                60L
        );
    }

    private static CwTimingEvent dah(long timestampMs) {
        return new CwTimingEvent(
                CwTimingEvent.Kind.TONE,
                CwTimingEvent.Classification.DAH,
                timestampMs,
                180L,
                60L,
                60L
        );
    }

    private static CwTimingEvent wordGap(long timestampMs) {
        return new CwTimingEvent(
                CwTimingEvent.Kind.GAP,
                CwTimingEvent.Classification.WORD_GAP,
                timestampMs,
                420L,
                60L,
                60L
        );
    }

    private static CwTimingEvent customTone(
            long timestampMs,
            CwTimingEvent.Classification classification,
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

    private static CwTimingEvent customGap(
            long timestampMs,
            CwTimingEvent.Classification classification,
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
}
