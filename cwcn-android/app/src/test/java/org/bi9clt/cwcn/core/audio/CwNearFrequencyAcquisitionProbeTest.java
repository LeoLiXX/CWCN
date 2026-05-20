package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.eval.CwFixtureLibrary;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.junit.Test;

import java.util.List;

public final class CwNearFrequencyAcquisitionProbeTest {
    private static final int[] PROBE_FREQUENCIES_HZ = new int[]{650, 660, 670, 680, 690, 700, 710, 720};
    private static final int[] PROBE_FRAME_INDEXES = new int[]{15, 16, 17, 27, 28, 43, 44, 45, 64, 65, 66, 67};

    @Test
    public void printNearFrequencyAcquisitionProfilesAtKeyFrames() {
        CwFixtureScenario scenario = findScenario("near_frequency_narrowband_noise_report");
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        List<AudioFrame> frames = source.renderFramesForTesting(scenario);

        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(670);

        System.out.println("==== near-frequency acquisition probe ====");
        for (int frameIndex = 0; frameIndex < frames.size(); frameIndex++) {
            AudioFrame frame = frames.get(frameIndex);
            if (isProbeFrame(frameIndex)) {
                CwSignalSnapshot before = processor.snapshot();
                System.out.println("---- BEFORE F" + frameIndex + " @" + frame.capturedAtMs() + " ----");
                System.out.println(renderSnapshotSummary(before));
                System.out.println(processor.debugAcquisitionProfile(frame, PROBE_FREQUENCIES_HZ));
            }

            List<CwToneEvent> events = processor.process(frame);

            if (isProbeFrame(frameIndex)) {
                CwSignalSnapshot after = processor.snapshot();
                System.out.println("---- AFTER  F" + frameIndex + " @" + frame.capturedAtMs() + " ----");
                System.out.println(renderSnapshotSummary(after));
                System.out.println("events=" + renderEvents(events));
                System.out.println("prefTop=" + after.preferredWindowTopCandidatesSummary());
                System.out.println("wideTop=" + after.wideScanTopCandidatesSummary());
            }
        }
    }

    private boolean isProbeFrame(int frameIndex) {
        for (int probeFrameIndex : PROBE_FRAME_INDEXES) {
            if (probeFrameIndex == frameIndex) {
                return true;
            }
        }
        return false;
    }

    private CwFixtureScenario findScenario(String scenarioId) {
        for (CwFixtureScenario scenario : CwFixtureLibrary.scenarios()) {
            if (scenario.id().equals(scenarioId)) {
                return scenario;
            }
        }
        throw new IllegalArgumentException("Unknown scenario: " + scenarioId);
    }

    private String renderSnapshotSummary(CwSignalSnapshot snapshot) {
        if (snapshot == null) {
            return "snapshot=(null)";
        }
        return "target=" + snapshot.targetToneFrequencyHz()
                + "Hz eff=" + snapshot.effectiveTrackedToneFrequencyHz()
                + "Hz lock=" + snapshot.targetToneLocked()
                + " aq=" + snapshot.acquisitionWinnerFrequencyHz()
                + "Hz/" + snapshot.acquisitionWinnerSource()
                + " score=" + String.format("%.2f", snapshot.acquisitionWinnerSelectionScore())
                + " conf=" + String.format("%.2f", snapshot.acquisitionWinnerConfidence())
                + " prev=" + snapshot.previousTargetBeforeScanFrequencyHz()
                + "Hz/" + String.format("%.2f", snapshot.previousTargetBeforeScanSelectionScore())
                + "/" + String.format("%.1f", snapshot.previousTargetBeforeScanToneRms())
                + " run=" + snapshot.acquisitionRunnerUpFrequencyHz()
                + "Hz score=" + String.format("%.2f", snapshot.acquisitionRunnerUpSelectionScore())
                + " prefScore=" + String.format("%.2f", snapshot.preferredWindowWinnerSelectionScore())
                + " prefConf=" + String.format("%.2f", snapshot.preferredWindowWinnerConfidence())
                + " wideScore=" + String.format("%.2f", snapshot.wideScanWinnerSelectionScore())
                + " wideConf=" + String.format("%.2f", snapshot.wideScanWinnerConfidence())
                + " final=" + snapshot.finalAdoptedFrequencyHz()
                + "Hz/" + snapshot.finalAdoptedSource()
                + " finalScore=" + String.format("%.2f", snapshot.finalAdoptedSelectionScore())
                + " guard=" + snapshot.lockedRetuneGuardBand()
                + ":" + snapshot.lockedRetuneGuardCandidateFrequencyHz()
                + "/" + snapshot.lockedRetuneGuardObservedScans()
                + "/" + snapshot.lockedRetuneGuardRequiredScans()
                + "/" + snapshot.lockedRetuneGuardHolding()
                + " aqDetail=" + snapshot.acquisitionDecisionDetail()
                + " finalDetail=" + snapshot.finalAdoptionDetail();
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
