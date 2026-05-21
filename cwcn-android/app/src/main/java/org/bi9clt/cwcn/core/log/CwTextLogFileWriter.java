package org.bi9clt.cwcn.core.log;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class CwTextLogFileWriter {
    private CwTextLogFileWriter() {
    }

    public static File export(Context context, List<ConfirmedQsoLog> logs) throws IOException {
        return export(context, logs, null);
    }

    public static File export(Context context, List<ConfirmedQsoLog> logs, String requestLabel) throws IOException {
        File shareDir = new File(context.getCacheDir(), "logbook-share");
        if (!shareDir.exists() && !shareDir.mkdirs()) {
            throw new IOException("Unable to create share directory.");
        }
        String normalizedLabel = normalizeLabel(requestLabel);
        String fileName = "cwcn-log"
                + (normalizedLabel == null ? "" : "-" + normalizedLabel)
                + "-"
                + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date())
                + ".txt";
        File targetFile = new File(shareDir, fileName);
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(targetFile),
                StandardCharsets.UTF_8
        )) {
            writer.write(buildTxtExport(logs));
        }
        return targetFile;
    }

    public static String buildTxtExport(List<ConfirmedQsoLog> logs) {
        StringBuilder builder = new StringBuilder();
        builder.append("CWCN QSO Log").append('\n');
        builder.append("Generated: ")
                .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()))
                .append('\n')
                .append('\n');
        if (logs == null || logs.isEmpty()) {
            builder.append("No records.");
            return builder.toString();
        }
        for (ConfirmedQsoLog log : logs) {
            builder.append("CALL: ").append(safe(log == null ? null : log.remoteCallsign())).append('\n');
            builder.append("STATION_CALLSIGN: ").append(safe(log == null ? null : log.stationCallsign())).append('\n');
            builder.append("QSO_TIME_LOCAL: ").append(LogDisplayFormatter.formatLocalDateTime(log == null ? 0L : log.qsoTimeUtcEpochMs())).append('\n');
            builder.append("MODE: CW").append('\n');
            builder.append("BAND: ").append(LogDisplayFormatter.formatBand(log == null ? 0L : log.frequencyHz())).append('\n');
            builder.append("FREQUENCY: ").append(LogDisplayFormatter.formatFrequency(log == null ? 0L : log.frequencyHz())).append('\n');
            builder.append("RST_SENT: ").append(safe(log == null ? null : log.rstSent())).append('\n');
            builder.append("RST_RCVD: ").append(safe(log == null ? null : log.rstRcvd())).append('\n');
            builder.append("REMOTE_GRID: ").append(safe(log == null ? null : log.remoteGrid())).append('\n');
            builder.append("MY_GRID: ").append(safe(log == null ? null : log.stationGrid())).append('\n');
            builder.append("NAME: ").append(safe(log == null ? null : log.name())).append('\n');
            builder.append("QTH: ").append(safe(log == null ? null : log.qth())).append('\n');
            builder.append("COMMENT: ").append(LogbookExportSupport.buildExportComment(log)).append('\n');
            builder.append("CONFIRMED: ").append(log != null && log.manualConfirmed() ? "yes" : "no").append('\n');
            builder.append('\n');
        }
        return builder.toString().trim() + '\n';
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value.trim();
    }

    private static String normalizeLabel(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.US).replaceAll("[^a-z0-9._-]+", "-");
        return normalized.isEmpty() ? null : normalized;
    }
}
