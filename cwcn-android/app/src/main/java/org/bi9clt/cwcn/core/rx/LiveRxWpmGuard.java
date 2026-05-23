package org.bi9clt.cwcn.core.rx;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;

import java.util.Locale;

public final class LiveRxWpmGuard {
    private static final double TIMING_RETARGET_MIN_WPM_DELTA = 0.75d;
    private static final long TIMING_RETARGET_MIN_DOT_DELTA_MS = 2L;
    private static final int TRUSTED_UPDATE_MIN_CONFIDENT_CHARACTERS = 2;
    private static final int INITIAL_TRUSTED_MIN_CONFIDENT_CHARACTERS = 3;
    private static final long INITIAL_TRUSTED_CANDIDATE_WINDOW_MS = 2600L;
    private static final double INITIAL_TRUSTED_BOOTSTRAP_MAX_SPREAD_WPM = 3.0d;
    private static final long TRUSTED_IDLE_FORGET_MS = 7000L;
    private static final long RETAINED_IDLE_FORGET_MS = 30000L;
    private static final long FAST_DOT_THRESHOLD_MS = 55L;
    private static final double TRUSTED_WPM_DOWNWARD_BLEND = 0.18d;
    private static final double STRONG_LOCKED_RATIO_MIN = 0.56d;
    private static final double STRONG_NEAR_TARGET_RATIO_MIN = 0.60d;
    private static final double STRONG_ACTIVE_UNLOCKED_RATIO_MAX = 0.28d;
    private static final double STRONG_TONE_DOMINANCE_MIN = 0.40d;
    private static final double STRONG_ISOLATION_MIN = 0.50d;
    private static final double TRUSTED_KEEP_UP_RATIO = 1.015d;
    private static final double TRUSTED_KEEP_UP_WPM = 0.35d;
    private static final double TRUSTED_UPDATE_DOWN_RATIO = 0.95d;
    private static final double TRUSTED_UPDATE_DOWN_WPM = 1.00d;
    private static final double HOLD_UP_RATIO = 1.05d;
    private static final double HOLD_UP_WPM = 1.20d;
    private static final double HOLD_DOWN_RATIO = 0.88d;
    private static final double HOLD_DOWN_WPM = 2.00d;
    private static final double BOOTSTRAP_BOUNDARY_SEED_FLOOR_RATIO = 0.84d;
    private static final double PRE_TRUST_WEAK_SIGNAL_SEED_FLOOR_RATIO = 0.88d;
    private static final double ACTIVE_SIGNAL_LOCKED_RATIO_MIN = 0.08d;
    private static final double ACTIVE_SIGNAL_NEAR_TARGET_RATIO_MIN = 0.12d;
    private static final double ACTIVE_SIGNAL_UNLOCKED_RATIO_MIN = 0.08d;
    private static final double TRUSTED_EVENT_LEARNING_FLOOR_RATIO = 0.94d;
    private static final double TRUSTED_EVENT_STRONG_FAST_LEARNING_FLOOR_RATIO = 0.72d;
    private static final double TRUSTED_TONE_DASH_LIKE_MIN_RATIO = 1.55d;
    private static final double RETARGET_RAW_DASH_PRESERVE_MIN_RATIO = 1.55d;
    private static final double RETARGET_RAW_LETTER_GAP_PRESERVE_MIN_RATIO = 1.65d;
    private static final double TRUSTED_TIMING_LEARNING_FAST_UP_RATIO = 1.18d;
    private static final double TRUSTED_TIMING_LEARNING_FAST_UP_WPM = 2.50d;

    private int seedWpm;
    private double retainedWpm;
    private double trustedWpm;
    private int confidentDecodedCharacterRun;
    private final double[] pendingInitialTrustedCandidates = new double[INITIAL_TRUSTED_MIN_CONFIDENT_CHARACTERS];
    private int pendingInitialTrustedCandidateCount;
    private long lastPendingInitialTrustedCandidateElapsedMs = -1L;
    private long lastRetainedUpdateElapsedMs = -1L;
    private long lastTrustedUpdateElapsedMs = -1L;
    private boolean holding;

    public void reset() {
        retainedWpm = 0.0d;
        trustedWpm = 0.0d;
        confidentDecodedCharacterRun = 0;
        clearPendingInitialTrustedCandidates();
        lastRetainedUpdateElapsedMs = -1L;
        lastTrustedUpdateElapsedMs = -1L;
        holding = false;
    }

    public void softReset() {
        if (trustedWpm > 0.0d) {
            rememberRetainedWpm(trustedWpm, lastTrustedUpdateElapsedMs);
        }
        confidentDecodedCharacterRun = 0;
        clearPendingInitialTrustedCandidates();
        holding = false;
    }

