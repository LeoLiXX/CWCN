package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CwLocalAudioFrontEndWindowProbeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;
    private static final long WINDOW_PADDING_MS = 120L;
    private static final long OPENING_UNKNOWN_WINDOW_END_MS = 8000L;
    private static final ProbeTarget[] OPENING_TARGETS = new ProbeTarget[]{
            new ProbeTarget("opening-first-unknown", null, OPENING_UNKNOWN_WINDOW_END_MS),
            new ProbeTarget("questioned-bg1xxx", "-.--...", Long.MAX_VALUE),
            new ProbeTarget("questioned-ja1abc", "-.-.---", Long.MAX_VALUE)
    };

    @Test
    public void printRecording8OpeningFrontEndWindows() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().contains("(8)"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for recording (8)"));
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                        wavFile,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
        LocalAudioDecodeTestSupport.ForcedToneReplayResult trkReplay =
                LocalAudioDecodeTestSupport.replayForcedTrackedToneDecode(detailed);
        LocalAudioDecodeTestSupport.ForcedToneReplayResult effReplay =
                LocalAudioDecodeTestSupport.replayForcedEffectiveTrackedToneDecode(detailed);
        LocalAudioDecodeTestSupport.ForcedToneReplayResult hypReplay =
                LocalAudioDecodeTestSupport.replayForcedHypothesisToneDecode(detailed);

        List<CharacterTimingDetail> baseDetails = buildCharacterTimingDetails(
                detailed.rawDecodeEvents(),
                detailed.timingEvents()
        );
        List<CharacterTimingDetail> trkDetails = buildCharacterTimingDetails(
                trkReplay.decodeEvents(),
                trkReplay.timingEvents()
        );
        List<CharacterTimingDetail> effDetails = buildCharacterTimingDetails(
                effReplay.decodeEvents(),
                effReplay.timingEvents()
        );
        List<CharacterTimingDetail> hypDetails = buildCharacterTimingDetails(
                hypReplay.decodeEvents(),
                hypReplay.timingEvents()
        );
        for (ProbeTarget target : OPENING_TARGETS) {
            WindowReference reference = findWindowReference(
                    target.sequence,
                    target.maxTimestampMs,
                    baseDetails,
                    trkDetails,
                    effDetails,
                    hypDetails
            );
            System.out.println("==== " + detailed.probeResult().sourceLabel() + " front-end [" + target.label + "] ====");
            if (reference == null) {
                System.out.println("target-seq=" + target.sequence + " not-found");
                continue;
            }
            CharacterTimingDetail detail = reference.detail;
            long startTimestampMs = Math.max(0L, firstCharacterTimestampMs(detail) - WINDOW_PADDING_MS);
            long endTimestampMs = lastCharacterTimestampMs(detail) + WINDOW_PADDING_MS;
            System.out.println("locator=" + reference.label);
            System.out.println("seq=" + detail.decodeEvent.sourceSequence()
                    + " ts=" + detail.decodeEvent.timestampMs()
                    + " window=" + startTimestampMs + ".." + endTimestampMs);
            System.out.println("tones=" + renderTones(detail.internalToneEvents()));
            System.out.println("gaps=" + renderGaps(detail.internalGapEvents()));
            printFrameWindow(detailed, startTimestampMs, endTimestampMs);
            printToneEventWindow(detailed.toneEvents(), startTimestampMs, endTimestampMs);
            printTimingEventWindow(detailed.timingEvents(), startTimestampMs, endTimestampMs);
            printDecodeEventWindow(detailed.rawDecodeEvents(), startTimestampMs, endTimestampMs);
        }
    }

    private static List<CharacterTimingDetail> buildCharacterTimingDetails(
            List<CwDecodeEvent> decodeEvents,
            List<CwTimingEvent> timingEvents
    ) {
        ArrayList<CwDecodeEvent> characterEvents = new ArrayList<>();
        for (CwDecodeEvent event : decodeEvents) {
            if (event.type() == CwDecodeEvent.Type.CHARACTER_DECODED) {
                characterEvents.add(event);
            }
        }

        ArrayList<CharacterTimingDetail> details = new ArrayList<>();
        int boundarySearchStart = 0;
        for (CwDecodeEvent characterEvent : characterEvents) {
            int boundaryIndex = findBoundaryIndexForCharacter(timingEvents, characterEvent, boundarySearchStart);
            CwTimingEvent boundaryGapEvent = boundaryIndex >= 0 ? timingEvents.get(boundaryIndex) : null;
            List<CwTimingEvent> characterTimingEvents = collectCharacterTimingEvents(
                    timingEvents,
                    boundaryIndex,
                    characterEvent.sourceSequence()
            );
            details.add(new CharacterTimingDetail(characterEvent, characterTimingEvents, boundaryGapEvent));
            if (boundaryIndex >= 0) {
                boundarySearchStart = boundaryIndex + 1;
            }
        }
        return details;
    }

    private static CharacterTimingDetail findCharacterDetail(
            List<CharacterTimingDetail> details,
            String sourceSequence
    ) {
        for (CharacterTimingDetail detail : details) {
            if (detail.decodeEvent.unknownCharacter()
                    && sourceSequence.equals(detail.decodeEvent.sourceSequence())) {
                return detail;
            }
        }
        return null;
    }

    private static WindowReference findWindowReference(
            String sourceSequence,
            long maxTimestampMs,
            List<CharacterTimingDetail> baseDetails,
            List<CharacterTimingDetail> trkDetails,
            List<CharacterTimingDetail> effDetails,
            List<CharacterTimingDetail> hypDetails
    ) {
        CharacterTimingDetail detail = findCharacterDetail(baseDetails, sourceSequence, maxTimestampMs);
        if (detail != null) {
            return new WindowReference("BASE", detail);
        }
        detail = findCharacterDetail(trkDetails, sourceSequence, maxTimestampMs);
        if (detail != null) {
            return new WindowReference("TRK-REPLAY", detail);
        }
        detail = findCharacterDetail(effDetails, sourceSequence, maxTimestampMs);
        if (detail != null) {
            return new WindowReference("EFF-REPLAY", detail);
        }
        detail = findCharacterDetail(hypDetails, sourceSequence, maxTimestampMs);
        if (detail != null) {
            return new WindowReference("HYP-REPLAY", detail);
        }
        return null;
    }

    private static CharacterTimingDetail findCharacterDetail(
            List<CharacterTimingDetail> details,
            String sourceSequence,
            long maxTimestampMs
    ) {
        if (sourceSequence == null) {
            return findFirstUnknownDetail(details, maxTimestampMs);
        }
        return findCharacterDetail(details, sourceSequence);
    }

    private static CharacterTimingDetail findFirstUnknownDetail(
            List<CharacterTimingDetail> details,
            long maxTimestampMs
    ) {
        for (CharacterTimingDetail detail : details) {
            if (!detail.decodeEvent.unknownCharacter()) {
                continue;
            }
            if (detail.decodeEvent.timestampMs() <= maxTimestampMs) {
                return detail;
            }
        }
        return null;
    }

    private static int findBoundaryIndexForCharacter(
            List<CwTimingEvent> timingEvents,
            CwDecodeEvent characterEvent,
            int searchStart
    ) {
        int fallbackIndex = -1;
        for (int index = Math.max(0, searchStart); index < timingEvents.size(); index++) {
            CwTimingEvent timingEvent = timingEvents.get(index);
            if (timingEvent.kind() != CwTimingEvent.Kind.GAP
                    || timingEvent.classification() == CwTimingEvent.Classification.INTRA_SYMBOL_GAP) {
                continue;
            }
            if (timingEvent.timestampMs() == characterEvent.timestampMs()) {
                return index;
            }
            if (fallbackIndex < 0 && timingEvent.timestampMs() > characterEvent.timestampMs()) {
                fallbackIndex = index;
            }
        }
        return fallbackIndex;
    }

    private static List<CwTimingEvent> collectCharacterTimingEvents(
            List<CwTimingEvent> timingEvents,
            int boundaryIndex,
            String sourceSequence
    ) {
        int requiredTones = sourceSequence == null ? 0 : sourceSequence.length();
        if (requiredTones <= 0) {
            return new ArrayList<>();
        }
        int scanIndex = boundaryIndex >= 0 ? boundaryIndex - 1 : timingEvents.size() - 1;
        ArrayList<CwTimingEvent> reversed = new ArrayList<>();
        int collectedTones = 0;
        while (scanIndex >= 0 && collectedTones < requiredTones) {
            CwTimingEvent timingEvent = timingEvents.get(scanIndex);
            if (timingEvent.kind() == CwTimingEvent.Kind.TONE) {
                reversed.add(timingEvent);
                collectedTones += 1;
                scanIndex -= 1;
                continue;
            }
            if (timingEvent.classification() == CwTimingEvent.Classification.INTRA_SYMBOL_GAP) {
                reversed.add(timingEvent);
                scanIndex -= 1;
                continue;
            }
            break;
        }
        ArrayList<CwTimingEvent> ordered = new ArrayList<>(reversed.size());
        for (int index = reversed.size() - 1; index >= 0; index--) {
            ordered.add(reversed.get(index));
        }
        return ordered;
    }

    private static long firstCharacterTimestampMs(CharacterTimingDetail detail) {
        if (detail.characterEvents.isEmpty()) {
            return detail.decodeEvent.timestampMs();
        }
        return detail.characterEvents.get(0).timestampMs();
    }

    private static long lastCharacterTimestampMs(CharacterTimingDetail detail) {
        if (detail.boundaryGapEvent != null) {
            return detail.boundaryGapEvent.timestampMs();
        }
        if (detail.characterEvents.isEmpty()) {
            return detail.decodeEvent.timestampMs();
        }
        return detail.characterEvents.get(detail.characterEvents.size() - 1).timestampMs();
    }

    private static void printFrameWindow(
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed,
            long startTimestampMs,
            long endTimestampMs
    ) {
        System.out.println("-- frames --");
        for (int index = 0; index < detailed.frameSignalTraces().size(); index++) {
            LocalAudioDecodeTestSupport.FrameSignalTrace trace = detailed.frameSignalTraces().get(index);
            long timestampMs = trace.timestampMs();
            if (timestampMs < startTimestampMs || timestampMs > endTimestampMs) {
                continue;
            }
            AudioFrame frame = detailed.frames().get(index);
            CwSignalSnapshot snapshot = trace.snapshot();
            CwSignalSnapshot previousSnapshot = index > 0 ? detailed.frameSignalTraces().get(index - 1).snapshot() : null;
            int deltaOn = previousSnapshot == null
                    ? snapshot.totalToneOnEvents()
                    : snapshot.totalToneOnEvents() - previousSnapshot.totalToneOnEvents();
            int deltaOff = previousSnapshot == null
                    ? snapshot.totalToneOffEvents()
                    : snapshot.totalToneOffEvents() - previousSnapshot.totalToneOffEvents();
            System.out.println(String.format(
                    Locale.US,
                    "F @%d peak=%d rms=%.1f det=%.1f clip=%d act=%s lock=%s trk=%d eff=%d hyp=%s tone=%.1f resid=%.1f dom=%.0f%% iso=%.0f%% thr=%d/%d floor=%d/%d "
                            + "wv=%s/%d rel=%s app=%s relThr=%d req=%.1f trust=%s/%s/%s weakHold=%d onset=%s accept=%s/%s rescue=%s curRescue=%s gap=%d/%d cont=%s/%d lc=%.2f on+%d off+%d last=%s",
                    timestampMs,
                    frame.peakAmplitude(),
                    frame.rmsAmplitude(),
                    trace.detectionLevel(),
                    frame.clippedSampleCount(),
                    snapshot.toneActive(),
                    snapshot.targetToneLocked(),
                    snapshot.targetToneFrequencyHz(),
                    snapshot.effectiveTrackedToneFrequencyHz(),
                    renderHypothesis(snapshot),
                    snapshot.lastToneRmsAmplitude(),
                    snapshot.lastWidebandResidualRmsAmplitude(),
                    snapshot.toneDominanceRatio() * 100.0d,
                    snapshot.narrowbandIsolationRatio() * 100.0d,
                    snapshot.currentThreshold(),
                    snapshot.releaseThreshold(),
                    snapshot.noiseFloorEstimate(),
                    snapshot.signalFloorEstimate(),
                    trace.weakValleyBridgeActive(),
                    trace.weakValleyBridgeFramesRemaining(),
                    trace.releaseTailHoldDecision(),
                    trace.releaseTailHoldApplied(),
                    trace.toneActiveReleaseThreshold(),
                    trace.releaseTailHoldRequiredDetectionThreshold(),
                    trace.releaseTailHoldSufficientRecentTrust(),
                    trace.releaseTailHoldCurrentRunStableBootstrapEligible(),
                    trace.releaseTailHoldCurrentRunWeakBootstrapEligible(),
                    trace.currentToneRunWeakBootstrapReleaseTailHoldCount(),
                    compactOnsetDecision(trace),
                    trace.toneOnAccepted(),
                    trace.toneOnAcceptedByRescue(),
                    compactRescueDecision(trace),
                    trace.currentToneStartedByPostReleaseRescue(),
                    trace.postReleaseGapMs(),
                    trace.postReleaseWindowMs(),
                    trace.postReleaseRescueContinuationWindowActive(),
                    trace.postReleaseRescueContinuationWindowRemainingMs(),
                    trace.localContrastRatio(),
                    deltaOn,
                    deltaOff,
                    renderLastEvent(snapshot.lastEvent())
            ));
        }
    }

    private static String compactOnsetDecision(LocalAudioDecodeTestSupport.FrameSignalTrace trace) {
        return trace.toneOnDecision()
                + "/aq=" + trace.attackQualified()
                + "/mem=" + trace.trackedToneMemoryActiveBeforeFrame()
                + "/aHz=" + trace.attackAnchorFrequencyHzBeforeFrame()
                + "/onThr=" + trace.toneOnThreshold();
    }

    private static String compactRescueDecision(LocalAudioDecodeTestSupport.FrameSignalTrace trace) {
        return trace.postReleaseRescueDecision()
                + "/steady=" + trace.steadyLateGapNearTargetRescueCandidate()
                + "/lowGrow=" + trace.lowGrowthStrongSteadyNearTargetRescue()
                + "/near=" + trace.nearTargetPostReleaseToneOnRescue()
                + "/supp=" + trace.postReleaseSteadyCarrierSuppressed()
                + "/delay=" + trace.farAttackToneOnDelayed();
    }

    private static void printToneEventWindow(
            List<CwToneEvent> toneEvents,
            long startTimestampMs,
            long endTimestampMs
    ) {
        System.out.println("-- tone-events --");
        for (CwToneEvent event : toneEvents) {
            if (event.timestampMs() < startTimestampMs || event.timestampMs() > endTimestampMs) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "T %s @%d dur=%d rms=%.1f peak=%d",
                    event.type(),
                    event.timestampMs(),
                    event.toneDurationMs(),
                    event.rmsAmplitude(),
                    event.peakAmplitude()
            ));
        }
    }

    private static void printTimingEventWindow(
            List<CwTimingEvent> timingEvents,
            long startTimestampMs,
            long endTimestampMs
    ) {
        System.out.println("-- timing-events --");
        for (CwTimingEvent event : timingEvents) {
            if (event.timestampMs() < startTimestampMs || event.timestampMs() > endTimestampMs) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "M %s/%s @%d dur=%d dot=%d",
                    event.kind(),
                    event.classification(),
                    event.timestampMs(),
                    event.durationMs(),
                    event.dotEstimateMs()
            ));
        }
    }

    private static void printDecodeEventWindow(
            List<CwDecodeEvent> decodeEvents,
            long startTimestampMs,
            long endTimestampMs
    ) {
        System.out.println("-- decode-events --");
        for (CwDecodeEvent event : decodeEvents) {
            if (event.timestampMs() < startTimestampMs || event.timestampMs() > endTimestampMs) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "D %s @%d out=%s emit=%s seq=%s unknown=%s",
                    event.type(),
                    event.timestampMs(),
                    sanitize(event.outputText()),
                    sanitize(event.emittedValue()),
                    sanitize(event.sourceSequence()),
                    event.unknownCharacter()
            ));
        }
    }

    private static String renderTones(List<CwTimingEvent> tones) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < tones.size(); index++) {
            if (index > 0) {
                builder.append(" | ");
            }
            CwTimingEvent tone = tones.get(index);
            double ratio = tone.durationMs() / (double) Math.max(1L, tone.dotEstimateMs());
            builder.append(String.format(
                    Locale.US,
                    "t%d=%s/%dms/%.2f",
                    index + 1,
                    tone.classification() == CwTimingEvent.Classification.DAH ? "DAH" : "DIT",
                    tone.durationMs(),
                    ratio
            ));
        }
        return builder.toString();
    }

    private static String renderGaps(List<CwTimingEvent> gaps) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < gaps.size(); index++) {
            if (index > 0) {
                builder.append(" | ");
            }
            CwTimingEvent gap = gaps.get(index);
            double ratio = gap.durationMs() / (double) Math.max(1L, gap.dotEstimateMs());
            builder.append(String.format(
                    Locale.US,
                    "g%d=%dms/%.2f",
                    index + 1,
                    gap.durationMs(),
                    ratio
            ));
        }
        return builder.toString();
    }

    private static String renderHypothesis(CwSignalSnapshot snapshot) {
        if (snapshot.toneHypothesisSupportFrames() <= 0 || "NONE".equals(snapshot.toneHypothesisSource())) {
            return "NONE";
        }
        return snapshot.toneHypothesisFrequencyHz()
                + "@" + Math.round(snapshot.toneHypothesisConfidence() * 100.0d) + "%";
    }

    private static String renderLastEvent(CwToneEvent event) {
        if (event == null) {
            return "-";
        }
        return event.type() + "@" + event.timestampMs() + "/" + event.toneDurationMs();
    }

    private static String sanitize(String value) {
        return value == null ? "(null)" : value.replace('\u25A1', '?');
    }

    private static final class ProbeTarget {
        private final String label;
        private final String sequence;
        private final long maxTimestampMs;

        private ProbeTarget(String label, String sequence, long maxTimestampMs) {
            this.label = label;
            this.sequence = sequence;
            this.maxTimestampMs = maxTimestampMs;
        }
    }

    private static final class CharacterTimingDetail {
        private final CwDecodeEvent decodeEvent;
        private final List<CwTimingEvent> characterEvents;
        private final CwTimingEvent boundaryGapEvent;

        private CharacterTimingDetail(
                CwDecodeEvent decodeEvent,
                List<CwTimingEvent> characterEvents,
                CwTimingEvent boundaryGapEvent
        ) {
            this.decodeEvent = decodeEvent;
            this.characterEvents = characterEvents;
            this.boundaryGapEvent = boundaryGapEvent;
        }

        private List<CwTimingEvent> internalToneEvents() {
            ArrayList<CwTimingEvent> tones = new ArrayList<>();
            for (CwTimingEvent event : characterEvents) {
                if (event.kind() == CwTimingEvent.Kind.TONE) {
                    tones.add(event);
                }
            }
            return tones;
        }

        private List<CwTimingEvent> internalGapEvents() {
            ArrayList<CwTimingEvent> gaps = new ArrayList<>();
            for (CwTimingEvent event : characterEvents) {
                if (event.kind() == CwTimingEvent.Kind.GAP) {
                    gaps.add(event);
                }
            }
            return gaps;
        }
    }

    private static final class WindowReference {
        private final String label;
        private final CharacterTimingDetail detail;

        private WindowReference(String label, CharacterTimingDetail detail) {
            this.label = label;
            this.detail = detail;
        }
    }
}
