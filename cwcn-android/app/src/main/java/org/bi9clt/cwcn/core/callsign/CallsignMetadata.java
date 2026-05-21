package org.bi9clt.cwcn.core.callsign;

import java.util.ArrayList;
import java.util.Locale;

public final class CallsignMetadata {
    private final String callsign;
    private final String matchedRule;
    private final String countryNameEn;
    private final String countryNameZhCn;
    private final int cqZone;
    private final int ituZone;
    private final String continent;
    private final double latitude;
    private final double longitude;
    private final double gmtOffsetHours;
    private final String dxccPrefix;

    CallsignMetadata(
            String callsign,
            String matchedRule,
            String countryNameEn,
            String countryNameZhCn,
            int cqZone,
            int ituZone,
            String continent,
            double latitude,
            double longitude,
            double gmtOffsetHours,
            String dxccPrefix
    ) {
        this.callsign = callsign;
        this.matchedRule = matchedRule;
        this.countryNameEn = countryNameEn;
        this.countryNameZhCn = countryNameZhCn;
        this.cqZone = cqZone;
        this.ituZone = ituZone;
        this.continent = continent;
        this.latitude = latitude;
        this.longitude = longitude;
        this.gmtOffsetHours = gmtOffsetHours;
        this.dxccPrefix = dxccPrefix;
    }

    public String callsign() {
        return callsign;
    }

    public String matchedRule() {
        return matchedRule;
    }

    public String countryNameEn() {
        return countryNameEn;
    }

    public String countryNameZhCn() {
        return countryNameZhCn;
    }

    public int cqZone() {
        return cqZone;
    }

    public int ituZone() {
        return ituZone;
    }

    public String continent() {
        return continent;
    }

    public double latitude() {
        return latitude;
    }

    public double longitude() {
        return longitude;
    }

    public double gmtOffsetHours() {
        return gmtOffsetHours;
    }

    public String dxccPrefix() {
        return dxccPrefix;
    }

    public String displayCountry() {
        if (prefersChineseDisplay() && hasMeaningfulText(countryNameZhCn)) {
            return countryNameZhCn;
        }
        return hasMeaningfulText(countryNameEn) ? countryNameEn : countryNameZhCn;
    }

    public String regionSummary() {
        ArrayList<String> parts = new ArrayList<>();
        if (hasMeaningfulText(continent)) {
            parts.add(continent);
        }
        if (cqZone > 0) {
            parts.add("CQ " + cqZone);
        }
        if (ituZone > 0) {
            parts.add("ITU " + ituZone);
        }
        return parts.isEmpty() ? null : String.join(" / ", parts);
    }

    private static boolean prefersChineseDisplay() {
        return "zh".equalsIgnoreCase(Locale.getDefault().getLanguage());
    }

    private static boolean hasMeaningfulText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
