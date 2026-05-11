package org.bi9clt.cwcn.core.timing;

import org.bi9clt.cwcn.core.signal.CwToneEvent;

import java.util.List;

public final class CwHybridTimingModel {
    private static final int ADAPTIVE_PROMOTION_MIN_WPM = 19;
    private static final int ADAPTIVE_PROMOTION_WPM_LEAD = 7;
    private static final int ADAPTIVE_PROMOTION_BASELINE_MAX_WPM = 20;
    private static final int BASELINE_RESTORE_MARGIN_WPM = 2;
    private static final long TRUSTED_IDLE_RELEASE_MS = 4200L;
    private static final double TRUSTED_FAST_FLOOR_RATIO = 0.92d;
    private static final double TRUSTED_FAST_DIRECT_UPDATE_RATIO = 0.97d;
    private static final double TRUSTED_FAST_REANCHOR_RATIO = 0.92d;
    private static final double TRUSTED_FAST_DIRECT_UPDATE_BLEND = 0.08d;
    private static final double TRUSTED_FAST_REANCHOR_BLEND = 0.10d;
    private static final double TRUSTED_SLOW_UPDATE_BLEND = 0.18d;
    private static final int TRUSTED_FAST_REANCHOR_REQUIRED_STABLE_DECODES = 6;
    private static final double TRUSTED_FAST_REANCHOR_MATCH_RATIO = 0.05d;
    private static final long FAST_DOT_THRESHOLD_MS = 55L;

    private enum Strategy {
        BASELINE,
        ADAPTIVE
    }

    private final CwTimingModel baseline = new CwTimingModel();
    private final CwAdaptiveTimingModel adaptive = new CwAdaptiveTimingModel();
    private Strategy activeStrategy = Strategy.BASELINE;
    private Strategy lastEmissionStrategy = Strategy.BASELINE;
    private int adaptivePromotionCount;
    private int adaptiveEmissionBatches;
    private int seedWpm;
    private double trustedDotEstimateMs;
    private long lastStableDecodeTimestampMs = -1L;
    private long lastObservationTimestampMs = -1L;
    private long lastTimingActivityTimestampMs = -1L;
    private double pendingFastTrustedDotEstimateMs;
    private int pendingFastTrustedEvidenceCount;

    public synchronized List<CwTimingEvent> process(CwToneEvent toneEvent) {
        return process(toneEvent, true);
    }

    public synchronized List<CwTimingEvent> process(CwToneEvent toneEvent, boolean allowLearning) {
        lastObservationTimestampMs = Math.max(lastObservationTimestampMs, toneEvent.timestampMs());
        maybeResetAfterExtendedIdle(toneEvent.timestampMs());
        List<CwTimingEvent> baselineEvents = baseline.process(toneEvent, allowLearning);
        List<CwTimingEvent> adaptiveEvents = adaptive.process(toneEvent, allowLearning);
        refreshStrategy();
        List<CwTimingEvent> outputEvents = selectOutputEvents(baselineEvents, adaptiveEvents);
        noteTimingActivity(outputEvents);
        return stabilizeOutputEvents(outputEvents, emissionStrategySnapshot(), toneEvent.timestampMs());
    }

    public synchronized List<CwTimingEvent> flushPendingGap(long timestampMs) {
        return flushPendingGap(timestampMs, true);
    }

    public synchronized List<CwTimingEvent> flushPendingGap(long timestampMs, boolean allowLearning) {
        lastObservationTimestampMs = Math.max(lastObservationTimestampMs, timestampMs);
        maybeResetAfterExtendedIdle(timestampMs);
        List<CwTimingEvent> baselineEvents = baseline.flushPendingGap(timestampMs, allowLearning);
        List<CwTimingEvent> adaptiveEvents = adaptive.flushPendingGap(timestampMs, allowLearning);
        refreshStrategy();
        List<CwTimingEvent> outputEvents = selectOutputEvents(baselineEvents, adaptiveEvents);
        noteTimingActivity(outputEvents);
        return stabilizeOutputEvents(outputEvents, emissionStrategySnapshot(), timestampMs);
    }

