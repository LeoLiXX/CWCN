package org.bi9clt.cwcn.core.log;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class LogDisplayFormatter {
    private static final DecimalFormat MHZ_FORMAT =
            new DecimalFormat("0.000", DecimalFormatSymbols.getInstance(Locale.US));

    private LogDisplayFormatter() {
    }

    public static String formatUtcDateTime(String qsoDateUtc, String timeOnUtc) {
        long epochMillis = parseUtcDateTimeMillis(qsoDateUtc, timeOnUtc);
        if (epochMillis <= 0L) {
            String safeDate = safeValue(qsoDateUtc);
            String safeTime = safeValue(timeOnUtc);
            return safeDate.equals("-") && safeTime.equals("-")
                    ? "-"
                    : safeDate + " " + safeTime + " UTC";
        }
        return createDisplayFormat("yyyy-MM-dd HH:mm:ss 'UTC'", TimeZone.getTimeZone("UTC"))
                .format(new Date(epochMillis));
    }

    public static String formatLocalDateTime(long epochMillis) {
        if (epochMillis <= 0L) {
            return "-";
        }
        return createDisplayFormat("yyyy-MM-dd HH:mm:ss", TimeZone.getDefault())
                .format(new Date(epochMillis));
    }

    public static String formatLocalDateTime(String qsoDateUtc, String timeOnUtc) {
        return formatLocalDateTime(parseUtcDateTimeMillis(qsoDateUtc, timeOnUtc));
    }

    public static String formatEpochMillis(long epochMillis) {
        return formatLocalDateTime(epochMillis);
    }

    public static String formatFrequency(long frequencyHz) {
        if (frequencyHz <= 0L) {
            return "-";
        }
        return MHZ_FORMAT.format(frequencyHz / 1_000_000.0d) + " MHz";
    }

    public static String formatBand(long frequencyHz) {
        String band = AmateurBandPlan.bandLabelForHz(frequencyHz);
        return band == null ? "-" : band;
    }

    public static String formatDistanceKm(Double distanceKm) {
        if (distanceKm == null || distanceKm <= 0.0d) {
            return "-";
        }
        return String.format(Locale.US, "%.0f km", distanceKm);
    }

    public static long parseUtcDateTimeMillis(String qsoDateUtc, String timeOnUtc) {
        if (qsoDateUtc == null || qsoDateUtc.trim().isEmpty()) {
            return -1L;
        }
        String normalizedDate = qsoDateUtc.replaceAll("[^0-9]", "");
        if (normalizedDate.length() != 8) {
            return -1L;
        }
        String normalizedTime = normalizeTimeDigits(timeOnUtc);
        try {
            Date parsed = createDisplayFormat("yyyyMMddHHmmss", TimeZone.getTimeZone("UTC"))
                    .parse(normalizedDate + normalizedTime);
            return parsed == null ? -1L : parsed.getTime();
        } catch (ParseException ignored) {
            return -1L;
        }
    }

    private static SimpleDateFormat createDisplayFormat(String pattern, TimeZone timeZone) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
        format.setTimeZone(timeZone);
        format.setLenient(false);
        return format;
    }

    private static String normalizeTimeDigits(String timeOnUtc) {
        if (timeOnUtc == null) {
            return "000000";
        }
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

    private static String safeValue(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value.trim();
    }
}
