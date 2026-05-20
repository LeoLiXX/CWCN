package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertTrue;

public final class CwRecording3ModeSweepProbeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;

    @Test
    public void printRecording3ModeSweep() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(3).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (3)"));
        List<AudioFrame> frames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );

        System.out.println("==== recording3 mode sweep ====");
        printCase("HYBRID_BOOTSTRAP", LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                "recording3-hybrid",
                frames,
                PREFERRED_TONE_HZ,
                SEED_WPM,
                SQL_PERCENT,
                false,
                CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
        ));
        printCase("STATIC_FIXED_TONE", LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeWithTurnCarry(
                "recording3-fixed",
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
        ));
        printCase("STATIC_AUTO_TRACK", LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeWithTurnCarry(
                "recording3-auto",
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
        ));

        assertTrue(true);
    }

    private static void printCase(
            String label,
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed
    ) {
        long trustMs = firstTrustedOffsetMs(detailed);
        double trustDotMs = firstTrustedDotMs(detailed);
        System.out.println(String.format(
                Locale.US,
                "%s trust=%s dot=%s chars=%d turns=%d tone=%d/%d/%d on=%d off=%d rejects=%s",
                label,
                trustMs < 0L ? "-" : trustMs + "ms",
                trustDotMs <= 0.0d ? "-" : String.format(Locale.US, "%.1f", trustDotMs),
                detailed.probeResult().decoderSnapshot().totalCharacters(),
                countTurns(detailed.turnTransitionTraces()),
                detailed.probeResult().signalSnapshot().targetToneFrequencyHz(),
                detailed.probeResult().signalSnapshot().effectiveAcquisitionWinnerFrequencyHz(),
                detailed.probeResult().signalSnapshot().effectiveFinalAdoptedFrequencyHz(),
                detailed.probeResult().signalSnapshot().totalToneOnEvents(),
                detailed.probeResult().signalSnapshot().totalToneOffEvents(),
                detailed.stableRejectCounts()
        ));
        System.out.println("final=" + sanitize(detailed.probeResult().decodedText()));
        System.out.println("stable=" + sanitize(textAtOrBefore(
                detailed.stableAcceptedDecodeEvents(),
                detailed.flushTimestampMs()
        )));
        System.out.println("raw=" + sanitize(textAtOrBefore(
                detailed.rawDecodeEvents(),
                detailed.flushTimestampMs()
        )));
    }

    private static int countTurns(List<LocalAudioDecodeTestSupport.TurnTransitionTrace> traces) {
        int count = 0;
        for (LocalAudioDecodeTestSupport.TurnTransitionTrace trace : traces) {
            if (trace != null
                    && trace.kind() == LocalAudioDecodeTestSupport.TurnTransitionTrace.Kind.START) {
                count += 1;
            }
        }
        return count;
    }

    private static long firstTrustedOffsetMs(LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed) {
        if (detailed.frames().isEmpty()) {
            return -1L;
        }
        long firstFrameTimestampMs = detailed.frames().get(0).capturedAtMs();
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : detailed.timingStateTraces()) {
            if (trace == null
                    || trace.debugSnapshot() == null
                    || trace.debugSnapshot().trustedDotEstimateMs() <= 0.0d) {
                continue;
            }
            return Math.max(0L, trace.timestampMs() - firstFrameTimestampMs);
        }
        return -1L;
    }

    private static double firstTrustedDotMs(LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed) {
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : detailed.timingStateTraces()) {
            if (trace == null
                    || trace.debugSnapshot() == null
                    || trace.debugSnapshot().trustedDotEstimateMs() <= 0.0d) {
                continue;
            }
            return trace.debugSnapshot().trustedDotEstimateMs();
        }
        return 0.0d;
    }

    private static String textAtOrBefore(
            List<org.bi9clt.cwcn.core.decoder.CwDecodeEvent> decodeEvents,
            long timestampMs
    ) {
        String latestText = "";
        for (org.bi9clt.cwcn.core.decoder.CwDecodeEvent decodeEvent : decodeEvents) {
            if (decodeEvent != null && decodeEvent.timestampMs() <= timestampMs) {
                latestText = decodeEvent.outputText();
            } else if (decodeEvent != null && decodeEvent.timestampMs() > timestampMs) {
                break;
            }
        }
        return latestText == null ? "" : latestText;
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "(empty)";
        }
        String normalized = text.replace('\u25A1', '?').replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.isEmpty() ? "(empty)" : normalized;
    }
}