    public synchronized void observeClock(long timestampMs) {
        if (timestampMs <= 0L) {
            return;
        }
        lastObservationTimestampMs = Math.max(lastObservationTimestampMs, timestampMs);
        maybeResetAfterExtendedIdle(timestampMs);
    }

    public synchronized void reset() {
        baseline.reset();
        adaptive.reset();
        activeStrategy = Strategy.BASELINE;
        lastEmissionStrategy = Strategy.BASELINE;
        adaptivePromotionCount = 0;
        adaptiveEmissionBatches = 0;
        trustedDotEstimateMs = 0.0d;
        lastStableDecodeTimestampMs = -1L;
        lastObservationTimestampMs = -1L;
        lastTimingActivityTimestampMs = -1L;
        pendingFastTrustedDotEstimateMs = 0.0d;
        pendingFastTrustedEvidenceCount = 0;
    }

    public synchronized CwTimingSnapshot snapshot() {
        return stabilizedSnapshot(snapshotSource(), effectiveTimestamp());
    }

    public synchronized CwTimingSnapshot rawSnapshot() {
        return activeStrategy == Strategy.ADAPTIVE
                ? adaptive.snapshot()
                : baseline.snapshot();
    }

    public synchronized String activeStrategyName() {
        return activeStrategy.name();
    }

    public synchronized String debugStrategySummary() {
        return activeStrategy.name()
                + " active / "
                + lastEmissionStrategy.name()
                + " last / promotions="
                + adaptivePromotionCount
                + " / adaptiveBatches="
                + adaptiveEmissionBatches
                + " / trusted="
                + Math.round(trustedWpm());
    }

    public synchronized void setSeedWpm(int wpm) {
        seedWpm = Math.max(0, wpm);
    }

    public synchronized void notifyStableDecode(long timestampMs) {
        lastObservationTimestampMs = Math.max(lastObservationTimestampMs, timestampMs);
        maybeResetAfterExtendedIdle(timestampMs);
        CwTimingSnapshot snapshot = stabilizedSnapshot(emissionStrategySnapshot(), timestampMs);
        if (snapshot == null || snapshot.dotEstimateMs() <= 0L) {
            return;
        }

        long candidateDotEstimateMs = snapshot.dotEstimateMs();
        if (trustedDotEstimateMs <= 0.0d) {
            trustedDotEstimateMs = candidateDotEstimateMs;
            clearPendingFastReanchor();
            lastStableDecodeTimestampMs = timestampMs;
            return;
        }

        if (candidateDotEstimateMs >= trustedDotEstimateMs) {
            trustedDotEstimateMs = blend(trustedDotEstimateMs, candidateDotEstimateMs, TRUSTED_SLOW_UPDATE_BLEND);
            clearPendingFastReanchor();
            lastStableDecodeTimestampMs = timestampMs;
            return;
        }

        double directFastFloor = trustedDotEstimateMs * TRUSTED_FAST_DIRECT_UPDATE_RATIO;
        if (candidateDotEstimateMs >= directFastFloor) {
            trustedDotEstimateMs = blend(
                    trustedDotEstimateMs,
                    candidateDotEstimateMs,
                    TRUSTED_FAST_DIRECT_UPDATE_BLEND
            );
            clearPendingFastReanchor();
            lastStableDecodeTimestampMs = timestampMs;
            return;
        }

        double reanchorFastFloor = trustedDotEstimateMs * TRUSTED_FAST_REANCHOR_RATIO;
        if (candidateDotEstimateMs < reanchorFastFloor) {
            clearPendingFastReanchor();
            lastStableDecodeTimestampMs = timestampMs;
            return;
        }

        if (!matchesPendingFastReanchor(candidateDotEstimateMs)) {
            pendingFastTrustedDotEstimateMs = candidateDotEstimateMs;
            pendingFastTrustedEvidenceCount = 1;
            lastStableDecodeTimestampMs = timestampMs;
            return;
        }

        pendingFastTrustedEvidenceCount += 1;
        if (pendingFastTrustedEvidenceCount >= TRUSTED_FAST_REANCHOR_REQUIRED_STABLE_DECODES) {
            trustedDotEstimateMs = blend(
                    trustedDotEstimateMs,
                    pendingFastTrustedDotEstimateMs,
                    TRUSTED_FAST_REANCHOR_BLEND
            );
            clearPendingFastReanchor();
        }
        lastStableDecodeTimestampMs = timestampMs;
    }

