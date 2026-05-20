package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.eval.CwFixtureLibrary;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.junit.Test;

import java.util.List;
import java.util.Locale;

public final class CwFixtureFrontEndTraceProbeTest {
    @Test
    public void traceNearFrequencyNarrowbandNoiseFrontEnd() {
        traceScenario("near_frequency_narrowband_noise_report", 670);
    }

    @Test
    public void traceDriftingNearbyInterfererFrontEnd() {
        traceScenario("drifting_nearby_interferer_directed_report", 670);
    }

    @Test
    public void traceModerateDualInterfererFrontEnd() {
        traceScenario("moderate_dual_interferer_directed_report", 670);
    }

    @Test
    public void traceToneSweepVvvFrontEndFrom450Preferred() {
        traceScenario("user_tone_sweep_vvv_18wpm", 450);
    }

    private void traceScenario(String scenarioId, int preferredToneHz) {
        CwFixtureScenario scenario = findScenario(scenarioId);
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        List<AudioFrame> frames = source.renderFramesForTesting(scenario);
        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(preferredToneHz);

        System.out.println("==== fixture front-end trace: " + scenarioId + " ====");
        for (int index = 0; index < frames.size(); index++) {
            AudioFrame frame = frames.get(index);
            CwSignalSnapshot before = processor.snapshot();
            List<CwToneEvent> events = processor.process(frame);
            CwSignalSnapshot after = processor.snapshot();
            if (index < 80 || !events.isEmpty()) {
                System.out.println(String.format(
                        Locale.US,
                        "F%03d @%d act=%s->%s lock=%s->%s target=%d->%d eff=%d AQ=%d/%s AD=%d/%s rms=%.1f tone=%.1f resid=%.1f dom=%.0f%% iso=%.0f%% thr=%d/%d floor=%d/%d on=%d off=%d last=%s events=%s",
                        index,
                        frame.capturedAtMs(),
                        before.toneActive(),
                        after.toneActive(),
                        before.targetToneLocked(),
                        after.targetToneLocked(),
                        before.targetToneFrequencyHz(),
                        after.targetToneFrequencyHz(),
                        after.effectiveTrackedToneFrequencyHz(),
                        after.acquisitionWinnerFrequencyHz(),
                        after.acquisitionWinnerSource(),
                        after.finalAdoptedFrequencyHz(),
                        after.finalAdoptedSource(),
                        after.lastRmsAmplitude(),
                        after.lastToneRmsAmplitude(),
                        after.lastWidebandResidualRmsAmplitude(),
                        after.toneDominanceRatio() * 100.0d,
                        after.narrowbandIsolationRatio() * 100.0d,
                        after.currentThreshold(),
                        after.releaseThreshold(),
                        after.noiseFloorEstimate(),
                        after.signalFloorEstimate(),
                        after.totalToneOnEvents(),
                        after.totalToneOffEvents(),
                        renderLastEvent(after),
                        renderEvents(events)
                ));
            }
        }
    }

    private CwFixtureScenario findScenario(String scenarioId) {
        for (CwFixtureScenario scenario : CwFixtureLibrary.scenarios()) {
            if (scenario.id().equals(scenarioId)) {
                return scenario;
            }
        }
        throw new IllegalArgumentException("Unknown scenario: " + scenarioId);
    }

    private String renderLastEvent(CwSignalSnapshot snapshot) {
        if (snapshot.lastEvent() == null) {
            return "-";
        }
        return snapshot.lastEvent().type()
                + "@"
                + snapshot.lastEvent().timestampMs()
                + "/"
                + snapshot.lastEvent().toneDurationMs();
    }

    private String renderEvents(List<CwToneEvent> events) {
        if (events == null || events.isEmpty()) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < events.size(); index++) {
            if (index > 0) {
                builder.append(" | ");
            }
            CwToneEvent event = events.get(index);
            builder.append(event.type())
                    .append('@')
                    .append(event.timestampMs())
                    .append('/')
                    .append(event.toneDurationMs());
        }
        return builder.toString();
    }
}