    public void beginNewTurn(int turnSeedWpm, long nowElapsedMs) {
        seedWpm = Math.max(0, turnSeedWpm);
        retainedWpm = 0.0d;
        trustedWpm = 0.0d;
        confidentDecodedCharacterRun = 0;
        clearPendingInitialTrustedCandidates();
        lastRetainedUpdateElapsedMs = -1L;
        lastTrustedUpdateElapsedMs = -1L;
        holding = false;
    }

    public void setSeedWpm(int seedWpm) {
        this.seedWpm = Math.max(0, seedWpm);
    }

    public void noteDecodedCharacter(
            boolean unknownCharacter,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            long nowElapsedMs
    ) {
        refreshAnchorState(signalSnapshot, nowElapsedMs);
        maybeExpirePendingInitialTrustedCandidates(nowElapsedMs);
        if (unknownCharacter && trustedWpm > 0.0d) {
            confidentDecodedCharacterRun = 0;
            return;
        }
        if (unknownCharacter) {
            confidentDecodedCharacterRun = 0;
        }
        Sample sample = Sample.from(signalSnapshot, timingSnapshot);
        boolean strongConfidence = isStrongConfidence(sample);
        boolean bootstrapConfidence = isBootstrapConfidence(sample);
        if (!strongConfidence) {
            confidentDecodedCharacterRun = 0;
        }
        double candidateWpm = trustedWpm <= 0.0d
                ? sample.rawWpm
                : sample.rawWpm > 0.0d
                ? sample.rawWpm
                : fallbackWpm();
        if (candidateWpm <= 0.0d) {
            return;
        }
        if (trustedWpm <= 0.0d) {
            if (!bootstrapConfidence) {
                return;
            }
            appendPendingInitialTrustedCandidate(candidateWpm, nowElapsedMs);
            if (pendingInitialTrustedCandidateCount < INITIAL_TRUSTED_MIN_CONFIDENT_CHARACTERS
                    || !pendingInitialTrustedCandidatesClustered()) {
                return;
            }
            trustedWpm = medianPendingInitialTrustedCandidate();
            rememberRetainedWpm(trustedWpm, nowElapsedMs);
            clearPendingInitialTrustedCandidates();
        } else {
            if (!strongConfidence) {
                return;
            }
            confidentDecodedCharacterRun += 1;
            if (confidentDecodedCharacterRun < TRUSTED_UPDATE_MIN_CONFIDENT_CHARACTERS) {
                return;
            }
            double updateLowerBound = trustedUpdateLowerBound();
            double keepUpperBound = trustedKeepUpperBound();
            if (candidateWpm < updateLowerBound) {
                trustedWpm = (trustedWpm * (1.0d - TRUSTED_WPM_DOWNWARD_BLEND))
                        + (candidateWpm * TRUSTED_WPM_DOWNWARD_BLEND);
                rememberRetainedWpm(trustedWpm, nowElapsedMs);
            } else if (candidateWpm > keepUpperBound) {
                holding = candidateWpm > holdUpperBound();
                lastTrustedUpdateElapsedMs = nowElapsedMs;
                return;
            }
        }
        lastTrustedUpdateElapsedMs = nowElapsedMs;
        rememberRetainedWpm(trustedWpm, nowElapsedMs);
        holding = false;
    }

    public CwTimingEvent adaptTimingEvent(
            @Nullable CwTimingEvent rawTimingEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            long nowElapsedMs
    ) {
        if (rawTimingEvent == null) {
            return null;
        }
        if (trustedWpm <= 0.0d) {
            return rawTimingEvent;
        }
        double effectiveWpm = resolveEffectiveWpm(signalSnapshot, timingSnapshot, nowElapsedMs);
        if (effectiveWpm <= 0.0d) {
            return rawTimingEvent;
        }
        long heldDotEstimateMs = wpmToDotEstimateMs(effectiveWpm);
        long heldIntraGapEstimateMs = heldDotEstimateMs;
        if (!shouldRetargetTimingEvent(rawTimingEvent, timingSnapshot, effectiveWpm, heldDotEstimateMs)) {
            return rawTimingEvent;
        }
        CwTimingEvent.Classification heldClassification = rawTimingEvent.kind() == CwTimingEvent.Kind.TONE
                ? classifyTone(rawTimingEvent.durationMs(), heldDotEstimateMs)
                : classifyGap(rawTimingEvent.durationMs(), heldDotEstimateMs, heldIntraGapEstimateMs);
        if (shouldPreserveRawDashClassification(rawTimingEvent, heldClassification, heldDotEstimateMs)) {
            heldClassification = CwTimingEvent.Classification.DAH;
        } else if (shouldPreserveRawLetterGapClassification(rawTimingEvent, heldClassification, heldDotEstimateMs)) {
            heldClassification = CwTimingEvent.Classification.LETTER_GAP;
        }
        return new CwTimingEvent(
                rawTimingEvent.kind(),
                heldClassification,
                rawTimingEvent.timestampMs(),
                rawTimingEvent.durationMs(),
                heldDotEstimateMs,
                heldIntraGapEstimateMs
        );
    }

