package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertTrue;

public final class CwDirtyCaseQualificationDiagnosisProbeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;

    @Test
    public void printDirtyCaseQualificationDiagnosis() throws Exception {
        printCase("20260427_222505");
        printCase("20260427_224524");
        assertTrue(true);
    }

    private static void printCase(String sourceLabel) throws Exception {
        Path wavFile = requireWavFile(sourceLabel);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        sourceLabel + "-qualification-diagnosis",
                        LocalAudioDecodeTestSupport.normalizeFramesToZero(
                                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
                        ),
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        DiagnosisSummary summary = analyze(detailed);
        System.out.println("==== " + sourceLabel + " qualification diagnosis ====");
        System.out.println(summary.render());
    }

    private static DiagnosisSummary analyze(LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed) {
        DiagnosisSummary summary = new DiagnosisSummary();
        for (LocalAudioDecodeTestSupport.FrameSignalTrace trace : detailed.frameSignalTraces()) {
            if (trace == null || trace.snapshot() == null) {
                continue;
            }
            CwSignalSnapshot snapshot = trace.snapshot();
            summary.totalFrames += 1;

            boolean widePresent = snapshot.wideScanWinnerFrequencyHz() > 0;
            boolean preferredPresent = snapshot.preferredWindowWinnerFrequencyHz() > 0;
            boolean wideFar = widePresent
                    && Math.abs(snapshot.wideScanWinnerFrequencyHz() - snapshot.preferredToneFrequencyHz()) >= 30;
            boolean wideChosen = "WIDE_SCAN".equals(snapshot.acquisitionWinnerSource());
            boolean fallbackHeld = "SEARCH_FALLBACK".equals(snapshot.finalAdoptedSource());
            boolean wideStrongerByScore = widePresent
                    && snapshot.wideScanWinnerSelectionScore() > (snapshot.preferredWindowWinnerSelectionScore() * 1.20d);
            boolean wideStrongerByTone = widePresent
                    && snapshot.wideScanWinnerToneRms() > (snapshot.preferredWindowWinnerToneRms() * 1.20d);
            boolean wideConfident = widePresent
                    && snapshot.wideScanWinnerConfidence() >= (snapshot.preferredWindowWinnerConfidence() + 0.10d);
            boolean candidateLooksStrong = wideFar && (wideStrongerByScore || wideStrongerByTone || wideConfident);

            if (widePresent) {
                summary.framesWithWideWinner += 1;
            }
            if (wideFar) {
                summary.framesWithFarWideWinner += 1;
            }
            if (wideChosen) {
                summary.framesWideChosen += 1;
            }
            if (fallbackHeld) {
                summary.framesSearchFallback += 1;
            }
            if (candidateLooksStrong) {
                summary.framesStrongFarWideEvidence += 1;
                if (!wideChosen) {
                    summary.framesStrongFarWideButNotChosen += 1;
                    collectExample(
                            summary.strongFarWideButNotChosenExamples,
                            renderExample(trace, "NOT_CHOSEN")
                    );
                } else if (fallbackHeld) {
                    summary.framesStrongFarWideChosenButFallbackHeld += 1;
                    collectExample(
                            summary.strongFarWideChosenButFallbackExamples,
                            renderExample(trace, "CHOSEN_FALLBACK")
                    );
                }
            }

            if (wideChosen && fallbackHeld) {
                summary.framesWideChosenButFallbackHeld += 1;
            }
            if (preferredPresent && !snapshot.preferredWindowWinnerLocked() && widePresent && !snapshot.wideScanWinnerLocked()) {
                summary.framesBothUnlocked += 1;
            }
            if (preferredPresent && snapshot.preferredWindowWinnerLocked() && widePresent && !snapshot.wideScanWinnerLocked()) {
                summary.framesPreferredLockedWideUnlocked += 1;
            }
            if (preferredPresent && !snapshot.preferredWindowWinnerLocked() && widePresent && snapshot.wideScanWinnerLocked()) {
                summary.framesPreferredUnlockedWideLocked += 1;
            }
        }
        return summary;
    }

    private static void collectExample(List<String> examples, String rendered) {
        if (rendered == null || rendered.isEmpty() || examples.size() >= 8) {
            return;
        }
        examples.add(rendered);
    }

    private static String renderExample(LocalAudioDecodeTestSupport.FrameSignalTrace trace, String tag) {
        CwSignalSnapshot snapshot = trace.snapshot();
        return String.format(
                Locale.US,
                "%s t=%dms pref=%dHz(score=%.1f rms=%.1f conf=%.2f lock=%s) wide=%dHz(score=%.1f rms=%.1f conf=%.2f lock=%s) acq=%s final=%s detail=%s / %s",
                tag,
                trace.timestampMs(),
                snapshot.preferredWindowWinnerFrequencyHz(),
                snapshot.preferredWindowWinnerSelectionScore(),
                snapshot.preferredWindowWinnerToneRms(),
                snapshot.preferredWindowWinnerConfidence(),
                snapshot.preferredWindowWinnerLocked(),
                snapshot.wideScanWinnerFrequencyHz(),
                snapshot.wideScanWinnerSelectionScore(),
                snapshot.wideScanWinnerToneRms(),
                snapshot.wideScanWinnerConfidence(),
                snapshot.wideScanWinnerLocked(),
                snapshot.acquisitionWinnerSource(),
                snapshot.finalAdoptedSource(),
                sanitizeInline(snapshot.acquisitionDecisionDetail()),
                sanitizeInline(snapshot.finalAdoptionDetail())
        );
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

    private static final class DiagnosisSummary {
        private int totalFrames;
        private int framesWithWideWinner;
        private int framesWithFarWideWinner;
        private int framesWideChosen;
        private int framesSearchFallback;
        private int framesStrongFarWideEvidence;
        private int framesStrongFarWideButNotChosen;
        private int framesStrongFarWideChosenButFallbackHeld;
        private int framesWideChosenButFallbackHeld;
        private int framesBothUnlocked;
        private int framesPreferredLockedWideUnlocked;
        private int framesPreferredUnlockedWideLocked;
        private final List<String> strongFarWideButNotChosenExamples = new ArrayList<>();
        private final List<String> strongFarWideChosenButFallbackExamples = new ArrayList<>();

        private String render() {
            StringBuilder builder = new StringBuilder();
            builder.append(String.format(
                    Locale.US,
                    "frames=%d wide=%d farWide=%d wideChosen=%d fallback=%d strongFarWide=%d notChosen=%d chosenButFallback=%d bothUnlocked=%d prefLockWideUnlock=%d prefUnlockWideLock=%d%n",
                    totalFrames,
                    framesWithWideWinner,
                    framesWithFarWideWinner,
                    framesWideChosen,
                    framesSearchFallback,
                    framesStrongFarWideEvidence,
                    framesStrongFarWideButNotChosen,
                    framesStrongFarWideChosenButFallbackHeld,
                    framesBothUnlocked,
                    framesPreferredLockedWideUnlocked,
                    framesPreferredUnlockedWideLocked
            ));
            builder.append("strong-far-wide not chosen examples:\n");
            if (strongFarWideButNotChosenExamples.isEmpty()) {
                builder.append("(none)\n");
            } else {
                for (String example : strongFarWideButNotChosenExamples) {
                    builder.append(example).append('\n');
                }
            }
            builder.append("strong-far-wide chosen but fallback held examples:\n");
            if (strongFarWideChosenButFallbackExamples.isEmpty()) {
                builder.append("(none)");
            } else {
                for (String example : strongFarWideChosenButFallbackExamples) {
                    builder.append(example).append('\n');
                }
            }
            return builder.toString().trim();
        }
    }
}
