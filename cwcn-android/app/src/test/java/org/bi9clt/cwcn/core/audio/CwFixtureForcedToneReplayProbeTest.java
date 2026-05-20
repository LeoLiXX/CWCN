package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.eval.CwFixtureLibrary;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.junit.Test;

import java.util.List;
import java.util.Locale;

public final class CwFixtureForcedToneReplayProbeTest {
    private static final long TONE_SWEEP_DE_WINDOW_START_MS = 6680L;
    private static final long TONE_SWEEP_DE_WINDOW_END_MS = 7320L;
    private static final long TONE_SWEEP_D_ONSET_WINDOW_START_MS = 6460L;
    private static final long TONE_SWEEP_D_ONSET_WINDOW_END_MS = 6900L;
    private static final long TONE_SWEEP_TAIL_WINDOW_START_MS = 20350L;
    private static final long TONE_SWEEP_TAIL_WINDOW_END_MS = 22100L;

    @Test
    public void printNearFrequencyForcedReplayComparison() {
        CwFixtureScenario scenario = findScenario("near_frequency_narrowband_noise_report");
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        List<AudioFrame> frames = source.renderFramesForTesting(scenario);

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        scenario.id(),
                        frames,
                        scenario.toneFrequencyHz(),
                        scenario.wpm(),
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        LocalAudioDecodeTestSupport.ForcedToneReplayResult trackedReplay =
                LocalAudioDecodeTestSupport.replayForcedTrackedToneDecode(detailed);
        LocalAudioDecodeTestSupport.ForcedToneReplayResult effectiveReplay =
                LocalAudioDecodeTestSupport.replayForcedEffectiveTrackedToneDecode(detailed);
        LocalAudioDecodeTestSupport.ForcedToneReplayResult hypothesisReplay =
                LocalAudioDecodeTestSupport.replayForcedHypothesisToneDecode(detailed);
        LocalAudioDecodeTestSupport.ForcedToneReplayResult fixed670Replay =
                LocalAudioDecodeTestSupport.replayForcedConstantToneDecode(detailed, 670);
        LocalAudioDecodeTestSupport.ForcedToneReplayResult fixed710Replay =
                LocalAudioDecodeTestSupport.replayForcedConstantToneDecode(detailed, 710);

        System.out.println("==== fixture forced replay: near_frequency_narrowband_noise_report ====");
        System.out.println("base text=" + detailed.probeResult().decodedText());
        System.out.println("base target=" + detailed.probeResult().signalSnapshot().targetToneFrequencyHz()
                + " effective=" + detailed.probeResult().signalSnapshot().effectiveTrackedToneFrequencyHz()
                + " rep=" + detailed.probeResult().signalSnapshot().representativeLockedToneFrequencyHz());
        System.out.println(trackedReplay.renderSummary());
        System.out.println(effectiveReplay.renderSummary());
        System.out.println(hypothesisReplay.renderSummary());
        System.out.println(fixed670Replay.renderSummary());
        System.out.println(fixed710Replay.renderSummary());
    }

