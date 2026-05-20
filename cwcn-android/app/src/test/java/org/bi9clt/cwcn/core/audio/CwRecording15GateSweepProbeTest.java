package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class CwRecording15GateSweepProbeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;

    @Test
    public void printRecording15GateSweep() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(15).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for recording (15)"));
        List<AudioFrame> frames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );

        System.out.println("==== recording(15) gate sweep ====");
        printCase("baseline", frames, false, false, false, false);
        printCase("merge-only", frames, false, false, true, false);
        printCase("hold-only", frames, false, false, false, true);
        printCase("onset-merge", frames, false, true, true, false);
        printCase("auth-onset-merge", frames, true, true, true, false);
        printCase("all-gates", frames, true, true, true, true);
    }

    private static void printCase(
            String label,
            List<AudioFrame> frames,
            boolean authority,
            boolean onset,
            boolean merge,
            boolean hold
    ) {
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult result =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "recording15-" + label,
                        frames,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        authority,
                        onset,
                        merge,
                        hold,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
        System.out.println(String.format(
                Locale.US,
                "%s trust=%dms dot=%.1f turns=%d tone=%d/%d/%d text=%s",
                label,
                firstTrustTimestampMs(result),
                result.probeResult().timingSnapshot().dotEstimateMs() * 1.0d,
                countTurns(result),
                result.probeResult().signalSnapshot().targetToneFrequencyHz(),
                result.probeResult().signalSnapshot().effectiveTrackedToneFrequencyHz(),
                result.probeResult().signalSnapshot().effectiveFinalAdoptedFrequencyHz(),
                sanitize(result.probeResult().decodedText())
        ));
    }

    private static long firstTrustTimestampMs(LocalAudioDecodeTestSupport.OfflineDetailedProbeResult result) {
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : result.timingStateTraces()) {
            if (trace != null
                    && trace.debugSnapshot() != null
                    && trace.debugSnapshot().trustedDotEstimateMs() > 0.0d) {
                return trace.timestampMs();
            }
        }
        return -1L;
    }

    private static int countTurns(LocalAudioDecodeTestSupport.OfflineDetailedProbeResult result) {
        int count = 0;
        for (LocalAudioDecodeTestSupport.TurnTransitionTrace trace : result.turnTransitionTraces()) {
            if (trace != null
                    && trace.kind() == LocalAudioDecodeTestSupport.TurnTransitionTrace.Kind.START) {
                count += 1;
            }
        }
        return count;
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.replace('\u25A1', '?');
    }
}
