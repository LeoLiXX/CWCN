package org.bi9clt.cwcn.core.qso;

import org.bi9clt.cwcn.core.log.ConfirmedQsoLog;

import java.util.ArrayList;
import java.util.List;

public final class QsoWorkflowSummaryFormatter {
    private QsoWorkflowSummaryFormatter() {
    }

    public static String renderDraftReviewSummary(QsoDraftSnapshot snapshot) {
        if (snapshot == null) {
            return "No active draft. Start from Debug or type fields here to build one.";
        }

        ArrayList<String> lines = new ArrayList<>();
        lines.add("Status: " + draftStatusLabel(snapshot));
        List<String> reasons = buildDraftReviewReasons(snapshot);
        if (reasons.isEmpty()) {
            lines.add("Reason: no blocking review issue detected.");
        } else {
            lines.add("Reason: " + String.join(" / ", reasons));
        }
        return String.join("\n", lines);
    }

    public static String renderDraftFieldOriginSummary(QsoDraftSnapshot snapshot) {
        if (snapshot == null) {
            return "Field source: no stored or preview draft yet.";
        }

        ArrayList<String> parts = new ArrayList<>();
        parts.add("Station=" + fieldOrigin(snapshot.stationCallsignUsed(), snapshot.stationCallsignManuallySet()));
        parts.add("Remote=" + fieldOrigin(snapshot.remoteCallsignCandidate(), snapshot.remoteCallsignManuallySet()));
        parts.add("RST sent=" + fieldOrigin(snapshot.rstSentCandidate(), snapshot.rstSentManuallySet()));
        parts.add("RST rcvd=" + fieldOrigin(snapshot.rstRcvdCandidate(), snapshot.rstRcvdManuallySet()));
        parts.add("Name=" + fieldOrigin(snapshot.nameCandidate(), snapshot.nameManuallySet()));
        parts.add("QTH=" + fieldOrigin(snapshot.qthCandidate(), snapshot.qthManuallySet()));
        return String.join("\n", parts);
    }

    public static String renderDraftNextStep(QsoDraftSnapshot snapshot, boolean editorDirty) {
        if (editorDirty) {
            return "Save Draft first so the stored draft, review state, and editor all match.";
        }
        if (snapshot == null) {
            return "Type at least a remote callsign or return to Debug to feed live decode.";
        }
        if (isBlank(snapshot.remoteCallsignCandidate())) {
            return "Fill in the remote callsign before confirmation.";
        }
        if (snapshot.needManualReview()) {
            return "Review the uncertain fields, then confirm once the record looks trustworthy.";
        }
        if (!snapshot.readyForDraftConfirmation()) {
            return "Capture at least one RST before confirming the draft.";
        }
        return "Draft is ready. You can confirm now or keep it in local storage for later.";
    }

    public static String renderConfirmedLogReviewSummary(ConfirmedQsoLog log) {
        if (log == null) {
            return "No confirmed log selected.";
        }

        ArrayList<String> lines = new ArrayList<>();
        lines.add("Status: " + confirmedLogStatusLabel(log));
        ArrayList<String> reasons = buildConfirmedLogReviewReasons(log);
        if (reasons.isEmpty()) {
            lines.add("Reason: no review blocker recorded.");
        } else {
            lines.add("Reason: " + String.join(" / ", reasons));
        }

        if (isBlank(log.normalizedText())) {
            lines.add("Evidence: normalized text was not kept.");
        } else {
            lines.add("Evidence: normalized text is available for manual cross-check.");
        }
        return String.join("\n", lines);
    }

    public static String renderConfirmedLogNextStep(ConfirmedQsoLog log) {
        if (log == null) {
            return "Select a log, then review it or load it back into the editor.";
        }
        if (log.needManualReview()) {
            return "Open this log in the editor, verify callsign/RST, then clear the review flag if it checks out.";
        }
        return "This log is ready for export, or you can reopen it in the editor for extra notes.";
    }

    private static ArrayList<String> buildDraftReviewReasons(QsoDraftSnapshot snapshot) {
        ArrayList<String> reasons = new ArrayList<>();
        if (snapshot == null) {
            reasons.add("draft is missing");
            return reasons;
        }
        if (isBlank(snapshot.remoteCallsignCandidate())) {
            reasons.add("remote callsign missing");
        } else if (snapshot.remoteCallsignCandidate().contains("?")) {
            reasons.add("remote callsign still uncertain");
        }
        if (snapshot.phase() == QsoPhase.COMPLETED
                && isBlank(snapshot.rstSentCandidate())
                && isBlank(snapshot.rstRcvdCandidate())) {
            reasons.add("completed QSO has no RST captured");
        } else if (!snapshot.readyForDraftConfirmation()) {
            reasons.add("at least one RST is still missing for confirmation");
        }
        return reasons;
    }

    private static ArrayList<String> buildConfirmedLogReviewReasons(ConfirmedQsoLog log) {
        ArrayList<String> reasons = new ArrayList<>();
        if (log == null) {
            reasons.add("log is missing");
            return reasons;
        }
        if (isBlank(log.remoteCallsign())) {
            reasons.add("remote callsign missing");
        } else if (log.remoteCallsign().contains("?")) {
            reasons.add("remote callsign still uncertain");
        }
        if (isBlank(log.rstSent()) && isBlank(log.rstRcvd())) {
            reasons.add("no RST captured");
        }
        if (log.needManualReview() && reasons.isEmpty()) {
            reasons.add("review flag was kept manually");
        }
        return reasons;
    }

    private static String draftStatusLabel(QsoDraftSnapshot snapshot) {
        if (snapshot.needManualReview()) {
            return "review-needed";
        }
        if (snapshot.readyForDraftConfirmation()) {
            return "ready-to-confirm";
        }
        return "in-progress";
    }

    private static String confirmedLogStatusLabel(ConfirmedQsoLog log) {
        return log.needManualReview() ? "review-queue" : "export-ready";
    }

    private static String fieldOrigin(String value, boolean manuallySet) {
        if (isBlank(value)) {
            return "empty";
        }
        return manuallySet ? "locked" : "live";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
