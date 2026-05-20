package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel.DebugSnapshot;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CwRecording8OpeningProbeTest {
    private static final int FRAME_SIZE_SAMPLES = 256;
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;
    private static final long OPENING_WINDOW_MS = 22000L;
    private static final int MAX_EVENT_LINES = 96;
    private static final long FIRST_FAILURE_WINDOW_START_MS = 2400L;
    private static final long FIRST_FAILURE_WINDOW_END_MS = 4300L;
    private static final long SECOND_FAILURE_WINDOW_START_MS = 10900L;
    private static final long SECOND_FAILURE_WINDOW_END_MS = 12150L;
    private static final int MAX_FRAME_LINES = 96;

    @Test
    public void printRecording8OpeningProbe() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(8).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (8)"));
        LocalAudioDecodeTestSupport.WaveDataProbe waveData =
                LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);
        List<AudioFrame> frames = normalizeFramesToZero(buildFrames(
                waveData.samples(),
                waveData.sampleRateHz()
        ));

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult live =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "录音 (8)-live",
                        frames,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult raw =
                LocalAudioDecodeTestSupport.decodeFramesDetailed(
                        "录音 (8)-raw",
                        frames,
                        false
                );

        LocalAudioDecodeTestSupport.TimingStateTrace initTrace = firstTrustedTimingTraceInWindow(
                live.timingStateTraces(),
                0L,
                live.flushTimestampMs()
        );
        long initOffsetMs = initTrace == null ? -1L : initTrace.timestampMs();
        DebugSnapshot initDebugSnapshot = initTrace == null ? null : initTrace.debugSnapshot();

        System.out.println("==== recording8 opening probe ====");
        System.out.println("expected=CQ CQ DE BG1XXX K BG1XXX DE JA1ABC K JA1ABC DE BG1XXX ...");
        System.out.println("live final=" + sanitize(live.probeResult().decodedText()));
        System.out.println("live committed-final=" + sanitize(textAtOrBefore(
                live.decodeEvents(),
                live.flushTimestampMs()
        )));
        System.out.println("live raw-final=" + sanitize(textAtOrBefore(
                live.rawDecodeEvents(),
                live.flushTimestampMs()
        )));
        System.out.println("live stable-final=" + sanitize(textAtOrBefore(
                live.stableAcceptedDecodeEvents(),
                live.flushTimestampMs()
        )));
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
        System.out.println("stableRejects=" + live.stableRejectCounts());
        System.out.println("bootstrapBoundaryRejects=" + live.bootstrapBoundaryRejectCounts());
        System.out.println("bootstrapCadenceRejects=" + live.bootstrapCadenceRejectCounts());

        System.out.println("-- committed-decode opening --");
        printDecodeEvents(live.decodeEvents(), OPENING_WINDOW_MS, MAX_EVENT_LINES);
        System.out.println("-- raw-decode opening --");
        printDecodeEvents(live.rawDecodeEvents(), OPENING_WINDOW_MS, MAX_EVENT_LINES);
        System.out.println("-- stable-decode opening --");
        printDecodeEvents(live.stableAcceptedDecodeEvents(), OPENING_WINDOW_MS, MAX_EVENT_LINES);
        System.out.println("-- stable-decision first-failure window --");
        printStableDecisionWindow(
                live.stableDecisionTraces(),
                FIRST_FAILURE_WINDOW_START_MS,
                FIRST_FAILURE_WINDOW_END_MS,
                MAX_EVENT_LINES
        );
        System.out.println("-- timing-events opening --");
        printTimingEvents(live.timingEvents(), OPENING_WINDOW_MS, MAX_EVENT_LINES);
        System.out.println("-- tone-events second-failure window --");
        printToneEventsWindow(
                live.toneEvents(),
                SECOND_FAILURE_WINDOW_START_MS,
                SECOND_FAILURE_WINDOW_END_MS,
                MAX_EVENT_LINES
        );
        System.out.println("-- timing-events second-failure window --");
        printTimingEventsWindow(
                live.timingEvents(),
                SECOND_FAILURE_WINDOW_START_MS,
                SECOND_FAILURE_WINDOW_END_MS,
                MAX_EVENT_LINES
        );
        System.out.println("-- raw tone-events second-failure window --");
        printToneEventsWindow(
                raw.toneEvents(),
                SECOND_FAILURE_WINDOW_START_MS,
                SECOND_FAILURE_WINDOW_END_MS,
                MAX_EVENT_LINES
        );
        System.out.println("-- raw timing-events second-failure window --");
        printTimingEventsWindow(
                raw.timingEvents(),
                SECOND_FAILURE_WINDOW_START_MS,
                SECOND_FAILURE_WINDOW_END_MS,
                MAX_EVENT_LINES
        );
        System.out.println("-- bootstrap-boundary opening --");
        printTimingEvents(live.bootstrapBoundaryTimingEvents(), OPENING_WINDOW_MS, MAX_EVENT_LINES);
        System.out.println("-- bootstrap-boundary decisions first-failure window --");
        printBootstrapDecisionWindow(
                live.bootstrapBoundaryDecisionTraces(),
                FIRST_FAILURE_WINDOW_START_MS,
                FIRST_FAILURE_WINDOW_END_MS,
                MAX_EVENT_LINES
        );
        System.out.println("-- bootstrap-cadence opening --");
        printTimingEvents(live.bootstrapCadenceTimingEvents(), OPENING_WINDOW_MS, MAX_EVENT_LINES);
        System.out.println("-- bootstrap-cadence decisions first-failure window --");
        printBootstrapDecisionWindow(
                live.bootstrapCadenceDecisionTraces(),
                FIRST_FAILURE_WINDOW_START_MS,
                FIRST_FAILURE_WINDOW_END_MS,
                MAX_EVENT_LINES
        );
        System.out.println("-- timing-learning opening --");
        printTimingLearningDecisions(live.timingLearningDecisionTraces(), OPENING_WINDOW_MS, MAX_EVENT_LINES);
        System.out.println("-- timing-state first-failure window --");
        printTimingStateWindow(
                live.timingStateTraces(),
                FIRST_FAILURE_WINDOW_START_MS,
                FIRST_FAILURE_WINDOW_END_MS,
                MAX_EVENT_LINES
        );
        System.out.println("-- frame-signal first-failure window --");
        printFrameSignalWindow(
                live.frameSignalTraces(),
                FIRST_FAILURE_WINDOW_START_MS,
                FIRST_FAILURE_WINDOW_END_MS,
                MAX_FRAME_LINES
        );
        System.out.println("-- frame-signal second-failure window --");
        printFrameSignalWindow(
                live.frameSignalTraces(),
                SECOND_FAILURE_WINDOW_START_MS,
                SECOND_FAILURE_WINDOW_END_MS,
                MAX_FRAME_LINES
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

    private static void printToneEventsWindow(
            List<CwToneEvent> toneEvents,
            long windowStartTimestampMs,
            long windowEndTimestampMs,
            int maxLines
    ) {
        int printed = 0;
        for (CwToneEvent toneEvent : toneEvents) {
            if (toneEvent == null) {
                continue;
            }
            long timestampMs = toneEvent.timestampMs();
            if (timestampMs < windowStartTimestampMs || timestampMs > windowEndTimestampMs) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "@%d type=%s dur=%d rms=%.1f peak=%d",
                    timestampMs,
                    toneEvent.type(),
                    toneEvent.toneDurationMs(),
                    toneEvent.rmsAmplitude(),
                    toneEvent.peakAmplitude()
            ));
            printed += 1;
            if (printed >= maxLines) {
                break;
            }
        }
    }

    private static void printTimingEventsWindow(
            List<CwTimingEvent> timingEvents,
            long windowStartTimestampMs,
            long windowEndTimestampMs,
            int maxLines
    ) {
        int printed = 0;
        for (CwTimingEvent timingEvent : timingEvents) {
            if (timingEvent == null) {
                continue;
            }
            long timestampMs = timingEvent.timestampMs();
            if (timestampMs < windowStartTimestampMs || timestampMs > windowEndTimestampMs) {
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

    private static void printStableDecisionWindow(
            List<LocalAudioDecodeTestSupport.StableDecisionTrace> traces,
            long windowStartTimestampMs,
            long windowEndTimestampMs,
            int maxLines
    ) {
        int printed = 0;
        for (LocalAudioDecodeTestSupport.StableDecisionTrace trace : traces) {
            if (trace == null) {
                continue;
            }
            long timestampMs = trace.timestampMs();
            if (timestampMs < windowStartTimestampMs || timestampMs > windowEndTimestampMs) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "@%d emit=%s seq=%s decision=%s verified=%s trusted=%s locked=%s"
                            + " lock=%.2f near=%.2f unlock=%.2f dom=%.2f iso=%.2f hot=%.2f clip=%.2f rawWpm=%.1f",
                    timestampMs,
                    sanitize(trace.emittedValue()),
                    sanitize(trace.sourceSequence()),
                    safe(trace.compatibleDecision()),
                    safe(trace.verifiedDecision()),
                    trace.trustedTimingEstablished(),
                    trace.targetToneLocked(),
                    trace.recentLockedFrameRatio(),
                    trace.recentNearTargetLockedFrameRatio(),
                    trace.recentActiveUnlockedFrameRatio(),
                    trace.toneDominanceRatio(),
                    trace.narrowbandIsolationRatio(),
                    trace.recentHotFrameRatio(),
                    trace.recentClippingFrameRatio(),
                    trace.rawWpm()
            ));
            printed += 1;
            if (printed >= maxLines) {
                break;
            }
        }
    }

    private static void printBootstrapDecisionWindow(
            List<LocalAudioDecodeTestSupport.BootstrapDecisionTrace> traces,
            long windowStartTimestampMs,
            long windowEndTimestampMs,
            int maxLines
    ) {
        int printed = 0;
        for (LocalAudioDecodeTestSupport.BootstrapDecisionTrace trace : traces) {
            if (trace == null) {
                continue;
            }
            long timestampMs = trace.timestampMs();
            if (timestampMs < windowStartTimestampMs || timestampMs > windowEndTimestampMs) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "@%d kind=%s class=%s dur=%d candidateDot=%d decision=%s trusted=%s locked=%s"
                            + " lock=%.2f near=%.2f unlock=%.2f dom=%.2f iso=%.2f rawWpm=%.1f rawDot=%d anchor=%s",
                    timestampMs,
                    safe(trace.eventKind()),
                    safe(trace.classification()),
                    trace.durationMs(),
                    trace.candidateDotEstimateMs(),
                    safe(trace.decision()),
                    trace.trustedTimingEstablished(),
                    trace.targetToneLocked(),
                    trace.recentLockedFrameRatio(),
                    trace.recentNearTargetLockedFrameRatio(),
                    trace.recentActiveUnlockedFrameRatio(),
                    trace.toneDominanceRatio(),
                    trace.narrowbandIsolationRatio(),
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

    private static void printTimingStateWindow(
            List<LocalAudioDecodeTestSupport.TimingStateTrace> traces,
            long windowStartTimestampMs,
            long windowEndTimestampMs,
            int maxLines
    ) {
        int printed = 0;
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : traces) {
            if (trace == null || trace.debugSnapshot() == null) {
                continue;
            }
            long timestampMs = trace.timestampMs();
            if (timestampMs < windowStartTimestampMs || timestampMs > windowEndTimestampMs) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "@%d trustDot=%.1f reason=%s rawWpm=%.1f rawDot=%d stableWpm=%.1f summary=%s",
                    timestampMs,
                    trace.debugSnapshot().trustedDotEstimateMs(),
                    safe(trace.debugSnapshot().lastTrustedUpdateReason()),
                    trace.rawSnapshot() == null ? 0.0d : trace.rawSnapshot().estimatedWpmPrecise(),
                    trace.rawSnapshot() == null ? 0L : trace.rawSnapshot().dotEstimateMs(),
                    trace.stabilizedSnapshot() == null
                            ? 0.0d
                            : trace.stabilizedSnapshot().estimatedWpmPrecise(),
                    safe(trace.debugSummary())
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

    private static void printFrameSignalWindow(
            List<LocalAudioDecodeTestSupport.FrameSignalTrace> traces,
            long windowStartTimestampMs,
            long windowEndTimestampMs,
            int maxLines
    ) {
        int printed = 0;
        for (LocalAudioDecodeTestSupport.FrameSignalTrace trace : traces) {
            if (trace == null || trace.snapshot() == null) {
                continue;
            }
            long timestampMs = trace.timestampMs();
            if (timestampMs < windowStartTimestampMs || timestampMs > windowEndTimestampMs) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "@%d tone=%s lock=%s lck=%.2f near=%.2f unl=%.2f dom=%.2f iso=%.2f rms=%.1f thr=%d"
                            + " det=%.1f gap=%d/%d localOn=%d rewind=%d bridge=%s hold=%s toneOn=%s rescue=%s suppress=%s",
                    timestampMs,
                    trace.snapshot().toneActive(),
                    trace.snapshot().targetToneLocked(),
                    trace.snapshot().recentLockedFrameRatio(),
                    trace.snapshot().recentNearTargetLockedFrameRatio(),
                    trace.snapshot().recentActiveUnlockedFrameRatio(),
                    trace.snapshot().toneDominanceRatio(),
                    trace.snapshot().narrowbandIsolationRatio(),
                    trace.snapshot().lastToneRmsAmplitude(),
                    trace.toneOnThreshold(),
                    trace.detectionLevel(),
                    trace.postReleaseGapMs(),
                    trace.postReleaseWindowMs(),
                    trace.frameLocalToneOnTimestampMs(),
                    trace.frameLocalToneOnTimestampMs() >= 0L
                            ? Math.max(0L, timestampMs - trace.frameLocalToneOnTimestampMs())
                            : -1L,
                    trace.weakValleyBridgeActive(),
                    trace.releaseTailHoldDecision(),
                    safe(trace.toneOnDecision()),
                    safe(trace.postReleaseRescueDecision()),
                    safe(trace.postReleaseSuppressionDecision())
            ));
            printed += 1;
            if (printed >= maxLines) {
                break;
            }
        }
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
