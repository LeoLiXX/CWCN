package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class CwLocalAudioTailEventProbeTest {
    private static final long TAIL_WINDOW_MS = 12000L;

    @Test
    public void printRecording8EffAndHypTailEvents() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().contains("(8)"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for recording (8)"));
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeWavFileDetailed(wavFile);
        LocalAudioDecodeTestSupport.ForcedToneReplayResult effReplay =
                LocalAudioDecodeTestSupport.replayForcedEffectiveTrackedToneDecode(detailed);
        LocalAudioDecodeTestSupport.ForcedToneReplayResult hypReplay =
                LocalAudioDecodeTestSupport.replayForcedHypothesisToneDecode(detailed);

        long endTimestampMs = Math.max(lastDecodeTimestampMs(effReplay), lastDecodeTimestampMs(hypReplay));
        long startTimestampMs = Math.max(0L, endTimestampMs - TAIL_WINDOW_MS);

        printReplayTail(effReplay, startTimestampMs, endTimestampMs);
        printReplayTail(hypReplay, startTimestampMs, endTimestampMs);
    }

    private static long lastDecodeTimestampMs(LocalAudioDecodeTestSupport.ForcedToneReplayResult replay) {
        List<CwDecodeEvent> decodeEvents = replay.decodeEvents();
        return decodeEvents.isEmpty() ? 0L : decodeEvents.get(decodeEvents.size() - 1).timestampMs();
    }

    private static void printReplayTail(
            LocalAudioDecodeTestSupport.ForcedToneReplayResult replay,
            long startTimestampMs,
            long endTimestampMs
    ) {
        System.out.println(
                "==== " + replay.sourceLabel() + " " + replay.mode()
                        + " tail " + startTimestampMs + "-" + endTimestampMs + "ms"
                        + " forcedLast=" + replay.lastForcedFrequencyHz() + "Hz ===="
        );
        System.out.println("text=" + replay.decodedText());
        System.out.println("-- tone --");
        for (CwToneEvent event : replay.toneEvents()) {
            if (event.timestampMs() < startTimestampMs) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "T %s @%d dur=%d rms=%.1f peak=%d",
                    event.type(),
                    event.timestampMs(),
                    event.toneDurationMs(),
                    event.rmsAmplitude(),
                    event.peakAmplitude()
            ));
        }
        System.out.println("-- timing --");
        for (CwTimingEvent event : replay.timingEvents()) {
            if (event.timestampMs() < startTimestampMs) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "M %s/%s @%d dur=%d dot=%d",
                    event.kind(),
                    event.classification(),
                    event.timestampMs(),
                    event.durationMs(),
                    event.dotEstimateMs()
            ));
        }
        System.out.println("-- decode --");
        for (CwDecodeEvent event : replay.decodeEvents()) {
            if (event.timestampMs() < startTimestampMs) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "D %s @%d out=%s emit=%s seq=%s unknown=%s",
                    event.type(),
                    event.timestampMs(),
                    sanitize(event.outputText()),
                    sanitize(event.emittedValue()),
                    sanitize(event.sourceSequence()),
                    event.unknownCharacter()
            ));
        }
    }

    private static String sanitize(String value) {
        return value == null ? "(null)" : value;
    }
}
