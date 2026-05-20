package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.rx.RxTurnController;
import org.bi9clt.cwcn.core.rx.RxToneModeBootstrapDecider;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.junit.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertTrue;

public final class CwSingleCqBadGroupSweepProbeTest {
    private static final int DEFAULT_PREFERRED_TONE_HZ = 700;
    private static final int DEFAULT_SEED_WPM = 15;
    private static final int DEFAULT_SQL_PERCENT = 55;
    private static final int[] SQL_LEVELS = new int[]{20, 30, 40, 50, 55, 60, 65, 70, 75, 80};
    private static final int[] SEED_WPM_LEVELS = new int[]{8, 10, 12, 15, 18, 20, 22, 24};
    private static final int[] PREFERRED_TONE_LEVELS = new int[]{600, 650, 700, 750, 800};
    private static final long PRE_TRUST_FALLBACK_DELAY_MS = 48L;
    private static final double PROGRESS_LOCKED_RATIO_MIN = 0.30d;
    private static final int PROGRESS_CONSECUTIVE_LOCKED_FRAMES_MIN = 4;
    private static final long OPENING_WINDOW_MS = 2600L;
    private static final long PRINT_STEP_MS = 96L;
    private static final Map<String, CaseExpectation> CASES = buildCases();

    @Test
    public void printSingleCqBadGroupSqlSweep() throws Exception {
        System.out.println("==== single-cq bad-group sql sweep ====");
        for (Map.Entry<String, CaseExpectation> entry : CASES.entrySet()) {
            Path wavFile = requireWav(entry.getKey());
            System.out.println("-- " + entry.getKey() + " --");
            for (int sqlPercent : SQL_LEVELS) {
                printSummary(
                        "sql=" + sqlPercent,
                        entry.getValue(),
                        LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                                wavFile,
                                DEFAULT_PREFERRED_TONE_HZ,
                                DEFAULT_SEED_WPM,
                                sqlPercent,
                                false,
                                CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                        )
                );
            }
        }

