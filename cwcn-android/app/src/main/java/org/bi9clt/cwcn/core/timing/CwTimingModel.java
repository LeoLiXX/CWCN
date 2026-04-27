package org.bi9clt.cwcn.core.timing;

import org.bi9clt.cwcn.core.signal.CwToneEvent;

import java.util.ArrayList;
import java.util.List;

public final class CwTimingModel {
    private static final long DEFAULT_DOT_MS = 80L;
    private static final long MIN_DOT_MS = 28L;
    private static final long MAX_DOT_MS = 220L;
    private static final double STARTUP_DASH_RATIO = 1.65d;
    private static final double DOT_SMOOTHING = 0.17d;
    private static final double GAP_SMOOTHING = 0.16d;
    private static final double FAST_DOT_SMOOTHING = 0.30d;
    private static final double FAST_GAP_SMOOTHING = 0.24d;
    private static final long FAST_DOT_THRESHOLD_MS = 55L;
    private static final double INTRA_GAP_MAX_RATIO = 1.8d;
    private static final double LETTER_GAP_MAX_RATIO = 4.35d;
    private static final double WORD_GAP_MAX_RATIO = 10.0d;
    private boolean initialized;
    private double dotEstimateMs = DEFAULT_DOT_MS;
    private double dashEstimateMs = DEFAULT_DOT_MS * 3.0d;
    private double intraGapEstimateMs = DEFAULT_DOT_MS;
    private long lastToneOffTimestampMs = -1L;
    private int totalToneEvents;
    private int totalGapEvents;
    private CwTimingEvent lastTimingEvent;

    public synchronized List<CwTimingEvent> process(CwToneEvent toneEvent) {
        ArrayList<CwTimingEvent> timingEvents = new ArrayList<>(2);

        if (toneEvent.type() == CwToneEvent.Type.TONE_ON) {
            if (lastToneOffTimestampMs > 0L) {
                long gapDurationMs = Math.max(0L, toneEvent.timestampMs() - lastToneOffTimestampMs);
                CwTimingEvent.Classification gapClassification = classifyGap(gapDurationMs);
                updateGapEstimates(gapDurationMs, gapClassification);
                CwTimingEvent gapEvent = new CwTimingEvent(
                        CwTimingEvent.Kind.GAP,
                        gapClassification,
                        toneEvent.timestampMs(),
                        gapDurationMs,
                        dotEstimateRounded()
                );
                totalGapEvents += 1;
                lastTimingEvent = gapEvent;
                timingEvents.add(gapEvent);
            }
            return timingEvents;
        }

        long toneDurationMs = Math.max(0L, toneEvent.toneDurationMs());
        updateDotEstimate(toneDurationMs);
        CwTimingEvent toneTimingEvent = new CwTimingEvent(
                CwTimingEvent.Kind.TONE,
                classifyTone(toneDurationMs),
                toneEvent.timestampMs(),
                toneDurationMs,
                dotEstimateRounded()
        );
        totalToneEvents += 1;
        lastToneOffTimestampMs = toneEvent.timestampMs();
        lastTimingEvent = toneTimingEvent;
        timingEvents.add(toneTimingEvent);
        return timingEvents;
    }

    public synchronized List<CwTimingEvent> flushPendingGap(long timestampMs) {
        ArrayList<CwTimingEvent> timingEvents = new ArrayList<>(1);
        if (lastToneOffTimestampMs <= 0L || timestampMs <= lastToneOffTimestampMs) {
            return timingEvents;
        }

        long gapDurationMs = Math.max(0L, timestampMs - lastToneOffTimestampMs);
        CwTimingEvent.Classification gapClassification = classifyGap(gapDurationMs);
        if (gapClassification == CwTimingEvent.Classification.INTRA_SYMBOL_GAP) {
            return timingEvents;
        }

        updateGapEstimates(gapDurationMs, gapClassification);
        CwTimingEvent gapEvent = new CwTimingEvent(
                CwTimingEvent.Kind.GAP,
                gapClassification,
                timestampMs,
                gapDurationMs,
                dotEstimateRounded()
        );
        totalGapEvents += 1;
        lastTimingEvent = gapEvent;
        lastToneOffTimestampMs = -1L;
        timingEvents.add(gapEvent);
        return timingEvents;
    }

    public synchronized void reset() {
        initialized = false;
        dotEstimateMs = DEFAULT_DOT_MS;
        dashEstimateMs = DEFAULT_DOT_MS * 3.0d;
        intraGapEstimateMs = DEFAULT_DOT_MS;
        lastToneOffTimestampMs = -1L;
        totalToneEvents = 0;
        totalGapEvents = 0;
        lastTimingEvent = null;
    }

    public synchronized CwTimingSnapshot snapshot() {
        long dotRounded = dotEstimateRounded();
        int estimatedWpm = dotRounded <= 0L ? 0 : (int) Math.round(1200.0d / dotRounded);
        return new CwTimingSnapshot(
                dotRounded,
                Math.max(1L, Math.round(dashEstimateMs)),
                Math.max(1L, Math.round(intraGapEstimateMs)),
                estimatedWpm,
                totalToneEvents,
                totalGapEvents,
                lastTimingEvent
        );
    }

