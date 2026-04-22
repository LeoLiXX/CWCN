package org.bi9clt.cwcn.core.timing;

import org.bi9clt.cwcn.core.signal.CwToneEvent;

import java.util.ArrayList;
import java.util.List;

public final class CwTimingModel {
    private static final long DEFAULT_DOT_MS = 80L;
    private static final long MIN_DOT_MS = 35L;
    private static final long MAX_DOT_MS = 220L;
    private static final double STARTUP_DASH_RATIO = 1.65d;
    private static final double DOT_SMOOTHING = 0.17d;
    private static final double GAP_SMOOTHING = 0.16d;

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
            dashEstimateMs = smoothEstimate(dashEstimateMs, toneDurationMs, DOT_SMOOTHING);
            normalizedDuration = dashEstimateMs / 3.0d;
        } else {
            dashEstimateMs = smoothEstimate(dashEstimateMs, dotEstimateMs * 3.0d, 0.05d);
        }

        dotEstimateMs = smoothEstimate(dotEstimateMs, normalizedDuration, DOT_SMOOTHING);
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

        // Blend toward the default boot dot so startup is stable for both fast and slow senders.
        double bootstrapDot = (inferredDot + DEFAULT_DOT_MS) * 0.5d;
        dotEstimateMs = clampDot(bootstrapDot);
        intraGapEstimateMs = dotEstimateMs;
        dashEstimateMs = Math.max(dashEstimateMs, dotEstimateMs * 3.0d);
    }

    private void updateGapEstimates(long gapDurationMs, CwTimingEvent.Classification classification) {
        if (gapDurationMs <= 0L) {
            return;
        }
        if (classification == CwTimingEvent.Classification.INTRA_SYMBOL_GAP) {
            intraGapEstimateMs = smoothEstimate(intraGapEstimateMs, gapDurationMs, GAP_SMOOTHING);
            if (initialized) {
                dotEstimateMs = clampDot(smoothEstimate(dotEstimateMs, gapDurationMs, GAP_SMOOTHING * 0.7d));
            }
            return;
        }

        if (classification == CwTimingEvent.Classification.LETTER_GAP) {
            double inferredDot = gapDurationMs / 3.0d;
            intraGapEstimateMs = smoothEstimate(intraGapEstimateMs, inferredDot, GAP_SMOOTHING * 0.6d);
            if (initialized) {
                dotEstimateMs = clampDot(smoothEstimate(dotEstimateMs, inferredDot, GAP_SMOOTHING * 0.5d));
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
        if (ratio <= 1.8d) {
            return CwTimingEvent.Classification.INTRA_SYMBOL_GAP;
        }
        if (ratio <= 4.8d) {
            return CwTimingEvent.Classification.LETTER_GAP;
        }
        if (ratio <= 10.0d) {
            return CwTimingEvent.Classification.WORD_GAP;
        }
        return CwTimingEvent.Classification.UNKNOWN;
    }

    private double smoothEstimate(double currentValue, double newValue, double smoothing) {
        return currentValue + ((newValue - currentValue) * smoothing);
    }

    private double clampDot(double candidate) {
        return Math.max(MIN_DOT_MS, Math.min(MAX_DOT_MS, candidate));
    }

    private long dotEstimateRounded() {
        return Math.max(1L, Math.round(dotEstimateMs));
    }
}
