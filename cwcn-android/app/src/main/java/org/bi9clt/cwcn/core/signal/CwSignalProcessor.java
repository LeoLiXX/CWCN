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
    private static final int MIN_TRACKED_TONE_FREQUENCY_HZ = 450;
    private static final int MAX_TRACKED_TONE_FREQUENCY_HZ = 850;
    private static final int TONE_SCAN_STEP_HZ = 10;
    private static final int TONE_BUCKET_COUNT =
            ((MAX_TRACKED_TONE_FREQUENCY_HZ - MIN_TRACKED_TONE_FREQUENCY_HZ) / TONE_SCAN_STEP_HZ) + 1;
    private static final int RETUNE_INTERVAL_FRAMES = 4;
    private static final int PREFERRED_TONE_SCAN_WINDOW_HZ = 160;
    private static final int UNLOCKED_ACQUISITION_SCAN_WINDOW_HZ = 160;
    private static final int LOCK_RETUNE_WINDOW_HZ = 50;
    private static final int FIXED_TONE_PREFERRED_SCAN_WINDOW_HZ = 50;
    private static final int FIXED_TONE_UNLOCKED_SCAN_WINDOW_HZ = 70;
    private static final int FIXED_TONE_LOCK_RETUNE_WINDOW_HZ = 30;
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
    private static final int MIN_THRESHOLD = 220;
    private static final int BASE_MARGIN = 140;
    private static final int DEFAULT_SQL_PERCENT = 55;
    private static final int EDGE_WINDOW_SAMPLES = 12;
    private static final int EDGE_CONFIRM_SAMPLES = 6;
    private static final double EDGE_THRESHOLD_RATIO = 0.30d;
    private static final double EDGE_DYNAMIC_RATIO = 0.24d;
    private static final double EDGE_TRANSITION_REQUIRED_RATIO = 0.82d;
    private static final double NOISE_FLOOR_RISE_SMOOTHING = 0.025d;
    private static final double NOISE_FLOOR_DROP_SMOOTHING = 0.14d;
    private static final double SIGNAL_FLOOR_SMOOTHING = 0.18d;
    private static final double SIGNAL_FLOOR_IDLE_DECAY_SMOOTHING = 0.62d;
    private static final int LOCKED_BRANCH_REFERENCE_MIN_STREAK_FRAMES = 4;
    private static final double LOCKED_BRANCH_REFERENCE_RISE_SMOOTHING = 0.24d;
    private static final double LOCKED_BRANCH_REFERENCE_DECAY_SMOOTHING = 0.03d;
    private static final double LOCKED_BRANCH_WEAK_RATIO = 0.46d;
    private static final double LOCKED_BRANCH_REFERENCE_MIN_THRESHOLD_MULTIPLIER = 1.75d;
    private static final long TONE_OFF_HANG_MS = 4L;
    private static final long AUTO_TRACK_TONE_OFF_HANG_MS = 14L;
    private static final int AUTO_TRACK_WEAK_VALLEY_BRIDGE_FRAMES = 2;
    private static final long TRACKED_TONE_IDLE_HANG_MS = 240L;
    private static final long TRACKED_TONE_IDLE_HANG_MIN_MS = 240L;
    private static final double TRACKED_TONE_IDLE_HANG_DOT_RATIO = 7.2d;
    private static final long FRAME_GAP_RESET_MIN_MS = 40L;
    private static final double FRAME_GAP_RESET_MULTIPLIER = 4.0d;
    private static final int REPRESENTATIVE_LOCKED_TONE_CLUSTER_WINDOW_HZ = 35;
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
    private static final int LOCKED_RETUNE_GUARD_FAR_DRIFT_HZ = 80;
    private static final int LOCKED_RETUNE_GUARD_MIN_STABLE_LOCK_FRAMES = 8;
    private static final int LOCKED_RETUNE_GUARD_REQUIRED_SCANS = 2;
    private static final int LOCKED_RETUNE_GUARD_FAR_REQUIRED_SCANS = 3;
    private static final int LOCKED_RETUNE_GUARD_WEAK_REFERENCE_EXTRA_SCANS = 2;
    private static final double LOCKED_RETUNE_GUARD_STRONG_SCORE_RATIO = 1.38d;
    private static final double LOCKED_RETUNE_GUARD_STRONG_TONE_RATIO = 1.45d;
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
    private static final int FAR_ATTACK_CONFIRM_DRIFT_HZ = 60;
    private static final int FAR_ATTACK_CONFIRM_REQUIRED_FRAMES = 2;
    private static final double FAR_ATTACK_CONFIRM_REFERENCE_TONE_RATIO = 1.45d;
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

    private boolean initialized;
    private boolean toneActive;
    private boolean targetToneLocked;
    private double noiseFloorEstimate;
    private double signalFloorEstimate;
    private int sqlPercent = DEFAULT_SQL_PERCENT;
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
    private boolean autoTrackWeakValleyBridgeActive;
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

    public synchronized List<CwToneEvent> process(AudioFrame frame) {
        ArrayList<CwToneEvent> events = new ArrayList<>(1);
        long timestampMs = frame.capturedAtMs();
        handleFrameGapReset(frame, timestampMs, events);
        double frameRms = frame.rmsAmplitude();
        double previousFrameRmsAmplitude = lastRmsAmplitude;
        double previousToneRmsAmplitude = lastToneRmsAmplitude;
        boolean hadToneHistoryBeforeFrame = totalToneOnEvents > 0 || totalToneOffEvents > 0;
        boolean hadTrackedToneMemoryBeforeFrame = hadToneHistoryBeforeFrame
                && isTrackedToneMemoryActive(timestampMs);
        int attackAnchorFrequencyHzBeforeFrame = hadToneHistoryBeforeFrame
                ? continuityAnchorFrequencyHz()
                : preferredToneFrequencyHz;
        double attackReferenceToneRmsBeforeFrame = lockedBranchReferenceToneRmsAmplitude;
        ToneFrequencyEstimate toneEstimate = analyzeToneFrequency(frame);
        double detectionLevel = effectiveDetectionLevel(frameRms, toneEstimate);
        double backgroundObservationLevel = backgroundObservationLevel(frameRms, toneEstimate);
        boolean attackQualified = isNarrowbandQualified(toneEstimate);
        int attackThreshold = currentThreshold();
        int releaseThreshold = currentReleaseThreshold();
        // Same-tone weak-residue suppression is intentionally not part of the
        // main RX path right now. In practice it helped a narrow cochannel case,
        // but it also regressed higher-priority normal/noisy baselines.
        // Keep the helper implementation around for future lab work, but do not
        // let it steer attack/release decisions until it can be scoped safely.

        if (!initialized) {
            noiseFloorEstimate = backgroundObservationLevel;
            signalFloorEstimate = Math.max(backgroundObservationLevel, detectionLevel);
            attackThreshold = currentThreshold();
            releaseThreshold = currentReleaseThreshold();
            initialized = true;
        }

        boolean weakLockedResidueHoldingSignalFloor = isAutoTrackWeakValleyCandidate(toneEstimate, attackThreshold);
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

        if (toneActive || !attackQualified || detectionLevel < attackThreshold) {
            clearPendingFarAttackCandidate();
        }

        if (!toneActive && attackQualified && detectionLevel >= attackThreshold) {
            if (shouldDelayFarAttackToneOn(
                    toneEstimate,
                    hadToneHistoryBeforeFrame,
                    hadTrackedToneMemoryBeforeFrame,
                    attackAnchorFrequencyHzBeforeFrame,
                    attackReferenceToneRmsBeforeFrame
            )) {
                rememberFrameLeaderObservability(false);
                rememberToneActivityWindow();
                rememberFrame(timestampMs, detectionLevel);
                return events;
            }
            long frameLocalToneOnTimestampMs = estimateFrameLocalTransitionTimestamp(frame, true);
            if (shouldSuppressPostReleaseSteadyCarrierToneOn(
                    toneEstimate,
                    frameRms,
                    previousFrameRmsAmplitude,
                    previousToneRmsAmplitude,
                    frameLocalToneOnTimestampMs
            )) {
                rememberFrameLeaderObservability(false);
                rememberToneActivityWindow();
                rememberFrame(timestampMs, detectionLevel);
                return events;
            }
            long toneOnTimestampMs = estimateCrossingTimestamp(timestampMs, attackThreshold, detectionLevel, true);
            if (frameLocalToneOnTimestampMs >= 0L) {
                toneOnTimestampMs = frameLocalToneOnTimestampMs;
            }
            toneActive = true;
            toneStartedAtMs = toneOnTimestampMs;
            silenceStartedAtMs = -1L;
            autoTrackWeakValleyBridgeFramesRemaining = 0;
            autoTrackWeakValleyBridgeActive = false;
            signalFloorEstimate = detectionLevel;
            CwToneEvent event = new CwToneEvent(
                    CwToneEvent.Type.TONE_ON,
                    toneOnTimestampMs,
                    frame.peakAmplitude(),
                    detectionLevel,
                    0L
            );
            lastEvent = event;
            totalToneOnEvents += 1;
            events.add(event);
            rememberFrameLeaderObservability(true);
            rememberToneActivityWindow();
            rememberFrame(timestampMs, detectionLevel);
            return events;
        }

        if (toneActive) {
            if (detectionLevel < releaseThreshold) {
                if (shouldBridgeAutoTrackWeakValley(toneEstimate, attackThreshold)) {
                    silenceStartedAtMs = -1L;
                    rememberFrameLeaderObservability(true);
                    rememberToneActivityWindow();
                    rememberFrame(timestampMs, detectionLevel);
                    return events;
                }
                if (silenceStartedAtMs < 0L) {
                    long frameLocalToneOffTimestampMs = estimateFrameLocalTransitionTimestamp(frame, false);
                    if (frameLocalToneOffTimestampMs >= 0L) {
                        silenceStartedAtMs = frameLocalToneOffTimestampMs;
                    } else {
                        silenceStartedAtMs = estimateCrossingTimestamp(timestampMs, releaseThreshold, detectionLevel, false);
                    }
                }
                long frameEndTimestampMs = timestampMs + estimateFrameDurationMs(frame);
                if (frameEndTimestampMs - silenceStartedAtMs >= currentToneOffHangMs()) {
                    toneActive = false;
                    long toneEndedAtMs = Math.max(toneStartedAtMs, silenceStartedAtMs);
                    long durationMs = Math.max(0L, toneEndedAtMs - toneStartedAtMs);
                    CwToneEvent event = new CwToneEvent(
                            CwToneEvent.Type.TONE_OFF,
                            toneEndedAtMs,
                            frame.peakAmplitude(),
                            detectionLevel,
                            durationMs
                    );
                    lastEvent = event;
                    totalToneOffEvents += 1;
                    events.add(event);
                    toneStartedAtMs = -1L;
                    silenceStartedAtMs = -1L;
                    autoTrackWeakValleyBridgeFramesRemaining = 0;
                    autoTrackWeakValleyBridgeActive = false;
                    noiseFloorEstimate = smoothNoiseFloor(noiseFloorEstimate, backgroundObservationLevel);
                    signalFloorEstimate = relaxSignalFloorDuringSilence(
                            signalFloorEstimate,
                            Math.max(detectionLevel, noiseFloorEstimate)
                    );
                }
            } else {
                silenceStartedAtMs = -1L;
                autoTrackWeakValleyBridgeFramesRemaining = 0;
                autoTrackWeakValleyBridgeActive = false;
            }
        }

        rememberFrameLeaderObservability(toneActive || attackQualified);
        rememberToneActivityWindow();
        rememberFrame(timestampMs, detectionLevel);
        return events;
    }

    private boolean shouldSuppressPostReleaseSteadyCarrierToneOn(
            ToneFrequencyEstimate toneEstimate,
            double frameRms,
            double previousFrameRmsAmplitude,
            double previousToneRmsAmplitude,
            long frameLocalToneOnTimestampMs
    ) {
        if (toneEstimate == null || toneEstimate.frequencyHz <= 0) {
            return false;
        }
        if (lastEvent == null || lastEvent.type() != CwToneEvent.Type.TONE_OFF) {
            return false;
        }
        if (totalToneOnEvents <= 0 || totalToneOnEvents != totalToneOffEvents) {
            return false;
        }
        if (frameLocalToneOnTimestampMs >= 0L) {
            return false;
        }
        double frameRmsGrowthRatio = frameRms / Math.max(1.0d, previousFrameRmsAmplitude);
        double toneRmsGrowthRatio = toneEstimate.toneRmsAmplitude / Math.max(1.0d, previousToneRmsAmplitude);
        return frameRmsGrowthRatio < POST_RELEASE_ONSET_GUARD_MIN_FRAME_RMS_GROWTH_RATIO
                && toneRmsGrowthRatio < POST_RELEASE_ONSET_GUARD_MIN_TONE_RMS_GROWTH_RATIO;
    }

    // Test-support path only: replay the same audio while forcing the detector
    // to look at an externally supplied tone frequency for this frame.
    public synchronized List<CwToneEvent> processForcedToneForTesting(AudioFrame frame, int forcedToneFrequencyHz) {
        ArrayList<CwToneEvent> events = new ArrayList<>(1);
        long timestampMs = frame.capturedAtMs();
        handleFrameGapReset(frame, timestampMs, events);
        double frameRms = frame.rmsAmplitude();
        double previousFrameRmsAmplitude = lastRmsAmplitude;
        double previousToneRmsAmplitude = lastToneRmsAmplitude;
        boolean hadToneHistoryBeforeFrame = totalToneOnEvents > 0 || totalToneOffEvents > 0;
        boolean hadTrackedToneMemoryBeforeFrame = hadToneHistoryBeforeFrame
                && isTrackedToneMemoryActive(timestampMs);
        int attackAnchorFrequencyHzBeforeFrame = hadToneHistoryBeforeFrame
                ? continuityAnchorFrequencyHz()
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
        int attackThreshold = currentThreshold();
        int releaseThreshold = currentReleaseThreshold();

        if (!initialized) {
            noiseFloorEstimate = backgroundObservationLevel;
            signalFloorEstimate = Math.max(backgroundObservationLevel, detectionLevel);
            attackThreshold = currentThreshold();
            releaseThreshold = currentReleaseThreshold();
            initialized = true;
        }

        boolean weakLockedResidueHoldingSignalFloor = isAutoTrackWeakValleyCandidate(toneEstimate, attackThreshold);
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

        if (toneActive || !attackQualified || detectionLevel < attackThreshold) {
            clearPendingFarAttackCandidate();
        }

        if (!toneActive && attackQualified && detectionLevel >= attackThreshold) {
            if (shouldDelayFarAttackToneOn(
                    toneEstimate,
                    hadToneHistoryBeforeFrame,
                    hadTrackedToneMemoryBeforeFrame,
                    attackAnchorFrequencyHzBeforeFrame,
                    attackReferenceToneRmsBeforeFrame
            )) {
                rememberFrame(timestampMs, detectionLevel);
                return events;
            }
            long toneOnTimestampMs = estimateCrossingTimestamp(timestampMs, attackThreshold, detectionLevel, true);
            long frameLocalToneOnTimestampMs = estimateFrameLocalTransitionTimestamp(frame, true);
            if (shouldSuppressPostReleaseSteadyCarrierToneOn(
                    toneEstimate,
                    frameRms,
                    previousFrameRmsAmplitude,
                    previousToneRmsAmplitude,
                    frameLocalToneOnTimestampMs
            )) {
                rememberFrame(timestampMs, detectionLevel);
                return events;
            }
            if (frameLocalToneOnTimestampMs >= 0L) {
                toneOnTimestampMs = frameLocalToneOnTimestampMs;
            }
            toneActive = true;
            toneStartedAtMs = toneOnTimestampMs;
            silenceStartedAtMs = -1L;
            autoTrackWeakValleyBridgeFramesRemaining = 0;
            autoTrackWeakValleyBridgeActive = false;
            signalFloorEstimate = detectionLevel;
            CwToneEvent event = new CwToneEvent(
                    CwToneEvent.Type.TONE_ON,
                    toneOnTimestampMs,
                    frame.peakAmplitude(),
                    detectionLevel,
                    0L
            );
            lastEvent = event;
            totalToneOnEvents += 1;
            events.add(event);
            rememberFrame(timestampMs, detectionLevel);
            return events;
        }

        if (toneActive) {
            if (detectionLevel < releaseThreshold) {
                if (silenceStartedAtMs < 0L) {
                    long frameLocalToneOffTimestampMs = estimateFrameLocalTransitionTimestamp(frame, false);
                    if (frameLocalToneOffTimestampMs >= 0L) {
                        silenceStartedAtMs = frameLocalToneOffTimestampMs;
                    } else {
                        silenceStartedAtMs = estimateCrossingTimestamp(timestampMs, releaseThreshold, detectionLevel, false);
                    }
                }
                long frameEndTimestampMs = timestampMs + estimateFrameDurationMs(frame);
                if (frameEndTimestampMs - silenceStartedAtMs >= currentToneOffHangMs()) {
                    toneActive = false;
                    long toneEndedAtMs = Math.max(toneStartedAtMs, silenceStartedAtMs);
                    long durationMs = Math.max(0L, toneEndedAtMs - toneStartedAtMs);
                    CwToneEvent event = new CwToneEvent(
                            CwToneEvent.Type.TONE_OFF,
                            toneEndedAtMs,
                            frame.peakAmplitude(),
                            detectionLevel,
                            durationMs
                    );
                    lastEvent = event;
                    totalToneOffEvents += 1;
                    events.add(event);
                    toneStartedAtMs = -1L;
                    silenceStartedAtMs = -1L;
                    autoTrackWeakValleyBridgeFramesRemaining = 0;
                    autoTrackWeakValleyBridgeActive = false;
                    noiseFloorEstimate = smoothNoiseFloor(noiseFloorEstimate, backgroundObservationLevel);
                    signalFloorEstimate = relaxSignalFloorDuringSilence(
                            signalFloorEstimate,
                            Math.max(detectionLevel, noiseFloorEstimate)
                    );
                }
            } else {
                silenceStartedAtMs = -1L;
                autoTrackWeakValleyBridgeFramesRemaining = 0;
                autoTrackWeakValleyBridgeActive = false;
            }
        }

        rememberFrame(timestampMs, detectionLevel);
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
        autoTrackWeakValleyBridgeActive = false;
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
        this.sqlPercent = Math.max(0, Math.min(100, sqlPercent));
    }

    public synchronized void setRxToneMode(RxToneMode mode) {
        rxToneMode = mode == null ? RxToneMode.AUTO_TRACK : mode;
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
        double noise = Math.max(0.0d, noiseFloorEstimate);
        double signalDelta = Math.max(0.0d, signalFloorEstimate - noise);
        double margin = Math.max(BASE_MARGIN, Math.max(noise * 0.18d, signalDelta * 0.30d));
        margin *= sqlMarginMultiplier();
        return Math.max(MIN_THRESHOLD, (int) Math.round(noise + margin));
    }

    private int currentReleaseThreshold() {
        double noise = Math.max(0.0d, noiseFloorEstimate);
        double attackThreshold = currentThreshold();
        double pullback = Math.max(BASE_MARGIN * 0.45d, (attackThreshold - noise) * 0.52d);
        return Math.max(MIN_THRESHOLD, (int) Math.round(noise + pullback));
    }

    private double sqlMarginMultiplier() {
        double normalized = (sqlPercent - DEFAULT_SQL_PERCENT) / 45.0d;
        return Math.max(0.40d, Math.min(1.75d, 1.0d + (normalized * 0.75d)));
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
        if (currentSignal <= safeTargetFloor) {
            return safeTargetFloor;
        }
        return currentSignal + ((safeTargetFloor - currentSignal) * SIGNAL_FLOOR_IDLE_DECAY_SMOOTHING);
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
        double lateMax = maxEnvelope(envelope, samples.length - edgeRegionLength, samples.length);
        if (earlyAverage <= threshold || lateMax >= (threshold * EDGE_TRANSITION_REQUIRED_RATIO)) {
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
        if (!hypothesisGuardAppliedThisFrame) {
            ToneFrequencyEstimate liveConsensusEstimate = maybeBuildExperimentalLiveConsensusEstimate(
                    samples,
                    frame.sampleRateHz(),
                    frame.rmsAmplitude(),
                    targetToneFrequencyHz
            );
            if (liveConsensusEstimate != null) {
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
        boolean narrowbandQualified = trackedToneRms >= MIN_TRACKED_TONE_RMS
                && dominanceRatio >= MIN_NARROWBAND_DOMINANCE_RATIO
                && (isolationRatio >= MIN_NARROWBAND_ISOLATION_RATIO
                || localContrastRatio >= MIN_NARROWBAND_LOCAL_CONTRAST_RATIO);
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
            if (shouldHoldFarIdleContinuityCandidate(searchEstimate, continuityMode)) {
                trackingState = TrackingState.CANDIDATE;
                rememberFinalAdoptedEstimate(
                        null,
                        AcquisitionWinnerSource.SEARCH_FALLBACK,
                        "continuity held the last tracked target through a short idle gap instead of jumping to a far off-target carrier"
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

        if (shouldHoldFarIdleContinuityCandidate(searchEstimate, continuityMode)) {
            trackingState = TrackingState.CANDIDATE;
            targetToneLocked = false;
            rememberFinalAdoptedEstimate(
                    null,
                    AcquisitionWinnerSource.SEARCH_FALLBACK,
                    "continuity held the last tracked target through a short idle gap instead of jumping to a far off-target carrier"
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
            boolean continuityMode
    ) {
        if (!continuityMode || toneActive || searchEstimate == null || !isAcquisitionCandidate(searchEstimate)) {
            return false;
        }
        int continuityAnchorFrequencyHz = continuityAnchorFrequencyHz();
        if (continuityAnchorFrequencyHz <= 0) {
            return false;
        }
        if (Math.abs(searchEstimate.frequencyHz - continuityAnchorFrequencyHz) < LOCKED_RETUNE_GUARD_MIN_DRIFT_HZ) {
            return false;
        }
        int searchDistanceHz = Math.abs(searchEstimate.frequencyHz - preferredToneFrequencyHz);
        int anchorDistanceHz = Math.abs(continuityAnchorFrequencyHz - preferredToneFrequencyHz);
        return searchDistanceHz > (anchorDistanceHz + 20);
    }

    private boolean shouldDelayFarContinuityCandidateAdoption(
            ToneFrequencyEstimate searchEstimate,
            boolean continuityMode
    ) {
        if (!continuityMode || searchEstimate == null || !isAcquisitionCandidate(searchEstimate)) {
            return false;
        }
        int continuityAnchorFrequencyHz = continuityAnchorFrequencyHz();
        if (continuityAnchorFrequencyHz <= 0) {
            return false;
        }
        if (Math.abs(searchEstimate.frequencyHz - continuityAnchorFrequencyHz) < LOCKED_RETUNE_GUARD_MIN_DRIFT_HZ) {
            return false;
        }
        int searchDistanceHz = Math.abs(searchEstimate.frequencyHz - preferredToneFrequencyHz);
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
            double attackReferenceToneRmsBeforeFrame
    ) {
        if (toneEstimate == null || toneEstimate.frequencyHz <= 0) {
            clearPendingFarAttackCandidate();
            return false;
        }
        if (!isFarAttackCandidate(
                toneEstimate,
                hadToneHistoryBeforeFrame,
                hadTrackedToneMemoryBeforeFrame,
                attackAnchorFrequencyHzBeforeFrame,
                attackReferenceToneRmsBeforeFrame
        )) {
            clearPendingFarAttackCandidate();
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
            return false;
        }
        return true;
    }

    private boolean isFarAttackCandidate(
            ToneFrequencyEstimate toneEstimate,
            boolean hadToneHistoryBeforeFrame,
            boolean hadTrackedToneMemoryBeforeFrame,
            int attackAnchorFrequencyHzBeforeFrame,
            double attackReferenceToneRmsBeforeFrame
    ) {
        if (toneEstimate == null || toneEstimate.frequencyHz <= 0) {
            return false;
        }
        if (hadToneHistoryBeforeFrame && hadTrackedToneMemoryBeforeFrame) {
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

    private boolean isFarFromContinuityAnchor(int candidateFrequencyHz) {
        int anchorFrequencyHz = continuityAnchorFrequencyHz();
        return anchorFrequencyHz > 0
                && Math.abs(candidateFrequencyHz - anchorFrequencyHz) >= FAR_ATTACK_CONFIRM_DRIFT_HZ;
    }

    private void clearPendingFarAttackCandidate() {
        pendingFarAttackCandidateFrequencyHz = 0;
        pendingFarAttackCandidateStableFrames = 0;
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
        int lockedDistanceHz = Math.abs(lockedWindowEstimate.frequencyHz - targetToneFrequencyHz);
        int searchDistanceHz = Math.abs(searchEstimate.frequencyHz - targetToneFrequencyHz);
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
        boolean immediateOverride = !previousTargetPlausible
                && isImmediateLockedRetuneOverride(retainedEstimate, previousTargetEstimate, driftHz);
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
        if (driftHz < LOCKED_RETUNE_GUARD_MIN_DRIFT_HZ) {
            return retainedEstimate;
        }
        boolean weakReferenceCandidate = shouldHoldWeakFarLockedRetune(retainedEstimate);
        int stableScans = noteLockedRetuneCandidate(retainedEstimate.frequencyHz);
        int requiredScans = lockedRetuneGuardRequiredScans(driftHz, weakReferenceCandidate)
                + TONE_ACTIVE_FAR_SEARCH_PREVIOUS_PLAUSIBLE_EXTRA_SCANS;
        boolean confirmed = stableScans >= requiredScans;
        boolean collapsedReferenceOverride = !isLockedRetentionQualified(previousTargetEstimate)
                && stableScans >= CANDIDATE_STABILITY_ACCEPT_SCANS
                && isImmediateLockedRetuneOverride(retainedEstimate, previousTargetEstimate, driftHz);
        boolean strongEnoughToRelease = retainedEstimate.selectionScore
                >= (previousTargetEstimate.selectionScore * TONE_ACTIVE_FAR_SEARCH_RELEASE_SCORE_RATIO)
                || retainedEstimate.toneRmsAmplitude
                >= (previousTargetEstimate.toneRmsAmplitude * TONE_ACTIVE_FAR_SEARCH_RELEASE_TONE_RATIO);
        boolean holding = (!confirmed && !collapsedReferenceOverride) || !strongEnoughToRelease;
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
                || searchDistanceHz < LOCKED_RETUNE_GUARD_MIN_DRIFT_HZ) {
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
        if (driftHz < LOCKED_RETUNE_GUARD_MIN_DRIFT_HZ) {
            return false;
        }
        if (isImmediateToneActiveFarSearchOverride(searchEstimate, previousTargetEstimate)) {
            return false;
        }
        int stableScans = noteLockedRetuneCandidate(searchEstimate.frequencyHz);
        boolean previousTargetStableAnchor = isToneActiveStableContinuityAnchor(previousTargetEstimate);
        boolean previousTargetPlausible = previousTargetStableAnchor
                || isToneActiveContinuityTargetPlausible(previousTargetEstimate);
        boolean weakReferenceCandidate = shouldHoldWeakFarLockedRetune(searchEstimate);
        int requiredScans = lockedRetuneGuardRequiredScans(driftHz, weakReferenceCandidate);
        if (previousTargetStableAnchor) {
            requiredScans += TONE_ACTIVE_FAR_SEARCH_PREVIOUS_PLAUSIBLE_EXTRA_SCANS;
        }
        boolean hold = stableScans < requiredScans;
        if (!hold && previousTargetPlausible) {
            double previousSelectionScore = Math.max(1.0d, previousTargetEstimate.selectionScore);
            hold = searchEstimate.selectionScore
                    < (previousSelectionScore * TONE_ACTIVE_FAR_SEARCH_RELEASE_SCORE_RATIO)
                    && searchEstimate.toneRmsAmplitude
                    < (previousTargetEstimate.toneRmsAmplitude * TONE_ACTIVE_FAR_SEARCH_RELEASE_TONE_RATIO);
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
        return Math.abs(retainedEstimate.frequencyHz - targetToneFrequencyHz) >= LOCKED_RETUNE_GUARD_MIN_DRIFT_HZ;
    }

    private int lockedRetuneGuardRequiredScans(int driftHz, boolean weakReferenceCandidate) {
        int requiredScans = driftHz >= LOCKED_RETUNE_GUARD_FAR_DRIFT_HZ
                ? LOCKED_RETUNE_GUARD_FAR_REQUIRED_SCANS
                : LOCKED_RETUNE_GUARD_REQUIRED_SCANS;
        if (weakReferenceCandidate) {
            requiredScans += LOCKED_RETUNE_GUARD_WEAK_REFERENCE_EXTRA_SCANS;
        }
        return requiredScans;
    }

    private boolean isImmediateLockedRetuneOverride(
            ToneFrequencyEstimate retainedEstimate,
            ToneFrequencyEstimate previousTargetEstimate,
            int driftHz
    ) {
        double scoreRatio = driftHz >= LOCKED_RETUNE_GUARD_FAR_DRIFT_HZ
                ? LOCKED_RETUNE_GUARD_FAR_STRONG_SCORE_RATIO
                : LOCKED_RETUNE_GUARD_STRONG_SCORE_RATIO;
        double toneRatio = driftHz >= LOCKED_RETUNE_GUARD_FAR_DRIFT_HZ
                ? LOCKED_RETUNE_GUARD_FAR_STRONG_TONE_RATIO
                : LOCKED_RETUNE_GUARD_STRONG_TONE_RATIO;
        return retainedEstimate.selectionScore >= (previousTargetEstimate.selectionScore * scoreRatio)
                && retainedEstimate.toneRmsAmplitude >= (previousTargetEstimate.toneRmsAmplitude * toneRatio);
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
        lockedRetuneGuardBand = driftHz >= LOCKED_RETUNE_GUARD_FAR_DRIFT_HZ ? "FAR" : "MID";
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
            return ToneScanResult.empty();
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
        ToneFrequencyEstimate preferredWindowEstimate = preferredWindowScan.winner;
        // If this gate is too strict, preferred-window bias comes back indirectly even
        // when no explicit "target = preferred" assignment exists.
        return !wasLocked
                && (preferredWindowEstimate == null
                || !isPreferredWindowAcquisitionSufficient(preferredWindowEstimate)
                || preferredWindowScan.winnerConfidence < 0.58d
                || isPreferredWindowEdgeEstimate(preferredWindowEstimate));
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
                ? FIXED_TONE_PREFERRED_SCAN_WINDOW_HZ
                : PREFERRED_TONE_SCAN_WINDOW_HZ;
    }

    private int currentUnlockedAcquisitionScanWindowHz() {
        return rxToneMode == RxToneMode.FIXED_TONE
                ? FIXED_TONE_UNLOCKED_SCAN_WINDOW_HZ
                : UNLOCKED_ACQUISITION_SCAN_WINDOW_HZ;
    }

    private int currentLockRetuneWindowHz() {
        return rxToneMode == RxToneMode.FIXED_TONE
                ? FIXED_TONE_LOCK_RETUNE_WINDOW_HZ
                : LOCK_RETUNE_WINDOW_HZ;
    }

    private long currentToneOffHangMs() {
        return rxToneMode == RxToneMode.FIXED_TONE
                ? TONE_OFF_HANG_MS
                : AUTO_TRACK_TONE_OFF_HANG_MS;
    }

    private boolean shouldBridgeAutoTrackWeakValley(
            ToneFrequencyEstimate toneEstimate,
            int attackThreshold
    ) {
        if (!isAutoTrackWeakValleyCandidate(toneEstimate, attackThreshold)) {
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

    private boolean isAutoTrackWeakValleyCandidate(
            ToneFrequencyEstimate toneEstimate,
            int attackThreshold
    ) {
        if (rxToneMode != RxToneMode.AUTO_TRACK || !toneActive || !targetToneLocked) {
            return false;
        }
        if (toneEstimate == null || toneEstimate.frequencyHz <= 0) {
            return false;
        }
        if (Math.abs(toneEstimate.frequencyHz - targetToneFrequencyHz) > TONE_SCAN_STEP_HZ) {
            return false;
        }
        return shouldSuppressWeakLockedBranchTone(toneEstimate, attackThreshold);
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
                && toneEstimate.toneRmsAmplitude >= MIN_TRACKED_TONE_RMS
                && toneEstimate.dominanceRatio >= MIN_NARROWBAND_DOMINANCE_RATIO
                && toneEstimate.isolationRatio >= MIN_NARROWBAND_ISOLATION_RATIO;
    }

    private double narrowbandConfidence(ToneFrequencyEstimate toneEstimate) {
        if (toneEstimate == null) {
            return 0.0d;
        }
        double dominanceConfidence = normalizeBetween(
                toneEstimate.dominanceRatio,
                MIN_NARROWBAND_DOMINANCE_RATIO,
                MIN_LOCK_DOMINANCE_RATIO
        );
        double isolationConfidence = normalizeBetween(
                toneEstimate.isolationRatio,
                MIN_NARROWBAND_ISOLATION_RATIO,
                MIN_LOCK_ISOLATION_RATIO
        );
        double localContrastConfidence = normalizeBetween(
                toneEstimate.localContrastRatio,
                MIN_NARROWBAND_LOCAL_CONTRAST_RATIO,
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
            representativeLockedToneFrequencyHz = currentRepresentativeLockedToneFrequencyHz;
            representativeLockedToneFrameCount = currentRepresentativeLockedToneFrameCount;
            representativeLockedToneScore = currentRepresentativeLockedToneScore;
        }
    }

    private boolean shouldPromoteRepresentativeLockedTone() {
        if (currentRepresentativeLockedToneFrameCount <= 0) {
            return false;
        }
        if (currentRepresentativeLockedToneFrameCount > representativeLockedToneFrameCount) {
            return true;
        }
        if (currentRepresentativeLockedToneFrameCount < representativeLockedToneFrameCount) {
            return false;
        }
        if (currentRepresentativeLockedToneScore > representativeLockedToneScore) {
            return true;
        }
        if (currentRepresentativeLockedToneScore < representativeLockedToneScore) {
            return false;
        }
        int currentDistanceHz = Math.abs(currentRepresentativeLockedToneFrequencyHz - preferredToneFrequencyHz);
        int representativeDistanceHz = Math.abs(representativeLockedToneFrequencyHz - preferredToneFrequencyHz);
        return currentDistanceHz < representativeDistanceHz;
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
                MIN_TRACKED_TONE_FREQUENCY_HZ,
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
