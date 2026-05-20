package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.eval.CwFixtureLibrary;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CwFixtureRawBoundaryTraceProbeTest {
    @Test
    public void traceNearFrequencyNarrowbandNoiseRawBoundaries() {
        traceScenario("near_frequency_narrowband_noise_report");
    }

    @Test
    public void traceHumanSplitShortTokensRawBoundaries() {
        traceScenario("human_split_short_tokens_report_exchange");
    }

    @Test
    public void traceHumanHesitationClarificationRawBoundaries() {
        traceScenario("human_hesitation_clarification_flow");
    }

    @Test
    public void traceHumanCompactAckClosingRawBoundaries() {
        traceScenario("human_compact_ack_closing_chain");
    }

    @Test
    public void traceFullyGluedAckClosingRawBoundaries() {
        traceScenario("fully_glued_ack_closing_chain");
    }

    @Test
    public void traceHumanRememberedUncertainAddressedMiddleClosingRawBoundaries() {
        traceScenario("human_remembered_uncertain_addressed_middle_closing");
    }

    @Test
    public void traceHumanCompactFollowupRawBoundaries() {
        traceScenario("human_compact_report_tail_followup");
    }

    @Test
    public void traceHumanHesitationAddressedDigitSplitRawBoundaries() {
        traceScenario("human_hesitation_addressed_digit_split_exchange");
    }

    @Test
    public void traceHumanHesitationCallsignRawBoundaries() {
        traceScenario("human_hesitation_callsign_report_exchange");
    }

    @Test
    public void traceHumanHesitationGapRawBoundaries() {
        traceScenario("human_hesitation_gap_report_exchange");
    }

    private void traceScenario(String scenarioId) {
        CwFixtureScenario scenario = findScenario(scenarioId);
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

        List<CwTimingEvent> timingEvents = detailed.timingEvents();
        List<CwDecodeEvent> decodeEvents = detailed.rawDecodeEvents();
        List<LocalAudioDecodeTestSupport.TimingEventAdaptationTrace> adaptationTraces =
                detailed.timingEventAdaptationTraces();
        System.out.println("==== raw boundary trace: " + scenarioId + " ====");
        System.out.println("expectedRaw=" + scenario.expectedRawText());
        System.out.println("actualRaw=" + sanitize(detailed.probeResult().decodedText()));
        System.out.println("timingDot=" + detailed.probeResult().timingSnapshot().dotEstimateMs()
                + " intra=" + detailed.probeResult().timingSnapshot().intraGapEstimateMs()
                + " wpm=" + detailed.probeResult().timingSnapshot().estimatedWpmPrecise());

        for (CwTimingEvent timingEvent : timingEvents) {
            if (timingEvent == null
                    || timingEvent.kind() != CwTimingEvent.Kind.GAP
                    || (timingEvent.classification() != CwTimingEvent.Classification.LETTER_GAP
                    && timingEvent.classification() != CwTimingEvent.Classification.WORD_GAP
                    && timingEvent.classification() != CwTimingEvent.Classification.UNKNOWN)) {
                continue;
            }
            List<CwDecodeEvent> emitted = decodeEventsAtTimestamp(decodeEvents, timingEvent.timestampMs());
            LocalAudioDecodeTestSupport.TimingEventAdaptationTrace adaptationTrace =
                    adaptationTraceAtTimestamp(adaptationTraces, timingEvent.timestampMs(), timingEvent.durationMs());
            System.out.println(String.format(
                    Locale.US,
                    "gap @%d %s dur=%d dot=%d intra=%d rDot=%.2f rIntra=%.2f adapt=%s -> %s",
                    timingEvent.timestampMs(),
                    timingEvent.classification(),
                    timingEvent.durationMs(),
                    timingEvent.dotEstimateMs(),
                    timingEvent.intraGapEstimateMs(),
                    timingEvent.ratioToDotEstimate(),
                    timingEvent.ratioToIntraGapEstimate(),
                    renderAdaptationTrace(adaptationTrace),
                    renderDecodeEvents(emitted)
            ));
        }
    }

    private List<CwDecodeEvent> decodeEventsAtTimestamp(List<CwDecodeEvent> decodeEvents, long timestampMs) {
        ArrayList<CwDecodeEvent> emitted = new ArrayList<>();
        if (decodeEvents == null) {
            return emitted;
        }
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            if (decodeEvent == null || decodeEvent.timestampMs() != timestampMs) {
                continue;
            }
            emitted.add(decodeEvent);
        }
        return emitted;
    }

    private LocalAudioDecodeTestSupport.TimingEventAdaptationTrace adaptationTraceAtTimestamp(
            List<LocalAudioDecodeTestSupport.TimingEventAdaptationTrace> traces,
            long timestampMs,
            long durationMs
    ) {
        if (traces == null) {
            return null;
        }
        for (LocalAudioDecodeTestSupport.TimingEventAdaptationTrace trace : traces) {
            if (trace == null
                    || trace.timestampMs() != timestampMs
                    || trace.durationMs() != durationMs) {
                continue;
            }
            if (!"GAP".equals(trace.eventKind())) {
                continue;
            }
            return trace;
        }
        return null;
    }

    private String renderAdaptationTrace(LocalAudioDecodeTestSupport.TimingEventAdaptationTrace trace) {
        if (trace == null) {
            return "-";
        }
        return String.format(
                Locale.US,
                "%s/%d -> %s/%d -> %s/%d trust=%s/%s",
                trace.rawClassification(),
                trace.rawDotEstimateMs(),
                trace.wpmGuardClassification(),
                trace.wpmGuardDotEstimateMs(),
                trace.anchorClassification(),
                trace.anchorDotEstimateMs(),
                trace.trustedTimingEstablished() ? "yes" : "no",
                trace.trustOrigin()
        );
    }

    private String renderDecodeEvents(List<CwDecodeEvent> decodeEvents) {
        if (decodeEvents == null || decodeEvents.isEmpty()) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < decodeEvents.size(); index++) {
            if (index > 0) {
                builder.append(" | ");
            }
            CwDecodeEvent decodeEvent = decodeEvents.get(index);
            builder.append(decodeEvent.type())
                    .append(':')
                    .append(safe(decodeEvent.emittedValue()))
                    .append(" text=")
                    .append(sanitize(decodeEvent.outputText()));
        }
        return builder.toString();
    }

    private CwFixtureScenario findScenario(String scenarioId) {
        for (CwFixtureScenario scenario : CwFixtureLibrary.scenarios()) {
            if (scenario.id().equals(scenarioId)) {
                return scenario;
            }
        }
        throw new IllegalArgumentException("Unknown scenario: " + scenarioId);
    }

    private static String sanitize(String text) {
        return safe(text).replace('\u25A1', '?');
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }
}
