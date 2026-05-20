package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel.DebugSnapshot;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CwRecording2BootstrapProbeTest {
    private static final int FRAME_SIZE_SAMPLES = 256;
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;
    private static final long OPENING_WINDOW_MS = 6000L;
    private static final long TURN2_OPENING_WINDOW_MS = 6000L;
    private static final int MAX_EVENT_LINES = 36;

    @Test
    public void printRecording2OpeningBootstrapProbe() throws Exception {
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

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult offline =
                LocalAudioDecodeTestSupport.decodeFramesDetailed("录音 (2)-offline", frames, false);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult configured =
                LocalAudioDecodeTestSupport.decodeFramesDetailedConfigured(
                        "录音 (2)-configured",
                        frames,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult live =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "录音 (2)-live",
                        frames,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        LocalAudioDecodeTestSupport.TimingStateTrace initTrace = firstTrustedTimingTraceInWindow(
                live.timingStateTraces(),
                0L,
                live.flushTimestampMs()
        );
        long initOffsetMs = initTrace == null ? -1L : initTrace.timestampMs();
        DebugSnapshot initDebugSnapshot = initTrace == null ? null : initTrace.debugSnapshot();

        String rawTextAtInit = initTrace == null
                ? ""
                : sliceNewText(
                textAtOrBefore(live.rawDecodeEvents(), 0L),
                textAtOrBefore(live.rawDecodeEvents(), initTrace.timestampMs())
        );
        String stableTextAtInit = initTrace == null
                ? ""
                : sliceNewText(
                textAtOrBefore(live.stableAcceptedDecodeEvents(), 0L),
                textAtOrBefore(live.stableAcceptedDecodeEvents(), initTrace.timestampMs())
        );

        System.out.println("==== recording2 opening bootstrap probe ====");
        System.out.println("expected=CQ DX CQ DX DE JV3VV JV3VV PAGE K. CQ DX CQ DX DE JV3VV JV3VV PAGE K.");
        System.out.println("offline final=" + sanitize(offline.probeResult().decodedText()));
        System.out.println("configured final=" + sanitize(configured.probeResult().decodedText()));
        System.out.println("live final=" + sanitize(live.probeResult().decodedText()));
        System.out.println("live raw-final=" + sanitize(textAtOrBefore(live.rawDecodeEvents(), live.flushTimestampMs())));
        System.out.println("live stable-final=" + sanitize(textAtOrBefore(live.stableAcceptedDecodeEvents(), live.flushTimestampMs())));
        System.out.println(String.format(
                Locale.US,
                "initOffset=%dms initReason=%s trustedDot=%.1f rawWpm=%.1f stableWpm=%.1f",
                initOffsetMs,
                initDebugSnapshot == null ? "none" : safe(initDebugSnapshot.lastTrustedUpdateReason()),
                initDebugSnapshot == null ? 0.0d : initDebugSnapshot.trustedDotEstimateMs(),
                initTrace == null || initTrace.rawSnapshot() == null ? 0.0d : initTrace.rawSnapshot().estimatedWpm(),
                initTrace == null || initTrace.stabilizedSnapshot() == null
                        ? 0.0d
                        : initTrace.stabilizedSnapshot().estimatedWpm()
        ));
        System.out.println("preTrust raw=" + sanitize(rawTextAtInit));
        System.out.println("preTrust stable=" + sanitize(stableTextAtInit));
        System.out.println("stableRejects=" + live.stableRejectCounts());
        System.out.println("bootstrapBoundaryRejects=" + live.bootstrapBoundaryRejectCounts());
        System.out.println("bootstrapCadenceRejects=" + live.bootstrapCadenceRejectCounts());

        System.out.println("-- raw-decode opening --");
        printDecodeEvents(live.rawDecodeEvents(), OPENING_WINDOW_MS, MAX_EVENT_LINES);
        System.out.println("-- stable-decode opening --");
        printDecodeEvents(live.stableAcceptedDecodeEvents(), OPENING_WINDOW_MS, MAX_EVENT_LINES);
        System.out.println("-- timing-events opening --");
        printTimingEvents(live.timingEvents(), OPENING_WINDOW_MS, MAX_EVENT_LINES);
        System.out.println("-- bootstrap-boundary opening --");
        printTimingEvents(live.bootstrapBoundaryTimingEvents(), OPENING_WINDOW_MS, MAX_EVENT_LINES);
        System.out.println("-- bootstrap-cadence opening --");
        printTimingEvents(live.bootstrapCadenceTimingEvents(), OPENING_WINDOW_MS, MAX_EVENT_LINES);
        System.out.println("-- timing-learning opening --");
        printTimingLearningDecisions(live.timingLearningDecisionTraces(), OPENING_WINDOW_MS, MAX_EVENT_LINES);
    }

    @Test
    public void printRecording2SecondTurnBootstrapProbe() throws Exception {
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

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult live =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "录音 (2)-live-turn2",
                        frames,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        LocalAudioDecodeTestSupport.TurnTransitionTrace turn2Start = null;
        LocalAudioDecodeTestSupport.TurnTransitionTrace turn2End = null;
        for (LocalAudioDecodeTestSupport.TurnTransitionTrace trace : live.turnTransitionTraces()) {
            if (trace == null) {
                continue;
            }
            if (trace.kind() == LocalAudioDecodeTestSupport.TurnTransitionTrace.Kind.START
                    && trace.turnIndex() == 2) {
                turn2Start = trace;
            } else if (trace.kind() == LocalAudioDecodeTestSupport.TurnTransitionTrace.Kind.END
                    && trace.turnIndex() == 2) {
                turn2End = trace;
                break;
            }
        }
        if (turn2Start == null) {
            throw new IllegalStateException("Missing turn 2 start transition");
        }
        long turn2StartMs = turn2Start.timestampMs();
        long turn2EndMs = turn2End == null ? live.flushTimestampMs() : turn2End.timestampMs();
        long turn2WindowEndMs = Math.min(turn2EndMs, turn2StartMs + TURN2_OPENING_WINDOW_MS);
        LocalAudioDecodeTestSupport.TimingStateTrace turn2TrustTrace = firstTrustedTimingTraceInWindow(
                live.timingStateTraces(),
                turn2StartMs,
                turn2EndMs
        );
        DebugSnapshot turn2DebugSnapshot = turn2TrustTrace == null ? null : turn2TrustTrace.debugSnapshot();
        String rawBeforeTurn2 = textAtOrBefore(live.rawDecodeEvents(), Math.max(0L, turn2StartMs - 1L));
        String stableBeforeTurn2 = textAtOrBefore(
                live.stableAcceptedDecodeEvents(),
                Math.max(0L, turn2StartMs - 1L)
        );
        String rawTurn2Opening = sliceNewText(
                rawBeforeTurn2,
                textAtOrBefore(live.rawDecodeEvents(), turn2WindowEndMs)
        );
        String stableTurn2Opening = sliceNewText(
                stableBeforeTurn2,
                textAtOrBefore(live.stableAcceptedDecodeEvents(), turn2WindowEndMs)
        );

        System.out.println("==== recording2 second turn bootstrap probe ====");
        System.out.println("final=" + sanitize(live.probeResult().decodedText()));
        System.out.println(String.format(
                Locale.US,
                "turn2=%d..%d windowEnd=%d trust=%dms trustOffset=%dms reason=%s trustedDot=%.1f",
                turn2StartMs,
                turn2EndMs,
                turn2WindowEndMs,
                turn2TrustTrace == null ? -1L : turn2TrustTrace.timestampMs(),
                turn2TrustTrace == null ? -1L : Math.max(0L, turn2TrustTrace.timestampMs() - turn2StartMs),
                turn2DebugSnapshot == null ? "none" : safe(turn2DebugSnapshot.lastTrustedUpdateReason()),
                turn2DebugSnapshot == null ? 0.0d : turn2DebugSnapshot.trustedDotEstimateMs()
        ));
        System.out.println("turn2 raw-opening=" + sanitize(rawTurn2Opening));
        System.out.println("turn2 stable-opening=" + sanitize(stableTurn2Opening));
        System.out.println("-- raw-decode turn2 opening --");
        printDecodeEventsInWindow(live.rawDecodeEvents(), turn2StartMs, turn2WindowEndMs, MAX_EVENT_LINES);
        System.out.println("-- stable-decode turn2 opening --");
        printDecodeEventsInWindow(
                live.stableAcceptedDecodeEvents(),
                turn2StartMs,
                turn2WindowEndMs,
                MAX_EVENT_LINES
        );
        System.out.println("-- timing-events turn2 opening --");
        printTimingEventsInWindow(live.timingEvents(), turn2StartMs, turn2WindowEndMs, MAX_EVENT_LINES);
        System.out.println("-- bootstrap-boundary turn2 opening --");
        printTimingEventsInWindow(
                live.bootstrapBoundaryTimingEvents(),
                turn2StartMs,
                turn2WindowEndMs,
                MAX_EVENT_LINES
        );
        System.out.println("-- bootstrap-cadence turn2 opening --");
        printTimingEventsInWindow(
                live.bootstrapCadenceTimingEvents(),
                turn2StartMs,
                turn2WindowEndMs,
                MAX_EVENT_LINES
        );
        System.out.println("-- timing-learning turn2 opening --");
        printTimingLearningDecisionsInWindow(
                live.timingLearningDecisionTraces(),
                turn2StartMs,
                turn2WindowEndMs,
                MAX_EVENT_LINES
        );
    }

    private static void printDecodeEvents(
            List<CwDecodeEvent> decodeEvents,
            long windowEndTimestampMs,
            int maxLines
    ) {
        int printed = 0;
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            if (decodeEvent == null || decodeEvent.timestampMs() > windowEndTimestampMs) {
                if (decodeEvent != null && decodeEvent.timestampMs() > windowEndTimestampMs) {
                    break;
                }
                continue;
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

    private static void printDecodeEventsInWindow(
            List<CwDecodeEvent> decodeEvents,
            long windowStartTimestampMs,
            long windowEndTimestampMs,
            int maxLines
    ) {
        int printed = 0;
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            if (decodeEvent == null || decodeEvent.timestampMs() < windowStartTimestampMs) {
                continue;
            }
            if (decodeEvent.timestampMs() > windowEndTimestampMs) {
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

    private static void printTimingEvents(
            List<CwTimingEvent> timingEvents,
            long windowEndTimestampMs,
            int maxLines
    ) {
        int printed = 0;
        for (CwTimingEvent timingEvent : timingEvents) {
            if (timingEvent == null || timingEvent.timestampMs() > windowEndTimestampMs) {
                if (timingEvent != null && timingEvent.timestampMs() > windowEndTimestampMs) {
                    break;
                }
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "@%d kind=%s class=%s dur=%d dot=%d intra=%d ratioDot=%.2f ratioIntra=%.2f",
                    timingEvent.timestampMs(),
                    timingEvent.kind(),
                    timingEvent.classification(),
                    timingEvent.durationMs(),
                    timingEvent.dotEstimateMs(),
                    timingEvent.intraGapEstimateMs(),
                    timingEvent.ratioToDotEstimate(),
                    timingEvent.ratioToIntraGapEstimate()
            ));
            printed += 1;
            if (printed >= maxLines) {
                break;
            }
        }
    }

    private static void printTimingEventsInWindow(
            List<CwTimingEvent> timingEvents,
            long windowStartTimestampMs,
            long windowEndTimestampMs,
            int maxLines
    ) {
        int printed = 0;
        for (CwTimingEvent timingEvent : timingEvents) {
            if (timingEvent == null || timingEvent.timestampMs() < windowStartTimestampMs) {
                continue;
            }
            if (timingEvent.timestampMs() > windowEndTimestampMs) {
                break;
            }
            System.out.println(String.format(
                    Locale.US,
                    "@%d kind=%s class=%s dur=%d dot=%d intra=%d ratioDot=%.2f ratioIntra=%.2f",
                    timingEvent.timestampMs(),
                    timingEvent.kind(),
                    timingEvent.classification(),
                    timingEvent.durationMs(),
                    timingEvent.dotEstimateMs(),
                    timingEvent.intraGapEstimateMs(),
                    timingEvent.ratioToDotEstimate(),
                    timingEvent.ratioToIntraGapEstimate()
            ));
            printed += 1;
            if (printed >= maxLines) {
                break;
            }
        }
    }

    private static void printTimingLearningDecisions(
            List<LocalAudioDecodeTestSupport.TimingLearningDecisionTrace> traces,
            long windowEndTimestampMs,
            int maxLines
    ) {
        int printed = 0;
        for (LocalAudioDecodeTestSupport.TimingLearningDecisionTrace trace : traces) {
            if (trace == null || trace.timestampMs() > windowEndTimestampMs) {
                if (trace != null && trace.timestampMs() > windowEndTimestampMs) {
                    break;
                }
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "@%d tone=%s decision=%s allow=%s trusted=%s locked=%s"
                            + " lock=%.2f near=%.2f unlock=%.2f dom=%.2f iso=%.2f hot=%.2f clip=%.2f"
                            + " rawWpm=%.1f rawDot=%d anchor=%s",
                    trace.timestampMs(),
                    safe(trace.toneEventType()),
                    safe(trace.decision()),
                    trace.allowTimingLearning(),
                    trace.trustedTimingEstablished(),
                    trace.targetToneLocked(),
                    trace.recentLockedFrameRatio(),
                    trace.recentNearTargetLockedFrameRatio(),
                    trace.recentActiveUnlockedFrameRatio(),
                    trace.toneDominanceRatio(),
                    trace.narrowbandIsolationRatio(),
                    trace.recentHotFrameRatio(),
                    trace.recentClippingFrameRatio(),
                    trace.rawWpm(),
                    trace.rawDotEstimateMs(),
                    safe(trace.anchorSummary())
            ));
            printed += 1;
            if (printed >= maxLines) {
                break;
            }
        }
    }

    private static void printTimingLearningDecisionsInWindow(
            List<LocalAudioDecodeTestSupport.TimingLearningDecisionTrace> traces,
            long windowStartTimestampMs,
            long windowEndTimestampMs,
            int maxLines
    ) {
        int printed = 0;
        for (LocalAudioDecodeTestSupport.TimingLearningDecisionTrace trace : traces) {
            if (trace == null || trace.timestampMs() < windowStartTimestampMs) {
                continue;
            }
            if (trace.timestampMs() > windowEndTimestampMs) {
                break;
            }
            System.out.println(String.format(
                    Locale.US,
                    "@%d tone=%s decision=%s allow=%s trusted=%s locked=%s"
                            + " lock=%.2f near=%.2f unlock=%.2f dom=%.2f iso=%.2f hot=%.2f clip=%.2f"
                            + " rawWpm=%.1f rawDot=%d anchor=%s",
                    trace.timestampMs(),
                    safe(trace.toneEventType()),
                    safe(trace.decision()),
                    trace.allowTimingLearning(),
                    trace.trustedTimingEstablished(),
                    trace.targetToneLocked(),
                    trace.recentLockedFrameRatio(),
                    trace.recentNearTargetLockedFrameRatio(),
                    trace.recentActiveUnlockedFrameRatio(),
                    trace.toneDominanceRatio(),
                    trace.narrowbandIsolationRatio(),
                    trace.recentHotFrameRatio(),
                    trace.recentClippingFrameRatio(),
                    trace.rawWpm(),
                    trace.rawDotEstimateMs(),
                    safe(trace.anchorSummary())
            ));
            printed += 1;
            if (printed >= maxLines) {
                break;
            }
        }
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

    private static String sanitize(String text) {
        if (text == null) {
            return "(empty)";
        }
        String normalized = text.replace('\u25A1', '?').trim();
        return normalized.isEmpty() ? "(empty)" : normalized;
    }

    private static String safe(String value) {
        return value == null || value.isEmpty() ? "(none)" : value;
    }
}
