package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.eval.CwFixtureLibrary;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.junit.Test;

import java.util.List;

public final class CwHypothesisGuardSweepTraceTest {
    @Test
    public void traceToneSweepGuardApplyMoments() {
        traceScenario("user_tone_sweep_vvv_18wpm");
        traceScenario("user_qsb_cq_18wpm_600hz");
    }

    private static void traceScenario(String scenarioId) {
        System.out.println("==== " + scenarioId + " ====");
        CwFixtureScenario scenario = requireScenario(scenarioId);
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        List<AudioFrame> frames = source.renderFramesForTesting(scenario);
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(450);
        processor.setExperimentalHypothesisGuardEnabled(true);

        int previousApplyCount = 0;
        for (int frameIndex = 0; frameIndex < frames.size(); frameIndex++) {
            processor.process(frames.get(frameIndex));
            CwSignalSnapshot snapshot = processor.snapshot();
            if (snapshot.hypothesisGuardApplyCount() > previousApplyCount) {
                System.out.println(
                        "frame=" + frameIndex
                                + " applyCount=" + snapshot.hypothesisGuardApplyCount()
                                + " decision=" + snapshot.hypothesisGuardDecision()
                                + " target=" + snapshot.targetToneFrequencyHz()
                                + " hyp=" + snapshot.toneHypothesisFrequencyHz()
                                + " hypConf=" + snapshot.toneHypothesisConfidence()
                                + " hypSupport=" + snapshot.toneHypothesisSupportFrames()
                                + " rep=" + snapshot.representativeLockedToneFrequencyHz()
                                + " repFrames=" + snapshot.representativeLockedToneFrameCount()
                                + " acqWinner=" + snapshot.acquisitionWinnerFrequencyHz()
                                + " acqSource=" + snapshot.acquisitionWinnerSource()
                                + " final=" + snapshot.finalAdoptedFrequencyHz()
                                + " finalSource=" + snapshot.finalAdoptedSource()
                );
                previousApplyCount = snapshot.hypothesisGuardApplyCount();
            }
        }
    }

    private static CwFixtureScenario requireScenario(String scenarioId) {
        for (CwFixtureScenario scenario : CwFixtureLibrary.scenarios()) {
            if (scenario.id().equals(scenarioId)) {
                return scenario;
            }
        }
        throw new IllegalArgumentException("Unknown fixture scenario: " + scenarioId);
    }
}