    public int resolveDisplayWpm(
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            long nowElapsedMs
    ) {
        double effectiveWpm = resolveEffectiveWpm(signalSnapshot, timingSnapshot, nowElapsedMs);
        if (effectiveWpm > 0.0d) {
            return (int) Math.round(effectiveWpm);
        }
        if (timingSnapshot != null && timingSnapshot.estimatedWpm() > 0) {
            return timingSnapshot.estimatedWpm();
        }
        return fallbackDisplayWpm();
    }

    public long resolveEffectiveDotEstimateMs(
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            long nowElapsedMs
    ) {
        double effectiveWpm = resolveEffectiveWpm(signalSnapshot, timingSnapshot, nowElapsedMs);
        if (effectiveWpm > 0.0d) {
            return wpmToDotEstimateMs(effectiveWpm);
        }
        if (timingSnapshot != null && timingSnapshot.dotEstimateMs() > 0L) {
            return timingSnapshot.dotEstimateMs();
        }
        return wpmToDotEstimateMs(Math.max(1, fallbackDisplayWpm()));
    }

    public int resolveReferenceWpm(@Nullable CwTimingSnapshot timingSnapshot) {
        double anchorWpm = anchorWpm();
        if (anchorWpm > 0.0d) {
            return (int) Math.round(anchorWpm);
        }
        if (timingSnapshot != null && timingSnapshot.estimatedWpm() > 0) {
            return timingSnapshot.estimatedWpm();
        }
        return 0;
    }

    public long resolveReferenceDotEstimateMs(@Nullable CwTimingSnapshot timingSnapshot) {
        double anchorWpm = anchorWpm();
        if (anchorWpm > 0.0d) {
            return wpmToDotEstimateMs(anchorWpm);
        }
        if (timingSnapshot != null && timingSnapshot.dotEstimateMs() > 0L) {
            return timingSnapshot.dotEstimateMs();
        }
        return 0L;
    }

    public boolean holding() {
        return holding;
    }

    public String compactDebugSummary(
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            long nowElapsedMs
    ) {
        double rawWpm = timingSnapshot == null
                ? 0.0d
                : timingSnapshot.estimatedWpmPrecise() > 0.0d
                ? timingSnapshot.estimatedWpmPrecise()
                : timingSnapshot.estimatedWpm();
        int displayWpm = resolveDisplayWpm(signalSnapshot, timingSnapshot, nowElapsedMs);
        return "wpm sd" + positiveOrDash(seedWpm)
                + " tr" + formatWpmOrDash(trustedWpm)
                + " p" + pendingInitialTrustedCandidateCount
                + " raw" + formatWpmOrDash(rawWpm)
                + " dsp" + positiveOrDash(displayWpm)
                + " h" + yesNoShort(holding);
    }

    public boolean shouldAcceptTimingAnchorUpdate(
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            long nowElapsedMs
    ) {
        refreshAnchorState(signalSnapshot, nowElapsedMs);
        Sample sample = Sample.from(signalSnapshot, timingSnapshot);
        if (sample.rawWpm <= 0.0d) {
            return false;
        }
        if (trustedWpm > 0.0d) {
            if (!isStrongConfidence(sample)) {
                return false;
            }
            return sample.rawWpm >= holdLowerBound()
                    && sample.rawWpm <= trustedKeepUpperBound();
        }
        if (!isBootstrapConfidence(sample)) {
            return false;
        }
        return true;
    }

    public boolean shouldAcceptBootstrapBoundaryAnchorUpdate(
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            long nowElapsedMs
    ) {
        refreshAnchorState(signalSnapshot, nowElapsedMs);
        Sample sample = Sample.from(signalSnapshot, timingSnapshot);
        if (sample.rawWpm <= 0.0d) {
            return false;
        }
        if (trustedWpm > 0.0d) {
            return shouldAcceptTimingAnchorUpdate(signalSnapshot, timingSnapshot, nowElapsedMs);
        }
        if (!signalLooksActive(signalSnapshot)) {
            return false;
        }
        if (seedWpm <= 0 || sample.rawWpm <= 0.0d) {
            return true;
        }
        return sample.rawWpm >= (seedWpm * BOOTSTRAP_BOUNDARY_SEED_FLOOR_RATIO);
    }

