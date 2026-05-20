package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertTrue;

public final class CwRecording7BootstrapProbeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;
    private static final long OPENING_WINDOW_MS = 3200L;
    private static final long PRINT_STEP_MS = 96L;
    private static final int MAX_EVENT_LINES = 20;

    @Test
    public void printRecording7HybridVsFixedBootstrapWindow() throws Exception {
        Path wavFile = requireRecordingWav("(7).wav");
        List<AudioFrame> frames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );

        System.out.println("==== recording7 bootstrap diagnosis ====");
        printModeCases("recording7", frames);

        assertTrue(true);
    }

    @Test
    public void printRecording3HybridVsFixedBootstrapWindow() throws Exception {
        Path wavFile = requireRecordingWav("(3).wav");
        List<AudioFrame> frames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );

        System.out.println("==== recording3 bootstrap diagnosis ====");
        printModeCases("recording3", frames);

        assertTrue(true);
    }

    private static void printModeCases(String label, List<AudioFrame> frames) {
        printCase(
                "HYBRID_BOOTSTRAP",
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        label + "-hybrid",
                        frames,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                )
        );
        printCase(
                "STATIC_FIXED_TONE",
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeWithTurnCarry(
                        label + "-fixed",
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
        printCase(
                "FIXED_UNTIL_TRUST_THEN_AUTO",
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeFixedUntilTrustThenAuto(
                        label + "-fixed-until-trust",
                        frames,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                )
        );
        printCase(
                "STATIC_AUTO_TRACK",
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeWithTurnCarry(
                        label + "-auto",
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
                        CwSignalProcessor.RxToneMode.AUTO_TRACK,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                )
        );

    }

    private static Path requireRecordingWav(String suffix) throws Exception {
        return LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith(suffix))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for " + suffix));
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

        String rawBeforeWindow = textAtOrBefore(detailed.rawDecodeEvents(), Math.max(0L, windowStartMs - 1L));
        String stableBeforeWindow = textAtOrBefore(
                detailed.stableAcceptedDecodeEvents(),
                Math.max(0L, windowStartMs - 1L)
        );
        String rawOpening = sliceNewText(rawBeforeWindow, textAtOrBefore(detailed.rawDecodeEvents(), windowEndMs));
        String stableOpening = sliceNewText(
                stableBeforeWindow,
                textAtOrBefore(detailed.stableAcceptedDecodeEvents(), windowEndMs)
        );

        System.out.println("-- " + label + " --");
        System.out.println(String.format(
                Locale.US,
                "final=%s trust=%d trustOffset=%d window=%d..%d chars=%d tone=%d/%d/%d wpm=%d stableRejects=%s boundaryRejects=%s cadenceRejects=%s",
                sanitize(detailed.probeResult().decodedText()),
                trustMs,
                trustMs < 0L || turnStartMs < 0L ? -1L : Math.max(0L, trustMs - turnStartMs),
                windowStartMs,
                windowEndMs,
                detailed.probeResult().decoderSnapshot().totalCharacters(),
                detailed.probeResult().signalSnapshot().targetToneFrequencyHz(),
                detailed.probeResult().signalSnapshot().effectiveAcquisitionWinnerFrequencyHz(),
                detailed.probeResult().signalSnapshot().effectiveFinalAdoptedFrequencyHz(),
                detailed.probeResult().timingSnapshot().estimatedWpm(),
                detailed.stableRejectCounts(),
                detailed.bootstrapBoundaryRejectCounts(),
                detailed.bootstrapCadenceRejectCounts()
        ));
        System.out.println("opening raw=" + sanitize(rawOpening));
        System.out.println("opening stable=" + sanitize(stableOpening));
        System.out.println("-- mode/front-end window --");
        printModeAndFrontEndWindow(detailed, windowStartMs, windowEndMs);
        System.out.println("-- raw-decode opening --");
        printDecodeEventsInWindow(detailed.rawDecodeEvents(), windowStartMs, windowEndMs, MAX_EVENT_LINES);
        System.out.println("-- stable-decode opening --");
        printDecodeEventsInWindow(
                detailed.stableAcceptedDecodeEvents(),
                windowStartMs,
                windowEndMs,
                MAX_EVENT_LINES
        );
        System.out.println("-- bootstrap-boundary opening --");
        printBootstrapDecisionWindow(
                detailed.bootstrapBoundaryDecisionTraces(),
                windowStartMs,
                windowEndMs,
                MAX_EVENT_LINES
        );
        System.out.println("-- bootstrap-cadence opening --");
        printBootstrapDecisionWindow(
                detailed.bootstrapCadenceDecisionTraces(),
                windowStartMs,
                windowEndMs,
                MAX_EVENT_LINES
        );
    }

    private static void printModeAndFrontEndWindow(
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed,
            long windowStartMs,
            long windowEndMs
    ) {
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
                    || !lastStateKey.equals(stateKey)
                    || timestampMs >= nextPrintAtMs;
            if (!shouldPrint) {
                continue;
            }

            System.out.println(String.format(
                    Locale.US,
                    "@%d mode=%s strategy=%s latched=%s trust=%s eligible=%s progress=%s lock=%s cons=%d lockR=%.2f det=%.1f on=%s tgt=%d eff=%d aq=%d final=%d hyp=%d(%.2f) dom=%.2f iso=%.2f toneOn=%s rescue=%s suppress=%s release=%s",
                    timestampMs,
                    modeTrace == null ? "?" : modeTrace.resolvedMode(),
                    modeTrace == null ? "?" : compact(modeTrace.strategy()),
                    yesNo(modeTrace != null && modeTrace.fallbackLatched()),
                    yesNo(modeTrace != null && modeTrace.trustedTimingEstablished()),
                    yesNo(modeTrace != null && modeTrace.eligibleForPreTrustFallback()),
                    yesNo(modeTrace != null && modeTrace.usefulFixedProgress()),
                    yesNo(snapshot.targetToneLocked()),
                    snapshot.consecutiveLockedFrames(),
                    snapshot.recentLockedFrameRatio(),
                    trace.detectionLevel(),
                    yesNo(snapshot.toneActive()),
                    snapshot.targetToneFrequencyHz(),
                    snapshot.effectiveTrackedToneFrequencyHz(),
                    snapshot.effectiveAcquisitionWinnerFrequencyHz(),
                    snapshot.effectiveFinalAdoptedFrequencyHz(),
                    snapshot.toneHypothesisFrequencyHz(),
                    snapshot.toneHypothesisConfidence(),
                    snapshot.toneDominanceRatio(),
                    snapshot.narrowbandIsolationRatio(),
                    compact(trace.toneOnDecision()),
                    compact(trace.postReleaseRescueDecision()),
                    compact(trace.postReleaseSuppressionDecision()),
                    compact(trace.releaseTailHoldDecision())
            ));
            System.out.println("src aq=" + compact(snapshot.acquisitionWinnerSource())
                    + " final=" + compact(snapshot.finalAdoptedSource())
                    + " aqDetail=" + compact(snapshot.acquisitionDecisionDetail())
                    + " finalDetail=" + compact(snapshot.finalAdoptionDetail()));
            System.out.println("cand pref=" + compact(snapshot.preferredWindowTopCandidatesSummary()));
            System.out.println("cand wide=" + compact(snapshot.wideScanTopCandidatesSummary()));

            lastStateKey = stateKey;
            nextPrintAtMs = timestampMs + PRINT_STEP_MS;
        }
    }

    private static void printDecodeEventsInWindow(
            List<CwDecodeEvent> decodeEvents,
            long windowStartMs,
            long windowEndMs,
            int maxLines
    ) {
        int printed = 0;
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            if (decodeEvent == null || decodeEvent.timestampMs() < windowStartMs) {
                continue;
            }
            if (decodeEvent.timestampMs() > windowEndMs) {
                break;
            }
            System.out.println(String.format(
                    Locale.US,
                    "@%d %-18s emit=%s seq=%s out=%s unknown=%s",
                    decodeEvent.timestampMs(),
                    decodeEvent.type(),
                    sanitize(decodeEvent.emittedValue()),
                    sanitize(decodeEvent.sourceSequence()),
                    sanitize(decodeEvent.outputText()),
                    decodeEvent.unknownCharacter()
            ));
            printed += 1;
            if (printed >= maxLines) {
                break;
            }
        }
    }

    private static void printBootstrapDecisionWindow(
            List<LocalAudioDecodeTestSupport.BootstrapDecisionTrace> traces,
            long windowStartMs,
            long windowEndMs,
            int maxLines
    ) {
        int printed = 0;
        for (LocalAudioDecodeTestSupport.BootstrapDecisionTrace trace : traces) {
            if (trace == null || trace.timestampMs() < windowStartMs) {
                continue;
            }
            if (trace.timestampMs() > windowEndMs) {
                break;
            }
            System.out.println(String.format(
                    Locale.US,
                    "@%d kind=%s class=%s dur=%d cand=%d decision=%s trusted=%s lock=%s lockR=%.2f near=%.2f unlock=%.2f dom=%.2f iso=%.2f rawWpm=%.1f rawDot=%d anchor=%s",
                    trace.timestampMs(),
                    safe(trace.eventKind()),
                    safe(trace.classification()),
                    trace.durationMs(),
                    trace.candidateDotEstimateMs(),
                    safe(trace.decision()),
                    yesNo(trace.trustedTimingEstablished()),
                    yesNo(trace.targetToneLocked()),
                    trace.recentLockedFrameRatio(),
                    trace.recentNearTargetLockedFrameRatio(),
                    trace.recentActiveUnlockedFrameRatio(),
                    trace.toneDominanceRatio(),
                    trace.narrowbandIsolationRatio(),
                    trace.rawWpm(),
                    trace.rawDotEstimateMs(),
                    compact(trace.anchorSummary())
            ));
            printed += 1;
            if (printed >= maxLines) {
                break;
            }
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
        long trustLimitedEndMs = trustMs < 0L
                ? windowStartMs + OPENING_WINDOW_MS
                : Math.min(windowStartMs + OPENING_WINDOW_MS, trustMs);
        return Math.min(flushTimestampMs, trustLimitedEndMs);
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

    private static String buildStateKey(
            CwSignalSnapshot snapshot,
            LocalAudioDecodeTestSupport.FrameSignalTrace trace,
            LocalAudioDecodeTestSupport.RxToneModeDecisionTrace modeTrace
    ) {
        return (modeTrace == null ? "?" : modeTrace.resolvedMode().name())
                + "|" + (modeTrace == null ? "?" : modeTrace.strategy())
                + "|" + (modeTrace != null && modeTrace.fallbackLatched())
                + "|" + (modeTrace != null && modeTrace.eligibleForPreTrustFallback())
                + "|" + (modeTrace != null && modeTrace.usefulFixedProgress())
                + "|" + snapshot.targetToneLocked()
                + "|" + snapshot.toneActive()
                + "|" + snapshot.effectiveTrackedToneFrequencyHz()
                + "|" + snapshot.effectiveAcquisitionWinnerFrequencyHz()
                + "|" + snapshot.effectiveFinalAdoptedFrequencyHz()
                + "|" + compact(snapshot.acquisitionWinnerSource())
                + "|" + compact(snapshot.finalAdoptedSource())
                + "|" + compact(trace.toneOnDecision())
                + "|" + compact(trace.postReleaseRescueDecision())
                + "|" + compact(trace.postReleaseSuppressionDecision())
                + "|" + compact(trace.releaseTailHoldDecision());
    }

    private static String textAtOrBefore(List<CwDecodeEvent> decodeEvents, long timestampMs) {
        String latestText = "";
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            if (decodeEvent != null && decodeEvent.timestampMs() <= timestampMs) {
                latestText = decodeEvent.outputText();
            } else if (decodeEvent != null && decodeEvent.timestampMs() > timestampMs) {
                break;
            }
        }
        return latestText == null ? "" : latestText;
    }

    private static String sliceNewText(String beforeText, String afterText) {
        String before = beforeText == null ? "" : beforeText;
        String after = afterText == null ? "" : afterText;
        if (after.startsWith(before)) {
            return after.substring(before.length());
        }
        return after;
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

    private static String safe(String text) {
        return text == null || text.isEmpty() ? "none" : text;
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }
}
