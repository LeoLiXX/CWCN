package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.eval.CwFixtureLibrary;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class CwHypothesisGuardExperimentTest {
    @Test
    public void stableQsbFixtureCanTriggerHypothesisGuardWhenExperimentEnabled() {
        CwSignalSnapshot enabledSnapshot = runScenario("user_qsb_cq_18wpm_600hz", true);
        CwSignalSnapshot disabledSnapshot = runScenario("user_qsb_cq_18wpm_600hz", false);

        assertTrue(enabledSnapshot.hypothesisGuardExperimentEnabled());
        assertTrue(enabledSnapshot.hypothesisGuardDecision().startsWith("BLOCKED:")
                || enabledSnapshot.hypothesisGuardApplied());
        assertTrue(
                enabledSnapshot.hypothesisGuardDecision(),
                !"DISABLED".equals(enabledSnapshot.hypothesisGuardDecision())
        );
        assertTrue(Math.abs(enabledSnapshot.toneHypothesisFrequencyHz() - 600) <= 20);
        assertTrue(Math.abs(enabledSnapshot.representativeLockedToneFrequencyHz() - 600) <= 20);
        assertMainlineTrackingRemainsStable(
                "stableQsb",
                disabledSnapshot,
                enabledSnapshot,
                10
        );
    }

    @Test
    public void stableQsbFixtureDoesNotTriggerHypothesisGuardWhenExperimentDisabled() {
        CwSignalSnapshot snapshot = runScenario("user_qsb_cq_18wpm_600hz", false);

        assertTrue(!snapshot.hypothesisGuardExperimentEnabled());
        assertEquals(0, snapshot.hypothesisGuardApplyCount());
        assertEquals("DISABLED", snapshot.hypothesisGuardDecision());
    }

    @Test
    public void toneSweepFixtureDoesNotRepeatedlyTriggerHypothesisGuard() {
        CwSignalSnapshot enabledSnapshot = runScenario("user_tone_sweep_vvv_18wpm", true);
        CwSignalSnapshot disabledSnapshot = runScenario("user_tone_sweep_vvv_18wpm", false);

        assertTrue(enabledSnapshot.hypothesisGuardApplyCount() <= 1);
        assertTrue(enabledSnapshot.hypothesisGuardDecision().startsWith("BLOCKED:"));
        assertMainlineTrackingRemainsStable(
                "toneSweep",
                disabledSnapshot,
                enabledSnapshot,
                10
        );
    }

    @Test
    public void nearbyToneUsbFixtureDoesNotTriggerHypothesisGuard() {
        CwSignalSnapshot enabledSnapshot = runScenario("usb_nearby_tone_cq_18wpm_700hz", true);
        CwSignalSnapshot disabledSnapshot = runScenario("usb_nearby_tone_cq_18wpm_700hz", false);

        assertEquals(0, enabledSnapshot.hypothesisGuardApplyCount());
        assertTrue(enabledSnapshot.hypothesisGuardDecision().startsWith("BLOCKED:"));
        assertMainlineTrackingRemainsStable(
                "nearbyToneUsb",
                disabledSnapshot,
                enabledSnapshot,
                10
        );
    }

    private static void assertMainlineTrackingRemainsStable(
            String label,
            CwSignalSnapshot disabledSnapshot,
            CwSignalSnapshot enabledSnapshot,
            int toleranceHz
    ) {
        String summary = label
                + "\ndisabled target=" + disabledSnapshot.targetToneFrequencyHz()
                + " final=" + disabledSnapshot.finalAdoptedFrequencyHz()
                + " rep=" + disabledSnapshot.representativeLockedToneFrequencyHz()
                + " source=" + disabledSnapshot.finalAdoptedSource()
                + "\nenabled target=" + enabledSnapshot.targetToneFrequencyHz()
                + " final=" + enabledSnapshot.finalAdoptedFrequencyHz()
                + " rep=" + enabledSnapshot.representativeLockedToneFrequencyHz()
                + " source=" + enabledSnapshot.finalAdoptedSource()
                + " decision=" + enabledSnapshot.hypothesisGuardDecision()
                + " applyCount=" + enabledSnapshot.hypothesisGuardApplyCount();

        assertTrue(
                summary,
                Math.abs(enabledSnapshot.targetToneFrequencyHz() - disabledSnapshot.targetToneFrequencyHz())
                        <= toleranceHz
        );
        assertTrue(
                summary,
                Math.abs(enabledSnapshot.finalAdoptedFrequencyHz() - disabledSnapshot.finalAdoptedFrequencyHz())
                        <= toleranceHz
        );
        assertTrue(
                summary,
                Math.abs(
                        enabledSnapshot.representativeLockedToneFrequencyHz()
                                - disabledSnapshot.representativeLockedToneFrequencyHz()
                ) <= toleranceHz
        );
        assertEquals(summary, disabledSnapshot.finalAdoptedSource(), enabledSnapshot.finalAdoptedSource());
    }

    private static CwSignalSnapshot runScenario(String scenarioId, boolean guardEnabled) {
        CwFixtureScenario scenario = requireScenario(scenarioId);
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        List<AudioFrame> frames = source.renderFramesForTesting(scenario);
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(450);
        processor.setExperimentalHypothesisGuardEnabled(guardEnabled);
        for (AudioFrame frame : frames) {
            processor.process(frame);
        }
        return processor.snapshot();
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
