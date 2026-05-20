package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.eval.CwFixtureLibrary;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CwNearFrequencyToneEventGapProbeTest {
    private static final int REFERENCE_TONE_HZ = 670;
    private static final int MAX_RENDERED_SEGMENTS = 8;

    @Test
    public void printNearFrequencyLiveVsForced670ToneEventGaps() {
        CwFixtureScenario scenario = findScenario("near_frequency_narrowband_noise_report");
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        List<AudioFrame> frames = source.renderFramesForTesting(scenario);

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult live =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        scenario.id(),
                        frames,
                        scenario.toneFrequencyHz(),
                        scenario.wpm(),
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
        LocalAudioDecodeTestSupport.ForcedToneReplayResult forced670 =
                LocalAudioDecodeTestSupport.replayForcedConstantToneDecode(live, REFERENCE_TONE_HZ);

        GapSummary summary = analyzeGapSummary(live.frameSignalTraces(), forced670.frameSignalTraces());

        System.out.println("==== near-frequency live vs forced670 tone-event gaps ====");
        System.out.println("live text=" + live.probeResult().decodedText());
        System.out.println("forced text=" + forced670.decodedText());
        System.out.println(summary.render());
        for (int index = 0; index < Math.min(MAX_RENDERED_SEGMENTS, summary.liveOnlySegments.size()); index++) {
            GapSegment segment = summary.liveOnlySegments.get(index);
            System.out.println(segment.renderHeader("live-stuck", index + 1));
            for (int frameIndex = segment.startFrameIndex; frameIndex <= segment.endFrameIndex; frameIndex++) {
                System.out.println(renderComparison(
                        frameIndex,
                        live.frameSignalTraces().get(frameIndex),
                        forced670.frameSignalTraces().get(frameIndex)
                ));
            }
        }
        for (int index = 0; index < Math.min(MAX_RENDERED_SEGMENTS, summary.forcedOnlySegments.size()); index++) {
            GapSegment segment = summary.forcedOnlySegments.get(index);
            System.out.println(segment.renderHeader("forced-only", index + 1));
            for (int frameIndex = segment.startFrameIndex; frameIndex <= segment.endFrameIndex; frameIndex++) {
                System.out.println(renderComparison(
                        frameIndex,
                        live.frameSignalTraces().get(frameIndex),
                        forced670.frameSignalTraces().get(frameIndex)
                ));
            }
        }
    }

    private GapSummary analyzeGapSummary(
            List<LocalAudioDecodeTestSupport.FrameSignalTrace> liveTraces,
            List<LocalAudioDecodeTestSupport.FrameSignalTrace> forcedTraces
    ) {
        GapSummary summary = new GapSummary();
        GapSegment openForcedOnlySegment = null;
        GapSegment openLiveOnlySegment = null;
        int frameCount = Math.min(liveTraces.size(), forcedTraces.size());
        for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
            LocalAudioDecodeTestSupport.FrameSignalTrace live = liveTraces.get(frameIndex);
            LocalAudioDecodeTestSupport.FrameSignalTrace forced = forcedTraces.get(frameIndex);
            CwSignalSnapshot liveSnapshot = live.snapshot();
            CwSignalSnapshot forcedSnapshot = forced.snapshot();
            boolean forcedOnlyActive = forcedSnapshot.toneActive() && !liveSnapshot.toneActive();
            boolean liveOnlyActive = liveSnapshot.toneActive() && !forcedSnapshot.toneActive();

            if (forcedOnlyActive) {
                summary.forcedOnlyActiveFrames += 1;
                if (!live.attackQualified()) {
                    summary.liveUnqualifiedFrames += 1;
                }
                if (live.detectionLevel() < liveSnapshot.currentThreshold()) {
                    summary.liveBelowAttackFrames += 1;
                }
                if (Math.abs(liveSnapshot.targetToneFrequencyHz() - REFERENCE_TONE_HZ) > 20) {
                    summary.liveOffTargetFrames += 1;
                }
                if (openForcedOnlySegment == null) {
                    openForcedOnlySegment = new GapSegment(frameIndex, frameIndex);
                } else {
                    openForcedOnlySegment.endFrameIndex = frameIndex;
                }
            } else if (openForcedOnlySegment != null) {
                summary.forcedOnlySegments.add(openForcedOnlySegment);
                openForcedOnlySegment = null;
            }

            if (liveOnlyActive) {
                summary.liveOnlyActiveFrames += 1;
                if (live.releaseTailHoldApplied()) {
                    summary.liveReleaseTailHoldFrames += 1;
                }
                if (live.weakValleyBridgeActive()) {
                    summary.liveWeakValleyBridgeFrames += 1;
                }
                if (live.detectionLevel() >= liveSnapshot.releaseThreshold()) {
                    summary.liveAboveReleaseFrames += 1;
                }
                if (openLiveOnlySegment == null) {
                    openLiveOnlySegment = new GapSegment(frameIndex, frameIndex);
                } else {
                    openLiveOnlySegment.endFrameIndex = frameIndex;
                }
            } else if (openLiveOnlySegment != null) {
                summary.liveOnlySegments.add(openLiveOnlySegment);
                openLiveOnlySegment = null;
            }
        }
        if (openForcedOnlySegment != null) {
            summary.forcedOnlySegments.add(openForcedOnlySegment);
        }
        if (openLiveOnlySegment != null) {
            summary.liveOnlySegments.add(openLiveOnlySegment);
        }
        return summary;
    }

    private String renderComparison(
            int frameIndex,
            LocalAudioDecodeTestSupport.FrameSignalTrace live,
            LocalAudioDecodeTestSupport.FrameSignalTrace forced
    ) {
        CwSignalSnapshot liveSnapshot = live.snapshot();
        CwSignalSnapshot forcedSnapshot = forced.snapshot();
        return String.format(
                Locale.US,
                "F%03d @%4dms live[%s det=%.0f thr=%d rel=%d aq=%s tgt=%d eff=%d rms=%.0f iso=%.2f bridge=%s hold=%s on=%s off=%s] "
                        + "fix[%s det=%.0f thr=%d rel=%d aq=%s tgt=%d rms=%.0f iso=%.2f bridge=%s hold=%s on=%s off=%s]",
                frameIndex,
                live.timestampMs(),
                liveSnapshot.toneActive() ? "ON " : "OFF",
                live.detectionLevel(),
                liveSnapshot.currentThreshold(),
                liveSnapshot.releaseThreshold(),
                live.attackQualified() ? "Y" : "N",
                liveSnapshot.targetToneFrequencyHz(),
                liveSnapshot.effectiveTrackedToneFrequencyHz(),
                liveSnapshot.lastToneRmsAmplitude(),
                liveSnapshot.narrowbandIsolationRatio(),
                live.weakValleyBridgeActive() ? "Y" : "N",
                live.releaseTailHoldApplied() ? "Y" : "N",
                live.toneOnDecision(),
                live.releaseTailHoldDecision(),
                forcedSnapshot.toneActive() ? "ON " : "OFF",
                forced.detectionLevel(),
                forcedSnapshot.currentThreshold(),
                forcedSnapshot.releaseThreshold(),
                forced.attackQualified() ? "Y" : "N",
                forcedSnapshot.targetToneFrequencyHz(),
                forcedSnapshot.lastToneRmsAmplitude(),
                forcedSnapshot.narrowbandIsolationRatio(),
                forced.weakValleyBridgeActive() ? "Y" : "N",
                forced.releaseTailHoldApplied() ? "Y" : "N",
                forced.toneOnDecision(),
                forced.releaseTailHoldDecision()
        );
    }

    private CwFixtureScenario findScenario(String scenarioId) {
        for (CwFixtureScenario scenario : CwFixtureLibrary.scenarios()) {
            if (scenario.id().equals(scenarioId)) {
                return scenario;
            }
        }
        throw new IllegalArgumentException("Unknown scenario: " + scenarioId);
    }

    private static final class GapSummary {
        private int forcedOnlyActiveFrames;
        private int liveOnlyActiveFrames;
        private int liveUnqualifiedFrames;
        private int liveBelowAttackFrames;
        private int liveOffTargetFrames;
        private int liveReleaseTailHoldFrames;
        private int liveWeakValleyBridgeFrames;
        private int liveAboveReleaseFrames;
        private final List<GapSegment> forcedOnlySegments = new ArrayList<>();
        private final List<GapSegment> liveOnlySegments = new ArrayList<>();

        private String render() {
            return String.format(
                    Locale.US,
                    "forcedOnlyActive=%d liveOnlyActive=%d liveUnqualified=%d liveBelowAttack=%d liveOffTarget=%d "
                            + "liveReleaseHold=%d liveWeakBridge=%d liveAboveRelease=%d forcedOnlySegments=%d liveOnlySegments=%d",
                    forcedOnlyActiveFrames,
                    liveOnlyActiveFrames,
                    liveUnqualifiedFrames,
                    liveBelowAttackFrames,
                    liveOffTargetFrames,
                    liveReleaseTailHoldFrames,
                    liveWeakValleyBridgeFrames,
                    liveAboveReleaseFrames,
                    forcedOnlySegments.size(),
                    liveOnlySegments.size()
            );
        }
    }

    private static final class GapSegment {
        private final int startFrameIndex;
        private int endFrameIndex;

        private GapSegment(int startFrameIndex, int endFrameIndex) {
            this.startFrameIndex = startFrameIndex;
            this.endFrameIndex = endFrameIndex;
        }

        private String renderHeader(String label, int ordinal) {
            return String.format(
                    Locale.US,
                    "-- %s-%d frames=%d..%d span=%d --",
                    label,
                    ordinal,
                    startFrameIndex,
                    endFrameIndex,
                    (endFrameIndex - startFrameIndex) + 1
            );
        }
    }
}
