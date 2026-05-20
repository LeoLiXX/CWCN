package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertTrue;

public final class CwRecording12MiddleSqlSweepProbeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int[] SQL_LEVELS = new int[]{0, 5, 10, 15, 20, 25, 30, 40, 55};
    private static final long RANGE_START_MS = 8700L;
    private static final long RANGE_END_MS = 13360L;

    @Test
    public void printRecording12MiddleSqlSweep() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(12).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for recording (12)"));
        List<AudioFrame> allFrames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );
        List<AudioFrame> rangeFrames = sliceFramesRange(allFrames, RANGE_START_MS, RANGE_END_MS);

        System.out.println("==== recording12 middle sql sweep ====");
        System.out.println(String.format(
                Locale.US,
                "range=%d..%d totalFrames=%d rangeFrames=%d firstRangeFrame=%d lastRangeFrame=%d",
                RANGE_START_MS,
                RANGE_END_MS,
                allFrames.size(),
                rangeFrames.size(),
                rangeFrames.isEmpty() ? -1L : rangeFrames.get(0).capturedAtMs(),
                rangeFrames.isEmpty() ? -1L : rangeFrames.get(rangeFrames.size() - 1).capturedAtMs()
        ));

        for (int sqlPercent : SQL_LEVELS) {
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult fullDetailed =
                    LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                            "recording12-middle-full-sql-" + sqlPercent,
                            allFrames,
                            PREFERRED_TONE_HZ,
                            SEED_WPM,
                            sqlPercent,
                            false,
                            CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                    );
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult segmentDetailed =
                    LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                            "recording12-middle-segment-sql-" + sqlPercent,
                            rangeFrames,
                            PREFERRED_TONE_HZ,
                            SEED_WPM,
                            sqlPercent,
                            false,
                            CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                    );

            String fullRangeText = sanitize(textBetween(
                    fullDetailed.decodeEvents(),
                    RANGE_START_MS,
                    RANGE_END_MS
            ));
            String fullFinal = sanitize(fullDetailed.probeResult().decodedText());
            String segmentFinal = sanitize(segmentDetailed.probeResult().decodedText());
            String segmentStable = sanitize(textAtOrBefore(
                    segmentDetailed.stableAcceptedDecodeEvents(),
                    segmentDetailed.flushTimestampMs()
            ));
            String segmentRaw = sanitize(textAtOrBefore(
                    segmentDetailed.rawDecodeEvents(),
                    segmentDetailed.flushTimestampMs()
            ));

            System.out.println(String.format(
                    Locale.US,
                    "sql=%d fullTrust=%s segTrust=%s fullWpm=%d segWpm=%d fullBi9=%d segBi9=%d fullIn700=%d segIn700=%d fullPse=%d segPse=%d",
                    sqlPercent,
                    firstTrustedOffsetMs(fullDetailed) < 0L ? "-" : firstTrustedOffsetMs(fullDetailed) + "ms",
                    firstTrustedOffsetMs(segmentDetailed) < 0L ? "-" : firstTrustedOffsetMs(segmentDetailed) + "ms",
                    fullDetailed.probeResult().timingSnapshot().estimatedWpm(),
                    segmentDetailed.probeResult().timingSnapshot().estimatedWpm(),
                    countOccurrences(canonicalize(fullRangeText), "BI9CMS"),
                    countOccurrences(canonicalize(segmentFinal), "BI9CMS"),
                    countOccurrences(canonicalize(fullRangeText), "IN700"),
                    countOccurrences(canonicalize(segmentFinal), "IN700"),
                    countOccurrences(canonicalize(fullRangeText), "PSE"),
                    countOccurrences(canonicalize(segmentFinal), "PSE")
            ));
            System.out.println("fullRange=" + fullRangeText);
            System.out.println("fullFinal=" + fullFinal);
            System.out.println("segmentFinal=" + segmentFinal);
            System.out.println("segmentStable=" + segmentStable);
            System.out.println("segmentRaw=" + segmentRaw);
        }

        assertTrue(true);
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

    private static long firstTrustedOffsetMs(LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed) {
        if (detailed.frames().isEmpty()) {
            return -1L;
        }
        long firstFrameTimestampMs = detailed.frames().get(0).capturedAtMs();
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : detailed.timingStateTraces()) {
            if (trace == null
                    || trace.debugSnapshot() == null
                    || trace.debugSnapshot().trustedDotEstimateMs() <= 0.0d) {
                continue;
            }
            return Math.max(0L, trace.timestampMs() - firstFrameTimestampMs);
        }
        return -1L;
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
        StringBuilder builder = new StringBuilder();
        String previousText = "";
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            if (decodeEvent == null) {
                continue;
            }
            if (decodeEvent.timestampMs() < startInclusiveMs) {
                previousText = decodeEvent.outputText() == null ? "" : decodeEvent.outputText();
                continue;
            }
            if (decodeEvent.timestampMs() >= endExclusiveMs) {
                break;
            }
            String currentText = decodeEvent.outputText() == null ? "" : decodeEvent.outputText();
            if (currentText.startsWith(previousText)) {
                builder.append(currentText.substring(previousText.length()));
            } else {
                builder.append(currentText);
            }
            previousText = currentText;
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
}
