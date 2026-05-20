package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.decoder.CwDecoder;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.qso.QsoStateMachine;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertTrue;

public final class CwRecording3FrequencyCompetitionProbeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;
    private static final int[] PROFILE_FREQUENCIES_HZ = new int[]{500, 580, 620, 650, 670, 700, 730, 770};
    private static final int[] FIXED_SWEEP_FREQUENCIES_HZ = new int[]{580, 620, 640, 650, 660, 680, 700, 720, 740, 760, 770};
    private static final String EXPECTED_TEXT =
            "BI9CMS BI9CMS BI9CMS DE BI9CLT BI8DLT BI9CLT UR 599 5NN BK.";

    @Test
    public void printRecording3EarlyAcquisitionProfiles() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(3).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (3)"));
        LocalAudioDecodeTestSupport.WaveDataProbe waveData = LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);

        CwSignalProcessor processor = new CwSignalProcessor();
        processor.setPreferredToneFrequencyHz(PREFERRED_TONE_HZ);

        StringBuilder trace = new StringBuilder();
        long sampleOffset = 0L;
        for (int offset = 0, frameIndex = 0; offset < waveData.samples().length; offset += 256, frameIndex++) {
            int frameLength = Math.min(256, waveData.samples().length - offset);
            short[] frameSamples = new short[frameLength];
            System.arraycopy(waveData.samples(), offset, frameSamples, 0, frameLength);
            AudioFrame frame = LocalAudioDecodeTestSupport.buildFrameForProbe(
                    frameSamples,
                    waveData.sampleRateHz(),
                    sampleOffset
            );
            if (frameIndex < 12 || (frameIndex < 72 && frameIndex % 6 == 0)) {
                CwSignalSnapshot before = processor.snapshot();
                trace.append("---- F").append(frameIndex)
                        .append(" @").append(frame.capturedAtMs()).append("ms")
                        .append(" peak=").append(frame.peakAmplitude())
                        .append(" rms=").append(String.format(Locale.US, "%.1f", frame.rmsAmplitude()))
                        .append('\n')
                        .append("before target=").append(before.targetToneFrequencyHz())
                        .append(" eff=").append(before.effectiveTrackedToneFrequencyHz())
                        .append(" lock=").append(before.targetToneLocked())
                        .append(" aq=").append(before.acquisitionWinnerFrequencyHz()).append('/').append(before.acquisitionWinnerSource())
                        .append(" final=").append(before.finalAdoptedFrequencyHz()).append('/').append(before.finalAdoptedSource())
                        .append(" hyp=").append(before.toneHypothesisFrequencyHz())
                        .append(" conf=").append(String.format(Locale.US, "%.2f", before.toneHypothesisConfidence()))
                        .append(" sup=").append(before.toneHypothesisSupportFrames())
                        .append('\n')
                        .append(processor.debugAcquisitionProfile(frame, PROFILE_FREQUENCIES_HZ))
                        .append('\n');
            }
            List<CwToneEvent> toneEvents = processor.process(frame);
            if (!toneEvents.isEmpty() && frameIndex < 96) {
                trace.append("events F").append(frameIndex).append(": ").append(renderEvents(toneEvents)).append('\n');
            }
            sampleOffset += frameLength;
        }
        trace.append(processor.debugActiveLeaderSummary()).append('\n');

        System.out.println("==== recording3 early acquisition profiles ====");
        System.out.println(trace);
        assertTrue(true);
    }

    @Test
    public void printRecording3FixedFrequencyForcedSweep() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(3).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (3)"));

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult baseline =
                LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                        wavFile,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        System.out.println("==== recording3 fixed forced sweep ====");
        System.out.println("baseline=" + sanitize(baseline.probeResult().decodedText())
                + " tone=" + baseline.probeResult().signalSnapshot().targetToneFrequencyHz()
                + "/" + baseline.probeResult().signalSnapshot().effectiveAcquisitionWinnerFrequencyHz()
                + "/" + baseline.probeResult().signalSnapshot().effectiveFinalAdoptedFrequencyHz());

        for (int frequencyHz : FIXED_SWEEP_FREQUENCIES_HZ) {
            ForcedSweepResult result = replayFixedFrequency(baseline, frequencyHz);
            String canonical = canonicalize(result.decodedText);
            double recall = charRecall(EXPECTED_TEXT, result.decodedText);
            System.out.println(String.format(
                    Locale.US,
                    "fix=%d recall=%.4f chars=%d tone=%d timing=%d decode=%d bi9cms=%d bi9clt=%d ur=%d rst=%d bk=%d text=%s",
                    frequencyHz,
                    recall,
                    result.decoder.snapshot().totalCharacters(),
                    result.toneEvents.size(),
                    result.timingEvents.size(),
                    result.decodeEvents.size(),
                    countOccurrences(canonical, "BI9CMS"),
                    countOccurrences(canonical, "BI9CLT"),
                    countOccurrences(canonical, "UR"),
                    countOccurrences(canonical, "RST"),
                    countOccurrences(canonical, "BK"),
                    sanitize(result.decodedText)
            ));
        }

        assertTrue(true);
    }

    private static ForcedSweepResult replayFixedFrequency(
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed,
            int forcedToneFrequencyHz
    ) {
        CwSignalProcessor signalProcessor = new CwSignalProcessor();
        signalProcessor.setPreferredToneFrequencyHz(forcedToneFrequencyHz);
        CwHybridTimingModel timingModel = new CwHybridTimingModel();
        CwDecoder decoder = new CwDecoder();
        CwInterpreter interpreter = new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);
        QsoStateMachine qsoStateMachine = new QsoStateMachine();
        ArrayList<CwToneEvent> replayedToneEvents = new ArrayList<>();
        ArrayList<CwTimingEvent> replayedTimingEvents = new ArrayList<>();
        ArrayList<CwDecodeEvent> replayedDecodeEvents = new ArrayList<>();

        for (AudioFrame frame : detailed.frames()) {
            List<CwToneEvent> toneEvents = signalProcessor.processForcedToneForTesting(frame, forcedToneFrequencyHz);
            replayedToneEvents.addAll(toneEvents);
            drainToneEvents(
                    toneEvents,
                    timingModel,
                    decoder,
                    interpreter,
                    qsoStateMachine,
                    replayedTimingEvents,
                    replayedDecodeEvents
            );
        }

        List<CwTimingEvent> flushedTimingEvents = timingModel.flushPendingGap(detailed.flushTimestampMs());
        replayedTimingEvents.addAll(flushedTimingEvents);
        drainTimingEvents(flushedTimingEvents, decoder, interpreter, qsoStateMachine, replayedDecodeEvents);
        drainDecodeEvents(
                decoder.flushPendingCharacter(detailed.flushTimestampMs()),
                interpreter,
                qsoStateMachine,
                replayedDecodeEvents
        );

        return new ForcedSweepResult(
                sanitize(decoder.snapshot().decodedText()),
                replayedToneEvents,
                replayedTimingEvents,
                replayedDecodeEvents,
                decoder
        );
    }

    private static void drainToneEvents(
            List<CwToneEvent> toneEvents,
            CwHybridTimingModel timingModel,
            CwDecoder decoder,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            List<CwTimingEvent> replayedTimingEvents,
            List<CwDecodeEvent> replayedDecodeEvents
    ) {
        for (CwToneEvent toneEvent : toneEvents) {
            List<CwTimingEvent> timingEvents = timingModel.process(toneEvent);
            replayedTimingEvents.addAll(timingEvents);
            drainTimingEvents(timingEvents, decoder, interpreter, qsoStateMachine, replayedDecodeEvents);
        }
    }

    private static void drainTimingEvents(
            List<CwTimingEvent> timingEvents,
            CwDecoder decoder,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            List<CwDecodeEvent> replayedDecodeEvents
    ) {
        for (CwTimingEvent timingEvent : timingEvents) {
            List<CwDecodeEvent> decodeEvents = decoder.process(timingEvent);
            drainDecodeEvents(decodeEvents, interpreter, qsoStateMachine, replayedDecodeEvents);
        }
    }

    private static void drainDecodeEvents(
            List<CwDecodeEvent> decodeEvents,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            List<CwDecodeEvent> replayedDecodeEvents
    ) {
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            replayedDecodeEvents.add(decodeEvent);
            interpreter.process(decodeEvent);
            qsoStateMachine.process(interpreter.snapshot(), decodeEvent.timestampMs());
        }
    }

    private static String renderEvents(List<CwToneEvent> events) {
        if (events == null || events.isEmpty()) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < events.size(); index++) {
            if (index > 0) {
                builder.append(" | ");
            }
            CwToneEvent event = events.get(index);
            builder.append(event.type())
                    .append('@')
                    .append(event.timestampMs())
                    .append('/')
                    .append(event.toneDurationMs());
        }
        return builder.toString();
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "(empty)";
        }
        String normalized = text.replace('\u25A1', '?').replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.isEmpty() ? "(empty)" : normalized;
    }

    private static String canonicalize(String text) {
        if (text == null) {
            return "";
        }
        String upper = text.toUpperCase(Locale.US).replace('\u25A1', '?');
        StringBuilder builder = new StringBuilder(upper.length());
        for (int index = 0; index < upper.length(); index++) {
            char ch = upper.charAt(index);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '?') {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static double charRecall(String expectedText, String actualText) {
        String expected = canonicalize(expectedText);
        String actual = canonicalize(actualText);
        if (expected.isEmpty()) {
            return actual.isEmpty() ? 1.0d : 0.0d;
        }
        int lcs = longestCommonSubsequenceLength(expected, actual);
        return lcs / (double) expected.length();
    }

    private static int longestCommonSubsequenceLength(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
            char leftChar = left.charAt(leftIndex - 1);
            for (int rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                if (leftChar == right.charAt(rightIndex - 1)) {
                    current[rightIndex] = previous[rightIndex - 1] + 1;
                } else {
                    current[rightIndex] = Math.max(previous[rightIndex], current[rightIndex - 1]);
                }
            }
            int[] swap = previous;
            previous = current;
            current = swap;
            java.util.Arrays.fill(current, 0);
        }
        return previous[right.length()];
    }

    private static int countOccurrences(String text, String fragment) {
        if (text == null || text.isEmpty() || fragment == null || fragment.isEmpty()) {
            return 0;
        }
        int count = 0;
        int offset = 0;
        while (offset <= text.length() - fragment.length()) {
            int index = text.indexOf(fragment, offset);
            if (index < 0) {
                break;
            }
            count += 1;
            offset = index + fragment.length();
        }
        return count;
    }

    private static final class ForcedSweepResult {
        private final String decodedText;
        private final List<CwToneEvent> toneEvents;
        private final List<CwTimingEvent> timingEvents;
        private final List<CwDecodeEvent> decodeEvents;
        private final CwDecoder decoder;

        private ForcedSweepResult(
                String decodedText,
                List<CwToneEvent> toneEvents,
                List<CwTimingEvent> timingEvents,
                List<CwDecodeEvent> decodeEvents,
                CwDecoder decoder
        ) {
            this.decodedText = decodedText;
            this.toneEvents = toneEvents;
            this.timingEvents = timingEvents;
            this.decodeEvents = decodeEvents;
            this.decoder = decoder;
        }
    }
}
