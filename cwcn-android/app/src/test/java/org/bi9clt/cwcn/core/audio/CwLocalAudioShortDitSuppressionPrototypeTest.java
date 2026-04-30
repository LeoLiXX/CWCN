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
    private static final int OVERLONG_SEQUENCE_MIN_SYMBOLS = 6;
    private static final double SHORT_DIT_RATIO_MAX = 0.45d;

    @Test
    public void printRecording8ShortDitSuppressionPrototypeComparison() throws Exception {
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
            ShortDitSuppressionResult prototype = runShortDitSuppressionPrototype(entry.getValue());
            System.out.println("==== " + entry.getValue().sourceLabel() + " " + entry.getKey() + " short-dit ====");
            System.out.println("base=" + entry.getValue().decodedText());
            System.out.println("soft=" + prototype.decodedText);
            System.out.println("drops=" + prototype.renderDropSummary());
        }
    }

    private static ShortDitSuppressionResult runShortDitSuppressionPrototype(
            LocalAudioDecodeTestSupport.ForcedToneReplayResult replay
    ) {
        List<CharacterTimingDetail> details = buildCharacterTimingDetails(replay);
        List<ShortDitDropDecision> drops = chooseShortDitDrops(details);
        List<CwToneEvent> rewrittenToneEvents = dropTonePulses(replay.toneEvents(), drops);

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

        List<CwTimingEvent> flushedTimingEvents = timingModel.flushPendingGap(lastTimestamp(replay.toneEvents()));
        rewrittenTimingEvents.addAll(flushedTimingEvents);
        drainTimingEvents(flushedTimingEvents, decoder, interpreter, qsoStateMachine, rewrittenDecodeEvents);
        drainDecodeEvents(
                decoder.flushPendingCharacter(lastTimestamp(replay.toneEvents())),
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

    private static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace('\u25A1', '?').trim();
        return normalized.isEmpty() ? "(empty)" : normalized;
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
}
