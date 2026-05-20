package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.eval.CwFixtureLibrary;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertTrue;

public final class CwNearFrequencyLockedRetuneRegressionTest {
    @Test
    public void decisive670CandidateCanBreakStaleLockedRetuneHold() {
        CwFixtureScenario scenario = findScenario("near_frequency_narrowband_noise_report");
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        List<AudioFrame> frames = source.renderFramesForTesting(scenario);

        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(670);

        CwSignalSnapshot snapshot = null;
        for (int frameIndex = 0; frameIndex <= 65 && frameIndex < frames.size(); frameIndex++) {
            processor.process(frames.get(frameIndex));
            snapshot = processor.snapshot();
        }

        String debug = snapshot == null
                ? "snapshot=(null)"
                : "target=" + snapshot.targetToneFrequencyHz()
                + " final=" + snapshot.finalAdoptedFrequencyHz()
                + " aq=" + snapshot.acquisitionWinnerFrequencyHz()
                + " src=" + snapshot.finalAdoptedSource()
                + " aqDetail=" + snapshot.acquisitionDecisionDetail()
                + " finalDetail=" + snapshot.finalAdoptionDetail()
                + " prefTop=" + snapshot.preferredWindowTopCandidatesSummary();

        assertTrue(debug, snapshot != null);
        assertTrue(debug, Math.abs(snapshot.acquisitionWinnerFrequencyHz() - 670) <= 20);
        assertTrue(debug, Math.abs(snapshot.targetToneFrequencyHz() - 670) <= 20);
        assertTrue(debug, Math.abs(snapshot.finalAdoptedFrequencyHz() - 670) <= 20);
    }

    private CwFixtureScenario findScenario(String scenarioId) {
        for (CwFixtureScenario scenario : CwFixtureLibrary.scenarios()) {
            if (scenario.id().equals(scenarioId)) {
                return scenario;
            }
        }
        throw new IllegalArgumentException("Unknown scenario: " + scenarioId);
    }
}