    public boolean shouldAllowTimingLearning(
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            long nowElapsedMs
    ) {
        refreshAnchorState(signalSnapshot, nowElapsedMs);
        if (!signalLooksActive(signalSnapshot)) {
            return false;
        }
        Sample sample = Sample.from(signalSnapshot, timingSnapshot);
        if (trustedWpm <= 0.0d) {
            if (sample.rawWpm <= 0.0d) {
                return false;
            }
            if (seedWpm <= 0 || isBootstrapConfidence(sample)) {
                return true;
            }
            return sample.rawWpm >= (seedWpm * PRE_TRUST_WEAK_SIGNAL_SEED_FLOOR_RATIO);
        }
        if (!isStrongConfidence(sample) || sample.rawWpm <= 0.0d) {
            return false;
        }
        return sample.rawWpm <= trustedTimingLearningUpperBound();
    }

    public boolean shouldAllowTimingLearningForEvent(
            @Nullable CwToneEvent toneEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            long nowElapsedMs
    ) {
        boolean baseAllow = shouldAllowTimingLearning(signalSnapshot, timingSnapshot, nowElapsedMs);
        if (!baseAllow && !shouldAllowActiveEventTimingLearning(toneEvent, signalSnapshot, timingSnapshot)) {
            return false;
        }
        if (trustedWpm <= 0.0d || toneEvent == null || timingSnapshot == null) {
            return true;
        }
        long anchorDotEstimateMs = resolveReferenceDotEstimateMs(timingSnapshot);
        if (anchorDotEstimateMs <= 0L) {
            return true;
        }
        long candidateDotEstimateMs = inferLearningDotCandidateMs(
                toneEvent,
                timingSnapshot,
                anchorDotEstimateMs
        );
        if (candidateDotEstimateMs <= 0L) {
            return true;
        }
        long minimumTrustedCandidateMs = Math.max(
                1L,
                Math.round(anchorDotEstimateMs * trustedEventLearningFloorRatio(signalSnapshot, timingSnapshot))
        );
        return candidateDotEstimateMs >= minimumTrustedCandidateMs;
    }

    private double resolveEffectiveWpm(
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            long nowElapsedMs
    ) {
        refreshAnchorState(signalSnapshot, nowElapsedMs);
        Sample sample = Sample.from(signalSnapshot, timingSnapshot);
        double rawWpm = sample.rawWpm;
        double anchorWpm = trustedWpm;
        boolean strongConfidence = isStrongConfidence(sample);
        if (anchorWpm <= 0.0d) {
            holding = false;
            if (rawWpm > 0.0d) {
                if (!strongConfidence && seedWpm > 0) {
                    rawWpm = Math.max(rawWpm, seedWpm * PRE_TRUST_WEAK_SIGNAL_SEED_FLOOR_RATIO);
                }
                return rawWpm;
            }
            if (retainedWpm > 0.0d) {
                return retainedWpm;
            }
            return Math.max(0, seedWpm);
        }

        if (anchorWpm > 0.0d && rawWpm > 0.0d) {
            double lowerBound = holdLowerBound();
            double upperBound = holdUpperBound();
            if (rawWpm < lowerBound || rawWpm > upperBound) {
                holding = true;
                return anchorWpm;
            }
        }

        if (holding) {
            if (anchorWpm <= 0.0d) {
                holding = false;
            } else if (strongConfidence && rawWpm > 0.0d) {
                double lowerBound = holdLowerBound();
                double releaseUpperBound = holdUpperBound();
                if (rawWpm >= lowerBound && rawWpm <= releaseUpperBound) {
                    holding = false;
                    return anchorWpm;
                }
                return anchorWpm;
            } else {
                return anchorWpm;
            }
        }

        if (anchorWpm > 0.0d && rawWpm > holdUpperBound()) {
            holding = true;
            return anchorWpm;
        }
        if (anchorWpm > 0.0d && rawWpm > trustedKeepUpperBound()) {
            return anchorWpm;
        }
        if (rawWpm > 0.0d) {
            return rawWpm;
        }
        if (anchorWpm > 0.0d) {
            return anchorWpm;
        }
        if (retainedWpm > 0.0d) {
            return retainedWpm;
        }
        return Math.max(0, seedWpm);
    }

    private int fallbackDisplayWpm() {
        if (trustedWpm > 0.0d) {
            return (int) Math.round(trustedWpm);
        }
        if (retainedWpm > 0.0d) {
            return (int) Math.round(retainedWpm);
        }
        return Math.max(0, seedWpm);
    }

