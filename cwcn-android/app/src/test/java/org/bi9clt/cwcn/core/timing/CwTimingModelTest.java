package org.bi9clt.cwcn.core.timing;

import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class CwTimingModelTest {
    @Test
    public void firstLongToneBootstrapsAsDashInsteadOfInflatingDotEstimate() {
        CwTimingModel model = new CwTimingModel();

        List<CwTimingEvent> events = model.process(toneOff(180L, 180L));

        assertEquals(1, events.size());
        assertEquals(CwTimingEvent.Classification.DAH, events.get(0).classification());
        assertTrue(events.get(0).dotEstimateMs() >= 55L);
        assertTrue(events.get(0).dotEstimateMs() <= 85L);
        assertTrue(model.snapshot().estimatedWpm() >= 14);
    }

    @Test
    public void firstLetterGapAfterLeadingDashStillClassifiesAsLetterGap() {
        CwTimingModel model = new CwTimingModel();

        model.process(toneOff(180L, 180L));
        List<CwTimingEvent> gapEvents = model.process(toneOn(360L));

        assertEquals(1, gapEvents.size());
        assertEquals(CwTimingEvent.Classification.LETTER_GAP, gapEvents.get(0).classification());
        assertTrue(gapEvents.get(0).dotEstimateMs() >= 55L);
        assertTrue(gapEvents.get(0).dotEstimateMs() <= 85L);
    }

    private CwToneEvent toneOff(long timestampMs, long durationMs) {
        return new CwToneEvent(
                CwToneEvent.Type.TONE_OFF,
                timestampMs,
                16000,
                12000.0d,
                durationMs
        );
    }

    private CwToneEvent toneOn(long timestampMs) {
        return new CwToneEvent(
                CwToneEvent.Type.TONE_ON,
                timestampMs,
                16000,
                12000.0d,
                0L
        );
    }
}