    @Test
    public void printToneSweepForcedReplayComparison() {
        CwFixtureScenario scenario = findScenario("user_tone_sweep_vvv_18wpm");
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        List<AudioFrame> frames = source.renderFramesForTesting(scenario);

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        scenario.id(),
                        frames,
                        450,
                        scenario.wpm(),
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        LocalAudioDecodeTestSupport.ForcedToneReplayResult trackedReplay =
                LocalAudioDecodeTestSupport.replayForcedTrackedToneDecode(detailed);
        LocalAudioDecodeTestSupport.ForcedToneReplayResult effectiveReplay =
                LocalAudioDecodeTestSupport.replayForcedEffectiveTrackedToneDecode(detailed);
        LocalAudioDecodeTestSupport.ForcedToneReplayResult hypothesisReplay =
                LocalAudioDecodeTestSupport.replayForcedHypothesisToneDecode(detailed);
        LocalAudioDecodeTestSupport.ForcedToneReplayResult fixed400Replay =
                LocalAudioDecodeTestSupport.replayForcedConstantToneDecode(detailed, 400);
        LocalAudioDecodeTestSupport.ForcedToneReplayResult fixed600Replay =
                LocalAudioDecodeTestSupport.replayForcedConstantToneDecode(detailed, 600);
        LocalAudioDecodeTestSupport.ForcedToneReplayResult fixed650Replay =
                LocalAudioDecodeTestSupport.replayForcedConstantToneDecode(detailed, 650);
        LocalAudioDecodeTestSupport.ForcedToneReplayResult fixed700Replay =
                LocalAudioDecodeTestSupport.replayForcedConstantToneDecode(detailed, 700);
        LocalAudioDecodeTestSupport.ForcedToneReplayResult fixed750Replay =
                LocalAudioDecodeTestSupport.replayForcedConstantToneDecode(detailed, 750);
        LocalAudioDecodeTestSupport.ForcedToneReplayResult fixed800Replay =
                LocalAudioDecodeTestSupport.replayForcedConstantToneDecode(detailed, 800);

        System.out.println("==== fixture forced replay: user_tone_sweep_vvv_18wpm ====");
        System.out.println("expectedRaw=VVV VVV DE BI9XXX BI9CXX SK");
        System.out.println("expectedSkeleton=VVVVVVDEBI9XXXBI9CXXSK");
        System.out.println(String.format(
                Locale.US,
                "base text=%s chars=%d target=%d effective=%d representative=%d adopted=%d",
                sanitize(detailed.probeResult().decodedText()),
                detailed.probeResult().decoderSnapshot().totalCharacters(),
                detailed.probeResult().signalSnapshot().targetToneFrequencyHz(),
                detailed.probeResult().signalSnapshot().effectiveTrackedToneFrequencyHz(),
                detailed.probeResult().signalSnapshot().representativeLockedToneFrequencyHz(),
                detailed.probeResult().signalSnapshot().finalAdoptedFrequencyHz()
        ));
        printReplaySummary(trackedReplay);
        printReplaySummary(effectiveReplay);
        printReplaySummary(hypothesisReplay);
        printReplaySummary(fixed400Replay);
        printReplaySummary(fixed600Replay);
        printReplaySummary(fixed650Replay);
        printReplaySummary(fixed700Replay);
        printReplaySummary(fixed750Replay);
        printReplaySummary(fixed800Replay);

        System.out.println("base character timeline:");
        System.out.println(renderCharacterTimeline(detailed.decodeEvents()));
        System.out.println("EFF character timeline:");
        System.out.println(renderCharacterTimeline(effectiveReplay.decodeEvents()));
        System.out.println("HYP character timeline:");
        System.out.println(renderCharacterTimeline(hypothesisReplay.decodeEvents()));
        System.out.println("FIX700 character timeline:");
        System.out.println(renderCharacterTimeline(fixed700Replay.decodeEvents()));
        System.out.println("FIX800 character timeline:");
        System.out.println(renderCharacterTimeline(fixed800Replay.decodeEvents()));
        System.out.println("FIX400 character timeline:");
        System.out.println(renderCharacterTimeline(fixed400Replay.decodeEvents()));
    }