    private double fallbackWpm() {
        return anchorWpm();
    }

    private void refreshAnchorState(
            @Nullable CwSignalSnapshot signalSnapshot,
            long nowElapsedMs
    ) {
        maybeForgetTrustedWpm(signalSnapshot, nowElapsedMs);
        maybeForgetRetainedWpm(signalSnapshot, nowElapsedMs);
    }

    private void maybeForgetTrustedWpm(
            @Nullable CwSignalSnapshot signalSnapshot,
            long nowElapsedMs
    ) {
        if (trustedWpm <= 0.0d || lastTrustedUpdateElapsedMs <= 0L) {
            return;
        }
        if ((nowElapsedMs - lastTrustedUpdateElapsedMs) < TRUSTED_IDLE_FORGET_MS) {
            return;
        }
        if (signalLooksActive(signalSnapshot)) {
            return;
        }
        rememberRetainedWpm(trustedWpm, nowElapsedMs);
        trustedWpm = 0.0d;
        confidentDecodedCharacterRun = 0;
        clearPendingInitialTrustedCandidates();
        lastTrustedUpdateElapsedMs = -1L;
        holding = false;
    }

    private void maybeForgetRetainedWpm(
            @Nullable CwSignalSnapshot signalSnapshot,
            long nowElapsedMs
    ) {
        if (retainedWpm <= 0.0d || lastRetainedUpdateElapsedMs <= 0L) {
            return;
        }
        if ((nowElapsedMs - lastRetainedUpdateElapsedMs) < RETAINED_IDLE_FORGET_MS) {
            return;
        }
        if (signalLooksActive(signalSnapshot)) {
            return;
        }
        retainedWpm = 0.0d;
        lastRetainedUpdateElapsedMs = -1L;
    }

    private void maybeExpirePendingInitialTrustedCandidates(long nowElapsedMs) {
        if (pendingInitialTrustedCandidateCount <= 0 || lastPendingInitialTrustedCandidateElapsedMs <= 0L) {
            return;
        }
        if ((nowElapsedMs - lastPendingInitialTrustedCandidateElapsedMs) < INITIAL_TRUSTED_CANDIDATE_WINDOW_MS) {
            return;
        }
        clearPendingInitialTrustedCandidates();
    }

    private double anchorWpm() {
        if (trustedWpm > 0.0d) {
            return trustedWpm;
        }
        if (retainedWpm > 0.0d) {
            return retainedWpm;
        }
        return Math.max(0, seedWpm);
    }

    private boolean isStrongConfidence(Sample sample) {
        return sample.targetToneLocked
                && sample.recentLockedFrameRatio >= STRONG_LOCKED_RATIO_MIN
                && sample.recentNearTargetLockedFrameRatio >= STRONG_NEAR_TARGET_RATIO_MIN
                && sample.recentActiveUnlockedFrameRatio <= STRONG_ACTIVE_UNLOCKED_RATIO_MAX
                && sample.toneDominanceRatio >= STRONG_TONE_DOMINANCE_MIN
                && sample.narrowbandIsolationRatio >= STRONG_ISOLATION_MIN;
    }

    private boolean isBootstrapConfidence(Sample sample) {
        return RxStableDecodeDecider.passesBootstrapDecodeShape(
                sample.targetToneLocked,
                sample.recentLockedFrameRatio,
                sample.recentNearTargetLockedFrameRatio,
                sample.recentActiveUnlockedFrameRatio,
                sample.toneDominanceRatio,
                sample.narrowbandIsolationRatio
        );
    }

    private boolean shouldRetargetTimingEvent(
            CwTimingEvent rawTimingEvent,
            @Nullable CwTimingSnapshot timingSnapshot,
            double effectiveWpm,
            long effectiveDotEstimateMs
    ) {
        if (holding) {
            return true;
        }
        long rawDotEstimateMs = rawTimingEvent.dotEstimateMs() > 0L
                ? rawTimingEvent.dotEstimateMs()
                : timingSnapshot == null
                ? 0L
                : timingSnapshot.dotEstimateMs();
        if (rawDotEstimateMs > 0L
                && Math.abs(rawDotEstimateMs - effectiveDotEstimateMs) >= TIMING_RETARGET_MIN_DOT_DELTA_MS) {
            return true;
        }
        double rawWpm = timingSnapshot != null && timingSnapshot.estimatedWpmPrecise() > 0.0d
                ? timingSnapshot.estimatedWpmPrecise()
                : rawDotEstimateMs > 0L
                ? 1200.0d / rawDotEstimateMs
                : 0.0d;
        return rawWpm > 0.0d
                && Math.abs(rawWpm - effectiveWpm) >= TIMING_RETARGET_MIN_WPM_DELTA;
    }

