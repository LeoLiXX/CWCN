package org.bi9clt.cwcn.core.interpreter;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CwInterpreterRawCopyFocusTest {
    @Test
    public void rawCopyFocusModeDoesNotSplitGluedCallsignRunOrEmitHints() {
        CwInterpreter interpreter = new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);

        interpreter.process(decoded("CQ CQ CQ DE BI9CLTBI9CLT PSE K", 1000L));

        CwInterpreterSnapshot snapshot = interpreter.snapshot();
        assertEquals("CQ CQ CQ DE BI9CLT BI9CLT PLEASE K", snapshot.normalizedText());
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
