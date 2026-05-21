package org.bi9clt.cwcn.core.log;

final class AmateurBandPlan {
    private AmateurBandPlan() {
    }

    static String bandLabelForHz(long frequencyHz) {
        if (frequencyHz <= 0L) {
            return null;
        }
        if (between(frequencyHz, 1_800_000L, 2_000_000L)) {
            return "160m";
        }
        if (between(frequencyHz, 3_500_000L, 4_000_000L)) {
            return "80m";
        }
        if (between(frequencyHz, 5_330_500L, 5_406_500L)) {
            return "60m";
        }
        if (between(frequencyHz, 7_000_000L, 7_300_000L)) {
            return "40m";
        }
        if (between(frequencyHz, 10_100_000L, 10_150_000L)) {
            return "30m";
        }
        if (between(frequencyHz, 14_000_000L, 14_350_000L)) {
            return "20m";
        }
        if (between(frequencyHz, 18_068_000L, 18_168_000L)) {
            return "17m";
        }
        if (between(frequencyHz, 21_000_000L, 21_450_000L)) {
            return "15m";
        }
        if (between(frequencyHz, 24_890_000L, 24_990_000L)) {
            return "12m";
        }
        if (between(frequencyHz, 28_000_000L, 29_700_000L)) {
            return "10m";
        }
        if (between(frequencyHz, 50_000_000L, 54_000_000L)) {
            return "6m";
        }
        if (between(frequencyHz, 144_000_000L, 148_000_000L)) {
            return "2m";
        }
        if (between(frequencyHz, 420_000_000L, 450_000_000L)) {
            return "70cm";
        }
        return null;
    }

    private static boolean between(long value, long min, long max) {
        return value >= min && value <= max;
    }
}