    private double trustedKeepUpperBound() {
        return Math.min(
                trustedWpm + TRUSTED_KEEP_UP_WPM,
                trustedWpm * TRUSTED_KEEP_UP_RATIO
        );
    }

    private double trustedUpdateLowerBound() {
        return Math.max(
                trustedWpm - TRUSTED_UPDATE_DOWN_WPM,
                trustedWpm * TRUSTED_UPDATE_DOWN_RATIO
        );
    }

    private double holdUpperBound() {
        return Math.min(
                trustedWpm + HOLD_UP_WPM,
                trustedWpm * HOLD_UP_RATIO
        );
    }

    private double holdLowerBound() {
        return Math.max(
                trustedWpm - HOLD_DOWN_WPM,
                trustedWpm * HOLD_DOWN_RATIO
        );
    }

    private double trustedTimingLearningUpperBound() {
        return Math.min(
                trustedWpm + TRUSTED_TIMING_LEARNING_FAST_UP_WPM,
                trustedWpm * TRUSTED_TIMING_LEARNING_FAST_UP_RATIO
        );
    }

    private boolean shouldAllowActiveEventTimingLearning(
            @Nullable CwToneEvent toneEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot
    ) {
        if (trustedWpm <= 0.0d
                || toneEvent == null
                || timingSnapshot == null) {
            return false;
        }
        if (!signalLooksActive(signalSnapshot)) {
            return false;
        }
        Sample sample = Sample.from(signalSnapshot, timingSnapshot);
        return sample.rawWpm > 0.0d && sample.rawWpm <= trustedTimingLearningUpperBound();
    }

    private boolean signalLooksActive(@Nullable CwSignalSnapshot signalSnapshot) {
        if (signalSnapshot == null) {
            return false;
        }
        if (signalSnapshot.toneActive()) {
            return true;
        }
        return signalSnapshot.recentLockedFrameRatio() >= ACTIVE_SIGNAL_LOCKED_RATIO_MIN
                || signalSnapshot.recentNearTargetLockedFrameRatio() >= ACTIVE_SIGNAL_NEAR_TARGET_RATIO_MIN
                || signalSnapshot.recentActiveUnlockedFrameRatio() >= ACTIVE_SIGNAL_UNLOCKED_RATIO_MIN;
    }

    private void rememberRetainedWpm(double candidateWpm, long nowElapsedMs) {
        if (candidateWpm <= 0.0d) {
            return;
        }
        retainedWpm = candidateWpm;
        if (nowElapsedMs > 0L) {
            lastRetainedUpdateElapsedMs = nowElapsedMs;
        }
    }

    private void appendPendingInitialTrustedCandidate(double candidateWpm, long nowElapsedMs) {
        if (candidateWpm <= 0.0d) {
            return;
        }
        if (pendingInitialTrustedCandidateCount < pendingInitialTrustedCandidates.length) {
            pendingInitialTrustedCandidates[pendingInitialTrustedCandidateCount] = candidateWpm;
            pendingInitialTrustedCandidateCount += 1;
        } else {
            pendingInitialTrustedCandidates[0] = pendingInitialTrustedCandidates[1];
            pendingInitialTrustedCandidates[1] = pendingInitialTrustedCandidates[2];
            pendingInitialTrustedCandidates[2] = candidateWpm;
        }
        lastPendingInitialTrustedCandidateElapsedMs = nowElapsedMs;
    }

    private boolean pendingInitialTrustedCandidatesClustered() {
        if (pendingInitialTrustedCandidateCount < INITIAL_TRUSTED_MIN_CONFIDENT_CHARACTERS) {
            return false;
        }
        double min = pendingInitialTrustedCandidates[0];
        double max = pendingInitialTrustedCandidates[0];
        for (int index = 1; index < pendingInitialTrustedCandidateCount; index++) {
            min = Math.min(min, pendingInitialTrustedCandidates[index]);
            max = Math.max(max, pendingInitialTrustedCandidates[index]);
        }
        return (max - min) <= INITIAL_TRUSTED_BOOTSTRAP_MAX_SPREAD_WPM;
    }

    private double medianPendingInitialTrustedCandidate() {
        if (pendingInitialTrustedCandidateCount <= 0) {
            return 0.0d;
        }
        if (pendingInitialTrustedCandidateCount == 1) {
            return pendingInitialTrustedCandidates[0];
        }
        if (pendingInitialTrustedCandidateCount == 2) {
            return (pendingInitialTrustedCandidates[0] + pendingInitialTrustedCandidates[1]) / 2.0d;
        }
        double first = pendingInitialTrustedCandidates[0];
        double second = pendingInitialTrustedCandidates[1];
        double third = pendingInitialTrustedCandidates[2];
        if ((first <= second && second <= third) || (third <= second && second <= first)) {
            return second;
        }
        if ((second <= first && first <= third) || (third <= first && first <= second)) {
            return first;
        }
        return third;
    }

