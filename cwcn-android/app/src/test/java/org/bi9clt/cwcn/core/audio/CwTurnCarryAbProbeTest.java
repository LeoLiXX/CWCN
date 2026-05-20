package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CwTurnCarryAbProbeTest {
    private static final int FRAME_SIZE_SAMPLES = 256;
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;

    @Test
    public void printRecording2AndCaptureTurnCarryAbProbe() throws Exception {
        Path recording2Wav = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(2).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (2)"));
        Path captureWav = LocalAudioDecodeTestSupport.findTraceDir().resolve("capture.wav");
        if (!Files.isRegularFile(captureWav)) {
            throw new IllegalStateException("Missing captured trace wav: " + captureWav);
        }

        printSourceProbe("recording2", loadFrames(recording2Wav));
        printSourceProbe("capture", loadFrames(captureWav));
    }

    private static void printSourceProbe(String label, List<AudioFrame> frames) {
        System.out.println("==== turn-carry A/B: " + label + " ====");
        printModeProbe(label, frames, false);
        printModeProbe(label, frames, true);
    }

    private static void printModeProbe(String label, List<AudioFrame> frames, boolean carryEnabled) {
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLikeWithTurnCarry(
                        label + (carryEnabled ? "-carry-on" : "-carry-off"),
                        normalizeFramesToZero(frames),
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        carryEnabled,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
        System.out.println(String.format(
                Locale.US,
                "-- carry=%s final=\"%s\" --",
                carryEnabled ? "ON" : "OFF",
                sanitize(detailed.probeResult().decodedText())
        ));
        for (TurnWindow turnWindow : buildTurnWindows(detailed)) {
            LocalAudioDecodeTestSupport.TimingStateTrace initTrace = firstTrustedTimingTraceInWindow(
                    detailed.timingStateTraces(),
                    turnWindow.startTimestampMs,
                    turnWindow.endTimestampMs
            );
            long initOffsetMs = initTrace == null
                    ? -1L
                    : Math.max(0L, initTrace.timestampMs() - turnWindow.startTimestampMs);
            String textBeforeTurn = textAtOrBefore(
                    detailed.decodeEvents(),
                    Math.max(0L, turnWindow.startTimestampMs - 1L)
            );
            String textAtTrust = textAtOrBefore(
                    detailed.decodeEvents(),
                    initTrace == null ? turnWindow.endTimestampMs : initTrace.timestampMs()
            );
            String textAtTurnEnd = textAtOrBefore(detailed.decodeEvents(), turnWindow.endTimestampMs);
            String preTrustText = sliceNewText(textBeforeTurn, textAtTrust);
            String turnText = sliceNewText(textBeforeTurn, textAtTurnEnd);
            System.out.println(String.format(
                    Locale.US,
                    "TURN %d seed=%d retained=%d trust=%dms reason=%s preTrust=\"%s\" text=\"%s\"",
                    turnWindow.turnIndex,
                    turnWindow.startTrace == null ? 0 : turnWindow.startTrace.turnSeedWpm(),
                    turnWindow.startTrace == null ? 0 : turnWindow.startTrace.retainedTurnAnchorWpm(),
                    initOffsetMs,
                    initTrace == null || initTrace.debugSnapshot() == null
                            ? "none"
                            : safe(initTrace.debugSnapshot().lastTrustedUpdateReason()),
                    sanitize(preTrustText),
                    sanitize(turnText)
            ));
        }
    }

    private static List<TurnWindow> buildTurnWindows(LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed) {
        ArrayList<TurnWindow> windows = new ArrayList<>();
        LocalAudioDecodeTestSupport.TurnTransitionTrace pendingStart = null;
        for (LocalAudioDecodeTestSupport.TurnTransitionTrace trace : detailed.turnTransitionTraces()) {
            if (trace == null) {
                continue;
            }
            if (trace.kind() == LocalAudioDecodeTestSupport.TurnTransitionTrace.Kind.START) {
                pendingStart = trace;
                continue;
            }
            if (trace.kind() == LocalAudioDecodeTestSupport.TurnTransitionTrace.Kind.END && pendingStart != null) {
                windows.add(new TurnWindow(
                        pendingStart.turnIndex(),
                        pendingStart.timestampMs(),
                        trace.timestampMs(),
                        pendingStart
                ));
                pendingStart = null;
            }
        }
        if (pendingStart != null) {
            windows.add(new TurnWindow(
                    pendingStart.turnIndex(),
                    pendingStart.timestampMs(),
                    detailed.flushTimestampMs(),
                    pendingStart
            ));
        }
        return windows;
    }

    private static LocalAudioDecodeTestSupport.TimingStateTrace firstTrustedTimingTraceInWindow(
            List<LocalAudioDecodeTestSupport.TimingStateTrace> traces,
            long startTimestampMs,
            long endTimestampMs
    ) {
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : traces) {
            if (trace == null
                    || trace.timestampMs() < startTimestampMs
                    || trace.timestampMs() > endTimestampMs
                    || trace.debugSnapshot() == null
                    || trace.debugSnapshot().trustedDotEstimateMs() <= 0.0d) {
                continue;
            }
            return trace;
        }
        return null;
    }

    private static String textAtOrBefore(List<CwDecodeEvent> decodeEvents, long timestampMs) {
        String latestText = "";
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            if (decodeEvent != null && decodeEvent.timestampMs() <= timestampMs) {
                latestText = decodeEvent.outputText();
            } else if (decodeEvent != null && decodeEvent.timestampMs() > timestampMs) {
                break;
            }
        }
        return latestText == null ? "" : latestText;
    }

    private static String sliceNewText(String previousText, String currentText) {
        String safePrevious = previousText == null ? "" : previousText;
        String safeCurrent = currentText == null ? "" : currentText;
        if (safePrevious.length() >= safeCurrent.length()) {
            return "";
        }
        return safeCurrent.substring(safePrevious.length());
    }

    private static List<AudioFrame> loadFrames(Path wavFile) throws Exception {
        LocalAudioDecodeTestSupport.WaveDataProbe waveData =
                LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);
        return buildFrames(waveData.samples(), waveData.sampleRateHz());
    }

    private static List<AudioFrame> buildFrames(short[] samples, int sampleRateHz) {
        ArrayList<AudioFrame> frames = new ArrayList<>();
        long sampleOffset = 0L;
        for (int offset = 0; offset < samples.length; offset += FRAME_SIZE_SAMPLES) {
            int frameLength = Math.min(FRAME_SIZE_SAMPLES, samples.length - offset);
            short[] frameSamples = new short[frameLength];
            System.arraycopy(samples, offset, frameSamples, 0, frameLength);
            frames.add(LocalAudioDecodeTestSupport.buildFrameForProbe(frameSamples, sampleRateHz, sampleOffset));
            sampleOffset += frameLength;
        }
        return frames;
    }

    private static List<AudioFrame> normalizeFramesToZero(List<AudioFrame> frames) {
        ArrayList<AudioFrame> normalized = new ArrayList<>(frames.size());
        if (frames.isEmpty()) {
            return normalized;
        }
        long firstTimestampMs = frames.get(0).capturedAtMs();
        for (AudioFrame frame : frames) {
            normalized.add(new AudioFrame(
                    frame.samples(),
                    frame.sampleRateHz(),
                    frame.channelCount(),
                    frame.peakAmplitude(),
                    frame.rmsAmplitude(),
                    frame.clippedSampleCount(),
                    Math.max(0L, frame.capturedAtMs() - firstTimestampMs)
            ));
        }
        return normalized;
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "(empty)";
        }
        String normalized = text.replace('\u25A1', '?').trim();
        return normalized.isEmpty() ? "(empty)" : normalized;
    }

    private static String safe(String value) {
        return value == null || value.isEmpty() ? "none" : value;
    }

    private static final class TurnWindow {
        private final int turnIndex;
        private final long startTimestampMs;
        private final long endTimestampMs;
        private final LocalAudioDecodeTestSupport.TurnTransitionTrace startTrace;

        private TurnWindow(
                int turnIndex,
                long startTimestampMs,
                long endTimestampMs,
                LocalAudioDecodeTestSupport.TurnTransitionTrace startTrace
        ) {
            this.turnIndex = turnIndex;
            this.startTimestampMs = startTimestampMs;
            this.endTimestampMs = endTimestampMs;
            this.startTrace = startTrace;
        }
    }
}
