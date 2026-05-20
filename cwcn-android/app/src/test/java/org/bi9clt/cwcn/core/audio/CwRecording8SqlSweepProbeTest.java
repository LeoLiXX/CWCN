package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.Assert.assertTrue;

public final class CwRecording8SqlSweepProbeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int DEFAULT_SEED_WPM = 15;
    private static final int[] SQL_LEVELS = new int[]{40, 55, 65, 70, 75, 80};
    private static final int[] SEED_WPM_LEVELS = new int[]{12, 15, 18, 20, 22, 24, 26, 28};
    private static final String EXPECTED_TEXT =
            "CQ CQ DE BG1XXX K BG1XXX DE JA1ABC K JA1ABC DE BG1XXX TNX FER CALL UR RST 599 QTH BEIJING NAME LEO HW? K BG1XXX DE JA1ABC FB OM UR RST 579 QTH TOKYO NAME KEN BK TNX QSO 73 DE BG1XXX SK";

    @Test
    public void printRecording8SqlSweep() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(8).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (8)"));

        System.out.println("==== recording8 sql sweep ====");
        for (int sqlPercent : SQL_LEVELS) {
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                    LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                            wavFile,
                            PREFERRED_TONE_HZ,
                            DEFAULT_SEED_WPM,
                            sqlPercent,
                            false,
                            CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                    );

            String finalText = sanitize(detailed.probeResult().decodedText());
            String stableFinal = sanitize(textAtOrBefore(
                    detailed.stableAcceptedDecodeEvents(),
                    detailed.flushTimestampMs()
            ));
            String actualCanonical = canonicalize(finalText);
            double recall = charRecall(EXPECTED_TEXT, finalText);

            System.out.println(String.format(
                    Locale.US,
                    "sql=%d recall=%.4f chars=%d tone=%d/%d/%d stableLen=%d bg1=%d ja1=%d rst=%d sk=%d rejects=%s",
                    sqlPercent,
                    recall,
                    detailed.probeResult().decoderSnapshot().totalCharacters(),
                    detailed.probeResult().signalSnapshot().targetToneFrequencyHz(),
                    detailed.probeResult().signalSnapshot().effectiveAcquisitionWinnerFrequencyHz(),
                    detailed.probeResult().signalSnapshot().effectiveFinalAdoptedFrequencyHz(),
                    stableFinal.length(),
                    countOccurrences(actualCanonical, "BG1XXX"),
                    countOccurrences(actualCanonical, "JA1ABC"),
                    countOccurrences(actualCanonical, "RST"),
                    countOccurrences(actualCanonical, "SK"),
                    detailed.stableRejectCounts()
            ));
            System.out.println("final=" + finalText);
            System.out.println("stable=" + stableFinal);
        }

        assertTrue(true);
    }

    @Test
    public void printRecording8SeedSweep() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(8).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (8)"));

        System.out.println("==== recording8 seed sweep ====");
        for (int seedWpm : SEED_WPM_LEVELS) {
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                    LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                            wavFile,
                            PREFERRED_TONE_HZ,
                            seedWpm,
                            55,
                            false,
                            CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                    );

            String finalText = sanitize(detailed.probeResult().decodedText());
            String stableFinal = sanitize(textAtOrBefore(
                    detailed.stableAcceptedDecodeEvents(),
                    detailed.flushTimestampMs()
            ));
            String actualCanonical = canonicalize(finalText);
            double recall = charRecall(EXPECTED_TEXT, finalText);

            System.out.println(String.format(
                    Locale.US,
                    "seed=%d recall=%.4f chars=%d tone=%d/%d/%d estWpm=%d stableLen=%d bg1=%d ja1=%d rst=%d sk=%d rejects=%s",
                    seedWpm,
                    recall,
                    detailed.probeResult().decoderSnapshot().totalCharacters(),
                    detailed.probeResult().signalSnapshot().targetToneFrequencyHz(),
                    detailed.probeResult().signalSnapshot().effectiveAcquisitionWinnerFrequencyHz(),
                    detailed.probeResult().signalSnapshot().effectiveFinalAdoptedFrequencyHz(),
                    detailed.probeResult().timingSnapshot().estimatedWpm(),
                    stableFinal.length(),
                    countOccurrences(actualCanonical, "BG1XXX"),
                    countOccurrences(actualCanonical, "JA1ABC"),
                    countOccurrences(actualCanonical, "RST"),
                    countOccurrences(actualCanonical, "SK"),
                    detailed.stableRejectCounts()
            ));
            System.out.println("final=" + finalText);
            System.out.println("stable=" + stableFinal);
        }

        assertTrue(true);
    }

    @Test
    public void printRecording8StableDecisionProfile() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(8).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (8)"));

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                        wavFile,
                        PREFERRED_TONE_HZ,
                        DEFAULT_SEED_WPM,
                        55,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        System.out.println("==== recording8 stable decision profile ====");
        LocalAudioDecodeTestSupport.TimingStateTrace firstTrustedTrace = firstTrustedTrace(
                detailed.timingStateTraces()
        );
        System.out.println(String.format(
                Locale.US,
                "firstTrust=%dms trustedDot=%.1f reason=%s",
                firstTrustedTrace == null ? -1L : firstTrustedTrace.timestampMs(),
                firstTrustedTrace == null || firstTrustedTrace.debugSnapshot() == null
                        ? 0.0d
                        : firstTrustedTrace.debugSnapshot().trustedDotEstimateMs(),
                firstTrustedTrace == null || firstTrustedTrace.debugSnapshot() == null
                        ? "(none)"
                        : sanitize(firstTrustedTrace.debugSnapshot().lastTrustedUpdateReason())
        ));
        System.out.println("stableRejects=" + detailed.stableRejectCounts());
        int printed = 0;
        for (LocalAudioDecodeTestSupport.StableDecisionTrace trace : detailed.stableDecisionTraces()) {
            if (trace == null || "pass".equals(trace.decision())) {
                continue;
            }
            LocalAudioDecodeTestSupport.TimingStateTrace timingTrace = timingTraceAtOrBefore(
                    detailed.timingStateTraces(),
                    trace.timestampMs()
            );
            System.out.println(String.format(
                    Locale.US,
                    "@%d emit=%s seq=%s unk=%s decision=%s trusted=%s lock=%s lck=%.2f near=%.2f unl=%.2f dom=%.2f iso=%.2f hot=%.2f clip=%.2f rawWpm=%.1f rawDot=%d trustDot=%.1f reason=%s",
                    trace.timestampMs(),
                    sanitize(trace.emittedValue()),
                    sanitize(trace.sourceSequence()),
                    trace.unknownCharacter(),
                    trace.decision(),
                    trace.trustedTimingEstablished(),
                    trace.targetToneLocked(),
                    trace.recentLockedFrameRatio(),
                    trace.recentNearTargetLockedFrameRatio(),
                    trace.recentActiveUnlockedFrameRatio(),
                    trace.toneDominanceRatio(),
                    trace.narrowbandIsolationRatio(),
                    trace.recentHotFrameRatio(),
                    trace.recentClippingFrameRatio(),
                    trace.rawWpm(),
                    timingTrace == null || timingTrace.rawSnapshot() == null
                            ? 0L
                            : timingTrace.rawSnapshot().dotEstimateMs(),
                    timingTrace == null || timingTrace.debugSnapshot() == null
                            ? 0.0d
                            : timingTrace.debugSnapshot().trustedDotEstimateMs(),
                    timingTrace == null || timingTrace.debugSnapshot() == null
                            ? "(none)"
                            : sanitize(timingTrace.debugSnapshot().lastTrustedUpdateReason())
            ));
            printed += 1;
            if (printed >= 24) {
                break;
            }
        }

        assertTrue(true);
    }

    @Test
    public void printRecording8UnknownAfterTrustAdaptationWindows() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(8).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (8)"));

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                        wavFile,
                        PREFERRED_TONE_HZ,
                        DEFAULT_SEED_WPM,
                        55,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        System.out.println("==== recording8 unknown-after-trust adaptation windows ====");
        for (LocalAudioDecodeTestSupport.StableDecisionTrace trace : detailed.stableDecisionTraces()) {
            if (trace == null || !"unknown-after-trust".equals(trace.decision())) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "unknown @%d emit=%s seq=%s trusted=%s",
                    trace.timestampMs(),
                    sanitize(trace.emittedValue()),
                    sanitize(trace.sourceSequence()),
                    trace.trustedTimingEstablished()
            ));
            printAdaptationWindow(detailed.timingEventAdaptationTraces(), trace.timestampMs(), 1600L);
        }

        assertTrue(true);
    }

    @Test
    public void printRecording8UnknownAfterTrustFrontEndWindows() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(8).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (8)"));

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                        wavFile,
                        PREFERRED_TONE_HZ,
                        DEFAULT_SEED_WPM,
                        55,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        System.out.println("==== recording8 unknown-after-trust front-end windows ====");
        for (LocalAudioDecodeTestSupport.StableDecisionTrace trace : detailed.stableDecisionTraces()) {
            if (trace == null
                    || !"unknown-after-trust".equals(trace.decision())
                    || trace.sourceSequence() == null
                    || trace.sourceSequence().length() < 8) {
                continue;
            }
            long startMs = Math.max(0L, trace.timestampMs() - 1400L);
            long endMs = trace.timestampMs() + 80L;
            System.out.println(String.format(
                    Locale.US,
                    "unknown @%d emit=%s seq=%s window=%d..%d",
                    trace.timestampMs(),
                    sanitize(trace.emittedValue()),
                    sanitize(trace.sourceSequence()),
                    startMs,
                    endMs
            ));
            printFrontEndFrameWindow(detailed.frameSignalTraces(), startMs, endMs);
        }

        assertTrue(true);
    }

    @Test
    public void printRecording8FallbackAttackReopenProfile() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(8).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (8)"));

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                        wavFile,
                        PREFERRED_TONE_HZ,
                        DEFAULT_SEED_WPM,
                        55,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        System.out.println("==== recording8 fallback attack reopen profile ====");
        List<FallbackAttackReopenProfile> profiles = new ArrayList<>();
        Map<String, Integer> bucketCounts = new LinkedHashMap<>();
        List<LocalAudioDecodeTestSupport.FrameSignalTrace> traces = detailed.frameSignalTraces();
        for (int index = 0; index < traces.size(); index++) {
            LocalAudioDecodeTestSupport.FrameSignalTrace trace = traces.get(index);
            if (trace == null
                    || trace.snapshot() == null
                    || !"ALLOW:ATTACK_THRESHOLD".equals(trace.toneOnDecision())
                    || trace.toneOnAcceptedByRescue()
                    || trace.postReleaseGapMs() < 0L) {
                continue;
            }
            LocalAudioDecodeTestSupport.FrameSignalTrace previous = index > 0
                    ? traces.get(index - 1)
                    : null;
            FallbackAttackReopenProfile profile = buildFallbackAttackReopenProfile(
                    traces,
                    index,
                    previous,
                    trace,
                    detailed.stableDecisionTraces()
            );
            profiles.add(profile);
            String bucketKey = profile.outcomeBucket()
                    + " | prevTail=" + profile.previousReleaseTailDecision()
                    + " | rescue=" + profile.postReleaseRescueDecision();
            bucketCounts.put(bucketKey, bucketCounts.getOrDefault(bucketKey, 0) + 1);
        }

        for (FallbackAttackReopenProfile profile : profiles) {
            System.out.println(String.format(
                    Locale.US,
                    "@%d outcome=%s prevTail=%s rescue=%s gap=%d window=%d slack=%d det/on=%.2f"
                            + " det=%.1f on=%d dom=%.2f iso=%.2f loc=%.2f lck=%.2f near=%.2f"
                            + " chain=%d prevRescueTone=%s prevLock=%s trackMem=%s anchor=%d",
                    profile.timestampMs(),
                    profile.outcomeBucket(),
                    sanitize(profile.previousReleaseTailDecision()),
                    sanitize(profile.postReleaseRescueDecision()),
                    profile.postReleaseGapMs(),
                    profile.postReleaseWindowMs(),
                    profile.gapSlackMs(),
                    profile.detectionToAttackRatio(),
                    profile.detectionLevel(),
                    profile.toneOnThreshold(),
                    profile.dominanceRatio(),
                    profile.isolationRatio(),
                    profile.localContrastRatio(),
                    profile.recentLockedRatio(),
                    profile.recentNearTargetLockedRatio(),
                    profile.postReleaseWeakContinuityRescueCount(),
                    profile.previousToneStartedByRescue(),
                    profile.previousLocked(),
                    profile.trackedToneMemoryActiveBeforeFrame(),
                    profile.attackAnchorFrequencyHzBeforeFrame()
            ));
            if (profile.linkedDecision() != null) {
                System.out.println(String.format(
                        Locale.US,
                        "  linked @%d decision=%s emit=%s seq=%s",
                        profile.linkedDecision().timestampMs(),
                        sanitize(profile.linkedDecision().decision()),
                        sanitize(profile.linkedDecision().emittedValue()),
                        sanitize(profile.linkedDecision().sourceSequence())
                ));
            }
        }

        System.out.println("---- buckets ----");
        for (Map.Entry<String, Integer> entry : bucketCounts.entrySet()) {
            System.out.println(entry.getValue() + " x " + entry.getKey());
        }

        assertTrue(true);
    }

    @Test
    public void printRecording8UnknownLastFallbackAttackProfile() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(8).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (8)"));

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                        wavFile,
                        PREFERRED_TONE_HZ,
                        DEFAULT_SEED_WPM,
                        55,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        System.out.println("==== recording8 unknown last fallback attack profile ====");
        Map<String, Integer> bucketCounts = new LinkedHashMap<>();
        List<LocalAudioDecodeTestSupport.FrameSignalTrace> traces = detailed.frameSignalTraces();
        for (LocalAudioDecodeTestSupport.StableDecisionTrace decisionTrace : detailed.stableDecisionTraces()) {
            if (decisionTrace == null || !"unknown-after-trust".equals(decisionTrace.decision())) {
                continue;
            }
            LastFallbackAttackBeforeDecisionProfile profile =
                    buildLastFallbackAttackBeforeDecisionProfile(traces, decisionTrace, 1400L);
            if (profile == null) {
                System.out.println(String.format(
                        Locale.US,
                        "unknown @%d emit=%s seq=%s lastFallback=(none)",
                        decisionTrace.timestampMs(),
                        sanitize(decisionTrace.emittedValue()),
                        sanitize(decisionTrace.sourceSequence())
                ));
                bucketCounts.put("none", bucketCounts.getOrDefault("none", 0) + 1);
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "unknown @%d emit=%s seq=%s lastFallback@%d fallbacks=%d prevTail=%s rescue=%s"
                            + " gap=%d window=%d slack=%d det/on=%.2f det=%.1f on=%d"
                            + " dom=%.2f iso=%.2f loc=%.2f lck=%.2f near=%.2f anchor=%d",
                    decisionTrace.timestampMs(),
                    sanitize(decisionTrace.emittedValue()),
                    sanitize(decisionTrace.sourceSequence()),
                    profile.fallbackTimestampMs(),
                    profile.fallbackCountInWindow(),
                    sanitize(profile.previousReleaseTailDecision()),
                    sanitize(profile.postReleaseRescueDecision()),
                    profile.postReleaseGapMs(),
                    profile.postReleaseWindowMs(),
                    profile.gapSlackMs(),
                    profile.detectionToAttackRatio(),
                    profile.detectionLevel(),
                    profile.toneOnThreshold(),
                    profile.dominanceRatio(),
                    profile.isolationRatio(),
                    profile.localContrastRatio(),
                    profile.recentLockedRatio(),
                    profile.recentNearTargetLockedRatio(),
                    profile.attackAnchorFrequencyHzBeforeFrame()
            ));
            String bucketKey = "prevTail=" + profile.previousReleaseTailDecision()
                    + " | rescue=" + profile.postReleaseRescueDecision();
            bucketCounts.put(bucketKey, bucketCounts.getOrDefault(bucketKey, 0) + 1);
        }

        System.out.println("---- buckets ----");
        for (Map.Entry<String, Integer> entry : bucketCounts.entrySet()) {
            System.out.println(entry.getValue() + " x " + entry.getKey());
        }

        assertTrue(true);
    }

    @Test
    public void printRecording8FrontEndLearningLastFallbackAttackProfile() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(8).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (8)"));

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                        wavFile,
                        PREFERRED_TONE_HZ,
                        DEFAULT_SEED_WPM,
                        55,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        System.out.println("==== recording8 front-end-learning last fallback attack profile ====");
        printDecisionLastFallbackAttackProfile(
                detailed.frameSignalTraces(),
                detailed.stableDecisionTraces(),
                "front-end-learning",
                1400L
        );

        assertTrue(true);
    }

    @Test
    public void printRecording8UnknownDecisionEventChains() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(8).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (8)"));

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                        wavFile,
                        PREFERRED_TONE_HZ,
                        DEFAULT_SEED_WPM,
                        55,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        System.out.println("==== recording8 unknown decision event chains ====");
        printDecisionEventChains(
                detailed.frameSignalTraces(),
                detailed.stableDecisionTraces(),
                "unknown-after-trust",
                1800L
        );

        assertTrue(true);
    }

    @Test
    public void printRecording8FrontEndLearningDecisionEventChains() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(8).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (8)"));

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                        wavFile,
                        PREFERRED_TONE_HZ,
                        DEFAULT_SEED_WPM,
                        55,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        System.out.println("==== recording8 front-end-learning decision event chains ====");
        printDecisionEventChains(
                detailed.frameSignalTraces(),
                detailed.stableDecisionTraces(),
                "front-end-learning",
                1800L
        );

        assertTrue(true);
    }

    @Test
    public void printImmediateFallbackCommitSuppressionSweep() throws Exception {
        List<CommitSuppressionRule> rules = buildCommitSuppressionRules();
        List<RecordingExpectation> recordings = new ArrayList<>();
        recordings.add(new RecordingExpectation(
                "录音 (2)",
                "CQ DX CQ DX DE JV3VV JV3VV PAGE K. CQ DX CQ DX DE JV3VV JV3VV PAGE K."
        ));
        recordings.add(new RecordingExpectation(
                "录音 (8)",
                EXPECTED_TEXT
        ));
        recordings.add(new RecordingExpectation(
                "录音 (16)",
                "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 700 24WPM PSE K."
        ));

        System.out.println("==== immediate fallback commit suppression sweep ====");
        for (RecordingExpectation recording : recordings) {
            Path wavFile = requireRecordingWav(recording.recordingLabel());
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                    LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                            wavFile,
                            PREFERRED_TONE_HZ,
                            DEFAULT_SEED_WPM,
                            55,
                            false,
                            CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                    );
            List<MatchedCommittedDecision> matches = matchCommittedCharacterDecisions(detailed, 1800L);
            SimulatedCommitResult baseline = simulateCommittedOutput(
                    detailed.decodeEvents(),
                    matches,
                    rule -> false
            );
            printSuppressionSummary(recording, "baseline", baseline);

            for (CommitSuppressionRule rule : rules) {
                SimulatedCommitResult result = simulateCommittedOutput(
                        detailed.decodeEvents(),
                        matches,
                        rule.predicate()
                );
                printSuppressionSummary(recording, rule.name(), result);
            }
        }

        assertTrue(true);
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

    private static LocalAudioDecodeTestSupport.TimingStateTrace firstTrustedTrace(
            List<LocalAudioDecodeTestSupport.TimingStateTrace> traces
    ) {
        if (traces == null) {
            return null;
        }
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : traces) {
            if (trace != null
                    && trace.debugSnapshot() != null
                    && trace.debugSnapshot().trustedDotEstimateMs() > 0.0d) {
                return trace;
            }
        }
        return null;
    }

    private static LastFallbackAttackBeforeDecisionProfile buildLastFallbackAttackBeforeDecisionProfile(
            List<LocalAudioDecodeTestSupport.FrameSignalTrace> traces,
            LocalAudioDecodeTestSupport.StableDecisionTrace decisionTrace,
            long lookbackMs
    ) {
        if (traces == null || decisionTrace == null) {
            return null;
        }
        long startMs = Math.max(0L, decisionTrace.timestampMs() - lookbackMs);
        int fallbackCount = 0;
        int lastIndex = -1;
        for (int index = 0; index < traces.size(); index++) {
            LocalAudioDecodeTestSupport.FrameSignalTrace trace = traces.get(index);
            if (trace == null || trace.snapshot() == null) {
                continue;
            }
            if (trace.timestampMs() < startMs) {
                continue;
            }
            if (trace.timestampMs() > decisionTrace.timestampMs()) {
                break;
            }
            if ("ALLOW:ATTACK_THRESHOLD".equals(trace.toneOnDecision())
                    && !trace.toneOnAcceptedByRescue()
                    && trace.postReleaseGapMs() >= 0L) {
                fallbackCount += 1;
                lastIndex = index;
            }
        }
        if (lastIndex < 0) {
            return null;
        }
        LocalAudioDecodeTestSupport.FrameSignalTrace trace = traces.get(lastIndex);
        LocalAudioDecodeTestSupport.FrameSignalTrace precedingTailContext =
                findLatestMeaningfulTailContext(traces, lastIndex);
        return new LastFallbackAttackBeforeDecisionProfile(
                trace.timestampMs(),
                fallbackCount,
                precedingTailContext == null ? "NONE" : precedingTailContext.releaseTailHoldDecision(),
                trace.postReleaseRescueDecision(),
                trace.postReleaseGapMs(),
                trace.postReleaseWindowMs(),
                trace.postReleaseGapMs() - trace.postReleaseWindowMs(),
                trace.detectionLevel(),
                trace.toneOnThreshold(),
                trace.snapshot().toneDominanceRatio(),
                trace.snapshot().narrowbandIsolationRatio(),
                trace.localContrastRatio(),
                trace.snapshot().recentLockedFrameRatio(),
                trace.snapshot().recentNearTargetLockedFrameRatio(),
                trace.attackAnchorFrequencyHzBeforeFrame()
        );
    }

    private static void printDecisionLastFallbackAttackProfile(
            List<LocalAudioDecodeTestSupport.FrameSignalTrace> traces,
            List<LocalAudioDecodeTestSupport.StableDecisionTrace> decisionTraces,
            String decisionName,
            long lookbackMs
    ) {
        Map<String, Integer> bucketCounts = new LinkedHashMap<>();
        for (LocalAudioDecodeTestSupport.StableDecisionTrace decisionTrace : decisionTraces) {
            if (decisionTrace == null || !decisionName.equals(decisionTrace.decision())) {
                continue;
            }
            LastFallbackAttackBeforeDecisionProfile profile =
                    buildLastFallbackAttackBeforeDecisionProfile(traces, decisionTrace, lookbackMs);
            if (profile == null) {
                System.out.println(String.format(
                        Locale.US,
                        "%s @%d emit=%s seq=%s lastFallback=(none)",
                        decisionName,
                        decisionTrace.timestampMs(),
                        sanitize(decisionTrace.emittedValue()),
                        sanitize(decisionTrace.sourceSequence())
                ));
                bucketCounts.put("none", bucketCounts.getOrDefault("none", 0) + 1);
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "%s @%d emit=%s seq=%s lastFallback@%d fallbacks=%d prevTail=%s rescue=%s"
                            + " gap=%d window=%d slack=%d det/on=%.2f det=%.1f on=%d"
                            + " dom=%.2f iso=%.2f loc=%.2f lck=%.2f near=%.2f anchor=%d",
                    decisionName,
                    decisionTrace.timestampMs(),
                    sanitize(decisionTrace.emittedValue()),
                    sanitize(decisionTrace.sourceSequence()),
                    profile.fallbackTimestampMs(),
                    profile.fallbackCountInWindow(),
                    sanitize(profile.previousReleaseTailDecision()),
                    sanitize(profile.postReleaseRescueDecision()),
                    profile.postReleaseGapMs(),
                    profile.postReleaseWindowMs(),
                    profile.gapSlackMs(),
                    profile.detectionToAttackRatio(),
                    profile.detectionLevel(),
                    profile.toneOnThreshold(),
                    profile.dominanceRatio(),
                    profile.isolationRatio(),
                    profile.localContrastRatio(),
                    profile.recentLockedRatio(),
                    profile.recentNearTargetLockedRatio(),
                    profile.attackAnchorFrequencyHzBeforeFrame()
            ));
            String bucketKey = "prevTail=" + profile.previousReleaseTailDecision()
                    + " | rescue=" + profile.postReleaseRescueDecision();
            bucketCounts.put(bucketKey, bucketCounts.getOrDefault(bucketKey, 0) + 1);
        }

        System.out.println("---- buckets ----");
        for (Map.Entry<String, Integer> entry : bucketCounts.entrySet()) {
            System.out.println(entry.getValue() + " x " + entry.getKey());
        }
    }

    private static void printDecisionEventChains(
            List<LocalAudioDecodeTestSupport.FrameSignalTrace> traces,
            List<LocalAudioDecodeTestSupport.StableDecisionTrace> decisionTraces,
            String decisionName,
            long lookbackMs
    ) {
        Map<String, Integer> bucketCounts = new LinkedHashMap<>();
        for (LocalAudioDecodeTestSupport.StableDecisionTrace decisionTrace : decisionTraces) {
            if (decisionTrace == null || !decisionName.equals(decisionTrace.decision())) {
                continue;
            }
            DecisionEventChainProfile profile =
                    buildDecisionEventChainProfile(traces, decisionTrace, lookbackMs);
            String bucketKey = profile.bucketKey();
            bucketCounts.put(bucketKey, bucketCounts.getOrDefault(bucketKey, 0) + 1);

            System.out.println(String.format(
                    Locale.US,
                    "%s @%d emit=%s seq=%s deltas rescue->tail=%s tail->fallback=%s fallback->decision=%s",
                    decisionName,
                    decisionTrace.timestampMs(),
                    sanitize(decisionTrace.emittedValue()),
                    sanitize(decisionTrace.sourceSequence()),
                    formatDelta(profile.rescueToTailDeltaMs()),
                    formatDelta(profile.tailToFallbackDeltaMs()),
                    formatDelta(profile.fallbackToDecisionDeltaMs())
            ));
            printChainFrameEvent("rescue", profile.lastAcceptedRescueReopen(), decisionTrace.timestampMs());
            printChainFrameEvent("tail", profile.lastTailBlock(), decisionTrace.timestampMs());
            printChainFrameEvent("fallback", profile.lastFallbackAttack(), decisionTrace.timestampMs());
            System.out.println(String.format(
                    Locale.US,
                    "  decision @%d trusted=%s lock=%s lck=%.2f near=%.2f unl=%.2f dom=%.2f iso=%.2f hot=%.2f clip=%.2f rawWpm=%.1f",
                    decisionTrace.timestampMs(),
                    decisionTrace.trustedTimingEstablished(),
                    decisionTrace.targetToneLocked(),
                    decisionTrace.recentLockedFrameRatio(),
                    decisionTrace.recentNearTargetLockedFrameRatio(),
                    decisionTrace.recentActiveUnlockedFrameRatio(),
                    decisionTrace.toneDominanceRatio(),
                    decisionTrace.narrowbandIsolationRatio(),
                    decisionTrace.recentHotFrameRatio(),
                    decisionTrace.recentClippingFrameRatio(),
                    decisionTrace.rawWpm()
            ));
        }

        System.out.println("---- buckets ----");
        for (Map.Entry<String, Integer> entry : bucketCounts.entrySet()) {
            System.out.println(entry.getValue() + " x " + entry.getKey());
        }
    }

    private static void printSuppressionSummary(
            RecordingExpectation recording,
            String variantName,
            SimulatedCommitResult result
    ) {
        String finalText = sanitize(result.outputText());
        double recall = charRecall(recording.expectedText(), finalText);
        String actualCanonical = canonicalize(finalText);
        System.out.println(String.format(
                Locale.US,
                "%s variant=%s recall=%.4f chars=%d bg1=%d ja1=%d rst=%d sk=%d suppressed=%d decisions=%s",
                recording.recordingLabel(),
                variantName,
                recall,
                actualCanonical.length(),
                countOccurrences(actualCanonical, "BG1XXX"),
                countOccurrences(actualCanonical, "JA1ABC"),
                countOccurrences(actualCanonical, "RST"),
                countOccurrences(actualCanonical, "SK"),
                result.suppressedCharacterCount(),
                result.suppressedDecisionCounts()
        ));
        if ("录音 (8)".equals(recording.recordingLabel())
                || result.suppressedCharacterCount() > 0
                || recall < 0.9999d) {
            System.out.println("text=" + finalText);
        }
    }

    private static LocalAudioDecodeTestSupport.TimingStateTrace timingTraceAtOrBefore(
            List<LocalAudioDecodeTestSupport.TimingStateTrace> traces,
            long timestampMs
    ) {
        LocalAudioDecodeTestSupport.TimingStateTrace latest = null;
        if (traces == null) {
            return null;
        }
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : traces) {
            if (trace == null || trace.timestampMs() > timestampMs) {
                break;
            }
            latest = trace;
        }
        return latest;
    }

    private static void printAdaptationWindow(
            List<LocalAudioDecodeTestSupport.TimingEventAdaptationTrace> traces,
            long centerTimestampMs,
            long windowMs
    ) {
        if (traces == null) {
            return;
        }
        long startMs = Math.max(0L, centerTimestampMs - windowMs);
        long endMs = centerTimestampMs + 120L;
        for (LocalAudioDecodeTestSupport.TimingEventAdaptationTrace trace : traces) {
            if (trace == null || trace.timestampMs() < startMs || trace.timestampMs() > endMs) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "  @%d kind=%s dur=%d raw=%s wpm=%s anc=%s dot=%d/%d/%d intra=%d/%d/%d trust=%s/%s/%d",
                    trace.timestampMs(),
                    trace.eventKind(),
                    trace.durationMs(),
                    trace.rawClassification(),
                    trace.wpmGuardClassification(),
                    trace.anchorClassification(),
                    trace.rawDotEstimateMs(),
                    trace.wpmGuardDotEstimateMs(),
                    trace.anchorDotEstimateMs(),
                    trace.rawIntraGapEstimateMs(),
                    trace.wpmGuardIntraGapEstimateMs(),
                    trace.anchorIntraGapEstimateMs(),
                    trace.trustedTimingEstablished(),
                    trace.trustOrigin(),
                    trace.trustedDotEstimateMs()
            ));
        }
    }

    private static FallbackAttackReopenProfile buildFallbackAttackReopenProfile(
            List<LocalAudioDecodeTestSupport.FrameSignalTrace> traces,
            int currentIndex,
            LocalAudioDecodeTestSupport.FrameSignalTrace previous,
            LocalAudioDecodeTestSupport.FrameSignalTrace current,
            List<LocalAudioDecodeTestSupport.StableDecisionTrace> decisionTraces
    ) {
        LocalAudioDecodeTestSupport.FrameSignalTrace precedingTailContext =
                findLatestMeaningfulTailContext(traces, currentIndex);
        LocalAudioDecodeTestSupport.StableDecisionTrace unknownDecision = findDecisionWindow(
                decisionTraces,
                current.timestampMs(),
                "unknown-after-trust",
                1400L,
                80L
        );
        LocalAudioDecodeTestSupport.StableDecisionTrace learningDecision = findDecisionWindow(
                decisionTraces,
                current.timestampMs(),
                "front-end-learning",
                1400L,
                80L
        );
        LocalAudioDecodeTestSupport.StableDecisionTrace linkedDecision = unknownDecision != null
                ? unknownDecision
                : learningDecision != null
                ? learningDecision
                : firstNonPassDecisionAtOrAfter(decisionTraces, current.timestampMs(), 1800L);
        String outcomeBucket;
        if (unknownDecision != null) {
            outcomeBucket = "unknown-window";
        } else if (learningDecision != null) {
            outcomeBucket = "front-end-learning-window";
        } else if (linkedDecision != null) {
            outcomeBucket = sanitize(linkedDecision.decision()) + "-after";
        } else {
            outcomeBucket = "no-reject-window";
        }
        long gapSlackMs = current.postReleaseGapMs() - current.postReleaseWindowMs();
        return new FallbackAttackReopenProfile(
                current.timestampMs(),
                outcomeBucket,
                linkedDecision,
                precedingTailContext == null ? "NONE" : precedingTailContext.releaseTailHoldDecision(),
                current.postReleaseRescueDecision(),
                current.postReleaseGapMs(),
                current.postReleaseWindowMs(),
                gapSlackMs,
                current.detectionLevel(),
                current.toneOnThreshold(),
                current.snapshot().toneDominanceRatio(),
                current.snapshot().narrowbandIsolationRatio(),
                current.localContrastRatio(),
                current.snapshot().recentLockedFrameRatio(),
                current.snapshot().recentNearTargetLockedFrameRatio(),
                current.postReleaseWeakContinuityRescueCount(),
                precedingTailContext != null && precedingTailContext.currentToneStartedByPostReleaseRescue(),
                precedingTailContext != null
                        && precedingTailContext.snapshot() != null
                        && precedingTailContext.snapshot().targetToneLocked(),
                current.trackedToneMemoryActiveBeforeFrame(),
                current.attackAnchorFrequencyHzBeforeFrame()
        );
    }

    private static LocalAudioDecodeTestSupport.FrameSignalTrace findLatestMeaningfulTailContext(
            List<LocalAudioDecodeTestSupport.FrameSignalTrace> traces,
            int currentIndex
    ) {
        if (traces == null) {
            return null;
        }
        for (int index = currentIndex - 1; index >= 0; index--) {
            LocalAudioDecodeTestSupport.FrameSignalTrace trace = traces.get(index);
            if (trace == null) {
                continue;
            }
            String decision = trace.releaseTailHoldDecision();
            if (decision != null && !"SKIP:NO_ATTEMPT".equals(decision)) {
                return trace;
            }
        }
        return null;
    }

    private static DecisionEventChainProfile buildDecisionEventChainProfile(
            List<LocalAudioDecodeTestSupport.FrameSignalTrace> traces,
            LocalAudioDecodeTestSupport.StableDecisionTrace decisionTrace,
            long lookbackMs
    ) {
        if (traces == null || decisionTrace == null) {
            return new DecisionEventChainProfile(-1L, null, null, null);
        }

        long startMs = Math.max(0L, decisionTrace.timestampMs() - lookbackMs);
        int fallbackIndex = findLastFallbackAttackIndex(traces, startMs, decisionTrace.timestampMs());
        int tailBlockIndex = findLastTailBlockIndex(
                traces,
                startMs,
                fallbackIndex >= 0 ? traces.get(fallbackIndex).timestampMs() : decisionTrace.timestampMs()
        );
        int rescueIndex = findLastAcceptedRescueReopenIndex(
                traces,
                startMs,
                tailBlockIndex >= 0 ? traces.get(tailBlockIndex).timestampMs() : decisionTrace.timestampMs()
        );

        LocalAudioDecodeTestSupport.FrameSignalTrace rescueTrace =
                rescueIndex >= 0 ? traces.get(rescueIndex) : null;
        LocalAudioDecodeTestSupport.FrameSignalTrace tailTrace =
                tailBlockIndex >= 0 ? traces.get(tailBlockIndex) : null;
        LocalAudioDecodeTestSupport.FrameSignalTrace fallbackTrace =
                fallbackIndex >= 0 ? traces.get(fallbackIndex) : null;

        if (tailTrace == null && rescueTrace != null) {
            tailTrace = findLastTailBlockTraceAfter(
                    traces,
                    rescueTrace.timestampMs(),
                    decisionTrace.timestampMs()
            );
        }
        if (fallbackTrace == null && tailTrace != null) {
            fallbackTrace = findLastFallbackAttackTraceAfter(
                    traces,
                    tailTrace.timestampMs(),
                    decisionTrace.timestampMs()
            );
        }
        if (rescueTrace == null && tailTrace != null) {
            rescueTrace = findLastAcceptedRescueReopenTraceBefore(
                    traces,
                    startMs,
                    tailTrace.timestampMs()
            );
        }

        return new DecisionEventChainProfile(
                decisionTrace.timestampMs(),
                rescueTrace,
                tailTrace,
                fallbackTrace
        );
    }

    private static List<MatchedCommittedDecision> matchCommittedCharacterDecisions(
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed,
            long lookbackMs
    ) {
        ArrayList<MatchedCommittedDecision> matches = new ArrayList<>();
        if (detailed == null) {
            return matches;
        }
        List<CwDecodeEvent> decodeEvents = detailed.decodeEvents();
        List<LocalAudioDecodeTestSupport.StableDecisionTrace> decisionTraces =
                detailed.stableDecisionTraces();
        int traceCursor = 0;
        for (int eventIndex = 0; eventIndex < decodeEvents.size(); eventIndex++) {
            CwDecodeEvent decodeEvent = decodeEvents.get(eventIndex);
            if (decodeEvent == null || decodeEvent.type() != CwDecodeEvent.Type.CHARACTER_DECODED) {
                continue;
            }
            int matchedTraceIndex = findMatchingStableDecisionTraceIndex(
                    decisionTraces,
                    traceCursor,
                    decodeEvent
            );
            if (matchedTraceIndex < 0) {
                continue;
            }
            traceCursor = matchedTraceIndex + 1;
            LocalAudioDecodeTestSupport.StableDecisionTrace decisionTrace =
                    decisionTraces.get(matchedTraceIndex);
            matches.add(new MatchedCommittedDecision(
                    eventIndex,
                    decodeEvent,
                    decisionTrace,
                    buildDecisionEventChainProfile(
                            detailed.frameSignalTraces(),
                            decisionTrace,
                            lookbackMs
                    )
            ));
        }
        return matches;
    }

    private static int findMatchingStableDecisionTraceIndex(
            List<LocalAudioDecodeTestSupport.StableDecisionTrace> traces,
            int startIndex,
            CwDecodeEvent decodeEvent
    ) {
        if (traces == null || decodeEvent == null) {
            return -1;
        }
        for (int index = Math.max(0, startIndex); index < traces.size(); index++) {
            LocalAudioDecodeTestSupport.StableDecisionTrace trace = traces.get(index);
            if (trace == null) {
                continue;
            }
            if (trace.timestampMs() != decodeEvent.timestampMs()) {
                continue;
            }
            if (!safeEquals(trace.emittedValue(), decodeEvent.emittedValue())) {
                continue;
            }
            if (!safeEquals(trace.sourceSequence(), decodeEvent.sourceSequence())) {
                continue;
            }
            return index;
        }
        return -1;
    }

    private static int findLastFallbackAttackIndex(
            List<LocalAudioDecodeTestSupport.FrameSignalTrace> traces,
            long startMs,
            long endMs
    ) {
        if (traces == null) {
            return -1;
        }
        for (int index = traces.size() - 1; index >= 0; index--) {
            LocalAudioDecodeTestSupport.FrameSignalTrace trace = traces.get(index);
            if (!isWithinWindow(trace, startMs, endMs)) {
                continue;
            }
            if (isFallbackAttack(trace)) {
                return index;
            }
        }
        return -1;
    }

    private static int findLastTailBlockIndex(
            List<LocalAudioDecodeTestSupport.FrameSignalTrace> traces,
            long startMs,
            long endMs
    ) {
        if (traces == null) {
            return -1;
        }
        for (int index = traces.size() - 1; index >= 0; index--) {
            LocalAudioDecodeTestSupport.FrameSignalTrace trace = traces.get(index);
            if (!isWithinWindow(trace, startMs, endMs)) {
                continue;
            }
            if (isTailBlock(trace)) {
                return index;
            }
        }
        return -1;
    }

    private static int findLastAcceptedRescueReopenIndex(
            List<LocalAudioDecodeTestSupport.FrameSignalTrace> traces,
            long startMs,
            long endMs
    ) {
        if (traces == null) {
            return -1;
        }
        for (int index = traces.size() - 1; index >= 0; index--) {
            LocalAudioDecodeTestSupport.FrameSignalTrace trace = traces.get(index);
            if (!isWithinWindow(trace, startMs, endMs)) {
                continue;
            }
            if (isAcceptedRescueReopen(trace)) {
                return index;
            }
        }
        return -1;
    }

    private static LocalAudioDecodeTestSupport.FrameSignalTrace findLastTailBlockTraceAfter(
            List<LocalAudioDecodeTestSupport.FrameSignalTrace> traces,
            long startMs,
            long endMs
    ) {
        int index = findLastTailBlockIndex(traces, startMs, endMs);
        return index >= 0 ? traces.get(index) : null;
    }

    private static LocalAudioDecodeTestSupport.FrameSignalTrace findLastFallbackAttackTraceAfter(
            List<LocalAudioDecodeTestSupport.FrameSignalTrace> traces,
            long startMs,
            long endMs
    ) {
        int index = findLastFallbackAttackIndex(traces, startMs, endMs);
        return index >= 0 ? traces.get(index) : null;
    }

    private static LocalAudioDecodeTestSupport.FrameSignalTrace findLastAcceptedRescueReopenTraceBefore(
            List<LocalAudioDecodeTestSupport.FrameSignalTrace> traces,
            long startMs,
            long endMs
    ) {
        int index = findLastAcceptedRescueReopenIndex(traces, startMs, endMs);
        return index >= 0 ? traces.get(index) : null;
    }

    private static boolean isWithinWindow(
            LocalAudioDecodeTestSupport.FrameSignalTrace trace,
            long startMs,
            long endMs
    ) {
        return trace != null
                && trace.snapshot() != null
                && trace.timestampMs() >= startMs
                && trace.timestampMs() <= endMs;
    }

    private static boolean isFallbackAttack(LocalAudioDecodeTestSupport.FrameSignalTrace trace) {
        return trace != null
                && trace.snapshot() != null
                && "ALLOW:ATTACK_THRESHOLD".equals(trace.toneOnDecision())
                && !trace.toneOnAcceptedByRescue()
                && trace.postReleaseGapMs() >= 0L;
    }

    private static boolean isAcceptedRescueReopen(LocalAudioDecodeTestSupport.FrameSignalTrace trace) {
        return trace != null
                && trace.snapshot() != null
                && trace.toneOnAcceptedByRescue()
                && trace.postReleaseGapMs() >= 0L;
    }

    private static boolean isTailBlock(LocalAudioDecodeTestSupport.FrameSignalTrace trace) {
        return trace != null
                && trace.snapshot() != null
                && trace.releaseTailHoldDecision() != null
                && trace.releaseTailHoldDecision().startsWith("BLOCKED:");
    }

    private static SimulatedCommitResult simulateCommittedOutput(
            List<CwDecodeEvent> decodeEvents,
            List<MatchedCommittedDecision> matches,
            Predicate<MatchedCommittedDecision> suppressionPredicate
    ) {
        StringBuilder builder = new StringBuilder();
        Map<Integer, MatchedCommittedDecision> matchesByEventIndex = new LinkedHashMap<>();
        for (MatchedCommittedDecision match : matches) {
            matchesByEventIndex.put(match.decodeEventIndex(), match);
        }
        Map<String, Integer> suppressedDecisionCounts = new LinkedHashMap<>();
        int suppressedCharacterCount = 0;
        for (int eventIndex = 0; eventIndex < decodeEvents.size(); eventIndex++) {
            CwDecodeEvent decodeEvent = decodeEvents.get(eventIndex);
            if (decodeEvent == null) {
                continue;
            }
            if (decodeEvent.type() == CwDecodeEvent.Type.CHARACTER_DECODED) {
                MatchedCommittedDecision match = matchesByEventIndex.get(eventIndex);
                if (match != null && suppressionPredicate.test(match)) {
                    suppressedCharacterCount += 1;
                    String decisionKey = sanitize(match.decisionTrace().decision());
                    suppressedDecisionCounts.put(
                            decisionKey,
                            suppressedDecisionCounts.getOrDefault(decisionKey, 0) + 1
                    );
                    continue;
                }
                builder.append(decodeEvent.emittedValue() == null ? "" : decodeEvent.emittedValue());
                continue;
            }
            if (decodeEvent.type() == CwDecodeEvent.Type.WORD_BREAK
                    && builder.length() > 0
                    && builder.charAt(builder.length() - 1) != ' ') {
                builder.append(' ');
            }
        }
        return new SimulatedCommitResult(
                builder.toString().trim(),
                suppressedCharacterCount,
                suppressedDecisionCounts
        );
    }

    private static void printChainFrameEvent(
            String label,
            LocalAudioDecodeTestSupport.FrameSignalTrace trace,
            long decisionTimestampMs
    ) {
        if (trace == null || trace.snapshot() == null) {
            System.out.println("  " + label + " (none)");
            return;
        }
        System.out.println(String.format(
                Locale.US,
                "  %s @%d dt=%d act=%s lock=%s lck=%.2f near=%.2f unl=%.2f det=%.1f on=%d"
                        + " gap=%d/%d slack=%d rescue=%s toneOn=%s byRescue=%s rescueTone=%s"
                        + " tail=%s dom=%.2f iso=%.2f loc=%.2f anchor=%d weakChain=%s(%d) chainStart=%d gapLimit=%d"
                        + " weakCand=%s trustedCand=%s",
                label,
                trace.timestampMs(),
                decisionTimestampMs - trace.timestampMs(),
                trace.snapshot().toneActive(),
                trace.snapshot().targetToneLocked(),
                trace.snapshot().recentLockedFrameRatio(),
                trace.snapshot().recentNearTargetLockedFrameRatio(),
                trace.snapshot().recentActiveUnlockedFrameRatio(),
                trace.detectionLevel(),
                trace.toneOnThreshold(),
                trace.postReleaseGapMs(),
                trace.postReleaseWindowMs(),
                trace.postReleaseGapMs() - trace.postReleaseWindowMs(),
                sanitize(trace.postReleaseRescueDecision()),
                sanitize(trace.toneOnDecision()),
                trace.toneOnAcceptedByRescue(),
                trace.currentToneStartedByPostReleaseRescue(),
                sanitize(trace.releaseTailHoldDecision()),
                trace.snapshot().toneDominanceRatio(),
                trace.snapshot().narrowbandIsolationRatio(),
                trace.localContrastRatio(),
                trace.attackAnchorFrequencyHzBeforeFrame(),
                trace.trustedWeakPostReleaseOnsetChainActive(),
                trace.trustedWeakPostReleaseOnsetChainFrameCount(),
                trace.trustedWeakPostReleaseOnsetChainStartMs(),
                trace.postReleaseWeakContinuityGapLimitMs(),
                trace.weakPostReleaseOnsetChainCandidate(),
                trace.trustedContinuityToneOnCandidate()
        ));
    }

    private static String formatDelta(Long deltaMs) {
        return deltaMs == null ? "-" : String.valueOf(deltaMs);
    }

    private static List<CommitSuppressionRule> buildCommitSuppressionRules() {
        ArrayList<CommitSuppressionRule> rules = new ArrayList<>();
        rules.add(new CommitSuppressionRule(
                "drop-unknown-all",
                match -> "unknown-after-trust".equals(match.decisionTrace().decision())
        ));
        rules.add(new CommitSuppressionRule(
                "drop-unknown-fallback-le20",
                match -> "unknown-after-trust".equals(match.decisionTrace().decision())
                        && match.fallbackToDecisionDeltaMs() != null
                        && match.fallbackToDecisionDeltaMs() <= 20L
        ));
        rules.add(new CommitSuppressionRule(
                "drop-learning-unlocked",
                match -> "front-end-learning".equals(match.decisionTrace().decision())
                        && !match.decisionTrace().targetToneLocked()
        ));
        rules.add(new CommitSuppressionRule(
                "drop-learning-unlocked-fallback-le250",
                match -> "front-end-learning".equals(match.decisionTrace().decision())
                        && !match.decisionTrace().targetToneLocked()
                        && match.fallbackToDecisionDeltaMs() != null
                        && match.fallbackToDecisionDeltaMs() <= 250L
        ));
        rules.add(new CommitSuppressionRule(
                "combo-unknown-fast-plus-learning-unlocked-fast",
                match -> ("unknown-after-trust".equals(match.decisionTrace().decision())
                        && match.fallbackToDecisionDeltaMs() != null
                        && match.fallbackToDecisionDeltaMs() <= 20L)
                        || ("front-end-learning".equals(match.decisionTrace().decision())
                        && !match.decisionTrace().targetToneLocked()
                        && match.fallbackToDecisionDeltaMs() != null
                        && match.fallbackToDecisionDeltaMs() <= 250L)
        ));
        rules.add(new CommitSuppressionRule(
                "combo-unknown-all-plus-learning-unlocked",
                match -> "unknown-after-trust".equals(match.decisionTrace().decision())
                        || ("front-end-learning".equals(match.decisionTrace().decision())
                        && !match.decisionTrace().targetToneLocked())
        ));
        return rules;
    }

    private static Path requireRecordingWav(String recordingLabel) throws Exception {
        String wavSuffix = recordingLabel.replace("录音 ", "");
        return LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith(wavSuffix + ".wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for " + recordingLabel));
    }

    private static boolean safeEquals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static LocalAudioDecodeTestSupport.StableDecisionTrace findDecisionWindow(
            List<LocalAudioDecodeTestSupport.StableDecisionTrace> traces,
            long timestampMs,
            String decision,
            long lookbackMs,
            long lookaheadMs
    ) {
        if (traces == null) {
            return null;
        }
        for (LocalAudioDecodeTestSupport.StableDecisionTrace trace : traces) {
            if (trace == null || !decision.equals(trace.decision())) {
                continue;
            }
            long windowStartMs = Math.max(0L, trace.timestampMs() - lookbackMs);
            long windowEndMs = trace.timestampMs() + lookaheadMs;
            if (timestampMs >= windowStartMs && timestampMs <= windowEndMs) {
                return trace;
            }
        }
        return null;
    }

    private static LocalAudioDecodeTestSupport.StableDecisionTrace firstNonPassDecisionAtOrAfter(
            List<LocalAudioDecodeTestSupport.StableDecisionTrace> traces,
            long timestampMs,
            long lookaheadMs
    ) {
        if (traces == null) {
            return null;
        }
        long deadlineMs = timestampMs + lookaheadMs;
        for (LocalAudioDecodeTestSupport.StableDecisionTrace trace : traces) {
            if (trace == null || trace.timestampMs() < timestampMs) {
                continue;
            }
            if (trace.timestampMs() > deadlineMs) {
                break;
            }
            if (!"pass".equals(trace.decision())) {
                return trace;
            }
        }
        return null;
    }

    private static void printFrontEndFrameWindow(
            List<LocalAudioDecodeTestSupport.FrameSignalTrace> traces,
            long startTimestampMs,
            long endTimestampMs
    ) {
        if (traces == null) {
            return;
        }
        for (LocalAudioDecodeTestSupport.FrameSignalTrace trace : traces) {
            if (trace == null || trace.snapshot() == null) {
                continue;
            }
            long timestampMs = trace.timestampMs();
            if (timestampMs < startTimestampMs || timestampMs > endTimestampMs) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "  F @%d act=%s lock=%s lck=%.2f near=%.2f unl=%.2f dom=%.2f iso=%.2f"
                            + " det=%.1f onThr=%d offThr=%d gap=%d/%d cont=%s/%d chain=%d"
                            + " bridge=%s/%d rescue=%s toneOn=%s byRescue=%s rescueTone=%s"
                            + " tail=%s tailApplied=%s weakTailCount=%d tailThr=%.1f",
                    timestampMs,
                    trace.snapshot().toneActive(),
                    trace.snapshot().targetToneLocked(),
                    trace.snapshot().recentLockedFrameRatio(),
                    trace.snapshot().recentNearTargetLockedFrameRatio(),
                    trace.snapshot().recentActiveUnlockedFrameRatio(),
                    trace.snapshot().toneDominanceRatio(),
                    trace.snapshot().narrowbandIsolationRatio(),
                    trace.detectionLevel(),
                    trace.toneOnThreshold(),
                    trace.toneActiveReleaseThreshold(),
                    trace.postReleaseGapMs(),
                    trace.postReleaseWindowMs(),
                    trace.postReleaseRescueContinuationWindowActive(),
                    trace.postReleaseRescueContinuationWindowRemainingMs(),
                    trace.postReleaseWeakContinuityRescueCount(),
                    trace.weakValleyBridgeActive(),
                    trace.weakValleyBridgeFramesRemaining(),
                    sanitize(trace.postReleaseRescueDecision()),
                    sanitize(trace.toneOnDecision()),
                    trace.toneOnAcceptedByRescue(),
                    trace.currentToneStartedByPostReleaseRescue(),
                    sanitize(trace.releaseTailHoldDecision()),
                    trace.releaseTailHoldApplied(),
                    trace.currentToneRunWeakBootstrapReleaseTailHoldCount(),
                    trace.releaseTailHoldRequiredDetectionThreshold()
            ));
        }
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

    private static String sanitize(String text) {
        if (text == null) {
            return "(empty)";
        }
        String normalized = text.replace('\u25A1', '?').trim();
        return normalized.isEmpty() ? "(empty)" : normalized;
    }

    private static final class FallbackAttackReopenProfile {
        private final long timestampMs;
        private final String outcomeBucket;
        private final LocalAudioDecodeTestSupport.StableDecisionTrace linkedDecision;
        private final String previousReleaseTailDecision;
        private final String postReleaseRescueDecision;
        private final long postReleaseGapMs;
        private final long postReleaseWindowMs;
        private final long gapSlackMs;
        private final double detectionLevel;
        private final int toneOnThreshold;
        private final double dominanceRatio;
        private final double isolationRatio;
        private final double localContrastRatio;
        private final double recentLockedRatio;
        private final double recentNearTargetLockedRatio;
        private final int postReleaseWeakContinuityRescueCount;
        private final boolean previousToneStartedByRescue;
        private final boolean previousLocked;
        private final boolean trackedToneMemoryActiveBeforeFrame;
        private final int attackAnchorFrequencyHzBeforeFrame;

        private FallbackAttackReopenProfile(
                long timestampMs,
                String outcomeBucket,
                LocalAudioDecodeTestSupport.StableDecisionTrace linkedDecision,
                String previousReleaseTailDecision,
                String postReleaseRescueDecision,
                long postReleaseGapMs,
                long postReleaseWindowMs,
                long gapSlackMs,
                double detectionLevel,
                int toneOnThreshold,
                double dominanceRatio,
                double isolationRatio,
                double localContrastRatio,
                double recentLockedRatio,
                double recentNearTargetLockedRatio,
                int postReleaseWeakContinuityRescueCount,
                boolean previousToneStartedByRescue,
                boolean previousLocked,
                boolean trackedToneMemoryActiveBeforeFrame,
                int attackAnchorFrequencyHzBeforeFrame
        ) {
            this.timestampMs = timestampMs;
            this.outcomeBucket = outcomeBucket;
            this.linkedDecision = linkedDecision;
            this.previousReleaseTailDecision = previousReleaseTailDecision;
            this.postReleaseRescueDecision = postReleaseRescueDecision;
            this.postReleaseGapMs = postReleaseGapMs;
            this.postReleaseWindowMs = postReleaseWindowMs;
            this.gapSlackMs = gapSlackMs;
            this.detectionLevel = detectionLevel;
            this.toneOnThreshold = toneOnThreshold;
            this.dominanceRatio = dominanceRatio;
            this.isolationRatio = isolationRatio;
            this.localContrastRatio = localContrastRatio;
            this.recentLockedRatio = recentLockedRatio;
            this.recentNearTargetLockedRatio = recentNearTargetLockedRatio;
            this.postReleaseWeakContinuityRescueCount = postReleaseWeakContinuityRescueCount;
            this.previousToneStartedByRescue = previousToneStartedByRescue;
            this.previousLocked = previousLocked;
            this.trackedToneMemoryActiveBeforeFrame = trackedToneMemoryActiveBeforeFrame;
            this.attackAnchorFrequencyHzBeforeFrame = attackAnchorFrequencyHzBeforeFrame;
        }

        long timestampMs() {
            return timestampMs;
        }

        String outcomeBucket() {
            return outcomeBucket;
        }

        LocalAudioDecodeTestSupport.StableDecisionTrace linkedDecision() {
            return linkedDecision;
        }

        String previousReleaseTailDecision() {
            return previousReleaseTailDecision;
        }

        String postReleaseRescueDecision() {
            return postReleaseRescueDecision;
        }

        long postReleaseGapMs() {
            return postReleaseGapMs;
        }

        long postReleaseWindowMs() {
            return postReleaseWindowMs;
        }

        long gapSlackMs() {
            return gapSlackMs;
        }

        double detectionLevel() {
            return detectionLevel;
        }

        int toneOnThreshold() {
            return toneOnThreshold;
        }

        double detectionToAttackRatio() {
            return detectionLevel / Math.max(1.0d, toneOnThreshold);
        }

        double dominanceRatio() {
            return dominanceRatio;
        }

        double isolationRatio() {
            return isolationRatio;
        }

        double localContrastRatio() {
            return localContrastRatio;
        }

        double recentLockedRatio() {
            return recentLockedRatio;
        }

        double recentNearTargetLockedRatio() {
            return recentNearTargetLockedRatio;
        }

        int postReleaseWeakContinuityRescueCount() {
            return postReleaseWeakContinuityRescueCount;
        }

        boolean previousToneStartedByRescue() {
            return previousToneStartedByRescue;
        }

        boolean previousLocked() {
            return previousLocked;
        }

        boolean trackedToneMemoryActiveBeforeFrame() {
            return trackedToneMemoryActiveBeforeFrame;
        }

        int attackAnchorFrequencyHzBeforeFrame() {
            return attackAnchorFrequencyHzBeforeFrame;
        }
    }

    private static final class LastFallbackAttackBeforeDecisionProfile {
        private final long fallbackTimestampMs;
        private final int fallbackCountInWindow;
        private final String previousReleaseTailDecision;
        private final String postReleaseRescueDecision;
        private final long postReleaseGapMs;
        private final long postReleaseWindowMs;
        private final long gapSlackMs;
        private final double detectionLevel;
        private final int toneOnThreshold;
        private final double dominanceRatio;
        private final double isolationRatio;
        private final double localContrastRatio;
        private final double recentLockedRatio;
        private final double recentNearTargetLockedRatio;
        private final int attackAnchorFrequencyHzBeforeFrame;

        private LastFallbackAttackBeforeDecisionProfile(
                long fallbackTimestampMs,
                int fallbackCountInWindow,
                String previousReleaseTailDecision,
                String postReleaseRescueDecision,
                long postReleaseGapMs,
                long postReleaseWindowMs,
                long gapSlackMs,
                double detectionLevel,
                int toneOnThreshold,
                double dominanceRatio,
                double isolationRatio,
                double localContrastRatio,
                double recentLockedRatio,
                double recentNearTargetLockedRatio,
                int attackAnchorFrequencyHzBeforeFrame
        ) {
            this.fallbackTimestampMs = fallbackTimestampMs;
            this.fallbackCountInWindow = fallbackCountInWindow;
            this.previousReleaseTailDecision = previousReleaseTailDecision;
            this.postReleaseRescueDecision = postReleaseRescueDecision;
            this.postReleaseGapMs = postReleaseGapMs;
            this.postReleaseWindowMs = postReleaseWindowMs;
            this.gapSlackMs = gapSlackMs;
            this.detectionLevel = detectionLevel;
            this.toneOnThreshold = toneOnThreshold;
            this.dominanceRatio = dominanceRatio;
            this.isolationRatio = isolationRatio;
            this.localContrastRatio = localContrastRatio;
            this.recentLockedRatio = recentLockedRatio;
            this.recentNearTargetLockedRatio = recentNearTargetLockedRatio;
            this.attackAnchorFrequencyHzBeforeFrame = attackAnchorFrequencyHzBeforeFrame;
        }

        long fallbackTimestampMs() {
            return fallbackTimestampMs;
        }

        int fallbackCountInWindow() {
            return fallbackCountInWindow;
        }

        String previousReleaseTailDecision() {
            return previousReleaseTailDecision;
        }

        String postReleaseRescueDecision() {
            return postReleaseRescueDecision;
        }

        long postReleaseGapMs() {
            return postReleaseGapMs;
        }

        long postReleaseWindowMs() {
            return postReleaseWindowMs;
        }

        long gapSlackMs() {
            return gapSlackMs;
        }

        double detectionLevel() {
            return detectionLevel;
        }

        int toneOnThreshold() {
            return toneOnThreshold;
        }

        double detectionToAttackRatio() {
            return detectionLevel / Math.max(1.0d, toneOnThreshold);
        }

        double dominanceRatio() {
            return dominanceRatio;
        }

        double isolationRatio() {
            return isolationRatio;
        }

        double localContrastRatio() {
            return localContrastRatio;
        }

        double recentLockedRatio() {
            return recentLockedRatio;
        }

        double recentNearTargetLockedRatio() {
            return recentNearTargetLockedRatio;
        }

        int attackAnchorFrequencyHzBeforeFrame() {
            return attackAnchorFrequencyHzBeforeFrame;
        }
    }

    private static final class DecisionEventChainProfile {
        private final long decisionTimestampMs;
        private final LocalAudioDecodeTestSupport.FrameSignalTrace lastAcceptedRescueReopen;
        private final LocalAudioDecodeTestSupport.FrameSignalTrace lastTailBlock;
        private final LocalAudioDecodeTestSupport.FrameSignalTrace lastFallbackAttack;

        private DecisionEventChainProfile(
                long decisionTimestampMs,
                LocalAudioDecodeTestSupport.FrameSignalTrace lastAcceptedRescueReopen,
                LocalAudioDecodeTestSupport.FrameSignalTrace lastTailBlock,
                LocalAudioDecodeTestSupport.FrameSignalTrace lastFallbackAttack
        ) {
            this.decisionTimestampMs = decisionTimestampMs;
            this.lastAcceptedRescueReopen = lastAcceptedRescueReopen;
            this.lastTailBlock = lastTailBlock;
            this.lastFallbackAttack = lastFallbackAttack;
        }

        LocalAudioDecodeTestSupport.FrameSignalTrace lastAcceptedRescueReopen() {
            return lastAcceptedRescueReopen;
        }

        LocalAudioDecodeTestSupport.FrameSignalTrace lastTailBlock() {
            return lastTailBlock;
        }

        LocalAudioDecodeTestSupport.FrameSignalTrace lastFallbackAttack() {
            return lastFallbackAttack;
        }

        Long rescueToTailDeltaMs() {
            return delta(lastAcceptedRescueReopen, lastTailBlock);
        }

        Long tailToFallbackDeltaMs() {
            return delta(lastTailBlock, lastFallbackAttack);
        }

        Long fallbackToDecisionDeltaMs() {
            return lastFallbackAttack == null || decisionTimestampMs < 0L
                    ? null
                    : decisionTimestampMs - lastFallbackAttack.timestampMs();
        }

        String bucketKey() {
            String rescueKey = lastAcceptedRescueReopen == null
                    ? "none"
                    : sanitize(lastAcceptedRescueReopen.postReleaseRescueDecision());
            String tailKey = lastTailBlock == null
                    ? "none"
                    : sanitize(lastTailBlock.releaseTailHoldDecision());
            String fallbackKey = lastFallbackAttack == null
                    ? "none"
                    : sanitize(lastFallbackAttack.postReleaseRescueDecision())
                    + " / "
                    + sanitize(lastFallbackAttack.toneOnDecision());
            return "rescue=" + rescueKey
                    + " | tail=" + tailKey
                    + " | fallback=" + fallbackKey;
        }

        private static Long delta(
                LocalAudioDecodeTestSupport.FrameSignalTrace earlier,
                LocalAudioDecodeTestSupport.FrameSignalTrace later
        ) {
            if (earlier == null || later == null) {
                return null;
            }
            return later.timestampMs() - earlier.timestampMs();
        }
    }

    private static final class RecordingExpectation {
        private final String recordingLabel;
        private final String expectedText;

        private RecordingExpectation(String recordingLabel, String expectedText) {
            this.recordingLabel = recordingLabel;
            this.expectedText = expectedText;
        }

        String recordingLabel() {
            return recordingLabel;
        }

        String expectedText() {
            return expectedText;
        }
    }

    private static final class CommitSuppressionRule {
        private final String name;
        private final Predicate<MatchedCommittedDecision> predicate;

        private CommitSuppressionRule(
                String name,
                Predicate<MatchedCommittedDecision> predicate
        ) {
            this.name = name;
            this.predicate = predicate;
        }

        String name() {
            return name;
        }

        Predicate<MatchedCommittedDecision> predicate() {
            return predicate;
        }
    }

    private static final class MatchedCommittedDecision {
        private final int decodeEventIndex;
        private final CwDecodeEvent decodeEvent;
        private final LocalAudioDecodeTestSupport.StableDecisionTrace decisionTrace;
        private final DecisionEventChainProfile chainProfile;

        private MatchedCommittedDecision(
                int decodeEventIndex,
                CwDecodeEvent decodeEvent,
                LocalAudioDecodeTestSupport.StableDecisionTrace decisionTrace,
                DecisionEventChainProfile chainProfile
        ) {
            this.decodeEventIndex = decodeEventIndex;
            this.decodeEvent = decodeEvent;
            this.decisionTrace = decisionTrace;
            this.chainProfile = chainProfile;
        }

        int decodeEventIndex() {
            return decodeEventIndex;
        }

        CwDecodeEvent decodeEvent() {
            return decodeEvent;
        }

        LocalAudioDecodeTestSupport.StableDecisionTrace decisionTrace() {
            return decisionTrace;
        }

        Long fallbackToDecisionDeltaMs() {
            return chainProfile == null ? null : chainProfile.fallbackToDecisionDeltaMs();
        }
    }

    private static final class SimulatedCommitResult {
        private final String outputText;
        private final int suppressedCharacterCount;
        private final Map<String, Integer> suppressedDecisionCounts;

        private SimulatedCommitResult(
                String outputText,
                int suppressedCharacterCount,
                Map<String, Integer> suppressedDecisionCounts
        ) {
            this.outputText = outputText;
            this.suppressedCharacterCount = suppressedCharacterCount;
            this.suppressedDecisionCounts = suppressedDecisionCounts;
        }

        String outputText() {
            return outputText;
        }

        int suppressedCharacterCount() {
            return suppressedCharacterCount;
        }

        Map<String, Integer> suppressedDecisionCounts() {
            return suppressedDecisionCounts;
        }
    }
}
