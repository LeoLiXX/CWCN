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
    private static final double LOCKED_SIGNAL_BLEND = 0.82d;
    private static final double UNLOCKED_SIGNAL_BLEND_FLOOR = 0.18d;
    private static final double UNLOCKED_TONE_GAIN = 1.18d;
    private static final int MIN_TRACKED_TONE_RMS = 120;
    private static final int MIN_THRESHOLD = 220;
    private static final int BASE_MARGIN = 140;
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
    private double toneDominanceRatio;
    private double lastDetectionLevel;
    private long lastFrameTimestampMs = -1L;
    private long toneStartedAtMs = -1L;
    private long silenceStartedAtMs = -1L;
    private int totalToneOnEvents;
    private int totalToneOffEvents;
    private int preferredToneFrequencyHz = DEFAULT_PREFERRED_TONE_FREQUENCY_HZ;
    private int targetToneFrequencyHz = DEFAULT_PREFERRED_TONE_FREQUENCY_HZ;
    private int processedFrameCount;
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
                    silenceStartedAtMs = estimateCrossingTimestamp(timestampMs, releaseThreshold, detectionLevel, false);
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
        toneDominanceRatio = 0.0d;
        lastDetectionLevel = 0.0d;
        lastFrameTimestampMs = -1L;
        toneStartedAtMs = -1L;
        silenceStartedAtMs = -1L;
        totalToneOnEvents = 0;
        totalToneOffEvents = 0;
        processedFrameCount = 0;
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
                toneDominanceRatio,
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

    private ToneFrequencyEstimate analyzeToneFrequency(AudioFrame frame) {
        processedFrameCount += 1;
        short[] samples = frame.samples();
        if (samples == null || samples.length == 0 || frame.sampleRateHz() <= 0) {
            targetToneLocked = false;
            lastToneRmsAmplitude = 0.0d;
            toneDominanceRatio = 0.0d;
            return new ToneFrequencyEstimate(targetToneFrequencyHz, 0.0d, 0.0d, false);
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
        double dominanceRatio = trackedToneRms <= 0.0d || frame.rmsAmplitude() <= 0.0d
                ? 0.0d
                : Math.min(1.0d, trackedToneRms / frame.rmsAmplitude());
        boolean stillLocked = trackedToneRms >= MIN_TRACKED_TONE_RMS && dominanceRatio >= (MIN_LOCK_DOMINANCE_RATIO * 0.75d);
        targetToneLocked = targetToneLocked && stillLocked;
        return new ToneFrequencyEstimate(targetToneFrequencyHz, trackedToneRms, dominanceRatio, targetToneLocked);
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
        boolean locked = bestToneRms >= MIN_TRACKED_TONE_RMS && dominanceRatio >= MIN_LOCK_DOMINANCE_RATIO;
        return new ToneFrequencyEstimate(bestFrequencyHz, bestToneRms, dominanceRatio, locked);
    }

    private double preferredFrequencyWeight(int candidateFrequencyHz) {
        int distanceHz = Math.abs(candidateFrequencyHz - preferredToneFrequencyHz);
        return Math.max(0.25d, 1.0d - (distanceHz / 180.0d));
    }

    private double effectiveDetectionLevel(double frameRms, ToneFrequencyEstimate toneEstimate) {
        if (toneEstimate == null) {
            return frameRms;
        }
        if (toneEstimate.locked) {
            return Math.max(
                    toneEstimate.toneRmsAmplitude,
                    frameRms * (1.0d - ((1.0d - toneEstimate.dominanceRatio) * LOCKED_SIGNAL_BLEND))
            );
        }
        if (isNarrowbandQualified(toneEstimate)) {
            return Math.max(
                    toneEstimate.toneRmsAmplitude * UNLOCKED_TONE_GAIN,
                    frameRms * Math.max(UNLOCKED_SIGNAL_BLEND_FLOOR, toneEstimate.dominanceRatio)
            );
        }
        return toneEstimate.toneRmsAmplitude;
    }

    private boolean isNarrowbandQualified(ToneFrequencyEstimate toneEstimate) {
        return toneEstimate != null
                && toneEstimate.toneRmsAmplitude >= MIN_TRACKED_TONE_RMS
                && toneEstimate.dominanceRatio >= MIN_NARROWBAND_DOMINANCE_RATIO;
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
        private final double dominanceRatio;
        private final boolean locked;

        private ToneFrequencyEstimate(
                int frequencyHz,
                double toneRmsAmplitude,
                double dominanceRatio,
                boolean locked
        ) {
            this.frequencyHz = frequencyHz;
            this.toneRmsAmplitude = toneRmsAmplitude;
            this.dominanceRatio = dominanceRatio;
            this.locked = locked;
        }
    }
}
