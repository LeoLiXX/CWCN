package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.eval.CwFixtureLibrary;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CwFixtureGapMicroWindowProbeTest {
    private static final long WINDOW_START_MS = 14100L;
    private static final long WINDOW_END_MS = 18650L;
    private static final long TONE_SWEEP_DROPPED_WINDOW_START_MS = 8000L;
    private static final long TONE_SWEEP_DROPPED_WINDOW_END_MS = 19650L;
    private static final double TARGET_LETTER_LIKE_RATIO = 5.0d;

    @Test
    public void traceHumanHesitationGapReportTailWordGapWindow() {
        traceScenarioWindow("human_hesitation_gap_report_exchange", WINDOW_START_MS, WINDOW_END_MS);
    }

    @Test
    public void traceToneSweepDroppedMidMessageWindow() {
        traceScenarioWindow(
                "user_tone_sweep_vvv_18wpm",
                TONE_SWEEP_DROPPED_WINDOW_START_MS,
                TONE_SWEEP_DROPPED_WINDOW_END_MS
        );
    }

    @Test
    public void traceToneSweepDroppedMidMessageWindowFrom450Preferred() {
        traceScenarioWindow(
                "user_tone_sweep_vvv_18wpm",
                450,
                TONE_SWEEP_DROPPED_WINDOW_START_MS,
                TONE_SWEEP_DROPPED_WINDOW_END_MS
        );
    }

    private void traceScenarioWindow(String scenarioId, long windowStartMs, long windowEndMs) {
        CwFixtureScenario scenario = findScenario(scenarioId);
        traceScenarioWindow(
                scenarioId,
                scenario.toneFrequencyHz(),
                windowStartMs,
                windowEndMs
        );
    }

    private void traceScenarioWindow(
            String scenarioId,
            int preferredToneFrequencyHz,
            long windowStartMs,
            long windowEndMs
    ) {
        CwFixtureScenario scenario = findScenario(scenarioId);
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        List<AudioFrame> frames = source.renderFramesForTesting(scenario);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        scenario.id(),
                        frames,
                        preferredToneFrequencyHz,
                        scenario.wpm(),
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        System.out.println("==== gap micro-window: " + scenarioId + " ====");
        System.out.println("preferredTone=" + preferredToneFrequencyHz);
        System.out.println("expectedRaw=" + scenario.expectedRawText());
        System.out.println("actualRaw=" + sanitize(detailed.probeResult().decodedText()));
        System.out.println(String.format(
                Locale.US,
                "window=%d..%d finalDot=%d finalWpm=%.2f",
                windowStartMs,
                windowEndMs,
                detailed.probeResult().timingSnapshot().dotEstimateMs(),
                detailed.probeResult().timingSnapshot().estimatedWpmPrecise()
        ));

        printTimingStatePivots(detailed, windowStartMs, windowEndMs);
        printTimingEvents(detailed, windowStartMs, windowEndMs);
        printWordGapMath(detailed, windowStartMs, windowEndMs);
        printWordGapFrontEndWindows(detailed, windowStartMs, windowEndMs);
    }

    private void printTimingStatePivots(
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed,
            long windowStartMs,
            long windowEndMs
    ) {
        System.out.println("-- timing state pivots --");
        long lastRawDot = Long.MIN_VALUE;
        long lastStableDot = Long.MIN_VALUE;
        long lastTrustedDot = Long.MIN_VALUE;
        String lastReason = null;
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : detailed.timingStateTraces()) {
            if (trace == null
                    || trace.timestampMs() < windowStartMs
                    || trace.timestampMs() > windowEndMs) {
                continue;
            }
            long rawDot = trace.rawSnapshot() == null ? 0L : trace.rawSnapshot().dotEstimateMs();
            long stableDot = trace.stabilizedSnapshot() == null ? 0L : trace.stabilizedSnapshot().dotEstimateMs();
            long trustedDot = trace.debugSnapshot() == null
                    ? 0L
                    : Math.round(trace.debugSnapshot().trustedDotEstimateMs());
            String reason = trace.debugSnapshot() == null
                    ? "none"
                    : safe(trace.debugSnapshot().lastTrustedUpdateReason());
            if (rawDot == lastRawDot
                    && stableDot == lastStableDot
                    && trustedDot == lastTrustedDot
                    && safe(reason).equals(safe(lastReason))) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "@%d rawDot=%d rawWpm=%.2f stableDot=%d stableWpm=%.2f trustDot=%d reason=%s",
                    trace.timestampMs(),
                    rawDot,
                    trace.rawSnapshot() == null ? 0.0d : trace.rawSnapshot().estimatedWpmPrecise(),
                    stableDot,
                    trace.stabilizedSnapshot() == null ? 0.0d : trace.stabilizedSnapshot().estimatedWpmPrecise(),
                    trustedDot,
                    reason
            ));
            lastRawDot = rawDot;
            lastStableDot = stableDot;
            lastTrustedDot = trustedDot;
            lastReason = reason;
        }
    }

    private void printTimingEvents(
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed,
            long windowStartMs,
            long windowEndMs
    ) {
        System.out.println("-- timing events --");
        for (CwTimingEvent timingEvent : detailed.timingEvents()) {
            if (timingEvent == null
                    || timingEvent.timestampMs() < windowStartMs
                    || timingEvent.timestampMs() > windowEndMs) {
                continue;
            }
            LocalAudioDecodeTestSupport.TimingEventAdaptationTrace trace = adaptationTraceAt(
                    detailed.timingEventAdaptationTraces(),
                    timingEvent
            );
            List<CwDecodeEvent> emitted = decodeEventsAtTimestamp(
                    detailed.rawDecodeEvents(),
                    timingEvent.timestampMs()
            );
            System.out.println(String.format(
                    Locale.US,
                    "@%d %s/%s dur=%d raw=%s/%d(r=%.2f) wpm=%s/%d(r=%.2f) anc=%s/%d(r=%.2f) trust=%s/%s emit=%s",
                    timingEvent.timestampMs(),
                    timingEvent.kind(),
                    timingEvent.classification(),
                    timingEvent.durationMs(),
                    trace == null ? "-" : trace.rawClassification(),
                    trace == null ? 0L : trace.rawDotEstimateMs(),
                    ratio(timingEvent.durationMs(), trace == null ? 0L : trace.rawDotEstimateMs()),
                    trace == null ? "-" : trace.wpmGuardClassification(),
                    trace == null ? 0L : trace.wpmGuardDotEstimateMs(),
                    ratio(timingEvent.durationMs(), trace == null ? 0L : trace.wpmGuardDotEstimateMs()),
                    trace == null ? "-" : trace.anchorClassification(),
                    trace == null ? 0L : trace.anchorDotEstimateMs(),
                    ratio(timingEvent.durationMs(), trace == null ? 0L : trace.anchorDotEstimateMs()),
                    trace != null && trace.trustedTimingEstablished() ? "yes" : "no",
                    trace == null ? "NONE" : safe(trace.trustOrigin()),
                    renderDecodeEvents(emitted)
            ));
        }
    }

    private void printWordGapMath(
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed,
            long windowStartMs,
            long windowEndMs
    ) {
        System.out.println("-- word-gap math --");
        for (CwTimingEvent timingEvent : detailed.timingEvents()) {
            if (timingEvent == null
                    || timingEvent.kind() != CwTimingEvent.Kind.GAP
                    || timingEvent.timestampMs() < windowStartMs
                    || timingEvent.timestampMs() > windowEndMs) {
                continue;
            }
            LocalAudioDecodeTestSupport.TimingEventAdaptationTrace trace = adaptationTraceAt(
                    detailed.timingEventAdaptationTraces(),
                    timingEvent
            );
            if (trace == null || !"WORD_GAP".equals(trace.anchorClassification())) {
                continue;
            }
            double neededDotMs = timingEvent.durationMs() / TARGET_LETTER_LIKE_RATIO;
            System.out.println(String.format(
                    Locale.US,
                    "@%d gap=%dms anchorDot=%d ratio=%.2f needDotFor%.1f=%.1fms delta=%.1fms (+%.0f%%)",
                    timingEvent.timestampMs(),
                    timingEvent.durationMs(),
                    trace.anchorDotEstimateMs(),
                    ratio(timingEvent.durationMs(), trace.anchorDotEstimateMs()),
                    TARGET_LETTER_LIKE_RATIO,
                    neededDotMs,
                    neededDotMs - trace.anchorDotEstimateMs(),
                    percentDelta(trace.anchorDotEstimateMs(), neededDotMs)
            ));
        }
    }

    private void printWordGapFrontEndWindows(
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed,
            long windowStartMs,
            long windowEndMs
    ) {
        System.out.println("-- word-gap front-end windows --");
        for (CwTimingEvent timingEvent : detailed.timingEvents()) {
            if (timingEvent == null
                    || timingEvent.kind() != CwTimingEvent.Kind.GAP
                    || timingEvent.timestampMs() < windowStartMs
                    || timingEvent.timestampMs() > windowEndMs) {
                continue;
            }
            LocalAudioDecodeTestSupport.TimingEventAdaptationTrace trace = adaptationTraceAt(
                    detailed.timingEventAdaptationTraces(),
                    timingEvent
            );
            if (trace == null || !"WORD_GAP".equals(trace.anchorClassification())) {
                continue;
            }
            long gapStartMs = Math.max(0L, timingEvent.timestampMs() - timingEvent.durationMs());
            long frameWindowStartMs = Math.max(0L, gapStartMs - 96L);
            long frameWindowEndMs = timingEvent.timestampMs() + 128L;
            System.out.println(String.format(
                    Locale.US,
                    "gapWindow @%d start=%d dur=%d raw=%d anc=%d ratio=%.2f",
                    timingEvent.timestampMs(),
                    gapStartMs,
                    timingEvent.durationMs(),
                    trace.rawDotEstimateMs(),
                    trace.anchorDotEstimateMs(),
                    ratio(timingEvent.durationMs(), trace.anchorDotEstimateMs())
            ));
            printFrameSignalWindow(detailed.frameSignalTraces(), frameWindowStartMs, frameWindowEndMs);
        }
    }

    private LocalAudioDecodeTestSupport.TimingEventAdaptationTrace adaptationTraceAt(
            List<LocalAudioDecodeTestSupport.TimingEventAdaptationTrace> traces,
            CwTimingEvent timingEvent
    ) {
        if (traces == null || timingEvent == null) {
            return null;
        }
        for (LocalAudioDecodeTestSupport.TimingEventAdaptationTrace trace : traces) {
            if (trace == null
                    || trace.timestampMs() != timingEvent.timestampMs()
                    || trace.durationMs() != timingEvent.durationMs()) {
                continue;
            }
            if (!safe(trace.eventKind()).equals(timingEvent.kind().name())) {
                continue;
            }
            return trace;
        }
        return null;
    }

    private List<CwDecodeEvent> decodeEventsAtTimestamp(List<CwDecodeEvent> decodeEvents, long timestampMs) {
        ArrayList<CwDecodeEvent> emitted = new ArrayList<>();
        if (decodeEvents == null) {
            return emitted;
        }
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            if (decodeEvent == null || decodeEvent.timestampMs() != timestampMs) {
                continue;
            }
            emitted.add(decodeEvent);
        }
        return emitted;
    }

    private String renderDecodeEvents(List<CwDecodeEvent> decodeEvents) {
        if (decodeEvents == null || decodeEvents.isEmpty()) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < decodeEvents.size(); index++) {
            if (index > 0) {
                builder.append(" | ");
            }
            CwDecodeEvent decodeEvent = decodeEvents.get(index);
            builder.append(decodeEvent.type())
                    .append(':')
                    .append(safe(decodeEvent.emittedValue()))
                    .append(" text=")
                    .append(sanitize(decodeEvent.outputText()));
        }
        return builder.toString();
    }

    private CwFixtureScenario findScenario(String scenarioId) {
        for (CwFixtureScenario scenario : CwFixtureLibrary.scenarios()) {
            if (scenario.id().equals(scenarioId)) {
                return scenario;
            }
        }
        throw new IllegalArgumentException("Unknown scenario: " + scenarioId);
    }

    private static double ratio(long durationMs, long dotMs) {
        return durationMs / (double) Math.max(1L, dotMs);
    }

    private static double percentDelta(long baseMs, double targetMs) {
        return ((targetMs - Math.max(1L, baseMs)) / Math.max(1L, baseMs)) * 100.0d;
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
                    "  frame @%d det=%.1f act=%s lock=%s eff=%d final=%d thr=%d/%d lc=%.2f on+%d off+%d"
                            + " gap=%d/%d rescueWin=%s/%d weak=%d trusted=%s mem=%s anchor=%d"
                            + " toneOn=%s rescue=%s suppress=%s release=%s attack=%s last=%s",
                    trace.timestampMs(),
                    trace.detectionLevel(),
                    yesNo(trace.snapshot().toneActive()),
                    yesNo(trace.snapshot().targetToneLocked()),
                    trace.snapshot().effectiveTrackedToneFrequencyHz(),
                    trace.snapshot().effectiveFinalAdoptedFrequencyHz(),
                    trace.snapshot().currentThreshold(),
                    trace.snapshot().releaseThreshold(),
                    trace.localContrastRatio(),
                    deltaOn,
                    deltaOff,
                    trace.postReleaseGapMs(),
                    trace.postReleaseWindowMs(),
                    yesNo(trace.postReleaseRescueContinuationWindowActive()),
                    trace.postReleaseRescueContinuationWindowRemainingMs(),
                    trace.postReleaseWeakContinuityRescueCount(),
                    yesNo(trace.trustedContinuityToneOnCandidate()),
                    yesNo(trace.trackedToneMemoryActiveBeforeFrame()),
                    trace.attackAnchorFrequencyHzBeforeFrame(),
                    compact(trace.toneOnDecision()),
                    compact(trace.postReleaseRescueDecision()),
                    compact(trace.postReleaseSuppressionDecision()),
                    compact(trace.releaseTailHoldDecision()),
                    compact(trace.farAttackDelayDecision()),
                    renderLastEvent(trace.snapshot().lastEvent())
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

    private static String yesNo(boolean value) {
        return value ? "Y" : "N";
    }

    private static String compact(String value) {
        return safe(value).replace(' ', '_');
    }

    private static String sanitize(String text) {
        return safe(text).replace('\u25A1', '?');
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }
}
