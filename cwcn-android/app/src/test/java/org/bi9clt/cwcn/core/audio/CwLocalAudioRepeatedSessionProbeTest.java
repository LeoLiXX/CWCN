package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.decoder.CwDecoder;
import org.bi9clt.cwcn.core.eval.CwFixtureLibrary;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.qso.QsoStateMachine;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel.DebugSnapshot;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class CwLocalAudioRepeatedSessionProbeTest {
    private static final int FRAME_SIZE_SAMPLES = 256;
    private static final int REPEAT_COUNT = 3;
    private static final long INTER_REPEAT_SILENCE_MS = 8000L;
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int OFF_TARGET_PREFERRED_TONE_HZ = 650;
    private static final int SEED_WPM = 15;
    private static final int OFF_TARGET_SEED_WPM = 18;
    private static final int SQL_PERCENT = 55;
    private static final int[] SEED_SWEEP_WPMS = new int[]{0, 12, 15, 18, 20, 24, 28};
    private static final String EXPECTED_CQ_24WPM_TEXT =
            "CQ CQ CQ DE BI9CXC BI9CXC BI9CXC IN 700 24WPM PSE K.";
    private static final long RECORDING16_MAX_INIT_OFFSET_MS = 2600L;
    private static final long CAPTURE_TURN_MAX_INIT_OFFSET_MS = 1800L;
    private static final double TRUSTED_DOT_24WPM_MIN_MS = 44.0d;
    private static final double TRUSTED_DOT_24WPM_MAX_MS = 56.0d;

    @Test
    public void printRepeatedLiveLikeProbeForRecording16() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(16).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (16)"));
        LocalAudioDecodeTestSupport.WaveDataProbe waveData =
                LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);
        runRepeatedLiveLikeProbe(
                "录音 (16)",
                buildFrames(waveData.samples(), waveData.sampleRateHz()),
                PREFERRED_TONE_HZ
        );
    }

    @Test
    public void printRepeatedLiveLikeProbeForRecording16_withOffTargetSeedAndTone() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(16).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (16)"));
        LocalAudioDecodeTestSupport.WaveDataProbe waveData =
                LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);
        runRepeatedLiveLikeProbe(
                "录音 (16) pref650 seed18",
                buildFrames(waveData.samples(), waveData.sampleRateHz()),
                OFF_TARGET_PREFERRED_TONE_HZ,
                OFF_TARGET_SEED_WPM,
                SQL_PERCENT
        );
    }

    @Test
    public void printRepeatedLiveLikeProbeForCapturedTraceWav() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.findTraceDir().resolve("capture.wav");
        if (!Files.isRegularFile(wavFile)) {
            throw new IllegalStateException("Missing captured trace wav: " + wavFile);
        }
        LocalAudioDecodeTestSupport.WaveDataProbe waveData =
                LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);
        runNaturalTurnProbe(
                "Trace capture.wav",
                buildFrames(waveData.samples(), waveData.sampleRateHz()),
                PREFERRED_TONE_HZ
        );
    }

    @Test
    public void printRecording16BootstrapInitOffset() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(16).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (16)"));
        LocalAudioDecodeTestSupport.WaveDataProbe waveData =
                LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "录音 (16)",
                        normalizeFramesToZero(buildFrames(waveData.samples(), waveData.sampleRateHz())),
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        org.bi9clt.cwcn.core.interpreter.CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        LocalAudioDecodeTestSupport.TimingStateTrace initTrace = firstTrustedTimingTraceInWindow(
                detailed.timingStateTraces(),
                0L,
                detailed.flushTimestampMs()
        );
        long initOffsetMs = initTrace == null ? -1L : initTrace.timestampMs();
        String preInitText = initTrace == null
                ? ""
                : sliceNewText(
                textAtOrBefore(detailed.decodeEvents(), 0L),
                textAtOrBefore(detailed.decodeEvents(), initTrace.timestampMs())
        );
        String initReason = initTrace == null || initTrace.debugSnapshot() == null
                ? "none"
                : safe(initTrace.debugSnapshot().lastTrustedUpdateReason());
        double initTrustedDotMs = initTrace == null || initTrace.debugSnapshot() == null
                ? 0.0d
                : initTrace.debugSnapshot().trustedDotEstimateMs();
        System.out.println(String.format(
                Locale.US,
                "recording16 initOffset=%dms initReason=%s trusted=%.1fms preInitText=\"%s\" finalText=\"%s\"",
                initOffsetMs,
                initReason,
                initTrustedDotMs,
                sanitize(preInitText),
                sanitize(detailed.probeResult().decodedText())
        ));
    }

    @Test
    public void recording16BootstrapTrustBuildsWithinOpeningWindow() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(16).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (16)"));
        LocalAudioDecodeTestSupport.WaveDataProbe waveData =
                LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "录音 (16)",
                        normalizeFramesToZero(buildFrames(waveData.samples(), waveData.sampleRateHz())),
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        org.bi9clt.cwcn.core.interpreter.CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        LocalAudioDecodeTestSupport.TimingStateTrace initTrace = firstTrustedTimingTraceInWindow(
                detailed.timingStateTraces(),
                0L,
                detailed.flushTimestampMs()
        );
        long initOffsetMs = initTrace == null ? -1L : initTrace.timestampMs();
        String preInitText = initTrace == null
                ? ""
                : sliceNewText(
                textAtOrBefore(detailed.decodeEvents(), 0L),
                textAtOrBefore(detailed.decodeEvents(), initTrace.timestampMs())
        );
        assertBootstrapTrustWithinOpeningWindow(
                "recording16",
                initTrace,
                initOffsetMs,
                preInitText,
                RECORDING16_MAX_INIT_OFFSET_MS
        );
    }

    @Test
    public void recording16PreTrustPrefixStaysClean() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(16).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (16)"));
        LocalAudioDecodeTestSupport.WaveDataProbe waveData =
                LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "录音 (16)",
                        normalizeFramesToZero(buildFrames(waveData.samples(), waveData.sampleRateHz())),
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        org.bi9clt.cwcn.core.interpreter.CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        LocalAudioDecodeTestSupport.TimingStateTrace initTrace = firstTrustedTimingTraceInWindow(
                detailed.timingStateTraces(),
                0L,
                detailed.flushTimestampMs()
        );
        assertPreTrustPrefixQuality(
                "recording16",
                detailed.decodeEvents(),
                0L,
                initTrace == null ? detailed.flushTimestampMs() : initTrace.timestampMs()
        );
    }

    @Test
    public void printCapturedTraceSeedSensitivityProbe() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.findTraceDir().resolve("capture.wav");
        if (!Files.isRegularFile(wavFile)) {
            throw new IllegalStateException("Missing captured trace wav: " + wavFile);
        }
        LocalAudioDecodeTestSupport.WaveDataProbe waveData =
                LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);
        List<AudioFrame> frames = buildFrames(waveData.samples(), waveData.sampleRateHz());
        runNaturalTurnProbe("Trace capture.wav", frames, PREFERRED_TONE_HZ, 15);
        runNaturalTurnProbe("Trace capture.wav", frames, PREFERRED_TONE_HZ, 20);
        runNaturalTurnProbe("Trace capture.wav", frames, PREFERRED_TONE_HZ, 24);
    }

    @Test
    public void printRecording16AndCapturedTraceSeedSweepProbe() throws Exception {
        Path recording16WavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(16).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (16)"));
        LocalAudioDecodeTestSupport.WaveDataProbe recording16WaveData =
                LocalAudioDecodeTestSupport.readWaveFileForProbe(recording16WavFile);
        List<AudioFrame> recording16Frames = buildFrames(
                recording16WaveData.samples(),
                recording16WaveData.sampleRateHz()
        );

        Path captureWavFile = LocalAudioDecodeTestSupport.findTraceDir().resolve("capture.wav");
        if (!Files.isRegularFile(captureWavFile)) {
            throw new IllegalStateException("Missing captured trace wav: " + captureWavFile);
        }
        LocalAudioDecodeTestSupport.WaveDataProbe captureWaveData =
                LocalAudioDecodeTestSupport.readWaveFileForProbe(captureWavFile);
        List<AudioFrame> captureFrames = buildFrames(
                captureWaveData.samples(),
                captureWaveData.sampleRateHz()
        );

        runSeedSweepProbe("录音 (16)", recording16Frames, PREFERRED_TONE_HZ);
        runSeedSweepProbe("Trace capture.wav", captureFrames, PREFERRED_TONE_HZ);
    }

    @Test
    public void capturedTraceTurnsStartFromFreshSeedWithoutCrossTurnCarry() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.findTraceDir().resolve("capture.wav");
        if (!Files.isRegularFile(wavFile)) {
            throw new IllegalStateException("Missing captured trace wav: " + wavFile);
        }
        LocalAudioDecodeTestSupport.WaveDataProbe waveData =
                LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "Trace capture.wav",
                        normalizeFramesToZero(buildFrames(waveData.samples(), waveData.sampleRateHz())),
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        org.bi9clt.cwcn.core.interpreter.CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        List<TurnWindow> turnWindows = buildTurnWindows(detailed);
        assertEquals(3, turnWindows.size());
        for (TurnWindow turnWindow : turnWindows) {
            assertNotNull(turnWindow.startTrace());
            assertEquals(SEED_WPM, turnWindow.startTrace().turnSeedWpm());
            assertEquals(0, turnWindow.startTrace().retainedTurnAnchorWpm());
            if (turnWindow.endTrace() != null) {
                assertEquals(0, turnWindow.endTrace().retainedTurnAnchorWpm());
            }
        }
    }

    @Test
    public void capturedTraceNaturalTurnsDecodeExactExpectedText() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.findTraceDir().resolve("capture.wav");
        if (!Files.isRegularFile(wavFile)) {
            throw new IllegalStateException("Missing captured trace wav: " + wavFile);
        }
        LocalAudioDecodeTestSupport.WaveDataProbe waveData =
                LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "Trace capture.wav",
                        normalizeFramesToZero(buildFrames(waveData.samples(), waveData.sampleRateHz())),
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        org.bi9clt.cwcn.core.interpreter.CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        List<TurnWindow> turnWindows = buildTurnWindows(detailed);
        assertEquals(3, turnWindows.size());
        for (TurnWindow turnWindow : turnWindows) {
            TurnSummary summary = summarizeTurn(detailed, turnWindow, EXPECTED_CQ_24WPM_TEXT);
            assertEquals(
                    "turn " + turnWindow.turnIndex() + " decoded text",
                    EXPECTED_CQ_24WPM_TEXT,
                    sanitize(summary.settledTurnText())
            );
        }
        assertEquals(
                EXPECTED_CQ_24WPM_TEXT + EXPECTED_CQ_24WPM_TEXT + EXPECTED_CQ_24WPM_TEXT,
                sanitize(detailed.probeResult().decodedText())
        );
    }

    @Test
    public void capturedTraceTurnsBuildTrustWithinOpeningWindow() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.findTraceDir().resolve("capture.wav");
        if (!Files.isRegularFile(wavFile)) {
            throw new IllegalStateException("Missing captured trace wav: " + wavFile);
        }
        LocalAudioDecodeTestSupport.WaveDataProbe waveData =
                LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "Trace capture.wav",
                        normalizeFramesToZero(buildFrames(waveData.samples(), waveData.sampleRateHz())),
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        org.bi9clt.cwcn.core.interpreter.CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        List<TurnWindow> turnWindows = buildTurnWindows(detailed);
        assertEquals(3, turnWindows.size());
        for (TurnWindow turnWindow : turnWindows) {
            LocalAudioDecodeTestSupport.TimingStateTrace initTrace = firstTrustedTimingTraceInWindow(
                    detailed.timingStateTraces(),
                    turnWindow.turnStartTimestampMs(),
                    turnWindow.turnEndTimestampMs()
            );
            long initOffsetMs = initTrace == null
                    ? -1L
                    : Math.max(0L, initTrace.timestampMs() - turnWindow.turnStartTimestampMs());
            String preInitText = initTrace == null
                    ? ""
                    : sliceNewText(
                    textAtOrBefore(detailed.decodeEvents(), turnWindow.turnStartTimestampMs()),
                    textAtOrBefore(detailed.decodeEvents(), initTrace.timestampMs())
            );
            assertBootstrapTrustWithinOpeningWindow(
                    "capture turn " + turnWindow.turnIndex(),
                    initTrace,
                    initOffsetMs,
                    preInitText,
                    CAPTURE_TURN_MAX_INIT_OFFSET_MS
            );
        }
    }

    @Test
    public void capturedTracePreTrustPrefixesStayClean() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.findTraceDir().resolve("capture.wav");
        if (!Files.isRegularFile(wavFile)) {
            throw new IllegalStateException("Missing captured trace wav: " + wavFile);
        }
        LocalAudioDecodeTestSupport.WaveDataProbe waveData =
                LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "Trace capture.wav",
                        normalizeFramesToZero(buildFrames(waveData.samples(), waveData.sampleRateHz())),
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        org.bi9clt.cwcn.core.interpreter.CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        for (TurnWindow turnWindow : buildTurnWindows(detailed)) {
            LocalAudioDecodeTestSupport.TimingStateTrace initTrace = firstTrustedTimingTraceInWindow(
                    detailed.timingStateTraces(),
                    turnWindow.turnStartTimestampMs(),
                    turnWindow.turnEndTimestampMs()
            );
            assertPreTrustPrefixQuality(
                    "capture turn " + turnWindow.turnIndex(),
                    detailed.decodeEvents(),
                    turnWindow.turnStartTimestampMs(),
                    initTrace == null ? turnWindow.turnEndTimestampMs() : initTrace.timestampMs()
            );
        }
    }

    @Test
    public void printCapturedTraceBootstrapInitOffsets() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.findTraceDir().resolve("capture.wav");
        if (!Files.isRegularFile(wavFile)) {
            throw new IllegalStateException("Missing captured trace wav: " + wavFile);
        }
        LocalAudioDecodeTestSupport.WaveDataProbe waveData =
                LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "Trace capture.wav",
                        normalizeFramesToZero(buildFrames(waveData.samples(), waveData.sampleRateHz())),
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        org.bi9clt.cwcn.core.interpreter.CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        System.out.println("==== capture bootstrap init offsets ====");
        for (TurnWindow turnWindow : buildTurnWindows(detailed)) {
            LocalAudioDecodeTestSupport.TimingStateTrace initTrace = firstTrustedTimingTraceInWindow(
                    detailed.timingStateTraces(),
                    turnWindow.turnStartTimestampMs(),
                    turnWindow.turnEndTimestampMs()
            );
            long initOffsetMs = initTrace == null
                    ? -1L
                    : Math.max(0L, initTrace.timestampMs() - turnWindow.turnStartTimestampMs());
            String preInitText = initTrace == null
                    ? ""
                    : sliceNewText(
                    textAtOrBefore(detailed.decodeEvents(), turnWindow.turnStartTimestampMs()),
                    textAtOrBefore(detailed.decodeEvents(), initTrace.timestampMs())
            );
            String initReason = initTrace == null || initTrace.debugSnapshot() == null
                    ? "none"
                    : safe(initTrace.debugSnapshot().lastTrustedUpdateReason());
            double initTrustedDotMs = initTrace == null || initTrace.debugSnapshot() == null
                    ? 0.0d
                    : initTrace.debugSnapshot().trustedDotEstimateMs();
            System.out.println(String.format(
                    Locale.US,
                    "TURN %d initOffset=%dms initReason=%s trusted=%.1fms preInitText=\"%s\"",
                    turnWindow.turnIndex(),
                    initOffsetMs,
                    initReason,
                    initTrustedDotMs,
                    sanitize(preInitText)
            ));
        }
    }

    @Test
    public void printCapturedTraceCommittedRawConsistencyProbe() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.findTraceDir().resolve("capture.wav");
        if (!Files.isRegularFile(wavFile)) {
            throw new IllegalStateException("Missing captured trace wav: " + wavFile);
        }
        LocalAudioDecodeTestSupport.WaveDataProbe waveData =
                LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "Trace capture.wav",
                        normalizeFramesToZero(buildFrames(waveData.samples(), waveData.sampleRateHz())),
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        org.bi9clt.cwcn.core.interpreter.CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        String probeDecodedText = sanitize(detailed.probeResult().decodedText());
        String interpreterSnapshotText = sanitize(
                detailed.probeResult().interpreterSnapshot().rawText()
        );
        String decoderSnapshotText = sanitize(
                detailed.probeResult().decoderSnapshot().decodedText()
        );
        String finalOnlyText = sanitize(textAtOrBeforeFinalOnly(
                detailed.decodeEvents(),
                detailed.flushTimestampMs()
        ));
        String allEventsText = sanitize(textAtOrBefore(
                detailed.decodeEvents(),
                detailed.flushTimestampMs()
        ));

        System.out.println("==== capture committed raw consistency ====");
        System.out.println(String.format(
                Locale.US,
                "final probeDecoded=\"%s\"",
                probeDecodedText
        ));
        System.out.println(String.format(
                Locale.US,
                "final interpreter=\"%s\"",
                interpreterSnapshotText
        ));
        System.out.println(String.format(
                Locale.US,
                "final decoder=\"%s\"",
                decoderSnapshotText
        ));
        System.out.println(String.format(
                Locale.US,
                "final final-only =\"%s\"",
                finalOnlyText
        ));
        System.out.println(String.format(
                Locale.US,
                "final all-events =\"%s\"",
                allEventsText
        ));

        List<String> turnAllSlices = new ArrayList<>();
        List<String> turnFinalSlices = new ArrayList<>();
        for (TurnWindow turnWindow : buildTurnWindows(detailed)) {
            String allAtStart = textAtOrBefore(detailed.decodeEvents(), turnWindow.turnStartTimestampMs());
            String allAtEnd = textAtOrBefore(detailed.decodeEvents(), turnWindow.turnEndTimestampMs());
            String finalAtStart = textAtOrBeforeFinalOnly(
                    detailed.decodeEvents(),
                    turnWindow.turnStartTimestampMs()
            );
            String finalAtEnd = textAtOrBeforeFinalOnly(
                    detailed.decodeEvents(),
                    turnWindow.turnEndTimestampMs()
            );
            System.out.println(String.format(
                    Locale.US,
                    "TURN %d allSlice=\"%s\" finalSlice=\"%s\"",
                    turnWindow.turnIndex(),
                    sanitize(sliceNewText(allAtStart, allAtEnd)),
                    sanitize(sliceNewText(finalAtStart, finalAtEnd))
            ));
            turnAllSlices.add(sanitize(sliceNewText(allAtStart, allAtEnd)));
            turnFinalSlices.add(sanitize(sliceNewText(finalAtStart, finalAtEnd)));
        }
        System.out.println(String.format(
                Locale.US,
                "turn-separated all-events =\"%s\"",
                String.join(" | ", turnAllSlices)
        ));
        System.out.println(String.format(
                Locale.US,
                "turn-separated final-only =\"%s\"",
                String.join(" | ", turnFinalSlices)
        ));

        long previousTimestampMs = Long.MIN_VALUE;
        int printed = 0;
        for (CwDecodeEvent decodeEvent : detailed.decodeEvents()) {
            if (decodeEvent == null || decodeEvent.timestampMs() > 9000L) {
                continue;
            }
            boolean outOfOrder = decodeEvent.timestampMs() < previousTimestampMs;
            System.out.println(String.format(
                    Locale.US,
                    "#%02d %s%s @%d emit=%s seq=%s out=%s unknown=%s",
                    printed,
                    decodeEvent.type(),
                    outOfOrder ? "[out-of-order]" : "",
                    decodeEvent.timestampMs(),
                    sanitize(decodeEvent.emittedValue()),
                    sanitize(decodeEvent.sourceSequence()),
                    sanitize(decodeEvent.outputText()),
                    decodeEvent.unknownCharacter()
            ));
            previousTimestampMs = Math.max(previousTimestampMs, decodeEvent.timestampMs());
            printed += 1;
            if (printed >= 40) {
                break;
            }
        }
    }

    @Test
    public void printCapturedTraceBoundaryBootstrapTimeline() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.findTraceDir().resolve("capture.wav");
        if (!Files.isRegularFile(wavFile)) {
            throw new IllegalStateException("Missing captured trace wav: " + wavFile);
        }
        LocalAudioDecodeTestSupport.WaveDataProbe waveData =
                LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "Trace capture.wav",
                        normalizeFramesToZero(buildFrames(waveData.samples(), waveData.sampleRateHz())),
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        org.bi9clt.cwcn.core.interpreter.CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        System.out.println("==== capture boundary bootstrap timeline ====");
        for (TurnWindow turnWindow : buildTurnWindows(detailed)) {
            LocalAudioDecodeTestSupport.TimingStateTrace pendingTrace = firstTimingTraceWithReasonPrefix(
                    detailed.timingStateTraces(),
                    turnWindow.turnStartTimestampMs(),
                    turnWindow.turnEndTimestampMs(),
                    "pending-boundary("
            );
            LocalAudioDecodeTestSupport.TimingStateTrace initTrace = firstTimingTraceWithReasonPrefix(
                    detailed.timingStateTraces(),
                    turnWindow.turnStartTimestampMs(),
                    turnWindow.turnEndTimestampMs(),
                    "init-boundary"
            );
            long pendingOffsetMs = pendingTrace == null
                    ? -1L
                    : Math.max(0L, pendingTrace.timestampMs() - turnWindow.turnStartTimestampMs());
            long initOffsetMs = initTrace == null
                    ? -1L
                    : Math.max(0L, initTrace.timestampMs() - turnWindow.turnStartTimestampMs());
            System.out.println(String.format(
                    Locale.US,
                    "TURN %d pendingBoundary=%dms initBoundary=%dms pendingReason=%s initReason=%s",
                    turnWindow.turnIndex(),
                    pendingOffsetMs,
                    initOffsetMs,
                    pendingTrace == null || pendingTrace.debugSnapshot() == null
                            ? "none"
                            : safe(pendingTrace.debugSnapshot().lastTrustedUpdateReason()),
                    initTrace == null || initTrace.debugSnapshot() == null
                            ? "none"
                            : safe(initTrace.debugSnapshot().lastTrustedUpdateReason())
            ));
        }
    }

    @Test
    public void printCapturedTraceBoundaryReasonTimeline() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.findTraceDir().resolve("capture.wav");
        if (!Files.isRegularFile(wavFile)) {
            throw new IllegalStateException("Missing captured trace wav: " + wavFile);
        }
        LocalAudioDecodeTestSupport.WaveDataProbe waveData =
                LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "Trace capture.wav",
                        normalizeFramesToZero(buildFrames(waveData.samples(), waveData.sampleRateHz())),
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        org.bi9clt.cwcn.core.interpreter.CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        System.out.println("==== capture boundary reason timeline ====");
        for (TurnWindow turnWindow : buildTurnWindows(detailed)) {
            System.out.println(String.format(Locale.US, "TURN %d", turnWindow.turnIndex()));
            for (LocalAudioDecodeTestSupport.TimingStateTrace trace : detailed.timingStateTraces()) {
                if (trace == null
                        || trace.timestampMs() < turnWindow.turnStartTimestampMs()
                        || trace.timestampMs() > turnWindow.turnEndTimestampMs()
                        || trace.debugSnapshot() == null) {
                    continue;
                }
                String reason = safe(trace.debugSnapshot().lastTrustedUpdateReason());
                if (!reason.startsWith("pending-boundary(")
                        && !reason.startsWith("init-boundary")
                        && !reason.startsWith("init(")) {
                    continue;
                }
                System.out.println(String.format(
                        Locale.US,
                        "  @%dms reason=%s trusted=%.1f raw=%.2f dsp=%s",
                        Math.max(0L, trace.timestampMs() - turnWindow.turnStartTimestampMs()),
                        reason,
                        trace.debugSnapshot().trustedDotEstimateMs(),
                        snapshotWpm(trace.rawSnapshot()),
                        safe(trace.debugSummary())
                ));
            }
        }
    }

    @Test
    public void printCapturedTracePreTrustGapTimeline() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.findTraceDir().resolve("capture.wav");
        if (!Files.isRegularFile(wavFile)) {
            throw new IllegalStateException("Missing captured trace wav: " + wavFile);
        }
        LocalAudioDecodeTestSupport.WaveDataProbe waveData =
                LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "Trace capture.wav",
                        normalizeFramesToZero(buildFrames(waveData.samples(), waveData.sampleRateHz())),
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        org.bi9clt.cwcn.core.interpreter.CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        System.out.println("==== capture pre-trust gap timeline ====");
        for (TurnWindow turnWindow : buildTurnWindows(detailed)) {
            LocalAudioDecodeTestSupport.TimingStateTrace initTrace = firstTrustedTimingTraceInWindow(
                    detailed.timingStateTraces(),
                    turnWindow.turnStartTimestampMs(),
                    turnWindow.turnEndTimestampMs()
            );
            long trustCutoffMs = initTrace == null
                    ? turnWindow.turnEndTimestampMs()
                    : initTrace.timestampMs();
            System.out.println(String.format(
                    Locale.US,
                    "TURN %d trustCutoff=%dms",
                    turnWindow.turnIndex(),
                    Math.max(0L, trustCutoffMs - turnWindow.turnStartTimestampMs())
            ));
            int printed = 0;
            for (org.bi9clt.cwcn.core.timing.CwTimingEvent timingEvent : detailed.timingEvents()) {
                if (timingEvent == null
                        || timingEvent.kind() != org.bi9clt.cwcn.core.timing.CwTimingEvent.Kind.GAP
                        || timingEvent.timestampMs() < turnWindow.turnStartTimestampMs()
                        || timingEvent.timestampMs() > trustCutoffMs) {
                    continue;
                }
                LocalAudioDecodeTestSupport.FrameSignalTrace signalTrace =
                        lastFrameSignalTraceAtOrBefore(detailed.frameSignalTraces(), timingEvent.timestampMs());
                LocalAudioDecodeTestSupport.TimingStateTrace timingTrace =
                        lastTimingTraceAtOrBefore(detailed.timingStateTraces(), timingEvent.timestampMs());
                CwSignalSnapshot signalSnapshot = signalTrace == null ? null : signalTrace.snapshot();
                CwTimingSnapshot timingSnapshot = timingTrace == null ? null : timingTrace.rawSnapshot();
                System.out.println(String.format(
                        Locale.US,
                        "  gap@%dms cls=%s dur=%d dot=%d raw=%.2f lock=%.0f near=%.0f unl=%.0f dom=%.0f iso=%.0f",
                        Math.max(0L, timingEvent.timestampMs() - turnWindow.turnStartTimestampMs()),
                        timingEvent.classification().name(),
                        timingEvent.durationMs(),
                        timingEvent.dotEstimateMs(),
                        snapshotWpm(timingSnapshot),
                        signalSnapshot == null ? 0.0d : signalSnapshot.recentLockedFrameRatio() * 100.0d,
                        signalSnapshot == null ? 0.0d : signalSnapshot.recentNearTargetLockedFrameRatio() * 100.0d,
                        signalSnapshot == null ? 0.0d : signalSnapshot.recentActiveUnlockedFrameRatio() * 100.0d,
                        signalSnapshot == null ? 0.0d : signalSnapshot.toneDominanceRatio() * 100.0d,
                        signalSnapshot == null ? 0.0d : signalSnapshot.narrowbandIsolationRatio() * 100.0d
                ));
                printed += 1;
                if (printed >= 18) {
                    break;
                }
            }
            if (printed == 0) {
                System.out.println("  gap=(none)");
            }
        }
    }

    @Test
    public void printCapturedTraceForcedAnchorPrefixReplay() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.findTraceDir().resolve("capture.wav");
        if (!Files.isRegularFile(wavFile)) {
            throw new IllegalStateException("Missing captured trace wav: " + wavFile);
        }
        LocalAudioDecodeTestSupport.WaveDataProbe waveData =
                LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        "Trace capture.wav",
                        normalizeFramesToZero(buildFrames(waveData.samples(), waveData.sampleRateHz())),
                        PREFERRED_TONE_HZ,
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        org.bi9clt.cwcn.core.interpreter.CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        System.out.println("==== capture forced-anchor prefix replay ====");
        for (TurnWindow turnWindow : buildTurnWindows(detailed)) {
            LocalAudioDecodeTestSupport.TimingStateTrace initTrace = firstTrustedTimingTraceInWindow(
                    detailed.timingStateTraces(),
                    turnWindow.turnStartTimestampMs(),
                    turnWindow.turnEndTimestampMs()
            );
            long trustedDotMs = initTrace == null || initTrace.debugSnapshot() == null
                    ? 0L
                    : Math.round(initTrace.debugSnapshot().trustedDotEstimateMs());
            long trustCutoffMs = initTrace == null
                    ? turnWindow.turnEndTimestampMs()
                    : initTrace.timestampMs();
            String originalPrefixText = initTrace == null
                    ? ""
                    : sliceNewText(
                    textAtOrBefore(detailed.decodeEvents(), turnWindow.turnStartTimestampMs()),
                    textAtOrBefore(detailed.decodeEvents(), trustCutoffMs)
            );
            String forcedPrefixText = replayTimingWindowWithForcedDot(
                    detailed,
                    turnWindow.turnStartTimestampMs(),
                    trustCutoffMs,
                    trustedDotMs
            );
            String forcedWholeTurnText = replayTimingWindowWithForcedDot(
                    detailed,
                    turnWindow.turnStartTimestampMs(),
                    turnWindow.turnEndTimestampMs(),
                    trustedDotMs
            );
            System.out.println(String.format(
                    Locale.US,
                    "TURN %d trustedDot=%dms originalPrefix=\"%s\" forcedPrefix=\"%s\" forcedWhole=\"%s\"",
                    turnWindow.turnIndex(),
                    trustedDotMs,
                    sanitize(originalPrefixText),
                    sanitize(forcedPrefixText),
                    sanitize(forcedWholeTurnText)
            ));
        }
    }

    @Test
    public void printRepeatedLiveLikeProbeForUserNoiseFixture25wpm() {
        runSyntheticFixtureProbe("user_noise_cq_25wpm_700hz");
    }

    @Test
    public void userNoiseFixtureEstablishesTrustedTimingEachRound() {
        CwFixtureScenario scenario = findScenario("user_noise_cq_25wpm_700hz");
        List<AudioFrame> frames = new SyntheticFixtureRxAudioSource().renderFramesForTesting(scenario);
        RepeatedTimeline timeline = buildRepeatedTimeline(frames);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        scenario.id() + " / " + scenario.displayName(),
                        timeline.sessionFrames(),
                        scenario.toneFrequencyHz(),
                        SEED_WPM,
                        SQL_PERCENT,
                        false,
                        org.bi9clt.cwcn.core.interpreter.CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        String referenceSettledRoundText = null;
        for (RoundWindow roundWindow : timeline.rounds()) {
            RoundSummary summary = summarizeRound(detailed, roundWindow, referenceSettledRoundText);
            if (referenceSettledRoundText == null) {
                referenceSettledRoundText = summary.settledRoundText();
            }
            DebugSnapshot debugSnapshot = summary.roundEndTimingTrace == null
                    ? null
                    : summary.roundEndTimingTrace.debugSnapshot();
            assertTrue(
                    "round " + roundWindow.roundIndex() + " trusted timing should be established",
                    debugSnapshot != null && debugSnapshot.trustedDotEstimateMs() > 0.0d
            );
        }
    }

    @Test
    public void printRepeatedLiveLikeProbeForUsbLowLevelFixture18wpm() {
        runSyntheticFixtureProbe("usb_low_level_cq_18wpm_700hz");
    }

    private static void runSyntheticFixtureProbe(String scenarioId) {
        CwFixtureScenario scenario = findScenario(scenarioId);
        List<AudioFrame> frames = new SyntheticFixtureRxAudioSource().renderFramesForTesting(scenario);
        runRepeatedLiveLikeProbe(
                scenario.id() + " / " + scenario.displayName(),
                frames,
                scenario.toneFrequencyHz()
        );
    }

    private static CwFixtureScenario findScenario(String scenarioId) {
        for (CwFixtureScenario scenario : CwFixtureLibrary.scenarios()) {
            if (scenario.id().equals(scenarioId)) {
                return scenario;
            }
        }
        throw new IllegalArgumentException("Unknown scenario: " + scenarioId);
    }

    private static void runRepeatedLiveLikeProbe(
            String sourceLabel,
            List<AudioFrame> baseFrames,
            int preferredToneHz
    ) {
        runRepeatedLiveLikeProbe(
                sourceLabel,
                baseFrames,
                preferredToneHz,
                SEED_WPM,
                SQL_PERCENT
        );
    }

    private static void runRepeatedLiveLikeProbe(
            String sourceLabel,
            List<AudioFrame> baseFrames,
            int preferredToneHz,
            int seedWpm,
            int sqlPercent
    ) {
        if (baseFrames == null || baseFrames.isEmpty()) {
            throw new IllegalArgumentException("baseFrames is empty");
        }

        RepeatedTimeline timeline = buildRepeatedTimeline(baseFrames);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        sourceLabel,
                        timeline.sessionFrames(),
                        preferredToneHz,
                        seedWpm,
                        sqlPercent,
                        false,
                        org.bi9clt.cwcn.core.interpreter.CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        System.out.println(String.format(
                Locale.US,
                "==== repeated live-like session probe: %s | repeats=%d | silence=%dms | seed=%dwpm | sql=%d ====",
                sourceLabel,
                REPEAT_COUNT,
                INTER_REPEAT_SILENCE_MS,
                seedWpm,
                sqlPercent
        ));

        String referenceSettledRoundText = null;
        for (RoundWindow roundWindow : timeline.rounds()) {
            RoundSummary summary = summarizeRound(detailed, roundWindow, referenceSettledRoundText);
            System.out.println(summary.renderRoundEnd());
            if (roundWindow.hasPostSilence()) {
                System.out.println(summary.renderPostSilence());
            }
            if (referenceSettledRoundText == null) {
                referenceSettledRoundText = summary.settledRoundText();
            }
        }

        LocalAudioDecodeTestSupport.FrameSignalTrace finalSignalTrace =
                lastFrameSignalTraceAtOrBefore(detailed.frameSignalTraces(), detailed.flushTimestampMs());
        LocalAudioDecodeTestSupport.TimingStateTrace finalTimingTrace =
                lastTimingTraceAtOrBefore(detailed.timingStateTraces(), detailed.flushTimestampMs());
        CwSignalSnapshot finalSignalSnapshot =
                finalSignalTrace == null ? detailed.probeResult().signalSnapshot() : finalSignalTrace.snapshot();
        CwTimingSnapshot finalStableSnapshot = finalTimingTrace == null
                ? detailed.probeResult().timingSnapshot()
                : finalTimingTrace.stabilizedSnapshot();
        CwTimingSnapshot finalRawSnapshot = finalTimingTrace == null
                ? detailed.probeResult().timingSnapshot()
                : finalTimingTrace.rawSnapshot();
        DebugSnapshot finalDebugSnapshot = finalTimingTrace == null ? null : finalTimingTrace.debugSnapshot();

        System.out.println(String.format(
                Locale.US,
                "FINAL text=\"%s\" chars=%d raw=%.2f stable=%.2f trusted=%.1fms target=%d eff=%d lock=%.0f%% near=%.0f%% unl=%.0f%% %s",
                sanitize(detailed.probeResult().decodedText()),
                detailed.probeResult().decoderSnapshot().totalCharacters(),
                snapshotWpm(finalRawSnapshot),
                snapshotWpm(finalStableSnapshot),
                finalDebugSnapshot == null ? 0.0d : finalDebugSnapshot.trustedDotEstimateMs(),
                finalSignalSnapshot == null ? 0 : finalSignalSnapshot.targetToneFrequencyHz(),
                finalSignalSnapshot == null ? 0 : finalSignalSnapshot.effectiveTrackedToneFrequencyHz(),
                finalSignalSnapshot == null ? 0.0d : finalSignalSnapshot.recentLockedFrameRatio() * 100.0d,
                finalSignalSnapshot == null ? 0.0d : finalSignalSnapshot.recentNearTargetLockedFrameRatio() * 100.0d,
                finalSignalSnapshot == null ? 0.0d : finalSignalSnapshot.recentActiveUnlockedFrameRatio() * 100.0d,
                renderDebugSummary(finalTimingTrace)
        ));
    }

    private static void runNaturalTurnProbe(
            String sourceLabel,
            List<AudioFrame> frames,
            int preferredToneHz
    ) {
        runNaturalTurnProbe(sourceLabel, frames, preferredToneHz, SEED_WPM);
    }

    private static void runNaturalTurnProbe(
            String sourceLabel,
            List<AudioFrame> frames,
            int preferredToneHz,
            int seedWpm
    ) {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("frames is empty");
        }
        List<AudioFrame> normalizedFrames = normalizeFramesToZero(frames);
        LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed =
                LocalAudioDecodeTestSupport.decodeFramesDetailedLiveLike(
                        sourceLabel,
                        normalizedFrames,
                        preferredToneHz,
                        seedWpm,
                        SQL_PERCENT,
                        false,
                        org.bi9clt.cwcn.core.interpreter.CwInterpreter.RecoveryMode.RAW_COPY_FOCUS
                );

        System.out.println(String.format(
                Locale.US,
                "==== natural turn probe: %s | seed=%dwpm | sql=%d ====",
                sourceLabel,
                seedWpm,
                SQL_PERCENT
        ));

        List<TurnWindow> turnWindows = buildTurnWindows(detailed);
        if (turnWindows.isEmpty()) {
            System.out.println("TURN none");
            return;
        }

        String referenceSettledTurnText = null;
        for (TurnWindow turnWindow : turnWindows) {
            TurnSummary summary = summarizeTurn(detailed, turnWindow, referenceSettledTurnText);
            System.out.println(summary.render());
            if (referenceSettledTurnText == null) {
                referenceSettledTurnText = summary.settledTurnText();
            }
        }

        LocalAudioDecodeTestSupport.TurnTransitionTrace lastTransition = detailed.turnTransitionTraces().isEmpty()
                ? null
                : detailed.turnTransitionTraces().get(detailed.turnTransitionTraces().size() - 1);
        System.out.println(String.format(
                Locale.US,
                "FINAL natural-turns=%d text=\"%s\" lastTransition=%s",
                turnWindows.size(),
                sanitize(detailed.probeResult().decodedText()),
                lastTransition == null
                        ? "none"
                        : lastTransition.kind() + "#" + lastTransition.turnIndex() + " " + safe(lastTransition.reason())
                ));
    }

    private static void runSeedSweepProbe(
            String sourceLabel,
            List<AudioFrame> frames,
            int preferredToneHz
    ) {
        System.out.println(String.format(
                Locale.US,
                "==== seed sweep: %s | sql=%d ====",
                sourceLabel,
                SQL_PERCENT
        ));
        for (int seedWpm : SEED_SWEEP_WPMS) {
            runNaturalTurnProbe(sourceLabel, frames, preferredToneHz, seedWpm);
        }
    }

    private static RepeatedTimeline buildRepeatedTimeline(List<AudioFrame> sourceFrames) {
        List<AudioFrame> baseFrames = normalizeFramesToZero(sourceFrames);
        if (baseFrames.isEmpty()) {
            return new RepeatedTimeline(new ArrayList<>(), new ArrayList<>());
        }

        int sampleRateHz = baseFrames.get(0).sampleRateHz();
        long silentFrameDurationMs = Math.max(
                1L,
                Math.round(FRAME_SIZE_SAMPLES * 1000.0d / Math.max(1, sampleRateHz))
        );
        long baseDurationMs = estimateFrameEndTimestampMs(baseFrames.get(baseFrames.size() - 1));

        ArrayList<AudioFrame> sessionFrames = new ArrayList<>();
        ArrayList<RoundWindow> rounds = new ArrayList<>();
        long sessionCursorMs = 0L;

        for (int roundIndex = 1; roundIndex <= REPEAT_COUNT; roundIndex++) {
            long roundStartTimestampMs = sessionCursorMs;
            for (AudioFrame baseFrame : baseFrames) {
                sessionFrames.add(shiftFrame(baseFrame, sessionCursorMs));
            }
            long roundEndTimestampMs = roundStartTimestampMs + baseDurationMs;
            long postSilenceEndTimestampMs = roundEndTimestampMs;
            if (roundIndex < REPEAT_COUNT) {
                long producedMs = 0L;
                long silentFrameTimestampMs = postSilenceEndTimestampMs;
                while (producedMs < INTER_REPEAT_SILENCE_MS) {
                    sessionFrames.add(buildSilentFrame(sampleRateHz, silentFrameTimestampMs));
                    silentFrameTimestampMs += silentFrameDurationMs;
                    producedMs += silentFrameDurationMs;
                }
                postSilenceEndTimestampMs = silentFrameTimestampMs;
            }
            rounds.add(new RoundWindow(
                    roundIndex,
                    roundStartTimestampMs,
                    roundEndTimestampMs,
                    postSilenceEndTimestampMs
            ));
            sessionCursorMs = postSilenceEndTimestampMs;
        }
        return new RepeatedTimeline(sessionFrames, rounds);
    }

    private static String replayTimingWindowWithForcedDot(
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed,
            long windowStartTimestampMs,
            long windowEndTimestampMs,
            long forcedDotMs
    ) {
        if (detailed == null
                || forcedDotMs <= 0L
                || windowEndTimestampMs < windowStartTimestampMs) {
            return "";
        }
        CwDecoder decoder = new CwDecoder();
        CwInterpreter interpreter = new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);
        QsoStateMachine qsoStateMachine = new QsoStateMachine();
        for (CwTimingEvent timingEvent : detailed.timingEvents()) {
            if (timingEvent == null
                    || timingEvent.timestampMs() < windowStartTimestampMs
                    || timingEvent.timestampMs() > windowEndTimestampMs) {
                continue;
            }
            CwTimingEvent anchoredEvent = forceClassifyTimingEvent(timingEvent, forcedDotMs);
            for (CwDecodeEvent decodeEvent : decoder.process(anchoredEvent)) {
                interpreter.process(decodeEvent);
                qsoStateMachine.process(interpreter.snapshot(), decodeEvent.timestampMs());
            }
        }
        for (CwDecodeEvent decodeEvent : decoder.flushPendingCharacter(windowEndTimestampMs)) {
            interpreter.process(decodeEvent);
            qsoStateMachine.process(interpreter.snapshot(), decodeEvent.timestampMs());
        }
        return sanitize(decoder.snapshot().decodedText());
    }

    private static CwTimingEvent forceClassifyTimingEvent(CwTimingEvent timingEvent, long forcedDotMs) {
        if (timingEvent == null || forcedDotMs <= 0L) {
            return timingEvent;
        }
        CwTimingEvent.Classification classification = timingEvent.kind() == CwTimingEvent.Kind.TONE
                ? classifyToneAgainstForcedDot(timingEvent.durationMs(), forcedDotMs)
                : classifyGapAgainstForcedDot(timingEvent.durationMs(), forcedDotMs);
        return new CwTimingEvent(
                timingEvent.kind(),
                classification,
                timingEvent.timestampMs(),
                timingEvent.durationMs(),
                forcedDotMs,
                forcedDotMs
        );
    }

    private static CwTimingEvent.Classification classifyToneAgainstForcedDot(long durationMs, long dotMs) {
        double ratio = durationMs / (double) Math.max(1L, dotMs);
        if (ratio <= 1.8d) {
            return CwTimingEvent.Classification.DIT;
        }
        if (ratio <= 4.8d) {
            return CwTimingEvent.Classification.DAH;
        }
        return CwTimingEvent.Classification.UNKNOWN;
    }

    private static CwTimingEvent.Classification classifyGapAgainstForcedDot(long durationMs, long dotMs) {
        double ratio = durationMs / (double) Math.max(1L, dotMs);
        if (ratio <= 1.8d) {
            return CwTimingEvent.Classification.INTRA_SYMBOL_GAP;
        }
        if (ratio <= 4.35d) {
            return CwTimingEvent.Classification.LETTER_GAP;
        }
        if (ratio <= 10.0d) {
            return CwTimingEvent.Classification.WORD_GAP;
        }
        return CwTimingEvent.Classification.UNKNOWN;
    }

    private static RoundSummary summarizeRound(
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed,
            RoundWindow roundWindow,
            String referenceSettledRoundText
    ) {
        String totalTextAtRoundStart = textAtOrBefore(detailed.decodeEvents(), roundWindow.roundStartTimestampMs());
        String totalTextAtRoundEnd = textAtOrBefore(detailed.decodeEvents(), roundWindow.roundEndTimestampMs());
        String totalTextAfterPostSilence = textAtOrBefore(
                detailed.decodeEvents(),
                roundWindow.postSilenceEndTimestampMs()
        );

        String roundTextAtEnd = sliceNewText(totalTextAtRoundStart, totalTextAtRoundEnd);
        String settledRoundText = sliceNewText(totalTextAtRoundStart, totalTextAfterPostSilence);

        int characterCount = 0;
        int unknownCharacterCount = 0;
        int wordBreakCount = 0;
        for (CwDecodeEvent decodeEvent : detailed.decodeEvents()) {
            if (decodeEvent == null
                    || decodeEvent.timestampMs() < roundWindow.roundStartTimestampMs()
                    || decodeEvent.timestampMs() > roundWindow.postSilenceEndTimestampMs()) {
                continue;
            }
            if (decodeEvent.type() == CwDecodeEvent.Type.CHARACTER_DECODED) {
                characterCount += 1;
                if (decodeEvent.unknownCharacter()) {
                    unknownCharacterCount += 1;
                }
            } else if (decodeEvent.type() == CwDecodeEvent.Type.WORD_BREAK) {
                wordBreakCount += 1;
            }
        }

        double maxRawWpm = 0.0d;
        long maxRawTimestampMs = roundWindow.roundStartTimestampMs();
        double maxStableWpm = 0.0d;
        long maxStableTimestampMs = roundWindow.roundStartTimestampMs();
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : detailed.timingStateTraces()) {
            if (trace == null
                    || trace.timestampMs() < roundWindow.roundStartTimestampMs()
                    || trace.timestampMs() > roundWindow.roundEndTimestampMs()) {
                continue;
            }
            double rawWpm = snapshotWpm(trace.rawSnapshot());
            if (rawWpm > maxRawWpm) {
                maxRawWpm = rawWpm;
                maxRawTimestampMs = trace.timestampMs();
            }
            double stableWpm = snapshotWpm(trace.stabilizedSnapshot());
            if (stableWpm > maxStableWpm) {
                maxStableWpm = stableWpm;
                maxStableTimestampMs = trace.timestampMs();
            }
        }

        NumericStats lockedRatioStats = new NumericStats();
        NumericStats nearTargetRatioStats = new NumericStats();
        NumericStats unlockedRatioStats = new NumericStats();
        for (LocalAudioDecodeTestSupport.FrameSignalTrace trace : detailed.frameSignalTraces()) {
            if (trace == null
                    || trace.snapshot() == null
                    || trace.timestampMs() < roundWindow.roundStartTimestampMs()
                    || trace.timestampMs() > roundWindow.roundEndTimestampMs()) {
                continue;
            }
            lockedRatioStats.add(trace.snapshot().recentLockedFrameRatio() * 100.0d);
            nearTargetRatioStats.add(trace.snapshot().recentNearTargetLockedFrameRatio() * 100.0d);
            unlockedRatioStats.add(trace.snapshot().recentActiveUnlockedFrameRatio() * 100.0d);
        }

        LocalAudioDecodeTestSupport.TimingStateTrace roundEndTimingTrace =
                lastTimingTraceAtOrBefore(detailed.timingStateTraces(), roundWindow.roundEndTimestampMs());
        LocalAudioDecodeTestSupport.TimingStateTrace postSilenceTimingTrace =
                lastTimingTraceAtOrBefore(detailed.timingStateTraces(), roundWindow.postSilenceEndTimestampMs());
        LocalAudioDecodeTestSupport.FrameSignalTrace roundEndSignalTrace =
                lastFrameSignalTraceAtOrBefore(detailed.frameSignalTraces(), roundWindow.roundEndTimestampMs());
        LocalAudioDecodeTestSupport.FrameSignalTrace postSilenceSignalTrace =
                lastFrameSignalTraceAtOrBefore(detailed.frameSignalTraces(), roundWindow.postSilenceEndTimestampMs());

        boolean sameAsReference = referenceSettledRoundText != null
                && normalizeText(referenceSettledRoundText).equals(normalizeText(settledRoundText));

        return new RoundSummary(
                roundWindow.roundIndex(),
                roundWindow.roundStartTimestampMs(),
                characterCount,
                unknownCharacterCount,
                wordBreakCount,
                maxRawWpm,
                maxRawTimestampMs,
                maxStableWpm,
                maxStableTimestampMs,
                lockedRatioStats.average(),
                nearTargetRatioStats.average(),
                unlockedRatioStats.average(),
                roundEndTimingTrace,
                postSilenceTimingTrace,
                roundEndSignalTrace,
                postSilenceSignalTrace,
                roundTextAtEnd,
                settledRoundText,
                totalTextAtRoundEnd,
                totalTextAfterPostSilence,
                referenceSettledRoundText == null ? null : sameAsReference
        );
    }

    private static List<TurnWindow> buildTurnWindows(LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed) {
        ArrayList<TurnWindow> turnWindows = new ArrayList<>();
        for (LocalAudioDecodeTestSupport.TurnTransitionTrace transitionTrace : detailed.turnTransitionTraces()) {
            if (transitionTrace == null
                    || transitionTrace.kind() != LocalAudioDecodeTestSupport.TurnTransitionTrace.Kind.START) {
                continue;
            }
            LocalAudioDecodeTestSupport.TurnTransitionTrace endTrace = findTurnEndTrace(
                    detailed.turnTransitionTraces(),
                    transitionTrace.turnIndex(),
                    transitionTrace.timestampMs()
            );
            long turnEndTimestampMs = endTrace == null ? detailed.flushTimestampMs() : endTrace.timestampMs();
            turnWindows.add(new TurnWindow(
                    transitionTrace.turnIndex(),
                    transitionTrace.timestampMs(),
                    turnEndTimestampMs,
                    transitionTrace,
                    endTrace
            ));
        }
        return turnWindows;
    }

    private static LocalAudioDecodeTestSupport.TurnTransitionTrace findTurnEndTrace(
            List<LocalAudioDecodeTestSupport.TurnTransitionTrace> traces,
            int turnIndex,
            long turnStartTimestampMs
    ) {
        for (LocalAudioDecodeTestSupport.TurnTransitionTrace trace : traces) {
            if (trace == null
                    || trace.kind() != LocalAudioDecodeTestSupport.TurnTransitionTrace.Kind.END
                    || trace.turnIndex() != turnIndex
                    || trace.timestampMs() < turnStartTimestampMs) {
                continue;
            }
            return trace;
        }
        return null;
    }

    private static TurnSummary summarizeTurn(
            LocalAudioDecodeTestSupport.OfflineDetailedProbeResult detailed,
            TurnWindow turnWindow,
            String referenceSettledTurnText
    ) {
        String totalTextAtTurnStart = textAtOrBefore(detailed.decodeEvents(), turnWindow.turnStartTimestampMs());
        String totalTextAtTurnEnd = textAtOrBefore(detailed.decodeEvents(), turnWindow.turnEndTimestampMs());
        String turnText = sliceNewText(totalTextAtTurnStart, totalTextAtTurnEnd);

        int characterCount = 0;
        int unknownCharacterCount = 0;
        int wordBreakCount = 0;
        for (CwDecodeEvent decodeEvent : detailed.decodeEvents()) {
            if (decodeEvent == null
                    || decodeEvent.timestampMs() < turnWindow.turnStartTimestampMs()
                    || decodeEvent.timestampMs() > turnWindow.turnEndTimestampMs()) {
                continue;
            }
            if (decodeEvent.type() == CwDecodeEvent.Type.CHARACTER_DECODED) {
                characterCount += 1;
                if (decodeEvent.unknownCharacter()) {
                    unknownCharacterCount += 1;
                }
            } else if (decodeEvent.type() == CwDecodeEvent.Type.WORD_BREAK) {
                wordBreakCount += 1;
            }
        }

        double maxRawWpm = 0.0d;
        long maxRawTimestampMs = turnWindow.turnStartTimestampMs();
        double maxStableWpm = 0.0d;
        long maxStableTimestampMs = turnWindow.turnStartTimestampMs();
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : detailed.timingStateTraces()) {
            if (trace == null
                    || trace.timestampMs() < turnWindow.turnStartTimestampMs()
                    || trace.timestampMs() > turnWindow.turnEndTimestampMs()) {
                continue;
            }
            double rawWpm = snapshotWpm(trace.rawSnapshot());
            if (rawWpm > maxRawWpm) {
                maxRawWpm = rawWpm;
                maxRawTimestampMs = trace.timestampMs();
            }
            double stableWpm = snapshotWpm(trace.stabilizedSnapshot());
            if (stableWpm > maxStableWpm) {
                maxStableWpm = stableWpm;
                maxStableTimestampMs = trace.timestampMs();
            }
        }

        NumericStats lockedRatioStats = new NumericStats();
        NumericStats nearTargetRatioStats = new NumericStats();
        NumericStats unlockedRatioStats = new NumericStats();
        for (LocalAudioDecodeTestSupport.FrameSignalTrace trace : detailed.frameSignalTraces()) {
            if (trace == null
                    || trace.snapshot() == null
                    || trace.timestampMs() < turnWindow.turnStartTimestampMs()
                    || trace.timestampMs() > turnWindow.turnEndTimestampMs()) {
                continue;
            }
            lockedRatioStats.add(trace.snapshot().recentLockedFrameRatio() * 100.0d);
            nearTargetRatioStats.add(trace.snapshot().recentNearTargetLockedFrameRatio() * 100.0d);
            unlockedRatioStats.add(trace.snapshot().recentActiveUnlockedFrameRatio() * 100.0d);
        }

        LocalAudioDecodeTestSupport.TimingStateTrace turnEndTimingTrace =
                lastTimingTraceAtOrBefore(detailed.timingStateTraces(), turnWindow.turnEndTimestampMs());
        LocalAudioDecodeTestSupport.FrameSignalTrace turnEndSignalTrace =
                lastFrameSignalTraceAtOrBefore(detailed.frameSignalTraces(), turnWindow.turnEndTimestampMs());

        boolean sameAsReference = referenceSettledTurnText != null
                && normalizeText(referenceSettledTurnText).equals(normalizeText(turnText));

        return new TurnSummary(
                turnWindow.turnIndex(),
                turnWindow.turnStartTimestampMs(),
                turnWindow.turnEndTimestampMs(),
                characterCount,
                unknownCharacterCount,
                wordBreakCount,
                maxRawWpm,
                maxRawTimestampMs,
                maxStableWpm,
                maxStableTimestampMs,
                lockedRatioStats.average(),
                nearTargetRatioStats.average(),
                unlockedRatioStats.average(),
                turnWindow.startTrace(),
                turnWindow.endTrace(),
                turnEndTimingTrace,
                turnEndSignalTrace,
                turnText,
                totalTextAtTurnEnd,
                referenceSettledTurnText == null ? null : sameAsReference
        );
    }

    private static LocalAudioDecodeTestSupport.TimingStateTrace lastTimingTraceAtOrBefore(
            List<LocalAudioDecodeTestSupport.TimingStateTrace> traces,
            long timestampMs
    ) {
        LocalAudioDecodeTestSupport.TimingStateTrace candidate = null;
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : traces) {
            if (trace != null && trace.timestampMs() <= timestampMs) {
                candidate = trace;
            } else if (trace != null && trace.timestampMs() > timestampMs) {
                break;
            }
        }
        return candidate;
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

    private static LocalAudioDecodeTestSupport.TimingStateTrace firstTimingTraceWithReasonPrefix(
            List<LocalAudioDecodeTestSupport.TimingStateTrace> traces,
            long windowStartTimestampMs,
            long windowEndTimestampMs,
            String reasonPrefix
    ) {
        for (LocalAudioDecodeTestSupport.TimingStateTrace trace : traces) {
            if (trace == null
                    || trace.debugSnapshot() == null
                    || trace.timestampMs() < windowStartTimestampMs
                    || trace.timestampMs() > windowEndTimestampMs) {
                continue;
            }
            String reason = safe(trace.debugSnapshot().lastTrustedUpdateReason());
            if (reason.startsWith(reasonPrefix)) {
                return trace;
            }
        }
        return null;
    }

    private static LocalAudioDecodeTestSupport.FrameSignalTrace lastFrameSignalTraceAtOrBefore(
            List<LocalAudioDecodeTestSupport.FrameSignalTrace> traces,
            long timestampMs
    ) {
        LocalAudioDecodeTestSupport.FrameSignalTrace candidate = null;
        for (LocalAudioDecodeTestSupport.FrameSignalTrace trace : traces) {
            if (trace != null && trace.timestampMs() <= timestampMs) {
                candidate = trace;
            } else if (trace != null && trace.timestampMs() > timestampMs) {
                break;
            }
        }
        return candidate;
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

    private static String textAtOrBeforeFinalOnly(List<CwDecodeEvent> decodeEvents, long timestampMs) {
        String latestText = "";
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            if (decodeEvent == null || decodeEvent.timestampMs() > timestampMs) {
                if (decodeEvent != null && decodeEvent.timestampMs() > timestampMs) {
                    break;
                }
                continue;
            }
            if (decodeEvent.type() != CwDecodeEvent.Type.CHARACTER_DECODED
                    && decodeEvent.type() != CwDecodeEvent.Type.WORD_BREAK) {
                continue;
            }
            latestText = decodeEvent.outputText();
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

    private static AudioFrame shiftFrame(AudioFrame frame, long offsetMs) {
        return new AudioFrame(
                frame.samples(),
                frame.sampleRateHz(),
                frame.channelCount(),
                frame.peakAmplitude(),
                frame.rmsAmplitude(),
                frame.clippedSampleCount(),
                frame.capturedAtMs() + offsetMs
        );
    }

    private static AudioFrame buildSilentFrame(int sampleRateHz, long capturedAtMs) {
        return new AudioFrame(
                new short[FRAME_SIZE_SAMPLES],
                sampleRateHz,
                1,
                0,
                0.0d,
                0,
                capturedAtMs
        );
    }

    private static long estimateFrameEndTimestampMs(AudioFrame frame) {
        if (frame == null) {
            return 0L;
        }
        long frameDurationMs = Math.max(
                1L,
                Math.round(frame.sampleCount() * 1000.0d / Math.max(1, frame.sampleRateHz()))
        );
        return frame.capturedAtMs() + frameDurationMs;
    }

    private static double snapshotWpm(CwTimingSnapshot snapshot) {
        if (snapshot == null) {
            return 0.0d;
        }
        if (snapshot.estimatedWpmPrecise() > 0.0d) {
            return snapshot.estimatedWpmPrecise();
        }
        return Math.max(0.0d, snapshot.estimatedWpm());
    }

    private static String renderDebugSummary(LocalAudioDecodeTestSupport.TimingStateTrace timingTrace) {
        if (timingTrace == null || timingTrace.debugSnapshot() == null) {
            return "debug=none";
        }
        DebugSnapshot debugSnapshot = timingTrace.debugSnapshot();
        return String.format(
                Locale.US,
                "act=%s sw=%s tu=%s trusted=%.1fms retained=%.1fms",
                safe(debugSnapshot.activeStrategyName()),
                safe(debugSnapshot.lastStrategyDecision()),
                safe(debugSnapshot.lastTrustedUpdateReason()),
                debugSnapshot.trustedDotEstimateMs(),
                debugSnapshot.retainedDotEstimateMs()
        );
    }

    private static void assertBootstrapTrustWithinOpeningWindow(
            String label,
            LocalAudioDecodeTestSupport.TimingStateTrace initTrace,
            long initOffsetMs,
            String preInitText,
            long maxInitOffsetMs
    ) {
        assertNotNull(label + " should establish trusted timing", initTrace);
        DebugSnapshot debugSnapshot = initTrace.debugSnapshot();
        assertNotNull(label + " should carry debug snapshot", debugSnapshot);
        assertTrue(
                label + " init offset should be positive",
                initOffsetMs >= 0L
        );
        assertTrue(
                label + " init offset should stay within opening window, actual=" + initOffsetMs,
                initOffsetMs <= maxInitOffsetMs
        );
        assertTrue(
                label + " trusted dot should stay in 24WPM neighborhood, actual="
                        + debugSnapshot.trustedDotEstimateMs(),
                debugSnapshot.trustedDotEstimateMs() >= TRUSTED_DOT_24WPM_MIN_MS
                        && debugSnapshot.trustedDotEstimateMs() <= TRUSTED_DOT_24WPM_MAX_MS
        );
        assertTrue(
                label + " trust should be initialized from bootstrap path, actual="
                        + safe(debugSnapshot.lastTrustedUpdateReason()),
                safe(debugSnapshot.lastTrustedUpdateReason()).startsWith("init-")
        );
        String normalizedPreInitText = normalizeText(preInitText);
        assertTrue(
                label + " trust should stay within the opening CQ prefix, preInitText=\"" + normalizedPreInitText + "\"",
                normalizedPreInitText.isEmpty() || "CQ".startsWith(normalizedPreInitText)
        );
    }

    private static void assertPreTrustPrefixQuality(
            String label,
            List<CwDecodeEvent> decodeEvents,
            long windowStartTimestampMs,
            long trustTimestampMs
    ) {
        String finalOnlyPrefix = normalizeText(sliceNewText(
                textAtOrBeforeFinalOnly(decodeEvents, windowStartTimestampMs),
                textAtOrBeforeFinalOnly(decodeEvents, trustTimestampMs)
        ));
        String allEventsPrefix = normalizeText(sliceNewText(
                textAtOrBefore(decodeEvents, windowStartTimestampMs),
                textAtOrBefore(decodeEvents, trustTimestampMs)
        ));
        int unknownCharacterCount = 0;
        int wordBreakCount = 0;
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            if (decodeEvent == null
                    || decodeEvent.timestampMs() < windowStartTimestampMs
                    || decodeEvent.timestampMs() > trustTimestampMs) {
                continue;
            }
            if (decodeEvent.type() == CwDecodeEvent.Type.CHARACTER_DECODED
                    && decodeEvent.unknownCharacter()) {
                unknownCharacterCount += 1;
            } else if (decodeEvent.type() == CwDecodeEvent.Type.WORD_BREAK) {
                wordBreakCount += 1;
            }
        }
        boolean allowedPrefix = finalOnlyPrefix.isEmpty()
                || "C".equals(finalOnlyPrefix)
                || "CQ".equals(finalOnlyPrefix);
        assertTrue(
                label + " final-only pre-trust prefix should stay on-path, actual=\"" + finalOnlyPrefix + "\"",
                allowedPrefix
        );
        assertTrue(
                label + " all-events pre-trust prefix should stay on-path, actual=\"" + allEventsPrefix + "\"",
                allEventsPrefix.isEmpty()
                        || "C".equals(allEventsPrefix)
                        || "CQ".equals(allEventsPrefix)
        );
        assertEquals(
                label + " should not emit unknown characters before trust",
                0,
                unknownCharacterCount
        );
        assertTrue(
                label + " should emit at most one word break before trust, actual=" + wordBreakCount,
                wordBreakCount <= 1
        );
    }

    private static String sanitize(String text) {
        String normalized = normalizeText(text);
        return normalized.isEmpty() ? "(empty)" : normalized;
    }

    private static String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace(CwDecoder.UNKNOWN_CHARACTER, "?").trim();
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "none" : value.trim();
    }

    private static final class RepeatedTimeline {
        private final List<AudioFrame> sessionFrames;
        private final List<RoundWindow> rounds;

        private RepeatedTimeline(List<AudioFrame> sessionFrames, List<RoundWindow> rounds) {
            this.sessionFrames = sessionFrames;
            this.rounds = rounds;
        }

        private List<AudioFrame> sessionFrames() {
            return sessionFrames;
        }

        private List<RoundWindow> rounds() {
            return rounds;
        }
    }

    private static final class RoundWindow {
        private final int roundIndex;
        private final long roundStartTimestampMs;
        private final long roundEndTimestampMs;
        private final long postSilenceEndTimestampMs;

        private RoundWindow(
                int roundIndex,
                long roundStartTimestampMs,
                long roundEndTimestampMs,
                long postSilenceEndTimestampMs
        ) {
            this.roundIndex = roundIndex;
            this.roundStartTimestampMs = roundStartTimestampMs;
            this.roundEndTimestampMs = roundEndTimestampMs;
            this.postSilenceEndTimestampMs = postSilenceEndTimestampMs;
        }

        private int roundIndex() {
            return roundIndex;
        }

        private long roundStartTimestampMs() {
            return roundStartTimestampMs;
        }

        private long roundEndTimestampMs() {
            return roundEndTimestampMs;
        }

        private long postSilenceEndTimestampMs() {
            return postSilenceEndTimestampMs;
        }

        private boolean hasPostSilence() {
            return postSilenceEndTimestampMs > roundEndTimestampMs;
        }
    }

    private static final class TurnWindow {
        private final int turnIndex;
        private final long turnStartTimestampMs;
        private final long turnEndTimestampMs;
        private final LocalAudioDecodeTestSupport.TurnTransitionTrace startTrace;
        private final LocalAudioDecodeTestSupport.TurnTransitionTrace endTrace;

        private TurnWindow(
                int turnIndex,
                long turnStartTimestampMs,
                long turnEndTimestampMs,
                LocalAudioDecodeTestSupport.TurnTransitionTrace startTrace,
                LocalAudioDecodeTestSupport.TurnTransitionTrace endTrace
        ) {
            this.turnIndex = turnIndex;
            this.turnStartTimestampMs = turnStartTimestampMs;
            this.turnEndTimestampMs = turnEndTimestampMs;
            this.startTrace = startTrace;
            this.endTrace = endTrace;
        }

        private int turnIndex() {
            return turnIndex;
        }

        private long turnStartTimestampMs() {
            return turnStartTimestampMs;
        }

        private long turnEndTimestampMs() {
            return turnEndTimestampMs;
        }

        private LocalAudioDecodeTestSupport.TurnTransitionTrace startTrace() {
            return startTrace;
        }

        private LocalAudioDecodeTestSupport.TurnTransitionTrace endTrace() {
            return endTrace;
        }
    }

    private static final class NumericStats {
        private double sum;
        private int count;

        private void add(double value) {
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return;
            }
            sum += value;
            count += 1;
        }

        private double average() {
            return count == 0 ? 0.0d : sum / count;
        }
    }

    private static final class RoundSummary {
        private final int roundIndex;
        private final long roundStartTimestampMs;
        private final int characterCount;
        private final int unknownCharacterCount;
        private final int wordBreakCount;
        private final double maxRawWpm;
        private final long maxRawTimestampMs;
        private final double maxStableWpm;
        private final long maxStableTimestampMs;
        private final double averageLockedRatio;
        private final double averageNearTargetRatio;
        private final double averageUnlockedRatio;
        private final LocalAudioDecodeTestSupport.TimingStateTrace roundEndTimingTrace;
        private final LocalAudioDecodeTestSupport.TimingStateTrace postSilenceTimingTrace;
        private final LocalAudioDecodeTestSupport.FrameSignalTrace roundEndSignalTrace;
        private final LocalAudioDecodeTestSupport.FrameSignalTrace postSilenceSignalTrace;
        private final String roundTextAtEnd;
        private final String settledRoundText;
        private final String totalTextAtRoundEnd;
        private final String totalTextAfterPostSilence;
        private final Boolean sameAsReference;

        private RoundSummary(
                int roundIndex,
                long roundStartTimestampMs,
                int characterCount,
                int unknownCharacterCount,
                int wordBreakCount,
                double maxRawWpm,
                long maxRawTimestampMs,
                double maxStableWpm,
                long maxStableTimestampMs,
                double averageLockedRatio,
                double averageNearTargetRatio,
                double averageUnlockedRatio,
                LocalAudioDecodeTestSupport.TimingStateTrace roundEndTimingTrace,
                LocalAudioDecodeTestSupport.TimingStateTrace postSilenceTimingTrace,
                LocalAudioDecodeTestSupport.FrameSignalTrace roundEndSignalTrace,
                LocalAudioDecodeTestSupport.FrameSignalTrace postSilenceSignalTrace,
                String roundTextAtEnd,
                String settledRoundText,
                String totalTextAtRoundEnd,
                String totalTextAfterPostSilence,
                Boolean sameAsReference
        ) {
            this.roundIndex = roundIndex;
            this.roundStartTimestampMs = roundStartTimestampMs;
            this.characterCount = characterCount;
            this.unknownCharacterCount = unknownCharacterCount;
            this.wordBreakCount = wordBreakCount;
            this.maxRawWpm = maxRawWpm;
            this.maxRawTimestampMs = maxRawTimestampMs;
            this.maxStableWpm = maxStableWpm;
            this.maxStableTimestampMs = maxStableTimestampMs;
            this.averageLockedRatio = averageLockedRatio;
            this.averageNearTargetRatio = averageNearTargetRatio;
            this.averageUnlockedRatio = averageUnlockedRatio;
            this.roundEndTimingTrace = roundEndTimingTrace;
            this.postSilenceTimingTrace = postSilenceTimingTrace;
            this.roundEndSignalTrace = roundEndSignalTrace;
            this.postSilenceSignalTrace = postSilenceSignalTrace;
            this.roundTextAtEnd = roundTextAtEnd;
            this.settledRoundText = settledRoundText;
            this.totalTextAtRoundEnd = totalTextAtRoundEnd;
            this.totalTextAfterPostSilence = totalTextAfterPostSilence;
            this.sameAsReference = sameAsReference;
        }

        private String settledRoundText() {
            return settledRoundText;
        }

        private String renderRoundEnd() {
            CwTimingSnapshot endRawSnapshot = roundEndTimingTrace == null ? null : roundEndTimingTrace.rawSnapshot();
            CwTimingSnapshot endStableSnapshot = roundEndTimingTrace == null
                    ? null
                    : roundEndTimingTrace.stabilizedSnapshot();
            DebugSnapshot endDebugSnapshot = roundEndTimingTrace == null ? null : roundEndTimingTrace.debugSnapshot();
            CwSignalSnapshot endSignalSnapshot = roundEndSignalTrace == null ? null : roundEndSignalTrace.snapshot();
            String sameAsRoundOne = sameAsReference == null ? "-" : (sameAsReference ? "Y" : "N");
            return String.format(
                    Locale.US,
                    "ROUND %d end start@%d chars=%d unknown=%d word=%d maxRaw=%.2f@%d maxStable=%.2f@%d"
                            + " endRaw=%.2f endStable=%.2f trusted=%.1fms target=%d eff=%d lock=%.0f%% near=%.0f%% unl=%.0f%% sameR1=%s"
                            + " endText=\"%s\" total=\"%s\" %s",
                    roundIndex,
                    roundStartTimestampMs,
                    characterCount,
                    unknownCharacterCount,
                    wordBreakCount,
                    maxRawWpm,
                    maxRawTimestampMs,
                    maxStableWpm,
                    maxStableTimestampMs,
                    snapshotWpm(endRawSnapshot),
                    snapshotWpm(endStableSnapshot),
                    endDebugSnapshot == null ? 0.0d : endDebugSnapshot.trustedDotEstimateMs(),
                    endSignalSnapshot == null ? 0 : endSignalSnapshot.targetToneFrequencyHz(),
                    endSignalSnapshot == null ? 0 : endSignalSnapshot.effectiveTrackedToneFrequencyHz(),
                    averageLockedRatio,
                    averageNearTargetRatio,
                    averageUnlockedRatio,
                    sameAsRoundOne,
                    sanitize(roundTextAtEnd),
                    sanitize(totalTextAtRoundEnd),
                    renderDebugSummary(roundEndTimingTrace)
            );
        }

        private String renderPostSilence() {
            CwTimingSnapshot postRawSnapshot = postSilenceTimingTrace == null ? null : postSilenceTimingTrace.rawSnapshot();
            CwTimingSnapshot postStableSnapshot = postSilenceTimingTrace == null
                    ? null
                    : postSilenceTimingTrace.stabilizedSnapshot();
            DebugSnapshot postDebugSnapshot = postSilenceTimingTrace == null ? null : postSilenceTimingTrace.debugSnapshot();
            CwSignalSnapshot postSignalSnapshot = postSilenceSignalTrace == null ? null : postSilenceSignalTrace.snapshot();
            String sameAsRoundOne = sameAsReference == null ? "-" : (sameAsReference ? "Y" : "N");
            return String.format(
                    Locale.US,
                    "ROUND %d post-silence raw=%.2f stable=%.2f trusted=%.1fms target=%d eff=%d sameR1=%s"
                            + " settled=\"%s\" total=\"%s\" %s",
                    roundIndex,
                    snapshotWpm(postRawSnapshot),
                    snapshotWpm(postStableSnapshot),
                    postDebugSnapshot == null ? 0.0d : postDebugSnapshot.trustedDotEstimateMs(),
                    postSignalSnapshot == null ? 0 : postSignalSnapshot.targetToneFrequencyHz(),
                    postSignalSnapshot == null ? 0 : postSignalSnapshot.effectiveTrackedToneFrequencyHz(),
                    sameAsRoundOne,
                    sanitize(settledRoundText),
                    sanitize(totalTextAfterPostSilence),
                    renderDebugSummary(postSilenceTimingTrace)
            );
        }
    }

    private static final class TurnSummary {
        private final int turnIndex;
        private final long turnStartTimestampMs;
        private final long turnEndTimestampMs;
        private final int characterCount;
        private final int unknownCharacterCount;
        private final int wordBreakCount;
        private final double maxRawWpm;
        private final long maxRawTimestampMs;
        private final double maxStableWpm;
        private final long maxStableTimestampMs;
        private final double averageLockedRatio;
        private final double averageNearTargetRatio;
        private final double averageUnlockedRatio;
        private final LocalAudioDecodeTestSupport.TurnTransitionTrace startTrace;
        private final LocalAudioDecodeTestSupport.TurnTransitionTrace endTrace;
        private final LocalAudioDecodeTestSupport.TimingStateTrace turnEndTimingTrace;
        private final LocalAudioDecodeTestSupport.FrameSignalTrace turnEndSignalTrace;
        private final String settledTurnText;
        private final String totalTextAtTurnEnd;
        private final Boolean sameAsReference;

        private TurnSummary(
                int turnIndex,
                long turnStartTimestampMs,
                long turnEndTimestampMs,
                int characterCount,
                int unknownCharacterCount,
                int wordBreakCount,
                double maxRawWpm,
                long maxRawTimestampMs,
                double maxStableWpm,
                long maxStableTimestampMs,
                double averageLockedRatio,
                double averageNearTargetRatio,
                double averageUnlockedRatio,
                LocalAudioDecodeTestSupport.TurnTransitionTrace startTrace,
                LocalAudioDecodeTestSupport.TurnTransitionTrace endTrace,
                LocalAudioDecodeTestSupport.TimingStateTrace turnEndTimingTrace,
                LocalAudioDecodeTestSupport.FrameSignalTrace turnEndSignalTrace,
                String settledTurnText,
                String totalTextAtTurnEnd,
                Boolean sameAsReference
        ) {
            this.turnIndex = turnIndex;
            this.turnStartTimestampMs = turnStartTimestampMs;
            this.turnEndTimestampMs = turnEndTimestampMs;
            this.characterCount = characterCount;
            this.unknownCharacterCount = unknownCharacterCount;
            this.wordBreakCount = wordBreakCount;
            this.maxRawWpm = maxRawWpm;
            this.maxRawTimestampMs = maxRawTimestampMs;
            this.maxStableWpm = maxStableWpm;
            this.maxStableTimestampMs = maxStableTimestampMs;
            this.averageLockedRatio = averageLockedRatio;
            this.averageNearTargetRatio = averageNearTargetRatio;
            this.averageUnlockedRatio = averageUnlockedRatio;
            this.startTrace = startTrace;
            this.endTrace = endTrace;
            this.turnEndTimingTrace = turnEndTimingTrace;
            this.turnEndSignalTrace = turnEndSignalTrace;
            this.settledTurnText = settledTurnText;
            this.totalTextAtTurnEnd = totalTextAtTurnEnd;
            this.sameAsReference = sameAsReference;
        }

        private String settledTurnText() {
            return settledTurnText;
        }

        private String render() {
            CwTimingSnapshot endRawSnapshot = turnEndTimingTrace == null ? null : turnEndTimingTrace.rawSnapshot();
            CwTimingSnapshot endStableSnapshot = turnEndTimingTrace == null
                    ? null
                    : turnEndTimingTrace.stabilizedSnapshot();
            DebugSnapshot endDebugSnapshot = turnEndTimingTrace == null ? null : turnEndTimingTrace.debugSnapshot();
            CwSignalSnapshot endSignalSnapshot = turnEndSignalTrace == null ? null : turnEndSignalTrace.snapshot();
            String sameAsTurnOne = sameAsReference == null ? "-" : (sameAsReference ? "Y" : "N");
            return String.format(
                    Locale.US,
                    "TURN %d [%d..%d] seed=%d ref=%d cur=%d ret=%d chars=%d unknown=%d word=%d"
                            + " maxRaw=%.2f@%d maxStable=%.2f@%d endRaw=%.2f endStable=%.2f trusted=%.1fms"
                            + " target=%d eff=%d lock=%.0f%% near=%.0f%% unl=%.0f%% sameT1=%s"
                            + " text=\"%s\" total=\"%s\" start=%s end=%s %s",
                    turnIndex,
                    turnStartTimestampMs,
                    turnEndTimestampMs,
                    startTrace == null ? 0 : startTrace.turnSeedWpm(),
                    startTrace == null ? 0 : startTrace.referenceWpm(),
                    startTrace == null ? 0 : startTrace.currentTurnAnchorWpm(),
                    startTrace == null ? 0 : startTrace.retainedTurnAnchorWpm(),
                    characterCount,
                    unknownCharacterCount,
                    wordBreakCount,
                    maxRawWpm,
                    maxRawTimestampMs,
                    maxStableWpm,
                    maxStableTimestampMs,
                    snapshotWpm(endRawSnapshot),
                    snapshotWpm(endStableSnapshot),
                    endDebugSnapshot == null ? 0.0d : endDebugSnapshot.trustedDotEstimateMs(),
                    endSignalSnapshot == null ? 0 : endSignalSnapshot.targetToneFrequencyHz(),
                    endSignalSnapshot == null ? 0 : endSignalSnapshot.effectiveTrackedToneFrequencyHz(),
                    averageLockedRatio,
                    averageNearTargetRatio,
                    averageUnlockedRatio,
                    sameAsTurnOne,
                    sanitize(settledTurnText),
                    sanitize(totalTextAtTurnEnd),
                    startTrace == null ? "none" : safe(startTrace.reason()),
                    endTrace == null ? "open" : safe(endTrace.reason()),
                    renderDebugSummary(turnEndTimingTrace)
            );
        }
    }
}
