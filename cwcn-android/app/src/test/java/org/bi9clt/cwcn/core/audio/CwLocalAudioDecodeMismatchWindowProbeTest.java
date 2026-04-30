package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
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

public final class CwLocalAudioDecodeMismatchWindowProbeTest {
    private static final long WINDOW_PADDING_MS = 900L;
    private static final int UNKNOWN_CONTEXT_CHARS = 6;
    private static final Map<String, String> MORSE_REFERENCE = buildMorseReference();
    private static final String[] TARGET_PATTERNS = new String[]{
            "? CQ",
            "?G1XXX",
            "?A1ABC",
            "R??E",
            "S??E",
            "BG1YXA",
            "BG1XXX"
    };
    private static final ComparisonGroup[] COMPARISON_GROUPS = new ComparisonGroup[]{
            new ComparisonGroup("opening-cq", true, "? CQ"),
            new ComparisonGroup("questioned-bg1xxx", true, "?G1XXX"),
            new ComparisonGroup("questioned-ja1abc", true, "?A1ABC"),
            new ComparisonGroup("tail-q-fragment", false, "R??E", "S??E"),
            new ComparisonGroup("tail-call", false, "BG1YXA", "BG1XXX")
    };
    private static final int OVERLONG_SEQUENCE_MIN_SYMBOLS = 6;

    @Test
    public void printRecording8MismatchWindowsAcrossForcedModes() throws Exception {
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

        printCrossModeComparison(replays);
        for (Map.Entry<String, LocalAudioDecodeTestSupport.ForcedToneReplayResult> entry : replays.entrySet()) {
            printReplayProbe(entry.getKey(), entry.getValue());
        }
    }

    private static void printCrossModeComparison(
            LinkedHashMap<String, LocalAudioDecodeTestSupport.ForcedToneReplayResult> replays
    ) {
        LinkedHashMap<String, ReplayTimeline> timelines = new LinkedHashMap<>();
        LinkedHashMap<String, List<CharacterTimingDetail>> characterDetails = new LinkedHashMap<>();
        for (Map.Entry<String, LocalAudioDecodeTestSupport.ForcedToneReplayResult> entry : replays.entrySet()) {
            timelines.put(entry.getKey(), buildTimeline(entry.getValue()));
            characterDetails.put(entry.getKey(), buildCharacterTimingDetails(entry.getValue()));
        }

        System.out.println("==== cross-mode mismatch summary ====");
        for (ComparisonGroup group : COMPARISON_GROUPS) {
            System.out.println("-- compare[" + group.label + "] --");
            for (Map.Entry<String, LocalAudioDecodeTestSupport.ForcedToneReplayResult> entry : replays.entrySet()) {
                String mode = entry.getKey();
                ReplayTimeline timeline = timelines.get(mode);
                WindowMatch match = findPreferredWindowMatch(timeline, group);
                if (match == null) {
                    System.out.println(mode + ": not-found");
                    continue;
                }
                System.out.println(
                        mode
                                + ": pattern=" + match.pattern
                                + " chars=" + match.startIndex + ".." + (match.endExclusive - 1)
                                + " ts=" + match.firstTimestampMs + ".." + match.lastTimestampMs
                                + " snippet=" + match.snippet
                                + " context=" + match.context
                );
                System.out.println(
                        mode + ": decode="
                                + renderDecodedCharWindow(timeline.decodedChars, match.startIndex, match.endExclusive)
                );
                System.out.println(
                        mode + ": unknown-analysis="
                                + renderUnknownAnalysis(
                                characterDetails.get(mode),
                                timeline.decodedChars,
                                match.startIndex,
                                match.endExclusive
                        )
                );
            }
        }
        System.out.println("-- anomaly-tone-summary --");
        for (Map.Entry<String, List<CharacterTimingDetail>> entry : characterDetails.entrySet()) {
            System.out.println(entry.getKey() + ": " + renderAnomalyToneSummary(entry.getValue()));
        }
    }

