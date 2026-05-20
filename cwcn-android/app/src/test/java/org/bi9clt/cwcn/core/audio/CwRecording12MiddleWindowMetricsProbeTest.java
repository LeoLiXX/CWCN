package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertTrue;

public final class CwRecording12MiddleWindowMetricsProbeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int[] SQL_LEVELS = new int[]{0, 5, 10, 15, 20, 25, 30, 40, 55};
    private static final long RANGE_START_MS = 8700L;
    private static final long RANGE_END_MS = 13360L;

    @Test
    public void printRecording12MiddleWindowMetrics() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(12).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for recording (12)"));
        List<AudioFrame> allFrames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );

        System.out.println("==== recording12 middle window metrics ====");
        System.out.println(String.format(
                Locale.US,
                "range=%d..%d",
                RANGE_START_MS,
                RANGE_END_MS
        ));
        for (int sqlPercent : SQL_LEVELS) {
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                    LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                            "recording12-middle-window-metrics-sql-" + sqlPercent,
                            allFrames,
                            PREFERRED_TONE_HZ,
                            SEED_WPM,
                            sqlPercent,
                            false,
                            CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                    );

            WindowMetrics metrics = computeWindowMetrics(detailed, RANGE_START_MS, RANGE_END_MS);
            System.out.println(String.format(
                    Locale.US,
                    "sql=%d frames=%d step=%d active=%d(%.1f%%) locked=%d(%.1f%%) aq=%d(%.1f%%) onFrames=%d offFrames=%d toneOn=%d toneOff=%d longestActive=%dms longestLocked=%dms meanDet=%.1f meanTone=%.1f meanDom=%.2f meanIso=%.2f",
                    sqlPercent,
                    metrics.frameCount,
                    metrics.frameStepMs,
                    metrics.activeFrameCount,
                    percent(metrics.activeFrameCount, metrics.frameCount),
                    metrics.lockedFrameCount,
                    percent(metrics.lockedFrameCount, metrics.frameCount),
                    metrics.attackQualifiedFrameCount,
                    percent(metrics.attackQualifiedFrameCount, metrics.frameCount),
                    metrics.activeTransitions,
                    metrics.inactiveTransitions,
                    metrics.toneOnEventCount,
                    metrics.toneOffEventCount,
                    metrics.longestActiveRunMs,
                    metrics.longestLockedRunMs,
                    metrics.meanDetectionLevel,
                    metrics.meanToneRms,
                    metrics.meanDominanceRatio,
                    metrics.meanIsolationRatio
            ));
        }

        assertTrue(true);
    }

    private static WindowMetrics computeWindowMetrics(
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed,
            long startInclusiveMs,
            long endExclusiveMs
    ) {
        long frameStepMs = resolveFrameStepMs(detailed.frameSignalTraces());
        int frameCount = 0;
        int activeFrameCount = 0;
        int lockedFrameCount = 0;
        int attackQualifiedFrameCount = 0;
        int activeTransitions = 0;
        int inactiveTransitions = 0;
        int currentActiveRunFrames = 0;
        int longestActiveRunFrames = 0;
        int currentLockedRunFrames = 0;
        int longestLockedRunFrames = 0;
        double detectionSum = 0.0d;
        double toneRmsSum = 0.0d;
        double dominanceSum = 0.0d;
        double isolationSum = 0.0d;
        boolean previousActive = false;

        for (LocalAudioDecodeTestSupport.FrameSignalTrace trace : detailed.frameSignalTraces()) {
            if (trace == null || trace.snapshot() == null) {
                continue;
            }
            long timestampMs = trace.timestampMs();
            if (timestampMs < startInclusiveMs) {
                previousActive = trace.snapshot().toneActive();
                continue;
            }
            if (timestampMs >= endExclusiveMs) {
                break;
            }

            CwSignalSnapshot snapshot = trace.snapshot();
            boolean active = snapshot.toneActive();
            boolean locked = snapshot.targetToneLocked();

            frameCount += 1;
            if (active) {
                activeFrameCount += 1;
                currentActiveRunFrames += 1;
                longestActiveRunFrames = Math.max(longestActiveRunFrames, currentActiveRunFrames);
            } else {
                currentActiveRunFrames = 0;
            }
            if (locked) {
                lockedFrameCount += 1;
                currentLockedRunFrames += 1;
                longestLockedRunFrames = Math.max(longestLockedRunFrames, currentLockedRunFrames);
            } else {
                currentLockedRunFrames = 0;
            }
            if (trace.attackQualified()) {
                attackQualifiedFrameCount += 1;
            }
            if (!previousActive && active) {
                activeTransitions += 1;
            }
            if (previousActive && !active) {
                inactiveTransitions += 1;
            }
            previousActive = active;

            detectionSum += trace.detectionLevel();
            toneRmsSum += snapshot.lastToneRmsAmplitude();
            dominanceSum += snapshot.toneDominanceRatio();
            isolationSum += snapshot.narrowbandIsolationRatio();
        }

        int toneOnEventCount = 0;
        int toneOffEventCount = 0;
        for (CwToneEvent toneEvent : detailed.toneEvents()) {
            if (toneEvent == null) {
                continue;
            }
            long timestampMs = toneEvent.timestampMs();
            if (timestampMs < startInclusiveMs || timestampMs >= endExclusiveMs) {
                continue;
            }
            if (toneEvent.type() == CwToneEvent.Type.TONE_ON) {
                toneOnEventCount += 1;
            } else if (toneEvent.type() == CwToneEvent.Type.TONE_OFF) {
                toneOffEventCount += 1;
            }
        }

        return new WindowMetrics(
                frameCount,
                frameStepMs,
                activeFrameCount,
                lockedFrameCount,
                attackQualifiedFrameCount,
                activeTransitions,
                inactiveTransitions,
                toneOnEventCount,
                toneOffEventCount,
                longestActiveRunFrames * frameStepMs,
                longestLockedRunFrames * frameStepMs,
                frameCount == 0 ? 0.0d : detectionSum / frameCount,
                frameCount == 0 ? 0.0d : toneRmsSum / frameCount,
                frameCount == 0 ? 0.0d : dominanceSum / frameCount,
                frameCount == 0 ? 0.0d : isolationSum / frameCount
        );
    }

    private static long resolveFrameStepMs(List<LocalAudioDecodeTestSupport.FrameSignalTrace> traces) {
        long minPositiveDeltaMs = Long.MAX_VALUE;
        long previousTimestampMs = Long.MIN_VALUE;
        for (LocalAudioDecodeTestSupport.FrameSignalTrace trace : traces) {
            if (trace == null) {
                continue;
            }
            long timestampMs = trace.timestampMs();
            if (previousTimestampMs != Long.MIN_VALUE) {
                long deltaMs = timestampMs - previousTimestampMs;
                if (deltaMs > 0L) {
                    minPositiveDeltaMs = Math.min(minPositiveDeltaMs, deltaMs);
                }
            }
            previousTimestampMs = timestampMs;
        }
        return minPositiveDeltaMs == Long.MAX_VALUE ? 16L : minPositiveDeltaMs;
    }

    private static double percent(int part, int total) {
        if (total <= 0) {
            return 0.0d;
        }
        return (part * 100.0d) / total;
    }

    private static final class WindowMetrics {
        private final int frameCount;
        private final long frameStepMs;
        private final int activeFrameCount;
        private final int lockedFrameCount;
        private final int attackQualifiedFrameCount;
        private final int activeTransitions;
        private final int inactiveTransitions;
        private final int toneOnEventCount;
        private final int toneOffEventCount;
        private final long longestActiveRunMs;
        private final long longestLockedRunMs;
        private final double meanDetectionLevel;
        private final double meanToneRms;
        private final double meanDominanceRatio;
        private final double meanIsolationRatio;

        private WindowMetrics(
                int frameCount,
                long frameStepMs,
                int activeFrameCount,
                int lockedFrameCount,
                int attackQualifiedFrameCount,
                int activeTransitions,
                int inactiveTransitions,
                int toneOnEventCount,
                int toneOffEventCount,
                long longestActiveRunMs,
                long longestLockedRunMs,
                double meanDetectionLevel,
                double meanToneRms,
                double meanDominanceRatio,
                double meanIsolationRatio
        ) {
            this.frameCount = frameCount;
            this.frameStepMs = frameStepMs;
            this.activeFrameCount = activeFrameCount;
            this.lockedFrameCount = lockedFrameCount;
            this.attackQualifiedFrameCount = attackQualifiedFrameCount;
            this.activeTransitions = activeTransitions;
            this.inactiveTransitions = inactiveTransitions;
            this.toneOnEventCount = toneOnEventCount;
            this.toneOffEventCount = toneOffEventCount;
            this.longestActiveRunMs = longestActiveRunMs;
            this.longestLockedRunMs = longestLockedRunMs;
            this.meanDetectionLevel = meanDetectionLevel;
            this.meanToneRms = meanToneRms;
            this.meanDominanceRatio = meanDominanceRatio;
            this.meanIsolationRatio = meanIsolationRatio;
        }
    }
}
