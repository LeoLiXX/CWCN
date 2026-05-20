package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.eval.CwFixtureLibrary;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class CwToneSweepTurnContinuityRegressionTest {
    private static final int PREFERRED_TONE_HZ = 450;
    private static final int SEED_WPM = 18;

    @Test
    public void toneSweepDoesNotResetTurnMidMessageWhenContinuityIsStillPresent() {
        CwFixtureScenario scenario = findScenario("user_tone_sweep_vvv_18wpm");
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        List<AudioFrame> frames = source.renderFramesForTesting(scenario);

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        scenario.id(),
                        frames,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        int startCount = 0;
        int endCount = 0;
        for (LocalAudioDecodeTestSupport.TurnTransitionTrace trace : detailed.turnTransitionTraces()) {
            if (trace == null) {
                continue;
            }
            if (trace.kind() == LocalAudioDecodeTestSupport.TurnTransitionTrace.Kind.START) {
                startCount += 1;
            } else if (trace.kind() == LocalAudioDecodeTestSupport.TurnTransitionTrace.Kind.END) {
                endCount += 1;
            }
        }

        assertEquals("tone sweep fixture should stay within one turn", 1, startCount);
        assertEquals("tone sweep fixture should not be cut into a new turn mid-message", 0, endCount);
    }

    @Test
    public void toneSweepAdoptsDominantWideScanWinnerAtDOnset() {
        CwFixtureScenario scenario = findScenario("user_tone_sweep_vvv_18wpm");
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        List<AudioFrame> frames = source.renderFramesForTesting(scenario);

        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(PREFERRED_TONE_HZ);

        CwSignalSnapshot onsetSnapshot = null;
        for (AudioFrame frame : frames) {
            processor.process(frame);
            if (frame.capturedAtMs() == 6528L) {
                onsetSnapshot = processor.snapshot();
                break;
            }
        }

        assertNotNull("expected to capture the D onset frame snapshot", onsetSnapshot);
        assertEquals("650 should win the D onset acquisition frame", 650, onsetSnapshot.acquisitionWinnerFrequencyHz());
        assertEquals("D onset should come from the wide scan", "WIDE_SCAN", onsetSnapshot.acquisitionWinnerSource());
        assertEquals("D onset winner should already qualify as a locked candidate", true, onsetSnapshot.acquisitionWinnerLocked());
        assertEquals("stale continuity hold should stand down for the dominant winner", "WIDE_SCAN", onsetSnapshot.finalAdoptedSource());
        assertEquals("the dominant D onset winner should become the live target immediately", 650, onsetSnapshot.finalAdoptedFrequencyHz());
    }

    @Test
    public void toneSweepRestoresSkTailAcrossShortLowEdgeGaps() {
        CwFixtureScenario scenario = findScenario("user_tone_sweep_vvv_18wpm");
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        List<AudioFrame> frames = source.renderFramesForTesting(scenario);

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        scenario.id(),
                        frames,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        String decodedSkeleton = squashWhitespace(detailed.probeResult().decodedText());

        // Full-sweep observability and full-text recall stay covered elsewhere. This
        // regression is intentionally narrow: it only protects the low-edge tail
        // continuity fix, which should at minimum restore the trailing SK.
        assertTrue(
                "tone sweep low-edge tail should keep the short-gap context and end with SK",
                decodedSkeleton.endsWith("SK")
        );
    }

    private static CwFixtureScenario findScenario(String scenarioId) {
        for (CwFixtureScenario scenario : CwFixtureLibrary.scenarios()) {
            if (scenario.id().equals(scenarioId)) {
                return scenario;
            }
        }
        throw new IllegalArgumentException("Unknown scenario: " + scenarioId);
    }

    private static String squashWhitespace(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", "").trim();
    }
}
