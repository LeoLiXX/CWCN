package org.bi9clt.cwcn.core.signal;

import org.bi9clt.cwcn.core.audio.AudioFrame;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CwSignalProcessor {
    // Preferred-tone audit note:
    // 1. preferredToneFrequencyHz is a soft prior and a search-window hint only.
    // 2. It may seed the initial target before any audio is processed.
    // 3. After processing starts, no branch should silently rewrite targetToneFrequencyHz
    //    back to preferredToneFrequencyHz just because acquisition is weak or absent.
    // 4. Any off-preferred adoption must happen through evidence-bearing paths:
    //    preferred-window winner, wide-scan winner, or locked-retention winner.
    private static final int RECENT_HISTORY_WINDOW_FRAMES = 24;
    private static final int DEFAULT_PREFERRED_TONE_FREQUENCY_HZ = 650;
    private static final int MIN_TRACKED_TONE_FREQUENCY_HZ = 400;
    private static final int MAX_TRACKED_TONE_FREQUENCY_HZ = 850;
    private static final int TONE_SCAN_STEP_HZ = 10;
    private static final int TONE_BUCKET_COUNT =
            ((MAX_TRACKED_TONE_FREQUENCY_HZ - MIN_TRACKED_TONE_FREQUENCY_HZ) / TONE_SCAN_STEP_HZ) + 1;
    private static final int RETUNE_INTERVAL_FRAMES = 4;
    private static final int PREFERRED_TONE_SCAN_WINDOW_HZ = 160;
    private static final int UNLOCKED_ACQUISITION_SCAN_WINDOW_HZ = 160;
    private static final int LOCK_RETUNE_WINDOW_HZ = 50;
    public static final int DEFAULT_FIXED_TONE_LEARNING_WINDOW_HZ = 50;
    public static final int MIN_FIXED_TONE_LEARNING_WINDOW_HZ = 20;
    public static final int MAX_FIXED_TONE_LEARNING_WINDOW_HZ = 120;
    private static final int FIXED_TONE_UNLOCKED_ACQUISITION_WINDOW_EXTRA_HZ = 20;
    private static final int FIXED_TONE_LOCK_RETUNE_WINDOW_MARGIN_HZ = 20;
    private static final int MIN_FIXED_TONE_LOCK_RETUNE_WINDOW_HZ = 10;
    private static final int FIXED_TONE_BOOTSTRAP_ESCAPE_SCAN_WINDOW_HZ = 180;
    private static final int FIXED_TONE_BOOTSTRAP_ESCAPE_MIN_FRAMES = 6;
    private static final int PREFERRED_WEIGHT_FULL_BIAS_WINDOW_HZ = 35;
    private static final int PREFERRED_ACQUISITION_SOFT_BIAS_WINDOW_HZ = 90;
    private static final int CONTINUITY_WEIGHT_FULL_BIAS_WINDOW_HZ = 20;
    private static final int CONTINUITY_WEIGHT_SOFT_BIAS_WINDOW_HZ = 120;
    private static final int ABSOLUTE_EDGE_PENALTY_WINDOW_HZ = 20;
    private static final int CANDIDATE_STABILITY_ACCEPT_SCANS = 2;
    private static final int CANDIDATE_STABILITY_CLUSTER_WINDOW_HZ = 20;
    private static final int LOCK_LOSS_GRACE_FRAMES = 3;
    private static final double MIN_LOCK_DOMINANCE_RATIO = 0.24d;
    private static final double MIN_NARROWBAND_DOMINANCE_RATIO = 0.12d;
    private static final double MIN_LOCK_ISOLATION_RATIO = 0.34d;
    private static final double MIN_NARROWBAND_ISOLATION_RATIO = 0.24d;
    private static final double MIN_LOCK_LOCAL_CONTRAST_RATIO = 0.54d;
    private static final double MIN_NARROWBAND_LOCAL_CONTRAST_RATIO = 0.50d;
    private static final double ACTIVE_TONE_LOCK_DOMINANCE_RATIO = 0.14d;
    private static final double ACTIVE_TONE_LOCK_ISOLATION_RATIO = 0.22d;
    private static final double ACTIVE_TONE_LOCK_LOCAL_CONTRAST_RATIO = 0.48d;
    private static final int[] LOCAL_CONTRAST_OFFSETS_HZ = new int[]{80, 120};
    private static final double LOCKED_SIGNAL_BLEND = 0.82d;
    private static final double UNLOCKED_SIGNAL_BLEND_FLOOR = 0.18d;
    private static final double UNLOCKED_TONE_GAIN = 1.18d;
    private static final int MIN_TRACKED_TONE_RMS = 120;
    private static final int DEFAULT_SQL_PERCENT = SqlThresholdModel.DEFAULT_SQL_PERCENT;
    private static final int EDGE_WINDOW_SAMPLES = 12;
    private static final int EDGE_CONFIRM_SAMPLES = 6;
    private static final double EDGE_THRESHOLD_RATIO = 0.30d;
    private static final double EDGE_DYNAMIC_RATIO = 0.24d;
    private static final double EDGE_TRANSITION_REQUIRED_RATIO = 0.82d;
    private static final double NOISE_FLOOR_RISE_SMOOTHING = 0.025d;
    private static final double NOISE_FLOOR_DROP_SMOOTHING = 0.14d;
    private static final double SIGNAL_FLOOR_SMOOTHING = 0.18d;
    private static final double SIGNAL_FLOOR_IDLE_DECAY_SMOOTHING = 0.62d;
    private static final double SIGNAL_FLOOR_IDLE_RISE_SMOOTHING = 0.10d;
    private static final int LOCKED_BRANCH_REFERENCE_MIN_STREAK_FRAMES = 4;
    private static final double LOCKED_BRANCH_REFERENCE_RISE_SMOOTHING = 0.24d;
    private static final double LOCKED_BRANCH_REFERENCE_DECAY_SMOOTHING = 0.03d;
    private static final double LOCKED_BRANCH_WEAK_RATIO = 0.46d;
    private static final double LOCKED_BRANCH_REFERENCE_MIN_THRESHOLD_MULTIPLIER = 1.75d;
    private static final double ACTIVE_RELEASE_WEAK_REFERENCE_MAX_RATIO = 0.20d;
    private static final double ACTIVE_RELEASE_WEAK_REFERENCE_FLOOR_RATIO = 0.20d;
    private static final double ACTIVE_RELEASE_WEAK_ATTACK_MULTIPLIER_MAX = 8.6d;
    private static final double ACTIVE_RELEASE_WEAK_SIGNAL_MULTIPLIER_MAX = 7.2d;
    private static final double ACTIVE_RELEASE_STABLE_DECAY_REFERENCE_FLOOR_RATIO = 0.70d;
    private static final double ACTIVE_RELEASE_STABLE_DECAY_MAX_REFERENCE_RATIO = 0.92d;
    private static final int AUTO_TRACK_WEAK_VALLEY_RESCUE_ANCHOR_DRIFT_HZ = 20;
    private static final int AUTO_TRACK_WEAK_VALLEY_RESCUE_NEAR_TARGET_OFFSET_HZ = 15;
    private static final int AUTO_TRACK_WEAK_VALLEY_RESCUE_MIN_STABLE_LOCK_FRAMES = 6;
    private static final int AUTO_TRACK_WEAK_VALLEY_RESCUE_MIN_REPRESENTATIVE_FRAMES = 6;
    private static final double AUTO_TRACK_WEAK_VALLEY_RESCUE_LOCKED_RATIO_MIN = 0.22d;
    private static final double AUTO_TRACK_WEAK_VALLEY_RESCUE_NEAR_TARGET_RATIO_MIN = 0.68d;
    private static final double AUTO_TRACK_WEAK_VALLEY_RESCUE_MIN_THRESHOLD_RATIO = 0.15d;
    private static final double AUTO_TRACK_WEAK_VALLEY_RESCUE_REFERENCE_MIN_THRESHOLD_MULTIPLIER = 1.15d;
    private static final double AUTO_TRACK_WEAK_VALLEY_RESCUE_REFERENCE_RATIO_MAX = 0.68d;
    private static final double AUTO_TRACK_WEAK_VALLEY_RESCUE_DOMINANCE_MIN = 0.10d;
    private static final double AUTO_TRACK_WEAK_VALLEY_RESCUE_ISOLATION_MIN = 0.16d;
    private static final double AUTO_TRACK_WEAK_VALLEY_RESCUE_LOCAL_CONTRAST_MIN = 0.42d;
    private static final long AUTO_TRACK_WEAK_VALLEY_RESCUE_CURRENT_RUN_MIN_TONE_DURATION_MS = 96L;
    private static final long TONE_OFF_HANG_MS = 4L;
    private static final long AUTO_TRACK_TONE_OFF_HANG_MS = 14L;
    private static final int AUTO_TRACK_WEAK_VALLEY_BRIDGE_FRAMES = 2;
    private static final int AUTO_TRACK_RELEASE_TAIL_HOLD_FRAMES = 2;
    private static final int AUTO_TRACK_RELEASE_TAIL_HOLD_MAX_ANCHOR_DRIFT_HZ = 20;
    private static final double AUTO_TRACK_RELEASE_TAIL_HOLD_MIN_THRESHOLD_RATIO = 0.88d;
    private static final double AUTO_TRACK_RELEASE_TAIL_HOLD_MIN_LOCKED_RATIO = 0.34d;
    private static final double AUTO_TRACK_RELEASE_TAIL_HOLD_MIN_NEAR_TARGET_RATIO = 0.84d;
    private static final double AUTO_TRACK_RELEASE_TAIL_HOLD_REFERENCE_MIN_THRESHOLD_MULTIPLIER = 1.10d;
    private static final int AUTO_TRACK_RELEASE_TAIL_HOLD_CURRENT_RUN_MIN_STABLE_LOCK_FRAMES = 4;
    private static final long AUTO_TRACK_RELEASE_TAIL_HOLD_CURRENT_RUN_MIN_TONE_DURATION_MS = 96L;
    private static final long AUTO_TRACK_RELEASE_TAIL_HOLD_WEAK_BOOTSTRAP_MIN_TONE_DURATION_MS = 48L;
    private static final double AUTO_TRACK_RELEASE_TAIL_HOLD_CURRENT_RUN_MIN_DOMINANCE = 0.82d;
    private static final double AUTO_TRACK_RELEASE_TAIL_HOLD_CURRENT_RUN_MIN_ISOLATION = 0.58d;
    private static final double AUTO_TRACK_RELEASE_TAIL_HOLD_CURRENT_RUN_MIN_LOCAL_CONTRAST = 0.72d;
    private static final double AUTO_TRACK_RELEASE_TAIL_HOLD_DOMINANCE_MIN = 0.70d;
    private static final double AUTO_TRACK_RELEASE_TAIL_HOLD_ISOLATION_MIN = 0.50d;
    private static final double AUTO_TRACK_RELEASE_TAIL_HOLD_LOCAL_CONTRAST_MIN = 0.54d;
    private static final double AUTO_TRACK_RELEASE_TAIL_HOLD_OPENING_WEAK_BOOTSTRAP_MIN_THRESHOLD_RATIO = 0.14d;
    private static final double AUTO_TRACK_RELEASE_TAIL_HOLD_OPENING_WEAK_BOOTSTRAP_MIN_LOCKED_RATIO = 0.75d;
    private static final double AUTO_TRACK_RELEASE_TAIL_HOLD_WEAK_BOOTSTRAP_MIN_THRESHOLD_RATIO = 0.24d;
    private static final double AUTO_TRACK_RELEASE_TAIL_HOLD_WEAK_BOOTSTRAP_DOMINANCE_MIN = 0.52d;
    private static final double AUTO_TRACK_RELEASE_TAIL_HOLD_WEAK_BOOTSTRAP_ISOLATION_MIN = 0.38d;
    private static final double AUTO_TRACK_RELEASE_TAIL_HOLD_WEAK_BOOTSTRAP_LOCAL_CONTRAST_MIN = 0.70d;
    private static final int AUTO_TRACK_RELEASE_TAIL_HOLD_WEAK_BOOTSTRAP_MAX_APPLICATIONS_PER_RUN = 1;
    private static final long FIXED_TONE_RELEASE_TAIL_HOLD_MIN_TONE_DURATION_MS = 176L;
    private static final int CONTINUITY_ANCHOR_TRUST_MIN_STABLE_LOCK_FRAMES = 4;
    private static final int CONTINUITY_ANCHOR_TRUST_MIN_REPRESENTATIVE_FRAMES = 3;
    private static final int CONTINUITY_ANCHOR_TRUST_MIN_HYPOTHESIS_SUPPORT_FRAMES = 4;
    private static final double CONTINUITY_ANCHOR_TRUST_MIN_HYPOTHESIS_CONFIDENCE = 0.72d;
    private static final double CONTINUITY_ANCHOR_TRUST_MIN_LOCKED_RATIO = 0.34d;
    private static final double CONTINUITY_ANCHOR_TRUST_MIN_NEAR_TARGET_RATIO = 0.72d;
    private static final long TRACKED_TONE_IDLE_HANG_MS = 240L;
    private static final long TRACKED_TONE_IDLE_HANG_MIN_MS = 240L;
    private static final double TRACKED_TONE_IDLE_HANG_DOT_RATIO = 7.2d;
    private static final long FRAME_GAP_RESET_MIN_MS = 40L;
    private static final double FRAME_GAP_RESET_MULTIPLIER = 4.0d;
    private static final int REPRESENTATIVE_LOCKED_TONE_CLUSTER_WINDOW_HZ = 20;
    private static final int REPRESENTATIVE_LOCKED_TONE_REPLACEMENT_MIN_FRAMES = 6;
    private static final double REPRESENTATIVE_LOCKED_TONE_REPLACEMENT_MIN_AVERAGE_SCORE_RATIO = 0.85d;
    private static final double TONE_HYPOTHESIS_DECAY_LOCKED = 0.992d;
    private static final double TONE_HYPOTHESIS_DECAY_ACTIVE = 0.982d;
    private static final double TONE_HYPOTHESIS_DECAY_IDLE = 0.930d;
    private static final double TONE_HYPOTHESIS_DECAY_STALE = 0.820d;
    private static final double TONE_HYPOTHESIS_LOCKED_EVIDENCE_GAIN = 1.35d;
    private static final double TONE_HYPOTHESIS_ACTIVE_EVIDENCE_GAIN = 0.58d;
    private static final double TONE_HYPOTHESIS_ACQUISITION_WINNER_GAIN = 0.78d;
    private static final double TONE_HYPOTHESIS_RUNNER_UP_GAIN = 0.20d;
    private static final double TONE_HYPOTHESIS_MIN_TOTAL_EVIDENCE = 0.70d;
    private static final double TONE_HYPOTHESIS_MIN_CONFIDENCE = 0.30d;
    private static final int TONE_HYPOTHESIS_CLEAR_IDLE_FRAMES = 28;
    private static final int HYPOTHESIS_GUARD_HISTORY_WINDOW_FRAMES = 16;
    private static final int HYPOTHESIS_GUARD_MIN_SUPPORT_FRAMES = 8;
    private static final double HYPOTHESIS_GUARD_MIN_CONFIDENCE = 0.78d;
    private static final int HYPOTHESIS_GUARD_STABLE_SPAN_MAX_HZ = 30;
    private static final int HYPOTHESIS_GUARD_MIN_DRIFT_FROM_TARGET_HZ = 40;
    private static final int HYPOTHESIS_GUARD_MAX_DRIFT_FROM_TARGET_HZ = 160;
    private static final int HYPOTHESIS_GUARD_REPRESENTATIVE_MAX_OFFSET_HZ = 25;
    private static final int HYPOTHESIS_GUARD_MIN_REPRESENTATIVE_FRAMES = 6;
    private static final double HYPOTHESIS_GUARD_BLOCK_STRONG_ISOLATION_RATIO = 0.88d;
    private static final double HYPOTHESIS_GUARD_BLOCK_STRONG_TONE_MULTIPLIER = 2.8d;
    private static final double HYPOTHESIS_GUARD_APPLY_MIN_CONFIDENCE = 0.26d;
    private static final double HYPOTHESIS_GUARD_APPLY_STRONG_SEARCH_LEAD = 1.18d;
    private static final int LOCKED_CONSENSUS_GUARD_MIN_STABLE_LOCK_FRAMES = 8;
    private static final int LOCKED_CONSENSUS_GUARD_MIN_ACTIVE_CENTER_OBSERVATIONS = 8;
    private static final int LOCKED_CONSENSUS_GUARD_MAX_SOURCE_SPAN_HZ = 20;
    private static final int LOCKED_CONSENSUS_GUARD_MIN_DRIFT_HZ = 40;
    private static final int LOCKED_RETUNE_GUARD_MIN_DRIFT_HZ = 40;
    private static final int LOCKED_RETUNE_GUARD_NEAR_FREQUENCY_MIN_DRIFT_HZ = 25;
    private static final int LOCKED_RETUNE_GUARD_FAR_DRIFT_HZ = 80;
    private static final int LOCKED_RETUNE_GUARD_MIN_STABLE_LOCK_FRAMES = 8;
    private static final int LOCKED_RETUNE_GUARD_REQUIRED_SCANS = 2;
    private static final int LOCKED_RETUNE_GUARD_FAR_REQUIRED_SCANS = 3;
    private static final int LOCKED_RETUNE_GUARD_WEAK_REFERENCE_EXTRA_SCANS = 2;
    private static final double LOCKED_RETUNE_GUARD_STRONG_SCORE_RATIO = 1.38d;
    private static final double LOCKED_RETUNE_GUARD_STRONG_TONE_RATIO = 1.45d;
    private static final double LOCKED_RETUNE_GUARD_DECISIVE_SCORE_RATIO = 2.40d;
    private static final double LOCKED_RETUNE_GUARD_DECISIVE_TONE_RATIO = 2.10d;
    private static final double LOCKED_RETUNE_GUARD_DECISIVE_DOMINANCE_RATIO = 0.90d;
    private static final double LOCKED_RETUNE_GUARD_DECISIVE_ISOLATION_RATIO = 0.80d;
    private static final double LOCKED_RETUNE_GUARD_DECISIVE_LOCAL_CONTRAST_RATIO = 0.82d;
    private static final double LOCKED_RETUNE_GUARD_FAR_STRONG_SCORE_RATIO = 1.55d;
    private static final double LOCKED_RETUNE_GUARD_FAR_STRONG_TONE_RATIO = 1.70d;
    private static final double LOCKED_RETUNE_GUARD_WEAK_REFERENCE_TONE_RATIO = 0.72d;
    private static final int TONE_ACTIVE_FAR_SEARCH_PREVIOUS_PLAUSIBLE_EXTRA_SCANS = 1;
    private static final double TONE_ACTIVE_LOCKED_SEARCH_REPLACE_SCORE_RATIO = 1.26d;
    private static final double TONE_ACTIVE_LOCKED_SEARCH_REPLACE_TONE_RATIO = 1.12d;
    private static final double TONE_ACTIVE_FAR_SEARCH_RELEASE_SCORE_RATIO = 1.18d;
    private static final double TONE_ACTIVE_FAR_SEARCH_RELEASE_TONE_RATIO = 1.24d;
    private static final double TONE_ACTIVE_FAR_SEARCH_IMMEDIATE_SCORE_RATIO = 1.70d;
    private static final double TONE_ACTIVE_FAR_SEARCH_IMMEDIATE_TONE_RATIO = 1.85d;
    private static final double TONE_ACTIVE_PREVIOUS_TARGET_MIN_REFERENCE_TONE_RATIO = 0.12d;
    private static final double TONE_ACTIVE_PREVIOUS_TARGET_MIN_TONE_MULTIPLIER = 1.50d;
    private static final double TONE_ACTIVE_PREVIOUS_TARGET_MIN_DOMINANCE_RATIO = 0.05d;
    private static final double CONTINUITY_FAR_CANDIDATE_RELEASE_TONE_RATIO = 0.72d;
    private static final double POST_RELEASE_ONSET_GUARD_MIN_FRAME_RMS_GROWTH_RATIO = 1.12d;
    private static final double POST_RELEASE_ONSET_GUARD_MIN_TONE_RMS_GROWTH_RATIO = 1.10d;
    private static final long POST_RELEASE_NEAR_TARGET_REACQUIRE_WINDOW_MS = 220L;
    private static final long POST_RELEASE_NEAR_TARGET_REACQUIRE_WINDOW_MIN_MS = 40L;
    private static final int POST_RELEASE_NEAR_TARGET_MAX_ANCHOR_DRIFT_HZ = 20;
    private static final double POST_RELEASE_NEAR_TARGET_ATTACK_SLACK_RATIO = 0.93d;
    private static final double POST_RELEASE_NEAR_TARGET_STEADY_ATTACK_SLACK_RATIO = 0.92d;
    private static final double POST_RELEASE_NEAR_TARGET_DOMINANCE_MIN = 0.30d;
    private static final double POST_RELEASE_NEAR_TARGET_ISOLATION_MIN = 0.30d;
    private static final double POST_RELEASE_NEAR_TARGET_LOCAL_CONTRAST_MIN = 0.46d;
    private static final double POST_RELEASE_NEAR_TARGET_MIN_FRAME_RMS_GROWTH_RATIO = 1.02d;
    private static final double POST_RELEASE_NEAR_TARGET_MIN_TONE_RMS_GROWTH_RATIO = 1.02d;
    private static final double POST_RELEASE_NEAR_TARGET_STEADY_DOMINANCE_MIN = 0.84d;
    private static final double POST_RELEASE_NEAR_TARGET_STEADY_ISOLATION_MIN = 0.60d;
    private static final long POST_RELEASE_NEAR_TARGET_STEADY_MIN_GAP_MS = 48L;
    private static final long POST_RELEASE_NEAR_TARGET_STEADY_LATE_GAP_MS = 72L;
    private static final long POST_RELEASE_FRAME_LOCAL_ONSET_MICRO_GAP_MAX_MS = 24L;
    private static final long POST_RELEASE_NEAR_TARGET_CONTINUITY_WINDOW_MS = 160L;
    private static final long POST_RELEASE_NEAR_TARGET_CONTINUITY_SHORT_TONE_MAX_MS = 128L;
    private static final double POST_RELEASE_NEAR_TARGET_CONTINUITY_ATTACK_SLACK_RATIO = 0.72d;
    private static final long POST_RELEASE_WEAK_ONSET_CONTINUITY_MAX_GAP_MS =
            POST_RELEASE_NEAR_TARGET_CONTINUITY_SHORT_TONE_MAX_MS;
    private static final int POST_RELEASE_WEAK_ONSET_CONTINUITY_BASE_MAX_RESCUES = 2;
    // A trusted weak-onset chain may legitimately need one more reopen before
    // the next onset is strong enough to clear the normal attack path.
    private static final int POST_RELEASE_WEAK_ONSET_TRUSTED_CHAIN_EXTRA_RESCUES = 1;
    private static final double POST_RELEASE_WEAK_ONSET_CHAIN_MIN_THRESHOLD_RATIO = 0.07d;
    private static final double POST_RELEASE_WEAK_ONSET_CHAIN_MIN_DOMINANCE = 0.18d;
    private static final double POST_RELEASE_WEAK_ONSET_CHAIN_MIN_LOCAL_CONTRAST = 0.36d;
    private static final int POST_RELEASE_WEAK_ONSET_TRUSTED_CHAIN_MIN_FRAMES = 2;
    private static final int POST_RELEASE_WEAK_ONSET_TRUSTED_MAX_ANCHOR_DRIFT_HZ = 20;
    private static final double POST_RELEASE_WEAK_ONSET_TRUSTED_MIN_TONE_RMS_RATIO = 0.90d;
    private static final double POST_RELEASE_WEAK_ONSET_TRUSTED_MIN_DOMINANCE = 0.54d;
    private static final double POST_RELEASE_WEAK_ONSET_TRUSTED_MIN_ISOLATION = 0.38d;
    private static final double POST_RELEASE_WEAK_ONSET_TRUSTED_MIN_LOCAL_CONTRAST = 0.76d;
    private static final double POST_RELEASE_WEAK_ONSET_TRUSTED_MIN_LOCKED_RATIO = 0.48d;
    private static final double POST_RELEASE_WEAK_ONSET_TRUSTED_MIN_NEAR_TARGET_RATIO = 0.78d;
    private static final double POST_RELEASE_EARLY_TRUSTED_CONTINUITY_MIN_RELEASE_RATIO = 1.20d;
    private static final double TRUSTED_CONTINUITY_TONE_ON_MIN_TONE_RATIO = 0.90d;
    private static final double TRUSTED_CONTINUITY_TONE_ON_MIN_DOMINANCE_RATIO = 0.10d;
    private static final double TRUSTED_CONTINUITY_TONE_ON_MIN_ISOLATION_RATIO = 0.22d;
    private static final double TRUSTED_CONTINUITY_TONE_ON_MIN_LOCAL_CONTRAST_RATIO = 0.40d;
    private static final int FAR_ATTACK_CONFIRM_DRIFT_HZ = 60;
    private static final int FAR_ATTACK_CONFIRM_REQUIRED_FRAMES = 2;
    private static final double FAR_ATTACK_CONFIRM_REFERENCE_TONE_RATIO = 1.45d;
    private static final double TRACKED_MEMORY_FAR_ATTACK_MIN_GAP_TONE_RATIO = 2.0d;
    private static final int TRUSTED_CONTINUITY_ANCHOR_MIN_DRIFT_HZ = 20;
    private static final int EDGE_FREE_FAR_CARRIER_BLOCK_MIN_DRIFT_HZ = 30;
    private static final int LOW_EDGE_HIJACK_SUPPRESSION_MIN_DRIFT_HZ = 140;
    private static final int LOW_EDGE_BAND_MIN_HZ = 450;
    private static final int LOW_EDGE_BAND_MAX_HZ = 470;
    private static final int TARGETISH_BAND_MIN_HZ = 650;
    private static final int TARGETISH_BAND_MAX_HZ = 700;

    private enum TrackingState {
        SEARCH,
        CANDIDATE,
        LOCKED
    }

    private enum AcquisitionWinnerSource {
        NONE,
        PREFERRED_WINDOW,
        WIDE_SCAN,
        LOCKED_RETUNE,
        HYPOTHESIS_GUARD,
        SEARCH_FALLBACK
    }

    public enum RxToneMode {
        FIXED_TONE,
        AUTO_TRACK
    }

    public static final class ExperimentalLockedRetuneGuardTuning {
        private final int minDriftHz;
        private final int nearFrequencyMinDriftHz;
        private final int farDriftHz;
        private final int requiredScans;
        private final int farRequiredScans;
        private final int weakReferenceExtraScans;

        public ExperimentalLockedRetuneGuardTuning(
                int minDriftHz,
                int nearFrequencyMinDriftHz,
                int farDriftHz,
                int requiredScans,
                int farRequiredScans,
                int weakReferenceExtraScans
        ) {
            int safeMinDriftHz = Math.max(1, minDriftHz);
            int safeNearFrequencyMinDriftHz = Math.max(1, nearFrequencyMinDriftHz);
            int safeFarDriftHz = Math.max(
                    Math.max(safeMinDriftHz, safeNearFrequencyMinDriftHz) + 1,
                    farDriftHz
            );
            this.minDriftHz = safeMinDriftHz;
            this.nearFrequencyMinDriftHz = safeNearFrequencyMinDriftHz;
            this.farDriftHz = safeFarDriftHz;
            this.requiredScans = Math.max(1, requiredScans);
            this.farRequiredScans = Math.max(this.requiredScans, farRequiredScans);
            this.weakReferenceExtraScans = Math.max(0, weakReferenceExtraScans);
        }

        int minDriftHz() {
            return minDriftHz;
        }

        int nearFrequencyMinDriftHz() {
            return nearFrequencyMinDriftHz;
        }

        int farDriftHz() {
            return farDriftHz;
        }

        int requiredScans() {
            return requiredScans;
        }

        int farRequiredScans() {
            return farRequiredScans;
        }

        int weakReferenceExtraScans() {
            return weakReferenceExtraScans;
        }
    }

    private boolean initialized;
    private boolean experimentalForceWideAcquisitionEnabled;
    private ExperimentalLockedRetuneGuardTuning experimentalLockedRetuneGuardTuning;
    private boolean toneActive;
    private boolean targetToneLocked;
    private double noiseFloorEstimate;
    private double signalFloorEstimate;
    private int sqlPercent = DEFAULT_SQL_PERCENT;
    private int fixedToneLearningWindowHz = DEFAULT_FIXED_TONE_LEARNING_WINDOW_HZ;
    private double lastRmsAmplitude;
    private double lastToneRmsAmplitude;
    private double lastWidebandResidualRmsAmplitude;
    private double toneDominanceRatio;
    private double narrowbandIsolationRatio;
    private double peakToneRmsAmplitude;
    private double peakNarrowbandIsolationRatio;
    private double lastDetectionLevel;
    private double lockedBranchReferenceToneRmsAmplitude;
    private double representativeLockedToneScore;
    private double currentRepresentativeLockedToneScore;
    private double toneHypothesisConfidence;
    private double toneHypothesisTotalEvidence;
    private long lastFrameTimestampMs = -1L;
    private long lastTrackedToneTimestampMs = -1L;
    private long toneStartedAtMs = -1L;
    private long silenceStartedAtMs = -1L;
    private long postReleaseRescueContinuationWindowUntilMs = -1L;
    private TrackingState trackingState = TrackingState.SEARCH;
    private int totalToneOnEvents;
    private int totalToneOffEvents;
    private int preferredToneFrequencyHz = DEFAULT_PREFERRED_TONE_FREQUENCY_HZ;
    private int targetToneFrequencyHz = DEFAULT_PREFERRED_TONE_FREQUENCY_HZ;
    private int pendingRetuneCandidateFrequencyHz = DEFAULT_PREFERRED_TONE_FREQUENCY_HZ;
    private int pendingLockedRetuneFrequencyHz = DEFAULT_PREFERRED_TONE_FREQUENCY_HZ;
    private int processedFrameCount;
    private int pendingRetuneCandidateStableScans;
    private int pendingLockedRetuneStableScans;
    private int pendingFarAttackCandidateFrequencyHz;
    private int pendingFarAttackCandidateStableFrames;
    private int autoTrackWeakValleyBridgeFramesRemaining;
    private int autoTrackReleaseTailHoldFramesRemaining;
    private int currentToneRunWeakBootstrapReleaseTailHoldCount;
    private boolean autoTrackWeakValleyBridgeActive;
    private boolean autoTrackReleaseTailHoldActive;
    private long autoTrackReleaseTailHoldExtendedUntilMs = -1L;
    private long postReleaseWeakOnsetChainStartMs = -1L;
    private long postReleaseTrustedWeakOnsetChainStartMs = -1L;
    private int postReleaseTrustedWeakOnsetChainFrameCount;
    private int postReleaseWeakContinuityRescueCount;
    private int lockedRetuneGuardCandidateFrequencyHz;
    private int lockedRetuneGuardDriftHz;
    private int lockedRetuneGuardObservedScans;
    private int lockedRetuneGuardRequiredScans;
    private int lockedRetuneGuardRemainingScans;
    private int lockLossFrames;
    private int lockedFrameCount;
    private int toneActiveFrameCount;
    private int toneActiveUnlockedFrameCount;
    private int consecutiveLockedFrames;
    private int maxConsecutiveLockedFrames;
    private int consecutiveToneActiveUnlockedFrames;
    private int maxConsecutiveToneActiveUnlockedFrames;
    private int recentHistoryFrameCount;
    private int recentHistoryNextIndex;
    private int frameGapResetCount;
    private int representativeLockedToneFrequencyHz = DEFAULT_PREFERRED_TONE_FREQUENCY_HZ;
    private int representativeLockedToneFrameCount;
    private int currentRepresentativeLockedToneFrequencyHz = DEFAULT_PREFERRED_TONE_FREQUENCY_HZ;
    private int currentRepresentativeLockedToneFrameCount;
    private int toneHypothesisFrequencyHz = DEFAULT_PREFERRED_TONE_FREQUENCY_HZ;
    private int toneHypothesisSupportFrames;
    private int toneHypothesisIdleFrames;
    private int pendingHypothesisGuardFrequencyHz;
    private int totalHypothesisGuardApplyCount;
    private int lastHypothesisGuardAppliedFrequencyHz;
    private int lastPreferredWindowWinnerFrequencyHz = DEFAULT_PREFERRED_TONE_FREQUENCY_HZ;
    private int lastWideScanWinnerFrequencyHz;
    private int lastAcquisitionWinnerFrequencyHz = DEFAULT_PREFERRED_TONE_FREQUENCY_HZ;
    private int lastFinalAdoptedFrequencyHz = DEFAULT_PREFERRED_TONE_FREQUENCY_HZ;
    private long lastFrameGapMs;
    private long lastFrameGapResetThresholdMs;
    private long worstFrameGapMs;
    private long lastFrameGapResetAtMs = -1L;
    private double lastPreferredWindowWinnerToneRms;
    private double lastWideScanWinnerToneRms;
    private double lastAcquisitionWinnerToneRms;
    private double lastFinalAdoptedToneRms;
    private double lastPreferredWindowWinnerSelectionScore;
    private double lastWideScanWinnerSelectionScore;
    private double lastAcquisitionWinnerSelectionScore;
    private double lastFinalAdoptedSelectionScore;
    private double lastPreferredWindowWinnerConfidence;
    private double lastWideScanWinnerConfidence;
    private double lastAcquisitionWinnerConfidence;
    private double lastFinalAdoptedConfidence;
    private double lastPreviousTargetToneRms;
    private double lastPreviousTargetSelectionScore;
    private int lastPreferredWindowRunnerUpFrequencyHz;
    private int lastWideScanRunnerUpFrequencyHz;
    private int lastAcquisitionRunnerUpFrequencyHz;
    private int lastPreviousTargetFrequencyHz = DEFAULT_PREFERRED_TONE_FREQUENCY_HZ;
    private double lastPreferredWindowRunnerUpSelectionScore;
    private double lastWideScanRunnerUpSelectionScore;
    private double lastAcquisitionRunnerUpSelectionScore;
    private boolean lastPreferredWindowWinnerLocked;
    private boolean lastWideScanWinnerLocked;
    private boolean lastAcquisitionWinnerLocked;
    private boolean lastFinalAdoptedLocked;
    private boolean lastPreviousTargetLocked;
    private AcquisitionWinnerSource lastAcquisitionWinnerSource = AcquisitionWinnerSource.NONE;
    private AcquisitionWinnerSource lastFinalAdoptedSource = AcquisitionWinnerSource.NONE;
    private String lastAcquisitionDecisionDetail = "NONE";
    private String lastFinalAdoptionDetail = "NONE";
    private String lastPreferredWindowTopCandidatesSummary = "NONE";
    private String lastWideScanTopCandidatesSummary = "NONE";
    private String toneHypothesisSource = "NONE";
    private String lastHypothesisGuardDecision = "DISABLED";
    private final char[] recentFrontEndStateHistory = new char[RECENT_HISTORY_WINDOW_FRAMES];
    private final int[] recentTrackingOffsetHistoryHz = new int[RECENT_HISTORY_WINDOW_FRAMES];
    private final double[] toneHypothesisPosterior = new double[TONE_BUCKET_COUNT];
    private final int[] recentHypothesisFrequencyHistoryHz = new int[HYPOTHESIS_GUARD_HISTORY_WINDOW_FRAMES];
    private final int[] activePreferredWinnerHistogram = new int[TONE_BUCKET_COUNT];
    private final int[] activeWideWinnerHistogram = new int[TONE_BUCKET_COUNT];
    private final int[] activeAcquisitionWinnerHistogram = new int[TONE_BUCKET_COUNT];
    private final int[] activeLiveTargetHistogram = new int[TONE_BUCKET_COUNT];
    private final int[] activeHypothesisHistogram = new int[TONE_BUCKET_COUNT];
    private int activeWindowObservationCount;
    private int activePreferredWinnerObservationCount;
    private int activeWideWinnerObservationCount;
    private int activeAcquisitionWinnerObservationCount;
    private int activeLiveTargetObservationCount;
    private int activeHypothesisObservationCount;
    private int representativeCompetitionObservationCount;
    private int representativeCompetitionTrackedWinFrames;
    private int representativeCompetitionHypothesisWinFrames;
    private int representativeCompetitionTieFrames;
    private int representativeCompetitionHypothesisCurrentWinStreak;
    private int representativeCompetitionHypothesisMaxWinStreak;
    private int activeCenterCompetitionObservationCount;
    private int activeCenterCompetitionTrackedWinFrames;
    private int activeCenterCompetitionHypothesisWinFrames;
    private int activeCenterCompetitionTieFrames;
    private int activeCenterCompetitionHypothesisCurrentWinStreak;
    private int activeCenterCompetitionHypothesisMaxWinStreak;
    private int recentHypothesisHistoryCount;
    private int recentHypothesisHistoryNextIndex;
    private boolean experimentalHypothesisGuardEnabled;
    private boolean pendingHypothesisGuardEligible;
    private boolean lastHypothesisGuardEligible;
    private boolean lastHypothesisGuardApplied;
    private boolean hypothesisGuardAppliedThisFrame;
    private boolean hypothesisGuardOverrideAppliedThisFrame;
    private boolean lockedRetuneGuardHoldingThisFrame;
    private RxToneMode rxToneMode = RxToneMode.AUTO_TRACK;
    private CwToneEvent lastEvent;
    private ToneFrequencyEstimate hypothesisGuardOverrideRunnerUpEstimate;
    private double hypothesisGuardOverrideConfidence;
    private String lockedRetuneGuardBand = "NONE";
    private boolean lastAttackQualified;
    private boolean lastTrackedToneMemoryActiveBeforeFrame;
    private int lastAttackAnchorFrequencyHzBeforeFrame;
    private int lastToneOnThreshold;
    private long lastFrameLocalToneOnTimestampMs = -1L;
    private long lastPostReleaseGapMs = -1L;
    private long lastPostReleaseWindowMs;
    private double lastLocalContrastRatio;
    private boolean lastWeakPostReleaseOnsetChainCandidate;
    private boolean lastTrustedContinuityToneOnCandidate;
    private boolean lastSteadyLateGapNearTargetRescueCandidate;
    private boolean lastLowGrowthStrongSteadyNearTargetRescue;
    private boolean lastNearTargetPostReleaseToneOnRescue;
    private boolean lastPostReleaseSteadyCarrierSuppressed;
    private boolean lastFarAttackToneOnDelayed;
    private boolean lastToneOnAccepted;
    private boolean lastToneOnAcceptedByRescue;
    private boolean lastReleaseTailHoldApplied;
    private int lastToneActiveReleaseThreshold;
    private double lastReleaseTailHoldRequiredDetectionThreshold;
    private boolean lastReleaseTailHoldSufficientRecentTrust;
    private boolean lastReleaseTailHoldCurrentRunStableBootstrapEligible;
    private boolean lastReleaseTailHoldCurrentRunWeakBootstrapEligible;
    private boolean currentToneStartedByPostReleaseRescue;
    private boolean currentToneStartedWithoutTrackedMemory;
    private String lastPostReleaseRescueDecision = "NONE";
    private String lastPostReleaseSuppressionDecision = "NONE";
    private String lastFarAttackDelayDecision = "NONE";
    private String lastToneOnDecision = "NONE";
    private String lastReleaseTailHoldDecision = "NONE";

    private static final class ToneOnAdmissionObservation {
        private final boolean attackQualified;
        private final boolean trustedContinuityToneOnCandidate;
        private final boolean weakPostReleaseOnsetChainCandidate;
        private final boolean steadyLateGapNearTargetRescueCandidate;
        private final boolean lowGrowthStrongSteadyNearTargetRescue;
        private final long frameLocalToneOnTimestampMs;
        private final PostReleaseContinuityObservation continuityObservation;
        private final PostReleaseContinuityDebtContext postReleaseDebtContext;

        private ToneOnAdmissionObservation(
                boolean attackQualified,
                boolean trustedContinuityToneOnCandidate,
                boolean weakPostReleaseOnsetChainCandidate,
                boolean steadyLateGapNearTargetRescueCandidate,
                boolean lowGrowthStrongSteadyNearTargetRescue,
                long frameLocalToneOnTimestampMs,
                PostReleaseContinuityObservation continuityObservation,
                PostReleaseContinuityDebtContext postReleaseDebtContext
        ) {
            this.attackQualified = attackQualified;
            this.trustedContinuityToneOnCandidate = trustedContinuityToneOnCandidate;
            this.weakPostReleaseOnsetChainCandidate = weakPostReleaseOnsetChainCandidate;
            this.steadyLateGapNearTargetRescueCandidate = steadyLateGapNearTargetRescueCandidate;
            this.lowGrowthStrongSteadyNearTargetRescue = lowGrowthStrongSteadyNearTargetRescue;
            this.frameLocalToneOnTimestampMs = frameLocalToneOnTimestampMs;
            this.continuityObservation = continuityObservation;
            this.postReleaseDebtContext = postReleaseDebtContext;
        }
    }

    private static final class ToneOnAdmissionContext {
        private final boolean attackQualified;
        private final boolean trustedContinuityToneOnCandidate;
        private final boolean weakPostReleaseOnsetRescueCandidate;
        private final boolean nearTargetPostReleaseToneOnRescue;
        private final boolean steadyLateGapNearTargetRescueCandidate;
        private final boolean lowGrowthStrongSteadyNearTargetRescue;
        private final boolean exhaustedPostReleaseContinuityAttackCandidate;
        private final boolean weakContinuityCooldownAttackCandidate;
        private final long frameLocalToneOnTimestampMs;

        private ToneOnAdmissionContext(
                boolean attackQualified,
                boolean trustedContinuityToneOnCandidate,
                boolean weakPostReleaseOnsetRescueCandidate,
                boolean nearTargetPostReleaseToneOnRescue,
                boolean steadyLateGapNearTargetRescueCandidate,
                boolean lowGrowthStrongSteadyNearTargetRescue,
                boolean exhaustedPostReleaseContinuityAttackCandidate,
                boolean weakContinuityCooldownAttackCandidate,
                long frameLocalToneOnTimestampMs
        ) {
            this.attackQualified = attackQualified;
            this.trustedContinuityToneOnCandidate = trustedContinuityToneOnCandidate;
            this.weakPostReleaseOnsetRescueCandidate = weakPostReleaseOnsetRescueCandidate;
            this.nearTargetPostReleaseToneOnRescue = nearTargetPostReleaseToneOnRescue;
            this.steadyLateGapNearTargetRescueCandidate = steadyLateGapNearTargetRescueCandidate;
            this.lowGrowthStrongSteadyNearTargetRescue = lowGrowthStrongSteadyNearTargetRescue;
            this.exhaustedPostReleaseContinuityAttackCandidate = exhaustedPostReleaseContinuityAttackCandidate;
            this.weakContinuityCooldownAttackCandidate = weakContinuityCooldownAttackCandidate;
            this.frameLocalToneOnTimestampMs = frameLocalToneOnTimestampMs;
        }

        private boolean hasToneOnCandidate() {
            return attackQualified
                    || weakPostReleaseOnsetRescueCandidate
                    || trustedContinuityToneOnCandidate;
        }

        private boolean shouldAttemptToneOn(double detectionLevel, int attackThreshold) {
            return hasToneOnCandidate()
                    && (detectionLevel >= attackThreshold || nearTargetPostReleaseToneOnRescue);
        }

        private boolean shouldClearPendingFarAttackCandidate(
                boolean toneActive,
                double detectionLevel,
                int attackThreshold
        ) {
            return toneActive
                    || (!attackQualified && !trustedContinuityToneOnCandidate)
                    || detectionLevel < attackThreshold;
        }

        private boolean blockedByAttackQualification() {
            return !attackQualified
                    && !weakPostReleaseOnsetRescueCandidate
                    && !trustedContinuityToneOnCandidate;
        }
    }

    private enum ToneOnAttemptResolutionType {
        PROCEED,
        BLOCKED_POST_RELEASE_STEADY_SUPPRESSION,
        BLOCKED_WEAK_CHAIN_FALLBACK_ATTACK,
        BLOCKED_MICRO_GAP_TONE_ON
    }

    private static final class ToneOnAttemptResolution {
        private final ToneOnAttemptResolutionType type;
        private final long toneOnTimestampMs;

        private ToneOnAttemptResolution(
                ToneOnAttemptResolutionType type,
                long toneOnTimestampMs
        ) {
            this.type = type;
            this.toneOnTimestampMs = toneOnTimestampMs;
        }

        private boolean blocked() {
            return type != ToneOnAttemptResolutionType.PROCEED;
        }
    }

    private enum ToneOnAttemptFrontGateResolutionType {
        PROCEED,
        BLOCKED_CONTINUITY_CHAIN_EXHAUSTED,
        BLOCKED_FAR_ATTACK_CONFIRM,
        BLOCKED_TRUSTED_FAR_CARRIER,
        BLOCKED_EDGE_FREE_FAR_CARRIER
    }

    private static final class ToneOnAttemptFrontGateResolution {
        private final ToneOnAttemptFrontGateResolutionType type;

        private ToneOnAttemptFrontGateResolution(ToneOnAttemptFrontGateResolutionType type) {
            this.type = type;
        }

        private boolean blocked() {
            return type != ToneOnAttemptFrontGateResolutionType.PROCEED;
        }
    }

    private enum ToneOnAttemptFrameSideEffectMode {
        LIVE_OBSERVABLE,
        REPLAY_ONLY
    }

    private enum ToneOnAcceptedFrameSideEffectMode {
        LIVE_OBSERVABLE,
        REPLAY_ONLY
    }

    private enum FrameBookkeepingMode {
        REPLAY_ONLY,
        LIVE_OBSERVABLE_FALSE,
        LIVE_OBSERVABLE_TRUE
    }

    private enum ToneActiveReleaseFrameSideEffectMode {
        LIVE_OBSERVABLE,
        REPLAY_ONLY
    }

    private enum ToneActiveReleaseResolutionType {
        RESET_RELEASE_ATTEMPT,
        CONTINUE_WEAK_VALLEY_BRIDGE,
        CONTINUE_RELEASE_TAIL_HOLD,
        WAIT_FOR_TONE_OFF_HANG,
        EMIT_TONE_OFF
    }

    private static final class ToneActiveReleaseContext {
        private final long forcedFrameLocalToneOffTimestampMs;
        private final int toneActiveReleaseThreshold;

        private ToneActiveReleaseContext(
                long forcedFrameLocalToneOffTimestampMs,
                int toneActiveReleaseThreshold
        ) {
            this.forcedFrameLocalToneOffTimestampMs = forcedFrameLocalToneOffTimestampMs;
            this.toneActiveReleaseThreshold = toneActiveReleaseThreshold;
        }

        private boolean shouldEvaluateRelease(double detectionLevel) {
            return forcedFrameLocalToneOffTimestampMs >= 0L
                    || detectionLevel < toneActiveReleaseThreshold;
        }
    }

    private static final class ToneActiveReleaseResolution {
        private final ToneActiveReleaseResolutionType type;
        private final long releaseTailHoldExtendedUntilMs;
        private final ToneOffAcceptedEventContext toneOffAcceptedEventContext;

        private ToneActiveReleaseResolution(
                ToneActiveReleaseResolutionType type,
                long releaseTailHoldExtendedUntilMs,
                ToneOffAcceptedEventContext toneOffAcceptedEventContext
        ) {
            this.type = type;
            this.releaseTailHoldExtendedUntilMs = releaseTailHoldExtendedUntilMs;
            this.toneOffAcceptedEventContext = toneOffAcceptedEventContext;
        }

        private boolean consumesCurrentFrame() {
            return type == ToneActiveReleaseResolutionType.CONTINUE_WEAK_VALLEY_BRIDGE
                    || type == ToneActiveReleaseResolutionType.CONTINUE_RELEASE_TAIL_HOLD;
        }
    }

    private static final class ReleaseTailHoldEligibilityContext {
        private final double recentLockedFrameRatio;
        private final boolean sufficientRecentTrust;
        private final boolean currentRunStableBootstrapEligible;
        private final boolean currentRunWeakBootstrapEligible;
        private final boolean rescuedToneNeedsStrongerTailEvidence;
        private final boolean rescueBootstrapWindowActive;

        private ReleaseTailHoldEligibilityContext(
                double recentLockedFrameRatio,
                boolean sufficientRecentTrust,
                boolean currentRunStableBootstrapEligible,
                boolean currentRunWeakBootstrapEligible,
                boolean rescuedToneNeedsStrongerTailEvidence,
                boolean rescueBootstrapWindowActive
        ) {
            this.recentLockedFrameRatio = recentLockedFrameRatio;
            this.sufficientRecentTrust = sufficientRecentTrust;
            this.currentRunStableBootstrapEligible = currentRunStableBootstrapEligible;
            this.currentRunWeakBootstrapEligible = currentRunWeakBootstrapEligible;
            this.rescuedToneNeedsStrongerTailEvidence = rescuedToneNeedsStrongerTailEvidence;
            this.rescueBootstrapWindowActive = rescueBootstrapWindowActive;
        }
    }

    private static final class CurrentToneRunBootstrapContext {
        private final boolean rescuedToneActive;
        private final boolean currentRunActive;
        private final long currentToneDurationMs;
        private final boolean stableBootstrapEligible;
        private final boolean weakBootstrapEligible;

        private CurrentToneRunBootstrapContext(
                boolean rescuedToneActive,
                boolean currentRunActive,
                long currentToneDurationMs,
                boolean stableBootstrapEligible,
                boolean weakBootstrapEligible
        ) {
            this.rescuedToneActive = rescuedToneActive;
            this.currentRunActive = currentRunActive;
            this.currentToneDurationMs = currentToneDurationMs;
            this.stableBootstrapEligible = stableBootstrapEligible;
            this.weakBootstrapEligible = weakBootstrapEligible;
        }
    }

    private static final class PostReleaseRescuedToneProgressContext {
        private final boolean rescuedToneActive;
        private final boolean rescueBootstrapWindowActive;
        private final boolean lockedNearContinuityAnchor;
        private final boolean sufficientRecentRescueTrust;
        private final boolean currentRunStableBootstrapEligible;
        private final boolean currentRunWeakBootstrapEligible;

        private PostReleaseRescuedToneProgressContext(
                boolean rescuedToneActive,
                boolean rescueBootstrapWindowActive,
                boolean lockedNearContinuityAnchor,
                boolean sufficientRecentRescueTrust,
                boolean currentRunStableBootstrapEligible,
                boolean currentRunWeakBootstrapEligible
        ) {
            this.rescuedToneActive = rescuedToneActive;
            this.rescueBootstrapWindowActive = rescueBootstrapWindowActive;
            this.lockedNearContinuityAnchor = lockedNearContinuityAnchor;
            this.sufficientRecentRescueTrust = sufficientRecentRescueTrust;
            this.currentRunStableBootstrapEligible = currentRunStableBootstrapEligible;
            this.currentRunWeakBootstrapEligible = currentRunWeakBootstrapEligible;
        }

        private boolean canGraduate() {
            return lockedNearContinuityAnchor
                    && sufficientRecentRescueTrust
                    && (currentRunStableBootstrapEligible || currentRunWeakBootstrapEligible);
        }
    }

    private static final class CurrentToneRunContinuityGuardContext {
        private final boolean openingRunNeedsLocalCadenceProof;
        private final boolean rescuedToneStillFragileForUnlockedWeakValley;
        private final boolean currentRunMatureForUnlockedWeakValley;

        private CurrentToneRunContinuityGuardContext(
                boolean openingRunNeedsLocalCadenceProof,
                boolean rescuedToneStillFragileForUnlockedWeakValley,
                boolean currentRunMatureForUnlockedWeakValley
        ) {
            this.openingRunNeedsLocalCadenceProof = openingRunNeedsLocalCadenceProof;
            this.rescuedToneStillFragileForUnlockedWeakValley =
                    rescuedToneStillFragileForUnlockedWeakValley;
            this.currentRunMatureForUnlockedWeakValley = currentRunMatureForUnlockedWeakValley;
        }
    }

    private static final class WeakPostReleaseOnsetChainState {
        private final long weakOnsetChainStartMs;
        private final long trustedWeakOnsetChainStartMs;
        private final int trustedWeakOnsetChainFrameCount;

        private WeakPostReleaseOnsetChainState(
                long weakOnsetChainStartMs,
                long trustedWeakOnsetChainStartMs,
                int trustedWeakOnsetChainFrameCount
        ) {
            this.weakOnsetChainStartMs = weakOnsetChainStartMs;
            this.trustedWeakOnsetChainStartMs = trustedWeakOnsetChainStartMs;
            this.trustedWeakOnsetChainFrameCount = trustedWeakOnsetChainFrameCount;
        }

        private boolean hasTrustedChain() {
            return trustedWeakOnsetChainStartMs >= 0L
                    && trustedWeakOnsetChainFrameCount >= POST_RELEASE_WEAK_ONSET_TRUSTED_CHAIN_MIN_FRAMES;
        }

        private long preferredChainStartMs() {
            return hasTrustedChain() ? trustedWeakOnsetChainStartMs : weakOnsetChainStartMs;
        }
    }

    private static final class WeakPostReleaseOnsetChainLifecycleContext {
        private final boolean continuationWindowActive;
        private final boolean candidate;
        private final boolean trustedFrame;
        private final boolean credibleWeakChainStart;
        private final long candidateTimestampMs;

        private WeakPostReleaseOnsetChainLifecycleContext(
                boolean continuationWindowActive,
                boolean candidate,
                boolean trustedFrame,
                boolean credibleWeakChainStart,
                long candidateTimestampMs
        ) {
            this.continuationWindowActive = continuationWindowActive;
            this.candidate = candidate;
            this.trustedFrame = trustedFrame;
            this.credibleWeakChainStart = credibleWeakChainStart;
            this.candidateTimestampMs = candidateTimestampMs;
        }
    }

    private static final class PostReleaseContinuityDebtState {
        private final long continuationWindowUntilMs;
        private final int weakContinuityRescueCount;
        private final WeakPostReleaseOnsetChainState weakOnsetChainState;

        private PostReleaseContinuityDebtState(
                long continuationWindowUntilMs,
                int weakContinuityRescueCount,
                WeakPostReleaseOnsetChainState weakOnsetChainState
        ) {
            this.continuationWindowUntilMs = continuationWindowUntilMs;
            this.weakContinuityRescueCount = weakContinuityRescueCount;
            this.weakOnsetChainState = weakOnsetChainState;
        }
    }

    private static final class PostReleaseContinuityObservation {
        private final boolean continuationWindowActive;
        private final int weakContinuityMaxRescues;
        private final boolean weakContinuityLimitReached;
        private final boolean weakContinuityCooldownActive;

        private PostReleaseContinuityObservation(
                boolean continuationWindowActive,
                int weakContinuityMaxRescues,
                boolean weakContinuityLimitReached,
                boolean weakContinuityCooldownActive
        ) {
            this.continuationWindowActive = continuationWindowActive;
            this.weakContinuityMaxRescues = weakContinuityMaxRescues;
            this.weakContinuityLimitReached = weakContinuityLimitReached;
            this.weakContinuityCooldownActive = weakContinuityCooldownActive;
        }
    }

    private static final class PostReleaseContinuityDebtContext {
        private final boolean continuationWindowActive;
        private final boolean weakPostReleaseOnsetChainCandidate;
        private final boolean weakPostReleaseOnsetRescueCandidate;
        private final boolean exhaustedPostReleaseContinuityAttackCandidate;
        private final boolean weakContinuityCooldownAttackCandidate;

        private PostReleaseContinuityDebtContext(
                boolean continuationWindowActive,
                boolean weakPostReleaseOnsetChainCandidate,
                boolean weakPostReleaseOnsetRescueCandidate,
                boolean exhaustedPostReleaseContinuityAttackCandidate,
                boolean weakContinuityCooldownAttackCandidate
        ) {
            this.continuationWindowActive = continuationWindowActive;
            this.weakPostReleaseOnsetChainCandidate = weakPostReleaseOnsetChainCandidate;
            this.weakPostReleaseOnsetRescueCandidate = weakPostReleaseOnsetRescueCandidate;
            this.exhaustedPostReleaseContinuityAttackCandidate = exhaustedPostReleaseContinuityAttackCandidate;
            this.weakContinuityCooldownAttackCandidate = weakContinuityCooldownAttackCandidate;
        }
    }

    private static final class PostReleaseToneOnRescueContext {
        private final long gapSinceLastToneOffMs;
        private final long reacquireWindowMs;
        private final boolean continuationWindowActive;
        private final boolean lowGrowthStrongSteadyNearTargetCandidate;
        private final boolean lateGapCandidate;
        private final double frameRmsGrowthRatio;
        private final double toneRmsGrowthRatio;
        private final int softenedThreshold;
        private final int continuityThreshold;
        private final int weakContinuityMaxRescues;
        private final boolean weakContinuityLimitReached;
        private final boolean strongGrowthContinuityOverflow;

        private PostReleaseToneOnRescueContext(
                long gapSinceLastToneOffMs,
                long reacquireWindowMs,
                boolean continuationWindowActive,
                boolean lowGrowthStrongSteadyNearTargetCandidate,
                boolean lateGapCandidate,
                double frameRmsGrowthRatio,
                double toneRmsGrowthRatio,
                int softenedThreshold,
                int continuityThreshold,
                int weakContinuityMaxRescues,
                boolean weakContinuityLimitReached,
                boolean strongGrowthContinuityOverflow
        ) {
            this.gapSinceLastToneOffMs = gapSinceLastToneOffMs;
            this.reacquireWindowMs = reacquireWindowMs;
            this.continuationWindowActive = continuationWindowActive;
            this.lowGrowthStrongSteadyNearTargetCandidate = lowGrowthStrongSteadyNearTargetCandidate;
            this.lateGapCandidate = lateGapCandidate;
            this.frameRmsGrowthRatio = frameRmsGrowthRatio;
            this.toneRmsGrowthRatio = toneRmsGrowthRatio;
            this.softenedThreshold = softenedThreshold;
            this.continuityThreshold = continuityThreshold;
            this.weakContinuityMaxRescues = weakContinuityMaxRescues;
            this.weakContinuityLimitReached = weakContinuityLimitReached;
            this.strongGrowthContinuityOverflow = strongGrowthContinuityOverflow;
        }
    }

    private static final class ToneOnAcceptedRunLifecycleContext {
        private final boolean acceptedByPostReleaseRescue;
        private final boolean startedWithoutTrackedMemory;
        private final long toneOnTimestampMs;
        private final double detectionLevel;
        private final boolean allowStrongRescueDebtReset;

        private ToneOnAcceptedRunLifecycleContext(
                boolean acceptedByPostReleaseRescue,
                boolean startedWithoutTrackedMemory,
                long toneOnTimestampMs,
                double detectionLevel,
                boolean allowStrongRescueDebtReset
        ) {
            this.acceptedByPostReleaseRescue = acceptedByPostReleaseRescue;
            this.startedWithoutTrackedMemory = startedWithoutTrackedMemory;
            this.toneOnTimestampMs = toneOnTimestampMs;
            this.detectionLevel = detectionLevel;
            this.allowStrongRescueDebtReset = allowStrongRescueDebtReset;
        }
    }

    private static final class ToneOffPostReleaseDebtContext {
        private final long toneEndedAtMs;
        private final boolean shouldCarryContinuationWindow;

        private ToneOffPostReleaseDebtContext(
                long toneEndedAtMs,
                boolean shouldCarryContinuationWindow
        ) {
            this.toneEndedAtMs = toneEndedAtMs;
            this.shouldCarryContinuationWindow = shouldCarryContinuationWindow;
        }
    }

    private static final class ToneOffAcceptedEventContext {
        private final long toneEndedAtMs;
        private final long durationMs;
        private final ToneOffPostReleaseDebtContext toneOffDebtContext;

        private ToneOffAcceptedEventContext(
                long toneEndedAtMs,
                long durationMs,
                ToneOffPostReleaseDebtContext toneOffDebtContext
        ) {
            this.toneEndedAtMs = toneEndedAtMs;
            this.durationMs = durationMs;
            this.toneOffDebtContext = toneOffDebtContext;
        }
    }

    public synchronized List<CwToneEvent> process(AudioFrame frame) {
        ArrayList<CwToneEvent> events = new ArrayList<>(1);
        long timestampMs = frame.capturedAtMs();
        handleFrameGapReset(frame, timestampMs, events);
        clearBootstrapDebugFrameState(timestampMs);
        double frameRms = frame.rmsAmplitude();
        double previousFrameRmsAmplitude = lastRmsAmplitude;
        double previousToneRmsAmplitude = lastToneRmsAmplitude;
        boolean hadToneHistoryBeforeFrame = totalToneOnEvents > 0 || totalToneOffEvents > 0;
        boolean hadTrackedToneMemoryBeforeFrame = hadToneHistoryBeforeFrame
                && isTrackedToneMemoryActive(timestampMs);
        int attackAnchorFrequencyHzBeforeFrame = hadToneHistoryBeforeFrame
                ? continuityAttackAnchorFrequencyHz(timestampMs)
                : preferredToneFrequencyHz;
        double attackReferenceToneRmsBeforeFrame = lockedBranchReferenceToneRmsAmplitude;
        ToneFrequencyEstimate toneEstimate = analyzeToneFrequency(frame);
        toneEstimate = maybePreferContinuityAnchorToneEstimateDuringTrustedToneActivity(
                frame,
                toneEstimate,
                hadTrackedToneMemoryBeforeFrame,
                attackAnchorFrequencyHzBeforeFrame
        );
        toneEstimate = maybePreferContinuityAnchorToneEstimateForToneOn(
                frame,
                toneEstimate,
                hadTrackedToneMemoryBeforeFrame,
                attackAnchorFrequencyHzBeforeFrame,
                timestampMs
        );
        double detectionLevel = effectiveDetectionLevel(frameRms, toneEstimate);
        double backgroundObservationLevel = backgroundObservationLevel(frameRms, toneEstimate);
        boolean attackQualified = isNarrowbandQualified(toneEstimate);
        lastTrackedToneMemoryActiveBeforeFrame = hadTrackedToneMemoryBeforeFrame;
        lastAttackAnchorFrequencyHzBeforeFrame = attackAnchorFrequencyHzBeforeFrame;
        lastAttackQualified = attackQualified;
        lastLocalContrastRatio = toneEstimate == null ? 0.0d : toneEstimate.localContrastRatio;
        int attackThreshold = currentThreshold();
        int releaseThreshold = currentReleaseThreshold();
        if (!initialized) {
            noiseFloorEstimate = backgroundObservationLevel;
            signalFloorEstimate = Math.max(backgroundObservationLevel, detectionLevel);
            attackThreshold = currentThreshold();
            releaseThreshold = currentReleaseThreshold();
            initialized = true;
        }

        boolean weakLockedResidueHoldingSignalFloor = isAutoTrackWeakValleyCandidate(
                toneEstimate,
                attackThreshold,
                timestampMs
        );
        if (!toneActive) {
            noiseFloorEstimate = smoothNoiseFloor(noiseFloorEstimate, backgroundObservationLevel);
            signalFloorEstimate = relaxSignalFloorDuringSilence(
                    signalFloorEstimate,
                    Math.max(noiseFloorEstimate, detectionLevel)
            );
        } else if (weakLockedResidueHoldingSignalFloor) {
            // Keep the tone-active floor anchored while we decide whether this is
            // only a short weak valley. Otherwise a same-tone weak residue can drag
            // releaseThreshold down underneath itself and prolong toneActive forever.
            signalFloorEstimate = Math.max(signalFloorEstimate, detectionLevel);
        } else {
            signalFloorEstimate = smoothSignalFloor(signalFloorEstimate, detectionLevel);
        }

        lastRmsAmplitude = frameRms;
        lastToneRmsAmplitude = toneEstimate.toneRmsAmplitude;
        toneDominanceRatio = toneEstimate.dominanceRatio;
        attackThreshold = currentThreshold();
        releaseThreshold = currentReleaseThreshold();
        ToneOnAdmissionContext toneOnContext = buildToneOnAdmissionContext(
                frame,
                toneEstimate,
                attackQualified,
                hadTrackedToneMemoryBeforeFrame,
                attackAnchorFrequencyHzBeforeFrame,
                detectionLevel,
                attackThreshold,
                releaseThreshold,
                frameRms,
                previousFrameRmsAmplitude,
                previousToneRmsAmplitude,
                timestampMs
        );

        if (toneOnContext.shouldClearPendingFarAttackCandidate(
                toneActive,
                detectionLevel,
                attackThreshold
        )) {
            clearPendingFarAttackCandidate();
        }

        rememberPreAttemptToneOnDecision(toneOnContext, toneActive, detectionLevel, attackThreshold);

        if (!toneActive && toneOnContext.shouldAttemptToneOn(detectionLevel, attackThreshold)) {
            ToneOnAttemptFrontGateResolution frontGateResolution =
                    buildToneOnAttemptFrontGateResolution(
                            toneOnContext,
                            toneEstimate,
                            hadToneHistoryBeforeFrame,
                            hadTrackedToneMemoryBeforeFrame,
                            attackAnchorFrequencyHzBeforeFrame,
                            attackReferenceToneRmsBeforeFrame,
                            timestampMs,
                            true
                    );
            if (consumeBlockedToneOnAttemptFrontGateResolution(
                    frontGateResolution,
                    ToneOnAttemptFrameSideEffectMode.LIVE_OBSERVABLE,
                    timestampMs,
                    detectionLevel
            )) {
                return events;
            }
            ToneOnAttemptResolution toneOnAttemptResolution = buildToneOnAttemptResolution(
                    toneOnContext,
                    toneEstimate,
                    frameRms,
                    previousFrameRmsAmplitude,
                    previousToneRmsAmplitude,
                    attackThreshold,
                    releaseThreshold,
                    detectionLevel,
                    timestampMs
            );
            if (consumeBlockedToneOnAttemptResolution(
                    toneOnAttemptResolution,
                    ToneOnAttemptFrameSideEffectMode.LIVE_OBSERVABLE,
                    timestampMs,
                    detectionLevel
            )) {
                return events;
            }
            consumeAcceptedToneOnAttempt(
                    toneOnContext,
                    toneOnAttemptResolution.toneOnTimestampMs,
                    hadTrackedToneMemoryBeforeFrame,
                    detectionLevel,
                    frame.peakAmplitude(),
                    toneEstimate,
                    timestampMs,
                    events,
                    true,
                    ToneOnAcceptedFrameSideEffectMode.LIVE_OBSERVABLE
            );
            return events;
        }

        if (toneActive) {
            ToneActiveReleaseContext toneActiveReleaseContext = buildToneActiveReleaseContext(
                    frame,
                    toneEstimate,
                    attackThreshold,
                    releaseThreshold,
                    timestampMs
            );
            if (handleToneActiveReleaseFrame(
                    frame,
                    toneEstimate,
                    toneActiveReleaseContext,
                    attackThreshold,
                    detectionLevel,
                    backgroundObservationLevel,
                    timestampMs,
                    events,
                    ToneActiveReleaseFrameSideEffectMode.LIVE_OBSERVABLE
            )) {
                return events;
            }
        }

        rememberNoEventToneOnFrame(
                toneActive || attackQualified,
                timestampMs,
                detectionLevel
        );
        return events;
    }

    private ToneOnAdmissionContext buildToneOnAdmissionContext(
            AudioFrame frame,
            ToneFrequencyEstimate toneEstimate,
            boolean attackQualified,
            boolean hadTrackedToneMemoryBeforeFrame,
            int attackAnchorFrequencyHzBeforeFrame,
            double detectionLevel,
            int attackThreshold,
            int releaseThreshold,
            double frameRms,
            double previousFrameRmsAmplitude,
            double previousToneRmsAmplitude,
            long timestampMs
    ) {
        ToneOnAdmissionObservation observation = observeToneOnAdmission(
                frame,
                toneEstimate,
                attackQualified,
                hadTrackedToneMemoryBeforeFrame,
                attackAnchorFrequencyHzBeforeFrame,
                detectionLevel,
                attackThreshold,
                releaseThreshold,
                frameRms,
                previousFrameRmsAmplitude,
                previousToneRmsAmplitude,
                timestampMs
        );
        boolean nearTargetPostReleaseToneOnRescue = decideNearTargetPostReleaseToneOnRescue(
                observation,
                toneEstimate,
                detectionLevel,
                attackThreshold,
                releaseThreshold,
                frameRms,
                previousFrameRmsAmplitude,
                previousToneRmsAmplitude,
                timestampMs
        );
        rememberToneOnAdmissionDecision(
                observation,
                nearTargetPostReleaseToneOnRescue,
                attackThreshold,
                releaseThreshold
        );
        return toneOnAdmissionContextFromObservation(
                observation,
                nearTargetPostReleaseToneOnRescue
        );
    }

    private ToneOnAdmissionObservation observeToneOnAdmission(
            AudioFrame frame,
            ToneFrequencyEstimate toneEstimate,
            boolean attackQualified,
            boolean hadTrackedToneMemoryBeforeFrame,
            int attackAnchorFrequencyHzBeforeFrame,
            double detectionLevel,
            int attackThreshold,
            int releaseThreshold,
            double frameRms,
            double previousFrameRmsAmplitude,
            double previousToneRmsAmplitude,
            long timestampMs
    ) {
        maybeGraduateCurrentPostReleaseRescuedTone(toneEstimate, timestampMs);
        boolean trustedContinuityToneOnCandidate = !toneActive
                && isTrustedContinuityToneOnCandidate(
                toneEstimate,
                detectionLevel,
                attackThreshold,
                hadTrackedToneMemoryBeforeFrame,
                attackAnchorFrequencyHzBeforeFrame,
                timestampMs
        );
        boolean weakPostReleaseOnsetChainCandidate = !toneActive
                && isWeakPostReleaseOnsetChainCandidate(
                toneEstimate,
                detectionLevel,
                releaseThreshold,
                timestampMs
        );
        long frameLocalToneOnTimestampMs = shouldEstimateToneOnFrameLocalTimestamp(
                attackQualified,
                weakPostReleaseOnsetChainCandidate,
                trustedContinuityToneOnCandidate
        )
                ? estimateFrameLocalTransitionTimestamp(frame, true)
                : -1L;
        updateWeakPostReleaseOnsetChain(
                weakPostReleaseOnsetChainCandidate,
                toneEstimate,
                frameLocalToneOnTimestampMs,
                detectionLevel,
                releaseThreshold,
                timestampMs
        );
        boolean steadyLateGapNearTargetRescueCandidate = !toneActive
                && attackQualified
                && isSteadyNearTargetLateGapCandidate(
                toneEstimate,
                timestampMs
        );
        boolean lowGrowthStrongSteadyNearTargetRescue = !toneActive
                && attackQualified
                && isLowGrowthStrongSteadyNearTargetReacquireCandidate(
                toneEstimate,
                frameRms,
                previousFrameRmsAmplitude,
                previousToneRmsAmplitude,
                frameLocalToneOnTimestampMs,
                timestampMs
        );
        PostReleaseContinuityObservation continuityObservation = observePostReleaseContinuity(
                toneEstimate,
                timestampMs
        );
        PostReleaseContinuityDebtContext postReleaseDebtContext = buildPostReleaseContinuityDebtContext(
                weakPostReleaseOnsetChainCandidate,
                trustedContinuityToneOnCandidate,
                steadyLateGapNearTargetRescueCandidate,
                lowGrowthStrongSteadyNearTargetRescue,
                continuityObservation
        );
        ToneOnAdmissionObservation observation = new ToneOnAdmissionObservation(
                attackQualified,
                trustedContinuityToneOnCandidate,
                weakPostReleaseOnsetChainCandidate,
                steadyLateGapNearTargetRescueCandidate,
                lowGrowthStrongSteadyNearTargetRescue,
                frameLocalToneOnTimestampMs,
                continuityObservation,
                postReleaseDebtContext
        );
        rememberToneOnAdmissionObservation(observation);
        return observation;
    }

    private boolean shouldEstimateToneOnFrameLocalTimestamp(
            boolean attackQualified,
            boolean weakPostReleaseOnsetChainCandidate,
            boolean trustedContinuityToneOnCandidate
    ) {
        return !toneActive
                && (attackQualified
                || weakPostReleaseOnsetChainCandidate
                || trustedContinuityToneOnCandidate);
    }

    private void rememberToneOnAdmissionObservation(ToneOnAdmissionObservation observation) {
        lastWeakPostReleaseOnsetChainCandidate = observation.weakPostReleaseOnsetChainCandidate;
        lastTrustedContinuityToneOnCandidate = observation.trustedContinuityToneOnCandidate;
        lastFrameLocalToneOnTimestampMs = observation.frameLocalToneOnTimestampMs;
    }

    private boolean decideNearTargetPostReleaseToneOnRescue(
            ToneOnAdmissionObservation observation,
            ToneFrequencyEstimate toneEstimate,
            double detectionLevel,
            int attackThreshold,
            int releaseThreshold,
            double frameRms,
            double previousFrameRmsAmplitude,
            double previousToneRmsAmplitude,
            long timestampMs
    ) {
        return !toneActive
                && (observation.attackQualified
                || observation.postReleaseDebtContext.weakPostReleaseOnsetRescueCandidate
                || observation.trustedContinuityToneOnCandidate)
                && shouldAllowNearTargetPostReleaseToneOn(
                observation.postReleaseDebtContext,
                observation.continuityObservation,
                toneEstimate,
                detectionLevel,
                attackThreshold,
                releaseThreshold,
                observation.trustedContinuityToneOnCandidate,
                frameRms,
                previousFrameRmsAmplitude,
                previousToneRmsAmplitude,
                observation.frameLocalToneOnTimestampMs,
                timestampMs
        );
    }

    private void rememberToneOnAdmissionDecision(
            ToneOnAdmissionObservation observation,
            boolean nearTargetPostReleaseToneOnRescue,
            int attackThreshold,
            int releaseThreshold
    ) {
        lastSteadyLateGapNearTargetRescueCandidate =
                observation.steadyLateGapNearTargetRescueCandidate;
        lastLowGrowthStrongSteadyNearTargetRescue =
                observation.lowGrowthStrongSteadyNearTargetRescue;
        lastNearTargetPostReleaseToneOnRescue = nearTargetPostReleaseToneOnRescue;
        lastToneOnThreshold = nearTargetPostReleaseToneOnRescue
                ? (observation.postReleaseDebtContext.weakPostReleaseOnsetRescueCandidate
                ? weakPostReleaseRescueThreshold(releaseThreshold)
                : softenedPostReleaseNearTargetAttackThreshold(
                attackThreshold,
                releaseThreshold,
                observation.steadyLateGapNearTargetRescueCandidate
                        || observation.lowGrowthStrongSteadyNearTargetRescue
        ))
                : attackThreshold;
    }

    private ToneOnAdmissionContext toneOnAdmissionContextFromObservation(
            ToneOnAdmissionObservation observation,
            boolean nearTargetPostReleaseToneOnRescue
    ) {
        return new ToneOnAdmissionContext(
                observation.attackQualified,
                observation.trustedContinuityToneOnCandidate,
                observation.postReleaseDebtContext.weakPostReleaseOnsetRescueCandidate,
                nearTargetPostReleaseToneOnRescue,
                observation.steadyLateGapNearTargetRescueCandidate,
                observation.lowGrowthStrongSteadyNearTargetRescue,
                observation.postReleaseDebtContext.exhaustedPostReleaseContinuityAttackCandidate,
                observation.postReleaseDebtContext.weakContinuityCooldownAttackCandidate,
                observation.frameLocalToneOnTimestampMs
        );
    }

    private ToneActiveReleaseContext buildToneActiveReleaseContext(
            AudioFrame frame,
            ToneFrequencyEstimate toneEstimate,
            int attackThreshold,
            int releaseThreshold,
            long timestampMs
    ) {
        long forcedFrameLocalToneOffTimestampMs = fixedToneFrameLocalReleaseTimestamp(frame, toneEstimate);
        int toneActiveReleaseThreshold = effectiveToneActiveReleaseThreshold(
                toneEstimate,
                attackThreshold,
                releaseThreshold,
                timestampMs
        );
        lastToneActiveReleaseThreshold = toneActiveReleaseThreshold;
        return new ToneActiveReleaseContext(
                forcedFrameLocalToneOffTimestampMs,
                toneActiveReleaseThreshold
        );
    }

    private PostReleaseContinuityDebtContext buildPostReleaseContinuityDebtContext(
            boolean weakPostReleaseOnsetChainCandidate,
            boolean trustedContinuityToneOnCandidate,
            boolean steadyLateGapNearTargetRescueCandidate,
            boolean lowGrowthStrongSteadyNearTargetRescue,
            PostReleaseContinuityObservation continuityObservation
    ) {
        boolean weakPostReleaseOnsetRescueCandidate = !toneActive
                && weakPostReleaseOnsetChainCandidate
                && continuityObservation.continuationWindowActive;
        boolean exhaustedPostReleaseContinuityAttackCandidate = !toneActive
                && continuityObservation.continuationWindowActive
                && continuityObservation.weakContinuityLimitReached
                && !steadyLateGapNearTargetRescueCandidate
                && !lowGrowthStrongSteadyNearTargetRescue
                && !trustedContinuityToneOnCandidate;
        boolean weakContinuityCooldownAttackCandidate = !toneActive
                && continuityObservation.weakContinuityCooldownActive
                && !steadyLateGapNearTargetRescueCandidate
                && !lowGrowthStrongSteadyNearTargetRescue;
        return new PostReleaseContinuityDebtContext(
                continuityObservation.continuationWindowActive,
                weakPostReleaseOnsetChainCandidate,
                weakPostReleaseOnsetRescueCandidate,
                exhaustedPostReleaseContinuityAttackCandidate,
                weakContinuityCooldownAttackCandidate
        );
    }

    private PostReleaseContinuityObservation observePostReleaseContinuity(
            ToneFrequencyEstimate toneEstimate,
            long timestampMs
    ) {
        PostReleaseContinuityDebtState debtState = snapshotPostReleaseContinuityDebtState();
        boolean continuationWindowActive = !toneActive
                && isPostReleaseRescueContinuationWindowActive(debtState, timestampMs);
        boolean trustedWeakContinuityBoostActive = debtState.weakOnsetChainState.hasTrustedChain()
                || isTrustedWeakPostReleaseOnsetChainFrame(toneEstimate, timestampMs)
                || isStrongSteadyNearTargetReacquireCandidate(toneEstimate);
        int weakContinuityMaxRescues = POST_RELEASE_WEAK_ONSET_CONTINUITY_BASE_MAX_RESCUES
                + (trustedWeakContinuityBoostActive
                ? POST_RELEASE_WEAK_ONSET_TRUSTED_CHAIN_EXTRA_RESCUES
                : 0);
        boolean weakContinuityLimitReached = continuationWindowActive
                && debtState.weakContinuityRescueCount >= weakContinuityMaxRescues;
        boolean weakContinuityCooldownActive = !toneActive
                && debtState.weakContinuityRescueCount > 0
                && lastEvent != null
                && lastEvent.type() == CwToneEvent.Type.TONE_OFF
                && timestampMs >= lastEvent.timestampMs()
                && (timestampMs - lastEvent.timestampMs()) <= POST_RELEASE_WEAK_ONSET_CONTINUITY_MAX_GAP_MS;
        return new PostReleaseContinuityObservation(
                continuationWindowActive,
                weakContinuityMaxRescues,
                weakContinuityLimitReached,
                weakContinuityCooldownActive
        );
    }

    private PostReleaseToneOnRescueContext buildPostReleaseToneOnRescueContext(
            PostReleaseContinuityDebtContext postReleaseDebtContext,
            PostReleaseContinuityObservation continuityObservation,
            ToneFrequencyEstimate toneEstimate,
            int attackThreshold,
            int releaseThreshold,
            double frameRms,
            double previousFrameRmsAmplitude,
            double previousToneRmsAmplitude,
            long frameLocalToneOnTimestampMs,
            long timestampMs
    ) {
        long gapSinceLastToneOffMs = timestampMs - lastEvent.timestampMs();
        long reacquireWindowMs = postReleaseNearTargetReacquireWindowMs();
        if (postReleaseDebtContext.continuationWindowActive) {
            reacquireWindowMs = Math.max(
                    reacquireWindowMs,
                    Math.max(POST_RELEASE_NEAR_TARGET_REACQUIRE_WINDOW_MIN_MS, gapSinceLastToneOffMs)
            );
            lastPostReleaseWindowMs = Math.max(lastPostReleaseWindowMs, reacquireWindowMs);
        }
        boolean lowGrowthStrongSteadyNearTargetCandidate =
                isLowGrowthStrongSteadyNearTargetReacquireCandidate(
                        toneEstimate,
                        frameRms,
                        previousFrameRmsAmplitude,
                        previousToneRmsAmplitude,
                        frameLocalToneOnTimestampMs,
                        timestampMs
                );
        boolean lateGapCandidate = isSteadyNearTargetLateGapCandidate(
                toneEstimate,
                timestampMs
        );
        double frameRmsGrowthRatio = frameRms / Math.max(1.0d, previousFrameRmsAmplitude);
        double toneRmsGrowthRatio = toneEstimate.toneRmsAmplitude / Math.max(1.0d, previousToneRmsAmplitude);
        int softenedThreshold = softenedPostReleaseNearTargetAttackThreshold(
                attackThreshold,
                releaseThreshold,
                lowGrowthStrongSteadyNearTargetCandidate || lateGapCandidate
        );
        int continuityThreshold = continuityPostReleaseNearTargetAttackThreshold(
                attackThreshold,
                releaseThreshold
        );
        boolean strongGrowthContinuityOverflow = postReleaseDebtContext.weakPostReleaseOnsetRescueCandidate
                && continuityObservation.weakContinuityLimitReached
                && isStrongSteadyNearTargetReacquireCandidate(toneEstimate)
                && frameRmsGrowthRatio >= POST_RELEASE_ONSET_GUARD_MIN_FRAME_RMS_GROWTH_RATIO
                && toneRmsGrowthRatio >= POST_RELEASE_ONSET_GUARD_MIN_TONE_RMS_GROWTH_RATIO;
        return new PostReleaseToneOnRescueContext(
                gapSinceLastToneOffMs,
                reacquireWindowMs,
                postReleaseDebtContext.continuationWindowActive,
                lowGrowthStrongSteadyNearTargetCandidate,
                lateGapCandidate,
                frameRmsGrowthRatio,
                toneRmsGrowthRatio,
                softenedThreshold,
                continuityThreshold,
                continuityObservation.weakContinuityMaxRescues,
                continuityObservation.weakContinuityLimitReached,
                strongGrowthContinuityOverflow
        );
    }

    private boolean shouldBlockPostReleaseRescueByContinuityDebt(
            PostReleaseToneOnRescueContext rescueContext,
            PostReleaseContinuityDebtContext postReleaseDebtContext,
            boolean trustedContinuityToneOnCandidate
    ) {
        if (rescueContext.continuationWindowActive
                && rescueContext.weakContinuityLimitReached
                && !postReleaseDebtContext.weakPostReleaseOnsetRescueCandidate
                && !rescueContext.lowGrowthStrongSteadyNearTargetCandidate
                && !rescueContext.lateGapCandidate
                && !trustedContinuityToneOnCandidate) {
            lastPostReleaseRescueDecision = "BLOCKED:CONTINUITY_CHAIN_EXHAUSTED";
            return true;
        }
        if (postReleaseDebtContext.weakPostReleaseOnsetRescueCandidate
                && rescueContext.continuationWindowActive) {
            if (rescueContext.gapSinceLastToneOffMs > POST_RELEASE_WEAK_ONSET_CONTINUITY_MAX_GAP_MS) {
                lastPostReleaseRescueDecision = "BLOCKED:WEAK_GAP_BOUNDARY";
                return true;
            }
            if (rescueContext.weakContinuityLimitReached
                    && !rescueContext.strongGrowthContinuityOverflow) {
                lastPostReleaseRescueDecision = "BLOCKED:WEAK_CHAIN_LIMIT";
                return true;
            }
        }
        return false;
    }

    private int requiredDetectionThresholdForPostReleaseToneOn(
            PostReleaseToneOnRescueContext rescueContext,
            PostReleaseContinuityDebtContext postReleaseDebtContext,
            boolean trustedContinuityToneOnCandidate,
            int releaseThreshold
    ) {
        int requiredDetectionThreshold = rescueContext.continuationWindowActive
                ? Math.min(rescueContext.softenedThreshold, rescueContext.continuityThreshold)
                : rescueContext.softenedThreshold;
        if (trustedContinuityToneOnCandidate) {
            requiredDetectionThreshold = Math.min(
                    requiredDetectionThreshold,
                    rescueContext.continuityThreshold
            );
        }
        if (postReleaseDebtContext.weakPostReleaseOnsetRescueCandidate
                && rescueContext.continuationWindowActive
                && !trustedContinuityToneOnCandidate
                && !hasTrustedWeakPostReleaseOnsetChain()) {
            // Do not reopen on sub-release residue. A continuity rescue may relax the
            // full attack threshold, but it still needs to climb back to at least the
            // prior release line before we treat it as a new tone onset.
            requiredDetectionThreshold = Math.max(requiredDetectionThreshold, releaseThreshold);
        }
        if (trustedContinuityToneOnCandidate
                && rescueContext.gapSinceLastToneOffMs >= POST_RELEASE_FRAME_LOCAL_ONSET_MICRO_GAP_MAX_MS
                && rescueContext.gapSinceLastToneOffMs < POST_RELEASE_NEAR_TARGET_STEADY_LATE_GAP_MS
                && !rescueContext.lateGapCandidate
                && !rescueContext.lowGrowthStrongSteadyNearTargetCandidate) {
            requiredDetectionThreshold = Math.max(
                    requiredDetectionThreshold,
                    (int) Math.round(Math.max(1, releaseThreshold)
                            * POST_RELEASE_EARLY_TRUSTED_CONTINUITY_MIN_RELEASE_RATIO)
            );
        }
        return requiredDetectionThreshold;
    }

    private CurrentToneRunBootstrapContext buildCurrentToneRunBootstrapContext(
            ToneFrequencyEstimate toneEstimate,
            long timestampMs
    ) {
        boolean rescuedToneActive = currentToneStartedByPostReleaseRescue;
        if (toneEstimate == null
                || toneStartedAtMs < 0L
                || timestampMs < toneStartedAtMs) {
            return new CurrentToneRunBootstrapContext(
                    rescuedToneActive,
                    false,
                    0L,
                    false,
                    false
            );
        }
        long currentToneDurationMs = timestampMs - toneStartedAtMs;
        boolean stableLockStreakEligible =
                consecutiveLockedFrames >= AUTO_TRACK_RELEASE_TAIL_HOLD_CURRENT_RUN_MIN_STABLE_LOCK_FRAMES
                        || maxConsecutiveLockedFrames
                        >= AUTO_TRACK_RELEASE_TAIL_HOLD_CURRENT_RUN_MIN_STABLE_LOCK_FRAMES;
        boolean stableBootstrapEligible = toneEstimate.locked
                && currentToneDurationMs >= AUTO_TRACK_RELEASE_TAIL_HOLD_CURRENT_RUN_MIN_TONE_DURATION_MS
                && stableLockStreakEligible
                && toneEstimate.dominanceRatio >= AUTO_TRACK_RELEASE_TAIL_HOLD_CURRENT_RUN_MIN_DOMINANCE
                && (toneEstimate.isolationRatio >= AUTO_TRACK_RELEASE_TAIL_HOLD_CURRENT_RUN_MIN_ISOLATION
                || toneEstimate.localContrastRatio
                >= AUTO_TRACK_RELEASE_TAIL_HOLD_CURRENT_RUN_MIN_LOCAL_CONTRAST);
        boolean weakBootstrapFrameQualified = rescuedToneActive
                ? toneEstimate.locked
                : isNarrowbandQualified(toneEstimate);
        boolean weakBootstrapEligible = weakBootstrapFrameQualified
                && currentToneDurationMs >= AUTO_TRACK_RELEASE_TAIL_HOLD_WEAK_BOOTSTRAP_MIN_TONE_DURATION_MS
                && stableLockStreakEligible
                && toneEstimate.dominanceRatio >= AUTO_TRACK_RELEASE_TAIL_HOLD_WEAK_BOOTSTRAP_DOMINANCE_MIN
                && (toneEstimate.isolationRatio >= AUTO_TRACK_RELEASE_TAIL_HOLD_WEAK_BOOTSTRAP_ISOLATION_MIN
                || toneEstimate.localContrastRatio
                >= AUTO_TRACK_RELEASE_TAIL_HOLD_WEAK_BOOTSTRAP_LOCAL_CONTRAST_MIN);
        return new CurrentToneRunBootstrapContext(
                rescuedToneActive,
                true,
                currentToneDurationMs,
                stableBootstrapEligible,
                weakBootstrapEligible
        );
    }

    private PostReleaseRescuedToneProgressContext buildPostReleaseRescuedToneProgressContext(
            ToneFrequencyEstimate toneEstimate,
            CurrentToneRunBootstrapContext currentRunBootstrapContext,
            long timestampMs
    ) {
        boolean rescuedToneActive = currentRunBootstrapContext.rescuedToneActive;
        boolean rescueBootstrapWindowActive = rescuedToneActive
                && isPostReleaseRescueContinuationWindowActive(timestampMs);
        boolean lockedNearContinuityAnchor = rescuedToneActive
                && toneActive
                && toneEstimate != null
                && toneEstimate.locked
                && currentRunBootstrapContext.currentRunActive;
        if (lockedNearContinuityAnchor) {
            int continuityAnchorFrequencyHz = continuityAnchorFrequencyHz();
            lockedNearContinuityAnchor = continuityAnchorFrequencyHz > 0
                    && Math.abs(toneEstimate.frequencyHz - continuityAnchorFrequencyHz)
                    <= POST_RELEASE_NEAR_TARGET_MAX_ANCHOR_DRIFT_HZ;
        }
        boolean sufficientRecentRescueTrust = lockedNearContinuityAnchor
                && recentLockedFrameRatioFromHistory() >= POST_RELEASE_WEAK_ONSET_TRUSTED_MIN_LOCKED_RATIO;
        return new PostReleaseRescuedToneProgressContext(
                rescuedToneActive,
                rescueBootstrapWindowActive,
                lockedNearContinuityAnchor,
                sufficientRecentRescueTrust,
                currentRunBootstrapContext.stableBootstrapEligible,
                currentRunBootstrapContext.weakBootstrapEligible
        );
    }

    private CurrentToneRunContinuityGuardContext buildCurrentToneRunContinuityGuardContext(
            CurrentToneRunBootstrapContext currentRunBootstrapContext,
            PostReleaseRescuedToneProgressContext rescuedToneProgressContext
    ) {
        boolean currentRunMatureForUnlockedWeakValley =
                currentRunBootstrapContext.currentRunActive
                        && currentRunBootstrapContext.currentToneDurationMs
                        >= AUTO_TRACK_WEAK_VALLEY_RESCUE_CURRENT_RUN_MIN_TONE_DURATION_MS;
        return new CurrentToneRunContinuityGuardContext(
                currentToneStartedWithoutTrackedMemory,
                rescuedToneProgressContext.rescuedToneActive,
                currentRunMatureForUnlockedWeakValley
        );
    }

    private ReleaseTailHoldEligibilityContext buildReleaseTailHoldEligibilityContext(
            ToneFrequencyEstimate toneEstimate,
            boolean fixedToneMode,
            long timestampMs
    ) {
        double recentLockedFrameRatio = recentLockedFrameRatioFromHistory();
        boolean sufficientRecentTrust = recentLockedFrameRatio >= AUTO_TRACK_RELEASE_TAIL_HOLD_MIN_LOCKED_RATIO
                && recentNearTargetLockedFrameRatioFromHistory()
                >= AUTO_TRACK_RELEASE_TAIL_HOLD_MIN_NEAR_TARGET_RATIO;
        CurrentToneRunBootstrapContext currentRunBootstrapContext =
                buildCurrentToneRunBootstrapContext(toneEstimate, timestampMs);
        PostReleaseRescuedToneProgressContext rescuedToneProgressContext =
                buildPostReleaseRescuedToneProgressContext(
                        toneEstimate,
                        currentRunBootstrapContext,
                        timestampMs
                );
        boolean currentRunStableBootstrapEligible;
        if (fixedToneMode) {
            currentRunStableBootstrapEligible = currentRunBootstrapContext.currentRunActive
                    && toneEstimate != null
                    && toneEstimate.locked
                    && currentRunBootstrapContext.currentToneDurationMs
                    >= FIXED_TONE_RELEASE_TAIL_HOLD_MIN_TONE_DURATION_MS;
        } else {
            currentRunStableBootstrapEligible = currentRunBootstrapContext.stableBootstrapEligible;
        }
        boolean currentRunWeakBootstrapEligible = !fixedToneMode
                && currentRunBootstrapContext.weakBootstrapEligible;
        boolean rescueBootstrapWindowActive = rescuedToneProgressContext.rescueBootstrapWindowActive;
        boolean rescuedToneNeedsStrongerTailEvidence = rescueBootstrapWindowActive
                && !sufficientRecentTrust
                && !currentRunStableBootstrapEligible
                && !currentRunWeakBootstrapEligible;
        return new ReleaseTailHoldEligibilityContext(
                recentLockedFrameRatio,
                sufficientRecentTrust,
                currentRunStableBootstrapEligible,
                currentRunWeakBootstrapEligible,
                rescuedToneNeedsStrongerTailEvidence,
                rescueBootstrapWindowActive
        );
    }

    private void rememberReleaseTailHoldEligibilityContext(
            ReleaseTailHoldEligibilityContext releaseTailContext
    ) {
        lastReleaseTailHoldSufficientRecentTrust = releaseTailContext.sufficientRecentTrust;
        lastReleaseTailHoldCurrentRunStableBootstrapEligible =
                releaseTailContext.currentRunStableBootstrapEligible;
        lastReleaseTailHoldCurrentRunWeakBootstrapEligible =
                releaseTailContext.currentRunWeakBootstrapEligible;
    }

    private double requiredDetectionThresholdForReleaseTailHold(
            ReleaseTailHoldEligibilityContext releaseTailContext,
            int releaseThreshold
    ) {
        double requiredThresholdRatio = AUTO_TRACK_RELEASE_TAIL_HOLD_MIN_THRESHOLD_RATIO;
        if (!releaseTailContext.sufficientRecentTrust) {
            if (releaseTailContext.currentRunWeakBootstrapEligible
                    && !releaseTailContext.currentRunStableBootstrapEligible) {
                requiredThresholdRatio = releaseTailContext.recentLockedFrameRatio
                        >= AUTO_TRACK_RELEASE_TAIL_HOLD_OPENING_WEAK_BOOTSTRAP_MIN_LOCKED_RATIO
                        ? AUTO_TRACK_RELEASE_TAIL_HOLD_OPENING_WEAK_BOOTSTRAP_MIN_THRESHOLD_RATIO
                        : AUTO_TRACK_RELEASE_TAIL_HOLD_WEAK_BOOTSTRAP_MIN_THRESHOLD_RATIO;
            } else if (releaseTailContext.currentRunStableBootstrapEligible
                    || releaseTailContext.rescueBootstrapWindowActive) {
                requiredThresholdRatio = AUTO_TRACK_RELEASE_TAIL_HOLD_WEAK_BOOTSTRAP_MIN_THRESHOLD_RATIO;
            }
        }
        return Math.max(1.0d, Math.max(1, releaseThreshold) * requiredThresholdRatio);
    }

    private boolean handleToneActiveReleaseFrame(
            AudioFrame frame,
            ToneFrequencyEstimate toneEstimate,
            ToneActiveReleaseContext releaseContext,
            int attackThreshold,
            double detectionLevel,
            double backgroundObservationLevel,
            long timestampMs,
            List<CwToneEvent> events,
            ToneActiveReleaseFrameSideEffectMode frameSideEffectMode
    ) {
        ToneActiveReleaseResolution releaseResolution = buildToneActiveReleaseResolution(
                frame,
                toneEstimate,
                releaseContext,
                attackThreshold,
                detectionLevel,
                timestampMs
        );
        return consumeToneActiveReleaseResolution(
                releaseResolution,
                frame,
                detectionLevel,
                backgroundObservationLevel,
                timestampMs,
                events,
                frameSideEffectMode
        );
    }

    private ToneActiveReleaseResolution buildToneActiveReleaseResolution(
            AudioFrame frame,
            ToneFrequencyEstimate toneEstimate,
            ToneActiveReleaseContext releaseContext,
            int attackThreshold,
            double detectionLevel,
            long timestampMs
    ) {
        if (!releaseContext.shouldEvaluateRelease(detectionLevel)) {
            return resetToneActiveReleaseResolution();
        }
        if (shouldBridgeAutoTrackWeakValley(toneEstimate, attackThreshold, timestampMs)) {
            return continuedWeakValleyBridgeToneActiveReleaseResolution();
        }
        ensureToneActiveSilenceStarted(
                frame,
                releaseContext,
                detectionLevel,
                timestampMs
        );
        if (shouldHoldNearTargetReleaseTail(
                toneEstimate,
                detectionLevel,
                releaseContext.toneActiveReleaseThreshold,
                timestampMs
        )) {
            return continuedReleaseTailHoldToneActiveReleaseResolution(
                    timestampMs + estimateFrameDurationMs(frame)
            );
        }
        long frameEndTimestampMs = timestampMs + estimateFrameDurationMs(frame);
        if (!shouldEmitToneOffAfterActiveRelease(frameEndTimestampMs)) {
            return waitingForToneOffHangToneActiveReleaseResolution();
        }
        return emittedToneOffToneActiveReleaseResolution(buildToneOffAcceptedEventContext());
    }

    private boolean consumeToneActiveReleaseResolution(
            ToneActiveReleaseResolution releaseResolution,
            AudioFrame frame,
            double detectionLevel,
            double backgroundObservationLevel,
            long timestampMs,
            List<CwToneEvent> events,
            ToneActiveReleaseFrameSideEffectMode frameSideEffectMode
    ) {
        switch (releaseResolution.type) {
            case RESET_RELEASE_ATTEMPT:
                resetToneActiveReleaseAttempt();
                return false;
            case CONTINUE_WEAK_VALLEY_BRIDGE:
                silenceStartedAtMs = -1L;
                rememberToneActiveContinuationFrame(
                        timestampMs,
                        detectionLevel,
                        frameSideEffectMode
                );
                return true;
            case CONTINUE_RELEASE_TAIL_HOLD:
                // If this weak valley is accepted as a held tail, the tone should not
                // later close retroactively at the first weak frame.
                silenceStartedAtMs = -1L;
                autoTrackReleaseTailHoldExtendedUntilMs = Math.max(
                        autoTrackReleaseTailHoldExtendedUntilMs,
                        releaseResolution.releaseTailHoldExtendedUntilMs
                );
                lastReleaseTailHoldApplied = true;
                rememberToneActiveContinuationFrame(
                        timestampMs,
                        detectionLevel,
                        frameSideEffectMode
                );
                return true;
            case WAIT_FOR_TONE_OFF_HANG:
                return false;
            case EMIT_TONE_OFF:
                consumeAcceptedToneOffEvent(
                        releaseResolution.toneOffAcceptedEventContext,
                        frame.peakAmplitude(),
                        detectionLevel,
                        backgroundObservationLevel,
                        events
                );
                return false;
            default:
                return false;
        }
    }

    private void ensureToneActiveSilenceStarted(
            AudioFrame frame,
            ToneActiveReleaseContext releaseContext,
            double detectionLevel,
            long timestampMs
    ) {
        if (silenceStartedAtMs >= 0L) {
            return;
        }
        long frameLocalToneOffTimestampMs = releaseContext.forcedFrameLocalToneOffTimestampMs >= 0L
                ? releaseContext.forcedFrameLocalToneOffTimestampMs
                : estimateFrameLocalTransitionTimestamp(frame, false);
        if (frameLocalToneOffTimestampMs >= 0L) {
            silenceStartedAtMs = frameLocalToneOffTimestampMs;
            return;
        }
        silenceStartedAtMs = estimateCrossingTimestamp(
                timestampMs,
                releaseContext.toneActiveReleaseThreshold,
                detectionLevel,
                false
        );
    }

    private ToneOnAcceptedRunLifecycleContext buildToneOnAcceptedRunLifecycleContext(
            boolean acceptedByPostReleaseRescue,
            boolean hadTrackedToneMemoryBeforeFrame,
            long toneOnTimestampMs,
            double detectionLevel,
            boolean allowStrongRescueDebtReset
    ) {
        return new ToneOnAcceptedRunLifecycleContext(
                acceptedByPostReleaseRescue,
                !hadTrackedToneMemoryBeforeFrame,
                toneOnTimestampMs,
                detectionLevel,
                allowStrongRescueDebtReset
        );
    }

    private void activateAcceptedToneRun(
            ToneOnAcceptedRunLifecycleContext lifecycleContext,
            ToneFrequencyEstimate toneEstimate,
            long timestampMs
    ) {
        applyAcceptedToneOnPostReleaseDebt(
                lifecycleContext.acceptedByPostReleaseRescue,
                toneEstimate,
                timestampMs,
                lifecycleContext.allowStrongRescueDebtReset
        );
        currentToneStartedWithoutTrackedMemory = lifecycleContext.startedWithoutTrackedMemory;
        toneActive = true;
        toneStartedAtMs = lifecycleContext.toneOnTimestampMs;
        clearWeakPostReleaseOnsetChainAfterAcceptedToneOn();
        silenceStartedAtMs = -1L;
        resetCurrentToneRunContinuitySupportState();
        signalFloorEstimate = lifecycleContext.detectionLevel;
    }

    private void applyAcceptedToneOnPostReleaseDebt(
            boolean acceptedByPostReleaseRescue,
            ToneFrequencyEstimate toneEstimate,
            long timestampMs,
            boolean allowStrongRescueDebtReset
    ) {
        currentToneStartedByPostReleaseRescue = acceptedByPostReleaseRescue;
        rememberPostReleaseContinuityDebtState(
                transitionedPostReleaseContinuityDebtStateAfterAcceptedToneOn(
                        snapshotPostReleaseContinuityDebtState(),
                        acceptedByPostReleaseRescue,
                        toneEstimate,
                        timestampMs,
                        allowStrongRescueDebtReset
                )
        );
    }

    private void clearWeakPostReleaseOnsetChainAfterAcceptedToneOn() {
        rememberPostReleaseContinuityDebtState(
                stateWithAcceptedToneOnWeakPostReleaseOnsetChainCleared(
                        snapshotPostReleaseContinuityDebtState()
                )
        );
    }

    private void resetCurrentPostReleaseRescueStateAfterRunReset() {
        currentToneStartedByPostReleaseRescue = false;
        rememberPostReleaseContinuityDebtState(
                clearedPostReleaseContinuityDebtStateAfterRunReset()
        );
    }

    private void graduateCurrentPostReleaseRescueState() {
        currentToneStartedByPostReleaseRescue = false;
        rememberPostReleaseContinuityDebtState(
                clearedPostReleaseContinuityDebtStateAfterRescueGraduation()
        );
    }

    private ToneOffPostReleaseDebtContext buildToneOffPostReleaseDebtContext(
            long toneEndedAtMs,
            long durationMs
    ) {
        boolean rescuedToneRun = currentToneStartedByPostReleaseRescue;
        boolean shouldCarryContinuationWindow = rescuedToneRun
                && toneEndedAtMs >= 0L
                && durationMs <= POST_RELEASE_NEAR_TARGET_CONTINUITY_SHORT_TONE_MAX_MS;
        return new ToneOffPostReleaseDebtContext(
                toneEndedAtMs,
                shouldCarryContinuationWindow
        );
    }

    private ToneOffAcceptedEventContext buildToneOffAcceptedEventContext() {
        long toneEndedAtMs = Math.max(toneStartedAtMs, silenceStartedAtMs);
        if (autoTrackReleaseTailHoldExtendedUntilMs >= 0L) {
            toneEndedAtMs = Math.max(toneEndedAtMs, autoTrackReleaseTailHoldExtendedUntilMs);
        }
        long durationMs = Math.max(0L, toneEndedAtMs - toneStartedAtMs);
        return new ToneOffAcceptedEventContext(
                toneEndedAtMs,
                durationMs,
                buildToneOffPostReleaseDebtContext(toneEndedAtMs, durationMs)
        );
    }

    private void applyToneOffPostReleaseDebt(ToneOffPostReleaseDebtContext debtContext) {
        rememberPostReleaseContinuityDebtState(
                transitionedPostReleaseContinuityDebtStateAfterToneOff(
                        snapshotPostReleaseContinuityDebtState(),
                        debtContext
                )
        );
    }

    private void clearCurrentToneRunOriginFlags() {
        currentToneStartedByPostReleaseRescue = false;
        currentToneStartedWithoutTrackedMemory = false;
    }

    private void resetCurrentToneRunContinuitySupportState() {
        currentToneRunWeakBootstrapReleaseTailHoldCount = 0;
        autoTrackWeakValleyBridgeFramesRemaining = 0;
        autoTrackWeakValleyBridgeActive = false;
        clearAutoTrackReleaseTailHold();
    }

    private void clearCurrentToneRunLifecycleState() {
        resetCurrentPostReleaseRescueStateAfterRunReset();
        currentToneStartedWithoutTrackedMemory = false;
        resetCurrentToneRunContinuitySupportState();
    }

    private boolean shouldEmitToneOffAfterActiveRelease(long frameEndTimestampMs) {
        if (lockedRetuneGuardHoldingThisFrame) {
            return false;
        }
        return frameEndTimestampMs - silenceStartedAtMs >= currentToneOffHangMs();
    }

    private void consumeAcceptedToneOffEvent(
            ToneOffAcceptedEventContext eventContext,
            int peakAmplitude,
            double detectionLevel,
            double backgroundObservationLevel,
            List<CwToneEvent> events
    ) {
        toneActive = false;
        rememberAcceptedToneOffEvent(
                eventContext,
                peakAmplitude,
                detectionLevel,
                events
        );
        applyAcceptedToneOffRunLifecycle(eventContext);
        recoverFloorsAfterAcceptedToneOff(detectionLevel, backgroundObservationLevel);
    }

    private void rememberAcceptedToneOffEvent(
            ToneOffAcceptedEventContext eventContext,
            int peakAmplitude,
            double detectionLevel,
            List<CwToneEvent> events
    ) {
        CwToneEvent event = new CwToneEvent(
                CwToneEvent.Type.TONE_OFF,
                eventContext.toneEndedAtMs,
                peakAmplitude,
                detectionLevel,
                eventContext.durationMs
        );
        lastEvent = event;
        totalToneOffEvents += 1;
        events.add(event);
    }

    private void applyAcceptedToneOffRunLifecycle(ToneOffAcceptedEventContext eventContext) {
        applyToneOffPostReleaseDebt(eventContext.toneOffDebtContext);
        clearCurrentToneRunOriginFlags();
        toneStartedAtMs = -1L;
        silenceStartedAtMs = -1L;
        resetCurrentToneRunContinuitySupportState();
    }

    private void recoverFloorsAfterAcceptedToneOff(
            double detectionLevel,
            double backgroundObservationLevel
    ) {
        noiseFloorEstimate = smoothNoiseFloor(noiseFloorEstimate, backgroundObservationLevel);
        signalFloorEstimate = relaxSignalFloorDuringSilence(
                signalFloorEstimate,
                Math.max(detectionLevel, noiseFloorEstimate)
        );
    }

    private void resetToneActiveReleaseAttempt() {
        silenceStartedAtMs = -1L;
        resetCurrentToneRunContinuitySupportState();
    }

    private void rememberToneActiveContinuationFrame(
            long timestampMs,
            double detectionLevel,
            ToneActiveReleaseFrameSideEffectMode frameSideEffectMode
    ) {
        rememberObservedFrame(
                frameBookkeepingModeForToneActiveContinuation(frameSideEffectMode),
                timestampMs,
                detectionLevel
        );
    }

    private ToneActiveReleaseResolution resetToneActiveReleaseResolution() {
        return new ToneActiveReleaseResolution(
                ToneActiveReleaseResolutionType.RESET_RELEASE_ATTEMPT,
                -1L,
                null
        );
    }

    private ToneActiveReleaseResolution continuedWeakValleyBridgeToneActiveReleaseResolution() {
        return new ToneActiveReleaseResolution(
                ToneActiveReleaseResolutionType.CONTINUE_WEAK_VALLEY_BRIDGE,
                -1L,
                null
        );
    }

    private ToneActiveReleaseResolution continuedReleaseTailHoldToneActiveReleaseResolution(
            long releaseTailHoldExtendedUntilMs
    ) {
        return new ToneActiveReleaseResolution(
                ToneActiveReleaseResolutionType.CONTINUE_RELEASE_TAIL_HOLD,
                releaseTailHoldExtendedUntilMs,
                null
        );
    }

    private ToneActiveReleaseResolution waitingForToneOffHangToneActiveReleaseResolution() {
        return new ToneActiveReleaseResolution(
                ToneActiveReleaseResolutionType.WAIT_FOR_TONE_OFF_HANG,
                -1L,
                null
        );
    }

    private ToneActiveReleaseResolution emittedToneOffToneActiveReleaseResolution(
            ToneOffAcceptedEventContext toneOffAcceptedEventContext
    ) {
        return new ToneActiveReleaseResolution(
                ToneActiveReleaseResolutionType.EMIT_TONE_OFF,
                -1L,
                toneOffAcceptedEventContext
        );
    }

    private void rememberObservedFrame(
            FrameBookkeepingMode bookkeepingMode,
            long timestampMs,
            double detectionLevel
    ) {
        switch (bookkeepingMode) {
            case LIVE_OBSERVABLE_FALSE:
                rememberFrameLeaderObservability(false);
                rememberToneActivityWindow();
                break;
            case LIVE_OBSERVABLE_TRUE:
                rememberFrameLeaderObservability(true);
                rememberToneActivityWindow();
                break;
            case REPLAY_ONLY:
            default:
                break;
        }
        rememberFrame(timestampMs, detectionLevel);
    }

    private FrameBookkeepingMode frameBookkeepingModeForBlockedToneOnAttempt(
            ToneOnAttemptFrameSideEffectMode frameSideEffectMode
    ) {
        return frameSideEffectMode == ToneOnAttemptFrameSideEffectMode.LIVE_OBSERVABLE
                ? FrameBookkeepingMode.LIVE_OBSERVABLE_FALSE
                : FrameBookkeepingMode.REPLAY_ONLY;
    }

    private FrameBookkeepingMode frameBookkeepingModeForAcceptedToneOnAttempt(
            ToneOnAcceptedFrameSideEffectMode frameSideEffectMode
    ) {
        return frameSideEffectMode == ToneOnAcceptedFrameSideEffectMode.LIVE_OBSERVABLE
                ? FrameBookkeepingMode.LIVE_OBSERVABLE_TRUE
                : FrameBookkeepingMode.REPLAY_ONLY;
    }

    private FrameBookkeepingMode frameBookkeepingModeForToneActiveContinuation(
            ToneActiveReleaseFrameSideEffectMode frameSideEffectMode
    ) {
        return frameSideEffectMode == ToneActiveReleaseFrameSideEffectMode.LIVE_OBSERVABLE
                ? FrameBookkeepingMode.LIVE_OBSERVABLE_TRUE
                : FrameBookkeepingMode.REPLAY_ONLY;
    }

    private FrameBookkeepingMode frameBookkeepingModeForNoEventToneOnFrame(
            boolean rememberLeaderObservability
    ) {
        return rememberLeaderObservability
                ? FrameBookkeepingMode.LIVE_OBSERVABLE_TRUE
                : FrameBookkeepingMode.REPLAY_ONLY;
    }

    private boolean shouldSuppressPostReleaseSteadyCarrierToneOn(
            ToneFrequencyEstimate toneEstimate,
            double frameRms,
            double previousFrameRmsAmplitude,
            double previousToneRmsAmplitude,
            long frameLocalToneOnTimestampMs,
            boolean allowFrameLocalToneOnBypass
    ) {
        if (toneEstimate == null || toneEstimate.frequencyHz <= 0) {
            lastPostReleaseSuppressionDecision = "SKIP:NO_TONE";
            return false;
        }
        if (lastEvent == null || lastEvent.type() != CwToneEvent.Type.TONE_OFF) {
            lastPostReleaseSuppressionDecision = "SKIP:NO_TONE_OFF";
            return false;
        }
        if (totalToneOnEvents <= 0 || totalToneOnEvents != totalToneOffEvents) {
            lastPostReleaseSuppressionDecision = "SKIP:UNBALANCED_EVENTS";
            return false;
        }
        if (allowFrameLocalToneOnBypass && frameLocalToneOnTimestampMs >= 0L) {
            lastPostReleaseSuppressionDecision = "ALLOW:FRAME_LOCAL_ONSET";
            return false;
        }
        double frameRmsGrowthRatio = frameRms / Math.max(1.0d, previousFrameRmsAmplitude);
        double toneRmsGrowthRatio = toneEstimate.toneRmsAmplitude / Math.max(1.0d, previousToneRmsAmplitude);
        boolean suppressed = frameRmsGrowthRatio < POST_RELEASE_ONSET_GUARD_MIN_FRAME_RMS_GROWTH_RATIO
                && toneRmsGrowthRatio < POST_RELEASE_ONSET_GUARD_MIN_TONE_RMS_GROWTH_RATIO;
        lastPostReleaseSuppressionDecision = suppressed
                ? "SUPPRESS:LOW_GROWTH"
                : "ALLOW:GROWTH_PRESENT";
        return suppressed;
    }

    private boolean isSuspiciousMicroGapToneOnTimestamp(long toneOnTimestampMs) {
        if (toneOnTimestampMs < 0L
                || lastEvent == null
                || lastEvent.type() != CwToneEvent.Type.TONE_OFF) {
            return false;
        }
        long gapSinceLastToneOffMs = toneOnTimestampMs - lastEvent.timestampMs();
        return gapSinceLastToneOffMs >= 0L
                && gapSinceLastToneOffMs <= POST_RELEASE_FRAME_LOCAL_ONSET_MICRO_GAP_MAX_MS;
    }

    private boolean shouldAllowNearTargetPostReleaseToneOn(
            PostReleaseContinuityDebtContext postReleaseDebtContext,
            PostReleaseContinuityObservation continuityObservation,
            ToneFrequencyEstimate toneEstimate,
            double detectionLevel,
            int attackThreshold,
            int releaseThreshold,
            boolean trustedContinuityToneOnCandidate,
            double frameRms,
            double previousFrameRmsAmplitude,
            double previousToneRmsAmplitude,
            long frameLocalToneOnTimestampMs,
            long timestampMs
    ) {
        if (rxToneMode != RxToneMode.AUTO_TRACK
                || toneEstimate == null
                || toneEstimate.frequencyHz <= 0) {
            lastPostReleaseRescueDecision = "BLOCKED:MODE_OR_TONE";
            return false;
        }
        if (lastEvent == null || lastEvent.type() != CwToneEvent.Type.TONE_OFF) {
            lastPostReleaseRescueDecision = "BLOCKED:NO_TONE_OFF";
            return false;
        }
        if (totalToneOnEvents <= 0
                || totalToneOnEvents != totalToneOffEvents) {
            lastPostReleaseRescueDecision = "BLOCKED:UNBALANCED_EVENTS";
            return false;
        }
        PostReleaseToneOnRescueContext rescueContext = buildPostReleaseToneOnRescueContext(
                postReleaseDebtContext,
                continuityObservation,
                toneEstimate,
                attackThreshold,
                releaseThreshold,
                frameRms,
                previousFrameRmsAmplitude,
                previousToneRmsAmplitude,
                frameLocalToneOnTimestampMs,
                timestampMs
        );
        if (!isTrackedToneMemoryActive(timestampMs)) {
            lastPostReleaseRescueDecision = "BLOCKED:NO_TRACKED_MEMORY";
            return false;
        }
        if (!targetToneLocked && !trustedContinuityToneOnCandidate) {
            lastPostReleaseRescueDecision = "BLOCKED:TARGET_NOT_LOCKED";
            return false;
        }
        if (rescueContext.gapSinceLastToneOffMs > rescueContext.reacquireWindowMs) {
            lastPostReleaseRescueDecision = "BLOCKED:WINDOW_EXPIRED";
            return false;
        }
        int continuityAnchorFrequencyHz = continuityAnchorFrequencyHz();
        if (continuityAnchorFrequencyHz <= 0) {
            continuityAnchorFrequencyHz = targetToneFrequencyHz;
        }
        if (continuityAnchorFrequencyHz <= 0
                || Math.abs(toneEstimate.frequencyHz - continuityAnchorFrequencyHz)
                > POST_RELEASE_NEAR_TARGET_MAX_ANCHOR_DRIFT_HZ) {
            lastPostReleaseRescueDecision = "BLOCKED:FAR_FROM_ANCHOR";
            return false;
        }
        if (shouldBlockPostReleaseRescueByContinuityDebt(
                rescueContext,
                postReleaseDebtContext,
                trustedContinuityToneOnCandidate
        )) {
            return false;
        }
        int requiredDetectionThreshold = requiredDetectionThresholdForPostReleaseToneOn(
                rescueContext,
                postReleaseDebtContext,
                trustedContinuityToneOnCandidate,
                releaseThreshold
        );
        if (detectionLevel < requiredDetectionThreshold) {
            lastPostReleaseRescueDecision = "BLOCKED:LOW_DETECTION";
            return false;
        }
        if (frameLocalToneOnTimestampMs < 0L
                && rescueContext.frameRmsGrowthRatio < POST_RELEASE_NEAR_TARGET_MIN_FRAME_RMS_GROWTH_RATIO
                && rescueContext.toneRmsGrowthRatio < POST_RELEASE_NEAR_TARGET_MIN_TONE_RMS_GROWTH_RATIO
                && !rescueContext.lowGrowthStrongSteadyNearTargetCandidate
                && !trustedContinuityToneOnCandidate
                && !rescueContext.continuationWindowActive) {
            lastPostReleaseRescueDecision = "BLOCKED:LOW_GROWTH";
            return false;
        }
        boolean allowed;
        if (postReleaseDebtContext.weakPostReleaseOnsetRescueCandidate
                && rescueContext.continuationWindowActive) {
            allowed = toneEstimate.dominanceRatio >= POST_RELEASE_WEAK_ONSET_CHAIN_MIN_DOMINANCE
                    || toneEstimate.localContrastRatio >= POST_RELEASE_WEAK_ONSET_CHAIN_MIN_LOCAL_CONTRAST;
            lastPostReleaseRescueDecision = allowed
                    ? (rescueContext.strongGrowthContinuityOverflow
                    ? "ALLOW:WEAK_CHAIN_STRONG_GROWTH"
                    : "ALLOW:WEAK_CONTINUITY_WINDOW")
                    : "BLOCKED:WEAK_LOW_QUALITY";
        } else if (trustedContinuityToneOnCandidate && !targetToneLocked) {
            allowed = toneEstimate.dominanceRatio >= TRUSTED_CONTINUITY_TONE_ON_MIN_DOMINANCE_RATIO
                    && (toneEstimate.isolationRatio >= TRUSTED_CONTINUITY_TONE_ON_MIN_ISOLATION_RATIO
                    || toneEstimate.localContrastRatio >= TRUSTED_CONTINUITY_TONE_ON_MIN_LOCAL_CONTRAST_RATIO);
            lastPostReleaseRescueDecision = allowed
                    ? "ALLOW:TRUSTED_CONTINUITY"
                    : "BLOCKED:TRUSTED_CONTINUITY_LOW_QUALITY";
        } else {
            allowed = toneEstimate.dominanceRatio >= POST_RELEASE_NEAR_TARGET_DOMINANCE_MIN
                    && (toneEstimate.isolationRatio >= POST_RELEASE_NEAR_TARGET_ISOLATION_MIN
                    || toneEstimate.localContrastRatio >= POST_RELEASE_NEAR_TARGET_LOCAL_CONTRAST_MIN);
            lastPostReleaseRescueDecision = allowed
                    ? (rescueContext.continuationWindowActive ? "ALLOW:CONTINUITY_WINDOW" : "ALLOW")
                    : "BLOCKED:LOW_QUALITY";
        }
        return allowed;
    }

    private boolean isStrongSteadyNearTargetReacquireCandidate(ToneFrequencyEstimate toneEstimate) {
        return toneEstimate.locked
                && toneEstimate.dominanceRatio >= POST_RELEASE_NEAR_TARGET_STEADY_DOMINANCE_MIN
                && toneEstimate.isolationRatio >= POST_RELEASE_NEAR_TARGET_STEADY_ISOLATION_MIN;
    }

    private boolean isLowGrowthStrongSteadyNearTargetReacquireCandidate(
            ToneFrequencyEstimate toneEstimate,
            double frameRms,
            double previousFrameRmsAmplitude,
            double previousToneRmsAmplitude,
            long frameLocalToneOnTimestampMs,
            long timestampMs
    ) {
        if (frameLocalToneOnTimestampMs >= 0L
                || lastEvent == null
                || lastEvent.type() != CwToneEvent.Type.TONE_OFF
                || !isStrongSteadyNearTargetReacquireCandidate(toneEstimate)) {
            return false;
        }
        if ((timestampMs - lastEvent.timestampMs()) <= POST_RELEASE_NEAR_TARGET_STEADY_MIN_GAP_MS) {
            return false;
        }
        double frameRmsGrowthRatio = frameRms / Math.max(1.0d, previousFrameRmsAmplitude);
        double toneRmsGrowthRatio = toneEstimate.toneRmsAmplitude / Math.max(1.0d, previousToneRmsAmplitude);
        return frameRmsGrowthRatio < POST_RELEASE_NEAR_TARGET_MIN_FRAME_RMS_GROWTH_RATIO
                && toneRmsGrowthRatio < POST_RELEASE_NEAR_TARGET_MIN_TONE_RMS_GROWTH_RATIO;
    }

    private boolean isSteadyNearTargetLateGapCandidate(
            ToneFrequencyEstimate toneEstimate,
            long timestampMs
    ) {
        return lastEvent != null
                && lastEvent.type() == CwToneEvent.Type.TONE_OFF
                && (timestampMs - lastEvent.timestampMs()) >= POST_RELEASE_NEAR_TARGET_STEADY_LATE_GAP_MS
                && isStrongSteadyNearTargetReacquireCandidate(toneEstimate);
    }

    private int softenedPostReleaseNearTargetAttackThreshold(
            int attackThreshold,
            int releaseThreshold,
            boolean lowGrowthStrongSteadyNearTargetCandidate
    ) {
        int continuityReferenceThreshold = Math.max(1, Math.min(attackThreshold, releaseThreshold));
        double slackRatio = lowGrowthStrongSteadyNearTargetCandidate
                ? POST_RELEASE_NEAR_TARGET_STEADY_ATTACK_SLACK_RATIO
                : POST_RELEASE_NEAR_TARGET_ATTACK_SLACK_RATIO;
        return Math.max(
                1,
                (int) Math.round(continuityReferenceThreshold * slackRatio)
        );
    }

    private int continuityPostReleaseNearTargetAttackThreshold(int attackThreshold, int releaseThreshold) {
        int continuityReferenceThreshold = Math.max(1, Math.min(attackThreshold, releaseThreshold));
        return Math.max(
                1,
                (int) Math.round(continuityReferenceThreshold * POST_RELEASE_NEAR_TARGET_CONTINUITY_ATTACK_SLACK_RATIO)
        );
    }

    private ToneOnAttemptFrontGateResolution buildToneOnAttemptFrontGateResolution(
            ToneOnAdmissionContext toneOnContext,
            ToneFrequencyEstimate toneEstimate,
            boolean hadToneHistoryBeforeFrame,
            boolean hadTrackedToneMemoryBeforeFrame,
            int attackAnchorFrequencyHzBeforeFrame,
            double attackReferenceToneRmsBeforeFrame,
            long timestampMs,
            boolean allowEdgeFreeFarCarrierBlock
    ) {
        if (toneOnContext.exhaustedPostReleaseContinuityAttackCandidate) {
            return blockedToneOnAttemptFrontGateResolution(
                    ToneOnAttemptFrontGateResolutionType.BLOCKED_CONTINUITY_CHAIN_EXHAUSTED
            );
        }
        if (shouldDelayFarAttackToneOn(
                toneEstimate,
                hadToneHistoryBeforeFrame,
                hadTrackedToneMemoryBeforeFrame,
                attackAnchorFrequencyHzBeforeFrame,
                attackReferenceToneRmsBeforeFrame,
                timestampMs
        )) {
            return blockedToneOnAttemptFrontGateResolution(
                    ToneOnAttemptFrontGateResolutionType.BLOCKED_FAR_ATTACK_CONFIRM
            );
        }
        if (allowEdgeFreeFarCarrierBlock
                && shouldBlockEdgeFreeFarCarrierToneOn(
                toneEstimate,
                hadTrackedToneMemoryBeforeFrame,
                attackAnchorFrequencyHzBeforeFrame,
                toneOnContext.frameLocalToneOnTimestampMs,
                toneOnContext.nearTargetPostReleaseToneOnRescue
        )) {
            return blockedToneOnAttemptFrontGateResolution(
                    hadTrackedToneMemoryBeforeFrame
                            ? ToneOnAttemptFrontGateResolutionType.BLOCKED_TRUSTED_FAR_CARRIER
                            : ToneOnAttemptFrontGateResolutionType.BLOCKED_EDGE_FREE_FAR_CARRIER
            );
        }
        return proceededToneOnAttemptFrontGateResolution();
    }

    private void rememberToneOnAttemptFrontGateResolution(
            ToneOnAttemptFrontGateResolution frontGateResolution
    ) {
        if (frontGateResolution.type
                == ToneOnAttemptFrontGateResolutionType.BLOCKED_FAR_ATTACK_CONFIRM) {
            lastFarAttackToneOnDelayed = true;
        }
        lastToneOnDecision = toneOnDecisionForFrontGateResolution(frontGateResolution);
    }

    private boolean consumeBlockedToneOnAttemptFrontGateResolution(
            ToneOnAttemptFrontGateResolution frontGateResolution,
            ToneOnAttemptFrameSideEffectMode frameSideEffectMode,
            long timestampMs,
            double detectionLevel
    ) {
        if (!frontGateResolution.blocked()) {
            return false;
        }
        rememberToneOnAttemptFrontGateResolution(frontGateResolution);
        rememberBlockedToneOnAttemptFrame(frameSideEffectMode, timestampMs, detectionLevel);
        return true;
    }

    private String toneOnDecisionForFrontGateResolution(
            ToneOnAttemptFrontGateResolution frontGateResolution
    ) {
        switch (frontGateResolution.type) {
            case BLOCKED_CONTINUITY_CHAIN_EXHAUSTED:
                return "BLOCKED:CONTINUITY_CHAIN_EXHAUSTED";
            case BLOCKED_FAR_ATTACK_CONFIRM:
                return "BLOCKED:FAR_ATTACK_CONFIRM";
            case BLOCKED_TRUSTED_FAR_CARRIER:
                return "BLOCKED:TRUSTED_FAR_CARRIER";
            case BLOCKED_EDGE_FREE_FAR_CARRIER:
                return "BLOCKED:EDGE_FREE_FAR_CARRIER";
            case PROCEED:
            default:
                return "NONE";
        }
    }

    private ToneOnAttemptFrontGateResolution blockedToneOnAttemptFrontGateResolution(
            ToneOnAttemptFrontGateResolutionType type
    ) {
        return new ToneOnAttemptFrontGateResolution(type);
    }

    private ToneOnAttemptFrontGateResolution proceededToneOnAttemptFrontGateResolution() {
        return new ToneOnAttemptFrontGateResolution(ToneOnAttemptFrontGateResolutionType.PROCEED);
    }

    private boolean consumeBlockedToneOnAttemptResolution(
            ToneOnAttemptResolution toneOnAttemptResolution,
            ToneOnAttemptFrameSideEffectMode frameSideEffectMode,
            long timestampMs,
            double detectionLevel
    ) {
        if (!toneOnAttemptResolution.blocked()) {
            return false;
        }
        lastToneOnDecision = toneOnDecisionForAttemptResolution(toneOnAttemptResolution);
        rememberBlockedToneOnAttemptFrame(frameSideEffectMode, timestampMs, detectionLevel);
        return true;
    }

    private String toneOnDecisionForAttemptResolution(
            ToneOnAttemptResolution toneOnAttemptResolution
    ) {
        switch (toneOnAttemptResolution.type) {
            case BLOCKED_POST_RELEASE_STEADY_SUPPRESSION:
                return "BLOCKED:POST_RELEASE_STEADY_SUPPRESSION";
            case BLOCKED_WEAK_CHAIN_FALLBACK_ATTACK:
                return "BLOCKED:WEAK_CHAIN_FALLBACK_ATTACK";
            case BLOCKED_MICRO_GAP_TONE_ON:
                return "BLOCKED:MICRO_GAP_TONE_ON";
            case PROCEED:
            default:
                return "NONE";
        }
    }

    private void rememberBlockedToneOnAttemptFrame(
            ToneOnAttemptFrameSideEffectMode frameSideEffectMode,
            long timestampMs,
            double detectionLevel
    ) {
        rememberObservedFrame(
                frameBookkeepingModeForBlockedToneOnAttempt(frameSideEffectMode),
                timestampMs,
                detectionLevel
        );
    }

    private void consumeAcceptedToneOnAttempt(
            ToneOnAdmissionContext toneOnContext,
            long toneOnTimestampMs,
            boolean hadTrackedToneMemoryBeforeFrame,
            double detectionLevel,
            int peakAmplitude,
            ToneFrequencyEstimate toneEstimate,
            long timestampMs,
            List<CwToneEvent> events,
            boolean allowStrongRescueDebtReset,
            ToneOnAcceptedFrameSideEffectMode frameSideEffectMode
    ) {
        rememberAcceptedToneOnAttempt(toneOnContext);
        ToneOnAcceptedRunLifecycleContext lifecycleContext =
                buildToneOnAcceptedRunLifecycleContext(
                        toneOnContext.nearTargetPostReleaseToneOnRescue,
                        hadTrackedToneMemoryBeforeFrame,
                        toneOnTimestampMs,
                        detectionLevel,
                        allowStrongRescueDebtReset
                );
        activateAcceptedToneRun(
                lifecycleContext,
                toneEstimate,
                timestampMs
        );
        CwToneEvent event = new CwToneEvent(
                CwToneEvent.Type.TONE_ON,
                toneOnTimestampMs,
                peakAmplitude,
                detectionLevel,
                0L
        );
        lastEvent = event;
        totalToneOnEvents += 1;
        events.add(event);
        rememberAcceptedToneOnAttemptFrame(frameSideEffectMode, timestampMs, detectionLevel);
    }

    private void rememberAcceptedToneOnAttempt(ToneOnAdmissionContext toneOnContext) {
        lastToneOnAccepted = true;
        lastToneOnAcceptedByRescue = toneOnContext.nearTargetPostReleaseToneOnRescue;
        lastToneOnDecision = toneOnDecisionForAcceptedAttempt(toneOnContext);
    }

    private String toneOnDecisionForAcceptedAttempt(ToneOnAdmissionContext toneOnContext) {
        return toneOnContext.nearTargetPostReleaseToneOnRescue
                ? "ALLOW:POST_RELEASE_RESCUE"
                : "ALLOW:ATTACK_THRESHOLD";
    }

    private void rememberAcceptedToneOnAttemptFrame(
            ToneOnAcceptedFrameSideEffectMode frameSideEffectMode,
            long timestampMs,
            double detectionLevel
    ) {
        rememberObservedFrame(
                frameBookkeepingModeForAcceptedToneOnAttempt(frameSideEffectMode),
                timestampMs,
                detectionLevel
        );
    }

    private void rememberPreAttemptToneOnDecision(
            ToneOnAdmissionContext toneOnContext,
            boolean toneActive,
            double detectionLevel,
            int attackThreshold
    ) {
        lastToneOnDecision = toneOnDecisionBeforeAttempt(
                toneOnContext,
                toneActive,
                detectionLevel,
                attackThreshold
        );
    }

    private String toneOnDecisionBeforeAttempt(
            ToneOnAdmissionContext toneOnContext,
            boolean toneActive,
            double detectionLevel,
            int attackThreshold
    ) {
        if (toneActive) {
            return "SKIP:ALREADY_ACTIVE";
        }
        if (toneOnContext.blockedByAttackQualification()) {
            return "BLOCKED:ATTACK_QUALIFICATION";
        }
        if (detectionLevel < attackThreshold
                && !toneOnContext.nearTargetPostReleaseToneOnRescue) {
            return "BLOCKED:BELOW_ATTACK_AND_NO_RESCUE";
        }
        return lastToneOnDecision;
    }

    private void rememberNoEventToneOnFrame(
            boolean rememberLeaderObservability,
            long timestampMs,
            double detectionLevel
    ) {
        rememberObservedFrame(
                frameBookkeepingModeForNoEventToneOnFrame(rememberLeaderObservability),
                timestampMs,
                detectionLevel
        );
    }

    private ToneOnAttemptResolution buildToneOnAttemptResolution(
            ToneOnAdmissionContext toneOnContext,
            ToneFrequencyEstimate toneEstimate,
            double frameRms,
            double previousFrameRmsAmplitude,
            double previousToneRmsAmplitude,
            int attackThreshold,
            int releaseThreshold,
            double detectionLevel,
            long timestampMs
    ) {
        rememberToneOnSuppressionBypass(toneOnContext);
        boolean strongTurnStartContinuation = shouldAllowStrongTurnStartContinuation(
                toneOnContext,
                toneEstimate,
                detectionLevel,
                attackThreshold,
                releaseThreshold,
                frameRms,
                previousFrameRmsAmplitude,
                previousToneRmsAmplitude
        );
        if (shouldBlockToneOnAttemptByPostReleaseSteadySuppression(
                toneOnContext,
                toneEstimate,
                frameRms,
                previousFrameRmsAmplitude,
                previousToneRmsAmplitude,
                strongTurnStartContinuation
        )) {
            return blockedToneOnAttemptResolution(
                    ToneOnAttemptResolutionType.BLOCKED_POST_RELEASE_STEADY_SUPPRESSION
            );
        }
        if (shouldBlockToneOnAttemptByWeakChainFallback(toneOnContext)) {
            return blockedToneOnAttemptResolution(
                    ToneOnAttemptResolutionType.BLOCKED_WEAK_CHAIN_FALLBACK_ATTACK
            );
        }
        long toneOnTimestampMs = resolvedToneOnTimestampForAttempt(
                toneOnContext,
                toneOnThresholdForAdmission(toneOnContext, attackThreshold, releaseThreshold),
                detectionLevel,
                timestampMs
        );
        if (isSuspiciousMicroGapToneOnTimestamp(toneOnTimestampMs)
                && !strongTurnStartContinuation) {
            lastPostReleaseSteadyCarrierSuppressed = true;
            lastPostReleaseSuppressionDecision = "SUPPRESS:MICRO_GAP_TONE_ON";
            return blockedToneOnAttemptResolution(
                    ToneOnAttemptResolutionType.BLOCKED_MICRO_GAP_TONE_ON
            );
        }
        return proceededToneOnAttemptResolution(toneOnTimestampMs);
    }

    private void rememberToneOnSuppressionBypass(ToneOnAdmissionContext toneOnContext) {
        if (toneOnContext.lowGrowthStrongSteadyNearTargetRescue) {
            lastPostReleaseSuppressionDecision = "BYPASS:LOW_GROWTH_STEADY_RESCUE";
        } else if (toneOnContext.steadyLateGapNearTargetRescueCandidate) {
            lastPostReleaseSuppressionDecision = "BYPASS:LATE_GAP_RESCUE";
        }
    }

    private boolean shouldBlockToneOnAttemptByPostReleaseSteadySuppression(
            ToneOnAdmissionContext toneOnContext,
            ToneFrequencyEstimate toneEstimate,
            double frameRms,
            double previousFrameRmsAmplitude,
            double previousToneRmsAmplitude,
            boolean strongTurnStartContinuation
    ) {
        if (strongTurnStartContinuation) {
            lastPostReleaseSuppressionDecision = "BYPASS:STRONG_TURN_START_CONTINUATION";
            return false;
        }
        if (shouldSuppressToneOnAttemptWithoutWeakContinuityPressure(
                toneOnContext,
                toneEstimate,
                frameRms,
                previousFrameRmsAmplitude,
                previousToneRmsAmplitude
        )) {
            lastPostReleaseSteadyCarrierSuppressed = true;
            return true;
        }
        if (shouldSuppressToneOnAttemptUnderWeakContinuityPressure(
                toneOnContext,
                toneEstimate,
                frameRms,
                previousFrameRmsAmplitude,
                previousToneRmsAmplitude
        )) {
            lastPostReleaseSteadyCarrierSuppressed = true;
            return true;
        }
        return false;
    }

    private boolean shouldAllowStrongTurnStartContinuation(
            ToneOnAdmissionContext toneOnContext,
            ToneFrequencyEstimate toneEstimate,
            double detectionLevel,
            int attackThreshold,
            int releaseThreshold,
            double frameRms,
            double previousFrameRmsAmplitude,
            double previousToneRmsAmplitude
    ) {
        if (toneOnContext == null
                || toneEstimate == null
                || !toneEstimate.locked
                || toneEstimate.frequencyHz <= 0
                || lockedBranchReferenceToneRmsAmplitude <= 0.0d) {
            return false;
        }
        if (rxToneMode != RxToneMode.FIXED_TONE) {
            return false;
        }
        if (toneOnContext.weakContinuityCooldownAttackCandidate
                || toneOnContext.exhaustedPostReleaseContinuityAttackCandidate) {
            return false;
        }
        double referenceToneRms = Math.max(1.0d, lockedBranchReferenceToneRmsAmplitude);
        double toneRmsRatio = toneEstimate.toneRmsAmplitude / referenceToneRms;
        double detectionRatio = detectionLevel / Math.max(1.0d, Math.max(attackThreshold, releaseThreshold));
        double frameRmsGrowthRatio = frameRms / Math.max(1.0d, previousFrameRmsAmplitude);
        double toneRmsGrowthRatio = toneEstimate.toneRmsAmplitude / Math.max(1.0d, previousToneRmsAmplitude);
        return toneRmsRatio >= 0.45d
                && detectionRatio >= 1.15d
                && frameRmsGrowthRatio >= 0.80d
                && toneRmsGrowthRatio >= 0.70d
                && toneEstimate.dominanceRatio >= 0.80d
                && (toneEstimate.isolationRatio >= 0.58d
                || toneEstimate.localContrastRatio >= 0.72d);
    }

    private boolean shouldSuppressToneOnAttemptWithoutWeakContinuityPressure(
            ToneOnAdmissionContext toneOnContext,
            ToneFrequencyEstimate toneEstimate,
            double frameRms,
            double previousFrameRmsAmplitude,
            double previousToneRmsAmplitude
    ) {
        return !toneOnContext.weakPostReleaseOnsetRescueCandidate
                && !toneOnContext.exhaustedPostReleaseContinuityAttackCandidate
                && !toneOnContext.weakContinuityCooldownAttackCandidate
                && !toneOnContext.lowGrowthStrongSteadyNearTargetRescue
                && !toneOnContext.steadyLateGapNearTargetRescueCandidate
                && shouldSuppressPostReleaseSteadyCarrierToneOn(
                toneEstimate,
                frameRms,
                previousFrameRmsAmplitude,
                previousToneRmsAmplitude,
                toneOnContext.frameLocalToneOnTimestampMs,
                true
        );
    }

    private boolean shouldSuppressToneOnAttemptUnderWeakContinuityPressure(
            ToneOnAdmissionContext toneOnContext,
            ToneFrequencyEstimate toneEstimate,
            double frameRms,
            double previousFrameRmsAmplitude,
            double previousToneRmsAmplitude
    ) {
        return (toneOnContext.weakPostReleaseOnsetRescueCandidate
                || toneOnContext.exhaustedPostReleaseContinuityAttackCandidate
                || toneOnContext.weakContinuityCooldownAttackCandidate)
                && !toneOnContext.nearTargetPostReleaseToneOnRescue
                && !toneOnContext.lowGrowthStrongSteadyNearTargetRescue
                && !toneOnContext.steadyLateGapNearTargetRescueCandidate
                && shouldSuppressPostReleaseSteadyCarrierToneOn(
                toneEstimate,
                frameRms,
                previousFrameRmsAmplitude,
                previousToneRmsAmplitude,
                toneOnContext.frameLocalToneOnTimestampMs,
                false
        );
    }

    private boolean shouldBlockToneOnAttemptByWeakChainFallback(ToneOnAdmissionContext toneOnContext) {
        return shouldBlockWeakChainFallbackAttack(
                toneOnContext.nearTargetPostReleaseToneOnRescue,
                toneOnContext.exhaustedPostReleaseContinuityAttackCandidate,
                toneOnContext.weakContinuityCooldownAttackCandidate,
                toneOnContext.lowGrowthStrongSteadyNearTargetRescue,
                toneOnContext.steadyLateGapNearTargetRescueCandidate
        );
    }

    private int toneOnThresholdForAdmission(
            ToneOnAdmissionContext toneOnContext,
            int attackThreshold,
            int releaseThreshold
    ) {
        return toneOnContext.nearTargetPostReleaseToneOnRescue
                ? softenedPostReleaseNearTargetAttackThreshold(
                attackThreshold,
                releaseThreshold,
                toneOnContext.steadyLateGapNearTargetRescueCandidate
        )
                : attackThreshold;
    }

    private long resolvedToneOnTimestampForAttempt(
            ToneOnAdmissionContext toneOnContext,
            int toneOnThreshold,
            double detectionLevel,
            long timestampMs
    ) {
        long toneOnTimestampMs = estimateCrossingTimestamp(
                timestampMs,
                toneOnThreshold,
                detectionLevel,
                true
        );
        if (toneOnContext.frameLocalToneOnTimestampMs >= 0L) {
            return toneOnContext.frameLocalToneOnTimestampMs;
        }
        if (toneOnContext.nearTargetPostReleaseToneOnRescue) {
            return weakPostReleaseOnsetChainToneOnTimestampOrFallback(toneOnTimestampMs);
        }
        return toneOnTimestampMs;
    }

    private ToneOnAttemptResolution blockedToneOnAttemptResolution(ToneOnAttemptResolutionType type) {
        return new ToneOnAttemptResolution(type, -1L);
    }

    private ToneOnAttemptResolution proceededToneOnAttemptResolution(long toneOnTimestampMs) {
        return new ToneOnAttemptResolution(ToneOnAttemptResolutionType.PROCEED, toneOnTimestampMs);
    }

    private int weakPostReleaseRescueThreshold(int releaseThreshold) {
        return Math.max(
                1,
                (int) Math.round(Math.max(1, releaseThreshold) * POST_RELEASE_WEAK_ONSET_CHAIN_MIN_THRESHOLD_RATIO)
        );
    }

    private boolean shouldBlockWeakChainFallbackAttack(
            boolean nearTargetPostReleaseToneOnRescue,
            boolean exhaustedPostReleaseContinuityAttackCandidate,
            boolean weakContinuityCooldownAttackCandidate,
            boolean lowGrowthStrongSteadyNearTargetRescue,
            boolean steadyLateGapNearTargetRescueCandidate
    ) {
        if (toneActive
                || nearTargetPostReleaseToneOnRescue
                || lowGrowthStrongSteadyNearTargetRescue
                || steadyLateGapNearTargetRescueCandidate
                || (!exhaustedPostReleaseContinuityAttackCandidate
                && !weakContinuityCooldownAttackCandidate)) {
            return false;
        }
        return "BLOCKED:WEAK_CHAIN_LIMIT".equals(lastPostReleaseRescueDecision)
                || "BLOCKED:CONTINUITY_CHAIN_EXHAUSTED".equals(lastPostReleaseRescueDecision);
    }

    private boolean shouldResetWeakPostReleaseContinuityDebtAfterRescue(
            ToneFrequencyEstimate toneEstimate
    ) {
        if (toneEstimate == null || !toneEstimate.locked) {
            return false;
        }
        int continuityAnchorFrequencyHz = continuityAnchorFrequencyHz();
        if (continuityAnchorFrequencyHz <= 0) {
            continuityAnchorFrequencyHz = targetToneFrequencyHz;
        }
        if (recentLockedFrameRatioFromHistory() < POST_RELEASE_WEAK_ONSET_TRUSTED_MIN_LOCKED_RATIO
                || recentLockedFrameRatioNearFrequencyFromHistory(
                continuityAnchorFrequencyHz,
                AUTO_TRACK_WEAK_VALLEY_RESCUE_NEAR_TARGET_OFFSET_HZ
        )
                < POST_RELEASE_WEAK_ONSET_TRUSTED_MIN_NEAR_TARGET_RATIO) {
            return false;
        }
        return toneEstimate.dominanceRatio >= POST_RELEASE_WEAK_ONSET_TRUSTED_MIN_DOMINANCE
                && toneEstimate.isolationRatio >= POST_RELEASE_WEAK_ONSET_TRUSTED_MIN_ISOLATION
                && toneEstimate.localContrastRatio >= POST_RELEASE_WEAK_ONSET_TRUSTED_MIN_LOCAL_CONTRAST;
    }

    private void maybeGraduateCurrentPostReleaseRescuedTone(
            ToneFrequencyEstimate toneEstimate,
            long timestampMs
    ) {
        if (!shouldGraduateCurrentPostReleaseRescuedTone(toneEstimate, timestampMs)) {
            return;
        }
        graduateCurrentPostReleaseRescueState();
    }

    private boolean shouldGraduateCurrentPostReleaseRescuedTone(
            ToneFrequencyEstimate toneEstimate,
            long timestampMs
    ) {
        CurrentToneRunBootstrapContext currentRunBootstrapContext =
                buildCurrentToneRunBootstrapContext(toneEstimate, timestampMs);
        PostReleaseRescuedToneProgressContext rescuedToneProgressContext =
                buildPostReleaseRescuedToneProgressContext(
                        toneEstimate,
                        currentRunBootstrapContext,
                        timestampMs
                );
        return rescuedToneProgressContext.canGraduate();
    }

    private boolean isPostReleaseRescueContinuationWindowActive(long timestampMs) {
        return isPostReleaseRescueContinuationWindowActive(
                snapshotPostReleaseContinuityDebtState(),
                timestampMs
        );
    }

    private boolean isPostReleaseRescueContinuationWindowActive(
            PostReleaseContinuityDebtState debtState,
            long timestampMs
    ) {
        return debtState.continuationWindowUntilMs >= 0L
                && timestampMs >= 0L
                && timestampMs <= debtState.continuationWindowUntilMs;
    }

    private boolean isWeakPostReleaseOnsetChainCandidate(
            ToneFrequencyEstimate toneEstimate,
            double detectionLevel,
            int releaseThreshold,
            long timestampMs
    ) {
        if (toneActive
                || toneEstimate == null
                || toneEstimate.frequencyHz <= 0
                || detectionLevel <= 0.0d
                || lastEvent == null
                || lastEvent.type() != CwToneEvent.Type.TONE_OFF
                || !isTrackedToneMemoryActive(timestampMs)
                || !isPostReleaseRescueContinuationWindowActive(timestampMs)) {
            return false;
        }
        int continuityAnchorFrequencyHz = continuityAnchorFrequencyHz();
        if (continuityAnchorFrequencyHz <= 0) {
            continuityAnchorFrequencyHz = targetToneFrequencyHz;
        }
        if (continuityAnchorFrequencyHz <= 0
                || Math.abs(toneEstimate.frequencyHz - continuityAnchorFrequencyHz)
                > POST_RELEASE_NEAR_TARGET_MAX_ANCHOR_DRIFT_HZ) {
            return false;
        }
        double minimumDetection = Math.max(
                1.0d,
                releaseThreshold * POST_RELEASE_WEAK_ONSET_CHAIN_MIN_THRESHOLD_RATIO
        );
        if (detectionLevel < minimumDetection) {
            return false;
        }
        return toneEstimate.dominanceRatio >= POST_RELEASE_WEAK_ONSET_CHAIN_MIN_DOMINANCE
                || toneEstimate.localContrastRatio >= POST_RELEASE_WEAK_ONSET_CHAIN_MIN_LOCAL_CONTRAST;
    }

    private boolean isTrustedContinuityToneOnCandidate(
            ToneFrequencyEstimate toneEstimate,
            double detectionLevel,
            int attackThreshold,
            boolean hadTrackedToneMemoryBeforeFrame,
            int attackAnchorFrequencyHzBeforeFrame,
            long timestampMs
    ) {
        if (toneActive
                || toneEstimate == null
                || toneEstimate.frequencyHz <= 0
                || detectionLevel <= 0.0d
                || !hadTrackedToneMemoryBeforeFrame
                || lastEvent == null
                || lastEvent.type() != CwToneEvent.Type.TONE_OFF
                || !isTrackedToneMemoryActive(timestampMs)) {
            return false;
        }
        int anchorFrequencyHz = attackAnchorFrequencyHzBeforeFrame > 0
                ? attackAnchorFrequencyHzBeforeFrame
                : continuityAnchorFrequencyHz();
        if (anchorFrequencyHz <= 0
                || Math.abs(toneEstimate.frequencyHz - anchorFrequencyHz)
                > POST_RELEASE_NEAR_TARGET_MAX_ANCHOR_DRIFT_HZ) {
            return false;
        }
        if (maxConsecutiveLockedFrames < CONTINUITY_ANCHOR_TRUST_MIN_STABLE_LOCK_FRAMES
                && consecutiveLockedFrames < CONTINUITY_ANCHOR_TRUST_MIN_STABLE_LOCK_FRAMES) {
            return false;
        }
        boolean targetStillAlignedWithAnchor = targetToneFrequencyHz > 0
                && Math.abs(targetToneFrequencyHz - anchorFrequencyHz) <= TONE_SCAN_STEP_HZ;
        if (!targetStillAlignedWithAnchor
                && !hasRepresentativeAnchorNear(anchorFrequencyHz)
                && !hasStrongHypothesisAnchorNear(anchorFrequencyHz)) {
            return false;
        }
        if (toneEstimate.toneRmsAmplitude < (MIN_TRACKED_TONE_RMS * TRUSTED_CONTINUITY_TONE_ON_MIN_TONE_RATIO)
                || toneEstimate.dominanceRatio < TRUSTED_CONTINUITY_TONE_ON_MIN_DOMINANCE_RATIO) {
            return false;
        }
        return toneEstimate.isolationRatio >= TRUSTED_CONTINUITY_TONE_ON_MIN_ISOLATION_RATIO
                || toneEstimate.localContrastRatio >= TRUSTED_CONTINUITY_TONE_ON_MIN_LOCAL_CONTRAST_RATIO;
    }

    private void updateWeakPostReleaseOnsetChain(
            boolean candidate,
            ToneFrequencyEstimate toneEstimate,
            long frameLocalToneOnTimestampMs,
            double detectionLevel,
            int releaseThreshold,
            long timestampMs
    ) {
        WeakPostReleaseOnsetChainState currentState = snapshotWeakPostReleaseOnsetChainState();
        WeakPostReleaseOnsetChainLifecycleContext lifecycleContext =
                buildWeakPostReleaseOnsetChainLifecycleContext(
                        candidate,
                        toneEstimate,
                        frameLocalToneOnTimestampMs,
                        detectionLevel,
                        releaseThreshold,
                        timestampMs
                );
        rememberWeakPostReleaseOnsetChainState(
                updatedWeakPostReleaseOnsetChainState(currentState, lifecycleContext)
        );
    }

    private WeakPostReleaseOnsetChainLifecycleContext buildWeakPostReleaseOnsetChainLifecycleContext(
            boolean candidate,
            ToneFrequencyEstimate toneEstimate,
            long frameLocalToneOnTimestampMs,
            double detectionLevel,
            int releaseThreshold,
            long timestampMs
    ) {
        boolean continuationWindowActive = !toneActive
                && isPostReleaseRescueContinuationWindowActive(timestampMs);
        long candidateTimestampMs = frameLocalToneOnTimestampMs >= 0L
                ? Math.min(frameLocalToneOnTimestampMs, timestampMs)
                : timestampMs;
        if (!continuationWindowActive || !candidate) {
            return new WeakPostReleaseOnsetChainLifecycleContext(
                    continuationWindowActive,
                    candidate,
                    false,
                    false,
                    candidateTimestampMs
            );
        }
        return new WeakPostReleaseOnsetChainLifecycleContext(
                true,
                true,
                isTrustedWeakPostReleaseOnsetChainFrame(toneEstimate, timestampMs),
                isCredibleWeakPostReleaseOnsetChainStart(
                        frameLocalToneOnTimestampMs,
                        detectionLevel,
                        releaseThreshold
                ),
                candidateTimestampMs
        );
    }

    private WeakPostReleaseOnsetChainState updatedWeakPostReleaseOnsetChainState(
            WeakPostReleaseOnsetChainState currentState,
            WeakPostReleaseOnsetChainLifecycleContext lifecycleContext
    ) {
        if (!lifecycleContext.continuationWindowActive || !lifecycleContext.candidate) {
            return clearedWeakPostReleaseOnsetChainState();
        }
        WeakPostReleaseOnsetChainState nextState = lifecycleContext.trustedFrame
                ? trustedWeakPostReleaseOnsetChainStateWithCandidateTimestamp(
                currentState,
                lifecycleContext.candidateTimestampMs
        )
                : stateWithTrustedWeakPostReleaseOnsetChainCleared(currentState);
        if (!lifecycleContext.credibleWeakChainStart) {
            return nextState;
        }
        if (nextState.weakOnsetChainStartMs < 0L) {
            return new WeakPostReleaseOnsetChainState(
                    lifecycleContext.candidateTimestampMs,
                    nextState.trustedWeakOnsetChainStartMs,
                    nextState.trustedWeakOnsetChainFrameCount
            );
        }
        return new WeakPostReleaseOnsetChainState(
                Math.min(nextState.weakOnsetChainStartMs, lifecycleContext.candidateTimestampMs),
                nextState.trustedWeakOnsetChainStartMs,
                nextState.trustedWeakOnsetChainFrameCount
        );
    }

    private WeakPostReleaseOnsetChainState trustedWeakPostReleaseOnsetChainStateWithCandidateTimestamp(
            WeakPostReleaseOnsetChainState currentState,
            long candidateTimestampMs
    ) {
        if (currentState.trustedWeakOnsetChainStartMs < 0L) {
            return new WeakPostReleaseOnsetChainState(
                    currentState.weakOnsetChainStartMs,
                    candidateTimestampMs,
                    1
            );
        }
        return new WeakPostReleaseOnsetChainState(
                currentState.weakOnsetChainStartMs,
                Math.min(currentState.trustedWeakOnsetChainStartMs, candidateTimestampMs),
                currentState.trustedWeakOnsetChainFrameCount + 1
        );
    }

    private WeakPostReleaseOnsetChainState snapshotWeakPostReleaseOnsetChainState() {
        return new WeakPostReleaseOnsetChainState(
                postReleaseWeakOnsetChainStartMs,
                postReleaseTrustedWeakOnsetChainStartMs,
                postReleaseTrustedWeakOnsetChainFrameCount
        );
    }

    private PostReleaseContinuityDebtState snapshotPostReleaseContinuityDebtState() {
        return new PostReleaseContinuityDebtState(
                postReleaseRescueContinuationWindowUntilMs,
                postReleaseWeakContinuityRescueCount,
                snapshotWeakPostReleaseOnsetChainState()
        );
    }

    private void rememberWeakPostReleaseOnsetChainState(WeakPostReleaseOnsetChainState state) {
        postReleaseWeakOnsetChainStartMs = state.weakOnsetChainStartMs;
        postReleaseTrustedWeakOnsetChainStartMs = state.trustedWeakOnsetChainStartMs;
        postReleaseTrustedWeakOnsetChainFrameCount = state.trustedWeakOnsetChainFrameCount;
    }

    private void rememberPostReleaseContinuityDebtState(PostReleaseContinuityDebtState state) {
        postReleaseRescueContinuationWindowUntilMs = state.continuationWindowUntilMs;
        postReleaseWeakContinuityRescueCount = state.weakContinuityRescueCount;
        rememberWeakPostReleaseOnsetChainState(state.weakOnsetChainState);
    }

    private WeakPostReleaseOnsetChainState clearedWeakPostReleaseOnsetChainState() {
        return new WeakPostReleaseOnsetChainState(-1L, -1L, 0);
    }

    private PostReleaseContinuityDebtState clearedPostReleaseContinuityDebtState() {
        return new PostReleaseContinuityDebtState(
                -1L,
                0,
                clearedWeakPostReleaseOnsetChainState()
        );
    }

    private PostReleaseContinuityDebtState clearedPostReleaseContinuityDebtStateAfterRunReset() {
        return clearedPostReleaseContinuityDebtState();
    }

    private PostReleaseContinuityDebtState clearedPostReleaseContinuityDebtStateAfterRescueGraduation() {
        return clearedPostReleaseContinuityDebtState();
    }

    private PostReleaseContinuityDebtState stateWithContinuationWindowAndRescueBudgetCleared(
            PostReleaseContinuityDebtState currentState
    ) {
        return new PostReleaseContinuityDebtState(
                -1L,
                0,
                currentState.weakOnsetChainState
        );
    }

    private PostReleaseContinuityDebtState stateWithAcceptedToneOnWeakPostReleaseOnsetChainCleared(
            PostReleaseContinuityDebtState currentState
    ) {
        return new PostReleaseContinuityDebtState(
                currentState.continuationWindowUntilMs,
                currentState.weakContinuityRescueCount,
                clearedWeakPostReleaseOnsetChainState()
        );
    }

    private PostReleaseContinuityDebtState stateWithContinuationWindowUntilMs(
            PostReleaseContinuityDebtState currentState,
            long continuationWindowUntilMs
    ) {
        return new PostReleaseContinuityDebtState(
                continuationWindowUntilMs,
                currentState.weakContinuityRescueCount,
                currentState.weakOnsetChainState
        );
    }

    private PostReleaseContinuityDebtState transitionedPostReleaseContinuityDebtStateAfterAcceptedToneOn(
            PostReleaseContinuityDebtState currentDebtState,
            boolean acceptedByPostReleaseRescue,
            ToneFrequencyEstimate toneEstimate,
            long timestampMs,
            boolean allowStrongRescueDebtReset
    ) {
        if (!acceptedByPostReleaseRescue) {
            return transitionedPostReleaseContinuityDebtStateAfterAcceptedNonRescueToneOn(
                    currentDebtState
            );
        }
        if (!isPostReleaseRescueContinuationWindowActive(timestampMs)) {
            return stateWithWeakContinuityRescueCount(currentDebtState, 0);
        }
        PostReleaseContinuityDebtState nextDebtState = stateWithWeakContinuityRescueCount(
                currentDebtState,
                currentDebtState.weakContinuityRescueCount + 1
        );
        if (allowStrongRescueDebtReset
                && shouldResetWeakPostReleaseContinuityDebtAfterRescue(toneEstimate)) {
            return transitionedPostReleaseContinuityDebtStateAfterStrongRescueDebtReset(
                    nextDebtState
            );
        }
        return nextDebtState;
    }

    private PostReleaseContinuityDebtState transitionedPostReleaseContinuityDebtStateAfterToneOff(
            PostReleaseContinuityDebtState currentDebtState,
            ToneOffPostReleaseDebtContext debtContext
    ) {
        if (debtContext.shouldCarryContinuationWindow) {
            return stateWithContinuationWindowUntilMs(
                    currentDebtState,
                    Math.max(
                            currentDebtState.continuationWindowUntilMs,
                            debtContext.toneEndedAtMs + POST_RELEASE_NEAR_TARGET_CONTINUITY_WINDOW_MS
                    )
            );
        }
        return transitionedPostReleaseContinuityDebtStateAfterToneOffWithoutCarry(
                currentDebtState
        );
    }

    private PostReleaseContinuityDebtState transitionedPostReleaseContinuityDebtStateAfterAcceptedNonRescueToneOn(
            PostReleaseContinuityDebtState currentDebtState
    ) {
        return stateWithContinuationWindowAndRescueBudgetCleared(currentDebtState);
    }

    private PostReleaseContinuityDebtState transitionedPostReleaseContinuityDebtStateAfterStrongRescueDebtReset(
            PostReleaseContinuityDebtState currentDebtState
    ) {
        return stateWithContinuationWindowAndRescueBudgetCleared(currentDebtState);
    }

    private PostReleaseContinuityDebtState transitionedPostReleaseContinuityDebtStateAfterToneOffWithoutCarry(
            PostReleaseContinuityDebtState currentDebtState
    ) {
        return stateWithContinuationWindowAndRescueBudgetCleared(currentDebtState);
    }

    private PostReleaseContinuityDebtState stateWithWeakContinuityRescueCount(
            PostReleaseContinuityDebtState currentState,
            int weakContinuityRescueCount
    ) {
        return new PostReleaseContinuityDebtState(
                currentState.continuationWindowUntilMs,
                weakContinuityRescueCount,
                currentState.weakOnsetChainState
        );
    }

    private WeakPostReleaseOnsetChainState stateWithTrustedWeakPostReleaseOnsetChainCleared(
            WeakPostReleaseOnsetChainState currentState
    ) {
        return new WeakPostReleaseOnsetChainState(
                currentState.weakOnsetChainStartMs,
                -1L,
                0
        );
    }

    private boolean isTrustedWeakPostReleaseOnsetChainFrame(
            ToneFrequencyEstimate toneEstimate,
            long timestampMs
    ) {
        if (toneEstimate == null
                || toneEstimate.frequencyHz <= 0
                || !toneEstimate.locked
                || !isTrackedToneMemoryActive(timestampMs)) {
            return false;
        }
        int continuityAnchorFrequencyHz = continuityAnchorFrequencyHz();
        if (continuityAnchorFrequencyHz <= 0
                || Math.abs(toneEstimate.frequencyHz - continuityAnchorFrequencyHz)
                > POST_RELEASE_WEAK_ONSET_TRUSTED_MAX_ANCHOR_DRIFT_HZ) {
            return false;
        }
        if (!hasTrustedWeakPostReleaseAnchorContext(continuityAnchorFrequencyHz)) {
            return false;
        }
        if (recentLockedFrameRatioFromHistory() < POST_RELEASE_WEAK_ONSET_TRUSTED_MIN_LOCKED_RATIO
                || recentLockedFrameRatioNearFrequencyFromHistory(
                continuityAnchorFrequencyHz,
                AUTO_TRACK_WEAK_VALLEY_RESCUE_NEAR_TARGET_OFFSET_HZ
        )
                < POST_RELEASE_WEAK_ONSET_TRUSTED_MIN_NEAR_TARGET_RATIO) {
            return false;
        }
        if (currentRepresentativeLockedToneFrameCount < POST_RELEASE_WEAK_ONSET_TRUSTED_CHAIN_MIN_FRAMES
                && consecutiveLockedFrames < POST_RELEASE_WEAK_ONSET_TRUSTED_CHAIN_MIN_FRAMES) {
            return false;
        }
        return toneEstimate.toneRmsAmplitude >= (MIN_TRACKED_TONE_RMS * POST_RELEASE_WEAK_ONSET_TRUSTED_MIN_TONE_RMS_RATIO)
                && toneEstimate.dominanceRatio >= POST_RELEASE_WEAK_ONSET_TRUSTED_MIN_DOMINANCE
                && (toneEstimate.isolationRatio >= POST_RELEASE_WEAK_ONSET_TRUSTED_MIN_ISOLATION
                || toneEstimate.localContrastRatio >= POST_RELEASE_WEAK_ONSET_TRUSTED_MIN_LOCAL_CONTRAST);
    }

    private boolean hasTrustedWeakPostReleaseAnchorContext(int continuityAnchorFrequencyHz) {
        return continuityAnchorFrequencyHz > 0
                && representativeLockedToneFrameCount > 0
                && hasStableRepresentativeLockContext()
                && Math.abs(representativeLockedToneFrequencyHz - continuityAnchorFrequencyHz)
                <= REPRESENTATIVE_LOCKED_TONE_CLUSTER_WINDOW_HZ;
    }

    private boolean hasTrustedWeakPostReleaseOnsetChain() {
        return snapshotWeakPostReleaseOnsetChainState().hasTrustedChain();
    }

    private void clearTrustedWeakPostReleaseOnsetChain() {
        rememberWeakPostReleaseOnsetChainState(
                stateWithTrustedWeakPostReleaseOnsetChainCleared(
                        snapshotWeakPostReleaseOnsetChainState()
                )
        );
    }

    private boolean isCredibleWeakPostReleaseOnsetChainStart(
            long frameLocalToneOnTimestampMs,
            double detectionLevel,
            int releaseThreshold
    ) {
        if (frameLocalToneOnTimestampMs >= 0L) {
            return true;
        }
        // A later valid continuity rescue may reuse this timestamp, so do not let
        // sub-release residue become the chain's remembered onset origin.
        return detectionLevel >= releaseThreshold;
    }

    private long weakPostReleaseOnsetChainToneOnTimestampOrFallback(long fallbackTimestampMs) {
        long preferredChainStartMs = snapshotWeakPostReleaseOnsetChainState().preferredChainStartMs();
        if (preferredChainStartMs < 0L || lastEvent == null) {
            return fallbackTimestampMs;
        }
        long backfilledTimestampMs = Math.max(lastEvent.timestampMs(), preferredChainStartMs);
        if (backfilledTimestampMs >= fallbackTimestampMs) {
            return fallbackTimestampMs;
        }
        return backfilledTimestampMs;
    }

    private long postReleaseNearTargetReacquireWindowMs() {
        if (lastEvent == null || lastEvent.type() != CwToneEvent.Type.TONE_OFF) {
            return POST_RELEASE_NEAR_TARGET_REACQUIRE_WINDOW_MIN_MS;
        }
        return Math.min(
                POST_RELEASE_NEAR_TARGET_REACQUIRE_WINDOW_MS,
                Math.max(POST_RELEASE_NEAR_TARGET_REACQUIRE_WINDOW_MIN_MS, lastEvent.toneDurationMs())
        );
    }

    // Test-support path only: replay the same audio while forcing the detector
    // to look at an externally supplied tone frequency for this frame.
    public synchronized List<CwToneEvent> processForcedToneForTesting(AudioFrame frame, int forcedToneFrequencyHz) {
        ArrayList<CwToneEvent> events = new ArrayList<>(1);
        long timestampMs = frame.capturedAtMs();
        handleFrameGapReset(frame, timestampMs, events);
        clearBootstrapDebugFrameState(timestampMs);
        double frameRms = frame.rmsAmplitude();
        double previousFrameRmsAmplitude = lastRmsAmplitude;
        double previousToneRmsAmplitude = lastToneRmsAmplitude;
        boolean hadToneHistoryBeforeFrame = totalToneOnEvents > 0 || totalToneOffEvents > 0;
        boolean hadTrackedToneMemoryBeforeFrame = hadToneHistoryBeforeFrame
                && isTrackedToneMemoryActive(timestampMs);
        int attackAnchorFrequencyHzBeforeFrame = hadToneHistoryBeforeFrame
                ? continuityAttackAnchorFrequencyHz(timestampMs)
                : preferredToneFrequencyHz;
        double attackReferenceToneRmsBeforeFrame = lockedBranchReferenceToneRmsAmplitude;
        short[] samples = frame.samples();
        ToneFrequencyEstimate toneEstimate;
        if (samples == null || samples.length == 0 || frame.sampleRateHz() <= 0) {
            toneEstimate = new ToneFrequencyEstimate(
                    clampPreferredToneFrequency(forcedToneFrequencyHz),
                    0.0d,
                    0.0d,
                    0.0d,
                    0.0d,
                    0.0d,
                    false,
                    0.0d
            );
        } else {
            toneEstimate = evaluateToneEstimate(
                    samples,
                    frame.sampleRateHz(),
                    frameRms,
                    clampPreferredToneFrequency(forcedToneFrequencyHz),
                    0.0d
            );
        }
        double detectionLevel = effectiveDetectionLevel(frameRms, toneEstimate);
        double backgroundObservationLevel = backgroundObservationLevel(frameRms, toneEstimate);
        boolean attackQualified = isNarrowbandQualified(toneEstimate);
        lastTrackedToneMemoryActiveBeforeFrame = hadTrackedToneMemoryBeforeFrame;
        lastAttackAnchorFrequencyHzBeforeFrame = attackAnchorFrequencyHzBeforeFrame;
        lastAttackQualified = attackQualified;
        lastLocalContrastRatio = toneEstimate == null ? 0.0d : toneEstimate.localContrastRatio;
        int attackThreshold = currentThreshold();
        int releaseThreshold = currentReleaseThreshold();

        if (!initialized) {
            noiseFloorEstimate = backgroundObservationLevel;
            signalFloorEstimate = Math.max(backgroundObservationLevel, detectionLevel);
            attackThreshold = currentThreshold();
            releaseThreshold = currentReleaseThreshold();
            initialized = true;
        }

        boolean weakLockedResidueHoldingSignalFloor = isAutoTrackWeakValleyCandidate(
                toneEstimate,
                attackThreshold,
                timestampMs
        );
        if (!toneActive) {
            noiseFloorEstimate = smoothNoiseFloor(noiseFloorEstimate, backgroundObservationLevel);
            signalFloorEstimate = relaxSignalFloorDuringSilence(
                    signalFloorEstimate,
                    Math.max(noiseFloorEstimate, detectionLevel)
            );
        } else if (weakLockedResidueHoldingSignalFloor) {
            // Mirror the live path exactly for test replays: weak same-tone residue
            // must not collapse the tone-active floor and self-rescue the release.
            signalFloorEstimate = Math.max(signalFloorEstimate, detectionLevel);
        } else {
            signalFloorEstimate = smoothSignalFloor(signalFloorEstimate, detectionLevel);
        }

        lastRmsAmplitude = frameRms;
        lastToneRmsAmplitude = toneEstimate.toneRmsAmplitude;
        lastWidebandResidualRmsAmplitude = toneEstimate.widebandResidualRmsAmplitude;
        toneDominanceRatio = toneEstimate.dominanceRatio;
        narrowbandIsolationRatio = toneEstimate.isolationRatio;
        attackThreshold = currentThreshold();
        releaseThreshold = currentReleaseThreshold();
        ToneOnAdmissionContext toneOnContext = buildToneOnAdmissionContext(
                frame,
                toneEstimate,
                attackQualified,
                hadTrackedToneMemoryBeforeFrame,
                attackAnchorFrequencyHzBeforeFrame,
                detectionLevel,
                attackThreshold,
                releaseThreshold,
                frameRms,
                previousFrameRmsAmplitude,
                previousToneRmsAmplitude,
                timestampMs
        );

        if (toneOnContext.shouldClearPendingFarAttackCandidate(
                toneActive,
                detectionLevel,
                attackThreshold
        )) {
            clearPendingFarAttackCandidate();
        }

        rememberPreAttemptToneOnDecision(toneOnContext, toneActive, detectionLevel, attackThreshold);

        if (!toneActive && toneOnContext.shouldAttemptToneOn(detectionLevel, attackThreshold)) {
            ToneOnAttemptFrontGateResolution frontGateResolution =
                    buildToneOnAttemptFrontGateResolution(
                            toneOnContext,
                            toneEstimate,
                            hadToneHistoryBeforeFrame,
                            hadTrackedToneMemoryBeforeFrame,
                            attackAnchorFrequencyHzBeforeFrame,
                            attackReferenceToneRmsBeforeFrame,
                            timestampMs,
                            false
                    );
            if (consumeBlockedToneOnAttemptFrontGateResolution(
                    frontGateResolution,
                    ToneOnAttemptFrameSideEffectMode.REPLAY_ONLY,
                    timestampMs,
                    detectionLevel
            )) {
                return events;
            }
            ToneOnAttemptResolution toneOnAttemptResolution = buildToneOnAttemptResolution(
                    toneOnContext,
                    toneEstimate,
                    frameRms,
                    previousFrameRmsAmplitude,
                    previousToneRmsAmplitude,
                    attackThreshold,
                    releaseThreshold,
                    detectionLevel,
                    timestampMs
            );
            if (consumeBlockedToneOnAttemptResolution(
                    toneOnAttemptResolution,
                    ToneOnAttemptFrameSideEffectMode.REPLAY_ONLY,
                    timestampMs,
                    detectionLevel
            )) {
                return events;
            }
            consumeAcceptedToneOnAttempt(
                    toneOnContext,
                    toneOnAttemptResolution.toneOnTimestampMs,
                    hadTrackedToneMemoryBeforeFrame,
                    detectionLevel,
                    frame.peakAmplitude(),
                    toneEstimate,
                    timestampMs,
                    events,
                    false,
                    ToneOnAcceptedFrameSideEffectMode.REPLAY_ONLY
            );
            return events;
        }

        if (toneActive) {
            ToneActiveReleaseContext toneActiveReleaseContext = buildToneActiveReleaseContext(
                    frame,
                    toneEstimate,
                    attackThreshold,
                    releaseThreshold,
                    timestampMs
            );
            if (handleToneActiveReleaseFrame(
                    frame,
                    toneEstimate,
                    toneActiveReleaseContext,
                    attackThreshold,
                    detectionLevel,
                    backgroundObservationLevel,
                    timestampMs,
                    events,
                    ToneActiveReleaseFrameSideEffectMode.REPLAY_ONLY
            )) {
                return events;
            }
        }

        rememberNoEventToneOnFrame(false, timestampMs, detectionLevel);
        return events;
    }

    public synchronized void reset() {
        initialized = false;
        toneActive = false;
        targetToneLocked = false;
        noiseFloorEstimate = 0.0d;
        signalFloorEstimate = 0.0d;
        lastRmsAmplitude = 0.0d;
        lastToneRmsAmplitude = 0.0d;
        lastWidebandResidualRmsAmplitude = 0.0d;
        toneDominanceRatio = 0.0d;
        narrowbandIsolationRatio = 0.0d;
        peakToneRmsAmplitude = 0.0d;
        peakNarrowbandIsolationRatio = 0.0d;
        lastDetectionLevel = 0.0d;
        lockedBranchReferenceToneRmsAmplitude = 0.0d;
        representativeLockedToneScore = 0.0d;
        currentRepresentativeLockedToneScore = 0.0d;
        toneHypothesisConfidence = 0.0d;
        toneHypothesisTotalEvidence = 0.0d;
        lastFrameTimestampMs = -1L;
        lastTrackedToneTimestampMs = -1L;
        toneStartedAtMs = -1L;
        silenceStartedAtMs = -1L;
        clearCurrentToneRunLifecycleState();
        trackingState = TrackingState.SEARCH;
        totalToneOnEvents = 0;
        totalToneOffEvents = 0;
        processedFrameCount = 0;
        lockedFrameCount = 0;
        toneActiveFrameCount = 0;
        toneActiveUnlockedFrameCount = 0;
        consecutiveLockedFrames = 0;
        maxConsecutiveLockedFrames = 0;
        consecutiveToneActiveUnlockedFrames = 0;
        maxConsecutiveToneActiveUnlockedFrames = 0;
        recentHistoryFrameCount = 0;
        recentHistoryNextIndex = 0;
        representativeLockedToneFrequencyHz = preferredToneFrequencyHz;
        representativeLockedToneFrameCount = 0;
        currentRepresentativeLockedToneFrequencyHz = preferredToneFrequencyHz;
        currentRepresentativeLockedToneFrameCount = 0;
        toneHypothesisFrequencyHz = preferredToneFrequencyHz;
        toneHypothesisSupportFrames = 0;
        toneHypothesisIdleFrames = 0;
        pendingHypothesisGuardFrequencyHz = 0;
        totalHypothesisGuardApplyCount = 0;
        lastHypothesisGuardAppliedFrequencyHz = 0;
        targetToneFrequencyHz = preferredToneFrequencyHz;
        pendingRetuneCandidateFrequencyHz = preferredToneFrequencyHz;
        pendingRetuneCandidateStableScans = 0;
        pendingFarAttackCandidateFrequencyHz = 0;
        pendingFarAttackCandidateStableFrames = 0;
        pendingLockedRetuneFrequencyHz = preferredToneFrequencyHz;
        pendingLockedRetuneStableScans = 0;
        autoTrackWeakValleyBridgeFramesRemaining = 0;
        autoTrackReleaseTailHoldFramesRemaining = 0;
        currentToneRunWeakBootstrapReleaseTailHoldCount = 0;
        autoTrackWeakValleyBridgeActive = false;
        autoTrackReleaseTailHoldActive = false;
        autoTrackReleaseTailHoldExtendedUntilMs = -1L;
        lockLossFrames = 0;
        frameGapResetCount = 0;
        lastPreferredWindowWinnerFrequencyHz = preferredToneFrequencyHz;
        lastWideScanWinnerFrequencyHz = 0;
        lastAcquisitionWinnerFrequencyHz = preferredToneFrequencyHz;
        lastFinalAdoptedFrequencyHz = preferredToneFrequencyHz;
        lastFrameGapMs = 0L;
        lastFrameGapResetThresholdMs = 0L;
        worstFrameGapMs = 0L;
        lastFrameGapResetAtMs = -1L;
        lastPreferredWindowWinnerToneRms = 0.0d;
        lastWideScanWinnerToneRms = 0.0d;
        lastAcquisitionWinnerToneRms = 0.0d;
        lastFinalAdoptedToneRms = 0.0d;
        lastPreferredWindowWinnerSelectionScore = 0.0d;
        lastWideScanWinnerSelectionScore = 0.0d;
        lastAcquisitionWinnerSelectionScore = 0.0d;
        lastFinalAdoptedSelectionScore = 0.0d;
        lastPreferredWindowWinnerConfidence = 0.0d;
        lastWideScanWinnerConfidence = 0.0d;
        lastAcquisitionWinnerConfidence = 0.0d;
        lastFinalAdoptedConfidence = 0.0d;
        lastPreviousTargetToneRms = 0.0d;
        lastPreviousTargetSelectionScore = 0.0d;
        lastPreferredWindowRunnerUpFrequencyHz = 0;
        lastWideScanRunnerUpFrequencyHz = 0;
        lastAcquisitionRunnerUpFrequencyHz = 0;
        lastPreviousTargetFrequencyHz = preferredToneFrequencyHz;
        lastPreferredWindowRunnerUpSelectionScore = 0.0d;
        lastWideScanRunnerUpSelectionScore = 0.0d;
        lastAcquisitionRunnerUpSelectionScore = 0.0d;
        lastPreferredWindowWinnerLocked = false;
        lastWideScanWinnerLocked = false;
        lastAcquisitionWinnerLocked = false;
        lastFinalAdoptedLocked = false;
        lastPreviousTargetLocked = false;
        lastAcquisitionWinnerSource = AcquisitionWinnerSource.NONE;
        lastFinalAdoptedSource = AcquisitionWinnerSource.NONE;
        lastAcquisitionDecisionDetail = "NONE";
        lastFinalAdoptionDetail = "NONE";
        lastPreferredWindowTopCandidatesSummary = "NONE";
        lastWideScanTopCandidatesSummary = "NONE";
        toneHypothesisSource = "NONE";
        lastHypothesisGuardDecision = "DISABLED";
        clearPosterior();
        clearRecentHypothesisHistory();
        clearHistogram(activePreferredWinnerHistogram);
        clearHistogram(activeWideWinnerHistogram);
        clearHistogram(activeAcquisitionWinnerHistogram);
        clearHistogram(activeLiveTargetHistogram);
        clearHistogram(activeHypothesisHistogram);
        activeWindowObservationCount = 0;
        activePreferredWinnerObservationCount = 0;
        activeWideWinnerObservationCount = 0;
        activeAcquisitionWinnerObservationCount = 0;
        activeLiveTargetObservationCount = 0;
        activeHypothesisObservationCount = 0;
        representativeCompetitionObservationCount = 0;
        representativeCompetitionTrackedWinFrames = 0;
        representativeCompetitionHypothesisWinFrames = 0;
        representativeCompetitionTieFrames = 0;
        representativeCompetitionHypothesisCurrentWinStreak = 0;
        representativeCompetitionHypothesisMaxWinStreak = 0;
        activeCenterCompetitionObservationCount = 0;
        activeCenterCompetitionTrackedWinFrames = 0;
        activeCenterCompetitionHypothesisWinFrames = 0;
        activeCenterCompetitionTieFrames = 0;
        activeCenterCompetitionHypothesisCurrentWinStreak = 0;
        activeCenterCompetitionHypothesisMaxWinStreak = 0;
        recentHypothesisHistoryCount = 0;
        recentHypothesisHistoryNextIndex = 0;
        experimentalHypothesisGuardEnabled = false;
        experimentalForceWideAcquisitionEnabled = false;
        experimentalLockedRetuneGuardTuning = null;
        pendingHypothesisGuardEligible = false;
        lastHypothesisGuardEligible = false;
        lastHypothesisGuardApplied = false;
        hypothesisGuardAppliedThisFrame = false;
        hypothesisGuardOverrideAppliedThisFrame = false;
        lockedRetuneGuardHoldingThisFrame = false;
        lastEvent = null;
        hypothesisGuardOverrideRunnerUpEstimate = null;
        hypothesisGuardOverrideConfidence = 0.0d;
        clearLockedRetuneGuardFrameState();
        clearBootstrapDebugFrameState(-1L);
    }

    public synchronized void setPreferredToneFrequencyHz(int preferredToneFrequencyHz) {
        int clamped = clampPreferredToneFrequency(preferredToneFrequencyHz);
        this.preferredToneFrequencyHz = clamped;
        // Preferred tone may seed the very first target before any evidence exists.
        // Once frames have been processed, changing this setting must not yank the
        // tracked target onto the new preferred tone.
        if (!targetToneLocked && processedFrameCount == 0) {
            this.targetToneFrequencyHz = clamped;
        }
        this.pendingRetuneCandidateFrequencyHz = targetToneLocked
                ? pendingRetuneCandidateFrequencyHz
                : targetToneFrequencyHz;
        this.pendingRetuneCandidateStableScans = 0;
        this.pendingFarAttackCandidateFrequencyHz = 0;
        this.pendingFarAttackCandidateStableFrames = 0;
    }

    public synchronized void setSqlPercent(int sqlPercent) {
        this.sqlPercent = SqlThresholdModel.clampSqlPercent(sqlPercent);
    }

    public synchronized void setFixedToneLearningWindowHz(int windowHz) {
        fixedToneLearningWindowHz = clampFixedToneLearningWindowHz(windowHz);
    }

    public synchronized int fixedToneLearningWindowHz() {
        return fixedToneLearningWindowHz;
    }

    public synchronized void setRxToneMode(RxToneMode mode) {
        rxToneMode = mode == null ? RxToneMode.AUTO_TRACK : mode;
    }

    public static int clampFixedToneLearningWindowHz(int windowHz) {
        return Math.max(
                MIN_FIXED_TONE_LEARNING_WINDOW_HZ,
                Math.min(MAX_FIXED_TONE_LEARNING_WINDOW_HZ, windowHz)
        );
    }

    public synchronized void setExperimentalHypothesisGuardEnabled(boolean enabled) {
        experimentalHypothesisGuardEnabled = enabled;
        if (!enabled) {
            pendingHypothesisGuardEligible = false;
            pendingHypothesisGuardFrequencyHz = 0;
            lastHypothesisGuardEligible = false;
            lastHypothesisGuardApplied = false;
            lastHypothesisGuardAppliedFrequencyHz = 0;
            hypothesisGuardAppliedThisFrame = false;
            hypothesisGuardOverrideAppliedThisFrame = false;
            hypothesisGuardOverrideRunnerUpEstimate = null;
            hypothesisGuardOverrideConfidence = 0.0d;
            lastHypothesisGuardDecision = "DISABLED";
        }
    }

    public synchronized void setExperimentalForceWideAcquisitionEnabled(boolean enabled) {
        experimentalForceWideAcquisitionEnabled = enabled;
    }

    public synchronized void setExperimentalLockedRetuneGuardTuning(
            ExperimentalLockedRetuneGuardTuning tuning
    ) {
        experimentalLockedRetuneGuardTuning = tuning;
    }

    public synchronized double lastDetectionLevel() {
        return lastDetectionLevel;
    }

    public synchronized boolean weakValleyBridgeActive() {
        return autoTrackWeakValleyBridgeActive;
    }

    public synchronized int weakValleyBridgeFramesRemaining() {
        return autoTrackWeakValleyBridgeFramesRemaining;
    }

    public synchronized boolean lastAttackQualified() {
        return lastAttackQualified;
    }

    public synchronized boolean lastTrackedToneMemoryActiveBeforeFrame() {
        return lastTrackedToneMemoryActiveBeforeFrame;
    }

    public synchronized int lastAttackAnchorFrequencyHzBeforeFrame() {
        return lastAttackAnchorFrequencyHzBeforeFrame;
    }

    public synchronized int lastToneOnThreshold() {
        return lastToneOnThreshold;
    }

    public synchronized long lastFrameLocalToneOnTimestampMs() {
        return lastFrameLocalToneOnTimestampMs;
    }

    public synchronized long lastPostReleaseGapMs() {
        return lastPostReleaseGapMs;
    }

    public synchronized long lastPostReleaseWindowMs() {
        return lastPostReleaseWindowMs;
    }

    public synchronized double lastLocalContrastRatio() {
        return lastLocalContrastRatio;
    }

    public synchronized boolean lastSteadyLateGapNearTargetRescueCandidate() {
        return lastSteadyLateGapNearTargetRescueCandidate;
    }

    public synchronized boolean lastWeakPostReleaseOnsetChainCandidate() {
        return lastWeakPostReleaseOnsetChainCandidate;
    }

    public synchronized boolean lastTrustedContinuityToneOnCandidate() {
        return lastTrustedContinuityToneOnCandidate;
    }

    public synchronized boolean lastLowGrowthStrongSteadyNearTargetRescue() {
        return lastLowGrowthStrongSteadyNearTargetRescue;
    }

    public synchronized boolean lastNearTargetPostReleaseToneOnRescue() {
        return lastNearTargetPostReleaseToneOnRescue;
    }

    public synchronized boolean lastPostReleaseSteadyCarrierSuppressed() {
        return lastPostReleaseSteadyCarrierSuppressed;
    }

    public synchronized boolean lastFarAttackToneOnDelayed() {
        return lastFarAttackToneOnDelayed;
    }

    public synchronized boolean lastToneOnAccepted() {
        return lastToneOnAccepted;
    }

    public synchronized boolean lastToneOnAcceptedByRescue() {
        return lastToneOnAcceptedByRescue;
    }

    public synchronized boolean lastReleaseTailHoldApplied() {
        return lastReleaseTailHoldApplied;
    }

    public synchronized int lastToneActiveReleaseThreshold() {
        return lastToneActiveReleaseThreshold;
    }

    public synchronized double lastReleaseTailHoldRequiredDetectionThreshold() {
        return lastReleaseTailHoldRequiredDetectionThreshold;
    }

    public synchronized boolean lastReleaseTailHoldSufficientRecentTrust() {
        return lastReleaseTailHoldSufficientRecentTrust;
    }

    public synchronized boolean lastReleaseTailHoldCurrentRunStableBootstrapEligible() {
        return lastReleaseTailHoldCurrentRunStableBootstrapEligible;
    }

    public synchronized boolean lastReleaseTailHoldCurrentRunWeakBootstrapEligible() {
        return lastReleaseTailHoldCurrentRunWeakBootstrapEligible;
    }

    public synchronized boolean currentToneStartedByPostReleaseRescue() {
        return currentToneStartedByPostReleaseRescue;
    }

    public synchronized boolean postReleaseRescueContinuationWindowActive(long timestampMs) {
        return isPostReleaseRescueContinuationWindowActive(timestampMs);
    }

    public synchronized long postReleaseRescueContinuationWindowRemainingMs(long timestampMs) {
        if (!isPostReleaseRescueContinuationWindowActive(timestampMs)) {
            return 0L;
        }
        return Math.max(0L, postReleaseRescueContinuationWindowUntilMs - timestampMs);
    }

    public synchronized int postReleaseWeakContinuityRescueCount() {
        return postReleaseWeakContinuityRescueCount;
    }

    public synchronized boolean trustedWeakPostReleaseOnsetChainActive() {
        return hasTrustedWeakPostReleaseOnsetChain();
    }

    public synchronized int trustedWeakPostReleaseOnsetChainFrameCount() {
        return postReleaseTrustedWeakOnsetChainFrameCount;
    }

    public synchronized long trustedWeakPostReleaseOnsetChainStartMs() {
        return postReleaseTrustedWeakOnsetChainStartMs;
    }

    public synchronized long postReleaseWeakContinuityGapLimitMs() {
        return POST_RELEASE_WEAK_ONSET_CONTINUITY_MAX_GAP_MS;
    }

    public synchronized int currentToneRunWeakBootstrapReleaseTailHoldCount() {
        return currentToneRunWeakBootstrapReleaseTailHoldCount;
    }

    public synchronized String lastPostReleaseRescueDecision() {
        return lastPostReleaseRescueDecision;
    }

    public synchronized String lastPostReleaseSuppressionDecision() {
        return lastPostReleaseSuppressionDecision;
    }

    public synchronized String lastFarAttackDelayDecision() {
        return lastFarAttackDelayDecision;
    }

    public synchronized String lastToneOnDecision() {
        return lastToneOnDecision;
    }

    public synchronized String lastReleaseTailHoldDecision() {
        return lastReleaseTailHoldDecision;
    }

    public synchronized CwSignalSnapshot snapshot() {
        return new CwSignalSnapshot(
                recentHistoryFrameCount,
                orderedRecentFrontEndStateHistory(),
                orderedRecentTrackingOffsetHistoryHz(),
                toneActive,
                targetToneLocked,
                preferredToneFrequencyHz,
                targetToneFrequencyHz,
                acquisitionReferenceFrequencyHz(),
                toneHypothesisFrequencyHz,
                toneHypothesisConfidence,
                toneHypothesisSupportFrames,
                toneHypothesisIdleFrames,
                toneHypothesisSource,
                experimentalHypothesisGuardEnabled,
                lastHypothesisGuardEligible,
                lastHypothesisGuardApplied,
                lastHypothesisGuardAppliedFrequencyHz,
                totalHypothesisGuardApplyCount,
                hypothesisGuardHistorySpanHz(),
                lastHypothesisGuardDecision,
                representativeLockedToneFrequencyHz,
                representativeLockedToneFrameCount,
                activeWindowObservationCount,
                histogramLeaderFrequencyHz(activeAcquisitionWinnerHistogram),
                histogramLeaderHitCount(activeAcquisitionWinnerHistogram),
                activeHypothesisObservationCount,
                histogramLeaderFrequencyHz(activeHypothesisHistogram),
                histogramLeaderHitCount(activeHypothesisHistogram),
                representativeCompetitionObservationCount,
                representativeCompetitionTrackedWinFrames,
                representativeCompetitionHypothesisWinFrames,
                representativeCompetitionTieFrames,
                representativeCompetitionHypothesisCurrentWinStreak,
                representativeCompetitionHypothesisMaxWinStreak,
                activeCenterCompetitionObservationCount,
                activeCenterCompetitionTrackedWinFrames,
                activeCenterCompetitionHypothesisWinFrames,
                activeCenterCompetitionTieFrames,
                activeCenterCompetitionHypothesisCurrentWinStreak,
                activeCenterCompetitionHypothesisMaxWinStreak,
                currentThreshold(),
                currentReleaseThreshold(),
                (int) Math.round(noiseFloorEstimate),
                (int) Math.round(signalFloorEstimate),
                lastRmsAmplitude,
                lastToneRmsAmplitude,
                lastWidebandResidualRmsAmplitude,
                toneDominanceRatio,
                narrowbandIsolationRatio,
                peakToneRmsAmplitude,
                peakNarrowbandIsolationRatio,
                processedFrameCount,
                lockedFrameCount,
                toneActiveFrameCount,
                toneActiveUnlockedFrameCount,
                consecutiveLockedFrames,
                maxConsecutiveLockedFrames,
                consecutiveToneActiveUnlockedFrames,
                maxConsecutiveToneActiveUnlockedFrames,
                pendingRetuneCandidateFrequencyHz,
                pendingRetuneCandidateStableScans,
                lockedRetuneGuardHoldingThisFrame,
                lockedRetuneGuardCandidateFrequencyHz,
                lockedRetuneGuardDriftHz,
                lockedRetuneGuardObservedScans,
                lockedRetuneGuardRequiredScans,
                lockedRetuneGuardRemainingScans,
                lockedRetuneGuardBand,
                lastPreferredWindowWinnerFrequencyHz,
                lastWideScanWinnerFrequencyHz,
                lastAcquisitionWinnerFrequencyHz,
                lastFinalAdoptedFrequencyHz,
                lastPreferredWindowWinnerToneRms,
                lastWideScanWinnerToneRms,
                lastAcquisitionWinnerToneRms,
                lastFinalAdoptedToneRms,
                lastPreferredWindowWinnerSelectionScore,
                lastWideScanWinnerSelectionScore,
                lastAcquisitionWinnerSelectionScore,
                lastFinalAdoptedSelectionScore,
                lastPreferredWindowWinnerConfidence,
                lastWideScanWinnerConfidence,
                lastAcquisitionWinnerConfidence,
                lastFinalAdoptedConfidence,
                lastPreferredWindowRunnerUpFrequencyHz,
                lastWideScanRunnerUpFrequencyHz,
                lastAcquisitionRunnerUpFrequencyHz,
                lastPreferredWindowRunnerUpSelectionScore,
                lastWideScanRunnerUpSelectionScore,
                lastAcquisitionRunnerUpSelectionScore,
                lastPreviousTargetFrequencyHz,
                lastPreviousTargetToneRms,
                lastPreviousTargetSelectionScore,
                lastPreviousTargetLocked,
                lastPreferredWindowWinnerLocked,
                lastWideScanWinnerLocked,
                lastAcquisitionWinnerLocked,
                lastFinalAdoptedLocked,
                lastAcquisitionWinnerSource.name(),
                lastFinalAdoptedSource.name(),
                lastAcquisitionDecisionDetail,
                lastFinalAdoptionDetail,
                lastPreferredWindowTopCandidatesSummary,
                lastWideScanTopCandidatesSummary,
                totalToneOnEvents,
                totalToneOffEvents,
                frameGapResetCount,
                lastFrameGapMs,
                lastFrameGapResetThresholdMs,
                worstFrameGapMs,
                lastFrameGapResetAtMs,
                lastEvent
        );
    }

    public synchronized String debugAcquisitionProfile(AudioFrame frame, int[] frequenciesHz) {
        if (frame == null || frequenciesHz == null || frequenciesHz.length == 0) {
            return "No debug acquisition profile available.";
        }
        short[] samples = frame.samples();
        if (samples == null || samples.length == 0 || frame.sampleRateHz() <= 0) {
            return "Empty frame.";
        }
        boolean lockRetentionMode = trackingState == TrackingState.LOCKED;
        boolean acquisitionMode = !lockRetentionMode;
        int previousTargetToneFrequencyHz = targetToneFrequencyHz;
        StringBuilder builder = new StringBuilder()
                .append("prevTarget=").append(previousTargetToneFrequencyHz).append("Hz")
                .append(", pref=").append(preferredToneFrequencyHz).append("Hz")
                .append(", ref=").append(acquisitionReferenceFrequencyHz()).append("Hz")
                .append(", hyp=").append(toneHypothesisSupportFrames > 0
                        ? toneHypothesisFrequencyHz + "Hz@" + Math.round(toneHypothesisConfidence * 100.0d) + "%"
                        : "NONE")
                .append(", rms=").append(String.format(Locale.US, "%.1f", frame.rmsAmplitude()))
                .append(", mode=").append(acquisitionMode ? "acquisition" : "locked-retune");
        for (int frequencyHz : frequenciesHz) {
            ToneFrequencyEstimate estimate = evaluateToneEstimate(
                    samples,
                    frame.sampleRateHz(),
                    frame.rmsAmplitude(),
                    frequencyHz,
                    0.0d
            );
            double weightedScore = scoreToneCandidate(
                    frequencyHz,
                    previousTargetToneFrequencyHz,
                    lockRetentionMode,
                    estimate,
                    acquisitionMode
            ) * estimate.toneRmsAmplitude;
            double evidenceScore = acquisitionEvidenceScore(estimate);
            builder.append("\n  ")
                    .append(frequencyHz).append("Hz")
                    .append(" rms=").append(String.format(Locale.US, "%.1f", estimate.toneRmsAmplitude))
                    .append(" dom=").append(Math.round(estimate.dominanceRatio * 100.0d)).append('%')
                    .append(" iso=").append(Math.round(estimate.isolationRatio * 100.0d)).append('%')
                    .append(" ctr=").append(Math.round(estimate.localContrastRatio * 100.0d)).append('%')
                    .append(" eval=").append(String.format(Locale.US, "%.2f", evidenceScore))
                    .append(" score=").append(String.format(Locale.US, "%.2f", weightedScore))
                    .append(estimate.locked ? " LOCK" : " cand");
        }
        return builder.toString();
    }

    public synchronized String debugActiveLeaderSummary() {
        if (activeWindowObservationCount <= 0) {
            return "Active leader stats: no active-window observations yet.";
        }
        return new StringBuilder()
                .append("Active leader stats: frames=").append(activeWindowObservationCount)
                .append("\n  pref: ").append(renderLeaderHistogramSummary(
                        activePreferredWinnerHistogram,
                        activePreferredWinnerObservationCount
                ))
                .append("\n  wide: ").append(renderLeaderHistogramSummary(
                        activeWideWinnerHistogram,
                        activeWideWinnerObservationCount
                ))
                .append("\n  acq: ").append(renderLeaderHistogramSummary(
                        activeAcquisitionWinnerHistogram,
                        activeAcquisitionWinnerObservationCount
                ))
                .append("\n  live: ").append(renderLeaderHistogramSummary(
                        activeLiveTargetHistogram,
                        activeLiveTargetObservationCount
                ))
                .append("\n  hyp: ").append(renderLeaderHistogramSummary(
                        activeHypothesisHistogram,
                        activeHypothesisObservationCount
                ))
                .append("\n  rep-comp: ").append(renderCompetitionSummary(
                        representativeCompetitionObservationCount,
                        representativeCompetitionTrackedWinFrames,
                        representativeCompetitionHypothesisWinFrames,
                        representativeCompetitionTieFrames,
                        representativeCompetitionHypothesisCurrentWinStreak,
                        representativeCompetitionHypothesisMaxWinStreak
                ))
                .append("\n  act-comp: ").append(renderCompetitionSummary(
                        activeCenterCompetitionObservationCount,
                        activeCenterCompetitionTrackedWinFrames,
                        activeCenterCompetitionHypothesisWinFrames,
                        activeCenterCompetitionTieFrames,
                        activeCenterCompetitionHypothesisCurrentWinStreak,
                        activeCenterCompetitionHypothesisMaxWinStreak
                ))
                .toString();
    }

    public synchronized String debugActiveLeaderCompactSummary() {
        if (activeWindowObservationCount <= 0) {
            return "no active-window observations";
        }
        return "frames=" + activeWindowObservationCount
                + " | acq " + renderCompactBandSummary(
                activeAcquisitionWinnerHistogram,
                activeAcquisitionWinnerObservationCount
        )
                + " | hyp " + renderCompactBandSummary(
                activeHypothesisHistogram,
                activeHypothesisObservationCount
        )
                + " | rep-comp "
                + renderCompactCompetitionSummary(
                representativeCompetitionObservationCount,
                representativeCompetitionTrackedWinFrames,
                representativeCompetitionHypothesisWinFrames,
                representativeCompetitionTieFrames,
                representativeCompetitionHypothesisMaxWinStreak
        )
                + " | act-comp "
                + renderCompactCompetitionSummary(
                activeCenterCompetitionObservationCount,
                activeCenterCompetitionTrackedWinFrames,
                activeCenterCompetitionHypothesisWinFrames,
                activeCenterCompetitionTieFrames,
                activeCenterCompetitionHypothesisMaxWinStreak
        )
                + " | live " + renderCompactBandSummary(
                activeLiveTargetHistogram,
                activeLiveTargetObservationCount
        );
    }

    private int currentThreshold() {
        return SqlThresholdModel.effectiveAttackThreshold(
                sqlPercent,
                noiseFloorEstimate,
                signalFloorEstimate,
                lastToneRmsAmplitude
        );
    }

    private int currentReleaseThreshold() {
        return SqlThresholdModel.effectiveReleaseThreshold(
                sqlPercent,
                currentThreshold(),
                noiseFloorEstimate
        );
    }

    private double lowSqlRelaxationRatio() {
        if (sqlPercent >= DEFAULT_SQL_PERCENT) {
            return 0.0d;
        }
        return Math.max(
                0.0d,
                Math.min(1.0d, (DEFAULT_SQL_PERCENT - sqlPercent) / (double) DEFAULT_SQL_PERCENT)
        );
    }

    private int effectiveMinTrackedToneRmsForQualification() {
        double relaxedThreshold = MIN_TRACKED_TONE_RMS * (1.0d - (0.25d * lowSqlRelaxationRatio()));
        return Math.max(72, (int) Math.round(relaxedThreshold));
    }

    private double effectiveMinNarrowbandDominanceRatio() {
        return MIN_NARROWBAND_DOMINANCE_RATIO * (1.0d - (0.25d * lowSqlRelaxationRatio()));
    }

    private double effectiveMinNarrowbandIsolationRatio() {
        return MIN_NARROWBAND_ISOLATION_RATIO * (1.0d - (0.25d * lowSqlRelaxationRatio()));
    }

    private double effectiveMinNarrowbandLocalContrastRatio() {
        return MIN_NARROWBAND_LOCAL_CONTRAST_RATIO * (1.0d - (0.16d * lowSqlRelaxationRatio()));
    }

    private double smoothNoiseFloor(double currentFloor, double frameRms) {
        double smoothing = frameRms <= currentFloor
                ? NOISE_FLOOR_DROP_SMOOTHING
                : NOISE_FLOOR_RISE_SMOOTHING;
        return currentFloor + ((frameRms - currentFloor) * smoothing);
    }

    private double smoothSignalFloor(double currentSignal, double frameRms) {
        if (currentSignal <= 0.0d) {
            return frameRms;
        }
        return currentSignal + ((frameRms - currentSignal) * SIGNAL_FLOOR_SMOOTHING);
    }

    private double relaxSignalFloorDuringSilence(double currentSignal, double targetFloor) {
        double safeTargetFloor = Math.max(0.0d, targetFloor);
        if (currentSignal <= 0.0d) {
            return safeTargetFloor;
        }
        double smoothing = safeTargetFloor >= currentSignal
                ? SIGNAL_FLOOR_IDLE_RISE_SMOOTHING
                : SIGNAL_FLOOR_IDLE_DECAY_SMOOTHING;
        return currentSignal + ((safeTargetFloor - currentSignal) * smoothing);
    }

    private void handleFrameGapReset(
            AudioFrame frame,
            long timestampMs,
            List<CwToneEvent> events
    ) {
        if (lastFrameTimestampMs < 0L || timestampMs <= lastFrameTimestampMs) {
            return;
        }
        long gapMs = timestampMs - lastFrameTimestampMs;
        long expectedFrameDurationMs = estimateFrameDurationMs(frame);
        long resetThresholdMs = Math.max(
                FRAME_GAP_RESET_MIN_MS,
                Math.round(expectedFrameDurationMs * FRAME_GAP_RESET_MULTIPLIER)
        );
        lastFrameGapMs = gapMs;
        lastFrameGapResetThresholdMs = resetThresholdMs;
        worstFrameGapMs = Math.max(worstFrameGapMs, gapMs);
        if (gapMs < resetThresholdMs) {
            return;
        }
        frameGapResetCount += 1;
        lastFrameGapResetAtMs = timestampMs;

        if (toneActive && toneStartedAtMs >= 0L) {
            long toneEndedAtMs = Math.max(
                    toneStartedAtMs,
                    lastFrameTimestampMs + expectedFrameDurationMs
            );
            long durationMs = Math.max(0L, toneEndedAtMs - toneStartedAtMs);
            CwToneEvent event = new CwToneEvent(
                    CwToneEvent.Type.TONE_OFF,
                    toneEndedAtMs,
                    0,
                    0.0d,
                    durationMs
            );
            lastEvent = event;
            totalToneOffEvents += 1;
            events.add(event);
        }

        toneActive = false;
        targetToneLocked = false;
        toneStartedAtMs = -1L;
        silenceStartedAtMs = -1L;
        clearCurrentToneRunLifecycleState();
        lastTrackedToneTimestampMs = -1L;
        lockedBranchReferenceToneRmsAmplitude = 0.0d;
        trackingState = TrackingState.SEARCH;
        signalFloorEstimate = Math.max(0.0d, noiseFloorEstimate);
        consecutiveLockedFrames = 0;
        consecutiveToneActiveUnlockedFrames = 0;
        pendingRetuneCandidateFrequencyHz = targetToneFrequencyHz;
        pendingRetuneCandidateStableScans = 0;
        lockLossFrames = 0;
    }

    private long estimateFrameDurationMs(AudioFrame frame) {
        if (frame == null || frame.sampleRateHz() <= 0 || frame.sampleCount() <= 0) {
            return 0L;
        }
        return Math.max(1L, Math.round(frame.sampleCount() * 1000.0d / frame.sampleRateHz()));
    }

    private long estimateCrossingTimestamp(
            long currentTimestampMs,
            double threshold,
            double currentDetectionLevel,
            boolean risingEdge
    ) {
        if (lastFrameTimestampMs < 0L || currentTimestampMs <= lastFrameTimestampMs) {
            return currentTimestampMs;
        }

        double previousLevel = Math.max(0.0d, lastDetectionLevel);
        double currentLevel = Math.max(0.0d, currentDetectionLevel);
        if (risingEdge) {
            if (currentLevel <= previousLevel) {
                return currentTimestampMs;
            }
            return interpolateTimestamp(lastFrameTimestampMs, currentTimestampMs, previousLevel, currentLevel, threshold);
        }

        if (currentLevel >= previousLevel) {
            return currentTimestampMs;
        }
        return interpolateTimestamp(lastFrameTimestampMs, currentTimestampMs, previousLevel, currentLevel, threshold);
    }

    private long interpolateTimestamp(
            long previousTimestampMs,
            long currentTimestampMs,
            double previousLevel,
            double currentLevel,
            double threshold
    ) {
        double denominator = currentLevel - previousLevel;
        if (Math.abs(denominator) < 0.0001d) {
            return currentTimestampMs;
        }
        double fraction = (threshold - previousLevel) / denominator;
        fraction = Math.max(0.0d, Math.min(1.0d, fraction));
        long deltaMs = currentTimestampMs - previousTimestampMs;
        return previousTimestampMs + Math.round(deltaMs * fraction);
    }

    private void clearBootstrapDebugFrameState(long timestampMs) {
        lastAttackQualified = false;
        lastTrackedToneMemoryActiveBeforeFrame = false;
        lastAttackAnchorFrequencyHzBeforeFrame = 0;
        lastToneOnThreshold = 0;
        lastFrameLocalToneOnTimestampMs = -1L;
        lastPostReleaseGapMs = lastEvent != null && lastEvent.type() == CwToneEvent.Type.TONE_OFF
                && timestampMs >= 0L
                ? Math.max(0L, timestampMs - lastEvent.timestampMs())
                : -1L;
        lastPostReleaseWindowMs = lastEvent != null && lastEvent.type() == CwToneEvent.Type.TONE_OFF
                ? postReleaseNearTargetReacquireWindowMs()
                : 0L;
        lastLocalContrastRatio = 0.0d;
        lastWeakPostReleaseOnsetChainCandidate = false;
        lastTrustedContinuityToneOnCandidate = false;
        lastSteadyLateGapNearTargetRescueCandidate = false;
        lastLowGrowthStrongSteadyNearTargetRescue = false;
        lastNearTargetPostReleaseToneOnRescue = false;
        lastPostReleaseSteadyCarrierSuppressed = false;
        lastFarAttackToneOnDelayed = false;
        lastToneOnAccepted = false;
        lastToneOnAcceptedByRescue = false;
        lastReleaseTailHoldApplied = false;
        lastToneActiveReleaseThreshold = 0;
        lastReleaseTailHoldRequiredDetectionThreshold = 0.0d;
        lastReleaseTailHoldSufficientRecentTrust = false;
        lastReleaseTailHoldCurrentRunStableBootstrapEligible = false;
        lastReleaseTailHoldCurrentRunWeakBootstrapEligible = false;
        lastPostReleaseRescueDecision = "SKIP:NO_ATTEMPT";
        lastPostReleaseSuppressionDecision = "SKIP:NO_ATTEMPT";
        lastFarAttackDelayDecision = "SKIP:NO_ATTEMPT";
        lastToneOnDecision = "SKIP:NO_ATTEMPT";
        lastReleaseTailHoldDecision = "SKIP:NO_ATTEMPT";
    }

    private void rememberFrame(long timestampMs, double detectionLevel) {
        if (lastFrameTimestampMs >= 0L && timestampMs > lastFrameTimestampMs) {
            lastFrameGapMs = timestampMs - lastFrameTimestampMs;
            worstFrameGapMs = Math.max(worstFrameGapMs, lastFrameGapMs);
        }
        lastFrameTimestampMs = timestampMs;
        lastDetectionLevel = detectionLevel;
        rememberRecentFrontEndHistory();
    }

    private void rememberRecentFrontEndHistory() {
        recentFrontEndStateHistory[recentHistoryNextIndex] = currentFrontEndStateCode();
        recentTrackingOffsetHistoryHz[recentHistoryNextIndex] = targetToneFrequencyHz - preferredToneFrequencyHz;
        recentHistoryNextIndex = (recentHistoryNextIndex + 1) % RECENT_HISTORY_WINDOW_FRAMES;
        recentHistoryFrameCount = Math.min(RECENT_HISTORY_WINDOW_FRAMES, recentHistoryFrameCount + 1);
    }

    private char currentFrontEndStateCode() {
        if (toneActive) {
            return targetToneLocked ? 'L' : 'u';
        }
        return targetToneLocked ? 'l' : '.';
    }

    private char[] orderedRecentFrontEndStateHistory() {
        char[] ordered = new char[recentHistoryFrameCount];
        if (recentHistoryFrameCount <= 0) {
            return ordered;
        }
        int startIndex = recentHistoryFrameCount < RECENT_HISTORY_WINDOW_FRAMES ? 0 : recentHistoryNextIndex;
        for (int index = 0; index < recentHistoryFrameCount; index++) {
            ordered[index] = recentFrontEndStateHistory[(startIndex + index) % RECENT_HISTORY_WINDOW_FRAMES];
        }
        return ordered;
    }

    private int[] orderedRecentTrackingOffsetHistoryHz() {
        int[] ordered = new int[recentHistoryFrameCount];
        if (recentHistoryFrameCount <= 0) {
            return ordered;
        }
        int startIndex = recentHistoryFrameCount < RECENT_HISTORY_WINDOW_FRAMES ? 0 : recentHistoryNextIndex;
        for (int index = 0; index < recentHistoryFrameCount; index++) {
            ordered[index] = recentTrackingOffsetHistoryHz[(startIndex + index) % RECENT_HISTORY_WINDOW_FRAMES];
        }
        return ordered;
    }

    private void rememberToneActivityWindow() {
        if (!toneActive) {
            consecutiveToneActiveUnlockedFrames = 0;
            return;
        }
        toneActiveFrameCount += 1;
        if (!targetToneLocked) {
            toneActiveUnlockedFrameCount += 1;
            consecutiveToneActiveUnlockedFrames += 1;
            maxConsecutiveToneActiveUnlockedFrames = Math.max(
                    maxConsecutiveToneActiveUnlockedFrames,
                    consecutiveToneActiveUnlockedFrames
            );
            return;
        }
        consecutiveToneActiveUnlockedFrames = 0;
    }

    private long estimateFrameLocalTransitionTimestamp(AudioFrame frame, boolean risingEdge) {
        short[] samples = frame.samples();
        int sampleRateHz = frame.sampleRateHz();
        if (samples == null || samples.length < (EDGE_WINDOW_SAMPLES * 3) || sampleRateHz <= 0) {
            return -1L;
        }

        double[] envelope = buildAbsoluteEnvelope(samples);
        double envelopeMax = 0.0d;
        double envelopeMin = Double.MAX_VALUE;
        for (double value : envelope) {
            envelopeMax = Math.max(envelopeMax, value);
            envelopeMin = Math.min(envelopeMin, value);
        }
        if (envelopeMax < MIN_TRACKED_TONE_RMS) {
            return -1L;
        }

        double threshold = Math.max(
                envelopeMax * EDGE_THRESHOLD_RATIO,
                envelopeMin + ((envelopeMax - envelopeMin) * EDGE_DYNAMIC_RATIO)
        );
        int edgeRegionLength = Math.max(EDGE_WINDOW_SAMPLES * 2, samples.length / 5);
        if (risingEdge) {
            double earlyMax = maxEnvelope(envelope, 0, edgeRegionLength);
            double lateAverage = averageEnvelope(envelope, samples.length - edgeRegionLength, samples.length);
            if (earlyMax >= (threshold * EDGE_TRANSITION_REQUIRED_RATIO) || lateAverage <= threshold) {
                return -1L;
            }
            for (int index = 0; index <= (samples.length - EDGE_CONFIRM_SAMPLES); index++) {
                if (allAboveThreshold(envelope, index, EDGE_CONFIRM_SAMPLES, threshold)) {
                    return sampleIndexToTimestamp(frame, index);
                }
            }
            return -1L;
        }

        double earlyAverage = averageEnvelope(envelope, 0, edgeRegionLength);
        double earlyMax = maxEnvelope(envelope, 0, edgeRegionLength);
        double lateMax = maxEnvelope(envelope, samples.length - edgeRegionLength, samples.length);
        if (((earlyAverage <= threshold)
                && earlyMax < (threshold * EDGE_TRANSITION_REQUIRED_RATIO))
                || lateMax >= (threshold * EDGE_TRANSITION_REQUIRED_RATIO)) {
            return -1L;
        }
        for (int index = samples.length - 1; index >= 0; index--) {
            if (envelope[index] >= threshold) {
                return sampleIndexToTimestamp(frame, Math.min(samples.length - 1, index + 1));
            }
        }
        return -1L;
    }

    private double[] buildAbsoluteEnvelope(short[] samples) {
        double[] prefix = new double[samples.length + 1];
        for (int index = 0; index < samples.length; index++) {
            prefix[index + 1] = prefix[index] + Math.abs((int) samples[index]);
        }
        double[] envelope = new double[samples.length];
        int halfWindow = Math.max(1, EDGE_WINDOW_SAMPLES / 2);
        for (int index = 0; index < samples.length; index++) {
            int start = Math.max(0, index - halfWindow);
            int end = Math.min(samples.length, index + halfWindow + 1);
            envelope[index] = (prefix[end] - prefix[start]) / Math.max(1, end - start);
        }
        return envelope;
    }

    private double averageEnvelope(double[] envelope, int start, int end) {
        int safeStart = Math.max(0, start);
        int safeEnd = Math.min(envelope.length, end);
        if (safeStart >= safeEnd) {
            return 0.0d;
        }
        double sum = 0.0d;
        for (int index = safeStart; index < safeEnd; index++) {
            sum += envelope[index];
        }
        return sum / (safeEnd - safeStart);
    }

    private double maxEnvelope(double[] envelope, int start, int end) {
        int safeStart = Math.max(0, start);
        int safeEnd = Math.min(envelope.length, end);
        double maximum = 0.0d;
        for (int index = safeStart; index < safeEnd; index++) {
            maximum = Math.max(maximum, envelope[index]);
        }
        return maximum;
    }

    private boolean allAboveThreshold(double[] envelope, int start, int count, double threshold) {
        int safeEnd = Math.min(envelope.length, start + count);
        for (int index = start; index < safeEnd; index++) {
            if (envelope[index] < threshold) {
                return false;
            }
        }
        return safeEnd > start;
    }

    private long sampleIndexToTimestamp(AudioFrame frame, int sampleIndex) {
        int clampedIndex = Math.max(0, Math.min(frame.sampleCount() - 1, sampleIndex));
        return frame.capturedAtMs() + Math.round((clampedIndex * 1000.0d) / frame.sampleRateHz());
    }

    private void rememberFrameLeaderObservability(boolean activeWindowFrame) {
        if (!activeWindowFrame) {
            return;
        }
        int previousActiveCenterFrequencyHz = histogramLeaderFrequencyHz(activeAcquisitionWinnerHistogram);
        activeWindowObservationCount += 1;
        activePreferredWinnerObservationCount += recordHistogramHit(
                activePreferredWinnerHistogram,
                lastPreferredWindowWinnerFrequencyHz
        );
        activeWideWinnerObservationCount += recordHistogramHit(
                activeWideWinnerHistogram,
                lastWideScanWinnerFrequencyHz
        );
        activeAcquisitionWinnerObservationCount += recordHistogramHit(
                activeAcquisitionWinnerHistogram,
                lastAcquisitionWinnerFrequencyHz
        );
        activeLiveTargetObservationCount += recordHistogramHit(
                activeLiveTargetHistogram,
                targetToneFrequencyHz
        );
        if (hasActiveToneHypothesis()) {
            activeHypothesisObservationCount += recordHistogramHit(
                    activeHypothesisHistogram,
                    toneHypothesisFrequencyHz
            );
            observeHypothesisCompetition(
                    representativeLockedToneFrameCount > 0 ? representativeLockedToneFrequencyHz : 0,
                    targetToneFrequencyHz,
                    toneHypothesisFrequencyHz,
                    true
            );
            int activeCenterFrequencyHz = previousActiveCenterFrequencyHz > 0
                    ? previousActiveCenterFrequencyHz
                    : histogramLeaderFrequencyHz(activeAcquisitionWinnerHistogram);
            observeHypothesisCompetition(
                    activeCenterFrequencyHz,
                    targetToneFrequencyHz,
                    toneHypothesisFrequencyHz,
                    false
            );
        } else {
            representativeCompetitionHypothesisCurrentWinStreak = 0;
            activeCenterCompetitionHypothesisCurrentWinStreak = 0;
        }
    }

    private int acquisitionReferenceFrequencyHz() {
        return hasActiveToneHypothesis() ? toneHypothesisFrequencyHz : preferredToneFrequencyHz;
    }

    private boolean hasActiveToneHypothesis() {
        return toneHypothesisSupportFrames > 0
                && toneHypothesisConfidence >= TONE_HYPOTHESIS_MIN_CONFIDENCE
                && toneHypothesisTotalEvidence >= TONE_HYPOTHESIS_MIN_TOTAL_EVIDENCE;
    }

    private ToneFrequencyEstimate analyzeToneFrequency(AudioFrame frame) {
        processedFrameCount += 1;
        short[] samples = frame.samples();
        if (samples == null || samples.length == 0 || frame.sampleRateHz() <= 0) {
            trackingState = TrackingState.SEARCH;
            targetToneLocked = false;
            lastToneRmsAmplitude = 0.0d;
            lastWidebandResidualRmsAmplitude = 0.0d;
            toneDominanceRatio = 0.0d;
            narrowbandIsolationRatio = 0.0d;
            ageOutToneHypothesis(false, false, frame.capturedAtMs());
            rememberSignalQuality(0.0d, 0.0d, false, false);
            return new ToneFrequencyEstimate(targetToneFrequencyHz, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d, false, 0.0d);
        }

        // Keep strict lock-retention separate from softer continuity memory.
        // Continuity may preserve momentum for a recent target, but it must not
        // suppress wide acquisition the way a real locked state can.
        boolean lockRetentionMode = trackingState == TrackingState.LOCKED;
        boolean continuityMode = lockRetentionMode
                || isTrackedToneMemoryActive(frame.capturedAtMs());
        int previousTargetToneFrequencyHz = targetToneFrequencyHz;
        ToneScanResult preferredWindowScan = scanPreferredToneWindow(
                samples,
                frame.sampleRateHz(),
                frame.rmsAmplitude(),
                previousTargetToneFrequencyHz,
                lockRetentionMode
        );
        ToneFrequencyEstimate preferredWindowEstimate = preferredWindowScan.winner;
        ToneScanResult wideScan = shouldRunWideAcquisitionScan(preferredWindowScan, lockRetentionMode)
                ? scanWideAcquisitionWindow(
                samples,
                frame.sampleRateHz(),
                frame.rmsAmplitude(),
                previousTargetToneFrequencyHz
        )
                : ToneScanResult.empty();
        ToneFrequencyEstimate wideEstimate = wideScan.winner;
        hypothesisGuardAppliedThisFrame = false;
        hypothesisGuardOverrideAppliedThisFrame = false;
        hypothesisGuardOverrideRunnerUpEstimate = null;
        hypothesisGuardOverrideConfidence = 0.0d;
        ToneFrequencyEstimate searchEstimate = chooseAcquisitionScanEstimate(preferredWindowScan, wideScan);
        searchEstimate = maybeApplyPendingHypothesisGuardOverride(
                samples,
                frame.sampleRateHz(),
                frame.rmsAmplitude(),
                previousTargetToneFrequencyHz,
                searchEstimate
        );
        rememberAcquisitionWinners(preferredWindowScan, wideScan, searchEstimate);
        ToneFrequencyEstimate lockedWindowEstimate = scanLockedRetuneWindow(
                samples,
                frame.sampleRateHz(),
                frame.rmsAmplitude(),
                previousTargetToneFrequencyHz
        );
        ToneFrequencyEstimate previousTargetEstimate = evaluateToneEstimate(
                samples,
                frame.sampleRateHz(),
                frame.rmsAmplitude(),
                previousTargetToneFrequencyHz,
                0.0d
        );
        previousTargetEstimate = withSelectionScore(
                previousTargetEstimate,
                previousTargetToneFrequencyHz,
                previousTargetToneFrequencyHz,
                lockRetentionMode,
                false
        );
        ToneFrequencyEstimate lockedConsensusEstimate = maybeBuildExperimentalLockedConsensusEstimate(
                samples,
                frame.sampleRateHz(),
                frame.rmsAmplitude(),
                previousTargetToneFrequencyHz
        );
        rememberPreviousTargetEstimate(previousTargetToneFrequencyHz, previousTargetEstimate, lockRetentionMode);
        updateTrackingState(
                searchEstimate,
                lockedWindowEstimate,
                previousTargetEstimate,
                lockedConsensusEstimate,
                continuityMode,
                frame.capturedAtMs()
        );

        double trackedToneRms = estimateToneRms(samples, frame.sampleRateHz(), targetToneFrequencyHz);
        double adjacentToneRms = estimateAdjacentToneRms(samples, frame.sampleRateHz(), targetToneFrequencyHz);
        double widebandResidualRms = estimateWidebandResidualRms(frame.rmsAmplitude(), trackedToneRms);
        double dominanceRatio = trackedToneRms <= 0.0d || frame.rmsAmplitude() <= 0.0d
                ? 0.0d
                : Math.min(1.0d, trackedToneRms / frame.rmsAmplitude());
        double isolationRatio = computeNarrowbandIsolationRatio(trackedToneRms, widebandResidualRms);
        double localContrastRatio = computeLocalContrastRatio(trackedToneRms, adjacentToneRms);
        ToneFrequencyEstimate liveTargetEstimate = new ToneFrequencyEstimate(
                targetToneFrequencyHz,
                trackedToneRms,
                widebandResidualRms,
                dominanceRatio,
                isolationRatio,
                localContrastRatio,
                isLockedRetentionQualified(trackedToneRms, dominanceRatio, isolationRatio, localContrastRatio),
                scoreToneCandidate(targetToneFrequencyHz, targetToneFrequencyHz, true, null) * trackedToneRms
        );
        if (!hypothesisGuardAppliedThisFrame) {
            ToneFrequencyEstimate liveConsensusEstimate = maybeBuildExperimentalLiveConsensusEstimate(
                    samples,
                    frame.sampleRateHz(),
                    frame.rmsAmplitude(),
                    targetToneFrequencyHz
            );
            if (liveConsensusEstimate != null) {
                ToneFrequencyEstimate correctedLiveTargetEstimate = maybeApplyExperimentalLockedConsensusGuard(
                        liveTargetEstimate,
                        liveConsensusEstimate
                );
                if (correctedLiveTargetEstimate != liveTargetEstimate) {
                    targetToneFrequencyHz = correctedLiveTargetEstimate.frequencyHz;
                    trackedToneRms = correctedLiveTargetEstimate.toneRmsAmplitude;
                    widebandResidualRms = correctedLiveTargetEstimate.widebandResidualRmsAmplitude;
                    dominanceRatio = correctedLiveTargetEstimate.dominanceRatio;
                    isolationRatio = correctedLiveTargetEstimate.isolationRatio;
                    localContrastRatio = correctedLiveTargetEstimate.localContrastRatio;
                    rememberFinalAdoptedEstimate(
                            correctedLiveTargetEstimate,
                            AcquisitionWinnerSource.HYPOTHESIS_GUARD,
                            "stable locked consensus corrected the live target after raw tracking drift"
                    );
                }
            }
        }
        lastWidebandResidualRmsAmplitude = widebandResidualRms;
        narrowbandIsolationRatio = isolationRatio;
        boolean narrowbandQualified = trackedToneRms >= effectiveMinTrackedToneRmsForQualification()
                && dominanceRatio >= effectiveMinNarrowbandDominanceRatio()
                && (isolationRatio >= effectiveMinNarrowbandIsolationRatio()
                || localContrastRatio >= effectiveMinNarrowbandLocalContrastRatio());
        targetToneLocked = trackingState == TrackingState.LOCKED
                && isLockedRetentionQualified(trackedToneRms, dominanceRatio, isolationRatio, localContrastRatio);
        if (targetToneLocked || narrowbandQualified) {
            lastTrackedToneTimestampMs = frame.capturedAtMs();
        }
        if (!targetToneLocked && !toneActive && !isTrackedToneMemoryActive(frame.capturedAtMs())) {
            trackingState = TrackingState.SEARCH;
            pendingRetuneCandidateFrequencyHz = targetToneFrequencyHz;
            pendingRetuneCandidateStableScans = 0;
            lockLossFrames = 0;
            // SEARCH_FALLBACK is an observability state only. Do not reset target to preferred here.
            rememberFinalAdoptedEstimate(
                    null,
                    AcquisitionWinnerSource.SEARCH_FALLBACK,
                    "tracked-tone memory expired with no qualified lock; staying in search fallback"
            );
        }
        if (!targetToneLocked && trackingState == TrackingState.LOCKED) {
            trackingState = TrackingState.CANDIDATE;
        }
        updateToneHypothesis(
                searchEstimate,
                preferredWindowScan.runnerUp,
                wideScan.runnerUp,
                trackedToneRms,
                isolationRatio,
                narrowbandQualified,
                frame.capturedAtMs()
        );
        rememberSignalQuality(trackedToneRms, isolationRatio, targetToneLocked, narrowbandQualified);
        if (!hypothesisGuardAppliedThisFrame) {
            evaluateExperimentalHypothesisGuard(searchEstimate, trackedToneRms, isolationRatio, narrowbandQualified);
        }
        return new ToneFrequencyEstimate(
                targetToneFrequencyHz,
                trackedToneRms,
                widebandResidualRms,
                dominanceRatio,
                isolationRatio,
                localContrastRatio,
                targetToneLocked,
                scoreToneCandidate(targetToneFrequencyHz, targetToneFrequencyHz, true, null) * trackedToneRms
        );
    }

    private void updateTrackingState(
            ToneFrequencyEstimate searchEstimate,
            ToneFrequencyEstimate lockedWindowEstimate,
            ToneFrequencyEstimate previousTargetEstimate,
            ToneFrequencyEstimate lockedConsensusEstimate,
            boolean continuityMode,
            long timestampMs
    ) {
        clearLockedRetuneGuardFrameState();
        if (trackingState == TrackingState.LOCKED) {
            ToneFrequencyEstimate retained = chooseLockedEstimate(
                    lockedWindowEstimate,
                    searchEstimate,
                    previousTargetEstimate
            );
            retained = maybeApplyToneActivePlausiblePreviousTargetGuard(retained, previousTargetEstimate);
            if (!hasLockedRetuneGuardFrameState()) {
                retained = applyLockedRetuneGuard(retained, previousTargetEstimate);
            }
            boolean continuityHeldRetained = isToneActiveContinuityHeldRetainedEstimate(retained, previousTargetEstimate);
            if (retained != null && (isLockedRetentionQualified(retained) || continuityHeldRetained)) {
                retained = maybeApplyExperimentalLockedConsensusGuard(retained, lockedConsensusEstimate);
                targetToneFrequencyHz = retained.frequencyHz;
                targetToneLocked = isLockedRetentionQualified(retained);
                trackingState = targetToneLocked ? TrackingState.LOCKED : TrackingState.CANDIDATE;
                pendingRetuneCandidateFrequencyHz = retained.frequencyHz;
                pendingRetuneCandidateStableScans = targetToneLocked
                        ? CANDIDATE_STABILITY_ACCEPT_SCANS
                        : Math.max(1, pendingRetuneCandidateStableScans);
                if (targetToneLocked) {
                    lockLossFrames = 0;
                    lastTrackedToneTimestampMs = timestampMs;
                } else {
                    lockLossFrames = Math.min(lockLossFrames + 1, LOCK_LOSS_GRACE_FRAMES);
                }
                rememberFinalAdoptedEstimate(
                        retained,
                        hypothesisGuardAppliedThisFrame && !hypothesisGuardOverrideAppliedThisFrame
                                ? AcquisitionWinnerSource.HYPOTHESIS_GUARD
                                : AcquisitionWinnerSource.LOCKED_RETUNE,
                        hypothesisGuardAppliedThisFrame && !hypothesisGuardOverrideAppliedThisFrame
                                ? "stable locked consensus corrected a drifting live target before lock retention"
                                : continuityHeldRetained
                                ? "tone-active continuity held the prior target through a weak valley while a far retune candidate was still being validated"
                                : "locked-retune winner stayed qualified and remained the live target"
                );
                return;
            }
            lockLossFrames += 1;
            targetToneLocked = false;
            if (shouldHoldFarIdleContinuityCandidate(searchEstimate, continuityMode, timestampMs)) {
                trackingState = TrackingState.CANDIDATE;
                rememberFinalAdoptedEstimate(
                        null,
                        AcquisitionWinnerSource.SEARCH_FALLBACK,
                        "continuity held the last tracked target through a short idle gap instead of jumping to a far off-target carrier"
                );
                return;
            }
            if (shouldHoldShortGapContinuityCandidate(searchEstimate, continuityMode, timestampMs)) {
                trackingState = TrackingState.CANDIDATE;
                targetToneLocked = false;
                rememberFinalAdoptedEstimate(
                        null,
                        AcquisitionWinnerSource.SEARCH_FALLBACK,
                        "continuity held the prior target across a short post-tone gap before the next symbol re-qualified"
                );
                return;
            }
            if (shouldDelayFarContinuityCandidateAdoption(searchEstimate, continuityMode)) {
                trackingState = TrackingState.CANDIDATE;
                targetToneLocked = false;
                rememberFinalAdoptedEstimate(
                        null,
                        AcquisitionWinnerSource.SEARCH_FALLBACK,
                        "continuity held the prior target because a far off-target candidate was still much weaker than the last locked branch reference"
                );
                return;
            }
            if (shouldHoldToneActiveFarSearchCandidate(searchEstimate, previousTargetEstimate, continuityMode)) {
                trackingState = TrackingState.CANDIDATE;
                targetToneLocked = false;
                rememberFinalAdoptedEstimate(
                        null,
                        AcquisitionWinnerSource.SEARCH_FALLBACK,
                        "tone-active continuity held the prior target until a far retune candidate proved it was not only a weak valley hijack"
                );
                return;
            }
            if (searchEstimate != null && isAcquisitionCandidate(searchEstimate)) {
                adoptCandidate(searchEstimate);
                trackingState = canPromoteAcquisitionCandidateToLocked()
                        && pendingRetuneCandidateStableScans >= CANDIDATE_STABILITY_ACCEPT_SCANS
                        ? TrackingState.LOCKED
                        : TrackingState.CANDIDATE;
                targetToneLocked = trackingState == TrackingState.LOCKED;
                if (targetToneLocked) {
                    lockLossFrames = 0;
                    lastTrackedToneTimestampMs = timestampMs;
                }
                rememberFinalAdoptedEstimate(
                        searchEstimate,
                        lastAcquisitionWinnerSource,
                        "qualified acquisition candidate became the live target"
                );
                return;
            }
            if (shouldUseSoftSearchTarget(searchEstimate, previousTargetEstimate, continuityMode)) {
                adoptSoftSearchTarget(searchEstimate);
                trackingState = TrackingState.CANDIDATE;
                targetToneLocked = false;
                rememberFinalAdoptedEstimate(
                        searchEstimate,
                        lastAcquisitionWinnerSource,
                        "soft-search fallback kept a plausible search estimate while unlock continues"
                );
                return;
            }
            if (continuityMode && shouldRetireStaleEdgeTarget(searchEstimate, previousTargetEstimate)) {
                adoptSoftSearchTarget(searchEstimate);
                trackingState = TrackingState.CANDIDATE;
                targetToneLocked = false;
                rememberFinalAdoptedEstimate(
                        searchEstimate,
                        lastAcquisitionWinnerSource,
                    "retired a stale edge-biased target in favor of a plausible search estimate"
                );
                return;
            }
            if (lockLossFrames <= LOCK_LOSS_GRACE_FRAMES && continuityMode) {
                trackingState = TrackingState.CANDIDATE;
                return;
            }
            clearTrackingTarget();
            return;
        }

        if (shouldHoldFarIdleContinuityCandidate(searchEstimate, continuityMode, timestampMs)) {
            trackingState = TrackingState.CANDIDATE;
            targetToneLocked = false;
            rememberFinalAdoptedEstimate(
                    null,
                    AcquisitionWinnerSource.SEARCH_FALLBACK,
                    "continuity held the last tracked target through a short idle gap instead of jumping to a far off-target carrier"
            );
            return;
        }
        if (shouldHoldShortGapContinuityCandidate(searchEstimate, continuityMode, timestampMs)) {
            trackingState = TrackingState.CANDIDATE;
            targetToneLocked = false;
            rememberFinalAdoptedEstimate(
                    null,
                    AcquisitionWinnerSource.SEARCH_FALLBACK,
                    "continuity held the prior target across a short post-tone gap before the next symbol re-qualified"
            );
            return;
        }
        if (shouldDelayFarContinuityCandidateAdoption(searchEstimate, continuityMode)) {
            trackingState = TrackingState.CANDIDATE;
            targetToneLocked = false;
            rememberFinalAdoptedEstimate(
                    null,
                    AcquisitionWinnerSource.SEARCH_FALLBACK,
                    "continuity held the prior target because a far off-target candidate was still much weaker than the last locked branch reference"
            );
            return;
        }
        if (shouldHoldToneActiveFarSearchCandidate(searchEstimate, previousTargetEstimate, continuityMode)) {
            trackingState = TrackingState.CANDIDATE;
            targetToneLocked = false;
            rememberFinalAdoptedEstimate(
                    null,
                    AcquisitionWinnerSource.SEARCH_FALLBACK,
                    "tone-active continuity held the prior target until a far retune candidate proved it was not only a weak valley hijack"
            );
            return;
        }
        if (searchEstimate != null && isAcquisitionCandidate(searchEstimate)) {
            adoptCandidate(searchEstimate);
            trackingState = canPromoteAcquisitionCandidateToLocked()
                    && pendingRetuneCandidateStableScans >= CANDIDATE_STABILITY_ACCEPT_SCANS
                    ? TrackingState.LOCKED
                    : TrackingState.CANDIDATE;
            targetToneLocked = trackingState == TrackingState.LOCKED;
            if (targetToneLocked) {
                lockLossFrames = 0;
                lastTrackedToneTimestampMs = timestampMs;
            }
            rememberFinalAdoptedEstimate(
                    searchEstimate,
                    lastAcquisitionWinnerSource,
                    "qualified acquisition candidate became the live target"
            );
            return;
        }
        if (shouldUseSoftSearchTarget(searchEstimate, previousTargetEstimate, continuityMode)) {
            adoptSoftSearchTarget(searchEstimate);
            trackingState = TrackingState.CANDIDATE;
            targetToneLocked = false;
            rememberFinalAdoptedEstimate(
                    searchEstimate,
                    lastAcquisitionWinnerSource,
                    "soft-search fallback kept a plausible search estimate while unlock continues"
            );
            return;
        }
        if (continuityMode && shouldRetireStaleEdgeTarget(searchEstimate, previousTargetEstimate)) {
            adoptSoftSearchTarget(searchEstimate);
            trackingState = TrackingState.CANDIDATE;
            targetToneLocked = false;
            rememberFinalAdoptedEstimate(
                    searchEstimate,
                    lastAcquisitionWinnerSource,
                    "retired a stale edge-biased target in favor of a plausible search estimate"
            );
            return;
        }

        if (continuityMode) {
            trackingState = TrackingState.CANDIDATE;
            targetToneLocked = false;
            rememberFinalAdoptedEstimate(
                    null,
                    AcquisitionWinnerSource.SEARCH_FALLBACK,
                    "continuity kept the last tracked target because no qualified acquisition candidate emerged"
            );
            return;
        }
        clearTrackingTarget();
    }

    private void adoptCandidate(ToneFrequencyEstimate estimate) {
        if (estimate == null) {
            clearTrackingTarget();
            return;
        }
        // This is the only place where an acquisition candidate becomes the live target.
        // If target behavior feels "mysteriously anchored", audit callers reaching here
        // rather than adding any preferred-tone rewrite outside this path.
        targetToneFrequencyHz = estimate.frequencyHz;
        if (Math.abs(estimate.frequencyHz - pendingRetuneCandidateFrequencyHz) <= CANDIDATE_STABILITY_CLUSTER_WINDOW_HZ) {
            pendingRetuneCandidateStableScans += 1;
        } else {
            pendingRetuneCandidateFrequencyHz = estimate.frequencyHz;
            pendingRetuneCandidateStableScans = 1;
        }
        if (isImmediateLockCandidate(estimate)) {
            pendingRetuneCandidateStableScans = CANDIDATE_STABILITY_ACCEPT_SCANS;
        }
        resetPendingLockedRetuneCandidate(estimate.frequencyHz);
    }

    private boolean canPromoteAcquisitionCandidateToLocked() {
        return toneActive
                || totalToneOnEvents > 0
                || totalToneOffEvents > 0;
    }

    private boolean shouldHoldFarIdleContinuityCandidate(
            ToneFrequencyEstimate searchEstimate,
            boolean continuityMode,
            long timestampMs
    ) {
        if (!continuityMode || toneActive || searchEstimate == null || !isAcquisitionCandidate(searchEstimate)) {
            return false;
        }
        int continuityAnchorFrequencyHz = continuityAnchorFrequencyHz();
        if (continuityAnchorFrequencyHz <= 0) {
            return false;
        }
        int guardMinDriftHz = lockedRetuneGuardMinDriftHz();
        if (Math.abs(searchEstimate.frequencyHz - continuityAnchorFrequencyHz) < guardMinDriftHz) {
            return false;
        }
        int searchDistanceHz = Math.abs(searchEstimate.frequencyHz - preferredToneFrequencyHz);
        int anchorDistanceHz = Math.abs(continuityAnchorFrequencyHz - preferredToneFrequencyHz);
        if (searchDistanceHz <= (anchorDistanceHz + 20)) {
            return false;
        }
        return !shouldReleaseDominantWideScanCandidateFromIdleContinuityHold(searchEstimate, timestampMs);
    }

    private boolean shouldReleaseDominantWideScanCandidateFromIdleContinuityHold(
            ToneFrequencyEstimate searchEstimate,
            long timestampMs
    ) {
        if (searchEstimate == null
                || !searchEstimate.locked
                || lastEvent == null
                || lastEvent.type() != CwToneEvent.Type.TONE_OFF
                || timestampMs < lastEvent.timestampMs()
                || lastAcquisitionWinnerSource != AcquisitionWinnerSource.WIDE_SCAN
                || !lastAcquisitionWinnerLocked
                || Math.abs(lastAcquisitionWinnerFrequencyHz - searchEstimate.frequencyHz) > TONE_SCAN_STEP_HZ) {
            return false;
        }
        if (lastAcquisitionWinnerConfidence < 0.70d) {
            return false;
        }
        if (searchEstimate.toneRmsAmplitude < (currentThreshold() * 1.75d)) {
            return false;
        }
        if (searchEstimate.selectionScore
                < (Math.max(1.0d, lastPreviousTargetSelectionScore) * 3.0d)) {
            return false;
        }
        return searchEstimate.dominanceRatio >= (MIN_LOCK_DOMINANCE_RATIO * 0.70d)
                && (searchEstimate.isolationRatio >= (MIN_LOCK_ISOLATION_RATIO * 0.60d)
                || searchEstimate.localContrastRatio >= (MIN_LOCK_LOCAL_CONTRAST_RATIO * 0.80d));
    }

    private boolean shouldHoldShortGapContinuityCandidate(
            ToneFrequencyEstimate searchEstimate,
            boolean continuityMode,
            long timestampMs
    ) {
        if (!continuityMode
                || toneActive
                || searchEstimate == null
                || !isShortPostToneGapActive(timestampMs)) {
            return false;
        }
        int continuityAnchorFrequencyHz = shortGapContinuityAnchorFrequencyHz();
        if (continuityAnchorFrequencyHz <= 0) {
            return false;
        }
        if (!isAcquisitionCandidate(searchEstimate) && !isSoftSearchFallbackPlausible(searchEstimate)) {
            return false;
        }
        if (Math.abs(searchEstimate.frequencyHz - continuityAnchorFrequencyHz)
                < lockedRetuneGuardMinDriftHz()) {
            return false;
        }
        return recentLockedFrameRatioFromHistory() >= CONTINUITY_ANCHOR_TRUST_MIN_LOCKED_RATIO
                || recentNearTargetLockedFrameRatioFromHistory()
                >= AUTO_TRACK_WEAK_VALLEY_RESCUE_NEAR_TARGET_RATIO_MIN
                || hasActiveToneHypothesis();
    }

    private int shortGapContinuityAnchorFrequencyHz() {
        if (targetToneFrequencyHz > 0) {
            return targetToneFrequencyHz;
        }
        return continuityAnchorFrequencyHz();
    }

    private boolean isShortPostToneGapActive(long timestampMs) {
        if (lastEvent == null
                || lastEvent.type() != CwToneEvent.Type.TONE_OFF
                || timestampMs < lastEvent.timestampMs()) {
            return false;
        }
        return (timestampMs - lastEvent.timestampMs()) <= postReleaseNearTargetReacquireWindowMs();
    }

    private int continuityAttackAnchorFrequencyHz(long timestampMs) {
        int continuityAnchorFrequencyHz = continuityAnchorFrequencyHz();
        if (!isTrackedToneMemoryActive(timestampMs) || targetToneFrequencyHz <= 0) {
            return continuityAnchorFrequencyHz;
        }
        if (continuityAnchorFrequencyHz <= 0) {
            return targetToneFrequencyHz;
        }
        if (!isShortPostToneGapActive(timestampMs)
                || Math.abs(targetToneFrequencyHz - continuityAnchorFrequencyHz)
                < lockedRetuneGuardMinDriftHz()) {
            return continuityAnchorFrequencyHz;
        }
        boolean targetHasRecentNearLockHistory = recentLockedFrameRatioNearFrequencyFromHistory(
                targetToneFrequencyHz,
                AUTO_TRACK_WEAK_VALLEY_RESCUE_NEAR_TARGET_OFFSET_HZ
        ) >= 0.45d;
        boolean targetHasStrongHypothesis = hasStrongHypothesisAnchorNear(targetToneFrequencyHz);
        if (!targetHasRecentNearLockHistory && !targetHasStrongHypothesis) {
            return continuityAnchorFrequencyHz;
        }
        if (hasStrongHypothesisAnchorNear(continuityAnchorFrequencyHz) && !targetHasStrongHypothesis) {
            return continuityAnchorFrequencyHz;
        }
        return targetToneFrequencyHz;
    }

    private boolean shouldDelayFarContinuityCandidateAdoption(
            ToneFrequencyEstimate searchEstimate,
            boolean continuityMode
    ) {
        if (!continuityMode || searchEstimate == null || !isAcquisitionCandidate(searchEstimate)) {
            return false;
        }
        int guardMinDriftHz = lockedRetuneGuardMinDriftHz();
        int searchDistanceHz = Math.abs(searchEstimate.frequencyHz - preferredToneFrequencyHz);
        if (toneActive) {
            int liveTargetDistanceHz = Math.abs(targetToneFrequencyHz - preferredToneFrequencyHz);
            if (Math.abs(searchEstimate.frequencyHz - targetToneFrequencyHz) >= guardMinDriftHz
                    && searchDistanceHz > liveTargetDistanceHz
                    && lockedBranchReferenceToneRmsAmplitude > 0.0d
                    && searchEstimate.toneRmsAmplitude
                    < (lockedBranchReferenceToneRmsAmplitude * CONTINUITY_FAR_CANDIDATE_RELEASE_TONE_RATIO)) {
                return true;
            }
        }
        int continuityAnchorFrequencyHz = continuityAnchorFrequencyHz();
        if (continuityAnchorFrequencyHz <= 0) {
            return false;
        }
        if (Math.abs(searchEstimate.frequencyHz - continuityAnchorFrequencyHz) < guardMinDriftHz) {
            return false;
        }
        int anchorDistanceHz = Math.abs(continuityAnchorFrequencyHz - preferredToneFrequencyHz);
        if (searchDistanceHz <= (anchorDistanceHz + 20)) {
            return false;
        }
        return lockedBranchReferenceToneRmsAmplitude > 0.0d
                && searchEstimate.toneRmsAmplitude
                < (lockedBranchReferenceToneRmsAmplitude * CONTINUITY_FAR_CANDIDATE_RELEASE_TONE_RATIO);
    }

    private int continuityAnchorFrequencyHz() {
        if (representativeLockedToneFrameCount > 0) {
            return representativeLockedToneFrequencyHz;
        }
        if (hasActiveToneHypothesis()) {
            return toneHypothesisFrequencyHz;
        }
        return targetToneFrequencyHz;
    }

    private int lockedRetuneGuardMinDriftHz() {
        int configuredMinDriftHz = experimentalLockedRetuneGuardTuning == null
                ? LOCKED_RETUNE_GUARD_MIN_DRIFT_HZ
                : experimentalLockedRetuneGuardTuning.minDriftHz();
        int configuredNearFrequencyMinDriftHz = experimentalLockedRetuneGuardTuning == null
                ? LOCKED_RETUNE_GUARD_NEAR_FREQUENCY_MIN_DRIFT_HZ
                : experimentalLockedRetuneGuardTuning.nearFrequencyMinDriftHz();
        if (representativeLockedToneFrameCount < REPRESENTATIVE_LOCKED_TONE_REPLACEMENT_MIN_FRAMES) {
            return configuredMinDriftHz;
        }
        int activeCenterFrequencyHz = histogramLeaderFrequencyHz(activeAcquisitionWinnerHistogram);
        if (activeCenterFrequencyHz > 0
                && Math.abs(activeCenterFrequencyHz - representativeLockedToneFrequencyHz)
                > REPRESENTATIVE_LOCKED_TONE_CLUSTER_WINDOW_HZ) {
            return configuredMinDriftHz;
        }
        return configuredNearFrequencyMinDriftHz;
    }

    private boolean shouldUseSoftSearchTarget(
            ToneFrequencyEstimate searchEstimate,
            ToneFrequencyEstimate previousTargetEstimate,
            boolean continuityMode
    ) {
        if (searchEstimate == null || isAcquisitionCandidate(searchEstimate)) {
            return false;
        }
        if (continuityMode && isFarFromContinuityAnchor(searchEstimate.frequencyHz)) {
            return false;
        }
        if (lastAcquisitionWinnerSource == AcquisitionWinnerSource.NONE) {
            return false;
        }
        if (lastAcquisitionWinnerConfidence < 0.18d) {
            return false;
        }
        if (!isSoftSearchFallbackPlausible(searchEstimate)) {
            return false;
        }
        if (previousTargetEstimate == null) {
            return true;
        }
        if (isStaleEdgeResidualTarget(previousTargetEstimate, searchEstimate)) {
            return true;
        }
        if (Math.abs(searchEstimate.frequencyHz - previousTargetEstimate.frequencyHz) < 40) {
            return false;
        }
        double searchEvidenceScore = acquisitionEvidenceScore(searchEstimate);
        double previousEvidenceScore = acquisitionEvidenceScore(previousTargetEstimate);
        if (previousEvidenceScore <= 0.0d) {
            return true;
        }
        double requiredLead = continuityMode ? 1.08d : 1.02d;
        return searchEvidenceScore >= (previousEvidenceScore * requiredLead)
                || searchEstimate.toneRmsAmplitude >= (previousTargetEstimate.toneRmsAmplitude * 1.10d);
    }

    private boolean isSoftSearchFallbackPlausible(ToneFrequencyEstimate estimate) {
        if (estimate == null) {
            return false;
        }
        if (estimate.toneRmsAmplitude < (MIN_TRACKED_TONE_RMS * 0.80d)) {
            return false;
        }
        if (estimate.dominanceRatio < (MIN_NARROWBAND_DOMINANCE_RATIO * 0.75d)) {
            return false;
        }
        return estimate.isolationRatio >= (MIN_NARROWBAND_ISOLATION_RATIO * 0.65d)
                || estimate.localContrastRatio >= (MIN_NARROWBAND_LOCAL_CONTRAST_RATIO * 0.75d);
    }

    private boolean isStaleEdgeResidualTarget(
            ToneFrequencyEstimate previousTargetEstimate,
            ToneFrequencyEstimate searchEstimate
    ) {
        if (previousTargetEstimate == null || searchEstimate == null) {
            return false;
        }
        if (!isAbsoluteTrackingEdgeFrequency(previousTargetEstimate.frequencyHz)) {
            return false;
        }
        int previousDistanceHz = Math.abs(previousTargetEstimate.frequencyHz - preferredToneFrequencyHz);
        int searchDistanceHz = Math.abs(searchEstimate.frequencyHz - preferredToneFrequencyHz);
        if (searchDistanceHz + 60 >= previousDistanceHz) {
            return false;
        }
        double searchEvidenceScore = acquisitionEvidenceScore(searchEstimate);
        double previousEvidenceScore = acquisitionEvidenceScore(previousTargetEstimate);
        return searchEvidenceScore >= (previousEvidenceScore * 0.72d)
                || searchEstimate.toneRmsAmplitude >= (previousTargetEstimate.toneRmsAmplitude * 0.82d);
    }

    private boolean shouldRetireStaleEdgeTarget(
            ToneFrequencyEstimate searchEstimate,
            ToneFrequencyEstimate previousTargetEstimate
    ) {
        if (searchEstimate == null || previousTargetEstimate == null) {
            return false;
        }
        if (!isAbsoluteTrackingEdgeFrequency(previousTargetEstimate.frequencyHz)) {
            return false;
        }
        if (lastAcquisitionWinnerSource == AcquisitionWinnerSource.NONE || lastAcquisitionWinnerConfidence < 0.20d) {
            return false;
        }
        int previousDistanceHz = Math.abs(previousTargetEstimate.frequencyHz - preferredToneFrequencyHz);
        int searchDistanceHz = Math.abs(searchEstimate.frequencyHz - preferredToneFrequencyHz);
        return searchDistanceHz + 50 < previousDistanceHz;
    }

    private void adoptSoftSearchTarget(ToneFrequencyEstimate estimate) {
        if (estimate == null) {
            return;
        }
        targetToneFrequencyHz = estimate.frequencyHz;
        if (Math.abs(estimate.frequencyHz - pendingRetuneCandidateFrequencyHz) <= CANDIDATE_STABILITY_CLUSTER_WINDOW_HZ) {
            pendingRetuneCandidateStableScans = Math.max(1, pendingRetuneCandidateStableScans + 1);
        } else {
            pendingRetuneCandidateFrequencyHz = estimate.frequencyHz;
            pendingRetuneCandidateStableScans = 1;
        }
        resetPendingLockedRetuneCandidate(estimate.frequencyHz);
    }

    private boolean shouldDelayFarAttackToneOn(
            ToneFrequencyEstimate toneEstimate,
            boolean hadToneHistoryBeforeFrame,
            boolean hadTrackedToneMemoryBeforeFrame,
            int attackAnchorFrequencyHzBeforeFrame,
            double attackReferenceToneRmsBeforeFrame,
            long timestampMs
    ) {
        if (toneEstimate == null || toneEstimate.frequencyHz <= 0) {
            clearPendingFarAttackCandidate();
            lastFarAttackDelayDecision = "ALLOW:NO_TONE";
            return false;
        }
        if (!isFarAttackCandidate(
                toneEstimate,
                hadToneHistoryBeforeFrame,
                hadTrackedToneMemoryBeforeFrame,
                attackAnchorFrequencyHzBeforeFrame,
                attackReferenceToneRmsBeforeFrame,
                timestampMs
        )) {
            clearPendingFarAttackCandidate();
            lastFarAttackDelayDecision = "ALLOW:NOT_FAR_ATTACK";
            return false;
        }
        if (toneEstimate.frequencyHz == pendingFarAttackCandidateFrequencyHz) {
            pendingFarAttackCandidateStableFrames += 1;
        } else {
            pendingFarAttackCandidateFrequencyHz = toneEstimate.frequencyHz;
            pendingFarAttackCandidateStableFrames = 1;
        }
        if (pendingFarAttackCandidateStableFrames >= FAR_ATTACK_CONFIRM_REQUIRED_FRAMES) {
            clearPendingFarAttackCandidate();
            lastFarAttackDelayDecision = "ALLOW:FAR_ATTACK_CONFIRMED";
            return false;
        }
        lastFarAttackDelayDecision = "DELAY:FAR_ATTACK_CONFIRM";
        return true;
    }

    private boolean isFarAttackCandidate(
            ToneFrequencyEstimate toneEstimate,
            boolean hadToneHistoryBeforeFrame,
            boolean hadTrackedToneMemoryBeforeFrame,
            int attackAnchorFrequencyHzBeforeFrame,
            double attackReferenceToneRmsBeforeFrame,
            long timestampMs
    ) {
        if (toneEstimate == null || toneEstimate.frequencyHz <= 0) {
            return false;
        }
        if (hadToneHistoryBeforeFrame
                && hadTrackedToneMemoryBeforeFrame
                && !shouldConfirmTrackedMemoryFarAttack(
                toneEstimate,
                attackAnchorFrequencyHzBeforeFrame,
                timestampMs
        )) {
            return false;
        }
        int anchorFrequencyHz = hadToneHistoryBeforeFrame
                ? attackAnchorFrequencyHzBeforeFrame
                : preferredToneFrequencyHz;
        if (anchorFrequencyHz <= 0) {
            anchorFrequencyHz = preferredToneFrequencyHz;
        }
        if (Math.abs(toneEstimate.frequencyHz - anchorFrequencyHz) < FAR_ATTACK_CONFIRM_DRIFT_HZ) {
            return false;
        }
        if (attackReferenceToneRmsBeforeFrame <= 0.0d) {
            return true;
        }
        return toneEstimate.toneRmsAmplitude
                < (attackReferenceToneRmsBeforeFrame * FAR_ATTACK_CONFIRM_REFERENCE_TONE_RATIO);
    }

    private boolean shouldConfirmTrackedMemoryFarAttack(
            ToneFrequencyEstimate toneEstimate,
            int attackAnchorFrequencyHzBeforeFrame,
            long timestampMs
    ) {
        if (toneActive
                || targetToneLocked
                || toneEstimate == null
                || toneEstimate.frequencyHz <= 0
                || lastEvent == null
                || lastEvent.type() != CwToneEvent.Type.TONE_OFF
                || timestampMs < lastEvent.timestampMs()) {
            return false;
        }
        int anchorFrequencyHz = attackAnchorFrequencyHzBeforeFrame > 0
                ? attackAnchorFrequencyHzBeforeFrame
                : continuityAnchorFrequencyHz();
        if (anchorFrequencyHz <= 0
                || Math.abs(toneEstimate.frequencyHz - anchorFrequencyHz)
                < FAR_ATTACK_CONFIRM_DRIFT_HZ) {
            return false;
        }
        return (timestampMs - lastEvent.timestampMs()) >= trackedMemoryFarAttackMinGapMs();
    }

    private long trackedMemoryFarAttackMinGapMs() {
        if (lastEvent == null
                || lastEvent.type() != CwToneEvent.Type.TONE_OFF
                || lastEvent.toneDurationMs() <= 0L) {
            return FRAME_GAP_RESET_MIN_MS;
        }
        return Math.max(
                FRAME_GAP_RESET_MIN_MS,
                Math.round(lastEvent.toneDurationMs() * TRACKED_MEMORY_FAR_ATTACK_MIN_GAP_TONE_RATIO)
        );
    }

    private boolean isFarFromContinuityAnchor(int candidateFrequencyHz) {
        int anchorFrequencyHz = continuityAnchorFrequencyHz();
        return anchorFrequencyHz > 0
                && Math.abs(candidateFrequencyHz - anchorFrequencyHz) >= FAR_ATTACK_CONFIRM_DRIFT_HZ;
    }

    private void clearPendingFarAttackCandidate() {
        pendingFarAttackCandidateFrequencyHz = 0;
        pendingFarAttackCandidateStableFrames = 0;
    }

    private boolean shouldBlockEdgeFreeFarCarrierToneOn(
            ToneFrequencyEstimate toneEstimate,
            boolean hadTrackedToneMemoryBeforeFrame,
            int attackAnchorFrequencyHzBeforeFrame,
            long frameLocalToneOnTimestampMs,
            boolean nearTargetPostReleaseToneOnRescue
    ) {
        if (toneEstimate == null || toneEstimate.frequencyHz <= 0 || nearTargetPostReleaseToneOnRescue) {
            return false;
        }
        int anchorFrequencyHz = attackAnchorFrequencyHzBeforeFrame > 0
                ? attackAnchorFrequencyHzBeforeFrame
                : preferredToneFrequencyHz;
        int minDriftHz = hadTrackedToneMemoryBeforeFrame && hasTrustedContinuityAnchorContext()
                ? TRUSTED_CONTINUITY_ANCHOR_MIN_DRIFT_HZ
                : EDGE_FREE_FAR_CARRIER_BLOCK_MIN_DRIFT_HZ;
        if (anchorFrequencyHz <= 0
                || Math.abs(toneEstimate.frequencyHz - anchorFrequencyHz)
                < minDriftHz) {
            return false;
        }
        if (!hadTrackedToneMemoryBeforeFrame) {
            // On a cold start, acquisition may already have converged on a lock-quality
            // off-preferred winner even though tone-on has not fired yet, so the live
            // tracking state has not been promoted to LOCKED. In that case this guard
            // must stand down or it will permanently suppress legitimate first-run tones.
            if (toneEstimate.locked
                    || (lastAcquisitionWinnerLocked
                    && lastAcquisitionWinnerFrequencyHz > 0
                    && Math.abs(lastAcquisitionWinnerFrequencyHz - toneEstimate.frequencyHz)
                    <= TONE_SCAN_STEP_HZ)) {
                return false;
            }
            return frameLocalToneOnTimestampMs < 0L;
        }
        return shouldBlockTrackedMemoryFarCarrierToneOn();
    }

    private ToneFrequencyEstimate maybePreferContinuityAnchorToneEstimateDuringTrustedToneActivity(
            AudioFrame frame,
            ToneFrequencyEstimate toneEstimate,
            boolean hadTrackedToneMemoryBeforeFrame,
            int attackAnchorFrequencyHzBeforeFrame
    ) {
        boolean lowEdgeSuppressionContext = shouldUseLowEdgeHijackSuppressionContext(
                toneEstimate,
                hadTrackedToneMemoryBeforeFrame,
                attackAnchorFrequencyHzBeforeFrame
        );
        if (!toneActive
                || toneEstimate == null
                || toneEstimate.frequencyHz <= 0
                || !hadTrackedToneMemoryBeforeFrame
                || (!hasTrustedContinuityAnchorContext() && !lowEdgeSuppressionContext)) {
            return toneEstimate;
        }
        int anchorFrequencyHz = attackAnchorFrequencyHzBeforeFrame > 0
                ? attackAnchorFrequencyHzBeforeFrame
                : continuityAnchorFrequencyHz();
        if (anchorFrequencyHz <= 0
                || Math.abs(toneEstimate.frequencyHz - anchorFrequencyHz)
                < TRUSTED_CONTINUITY_ANCHOR_MIN_DRIFT_HZ) {
            return toneEstimate;
        }
        short[] samples = frame.samples();
        if (samples == null || samples.length == 0 || frame.sampleRateHz() <= 0) {
            return toneEstimate;
        }
        ToneFrequencyEstimate anchorEstimate = evaluateToneEstimate(
                samples,
                frame.sampleRateHz(),
                frame.rmsAmplitude(),
                anchorFrequencyHz,
                0.0d
        );
        anchorEstimate = withSelectionScore(
                anchorEstimate,
                anchorFrequencyHz,
                anchorFrequencyHz,
                true,
                false
        );
        int anchorDriftHz = Math.abs(toneEstimate.frequencyHz - anchorEstimate.frequencyHz);
        if (!lowEdgeSuppressionContext
                && isDecisiveLockedRetuneOverrideAgainstPlausibleAnchor(
                toneEstimate,
                anchorEstimate,
                null,
                anchorDriftHz
        )) {
            return toneEstimate;
        }
        if (toneEstimate.locked
                && anchorDriftHz >= lockedRetuneGuardMinDriftHz()
                && !isLockedRetentionQualified(anchorEstimate)
                && isImmediateLockedRetuneOverride(toneEstimate, anchorEstimate, anchorDriftHz)
                && isFarCandidateStrongEnoughToReleasePlausibleAnchor(
                toneEstimate,
                anchorEstimate,
                TONE_ACTIVE_FAR_SEARCH_RELEASE_SCORE_RATIO,
                TONE_ACTIVE_FAR_SEARCH_RELEASE_TONE_RATIO
        )) {
            return toneEstimate;
        }
        targetToneFrequencyHz = anchorEstimate.frequencyHz;
        pendingRetuneCandidateFrequencyHz = anchorEstimate.frequencyHz;
        pendingRetuneCandidateStableScans = Math.max(1, pendingRetuneCandidateStableScans);
        resetPendingLockedRetuneCandidate(anchorEstimate.frequencyHz);
        rememberFinalAdoptedEstimate(
                anchorEstimate,
                AcquisitionWinnerSource.LOCKED_RETUNE,
                lowEdgeSuppressionContext
                        ? "trusted continuity anchor suppressed a low-edge hijack during tone-active release evaluation"
                        : "trusted continuity anchor replaced a drifting far-tone estimate during tone-active release evaluation"
        );
        return anchorEstimate;
    }

    private ToneFrequencyEstimate maybePreferContinuityAnchorToneEstimateForToneOn(
            AudioFrame frame,
            ToneFrequencyEstimate toneEstimate,
            boolean hadTrackedToneMemoryBeforeFrame,
            int attackAnchorFrequencyHzBeforeFrame,
            long timestampMs
    ) {
        boolean lowEdgeSuppressionContext = shouldUseLowEdgeHijackSuppressionContext(
                toneEstimate,
                hadTrackedToneMemoryBeforeFrame,
                attackAnchorFrequencyHzBeforeFrame
        );
        if (toneActive
                || toneEstimate == null
                || toneEstimate.frequencyHz <= 0
                || !hadTrackedToneMemoryBeforeFrame
                || (!hasTrustedContinuityAnchorReacquireContext(timestampMs) && !lowEdgeSuppressionContext)) {
            return toneEstimate;
        }
        int anchorFrequencyHz = attackAnchorFrequencyHzBeforeFrame > 0
                ? attackAnchorFrequencyHzBeforeFrame
                : continuityAnchorFrequencyHz();
        if (anchorFrequencyHz <= 0
                || Math.abs(toneEstimate.frequencyHz - anchorFrequencyHz)
                < TRUSTED_CONTINUITY_ANCHOR_MIN_DRIFT_HZ) {
            return toneEstimate;
        }
        short[] samples = frame.samples();
        if (samples == null || samples.length == 0 || frame.sampleRateHz() <= 0) {
            return toneEstimate;
        }
        ToneFrequencyEstimate anchorEstimate = evaluateToneEstimate(
                samples,
                frame.sampleRateHz(),
                frame.rmsAmplitude(),
                anchorFrequencyHz,
                0.0d
        );
        anchorEstimate = withSelectionScore(
                anchorEstimate,
                anchorFrequencyHz,
                anchorFrequencyHz,
                true,
                false
        );
        boolean anchorPlausible = isToneActiveContinuityTargetPlausible(anchorEstimate);
        boolean strongLowEdgeToneOnCandidate = shouldHonorStrongLowEdgeToneOnCandidate(
                toneEstimate,
                anchorEstimate
        );
        boolean strongAbsoluteEdgeToneOnCandidate = shouldHonorStrongAbsoluteEdgeToneOnCandidate(
                toneEstimate,
                anchorEstimate
        );
        boolean suppressLowEdgeToneOn = lowEdgeSuppressionContext
                && shouldSuppressLowEdgeToneOnWithContinuityAnchor(toneEstimate, anchorEstimate)
                && !strongLowEdgeToneOnCandidate;
        boolean suppressFarToneOn = shouldSuppressTrustedFarToneOnWithContinuityAnchor(
                toneEstimate,
                anchorEstimate
        ) && !strongLowEdgeToneOnCandidate
                && !strongAbsoluteEdgeToneOnCandidate
                || suppressLowEdgeToneOn;
        if (!anchorPlausible && !suppressFarToneOn) {
            return toneEstimate;
        }
        targetToneFrequencyHz = anchorEstimate.frequencyHz;
        pendingRetuneCandidateFrequencyHz = anchorEstimate.frequencyHz;
        pendingRetuneCandidateStableScans = Math.max(1, pendingRetuneCandidateStableScans);
        resetPendingLockedRetuneCandidate(anchorEstimate.frequencyHz);
        rememberFinalAdoptedEstimate(
                anchorEstimate,
                AcquisitionWinnerSource.LOCKED_RETUNE,
                anchorPlausible
                        ? "trusted continuity anchor replaced a drifting far-tone estimate before tone-on qualification"
                        : suppressLowEdgeToneOn
                        ? "trusted continuity anchor suppressed a low-edge tone-on hijack until local requalification"
                        : "trusted continuity anchor suppressed a far post-release tone-on until local requalification"
        );
        return anchorEstimate;
    }

    private boolean hasTrustedContinuityAnchorReacquireContext(long timestampMs) {
        if (hasTrustedContinuityAnchorContext()) {
            return true;
        }
        int anchorFrequencyHz = continuityAnchorFrequencyHz();
        if (anchorFrequencyHz <= 0) {
            return false;
        }
        if (maxConsecutiveLockedFrames < CONTINUITY_ANCHOR_TRUST_MIN_STABLE_LOCK_FRAMES
                && consecutiveLockedFrames < CONTINUITY_ANCHOR_TRUST_MIN_STABLE_LOCK_FRAMES) {
            return false;
        }
        boolean hasAnchorEvidence = hasRepresentativeAnchorNear(anchorFrequencyHz)
                || hasStrongHypothesisAnchorNear(anchorFrequencyHz);
        if (!hasAnchorEvidence) {
            return false;
        }
        if (lastEvent == null
                || lastEvent.type() != CwToneEvent.Type.TONE_OFF
                || timestampMs < lastEvent.timestampMs()) {
            return false;
        }
        return (timestampMs - lastEvent.timestampMs()) <= currentTrackedToneIdleHangMs();
    }

    private boolean shouldUseLowEdgeHijackSuppressionContext(
            ToneFrequencyEstimate toneEstimate,
            boolean hadTrackedToneMemoryBeforeFrame,
            int attackAnchorFrequencyHzBeforeFrame
    ) {
        if (!hadTrackedToneMemoryBeforeFrame || toneEstimate == null || toneEstimate.frequencyHz <= 0) {
            return false;
        }
        if (!isLowEdgeBandFrequency(toneEstimate.frequencyHz)) {
            return false;
        }
        int anchorFrequencyHz = attackAnchorFrequencyHzBeforeFrame > 0
                ? attackAnchorFrequencyHzBeforeFrame
                : continuityAnchorFrequencyHz();
        if (anchorFrequencyHz <= 0
                || Math.abs(toneEstimate.frequencyHz - anchorFrequencyHz) < LOW_EDGE_HIJACK_SUPPRESSION_MIN_DRIFT_HZ) {
            return false;
        }
        return hasRepresentativeAnchorNear(anchorFrequencyHz)
                || hasStrongHypothesisAnchorNear(anchorFrequencyHz);
    }

    private boolean isLowEdgeBandFrequency(int frequencyHz) {
        return frequencyHz >= LOW_EDGE_BAND_MIN_HZ && frequencyHz <= LOW_EDGE_BAND_MAX_HZ;
    }

    private boolean hasRepresentativeAnchorNear(int anchorFrequencyHz) {
        return representativeLockedToneFrameCount >= CONTINUITY_ANCHOR_TRUST_MIN_REPRESENTATIVE_FRAMES
                && Math.abs(representativeLockedToneFrequencyHz - anchorFrequencyHz)
                <= REPRESENTATIVE_LOCKED_TONE_CLUSTER_WINDOW_HZ;
    }

    private boolean hasStrongHypothesisAnchorNear(int anchorFrequencyHz) {
        return hasActiveToneHypothesis()
                && toneHypothesisSupportFrames >= CONTINUITY_ANCHOR_TRUST_MIN_HYPOTHESIS_SUPPORT_FRAMES
                && toneHypothesisConfidence >= CONTINUITY_ANCHOR_TRUST_MIN_HYPOTHESIS_CONFIDENCE
                && Math.abs(toneHypothesisFrequencyHz - anchorFrequencyHz)
                <= (REPRESENTATIVE_LOCKED_TONE_CLUSTER_WINDOW_HZ * 2);
    }

    private boolean shouldSuppressTrustedFarToneOnWithContinuityAnchor(
            ToneFrequencyEstimate toneEstimate,
            ToneFrequencyEstimate anchorEstimate
    ) {
        return lastEvent != null
                && lastEvent.type() == CwToneEvent.Type.TONE_OFF
                && toneEstimate != null
                && toneEstimate.frequencyHz > 0
                && anchorEstimate != null
                && anchorEstimate.frequencyHz > 0
                && Math.abs(toneEstimate.frequencyHz - anchorEstimate.frequencyHz)
                >= TRUSTED_CONTINUITY_ANCHOR_MIN_DRIFT_HZ;
    }

    private boolean shouldSuppressLowEdgeToneOnWithContinuityAnchor(
            ToneFrequencyEstimate toneEstimate,
            ToneFrequencyEstimate anchorEstimate
    ) {
        return lastEvent != null
                && lastEvent.type() == CwToneEvent.Type.TONE_OFF
                && toneEstimate != null
                && anchorEstimate != null
                && isLowEdgeBandFrequency(toneEstimate.frequencyHz)
                && anchorEstimate.frequencyHz > 0
                && Math.abs(toneEstimate.frequencyHz - anchorEstimate.frequencyHz)
                >= LOW_EDGE_HIJACK_SUPPRESSION_MIN_DRIFT_HZ;
    }

    private boolean shouldHonorStrongLowEdgeToneOnCandidate(
            ToneFrequencyEstimate toneEstimate,
            ToneFrequencyEstimate anchorEstimate
    ) {
        if (toneEstimate == null
                || anchorEstimate == null
                || !isLowEdgeBandFrequency(toneEstimate.frequencyHz)
                || !isNarrowbandQualified(toneEstimate)) {
            return false;
        }
        boolean strongLowEdgeQuality = toneEstimate.dominanceRatio >= 0.32d
                && (toneEstimate.isolationRatio >= 0.30d
                || toneEstimate.localContrastRatio >= 0.52d);
        if (!strongLowEdgeQuality) {
            return false;
        }
        boolean anchorWeakOrImplausible = !isToneActiveContinuityTargetPlausible(anchorEstimate);
        if (anchorWeakOrImplausible) {
            return true;
        }
        return toneEstimate.selectionScore >= (Math.max(1.0d, anchorEstimate.selectionScore) * 3.0d)
                || toneEstimate.toneRmsAmplitude
                >= (Math.max(1.0d, anchorEstimate.toneRmsAmplitude) * 2.5d);
    }

    private boolean shouldHonorStrongAbsoluteEdgeToneOnCandidate(
            ToneFrequencyEstimate toneEstimate,
            ToneFrequencyEstimate anchorEstimate
    ) {
        if (toneEstimate == null
                || anchorEstimate == null
                || !isAbsoluteTrackingEdgeFrequency(toneEstimate.frequencyHz)
                || !toneEstimate.locked
                || !isNarrowbandQualified(toneEstimate)) {
            return false;
        }
        boolean strongEdgeQuality = toneEstimate.dominanceRatio >= 0.32d
                && (toneEstimate.isolationRatio >= 0.30d
                || toneEstimate.localContrastRatio >= 0.50d);
        if (!strongEdgeQuality) {
            return false;
        }
        boolean anchorWeakOrImplausible = !isToneActiveContinuityTargetPlausible(anchorEstimate);
        if (anchorWeakOrImplausible) {
            return true;
        }
        return toneEstimate.selectionScore >= (Math.max(1.0d, anchorEstimate.selectionScore) * 3.0d)
                || toneEstimate.toneRmsAmplitude
                >= (Math.max(1.0d, anchorEstimate.toneRmsAmplitude) * 2.5d);
    }

    private boolean shouldBlockTrackedMemoryFarCarrierToneOn() {
        if (lastEvent == null || lastEvent.type() != CwToneEvent.Type.TONE_OFF) {
            return false;
        }
        if (!hasTrustedContinuityAnchorContext()) {
            return false;
        }
        return recentLockedFrameRatioFromHistory() >= POST_RELEASE_WEAK_ONSET_TRUSTED_MIN_LOCKED_RATIO
                && recentNearTargetLockedFrameRatioFromHistory()
                >= POST_RELEASE_WEAK_ONSET_TRUSTED_MIN_NEAR_TARGET_RATIO;
    }

    private void clearTrackingTarget() {
        trackingState = TrackingState.SEARCH;
        targetToneLocked = false;
        lockedBranchReferenceToneRmsAmplitude = 0.0d;
        pendingRetuneCandidateFrequencyHz = targetToneFrequencyHz;
        pendingRetuneCandidateStableScans = 0;
        resetPendingLockedRetuneCandidate(targetToneFrequencyHz);
        lockLossFrames = 0;
        // Losing confidence does not mean "snap back to preferred". We leave the last
        // adopted target in place so observability can show what was last tracked.
        rememberFinalAdoptedEstimate(
                null,
                AcquisitionWinnerSource.SEARCH_FALLBACK,
                "tracking confidence collapsed; keeping the last tracked target as search fallback"
        );
    }

    private void rememberAcquisitionWinners(
            ToneScanResult preferredWindowScan,
            ToneScanResult wideScan,
            ToneFrequencyEstimate acquisitionWinner
    ) {
        ToneFrequencyEstimate preferredWindowEstimate = preferredWindowScan.winner;
        ToneFrequencyEstimate wideEstimate = wideScan.winner;
        lastPreferredWindowWinnerFrequencyHz = preferredWindowEstimate == null
                ? 0
                : preferredWindowEstimate.frequencyHz;
        lastWideScanWinnerFrequencyHz = wideEstimate == null ? 0 : wideEstimate.frequencyHz;
        lastAcquisitionWinnerFrequencyHz = acquisitionWinner == null
                ? 0
                : acquisitionWinner.frequencyHz;
        lastPreferredWindowWinnerToneRms = preferredWindowEstimate == null ? 0.0d : preferredWindowEstimate.toneRmsAmplitude;
        lastWideScanWinnerToneRms = wideEstimate == null ? 0.0d : wideEstimate.toneRmsAmplitude;
        lastAcquisitionWinnerToneRms = acquisitionWinner == null ? 0.0d : acquisitionWinner.toneRmsAmplitude;
        lastPreferredWindowWinnerSelectionScore = preferredWindowEstimate == null ? 0.0d : preferredWindowEstimate.selectionScore;
        lastWideScanWinnerSelectionScore = wideEstimate == null ? 0.0d : wideEstimate.selectionScore;
        lastAcquisitionWinnerSelectionScore = acquisitionWinner == null ? 0.0d : acquisitionWinner.selectionScore;
        lastPreferredWindowWinnerConfidence = preferredWindowScan.winnerConfidence;
        lastWideScanWinnerConfidence = wideScan.winnerConfidence;
        if (hypothesisGuardOverrideAppliedThisFrame && acquisitionWinner != null) {
            lastAcquisitionWinnerConfidence = hypothesisGuardOverrideConfidence;
        } else {
            lastAcquisitionWinnerConfidence = acquisitionWinner == null
                    ? 0.0d
                    : acquisitionWinner == wideEstimate
                    ? wideScan.winnerConfidence
                    : preferredWindowScan.winnerConfidence;
        }
        lastPreferredWindowRunnerUpFrequencyHz = preferredWindowScan.runnerUp == null
                ? 0
                : preferredWindowScan.runnerUp.frequencyHz;
        lastWideScanRunnerUpFrequencyHz = wideScan.runnerUp == null ? 0 : wideScan.runnerUp.frequencyHz;
        lastPreferredWindowRunnerUpSelectionScore = preferredWindowScan.runnerUp == null
                ? 0.0d
                : preferredWindowScan.runnerUp.selectionScore;
        lastWideScanRunnerUpSelectionScore = wideScan.runnerUp == null
                ? 0.0d
                : wideScan.runnerUp.selectionScore;
        ToneFrequencyEstimate acquisitionRunnerUp = null;
        if (acquisitionWinner == preferredWindowEstimate) {
            acquisitionRunnerUp = wideEstimate;
        } else if (acquisitionWinner == wideEstimate) {
            acquisitionRunnerUp = preferredWindowEstimate;
        } else if (hypothesisGuardOverrideAppliedThisFrame) {
            acquisitionRunnerUp = hypothesisGuardOverrideRunnerUpEstimate;
        }
        if (acquisitionRunnerUp != null
                && acquisitionWinner != null
                && Math.abs(acquisitionRunnerUp.frequencyHz - acquisitionWinner.frequencyHz) < TONE_SCAN_STEP_HZ) {
            acquisitionRunnerUp = null;
        }
        lastAcquisitionRunnerUpFrequencyHz = acquisitionRunnerUp == null ? 0 : acquisitionRunnerUp.frequencyHz;
        lastAcquisitionRunnerUpSelectionScore = acquisitionRunnerUp == null ? 0.0d : acquisitionRunnerUp.selectionScore;
        lastPreferredWindowWinnerLocked = preferredWindowEstimate != null && preferredWindowEstimate.locked;
        lastWideScanWinnerLocked = wideEstimate != null && wideEstimate.locked;
        lastAcquisitionWinnerLocked = acquisitionWinner != null && acquisitionWinner.locked;
        if (hypothesisGuardOverrideAppliedThisFrame && acquisitionWinner != null) {
            lastAcquisitionWinnerSource = AcquisitionWinnerSource.HYPOTHESIS_GUARD;
        } else if (acquisitionWinner == null) {
            lastAcquisitionWinnerSource = AcquisitionWinnerSource.NONE;
        } else if (acquisitionWinner == wideEstimate) {
            lastAcquisitionWinnerSource = AcquisitionWinnerSource.WIDE_SCAN;
        } else {
            lastAcquisitionWinnerSource = AcquisitionWinnerSource.PREFERRED_WINDOW;
        }
    }

    private void rememberPreviousTargetEstimate(
            int previousTargetToneFrequencyHz,
            ToneFrequencyEstimate previousTargetEstimate,
            boolean previousTargetLocked
    ) {
        lastPreviousTargetFrequencyHz = previousTargetToneFrequencyHz;
        lastPreviousTargetToneRms = previousTargetEstimate == null ? 0.0d : previousTargetEstimate.toneRmsAmplitude;
        lastPreviousTargetSelectionScore = previousTargetEstimate == null
                ? 0.0d
                : previousTargetEstimate.selectionScore;
        lastPreviousTargetLocked = previousTargetLocked;
    }

    private ToneFrequencyEstimate withSelectionScore(
            ToneFrequencyEstimate estimate,
            int candidateFrequencyHz,
            int previousTargetToneFrequencyHz,
            boolean wasLocked,
            boolean acquisitionMode
    ) {
        if (estimate == null) {
            return null;
        }
        double weightedScore = scoreToneCandidate(
                candidateFrequencyHz,
                previousTargetToneFrequencyHz,
                wasLocked,
                estimate,
                acquisitionMode
        ) * estimate.toneRmsAmplitude;
        return new ToneFrequencyEstimate(
                estimate.frequencyHz,
                estimate.toneRmsAmplitude,
                estimate.widebandResidualRmsAmplitude,
                estimate.dominanceRatio,
                estimate.isolationRatio,
                estimate.localContrastRatio,
                estimate.locked,
                weightedScore
        );
    }

    private ToneFrequencyEstimate maybeApplyPendingHypothesisGuardOverride(
            short[] samples,
            int sampleRateHz,
            double frameRms,
            int previousTargetToneFrequencyHz,
            ToneFrequencyEstimate searchEstimate
    ) {
        if (!experimentalHypothesisGuardEnabled || !pendingHypothesisGuardEligible || pendingHypothesisGuardFrequencyHz <= 0) {
            return searchEstimate;
        }

        int guardFrequencyHz = clampPreferredToneFrequency(pendingHypothesisGuardFrequencyHz);
        pendingHypothesisGuardEligible = false;
        pendingHypothesisGuardFrequencyHz = 0;

        if (Math.abs(previousTargetToneFrequencyHz - guardFrequencyHz) < HYPOTHESIS_GUARD_MIN_DRIFT_FROM_TARGET_HZ) {
            lastHypothesisGuardDecision = "BLOCKED:TARGET_ALREADY_NEAR_HYP";
            lastAcquisitionDecisionDetail = "hypothesis-guard apply blocked: target already near hypothesis";
            return searchEstimate;
        }

        ToneFrequencyEstimate rawGuardEstimate = evaluateToneEstimate(
                samples,
                sampleRateHz,
                frameRms,
                guardFrequencyHz,
                0.0d
        );
        double guardSelectionScore = scoreToneCandidate(
                guardFrequencyHz,
                guardFrequencyHz,
                false,
                rawGuardEstimate,
                true
        ) * rawGuardEstimate.toneRmsAmplitude;
        ToneFrequencyEstimate guardEstimate = new ToneFrequencyEstimate(
                rawGuardEstimate.frequencyHz,
                rawGuardEstimate.toneRmsAmplitude,
                rawGuardEstimate.widebandResidualRmsAmplitude,
                rawGuardEstimate.dominanceRatio,
                rawGuardEstimate.isolationRatio,
                rawGuardEstimate.localContrastRatio,
                rawGuardEstimate.locked,
                guardSelectionScore
        );

        double guardEvidenceScore = acquisitionEvidenceScore(guardEstimate);
        double guardConfidence = Math.max(narrowbandConfidence(guardEstimate), toneHypothesisConfidence);
        if (!guardEstimate.locked && !isSoftSearchFallbackPlausible(guardEstimate)) {
            lastHypothesisGuardDecision = "BLOCKED:GUARD_ESTIMATE_NOT_PLAUSIBLE";
            lastAcquisitionDecisionDetail = "hypothesis-guard apply blocked: guard estimate not plausible";
            return searchEstimate;
        }
        if (guardConfidence < HYPOTHESIS_GUARD_APPLY_MIN_CONFIDENCE
                && guardEvidenceScore < (MIN_TRACKED_TONE_RMS * 0.80d)) {
            lastHypothesisGuardDecision = "BLOCKED:GUARD_EVIDENCE_TOO_WEAK";
            lastAcquisitionDecisionDetail = "hypothesis-guard apply blocked: guard evidence too weak";
            return searchEstimate;
        }
        if (searchEstimate != null) {
            if (Math.abs(searchEstimate.frequencyHz - guardEstimate.frequencyHz) < TONE_SCAN_STEP_HZ) {
                lastHypothesisGuardDecision = "BLOCKED:SEARCH_ALREADY_NEAR_HYP";
                lastAcquisitionDecisionDetail = "hypothesis-guard apply blocked: search winner already near hypothesis";
                return searchEstimate;
            }
            double searchEvidenceScore = acquisitionEvidenceScore(searchEstimate);
            if (isAcquisitionCandidate(searchEstimate)
                    && searchEvidenceScore > (guardEvidenceScore * HYPOTHESIS_GUARD_APPLY_STRONG_SEARCH_LEAD)
                    && searchEstimate.toneRmsAmplitude > (guardEstimate.toneRmsAmplitude * 1.05d)) {
                lastHypothesisGuardDecision = "BLOCKED:SEARCH_CURRENTLY_STRONGER";
                lastAcquisitionDecisionDetail = "hypothesis-guard apply blocked: current search winner still stronger";
                return searchEstimate;
            }
        }

        hypothesisGuardAppliedThisFrame = true;
        hypothesisGuardOverrideAppliedThisFrame = true;
        hypothesisGuardOverrideRunnerUpEstimate = searchEstimate;
        hypothesisGuardOverrideConfidence = guardConfidence;
        lastHypothesisGuardEligible = true;
        lastHypothesisGuardApplied = true;
        lastHypothesisGuardAppliedFrequencyHz = guardEstimate.frequencyHz;
        totalHypothesisGuardApplyCount += 1;
        lastHypothesisGuardDecision = "APPLIED:ACQUISITION_OVERRIDE";
        lastAcquisitionDecisionDetail =
                "hypothesis guard overrode the acquisition winner after a stable single-peak disagreement";
        return guardEstimate;
    }

    private void rememberFinalAdoptedEstimate(
            ToneFrequencyEstimate finalEstimate,
            AcquisitionWinnerSource source,
            String detail
    ) {
        lastFinalAdoptedFrequencyHz = finalEstimate == null
                ? targetToneFrequencyHz
                : finalEstimate.frequencyHz;
        lastFinalAdoptedToneRms = finalEstimate == null ? 0.0d : finalEstimate.toneRmsAmplitude;
        lastFinalAdoptedSelectionScore = finalEstimate == null ? 0.0d : finalEstimate.selectionScore;
        lastFinalAdoptedConfidence = finalEstimate == null
                ? 0.0d
                : source == AcquisitionWinnerSource.WIDE_SCAN
                ? lastWideScanWinnerConfidence
                : source == AcquisitionWinnerSource.PREFERRED_WINDOW
                ? lastPreferredWindowWinnerConfidence
                : source == AcquisitionWinnerSource.HYPOTHESIS_GUARD
                ? Math.max(lastAcquisitionWinnerConfidence, toneHypothesisConfidence)
                : source == AcquisitionWinnerSource.LOCKED_RETUNE
                ? narrowbandConfidence(finalEstimate)
                : 0.0d;
        lastFinalAdoptedLocked = finalEstimate != null && finalEstimate.locked;
        lastFinalAdoptedSource = source == null ? AcquisitionWinnerSource.NONE : source;
        lastFinalAdoptionDetail = detail == null ? "NONE" : detail;
    }

    private ToneFrequencyEstimate scanLockedRetuneWindow(
            short[] samples,
            int sampleRateHz,
            double frameRms,
            int previousTargetToneFrequencyHz
    ) {
        int centerFrequencyHz = clampPreferredToneFrequency(previousTargetToneFrequencyHz);
        int retuneWindowHz = currentLockRetuneWindowHz();
        return scanToneWindow(
                samples,
                sampleRateHz,
                frameRms,
                Math.max(MIN_TRACKED_TONE_FREQUENCY_HZ, centerFrequencyHz - retuneWindowHz),
                Math.min(MAX_TRACKED_TONE_FREQUENCY_HZ, centerFrequencyHz + retuneWindowHz),
                previousTargetToneFrequencyHz,
                true,
                false,
                null
        ).winner;
    }

    private ToneFrequencyEstimate chooseLockedEstimate(
            ToneFrequencyEstimate lockedWindowEstimate,
            ToneFrequencyEstimate searchEstimate,
            ToneFrequencyEstimate previousTargetEstimate
    ) {
        if (lockedWindowEstimate == null) {
            return searchEstimate;
        }
        if (searchEstimate == null) {
            return lockedWindowEstimate;
        }
        if (!searchEstimate.locked) {
            return lockedWindowEstimate;
        }
        if (!isLockedRetentionQualified(lockedWindowEstimate)) {
            return searchEstimate;
        }
        int searchDriftHz = Math.abs(searchEstimate.frequencyHz - targetToneFrequencyHz);
        if (isDecisiveLockedRetuneOverrideAgainstPlausibleAnchor(
                searchEstimate,
                lockedWindowEstimate,
                previousTargetEstimate,
                searchDriftHz
        )) {
            return searchEstimate;
        }
        if (previousTargetEstimate != null
                && searchDriftHz >= lockedRetuneGuardMinDriftHz()
                && !isLockedRetentionQualified(previousTargetEstimate)
                && isImmediateLockedRetuneOverride(searchEstimate, previousTargetEstimate, searchDriftHz)) {
            return searchEstimate;
        }
        if (previousTargetEstimate != null
                && searchDriftHz >= lockedRetuneGuardMinDriftHz()
                && !isToneActiveStableContinuityAnchor(previousTargetEstimate)
                && isImmediateLockedRetuneOverride(searchEstimate, previousTargetEstimate, searchDriftHz)) {
            return searchEstimate;
        }
        if (maybeApplyLockedWindowFarRetuneGuard(
                lockedWindowEstimate,
                searchEstimate,
                previousTargetEstimate,
                searchDriftHz
        )) {
            return lockedRetuneGuardHoldingThisFrame ? lockedWindowEstimate : searchEstimate;
        }
        int lockedDistanceHz = Math.abs(lockedWindowEstimate.frequencyHz - targetToneFrequencyHz);
        int searchDistanceHz = searchDriftHz;
        if (shouldPreferLockedWindowDuringToneActive(
                lockedWindowEstimate,
                searchEstimate,
                previousTargetEstimate,
                searchDistanceHz
        )) {
            return lockedWindowEstimate;
        }
        if (searchDistanceHz <= lockedDistanceHz
                && searchEstimate.selectionScore > lockedWindowEstimate.selectionScore * 1.18d) {
            return searchEstimate;
        }
        return lockedWindowEstimate;
    }

    private boolean maybeApplyLockedWindowFarRetuneGuard(
            ToneFrequencyEstimate lockedWindowEstimate,
            ToneFrequencyEstimate searchEstimate,
            ToneFrequencyEstimate previousTargetEstimate,
            int searchDistanceHz
    ) {
        if (!toneActive
                || lockedWindowEstimate == null
                || searchEstimate == null
                || !searchEstimate.locked
                || searchDistanceHz < lockedRetuneGuardMinDriftHz()
                || !isToneActiveStableContinuityAnchor(previousTargetEstimate)) {
            return false;
        }
        boolean weakReferenceCandidate = shouldHoldWeakFarLockedRetune(searchEstimate);
        int stableScans = noteLockedRetuneCandidate(searchEstimate.frequencyHz);
        int requiredScans = lockedRetuneGuardRequiredScans(searchDistanceHz, weakReferenceCandidate)
                + TONE_ACTIVE_FAR_SEARCH_PREVIOUS_PLAUSIBLE_EXTRA_SCANS;
        boolean sameRetuneCluster = Math.abs(searchEstimate.frequencyHz - lockedWindowEstimate.frequencyHz)
                <= CANDIDATE_STABILITY_CLUSTER_WINDOW_HZ;
        boolean strongEnoughToBeatLockedWindow = sameRetuneCluster
                || (searchEstimate.selectionScore
                >= (lockedWindowEstimate.selectionScore * TONE_ACTIVE_LOCKED_SEARCH_REPLACE_SCORE_RATIO)
                && searchEstimate.toneRmsAmplitude
                >= (lockedWindowEstimate.toneRmsAmplitude * TONE_ACTIVE_LOCKED_SEARCH_REPLACE_TONE_RATIO));
        boolean strongEnoughToReleasePlausibleAnchor = isFarCandidateStrongEnoughToReleasePlausibleAnchor(
                searchEstimate,
                previousTargetEstimate,
                TONE_ACTIVE_FAR_SEARCH_RELEASE_SCORE_RATIO,
                TONE_ACTIVE_FAR_SEARCH_RELEASE_TONE_RATIO
        );
        boolean decisiveOverride = isDecisiveLockedRetuneOverrideAgainstPlausibleAnchor(
                searchEstimate,
                previousTargetEstimate,
                lockedWindowEstimate,
                searchDistanceHz
        );
        boolean collapsedAnchorOverride = !isLockedRetentionQualified(previousTargetEstimate)
                && stableScans >= CANDIDATE_STABILITY_ACCEPT_SCANS
                && strongEnoughToBeatLockedWindow
                && strongEnoughToReleasePlausibleAnchor;
        boolean holding = (stableScans < requiredScans
                || !strongEnoughToBeatLockedWindow
                || !strongEnoughToReleasePlausibleAnchor)
                && !collapsedAnchorOverride
                && !decisiveOverride;
        noteLockedRetuneGuardFrameState(
                searchEstimate.frequencyHz,
                searchDistanceHz,
                stableScans,
                requiredScans,
                holding
        );
        if (!holding) {
            resetPendingLockedRetuneCandidate(searchEstimate.frequencyHz);
        }
        return true;
    }

    private ToneFrequencyEstimate applyLockedRetuneGuard(
            ToneFrequencyEstimate retainedEstimate,
            ToneFrequencyEstimate previousTargetEstimate
    ) {
        if (!shouldGuardLockedRetuneEstimate(retainedEstimate, previousTargetEstimate)) {
            resetPendingLockedRetuneCandidate(retainedEstimate == null ? targetToneFrequencyHz : retainedEstimate.frequencyHz);
            return retainedEstimate;
        }
        if (previousTargetEstimate == null) {
            resetPendingLockedRetuneCandidate(retainedEstimate.frequencyHz);
            return retainedEstimate;
        }
        int driftHz = Math.abs(retainedEstimate.frequencyHz - targetToneFrequencyHz);
        boolean weakReferenceCandidate = shouldHoldWeakFarLockedRetune(retainedEstimate);
        boolean previousTargetPlausible = isToneActiveStableContinuityAnchor(previousTargetEstimate);
        if (!isLockedRetentionQualified(previousTargetEstimate)) {
            if (previousTargetPlausible) {
                int stableScans = noteLockedRetuneCandidate(retainedEstimate.frequencyHz);
                int requiredScans = lockedRetuneGuardRequiredScans(driftHz, weakReferenceCandidate)
                        + TONE_ACTIVE_FAR_SEARCH_PREVIOUS_PLAUSIBLE_EXTRA_SCANS;
                boolean confirmed = stableScans >= requiredScans;
                noteLockedRetuneGuardFrameState(
                        retainedEstimate.frequencyHz,
                        driftHz,
                        stableScans,
                        requiredScans,
                        !confirmed
                );
                if (!confirmed) {
                    return previousTargetEstimate;
                }
                resetPendingLockedRetuneCandidate(retainedEstimate.frequencyHz);
                return retainedEstimate;
            }
            if (weakReferenceCandidate) {
                int requiredScans = lockedRetuneGuardRequiredScans(driftHz, true);
                noteLockedRetuneGuardFrameState(
                        retainedEstimate.frequencyHz,
                        driftHz,
                        0,
                        requiredScans,
                        true
                );
                return previousTargetEstimate;
            }
            resetPendingLockedRetuneCandidate(retainedEstimate.frequencyHz);
            return retainedEstimate;
        }
        int stableScans = noteLockedRetuneCandidate(retainedEstimate.frequencyHz);
        int requiredScans = lockedRetuneGuardRequiredScans(driftHz, weakReferenceCandidate);
        if (previousTargetPlausible) {
            requiredScans += TONE_ACTIVE_FAR_SEARCH_PREVIOUS_PLAUSIBLE_EXTRA_SCANS;
        }
        boolean decisiveOverride = previousTargetPlausible
                && isDecisiveLockedRetuneOverrideAgainstPlausibleAnchor(
                retainedEstimate,
                previousTargetEstimate,
                null,
                driftHz
        );
        boolean immediateOverride = decisiveOverride
                || (!previousTargetPlausible
                && isImmediateLockedRetuneOverride(retainedEstimate, previousTargetEstimate, driftHz));
        boolean confirmed = stableScans >= requiredScans;
        noteLockedRetuneGuardFrameState(
                retainedEstimate.frequencyHz,
                driftHz,
                stableScans,
                requiredScans,
                !confirmed && !immediateOverride
        );
        if (confirmed || immediateOverride) {
            resetPendingLockedRetuneCandidate(retainedEstimate.frequencyHz);
            return retainedEstimate;
        }
        return previousTargetEstimate;
    }

    private boolean shouldHoldWeakFarLockedRetune(ToneFrequencyEstimate retainedEstimate) {
        if (retainedEstimate == null || lockedBranchReferenceToneRmsAmplitude <= 0.0d) {
            return false;
        }
        return retainedEstimate.toneRmsAmplitude
                < (lockedBranchReferenceToneRmsAmplitude * LOCKED_RETUNE_GUARD_WEAK_REFERENCE_TONE_RATIO);
    }

    private ToneFrequencyEstimate maybeApplyToneActivePlausiblePreviousTargetGuard(
            ToneFrequencyEstimate retainedEstimate,
            ToneFrequencyEstimate previousTargetEstimate
    ) {
        if (retainedEstimate == null
                || previousTargetEstimate == null
                || !toneActive
                || consecutiveLockedFrames < LOCKED_RETUNE_GUARD_MIN_STABLE_LOCK_FRAMES
                || Math.abs(previousTargetEstimate.frequencyHz - targetToneFrequencyHz) > TONE_SCAN_STEP_HZ
                || !isToneActiveStableContinuityAnchor(previousTargetEstimate)) {
            return retainedEstimate;
        }
        int driftHz = Math.abs(retainedEstimate.frequencyHz - targetToneFrequencyHz);
        if (driftHz < lockedRetuneGuardMinDriftHz()) {
            return retainedEstimate;
        }
        if (hasLockedRetuneGuardFrameState()) {
            return lockedRetuneGuardHoldingThisFrame ? previousTargetEstimate : retainedEstimate;
        }
        boolean weakReferenceCandidate = shouldHoldWeakFarLockedRetune(retainedEstimate);
        int stableScans = noteLockedRetuneCandidate(retainedEstimate.frequencyHz);
        int requiredScans = lockedRetuneGuardRequiredScans(driftHz, weakReferenceCandidate)
                + TONE_ACTIVE_FAR_SEARCH_PREVIOUS_PLAUSIBLE_EXTRA_SCANS;
        boolean decisiveOverride = isDecisiveLockedRetuneOverrideAgainstPlausibleAnchor(
                retainedEstimate,
                previousTargetEstimate,
                null,
                driftHz
        );
        boolean immediateOverride = decisiveOverride
                || (!weakReferenceCandidate
                && isImmediateLockedRetuneOverride(retainedEstimate, previousTargetEstimate, driftHz));
        if (immediateOverride) {
            noteLockedRetuneGuardFrameState(
                    retainedEstimate.frequencyHz,
                    driftHz,
                    stableScans,
                    requiredScans,
                    false
            );
            resetPendingLockedRetuneCandidate(retainedEstimate.frequencyHz);
            return retainedEstimate;
        }
        boolean confirmed = stableScans >= requiredScans;
        boolean collapsedReferenceOverride = !isLockedRetentionQualified(previousTargetEstimate)
                && stableScans >= CANDIDATE_STABILITY_ACCEPT_SCANS
                && !weakReferenceCandidate
                && isImmediateLockedRetuneOverride(retainedEstimate, previousTargetEstimate, driftHz);
        boolean strongEnoughToRelease = isFarCandidateStrongEnoughToReleasePlausibleAnchor(
                retainedEstimate,
                previousTargetEstimate,
                TONE_ACTIVE_FAR_SEARCH_RELEASE_SCORE_RATIO,
                TONE_ACTIVE_FAR_SEARCH_RELEASE_TONE_RATIO
        );
        boolean holding = ((!confirmed && !collapsedReferenceOverride) || !strongEnoughToRelease)
                && !decisiveOverride;
        noteLockedRetuneGuardFrameState(
                retainedEstimate.frequencyHz,
                driftHz,
                stableScans,
                requiredScans,
                holding
        );
        if (holding) {
            return previousTargetEstimate;
        }
        resetPendingLockedRetuneCandidate(retainedEstimate.frequencyHz);
        return retainedEstimate;
    }

    private boolean hasLockedRetuneGuardFrameState() {
        return lockedRetuneGuardHoldingThisFrame
                || lockedRetuneGuardCandidateFrequencyHz > 0
                || lockedRetuneGuardObservedScans > 0
                || lockedRetuneGuardRequiredScans > 0;
    }

    private boolean isToneActiveContinuityHeldRetainedEstimate(
            ToneFrequencyEstimate retainedEstimate,
            ToneFrequencyEstimate previousTargetEstimate
    ) {
        if (retainedEstimate == null
                || previousTargetEstimate == null
                || !hasLockedRetuneGuardFrameState()
                || !toneActive) {
            return false;
        }
        if (isLockedRetentionQualified(retainedEstimate)) {
            return false;
        }
        return Math.abs(retainedEstimate.frequencyHz - previousTargetEstimate.frequencyHz) <= TONE_SCAN_STEP_HZ
                && isToneActivePreviousTargetAnchorPresent(retainedEstimate);
    }

    private boolean shouldPreferLockedWindowDuringToneActive(
            ToneFrequencyEstimate lockedWindowEstimate,
            ToneFrequencyEstimate searchEstimate,
            ToneFrequencyEstimate previousTargetEstimate,
            int searchDistanceHz
    ) {
        if (!toneActive
                || lockedWindowEstimate == null
                || searchEstimate == null
                || searchDistanceHz < lockedRetuneGuardMinDriftHz()) {
            return false;
        }
        if (!isToneActiveStableContinuityAnchor(previousTargetEstimate)) {
            return false;
        }
        return searchEstimate.selectionScore
                < (lockedWindowEstimate.selectionScore * TONE_ACTIVE_LOCKED_SEARCH_REPLACE_SCORE_RATIO)
                || searchEstimate.toneRmsAmplitude
                < (lockedWindowEstimate.toneRmsAmplitude * TONE_ACTIVE_LOCKED_SEARCH_REPLACE_TONE_RATIO);
    }

    private boolean shouldHoldToneActiveFarSearchCandidate(
            ToneFrequencyEstimate searchEstimate,
            ToneFrequencyEstimate previousTargetEstimate,
            boolean continuityMode
    ) {
        if (!toneActive
                || !continuityMode
                || searchEstimate == null
                || previousTargetEstimate == null
                || !isAcquisitionCandidate(searchEstimate)) {
            return false;
        }
        int driftHz = Math.abs(searchEstimate.frequencyHz - targetToneFrequencyHz);
        if (driftHz < lockedRetuneGuardMinDriftHz()) {
            return false;
        }
        boolean previousTargetStableAnchor = isToneActiveStableContinuityAnchor(previousTargetEstimate);
        if (previousTargetStableAnchor
                && isDecisiveLockedRetuneOverrideAgainstPlausibleAnchor(
                searchEstimate,
                previousTargetEstimate,
                null,
                driftHz
        )) {
            int stableScans = noteLockedRetuneCandidate(searchEstimate.frequencyHz);
            int requiredScans = lockedRetuneGuardRequiredScans(
                    driftHz,
                    shouldHoldWeakFarLockedRetune(searchEstimate)
            );
            noteLockedRetuneGuardFrameState(
                    searchEstimate.frequencyHz,
                    driftHz,
                    stableScans,
                    requiredScans,
                    false
            );
            resetPendingLockedRetuneCandidate(searchEstimate.frequencyHz);
            return false;
        }
        if (isImmediateToneActiveFarSearchOverride(searchEstimate, previousTargetEstimate)) {
            return false;
        }
        int stableScans = noteLockedRetuneCandidate(searchEstimate.frequencyHz);
        boolean previousTargetPlausible = previousTargetStableAnchor
                || isToneActiveContinuityTargetPlausible(previousTargetEstimate);
        boolean weakReferenceCandidate = shouldHoldWeakFarLockedRetune(searchEstimate);
        int requiredScans = lockedRetuneGuardRequiredScans(driftHz, weakReferenceCandidate);
        if (previousTargetStableAnchor) {
            requiredScans += TONE_ACTIVE_FAR_SEARCH_PREVIOUS_PLAUSIBLE_EXTRA_SCANS;
        }
        boolean hold = stableScans < requiredScans;
        if (!hold && previousTargetPlausible) {
            hold = !isFarCandidateStrongEnoughToReleasePlausibleAnchor(
                    searchEstimate,
                    previousTargetEstimate,
                    TONE_ACTIVE_FAR_SEARCH_RELEASE_SCORE_RATIO,
                    TONE_ACTIVE_FAR_SEARCH_RELEASE_TONE_RATIO
            );
        }
        noteLockedRetuneGuardFrameState(
                searchEstimate.frequencyHz,
                driftHz,
                stableScans,
                requiredScans,
                hold
        );
        return hold;
    }

    private boolean isImmediateToneActiveFarSearchOverride(
            ToneFrequencyEstimate searchEstimate,
            ToneFrequencyEstimate previousTargetEstimate
    ) {
        if (searchEstimate == null || previousTargetEstimate == null) {
            return false;
        }
        double previousSelectionScore = Math.max(1.0d, previousTargetEstimate.selectionScore);
        double previousToneRms = Math.max(1.0d, previousTargetEstimate.toneRmsAmplitude);
        return searchEstimate.selectionScore
                >= (previousSelectionScore * TONE_ACTIVE_FAR_SEARCH_IMMEDIATE_SCORE_RATIO)
                && searchEstimate.toneRmsAmplitude
                >= (previousToneRms * TONE_ACTIVE_FAR_SEARCH_IMMEDIATE_TONE_RATIO);
    }

    private boolean isFarCandidateStrongEnoughToReleasePlausibleAnchor(
            ToneFrequencyEstimate candidateEstimate,
            ToneFrequencyEstimate previousTargetEstimate,
            double scoreRatio,
            double toneRatio
    ) {
        if (candidateEstimate == null || previousTargetEstimate == null) {
            return false;
        }
        double previousSelectionScore = Math.max(1.0d, previousTargetEstimate.selectionScore);
        double previousToneRms = Math.max(1.0d, previousTargetEstimate.toneRmsAmplitude);
        return candidateEstimate.selectionScore >= (previousSelectionScore * scoreRatio)
                && candidateEstimate.toneRmsAmplitude >= (previousToneRms * toneRatio);
    }

    private boolean isToneActiveContinuityTargetPlausible(ToneFrequencyEstimate previousTargetEstimate) {
        if (previousTargetEstimate == null) {
            return false;
        }
        if (isLockedRetentionQualified(previousTargetEstimate)) {
            return true;
        }
        return previousTargetEstimate.toneRmsAmplitude >= (MIN_TRACKED_TONE_RMS * 0.90d)
                && previousTargetEstimate.dominanceRatio >= (MIN_NARROWBAND_DOMINANCE_RATIO * 0.90d)
                && (previousTargetEstimate.isolationRatio >= (MIN_NARROWBAND_ISOLATION_RATIO * 0.82d)
                || previousTargetEstimate.localContrastRatio >= (MIN_NARROWBAND_LOCAL_CONTRAST_RATIO * 0.84d));
    }

    private boolean isToneActiveStableContinuityAnchor(ToneFrequencyEstimate previousTargetEstimate) {
        return previousTargetEstimate != null
                && toneActive
                && consecutiveLockedFrames >= LOCKED_RETUNE_GUARD_MIN_STABLE_LOCK_FRAMES
                && Math.abs(previousTargetEstimate.frequencyHz - targetToneFrequencyHz) <= TONE_SCAN_STEP_HZ
                && isToneActivePreviousTargetAnchorPresent(previousTargetEstimate);
    }

    private boolean isToneActivePreviousTargetAnchorPresent(ToneFrequencyEstimate previousTargetEstimate) {
        if (previousTargetEstimate == null) {
            return false;
        }
        if (previousTargetEstimate.toneRmsAmplitude
                < (MIN_TRACKED_TONE_RMS * TONE_ACTIVE_PREVIOUS_TARGET_MIN_TONE_MULTIPLIER)) {
            return false;
        }
        if (previousTargetEstimate.dominanceRatio < TONE_ACTIVE_PREVIOUS_TARGET_MIN_DOMINANCE_RATIO) {
            return false;
        }
        return lockedBranchReferenceToneRmsAmplitude <= 0.0d
                || previousTargetEstimate.toneRmsAmplitude
                >= (lockedBranchReferenceToneRmsAmplitude * TONE_ACTIVE_PREVIOUS_TARGET_MIN_REFERENCE_TONE_RATIO);
    }

    private ToneFrequencyEstimate maybeApplyExperimentalLockedConsensusGuard(
            ToneFrequencyEstimate candidateEstimate,
            ToneFrequencyEstimate lockedConsensusEstimate
    ) {
        if (!experimentalHypothesisGuardEnabled || candidateEstimate == null || lockedConsensusEstimate == null) {
            return candidateEstimate;
        }
        int candidateDriftHz = Math.abs(candidateEstimate.frequencyHz - lockedConsensusEstimate.frequencyHz);
        if (candidateDriftHz < LOCKED_CONSENSUS_GUARD_MIN_DRIFT_HZ) {
            return candidateEstimate;
        }
        hypothesisGuardAppliedThisFrame = true;
        lastHypothesisGuardEligible = true;
        lastHypothesisGuardApplied = true;
        lastHypothesisGuardAppliedFrequencyHz = lockedConsensusEstimate.frequencyHz;
        totalHypothesisGuardApplyCount += 1;
        lastHypothesisGuardDecision = "APPLIED:LOCKED_CONSENSUS_RETUNE";
        return lockedConsensusEstimate;
    }

    private boolean shouldGuardLockedRetuneEstimate(
            ToneFrequencyEstimate retainedEstimate,
            ToneFrequencyEstimate previousTargetEstimate
    ) {
        if (retainedEstimate == null || previousTargetEstimate == null) {
            return false;
        }
        if (!toneActive || consecutiveLockedFrames < LOCKED_RETUNE_GUARD_MIN_STABLE_LOCK_FRAMES) {
            return false;
        }
        if (Math.abs(previousTargetEstimate.frequencyHz - targetToneFrequencyHz) > TONE_SCAN_STEP_HZ) {
            return false;
        }
        return Math.abs(retainedEstimate.frequencyHz - targetToneFrequencyHz) >= lockedRetuneGuardMinDriftHz();
    }

    private int lockedRetuneGuardRequiredScans(int driftHz, boolean weakReferenceCandidate) {
        int farDriftHz = experimentalLockedRetuneGuardTuning == null
                ? LOCKED_RETUNE_GUARD_FAR_DRIFT_HZ
                : experimentalLockedRetuneGuardTuning.farDriftHz();
        int requiredScans = driftHz >= farDriftHz
                ? experimentalLockedRetuneGuardTuning == null
                ? LOCKED_RETUNE_GUARD_FAR_REQUIRED_SCANS
                : experimentalLockedRetuneGuardTuning.farRequiredScans()
                : experimentalLockedRetuneGuardTuning == null
                ? LOCKED_RETUNE_GUARD_REQUIRED_SCANS
                : experimentalLockedRetuneGuardTuning.requiredScans();
        if (weakReferenceCandidate) {
            requiredScans += experimentalLockedRetuneGuardTuning == null
                    ? LOCKED_RETUNE_GUARD_WEAK_REFERENCE_EXTRA_SCANS
                    : experimentalLockedRetuneGuardTuning.weakReferenceExtraScans();
        }
        return requiredScans;
    }

    private boolean isImmediateLockedRetuneOverride(
            ToneFrequencyEstimate retainedEstimate,
            ToneFrequencyEstimate previousTargetEstimate,
            int driftHz
    ) {
        int farDriftHz = experimentalLockedRetuneGuardTuning == null
                ? LOCKED_RETUNE_GUARD_FAR_DRIFT_HZ
                : experimentalLockedRetuneGuardTuning.farDriftHz();
        double scoreRatio = driftHz >= farDriftHz
                ? LOCKED_RETUNE_GUARD_FAR_STRONG_SCORE_RATIO
                : LOCKED_RETUNE_GUARD_STRONG_SCORE_RATIO;
        double toneRatio = driftHz >= farDriftHz
                ? LOCKED_RETUNE_GUARD_FAR_STRONG_TONE_RATIO
                : LOCKED_RETUNE_GUARD_STRONG_TONE_RATIO;
        return retainedEstimate.selectionScore >= (previousTargetEstimate.selectionScore * scoreRatio)
                && retainedEstimate.toneRmsAmplitude >= (previousTargetEstimate.toneRmsAmplitude * toneRatio);
    }

    private boolean isDecisiveLockedRetuneOverrideAgainstPlausibleAnchor(
            ToneFrequencyEstimate candidateEstimate,
            ToneFrequencyEstimate primaryReferenceEstimate,
            ToneFrequencyEstimate secondaryReferenceEstimate,
            int driftHz
    ) {
        if (candidateEstimate == null
                || primaryReferenceEstimate == null
                || !candidateEstimate.locked
                || driftHz < lockedRetuneGuardMinDriftHz()) {
            return false;
        }
        double referenceScore = 0.0d;
        double referenceTone = 0.0d;
        boolean hasDistinctReference = false;
        if (!isSameRetuneClusterReference(candidateEstimate, primaryReferenceEstimate)) {
            referenceScore = Math.max(1.0d, primaryReferenceEstimate.selectionScore);
            referenceTone = Math.max(1.0d, primaryReferenceEstimate.toneRmsAmplitude);
            hasDistinctReference = true;
        }
        if (!isSameRetuneClusterReference(candidateEstimate, secondaryReferenceEstimate)) {
            referenceScore = hasDistinctReference
                    ? Math.max(referenceScore, Math.max(1.0d, secondaryReferenceEstimate.selectionScore))
                    : Math.max(1.0d, secondaryReferenceEstimate.selectionScore);
            referenceTone = hasDistinctReference
                    ? Math.max(referenceTone, Math.max(1.0d, secondaryReferenceEstimate.toneRmsAmplitude))
                    : Math.max(1.0d, secondaryReferenceEstimate.toneRmsAmplitude);
            hasDistinctReference = true;
        }
        if (!hasDistinctReference) {
            return false;
        }
        return candidateEstimate.selectionScore >= (referenceScore * LOCKED_RETUNE_GUARD_DECISIVE_SCORE_RATIO)
                && candidateEstimate.toneRmsAmplitude >= (referenceTone * LOCKED_RETUNE_GUARD_DECISIVE_TONE_RATIO)
                && candidateEstimate.dominanceRatio >= LOCKED_RETUNE_GUARD_DECISIVE_DOMINANCE_RATIO
                && (candidateEstimate.isolationRatio >= LOCKED_RETUNE_GUARD_DECISIVE_ISOLATION_RATIO
                || candidateEstimate.localContrastRatio >= LOCKED_RETUNE_GUARD_DECISIVE_LOCAL_CONTRAST_RATIO);
    }

    private boolean isSameRetuneClusterReference(
            ToneFrequencyEstimate candidateEstimate,
            ToneFrequencyEstimate referenceEstimate
    ) {
        return candidateEstimate == null
                || referenceEstimate == null
                || Math.abs(candidateEstimate.frequencyHz - referenceEstimate.frequencyHz)
                <= CANDIDATE_STABILITY_CLUSTER_WINDOW_HZ;
    }

    private int noteLockedRetuneCandidate(int candidateFrequencyHz) {
        if (Math.abs(candidateFrequencyHz - pendingLockedRetuneFrequencyHz) <= CANDIDATE_STABILITY_CLUSTER_WINDOW_HZ) {
            pendingLockedRetuneStableScans += 1;
        } else {
            pendingLockedRetuneFrequencyHz = candidateFrequencyHz;
            pendingLockedRetuneStableScans = 1;
        }
        return pendingLockedRetuneStableScans;
    }

    private void resetPendingLockedRetuneCandidate(int referenceFrequencyHz) {
        pendingLockedRetuneFrequencyHz = referenceFrequencyHz > 0 ? referenceFrequencyHz : targetToneFrequencyHz;
        pendingLockedRetuneStableScans = 0;
    }

    private void clearLockedRetuneGuardFrameState() {
        lockedRetuneGuardHoldingThisFrame = false;
        lockedRetuneGuardCandidateFrequencyHz = 0;
        lockedRetuneGuardDriftHz = 0;
        lockedRetuneGuardObservedScans = 0;
        lockedRetuneGuardRequiredScans = 0;
        lockedRetuneGuardRemainingScans = 0;
        lockedRetuneGuardBand = "NONE";
    }

    private void noteLockedRetuneGuardFrameState(
            int candidateFrequencyHz,
            int driftHz,
            int observedScans,
            int requiredScans,
            boolean holding
    ) {
        lockedRetuneGuardHoldingThisFrame = holding;
        lockedRetuneGuardCandidateFrequencyHz = candidateFrequencyHz;
        lockedRetuneGuardDriftHz = driftHz;
        lockedRetuneGuardObservedScans = observedScans;
        lockedRetuneGuardRequiredScans = requiredScans;
        lockedRetuneGuardRemainingScans = Math.max(0, requiredScans - observedScans);
        int farDriftHz = experimentalLockedRetuneGuardTuning == null
                ? LOCKED_RETUNE_GUARD_FAR_DRIFT_HZ
                : experimentalLockedRetuneGuardTuning.farDriftHz();
        lockedRetuneGuardBand = driftHz >= farDriftHz ? "FAR" : "MID";
    }

    private boolean isAcquisitionCandidate(ToneFrequencyEstimate estimate) {
        if (estimate == null) {
            return false;
        }
        if (estimate.locked) {
            return true;
        }
        return estimate.toneRmsAmplitude >= (MIN_TRACKED_TONE_RMS * 1.20d)
                && estimate.dominanceRatio >= (MIN_NARROWBAND_DOMINANCE_RATIO * 1.15d)
                && (estimate.isolationRatio >= (MIN_NARROWBAND_ISOLATION_RATIO * 1.10d)
                || estimate.localContrastRatio >= MIN_NARROWBAND_LOCAL_CONTRAST_RATIO);
    }

    private boolean isImmediateLockCandidate(ToneFrequencyEstimate estimate) {
        return estimate != null
                && estimate.toneRmsAmplitude >= (MIN_TRACKED_TONE_RMS * 1.8d)
                && estimate.dominanceRatio >= (MIN_LOCK_DOMINANCE_RATIO * 1.10d)
                && (estimate.isolationRatio >= MIN_LOCK_ISOLATION_RATIO
                || estimate.localContrastRatio >= MIN_LOCK_LOCAL_CONTRAST_RATIO);
    }

    private boolean isLockedRetentionQualified(ToneFrequencyEstimate estimate) {
        if (estimate == null) {
            return false;
        }
        return isLockedRetentionQualified(
                estimate.toneRmsAmplitude,
                estimate.dominanceRatio,
                estimate.isolationRatio,
                estimate.localContrastRatio
        );
    }

    private boolean isLockedRetentionQualified(
            double trackedToneRms,
            double dominanceRatio,
            double isolationRatio,
            double localContrastRatio
    ) {
        boolean toneActiveLockRetention = toneActive
                && trackedToneRms >= MIN_TRACKED_TONE_RMS
                && dominanceRatio >= ACTIVE_TONE_LOCK_DOMINANCE_RATIO
                && (isolationRatio >= ACTIVE_TONE_LOCK_ISOLATION_RATIO
                || localContrastRatio >= ACTIVE_TONE_LOCK_LOCAL_CONTRAST_RATIO);
        return toneActiveLockRetention || (trackedToneRms >= MIN_TRACKED_TONE_RMS
                && dominanceRatio >= (MIN_LOCK_DOMINANCE_RATIO * 0.75d)
                && (isolationRatio >= (MIN_LOCK_ISOLATION_RATIO * 0.80d)
                || localContrastRatio >= MIN_LOCK_LOCAL_CONTRAST_RATIO));
    }

    private ToneScanResult scanPreferredToneWindow(
            short[] samples,
            int sampleRateHz,
            double frameRms,
            int previousTargetToneFrequencyHz,
            boolean wasLocked
    ) {
        int scanWindowHz = wasLocked
                ? currentPreferredToneScanWindowHz()
                : currentUnlockedAcquisitionScanWindowHz();
        int searchMin = Math.max(MIN_TRACKED_TONE_FREQUENCY_HZ, preferredToneFrequencyHz - scanWindowHz);
        int searchMax = Math.min(MAX_TRACKED_TONE_FREQUENCY_HZ, preferredToneFrequencyHz + scanWindowHz);
        // This scan is intentionally centered on preferred tone, but it is still only
        // evidence gathering. It must not be treated as an implicit command to adopt
        // the preferred region unless later selection logic confirms it.
        // Important: when not already locked, this window is still part of acquisition.
        // It should use acquisition scoring softness instead of the heavier locked-time
        // preferred weighting, or the preferred prior becomes a hidden hard anchor again.
        return scanToneWindow(
                samples,
                sampleRateHz,
                frameRms,
                searchMin,
                searchMax,
                previousTargetToneFrequencyHz,
                wasLocked,
                !wasLocked,
                "preferred"
        );
    }

    private ToneScanResult scanWideAcquisitionWindow(
            short[] samples,
            int sampleRateHz,
            double frameRms,
            int previousTargetToneFrequencyHz
    ) {
        if (rxToneMode == RxToneMode.FIXED_TONE) {
            int searchMin = Math.max(
                    MIN_TRACKED_TONE_FREQUENCY_HZ,
                    preferredToneFrequencyHz - FIXED_TONE_BOOTSTRAP_ESCAPE_SCAN_WINDOW_HZ
            );
            int searchMax = Math.min(
                    MAX_TRACKED_TONE_FREQUENCY_HZ,
                    preferredToneFrequencyHz + FIXED_TONE_BOOTSTRAP_ESCAPE_SCAN_WINDOW_HZ
            );
            return scanToneWindow(
                    samples,
                    sampleRateHz,
                    frameRms,
                    searchMin,
                    searchMax,
                    previousTargetToneFrequencyHz,
                    false,
                    true,
                    "wide"
            );
        }
        // Wide acquisition is the counterweight that prevents preferred tone from
        // becoming a de facto hard anchor when the true CW peak sits elsewhere.
        return scanToneWindow(
                samples,
                sampleRateHz,
                frameRms,
                MIN_TRACKED_TONE_FREQUENCY_HZ,
                MAX_TRACKED_TONE_FREQUENCY_HZ,
                previousTargetToneFrequencyHz,
                false,
                true,
                "wide"
        );
    }

    private ToneScanResult scanToneWindow(
            short[] samples,
            int sampleRateHz,
            double frameRms,
            int searchMin,
            int searchMax,
            int previousTargetToneFrequencyHz,
            boolean wasLocked,
            boolean acquisitionMode,
            String scanLabel
    ) {
        List<ToneFrequencyEstimate> topCandidates = new ArrayList<>(3);
        int seedFrequencyHz = acquisitionMode
                ? searchMin
                : Math.max(searchMin, Math.min(searchMax, preferredToneFrequencyHz));
        ToneFrequencyEstimate bestEstimate = evaluateToneEstimate(
                samples,
                sampleRateHz,
                frameRms,
                seedFrequencyHz,
                0.0d
        );
        double bestWeightedScore = scoreToneCandidate(
                seedFrequencyHz,
                previousTargetToneFrequencyHz,
                wasLocked,
                bestEstimate,
                acquisitionMode
        ) * bestEstimate.toneRmsAmplitude;
        bestEstimate = new ToneFrequencyEstimate(
                bestEstimate.frequencyHz,
                bestEstimate.toneRmsAmplitude,
                bestEstimate.widebandResidualRmsAmplitude,
                bestEstimate.dominanceRatio,
                bestEstimate.isolationRatio,
                bestEstimate.localContrastRatio,
                bestEstimate.locked,
                bestWeightedScore
        );
        collectTopCandidates(topCandidates, bestEstimate);
        ToneFrequencyEstimate runnerUpEstimate = null;
        double runnerUpWeightedScore = Double.NEGATIVE_INFINITY;
        for (int frequencyHz = searchMin; frequencyHz <= searchMax; frequencyHz += TONE_SCAN_STEP_HZ) {
            ToneFrequencyEstimate estimate = evaluateToneEstimate(
                    samples,
                    sampleRateHz,
                    frameRms,
                    frequencyHz,
                    0.0d
            );
            double weightedScore = scoreToneCandidate(
                    frequencyHz,
                    previousTargetToneFrequencyHz,
                    wasLocked,
                    estimate,
                    acquisitionMode
            )
                    * estimate.toneRmsAmplitude;
            ToneFrequencyEstimate weightedEstimate = new ToneFrequencyEstimate(
                    estimate.frequencyHz,
                    estimate.toneRmsAmplitude,
                    estimate.widebandResidualRmsAmplitude,
                    estimate.dominanceRatio,
                    estimate.isolationRatio,
                    estimate.localContrastRatio,
                    estimate.locked,
                    weightedScore
            );
            collectTopCandidates(topCandidates, weightedEstimate);
            if (weightedScore > bestWeightedScore) {
                runnerUpEstimate = bestEstimate;
                runnerUpWeightedScore = bestWeightedScore;
                bestWeightedScore = weightedScore;
                bestEstimate = weightedEstimate;
            } else if (weightedScore > runnerUpWeightedScore
                    && Math.abs(estimate.frequencyHz - bestEstimate.frequencyHz) >= TONE_SCAN_STEP_HZ) {
                runnerUpWeightedScore = weightedScore;
                runnerUpEstimate = weightedEstimate;
            }
        }
        if (acquisitionMode) {
            ToneScanResult rebalanced = rebalanceWeakAbsoluteEdgeWinner(
                    bestEstimate,
                    runnerUpEstimate,
                    previousTargetToneFrequencyHz
            );
            bestEstimate = rebalanced.winner;
            runnerUpEstimate = rebalanced.runnerUp;
            if ("preferred".equals(scanLabel)) {
                ToneScanResult preferredEdgeRebalanced = rebalancePreferredWindowEdgeWinner(
                        bestEstimate,
                        runnerUpEstimate,
                        topCandidates
                );
                bestEstimate = preferredEdgeRebalanced.winner;
                runnerUpEstimate = preferredEdgeRebalanced.runnerUp;
            }
        }
        rememberTopCandidatesSummary(scanLabel, topCandidates);
        return new ToneScanResult(
                bestEstimate,
                runnerUpEstimate,
                scanWinnerConfidence(bestEstimate, runnerUpEstimate)
        );
    }

    private void collectTopCandidates(List<ToneFrequencyEstimate> topCandidates, ToneFrequencyEstimate candidate) {
        if (candidate == null) {
            return;
        }
        for (int index = 0; index < topCandidates.size(); index++) {
            ToneFrequencyEstimate existing = topCandidates.get(index);
            if (Math.abs(existing.frequencyHz - candidate.frequencyHz) < TONE_SCAN_STEP_HZ) {
                if (candidate.selectionScore > existing.selectionScore) {
                    topCandidates.set(index, candidate);
                }
                sortTopCandidates(topCandidates);
                trimTopCandidates(topCandidates);
                return;
            }
        }
        topCandidates.add(candidate);
        sortTopCandidates(topCandidates);
        trimTopCandidates(topCandidates);
    }

    private void sortTopCandidates(List<ToneFrequencyEstimate> topCandidates) {
        topCandidates.sort((left, right) -> Double.compare(right.selectionScore, left.selectionScore));
    }

    private void trimTopCandidates(List<ToneFrequencyEstimate> topCandidates) {
        while (topCandidates.size() > 3) {
            topCandidates.remove(topCandidates.size() - 1);
        }
    }

    private void rememberTopCandidatesSummary(String scanLabel, List<ToneFrequencyEstimate> topCandidates) {
        if (scanLabel == null) {
            return;
        }
        String summary = renderTopCandidatesSummary(topCandidates);
        if ("preferred".equals(scanLabel)) {
            lastPreferredWindowTopCandidatesSummary = summary;
        } else if ("wide".equals(scanLabel)) {
            lastWideScanTopCandidatesSummary = summary;
        }
    }

    private String renderTopCandidatesSummary(List<ToneFrequencyEstimate> topCandidates) {
        if (topCandidates == null || topCandidates.isEmpty()) {
            return "NONE";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < topCandidates.size(); index++) {
            ToneFrequencyEstimate candidate = topCandidates.get(index);
            if (index > 0) {
                builder.append(" || ");
            }
            builder.append('#').append(index + 1)
                    .append(' ')
                    .append(candidate.frequencyHz).append("Hz")
                    .append(" score ").append(String.format(Locale.US, "%.1f", candidate.selectionScore))
                    .append(" rms ").append(String.format(Locale.US, "%.1f", candidate.toneRmsAmplitude))
                    .append(" dom ").append(Math.round(candidate.dominanceRatio * 100.0d)).append('%')
                    .append(" iso ").append(Math.round(candidate.isolationRatio * 100.0d)).append('%')
                    .append(" ctr ").append(Math.round(candidate.localContrastRatio * 100.0d)).append('%')
                    .append(candidate.locked ? " LOCK" : " cand");
        }
        return builder.toString();
    }

    private boolean shouldRunWideAcquisitionScan(ToneScanResult preferredWindowScan, boolean wasLocked) {
        if (experimentalForceWideAcquisitionEnabled && !wasLocked) {
            return true;
        }
        if (rxToneMode == RxToneMode.FIXED_TONE && !wasLocked) {
            return true;
        }
        ToneFrequencyEstimate preferredWindowEstimate = preferredWindowScan.winner;
        // If this gate is too strict, preferred-window bias comes back indirectly even
        // when no explicit "target = preferred" assignment exists.
        return !wasLocked
                && isWideAcquisitionAllowedInCurrentMode()
                && (preferredWindowEstimate == null
                || !isPreferredWindowAcquisitionSufficient(preferredWindowEstimate)
                || preferredWindowScan.winnerConfidence < 0.58d
                || isPreferredWindowEdgeEstimate(preferredWindowEstimate));
    }

    private boolean isWideAcquisitionAllowedInCurrentMode() {
        if (rxToneMode != RxToneMode.FIXED_TONE) {
            return true;
        }
        return !toneActive
                && trackingState != TrackingState.LOCKED
                && totalToneOnEvents == 0
                && totalToneOffEvents == 0
                && processedFrameCount >= FIXED_TONE_BOOTSTRAP_ESCAPE_MIN_FRAMES;
    }

    private boolean isPreferredWindowAcquisitionSufficient(ToneFrequencyEstimate estimate) {
        return estimate.locked
                && Math.abs(estimate.frequencyHz - preferredToneFrequencyHz) <= currentUnlockedAcquisitionScanWindowHz()
                && (estimate.isolationRatio >= MIN_LOCK_ISOLATION_RATIO
                || estimate.localContrastRatio >= MIN_LOCK_LOCAL_CONTRAST_RATIO);
    }

    private ToneFrequencyEstimate chooseAcquisitionScanEstimate(
            ToneScanResult preferredWindowScan,
            ToneScanResult wideScan
    ) {
        // This is the main soft-prior arbitration point. The preferred-window winner
        // may start with a geographic advantage, but wide-scan evidence must be able
        // to overrule it when the off-preferred peak is materially stronger or cleaner.
        ToneFrequencyEstimate preferredWindowEstimate = preferredWindowScan.winner;
        ToneFrequencyEstimate wideEstimate = wideScan.winner;
        if (wideEstimate == null) {
            return rememberAcquisitionDecision(
                    preferredWindowEstimate,
                    preferredWindowEstimate == null
                            ? "no acquisition winner: neither preferred window nor wide scan produced a candidate"
                            : "preferred-window winner retained because no wide-scan candidate was available"
            );
        }
        if (isWideAcquisitionEdgeEstimate(wideEstimate) && (preferredWindowEstimate == null || !preferredWindowEstimate.locked)) {
            return rememberAcquisitionDecision(
                    preferredWindowEstimate,
                    preferredWindowEstimate == null
                            ? "wide-scan edge candidate was rejected and no preferred-window fallback was available"
                            : "preferred-window winner retained because the wide-scan candidate sat on the absolute edge"
            );
        }
        if (preferredWindowEstimate == null || !preferredWindowEstimate.locked) {
            if (isWideAcquisitionEdgeEstimate(wideEstimate)
                    && shouldRetainPlausiblePreferredWindowFallback(preferredWindowEstimate, wideEstimate, preferredWindowScan, wideScan)) {
                return rememberAcquisitionDecision(
                        preferredWindowEstimate,
                        "preferred-window fallback retained because the wide-scan edge candidate was not convincing enough"
                );
            }
            return rememberAcquisitionDecision(
                    wideEstimate,
                    preferredWindowEstimate == null
                            ? "wide-scan winner chosen because the preferred window produced no locked candidate"
                            : "wide-scan winner chosen because the preferred-window candidate was not locked"
            );
        }
        if (!wideEstimate.locked) {
            if (!isWithinPreferredWindow(wideEstimate.frequencyHz)
                    && shouldPreferWideUnlockedAcquisitionEstimate(preferredWindowScan, wideScan)) {
                return rememberAcquisitionDecision(
                        wideEstimate,
                        "wide-scan candidate overruled the preferred window even without a lock because its evidence was materially stronger"
                );
            }
            return rememberAcquisitionDecision(
                    preferredWindowEstimate,
                    "preferred-window winner retained because the wide-scan candidate was not locked"
            );
        }
        if (shouldPreferWideAcquisitionEstimate(preferredWindowScan, wideScan)) {
            return rememberAcquisitionDecision(
                    wideEstimate,
                    "wide-scan winner overruled the preferred window on stronger acquisition evidence"
            );
        }
        if (isWideAcquisitionEdgeEstimate(wideEstimate)
                && !isPreferredWindowEdgeEstimate(preferredWindowEstimate)) {
            double preferredEvidenceScore = acquisitionEvidenceScore(preferredWindowEstimate);
            double wideEvidenceScore = acquisitionEvidenceScore(wideEstimate);
            if (wideScan.winnerConfidence < (preferredWindowScan.winnerConfidence + 0.22d)
                    || wideEvidenceScore < preferredEvidenceScore * 1.20d) {
                return rememberAcquisitionDecision(
                        preferredWindowEstimate,
                        "preferred-window winner retained because the wide-scan edge candidate was not strong enough to justify leaving the interior"
                );
            }
        }
        int preferredDistanceHz = Math.abs(preferredWindowEstimate.frequencyHz - preferredToneFrequencyHz);
        int wideDistanceHz = Math.abs(wideEstimate.frequencyHz - preferredToneFrequencyHz);
        if (wideDistanceHz <= preferredDistanceHz) {
            return rememberAcquisitionDecision(
                    wideEstimate,
                    "wide-scan winner chosen because it stayed at least as close to the preferred region as the preferred-window winner"
            );
        }
        if (isPreferredWindowEdgeEstimate(preferredWindowEstimate)
                && wideEstimate.selectionScore > preferredWindowEstimate.selectionScore * 1.03d
                && wideEstimate.toneRmsAmplitude > preferredWindowEstimate.toneRmsAmplitude * 1.05d) {
            return rememberAcquisitionDecision(
                    wideEstimate,
                    "wide-scan winner replaced an edge-biased preferred-window winner on stronger score and tone RMS"
            );
        }
        if (isWithinPreferredWindow(wideEstimate.frequencyHz)
                && wideDistanceHz >= (PREFERRED_TONE_SCAN_WINDOW_HZ - 20)
                && wideEstimate.selectionScore >= preferredWindowEstimate.selectionScore
                && wideEstimate.toneRmsAmplitude > preferredWindowEstimate.toneRmsAmplitude * 1.20d) {
            return rememberAcquisitionDecision(
                    wideEstimate,
                    "wide-scan winner was still inside the preferred window and materially stronger than the preferred-window candidate"
            );
        }
        if (wideEstimate.selectionScore > preferredWindowEstimate.selectionScore * 1.22d
                && wideEstimate.toneRmsAmplitude > preferredWindowEstimate.toneRmsAmplitude * 1.35d) {
            return rememberAcquisitionDecision(
                    wideEstimate,
                    "wide-scan winner was materially stronger than the preferred-window candidate on both score and RMS"
            );
        }
        return rememberAcquisitionDecision(
                preferredWindowEstimate,
                "preferred-window winner retained after soft-prior arbitration"
        );
    }

    private ToneFrequencyEstimate rememberAcquisitionDecision(
            ToneFrequencyEstimate chosenEstimate,
            String detail
    ) {
        lastAcquisitionDecisionDetail = detail == null ? "NONE" : detail;
        return chosenEstimate;
    }

    private boolean isPreferredWindowEdgeEstimate(ToneFrequencyEstimate estimate) {
        if (estimate == null) {
            return false;
        }
        int edgeWindowHz = currentPreferredRegionWindowHz();
        int searchMin = Math.max(MIN_TRACKED_TONE_FREQUENCY_HZ, preferredToneFrequencyHz - edgeWindowHz);
        int searchMax = Math.min(MAX_TRACKED_TONE_FREQUENCY_HZ, preferredToneFrequencyHz + edgeWindowHz);
        return estimate.frequencyHz <= searchMin + TONE_SCAN_STEP_HZ
                || estimate.frequencyHz >= searchMax - TONE_SCAN_STEP_HZ;
    }

    private boolean isWideAcquisitionEdgeEstimate(ToneFrequencyEstimate estimate) {
        if (estimate == null) {
            return false;
        }
        return estimate.frequencyHz <= MIN_TRACKED_TONE_FREQUENCY_HZ + TONE_SCAN_STEP_HZ
                || estimate.frequencyHz >= MAX_TRACKED_TONE_FREQUENCY_HZ - TONE_SCAN_STEP_HZ;
    }

    private boolean isAbsoluteTrackingEdgeFrequency(int candidateFrequencyHz) {
        return candidateFrequencyHz <= MIN_TRACKED_TONE_FREQUENCY_HZ + ABSOLUTE_EDGE_PENALTY_WINDOW_HZ
                || candidateFrequencyHz >= MAX_TRACKED_TONE_FREQUENCY_HZ - ABSOLUTE_EDGE_PENALTY_WINDOW_HZ;
    }

    private ToneScanResult rebalanceWeakAbsoluteEdgeWinner(
            ToneFrequencyEstimate winner,
            ToneFrequencyEstimate runnerUp,
            int previousTargetToneFrequencyHz
    ) {
        if (winner == null || runnerUp == null) {
            return new ToneScanResult(winner, runnerUp, 0.0d);
        }
        if (!isAbsoluteTrackingEdgeFrequency(winner.frequencyHz)
                || isAbsoluteTrackingEdgeFrequency(runnerUp.frequencyHz)) {
            return new ToneScanResult(winner, runnerUp, 0.0d);
        }

        double winnerConfidence = scanWinnerConfidence(winner, runnerUp);
        if (winnerConfidence >= 0.52d) {
            return new ToneScanResult(winner, runnerUp, winnerConfidence);
        }

        double winnerEvidenceScore = acquisitionEvidenceScore(winner);
        double runnerUpEvidenceScore = acquisitionEvidenceScore(runnerUp);
        if (runnerUp.selectionScore < (winner.selectionScore * 0.84d)
                || runnerUp.toneRmsAmplitude < (winner.toneRmsAmplitude * 0.82d)
                || runnerUpEvidenceScore < (winnerEvidenceScore * 0.80d)) {
            return new ToneScanResult(winner, runnerUp, winnerConfidence);
        }

        if (isAbsoluteTrackingEdgeFrequency(previousTargetToneFrequencyHz)) {
            return new ToneScanResult(runnerUp, winner, scanWinnerConfidence(runnerUp, winner));
        }

        int winnerDistanceHz = Math.abs(winner.frequencyHz - previousTargetToneFrequencyHz);
        int runnerUpDistanceHz = Math.abs(runnerUp.frequencyHz - previousTargetToneFrequencyHz);
        if (runnerUpDistanceHz + 40 >= winnerDistanceHz) {
            return new ToneScanResult(winner, runnerUp, winnerConfidence);
        }

        return new ToneScanResult(runnerUp, winner, scanWinnerConfidence(runnerUp, winner));
    }

    private ToneScanResult rebalancePreferredWindowEdgeWinner(
            ToneFrequencyEstimate winner,
            ToneFrequencyEstimate runnerUp,
            List<ToneFrequencyEstimate> topCandidates
    ) {
        if (winner == null || runnerUp == null) {
            return new ToneScanResult(winner, runnerUp, scanWinnerConfidence(winner, runnerUp));
        }
        if (!isPreferredWindowEdgeEstimate(winner) || !isPreferredWindowEdgeEstimate(runnerUp)) {
            return new ToneScanResult(winner, runnerUp, scanWinnerConfidence(winner, runnerUp));
        }
        ToneFrequencyEstimate interiorCandidate = strongestPreferredWindowInteriorCandidate(topCandidates, winner, runnerUp);
        if (interiorCandidate == null) {
            return new ToneScanResult(winner, runnerUp, scanWinnerConfidence(winner, runnerUp));
        }
        int winnerDistanceHz = Math.abs(winner.frequencyHz - preferredToneFrequencyHz);
        int interiorDistanceHz = Math.abs(interiorCandidate.frequencyHz - preferredToneFrequencyHz);
        if (interiorDistanceHz + 60 >= winnerDistanceHz) {
            return new ToneScanResult(winner, runnerUp, scanWinnerConfidence(winner, runnerUp));
        }

        double winnerConfidence = scanWinnerConfidence(winner, runnerUp);
        double winnerEvidenceScore = acquisitionEvidenceScore(winner);
        double interiorEvidenceScore = acquisitionEvidenceScore(interiorCandidate);
        boolean interiorCandidateCloseEnough = interiorCandidate.selectionScore >= (winner.selectionScore * 0.72d)
                && interiorCandidate.toneRmsAmplitude >= (winner.toneRmsAmplitude * 0.78d)
                && interiorEvidenceScore >= (winnerEvidenceScore * 0.76d);
        if (!interiorCandidateCloseEnough) {
            return new ToneScanResult(winner, runnerUp, winnerConfidence);
        }
        boolean edgeClusterClearlyStrongerThanInterior = winnerConfidence >= 0.78d
                && winner.selectionScore > (interiorCandidate.selectionScore * 1.26d)
                && winner.toneRmsAmplitude > (interiorCandidate.toneRmsAmplitude * 1.22d)
                && runnerUp.selectionScore > (interiorCandidate.selectionScore * 1.14d)
                && runnerUp.toneRmsAmplitude > (interiorCandidate.toneRmsAmplitude * 1.08d);
        if (edgeClusterClearlyStrongerThanInterior) {
            return new ToneScanResult(winner, runnerUp, winnerConfidence);
        }
        return new ToneScanResult(interiorCandidate, winner, scanWinnerConfidence(interiorCandidate, winner));
    }

    private ToneFrequencyEstimate strongestPreferredWindowInteriorCandidate(
            List<ToneFrequencyEstimate> topCandidates,
            ToneFrequencyEstimate winner,
            ToneFrequencyEstimate runnerUp
    ) {
        if (topCandidates == null || topCandidates.isEmpty()) {
            return null;
        }
        for (ToneFrequencyEstimate candidate : topCandidates) {
            if (candidate == null) {
                continue;
            }
            if (winner != null && candidate.frequencyHz == winner.frequencyHz) {
                continue;
            }
            if (runnerUp != null && candidate.frequencyHz == runnerUp.frequencyHz) {
                continue;
            }
            if (!isPreferredWindowEdgeEstimate(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isWithinPreferredWindow(int candidateFrequencyHz) {
        int windowHz = currentPreferredRegionWindowHz();
        int searchMin = Math.max(MIN_TRACKED_TONE_FREQUENCY_HZ, preferredToneFrequencyHz - windowHz);
        int searchMax = Math.min(MAX_TRACKED_TONE_FREQUENCY_HZ, preferredToneFrequencyHz + windowHz);
        return candidateFrequencyHz >= searchMin && candidateFrequencyHz <= searchMax;
    }

    private int currentPreferredRegionWindowHz() {
        return trackingState == TrackingState.LOCKED
                ? currentPreferredToneScanWindowHz()
                : currentUnlockedAcquisitionScanWindowHz();
    }

    private int currentPreferredToneScanWindowHz() {
        return rxToneMode == RxToneMode.FIXED_TONE
                ? fixedToneLearningWindowHz
                : PREFERRED_TONE_SCAN_WINDOW_HZ;
    }

    private int currentUnlockedAcquisitionScanWindowHz() {
        return rxToneMode == RxToneMode.FIXED_TONE
                ? Math.min(
                MAX_FIXED_TONE_LEARNING_WINDOW_HZ + FIXED_TONE_UNLOCKED_ACQUISITION_WINDOW_EXTRA_HZ,
                fixedToneLearningWindowHz + FIXED_TONE_UNLOCKED_ACQUISITION_WINDOW_EXTRA_HZ
        )
                : UNLOCKED_ACQUISITION_SCAN_WINDOW_HZ;
    }

    private int currentLockRetuneWindowHz() {
        return rxToneMode == RxToneMode.FIXED_TONE
                ? Math.max(
                MIN_FIXED_TONE_LOCK_RETUNE_WINDOW_HZ,
                fixedToneLearningWindowHz - FIXED_TONE_LOCK_RETUNE_WINDOW_MARGIN_HZ
        )
                : LOCK_RETUNE_WINDOW_HZ;
    }

    private long currentToneOffHangMs() {
        return rxToneMode == RxToneMode.FIXED_TONE
                ? TONE_OFF_HANG_MS
                : AUTO_TRACK_TONE_OFF_HANG_MS;
    }

    private boolean shouldBridgeAutoTrackWeakValley(
            ToneFrequencyEstimate toneEstimate,
            int attackThreshold,
            long timestampMs
    ) {
        if (!isAutoTrackWeakValleyCandidate(toneEstimate, attackThreshold, timestampMs)) {
            autoTrackWeakValleyBridgeFramesRemaining = 0;
            autoTrackWeakValleyBridgeActive = false;
            return false;
        }
        if (!autoTrackWeakValleyBridgeActive) {
            autoTrackWeakValleyBridgeFramesRemaining = AUTO_TRACK_WEAK_VALLEY_BRIDGE_FRAMES;
            autoTrackWeakValleyBridgeActive = true;
        }
        if (autoTrackWeakValleyBridgeFramesRemaining <= 0) {
            return false;
        }
        autoTrackWeakValleyBridgeFramesRemaining -= 1;
        return true;
    }

    private boolean shouldHoldNearTargetReleaseTail(
            ToneFrequencyEstimate toneEstimate,
            double detectionLevel,
            int releaseThreshold,
            long timestampMs
    ) {
        if (!isNearTargetReleaseTailCandidate(
                toneEstimate,
                detectionLevel,
                releaseThreshold,
                timestampMs
        )) {
            clearAutoTrackReleaseTailHold();
            return false;
        }
        if (!autoTrackReleaseTailHoldActive) {
            autoTrackReleaseTailHoldActive = true;
            autoTrackReleaseTailHoldFramesRemaining = AUTO_TRACK_RELEASE_TAIL_HOLD_FRAMES;
        }
        if (autoTrackReleaseTailHoldFramesRemaining <= 0) {
            lastReleaseTailHoldDecision = "BLOCKED:HOLD_EXHAUSTED";
            return false;
        }
        autoTrackReleaseTailHoldFramesRemaining -= 1;
        if (!lastReleaseTailHoldSufficientRecentTrust
                && lastReleaseTailHoldCurrentRunWeakBootstrapEligible
                && !lastReleaseTailHoldCurrentRunStableBootstrapEligible) {
            currentToneRunWeakBootstrapReleaseTailHoldCount += 1;
        }
        lastReleaseTailHoldDecision = "HOLD:APPLIED";
        return true;
    }

    private int effectiveToneActiveReleaseThreshold(
            ToneFrequencyEstimate toneEstimate,
            int attackThreshold,
            int releaseThreshold,
            long timestampMs
    ) {
        int threshold = releaseThreshold;
        if (toneEstimate == null
                || toneEstimate.frequencyHz <= 0
                || lockedBranchReferenceToneRmsAmplitude <= 0.0d) {
            return threshold;
        }
        if (maxConsecutiveLockedFrames < LOCKED_BRANCH_REFERENCE_MIN_STREAK_FRAMES
                && consecutiveLockedFrames < LOCKED_BRANCH_REFERENCE_MIN_STREAK_FRAMES) {
            return threshold;
        }
        double referenceToneRms = lockedBranchReferenceToneRmsAmplitude;
        if (toneEstimate.toneRmsAmplitude < (referenceToneRms * ACTIVE_RELEASE_WEAK_REFERENCE_MAX_RATIO)
                && toneEstimate.toneRmsAmplitude < (attackThreshold * ACTIVE_RELEASE_WEAK_ATTACK_MULTIPLIER_MAX)
                && toneEstimate.toneRmsAmplitude < (Math.max(1.0d, signalFloorEstimate) * ACTIVE_RELEASE_WEAK_SIGNAL_MULTIPLIER_MAX)
                && referenceToneRms >= attackThreshold) {
            return Math.max(
                    threshold,
                    (int) Math.round(referenceToneRms * ACTIVE_RELEASE_WEAK_REFERENCE_FLOOR_RATIO)
            );
        }
        if (rxToneMode == RxToneMode.AUTO_TRACK
                && isCurrentToneRunStableForReleaseTailHoldBootstrap(toneEstimate, timestampMs)
                && toneEstimate.toneRmsAmplitude
                < (referenceToneRms * ACTIVE_RELEASE_STABLE_DECAY_MAX_REFERENCE_RATIO)) {
            return Math.max(
                    threshold,
                    (int) Math.round(referenceToneRms * ACTIVE_RELEASE_STABLE_DECAY_REFERENCE_FLOOR_RATIO)
            );
        }
        return threshold;
    }

    private long fixedToneFrameLocalReleaseTimestamp(
            AudioFrame frame,
            ToneFrequencyEstimate toneEstimate
    ) {
        if (rxToneMode != RxToneMode.FIXED_TONE
                || frame == null
                || toneEstimate == null
                || toneEstimate.frequencyHz <= 0
                || lockedBranchReferenceToneRmsAmplitude <= 0.0d) {
            return -1L;
        }
        if (toneEstimate.toneRmsAmplitude
                >= (lockedBranchReferenceToneRmsAmplitude * ACTIVE_RELEASE_WEAK_REFERENCE_MAX_RATIO)) {
            return -1L;
        }
        return estimateFixedToneLongSilenceTimestamp(frame);
    }

    private long estimateFixedToneLongSilenceTimestamp(AudioFrame frame) {
        short[] samples = frame == null ? null : frame.samples();
        int sampleRateHz = frame == null ? 0 : frame.sampleRateHz();
        if (samples == null || samples.length < (EDGE_WINDOW_SAMPLES * 3) || sampleRateHz <= 0) {
            return -1L;
        }
        double[] envelope = buildAbsoluteEnvelope(samples);
        double envelopeMax = 0.0d;
        for (double value : envelope) {
            envelopeMax = Math.max(envelopeMax, value);
        }
        if (envelopeMax < MIN_TRACKED_TONE_RMS) {
            return -1L;
        }
        double threshold = Math.max(MIN_TRACKED_TONE_RMS * 0.60d, envelopeMax * 0.22d);
        int lastAboveThresholdIndex = -1;
        for (int index = envelope.length - 1; index >= 0; index--) {
            if (envelope[index] >= threshold) {
                lastAboveThresholdIndex = index;
                break;
            }
        }
        if (lastAboveThresholdIndex < 0 || lastAboveThresholdIndex >= (envelope.length - 1)) {
            return -1L;
        }
        int silenceStartIndex = Math.min(envelope.length - 1, lastAboveThresholdIndex + 1);
        int remainingSilenceSamples = envelope.length - silenceStartIndex;
        if (remainingSilenceSamples < (EDGE_WINDOW_SAMPLES * 4)) {
            return -1L;
        }
        if (maxEnvelope(envelope, silenceStartIndex, envelope.length) >= threshold) {
            return -1L;
        }
        return sampleIndexToTimestamp(frame, silenceStartIndex);
    }

    private boolean isNearTargetReleaseTailCandidate(
            ToneFrequencyEstimate toneEstimate,
            double detectionLevel,
            int releaseThreshold,
            long timestampMs
    ) {
        boolean autoTrackMode = rxToneMode == RxToneMode.AUTO_TRACK;
        boolean fixedToneMode = rxToneMode == RxToneMode.FIXED_TONE;
        if ((!autoTrackMode && !fixedToneMode)
                || !toneActive
                || silenceStartedAtMs < 0L
                || toneEstimate == null
                || toneEstimate.frequencyHz <= 0
                || detectionLevel <= 0.0d) {
            lastReleaseTailHoldDecision = "BLOCKED:BASIC_PRECONDITION";
            return false;
        }
        int continuityAnchorFrequencyHz = continuityAnchorFrequencyHz();
        if (continuityAnchorFrequencyHz <= 0) {
            continuityAnchorFrequencyHz = targetToneFrequencyHz > 0
                    ? targetToneFrequencyHz
                    : representativeLockedToneFrequencyHz;
        }
        if (continuityAnchorFrequencyHz <= 0
                || Math.abs(toneEstimate.frequencyHz - continuityAnchorFrequencyHz)
                > AUTO_TRACK_RELEASE_TAIL_HOLD_MAX_ANCHOR_DRIFT_HZ) {
            lastReleaseTailHoldDecision = "BLOCKED:FAR_FROM_ANCHOR";
            return false;
        }
        ReleaseTailHoldEligibilityContext releaseTailContext = buildReleaseTailHoldEligibilityContext(
                toneEstimate,
                fixedToneMode,
                timestampMs
        );
        rememberReleaseTailHoldEligibilityContext(releaseTailContext);
        if (!isTrackedToneMemoryActive(timestampMs)
                || (!releaseTailContext.sufficientRecentTrust
                && !releaseTailContext.currentRunStableBootstrapEligible
                && !releaseTailContext.currentRunWeakBootstrapEligible)) {
            lastReleaseTailHoldDecision = releaseTailContext.rescuedToneNeedsStrongerTailEvidence
                    ? "BLOCKED:RESCUED_TONE_NEEDS_STRONGER_TAIL"
                    : "BLOCKED:LOW_RECENT_TRUST";
            return false;
        }
        if (!releaseTailContext.sufficientRecentTrust
                && !releaseTailContext.currentRunStableBootstrapEligible
                && releaseTailContext.currentRunWeakBootstrapEligible
                && currentToneRunWeakBootstrapReleaseTailHoldCount
                >= AUTO_TRACK_RELEASE_TAIL_HOLD_WEAK_BOOTSTRAP_MAX_APPLICATIONS_PER_RUN) {
            lastReleaseTailHoldDecision = "BLOCKED:WEAK_BOOTSTRAP_HOLD_SPENT";
            return false;
        }
        if (!releaseTailContext.sufficientRecentTrust
                && !releaseTailContext.currentRunStableBootstrapEligible
                && releaseTailContext.currentRunWeakBootstrapEligible
                && releaseTailContext.recentLockedFrameRatio
                < AUTO_TRACK_RELEASE_TAIL_HOLD_OPENING_WEAK_BOOTSTRAP_MIN_LOCKED_RATIO) {
            lastReleaseTailHoldDecision = "BLOCKED:LOW_WEAK_BOOTSTRAP_LOCK";
            return false;
        }
        if (lockedBranchReferenceToneRmsAmplitude
                < (currentThreshold() * AUTO_TRACK_RELEASE_TAIL_HOLD_REFERENCE_MIN_THRESHOLD_MULTIPLIER)) {
            lastReleaseTailHoldDecision = "BLOCKED:LOW_REFERENCE";
            return false;
        }
        double requiredDetectionThreshold = requiredDetectionThresholdForReleaseTailHold(
                releaseTailContext,
                releaseThreshold
        );
        lastReleaseTailHoldRequiredDetectionThreshold = requiredDetectionThreshold;
        if (detectionLevel < requiredDetectionThreshold) {
            lastReleaseTailHoldDecision = "BLOCKED:LOW_DETECTION_RATIO";
            return false;
        }
        boolean eligible = toneEstimate.dominanceRatio >= AUTO_TRACK_RELEASE_TAIL_HOLD_DOMINANCE_MIN
                && (toneEstimate.isolationRatio >= AUTO_TRACK_RELEASE_TAIL_HOLD_ISOLATION_MIN
                || toneEstimate.localContrastRatio >= AUTO_TRACK_RELEASE_TAIL_HOLD_LOCAL_CONTRAST_MIN);
        if (!eligible
                && !releaseTailContext.sufficientRecentTrust
                && releaseTailContext.currentRunWeakBootstrapEligible) {
            eligible = toneEstimate.dominanceRatio >= AUTO_TRACK_RELEASE_TAIL_HOLD_WEAK_BOOTSTRAP_DOMINANCE_MIN
                    && (toneEstimate.isolationRatio >= AUTO_TRACK_RELEASE_TAIL_HOLD_WEAK_BOOTSTRAP_ISOLATION_MIN
                    || toneEstimate.localContrastRatio
                    >= AUTO_TRACK_RELEASE_TAIL_HOLD_WEAK_BOOTSTRAP_LOCAL_CONTRAST_MIN);
        }
        if (!eligible) {
            lastReleaseTailHoldDecision = "BLOCKED:LOW_QUALITY";
        } else if (releaseTailContext.rescueBootstrapWindowActive) {
            if (releaseTailContext.currentRunStableBootstrapEligible
                    && !releaseTailContext.sufficientRecentTrust) {
                lastReleaseTailHoldDecision = "ELIGIBLE:CURRENT_RUN_BOOTSTRAP+RESCUE";
            } else if (!releaseTailContext.sufficientRecentTrust
                    && releaseTailContext.currentRunWeakBootstrapEligible) {
                lastReleaseTailHoldDecision = "ELIGIBLE:WEAK_BOOTSTRAP+RESCUE";
            } else {
                lastReleaseTailHoldDecision = "ELIGIBLE:RESCUE_BOOTSTRAP";
            }
        } else if (releaseTailContext.currentRunStableBootstrapEligible
                && !releaseTailContext.sufficientRecentTrust) {
            lastReleaseTailHoldDecision = "ELIGIBLE:CURRENT_RUN_BOOTSTRAP";
        } else if (!releaseTailContext.sufficientRecentTrust
                && releaseTailContext.currentRunWeakBootstrapEligible) {
            lastReleaseTailHoldDecision = "ELIGIBLE:WEAK_BOOTSTRAP";
        } else {
            lastReleaseTailHoldDecision = "ELIGIBLE";
        }
        return eligible;
    }

    private boolean isCurrentToneRunStableForReleaseTailHoldBootstrap(
            ToneFrequencyEstimate toneEstimate,
            long timestampMs
    ) {
        return buildCurrentToneRunBootstrapContext(toneEstimate, timestampMs).stableBootstrapEligible;
    }

    private boolean isCurrentToneRunWeakBootstrapForReleaseTailHold(
            ToneFrequencyEstimate toneEstimate,
            long timestampMs
    ) {
        return buildCurrentToneRunBootstrapContext(toneEstimate, timestampMs).weakBootstrapEligible;
    }

    private void clearAutoTrackReleaseTailHold() {
        autoTrackReleaseTailHoldActive = false;
        autoTrackReleaseTailHoldFramesRemaining = 0;
        autoTrackReleaseTailHoldExtendedUntilMs = -1L;
    }

    private boolean isAutoTrackWeakValleyCandidate(
            ToneFrequencyEstimate toneEstimate,
            int attackThreshold,
            long timestampMs
    ) {
        if (rxToneMode != RxToneMode.AUTO_TRACK || !toneActive) {
            return false;
        }
        if (toneEstimate == null || toneEstimate.frequencyHz <= 0) {
            return false;
        }
        if (targetToneLocked) {
            if (Math.abs(toneEstimate.frequencyHz - targetToneFrequencyHz) > TONE_SCAN_STEP_HZ) {
                return false;
            }
            return shouldSuppressWeakLockedBranchTone(toneEstimate, attackThreshold);
        }
        return shouldSuppressNearTargetWeakValleyWithoutHardLock(
                toneEstimate,
                attackThreshold,
                timestampMs
        );
    }

    private boolean shouldSuppressNearTargetWeakValleyWithoutHardLock(
            ToneFrequencyEstimate toneEstimate,
            int currentThreshold,
            long timestampMs
    ) {
        if (toneEstimate == null
                || toneEstimate.frequencyHz <= 0
                || !isTrackedToneMemoryActive(timestampMs)
                || !hasStableRepresentativeLockContext()) {
            return false;
        }
        CurrentToneRunBootstrapContext currentRunBootstrapContext =
                buildCurrentToneRunBootstrapContext(toneEstimate, timestampMs);
        PostReleaseRescuedToneProgressContext rescuedToneProgressContext =
                buildPostReleaseRescuedToneProgressContext(
                        toneEstimate,
                        currentRunBootstrapContext,
                        timestampMs
                );
        CurrentToneRunContinuityGuardContext continuityGuardContext =
                buildCurrentToneRunContinuityGuardContext(
                        currentRunBootstrapContext,
                        rescuedToneProgressContext
                );
        // A fresh opening run that started without prior tracked-memory continuity
        // should not use the unlocked weak-valley rescue to glue together its first
        // short gaps. Let the stream prove a local cadence first.
        if (continuityGuardContext.openingRunNeedsLocalCadenceProof) {
            return false;
        }
        // A run that itself was reopened by post-release rescue is still a fragile
        // continuity candidate. Do not let the unlocked weak-valley path immediately
        // start gluing its short gaps back together.
        if (continuityGuardContext.rescuedToneStillFragileForUnlockedWeakValley) {
            return false;
        }
        // A short fresh tone is exactly where a real DIT-to-gap or DAH-to-gap
        // boundary is most likely to appear. Do not let the unlocked weak-valley
        // rescue glue that early release into the following symbol.
        if (!continuityGuardContext.currentRunMatureForUnlockedWeakValley) {
            return false;
        }
        if (representativeLockedToneFrequencyHz <= 0
                || Math.abs(toneEstimate.frequencyHz - representativeLockedToneFrequencyHz)
                > AUTO_TRACK_WEAK_VALLEY_RESCUE_ANCHOR_DRIFT_HZ) {
            return false;
        }
        if (recentLockedFrameRatioFromHistory() < AUTO_TRACK_WEAK_VALLEY_RESCUE_LOCKED_RATIO_MIN
                || recentNearTargetLockedFrameRatioFromHistory()
                < AUTO_TRACK_WEAK_VALLEY_RESCUE_NEAR_TARGET_RATIO_MIN) {
            return false;
        }
        // When the near-target tone estimate has already collapsed to only a tiny
        // fraction of the current threshold, treat it as a real release/gap rather
        // than gluing through it with unlocked weak-valley rescue.
        if (toneEstimate.toneRmsAmplitude
                < Math.max(
                MIN_TRACKED_TONE_RMS,
                currentThreshold * AUTO_TRACK_WEAK_VALLEY_RESCUE_MIN_THRESHOLD_RATIO
        )) {
            return false;
        }
        if (lockedBranchReferenceToneRmsAmplitude
                < (currentThreshold * AUTO_TRACK_WEAK_VALLEY_RESCUE_REFERENCE_MIN_THRESHOLD_MULTIPLIER)) {
            return false;
        }
        if (toneEstimate.toneRmsAmplitude
                >= (lockedBranchReferenceToneRmsAmplitude * AUTO_TRACK_WEAK_VALLEY_RESCUE_REFERENCE_RATIO_MAX)) {
            return false;
        }
        return toneEstimate.dominanceRatio >= AUTO_TRACK_WEAK_VALLEY_RESCUE_DOMINANCE_MIN
                && (toneEstimate.isolationRatio >= AUTO_TRACK_WEAK_VALLEY_RESCUE_ISOLATION_MIN
                || toneEstimate.localContrastRatio >= AUTO_TRACK_WEAK_VALLEY_RESCUE_LOCAL_CONTRAST_MIN);
    }

    private boolean hasStableRepresentativeLockContext() {
        if (representativeLockedToneFrameCount < AUTO_TRACK_WEAK_VALLEY_RESCUE_MIN_REPRESENTATIVE_FRAMES) {
            return false;
        }
        return maxConsecutiveLockedFrames >= AUTO_TRACK_WEAK_VALLEY_RESCUE_MIN_STABLE_LOCK_FRAMES
                || consecutiveLockedFrames >= AUTO_TRACK_WEAK_VALLEY_RESCUE_MIN_STABLE_LOCK_FRAMES;
    }

    private boolean hasTrustedContinuityAnchorContext() {
        if (continuityAnchorFrequencyHz() <= 0) {
            return false;
        }
        if (maxConsecutiveLockedFrames < CONTINUITY_ANCHOR_TRUST_MIN_STABLE_LOCK_FRAMES
                && consecutiveLockedFrames < CONTINUITY_ANCHOR_TRUST_MIN_STABLE_LOCK_FRAMES) {
            return false;
        }
        if (recentLockedFrameRatioFromHistory() < CONTINUITY_ANCHOR_TRUST_MIN_LOCKED_RATIO
                || recentLockedFrameRatioNearFrequencyFromHistory(
                continuityAnchorFrequencyHz(),
                AUTO_TRACK_WEAK_VALLEY_RESCUE_NEAR_TARGET_OFFSET_HZ
        )
                < CONTINUITY_ANCHOR_TRUST_MIN_NEAR_TARGET_RATIO) {
            return false;
        }
        if (representativeLockedToneFrameCount >= CONTINUITY_ANCHOR_TRUST_MIN_REPRESENTATIVE_FRAMES
                || currentRepresentativeLockedToneFrameCount
                >= CONTINUITY_ANCHOR_TRUST_MIN_REPRESENTATIVE_FRAMES) {
            return true;
        }
        return hasActiveToneHypothesis()
                && toneHypothesisSupportFrames >= CONTINUITY_ANCHOR_TRUST_MIN_HYPOTHESIS_SUPPORT_FRAMES
                && toneHypothesisConfidence >= CONTINUITY_ANCHOR_TRUST_MIN_HYPOTHESIS_CONFIDENCE;
    }

    private double recentLockedFrameRatioFromHistory() {
        if (recentHistoryFrameCount <= 0) {
            return 0.0d;
        }
        return countRecentStateCodes('L', 'l') / (double) recentHistoryFrameCount;
    }

    private double recentNearTargetLockedFrameRatioFromHistory() {
        int recentLockedFrameCount = countRecentStateCodes('L', 'l');
        if (recentLockedFrameCount <= 0) {
            return 0.0d;
        }
        return countRecentLockedFramesWithinOffset(AUTO_TRACK_WEAK_VALLEY_RESCUE_NEAR_TARGET_OFFSET_HZ)
                / (double) recentLockedFrameCount;
    }

    private double recentLockedFrameRatioNearFrequencyFromHistory(int anchorFrequencyHz, int thresholdHz) {
        if (anchorFrequencyHz <= 0 || thresholdHz < 0) {
            return 0.0d;
        }
        int recentLockedFrameCount = countRecentStateCodes('L', 'l');
        if (recentLockedFrameCount <= 0) {
            return 0.0d;
        }
        return countRecentLockedFramesNearFrequency(anchorFrequencyHz, thresholdHz)
                / (double) recentLockedFrameCount;
    }

    private int countRecentStateCodes(char... acceptedCodes) {
        if (acceptedCodes == null || acceptedCodes.length == 0 || recentHistoryFrameCount <= 0) {
            return 0;
        }
        int count = 0;
        int limit = Math.min(recentHistoryFrameCount, RECENT_HISTORY_WINDOW_FRAMES);
        for (int index = 0; index < limit; index++) {
            char stateCode = recentFrontEndStateHistory[index];
            for (char acceptedCode : acceptedCodes) {
                if (stateCode == acceptedCode) {
                    count += 1;
                    break;
                }
            }
        }
        return count;
    }

    private int countRecentLockedFramesWithinOffset(int thresholdHz) {
        if (recentHistoryFrameCount <= 0 || thresholdHz < 0) {
            return 0;
        }
        int count = 0;
        int limit = Math.min(recentHistoryFrameCount, RECENT_HISTORY_WINDOW_FRAMES);
        for (int index = 0; index < limit; index++) {
            char stateCode = recentFrontEndStateHistory[index];
            if (stateCode != 'L' && stateCode != 'l') {
                continue;
            }
            if (Math.abs(recentTrackingOffsetHistoryHz[index]) <= thresholdHz) {
                count += 1;
            }
        }
        return count;
    }

    private int countRecentLockedFramesNearFrequency(int anchorFrequencyHz, int thresholdHz) {
        if (recentHistoryFrameCount <= 0 || anchorFrequencyHz <= 0 || thresholdHz < 0) {
            return 0;
        }
        int count = 0;
        int anchorOffsetHz = anchorFrequencyHz - preferredToneFrequencyHz;
        int limit = Math.min(recentHistoryFrameCount, RECENT_HISTORY_WINDOW_FRAMES);
        for (int index = 0; index < limit; index++) {
            char stateCode = recentFrontEndStateHistory[index];
            if (stateCode != 'L' && stateCode != 'l') {
                continue;
            }
            if (Math.abs(recentTrackingOffsetHistoryHz[index] - anchorOffsetHz) <= thresholdHz) {
                count += 1;
            }
        }
        return count;
    }

    private double preferredFrequencyWeight(int candidateFrequencyHz, boolean acquisitionMode) {
        // This is intentionally a soft bias, not a hard clamp. Keep the floor above zero
        // so off-preferred candidates remain selectable, especially in acquisition mode.
        int distanceHz = Math.abs(candidateFrequencyHz - preferredToneFrequencyHz);
        if (distanceHz <= PREFERRED_WEIGHT_FULL_BIAS_WINDOW_HZ) {
            return 1.0d;
        }
        if (acquisitionMode) {
            if (distanceHz <= PREFERRED_ACQUISITION_SOFT_BIAS_WINDOW_HZ) {
                return 0.98d;
            }
            double normalized = (distanceHz - PREFERRED_ACQUISITION_SOFT_BIAS_WINDOW_HZ) / 220.0d;
            return Math.max(0.84d, 0.98d * Math.exp(-normalized));
        }
        double normalized = (distanceHz - PREFERRED_WEIGHT_FULL_BIAS_WINDOW_HZ) / 55.0d;
        return Math.max(0.18d, Math.exp(-normalized));
    }

    private boolean shouldPreferWideAcquisitionEstimate(
            ToneScanResult preferredWindowScan,
            ToneScanResult wideScan
    ) {
        ToneFrequencyEstimate preferredWindowEstimate = preferredWindowScan.winner;
        ToneFrequencyEstimate wideEstimate = wideScan.winner;
        if (preferredWindowEstimate == null || wideEstimate == null) {
            return wideEstimate != null;
        }
        if (!wideEstimate.locked) {
            return false;
        }
        if (!preferredWindowEstimate.locked) {
            return true;
        }
        if (isWithinPreferredWindow(wideEstimate.frequencyHz) && !isPreferredWindowEdgeEstimate(preferredWindowEstimate)) {
            return false;
        }
        double preferredEvidenceScore = acquisitionEvidenceScore(preferredWindowEstimate);
        double wideEvidenceScore = acquisitionEvidenceScore(wideEstimate);
        if (wideScan.winnerConfidence >= (preferredWindowScan.winnerConfidence + 0.18d)
                && wideEvidenceScore >= preferredEvidenceScore * 1.05d) {
            return true;
        }
        if (wideEvidenceScore <= preferredEvidenceScore) {
            return false;
        }
        if (wideEvidenceScore >= preferredEvidenceScore * 1.25d) {
            return true;
        }
        if (wideEstimate.toneRmsAmplitude >= preferredWindowEstimate.toneRmsAmplitude * 1.18d
                && wideEstimate.dominanceRatio >= preferredWindowEstimate.dominanceRatio * 0.94d
                && wideEstimate.localContrastRatio >= preferredWindowEstimate.localContrastRatio * 0.92d) {
            if (isWideAcquisitionEdgeEstimate(wideEstimate)
                    && !isPreferredWindowEdgeEstimate(preferredWindowEstimate)
                    && wideScan.winnerConfidence < (preferredWindowScan.winnerConfidence + 0.25d)) {
                return false;
            }
            return true;
        }
        return isPreferredWindowEdgeEstimate(preferredWindowEstimate)
                && wideEvidenceScore >= preferredEvidenceScore * 1.08d;
    }

    private boolean shouldPreferWideUnlockedAcquisitionEstimate(
            ToneScanResult preferredWindowScan,
            ToneScanResult wideScan
    ) {
        ToneFrequencyEstimate preferredWindowEstimate = preferredWindowScan.winner;
        ToneFrequencyEstimate wideEstimate = wideScan.winner;
        if (preferredWindowEstimate == null || wideEstimate == null) {
            return false;
        }
        double preferredEvidenceScore = acquisitionEvidenceScore(preferredWindowEstimate);
        double wideEvidenceScore = acquisitionEvidenceScore(wideEstimate);
        return wideEstimate.toneRmsAmplitude >= (MIN_TRACKED_TONE_RMS * 1.35d)
                && wideEstimate.dominanceRatio >= (MIN_NARROWBAND_DOMINANCE_RATIO * 1.10d)
                && wideEstimate.localContrastRatio >= (MIN_NARROWBAND_LOCAL_CONTRAST_RATIO * 0.92d)
                && wideScan.winnerConfidence >= (preferredWindowScan.winnerConfidence + 0.10d)
                && wideEvidenceScore >= preferredEvidenceScore * 1.45d
                && wideEstimate.toneRmsAmplitude >= preferredWindowEstimate.toneRmsAmplitude * 1.30d;
    }

    private boolean shouldRetainPlausiblePreferredWindowFallback(
            ToneFrequencyEstimate preferredWindowEstimate,
            ToneFrequencyEstimate wideEstimate,
            ToneScanResult preferredWindowScan,
            ToneScanResult wideScan
    ) {
        if (preferredWindowEstimate == null || wideEstimate == null) {
            return false;
        }
        if (!isWideAcquisitionEdgeEstimate(wideEstimate) || !isPlausiblePreferredWindowFallback(preferredWindowEstimate)) {
            return false;
        }
        int preferredDistanceHz = Math.abs(preferredWindowEstimate.frequencyHz - preferredToneFrequencyHz);
        int wideDistanceHz = Math.abs(wideEstimate.frequencyHz - preferredToneFrequencyHz);
        if (preferredDistanceHz >= wideDistanceHz) {
            return false;
        }
        double preferredEvidenceScore = acquisitionEvidenceScore(preferredWindowEstimate);
        double wideEvidenceScore = acquisitionEvidenceScore(wideEstimate);
        return wideScan.winnerConfidence < 0.82d
                && wideScan.winnerConfidence < (preferredWindowScan.winnerConfidence + 0.22d)
                && wideEvidenceScore < preferredEvidenceScore * 1.35d
                && wideEstimate.toneRmsAmplitude < preferredWindowEstimate.toneRmsAmplitude * 1.28d;
    }

    private boolean isPlausiblePreferredWindowFallback(ToneFrequencyEstimate estimate) {
        if (estimate == null) {
            return false;
        }
        return estimate.toneRmsAmplitude >= (MIN_TRACKED_TONE_RMS * 0.90d)
                && estimate.dominanceRatio >= (MIN_NARROWBAND_DOMINANCE_RATIO * 0.92d)
                && (estimate.isolationRatio >= (MIN_NARROWBAND_ISOLATION_RATIO * 0.90d)
                || estimate.localContrastRatio >= (MIN_NARROWBAND_LOCAL_CONTRAST_RATIO * 0.88d));
    }

    private double acquisitionEvidenceScore(ToneFrequencyEstimate estimate) {
        if (estimate == null) {
            return 0.0d;
        }
        double dominanceWeight = 0.65d + (0.35d * estimate.dominanceRatio);
        double separationWeight = 0.65d + (0.35d * Math.max(estimate.isolationRatio, estimate.localContrastRatio));
        return estimate.toneRmsAmplitude * dominanceWeight * separationWeight;
    }

    private double continuityFrequencyWeight(int candidateFrequencyHz, int previousTargetToneFrequencyHz, boolean wasLocked) {
        if (!wasLocked) {
            return 1.0d;
        }
        int distanceHz = Math.abs(candidateFrequencyHz - previousTargetToneFrequencyHz);
        if (distanceHz <= CONTINUITY_WEIGHT_FULL_BIAS_WINDOW_HZ) {
            return 1.12d;
        }
        if (distanceHz >= CONTINUITY_WEIGHT_SOFT_BIAS_WINDOW_HZ) {
            return 0.74d;
        }
        double normalized = (distanceHz - CONTINUITY_WEIGHT_FULL_BIAS_WINDOW_HZ)
                / (double) (CONTINUITY_WEIGHT_SOFT_BIAS_WINDOW_HZ - CONTINUITY_WEIGHT_FULL_BIAS_WINDOW_HZ);
        return 1.12d - (normalized * 0.38d);
    }

    private double scoreToneCandidate(
            int candidateFrequencyHz,
            int previousTargetToneFrequencyHz,
            boolean wasLocked,
            ToneFrequencyEstimate estimate
    ) {
        return scoreToneCandidate(candidateFrequencyHz, previousTargetToneFrequencyHz, wasLocked, estimate, false);
    }

    private double scoreToneCandidate(
            int candidateFrequencyHz,
            int previousTargetToneFrequencyHz,
            boolean wasLocked,
            ToneFrequencyEstimate estimate,
            boolean acquisitionMode
    ) {
        double qualityWeight = 1.0d;
        if (estimate != null) {
            double isolationWeight = 0.60d + (0.40d * estimate.isolationRatio);
            double localContrastWeight = 0.55d + (0.45d * estimate.localContrastRatio);
            double dominanceWeight = 0.68d + (0.32d * estimate.dominanceRatio);
            if (acquisitionMode) {
                double acquisitionIsolationWeight = 0.35d + (0.65d * estimate.isolationRatio);
                double acquisitionContrastWeight = 0.30d + (0.70d * estimate.localContrastRatio);
                qualityWeight = acquisitionIsolationWeight
                        * acquisitionIsolationWeight
                        * acquisitionContrastWeight
                        * acquisitionContrastWeight
                        * dominanceWeight;
            } else {
                qualityWeight = isolationWeight * localContrastWeight;
            }
        }
        double preferredWeight = preferredFrequencyWeight(candidateFrequencyHz, acquisitionMode);
        if (acquisitionMode
                && estimate != null
                && estimate.locked
                && !isWithinPreferredWindow(candidateFrequencyHz)) {
            preferredWeight = Math.max(preferredWeight, 0.96d);
        }
        if (acquisitionMode && isAbsoluteTrackingEdgeFrequency(candidateFrequencyHz)) {
            preferredWeight *= 0.84d;
        }
        return preferredWeight
                * continuityFrequencyWeight(candidateFrequencyHz, previousTargetToneFrequencyHz, wasLocked)
                * qualityWeight;
    }

    private ToneFrequencyEstimate evaluateToneEstimate(
            short[] samples,
            int sampleRateHz,
            double frameRms,
            int frequencyHz,
            double selectionScore
    ) {
        double toneRms = estimateToneRms(samples, sampleRateHz, frequencyHz);
        double dominanceRatio = toneRms <= 0.0d || frameRms <= 0.0d
                ? 0.0d
                : Math.min(1.0d, toneRms / frameRms);
        double adjacentToneRms = estimateAdjacentToneRms(samples, sampleRateHz, frequencyHz);
        double widebandResidualRms = estimateWidebandResidualRms(frameRms, toneRms);
        double isolationRatio = computeNarrowbandIsolationRatio(toneRms, widebandResidualRms);
        double localContrastRatio = computeLocalContrastRatio(toneRms, adjacentToneRms);
        boolean locked = toneRms >= MIN_TRACKED_TONE_RMS
                && dominanceRatio >= MIN_LOCK_DOMINANCE_RATIO
                && (isolationRatio >= MIN_LOCK_ISOLATION_RATIO
                || localContrastRatio >= MIN_LOCK_LOCAL_CONTRAST_RATIO);
        return new ToneFrequencyEstimate(
                frequencyHz,
                toneRms,
                widebandResidualRms,
                dominanceRatio,
                isolationRatio,
                localContrastRatio,
                locked,
                selectionScore
        );
    }

    private double effectiveDetectionLevel(double frameRms, ToneFrequencyEstimate toneEstimate) {
        if (toneEstimate == null) {
            return frameRms;
        }
        double narrowbandConfidence = narrowbandConfidence(toneEstimate);
        if (toneEstimate.locked) {
            return Math.max(
                    toneEstimate.toneRmsAmplitude,
                    frameRms * Math.max(
                            UNLOCKED_SIGNAL_BLEND_FLOOR,
                            1.0d - ((1.0d - narrowbandConfidence) * LOCKED_SIGNAL_BLEND)
                    )
            );
        }
        if (isNarrowbandQualified(toneEstimate)) {
            return Math.max(
                    toneEstimate.toneRmsAmplitude * UNLOCKED_TONE_GAIN,
                    frameRms * Math.max(UNLOCKED_SIGNAL_BLEND_FLOOR, narrowbandConfidence)
            );
        }
        return toneEstimate.toneRmsAmplitude;
    }

    private double backgroundObservationLevel(double frameRms, ToneFrequencyEstimate toneEstimate) {
        if (toneEstimate == null) {
            return Math.max(0.0d, frameRms);
        }
        if (toneEstimate.widebandResidualRmsAmplitude > 0.0d) {
            return toneEstimate.widebandResidualRmsAmplitude;
        }
        return Math.max(0.0d, frameRms);
    }

    private boolean isNarrowbandQualified(ToneFrequencyEstimate toneEstimate) {
        return toneEstimate != null
                && toneEstimate.toneRmsAmplitude >= effectiveMinTrackedToneRmsForQualification()
                && toneEstimate.dominanceRatio >= effectiveMinNarrowbandDominanceRatio()
                && (toneEstimate.isolationRatio >= effectiveMinNarrowbandIsolationRatio()
                || toneEstimate.localContrastRatio >= effectiveMinNarrowbandLocalContrastRatio());
    }

    private double narrowbandConfidence(ToneFrequencyEstimate toneEstimate) {
        if (toneEstimate == null) {
            return 0.0d;
        }
        double effectiveMinDominanceRatio = effectiveMinNarrowbandDominanceRatio();
        double effectiveMinIsolationRatio = effectiveMinNarrowbandIsolationRatio();
        double effectiveMinLocalContrastRatio = effectiveMinNarrowbandLocalContrastRatio();
        double dominanceConfidence = normalizeBetween(
                toneEstimate.dominanceRatio,
                effectiveMinDominanceRatio,
                MIN_LOCK_DOMINANCE_RATIO
        );
        double isolationConfidence = normalizeBetween(
                toneEstimate.isolationRatio,
                effectiveMinIsolationRatio,
                MIN_LOCK_ISOLATION_RATIO
        );
        double localContrastConfidence = normalizeBetween(
                toneEstimate.localContrastRatio,
                effectiveMinLocalContrastRatio,
                MIN_LOCK_LOCAL_CONTRAST_RATIO
        );
        return Math.max(
                UNLOCKED_SIGNAL_BLEND_FLOOR,
                Math.min(1.0d, (dominanceConfidence * 0.46d)
                        + (isolationConfidence * 0.24d)
                        + (localContrastConfidence * 0.30d))
        );
    }

    private double scanWinnerConfidence(ToneFrequencyEstimate winner, ToneFrequencyEstimate runnerUp) {
        if (winner == null) {
            return 0.0d;
        }
        double qualityConfidence = narrowbandConfidence(winner);
        double evidenceConfidence = normalizeBetween(
                acquisitionEvidenceScore(winner),
                MIN_TRACKED_TONE_RMS * 0.90d,
                MIN_TRACKED_TONE_RMS * 3.20d
        );
        double separationConfidence;
        if (runnerUp == null || runnerUp.selectionScore <= 0.0d) {
            separationConfidence = 1.0d;
        } else {
            double scoreRatio = winner.selectionScore / Math.max(1.0d, runnerUp.selectionScore);
            separationConfidence = normalizeBetween(scoreRatio, 1.02d, 1.35d);
        }
        return clamp01((qualityConfidence * 0.55d) + (separationConfidence * 0.30d) + (evidenceConfidence * 0.15d));
    }

    private double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private boolean shouldSuppressWeakLockedBranchTone(
            ToneFrequencyEstimate toneEstimate,
            int currentThreshold
    ) {
        if (toneEstimate == null || lockedBranchReferenceToneRmsAmplitude <= 0.0d) {
            return false;
        }
        if (maxConsecutiveLockedFrames < LOCKED_BRANCH_REFERENCE_MIN_STREAK_FRAMES
                && consecutiveLockedFrames < LOCKED_BRANCH_REFERENCE_MIN_STREAK_FRAMES) {
            return false;
        }
        if (lockedBranchReferenceToneRmsAmplitude
                < (currentThreshold * LOCKED_BRANCH_REFERENCE_MIN_THRESHOLD_MULTIPLIER)) {
            return false;
        }
        boolean sufficientRecentTrust = recentLockedFrameRatioFromHistory()
                >= AUTO_TRACK_RELEASE_TAIL_HOLD_MIN_LOCKED_RATIO
                && recentNearTargetLockedFrameRatioFromHistory()
                >= AUTO_TRACK_RELEASE_TAIL_HOLD_MIN_NEAR_TARGET_RATIO;
        boolean currentRunBootstrapEligible = consecutiveLockedFrames >= LOCKED_BRANCH_REFERENCE_MIN_STREAK_FRAMES
                || maxConsecutiveLockedFrames >= LOCKED_BRANCH_REFERENCE_MIN_STREAK_FRAMES;
        if (!sufficientRecentTrust && !currentRunBootstrapEligible) {
            return false;
        }
        if (toneEstimate.toneRmsAmplitude
                < Math.max(
                MIN_TRACKED_TONE_RMS,
                currentThreshold * AUTO_TRACK_WEAK_VALLEY_RESCUE_MIN_THRESHOLD_RATIO
        )) {
            return false;
        }
        return toneEstimate.toneRmsAmplitude
                < (lockedBranchReferenceToneRmsAmplitude * LOCKED_BRANCH_WEAK_RATIO);
    }

    private void rememberSignalQuality(
            double toneRmsAmplitude,
            double isolationRatio,
            boolean locked,
            boolean narrowbandQualified
    ) {
        if (locked) {
            lockedFrameCount += 1;
            consecutiveLockedFrames += 1;
            maxConsecutiveLockedFrames = Math.max(maxConsecutiveLockedFrames, consecutiveLockedFrames);
        } else {
            consecutiveLockedFrames = 0;
            currentRepresentativeLockedToneFrameCount = 0;
            currentRepresentativeLockedToneScore = 0.0d;
        }
        if (narrowbandQualified) {
            peakToneRmsAmplitude = Math.max(peakToneRmsAmplitude, toneRmsAmplitude);
            peakNarrowbandIsolationRatio = Math.max(peakNarrowbandIsolationRatio, isolationRatio);
        }
        if (locked && toneRmsAmplitude > 0.0d) {
            if (lockedBranchReferenceToneRmsAmplitude <= 0.0d) {
                lockedBranchReferenceToneRmsAmplitude = toneRmsAmplitude;
            } else {
                double smoothing = toneRmsAmplitude >= lockedBranchReferenceToneRmsAmplitude
                        ? LOCKED_BRANCH_REFERENCE_RISE_SMOOTHING
                        : LOCKED_BRANCH_REFERENCE_DECAY_SMOOTHING;
                lockedBranchReferenceToneRmsAmplitude +=
                        (toneRmsAmplitude - lockedBranchReferenceToneRmsAmplitude) * smoothing;
            }
            rememberRepresentativeLockedTone(targetToneFrequencyHz, toneRmsAmplitude, isolationRatio);
        }
    }

    private void updateToneHypothesis(
            ToneFrequencyEstimate acquisitionWinner,
            ToneFrequencyEstimate preferredWindowRunnerUp,
            ToneFrequencyEstimate wideScanRunnerUp,
            double trackedToneRmsAmplitude,
            double trackedIsolationRatio,
            boolean narrowbandQualified,
            long timestampMs
    ) {
        boolean acquisitionEvidence = acquisitionWinner != null
                && lastAcquisitionWinnerConfidence >= 0.18d
                && acquisitionEvidenceScore(acquisitionWinner) >= (MIN_TRACKED_TONE_RMS * 0.45d);
        boolean liveTargetEvidence = narrowbandQualified && trackedToneRmsAmplitude > 0.0d && targetToneFrequencyHz > 0;
        boolean lockedEvidence = targetToneLocked && trackedToneRmsAmplitude > 0.0d && targetToneFrequencyHz > 0;
        boolean hasEvidence = acquisitionEvidence || liveTargetEvidence || lockedEvidence;

        ageOutToneHypothesis(targetToneLocked, hasEvidence, timestampMs);

        String source = "IDLE";
        if (lockedEvidence) {
            addToneHypothesisEvidence(
                    targetToneFrequencyHz,
                    TONE_HYPOTHESIS_LOCKED_EVIDENCE_GAIN
                            * normalizeBetween(trackedToneRmsAmplitude, MIN_TRACKED_TONE_RMS, MIN_TRACKED_TONE_RMS * 6.0d)
                            * (0.50d + (0.50d * clamp01(trackedIsolationRatio)))
            );
            source = "LOCKED_TARGET";
        } else if (liveTargetEvidence) {
            addToneHypothesisEvidence(
                    targetToneFrequencyHz,
                    TONE_HYPOTHESIS_ACTIVE_EVIDENCE_GAIN
                            * normalizeBetween(trackedToneRmsAmplitude, MIN_TRACKED_TONE_RMS, MIN_TRACKED_TONE_RMS * 5.0d)
                            * (0.40d + (0.60d * clamp01(trackedIsolationRatio)))
            );
            source = "TRACKED_TARGET";
        }

        if (acquisitionEvidence) {
            addToneHypothesisEvidence(
                    acquisitionWinner.frequencyHz,
                    TONE_HYPOTHESIS_ACQUISITION_WINNER_GAIN
                            * Math.max(0.20d, lastAcquisitionWinnerConfidence)
                            * normalizeBetween(
                            acquisitionEvidenceScore(acquisitionWinner),
                            MIN_TRACKED_TONE_RMS * 0.90d,
                            MIN_TRACKED_TONE_RMS * 4.50d
                    )
            );
            if (!lockedEvidence && !liveTargetEvidence) {
                source = "ACQUISITION_WINNER";
            }
        }

        ToneFrequencyEstimate runnerUp = chooseToneHypothesisRunnerUp(
                acquisitionWinner,
                preferredWindowRunnerUp,
                wideScanRunnerUp
        );
        if (runnerUp != null) {
            addToneHypothesisEvidence(
                    runnerUp.frequencyHz,
                    TONE_HYPOTHESIS_RUNNER_UP_GAIN
                            * normalizeBetween(
                            acquisitionEvidenceScore(runnerUp),
                            MIN_TRACKED_TONE_RMS * 0.80d,
                            MIN_TRACKED_TONE_RMS * 3.40d
                    )
            );
        }
        recomputeToneHypothesis(source);
    }

    private ToneFrequencyEstimate chooseToneHypothesisRunnerUp(
            ToneFrequencyEstimate acquisitionWinner,
            ToneFrequencyEstimate preferredWindowRunnerUp,
            ToneFrequencyEstimate wideScanRunnerUp
    ) {
        ToneFrequencyEstimate bestRunnerUp = null;
        if (preferredWindowRunnerUp != null
                && (acquisitionWinner == null
                || Math.abs(preferredWindowRunnerUp.frequencyHz - acquisitionWinner.frequencyHz) >= TONE_SCAN_STEP_HZ)) {
            bestRunnerUp = preferredWindowRunnerUp;
        }
        if (wideScanRunnerUp != null
                && (acquisitionWinner == null
                || Math.abs(wideScanRunnerUp.frequencyHz - acquisitionWinner.frequencyHz) >= TONE_SCAN_STEP_HZ)
                && (bestRunnerUp == null || wideScanRunnerUp.selectionScore > bestRunnerUp.selectionScore)) {
            bestRunnerUp = wideScanRunnerUp;
        }
        return bestRunnerUp;
    }

    private void ageOutToneHypothesis(boolean locked, boolean hasEvidence, long timestampMs) {
        double decay = locked
                ? TONE_HYPOTHESIS_DECAY_LOCKED
                : hasEvidence
                ? TONE_HYPOTHESIS_DECAY_ACTIVE
                : isTrackedToneMemoryActive(timestampMs)
                ? TONE_HYPOTHESIS_DECAY_IDLE
                : TONE_HYPOTHESIS_DECAY_STALE;
        for (int index = 0; index < toneHypothesisPosterior.length; index++) {
            double decayed = toneHypothesisPosterior[index] * decay;
            toneHypothesisPosterior[index] = decayed < 0.0001d ? 0.0d : decayed;
        }
        toneHypothesisIdleFrames = hasEvidence ? 0 : toneHypothesisIdleFrames + 1;
        if (!hasEvidence && toneHypothesisIdleFrames >= TONE_HYPOTHESIS_CLEAR_IDLE_FRAMES) {
            clearPosterior();
            clearRecentHypothesisHistory();
            toneHypothesisFrequencyHz = preferredToneFrequencyHz;
            toneHypothesisConfidence = 0.0d;
            toneHypothesisTotalEvidence = 0.0d;
            toneHypothesisSupportFrames = 0;
            toneHypothesisSource = "NONE";
        }
    }

    private void addToneHypothesisEvidence(int frequencyHz, double evidenceWeight) {
        if (frequencyHz <= 0 || evidenceWeight <= 0.0d) {
            return;
        }
        int centerIndex = toneBucketIndex(clampPreferredToneFrequency(frequencyHz));
        for (int offset = -2; offset <= 2; offset++) {
            int index = centerIndex + offset;
            if (index < 0 || index >= toneHypothesisPosterior.length) {
                continue;
            }
            double spreadWeight;
            switch (Math.abs(offset)) {
                case 0:
                    spreadWeight = 1.0d;
                    break;
                case 1:
                    spreadWeight = 0.35d;
                    break;
                default:
                    spreadWeight = 0.12d;
                    break;
            }
            toneHypothesisPosterior[index] += evidenceWeight * spreadWeight;
        }
    }

    private void recomputeToneHypothesis(String source) {
        double totalEvidence = 0.0d;
        int bestIndex = -1;
        int runnerUpIndex = -1;
        double bestWeight = 0.0d;
        double runnerUpWeight = 0.0d;
        for (int index = 0; index < toneHypothesisPosterior.length; index++) {
            double weight = toneHypothesisPosterior[index];
            totalEvidence += weight;
            if (weight > bestWeight) {
                runnerUpWeight = bestWeight;
                runnerUpIndex = bestIndex;
                bestWeight = weight;
                bestIndex = index;
            } else if (weight > runnerUpWeight) {
                runnerUpWeight = weight;
                runnerUpIndex = index;
            }
        }
        toneHypothesisTotalEvidence = totalEvidence;
        if (bestIndex < 0 || bestWeight <= 0.0d) {
            clearRecentHypothesisHistory();
            toneHypothesisFrequencyHz = preferredToneFrequencyHz;
            toneHypothesisConfidence = 0.0d;
            toneHypothesisSupportFrames = 0;
            toneHypothesisSource = "NONE";
            return;
        }

        double clusterWeight = 0.0d;
        double clusterFrequencySum = 0.0d;
        for (int offset = -2; offset <= 2; offset++) {
            int index = bestIndex + offset;
            if (index < 0 || index >= toneHypothesisPosterior.length) {
                continue;
            }
            double weight = toneHypothesisPosterior[index];
            clusterWeight += weight;
            clusterFrequencySum += weight * bucketFrequencyHz(index);
        }
        toneHypothesisFrequencyHz = clusterWeight > 0.0d
                ? (int) Math.round(clusterFrequencySum / clusterWeight)
                : bucketFrequencyHz(bestIndex);

        double posteriorShare = totalEvidence <= 0.0d ? 0.0d : clusterWeight / totalEvidence;
        double runnerUpGuard = runnerUpIndex < 0 ? 0.0d : runnerUpWeight;
        double separationRatio = clusterWeight / Math.max(0.0001d, runnerUpGuard);
        if (runnerUpIndex < 0) {
            separationRatio = 3.0d;
        }
        toneHypothesisConfidence = clamp01(
                (normalizeBetween(posteriorShare, 0.24d, 0.70d) * 0.60d)
                        + (normalizeBetween(separationRatio, 1.03d, 2.60d) * 0.40d)
        );
        toneHypothesisSupportFrames = (totalEvidence >= TONE_HYPOTHESIS_MIN_TOTAL_EVIDENCE * 0.5d)
                ? toneHypothesisSupportFrames + 1
                : 0;
        toneHypothesisSource = source == null ? "NONE" : source;
        rememberRecentHypothesisFrequency();
    }

    private void clearPosterior() {
        for (int index = 0; index < toneHypothesisPosterior.length; index++) {
            toneHypothesisPosterior[index] = 0.0d;
        }
    }

    private void clearRecentHypothesisHistory() {
        for (int index = 0; index < recentHypothesisFrequencyHistoryHz.length; index++) {
            recentHypothesisFrequencyHistoryHz[index] = 0;
        }
        recentHypothesisHistoryCount = 0;
        recentHypothesisHistoryNextIndex = 0;
    }

    private void rememberRecentHypothesisFrequency() {
        if (!hasActiveToneHypothesis()) {
            return;
        }
        recentHypothesisFrequencyHistoryHz[recentHypothesisHistoryNextIndex] = toneHypothesisFrequencyHz;
        recentHypothesisHistoryNextIndex =
                (recentHypothesisHistoryNextIndex + 1) % recentHypothesisFrequencyHistoryHz.length;
        if (recentHypothesisHistoryCount < recentHypothesisFrequencyHistoryHz.length) {
            recentHypothesisHistoryCount += 1;
        }
    }

    private int hypothesisGuardHistorySpanHz() {
        if (recentHypothesisHistoryCount <= 0) {
            return 0;
        }
        int minFrequencyHz = Integer.MAX_VALUE;
        int maxFrequencyHz = Integer.MIN_VALUE;
        for (int index = 0; index < recentHypothesisHistoryCount; index++) {
            int frequencyHz = recentHypothesisFrequencyHistoryHz[index];
            if (frequencyHz <= 0) {
                continue;
            }
            minFrequencyHz = Math.min(minFrequencyHz, frequencyHz);
            maxFrequencyHz = Math.max(maxFrequencyHz, frequencyHz);
        }
        if (minFrequencyHz == Integer.MAX_VALUE || maxFrequencyHz == Integer.MIN_VALUE) {
            return 0;
        }
        return Math.max(0, maxFrequencyHz - minFrequencyHz);
    }

    private void evaluateExperimentalHypothesisGuard(
            ToneFrequencyEstimate searchEstimate,
            double trackedToneRmsAmplitude,
            double trackedIsolationRatio,
            boolean narrowbandQualified
    ) {
        // Product intent:
        // 1. Once front-end has stabilized to a plausible CW tone, continuity should
        //    be sticky and noise should not drag the decode center far away easily.
        // 2. Large tone changes are expected only in narrower cases such as a new OP
        //    arriving later on the same listening session, or an explicit radio/CAT
        //    retune signal in future integrations.
        // 3. This prototype keeps the intervention narrow: it does not directly rewrite
        //    target on the same frame. Instead it arms a next-frame acquisition override
        //    when the stable-single-peak evidence is strong enough.
        pendingHypothesisGuardEligible = false;
        pendingHypothesisGuardFrequencyHz = 0;
        lastHypothesisGuardEligible = false;
        lastHypothesisGuardApplied = false;
        lastHypothesisGuardAppliedFrequencyHz = 0;
        if (!experimentalHypothesisGuardEnabled) {
            lastHypothesisGuardDecision = "DISABLED";
            return;
        }
        if (!hasActiveToneHypothesis()) {
            lastHypothesisGuardDecision = "BLOCKED:NO_ACTIVE_HYPOTHESIS";
            return;
        }
        if (toneHypothesisSupportFrames < HYPOTHESIS_GUARD_MIN_SUPPORT_FRAMES) {
            lastHypothesisGuardDecision = "BLOCKED:LOW_SUPPORT";
            return;
        }
        if (toneHypothesisConfidence < HYPOTHESIS_GUARD_MIN_CONFIDENCE) {
            lastHypothesisGuardDecision = "BLOCKED:LOW_CONFIDENCE";
            return;
        }
        if (recentHypothesisHistoryCount < HYPOTHESIS_GUARD_MIN_SUPPORT_FRAMES) {
            lastHypothesisGuardDecision = "BLOCKED:SHORT_HISTORY";
            return;
        }
        int historySpanHz = hypothesisGuardHistorySpanHz();
        if (historySpanHz > HYPOTHESIS_GUARD_STABLE_SPAN_MAX_HZ) {
            lastHypothesisGuardDecision = "BLOCKED:VARIABLE_HYPOTHESIS_SPAN";
            return;
        }
        if (representativeLockedToneFrameCount < HYPOTHESIS_GUARD_MIN_REPRESENTATIVE_FRAMES) {
            lastHypothesisGuardDecision = "BLOCKED:NO_REPRESENTATIVE_LOCK";
            return;
        }
        if (Math.abs(toneHypothesisFrequencyHz - representativeLockedToneFrequencyHz)
                > HYPOTHESIS_GUARD_REPRESENTATIVE_MAX_OFFSET_HZ) {
            lastHypothesisGuardDecision = "BLOCKED:HYP_NOT_NEAR_REPRESENTATIVE";
            return;
        }
        int targetToHypothesisDriftHz = Math.abs(targetToneFrequencyHz - toneHypothesisFrequencyHz);
        if (targetToHypothesisDriftHz < HYPOTHESIS_GUARD_MIN_DRIFT_FROM_TARGET_HZ) {
            lastHypothesisGuardDecision = "BLOCKED:TARGET_ALREADY_NEAR_HYP";
            return;
        }
        if (targetToHypothesisDriftHz > HYPOTHESIS_GUARD_MAX_DRIFT_FROM_TARGET_HZ) {
            lastHypothesisGuardDecision = "BLOCKED:DRIFT_TOO_LARGE";
            return;
        }
        if (Math.abs(targetToneFrequencyHz - representativeLockedToneFrequencyHz)
                < HYPOTHESIS_GUARD_MIN_DRIFT_FROM_TARGET_HZ) {
            lastHypothesisGuardDecision = "BLOCKED:TARGET_STILL_NEAR_REPRESENTATIVE";
            return;
        }
        if (narrowbandQualified
                && trackedIsolationRatio >= HYPOTHESIS_GUARD_BLOCK_STRONG_ISOLATION_RATIO
                && trackedToneRmsAmplitude >= (MIN_TRACKED_TONE_RMS * HYPOTHESIS_GUARD_BLOCK_STRONG_TONE_MULTIPLIER)) {
            lastHypothesisGuardDecision = "BLOCKED:CURRENT_TARGET_STRONG";
            return;
        }
        lastHypothesisGuardEligible = true;
        pendingHypothesisGuardEligible = false;
        pendingHypothesisGuardFrequencyHz = 0;
        lastHypothesisGuardApplied = false;
        lastHypothesisGuardAppliedFrequencyHz = 0;
        lastHypothesisGuardDecision = "ELIGIBLE:OBSERVE_ONLY";
    }

    private ToneFrequencyEstimate maybeBuildExperimentalLockedConsensusEstimate(
            short[] samples,
            int sampleRateHz,
            double frameRms,
            int previousTargetToneFrequencyHz
    ) {
        if (!experimentalHypothesisGuardEnabled || trackingState == TrackingState.SEARCH) {
            return null;
        }
        int consensusFrequencyHz = lockedConsensusFrequencyHz();
        if (consensusFrequencyHz <= 0) {
            return null;
        }
        if (Math.abs(previousTargetToneFrequencyHz - consensusFrequencyHz) < LOCKED_CONSENSUS_GUARD_MIN_DRIFT_HZ) {
            return null;
        }
        ToneFrequencyEstimate rawConsensusEstimate = evaluateToneEstimate(
                samples,
                sampleRateHz,
                frameRms,
                consensusFrequencyHz,
                0.0d
        );
        double consensusSelectionScore = scoreToneCandidate(
                consensusFrequencyHz,
                previousTargetToneFrequencyHz,
                true,
                rawConsensusEstimate
        ) * rawConsensusEstimate.toneRmsAmplitude;
        return new ToneFrequencyEstimate(
                rawConsensusEstimate.frequencyHz,
                rawConsensusEstimate.toneRmsAmplitude,
                rawConsensusEstimate.widebandResidualRmsAmplitude,
                rawConsensusEstimate.dominanceRatio,
                rawConsensusEstimate.isolationRatio,
                rawConsensusEstimate.localContrastRatio,
                rawConsensusEstimate.locked,
                consensusSelectionScore
        );
    }

    private ToneFrequencyEstimate maybeBuildExperimentalLiveConsensusEstimate(
            short[] samples,
            int sampleRateHz,
            double frameRms,
            int liveTargetToneFrequencyHz
    ) {
        if (!experimentalHypothesisGuardEnabled) {
            return null;
        }
        int consensusFrequencyHz = lockedConsensusFrequencyHz();
        if (consensusFrequencyHz <= 0) {
            return null;
        }
        if (Math.abs(liveTargetToneFrequencyHz - consensusFrequencyHz) < LOCKED_CONSENSUS_GUARD_MIN_DRIFT_HZ) {
            return null;
        }
        ToneFrequencyEstimate rawConsensusEstimate = evaluateToneEstimate(
                samples,
                sampleRateHz,
                frameRms,
                consensusFrequencyHz,
                0.0d
        );
        double consensusSelectionScore = scoreToneCandidate(
                consensusFrequencyHz,
                liveTargetToneFrequencyHz,
                true,
                rawConsensusEstimate
        ) * rawConsensusEstimate.toneRmsAmplitude;
        return new ToneFrequencyEstimate(
                rawConsensusEstimate.frequencyHz,
                rawConsensusEstimate.toneRmsAmplitude,
                rawConsensusEstimate.widebandResidualRmsAmplitude,
                rawConsensusEstimate.dominanceRatio,
                rawConsensusEstimate.isolationRatio,
                rawConsensusEstimate.localContrastRatio,
                rawConsensusEstimate.locked,
                consensusSelectionScore
        );
    }

    private int lockedConsensusFrequencyHz() {
        if (representativeLockedToneFrameCount < HYPOTHESIS_GUARD_MIN_REPRESENTATIVE_FRAMES) {
            return 0;
        }
        if (!hasActiveToneHypothesis()
                || toneHypothesisSupportFrames < HYPOTHESIS_GUARD_MIN_SUPPORT_FRAMES
                || toneHypothesisConfidence < HYPOTHESIS_GUARD_MIN_CONFIDENCE) {
            return 0;
        }
        if (activeAcquisitionWinnerObservationCount < LOCKED_CONSENSUS_GUARD_MIN_ACTIVE_CENTER_OBSERVATIONS) {
            return 0;
        }
        int activeCenterFrequencyHz = histogramLeaderFrequencyHz(activeAcquisitionWinnerHistogram);
        if (activeCenterFrequencyHz <= 0) {
            return 0;
        }
        int minConsensusFrequencyHz = Math.min(
                representativeLockedToneFrequencyHz,
                Math.min(toneHypothesisFrequencyHz, activeCenterFrequencyHz)
        );
        int maxConsensusFrequencyHz = Math.max(
                representativeLockedToneFrequencyHz,
                Math.max(toneHypothesisFrequencyHz, activeCenterFrequencyHz)
        );
        if ((maxConsensusFrequencyHz - minConsensusFrequencyHz) > LOCKED_CONSENSUS_GUARD_MAX_SOURCE_SPAN_HZ) {
            return 0;
        }
        return clampPreferredToneFrequency((int) Math.round(
                (representativeLockedToneFrequencyHz + toneHypothesisFrequencyHz + activeCenterFrequencyHz) / 3.0d
        ));
    }

    private void rememberRepresentativeLockedTone(
            int frequencyHz,
            double toneRmsAmplitude,
            double isolationRatio
    ) {
        if (frequencyHz <= 0 || toneRmsAmplitude <= 0.0d) {
            return;
        }
        double frameScore = toneRmsAmplitude * Math.max(0.10d, isolationRatio);
        if (currentRepresentativeLockedToneFrameCount > 0
                && Math.abs(frequencyHz - currentRepresentativeLockedToneFrequencyHz)
                <= REPRESENTATIVE_LOCKED_TONE_CLUSTER_WINDOW_HZ) {
            double nextFrameCount = currentRepresentativeLockedToneFrameCount + 1.0d;
            currentRepresentativeLockedToneFrequencyHz = (int) Math.round(
                    ((currentRepresentativeLockedToneFrequencyHz * currentRepresentativeLockedToneFrameCount)
                            + frequencyHz) / nextFrameCount
            );
            currentRepresentativeLockedToneFrameCount += 1;
            currentRepresentativeLockedToneScore += frameScore;
        } else {
            currentRepresentativeLockedToneFrequencyHz = frequencyHz;
            currentRepresentativeLockedToneFrameCount = 1;
            currentRepresentativeLockedToneScore = frameScore;
        }

        if (shouldPromoteRepresentativeLockedTone()) {
            if (representativeLockedToneFrameCount > 0
                    && Math.abs(currentRepresentativeLockedToneFrequencyHz - representativeLockedToneFrequencyHz)
                    <= REPRESENTATIVE_LOCKED_TONE_CLUSTER_WINDOW_HZ) {
                double totalFrameWeight = representativeLockedToneFrameCount
                        + currentRepresentativeLockedToneFrameCount;
                representativeLockedToneFrequencyHz = (int) Math.round(
                        ((representativeLockedToneFrequencyHz * representativeLockedToneFrameCount)
                                + (currentRepresentativeLockedToneFrequencyHz
                                * currentRepresentativeLockedToneFrameCount))
                                / Math.max(1.0d, totalFrameWeight)
                );
                representativeLockedToneFrameCount = Math.max(
                        representativeLockedToneFrameCount,
                        currentRepresentativeLockedToneFrameCount
                );
                representativeLockedToneScore = Math.max(
                        representativeLockedToneScore,
                        currentRepresentativeLockedToneScore
                );
            } else {
                representativeLockedToneFrequencyHz = currentRepresentativeLockedToneFrequencyHz;
                representativeLockedToneFrameCount = currentRepresentativeLockedToneFrameCount;
                representativeLockedToneScore = currentRepresentativeLockedToneScore;
            }
        }
    }

    private boolean shouldPromoteRepresentativeLockedTone() {
        if (currentRepresentativeLockedToneFrameCount <= 0) {
            return false;
        }
        if (representativeLockedToneFrameCount <= 0) {
            return true;
        }
        if (Math.abs(currentRepresentativeLockedToneFrequencyHz - representativeLockedToneFrequencyHz)
                <= REPRESENTATIVE_LOCKED_TONE_CLUSTER_WINDOW_HZ) {
            return true;
        }
        if (currentRepresentativeLockedToneFrameCount < REPRESENTATIVE_LOCKED_TONE_REPLACEMENT_MIN_FRAMES) {
            return false;
        }
        double representativeAverageScore = representativeLockedToneScore
                / Math.max(1, representativeLockedToneFrameCount);
        double currentAverageScore = currentRepresentativeLockedToneScore
                / Math.max(1, currentRepresentativeLockedToneFrameCount);
        if (currentAverageScore
                >= (representativeAverageScore
                * REPRESENTATIVE_LOCKED_TONE_REPLACEMENT_MIN_AVERAGE_SCORE_RATIO)) {
            return true;
        }
        int currentDistanceHz = Math.abs(currentRepresentativeLockedToneFrequencyHz - preferredToneFrequencyHz);
        int representativeDistanceHz = Math.abs(representativeLockedToneFrequencyHz - preferredToneFrequencyHz);
        return currentDistanceHz < representativeDistanceHz
                && currentAverageScore >= (representativeAverageScore * 0.70d);
    }

    private double normalizeBetween(double value, double minimum, double maximum) {
        if (maximum <= minimum) {
            return value >= maximum ? 1.0d : 0.0d;
        }
        double normalized = (value - minimum) / (maximum - minimum);
        return Math.max(0.0d, Math.min(1.0d, normalized));
    }

    private double computeNarrowbandIsolationRatio(double toneRmsAmplitude, double widebandResidualRmsAmplitude) {
        if (toneRmsAmplitude <= 0.0d) {
            return 0.0d;
        }
        double denominator = toneRmsAmplitude + Math.max(0.0d, widebandResidualRmsAmplitude);
        if (denominator <= 0.0d) {
            return 0.0d;
        }
        return toneRmsAmplitude / denominator;
    }

    private double computeLocalContrastRatio(double toneRmsAmplitude, double adjacentToneRmsAmplitude) {
        if (toneRmsAmplitude <= 0.0d) {
            return 0.0d;
        }
        double denominator = toneRmsAmplitude + Math.max(0.0d, adjacentToneRmsAmplitude);
        if (denominator <= 0.0d) {
            return 0.0d;
        }
        return toneRmsAmplitude / denominator;
    }

    private double estimateWidebandResidualRms(double frameRms, double toneRmsAmplitude) {
        double framePower = Math.max(0.0d, frameRms * frameRms);
        double tonePower = Math.max(0.0d, toneRmsAmplitude * toneRmsAmplitude);
        return Math.sqrt(Math.max(0.0d, framePower - tonePower));
    }

    private double estimateAdjacentToneRms(short[] samples, int sampleRateHz, int targetFrequencyHz) {
        double strongestAdjacentRms = 0.0d;
        for (int offsetHz : LOCAL_CONTRAST_OFFSETS_HZ) {
            strongestAdjacentRms = Math.max(
                    strongestAdjacentRms,
                    estimateToneRms(samples, sampleRateHz, clampPreferredToneFrequency(targetFrequencyHz - offsetHz))
            );
            strongestAdjacentRms = Math.max(
                    strongestAdjacentRms,
                    estimateToneRms(samples, sampleRateHz, clampPreferredToneFrequency(targetFrequencyHz + offsetHz))
            );
        }
        return strongestAdjacentRms;
    }

    private double estimateToneRms(short[] samples, int sampleRateHz, int targetFrequencyHz) {
        if (samples == null || samples.length == 0 || sampleRateHz <= 0 || targetFrequencyHz <= 0) {
            return 0.0d;
        }

        double omega = (2.0d * Math.PI * targetFrequencyHz) / sampleRateHz;
        double coeff = 2.0d * Math.cos(omega);
        double q0 = 0.0d;
        double q1 = 0.0d;
        double q2 = 0.0d;

        for (short sample : samples) {
            q0 = coeff * q1 - q2 + sample;
            q2 = q1;
            q1 = q0;
        }

        double power = (q1 * q1) + (q2 * q2) - (coeff * q1 * q2);
        if (power <= 0.0d) {
            return 0.0d;
        }
        double magnitude = Math.sqrt(power);
        return (magnitude * Math.sqrt(2.0d)) / samples.length;
    }

    private int clampPreferredToneFrequency(int preferredToneFrequencyHz) {
        return Math.max(
                LOW_EDGE_BAND_MIN_HZ,
                Math.min(MAX_TRACKED_TONE_FREQUENCY_HZ, preferredToneFrequencyHz)
        );
    }

    private boolean isTrackedToneMemoryActive(long timestampMs) {
        return lastTrackedToneTimestampMs >= 0L
                && timestampMs >= lastTrackedToneTimestampMs
                && (timestampMs - lastTrackedToneTimestampMs) <= currentTrackedToneIdleHangMs();
    }

    private long currentTrackedToneIdleHangMs() {
        long dynamicHangMs = TRACKED_TONE_IDLE_HANG_MS;
        if (lastEvent != null
                && lastEvent.type() == CwToneEvent.Type.TONE_OFF
                && lastEvent.toneDurationMs() > 0L) {
            dynamicHangMs = Math.max(
                    TRACKED_TONE_IDLE_HANG_MIN_MS,
                    Math.round(lastEvent.toneDurationMs() * TRACKED_TONE_IDLE_HANG_DOT_RATIO / 3.0d)
            );
        }
        return Math.max(TRACKED_TONE_IDLE_HANG_MIN_MS, dynamicHangMs);
    }

    private int recordHistogramHit(int[] histogram, int frequencyHz) {
        int bucketIndex = toneBucketIndex(frequencyHz);
        if (histogram == null || bucketIndex < 0) {
            return 0;
        }
        histogram[bucketIndex] += 1;
        return 1;
    }

    private int toneBucketIndex(int frequencyHz) {
        if (frequencyHz < MIN_TRACKED_TONE_FREQUENCY_HZ || frequencyHz > MAX_TRACKED_TONE_FREQUENCY_HZ) {
            return -1;
        }
        if (((frequencyHz - MIN_TRACKED_TONE_FREQUENCY_HZ) % TONE_SCAN_STEP_HZ) != 0) {
            return -1;
        }
        return (frequencyHz - MIN_TRACKED_TONE_FREQUENCY_HZ) / TONE_SCAN_STEP_HZ;
    }

    private int bucketFrequencyHz(int bucketIndex) {
        return MIN_TRACKED_TONE_FREQUENCY_HZ + (bucketIndex * TONE_SCAN_STEP_HZ);
    }

    private int histogramLeaderFrequencyHz(int[] histogram) {
        int bucketIndex = histogramLeaderBucketIndex(histogram);
        return bucketIndex < 0 ? 0 : bucketFrequencyHz(bucketIndex);
    }

    private int histogramLeaderHitCount(int[] histogram) {
        int bucketIndex = histogramLeaderBucketIndex(histogram);
        return bucketIndex < 0 || histogram == null ? 0 : histogram[bucketIndex];
    }

    private int histogramLeaderBucketIndex(int[] histogram) {
        if (histogram == null) {
            return -1;
        }
        int topIndex = -1;
        for (int index = 0; index < histogram.length; index++) {
            int hitCount = histogram[index];
            if (hitCount <= 0) {
                continue;
            }
            if (topIndex < 0 || hitCount > histogram[topIndex]) {
                topIndex = index;
            }
        }
        return topIndex;
    }

    private void clearHistogram(int[] histogram) {
        if (histogram == null) {
            return;
        }
        for (int index = 0; index < histogram.length; index++) {
            histogram[index] = 0;
        }
    }

    private String renderLeaderHistogramSummary(int[] histogram, int observationCount) {
        if (histogram == null || observationCount <= 0) {
            return "none";
        }
        int lowEdgeCount = 0;
        int targetishCount = 0;
        int otherCount = 0;
        int firstIndex = -1;
        int secondIndex = -1;
        int thirdIndex = -1;
        for (int index = 0; index < histogram.length; index++) {
            int hitCount = histogram[index];
            if (hitCount <= 0) {
                continue;
            }
            int frequencyHz = bucketFrequencyHz(index);
            if (frequencyHz >= LOW_EDGE_BAND_MIN_HZ && frequencyHz <= LOW_EDGE_BAND_MAX_HZ) {
                lowEdgeCount += hitCount;
            } else if (frequencyHz >= TARGETISH_BAND_MIN_HZ && frequencyHz <= TARGETISH_BAND_MAX_HZ) {
                targetishCount += hitCount;
            } else {
                otherCount += hitCount;
            }
            if (firstIndex < 0 || hitCount > histogram[firstIndex]) {
                thirdIndex = secondIndex;
                secondIndex = firstIndex;
                firstIndex = index;
            } else if (secondIndex < 0 || hitCount > histogram[secondIndex]) {
                thirdIndex = secondIndex;
                secondIndex = index;
            } else if (thirdIndex < 0 || hitCount > histogram[thirdIndex]) {
                thirdIndex = index;
            }
        }
        StringBuilder builder = new StringBuilder()
                .append("450-470 ")
                .append(lowEdgeCount)
                .append(" (")
                .append(Math.round((lowEdgeCount * 100.0d) / observationCount))
                .append("%), 650-700 ")
                .append(targetishCount)
                .append(" (")
                .append(Math.round((targetishCount * 100.0d) / observationCount))
                .append("%), other ")
                .append(otherCount)
                .append(" (")
                .append(Math.round((otherCount * 100.0d) / observationCount))
                .append("%)");
        appendTopHistogramBucket(builder, histogram, firstIndex, 1);
        appendTopHistogramBucket(builder, histogram, secondIndex, 2);
        appendTopHistogramBucket(builder, histogram, thirdIndex, 3);
        return builder.toString();
    }

    private String renderCompactBandSummary(int[] histogram, int observationCount) {
        if (histogram == null || observationCount <= 0) {
            return "none";
        }
        int lowEdgeCount = 0;
        int targetishCount = 0;
        int otherCount = 0;
        int topIndex = -1;
        for (int index = 0; index < histogram.length; index++) {
            int hitCount = histogram[index];
            if (hitCount <= 0) {
                continue;
            }
            int frequencyHz = bucketFrequencyHz(index);
            if (frequencyHz >= LOW_EDGE_BAND_MIN_HZ && frequencyHz <= LOW_EDGE_BAND_MAX_HZ) {
                lowEdgeCount += hitCount;
            } else if (frequencyHz >= TARGETISH_BAND_MIN_HZ && frequencyHz <= TARGETISH_BAND_MAX_HZ) {
                targetishCount += hitCount;
            } else {
                otherCount += hitCount;
            }
            if (topIndex < 0 || hitCount > histogram[topIndex]) {
                topIndex = index;
            }
        }
        return "450-470="
                + Math.round((lowEdgeCount * 100.0d) / observationCount)
                + "%, 650-700="
                + Math.round((targetishCount * 100.0d) / observationCount)
                + "%, other="
                + Math.round((otherCount * 100.0d) / observationCount)
                + "%, top="
                + (topIndex < 0 ? "none" : bucketFrequencyHz(topIndex) + "Hz x" + histogram[topIndex]);
    }

    private void observeHypothesisCompetition(
            int centerFrequencyHz,
            int trackedFrequencyHz,
            int hypothesisFrequencyHz,
            boolean representativeCenter
    ) {
        if (centerFrequencyHz <= 0 || trackedFrequencyHz <= 0 || hypothesisFrequencyHz <= 0) {
            if (representativeCenter) {
                representativeCompetitionHypothesisCurrentWinStreak = 0;
            } else {
                activeCenterCompetitionHypothesisCurrentWinStreak = 0;
            }
            return;
        }
        int trackedDistanceHz = Math.abs(trackedFrequencyHz - centerFrequencyHz);
        int hypothesisDistanceHz = Math.abs(hypothesisFrequencyHz - centerFrequencyHz);
        if (representativeCenter) {
            representativeCompetitionObservationCount += 1;
            if (hypothesisDistanceHz < trackedDistanceHz) {
                representativeCompetitionHypothesisWinFrames += 1;
                representativeCompetitionHypothesisCurrentWinStreak += 1;
                representativeCompetitionHypothesisMaxWinStreak = Math.max(
                        representativeCompetitionHypothesisMaxWinStreak,
                        representativeCompetitionHypothesisCurrentWinStreak
                );
                return;
            }
            representativeCompetitionHypothesisCurrentWinStreak = 0;
            if (trackedDistanceHz < hypothesisDistanceHz) {
                representativeCompetitionTrackedWinFrames += 1;
            } else {
                representativeCompetitionTieFrames += 1;
            }
            return;
        }
        activeCenterCompetitionObservationCount += 1;
        if (hypothesisDistanceHz < trackedDistanceHz) {
            activeCenterCompetitionHypothesisWinFrames += 1;
            activeCenterCompetitionHypothesisCurrentWinStreak += 1;
            activeCenterCompetitionHypothesisMaxWinStreak = Math.max(
                    activeCenterCompetitionHypothesisMaxWinStreak,
                    activeCenterCompetitionHypothesisCurrentWinStreak
            );
            return;
        }
        activeCenterCompetitionHypothesisCurrentWinStreak = 0;
        if (trackedDistanceHz < hypothesisDistanceHz) {
            activeCenterCompetitionTrackedWinFrames += 1;
        } else {
            activeCenterCompetitionTieFrames += 1;
        }
    }

    private String renderCompetitionSummary(
            int observationCount,
            int trackedWinFrames,
            int hypothesisWinFrames,
            int tieFrames,
            int currentHypothesisWinStreak,
            int maxHypothesisWinStreak
    ) {
        if (observationCount <= 0) {
            return "none";
        }
        return "obs=" + observationCount
                + " trk=" + trackedWinFrames
                + " hyp=" + hypothesisWinFrames
                + " tie=" + tieFrames
                + " hypStreak=" + currentHypothesisWinStreak
                + "/" + maxHypothesisWinStreak;
    }

    private String renderCompactCompetitionSummary(
            int observationCount,
            int trackedWinFrames,
            int hypothesisWinFrames,
            int tieFrames,
            int maxHypothesisWinStreak
    ) {
        if (observationCount <= 0) {
            return "none";
        }
        return "trk=" + trackedWinFrames
                + ", hyp=" + hypothesisWinFrames
                + ", tie=" + tieFrames
                + ", maxHyp=" + maxHypothesisWinStreak;
    }

    private void appendTopHistogramBucket(
            StringBuilder builder,
            int[] histogram,
            int bucketIndex,
            int rank
    ) {
        if (builder == null || histogram == null || bucketIndex < 0 || rank <= 0) {
            return;
        }
        builder.append(" | #")
                .append(rank)
                .append(' ')
                .append(bucketFrequencyHz(bucketIndex))
                .append("Hz x")
                .append(histogram[bucketIndex]);
    }

    private static final class ToneFrequencyEstimate {
        private final int frequencyHz;
        private final double toneRmsAmplitude;
        private final double widebandResidualRmsAmplitude;
        private final double dominanceRatio;
        private final double isolationRatio;
        private final double localContrastRatio;
        private final boolean locked;
        private final double selectionScore;

        private ToneFrequencyEstimate(
                int frequencyHz,
                double toneRmsAmplitude,
                double widebandResidualRmsAmplitude,
                double dominanceRatio,
                double isolationRatio,
                double localContrastRatio,
                boolean locked,
                double selectionScore
        ) {
            this.frequencyHz = frequencyHz;
            this.toneRmsAmplitude = toneRmsAmplitude;
            this.widebandResidualRmsAmplitude = widebandResidualRmsAmplitude;
            this.dominanceRatio = dominanceRatio;
            this.isolationRatio = isolationRatio;
            this.localContrastRatio = localContrastRatio;
            this.locked = locked;
            this.selectionScore = selectionScore;
        }
    }

    private static final class ToneScanResult {
        private final ToneFrequencyEstimate winner;
        private final ToneFrequencyEstimate runnerUp;
        private final double winnerConfidence;

        private ToneScanResult(
                ToneFrequencyEstimate winner,
                ToneFrequencyEstimate runnerUp,
                double winnerConfidence
        ) {
            this.winner = winner;
            this.runnerUp = runnerUp;
            this.winnerConfidence = winnerConfidence;
        }

        private static ToneScanResult empty() {
            return new ToneScanResult(null, null, 0.0d);
        }
    }
}
