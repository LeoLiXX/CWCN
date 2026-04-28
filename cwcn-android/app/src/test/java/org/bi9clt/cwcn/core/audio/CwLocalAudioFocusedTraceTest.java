package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.decoder.CwDecoder;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.qso.QsoStateMachine;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;

public final class CwLocalAudioFocusedTraceTest {
    @Test
    public void printFocusedTraceForRecording2() throws Exception {
        printTraceForSuffix("(2).wav");
    }

    @Test
    public void printFocusedTraceForRecording9() throws Exception {
        printTraceForSuffix("(9).wav");
    }

    @Test
    public void printFocusedTraceForRecording13() throws Exception {
        printTraceForSuffix("(13).wav");
    }

    private void printTraceForSuffix(String fileNameSuffix) throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith(fileNameSuffix))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture with suffix " + fileNameSuffix));
        LocalAudioDecodeTestSupport.WaveDataProbe waveData = LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);

        CwSignalProcessor signalProcessor = new CwSignalProcessor();
        CwHybridTimingModel timingModel = new CwHybridTimingModel();
        CwDecoder decoder = new CwDecoder();
        CwInterpreter interpreter = new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);
        QsoStateMachine qsoStateMachine = new QsoStateMachine();

        StringBuilder trace = new StringBuilder();
        long sampleOffset = 0L;
        for (int offset = 0; offset < waveData.samples().length; offset += 256) {
            int frameLength = Math.min(256, waveData.samples().length - offset);
            short[] frameSamples = new short[frameLength];
            System.arraycopy(waveData.samples(), offset, frameSamples, 0, frameLength);
            AudioFrame frame = LocalAudioDecodeTestSupport.buildFrameForProbe(
                    frameSamples,
                    waveData.sampleRateHz(),
                    sampleOffset
            );
            List<CwToneEvent> toneEvents = signalProcessor.process(frame);
            for (CwToneEvent toneEvent : toneEvents) {
                trace.append("TONE ")
                        .append(toneEvent.type())
                        .append(" @").append(toneEvent.timestampMs())
                        .append(" dur=").append(toneEvent.toneDurationMs())
                        .append('\n');
                List<CwTimingEvent> timingEvents = timingModel.process(toneEvent);
                for (CwTimingEvent timingEvent : timingEvents) {
                    trace.append("  TIMING ")
                            .append(timingEvent.kind())
                            .append(' ')
                            .append(timingEvent.classification())
                            .append(" @").append(timingEvent.timestampMs())
                            .append(" dur=").append(timingEvent.durationMs())
                            .append(" dot=").append(timingEvent.dotEstimateMs())
                            .append('\n');
                    List<CwDecodeEvent> decodeEvents = decoder.process(timingEvent);
                    for (CwDecodeEvent decodeEvent : decodeEvents) {
                        appendDecodeTraceLine(trace, decodeEvent, false);
                        interpreter.process(decodeEvent);
                        qsoStateMachine.process(interpreter.snapshot(), decodeEvent.timestampMs());
                    }
                }
            }
            sampleOffset += frameLength;
        }

        long flushTimestampMs = Math.max(
                1L,
                Math.round(waveData.samples().length * 1000.0d / waveData.sampleRateHz())
        );
        for (CwTimingEvent timingEvent : timingModel.flushPendingGap(flushTimestampMs)) {
            trace.append("  TIMING ")
                    .append(timingEvent.kind())
                    .append(' ')
                    .append(timingEvent.classification())
                    .append(" @").append(timingEvent.timestampMs())
                    .append(" dur=").append(timingEvent.durationMs())
                    .append(" dot=").append(timingEvent.dotEstimateMs())
                    .append(" [flush]\n");
            for (CwDecodeEvent decodeEvent : decoder.process(timingEvent)) {
                appendDecodeTraceLine(trace, decodeEvent, false);
                interpreter.process(decodeEvent);
                qsoStateMachine.process(interpreter.snapshot(), decodeEvent.timestampMs());
            }
        }
        for (CwDecodeEvent decodeEvent : decoder.flushPendingCharacter(flushTimestampMs)) {
            appendDecodeTraceLine(trace, decodeEvent, true);
            interpreter.process(decodeEvent);
            qsoStateMachine.process(interpreter.snapshot(), decodeEvent.timestampMs());
        }

        trace.append("\nFINAL=").append(decoder.snapshot().decodedText());
        System.out.println(trace);
    }

    private void appendDecodeTraceLine(StringBuilder trace, CwDecodeEvent decodeEvent, boolean flushed) {
        trace.append("    DECODE ")
                .append(decodeEvent.type())
                .append(" text=").append(decodeEvent.outputText())
                .append(" symbol=").append(decodeEvent.emittedValue())
                .append(" seq=").append(decodeEvent.sourceSequence());
        if (flushed) {
            trace.append(" [flush]");
        }
        trace.append('\n');
    }
}
