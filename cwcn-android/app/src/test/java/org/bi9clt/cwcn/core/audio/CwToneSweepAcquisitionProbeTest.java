package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.eval.CwFixtureLibrary;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.junit.Test;

import java.util.List;
import java.util.Locale;

public final class CwToneSweepAcquisitionProbeTest {
    private static final int[] PROBE_FREQUENCIES_HZ = new int[]{450, 480, 520, 610, 680, 780, 800};
    private static final int[] PROBE_FRAME_INDEXES = new int[]{103, 1275, 1284, 1292, 1309, 1315, 1326, 1334, 1340};
    private static final int[] DECISION_WINDOW_FREQUENCIES_HZ = new int[]{400, 450, 600, 650, 700, 750, 800};
    private static final int[] ONSET_WINDOW_FREQUENCIES_HZ = new int[]{450, 520, 580, 600, 620, 650, 680, 700};
    private static final int[] TAIL_S_WINDOW_FREQUENCIES_HZ = new int[]{400, 410, 450, 600, 650, 700, 750, 800};
    private static final long D_ONSET_WINDOW_START_MS = 6460L;
    private static final long D_ONSET_WINDOW_END_MS = 6900L;
    private static final long DE_WINDOW_START_MS = 7000L;
    private static final long DE_WINDOW_END_MS = 8400L;
    private static final long TAIL_S_WINDOW_START_MS = 20480L;
    private static final long TAIL_S_WINDOW_END_MS = 20720L;
    private static final long SK_WINDOW_START_MS = 20850L;
    private static final long SK_WINDOW_END_MS = 22100L;

