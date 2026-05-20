package org.bi9clt.cwcn.core.rx;

import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;

/**
 * Separates meaningful CW turn activity from broad front-end "toneActive".
 *
 * <p>{@code toneActive()} is intentionally permissive because it helps the
 * front-end bridge short valleys and weak residues. Turn segmentation and idle
 * handling need a narrower notion of "the other station is still talking",
 * otherwise white noise can keep one long turn alive across real gaps.</p>
 */
public final class RxTurnActivityDecider {
    private static final double MIN_SQL_CLEAR_RATIO = 0.82d;
    private static final double MIN_SIGNAL_FLOOR_CLEAR_RATIO = 0.92d;
    private static final double MIN_TONE_DOMINANCE_RATIO = 0.12d;
    private static final double MIN_ISOLATION_RATIO = 0.24d;
    private static final double MIN_LOCKED_RATIO = 0.08d;
    private static final double MIN_SIGNAL_OVER_NOISE = 80.0d;
    private static final int MIN_CONSECUTIVE_LOCKED_FRAMES = 2;
    private static final double MIN_CONTINUATION_SQL_CLEAR_RATIO = 0.42d;
    private static final double MIN_CONTINUATION_SIGNAL_FLOOR_CLEAR_RATIO = 0.72d;
    private static final double MIN_CONTINUATION_TONE_DOMINANCE_RATIO = 0.12d;
    private static final double MIN_CONTINUATION_ISOLATION_RATIO = 0.12d;
    private static final int MAX_CONTINUATION_TARGET_DRIFT_HZ = 90;

    private RxTurnActivityDecider() {
    }

    public static boolean isMeaningfulTurnActivity(CwSignalSnapshot signalSnapshot) {
        if (signalSnapshot == null) {
            return false;
        }
        if (signalSnapshot.targetToneLocked()) {
            return true;
        }
        if (!signalSnapshot.toneActive()) {
            return false;
        }

        boolean sqlQualified = sqlClearRatio(signalSnapshot) >= MIN_SQL_CLEAR_RATIO
                || signalFloorClearRatio(signalSnapshot) >= MIN_SIGNAL_FLOOR_CLEAR_RATIO;
        boolean toneShapeQualified = signalSnapshot.toneDominanceRatio() >= MIN_TONE_DOMINANCE_RATIO
                && signalSnapshot.narrowbandIsolationRatio() >= MIN_ISOLATION_RATIO;
        boolean lockContextQualified = signalSnapshot.consecutiveLockedFrames() >= MIN_CONSECUTIVE_LOCKED_FRAMES
                || signalSnapshot.recentLockedFrameRatio() >= MIN_LOCKED_RATIO;
        boolean signalAboveNoise = signalSnapshot.lastToneRmsAmplitude()
                >= signalSnapshot.noiseFloorEstimate() + MIN_SIGNAL_OVER_NOISE
                || signalSnapshot.signalFloorEstimate()
                >= signalSnapshot.noiseFloorEstimate() + MIN_SIGNAL_OVER_NOISE;

        return sqlQualified && toneShapeQualified && (lockContextQualified || signalAboveNoise);
    }

    public static boolean isMeaningfulTurnContinuation(CwSignalSnapshot signalSnapshot) {
        if (isMeaningfulTurnActivity(signalSnapshot)) {
            return true;
        }
        if (signalSnapshot == null || signalSnapshot.toneActive()) {
            return false;
        }

        int targetToneHz = signalSnapshot.targetToneFrequencyHz();
        int effectiveTrackedToneHz = signalSnapshot.effectiveTrackedToneFrequencyHz();
        if (targetToneHz <= 0
                || effectiveTrackedToneHz <= 0
                || Math.abs(targetToneHz - effectiveTrackedToneHz) > MAX_CONTINUATION_TARGET_DRIFT_HZ) {
            return false;
        }

        boolean sqlQualified = sqlClearRatio(signalSnapshot) >= MIN_CONTINUATION_SQL_CLEAR_RATIO
                || signalFloorClearRatio(signalSnapshot) >= MIN_CONTINUATION_SIGNAL_FLOOR_CLEAR_RATIO;
        boolean toneShapeQualified = signalSnapshot.toneDominanceRatio() >= MIN_CONTINUATION_TONE_DOMINANCE_RATIO
                && signalSnapshot.narrowbandIsolationRatio() >= MIN_CONTINUATION_ISOLATION_RATIO;
        return sqlQualified && toneShapeQualified;
    }

    public static boolean shouldResetFrontEndOnTurnEnd(CwSignalSnapshot signalSnapshot) {
        if (signalSnapshot == null) {
            return true;
        }
        // Empty pseudo-turns caused by noise or bootstrap wobble should not clear the
        // front-end learner, otherwise acquisition can be repeatedly reset before the
        // first real CW onset ever produces a tone event.
        if (signalSnapshot.totalToneOnEvents() <= 0
                && signalSnapshot.totalToneOffEvents() <= 0) {
            return false;
        }
        // A few weak or noisy tone events are still not enough evidence that the
        // front-end has learned a meaningful carrier. Keep that memory alive across
        // pseudo-turn boundaries so fixed-tone bootstrap can continue converging on
        // the actual CW tone in hard samples such as recording(3).
        if (signalSnapshot.targetToneLocked()) {
            return true;
        }
        if (signalSnapshot.maxConsecutiveLockedFrames() >= 4) {
            return true;
        }
        return signalSnapshot.lockedFrameCount() >= 8
                && signalSnapshot.representativeLockedToneFrameCount() > 0;
    }

    private static double sqlClearRatio(CwSignalSnapshot signalSnapshot) {
        return signalSnapshot.lastToneRmsAmplitude()
                / Math.max(1.0d, signalSnapshot.releaseThreshold());
    }

    private static double signalFloorClearRatio(CwSignalSnapshot signalSnapshot) {
        return signalSnapshot.signalFloorEstimate()
                / Math.max(1.0d, signalSnapshot.releaseThreshold());
    }
}
