package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.decoder.CwDecoder;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.qso.QsoStateMachine;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel.DebugSnapshot;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CwRecording2HybridBootstrapSweepProbeTest {
    private static final int FRAME_SIZE_SAMPLES = 256;
    private static final int[] PREFERRED_TONES_HZ = new int[]{650, 700, 750};
    private static final int[] SQL_LEVELS = new int[]{35, 40, 45, 50, 55};
    private static final int SEED_WPM = 15;
    private static final long OPENING_WINDOW_MS = 6500L;
    private static final long SECOND_CQ_WINDOW_START_MS = 3160L;
    private static final long SECOND_CQ_WINDOW_END_MS = 3560L;
    private static final long TURN2_START_WINDOW_START_MS = 21056L;
    private static final long TURN2_START_WINDOW_END_MS = 21880L;
    private static final long TURN2_X_WINDOW_START_MS = 22880L;
    private static final long TURN2_X_WINDOW_END_MS = 23380L;
    private static final long EDGE_PROBE_WINDOW_START_MS = 3232L;
    private static final long EDGE_PROBE_WINDOW_END_MS = 3424L;
    private static final long LOCAL_REDECODE_START_MS = 3000L;
    private static final long LOCAL_REDECODE_END_MS = 4400L;
    private static final int EDGE_WINDOW_SAMPLES = 12;
    private static final int EDGE_CONFIRM_SAMPLES = 6;
    private static final double EDGE_THRESHOLD_RATIO = 0.30d;
    private static final double EDGE_DYNAMIC_RATIO = 0.24d;
    private static final double EDGE_TRANSITION_REQUIRED_RATIO = 0.82d;
    private static final int EDGE_MASK_BINS = 16;

    @Test
    public void printRecording2HybridBootstrapSweep() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(2).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (2)"));
        LocalAudioDecodeTestSupport.WaveDataProbe waveData =
                LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);
        List<AudioFrame> frames = normalizeFramesToZero(buildFrames(
                waveData.samples(),
                waveData.sampleRateHz()
        ));

        System.out.println("==== recording2 hybrid bootstrap sweep ====");
        System.out.println("expectedOpening=CQ DX CQ DX DE JV3VV JV3VV PAGE K.");
        for (int preferredToneHz : PREFERRED_TONES_HZ) {
            for (int sqlPercent : SQL_LEVELS) {
                LocalAudioDecodeTestSupport.OfflineDetailedProbeResult result =
                        LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                                String.format(Locale.US, "recording2-hyb-%d-%d", preferredToneHz, sqlPercent),
                                frames,
                                preferredToneHz,
                                SEED_WPM,
                                sqlPercent,
                                false,
                                CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                        );
                LocalAudioDecodeTestSupport.TimingStateTrace firstTrustTrace =
                        firstTrustedTimingTrace(result.timingStateTraces());
                DebugSnapshot debugSnapshot = firstTrustTrace == null ? null : firstTrustTrace.debugSnapshot();
                System.out.println(String.format(
                        Locale.US,
                        "pref=%d sql=%d trust=%dms dot=%.1f open=%s final=%s",
                        preferredToneHz,
                        sqlPercent,
                        firstTrustTrace == null ? -1L : firstTrustTrace.timestampMs(),
                        debugSnapshot == null ? 0.0d : debugSnapshot.trustedDotEstimateMs(),
                        sanitize(textAtOrBefore(result.rawDecodeEvents(), OPENING_WINDOW_MS)),
                        sanitize(result.probeResult().decodedText())
                ));
            }
        }
    }

    @Test
    public void printRecording2FrontEndGateSweep() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(2).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (2)"));
        LocalAudioDecodeTestSupport.WaveDataProbe waveData =
                LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);
        List<AudioFrame> frames = normalizeFramesToZero(buildFrames(
                waveData.samples(),
                waveData.sampleRateHz()
        ));

        System.out.println("==== recording2 front-end gate sweep ====");
        System.out.println("expectedOpening=CQ DX CQ DX DE JV3VV JV3VV PAGE K.");
        printGateCase("BASE", frames, false, false, false, false);
        printGateCase("AUTH", frames, true, false, false, false);
        printGateCase("ONSET", frames, false, true, false, false);
        printGateCase("MERGE", frames, false, false, true, false);
        printGateCase("HOLD", frames, false, false, false, true);
        printGateCase("AUTH+ONSET", frames, true, true, false, false);
        printGateCase("ONSET+MERGE", frames, false, true, true, false);
        printGateCase("ONSET+HOLD", frames, false, true, false, true);
        printGateCase("MERGE+HOLD", frames, false, false, true, true);
        printGateCase("ALL", frames, true, true, true, true);
    }

    @Test
    public void printRecording2SecondCqWindowTrace() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(2).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (2)"));
        LocalAudioDecodeTestSupport.WaveDataProbe waveData =
                LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);
        List<AudioFrame> frames = normalizeFramesToZero(buildFrames(
                waveData.samples(),
                waveData.sampleRateHz()
        ));

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult result =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "recording2-second-cq-window",
                        frames,
                        700,
                        SEED_WPM,
                        55,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        System.out.println("==== recording2 second CQ window trace ====");
        System.out.println(String.format(
                Locale.US,
                "window=%d..%d open=%s",
                SECOND_CQ_WINDOW_START_MS,
                SECOND_CQ_WINDOW_END_MS,
                sanitize(textAtOrBefore(result.rawDecodeEvents(), OPENING_WINDOW_MS))
        ));
        System.out.println("-- frame-trace --");
        for (LocalAudioDecodeTestSupport.FrameSignalTrace trace : result.frameSignalTraces()) {
            if (trace == null
                    || trace.timestampMs() < SECOND_CQ_WINDOW_START_MS
                    || trace.timestampMs() > SECOND_CQ_WINDOW_END_MS) {
                continue;
            }
            System.out.println(renderFrameTrace(trace));
        }
        System.out.println("-- tone-events --");
        for (org.bi9clt.cwcn.core.signal.CwToneEvent toneEvent : result.toneEvents()) {
            if (toneEvent == null
                    || toneEvent.timestampMs() < SECOND_CQ_WINDOW_START_MS
                    || toneEvent.timestampMs() > SECOND_CQ_WINDOW_END_MS) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "@%d %s dur=%d rms=%.1f",
                    toneEvent.timestampMs(),
                    toneEvent.type(),
                    toneEvent.toneDurationMs(),
                    toneEvent.rmsAmplitude()
            ));
        }
    }

    @Test
    public void printRecording2Turn2XWindowTrace() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(2).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (2)"));
        LocalAudioDecodeTestSupport.WaveDataProbe waveData =
                LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);
        List<AudioFrame> frames = normalizeFramesToZero(buildFrames(
                waveData.samples(),
                waveData.sampleRateHz()
        ));

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult result =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "recording2-turn2-x-window",
                        frames,
                        700,
                        SEED_WPM,
                        55,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        System.out.println("==== recording2 turn2 X window trace ====");
        System.out.println(String.format(
                Locale.US,
                "window=%d..%d final=%s",
                TURN2_X_WINDOW_START_MS,
                TURN2_X_WINDOW_END_MS,
                sanitize(result.probeResult().decodedText())
        ));
        System.out.println("-- frame-trace --");
        for (LocalAudioDecodeTestSupport.FrameSignalTrace trace : result.frameSignalTraces()) {
            if (trace == null
                    || trace.timestampMs() < TURN2_X_WINDOW_START_MS
                    || trace.timestampMs() > TURN2_X_WINDOW_END_MS) {
                continue;
            }
            System.out.println(renderFrameTrace(trace));
        }
        System.out.println("-- tone-events --");
        for (org.bi9clt.cwcn.core.signal.CwToneEvent toneEvent : result.toneEvents()) {
            if (toneEvent == null
                    || toneEvent.timestampMs() < TURN2_X_WINDOW_START_MS
                    || toneEvent.timestampMs() > TURN2_X_WINDOW_END_MS) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "@%d %s dur=%d rms=%.1f",
                    toneEvent.timestampMs(),
                    toneEvent.type(),
                    toneEvent.toneDurationMs(),
                    toneEvent.rmsAmplitude()
            ));
        }
    }

    @Test
    public void printRecording2Turn2StartWindowTrace() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(2).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (2)"));
        LocalAudioDecodeTestSupport.WaveDataProbe waveData =
                LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);
        List<AudioFrame> frames = normalizeFramesToZero(buildFrames(
                waveData.samples(),
                waveData.sampleRateHz()
        ));

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult result =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "recording2-turn2-start-window",
                        frames,
                        700,
                        SEED_WPM,
                        55,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        System.out.println("==== recording2 turn2 start window trace ====");
        System.out.println(String.format(
                Locale.US,
                "window=%d..%d final=%s",
                TURN2_START_WINDOW_START_MS,
                TURN2_START_WINDOW_END_MS,
                sanitize(result.probeResult().decodedText())
        ));
        System.out.println("-- mode-trace --");
        for (LocalAudioDecodeTestSupport.RxToneModeDecisionTrace trace : result.rxToneModeDecisionTraces()) {
            if (trace == null
                    || trace.timestampMs() < TURN2_START_WINDOW_START_MS
                    || trace.timestampMs() > TURN2_START_WINDOW_END_MS) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "@%d mode=%s strategy=%s trusted=%s latched=%s turn=%s start=%d useful=%s eligible=%s lock=%s streak=%d lockR=%.2f",
                    trace.timestampMs(),
                    trace.resolvedMode(),
                    safe(trace.strategy()),
                    trace.trustedTimingEstablished(),
                    trace.fallbackLatched(),
                    safe(trace.turnPhase()),
                    trace.turnStartedAtMs(),
                    trace.usefulFixedProgress(),
                    trace.eligibleForPreTrustFallback(),
                    trace.targetToneLocked(),
                    trace.consecutiveLockedFrames(),
                    trace.recentLockedFrameRatio()
            ));
        }
        System.out.println("-- frame-trace --");
        for (LocalAudioDecodeTestSupport.FrameSignalTrace trace : result.frameSignalTraces()) {
            if (trace == null
                    || trace.timestampMs() < TURN2_START_WINDOW_START_MS
                    || trace.timestampMs() > TURN2_START_WINDOW_END_MS) {
                continue;
            }
            System.out.println(renderFrameTrace(trace));
        }
        System.out.println("-- tone-events --");
        for (org.bi9clt.cwcn.core.signal.CwToneEvent toneEvent : result.toneEvents()) {
            if (toneEvent == null
                    || toneEvent.timestampMs() < TURN2_START_WINDOW_START_MS
                    || toneEvent.timestampMs() > TURN2_START_WINDOW_END_MS) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "@%d %s dur=%d rms=%.1f",
                    toneEvent.timestampMs(),
                    toneEvent.type(),
                    toneEvent.toneDurationMs(),
                    toneEvent.rmsAmplitude()
            ));
        }
    }

    @Test
    public void printRecording2TurnBoundaryProbe() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(2).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (2)"));
        LocalAudioDecodeTestSupport.WaveDataProbe waveData =
                LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);
        List<AudioFrame> frames = normalizeFramesToZero(buildFrames(
                waveData.samples(),
                waveData.sampleRateHz()
        ));

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult result =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "recording2-turn-boundary",
                        frames,
                        700,
                        SEED_WPM,
                        55,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        System.out.println("==== recording2 turn boundary probe ====");
        System.out.println("final=" + sanitize(result.probeResult().decodedText()));
        System.out.println("-- transitions --");
        for (LocalAudioDecodeTestSupport.TurnTransitionTrace trace : result.turnTransitionTraces()) {
            if (trace == null) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "@%d %s turn=%d seed=%d ref=%d currentAnchor=%d retainedAnchor=%d reason=%s",
                    trace.timestampMs(),
                    trace.kind(),
                    trace.turnIndex(),
                    trace.turnSeedWpm(),
                    trace.referenceWpm(),
                    trace.currentTurnAnchorWpm(),
                    trace.retainedTurnAnchorWpm(),
                    safe(trace.reason())
            ));
        }

        System.out.println("-- per-turn decode --");
        List<LocalAudioDecodeTestSupport.TurnTransitionTrace> transitions = result.turnTransitionTraces();
        for (int index = 0; index < transitions.size(); index++) {
            LocalAudioDecodeTestSupport.TurnTransitionTrace start = transitions.get(index);
            if (start == null || start.kind() != LocalAudioDecodeTestSupport.TurnTransitionTrace.Kind.START) {
                continue;
            }
            long turnStartMs = start.timestampMs();
            long turnEndMs = result.flushTimestampMs();
            for (int probe = index + 1; probe < transitions.size(); probe++) {
                LocalAudioDecodeTestSupport.TurnTransitionTrace candidate = transitions.get(probe);
                if (candidate != null
                        && candidate.turnIndex() == start.turnIndex()
                        && candidate.kind() == LocalAudioDecodeTestSupport.TurnTransitionTrace.Kind.END) {
                    turnEndMs = candidate.timestampMs();
                    break;
                }
            }
            String rawBefore = textAtOrBefore(result.rawDecodeEvents(), Math.max(0L, turnStartMs - 1L));
            String rawAfter = textAtOrBefore(result.rawDecodeEvents(), turnEndMs);
            String stableBefore = textAtOrBefore(
                    result.stableAcceptedDecodeEvents(),
                    Math.max(0L, turnStartMs - 1L)
            );
            String stableAfter = textAtOrBefore(result.stableAcceptedDecodeEvents(), turnEndMs);
            LocalAudioDecodeTestSupport.TimingStateTrace trustTrace =
                    firstTrustedTimingTraceInWindow(result.timingStateTraces(), turnStartMs, turnEndMs);
            DebugSnapshot debugSnapshot = trustTrace == null ? null : trustTrace.debugSnapshot();
            System.out.println(String.format(
                    Locale.US,
                    "turn=%d window=%d..%d trust=%dms dot=%.1f raw=%s stable=%s",
                    start.turnIndex(),
                    turnStartMs,
                    turnEndMs,
                    trustTrace == null ? -1L : trustTrace.timestampMs(),
                    debugSnapshot == null ? 0.0d : debugSnapshot.trustedDotEstimateMs(),
                    sanitize(sliceNewText(rawBefore, rawAfter)),
                    sanitize(sliceNewText(stableBefore, stableAfter))
            ));
        }
    }

    @Test
    public void printRecording2WeakEdgeEnvelopeProbe() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(2).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (2)"));
        LocalAudioDecodeTestSupport.WaveDataProbe waveData =
                LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);
        List<AudioFrame> frames = normalizeFramesToZero(buildFrames(
                waveData.samples(),
                waveData.sampleRateHz()
        ));

        System.out.println("==== recording2 weak edge envelope probe ====");
        System.out.println(String.format(
                Locale.US,
                "window=%d..%d",
                EDGE_PROBE_WINDOW_START_MS,
                EDGE_PROBE_WINDOW_END_MS
        ));
        for (AudioFrame frame : frames) {
            if (frame == null
                    || frame.capturedAtMs() < EDGE_PROBE_WINDOW_START_MS
                    || frame.capturedAtMs() > EDGE_PROBE_WINDOW_END_MS) {
                continue;
            }
            EdgeEnvelopeProbe probe = analyzeEdgeEnvelope(frame);
            System.out.println(String.format(
                    Locale.US,
                    "@%d rms=%.1f peak=%d thr=%.1f first=%s last=%s rise=%s fall=%s hard=%s soft=%s",
                    frame.capturedAtMs(),
                    frame.rmsAmplitude(),
                    frame.peakAmplitude(),
                    probe.threshold(),
                    formatSampleOffsetMs(probe.firstAboveIndex(), frame.sampleRateHz()),
                    formatSampleOffsetMs(probe.lastAboveIndex(), frame.sampleRateHz()),
                    formatSampleOffsetMs(probe.risingEdgeIndex(), frame.sampleRateHz()),
                    formatSampleOffsetMs(probe.fallingEdgeIndex(), frame.sampleRateHz()),
                    probe.hardMask(),
                    probe.softMask()
            ));
        }
    }

    @Test
    public void printRecording2ShortGapMergeReplayProbe() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(2).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (2)"));
        LocalAudioDecodeTestSupport.WaveDataProbe waveData =
                LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);
        List<AudioFrame> frames = normalizeFramesToZero(buildFrames(
                waveData.samples(),
                waveData.sampleRateHz()
        ));

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult result =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "recording2-short-gap-merge",
                        frames,
                        700,
                        SEED_WPM,
                        55,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        System.out.println("==== recording2 short gap merge replay probe ====");
        System.out.println("baseline=" + sanitize(result.probeResult().decodedText()));
        for (long gapThresholdMs : new long[]{40L, 48L, 64L}) {
            ToneReplayResult replay = replayMergedShortGaps(
                    result.toneEvents(),
                    result.flushTimestampMs(),
                    gapThresholdMs
            );
            System.out.println(String.format(
                    Locale.US,
                    "gap<=%dms merged=%d text=%s",
                    gapThresholdMs,
                    replay.mergedPairCount(),
                    sanitize(replay.decodedText())
            ));
        }
        ToneReplayResult selectiveReplay = replayMergedShortGaps(
                result.toneEvents(),
                result.flushTimestampMs(),
                48L,
                3240L,
                3380L
        );
        System.out.println(String.format(
                Locale.US,
                "window[3240..3380] gap<=48ms merged=%d text=%s",
                selectiveReplay.mergedPairCount(),
                sanitize(selectiveReplay.decodedText())
        ));
        ToneReplayResult firstGapReplay = replayMergedShortGaps(
                result.toneEvents(),
                result.flushTimestampMs(),
                48L,
                3240L,
                3300L
        );
        System.out.println(String.format(
                Locale.US,
                "window[3240..3300] gap<=48ms merged=%d text=%s",
                firstGapReplay.mergedPairCount(),
                sanitize(firstGapReplay.decodedText())
        ));
        ToneReplayResult secondGapReplay = replayMergedShortGaps(
                result.toneEvents(),
                result.flushTimestampMs(),
                48L,
                3300L,
                3380L
        );
        System.out.println(String.format(
                Locale.US,
                "window[3300..3380] gap<=48ms merged=%d text=%s",
                secondGapReplay.mergedPairCount(),
                sanitize(secondGapReplay.decodedText())
        ));
        ToneReplayResult extendedSelectiveReplay = replayMergedShortGaps(
                result.toneEvents(),
                result.flushTimestampMs(),
                130L,
                3240L,
                3540L
        );
        System.out.println(String.format(
                Locale.US,
                "window[3240..3540] gap<=130ms merged=%d text=%s",
                extendedSelectiveReplay.mergedPairCount(),
                sanitize(extendedSelectiveReplay.decodedText())
        ));
    }

    @Test
    public void printRecording2LocalWindowRedecodeProbe() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(2).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (2)"));
        LocalAudioDecodeTestSupport.WaveDataProbe waveData =
                LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);
        List<AudioFrame> fullFrames = normalizeFramesToZero(buildFrames(
                waveData.samples(),
                waveData.sampleRateHz()
        ));
        List<AudioFrame> windowFrames = sliceFrames(fullFrames, LOCAL_REDECODE_START_MS, LOCAL_REDECODE_END_MS);

        System.out.println("==== recording2 local window redecode probe ====");
        System.out.println(String.format(
                Locale.US,
                "window=%d..%d expected~CQ DX / CQ DX",
                LOCAL_REDECODE_START_MS,
                LOCAL_REDECODE_END_MS
        ));

        printLocalWindowCase(
                "LIVE-HYB seed15 pref700",
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "recording2-local-live-hyb-15",
                        windowFrames,
                        700,
                        15,
                        55,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                )
        );
        printLocalWindowCase(
                "LIVE-HYB seed29 pref700",
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "recording2-local-live-hyb-29",
                        windowFrames,
                        700,
                        29,
                        55,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                )
        );
        printLocalWindowCase(
                "LIVE-FIX seed29 pref700",
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "recording2-local-live-fix-29",
                        windowFrames,
                        700,
                        29,
                        55,
                        false,
                        false,
                        false,
                        false,
                        false,
                        org.bi9clt.cwcn.core.signal.CwSignalProcessor.RxToneMode.FIXED_TONE,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                )
        );
        printLocalWindowCase(
                "LIVE-FIX seed29 pref660",
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "recording2-local-live-fix-29-660",
                        windowFrames,
                        660,
                        29,
                        55,
                        false,
                        false,
                        false,
                        false,
                        false,
                        org.bi9clt.cwcn.core.signal.CwSignalProcessor.RxToneMode.FIXED_TONE,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                )
        );
        printLocalWindowCase(
                "LIVE-AUTO seed29 pref700",
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "recording2-local-live-auto-29",
                        windowFrames,
                        700,
                        29,
                        55,
                        false,
                        false,
                        false,
                        false,
                        false,
                        org.bi9clt.cwcn.core.signal.CwSignalProcessor.RxToneMode.AUTO_TRACK,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                )
        );

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult localDetailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailed(
                        "recording2-local-offline",
                        windowFrames,
                        false
                );
        for (int forcedToneHz : new int[]{620, 640, 660, 680, 700}) {
            printForcedLocalWindowCase(
                    "FORCED-" + forcedToneHz,
                    LocalAudioDecodeTestSupport.replayForcedConstantToneDecode(localDetailed, forcedToneHz)
            );
        }
    }

    private static void printGateCase(
            String label,
            List<AudioFrame> frames,
            boolean authority,
            boolean onset,
            boolean merge,
            boolean hold
    ) {
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult result =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "recording2-" + label,
                        frames,
                        700,
                        SEED_WPM,
                        55,
                        false,
                        authority,
                        onset,
                        merge,
                        hold,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
        LocalAudioDecodeTestSupport.TimingStateTrace firstTrustTrace =
                firstTrustedTimingTrace(result.timingStateTraces());
        DebugSnapshot debugSnapshot = firstTrustTrace == null ? null : firstTrustTrace.debugSnapshot();
        System.out.println(String.format(
                Locale.US,
                "%s trust=%dms dot=%.1f open=%s final=%s",
                label,
                firstTrustTrace == null ? -1L : firstTrustTrace.timestampMs(),
                debugSnapshot == null ? 0.0d : debugSnapshot.trustedDotEstimateMs(),
                sanitize(textAtOrBefore(result.rawDecodeEvents(), OPENING_WINDOW_MS)),
                sanitize(result.probeResult().decodedText())
        ));
    }

    private static String renderFrameTrace(LocalAudioDecodeTestSupport.FrameSignalTrace trace) {
        org.bi9clt.cwcn.core.signal.CwSignalSnapshot snapshot = trace.snapshot();
        if (snapshot == null) {
            return String.format(Locale.US, "@%d snapshot=(null)", trace.timestampMs());
        }
        return String.format(
                Locale.US,
                "@%d det=%.1f active=%s lock=%s tgt=%d eff=%d hyp=%d thr=%d rel=%d"
                        + " lockR=%.2f near=%.2f unlock=%.2f dom=%.2f iso=%.2f"
                        + " contrast=%.2f on=%s rescue=%s suppress=%s far=%s"
                        + " hold=%s holdRel=%d holdReq=%.1f trust=%s weak=%s stable=%s",
                trace.timestampMs(),
                trace.detectionLevel(),
                snapshot.toneActive(),
                snapshot.targetToneLocked(),
                snapshot.targetToneFrequencyHz(),
                snapshot.effectiveTrackedToneFrequencyHz(),
                snapshot.toneHypothesisFrequencyHz(),
                snapshot.currentThreshold(),
                snapshot.releaseThreshold(),
                snapshot.recentLockedFrameRatio(),
                snapshot.recentNearTargetLockedFrameRatio(),
                snapshot.recentActiveUnlockedFrameRatio(),
                snapshot.toneDominanceRatio(),
                snapshot.narrowbandIsolationRatio(),
                trace.localContrastRatio(),
                safe(trace.toneOnDecision()),
                safe(trace.postReleaseRescueDecision()),
                safe(trace.postReleaseSuppressionDecision()),
                safe(trace.farAttackDelayDecision()),
                safe(trace.releaseTailHoldDecision()),
                trace.toneActiveReleaseThreshold(),
                trace.releaseTailHoldRequiredDetectionThreshold(),
                trace.releaseTailHoldSufficientRecentTrust(),
                trace.releaseTailHoldCurrentRunWeakBootstrapEligible(),
                trace.releaseTailHoldCurrentRunStableBootstrapEligible()
        );
    }

    private static void printLocalWindowCase(
            String label,
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult result
    ) {
        System.out.println("-- " + label + " --");
        System.out.println("text=" + sanitize(result.probeResult().decodedText()));
        for (CwDecodeEvent decodeEvent : result.decodeEvents()) {
            if (decodeEvent == null) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "  @%d %-18s emit=%s seq=%s out=%s unknown=%s",
                    decodeEvent.timestampMs(),
                    decodeEvent.type(),
                    sanitize(decodeEvent.emittedValue()),
                    sanitize(decodeEvent.sourceSequence()),
                    sanitize(decodeEvent.outputText()),
                    decodeEvent.unknownCharacter()
                ));
        }
    }

    private static void printForcedLocalWindowCase(
            String label,
            LocalAudioDecodeTestSupport.ForcedToneReplayResult result
    ) {
        System.out.println("-- " + label + " --");
        System.out.println("text=" + sanitize(result.decodedText()));
        for (CwDecodeEvent decodeEvent : result.decodeEvents()) {
            if (decodeEvent == null) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "  @%d %-18s emit=%s seq=%s out=%s unknown=%s",
                    decodeEvent.timestampMs(),
                    decodeEvent.type(),
                    sanitize(decodeEvent.emittedValue()),
                    sanitize(decodeEvent.sourceSequence()),
                    sanitize(decodeEvent.outputText()),
                    decodeEvent.unknownCharacter()
            ));
        }
    }

    private static LocalAudioDecodeTestSupport.TimingStateTrace firstTrustedTimingTrace(
            List<LocalAudioDecodeTestSupport.TimingStateTrace> traces
    ) {
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : traces) {
            if (trace != null
                    && trace.debugSnapshot() != null
                    && trace.debugSnapshot().trustedDotEstimateMs() > 0.0d) {
                return trace;
            }
        }
        return null;
    }

    private static LocalAudioDecodeTestSupport.TimingStateTrace firstTrustedTimingTraceInWindow(
            List<LocalAudioDecodeTestSupport.TimingStateTrace> traces,
            long windowStartTimestampMs,
            long windowEndTimestampMs
    ) {
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : traces) {
            if (trace == null
                    || trace.debugSnapshot() == null
                    || trace.timestampMs() < windowStartTimestampMs
                    || trace.timestampMs() > windowEndTimestampMs) {
                continue;
            }
            if (trace.debugSnapshot().trustedDotEstimateMs() > 0.0d) {
                return trace;
            }
        }
        return null;
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

    private static ToneReplayResult replayMergedShortGaps(
            List<CwToneEvent> toneEvents,
            long flushTimestampMs,
            long gapThresholdMs
    ) {
        return replayMergedShortGaps(
                toneEvents,
                flushTimestampMs,
                gapThresholdMs,
                Long.MIN_VALUE,
                Long.MAX_VALUE
        );
    }

    private static ToneReplayResult replayMergedShortGaps(
            List<CwToneEvent> toneEvents,
            long flushTimestampMs,
            long gapThresholdMs,
            long mergeWindowStartMs,
            long mergeWindowEndMs
    ) {
        ArrayList<CwToneEvent> mergedEvents = new ArrayList<>();
        int mergedPairCount = 0;
        for (int index = 0; index < toneEvents.size(); index++) {
            CwToneEvent toneEvent = toneEvents.get(index);
            if (toneEvent == null) {
                continue;
            }
            if (toneEvent.type() == CwToneEvent.Type.TONE_OFF
                    && index + 1 < toneEvents.size()
                    && toneEvents.get(index + 1) != null
                    && toneEvents.get(index + 1).type() == CwToneEvent.Type.TONE_ON
                    && !mergedEvents.isEmpty()
                    && mergedEvents.get(mergedEvents.size() - 1).type() == CwToneEvent.Type.TONE_ON
                    && toneEvent.timestampMs() >= mergeWindowStartMs
                    && toneEvents.get(index + 1).timestampMs() <= mergeWindowEndMs) {
                long gapMs = toneEvents.get(index + 1).timestampMs() - toneEvent.timestampMs();
                if (gapMs <= gapThresholdMs) {
                    mergedPairCount += 1;
                    index += 1;
                    continue;
                }
            }
            if (toneEvent.type() == CwToneEvent.Type.TONE_OFF
                    && !mergedEvents.isEmpty()
                    && mergedEvents.get(mergedEvents.size() - 1).type() == CwToneEvent.Type.TONE_ON) {
                CwToneEvent toneOnEvent = mergedEvents.get(mergedEvents.size() - 1);
                mergedEvents.add(new CwToneEvent(
                        CwToneEvent.Type.TONE_OFF,
                        toneEvent.timestampMs(),
                        toneEvent.peakAmplitude(),
                        toneEvent.rmsAmplitude(),
                        Math.max(0L, toneEvent.timestampMs() - toneOnEvent.timestampMs())
                ));
                continue;
            }
            mergedEvents.add(toneEvent);
        }

        CwHybridTimingModel timingModel = new CwHybridTimingModel();
        CwDecoder decoder = new CwDecoder();
        CwInterpreter interpreter = new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);
        QsoStateMachine qsoStateMachine = new QsoStateMachine();
        ArrayList<CwDecodeEvent> decodeEvents = new ArrayList<>();
        for (CwToneEvent mergedEvent : mergedEvents) {
            List<CwTimingEvent> timingEvents = timingModel.process(mergedEvent);
            drainTimingEvents(timingEvents, decoder, interpreter, qsoStateMachine, decodeEvents);
        }
        List<CwTimingEvent> flushedTimingEvents = timingModel.flushPendingGap(flushTimestampMs);
        drainTimingEvents(flushedTimingEvents, decoder, interpreter, qsoStateMachine, decodeEvents);
        drainDecodeEvents(
                decoder.flushPendingCharacter(flushTimestampMs),
                interpreter,
                qsoStateMachine,
                decodeEvents
        );
        return new ToneReplayResult(decoder.snapshot().decodedText(), mergedPairCount);
    }

    private static void drainTimingEvents(
            List<CwTimingEvent> timingEvents,
            CwDecoder decoder,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            List<CwDecodeEvent> capturedDecodeEvents
    ) {
        for (CwTimingEvent timingEvent : timingEvents) {
            drainDecodeEvents(decoder.process(timingEvent), interpreter, qsoStateMachine, capturedDecodeEvents);
        }
    }

    private static void drainDecodeEvents(
            List<CwDecodeEvent> decodeEvents,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            List<CwDecodeEvent> capturedDecodeEvents
    ) {
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            interpreter.process(decodeEvent);
            qsoStateMachine.process(interpreter.snapshot(), decodeEvent.timestampMs());
            capturedDecodeEvents.add(decodeEvent);
        }
    }

    private static EdgeEnvelopeProbe analyzeEdgeEnvelope(AudioFrame frame) {
        short[] samples = frame == null ? null : frame.samples();
        if (samples == null || samples.length == 0) {
            return new EdgeEnvelopeProbe(0.0d, -1, -1, -1, -1, "", "");
        }
        double[] envelope = buildAbsoluteEnvelope(samples);
        double envelopeMax = 0.0d;
        double envelopeMin = Double.MAX_VALUE;
        for (double value : envelope) {
            envelopeMax = Math.max(envelopeMax, value);
            envelopeMin = Math.min(envelopeMin, value);
        }
        double threshold = Math.max(
                envelopeMax * EDGE_THRESHOLD_RATIO,
                envelopeMin + ((envelopeMax - envelopeMin) * EDGE_DYNAMIC_RATIO)
        );
        int edgeRegionLength = Math.max(EDGE_WINDOW_SAMPLES * 2, samples.length / 5);
        int firstAboveIndex = firstAboveThresholdIndex(envelope, threshold);
        int lastAboveIndex = lastAboveThresholdIndex(envelope, threshold);
        int risingEdgeIndex = risingEdgeIndex(envelope, threshold, edgeRegionLength);
        int fallingEdgeIndex = fallingEdgeIndex(envelope, threshold, edgeRegionLength);
        return new EdgeEnvelopeProbe(
                threshold,
                firstAboveIndex,
                lastAboveIndex,
                risingEdgeIndex,
                fallingEdgeIndex,
                buildThresholdMask(envelope, threshold),
                buildThresholdMask(envelope, threshold * 0.75d)
        );
    }

    private static double[] buildAbsoluteEnvelope(short[] samples) {
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

    private static int risingEdgeIndex(double[] envelope, double threshold, int edgeRegionLength) {
        double earlyMax = maxEnvelope(envelope, 0, edgeRegionLength);
        double lateAverage = averageEnvelope(envelope, envelope.length - edgeRegionLength, envelope.length);
        if (earlyMax >= (threshold * EDGE_TRANSITION_REQUIRED_RATIO) || lateAverage <= threshold) {
            return -1;
        }
        for (int index = 0; index <= (envelope.length - EDGE_CONFIRM_SAMPLES); index++) {
            if (allAboveThreshold(envelope, index, EDGE_CONFIRM_SAMPLES, threshold)) {
                return index;
            }
        }
        return -1;
    }

    private static int fallingEdgeIndex(double[] envelope, double threshold, int edgeRegionLength) {
        double earlyAverage = averageEnvelope(envelope, 0, edgeRegionLength);
        double earlyMax = maxEnvelope(envelope, 0, edgeRegionLength);
        double lateMax = maxEnvelope(envelope, envelope.length - edgeRegionLength, envelope.length);
        if (((earlyAverage <= threshold)
                && earlyMax < (threshold * EDGE_TRANSITION_REQUIRED_RATIO))
                || lateMax >= (threshold * EDGE_TRANSITION_REQUIRED_RATIO)) {
            return -1;
        }
        for (int index = envelope.length - 1; index >= 0; index--) {
            if (envelope[index] >= threshold) {
                return Math.min(envelope.length - 1, index + 1);
            }
        }
        return -1;
    }

    private static int firstAboveThresholdIndex(double[] envelope, double threshold) {
        for (int index = 0; index < envelope.length; index++) {
            if (envelope[index] >= threshold) {
                return index;
            }
        }
        return -1;
    }

    private static int lastAboveThresholdIndex(double[] envelope, double threshold) {
        for (int index = envelope.length - 1; index >= 0; index--) {
            if (envelope[index] >= threshold) {
                return index;
            }
        }
        return -1;
    }

    private static boolean allAboveThreshold(double[] envelope, int start, int count, double threshold) {
        int safeEnd = Math.min(envelope.length, start + count);
        for (int index = start; index < safeEnd; index++) {
            if (envelope[index] < threshold) {
                return false;
            }
        }
        return safeEnd > start;
    }

    private static double averageEnvelope(double[] envelope, int start, int end) {
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

    private static double maxEnvelope(double[] envelope, int start, int end) {
        int safeStart = Math.max(0, start);
        int safeEnd = Math.min(envelope.length, end);
        double maximum = 0.0d;
        for (int index = safeStart; index < safeEnd; index++) {
            maximum = Math.max(maximum, envelope[index]);
        }
        return maximum;
    }

    private static String buildThresholdMask(double[] envelope, double threshold) {
        StringBuilder builder = new StringBuilder(EDGE_MASK_BINS);
        for (int bin = 0; bin < EDGE_MASK_BINS; bin++) {
            int start = (bin * envelope.length) / EDGE_MASK_BINS;
            int end = ((bin + 1) * envelope.length) / EDGE_MASK_BINS;
            builder.append(maxEnvelope(envelope, start, end) >= threshold ? '#' : '.');
        }
        return builder.toString();
    }

    private static String formatSampleOffsetMs(int sampleIndex, int sampleRateHz) {
        if (sampleIndex < 0 || sampleRateHz <= 0) {
            return "-";
        }
        return String.format(Locale.US, "%.1fms", (sampleIndex * 1000.0d) / sampleRateHz);
    }

    private static String sliceNewText(String previousText, String currentText) {
        String safePrevious = previousText == null ? "" : previousText;
        String safeCurrent = currentText == null ? "" : currentText;
        if (safePrevious.length() >= safeCurrent.length()) {
            return "";
        }
        return safeCurrent.substring(safePrevious.length());
    }

    private static List<AudioFrame> buildFrames(short[] samples, int sampleRateHz) {
        ArrayList<AudioFrame> frames = new ArrayList<>();
        long sampleOffset = 0L;
        for (int offset = 0; offset < samples.length; offset += FRAME_SIZE_SAMPLES) {
            int frameLength = Math.min(FRAME_SIZE_SAMPLES, samples.length - offset);
            short[] frameSamples = new short[frameLength];
            System.arraycopy(samples, offset, frameSamples, 0, frameLength);
            frames.add(LocalAudioDecodeTestSupport.buildFrameForProbe(frameSamples, sampleRateHz, sampleOffset));
            sampleOffset += frameLength;
        }
        return frames;
    }

    private static List<AudioFrame> normalizeFramesToZero(List<AudioFrame> frames) {
        ArrayList<AudioFrame> normalized = new ArrayList<>(frames.size());
        if (frames.isEmpty()) {
            return normalized;
        }
        long firstTimestampMs = frames.get(0).capturedAtMs();
        for (AudioFrame frame : frames) {
            normalized.add(new AudioFrame(
                    frame.samples(),
                    frame.sampleRateHz(),
                    frame.channelCount(),
                    frame.peakAmplitude(),
                    frame.rmsAmplitude(),
                    frame.clippedSampleCount(),
                    Math.max(0L, frame.capturedAtMs() - firstTimestampMs)
            ));
        }
        return normalized;
    }

    private static List<AudioFrame> sliceFrames(
            List<AudioFrame> frames,
            long startTimestampMs,
            long endTimestampMs
    ) {
        ArrayList<AudioFrame> sliced = new ArrayList<>();
        for (AudioFrame frame : frames) {
            if (frame == null
                    || frame.capturedAtMs() < startTimestampMs
                    || frame.capturedAtMs() > endTimestampMs) {
                continue;
            }
            sliced.add(frame);
        }
        return normalizeFramesToZero(sliced);
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "(empty)";
        }
        String normalized = text.replace('\u25A1', '?').trim();
        return normalized.isEmpty() ? "(empty)" : normalized;
    }

    private static String safe(String text) {
        return text == null || text.isEmpty() ? "-" : text;
    }

    private static final class EdgeEnvelopeProbe {
        private final double threshold;
        private final int firstAboveIndex;
        private final int lastAboveIndex;
        private final int risingEdgeIndex;
        private final int fallingEdgeIndex;
        private final String hardMask;
        private final String softMask;

        private EdgeEnvelopeProbe(
                double threshold,
                int firstAboveIndex,
                int lastAboveIndex,
                int risingEdgeIndex,
                int fallingEdgeIndex,
                String hardMask,
                String softMask
        ) {
            this.threshold = threshold;
            this.firstAboveIndex = firstAboveIndex;
            this.lastAboveIndex = lastAboveIndex;
            this.risingEdgeIndex = risingEdgeIndex;
            this.fallingEdgeIndex = fallingEdgeIndex;
            this.hardMask = hardMask;
            this.softMask = softMask;
        }

        double threshold() {
            return threshold;
        }

        int firstAboveIndex() {
            return firstAboveIndex;
        }

        int lastAboveIndex() {
            return lastAboveIndex;
        }

        int risingEdgeIndex() {
            return risingEdgeIndex;
        }

        int fallingEdgeIndex() {
            return fallingEdgeIndex;
        }

        String hardMask() {
            return hardMask;
        }

        String softMask() {
            return softMask;
        }
    }

    private static final class ToneReplayResult {
        private final String decodedText;
        private final int mergedPairCount;

        private ToneReplayResult(String decodedText, int mergedPairCount) {
            this.decodedText = decodedText;
            this.mergedPairCount = mergedPairCount;
        }

        String decodedText() {
            return decodedText;
        }

        int mergedPairCount() {
            return mergedPairCount;
        }
    }
}