    private void refreshStrategy() {
        CwTimingSnapshot baselineSnapshot = baseline.snapshot();
        CwTimingSnapshot adaptiveSnapshot = adaptive.snapshot();
        if (shouldPromoteAdaptive(baselineSnapshot, adaptiveSnapshot)) {
            if (activeStrategy != Strategy.ADAPTIVE) {
                adaptivePromotionCount += 1;
            }
            activeStrategy = Strategy.ADAPTIVE;
            return;
        }
        if (shouldRestoreBaseline(baselineSnapshot, adaptiveSnapshot)) {
            activeStrategy = Strategy.BASELINE;
        }
    }

    private boolean shouldPromoteAdaptive(
            CwTimingSnapshot baselineSnapshot,
            CwTimingSnapshot adaptiveSnapshot
    ) {
        return adaptiveSnapshot.estimatedWpm() >= ADAPTIVE_PROMOTION_MIN_WPM
                && baselineSnapshot.estimatedWpm() <= ADAPTIVE_PROMOTION_BASELINE_MAX_WPM
                && adaptiveSnapshot.estimatedWpm() >= baselineSnapshot.estimatedWpm() + ADAPTIVE_PROMOTION_WPM_LEAD;
    }

    private boolean shouldRestoreBaseline(
            CwTimingSnapshot baselineSnapshot,
            CwTimingSnapshot adaptiveSnapshot
    ) {
        if (activeStrategy != Strategy.ADAPTIVE) {
            return false;
        }
        return baselineSnapshot.estimatedWpm() >= adaptiveSnapshot.estimatedWpm() - BASELINE_RESTORE_MARGIN_WPM
                || adaptiveSnapshot.estimatedWpm() < ADAPTIVE_PROMOTION_MIN_WPM;
    }

    private List<CwTimingEvent> selectOutputEvents(
            List<CwTimingEvent> baselineEvents,
            List<CwTimingEvent> adaptiveEvents
    ) {
        if (activeStrategy == Strategy.ADAPTIVE) {
            lastEmissionStrategy = Strategy.ADAPTIVE;
            adaptiveEmissionBatches += 1;
            return adaptiveEvents;
        }
        lastEmissionStrategy = Strategy.BASELINE;
        return baselineEvents;
    }

    private CwTimingSnapshot snapshotSource() {
        return lastObservationTimestampMs > 0L ? emissionStrategySnapshot() : rawSnapshot();
    }

    private CwTimingSnapshot emissionStrategySnapshot() {
        return lastEmissionStrategy == Strategy.ADAPTIVE
                ? adaptive.snapshot()
                : baseline.snapshot();
    }

    private long effectiveTimestamp() {
        if (lastObservationTimestampMs > 0L) {
            return lastObservationTimestampMs;
        }
        if (lastStableDecodeTimestampMs > 0L) {
            return lastStableDecodeTimestampMs;
        }
        return 0L;
    }

    private List<CwTimingEvent> stabilizeOutputEvents(
            List<CwTimingEvent> outputEvents,
            CwTimingSnapshot rawSnapshot,
            long timestampMs
    ) {
        if (outputEvents == null || outputEvents.isEmpty()) {
            return outputEvents;
        }
        long effectiveDotEstimateMs = resolveEffectiveDotEstimateMs(rawSnapshot, timestampMs);
        long effectiveIntraGapEstimateMs = resolveEffectiveIntraGapEstimateMs(rawSnapshot, effectiveDotEstimateMs);
        java.util.ArrayList<CwTimingEvent> stabilizedEvents = new java.util.ArrayList<>(outputEvents.size());
        for (CwTimingEvent outputEvent : outputEvents) {
            if (outputEvent == null) {
                continue;
            }
            stabilizedEvents.add(new CwTimingEvent(
                    outputEvent.kind(),
                    reclassify(outputEvent, effectiveDotEstimateMs, effectiveIntraGapEstimateMs),
                    outputEvent.timestampMs(),
                    outputEvent.durationMs(),
                    effectiveDotEstimateMs,
                    effectiveIntraGapEstimateMs
            ));
        }
        return stabilizedEvents;
    }