    @Test
    public void printToneSweepAcquisitionProfilesAtFailureFrames() {
        CwFixtureScenario scenario = findScenario("user_tone_sweep_vvv_18wpm");
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        List<AudioFrame> frames = source.renderFramesForTesting(scenario);

        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(450);

        System.out.println("==== tone-sweep acquisition probe ====");
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

    @Test
    public void printToneSweepDecisionWindowsForDeAndSk() {
        CwFixtureScenario scenario = findScenario("user_tone_sweep_vvv_18wpm");
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        List<AudioFrame> frames = source.renderFramesForTesting(scenario);

        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(450);

        System.out.println("==== tone-sweep decision window probe ====");
        for (int frameIndex = 0; frameIndex < frames.size(); frameIndex++) {
            AudioFrame frame = frames.get(frameIndex);
            long timestampMs = frame.capturedAtMs();
            boolean deWindow = timestampMs >= DE_WINDOW_START_MS && timestampMs <= DE_WINDOW_END_MS;
            boolean skWindow = timestampMs >= SK_WINDOW_START_MS && timestampMs <= SK_WINDOW_END_MS;
            if (!deWindow && !skWindow) {
                processor.process(frame);
                continue;
            }

            CwSignalSnapshot before = processor.snapshot();
            String acquisitionProfile = processor.debugAcquisitionProfile(frame, DECISION_WINDOW_FREQUENCIES_HZ);
            List<CwToneEvent> events = processor.process(frame);
            CwSignalSnapshot after = processor.snapshot();
            boolean interesting = !events.isEmpty()
                    || before.targetToneFrequencyHz() != after.targetToneFrequencyHz()
                    || before.effectiveTrackedToneFrequencyHz() != after.effectiveTrackedToneFrequencyHz()
                    || before.acquisitionWinnerFrequencyHz() != after.acquisitionWinnerFrequencyHz()
                    || before.finalAdoptedFrequencyHz() != after.finalAdoptedFrequencyHz()
                    || before.toneHypothesisFrequencyHz() != after.toneHypothesisFrequencyHz()
                    || before.toneHypothesisSupportFrames() != after.toneHypothesisSupportFrames();
            if (!interesting) {
                continue;
            }

            System.out.println("---- " + (deWindow ? "DE" : "SK") + " F" + frameIndex + " @" + timestampMs + "ms ----");
            System.out.println("before=" + renderDecisionSnapshot(before));
            System.out.println(acquisitionProfile);
            System.out.println("after =" + renderDecisionSnapshot(after));
            System.out.println("events=" + renderEvents(events));
            System.out.println("prefTop=" + after.preferredWindowTopCandidatesSummary());
            System.out.println("wideTop=" + after.wideScanTopCandidatesSummary());
        }
    }

    @Test
    public void printToneSweepOnsetDecisionWindowForD() {
        CwFixtureScenario scenario = findScenario("user_tone_sweep_vvv_18wpm");
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        List<AudioFrame> frames = source.renderFramesForTesting(scenario);

        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(450);

        System.out.println("==== tone-sweep D onset acquisition window ====");
        for (int frameIndex = 0; frameIndex < frames.size(); frameIndex++) {
            AudioFrame frame = frames.get(frameIndex);
            long timestampMs = frame.capturedAtMs();
            if (timestampMs < D_ONSET_WINDOW_START_MS || timestampMs > D_ONSET_WINDOW_END_MS) {
                processor.process(frame);
                continue;
            }

            CwSignalSnapshot before = processor.snapshot();
            String acquisitionProfile = processor.debugAcquisitionProfile(frame, ONSET_WINDOW_FREQUENCIES_HZ);
            List<CwToneEvent> events = processor.process(frame);
            CwSignalSnapshot after = processor.snapshot();
            boolean interesting = !events.isEmpty()
                    || before.targetToneFrequencyHz() != after.targetToneFrequencyHz()
                    || before.effectiveTrackedToneFrequencyHz() != after.effectiveTrackedToneFrequencyHz()
                    || before.acquisitionWinnerFrequencyHz() != after.acquisitionWinnerFrequencyHz()
                    || before.finalAdoptedFrequencyHz() != after.finalAdoptedFrequencyHz()
                    || before.toneHypothesisFrequencyHz() != after.toneHypothesisFrequencyHz()
                    || before.toneHypothesisSupportFrames() != after.toneHypothesisSupportFrames()
                    || before.targetToneLocked() != after.targetToneLocked()
                    || before.toneActive() != after.toneActive();
            if (!interesting) {
                continue;
            }

            System.out.println("---- D-ONSET F" + frameIndex + " @" + timestampMs + "ms ----");
            System.out.println("before=" + renderDecisionSnapshot(before));
            System.out.println(acquisitionProfile);
            System.out.println("after =" + renderDecisionSnapshot(after));
            System.out.println("events=" + renderEvents(events));
            System.out.println("prefTop=" + after.preferredWindowTopCandidatesSummary());
            System.out.println("wideTop=" + after.wideScanTopCandidatesSummary());
        }
    }

    @Test
    public void printToneSweepDecisionWindowForTailS() {
        CwFixtureScenario scenario = findScenario("user_tone_sweep_vvv_18wpm");
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        List<AudioFrame> frames = source.renderFramesForTesting(scenario);

        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(450);

        System.out.println("==== tone-sweep tail S acquisition window ====");
        for (int frameIndex = 0; frameIndex < frames.size(); frameIndex++) {
            AudioFrame frame = frames.get(frameIndex);
            long timestampMs = frame.capturedAtMs();
            if (timestampMs < TAIL_S_WINDOW_START_MS || timestampMs > TAIL_S_WINDOW_END_MS) {
                processor.process(frame);
                continue;
            }

            CwSignalSnapshot before = processor.snapshot();
            String acquisitionProfile = processor.debugAcquisitionProfile(frame, TAIL_S_WINDOW_FREQUENCIES_HZ);
            List<CwToneEvent> events = processor.process(frame);
            CwSignalSnapshot after = processor.snapshot();
            boolean interesting = !events.isEmpty()
                    || before.targetToneFrequencyHz() != after.targetToneFrequencyHz()
                    || before.effectiveTrackedToneFrequencyHz() != after.effectiveTrackedToneFrequencyHz()
                    || before.acquisitionWinnerFrequencyHz() != after.acquisitionWinnerFrequencyHz()
                    || before.finalAdoptedFrequencyHz() != after.finalAdoptedFrequencyHz()
                    || before.toneHypothesisFrequencyHz() != after.toneHypothesisFrequencyHz()
                    || before.toneHypothesisSupportFrames() != after.toneHypothesisSupportFrames()
                    || before.targetToneLocked() != after.targetToneLocked()
                    || before.toneActive() != after.toneActive();
            if (!interesting) {
                continue;
            }

            System.out.println("---- TAIL-S F" + frameIndex + " @" + timestampMs + "ms ----");
            System.out.println("before=" + renderDecisionSnapshot(before));
            System.out.println(acquisitionProfile);
            System.out.println("after =" + renderDecisionSnapshot(after));
            System.out.println("events=" + renderEvents(events));
            System.out.println("prefTop=" + after.preferredWindowTopCandidatesSummary());
            System.out.println("wideTop=" + after.wideScanTopCandidatesSummary());
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

    private String renderDecisionSnapshot(CwSignalSnapshot snapshot) {
        if (snapshot == null) {
            return "snapshot=(null)";
        }
        return "act=" + snapshot.toneActive()
                + " lock=" + snapshot.targetToneLocked()
                + " target=" + snapshot.targetToneFrequencyHz()
                + " eff=" + snapshot.effectiveTrackedToneFrequencyHz()
                + " hyp=" + snapshot.toneHypothesisFrequencyHz()
                + "@" + String.format(Locale.US, "%.2f", snapshot.toneHypothesisConfidence())
                + "/" + snapshot.toneHypothesisSupportFrames()
                + " rep=" + snapshot.representativeLockedToneFrequencyHz()
                + " aq=" + snapshot.acquisitionWinnerFrequencyHz()
                + "/" + snapshot.acquisitionWinnerSource()
                + " ad=" + snapshot.finalAdoptedFrequencyHz()
                + "/" + snapshot.finalAdoptedSource()
                + " tone=" + String.format(Locale.US, "%.1f", snapshot.lastToneRmsAmplitude())
                + " dom=" + String.format(Locale.US, "%.2f", snapshot.toneDominanceRatio())
                + " iso=" + String.format(Locale.US, "%.2f", snapshot.narrowbandIsolationRatio())
                + " thr=" + snapshot.currentThreshold() + "/" + snapshot.releaseThreshold()
                + " aqDetail=" + snapshot.acquisitionDecisionDetail()
                + " adDetail=" + snapshot.finalAdoptionDetail();
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
