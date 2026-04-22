package org.bi9clt.cwcn.core.signal;

import org.bi9clt.cwcn.core.audio.AudioFrame;

import java.util.ArrayList;
import java.util.List;

public final class CwSignalProcessor {
    private static final int DEFAULT_PREFERRED_TONE_FREQUENCY_HZ = 650;
    private static final int MIN_TRACKED_TONE_FREQUENCY_HZ = 450;
    private static final int MAX_TRACKED_TONE_FREQUENCY_HZ = 850;
    private static final int TONE_SCAN_STEP_HZ = 10;
    private static final int RETUNE_INTERVAL_FRAMES = 4;
    private static final double MIN_LOCK_DOMINANCE_RATIO = 0.24d;
    private static final double MIN_NARROWBAND_DOMINANCE_RATIO = 0.12d;
    private static final double MIN_LOCK_ISOLATION_RATIO = 0.34d;
    private static final double MIN_NARROWBAND_ISOLATION_RATIO = 0.24d;
    private static final double LOCKED_SIGNAL_BLEND = 0.82d;
    private static final double UNLOCKED_SIGNAL_BLEND_FLOOR = 0.18d;
    private static final double UNLOCKED_TONE_GAIN = 1.18d;
    private static final int MIN_TRACKED_TONE_RMS = 120;
    private static final int MIN_THRESHOLD = 220;
    private static final int BASE_MARGIN = 140;
    private static final int EDGE_WINDOW_SAMPLES = 12;
    private static final int EDGE_CONFIRM_SAMPLES = 6;
    private static final double EDGE_THRESHOLD_RATIO = 0.30d;
    private static final double EDGE_DYNAMIC_RATIO = 0.24d;
    private static final double EDGE_TRANSITION_REQUIRED_RATIO = 0.82d;
    private static final double NOISE_FLOOR_RISE_SMOOTHING = 0.025d;
    private static final double NOISE_FLOOR_DROP_SMOOTHING = 0.14d;
    private static final double SIGNAL_FLOOR_SMOOTHING = 0.18d;
    private static final long TONE_OFF_HANG_MS = 8L;

    private boolean initialized;
    private boolean toneActive;
    private boolean targetToneLocked;
    private double noiseFloorEstimate;
    private double signalFloorEstimate;
    private double lastRmsAmplitude;
    private double lastToneRmsAmplitude;
    private double lastWidebandResidualRmsAmplitude;
    private double toneDominanceRatio;
    private double narrowbandIsolationRatio;
    private double peakToneRmsAmplitude;
    private double peakNarrowbandIsolationRatio;
    private double lastDetectionLevel;
    private long lastFrameTimestampMs = -1L;
    private long toneStartedAtMs = -1L;
    private long silenceStartedAtMs = -1L;
    private int totalToneOnEvents;
    private int totalToneOffEvents;
    private int preferredToneFrequencyHz = DEFAULT_PREFERRED_TONE_FREQUENCY_HZ;
    private int targetToneFrequencyHz = DEFAULT_PREFERRED_TONE_FREQUENCY_HZ;
    private int processedFrameCount;
    private int lockedFrameCount;
    private int consecutiveLockedFrames;
    private int maxConsecutiveLockedFrames;
    private CwToneEvent lastEvent;

