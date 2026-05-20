package org.bi9clt.cwcn.core.rx;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.decoder.CwDecoder;
import org.bi9clt.cwcn.core.decoder.CwDecoderSnapshot;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.interpreter.CwInterpreterSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Conservative tail repair by fresh re-decode of the trailing window.
 *
 * <p>The live RX chain can end a turn with slightly biased tail timing even
 * after the opening and mid-turn have already stabilized. This helper keeps
 * the repair policy narrow: only re-decode the trailing words from original
 * tone events, and only adopt the candidate when it strictly reduces unknowns
 * without shrinking the visible tail.</p>
 */
public final class RxTrailingWindowRepair {
    private RxTrailingWindowRepair() {
    }

    public static RedecodeResult redecodeTrailingWords(
            List<CwToneEvent> toneEvents,
            List<CwDecodeEvent> baseDecodeEvents,
            long flushTimestampMs,
            int trailingWordCount
    ) {
        if (toneEvents == null) {
            throw new IllegalArgumentException("toneEvents == null");
        }
        if (baseDecodeEvents == null) {
            throw new IllegalArgumentException("baseDecodeEvents == null");
        }
        if (trailingWordCount <= 0) {
            throw new IllegalArgumentException("trailingWordCount must be > 0");
        }

        long windowStartTimestampMs = trailingWordWindowStartTimestampMs(
                baseDecodeEvents,
                trailingWordCount
        );
        CwHybridTimingModel timingModel = new CwHybridTimingModel();
        CwDecoder decoder = new CwDecoder();
        CwInterpreter interpreter = new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);
        ArrayList<CwTimingEvent> replayedTimingEvents = new ArrayList<>();
        ArrayList<CwDecodeEvent> replayedDecodeEvents = new ArrayList<>();
        RxTimingDecodeRunner timingDecodeRunner = new RxTimingDecodeRunner(decoder);
        RxToneTimingRunner toneTimingRunner = new RxToneTimingRunner(timingDecodeRunner);
        RxTimingDecodeRunner.DecodeEventConsumer decodeEventConsumer =
                replayedDecodeEventConsumer(interpreter, replayedDecodeEvents);

        toneTimingRunner.dispatchToneEvents(
                toneEvents,
                toneEvent -> {
                    if (toneEvent == null || toneEvent.timestampMs() < windowStartTimestampMs) {
                        return Collections.emptyList();
                    }
                    return timingModel.process(toneEvent);
                },
                (toneEvent, timingEvents) -> replayedTimingEvents.addAll(timingEvents),
                null,
                decodeEventConsumer
        );

        List<CwTimingEvent> flushedTimingEvents = timingModel.flushPendingGap(flushTimestampMs);
        replayedTimingEvents.addAll(flushedTimingEvents);
        timingDecodeRunner.dispatchTimingEvents(
                flushedTimingEvents,
                null,
                decodeEventConsumer
        );
        timingDecodeRunner.flushPendingCharacter(
                flushTimestampMs,
                decodeEventConsumer
        );

