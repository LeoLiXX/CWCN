package org.bi9clt.cwcn.core.timing;

import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.junit.Test;

import java.lang.reflect.Field;
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

    @Test
    public void moderatelyCompressedWordGapStillClassifiesAsWordGap() {
        CwTimingModel model = new CwTimingModel();

        model.process(toneOff(60L, 60L));
        List<CwTimingEvent> gapEvents = model.process(toneOn(380L));

        assertEquals(1, gapEvents.size());
        assertEquals(CwTimingEvent.Classification.WORD_GAP, gapEvents.get(0).classification());
    }

    @Test
    public void longButStillLetterSizedGapRemainsLetterGap() {
        CwTimingModel model = new CwTimingModel();

        model.process(toneOff(60L, 60L));
        List<CwTimingEvent> gapEvents = model.process(toneOn(300L));

        assertEquals(1, gapEvents.size());
        assertEquals(CwTimingEvent.Classification.LETTER_GAP, gapEvents.get(0).classification());
    }

    @Test
    public void firstFastDotBootstrapsCloserToActualFastSpeed() {
        CwTimingModel model = new CwTimingModel();

        List<CwTimingEvent> events = model.process(toneOff(40L, 40L));

        assertEquals(1, events.size());
        assertEquals(CwTimingEvent.Classification.DIT, events.get(0).classification());
        assertTrue(events.get(0).dotEstimateMs() >= 42L);
        assertTrue(events.get(0).dotEstimateMs() <= 50L);
        assertTrue(model.snapshot().estimatedWpm() >= 24);
    }

    @Test
    public void repeatedFast30WpmDotsConvergeTowardHighSpeedInsteadOfStayingSlow() {
        CwTimingModel model = new CwTimingModel();

        model.process(toneOff(40L, 40L));
        model.process(toneOn(80L));
        model.process(toneOff(120L, 40L));
        model.process(toneOn(160L));
        model.process(toneOff(200L, 40L));

        CwTimingSnapshot snapshot = model.snapshot();
        assertTrue("dot=" + snapshot.dotEstimateMs(), snapshot.dotEstimateMs() <= 46L);
        assertTrue("wpm=" + snapshot.estimatedWpm(), snapshot.estimatedWpm() >= 26);
    }

    @Test
    public void singleStretchedGapDoesNotImmediatelyDragFastTimingModelBackToSlowSpeed() {
        CwTimingModel model = new CwTimingModel();

        model.process(toneOff(40L, 40L));
        model.process(toneOn(80L));
        model.process(toneOff(120L, 40L));
        model.process(toneOn(280L));
        model.process(toneOff(320L, 40L));

        CwTimingSnapshot snapshot = model.snapshot();
        assertTrue("dot=" + snapshot.dotEstimateMs(), snapshot.dotEstimateMs() <= 52L);
        assertTrue("wpm=" + snapshot.estimatedWpm(), snapshot.estimatedWpm() >= 23);
    }

    @Test
    public void fastLetterGapDoesNotGetMisclassifiedAsIntraSymbolGap() {
        CwTimingModel model = new CwTimingModel();

        model.process(toneOff(40L, 40L));
        model.process(toneOn(80L));
        model.process(toneOff(120L, 40L));
        List<CwTimingEvent> gapEvents = model.process(toneOn(230L));

        assertEquals(1, gapEvents.size());
        assertEquals(CwTimingEvent.Classification.LETTER_GAP, gapEvents.get(0).classification());
    }

    @Test
    public void compressedWordGapCanStillPromoteUsingIntraGapEstimateWhenDotRunsSlow() throws Exception {
        CwTimingModel model = new CwTimingModel();
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
        CwTimingModel model = new CwTimingModel();
        setDoubleField(model, "dotEstimateMs", 82.0d);
        setDoubleField(model, "intraGapEstimateMs", 74.0d);
        setBooleanField(model, "initialized", true);
        setLongField(model, "lastToneOffTimestampMs", 100L);

        List<CwTimingEvent> gapEvents = model.process(toneOn(380L));

        assertEquals(1, gapEvents.size());
        assertEquals(CwTimingEvent.Classification.LETTER_GAP, gapEvents.get(0).classification());
    }

    private void setBooleanField(CwTimingModel model, String name, boolean value) throws Exception {
        Field field = CwTimingModel.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(model, value);
    }

    private void setLongField(CwTimingModel model, String name, long value) throws Exception {
        Field field = CwTimingModel.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setLong(model, value);
    }

    private void setDoubleField(CwTimingModel model, String name, double value) throws Exception {
        Field field = CwTimingModel.class.getDeclaredField(name);
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
