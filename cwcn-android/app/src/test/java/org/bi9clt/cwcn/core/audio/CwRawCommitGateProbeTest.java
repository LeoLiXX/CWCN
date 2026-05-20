package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.eval.CwFixtureLibrary;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.rx.RxTurnActivityDecider;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.junit.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.nio.file.Path;
import java.util.ArrayList;

public final class CwRawCommitGateProbeTest {
    private static final double SOFT_WORD_BREAK_GAP_RATIO_PROBE = 5.0d;

    @Test
    public void printLongQsoEdgeHigh800hzRawCommitDiagnostics() {
        printDiagnostics("user_long_qso_edge_high_800hz", 450, 55);
    }

    @Test
    public void printUsbOffset20wpm800hzRawCommitDiagnostics() {
        printDiagnostics("usb_freq_offset_cq_20wpm_800hz", 450, 55);
    }

    @Test
    public void printUserRangeCq10wpm700hzRawCommitDiagnostics() {
        printDiagnostics("user_range_cq_10wpm_700hz", 700, 55);
    }

    @Test
    public void printUserSpeedSweepVvv700hzRawCommitDiagnostics() {
        printDiagnostics("user_speed_sweep_vvv_700hz", 700, 55);
    }

    @Test
    public void printUserToneSweepVvv450hzRawCommitDiagnostics() {
        printDiagnostics("user_tone_sweep_vvv_18wpm", 450, 55);
    }

    @Test
    public void printUserMultiRoundContinuousQsoBi9cltJa1abcRawCommitDiagnostics() {
        printDiagnostics("user_multi_round_continuous_qso_bi9clt_ja1abc", 680, 55);
    }

    @Test
    public void printRecording16RawCommitDiagnostics() throws Exception {
        Path recording16Wav = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(16).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (16)"));
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "recording16-probe",
                        shiftFramesToZero(loadFrames(recording16Wav)),
                        700,
                        15,
                        55,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
        printDetailedDiagnostics("recording16", detailed, 15, 700, 55);
    }

    @Test
    public void printRecording16TailWindowDiagnostics() throws Exception {
        Path recording16Wav = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(16).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 褰曢煶 (16)"));
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "recording16-tail-probe",
                        shiftFramesToZero(loadFrames(recording16Wav)),
                        700,
                        15,
                        55,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
        long windowStartMs = 22800L;
        long windowEndMs = 27650L;
        System.out.println("==== recording16 tail window diagnostics ====");
        System.out.println(String.format(
                Locale.US,
                "window=%d..%d final=%s",
                windowStartMs,
                windowEndMs,
                sanitize(detailed.probeResult().decodedText())
        ));
        System.out.println("-- frame window: 24WPM tail --");
        printFrameSignalWindow(detailed, 23300L, 24050L);
        System.out.println("-- frame window: PSE tail --");
        printFrameSignalWindow(detailed, 26600L, 27150L);
        System.out.println("-- tone events --");
        printToneWindow(detailed.toneEvents(), windowStartMs, windowEndMs);
        System.out.println("-- timing events --");
        printTimingWindow(detailed.timingEvents(), windowStartMs, windowEndMs);
        System.out.println("-- timing adaptation --");
        printTimingAdaptationWindow(detailed.timingEventAdaptationTraces(), windowStartMs, windowEndMs);
        System.out.println("-- raw decode events --");
        printDecodeWindow(detailed.rawDecodeEvents(), windowStartMs, windowEndMs);
        System.out.println("-- committed decode events --");
        printDecodeWindow(detailed.decodeEvents(), windowStartMs, windowEndMs);
        System.out.println("-- stable decisions --");
        printStableDecisionWindow(detailed.stableDecisionTraces(), windowStartMs, windowEndMs);
    }

    @Test
    public void printRecording3Preferred700RawCommitDiagnostics() throws Exception {
        Path recording3Wav = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(3).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (3)"));
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "recording3-pref700-probe",
                        shiftFramesToZero(loadFrames(recording3Wav)),
                        700,
                        15,
                        55,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
        printDetailedDiagnostics("recording3-pref700", detailed, 15, 700, 55);
        long firstRawCharTimestampMs = firstCharacterTimestamp(detailed.rawDecodeEvents());
        long lastRawCharTimestampMs = lastCharacterTimestamp(detailed.rawDecodeEvents());
        long windowStartMs = firstRawCharTimestampMs < 0L
                ? 0L
                : Math.max(0L, firstRawCharTimestampMs - 500L);
        long windowEndMs = lastRawCharTimestampMs < 0L
                ? Math.min(detailed.flushTimestampMs(), 8000L)
                : Math.min(detailed.flushTimestampMs(), lastRawCharTimestampMs + 500L);
        System.out.println("==== recording3 preferred 700 detail window ====");
        System.out.println(String.format(
                Locale.US,
                "window=%d..%d rawChars=%d stableChars=%d committedChars=%d final=%s",
                windowStartMs,
                windowEndMs,
                characterCount(detailed.rawDecodeEvents()),
                characterCount(detailed.stableAcceptedDecodeEvents()),
                committedCharacterCount(detailed.decodeEvents()),
                sanitize(detailed.probeResult().decodedText())
        ));
        System.out.println("-- raw decode events --");
        printDecodeWindow(detailed.rawDecodeEvents(), windowStartMs, windowEndMs);
        System.out.println("-- stable accepted events --");
        printDecodeWindow(detailed.stableAcceptedDecodeEvents(), windowStartMs, windowEndMs);
        System.out.println("-- committed decode events --");
        printDecodeWindow(detailed.decodeEvents(), windowStartMs, windowEndMs);
        System.out.println("-- stable decisions --");
        printStableDecisionWindow(detailed.stableDecisionTraces(), windowStartMs, windowEndMs);
    }

