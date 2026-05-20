package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertTrue;

public final class CwRecording7ContinuityProbeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;
    private static final long WINDOW_AFTER_TRUST_MS = 2200L;
    private static final long PRINT_STEP_MS = 96L;

    @Test
    public void printRecording7PostTrustContinuityWindow() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(7).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for recording (7)"));
        List<AudioFrame> frames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "recording7-continuity",
                        frames,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        long trustMs = firstTrustTimestampMs(detailed.timingStateTraces());
        long windowStartMs = trustMs < 0L ? 0L : trustMs;
        long windowEndMs = trustMs < 0L
                ? detailed.flushTimestampMs()
                : Math.min(detailed.flushTimestampMs(), trustMs + WINDOW_AFTER_TRUST_MS);

        System.out.println("==== recording7 post-trust continuity window ====");
        System.out.println(String.format(
                Locale.US,
                "trust=%d window=%d..%d final=%s",
                trustMs,
                windowStartMs,
                windowEndMs,
                sanitize(detailed.probeResult().decodedText())
        ));
        System.out.println("-- frame window --");
        printFrameWindow(detailed.frameSignalTraces(), windowStartMs, windowEndMs);
        System.out.println("-- decode window --");
        printDecodeWindow(detailed.rawDecodeEvents(), windowStartMs, windowEndMs);

        assertTrue(true);
    }

    private static void printFrameWindow(
            List<LocalAudioDecodeTestSupport.FrameSignalTrace> traces,
            long windowStartMs,
            long windowEndMs
    ) {
        long nextPrintAtMs = Long.MIN_VALUE;
        String lastStateKey = null;
        for (LocalAudioDecodeTestSupport.FrameSignalTrace trace : traces) {
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
            String stateKey = buildStateKey(trace, snapshot);
            boolean shouldPrint = lastStateKey == null
                    || !lastStateKey.equals(stateKey)
                    || timestampMs >= nextPrintAtMs;
            if (!shouldPrint) {
                continue;
            }

            System.out.println(String.format(
                    Locale.US,
                    "@%d det=%.1f thr=%d/%d tone=%.1f attackQ=%s lock=%s cons=%d lockR=%.2f near=%.2f far=%.2f"
                            + " tgt=%d eff=%d aq=%d final=%d pend=%d/%d guard=%s/%d/%d drift=%d"
                            + " dom=%.2f iso=%.2f lc=%.2f rep=%d maxLock=%d anchorNear=%.2f gap=%d/%d rescueWin=%s/%d rescue=%d"
                            + " weakCand=%s trustedCand=%s trackedMem=%s anchor=%d trustedChain=%s/%d"
                            + " toneOn=%s rescueDec=%s suppress=%s delay=%s hold=%s",
                    timestampMs,
                    trace.detectionLevel(),
                    snapshot.currentThreshold(),
                    snapshot.releaseThreshold(),
                    snapshot.lastToneRmsAmplitude(),
                    yesNo(trace.attackQualified()),
                    yesNo(snapshot.targetToneLocked()),
                    snapshot.consecutiveLockedFrames(),
                    snapshot.recentLockedFrameRatio(),
                    snapshot.recentNearTargetLockedFrameRatio(),
                    snapshot.recentFarOffTargetLockedFrameRatio(),
                    snapshot.targetToneFrequencyHz(),
                    snapshot.effectiveTrackedToneFrequencyHz(),
                    snapshot.effectiveAcquisitionWinnerFrequencyHz(),
                    snapshot.effectiveFinalAdoptedFrequencyHz(),
                    snapshot.pendingRetuneCandidateFrequencyHz(),
                    snapshot.pendingRetuneCandidateStableScans(),
                    yesNo(snapshot.lockedRetuneGuardHolding()),
                    snapshot.lockedRetuneGuardObservedScans(),
                    snapshot.lockedRetuneGuardRequiredScans(),
                    snapshot.lockedRetuneGuardDriftHz(),
                    snapshot.toneDominanceRatio(),
                    snapshot.narrowbandIsolationRatio(),
                    trace.localContrastRatio(),
                    snapshot.representativeLockedToneFrameCount(),
                    snapshot.maxConsecutiveLockedFrames(),
                    continuityAnchorNearRatio(snapshot, trace.attackAnchorFrequencyHzBeforeFrame()),
                    trace.postReleaseGapMs(),
                    trace.postReleaseWindowMs(),
                    yesNo(trace.postReleaseRescueContinuationWindowActive()),
                    trace.postReleaseRescueContinuationWindowRemainingMs(),
                    trace.postReleaseWeakContinuityRescueCount(),
                    yesNo(trace.weakPostReleaseOnsetChainCandidate()),
                    yesNo(trace.trustedContinuityToneOnCandidate()),
                    yesNo(trace.trackedToneMemoryActiveBeforeFrame()),
                    trace.attackAnchorFrequencyHzBeforeFrame(),
                    yesNo(trace.trustedWeakPostReleaseOnsetChainActive()),
                    trace.trustedWeakPostReleaseOnsetChainFrameCount(),
                    compact(trace.toneOnDecision()),
                    compact(trace.postReleaseRescueDecision()),
                    compact(trace.postReleaseSuppressionDecision()),
                    compact(trace.farAttackDelayDecision()),
                    compact(trace.releaseTailHoldDecision())
            ));
            System.out.println("src aq=" + compact(snapshot.acquisitionWinnerSource())
                    + " final=" + compact(snapshot.finalAdoptedSource())
                    + " aqDetail=" + compact(snapshot.acquisitionDecisionDetail())
                    + " finalDetail=" + compact(snapshot.finalAdoptionDetail()));

            lastStateKey = stateKey;
            nextPrintAtMs = timestampMs + PRINT_STEP_MS;
        }
    }

    private static void printDecodeWindow(List<CwDecodeEvent> events, long windowStartMs, long windowEndMs) {
        for (CwDecodeEvent event : events) {
            if (event == null || event.timestampMs() < windowStartMs) {
                continue;
            }
            if (event.timestampMs() > windowEndMs) {
                break;
            }
            System.out.println(String.format(
                    Locale.US,
                    "@%d %-18s emit=%s seq=%s out=%s unknown=%s",
                    event.timestampMs(),
                    event.type(),
                    sanitize(event.emittedValue()),
                    sanitize(event.sourceSequence()),
                    sanitize(event.outputText()),
                    event.unknownCharacter()
            ));
        }
    }

    private static String buildStateKey(
            LocalAudioDecodeTestSupport.FrameSignalTrace trace,
            CwSignalSnapshot snapshot
    ) {
        return yesNo(snapshot.targetToneLocked())
                + "|" + snapshot.consecutiveLockedFrames()
                + "|" + snapshot.targetToneFrequencyHz()
                + "|" + snapshot.effectiveFinalAdoptedFrequencyHz()
                + "|" + snapshot.pendingRetuneCandidateFrequencyHz()
                + "|" + snapshot.pendingRetuneCandidateStableScans()
                + "|" + yesNo(snapshot.lockedRetuneGuardHolding())
                + "|" + trace.postReleaseGapMs()
                + "|" + trace.postReleaseWindowMs()
                + "|" + trace.postReleaseWeakContinuityRescueCount()
                + "|" + compact(trace.postReleaseRescueDecision())
                + "|" + compact(trace.postReleaseSuppressionDecision())
                + "|" + compact(trace.farAttackDelayDecision())
                + "|" + compact(trace.releaseTailHoldDecision());
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

    private static String compact(String value) {
        if (value == null) {
            return "NONE";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.isEmpty()) {
            return "NONE";
        }
        if (normalized.length() <= 120) {
            return normalized;
        }
        return normalized.substring(0, 117) + "...";
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "(empty)";
        }
        String normalized = value.replace('\u25A1', '?').replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.isEmpty() ? "(empty)" : normalized;
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private static double continuityAnchorNearRatio(CwSignalSnapshot snapshot, int anchorFrequencyHz) {
        if (snapshot == null || anchorFrequencyHz <= 0) {
            return 0.0d;
        }
        char[] states = snapshot.recentFrontEndStateHistory();
        int[] offsets = snapshot.recentTrackingOffsetHistoryHz();
        int recentCount = Math.min(snapshot.recentHistoryFrameCount(), Math.min(states.length, offsets.length));
        if (recentCount <= 0) {
            return 0.0d;
        }
        int anchorOffsetHz = anchorFrequencyHz - snapshot.preferredToneFrequencyHz();
        int lockedCount = 0;
        int nearCount = 0;
        for (int index = 0; index < recentCount; index++) {
            char state = states[index];
            if (state != 'L' && state != 'l') {
                continue;
            }
            lockedCount += 1;
            if (Math.abs(offsets[index] - anchorOffsetHz) <= 15) {
                nearCount += 1;
            }
        }
        return lockedCount <= 0 ? 0.0d : nearCount / (double) lockedCount;
    }
}
