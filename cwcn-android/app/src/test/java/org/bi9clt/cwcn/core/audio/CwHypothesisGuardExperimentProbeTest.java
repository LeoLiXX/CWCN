package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.eval.CwFixtureLibrary;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.junit.Test;

import java.util.List;

public final class CwHypothesisGuardExperimentProbeTest {
    @Test
    public void printHypothesisGuardExperimentProbe() {
        printScenario("user_qsb_cq_18wpm_600hz", true);
        printScenario("user_tone_sweep_vvv_18wpm", true);
        printScenario("usb_nearby_tone_cq_18wpm_700hz", true);
        printScenario("user_qsb_cq_18wpm_600hz", false);
    }

    private static void printScenario(String scenarioId, boolean guardEnabled) {
        CwFixtureScenario scenario = requireScenario(scenarioId);
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        List<AudioFrame> frames = source.renderFramesForTesting(scenario);
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(450);
        processor.setExperimentalHypothesisGuardEnabled(guardEnabled);

        for (AudioFrame frame : frames) {
            processor.process(frame);
        }

        CwSignalSnapshot snapshot = processor.snapshot();
        System.out.println(
                scenarioId
                        + " guard=" + guardEnabled
                        + " applyCount=" + snapshot.hypothesisGuardApplyCount()
                        + " applied=" + snapshot.hypothesisGuardApplied()
                        + " eligible=" + snapshot.hypothesisGuardEligible()
                        + " histSpan=" + snapshot.hypothesisGuardHistorySpanHz()
                        + " decision=" + snapshot.hypothesisGuardDecision()
                        + " target=" + snapshot.targetToneFrequencyHz()
                        + " hyp=" + snapshot.toneHypothesisFrequencyHz()
                        + " rep=" + snapshot.representativeLockedToneFrequencyHz()
                        + " repFrames=" + snapshot.representativeLockedToneFrameCount()
        );
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
