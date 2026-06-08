package org.bi9clt.cwcn.core.spectrum;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.app.SqlLevelStore;

import java.util.Locale;

public final class SqlObservationFormatter {
    private static final String EMPTY_DETAIL = "MAN - | IN - | NOI - | TON -";
    private static final String EMPTY_HINT = "REC - | T/N - | T/F - | POS -";

    private SqlObservationFormatter() {
    }

    public static String renderDetail(
            @Nullable SpectrumSnapshotData snapshotData,
            int fallbackManualThreshold
    ) {
        return renderDetail(snapshotData, fallbackManualThreshold, 0, 0);
    }

    public static String renderDetail(
            @Nullable SpectrumSnapshotData snapshotData,
            int fallbackManualThreshold,
            int referenceTrimDb,
            int currentTrimDb
    ) {
        if (snapshotData == null) {
            return EMPTY_DETAIL;
        }
        int manualThreshold = scaleLevel(
                resolveManualThreshold(snapshotData, fallbackManualThreshold),
                referenceTrimDb,
                currentTrimDb
        );
        int frameLevel = scaleLevel(
                Math.round(snapshotData.sqlFrameRmsAmplitude()),
                referenceTrimDb,
                currentTrimDb
        );
        int noiseLevel = scaleLevel(
                snapshotData.sqlNoiseFloorEstimate(),
                referenceTrimDb,
                currentTrimDb
        );
        int toneLevel = scaleLevel(
                Math.round(snapshotData.sqlToneRmsAmplitude()),
                referenceTrimDb,
                currentTrimDb
        );
        return "MAN " + presentLevel(manualThreshold)
                + " | IN " + presentLevel(frameLevel)
                + " | NOI " + presentLevel(noiseLevel)
                + " | TON " + presentLevel(toneLevel);
    }

    public static String renderHint(
            @Nullable SpectrumSnapshotData snapshotData,
            int fallbackManualThreshold
    ) {
        return renderHint(snapshotData, fallbackManualThreshold, 0, 0);
    }

    public static String renderHint(
            @Nullable SpectrumSnapshotData snapshotData,
            int fallbackManualThreshold,
            int referenceTrimDb,
            int currentTrimDb
    ) {
        if (snapshotData == null) {
            return EMPTY_HINT;
        }
        int recommendedThreshold = scaleLevel(
                resolveRecommendedThreshold(snapshotData),
                referenceTrimDb,
                currentTrimDb
        );
        double toneLevel = scaleLevel(
                snapshotData.sqlToneRmsAmplitude(),
                referenceTrimDb,
                currentTrimDb
        );
        double frameLevel = scaleLevel(
                snapshotData.sqlFrameRmsAmplitude(),
                referenceTrimDb,
                currentTrimDb
        );
        double noiseLevel = scaleLevel(
                snapshotData.sqlNoiseFloorEstimate(),
                referenceTrimDb,
                currentTrimDb
        );
        double manualThreshold = scaleLevel(
                resolveManualThreshold(snapshotData, fallbackManualThreshold),
                referenceTrimDb,
                currentTrimDb
        );
        return "REC " + presentLevel(recommendedThreshold)
                + " | T/N " + presentRatio(toneLevel, noiseLevel)
                + " | T/F " + presentRatio(toneLevel, frameLevel)
                + " | POS " + presentThresholdPosition(manualThreshold, noiseLevel, toneLevel);
    }

    private static int resolveManualThreshold(
            SpectrumSnapshotData snapshotData,
            int fallbackManualThreshold
    ) {
        if (snapshotData.sqlManualThreshold() > 0) {
            return snapshotData.sqlManualThreshold();
        }
        if (fallbackManualThreshold > 0) {
            return fallbackManualThreshold;
        }
        return snapshotData.sqlAttackThreshold();
    }

    private static int resolveRecommendedThreshold(SpectrumSnapshotData snapshotData) {
        if (snapshotData.sqlRecommendedThreshold() > 0) {
            return snapshotData.sqlRecommendedThreshold();
        }
        SqlThresholdAdvisor.Recommendation recommendation = SqlThresholdAdvisor.recommend(snapshotData);
        return recommendation.available() ? recommendation.recommendedThresholdLevel() : 0;
    }

    private static String presentLevel(int value) {
        return value > 0 ? String.valueOf(value) : "-";
    }

    private static String presentRatio(double numerator, double denominator) {
        if (denominator <= 0.0d || numerator < 0.0d) {
            return "-";
        }
        return String.format(Locale.US, "%.1fx", numerator / denominator);
    }

    private static String presentThresholdPosition(
            double manualThreshold,
            double noiseLevel,
            double toneLevel
    ) {
        double span = toneLevel - noiseLevel;
        if (manualThreshold <= 0.0d || span <= 0.0d) {
            return "-";
        }
        double position = (manualThreshold - noiseLevel) / span;
        return String.format(Locale.US, "%.2f", position);
    }

    private static int scaleLevel(int level, int referenceTrimDb, int currentTrimDb) {
        if (referenceTrimDb == currentTrimDb) {
            return level;
        }
        return SqlLevelStore.convertInternalLevelToDisplay(level, currentTrimDb, referenceTrimDb);
    }

    private static double scaleLevel(double level, int referenceTrimDb, int currentTrimDb) {
        if (referenceTrimDb == currentTrimDb) {
            return level;
        }
        return SqlLevelStore.convertInternalLevelToDisplay((float) level, currentTrimDb, referenceTrimDb);
    }
}
