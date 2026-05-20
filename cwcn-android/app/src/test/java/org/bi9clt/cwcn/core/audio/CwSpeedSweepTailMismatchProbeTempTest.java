package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.eval.CwFixtureLibrary;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CwSpeedSweepTailMismatchProbeTempTest {
    private static final long FIRST_CALLSIGN_START_MS = 9000L;
    private static final long FIRST_CALLSIGN_END_MS = 16850L;
    private static final long START_MS = 16500L;
    private static final long END_MS = 22150L;

    @Test
    public void printSpeedSweepTailAcrossReplayModes() {
        CwFixtureScenario scenario = findScenario("user_speed_sweep_vvv_700hz");
        List<AudioFrame> frames = new SyntheticFixtureRxAudioSource().renderFramesForTesting(scenario);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        scenario.id(),
                        frames,
                        700,
                        scenario.wpm(),
                        55,
                        false,
                        CwInterpreter.RecoveryMode.SEMANTIC_RECOVERY
                );

        LinkedHashMap<String, TailView> views = new LinkedHashMap<>();
        views.put("BASE", TailView.fromDetailed(detailed));
        views.put("BASE_REDECODE_2W", TailView.fromRedecode(LocalAudioDecodeTestSupport.redecodeTrailingWords(detailed, 2)));
        views.put("TRK", TailView.fromReplay(LocalAudioDecodeTestSupport.replayForcedTrackedToneDecode(detailed)));
        views.put("EFF", TailView.fromReplay(LocalAudioDecodeTestSupport.replayForcedEffectiveTrackedToneDecode(detailed)));
        views.put("HYP", TailView.fromReplay(LocalAudioDecodeTestSupport.replayForcedHypothesisToneDecode(detailed)));

        System.out.println("==== speed sweep tail mismatch probe ====");
        for (Map.Entry<String, TailView> entry : views.entrySet()) {
            printView(entry.getKey(), entry.getValue());
        }
    }

    @Test
    public void printSpeedSweepFirstCallsignWindow() {
        CwFixtureScenario scenario = findScenario("user_speed_sweep_vvv_700hz");
        List<AudioFrame> frames = new SyntheticFixtureRxAudioSource().renderFramesForTesting(scenario);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        scenario.id(),
                        frames,
                        700,
                        scenario.wpm(),
                        55,
                        false,
                        CwInterpreter.RecoveryMode.SEMANTIC_RECOVERY
                );

        LinkedHashMap<String, TailView> views = new LinkedHashMap<>();
        views.put("BASE_RAW", TailView.fromEvents(
                detailed.probeResult().decodedText(),
                detailed.toneEvents(),
                detailed.timingEvents(),
                detailed.rawDecodeEvents(),
                detailed.timingLearningDecisionTraces()
        ));
        views.put("BASE_STABLE", TailView.fromEvents(
                detailed.probeResult().decodedText(),
                detailed.toneEvents(),
                detailed.timingEvents(),
                detailed.stableAcceptedDecodeEvents(),
                detailed.timingLearningDecisionTraces()
        ));
        views.put("BASE_COMMITTED", TailView.fromDetailed(detailed));
        views.put("REDECODE_3W", TailView.fromRedecode(LocalAudioDecodeTestSupport.redecodeTrailingWords(detailed, 3)));
        views.put("TRK", TailView.fromReplay(LocalAudioDecodeTestSupport.replayForcedTrackedToneDecode(detailed)));
        views.put("EFF", TailView.fromReplay(LocalAudioDecodeTestSupport.replayForcedEffectiveTrackedToneDecode(detailed)));
        views.put("HYP", TailView.fromReplay(LocalAudioDecodeTestSupport.replayForcedHypothesisToneDecode(detailed)));

        System.out.println("==== speed sweep first callsign window ====");
        System.out.println("-- base timing adaptation --");
        printTimingAdaptationWindow(
                detailed.timingEventAdaptationTraces(),
                FIRST_CALLSIGN_START_MS,
                FIRST_CALLSIGN_END_MS
        );
        System.out.println("-- base stable decisions --");
        printStableDecisionWindow(
                detailed.stableDecisionTraces(),
                FIRST_CALLSIGN_START_MS,
                FIRST_CALLSIGN_END_MS
        );
        System.out.println("-- base timing state --");
        printTimingStateWindow(
                detailed.timingStateTraces(),
                FIRST_CALLSIGN_START_MS,
                FIRST_CALLSIGN_END_MS
        );
        for (Map.Entry<String, TailView> entry : views.entrySet()) {
            printView(entry.getKey(), entry.getValue(), FIRST_CALLSIGN_START_MS, FIRST_CALLSIGN_END_MS);
        }
    }

    private static void printView(String label, TailView view) {
        printView(label, view, START_MS, END_MS);
    }

    private static void printView(String label, TailView view, long startMs, long endMs) {
        System.out.println("---- " + label + " ----");
        System.out.println("text=" + sanitize(view.decodedText));
        if (view.timingLearningDecisionTraces != null && !view.timingLearningDecisionTraces.isEmpty()) {
            System.out.println("-- learn --");
            for (LocalAudioDecodeTestSupport.TimingLearningDecisionTrace trace : view.timingLearningDecisionTraces) {
                if (trace.timestampMs() < startMs || trace.timestampMs() > endMs) {
                    continue;
                }
                System.out.println(String.format(
                        Locale.US,
                        "L %s @%d allow=%s reason=%s trust=%s lock=%s lck=%.2f near=%.2f unl=%.2f dom=%.2f iso=%.2f hot=%.2f clip=%.2f anc=%s rawWpm=%.2f dot=%d",
                        trace.toneEventType(),
                        trace.timestampMs(),
                        trace.allowTimingLearning(),
                        trace.decision(),
                        trace.trustedTimingEstablished(),
                        trace.targetToneLocked(),
                        trace.recentLockedFrameRatio(),
                        trace.recentNearTargetLockedFrameRatio(),
                        trace.recentActiveUnlockedFrameRatio(),
                        trace.toneDominanceRatio(),
                        trace.narrowbandIsolationRatio(),
                        trace.recentHotFrameRatio(),
                        trace.recentClippingFrameRatio(),
                        trace.anchorSummary(),
                        trace.rawWpm(),
                        trace.rawDotEstimateMs()
                ));
            }
        }
        System.out.println("-- tone --");
        for (CwToneEvent event : view.toneEvents) {
            if (event.timestampMs() < startMs || event.timestampMs() > endMs) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "T %s @%d dur=%d rms=%.1f peak=%d",
                    event.type(),
                    event.timestampMs(),
                    event.toneDurationMs(),
                    event.rmsAmplitude(),
                    event.peakAmplitude()
            ));
        }
        System.out.println("-- timing --");
        for (CwTimingEvent event : view.timingEvents) {
            if (event.timestampMs() < startMs || event.timestampMs() > endMs) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "M %s/%s @%d dur=%d dot=%d intra=%d",
                    event.kind(),
                    event.classification(),
                    event.timestampMs(),
                    event.durationMs(),
                    event.dotEstimateMs(),
                    event.intraGapEstimateMs()
            ));
        }
        System.out.println("-- decode --");
        for (CwDecodeEvent event : view.decodeEvents) {
            if (event.timestampMs() < startMs || event.timestampMs() > endMs) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "D %s @%d out=%s emit=%s seq=%s unknown=%s",
                    event.type(),
                    event.timestampMs(),
                    sanitize(event.outputText()),
                    sanitize(event.emittedValue()),
                    sanitize(event.sourceSequence()),
                    event.unknownCharacter()
            ));
        }
    }

    private static void printTimingAdaptationWindow(
            List<LocalAudioDecodeTestSupport.TimingEventAdaptationTrace> traces,
            long startMs,
            long endMs
    ) {
        for (LocalAudioDecodeTestSupport.TimingEventAdaptationTrace trace : traces) {
            if (trace == null || trace.timestampMs() < startMs) {
                continue;
            }
            if (trace.timestampMs() > endMs) {
                break;
            }
            System.out.println(String.format(
                    Locale.US,
                    "A @%d %s dur=%d raw=%s(%d/%d) wpm=%s(%d/%d) anc=%s(%d/%d) trust=%s origin=%s tdot=%d",
                    trace.timestampMs(),
                    sanitize(trace.eventKind()),
                    trace.durationMs(),
                    sanitize(trace.rawClassification()),
                    trace.rawDotEstimateMs(),
                    trace.rawIntraGapEstimateMs(),
                    sanitize(trace.wpmGuardClassification()),
                    trace.wpmGuardDotEstimateMs(),
                    trace.wpmGuardIntraGapEstimateMs(),
                    sanitize(trace.anchorClassification()),
                    trace.anchorDotEstimateMs(),
                    trace.anchorIntraGapEstimateMs(),
                    trace.trustedTimingEstablished(),
                    sanitize(trace.trustOrigin()),
                    trace.trustedDotEstimateMs()
            ));
        }
    }

    private static void printStableDecisionWindow(
            List<LocalAudioDecodeTestSupport.StableDecisionTrace> traces,
            long startMs,
            long endMs
    ) {
        for (LocalAudioDecodeTestSupport.StableDecisionTrace trace : traces) {
            if (trace == null || trace.timestampMs() < startMs || trace.timestampMs() > endMs) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "S @%d emit=%s seq=%s unk=%s dec=%s ver=%s trust=%s lock=%s lck=%.2f near=%.2f unl=%.2f dom=%.2f iso=%.2f hot=%.2f clip=%.2f rawWpm=%.2f",
                    trace.timestampMs(),
                    sanitize(trace.emittedValue()),
                    sanitize(trace.sourceSequence()),
                    trace.unknownCharacter(),
                    sanitize(trace.decision()),
                    sanitize(trace.verifiedDecision()),
                    trace.trustedTimingEstablished(),
                    trace.targetToneLocked(),
                    trace.recentLockedFrameRatio(),
                    trace.recentNearTargetLockedFrameRatio(),
                    trace.recentActiveUnlockedFrameRatio(),
                    trace.toneDominanceRatio(),
                    trace.narrowbandIsolationRatio(),
                    trace.recentHotFrameRatio(),
                    trace.recentClippingFrameRatio(),
                    trace.rawWpm()
            ));
        }
    }

    private static void printTimingStateWindow(
            List<LocalAudioDecodeTestSupport.TimingStateTrace> traces,
            long startMs,
            long endMs
    ) {
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : traces) {
            if (trace == null || trace.timestampMs() < startMs || trace.timestampMs() > endMs) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "H @%d htr=%.1f hreason=%s stabWpm=%.2f stabDot=%d rawWpm=%.2f rawDot=%d summary=%s",
                    trace.timestampMs(),
                    trace.debugSnapshot() == null ? 0.0d : trace.debugSnapshot().trustedDotEstimateMs(),
                    trace.debugSnapshot() == null ? "(none)" : sanitize(trace.debugSnapshot().lastTrustedUpdateReason()),
                    trace.stabilizedSnapshot() == null ? 0.0d : trace.stabilizedSnapshot().estimatedWpmPrecise(),
                    trace.stabilizedSnapshot() == null ? 0L : trace.stabilizedSnapshot().dotEstimateMs(),
                    trace.rawSnapshot() == null ? 0.0d : trace.rawSnapshot().estimatedWpmPrecise(),
                    trace.rawSnapshot() == null ? 0L : trace.rawSnapshot().dotEstimateMs(),
                    sanitize(trace.debugSummary())
            ));
        }
    }

    private static CwFixtureScenario findScenario(String scenarioId) {
        for (CwFixtureScenario scenario : CwFixtureLibrary.scenarios()) {
            if (scenario.id().equals(scenarioId)) {
                return scenario;
            }
        }
        throw new IllegalArgumentException("Unknown scenario: " + scenarioId);
    }

    private static String sanitize(String value) {
        return value == null ? "(null)" : value.replace('\u25A1', '?');
    }

    private static final class TailView {
        private final String decodedText;
        private final List<CwToneEvent> toneEvents;
        private final List<CwTimingEvent> timingEvents;
        private final List<CwDecodeEvent> decodeEvents;
        private final List<LocalAudioDecodeTestSupport.TimingLearningDecisionTrace> timingLearningDecisionTraces;

        private TailView(
                String decodedText,
                List<CwToneEvent> toneEvents,
                List<CwTimingEvent> timingEvents,
                List<CwDecodeEvent> decodeEvents,
                List<LocalAudioDecodeTestSupport.TimingLearningDecisionTrace> timingLearningDecisionTraces
        ) {
            this.decodedText = decodedText;
            this.toneEvents = toneEvents;
            this.timingEvents = timingEvents;
            this.decodeEvents = decodeEvents;
            this.timingLearningDecisionTraces = timingLearningDecisionTraces;
        }

        private static TailView fromDetailed(LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed) {
            return new TailView(
                    detailed.probeResult().decoderSnapshot().decodedText(),
                    detailed.toneEvents(),
                    detailed.timingEvents(),
                    detailed.decodeEvents(),
                    detailed.timingLearningDecisionTraces()
            );
        }

        private static TailView fromEvents(
                String decodedText,
                List<CwToneEvent> toneEvents,
                List<CwTimingEvent> timingEvents,
                List<CwDecodeEvent> decodeEvents,
                List<LocalAudioDecodeTestSupport.TimingLearningDecisionTrace> timingLearningDecisionTraces
        ) {
            return new TailView(
                    decodedText,
                    toneEvents,
                    timingEvents,
                    decodeEvents,
                    timingLearningDecisionTraces
            );
        }

        private static TailView fromReplay(LocalAudioDecodeTestSupport.ForcedToneReplayResult replay) {
            return new TailView(
                    replay.decodedText(),
                    replay.toneEvents(),
                    replay.timingEvents(),
                    replay.decodeEvents(),
                    java.util.Collections.emptyList()
            );
        }

        private static TailView fromRedecode(LocalAudioDecodeTestSupport.TrailingWindowRedecodeResult replay) {
            return new TailView(
                    replay.decodedText(),
                    java.util.Collections.emptyList(),
                    replay.timingEvents(),
                    replay.decodeEvents(),
                    java.util.Collections.emptyList()
            );
        }
    }
}
