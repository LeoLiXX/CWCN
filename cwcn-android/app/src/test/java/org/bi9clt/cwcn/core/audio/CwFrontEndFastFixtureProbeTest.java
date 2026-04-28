package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.eval.CwFixtureLibrary;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertTrue;

public final class CwFrontEndFastFixtureProbeTest {
    @Test
    public void probeShortPulseFrontEndBehaviorOnFastNoiseFixtures() {
        probeScenario("user_noise_cq_25wpm_700hz");
        probeScenario("user_noise_cq_30wpm_700hz");
    }

    private void probeScenario(String scenarioId) {
        CwFixtureScenario scenario = findScenario(scenarioId);
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(scenario.toneFrequencyHz());

        List<AudioFrame> frames = source.renderFramesForTesting(scenario);
        ArrayList<Long> toneDurationsMs = new ArrayList<>();
        ArrayList<Long> gapDurationsMs = new ArrayList<>();
        long previousToneOffMs = -1L;

        for (AudioFrame frame : frames) {
            List<CwToneEvent> events = processor.process(frame);
            for (CwToneEvent event : events) {
                if (event.type() == CwToneEvent.Type.TONE_OFF) {
                    toneDurationsMs.add(event.toneDurationMs());
                    previousToneOffMs = event.timestampMs();
                } else if (event.type() == CwToneEvent.Type.TONE_ON && previousToneOffMs >= 0L) {
                    gapDurationsMs.add(Math.max(0L, event.timestampMs() - previousToneOffMs));
                }
            }
        }

        CwSignalSnapshot snapshot = processor.snapshot();
        long expectedDotMs = Math.max(1L, Math.round(1200.0d / scenario.wpm()));
        String summary = scenarioId
                + "\nexpectedDotMs=" + expectedDotMs
                + " toneEvents=" + toneDurationsMs.size()
                + " gapEvents=" + gapDurationsMs.size()
                + " medianToneMs=" + median(toneDurationsMs)
                + " p25ToneMs=" + percentile(toneDurationsMs, 0.25d)
                + " medianGapMs=" + median(gapDurationsMs)
                + " p25GapMs=" + percentile(gapDurationsMs, 0.25d)
                + " trackedTone=" + snapshot.targetToneFrequencyHz()
                + " lock=" + snapshot.targetToneLocked()
                + " toneOn=" + snapshot.totalToneOnEvents()
                + " toneOff=" + snapshot.totalToneOffEvents()
                + " bestLockRun=" + snapshot.maxConsecutiveLockedFrames();

        System.out.println(summary);
        assertTrue(summary, snapshot.totalToneOnEvents() > 0);
        assertTrue(summary, snapshot.totalToneOffEvents() > 0);
    }

    private CwFixtureScenario findScenario(String scenarioId) {
        for (CwFixtureScenario scenario : CwFixtureLibrary.scenarios()) {
            if (scenario.id().equals(scenarioId)) {
                return scenario;
            }
        }
        throw new IllegalArgumentException("Unknown scenario: " + scenarioId);
    }

    private long median(List<Long> values) {
        return percentile(values, 0.50d);
    }

    private long percentile(List<Long> values, double fraction) {
        if (values == null || values.isEmpty()) {
            return 0L;
        }
        ArrayList<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int index = (int) Math.max(0, Math.min(sorted.size() - 1, Math.round((sorted.size() - 1) * fraction)));
        return sorted.get(index);
    }
}
