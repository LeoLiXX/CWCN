package org.bi9clt.cwcn.core.signal;

public final class SqlThresholdModel {
    public static final int DEFAULT_SQL_PERCENT = 60;
    public static final int SAFETY_FLOOR_THRESHOLD = 90;
    public static final int MIN_MANUAL_THRESHOLD = 100;
    public static final int MAX_MANUAL_THRESHOLD = 20000;
    private static final int MIN_SQL_SAFETY_FLOOR_THRESHOLD = 60;
    private static volatile Integer testingSafetyFloorThresholdOverride;

    private static final int MIN_NOISE_CLEARANCE = 12;
    private static final double NOISE_CLEARANCE_RATIO = 0.18d;
    private static final double SIGNAL_WINDOW_FLOOR_RATIO = 0.75d;
    private static final int USER_TRIM_MIN_RANGE = 18;
    private static final double USER_TRIM_SIGNAL_RATIO = 0.22d;
    private static final int MIN_TONE_HEADROOM = 4;
    private static final double TONE_HEADROOM_RATIO = 0.05d;
    private static final double RELEASE_RETAIN_RATIO = 0.92d;
    private static final double MANUAL_SQL_CURVE = 0.82d;

    private SqlThresholdModel() {
    }

    private static int safetyFloorThreshold() {
        Integer override = testingSafetyFloorThresholdOverride;
        return override == null ? SAFETY_FLOOR_THRESHOLD : Math.max(0, override);
    }

    private static int safetyFloorThreshold(int sqlPercent) {
        Integer override = testingSafetyFloorThresholdOverride;
        if (override != null) {
            return Math.max(0, override);
        }
        int clampedSqlPercent = clampSqlPercent(sqlPercent);
        if (clampedSqlPercent >= DEFAULT_SQL_PERCENT) {
            return SAFETY_FLOOR_THRESHOLD;
        }
        double relaxationRatio = (DEFAULT_SQL_PERCENT - clampedSqlPercent) / (double) DEFAULT_SQL_PERCENT;
        int relaxedFloor = (int) Math.round(
                SAFETY_FLOOR_THRESHOLD
                        - ((SAFETY_FLOOR_THRESHOLD - MIN_SQL_SAFETY_FLOOR_THRESHOLD) * relaxationRatio)
        );
        return Math.max(MIN_SQL_SAFETY_FLOOR_THRESHOLD, relaxedFloor);
    }

    public static void setSafetyFloorThresholdForTesting(Integer threshold) {
        testingSafetyFloorThresholdOverride = threshold == null ? null : Math.max(0, threshold);
    }

    public static void clearSafetyFloorThresholdForTesting() {
        testingSafetyFloorThresholdOverride = null;
    }

    public static int effectiveSafetyFloorThresholdForTesting() {
        return safetyFloorThreshold();
    }

    public static Recommendation recommend(
            double noiseFloorEstimate,
            double signalFloorEstimate,
            double toneLevelEstimate
    ) {
        return recommendInternal(
                noiseFloorEstimate,
                signalFloorEstimate,
                toneLevelEstimate,
                true,
                safetyFloorThreshold()
        );
    }

    private static Recommendation recommendInternal(
            double noiseFloorEstimate,
            double signalFloorEstimate,
            double toneLevelEstimate,
            boolean limitByToneHeadroom,
            int effectiveSafetyFloorThreshold
    ) {
        double noise = Math.max(0.0d, noiseFloorEstimate);
        double signal = Math.max(noise, signalFloorEstimate);
        double tone = Math.max(0.0d, toneLevelEstimate);
        double referenceSignal = Math.max(signal, tone);
        double signalDelta = Math.max(0.0d, referenceSignal - noise);
        int noiseClearance = Math.max(
                MIN_NOISE_CLEARANCE,
                (int) Math.round(noise * NOISE_CLEARANCE_RATIO)
        );
        int recommendedThreshold = Math.max(
                effectiveSafetyFloorThreshold,
                (int) Math.round(noise + noiseClearance)
        );
        boolean limitedByToneHeadroom = false;
        if (signalDelta > 0.0d) {
            recommendedThreshold = Math.max(
                    recommendedThreshold,
                    (int) Math.round(noise + Math.max(noiseClearance, signalDelta * SIGNAL_WINDOW_FLOOR_RATIO))
            );
            if (limitByToneHeadroom && tone > noise) {
                int toneHeadroom = Math.max(
                        MIN_TONE_HEADROOM,
                        (int) Math.round((tone - noise) * TONE_HEADROOM_RATIO)
                );
                int toneLimitedThreshold = Math.max((int) Math.round(noise + 1.0d),
                        (int) Math.round(tone - toneHeadroom));
                if (toneLimitedThreshold < recommendedThreshold) {
                    recommendedThreshold = Math.max(effectiveSafetyFloorThreshold, toneLimitedThreshold);
                    limitedByToneHeadroom = true;
                }
            }
        }
        boolean limitedBySafetyFloor = recommendedThreshold == effectiveSafetyFloorThreshold;
        return new Recommendation(
                recommendedThreshold,
                (int) Math.round(noise),
                (int) Math.round(referenceSignal),
                limitedBySafetyFloor,
                limitedByToneHeadroom
        );
    }

