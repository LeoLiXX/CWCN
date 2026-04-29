package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.eval.CwFixtureLibrary;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class CwToneTruthEvaluationSupport {
    private static final double DEFAULT_MIN_ACTIVE_SAMPLE_RATIO = 0.20d;
    private static final int DEFAULT_HIT_TOLERANCE_HZ = 30;
    private static final int DEFAULT_LARGE_ERROR_THRESHOLD_HZ = 60;
    private static final int DEFAULT_DISAGREEMENT_THRESHOLD_HZ = 40;
    private static final double STABLE_TONE_TRUTH_SPAN_THRESHOLD_HZ = 35.0d;
    private static final double VARIABLE_TONE_TRUTH_SPAN_THRESHOLD_HZ = 80.0d;

    private CwToneTruthEvaluationSupport() {
    }

    static ToneTruthComparison evaluateFixture(String scenarioId, int preferredToneHz) {
        return evaluateFixture(
                requireScenario(scenarioId),
                preferredToneHz,
                DEFAULT_MIN_ACTIVE_SAMPLE_RATIO,
                DEFAULT_HIT_TOLERANCE_HZ,
                DEFAULT_LARGE_ERROR_THRESHOLD_HZ,
                DEFAULT_DISAGREEMENT_THRESHOLD_HZ
        );
    }

    static ToneTruthComparison evaluateFixture(CwFixtureScenario scenario, int preferredToneHz) {
        return evaluateFixture(
                scenario,
                preferredToneHz,
                DEFAULT_MIN_ACTIVE_SAMPLE_RATIO,
                DEFAULT_HIT_TOLERANCE_HZ,
                DEFAULT_LARGE_ERROR_THRESHOLD_HZ,
                DEFAULT_DISAGREEMENT_THRESHOLD_HZ
        );
    }

    static ToneTruthComparison evaluateFixture(
            CwFixtureScenario scenario,
            int preferredToneHz,
            double minActiveSampleRatio,
            int hitToleranceHz,
            int largeErrorThresholdHz,
            int disagreementThresholdHz
    ) {
        SyntheticFixtureRxAudioSource audioSource = new SyntheticFixtureRxAudioSource();
        SyntheticFixtureRxAudioSource.RenderedFixtureFrames rendered =
                audioSource.renderFramesWithTruthForTesting(scenario);
        List<AudioFrame> frames = rendered.frames();
        List<SyntheticFixtureRxAudioSource.FrameToneTruth> truths = rendered.toneTruths();
        if (frames.size() != truths.size()) {
            throw new IllegalStateException(String.format(
                    Locale.US,
                    "Frame/truth size mismatch for %s: frames=%d truths=%d",
                    scenario.id(),
                    frames.size(),
                    truths.size()
            ));
        }

        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(preferredToneHz);
        MetricAccumulator tracked = new MetricAccumulator("tracked");
        MetricAccumulator hypothesis = new MetricAccumulator("hypothesis");
        ArrayList<DisagreementFrame> disagreements = new ArrayList<>();
        int activeTruthFrameCount = 0;
        int coObservedFrameCount = 0;
        int trackedCloserFrameCount = 0;
        int hypothesisCloserFrameCount = 0;
        int tieCloserFrameCount = 0;
        int disagreementObservedFrameCount = 0;
        int disagreementTrackedCloserFrameCount = 0;
        int disagreementHypothesisCloserFrameCount = 0;
        int disagreementTieCloserFrameCount = 0;
        int trackedRescueFrameCount = 0;
        int hypothesisRescueFrameCount = 0;
        double minTruthToneHz = Double.POSITIVE_INFINITY;
        double maxTruthToneHz = Double.NEGATIVE_INFINITY;

        for (int index = 0; index < frames.size(); index++) {
            processor.process(frames.get(index));
            CwSignalSnapshot snapshot = processor.snapshot();
            SyntheticFixtureRxAudioSource.FrameToneTruth truth = truths.get(index);
            if (!truth.toneActive() || truth.activeSampleRatio() < minActiveSampleRatio) {
                continue;
            }

            activeTruthFrameCount += 1;
            double truthToneHz = truth.expectedToneFrequencyHz();
            minTruthToneHz = Math.min(minTruthToneHz, truthToneHz);
            maxTruthToneHz = Math.max(maxTruthToneHz, truthToneHz);
            int trackedToneHz = snapshot.targetToneFrequencyHz();
            double trackedErrorHz = Math.abs(trackedToneHz - truthToneHz);
            tracked.record(trackedToneHz, truthToneHz, trackedErrorHz, hitToleranceHz, largeErrorThresholdHz);

            boolean hypothesisAvailable = snapshot.toneHypothesisSupportFrames() > 0
                    && !"NONE".equals(snapshot.toneHypothesisSource());
            if (!hypothesisAvailable) {
                continue;
            }

            int hypothesisToneHz = snapshot.toneHypothesisFrequencyHz();
            double hypothesisErrorHz = Math.abs(hypothesisToneHz - truthToneHz);
            hypothesis.record(
                    hypothesisToneHz,
                    truthToneHz,
                    hypothesisErrorHz,
                    hitToleranceHz,
                    largeErrorThresholdHz
            );

            coObservedFrameCount += 1;
            if (trackedErrorHz + 0.0001d < hypothesisErrorHz) {
                trackedCloserFrameCount += 1;
            } else if (hypothesisErrorHz + 0.0001d < trackedErrorHz) {
                hypothesisCloserFrameCount += 1;
            } else {
                tieCloserFrameCount += 1;
            }
            if (trackedErrorHz >= largeErrorThresholdHz && hypothesisErrorHz <= hitToleranceHz) {
                hypothesisRescueFrameCount += 1;
            }
            if (hypothesisErrorHz >= largeErrorThresholdHz && trackedErrorHz <= hitToleranceHz) {
                trackedRescueFrameCount += 1;
            }

            if (Math.abs(trackedToneHz - hypothesisToneHz) >= disagreementThresholdHz) {
                disagreementObservedFrameCount += 1;
                if (trackedErrorHz + 0.0001d < hypothesisErrorHz) {
                    disagreementTrackedCloserFrameCount += 1;
                } else if (hypothesisErrorHz + 0.0001d < trackedErrorHz) {
                    disagreementHypothesisCloserFrameCount += 1;
                } else {
                    disagreementTieCloserFrameCount += 1;
                }
                disagreements.add(new DisagreementFrame(
                        truth.frameIndex(),
                        truth.frameStartTimestampMs(),
                        truthToneHz,
                        trackedToneHz,
                        trackedErrorHz,
                        hypothesisToneHz,
                        hypothesisErrorHz,
                        snapshot.toneHypothesisConfidence(),
                        snapshot.toneHypothesisSource()
                ));
            }
        }

        ToneMetrics trackedMetrics = tracked.finish(activeTruthFrameCount);
        ToneMetrics hypothesisMetrics = hypothesis.finish(activeTruthFrameCount);
        double truthToneSpanHz = activeTruthFrameCount <= 0 ? 0.0d : (maxTruthToneHz - minTruthToneHz);
        GroundTruthToneMode groundTruthToneMode = classifyGroundTruthToneMode(truthToneSpanHz);
        Winner winner = decideWinner(
                activeTruthFrameCount,
                disagreementObservedFrameCount,
                trackedMetrics,
                hypothesisMetrics,
                trackedCloserFrameCount,
                hypothesisCloserFrameCount,
                disagreementTrackedCloserFrameCount,
                disagreementHypothesisCloserFrameCount
        );
        CwSignalSnapshot finalSnapshot = processor.snapshot();
        PrototypeRecommendation prototypeRecommendation = decidePrototypeRecommendation(
                groundTruthToneMode,
                disagreementObservedFrameCount,
                disagreementTrackedCloserFrameCount,
                disagreementHypothesisCloserFrameCount
        );
        return new ToneTruthComparison(
                scenario.id(),
                preferredToneHz,
                activeTruthFrameCount,
                coObservedFrameCount,
                trackedCloserFrameCount,
                hypothesisCloserFrameCount,
                tieCloserFrameCount,
                disagreementObservedFrameCount,
                disagreementTrackedCloserFrameCount,
                disagreementHypothesisCloserFrameCount,
                disagreementTieCloserFrameCount,
                trackedRescueFrameCount,
                hypothesisRescueFrameCount,
                truthToneSpanHz,
                groundTruthToneMode,
                disagreements,
                trackedMetrics,
                hypothesisMetrics,
                winner,
                prototypeRecommendation,
                finalSnapshot.activeWindowObservationCount(),
                finalSnapshot.activeAcquisitionCenterFrequencyHz(),
                finalSnapshot.activeHypothesisObservationCount(),
                finalSnapshot.activeHypothesisCenterFrequencyHz(),
                finalSnapshot.representativeCompetitionObservationCount(),
                finalSnapshot.representativeCompetitionTrackedWinFrames(),
                finalSnapshot.representativeCompetitionHypothesisWinFrames(),
                finalSnapshot.representativeCompetitionHypothesisMaxWinStreak(),
                finalSnapshot.activeCenterCompetitionObservationCount(),
                finalSnapshot.activeCenterCompetitionTrackedWinFrames(),
                finalSnapshot.activeCenterCompetitionHypothesisWinFrames(),
                finalSnapshot.activeCenterCompetitionHypothesisMaxWinStreak()
        );
    }

    private static GroundTruthToneMode classifyGroundTruthToneMode(double truthToneSpanHz) {
        if (truthToneSpanHz <= STABLE_TONE_TRUTH_SPAN_THRESHOLD_HZ) {
            return GroundTruthToneMode.STABLE_SINGLE_PEAK;
        }
        if (truthToneSpanHz >= VARIABLE_TONE_TRUTH_SPAN_THRESHOLD_HZ) {
            return GroundTruthToneMode.VARIABLE_OR_SWEEP;
        }
        return GroundTruthToneMode.TRANSITIONAL;
    }

    private static Winner decideWinner(
            int activeTruthFrameCount,
            int disagreementObservedFrameCount,
            ToneMetrics tracked,
            ToneMetrics hypothesis,
            int trackedCloserFrameCount,
            int hypothesisCloserFrameCount,
            int disagreementTrackedCloserFrameCount,
            int disagreementHypothesisCloserFrameCount
    ) {
        if (activeTruthFrameCount <= 0) {
            return Winner.INSUFFICIENT;
        }
        if (hypothesis.availableFrameCount() == 0) {
            return Winner.TRACKED;
        }
        if (tracked.availableFrameCount() == 0) {
            return Winner.HYPOTHESIS;
        }
        if (disagreementObservedFrameCount >= 3) {
            if (disagreementTrackedCloserFrameCount >= disagreementHypothesisCloserFrameCount + 2) {
                return Winner.TRACKED;
            }
            if (disagreementHypothesisCloserFrameCount >= disagreementTrackedCloserFrameCount + 2) {
                return Winner.HYPOTHESIS;
            }
        }
        if (trackedCloserFrameCount >= hypothesisCloserFrameCount + 3
                && tracked.meanAbsoluteErrorHz() <= hypothesis.meanAbsoluteErrorHz() + 8.0d) {
            return Winner.TRACKED;
        }
        if (hypothesisCloserFrameCount >= trackedCloserFrameCount + 3
                && hypothesis.meanAbsoluteErrorHz() <= tracked.meanAbsoluteErrorHz() + 8.0d) {
            return Winner.HYPOTHESIS;
        }
        if (tracked.hitRate() >= hypothesis.hitRate() + 0.08d
                && tracked.meanAbsoluteErrorHz() <= hypothesis.meanAbsoluteErrorHz() + 10.0d) {
            return Winner.TRACKED;
        }
        if (hypothesis.hitRate() >= tracked.hitRate() + 0.08d
                && hypothesis.meanAbsoluteErrorHz() <= tracked.meanAbsoluteErrorHz() + 10.0d) {
            return Winner.HYPOTHESIS;
        }
        if (tracked.meanAbsoluteErrorHz() + 12.0d < hypothesis.meanAbsoluteErrorHz()
                && tracked.availabilityRatio() + 0.05d >= hypothesis.availabilityRatio()) {
            return Winner.TRACKED;
        }
        if (hypothesis.meanAbsoluteErrorHz() + 12.0d < tracked.meanAbsoluteErrorHz()
                && hypothesis.availabilityRatio() + 0.05d >= tracked.availabilityRatio()) {
            return Winner.HYPOTHESIS;
        }
        return Winner.TIE;
    }

    private static PrototypeRecommendation decidePrototypeRecommendation(
            GroundTruthToneMode groundTruthToneMode,
            int disagreementObservedFrameCount,
            int disagreementTrackedCloserFrameCount,
            int disagreementHypothesisCloserFrameCount
    ) {
        if (groundTruthToneMode != GroundTruthToneMode.STABLE_SINGLE_PEAK) {
            return PrototypeRecommendation.STAY_TRACKED;
        }
        if (disagreementObservedFrameCount < 3) {
            return PrototypeRecommendation.STAY_TRACKED;
        }
        if (disagreementHypothesisCloserFrameCount >= disagreementTrackedCloserFrameCount + 2) {
            return PrototypeRecommendation.FAVOR_HYPOTHESIS_GUARD;
        }
        return PrototypeRecommendation.STAY_TRACKED;
    }

    private static CwFixtureScenario requireScenario(String scenarioId) {
        for (CwFixtureScenario scenario : CwFixtureLibrary.scenarios()) {
            if (scenario.id().equals(scenarioId)) {
                return scenario;
            }
        }
        throw new IllegalArgumentException("Unknown fixture scenario: " + scenarioId);
    }

    enum Winner {
        TRACKED,
        HYPOTHESIS,
        TIE,
        INSUFFICIENT
    }

    enum GroundTruthToneMode {
        STABLE_SINGLE_PEAK,
        TRANSITIONAL,
        VARIABLE_OR_SWEEP
    }

    enum PrototypeRecommendation {
        FAVOR_HYPOTHESIS_GUARD,
        STAY_TRACKED
    }

    static final class ToneTruthComparison {
        private final String scenarioId;
        private final int preferredToneHz;
        private final int activeTruthFrameCount;
        private final int coObservedFrameCount;
        private final int trackedCloserFrameCount;
        private final int hypothesisCloserFrameCount;
        private final int tieCloserFrameCount;
        private final int disagreementObservedFrameCount;
        private final int disagreementTrackedCloserFrameCount;
        private final int disagreementHypothesisCloserFrameCount;
        private final int disagreementTieCloserFrameCount;
        private final int trackedRescueFrameCount;
        private final int hypothesisRescueFrameCount;
        private final double truthToneSpanHz;
        private final GroundTruthToneMode groundTruthToneMode;
        private final List<DisagreementFrame> disagreements;
        private final ToneMetrics trackedMetrics;
        private final ToneMetrics hypothesisMetrics;
        private final Winner winner;
        private final PrototypeRecommendation prototypeRecommendation;
        private final int activeWindowObservationCount;
        private final int activeAcquisitionCenterFrequencyHz;
        private final int activeHypothesisObservationCount;
        private final int activeHypothesisCenterFrequencyHz;
        private final int representativeCompetitionObservationCount;
        private final int representativeCompetitionTrackedWinFrames;
        private final int representativeCompetitionHypothesisWinFrames;
        private final int representativeCompetitionHypothesisMaxWinStreak;
        private final int activeCenterCompetitionObservationCount;
        private final int activeCenterCompetitionTrackedWinFrames;
        private final int activeCenterCompetitionHypothesisWinFrames;
        private final int activeCenterCompetitionHypothesisMaxWinStreak;

        private ToneTruthComparison(
                String scenarioId,
                int preferredToneHz,
                int activeTruthFrameCount,
                int coObservedFrameCount,
                int trackedCloserFrameCount,
                int hypothesisCloserFrameCount,
                int tieCloserFrameCount,
                int disagreementObservedFrameCount,
                int disagreementTrackedCloserFrameCount,
                int disagreementHypothesisCloserFrameCount,
                int disagreementTieCloserFrameCount,
                int trackedRescueFrameCount,
                int hypothesisRescueFrameCount,
                double truthToneSpanHz,
                GroundTruthToneMode groundTruthToneMode,
                List<DisagreementFrame> disagreements,
                ToneMetrics trackedMetrics,
                ToneMetrics hypothesisMetrics,
                Winner winner,
                PrototypeRecommendation prototypeRecommendation,
                int activeWindowObservationCount,
                int activeAcquisitionCenterFrequencyHz,
                int activeHypothesisObservationCount,
                int activeHypothesisCenterFrequencyHz,
                int representativeCompetitionObservationCount,
                int representativeCompetitionTrackedWinFrames,
                int representativeCompetitionHypothesisWinFrames,
                int representativeCompetitionHypothesisMaxWinStreak,
                int activeCenterCompetitionObservationCount,
                int activeCenterCompetitionTrackedWinFrames,
                int activeCenterCompetitionHypothesisWinFrames,
                int activeCenterCompetitionHypothesisMaxWinStreak
        ) {
            this.scenarioId = scenarioId;
            this.preferredToneHz = preferredToneHz;
            this.activeTruthFrameCount = activeTruthFrameCount;
            this.coObservedFrameCount = coObservedFrameCount;
            this.trackedCloserFrameCount = trackedCloserFrameCount;
            this.hypothesisCloserFrameCount = hypothesisCloserFrameCount;
            this.tieCloserFrameCount = tieCloserFrameCount;
            this.disagreementObservedFrameCount = disagreementObservedFrameCount;
            this.disagreementTrackedCloserFrameCount = disagreementTrackedCloserFrameCount;
            this.disagreementHypothesisCloserFrameCount = disagreementHypothesisCloserFrameCount;
            this.disagreementTieCloserFrameCount = disagreementTieCloserFrameCount;
            this.trackedRescueFrameCount = trackedRescueFrameCount;
            this.hypothesisRescueFrameCount = hypothesisRescueFrameCount;
            this.truthToneSpanHz = truthToneSpanHz;
            this.groundTruthToneMode = groundTruthToneMode;
            this.disagreements = disagreements;
            this.trackedMetrics = trackedMetrics;
            this.hypothesisMetrics = hypothesisMetrics;
            this.winner = winner;
            this.prototypeRecommendation = prototypeRecommendation;
            this.activeWindowObservationCount = activeWindowObservationCount;
            this.activeAcquisitionCenterFrequencyHz = activeAcquisitionCenterFrequencyHz;
            this.activeHypothesisObservationCount = activeHypothesisObservationCount;
            this.activeHypothesisCenterFrequencyHz = activeHypothesisCenterFrequencyHz;
            this.representativeCompetitionObservationCount = representativeCompetitionObservationCount;
            this.representativeCompetitionTrackedWinFrames = representativeCompetitionTrackedWinFrames;
            this.representativeCompetitionHypothesisWinFrames = representativeCompetitionHypothesisWinFrames;
            this.representativeCompetitionHypothesisMaxWinStreak = representativeCompetitionHypothesisMaxWinStreak;
            this.activeCenterCompetitionObservationCount = activeCenterCompetitionObservationCount;
            this.activeCenterCompetitionTrackedWinFrames = activeCenterCompetitionTrackedWinFrames;
            this.activeCenterCompetitionHypothesisWinFrames = activeCenterCompetitionHypothesisWinFrames;
            this.activeCenterCompetitionHypothesisMaxWinStreak = activeCenterCompetitionHypothesisMaxWinStreak;
        }

        String scenarioId() {
            return scenarioId;
        }

        int activeTruthFrameCount() {
            return activeTruthFrameCount;
        }

        int coObservedFrameCount() {
            return coObservedFrameCount;
        }

        int trackedCloserFrameCount() {
            return trackedCloserFrameCount;
        }

        int hypothesisCloserFrameCount() {
            return hypothesisCloserFrameCount;
        }

        int tieCloserFrameCount() {
            return tieCloserFrameCount;
        }

        int disagreementFrameCount() {
            return disagreements.size();
        }

        int disagreementObservedFrameCount() {
            return disagreementObservedFrameCount;
        }

        int disagreementTrackedCloserFrameCount() {
            return disagreementTrackedCloserFrameCount;
        }

        int disagreementHypothesisCloserFrameCount() {
            return disagreementHypothesisCloserFrameCount;
        }

        int disagreementTieCloserFrameCount() {
            return disagreementTieCloserFrameCount;
        }

        int trackedRescueFrameCount() {
            return trackedRescueFrameCount;
        }

        int hypothesisRescueFrameCount() {
            return hypothesisRescueFrameCount;
        }

        double truthToneSpanHz() {
            return truthToneSpanHz;
        }

        GroundTruthToneMode groundTruthToneMode() {
            return groundTruthToneMode;
        }

        ToneMetrics trackedMetrics() {
            return trackedMetrics;
        }

        ToneMetrics hypothesisMetrics() {
            return hypothesisMetrics;
        }

        Winner winner() {
            return winner;
        }

        PrototypeRecommendation prototypeRecommendation() {
            return prototypeRecommendation;
        }

        int activeWindowObservationCount() {
            return activeWindowObservationCount;
        }

        int activeAcquisitionCenterFrequencyHz() {
            return activeAcquisitionCenterFrequencyHz;
        }

        int activeHypothesisObservationCount() {
            return activeHypothesisObservationCount;
        }

        int activeHypothesisCenterFrequencyHz() {
            return activeHypothesisCenterFrequencyHz;
        }

        int representativeCompetitionObservationCount() {
            return representativeCompetitionObservationCount;
        }

        int representativeCompetitionTrackedWinFrames() {
            return representativeCompetitionTrackedWinFrames;
        }

        int representativeCompetitionHypothesisWinFrames() {
            return representativeCompetitionHypothesisWinFrames;
        }

        int representativeCompetitionHypothesisMaxWinStreak() {
            return representativeCompetitionHypothesisMaxWinStreak;
        }

        int activeCenterCompetitionObservationCount() {
            return activeCenterCompetitionObservationCount;
        }

        int activeCenterCompetitionTrackedWinFrames() {
            return activeCenterCompetitionTrackedWinFrames;
        }

        int activeCenterCompetitionHypothesisWinFrames() {
            return activeCenterCompetitionHypothesisWinFrames;
        }

        int activeCenterCompetitionHypothesisMaxWinStreak() {
            return activeCenterCompetitionHypothesisMaxWinStreak;
        }

        String renderSummary() {
            StringBuilder builder = new StringBuilder()
                    .append("scenario=").append(scenarioId)
                    .append(", pref=").append(preferredToneHz).append("Hz")
                    .append(", activeTruthFrames=").append(activeTruthFrameCount)
                    .append(", truthSpan=").append(String.format(Locale.US, "%.1f", truthToneSpanHz)).append("Hz")
                    .append(", truthMode=").append(groundTruthToneMode)
                    .append(", coObserved=").append(coObservedFrameCount)
                    .append(", closer(trk/hyp/tie)=")
                    .append(trackedCloserFrameCount).append("/")
                    .append(hypothesisCloserFrameCount).append("/")
                    .append(tieCloserFrameCount)
                    .append(", disagreementCloser(trk/hyp/tie)=")
                    .append(disagreementTrackedCloserFrameCount).append("/")
                    .append(disagreementHypothesisCloserFrameCount).append("/")
                    .append(disagreementTieCloserFrameCount)
                    .append(", rescue(trk/hyp)=")
                    .append(trackedRescueFrameCount).append("/")
                    .append(hypothesisRescueFrameCount)
                    .append(", winner=").append(winner)
                    .append(", proto=").append(prototypeRecommendation)
                    .append(", centers(acq/hypObs/hyp)=")
                    .append(activeAcquisitionCenterFrequencyHz).append("Hz/")
                    .append(activeHypothesisObservationCount).append("/")
                    .append(activeHypothesisCenterFrequencyHz).append("Hz")
                    .append(", repComp(obs/trk/hyp/maxHyp)=")
                    .append(representativeCompetitionObservationCount).append("/")
                    .append(representativeCompetitionTrackedWinFrames).append("/")
                    .append(representativeCompetitionHypothesisWinFrames).append("/")
                    .append(representativeCompetitionHypothesisMaxWinStreak)
                    .append(", actComp(obs/trk/hyp/maxHyp)=")
                    .append(activeCenterCompetitionObservationCount).append("/")
                    .append(activeCenterCompetitionTrackedWinFrames).append("/")
                    .append(activeCenterCompetitionHypothesisWinFrames).append("/")
                    .append(activeCenterCompetitionHypothesisMaxWinStreak)
                    .append("\ntracked: ").append(trackedMetrics.renderSummary())
                    .append("\nhypothesis: ").append(hypothesisMetrics.renderSummary());

            if (!disagreements.isEmpty()) {
                builder.append("\ndisagreements:");
                int limit = Math.min(6, disagreements.size());
                for (int i = 0; i < limit; i++) {
                    builder.append("\n  ").append(disagreements.get(i).renderSummary());
                }
            }
            return builder.toString();
        }
    }

    static final class ToneMetrics {
        private final String label;
        private final int availableFrameCount;
        private final double availabilityRatio;
        private final double meanAbsoluteErrorHz;
        private final double meanSignedErrorHz;
        private final double hitRate;
        private final int largeErrorFrameCount;

        private ToneMetrics(
                String label,
                int availableFrameCount,
                double availabilityRatio,
                double meanAbsoluteErrorHz,
                double meanSignedErrorHz,
                double hitRate,
                int largeErrorFrameCount
        ) {
            this.label = label;
            this.availableFrameCount = availableFrameCount;
            this.availabilityRatio = availabilityRatio;
            this.meanAbsoluteErrorHz = meanAbsoluteErrorHz;
            this.meanSignedErrorHz = meanSignedErrorHz;
            this.hitRate = hitRate;
            this.largeErrorFrameCount = largeErrorFrameCount;
        }

        int availableFrameCount() {
            return availableFrameCount;
        }

        double availabilityRatio() {
            return availabilityRatio;
        }

        double meanAbsoluteErrorHz() {
            return meanAbsoluteErrorHz;
        }

        double hitRate() {
            return hitRate;
        }

        int largeErrorFrameCount() {
            return largeErrorFrameCount;
        }

        String renderSummary() {
            return String.format(
                    Locale.US,
                    "%s available=%d ratio=%.2f mae=%.1fHz bias=%.1fHz hit=%.2f large=%d",
                    label,
                    availableFrameCount,
                    availabilityRatio,
                    meanAbsoluteErrorHz,
                    meanSignedErrorHz,
                    hitRate,
                    largeErrorFrameCount
            );
        }
    }

    static final class DisagreementFrame {
        private final int frameIndex;
        private final long frameStartTimestampMs;
        private final double truthToneHz;
        private final int trackedToneHz;
        private final double trackedErrorHz;
        private final int hypothesisToneHz;
        private final double hypothesisErrorHz;
        private final double hypothesisConfidence;
        private final String hypothesisSource;

        private DisagreementFrame(
                int frameIndex,
                long frameStartTimestampMs,
                double truthToneHz,
                int trackedToneHz,
                double trackedErrorHz,
                int hypothesisToneHz,
                double hypothesisErrorHz,
                double hypothesisConfidence,
                String hypothesisSource
        ) {
            this.frameIndex = frameIndex;
            this.frameStartTimestampMs = frameStartTimestampMs;
            this.truthToneHz = truthToneHz;
            this.trackedToneHz = trackedToneHz;
            this.trackedErrorHz = trackedErrorHz;
            this.hypothesisToneHz = hypothesisToneHz;
            this.hypothesisErrorHz = hypothesisErrorHz;
            this.hypothesisConfidence = hypothesisConfidence;
            this.hypothesisSource = hypothesisSource;
        }

        String renderSummary() {
            return String.format(
                    Locale.US,
                    "frame=%d t=%dms truth=%.1fHz tracked=%dHz(%.1f) hyp=%dHz(%.1f conf=%.2f src=%s)",
                    frameIndex,
                    frameStartTimestampMs,
                    truthToneHz,
                    trackedToneHz,
                    trackedErrorHz,
                    hypothesisToneHz,
                    hypothesisErrorHz,
                    hypothesisConfidence,
                    hypothesisSource
            );
        }
    }

    private static final class MetricAccumulator {
        private final String label;
        private int availableFrameCount;
        private int hitFrameCount;
        private int largeErrorFrameCount;
        private double absoluteErrorSumHz;
        private double signedErrorSumHz;

        private MetricAccumulator(String label) {
            this.label = label;
        }

        private void record(
                int estimateToneHz,
                double truthToneHz,
                double absoluteErrorHz,
                int hitToleranceHz,
                int largeErrorThresholdHz
        ) {
            availableFrameCount += 1;
            absoluteErrorSumHz += absoluteErrorHz;
            signedErrorSumHz += estimateToneHz - truthToneHz;
            if (absoluteErrorHz <= hitToleranceHz) {
                hitFrameCount += 1;
            }
            if (absoluteErrorHz >= largeErrorThresholdHz) {
                largeErrorFrameCount += 1;
            }
        }

        private ToneMetrics finish(int activeTruthFrameCount) {
            if (availableFrameCount <= 0) {
                return new ToneMetrics(label, 0, 0.0d, Double.POSITIVE_INFINITY, 0.0d, 0.0d, 0);
            }
            return new ToneMetrics(
                    label,
                    availableFrameCount,
                    activeTruthFrameCount <= 0 ? 0.0d : availableFrameCount / (double) activeTruthFrameCount,
                    absoluteErrorSumHz / availableFrameCount,
                    signedErrorSumHz / availableFrameCount,
                    hitFrameCount / (double) availableFrameCount,
                    largeErrorFrameCount
            );
        }
    }
}
