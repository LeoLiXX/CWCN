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
    private static final double MIN_POST_DECODE_CONTINUATION_TONE_DOMINANCE_RATIO = 0.18d;
    private static final double MIN_POST_DECODE_CONTINUATION_ISOLATION_RATIO = 0.18d;
    private static final int MIN_POST_DECODE_CONTINUATION_CONSECUTIVE_LOCKED_FRAMES = 2;
    private static final int MIN_POST_DECODE_CONTINUATION_REPRESENTATIVE_LOCKED_FRAMES = 2;
    private static final double MIN_RESTART_SQL_CLEAR_RATIO = 0.62d;
    private static final double MIN_RESTART_SIGNAL_FLOOR_CLEAR_RATIO = 0.82d;
    private static final double MIN_RESTART_TONE_DOMINANCE_RATIO = 0.18d;
    private static final double MIN_RESTART_ISOLATION_RATIO = 0.18d;
    private static final int MIN_RESTART_CONSECUTIVE_LOCKED_FRAMES = 2;
    private static final int MIN_RESTART_REPRESENTATIVE_LOCKED_FRAMES = 2;
    private static final double MIN_RESTART_SIGNAL_OVER_NOISE = 100.0d;
    private static final double MIN_BOOTSTRAP_CONTINUATION_SQL_CLEAR_RATIO = 0.96d;
    private static final double MIN_BOOTSTRAP_CONTINUATION_SIGNAL_FLOOR_CLEAR_RATIO = 1.02d;
    private static final double MIN_BOOTSTRAP_CONTINUATION_TONE_DOMINANCE_RATIO = 0.18d;
    private static final double MIN_BOOTSTRAP_CONTINUATION_ISOLATION_RATIO = 0.28d;
    private static final double MIN_BOOTSTRAP_CONTINUATION_LOCKED_RATIO = 0.12d;
    private static final int MIN_BOOTSTRAP_CONTINUATION_CONSECUTIVE_LOCKED_FRAMES = 3;
    private static final int MIN_BOOTSTRAP_CONTINUATION_REPRESENTATIVE_LOCKED_FRAMES = 2;
    private static final double MIN_BOOTSTRAP_CONTINUATION_SIGNAL_OVER_NOISE = 140.0d;

    private RxTurnActivityDecider() {
    }

    public static boolean isMeaningfulTurnActivity(CwSignalSnapshot signalSnapshot) {
        if (signalSnapshot == null) {
            return false;
        }
        if (signalSnapshot.targetToneLocked()) {
            return true;
        }
        return passesToneActiveEvidence(
                signalSnapshot,
                MIN_SQL_CLEAR_RATIO,
                MIN_SIGNAL_FLOOR_CLEAR_RATIO,
                MIN_TONE_DOMINANCE_RATIO,
                MIN_ISOLATION_RATIO,
                MIN_LOCKED_RATIO,
                MIN_CONSECUTIVE_LOCKED_FRAMES,
                0,
                MIN_SIGNAL_OVER_NOISE
        );
    }

    public static boolean isMeaningfulTurnContinuation(CwSignalSnapshot signalSnapshot) {
        if (signalSnapshot == null || signalSnapshot.toneActive()) {
            return false;
        }
        return passesSilentContinuationEvidence(signalSnapshot);
    }

    public static boolean isMeaningfulTurnPostDecodeContinuation(CwSignalSnapshot signalSnapshot) {
        if (signalSnapshot == null) {
            return false;
        }
        if (signalSnapshot.toneActive()) {
            return passesToneActiveEvidence(
                    signalSnapshot,
                    MIN_CONTINUATION_SQL_CLEAR_RATIO,
                    MIN_CONTINUATION_SIGNAL_FLOOR_CLEAR_RATIO,
                    MIN_POST_DECODE_CONTINUATION_TONE_DOMINANCE_RATIO,
                    MIN_POST_DECODE_CONTINUATION_ISOLATION_RATIO,
                    MIN_LOCKED_RATIO,
                    MIN_POST_DECODE_CONTINUATION_CONSECUTIVE_LOCKED_FRAMES,
                    MIN_POST_DECODE_CONTINUATION_REPRESENTATIVE_LOCKED_FRAMES,
                    MIN_SIGNAL_OVER_NOISE
            );
        }
        return passesSilentContinuationEvidence(signalSnapshot);
    }

    public static boolean isMeaningfulTurnRestartActivity(CwSignalSnapshot signalSnapshot) {
        return passesToneActiveEvidence(
                signalSnapshot,
                MIN_RESTART_SQL_CLEAR_RATIO,
                MIN_RESTART_SIGNAL_FLOOR_CLEAR_RATIO,
                MIN_RESTART_TONE_DOMINANCE_RATIO,
                MIN_RESTART_ISOLATION_RATIO,
                MIN_LOCKED_RATIO,
                MIN_RESTART_CONSECUTIVE_LOCKED_FRAMES,
                MIN_RESTART_REPRESENTATIVE_LOCKED_FRAMES,
                MIN_RESTART_SIGNAL_OVER_NOISE
        );
    }

    private static boolean passesSilentContinuationEvidence(CwSignalSnapshot signalSnapshot) {
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

    private static boolean passesToneActiveEvidence(
            CwSignalSnapshot signalSnapshot,
            double minSqlClearRatio,
            double minSignalFloorClearRatio,
            double minToneDominanceRatio,
            double minIsolationRatio,
            double minLockedRatio,
            int minConsecutiveLockedFrames,
            int minRepresentativeLockedFrames,
            double minSignalOverNoise
    ) {
        if (signalSnapshot == null || !signalSnapshot.toneActive()) {
            return false;
        }
        boolean sqlQualified = sqlClearRatio(signalSnapshot) >= minSqlClearRatio
                || signalFloorClearRatio(signalSnapshot) >= minSignalFloorClearRatio;
        boolean toneShapeQualified = signalSnapshot.toneDominanceRatio() >= minToneDominanceRatio
                && signalSnapshot.narrowbandIsolationRatio() >= minIsolationRatio;
        boolean lockContextQualified = signalSnapshot.consecutiveLockedFrames() >= minConsecutiveLockedFrames
                || signalSnapshot.recentLockedFrameRatio() >= minLockedRatio
                || signalSnapshot.representativeLockedToneFrameCount() >= minRepresentativeLockedFrames;
        boolean signalAboveNoise = signalSnapshot.lastToneRmsAmplitude()
                >= signalSnapshot.noiseFloorEstimate() + minSignalOverNoise
                || signalSnapshot.signalFloorEstimate()
                >= signalSnapshot.noiseFloorEstimate() + minSignalOverNoise;
        return sqlQualified && toneShapeQualified && (lockContextQualified || signalAboveNoise);
    }

    public static boolean isMeaningfulTurnBootstrapContinuation(CwSignalSnapshot signalSnapshot) {
        if (signalSnapshot == null || !signalSnapshot.toneActive()) {
            return false;
        }
        boolean sqlQualified = sqlClearRatio(signalSnapshot) >= MIN_BOOTSTRAP_CONTINUATION_SQL_CLEAR_RATIO
                || signalFloorClearRatio(signalSnapshot) >= MIN_BOOTSTRAP_CONTINUATION_SIGNAL_FLOOR_CLEAR_RATIO;
        boolean toneShapeQualified =
                signalSnapshot.toneDominanceRatio() >= MIN_BOOTSTRAP_CONTINUATION_TONE_DOMINANCE_RATIO
                        && signalSnapshot.narrowbandIsolationRatio()
                        >= MIN_BOOTSTRAP_CONTINUATION_ISOLATION_RATIO;
        boolean lockContextQualified =
                signalSnapshot.consecutiveLockedFrames()
                        >= MIN_BOOTSTRAP_CONTINUATION_CONSECUTIVE_LOCKED_FRAMES
                        || signalSnapshot.recentLockedFrameRatio()
                        >= MIN_BOOTSTRAP_CONTINUATION_LOCKED_RATIO
                        || signalSnapshot.representativeLockedToneFrameCount()
                        >= MIN_BOOTSTRAP_CONTINUATION_REPRESENTATIVE_LOCKED_FRAMES;
        boolean signalAboveNoise = signalSnapshot.lastToneRmsAmplitude()
                >= signalSnapshot.noiseFloorEstimate() + MIN_BOOTSTRAP_CONTINUATION_SIGNAL_OVER_NOISE
                || signalSnapshot.signalFloorEstimate()
                >= signalSnapshot.noiseFloorEstimate() + MIN_BOOTSTRAP_CONTINUATION_SIGNAL_OVER_NOISE;
        return sqlQualified && toneShapeQualified && lockContextQualified && signalAboveNoise;
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
