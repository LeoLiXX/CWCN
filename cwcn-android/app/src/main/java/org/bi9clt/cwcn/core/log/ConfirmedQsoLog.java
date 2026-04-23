package org.bi9clt.cwcn.core.log;

import org.bi9clt.cwcn.core.adif.CwAdifExporter;
import org.bi9clt.cwcn.core.qso.QsoDraftSnapshot;
import org.bi9clt.cwcn.core.qso.QsoPhase;

public final class ConfirmedQsoLog {
    private final long id;
    private final String remoteCallsign;
    private final String qsoDateUtc;
    private final String timeOnUtc;
    private final String mode;
    private final String rstSent;
    private final String rstRcvd;
    private final String name;
    private final String qth;
    private final String stationCallsign;
    private final String phase;
    private final String normalizedText;
    private final boolean needManualReview;
    private final long confirmedAtEpochMs;

    public ConfirmedQsoLog(
            long id,
            String remoteCallsign,
            String qsoDateUtc,
            String timeOnUtc,
            String mode,
            String rstSent,
            String rstRcvd,
            String name,
            String qth,
            String stationCallsign,
            String phase,
            String normalizedText,
            boolean needManualReview,
            long confirmedAtEpochMs
    ) {
        this.id = id;
        this.remoteCallsign = remoteCallsign;
        this.qsoDateUtc = qsoDateUtc;
        this.timeOnUtc = timeOnUtc;
        this.mode = mode;
        this.rstSent = rstSent;
        this.rstRcvd = rstRcvd;
        this.name = name;
        this.qth = qth;
        this.stationCallsign = stationCallsign;
        this.phase = phase;
        this.normalizedText = normalizedText;
        this.needManualReview = needManualReview;
        this.confirmedAtEpochMs = confirmedAtEpochMs;
    }

    public static ConfirmedQsoLog fromDraft(QsoDraftSnapshot snapshot, long confirmedAtEpochMs) {
        long timestamp = snapshot.updatedAtEpochMs() > 0L ? snapshot.updatedAtEpochMs() : confirmedAtEpochMs;
        return new ConfirmedQsoLog(
                0L,
                snapshot.remoteCallsignCandidate(),
                CwAdifExporter.formatQsoDateUtc(timestamp),
                CwAdifExporter.formatTimeOnUtc(timestamp),
                "CW",
                CwAdifExporter.normalizeRstValue(snapshot.rstSentCandidate()),
                CwAdifExporter.normalizeRstValue(snapshot.rstRcvdCandidate()),
                snapshot.nameCandidate(),
                snapshot.qthCandidate(),
                snapshot.stationCallsignUsed(),
                phaseName(snapshot.phase()),
                snapshot.normalizedText(),
                snapshot.needManualReview(),
                confirmedAtEpochMs
        );
    }

    public ConfirmedQsoLog withNeedManualReview(boolean updatedNeedManualReview) {
        return new ConfirmedQsoLog(
                id,
                remoteCallsign,
                qsoDateUtc,
                timeOnUtc,
                mode,
                rstSent,
                rstRcvd,
                name,
                qth,
                stationCallsign,
                phase,
                normalizedText,
                updatedNeedManualReview,
                confirmedAtEpochMs
        );
    }

    public ConfirmedQsoLog withDraftEdits(QsoDraftSnapshot snapshot) {
        if (snapshot == null) {
            return this;
        }
        return new ConfirmedQsoLog(
                id,
                snapshot.remoteCallsignCandidate(),
                qsoDateUtc,
                timeOnUtc,
                mode,
                CwAdifExporter.normalizeRstValue(snapshot.rstSentCandidate()),
                CwAdifExporter.normalizeRstValue(snapshot.rstRcvdCandidate()),
                snapshot.nameCandidate(),
                snapshot.qthCandidate(),
                snapshot.stationCallsignUsed(),
                phaseName(snapshot.phase()),
                snapshot.normalizedText(),
                snapshot.needManualReview(),
                confirmedAtEpochMs
        );
    }

    public long id() {
        return id;
    }

    private static String phaseName(QsoPhase phase) {
        return phase == null ? null : phase.displayName();
    }

    public String remoteCallsign() {
        return remoteCallsign;
    }

    public String qsoDateUtc() {
        return qsoDateUtc;
    }

    public String timeOnUtc() {
        return timeOnUtc;
    }

    public String mode() {
        return mode;
    }

    public String rstSent() {
        return rstSent;
    }

    public String rstRcvd() {
        return rstRcvd;
    }

    public String name() {
        return name;
    }

    public String qth() {
        return qth;
    }

    public String stationCallsign() {
        return stationCallsign;
    }

    public String phase() {
        return phase;
    }

    public String normalizedText() {
        return normalizedText;
    }

    public boolean needManualReview() {
        return needManualReview;
    }

    public long confirmedAtEpochMs() {
        return confirmedAtEpochMs;
    }
}
