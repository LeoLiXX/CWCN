package org.bi9clt.cwcn.core.qso;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.decoder.CwDecoder;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.interpreter.CwInterpreterSnapshot;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class CwConversationSemanticsTest {
    @Test
    public void partialRepeatRequestDoesNotBecomeReportExchange() {
        QsoDraftSnapshot snapshot = runConversation("BI9??Z UR CALL AGAIN PSE K");

        assertEquals(QsoPhase.REPLY_DETECTED, snapshot.phase());
        assertEquals("BI9??Z", snapshot.remoteCallsignCandidate());
        assertNull(snapshot.rstSentCandidate());
        assertNull(snapshot.rstRcvdCandidate());
        assertTrue(snapshot.needManualReview());
        assertTrue(snapshot.hints().contains("Repeat / clarification request"));
    }

    @Test
    public void qrzLoopStaysInCallingFlow() {
        QsoDraftSnapshot snapshot = runConversation("QRZ QRZ DE BG7YOZ BG7YOZ K");

        assertEquals(QsoPhase.CALLING_CQ, snapshot.phase());
        assertEquals("BG7YOZ", snapshot.remoteCallsignCandidate());
        assertTrue(snapshot.hints().contains("QRZ / next caller flow"));
    }

    @Test
    public void questionedQrzAndKnStillCountAsCallingFlowAndHandoff() {
        QsoDraftSnapshot snapshot = runConversation("QRZ? DE BG7YOZ KN");

        assertEquals(QsoPhase.CALLING_CQ, snapshot.phase());
        assertEquals("BG7YOZ", snapshot.remoteCallsignCandidate());
        assertTrue(snapshot.hints().contains("QRZ / next caller flow"));
        assertTrue(snapshot.hints().contains("Turn handoff / over"));
    }

    @Test
    public void agnQuestionMarkAndKnNormalizeToClarificationFlow() {
        QsoDraftSnapshot snapshot = runConversation("BI9??Z AGN? CALLSIGN PSE KN");

        assertEquals(QsoPhase.REPLY_DETECTED, snapshot.phase());
        assertEquals("BI9??Z", snapshot.remoteCallsignCandidate());
        assertTrue(snapshot.needManualReview());
        assertTrue(snapshot.hints().contains("Repeat / clarification request"));
        assertTrue(snapshot.hints().contains("Callsign confirmation cycle"));
        assertTrue(snapshot.hints().contains("Turn handoff / over"));
    }

    @Test
    public void gluedRepeatedCallsignRunNormalizesIntoCallingFlow() {
        QsoDraftSnapshot snapshot = runConversation("CQCQCQ DE BI9CLTBI9CLTBI 9CL T");

        assertEquals(QsoPhase.CALLING_CQ, snapshot.phase());
        assertEquals("BI9CLT", snapshot.remoteCallsignCandidate());
        assertTrue(snapshot.hints().contains("CQ / calling flow"));
        assertTrue(snapshot.hints().contains("Station identification / callsign exchange"));
        assertTrue(!snapshot.needManualReview());
    }

    @Test
    public void keywordPrefixedCallsignSplitsIntoDeAndCallsign() {
        QsoDraftSnapshot snapshot = runConversation("CQ CQ DEBI9CLT K");

        assertEquals(QsoPhase.CALLING_CQ, snapshot.phase());
        assertEquals("BI9CLT", snapshot.remoteCallsignCandidate());
        assertTrue(snapshot.hints().contains("CQ / calling flow"));
        assertTrue(snapshot.hints().contains("Station identification / callsign exchange"));
    }

    @Test
    public void cqDeCallsignChainSplitsAcrossMultipleStickyKeywords() {
        QsoDraftSnapshot snapshot = runConversation("CQDEBI9CLT K");

        assertEquals(QsoPhase.CALLING_CQ, snapshot.phase());
        assertEquals("BI9CLT", snapshot.remoteCallsignCandidate());
        assertTrue(snapshot.hints().contains("CQ / calling flow"));
        assertTrue(snapshot.hints().contains("Station identification / callsign exchange"));
    }

    @Test
    public void spacedCallsignFragmentsMergeIntoSingleCallsign() {
        QsoDraftSnapshot snapshot = runConversation("CQ CQ DE BI9 CLT K");

        assertEquals(QsoPhase.CALLING_CQ, snapshot.phase());
        assertEquals("BI9CLT", snapshot.remoteCallsignCandidate());
        assertTrue(snapshot.hints().contains("Station identification / callsign exchange"));
        assertTrue(!snapshot.needManualReview());
    }

    @Test
    public void spacedCallsignFragmentsAlsoWorkInDirectedExchange() {
        QsoDraftSnapshot snapshot = runConversation("BI9 CLT DE BG7 YOZ UR 5NN BK");

        assertEquals(QsoPhase.REPORT_EXCHANGE, snapshot.phase());
        assertEquals("BI9CLT", snapshot.stationCallsignUsed());
        assertEquals("BG7YOZ", snapshot.remoteCallsignCandidate());
        assertEquals("599", snapshot.rstRcvdCandidate());
        assertTrue(snapshot.hints().contains("Directed report to called station"));
    }

    @Test
    public void gluedDeBetweenTwoCallsignsSplitsIntoDirectedExchange() {
        QsoDraftSnapshot snapshot = runConversation("BI9CLTDEBG7YOZ UR 5NN BK");

        assertEquals(QsoPhase.REPORT_EXCHANGE, snapshot.phase());
        assertEquals("BI9CLT", snapshot.stationCallsignUsed());
        assertEquals("BG7YOZ", snapshot.remoteCallsignCandidate());
        assertEquals("599", snapshot.rstRcvdCandidate());
        assertTrue(snapshot.hints().contains("Directed report to called station"));
    }

    @Test
    public void gluedReportAndHandoffStillRecoverDirectedExchange() {
        QsoDraftSnapshot snapshot = runConversation("BI9CLT DE BG7YOZ UR5NNBK");

        assertEquals(QsoPhase.REPORT_EXCHANGE, snapshot.phase());
        assertEquals("BI9CLT", snapshot.stationCallsignUsed());
        assertEquals("BG7YOZ", snapshot.remoteCallsignCandidate());
        assertEquals("599", snapshot.rstRcvdCandidate());
        assertTrue(snapshot.hints().contains("Directed report to called station"));
        assertTrue(snapshot.hints().contains("Turn handoff / over"));
    }

    @Test
    public void compactDirectedExchangeChainRecoversCallsignReportAndHandoff() {
        QsoDraftSnapshot snapshot = runConversation("BI9CLT DEBG7YOZUR5NNBK");

        assertEquals(QsoPhase.REPORT_EXCHANGE, snapshot.phase());
        assertEquals("BI9CLT", snapshot.stationCallsignUsed());
        assertEquals("BG7YOZ", snapshot.remoteCallsignCandidate());
        assertEquals("599", snapshot.rstRcvdCandidate());
        assertTrue(snapshot.hints().contains("Station identification / callsign exchange"));
        assertTrue(snapshot.hints().contains("Directed report to called station"));
        assertTrue(snapshot.hints().contains("Turn handoff / over"));
    }

    @Test
    public void damagedDirectedReportResidueStillRecoversReceivedReportAndHandoff() {
        QsoDraftSnapshot snapshot = runConversation("BI9CLT DE BG7YOZ UR?NN B");

        assertEquals(QsoPhase.REPORT_EXCHANGE, snapshot.phase());
        assertEquals("BI9CLT", snapshot.stationCallsignUsed());
        assertEquals("BG7YOZ", snapshot.remoteCallsignCandidate());
        assertEquals("599", snapshot.rstRcvdCandidate());
        assertTrue(snapshot.hints().contains("Directed report to called station"));
        assertTrue(snapshot.hints().contains("Turn handoff / over"));
    }

    @Test
    public void damagedCompactReportTailStillRecoversReceivedReportAndHandoff() {
        QsoDraftSnapshot snapshot = runConversation("BI9CLT DE BG7YOZ UR 5NNEB");

        assertEquals(QsoPhase.REPORT_EXCHANGE, snapshot.phase());
        assertEquals("BI9CLT", snapshot.stationCallsignUsed());
        assertEquals("BG7YOZ", snapshot.remoteCallsignCandidate());
        assertEquals("599", snapshot.rstRcvdCandidate());
        assertTrue(snapshot.hints().contains("Directed report to called station"));
        assertTrue(snapshot.hints().contains("Turn handoff / over"));
    }

    @Test
    public void damagedSeparatedReportAndControlResidueStillRecoversReceivedReport() {
        QsoDraftSnapshot snapshot = runConversation("BI9CLT DE BG7YOZ UR ?NN EB");

        assertEquals(QsoPhase.REPORT_EXCHANGE, snapshot.phase());
        assertEquals("BI9CLT", snapshot.stationCallsignUsed());
        assertEquals("BG7YOZ", snapshot.remoteCallsignCandidate());
        assertEquals("599", snapshot.rstRcvdCandidate());
        assertTrue(snapshot.hints().contains("Directed report to called station"));
        assertTrue(snapshot.hints().contains("Turn handoff / over"));
    }

    @Test
    public void damagedAckReportTailStillRecoversSentReportAndHandoff() {
        QsoDraftSnapshot snapshot = runConversation("BI9CLT DE BG7YOZ R?NNB");

        assertEquals(QsoPhase.REPORT_EXCHANGE, snapshot.phase());
        assertEquals("BI9CLT", snapshot.stationCallsignUsed());
        assertEquals("BG7YOZ", snapshot.remoteCallsignCandidate());
        assertEquals("599", snapshot.rstSentCandidate());
        assertNull(snapshot.rstRcvdCandidate());
        assertTrue(snapshot.hints().contains("Report acknowledgement / return report"));
        assertTrue(snapshot.hints().contains("Turn handoff / over"));
    }

    @Test
    public void qrzDeCallsignChainStillCountsAsCallingFlow() {
        QsoDraftSnapshot snapshot = runConversation("QRZDEBG7YOZ K");

        assertEquals(QsoPhase.CALLING_CQ, snapshot.phase());
        assertEquals("BG7YOZ", snapshot.remoteCallsignCandidate());
        assertTrue(snapshot.hints().contains("QRZ / next caller flow"));
        assertTrue(snapshot.hints().contains("Station identification / callsign exchange"));
    }

    @Test
    public void ackClosingExchangeProducesCompletedPhaseAndSentReport() {
        QsoDraftSnapshot snapshot = runConversation("BI9CLT DE BG7YOZ R 5NN TU 73 BK");

        assertEquals(QsoPhase.COMPLETED, snapshot.phase());
        assertEquals("BG7YOZ", snapshot.remoteCallsignCandidate());
        assertEquals("599", snapshot.rstSentCandidate());
        assertNull(snapshot.rstRcvdCandidate());
        assertTrue(snapshot.readyForDraftConfirmation());
        assertTrue(snapshot.hints().contains("73 closing"));
    }

    @Test
    public void compactClosingChainStillProducesCompletedPhase() {
        QsoDraftSnapshot snapshot = runConversation("BI9CLT DE BG7YOZ TU73BK");

        assertEquals(QsoPhase.CLOSING, snapshot.phase());
        assertEquals("BG7YOZ", snapshot.remoteCallsignCandidate());
        assertNull(snapshot.rstSentCandidate());
        assertTrue(snapshot.hints().contains("Closing / acknowledgement"));
        assertTrue(snapshot.hints().contains("73 closing"));
        assertTrue(snapshot.hints().contains("Turn handoff / over"));
    }

    @Test
    public void compactClosingChainWithTrailingCallsignStillKeepsSpeakerContext() {
        QsoDraftSnapshot snapshot = runConversation("BI9CLT DE BG7YOZ TU73BKBI9CLT");

        assertEquals(QsoPhase.CLOSING, snapshot.phase());
        assertEquals("BI9CLT", snapshot.stationCallsignUsed());
        assertEquals("BG7YOZ", snapshot.remoteCallsignCandidate());
        assertNull(snapshot.rstSentCandidate());
        assertTrue(snapshot.hints().contains("Closing / acknowledgement"));
        assertTrue(snapshot.hints().contains("73 closing"));
        assertTrue(snapshot.hints().contains("Turn handoff / over"));
    }

    @Test
    public void compactReportHandoffWithTrailingCallsignStillKeepsDirectedExchange() {
        QsoDraftSnapshot snapshot = runConversation("BI9CLT DE BG7YOZ UR5NNBKBI9CLT");

        assertEquals(QsoPhase.REPORT_EXCHANGE, snapshot.phase());
        assertEquals("BI9CLT", snapshot.stationCallsignUsed());
        assertEquals("BG7YOZ", snapshot.remoteCallsignCandidate());
        assertEquals("599", snapshot.rstRcvdCandidate());
        assertTrue(snapshot.hints().contains("Directed report to called station"));
        assertTrue(snapshot.hints().contains("Turn handoff / over"));
    }

    @Test
    public void compactAckReportHandoffWithTrailingCallsignStillKeepsSpeakerContext() {
        QsoDraftSnapshot snapshot = runConversation("BI9CLT DE BG7YOZ R5NNBKBI9CLT");

        assertEquals(QsoPhase.REPORT_EXCHANGE, snapshot.phase());
        assertEquals("BI9CLT", snapshot.stationCallsignUsed());
        assertEquals("BG7YOZ", snapshot.remoteCallsignCandidate());
        assertEquals("599", snapshot.rstSentCandidate());
        assertNull(snapshot.rstRcvdCandidate());
        assertTrue(snapshot.hints().contains("Report acknowledgement / return report"));
        assertTrue(snapshot.hints().contains("Turn handoff / over"));
    }

    @Test
    public void compactDeClosingChainStillSplitsSpeakerAndClosingTokens() {
        QsoDraftSnapshot snapshot = runConversation("BI9CLT DEBG7YOZTU73BK");

        assertEquals(QsoPhase.CLOSING, snapshot.phase());
        assertEquals("BI9CLT", snapshot.stationCallsignUsed());
        assertEquals("BG7YOZ", snapshot.remoteCallsignCandidate());
        assertNull(snapshot.rstSentCandidate());
        assertTrue(snapshot.hints().contains("Station identification / callsign exchange"));
        assertTrue(snapshot.hints().contains("Closing / acknowledgement"));
        assertTrue(snapshot.hints().contains("73 closing"));
    }

    @Test
    public void compactAckReportClosingChainStillProducesSentReportAndCompletion() {
        QsoDraftSnapshot snapshot = runConversation("BI9CLT DE BG7YOZ R5NNTU73BK");

        assertEquals(QsoPhase.COMPLETED, snapshot.phase());
        assertEquals("BG7YOZ", snapshot.remoteCallsignCandidate());
        assertEquals("599", snapshot.rstSentCandidate());
        assertNull(snapshot.rstRcvdCandidate());
        assertTrue(snapshot.readyForDraftConfirmation());
        assertTrue(snapshot.hints().contains("Report acknowledgement / return report"));
        assertTrue(snapshot.hints().contains("Closing / acknowledgement"));
        assertTrue(snapshot.hints().contains("73 closing"));
    }

    @Test
    public void degradedLeadingEdgeBeforeDeStillKeepsStationContextAcrossClosingFollowup() {
        QsoDraftSnapshot snapshot = runConversationSequence(
                "BI9CLT DE BG7YOZ UR 5NN BK",
                CwDecoder.UNKNOWN_CHARACTER + "DE BG7YOZ R5NNTU73B"
        );

        assertEquals(QsoPhase.COMPLETED, snapshot.phase());
        assertEquals("BI9CLT", snapshot.stationCallsignUsed());
        assertEquals("BG7YOZ", snapshot.remoteCallsignCandidate());
        assertEquals("599", snapshot.rstSentCandidate());
        assertEquals("599", snapshot.rstRcvdCandidate());
        assertTrue(snapshot.readyForDraftConfirmation());
        assertTrue(snapshot.hints().contains("Station identification / callsign exchange"));
        assertTrue(snapshot.hints().contains("73 closing"));
    }

    @Test
    public void controlPrefixedStationCallsignBeforeDeStillKeepsCompletedClosingContext() {
        QsoDraftSnapshot snapshot = runConversation("KBI9CLTDE BG7YOZ R5NNTU73BK");

        assertEquals(QsoPhase.COMPLETED, snapshot.phase());
        assertEquals("BI9CLT", snapshot.stationCallsignUsed());
        assertEquals("BG7YOZ", snapshot.remoteCallsignCandidate());
        assertEquals("599", snapshot.rstSentCandidate());
        assertNull(snapshot.rstRcvdCandidate());
        assertTrue(snapshot.readyForDraftConfirmation());
        assertTrue(snapshot.hints().contains("Station identification / callsign exchange"));
    }

    @Test
    public void bkPrefixedStationCallsignBeforeDeStillKeepsClosingContext() {
        QsoDraftSnapshot snapshot = runConversation("BKBI9CLTDE BG7YOZ TU73BK");

        assertEquals(QsoPhase.CLOSING, snapshot.phase());
        assertEquals("BI9CLT", snapshot.stationCallsignUsed());
        assertEquals("BG7YOZ", snapshot.remoteCallsignCandidate());
        assertNull(snapshot.rstSentCandidate());
        assertTrue(snapshot.hints().contains("Station identification / callsign exchange"));
        assertTrue(snapshot.hints().contains("Closing / acknowledgement"));
        assertTrue(snapshot.hints().contains("73 closing"));
    }

    @Test
    public void fullyGluedControlCallsignReportClosingChainStillCompletesQso() {
        QsoDraftSnapshot snapshot = runConversation("BKBI9CLTDEBG7YOZR5NNTU73BK");

        assertEquals(QsoPhase.COMPLETED, snapshot.phase());
        assertEquals("BI9CLT", snapshot.stationCallsignUsed());
        assertEquals("BG7YOZ", snapshot.remoteCallsignCandidate());
        assertEquals("599", snapshot.rstSentCandidate());
        assertNull(snapshot.rstRcvdCandidate());
        assertTrue(snapshot.readyForDraftConfirmation());
        assertTrue(snapshot.hints().contains("Station identification / callsign exchange"));
        assertTrue(snapshot.hints().contains("Report acknowledgement / return report"));
        assertTrue(snapshot.hints().contains("Closing / acknowledgement"));
        assertTrue(snapshot.hints().contains("73 closing"));
    }

    @Test
    public void multiRoundClarificationConvergesFromPartialToFullContext() {
        QsoDraftSnapshot snapshot = runConversationSequence(
                "BI9??Z UR CALL AGAIN PSE K",
                "BI9CLZ DE BG7YOZ UR 5NN BK"
        );

        assertEquals(QsoPhase.REPORT_EXCHANGE, snapshot.phase());
        assertEquals("BI9CLZ", snapshot.stationCallsignUsed());
        assertEquals("BG7YOZ", snapshot.remoteCallsignCandidate());
        assertEquals("599", snapshot.rstRcvdCandidate());
        assertNull(snapshot.rstSentCandidate());
        assertTrue(snapshot.hints().contains("Report exchange"));
        assertTrue(snapshot.hints().contains("Directed report to called station"));
    }

    @Test
    public void fullRemoteCallsignDoesNotRegressWhenLaterRoundIsPartial() {
        QsoDraftSnapshot snapshot = runConversationSequence(
                "BI9CLZ DE BG7YOZ UR 5NN BK",
                "BG7?OZ TU 73 BK"
        );

        assertEquals(QsoPhase.COMPLETED, snapshot.phase());
        assertEquals("BG7YOZ", snapshot.remoteCallsignCandidate());
        assertEquals("599", snapshot.rstRcvdCandidate());
        assertTrue(snapshot.readyForDraftConfirmation());
    }

    @Test
    public void multiRoundResolutionWithClosingCompletesQso() {
        QsoDraftSnapshot snapshot = runConversationSequence(
                "BI9??Z UR CALL AGAIN PSE K",
                "BI9CLZ DE BG7YOZ UR 5NN BK",
                "BI9CLZ DE BG7YOZ TU 73 BK"
        );

        assertEquals(QsoPhase.COMPLETED, snapshot.phase());
        assertEquals("BI9CLZ", snapshot.stationCallsignUsed());
        assertEquals("BG7YOZ", snapshot.remoteCallsignCandidate());
        assertEquals("599", snapshot.rstRcvdCandidate());
        assertNull(snapshot.rstSentCandidate());
        assertTrue(snapshot.readyForDraftConfirmation());
        assertTrue(!snapshot.needManualReview());
        assertTrue(snapshot.hints().contains("73 closing"));
    }

    private QsoDraftSnapshot runConversation(String message) {
        return runConversationSequence(message);
    }

    private QsoDraftSnapshot runConversationSequence(String... messages) {
        CwInterpreter interpreter = new CwInterpreter();
        QsoStateMachine qsoStateMachine = new QsoStateMachine();
        long timestampMs = 1234L;
        for (String message : messages) {
            interpreter.process(new CwDecodeEvent(
                    CwDecodeEvent.Type.CHARACTER_DECODED,
                    timestampMs,
                    "",
                    message,
                    ""
            ));
            CwInterpreterSnapshot interpreterSnapshot = interpreter.snapshot();
            qsoStateMachine.process(interpreterSnapshot, timestampMs);
            timestampMs += 1000L;
        }
        return qsoStateMachine.snapshot();
    }
}
