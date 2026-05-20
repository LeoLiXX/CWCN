package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.junit.Test;

import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public final class CwRecording16LiveCommittedRawRegressionTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;
    private static final String EXPECTED_TEXT =
            "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 700 24WPM PSE K.";

    @Test
    public void liveLikeCommittedRawKeepsRecording16OpeningPrefix() throws Exception {
        Path recording16Wav = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(16).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (16)"));

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                        recording16Wav,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        assertEquals(
                EXPECTED_TEXT,
                sanitize(detailed.probeResult().decodedText())
        );
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace('\u25A1', '?').trim();
        return normalized.isEmpty() ? "(empty)" : normalized;
    }
}
