package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.eval.CwFixtureLibrary;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.rx.RxCommittedOutputController;
import org.bi9clt.cwcn.core.rx.RxTurnSessionFinalizer;
import org.bi9clt.cwcn.core.rx.RxTurnTailRepairController;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public final class CwOperateTailRepairReplayTest {
    private static final int FRAME_SIZE_SAMPLES = 256;
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SQL_PERCENT = 55;
    private static final int SEED_WPM = 15;
    private static final String EXPECTED_CAPTURE_TURN_TEXT =
            "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 700 24WPM PSE K.";

    @Test
    public void operateLikeTailRepairReplay_keepsCaptureTraceOnExpectedText() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.findTraceDir().resolve("capture.wav");
        if (!Files.isRegularFile(wavFile)) {
            throw new IllegalStateException("Missing captured trace wav: " + wavFile);
        }
        LocalAudioDecodeTestSupport.WaveDataProbe waveData =
                LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);
        List<AudioFrame> frames = normalizeFramesToZero(buildFrames(waveData.samples(), waveData.sampleRateHz()));

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult baseDetailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeWithoutFinalTailRepair(
                        "Trace capture.wav",
                        frames,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        OperateLikeReplayResult result = replayThroughOperateTurnTailRepair(baseDetailed);
        String expectedText = EXPECTED_CAPTURE_TURN_TEXT
                + EXPECTED_CAPTURE_TURN_TEXT
                + EXPECTED_CAPTURE_TURN_TEXT;

        assertEquals(renderSummary("capture", expectedText, result), expectedText, sanitize(result.repairedText));
    }

    @Test
    public void operateLikeTailRepairReplay_keepsSpeedSweepFixtureOnExpectedText() {
        CwFixtureScenario scenario = requireScenario("user_speed_sweep_vvv_700hz");
        List<AudioFrame> frames = new SyntheticFixtureRxAudioSource().renderFramesForTesting(scenario);

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult baseDetailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeWithoutFinalTailRepair(
                        scenario.id(),
                        frames,
                        scenario.toneFrequencyHz(),
                        scenario.wpm(),
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.SEMANTIC_RECOVERY
                );

        OperateLikeReplayResult result = replayThroughOperateTurnTailRepair(baseDetailed);
        assertEquals(
                renderSummary("speed-sweep", scenario.expectedRawText(), result),
                sanitize(scenario.expectedRawText()),
                sanitize(result.repairedText)
        );
    }

    private static OperateLikeReplayResult replayThroughOperateTurnTailRepair(
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed
    ) {
        RxCommittedOutputController committedOutputController = new RxCommittedOutputController(
                new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS),
                null,
                null,
                null,
                null
        );
        RxTurnSessionFinalizer finalizer = new RxTurnSessionFinalizer(
                new RxTurnTailRepairController(),
                committedOutputController
        );
        List<TurnWindow> turnWindows = buildTurnWindows(detailed);
        int repairCount = 0;

        if (turnWindows.isEmpty()) {
            finalizer.beginNewTurn();
            noteDecodeEvents(finalizer, detailed.decodeEvents(), Long.MIN_VALUE, detailed.flushTimestampMs());
            noteToneEvents(finalizer, detailed.toneEvents(), Long.MIN_VALUE, detailed.flushTimestampMs());
            if (finalizer.finalizeCurrentTurn(detailed.flushTimestampMs()) != null) {
                repairCount += 1;
            }
            finalizer.endTurn();
        } else {
            for (TurnWindow turnWindow : turnWindows) {
                finalizer.beginNewTurn();
                noteDecodeEvents(
                        finalizer,
                        detailed.decodeEvents(),
                        turnWindow.turnStartTimestampMs,
                        turnWindow.turnEndTimestampMs
                );
                noteToneEvents(
                        finalizer,
                        detailed.toneEvents(),
                        turnWindow.turnStartTimestampMs,
                        turnWindow.turnEndTimestampMs
                );
                if (finalizer.finalizeCurrentTurn(turnWindow.turnEndTimestampMs) != null) {
                    repairCount += 1;
                }
                finalizer.endTurn();
            }
        }

        return new OperateLikeReplayResult(
                sanitize(detailed.probeResult().decodedText()),
                sanitize(committedOutputController.rawSnapshot() == null
                        ? ""
                        : committedOutputController.rawSnapshot().rawText()),
                repairCount,
                turnWindows.size()
        );
    }

    private static void noteDecodeEvents(
            RxTurnSessionFinalizer finalizer,
            List<CwDecodeEvent> decodeEvents,
            long startTimestampMs,
            long endTimestampMs
    ) {
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            if (decodeEvent == null
                    || decodeEvent.timestampMs() < startTimestampMs
                    || decodeEvent.timestampMs() > endTimestampMs) {
                continue;
            }
            finalizer.processCommittedDecodeEvent(decodeEvent);
        }
    }

    private static void noteToneEvents(
            RxTurnSessionFinalizer finalizer,
            List<CwToneEvent> toneEvents,
            long startTimestampMs,
            long endTimestampMs
    ) {
        for (CwToneEvent toneEvent : toneEvents) {
            if (toneEvent == null
                    || toneEvent.timestampMs() < startTimestampMs
                    || toneEvent.timestampMs() > endTimestampMs) {
                continue;
            }
            finalizer.noteToneEvent(toneEvent);
        }
    }

    private static List<TurnWindow> buildTurnWindows(
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed
    ) {
        ArrayList<TurnWindow> turnWindows = new ArrayList<>();
        for (LocalAudioDecodeTestSupport.TurnTransitionTrace transitionTrace : detailed.turnTransitionTraces()) {
            if (transitionTrace == null
                    || transitionTrace.kind() != LocalAudioDecodeTestSupport.TurnTransitionTrace.Kind.START) {
                continue;
            }
            LocalAudioDecodeTestSupport.TurnTransitionTrace endTrace = findTurnEndTrace(
                    detailed.turnTransitionTraces(),
                    transitionTrace.turnIndex(),
                    transitionTrace.timestampMs()
            );
            turnWindows.add(new TurnWindow(
                    transitionTrace.turnIndex(),
                    transitionTrace.timestampMs(),
                    endTrace == null ? detailed.flushTimestampMs() : endTrace.timestampMs()
            ));
        }
        return turnWindows;
    }

    private static LocalAudioDecodeTestSupport.TurnTransitionTrace findTurnEndTrace(
            List<LocalAudioDecodeTestSupport.TurnTransitionTrace> traces,
            int turnIndex,
            long turnStartTimestampMs
    ) {
        for (LocalAudioDecodeTestSupport.TurnTransitionTrace trace : traces) {
            if (trace == null
                    || trace.kind() != LocalAudioDecodeTestSupport.TurnTransitionTrace.Kind.END
                    || trace.turnIndex() != turnIndex
                    || trace.timestampMs() < turnStartTimestampMs) {
                continue;
            }
            return trace;
        }
        return null;
    }

    private static List<AudioFrame> buildFrames(short[] samples, int sampleRateHz) {
        ArrayList<AudioFrame> frames = new ArrayList<>();
        long sampleOffset = 0L;
        for (int offset = 0; offset < samples.length; offset += FRAME_SIZE_SAMPLES) {
            int frameLength = Math.min(FRAME_SIZE_SAMPLES, samples.length - offset);
            short[] frameSamples = new short[frameLength];
            System.arraycopy(samples, offset, frameSamples, 0, frameLength);
            frames.add(LocalAudioDecodeTestSupport.buildFrameForProbe(frameSamples, sampleRateHz, sampleOffset));
            sampleOffset += frameLength;
        }
        return frames;
    }

    private static List<AudioFrame> normalizeFramesToZero(List<AudioFrame> frames) {
        ArrayList<AudioFrame> normalized = new ArrayList<>(frames.size());
        if (frames.isEmpty()) {
            return normalized;
        }
        long firstTimestampMs = frames.get(0).capturedAtMs();
        for (AudioFrame frame : frames) {
            normalized.add(new AudioFrame(
                    frame.samples(),
                    frame.sampleRateHz(),
                    frame.channelCount(),
                    frame.peakAmplitude(),
                    frame.rmsAmplitude(),
                    frame.clippedSampleCount(),
                    Math.max(0L, frame.capturedAtMs() - firstTimestampMs)
            ));
        }
        return normalized;
    }

    private static CwFixtureScenario requireScenario(String scenarioId) {
        for (CwFixtureScenario scenario : CwFixtureLibrary.scenarios()) {
            if (scenario.id().equals(scenarioId)) {
                return scenario;
            }
        }
        throw new IllegalArgumentException("Unknown scenario: " + scenarioId);
    }

    private static String renderSummary(
            String label,
            String expectedText,
            OperateLikeReplayResult result
    ) {
        return String.format(
                Locale.US,
                "%s%nexpected=%s%nbase=%s%nrepaired=%s%nrepairs=%d turns=%d",
                label,
                sanitize(expectedText),
                sanitize(result.baseText),
                sanitize(result.repairedText),
                result.repairCount,
                result.turnCount
        );
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace('\u25A1', '?').trim();
        return normalized.isEmpty() ? "(empty)" : normalized;
    }

    private static final class TurnWindow {
        private final int turnIndex;
        private final long turnStartTimestampMs;
        private final long turnEndTimestampMs;

        private TurnWindow(int turnIndex, long turnStartTimestampMs, long turnEndTimestampMs) {
            this.turnIndex = turnIndex;
            this.turnStartTimestampMs = turnStartTimestampMs;
            this.turnEndTimestampMs = turnEndTimestampMs;
        }
    }

    private static final class OperateLikeReplayResult {
        private final String baseText;
        private final String repairedText;
        private final int repairCount;
        private final int turnCount;

        private OperateLikeReplayResult(
                String baseText,
                String repairedText,
                int repairCount,
                int turnCount
        ) {
            this.baseText = baseText;
            this.repairedText = repairedText;
            this.repairCount = repairCount;
            this.turnCount = turnCount;
        }
    }
}
