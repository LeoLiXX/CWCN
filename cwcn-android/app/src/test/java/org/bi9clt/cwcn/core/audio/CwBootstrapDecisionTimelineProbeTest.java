package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CwBootstrapDecisionTimelineProbeTest {
    private static final int FRAME_SIZE_SAMPLES = 256;
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;
    private static final long OPENING_WINDOW_MS = 2600L;
    private static final int MAX_TRACE_LINES = 24;

    @Test
    public void printRecording2AndCaptureBootstrapDecisionTimeline() throws Exception {
        Path recording2Wav = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(2).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (2)"));
        Path captureWav = LocalAudioDecodeTestSupport.findTraceDir().resolve("capture.wav");
        if (!Files.isRegularFile(captureWav)) {
            throw new IllegalStateException("Missing captured trace wav: " + captureWav);
        }

        printSource("recording2", loadFrames(recording2Wav));
        printSource("capture", loadFrames(captureWav));
    }

    private static void printSource(String label, List<AudioFrame> frames) {
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        label,
                        normalizeFramesToZero(frames),
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        System.out.println("==== bootstrap timeline: " + label + " ====");
        System.out.println("final=" + sanitize(detailed.probeResult().decodedText()));
        printFirstPassSummary("boundary", detailed.bootstrapBoundaryDecisionTraces());
        printFirstPassSummary("cadence", detailed.bootstrapCadenceDecisionTraces());
        System.out.println("-- boundary attempts --");
        printDecisionTraceWindow(detailed.bootstrapBoundaryDecisionTraces(), OPENING_WINDOW_MS, MAX_TRACE_LINES);
        System.out.println("-- cadence attempts --");
        printDecisionTraceWindow(detailed.bootstrapCadenceDecisionTraces(), OPENING_WINDOW_MS, MAX_TRACE_LINES);
    }

    private static void printFirstPassSummary(
            String label,
            List<LocalAudioDecodeTestSupport.BootstrapDecisionTrace> traces
    ) {
        LocalAudioDecodeTestSupport.BootstrapDecisionTrace firstAttempt = null;
        LocalAudioDecodeTestSupport.BootstrapDecisionTrace firstPass = null;
        for (LocalAudioDecodeTestSupport.BootstrapDecisionTrace trace : traces) {
            if (trace == null) {
                continue;
            }
            if (firstAttempt == null) {
                firstAttempt = trace;
            }
            if ("pass".equals(trace.compatibleDecision())) {
                firstPass = trace;
                break;
            }
        }
        System.out.println(String.format(
                Locale.US,
                "%s firstAttempt=%s firstPass=%s",
                label,
                describeTrace(firstAttempt),
                describeTrace(firstPass)
        ));
    }

    private static void printDecisionTraceWindow(
            List<LocalAudioDecodeTestSupport.BootstrapDecisionTrace> traces,
            long windowEndTimestampMs,
            int maxLines
    ) {
        int printed = 0;
        for (LocalAudioDecodeTestSupport.BootstrapDecisionTrace trace : traces) {
            if (trace == null || trace.timestampMs() > windowEndTimestampMs) {
                if (trace != null && trace.timestampMs() > windowEndTimestampMs) {
                    break;
                }
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "@%d kind=%s class=%s dur=%d cand=%d decision=%s verified=%s trusted=%s"
                            + " lock=%.2f near=%.2f unlock=%.2f dom=%.2f iso=%.2f rawWpm=%.1f rawDot=%d anchor=%s",
                    trace.timestampMs(),
                    safe(trace.eventKind()),
                    safe(trace.classification()),
                    trace.durationMs(),
                    trace.candidateDotEstimateMs(),
                    safe(trace.compatibleDecision()),
                    safe(trace.verifiedDecision()),
                    trace.trustedTimingEstablished(),
                    trace.recentLockedFrameRatio(),
                    trace.recentNearTargetLockedFrameRatio(),
                    trace.recentActiveUnlockedFrameRatio(),
                    trace.toneDominanceRatio(),
                    trace.narrowbandIsolationRatio(),
                    trace.rawWpm(),
                    trace.rawDotEstimateMs(),
                    safe(trace.anchorSummary())
            ));
            printed += 1;
            if (printed >= maxLines) {
                break;
            }
        }
    }

    private static String describeTrace(LocalAudioDecodeTestSupport.BootstrapDecisionTrace trace) {
        if (trace == null) {
            return "none";
        }
        return String.format(
                Locale.US,
                "@%dms/%s[%s] cand=%d rawDot=%d",
                trace.timestampMs(),
                safe(trace.compatibleDecision()),
                safe(trace.verifiedDecision()),
                trace.candidateDotEstimateMs(),
                trace.rawDotEstimateMs()
        );
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
}
