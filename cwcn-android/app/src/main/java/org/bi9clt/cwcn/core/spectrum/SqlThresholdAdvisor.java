package org.bi9clt.cwcn.core.spectrum;

import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.SqlThresholdModel;

public final class SqlThresholdAdvisor {
    private SqlThresholdAdvisor() {
    }

    public static Recommendation recommend(SpectrumSnapshotData snapshotData) {
        if (snapshotData == null) {
            return Recommendation.none();
        }
        SqlThresholdModel.Recommendation recommendation = SqlThresholdModel.recommend(
                snapshotData.sqlNoiseFloorEstimate(),
                snapshotData.sqlSignalFloorEstimate(),
                snapshotData.sqlToneRmsAmplitude()
        );
        return new Recommendation(
                recommendation.recommendedThresholdLevel(),
                recommendation.noiseFloorLevel(),
                recommendation.referenceSignalLevel(),
                recommendation.limitedBySafetyFloor(),
                recommendation.limitedByToneHeadroom()
        );
    }

    public static Recommendation recommend(CwSignalSnapshot signalSnapshot) {
        if (signalSnapshot == null) {
            return Recommendation.none();
        }
        SqlThresholdModel.Recommendation recommendation = SqlThresholdModel.recommend(
                signalSnapshot.noiseFloorEstimate(),
                signalSnapshot.signalFloorEstimate(),
                signalSnapshot.lastToneRmsAmplitude()
        );
        return new Recommendation(
                recommendation.recommendedThresholdLevel(),
                recommendation.noiseFloorLevel(),
                recommendation.referenceSignalLevel(),
                recommendation.limitedBySafetyFloor(),
                recommendation.limitedByToneHeadroom()
        );
    }

    public static final class Recommendation {
        private static final Recommendation NONE = new Recommendation(0, 0, 0, false, false);

        private final int recommendedThresholdLevel;
        private final int noiseFloorLevel;
        private final int toneLevel;
        private final boolean limitedBySafetyFloor;
        private final boolean limitedByToneHeadroom;

        private Recommendation(
                int recommendedThresholdLevel,
                int noiseFloorLevel,
                int toneLevel,
                boolean limitedBySafetyFloor,
                boolean limitedByToneHeadroom
        ) {
            this.recommendedThresholdLevel = Math.max(0, recommendedThresholdLevel);
            this.noiseFloorLevel = Math.max(0, noiseFloorLevel);
            this.toneLevel = Math.max(0, toneLevel);
            this.limitedBySafetyFloor = limitedBySafetyFloor;
            this.limitedByToneHeadroom = limitedByToneHeadroom;
        }

        public static Recommendation none() {
            return NONE;
        }

        public boolean available() {
            return recommendedThresholdLevel > 0;
        }

        public int recommendedThresholdLevel() {
            return recommendedThresholdLevel;
        }

        public int noiseFloorLevel() {
            return noiseFloorLevel;
        }

        public int toneLevel() {
            return toneLevel;
        }

        public boolean limitedBySafetyFloor() {
            return limitedBySafetyFloor;
        }

        public boolean limitedByToneHeadroom() {
            return limitedByToneHeadroom;
        }
    }
}
