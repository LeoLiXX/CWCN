package org.bi9clt.cwcn.core.log;

import org.bi9clt.cwcn.core.adif.CwAdifExporter;
import org.bi9clt.cwcn.core.qso.QsoDraftSnapshot;
import org.bi9clt.cwcn.core.qso.QsoPhase;

public final class ConfirmedQsoLog {
    private final long id;
    private final String remoteCallsign;
    private final String stationCallsign;
    private final long qsoTimeUtcEpochMs;
    private final long frequencyHz;
    private final String rstSent;
    private final String rstRcvd;
    private final String remoteGrid;
    private final String stationGrid;
    private final String name;
    private final String qth;
    private final String comment;
    private final boolean manualConfirmed;
    private final String mode;
    private final String phase;
    private final String normalizedText;
    private final boolean needManualReview;
    private final long confirmedAtEpochMs;

    public ConfirmedQsoLog(
            long id,
            String remoteCallsign,
            String stationCallsign,
            long qsoTimeUtcEpochMs,
            long frequencyHz,
            String rstSent,
            String rstRcvd,
            String remoteGrid,
            String stationGrid,
            String name,
            String qth,
            String comment,
            boolean manualConfirmed,
            String mode,
            String phase,
            String normalizedText,
            boolean needManualReview,
            long confirmedAtEpochMs
    ) {
        this.id = id;
        this.remoteCallsign = normalizeCallsign(remoteCallsign);
        this.stationCallsign = normalizeCallsign(stationCallsign);
        this.qsoTimeUtcEpochMs = qsoTimeUtcEpochMs;
        this.frequencyHz = Math.max(0L, frequencyHz);
        this.rstSent = normalizeOptional(rstSent);
        this.rstRcvd = normalizeOptional(rstRcvd);
        this.remoteGrid = MaidenheadGridUtil.normalize(remoteGrid);
        this.stationGrid = MaidenheadGridUtil.normalize(stationGrid);
        this.name = normalizeOptional(name);
        this.qth = normalizeOptional(qth);
        this.comment = normalizeOptional(comment);
        this.manualConfirmed = manualConfirmed;
        this.mode = normalizeOptional(mode) == null ? "CW" : normalizeOptional(mode);
        this.phase = normalizeOptional(phase);
        this.normalizedText = normalizedText == null ? "" : normalizedText;
        this.needManualReview = needManualReview;
        this.confirmedAtEpochMs = confirmedAtEpochMs;
    }

    public static ConfirmedQsoLog fromDraft(
            QsoDraftSnapshot snapshot,
            long confirmedAtEpochMs,
            String stationGrid,
            long frequencyHz,
            String comment
    ) {
        long timestamp = snapshot != null && snapshot.updatedAtEpochMs() > 0L
                ? snapshot.updatedAtEpochMs()
                : confirmedAtEpochMs;
        return new ConfirmedQsoLog(
                0L,
                snapshot == null ? null : snapshot.remoteCallsignCandidate(),
                snapshot == null ? null : snapshot.stationCallsignUsed(),
                timestamp,
                frequencyHz,
                CwAdifExporter.normalizeRstValue(snapshot == null ? null : snapshot.rstSentCandidate()),
                CwAdifExporter.normalizeRstValue(snapshot == null ? null : snapshot.rstRcvdCandidate()),
                null,
                stationGrid,
                snapshot == null ? null : snapshot.nameCandidate(),
                snapshot == null ? null : snapshot.qthCandidate(),
                comment,
                false,
                "CW",
                phaseName(snapshot == null ? null : snapshot.phase()),
                snapshot == null ? "" : snapshot.normalizedText(),
                snapshot != null && snapshot.needManualReview(),
                confirmedAtEpochMs
        );
    }

    public ConfirmedQsoLog withId(long updatedId) {
        return new ConfirmedQsoLog(
                updatedId,
                remoteCallsign,
                stationCallsign,
                qsoTimeUtcEpochMs,
                frequencyHz,
                rstSent,
                rstRcvd,
                remoteGrid,
                stationGrid,
                name,
                qth,
                comment,
                manualConfirmed,
                mode,
                phase,
                normalizedText,
                needManualReview,
                confirmedAtEpochMs
        );
    }