    @Test
    public void printRecording3Preferred650RawCommitDiagnostics() throws Exception {
        Path recording3Wav = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(3).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (3)"));
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "recording3-pref650-probe",
                        shiftFramesToZero(loadFrames(recording3Wav)),
                        650,
                        15,
                        55,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
        printDetailedDiagnostics("recording3-pref650", detailed, 15, 650, 55);
        LocalAudioDecodeTestSupport.TimingStateTrace firstTrustedTrace = firstTrustedTrace(
                detailed.timingStateTraces()
        );
        long trustTimestampMs = firstTrustedTrace == null ? -1L : firstTrustedTrace.timestampMs();
        long windowStartMs = trustTimestampMs < 0L ? 0L : Math.max(0L, trustTimestampMs - 800L);
        long windowEndMs = trustTimestampMs < 0L
                ? Math.min(detailed.flushTimestampMs(), 6000L)
                : Math.min(detailed.flushTimestampMs(), trustTimestampMs + 800L);
        System.out.println("==== recording3 preferred 650 trust window ====");
        System.out.println(String.format(
                Locale.US,
                "window=%d..%d trust=%d final=%s",
                windowStartMs,
                windowEndMs,
                trustTimestampMs,
                sanitize(detailed.probeResult().decodedText())
        ));
        System.out.println("-- raw decode events --");
        printDecodeWindow(detailed.rawDecodeEvents(), windowStartMs, windowEndMs);
        System.out.println("-- stable accepted events --");
        printDecodeWindow(detailed.stableAcceptedDecodeEvents(), windowStartMs, windowEndMs);
        System.out.println("-- committed decode events --");
        printDecodeWindow(detailed.decodeEvents(), windowStartMs, windowEndMs);
        System.out.println("-- stable decisions --");
        printStableDecisionWindow(detailed.stableDecisionTraces(), windowStartMs, windowEndMs);
    }

    @Test
    public void printRecording3OpeningWindowDiagnostics() throws Exception {
        Path recording3Wav = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(3).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (3)"));
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "recording3-opening-probe",
                        shiftFramesToZero(loadFrames(recording3Wav)),
                        700,
                        15,
                        55,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
        long windowStartMs = 800L;
        long windowEndMs = 2600L;
        System.out.println("==== recording3 opening window diagnostics ====");
        System.out.println(String.format(
                Locale.US,
                "window=%d..%d final=%s firstRaw=%d firstTrust=%d",
                windowStartMs,
                windowEndMs,
                sanitize(detailed.probeResult().decodedText()),
                firstCharacterTimestamp(detailed.rawDecodeEvents()),
                firstTrustedTrace(detailed.timingStateTraces()) == null
                        ? -1L
                        : firstTrustedTrace(detailed.timingStateTraces()).timestampMs()
        ));
        System.out.println("-- frame window --");
        printFrameSignalWindow(detailed, windowStartMs, windowEndMs);
        System.out.println("-- tone events --");
        printToneWindow(detailed.toneEvents(), windowStartMs, windowEndMs);
        System.out.println("-- timing events --");
        printTimingWindow(detailed.timingEvents(), windowStartMs, windowEndMs);
        System.out.println("-- boundary decisions --");
        printBootstrapDecisionWindow(detailed.bootstrapBoundaryDecisionTraces(), windowStartMs, windowEndMs);
        System.out.println("-- cadence decisions --");
        printBootstrapDecisionWindow(detailed.bootstrapCadenceDecisionTraces(), windowStartMs, windowEndMs);
        System.out.println("-- raw decode events --");
        printDecodeWindow(detailed.rawDecodeEvents(), windowStartMs, windowEndMs);
        System.out.println("-- stable accepted events --");
        printDecodeWindow(detailed.stableAcceptedDecodeEvents(), windowStartMs, windowEndMs);
        System.out.println("-- committed decode events --");
        printDecodeWindow(detailed.decodeEvents(), windowStartMs, windowEndMs);
        System.out.println("-- stable decisions --");
        printStableDecisionWindow(detailed.stableDecisionTraces(), windowStartMs, windowEndMs);
        System.out.println("-- timing state --");
        printTimingStateWindow(detailed.timingStateTraces(), windowStartMs, windowEndMs);
    }

