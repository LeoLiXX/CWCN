package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.rx.RxDeveloperStartupToneHintAnalyzer;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class RxDeveloperStartupToneHintAnalyzerTest {
    private static final int BASE_PREFERRED_TONE_HZ = 700;

    @Test
    public void acceptsOnlyTheKnownDirtyUpperSideStartupCase() throws Exception {
        RxDeveloperStartupToneHintAnalyzer.Result result = analyze("20260427_224524");

        assertTrue(describe(result), result.accepted());
        assertTrue(
                describe(result),
                result.suggestedToneHz() >= 730 && result.suggestedToneHz() <= 740
        );
        assertTrue(result.supportFrames() >= 3);
    }

    @Test
    public void rejectsTheKnownLowerSideDirtyCase() throws Exception {
        RxDeveloperStartupToneHintAnalyzer.Result result = analyze("20260427_222505");

        assertFalse(describe(result), result.accepted());
    }

    @Test
    public void rejectsRepresentativeNormalSamplesSoItStaysConservative() throws Exception {
        assertRejected("录音 (7)");
        assertRejected("录音 (10)");
        assertRejected("录音 (12)");
        assertRejected("录音 (13)");
        assertRejected("录音 (14)");
        assertRejected("录音 (15)");
    }

    private static void assertRejected(String sourceLabel) throws Exception {
        RxDeveloperStartupToneHintAnalyzer.Result result = analyze(sourceLabel);
        assertFalse(
                sourceLabel + " unexpectedly accepted: " + describe(result),
                result.accepted()
        );
    }

    private static RxDeveloperStartupToneHintAnalyzer.Result analyze(String sourceLabel) throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> matchesSourceLabel(fileNameWithoutExtension(path), sourceLabel))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for " + sourceLabel));
        List<AudioFrame> frames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );
        return new RxDeveloperStartupToneHintAnalyzer().analyze(frames, BASE_PREFERRED_TONE_HZ);
    }

    private static boolean matchesSourceLabel(String candidateLabel, String sourceLabel) {
        if (candidateLabel == null || sourceLabel == null) {
            return false;
        }
        return candidateLabel.equalsIgnoreCase(sourceLabel)
                || candidateLabel.endsWith(sourceLabel)
                || sourceLabel.endsWith(candidateLabel);
    }

    private static String fileNameWithoutExtension(Path wavFile) {
        String fileName = wavFile.getFileName().toString();
        return fileName.toLowerCase(Locale.US).endsWith(".wav")
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;
    }

    private static String describe(RxDeveloperStartupToneHintAnalyzer.Result result) {
        return result.decisionCode()
                + " tone="
                + result.suggestedToneHz()
                + " cluster="
                + result.clusterSummary()
                + " support="
                + result.supportFrames();
    }
}