    private static void printReplayProbe(
            String label,
            LocalAudioDecodeTestSupport.ForcedToneReplayResult replay
    ) {
        ReplayTimeline timeline = buildTimeline(replay);
        System.out.println("==== " + replay.sourceLabel() + " " + label + " mismatch probe ====");
        System.out.println(replay.renderSummary());
        System.out.println("text=" + timeline.text);

        for (String pattern : TARGET_PATTERNS) {
            printPatternWindows(replay, timeline, pattern);
        }
        printUnknownNeighborhoods(replay, timeline);
    }

    private static ReplayTimeline buildTimeline(LocalAudioDecodeTestSupport.ForcedToneReplayResult replay) {
        ArrayList<DecodedCharStamp> decodedChars = new ArrayList<>();
        StringBuilder textBuilder = new StringBuilder();
        String previousOutputText = "";
        for (CwDecodeEvent event : replay.decodeEvents()) {
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

    private static void printPatternWindows(
            LocalAudioDecodeTestSupport.ForcedToneReplayResult replay,
            ReplayTimeline timeline,
            String pattern
    ) {
        int occurrenceCount = 0;
        int searchFrom = 0;
        while (searchFrom >= 0 && searchFrom < timeline.text.length()) {
            int matchIndex = timeline.text.indexOf(pattern, searchFrom);
            if (matchIndex < 0) {
                break;
            }
            occurrenceCount += 1;
            printWindow(replay, timeline, "pattern[" + pattern + "]#" + occurrenceCount, matchIndex, pattern.length());
            searchFrom = matchIndex + 1;
        }
        if (occurrenceCount == 0) {
            System.out.println("pattern[" + pattern + "]=not-found");
        }
    }

    private static WindowMatch findPreferredWindowMatch(ReplayTimeline timeline, ComparisonGroup group) {
        WindowMatch selected = null;
        for (String pattern : group.patterns) {
            List<WindowMatch> matches = findPatternMatches(timeline, pattern);
            if (matches.isEmpty()) {
                continue;
            }
            WindowMatch candidate = group.pickFirst ? matches.get(0) : matches.get(matches.size() - 1);
            if (selected == null) {
                selected = candidate;
                continue;
            }
            if (group.pickFirst) {
                if (candidate.startIndex < selected.startIndex) {
                    selected = candidate;
                }
            } else if (candidate.startIndex > selected.startIndex) {
                selected = candidate;
            }
        }
        return selected;
    }

    private static List<WindowMatch> findPatternMatches(ReplayTimeline timeline, String pattern) {
        ArrayList<WindowMatch> matches = new ArrayList<>();
        int searchFrom = 0;
        while (searchFrom >= 0 && searchFrom < timeline.text.length()) {
            int matchIndex = timeline.text.indexOf(pattern, searchFrom);
            if (matchIndex < 0) {
                break;
            }
            int endExclusive = matchIndex + pattern.length();
            if (endExclusive <= timeline.decodedChars.size()) {
                DecodedCharStamp first = timeline.decodedChars.get(matchIndex);
                DecodedCharStamp last = timeline.decodedChars.get(endExclusive - 1);
                matches.add(new WindowMatch(
                        pattern,
                        matchIndex,
                        endExclusive,
                        first.timestampMs,
                        last.timestampMs,
                        timeline.text.substring(matchIndex, endExclusive),
                        renderContext(timeline.text, matchIndex, endExclusive)
                ));
            }
            searchFrom = matchIndex + 1;
        }
        return matches;
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

    private static void printUnknownNeighborhoods(
            LocalAudioDecodeTestSupport.ForcedToneReplayResult replay,
            ReplayTimeline timeline
    ) {
        ArrayList<CharWindow> windows = new ArrayList<>();
        for (int index = 0; index < timeline.text.length(); index++) {
            if (timeline.text.charAt(index) != '?') {
                continue;
            }
            int startIndex = Math.max(0, index - UNKNOWN_CONTEXT_CHARS);
            int endExclusive = Math.min(timeline.text.length(), index + UNKNOWN_CONTEXT_CHARS + 1);
            if (!windows.isEmpty() && startIndex <= windows.get(windows.size() - 1).endExclusive) {
                CharWindow previous = windows.remove(windows.size() - 1);
                windows.add(new CharWindow(
                        previous.startIndex,
                        Math.max(previous.endExclusive, endExclusive)
                ));
            } else {
                windows.add(new CharWindow(startIndex, endExclusive));
            }
        }

        for (int index = 0; index < windows.size(); index++) {
            CharWindow window = windows.get(index);
            printWindow(
                    replay,
                    timeline,
                    "unknown-neighborhood#" + (index + 1),
                    window.startIndex,
                    window.endExclusive - window.startIndex
            );
        }
    }

    private static void printWindow(
            LocalAudioDecodeTestSupport.ForcedToneReplayResult replay,
            ReplayTimeline timeline,
            String label,
            int startIndex,
            int length
    ) {
        if (length <= 0 || startIndex < 0 || (startIndex + length) > timeline.decodedChars.size()) {
            return;
        }
        int endExclusive = startIndex + length;
        DecodedCharStamp first = timeline.decodedChars.get(startIndex);
        DecodedCharStamp last = timeline.decodedChars.get(endExclusive - 1);
        long startTimestampMs = Math.max(0L, first.timestampMs - WINDOW_PADDING_MS);
        long endTimestampMs = last.timestampMs + WINDOW_PADDING_MS;
        String snippet = timeline.text.substring(startIndex, endExclusive);
        String context = renderContext(timeline.text, startIndex, endExclusive);

        System.out.println(
                "-- " + label
                        + " chars=" + startIndex + ".." + (endExclusive - 1)
                        + " ts=" + first.timestampMs + ".." + last.timestampMs
                        + " window=" + startTimestampMs + ".." + endTimestampMs
                        + " snippet=" + snippet
                        + " context=" + context
        );
        System.out.println("decode-chars=" + renderDecodedCharWindow(timeline.decodedChars, startIndex, endExclusive));
        printEventWindow(replay, startTimestampMs, endTimestampMs);
    }

    private static String renderContext(String text, int startIndex, int endExclusive) {
        int contextStart = Math.max(0, startIndex - UNKNOWN_CONTEXT_CHARS);
        int contextEnd = Math.min(text.length(), endExclusive + UNKNOWN_CONTEXT_CHARS);
        return text.substring(contextStart, contextEnd);
    }

    private static String renderDecodedCharWindow(
            List<DecodedCharStamp> decodedChars,
            int startIndex,
            int endExclusive
    ) {
        StringBuilder builder = new StringBuilder();
        for (int index = startIndex; index < endExclusive; index++) {
            DecodedCharStamp stamp = decodedChars.get(index);
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append('@').append(stamp.timestampMs)
                    .append(':').append(stamp.value == ' ' ? "<sp>" : stamp.value)
                    .append('/').append(stamp.type)
                    .append("/emit=").append(stamp.emittedValue)
                    .append("/seq=").append(stamp.sourceSequence)
                    .append("/unk=").append(stamp.unknownCharacter);
        }
        return builder.toString();
    }

    private static String renderUnknownAnalysis(
            List<CharacterTimingDetail> characterDetails,
            List<DecodedCharStamp> decodedChars,
            int startIndex,
            int endExclusive
    ) {
        StringBuilder builder = new StringBuilder();
        for (int index = startIndex; index < endExclusive; index++) {
            DecodedCharStamp stamp = decodedChars.get(index);
            if (!stamp.unknownCharacter) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append('@').append(stamp.timestampMs)
                    .append(":seq=").append(stamp.sourceSequence)
                    .append(" nearest=").append(renderNearestKnownCandidates(stamp.sourceSequence))
                    .append(" split=").append(renderSplitCandidates(
                            findCharacterTimingDetail(characterDetails, stamp.timestampMs, stamp.sourceSequence)
                    ))
                    .append(" anomaly=").append(renderOverlongCharacterAnomaly(
                            findCharacterTimingDetail(characterDetails, stamp.timestampMs, stamp.sourceSequence)
                    ));
        }
        return builder.length() == 0 ? "(none)" : builder.toString();
    }

    private static CharacterTimingDetail findCharacterTimingDetail(
            List<CharacterTimingDetail> characterDetails,
            long timestampMs,
            String sourceSequence
    ) {
        if (characterDetails == null) {
            return null;
        }
        for (CharacterTimingDetail detail : characterDetails) {
            if (detail.decodeEvent.timestampMs() == timestampMs
                    && safeEquals(detail.decodeEvent.sourceSequence(), sourceSequence)) {
                return detail;
            }
        }
        return null;
    }

    private static String renderNearestKnownCandidates(String sourceSequence) {
        ArrayList<MorseCandidateDistance> candidates = new ArrayList<>();
        for (Map.Entry<String, String> entry : MORSE_REFERENCE.entrySet()) {
            String candidateSequence = entry.getKey();
            candidates.add(new MorseCandidateDistance(
                    entry.getValue(),
                    candidateSequence,
                    editDistance(sourceSequence, candidateSequence),
                    Math.abs(sourceSequence.length() - candidateSequence.length())
            ));
        }
        candidates.sort(Comparator
                .comparingInt(MorseCandidateDistance::editDistance)
                .thenComparingInt(MorseCandidateDistance::lengthDelta)
                .thenComparing(MorseCandidateDistance::symbol));
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(3, candidates.size());
        for (int index = 0; index < limit; index++) {
            MorseCandidateDistance candidate = candidates.get(index);
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(candidate.symbol)
                    .append('(')
                    .append(candidate.sequence)
                    .append(",d=").append(candidate.editDistance)
                    .append(')');
        }
        return builder.toString();
    }

    private static String renderSplitCandidates(CharacterTimingDetail detail) {
        if (detail == null) {
            return "no-detail";
        }
        String sourceSequence = detail.decodeEvent.sourceSequence();
        if (sourceSequence == null || sourceSequence.length() <= 1) {
            return "none";
        }
        List<CwTimingEvent> internalGaps = detail.internalGapEvents();
        ArrayList<String> candidates = new ArrayList<>();

        for (int splitIndex = 1; splitIndex < sourceSequence.length(); splitIndex++) {
            String left = sourceSequence.substring(0, splitIndex);
            String right = sourceSequence.substring(splitIndex);
            String leftSymbol = MORSE_REFERENCE.get(left);
            String rightSymbol = MORSE_REFERENCE.get(right);
            if (leftSymbol == null || rightSymbol == null) {
                continue;
            }
            candidates.add(renderSplitCandidate(
                    leftSymbol + "|" + rightSymbol,
                    new int[]{splitIndex - 1},
                    internalGaps
            ));
        }

        for (int firstSplit = 1; firstSplit < sourceSequence.length() - 1; firstSplit++) {
            for (int secondSplit = firstSplit + 1; secondSplit < sourceSequence.length(); secondSplit++) {
                String first = sourceSequence.substring(0, firstSplit);
                String second = sourceSequence.substring(firstSplit, secondSplit);
                String third = sourceSequence.substring(secondSplit);
                String firstSymbol = MORSE_REFERENCE.get(first);
                String secondSymbol = MORSE_REFERENCE.get(second);
                String thirdSymbol = MORSE_REFERENCE.get(third);
                if (firstSymbol == null || secondSymbol == null || thirdSymbol == null) {
                    continue;
                }
                candidates.add(renderSplitCandidate(
                        firstSymbol + "|" + secondSymbol + "|" + thirdSymbol,
                        new int[]{firstSplit - 1, secondSplit - 1},
                        internalGaps
                ));
            }
        }

        if (candidates.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(4, candidates.size());
        for (int index = 0; index < limit; index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(candidates.get(index));
        }
        return builder.toString();
    }

    private static String renderOverlongCharacterAnomaly(CharacterTimingDetail detail) {
        if (detail == null) {
            return "no-detail";
        }
        String sourceSequence = detail.decodeEvent.sourceSequence();
        if (sourceSequence == null || sourceSequence.length() < OVERLONG_SEQUENCE_MIN_SYMBOLS) {
            return "short";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("tones=").append(renderToneProfile(detail.internalToneEvents()));
        builder.append("/gaps=").append(renderGapProfile(detail.internalGapEvents()));
        builder.append("/drop=").append(renderDropOneToneCandidates(detail));
        builder.append("/suspect=").append(renderSuspectToneCandidates(detail));
        return builder.toString();
    }

    private static String renderSuspectToneCandidates(CharacterTimingDetail detail) {
        List<SuspectToneCandidate> suspects = findSuspectToneCandidates(detail);
        if (suspects.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(3, suspects.size());
        for (int index = 0; index < limit; index++) {
            SuspectToneCandidate suspect = suspects.get(index);
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(suspect.render());
        }
        return builder.toString();
    }

    private static String renderToneProfile(List<CwTimingEvent> toneEvents) {
        if (toneEvents.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < toneEvents.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            CwTimingEvent toneEvent = toneEvents.get(index);
            double ratio = toneEvent.durationMs() / (double) Math.max(1L, toneEvent.dotEstimateMs());
            builder.append('t').append(index + 1)
                    .append('=')
                    .append(toneEvent.durationMs())
                    .append("ms/")
                    .append(String.format(Locale.US, "%.2f", ratio))
                    .append(toneEvent.classification() == CwTimingEvent.Classification.DAH ? 'D' : 'd');
        }
        return builder.toString();
    }

    private static String renderGapProfile(List<CwTimingEvent> gapEvents) {
        if (gapEvents.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < gapEvents.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            CwTimingEvent gapEvent = gapEvents.get(index);
            double ratio = gapEvent.durationMs() / (double) Math.max(1L, gapEvent.dotEstimateMs());
            builder.append('g').append(index + 1)
                    .append('=')
                    .append(gapEvent.durationMs())
                    .append("ms/")
                    .append(String.format(Locale.US, "%.2f", ratio));
        }
        return builder.toString();
    }

    private static String renderDropOneToneCandidates(CharacterTimingDetail detail) {
        String sourceSequence = detail.decodeEvent.sourceSequence();
        List<CwTimingEvent> toneEvents = detail.internalToneEvents();
        if (sourceSequence == null || sourceSequence.isEmpty() || toneEvents.size() != sourceSequence.length()) {
            return "unavailable";
        }
        ArrayList<String> candidates = new ArrayList<>();
        for (int index = 0; index < sourceSequence.length(); index++) {
            String candidateSequence = sourceSequence.substring(0, index) + sourceSequence.substring(index + 1);
            String symbol = MORSE_REFERENCE.get(candidateSequence);
            if (symbol == null) {
                continue;
            }
            CwTimingEvent removedTone = toneEvents.get(index);
            double ratio = removedTone.durationMs() / (double) Math.max(1L, removedTone.dotEstimateMs());
            candidates.add(String.format(
                    Locale.US,
                    "%s(%s<-drop t%d=%dms/%.2f)",
                    symbol,
                    candidateSequence,
                    index + 1,
                    removedTone.durationMs(),
                    ratio
            ));
        }
        if (candidates.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(4, candidates.size());
        for (int index = 0; index < limit; index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(candidates.get(index));
        }
        return builder.toString();
    }

    private static List<SuspectToneCandidate> findSuspectToneCandidates(CharacterTimingDetail detail) {
        ArrayList<SuspectToneCandidate> suspects = new ArrayList<>();
        if (detail == null) {
            return suspects;
        }
        String sourceSequence = detail.decodeEvent.sourceSequence();
        List<CwTimingEvent> toneEvents = detail.internalToneEvents();
        if (sourceSequence == null || toneEvents.size() != sourceSequence.length()) {
            return suspects;
        }
        for (int index = 0; index < toneEvents.size(); index++) {
            CwTimingEvent toneEvent = toneEvents.get(index);
            double ratio = toneEvent.durationMs() / (double) Math.max(1L, toneEvent.dotEstimateMs());
            double expected = toneEvent.classification() == CwTimingEvent.Classification.DAH ? 3.0d : 1.0d;
            double deviation = Math.abs(ratio - expected);
            boolean likelyShortGlitch = toneEvent.classification() == CwTimingEvent.Classification.DIT && ratio <= 0.75d;
            boolean likelyLongGlitch = toneEvent.classification() == CwTimingEvent.Classification.DIT && ratio >= 1.85d;
            boolean likelyDashBreak = toneEvent.classification() == CwTimingEvent.Classification.DAH && ratio >= 3.80d;
            if (!likelyShortGlitch && !likelyLongGlitch && !likelyDashBreak) {
                continue;
            }
            suspects.add(new SuspectToneCandidate(
                    index + 1,
                    toneEvent.classification(),
                    toneEvent.durationMs(),
                    ratio,
                    deviation,
                    likelyShortGlitch ? "short-dit" : likelyLongGlitch ? "long-dit" : "long-dah"
            ));
        }
        suspects.sort(Comparator
                .comparingDouble(SuspectToneCandidate::deviation).reversed()
                .thenComparingInt(SuspectToneCandidate::toneIndex));
        return suspects;
    }

    private static String renderAnomalyToneSummary(List<CharacterTimingDetail> details) {
        if (details == null || details.isEmpty()) {
            return "none";
        }
        ArrayList<String> lines = new ArrayList<>();
        for (CharacterTimingDetail detail : details) {
            String sequence = detail.decodeEvent.sourceSequence();
            if (sequence == null || sequence.length() < OVERLONG_SEQUENCE_MIN_SYMBOLS) {
                continue;
            }
            List<SuspectToneCandidate> suspects = findSuspectToneCandidates(detail);
            if (suspects.isEmpty()) {
                continue;
            }
            StringBuilder builder = new StringBuilder();
            builder.append('@').append(detail.decodeEvent.timestampMs())
                    .append(":seq=").append(sequence)
                    .append(":");
            int limit = Math.min(2, suspects.size());
            for (int index = 0; index < limit; index++) {
                if (index > 0) {
                    builder.append(", ");
                }
                builder.append(suspects.get(index).render());
            }
            lines.add(builder.toString());
        }
        if (lines.isEmpty()) {
            return "none";
        }
        StringBuilder summary = new StringBuilder();
        int limit = Math.min(6, lines.size());
        for (int index = 0; index < limit; index++) {
            if (index > 0) {
                summary.append(" | ");
            }
            summary.append(lines.get(index));
        }
        return summary.toString();
    }

    private static String renderSplitCandidate(
            String splitLabel,
            int[] gapIndexes,
            List<CwTimingEvent> internalGaps
    ) {
        StringBuilder builder = new StringBuilder(splitLabel).append('@');
        for (int index = 0; index < gapIndexes.length; index++) {
            int gapIndex = gapIndexes[index];
            if (index > 0) {
                builder.append('&');
            }
            builder.append(renderGapDescriptor(gapIndex, internalGaps));
        }
        return builder.toString();
    }

    private static String renderGapDescriptor(int gapIndex, List<CwTimingEvent> internalGaps) {
        if (gapIndex < 0 || gapIndex >= internalGaps.size()) {
            return "g" + (gapIndex + 1) + "=missing";
        }
        CwTimingEvent gapEvent = internalGaps.get(gapIndex);
        double ratio = gapEvent.durationMs() / (double) Math.max(1L, gapEvent.dotEstimateMs());
        return String.format(
                Locale.US,
                "g%d=%dms/%.2fdot",
                gapIndex + 1,
                gapEvent.durationMs(),
                ratio
        );
    }

    private static int editDistance(String left, String right) {
        if (left == null) {
            left = "";
        }
        if (right == null) {
            right = "";
        }
        int[][] dp = new int[left.length() + 1][right.length() + 1];
        for (int i = 0; i <= left.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= right.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            for (int j = 1; j <= right.length(); j++) {
                int substitutionCost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + substitutionCost
                );
            }
        }
        return dp[left.length()][right.length()];
    }

    private static void printEventWindow(
            LocalAudioDecodeTestSupport.ForcedToneReplayResult replay,
            long startTimestampMs,
            long endTimestampMs
    ) {
        System.out.println("tone:");
        for (CwToneEvent event : replay.toneEvents()) {
            if (event.timestampMs() < startTimestampMs || event.timestampMs() > endTimestampMs) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "  T %s @%d dur=%d rms=%.1f peak=%d",
                    event.type(),
                    event.timestampMs(),
                    event.toneDurationMs(),
                    event.rmsAmplitude(),
                    event.peakAmplitude()
            ));
        }
        System.out.println("timing:");
        for (CwTimingEvent event : replay.timingEvents()) {
            if (event.timestampMs() < startTimestampMs || event.timestampMs() > endTimestampMs) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "  M %s/%s @%d dur=%d dot=%d",
                    event.kind(),
                    event.classification(),
                    event.timestampMs(),
                    event.durationMs(),
                    event.dotEstimateMs()
            ));
        }
        System.out.println("decode:");
        for (CwDecodeEvent event : replay.decodeEvents()) {
            if (event.timestampMs() < startTimestampMs || event.timestampMs() > endTimestampMs) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "  D %s @%d out=%s emit=%s seq=%s unknown=%s",
                    event.type(),
                    event.timestampMs(),
                    normalizeNullable(event.outputText()),
                    normalizeNullable(event.emittedValue()),
                    normalizeNullable(event.sourceSequence()),
                    event.unknownCharacter()
            ));
        }
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

    private static boolean safeEquals(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
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

    private static final class ReplayTimeline {
        private final String text;
        private final List<DecodedCharStamp> decodedChars;

        private ReplayTimeline(String text, List<DecodedCharStamp> decodedChars) {
            this.text = text;
            this.decodedChars = decodedChars;
        }
    }

    private static final class CharWindow {
        private final int startIndex;
        private final int endExclusive;

        private CharWindow(int startIndex, int endExclusive) {
            this.startIndex = startIndex;
            this.endExclusive = endExclusive;
        }
    }

    private static final class WindowMatch {
        private final String pattern;
        private final int startIndex;
        private final int endExclusive;
        private final long firstTimestampMs;
        private final long lastTimestampMs;
        private final String snippet;
        private final String context;

        private WindowMatch(
                String pattern,
                int startIndex,
                int endExclusive,
                long firstTimestampMs,
                long lastTimestampMs,
                String snippet,
                String context
        ) {
            this.pattern = pattern;
            this.startIndex = startIndex;
            this.endExclusive = endExclusive;
            this.firstTimestampMs = firstTimestampMs;
            this.lastTimestampMs = lastTimestampMs;
            this.snippet = snippet;
            this.context = context;
        }
    }

    private static final class ComparisonGroup {
        private final String label;
        private final boolean pickFirst;
        private final String[] patterns;

        private ComparisonGroup(String label, boolean pickFirst, String... patterns) {
            this.label = label;
            this.pickFirst = pickFirst;
            this.patterns = patterns;
        }
    }

    private static final class MorseCandidateDistance {
        private final String symbol;
        private final String sequence;
        private final int editDistance;
        private final int lengthDelta;

        private MorseCandidateDistance(String symbol, String sequence, int editDistance, int lengthDelta) {
            this.symbol = symbol;
            this.sequence = sequence;
            this.editDistance = editDistance;
            this.lengthDelta = lengthDelta;
        }

        private String symbol() {
            return symbol;
        }

        private int editDistance() {
            return editDistance;
        }

        private int lengthDelta() {
            return lengthDelta;
        }
    }

    private static final class SuspectToneCandidate {
        private final int toneIndex;
        private final CwTimingEvent.Classification classification;
        private final long durationMs;
        private final double ratio;
        private final double deviation;
        private final String reason;

        private SuspectToneCandidate(
                int toneIndex,
                CwTimingEvent.Classification classification,
                long durationMs,
                double ratio,
                double deviation,
                String reason
        ) {
            this.toneIndex = toneIndex;
            this.classification = classification;
            this.durationMs = durationMs;
            this.ratio = ratio;
            this.deviation = deviation;
            this.reason = reason;
        }

        private int toneIndex() {
            return toneIndex;
        }

        private double deviation() {
            return deviation;
        }

        private String render() {
            return String.format(
                    Locale.US,
                    "t%d=%s/%dms/%.2f(%s)",
                    toneIndex,
                    classification == CwTimingEvent.Classification.DAH ? "DAH" : "DIT",
                    durationMs,
                    ratio,
                    reason
            );
        }
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
