package org.bi9clt.cwcn.core.interpreter;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CwInterpreterRawCopyFocusTest {
    @Test
    public void rawCopyFocusModeDoesNotSplitGluedCallsignRunOrEmitHints() {
        CwInterpreter interpreter = new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);

        interpreter.process(decoded("CQ CQ CQ DE BI9CLTBI9CLT PSE K", 1000L));

        CwInterpreterSnapshot snapshot = interpreter.snapshot();
        assertEquals("CQ CQ CQ DE BI9CLTBI9CLT PSE K", snapshot.rawText());
        assertEquals("CQ CQ CQ DE BI9CLT BI9CLT PSE K", snapshot.normalizedText());
        assertEquals(1, snapshot.callsignCandidates().size());
        assertEquals("BI9CLT", snapshot.primaryCallsignCandidate());
        assertTrue(snapshot.phraseHints().isEmpty());
    }

    @Test
    public void rawCopyFocusModeKeepsExplicitCallsignsWithoutAggressiveRecovery() {
        CwInterpreter interpreter = new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);

        interpreter.process(decoded("DE BI3TUK KN", 1000L));

        CwInterpreterSnapshot snapshot = interpreter.snapshot();
        assertEquals("DE BI3TUK KN", snapshot.normalizedText());
        assertEquals(1, snapshot.callsignCandidates().size());
        assertEquals("BI3TUK", snapshot.primaryCallsignCandidate());
        assertFalse(snapshot.tokens().isEmpty());
    }

    @Test
    public void rawChannelKeepsObservedSpacingWhileNormalizedChannelCanBeReadable() {
        CwInterpreter interpreter = new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);

        interpreter.process(decoded("CQ  CQ DE BI9CLT ", 1000L));

        CwInterpreterSnapshot snapshot = interpreter.snapshot();
        assertEquals("CQ  CQ DE BI9CLT", snapshot.rawText());
        assertEquals("CQ CQ DE BI9CLT", snapshot.normalizedText());
    }

    @Test
    public void rawCopyFocusModeDoesNotUseAggressiveStructuralRecoveryOnCompactChain() {
        CwInterpreter interpreter = new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);

        interpreter.process(decoded("BKBI9CLTDEBG7YOZR5NNTU73BK", 1000L));

        CwInterpreterSnapshot snapshot = interpreter.snapshot();
        assertEquals("BKBI9CLTDEBG7YOZR5NNTU73BK", snapshot.rawText());
        assertEquals("BKBI9CLTDEBG7YOZR5NNTU73BK", snapshot.normalizedText());
        assertTrue(snapshot.callsignCandidates().isEmpty());
        assertTrue(snapshot.phraseHints().isEmpty());
    }

    @Test
    public void rawCopyFocusModeKeepsOriginalReportAndClosingForms() {
        CwInterpreter interpreter = new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);

        interpreter.process(decoded("UR 5 NN TU 73B", 1000L));

        CwInterpreterSnapshot snapshot = interpreter.snapshot();
        assertEquals("UR 5 NN TU 73B", snapshot.rawText());
        assertEquals("UR 5NN TU 73B", snapshot.normalizedText());
    }

    @Test
    public void rawCopyFocusClassificationStaysAvailableWithoutRewritingDisplayCopy() {
        CwInterpreter interpreter = new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);

        interpreter.process(decoded("UR 5 NN TU 73B", 1000L));

        CwInterpreterSnapshot snapshot = interpreter.snapshot();
        List<CwInterpretedToken> tokens = snapshot.tokens();
        assertEquals("UR", tokens.get(0).normalizedText());
        assertEquals(CwInterpretedToken.Type.REPORT, tokens.get(0).type());
        assertEquals("5NN", tokens.get(1).normalizedText());
        assertEquals(CwInterpretedToken.Type.REPORT, tokens.get(1).type());
        assertEquals("TU", tokens.get(2).normalizedText());
        assertEquals(CwInterpretedToken.Type.THANKS, tokens.get(2).type());
        assertEquals("73B", tokens.get(3).normalizedText());
        assertEquals(CwInterpretedToken.Type.FREE_TEXT, tokens.get(3).type());
    }

    private CwDecodeEvent decoded(String text, long timestampMs) {
        return new CwDecodeEvent(
                CwDecodeEvent.Type.CHARACTER_DECODED,
                timestampMs,
                "",
                text,
                text,
                "",
                false
        );
    }
}
