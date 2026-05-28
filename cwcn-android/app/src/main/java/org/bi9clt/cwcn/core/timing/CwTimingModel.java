package org.bi9clt.cwcn.core.timing;

import org.bi9clt.cwcn.core.signal.CwToneEvent;

import java.util.ArrayList;
import java.util.List;

public final class CwTimingModel {
    private static final long DEFAULT_DOT_MS = 80L;
    private static final long MIN_DOT_MS = 28L;
    private static final long MAX_DOT_MS = 220L;
    // Keep startup dash detection permissive enough for fast 30 WPM openers:
    // the first CQ dah can land around 118 ms and should not inflate the initial dot estimate.
    private static final double STARTUP_DASH_RATIO = 1.45d;
    private static final double DOT_SMOOTHING = 0.17d;
    private static final double GAP_SMOOTHING = 0.16d;
    private static final double FAST_DOT_SMOOTHING = 0.30d;
    private static final double FAST_GAP_SMOOTHING = 0.24d;
    private static final long FAST_DOT_THRESHOLD_MS = 55L;
    private static final double GAP_DOT_MAX_SLOWDOWN_STEP_RATIO = 1.18d;
    private static final double GAP_DOT_MAX_SPEEDUP_STEP_RATIO = 0.88d;
    private static final double INTRA_GAP_MAX_RATIO = 1.8d;
    private static final double LETTER_GAP_MAX_RATIO = 4.70d;
    private static final double WORD_GAP_MAX_RATIO = 12.8d;
    private static final double FAST_INTRA_GAP_MAX_RATIO = 1.55d;
    private static final double FAST_LETTER_GAP_MAX_RATIO = 3.95d;
    private static final double FAST_WORD_GAP_MAX_RATIO = 11.8d;
    private static final double WORD_GAP_INTRA_RATIO_FALLBACK = 5.0d;
    private static final double WORD_GAP_DOT_RATIO_FALLBACK_MIN = 3.15d;
    private static final double TONE_REFERENCE_SMOOTHING_RATIO = 0.60d;
    // Keep only a very small tone-derived cushion here:
    // borderline gaps should remain decodable as letter gaps,
    // but clearly intra-like gaps still get a bit of protection from a drifted-fast dot estimate.
    private static final double INTRA_GAP_TONE_REFERENCE_HYSTERESIS_RATIO = 1.04d;
    private static final double CLEAR_LETTER_GAP_LEARNING_MIN_RATIO = 2.25d;
    private static final double LETTER_GAP_INTRA_SMOOTHING_SCALE = 0.55d;
    private static final double LETTER_GAP_DOT_SMOOTHING_SCALE = 0.35d;
    private static final double INTRA_GAP_DOT_SMOOTHING_SCALE = 0.75d;
    private static final double GAP_DOT_TONE_REFERENCE_FLOOR_RATIO = 0.98d;
    private boolean initialized;
    private double dotEstimateMs = DEFAULT_DOT_MS;
    private double dashEstimateMs = DEFAULT_DOT_MS * 3.0d;
    private double intraGapEstimateMs = DEFAULT_DOT_MS;
    private double toneDotReferenceMs = DEFAULT_DOT_MS;
    private long lastToneOffTimestampMs = -1L;
    private int totalToneEvents;
    private int totalGapEvents;
    private CwTimingEvent lastTimingEvent;

    public synchronized List<CwTimingEvent> process(CwToneEvent toneEvent) {
        return process(toneEvent, true);
    }

