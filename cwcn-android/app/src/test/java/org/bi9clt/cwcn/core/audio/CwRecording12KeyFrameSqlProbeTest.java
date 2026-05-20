package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertTrue;

public final class CwRecording12KeyFrameSqlProbeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 15;
    private static final long[] KEY_TIMESTAMPS_MS = new long[]{
            1952L, 2000L, 2016L, 2032L,
            8704L, 8816L, 8832L, 8848L, 8976L, 9040L, 9072L
    };

    @Test
    public void printRecording12KeyFramesAtSql15() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(12).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for recording (12)"));

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                        wavFile,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        System.out.println("==== recording12 key frames sql=15 ====");
        for (long timestampMs : KEY_TIMESTAMPS_MS) {
            LocalAudioDecodeTestSupport.FrameSignalTrace trace = traceAtOrAfter(
                    detailed.frameSignalTraces(),
                    timestampMs
            );
            if (trace == null || trace.snapshot() == null) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "@%d det=%.1f on=%s lock=%s aq=%s thr=%d/%d floor=%d/%d tone=%.1f dom=%.2f iso=%.2f",
                    trace.timestampMs(),
                    trace.detectionLevel(),
                    yesNo(trace.snapshot().toneActive()),
                    yesNo(trace.snapshot().targetToneLocked()),
                    yesNo(trace.attackQualified()),
                    trace.snapshot().currentThreshold(),
                    trace.snapshot().releaseThreshold(),
                    trace.snapshot().noiseFloorEstimate(),
                    trace.snapshot().signalFloorEstimate(),
                    trace.snapshot().lastToneRmsAmplitude(),
                    trace.snapshot().toneDominanceRatio(),
                    trace.snapshot().narrowbandIsolationRatio()
            ));
        }

        assertTrue(true);
    }

    private static LocalAudioDecodeTestSupport.FrameSignalTrace traceAtOrAfter(
            List<LocalAudioDecodeTestSupport.FrameSignalTrace> traces,
            long timestampMs
    ) {
        for (LocalAudioDecodeTestSupport.FrameSignalTrace trace : traces) {
            if (trace != null && trace.timestampMs() >= timestampMs) {
                return trace;
            }
        }
        return null;
    }

    private static String yesNo(boolean value) {
        return value ? "Y" : "N";
    }
}
