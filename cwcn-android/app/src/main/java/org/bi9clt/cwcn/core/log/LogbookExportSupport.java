package org.bi9clt.cwcn.core.log;

import android.content.Context;

import org.bi9clt.cwcn.R;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class LogbookExportSupport {
    private LogbookExportSupport() {
    }

    public static List<ConfirmedQsoLog> loadLogs(
            LocalLogRepository repository,
            LogbookExportRequest request
    ) {
        if (repository == null) {
            return Collections.emptyList();
        }
        LogbookExportRequest safeRequest = request == null
                ? new LogbookExportRequest(
                LogbookExportRequest.ConfirmationScope.ALL,
                LogbookExportRequest.TimeRangeScope.ALL
        )
                : request;
        List<ConfirmedQsoLog> queried = repository.queryConfirmedLogs(new ConfirmedLogQuery(
                null,
                null,
                null,
                null,
                mapConfirmationFilter(safeRequest.confirmationScope()),
                ConfirmedLogQuery.SortOrder.QSO_TIME_DESC
        ));
        return applyLocalTimeRange(queried, safeRequest.timeRangeScope());
    }

    public static String buildRequestLabel(LogbookExportRequest request) {
        if (request == null) {
            return "all-all";
        }
        return request.timeRangeScope().name().toLowerCase(Locale.US)
                + "-"
                + request.confirmationScope().name().toLowerCase(Locale.US);
    }

    public static String buildRequestSummary(LogbookExportRequest request) {
        LogbookExportRequest safeRequest = request == null
                ? new LogbookExportRequest(
                LogbookExportRequest.ConfirmationScope.ALL,
                LogbookExportRequest.TimeRangeScope.ALL
        )
                : request;
        return renderTimeRangeScope(safeRequest.timeRangeScope())
                + " / "
                + renderConfirmationScope(safeRequest.confirmationScope());
    }

    public static String buildRequestSummary(Context context, LogbookExportRequest request) {
        LogbookExportRequest safeRequest = request == null
                ? new LogbookExportRequest(
                LogbookExportRequest.ConfirmationScope.ALL,
                LogbookExportRequest.TimeRangeScope.ALL
        )
                : request;
        return renderTimeRangeScope(context, safeRequest.timeRangeScope())
                + " / "
                + renderConfirmationScope(context, safeRequest.confirmationScope());
    }

    public static String buildExportComment(ConfirmedQsoLog log) {
        if (log == null) {
            return "QSO by CWCN";
        }
        String autoComment = "QSO by CWCN";
        if (log.distanceKm() != null && log.distanceKm() > 0.0d) {
            autoComment = "Distance: " + LogDisplayFormatter.formatDistanceKm(log.distanceKm()) + ", QSO by CWCN";
        }
        String comment = trimToNull(log.comment());
        if (comment == null) {
            return autoComment;
        }
        if (comment.contains("QSO by CWCN")) {
            return comment;
        }
        return comment + "; " + autoComment;
    }

    public static String renderConfirmationScope(LogbookExportRequest.ConfirmationScope confirmationScope) {
        if (confirmationScope == null) {
            return "All";
        }
        switch (confirmationScope) {
            case CONFIRMED:
                return "Confirmed";
            case UNCONFIRMED:
                return "Unconfirmed";
            case ALL:
            default:
                return "All";
        }
    }

    public static String renderConfirmationScope(
            Context context,
            LogbookExportRequest.ConfirmationScope confirmationScope
    ) {
        if (context == null) {
            return renderConfirmationScope(confirmationScope);
        }
        if (confirmationScope == null) {
            return context.getString(R.string.qso_logbook_filter_all);
        }
        switch (confirmationScope) {
            case CONFIRMED:
                return context.getString(R.string.qso_logbook_filter_confirmed);
            case UNCONFIRMED:
                return context.getString(R.string.qso_logbook_filter_unconfirmed);
            case ALL:
            default:
                return context.getString(R.string.qso_logbook_filter_all);
        }
    }

    public static String renderTimeRangeScope(LogbookExportRequest.TimeRangeScope timeRangeScope) {
        if (timeRangeScope == null) {
            return "All";
        }
        switch (timeRangeScope) {
            case TODAY:
                return "Today";
            case THIS_MONTH:
                return "This Month";
            case ALL:
            default:
                return "All";
        }
    }

    public static String renderTimeRangeScope(
            Context context,
            LogbookExportRequest.TimeRangeScope timeRangeScope
    ) {
        if (context == null) {
            return renderTimeRangeScope(timeRangeScope);
        }
        if (timeRangeScope == null) {
            return context.getString(R.string.qso_logbook_filter_all);
        }
        switch (timeRangeScope) {
            case TODAY:
                return context.getString(R.string.qso_logbook_filter_today);
            case THIS_MONTH:
                return context.getString(R.string.qso_logbook_filter_this_month);
            case ALL:
            default:
                return context.getString(R.string.qso_logbook_filter_all);
        }
    }

    private static List<ConfirmedQsoLog> applyLocalTimeRange(
            List<ConfirmedQsoLog> source,
            LogbookExportRequest.TimeRangeScope timeRangeScope
    ) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        if (timeRangeScope == null || timeRangeScope == LogbookExportRequest.TimeRangeScope.ALL) {
            return source;
        }
        long startEpochMs = timeRangeStartEpochMs(timeRangeScope);
        ArrayList<ConfirmedQsoLog> filtered = new ArrayList<>();
        for (ConfirmedQsoLog log : source) {
            if (log != null && log.qsoTimeUtcEpochMs() >= startEpochMs) {
                filtered.add(log);
            }
        }
        return filtered;
    }

    private static long timeRangeStartEpochMs(LogbookExportRequest.TimeRangeScope timeRangeScope) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (timeRangeScope == LogbookExportRequest.TimeRangeScope.THIS_MONTH) {
            calendar.set(Calendar.DAY_OF_MONTH, 1);
        }
        return calendar.getTimeInMillis();
    }

    private static ConfirmedLogQuery.ConfirmationFilter mapConfirmationFilter(
            LogbookExportRequest.ConfirmationScope confirmationScope
    ) {
        if (confirmationScope == null) {
            return ConfirmedLogQuery.ConfirmationFilter.ALL;
        }
        switch (confirmationScope) {
            case CONFIRMED:
                return ConfirmedLogQuery.ConfirmationFilter.MANUALLY_CONFIRMED;
            case UNCONFIRMED:
                return ConfirmedLogQuery.ConfirmationFilter.UNCONFIRMED;
            case ALL:
            default:
                return ConfirmedLogQuery.ConfirmationFilter.ALL;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
