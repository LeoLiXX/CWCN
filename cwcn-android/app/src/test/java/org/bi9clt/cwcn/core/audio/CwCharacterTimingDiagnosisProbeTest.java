package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel.DebugSnapshot;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class CwCharacterTimingDiagnosisProbeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;
    private static final int RECORDING12_SQL_PERCENT = 15;
    private static final String RECORDING12_EXPECTED_TEXT = "CQ CQ CQ DE BI9CMS BI9CMS IN 700 PSE K.";

    private static final long CAPTURE_K_TIMESTAMP_MS = 49952L;
    private static final long CAPTURE_WINDOW_PADDING_MS = 320L;
    private static final long RECORDING15_WINDOW_PADDING_MS = 360L;
    private static final long RECORDING12_WINDOW_PADDING_MS = 360L;
    private static final long PREFERRED_MATCH_MAX_DISTANCE_MS = 256L;

    private static final Map<String, String> MORSE_REFERENCE = buildMorseReference();

    @Test
    public void printCaptureTurn2KCharacterDiagnosis() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.findTraceDir().resolve("capture.wav");
        if (!Files.isRegularFile(wavFile)) {
            throw new IllegalStateException("Missing capture wav: " + wavFile);
        }
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed = decodeLiveLikeDetailed(wavFile);
        CwDecodeEvent target = findCharacterEventNearTimestamp(
                detailed.decodeEvents(),
                CAPTURE_K_TIMESTAMP_MS,
                "K",
                "-.-"
        );
        printCharacterDiagnosis(
                "capture.wav turn2 suspicious K",
                detailed,
                target,
                CAPTURE_WINDOW_PADDING_MS
        );
        assertNotNull(target);
    }

    @Test
    public void printRecording15OpeningUnknownDiagnosis() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(15).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for recording (15)"));
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed = decodeLiveLikeDetailed(wavFile);
        CwDecodeEvent target = findFirstUnknownCharacterEvent(detailed.decodeEvents());
        printCharacterDiagnosis(
                "recording(15) first unknown character",
                detailed,
                target,
                RECORDING15_WINDOW_PADDING_MS
        );
        assertNotNull(target);
    }

    @Test
    public void printRecording12FirstPostTrustMismatchDiagnosis() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(12).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for recording (12)"));
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                decodeLiveLikeDetailed(wavFile, RECORDING12_SQL_PERCENT);
        long trustTimestampMs = firstTrustedTimestampMs(detailed.timingStateTraces());
        CwDecodeEvent target = findFirstPostTrustMismatchCharacterEvent(
                detailed.decodeEvents(),
                trustTimestampMs,
                RECORDING12_EXPECTED_TEXT
        );
        System.out.println(String.format(
                Locale.US,
                "recording(12) trust=%d expected=%s final=%s",
                trustTimestampMs,
                compact(normalize(RECORDING12_EXPECTED_TEXT)),
                compact(normalize(detailed.probeResult().decodedText()))
        ));
        printCharacterDiagnosis(
                "recording(12) first post-trust mismatch",
                detailed,
                target,
                RECORDING12_WINDOW_PADDING_MS
        );
        assertNotNull(target);
    }

    @Test
    public void printRecording12SecondPostTrustMismatchDiagnosis() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(12).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for recording (12)"));
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                decodeLiveLikeDetailed(wavFile, RECORDING12_SQL_PERCENT);
        long trustTimestampMs = firstTrustedTimestampMs(detailed.timingStateTraces());
        List<CwDecodeEvent> mismatches = listPostTrustMismatchCharacterEvents(
                detailed.decodeEvents(),
                trustTimestampMs,
                RECORDING12_EXPECTED_TEXT
        );
        CwDecodeEvent target = mismatches.size() >= 2 ? mismatches.get(1) : null;
        System.out.println(String.format(
                Locale.US,
                "recording(12) trust=%d expected=%s final=%s mismatchCount=%d",
                trustTimestampMs,
                compact(normalize(RECORDING12_EXPECTED_TEXT)),
                compact(normalize(detailed.probeResult().decodedText())),
                mismatches.size()
        ));
        printCharacterDiagnosis(
                "recording(12) second post-trust mismatch",
                detailed,
                target,
                RECORDING12_WINDOW_PADDING_MS
        );
        assertNotNull(target);
    }

    @Test
    public void printRecording12ThirdPostTrustMismatchDiagnosis() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(12).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for recording (12)"));
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                decodeLiveLikeDetailed(wavFile, RECORDING12_SQL_PERCENT);
        long trustTimestampMs = firstTrustedTimestampMs(detailed.timingStateTraces());
        List<CwDecodeEvent> mismatches = listPostTrustMismatchCharacterEvents(
                detailed.decodeEvents(),
                trustTimestampMs,
                RECORDING12_EXPECTED_TEXT
        );
        CwDecodeEvent target = mismatches.size() >= 3 ? mismatches.get(2) : null;
        System.out.println(String.format(
                Locale.US,
                "recording(12) trust=%d expected=%s final=%s mismatchCount=%d",
                trustTimestampMs,
                compact(normalize(RECORDING12_EXPECTED_TEXT)),
                compact(normalize(detailed.probeResult().decodedText())),
                mismatches.size()
        ));
        printCharacterDiagnosis(
                "recording(12) third post-trust mismatch",
                detailed,
                target,
                RECORDING12_WINDOW_PADDING_MS
        );
        printCanonicalAlignmentWindow(
                "recording(12) third post-trust mismatch",
                detailed.decodeEvents(),
                trustTimestampMs,
                RECORDING12_EXPECTED_TEXT,
                target,
                28
        );
        assertNotNull(target);
    }

    @Test
    public void printRecording12PostTrustMismatchSummary() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(12).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for recording (12)"));
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                decodeLiveLikeDetailed(wavFile, RECORDING12_SQL_PERCENT);
        long trustTimestampMs = firstTrustedTimestampMs(detailed.timingStateTraces());
        List<CwDecodeEvent> mismatches = listPostTrustMismatchCharacterEvents(
                detailed.decodeEvents(),
                trustTimestampMs,
                RECORDING12_EXPECTED_TEXT
        );

        System.out.println(String.format(
                Locale.US,
                "recording(12) trust=%d expected=%s final=%s mismatchCount=%d",
                trustTimestampMs,
                compact(normalize(RECORDING12_EXPECTED_TEXT)),
                compact(normalize(detailed.probeResult().decodedText())),
                mismatches.size()
        ));
        System.out.println("==== recording(12) post-trust mismatch summary ====");
        for (int index = 0; index < mismatches.size(); index++) {
            CwDecodeEvent mismatch = mismatches.get(index);
            System.out.println(String.format(
                    Locale.US,
                    "#%d @%d emit=%s seq=%s out=%s",
                    index + 1,
                    mismatch.timestampMs(),
                    compact(mismatch.emittedValue()),
                    compact(mismatch.sourceSequence()),
                    compact(normalize(mismatch.outputText()))
            ));
        }
        assertTrue(!mismatches.isEmpty());
    }

    @Test
    public void printRecording12LastPostTrustMismatchDiagnosis() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(12).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for recording (12)"));
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                decodeLiveLikeDetailed(wavFile, RECORDING12_SQL_PERCENT);
        long trustTimestampMs = firstTrustedTimestampMs(detailed.timingStateTraces());
        List<CwDecodeEvent> mismatches = listPostTrustMismatchCharacterEvents(
                detailed.decodeEvents(),
                trustTimestampMs,
                RECORDING12_EXPECTED_TEXT
        );
        CwDecodeEvent target = mismatches.isEmpty() ? null : mismatches.get(mismatches.size() - 1);
        System.out.println(String.format(
                Locale.US,
                "recording(12) trust=%d expected=%s final=%s mismatchCount=%d",
                trustTimestampMs,
                compact(normalize(RECORDING12_EXPECTED_TEXT)),
                compact(normalize(detailed.probeResult().decodedText())),
                mismatches.size()
        ));
        printCharacterDiagnosis(
                "recording(12) last post-trust mismatch",
                detailed,
                target,
                RECORDING12_WINDOW_PADDING_MS
        );
        assertNotNull(target);
    }

    @Test
    public void printRecording12FirstCallsignUnknownMismatchDiagnosis() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(12).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for recording (12)"));
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                decodeLiveLikeDetailed(wavFile, RECORDING12_SQL_PERCENT);
        long trustTimestampMs = firstTrustedTimestampMs(detailed.timingStateTraces());
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
        System.out.println(String.format(
                Locale.US,
                "recording(12) trust=%d expected=%s final=%s mismatchCount=%d",
                trustTimestampMs,
                compact(normalize(RECORDING12_EXPECTED_TEXT)),
                compact(normalize(detailed.probeResult().decodedText())),
                mismatches.size()
        ));
        printCharacterDiagnosis(
                "recording(12) first callsign unknown mismatch",
                detailed,
                target,
                520L
        );
        assertNotNull(target);
    }

    private static LocalAudioDecodeTestSupport.OfflineDetailedProbeResult decodeLiveLikeDetailed(Path wavFile)
            throws Exception {
        return decodeLiveLikeDetailed(wavFile, SQL_PERCENT);
    }

    private static LocalAudioDecodeTestSupport.OfflineDetailedProbeResult decodeLiveLikeDetailed(
            Path wavFile,
            int sqlPercent
    ) throws Exception {
        return LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                wavFile,
                PREFERRED_TONE_HZ,
                SEED_WPM,
                sqlPercent,
                false,
                CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
        );
    }

    private static void printCharacterDiagnosis(
            String label,
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed,
            CwDecodeEvent target,
            long windowPaddingMs
    ) {
        if (target == null) {
            throw new IllegalStateException("Target decode event not found for " + label);
        }

        long windowStartMs = Math.max(0L, target.timestampMs() - windowPaddingMs);
        long windowEndMs = target.timestampMs() + windowPaddingMs;
        CharacterTimingBundle bundle = buildCharacterTimingBundle(detailed.timingEvents(), target);
        List<CwDecodeEvent> characterEvents = listCharacterEvents(detailed.decodeEvents());

        System.out.println("==== " + label + " ====");
        System.out.println(String.format(
                Locale.US,
                "target=@%d emit=%s seq=%s out=%s neighbor=%s",
                target.timestampMs(),
                compact(target.emittedValue()),
                compact(target.sourceSequence()),
                compact(normalize(target.outputText())),
                describeNeighborCandidates(target.sourceSequence())
        ));
        System.out.println("char-neighborhood=" + renderCharacterNeighborhood(characterEvents, target, 4));
        System.out.println("window=" + windowStartMs + ".." + windowEndMs);

        System.out.println("-- target-character-timing --");
        if (bundle.internalEvents.isEmpty()) {
            System.out.println("  no-internal-events");
        } else {
            for (CwTimingEvent event : bundle.internalEvents) {
                System.out.println("  " + renderTimingEvent("char", event));
            }
        }
        System.out.println("boundary-gap=" + renderTimingEvent("boundary", bundle.boundaryGapEvent));
        System.out.println("pre-boundary=" + renderTimingEventList("pre", bundle.precedingEvents));
        System.out.println("post-boundary=" + renderTimingEventList("post", bundle.followingEvents));

        System.out.println("-- nearby-tone-events --");
        for (CwToneEvent event : detailed.toneEvents()) {
            if (event.timestampMs() < windowStartMs || event.timestampMs() > windowEndMs) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "  tone %s @%d dur=%d rms=%.1f peak=%d",
                    event.type(),
                    event.timestampMs(),
                    event.toneDurationMs(),
                    event.rmsAmplitude(),
                    event.peakAmplitude()
            ));
        }

        System.out.println("-- nearby-timing-events --");
        for (CwTimingEvent event : detailed.timingEvents()) {
            if (event.timestampMs() < windowStartMs || event.timestampMs() > windowEndMs) {
                continue;
            }
            System.out.println("  " + renderTimingEvent("timing", event));
        }

        System.out.println("-- nearby-decode-events --");
        String previousText = "";
        for (CwDecodeEvent event : detailed.decodeEvents()) {
            if (event.timestampMs() < windowStartMs || event.timestampMs() > windowEndMs) {
                continue;
            }
            String outputText = normalize(event.outputText());
            String appended = outputText.startsWith(previousText)
                    ? outputText.substring(previousText.length())
                    : normalize(event.emittedValue());
            System.out.println(String.format(
                    Locale.US,
                    "  decode @%d type=%s emit=%s seq=%s unk=%s append=%s out=%s",
                    event.timestampMs(),
                    event.type(),
                    compact(normalize(event.emittedValue())),
                    compact(event.sourceSequence()),
                    yesNo(event.unknownCharacter()),
                    compact(appended),
                    compact(outputText)
            ));
            previousText = outputText;
        }

        System.out.println("-- timing-state-snapshots --");
        printTimingStateSnapshots(detailed.timingStateTraces(), windowStartMs, windowEndMs);

        System.out.println("-- frame-signal-window --");
        printFrameSignalWindow(detailed.frameSignalTraces(), windowStartMs, windowEndMs);
    }

    private static void printCanonicalAlignmentWindow(
            String label,
            List<CwDecodeEvent> decodeEvents,
            long trustTimestampMs,
            String expectedText,
            CwDecodeEvent target,
            int contextChars
    ) {
        if (target == null) {
            return;
        }
        String expectedCanonical = canonicalize(expectedText);
        String actualCanonical = canonicalize(lastOutputText(decodeEvents));
        CanonicalAlignment alignment = buildCanonicalAlignment(expectedCanonical, actualCanonical);
        int actualIndex = canonicalLengthAtOrBefore(decodeEvents, target.timestampMs()) - 1;
        if (actualIndex < 0 || actualIndex >= alignment.actualIndexToAlignedIndex.length) {
            return;
        }
        int alignedIndex = alignment.actualIndexToAlignedIndex[actualIndex];
        int trustActualIndex = canonicalLengthAtOrBefore(decodeEvents, trustTimestampMs);
        int trustAlignedIndex = 0;
        if (trustActualIndex > 0 && trustActualIndex - 1 < alignment.actualIndexToAlignedIndex.length) {
            trustAlignedIndex = alignment.actualIndexToAlignedIndex[trustActualIndex - 1] + 1;
        }
        int windowStart = Math.max(0, Math.min(alignedIndex, trustAlignedIndex) - 8);
        int windowEnd = Math.min(
                alignment.expectedAligned.length(),
                alignedIndex + contextChars + 1
        );

        System.out.println("-- canonical-alignment-window --");
        System.out.println(String.format(
                Locale.US,
                "label=%s trustCanonicalIndex=%d targetActualIndex=%d alignedIndex=%d window=%d..%d",
                label,
                trustActualIndex,
                actualIndex,
                alignedIndex,
                windowStart,
                windowEnd
        ));
        System.out.println("  exp  " + alignment.expectedAligned.substring(windowStart, windowEnd));
        System.out.println("  mark " + alignment.markerAligned.substring(windowStart, windowEnd));
        System.out.println("  act  " + alignment.actualAligned.substring(windowStart, windowEnd));
    }

    private static CharacterTimingBundle buildCharacterTimingBundle(
            List<CwTimingEvent> timingEvents,
            CwDecodeEvent target
    ) {
        int boundaryIndex = findBoundaryIndexForCharacter(timingEvents, target);
        CwTimingEvent boundaryGap = boundaryIndex >= 0 ? timingEvents.get(boundaryIndex) : null;
        List<CwTimingEvent> internalEvents = collectCharacterTimingEvents(
                timingEvents,
                boundaryIndex,
                target.sourceSequence()
        );
        List<CwTimingEvent> precedingEvents = collectDirectionalEvents(timingEvents, boundaryIndex - 1, -1, 4);
        List<CwTimingEvent> followingEvents = collectDirectionalEvents(
                timingEvents,
                boundaryIndex + 1,
                1,
                6
        );
        return new CharacterTimingBundle(boundaryGap, internalEvents, precedingEvents, followingEvents);
    }

    private static int findBoundaryIndexForCharacter(List<CwTimingEvent> timingEvents, CwDecodeEvent target) {
        for (int index = 0; index < timingEvents.size(); index++) {
            CwTimingEvent timingEvent = timingEvents.get(index);
            if (timingEvent.timestampMs() != target.timestampMs()) {
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

        ArrayList<CwTimingEvent> reversed = new ArrayList<>();
        int collectedTones = 0;
        int scanIndex = boundaryIndex >= 0 ? boundaryIndex - 1 : timingEvents.size() - 1;
        while (scanIndex >= 0 && collectedTones < requiredTones) {
            CwTimingEvent timingEvent = timingEvents.get(scanIndex);
            if (timingEvent.kind() == CwTimingEvent.Kind.TONE) {
                reversed.add(timingEvent);
                collectedTones += 1;
                scanIndex -= 1;
                continue;
            }
            if (timingEvent.kind() == CwTimingEvent.Kind.GAP
                    && timingEvent.classification() == CwTimingEvent.Classification.INTRA_SYMBOL_GAP) {
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

    private static List<CwTimingEvent> collectDirectionalEvents(
            List<CwTimingEvent> timingEvents,
            int startIndex,
            int direction,
            int maxCount
    ) {
        ArrayList<CwTimingEvent> collected = new ArrayList<>();
        int index = startIndex;
        while (index >= 0 && index < timingEvents.size() && collected.size() < maxCount) {
            collected.add(timingEvents.get(index));
            index += direction;
        }
        if (direction < 0) {
            ArrayList<CwTimingEvent> ordered = new ArrayList<>(collected.size());
            for (int reverse = collected.size() - 1; reverse >= 0; reverse--) {
                ordered.add(collected.get(reverse));
            }
            return ordered;
        }
        return collected;
    }

    private static CwDecodeEvent findCharacterEventNearTimestamp(
            List<CwDecodeEvent> decodeEvents,
            long targetTimestampMs,
            String emittedValue,
            String sourceSequence
    ) {
        CwDecodeEvent preferredNearest = null;
        long preferredNearestDistanceMs = Long.MAX_VALUE;
        CwDecodeEvent fallbackNearest = null;
        long fallbackNearestDistanceMs = Long.MAX_VALUE;
        for (CwDecodeEvent event : decodeEvents) {
            if (event.type() != CwDecodeEvent.Type.CHARACTER_DECODED) {
                continue;
            }
            long distanceMs = Math.abs(event.timestampMs() - targetTimestampMs);
            if (distanceMs < fallbackNearestDistanceMs) {
                fallbackNearest = event;
                fallbackNearestDistanceMs = distanceMs;
            }
            if (!safeEquals(event.emittedValue(), emittedValue)
                    || !safeEquals(event.sourceSequence(), sourceSequence)) {
                continue;
            }
            if (distanceMs < preferredNearestDistanceMs) {
                preferredNearest = event;
                preferredNearestDistanceMs = distanceMs;
            }
        }
        if (preferredNearest != null && preferredNearestDistanceMs <= PREFERRED_MATCH_MAX_DISTANCE_MS) {
            return preferredNearest;
        }
        return fallbackNearest;
    }

    private static CwDecodeEvent findFirstUnknownCharacterEvent(List<CwDecodeEvent> decodeEvents) {
        for (CwDecodeEvent event : decodeEvents) {
            if (event.type() == CwDecodeEvent.Type.CHARACTER_DECODED && event.unknownCharacter()) {
                return event;
            }
        }
        return null;
    }

    private static CwDecodeEvent findFirstPostTrustMismatchCharacterEvent(
            List<CwDecodeEvent> decodeEvents,
            long trustTimestampMs,
            String expectedText
    ) {
        List<CwDecodeEvent> mismatches = listPostTrustMismatchCharacterEvents(
                decodeEvents,
                trustTimestampMs,
                expectedText
        );
        return mismatches.isEmpty() ? null : mismatches.get(0);
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

    private static List<CwDecodeEvent> listCharacterEvents(List<CwDecodeEvent> decodeEvents) {
        ArrayList<CwDecodeEvent> characters = new ArrayList<>();
        for (CwDecodeEvent event : decodeEvents) {
            if (event.type() == CwDecodeEvent.Type.CHARACTER_DECODED) {
                characters.add(event);
            }
        }
        return characters;
    }

    private static String renderCharacterNeighborhood(
            List<CwDecodeEvent> characterEvents,
            CwDecodeEvent target,
            int radius
    ) {
        int targetIndex = -1;
        for (int index = 0; index < characterEvents.size(); index++) {
            if (characterEvents.get(index) == target) {
                targetIndex = index;
                break;
            }
        }
        if (targetIndex < 0) {
            return "target-not-in-character-list";
        }

        StringBuilder builder = new StringBuilder();
        int startIndex = Math.max(0, targetIndex - radius);
        int endExclusive = Math.min(characterEvents.size(), targetIndex + radius + 1);
        for (int index = startIndex; index < endExclusive; index++) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            CwDecodeEvent event = characterEvents.get(index);
            if (index == targetIndex) {
                builder.append(">>");
            }
            builder.append('@').append(event.timestampMs())
                    .append(':').append(compact(event.emittedValue()))
                    .append('/').append(compact(event.sourceSequence()))
                    .append("/unk=").append(yesNo(event.unknownCharacter()));
            if (index == targetIndex) {
                builder.append("<<");
            }
        }
        return builder.toString();
    }

    private static String renderTimingEventList(String label, List<CwTimingEvent> events) {
        if (events == null || events.isEmpty()) {
            return label + "=none";
        }
        StringBuilder builder = new StringBuilder(label).append('=');
        for (int index = 0; index < events.size(); index++) {
            if (index > 0) {
                builder.append(" | ");
            }
            builder.append(renderTimingEvent("", events.get(index)));
        }
        return builder.toString();
    }

    private static String renderTimingEvent(String label, CwTimingEvent event) {
        if (event == null) {
            return label.isEmpty() ? "none" : label + "=none";
        }
        return (label.isEmpty() ? "" : label + " ")
                + String.format(
                Locale.US,
                "%s/%s @%d dur=%d dot=%d intra=%d rDot=%.2f rIntra=%.2f",
                event.kind(),
                event.classification(),
                event.timestampMs(),
                event.durationMs(),
                event.dotEstimateMs(),
                event.intraGapEstimateMs(),
                event.ratioToDotEstimate(),
                event.ratioToIntraGapEstimate()
        );
    }

    private static void printTimingStateSnapshots(
            List<LocalAudioDecodeTestSupport.TimingStateTrace> timingStateTraces,
            long windowStartMs,
            long windowEndMs
    ) {
        long lastPrintedTimestampMs = Long.MIN_VALUE;
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : timingStateTraces) {
            if (trace == null || trace.timestampMs() < windowStartMs || trace.timestampMs() > windowEndMs) {
                continue;
            }
            boolean interesting = trace.debugSnapshot() != null
                    && (trace.debugSnapshot().trustedDotEstimateMs() > 0.0d
                    || trace.debugSnapshot().pendingFastTrustedEvidenceCount() > 0
                    || trace.debugSnapshot().lastTrustedUpdateTimestampMs() >= 0L);
            if (!interesting && (trace.timestampMs() - lastPrintedTimestampMs) < 96L) {
                continue;
            }
            lastPrintedTimestampMs = trace.timestampMs();
            System.out.println(renderTimingStateTrace(trace));
        }
    }

    private static String renderTimingStateTrace(LocalAudioDecodeTestSupport.TimingStateTrace trace) {
        DebugSnapshot debugSnapshot = trace.debugSnapshot();
        CwTimingSnapshot stabilized = trace.stabilizedSnapshot();
        CwTimingSnapshot raw = trace.rawSnapshot();
        return String.format(
                Locale.US,
                "  state @%d tr=%.1f rf=%.1f pf=%.1f/%d act=%s last=%s stab=%d/%.1f raw=%d/%.1f obs=%s",
                trace.timestampMs(),
                debugSnapshot == null ? 0.0d : debugSnapshot.trustedDotEstimateMs(),
                debugSnapshot == null ? 0.0d : debugSnapshot.retainedDotEstimateMs(),
                debugSnapshot == null ? 0.0d : debugSnapshot.pendingFastTrustedDotEstimateMs(),
                debugSnapshot == null ? 0 : debugSnapshot.pendingFastTrustedEvidenceCount(),
                debugSnapshot == null ? "?" : debugSnapshot.activeStrategyName(),
                debugSnapshot == null ? "?" : debugSnapshot.lastEmissionStrategyName(),
                stabilized == null ? 0 : stabilized.dotEstimateMs(),
                stabilized == null ? 0.0d : stabilized.estimatedWpmPrecise(),
                raw == null ? 0 : raw.dotEstimateMs(),
                raw == null ? 0.0d : raw.estimatedWpmPrecise(),
                trace.debugSummary()
        );
    }

    private static void printFrameSignalWindow(
            List<LocalAudioDecodeTestSupport.FrameSignalTrace> traces,
            long windowStartMs,
            long windowEndMs
    ) {
        long lastPrintedTimestampMs = Long.MIN_VALUE;
        for (int index = 0; index < traces.size(); index++) {
            LocalAudioDecodeTestSupport.FrameSignalTrace trace = traces.get(index);
            if (trace == null || trace.snapshot() == null) {
                continue;
            }
            if (trace.timestampMs() < windowStartMs || trace.timestampMs() > windowEndMs) {
                continue;
            }
            int deltaOn = index <= 0
                    ? trace.snapshot().totalToneOnEvents()
                    : trace.snapshot().totalToneOnEvents() - traces.get(index - 1).snapshot().totalToneOnEvents();
            int deltaOff = index <= 0
                    ? trace.snapshot().totalToneOffEvents()
                    : trace.snapshot().totalToneOffEvents() - traces.get(index - 1).snapshot().totalToneOffEvents();
            boolean decisionFrame = trace.toneOnAcceptedByRescue()
                    || trace.currentToneStartedByPostReleaseRescue()
                    || trace.releaseTailHoldApplied()
                    || isInterestingDecision(trace.toneOnDecision())
                    || isInterestingDecision(trace.postReleaseRescueDecision())
                    || isInterestingDecision(trace.postReleaseSuppressionDecision())
                    || isInterestingDecision(trace.farAttackDelayDecision())
                    || isInterestingDecision(trace.releaseTailHoldDecision());
            if (!decisionFrame && (trace.timestampMs() - lastPrintedTimestampMs) < 48L) {
                continue;
            }
            lastPrintedTimestampMs = trace.timestampMs();
            System.out.println(String.format(
                    Locale.US,
                    "  frame @%d det=%.1f on=%s lock=%s eff=%d final=%d locked=%.2f unlocked=%.2f dom=%.2f iso=%.2f thr=%d/%d lc=%.2f on+%d off+%d last=%s toneOn=%s rescue=%s suppress=%s release=%s attack=%s",
                    trace.timestampMs(),
                    trace.detectionLevel(),
                    yesNo(trace.snapshot().toneActive()),
                    yesNo(trace.snapshot().targetToneLocked()),
                    trace.snapshot().effectiveTrackedToneFrequencyHz(),
                    trace.snapshot().effectiveFinalAdoptedFrequencyHz(),
                    trace.snapshot().recentLockedFrameRatio(),
                    trace.snapshot().recentActiveUnlockedFrameRatio(),
                    trace.snapshot().toneDominanceRatio(),
                    trace.snapshot().narrowbandIsolationRatio(),
                    trace.snapshot().currentThreshold(),
                    trace.snapshot().releaseThreshold(),
                    trace.localContrastRatio(),
                    deltaOn,
                    deltaOff,
                    renderLastEvent(trace.snapshot().lastEvent()),
                    compact(trace.toneOnDecision()),
                    compact(trace.postReleaseRescueDecision()),
                    compact(trace.postReleaseSuppressionDecision()),
                    compact(trace.releaseTailHoldDecision()),
                    compact(trace.farAttackDelayDecision())
            ));
        }
    }

    private static String renderLastEvent(CwToneEvent event) {
        if (event == null) {
            return "-";
        }
        return event.type() + "@" + event.timestampMs() + "/" + event.toneDurationMs();
    }

    private static boolean isInterestingDecision(String value) {
        if (value == null) {
            return false;
        }
        return !"NONE".equals(value)
                && !"ELIGIBLE".equals(value)
                && !"BLOCKED:BASIC_PRECONDITION".equals(value);
    }

    private static String describeNeighborCandidates(String sourceSequence) {
        if (sourceSequence == null || sourceSequence.isEmpty()) {
            return "none";
        }
        ArrayList<String> candidates = new ArrayList<>();
        addCandidate(candidates, "append-dot", sourceSequence + ".");
        addCandidate(candidates, "append-dah", sourceSequence + "-");
        if (sourceSequence.length() > 1) {
            addCandidate(candidates, "drop-last", sourceSequence.substring(0, sourceSequence.length() - 1));
        }
        if (sourceSequence.length() > 0) {
            addCandidate(candidates, "drop-first", sourceSequence.substring(1));
        }
        return candidates.isEmpty() ? "none" : String.join(", ", candidates);
    }

    private static void addCandidate(List<String> sink, String action, String sequence) {
        String symbol = MORSE_REFERENCE.get(sequence);
        if (symbol == null) {
            return;
        }
        sink.add(action + "=" + symbol + "/" + sequence);
    }

    private static String compact(String value) {
        if (value == null) {
            return "-";
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "-" : trimmed;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace('\u25A1', '?');
    }

    private static String canonicalize(String text) {
        if (text == null) {
            return "";
        }
        String upper = normalize(text).toUpperCase(Locale.US);
        StringBuilder builder = new StringBuilder(upper.length());
        for (int index = 0; index < upper.length(); index++) {
            char ch = upper.charAt(index);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '?') {
                builder.append(ch);
            }
        }
        return builder.toString();
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

    private static long firstTrustedTimestampMs(List<LocalAudioDecodeTestSupport.TimingStateTrace> timingStateTraces) {
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : timingStateTraces) {
            if (trace != null
                    && trace.debugSnapshot() != null
                    && trace.debugSnapshot().trustedDotEstimateMs() > 0.0d) {
                return trace.timestampMs();
            }
        }
        return -1L;
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

    private static CanonicalAlignment buildCanonicalAlignment(String expected, String actual) {
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

        StringBuilder expectedAligned = new StringBuilder();
        StringBuilder actualAligned = new StringBuilder();
        StringBuilder markerAligned = new StringBuilder();
        int[] actualIndexToAlignedIndex = new int[actualLength];
        int expectedIndex = 0;
        int actualIndex = 0;
        while (expectedIndex < expectedLength || actualIndex < actualLength) {
            if (expectedIndex < expectedLength
                    && actualIndex < actualLength
                    && expected.charAt(expectedIndex) == actual.charAt(actualIndex)) {
                int alignedIndex = expectedAligned.length();
                expectedAligned.append(expected.charAt(expectedIndex));
                actualAligned.append(actual.charAt(actualIndex));
                markerAligned.append('|');
                actualIndexToAlignedIndex[actualIndex] = alignedIndex;
                expectedIndex += 1;
                actualIndex += 1;
                continue;
            }
            if (expectedIndex < expectedLength
                    && (actualIndex >= actualLength
                    || dp[expectedIndex + 1][actualIndex] >= dp[expectedIndex][actualIndex + 1])) {
                expectedAligned.append(expected.charAt(expectedIndex));
                actualAligned.append('-');
                markerAligned.append(' ');
                expectedIndex += 1;
                continue;
            }
            int alignedIndex = expectedAligned.length();
            expectedAligned.append('-');
            actualAligned.append(actual.charAt(actualIndex));
            markerAligned.append('^');
            actualIndexToAlignedIndex[actualIndex] = alignedIndex;
            actualIndex += 1;
        }

        return new CanonicalAlignment(
                expectedAligned.toString(),
                actualAligned.toString(),
                markerAligned.toString(),
                actualIndexToAlignedIndex
        );
    }

    private static final class CanonicalAlignment {
        private final String expectedAligned;
        private final String actualAligned;
        private final String markerAligned;
        private final int[] actualIndexToAlignedIndex;

        private CanonicalAlignment(
                String expectedAligned,
                String actualAligned,
                String markerAligned,
                int[] actualIndexToAlignedIndex
        ) {
            this.expectedAligned = expectedAligned;
            this.actualAligned = actualAligned;
            this.markerAligned = markerAligned;
            this.actualIndexToAlignedIndex = actualIndexToAlignedIndex;
        }
    }

    private static String yesNo(boolean value) {
        return value ? "Y" : "N";
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

    private static final class CharacterTimingBundle {
        private final CwTimingEvent boundaryGapEvent;
        private final List<CwTimingEvent> internalEvents;
        private final List<CwTimingEvent> precedingEvents;
        private final List<CwTimingEvent> followingEvents;

        private CharacterTimingBundle(
                CwTimingEvent boundaryGapEvent,
                List<CwTimingEvent> internalEvents,
                List<CwTimingEvent> precedingEvents,
                List<CwTimingEvent> followingEvents
        ) {
            this.boundaryGapEvent = boundaryGapEvent;
            this.internalEvents = internalEvents;
            this.precedingEvents = precedingEvents;
            this.followingEvents = followingEvents;
        }
    }
}