    @Test
    public void printToneSweepDeWindowBaseVsHyp() {
        CwFixtureScenario scenario = findScenario("user_tone_sweep_vvv_18wpm");
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        List<AudioFrame> frames = source.renderFramesForTesting(scenario);

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult baseDetailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        scenario.id(),
                        frames,
                        450,
                        scenario.wpm(),
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
        LocalAudioDecodeTestSupport.ForcedToneReplayResult hypothesisReplay =
                LocalAudioDecodeTestSupport.replayForcedHypothesisToneDecode(baseDetailed);

        System.out.println("==== tone-sweep DE window base vs HYP ====");
        System.out.println("expected-window=DE");
        System.out.println("base text=" + sanitize(baseDetailed.probeResult().decodedText()));
        System.out.println("hyp  text=" + sanitize(hypothesisReplay.decodedText()));
        printFrameWindow(
                "BASE",
                baseDetailed.frames(),
                baseDetailed.frameSignalTraces(),
                TONE_SWEEP_DE_WINDOW_START_MS,
                TONE_SWEEP_DE_WINDOW_END_MS
        );
        printToneEventWindow(
                "BASE",
                baseDetailed.toneEvents(),
                TONE_SWEEP_DE_WINDOW_START_MS,
                TONE_SWEEP_DE_WINDOW_END_MS
        );
        printTimingEventWindow(
                "BASE",
                baseDetailed.timingEvents(),
                TONE_SWEEP_DE_WINDOW_START_MS,
                TONE_SWEEP_DE_WINDOW_END_MS
        );
        printDecodeEventWindow(
                "BASE",
                baseDetailed.decodeEvents(),
                TONE_SWEEP_DE_WINDOW_START_MS,
                TONE_SWEEP_DE_WINDOW_END_MS
        );
        printFrameWindow(
                "HYP",
                baseDetailed.frames(),
                hypothesisReplay.frameSignalTraces(),
                TONE_SWEEP_DE_WINDOW_START_MS,
                TONE_SWEEP_DE_WINDOW_END_MS
        );
        printToneEventWindow(
                "HYP",
                hypothesisReplay.toneEvents(),
                TONE_SWEEP_DE_WINDOW_START_MS,
                TONE_SWEEP_DE_WINDOW_END_MS
        );
        printTimingEventWindow(
                "HYP",
                hypothesisReplay.timingEvents(),
                TONE_SWEEP_DE_WINDOW_START_MS,
                TONE_SWEEP_DE_WINDOW_END_MS
        );
        printDecodeEventWindow(
                "HYP",
                hypothesisReplay.decodeEvents(),
                TONE_SWEEP_DE_WINDOW_START_MS,
                TONE_SWEEP_DE_WINDOW_END_MS
        );
    }

    @Test
    public void printToneSweepDOnsetWindowBaseVsHyp() {
        CwFixtureScenario scenario = findScenario("user_tone_sweep_vvv_18wpm");
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        List<AudioFrame> frames = source.renderFramesForTesting(scenario);

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult baseDetailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        scenario.id(),
                        frames,
                        450,
                        scenario.wpm(),
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
        LocalAudioDecodeTestSupport.ForcedToneReplayResult hypothesisReplay =
                LocalAudioDecodeTestSupport.replayForcedHypothesisToneDecode(baseDetailed);

        System.out.println("==== tone-sweep D onset window base vs HYP ====");
        System.out.println("expected-window=DAH+DIT onset");
        System.out.println("base text=" + sanitize(baseDetailed.probeResult().decodedText()));
        System.out.println("hyp  text=" + sanitize(hypothesisReplay.decodedText()));
        printFrameWindow(
                "BASE",
                baseDetailed.frames(),
                baseDetailed.frameSignalTraces(),
                TONE_SWEEP_D_ONSET_WINDOW_START_MS,
                TONE_SWEEP_D_ONSET_WINDOW_END_MS
        );
        printToneEventWindow(
                "BASE",
                baseDetailed.toneEvents(),
                TONE_SWEEP_D_ONSET_WINDOW_START_MS,
                TONE_SWEEP_D_ONSET_WINDOW_END_MS
        );
        printTimingEventWindow(
                "BASE",
                baseDetailed.timingEvents(),
                TONE_SWEEP_D_ONSET_WINDOW_START_MS,
                TONE_SWEEP_D_ONSET_WINDOW_END_MS
        );
        printDecodeEventWindow(
                "BASE",
                baseDetailed.decodeEvents(),
                TONE_SWEEP_D_ONSET_WINDOW_START_MS,
                TONE_SWEEP_D_ONSET_WINDOW_END_MS
        );
        printFrameWindow(
                "HYP",
                baseDetailed.frames(),
                hypothesisReplay.frameSignalTraces(),
                TONE_SWEEP_D_ONSET_WINDOW_START_MS,
                TONE_SWEEP_D_ONSET_WINDOW_END_MS
        );
        printToneEventWindow(
                "HYP",
                hypothesisReplay.toneEvents(),
                TONE_SWEEP_D_ONSET_WINDOW_START_MS,
                TONE_SWEEP_D_ONSET_WINDOW_END_MS
        );
        printTimingEventWindow(
                "HYP",
                hypothesisReplay.timingEvents(),
                TONE_SWEEP_D_ONSET_WINDOW_START_MS,
                TONE_SWEEP_D_ONSET_WINDOW_END_MS
        );
        printDecodeEventWindow(
                "HYP",
                hypothesisReplay.decodeEvents(),
                TONE_SWEEP_D_ONSET_WINDOW_START_MS,
                TONE_SWEEP_D_ONSET_WINDOW_END_MS
        );
    }

