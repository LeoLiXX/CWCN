package org.bi9clt.cwcn.core.log;

import java.util.Locale;

public final class MaidenheadGridUtil {
    private static final double EARTH_RADIUS_M = 6_371_393.0d;

    private MaidenheadGridUtil() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String upper = trimmed.toUpperCase(Locale.US);
        if (!isValid(upper)) {
            return null;
        }
        return upper.length() >= 4 ? upper.substring(0, 4) : upper;
    }

    public static boolean isValid(String grid) {
        if (grid == null) {
            return false;
        }
        String trimmed = grid.trim();
        if (trimmed.length() != 4 && trimmed.length() != 6) {
            return false;
        }
        return Character.isLetter(trimmed.charAt(0))
                && Character.isLetter(trimmed.charAt(1))
                && Character.isDigit(trimmed.charAt(2))
                && Character.isDigit(trimmed.charAt(3));
    }

    public static Double distanceKm(String fromGrid, String toGrid) {
        double[] from = gridToLatLon(normalize(fromGrid));
        double[] to = gridToLatLon(normalize(toGrid));
        if (from == null || to == null) {
            return null;
        }
        double radiansAX = Math.toRadians(from[1]);
        double radiansAY = Math.toRadians(from[0]);
        double radiansBX = Math.toRadians(to[1]);
        double radiansBY = Math.toRadians(to[0]);
        double cos = Math.cos(radiansAY) * Math.cos(radiansBY) * Math.cos(radiansAX - radiansBX)
                + Math.sin(radiansAY) * Math.sin(radiansBY);
        double acos = Math.acos(cos);
        return (EARTH_RADIUS_M * acos) / 1000.0d;
    }

    public static String fromLatLon(double latitude, double longitude) {
        double safeLatitude = Math.max(-89.999999d, Math.min(89.999999d, latitude));
        double safeLongitude = longitude;
        while (safeLongitude < -180.0d) {
            safeLongitude += 360.0d;
        }
        while (safeLongitude >= 180.0d) {
            safeLongitude -= 360.0d;
        }

        double normalizedLongitude = safeLongitude + 180.0d;
        double normalizedLatitude = safeLatitude + 90.0d;

        int fieldLongitude = (int) Math.floor(normalizedLongitude / 20.0d);
        int fieldLatitude = (int) Math.floor(normalizedLatitude / 10.0d);
        int squareLongitude = (int) Math.floor((normalizedLongitude % 20.0d) / 2.0d);
        int squareLatitude = (int) Math.floor(normalizedLatitude % 10.0d);

        fieldLongitude = clamp(fieldLongitude, 0, 17);
        fieldLatitude = clamp(fieldLatitude, 0, 17);
        squareLongitude = clamp(squareLongitude, 0, 9);
        squareLatitude = clamp(squareLatitude, 0, 9);

        return new StringBuilder(4)
                .append((char) ('A' + fieldLongitude))
                .append((char) ('A' + fieldLatitude))
                .append((char) ('0' + squareLongitude))
                .append((char) ('0' + squareLatitude))
                .toString();
    }

    private static double[] gridToLatLon(String grid) {
        if (!isValid(grid)) {
            return null;
        }
        String upper = grid.toUpperCase(Locale.US);
        double lat = ((upper.charAt(1) - 'A') * 10.0d)
                + (upper.charAt(3) - '0')
                + 0.5d
                - 90.0d;
        double lon = ((upper.charAt(0) - 'A') * 20.0d)
                + ((upper.charAt(2) - '0') * 2.0d)
                + 1.0d
                - 180.0d;
        return new double[]{lat, lon};
    }

    private static int clamp(int value, int minValue, int maxValue) {
        return Math.max(minValue, Math.min(maxValue, value));
    }
}