        assertTrue(true);
    }

    @Test
    public void printSingleCqBadGroupSeedSweep() throws Exception {
        System.out.println("==== single-cq bad-group seed sweep ====");
        for (Map.Entry<String, CaseExpectation> entry : CASES.entrySet()) {
            Path wavFile = requireWav(entry.getKey());
            System.out.println("-- " + entry.getKey() + " --");
            for (int seedWpm : SEED_WPM_LEVELS) {
                printSummary(
                        "seed=" + seedWpm,
                        entry.getValue(),
                        LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                                wavFile,
                                DEFAULT_PREFERRED_TONE_HZ,
                                seedWpm,
                                DEFAULT_SQL_PERCENT,
                                false,
                                CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                        )
                );
            }
        }

        assertTrue(true);
    }

    @Test
    public void printSingleCqBadGroupPreferredToneSweep() throws Exception {
        System.out.println("==== single-cq bad-group preferred-tone sweep ====");
        for (Map.Entry<String, CaseExpectation> entry : CASES.entrySet()) {
            Path wavFile = requireWav(entry.getKey());
            System.out.println("-- " + entry.getKey() + " --");
            for (int preferredToneHz : PREFERRED_TONE_LEVELS) {
                printSummary(
                        "tone=" + preferredToneHz,
                        entry.getValue(),
                        LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                                wavFile,
                                preferredToneHz,
                                DEFAULT_SEED_WPM,
                                DEFAULT_SQL_PERCENT,
                                false,
                                CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                        )
                );
            }
        }

        assertTrue(true);
    }

    @Test
    public void printSingleCqBadGroupModeSweep() throws Exception {
        System.out.println("==== single-cq bad-group mode sweep ====");
        for (Map.Entry<String, CaseExpectation> entry : CASES.entrySet()) {
            Path wavFile = requireWav(entry.getKey());
            List<AudioFrame> frames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                    LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
            );
            System.out.println("-- " + entry.getKey() + " --");

            printSummary(
                    "mode=HYBRID_BOOTSTRAP",
                    entry.getValue(),
                    LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                            entry.getKey() + "-hybrid",
                            frames,
                            DEFAULT_PREFERRED_TONE_HZ,
                            DEFAULT_SEED_WPM,
                            DEFAULT_SQL_PERCENT,
                            false,
                            CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                    )
            );
            printSummary(
                    "mode=STATIC_FIXED_TONE",
                    entry.getValue(),
                    LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeWithTurnCarry(
                            entry.getKey() + "-fixed",
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
                    )
            );
            printSummary(
                    "mode=STATIC_AUTO_TRACK",
                    entry.getValue(),
                    LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeWithTurnCarry(
                            entry.getKey() + "-auto",
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
                    )
            );
        }

        assertTrue(true);
    }

    @Test
    public void printSingleCqBadGroupBootstrapDecisionWindows() throws Exception {
        System.out.println("==== single-cq bad-group bootstrap decision windows ====");
        for (Map.Entry<String, CaseExpectation> entry : CASES.entrySet()) {
            Path wavFile = requireWav(entry.getKey());
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                    LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                            wavFile,
                            DEFAULT_PREFERRED_TONE_HZ,
                            DEFAULT_SEED_WPM,
                            DEFAULT_SQL_PERCENT,
                            false,
                            CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                    );

            long turnStartMs = firstTurnStartMs(detailed.turnTransitionTraces());
            long trustMs = firstTrustTimestampMs(detailed.timingStateTraces());
            long windowEndMs = resolveWindowEndMs(turnStartMs, trustMs, detailed.flushTimestampMs());

            System.out.println("-- " + entry.getKey() + " --");
            System.out.println(String.format(
                    Locale.US,
                    "final=%s turnStart=%d trust=%d trustOffset=%d windowEnd=%d",
                    sanitize(detailed.probeResult().decodedText()),
                    turnStartMs,
                    trustMs,
                    trustMs < 0L || turnStartMs < 0L ? -1L : Math.max(0L, trustMs - turnStartMs),
                    windowEndMs
            ));

            long nextPrintAtMs = Long.MIN_VALUE;
            Boolean lastFallback = null;
            for (LocalAudioDecodeTestSupport.FrameSignalTrace trace : detailed.frameSignalTraces()) {
                if (trace == null || trace.snapshot() == null) {
                    continue;
                }
                long timestampMs = trace.timestampMs();
                if (turnStartMs >= 0L && timestampMs < turnStartMs) {
                    continue;
                }
                if (timestampMs > windowEndMs) {
                    break;
                }
                CwSignalSnapshot snapshot = trace.snapshot();
                boolean usefulProgress = showsUsefulFixedToneBootstrapProgress(snapshot);
                boolean eligibleForFallback = turnStartMs >= 0L
                        && timestampMs >= (turnStartMs + PRE_TRUST_FALLBACK_DELAY_MS)
                        && !usefulProgress;

                boolean shouldPrint = lastFallback == null
                        || eligibleForFallback != lastFallback
                        || timestampMs >= nextPrintAtMs;
                if (!shouldPrint) {
                    continue;
                }

                int acquisitionHz = snapshot.effectiveAcquisitionWinnerFrequencyHz();
                int adoptedHz = snapshot.effectiveFinalAdoptedFrequencyHz();
                int hypothesisHz = snapshot.toneHypothesisFrequencyHz();
                System.out.println(String.format(
                        Locale.US,
                        "@%d fallback=%s progress=%s lock=%s cons=%d lockR=%.2f"
                                + " tgt=%d eff=%d aq=%d(%+d) final=%d(%+d) hyp=%d(%+d) conf=%.2f"
                                + " dom=%.2f iso=%.2f on=%d off=%d",
                        timestampMs,
                        yesNo(eligibleForFallback),
                        usefulProgress ? progressReason(snapshot) : "none",
                        yesNo(snapshot.targetToneLocked()),
                        snapshot.consecutiveLockedFrames(),
                        snapshot.recentLockedFrameRatio(),
                        snapshot.targetToneFrequencyHz(),
                        snapshot.effectiveTrackedToneFrequencyHz(),
                        acquisitionHz,
                        offsetFromPreferred(acquisitionHz),
                        adoptedHz,
                        offsetFromPreferred(adoptedHz),
                        hypothesisHz,
                        offsetFromPreferred(hypothesisHz),
                        snapshot.toneHypothesisConfidence(),
                        snapshot.toneDominanceRatio(),
                        snapshot.narrowbandIsolationRatio(),
                        snapshot.totalToneOnEvents(),
                        snapshot.totalToneOffEvents()
                ));

                lastFallback = eligibleForFallback;
                nextPrintAtMs = timestampMs + PRINT_STEP_MS;
            }
        }

        assertTrue(true);
    }

    @Test
    public void printKeyAnchorHybridVsFixedComparison() throws Exception {
        LinkedHashMap<String, String> anchorCases = new LinkedHashMap<>();
        anchorCases.put(
                "capture.wav",
                "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 700 24WPM PSE K."
                        + "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 700 24WPM PSE K."
                        + "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 700 24WPM PSE K."
        );
        anchorCases.put(
                "recording(2)",
                "CQ DX CQ DX DE JV3VV JV3VV PAGE K. CQ DX CQ DX DE JV3VV JV3VV PAGE K."
        );
        anchorCases.put(
                "recording(16)",
                "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 700 24WPM PSE K."
        );

        System.out.println("==== key-anchor hybrid-vs-fixed comparison ====");
        for (Map.Entry<String, String> entry : anchorCases.entrySet()) {
            Path wavFile = "capture.wav".equals(entry.getKey())
                    ? LocalAudioDecodeTestSupport.findTraceDir().resolve("capture.wav")
                    : requireWav(entry.getKey());
            List<AudioFrame> frames = "capture.wav".equals(entry.getKey())
                    ? LocalAudioDecodeTestSupport.normalizeFramesToZero(
                    LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
            )
                    : LocalAudioDecodeTestSupport.normalizeFramesToZero(
                    LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
            );
            System.out.println("-- " + entry.getKey() + " --");
            printComparisonCase(
                    "HYBRID_BOOTSTRAP",
                    entry.getValue(),
                    LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                            entry.getKey() + "-hybrid",
                            frames,
                            DEFAULT_PREFERRED_TONE_HZ,
                            DEFAULT_SEED_WPM,
                            DEFAULT_SQL_PERCENT,
                            false,
                            CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                    )
            );
            printComparisonCase(
                    "STATIC_FIXED_TONE",
                    entry.getValue(),
                    LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeWithTurnCarry(
                            entry.getKey() + "-fixed",
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
                    )
            );
        }

        assertTrue(true);
    }

    @Test
    public void printSingleCqBadGroupPostTrustRetunePressure() throws Exception {
        System.out.println("==== single-cq bad-group post-trust retune pressure ====");
        for (Map.Entry<String, CaseExpectation> entry : CASES.entrySet()) {
            Path wavFile = requireWav(entry.getKey());
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                    LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                            wavFile,
                            DEFAULT_PREFERRED_TONE_HZ,
                            DEFAULT_SEED_WPM,
                            DEFAULT_SQL_PERCENT,
                            false,
                            CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                    );
            long trustMs = firstTrustTimestampMs(detailed.timingStateTraces());
            long windowEndMs = trustMs < 0L
                    ? detailed.flushTimestampMs()
                    : Math.min(detailed.flushTimestampMs(), trustMs + 2200L);

            System.out.println("-- " + entry.getKey() + " --");
            System.out.println(String.format(
                    Locale.US,
                    "final=%s trust=%d windowEnd=%d",
                    sanitize(detailed.probeResult().decodedText()),
                    trustMs,
                    windowEndMs
            ));

            RxTurnController syntheticTurnController = new RxTurnController();
            syntheticTurnController.reset();
            Boolean lastExplicitRetune = null;
            long nextPrintAtMs = Long.MIN_VALUE;
            for (LocalAudioDecodeTestSupport.FrameSignalTrace trace : detailed.frameSignalTraces()) {
                if (trace == null || trace.snapshot() == null) {
                    continue;
                }
                long timestampMs = trace.timestampMs();
                if (trustMs >= 0L && timestampMs < trustMs) {
                    continue;
                }
                if (timestampMs > windowEndMs) {
                    break;
                }
                CwSignalSnapshot snapshot = trace.snapshot();
                boolean explicitRetune = snapshot.pendingRetuneCandidateStableScans() > 0
                        || snapshot.lockedRetuneGuardHolding()
                        || snapshot.lockedRetuneGuardObservedScans() > 0;
                boolean shouldPrint = lastExplicitRetune == null
                        || explicitRetune != lastExplicitRetune
                        || timestampMs >= nextPrintAtMs;
                if (!shouldPrint) {
                    continue;
                }
                CwSignalProcessor.RxToneMode resolvedMode = RxToneModeBootstrapDecider.resolveHybridBootstrapMode(
                        true,
                        syntheticTurnController,
                        snapshot,
                        timestampMs
                );
                System.out.println(String.format(
                        Locale.US,
                        "@%d mode=%s retune=%s lock=%s cons=%d lockR=%.2f pending=%d@%d guard=%s obs=%d req=%d drift=%d tgt=%d eff=%d aq=%d final=%d hyp=%d conf=%.2f dom=%.2f iso=%.2f",
                        timestampMs,
                        resolvedMode,
                        yesNo(explicitRetune),
                        yesNo(snapshot.targetToneLocked()),
                        snapshot.consecutiveLockedFrames(),
                        snapshot.recentLockedFrameRatio(),
                        snapshot.pendingRetuneCandidateStableScans(),
                        snapshot.pendingRetuneCandidateFrequencyHz(),
                        yesNo(snapshot.lockedRetuneGuardHolding()),
                        snapshot.lockedRetuneGuardObservedScans(),
                        snapshot.lockedRetuneGuardRequiredScans(),
                        snapshot.lockedRetuneGuardDriftHz(),
                        snapshot.targetToneFrequencyHz(),
                        snapshot.effectiveTrackedToneFrequencyHz(),
                        snapshot.effectiveAcquisitionWinnerFrequencyHz(),
                        snapshot.effectiveFinalAdoptedFrequencyHz(),
                        snapshot.toneHypothesisFrequencyHz(),
                        snapshot.toneHypothesisConfidence(),
                        snapshot.toneDominanceRatio(),
                        snapshot.narrowbandIsolationRatio()
                ));
                lastExplicitRetune = explicitRetune;
                nextPrintAtMs = timestampMs + PRINT_STEP_MS;
            }
        }

        assertTrue(true);
    }

    @Test
    public void printRiskBundleHybridVsFixedComparison() throws Exception {
        LinkedHashMap<String, String> riskCases = new LinkedHashMap<>();
        riskCases.put(
                "recording(3)",
                "BI9CMS BI9CMS BI9CMS DE BI9CLT BI8DLT BI9CLT UR 599 5NN BK."
        );
        riskCases.put(
                "recording(7)",
                "QRZ? DE BI3TUK KN."
        );
        riskCases.put(
                "recording(8)",
                "CQ CQ DE BG1XXX K BG1XXX DE JA1ABC K JA1ABC DE BG1XXX TNX FER CALL UR RST 599 QTH BEIJING NAME LEO HW? K BG1XXX DE JA1ABC FB OM UR RST 579 QTH TOKYO NAME KEN BK TNX QSO 73 DE BG1XXX SK"
        );
        riskCases.put(
                "recording(10)",
                "CQ CQ DE BG1XXX K BG1XXX DE JA1ABC K JA1ABC DE BG1XXX TNX FER CALL UR RST 599 QTH BEIJING NAME LEO HW? K BG1XXX DE JA1ABC FB OM UR RST 579 QTH TOKYO NAME KEN BK TNX QSO 73 DE BG1XXX SK"
        );
        riskCases.put(
                "recording(11)",
                "CQ CQ DE BG1XXX K BG1XXX DE JA1ABC K JA1ABC DE BG1XXX TNX FER CALL UR RST 599 QTH BEIJING NAME LEO HW? K BG1XXX DE JA1ABC FB OM UR RST 579 QTH TOKYO NAME KEN BK TNX QSO 73 DE BG1XXX SK"
        );

        System.out.println("==== risk-bundle hybrid-vs-fixed comparison ====");
        for (Map.Entry<String, String> entry : riskCases.entrySet()) {
            Path wavFile = requireWav(entry.getKey());
            List<AudioFrame> frames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                    LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
            );
            System.out.println("-- " + entry.getKey() + " --");
            printComparisonCase(
                    "HYBRID_BOOTSTRAP",
                    entry.getValue(),
                    LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                            entry.getKey() + "-hybrid",
                            frames,
                            DEFAULT_PREFERRED_TONE_HZ,
                            DEFAULT_SEED_WPM,
                            DEFAULT_SQL_PERCENT,
                            false,
                            CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                    )
            );
            printComparisonCase(
                    "STATIC_FIXED_TONE",
                    entry.getValue(),
                    LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeWithTurnCarry(
                            entry.getKey() + "-fixed",
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
                    )
            );
        }

        assertTrue(true);
    }

    private static void printSummary(
            String label,
            CaseExpectation expectation,
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed
    ) {
        String finalText = sanitize(detailed.probeResult().decodedText());
        String stableText = sanitize(textAtOrBefore(
                detailed.stableAcceptedDecodeEvents(),
                detailed.flushTimestampMs()
        ));
        String rawText = sanitize(textAtOrBefore(
                detailed.rawDecodeEvents(),
                detailed.flushTimestampMs()
        ));
        double recall = charRecall(expectation.expectedText, finalText);
        long firstTrustOffsetMs = firstTrustedOffsetMs(detailed);
        double firstTrustDotMs = firstTrustedDotMs(detailed);

        System.out.println(String.format(
                Locale.US,
                "%s recall=%.4f chars=%d trust=%s dot=%s tone=%d/%d/%d wpm=%d cq=%d de=%d bi9=%d pse=%d rejects=%s",
                label,
                recall,
                detailed.probeResult().decoderSnapshot().totalCharacters(),
                firstTrustOffsetMs < 0L ? "-" : firstTrustOffsetMs + "ms",
                firstTrustDotMs <= 0.0d ? "-" : String.format(Locale.US, "%.1f", firstTrustDotMs),
                detailed.probeResult().signalSnapshot().targetToneFrequencyHz(),
                detailed.probeResult().signalSnapshot().effectiveAcquisitionWinnerFrequencyHz(),
                detailed.probeResult().signalSnapshot().effectiveFinalAdoptedFrequencyHz(),
                detailed.probeResult().timingSnapshot().estimatedWpm(),
                countOccurrences(canonicalize(finalText), "CQ"),
                countOccurrences(canonicalize(finalText), "DE"),
                countOccurrences(canonicalize(finalText), "BI9"),
                countOccurrences(canonicalize(finalText), "PSE"),
                detailed.stableRejectCounts()
        ));
        System.out.println("final=" + finalText);
        System.out.println("stable=" + stableText);
        System.out.println("raw=" + rawText);
    }

    private static void printComparisonCase(
            String label,
            String expectedText,
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed
    ) {
        String finalText = sanitize(detailed.probeResult().decodedText());
        double recall = charRecall(expectedText, finalText);
        System.out.println(String.format(
                Locale.US,
                "%s recall=%.4f chars=%d trust=%s dot=%s tone=%d/%d/%d wpm=%d rejects=%s",
                label,
                recall,
                detailed.probeResult().decoderSnapshot().totalCharacters(),
                firstTrustedOffsetMs(detailed) < 0L ? "-" : firstTrustedOffsetMs(detailed) + "ms",
                firstTrustedDotMs(detailed) <= 0.0d
                        ? "-"
                        : String.format(Locale.US, "%.1f", firstTrustedDotMs(detailed)),
                detailed.probeResult().signalSnapshot().targetToneFrequencyHz(),
                detailed.probeResult().signalSnapshot().effectiveAcquisitionWinnerFrequencyHz(),
                detailed.probeResult().signalSnapshot().effectiveFinalAdoptedFrequencyHz(),
                detailed.probeResult().timingSnapshot().estimatedWpm(),
                detailed.stableRejectCounts()
        ));
        System.out.println("final=" + finalText);
    }

    private static Path requireWav(String suffixAlias) throws Exception {
        String suffix = suffixAlias.substring("recording".length());
        return LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith(suffix + ".wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for " + suffixAlias));
    }

    private static Map<String, CaseExpectation> buildCases() {
        LinkedHashMap<String, CaseExpectation> cases = new LinkedHashMap<>();
        cases.put("recording(12)", new CaseExpectation(
                "CQ CQ CQ DE BI9CMS BI9CMS IN 700 PSE K."
        ));
        cases.put("recording(14)", new CaseExpectation(
                "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 800 PSE K."
        ));
        cases.put("recording(15)", new CaseExpectation(
                "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 800 24WPM PSE K."
        ));
        return cases;
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

    private static long firstTurnStartMs(List<LocalAudioDecodeTestSupport.TurnTransitionTrace> traces) {
        for (LocalAudioDecodeTestSupport.TurnTransitionTrace trace : traces) {
            if (trace != null && trace.kind() == LocalAudioDecodeTestSupport.TurnTransitionTrace.Kind.START) {
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

    private static long resolveWindowEndMs(long turnStartMs, long trustMs, long flushTimestampMs) {
        long baseline = flushTimestampMs;
        if (turnStartMs >= 0L) {
            baseline = Math.min(baseline, turnStartMs + OPENING_WINDOW_MS);
        }
        if (trustMs >= 0L) {
            baseline = Math.min(baseline, trustMs);
        }
        return baseline;
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

    private static boolean showsUsefulFixedToneBootstrapProgress(CwSignalSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        return snapshot.targetToneLocked()
                || snapshot.consecutiveLockedFrames() >= PROGRESS_CONSECUTIVE_LOCKED_FRAMES_MIN
                || snapshot.recentLockedFrameRatio() >= PROGRESS_LOCKED_RATIO_MIN;
    }

    private static String progressReason(CwSignalSnapshot snapshot) {
        if (snapshot == null) {
            return "none";
        }
        if (snapshot.targetToneLocked()) {
            return "target-locked";
        }
        if (snapshot.consecutiveLockedFrames() >= PROGRESS_CONSECUTIVE_LOCKED_FRAMES_MIN) {
            return "cons-locked";
        }
        if (snapshot.recentLockedFrameRatio() >= PROGRESS_LOCKED_RATIO_MIN) {
            return "lock-ratio";
        }
        return "none";
    }

    private static int offsetFromPreferred(int frequencyHz) {
        if (frequencyHz <= 0) {
            return 0;
        }
        return frequencyHz - DEFAULT_PREFERRED_TONE_HZ;
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private static String textAtOrBefore(List<CwDecodeEvent> decodeEvents, long timestampMs) {
        String latestText = "";
        for (CwDecodeEvent decodeEvent : decodeEvents) {
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
            java.util.Arrays.fill(current, 0);
        }
        return previous[right.length()];
    }

    private static int countOccurrences(String text, String fragment) {
        if (text == null || text.isEmpty() || fragment == null || fragment.isEmpty()) {
            return 0;
        }
        int count = 0;
        int offset = 0;
        while (offset <= text.length() - fragment.length()) {
            int index = text.indexOf(fragment, offset);
            if (index < 0) {
                break;
            }
            count += 1;
            offset = index + fragment.length();
        }
        return count;
    }

    private static final class CaseExpectation {
        private final String expectedText;

        private CaseExpectation(String expectedText) {
            this.expectedText = expectedText;
        }
    }
}
