package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.decoder.CwDecoder;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.qso.QsoStateMachine;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CwLocalAudioGapSoftScorePrototypeTest {
    private static final Map<String, String> MORSE_REFERENCE = buildMorseReference();
    private static final int OVERLONG_SEQUENCE_MIN_SYMBOLS = 6;
    private static final double PROMOTION_MIN_RATIO = 0.95d;
    private static final double PROMOTION_MAX_RATIO = 1.35d;
    private static final double PROMOTION_TARGET_RATIO = 1.20d;
    private static final double PROMOTION_MIN_MEDIAN_LEAD = 0.25d;

    @Test
    public void printRecording8GapSoftScorePrototypeComparison() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().contains("(8)"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for recording (8)"));
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeWavFileDetailed(wavFile);

        LinkedHashMap<String, LocalAudioDecodeTestSupport.ForcedToneReplayResult> replays = new LinkedHashMap<>();
        replays.put("TRK", LocalAudioDecodeTestSupport.replayForcedTrackedToneDecode(detailed));
        replays.put("EFF", LocalAudioDecodeTestSupport.replayForcedEffectiveTrackedToneDecode(detailed));
        replays.put("HYP", LocalAudioDecodeTestSupport.replayForcedHypothesisToneDecode(detailed));

        for (Map.Entry<String, LocalAudioDecodeTestSupport.ForcedToneReplayResult> entry : replays.entrySet()) {
            GapSoftScorePrototypeResult prototype = runGapSoftScorePrototype(entry.getValue());
            System.out.println("==== " + entry.getValue().sourceLabel() + " " + entry.getKey() + " gap-soft ====");
            System.out.println("base=" + entry.getValue().decodedText());
            System.out.println("soft=" + prototype.decodedText);
            System.out.println("promotions=" + prototype.renderPromotionSummary());
        }
    }

    private static GapSoftScorePrototypeResult runGapSoftScorePrototype(
            LocalAudioDecodeTestSupport.ForcedToneReplayResult replay
    ) {
        List<CharacterTimingDetail> characterDetails = buildCharacterTimingDetails(replay);
        ArrayList<GapPromotionDecision> promotions = new ArrayList<>();
        for (CharacterTimingDetail detail : characterDetails) {
            GapPromotionDecision decision = choosePromotion(detail);
            if (decision != null) {
                promotions.add(decision);
            }
        }

        HashMap<Long, GapPromotionDecision> promotionsByTimestamp = new HashMap<>();
        for (GapPromotionDecision promotion : promotions) {
            promotionsByTimestamp.put(promotion.timestampMs, promotion);
        }

        ArrayList<CwTimingEvent> rewrittenTimingEvents = new ArrayList<>(replay.timingEvents().size());
        for (CwTimingEvent timingEvent : replay.timingEvents()) {
            GapPromotionDecision promotion = promotionsByTimestamp.get(timingEvent.timestampMs());
            if (promotion != null
                    && timingEvent.kind() == CwTimingEvent.Kind.GAP
                    && timingEvent.classification() == CwTimingEvent.Classification.INTRA_SYMBOL_GAP) {
                rewrittenTimingEvents.add(new CwTimingEvent(
                        timingEvent.kind(),
                        CwTimingEvent.Classification.LETTER_GAP,
                        timingEvent.timestampMs(),
                        timingEvent.durationMs(),
                        timingEvent.dotEstimateMs()
                ));
            } else {
                rewrittenTimingEvents.add(timingEvent);
            }
        }

        CwDecoder decoder = new CwDecoder();
        CwInterpreter interpreter = new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);
        QsoStateMachine qsoStateMachine = new QsoStateMachine();
        ArrayList<CwDecodeEvent> decodeEvents = new ArrayList<>();
        for (CwTimingEvent timingEvent : rewrittenTimingEvents) {
            for (CwDecodeEvent decodeEvent : decoder.process(timingEvent)) {
                interpreter.process(decodeEvent);
                qsoStateMachine.process(interpreter.snapshot(), decodeEvent.timestampMs());
                decodeEvents.add(decodeEvent);
            }
        }
        return new GapSoftScorePrototypeResult(
                sanitize(decoder.snapshot().decodedText()),
                rewrittenTimingEvents,
                decodeEvents,
                promotions
        );
    }

    private static GapPromotionDecision choosePromotion(CharacterTimingDetail detail) {
        if (detail == null || !detail.decodeEvent.unknownCharacter()) {
            return null;
        }
        String sequence = detail.decodeEvent.sourceSequence();
        if (sequence == null || sequence.length() < OVERLONG_SEQUENCE_MIN_SYMBOLS) {
            return null;
        }
        List<CwTimingEvent> internalGaps = detail.internalGapEvents();
        if (internalGaps.isEmpty()) {
            return null;
        }
        if (hasSuspectTone(detail.internalToneEvents())) {
            return null;
        }
        double medianGapRatio = medianGapRatio(internalGaps);
        GapPromotionDecision best = null;
        for (int splitIndex = 1; splitIndex < sequence.length(); splitIndex++) {
            String left = sequence.substring(0, splitIndex);
            String right = sequence.substring(splitIndex);
            String leftSymbol = MORSE_REFERENCE.get(left);
            String rightSymbol = MORSE_REFERENCE.get(right);
            if (leftSymbol == null || rightSymbol == null) {
                continue;
            }
            int gapIndex = splitIndex - 1;
            if (gapIndex < 0 || gapIndex >= internalGaps.size()) {
                continue;
            }
            CwTimingEvent gapEvent = internalGaps.get(gapIndex);
            double ratio = gapEvent.durationMs() / (double) Math.max(1L, gapEvent.dotEstimateMs());
            if (ratio < PROMOTION_MIN_RATIO || ratio > PROMOTION_MAX_RATIO) {
                continue;
            }
            if ((ratio - medianGapRatio) < PROMOTION_MIN_MEDIAN_LEAD) {
                continue;
            }
            double score = 100.0d - (Math.abs(ratio - PROMOTION_TARGET_RATIO) * 25.0d);
            GapPromotionDecision candidate = new GapPromotionDecision(
                    gapEvent.timestampMs(),
                    gapEvent.durationMs(),
                    gapEvent.dotEstimateMs(),
                    ratio,
                    leftSymbol + "|" + rightSymbol,
                    left,
                    right,
                    score,
                    sequence
            );
            if (best == null || candidate.score > best.score) {
                best = candidate;
            }
        }
        return best;
    }

    private static boolean hasSuspectTone(List<CwTimingEvent> toneEvents) {
        for (CwTimingEvent toneEvent : toneEvents) {
            double ratio = toneEvent.durationMs() / (double) Math.max(1L, toneEvent.dotEstimateMs());
            if (toneEvent.classification() == CwTimingEvent.Classification.DIT) {
                if (ratio <= 0.75d || ratio >= 1.85d) {
                    return true;
                }
                continue;
            }
            if (toneEvent.classification() == CwTimingEvent.Classification.DAH && ratio >= 3.80d) {
                return true;
            }
        }
        return false;
    }

    private static double medianGapRatio(List<CwTimingEvent> internalGaps) {
        if (internalGaps.isEmpty()) {
            return 0.0d;
        }
        ArrayList<Double> ratios = new ArrayList<>(internalGaps.size());
        for (CwTimingEvent gapEvent : internalGaps) {
            ratios.add(gapEvent.durationMs() / (double) Math.max(1L, gapEvent.dotEstimateMs()));
        }
        ratios.sort(Comparator.naturalOrder());
        return ratios.get(ratios.size() / 2);
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

    private static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace('\u25A1', '?').trim();
        return normalized.isEmpty() ? "(empty)" : normalized;
    }

    private static Map<String, String> buildMorseReference() {
        HashMap<String, String> table = new HashMap<>();
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

        private List<CwTimingEvent> internalGapEvents() {
            ArrayList<CwTimingEvent> gaps = new ArrayList<>();
            for (CwTimingEvent event : characterEvents) {
                if (event.kind() == CwTimingEvent.Kind.GAP) {
                    gaps.add(event);
                }
            }
            return gaps;
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

    private static final class GapPromotionDecision {
        private final long timestampMs;
        private final long durationMs;
        private final long dotEstimateMs;
        private final double ratio;
        private final String splitLabel;
        private final String leftSequence;
        private final String rightSequence;
        private final double score;
        private final String sourceSequence;

        private GapPromotionDecision(
                long timestampMs,
                long durationMs,
                long dotEstimateMs,
                double ratio,
                String splitLabel,
                String leftSequence,
                String rightSequence,
                double score,
                String sourceSequence
        ) {
            this.timestampMs = timestampMs;
            this.durationMs = durationMs;
            this.dotEstimateMs = dotEstimateMs;
            this.ratio = ratio;
            this.splitLabel = splitLabel;
            this.leftSequence = leftSequence;
            this.rightSequence = rightSequence;
            this.score = score;
            this.sourceSequence = sourceSequence;
        }

        private String render() {
            return String.format(
                    Locale.US,
                    "@%d %s seq=%s -> %s|%s gap=%dms/%.2fdot score=%.1f",
                    timestampMs,
                    splitLabel,
                    sourceSequence,
                    leftSequence,
                    rightSequence,
                    durationMs,
                    ratio,
                    score
            );
        }
    }

    private static final class GapSoftScorePrototypeResult {
        private final String decodedText;
        private final List<CwTimingEvent> timingEvents;
        private final List<CwDecodeEvent> decodeEvents;
        private final List<GapPromotionDecision> promotions;

        private GapSoftScorePrototypeResult(
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

        private String renderPromotionSummary() {
            if (promotions.isEmpty()) {
                return "none";
            }
            ArrayList<GapPromotionDecision> sorted = new ArrayList<>(promotions);
            sorted.sort(Comparator.comparingDouble(decision -> -decision.score));
            StringBuilder builder = new StringBuilder();
            int limit = Math.min(6, sorted.size());
            for (int index = 0; index < limit; index++) {
                if (index > 0) {
                    builder.append(" | ");
                }
                builder.append(sorted.get(index).render());
            }
            return builder.toString();
        }
    }
}
