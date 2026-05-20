package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertTrue;

public final class CwDirtyCaseEarlyTimelineProbeTest {
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;
    private static final long PRINT_UNTIL_MS = 3200L;
    private static final int[] PREFERREDS_HZ = new int[]{640, 700, 760};

    @Test
    public void printEarlyTimelineFor224524PreferredVariants() throws Exception {
        Path wavFile = requireWavFile("20260427_224524");
        List<AudioFrame> frames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );
        for (int preferredToneHz : PREFERREDS_HZ) {
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                    LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                            "20260427_224524-pref-" + preferredToneHz + "-timeline",
                            frames,
                            preferredToneHz,
                            SEED_WPM,
                            SQL_PERCENT,
                            false,
                            CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                    );
            printTimeline(preferredToneHz, detailed);
        }
        assertTrue(true);
    }

    private static void printTimeline(
            int preferredToneHz,
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed
    ) {
        System.out.println("==== 20260427_224524 pref=" + preferredToneHz + " early timeline ====");
        int lastAcquisitionWinnerHz = Integer.MIN_VALUE;
        int lastFinalAdoptedHz = Integer.MIN_VALUE;
        String lastAcquisitionSource = null;
        String lastFinalSource = null;
        boolean lastToneActive = false;
        boolean lastLocked = false;

        for (LocalAudioDecodeTestSupport.FrameSignalTrace trace : detailed.frameSignalTraces()) {
            if (trace == null || trace.snapshot() == null) {
                continue;
            }
            if (trace.timestampMs() > PRINT_UNTIL_MS) {
                break;
            }
            CwSignalSnapshot snapshot = trace.snapshot();
            boolean changed = snapshot.acquisitionWinnerFrequencyHz() != lastAcquisitionWinnerHz
                    || snapshot.finalAdoptedFrequencyHz() != lastFinalAdoptedHz
                    || !safeEquals(snapshot.acquisitionWinnerSource(), lastAcquisitionSource)
                    || !safeEquals(snapshot.finalAdoptedSource(), lastFinalSource)
                    || snapshot.toneActive() != lastToneActive
                    || snapshot.targetToneLocked() != lastLocked;
            if (!changed) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "t=%dms toneActive=%s locked=%s target=%d acq=%s:%d conf=%.2f final=%s:%d conf=%.2f prefWin=%d lock=%s conf=%.2f wide=%d lock=%s conf=%.2f detail=%s / %s",
                    trace.timestampMs(),
                    snapshot.toneActive(),
                    snapshot.targetToneLocked(),
                    snapshot.targetToneFrequencyHz(),
                    snapshot.acquisitionWinnerSource(),
                    snapshot.acquisitionWinnerFrequencyHz(),
                    snapshot.acquisitionWinnerConfidence(),
                    snapshot.finalAdoptedSource(),
                    snapshot.finalAdoptedFrequencyHz(),
                    snapshot.finalAdoptedConfidence(),
                    snapshot.preferredWindowWinnerFrequencyHz(),
                    snapshot.preferredWindowWinnerLocked(),
                    snapshot.preferredWindowWinnerConfidence(),
                    snapshot.wideScanWinnerFrequencyHz(),
                    snapshot.wideScanWinnerLocked(),
                    snapshot.wideScanWinnerConfidence(),
                    sanitizeInline(snapshot.acquisitionDecisionDetail()),
                    sanitizeInline(snapshot.finalAdoptionDetail())
            ));
            System.out.println("  prefTop=" + snapshot.preferredWindowTopCandidatesSummary());
            System.out.println("  wideTop=" + snapshot.wideScanTopCandidatesSummary());

            lastAcquisitionWinnerHz = snapshot.acquisitionWinnerFrequencyHz();
            lastFinalAdoptedHz = snapshot.finalAdoptedFrequencyHz();
            lastAcquisitionSource = snapshot.acquisitionWinnerSource();
            lastFinalSource = snapshot.finalAdoptedSource();
            lastToneActive = snapshot.toneActive();
            lastLocked = snapshot.targetToneLocked();
        }
        System.out.println("decoded=" + detailed.probeResult().decodedText());
    }

    private static boolean safeEquals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static String sanitizeInline(String text) {
        if (text == null) {
            return "-";
        }
        String normalized = text.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.isEmpty() ? "-" : normalized;
    }

    private static Path requireWavFile(String sourceLabel) throws Exception {
        return LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> matchesSourceLabel(fileNameWithoutExtension(path), sourceLabel))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for " + sourceLabel));
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
}
