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

public final class CwFixtureUnknownCharacterTraceProbeTest {
    @Test
    public void traceHumanHesitationGapFirstUnknownCharacter() {
        traceFirstUnknownCharacter("human_hesitation_gap_report_exchange");
    }

    private void traceFirstUnknownCharacter(String scenarioId) {
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

        CwDecodeEvent unknownEvent = firstUnknownCharacter(detailed.rawDecodeEvents());
        if (unknownEvent == null) {
            System.out.println("no unknown character in " + scenarioId);
            return;
        }

        List<CwTimingEvent> characterTimingEvents = collectCharacterTimingEvents(
                detailed.timingEvents(),
                unknownEvent
        );

        System.out.println("==== unknown character trace: " + scenarioId + " ====");
        System.out.println("expectedRaw=" + scenario.expectedRawText());
        System.out.println("actualRaw=" + sanitize(detailed.probeResult().decodedText()));
        System.out.println(String.format(
                Locale.US,
                "unknown @%d emitted=%s source=%s text=%s",
                unknownEvent.timestampMs(),
                unknownEvent.emittedValue(),
                unknownEvent.sourceSequence(),
                sanitize(unknownEvent.outputText())
        ));
        for (CwTimingEvent timingEvent : characterTimingEvents) {
            System.out.println(String.format(
                    Locale.US,
                    "  %s %s @%d dur=%d dot=%d intra=%d rDot=%.2f rIntra=%.2f",
                    timingEvent.kind(),
                    timingEvent.classification(),
                    timingEvent.timestampMs(),
                    timingEvent.durationMs(),
                    timingEvent.dotEstimateMs(),
                    timingEvent.intraGapEstimateMs(),
                    timingEvent.ratioToDotEstimate(),
                    timingEvent.ratioToIntraGapEstimate()
            ));
        }
    }

    private CwDecodeEvent firstUnknownCharacter(List<CwDecodeEvent> decodeEvents) {
        if (decodeEvents == null) {
            return null;
        }
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            if (decodeEvent != null && decodeEvent.unknownCharacter()) {
                return decodeEvent;
            }
        }
        return null;
    }

    private List<CwTimingEvent> collectCharacterTimingEvents(
            List<CwTimingEvent> timingEvents,
            CwDecodeEvent characterEvent
    ) {
        ArrayList<CwTimingEvent> reversed = new ArrayList<>();
        if (timingEvents == null || characterEvent == null) {
            return reversed;
        }
        int requiredTones = characterEvent.sourceSequence() == null ? 0 : characterEvent.sourceSequence().length();
        if (requiredTones <= 0) {
            return reversed;
        }

        int boundaryIndex = -1;
        for (int index = 0; index < timingEvents.size(); index++) {
            CwTimingEvent timingEvent = timingEvents.get(index);
            if (timingEvent.kind() == CwTimingEvent.Kind.GAP
                    && timingEvent.timestampMs() == characterEvent.timestampMs()
                    && timingEvent.classification() != CwTimingEvent.Classification.INTRA_SYMBOL_GAP) {
                boundaryIndex = index;
                break;
            }
        }
        int scanIndex = boundaryIndex >= 0 ? boundaryIndex - 1 : timingEvents.size() - 1;
        int collectedTones = 0;
        while (scanIndex >= 0 && collectedTones < requiredTones) {
            CwTimingEvent timingEvent = timingEvents.get(scanIndex);
            reversed.add(timingEvent);
            if (timingEvent.kind() == CwTimingEvent.Kind.TONE) {
                collectedTones += 1;
            }
            scanIndex -= 1;
        }
        ArrayList<CwTimingEvent> ordered = new ArrayList<>(reversed.size());
        for (int index = reversed.size() - 1; index >= 0; index--) {
            ordered.add(reversed.get(index));
        }
        return ordered;
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
        return text == null ? "" : text.replace('\u25A1', '?');
    }
}
