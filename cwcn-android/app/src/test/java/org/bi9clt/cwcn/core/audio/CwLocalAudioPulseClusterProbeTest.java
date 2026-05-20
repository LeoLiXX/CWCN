package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CwLocalAudioPulseClusterProbeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;
    private static final long OPENING_UNKNOWN_WINDOW_END_MS = 8000L;
    private static final ProbeTarget[] OPENING_TARGETS = new ProbeTarget[]{
            new ProbeTarget("opening-first-unknown", null, OPENING_UNKNOWN_WINDOW_END_MS),
            new ProbeTarget("questioned-bg1xxx", "-.--...", Long.MAX_VALUE),
            new ProbeTarget("questioned-ja1abc", "-.-.---", Long.MAX_VALUE)
    };
    private static final double[] CLUSTER_THRESHOLDS = new double[]{0.75d, 1.00d, 1.20d, 1.25d};

    @Test
    public void printRecording8OpeningPulseClusters() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().contains("(8)"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for recording (8)"));
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeWavFileDetailedLiveLike(
                        wavFile,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        LinkedHashMap<String, LocalAudioDecodeTestSupport.ForcedToneReplayResult> replays = new LinkedHashMap<>();
        replays.put("TRK", LocalAudioDecodeTestSupport.replayForcedTrackedToneDecode(detailed));
        replays.put("EFF", LocalAudioDecodeTestSupport.replayForcedEffectiveTrackedToneDecode(detailed));
        replays.put("HYP", LocalAudioDecodeTestSupport.replayForcedHypothesisToneDecode(detailed));

        for (ProbeTarget target : OPENING_TARGETS) {
            for (Map.Entry<String, LocalAudioDecodeTestSupport.ForcedToneReplayResult> entry : replays.entrySet()) {
                CharacterTimingDetail detail = findUnknownDetail(
                        entry.getValue(),
                        target.sequence,
                        target.maxTimestampMs
                );
                System.out.println(
                        "==== " + entry.getValue().sourceLabel()
                                + " " + entry.getKey()
                                + " pulse-cluster [" + target.label + "] ===="
                );
                System.out.println("opening=" + sanitize(textAtOrBefore(
                        entry.getValue().decodeEvents(),
                        OPENING_UNKNOWN_WINDOW_END_MS
                )));
                System.out.println("flush=" + sanitize(textAtOrBefore(
                        entry.getValue().decodeEvents(),
                        Long.MAX_VALUE
                )));
                if (detail == null) {
                    System.out.println("target=" + (target.sequence == null ? "first-unknown" : target.sequence) + " not-found");
                    continue;
                }
                System.out.println("seq=" + detail.decodeEvent.sourceSequence() + " ts=" + detail.decodeEvent.timestampMs());
                System.out.println("tones=" + renderTones(detail.internalToneEvents()));
                System.out.println("gaps=" + renderGaps(detail.internalGapEvents()));
                for (double threshold : CLUSTER_THRESHOLDS) {
                    System.out.println(String.format(
                            Locale.US,
                            "cluster<=%.2fdot=%s",
                            threshold,
                            renderClusters(detail, threshold)
                    ));
                }
            }
        }
    }

    private static CharacterTimingDetail findUnknownDetail(
            LocalAudioDecodeTestSupport.ForcedToneReplayResult replay,
            String sourceSequence
    ) {
        return findUnknownDetail(replay, sourceSequence, Long.MAX_VALUE);
    }

    private static CharacterTimingDetail findUnknownDetail(
            LocalAudioDecodeTestSupport.ForcedToneReplayResult replay,
            String sourceSequence,
            long maxTimestampMs
    ) {
        List<CharacterTimingDetail> details = buildCharacterTimingDetails(replay);
        if (sourceSequence == null) {
            for (CharacterTimingDetail detail : details) {
                if (detail.decodeEvent.unknownCharacter()
                        && detail.decodeEvent.timestampMs() <= maxTimestampMs) {
                    return detail;
                }
            }
            return null;
        }
        for (CharacterTimingDetail detail : details) {
            if (!detail.decodeEvent.unknownCharacter()) {
                continue;
            }
            if (sourceSequence.equals(detail.decodeEvent.sourceSequence())) {
                return detail;
            }
        }
        return null;
    }

    private static String textAtOrBefore(List<CwDecodeEvent> decodeEvents, long timestampMs) {
        String latestText = "";
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            if (decodeEvent == null) {
                continue;
            }
            if (decodeEvent.timestampMs() <= timestampMs) {
                latestText = decodeEvent.outputText();
                continue;
            }
            break;
        }
        return latestText == null ? "" : latestText;
    }

    private static String sanitize(String value) {
        return value == null ? "(null)" : value.replace('\u25A1', '?');
    }

    private static List<CharacterTimingDetail> buildCharacterTimingDetails(
            LocalAudioDecodeTestSupport.ForcedToneReplayResult replay
    ) {
        ArrayList<CwDecodeEvent> characterEvents = new ArrayList<>();
        for (CwDecodeEvent event : replay.decodeEvents()) {
            if (event.type() == CwDecodeEvent.Type.CHARACTER_DECODED) {
                characterEvents.add(event);
            }
        }

        ArrayList<CharacterTimingDetail> details = new ArrayList<>();
        List<CwTimingEvent> timingEvents = replay.timingEvents();
        int boundarySearchStart = 0;
        for (CwDecodeEvent characterEvent : characterEvents) {
            int boundaryIndex = findBoundaryIndexForCharacter(timingEvents, characterEvent, boundarySearchStart);
            CwTimingEvent boundaryGapEvent = boundaryIndex >= 0 ? timingEvents.get(boundaryIndex) : null;
            List<CwTimingEvent> characterTimingEvents = collectCharacterTimingEvents(
                    timingEvents,
                    boundaryIndex,
                    characterEvent.sourceSequence()
            );
            details.add(new CharacterTimingDetail(
                    characterEvent,
                    characterTimingEvents,
                    boundaryGapEvent
            ));
            if (boundaryIndex >= 0) {
                boundarySearchStart = boundaryIndex + 1;
            }
        }
        return details;
    }

    private static int findBoundaryIndexForCharacter(
            List<CwTimingEvent> timingEvents,
            CwDecodeEvent characterEvent,
            int searchStart
    ) {
        for (int index = Math.max(0, searchStart); index < timingEvents.size(); index++) {
            CwTimingEvent timingEvent = timingEvents.get(index);
            if (timingEvent.timestampMs() != characterEvent.timestampMs()) {
                continue;
            }
            if (timingEvent.kind() == CwTimingEvent.Kind.GAP
                    && timingEvent.classification() != CwTimingEvent.Classification.INTRA_SYMBOL_GAP) {
                return index;
            }
        }
        return -1;
    }

    private static List<CwTimingEvent> collectCharacterTimingEvents(
            List<CwTimingEvent> timingEvents,
            int boundaryIndex,
            String sourceSequence
    ) {
        int requiredTones = sourceSequence == null ? 0 : sourceSequence.length();
        if (requiredTones <= 0) {
            return new ArrayList<>();
        }
        int scanIndex = boundaryIndex >= 0 ? boundaryIndex - 1 : timingEvents.size() - 1;
        ArrayList<CwTimingEvent> reversed = new ArrayList<>();
        int collectedTones = 0;
        while (scanIndex >= 0 && collectedTones < requiredTones) {
            CwTimingEvent timingEvent = timingEvents.get(scanIndex);
            if (timingEvent.kind() == CwTimingEvent.Kind.TONE) {
                reversed.add(timingEvent);
                collectedTones += 1;
                scanIndex -= 1;
                continue;
            }
            if (timingEvent.classification() == CwTimingEvent.Classification.INTRA_SYMBOL_GAP) {
                reversed.add(timingEvent);
                scanIndex -= 1;
                continue;
            }
            break;
        }
        ArrayList<CwTimingEvent> ordered = new ArrayList<>(reversed.size());
        for (int index = reversed.size() - 1; index >= 0; index--) {
            ordered.add(reversed.get(index));
        }
        return ordered;
    }

    private static String renderTones(List<CwTimingEvent> tones) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < tones.size(); index++) {
            if (index > 0) {
                builder.append(" | ");
            }
            CwTimingEvent tone = tones.get(index);
            double ratio = tone.durationMs() / (double) Math.max(1L, tone.dotEstimateMs());
            builder.append(String.format(
                    Locale.US,
                    "t%d=%s/%dms/%.2f",
                    index + 1,
                    tone.classification() == CwTimingEvent.Classification.DAH ? "DAH" : "DIT",
                    tone.durationMs(),
                    ratio
            ));
        }
        return builder.toString();
    }

    private static String renderGaps(List<CwTimingEvent> gaps) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < gaps.size(); index++) {
            if (index > 0) {
                builder.append(" | ");
            }
            CwTimingEvent gap = gaps.get(index);
            double ratio = gap.durationMs() / (double) Math.max(1L, gap.dotEstimateMs());
            builder.append(String.format(
                    Locale.US,
                    "g%d=%dms/%.2f",
                    index + 1,
                    gap.durationMs(),
                    ratio
            ));
        }
        return builder.toString();
    }

    private static String renderClusters(CharacterTimingDetail detail, double thresholdRatio) {
        List<CwTimingEvent> tones = detail.internalToneEvents();
        List<CwTimingEvent> gaps = detail.internalGapEvents();
        if (tones.isEmpty()) {
            return "none";
        }
        ArrayList<String> clusters = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        current.append(toneSymbol(tones.get(0)));
        for (int index = 0; index < gaps.size() && (index + 1) < tones.size(); index++) {
            CwTimingEvent gap = gaps.get(index);
            double ratio = gap.durationMs() / (double) Math.max(1L, gap.dotEstimateMs());
            if (ratio <= thresholdRatio) {
                current.append(toneSymbol(tones.get(index + 1)));
            } else {
                clusters.add(current.toString());
                current = new StringBuilder();
                current.append(toneSymbol(tones.get(index + 1)));
            }
        }
        clusters.add(current.toString());
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < clusters.size(); index++) {
            if (index > 0) {
                builder.append(' ');
            }
            builder.append('[').append(clusters.get(index)).append(']');
        }
        return builder.toString();
    }

    private static char toneSymbol(CwTimingEvent tone) {
        return tone.classification() == CwTimingEvent.Classification.DAH ? '-' : '.';
    }

    private static final class CharacterTimingDetail {
        private final CwDecodeEvent decodeEvent;
        private final List<CwTimingEvent> characterEvents;
        private final CwTimingEvent boundaryGapEvent;

        private CharacterTimingDetail(
                CwDecodeEvent decodeEvent,
                List<CwTimingEvent> characterEvents,
                CwTimingEvent boundaryGapEvent
        ) {
            this.decodeEvent = decodeEvent;
            this.characterEvents = characterEvents;
            this.boundaryGapEvent = boundaryGapEvent;
        }

        private List<CwTimingEvent> internalToneEvents() {
            ArrayList<CwTimingEvent> tones = new ArrayList<>();
            for (CwTimingEvent event : characterEvents) {
                if (event.kind() == CwTimingEvent.Kind.TONE) {
                    tones.add(event);
                }
            }
            return tones;
        }

        private List<CwTimingEvent> internalGapEvents() {
            ArrayList<CwTimingEvent> gaps = new ArrayList<>();
            for (CwTimingEvent event : characterEvents) {
                if (event.kind() == CwTimingEvent.Kind.GAP) {
                    gaps.add(event);
                }
            }
            return gaps;
        }
    }

    private static final class ProbeTarget {
        private final String label;
        private final String sequence;
        private final long maxTimestampMs;

        private ProbeTarget(String label, String sequence, long maxTimestampMs) {
            this.label = label;
            this.sequence = sequence;
            this.maxTimestampMs = maxTimestampMs;
        }
    }
}
