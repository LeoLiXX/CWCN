package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CwRecording12PrefixWindowProbeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;
    private static final long PREFIX_END_MS = 8480L;
    private static final long MIDDLE_START_MS = 8500L;
    private static final long MIDDLE_END_MS = 14760L;
    private static final long TAIL_START_MS = 14760L;

    @Test
    public void printRecording12PrefixUntil8480Ms() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(12).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for recording (12)"));
        List<AudioFrame> allFrames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );
        List<AudioFrame> prefixFrames = sliceFramesBefore(allFrames, PREFIX_END_MS);

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult fullBase = decode(allFrames, false);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult fullMerge = decode(allFrames, true);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult prefixBase = decode(prefixFrames, false);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult prefixMerge = decode(prefixFrames, true);

        System.out.println("==== recording(12) prefix until 8480ms ====");
        System.out.println(String.format(
                Locale.US,
                "frameCutoff=<%dms totalFrames=%d prefixFrames=%d lastPrefixFrame=%d",
                PREFIX_END_MS,
                allFrames.size(),
                prefixFrames.size(),
                prefixFrames.isEmpty() ? -1L : prefixFrames.get(prefixFrames.size() - 1).capturedAtMs()
        ));

        printCase(
                "BASE",
                prefixBase,
                textAtOrBefore(fullBase.decodeEvents(), PREFIX_END_MS),
                PREFIX_END_MS
        );
        printCase(
                "MERGE",
                prefixMerge,
                textAtOrBefore(fullMerge.decodeEvents(), PREFIX_END_MS),
                PREFIX_END_MS
        );
    }

    @Test
    public void printRecording12MiddleAndTailSegments() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(12).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for recording (12)"));
        List<AudioFrame> allFrames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );

        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult fullBase = decode(allFrames, false);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult fullMerge = decode(allFrames, true);

        printRangeCase(
                "middle-8500-14760",
                allFrames,
                fullBase,
                fullMerge,
                MIDDLE_START_MS,
                MIDDLE_END_MS
        );
        printRangeCase(
                "tail-14760-end",
                allFrames,
                fullBase,
                fullMerge,
                TAIL_START_MS,
                Long.MAX_VALUE
        );
    }

    private static LocalAudioDecodeTestSupport.OfflineDetailedProbeResult decode(
            List<AudioFrame> frames,
            boolean merge
    ) {
        return LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                merge ? "recording12-prefix-merge" : "recording12-prefix-base",
                frames,
                PREFERRED_TONE_HZ,
                SEED_WPM,
                SQL_PERCENT,
                false,
                false,
                false,
                merge,
                false,
                CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
        );
    }

    private static List<AudioFrame> sliceFramesBefore(List<AudioFrame> frames, long endExclusiveMs) {
        ArrayList<AudioFrame> sliced = new ArrayList<>();
        for (AudioFrame frame : frames) {
            if (frame == null || frame.capturedAtMs() >= endExclusiveMs) {
                break;
            }
            sliced.add(frame);
        }
        return sliced;
    }

    private static List<AudioFrame> sliceFramesRange(
            List<AudioFrame> frames,
            long startInclusiveMs,
            long endExclusiveMs
    ) {
        ArrayList<AudioFrame> sliced = new ArrayList<>();
        for (AudioFrame frame : frames) {
            if (frame == null) {
                continue;
            }
            long timestampMs = frame.capturedAtMs();
            if (timestampMs < startInclusiveMs) {
                continue;
            }
            if (timestampMs >= endExclusiveMs) {
                break;
            }
            sliced.add(frame);
        }
        return LocalAudioDecodeTestSupport.normalizeFramesToZero(sliced);
    }

    private static void printRangeCase(
            String label,
            List<AudioFrame> allFrames,
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult fullBase,
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult fullMerge,
            long startInclusiveMs,
            long endExclusiveMs
    ) {
        List<AudioFrame> rangeFrames = sliceFramesRange(allFrames, startInclusiveMs, endExclusiveMs);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult baseRange = decode(rangeFrames, false);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult mergeRange = decode(rangeFrames, true);

        System.out.println("==== recording(12) " + label + " ====");
        System.out.println(String.format(
                Locale.US,
                "range=%d..%s totalFrames=%d rangeFrames=%d firstRangeFrame=%d lastRangeFrame=%d",
                startInclusiveMs,
                endExclusiveMs == Long.MAX_VALUE ? "end" : String.valueOf(endExclusiveMs),
                allFrames.size(),
                rangeFrames.size(),
                rangeFrames.isEmpty() ? -1L : rangeFrames.get(0).capturedAtMs(),
                rangeFrames.isEmpty() ? -1L : rangeFrames.get(rangeFrames.size() - 1).capturedAtMs()
        ));
        printRangeVariant(
                "BASE",
                baseRange,
                textBetween(fullBase.decodeEvents(), startInclusiveMs, endExclusiveMs)
        );
        printRangeVariant(
                "MERGE",
                mergeRange,
                textBetween(fullMerge.decodeEvents(), startInclusiveMs, endExclusiveMs)
        );
    }

    private static void printCase(
            String label,
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult prefixResult,
            String fullTextAtCutoff,
            long cutoffMs
    ) {
        System.out.println("-- " + label + " --");
        System.out.println("prefixFinal=" + sanitize(prefixResult.probeResult().decodedText()));
        System.out.println("fullTextAtCutoff=" + sanitize(fullTextAtCutoff));
        System.out.println("sameAsFullAtCutoff=" + yesNo(
                sanitize(prefixResult.probeResult().decodedText()).equals(sanitize(fullTextAtCutoff))
        ));
        System.out.println("decodeEvents:");
        boolean printed = false;
        for (CwDecodeEvent event : prefixResult.decodeEvents()) {
            if (event == null || event.timestampMs() > cutoffMs) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "  @%d %-18s emit=%s seq=%s out=%s unk=%s",
                    event.timestampMs(),
                    event.type(),
                    sanitize(event.emittedValue()),
                    sanitize(event.sourceSequence()),
                    sanitize(event.outputText()),
                    yesNo(event.unknownCharacter())
            ));
            printed = true;
        }
        if (!printed) {
            System.out.println("  none");
        }
    }

    private static void printRangeVariant(
            String label,
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult rangeResult,
            String fullRangeText
    ) {
        System.out.println("-- " + label + " --");
        System.out.println("segmentFinal=" + sanitize(rangeResult.probeResult().decodedText()));
        System.out.println("fullRangeText=" + sanitize(fullRangeText));
        System.out.println("sameAsFullRange=" + yesNo(
                sanitize(rangeResult.probeResult().decodedText()).equals(sanitize(fullRangeText))
        ));
        System.out.println("decodeEvents:");
        boolean printed = false;
        for (CwDecodeEvent event : rangeResult.decodeEvents()) {
            if (event == null) {
                continue;
            }
            System.out.println(String.format(
                    Locale.US,
                    "  @%d %-18s emit=%s seq=%s out=%s unk=%s",
                    event.timestampMs(),
                    event.type(),
                    sanitize(event.emittedValue()),
                    sanitize(event.sourceSequence()),
                    sanitize(event.outputText()),
                    yesNo(event.unknownCharacter())
            ));
            printed = true;
        }
        if (!printed) {
            System.out.println("  none");
        }
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

    private static String textBetween(
            List<CwDecodeEvent> decodeEvents,
            long startInclusiveMs,
            long endExclusiveMs
    ) {
        String startText = textAtOrBefore(decodeEvents, Math.max(-1L, startInclusiveMs - 1L));
        String endText = endExclusiveMs == Long.MAX_VALUE
                ? textAtOrBefore(decodeEvents, Long.MAX_VALUE)
                : textAtOrBefore(decodeEvents, endExclusiveMs);
        if (endText.startsWith(startText)) {
            return endText.substring(startText.length());
        }
        return endText;
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u25A1', '?');
    }

    private static String yesNo(boolean value) {
        return value ? "Y" : "N";
    }
}
