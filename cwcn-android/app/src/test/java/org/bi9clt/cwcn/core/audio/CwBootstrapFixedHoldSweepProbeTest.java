package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertTrue;

public final class CwBootstrapFixedHoldSweepProbeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;
    private static final long[] FIXED_HOLD_MS_LEVELS = new long[]{0L, 48L, 96L, 160L, 240L, 320L, 480L, 640L};

    @Test
    public void printRecording7And3FixedHoldSweep() throws Exception {
        printCase("recording7", "(7).wav");
        printCase("recording3", "(3).wav");
        assertTrue(true);
    }

    private static void printCase(String label, String suffix) throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith(suffix))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for " + suffix));
        List<AudioFrame> frames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );

        System.out.println("==== " + label + " fixed-hold sweep ====");
        for (long fixedHoldMs : FIXED_HOLD_MS_LEVELS) {
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                    LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeFixedHoldThenAuto(
                            label + "-hold-" + fixedHoldMs,
                            frames,
                            PREFERRED_TONE_HZ,
                            SEED_WPM,
                            SQL_PERCENT,
                            false,
                            fixedHoldMs,
                            CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                    );
            System.out.println(String.format(
                    Locale.US,
                    "hold=%dms trust=%d opening=%s final=%s",
                    fixedHoldMs,
                    firstTrustTimestampMs(detailed),
                    sanitize(textAtOrBefore(
                            detailed.rawDecodeEvents(),
                            resolveOpeningWindowEndMs(detailed)
                    )),
                    sanitize(detailed.probeResult().decodedText())
            ));
        }
    }

    private static long firstTrustTimestampMs(LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed) {
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : detailed.timingStateTraces()) {
            if (trace != null
                    && trace.debugSnapshot() != null
                    && trace.debugSnapshot().trustedDotEstimateMs() > 0.0d) {
                return trace.timestampMs();
            }
        }
        return -1L;
    }

    private static long resolveOpeningWindowEndMs(LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed) {
        long trustTimestampMs = firstTrustTimestampMs(detailed);
        if (trustTimestampMs > 0L) {
            return trustTimestampMs;
        }
        return detailed.flushTimestampMs();
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
