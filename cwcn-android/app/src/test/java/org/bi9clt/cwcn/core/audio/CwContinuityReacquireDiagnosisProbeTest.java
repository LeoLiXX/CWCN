package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertTrue;

public final class CwContinuityReacquireDiagnosisProbeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;
    private static final long PRINT_STEP_MS = 128L;

    @Test
    public void printRecording15LiveLikeContinuityWindow() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(15).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for recording (15)"));
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                        wavFile,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
        long windowStartMs = detailed.frames().isEmpty()
                ? 0L
                : detailed.frames().get(0).capturedAtMs();
        printCase("recording(15)", detailed, windowStartMs, detailed.flushTimestampMs());
        assertTrue(true);
    }

    @Test
    public void printCaptureTurn2LiveLikeContinuityWindow() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.findTraceDir().resolve("capture.wav");
        if (!Files.isRegularFile(wavFile)) {
            throw new IllegalStateException("Missing capture wav: " + wavFile);
        }
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                        wavFile,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
        List<TurnWindow> turnWindows = buildTurnWindows(detailed);
        if (turnWindows.size() < 2) {
            throw new IllegalStateException("Expected at least 2 turns in capture.wav, got " + turnWindows.size());
        }
        TurnWindow turn2 = turnWindows.get(1);
        printCase("capture.wav turn2", detailed, turn2.turnStartTimestampMs(), turn2.turnEndTimestampMs());
        assertTrue(true);
    }

    private static void printCase(
            String label,
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed,
            long windowStartMs,
            long windowEndMs
    ) {
        System.out.println("==== " + label + " continuity/reacquire diagnosis ====");
        System.out.println(String.format(
                Locale.US,
                "window=%d..%d final=%s stable=%s raw=%s turns=%d trust=%d tone=%d/%d/%d wpm=%d rejects=%s",
                windowStartMs,
                windowEndMs,
                sanitize(detailed.probeResult().decodedText()),
                sanitize(renderStableText(detailed.stableAcceptedDecodeEvents())),
                sanitize(renderRawText(detailed.rawDecodeEvents())),
                countTurns(detailed.turnTransitionTraces()),
                firstTrustTimestampMsInWindow(detailed.timingStateTraces(), windowStartMs, windowEndMs),
                detailed.probeResult().signalSnapshot().targetToneFrequencyHz(),
                detailed.probeResult().signalSnapshot().effectiveTrackedToneFrequencyHz(),
                detailed.probeResult().signalSnapshot().effectiveFinalAdoptedFrequencyHz(),
                detailed.probeResult().timingSnapshot().estimatedWpm(),
                detailed.stableRejectCounts()
        ));
        printDecodeEventsInWindow(detailed.decodeEvents(), windowStartMs, windowEndMs);

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
            boolean diagnosticDecision = hasInterestingDecision(trace, snapshot);
            boolean shouldPrint = diagnosticDecision
                    || lastStateKey == null
                    || !stateKey.equals(lastStateKey)
                    || timestampMs >= nextPrintAtMs;
            if (!shouldPrint) {
                continue;
            }

            System.out.println(String.format(
                    Locale.US,
                    "@%d mode=%s trust=%s useful=%s det=%.1f on=%s lock=%s cons=%d tgt=%d eff=%d aq=%d final=%d hyp=%d(%.2f) pending=%d@%d guard=%s %d/%d drift=%d dom=%.2f iso=%.2f lc=%.2f thr=%d/%d tone=%.1f startedByRescue=%s rescueOn=%s tail=%s",
                    timestampMs,
                    modeTrace == null ? "?" : modeTrace.resolvedMode(),
                    yesNo(modeTrace != null && modeTrace.trustedTimingEstablished()),
                    yesNo(modeTrace != null && modeTrace.usefulFixedProgress()),
                    trace.detectionLevel(),
                    yesNo(snapshot.toneActive()),
                    yesNo(snapshot.targetToneLocked()),
                    snapshot.consecutiveLockedFrames(),
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
                    trace.localContrastRatio(),
                    snapshot.currentThreshold(),
                    snapshot.releaseThreshold(),
                    snapshot.lastToneRmsAmplitude(),
                    yesNo(trace.currentToneStartedByPostReleaseRescue()),
                    yesNo(trace.toneOnAcceptedByRescue()),
                    yesNo(trace.releaseTailHoldApplied())
            ));
            System.out.println("src aq=" + compact(snapshot.acquisitionWinnerSource())
                    + " final=" + compact(snapshot.finalAdoptedSource())
                    + " aqDetail=" + compact(snapshot.acquisitionDecisionDetail())
                    + " finalDetail=" + compact(snapshot.finalAdoptionDetail()));
            System.out.println("front toneOn=" + compact(trace.toneOnDecision())
                    + " rescue=" + compact(trace.postReleaseRescueDecision())
                    + " suppress=" + compact(trace.postReleaseSuppressionDecision())
                    + " release=" + compact(trace.releaseTailHoldDecision())
                    + " attack=" + compact(trace.farAttackDelayDecision()));
            if (diagnosticDecision) {
                System.out.println("cand pref=" + compact(snapshot.preferredWindowTopCandidatesSummary()));
                System.out.println("cand wide=" + compact(snapshot.wideScanTopCandidatesSummary()));
            }

            lastStateKey = stateKey;
            nextPrintAtMs = timestampMs + PRINT_STEP_MS;
        }
    }

    private static void printDecodeEventsInWindow(
            List<CwDecodeEvent> decodeEvents,
            long windowStartMs,
            long windowEndMs
    ) {
        System.out.println("-- decode-events --");
        String previousText = "";
        for (CwDecodeEvent event : decodeEvents) {
            if (event == null
                    || event.timestampMs() < windowStartMs
                    || event.timestampMs() > windowEndMs) {
                continue;
            }
            String outputText = normalize(event.outputText());
            String appended = outputText.startsWith(previousText)
                    ? outputText.substring(previousText.length())
                    : normalize(event.emittedValue());
            System.out.println(String.format(
                    Locale.US,
                    "@%d type=%s val=%s seq=%s unk=%s append=%s out=%s",
                    event.timestampMs(),
                    event.type(),
                    compact(normalize(event.emittedValue())),
                    compact(event.sourceSequence()),
                    yesNo(event.unknownCharacter()),
                    compact(appended),
                    compact(outputText)
            ));
            previousText = outputText;
        }
    }

    private static boolean hasInterestingDecision(
            LocalAudioDecodeTestSupport.FrameSignalTrace trace,
            CwSignalSnapshot snapshot
    ) {
        return trace.toneOnAcceptedByRescue()
                || trace.currentToneStartedByPostReleaseRescue()
                || trace.releaseTailHoldApplied()
                || snapshot.lockedRetuneGuardHolding()
                || isInterestingDecision(trace.postReleaseRescueDecision())
                || isInterestingDecision(trace.postReleaseSuppressionDecision())
                || isInterestingDecision(trace.farAttackDelayDecision())
                || isInterestingDecision(trace.releaseTailHoldDecision());
    }

    private static boolean isInterestingDecision(String decision) {
        if (decision == null) {
            return false;
        }
        return !"NONE".equals(decision)
                && !"ELIGIBLE".equals(decision)
                && !"BLOCKED:BASIC_PRECONDITION".equals(decision);
    }

    private static String buildStateKey(
            CwSignalSnapshot snapshot,
            LocalAudioDecodeTestSupport.FrameSignalTrace trace,
            LocalAudioDecodeTestSupport.RxToneModeDecisionTrace modeTrace
    ) {
        return (modeTrace == null ? "?" : modeTrace.resolvedMode().name())
                + "|" + (modeTrace != null && modeTrace.trustedTimingEstablished())
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
                + "|" + compact(trace.postReleaseRescueDecision())
                + "|" + compact(trace.postReleaseSuppressionDecision())
                + "|" + compact(trace.farAttackDelayDecision())
                + "|" + compact(trace.releaseTailHoldDecision());
    }

    private static LocalAudioDecodeTestSupport.RxToneModeDecisionTrace latestModeTraceAtOrBefore(
            List<LocalAudioDecodeTestSupport.RxToneModeDecisionTrace> traces,
            long timestampMs
    ) {
        LocalAudioDecodeTestSupport.RxToneModeDecisionTrace latest = null;
        for (LocalAudioDecodeTestSupport.RxToneModeDecisionTrace trace : traces) {
            if (trace == null || trace.timestampMs() > timestampMs) {
                continue;
            }
            latest = trace;
        }
        return latest;
    }

    private static long firstTrustTimestampMsInWindow(
            List<LocalAudioDecodeTestSupport.TimingStateTrace> traces,
            long windowStartMs,
            long windowEndMs
    ) {
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : traces) {
            if (trace == null
                    || trace.timestampMs() < windowStartMs
                    || trace.timestampMs() > windowEndMs
                    || trace.debugSnapshot() == null) {
                continue;
            }
            if (trace.debugSnapshot().trustedDotEstimateMs() > 0.0d) {
                return trace.timestampMs();
            }
        }
        return -1L;
    }

    private static int countTurns(List<LocalAudioDecodeTestSupport.TurnTransitionTrace> traces) {
        int count = 0;
        for (LocalAudioDecodeTestSupport.TurnTransitionTrace trace : traces) {
            if (trace != null && trace.kind() == LocalAudioDecodeTestSupport.TurnTransitionTrace.Kind.START) {
                count += 1;
            }
        }
        return count;
    }

    private static List<TurnWindow> buildTurnWindows(LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed) {
        ArrayList<TurnWindow> windows = new ArrayList<>();
        for (LocalAudioDecodeTestSupport.TurnTransitionTrace trace : detailed.turnTransitionTraces()) {
            if (trace == null || trace.kind() != LocalAudioDecodeTestSupport.TurnTransitionTrace.Kind.START) {
                continue;
            }
            LocalAudioDecodeTestSupport.TurnTransitionTrace endTrace = findTurnEndTrace(
                    detailed.turnTransitionTraces(),
                    trace.turnIndex(),
                    trace.timestampMs()
            );
            windows.add(new TurnWindow(
                    trace.turnIndex(),
                    trace.timestampMs(),
                    endTrace == null ? detailed.flushTimestampMs() : endTrace.timestampMs()
            ));
        }
        return windows;
    }

    private static LocalAudioDecodeTestSupport.TurnTransitionTrace findTurnEndTrace(
            List<LocalAudioDecodeTestSupport.TurnTransitionTrace> traces,
            int turnIndex,
            long turnStartTimestampMs
    ) {
        for (LocalAudioDecodeTestSupport.TurnTransitionTrace trace : traces) {
            if (trace == null
                    || trace.kind() != LocalAudioDecodeTestSupport.TurnTransitionTrace.Kind.END
                    || trace.turnIndex() != turnIndex
                    || trace.timestampMs() < turnStartTimestampMs) {
                continue;
            }
            return trace;
        }
        return null;
    }

    private static String renderStableText(List<CwDecodeEvent> events) {
        return renderLastOutputText(events);
    }

    private static String renderRawText(List<CwDecodeEvent> events) {
        return renderLastOutputText(events);
    }

    private static String renderLastOutputText(List<CwDecodeEvent> events) {
        String text = "";
        for (CwDecodeEvent event : events) {
            if (event == null) {
                continue;
            }
            text = normalize(event.outputText());
        }
        return text;
    }

    private static String compact(Object value) {
        if (value == null) {
            return "-";
        }
        String text = value.toString().trim();
        return text.isEmpty() ? "-" : text;
    }

    private static String sanitize(String value) {
        return normalize(value).replace("\r", "").replace("\n", "\\n");
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }

    private static String yesNo(boolean value) {
        return value ? "Y" : "N";
    }

    private static final class TurnWindow {
        private final int turnIndex;
        private final long turnStartTimestampMs;
        private final long turnEndTimestampMs;

        private TurnWindow(int turnIndex, long turnStartTimestampMs, long turnEndTimestampMs) {
            this.turnIndex = turnIndex;
            this.turnStartTimestampMs = turnStartTimestampMs;
            this.turnEndTimestampMs = turnEndTimestampMs;
        }

        private int turnIndex() {
            return turnIndex;
        }

        private long turnStartTimestampMs() {
            return turnStartTimestampMs;
        }

        private long turnEndTimestampMs() {
            return turnEndTimestampMs;
        }
    }
}
