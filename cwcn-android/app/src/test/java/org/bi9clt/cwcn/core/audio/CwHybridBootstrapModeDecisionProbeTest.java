package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertTrue;

public final class CwHybridBootstrapModeDecisionProbeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;
    private static final long PRE_TRUST_FALLBACK_DELAY_MS = 48L;
    private static final double PROGRESS_LOCKED_RATIO_MIN = 0.30d;
    private static final int PROGRESS_CONSECUTIVE_LOCKED_FRAMES_MIN = 4;
    private static final long OPENING_WINDOW_MS = 2600L;
    private static final long PRINT_STEP_MS = 96L;

    @Test
    public void printRecording2AndRecording3BootstrapModeDecisionWindows() throws Exception {
        printSource("recording2", findRecordingWav("(2).wav"));
        printSource("recording3", findRecordingWav("(3).wav"));
        printSource("capture", findCaptureWav());
        assertTrue(true);
    }

    private static void printSource(String label, Path wavFile) throws Exception {
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                        wavFile,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        long turnStartMs = firstTurnStartMs(detailed.turnTransitionTraces());
        long trustMs = firstTrustMs(detailed.timingStateTraces());
        long windowEndMs = resolveWindowEndMs(turnStartMs, trustMs, detailed.flushTimestampMs());

        System.out.println("==== hybrid bootstrap decision: " + label + " ====");
        System.out.println(String.format(
                Locale.US,
                "final=%s turnStart=%d trust=%d trustOffset=%d windowEnd=%d",
                sanitize(detailed.probeResult().decodedText()),
                turnStartMs,
                trustMs,
                trustMs < 0L || turnStartMs < 0L ? -1L : Math.max(0L, trustMs - turnStartMs),
                windowEndMs
        ));

        long nextPrintAtMs = Long.MIN_VALUE;
        Boolean lastFallback = null;
        for (LocalAudioDecodeTestSupport.FrameSignalTrace trace : detailed.frameSignalTraces()) {
            if (trace == null || trace.snapshot() == null) {
                continue;
            }
            long timestampMs = trace.timestampMs();
            if (turnStartMs >= 0L && timestampMs < turnStartMs) {
                continue;
            }
            if (timestampMs > windowEndMs) {
                break;
            }
            CwSignalSnapshot snapshot = trace.snapshot();
            boolean usefulProgress = showsUsefulFixedToneBootstrapProgress(snapshot);
            boolean eligibleForFallback = turnStartMs >= 0L
                    && timestampMs >= (turnStartMs + PRE_TRUST_FALLBACK_DELAY_MS)
                    && !usefulProgress;

            boolean shouldPrint = lastFallback == null
                    || eligibleForFallback != lastFallback
                    || timestampMs >= nextPrintAtMs;
            if (!shouldPrint) {
                continue;
            }

            int acquisitionHz = snapshot.effectiveAcquisitionWinnerFrequencyHz();
            int adoptedHz = snapshot.effectiveFinalAdoptedFrequencyHz();
            int hypothesisHz = snapshot.toneHypothesisFrequencyHz();
            System.out.println(String.format(
                    Locale.US,
                    "@%d fallback=%s progress=%s lock=%s cons=%d lockR=%.2f"
                            + " tgt=%d eff=%d aq=%d(%+d) final=%d(%+d) hyp=%d(%+d) conf=%.2f"
                            + " dom=%.2f iso=%.2f on=%d off=%d",
                    timestampMs,
                    yesNo(eligibleForFallback),
                    usefulProgress ? progressReason(snapshot) : "none",
                    yesNo(snapshot.targetToneLocked()),
                    snapshot.consecutiveLockedFrames(),
                    snapshot.recentLockedFrameRatio(),
                    snapshot.targetToneFrequencyHz(),
                    snapshot.effectiveTrackedToneFrequencyHz(),
                    acquisitionHz,
                    offsetFromPreferred(acquisitionHz),
                    adoptedHz,
                    offsetFromPreferred(adoptedHz),
                    hypothesisHz,
                    offsetFromPreferred(hypothesisHz),
                    snapshot.toneHypothesisConfidence(),
                    snapshot.toneDominanceRatio(),
                    snapshot.narrowbandIsolationRatio(),
                    snapshot.totalToneOnEvents(),
                    snapshot.totalToneOffEvents()
            ));

            lastFallback = eligibleForFallback;
            nextPrintAtMs = timestampMs + PRINT_STEP_MS;
        }
    }

    private static Path findRecordingWav(String suffix) throws Exception {
        return LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith(suffix))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for " + suffix));
    }

    private static Path findCaptureWav() {
        Path captureWav = LocalAudioDecodeTestSupport.findTraceDir().resolve("capture.wav");
        if (!Files.isRegularFile(captureWav)) {
            throw new IllegalStateException("Missing captured trace wav: " + captureWav);
        }
        return captureWav;
    }

    private static long firstTurnStartMs(List<LocalAudioDecodeTestSupport.TurnTransitionTrace> traces) {
        for (LocalAudioDecodeTestSupport.TurnTransitionTrace trace : traces) {
            if (trace != null && trace.kind() == LocalAudioDecodeTestSupport.TurnTransitionTrace.Kind.START) {
                return trace.timestampMs();
            }
        }
        return -1L;
    }

    private static long firstTrustMs(List<LocalAudioDecodeTestSupport.TimingStateTrace> traces) {
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : traces) {
            if (trace != null
                    && trace.debugSnapshot() != null
                    && trace.debugSnapshot().trustedDotEstimateMs() > 0.0d) {
                return trace.timestampMs();
            }
        }
        return -1L;
    }

    private static long resolveWindowEndMs(long turnStartMs, long trustMs, long flushTimestampMs) {
        long baseline = flushTimestampMs;
        if (turnStartMs >= 0L) {
            baseline = Math.min(baseline, turnStartMs + OPENING_WINDOW_MS);
        }
        if (trustMs >= 0L) {
            baseline = Math.min(baseline, trustMs);
        }
        return baseline;
    }

    private static boolean showsUsefulFixedToneBootstrapProgress(CwSignalSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        return snapshot.targetToneLocked()
                || snapshot.consecutiveLockedFrames() >= PROGRESS_CONSECUTIVE_LOCKED_FRAMES_MIN
                || snapshot.recentLockedFrameRatio() >= PROGRESS_LOCKED_RATIO_MIN;
    }

    private static String progressReason(CwSignalSnapshot snapshot) {
        if (snapshot == null) {
            return "none";
        }
        if (snapshot.targetToneLocked()) {
            return "target-locked";
        }
        if (snapshot.consecutiveLockedFrames() >= PROGRESS_CONSECUTIVE_LOCKED_FRAMES_MIN) {
            return "cons-locked";
        }
        if (snapshot.recentLockedFrameRatio() >= PROGRESS_LOCKED_RATIO_MIN) {
            return "lock-ratio";
        }
        return "none";
    }

    private static int offsetFromPreferred(int frequencyHz) {
        if (frequencyHz <= 0) {
            return 0;
        }
        return frequencyHz - PREFERRED_TONE_HZ;
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "(empty)";
        }
        String normalized = text.replace('\u25A1', '?').replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.isEmpty() ? "(empty)" : normalized;
    }
}