    public synchronized List<CwTimingEvent> process(CwToneEvent toneEvent, boolean allowLearning) {
        ArrayList<CwTimingEvent> timingEvents = new ArrayList<>(2);

        if (toneEvent.type() == CwToneEvent.Type.TONE_ON) {
            if (lastToneOffTimestampMs > 0L) {
                long gapDurationMs = Math.max(0L, toneEvent.timestampMs() - lastToneOffTimestampMs);
                CwTimingEvent.Classification gapClassification = classifyGap(gapDurationMs);
                if (allowLearning) {
                    updateGapEstimates(gapDurationMs, gapClassification);
                }
                CwTimingEvent gapEvent = new CwTimingEvent(
                        CwTimingEvent.Kind.GAP,
                        gapClassification,
                        toneEvent.timestampMs(),
                        gapDurationMs,
                        dotEstimateRounded(),
                        intraGapEstimateRounded()
                );
                totalGapEvents += 1;
                lastTimingEvent = gapEvent;
                timingEvents.add(gapEvent);
            }
            return timingEvents;
        }

        long toneDurationMs = Math.max(0L, toneEvent.toneDurationMs());
        if (allowLearning) {
            updateDotEstimate(toneDurationMs);
        }
        CwTimingEvent toneTimingEvent = new CwTimingEvent(
                CwTimingEvent.Kind.TONE,
                classifyTone(toneDurationMs),
                toneEvent.timestampMs(),
                toneDurationMs,
                dotEstimateRounded(),
                intraGapEstimateRounded()
        );
        totalToneEvents += 1;
        lastToneOffTimestampMs = toneEvent.timestampMs();
        lastTimingEvent = toneTimingEvent;
        timingEvents.add(toneTimingEvent);
        return timingEvents;
    }

    public synchronized List<CwTimingEvent> flushPendingGap(long timestampMs) {
        return flushPendingGap(timestampMs, true);
    }

