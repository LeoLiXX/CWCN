package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel.DebugSnapshot;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CwCommittedOpeningVisibilityProbeTest {
    private static final int FRAME_SIZE_SAMPLES = 256;
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;
    private static final long MIN_OPENING_WINDOW_MS = 6000L;
    private static final long POST_TRUST_WINDOW_MS = 1800L;
    private static final int MAX_EVENT_LINES = 48;

    @Test
    public void printCommittedOpeningVisibilityForRepresentativeCases() throws Exception {
        Path recording2Wav = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(2).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (2)"));
        Path recording8Wav = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(8).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (8)"));
        Path recording16Wav = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(16).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (16)"));
        Path captureWav = LocalAudioDecodeTestSupport.findTraceDir().resolve("capture.wav");
        if (!Files.isRegularFile(captureWav)) {
            throw new IllegalStateException("Missing captured trace wav: " + captureWav);
        }

        printSource("recording2", loadFrames(recording2Wav));
        printSource("recording8", loadFrames(recording8Wav));
        printSource("recording16", loadFrames(recording16Wav));
        printSource("capture.wav", loadFrames(captureWav));
    }

    private static void printSource(String label, List<AudioFrame> frames) {
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult live =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        label,
                        normalizeFramesToZero(frames),
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        LocalAudioDecodeTestSupport.TimingStateTrace initTrace = firstTrustedTimingTraceInWindow(
                live.timingStateTraces(),
                0L,
                live.flushTimestampMs()
        );
        long trustTimestampMs = initTrace == null ? -1L : initTrace.timestampMs();
        DebugSnapshot initDebugSnapshot = initTrace == null ? null : initTrace.debugSnapshot();
        String committedAtTrust = trustTimestampMs < 0L
                ? ""
                : sliceNewText(
                textAtOrBefore(live.decodeEvents(), 0L),
                textAtOrBefore(live.decodeEvents(), trustTimestampMs)
        );
        String rawAtTrust = trustTimestampMs < 0L
                ? ""
                : sliceNewText(
                textAtOrBefore(live.rawDecodeEvents(), 0L),
                textAtOrBefore(live.rawDecodeEvents(), trustTimestampMs)
        );
        String stableAtTrust = trustTimestampMs < 0L
                ? ""
                : sliceNewText(
                textAtOrBefore(live.stableAcceptedDecodeEvents(), 0L),
                textAtOrBefore(live.stableAcceptedDecodeEvents(), trustTimestampMs)
        );
        int committedPreTrustWordBreakCount = trustTimestampMs < 0L
                ? 0
                : countWordBreaksAtOrBefore(live.decodeEvents(), trustTimestampMs);
        int rawPreTrustWordBreakCount = trustTimestampMs < 0L
                ? 0
                : countWordBreaksAtOrBefore(live.rawDecodeEvents(), trustTimestampMs);
        int stablePreTrustWordBreakCount = trustTimestampMs < 0L
                ? 0
                : countWordBreaksAtOrBefore(live.stableAcceptedDecodeEvents(), trustTimestampMs);
        long committedFirstPreTrustWordBreakMs = trustTimestampMs < 0L
                ? -1L
                : firstWordBreakTimestampAtOrBefore(live.decodeEvents(), trustTimestampMs);
        long rawFirstPreTrustWordBreakMs = trustTimestampMs < 0L
                ? -1L
                : firstWordBreakTimestampAtOrBefore(live.rawDecodeEvents(), trustTimestampMs);
        long stableFirstPreTrustWordBreakMs = trustTimestampMs < 0L
                ? -1L
                : firstWordBreakTimestampAtOrBefore(live.stableAcceptedDecodeEvents(), trustTimestampMs);
        long firstCommittedCharacterTimestampMs = firstCommittedCharacterTimestamp(live.decodeEvents());
        long firstCommittedLeadMs = trustTimestampMs < 0L || firstCommittedCharacterTimestampMs < 0L
                ? -1L
                : Math.max(0L, trustTimestampMs - firstCommittedCharacterTimestampMs);
        long openingWindowEndMs = trustTimestampMs < 0L
                ? Math.min(live.flushTimestampMs(), MIN_OPENING_WINDOW_MS)
                : Math.min(
                live.flushTimestampMs(),
                Math.max(MIN_OPENING_WINDOW_MS, trustTimestampMs + POST_TRUST_WINDOW_MS)
        );

        System.out.println("==== committed opening visibility: " + label + " ====");
        System.out.println("committed final=" + sanitize(textAtOrBefore(live.decodeEvents(), live.flushTimestampMs())));
        System.out.println("raw final=" + sanitize(textAtOrBefore(live.rawDecodeEvents(), live.flushTimestampMs())));
        System.out.println("stable final=" + sanitize(textAtOrBefore(
                live.stableAcceptedDecodeEvents(),
                live.flushTimestampMs()
        )));
        System.out.println(String.format(
                Locale.US,
                "trustAt=%dms trustReason=%s trustedDot=%.1f firstCommittedChar=%dms leadBeforeTrust=%dms",
                trustTimestampMs,
                initDebugSnapshot == null ? "none" : safe(initDebugSnapshot.lastTrustedUpdateReason()),
                initDebugSnapshot == null ? 0.0d : initDebugSnapshot.trustedDotEstimateMs(),
                firstCommittedCharacterTimestampMs,
                firstCommittedLeadMs
        ));
        System.out.println("preTrust committed=" + sanitize(committedAtTrust));
        System.out.println("preTrust raw=" + sanitize(rawAtTrust));
        System.out.println("preTrust stable=" + sanitize(stableAtTrust));
        System.out.println(String.format(
                Locale.US,
                "preTrust wordBreaks committed=%d@%d raw=%d@%d stable=%d@%d",
                committedPreTrustWordBreakCount,
                committedFirstPreTrustWordBreakMs,
                rawPreTrustWordBreakCount,
                rawFirstPreTrustWordBreakMs,
                stablePreTrustWordBreakCount,
                stableFirstPreTrustWordBreakMs
        ));
        System.out.println("-- committed opening --");
        printDecodeEvents(live.decodeEvents(), openingWindowEndMs, MAX_EVENT_LINES);
    }

    private static long firstCommittedCharacterTimestamp(List<CwDecodeEvent> decodeEvents) {
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            if (decodeEvent != null && decodeEvent.type() == CwDecodeEvent.Type.CHARACTER_DECODED) {
                return decodeEvent.timestampMs();
            }
        }
        return -1L;
    }

    private static int countWordBreaksAtOrBefore(List<CwDecodeEvent> decodeEvents, long timestampMs) {
        int count = 0;
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            if (decodeEvent == null) {
                continue;
            }
            if (decodeEvent.timestampMs() > timestampMs) {
                break;
            }
            if (decodeEvent.type() == CwDecodeEvent.Type.WORD_BREAK) {
                count += 1;
            }
        }
        return count;
    }

    private static long firstWordBreakTimestampAtOrBefore(List<CwDecodeEvent> decodeEvents, long timestampMs) {
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            if (decodeEvent == null) {
                continue;
            }
            if (decodeEvent.timestampMs() > timestampMs) {
                break;
            }
            if (decodeEvent.type() == CwDecodeEvent.Type.WORD_BREAK) {
                return decodeEvent.timestampMs();
            }
        }
        return -1L;
    }

    private static void printDecodeEvents(
            List<CwDecodeEvent> decodeEvents,
            long windowEndTimestampMs,
            int maxLines
    ) {
        int printed = 0;
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            if (decodeEvent == null || decodeEvent.timestampMs() > windowEndTimestampMs) {
                if (decodeEvent != null && decodeEvent.timestampMs() > windowEndTimestampMs) {
                    break;
                }
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "@%d %-18s emit=%s seq=%s out=%s unknown=%s",
                    decodeEvent.timestampMs(),
                    decodeEvent.type(),
                    sanitize(decodeEvent.emittedValue()),
                    sanitize(decodeEvent.sourceSequence()),
                    sanitize(decodeEvent.outputText()),
                    decodeEvent.unknownCharacter()
            ));
            printed += 1;
            if (printed >= maxLines) {
                break;
            }
        }
    }

    private static LocalAudioDecodeTestSupport.TimingStateTrace firstTrustedTimingTraceInWindow(
            List<LocalAudioDecodeTestSupport.TimingStateTrace> traces,
            long windowStartTimestampMs,
            long windowEndTimestampMs
    ) {
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : traces) {
            if (trace == null
                    || trace.debugSnapshot() == null
                    || trace.timestampMs() < windowStartTimestampMs
                    || trace.timestampMs() > windowEndTimestampMs) {
                continue;
            }
            if (trace.debugSnapshot().trustedDotEstimateMs() > 0.0d) {
                return trace;
            }
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
        return value == null || value.isEmpty() ? "(none)" : value;
    }
}