    private void clearPendingInitialTrustedCandidates() {
        pendingInitialTrustedCandidateCount = 0;
        pendingInitialTrustedCandidates[0] = 0.0d;
        pendingInitialTrustedCandidates[1] = 0.0d;
        pendingInitialTrustedCandidates[2] = 0.0d;
        lastPendingInitialTrustedCandidateElapsedMs = -1L;
    }

    private long wpmToDotEstimateMs(double wpm) {
        double safeWpm = Math.max(1.0d, wpm);
        return Math.max(1L, Math.round(1200.0d / safeWpm));
    }

    private String positiveOrDash(int value) {
        return value > 0 ? String.valueOf(value) : "-";
    }

    private String formatWpmOrDash(double value) {
        return value > 0.0d ? String.format(Locale.US, "%.1f", value) : "-";
    }

    private String yesNoShort(boolean value) {
        return value ? "Y" : "N";
    }

    private long inferLearningDotCandidateMs(
            CwToneEvent toneEvent,
            CwTimingSnapshot timingSnapshot,
            long anchorDotEstimateMs
    ) {
        if (toneEvent.type() == CwToneEvent.Type.TONE_OFF) {
            long toneDurationMs = Math.max(0L, toneEvent.toneDurationMs());
            if (toneDurationMs <= 0L) {
                return 0L;
            }
            double toneRatio = toneDurationMs / (double) Math.max(1L, anchorDotEstimateMs);
            if (toneRatio >= TRUSTED_TONE_DASH_LIKE_MIN_RATIO && toneRatio <= 5.2d) {
                return Math.max(1L, Math.round(toneDurationMs / 3.0d));
            }
            return toneDurationMs;
        }

        CwTimingEvent lastTimingEvent = timingSnapshot.lastTimingEvent();
        if (lastTimingEvent == null || lastTimingEvent.kind() != CwTimingEvent.Kind.TONE) {
            return 0L;
        }
        long gapDurationMs = Math.max(0L, toneEvent.timestampMs() - lastTimingEvent.timestampMs());
        if (gapDurationMs <= 0L) {
            return 0L;
        }
        double gapRatio = gapDurationMs / (double) Math.max(1L, anchorDotEstimateMs);
        if (gapRatio <= 1.95d) {
            return gapDurationMs;
        }
        if (gapRatio <= 5.2d) {
            return Math.max(1L, Math.round(gapDurationMs / 3.0d));
        }
        if (gapRatio <= 10.8d) {
            return Math.max(1L, Math.round(gapDurationMs / 7.0d));
        }
        return 0L;
    }

    private double trustedEventLearningFloorRatio(
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot
    ) {
        Sample sample = Sample.from(signalSnapshot, timingSnapshot);
        if (trustedWpm > 0.0d && isStrongConfidence(sample)) {
            return TRUSTED_EVENT_STRONG_FAST_LEARNING_FLOOR_RATIO;
        }
        return TRUSTED_EVENT_LEARNING_FLOOR_RATIO;
    }

    private CwTimingEvent.Classification classifyTone(long toneDurationMs, long dotEstimateMs) {
        double ratio = toneDurationMs / (double) Math.max(1L, dotEstimateMs);
        if (ratio <= 1.8d) {
            return CwTimingEvent.Classification.DIT;
        }
        if (ratio <= 4.8d) {
            return CwTimingEvent.Classification.DAH;
        }
        return CwTimingEvent.Classification.UNKNOWN;
    }

    private CwTimingEvent.Classification classifyGap(
            long gapDurationMs,
            long dotEstimateMs,
            long intraGapEstimateMs
    ) {
        double ratio = gapDurationMs / (double) Math.max(1L, dotEstimateMs);
        double intraRatio = gapDurationMs / (double) Math.max(1L, intraGapEstimateMs);
        boolean fastTimingContext = dotEstimateMs <= FAST_DOT_THRESHOLD_MS
                || intraGapEstimateMs <= FAST_DOT_THRESHOLD_MS;
        double intraGapMaxRatio = fastTimingContext ? 1.55d : 1.8d;
        double letterGapMaxRatio = fastTimingContext ? 3.95d : 4.35d;
        double wordGapMaxRatio = fastTimingContext ? 9.2d : 10.0d;
        if (ratio <= intraGapMaxRatio) {
            return CwTimingEvent.Classification.INTRA_SYMBOL_GAP;
        }
        if (ratio >= 3.15d && intraRatio >= 5.0d) {
            return CwTimingEvent.Classification.WORD_GAP;
        }
        if (ratio <= letterGapMaxRatio) {
            return CwTimingEvent.Classification.LETTER_GAP;
        }
        if (ratio <= wordGapMaxRatio) {
            return CwTimingEvent.Classification.WORD_GAP;
        }
        return CwTimingEvent.Classification.UNKNOWN;
    }

