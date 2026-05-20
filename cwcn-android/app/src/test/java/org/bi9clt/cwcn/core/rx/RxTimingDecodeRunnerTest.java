package org.bi9clt.cwcn.core.rx;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.decoder.CwDecoder;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class RxTimingDecodeRunnerTest {
    @Test
    public void dispatchTimingEventsRelaysIntoDecoderAndEmitsDecodedCharacter() {
        RxTimingDecodeRunner runner = new RxTimingDecodeRunner(new CwDecoder());
        ArrayList<CwTimingEvent> relayedEvents = new ArrayList<>();
        ArrayList<String> decodedValues = new ArrayList<>();

        runner.dispatchTimingEvents(
                Arrays.asList(
                        tone(CwTimingEvent.Classification.DIT, 100L, 60L, 60L, 60L),
                        gap(CwTimingEvent.Classification.LETTER_GAP, 280L, 180L, 60L, 60L)
                ),
                timingEvent -> {
                    relayedEvents.add(timingEvent);
                    return timingEvent;
                },
                decodeEvent -> {
                    if (decodeEvent.type() == CwDecodeEvent.Type.CHARACTER_DECODED) {
                        decodedValues.add(decodeEvent.emittedValue());
                    }
                }
        );

        assertEquals(2, relayedEvents.size());
        assertEquals(Arrays.asList("E"), decodedValues);
    }

    @Test
    public void flushPendingCharacterUsesSharedConsumerPath() {
        RxTimingDecodeRunner runner = new RxTimingDecodeRunner(new CwDecoder());
        ArrayList<String> decodedValues = new ArrayList<>();

        runner.dispatchTimingEvents(
                List.of(tone(CwTimingEvent.Classification.DIT, 100L, 60L, 60L, 60L)),
                null,
                decodeEvent -> {
                    if (decodeEvent.type() == CwDecodeEvent.Type.CHARACTER_DECODED) {
                        decodedValues.add(decodeEvent.emittedValue());
                    }
                }
        );

        assertTrue(runner.hasPendingCharacter());

        runner.flushPendingCharacter(
                220L,
                decodeEvent -> {
                    if (decodeEvent.type() == CwDecodeEvent.Type.CHARACTER_DECODED) {
                        decodedValues.add(decodeEvent.emittedValue());
                    }
                }
        );

        assertFalse(runner.hasPendingCharacter());
        assertEquals(Arrays.asList("E"), decodedValues);
    }

    private static CwTimingEvent tone(
            CwTimingEvent.Classification classification,
            long timestampMs,
            long durationMs,
            long dotEstimateMs,
            long intraGapEstimateMs
    ) {
        return new CwTimingEvent(
                CwTimingEvent.Kind.TONE,
                classification,
                timestampMs,
                durationMs,
                dotEstimateMs,
                intraGapEstimateMs
        );
    }

    private static CwTimingEvent gap(
            CwTimingEvent.Classification classification,
            long timestampMs,
            long durationMs,
            long dotEstimateMs,
            long intraGapEstimateMs
    ) {
        return new CwTimingEvent(
                CwTimingEvent.Kind.GAP,
                classification,
                timestampMs,
                durationMs,
                dotEstimateMs,
                intraGapEstimateMs
        );
    }
}