    public synchronized List<CwToneEvent> process(AudioFrame frame) {
        ArrayList<CwToneEvent> events = new ArrayList<>(1);
        double frameRms = frame.rmsAmplitude();
        ToneFrequencyEstimate toneEstimate = analyzeToneFrequency(frame);
        double detectionLevel = effectiveDetectionLevel(frameRms, toneEstimate);
        boolean attackQualified = isNarrowbandQualified(toneEstimate);
        long timestampMs = frame.capturedAtMs();
        int attackThreshold = currentThreshold();
        int releaseThreshold = currentReleaseThreshold();

        if (!initialized) {
            noiseFloorEstimate = detectionLevel;
            signalFloorEstimate = detectionLevel;
            attackThreshold = currentThreshold();
            releaseThreshold = currentReleaseThreshold();
            initialized = true;
        }

        if (!toneActive) {
            noiseFloorEstimate = smoothNoiseFloor(noiseFloorEstimate, detectionLevel);
        } else {
            signalFloorEstimate = smoothSignalFloor(signalFloorEstimate, detectionLevel);
        }

        lastRmsAmplitude = frameRms;
        lastToneRmsAmplitude = toneEstimate.toneRmsAmplitude;
        toneDominanceRatio = toneEstimate.dominanceRatio;
        attackThreshold = currentThreshold();
        releaseThreshold = currentReleaseThreshold();

        if (!toneActive && attackQualified && detectionLevel >= attackThreshold) {
            long toneOnTimestampMs = estimateCrossingTimestamp(timestampMs, attackThreshold, detectionLevel, true);
            long frameLocalToneOnTimestampMs = estimateFrameLocalTransitionTimestamp(frame, true);
            if (frameLocalToneOnTimestampMs >= 0L) {
                toneOnTimestampMs = frameLocalToneOnTimestampMs;
            }
            toneActive = true;
            toneStartedAtMs = toneOnTimestampMs;
            silenceStartedAtMs = -1L;
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
                if (timestampMs - silenceStartedAtMs >= TONE_OFF_HANG_MS) {
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
                    noiseFloorEstimate = smoothNoiseFloor(noiseFloorEstimate, detectionLevel);
                    signalFloorEstimate = smoothSignalFloor(signalFloorEstimate, Math.max(detectionLevel, noiseFloorEstimate));
                }
            } else {
                silenceStartedAtMs = -1L;
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
        lastFrameTimestampMs = -1L;
        toneStartedAtMs = -1L;
        silenceStartedAtMs = -1L;
        totalToneOnEvents = 0;
        totalToneOffEvents = 0;
        processedFrameCount = 0;
        lockedFrameCount = 0;
        consecutiveLockedFrames = 0;
        maxConsecutiveLockedFrames = 0;
        targetToneFrequencyHz = preferredToneFrequencyHz;
        lastEvent = null;
    }

    public synchronized void setPreferredToneFrequencyHz(int preferredToneFrequencyHz) {
        int clamped = clampPreferredToneFrequency(preferredToneFrequencyHz);
        this.preferredToneFrequencyHz = clamped;
        if (!targetToneLocked) {
            this.targetToneFrequencyHz = clamped;
        }
    }

    public synchronized CwSignalSnapshot snapshot() {
        return new CwSignalSnapshot(
                toneActive,
                targetToneLocked,
                preferredToneFrequencyHz,
                targetToneFrequencyHz,
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
                maxConsecutiveLockedFrames,
                totalToneOnEvents,
                totalToneOffEvents,
                lastEvent
        );
    }

    private int currentThreshold() {
        double noise = Math.max(0.0d, noiseFloorEstimate);
        double signalDelta = Math.max(0.0d, signalFloorEstimate - noise);
        double margin = Math.max(BASE_MARGIN, Math.max(noise * 0.18d, signalDelta * 0.30d));
        return Math.max(MIN_THRESHOLD, (int) Math.round(noise + margin));
    }

    private int currentReleaseThreshold() {
        double noise = Math.max(0.0d, noiseFloorEstimate);
        double attackThreshold = currentThreshold();
        double pullback = Math.max(BASE_MARGIN * 0.45d, (attackThreshold - noise) * 0.52d);
        return Math.max(MIN_THRESHOLD, (int) Math.round(noise + pullback));
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
        lastFrameTimestampMs = timestampMs;
        lastDetectionLevel = detectionLevel;
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

    private ToneFrequencyEstimate analyzeToneFrequency(AudioFrame frame) {
        processedFrameCount += 1;
        short[] samples = frame.samples();
        if (samples == null || samples.length == 0 || frame.sampleRateHz() <= 0) {
            targetToneLocked = false;
            lastToneRmsAmplitude = 0.0d;
            lastWidebandResidualRmsAmplitude = 0.0d;
            toneDominanceRatio = 0.0d;
            narrowbandIsolationRatio = 0.0d;
            rememberSignalQuality(0.0d, 0.0d, false, false);
            return new ToneFrequencyEstimate(targetToneFrequencyHz, 0.0d, 0.0d, 0.0d, 0.0d, false);
        }

        boolean shouldRetune = !targetToneLocked || processedFrameCount == 1
                || (processedFrameCount % RETUNE_INTERVAL_FRAMES) == 0;
        if (shouldRetune) {
            ToneFrequencyEstimate scanned = scanPreferredToneWindow(samples, frame.sampleRateHz(), frame.rmsAmplitude());
            if (scanned.locked) {
                targetToneFrequencyHz = scanned.frequencyHz;
                targetToneLocked = true;
            } else if (!targetToneLocked) {
                targetToneFrequencyHz = preferredToneFrequencyHz;
            }
        }

        double trackedToneRms = estimateToneRms(samples, frame.sampleRateHz(), targetToneFrequencyHz);
        double widebandResidualRms = estimateWidebandResidualRms(frame.rmsAmplitude(), trackedToneRms);
        double dominanceRatio = trackedToneRms <= 0.0d || frame.rmsAmplitude() <= 0.0d
                ? 0.0d
                : Math.min(1.0d, trackedToneRms / frame.rmsAmplitude());
        double isolationRatio = computeNarrowbandIsolationRatio(trackedToneRms, widebandResidualRms);
        lastWidebandResidualRmsAmplitude = widebandResidualRms;
        narrowbandIsolationRatio = isolationRatio;
        boolean narrowbandQualified = trackedToneRms >= MIN_TRACKED_TONE_RMS
                && dominanceRatio >= MIN_NARROWBAND_DOMINANCE_RATIO
                && isolationRatio >= MIN_NARROWBAND_ISOLATION_RATIO;
        boolean stillLocked = trackedToneRms >= MIN_TRACKED_TONE_RMS
                && dominanceRatio >= (MIN_LOCK_DOMINANCE_RATIO * 0.75d)
                && isolationRatio >= (MIN_LOCK_ISOLATION_RATIO * 0.80d);
        targetToneLocked = targetToneLocked && stillLocked;
        rememberSignalQuality(trackedToneRms, isolationRatio, targetToneLocked, narrowbandQualified);
        return new ToneFrequencyEstimate(
                targetToneFrequencyHz,
                trackedToneRms,
                widebandResidualRms,
                dominanceRatio,
                isolationRatio,
                targetToneLocked
        );
    }

    private ToneFrequencyEstimate scanPreferredToneWindow(short[] samples, int sampleRateHz, double frameRms) {
        int searchMin = Math.max(MIN_TRACKED_TONE_FREQUENCY_HZ, preferredToneFrequencyHz - 160);
        int searchMax = Math.min(MAX_TRACKED_TONE_FREQUENCY_HZ, preferredToneFrequencyHz + 160);
        int bestFrequencyHz = preferredToneFrequencyHz;
        double bestToneRms = 0.0d;
        double bestWeightedScore = 0.0d;
        for (int frequencyHz = searchMin; frequencyHz <= searchMax; frequencyHz += TONE_SCAN_STEP_HZ) {
            double toneRms = estimateToneRms(samples, sampleRateHz, frequencyHz);
            double weightedScore = toneRms * preferredFrequencyWeight(frequencyHz);
            if (weightedScore > bestWeightedScore) {
                bestWeightedScore = weightedScore;
                bestToneRms = toneRms;
                bestFrequencyHz = frequencyHz;
            }
        }
        double dominanceRatio = bestToneRms <= 0.0d || frameRms <= 0.0d
                ? 0.0d
                : Math.min(1.0d, bestToneRms / frameRms);
        double widebandResidualRms = estimateWidebandResidualRms(frameRms, bestToneRms);
        double isolationRatio = computeNarrowbandIsolationRatio(bestToneRms, widebandResidualRms);
        boolean locked = bestToneRms >= MIN_TRACKED_TONE_RMS
                && dominanceRatio >= MIN_LOCK_DOMINANCE_RATIO
                && isolationRatio >= MIN_LOCK_ISOLATION_RATIO;
        return new ToneFrequencyEstimate(
                bestFrequencyHz,
                bestToneRms,
                widebandResidualRms,
                dominanceRatio,
                isolationRatio,
                locked
        );
    }

    private double preferredFrequencyWeight(int candidateFrequencyHz) {
        int distanceHz = Math.abs(candidateFrequencyHz - preferredToneFrequencyHz);
        return Math.max(0.25d, 1.0d - (distanceHz / 180.0d));
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
        return Math.max(
                UNLOCKED_SIGNAL_BLEND_FLOOR,
                Math.min(1.0d, (dominanceConfidence * 0.58d) + (isolationConfidence * 0.42d))
        );
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
        }
        if (narrowbandQualified) {
            peakToneRmsAmplitude = Math.max(peakToneRmsAmplitude, toneRmsAmplitude);
            peakNarrowbandIsolationRatio = Math.max(peakNarrowbandIsolationRatio, isolationRatio);
        }
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

    private double estimateWidebandResidualRms(double frameRms, double toneRmsAmplitude) {
        double framePower = Math.max(0.0d, frameRms * frameRms);
        double tonePower = Math.max(0.0d, toneRmsAmplitude * toneRmsAmplitude);
        return Math.sqrt(Math.max(0.0d, framePower - tonePower));
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

    private static final class ToneFrequencyEstimate {
        private final int frequencyHz;
        private final double toneRmsAmplitude;
        private final double widebandResidualRmsAmplitude;
        private final double dominanceRatio;
        private final double isolationRatio;
        private final boolean locked;

        private ToneFrequencyEstimate(
                int frequencyHz,
                double toneRmsAmplitude,
                double widebandResidualRmsAmplitude,
                double dominanceRatio,
                double isolationRatio,
                boolean locked
        ) {
            this.frequencyHz = frequencyHz;
            this.toneRmsAmplitude = toneRmsAmplitude;
            this.widebandResidualRmsAmplitude = widebandResidualRmsAmplitude;
            this.dominanceRatio = dominanceRatio;
            this.isolationRatio = isolationRatio;
            this.locked = locked;
        }
    }
}
