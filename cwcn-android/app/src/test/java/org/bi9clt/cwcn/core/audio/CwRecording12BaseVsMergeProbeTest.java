package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel.DebugSnapshot;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CwRecording12BaseVsMergeProbeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;
    private static final String EXPECTED_TEXT = "CQ CQ CQ DE BI9CMS BI9CMS IN 700 PSE K.";
    private static final long DE_WINDOW_PADDING_MS = 520L;
    private static final long CALLSIGN_WINDOW_PADDING_MS = 720L;
    private static final long CALLSIGN_WINDOW_START_MS = 18000L;
    private static final long CALLSIGN_WINDOW_END_MS = 26000L;
    private static final long NEAREST_CHARACTER_MAX_DISTANCE_MS = 320L;

    @Test
    public void printRecording12BaseVsMergeKeyWindows() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(12).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for recording (12)"));
        List<AudioFrame> frames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult base = decode(frames, false);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult merge = decode(frames, true);
        long baseTrustMs = firstTrustedTimestampMs(base.timingStateTraces());
        long mergeTrustMs = firstTrustedTimestampMs(merge.timingStateTraces());
        CwDecodeEvent baseFirstMismatch = findFirstPostTrustMismatchCharacterEvent(
                base.decodeEvents(),
                baseTrustMs,
                EXPECTED_TEXT
        );
        CwDecodeEvent baseFirstCallsignUnknown = findFirstUnknownMismatchInWindow(
                base.decodeEvents(),
                baseTrustMs,
                EXPECTED_TEXT,
                CALLSIGN_WINDOW_START_MS,
                CALLSIGN_WINDOW_END_MS
        );

        System.out.println("==== recording(12) base-vs-merge ====");
        System.out.println("expected=" + EXPECTED_TEXT);
        System.out.println(String.format(
                Locale.US,
                "BASE  trust=%dms recall=%.3f rejects=%s text=%s",
                baseTrustMs,
                charRecall(EXPECTED_TEXT, base.probeResult().decodedText()),
                base.stableRejectCounts(),
                sanitize(base.probeResult().decodedText())
        ));
        System.out.println(String.format(
                Locale.US,
                "MERGE trust=%dms recall=%.3f rejects=%s text=%s",
                mergeTrustMs,
                charRecall(EXPECTED_TEXT, merge.probeResult().decodedText()),
                merge.stableRejectCounts(),
                sanitize(merge.probeResult().decodedText())
        ));

        if (baseFirstMismatch != null) {
            printWindowComparison(
                    "first-post-trust-mismatch-window",
                    baseFirstMismatch.timestampMs(),
                    base,
                    merge,
                    DE_WINDOW_PADDING_MS
            );
        }
        if (baseFirstCallsignUnknown != null) {
            printWindowComparison(
                    "first-callsign-unknown-window",
                    baseFirstCallsignUnknown.timestampMs(),
                    base,
                    merge,
                    CALLSIGN_WINDOW_PADDING_MS
            );
        }
    }

    private static LocalAudioDecodeTestSupport.OfflineDetailedProbeResult decode(
            List<AudioFrame> frames,
            boolean merge
    ) {
        return LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                merge ? "recording12-merge-compare" : "recording12-base-compare",
                frames,
                PREFERRED_TONE_HZ,
                SEED_WPM,
                SQL_PERCENT,
                false,
                false,
                false,
                merge,
                false,
                CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
        );
    }

    private static void printWindowComparison(
            String label,
            long anchorTimestampMs,
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult base,
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult merge,
            long paddingMs
    ) {
        System.out.println();
        System.out.println(String.format(
                Locale.US,
                "==== %s anchor=%d window=%d..%d ====",
                label,
                anchorTimestampMs,
                Math.max(0L, anchorTimestampMs - paddingMs),
                anchorTimestampMs + paddingMs
        ));
        printVariantWindow("BASE", anchorTimestampMs, base, paddingMs);
        printVariantWindow("MERGE", anchorTimestampMs, merge, paddingMs);
    }

    private static void printVariantWindow(
            String label,
            long anchorTimestampMs,
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed,
            long paddingMs
    ) {
        long windowStartMs = Math.max(0L, anchorTimestampMs - paddingMs);
        long windowEndMs = anchorTimestampMs + paddingMs;
        CwDecodeEvent nearestCharacter = findNearestCharacterEvent(
                detailed.decodeEvents(),
                anchorTimestampMs,
                NEAREST_CHARACTER_MAX_DISTANCE_MS
        );

        System.out.println("-- " + label + " --");
        System.out.println(String.format(
                Locale.US,
                "target=%s final=%s",
                renderCharacterEvent(nearestCharacter),
                sanitize(detailed.probeResult().decodedText())
        ));
        if (nearestCharacter != null) {
            System.out.println("char-neighborhood="
                    + renderCharacterNeighborhood(listCharacterEvents(detailed.decodeEvents()), nearestCharacter, 4));
        }

        System.out.println("decode-events:");
        printDecodeEventsWindow(detailed.decodeEvents(), windowStartMs, windowEndMs);

        System.out.println("stable-decisions:");
        printStableDecisionWindow(detailed.stableDecisionTraces(), windowStartMs, windowEndMs);

        System.out.println("tone-events:");
        printToneEventsWindow(detailed.toneEvents(), windowStartMs, windowEndMs);

        System.out.println("timing-events:");
        printTimingEventsWindow(detailed.timingEvents(), windowStartMs, windowEndMs);

        System.out.println("timing-state:");
        printTimingStateWindow(detailed.timingStateTraces(), windowStartMs, windowEndMs);

        System.out.println("frame-signals:");
        printFrameSignalWindow(detailed.frameSignalTraces(), windowStartMs, windowEndMs);
    }

    private static void printDecodeEventsWindow(
            List<CwDecodeEvent> decodeEvents,
            long windowStartMs,
            long windowEndMs
    ) {
        String previousText = "";
        boolean printed = false;
        for (CwDecodeEvent event : decodeEvents) {
            if (event == null || event.timestampMs() < windowStartMs || event.timestampMs() > windowEndMs) {
                continue;
            }
            String outputText = normalize(event.outputText());
            String appended = outputText.startsWith(previousText)
                    ? outputText.substring(previousText.length())
                    : normalize(event.emittedValue());
            System.out.println(String.format(
                    Locale.US,
                    "  @%d type=%s emit=%s seq=%s unk=%s append=%s out=%s",
                    event.timestampMs(),
                    event.type(),
                    compact(normalize(event.emittedValue())),
                    compact(event.sourceSequence()),
                    yesNo(event.unknownCharacter()),
                    compact(appended),
                    compact(outputText)
            ));
            previousText = outputText;
            printed = true;
        }
        if (!printed) {
            System.out.println("  none");
        }
    }

    private static void printStableDecisionWindow(
            List<LocalAudioDecodeTestSupport.StableDecisionTrace> traces,
            long windowStartMs,
            long windowEndMs
    ) {
        boolean printed = false;
        for (LocalAudioDecodeTestSupport.StableDecisionTrace trace : traces) {
            if (trace == null || trace.timestampMs() < windowStartMs || trace.timestampMs() > windowEndMs) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "  @%d emit=%s seq=%s decision=%s trusted=%s lock=%s lockR=%.2f near=%.2f unlock=%.2f dom=%.2f iso=%.2f rawWpm=%.1f",
                    trace.timestampMs(),
                    compact(normalize(trace.emittedValue())),
                    compact(trace.sourceSequence()),
                    compact(trace.decision()),
                    yesNo(trace.trustedTimingEstablished()),
                    yesNo(trace.targetToneLocked()),
                    trace.recentLockedFrameRatio(),
                    trace.recentNearTargetLockedFrameRatio(),
                    trace.recentActiveUnlockedFrameRatio(),
                    trace.toneDominanceRatio(),
                    trace.narrowbandIsolationRatio(),
                    trace.rawWpm()
            ));
            printed = true;
        }
        if (!printed) {
            System.out.println("  none");
        }
    }

    private static void printToneEventsWindow(
            List<CwToneEvent> toneEvents,
            long windowStartMs,
            long windowEndMs
    ) {
        boolean printed = false;
        for (CwToneEvent event : toneEvents) {
            if (event == null || event.timestampMs() < windowStartMs || event.timestampMs() > windowEndMs) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "  %s @%d dur=%d rms=%.1f peak=%d",
                    event.type(),
                    event.timestampMs(),
                    event.toneDurationMs(),
                    event.rmsAmplitude(),
                    event.peakAmplitude()
            ));
            printed = true;
        }
        if (!printed) {
            System.out.println("  none");
        }
    }

    private static void printTimingEventsWindow(
            List<CwTimingEvent> timingEvents,
            long windowStartMs,
            long windowEndMs
    ) {
        boolean printed = false;
        for (CwTimingEvent event : timingEvents) {
            if (event == null || event.timestampMs() < windowStartMs || event.timestampMs() > windowEndMs) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "  %s/%s @%d dur=%d dot=%d intra=%d rDot=%.2f rIntra=%.2f",
                    event.kind(),
                    event.classification(),
                    event.timestampMs(),
                    event.durationMs(),
                    event.dotEstimateMs(),
                    event.intraGapEstimateMs(),
                    event.ratioToDotEstimate(),
                    event.ratioToIntraGapEstimate()
            ));
            printed = true;
        }
        if (!printed) {
            System.out.println("  none");
        }
    }

    private static void printTimingStateWindow(
            List<LocalAudioDecodeTestSupport.TimingStateTrace> traces,
            long windowStartMs,
            long windowEndMs
    ) {
        long lastPrintedTimestampMs = Long.MIN_VALUE;
        boolean printed = false;
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : traces) {
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
            printed = true;
        }
        if (!printed) {
            System.out.println("  none");
        }
    }

    private static void printFrameSignalWindow(
            List<LocalAudioDecodeTestSupport.FrameSignalTrace> traces,
            long windowStartMs,
            long windowEndMs
    ) {
        long lastPrintedTimestampMs = Long.MIN_VALUE;
        boolean printed = false;
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
                    "  @%d det=%.1f on=%s lock=%s eff=%d final=%d locked=%.2f unlocked=%.2f dom=%.2f iso=%.2f thr=%d/%d lc=%.2f on+%d off+%d toneOn=%s rescue=%s suppress=%s release=%s attack=%s",
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
                    compact(trace.toneOnDecision()),
                    compact(trace.postReleaseRescueDecision()),
                    compact(trace.postReleaseSuppressionDecision()),
                    compact(trace.releaseTailHoldDecision()),
                    compact(trace.farAttackDelayDecision())
            ));
            printed = true;
        }
        if (!printed) {
            System.out.println("  none");
        }
    }

    private static String renderTimingStateTrace(LocalAudioDecodeTestSupport.TimingStateTrace trace) {
        DebugSnapshot debugSnapshot = trace.debugSnapshot();
        CwTimingSnapshot stabilized = trace.stabilizedSnapshot();
        CwTimingSnapshot raw = trace.rawSnapshot();
        return String.format(
                Locale.US,
                "  @%d tr=%.1f rf=%.1f pf=%.1f/%d act=%s last=%s stab=%d/%.1f raw=%d/%.1f obs=%s",
                trace.timestampMs(),
                debugSnapshot == null ? 0.0d : debugSnapshot.trustedDotEstimateMs(),
                debugSnapshot == null ? 0.0d : debugSnapshot.retainedDotEstimateMs(),
                debugSnapshot == null ? 0.0d : debugSnapshot.pendingFastTrustedDotEstimateMs(),
                debugSnapshot == null ? 0 : debugSnapshot.pendingFastTrustedEvidenceCount(),
                debugSnapshot == null ? "?" : compact(debugSnapshot.activeStrategyName()),
                debugSnapshot == null ? "?" : compact(debugSnapshot.lastEmissionStrategyName()),
                stabilized == null ? 0 : stabilized.dotEstimateMs(),
                stabilized == null ? 0.0d : renderWpm(stabilized),
                raw == null ? 0 : raw.dotEstimateMs(),
                raw == null ? 0.0d : renderWpm(raw),
                compact(trace.debugSummary())
        );
    }

    private static double renderWpm(CwTimingSnapshot snapshot) {
        if (snapshot == null) {
            return 0.0d;
        }
        if (snapshot.estimatedWpmPrecise() > 0.0d) {
            return snapshot.estimatedWpmPrecise();
        }
        return Math.max(0, snapshot.estimatedWpm());
    }

    private static long firstTrustedTimestampMs(List<LocalAudioDecodeTestSupport.TimingStateTrace> traces) {
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : traces) {
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
        List<CwDecodeEvent> mismatches = listPostTrustMismatchCharacterEvents(
                decodeEvents,
                trustTimestampMs,
                expectedText
        );
        return mismatches.isEmpty() ? null : mismatches.get(0);
    }

    private static CwDecodeEvent findFirstUnknownMismatchInWindow(
            List<CwDecodeEvent> decodeEvents,
            long trustTimestampMs,
            String expectedText,
            long windowStartMs,
            long windowEndMs
    ) {
        List<CwDecodeEvent> mismatches = listPostTrustMismatchCharacterEvents(
                decodeEvents,
                trustTimestampMs,
                expectedText
        );
        for (CwDecodeEvent mismatch : mismatches) {
            if (mismatch != null
                    && mismatch.unknownCharacter()
                    && mismatch.timestampMs() >= windowStartMs
                    && mismatch.timestampMs() <= windowEndMs) {
                return mismatch;
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

    private static CwDecodeEvent findNearestCharacterEvent(
            List<CwDecodeEvent> decodeEvents,
            long targetTimestampMs,
            long maxDistanceMs
    ) {
        CwDecodeEvent nearest = null;
        long nearestDistanceMs = Long.MAX_VALUE;
        for (CwDecodeEvent event : decodeEvents) {
            if (event == null || event.type() != CwDecodeEvent.Type.CHARACTER_DECODED) {
                continue;
            }
            long distanceMs = Math.abs(event.timestampMs() - targetTimestampMs);
            if (distanceMs > maxDistanceMs) {
                continue;
            }
            if (distanceMs < nearestDistanceMs) {
                nearest = event;
                nearestDistanceMs = distanceMs;
            }
        }
        return nearest;
    }

    private static List<CwDecodeEvent> listCharacterEvents(List<CwDecodeEvent> decodeEvents) {
        ArrayList<CwDecodeEvent> characters = new ArrayList<>();
        for (CwDecodeEvent event : decodeEvents) {
            if (event != null && event.type() == CwDecodeEvent.Type.CHARACTER_DECODED) {
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
                    .append(':').append(compact(normalize(event.emittedValue())))
                    .append('/').append(compact(event.sourceSequence()))
                    .append("/unk=").append(yesNo(event.unknownCharacter()));
            if (index == targetIndex) {
                builder.append("<<");
            }
        }
        return builder.toString();
    }

    private static String renderCharacterEvent(CwDecodeEvent event) {
        if (event == null) {
            return "none";
        }
        return String.format(
                Locale.US,
                "@%d emit=%s seq=%s unk=%s out=%s",
                event.timestampMs(),
                compact(normalize(event.emittedValue())),
                compact(event.sourceSequence()),
                yesNo(event.unknownCharacter()),
                compact(normalize(event.outputText()))
        );
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

    private static double charRecall(String expectedText, String actualText) {
        String expected = canonicalize(expectedText);
        String actual = canonicalize(actualText);
        if (expected.isEmpty()) {
            return actual.isEmpty() ? 1.0d : 0.0d;
        }
        return longestCommonSubsequenceLength(expected, actual) / (double) expected.length();
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

    private static boolean isInterestingDecision(String value) {
        if (value == null) {
            return false;
        }
        return !"NONE".equals(value)
                && !"ELIGIBLE".equals(value)
                && !"BLOCKED:BASIC_PRECONDITION".equals(value);
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u25A1', '?');
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace('\u25A1', '?');
    }

    private static String compact(String value) {
        if (value == null) {
            return "-";
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "-" : trimmed;
    }

    private static String yesNo(boolean value) {
        return value ? "Y" : "N";
    }
}
