package org.bi9clt.cwcn.core.qso;

import static org.junit.Assert.assertTrue;

import org.bi9clt.cwcn.core.log.ConfirmedQsoLog;

import org.junit.Test;

import java.util.Collections;

public final class QsoWorkflowSummaryFormatterTest {
    @Test
    public void draftReviewSummaryExplainsMissingCallsignAndRst() {
        QsoDraftSnapshot snapshot = new QsoDraftSnapshot(
                QsoPhase.COMPLETED,
                "BI9CLT",
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                false,
                false,
                false,
                "TU 73",
                Collections.emptyList(),
                false,
                true,
                1234L,
                new QsoStateEvent(1234L, QsoPhase.COMPLETED, "completed")
        );

        String summary = QsoWorkflowSummaryFormatter.renderDraftReviewSummary(snapshot);

        assertTrue(summary.contains("Status: review-needed"));
        assertTrue(summary.contains("remote callsign missing"));
        assertTrue(summary.contains("completed QSO has no RST captured"));
    }

    @Test
    public void draftFieldOriginSummaryDifferentiatesLockedLiveAndEmpty() {
        QsoDraftSnapshot snapshot = new QsoDraftSnapshot(
                QsoPhase.REPORT_EXCHANGE,
                "BI9CLT",
                "BG7YOZ",
                "599",
                null,
                null,
                "SHANGHAI",
                true,
                false,
                true,
                false,
                false,
                true,
                "BG7YOZ UR 599",
                Collections.singletonList("partial-callsign-resolved"),
                true,
                false,
                1234L,
                null
        );

        String summary = QsoWorkflowSummaryFormatter.renderDraftFieldOriginSummary(snapshot);

        assertTrue(summary.contains("Station=locked"));
        assertTrue(summary.contains("Remote=live"));
        assertTrue(summary.contains("RST sent=locked"));
        assertTrue(summary.contains("RST rcvd=empty"));
        assertTrue(summary.contains("QTH=locked"));
    }

    @Test
    public void draftNextStepPrefersSavingDirtyEditorState() {
        QsoDraftSnapshot snapshot = new QsoDraftSnapshot(
                QsoPhase.REPORT_EXCHANGE,
                "BI9CLT",
                "BG7YOZ",
                "599",
                null,
                null,
                null,
                false,
                false,
                false,
                false,
                false,
                false,
                "",
                Collections.emptyList(),
                true,
                false,
                1234L,
                null
        );

        String nextStep = QsoWorkflowSummaryFormatter.renderDraftNextStep(snapshot, true);

        assertTrue(nextStep.contains("Save Draft first"));
    }

    @Test
    public void confirmedLogReviewSummaryExplainsUncertainCallsign() {
        ConfirmedQsoLog log = new ConfirmedQsoLog(
                7L,
                "BG7?OZ",
                "20260423",
                "123000",
                "CW",
                "599",
                null,
                null,
                null,
                "BI9CLT",
                "completed",
                "BI9CLT DE BG7?OZ TU 73 BK",
                true,
                1234L
        );

        String summary = QsoWorkflowSummaryFormatter.renderConfirmedLogReviewSummary(log);
        String nextStep = QsoWorkflowSummaryFormatter.renderConfirmedLogNextStep(log);

        assertTrue(summary.contains("Status: review-queue"));
        assertTrue(summary.contains("remote callsign still uncertain"));
        assertTrue(summary.contains("normalized text is available"));
        assertTrue(nextStep.contains("Open this log in the editor"));
    }
}