    private CwTimingSnapshot stabilizedSnapshot(
            CwTimingSnapshot rawSnapshot,
            long timestampMs
    ) {
        maybeResetAfterExtendedIdle(timestampMs);
        if (rawSnapshot == null) {
            return syntheticSnapshot(resolveFallbackDotEstimateMs(), 0L, 0L, 0, 0, null);
        }
        long effectiveDotEstimateMs = resolveEffectiveDotEstimateMs(rawSnapshot, timestampMs);
        long effectiveIntraGapEstimateMs = resolveEffectiveIntraGapEstimateMs(rawSnapshot, effectiveDotEstimateMs);
        long effectiveDashEstimateMs = Math.max(
                Math.max(1L, rawSnapshot.dashEstimateMs()),
                effectiveDotEstimateMs * 3L
        );
        return syntheticSnapshot(
                effectiveDotEstimateMs,
                effectiveDashEstimateMs,
                effectiveIntraGapEstimateMs,
                rawSnapshot.totalToneEvents(),
                rawSnapshot.totalGapEvents(),
                restyleLastTimingEvent(rawSnapshot.lastTimingEvent(), effectiveDotEstimateMs, effectiveIntraGapEstimateMs)
        );
    }

    private CwTimingSnapshot syntheticSnapshot(
            long dotEstimateMs,
            long dashEstimateMs,
            long intraGapEstimateMs,
            int totalToneEvents,
            int totalGapEvents,
            CwTimingEvent lastTimingEvent
    ) {
        long safeDotEstimateMs = Math.max(1L, dotEstimateMs);
        long safeDashEstimateMs = Math.max(safeDotEstimateMs * 3L, dashEstimateMs);
        long safeIntraGapEstimateMs = Math.max(safeDotEstimateMs, intraGapEstimateMs);
        double estimatedWpmPrecise = 1200.0d / safeDotEstimateMs;
        return new CwTimingSnapshot(
                safeDotEstimateMs,
                safeDashEstimateMs,
                safeIntraGapEstimateMs,
                (int) Math.round(estimatedWpmPrecise),
                estimatedWpmPrecise,
                totalToneEvents,
                totalGapEvents,
                lastTimingEvent
        );
    }

    private CwTimingEvent restyleLastTimingEvent(
            CwTimingEvent lastTimingEvent,
            long effectiveDotEstimateMs,
            long effectiveIntraGapEstimateMs
    ) {
        if (lastTimingEvent == null) {
            return null;
        }
        return new CwTimingEvent(
                lastTimingEvent.kind(),
                reclassify(lastTimingEvent, effectiveDotEstimateMs, effectiveIntraGapEstimateMs),
                lastTimingEvent.timestampMs(),
                lastTimingEvent.durationMs(),
                effectiveDotEstimateMs,
                effectiveIntraGapEstimateMs
        );
    }

    private long resolveEffectiveDotEstimateMs(CwTimingSnapshot rawSnapshot, long timestampMs) {
        maybeResetAfterExtendedIdle(timestampMs);
        long rawDotEstimateMs = resolveRawDotEstimateMs(rawSnapshot);
        double effectiveDotEstimateMs = rawDotEstimateMs > 0L
                ? rawDotEstimateMs
                : resolveFallbackDotEstimateMs();
        if (trustedDotEstimateMs > 0.0d) {
            double trustedFastFloor = trustedDotEstimateMs * TRUSTED_FAST_FLOOR_RATIO;
            if (effectiveDotEstimateMs < trustedFastFloor) {
                effectiveDotEstimateMs = trustedFastFloor;
            }
        }
        return Math.max(1L, Math.round(effectiveDotEstimateMs));
    }

