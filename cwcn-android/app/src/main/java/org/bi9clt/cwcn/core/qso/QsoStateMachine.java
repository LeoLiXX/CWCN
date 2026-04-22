package org.bi9clt.cwcn.core.qso;

import org.bi9clt.cwcn.core.interpreter.CwInterpretedToken;
import org.bi9clt.cwcn.core.interpreter.CwInterpreterSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class QsoStateMachine {
    private QsoPhase phase = QsoPhase.IDLE;
    private String autoStationCallsignUsed;
    private String autoRemoteCallsignCandidate;
    private String autoRstSentCandidate;
    private String autoRstRcvdCandidate;
    private String autoNameCandidate;
    private String autoQthCandidate;
    private String manualStationCallsignUsed;
    private String manualRemoteCallsignCandidate;
    private String manualRstSentCandidate;
    private String manualRstRcvdCandidate;
    private String manualNameCandidate;
    private String manualQthCandidate;
    private String normalizedText = "";
    private List<String> hints = new ArrayList<>();
    private boolean readyForDraftConfirmation;
    private boolean needManualReview;
    private long updatedAtEpochMs;
    private QsoStateEvent lastEvent;

    public synchronized void process(CwInterpreterSnapshot snapshot, long timestampMs) {
        normalizedText = snapshot.normalizedText();
        hints = new ArrayList<>(snapshot.phraseHints());

        ContextualCallsigns contextualCallsigns = extractContextualCallsigns(snapshot);
        if (contextualCallsigns.addressedCallsign != null) {
            autoStationCallsignUsed = upgradeCallsignCandidate(
                    autoStationCallsignUsed,
                    contextualCallsigns.addressedCallsign
            );
        }

        String newCandidate = chooseRemoteCallsignCandidate(snapshot, contextualCallsigns);
        if (newCandidate != null && !newCandidate.isEmpty()) {
            autoRemoteCallsignCandidate = upgradeCallsignCandidate(autoRemoteCallsignCandidate, newCandidate);
        }

        ReportSemantics reportSemantics = extractReportSemantics(snapshot);
        String detectedSentReport = reportSemantics.sentReport;
        if (detectedSentReport != null) {
            autoRstSentCandidate = detectedSentReport;
        }

        String detectedReceivedReport = reportSemantics.receivedReport;
        if (detectedReceivedReport != null) {
            autoRstRcvdCandidate = detectedReceivedReport;
        }

        String detectedName = extractFieldValue(snapshot, "NAME");
        if (detectedName != null) {
            autoNameCandidate = detectedName;
        }

        String detectedQth = extractFieldValue(snapshot, "QTH");
        if (detectedQth != null) {
            autoQthCandidate = detectedQth;
        }

        QsoPhase previousPhase = phase;
        phase = derivePhase(snapshot, reportSemantics);
        readyForDraftConfirmation = phase == QsoPhase.COMPLETED
                || (effectiveRemoteCallsign() != null
                && (effectiveRstSent() != null || effectiveRstRcvd() != null)
                && phase == QsoPhase.CLOSING);
        needManualReview = effectiveRemoteCallsign() == null
                || effectiveRemoteCallsign().contains("?")
                || (phase == QsoPhase.COMPLETED && (effectiveRstSent() == null && effectiveRstRcvd() == null));
        updatedAtEpochMs = System.currentTimeMillis();

        String summary = buildSummary(previousPhase, phase);
        lastEvent = new QsoStateEvent(timestampMs, phase, summary);
    }

    public synchronized void applyManualCorrections(
            String stationCallsign,
            String remoteCallsign,
            String rstSent,
            String rstRcvd,
            String name,
            String qth,
            long timestampMs
    ) {
        manualStationCallsignUsed = normalizeOptionalField(stationCallsign);
        manualRemoteCallsignCandidate = normalizeOptionalField(remoteCallsign);
        manualRstSentCandidate = normalizeOptionalField(rstSent);
        manualRstRcvdCandidate = normalizeOptionalField(rstRcvd);
        manualNameCandidate = normalizeOptionalField(name);
        manualQthCandidate = normalizeOptionalField(qth);
        updatedAtEpochMs = timestampMs;
        readyForDraftConfirmation = phase == QsoPhase.COMPLETED
                || (effectiveRemoteCallsign() != null
                && (effectiveRstSent() != null || effectiveRstRcvd() != null)
                && phase == QsoPhase.CLOSING);
        needManualReview = effectiveRemoteCallsign() == null
                || effectiveRemoteCallsign().contains("?")
                || (phase == QsoPhase.COMPLETED && (effectiveRstSent() == null && effectiveRstRcvd() == null));
        lastEvent = new QsoStateEvent(timestampMs, phase, buildSummary(phase, phase) + " | manual-edit");
    }

    public synchronized void clearManualCorrections(long timestampMs) {
        manualStationCallsignUsed = null;
        manualRemoteCallsignCandidate = null;
        manualRstSentCandidate = null;
        manualRstRcvdCandidate = null;
        manualNameCandidate = null;
        manualQthCandidate = null;
        updatedAtEpochMs = timestampMs;
        readyForDraftConfirmation = phase == QsoPhase.COMPLETED
                || (effectiveRemoteCallsign() != null
                && (effectiveRstSent() != null || effectiveRstRcvd() != null)
                && phase == QsoPhase.CLOSING);
        needManualReview = effectiveRemoteCallsign() == null
                || effectiveRemoteCallsign().contains("?")
                || (phase == QsoPhase.COMPLETED && (effectiveRstSent() == null && effectiveRstRcvd() == null));
        lastEvent = new QsoStateEvent(timestampMs, phase, buildSummary(phase, phase) + " | manual-reset");
    }

    public synchronized void reset() {
        phase = QsoPhase.IDLE;
        autoStationCallsignUsed = null;
        autoRemoteCallsignCandidate = null;
        autoRstSentCandidate = null;
        autoRstRcvdCandidate = null;
        autoNameCandidate = null;
        autoQthCandidate = null;
        manualStationCallsignUsed = null;
        manualRemoteCallsignCandidate = null;
        manualRstSentCandidate = null;
        manualRstRcvdCandidate = null;
        manualNameCandidate = null;
        manualQthCandidate = null;
        normalizedText = "";
        hints = new ArrayList<>();
        readyForDraftConfirmation = false;
        needManualReview = false;
        updatedAtEpochMs = 0L;
        lastEvent = null;
    }

    public synchronized void restoreDraft(QsoDraftSnapshot snapshot) {
        if (snapshot == null) {
            reset();
            return;
        }

        phase = snapshot.phase();
        autoStationCallsignUsed = snapshot.stationCallsignUsed();
        autoRemoteCallsignCandidate = snapshot.remoteCallsignCandidate();
        autoRstSentCandidate = snapshot.rstSentCandidate();
        autoRstRcvdCandidate = snapshot.rstRcvdCandidate();
        autoNameCandidate = snapshot.nameCandidate();
        autoQthCandidate = snapshot.qthCandidate();
        manualStationCallsignUsed = snapshot.stationCallsignManuallySet() ? snapshot.stationCallsignUsed() : null;
        manualRemoteCallsignCandidate = snapshot.remoteCallsignManuallySet() ? snapshot.remoteCallsignCandidate() : null;
        manualRstSentCandidate = snapshot.rstSentManuallySet() ? snapshot.rstSentCandidate() : null;
        manualRstRcvdCandidate = snapshot.rstRcvdManuallySet() ? snapshot.rstRcvdCandidate() : null;
        manualNameCandidate = snapshot.nameManuallySet() ? snapshot.nameCandidate() : null;
        manualQthCandidate = snapshot.qthManuallySet() ? snapshot.qthCandidate() : null;
        normalizedText = snapshot.normalizedText() == null ? "" : snapshot.normalizedText();
        hints = new ArrayList<>(snapshot.hints());
        readyForDraftConfirmation = snapshot.readyForDraftConfirmation();
        needManualReview = snapshot.needManualReview();
        updatedAtEpochMs = snapshot.updatedAtEpochMs();
        lastEvent = snapshot.lastEvent();
    }

    public synchronized QsoDraftSnapshot snapshot() {
        return new QsoDraftSnapshot(
                phase,
                effectiveStationCallsign(),
                effectiveRemoteCallsign(),
                effectiveRstSent(),
                effectiveRstRcvd(),
                effectiveName(),
                effectiveQth(),
                manualStationCallsignUsed != null,
                manualRemoteCallsignCandidate != null,
                manualRstSentCandidate != null,
                manualRstRcvdCandidate != null,
                manualNameCandidate != null,
                manualQthCandidate != null,
                normalizedText,
                new ArrayList<>(hints),
                readyForDraftConfirmation,
                needManualReview,
                updatedAtEpochMs,
                lastEvent
        );
    }

    private QsoPhase derivePhase(CwInterpreterSnapshot snapshot, ReportSemantics reportSemantics) {
        String normalized = snapshot.normalizedText().toUpperCase(Locale.US);
        boolean hasText = !normalized.isEmpty();
        boolean hasCallsign = snapshot.primaryCallsignCandidate() != null
                || !snapshot.callsignCandidates().isEmpty()
                || effectiveRemoteCallsign() != null;
        boolean hasCq = containsToken(normalized, "CQ");
        boolean hasQrz = containsToken(normalized, "QRZ");
        boolean hasExplicitReport = reportSemantics != null
                && (reportSemantics.sentReport != null || reportSemantics.receivedReport != null);
        boolean hasLabeledReport = containsReportLabelSequence(snapshot.tokens(), "RST");
        boolean hasReport = hasExplicitReport || hasLabeledReport;
        boolean hasInfo = containsToken(normalized, "NAME")
                || containsToken(normalized, "QTH")
                || containsToken(normalized, "RIG")
                || containsToken(normalized, "WX")
                || containsToken(normalized, "ANT");
        boolean hasRepeatRequest = containsHint(snapshot.phraseHints(), "Repeat / clarification request")
                || containsHint(snapshot.phraseHints(), "Callsign confirmation cycle");
        boolean hasAck = containsToken(normalized, " R ");
        boolean hasTurnHandoff = containsToken(normalized, " BK ")
                || containsToken(normalized, " KN ")
                || normalized.endsWith(" KN")
                || normalized.endsWith(" BK")
                || containsToken(normalized, " K ")
                || normalized.endsWith(" K");
        boolean hasClosing = containsToken(normalized, "THANKS")
                || containsToken(normalized, "73")
                || containsToken(normalized, "EE")
                || containsToken(normalized, "GL");
        boolean hasDe = containsToken(normalized, "DE");

        if (!hasText) {
            return QsoPhase.IDLE;
        }

        if (hasClosing && hasCallsign && (effectiveRstSent() != null || effectiveRstRcvd() != null)) {
            return QsoPhase.COMPLETED;
        }
        if (hasClosing || (hasTurnHandoff && hasAck && (effectiveRstSent() != null || effectiveRstRcvd() != null))) {
            return QsoPhase.CLOSING;
        }
        if (hasReport) {
            return QsoPhase.REPORT_EXCHANGE;
        }
        if (hasInfo) {
            return QsoPhase.INFO_EXCHANGE;
        }
        if (hasCq || hasQrz) {
            return QsoPhase.CALLING_CQ;
        }
        if (hasRepeatRequest && hasCallsign) {
            return QsoPhase.REPLY_DETECTED;
        }
        if (hasCallsign && (hasDe || phase == QsoPhase.AWAITING_REPLY || phase == QsoPhase.CALLING_CQ)) {
            return QsoPhase.REPLY_DETECTED;
        }
        if (phase == QsoPhase.CALLING_CQ || phase == QsoPhase.AWAITING_REPLY) {
            return QsoPhase.AWAITING_REPLY;
        }
        if (hasCallsign) {
            return QsoPhase.REPLY_DETECTED;
        }
        return QsoPhase.IDLE;
    }

    private boolean containsToken(String normalizedText, String token) {
        String padded = " " + normalizedText + " ";
        return padded.contains(" " + token + " ");
    }

    private boolean containsHint(List<String> phraseHints, String hint) {
        if (phraseHints == null || hint == null) {
            return false;
        }
        for (String phraseHint : phraseHints) {
            if (hint.equals(phraseHint)) {
                return true;
            }
        }
        return false;
    }

    private String chooseRemoteCallsignCandidate(
            CwInterpreterSnapshot snapshot,
            ContextualCallsigns contextualCallsigns
    ) {
        if (snapshot == null) {
            return null;
        }
        if (contextualCallsigns != null
                && contextualCallsigns.speakerCallsign != null
                && !contextualCallsigns.speakerCallsign.isEmpty()) {
            return contextualCallsigns.speakerCallsign;
        }
        if (snapshot.primaryCallsignCandidate() != null && !snapshot.primaryCallsignCandidate().isEmpty()) {
            return snapshot.primaryCallsignCandidate();
        }
        List<String> callsignCandidates = snapshot.callsignCandidates();
        if (callsignCandidates == null || callsignCandidates.isEmpty()) {
            return null;
        }
        return callsignCandidates.get(0);
    }

    private String upgradeCallsignCandidate(String existingCandidate, String newCandidate) {
        if (existingCandidate == null || existingCandidate.isEmpty()) {
            return newCandidate;
        }
        String merged = mergeCompatibleCallsigns(existingCandidate, newCandidate);
        if (merged != null) {
            return merged;
        }
        return certaintyScore(newCandidate) >= certaintyScore(existingCandidate)
                ? newCandidate
                : existingCandidate;
    }

    private String mergeCompatibleCallsigns(String left, String right) {
        if (left == null || right == null || left.length() != right.length()) {
            return null;
        }
        StringBuilder merged = new StringBuilder(left.length());
        for (int index = 0; index < left.length(); index++) {
            char leftChar = left.charAt(index);
            char rightChar = right.charAt(index);
            if (leftChar == rightChar) {
                merged.append(leftChar);
            } else if (leftChar == '?') {
                merged.append(rightChar);
            } else if (rightChar == '?') {
                merged.append(leftChar);
            } else {
                return null;
            }
        }
        return merged.toString();
    }

    private int certaintyScore(String callsign) {
        if (callsign == null) {
            return 0;
        }
        int score = 0;
        for (int index = 0; index < callsign.length(); index++) {
            if (callsign.charAt(index) != '?') {
                score += 1;
            }
        }
        return score;
    }

    private ReportSemantics extractReportSemantics(CwInterpreterSnapshot snapshot) {
        if (snapshot == null || snapshot.normalizedText() == null || snapshot.normalizedText().isEmpty()) {
            return new ReportSemantics(null, null);
        }

        String receivedReport = null;
        String sentReport = null;
        List<CwInterpretedToken> tokens = snapshot.tokens();
        for (int index = 0; index < tokens.size(); index++) {
            CwInterpretedToken token = tokens.get(index);
            String normalized = token.normalizedText();

            if ("UR".equals(normalized)) {
                String report = nearestReportAfter(tokens, index);
                if (report != null) {
                    receivedReport = report;
                }
                continue;
            }

            if ("R".equals(normalized)) {
                String report = nearestReportAfter(tokens, index);
                if (report != null) {
                    sentReport = report;
                }
            }
        }

        if (receivedReport == null && sentReport == null) {
            String trailingReport = trailingReport(tokens);
            if (trailingReport != null) {
                receivedReport = trailingReport;
            }
        }

        return new ReportSemantics(sentReport, receivedReport);
    }

    private ContextualCallsigns extractContextualCallsigns(CwInterpreterSnapshot snapshot) {
        if (snapshot == null) {
            return new ContextualCallsigns(null, null);
        }
        List<CwInterpretedToken> tokens = snapshot.tokens();
        String addressedCallsign = null;
        String speakerCallsign = null;
        for (int index = 0; index < tokens.size(); index++) {
            CwInterpretedToken token = tokens.get(index);
            if (token.type() != CwInterpretedToken.Type.DE) {
                continue;
            }
            String before = nearestCallsignBefore(tokens, index);
            String after = nearestCallsignAfter(tokens, index);
            if (before != null) {
                addressedCallsign = before;
            }
            if (after != null) {
                speakerCallsign = after;
            }
        }
        return new ContextualCallsigns(addressedCallsign, speakerCallsign);
    }

    private String nearestCallsignBefore(List<CwInterpretedToken> tokens, int indexExclusive) {
        for (int index = indexExclusive - 1; index >= 0 && index >= indexExclusive - 2; index--) {
            CwInterpretedToken token = tokens.get(index);
            if (token.type() == CwInterpretedToken.Type.CALLSIGN_CANDIDATE) {
                return token.normalizedText();
            }
        }
        return null;
    }

    private String nearestCallsignAfter(List<CwInterpretedToken> tokens, int indexExclusive) {
        for (int index = indexExclusive + 1; index < tokens.size() && index <= indexExclusive + 2; index++) {
            CwInterpretedToken token = tokens.get(index);
            if (token.type() == CwInterpretedToken.Type.CALLSIGN_CANDIDATE) {
                return token.normalizedText();
            }
        }
        return null;
    }

    private String extractFieldValue(CwInterpreterSnapshot snapshot, String label) {
        List<CwInterpretedToken> tokens = snapshot.tokens();
        for (int i = 0; i < tokens.size(); i++) {
            CwInterpretedToken token = tokens.get(i);
            if (label.equals(token.normalizedText()) && i + 1 < tokens.size()) {
                CwInterpretedToken next = tokens.get(i + 1);
                if (next.type() == CwInterpretedToken.Type.FREE_TEXT
                        || next.type() == CwInterpretedToken.Type.CALLSIGN_CANDIDATE) {
                    return next.rawText();
                }
            }
        }
        return null;
    }

    private String nearestReportAfter(List<CwInterpretedToken> tokens, int startIndex) {
        for (int index = startIndex + 1; index < tokens.size() && index <= startIndex + 2; index++) {
            CwInterpretedToken token = tokens.get(index);
            if (token.type() == CwInterpretedToken.Type.REPORT && "599".equals(token.normalizedText())) {
                return token.normalizedText();
            }
        }
        return null;
    }

    private String trailingReport(List<CwInterpretedToken> tokens) {
        for (int index = tokens.size() - 1; index >= 0 && index >= tokens.size() - 3; index--) {
            CwInterpretedToken token = tokens.get(index);
            if (token.type() == CwInterpretedToken.Type.REPORT && "599".equals(token.normalizedText())) {
                return token.normalizedText();
            }
        }
        return null;
    }

    private boolean containsReportLabelSequence(List<CwInterpretedToken> tokens, String reportLabel) {
        if (tokens == null || reportLabel == null) {
            return false;
        }
        for (int index = 0; index < tokens.size() - 1; index++) {
            CwInterpretedToken first = tokens.get(index);
            CwInterpretedToken second = tokens.get(index + 1);
            if (reportLabel.equals(first.normalizedText())
                    && second.type() == CwInterpretedToken.Type.REPORT
                    && "599".equals(second.normalizedText())) {
                return true;
            }
        }
        return false;
    }

    private String buildSummary(QsoPhase previousPhase, QsoPhase currentPhase) {
        StringBuilder builder = new StringBuilder();
        if (previousPhase != currentPhase) {
            builder.append(previousPhase.displayName())
                    .append(" -> ")
                    .append(currentPhase.displayName());
        } else {
            builder.append("phase stable: ").append(currentPhase.displayName());
        }

        if (effectiveRemoteCallsign() != null) {
            builder.append(" | call=").append(effectiveRemoteCallsign());
        }
        if (effectiveRstSent() != null) {
            builder.append(" | rst_s=").append(effectiveRstSent());
        }
        if (effectiveRstRcvd() != null) {
            builder.append(" | rst_r=").append(effectiveRstRcvd());
        }
        if (effectiveName() != null) {
            builder.append(" | name=").append(effectiveName());
        }
        if (effectiveQth() != null) {
            builder.append(" | qth=").append(effectiveQth());
        }
        if (hasManualOverrides()) {
            builder.append(" | manual");
        }
        if (readyForDraftConfirmation) {
            builder.append(" | draft-ready");
        }
        if (needManualReview) {
            builder.append(" | review");
        }
        return builder.toString();
    }

    private boolean hasManualOverrides() {
        return manualStationCallsignUsed != null
                || manualRemoteCallsignCandidate != null
                || manualRstSentCandidate != null
                || manualRstRcvdCandidate != null
                || manualNameCandidate != null
                || manualQthCandidate != null;
    }

    private String effectiveStationCallsign() {
        return manualStationCallsignUsed != null ? manualStationCallsignUsed : autoStationCallsignUsed;
    }

    private String effectiveRemoteCallsign() {
        return manualRemoteCallsignCandidate != null ? manualRemoteCallsignCandidate : autoRemoteCallsignCandidate;
    }

    private String effectiveRstSent() {
        return manualRstSentCandidate != null ? manualRstSentCandidate : autoRstSentCandidate;
    }

    private String effectiveRstRcvd() {
        return manualRstRcvdCandidate != null ? manualRstRcvdCandidate : autoRstRcvdCandidate;
    }

    private String effectiveName() {
        return manualNameCandidate != null ? manualNameCandidate : autoNameCandidate;
    }

    private String effectiveQth() {
        return manualQthCandidate != null ? manualQthCandidate : autoQthCandidate;
    }

    private String normalizeOptionalField(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static final class ContextualCallsigns {
        private final String addressedCallsign;
        private final String speakerCallsign;

        private ContextualCallsigns(String addressedCallsign, String speakerCallsign) {
            this.addressedCallsign = addressedCallsign;
            this.speakerCallsign = speakerCallsign;
        }
    }

    private static final class ReportSemantics {
        private final String sentReport;
        private final String receivedReport;

        private ReportSemantics(String sentReport, String receivedReport) {
            this.sentReport = sentReport;
            this.receivedReport = receivedReport;
        }
    }
}
