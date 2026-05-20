package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.eval.CwFixtureLibrary;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.junit.Test;

import java.util.List;
import java.util.Locale;

public final class CwNearFrequencyRepresentativeEvolutionProbeTest {
    @Test
    public void printNearFrequencyRepresentativeEvolution() {
        CwFixtureScenario scenario = findScenario("near_frequency_narrowband_noise_report");
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        List<AudioFrame> frames = source.renderFramesForTesting(scenario);

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        scenario.id(),
                        frames,
                        scenario.toneFrequencyHz(),
                        scenario.wpm(),
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
        LocalAudioDecodeTestSupport.FrontEndDisagreementProfile profile =
                LocalAudioDecodeTestSupport.evaluateFrontEndDisagreementProfile(detailed, 25);

        System.out.println("==== near-frequency representative evolution ====");
        System.out.println("text=" + detailed.probeResult().decodedText());
        System.out.println("profile=" + profile.renderSummary());
        System.out.println("timeline:");

        int lastTargetHz = Integer.MIN_VALUE;
        int lastEffectiveHz = Integer.MIN_VALUE;
        int lastRepresentativeHz = Integer.MIN_VALUE;
        int lastHypothesisHz = Integer.MIN_VALUE;
        int lastActiveCenterHz = Integer.MIN_VALUE;
        boolean lastLocked = false;

        for (LocalAudioDecodeTestSupport.FrameSignalTrace trace : detailed.frameSignalTraces()) {
            CwSignalSnapshot snapshot = trace.snapshot();
            int targetHz = snapshot.targetToneFrequencyHz();
            int effectiveHz = snapshot.effectiveTrackedToneFrequencyHz();
            int representativeHz = snapshot.representativeLockedToneFrequencyHz();
            int hypothesisHz = snapshot.toneHypothesisFrequencyHz();
            int activeCenterHz = snapshot.activeAcquisitionCenterFrequencyHz();
            boolean locked = snapshot.targetToneLocked();
            boolean changed = targetHz != lastTargetHz
                    || effectiveHz != lastEffectiveHz
                    || representativeHz != lastRepresentativeHz
                    || hypothesisHz != lastHypothesisHz
                    || activeCenterHz != lastActiveCenterHz
                    || locked != lastLocked;
            boolean divergent = representativeHz > 0
                    && (Math.abs(targetHz - representativeHz) >= 20
                    || Math.abs(effectiveHz - representativeHz) >= 20
                    || Math.abs(hypothesisHz - representativeHz) >= 20);
            if (!changed && !divergent) {
                continue;
            }
            System.out.println(renderTrace(trace));
            lastTargetHz = targetHz;
            lastEffectiveHz = effectiveHz;
            lastRepresentativeHz = representativeHz;
            lastHypothesisHz = hypothesisHz;
            lastActiveCenterHz = activeCenterHz;
            lastLocked = locked;
        }

        CwSignalSnapshot finalSnapshot = detailed.probeResult().signalSnapshot();
        System.out.println("final="
                + " target=" + finalSnapshot.targetToneFrequencyHz()
                + " eff=" + finalSnapshot.effectiveTrackedToneFrequencyHz()
                + " rep=" + finalSnapshot.representativeLockedToneFrequencyHz()
                + "/" + finalSnapshot.representativeLockedToneFrameCount()
                + " hyp=" + finalSnapshot.toneHypothesisFrequencyHz()
                + " act=" + finalSnapshot.activeAcquisitionCenterFrequencyHz()
                + " final=" + finalSnapshot.finalAdoptedFrequencyHz()
                + "/" + finalSnapshot.finalAdoptedSource());
    }

    private CwFixtureScenario findScenario(String scenarioId) {
        for (CwFixtureScenario scenario : CwFixtureLibrary.scenarios()) {
            if (scenario.id().equals(scenarioId)) {
                return scenario;
            }
        }
        throw new IllegalArgumentException("Unknown scenario: " + scenarioId);
    }

    private String renderTrace(LocalAudioDecodeTestSupport.FrameSignalTrace trace) {
        CwSignalSnapshot snapshot = trace.snapshot();
        return String.format(
                Locale.US,
                "%4dms lock=%s tone=%s target=%d eff=%d rep=%d/%d hyp=%d@%.0f%% act=%d conLock=%d final=%d/%s det=%.1f guard=%s adopt=%s",
                trace.timestampMs(),
                snapshot.targetToneLocked() ? "Y" : "N",
                snapshot.toneActive() ? "ON" : "OFF",
                snapshot.targetToneFrequencyHz(),
                snapshot.effectiveTrackedToneFrequencyHz(),
                snapshot.representativeLockedToneFrequencyHz(),
                snapshot.representativeLockedToneFrameCount(),
                snapshot.toneHypothesisFrequencyHz(),
                snapshot.toneHypothesisConfidence() * 100.0d,
                snapshot.activeAcquisitionCenterFrequencyHz(),
                snapshot.consecutiveLockedFrames(),
                snapshot.finalAdoptedFrequencyHz(),
                snapshot.finalAdoptedSource(),
                trace.detectionLevel(),
                snapshot.lockedRetuneGuardBand(),
                snapshot.finalAdoptionDetail()
        );
    }
}
