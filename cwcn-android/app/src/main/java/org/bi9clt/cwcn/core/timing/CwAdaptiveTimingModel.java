package org.bi9clt.cwcn.core.timing;

import org.bi9clt.cwcn.core.signal.CwToneEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class CwAdaptiveTimingModel {
    private static final long DEFAULT_DOT_MS = 60L;
    private static final long MIN_DOT_MS = 24L;
    private static final long MAX_DOT_MS = 220L;
    private static final long FAST_BOOTSTRAP_THRESHOLD_MS = 55L;
    private static final int RECENT_CANDIDATE_WINDOW = 12;
    private static final double INTRA_GAP_MAX_RATIO = 1.95d;
    private static final double LETTER_GAP_MAX_RATIO = 5.2d;
    private static final double WORD_GAP_MAX_RATIO = 10.8d;
    private static final double WORD_GAP_INTRA_RATIO_FALLBACK = 5.0d;
    private static final double WORD_GAP_DOT_RATIO_FALLBACK_MIN = 3.15d;

    private final long[] recentToneDotCandidatesMs = new long[RECENT_CANDIDATE_WINDOW];
    private final long[] recentGapDotCandidatesMs = new long[RECENT_CANDIDATE_WINDOW];
    private final long[] recentDashCandidatesMs = new long[RECENT_CANDIDATE_WINDOW];

    private boolean initialized;
    private double dotEstimateMs = DEFAULT_DOT_MS;
    private double dashEstimateMs = DEFAULT_DOT_MS * 3.0d;
    private double intraGapEstimateMs = DEFAULT_DOT_MS;
    private long lastToneOffTimestampMs = -1L;
    private int totalToneEvents;
    private int totalGapEvents;
    private int toneCandidateCount;
    private int gapCandidateCount;
    private int dashCandidateCount;
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
        updateToneEstimates(toneDurationMs);
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
        lastToneOffTimestampMs = -1L;
        totalToneEvents = 0;
        totalGapEvents = 0;
        toneCandidateCount = 0;
        gapCandidateCount = 0;
        dashCandidateCount = 0;
        Arrays.fill(recentToneDotCandidatesMs, 0L);
        Arrays.fill(recentGapDotCandidatesMs, 0L);
        Arrays.fill(recentDashCandidatesMs, 0L);
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

    private void updateToneEstimates(long toneDurationMs) {
        if (toneDurationMs <= 0L) {
            return;
        }

        long dotCandidateMs = inferDotCandidateFromTone(toneDurationMs);
        appendCandidate(recentToneDotCandidatesMs, toneCandidateCount, dotCandidateMs);
        toneCandidateCount += 1;
        if (toneDurationMs >= Math.max(dotEstimateMs * 2.1d, FAST_BOOTSTRAP_THRESHOLD_MS * 1.8d)) {
            appendCandidate(recentDashCandidatesMs, dashCandidateCount, toneDurationMs);
            dashCandidateCount += 1;
        }

        recomputeEstimates();
        initialized = true;
    }

    private void updateGapEstimates(long gapDurationMs, CwTimingEvent.Classification classification) {
        if (gapDurationMs <= 0L) {
            return;
        }

        long dotCandidateMs = 0L;
        if (classification == CwTimingEvent.Classification.INTRA_SYMBOL_GAP) {
            dotCandidateMs = gapDurationMs;
        } else if (classification == CwTimingEvent.Classification.LETTER_GAP) {
            if (isAmbiguousLongLetterGap(gapDurationMs)) {
                return;
            }
            dotCandidateMs = Math.max(1L, Math.round(gapDurationMs / 3.0d));
        } else if (classification == CwTimingEvent.Classification.WORD_GAP) {
            dotCandidateMs = Math.max(1L, Math.round(gapDurationMs / 7.0d));
        }

        if (dotCandidateMs > 0L) {
            appendCandidate(recentGapDotCandidatesMs, gapCandidateCount, dotCandidateMs);
            gapCandidateCount += 1;
            recomputeEstimates();
            initialized = true;
        }
    }

    private void recomputeEstimates() {
        long toneMedianMs = medianCandidate(recentToneDotCandidatesMs, toneCandidateCount);
        long gapMedianMs = medianCandidate(recentGapDotCandidatesMs, gapCandidateCount);
        long dashMedianMs = medianCandidate(recentDashCandidatesMs, dashCandidateCount);

        double nextDotEstimateMs;
        if (toneMedianMs > 0L && gapMedianMs > 0L) {
            nextDotEstimateMs = (toneMedianMs * 0.68d) + (gapMedianMs * 0.32d);
        } else if (toneMedianMs > 0L) {
            nextDotEstimateMs = toneMedianMs;
        } else if (gapMedianMs > 0L) {
            nextDotEstimateMs = gapMedianMs;
        } else {
            nextDotEstimateMs = dotEstimateMs;
        }

        if (!initialized && nextDotEstimateMs <= FAST_BOOTSTRAP_THRESHOLD_MS) {
            nextDotEstimateMs = (nextDotEstimateMs * 0.92d) + (DEFAULT_DOT_MS * 0.08d);
        }

        dotEstimateMs = clampDot(nextDotEstimateMs);
        intraGapEstimateMs = gapMedianMs > 0L
                ? clampDot((gapMedianMs * 0.80d) + (dotEstimateMs * 0.20d))
                : dotEstimateMs;
        dashEstimateMs = dashMedianMs > 0L
                ? Math.max(dotEstimateMs * 2.6d, dashMedianMs)
                : Math.max(dotEstimateMs * 3.0d, dashEstimateMs * 0.85d);
    }

    private long inferDotCandidateFromTone(long toneDurationMs) {
        if (!initialized) {
            if (toneDurationMs <= FAST_BOOTSTRAP_THRESHOLD_MS) {
                return toneDurationMs;
            }
            if (toneDurationMs >= DEFAULT_DOT_MS * 1.5d) {
                return Math.max(1L, Math.round(toneDurationMs / 3.0d));
            }
            return toneDurationMs;
        }

        double directDistanceMs = Math.abs(toneDurationMs - dotEstimateMs);
        double dashDistanceMs = Math.abs((toneDurationMs / 3.0d) - dotEstimateMs);
        if (toneDurationMs >= dotEstimateMs * 1.85d && dashDistanceMs <= directDistanceMs * 1.20d) {
            return Math.max(1L, Math.round(toneDurationMs / 3.0d));
        }
        return toneDurationMs;
    }

    private CwTimingEvent.Classification classifyTone(long toneDurationMs) {
        double ratio = toneDurationMs / Math.max(1.0d, dotEstimateMs);
        if (ratio <= 1.7d) {
            return CwTimingEvent.Classification.DIT;
        }
        if (ratio <= 4.6d) {
            return CwTimingEvent.Classification.DAH;
        }
        return CwTimingEvent.Classification.UNKNOWN;
    }

    private CwTimingEvent.Classification classifyGap(long gapDurationMs) {
        double ratio = gapDurationMs / Math.max(1.0d, dotEstimateMs);
        double intraRatio = gapDurationMs / Math.max(1.0d, intraGapEstimateMs);
        if (ratio <= INTRA_GAP_MAX_RATIO) {
            return CwTimingEvent.Classification.INTRA_SYMBOL_GAP;
        }
        if (ratio >= WORD_GAP_DOT_RATIO_FALLBACK_MIN
                && intraRatio >= WORD_GAP_INTRA_RATIO_FALLBACK) {
            return CwTimingEvent.Classification.WORD_GAP;
        }
        if (ratio <= LETTER_GAP_MAX_RATIO) {
            return CwTimingEvent.Classification.LETTER_GAP;
        }
        if (ratio <= WORD_GAP_MAX_RATIO) {
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

    private boolean isFastTimingContext() {
        return dotEstimateMs <= FAST_BOOTSTRAP_THRESHOLD_MS || intraGapEstimateMs <= FAST_BOOTSTRAP_THRESHOLD_MS;
    }

    private void appendCandidate(long[] buffer, int absoluteCount, long candidateMs) {
        if (candidateMs <= 0L) {
            return;
        }
        buffer[absoluteCount % buffer.length] = candidateMs;
    }

    private long medianCandidate(long[] buffer, int absoluteCount) {
        int available = Math.min(buffer.length, Math.max(0, absoluteCount));
        if (available <= 0) {
            return 0L;
        }
        long[] copy = new long[available];
        int startIndex = Math.max(0, absoluteCount - available);
        for (int index = 0; index < available; index++) {
            copy[index] = buffer[(startIndex + index) % buffer.length];
        }
        Arrays.sort(copy);
        return copy[available / 2];
    }

    private double clampDot(double candidateMs) {
        return Math.max(MIN_DOT_MS, Math.min(MAX_DOT_MS, candidateMs));
    }

    private long dotEstimateRounded() {
        return Math.max(1L, Math.round(dotEstimateMs));
    }

    private long intraGapEstimateRounded() {
        return Math.max(1L, Math.round(intraGapEstimateMs));
    }
}