    private boolean shouldPreserveRawDashClassification(
            CwTimingEvent rawTimingEvent,
            CwTimingEvent.Classification heldClassification,
            long heldDotEstimateMs
    ) {
        if (rawTimingEvent == null
                || rawTimingEvent.kind() != CwTimingEvent.Kind.TONE
                || rawTimingEvent.classification() != CwTimingEvent.Classification.DAH
                || heldClassification != CwTimingEvent.Classification.DIT) {
            return false;
        }
        long rawDotEstimateMs = Math.max(1L, rawTimingEvent.dotEstimateMs());
        if (heldDotEstimateMs <= rawDotEstimateMs) {
            return false;
        }
        double ratioToHeldDot = rawTimingEvent.durationMs() / (double) Math.max(1L, heldDotEstimateMs);
        return ratioToHeldDot >= RETARGET_RAW_DASH_PRESERVE_MIN_RATIO;
    }

    private boolean shouldPreserveRawLetterGapClassification(
            CwTimingEvent rawTimingEvent,
            CwTimingEvent.Classification heldClassification,
            long heldDotEstimateMs
    ) {
        if (rawTimingEvent == null
                || rawTimingEvent.kind() != CwTimingEvent.Kind.GAP
                || rawTimingEvent.classification() != CwTimingEvent.Classification.LETTER_GAP
                || heldClassification != CwTimingEvent.Classification.INTRA_SYMBOL_GAP) {
            return false;
        }
        long rawDotEstimateMs = Math.max(1L, rawTimingEvent.dotEstimateMs());
        if (heldDotEstimateMs <= rawDotEstimateMs) {
            return false;
        }
        double ratioToHeldDot = rawTimingEvent.durationMs() / (double) Math.max(1L, heldDotEstimateMs);
        return ratioToHeldDot >= RETARGET_RAW_LETTER_GAP_PRESERVE_MIN_RATIO;
    }

    private static final class Sample {
        private final double rawWpm;
        private final boolean targetToneLocked;
        private final double recentLockedFrameRatio;
        private final double recentNearTargetLockedFrameRatio;
        private final double recentActiveUnlockedFrameRatio;
        private final double toneDominanceRatio;
        private final double narrowbandIsolationRatio;

        private Sample(
                double rawWpm,
                boolean targetToneLocked,
                double recentLockedFrameRatio,
                double recentNearTargetLockedFrameRatio,
                double recentActiveUnlockedFrameRatio,
                double toneDominanceRatio,
                double narrowbandIsolationRatio
        ) {
            this.rawWpm = rawWpm;
            this.targetToneLocked = targetToneLocked;
            this.recentLockedFrameRatio = recentLockedFrameRatio;
            this.recentNearTargetLockedFrameRatio = recentNearTargetLockedFrameRatio;
            this.recentActiveUnlockedFrameRatio = recentActiveUnlockedFrameRatio;
            this.toneDominanceRatio = toneDominanceRatio;
            this.narrowbandIsolationRatio = narrowbandIsolationRatio;
        }

        private static Sample from(
                @Nullable CwSignalSnapshot signalSnapshot,
                @Nullable CwTimingSnapshot timingSnapshot
        ) {
            return new Sample(
                    timingSnapshot == null
                            ? 0.0d
                            : timingSnapshot.estimatedWpmPrecise() > 0.0d
                            ? timingSnapshot.estimatedWpmPrecise()
                            : timingSnapshot.estimatedWpm(),
                    signalSnapshot != null && signalSnapshot.targetToneLocked(),
                    signalSnapshot == null ? 0.0d : signalSnapshot.recentLockedFrameRatio(),
                    signalSnapshot == null ? 0.0d : signalSnapshot.recentNearTargetLockedFrameRatio(),
                    signalSnapshot == null ? 0.0d : signalSnapshot.recentActiveUnlockedFrameRatio(),
                    signalSnapshot == null ? 0.0d : signalSnapshot.toneDominanceRatio(),
                    signalSnapshot == null ? 0.0d : signalSnapshot.narrowbandIsolationRatio()
            );
        }
    }
}
