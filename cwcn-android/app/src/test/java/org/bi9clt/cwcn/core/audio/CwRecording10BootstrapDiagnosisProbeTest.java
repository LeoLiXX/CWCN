package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertTrue;

public final class CwRecording10BootstrapDiagnosisProbeTest {
    private static final int DEFAULT_SQL_PERCENT = 55;
    private static final int DEFAULT_SEED_WPM = 15;
    private static final int DEFAULT_PREFERRED_TONE_HZ = 700;
    private static final int DISAGREEMENT_THRESHOLD_HZ = 45;
    private static final int MODE_PRINT_STEP_MS = 160;
    private static final long POST_TRUST_TAIL_MS = 640L;
    private static final long MIN_WINDOW_SPAN_MS = 1800L;
    private static final String EXPECTED_LONG_QSO =
            "CQ CQ DE BG1XXX K BG1XXX DE JA1ABC K JA1ABC DE BG1XXX TNX FER CALL UR RST 599 QTH BEIJING NAME LEO HW? K BG1XXX DE JA1ABC FB OM UR RST 579 QTH TOKYO NAME KEN BK TNX QSO 73 DE BG1XXX SK";

    @Test
    public void printRecording10BootstrapDiagnosis() throws Exception {
        Path wavFile = requireWav("(10).wav");
        List<AudioFrame> frames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult hybrid =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "recording10-hybrid-700",
                        frames,
                        DEFAULT_PREFERRED_TONE_HZ,
                        DEFAULT_SEED_WPM,
                        DEFAULT_SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult auto =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeWithTurnCarry(
                        "recording10-auto-700",
                        frames,
                        DEFAULT_PREFERRED_TONE_HZ,
                        DEFAULT_SEED_WPM,
                        DEFAULT_SQL_PERCENT,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        CwSignalProcessor.RxToneMode.AUTO_TRACK,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult fixedUntilTrust =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeFixedUntilTrustThenAuto(
                        "recording10-fixed-until-trust-700",
                        frames,
                        DEFAULT_PREFERRED_TONE_HZ,
                        DEFAULT_SEED_WPM,
                        DEFAULT_SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult fixed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeWithTurnCarry(
                        "recording10-fixed-700",
                        frames,
                        DEFAULT_PREFERRED_TONE_HZ,
                        DEFAULT_SEED_WPM,
                        DEFAULT_SQL_PERCENT,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        CwSignalProcessor.RxToneMode.FIXED_TONE,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        System.out.println("==== recording10 bootstrap diagnosis ====");
        printCaseSummary("hybrid@700", hybrid);
        printCaseSummary("auto@700", auto);
        printCaseSummary("fixedUntilTrust@700", fixedUntilTrust);
        printCaseSummary("fixed@700", fixed);

        System.out.println("==== hybrid@700 mode window ====");
        printModeWindow("hybrid@700", hybrid);
        System.out.println("==== fixedUntilTrust@700 mode window ====");
        printModeWindow("fixedUntilTrust@700", fixedUntilTrust);
        System.out.println("==== auto@700 mode window ====");
        printModeWindow("auto@700", auto);

        assertTrue(true);
    }

    private static void printCaseSummary(
            String label,
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed
    ) {
        String finalText = sanitize(detailed.probeResult().decodedText());
        double recall = charRecall(EXPECTED_LONG_QSO, finalText);
        long trustMs = firstTrustTimestampMs(detailed.timingStateTraces());
        CwSignalSnapshot snapshot = detailed.probeResult().signalSnapshot();
        LocalAudioDecodeTestSupport.FrontEndDisagreementProfile disagreementProfile =
                LocalAudioDecodeTestSupport.evaluateFrontEndDisagreementProfile(
                        detailed,
                        DISAGREEMENT_THRESHOLD_HZ
                );
        int fixedFrames = 0;
        int autoFrames = 0;
        int fallbackEligibleFrames = 0;
        int fallbackLatchedFrames = 0;
        int usefulProgressFrames = 0;
        int modeSwitches = 0;
        CwSignalProcessor.RxToneMode previousMode = null;
        for (LocalAudioDecodeTestSupport.RxToneModeDecisionTrace trace : detailed.rxToneModeDecisionTraces()) {
            if (trace == null || trace.resolvedMode() == null) {
                continue;
            }
            if (trace.resolvedMode() == CwSignalProcessor.RxToneMode.FIXED_TONE) {
                fixedFrames += 1;
            } else {
                autoFrames += 1;
            }
            if (trace.eligibleForPreTrustFallback()) {
                fallbackEligibleFrames += 1;
            }
            if (trace.fallbackLatched()) {
                fallbackLatchedFrames += 1;
            }
            if (trace.usefulFixedProgress()) {
                usefulProgressFrames += 1;
            }
            if (previousMode != null && previousMode != trace.resolvedMode()) {
                modeSwitches += 1;
            }
            previousMode = trace.resolvedMode();
        }

        System.out.println(String.format(
                Locale.US,
                "%s recall=%.4f chars=%d trust=%d tone=%d/%d/%d eff=%d hyp=%d(%.2f) rep=%d lock=%s cons=%d lockR=%.2f modeFrames[f=%d a=%d] eligible=%d latched=%d progress=%d switches=%d",
                label,
                recall,
                detailed.probeResult().decoderSnapshot().totalCharacters(),
                trustMs,
                snapshot.targetToneFrequencyHz(),
                snapshot.effectiveAcquisitionWinnerFrequencyHz(),
                snapshot.effectiveFinalAdoptedFrequencyHz(),
                snapshot.effectiveTrackedToneFrequencyHz(),
                snapshot.toneHypothesisFrequencyHz(),
                snapshot.toneHypothesisConfidence(),
                snapshot.representativeLockedToneFrequencyHz(),
                yesNo(snapshot.targetToneLocked()),
                snapshot.consecutiveLockedFrames(),
                snapshot.recentLockedFrameRatio(),
                fixedFrames,
                autoFrames,
                fallbackEligibleFrames,
                fallbackLatchedFrames,
                usefulProgressFrames,
                modeSwitches
        ));
        System.out.println("final=" + finalText);
        System.out.println("src aq=" + compact(snapshot.acquisitionWinnerSource())
                + " final=" + compact(snapshot.finalAdoptedSource())
                + " aqDetail=" + compact(snapshot.acquisitionDecisionDetail())
                + " finalDetail=" + compact(snapshot.finalAdoptionDetail()));
        System.out.println("profile=" + disagreementProfile.renderSummary());
    }

    private static void printModeWindow(
            String label,
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed
    ) {
        long turnStartMs = firstTurnStartMs(detailed.turnTransitionTraces());
        long trustMs = firstTrustTimestampMs(detailed.timingStateTraces());
        long windowStartMs = turnStartMs >= 0L
                ? turnStartMs
                : detailed.frames().isEmpty() ? 0L : detailed.frames().get(0).capturedAtMs();
        long windowEndMs = resolveWindowEndMs(windowStartMs, trustMs, detailed.flushTimestampMs());

        System.out.println(String.format(
                Locale.US,
                "%s window=%d..%d trust=%d",
                label,
                windowStartMs,
                windowEndMs,
                trustMs
        ));

        long nextPrintAtMs = Long.MIN_VALUE;
        String lastStateKey = null;
        for (LocalAudioDecodeTestSupport.FrameSignalTrace trace : detailed.frameSignalTraces()) {
            if (trace == null || trace.snapshot() == null) {
                continue;
            }
            long timestampMs = trace.timestampMs();
            if (timestampMs < windowStartMs) {
                continue;
            }
            if (timestampMs > windowEndMs) {
                break;
            }

            CwSignalSnapshot snapshot = trace.snapshot();
            LocalAudioDecodeTestSupport.RxToneModeDecisionTrace modeTrace =
                    latestModeTraceAtOrBefore(detailed.rxToneModeDecisionTraces(), timestampMs);
            String stateKey = buildStateKey(snapshot, trace, modeTrace);
            boolean shouldPrint = lastStateKey == null
                    || !lastStateKey.equals(stateKey)
                    || timestampMs >= nextPrintAtMs;
            if (!shouldPrint) {
                continue;
            }

            System.out.println(String.format(
                    Locale.US,
                    "@%d mode=%s latched=%s trust=%s eligible=%s progress=%s on=%s lock=%s cons=%d lockR=%.2f det=%.1f tgt=%d eff=%d aq=%d final=%d hyp=%d(%.2f) rep=%d dom=%.2f iso=%.2f toneOn=%s release=%s",
                    timestampMs,
                    modeTrace == null ? "?" : modeTrace.resolvedMode(),
                    yesNo(modeTrace != null && modeTrace.fallbackLatched()),
                    yesNo(modeTrace != null && modeTrace.trustedTimingEstablished()),
                    yesNo(modeTrace != null && modeTrace.eligibleForPreTrustFallback()),
                    yesNo(modeTrace != null && modeTrace.usefulFixedProgress()),
                    yesNo(snapshot.toneActive()),
                    yesNo(snapshot.targetToneLocked()),
                    snapshot.consecutiveLockedFrames(),
                    snapshot.recentLockedFrameRatio(),
                    trace.detectionLevel(),
                    snapshot.targetToneFrequencyHz(),
                    snapshot.effectiveTrackedToneFrequencyHz(),
                    snapshot.effectiveAcquisitionWinnerFrequencyHz(),
                    snapshot.effectiveFinalAdoptedFrequencyHz(),
                    snapshot.toneHypothesisFrequencyHz(),
                    snapshot.toneHypothesisConfidence(),
                    snapshot.representativeLockedToneFrequencyHz(),
                    snapshot.toneDominanceRatio(),
                    snapshot.narrowbandIsolationRatio(),
                    compact(trace.toneOnDecision()),
                    compact(trace.releaseTailHoldDecision())
            ));
            System.out.println("src aq=" + compact(snapshot.acquisitionWinnerSource())
                    + " final=" + compact(snapshot.finalAdoptedSource())
                    + " aqDetail=" + compact(snapshot.acquisitionDecisionDetail())
                    + " finalDetail=" + compact(snapshot.finalAdoptionDetail()));
            System.out.println("cand pref=" + compact(snapshot.preferredWindowTopCandidatesSummary()));
            System.out.println("cand wide=" + compact(snapshot.wideScanTopCandidatesSummary()));

            lastStateKey = stateKey;
            nextPrintAtMs = timestampMs + MODE_PRINT_STEP_MS;
        }
    }

    private static String buildStateKey(
            CwSignalSnapshot snapshot,
            LocalAudioDecodeTestSupport.FrameSignalTrace trace,
            LocalAudioDecodeTestSupport.RxToneModeDecisionTrace modeTrace
    ) {
        return (modeTrace == null ? "?" : modeTrace.resolvedMode().name())
                + "|" + (modeTrace != null && modeTrace.fallbackLatched())
                + "|" + (modeTrace != null && modeTrace.trustedTimingEstablished())
                + "|" + (modeTrace != null && modeTrace.eligibleForPreTrustFallback())
                + "|" + (modeTrace != null && modeTrace.usefulFixedProgress())
                + "|" + snapshot.targetToneLocked()
                + "|" + snapshot.toneActive()
                + "|" + snapshot.targetToneFrequencyHz()
                + "|" + snapshot.effectiveTrackedToneFrequencyHz()
                + "|" + snapshot.effectiveAcquisitionWinnerFrequencyHz()
                + "|" + snapshot.effectiveFinalAdoptedFrequencyHz()
                + "|" + snapshot.toneHypothesisFrequencyHz()
                + "|" + compact(snapshot.acquisitionWinnerSource())
                + "|" + compact(snapshot.finalAdoptedSource())
                + "|" + compact(snapshot.acquisitionDecisionDetail())
                + "|" + compact(snapshot.finalAdoptionDetail())
                + "|" + compact(trace.toneOnDecision())
                + "|" + compact(trace.releaseTailHoldDecision());
    }

    private static LocalAudioDecodeTestSupport.RxToneModeDecisionTrace latestModeTraceAtOrBefore(
            List<LocalAudioDecodeTestSupport.RxToneModeDecisionTrace> traces,
            long timestampMs
    ) {
        LocalAudioDecodeTestSupport.RxToneModeDecisionTrace latest = null;
        for (LocalAudioDecodeTestSupport.RxToneModeDecisionTrace trace : traces) {
            if (trace == null || trace.timestampMs() > timestampMs) {
                break;
            }
            latest = trace;
        }
        return latest;
    }

    private static long firstTurnStartMs(List<LocalAudioDecodeTestSupport.TurnTransitionTrace> traces) {
        for (LocalAudioDecodeTestSupport.TurnTransitionTrace trace : traces) {
            if (trace != null
                    && trace.kind() == LocalAudioDecodeTestSupport.TurnTransitionTrace.Kind.START) {
                return trace.timestampMs();
            }
        }
        return -1L;
    }

    private static long firstTrustTimestampMs(List<LocalAudioDecodeTestSupport.TimingStateTrace> traces) {
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : traces) {
            if (trace != null
                    && trace.debugSnapshot() != null
                    && trace.debugSnapshot().trustedDotEstimateMs() > 0.0d) {
                return trace.timestampMs();
            }
        }
        return -1L;
    }

    private static long resolveWindowEndMs(long windowStartMs, long trustMs, long flushTimestampMs) {
        long trustTailMs = trustMs < 0L
                ? windowStartMs + MIN_WINDOW_SPAN_MS
                : Math.max(windowStartMs + MIN_WINDOW_SPAN_MS, trustMs + POST_TRUST_TAIL_MS);
        return Math.min(flushTimestampMs, trustTailMs);
    }

    private static Path requireWav(String suffix) throws Exception {
        return LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith(suffix))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for " + suffix));
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "(empty)";
        }
        String normalized = text.replace('\u25A1', '?').replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.isEmpty() ? "(empty)" : normalized;
    }