        return new RedecodeResult(
                trailingWordCount,
                windowStartTimestampMs,
                sanitize(decoder.snapshot().decodedText()),
                timingModel.snapshot(),
                decoder.snapshot(),
                interpreter.snapshot(),
                replayedTimingEvents,
                replayedDecodeEvents
        );
    }

    public static RepairResult repairTrailingWordsIfBeneficial(
            List<CwToneEvent> toneEvents,
            List<CwDecodeEvent> baseDecodeEvents,
            long flushTimestampMs,
            int trailingWordCount
    ) {
        if (toneEvents == null
                || toneEvents.isEmpty()
                || baseDecodeEvents == null
                || baseDecodeEvents.isEmpty()) {
            return null;
        }

        RedecodeResult redecodeResult = redecodeTrailingWords(
                toneEvents,
                baseDecodeEvents,
                flushTimestampMs,
                trailingWordCount
        );
        if (!shouldUseTrailingWindowRedecodeRepair(baseDecodeEvents, redecodeResult)) {
            return null;
        }

        List<CwDecodeEvent> repairedDecodeEvents = buildRepairedDecodeEvents(
                baseDecodeEvents,
                redecodeResult
        );
        if (repairedDecodeEvents.isEmpty()) {
            return null;
        }

        return new RepairResult(
                redecodeResult,
                repairedDecodeEvents,
                sanitize(lastOutputText(repairedDecodeEvents)),
                trailingTextFromWindow(baseDecodeEvents, redecodeResult.windowStartTimestampMs()),
                trailingTextFromWindow(repairedDecodeEvents, redecodeResult.windowStartTimestampMs())
        );
    }

    private static RxTimingDecodeRunner.DecodeEventConsumer replayedDecodeEventConsumer(
            CwInterpreter interpreter,
            List<CwDecodeEvent> capturedDecodeEvents
    ) {
        return decodeEvent -> {
            if (decodeEvent == null) {
                return;
            }
            interpreter.process(decodeEvent);
            capturedDecodeEvents.add(decodeEvent);
        };
    }

    private static boolean shouldUseTrailingWindowRedecodeRepair(
            List<CwDecodeEvent> baseDecodeEvents,
            RedecodeResult redecodeResult
    ) {
        if (baseDecodeEvents == null
                || redecodeResult == null
                || redecodeResult.windowStartTimestampMs() < 0L) {
            return false;
        }
        String baseTailText = trailingTextFromWindow(
                baseDecodeEvents,
                redecodeResult.windowStartTimestampMs()
        );
        String candidateTailText = sanitize(redecodeResult.decodedText());
        if (baseTailText.isEmpty()
                || candidateTailText.isEmpty()
                || baseTailText.equals(candidateTailText)) {
            return false;
        }
        int baseUnknownCount = countCharacter(baseTailText, '?');
        int candidateUnknownCount = countCharacter(candidateTailText, '?');
        if (baseUnknownCount <= 0 || candidateUnknownCount >= baseUnknownCount) {
            return false;
        }
        if (countWordTokens(candidateTailText) < countWordTokens(baseTailText)) {
            return false;
        }
        return countAlphaNumeric(candidateTailText) >= countAlphaNumeric(baseTailText);
    }

    private static List<CwDecodeEvent> buildRepairedDecodeEvents(
            List<CwDecodeEvent> baseDecodeEvents,
            RedecodeResult redecodeResult
    ) {
        if (baseDecodeEvents == null || baseDecodeEvents.isEmpty() || redecodeResult == null) {
            return Collections.emptyList();
        }
        long windowStartTimestampMs = redecodeResult.windowStartTimestampMs();
        ArrayList<CwDecodeEvent> repairedDecodeEvents = new ArrayList<>();
        String prefixText = "";
        for (CwDecodeEvent decodeEvent : baseDecodeEvents) {
            if (decodeEvent == null) {
                continue;
            }
            if (decodeEvent.timestampMs() >= windowStartTimestampMs) {
                break;
            }
            repairedDecodeEvents.add(decodeEvent);
            prefixText = safeValue(decodeEvent.outputText());
        }
        for (CwDecodeEvent decodeEvent : redecodeResult.decodeEvents()) {
            if (decodeEvent == null) {
                continue;
            }
            repairedDecodeEvents.add(new CwDecodeEvent(
                    decodeEvent.type(),
                    decodeEvent.timestampMs(),
                    decodeEvent.currentSequence(),
                    stitchOutputText(prefixText, decodeEvent.outputText()),
                    decodeEvent.emittedValue(),
                    decodeEvent.sourceSequence(),
                    decodeEvent.unknownCharacter()
            ));
        }
        return repairedDecodeEvents;
    }

    private static String stitchOutputText(String prefixText, String trailingOutputText) {
        String safePrefixText = safeValue(prefixText).stripTrailing();
        String safeTrailingOutputText = safeValue(trailingOutputText).stripLeading();
        if (safePrefixText.isEmpty()) {
            return safeTrailingOutputText;
        }
        if (safeTrailingOutputText.isEmpty()) {
            return safePrefixText;
        }
        if (safePrefixText.endsWith(" ") || safeTrailingOutputText.startsWith(" ")) {
            return safePrefixText + safeTrailingOutputText;
        }
        return safePrefixText + " " + safeTrailingOutputText;
    }

    private static String trailingTextFromWindow(
            List<CwDecodeEvent> decodeEvents,
            long windowStartTimestampMs
    ) {
        if (decodeEvents == null || decodeEvents.isEmpty()) {
            return "";
        }
        String prefixText = "";
        String finalText = "";
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            if (decodeEvent == null) {
                continue;
            }
            String outputText = safeValue(decodeEvent.outputText());
            if (decodeEvent.timestampMs() < windowStartTimestampMs) {
                prefixText = outputText;
                continue;
            }
            finalText = outputText;
        }
        String safePrefixText = sanitize(prefixText);
        String safeFinalText = sanitize(finalText);
        if (safeFinalText.isEmpty()) {
            return "";
        }
        if (safePrefixText.isEmpty()) {
            return safeFinalText;
        }
        if (safeFinalText.startsWith(safePrefixText)) {
            return safeFinalText.substring(safePrefixText.length()).trim();
        }
        return safeFinalText;
    }

    private static long trailingWordWindowStartTimestampMs(
            List<CwDecodeEvent> decodeEvents,
            int trailingWordCount
    ) {
        if (decodeEvents == null || decodeEvents.isEmpty()) {
            return 0L;
        }
        int endIndex = decodeEvents.size() - 1;
        while (endIndex >= 0 && decodeEvents.get(endIndex).type() == CwDecodeEvent.Type.WORD_BREAK) {
            endIndex -= 1;
        }
        if (endIndex < 0) {
            return decodeEvents.get(0).timestampMs();
        }

        int remainingWordBreaks = trailingWordCount;
        for (int index = endIndex; index >= 0; index--) {
            CwDecodeEvent decodeEvent = decodeEvents.get(index);
            if (decodeEvent.type() != CwDecodeEvent.Type.WORD_BREAK) {
                continue;
            }
            remainingWordBreaks -= 1;
            if (remainingWordBreaks == 0) {
                return decodeEvent.timestampMs();
            }
        }
        return decodeEvents.get(0).timestampMs();
    }

    private static int countCharacter(String text, char target) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == target) {
                count += 1;
            }
        }
        return count;
    }

    private static int countWordTokens(String text) {
        if (text == null) {
            return 0;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }
        return trimmed.split("\\s+").length;
    }

    private static int countAlphaNumeric(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int index = 0; index < text.length(); index++) {
            if (Character.isLetterOrDigit(text.charAt(index))) {
                count += 1;
            }
        }
        return count;
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace('\u25A1', '?').trim();
        return normalized.isEmpty() ? "" : normalized;
    }

    private static String safeValue(String value) {
        return value == null ? "" : value;
    }

    private static String lastOutputText(List<CwDecodeEvent> decodeEvents) {
        if (decodeEvents == null || decodeEvents.isEmpty()) {
            return "";
        }
        for (int index = decodeEvents.size() - 1; index >= 0; index--) {
            CwDecodeEvent decodeEvent = decodeEvents.get(index);
            if (decodeEvent != null) {
                return safeValue(decodeEvent.outputText());
            }
        }
        return "";
    }

    public static final class RedecodeResult {
        private final int trailingWordCount;
        private final long windowStartTimestampMs;
        private final String decodedText;
        private final CwTimingSnapshot timingSnapshot;
        private final CwDecoderSnapshot decoderSnapshot;
        private final CwInterpreterSnapshot interpreterSnapshot;
        private final List<CwTimingEvent> timingEvents;
        private final List<CwDecodeEvent> decodeEvents;

        private RedecodeResult(
                int trailingWordCount,
                long windowStartTimestampMs,
                String decodedText,
                CwTimingSnapshot timingSnapshot,
                CwDecoderSnapshot decoderSnapshot,
                CwInterpreterSnapshot interpreterSnapshot,
                List<CwTimingEvent> timingEvents,
                List<CwDecodeEvent> decodeEvents
        ) {
            this.trailingWordCount = trailingWordCount;
            this.windowStartTimestampMs = windowStartTimestampMs;
            this.decodedText = safeValue(decodedText);
            this.timingSnapshot = timingSnapshot;
            this.decoderSnapshot = decoderSnapshot;
            this.interpreterSnapshot = interpreterSnapshot;
            this.timingEvents = Collections.unmodifiableList(new ArrayList<>(timingEvents));
            this.decodeEvents = Collections.unmodifiableList(new ArrayList<>(decodeEvents));
        }

        public int trailingWordCount() {
            return trailingWordCount;
        }

        public long windowStartTimestampMs() {
            return windowStartTimestampMs;
        }

        public String decodedText() {
            return decodedText;
        }

        public CwTimingSnapshot timingSnapshot() {
            return timingSnapshot;
        }

        public CwDecoderSnapshot decoderSnapshot() {
            return decoderSnapshot;
        }

        public CwInterpreterSnapshot interpreterSnapshot() {
            return interpreterSnapshot;
        }

        public List<CwTimingEvent> timingEvents() {
            return timingEvents;
        }

        public List<CwDecodeEvent> decodeEvents() {
            return decodeEvents;
        }
    }

    public static final class RepairResult {
        private final RedecodeResult redecodeResult;
        private final List<CwDecodeEvent> repairedDecodeEvents;
        private final String repairedText;
        private final String baseTailText;
        private final String repairedTailText;

        private RepairResult(
                RedecodeResult redecodeResult,
                List<CwDecodeEvent> repairedDecodeEvents,
                String repairedText,
                String baseTailText,
                String repairedTailText
        ) {
            this.redecodeResult = redecodeResult;
            this.repairedDecodeEvents = Collections.unmodifiableList(new ArrayList<>(repairedDecodeEvents));
            this.repairedText = safeValue(repairedText);
            this.baseTailText = safeValue(baseTailText);
            this.repairedTailText = safeValue(repairedTailText);
        }

        public RedecodeResult redecodeResult() {
            return redecodeResult;
        }

        public List<CwDecodeEvent> repairedDecodeEvents() {
            return repairedDecodeEvents;
        }

        public String repairedText() {
            return repairedText;
        }

        public String baseTailText() {
            return baseTailText;
        }

        public String repairedTailText() {
            return repairedTailText;
        }
    }
}
