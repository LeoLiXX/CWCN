package org.bi9clt.cwcn.core.qso;

import org.bi9clt.cwcn.core.log.ConfirmedQsoLog;

import java.util.ArrayList;

public final class QsoDraftFactory {
    private QsoDraftFactory() {
    }

    public static QsoDraftSnapshot createManualDraft(
            QsoDraftSnapshot existingSnapshot,
            String stationCallsign,
            String remoteCallsign,
            String rstSent,
            String rstRcvd,
            String name,
            String qth,
            long timestampMs,
            String summary
    ) {
        QsoPhase phase = existingSnapshot == null ? QsoPhase.IDLE : existingSnapshot.phase();
        String normalizedText = existingSnapshot == null ? "" : safeText(existingSnapshot.normalizedText());
        ArrayList<String> hints = existingSnapshot == null
                ? new ArrayList<>()
                : new ArrayList<>(existingSnapshot.hints());

        String normalizedStation = normalizeOptionalField(stationCallsign);
        String normalizedRemote = normalizeOptionalField(remoteCallsign);
        String normalizedRstSent = normalizeOptionalField(rstSent);
        String normalizedRstRcvd = normalizeOptionalField(rstRcvd);
        String normalizedName = normalizeOptionalField(name);
        String normalizedQth = normalizeOptionalField(qth);

        boolean readyForDraftConfirmation = normalizedRemote != null
                && (normalizedRstSent != null || normalizedRstRcvd != null);
        boolean needManualReview = normalizedRemote == null
                || normalizedRemote.contains("?")
                || (phase == QsoPhase.COMPLETED && normalizedRstSent == null && normalizedRstRcvd == null);

        QsoStateEvent lastEvent = new QsoStateEvent(timestampMs, phase, safeText(summary));
        return new QsoDraftSnapshot(
                phase,
                normalizedStation,
                normalizedRemote,
                normalizedRstSent,
                normalizedRstRcvd,
                normalizedName,
                normalizedQth,
                normalizedStation != null,
                normalizedRemote != null,
                normalizedRstSent != null,
                normalizedRstRcvd != null,
                normalizedName != null,
                normalizedQth != null,
                normalizedText,
                hints,
                readyForDraftConfirmation,
                needManualReview,
                timestampMs,
                lastEvent
        );
    }

    public static QsoDraftSnapshot createDraftFromConfirmedLog(
            ConfirmedQsoLog log,
            long timestampMs,
            String summary
    ) {
        ArrayList<String> hints = new ArrayList<>();
        if (log.needManualReview()) {
            hints.add("loaded-from-confirmed-log");
        }

        return new QsoDraftSnapshot(
                safePhase(log.phase()),
                normalizeOptionalField(log.stationCallsign()),
                normalizeOptionalField(log.remoteCallsign()),
                normalizeOptionalField(log.rstSent()),
                normalizeOptionalField(log.rstRcvd()),
                normalizeOptionalField(log.name()),
                normalizeOptionalField(log.qth()),
                true,
                true,
                log.rstSent() != null,
                log.rstRcvd() != null,
                log.name() != null,
                log.qth() != null,
                safeText(log.normalizedText()),
                hints,
                log.remoteCallsign() != null && (log.rstSent() != null || log.rstRcvd() != null),
                log.needManualReview(),
                timestampMs,
                new QsoStateEvent(timestampMs, safePhase(log.phase()), safeText(summary))
        );
    }

    private static String normalizeOptionalField(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }

    private static QsoPhase safePhase(String rawPhase) {
        if (rawPhase == null || rawPhase.isEmpty()) {
            return QsoPhase.IDLE;
        }
        for (QsoPhase phase : QsoPhase.values()) {
            if (rawPhase.equals(phase.displayName()) || rawPhase.equals(phase.name())) {
                return phase;
            }
        }
        return QsoPhase.IDLE;
    }
}
