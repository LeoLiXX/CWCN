package org.bi9clt.cwcn.core.audio;

import static org.junit.Assert.assertEquals;

import org.bi9clt.cwcn.core.eval.CwFixtureLibrary;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.junit.Test;

import java.util.List;

public final class CwSpeedSweepLiveLikeRegressionTest {
    // Synthetic speed-sweep stress case. Useful for observability, but not a
    // primary RX mainline gate because the cadence changes are intentionally extreme.
    private static final int SQL_PERCENT = 55;

    @Test
    public void liveLikeDecodeKeepsSpeedSweepFixtureOnExpectedText() {
        CwFixtureScenario scenario = findScenario("user_speed_sweep_vvv_700hz");
        List<AudioFrame> frames = new SyntheticFixtureRxAudioSource().renderFramesForTesting(scenario);

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        scenario.id(),
                        frames,
                        scenario.toneFrequencyHz(),
                        scenario.wpm(),
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.SEMANTIC_RECOVERY
                );

        assertEquals(
                sanitize(scenario.expectedRawText()),
                sanitize(detailed.probeResult().decodedText())
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

    private static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace('\u25A1', '?').trim();
        return normalized.isEmpty() ? "(empty)" : normalized;
    }
}