    public synchronized List<CwTimingEvent> flushPendingGap(long timestampMs, boolean allowLearning) {
        ArrayList<CwTimingEvent> timingEvents = new ArrayList<>(1);
        if (lastToneOffTimestampMs <= 0L || timestampMs <= lastToneOffTimestampMs) {
            return timingEvents;
        }

        long gapDurationMs = Math.max(0L, timestampMs - lastToneOffTimestampMs);
        CwTimingEvent.Classification gapClassification = classifyGap(gapDurationMs);
        if (gapClassification == CwTimingEvent.Classification.INTRA_SYMBOL_GAP) {
            return timingEvents;
        }

        if (allowLearning) {
            updateGapEstimates(gapDurationMs, gapClassification);
        }
        CwTimingEvent gapEvent = new CwTimingEvent(
                CwTimingEvent.Kind.GAP,
                gapClassification,
                timestampMs,
                gapDurationMs,
                dotEstimateRounded(),
                intraGapEstimateRounded()
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
        toneDotReferenceMs = DEFAULT_DOT_MS;
        lastToneOffTimestampMs = -1L;
        totalToneEvents = 0;
        totalGapEvents = 0;
        lastTimingEvent = null;
    }

    public synchronized CwTimingSnapshot snapshot() {
        long dotRounded = dotEstimateRounded();
        double estimatedWpmPrecise = dotRounded <= 0L ? 0.0d : 1200.0d / dotRounded;
        int estimatedWpm = (int) Math.round(estimatedWpmPrecise);
        return new CwTimingSnapshot(
                dotRounded,
                Math.max(1L, Math.round(dashEstimateMs)),
                Math.max(1L, Math.round(intraGapEstimateMs)),
                estimatedWpm,
                estimatedWpmPrecise,
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
        toneDotReferenceMs = clampDot(smoothEstimate(
                toneDotReferenceMs,
                normalizedDuration,
                toneReferenceSmoothing(normalizedDuration)
        ));
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
        toneDotReferenceMs = dotEstimateMs;
    }

    private void updateGapEstimates(long gapDurationMs, CwTimingEvent.Classification classification) {
        if (gapDurationMs <= 0L) {
            return;
        }
        if (classification == CwTimingEvent.Classification.INTRA_SYMBOL_GAP) {
            intraGapEstimateMs = smoothEstimate(intraGapEstimateMs, gapDurationMs, gapSmoothing(gapDurationMs));
            if (initialized) {
                dotEstimateMs = clampDot(limitGapDrivenDotShift(
                        dotEstimateMs,
                        smoothEstimate(
                                dotEstimateMs,
                                gapDurationMs,
                                gapDotSmoothing(gapDurationMs) * INTRA_GAP_DOT_SMOOTHING_SCALE
                        )
                ));
            }
            return;
        }

        if (classification == CwTimingEvent.Classification.LETTER_GAP
                || classification == CwTimingEvent.Classification.WORD_GAP) {
            // Experimental branch:
            // keep boundary gaps available for classification/decoding only.
            // Do not let letter/word spacing feed back into timing learning.
            return;
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
        double intraRatio = gapDurationMs / Math.max(1.0d, intraGapEstimateMs);
        double intraReferenceRatio = gapDurationMs / intraGapClassificationReferenceDotMs();
        double intraGapMaxRatio = isFastTimingContext()
                ? FAST_INTRA_GAP_MAX_RATIO
                : INTRA_GAP_MAX_RATIO;
        double letterGapMaxRatio = isFastTimingContext()
                ? FAST_LETTER_GAP_MAX_RATIO
                : LETTER_GAP_MAX_RATIO;
        double wordGapMaxRatio = isFastTimingContext()
                ? FAST_WORD_GAP_MAX_RATIO
                : WORD_GAP_MAX_RATIO;
        if (intraReferenceRatio <= intraGapMaxRatio) {
            return CwTimingEvent.Classification.INTRA_SYMBOL_GAP;
        }
        if (ratio >= WORD_GAP_DOT_RATIO_FALLBACK_MIN
                && intraRatio >= WORD_GAP_INTRA_RATIO_FALLBACK) {
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

    private boolean isAmbiguousLongLetterGap(long gapDurationMs) {
        if (gapDurationMs <= 0L || !isFastTimingContext()) {
            return false;
        }
        double ratio = gapDurationMs / Math.max(1.0d, dotEstimateMs);
        double intraRatio = gapDurationMs / Math.max(1.0d, intraGapEstimateMs);
        return ratio >= WORD_GAP_DOT_RATIO_FALLBACK_MIN
                && intraRatio < WORD_GAP_INTRA_RATIO_FALLBACK;
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

    private double toneReferenceSmoothing(double observedDurationMs) {
        return toneSmoothing(observedDurationMs) * TONE_REFERENCE_SMOOTHING_RATIO;
    }

    private double clampDot(double candidate) {
        return Math.max(MIN_DOT_MS, Math.min(MAX_DOT_MS, candidate));
    }

    private double intraGapClassificationReferenceDotMs() {
        double referenceDotMs = Math.max(1.0d, dotEstimateMs);
        if (toneDotReferenceMs > 0.0d) {
            referenceDotMs = Math.max(
                    referenceDotMs,
                    toneDotReferenceMs * INTRA_GAP_TONE_REFERENCE_HYSTERESIS_RATIO
            );
        }
        return referenceDotMs;
    }

    private boolean isFastTimingContext() {
        return dotEstimateMs <= FAST_DOT_THRESHOLD_MS || intraGapEstimateMs <= FAST_DOT_THRESHOLD_MS;
    }

    private long intraGapEstimateRounded() {
        return Math.max(1L, Math.round(intraGapEstimateMs));
    }

    private double limitGapDrivenDotShift(double currentDotMs, double gapDrivenDotMs) {
        if (gapDrivenDotMs < currentDotMs) {
            double minimumDotMs = currentDotMs * GAP_DOT_MAX_SPEEDUP_STEP_RATIO;
            if (toneDotReferenceMs > 0.0d) {
                minimumDotMs = Math.max(
                        minimumDotMs,
                        toneDotReferenceMs * GAP_DOT_TONE_REFERENCE_FLOOR_RATIO
                );
            }
            return Math.max(gapDrivenDotMs, minimumDotMs);
        }
        if (gapDrivenDotMs <= currentDotMs) {
            return gapDrivenDotMs;
        }
        return Math.min(gapDrivenDotMs, currentDotMs * GAP_DOT_MAX_SLOWDOWN_STEP_RATIO);
    }

    private boolean shouldLearnFromLetterGap(long gapDurationMs, double inferredDotMs) {
        if (gapDurationMs <= 0L || inferredDotMs <= 0.0d) {
            return false;
        }
        double learningReferenceDotMs = Math.max(1.0d, dotEstimateMs);
        double ratioToReference = gapDurationMs / learningReferenceDotMs;
        if (ratioToReference < CLEAR_LETTER_GAP_LEARNING_MIN_RATIO) {
            return false;
        }
        if (toneDotReferenceMs <= 0.0d) {
            return true;
        }
        return inferredDotMs >= (toneDotReferenceMs * GAP_DOT_TONE_REFERENCE_FLOOR_RATIO);
    }

    private long dotEstimateRounded() {
        return Math.max(1L, Math.round(dotEstimateMs));
    }
}
