package org.bi9clt.cwcn.core.timing;

import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class CwAdaptiveTimingModelTest {
    @Test
    public void firstFastDotBootstrapsAggressivelyTowardFastSpeed() {
        CwAdaptiveTimingModel model = new CwAdaptiveTimingModel();

        List<CwTimingEvent> events = model.process(toneOff(40L, 40L));

        assertEquals(1, events.size());
        assertEquals(CwTimingEvent.Classification.DIT, events.get(0).classification());
        assertTrue(events.get(0).dotEstimateMs() >= 36L);
        assertTrue(events.get(0).dotEstimateMs() <= 44L);
        assertTrue(model.snapshot().estimatedWpm() >= 27);
    }

    @Test
    public void repeatedFast30WpmDotsStayInHighSpeedRange() {
        CwAdaptiveTimingModel model = new CwAdaptiveTimingModel();

        model.process(toneOff(40L, 40L));
        model.process(toneOn(80L));
        model.process(toneOff(120L, 40L));
        model.process(toneOn(160L));
        model.process(toneOff(200L, 40L));

        CwTimingSnapshot snapshot = model.snapshot();
        assertTrue("dot=" + snapshot.dotEstimateMs(), snapshot.dotEstimateMs() <= 42L);
        assertTrue("wpm=" + snapshot.estimatedWpm(), snapshot.estimatedWpm() >= 28);
    }

    @Test
    public void leadingDashStillBootstrapsIntoPlausibleMidSpeedRange() {
        CwAdaptiveTimingModel model = new CwAdaptiveTimingModel();

        List<CwTimingEvent> events = model.process(toneOff(180L, 180L));

        assertEquals(1, events.size());
        assertEquals(CwTimingEvent.Classification.DAH, events.get(0).classification());
        assertTrue(events.get(0).dotEstimateMs() >= 55L);
        assertTrue(events.get(0).dotEstimateMs() <= 70L);
    }

    @Test
    public void compressedWordGapCanStillPromoteUsingIntraGapEstimateWhenDotRunsSlow() throws Exception {
        CwAdaptiveTimingModel model = new CwAdaptiveTimingModel();
        setDoubleField(model, "dotEstimateMs", 90.0d);
        setDoubleField(model, "intraGapEstimateMs", 60.0d);
        setBooleanField(model, "initialized", true);
        setLongField(model, "lastToneOffTimestampMs", 100L);

        List<CwTimingEvent> gapEvents = model.process(toneOn(460L));

        assertEquals(1, gapEvents.size());
        assertEquals(CwTimingEvent.Classification.WORD_GAP, gapEvents.get(0).classification());
    }

    @Test
    public void stretchedLetterGapDoesNotPromoteToWordGapWithoutStrongIntraGapEvidence() throws Exception {
        CwAdaptiveTimingModel model = new CwAdaptiveTimingModel();
        setDoubleField(model, "dotEstimateMs", 82.0d);
        setDoubleField(model, "intraGapEstimateMs", 74.0d);
        setBooleanField(model, "initialized", true);
        setLongField(model, "lastToneOffTimestampMs", 100L);

        List<CwTimingEvent> gapEvents = model.process(toneOn(380L));

        assertEquals(1, gapEvents.size());
        assertEquals(CwTimingEvent.Classification.LETTER_GAP, gapEvents.get(0).classification());
    }

    private void setBooleanField(CwAdaptiveTimingModel model, String name, boolean value) throws Exception {
        Field field = CwAdaptiveTimingModel.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(model, value);
    }

    private void setLongField(CwAdaptiveTimingModel model, String name, long value) throws Exception {
        Field field = CwAdaptiveTimingModel.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setLong(model, value);
    }

    private void setDoubleField(CwAdaptiveTimingModel model, String name, double value) throws Exception {
        Field field = CwAdaptiveTimingModel.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setDouble(model, value);
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