    @Test
    public void printToneSweepTailWindowBaseVsFix400() {
        CwFixtureScenario scenario = findScenario("user_tone_sweep_vvv_18wpm");
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        List<AudioFrame> frames = source.renderFramesForTesting(scenario);

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult baseDetailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        scenario.id(),
                        frames,
                        450,
                        scenario.wpm(),
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
        LocalAudioDecodeTestSupport.ForcedToneReplayResult fixed400Replay =
                LocalAudioDecodeTestSupport.replayForcedConstantToneDecode(baseDetailed, 400);

        System.out.println("==== tone-sweep tail window base vs FIX400 ====");
        System.out.println("expected-window=...SK tail");
        System.out.println("base   text=" + sanitize(baseDetailed.probeResult().decodedText()));
        System.out.println("fix400 text=" + sanitize(fixed400Replay.decodedText()));
        printFrameWindow(
                "BASE",
                baseDetailed.frames(),
                baseDetailed.frameSignalTraces(),
                TONE_SWEEP_TAIL_WINDOW_START_MS,
                TONE_SWEEP_TAIL_WINDOW_END_MS
        );
        printToneEventWindow(
                "BASE",
                baseDetailed.toneEvents(),
                TONE_SWEEP_TAIL_WINDOW_START_MS,
                TONE_SWEEP_TAIL_WINDOW_END_MS
        );
        printTimingEventWindow(
                "BASE",
                baseDetailed.timingEvents(),
                TONE_SWEEP_TAIL_WINDOW_START_MS,
                TONE_SWEEP_TAIL_WINDOW_END_MS
        );
        printDecodeEventWindow(
                "BASE",
                baseDetailed.decodeEvents(),
                TONE_SWEEP_TAIL_WINDOW_START_MS,
                TONE_SWEEP_TAIL_WINDOW_END_MS
        );
        printFrameWindow(
                "FIX400",
                baseDetailed.frames(),
                fixed400Replay.frameSignalTraces(),
                TONE_SWEEP_TAIL_WINDOW_START_MS,
                TONE_SWEEP_TAIL_WINDOW_END_MS
        );
        printToneEventWindow(
                "FIX400",
                fixed400Replay.toneEvents(),
                TONE_SWEEP_TAIL_WINDOW_START_MS,
                TONE_SWEEP_TAIL_WINDOW_END_MS
        );
        printTimingEventWindow(
                "FIX400",
                fixed400Replay.timingEvents(),
                TONE_SWEEP_TAIL_WINDOW_START_MS,
                TONE_SWEEP_TAIL_WINDOW_END_MS
        );
        printDecodeEventWindow(
                "FIX400",
                fixed400Replay.decodeEvents(),
                TONE_SWEEP_TAIL_WINDOW_START_MS,
                TONE_SWEEP_TAIL_WINDOW_END_MS
        );
    }

    private CwFixtureScenario findScenario(String scenarioId) {
        for (CwFixtureScenario scenario : CwFixtureLibrary.scenarios()) {
            if (scenario.id().equals(scenarioId)) {
                return scenario;
            }
        }
        throw new IllegalArgumentException("Unknown scenario: " + scenarioId);
    }

    private static void printReplaySummary(LocalAudioDecodeTestSupport.ForcedToneReplayResult replay) {
        System.out.println(String.format(
                Locale.US,
                "%s chars=%d text=%s",
                replay.renderSummary(),
                replay.decoderSnapshot().totalCharacters(),
                sanitize(replay.decodedText())
        ));
    }

