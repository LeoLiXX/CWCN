package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.decoder.CwDecoder;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.qso.QsoStateMachine;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class CwRecording7WeakDitSuppressionProbeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;
    private static final long CANDIDATE_WINDOW_START_MS = 9480L;
    private static final long CANDIDATE_WINDOW_END_MS = 9800L;
    private static final long TIMING_CANDIDATE_WINDOW_END_MS = 10948L;
    private static final long TARGET_TONE_OFF_TIMESTAMP_MS = 9632L;
    private static final double SHORT_DIT_RATIO_MAX = 0.45d;
    private static final double TIMING_SHORT_DIT_RATIO_MAX = 0.60d;
    private static final long KEY_INFO_WINDOW_START_MS = 9400L;
    private static final long KEY_INFO_WINDOW_END_MS = 11250L;
    private static final String PRIMARY_PATTERN = "DE BI3TUK KN";
    private static final String FALLBACK_PATTERN = "BI3TUK KN";

    @Test
    public void printRecording7WeakDitSuppressionProbe() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(7).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for recording (7)"));
        List<AudioFrame> frames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult liveDetailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "recording7-live",
                        frames,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult fixedDetailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeWithTurnCarry(
                        "recording7-fixed",
                        frames,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        CwSignalProcessor.RxToneMode.FIXED_TONE,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        ReplayTimeline liveRaw = buildTimeline(liveDetailed.rawDecodeEvents());
        ReplayTimeline fixedRaw = buildTimeline(fixedDetailed.rawDecodeEvents());
        List<ShortDitCandidate> candidates = collectLocalShortDitCandidates(
                liveDetailed.timingEvents(),
                CANDIDATE_WINDOW_START_MS,
                CANDIDATE_WINDOW_END_MS
        );
        ShortDitCandidate targetCandidate = findCandidateByToneOffTimestamp(
                candidates,
                TARGET_TONE_OFF_TIMESTAMP_MS
        );

        System.out.println("==== recording(7) weak-dit suppression probe ====");
        System.out.println("live-raw =" + sanitize(liveRaw.text));
        System.out.println("fixed-raw=" + sanitize(fixedRaw.text));
        System.out.println("candidates=" + renderCandidateList(candidates));
        System.out.println("target=" + (targetCandidate == null ? "missing" : targetCandidate.render()));
        System.out.println();

        printDecodedWindow("live-raw-window", liveRaw, KEY_INFO_WINDOW_START_MS, KEY_INFO_WINDOW_END_MS);
        printDecodedWindow("fixed-raw-window", fixedRaw, KEY_INFO_WINDOW_START_MS, KEY_INFO_WINDOW_END_MS);

        for (ShortDitCandidate candidate : candidates) {
            ReplayVariant variant = replayWithDroppedPulses(
                    liveDetailed.toneEvents(),
                    liveDetailed.flushTimestampMs(),
                    singleCandidateList(candidate)
            );
            ReplayTimeline variantTimeline = buildTimeline(variant.decodeEvents);
            boolean targetMatch = candidate.toneOffTimestampMs == TARGET_TONE_OFF_TIMESTAMP_MS;
            System.out.println("-- drop " + candidate.render() + (targetMatch ? " [target]" : "") + " --");
            System.out.println("raw=" + sanitize(variant.decodedText));
            System.out.println(String.format(
                    Locale.US,
                    "containsKeyInfo=%s",
                    yesNo(containsKeyInfo(variantTimeline.text))
            ));
            printDecodedWindow(
                    "variant-window",
                    variantTimeline,
                    KEY_INFO_WINDOW_START_MS,
                    KEY_INFO_WINDOW_END_MS
            );
        }

        if (!candidates.isEmpty()) {
            ReplayVariant dropAllVariant = replayWithDroppedPulses(
                    liveDetailed.toneEvents(),
                    liveDetailed.flushTimestampMs(),
                    candidates
            );
            ReplayTimeline dropAllTimeline = buildTimeline(dropAllVariant.decodeEvents);
            System.out.println("-- drop all window candidates --");
            System.out.println("raw=" + sanitize(dropAllVariant.decodedText));
            System.out.println(String.format(
                    Locale.US,
                    "containsKeyInfo=%s",
                    yesNo(containsKeyInfo(dropAllTimeline.text))
            ));
            printDecodedWindow(
                    "drop-all-window",
                    dropAllTimeline,
                    KEY_INFO_WINDOW_START_MS,
                    KEY_INFO_WINDOW_END_MS
            );
        }

        List<ShortDitCandidate> timingCandidates = collectLocalShortDitCandidates(
                liveDetailed.timingEvents(),
                CANDIDATE_WINDOW_START_MS,
                TIMING_CANDIDATE_WINDOW_END_MS,
                TIMING_SHORT_DIT_RATIO_MAX
        );
        System.out.println();
        System.out.println("timing-candidates=" + renderCandidateList(timingCandidates));
        for (List<ShortDitCandidate> combination : enumerateNonEmptyCombinations(timingCandidates)) {
            ReplayVariant timingVariant = replayWithDroppedTimingEvents(
                    liveDetailed.timingEvents(),
                    liveDetailed.flushTimestampMs(),
                    combination
            );
            ReplayTimeline timingTimeline = buildTimeline(timingVariant.decodeEvents);
            System.out.println("-- timing-drop " + renderCandidateList(combination) + " --");
            System.out.println("raw=" + sanitize(timingVariant.decodedText));
            System.out.println(String.format(
                    Locale.US,
                    "containsKeyInfo=%s",
                    yesNo(containsKeyInfo(timingTimeline.text))
            ));
            printDecodedWindow(
                    "timing-window",
                    timingTimeline,
                    KEY_INFO_WINDOW_START_MS,
                    KEY_INFO_WINDOW_END_MS
            );
        }

        assertTrue(true);
        assertNotNull(targetCandidate);
    }

    private static List<ShortDitCandidate> singleCandidateList(ShortDitCandidate candidate) {
        ArrayList<ShortDitCandidate> candidates = new ArrayList<>();
        candidates.add(candidate);
        return candidates;
    }

    private static ReplayVariant replayWithDroppedPulses(
            List<CwToneEvent> sourceToneEvents,
            long flushTimestampMs,
            List<ShortDitCandidate> drops
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

        List<CwTimingEvent> flushedTimingEvents = timingModel.flushPendingGap(flushTimestampMs);
        rewrittenTimingEvents.addAll(flushedTimingEvents);
        drainTimingEvents(flushedTimingEvents, decoder, interpreter, qsoStateMachine, rewrittenDecodeEvents);
        drainDecodeEvents(
                decoder.flushPendingCharacter(flushTimestampMs),
                interpreter,
                qsoStateMachine,
                rewrittenDecodeEvents
        );

        return new ReplayVariant(
                sanitize(decoder.snapshot().decodedText()),
                rewrittenToneEvents,
                rewrittenTimingEvents,
                rewrittenDecodeEvents
        );
    }

    private static ReplayVariant replayWithDroppedTimingEvents(
            List<CwTimingEvent> sourceTimingEvents,
            long flushTimestampMs,
            List<ShortDitCandidate> drops
    ) {
        List<CwTimingEvent> rewrittenTimingEvents = dropTimingSymbols(sourceTimingEvents, drops);

        CwDecoder decoder = new CwDecoder();
        CwInterpreter interpreter = new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);
        QsoStateMachine qsoStateMachine = new QsoStateMachine();
        ArrayList<CwDecodeEvent> rewrittenDecodeEvents = new ArrayList<>();

        drainTimingEvents(rewrittenTimingEvents, decoder, interpreter, qsoStateMachine, rewrittenDecodeEvents);
        drainDecodeEvents(
                decoder.flushPendingCharacter(flushTimestampMs),
                interpreter,
                qsoStateMachine,
                rewrittenDecodeEvents
        );

        return new ReplayVariant(
                sanitize(decoder.snapshot().decodedText()),
                new ArrayList<CwToneEvent>(),
                rewrittenTimingEvents,
                rewrittenDecodeEvents
        );
    }

    private static List<CwToneEvent> dropTonePulses(
            List<CwToneEvent> toneEvents,
            List<ShortDitCandidate> drops
    ) {
        if (drops == null || drops.isEmpty()) {
            return new ArrayList<>(toneEvents);
        }
        ArrayList<CwToneEvent> rewritten = new ArrayList<>();
        int dropIndex = 0;
        ShortDitCandidate activeDrop = drops.get(dropIndex);
        boolean skippingPulse = false;
        for (CwToneEvent toneEvent : toneEvents) {
            while (activeDrop != null
                    && toneEvent.timestampMs() > activeDrop.toneOffTimestampMs
                    && !skippingPulse) {
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

    private static List<CwTimingEvent> dropTimingSymbols(
            List<CwTimingEvent> timingEvents,
            List<ShortDitCandidate> drops
    ) {
        if (drops == null || drops.isEmpty()) {
            return new ArrayList<>(timingEvents);
        }
        ArrayList<CwTimingEvent> rewritten = new ArrayList<>();
        for (CwTimingEvent timingEvent : timingEvents) {
            if (shouldDropTimingEvent(timingEvent, drops)) {
                continue;
            }
            rewritten.add(timingEvent);
        }
        return rewritten;
    }

    private static boolean shouldDropTimingEvent(
            CwTimingEvent timingEvent,
            List<ShortDitCandidate> drops
    ) {
        if (timingEvent == null
                || timingEvent.kind() != CwTimingEvent.Kind.TONE
                || timingEvent.classification() != CwTimingEvent.Classification.DIT) {
            return false;
        }
        for (ShortDitCandidate candidate : drops) {
            if (candidate.toneOffTimestampMs == timingEvent.timestampMs()
                    && candidate.durationMs == timingEvent.durationMs()) {
                return true;
            }
        }
        return false;
    }

    private static void drainTimingEvents(
            List<CwTimingEvent> timingEvents,
            CwDecoder decoder,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            List<CwDecodeEvent> capturedDecodeEvents
    ) {
        for (CwTimingEvent timingEvent : timingEvents) {
            List<CwDecodeEvent> decodeEvents = decoder.process(timingEvent);
            drainDecodeEvents(decodeEvents, interpreter, qsoStateMachine, capturedDecodeEvents);
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

    private static List<ShortDitCandidate> collectLocalShortDitCandidates(
            List<CwTimingEvent> timingEvents,
            long windowStartMs,
            long windowEndMs
    ) {
        return collectLocalShortDitCandidates(timingEvents, windowStartMs, windowEndMs, SHORT_DIT_RATIO_MAX);
    }

    private static List<ShortDitCandidate> collectLocalShortDitCandidates(
            List<CwTimingEvent> timingEvents,
            long windowStartMs,
            long windowEndMs,
            double ratioMax
    ) {
        ArrayList<ShortDitCandidate> candidates = new ArrayList<>();
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
            if (ratio > ratioMax) {
                continue;
            }
            toneIndexWithinWindow += 1;
            candidates.add(new ShortDitCandidate(
                    timingEvent.timestampMs(),
                    timingEvent.durationMs(),
                    timingEvent.dotEstimateMs(),
                    ratio,
                    toneIndexWithinWindow
            ));
        }
        return candidates;
    }

    private static ShortDitCandidate findCandidateByToneOffTimestamp(
            List<ShortDitCandidate> candidates,
            long toneOffTimestampMs
    ) {
        for (ShortDitCandidate candidate : candidates) {
            if (candidate.toneOffTimestampMs == toneOffTimestampMs) {
                return candidate;
            }
        }
        return null;
    }

    private static List<List<ShortDitCandidate>> enumerateNonEmptyCombinations(
            List<ShortDitCandidate> candidates
    ) {
        ArrayList<List<ShortDitCandidate>> combinations = new ArrayList<>();
        int size = candidates.size();
        if (size <= 0 || size >= 31) {
            return combinations;
        }
        int maxMask = 1 << size;
        for (int mask = 1; mask < maxMask; mask++) {
            ArrayList<ShortDitCandidate> combination = new ArrayList<>();
            for (int index = 0; index < size; index++) {
                if ((mask & (1 << index)) != 0) {
                    combination.add(candidates.get(index));
                }
            }
            combinations.add(combination);
        }
        return combinations;
    }

    private static boolean containsKeyInfo(String text) {
        return text != null && (text.contains(PRIMARY_PATTERN) || text.contains(FALLBACK_PATTERN));
    }

    private static ReplayTimeline buildTimeline(List<CwDecodeEvent> decodeEvents) {
        ArrayList<DecodedCharStamp> decodedChars = new ArrayList<>();
        StringBuilder textBuilder = new StringBuilder();
        String previousOutputText = "";
        for (CwDecodeEvent event : decodeEvents) {
            if (event == null) {
                continue;
            }
            String currentOutputText = normalizeOutputText(event.outputText());
            String appendedText = "";
            if (currentOutputText.startsWith(previousOutputText)) {
                appendedText = currentOutputText.substring(previousOutputText.length());
            } else if (event.type() != CwDecodeEvent.Type.SYMBOL_APPENDED) {
                appendedText = fallbackAppendedText(event);
            }
            if (!appendedText.isEmpty()) {
                for (int index = 0; index < appendedText.length(); index++) {
                    char value = appendedText.charAt(index);
                    decodedChars.add(new DecodedCharStamp(
                            value,
                            event.timestampMs(),
                            event.type(),
                            normalizeNullable(event.emittedValue()),
                            normalizeNullable(event.sourceSequence()),
                            event.unknownCharacter()
                    ));
                    textBuilder.append(value);
                }
            }
            if (event.type() != CwDecodeEvent.Type.SYMBOL_APPENDED) {
                previousOutputText = currentOutputText;
            }
        }
        return new ReplayTimeline(textBuilder.toString(), decodedChars);
    }

    private static void printDecodedWindow(
            String label,
            ReplayTimeline timeline,
            long startTimestampMs,
            long endTimestampMs
    ) {
        System.out.println("-- " + label + " --");
        ArrayList<DecodedCharStamp> window = new ArrayList<>();
        for (DecodedCharStamp stamp : timeline.decodedChars) {
            if (stamp.timestampMs >= startTimestampMs && stamp.timestampMs <= endTimestampMs) {
                window.add(stamp);
            }
        }
        if (window.isEmpty()) {
            System.out.println("text=(empty)");
            System.out.println("chars=none");
            return;
        }
        StringBuilder text = new StringBuilder();
        StringBuilder detail = new StringBuilder();
        for (DecodedCharStamp stamp : window) {
            text.append(stamp.value);
            if (detail.length() > 0) {
                detail.append(" | ");
            }
            detail.append('@').append(stamp.timestampMs)
                    .append(':').append(stamp.value == ' ' ? "<sp>" : stamp.value)
                    .append('/').append(stamp.type)
                    .append("/emit=").append(stamp.emittedValue)
                    .append("/seq=").append(stamp.sourceSequence)
                    .append("/unk=").append(yesNo(stamp.unknownCharacter));
        }
        System.out.println("text=" + sanitize(text.toString()));
        System.out.println("chars=" + detail);
    }

    private static String renderCandidateList(List<ShortDitCandidate> candidates) {
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

    private static String fallbackAppendedText(CwDecodeEvent event) {
        if (event.type() == CwDecodeEvent.Type.WORD_BREAK) {
            return " ";
        }
        if (event.emittedValue() != null && !event.emittedValue().isEmpty()) {
            return normalizeOutputText(event.emittedValue());
        }
        return event.unknownCharacter() ? "?" : "";
    }

    private static String normalizeOutputText(String value) {
        return value == null ? "" : value.replace('\u25A1', '?');
    }

    private static String normalizeNullable(String value) {
        return value == null ? "(null)" : value.replace('\u25A1', '?');
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "(empty)";
        }
        String normalized = text.replace('\u25A1', '?').replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.isEmpty() ? "(empty)" : normalized;
    }

    private static String yesNo(boolean value) {
        return value ? "Y" : "N";
    }

    private static final class ReplayVariant {
        private final String decodedText;
        private final List<CwToneEvent> toneEvents;
        private final List<CwTimingEvent> timingEvents;
        private final List<CwDecodeEvent> decodeEvents;

        private ReplayVariant(
                String decodedText,
                List<CwToneEvent> toneEvents,
                List<CwTimingEvent> timingEvents,
                List<CwDecodeEvent> decodeEvents
        ) {
            this.decodedText = decodedText;
            this.toneEvents = toneEvents;
            this.timingEvents = timingEvents;
            this.decodeEvents = decodeEvents;
        }
    }

    private static final class ShortDitCandidate {
        private final long toneOffTimestampMs;
        private final long durationMs;
        private final long dotEstimateMs;
        private final double ratio;
        private final int toneIndex;

        private ShortDitCandidate(
                long toneOffTimestampMs,
                long durationMs,
                long dotEstimateMs,
                double ratio,
                int toneIndex
        ) {
            this.toneOffTimestampMs = toneOffTimestampMs;
            this.durationMs = durationMs;
            this.dotEstimateMs = dotEstimateMs;
            this.ratio = ratio;
            this.toneIndex = toneIndex;
        }

        private String render() {
            return String.format(
                    Locale.US,
                    "@%d t%d %dms/%.2fdot",
                    toneOffTimestampMs,
                    toneIndex,
                    durationMs,
                    ratio
            );
        }
    }

    private static final class ReplayTimeline {
        private final String text;
        private final List<DecodedCharStamp> decodedChars;

        private ReplayTimeline(String text, List<DecodedCharStamp> decodedChars) {
            this.text = text;
            this.decodedChars = decodedChars;
        }
    }

    private static final class DecodedCharStamp {
        private final char value;
        private final long timestampMs;
        private final CwDecodeEvent.Type type;
        private final String emittedValue;
        private final String sourceSequence;
        private final boolean unknownCharacter;

        private DecodedCharStamp(
                char value,
                long timestampMs,
                CwDecodeEvent.Type type,
                String emittedValue,
                String sourceSequence,
                boolean unknownCharacter
        ) {
            this.value = value;
            this.timestampMs = timestampMs;
            this.type = type;
            this.emittedValue = emittedValue;
            this.sourceSequence = sourceSequence;
            this.unknownCharacter = unknownCharacter;
        }
    }
}
