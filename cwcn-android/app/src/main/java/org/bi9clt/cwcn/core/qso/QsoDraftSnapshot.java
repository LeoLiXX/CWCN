package org.bi9clt.cwcn.core.qso;

import java.util.List;

public final class QsoDraftSnapshot {
    private final QsoPhase phase;
    private final String stationCallsignUsed;
    private final String remoteCallsignCandidate;
    private final String rstSentCandidate;
    private final String rstRcvdCandidate;
    private final String nameCandidate;
    private final String qthCandidate;
    private final boolean stationCallsignManuallySet;
    private final boolean remoteCallsignManuallySet;
    private final boolean rstSentManuallySet;
    private final boolean rstRcvdManuallySet;
    private final boolean nameManuallySet;
    private final boolean qthManuallySet;
    private final String normalizedText;
    private final List<String> hints;
    private final boolean readyForDraftConfirmation;
    private final boolean needManualReview;
    private final long updatedAtEpochMs;
    private final QsoStateEvent lastEvent;

    public QsoDraftSnapshot(
            QsoPhase phase,
            String stationCallsignUsed,
            String remoteCallsignCandidate,
            String rstSentCandidate,
            String rstRcvdCandidate,
            String nameCandidate,
            String qthCandidate,
            boolean stationCallsignManuallySet,
            boolean remoteCallsignManuallySet,
            boolean rstSentManuallySet,
            boolean rstRcvdManuallySet,
            boolean nameManuallySet,
            boolean qthManuallySet,
            String normalizedText,
            List<String> hints,
            boolean readyForDraftConfirmation,
            boolean needManualReview,
            long updatedAtEpochMs,
            QsoStateEvent lastEvent
    ) {
        this.phase = phase;
        this.stationCallsignUsed = stationCallsignUsed;
        this.remoteCallsignCandidate = remoteCallsignCandidate;
        this.rstSentCandidate = rstSentCandidate;
        this.rstRcvdCandidate = rstRcvdCandidate;
        this.nameCandidate = nameCandidate;
        this.qthCandidate = qthCandidate;
        this.stationCallsignManuallySet = stationCallsignManuallySet;
        this.remoteCallsignManuallySet = remoteCallsignManuallySet;
        this.rstSentManuallySet = rstSentManuallySet;
        this.rstRcvdManuallySet = rstRcvdManuallySet;
        this.nameManuallySet = nameManuallySet;
        this.qthManuallySet = qthManuallySet;
        this.normalizedText = normalizedText;
        this.hints = hints;
        this.readyForDraftConfirmation = readyForDraftConfirmation;
        this.needManualReview = needManualReview;
        this.updatedAtEpochMs = updatedAtEpochMs;
        this.lastEvent = lastEvent;
    }

    public QsoPhase phase() {
        return phase;
    }

    public String stationCallsignUsed() {
        return stationCallsignUsed;
    }

    public String remoteCallsignCandidate() {
        return remoteCallsignCandidate;
    }

    public String rstSentCandidate() {
        return rstSentCandidate;
    }

    public String rstRcvdCandidate() {
        return rstRcvdCandidate;
    }

    public String nameCandidate() {
        return nameCandidate;
    }

    public String qthCandidate() {
        return qthCandidate;
    }

    public boolean stationCallsignManuallySet() {
        return stationCallsignManuallySet;
    }

    public boolean remoteCallsignManuallySet() {
        return remoteCallsignManuallySet;
    }

    public boolean rstSentManuallySet() {
        return rstSentManuallySet;
    }

    public boolean rstRcvdManuallySet() {
        return rstRcvdManuallySet;
    }

    public boolean nameManuallySet() {
        return nameManuallySet;
    }

    public boolean qthManuallySet() {
        return qthManuallySet;
    }

    public String normalizedText() {
        return normalizedText;
    }

    public List<String> hints() {
        return hints;
    }

    public boolean readyForDraftConfirmation() {
        return readyForDraftConfirmation;
    }

    public boolean needManualReview() {
        return needManualReview;
    }

    public long updatedAtEpochMs() {
        return updatedAtEpochMs;
    }

    public QsoStateEvent lastEvent() {
        return lastEvent;
    }
}
