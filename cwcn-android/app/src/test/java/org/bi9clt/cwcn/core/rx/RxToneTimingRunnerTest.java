package org.bi9clt.cwcn.core.rx;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.decoder.CwDecoder;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class RxToneTimingRunnerTest {
    @Test
    public void dispatchToneEventsRoutesProducerObserverRelayAndDecodeConsumer() {
        RxToneTimingRunner runner = new RxToneTimingRunner(
                new RxTimingDecodeRunner(new CwDecoder())
        );
        CwToneEvent toneEvent = tone(CwToneEvent.Type.TONE_OFF, 280L);
        ArrayList<CwToneEvent> observedToneEvents = new ArrayList<>();
        ArrayList<List<CwTimingEvent>> observedTimingBatches = new ArrayList<>();
        ArrayList<CwTimingEvent> relayedTimingEvents = new ArrayList<>();
        ArrayList<String> decodedValues = new ArrayList<>();

        runner.dispatchToneEvents(
                Collections.singletonList(toneEvent),
                event -> Arrays.asList(
                        timing(CwTimingEvent.Kind.TONE, CwTimingEvent.Classification.DIT, 100L, 60L),
                        timing(CwTimingEvent.Kind.GAP, CwTimingEvent.Classification.LETTER_GAP, 280L, 180L)
                ),
                (observedToneEvent, timingEvents) -> {
                    observedToneEvents.add(observedToneEvent);
                    observedTimingBatches.add(new ArrayList<>(timingEvents));
                },
                timingEvent -> {
                    relayedTimingEvents.add(timingEvent);
                    return timingEvent;
                },
                decodeEvent -> {
                    if (decodeEvent.type() == CwDecodeEvent.Type.CHARACTER_DECODED) {
                        decodedValues.add(decodeEvent.emittedValue());
                    }
                }
        );

        assertEquals(Collections.singletonList(toneEvent), observedToneEvents);
        assertEquals(1, observedTimingBatches.size());
        assertEquals(2, observedTimingBatches.get(0).size());
        assertEquals(2, relayedTimingEvents.size());
        assertEquals(Collections.singletonList("E"), decodedValues);
    }

    @Test
    public void dispatchToneEventsNormalizesNullTimingBatchForObserver() {
        RxToneTimingRunner runner = new RxToneTimingRunner(
                new RxTimingDecodeRunner(new CwDecoder())
        );
        ArrayList<List<CwTimingEvent>> observedTimingBatches = new ArrayList<>();
        ArrayList<String> decodedValues = new ArrayList<>();

        runner.dispatchToneEvents(
                Arrays.asList(
                        null,
                        tone(CwToneEvent.Type.TONE_ON, 100L)
                ),
                toneEvent -> null,
                (toneEvent, timingEvents) -> observedTimingBatches.add(timingEvents),
                null,
                decodeEvent -> {
                    if (decodeEvent.type() == CwDecodeEvent.Type.CHARACTER_DECODED) {
                        decodedValues.add(decodeEvent.emittedValue());
                    }
                }
        );

        assertEquals(1, observedTimingBatches.size());
        assertTrue(observedTimingBatches.get(0).isEmpty());
        assertTrue(decodedValues.isEmpty());
    }

    private static CwToneEvent tone(CwToneEvent.Type type, long timestampMs) {
        return new CwToneEvent(type, timestampMs, 120, 0.5d, 60L);
    }

    private static CwTimingEvent timing(
            CwTimingEvent.Kind kind,
            CwTimingEvent.Classification classification,
            long timestampMs,
            long durationMs
    ) {
        return new CwTimingEvent(kind, classification, timestampMs, durationMs, 60L, 60L);
    }
}