    public static int effectiveAttackThreshold(
            int sqlPercent,
            double noiseFloorEstimate,
            double signalFloorEstimate,
            double toneLevelEstimate
    ) {
        int effectiveSafetyFloorThreshold = safetyFloorThreshold(sqlPercent);
        Recommendation recommendation = recommendInternal(
                noiseFloorEstimate,
                signalFloorEstimate,
                toneLevelEstimate,
                true,
                effectiveSafetyFloorThreshold
        );
        double noise = Math.max(0.0d, noiseFloorEstimate);
        double signal = Math.max(noise, signalFloorEstimate);
        double tone = Math.max(0.0d, toneLevelEstimate);
        double referenceSignal = Math.max(signal, tone);
        double signalDelta = Math.max(0.0d, referenceSignal - noise);
        int trimRange = Math.max(
                USER_TRIM_MIN_RANGE,
                (int) Math.round(signalDelta * USER_TRIM_SIGNAL_RATIO)
        );
        double normalizedTrim = (clampSqlPercent(sqlPercent) - DEFAULT_SQL_PERCENT) / 45.0d;
        normalizedTrim = Math.max(-1.0d, Math.min(1.0d, normalizedTrim));
        int userTrim = (int) Math.round(trimRange * normalizedTrim);
        return Math.max(effectiveSafetyFloorThreshold, recommendation.recommendedThresholdLevel() + userTrim);
    }

    public static int effectiveReleaseThreshold(
            int sqlPercent,
            int attackThreshold,
            double noiseFloorEstimate
    ) {
        double noise = Math.max(0.0d, noiseFloorEstimate);
        int effectiveSafetyFloorThreshold = safetyFloorThreshold(sqlPercent);
        double attack = Math.max(effectiveSafetyFloorThreshold, attackThreshold);
        double signalSpan = Math.max(0.0d, attack - noise);
        double threshold = noise + (signalSpan * RELEASE_RETAIN_RATIO);
        return Math.max(
                effectiveSafetyFloorThreshold,
                Math.min((int) Math.round(attack), (int) Math.round(threshold))
        );
    }

    public static int clampSqlPercent(int sqlPercent) {
        return Math.max(0, Math.min(100, sqlPercent));
    }

    public static int clampManualThreshold(int threshold) {
        return Math.max(MIN_MANUAL_THRESHOLD, Math.min(MAX_MANUAL_THRESHOLD, threshold));
    }

    public static int manualThresholdFromPercent(
            int sqlPercent,
            double noiseFloorEstimate,
            double signalFloorEstimate,
            double toneLevelEstimate
    ) {
        int clampedSqlPercent = clampSqlPercent(sqlPercent);
        double noise = Math.max(0.0d, noiseFloorEstimate);
        double signal = Math.max(noise, signalFloorEstimate);
        double tone = Math.max(0.0d, toneLevelEstimate);
        double referenceSignal = Math.max(signal, tone);
        if (referenceSignal <= 0.0d) {
            return (int) Math.round(safetyFloorThreshold(clampedSqlPercent) * (clampedSqlPercent / 100.0d));
        }
        int noiseClearance = Math.max(
                MIN_NOISE_CLEARANCE,
                (int) Math.round(noise * NOISE_CLEARANCE_RATIO)
        );
        double floor = noise + noiseClearance;
        double span = Math.max(
                USER_TRIM_MIN_RANGE,
                Math.max(0.0d, referenceSignal - noise) * USER_TRIM_SIGNAL_RATIO
        );
        double ceiling = Math.max(referenceSignal, floor + span);
        if (clampedSqlPercent >= 100) {
            return (int) Math.round(ceiling);
        }
        double normalizedPercent = clampedSqlPercent / 100.0d;
        double curvedPercent = Math.pow(normalizedPercent, MANUAL_SQL_CURVE);
        double mappedThreshold = floor + ((ceiling - floor) * curvedPercent);
        return Math.max(0, (int) Math.round(mappedThreshold));
    }

    public static final class Recommendation {
        private final int recommendedThresholdLevel;
        private final int noiseFloorLevel;
        private final int referenceSignalLevel;
        private final boolean limitedBySafetyFloor;
        private final boolean limitedByToneHeadroom;

        public Recommendation(
                int recommendedThresholdLevel,
                int noiseFloorLevel,
                int referenceSignalLevel,
                boolean limitedBySafetyFloor,
                boolean limitedByToneHeadroom
        ) {
            this.recommendedThresholdLevel = Math.max(0, recommendedThresholdLevel);
            this.noiseFloorLevel = Math.max(0, noiseFloorLevel);
            this.referenceSignalLevel = Math.max(0, referenceSignalLevel);
            this.limitedBySafetyFloor = limitedBySafetyFloor;
            this.limitedByToneHeadroom = limitedByToneHeadroom;
        }

        public int recommendedThresholdLevel() {
            return recommendedThresholdLevel;
        }

        public int noiseFloorLevel() {
            return noiseFloorLevel;
        }

        public int referenceSignalLevel() {
            return referenceSignalLevel;
        }

        public boolean limitedBySafetyFloor() {
            return limitedBySafetyFloor;
        }

        public boolean limitedByToneHeadroom() {
            return limitedByToneHeadroom;
        }
    }
}
