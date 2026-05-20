package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertTrue;

public final class CwRecording12QsbDiagnosisProbeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;
    private static final long PRINT_STEP_MS = 128L;
    private static final long MIN_WINDOW_SPAN_MS = 7200L;
    private static final long POST_TRUST_TAIL_MS = 1400L;

    @Test
    public void printRecording12HybridVsFixedQsbWindow() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(12).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (12)"));
        List<AudioFrame> frames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );

        System.out.println("==== recording12 qsb diagnosis ====");
        printCase(
                "HYBRID_BOOTSTRAP",
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "recording12-hybrid-qsb",
                        frames,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                )
        );
        printCase(
                "HYBRID_RELEASE_HOLD",
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "recording12-hybrid-release-hold-qsb",
                        frames,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        false,
                        false,
                        false,
                        true,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                )
        );
        printCase(
                "STATIC_FIXED_TONE",
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeWithTurnCarry(
                        "recording12-fixed-qsb",
                        frames,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        CwSignalProcessor.RxToneMode.FIXED_TONE,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                )
        );

        assertTrue(true);
    }

    private static void printCase(
            String label,
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed
    ) {
        long turnStartMs = firstTurnStartMs(detailed.turnTransitionTraces());
        long trustMs = firstTrustTimestampMs(detailed.timingStateTraces());
        long windowStartMs = turnStartMs >= 0L
                ? turnStartMs
                : detailed.frames().isEmpty() ? 0L : detailed.frames().get(0).capturedAtMs();
        long windowEndMs = resolveWindowEndMs(windowStartMs, trustMs, detailed.flushTimestampMs());

        System.out.println("-- " + label + " --");
        System.out.println(String.format(
                Locale.US,
                "final=%s chars=%d trust=%d window=%d..%d tone=%d/%d/%d wpm=%d rejects=%s",
                sanitize(detailed.probeResult().decodedText()),
                detailed.probeResult().decoderSnapshot().totalCharacters(),
                trustMs,
                windowStartMs,
                windowEndMs,
                detailed.probeResult().signalSnapshot().targetToneFrequencyHz(),
                detailed.probeResult().signalSnapshot().effectiveAcquisitionWinnerFrequencyHz(),
                detailed.probeResult().signalSnapshot().effectiveFinalAdoptedFrequencyHz(),
                detailed.probeResult().timingSnapshot().estimatedWpm(),
                detailed.stableRejectCounts()
        ));

        long nextPrintAtMs = Long.MIN_VALUE;
        String lastStateKey = null;
        for (LocalAudioDecodeTestSupport.FrameSignalTrace trace : detailed.frameSignalTraces()) {
            if (trace == null || trace.snapshot() == null) {
                continue;
            }
            long timestampMs = trace.timestampMs();
            if (timestampMs < windowStartMs) {
                continue;
            }
            if (timestampMs > windowEndMs) {
                break;
            }
            CwSignalSnapshot snapshot = trace.snapshot();
            LocalAudioDecodeTestSupport.RxToneModeDecisionTrace modeTrace =
                    latestModeTraceAtOrBefore(detailed.rxToneModeDecisionTraces(), timestampMs);
            String stateKey = buildStateKey(snapshot, trace, modeTrace);
            boolean shouldPrint = lastStateKey == null
                    || !stateKey.equals(lastStateKey)
                    || timestampMs >= nextPrintAtMs;
            if (!shouldPrint) {
                continue;
            }

            System.out.println(String.format(
                    Locale.US,
                    "@%d mode=%s latched=%s trust=%s eligible=%s progress=%s det=%.1f on=%s lock=%s cons=%d lockR=%.2f tgt=%d eff=%d aq=%d final=%d hyp=%d(%.2f) pending=%d@%d guard=%s %d/%d drift=%d dom=%.2f iso=%.2f thr=%d/%d floor=%d/%d tone=%.1f",
                    timestampMs,
                    modeTrace == null ? "?" : modeTrace.resolvedMode(),
                    yesNo(modeTrace != null && modeTrace.fallbackLatched()),
                    yesNo(modeTrace != null && modeTrace.trustedTimingEstablished()),
                    yesNo(modeTrace != null && modeTrace.eligibleForPreTrustFallback()),
                    yesNo(modeTrace != null && modeTrace.usefulFixedProgress()),
                    trace.detectionLevel(),
                    yesNo(snapshot.toneActive()),
                    yesNo(snapshot.targetToneLocked()),
                    snapshot.consecutiveLockedFrames(),
                    snapshot.recentLockedFrameRatio(),
                    snapshot.targetToneFrequencyHz(),
                    snapshot.effectiveTrackedToneFrequencyHz(),
                    snapshot.effectiveAcquisitionWinnerFrequencyHz(),
                    snapshot.effectiveFinalAdoptedFrequencyHz(),
                    snapshot.toneHypothesisFrequencyHz(),
                    snapshot.toneHypothesisConfidence(),
                    snapshot.pendingRetuneCandidateStableScans(),
                    snapshot.pendingRetuneCandidateFrequencyHz(),
                    yesNo(snapshot.lockedRetuneGuardHolding()),
                    snapshot.lockedRetuneGuardObservedScans(),
                    snapshot.lockedRetuneGuardRequiredScans(),
                    snapshot.lockedRetuneGuardDriftHz(),
                    snapshot.toneDominanceRatio(),
                    snapshot.narrowbandIsolationRatio(),
                    snapshot.currentThreshold(),
                    snapshot.releaseThreshold(),
                    snapshot.noiseFloorEstimate(),
                    snapshot.signalFloorEstimate(),
                    snapshot.lastToneRmsAmplitude()
            ));
            System.out.println("src aq=" + compact(snapshot.acquisitionWinnerSource())
                    + " final=" + compact(snapshot.finalAdoptedSource())
                    + " aqDetail=" + compact(snapshot.acquisitionDecisionDetail())
                    + " finalDetail=" + compact(snapshot.finalAdoptionDetail()));
            System.out.println("cand pref=" + compact(snapshot.preferredWindowTopCandidatesSummary()));
            System.out.println("cand wide=" + compact(snapshot.wideScanTopCandidatesSummary()));
            System.out.println("front toneOn=" + compact(trace.toneOnDecision())
                    + " rescue=" + compact(trace.postReleaseRescueDecision())
                    + " suppress=" + compact(trace.postReleaseSuppressionDecision())
                    + " release=" + compact(trace.releaseTailHoldDecision())
                    + " attack=" + compact(trace.farAttackDelayDecision()));

            lastStateKey = stateKey;
            nextPrintAtMs = timestampMs + PRINT_STEP_MS;
        }
    }

    private static long firstTurnStartMs(List<LocalAudioDecodeTestSupport.TurnTransitionTrace> traces) {
        for (LocalAudioDecodeTestSupport.TurnTransitionTrace trace : traces) {
            if (trace != null
                    && trace.kind() == LocalAudioDecodeTestSupport.TurnTransitionTrace.Kind.START) {
                return trace.timestampMs();
            }
        }
        return -1L;
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

    private static long resolveWindowEndMs(long windowStartMs, long trustMs, long flushTimestampMs) {
        long trustTailMs = trustMs < 0L
                ? windowStartMs + MIN_WINDOW_SPAN_MS
                : Math.max(windowStartMs + MIN_WINDOW_SPAN_MS, trustMs + POST_TRUST_TAIL_MS);
        return Math.min(flushTimestampMs, trustTailMs);
    }

    private static String buildStateKey(
            CwSignalSnapshot snapshot,
            LocalAudioDecodeTestSupport.FrameSignalTrace trace,
            LocalAudioDecodeTestSupport.RxToneModeDecisionTrace modeTrace
    ) {
        return (modeTrace == null ? "?" : modeTrace.resolvedMode().name())
                + "|" + (modeTrace != null && modeTrace.fallbackLatched())
                + "|" + (modeTrace != null && modeTrace.trustedTimingEstablished())
                + "|" + (modeTrace != null && modeTrace.eligibleForPreTrustFallback())
                + "|" + (modeTrace != null && modeTrace.usefulFixedProgress())
                + "|" + snapshot.targetToneLocked()
                + "|" + snapshot.toneActive()
                + "|" + snapshot.effectiveTrackedToneFrequencyHz()
                + "|" + snapshot.effectiveAcquisitionWinnerFrequencyHz()
                + "|" + snapshot.effectiveFinalAdoptedFrequencyHz()
                + "|" + snapshot.pendingRetuneCandidateStableScans()
                + "|" + snapshot.pendingRetuneCandidateFrequencyHz()
                + "|" + snapshot.lockedRetuneGuardHolding()
                + "|" + snapshot.lockedRetuneGuardObservedScans()
                + "|" + snapshot.lockedRetuneGuardRequiredScans()
                + "|" + compact(snapshot.acquisitionWinnerSource())
                + "|" + compact(snapshot.finalAdoptedSource())
                + "|" + compact(snapshot.acquisitionDecisionDetail())
                + "|" + compact(snapshot.finalAdoptionDetail())
                + "|" + compact(trace.toneOnDecision())
                + "|" + compact(trace.postReleaseRescueDecision())
                + "|" + compact(trace.postReleaseSuppressionDecision())
                + "|" + compact(trace.releaseTailHoldDecision())
                + "|" + compact(trace.farAttackDelayDecision());
    }

    private static LocalAudioDecodeTestSupport.RxToneModeDecisionTrace latestModeTraceAtOrBefore(
            List<LocalAudioDecodeTestSupport.RxToneModeDecisionTrace> traces,
            long timestampMs
    ) {
        LocalAudioDecodeTestSupport.RxToneModeDecisionTrace latest = null;
        for (LocalAudioDecodeTestSupport.RxToneModeDecisionTrace trace : traces) {
            if (trace == null || trace.timestampMs() > timestampMs) {
                break;
            }
            latest = trace;
        }
        return latest;
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "(empty)";
        }
        String normalized = text.replace('\u25A1', '?').replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.isEmpty() ? "(empty)" : normalized;
    }

    private static String compact(String text) {
        if (text == null) {
            return "NONE";
        }
        String normalized = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.isEmpty()) {
            return "NONE";
        }
        if (normalized.length() <= 160) {
            return normalized;
        }
        return normalized.substring(0, 157) + "...";
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }
}
