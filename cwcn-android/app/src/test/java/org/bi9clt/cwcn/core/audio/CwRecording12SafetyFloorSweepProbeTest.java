package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.SqlThresholdModel;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class CwRecording12SafetyFloorSweepProbeTest {
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int DEFAULT_SQL_PERCENT = 55;
    private static final int MIN_SQL_PERCENT = 0;
    private static final int[] FLOOR_SWEEP = new int[] {90, 60, 30, 10, 0};
    private static final long PREFIX_CUTOFF_MS = 5500L;
    private static final long[] SAMPLE_TIMESTAMPS_MS = new long[] {1952L, 2000L, 2032L, 2048L, 2064L, 2240L, 2256L};

    @Test
    public void printRecording12SafetyFloorSweepAtMinSql() throws Exception {
        printSweep("sql0", MIN_SQL_PERCENT);
    }

    @Test
    public void printRecording12SafetyFloorSweepAtDefaultSql() throws Exception {
        printSweep("sql55", DEFAULT_SQL_PERCENT);
    }

    private static void printSweep(String label, int sqlPercent) throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(12).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for recording (12)"));
        List<AudioFrame> frames = LocalAudioDecodeTestSupport.normalizeFramesToZero(
                LocalAudioDecodeTestSupport.loadFramesFromWavFile(wavFile)
        );

        System.out.println("==== recording(12) safety floor sweep " + label + " ====");
        for (int floor : FLOOR_SWEEP) {
            SqlThresholdModel.setSafetyFloorThresholdForTesting(floor);
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed;
            try {
                detailed = LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "recording12-safety-floor-" + label + "-" + floor,
                        frames,
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        sqlPercent,
                        false,
                        false,
                        false,
                        false,
                        false,
                        CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );
            } finally {
                SqlThresholdModel.clearSafetyFloorThresholdForTesting();
            }

            System.out.println(String.format(
                    Locale.US,
                    "floor=%d effective=%d trust=%d firstActive=%d firstAccepted=%d firstLock=%d firstAttackQualified=%d firstSymbol=%s firstChar=%s prefix=%s",
                    floor,
                    floor,
                    firstTrustTimestampMs(detailed.timingStateTraces()),
                    firstToneActiveMs(detailed.frameSignalTraces()),
                    firstToneAcceptedMs(detailed.frameSignalTraces()),
                    firstTargetLockMs(detailed.frameSignalTraces()),
                    firstAttackQualifiedMs(detailed.frameSignalTraces()),
                    summarizeFirstSymbol(detailed.decodeEvents()),
                    summarizeFirstCharacter(detailed.decodeEvents()),
                    sanitize(textAtOrBefore(detailed.decodeEvents(), PREFIX_CUTOFF_MS))
            ));
            for (long sampleTimestampMs : SAMPLE_TIMESTAMPS_MS) {
                LocalAudioDecodeTestSupport.FrameSignalTrace trace =
                        traceAtOrAfter(detailed.frameSignalTraces(), sampleTimestampMs);
                if (trace == null || trace.snapshot() == null) {
                    continue;
                }
                System.out.println(String.format(
                        Locale.US,
                        "  @%d det=%.1f thr=%d/%d tone=%.1f dom=%.2f iso=%.2f lc=%.2f on=%s lock=%s aq=%s toneOn=%s",
                        sampleTimestampMs,
                        trace.detectionLevel(),
                        trace.snapshot().currentThreshold(),
                        trace.snapshot().releaseThreshold(),
                        trace.snapshot().lastToneRmsAmplitude(),
                        trace.snapshot().toneDominanceRatio(),
                        trace.snapshot().narrowbandIsolationRatio(),
                        trace.localContrastRatio(),
                        yesNo(trace.snapshot().toneActive()),
                        yesNo(trace.snapshot().targetToneLocked()),
                        yesNo(trace.attackQualified()),
                        compact(trace.toneOnDecision())
                ));
            }
        }
    }

    private static long firstTrustTimestampMs(List<LocalAudioDecodeTestSupport.TimingStateTrace> traces) {
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : traces) {
            if (trace != null
                    && trace.debugSnapshot() != null
                    && trace.debugSnapshot().trustedDotEstimateMs() > 0.0d) {
                return trace.timestampMs();
            }
        }
        return -1L;
    }

    private static long firstToneActiveMs(List<LocalAudioDecodeTestSupport.FrameSignalTrace> traces) {
        for (LocalAudioDecodeTestSupport.FrameSignalTrace trace : traces) {
            if (trace != null && trace.snapshot() != null && trace.snapshot().toneActive()) {
                return trace.timestampMs();
            }
        }
        return -1L;
    }

    private static long firstToneAcceptedMs(List<LocalAudioDecodeTestSupport.FrameSignalTrace> traces) {
        for (LocalAudioDecodeTestSupport.FrameSignalTrace trace : traces) {
            if (trace != null && trace.toneOnAccepted()) {
                return trace.timestampMs();
            }
        }
        return -1L;
    }

    private static long firstTargetLockMs(List<LocalAudioDecodeTestSupport.FrameSignalTrace> traces) {
        for (LocalAudioDecodeTestSupport.FrameSignalTrace trace : traces) {
            if (trace != null && trace.snapshot() != null && trace.snapshot().targetToneLocked()) {
                return trace.timestampMs();
            }
        }
        return -1L;
    }

    private static long firstAttackQualifiedMs(List<LocalAudioDecodeTestSupport.FrameSignalTrace> traces) {
        for (LocalAudioDecodeTestSupport.FrameSignalTrace trace : traces) {
            if (trace != null && trace.attackQualified()) {
                return trace.timestampMs();
            }
        }
        return -1L;
    }

    private static String summarizeFirstSymbol(List<CwDecodeEvent> decodeEvents) {
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            if (decodeEvent == null || decodeEvent.timestampMs() > PREFIX_CUTOFF_MS) {
                continue;
            }
            if (decodeEvent.type() == CwDecodeEvent.Type.SYMBOL_APPENDED) {
                return "@" + decodeEvent.timestampMs()
                        + ":" + sanitize(decodeEvent.emittedValue())
                        + "/" + sanitize(decodeEvent.sourceSequence());
            }
        }
        return "none";
    }

    private static String summarizeFirstCharacter(List<CwDecodeEvent> decodeEvents) {
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            if (decodeEvent == null || decodeEvent.timestampMs() > PREFIX_CUTOFF_MS) {
                continue;
            }
            if (decodeEvent.type() == CwDecodeEvent.Type.CHARACTER_DECODED) {
                return "@" + decodeEvent.timestampMs()
                        + ":" + sanitize(decodeEvent.emittedValue())
                        + "/" + sanitize(decodeEvent.sourceSequence());
            }
        }
        return "none";
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

    private static LocalAudioDecodeTestSupport.FrameSignalTrace traceAtOrAfter(
            List<LocalAudioDecodeTestSupport.FrameSignalTrace> traces,
            long timestampMs
    ) {
        for (LocalAudioDecodeTestSupport.FrameSignalTrace trace : traces) {
            if (trace != null && trace.timestampMs() >= timestampMs) {
                return trace;
            }
        }
        return null;
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "(empty)";
        }
        String normalized = text.replace('\u25A1', '?').replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.isEmpty() ? "(empty)" : normalized;
    }

    private static String compact(String text) {
        if (text == null) {
            return "NONE";
        }
        String normalized = text.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.isEmpty() ? "NONE" : normalized;
    }

    private static String yesNo(boolean value) {
        return value ? "Y" : "N";
    }
}
