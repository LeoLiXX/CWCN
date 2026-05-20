package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.decoder.CwDecoder;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.qso.QsoStateMachine;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CwLocalAudioShortDitSuppressionPrototypeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;
    private static final int RECORDING12_SQL_PERCENT = 15;
    private static final int OVERLONG_SEQUENCE_MIN_SYMBOLS = 6;
    private static final int TAIL_SEQUENCE_MIN_SYMBOLS = 5;
    private static final double SHORT_DIT_RATIO_MAX = 0.45d;
    private static final double TAIL_GAP_PROMOTION_MAX_RATIO = 0.42d;
    private static final double TAIL_GAP_PROMOTION_LAST_TONE_MAX_RATIO = 0.12d;
    private static final double TAIL_TONE_TRIM_MAX_RATIO = 0.12d;
    private static final int TAIL_DIT_REPAIR_MIN_TONES = 4;
    private static final int TAIL_DIT_REPAIR_MAX_TONES = 5;
    private static final double TAIL_DIT_REPAIR_LAST_TONE_MAX_RATIO = 0.72d;
    private static final double TAIL_DIT_REPAIR_LAST_GAP_MAX_RATIO = 0.45d;
    private static final double TAIL_DIT_REPAIR_PRIOR_DIT_MAX_RATIO = 0.72d;
    private static final String RECORDING12_EXPECTED_TEXT = "CQ CQ CQ DE BI9CMS BI9CMS IN 700 PSE K.";
    private static final String CAPTURE_EXPECTED_TEXT =
            "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 700 24WPM PSE K."
                    + "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 700 24WPM PSE K."
                    + "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 700 24WPM PSE K.";
    private static final String LONG_QSO_EXPECTED_TEXT =
            "CQ CQ DE BG1XXX K BG1XXX DE JA1ABC K JA1ABC DE BG1XXX TNX FER CALL UR RST 599 "
                    + "QTH BEIJING NAME LEO HW? K BG1XXX DE JA1ABC FB OM UR RST 579 QTH TOKYO "
                    + "NAME KEN BK TNX QSO 73 DE BG1XXX SK";
    private static final String RECORDING2_EXPECTED_TEXT =
            "CQ DX CQ DX DE JV3VV JV3VV PAGE K. CQ DX CQ DX DE JV3VV JV3VV PAGE K.";
    private static final String RECORDING3_EXPECTED_TEXT =
            "BI9CMS BI9CMS BI9CMS DE BI9CLT BI8DLT BI9CLT UR 599 5NN BK.";
    private static final String RECORDING7_EXPECTED_TEXT = "QRZ? DE BI3TUK KN.";
    private static final String RECORDING13_EXPECTED_TEXT = "CQ CQ CQ DE BI9CLT BI9CLT BI9CLT IN 600 PSE K.";
    private static final String RECORDING14_EXPECTED_TEXT = "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 800 PSE K.";
    private static final String RECORDING15_EXPECTED_TEXT = "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 800 24WPM PSE K.";
    private static final String RECORDING16_EXPECTED_TEXT = "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 700 24WPM PSE K.";
    private static final long RECORDING12_SHORT_DIT_WINDOW_BEFORE_MS = 520L;
    private static final long RECORDING12_SHORT_DIT_WINDOW_AFTER_MS = 160L;
    private static final long RECORDING12_CALLSIGN_UNKNOWN_WINDOW_BEFORE_MS = 920L;
    private static final long RECORDING12_CALLSIGN_UNKNOWN_WINDOW_AFTER_MS = 96L;
    private static final int MAX_LOCAL_SHORT_DIT_CANDIDATES = 5;
    private static final int MAX_LOCAL_GAP_PROMOTION_CANDIDATES = 4;

    @Test
    public void printRecording8ShortDitSuppressionPrototypeComparison() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().contains("(8)"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for recording (8)"));
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                        wavFile,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        LinkedHashMap<String, LocalAudioDecodeTestSupport.ForcedToneReplayResult> replays = new LinkedHashMap<>();
        replays.put("TRK", LocalAudioDecodeTestSupport.replayForcedTrackedToneDecode(detailed));
        replays.put("EFF", LocalAudioDecodeTestSupport.replayForcedEffectiveTrackedToneDecode(detailed));
        replays.put("HYP", LocalAudioDecodeTestSupport.replayForcedHypothesisToneDecode(detailed));

        for (Map.Entry<String, LocalAudioDecodeTestSupport.ForcedToneReplayResult> entry : replays.entrySet()) {
            ShortDitSuppressionResult prototype = runShortDitSuppressionPrototype(entry.getValue());
            System.out.println("==== " + entry.getValue().sourceLabel() + " " + entry.getKey() + " short-dit ====");
            System.out.println("base=" + entry.getValue().decodedText());
            System.out.println("soft=" + prototype.decodedText);
            System.out.println("drops=" + prototype.renderDropSummary());
        }
    }

    @Test
    public void printRecording12LocalShortDitCombinationProbe() throws Exception {
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                decodeDetailedCase("recording(12)", RECORDING12_SQL_PERCENT);
        long trustTimestampMs = firstTrustedTimestampMs(detailed);
        CwDecodeEvent liveTarget = findFirstPostTrustMismatchCharacterEvent(
                detailed.decodeEvents(),
                trustTimestampMs,
                RECORDING12_EXPECTED_TEXT
        );
        if (liveTarget == null) {
            throw new IllegalStateException("Missing recording(12) post-trust mismatch target");
        }

        LinkedHashMap<String, LocalAudioDecodeTestSupport.ForcedToneReplayResult> replays = new LinkedHashMap<>();
        replays.put("TRK", LocalAudioDecodeTestSupport.replayForcedTrackedToneDecode(detailed));
        replays.put("EFF", LocalAudioDecodeTestSupport.replayForcedEffectiveTrackedToneDecode(detailed));
        replays.put("HYP", LocalAudioDecodeTestSupport.replayForcedHypothesisToneDecode(detailed));

        System.out.println(String.format(
                Locale.US,
                "recording(12) live target @%d emit=%s seq=%s final=%s",
                liveTarget.timestampMs(),
                compact(liveTarget.emittedValue()),
                compact(liveTarget.sourceSequence()),
                sanitize(detailed.probeResult().decodedText())
        ));

        List<ShortDitDropDecision> liveCandidates = collectLocalShortDitCandidates(
                detailed.timingEvents(),
                liveTarget.timestampMs() - RECORDING12_SHORT_DIT_WINDOW_BEFORE_MS,
                liveTarget.timestampMs() + RECORDING12_SHORT_DIT_WINDOW_AFTER_MS
        );
        if (liveCandidates.size() > MAX_LOCAL_SHORT_DIT_CANDIDATES) {
            liveCandidates = new ArrayList<>(liveCandidates.subList(0, MAX_LOCAL_SHORT_DIT_CANDIDATES));
        }

        System.out.println("==== LIVE recording12-local-short-dit ====");
        System.out.println(String.format(
                Locale.US,
                "base recall=%.3f text=%s target=%s",
                computeRecall(RECORDING12_EXPECTED_TEXT, detailed.probeResult().decodedText()),
                sanitize(detailed.probeResult().decodedText()),
                renderCharacterEvent(liveTarget)
        ));
        System.out.println("candidates=" + renderDropList(liveCandidates));

        List<List<ShortDitDropDecision>> liveCombinations = enumerateNonEmptyDropCombinations(liveCandidates);
        if (liveCombinations.isEmpty()) {
            System.out.println("variants=none");
        } else {
            for (List<ShortDitDropDecision> combination : liveCombinations) {
                ShortDitSuppressionResult variant = runShortDitSuppressionPrototype(
                        detailed.toneEvents(),
                        detailed.flushTimestampMs(),
                        combination
                );
                CwDecodeEvent variantTarget = findCharacterEventNearTimestamp(
                        variant.decodeEvents,
                        liveTarget.timestampMs()
                );
                System.out.println(String.format(
                        Locale.US,
                        "variant recall=%.3f drops=%s target=%s text=%s",
                        computeRecall(RECORDING12_EXPECTED_TEXT, variant.decodedText),
                        renderDropList(combination),
                        renderCharacterEvent(variantTarget),
                        sanitize(variant.decodedText)
                ));
            }
        }

        for (Map.Entry<String, LocalAudioDecodeTestSupport.ForcedToneReplayResult> entry : replays.entrySet()) {
            LocalAudioDecodeTestSupport.ForcedToneReplayResult replay = entry.getValue();
            System.out.println("==== " + replay.sourceLabel() + " " + entry.getKey() + " recording12-local-short-dit ====");
            System.out.println(String.format(
                    Locale.US,
                    "base recall=%.3f text=%s target=%s",
                    computeRecall(RECORDING12_EXPECTED_TEXT, replay.decodedText()),
                    sanitize(replay.decodedText()),
                    renderCharacterEvent(findCharacterEventNearTimestamp(replay.decodeEvents(), liveTarget.timestampMs()))
            ));
            System.out.println("note=forced replay drifts away from live target window; kept only as coarse reference");
        }
    }

    @Test
    public void printRecording12LocalGapPromotionProbe() throws Exception {
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                decodeDetailedCase("recording(12)", RECORDING12_SQL_PERCENT);
        long trustTimestampMs = firstTrustedTimestampMs(detailed);
        CwDecodeEvent liveTarget = findFirstPostTrustMismatchCharacterEvent(
                detailed.decodeEvents(),
                trustTimestampMs,
                RECORDING12_EXPECTED_TEXT
        );
        if (liveTarget == null) {
            throw new IllegalStateException("Missing recording(12) post-trust mismatch target");
        }

        CharacterTimingDetail liveTargetDetail = buildCharacterTimingDetail(detailed.timingEvents(), liveTarget);
        List<GapPromotionDecision> candidates = collectLocalGapPromotionCandidates(liveTargetDetail);
        if (candidates.size() > MAX_LOCAL_GAP_PROMOTION_CANDIDATES) {
            candidates = new ArrayList<>(candidates.subList(candidates.size() - MAX_LOCAL_GAP_PROMOTION_CANDIDATES, candidates.size()));
        }

        System.out.println(String.format(
                Locale.US,
                "recording(12) gap-promotion target @%d emit=%s seq=%s final=%s",
                liveTarget.timestampMs(),
                compact(liveTarget.emittedValue()),
                compact(liveTarget.sourceSequence()),
                sanitize(detailed.probeResult().decodedText())
        ));
        System.out.println("==== LIVE recording12-local-gap-promotion ====");
        System.out.println(String.format(
                Locale.US,
                "base recall=%.3f text=%s target=%s",
                computeRecall(RECORDING12_EXPECTED_TEXT, detailed.probeResult().decodedText()),
                sanitize(detailed.probeResult().decodedText()),
                renderCharacterEvent(liveTarget)
        ));
        System.out.println("candidates=" + renderGapPromotionList(candidates));

        List<List<GapPromotionDecision>> combinations = enumerateNonEmptyGapPromotionCombinations(candidates);
        if (combinations.isEmpty()) {
            System.out.println("variants=none");
            return;
        }

        for (List<GapPromotionDecision> combination : combinations) {
            TimingRewriteResult variant = runGapPromotionPrototype(
                    detailed.timingEvents(),
                    detailed.flushTimestampMs(),
                    combination
            );
            CwDecodeEvent variantTarget = findCharacterEventNearTimestamp(variant.decodeEvents, liveTarget.timestampMs());
            System.out.println(String.format(
                    Locale.US,
                    "variant recall=%.3f promotions=%s target=%s text=%s",
                    computeRecall(RECORDING12_EXPECTED_TEXT, variant.decodedText),
                    renderGapPromotionList(combination),
                    renderCharacterEvent(variantTarget),
                    sanitize(variant.decodedText)
            ));
        }
    }

    @Test
    public void printTailGapPromotionHeuristicSafetySweep() throws Exception {
        List<PrototypeCase> cases = new ArrayList<>();
        cases.add(new PrototypeCase("capture.wav", CAPTURE_EXPECTED_TEXT));
        cases.add(new PrototypeCase("recording(12)", RECORDING12_EXPECTED_TEXT));
        cases.add(new PrototypeCase("recording(13)", RECORDING13_EXPECTED_TEXT));
        cases.add(new PrototypeCase("recording(14)", RECORDING14_EXPECTED_TEXT));
        cases.add(new PrototypeCase("recording(15)", RECORDING15_EXPECTED_TEXT));
        cases.add(new PrototypeCase("recording(16)", RECORDING16_EXPECTED_TEXT));
        cases.add(new PrototypeCase("recording(2)", RECORDING2_EXPECTED_TEXT));
        cases.add(new PrototypeCase("recording(3)", RECORDING3_EXPECTED_TEXT));
        cases.add(new PrototypeCase("recording(7)", RECORDING7_EXPECTED_TEXT));
        cases.add(new PrototypeCase("recording(8)", LONG_QSO_EXPECTED_TEXT));

        System.out.println("==== tail-gap-promotion heuristic safety sweep ====");
        for (PrototypeCase testCase : cases) {
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed = decodeDetailedCase(testCase.alias);
            TimingRewriteResult replayBaseline = runGapPromotionPrototype(
                    detailed.timingEvents(),
                    detailed.flushTimestampMs(),
                    new ArrayList<>()
            );
            List<GapPromotionDecision> promotions = chooseTailGapPromotions(detailed);
            TimingRewriteResult variant = runGapPromotionPrototype(
                    detailed.timingEvents(),
                    detailed.flushTimestampMs(),
                    promotions
            );
            double liveRecall = computeRecall(testCase.expectedText, detailed.probeResult().decodedText());
            double baseRecall = computeRecall(testCase.expectedText, replayBaseline.decodedText);
            double variantRecall = computeRecall(testCase.expectedText, variant.decodedText);
            System.out.println(String.format(
                    Locale.US,
                    "%s live=%.3f replayBase=%.3f variant=%.3f delta=%+.3f promotions=%d changed=%s",
                    testCase.alias,
                    liveRecall,
                    baseRecall,
                    variantRecall,
                    variantRecall - baseRecall,
                    promotions.size(),
                    yesNo(!canonicalize(replayBaseline.decodedText).equals(canonicalize(variant.decodedText)))
            ));
            if (!promotions.isEmpty()) {
                System.out.println("  promotions=" + renderGapPromotionList(promotions));
            }
            if (!canonicalize(replayBaseline.decodedText).equals(canonicalize(variant.decodedText()))) {
                System.out.println("  live=" + sanitize(detailed.probeResult().decodedText()));
                System.out.println("  base=" + sanitize(replayBaseline.decodedText));
                System.out.println("  var =" + sanitize(variant.decodedText));
            }
        }
    }

    @Test
    public void printTailGapPromotionTriggerScanAllCases() throws Exception {
        System.out.println("==== tail-gap-promotion trigger scan ====");

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult captureDetailed = decodeDetailedCase("capture.wav");
        printTriggerScanCase("capture.wav", captureDetailed);

        for (Path wavFile : LocalAudioDecodeTestSupport.listConvertedWavFiles()) {
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                    LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                            wavFile,
                            PREFERRED_TONE_HZ,
                            SEED_WPM,
                            SQL_PERCENT,
                            false,
                            CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                    );
            printTriggerScanCase(wavFile.getFileName().toString(), detailed);
        }
    }

    @Test
    public void printTailDitRepairTriggerScanAllCases() throws Exception {
        System.out.println("==== tail-dit-repair trigger scan ====");

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult captureDetailed = decodeDetailedCase("capture.wav");
        printTailDitRepairScanCase("capture.wav", captureDetailed);

        for (Path wavFile : LocalAudioDecodeTestSupport.listConvertedWavFiles()) {
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                    LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                            wavFile,
                            PREFERRED_TONE_HZ,
                            SEED_WPM,
                            SQL_PERCENT,
                            false,
                            CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                    );
            printTailDitRepairScanCase(wavFile.getFileName().toString(), detailed);
        }
    }

    @Test
    public void printRecording12TailToneTrimProbe() throws Exception {
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                decodeDetailedCase("recording(12)", RECORDING12_SQL_PERCENT);
        List<TailToneTrimDecision> trims = chooseTailToneTrims(detailed);
        TimingRewriteResult variant = runTailToneTrimPrototype(
                detailed.timingEvents(),
                detailed.flushTimestampMs(),
                trims
        );
        System.out.println("==== recording12 tail-tone-trim ====");
        System.out.println(String.format(
                Locale.US,
                "base recall=%.3f text=%s",
                computeRecall(RECORDING12_EXPECTED_TEXT, detailed.probeResult().decodedText()),
                sanitize(detailed.probeResult().decodedText())
        ));
        System.out.println("trims=" + renderTailToneTrimList(trims));
        System.out.println(String.format(
                Locale.US,
                "variant recall=%.3f text=%s",
                computeRecall(RECORDING12_EXPECTED_TEXT, variant.decodedText),
                sanitize(variant.decodedText)
        ));
    }

    @Test
    public void printRecording7OpeningTailDitSweep() throws Exception {
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed = decodeDetailedCase("recording(7)");
        List<RawTimingCharacterCandidate> candidates = buildRawTimingCharacterCandidates(detailed.timingEvents());

        RawTimingCharacterCandidate qLike = null;
        RawTimingCharacterCandidate rLike = null;
        for (RawTimingCharacterCandidate candidate : candidates) {
            if (candidate.boundaryTimestampMs == 6389L && "--.-.".equals(candidate.sequence)) {
                qLike = candidate;
            } else if (candidate.boundaryTimestampMs == 6988L && ".-..".equals(candidate.sequence)) {
                rLike = candidate;
            }
        }
        if (qLike == null || rLike == null) {
            throw new IllegalStateException("Missing recording(7) opening tail-dit candidates");
        }

        ShortDitDropDecision dropQTail = buildTailToneDropDecision(qLike, 1);
        ShortDitDropDecision dropRTail = buildTailToneDropDecision(rLike, 2);

        ShortDitSuppressionResult dropQOnly = runShortDitSuppressionPrototype(
                detailed.toneEvents(),
                detailed.flushTimestampMs(),
                java.util.Collections.singletonList(dropQTail)
        );
        ShortDitSuppressionResult dropROnly = runShortDitSuppressionPrototype(
                detailed.toneEvents(),
                detailed.flushTimestampMs(),
                java.util.Collections.singletonList(dropRTail)
        );
        ArrayList<ShortDitDropDecision> bothDrops = new ArrayList<>();
        bothDrops.add(dropQTail);
        bothDrops.add(dropRTail);
        ShortDitSuppressionResult dropBoth = runShortDitSuppressionPrototype(
                detailed.toneEvents(),
                detailed.flushTimestampMs(),
                bothDrops
        );

        TimingRewriteResult trimQOnly = runTailToneTrimPrototype(
                detailed.timingEvents(),
                detailed.flushTimestampMs(),
                java.util.Collections.singletonList(buildTailToneTrimDecision(qLike))
        );
        TimingRewriteResult trimROnly = runTailToneTrimPrototype(
                detailed.timingEvents(),
                detailed.flushTimestampMs(),
                java.util.Collections.singletonList(buildTailToneTrimDecision(rLike))
        );
        ArrayList<TailToneTrimDecision> bothTrims = new ArrayList<>();
        bothTrims.add(buildTailToneTrimDecision(qLike));
        bothTrims.add(buildTailToneTrimDecision(rLike));
        TimingRewriteResult trimBoth = runTailToneTrimPrototype(
                detailed.timingEvents(),
                detailed.flushTimestampMs(),
                bothTrims
        );

        System.out.println("==== recording7 opening tail-dit sweep ====");
        System.out.println(String.format(
                Locale.US,
                "base recall=%.3f text=%s",
                computeRecall(RECORDING7_EXPECTED_TEXT, detailed.probeResult().decodedText()),
                sanitize(detailed.probeResult().decodedText())
        ));
        System.out.println(String.format(
                Locale.US,
                "qLike boundary=@%d seq=%s tail=%s",
                qLike.boundaryTimestampMs,
                qLike.sequence,
                dropQTail.render()
        ));
        System.out.println(String.format(
                Locale.US,
                "rLike boundary=@%d seq=%s tail=%s",
                rLike.boundaryTimestampMs,
                rLike.sequence,
                dropRTail.render()
        ));
        System.out.println(String.format(
                Locale.US,
                "drop-q recall=%.3f text=%s",
                computeRecall(RECORDING7_EXPECTED_TEXT, dropQOnly.decodedText),
                sanitize(dropQOnly.decodedText)
        ));
        System.out.println(String.format(
                Locale.US,
                "drop-r recall=%.3f text=%s",
                computeRecall(RECORDING7_EXPECTED_TEXT, dropROnly.decodedText),
                sanitize(dropROnly.decodedText)
        ));
        System.out.println(String.format(
                Locale.US,
                "drop-both recall=%.3f text=%s",
                computeRecall(RECORDING7_EXPECTED_TEXT, dropBoth.decodedText),
                sanitize(dropBoth.decodedText)
        ));
        System.out.println(String.format(
                Locale.US,
                "trim-q recall=%.3f text=%s",
                computeRecall(RECORDING7_EXPECTED_TEXT, trimQOnly.decodedText),
                sanitize(trimQOnly.decodedText)
        ));
        System.out.println(String.format(
                Locale.US,
                "trim-r recall=%.3f text=%s",
                computeRecall(RECORDING7_EXPECTED_TEXT, trimROnly.decodedText),
                sanitize(trimROnly.decodedText)
        ));
        System.out.println(String.format(
                Locale.US,
                "trim-both recall=%.3f text=%s",
                computeRecall(RECORDING7_EXPECTED_TEXT, trimBoth.decodedText),
                sanitize(trimBoth.decodedText)
        ));
    }

    @Test
    public void printRecording12SecondMismatchToneOmissionSweep() throws Exception {
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                decodeDetailedCase("recording(12)", RECORDING12_SQL_PERCENT);
        List<RawTimingCharacterCandidate> candidates = buildRawTimingCharacterCandidates(detailed.timingEvents());
        RawTimingCharacterCandidate target = null;
        for (RawTimingCharacterCandidate candidate : candidates) {
            if (candidate.boundaryTimestampMs == 10845L && "--...".equals(candidate.sequence)) {
                target = candidate;
                break;
            }
        }
        if (target == null) {
            throw new IllegalStateException("Missing recording(12) second mismatch candidate");
        }

        System.out.println("==== recording12 second-mismatch tone omission sweep ====");
        System.out.println(String.format(
                Locale.US,
                "base recall=%.3f seq=%s text=%s",
                computeRecall(RECORDING12_EXPECTED_TEXT, detailed.probeResult().decodedText()),
                target.sequence,
                sanitize(detailed.probeResult().decodedText())
        ));

        for (int index = 0; index < target.toneEvents.size(); index++) {
            CwTimingEvent tone = target.toneEvents.get(index);
            TimingRewriteResult variant = runTailToneTrimPrototype(
                    detailed.timingEvents(),
                    detailed.flushTimestampMs(),
                    java.util.Collections.singletonList(new TailToneTrimDecision(
                            tone.timestampMs(),
                            tone.durationMs(),
                            tone.dotEstimateMs(),
                            tone.ratioToDotEstimate(),
                            index < target.intraGapEvents.size()
                                    ? target.intraGapEvents.get(index).timestampMs()
                                    : target.intraGapEvents.get(target.intraGapEvents.size() - 1).timestampMs(),
                            omitSequenceChar(target.sequence, index),
                            target.sequence
                    ))
            );
            System.out.println(String.format(
                    Locale.US,
                    "omit t%d @%d %.0fms -> recall=%.3f text=%s",
                    index + 1,
                    tone.timestampMs(),
                    (double) tone.durationMs(),
                    computeRecall(RECORDING12_EXPECTED_TEXT, variant.decodedText),
                    sanitize(variant.decodedText)
                ));
        }
    }

    @Test
    public void printRecording12In700ClusterToneOmissionSweep() throws Exception {
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                decodeDetailedCase("recording(12)", RECORDING12_SQL_PERCENT);
        List<RawTimingCharacterCandidate> candidates = buildRawTimingCharacterCandidates(detailed.timingEvents());
        RawTimingCharacterCandidate bLike = null;
        for (RawTimingCharacterCandidate candidate : candidates) {
            if (candidate.boundaryTimestampMs == 20712L && "-...".equals(candidate.sequence)) {
                bLike = candidate;
            }
        }
        if (bLike == null) {
            throw new IllegalStateException("Missing recording(12) B-like cluster candidate");
        }

        System.out.println("==== recording12 IN700-cluster tone omission sweep ====");
        System.out.println(String.format(
                Locale.US,
                "base recall=%.3f text=%s",
                computeRecall(RECORDING12_EXPECTED_TEXT, detailed.probeResult().decodedText()),
                sanitize(detailed.probeResult().decodedText())
        ));
        System.out.println("candidate-window:");
        for (RawTimingCharacterCandidate candidate : candidates) {
            if (candidate.boundaryTimestampMs < 20000L || candidate.boundaryTimestampMs > 26000L) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "  boundary=%d seq=%s tones=%d",
                    candidate.boundaryTimestampMs,
                    candidate.sequence,
                    candidate.toneEvents.size()
            ));
        }
        printToneOmissionSweepForCandidate("b-like", bLike, detailed);
    }

    @Test
    public void printRecording12UnknownClusterGapPromotionSweep() throws Exception {
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                decodeDetailedCase("recording(12)", RECORDING12_SQL_PERCENT);
        List<RawTimingCharacterCandidate> candidates = buildRawTimingCharacterCandidates(detailed.timingEvents());
        RawTimingCharacterCandidate target = null;
        for (RawTimingCharacterCandidate candidate : candidates) {
            if (candidate.boundaryTimestampMs == 24044L && "...----..-.-..--".equals(candidate.sequence)) {
                target = candidate;
                break;
            }
        }
        if (target == null) {
            throw new IllegalStateException("Missing recording(12) unknown-cluster candidate");
        }

        System.out.println("==== recording12 unknown-cluster gap-promotion sweep ====");
        System.out.println(String.format(
                Locale.US,
                "base recall=%.3f boundary=%d seq=%s text=%s",
                computeRecall(RECORDING12_EXPECTED_TEXT, detailed.probeResult().decodedText()),
                target.boundaryTimestampMs,
                target.sequence,
                sanitize(detailed.probeResult().decodedText())
        ));
        System.out.println("internal-gaps:");
        for (int index = 0; index < target.intraGapEvents.size(); index++) {
            CwTimingEvent gap = target.intraGapEvents.get(index);
            System.out.println(String.format(
                    Locale.US,
                    "  g%d @%d dur=%d ratioIntra=%.2f ratioDot=%.2f",
                    index + 1,
                    gap.timestampMs(),
                    gap.durationMs(),
                    gap.ratioToIntraGapEstimate(),
                    gap.ratioToDotEstimate()
            ));
        }

        for (int index = 0; index < target.intraGapEvents.size(); index++) {
            CwTimingEvent gap = target.intraGapEvents.get(index);
            GapPromotionDecision promotion = new GapPromotionDecision(
                    gap.timestampMs(),
                    gap.durationMs(),
                    gap.dotEstimateMs(),
                    gap.intraGapEstimateMs(),
                    gap.ratioToIntraGapEstimate(),
                    index + 1
            );
            TimingRewriteResult variant = runGapPromotionPrototype(
                    detailed.timingEvents(),
                    detailed.flushTimestampMs(),
                    java.util.Collections.singletonList(promotion)
            );
            CwDecodeEvent nearest = findCharacterEventNearTimestamp(variant.decodeEvents, target.boundaryTimestampMs);
            System.out.println(String.format(
                    Locale.US,
                    "promote g%d @%d %.0fms/%.2fintra -> recall=%.3f nearest=%s text=%s",
                    index + 1,
                    gap.timestampMs(),
                    (double) gap.durationMs(),
                    gap.ratioToIntraGapEstimate(),
                    computeRecall(RECORDING12_EXPECTED_TEXT, variant.decodedText),
                    renderCharacterEvent(nearest),
                sanitize(variant.decodedText)
            ));
        }
    }

    @Test
    public void printRecording12UnknownClusterTopGapCombinationSweep() throws Exception {
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                decodeDetailedCase("recording(12)", RECORDING12_SQL_PERCENT);
        List<RawTimingCharacterCandidate> candidates = buildRawTimingCharacterCandidates(detailed.timingEvents());
        RawTimingCharacterCandidate target = null;
        for (RawTimingCharacterCandidate candidate : candidates) {
            if (candidate.boundaryTimestampMs == 24044L && "...----..-.-..--".equals(candidate.sequence)) {
                target = candidate;
                break;
            }
        }
        if (target == null) {
            throw new IllegalStateException("Missing recording(12) unknown-cluster candidate");
        }

        List<GapPromotionDecision> longest = new ArrayList<>();
        int[] indices = new int[] {2, 8, 13};
        for (int rawIndex : indices) {
            CwTimingEvent gap = target.intraGapEvents.get(rawIndex);
            longest.add(new GapPromotionDecision(
                    gap.timestampMs(),
                    gap.durationMs(),
                    gap.dotEstimateMs(),
                    gap.intraGapEstimateMs(),
                    gap.ratioToIntraGapEstimate(),
                    rawIndex + 1
            ));
        }

        System.out.println("==== recording12 unknown-cluster top-gap combination sweep ====");
        System.out.println(String.format(
                Locale.US,
                "base recall=%.3f text=%s",
                computeRecall(RECORDING12_EXPECTED_TEXT, detailed.probeResult().decodedText()),
                sanitize(detailed.probeResult().decodedText())
        ));
        System.out.println("top-gaps=" + renderGapPromotionList(longest));

        int maxMask = 1 << longest.size();
        for (int mask = 1; mask < maxMask; mask++) {
            ArrayList<GapPromotionDecision> combination = new ArrayList<>();
            for (int index = 0; index < longest.size(); index++) {
                if ((mask & (1 << index)) != 0) {
                    combination.add(longest.get(index));
                }
            }
            TimingRewriteResult variant = runGapPromotionPrototype(
                    detailed.timingEvents(),
                    detailed.flushTimestampMs(),
                    combination
            );
            CwDecodeEvent nearest = findCharacterEventNearTimestamp(variant.decodeEvents, target.boundaryTimestampMs);
            System.out.println(String.format(
                    Locale.US,
                    "promote %s -> recall=%.3f nearest=%s text=%s",
                    renderGapPromotionList(combination),
                    computeRecall(RECORDING12_EXPECTED_TEXT, variant.decodedText),
                    renderCharacterEvent(nearest),
                sanitize(variant.decodedText)
            ));
        }
    }

    @Test
    public void printRecording12UnknownClusterGapThresholdSweep() throws Exception {
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                decodeDetailedCase("recording(12)", RECORDING12_SQL_PERCENT);
        List<RawTimingCharacterCandidate> candidates = buildRawTimingCharacterCandidates(detailed.timingEvents());
        RawTimingCharacterCandidate target = null;
        for (RawTimingCharacterCandidate candidate : candidates) {
            if (candidate.boundaryTimestampMs == 24044L && "...----..-.-..--".equals(candidate.sequence)) {
                target = candidate;
                break;
            }
        }
        if (target == null) {
            throw new IllegalStateException("Missing recording(12) unknown-cluster candidate");
        }

        double[] thresholds = new double[] {0.35d, 0.45d, 0.55d, 0.65d, 0.75d, 0.90d, 1.10d, 1.25d};
        System.out.println("==== recording12 unknown-cluster gap-threshold sweep ====");
        System.out.println(String.format(
                Locale.US,
                "base recall=%.3f seq=%s text=%s",
                computeRecall(RECORDING12_EXPECTED_TEXT, detailed.probeResult().decodedText()),
                target.sequence,
                sanitize(detailed.probeResult().decodedText())
        ));

        for (double threshold : thresholds) {
            ArrayList<GapPromotionDecision> promotions = new ArrayList<>();
            for (int index = 0; index < target.intraGapEvents.size(); index++) {
                CwTimingEvent gap = target.intraGapEvents.get(index);
                if (gap.ratioToIntraGapEstimate() < threshold) {
                    continue;
                }
                promotions.add(new GapPromotionDecision(
                        gap.timestampMs(),
                        gap.durationMs(),
                        gap.dotEstimateMs(),
                        gap.intraGapEstimateMs(),
                        gap.ratioToIntraGapEstimate(),
                        index + 1
                ));
            }
            TimingRewriteResult variant = runGapPromotionPrototype(
                    detailed.timingEvents(),
                    detailed.flushTimestampMs(),
                    promotions
            );
            CwDecodeEvent nearest = findCharacterEventNearTimestamp(variant.decodeEvents, target.boundaryTimestampMs);
            System.out.println(String.format(
                    Locale.US,
                    "threshold>=%.2f promotions=%s -> recall=%.3f nearest=%s text=%s",
                    threshold,
                    renderGapPromotionList(promotions),
                    computeRecall(RECORDING12_EXPECTED_TEXT, variant.decodedText),
                    renderCharacterEvent(nearest),
                sanitize(variant.decodedText)
            ));
        }
    }

    @Test
    public void printRecording12UnknownClusterCadenceContrast() throws Exception {
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                decodeDetailedCase("recording(12)", RECORDING12_SQL_PERCENT);
        List<RawTimingCharacterCandidate> candidates = buildRawTimingCharacterCandidates(detailed.timingEvents());
        RawTimingCharacterCandidate target = null;
        for (RawTimingCharacterCandidate candidate : candidates) {
            if (candidate.boundaryTimestampMs == 24044L && "...----..-.-..--".equals(candidate.sequence)) {
                target = candidate;
                break;
            }
        }
        if (target == null) {
            throw new IllegalStateException("Missing recording(12) unknown-cluster candidate");
        }

        ArrayList<Long> ditDurations = new ArrayList<>();
        ArrayList<Long> dahDurations = new ArrayList<>();
        ArrayList<Long> gapDurations = new ArrayList<>();
        ArrayList<Long> stabilizedDots = new ArrayList<>();
        ArrayList<Long> rawDots = new ArrayList<>();
        ArrayList<Double> trustedDots = new ArrayList<>();
        long windowStartMs = target.toneEvents.get(0).timestampMs() - 128L;
        long windowEndMs = target.boundaryTimestampMs;

        for (CwTimingEvent tone : target.toneEvents) {
            if (tone.classification() == CwTimingEvent.Classification.DIT) {
                ditDurations.add(tone.durationMs());
            } else if (tone.classification() == CwTimingEvent.Classification.DAH) {
                dahDurations.add(tone.durationMs());
            }
        }
        for (CwTimingEvent gap : target.intraGapEvents) {
            gapDurations.add(gap.durationMs());
        }
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : detailed.timingStateTraces()) {
            if (trace == null || trace.timestampMs() < windowStartMs || trace.timestampMs() > windowEndMs) {
                continue;
            }
            if (trace.stabilizedSnapshot() != null) {
                stabilizedDots.add((long) trace.stabilizedSnapshot().dotEstimateMs());
            }
            if (trace.rawSnapshot() != null) {
                rawDots.add((long) trace.rawSnapshot().dotEstimateMs());
            }
            if (trace.debugSnapshot() != null && trace.debugSnapshot().trustedDotEstimateMs() > 0.0d) {
                trustedDots.add(trace.debugSnapshot().trustedDotEstimateMs());
            }
        }

        System.out.println("==== recording12 unknown-cluster cadence contrast ====");
        System.out.println(String.format(
                Locale.US,
                "window=%d..%d seq=%s text=%s",
                windowStartMs,
                windowEndMs,
                target.sequence,
                sanitize(detailed.probeResult().decodedText())
        ));
        System.out.println(String.format(
                Locale.US,
                "local dits count=%d median=%d min=%d max=%d",
                ditDurations.size(),
                medianLong(ditDurations),
                minLong(ditDurations),
                maxLong(ditDurations)
        ));
        System.out.println(String.format(
                Locale.US,
                "local dahs count=%d median=%d min=%d max=%d",
                dahDurations.size(),
                medianLong(dahDurations),
                minLong(dahDurations),
                maxLong(dahDurations)
        ));
        System.out.println(String.format(
                Locale.US,
                "local gaps count=%d median=%d min=%d max=%d",
                gapDurations.size(),
                medianLong(gapDurations),
                minLong(gapDurations),
                maxLong(gapDurations)
        ));
        System.out.println(String.format(
                Locale.US,
                "state stabilizedDot median=%d min=%d max=%d",
                medianLong(stabilizedDots),
                minLong(stabilizedDots),
                maxLong(stabilizedDots)
        ));
        System.out.println(String.format(
                Locale.US,
                "state rawDot median=%d min=%d max=%d",
                medianLong(rawDots),
                minLong(rawDots),
                maxLong(rawDots)
        ));
        System.out.println(String.format(
                Locale.US,
                "state trustedDot median=%.1f min=%.1f max=%.1f",
                medianDouble(trustedDots),
                minDouble(trustedDots),
                maxDouble(trustedDots)
        ));
    }

    @Test
    public void printRecording12UnknownClusterForcedDotReclassSweep() throws Exception {
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                decodeDetailedCase("recording(12)", RECORDING12_SQL_PERCENT);
        List<RawTimingCharacterCandidate> candidates = buildRawTimingCharacterCandidates(detailed.timingEvents());
        RawTimingCharacterCandidate target = null;
        for (RawTimingCharacterCandidate candidate : candidates) {
            if (candidate.boundaryTimestampMs == 24044L && "...----..-.-..--".equals(candidate.sequence)) {
                target = candidate;
                break;
            }
        }
        if (target == null) {
            throw new IllegalStateException("Missing recording(12) unknown-cluster candidate");
        }

        long windowStartMs = target.toneEvents.get(0).timestampMs() - 128L;
        long windowEndMs = target.boundaryTimestampMs;
        long[] forcedDots = new long[] {70L, 80L, 90L, 100L};

        System.out.println("==== recording12 unknown-cluster forced-dot reclass sweep ====");
        System.out.println(String.format(
                Locale.US,
                "base recall=%.3f window=%d..%d text=%s",
                computeRecall(RECORDING12_EXPECTED_TEXT, detailed.probeResult().decodedText()),
                windowStartMs,
                windowEndMs,
                sanitize(detailed.probeResult().decodedText())
        ));

        for (long forcedDotMs : forcedDots) {
            TimingRewriteResult variant = runForcedDotWindowReclassPrototype(
                    detailed.timingEvents(),
                    detailed.flushTimestampMs(),
                    windowStartMs,
                    windowEndMs,
                    forcedDotMs
            );
            CwDecodeEvent nearest = findCharacterEventNearTimestamp(variant.decodeEvents, target.boundaryTimestampMs);
            System.out.println(String.format(
                    Locale.US,
                    "forcedDot=%d -> recall=%.3f nearest=%s text=%s",
                    forcedDotMs,
                    computeRecall(RECORDING12_EXPECTED_TEXT, variant.decodedText),
                    renderCharacterEvent(nearest),
                    sanitize(variant.decodedText)
            ));
        }
    }

    @Test
    public void printRecording12FirstCallsignUnknownShortDitSweep() throws Exception {
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                decodeDetailedCase("recording(12)", RECORDING12_SQL_PERCENT);
        long trustTimestampMs = firstTrustedTimestampMs(detailed);
        List<CwDecodeEvent> mismatches = listPostTrustMismatchCharacterEvents(
                detailed.decodeEvents(),
                trustTimestampMs,
                RECORDING12_EXPECTED_TEXT
        );
        CwDecodeEvent target = null;
        for (CwDecodeEvent mismatch : mismatches) {
            if (mismatch != null
                    && mismatch.unknownCharacter()
                    && mismatch.timestampMs() >= 18000L
                    && mismatch.timestampMs() <= 26000L) {
                target = mismatch;
                break;
            }
        }
        if (target == null) {
            throw new IllegalStateException("Missing recording(12) first callsign unknown target");
        }

        List<ShortDitDropDecision> candidates = collectLocalShortDitCandidates(
                detailed.timingEvents(),
                target.timestampMs() - RECORDING12_CALLSIGN_UNKNOWN_WINDOW_BEFORE_MS,
                target.timestampMs() + RECORDING12_CALLSIGN_UNKNOWN_WINDOW_AFTER_MS
        );

        System.out.println("==== recording12 first-callsign-unknown short-dit sweep ====");
        System.out.println(String.format(
                Locale.US,
                "base recall=%.3f target=%s text=%s",
                computeRecall(RECORDING12_EXPECTED_TEXT, detailed.probeResult().decodedText()),
                renderCharacterEvent(target),
                sanitize(detailed.probeResult().decodedText())
        ));
        System.out.println("candidates=" + renderDropList(candidates));

        if (candidates.isEmpty()) {
            System.out.println("variants=none");
            return;
        }

        for (ShortDitDropDecision candidate : candidates) {
            ShortDitSuppressionResult variant = runShortDitSuppressionPrototype(
                    detailed.toneEvents(),
                    detailed.flushTimestampMs(),
                    java.util.Collections.singletonList(candidate)
            );
            CwDecodeEvent variantTarget = findCharacterEventNearTimestamp(variant.decodeEvents, target.timestampMs());
            System.out.println(String.format(
                    Locale.US,
                    "drop %s -> recall=%.3f target=%s text=%s",
                    candidate.render(),
                    computeRecall(RECORDING12_EXPECTED_TEXT, variant.decodedText),
                    renderCharacterEvent(variantTarget),
                sanitize(variant.decodedText)
            ));
        }
    }

    private static void printToneOmissionSweepForCandidate(
            String label,
            RawTimingCharacterCandidate target,
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed
    ) {
        System.out.println(String.format(
                Locale.US,
                "-- %s boundary=%d seq=%s --",
                label,
                target.boundaryTimestampMs,
                target.sequence
        ));
        for (int index = 0; index < target.toneEvents.size(); index++) {
            CwTimingEvent tone = target.toneEvents.get(index);
            long followingGapTimestampMs = index < target.intraGapEvents.size()
                    ? target.intraGapEvents.get(index).timestampMs()
                    : target.boundaryTimestampMs;
            TimingRewriteResult variant = runTailToneTrimPrototype(
                    detailed.timingEvents(),
                    detailed.flushTimestampMs(),
                    java.util.Collections.singletonList(new TailToneTrimDecision(
                            tone.timestampMs(),
                            tone.durationMs(),
                            tone.dotEstimateMs(),
                            tone.ratioToDotEstimate(),
                            followingGapTimestampMs,
                            omitSequenceChar(target.sequence, index),
                            target.sequence
                    ))
            );
            System.out.println(String.format(
                    Locale.US,
                    "omit t%d @%d %.0fms -> recall=%.3f text=%s",
                    index + 1,
                    tone.timestampMs(),
                    (double) tone.durationMs(),
                    computeRecall(RECORDING12_EXPECTED_TEXT, variant.decodedText),
                    sanitize(variant.decodedText)
            ));
        }
    }

    @Test
    public void printRecording12FirstCallsignUnknownLocalTimingRepairProbe() throws Exception {
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                decodeDetailedCase("recording(12)", RECORDING12_SQL_PERCENT);
        List<RawTimingCharacterCandidate> candidates = buildRawTimingCharacterCandidates(detailed.timingEvents());
        RawTimingCharacterCandidate target = null;
        for (RawTimingCharacterCandidate candidate : candidates) {
            if (candidate.boundaryTimestampMs == 19112L && "-.-..--".equals(candidate.sequence)) {
                target = candidate;
                break;
            }
        }
        if (target == null) {
            throw new IllegalStateException("Missing recording(12) first callsign unknown candidate");
        }

        int shortestDitIndex = -1;
        CwTimingEvent shortestDitTone = null;
        for (int index = 0; index < target.toneEvents.size(); index++) {
            CwTimingEvent tone = target.toneEvents.get(index);
            if (tone.classification() != CwTimingEvent.Classification.DIT) {
                continue;
            }
            if (shortestDitTone == null || tone.durationMs() < shortestDitTone.durationMs()) {
                shortestDitTone = tone;
                shortestDitIndex = index;
            }
        }
        if (shortestDitTone == null || shortestDitIndex < 0 || shortestDitIndex >= target.intraGapEvents.size()) {
            throw new IllegalStateException("Missing recording(12) local repair micro-dit");
        }

        CwTimingEvent followingGap = target.intraGapEvents.get(shortestDitIndex);
        TailToneTrimDecision trim = new TailToneTrimDecision(
                shortestDitTone.timestampMs(),
                shortestDitTone.durationMs(),
                shortestDitTone.dotEstimateMs(),
                shortestDitTone.ratioToDotEstimate(),
                followingGap.timestampMs(),
                omitSequenceChar(target.sequence, shortestDitIndex),
                target.sequence
        );
        GapPromotionDecision promotion = new GapPromotionDecision(
                followingGap.timestampMs(),
                followingGap.durationMs(),
                followingGap.dotEstimateMs(),
                followingGap.intraGapEstimateMs(),
                followingGap.ratioToIntraGapEstimate(),
                shortestDitIndex + 1
        );

        TimingRewriteResult dropOnly = runTailToneTrimPrototype(
                detailed.timingEvents(),
                detailed.flushTimestampMs(),
                java.util.Collections.singletonList(trim)
        );
        TimingRewriteResult promoteOnly = runGapPromotionPrototype(
                detailed.timingEvents(),
                detailed.flushTimestampMs(),
                java.util.Collections.singletonList(promotion)
        );
        List<CwTimingEvent> droppedTimingEvents = dropTailToneEvents(
                detailed.timingEvents(),
                java.util.Collections.singletonList(trim)
        );
        TimingRewriteResult combined = runGapPromotionPrototype(
                droppedTimingEvents,
                detailed.flushTimestampMs(),
                java.util.Collections.singletonList(promotion)
        );

        System.out.println("==== recording12 first-callsign-unknown local-timing-repair ====");
        System.out.println(String.format(
                Locale.US,
                "base recall=%.3f seq=%s text=%s",
                computeRecall(RECORDING12_EXPECTED_TEXT, detailed.probeResult().decodedText()),
                target.sequence,
                sanitize(detailed.probeResult().decodedText())
        ));
        System.out.println(String.format(
                Locale.US,
                "microDit=t%d @%d %dms/%.2fdot promoteGap=@%d %dms/%.2fintra",
                shortestDitIndex + 1,
                shortestDitTone.timestampMs(),
                shortestDitTone.durationMs(),
                shortestDitTone.ratioToDotEstimate(),
                followingGap.timestampMs(),
                followingGap.durationMs(),
                followingGap.ratioToIntraGapEstimate()
        ));
        System.out.println(String.format(
                Locale.US,
                "drop-only recall=%.3f text=%s",
                computeRecall(RECORDING12_EXPECTED_TEXT, dropOnly.decodedText),
                sanitize(dropOnly.decodedText)
        ));
        System.out.println(String.format(
                Locale.US,
                "promote-only recall=%.3f text=%s",
                computeRecall(RECORDING12_EXPECTED_TEXT, promoteOnly.decodedText),
                sanitize(promoteOnly.decodedText)
        ));
        System.out.println(String.format(
                Locale.US,
                "drop+promote recall=%.3f text=%s",
                computeRecall(RECORDING12_EXPECTED_TEXT, combined.decodedText),
                sanitize(combined.decodedText)
        ));
    }

    private static ShortDitSuppressionResult runShortDitSuppressionPrototype(
            LocalAudioDecodeTestSupport.ForcedToneReplayResult replay
    ) {
        List<CharacterTimingDetail> details = buildCharacterTimingDetails(replay);
        List<ShortDitDropDecision> drops = chooseShortDitDrops(details);
        return runShortDitSuppressionPrototype(replay, drops);
    }

    private static ShortDitSuppressionResult runShortDitSuppressionPrototype(
            LocalAudioDecodeTestSupport.ForcedToneReplayResult replay,
            List<ShortDitDropDecision> drops
    ) {
        return runShortDitSuppressionPrototype(replay.toneEvents(), lastTimestamp(replay.toneEvents()), drops);
    }

    private static ShortDitSuppressionResult runShortDitSuppressionPrototype(
            List<CwToneEvent> sourceToneEvents,
            long flushTimestampMs,
            List<ShortDitDropDecision> drops
    ) {
        List<CwToneEvent> rewrittenToneEvents = dropTonePulses(sourceToneEvents, drops);

        CwHybridTimingModel timingModel = new CwHybridTimingModel();
        CwDecoder decoder = new CwDecoder();
        CwInterpreter interpreter = new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);
        QsoStateMachine qsoStateMachine = new QsoStateMachine();
        ArrayList<CwTimingEvent> rewrittenTimingEvents = new ArrayList<>();
        ArrayList<CwDecodeEvent> rewrittenDecodeEvents = new ArrayList<>();

        for (CwToneEvent toneEvent : rewrittenToneEvents) {
            List<CwTimingEvent> timingEvents = timingModel.process(toneEvent);
            rewrittenTimingEvents.addAll(timingEvents);
            drainTimingEvents(timingEvents, decoder, interpreter, qsoStateMachine, rewrittenDecodeEvents);
        }

        long effectiveFlushTimestampMs = Math.max(flushTimestampMs, lastTimestamp(sourceToneEvents));
        List<CwTimingEvent> flushedTimingEvents = timingModel.flushPendingGap(effectiveFlushTimestampMs);
        rewrittenTimingEvents.addAll(flushedTimingEvents);
        drainTimingEvents(flushedTimingEvents, decoder, interpreter, qsoStateMachine, rewrittenDecodeEvents);
        drainDecodeEvents(
                decoder.flushPendingCharacter(effectiveFlushTimestampMs),
                interpreter,
                qsoStateMachine,
                rewrittenDecodeEvents
        );

        return new ShortDitSuppressionResult(
                sanitize(decoder.snapshot().decodedText()),
                rewrittenToneEvents,
                rewrittenTimingEvents,
                rewrittenDecodeEvents,
                drops
        );
    }

    private static List<ShortDitDropDecision> chooseShortDitDrops(List<CharacterTimingDetail> details) {
        ArrayList<ShortDitDropDecision> drops = new ArrayList<>();
        for (CharacterTimingDetail detail : details) {
            if (detail == null || !detail.decodeEvent.unknownCharacter()) {
                continue;
            }
            String sourceSequence = detail.decodeEvent.sourceSequence();
            if (sourceSequence == null || sourceSequence.length() < OVERLONG_SEQUENCE_MIN_SYMBOLS) {
                continue;
            }
            List<CwTimingEvent> internalToneEvents = detail.internalToneEvents();
            if (internalToneEvents.size() != sourceSequence.length()) {
                continue;
            }
            for (int index = 0; index < internalToneEvents.size(); index++) {
                CwTimingEvent toneEvent = internalToneEvents.get(index);
                if (toneEvent.classification() != CwTimingEvent.Classification.DIT) {
                    continue;
                }
                double ratio = toneEvent.durationMs() / (double) Math.max(1L, toneEvent.dotEstimateMs());
                if (ratio > SHORT_DIT_RATIO_MAX) {
                    continue;
                }
                drops.add(new ShortDitDropDecision(
                        toneEvent.timestampMs(),
                        toneEvent.durationMs(),
                        toneEvent.dotEstimateMs(),
                        ratio,
                        sourceSequence,
                        index + 1
                ));
            }
        }
        return drops;
    }

    private static List<ShortDitDropDecision> collectLocalShortDitCandidates(
            List<CwTimingEvent> timingEvents,
            long windowStartMs,
            long windowEndMs
    ) {
        ArrayList<ShortDitDropDecision> candidates = new ArrayList<>();
        int toneIndexWithinWindow = 0;
        for (CwTimingEvent timingEvent : timingEvents) {
            if (timingEvent == null
                    || timingEvent.kind() != CwTimingEvent.Kind.TONE
                    || timingEvent.classification() != CwTimingEvent.Classification.DIT) {
                continue;
            }
            if (timingEvent.timestampMs() < windowStartMs || timingEvent.timestampMs() > windowEndMs) {
                continue;
            }
            double ratio = timingEvent.durationMs() / (double) Math.max(1L, timingEvent.dotEstimateMs());
            if (ratio > SHORT_DIT_RATIO_MAX) {
                continue;
            }
            toneIndexWithinWindow += 1;
            candidates.add(new ShortDitDropDecision(
                    timingEvent.timestampMs(),
                    timingEvent.durationMs(),
                    timingEvent.dotEstimateMs(),
                    ratio,
                    "window",
                    toneIndexWithinWindow
            ));
        }
        return candidates;
    }

    private static CharacterTimingDetail buildCharacterTimingDetail(
            List<CwTimingEvent> timingEvents,
            CwDecodeEvent characterEvent
    ) {
        int boundaryIndex = findBoundaryIndexForCharacter(timingEvents, characterEvent, 0);
        CwTimingEvent boundaryGapEvent = boundaryIndex >= 0 ? timingEvents.get(boundaryIndex) : null;
        List<CwTimingEvent> characterTimingEvents = collectCharacterTimingEvents(
                timingEvents,
                boundaryIndex,
                characterEvent.sourceSequence()
        );
        return new CharacterTimingDetail(characterEvent, characterTimingEvents, boundaryGapEvent);
    }

    private static List<GapPromotionDecision> collectLocalGapPromotionCandidates(CharacterTimingDetail detail) {
        ArrayList<GapPromotionDecision> candidates = new ArrayList<>();
        if (detail == null) {
            return candidates;
        }
        int candidateIndex = 0;
        for (CwTimingEvent event : detail.characterEvents) {
            if (event.kind() != CwTimingEvent.Kind.GAP
                    || event.classification() != CwTimingEvent.Classification.INTRA_SYMBOL_GAP) {
                continue;
            }
            double ratio = event.durationMs() / (double) Math.max(1L, event.intraGapEstimateMs());
            if (ratio > SHORT_DIT_RATIO_MAX) {
                continue;
            }
            candidateIndex += 1;
            candidates.add(new GapPromotionDecision(
                    event.timestampMs(),
                    event.durationMs(),
                    event.dotEstimateMs(),
                    event.intraGapEstimateMs(),
                    ratio,
                    candidateIndex
            ));
        }
        return candidates;
    }

    private static List<GapPromotionDecision> chooseTailGapPromotions(
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed
    ) {
        long trustTimestampMs = firstTrustedTimestampMs(detailed);
        List<CharacterTimingDetail> details = buildCharacterTimingDetailsFromTimingAndDecode(
                detailed.timingEvents(),
                detailed.decodeEvents()
        );
        ArrayList<GapPromotionDecision> promotions = new ArrayList<>();
        for (CharacterTimingDetail detail : details) {
            if (!eligibleForTailGapPromotion(detail, trustTimestampMs)) {
                continue;
            }
            List<CwTimingEvent> internalGaps = internalIntraGaps(detail);
            int startIndex = Math.max(0, internalGaps.size() - 2);
            for (int index = startIndex; index < internalGaps.size(); index++) {
                CwTimingEvent gap = internalGaps.get(index);
                promotions.add(new GapPromotionDecision(
                        gap.timestampMs(),
                        gap.durationMs(),
                        gap.dotEstimateMs(),
                        gap.intraGapEstimateMs(),
                        gap.ratioToIntraGapEstimate(),
                        index + 1
                ));
            }
        }
        return promotions;
    }

    private static List<TailToneTrimDecision> chooseTailToneTrims(
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed
    ) {
        long trustTimestampMs = firstTrustedTimestampMs(detailed);
        List<RawTimingCharacterCandidate> details = buildRawTimingCharacterCandidates(detailed.timingEvents());
        ArrayList<TailToneTrimDecision> trims = new ArrayList<>();
        for (RawTimingCharacterCandidate detail : details) {
            if (!eligibleForTailToneTrim(detail, trustTimestampMs)) {
                continue;
            }
            List<CwTimingEvent> tones = detail.toneEvents;
            List<CwTimingEvent> gaps = detail.intraGapEvents;
            CwTimingEvent tailTone = tones.get(tones.size() - 1);
            String trimmedSequence = detail.sequence.substring(0, detail.sequence.length() - 1);
            trims.add(new TailToneTrimDecision(
                    tailTone.timestampMs(),
                    tailTone.durationMs(),
                    tailTone.dotEstimateMs(),
                    tailTone.ratioToDotEstimate(),
                    gaps.get(gaps.size() - 1).timestampMs(),
                    trimmedSequence,
                    detail.sequence
            ));
        }
        return trims;
    }

    private static ShortDitDropDecision buildTailToneDropDecision(
            RawTimingCharacterCandidate candidate,
            int toneIndex
    ) {
        if (candidate == null || candidate.toneEvents.isEmpty()) {
            throw new IllegalArgumentException("Missing candidate tail tone");
        }
        CwTimingEvent tailTone = candidate.toneEvents.get(candidate.toneEvents.size() - 1);
        return new ShortDitDropDecision(
                tailTone.timestampMs(),
                tailTone.durationMs(),
                tailTone.dotEstimateMs(),
                tailTone.ratioToDotEstimate(),
                candidate.sequence,
                toneIndex
        );
    }

    private static TailToneTrimDecision buildTailToneTrimDecision(RawTimingCharacterCandidate candidate) {
        if (candidate == null
                || candidate.toneEvents.isEmpty()
                || candidate.intraGapEvents.isEmpty()
                || candidate.sequence == null
                || candidate.sequence.length() < 2) {
            throw new IllegalArgumentException("Missing candidate tail trim detail");
        }
        CwTimingEvent tailTone = candidate.toneEvents.get(candidate.toneEvents.size() - 1);
        CwTimingEvent precedingGap = candidate.intraGapEvents.get(candidate.intraGapEvents.size() - 1);
        return new TailToneTrimDecision(
                tailTone.timestampMs(),
                tailTone.durationMs(),
                tailTone.dotEstimateMs(),
                tailTone.ratioToDotEstimate(),
                precedingGap.timestampMs(),
                candidate.sequence.substring(0, candidate.sequence.length() - 1),
                candidate.sequence
        );
    }

    private static boolean eligibleForTailToneTrim(
            RawTimingCharacterCandidate detail,
            long trustTimestampMs
    ) {
        if (detail == null
                || detail.boundaryTimestampMs < trustTimestampMs) {
            return false;
        }
        String sourceSequence = detail.sequence;
        if (sourceSequence == null
                || sourceSequence.length() < TAIL_SEQUENCE_MIN_SYMBOLS
                || !isKnownMorseSequence(sourceSequence)) {
            return false;
        }
        String trimmedSequence = sourceSequence.substring(0, sourceSequence.length() - 1);
        if (!isKnownMorseSequence(trimmedSequence)) {
            return false;
        }
        List<CwTimingEvent> tones = detail.toneEvents;
        List<CwTimingEvent> gaps = detail.intraGapEvents;
        if (tones.size() != sourceSequence.length() || gaps.size() != sourceSequence.length() - 1 || gaps.size() < 1) {
            return false;
        }
        CwTimingEvent tailTone = tones.get(tones.size() - 1);
        if (tailTone.classification() != CwTimingEvent.Classification.DIT
                || tailTone.ratioToDotEstimate() > TAIL_TONE_TRIM_MAX_RATIO) {
            return false;
        }
        CwTimingEvent lastGap = gaps.get(gaps.size() - 1);
        return lastGap.ratioToIntraGapEstimate() <= TAIL_GAP_PROMOTION_MAX_RATIO;
    }

    private static List<RawTimingCharacterCandidate> buildRawTimingCharacterCandidates(
            List<CwTimingEvent> timingEvents
    ) {
        ArrayList<RawTimingCharacterCandidate> candidates = new ArrayList<>();
        ArrayList<CwTimingEvent> currentToneEvents = new ArrayList<>();
        ArrayList<CwTimingEvent> currentIntraGapEvents = new ArrayList<>();
        StringBuilder currentSequence = new StringBuilder();
        for (CwTimingEvent timingEvent : timingEvents) {
            if (timingEvent == null) {
                continue;
            }
            if (timingEvent.kind() == CwTimingEvent.Kind.TONE) {
                if (timingEvent.classification() == CwTimingEvent.Classification.DIT) {
                    currentSequence.append('.');
                    currentToneEvents.add(timingEvent);
                } else if (timingEvent.classification() == CwTimingEvent.Classification.DAH) {
                    currentSequence.append('-');
                    currentToneEvents.add(timingEvent);
                }
                continue;
            }
            if (timingEvent.classification() == CwTimingEvent.Classification.INTRA_SYMBOL_GAP) {
                currentIntraGapEvents.add(timingEvent);
                continue;
            }
            if (currentSequence.length() > 0) {
                candidates.add(new RawTimingCharacterCandidate(
                        currentSequence.toString(),
                        new ArrayList<>(currentToneEvents),
                        new ArrayList<>(currentIntraGapEvents),
                        timingEvent.timestampMs()
                ));
                currentSequence.setLength(0);
                currentToneEvents.clear();
                currentIntraGapEvents.clear();
            }
        }
        return candidates;
    }

    private static void printTriggerScanCase(
            String label,
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed
    ) {
        List<GapPromotionDecision> promotions = chooseTailGapPromotions(detailed);
        System.out.println(String.format(
                Locale.US,
                "%s promotions=%d text=%s",
                label,
                promotions.size(),
                sanitize(detailed.probeResult().decodedText())
        ));
        if (!promotions.isEmpty()) {
            TimingRewriteResult variant = runGapPromotionPrototype(
                    detailed.timingEvents(),
                    detailed.flushTimestampMs(),
                    promotions
            );
            System.out.println("  promote=" + renderGapPromotionList(promotions));
            System.out.println("  var=" + sanitize(variant.decodedText));
        }
    }

    private static void printTailDitRepairScanCase(
            String label,
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed
    ) {
        List<TailDitRepairCandidate> candidates = chooseTailDitRepairCandidates(detailed);
        System.out.println(String.format(
                Locale.US,
                "%s candidates=%d text=%s",
                label,
                candidates.size(),
                sanitize(detailed.probeResult().decodedText())
        ));
        if (!candidates.isEmpty()) {
            System.out.println("  repair=" + renderTailDitRepairList(candidates));
        }
    }

    private static List<TailDitRepairCandidate> chooseTailDitRepairCandidates(
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed
    ) {
        List<RawTimingCharacterCandidate> candidates = buildRawTimingCharacterCandidates(detailed.timingEvents());
        ArrayList<TailDitRepairCandidate> repairs = new ArrayList<>();
        for (RawTimingCharacterCandidate candidate : candidates) {
            TailDitRepairCandidate repair = buildTailDitRepairCandidate(candidate);
            if (repair != null) {
                repairs.add(repair);
            }
        }
        return repairs;
    }

    private static TailDitRepairCandidate buildTailDitRepairCandidate(
            RawTimingCharacterCandidate candidate
    ) {
        if (candidate == null) {
            return null;
        }
        String sourceSequence = candidate.sequence;
        if (sourceSequence == null
                || sourceSequence.length() < TAIL_DIT_REPAIR_MIN_TONES
                || sourceSequence.length() > TAIL_DIT_REPAIR_MAX_TONES) {
            return null;
        }

        String trimmedSequence = sourceSequence.substring(0, sourceSequence.length() - 1);
        if (!isKnownAlphaNumericSequence(trimmedSequence)) {
            return null;
        }

        List<CwTimingEvent> tones = candidate.toneEvents;
        List<CwTimingEvent> gaps = candidate.intraGapEvents;
        if (tones.size() != sourceSequence.length() || gaps.size() != sourceSequence.length() - 1 || gaps.isEmpty()) {
            return null;
        }

        CwTimingEvent tailTone = tones.get(tones.size() - 1);
        if (tailTone.classification() != CwTimingEvent.Classification.DIT
                || tailTone.ratioToDotEstimate() > TAIL_DIT_REPAIR_LAST_TONE_MAX_RATIO) {
            return null;
        }

        CwTimingEvent lastGap = gaps.get(gaps.size() - 1);
        if (lastGap.ratioToIntraGapEstimate() > TAIL_DIT_REPAIR_LAST_GAP_MAX_RATIO) {
            return null;
        }

        long priorDitMedianDurationMs = medianPriorDitDurationMs(tones);
        if (priorDitMedianDurationMs <= 0L) {
            return null;
        }
        double priorDitRatio = tailTone.durationMs() / (double) Math.max(1L, priorDitMedianDurationMs);
        if (priorDitRatio > TAIL_DIT_REPAIR_PRIOR_DIT_MAX_RATIO) {
            return null;
        }

        return new TailDitRepairCandidate(
                candidate.boundaryTimestampMs,
                sourceSequence,
                trimmedSequence,
                tailTone.timestampMs(),
                tailTone.durationMs(),
                tailTone.dotEstimateMs(),
                tailTone.ratioToDotEstimate(),
                lastGap.timestampMs(),
                lastGap.durationMs(),
                lastGap.intraGapEstimateMs(),
                lastGap.ratioToIntraGapEstimate(),
                priorDitMedianDurationMs,
                priorDitRatio
        );
    }

    private static TimingRewriteResult runTailToneTrimPrototype(
            List<CwTimingEvent> sourceTimingEvents,
            long flushTimestampMs,
            List<TailToneTrimDecision> trims
    ) {
        List<CwTimingEvent> rewrittenTimingEvents = dropTailToneEvents(sourceTimingEvents, trims);
        CwDecoder decoder = new CwDecoder();
        CwInterpreter interpreter = new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);
        QsoStateMachine qsoStateMachine = new QsoStateMachine();
        ArrayList<CwDecodeEvent> rewrittenDecodeEvents = new ArrayList<>();
        drainTimingEvents(rewrittenTimingEvents, decoder, interpreter, qsoStateMachine, rewrittenDecodeEvents);
        drainDecodeEvents(
                decoder.flushPendingCharacter(Math.max(1L, flushTimestampMs)),
                interpreter,
                qsoStateMachine,
                rewrittenDecodeEvents
        );
        return new TimingRewriteResult(
                sanitize(decoder.snapshot().decodedText()),
                rewrittenTimingEvents,
                rewrittenDecodeEvents,
                new ArrayList<>()
        );
    }

    private static List<CwTimingEvent> dropTailToneEvents(
            List<CwTimingEvent> timingEvents,
            List<TailToneTrimDecision> trims
    ) {
        if (trims == null || trims.isEmpty()) {
            return new ArrayList<>(timingEvents);
        }
        ArrayList<CwTimingEvent> rewritten = new ArrayList<>(timingEvents.size());
        for (CwTimingEvent timingEvent : timingEvents) {
            if (findTailToneTrimDecision(trims, timingEvent) != null) {
                continue;
            }
            rewritten.add(timingEvent);
        }
        return rewritten;
    }

    private static TailToneTrimDecision findTailToneTrimDecision(
            List<TailToneTrimDecision> trims,
            CwTimingEvent timingEvent
    ) {
        if (timingEvent == null
                || timingEvent.kind() != CwTimingEvent.Kind.TONE) {
            return null;
        }
        for (TailToneTrimDecision trim : trims) {
            if (trim.timestampMs == timingEvent.timestampMs()) {
                return trim;
            }
        }
        return null;
    }

    private static boolean eligibleForTailGapPromotion(
            CharacterTimingDetail detail,
            long trustTimestampMs
    ) {
        if (detail == null
                || detail.decodeEvent == null
                || detail.decodeEvent.timestampMs() < trustTimestampMs) {
            return false;
        }
        List<CwTimingEvent> tones = detail.internalToneEvents();
        List<CwTimingEvent> gaps = internalIntraGaps(detail);
        if (tones.size() < 5 || gaps.size() < 2) {
            return false;
        }
        CwTimingEvent lastTone = tones.get(tones.size() - 1);
        if (lastTone.ratioToDotEstimate() > TAIL_GAP_PROMOTION_LAST_TONE_MAX_RATIO) {
            return false;
        }
        for (int index = Math.max(0, gaps.size() - 2); index < gaps.size(); index++) {
            if (gaps.get(index).ratioToIntraGapEstimate() > TAIL_GAP_PROMOTION_MAX_RATIO) {
                return false;
            }
        }
        return true;
    }

    private static List<CharacterTimingDetail> buildCharacterTimingDetailsFromTimingAndDecode(
            List<CwTimingEvent> timingEvents,
            List<CwDecodeEvent> decodeEvents
    ) {
        ArrayList<CwDecodeEvent> characterEvents = new ArrayList<>();
        for (CwDecodeEvent event : decodeEvents) {
            if (event.type() == CwDecodeEvent.Type.CHARACTER_DECODED) {
                characterEvents.add(event);
            }
        }

        ArrayList<CharacterTimingDetail> details = new ArrayList<>();
        int boundarySearchStart = 0;
        for (CwDecodeEvent characterEvent : characterEvents) {
            int boundaryIndex = findBoundaryIndexForCharacter(timingEvents, characterEvent, boundarySearchStart);
            CwTimingEvent boundaryGapEvent = boundaryIndex >= 0 ? timingEvents.get(boundaryIndex) : null;
            List<CwTimingEvent> characterTimingEvents = collectCharacterTimingEvents(
                    timingEvents,
                    boundaryIndex,
                    characterEvent.sourceSequence()
            );
            details.add(new CharacterTimingDetail(characterEvent, characterTimingEvents, boundaryGapEvent));
            if (boundaryIndex >= 0) {
                boundarySearchStart = boundaryIndex + 1;
            }
        }
        return details;
    }

    private static List<CwTimingEvent> internalIntraGaps(CharacterTimingDetail detail) {
        ArrayList<CwTimingEvent> gaps = new ArrayList<>();
        if (detail == null) {
            return gaps;
        }
        for (CwTimingEvent event : detail.characterEvents) {
            if (event.kind() == CwTimingEvent.Kind.GAP
                    && event.classification() == CwTimingEvent.Classification.INTRA_SYMBOL_GAP) {
                gaps.add(event);
            }
        }
        return gaps;
    }

    private static List<CwToneEvent> dropTonePulses(
            List<CwToneEvent> toneEvents,
            List<ShortDitDropDecision> drops
    ) {
        if (drops.isEmpty()) {
            return new ArrayList<>(toneEvents);
        }
        ArrayList<CwToneEvent> rewritten = new ArrayList<>();
        int dropIndex = 0;
        ShortDitDropDecision activeDrop = drops.get(dropIndex);
        boolean skippingPulse = false;
        for (CwToneEvent toneEvent : toneEvents) {
            while (activeDrop != null && toneEvent.timestampMs() > activeDrop.toneOffTimestampMs && !skippingPulse) {
                dropIndex += 1;
                activeDrop = dropIndex < drops.size() ? drops.get(dropIndex) : null;
            }
            if (activeDrop != null
                    && toneEvent.type() == CwToneEvent.Type.TONE_ON
                    && toneEvent.timestampMs() < activeDrop.toneOffTimestampMs) {
                skippingPulse = true;
                continue;
            }
            if (activeDrop != null
                    && skippingPulse
                    && toneEvent.type() == CwToneEvent.Type.TONE_OFF
                    && toneEvent.timestampMs() == activeDrop.toneOffTimestampMs) {
                skippingPulse = false;
                dropIndex += 1;
                activeDrop = dropIndex < drops.size() ? drops.get(dropIndex) : null;
                continue;
            }
            rewritten.add(toneEvent);
        }
        return rewritten;
    }

    private static List<CharacterTimingDetail> buildCharacterTimingDetails(
            LocalAudioDecodeTestSupport.ForcedToneReplayResult replay
    ) {
        ArrayList<CwDecodeEvent> characterEvents = new ArrayList<>();
        for (CwDecodeEvent event : replay.decodeEvents()) {
            if (event.type() == CwDecodeEvent.Type.CHARACTER_DECODED) {
                characterEvents.add(event);
            }
        }

        ArrayList<CharacterTimingDetail> details = new ArrayList<>();
        List<CwTimingEvent> timingEvents = replay.timingEvents();
        int boundarySearchStart = 0;
        for (CwDecodeEvent characterEvent : characterEvents) {
            int boundaryIndex = findBoundaryIndexForCharacter(timingEvents, characterEvent, boundarySearchStart);
            CwTimingEvent boundaryGapEvent = boundaryIndex >= 0 ? timingEvents.get(boundaryIndex) : null;
            List<CwTimingEvent> characterTimingEvents = collectCharacterTimingEvents(
                    timingEvents,
                    boundaryIndex,
                    characterEvent.sourceSequence()
            );
            details.add(new CharacterTimingDetail(
                    characterEvent,
                    characterTimingEvents,
                    boundaryGapEvent
            ));
            if (boundaryIndex >= 0) {
                boundarySearchStart = boundaryIndex + 1;
            }
        }
        return details;
    }

    private static List<List<ShortDitDropDecision>> enumerateNonEmptyDropCombinations(
            List<ShortDitDropDecision> candidates
    ) {
        ArrayList<List<ShortDitDropDecision>> combinations = new ArrayList<>();
        int size = candidates.size();
        if (size <= 0 || size >= 31) {
            return combinations;
        }
        int maxMask = 1 << size;
        for (int mask = 1; mask < maxMask; mask++) {
            ArrayList<ShortDitDropDecision> combination = new ArrayList<>();
            for (int index = 0; index < size; index++) {
                if ((mask & (1 << index)) != 0) {
                    combination.add(candidates.get(index));
                }
            }
            combinations.add(combination);
        }
        return combinations;
    }

    private static List<List<GapPromotionDecision>> enumerateNonEmptyGapPromotionCombinations(
            List<GapPromotionDecision> candidates
    ) {
        ArrayList<List<GapPromotionDecision>> combinations = new ArrayList<>();
        int size = candidates.size();
        if (size <= 0 || size >= 31) {
            return combinations;
        }
        int maxMask = 1 << size;
        for (int mask = 1; mask < maxMask; mask++) {
            ArrayList<GapPromotionDecision> combination = new ArrayList<>();
            for (int index = 0; index < size; index++) {
                if ((mask & (1 << index)) != 0) {
                    combination.add(candidates.get(index));
                }
            }
            combinations.add(combination);
        }
        return combinations;
    }

    private static int findBoundaryIndexForCharacter(
            List<CwTimingEvent> timingEvents,
            CwDecodeEvent characterEvent,
            int searchStart
    ) {
        for (int index = Math.max(0, searchStart); index < timingEvents.size(); index++) {
            CwTimingEvent timingEvent = timingEvents.get(index);
            if (timingEvent.timestampMs() != characterEvent.timestampMs()) {
                continue;
            }
            if (timingEvent.kind() == CwTimingEvent.Kind.GAP
                    && timingEvent.classification() != CwTimingEvent.Classification.INTRA_SYMBOL_GAP) {
                return index;
            }
        }
        return -1;
    }

    private static List<CwTimingEvent> collectCharacterTimingEvents(
            List<CwTimingEvent> timingEvents,
            int boundaryIndex,
            String sourceSequence
    ) {
        int requiredTones = sourceSequence == null ? 0 : sourceSequence.length();
        if (requiredTones <= 0) {
            return new ArrayList<>();
        }
        int scanIndex = boundaryIndex >= 0 ? boundaryIndex - 1 : timingEvents.size() - 1;
        ArrayList<CwTimingEvent> reversed = new ArrayList<>();
        int collectedTones = 0;
        while (scanIndex >= 0 && collectedTones < requiredTones) {
            CwTimingEvent timingEvent = timingEvents.get(scanIndex);
            if (timingEvent.kind() == CwTimingEvent.Kind.TONE) {
                reversed.add(timingEvent);
                collectedTones += 1;
                scanIndex -= 1;
                continue;
            }
            if (timingEvent.classification() == CwTimingEvent.Classification.INTRA_SYMBOL_GAP) {
                reversed.add(timingEvent);
                scanIndex -= 1;
                continue;
            }
            break;
        }
        ArrayList<CwTimingEvent> ordered = new ArrayList<>(reversed.size());
        for (int index = reversed.size() - 1; index >= 0; index--) {
            ordered.add(reversed.get(index));
        }
        return ordered;
    }

    private static void drainTimingEvents(
            List<CwTimingEvent> timingEvents,
            CwDecoder decoder,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            List<CwDecodeEvent> decodeEvents
    ) {
        for (CwTimingEvent timingEvent : timingEvents) {
            drainDecodeEvents(decoder.process(timingEvent), interpreter, qsoStateMachine, decodeEvents);
        }
    }

    private static void drainDecodeEvents(
            List<CwDecodeEvent> decodeEvents,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            List<CwDecodeEvent> capturedDecodeEvents
    ) {
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            interpreter.process(decodeEvent);
            qsoStateMachine.process(interpreter.snapshot(), decodeEvent.timestampMs());
            capturedDecodeEvents.add(decodeEvent);
        }
    }

    private static long lastTimestamp(List<CwToneEvent> toneEvents) {
        if (toneEvents.isEmpty()) {
            return 1L;
        }
        return Math.max(1L, toneEvents.get(toneEvents.size() - 1).timestampMs());
    }

    private static TimingRewriteResult runGapPromotionPrototype(
            List<CwTimingEvent> sourceTimingEvents,
            long flushTimestampMs,
            List<GapPromotionDecision> promotions
    ) {
        List<CwTimingEvent> rewrittenTimingEvents = promoteGapEvents(sourceTimingEvents, promotions);
        CwDecoder decoder = new CwDecoder();
        CwInterpreter interpreter = new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);
        QsoStateMachine qsoStateMachine = new QsoStateMachine();
        ArrayList<CwDecodeEvent> rewrittenDecodeEvents = new ArrayList<>();

        drainTimingEvents(rewrittenTimingEvents, decoder, interpreter, qsoStateMachine, rewrittenDecodeEvents);
        drainDecodeEvents(
                decoder.flushPendingCharacter(Math.max(1L, flushTimestampMs)),
                interpreter,
                qsoStateMachine,
                rewrittenDecodeEvents
        );

        return new TimingRewriteResult(
                sanitize(decoder.snapshot().decodedText()),
                rewrittenTimingEvents,
                rewrittenDecodeEvents,
                promotions
        );
    }

    private static TimingRewriteResult runForcedDotWindowReclassPrototype(
            List<CwTimingEvent> sourceTimingEvents,
            long flushTimestampMs,
            long windowStartMs,
            long windowEndMs,
            long forcedDotMs
    ) {
        List<CwTimingEvent> rewrittenTimingEvents = reclassifyWindowWithForcedDot(
                sourceTimingEvents,
                windowStartMs,
                windowEndMs,
                forcedDotMs
        );
        CwDecoder decoder = new CwDecoder();
        CwInterpreter interpreter = new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);
        QsoStateMachine qsoStateMachine = new QsoStateMachine();
        ArrayList<CwDecodeEvent> rewrittenDecodeEvents = new ArrayList<>();

        drainTimingEvents(rewrittenTimingEvents, decoder, interpreter, qsoStateMachine, rewrittenDecodeEvents);
        drainDecodeEvents(
                decoder.flushPendingCharacter(Math.max(1L, flushTimestampMs)),
                interpreter,
                qsoStateMachine,
                rewrittenDecodeEvents
        );

        return new TimingRewriteResult(
                sanitize(decoder.snapshot().decodedText()),
                rewrittenTimingEvents,
                rewrittenDecodeEvents,
                new ArrayList<>()
        );
    }

    private static List<CwTimingEvent> reclassifyWindowWithForcedDot(
            List<CwTimingEvent> timingEvents,
            long windowStartMs,
            long windowEndMs,
            long forcedDotMs
    ) {
        ArrayList<CwTimingEvent> rewritten = new ArrayList<>(timingEvents.size());
        long safeDotMs = Math.max(1L, forcedDotMs);
        for (CwTimingEvent timingEvent : timingEvents) {
            if (timingEvent == null
                    || timingEvent.timestampMs() < windowStartMs
                    || timingEvent.timestampMs() > windowEndMs) {
                rewritten.add(timingEvent);
                continue;
            }
            CwTimingEvent.Classification classification = timingEvent.kind() == CwTimingEvent.Kind.TONE
                    ? classifyToneForForcedDot(timingEvent.durationMs(), safeDotMs)
                    : classifyGapForForcedDot(timingEvent.durationMs(), safeDotMs, safeDotMs);
            rewritten.add(new CwTimingEvent(
                    timingEvent.kind(),
                    classification,
                    timingEvent.timestampMs(),
                    timingEvent.durationMs(),
                    safeDotMs,
                    safeDotMs
            ));
        }
        return rewritten;
    }

    private static CwTimingEvent.Classification classifyToneForForcedDot(long toneDurationMs, long dotEstimateMs) {
        double ratio = toneDurationMs / (double) Math.max(1L, dotEstimateMs);
        if (ratio <= 1.8d) {
            return CwTimingEvent.Classification.DIT;
        }
        if (ratio <= 4.8d) {
            return CwTimingEvent.Classification.DAH;
        }
        return CwTimingEvent.Classification.UNKNOWN;
    }

    private static CwTimingEvent.Classification classifyGapForForcedDot(
            long gapDurationMs,
            long dotEstimateMs,
            long intraGapEstimateMs
    ) {
        double ratio = gapDurationMs / (double) Math.max(1L, dotEstimateMs);
        double intraRatio = gapDurationMs / (double) Math.max(1L, intraGapEstimateMs);
        if (ratio <= 1.8d) {
            return CwTimingEvent.Classification.INTRA_SYMBOL_GAP;
        }
        if (ratio >= 4.55d && intraRatio >= 5.0d) {
            return CwTimingEvent.Classification.WORD_GAP;
        }
        if (ratio <= 4.70d) {
            return CwTimingEvent.Classification.LETTER_GAP;
        }
        if (ratio <= 12.8d) {
            return CwTimingEvent.Classification.WORD_GAP;
        }
        return CwTimingEvent.Classification.UNKNOWN;
    }

    private static List<CwTimingEvent> promoteGapEvents(
            List<CwTimingEvent> timingEvents,
            List<GapPromotionDecision> promotions
    ) {
        if (promotions == null || promotions.isEmpty()) {
            return new ArrayList<>(timingEvents);
        }
        ArrayList<CwTimingEvent> rewritten = new ArrayList<>(timingEvents.size());
        for (CwTimingEvent timingEvent : timingEvents) {
            GapPromotionDecision promotion = findGapPromotionDecision(promotions, timingEvent);
            if (promotion == null) {
                rewritten.add(timingEvent);
                continue;
            }
            rewritten.add(new CwTimingEvent(
                    timingEvent.kind(),
                    CwTimingEvent.Classification.LETTER_GAP,
                    timingEvent.timestampMs(),
                    timingEvent.durationMs(),
                    timingEvent.dotEstimateMs(),
                    timingEvent.intraGapEstimateMs()
            ));
        }
        return rewritten;
    }

    private static GapPromotionDecision findGapPromotionDecision(
            List<GapPromotionDecision> promotions,
            CwTimingEvent timingEvent
    ) {
        if (timingEvent == null
                || timingEvent.kind() != CwTimingEvent.Kind.GAP
                || timingEvent.classification() != CwTimingEvent.Classification.INTRA_SYMBOL_GAP) {
            return null;
        }
        for (GapPromotionDecision promotion : promotions) {
            if (promotion.timestampMs == timingEvent.timestampMs()) {
                return promotion;
            }
        }
        return null;
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace('\u25A1', '?').trim();
        return normalized.isEmpty() ? "(empty)" : normalized;
    }

    private static double computeRecall(String expected, String actual) {
        String expectedCanonical = canonicalize(expected);
        String actualCanonical = canonicalize(actual);
        if (expectedCanonical.isEmpty()) {
            return actualCanonical.isEmpty() ? 1.0d : 0.0d;
        }
        int[][] dp = new int[expectedCanonical.length() + 1][actualCanonical.length() + 1];
        for (int expectedIndex = expectedCanonical.length() - 1; expectedIndex >= 0; expectedIndex--) {
            for (int actualIndex = actualCanonical.length() - 1; actualIndex >= 0; actualIndex--) {
                if (expectedCanonical.charAt(expectedIndex) == actualCanonical.charAt(actualIndex)) {
                    dp[expectedIndex][actualIndex] = dp[expectedIndex + 1][actualIndex + 1] + 1;
                } else {
                    dp[expectedIndex][actualIndex] = Math.max(
                            dp[expectedIndex + 1][actualIndex],
                            dp[expectedIndex][actualIndex + 1]
                    );
                }
            }
        }
        return dp[0][0] / (double) expectedCanonical.length();
    }

    private static String canonicalize(String text) {
        if (text == null) {
            return "";
        }
        String upper = text.replace('\u25A1', '?').toUpperCase(Locale.US);
        StringBuilder builder = new StringBuilder(upper.length());
        for (int index = 0; index < upper.length(); index++) {
            char ch = upper.charAt(index);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '?') {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static long firstTrustedTimestampMs(LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed) {
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : detailed.timingStateTraces()) {
            if (trace != null
                    && trace.debugSnapshot() != null
                    && trace.debugSnapshot().trustedDotEstimateMs() > 0.0d) {
                return trace.timestampMs();
            }
        }
        return -1L;
    }

    private static CwDecodeEvent findFirstPostTrustMismatchCharacterEvent(
            List<CwDecodeEvent> decodeEvents,
            long trustTimestampMs,
            String expectedText
    ) {
        String expectedCanonical = canonicalize(expectedText);
        String actualCanonical = canonicalize(lastOutputText(decodeEvents));
        if (expectedCanonical.isEmpty() || actualCanonical.isEmpty()) {
            return null;
        }
        boolean[] matchedActualPositions = computeActualLcsMatches(expectedCanonical, actualCanonical);
        int actualCanonicalLengthBeforeTrust = canonicalLengthAtOrBefore(decodeEvents, trustTimestampMs);
        int firstMismatchActualIndex = -1;
        for (int index = actualCanonicalLengthBeforeTrust; index < matchedActualPositions.length; index++) {
            if (!matchedActualPositions[index]) {
                firstMismatchActualIndex = index;
                break;
            }
        }
        if (firstMismatchActualIndex < 0) {
            return null;
        }
        for (CwDecodeEvent event : decodeEvents) {
            if (event == null
                    || event.type() != CwDecodeEvent.Type.CHARACTER_DECODED
                    || event.timestampMs() < trustTimestampMs) {
                continue;
            }
            int actualLengthAfterEvent = canonicalize(event.outputText()).length();
            if (actualLengthAfterEvent > firstMismatchActualIndex) {
                return event;
            }
        }
        return null;
    }

    private static List<CwDecodeEvent> listPostTrustMismatchCharacterEvents(
            List<CwDecodeEvent> decodeEvents,
            long trustTimestampMs,
            String expectedText
    ) {
        ArrayList<CwDecodeEvent> mismatches = new ArrayList<>();
        if (decodeEvents == null || decodeEvents.isEmpty()) {
            return mismatches;
        }
        String expectedCanonical = canonicalize(expectedText);
        String finalActualCanonical = canonicalize(lastOutputText(decodeEvents));
        if (expectedCanonical.isEmpty() || finalActualCanonical.isEmpty()) {
            return mismatches;
        }

        boolean[] matchedActualPositions = computeActualLcsMatches(expectedCanonical, finalActualCanonical);
        int actualCanonicalLengthBeforeTrust = canonicalLengthAtOrBefore(decodeEvents, trustTimestampMs);
        int nextCharacterStart = actualCanonicalLengthBeforeTrust;
        for (int index = actualCanonicalLengthBeforeTrust; index < matchedActualPositions.length; index++) {
            if (matchedActualPositions[index]) {
                continue;
            }
            for (CwDecodeEvent event : decodeEvents) {
                if (event == null
                        || event.type() != CwDecodeEvent.Type.CHARACTER_DECODED
                        || event.timestampMs() < trustTimestampMs) {
                    continue;
                }
                int actualLengthAfterEvent = canonicalize(event.outputText()).length();
                if (actualLengthAfterEvent <= index || actualLengthAfterEvent <= nextCharacterStart) {
                    continue;
                }
                mismatches.add(event);
                nextCharacterStart = actualLengthAfterEvent;
                break;
            }
        }
        return mismatches;
    }

    private static CwDecodeEvent findCharacterEventNearTimestamp(
            List<CwDecodeEvent> decodeEvents,
            long targetTimestampMs
    ) {
        CwDecodeEvent nearest = null;
        long nearestDistanceMs = Long.MAX_VALUE;
        for (CwDecodeEvent event : decodeEvents) {
            if (event == null || event.type() != CwDecodeEvent.Type.CHARACTER_DECODED) {
                continue;
            }
            long distanceMs = Math.abs(event.timestampMs() - targetTimestampMs);
            if (distanceMs < nearestDistanceMs) {
                nearest = event;
                nearestDistanceMs = distanceMs;
            }
        }
        return nearest;
    }

    private static String renderCharacterEvent(CwDecodeEvent event) {
        if (event == null) {
            return "none";
        }
        return String.format(
                Locale.US,
                "@%d emit=%s seq=%s out=%s",
                event.timestampMs(),
                compact(event.emittedValue()),
                compact(event.sourceSequence()),
                sanitize(event.outputText())
        );
    }

    private static String renderDropList(List<ShortDitDropDecision> drops) {
        if (drops == null || drops.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < drops.size(); index++) {
            if (index > 0) {
                builder.append(" | ");
            }
            builder.append(drops.get(index).render());
        }
        return builder.toString();
    }

    private static String renderGapPromotionList(List<GapPromotionDecision> promotions) {
        if (promotions == null || promotions.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < promotions.size(); index++) {
            if (index > 0) {
                builder.append(" | ");
            }
            builder.append(promotions.get(index).render());
        }
        return builder.toString();
    }

    private static String renderTailToneTrimList(List<TailToneTrimDecision> trims) {
        if (trims == null || trims.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < trims.size(); index++) {
            if (index > 0) {
                builder.append(" | ");
            }
            builder.append(trims.get(index).render());
        }
        return builder.toString();
    }

    private static String renderTailDitRepairList(List<TailDitRepairCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < candidates.size(); index++) {
            if (index > 0) {
                builder.append(" | ");
            }
            builder.append(candidates.get(index).render());
        }
        return builder.toString();
    }

    private static String yesNo(boolean value) {
        return value ? "Y" : "N";
    }

    private static boolean isKnownMorseSequence(String sequence) {
        if (sequence == null || sequence.isEmpty()) {
            return false;
        }
        return LocalMorseReference.TABLE.containsKey(sequence);
    }

    private static boolean isKnownAlphaNumericSequence(String sequence) {
        if (sequence == null || sequence.isEmpty()) {
            return false;
        }
        String decoded = LocalMorseReference.TABLE.get(sequence);
        return decoded != null && decoded.length() == 1 && Character.isLetterOrDigit(decoded.charAt(0));
    }

    private static long medianPriorDitDurationMs(List<CwTimingEvent> tones) {
        if (tones == null || tones.size() < 2) {
            return 0L;
        }
        ArrayList<Long> priorDits = new ArrayList<>();
        for (int index = 0; index < tones.size() - 1; index++) {
            CwTimingEvent tone = tones.get(index);
            if (tone != null && tone.classification() == CwTimingEvent.Classification.DIT) {
                priorDits.add(tone.durationMs());
            }
        }
        if (priorDits.isEmpty()) {
            return 0L;
        }
        priorDits.sort(Long::compareTo);
        return priorDits.get(priorDits.size() / 2);
    }

    private static String omitSequenceChar(String sequence, int indexToOmit) {
        if (sequence == null || sequence.isEmpty() || indexToOmit < 0 || indexToOmit >= sequence.length()) {
            return sequence == null ? "" : sequence;
        }
        return sequence.substring(0, indexToOmit) + sequence.substring(indexToOmit + 1);
    }

    private static long medianLong(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return 0L;
        }
        ArrayList<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        return sorted.get(sorted.size() / 2);
    }

    private static long minLong(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return 0L;
        }
        long min = Long.MAX_VALUE;
        for (Long value : values) {
            if (value != null && value < min) {
                min = value;
            }
        }
        return min == Long.MAX_VALUE ? 0L : min;
    }

    private static long maxLong(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return 0L;
        }
        long max = Long.MIN_VALUE;
        for (Long value : values) {
            if (value != null && value > max) {
                max = value;
            }
        }
        return max == Long.MIN_VALUE ? 0L : max;
    }

    private static double medianDouble(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0d;
        }
        ArrayList<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);
        return sorted.get(sorted.size() / 2);
    }

    private static double minDouble(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0d;
        }
        double min = Double.POSITIVE_INFINITY;
        for (Double value : values) {
            if (value != null && value < min) {
                min = value;
            }
        }
        return Double.isInfinite(min) ? 0.0d : min;
    }

    private static double maxDouble(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0d;
        }
        double max = Double.NEGATIVE_INFINITY;
        for (Double value : values) {
            if (value != null && value > max) {
                max = value;
            }
        }
        return Double.isInfinite(max) ? 0.0d : max;
    }

    private static String compact(String text) {
        if (text == null) {
            return "-";
        }
        String normalized = text.replace('\u25A1', '?').trim();
        return normalized.isEmpty() ? "-" : normalized;
    }

    private static String lastOutputText(List<CwDecodeEvent> decodeEvents) {
        String outputText = "";
        for (CwDecodeEvent event : decodeEvents) {
            if (event != null) {
                outputText = event.outputText();
            }
        }
        return outputText;
    }

    private static LocalAudioDecodeTestSupport.OfflineDetailedProbeResult decodeDetailedCase(String alias)
            throws Exception {
        return decodeDetailedCase(alias, SQL_PERCENT);
    }

    private static LocalAudioDecodeTestSupport.OfflineDetailedProbeResult decodeDetailedCase(
            String alias,
            int sqlPercent
    ) throws Exception {
        if ("capture.wav".equals(alias)) {
            Path captureWav = LocalAudioDecodeTestSupport.findTraceDir().resolve("capture.wav");
            if (!java.nio.file.Files.isRegularFile(captureWav)) {
                throw new IllegalStateException("Missing capture wav: " + captureWav);
            }
            return LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                    "capture.wav",
                    LocalAudioDecodeTestSupport.normalizeFramesToZero(
                            LocalAudioDecodeTestSupport.loadFramesFromWavFile(captureWav)
                    ),
                    PREFERRED_TONE_HZ,
                    SEED_WPM,
                    sqlPercent,
                    false,
                    CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
            );
        }
        for (Path wavFile : LocalAudioDecodeTestSupport.listConvertedWavFiles()) {
            String filename = wavFile.getFileName().toString();
            if (filename.contains(alias.replace("recording", ""))) {
                return LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                        wavFile,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        sqlPercent,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
            }
        }
        throw new IllegalStateException("Missing WAV fixture for " + alias);
    }

    private static int canonicalLengthAtOrBefore(List<CwDecodeEvent> decodeEvents, long timestampMs) {
        String outputText = "";
        for (CwDecodeEvent event : decodeEvents) {
            if (event != null && event.timestampMs() <= timestampMs) {
                outputText = event.outputText();
            } else if (event != null && event.timestampMs() > timestampMs) {
                break;
            }
        }
        return canonicalize(outputText).length();
    }

    private static boolean[] computeActualLcsMatches(String expected, String actual) {
        int expectedLength = expected.length();
        int actualLength = actual.length();
        int[][] dp = new int[expectedLength + 1][actualLength + 1];
        for (int expectedIndex = expectedLength - 1; expectedIndex >= 0; expectedIndex--) {
            for (int actualIndex = actualLength - 1; actualIndex >= 0; actualIndex--) {
                if (expected.charAt(expectedIndex) == actual.charAt(actualIndex)) {
                    dp[expectedIndex][actualIndex] = dp[expectedIndex + 1][actualIndex + 1] + 1;
                } else {
                    dp[expectedIndex][actualIndex] = Math.max(
                            dp[expectedIndex + 1][actualIndex],
                            dp[expectedIndex][actualIndex + 1]
                    );
                }
            }
        }

        boolean[] matchedActualPositions = new boolean[actualLength];
        int expectedIndex = 0;
        int actualIndex = 0;
        while (expectedIndex < expectedLength && actualIndex < actualLength) {
            if (expected.charAt(expectedIndex) == actual.charAt(actualIndex)) {
                matchedActualPositions[actualIndex] = true;
                expectedIndex += 1;
                actualIndex += 1;
            } else if (dp[expectedIndex + 1][actualIndex] >= dp[expectedIndex][actualIndex + 1]) {
                expectedIndex += 1;
            } else {
                actualIndex += 1;
            }
        }
        return matchedActualPositions;
    }

    private static final class CharacterTimingDetail {
        private final CwDecodeEvent decodeEvent;
        private final List<CwTimingEvent> characterEvents;
        private final CwTimingEvent boundaryGapEvent;

        private CharacterTimingDetail(
                CwDecodeEvent decodeEvent,
                List<CwTimingEvent> characterEvents,
                CwTimingEvent boundaryGapEvent
        ) {
            this.decodeEvent = decodeEvent;
            this.characterEvents = characterEvents;
            this.boundaryGapEvent = boundaryGapEvent;
        }

        private List<CwTimingEvent> internalToneEvents() {
            ArrayList<CwTimingEvent> tones = new ArrayList<>();
            for (CwTimingEvent event : characterEvents) {
                if (event.kind() == CwTimingEvent.Kind.TONE) {
                    tones.add(event);
                }
            }
            return tones;
        }
    }

    private static final class ShortDitDropDecision {
        private final long toneOffTimestampMs;
        private final long durationMs;
        private final long dotEstimateMs;
        private final double ratio;
        private final String sourceSequence;
        private final int toneIndex;

        private ShortDitDropDecision(
                long toneOffTimestampMs,
                long durationMs,
                long dotEstimateMs,
                double ratio,
                String sourceSequence,
                int toneIndex
        ) {
            this.toneOffTimestampMs = toneOffTimestampMs;
            this.durationMs = durationMs;
            this.dotEstimateMs = dotEstimateMs;
            this.ratio = ratio;
            this.sourceSequence = sourceSequence;
            this.toneIndex = toneIndex;
        }

        private String render() {
            return String.format(
                    Locale.US,
                    "@%d seq=%s dropTone=t%d %dms/%.2fdot",
                    toneOffTimestampMs,
                    sourceSequence,
                    toneIndex,
                    durationMs,
                    ratio
            );
        }
    }

    private static final class GapPromotionDecision {
        private final long timestampMs;
        private final long durationMs;
        private final long dotEstimateMs;
        private final long intraGapEstimateMs;
        private final double ratio;
        private final int gapIndex;

        private GapPromotionDecision(
                long timestampMs,
                long durationMs,
                long dotEstimateMs,
                long intraGapEstimateMs,
                double ratio,
                int gapIndex
        ) {
            this.timestampMs = timestampMs;
            this.durationMs = durationMs;
            this.dotEstimateMs = dotEstimateMs;
            this.intraGapEstimateMs = intraGapEstimateMs;
            this.ratio = ratio;
            this.gapIndex = gapIndex;
        }

        private String render() {
            return String.format(
                    Locale.US,
                    "@%d gap=g%d %dms/%.2fintra dot=%d intra=%d",
                    timestampMs,
                    gapIndex,
                    durationMs,
                    ratio,
                    dotEstimateMs,
                    intraGapEstimateMs
            );
        }
    }

    private static final class TailToneTrimDecision {
        private final long timestampMs;
        private final long durationMs;
        private final long dotEstimateMs;
        private final double ratio;
        private final long precedingGapTimestampMs;
        private final String trimmedSequence;
        private final String originalSequence;

        private TailToneTrimDecision(
                long timestampMs,
                long durationMs,
                long dotEstimateMs,
                double ratio,
                long precedingGapTimestampMs,
                String trimmedSequence,
                String originalSequence
        ) {
            this.timestampMs = timestampMs;
            this.durationMs = durationMs;
            this.dotEstimateMs = dotEstimateMs;
            this.ratio = ratio;
            this.precedingGapTimestampMs = precedingGapTimestampMs;
            this.trimmedSequence = trimmedSequence;
            this.originalSequence = originalSequence;
        }

        private String render() {
            return String.format(
                    Locale.US,
                    "@%d trimTail %s -> %s prevGap=@%d %dms/%.2fdot",
                    timestampMs,
                    originalSequence,
                    trimmedSequence,
                    precedingGapTimestampMs,
                    durationMs,
                    ratio
            );
        }
    }

    private static final class TailDitRepairCandidate {
        private final long boundaryTimestampMs;
        private final String originalSequence;
        private final String trimmedSequence;
        private final long tailToneTimestampMs;
        private final long tailToneDurationMs;
        private final long tailToneDotEstimateMs;
        private final double tailToneRatio;
        private final long lastGapTimestampMs;
        private final long lastGapDurationMs;
        private final long lastGapEstimateMs;
        private final double lastGapRatio;
        private final long priorDitMedianDurationMs;
        private final double priorDitRatio;

        private TailDitRepairCandidate(
                long boundaryTimestampMs,
                String originalSequence,
                String trimmedSequence,
                long tailToneTimestampMs,
                long tailToneDurationMs,
                long tailToneDotEstimateMs,
                double tailToneRatio,
                long lastGapTimestampMs,
                long lastGapDurationMs,
                long lastGapEstimateMs,
                double lastGapRatio,
                long priorDitMedianDurationMs,
                double priorDitRatio
        ) {
            this.boundaryTimestampMs = boundaryTimestampMs;
            this.originalSequence = originalSequence;
            this.trimmedSequence = trimmedSequence;
            this.tailToneTimestampMs = tailToneTimestampMs;
            this.tailToneDurationMs = tailToneDurationMs;
            this.tailToneDotEstimateMs = tailToneDotEstimateMs;
            this.tailToneRatio = tailToneRatio;
            this.lastGapTimestampMs = lastGapTimestampMs;
            this.lastGapDurationMs = lastGapDurationMs;
            this.lastGapEstimateMs = lastGapEstimateMs;
            this.lastGapRatio = lastGapRatio;
            this.priorDitMedianDurationMs = priorDitMedianDurationMs;
            this.priorDitRatio = priorDitRatio;
        }

        private String render() {
            return String.format(
                    Locale.US,
                    "@%d %s->%s tail=@%d %dms/%.2fdot gap=@%d %dms/%.2fintra priorDit=%dms ratio=%.2f",
                    boundaryTimestampMs,
                    originalSequence,
                    trimmedSequence,
                    tailToneTimestampMs,
                    tailToneDurationMs,
                    tailToneRatio,
                    lastGapTimestampMs,
                    lastGapDurationMs,
                    lastGapRatio,
                    priorDitMedianDurationMs,
                    priorDitRatio
            );
        }
    }

    private static final class ShortDitSuppressionResult {
        private final String decodedText;
        private final List<CwToneEvent> toneEvents;
        private final List<CwTimingEvent> timingEvents;
        private final List<CwDecodeEvent> decodeEvents;
        private final List<ShortDitDropDecision> drops;

        private ShortDitSuppressionResult(
                String decodedText,
                List<CwToneEvent> toneEvents,
                List<CwTimingEvent> timingEvents,
                List<CwDecodeEvent> decodeEvents,
                List<ShortDitDropDecision> drops
        ) {
            this.decodedText = decodedText;
            this.toneEvents = toneEvents;
            this.timingEvents = timingEvents;
            this.decodeEvents = decodeEvents;
            this.drops = drops;
        }

        private String renderDropSummary() {
            if (drops.isEmpty()) {
                return "none";
            }
            StringBuilder builder = new StringBuilder();
            int limit = Math.min(6, drops.size());
            for (int index = 0; index < limit; index++) {
                if (index > 0) {
                    builder.append(" | ");
                }
                builder.append(drops.get(index).render());
            }
            return builder.toString();
        }
    }

    private static final class TimingRewriteResult {
        private final String decodedText;
        private final List<CwTimingEvent> timingEvents;
        private final List<CwDecodeEvent> decodeEvents;
        private final List<GapPromotionDecision> promotions;

        private TimingRewriteResult(
                String decodedText,
                List<CwTimingEvent> timingEvents,
                List<CwDecodeEvent> decodeEvents,
                List<GapPromotionDecision> promotions
        ) {
            this.decodedText = decodedText;
            this.timingEvents = timingEvents;
            this.decodeEvents = decodeEvents;
            this.promotions = promotions;
        }

        private String decodedText() {
            return decodedText;
        }
    }

    private static final class PrototypeCase {
        private final String alias;
        private final String expectedText;

        private PrototypeCase(String alias, String expectedText) {
            this.alias = alias;
            this.expectedText = expectedText;
        }
    }

    private static final class LocalMorseReference {
        private static final java.util.Map<String, String> TABLE = buildTable();

        private static java.util.Map<String, String> buildTable() {
            java.util.HashMap<String, String> table = new java.util.HashMap<>();
            table.put(".-", "A");
            table.put("-...", "B");
            table.put("-.-.", "C");
            table.put("-..", "D");
            table.put(".", "E");
            table.put("..-.", "F");
            table.put("--.", "G");
            table.put("....", "H");
            table.put("..", "I");
            table.put(".---", "J");
            table.put("-.-", "K");
            table.put(".-..", "L");
            table.put("--", "M");
            table.put("-.", "N");
            table.put("---", "O");
            table.put(".--.", "P");
            table.put("--.-", "Q");
            table.put(".-.", "R");
            table.put("...", "S");
            table.put("-", "T");
            table.put("..-", "U");
            table.put("...-", "V");
            table.put(".--", "W");
            table.put("-..-", "X");
            table.put("-.--", "Y");
            table.put("--..", "Z");
            table.put(".----", "1");
            table.put("..---", "2");
            table.put("...--", "3");
            table.put("....-", "4");
            table.put(".....", "5");
            table.put("-....", "6");
            table.put("--...", "7");
            table.put("---..", "8");
            table.put("----.", "9");
            table.put("-----", "0");
            table.put(".-.-.-", ".");
            table.put("--..--", ",");
            table.put("..--..", "?");
            table.put("-..-.", "/");
            table.put(".--.-.", "@");
            table.put("-...-", "=");
            return table;
        }
    }

    private static final class RawTimingCharacterCandidate {
        private final String sequence;
        private final List<CwTimingEvent> toneEvents;
        private final List<CwTimingEvent> intraGapEvents;
        private final long boundaryTimestampMs;

        private RawTimingCharacterCandidate(
                String sequence,
                List<CwTimingEvent> toneEvents,
                List<CwTimingEvent> intraGapEvents,
                long boundaryTimestampMs
        ) {
            this.sequence = sequence;
            this.toneEvents = toneEvents;
            this.intraGapEvents = intraGapEvents;
            this.boundaryTimestampMs = boundaryTimestampMs;
        }
    }
}
