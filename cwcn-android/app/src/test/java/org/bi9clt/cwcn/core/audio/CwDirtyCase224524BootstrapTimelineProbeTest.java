package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertTrue;

public final class CwDirtyCase224524BootstrapTimelineProbeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;
    private static final long WINDOW_END_MS = 7000L;
    private static final int MAX_LINES = 40;

    @Test
    public void printBootstrapTimelineFor224524Pref700() throws Exception {
        Path wavFile = requireWavFile("20260427_224524");
        List<AudioFrame> frames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "20260427_224524-bootstrap-pref-700",
                        frames,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        System.out.println("final=" + sanitize(detailed.probeResult().decodedText()));
        System.out.println("-- boundary --");
        printBootstrapWindow(detailed.bootstrapBoundaryDecisionTraces());
        System.out.println("-- cadence --");
        printBootstrapWindow(detailed.bootstrapCadenceDecisionTraces());
        System.out.println("-- stable --");
        printStableWindow(detailed.stableDecisionTraces());
        System.out.println("-- trust --");
        printFirstTrust(detailed);
        assertTrue(true);
    }

    private static void printBootstrapWindow(
            List<LocalAudioDecodeTestSupport.BootstrapDecisionTrace> traces
    ) {
        int printed = 0;
        for (LocalAudioDecodeTestSupport.BootstrapDecisionTrace trace : traces) {
            if (trace == null || trace.timestampMs() > WINDOW_END_MS) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "@%d kind=%s class=%s dur=%d cand=%d decision=%s verified=%s trusted=%s rawWpm=%.1f rawDot=%d anchor=%s",
                    trace.timestampMs(),
                    safe(trace.eventKind()),
                    safe(trace.classification()),
                    trace.durationMs(),
                    trace.candidateDotEstimateMs(),
                    safe(trace.compatibleDecision()),
                    safe(trace.verifiedDecision()),
                    trace.trustedTimingEstablished(),
                    trace.rawWpm(),
                    trace.rawDotEstimateMs(),
                    safe(trace.anchorSummary())
            ));
            printed += 1;
            if (printed >= MAX_LINES) {
                break;
            }
        }
    }

    private static void printStableWindow(
            List<LocalAudioDecodeTestSupport.StableDecisionTrace> traces
    ) {
        int printed = 0;
        for (LocalAudioDecodeTestSupport.StableDecisionTrace trace : traces) {
            if (trace == null || trace.timestampMs() > WINDOW_END_MS) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "@%d char=%s seq=%s decision=%s verified=%s trusted=%s rawWpm=%.1f",
                    trace.timestampMs(),
                    safe(trace.emittedValue()),
                    safe(trace.sourceSequence()),
                    safe(trace.compatibleDecision()),
                    safe(trace.verifiedDecision()),
                    trace.trustedTimingEstablished(),
                    trace.rawWpm()
            ));
            printed += 1;
            if (printed >= MAX_LINES) {
                break;
            }
        }
    }

    private static void printFirstTrust(LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed) {
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : detailed.timingStateTraces()) {
            if (trace != null
                    && trace.debugSnapshot() != null
                    && trace.debugSnapshot().trustedDotEstimateMs() > 0.0d) {
                System.out.println(String.format(
                        Locale.US,
                        "@%d trustDot=%.1f reason=%s summary=%s",
                        trace.timestampMs(),
                        trace.debugSnapshot().trustedDotEstimateMs(),
                        safe(trace.debugSnapshot().lastTrustedUpdateReason()),
                        safe(trace.debugSummary())
                ));
                return;
            }
        }
        System.out.println("no-trust");
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

    private static String sanitize(String text) {
        if (text == null) {
            return "(empty)";
        }
        String normalized = text.replace('\u25A1', '?').replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.isEmpty() ? "(empty)" : normalized;
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }
}
