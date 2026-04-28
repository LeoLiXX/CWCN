package org.bi9clt.cwcn.core.interpreter;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.decoder.CwDecoder;
import org.junit.Test;

import java.util.List;

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
    public void rawTextStabilizationMergesSplitShortControlAndReportTokens() {
        CwInterpreterSnapshot snapshot = runSequence("BI9CLT DE BG7YOZ UR 5 NN B K");

        assertEquals("BI9CLT DE BG7YOZ UR 5NN BK", snapshot.rawText());
    }

    @Test
    public void rawTextStabilizationSplitsCompactAckCallsignChainsIntoStructuralTokens() {
        CwInterpreterSnapshot snapshot = runSequence("BKBI9CLTDEBG7YOZR5NNTU73BK");

        assertEquals("BK BI9CLT DE BG7YOZ R 5NN TU 73 BK", snapshot.rawText());
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
    public void rememberedSpeakerCallsignUpgradesUncertainSingleCharacterPrefixSplit() {
        CwInterpreter interpreter = new CwInterpreter();

        interpreter.process(decode("BI9CLT DE BG7YOZ UR 5NN BK", 1000L));
        interpreter.process(decode("BI9CLT DE ? G7YOZ UR 5NN BK", 2000L));

        CwInterpreterSnapshot snapshot = interpreter.snapshot();
        assertEquals("BG7YOZ", snapshot.primaryCallsignCandidate());
        assertTrue(snapshot.callsignCandidates().contains("BG7YOZ"));
        assertFalse(snapshot.callsignCandidates().contains("?G7YOZ"));
        assertFalse(snapshot.callsignCandidates().contains("G7YOZ"));
        assertTrue(snapshot.phraseHints().contains("Partial callsign / uncertain copy"));
    }

    @Test
    public void rememberedSpeakerCallsignUpgradesUncertainSingleCharacterSuffixSplit() {
        CwInterpreter interpreter = new CwInterpreter();

        interpreter.process(decode("BI9CLT DE BG7YOZ UR 5NN BK", 1000L));
        interpreter.process(decode("BI9CLT DE BG7YO ? UR 5NN BK", 2000L));

        CwInterpreterSnapshot snapshot = interpreter.snapshot();
        assertEquals("BG7YOZ", snapshot.primaryCallsignCandidate());
        assertTrue(snapshot.callsignCandidates().contains("BG7YOZ"));
        assertFalse(snapshot.callsignCandidates().contains("BG7YO?"));
        assertFalse(snapshot.callsignCandidates().contains("BG7YO"));
        assertTrue(snapshot.phraseHints().contains("Partial callsign / uncertain copy"));
    }

    @Test
    public void rememberedSpeakerCallsignDoesNotOverrideUncertainSpeakerWhenAnotherCleanConflictExists() {
        CwInterpreter interpreter = new CwInterpreter();

        interpreter.process(decode("BI9CLT DE BG7YOZ UR 5NN BK", 1000L));
        interpreter.process(decode("BI9CLT DE BG7Y?Z BG7YQZ TU 73 BK", 2000L));

        CwInterpreterSnapshot snapshot = interpreter.snapshot();
        assertEquals("BG7Y?Z", snapshot.primaryCallsignCandidate());
        assertTrue(snapshot.callsignCandidates().contains("BG7Y?Z"));
        assertTrue(snapshot.callsignCandidates().contains("BG7YQZ"));
        assertFalse(snapshot.callsignCandidates().contains("BG7YOZ"));
        assertTrue(snapshot.phraseHints().contains("Partial callsign / uncertain copy"));
    }

    @Test
    public void ambiguousConflictRoundDoesNotEraseEarlierCleanRememberedSpeakerCallsign() {
        CwInterpreter interpreter = new CwInterpreter();

        interpreter.process(decode("BI9CLT DE BG7YOZ UR 5NN BK", 1000L));
        interpreter.process(decode("BI9CLT DE BG7Y?Z BG7YQZ TU 73 BK", 2000L));
        interpreter.process(decode("BI9CLT DE ? G7YOZ TU 73 BK", 3000L));

        CwInterpreterSnapshot snapshot = interpreter.snapshot();
        assertEquals("BG7YOZ", snapshot.primaryCallsignCandidate());
        assertTrue(snapshot.callsignCandidates().contains("BG7YOZ"));
        assertFalse(snapshot.callsignCandidates().contains("?G7YOZ"));
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
    public void rememberedAddressedCallsignUpgradesUncertainSingleCharacterMiddleSplit() {
        CwInterpreter interpreter = new CwInterpreter();

        interpreter.process(decode("BI9CLT DE BG7YOZ UR 5NN BK", 1000L));
        interpreter.process(decode("BI ?CLT DE BG7YOZ TU 73 BK", 2000L));

        CwInterpreterSnapshot snapshot = interpreter.snapshot();
        assertTrue(snapshot.callsignCandidates().contains("BI9CLT"));
        assertFalse(snapshot.callsignCandidates().contains("BI?CLT"));
        assertEquals("BG7YOZ", snapshot.primaryCallsignCandidate());
        assertTrue(snapshot.phraseHints().contains("Partial callsign / uncertain copy"));
    }

    @Test
    public void rememberedAddressedCallsignUpgradesUncertainSingleCharacterPrefixSplit() {
        CwInterpreter interpreter = new CwInterpreter();

        interpreter.process(decode("BI9CLT DE BG7YOZ UR 5NN BK", 1000L));
        interpreter.process(decode("? I9CLT DE BG7YOZ TU 73 BK", 2000L));

        CwInterpreterSnapshot snapshot = interpreter.snapshot();
        assertTrue(snapshot.callsignCandidates().contains("BI9CLT"));
        assertFalse(snapshot.callsignCandidates().contains("?I9CLT"));
        assertEquals("BG7YOZ", snapshot.primaryCallsignCandidate());
        assertTrue(snapshot.phraseHints().contains("Partial callsign / uncertain copy"));
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
    public void damagedDirectedReportResidueStillNormalizesIntoReportAndHandoffHints() {
        CwInterpreterSnapshot snapshot = runSequence("BI9CLT DE BG7YOZ UR?NN B");

        assertTrue(snapshot.normalizedText().contains("UR 599 BK"));
        assertTrue(snapshot.phraseHints().contains("Report exchange"));
        assertTrue(snapshot.phraseHints().contains("Directed report to called station"));
        assertTrue(snapshot.phraseHints().contains("Turn handoff / over"));
    }

    @Test
    public void damagedCompactReportTailStillNormalizesIntoReportAndHandoffHints() {
        CwInterpreterSnapshot snapshot = runSequence("BI9CLT DE BG7YOZ UR 5NNEB");

        assertTrue(snapshot.normalizedText().contains("UR 599 BK"));
        assertTrue(snapshot.phraseHints().contains("Report exchange"));
        assertTrue(snapshot.phraseHints().contains("Directed report to called station"));
        assertTrue(snapshot.phraseHints().contains("Turn handoff / over"));
    }

    @Test
    public void damagedSeparatedReportAndControlResidueStillNormalizeInContext() {
        CwInterpreterSnapshot snapshot = runSequence("BI9CLT DE BG7YOZ UR ?NN EB");

        assertTrue(snapshot.normalizedText().contains("UR 599 BK"));
        assertTrue(snapshot.phraseHints().contains("Report exchange"));
        assertTrue(snapshot.phraseHints().contains("Directed report to called station"));
        assertTrue(snapshot.phraseHints().contains("Turn handoff / over"));
    }

    @Test
    public void splitReportAndControlWordsMergeBackIntoCompactTokens() {
        CwInterpreterSnapshot snapshot = runSequence("BI9CLT DE BG7YOZ UR 5 NN B K");

        assertTrue(snapshot.normalizedText().contains("UR 599 BK"));
        assertTrue(snapshot.phraseHints().contains("Report exchange"));
        assertTrue(snapshot.phraseHints().contains("Directed report to called station"));
        assertTrue(snapshot.phraseHints().contains("Turn handoff / over"));
    }

    @Test
    public void splitClarificationKeywordMergesBackIntoAgain() {
        CwInterpreterSnapshot snapshot = runSequence("BI9??Z AG N PSE K");

        assertTrue(snapshot.normalizedText().contains("AGAIN PLEASE K"));
        assertTrue(snapshot.callsignCandidates().contains("BI9??Z"));
        assertTrue(snapshot.phraseHints().contains("Repeat / clarification request"));
    }

    @Test
    public void splitClarificationKeywordsDoNotPolluteFullCallsignCandidate() {
        CwInterpreterSnapshot snapshot = runSequence("BI9CLT AG N PS E K");

        assertTrue(snapshot.normalizedText().contains("BI9CLT AGAIN PLEASE K"));
        assertTrue(snapshot.callsignCandidates().contains("BI9CLT"));
        assertFalse(snapshot.callsignCandidates().contains("BI9CLTAG"));
        assertTrue(snapshot.phraseHints().contains("Repeat / clarification request"));
    }

    @Test
    public void rememberedPrimaryCallsignDoesNotStayPollutedByClarificationPrefixResidue() {
        CwInterpreter interpreter = new CwInterpreter();

        interpreter.process(decode("BI9CLTAG", 1000L));
        interpreter.process(decode("BI9CLT AG N PS E K", 2000L));

        CwInterpreterSnapshot snapshot = interpreter.snapshot();
        assertTrue(snapshot.normalizedText().contains("BI9CLT AGAIN PLEASE K"));
        assertEquals("BI9CLT", snapshot.primaryCallsignCandidate());
        assertTrue(snapshot.callsignCandidates().contains("BI9CLT"));
        assertFalse(snapshot.callsignCandidates().contains("BI9CLTAG"));
    }

    @Test
    public void splitDeAndCallsignFragmentsRecoverStationIdentificationFlow() {
        CwInterpreterSnapshot snapshot = runSequence("BI9CLT D E BG 7YOZ UR 5NN BK");

        assertTrue(snapshot.normalizedText().contains("BI9CLT DE BG7YOZ UR 599 BK"));
        assertTrue(snapshot.callsignCandidates().contains("BI9CLT"));
        assertTrue(snapshot.callsignCandidates().contains("BG7YOZ"));
        assertTrue(snapshot.phraseHints().contains("Station identification / callsign exchange"));
        assertTrue(snapshot.phraseHints().contains("Directed report to called station"));
    }

    @Test
    public void singleLetterSpeakerPrefixFragmentMergesBackIntoCallsignInContext() {
        CwInterpreterSnapshot snapshot = runSequence("BI9CLT DE B G7YOZ UR 5NN BK");

        assertTrue(snapshot.normalizedText().contains("BI9CLT DE BG7YOZ UR 599 BK"));
        assertTrue(snapshot.callsignCandidates().contains("BG7YOZ"));
        assertFalse(snapshot.callsignCandidates().contains("G7YOZ"));
        assertTrue(snapshot.phraseHints().contains("Station identification / callsign exchange"));
    }

    @Test
    public void singleLetterSpeakerSuffixFragmentMergesBackIntoCallsignInContext() {
        CwInterpreterSnapshot snapshot = runSequence("BI9CLT DE BG7YO Z UR 5NN BK");

        assertTrue(snapshot.normalizedText().contains("BI9CLT DE BG7YOZ UR 599 BK"));
        assertTrue(snapshot.callsignCandidates().contains("BG7YOZ"));
        assertFalse(snapshot.callsignCandidates().contains("BG7YO"));
        assertTrue(snapshot.phraseHints().contains("Station identification / callsign exchange"));
    }

    @Test
    public void uncertainSpeakerPrefixFragmentMergesBackIntoPartialCallsignInContext() {
        CwInterpreterSnapshot snapshot = runSequence("BI9CLT DE ? G7YOZ UR 5NN BK");

        assertTrue(snapshot.normalizedText().contains("BI9CLT DE ?G7YOZ UR 599 BK"));
        assertTrue(snapshot.callsignCandidates().contains("?G7YOZ"));
        assertFalse(snapshot.callsignCandidates().contains("G7YOZ"));
        assertTrue(snapshot.phraseHints().contains("Partial callsign / uncertain copy"));
    }

    @Test
    public void uncertainSpeakerSuffixFragmentMergesBackIntoPartialCallsignInContext() {
        CwInterpreterSnapshot snapshot = runSequence("BI9CLT DE BG7YO ? UR 5NN BK");

        assertTrue(snapshot.normalizedText().contains("BI9CLT DE BG7YO? UR 599 BK"));
        assertTrue(snapshot.callsignCandidates().contains("BG7YO?"));
        assertFalse(snapshot.callsignCandidates().contains("BG7YO"));
        assertTrue(snapshot.phraseHints().contains("Partial callsign / uncertain copy"));
    }

    @Test
    public void incrementalCharacterGrowthKeepsFullAddressedCallsignAfterSplitReportRecovery() {
        CwInterpreterSnapshot snapshot = runIncrementalSequence("BI9CLT DE BG7YOZ UR 5 NN B K");
        String summary = snapshot.normalizedText() + " | " + snapshot.callsignCandidates() + " | " + snapshot.primaryCallsignCandidate();

        assertTrue(summary, snapshot.normalizedText().contains("BI9CLT DE BG7YOZ UR 599 BK"));
        assertTrue(summary, snapshot.callsignCandidates().contains("BI9CLT"));
        assertTrue(summary, snapshot.callsignCandidates().contains("BG7YOZ"));
        assertFalse(summary, snapshot.callsignCandidates().contains("BI9CL"));
    }

    @Test
    public void incrementalCharacterGrowthKeepsFullAddressedCallsignAfterSplitDeFragments() {
        CwInterpreterSnapshot snapshot = runIncrementalSequence("BI9CLT D E BG 7YOZ UR 5NN BK");
        String summary = snapshot.normalizedText() + " | " + snapshot.callsignCandidates() + " | " + snapshot.primaryCallsignCandidate();

        assertTrue(summary, snapshot.normalizedText().contains("BI9CLT DE BG7YOZ UR 599 BK"));
        assertTrue(summary, snapshot.callsignCandidates().contains("BI9CLT"));
        assertTrue(summary, snapshot.callsignCandidates().contains("BG7YOZ"));
        assertFalse(summary, snapshot.callsignCandidates().contains("BI9CL"));
    }

    @Test
    public void incrementalCharacterGrowthKeepsSpeakerCallsignAfterSingleLetterPrefixSplit() {
        CwInterpreterSnapshot snapshot = runIncrementalSequence("BI9CLT DE B G7YOZ UR 5NN BK");
        String summary = snapshot.normalizedText() + " | " + snapshot.callsignCandidates() + " | " + snapshot.primaryCallsignCandidate();

        assertTrue(summary, snapshot.normalizedText().contains("BI9CLT DE BG7YOZ UR 599 BK"));
        assertTrue(summary, snapshot.callsignCandidates().contains("BI9CLT"));
        assertTrue(summary, snapshot.callsignCandidates().contains("BG7YOZ"));
        assertFalse(summary, snapshot.callsignCandidates().contains("G7YOZ"));
    }

    @Test
    public void incrementalCharacterGrowthKeepsPartialSpeakerCallsignAfterUncertainPrefixSplit() {
        CwInterpreterSnapshot snapshot = runIncrementalSequence("BI9CLT DE ? G7YOZ UR 5NN BK");
        String summary = snapshot.normalizedText() + " | " + snapshot.callsignCandidates() + " | " + snapshot.primaryCallsignCandidate();

        assertTrue(summary, snapshot.normalizedText().contains("BI9CLT DE ?G7YOZ UR 599 BK"));
        assertTrue(summary, snapshot.callsignCandidates().contains("BI9CLT"));
        assertTrue(summary, snapshot.callsignCandidates().contains("?G7YOZ"));
        assertFalse(summary, snapshot.callsignCandidates().contains("G7YOZ"));
    }

    @Test
    public void incrementalCharacterGrowthKeepsAddressedCallsignAfterUncertainMiddleSplit() {
        CwInterpreter interpreter = new CwInterpreter();
        interpreter.process(decode("BI9CLT DE BG7YOZ UR 5NN BK", 1000L));

        long timestampMs = 2000L;
        String message = "BI ?CLT DE BG7YOZ UR 5NN BK";
        for (int index = 1; index <= message.length(); index++) {
            interpreter.process(decode(message.substring(0, index), timestampMs));
            timestampMs += 25L;
        }

        CwInterpreterSnapshot snapshot = interpreter.snapshot();
        String summary = snapshot.normalizedText() + " | " + snapshot.callsignCandidates() + " | " + snapshot.primaryCallsignCandidate();

        assertTrue(summary, snapshot.normalizedText().contains("BI?CLT DE BG7YOZ UR 599 BK"));
        assertTrue(summary, snapshot.callsignCandidates().contains("BI9CLT"));
        assertTrue(summary, snapshot.callsignCandidates().contains("BG7YOZ"));
        assertFalse(summary, snapshot.callsignCandidates().contains("BI?CLT"));
    }

    @Test
    public void damagedAckReportTailStillNormalizesIntoSentReportAndHandoffHints() {
        CwInterpreterSnapshot snapshot = runSequence("BI9CLT DE BG7YOZ R?NNB");

        assertTrue(snapshot.normalizedText().contains("R 599 BK"));
        assertTrue(snapshot.phraseHints().contains("Report acknowledgement / return report"));
        assertTrue(snapshot.phraseHints().contains("Turn handoff / over"));
    }

    @Test
    public void deTailContaminationDoesNotReplaceCleanAddressedCallsignCandidate() {
        CwInterpreter interpreter = new CwInterpreter();

        interpreter.process(decode("BI9CLTD DE BG7YOZ UR?NN B", 1000L));

        CwInterpreterSnapshot snapshot = interpreter.snapshot();
        assertTrue(snapshot.callsignCandidates().contains("BI9CLT"));
        assertTrue(snapshot.callsignCandidates().contains("BG7YOZ"));
        assertFalse(snapshot.callsignCandidates().contains("BI9CLTD"));
        assertFalse(snapshot.callsignCandidates().contains("I9CL"));
        assertFalse(snapshot.callsignCandidates().contains("G7YO"));
    }

    @Test
    public void deTailContaminationAlsoFallsBackToCleanAddressedCallsignInAckReport() {
        CwInterpreter interpreter = new CwInterpreter();

        interpreter.process(decode("BI9CLTD DE BG7YOZ R?NNB", 1000L));

        CwInterpreterSnapshot snapshot = interpreter.snapshot();
        assertTrue(snapshot.callsignCandidates().contains("BI9CLT"));
        assertTrue(snapshot.callsignCandidates().contains("BG7YOZ"));
        assertFalse(snapshot.callsignCandidates().contains("BI9CLTD"));
    }

    @Test
    public void damagedShortResiduesAreExplicitlyMarkedAsNormalizedFromRaw() {
        CwInterpreterSnapshot snapshot = runSequence("BI9CLT DE BG7YOZ UR?NN B");
        List<CwInterpretedToken> tokens = snapshot.tokens();

        boolean sawRecoveredReport = false;
        boolean sawRecoveredControl = false;
        for (CwInterpretedToken token : tokens) {
            if ("599".equals(token.normalizedText()) && token.normalizedFromRaw()) {
                sawRecoveredReport = true;
            }
            if ("BK".equals(token.normalizedText()) && token.normalizedFromRaw()) {
                sawRecoveredControl = true;
            }
        }

        assertTrue(sawRecoveredReport);
        assertTrue(sawRecoveredControl);
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

    @Test
    public void repeatedSelfCallDoesNotLeavePartialRepeatAsPrimaryCallsign() {
        CwInterpreterSnapshot snapshot = runSequence("CQ CQ CQ DE BI9CLT BI9CLT BI9CLT PSE K");

        assertEquals("BI9CLT", snapshot.primaryCallsignCandidate());
        assertTrue(snapshot.callsignCandidates().contains("BI9CLT"));
        assertFalse(snapshot.callsignCandidates().contains("BI9CLTBI9C"));
    }

    @Test
    public void missingWordGapBetweenRepeatedCallsignsIsRecoveredInNormalizedText() {
        CwInterpreterSnapshot snapshot = runSequence("CQ CQ CQ DE BI9CLTBI9CLT PSE K");

        assertTrue(snapshot.normalizedText().contains("CQ CQ CQ DE BI9CLT BI9CLT PLEASE K"));
        assertEquals("BI9CLT", snapshot.primaryCallsignCandidate());
        assertTrue(snapshot.callsignCandidates().contains("BI9CLT"));
        assertFalse(snapshot.callsignCandidates().contains("BI9CLTBI9CLT"));
    }

    @Test
    public void partialRepeatTailAfterCleanCallsignIsSuppressed() {
        CwInterpreterSnapshot snapshot = runSequence("CQ CQ CQ DE BI9CLTBI9C PSE K");

        assertEquals("BI9CLT", snapshot.primaryCallsignCandidate());
        assertTrue(snapshot.callsignCandidates().contains("BI9CLT"));
        assertFalse(snapshot.callsignCandidates().contains("BI9CLTBI9C"));
    }

    @Test
    public void repeatedSelfCallMajorityBeatsNearMissAndPollutedCandidates() {
        CwInterpreterSnapshot snapshot = runSequence(
                "CQ CQ CQ DE BI9CLT BI9CLT BI9CLT PSE K "
                        + "NQ CQ CQ DE BI9CLT BI9CLT GI9CLT PSE K "
                        + "CQ CQ CQ DE BI9GLTBI9C BI9CLT BI9CLA PSE K"
        );

        assertEquals("BI9CLT", snapshot.primaryCallsignCandidate());
        assertTrue(snapshot.callsignCandidates().contains("BI9CLT"));
        assertFalse(snapshot.callsignCandidates().contains("BI9GLTBI9C"));
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

    private CwInterpreterSnapshot runIncrementalSequence(String message) {
        CwInterpreter interpreter = new CwInterpreter();
        long timestampMs = 1000L;
        for (int index = 1; index <= message.length(); index++) {
            interpreter.process(decode(message.substring(0, index), timestampMs));
            timestampMs += 25L;
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