    private long resolveRawDotEstimateMs(CwTimingSnapshot rawSnapshot) {
        if (rawSnapshot == null) {
            return 0L;
        }
        if (rawSnapshot.lastTimingEvent() == null
                && rawSnapshot.totalToneEvents() == 0
                && rawSnapshot.totalGapEvents() == 0) {
            return resolveFallbackDotEstimateMs();
        }
        return Math.max(1L, rawSnapshot.dotEstimateMs());
    }

    private long resolveFallbackDotEstimateMs() {
        if (trustedDotEstimateMs > 0.0d) {
            return Math.max(1L, Math.round(trustedDotEstimateMs));
        }
        if (seedWpm > 0) {
            return wpmToDotEstimateMs(seedWpm);
        }
        return Math.max(1L, baseline.snapshot().dotEstimateMs());
    }

    private long resolveEffectiveIntraGapEstimateMs(
            CwTimingSnapshot rawSnapshot,
            long effectiveDotEstimateMs
    ) {
        long rawIntraGapEstimateMs = rawSnapshot == null
                ? 0L
                : Math.max(1L, rawSnapshot.intraGapEstimateMs());
        return Math.max(effectiveDotEstimateMs, rawIntraGapEstimateMs);
    }

    private void maybeResetAfterExtendedIdle(long timestampMs) {
        if (timestampMs <= 0L
                || lastTimingActivityTimestampMs <= 0L
                || (timestampMs - lastTimingActivityTimestampMs) < TRUSTED_IDLE_RELEASE_MS) {
            return;
        }
        resetAfterExtendedIdle();
    }

    private void resetAfterExtendedIdle() {
        baseline.reset();
        adaptive.reset();
        activeStrategy = Strategy.BASELINE;
        lastEmissionStrategy = Strategy.BASELINE;
        trustedDotEstimateMs = 0.0d;
        lastStableDecodeTimestampMs = -1L;
        lastTimingActivityTimestampMs = -1L;
        clearPendingFastReanchor();
    }

    private void noteTimingActivity(List<CwTimingEvent> outputEvents) {
        if (outputEvents == null || outputEvents.isEmpty()) {
            return;
        }
        for (int index = outputEvents.size() - 1; index >= 0; index--) {
            CwTimingEvent timingEvent = outputEvents.get(index);
            if (timingEvent == null) {
                continue;
            }
            lastTimingActivityTimestampMs = Math.max(lastTimingActivityTimestampMs, timingEvent.timestampMs());
            return;
        }
    }

    private void clearPendingFastReanchor() {
        pendingFastTrustedDotEstimateMs = 0.0d;
        pendingFastTrustedEvidenceCount = 0;
    }

    private boolean matchesPendingFastReanchor(long candidateDotEstimateMs) {
        if (pendingFastTrustedEvidenceCount <= 0 || pendingFastTrustedDotEstimateMs <= 0.0d) {
            return false;
        }
        double absoluteDeltaMs = Math.abs(candidateDotEstimateMs - pendingFastTrustedDotEstimateMs);
        double maxDeltaMs = Math.max(3.0d, pendingFastTrustedDotEstimateMs * TRUSTED_FAST_REANCHOR_MATCH_RATIO);
        return absoluteDeltaMs <= maxDeltaMs;
    }

    private double blend(double currentValue, double newValue, double ratio) {
        return currentValue + ((newValue - currentValue) * ratio);
    }

    private double trustedWpm() {
        if (trustedDotEstimateMs <= 0.0d) {
            return seedWpm > 0 ? seedWpm : 0.0d;
        }
        return 1200.0d / trustedDotEstimateMs;
    }

    private long wpmToDotEstimateMs(int wpm) {
        return Math.max(1L, Math.round(1200.0d / Math.max(1, wpm)));
    }

    private CwTimingEvent.Classification reclassify(
            CwTimingEvent timingEvent,
            long effectiveDotEstimateMs,
            long effectiveIntraGapEstimateMs
    ) {
        if (timingEvent.kind() == CwTimingEvent.Kind.TONE) {
            return classifyTone(timingEvent.durationMs(), effectiveDotEstimateMs);
        }
        return classifyGap(
                timingEvent.durationMs(),
                effectiveDotEstimateMs,
                effectiveIntraGapEstimateMs
        );
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
}