    public ConfirmedQsoLog withManualConfirmed(boolean updatedManualConfirmed) {
        return new ConfirmedQsoLog(
                id,
                remoteCallsign,
                stationCallsign,
                qsoTimeUtcEpochMs,
                frequencyHz,
                rstSent,
                rstRcvd,
                remoteGrid,
                stationGrid,
                name,
                qth,
                comment,
                updatedManualConfirmed,
                mode,
                phase,
                normalizedText,
                needManualReview,
                confirmedAtEpochMs
        );
    }

    public ConfirmedQsoLog withNeedManualReview(boolean updatedNeedManualReview) {
        return new ConfirmedQsoLog(
                id,
                remoteCallsign,
                stationCallsign,
                qsoTimeUtcEpochMs,
                frequencyHz,
                rstSent,
                rstRcvd,
                remoteGrid,
                stationGrid,
                name,
                qth,
                comment,
                manualConfirmed,
                mode,
                phase,
                normalizedText,
                updatedNeedManualReview,
                confirmedAtEpochMs
        );
    }

    public ConfirmedQsoLog withDraftEdits(
            QsoDraftSnapshot snapshot,
            Long updatedQsoTimeUtcEpochMs,
            Long updatedFrequencyHz,
            String updatedRemoteGrid,
            String updatedStationGrid,
            String updatedComment,
            Boolean updatedManualConfirmed
    ) {
        if (snapshot == null) {
            return this;
        }
        return new ConfirmedQsoLog(
                id,
                snapshot.remoteCallsignCandidate(),
                snapshot.stationCallsignUsed(),
                updatedQsoTimeUtcEpochMs == null ? qsoTimeUtcEpochMs : updatedQsoTimeUtcEpochMs,
                updatedFrequencyHz == null ? frequencyHz : Math.max(0L, updatedFrequencyHz),
                CwAdifExporter.normalizeRstValue(snapshot.rstSentCandidate()),
                CwAdifExporter.normalizeRstValue(snapshot.rstRcvdCandidate()),
                updatedRemoteGrid == null ? remoteGrid : updatedRemoteGrid,
                updatedStationGrid == null ? stationGrid : updatedStationGrid,
                snapshot.nameCandidate(),
                snapshot.qthCandidate(),
                updatedComment == null ? comment : updatedComment,
                updatedManualConfirmed == null ? manualConfirmed : updatedManualConfirmed,
                mode,
                phaseName(snapshot.phase()),
                snapshot.normalizedText(),
                snapshot.needManualReview(),
                confirmedAtEpochMs
        );
    }

    public long id() {
        return id;
    }

    public String remoteCallsign() {
        return remoteCallsign;
    }

    public String stationCallsign() {
        return stationCallsign;
    }

    public long qsoTimeUtcEpochMs() {
        return qsoTimeUtcEpochMs;
    }

    public long frequencyHz() {
        return frequencyHz;
    }

    public String rstSent() {
        return rstSent;
    }

    public String rstRcvd() {
        return rstRcvd;
    }

    public String remoteGrid() {
        return remoteGrid;
    }

    public String stationGrid() {
        return stationGrid;
    }

    public String name() {
        return name;
    }

    public String qth() {
        return qth;
    }

    public String comment() {
        return comment;
    }

    public boolean manualConfirmed() {
        return manualConfirmed;
    }

    public String mode() {
        return mode;
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

    public String qsoDateUtc() {
        return qsoTimeUtcEpochMs > 0L
                ? CwAdifExporter.formatQsoDateUtc(qsoTimeUtcEpochMs)
                : null;
    }

    public String timeOnUtc() {
        return qsoTimeUtcEpochMs > 0L
                ? CwAdifExporter.formatTimeOnUtc(qsoTimeUtcEpochMs)
                : null;
    }

    public String bandLabel() {
        return AmateurBandPlan.bandLabelForHz(frequencyHz);
    }

    public Double distanceKm() {
        return MaidenheadGridUtil.distanceKm(stationGrid, remoteGrid);
    }

    private static String phaseName(QsoPhase phase) {
        return phase == null ? null : phase.displayName();
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeCallsign(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null ? null : normalized.toUpperCase();
    }
}
