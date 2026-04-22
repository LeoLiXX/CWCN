package org.bi9clt.cwcn.core.log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class LogDisplayFormatter {
    private LogDisplayFormatter() {
    }

    public static String formatUtcDateTime(String qsoDateUtc, String timeOnUtc) {
        if (qsoDateUtc == null || qsoDateUtc.isEmpty()) {
            return "-";
        }

        String safeTime = (timeOnUtc == null || timeOnUtc.isEmpty()) ? "000000" : padTime(timeOnUtc);
        SimpleDateFormat inputFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
        inputFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US);
        outputFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            Date parsed = inputFormat.parse(qsoDateUtc + safeTime);
            if (parsed != null) {
                return outputFormat.format(parsed);
            }
        } catch (ParseException ignored) {
            // Fallback below keeps the original values visible.
        }
        return qsoDateUtc + " " + safeTime + " UTC";
    }

    public static String formatEpochMillis(long epochMillis) {
        if (epochMillis <= 0L) {
            return "-";
        }
        SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        return outputFormat.format(new Date(epochMillis));
    }

    private static String padTime(String timeOnUtc) {
        String digitsOnly = timeOnUtc.replaceAll("[^0-9]", "");
        if (digitsOnly.length() >= 6) {
            return digitsOnly.substring(0, 6);
        }
        StringBuilder builder = new StringBuilder(digitsOnly);
        while (builder.length() < 6) {
            builder.append('0');
        }
        return builder.toString();
    }
}