    private static String renderCharacterTimeline(List<CwDecodeEvent> decodeEvents) {
        StringBuilder builder = new StringBuilder();
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            if (decodeEvent == null || decodeEvent.type() == CwDecodeEvent.Type.SYMBOL_APPENDED) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(String.format(
                    Locale.US,
                    "@%5dms %-17s emit=%-3s source=%-5s output=%s",
                    decodeEvent.timestampMs(),
                    decodeEvent.type().name(),
                    sanitize(decodeEvent.emittedValue()),
                    sanitize(decodeEvent.sourceSequence()),
                    sanitize(decodeEvent.outputText())
            ));
        }
        return builder.toString();
    }

    private static void printFrameWindow(
            String label,
            List<AudioFrame> frames,
            List<LocalAudioDecodeTestSupport.FrameSignalTrace> traces,
            long startTimestampMs,
            long endTimestampMs
    ) {
        System.out.println("-- " + label + " frames --");
        for (int index = 0; index < traces.size(); index++) {
            LocalAudioDecodeTestSupport.FrameSignalTrace trace = traces.get(index);
            long timestampMs = trace.timestampMs();
            if (timestampMs < startTimestampMs || timestampMs > endTimestampMs) {
                continue;
            }
            AudioFrame frame = frames.get(index);
            CwSignalSnapshot snapshot = trace.snapshot();
            System.out.println(String.format(
                    Locale.US,
                    "@%5dms peak=%5d rms=%7.1f det=%7.1f act=%-5s lock=%-5s trk=%3d eff=%3d hyp=%3d@%.2f tone=%7.1f thr=%4d gap=%3d/%3d mem=%-5s aq=%-5s on=%-5s rescue=%-5s hold=%-5s rescueDec=%s suppressDec=%s onDec=%s",
                    timestampMs,
                    frame.peakAmplitude(),
                    frame.rmsAmplitude(),
                    trace.detectionLevel(),
                    snapshot.toneActive(),
                    snapshot.targetToneLocked(),
                    snapshot.targetToneFrequencyHz(),
                    snapshot.effectiveTrackedToneFrequencyHz(),
                    snapshot.toneHypothesisFrequencyHz(),
                    snapshot.toneHypothesisConfidence(),
                    snapshot.lastToneRmsAmplitude(),
                    trace.toneOnThreshold(),
                    trace.postReleaseGapMs(),
                    trace.postReleaseWindowMs(),
                    trace.trackedToneMemoryActiveBeforeFrame(),
                    trace.attackQualified(),
                    trace.toneOnAccepted(),
                    trace.toneOnAcceptedByRescue(),
                    trace.releaseTailHoldApplied(),
                    trace.postReleaseRescueDecision(),
                    trace.postReleaseSuppressionDecision(),
                    trace.toneOnDecision()
            ));
        }
    }

    private static void printToneEventWindow(
            String label,
            List<CwToneEvent> toneEvents,
            long startTimestampMs,
            long endTimestampMs
    ) {
        System.out.println("-- " + label + " tone-events --");
        for (CwToneEvent event : toneEvents) {
            if (event.timestampMs() < startTimestampMs || event.timestampMs() > endTimestampMs) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "@%5dms %-8s dur=%3d rms=%7.1f peak=%5d",
                    event.timestampMs(),
                    event.type(),
                    event.toneDurationMs(),
                    event.rmsAmplitude(),
                    event.peakAmplitude()
            ));
        }
    }

    private static void printTimingEventWindow(
            String label,
            List<CwTimingEvent> timingEvents,
            long startTimestampMs,
            long endTimestampMs
    ) {
        System.out.println("-- " + label + " timing-events --");
        for (CwTimingEvent event : timingEvents) {
            if (event.timestampMs() < startTimestampMs || event.timestampMs() > endTimestampMs) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "@%5dms %-4s %-18s dur=%3d dot=%3d",
                    event.timestampMs(),
                    event.kind(),
                    event.classification(),
                    event.durationMs(),
                    event.dotEstimateMs()
            ));
        }
    }

    private static void printDecodeEventWindow(
            String label,
            List<CwDecodeEvent> decodeEvents,
            long startTimestampMs,
            long endTimestampMs
    ) {
        System.out.println("-- " + label + " decode-events --");
        for (CwDecodeEvent event : decodeEvents) {
            if (event.timestampMs() < startTimestampMs || event.timestampMs() > endTimestampMs) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "@%5dms %-18s emit=%-3s seq=%-5s out=%s",
                    event.timestampMs(),
                    event.type(),
                    sanitize(event.emittedValue()),
                    sanitize(event.sourceSequence()),
                    sanitize(event.outputText())
            ));
        }
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "(null)";
        }
        return text
                .replace('\u25A1', '?')
                .replace('\n', ' ')
                .replace('\r', ' ');
    }
}
