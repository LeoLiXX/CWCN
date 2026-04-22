package org.bi9clt.cwcn.core.interpreter;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.decoder.CwDecoder;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CwInterpreterCallsignRecoveryTest {
    @Test
    public void contextualPartialSuffixNearDeStillBecomesCallsignCandidate() {
        CwInterpreterSnapshot snapshot = runSequence("BI9CLT DE ?YOZ TU 73 BK");

        assertTrue(snapshot.callsignCandidates().contains("BI9CLT"));
        assertTrue(snapshot.callsignCandidates().contains("?YOZ"));
        assertTrue(snapshot.phraseHints().contains("Partial callsign / uncertain copy"));
    }

    @Test
    public void tailPrefixFragmentMergesIntoEarlierFullCallsignCandidate() {
        CwInterpreterSnapshot snapshot = runSequence("BI9CLT DE BG7YOZ TU73BKBI9CL");

        assertTrue(snapshot.callsignCandidates().contains("BI9CLT"));
        assertTrue(snapshot.callsignCandidates().contains("BG7YOZ"));
        assertFalse(snapshot.callsignCandidates().contains("BI9CL"));
    }

    @Test
    public void rememberedSpeakerCallsignUpgradesContextualSuffixFragment() {
        CwInterpreter interpreter = new CwInterpreter();

        interpreter.process(decode("BI9CLT DE BG7YOZ UR 5NN BK", 1000L));
        interpreter.process(decode("BI9CLT DE ?YOZ TU 73 BK", 2000L));

        CwInterpreterSnapshot snapshot = interpreter.snapshot();
        assertEquals("BG7YOZ", snapshot.primaryCallsignCandidate());
        assertTrue(snapshot.callsignCandidates().contains("BG7YOZ"));
        assertFalse(snapshot.callsignCandidates().contains("?YOZ"));
        assertTrue(snapshot.phraseHints().contains("Partial callsign / uncertain copy"));
    }

    @Test
    public void unknownPlaceholderActsLikeUncertainCharacterForSemantics() {
        CwInterpreter interpreter = new CwInterpreter();

        interpreter.process(decode("BI9CLT DE BG7YOZ UR 5NN BK", 1000L));
        interpreter.process(decode("BI9CLT DE " + CwDecoder.UNKNOWN_CHARACTER + "YOZ TU 73 BK", 2000L));

        CwInterpreterSnapshot snapshot = interpreter.snapshot();
        assertEquals("BG7YOZ", snapshot.primaryCallsignCandidate());
        assertTrue(snapshot.callsignCandidates().contains("BG7YOZ"));
        assertFalse(snapshot.callsignCandidates().contains(CwDecoder.UNKNOWN_CHARACTER + "YOZ"));
        assertTrue(snapshot.phraseHints().contains("Partial callsign / uncertain copy"));
    }

    @Test
    public void rememberedAddressedCallsignUpgradesShortPrefixFragment() {
        CwInterpreter interpreter = new CwInterpreter();

        interpreter.process(decode("BI9CLT DE BG7YOZ UR 5NN BK", 1000L));
        interpreter.process(decode("BI9CL DE BG7YOZ TU 73 BK", 2000L));

        CwInterpreterSnapshot snapshot = interpreter.snapshot();
        assertTrue(snapshot.callsignCandidates().contains("BI9CLT"));
        assertTrue(snapshot.callsignCandidates().contains("BG7YOZ"));
        assertFalse(snapshot.callsignCandidates().contains("BI9CL"));
    }

    @Test
    public void rememberedSpeakerCallsignRecoversFromReportChainContamination() {
        CwInterpreter interpreter = new CwInterpreter();

        interpreter.process(decode("BI9CLT DE BG7YOZ UR 5NN BK", 1000L));
        interpreter.process(decode("?? DE ?YOZUR5NNBK BI9CL", 2000L));

        CwInterpreterSnapshot snapshot = interpreter.snapshot();
        assertTrue(snapshot.callsignCandidates().contains("BG7YOZ"));
    }

    @Test
    public void rememberedAddressedCallsignRecoversShortUncertainSuffixFragment() {
        CwInterpreter interpreter = new CwInterpreter();

        interpreter.process(decode("BI9CLT DE BG7YOZ UR 5NN BK", 1000L));
        interpreter.process(decode("?LT DE BG7YOZ TU 73 BK", 2000L));

        CwInterpreterSnapshot snapshot = interpreter.snapshot();
        assertTrue(snapshot.callsignCandidates().contains("BI9CLT"));
        assertEquals("BG7YOZ", snapshot.primaryCallsignCandidate());
    }

    @Test
    public void rememberedSpeakerCallsignDropsSingleLeadingContaminationLetter() {
        CwInterpreter interpreter = new CwInterpreter();

        interpreter.process(decode("BI9CLT DE BG7YOZ UR 5NN BK", 1000L));
        interpreter.process(decode("BI9CLT DE HBG7YOZ UR 5NN BK", 2000L));

        CwInterpreterSnapshot snapshot = interpreter.snapshot();
        assertTrue(snapshot.callsignCandidates().contains("BG7YOZ"));
        assertEquals("BG7YOZ", snapshot.primaryCallsignCandidate());
    }

    @Test
    public void rememberedSpeakerCallsignDropsSingleTrailingContaminationLetter() {
        CwInterpreter interpreter = new CwInterpreter();

        interpreter.process(decode("BI9CLT DE BG7YOZ UR 5NN BK", 1000L));
        interpreter.process(decode("BI9CLT DE BG7YOZU UR 5NN BK", 2000L));

        CwInterpreterSnapshot snapshot = interpreter.snapshot();
        assertTrue(snapshot.callsignCandidates().contains("BG7YOZ"));
        assertEquals("BG7YOZ", snapshot.primaryCallsignCandidate());
    }

    @Test
    public void compactAckResidueSuffixDoesNotWinOverCleanSpeakerCallsign() {
        CwInterpreterSnapshot snapshot = runSequence("BI9CLT DE BG7YOZR5 TU 73 BK");

        assertTrue(snapshot.callsignCandidates().contains("BG7YOZ"));
        assertFalse(snapshot.callsignCandidates().contains("BG7YOZR5"));
    }

    @Test
    public void leadingNoiseTrimCanRecoverContaminatedSpeakerCallsign() {
        CwInterpreter interpreter = new CwInterpreter();

        interpreter.process(decode("BI9CLT DE BG7YOZ UR 5NN BK", 1000L));
        interpreter.process(decode("?? DE ?BBG7YOZ UR 5NN BK", 2000L));

        CwInterpreterSnapshot snapshot = interpreter.snapshot();
        assertTrue(snapshot.callsignCandidates().contains("BG7YOZ"));
    }

    @Test
    public void controlAndReportResidueDoNotPolluteRecoveredCallsignCandidates() {
        CwInterpreter interpreter = new CwInterpreter();

        interpreter.process(decode("BI9CLT DE BG7YOZ UR 5NN BK", 1000L));
        interpreter.process(decode("?? DE ?BBG7YOZ UR 5CB BK??", 2000L));

        CwInterpreterSnapshot snapshot = interpreter.snapshot();
        assertEquals("BG7YOZ", snapshot.primaryCallsignCandidate());
        assertTrue(snapshot.callsignCandidates().contains("BG7YOZ"));
        assertFalse(snapshot.callsignCandidates().contains("5CB"));
        assertFalse(snapshot.callsignCandidates().contains("BK??"));
    }

    @Test
    public void closingChainWrappedSpeakerCallsignStillRecoversCleanCandidate() {
        CwInterpreter interpreter = new CwInterpreter();

        interpreter.process(decode("BI9CLT DE BG7YOZ UR 5NN BK", 1000L));
        interpreter.process(decode("?? DE ?BBG7YOZTU73BK??", 2000L));

        CwInterpreterSnapshot snapshot = interpreter.snapshot();
        assertEquals("BG7YOZ", snapshot.primaryCallsignCandidate());
        assertTrue(snapshot.callsignCandidates().contains("BG7YOZ"));
        assertFalse(snapshot.callsignCandidates().contains("?BBG7YOZTU73BK??"));
    }

    @Test
    public void gluedAckReportClosingResidueDoesNotBeatCleanRememberedCallsign() {
        CwInterpreter interpreter = new CwInterpreter();

        interpreter.process(decode("BI9CLT DE BG7YOZ UR 5NN BK", 1000L));
        interpreter.process(decode("?? DE BG7YOZR5NNTU73BK", 2000L));

        CwInterpreterSnapshot snapshot = interpreter.snapshot();
        assertEquals("BG7YOZ", snapshot.primaryCallsignCandidate());
        assertTrue(snapshot.callsignCandidates().contains("BG7YOZ"));
        assertFalse(snapshot.callsignCandidates().contains("BG7YOZR5NNTU73BK"));
    }

    @Test
    public void partialCallBeforeClarificationKeywordsStillCountsAsUncertainCallsign() {
        CwInterpreterSnapshot snapshot = runSequence("H??Z AGN PSE K");

        assertTrue(snapshot.callsignCandidates().contains("H??Z"));
        assertTrue(snapshot.phraseHints().contains("Partial callsign / uncertain copy"));
        assertTrue(snapshot.phraseHints().contains("Repeat / clarification request"));
    }

    @Test
    public void uncertainCallsignAheadOfCallsignAgainPleaseFlowIsPreserved() {
        CwInterpreterSnapshot snapshot = runSequence("BI9??Z UR CALLSIGN AGAIN PSE");

        assertTrue(snapshot.callsignCandidates().contains("BI9??Z"));
        assertTrue(snapshot.phraseHints().contains("Partial callsign / uncertain copy"));
        assertTrue(snapshot.phraseHints().contains("Repeat / clarification request"));
    }

    @Test
    public void portableSuffixCallsignIsKeptAsValidCandidate() {
        CwInterpreterSnapshot snapshot = runSequence("BI9CLT DE BG7YOZ/P UR 5NN BK");

        assertEquals("BG7YOZ/P", snapshot.primaryCallsignCandidate());
        assertTrue(snapshot.callsignCandidates().contains("BG7YOZ/P"));
        assertTrue(snapshot.phraseHints().contains("Station identification / callsign exchange"));
    }

    @Test
    public void closingResidueTokenContributes73HintWithoutPollutingCallsigns() {
        CwInterpreterSnapshot snapshot = runSequence("BI9CLT DE BG7YOZ TU 73B");

        assertTrue(snapshot.phraseHints().contains("73 closing"));
        assertFalse(snapshot.callsignCandidates().contains("73B"));
    }

    @Test
    public void unknownLeadingEdgeBeforeDeStillRestoresStationIdentificationContext() {
        CwInterpreterSnapshot snapshot = runSequence(CwDecoder.UNKNOWN_CHARACTER + "DE BG7YOZ R5NNTU73B");

        assertTrue(snapshot.normalizedText().contains("DE BG7YOZ"));
        assertTrue(snapshot.phraseHints().contains("Station identification / callsign exchange"));
        assertTrue(snapshot.callsignCandidates().contains("BG7YOZ"));
    }

    @Test
    public void controlPrefixedStationCallsignBeforeDeSplitsBackIntoContext() {
        CwInterpreterSnapshot snapshot = runSequence("KBI9CLTDE BG7YOZ R5NNTU73BK");

        assertTrue(snapshot.normalizedText().contains("K BI9CLT DE BG7YOZ"));
        assertTrue(snapshot.callsignCandidates().contains("BI9CLT"));
        assertTrue(snapshot.callsignCandidates().contains("BG7YOZ"));
        assertTrue(snapshot.phraseHints().contains("Station identification / callsign exchange"));
    }

    @Test
    public void bkPrefixedStationCallsignBeforeDeSplitsBackIntoContext() {
        CwInterpreterSnapshot snapshot = runSequence("BKBI9CLTDE BG7YOZ TU73BK");

        assertTrue(snapshot.normalizedText().contains("BK BI9CLT DE BG7YOZ"));
        assertTrue(snapshot.callsignCandidates().contains("BI9CLT"));
        assertTrue(snapshot.callsignCandidates().contains("BG7YOZ"));
        assertTrue(snapshot.phraseHints().contains("Station identification / callsign exchange"));
    }

    @Test
    public void fullyGluedControlCallsignReportClosingChainStillRecoversBothCallsigns() {
        CwInterpreterSnapshot snapshot = runSequence("BKBI9CLTDEBG7YOZR5NNTU73BK");

        assertTrue(snapshot.normalizedText().contains("BK BI9CLT DE"));
        assertTrue(snapshot.callsignCandidates().contains("BI9CLT"));
        assertTrue(snapshot.callsignCandidates().contains("BG7YOZ"));
        assertFalse(snapshot.callsignCandidates().contains("BG7YOZR"));
        assertTrue(snapshot.phraseHints().contains("Station identification / callsign exchange"));
        assertTrue(snapshot.phraseHints().contains("Report acknowledgement / return report"));
        assertTrue(snapshot.phraseHints().contains("Closing / acknowledgement"));
        assertTrue(snapshot.phraseHints().contains("73 closing"));
    }

    private CwInterpreterSnapshot runSequence(String... messages) {
        CwInterpreter interpreter = new CwInterpreter();
        long timestampMs = 1000L;
        for (String message : messages) {
            interpreter.process(decode(message, timestampMs));
            timestampMs += 1000L;
        }
        return interpreter.snapshot();
    }

    private CwDecodeEvent decode(String message, long timestampMs) {
        return new CwDecodeEvent(
                CwDecodeEvent.Type.CHARACTER_DECODED,
                timestampMs,
                "",
                message,
                ""
        );
    }
}
