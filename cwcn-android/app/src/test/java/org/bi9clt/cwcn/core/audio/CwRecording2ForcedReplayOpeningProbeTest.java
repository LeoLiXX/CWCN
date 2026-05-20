package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class CwRecording2ForcedReplayOpeningProbeTest {
    private static final long WINDOW_END_MS = 6500L;
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;

    @Test
    public void printRecording2ForcedReplayOpeningComparison() throws Exception {
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
                        "录音 (2)-live",
                        frames,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult liveAuto =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "录音 (2)-live-auto",
                        frames,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        false,
                        false,
                        false,
                        false,
                        org.bi9clt.cwcn.core.signal.CwSignalProcessor.RxToneMode.AUTO_TRACK,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult liveFixed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "录音 (2)-live-fixed",
                        frames,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        false,
                        false,
                        false,
                        false,
                        org.bi9clt.cwcn.core.signal.CwSignalProcessor.RxToneMode.FIXED_TONE,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
        LocalAudioDecodeTestSupport.ForcedToneReplayResult trackedReplay =
                LocalAudioDecodeTestSupport.replayForcedTrackedToneDecode(live);
        LocalAudioDecodeTestSupport.ForcedToneReplayResult effectiveReplay =
                LocalAudioDecodeTestSupport.replayForcedEffectiveTrackedToneDecode(live);
        LocalAudioDecodeTestSupport.ForcedToneReplayResult hypothesisReplay =
                LocalAudioDecodeTestSupport.replayForcedHypothesisToneDecode(live);
        LocalAudioDecodeTestSupport.ForcedToneReplayResult fixed700Replay =
                LocalAudioDecodeTestSupport.replayForcedConstantToneDecode(live, 700);

        System.out.println("==== recording2 forced replay opening comparison ====");
        System.out.println("expectedOpening=CQ DX CQ DX DE JV3VV JV3VV PAGE K.");
        printOpening("LIVE-HYB", live.rawDecodeEvents(), live.timingEvents(), live.toneEvents());
        printOpening("LIVE-AUTO", liveAuto.rawDecodeEvents(), liveAuto.timingEvents(), liveAuto.toneEvents());
        printOpening("LIVE-FIXED", liveFixed.rawDecodeEvents(), liveFixed.timingEvents(), liveFixed.toneEvents());
        printOpening("TRK", trackedReplay.decodeEvents(), trackedReplay.timingEvents(), trackedReplay.toneEvents());
        printOpening("EFF", effectiveReplay.decodeEvents(), effectiveReplay.timingEvents(), effectiveReplay.toneEvents());
        printOpening("HYP", hypothesisReplay.decodeEvents(), hypothesisReplay.timingEvents(), hypothesisReplay.toneEvents());
        printOpening("FIX700", fixed700Replay.decodeEvents(), fixed700Replay.timingEvents(), fixed700Replay.toneEvents());
    }

    private static void printOpening(
            String label,
            List<CwDecodeEvent> decodeEvents,
            List<CwTimingEvent> timingEvents,
            List<CwToneEvent> toneEvents
    ) {
        System.out.println("-- " + label + " --");
        System.out.println("text=" + sanitize(textAtOrBefore(decodeEvents, WINDOW_END_MS)));
        System.out.println("tone:");
        for (CwToneEvent event : toneEvents) {
            if (event.timestampMs() > WINDOW_END_MS) {
                break;
            }
            System.out.println(String.format(
                    Locale.US,
                    "  @%d %-8s dur=%d rms=%.1f",
                    event.timestampMs(),
                    event.type(),
                    event.toneDurationMs(),
                    event.rmsAmplitude()
            ));
        }
        System.out.println("timing:");
        for (CwTimingEvent event : timingEvents) {
            if (event.timestampMs() > WINDOW_END_MS) {
                break;
            }
            System.out.println(String.format(
                    Locale.US,
                    "  @%d %s/%s dur=%d dot=%d",
                    event.timestampMs(),
                    event.kind(),
                    event.classification(),
                    event.durationMs(),
                    event.dotEstimateMs()
            ));
        }
        System.out.println("decode:");
        for (CwDecodeEvent event : decodeEvents) {
            if (event.timestampMs() > WINDOW_END_MS) {
                break;
            }
            System.out.println(String.format(
                    Locale.US,
                    "  @%d %-18s emit=%s seq=%s out=%s unknown=%s",
                    event.timestampMs(),
                    event.type(),
                    sanitize(event.emittedValue()),
                    sanitize(event.sourceSequence()),
                    sanitize(event.outputText()),
                    event.unknownCharacter()
            ));
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
        java.util.ArrayList<AudioFrame> frames = new java.util.ArrayList<>();
        long sampleOffset = 0L;
        for (int offset = 0; offset < samples.length; offset += 256) {
            int frameLength = Math.min(256, samples.length - offset);
            short[] frameSamples = new short[frameLength];
            System.arraycopy(samples, offset, frameSamples, 0, frameLength);
            frames.add(LocalAudioDecodeTestSupport.buildFrameForProbe(frameSamples, sampleRateHz, sampleOffset));
            sampleOffset += frameLength;
        }
        return frames;
    }

    private static List<AudioFrame> normalizeFramesToZero(List<AudioFrame> frames) {
        java.util.ArrayList<AudioFrame> normalized = new java.util.ArrayList<>(frames.size());
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
}