    private static String compact(String text) {
        if (text == null) {
            return "NONE";
        }
        String normalized = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.isEmpty()) {
            return "NONE";
        }
        if (normalized.length() <= 160) {
            return normalized;
        }
        return normalized.substring(0, 157) + "...";
    }

    private static String yesNo(boolean value) {
        return value ? "Y" : "N";
    }

    private static String canonicalize(String text) {
        if (text == null) {
            return "";
        }
        String upper = text.toUpperCase(Locale.US).replace('\u25A1', '?');
        StringBuilder builder = new StringBuilder(upper.length());
        for (int index = 0; index < upper.length(); index++) {
            char ch = upper.charAt(index);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '?') {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static double charRecall(String expectedText, String actualText) {
        String expected = canonicalize(expectedText);
        String actual = canonicalize(actualText);
        if (expected.isEmpty()) {
            return actual.isEmpty() ? 1.0d : 0.0d;
        }
        int lcs = longestCommonSubsequenceLength(expected, actual);
        return lcs / (double) expected.length();
    }

    private static int longestCommonSubsequenceLength(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
            char leftChar = left.charAt(leftIndex - 1);
            for (int rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                if (leftChar == right.charAt(rightIndex - 1)) {
                    current[rightIndex] = previous[rightIndex - 1] + 1;
                } else {
                    current[rightIndex] = Math.max(previous[rightIndex], current[rightIndex - 1]);
                }
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }
}
