package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertTrue;

public final class CwRecording7LiveVsFixedDivergenceProbeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;
    private static final long WINDOW_PADDING_MS = 900L;
    private static final long MICROSCOPE_START_MS = 9480L;
    private static final long MICROSCOPE_END_MS = 9800L;
    private static final String PRIMARY_PATTERN = "DE BI3TUK KN";
    private static final String FALLBACK_PATTERN = "BI3TUK KN";

    @Test
    public void printRecording7LiveVsFixedDivergence() throws Exception {
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

        ReplayTimeline liveFinal = buildTimeline(liveDetailed.decodeEvents());
        ReplayTimeline fixedFinal = buildTimeline(fixedDetailed.decodeEvents());
        ReplayTimeline liveRaw = buildTimeline(liveDetailed.rawDecodeEvents());
        ReplayTimeline fixedRaw = buildTimeline(fixedDetailed.rawDecodeEvents());
        ReplayTimeline liveStable = buildTimeline(liveDetailed.stableAcceptedDecodeEvents());
        ReplayTimeline fixedStable = buildTimeline(fixedDetailed.stableAcceptedDecodeEvents());

        WindowMatch focus = findPreferredWindowMatch(fixedFinal, PRIMARY_PATTERN, FALLBACK_PATTERN);
        if (focus == null) {
            throw new IllegalStateException("Missing fixed key-info pattern for recording(7)");
        }

        long windowStartMs = Math.max(0L, focus.firstTimestampMs - WINDOW_PADDING_MS);
        long windowEndMs = focus.lastTimestampMs + WINDOW_PADDING_MS;

        System.out.println("==== recording(7) live-vs-fixed divergence ====");
        System.out.println("focusPattern=" + focus.pattern
                + " chars=" + focus.startIndex + ".." + (focus.endExclusive - 1)
                + " ts=" + focus.firstTimestampMs + ".." + focus.lastTimestampMs
                + " window=" + windowStartMs + ".." + windowEndMs);
        System.out.println();

        System.out.println("-- text summary --");
        System.out.println("live-final =" + sanitize(liveFinal.text));
        System.out.println("fixed-final=" + sanitize(fixedFinal.text));
        System.out.println("live-raw   =" + sanitize(liveRaw.text));
        System.out.println("fixed-raw  =" + sanitize(fixedRaw.text));
        System.out.println("live-stable=" + sanitize(liveStable.text));
        System.out.println("fixed-stable=" + sanitize(fixedStable.text));
        System.out.println(String.format(
                Locale.US,
                "contains-final live=%s fixed=%s | contains-raw live=%s fixed=%s | contains-stable live=%s fixed=%s",
                yesNo(liveFinal.text.contains(PRIMARY_PATTERN) || liveFinal.text.contains(FALLBACK_PATTERN)),
                yesNo(fixedFinal.text.contains(PRIMARY_PATTERN) || fixedFinal.text.contains(FALLBACK_PATTERN)),
                yesNo(liveRaw.text.contains(PRIMARY_PATTERN) || liveRaw.text.contains(FALLBACK_PATTERN)),
                yesNo(fixedRaw.text.contains(PRIMARY_PATTERN) || fixedRaw.text.contains(FALLBACK_PATTERN)),
                yesNo(liveStable.text.contains(PRIMARY_PATTERN) || liveStable.text.contains(FALLBACK_PATTERN)),
                yesNo(fixedStable.text.contains(PRIMARY_PATTERN) || fixedStable.text.contains(FALLBACK_PATTERN))
        ));
        System.out.println();

        printDecodedWindow("live-final", liveFinal, windowStartMs, windowEndMs);
        printDecodedWindow("fixed-final", fixedFinal, windowStartMs, windowEndMs);
        printDecodedWindow("live-raw", liveRaw, windowStartMs, windowEndMs);
        printDecodedWindow("fixed-raw", fixedRaw, windowStartMs, windowEndMs);
        printDecodedWindow("live-stable", liveStable, windowStartMs, windowEndMs);
        printDecodedWindow("fixed-stable", fixedStable, windowStartMs, windowEndMs);

        printStableDecisionWindow("live-stable-decisions", liveDetailed.stableDecisionTraces(), windowStartMs, windowEndMs);
        printStableDecisionWindow("fixed-stable-decisions", fixedDetailed.stableDecisionTraces(), windowStartMs, windowEndMs);

        printToneWindow("live-tone", liveDetailed.toneEvents(), windowStartMs, windowEndMs);
        printToneWindow("fixed-tone", fixedDetailed.toneEvents(), windowStartMs, windowEndMs);
        printTimingWindow("live-timing", liveDetailed.timingEvents(), windowStartMs, windowEndMs);
        printTimingWindow("fixed-timing", fixedDetailed.timingEvents(), windowStartMs, windowEndMs);
        printMicroscopeWindow("live-microscope", liveDetailed, MICROSCOPE_START_MS, MICROSCOPE_END_MS);
        printMicroscopeWindow("fixed-microscope", fixedDetailed, MICROSCOPE_START_MS, MICROSCOPE_END_MS);

        assertTrue(true);
    }

    private static WindowMatch findPreferredWindowMatch(ReplayTimeline timeline, String... patterns) {
        for (String pattern : patterns) {
            if (pattern == null || pattern.isEmpty()) {
                continue;
            }
            WindowMatch match = findFirstPatternMatch(timeline, pattern);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static WindowMatch findFirstPatternMatch(ReplayTimeline timeline, String pattern) {
        int matchIndex = timeline.text.indexOf(pattern);
        if (matchIndex < 0) {
            return null;
        }
        int endExclusive = matchIndex + pattern.length();
        if (endExclusive > timeline.decodedChars.size()) {
            return null;
        }
        DecodedCharStamp first = timeline.decodedChars.get(matchIndex);
        DecodedCharStamp last = timeline.decodedChars.get(endExclusive - 1);
        return new WindowMatch(pattern, matchIndex, endExclusive, first.timestampMs, last.timestampMs);
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

    private static void printStableDecisionWindow(
            String label,
            List<LocalAudioDecodeTestSupport.StableDecisionTrace> traces,
            long startTimestampMs,
            long endTimestampMs
    ) {
        System.out.println("-- " + label + " --");
        boolean any = false;
        for (LocalAudioDecodeTestSupport.StableDecisionTrace trace : traces) {
            if (trace == null || trace.timestampMs() < startTimestampMs || trace.timestampMs() > endTimestampMs) {
                continue;
            }
            any = true;
            System.out.println(String.format(
                    Locale.US,
                    "  @%d emit=%s seq=%s decision=%s trust=%s locked=%s rawWpm=%.1f dom=%.2f iso=%.2f",
                    trace.timestampMs(),
                    compact(trace.emittedValue()),
                    compact(trace.sourceSequence()),
                    trace.decision(),
                    yesNo(trace.trustedTimingEstablished()),
                    yesNo(trace.targetToneLocked()),
                    trace.rawWpm(),
                    trace.toneDominanceRatio(),
                    trace.narrowbandIsolationRatio()
            ));
        }
        if (!any) {
            System.out.println("  none");
        }
    }

    private static void printToneWindow(
            String label,
            List<CwToneEvent> toneEvents,
            long startTimestampMs,
            long endTimestampMs
    ) {
        System.out.println("-- " + label + " --");
        boolean any = false;
        for (CwToneEvent event : toneEvents) {
            if (event == null || event.timestampMs() < startTimestampMs || event.timestampMs() > endTimestampMs) {
                continue;
            }
            any = true;
            System.out.println(String.format(
                    Locale.US,
                    "  %s @%d dur=%d rms=%.1f peak=%d",
                    event.type(),
                    event.timestampMs(),
                    event.toneDurationMs(),
                    event.rmsAmplitude(),
                    event.peakAmplitude()
            ));
        }
        if (!any) {
            System.out.println("  none");
        }
    }

    private static void printTimingWindow(
            String label,
            List<CwTimingEvent> timingEvents,
            long startTimestampMs,
            long endTimestampMs
    ) {
        System.out.println("-- " + label + " --");
        boolean any = false;
        for (CwTimingEvent event : timingEvents) {
            if (event == null || event.timestampMs() < startTimestampMs || event.timestampMs() > endTimestampMs) {
                continue;
            }
            any = true;
            System.out.println(String.format(
                    Locale.US,
                    "  %s/%s @%d dur=%d dot=%d intra=%d",
                    event.kind(),
                    event.classification(),
                    event.timestampMs(),
                    event.durationMs(),
                    event.dotEstimateMs(),
                    event.intraGapEstimateMs()
            ));
        }
        if (!any) {
            System.out.println("  none");
        }
    }

    private static void printMicroscopeWindow(
            String label,
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed,
            long startTimestampMs,
            long endTimestampMs
    ) {
        System.out.println("-- " + label + " --");
        printMicroscopeFrames(detailed.frameSignalTraces(), detailed.rxToneModeDecisionTraces(), startTimestampMs, endTimestampMs);
        printMicroscopeTimingState(detailed.timingStateTraces(), startTimestampMs, endTimestampMs);
        printMicroscopeDecodeEvents(detailed.rawDecodeEvents(), startTimestampMs, endTimestampMs);
    }

    private static void printMicroscopeFrames(
            List<LocalAudioDecodeTestSupport.FrameSignalTrace> traces,
            List<LocalAudioDecodeTestSupport.RxToneModeDecisionTrace> modeTraces,
            long startTimestampMs,
            long endTimestampMs
    ) {
        System.out.println("  frames:");
        boolean any = false;
        for (LocalAudioDecodeTestSupport.FrameSignalTrace trace : traces) {
            if (trace == null || trace.snapshot() == null) {
                continue;
            }
            long timestampMs = trace.timestampMs();
            if (timestampMs < startTimestampMs || timestampMs > endTimestampMs) {
                continue;
            }
            any = true;
            LocalAudioDecodeTestSupport.RxToneModeDecisionTrace modeTrace =
                    latestModeTraceAtOrBefore(modeTraces, timestampMs);
            System.out.println(String.format(
                    Locale.US,
                    "    @%d mode=%s/%s trust=%s fixedProg=%s det=%.1f act=%s lock=%s cons=%d lockR=%.2f near=%.2f far=%.2f"
                            + " tgt=%d eff=%d aq=%d final=%d hyp=%d/%d/%s conf=%.2f"
                            + " thr=%d/%d tone=%.1f dom=%.2f iso=%.2f lc=%.2f"
                            + " attQ=%s mem=%s anchor=%d onThr=%d localOn=%d"
                            + " gap=%d/%d rescueWin=%s/%d rescueCnt=%d"
                            + " weakCand=%s trustedCand=%s trustedChain=%s/%d gapLimit=%d"
                            + " toneOn=%s accept=%s/%s rescue=%s suppress=%s delay=%s hold=%s curRescue=%s tail=%s",
                    timestampMs,
                    modeTrace == null ? "?" : modeTrace.resolvedMode(),
                    modeTrace == null ? "?" : compact(modeTrace.strategy()),
                    yesNo(modeTrace != null && modeTrace.trustedTimingEstablished()),
                    yesNo(modeTrace != null && modeTrace.usefulFixedProgress()),
                    trace.detectionLevel(),
                    yesNo(trace.snapshot().toneActive()),
                    yesNo(trace.snapshot().targetToneLocked()),
                    trace.snapshot().consecutiveLockedFrames(),
                    trace.snapshot().recentLockedFrameRatio(),
                    trace.snapshot().recentNearTargetLockedFrameRatio(),
                    trace.snapshot().recentFarOffTargetLockedFrameRatio(),
                    trace.snapshot().targetToneFrequencyHz(),
                    trace.snapshot().effectiveTrackedToneFrequencyHz(),
                    trace.snapshot().effectiveAcquisitionWinnerFrequencyHz(),
                    trace.snapshot().effectiveFinalAdoptedFrequencyHz(),
                    trace.snapshot().toneHypothesisFrequencyHz(),
                    trace.snapshot().toneHypothesisSupportFrames(),
                    compact(trace.snapshot().toneHypothesisSource()),
                    trace.snapshot().toneHypothesisConfidence(),
                    trace.snapshot().currentThreshold(),
                    trace.snapshot().releaseThreshold(),
                    trace.snapshot().lastToneRmsAmplitude(),
                    trace.snapshot().toneDominanceRatio(),
                    trace.snapshot().narrowbandIsolationRatio(),
                    trace.localContrastRatio(),
                    yesNo(trace.attackQualified()),
                    yesNo(trace.trackedToneMemoryActiveBeforeFrame()),
                    trace.attackAnchorFrequencyHzBeforeFrame(),
                    trace.toneOnThreshold(),
                    trace.frameLocalToneOnTimestampMs(),
                    trace.postReleaseGapMs(),
                    trace.postReleaseWindowMs(),
                    yesNo(trace.postReleaseRescueContinuationWindowActive()),
                    trace.postReleaseRescueContinuationWindowRemainingMs(),
                    trace.postReleaseWeakContinuityRescueCount(),
                    yesNo(trace.weakPostReleaseOnsetChainCandidate()),
                    yesNo(trace.trustedContinuityToneOnCandidate()),
                    yesNo(trace.trustedWeakPostReleaseOnsetChainActive()),
                    trace.trustedWeakPostReleaseOnsetChainFrameCount(),
                    trace.postReleaseWeakContinuityGapLimitMs(),
                    compact(trace.toneOnDecision()),
                    yesNo(trace.toneOnAccepted()),
                    yesNo(trace.toneOnAcceptedByRescue()),
                    compact(trace.postReleaseRescueDecision()),
                    compact(trace.postReleaseSuppressionDecision()),
                    compact(trace.farAttackDelayDecision()),
                    compact(trace.releaseTailHoldDecision()),
                    yesNo(trace.currentToneStartedByPostReleaseRescue()),
                    yesNo(trace.releaseTailHoldApplied())
            ));
            System.out.println("      src aq=" + compact(trace.snapshot().acquisitionWinnerSource())
                    + " final=" + compact(trace.snapshot().finalAdoptedSource())
                    + " aqDetail=" + compact(trace.snapshot().acquisitionDecisionDetail())
                    + " finalDetail=" + compact(trace.snapshot().finalAdoptionDetail()));
            System.out.println("      cand pref=" + compact(trace.snapshot().preferredWindowTopCandidatesSummary()));
            System.out.println("      cand wide=" + compact(trace.snapshot().wideScanTopCandidatesSummary()));
        }
        if (!any) {
            System.out.println("    none");
        }
    }

    private static void printMicroscopeTimingState(
            List<LocalAudioDecodeTestSupport.TimingStateTrace> traces,
            long startTimestampMs,
            long endTimestampMs
    ) {
        System.out.println("  timing-state:");
        boolean any = false;
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : traces) {
            if (trace == null || trace.timestampMs() < startTimestampMs || trace.timestampMs() > endTimestampMs) {
                continue;
            }
            any = true;
            double trustedDotMs = trace.debugSnapshot() == null ? 0.0d : trace.debugSnapshot().trustedDotEstimateMs();
            double retainedDotMs = trace.debugSnapshot() == null ? 0.0d : trace.debugSnapshot().retainedDotEstimateMs();
            double pendingFastTrustedDotMs = trace.debugSnapshot() == null
                    ? 0.0d
                    : trace.debugSnapshot().pendingFastTrustedDotEstimateMs();
            String strategy = trace.debugSnapshot() == null
                    ? "NONE"
                    : compact(trace.debugSnapshot().lastStrategyDecision());
            String trustedReason = trace.debugSnapshot() == null
                    ? "NONE"
                    : compact(trace.debugSnapshot().lastTrustedUpdateReason());
            System.out.println(String.format(
                    Locale.US,
                    "    @%d trustedDot=%.1f retainedDot=%.1f pendingFast=%.1f strategy=%s trustedReason=%s summary=%s",
                    trace.timestampMs(),
                    trustedDotMs,
                    retainedDotMs,
                    pendingFastTrustedDotMs,
                    strategy,
                    trustedReason,
                    compact(trace.debugSummary())
            ));
        }
        if (!any) {
            System.out.println("    none");
        }
    }

    private static void printMicroscopeDecodeEvents(
            List<CwDecodeEvent> events,
            long startTimestampMs,
            long endTimestampMs
    ) {
        System.out.println("  raw-decode-events:");
        boolean any = false;
        for (CwDecodeEvent event : events) {
            if (event == null || event.timestampMs() < startTimestampMs || event.timestampMs() > endTimestampMs) {
                continue;
            }
            any = true;
            System.out.println(String.format(
                    Locale.US,
                    "    @%d %s emit=%s seq=%s out=%s unk=%s",
                    event.timestampMs(),
                    event.type(),
                    compact(normalizeNullable(event.emittedValue())),
                    compact(normalizeNullable(event.sourceSequence())),
                    compact(normalizeOutputText(event.outputText())),
                    yesNo(event.unknownCharacter())
            ));
        }
        if (!any) {
            System.out.println("    none");
        }
    }

    private static LocalAudioDecodeTestSupport.RxToneModeDecisionTrace latestModeTraceAtOrBefore(
            List<LocalAudioDecodeTestSupport.RxToneModeDecisionTrace> traces,
            long timestampMs
    ) {
        LocalAudioDecodeTestSupport.RxToneModeDecisionTrace latest = null;
        for (LocalAudioDecodeTestSupport.RxToneModeDecisionTrace trace : traces) {
            if (trace == null || trace.timestampMs() > timestampMs) {
                continue;
            }
            latest = trace;
        }
        return latest;
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

    private static String compact(String text) {
        if (text == null) {
            return "-";
        }
        String normalized = text.replace('\u25A1', '?').trim();
        return normalized.isEmpty() ? "-" : normalized;
    }

    private static String yesNo(boolean value) {
        return value ? "Y" : "N";
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

    private static final class WindowMatch {
        private final String pattern;
        private final int startIndex;
        private final int endExclusive;
        private final long firstTimestampMs;
        private final long lastTimestampMs;

        private WindowMatch(
                String pattern,
                int startIndex,
                int endExclusive,
                long firstTimestampMs,
                long lastTimestampMs
        ) {
            this.pattern = pattern;
            this.startIndex = startIndex;
            this.endExclusive = endExclusive;
            this.firstTimestampMs = firstTimestampMs;
            this.lastTimestampMs = lastTimestampMs;
        }
    }
}
