package org.bi9clt.cwcn.core.audio;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.decoder.CwDecoder;
import org.bi9clt.cwcn.core.decoder.CwDecoderSnapshot;
import org.bi9clt.cwcn.core.eval.CwFixtureLibrary;
import org.bi9clt.cwcn.core.eval.CwFixtureScenario;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.qso.QsoStateMachine;
import org.bi9clt.cwcn.core.rx.LiveRxToneEventStabilizer;
import org.bi9clt.cwcn.core.rx.LiveRxWpmGuard;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;
import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CwLocalAudioRepeatedSessionProbeTest {
    private static final int FRAME_SIZE_SAMPLES = 256;
    private static final int REPEAT_COUNT = 3;
    private static final long INTER_REPEAT_SILENCE_MS = 8000L;
    private static final int PREFERRED_TONE_HZ = 700;
    private static final int SEED_WPM = 15;
    private static final int SQL_PERCENT = 55;
    private static final double LIVE_CHARACTER_FLUSH_GAP_RATIO = 3.35d;
    private static final double STABLE_DECODE_LOCKED_RATIO_MIN = 0.60d;
    private static final double STABLE_DECODE_NEAR_TARGET_RATIO_MIN = 0.64d;
    private static final double STABLE_DECODE_ACTIVE_UNLOCKED_RATIO_MAX = 0.24d;
    private static final double STABLE_DECODE_TONE_DOMINANCE_MIN = 0.44d;
    private static final double STABLE_DECODE_ISOLATION_MIN = 0.54d;

    @Test
    public void printRepeatedLiveLikeProbeForRecording16() throws Exception {
        Path wavFile = LocalAudioDecodeTestSupport.listConvertedWavFiles().stream()
                .filter(path -> path.getFileName().toString().endsWith("(16).wav"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing WAV fixture for 录音 (16)"));
        LocalAudioDecodeTestSupport.WaveDataProbe waveData =
                LocalAudioDecodeTestSupport.readWaveFileForProbe(wavFile);
        List<AudioFrame> baseFrames = buildFrames(waveData.samples(), waveData.sampleRateHz());

        ProbeHarness harness = new ProbeHarness(
                "录音 (16)",
                waveData.sampleRateHz(),
                PREFERRED_TONE_HZ,
                SEED_WPM,
                SQL_PERCENT
        );
        harness.run(baseFrames);
    }

    @Test
    public void printRepeatedLiveLikeProbeForUserNoiseFixture25wpm() {
        runSyntheticFixtureProbe("user_noise_cq_25wpm_700hz");
    }

    @Test
    public void printRepeatedLiveLikeProbeForUsbLowLevelFixture18wpm() {
        runSyntheticFixtureProbe("usb_low_level_cq_18wpm_700hz");
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

    private static void runSyntheticFixtureProbe(String scenarioId) {
        CwFixtureScenario scenario = findScenario(scenarioId);
        List<AudioFrame> frames = new SyntheticFixtureRxAudioSource().renderFramesForTesting(scenario);
        int sampleRateHz = frames.isEmpty() ? 16000 : frames.get(0).sampleRateHz();
        ProbeHarness harness = new ProbeHarness(
                scenario.id() + " / " + scenario.displayName(),
                sampleRateHz,
                scenario.toneFrequencyHz(),
                SEED_WPM,
                SQL_PERCENT
        );
        harness.run(frames);
    }

    private static CwFixtureScenario findScenario(String scenarioId) {
        for (CwFixtureScenario scenario : CwFixtureLibrary.scenarios()) {
            if (scenario.id().equals(scenarioId)) {
                return scenario;
            }
        }
        throw new IllegalArgumentException("Unknown scenario: " + scenarioId);
    }

    private static final class ProbeHarness {
        private final String sourceLabel;
        private final int sampleRateHz;
        private final int preferredToneHz;
        private final int seedWpm;
        private final int sqlPercent;
        private final long silentFrameDurationMs;

        private final CwSignalProcessor signalProcessor = new CwSignalProcessor();
        private final CwHybridTimingModel timingModel = new CwHybridTimingModel();
        private final CwDecoder decoder = new CwDecoder();
        private final CwInterpreter interpreter = new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);
        private final QsoStateMachine qsoStateMachine = new QsoStateMachine();
        private final AudioInputHealthTracker inputHealthTracker = new AudioInputHealthTracker();
        private final LiveRxWpmGuard wpmGuard = new LiveRxWpmGuard();
        private final LiveRxToneEventStabilizer toneEventStabilizer = new LiveRxToneEventStabilizer();

        private ProbeHarness(
                String sourceLabel,
                int sampleRateHz,
                int preferredToneHz,
                int seedWpm,
                int sqlPercent
        ) {
            this.sourceLabel = sourceLabel;
            this.sampleRateHz = sampleRateHz;
            this.preferredToneHz = preferredToneHz;
            this.seedWpm = seedWpm;
            this.sqlPercent = sqlPercent;
            this.silentFrameDurationMs = Math.max(
                    1L,
                    Math.round(FRAME_SIZE_SAMPLES * 1000.0d / Math.max(1, sampleRateHz))
            );
            signalProcessor.setPreferredToneFrequencyHz(preferredToneHz);
            signalProcessor.setSqlPercent(sqlPercent);
            timingModel.setSeedWpm(seedWpm);
            wpmGuard.setSeedWpm(seedWpm);
        }

        private void run(List<AudioFrame> baseFrames) {
            if (baseFrames.isEmpty()) {
                throw new IllegalArgumentException("baseFrames is empty");
            }

            System.out.println(String.format(
                    Locale.US,
                    "==== repeated live-like session probe: %s | repeats=%d | silence=%dms | seed=%dwpm | sql=%d ====",
                    sourceLabel,
                    REPEAT_COUNT,
                    INTER_REPEAT_SILENCE_MS,
                    seedWpm,
                    sqlPercent
            ));

            long sessionCursorMs = 0L;
            for (int roundIndex = 1; roundIndex <= REPEAT_COUNT; roundIndex++) {
                RoundProbe roundProbe = new RoundProbe(roundIndex, sessionCursorMs, decoder.snapshot().decodedText().length());
                for (AudioFrame baseFrame : baseFrames) {
                    AudioFrame shiftedFrame = shiftFrame(baseFrame, sessionCursorMs);
                    processFrame(shiftedFrame, roundProbe);
                }
                sessionCursorMs += estimateFrameEndTimestampMs(baseFrames.get(baseFrames.size() - 1));
                roundProbe.captureRoundEnd(snapshotLine("round-end", sessionCursorMs), decoder.snapshot());
                System.out.println(roundProbe.renderRoundEnd());

                if (roundIndex >= REPEAT_COUNT) {
                    break;
                }

                sessionCursorMs = injectSilence(sessionCursorMs, INTER_REPEAT_SILENCE_MS, roundProbe);
                roundProbe.capturePostSilence(snapshotLine("post-silence", sessionCursorMs), decoder.snapshot());
                System.out.println(roundProbe.renderPostSilence());
            }

            CwSignalSnapshot signalSnapshot = signalProcessor.snapshot();
            CwTimingSnapshot timingSnapshot = timingModel.snapshot();
            CwDecoderSnapshot decoderSnapshot = decoder.snapshot();
            System.out.println(String.format(
                    Locale.US,
                    "FINAL text=\"%s\" chars=%d raw=%.2f display=%d hold=%s target=%d eff=%d lock=%.0f%% near=%.0f%% unl=%.0f%% %s | %s",
                    sanitize(decoderSnapshot.decodedText()),
                    decoderSnapshot.totalCharacters(),
                    timingSnapshot.estimatedWpmPrecise(),
                    wpmGuard.resolveDisplayWpm(signalSnapshot, timingSnapshot, sessionCursorMs),
                    wpmGuard.holding(),
                    signalSnapshot.targetToneFrequencyHz(),
                    signalSnapshot.effectiveTrackedToneFrequencyHz(),
                    signalSnapshot.recentLockedFrameRatio() * 100.0d,
                    signalSnapshot.recentNearTargetLockedFrameRatio() * 100.0d,
                    signalSnapshot.recentActiveUnlockedFrameRatio() * 100.0d,
                    wpmGuard.compactDebugSummary(signalSnapshot, timingSnapshot, sessionCursorMs),
                    toneEventStabilizer.stats().compactSummary()
            ));
        }

        private long injectSilence(long sessionCursorMs, long silenceMs, RoundProbe roundProbe) {
            long producedMs = 0L;
            long frameTimestampMs = sessionCursorMs;
            while (producedMs < silenceMs) {
                int sampleCount = FRAME_SIZE_SAMPLES;
                AudioFrame silentFrame = new AudioFrame(
                        new short[sampleCount],
                        sampleRateHz,
                        1,
                        0,
                        0.0d,
                        0,
                        frameTimestampMs
                );
                processFrame(silentFrame, roundProbe);
                frameTimestampMs += silentFrameDurationMs;
                producedMs += silentFrameDurationMs;
            }
            return frameTimestampMs;
        }

        private void processFrame(AudioFrame frame, RoundProbe roundProbe) {
            inputHealthTracker.process(frame);
            AudioInputHealthSnapshot inputHealthSnapshot = inputHealthTracker.snapshot();

            List<CwToneEvent> toneEvents = signalProcessor.process(frame);
            for (CwToneEvent toneEvent : toneEvents) {
                routeLiveLikeToneEvent(
                        toneEvent,
                        toneEvent.timestampMs(),
                        inputHealthSnapshot,
                        roundProbe
                );
            }

            long frameEndTimestampMs = estimateFrameEndTimestampMs(frame);
            flushLiveLikeToneEventStabilizer(frameEndTimestampMs, inputHealthSnapshot, roundProbe);
            maybeFlushPendingCharacterDuringSilence(frame, roundProbe);
            timingModel.observeClock(frameEndTimestampMs);
            roundProbe.observeSnapshot(frameEndTimestampMs, signalProcessor.snapshot(), timingModel.snapshot(), wpmGuard);
        }

        private void routeLiveLikeToneEvent(
                CwToneEvent toneEvent,
                long nowTimestampMs,
                AudioInputHealthSnapshot inputHealthSnapshot,
                RoundProbe roundProbe
        ) {
            if (toneEvent == null) {
                return;
            }
            CwSignalSnapshot currentSignalSnapshot = signalProcessor.snapshot();
            CwTimingSnapshot currentTimingSnapshot = timingModel.rawSnapshot();
            long referenceDotEstimateMs = resolveReferenceDotEstimateMs(
                    currentSignalSnapshot,
                    currentTimingSnapshot,
                    nowTimestampMs
            );
            List<CwToneEvent> stabilizedEvents = toneEventStabilizer.process(
                    toneEvent,
                    currentSignalSnapshot,
                    inputHealthSnapshot,
                    referenceDotEstimateMs
            );
            for (CwToneEvent stabilizedEvent : stabilizedEvents) {
                dispatchLiveLikeToneEvent(
                        stabilizedEvent,
                        stabilizedEvent.timestampMs(),
                        inputHealthSnapshot,
                        roundProbe
                );
            }
        }

        private void flushLiveLikeToneEventStabilizer(
                long nowTimestampMs,
                AudioInputHealthSnapshot inputHealthSnapshot,
                RoundProbe roundProbe
        ) {
            List<CwToneEvent> stabilizedEvents = toneEventStabilizer.flush(nowTimestampMs);
            for (CwToneEvent stabilizedEvent : stabilizedEvents) {
                dispatchLiveLikeToneEvent(
                        stabilizedEvent,
                        nowTimestampMs,
                        inputHealthSnapshot,
                        roundProbe
                );
            }
        }

        private void dispatchLiveLikeToneEvent(
                CwToneEvent toneEvent,
                long nowTimestampMs,
                AudioInputHealthSnapshot inputHealthSnapshot,
                RoundProbe roundProbe
        ) {
            if (toneEvent == null) {
                return;
            }
            CwSignalSnapshot signalSnapshot = signalProcessor.snapshot();
            CwTimingSnapshot timingSnapshot = timingModel.rawSnapshot();
            long referenceDotEstimateMs = resolveReferenceDotEstimateMs(
                    signalSnapshot,
                    timingSnapshot,
                    nowTimestampMs
            );
            if (referenceDotEstimateMs > 0L
                    && toneEventStabilizer.shouldSuppressShortTone(
                    toneEvent,
                    signalSnapshot,
                    inputHealthSnapshot,
                    referenceDotEstimateMs
            )) {
                return;
            }

            CwSignalSnapshot currentSignalSnapshot = signalProcessor.snapshot();
            CwTimingSnapshot currentTimingSnapshot = timingModel.rawSnapshot();
            boolean allowTimingLearning = wpmGuard.shouldAllowTimingLearningForEvent(
                    toneEvent,
                    currentSignalSnapshot,
                    currentTimingSnapshot,
                    nowTimestampMs
            );
            List<CwTimingEvent> timingEvents = timingModel.process(toneEvent, allowTimingLearning);
            currentSignalSnapshot = signalProcessor.snapshot();
            currentTimingSnapshot = timingModel.rawSnapshot();
            for (CwTimingEvent timingEvent : timingEvents) {
                CwTimingEvent adaptedTimingEvent = wpmGuard.adaptTimingEvent(
                        timingEvent,
                        currentSignalSnapshot,
                        currentTimingSnapshot,
                        nowTimestampMs
                );
                processLiveLikeTimingEvent(adaptedTimingEvent, roundProbe);
            }
        }

        private void processLiveLikeTimingEvent(CwTimingEvent timingEvent, RoundProbe roundProbe) {
            if (timingEvent == null) {
                return;
            }
            List<CwDecodeEvent> decodeEvents = decoder.process(timingEvent);
            for (CwDecodeEvent decodeEvent : decodeEvents) {
                processLiveLikeDecodeEvent(decodeEvent, roundProbe);
            }
        }

        private void processLiveLikeDecodeEvent(CwDecodeEvent decodeEvent, RoundProbe roundProbe) {
            if (decodeEvent == null) {
                return;
            }
            boolean stableDecode = shouldTreatAsStableDecode(
                    decodeEvent,
                    signalProcessor.snapshot(),
                    timingModel.rawSnapshot()
            );
            if (stableDecode) {
                timingModel.notifyStableDecode(decodeEvent.timestampMs());
            }
            if (decodeEvent.type() == CwDecodeEvent.Type.CHARACTER_DECODED) {
                CwSignalSnapshot signalSnapshot = signalProcessor.snapshot();
                CwTimingSnapshot timingSnapshot = timingModel.rawSnapshot();
                wpmGuard.noteDecodedCharacter(
                        decodeEvent.unknownCharacter(),
                        signalSnapshot,
                        timingSnapshot,
                        decodeEvent.timestampMs()
                );
                roundProbe.observeDecode(
                        decodeEvent,
                        stableDecode,
                        signalSnapshot,
                        timingSnapshot,
                        wpmGuard
                );
            }
            interpreter.process(decodeEvent);
            qsoStateMachine.process(interpreter.snapshot(), decodeEvent.timestampMs());
        }

        private void maybeFlushPendingCharacterDuringSilence(AudioFrame frame, RoundProbe roundProbe) {
            if (frame == null || !decoder.hasPendingCharacter()) {
                return;
            }
            CwSignalSnapshot signalSnapshot = signalProcessor.snapshot();
            if (signalSnapshot.toneActive()) {
                return;
            }
            CwToneEvent lastSignalEvent = signalSnapshot.lastEvent();
            if (lastSignalEvent == null || lastSignalEvent.type() != CwToneEvent.Type.TONE_OFF) {
                return;
            }

            long flushTimestampMs = estimateFrameEndTimestampMs(frame);
            long silentGapMs = Math.max(0L, flushTimestampMs - lastSignalEvent.timestampMs());
            long minFlushGapMs = minimumLiveCharacterFlushGapMs(
                    signalSnapshot,
                    timingModel.rawSnapshot(),
                    flushTimestampMs
            );
            if (silentGapMs < minFlushGapMs) {
                return;
            }

            List<CwDecodeEvent> trailingDecodeEvents = decoder.flushPendingCharacter(flushTimestampMs);
            for (CwDecodeEvent decodeEvent : trailingDecodeEvents) {
                processLiveLikeDecodeEvent(decodeEvent, roundProbe);
            }
        }

        private long resolveReferenceDotEstimateMs(
                CwSignalSnapshot signalSnapshot,
                CwTimingSnapshot timingSnapshot,
                long nowTimestampMs
        ) {
            long referenceDotEstimateMs = wpmGuard.resolveReferenceDotEstimateMs(timingSnapshot);
            if (referenceDotEstimateMs > 0L) {
                return referenceDotEstimateMs;
            }
            return wpmGuard.resolveEffectiveDotEstimateMs(signalSnapshot, timingSnapshot, nowTimestampMs);
        }

        private long minimumLiveCharacterFlushGapMs(
                CwSignalSnapshot signalSnapshot,
                CwTimingSnapshot timingSnapshot,
                long nowTimestampMs
        ) {
            long dotEstimateMs = Math.max(
                    1L,
                    wpmGuard.resolveEffectiveDotEstimateMs(signalSnapshot, timingSnapshot, nowTimestampMs)
            );
            return Math.max(1L, Math.round(dotEstimateMs * LIVE_CHARACTER_FLUSH_GAP_RATIO));
        }

        private boolean shouldTreatAsStableDecode(
                CwDecodeEvent decodeEvent,
                CwSignalSnapshot signalSnapshot,
                CwTimingSnapshot timingSnapshot
        ) {
            if (decodeEvent == null
                    || decodeEvent.type() != CwDecodeEvent.Type.CHARACTER_DECODED
                    || decodeEvent.unknownCharacter()
                    || signalSnapshot == null
                    || timingSnapshot == null
                    || timingSnapshot.estimatedWpm() <= 0
                    || timingSnapshot.dotEstimateMs() <= 0L) {
                return false;
            }
            if (!wpmGuard.shouldAcceptTimingAnchorUpdate(
                    signalSnapshot,
                    timingSnapshot,
                    decodeEvent.timestampMs()
            )) {
                return false;
            }
            return signalSnapshot.targetToneLocked()
                    && signalSnapshot.recentLockedFrameRatio() >= STABLE_DECODE_LOCKED_RATIO_MIN
                    && signalSnapshot.recentNearTargetLockedFrameRatio() >= STABLE_DECODE_NEAR_TARGET_RATIO_MIN
                    && signalSnapshot.recentActiveUnlockedFrameRatio() <= STABLE_DECODE_ACTIVE_UNLOCKED_RATIO_MAX
                    && signalSnapshot.toneDominanceRatio() >= STABLE_DECODE_TONE_DOMINANCE_MIN
                    && signalSnapshot.narrowbandIsolationRatio() >= STABLE_DECODE_ISOLATION_MIN;
        }

        private String snapshotLine(String label, long nowTimestampMs) {
            CwSignalSnapshot signalSnapshot = signalProcessor.snapshot();
            CwTimingSnapshot timingSnapshot = timingModel.snapshot();
            CwTimingSnapshot rawTimingSnapshot = timingModel.rawSnapshot();
            long lastStableDecodeTimestampMs = reflectLongField(timingModel, "lastStableDecodeTimestampMs");
            double trustedDotEstimateMs = reflectDoubleField(timingModel, "trustedDotEstimateMs");
            return String.format(
                    Locale.US,
                    "%s @%d stable=%.2f raw=%.2f display=%d hold=%s target=%d eff=%d lock=%.0f%% near=%.0f%% unl=%.0f%% trustedDot=%.2f lastStable=%d strategy=%s %s | %s",
                    label,
                    nowTimestampMs,
                    timingSnapshot.estimatedWpmPrecise(),
                    rawTimingSnapshot.estimatedWpmPrecise(),
                    wpmGuard.resolveDisplayWpm(signalSnapshot, timingSnapshot, nowTimestampMs),
                    wpmGuard.holding(),
                    signalSnapshot.targetToneFrequencyHz(),
                    signalSnapshot.effectiveTrackedToneFrequencyHz(),
                    signalSnapshot.recentLockedFrameRatio() * 100.0d,
                    signalSnapshot.recentNearTargetLockedFrameRatio() * 100.0d,
                    signalSnapshot.recentActiveUnlockedFrameRatio() * 100.0d,
                    trustedDotEstimateMs,
                    lastStableDecodeTimestampMs,
                    timingModel.debugStrategySummary(),
                    wpmGuard.compactDebugSummary(signalSnapshot, rawTimingSnapshot, nowTimestampMs),
                    toneEventStabilizer.stats().compactSummary()
            );
        }

        private long reflectLongField(Object target, String fieldName) {
            try {
                Field field = target.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.getLong(target);
            } catch (Exception exception) {
                return Long.MIN_VALUE;
            }
        }

        private double reflectDoubleField(Object target, String fieldName) {
            try {
                Field field = target.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.getDouble(target);
            } catch (Exception exception) {
                return Double.NaN;
            }
        }
    }

    private static final class RoundProbe {
        private final int roundIndex;
        private final long startTimestampMs;
        private final int decodedTextStartLength;

        private double maxRawWpm;
        private int maxDisplayWpm;
        private long maxRawTimestampMs;
        private long maxDisplayTimestampMs;
        private int stableDecodeCount;
        private int decodedCharacterCount;
        private int unknownCharacterCount;
        private int holdFrameCount;
        private int decodeTargetLockedCount;
        private int decodeAcceptAnchorCount;
        private int decodeSignalThresholdPassCount;
        private double maxDecodeLockedRatio;
        private double maxDecodeNearTargetRatio;
        private double minDecodeUnlockedRatio = 1.0d;
        private double maxDecodeToneDominanceRatio;
        private double maxDecodeIsolationRatio;
        private double maxDecodeRawWpm;
        private int highWatermarkLogThreshold = 26;
        private String roundEndSnapshot = "";
        private String postSilenceSnapshot = "";
        private String roundDecodedText = "";
        private String totalDecodedTextAtRoundEnd = "";
        private String totalDecodedTextAfterSilence = "";

        private RoundProbe(int roundIndex, long startTimestampMs, int decodedTextStartLength) {
            this.roundIndex = roundIndex;
            this.startTimestampMs = startTimestampMs;
            this.decodedTextStartLength = decodedTextStartLength;
        }

        private void observeSnapshot(
                long timestampMs,
                CwSignalSnapshot signalSnapshot,
                CwTimingSnapshot timingSnapshot,
                LiveRxWpmGuard wpmGuard
        ) {
            double rawWpm = timingSnapshot == null ? 0.0d : timingSnapshot.estimatedWpmPrecise();
            int displayWpm = wpmGuard.resolveDisplayWpm(signalSnapshot, timingSnapshot, timestampMs);
            if (rawWpm > maxRawWpm) {
                maxRawWpm = rawWpm;
                maxRawTimestampMs = timestampMs;
            }
            if (displayWpm > maxDisplayWpm) {
                maxDisplayWpm = displayWpm;
                maxDisplayTimestampMs = timestampMs;
            }
            if (wpmGuard.holding()) {
                holdFrameCount += 1;
            }
            while (rawWpm >= highWatermarkLogThreshold) {
                System.out.println(String.format(
                        Locale.US,
                        "round=%d high-water raw=%.2f display=%d @%d hold=%s target=%d eff=%d lock=%.0f%% near=%.0f%% unl=%.0f%%",
                        roundIndex,
                        rawWpm,
                        displayWpm,
                        timestampMs,
                        wpmGuard.holding(),
                        signalSnapshot.targetToneFrequencyHz(),
                        signalSnapshot.effectiveTrackedToneFrequencyHz(),
                        signalSnapshot.recentLockedFrameRatio() * 100.0d,
                        signalSnapshot.recentNearTargetLockedFrameRatio() * 100.0d,
                        signalSnapshot.recentActiveUnlockedFrameRatio() * 100.0d
                ));
                highWatermarkLogThreshold += 1;
            }
        }

        private void observeDecode(
                CwDecodeEvent decodeEvent,
                boolean stableDecode,
                CwSignalSnapshot signalSnapshot,
                CwTimingSnapshot timingSnapshot,
                LiveRxWpmGuard wpmGuard
        ) {
            decodedCharacterCount += 1;
            if (decodeEvent.unknownCharacter()) {
                unknownCharacterCount += 1;
            }
            if (stableDecode) {
                stableDecodeCount += 1;
            }
            if (signalSnapshot == null) {
                return;
            }
            if (signalSnapshot.targetToneLocked()) {
                decodeTargetLockedCount += 1;
            }
            if (wpmGuard.shouldAcceptTimingAnchorUpdate(
                    signalSnapshot,
                    timingSnapshot,
                    decodeEvent.timestampMs()
            )) {
                decodeAcceptAnchorCount += 1;
            }
            if (signalSnapshot.targetToneLocked()
                    && signalSnapshot.recentLockedFrameRatio() >= STABLE_DECODE_LOCKED_RATIO_MIN
                    && signalSnapshot.recentNearTargetLockedFrameRatio() >= STABLE_DECODE_NEAR_TARGET_RATIO_MIN
                    && signalSnapshot.recentActiveUnlockedFrameRatio() <= STABLE_DECODE_ACTIVE_UNLOCKED_RATIO_MAX
                    && signalSnapshot.toneDominanceRatio() >= STABLE_DECODE_TONE_DOMINANCE_MIN
                    && signalSnapshot.narrowbandIsolationRatio() >= STABLE_DECODE_ISOLATION_MIN) {
                decodeSignalThresholdPassCount += 1;
            }
            maxDecodeLockedRatio = Math.max(maxDecodeLockedRatio, signalSnapshot.recentLockedFrameRatio());
            maxDecodeNearTargetRatio = Math.max(
                    maxDecodeNearTargetRatio,
                    signalSnapshot.recentNearTargetLockedFrameRatio()
            );
            minDecodeUnlockedRatio = Math.min(
                    minDecodeUnlockedRatio,
                    signalSnapshot.recentActiveUnlockedFrameRatio()
            );
            maxDecodeToneDominanceRatio = Math.max(
                    maxDecodeToneDominanceRatio,
                    signalSnapshot.toneDominanceRatio()
            );
            maxDecodeIsolationRatio = Math.max(
                    maxDecodeIsolationRatio,
                    signalSnapshot.narrowbandIsolationRatio()
            );
            if (timingSnapshot != null) {
                maxDecodeRawWpm = Math.max(maxDecodeRawWpm, timingSnapshot.estimatedWpmPrecise());
            }
        }

        private void captureRoundEnd(String snapshot, CwDecoderSnapshot decoderSnapshot) {
            roundEndSnapshot = snapshot;
            totalDecodedTextAtRoundEnd = sanitize(decoderSnapshot.decodedText());
            roundDecodedText = sanitize(sliceDecodedText(decoderSnapshot.decodedText()));
        }

        private void capturePostSilence(String snapshot, CwDecoderSnapshot decoderSnapshot) {
            postSilenceSnapshot = snapshot;
            totalDecodedTextAfterSilence = sanitize(decoderSnapshot.decodedText());
        }

        private String renderRoundEnd() {
            return String.format(
                    Locale.US,
                    "ROUND %d end start@%d chars=%d unknown=%d stable=%d maxRaw=%.2f@%d maxDisplay=%d@%d holdFrames=%d"
                            + " decLock=%d decAccept=%d decPass=%d"
                            + " decMaxLock=%.0f%% decMaxNear=%.0f%% decMinUnl=%.0f%% decMaxDom=%.0f%% decMaxIso=%.0f%% decMaxRaw=%.2f"
                            + " newText=\"%s\" total=\"%s\" %s",
                    roundIndex,
                    startTimestampMs,
                    decodedCharacterCount,
                    unknownCharacterCount,
                    stableDecodeCount,
                    maxRawWpm,
                    maxRawTimestampMs,
                    maxDisplayWpm,
                    maxDisplayTimestampMs,
                    holdFrameCount,
                    decodeTargetLockedCount,
                    decodeAcceptAnchorCount,
                    decodeSignalThresholdPassCount,
                    maxDecodeLockedRatio * 100.0d,
                    maxDecodeNearTargetRatio * 100.0d,
                    minDecodeUnlockedRatio * 100.0d,
                    maxDecodeToneDominanceRatio * 100.0d,
                    maxDecodeIsolationRatio * 100.0d,
                    maxDecodeRawWpm,
                    roundDecodedText,
                    totalDecodedTextAtRoundEnd,
                    roundEndSnapshot
            );
        }

        private String renderPostSilence() {
            return String.format(
                    Locale.US,
                    "ROUND %d post-silence total=\"%s\" %s",
                    roundIndex,
                    totalDecodedTextAfterSilence,
                    postSilenceSnapshot
            );
        }

        private String sliceDecodedText(String decodedText) {
            if (decodedText == null || decodedText.isEmpty()) {
                return "";
            }
            if (decodedTextStartLength >= decodedText.length()) {
                return "";
            }
            return decodedText.substring(decodedTextStartLength);
        }
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

    private static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace(CwDecoder.UNKNOWN_CHARACTER, "?").trim();
        return normalized.isEmpty() ? "(empty)" : normalized;
    }
}
