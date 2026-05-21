package org.bi9clt.cwcn.core.adif;

import org.bi9clt.cwcn.core.log.ConfirmedQsoLog;
import org.bi9clt.cwcn.core.log.LogbookExportSupport;
import org.bi9clt.cwcn.core.qso.QsoDraftSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public final class CwAdifExporter {
    private CwAdifExporter() {
    }

    public static String buildPreview(QsoDraftSnapshot snapshot) {
        if (snapshot == null || snapshot.remoteCallsignCandidate() == null) {
            return "";
        }

        long timestamp = snapshot.updatedAtEpochMs() > 0L ? snapshot.updatedAtEpochMs() : System.currentTimeMillis();
        ArrayList<String> fields = new ArrayList<>();
        fields.add(adifField("CALL", snapshot.remoteCallsignCandidate()));
        fields.add(adifField("QSO_DATE", formatQsoDateUtc(timestamp)));
        fields.add(adifField("TIME_ON", formatTimeOnUtc(timestamp)));
        fields.add(adifField("MODE", "CW"));

        if (snapshot.rstSentCandidate() != null) {
            fields.add(adifField("RST_SENT", normalizeRstValue(snapshot.rstSentCandidate())));
        }
        if (snapshot.rstRcvdCandidate() != null) {
            fields.add(adifField("RST_RCVD", normalizeRstValue(snapshot.rstRcvdCandidate())));
        }
        if (snapshot.nameCandidate() != null) {
            fields.add(adifField("NAME", snapshot.nameCandidate()));
        }
        if (snapshot.qthCandidate() != null) {
            fields.add(adifField("QTH", snapshot.qthCandidate()));
        }
        if (snapshot.stationCallsignUsed() != null) {
            fields.add(adifField("STATION_CALLSIGN", snapshot.stationCallsignUsed()));
        }

        fields.add(adifField("COMMENT", buildDraftComment(snapshot)));
        fields.add("<EOR>");
        return String.join(" ", fields);
    }

    public static String buildAdifFile(List<ConfirmedQsoLog> logs, String programVersion) {
        ArrayList<String> lines = new ArrayList<>();
        lines.add("CWCN ADIF Export");
        lines.add(
                adifField("ADIF_VER", "3.1.0")
                        + " "
                        + adifField("PROGRAMID", "CWCN")
                        + " "
                        + adifField("PROGRAMVERSION", programVersion == null ? "" : programVersion)
                        + " <EOH>"
        );
        for (ConfirmedQsoLog log : logs) {
            String record = buildRecord(log);
            if (!record.isEmpty()) {
                lines.add(record);
            }
        }
        return String.join("\n", lines) + "\n";
    }

    public static List<String> previewMappedFields(QsoDraftSnapshot snapshot) {
        ArrayList<String> mappings = new ArrayList<>();
        if (snapshot.remoteCallsignCandidate() != null) {
            mappings.add("CALL=" + snapshot.remoteCallsignCandidate());
        }
        if (snapshot.rstSentCandidate() != null) {
            mappings.add("RST_SENT=" + normalizeRstValue(snapshot.rstSentCandidate()));
        }
        if (snapshot.rstRcvdCandidate() != null) {
            mappings.add("RST_RCVD=" + normalizeRstValue(snapshot.rstRcvdCandidate()));
        }
        if (snapshot.nameCandidate() != null) {
            mappings.add("NAME=" + snapshot.nameCandidate());
        }
        if (snapshot.qthCandidate() != null) {
            mappings.add("QTH=" + snapshot.qthCandidate());
        }
        mappings.add("MODE=CW");
        return mappings;
    }

    public static String normalizeRstValue(String rawValue) {
        if (rawValue == null) {
            return null;
        }

        String trimmed = rawValue.trim().toUpperCase(Locale.US);
        if (trimmed.isEmpty()) {
            return null;
        }

        StringBuilder builder = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char current = trimmed.charAt(i);
            if (current == 'N') {
                builder.append('9');
            } else if (current == 'T') {
                builder.append('0');
            } else {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    public static String formatQsoDateUtc(long timestamp) {
        return formatUtc(timestamp, "yyyyMMdd");
    }

    public static String formatTimeOnUtc(long timestamp) {
        return formatUtc(timestamp, "HHmmss");
    }

    private static String buildDraftComment(QsoDraftSnapshot snapshot) {
        ArrayList<String> parts = new ArrayList<>();
        parts.add("phase=" + snapshot.phase().displayName());
        if (snapshot.needManualReview()) {
            parts.add("review=true");
        }
        if (!snapshot.hints().isEmpty()) {
            parts.add("hints=" + String.join("/", snapshot.hints()));
        }
        return String.join("; ", parts);
    }

    private static String buildRecord(ConfirmedQsoLog log) {
        if (log == null || log.remoteCallsign() == null || log.remoteCallsign().isEmpty()) {
            return "";
        }

        ArrayList<String> fields = new ArrayList<>();
        fields.add(adifField("CALL", log.remoteCallsign()));
        if (log.qsoDateUtc() != null) {
            fields.add(adifField("QSO_DATE", log.qsoDateUtc()));
        }
        if (log.timeOnUtc() != null) {
            fields.add(adifField("TIME_ON", log.timeOnUtc()));
        }
        fields.add(adifField("MODE", log.mode() == null ? "CW" : log.mode()));
        if (log.frequencyHz() > 0L) {
            fields.add(adifField("FREQ", formatFrequencyMhz(log.frequencyHz())));
        }
        if (log.bandLabel() != null) {
            fields.add(adifField("BAND", log.bandLabel()));
        }
        if (log.rstSent() != null) {
            fields.add(adifField("RST_SENT", log.rstSent()));
        }
        if (log.rstRcvd() != null) {
            fields.add(adifField("RST_RCVD", log.rstRcvd()));
        }
        if (log.name() != null) {
            fields.add(adifField("NAME", log.name()));
        }
        if (log.qth() != null) {
            fields.add(adifField("QTH", log.qth()));
        }
        if (log.stationCallsign() != null) {
            fields.add(adifField("STATION_CALLSIGN", log.stationCallsign()));
        }
        if (log.remoteGrid() != null) {
            fields.add(adifField("GRIDSQUARE", log.remoteGrid()));
        }
        if (log.stationGrid() != null) {
            fields.add(adifField("MY_GRIDSQUARE", log.stationGrid()));
        }
        fields.add(adifField("COMMENT", buildLogComment(log)));
        fields.add("<EOR>");
        return String.join(" ", fields);
    }

    private static String buildLogComment(ConfirmedQsoLog log) {
        return LogbookExportSupport.buildExportComment(log);
    }

    private static String adifField(String name, String value) {
        String safeValue = value == null ? "" : value;
        return "<" + name + ":" + safeValue.length() + ">" + safeValue;
    }

    private static String formatUtc(long timestamp, String pattern) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(timestamp));
    }

    private static String formatFrequencyMhz(long frequencyHz) {
        return String.format(Locale.US, "%.6f", frequencyHz / 1_000_000.0d);
    }
}
