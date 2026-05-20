package org.bi9clt.cwcn.core.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.bi9clt.cwcn.core.eval.CwFixtureLibrary;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario;
import org.bi9clt.cwcn.core.rx.RxReplayAnalysisResult;
import org.bi9clt.cwcn.core.rx.RxReplayAnalysisRunner;
import org.junit.Test;

import java.util.List;

public final class RxReplayAnalysisRunnerTest {
    @Test
    public void analyzeDecodesCleanFixtureThroughSharedReplayPipeline() {
        CwFixtureScenario scenario = CwFixtureLibrary.defaultScenario();
        SyntheticFixtureRxAudioSource syntheticFixtureRxAudioSource = new SyntheticFixtureRxAudioSource();
        List<AudioFrame> frames = syntheticFixtureRxAudioSource.renderFramesForTesting(scenario);

        RxReplayAnalysisResult analysisResult = new RxReplayAnalysisRunner().analyze(
                frames,
                scenario.toneFrequencyHz(),
                30,
                scenario.wpm()
        );

        assertEquals(frames.size(), analysisResult.processedFrameCount());
        assertTrue(analysisResult.toneEventCount() > 0);
        assertTrue(analysisResult.timingEventCount() > 0);
        assertTrue(analysisResult.decodeEventCount() > 0);
        assertTrue(analysisResult.turnCount() > 0);
        assertTrue(analysisResult.tailRepairCount() >= 0);
        assertTrue(analysisResult.turnWindows().size() > 0);
        assertTrue(analysisResult.transitionTraces().size() >= analysisResult.turnCount());
        assertTrue(analysisResult.signalSnapshot().effectiveTrackedToneFrequencyHz() > 0);
        assertTrue(analysisResult.decodedText().contains("CQ"));
        assertTrue(analysisResult.decodedText().contains("BI9CLT"));
    }
}