    private void updateDotEstimate(long toneDurationMs) {
        if (toneDurationMs <= 0L) {
            return;
        }

        if (!initialized) {
            bootstrapFromFirstTone(toneDurationMs);
            initialized = true;
            return;
        }

        double normalizedDuration = toneDurationMs;
        double ratioToDot = toneDurationMs / Math.max(1.0d, dotEstimateMs);
        if (ratioToDot > 1.9d && ratioToDot < 5.2d) {
            dashEstimateMs = smoothEstimate(dashEstimateMs, toneDurationMs, toneSmoothing(toneDurationMs));
            normalizedDuration = dashEstimateMs / 3.0d;
        } else {
            dashEstimateMs = smoothEstimate(dashEstimateMs, dotEstimateMs * 3.0d, 0.05d);
        }

        dotEstimateMs = smoothEstimate(dotEstimateMs, normalizedDuration, toneSmoothing(normalizedDuration));
        dotEstimateMs = clampDot(dotEstimateMs);
    }

    private void bootstrapFromFirstTone(long toneDurationMs) {
        double rawDuration = Math.max(1.0d, toneDurationMs);
        double inferredDot = rawDuration;
        boolean looksLikeDash = rawDuration >= (DEFAULT_DOT_MS * STARTUP_DASH_RATIO);
        if (looksLikeDash) {
            inferredDot = rawDuration / 3.0d;
            dashEstimateMs = rawDuration;
        } else {
            dashEstimateMs = rawDuration * 3.0d;
        }

        // Keep some default bias for stability, but lean harder toward the observed first element
        // so faster on-air starts do not begin with an overly slow dot estimate.
        double defaultBias = inferredDot <= FAST_DOT_THRESHOLD_MS ? 0.15d : 0.35d;
        double observedBias = 1.0d - defaultBias;
        double bootstrapDot = (inferredDot * observedBias) + (DEFAULT_DOT_MS * defaultBias);
        dotEstimateMs = clampDot(bootstrapDot);
        intraGapEstimateMs = dotEstimateMs;
        dashEstimateMs = Math.max(dashEstimateMs, dotEstimateMs * 3.0d);
    }

    private void updateGapEstimates(long gapDurationMs, CwTimingEvent.Classification classification) {
        if (gapDurationMs <= 0L) {
            return;
        }
        if (classification == CwTimingEvent.Classification.INTRA_SYMBOL_GAP) {
            intraGapEstimateMs = smoothEstimate(intraGapEstimateMs, gapDurationMs, gapSmoothing(gapDurationMs));
            if (initialized) {
                dotEstimateMs = clampDot(smoothEstimate(dotEstimateMs, gapDurationMs, gapDotSmoothing(gapDurationMs)));
            }
            return;
        }

        if (classification == CwTimingEvent.Classification.LETTER_GAP) {
            double inferredDot = gapDurationMs / 3.0d;
            intraGapEstimateMs = smoothEstimate(intraGapEstimateMs, inferredDot, gapSmoothing(inferredDot) * 0.75d);
            if (initialized) {
                dotEstimateMs = clampDot(smoothEstimate(dotEstimateMs, inferredDot, gapDotSmoothing(inferredDot) * 0.8d));
            }
        }
    }

    private CwTimingEvent.Classification classifyTone(long toneDurationMs) {
        double ratio = toneDurationMs / Math.max(1.0d, dotEstimateMs);
        if (ratio <= 1.8d) {
            return CwTimingEvent.Classification.DIT;
        }
        if (ratio <= 4.8d) {
            return CwTimingEvent.Classification.DAH;
        }
        return CwTimingEvent.Classification.UNKNOWN;
    }

    private CwTimingEvent.Classification classifyGap(long gapDurationMs) {
        double ratio = gapDurationMs / Math.max(1.0d, dotEstimateMs);
        if (ratio <= INTRA_GAP_MAX_RATIO) {
            return CwTimingEvent.Classification.INTRA_SYMBOL_GAP;
        }
        if (ratio <= LETTER_GAP_MAX_RATIO) {
            return CwTimingEvent.Classification.LETTER_GAP;
        }
        if (ratio <= WORD_GAP_MAX_RATIO) {
            return CwTimingEvent.Classification.WORD_GAP;
        }
        return CwTimingEvent.Classification.UNKNOWN;
    }

    private double smoothEstimate(double currentValue, double newValue, double smoothing) {
        return currentValue + ((newValue - currentValue) * smoothing);
    }

    private double toneSmoothing(double observedDurationMs) {
        return observedDurationMs <= FAST_DOT_THRESHOLD_MS ? FAST_DOT_SMOOTHING : DOT_SMOOTHING;
    }

    private double gapSmoothing(double observedDurationMs) {
        return observedDurationMs <= FAST_DOT_THRESHOLD_MS ? FAST_GAP_SMOOTHING : GAP_SMOOTHING;
    }

    private double gapDotSmoothing(double inferredDotMs) {
        return inferredDotMs <= FAST_DOT_THRESHOLD_MS ? FAST_GAP_SMOOTHING * 0.85d : GAP_SMOOTHING * 0.7d;
    }

    private double clampDot(double candidate) {
        return Math.max(MIN_DOT_MS, Math.min(MAX_DOT_MS, candidate));
    }

    private long dotEstimateRounded() {
        return Math.max(1L, Math.round(dotEstimateMs));
    }
}
