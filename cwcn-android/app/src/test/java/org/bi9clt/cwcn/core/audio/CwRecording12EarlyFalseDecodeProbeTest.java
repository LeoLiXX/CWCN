package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class CwRecording12EarlyFalseDecodeProbeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int DEFAULT_SQL_PERCENT = 55;
    private static final int MIN_SQL_PERCENT = 0;
    private static final long WINDOW_START_MS = 0L;
    private static final long WINDOW_END_MS = 5500L;
    private static final long FRAME_PRINT_STEP_MS = 128L;

    @Test
    public void printRecording12EarlyFalseDecodeWindow() throws Exception {
        printWindow("default-sql", DEFAULT_SQL_PERCENT);
    }

    @Test
    public void printRecording12EarlyFalseDecodeWindowAtMinSql() throws Exception {
        printWindow("min-sql", MIN_SQL_PERCENT);
    }

    private static void printWindow(String label, int sqlPercent) throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(12).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for recording (12)"));
        List<AudioFrame> frames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "recording12-early-false-decode",
                        frames,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        sqlPercent,
                        false,
                        false,
                        false,
                        false,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        System.out.println("==== recording(12) early false decode window " + label + " ====");
        System.out.println(String.format(
                Locale.US,
                "sql=%d window=%d..%d final=%s trust=%d tone=%d/%d/%d",
                sqlPercent,
                WINDOW_START_MS,
                WINDOW_END_MS,
                sanitize(detailed.probeResult().decodedText()),
                firstTrustTimestampMs(detailed.timingStateTraces()),
                detailed.probeResult().signalSnapshot().targetToneFrequencyHz(),
                detailed.probeResult().signalSnapshot().effectiveAcquisitionWinnerFrequencyHz(),
                detailed.probeResult().signalSnapshot().effectiveFinalAdoptedFrequencyHz()
        ));

        System.out.println("-- decode events --");
        boolean printedDecodeEvent = false;
        for (CwDecodeEvent decodeEvent : detailed.decodeEvents()) {
            if (decodeEvent == null || decodeEvent.timestampMs() < WINDOW_START_MS
                    || decodeEvent.timestampMs() > WINDOW_END_MS) {
                continue;
            }
            printedDecodeEvent = true;
            System.out.println(String.format(
                    Locale.US,
                    "@%d %-18s emit=%s seq=%s out=%s unk=%s",
                    decodeEvent.timestampMs(),
                    decodeEvent.type(),
                    sanitize(decodeEvent.emittedValue()),
                    sanitize(decodeEvent.sourceSequence()),
                    sanitize(decodeEvent.outputText()),
                    yesNo(decodeEvent.unknownCharacter())
            ));
        }
        if (!printedDecodeEvent) {
            System.out.println("  none");
        }

        System.out.println("-- front-end frames --");
        long nextPrintAtMs = Long.MIN_VALUE;
        String lastStateKey = null;
        for (LocalAudioDecodeTestSupport.FrameSignalTrace trace : detailed.frameSignalTraces()) {
            if (trace == null || trace.snapshot() == null) {
                continue;
            }
            long timestampMs = trace.timestampMs();
            if (timestampMs < WINDOW_START_MS) {
                continue;
            }
            if (timestampMs > WINDOW_END_MS) {
                break;
            }
            CwSignalSnapshot snapshot = trace.snapshot();
            String stateKey = buildStateKey(snapshot, trace);
            boolean interesting = isInteresting(snapshot, trace);
            boolean shouldPrint = interesting && (
                    lastStateKey == null
                            || !stateKey.equals(lastStateKey)
                            || timestampMs >= nextPrintAtMs
            );
            if (!shouldPrint) {
                continue;
            }

            System.out.println(String.format(
                    Locale.US,
                    "@%d det=%.1f on=%s lock=%s cons=%d lockR=%.2f tgt=%d eff=%d aq=%d final=%d hyp=%d(%.2f) thr=%d/%d floor=%d/%d tone=%.1f aqd=%s mem=%s anchor=%d localOn=%d gap=%d/%d rescueWin=%s+%d rescueCnt=%d weakChain=%s/%d/%d cont=%s suppress=%s late=%s lowGrow=%s nearRescue=%s tailHold=%s weakHold=%d",
                    timestampMs,
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
                    snapshot.currentThreshold(),
                    snapshot.releaseThreshold(),
                    snapshot.noiseFloorEstimate(),
                    snapshot.signalFloorEstimate(),
                    snapshot.lastToneRmsAmplitude(),
                    yesNo(trace.attackQualified()),
                    yesNo(trace.trackedToneMemoryActiveBeforeFrame()),
                    trace.attackAnchorFrequencyHzBeforeFrame(),
                    trace.frameLocalToneOnTimestampMs(),
                    trace.postReleaseGapMs(),
                    trace.postReleaseWindowMs(),
                    yesNo(trace.postReleaseRescueContinuationWindowActive()),
                    trace.postReleaseRescueContinuationWindowRemainingMs(),
                    trace.postReleaseWeakContinuityRescueCount(),
                    yesNo(trace.trustedWeakPostReleaseOnsetChainActive()),
                    trace.trustedWeakPostReleaseOnsetChainFrameCount(),
                    trace.trustedWeakPostReleaseOnsetChainStartMs(),
                    yesNo(trace.trustedContinuityToneOnCandidate()),
                    yesNo(trace.postReleaseSteadyCarrierSuppressed()),
                    yesNo(trace.steadyLateGapNearTargetRescueCandidate()),
                    yesNo(trace.lowGrowthStrongSteadyNearTargetRescue()),
                    yesNo(trace.nearTargetPostReleaseToneOnRescue()),
                    yesNo(trace.releaseTailHoldApplied()),
                    trace.currentToneRunWeakBootstrapReleaseTailHoldCount()
            ));
            System.out.println("decision toneOn=" + compact(trace.toneOnDecision())
                    + " rescue=" + compact(trace.postReleaseRescueDecision())
                    + " suppress=" + compact(trace.postReleaseSuppressionDecision())
                    + " release=" + compact(trace.releaseTailHoldDecision())
                    + " attack=" + compact(trace.farAttackDelayDecision()));

            lastStateKey = stateKey;
            nextPrintAtMs = timestampMs + FRAME_PRINT_STEP_MS;
        }
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

    private static boolean isInteresting(
            CwSignalSnapshot snapshot,
            LocalAudioDecodeTestSupport.FrameSignalTrace trace
    ) {
        return snapshot.toneActive()
                || snapshot.targetToneLocked()
                || trace.toneOnAccepted()
                || trace.toneOnAcceptedByRescue()
                || trace.releaseTailHoldApplied()
                || trace.trustedContinuityToneOnCandidate()
                || trace.weakPostReleaseOnsetChainCandidate()
                || trace.postReleaseRescueContinuationWindowActive()
                || trace.attackQualified()
                || !"NONE".equals(trace.toneOnDecision())
                || !"NONE".equals(trace.postReleaseRescueDecision())
                || !"NONE".equals(trace.postReleaseSuppressionDecision())
                || !"NONE".equals(trace.releaseTailHoldDecision())
                || !"NONE".equals(trace.farAttackDelayDecision());
    }

    private static String buildStateKey(
            CwSignalSnapshot snapshot,
            LocalAudioDecodeTestSupport.FrameSignalTrace trace
    ) {
        return snapshot.toneActive()
                + "|" + snapshot.targetToneLocked()
                + "|" + snapshot.consecutiveLockedFrames()
                + "|" + snapshot.effectiveTrackedToneFrequencyHz()
                + "|" + snapshot.effectiveAcquisitionWinnerFrequencyHz()
                + "|" + snapshot.effectiveFinalAdoptedFrequencyHz()
                + "|" + trace.toneOnAccepted()
                + "|" + trace.toneOnAcceptedByRescue()
                + "|" + trace.releaseTailHoldApplied()
                + "|" + trace.trustedContinuityToneOnCandidate()
                + "|" + trace.weakPostReleaseOnsetChainCandidate()
                + "|" + trace.postReleaseRescueContinuationWindowActive()
                + "|" + compact(trace.toneOnDecision())
                + "|" + compact(trace.postReleaseRescueDecision())
                + "|" + compact(trace.postReleaseSuppressionDecision())
                + "|" + compact(trace.releaseTailHoldDecision())
                + "|" + compact(trace.farAttackDelayDecision());
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
        return normalized.isEmpty() ? "NONE" : normalized;
    }

    private static String yesNo(boolean value) {
        return value ? "Y" : "N";
    }
}
