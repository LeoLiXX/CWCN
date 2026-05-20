package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.eval.CwFixtureLibrary;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.qso.QsoStateMachine;
import org.bi9clt.cwcn.core.rx.RxCoreComponents;
import org.bi9clt.cwcn.core.rx.RxFrameSignalRunner;
import org.bi9clt.cwcn.core.rx.RxReplaySessionRunner;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class RxReplaySessionRunnerTest {
    @Test
    public void replayFramesRunsSharedReplaySkeletonOnFixtureSequence() {
        CwFixtureScenario scenario = CwFixtureLibrary.defaultScenario();
        List<AudioFrame> frames = new SyntheticFixtureRxAudioSource().renderFramesForTesting(scenario);
        RxCoreComponents rxCore = new RxCoreComponents();
        rxCore.resetRuntimeState(scenario.wpm());
        rxCore.signalProcessor().setPreferredToneFrequencyHz(scenario.toneFrequencyHz());
        RxReplaySessionRunner replaySessionRunner = new RxReplaySessionRunner(
                new RxFrameSignalRunner(null, rxCore.signalProcessor()),
                rxCore.toneTimingRunner(),
                rxCore.timingDecodeRunner()
        );
        CwInterpreter interpreter = new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);
        QsoStateMachine qsoStateMachine = new QsoStateMachine();
        ArrayList<CwTimingEvent> capturedTimingEvents = new ArrayList<>();
        ArrayList<String> toneEventKinds = new ArrayList<>();
        ArrayList<String> decodedValues = new ArrayList<>();

        RxReplaySessionRunner.Result replayResult = replaySessionRunner.replayFrames(
                frames,
                rxCore.timingModel()::process,
                (frame, frameResult) -> {
                    for (int index = 0; index < frameResult.toneEvents().size(); index++) {
                        toneEventKinds.add(frameResult.toneEvents().get(index).type().name());
                    }
                },
                (toneEvent, timingEvents) -> capturedTimingEvents.addAll(timingEvents),
                rxCore.timingModel()::flushPendingGap,
                (flushTimestampMs, timingEvents) -> capturedTimingEvents.addAll(timingEvents),
                null,
                decodeEvent -> {
                    interpreter.process(decodeEvent);
                    qsoStateMachine.process(interpreter.snapshot(), decodeEvent.timestampMs());
                    if (decodeEvent.emittedValue() != null && !decodeEvent.emittedValue().isEmpty()) {
                        decodedValues.add(decodeEvent.emittedValue());
                    }
                }
        );

        String finalText = sanitize(interpreter.snapshot().rawText());

        assertEquals(frames.size(), replayResult.processedFrameCount());
        assertTrue(replayResult.flushTimestampMs() > 0L);
        assertTrue("expected tone events", toneEventKinds.size() >= 2);
        assertTrue("expected timing events", capturedTimingEvents.size() >= 2);
        assertTrue("expected decoded values", decodedValues.size() >= 4);
        assertTrue("final text=" + finalText, finalText.contains("CQ"));
        assertTrue("final text=" + finalText, finalText.contains("BI9CLT"));
        assertTrue(
                "final text=" + finalText,
                finalText.contains(sanitize(scenario.message()))
                        || finalText.startsWith("CQCQCQDEBI9CLT")
        );
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\u25A1', '?')
                .replaceAll("\\s+", "")
                .toUpperCase(Locale.US)
                .trim();
    }
}
