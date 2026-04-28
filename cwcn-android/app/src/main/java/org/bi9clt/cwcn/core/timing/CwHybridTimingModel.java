package org.bi9clt.cwcn.core.timing;

import org.bi9clt.cwcn.core.signal.CwToneEvent;

import java.util.List;

public final class CwHybridTimingModel {
    private static final int ADAPTIVE_PROMOTION_MIN_WPM = 19;
    private static final int ADAPTIVE_PROMOTION_WPM_LEAD = 5;
    private static final int ADAPTIVE_PROMOTION_BASELINE_MAX_WPM = 20;
    private static final int BASELINE_RESTORE_MARGIN_WPM = 1;

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

    public synchronized List<CwTimingEvent> process(CwToneEvent toneEvent) {
        List<CwTimingEvent> baselineEvents = baseline.process(toneEvent);
        List<CwTimingEvent> adaptiveEvents = adaptive.process(toneEvent);
        refreshStrategy();
        return selectOutputEvents(baselineEvents, adaptiveEvents);
    }

    public synchronized List<CwTimingEvent> flushPendingGap(long timestampMs) {
        List<CwTimingEvent> baselineEvents = baseline.flushPendingGap(timestampMs);
        List<CwTimingEvent> adaptiveEvents = adaptive.flushPendingGap(timestampMs);
        refreshStrategy();
        return selectOutputEvents(baselineEvents, adaptiveEvents);
    }

    public synchronized void reset() {
        baseline.reset();
        adaptive.reset();
        activeStrategy = Strategy.BASELINE;
        lastEmissionStrategy = Strategy.BASELINE;
        adaptivePromotionCount = 0;
        adaptiveEmissionBatches = 0;
    }

    public synchronized CwTimingSnapshot snapshot() {
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
                + adaptiveEmissionBatches;
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
}
