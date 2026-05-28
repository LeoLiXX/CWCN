package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel.DebugSnapshot;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class CwDirtyCase222505TimingStateProbeTest {
    private static final int PREFERRED_TONE_HZ = 760;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;

    @Test
    public void print222505TimingStateProfile() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> {
                    String fileName = path.getFileName().toString();
                    return fileName.equalsIgnoreCase("20260427_222505.wav")
                            || fileName.endsWith("20260427_222505.wav");
                })
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 20260427_222505"));

        List<AudioFrame> frames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "20260427_222505-timing-state",
                        frames,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        System.out.println("==== 20260427_222505 timing state profile ====");
        System.out.println(String.format(
                Locale.US,
                "final=%s rawWpm=%.1f stableWpm=%.1f trust=%d",
                sanitize(detailed.probeResult().decodedText()),
                detailed.probeResult().timingSnapshot().estimatedWpmPrecise(),
                detailed.probeResult().timingSnapshot().estimatedWpmPrecise(),
                firstTrustTimestampMs(detailed.timingStateTraces())
        ));
        printInterestingTransitions(detailed.timingStateTraces());
    }

    private static void printInterestingTransitions(
            List<LocalAudioDecodeTestSupport.TimingStateTrace> traces
    ) {
        LocalAudioDecodeTestSupport.TimingStateTrace previous = null;
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : traces) {
            if (trace == null || trace.debugSnapshot() == null) {
                continue;
            }
            if (!isInteresting(previous, trace)) {
                previous = trace;
                continue;
            }
            DebugSnapshot debug = trace.debugSnapshot();
            System.out.println(String.format(
                    Locale.US,
                    "@%dms act=%s emit=%s raw=%.1f stable=%.1f trustDot=%.1f retain=%.1f floor=%s strategy=%s trust=%s obs=%s",
                    trace.timestampMs(),
                    safe(debug.activeStrategyName()),
                    safe(debug.lastEmissionStrategyName()),
                    trace.rawSnapshot() == null ? 0.0d : trace.rawSnapshot().estimatedWpmPrecise(),
                    trace.stabilizedSnapshot() == null ? 0.0d : trace.stabilizedSnapshot().estimatedWpmPrecise(),
                    debug.trustedDotEstimateMs(),
                    debug.retainedDotEstimateMs(),
                    yesNo(debug.trustedFastFloorActive()),
                    safe(debug.lastStrategyDecision()),
                    safe(debug.lastTrustedUpdateReason()),
                    safe(debug.lastObservationSummary())
            ));
            previous = trace;
        }
    }

    private static boolean isInteresting(
            LocalAudioDecodeTestSupport.TimingStateTrace previous,
            LocalAudioDecodeTestSupport.TimingStateTrace current
    ) {
        if (current == null || current.debugSnapshot() == null) {
            return false;
        }
        if (previous == null || previous.debugSnapshot() == null) {
            return true;
        }
        DebugSnapshot before = previous.debugSnapshot();
        DebugSnapshot after = current.debugSnapshot();
        if (!safe(before.lastStrategyDecision()).equals(safe(after.lastStrategyDecision()))) {
            return true;
        }
        if (!safe(before.lastTrustedUpdateReason()).equals(safe(after.lastTrustedUpdateReason()))) {
            return true;
        }
        if (Math.abs((current.rawSnapshot() == null ? 0.0d : current.rawSnapshot().estimatedWpmPrecise())
                - (previous.rawSnapshot() == null ? 0.0d : previous.rawSnapshot().estimatedWpmPrecise())) >= 1.0d) {
            return true;
        }
        return before.trustedFastFloorActive() != after.trustedFastFloorActive();
    }

    private static long firstTrustTimestampMs(List<LocalAudioDecodeTestSupport.TimingStateTrace> traces) {
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : traces) {
            if (trace != null
                    && trace.debugSnapshot() != null
                    && trace.debugSnapshot().trustedDotEstimateMs() > 0.0d) {
                return trace.timestampMs();
            }
        }
        return -1L;
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "(empty)";
        }
        String normalized = text.replace('\u25A1', '?').replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.isEmpty() ? "(empty)" : normalized;
    }

    private static String safe(String value) {
        return value == null || value.isEmpty() ? "(none)" : value;
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }
}