    @Test
    public void printUserSpeedSweepOpeningWindowDiagnostics() {
        CwFixtureScenario scenario = findScenario("user_speed_sweep_vvv_700hz");
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        List<AudioFrame> frames = source.renderFramesForTesting(scenario);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        scenario.id(),
                        frames,
                        700,
                        scenario.wpm(),
                        55,
                        false,
                        CwInterpreter.RecoveryMode.SEMANTIC_RECOVERY
                );
        long windowStartMs = 3000L;
        long windowEndMs = 12500L;
        System.out.println("==== speed sweep opening window diagnostics ====");
        System.out.println(String.format(
                Locale.US,
                "window=%d..%d final=%s",
                windowStartMs,
                windowEndMs,
                sanitize(detailed.probeResult().decodedText())
        ));
        System.out.println("-- frame window --");
        printFrameSignalWindow(detailed, windowStartMs, windowEndMs);
        System.out.println("-- tone events --");
        printToneWindow(detailed.toneEvents(), windowStartMs, windowEndMs);
        System.out.println("-- timing events --");
        printTimingWindow(detailed.timingEvents(), windowStartMs, windowEndMs);
        System.out.println("-- timing adaptation --");
        printTimingAdaptationWindow(detailed.timingEventAdaptationTraces(), windowStartMs, windowEndMs);
        System.out.println("-- raw decode events --");
        printDecodeWindow(detailed.rawDecodeEvents(), windowStartMs, windowEndMs);
        System.out.println("-- committed decode events --");
        printDecodeWindow(detailed.decodeEvents(), windowStartMs, windowEndMs);
        System.out.println("-- stable decisions --");
        printStableDecisionWindow(detailed.stableDecisionTraces(), windowStartMs, windowEndMs);
    }

    private void printDiagnostics(String scenarioId, int preferredToneHz, int sqlPercent) {
        CwFixtureScenario scenario = findScenario(scenarioId);
        SyntheticFixtureRxAudioSource source = new SyntheticFixtureRxAudioSource();
        List<AudioFrame> frames = source.renderFramesForTesting(scenario);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        scenario.id(),
                        frames,
                        preferredToneHz,
                        scenario.wpm(),
                        sqlPercent,
                        false,
                        CwInterpreter.RecoveryMode.SEMANTIC_RECOVERY
                );
        printDetailedDiagnostics(scenario.id(), detailed, scenario.wpm(), preferredToneHz, sqlPercent);
    }

    private void printDetailedDiagnostics(
            String label,
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed,
            int scenarioWpm,
            int preferredToneHz,
            int sqlPercent
    ) {
        long firstCommittedTimestampMs = detailed.decodeEvents().isEmpty()
                ? -1L
                : detailed.decodeEvents().get(0).timestampMs();
        String firstCommittedText = detailed.decodeEvents().isEmpty()
                ? "(none)"
                : sanitize(detailed.decodeEvents().get(0).outputText());

        LocalAudioDecodeTestSupport.TimingStateTrace firstTrustedTrace = firstTrustedTrace(
                detailed.timingStateTraces()
        );
        long firstTrustedTimestampMs = firstTrustedTrace == null
                ? -1L
                : firstTrustedTrace.timestampMs();
        String firstTrustedReason = firstTrustedTrace == null || firstTrustedTrace.debugSnapshot() == null
                ? "(none)"
                : safe(firstTrustedTrace.debugSnapshot().lastTrustedUpdateReason());
        long firstTrustedDotMs = firstTrustedTrace == null || firstTrustedTrace.debugSnapshot() == null
                ? 0L
                : Math.round(firstTrustedTrace.debugSnapshot().trustedDotEstimateMs());

        LocalAudioDecodeTestSupport.TimingStateTrace finalTimingTrace = lastTrace(
                detailed.timingStateTraces()
        );

        System.out.println("==== raw commit probe: " + label + " ====");
        System.out.println(String.format(
                Locale.US,
                "scenarioWpm=%d preferredTone=%d sql=%d",
                scenarioWpm,
                preferredToneHz,
                sqlPercent
        ));
        System.out.println(String.format(
                Locale.US,
                "decoderChars=%d rawDecodeChars=%d stableAcceptedChars=%d boundaryObs=%d cadenceObs=%d committedChars=%d committedEvents=%d",
                detailed.probeResult().decoderSnapshot().totalCharacters(),
                characterCount(detailed.rawDecodeEvents()),
                characterCount(detailed.stableAcceptedDecodeEvents()),
                detailed.bootstrapBoundaryTimingEvents().size(),
                detailed.bootstrapCadenceTimingEvents().size(),
                committedCharacterCount(detailed.decodeEvents()),
                detailed.decodeEvents().size()
        ));
        System.out.println("decoderCharacterStream="
                + sanitize(detailed.probeResult().decoderSnapshot().decodedText()));
        System.out.println("interpreterRawText="
                + sanitize(detailed.probeResult().interpreterSnapshot().rawText()));
        System.out.println(String.format(
                Locale.US,
                "firstModelTrust=%dms dot=%dms reason=%s",
                firstTrustedTimestampMs,
                firstTrustedDotMs,
                firstTrustedReason
        ));
        System.out.println(String.format(
                Locale.US,
                "firstCommitted=%dms text=%s",
                firstCommittedTimestampMs,
                firstCommittedText
        ));
        System.out.println("stableRejects=" + formatCounts(detailed.stableRejectCounts()));
        System.out.println("boundaryRejects=" + formatCounts(detailed.bootstrapBoundaryRejectCounts()));
        System.out.println("cadenceRejects=" + formatCounts(detailed.bootstrapCadenceRejectCounts()));
        System.out.println("boundaryEvents:");
        printTimingEvents(detailed.bootstrapBoundaryTimingEvents(), 10);
        System.out.println("cadenceEvents:");
        printTimingEvents(detailed.bootstrapCadenceTimingEvents(), 10);
        System.out.println("wordBreakTimeline:");
        printWordBreakTimeline(detailed, 16);
        System.out.println("characterTimelineTail:");
        printCharacterTimelineTail(detailed.decodeEvents(), 20);
        System.out.println("stableDecisionTimelineTail:");
        printStableDecisionTimelineTail(detailed.stableDecisionTraces(), 20);
        System.out.println("trustReasonTransitions:");
        printTrustReasonTransitions(detailed.timingStateTraces(), 16);
        System.out.println("turnTransitions:");
        printTurnTransitions(detailed.turnTransitionTraces(), 12);
        System.out.println("turnEndActivityWindow:");
        printTurnEndActivityWindow(detailed.turnTransitionTraces(), detailed.frameSignalTraces(), 64);
        if (finalTimingTrace != null && finalTimingTrace.debugSnapshot() != null) {
            System.out.println(String.format(
                Locale.US,
                    "finalModelTrust=%dms dot=%dms reason=%s summary=%s",
                    finalTimingTrace.timestampMs(),
                    Math.round(finalTimingTrace.debugSnapshot().trustedDotEstimateMs()),
                    safe(finalTimingTrace.debugSnapshot().lastTrustedUpdateReason()),
                    safe(finalTimingTrace.debugSummary())
            ));
        }
        System.out.println("recentTimingReasons:");
        int printed = 0;
        for (int index = detailed.timingStateTraces().size() - 1; index >= 0 && printed < 8; index--) {
            LocalAudioDecodeTestSupport.TimingStateTrace trace = detailed.timingStateTraces().get(index);
            if (trace == null || trace.debugSnapshot() == null) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "  @%dms trust=%.1f rawWpm=%.2f reason=%s",
                    trace.timestampMs(),
                    trace.debugSnapshot().trustedDotEstimateMs(),
                    trace.rawSnapshot() == null ? 0.0d : trace.rawSnapshot().estimatedWpmPrecise(),
                    safe(trace.debugSnapshot().lastTrustedUpdateReason())
            ));
            printed += 1;
        }
    }

    private static List<AudioFrame> loadFrames(Path wavFile) throws Exception {
        LocalAudioDecodeTestSupport.WaveDataProbe waveData =
                LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);
        ArrayList<AudioFrame> frames = new ArrayList<>();
        long sampleOffset = 0L;
        for (int offset = 0; offset < waveData.samples().length; offset += 256) {
            int frameLength = Math.min(256, waveData.samples().length - offset);
            short[] frameSamples = new short[frameLength];
            System.arraycopy(waveData.samples(), offset, frameSamples, 0, frameLength);
            frames.add(LocalAudioDecodeTestSupport.buildFrameForProbe(
                    frameSamples,
                    waveData.sampleRateHz(),
                    sampleOffset
            ));
            sampleOffset += frameLength;
        }
        return frames;
    }

    private static List<AudioFrame> shiftFramesToZero(List<AudioFrame> frames) {
        ArrayList<AudioFrame> shifted = new ArrayList<>(frames.size());
        if (frames.isEmpty()) {
            return shifted;
        }
        long firstTimestampMs = frames.get(0).capturedAtMs();
        for (AudioFrame frame : frames) {
            shifted.add(new AudioFrame(
                    frame.samples(),
                    frame.sampleRateHz(),
                    frame.channelCount(),
                    frame.peakAmplitude(),
                    frame.rmsAmplitude(),
                    frame.clippedSampleCount(),
                    Math.max(0L, frame.capturedAtMs() - firstTimestampMs)
            ));
        }
        return shifted;
    }

    private CwFixtureScenario findScenario(String scenarioId) {
        for (CwFixtureScenario scenario : CwFixtureLibrary.scenarios()) {
            if (scenario.id().equals(scenarioId)) {
                return scenario;
            }
        }
        throw new IllegalArgumentException("Unknown scenario: " + scenarioId);
    }

    private static LocalAudioDecodeTestSupport.TimingStateTrace firstTrustedTrace(
            List<LocalAudioDecodeTestSupport.TimingStateTrace> traces
    ) {
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : traces) {
            if (trace == null
                    || trace.debugSnapshot() == null
                    || trace.debugSnapshot().trustedDotEstimateMs() <= 0.0d) {
                continue;
            }
            return trace;
        }
        return null;
    }

    private static LocalAudioDecodeTestSupport.TimingStateTrace lastTrace(
            List<LocalAudioDecodeTestSupport.TimingStateTrace> traces
    ) {
        if (traces == null || traces.isEmpty()) {
            return null;
        }
        return traces.get(traces.size() - 1);
    }

    private static int committedCharacterCount(List<org.bi9clt.cwcn.core.decoder.CwDecodeEvent> decodeEvents) {
        return characterCount(decodeEvents);
    }

    private static int characterCount(List<org.bi9clt.cwcn.core.decoder.CwDecodeEvent> decodeEvents) {
        int count = 0;
        for (org.bi9clt.cwcn.core.decoder.CwDecodeEvent decodeEvent : decodeEvents) {
            if (decodeEvent != null
                    && decodeEvent.type() == org.bi9clt.cwcn.core.decoder.CwDecodeEvent.Type.CHARACTER_DECODED) {
                count += 1;
            }
        }
        return count;
    }

    private static long firstCharacterTimestamp(List<org.bi9clt.cwcn.core.decoder.CwDecodeEvent> decodeEvents) {
        if (decodeEvents == null) {
            return -1L;
        }
        for (org.bi9clt.cwcn.core.decoder.CwDecodeEvent decodeEvent : decodeEvents) {
            if (decodeEvent != null
                    && decodeEvent.type() == org.bi9clt.cwcn.core.decoder.CwDecodeEvent.Type.CHARACTER_DECODED) {
                return decodeEvent.timestampMs();
            }
        }
        return -1L;
    }

    private static long lastCharacterTimestamp(List<org.bi9clt.cwcn.core.decoder.CwDecodeEvent> decodeEvents) {
        if (decodeEvents == null) {
            return -1L;
        }
        for (int index = decodeEvents.size() - 1; index >= 0; index--) {
            org.bi9clt.cwcn.core.decoder.CwDecodeEvent decodeEvent = decodeEvents.get(index);
            if (decodeEvent != null
                    && decodeEvent.type() == org.bi9clt.cwcn.core.decoder.CwDecodeEvent.Type.CHARACTER_DECODED) {
                return decodeEvent.timestampMs();
            }
        }
        return -1L;
    }

    private static String sanitize(String value) {
        if (value == null || value.isEmpty()) {
            return "(none)";
        }
        return value.replace('\u25A1', '?').replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String safe(String value) {
        return value == null || value.isEmpty() ? "(none)" : value;
    }

    private static String formatCounts(Map<String, Integer> counts) {
        if (counts == null || counts.isEmpty()) {
            return "{}";
        }
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        builder.append("}");
        return builder.toString();
    }

    private static void printTimingEvents(
            List<org.bi9clt.cwcn.core.timing.CwTimingEvent> timingEvents,
            int maxItems
    ) {
        if (timingEvents == null || timingEvents.isEmpty()) {
            System.out.println("  (none)");
            return;
        }
        int limit = Math.min(maxItems, timingEvents.size());
        for (int index = 0; index < limit; index++) {
            org.bi9clt.cwcn.core.timing.CwTimingEvent timingEvent = timingEvents.get(index);
            if (timingEvent == null) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "  @%dms kind=%s class=%s dur=%dms dot=%dms intra=%dms",
                    timingEvent.timestampMs(),
                    timingEvent.kind(),
                    timingEvent.classification(),
                    timingEvent.durationMs(),
                    timingEvent.dotEstimateMs(),
                    timingEvent.intraGapEstimateMs()
            ));
        }
        if (timingEvents.size() > limit) {
            System.out.println("  ... +" + (timingEvents.size() - limit) + " more");
        }
    }

    private static void printWordBreakTimeline(
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed,
            int maxItems
    ) {
        if (detailed == null || detailed.decodeEvents() == null || detailed.decodeEvents().isEmpty()) {
            System.out.println("  (none)");
            return;
        }
        int printed = 0;
        for (org.bi9clt.cwcn.core.decoder.CwDecodeEvent decodeEvent : detailed.decodeEvents()) {
            if (decodeEvent == null
                    || decodeEvent.type() != org.bi9clt.cwcn.core.decoder.CwDecodeEvent.Type.WORD_BREAK) {
                continue;
            }
            org.bi9clt.cwcn.core.timing.CwTimingEvent gapEvent =
                    findGapTimingEventAtTimestamp(detailed.timingEvents(), decodeEvent.timestampMs());
            String sourceSummary = formatWordBreakSource(gapEvent);
            System.out.println(String.format(
                    Locale.US,
                    "  @%dms text=%s %s",
                    decodeEvent.timestampMs(),
                    excerpt(decodeEvent.outputText(), 24),
                    sourceSummary
            ));
            printed += 1;
            if (printed >= maxItems) {
                break;
            }
        }
        if (printed == 0) {
            System.out.println("  (none)");
            return;
        }
        int totalWordBreaks = wordBreakCount(detailed.decodeEvents());
        if (totalWordBreaks > printed) {
            System.out.println("  ... +" + (totalWordBreaks - printed) + " more");
        }
    }

    private static org.bi9clt.cwcn.core.timing.CwTimingEvent findGapTimingEventAtTimestamp(
            List<org.bi9clt.cwcn.core.timing.CwTimingEvent> timingEvents,
            long timestampMs
    ) {
        if (timingEvents == null || timingEvents.isEmpty()) {
            return null;
        }
        for (int index = timingEvents.size() - 1; index >= 0; index--) {
            org.bi9clt.cwcn.core.timing.CwTimingEvent timingEvent = timingEvents.get(index);
            if (timingEvent == null || timingEvent.timestampMs() != timestampMs) {
                continue;
            }
            if (timingEvent.kind() == org.bi9clt.cwcn.core.timing.CwTimingEvent.Kind.GAP) {
                return timingEvent;
            }
        }
        return null;
    }

    private static String formatWordBreakSource(org.bi9clt.cwcn.core.timing.CwTimingEvent gapEvent) {
        if (gapEvent == null) {
            return "source=(timing-missing)";
        }
        double ratioToDot = gapEvent.ratioToDotEstimate();
        if (gapEvent.classification() == org.bi9clt.cwcn.core.timing.CwTimingEvent.Classification.WORD_GAP) {
            return String.format(
                    Locale.US,
                    "source=WORD_GAP dur=%dms dot=%dms ratio=%.2f",
                    gapEvent.durationMs(),
                    gapEvent.dotEstimateMs(),
                    ratioToDot
            );
        }
        if (gapEvent.classification() == org.bi9clt.cwcn.core.timing.CwTimingEvent.Classification.LETTER_GAP
                && ratioToDot >= SOFT_WORD_BREAK_GAP_RATIO_PROBE) {
            return String.format(
                    Locale.US,
                    "source=SOFT_PROMOTE dur=%dms dot=%dms ratio=%.2f",
                    gapEvent.durationMs(),
                    gapEvent.dotEstimateMs(),
                    ratioToDot
            );
        }
        return String.format(
                Locale.US,
                "source=%s dur=%dms dot=%dms ratio=%.2f",
                gapEvent.classification(),
                gapEvent.durationMs(),
                gapEvent.dotEstimateMs(),
                ratioToDot
        );
    }

    private static int wordBreakCount(List<org.bi9clt.cwcn.core.decoder.CwDecodeEvent> decodeEvents) {
        int count = 0;
        for (org.bi9clt.cwcn.core.decoder.CwDecodeEvent decodeEvent : decodeEvents) {
            if (decodeEvent != null
                    && decodeEvent.type() == org.bi9clt.cwcn.core.decoder.CwDecodeEvent.Type.WORD_BREAK) {
                count += 1;
            }
        }
        return count;
    }

    private static void printCharacterTimelineTail(
            List<org.bi9clt.cwcn.core.decoder.CwDecodeEvent> decodeEvents,
            int maxItems
    ) {
        if (decodeEvents == null || decodeEvents.isEmpty()) {
            System.out.println("  (none)");
            return;
        }
        ArrayList<org.bi9clt.cwcn.core.decoder.CwDecodeEvent> characters = new ArrayList<>();
        for (org.bi9clt.cwcn.core.decoder.CwDecodeEvent decodeEvent : decodeEvents) {
            if (decodeEvent != null
                    && decodeEvent.type() == org.bi9clt.cwcn.core.decoder.CwDecodeEvent.Type.CHARACTER_DECODED) {
                characters.add(decodeEvent);
            }
        }
        if (characters.isEmpty()) {
            System.out.println("  (none)");
            return;
        }
        int startIndex = Math.max(0, characters.size() - maxItems);
        for (int index = startIndex; index < characters.size(); index++) {
            org.bi9clt.cwcn.core.decoder.CwDecodeEvent decodeEvent = characters.get(index);
            System.out.println(String.format(
                    Locale.US,
                    "  @%dms char=%s seq=%s unknown=%s text=%s",
                    decodeEvent.timestampMs(),
                    safe(decodeEvent.emittedValue()),
                    safe(decodeEvent.sourceSequence()),
                    decodeEvent.unknownCharacter(),
                    excerpt(decodeEvent.outputText(), 24)
            ));
        }
    }

    private static void printStableDecisionTimelineTail(
            List<LocalAudioDecodeTestSupport.StableDecisionTrace> traces,
            int maxItems
    ) {
        if (traces == null || traces.isEmpty()) {
            System.out.println("  (none)");
            return;
        }
        int startIndex = Math.max(0, traces.size() - maxItems);
        for (int index = startIndex; index < traces.size(); index++) {
            LocalAudioDecodeTestSupport.StableDecisionTrace trace = traces.get(index);
            if (trace == null) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "  @%dms char=%s seq=%s decision=%s verified=%s trust=%s lock=%s lck=%.2f near=%.2f unl=%.2f dom=%.2f iso=%.2f rawWpm=%.2f unknown=%s",
                    trace.timestampMs(),
                    safe(trace.emittedValue()),
                    safe(trace.sourceSequence()),
                    safe(trace.compatibleDecision()),
                    safe(trace.verifiedDecision()),
                    trace.trustedTimingEstablished(),
                    trace.targetToneLocked(),
                    trace.recentLockedFrameRatio(),
                    trace.recentNearTargetLockedFrameRatio(),
                    trace.recentActiveUnlockedFrameRatio(),
                    trace.toneDominanceRatio(),
                    trace.narrowbandIsolationRatio(),
                    trace.rawWpm(),
                    trace.unknownCharacter()
            ));
        }
    }

    private static String excerpt(String value, int maxChars) {
        if (value == null || value.isEmpty()) {
            return "(none)";
        }
        String visible = value.replace('\u25A1', '?').replace('\n', ' ').replace('\r', ' ');
        if (visible.length() <= maxChars) {
            return visible;
        }
        return "..." + visible.substring(Math.max(0, visible.length() - maxChars));
    }

    private static void printTrustReasonTransitions(
            List<LocalAudioDecodeTestSupport.TimingStateTrace> traces,
            int maxLines
    ) {
        String lastReason = null;
        int printed = 0;
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : traces) {
            if (trace == null || trace.debugSnapshot() == null) {
                continue;
            }
            String reason = safe(trace.debugSnapshot().lastTrustedUpdateReason());
            if (reason.equals(lastReason)) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "  @%dms trust=%.1f pendingFast=%.1f/%d reason=%s obs=%s",
                    trace.timestampMs(),
                    trace.debugSnapshot().trustedDotEstimateMs(),
                    trace.debugSnapshot().pendingFastTrustedDotEstimateMs(),
                    trace.debugSnapshot().pendingFastTrustedEvidenceCount(),
                    reason,
                    safe(trace.debugSnapshot().lastObservationSummary())
            ));
            lastReason = reason;
            printed += 1;
            if (printed >= maxLines) {
                break;
            }
        }
        if (printed == 0) {
            System.out.println("  (none)");
        }
    }

    private static void printTimingWindow(
            List<CwTimingEvent> timingEvents,
            long windowStartMs,
            long windowEndMs
    ) {
        for (CwTimingEvent timingEvent : timingEvents) {
            if (timingEvent == null || timingEvent.timestampMs() < windowStartMs) {
                continue;
            }
            if (timingEvent.timestampMs() > windowEndMs) {
                break;
            }
            System.out.println(String.format(
                    Locale.US,
                    "  @%d %s/%s dur=%d dot=%d intra=%d",
                    timingEvent.timestampMs(),
                    timingEvent.kind(),
                    timingEvent.classification(),
                    timingEvent.durationMs(),
                    timingEvent.dotEstimateMs(),
                    timingEvent.intraGapEstimateMs()
            ));
        }
    }

    private static void printTimingAdaptationWindow(
            List<LocalAudioDecodeTestSupport.TimingEventAdaptationTrace> traces,
            long windowStartMs,
            long windowEndMs
    ) {
        for (LocalAudioDecodeTestSupport.TimingEventAdaptationTrace trace : traces) {
            if (trace == null || trace.timestampMs() < windowStartMs) {
                continue;
            }
            if (trace.timestampMs() > windowEndMs) {
                break;
            }
            System.out.println(String.format(
                    Locale.US,
                    "  @%d %s dur=%d raw=%s(%d/%d) wpm=%s(%d/%d) anc=%s(%d/%d) trust=%s origin=%s tdot=%d",
                    trace.timestampMs(),
                    safe(trace.eventKind()),
                    trace.durationMs(),
                    safe(trace.rawClassification()),
                    trace.rawDotEstimateMs(),
                    trace.rawIntraGapEstimateMs(),
                    safe(trace.wpmGuardClassification()),
                    trace.wpmGuardDotEstimateMs(),
                    trace.wpmGuardIntraGapEstimateMs(),
                    safe(trace.anchorClassification()),
                    trace.anchorDotEstimateMs(),
                    trace.anchorIntraGapEstimateMs(),
                    trace.trustedTimingEstablished(),
                    safe(trace.trustOrigin()),
                    trace.trustedDotEstimateMs()
            ));
        }
    }

    private static void printToneWindow(
            List<org.bi9clt.cwcn.core.signal.CwToneEvent> toneEvents,
            long windowStartMs,
            long windowEndMs
    ) {
        for (org.bi9clt.cwcn.core.signal.CwToneEvent toneEvent : toneEvents) {
            if (toneEvent == null || toneEvent.timestampMs() < windowStartMs) {
                continue;
            }
            if (toneEvent.timestampMs() > windowEndMs) {
                break;
            }
            System.out.println(String.format(
                    Locale.US,
                    "  @%d %s toneDur=%d peak=%d rms=%.1f",
                    toneEvent.timestampMs(),
                    toneEvent.type(),
                    toneEvent.toneDurationMs(),
                    toneEvent.peakAmplitude(),
                    toneEvent.rmsAmplitude()
            ));
        }
    }

    private static void printFrameSignalWindow(
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed,
            long windowStartMs,
            long windowEndMs
    ) {
        for (int index = 0; index < detailed.frameSignalTraces().size(); index++) {
            LocalAudioDecodeTestSupport.FrameSignalTrace trace = detailed.frameSignalTraces().get(index);
            if (trace == null || trace.snapshot() == null) {
                continue;
            }
            long timestampMs = trace.timestampMs();
            if (timestampMs < windowStartMs || timestampMs > windowEndMs) {
                continue;
            }
            org.bi9clt.cwcn.core.signal.CwSignalSnapshot snapshot = trace.snapshot();
            org.bi9clt.cwcn.core.signal.CwSignalSnapshot previousSnapshot = index > 0
                    ? detailed.frameSignalTraces().get(index - 1).snapshot()
                    : null;
            int deltaOn = previousSnapshot == null
                    ? snapshot.totalToneOnEvents()
                    : snapshot.totalToneOnEvents() - previousSnapshot.totalToneOnEvents();
            int deltaOff = previousSnapshot == null
                    ? snapshot.totalToneOffEvents()
                    : snapshot.totalToneOffEvents() - previousSnapshot.totalToneOffEvents();
            System.out.println(String.format(
                    Locale.US,
                    "  F@%d act=%s lock=%s mem=%s anc=%d trk=%d eff=%d tone=%.1f thr=%d/%d gap=%d/%d cont=%s/%d weak=%d onset=%s far=%s post=%s accept=%s/%s rescue=%s curRescue=%s on+%d off+%d",
                    timestampMs,
                    snapshot.toneActive(),
                    snapshot.targetToneLocked(),
                    trace.trackedToneMemoryActiveBeforeFrame(),
                    trace.attackAnchorFrequencyHzBeforeFrame(),
                    snapshot.targetToneFrequencyHz(),
                    snapshot.effectiveTrackedToneFrequencyHz(),
                    snapshot.lastToneRmsAmplitude(),
                    snapshot.currentThreshold(),
                    snapshot.releaseThreshold(),
                    trace.postReleaseGapMs(),
                    trace.postReleaseWindowMs(),
                    trace.postReleaseRescueContinuationWindowActive(),
                    trace.postReleaseRescueContinuationWindowRemainingMs(),
                    trace.postReleaseWeakContinuityRescueCount(),
                    safe(trace.toneOnDecision()),
                    safe(trace.farAttackDelayDecision()),
                    safe(trace.postReleaseSuppressionDecision()),
                    trace.toneOnAccepted(),
                    trace.toneOnAcceptedByRescue(),
                    safe(trace.postReleaseRescueDecision()),
                    trace.currentToneStartedByPostReleaseRescue(),
                    deltaOn,
                    deltaOff
            ));
        }
    }

    private static void printDecodeWindow(
            List<CwDecodeEvent> decodeEvents,
            long windowStartMs,
            long windowEndMs
    ) {
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            if (decodeEvent == null || decodeEvent.timestampMs() < windowStartMs) {
                continue;
            }
            if (decodeEvent.timestampMs() > windowEndMs) {
                break;
            }
            System.out.println(String.format(
                    Locale.US,
                    "  @%d %s emit=%s seq=%s out=%s unknown=%s",
                    decodeEvent.timestampMs(),
                    decodeEvent.type(),
                    safe(decodeEvent.emittedValue()),
                    safe(decodeEvent.sourceSequence()),
                    safe(decodeEvent.outputText()),
                    decodeEvent.unknownCharacter()
            ));
        }
    }

    private static void printStableDecisionWindow(
            List<LocalAudioDecodeTestSupport.StableDecisionTrace> traces,
            long windowStartMs,
            long windowEndMs
    ) {
        for (LocalAudioDecodeTestSupport.StableDecisionTrace trace : traces) {
            if (trace == null || trace.timestampMs() < windowStartMs) {
                continue;
            }
            if (trace.timestampMs() > windowEndMs) {
                break;
            }
            System.out.println(String.format(
                    Locale.US,
                    "  @%d char=%s seq=%s decision=%s verified=%s trust=%s lock=%s lck=%.2f near=%.2f unl=%.2f dom=%.2f iso=%.2f rawWpm=%.2f unknown=%s",
                    trace.timestampMs(),
                    safe(trace.emittedValue()),
                    safe(trace.sourceSequence()),
                    safe(trace.compatibleDecision()),
                    safe(trace.verifiedDecision()),
                    trace.trustedTimingEstablished(),
                    trace.targetToneLocked(),
                    trace.recentLockedFrameRatio(),
                    trace.recentNearTargetLockedFrameRatio(),
                    trace.recentActiveUnlockedFrameRatio(),
                    trace.toneDominanceRatio(),
                    trace.narrowbandIsolationRatio(),
                    trace.rawWpm(),
                    trace.unknownCharacter()
            ));
        }
    }

    private static void printBootstrapDecisionWindow(
            List<LocalAudioDecodeTestSupport.BootstrapDecisionTrace> traces,
            long windowStartMs,
            long windowEndMs
    ) {
        for (LocalAudioDecodeTestSupport.BootstrapDecisionTrace trace : traces) {
            if (trace == null || trace.timestampMs() < windowStartMs) {
                continue;
            }
            if (trace.timestampMs() > windowEndMs) {
                break;
            }
            System.out.println(String.format(
                    Locale.US,
                    "  @%d kind=%s class=%s dur=%d candDot=%d decision=%s trust=%s lock=%s lck=%.2f near=%.2f unl=%.2f dom=%.2f iso=%.2f rawWpm=%.2f rawDot=%d anchor=%s",
                    trace.timestampMs(),
                    safe(trace.eventKind()),
                    safe(trace.classification()),
                    trace.durationMs(),
                    trace.candidateDotEstimateMs(),
                    safe(trace.decision()),
                    trace.trustedTimingEstablished(),
                    trace.targetToneLocked(),
                    trace.recentLockedFrameRatio(),
                    trace.recentNearTargetLockedFrameRatio(),
                    trace.recentActiveUnlockedFrameRatio(),
                    trace.toneDominanceRatio(),
                    trace.narrowbandIsolationRatio(),
                    trace.rawWpm(),
                    trace.rawDotEstimateMs(),
                    safe(trace.anchorSummary())
            ));
        }
    }

    private static void printTimingStateWindow(
            List<LocalAudioDecodeTestSupport.TimingStateTrace> traces,
            long windowStartMs,
            long windowEndMs
    ) {
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : traces) {
            if (trace == null || trace.timestampMs() < windowStartMs) {
                continue;
            }
            if (trace.timestampMs() > windowEndMs) {
                break;
            }
            System.out.println(String.format(
                    Locale.US,
                    "  @%d trust=%.1f rawWpm=%.2f rawDot=%d reason=%s summary=%s",
                    trace.timestampMs(),
                    trace.debugSnapshot() == null ? 0.0d : trace.debugSnapshot().trustedDotEstimateMs(),
                    trace.rawSnapshot() == null ? 0.0d : trace.rawSnapshot().estimatedWpmPrecise(),
                    trace.rawSnapshot() == null ? 0L : trace.rawSnapshot().dotEstimateMs(),
                    trace.debugSnapshot() == null ? "(none)" : safe(trace.debugSnapshot().lastTrustedUpdateReason()),
                    safe(trace.debugSummary())
            ));
        }
    }

    private static void printTurnTransitions(
            List<LocalAudioDecodeTestSupport.TurnTransitionTrace> traces,
            int maxLines
    ) {
        if (traces == null || traces.isEmpty()) {
            System.out.println("  (none)");
            return;
        }
        int startIndex = Math.max(0, traces.size() - maxLines);
        for (int index = startIndex; index < traces.size(); index++) {
            LocalAudioDecodeTestSupport.TurnTransitionTrace trace = traces.get(index);
            if (trace == null) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "  @%dms kind=%s turn=%d seed=%d ref=%d cur=%d ret=%d reason=%s",
                    trace.timestampMs(),
                    trace.kind(),
                    trace.turnIndex(),
                    trace.turnSeedWpm(),
                    trace.referenceWpm(),
                    trace.currentTurnAnchorWpm(),
                    trace.retainedTurnAnchorWpm(),
                    safe(trace.reason())
            ));
        }
    }

    private static void printTurnEndActivityWindow(
            List<LocalAudioDecodeTestSupport.TurnTransitionTrace> transitions,
            List<LocalAudioDecodeTestSupport.FrameSignalTrace> frameSignalTraces,
            int maxLines
    ) {
        if (transitions == null || transitions.isEmpty()
                || frameSignalTraces == null || frameSignalTraces.isEmpty()) {
            System.out.println("  (none)");
            return;
        }
        LocalAudioDecodeTestSupport.TurnTransitionTrace endTrace = null;
        for (int index = transitions.size() - 1; index >= 0; index--) {
            LocalAudioDecodeTestSupport.TurnTransitionTrace candidate = transitions.get(index);
            if (candidate != null
                    && candidate.kind() == LocalAudioDecodeTestSupport.TurnTransitionTrace.Kind.END) {
                endTrace = candidate;
                break;
            }
        }
        if (endTrace == null) {
            System.out.println("  (none)");
            return;
        }
        long startMs = Math.max(0L, endTrace.timestampMs() - 1800L);
        long endMs = endTrace.timestampMs() + 400L;
        int printed = 0;
        for (LocalAudioDecodeTestSupport.FrameSignalTrace trace : frameSignalTraces) {
            if (trace == null || trace.snapshot() == null) {
                continue;
            }
            long timestampMs = trace.timestampMs();
            if (timestampMs < startMs || timestampMs > endMs) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "  @%dms act=%s tone=%s lock=%s lck=%.2f dom=%.2f iso=%.2f sql=%.2f floor=%.2f rms=%.1f thr=%d target=%d eff=%d decision=%s",
                    timestampMs,
                    RxTurnActivityDecider.isMeaningfulTurnActivity(trace.snapshot()),
                    trace.snapshot().toneActive(),
                    trace.snapshot().targetToneLocked(),
                    trace.snapshot().recentLockedFrameRatio(),
                    trace.snapshot().toneDominanceRatio(),
                    trace.snapshot().narrowbandIsolationRatio(),
                    trace.snapshot().lastToneRmsAmplitude()
                            / Math.max(1.0d, trace.snapshot().releaseThreshold()),
                    trace.snapshot().signalFloorEstimate()
                            / Math.max(1.0d, trace.snapshot().releaseThreshold()),
                    trace.snapshot().lastToneRmsAmplitude(),
                    trace.snapshot().releaseThreshold(),
                    trace.snapshot().targetToneFrequencyHz(),
                    trace.snapshot().effectiveTrackedToneFrequencyHz(),
                    safe(trace.toneOnDecision())
            ));
            printed += 1;
            if (printed >= maxLines) {
                break;
            }
        }
        if (printed == 0) {
            System.out.println("  (none)");
        }
    }
}
