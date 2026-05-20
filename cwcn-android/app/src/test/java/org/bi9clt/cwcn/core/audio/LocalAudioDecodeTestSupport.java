package org.bi9clt.cwcn.core.audio;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.decoder.CwDecoder;
import org.bi9clt.cwcn.core.decoder.CwDecoderSnapshot;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.interpreter.CwInterpreterSnapshot;
import org.bi9clt.cwcn.core.qso.QsoDraftSnapshot;
import org.bi9clt.cwcn.core.qso.QsoStateMachine;
import org.bi9clt.cwcn.core.rx.CwFrontEndAuthorityTracker;
import org.bi9clt.cwcn.core.rx.CwFrontEndLearningGate;
import org.bi9clt.cwcn.core.rx.LiveRxToneEventStabilizer;
import org.bi9clt.cwcn.core.rx.LiveRxWpmGuard;
import org.bi9clt.cwcn.core.rx.RxBootstrapTimingObserver;
import org.bi9clt.cwcn.core.rx.RxFrameSignalRunner;
import org.bi9clt.cwcn.core.rx.RxRawCommitGate;
import org.bi9clt.cwcn.core.rx.RxReplaySessionRunner;
import org.bi9clt.cwcn.core.rx.RxStableDecodeDecider;
import org.bi9clt.cwcn.core.rx.RxTimingDecodeRunner;
import org.bi9clt.cwcn.core.rx.RxToneTimingRunner;
import org.bi9clt.cwcn.core.rx.RxToneModeBootstrapDecider;
import org.bi9clt.cwcn.core.rx.RxTrailingWindowRepair;
import org.bi9clt.cwcn.core.rx.RxTurnActivityDecider;
import org.bi9clt.cwcn.core.rx.RxTurnController;
import org.bi9clt.cwcn.core.rx.RxTurnSessionCoordinator;
import org.bi9clt.cwcn.core.rx.TimingAnchorController;
import org.bi9clt.cwcn.core.rx.experimental.RxLearningAuthority;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel.DebugSnapshot;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class LocalAudioDecodeTestSupport {
    private static final int FRAME_SIZE_SAMPLES = 256;
    private static final int CLIPPING_SAMPLE_THRESHOLD = 32700;
    private static final int DEFAULT_SQL_PERCENT = 55;
    private static final double LIVE_CHARACTER_FLUSH_GAP_RATIO = 3.35d;
    private static final long FRONT_END_AUTHORITY_HOLD_MS = 420L;
    private static final ThreadLocal<LiveLikeProbeDiagnosticsCollector> LIVE_LIKE_DIAGNOSTICS =
            new ThreadLocal<>();
    private static final ThreadLocal<Long> LIVE_LIKE_FIXED_HOLD_UNTIL_MS = new ThreadLocal<>();
    private static final ThreadLocal<LiveLikeFrameHook> LIVE_LIKE_FRAME_HOOK = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> LIVE_LIKE_FORCE_WIDE_ACQUISITION = new ThreadLocal<>();
    private static final ThreadLocal<Integer> LIVE_LIKE_FIXED_TONE_LEARNING_WINDOW_HZ =
            new ThreadLocal<>();

    private enum LiveLikeRxToneModeStrategy {
        HYBRID_BOOTSTRAP,
        FIXED_HOLD_THEN_AUTO,
        FIXED_UNTIL_TRUST_THEN_AUTO,
        STATIC_AUTO_TRACK,
        STATIC_FIXED_TONE
    }

    private LocalAudioDecodeTestSupport() {
    }

    @FunctionalInterface
    interface LiveLikeFrameHook {
        void beforeProcessFrame(AudioFrame frame, CwSignalProcessor signalProcessor, int frameIndex);
    }

    static Path findTestAudioDir() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        for (Path probe = current; probe != null; probe = probe.getParent()) {
            Path candidate = probe.resolve("TestAudio");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to locate TestAudio directory from " + current);
    }

    static Path findTraceDir() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        for (Path probe = current; probe != null; probe = probe.getParent()) {
            Path candidate = probe.resolve("Trace");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to locate Trace directory from " + current);
    }

    static List<Path> listConvertedWavFiles() throws IOException {
        Path wavDir = findTestAudioDir().resolve("wav");
        if (!Files.isDirectory(wavDir)) {
            throw new IOException("Converted WAV folder is missing: " + wavDir);
        }
        try (Stream<Path> stream = Files.list(wavDir)) {
            return stream
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.US).endsWith(".wav"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());
        }
    }

    static OfflineProbeResult decodeWavFile(Path wavFile) throws IOException {
        return decodeWavFileDetailed(wavFile).probeResult();
    }

    static OfflineDetailedProbeResult decodeWavFileDetailed(Path wavFile) throws IOException {
        return decodeWavFileDetailed(wavFile, false);
    }

    static OfflineDetailedProbeResult decodeWavFileDetailed(
            Path wavFile,
            boolean experimentalHypothesisGuardEnabled
    ) throws IOException {
        return decodeFramesDetailed(
                sourceLabelFromWavFile(wavFile),
                loadFramesFromWavFile(wavFile),
                experimentalHypothesisGuardEnabled
        );
    }

    static OfflineProbeResult decodeWavFileLiveLike(
            Path wavFile,
            int preferredToneFrequencyHz,
            int seedWpm,
            int sqlPercent,
            boolean experimentalHypothesisGuardEnabled,
            CwInterpreter.RecoveryMode recoveryMode
    ) throws IOException {
        return decodeWavFileDetailedLiveLike(
                wavFile,
                preferredToneFrequencyHz,
                seedWpm,
                sqlPercent,
                experimentalHypothesisGuardEnabled,
                recoveryMode
        ).probeResult();
    }

    static OfflineDetailedProbeResult decodeWavFileDetailedLiveLike(
            Path wavFile,
            int preferredToneFrequencyHz,
            int seedWpm,
            int sqlPercent,
            boolean experimentalHypothesisGuardEnabled,
            CwInterpreter.RecoveryMode recoveryMode
    ) throws IOException {
        return decodeFramesDetailedLiveLike(
                sourceLabelFromWavFile(wavFile),
                normalizeFramesToZero(loadFramesFromWavFile(wavFile)),
                preferredToneFrequencyHz,
                seedWpm,
                sqlPercent,
                experimentalHypothesisGuardEnabled,
                recoveryMode
        );
    }

    static OfflineDetailedProbeResult decodeFramesDetailed(
            String sourceLabel,
            List<AudioFrame> frames,
            boolean experimentalHypothesisGuardEnabled
    ) {
        CwSignalProcessor signalProcessor = new CwSignalProcessor();
        signalProcessor.setExperimentalHypothesisGuardEnabled(experimentalHypothesisGuardEnabled);
        CwHybridTimingModel timingModel = new CwHybridTimingModel();
        CwDecoder decoder = new CwDecoder();
        CwInterpreter interpreter = new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);
        QsoStateMachine qsoStateMachine = new QsoStateMachine();
        ArrayList<AudioFrame> capturedFrames = new ArrayList<>();
        ArrayList<CwToneEvent> capturedToneEvents = new ArrayList<>();
        ArrayList<CwTimingEvent> capturedTimingEvents = new ArrayList<>();
        ArrayList<CwDecodeEvent> capturedDecodeEvents = new ArrayList<>();
        ArrayList<FrameSignalTrace> frameSignalTraces = new ArrayList<>();
        ArrayList<TimingStateTrace> timingStateTraces = new ArrayList<>();
        ArrayList<TurnTransitionTrace> capturedTurnTransitionTraces = new ArrayList<>();

        if (frames != null) {
            capturedFrames.addAll(frames);
        }
        long flushTimestampMs = runSimpleReplaySession(
                frames,
                signalProcessor,
                timingModel,
                decoder,
                interpreter,
                qsoStateMachine,
                capturedToneEvents,
                capturedTimingEvents,
                capturedDecodeEvents,
                frameSignalTraces,
                timingStateTraces
        );

        OfflineProbeResult probeResult = new OfflineProbeResult(
                sourceLabel,
                sanitize(interpreter.snapshot().rawText()),
                signalProcessor.snapshot(),
                signalProcessor.debugActiveLeaderCompactSummary(),
                timingModel.snapshot(),
                timingModel.debugStrategySummary(),
                decoder.snapshot(),
                interpreter.snapshot()
        );
        OfflineDetailedProbeResult detailedProbeResult = new OfflineDetailedProbeResult(
                probeResult,
                capturedFrames,
                capturedToneEvents,
                capturedTimingEvents,
                capturedDecodeEvents,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new LinkedHashMap<>(),
                new LinkedHashMap<>(),
                new LinkedHashMap<>(),
                frameSignalTraces,
                timingStateTraces,
                new ArrayList<>(),
                new ArrayList<>(),
                flushTimestampMs
        );
        return detailedProbeResult;
    }

    static OfflineDetailedProbeResult decodeFramesDetailedConfigured(
            String sourceLabel,
            List<AudioFrame> frames,
            int preferredToneFrequencyHz,
            int seedWpm,
            int sqlPercent,
            boolean experimentalHypothesisGuardEnabled,
            CwInterpreter.RecoveryMode recoveryMode
    ) {
        CwSignalProcessor signalProcessor = new CwSignalProcessor();
        signalProcessor.setPreferredToneFrequencyHz(preferredToneFrequencyHz);
        signalProcessor.setSqlPercent(sqlPercent);
        Integer fixedToneLearningWindowHz = LIVE_LIKE_FIXED_TONE_LEARNING_WINDOW_HZ.get();
        if (fixedToneLearningWindowHz != null) {
            signalProcessor.setFixedToneLearningWindowHz(fixedToneLearningWindowHz);
        }
        signalProcessor.setExperimentalHypothesisGuardEnabled(experimentalHypothesisGuardEnabled);
        signalProcessor.setExperimentalForceWideAcquisitionEnabled(
                Boolean.TRUE.equals(LIVE_LIKE_FORCE_WIDE_ACQUISITION.get())
        );
        CwHybridTimingModel timingModel = new CwHybridTimingModel();
        timingModel.setIdleResetEnabled(false);
        timingModel.setSeedWpm(seedWpm);
        CwDecoder decoder = new CwDecoder();
        CwInterpreter interpreter = new CwInterpreter(recoveryMode);
        QsoStateMachine qsoStateMachine = new QsoStateMachine();
        ArrayList<AudioFrame> capturedFrames = new ArrayList<>();
        ArrayList<CwToneEvent> capturedToneEvents = new ArrayList<>();
        ArrayList<CwTimingEvent> capturedTimingEvents = new ArrayList<>();
        ArrayList<CwDecodeEvent> capturedDecodeEvents = new ArrayList<>();
        ArrayList<FrameSignalTrace> frameSignalTraces = new ArrayList<>();
        ArrayList<TimingStateTrace> timingStateTraces = new ArrayList<>();

        if (frames != null) {
            capturedFrames.addAll(frames);
        }
        long flushTimestampMs = runSimpleReplaySession(
                frames,
                signalProcessor,
                timingModel,
                decoder,
                interpreter,
                qsoStateMachine,
                capturedToneEvents,
                capturedTimingEvents,
                capturedDecodeEvents,
                frameSignalTraces,
                timingStateTraces
        );

        OfflineProbeResult probeResult = new OfflineProbeResult(
                sourceLabel,
                sanitize(interpreter.snapshot().rawText()),
                signalProcessor.snapshot(),
                signalProcessor.debugActiveLeaderCompactSummary(),
                timingModel.snapshot(),
                timingModel.debugStrategySummary(),
                decoder.snapshot(),
                interpreter.snapshot()
        );
        OfflineDetailedProbeResult detailedProbeResult = new OfflineDetailedProbeResult(
                probeResult,
                capturedFrames,
                capturedToneEvents,
                capturedTimingEvents,
                capturedDecodeEvents,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new LinkedHashMap<>(),
                new LinkedHashMap<>(),
                new LinkedHashMap<>(),
                frameSignalTraces,
                timingStateTraces,
                new ArrayList<>(),
                new ArrayList<>(),
                flushTimestampMs
        );
        return detailedProbeResult;
    }

    private static FrameSignalTrace buildFrameSignalTrace(
            AudioFrame frame,
            CwSignalProcessor signalProcessor
    ) {
        return new FrameSignalTrace(
                frame.capturedAtMs(),
                signalProcessor.snapshot(),
                signalProcessor.lastDetectionLevel(),
                signalProcessor.weakValleyBridgeActive(),
                signalProcessor.weakValleyBridgeFramesRemaining(),
                signalProcessor.lastAttackQualified(),
                signalProcessor.lastTrackedToneMemoryActiveBeforeFrame(),
                signalProcessor.lastAttackAnchorFrequencyHzBeforeFrame(),
                signalProcessor.lastToneOnThreshold(),
                signalProcessor.lastFrameLocalToneOnTimestampMs(),
                signalProcessor.lastPostReleaseGapMs(),
                signalProcessor.lastPostReleaseWindowMs(),
                signalProcessor.postReleaseRescueContinuationWindowActive(frame.capturedAtMs()),
                signalProcessor.postReleaseRescueContinuationWindowRemainingMs(frame.capturedAtMs()),
                signalProcessor.postReleaseWeakContinuityRescueCount(),
                signalProcessor.trustedWeakPostReleaseOnsetChainActive(),
                signalProcessor.trustedWeakPostReleaseOnsetChainFrameCount(),
                signalProcessor.trustedWeakPostReleaseOnsetChainStartMs(),
                signalProcessor.postReleaseWeakContinuityGapLimitMs(),
                signalProcessor.lastWeakPostReleaseOnsetChainCandidate(),
                signalProcessor.lastTrustedContinuityToneOnCandidate(),
                signalProcessor.lastLocalContrastRatio(),
                signalProcessor.lastSteadyLateGapNearTargetRescueCandidate(),
                signalProcessor.lastLowGrowthStrongSteadyNearTargetRescue(),
                signalProcessor.lastNearTargetPostReleaseToneOnRescue(),
                signalProcessor.lastPostReleaseSteadyCarrierSuppressed(),
                signalProcessor.lastFarAttackToneOnDelayed(),
                signalProcessor.lastToneOnAccepted(),
                signalProcessor.lastToneOnAcceptedByRescue(),
                signalProcessor.currentToneStartedByPostReleaseRescue(),
                signalProcessor.lastReleaseTailHoldApplied(),
                signalProcessor.currentToneRunWeakBootstrapReleaseTailHoldCount(),
                signalProcessor.lastToneActiveReleaseThreshold(),
                signalProcessor.lastReleaseTailHoldRequiredDetectionThreshold(),
                signalProcessor.lastReleaseTailHoldSufficientRecentTrust(),
                signalProcessor.lastReleaseTailHoldCurrentRunStableBootstrapEligible(),
                signalProcessor.lastReleaseTailHoldCurrentRunWeakBootstrapEligible(),
                signalProcessor.lastPostReleaseRescueDecision(),
                signalProcessor.lastPostReleaseSuppressionDecision(),
                signalProcessor.lastFarAttackDelayDecision(),
                signalProcessor.lastToneOnDecision(),
                signalProcessor.lastReleaseTailHoldDecision()
        );
    }

    static OfflineDetailedProbeResult decodeFramesDetailedLiveLike(
            String sourceLabel,
            List<AudioFrame> frames,
            int preferredToneFrequencyHz,
            int seedWpm,
            boolean experimentalHypothesisGuardEnabled,
            CwInterpreter.RecoveryMode recoveryMode
    ) {
        return decodeFramesDetailedLiveLike(
                sourceLabel,
                frames,
                preferredToneFrequencyHz,
                seedWpm,
                DEFAULT_SQL_PERCENT,
                experimentalHypothesisGuardEnabled,
                recoveryMode
        );
    }

    static OfflineDetailedProbeResult decodeFramesDetailedLiveLike(
            String sourceLabel,
            List<AudioFrame> frames,
            int preferredToneFrequencyHz,
            int seedWpm,
            int sqlPercent,
            boolean experimentalHypothesisGuardEnabled,
            boolean experimentalFrontEndAuthorityGateEnabled,
            CwInterpreter.RecoveryMode recoveryMode
    ) {
        return decodeFramesDetailedLiveLikeWithTurnCarry(
                sourceLabel,
                frames,
                preferredToneFrequencyHz,
                seedWpm,
                sqlPercent,
                experimentalHypothesisGuardEnabled,
                false,
                experimentalFrontEndAuthorityGateEnabled,
                recoveryMode
        );
    }

    static OfflineDetailedProbeResult decodeFramesDetailedLiveLikeWithoutFinalTailRepair(
            String sourceLabel,
            List<AudioFrame> frames,
            int preferredToneFrequencyHz,
            int seedWpm,
            int sqlPercent,
            boolean experimentalHypothesisGuardEnabled,
            CwInterpreter.RecoveryMode recoveryMode
    ) {
        return decodeFramesDetailedLiveLike(
                sourceLabel,
                frames,
                preferredToneFrequencyHz,
                seedWpm,
                sqlPercent,
                experimentalHypothesisGuardEnabled,
                false,
                false,
                false,
                false,
                false,
                LiveLikeRxToneModeStrategy.HYBRID_BOOTSTRAP,
                recoveryMode,
                false
        );
    }

    static OfflineDetailedProbeResult decodeFramesDetailedLiveLikeWithSoftStableBootstrapCandidate(
            String sourceLabel,
            List<AudioFrame> frames,
            int preferredToneFrequencyHz,
            int seedWpm,
            int sqlPercent,
            boolean experimentalHypothesisGuardEnabled,
            CwInterpreter.RecoveryMode recoveryMode
    ) {
        return decodeFramesDetailedLiveLike(
                sourceLabel,
                frames,
                preferredToneFrequencyHz,
                seedWpm,
                sqlPercent,
                experimentalHypothesisGuardEnabled,
                false,
                false,
                false,
                false,
                false,
                LiveLikeRxToneModeStrategy.HYBRID_BOOTSTRAP,
                recoveryMode,
                true,
                true
        );
    }

    static OfflineDetailedProbeResult decodeFramesDetailedLiveLikeFixedUntilTrustThenAuto(
            String sourceLabel,
            List<AudioFrame> frames,
            int preferredToneFrequencyHz,
            int seedWpm,
            int sqlPercent,
            boolean experimentalHypothesisGuardEnabled,
            CwInterpreter.RecoveryMode recoveryMode
    ) {
        return decodeFramesDetailedLiveLike(
                sourceLabel,
                frames,
                preferredToneFrequencyHz,
                seedWpm,
                sqlPercent,
                experimentalHypothesisGuardEnabled,
                false,
                false,
                false,
                false,
                false,
                LiveLikeRxToneModeStrategy.FIXED_UNTIL_TRUST_THEN_AUTO,
                recoveryMode
        );
    }

    static OfflineDetailedProbeResult decodeFramesDetailedLiveLikeFixedHoldThenAuto(
            String sourceLabel,
            List<AudioFrame> frames,
            int preferredToneFrequencyHz,
            int seedWpm,
            int sqlPercent,
            boolean experimentalHypothesisGuardEnabled,
            long fixedHoldUntilMs,
            CwInterpreter.RecoveryMode recoveryMode
    ) {
        LIVE_LIKE_FIXED_HOLD_UNTIL_MS.set(Math.max(0L, fixedHoldUntilMs));
        try {
            return decodeFramesDetailedLiveLike(
                    sourceLabel,
                    frames,
                    preferredToneFrequencyHz,
                    seedWpm,
                    sqlPercent,
                    experimentalHypothesisGuardEnabled,
                    false,
                    false,
                    false,
                    false,
                    false,
                    LiveLikeRxToneModeStrategy.FIXED_HOLD_THEN_AUTO,
                    recoveryMode
            );
        } finally {
            LIVE_LIKE_FIXED_HOLD_UNTIL_MS.remove();
        }
    }

    static OfflineDetailedProbeResult decodeFramesDetailedLiveLikeFixedHoldThenAutoWithFixedToneLearningWindow(
            String sourceLabel,
            List<AudioFrame> frames,
            int preferredToneFrequencyHz,
            int seedWpm,
            int sqlPercent,
            boolean experimentalHypothesisGuardEnabled,
            long fixedHoldUntilMs,
            int fixedToneLearningWindowHz,
            CwInterpreter.RecoveryMode recoveryMode
    ) {
        LIVE_LIKE_FIXED_HOLD_UNTIL_MS.set(Math.max(0L, fixedHoldUntilMs));
        LIVE_LIKE_FIXED_TONE_LEARNING_WINDOW_HZ.set(
                CwSignalProcessor.clampFixedToneLearningWindowHz(fixedToneLearningWindowHz)
        );
        try {
            return decodeFramesDetailedLiveLike(
                    sourceLabel,
                    frames,
                    preferredToneFrequencyHz,
                    seedWpm,
                    sqlPercent,
                    experimentalHypothesisGuardEnabled,
                    false,
                    false,
                    false,
                    false,
                    false,
                    LiveLikeRxToneModeStrategy.FIXED_HOLD_THEN_AUTO,
                    recoveryMode
            );
        } finally {
            LIVE_LIKE_FIXED_TONE_LEARNING_WINDOW_HZ.remove();
            LIVE_LIKE_FIXED_HOLD_UNTIL_MS.remove();
        }
    }

    static OfflineDetailedProbeResult decodeFramesDetailedLiveLikeWithFrameHook(
            String sourceLabel,
            List<AudioFrame> frames,
            int preferredToneFrequencyHz,
            int seedWpm,
            int sqlPercent,
            boolean experimentalHypothesisGuardEnabled,
            LiveLikeFrameHook frameHook,
            CwInterpreter.RecoveryMode recoveryMode
    ) {
        LIVE_LIKE_FRAME_HOOK.set(frameHook);
        try {
            return decodeFramesDetailedLiveLike(
                    sourceLabel,
                    frames,
                    preferredToneFrequencyHz,
                    seedWpm,
                    sqlPercent,
                    experimentalHypothesisGuardEnabled,
                    false,
                    false,
                    false,
                    false,
                    false,
                    LiveLikeRxToneModeStrategy.HYBRID_BOOTSTRAP,
                    recoveryMode
            );
        } finally {
            LIVE_LIKE_FRAME_HOOK.remove();
        }
    }

    static OfflineDetailedProbeResult decodeFramesDetailedLiveLikeWithForcedWideAcquisition(
            String sourceLabel,
            List<AudioFrame> frames,
            int preferredToneFrequencyHz,
            int seedWpm,
            int sqlPercent,
            boolean experimentalHypothesisGuardEnabled,
            CwInterpreter.RecoveryMode recoveryMode
    ) {
        LIVE_LIKE_FORCE_WIDE_ACQUISITION.set(Boolean.TRUE);
        try {
            return decodeFramesDetailedLiveLike(
                    sourceLabel,
                    frames,
                    preferredToneFrequencyHz,
                    seedWpm,
                    sqlPercent,
                    experimentalHypothesisGuardEnabled,
                    false,
                    false,
                    false,
                    false,
                    false,
                    LiveLikeRxToneModeStrategy.HYBRID_BOOTSTRAP,
                    recoveryMode
            );
        } finally {
            LIVE_LIKE_FORCE_WIDE_ACQUISITION.remove();
        }
    }

    static OfflineDetailedProbeResult decodeFramesDetailedLiveLikeWithFixedToneLearningWindow(
            String sourceLabel,
            List<AudioFrame> frames,
            int preferredToneFrequencyHz,
            int seedWpm,
            int sqlPercent,
            boolean experimentalHypothesisGuardEnabled,
            int fixedToneLearningWindowHz,
            CwInterpreter.RecoveryMode recoveryMode
    ) {
        LIVE_LIKE_FIXED_TONE_LEARNING_WINDOW_HZ.set(
                CwSignalProcessor.clampFixedToneLearningWindowHz(fixedToneLearningWindowHz)
        );
        try {
            return decodeFramesDetailedLiveLike(
                    sourceLabel,
                    frames,
                    preferredToneFrequencyHz,
                    seedWpm,
                    sqlPercent,
                    experimentalHypothesisGuardEnabled,
                    false,
                    false,
                    false,
                    false,
                    false,
                    LiveLikeRxToneModeStrategy.HYBRID_BOOTSTRAP,
                    recoveryMode
            );
        } finally {
            LIVE_LIKE_FIXED_TONE_LEARNING_WINDOW_HZ.remove();
        }
    }

    static OfflineDetailedProbeResult decodeFramesDetailedLiveLikeWithFixedToneLearningWindow(
            String sourceLabel,
            List<AudioFrame> frames,
            int preferredToneFrequencyHz,
            int seedWpm,
            int sqlPercent,
            boolean experimentalHypothesisGuardEnabled,
            int fixedToneLearningWindowHz,
            CwSignalProcessor.RxToneMode rxToneMode,
            CwInterpreter.RecoveryMode recoveryMode
    ) {
        LIVE_LIKE_FIXED_TONE_LEARNING_WINDOW_HZ.set(
                CwSignalProcessor.clampFixedToneLearningWindowHz(fixedToneLearningWindowHz)
        );
        try {
            return decodeFramesDetailedLiveLikeWithTurnCarry(
                    sourceLabel,
                    frames,
                    preferredToneFrequencyHz,
                    seedWpm,
                    sqlPercent,
                    experimentalHypothesisGuardEnabled,
                    false,
                    false,
                    false,
                    false,
                    false,
                    rxToneMode,
                    recoveryMode
            );
        } finally {
            LIVE_LIKE_FIXED_TONE_LEARNING_WINDOW_HZ.remove();
        }
    }

    static OfflineDetailedProbeResult decodeFramesDetailedLiveLikeFixedUntilTrustThenAutoWithFixedToneLearningWindow(
            String sourceLabel,
            List<AudioFrame> frames,
            int preferredToneFrequencyHz,
            int seedWpm,
            int sqlPercent,
            boolean experimentalHypothesisGuardEnabled,
            int fixedToneLearningWindowHz,
            CwInterpreter.RecoveryMode recoveryMode
    ) {
        LIVE_LIKE_FIXED_TONE_LEARNING_WINDOW_HZ.set(
                CwSignalProcessor.clampFixedToneLearningWindowHz(fixedToneLearningWindowHz)
        );
        try {
            return decodeFramesDetailedLiveLikeFixedUntilTrustThenAuto(
                    sourceLabel,
                    frames,
                    preferredToneFrequencyHz,
                    seedWpm,
                    sqlPercent,
                    experimentalHypothesisGuardEnabled,
                    recoveryMode
            );
        } finally {
            LIVE_LIKE_FIXED_TONE_LEARNING_WINDOW_HZ.remove();
        }
    }

    static OfflineDetailedProbeResult decodeFramesDetailedLiveLike(
            String sourceLabel,
            List<AudioFrame> frames,
            int preferredToneFrequencyHz,
            int seedWpm,
            int sqlPercent,
            boolean experimentalHypothesisGuardEnabled,
            CwInterpreter.RecoveryMode recoveryMode
    ) {
        return decodeFramesDetailedLiveLikeWithTurnCarry(
                sourceLabel,
                frames,
                preferredToneFrequencyHz,
                seedWpm,
                sqlPercent,
                experimentalHypothesisGuardEnabled,
                false,
                recoveryMode
        );
    }

    static OfflineDetailedProbeResult decodeFramesDetailedLiveLike(
            String sourceLabel,
            List<AudioFrame> frames,
            int preferredToneFrequencyHz,
            int seedWpm,
            int sqlPercent,
            boolean experimentalHypothesisGuardEnabled,
            boolean experimentalFrontEndAuthorityGateEnabled,
            boolean experimentalFrontEndOnsetAdmissionEnabled,
            CwInterpreter.RecoveryMode recoveryMode
    ) {
        return decodeFramesDetailedLiveLikeWithTurnCarry(
                sourceLabel,
                frames,
                preferredToneFrequencyHz,
                seedWpm,
                sqlPercent,
                experimentalHypothesisGuardEnabled,
                false,
                experimentalFrontEndAuthorityGateEnabled,
                experimentalFrontEndOnsetAdmissionEnabled,
                recoveryMode
        );
    }

    static OfflineDetailedProbeResult decodeFramesDetailedLiveLikeWithTurnCarry(
            String sourceLabel,
            List<AudioFrame> frames,
            int preferredToneFrequencyHz,
            int seedWpm,
            int sqlPercent,
            boolean experimentalHypothesisGuardEnabled,
            boolean crossTurnCarryEnabled,
            CwInterpreter.RecoveryMode recoveryMode
    ) {
        return decodeFramesDetailedLiveLikeWithTurnCarry(
                sourceLabel,
                frames,
                preferredToneFrequencyHz,
                seedWpm,
                sqlPercent,
                experimentalHypothesisGuardEnabled,
                crossTurnCarryEnabled,
                false,
                false,
                false,
                false,
                recoveryMode
        );
    }

    static OfflineDetailedProbeResult decodeFramesDetailedLiveLike(
            String sourceLabel,
            List<AudioFrame> frames,
            int preferredToneFrequencyHz,
            int seedWpm,
            int sqlPercent,
            boolean experimentalHypothesisGuardEnabled,
            boolean experimentalFrontEndAuthorityGateEnabled,
            boolean experimentalFrontEndOnsetAdmissionEnabled,
            boolean experimentalFrontEndShortGapMergeEnabled,
            CwInterpreter.RecoveryMode recoveryMode
    ) {
        return decodeFramesDetailedLiveLikeWithTurnCarry(
                sourceLabel,
                frames,
                preferredToneFrequencyHz,
                seedWpm,
                sqlPercent,
                experimentalHypothesisGuardEnabled,
                false,
                experimentalFrontEndAuthorityGateEnabled,
                experimentalFrontEndOnsetAdmissionEnabled,
                experimentalFrontEndShortGapMergeEnabled,
                recoveryMode
        );
    }

    static OfflineDetailedProbeResult decodeFramesDetailedLiveLikeWithTurnCarry(
            String sourceLabel,
            List<AudioFrame> frames,
            int preferredToneFrequencyHz,
            int seedWpm,
            int sqlPercent,
            boolean experimentalHypothesisGuardEnabled,
            boolean crossTurnCarryEnabled,
            boolean experimentalFrontEndAuthorityGateEnabled,
            CwInterpreter.RecoveryMode recoveryMode
    ) {
        return decodeFramesDetailedLiveLikeWithTurnCarry(
                sourceLabel,
                frames,
                preferredToneFrequencyHz,
                seedWpm,
                sqlPercent,
                experimentalHypothesisGuardEnabled,
                crossTurnCarryEnabled,
                experimentalFrontEndAuthorityGateEnabled,
                false,
                false,
                false,
                recoveryMode
        );
    }

    static OfflineDetailedProbeResult decodeFramesDetailedLiveLike(
            String sourceLabel,
            List<AudioFrame> frames,
            int preferredToneFrequencyHz,
            int seedWpm,
            int sqlPercent,
            boolean experimentalHypothesisGuardEnabled,
            boolean experimentalFrontEndAuthorityGateEnabled,
            boolean experimentalFrontEndOnsetAdmissionEnabled,
            boolean experimentalFrontEndShortGapMergeEnabled,
            boolean experimentalFrontEndReleasePressureHoldEnabled,
            CwInterpreter.RecoveryMode recoveryMode
    ) {
        return decodeFramesDetailedLiveLikeWithTurnCarry(
                sourceLabel,
                frames,
                preferredToneFrequencyHz,
                seedWpm,
                sqlPercent,
                experimentalHypothesisGuardEnabled,
                false,
                experimentalFrontEndAuthorityGateEnabled,
                experimentalFrontEndOnsetAdmissionEnabled,
                experimentalFrontEndShortGapMergeEnabled,
                experimentalFrontEndReleasePressureHoldEnabled,
                recoveryMode
        );
    }

    static OfflineDetailedProbeResult decodeFramesDetailedLiveLikeWithTurnCarry(
            String sourceLabel,
            List<AudioFrame> frames,
            int preferredToneFrequencyHz,
            int seedWpm,
            int sqlPercent,
            boolean experimentalHypothesisGuardEnabled,
            boolean crossTurnCarryEnabled,
            boolean experimentalFrontEndAuthorityGateEnabled,
            boolean experimentalFrontEndOnsetAdmissionEnabled,
            CwInterpreter.RecoveryMode recoveryMode
    ) {
        return decodeFramesDetailedLiveLikeWithTurnCarry(
                sourceLabel,
                frames,
                preferredToneFrequencyHz,
                seedWpm,
                sqlPercent,
                experimentalHypothesisGuardEnabled,
                crossTurnCarryEnabled,
                experimentalFrontEndAuthorityGateEnabled,
                experimentalFrontEndOnsetAdmissionEnabled,
                false,
                false,
                recoveryMode
        );
    }

    static OfflineDetailedProbeResult decodeFramesDetailedLiveLike(
            String sourceLabel,
            List<AudioFrame> frames,
            int preferredToneFrequencyHz,
            int seedWpm,
            int sqlPercent,
            boolean experimentalHypothesisGuardEnabled,
            boolean experimentalFrontEndAuthorityGateEnabled,
            boolean experimentalFrontEndOnsetAdmissionEnabled,
            boolean experimentalFrontEndShortGapMergeEnabled,
            boolean experimentalFrontEndReleasePressureHoldEnabled,
            CwSignalProcessor.RxToneMode rxToneMode,
            CwInterpreter.RecoveryMode recoveryMode
    ) {
        return decodeFramesDetailedLiveLikeWithTurnCarry(
                sourceLabel,
                frames,
                preferredToneFrequencyHz,
                seedWpm,
                sqlPercent,
                experimentalHypothesisGuardEnabled,
                false,
                experimentalFrontEndAuthorityGateEnabled,
                experimentalFrontEndOnsetAdmissionEnabled,
                experimentalFrontEndShortGapMergeEnabled,
                experimentalFrontEndReleasePressureHoldEnabled,
                rxToneMode,
                recoveryMode
        );
    }

    static OfflineDetailedProbeResult decodeFramesDetailedLiveLikeWithTurnCarry(
            String sourceLabel,
            List<AudioFrame> frames,
            int preferredToneFrequencyHz,
            int seedWpm,
            int sqlPercent,
            boolean experimentalHypothesisGuardEnabled,
            boolean crossTurnCarryEnabled,
            boolean experimentalFrontEndAuthorityGateEnabled,
            boolean experimentalFrontEndOnsetAdmissionEnabled,
            boolean experimentalFrontEndShortGapMergeEnabled,
            CwInterpreter.RecoveryMode recoveryMode
    ) {
        return decodeFramesDetailedLiveLikeWithTurnCarry(
                sourceLabel,
                frames,
                preferredToneFrequencyHz,
                seedWpm,
                sqlPercent,
                experimentalHypothesisGuardEnabled,
                crossTurnCarryEnabled,
                experimentalFrontEndAuthorityGateEnabled,
                experimentalFrontEndOnsetAdmissionEnabled,
                experimentalFrontEndShortGapMergeEnabled,
                false,
                recoveryMode
        );
    }

    static OfflineDetailedProbeResult decodeFramesDetailedLiveLikeWithTurnCarry(
            String sourceLabel,
            List<AudioFrame> frames,
            int preferredToneFrequencyHz,
            int seedWpm,
            int sqlPercent,
            boolean experimentalHypothesisGuardEnabled,
            boolean crossTurnCarryEnabled,
            boolean experimentalFrontEndAuthorityGateEnabled,
            boolean experimentalFrontEndOnsetAdmissionEnabled,
            boolean experimentalFrontEndShortGapMergeEnabled,
            boolean experimentalFrontEndReleasePressureHoldEnabled,
            CwInterpreter.RecoveryMode recoveryMode
    ) {
        return decodeFramesDetailedLiveLike(
                sourceLabel,
                frames,
                preferredToneFrequencyHz,
                seedWpm,
                sqlPercent,
                experimentalHypothesisGuardEnabled,
                crossTurnCarryEnabled,
                experimentalFrontEndAuthorityGateEnabled,
                experimentalFrontEndOnsetAdmissionEnabled,
                experimentalFrontEndShortGapMergeEnabled,
                experimentalFrontEndReleasePressureHoldEnabled,
                LiveLikeRxToneModeStrategy.HYBRID_BOOTSTRAP,
                recoveryMode
        );
    }

    static OfflineDetailedProbeResult decodeFramesDetailedLiveLikeWithTurnCarry(
            String sourceLabel,
            List<AudioFrame> frames,
            int preferredToneFrequencyHz,
            int seedWpm,
            int sqlPercent,
            boolean experimentalHypothesisGuardEnabled,
            boolean crossTurnCarryEnabled,
            boolean experimentalFrontEndAuthorityGateEnabled,
            boolean experimentalFrontEndOnsetAdmissionEnabled,
            boolean experimentalFrontEndShortGapMergeEnabled,
            boolean experimentalFrontEndReleasePressureHoldEnabled,
            CwSignalProcessor.RxToneMode rxToneMode,
            CwInterpreter.RecoveryMode recoveryMode
    ) {
        return decodeFramesDetailedLiveLike(
                sourceLabel,
                frames,
                preferredToneFrequencyHz,
                seedWpm,
                sqlPercent,
                experimentalHypothesisGuardEnabled,
                crossTurnCarryEnabled,
                experimentalFrontEndAuthorityGateEnabled,
                experimentalFrontEndOnsetAdmissionEnabled,
                experimentalFrontEndShortGapMergeEnabled,
                experimentalFrontEndReleasePressureHoldEnabled,
                rxToneMode == CwSignalProcessor.RxToneMode.AUTO_TRACK
                        ? LiveLikeRxToneModeStrategy.STATIC_AUTO_TRACK
                        : LiveLikeRxToneModeStrategy.STATIC_FIXED_TONE,
                recoveryMode
        );
    }

    private static OfflineDetailedProbeResult decodeFramesDetailedLiveLike(
            String sourceLabel,
            List<AudioFrame> frames,
            int preferredToneFrequencyHz,
            int seedWpm,
            int sqlPercent,
            boolean experimentalHypothesisGuardEnabled,
            boolean crossTurnCarryEnabled,
            boolean experimentalFrontEndAuthorityGateEnabled,
            boolean experimentalFrontEndOnsetAdmissionEnabled,
            boolean experimentalFrontEndShortGapMergeEnabled,
            boolean experimentalFrontEndReleasePressureHoldEnabled,
            LiveLikeRxToneModeStrategy rxToneModeStrategy,
            CwInterpreter.RecoveryMode recoveryMode
    ) {
        return decodeFramesDetailedLiveLike(
                sourceLabel,
                frames,
                preferredToneFrequencyHz,
                seedWpm,
                sqlPercent,
                experimentalHypothesisGuardEnabled,
                crossTurnCarryEnabled,
                experimentalFrontEndAuthorityGateEnabled,
                experimentalFrontEndOnsetAdmissionEnabled,
                experimentalFrontEndShortGapMergeEnabled,
                experimentalFrontEndReleasePressureHoldEnabled,
                rxToneModeStrategy,
                recoveryMode,
                true
        );
    }

    private static OfflineDetailedProbeResult decodeFramesDetailedLiveLike(
            String sourceLabel,
            List<AudioFrame> frames,
            int preferredToneFrequencyHz,
            int seedWpm,
            int sqlPercent,
            boolean experimentalHypothesisGuardEnabled,
            boolean crossTurnCarryEnabled,
            boolean experimentalFrontEndAuthorityGateEnabled,
            boolean experimentalFrontEndOnsetAdmissionEnabled,
            boolean experimentalFrontEndShortGapMergeEnabled,
            boolean experimentalFrontEndReleasePressureHoldEnabled,
            LiveLikeRxToneModeStrategy rxToneModeStrategy,
            CwInterpreter.RecoveryMode recoveryMode,
            boolean applyTrailingWindowRepair
    ) {
        return decodeFramesDetailedLiveLike(
                sourceLabel,
                frames,
                preferredToneFrequencyHz,
                seedWpm,
                sqlPercent,
                experimentalHypothesisGuardEnabled,
                crossTurnCarryEnabled,
                experimentalFrontEndAuthorityGateEnabled,
                experimentalFrontEndOnsetAdmissionEnabled,
                experimentalFrontEndShortGapMergeEnabled,
                experimentalFrontEndReleasePressureHoldEnabled,
                rxToneModeStrategy,
                recoveryMode,
                applyTrailingWindowRepair,
                false
        );
    }

    private static OfflineDetailedProbeResult decodeFramesDetailedLiveLike(
            String sourceLabel,
            List<AudioFrame> frames,
            int preferredToneFrequencyHz,
            int seedWpm,
            int sqlPercent,
            boolean experimentalHypothesisGuardEnabled,
            boolean crossTurnCarryEnabled,
            boolean experimentalFrontEndAuthorityGateEnabled,
            boolean experimentalFrontEndOnsetAdmissionEnabled,
            boolean experimentalFrontEndShortGapMergeEnabled,
            boolean experimentalFrontEndReleasePressureHoldEnabled,
            LiveLikeRxToneModeStrategy rxToneModeStrategy,
            CwInterpreter.RecoveryMode recoveryMode,
            boolean applyTrailingWindowRepair,
            boolean experimentalSoftStableBootstrapCandidateEnabled
    ) {
        LiveLikeProbeDiagnosticsCollector diagnosticsCollector =
                new LiveLikeProbeDiagnosticsCollector(experimentalSoftStableBootstrapCandidateEnabled);
        LIVE_LIKE_DIAGNOSTICS.set(diagnosticsCollector);
        try {
        CwSignalProcessor signalProcessor = new CwSignalProcessor();
        signalProcessor.setPreferredToneFrequencyHz(preferredToneFrequencyHz);
        signalProcessor.setSqlPercent(sqlPercent);
        Integer fixedToneLearningWindowHz = LIVE_LIKE_FIXED_TONE_LEARNING_WINDOW_HZ.get();
        if (fixedToneLearningWindowHz != null) {
            signalProcessor.setFixedToneLearningWindowHz(fixedToneLearningWindowHz);
        }
        signalProcessor.setExperimentalHypothesisGuardEnabled(experimentalHypothesisGuardEnabled);

        CwHybridTimingModel timingModel = new CwHybridTimingModel();
        timingModel.setIdleResetEnabled(false);
        timingModel.setSeedWpm(seedWpm);
        CwDecoder decoder = new CwDecoder();
        CwInterpreter interpreter = new CwInterpreter(recoveryMode);
        QsoStateMachine qsoStateMachine = new QsoStateMachine();
        AudioInputHealthTracker inputHealthTracker = new AudioInputHealthTracker();
        RxFrameSignalRunner frameSignalRunner = new RxFrameSignalRunner(
                inputHealthTracker,
                signalProcessor
        );
        LiveRxWpmGuard wpmGuard = new LiveRxWpmGuard();
        wpmGuard.setSeedWpm(seedWpm);
        RxTurnController turnController = new RxTurnController();
        turnController.setTxSeedWpm(seedWpm);
        turnController.setCrossTurnCarryEnabled(crossTurnCarryEnabled);
        TimingAnchorController timingAnchorController = new TimingAnchorController();
        timingAnchorController.setSeedWpm(seedWpm);
        syncLiveLikeRxToneMode(
                signalProcessor,
                rxToneModeStrategy,
                timingAnchorController,
                turnController,
                -1L
        );
        RxRawCommitGate rawCommitGate = new RxRawCommitGate();
        CwFrontEndLearningGate frontEndLearningGate = new CwFrontEndLearningGate();
        LiveRxToneEventStabilizer toneEventStabilizer = new LiveRxToneEventStabilizer();
        LiveLikeFrontEndAuthorityGate frontEndAuthorityGate =
                (experimentalFrontEndAuthorityGateEnabled
                        || experimentalFrontEndOnsetAdmissionEnabled
                        || experimentalFrontEndShortGapMergeEnabled)
                ? new LiveLikeFrontEndAuthorityGate(sqlPercent)
                : null;
        LiveLikeOnsetAdmissionGate onsetAdmissionGate = experimentalFrontEndOnsetAdmissionEnabled
                ? new LiveLikeOnsetAdmissionGate()
                : null;
        LiveLikeReleasePressureHoldGate releasePressureHoldGate = experimentalFrontEndReleasePressureHoldEnabled
                ? new LiveLikeReleasePressureHoldGate()
                : null;
        LiveLikeShortGapMergeGate shortGapMergeGate = experimentalFrontEndShortGapMergeEnabled
                ? new LiveLikeShortGapMergeGate()
                : null;

        ArrayList<AudioFrame> capturedFrames = new ArrayList<>();
        ArrayList<CwToneEvent> capturedToneEvents = new ArrayList<>();
        ArrayList<CwTimingEvent> capturedTimingEvents = new ArrayList<>();
        ArrayList<CwDecodeEvent> capturedDecodeEvents = new ArrayList<>();
        ArrayList<FrameSignalTrace> frameSignalTraces = new ArrayList<>();
        ArrayList<TimingStateTrace> timingStateTraces = new ArrayList<>();
        ArrayList<TurnTransitionTrace> capturedTurnTransitionTraces = new ArrayList<>();

        AudioFrame lastFrame = null;
        long lastFrameEndTimestampMs = 0L;
        AudioInputHealthSnapshot lastInputHealthSnapshot = null;
        LiveLikeFrameHook frameHook = LIVE_LIKE_FRAME_HOOK.get();
        int frameIndex = 0;
        for (AudioFrame frame : frames) {
            lastFrame = frame;
            capturedFrames.add(frame);

            syncLiveLikeRxToneMode(
                    signalProcessor,
                    rxToneModeStrategy,
                    timingAnchorController,
                    turnController,
                    frame.capturedAtMs()
            );
            if (frameHook != null) {
                frameHook.beforeProcessFrame(frame, signalProcessor, frameIndex);
            }
            RxFrameSignalRunner.Result frameSignalResult = frameSignalRunner.processFrame(
                    frame,
                    frame == null ? 0L : frame.capturedAtMs()
            );
            if (frameSignalResult == null) {
                frameIndex += 1;
                continue;
            }
            lastInputHealthSnapshot = frameSignalResult.inputHealthSnapshot();
            frameSignalTraces.add(buildFrameSignalTrace(frame, signalProcessor));
            long frameEndTimestampMs = frameSignalResult.frameEndTimestampMs();
            lastFrameEndTimestampMs = frameEndTimestampMs;
            CwSignalSnapshot signalSnapshotAfterProcess = frameSignalResult.signalSnapshotAfterProcess();
            handleTurnTransition(
                    signalSnapshotAfterProcess,
                    signalProcessor,
                    decoder,
                    timingModel,
                    wpmGuard,
                    turnController,
                    timingAnchorController,
                    rawCommitGate,
                    capturedTurnTransitionTraces,
                    frameEndTimestampMs
            );
            syncLiveLikeRxToneMode(
                    signalProcessor,
                    rxToneModeStrategy,
                    timingAnchorController,
                    turnController,
                    frameEndTimestampMs
            );
            if (frontEndAuthorityGate != null) {
                frontEndAuthorityGate.observeFrame(
                        signalSnapshotAfterProcess,
                        lastInputHealthSnapshot,
                        frameEndTimestampMs
                );
            }
            for (CwToneEvent toneEvent : frameSignalResult.toneEvents()) {
                routeLiveLikeToneEvent(
                        toneEvent,
                        toneEvent.timestampMs(),
                        lastInputHealthSnapshot,
                        signalProcessor,
                        timingModel,
                        decoder,
                        interpreter,
                        qsoStateMachine,
                        wpmGuard,
                        turnController,
                        timingAnchorController,
                        rawCommitGate,
                        capturedTurnTransitionTraces,
                        frontEndLearningGate,
                        frontEndAuthorityGate,
                        onsetAdmissionGate,
                        releasePressureHoldGate,
                        shortGapMergeGate,
                        toneEventStabilizer,
                        signalProcessor.lastDetectionLevel(),
                        capturedToneEvents,
                        capturedTimingEvents,
                        capturedDecodeEvents
                );
            }

            flushLiveLikeToneEventStabilizer(
                    frameEndTimestampMs,
                    lastInputHealthSnapshot,
                    signalProcessor,
                    timingModel,
                    decoder,
                    interpreter,
                    qsoStateMachine,
                    wpmGuard,
                    turnController,
                    timingAnchorController,
                    rawCommitGate,
                    capturedTurnTransitionTraces,
                    frontEndLearningGate,
                    frontEndAuthorityGate,
                    onsetAdmissionGate,
                    releasePressureHoldGate,
                    shortGapMergeGate,
                    toneEventStabilizer,
                    signalProcessor.lastDetectionLevel(),
                    capturedToneEvents,
                    capturedTimingEvents,
                    capturedDecodeEvents
            );
            flushLiveLikeReleasePressureHoldGate(
                    frameEndTimestampMs,
                    signalProcessor,
                    timingModel,
                    decoder,
                    interpreter,
                    qsoStateMachine,
                    wpmGuard,
                    turnController,
                    timingAnchorController,
                    rawCommitGate,
                    capturedTurnTransitionTraces,
                    frontEndLearningGate,
                    frontEndAuthorityGate,
                    onsetAdmissionGate,
                    releasePressureHoldGate,
                    shortGapMergeGate,
                    capturedToneEvents,
                    capturedTimingEvents,
                    capturedDecodeEvents
            );
            flushLiveLikeShortGapMergeGate(
                    frameEndTimestampMs,
                    signalProcessor,
                    timingModel,
                    decoder,
                    interpreter,
                    qsoStateMachine,
                    wpmGuard,
                    turnController,
                    timingAnchorController,
                    rawCommitGate,
                    capturedTurnTransitionTraces,
                    frontEndLearningGate,
                    frontEndAuthorityGate,
                    shortGapMergeGate,
                    capturedToneEvents,
                    capturedTimingEvents,
                    capturedDecodeEvents
            );
            flushLiveLikeOnsetAdmissionGate(
                    frameEndTimestampMs,
                    signalProcessor,
                    frontEndAuthorityGate,
                    onsetAdmissionGate,
                    timingModel,
                    decoder,
                    interpreter,
                    qsoStateMachine,
                    wpmGuard,
                    turnController,
                    timingAnchorController,
                    rawCommitGate,
                    capturedTurnTransitionTraces,
                    frontEndLearningGate,
                    capturedToneEvents,
                    capturedTimingEvents,
                    capturedDecodeEvents
            );
            maybeFlushPendingCharacterDuringSilence(
                    frameEndTimestampMs,
                    lastInputHealthSnapshot,
                    signalProcessor,
                    timingModel,
                    decoder,
                    interpreter,
                    qsoStateMachine,
                    wpmGuard,
                    turnController,
                    timingAnchorController,
                    rawCommitGate,
                    capturedTurnTransitionTraces,
                    frontEndLearningGate,
                    frontEndAuthorityGate,
                    onsetAdmissionGate,
                    capturedDecodeEvents
            );
            timingModel.observeClock(frameEndTimestampMs);
            handleTurnTransition(
                    signalProcessor.snapshot(),
                    signalProcessor,
                    decoder,
                    timingModel,
                    wpmGuard,
                    turnController,
                    timingAnchorController,
                    rawCommitGate,
                    capturedTurnTransitionTraces,
                    frameEndTimestampMs
            );
            syncLiveLikeRxToneMode(
                    signalProcessor,
                    rxToneModeStrategy,
                    timingAnchorController,
                    turnController,
                    frameEndTimestampMs
            );
            timingStateTraces.add(new TimingStateTrace(
                    frameEndTimestampMs,
                    timingModel.debugSnapshot(),
                    timingModel.snapshot(),
                    timingModel.rawSnapshot(),
                    timingModel.debugStrategySummary()
            ));
            frameIndex += 1;
        }

        long flushTimestampMs = 0L;
        if (lastFrame != null) {
            flushTimestampMs = lastFrameEndTimestampMs > 0L
                    ? lastFrameEndTimestampMs
                    : estimateFrameEndTimestampMs(lastFrame);
            flushLiveLikeToneEventStabilizer(
                    flushTimestampMs,
                    lastInputHealthSnapshot,
                    signalProcessor,
                    timingModel,
                    decoder,
                    interpreter,
                    qsoStateMachine,
                    wpmGuard,
                    turnController,
                    timingAnchorController,
                    rawCommitGate,
                    capturedTurnTransitionTraces,
                    frontEndLearningGate,
                    frontEndAuthorityGate,
                    onsetAdmissionGate,
                    releasePressureHoldGate,
                    shortGapMergeGate,
                    toneEventStabilizer,
                    signalProcessor.lastDetectionLevel(),
                    capturedToneEvents,
                    capturedTimingEvents,
                    capturedDecodeEvents
            );
            flushLiveLikeReleasePressureHoldGate(
                    flushTimestampMs,
                    signalProcessor,
                    timingModel,
                    decoder,
                    interpreter,
                    qsoStateMachine,
                    wpmGuard,
                    turnController,
                    timingAnchorController,
                    rawCommitGate,
                    capturedTurnTransitionTraces,
                    frontEndLearningGate,
                    frontEndAuthorityGate,
                    onsetAdmissionGate,
                    releasePressureHoldGate,
                    shortGapMergeGate,
                    capturedToneEvents,
                    capturedTimingEvents,
                    capturedDecodeEvents
            );
            flushLiveLikeShortGapMergeGate(
                    flushTimestampMs,
                    signalProcessor,
                    timingModel,
                    decoder,
                    interpreter,
                    qsoStateMachine,
                    wpmGuard,
                    turnController,
                    timingAnchorController,
                    rawCommitGate,
                    capturedTurnTransitionTraces,
                    frontEndLearningGate,
                    frontEndAuthorityGate,
                    shortGapMergeGate,
                    capturedToneEvents,
                    capturedTimingEvents,
                    capturedDecodeEvents
            );
            flushLiveLikeOnsetAdmissionGate(
                    flushTimestampMs,
                    signalProcessor,
                    frontEndAuthorityGate,
                    onsetAdmissionGate,
                    timingModel,
                    decoder,
                    interpreter,
                    qsoStateMachine,
                    wpmGuard,
                    turnController,
                    timingAnchorController,
                    rawCommitGate,
                    capturedTurnTransitionTraces,
                    frontEndLearningGate,
                    capturedToneEvents,
                    capturedTimingEvents,
                    capturedDecodeEvents
            );
            flushPendingDecode(
                    flushTimestampMs,
                    lastInputHealthSnapshot,
                    signalProcessor,
                    timingModel,
                    decoder,
                    interpreter,
                    qsoStateMachine,
                    wpmGuard,
                    turnController,
                    timingAnchorController,
                    rawCommitGate,
                    capturedTurnTransitionTraces,
                    frontEndLearningGate,
                    frontEndAuthorityGate,
                    onsetAdmissionGate,
                    capturedTimingEvents,
                    capturedDecodeEvents
            );
            timingStateTraces.add(new TimingStateTrace(
                    flushTimestampMs,
                    timingModel.debugSnapshot(),
                    timingModel.snapshot(),
                    timingModel.rawSnapshot(),
                    timingModel.debugStrategySummary()
            ));
        }

        OfflineProbeResult probeResult = new OfflineProbeResult(
                sourceLabel,
                sanitize(interpreter.snapshot().rawText()),
                signalProcessor.snapshot(),
                signalProcessor.debugActiveLeaderCompactSummary(),
                timingModel.snapshot(),
                timingModel.debugStrategySummary(),
                decoder.snapshot(),
                interpreter.snapshot(),
                qsoStateMachine.snapshot()
        );
        OfflineDetailedProbeResult detailedProbeResult = new OfflineDetailedProbeResult(
                probeResult,
                capturedFrames,
                capturedToneEvents,
                capturedTimingEvents,
                capturedDecodeEvents,
                diagnosticsCollector.rawDecodeEvents,
                diagnosticsCollector.stableAcceptedDecodeEvents,
                diagnosticsCollector.stableDecisionTraces,
                diagnosticsCollector.bootstrapBoundaryTimingEvents,
                diagnosticsCollector.bootstrapCadenceTimingEvents,
                diagnosticsCollector.bootstrapBoundaryDecisionTraces,
                diagnosticsCollector.bootstrapCadenceDecisionTraces,
                diagnosticsCollector.timingLearningDecisionTraces,
                diagnosticsCollector.timingEventAdaptationTraces,
                diagnosticsCollector.stableRejectCounts,
                diagnosticsCollector.bootstrapBoundaryRejectCounts,
                diagnosticsCollector.bootstrapCadenceRejectCounts,
                frameSignalTraces,
                timingStateTraces,
                diagnosticsCollector.rxToneModeDecisionTraces,
                capturedTurnTransitionTraces,
                flushTimestampMs
        );
        return applyTrailingWindowRepair
                ? maybeRepairTrailingWindowWithFreshRedecode(detailedProbeResult, recoveryMode)
                : detailedProbeResult;
        } finally {
            LIVE_LIKE_DIAGNOSTICS.remove();
        }
    }

    private static void syncLiveLikeRxToneMode(
            CwSignalProcessor signalProcessor,
            LiveLikeRxToneModeStrategy rxToneModeStrategy,
            @Nullable TimingAnchorController timingAnchorController,
            @Nullable RxTurnController turnController,
            long nowTimestampMs
    ) {
        if (signalProcessor == null || rxToneModeStrategy == null) {
            return;
        }
        CwSignalSnapshot signalSnapshot = signalProcessor.snapshot();
        boolean trustedTimingEstablished = timingAnchorController != null
                && timingAnchorController.trustedDotEstimateMs() > 0L;
        CwSignalProcessor.RxToneMode resolvedMode = resolveEffectiveLiveLikeRxToneMode(
                rxToneModeStrategy,
                timingAnchorController,
                turnController,
                signalSnapshot,
                nowTimestampMs
        );
        signalProcessor.setRxToneMode(resolvedMode);
        LiveLikeProbeDiagnosticsCollector diagnosticsCollector = liveLikeDiagnosticsCollector();
        if (diagnosticsCollector != null) {
            diagnosticsCollector.rxToneModeDecisionTraces.add(new RxToneModeDecisionTrace(
                    nowTimestampMs,
                    resolvedMode,
                    rxToneModeStrategy.name(),
                    trustedTimingEstablished,
                    turnController != null && turnController.bootstrapAutoTrackFallbackLatched(),
                    turnController == null ? "(none)" : turnController.phase().name(),
                    turnController == null ? -1L : turnController.currentTurnStartedAtMs(),
                    showsUsefulFixedToneBootstrapProgress(signalSnapshot),
                    isEligibleForPreTrustAutoTrackFallback(
                            turnController,
                            signalSnapshot,
                            nowTimestampMs
                    ),
                    signalSnapshot == null ? false : signalSnapshot.targetToneLocked(),
                    signalSnapshot == null ? 0 : signalSnapshot.consecutiveLockedFrames(),
                    signalSnapshot == null ? 0.0d : signalSnapshot.recentLockedFrameRatio()
            ));
        }
    }

    private static boolean showsUsefulFixedToneBootstrapProgress(@Nullable CwSignalSnapshot signalSnapshot) {
        if (signalSnapshot == null) {
            return false;
        }
        return signalSnapshot.targetToneLocked()
                || signalSnapshot.consecutiveLockedFrames() >= 4
                || signalSnapshot.recentLockedFrameRatio() >= 0.30d;
    }

    private static boolean isEligibleForPreTrustAutoTrackFallback(
            @Nullable RxTurnController turnController,
            @Nullable CwSignalSnapshot signalSnapshot,
            long nowTimestampMs
    ) {
        if (turnController == null
                || turnController.phase() != RxTurnController.Phase.ACTIVE
                || nowTimestampMs <= 0L) {
            return false;
        }
        long turnStartedAtMs = turnController.currentTurnStartedAtMs();
        if (turnStartedAtMs <= 0L || nowTimestampMs < turnStartedAtMs) {
            return false;
        }
        if ((nowTimestampMs - turnStartedAtMs) < 48L) {
            return false;
        }
        return !showsUsefulFixedToneBootstrapProgress(signalSnapshot);
    }

    private static CwSignalProcessor.RxToneMode resolveEffectiveLiveLikeRxToneMode(
            LiveLikeRxToneModeStrategy rxToneModeStrategy,
            @Nullable TimingAnchorController timingAnchorController,
            @Nullable RxTurnController turnController,
            @Nullable CwSignalSnapshot signalSnapshot,
            long nowTimestampMs
    ) {
        if (rxToneModeStrategy == LiveLikeRxToneModeStrategy.STATIC_FIXED_TONE) {
            return CwSignalProcessor.RxToneMode.FIXED_TONE;
        }
        if (rxToneModeStrategy == LiveLikeRxToneModeStrategy.STATIC_AUTO_TRACK) {
            return CwSignalProcessor.RxToneMode.AUTO_TRACK;
        }
        if (rxToneModeStrategy == LiveLikeRxToneModeStrategy.FIXED_HOLD_THEN_AUTO) {
            long fixedHoldUntilMs = liveLikeFixedHoldUntilMs();
            if (fixedHoldUntilMs > 0L && turnController != null) {
                long turnStartedAtMs = turnController.currentTurnStartedAtMs();
                if (turnStartedAtMs > 0L
                        && nowTimestampMs >= turnStartedAtMs
                        && (nowTimestampMs - turnStartedAtMs) < fixedHoldUntilMs) {
                    return CwSignalProcessor.RxToneMode.FIXED_TONE;
                }
            }
            return CwSignalProcessor.RxToneMode.AUTO_TRACK;
        }
        boolean trustedTimingEstablished = timingAnchorController != null
                && timingAnchorController.trustedDotEstimateMs() > 0L;
        if (rxToneModeStrategy == LiveLikeRxToneModeStrategy.FIXED_UNTIL_TRUST_THEN_AUTO) {
            return trustedTimingEstablished
                    ? CwSignalProcessor.RxToneMode.AUTO_TRACK
                    : CwSignalProcessor.RxToneMode.FIXED_TONE;
        }
        return RxToneModeBootstrapDecider.resolveHybridBootstrapMode(
                trustedTimingEstablished,
                turnController,
                signalSnapshot,
                nowTimestampMs
        );
    }

    private static long liveLikeFixedHoldUntilMs() {
        Long value = LIVE_LIKE_FIXED_HOLD_UNTIL_MS.get();
        return value == null ? 0L : value;
    }

    static AudioFrame buildFrameForProbe(short[] samples, int sampleRateHz, long sampleOffset) {
        int peak = 0;
        int clippedSampleCount = 0;
        double sumSquares = 0.0d;
        for (short sample : samples) {
            int absolute = Math.abs((int) sample);
            if (absolute > peak) {
                peak = absolute;
            }
            if (absolute >= CLIPPING_SAMPLE_THRESHOLD) {
                clippedSampleCount += 1;
            }
            sumSquares += (double) sample * sample;
        }
        double rms = samples.length == 0 ? 0.0d : Math.sqrt(sumSquares / samples.length);
        long capturedAtMs = Math.round(sampleOffset * 1000.0d / sampleRateHz);
        return new AudioFrame(samples, sampleRateHz, 1, peak, rms, clippedSampleCount, capturedAtMs);
    }

    static WaveDataProbe readWaveFileForProbe(Path wavFile) throws IOException {
        try (InputStream fileInputStream = Files.newInputStream(wavFile);
             BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
             DataInputStream inputStream = new DataInputStream(bufferedInputStream)) {
            byte[] riffHeader = new byte[12];
            inputStream.readFully(riffHeader);
            String riff = new String(riffHeader, 0, 4, StandardCharsets.US_ASCII);
            String wave = new String(riffHeader, 8, 4, StandardCharsets.US_ASCII);
            if (!"RIFF".equals(riff) || !"WAVE".equals(wave)) {
                throw new IOException("Unsupported WAV header in " + wavFile);
            }

            Integer sampleRateHz = null;
            Integer channelCount = null;
            Integer bitsPerSample = null;
            byte[] dataBytes = null;

            while (inputStream.available() > 0) {
                byte[] chunkHeader = new byte[8];
                inputStream.readFully(chunkHeader);
                String chunkId = new String(chunkHeader, 0, 4, StandardCharsets.US_ASCII);
                int chunkSize = littleEndianInt(chunkHeader, 4);

                if ("fmt ".equals(chunkId)) {
                    byte[] fmtBytes = new byte[chunkSize];
                    inputStream.readFully(fmtBytes);
                    int audioFormat = littleEndianShort(fmtBytes, 0);
                    channelCount = littleEndianShort(fmtBytes, 2);
                    sampleRateHz = littleEndianInt(fmtBytes, 4);
                    bitsPerSample = littleEndianShort(fmtBytes, 14);
                    if (audioFormat != 1) {
                        throw new IOException("Only PCM WAV is supported for local probe: " + wavFile);
                    }
                } else if ("data".equals(chunkId)) {
                    dataBytes = new byte[chunkSize];
                    inputStream.readFully(dataBytes);
                } else {
                    long skipped = inputStream.skipBytes(chunkSize);
                    if (skipped != chunkSize) {
                        throw new IOException("Failed to skip WAV chunk " + chunkId + " in " + wavFile);
                    }
                }

                if ((chunkSize & 1) != 0) {
                    inputStream.skipBytes(1);
                }
            }

            if (sampleRateHz == null || channelCount == null || bitsPerSample == null || dataBytes == null) {
                throw new IOException("Incomplete WAV structure in " + wavFile);
            }
            if (bitsPerSample != 16) {
                throw new IOException("Only PCM16 WAV is supported for local probe: " + wavFile);
            }

            short[] rawSamples = new short[dataBytes.length / 2];
            for (int index = 0; index < rawSamples.length; index++) {
                rawSamples[index] = (short) littleEndianShort(dataBytes, index * 2);
            }

            short[] monoSamples;
            if (channelCount == 1) {
                monoSamples = rawSamples;
            } else {
                int frameCount = rawSamples.length / channelCount;
                monoSamples = new short[frameCount];
                for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
                    int sum = 0;
                    for (int channelIndex = 0; channelIndex < channelCount; channelIndex++) {
                        sum += rawSamples[(frameIndex * channelCount) + channelIndex];
                    }
                    monoSamples[frameIndex] = (short) Math.max(
                            Short.MIN_VALUE,
                            Math.min(Short.MAX_VALUE, Math.round(sum / (float) channelCount))
                    );
                }
            }

            return new WaveDataProbe(sampleRateHz, monoSamples);
        }
    }

    static List<AudioFrame> loadFramesFromWavFile(Path wavFile) throws IOException {
        WaveDataProbe waveData = readWaveFileForProbe(wavFile);
        ArrayList<AudioFrame> frames = new ArrayList<>();
        long sampleOffset = 0L;
        for (int offset = 0; offset < waveData.samples().length; offset += FRAME_SIZE_SAMPLES) {
            int frameLength = Math.min(FRAME_SIZE_SAMPLES, waveData.samples().length - offset);
            short[] frameSamples = new short[frameLength];
            System.arraycopy(waveData.samples(), offset, frameSamples, 0, frameLength);
            frames.add(buildFrameForProbe(frameSamples, waveData.sampleRateHz(), sampleOffset));
            sampleOffset += frameLength;
        }
        return frames;
    }

    static List<AudioFrame> normalizeFramesToZero(List<AudioFrame> frames) {
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

    private static String sourceLabelFromWavFile(Path wavFile) {
        String fileName = wavFile.getFileName().toString();
        return fileName.toLowerCase(Locale.US).endsWith(".wav")
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;
    }

    private static int littleEndianInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF)
                | ((bytes[offset + 1] & 0xFF) << 8)
                | ((bytes[offset + 2] & 0xFF) << 16)
                | ((bytes[offset + 3] & 0xFF) << 24);
    }

    private static int littleEndianShort(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace('\u25A1', '?').trim();
        return normalized.isEmpty() ? "(empty)" : normalized;
    }

    private static void routeLiveLikeToneEvent(
            CwToneEvent toneEvent,
            long nowTimestampMs,
            AudioInputHealthSnapshot inputHealthSnapshot,
            CwSignalProcessor signalProcessor,
            CwHybridTimingModel timingModel,
            CwDecoder decoder,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            LiveRxWpmGuard wpmGuard,
            RxTurnController turnController,
            TimingAnchorController timingAnchorController,
            RxRawCommitGate rawCommitGate,
            List<TurnTransitionTrace> capturedTurnTransitionTraces,
            CwFrontEndLearningGate frontEndLearningGate,
            LiveLikeFrontEndAuthorityGate frontEndAuthorityGate,
            LiveLikeOnsetAdmissionGate onsetAdmissionGate,
            LiveLikeReleasePressureHoldGate releasePressureHoldGate,
            LiveLikeShortGapMergeGate shortGapMergeGate,
            LiveRxToneEventStabilizer toneEventStabilizer,
            double detectionLevel,
            List<CwToneEvent> capturedToneEvents,
            List<CwTimingEvent> capturedTimingEvents,
            List<CwDecodeEvent> capturedDecodeEvents
    ) {
        if (toneEvent == null) {
            return;
        }
        CwSignalSnapshot currentSignalSnapshot = signalProcessor.snapshot();
        CwTimingSnapshot currentTimingSnapshot = timingModel.rawSnapshot();
        long referenceDotEstimateMs = resolveReferenceDotEstimateMs(
                currentSignalSnapshot,
                currentTimingSnapshot,
                nowTimestampMs,
                wpmGuard
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
                    signalProcessor,
                    timingModel,
                    decoder,
                    interpreter,
                    qsoStateMachine,
                    wpmGuard,
                    turnController,
                    timingAnchorController,
                    rawCommitGate,
                    capturedTurnTransitionTraces,
                    frontEndLearningGate,
                    frontEndAuthorityGate,
                    onsetAdmissionGate,
                    releasePressureHoldGate,
                    shortGapMergeGate,
                    toneEventStabilizer,
                    detectionLevel,
                    capturedToneEvents,
                    capturedTimingEvents,
                    capturedDecodeEvents
            );
        }
    }

    private static void flushLiveLikeToneEventStabilizer(
            long nowTimestampMs,
            AudioInputHealthSnapshot inputHealthSnapshot,
            CwSignalProcessor signalProcessor,
            CwHybridTimingModel timingModel,
            CwDecoder decoder,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            LiveRxWpmGuard wpmGuard,
            RxTurnController turnController,
            TimingAnchorController timingAnchorController,
            RxRawCommitGate rawCommitGate,
            List<TurnTransitionTrace> capturedTurnTransitionTraces,
            CwFrontEndLearningGate frontEndLearningGate,
            LiveLikeFrontEndAuthorityGate frontEndAuthorityGate,
            LiveLikeOnsetAdmissionGate onsetAdmissionGate,
            LiveLikeReleasePressureHoldGate releasePressureHoldGate,
            LiveLikeShortGapMergeGate shortGapMergeGate,
            LiveRxToneEventStabilizer toneEventStabilizer,
            double detectionLevel,
            List<CwToneEvent> capturedToneEvents,
            List<CwTimingEvent> capturedTimingEvents,
            List<CwDecodeEvent> capturedDecodeEvents
    ) {
        List<CwToneEvent> stabilizedEvents = toneEventStabilizer.flush(nowTimestampMs);
        for (CwToneEvent stabilizedEvent : stabilizedEvents) {
            dispatchLiveLikeToneEvent(
                    stabilizedEvent,
                    nowTimestampMs,
                    inputHealthSnapshot,
                    signalProcessor,
                    timingModel,
                    decoder,
                    interpreter,
                    qsoStateMachine,
                    wpmGuard,
                    turnController,
                    timingAnchorController,
                    rawCommitGate,
                    capturedTurnTransitionTraces,
                    frontEndLearningGate,
                    frontEndAuthorityGate,
                    onsetAdmissionGate,
                    releasePressureHoldGate,
                    shortGapMergeGate,
                    toneEventStabilizer,
                    detectionLevel,
                    capturedToneEvents,
                    capturedTimingEvents,
                    capturedDecodeEvents
            );
        }
    }

    private static void flushLiveLikeReleasePressureHoldGate(
            long nowTimestampMs,
            CwSignalProcessor signalProcessor,
            CwHybridTimingModel timingModel,
            CwDecoder decoder,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            LiveRxWpmGuard wpmGuard,
            RxTurnController turnController,
            TimingAnchorController timingAnchorController,
            RxRawCommitGate rawCommitGate,
            List<TurnTransitionTrace> capturedTurnTransitionTraces,
            CwFrontEndLearningGate frontEndLearningGate,
            LiveLikeFrontEndAuthorityGate frontEndAuthorityGate,
            LiveLikeOnsetAdmissionGate onsetAdmissionGate,
            LiveLikeReleasePressureHoldGate releasePressureHoldGate,
            LiveLikeShortGapMergeGate shortGapMergeGate,
            List<CwToneEvent> capturedToneEvents,
            List<CwTimingEvent> capturedTimingEvents,
            List<CwDecodeEvent> capturedDecodeEvents
    ) {
        if (releasePressureHoldGate == null) {
            return;
        }
        CwSignalSnapshot signalSnapshot = signalProcessor.snapshot();
        CwTimingSnapshot timingSnapshot = timingModel.rawSnapshot();
        long referenceDotEstimateMs = resolveReferenceDotEstimateMs(
                signalSnapshot,
                timingSnapshot,
                nowTimestampMs,
                wpmGuard
        );
        List<CwToneEvent> candidateEvents = releasePressureHoldGate.flush(
                signalSnapshot,
                referenceDotEstimateMs,
                nowTimestampMs
        );
        for (CwToneEvent candidateEvent : candidateEvents) {
            dispatchToneEventAfterReleasePressureHold(
                    candidateEvent,
                    nowTimestampMs,
                    null,
                    signalProcessor,
                    timingModel,
                    decoder,
                    interpreter,
                    qsoStateMachine,
                    wpmGuard,
                    turnController,
                    timingAnchorController,
                    rawCommitGate,
                    capturedTurnTransitionTraces,
                    frontEndLearningGate,
                    frontEndAuthorityGate,
                    onsetAdmissionGate,
                    shortGapMergeGate,
                    null,
                    capturedToneEvents,
                    capturedTimingEvents,
                    capturedDecodeEvents
            );
        }
    }

    private static void flushLiveLikeShortGapMergeGate(
            long nowTimestampMs,
            CwSignalProcessor signalProcessor,
            CwHybridTimingModel timingModel,
            CwDecoder decoder,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            LiveRxWpmGuard wpmGuard,
            RxTurnController turnController,
            TimingAnchorController timingAnchorController,
            RxRawCommitGate rawCommitGate,
            List<TurnTransitionTrace> capturedTurnTransitionTraces,
            CwFrontEndLearningGate frontEndLearningGate,
            LiveLikeFrontEndAuthorityGate frontEndAuthorityGate,
            LiveLikeShortGapMergeGate shortGapMergeGate,
            List<CwToneEvent> capturedToneEvents,
            List<CwTimingEvent> capturedTimingEvents,
            List<CwDecodeEvent> capturedDecodeEvents
    ) {
        if (shortGapMergeGate == null) {
            return;
        }
        CwSignalSnapshot signalSnapshot = signalProcessor.snapshot();
        CwTimingSnapshot timingSnapshot = timingModel.rawSnapshot();
        long referenceDotEstimateMs = resolveReferenceDotEstimateMs(
                signalSnapshot,
                timingSnapshot,
                nowTimestampMs,
                wpmGuard
        );
        List<CwToneEvent> admittedEvents = shortGapMergeGate.flush(
                signalSnapshot,
                frontEndAuthorityGate,
                referenceDotEstimateMs,
                nowTimestampMs
        );
        for (CwToneEvent admittedEvent : admittedEvents) {
            dispatchAdmittedLiveLikeToneEvent(
                    admittedEvent,
                    nowTimestampMs,
                    null,
                    signalProcessor,
                    timingModel,
                    decoder,
                    interpreter,
                    qsoStateMachine,
                    wpmGuard,
                    turnController,
                    timingAnchorController,
                    rawCommitGate,
                    capturedTurnTransitionTraces,
                    frontEndLearningGate,
                    frontEndAuthorityGate,
                    null,
                    capturedToneEvents,
                    capturedTimingEvents,
                    capturedDecodeEvents
            );
        }
    }

    private static void dispatchLiveLikeToneEvent(
            CwToneEvent toneEvent,
            long nowTimestampMs,
            AudioInputHealthSnapshot inputHealthSnapshot,
            CwSignalProcessor signalProcessor,
            CwHybridTimingModel timingModel,
            CwDecoder decoder,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            LiveRxWpmGuard wpmGuard,
            RxTurnController turnController,
            TimingAnchorController timingAnchorController,
            RxRawCommitGate rawCommitGate,
            List<TurnTransitionTrace> capturedTurnTransitionTraces,
            CwFrontEndLearningGate frontEndLearningGate,
            LiveLikeFrontEndAuthorityGate frontEndAuthorityGate,
            LiveLikeOnsetAdmissionGate onsetAdmissionGate,
            LiveLikeReleasePressureHoldGate releasePressureHoldGate,
            LiveLikeShortGapMergeGate shortGapMergeGate,
            LiveRxToneEventStabilizer toneEventStabilizer,
            double detectionLevel,
            List<CwToneEvent> capturedToneEvents,
            List<CwTimingEvent> capturedTimingEvents,
            List<CwDecodeEvent> capturedDecodeEvents
    ) {
        if (toneEvent == null) {
            return;
        }
        ArrayList<CwToneEvent> releaseCandidateEvents = new ArrayList<>(2);
        if (releasePressureHoldGate != null) {
            CwSignalSnapshot signalSnapshot = signalProcessor.snapshot();
            CwTimingSnapshot timingSnapshot = timingModel.rawSnapshot();
            long referenceDotEstimateMs = resolveReferenceDotEstimateMs(
                    signalSnapshot,
                    timingSnapshot,
                    nowTimestampMs,
                    wpmGuard
            );
            releaseCandidateEvents.addAll(releasePressureHoldGate.process(
                    toneEvent,
                    signalSnapshot,
                    referenceDotEstimateMs,
                    nowTimestampMs,
                    detectionLevel
            ));
        } else {
            releaseCandidateEvents.add(toneEvent);
        }

        for (CwToneEvent releaseCandidateEvent : releaseCandidateEvents) {
            dispatchToneEventAfterReleasePressureHold(
                    releaseCandidateEvent,
                    nowTimestampMs,
                    inputHealthSnapshot,
                    signalProcessor,
                    timingModel,
                    decoder,
                    interpreter,
                    qsoStateMachine,
                    wpmGuard,
                    turnController,
                    timingAnchorController,
                    rawCommitGate,
                    capturedTurnTransitionTraces,
                    frontEndLearningGate,
                    frontEndAuthorityGate,
                    onsetAdmissionGate,
                    shortGapMergeGate,
                    toneEventStabilizer,
                    capturedToneEvents,
                    capturedTimingEvents,
                    capturedDecodeEvents
            );
        }
    }

    private static void dispatchToneEventAfterReleasePressureHold(
            CwToneEvent toneEvent,
            long nowTimestampMs,
            AudioInputHealthSnapshot inputHealthSnapshot,
            CwSignalProcessor signalProcessor,
            CwHybridTimingModel timingModel,
            CwDecoder decoder,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            LiveRxWpmGuard wpmGuard,
            RxTurnController turnController,
            TimingAnchorController timingAnchorController,
            RxRawCommitGate rawCommitGate,
            List<TurnTransitionTrace> capturedTurnTransitionTraces,
            CwFrontEndLearningGate frontEndLearningGate,
            LiveLikeFrontEndAuthorityGate frontEndAuthorityGate,
            LiveLikeOnsetAdmissionGate onsetAdmissionGate,
            LiveLikeShortGapMergeGate shortGapMergeGate,
            LiveRxToneEventStabilizer toneEventStabilizer,
            List<CwToneEvent> capturedToneEvents,
            List<CwTimingEvent> capturedTimingEvents,
            List<CwDecodeEvent> capturedDecodeEvents
    ) {
        if (toneEvent == null) {
            return;
        }
        ArrayList<CwToneEvent> candidateEvents = new ArrayList<>(2);
        if (shortGapMergeGate != null) {
            CwSignalSnapshot signalSnapshot = signalProcessor.snapshot();
            CwTimingSnapshot timingSnapshot = timingModel.rawSnapshot();
            long referenceDotEstimateMs = resolveReferenceDotEstimateMs(
                    signalSnapshot,
                    timingSnapshot,
                    nowTimestampMs,
                    wpmGuard
            );
            candidateEvents.addAll(shortGapMergeGate.process(
                    toneEvent,
                    signalSnapshot,
                    frontEndAuthorityGate,
                    referenceDotEstimateMs,
                    nowTimestampMs
            ));
        } else {
            candidateEvents.add(toneEvent);
        }

        for (CwToneEvent candidateEvent : candidateEvents) {
            dispatchToneEventAfterShortGapMerge(
                    candidateEvent,
                    nowTimestampMs,
                    inputHealthSnapshot,
                    signalProcessor,
                    timingModel,
                    decoder,
                    interpreter,
                    qsoStateMachine,
                    wpmGuard,
                    turnController,
                    timingAnchorController,
                    rawCommitGate,
                    capturedTurnTransitionTraces,
                    frontEndLearningGate,
                    frontEndAuthorityGate,
                    onsetAdmissionGate,
                    toneEventStabilizer,
                    capturedToneEvents,
                    capturedTimingEvents,
                    capturedDecodeEvents
            );
        }
    }

    private static void dispatchToneEventAfterShortGapMerge(
            CwToneEvent toneEvent,
            long nowTimestampMs,
            AudioInputHealthSnapshot inputHealthSnapshot,
            CwSignalProcessor signalProcessor,
            CwHybridTimingModel timingModel,
            CwDecoder decoder,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            LiveRxWpmGuard wpmGuard,
            RxTurnController turnController,
            TimingAnchorController timingAnchorController,
            RxRawCommitGate rawCommitGate,
            List<TurnTransitionTrace> capturedTurnTransitionTraces,
            CwFrontEndLearningGate frontEndLearningGate,
            LiveLikeFrontEndAuthorityGate frontEndAuthorityGate,
            LiveLikeOnsetAdmissionGate onsetAdmissionGate,
            LiveRxToneEventStabilizer toneEventStabilizer,
            List<CwToneEvent> capturedToneEvents,
            List<CwTimingEvent> capturedTimingEvents,
            List<CwDecodeEvent> capturedDecodeEvents
    ) {
        if (toneEvent == null) {
            return;
        }
        if (onsetAdmissionGate != null) {
            List<CwToneEvent> admittedEvents = onsetAdmissionGate.process(
                    toneEvent,
                    signalProcessor.snapshot(),
                    frontEndAuthorityGate,
                    nowTimestampMs
            );
            for (CwToneEvent admittedEvent : admittedEvents) {
                dispatchAdmittedLiveLikeToneEvent(
                        admittedEvent,
                        nowTimestampMs,
                        inputHealthSnapshot,
                        signalProcessor,
                        timingModel,
                        decoder,
                        interpreter,
                        qsoStateMachine,
                        wpmGuard,
                        turnController,
                        timingAnchorController,
                        rawCommitGate,
                        capturedTurnTransitionTraces,
                        frontEndLearningGate,
                        frontEndAuthorityGate,
                        toneEventStabilizer,
                        capturedToneEvents,
                        capturedTimingEvents,
                        capturedDecodeEvents
                );
            }
            return;
        }
        dispatchAdmittedLiveLikeToneEvent(
                toneEvent,
                nowTimestampMs,
                inputHealthSnapshot,
                signalProcessor,
                timingModel,
                decoder,
                interpreter,
                qsoStateMachine,
                wpmGuard,
                turnController,
                timingAnchorController,
                rawCommitGate,
                capturedTurnTransitionTraces,
                frontEndLearningGate,
                frontEndAuthorityGate,
                toneEventStabilizer,
                capturedToneEvents,
                capturedTimingEvents,
                capturedDecodeEvents
        );
    }

    private static void flushLiveLikeOnsetAdmissionGate(
            long nowTimestampMs,
            CwSignalProcessor signalProcessor,
            LiveLikeFrontEndAuthorityGate frontEndAuthorityGate,
            LiveLikeOnsetAdmissionGate onsetAdmissionGate,
            CwHybridTimingModel timingModel,
            CwDecoder decoder,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            LiveRxWpmGuard wpmGuard,
            RxTurnController turnController,
            TimingAnchorController timingAnchorController,
            RxRawCommitGate rawCommitGate,
            List<TurnTransitionTrace> capturedTurnTransitionTraces,
            CwFrontEndLearningGate frontEndLearningGate,
            List<CwToneEvent> capturedToneEvents,
            List<CwTimingEvent> capturedTimingEvents,
            List<CwDecodeEvent> capturedDecodeEvents
    ) {
        if (onsetAdmissionGate == null) {
            return;
        }
        List<CwToneEvent> admittedEvents = onsetAdmissionGate.flush(
                signalProcessor.snapshot(),
                frontEndAuthorityGate,
                nowTimestampMs
        );
        for (CwToneEvent admittedEvent : admittedEvents) {
            dispatchAdmittedLiveLikeToneEvent(
                    admittedEvent,
                    nowTimestampMs,
                    null,
                    signalProcessor,
                    timingModel,
                    decoder,
                    interpreter,
                    qsoStateMachine,
                    wpmGuard,
                    turnController,
                    timingAnchorController,
                    rawCommitGate,
                    capturedTurnTransitionTraces,
                    frontEndLearningGate,
                    frontEndAuthorityGate,
                    null,
                    capturedToneEvents,
                    capturedTimingEvents,
                    capturedDecodeEvents
            );
        }
    }

    private static void dispatchAdmittedLiveLikeToneEvent(
            CwToneEvent toneEvent,
            long nowTimestampMs,
            AudioInputHealthSnapshot inputHealthSnapshot,
            CwSignalProcessor signalProcessor,
            CwHybridTimingModel timingModel,
            CwDecoder decoder,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            LiveRxWpmGuard wpmGuard,
            RxTurnController turnController,
            TimingAnchorController timingAnchorController,
            RxRawCommitGate rawCommitGate,
            List<TurnTransitionTrace> capturedTurnTransitionTraces,
            CwFrontEndLearningGate frontEndLearningGate,
            LiveLikeFrontEndAuthorityGate frontEndAuthorityGate,
            LiveRxToneEventStabilizer toneEventStabilizer,
            List<CwToneEvent> capturedToneEvents,
            List<CwTimingEvent> capturedTimingEvents,
            List<CwDecodeEvent> capturedDecodeEvents
    ) {
        if (toneEvent == null) {
            return;
        }
        CwSignalSnapshot signalSnapshot = signalProcessor.snapshot();
        CwTimingSnapshot timingSnapshot = timingModel.rawSnapshot();
        long referenceDotEstimateMs = resolveReferenceDotEstimateMs(
                signalSnapshot,
                timingSnapshot,
                nowTimestampMs,
                wpmGuard
        );
        if (toneEventStabilizer != null
                && referenceDotEstimateMs > 0L
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
        handleTurnTransition(
                currentSignalSnapshot,
                signalProcessor,
                decoder,
                timingModel,
                wpmGuard,
                turnController,
                timingAnchorController,
                rawCommitGate,
                capturedTurnTransitionTraces,
                nowTimestampMs
        );
        String timingLearningDecision = diagnoseTimingLearningForEventDecision(
                toneEvent,
                currentSignalSnapshot,
                currentTimingSnapshot,
                inputHealthSnapshot,
                nowTimestampMs,
                frontEndLearningGate,
                wpmGuard,
                frontEndAuthorityGate,
                timingAnchorController
        );
        boolean allowTimingLearning = "pass".equals(timingLearningDecision);
        LiveLikeProbeDiagnosticsCollector diagnosticsCollector = liveLikeDiagnosticsCollector();
        boolean trustedTimingEstablished = RxStableDecodeDecider.hasTrustedTiming(
                timingAnchorController
        );
        if (diagnosticsCollector != null) {
            diagnosticsCollector.timingLearningDecisionTraces.add(new TimingLearningDecisionTrace(
                    toneEvent.timestampMs(),
                    toneEvent.type().name(),
                    timingLearningDecision,
                    allowTimingLearning,
                    trustedTimingEstablished,
                    currentSignalSnapshot != null && currentSignalSnapshot.targetToneLocked(),
                    currentSignalSnapshot == null ? 0.0d : currentSignalSnapshot.recentLockedFrameRatio(),
                    currentSignalSnapshot == null ? 0.0d : currentSignalSnapshot.recentNearTargetLockedFrameRatio(),
                    currentSignalSnapshot == null ? 0.0d : currentSignalSnapshot.recentActiveUnlockedFrameRatio(),
                    currentSignalSnapshot == null ? 0.0d : currentSignalSnapshot.toneDominanceRatio(),
                    currentSignalSnapshot == null ? 0.0d : currentSignalSnapshot.narrowbandIsolationRatio(),
                    inputHealthSnapshot == null ? 0.0d : inputHealthSnapshot.recentHotFrameRatio(),
                    inputHealthSnapshot == null ? 0.0d : inputHealthSnapshot.recentClippingFrameRatio(),
                    timingAnchorController == null ? "-" : timingAnchorController.compactDebugSummary(),
                    currentTimingSnapshot == null
                            ? 0.0d
                            : currentTimingSnapshot.estimatedWpmPrecise() > 0.0d
                            ? currentTimingSnapshot.estimatedWpmPrecise()
                            : currentTimingSnapshot.estimatedWpm(),
                    currentTimingSnapshot == null ? 0L : currentTimingSnapshot.dotEstimateMs()
            ));
        }
        capturedToneEvents.add(toneEvent);
        RxTimingDecodeRunner timingDecodeRunner = new RxTimingDecodeRunner(decoder);
        RxToneTimingRunner toneTimingRunner = new RxToneTimingRunner(timingDecodeRunner);
        final CwSignalSnapshot[] finalSignalSnapshot = new CwSignalSnapshot[1];
        final CwTimingSnapshot[] finalTimingSnapshot = new CwTimingSnapshot[1];
        toneTimingRunner.dispatchToneEvents(
                java.util.Collections.singletonList(toneEvent),
                event -> {
                    List<CwTimingEvent> timingEvents = timingModel.process(event, allowTimingLearning);
                    finalSignalSnapshot[0] = signalProcessor.snapshot();
                    finalTimingSnapshot[0] = timingModel.rawSnapshot();
                    return timingEvents;
                },
                null,
                timingEvent -> prepareLiveLikeTimingEventForDecode(
                        timingEvent,
                        nowTimestampMs,
                        finalSignalSnapshot[0],
                        finalTimingSnapshot[0],
                        signalProcessor,
                        timingModel,
                        wpmGuard,
                        turnController,
                        timingAnchorController,
                        rawCommitGate,
                        frontEndLearningGate,
                        frontEndAuthorityGate,
                        capturedTimingEvents
                ),
                liveLikeDecodeEventConsumer(
                        inputHealthSnapshot,
                        signalProcessor,
                        timingModel,
                        interpreter,
                        qsoStateMachine,
                        wpmGuard,
                        turnController,
                        timingAnchorController,
                        rawCommitGate,
                        frontEndLearningGate,
                        frontEndAuthorityGate,
                        capturedDecodeEvents
                )
        );
    }

    private static CwTimingEvent prepareLiveLikeTimingEventForDecode(
            @Nullable CwTimingEvent timingEvent,
            long timelineTimestampMs,
            @Nullable CwSignalSnapshot adaptationSignalSnapshot,
            @Nullable CwTimingSnapshot adaptationTimingSnapshot,
            CwSignalProcessor signalProcessor,
            CwHybridTimingModel timingModel,
            LiveRxWpmGuard wpmGuard,
            RxTurnController turnController,
            TimingAnchorController timingAnchorController,
            RxRawCommitGate rawCommitGate,
            CwFrontEndLearningGate frontEndLearningGate,
            LiveLikeFrontEndAuthorityGate frontEndAuthorityGate,
            List<CwTimingEvent> capturedTimingEvents
    ) {
        if (timingEvent == null) {
            return null;
        }
        CwTimingEvent wpmGuardTimingEvent = wpmGuard.adaptTimingEvent(
                timingEvent,
                adaptationSignalSnapshot,
                adaptationTimingSnapshot,
                timelineTimestampMs
        );
        CwTimingEvent adaptedTimingEvent = timingAnchorController == null
                ? wpmGuardTimingEvent
                : timingAnchorController.adaptTimingEvent(
                wpmGuardTimingEvent,
                adaptationSignalSnapshot,
                adaptationTimingSnapshot,
                timelineTimestampMs
        );
        LiveLikeProbeDiagnosticsCollector diagnosticsCollector = liveLikeDiagnosticsCollector();
        if (diagnosticsCollector != null) {
            diagnosticsCollector.timingEventAdaptationTraces.add(new TimingEventAdaptationTrace(
                    timingEvent.timestampMs(),
                    timingEvent.kind().name(),
                    timingEvent.durationMs(),
                    timingEvent.classification().name(),
                    wpmGuardTimingEvent == null ? "(null)" : wpmGuardTimingEvent.classification().name(),
                    adaptedTimingEvent == null ? "(null)" : adaptedTimingEvent.classification().name(),
                    timingEvent.dotEstimateMs(),
                    timingEvent.intraGapEstimateMs(),
                    wpmGuardTimingEvent == null ? 0L : wpmGuardTimingEvent.dotEstimateMs(),
                    wpmGuardTimingEvent == null ? 0L : wpmGuardTimingEvent.intraGapEstimateMs(),
                    adaptedTimingEvent == null ? 0L : adaptedTimingEvent.dotEstimateMs(),
                    adaptedTimingEvent == null ? 0L : adaptedTimingEvent.intraGapEstimateMs(),
                    RxStableDecodeDecider.hasTrustedTiming(timingAnchorController),
                    timingAnchorController == null
                            ? TimingAnchorController.TrustOrigin.NONE.name()
                            : timingAnchorController.trustOrigin().name(),
                    timingAnchorController == null
                            ? 0L
                            : timingAnchorController.trustedDotEstimateMs()
            ));
        }
        if (adaptedTimingEvent == null) {
            return null;
        }
        capturedTimingEvents.add(adaptedTimingEvent);
        CwSignalSnapshot signalSnapshot = signalProcessor.snapshot();
        CwTimingSnapshot timingSnapshot = timingModel.rawSnapshot();
        maybeNoteBootstrapCadenceObservation(
                adaptedTimingEvent,
                signalSnapshot,
                timingSnapshot,
                timingModel,
                wpmGuard,
                timingAnchorController,
                frontEndLearningGate,
                frontEndAuthorityGate
        );
        maybeNoteBootstrapTimingBoundary(
                adaptedTimingEvent,
                signalSnapshot,
                timingSnapshot,
                timingModel,
                wpmGuard,
                turnController,
                timingAnchorController,
                frontEndLearningGate,
                frontEndAuthorityGate
        );
        if (rawCommitGate != null) {
            rawCommitGate.noteTimingEvent(
                    adaptedTimingEvent,
                    RxStableDecodeDecider.hasTrustedTiming(timingAnchorController),
                    timingAnchorController == null
                            ? TimingAnchorController.TrustOrigin.NONE
                            : timingAnchorController.trustOrigin(),
                    timingAnchorController == null
                            ? 0L
                            : timingAnchorController.trustedDotEstimateMs(),
                    timingAnchorController == null
                            ? -1L
                            : timingAnchorController.lastTrustedUpdateTimestampMs()
            );
        }
        return adaptedTimingEvent;
    }

    private static void processLiveLikeDecodeEvent(
            CwDecodeEvent decodeEvent,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot,
            CwSignalProcessor signalProcessor,
            CwHybridTimingModel timingModel,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            LiveRxWpmGuard wpmGuard,
            RxTurnController turnController,
            TimingAnchorController timingAnchorController,
            RxRawCommitGate rawCommitGate,
            CwFrontEndLearningGate frontEndLearningGate,
            LiveLikeFrontEndAuthorityGate frontEndAuthorityGate,
            List<CwDecodeEvent> capturedDecodeEvents
    ) {
        if (decodeEvent == null) {
            return;
        }
        if (turnController != null && turnController.phase() != RxTurnController.Phase.ACTIVE) {
            return;
        }
        LiveLikeProbeDiagnosticsCollector diagnosticsCollector = liveLikeDiagnosticsCollector();
        if (diagnosticsCollector != null) {
            diagnosticsCollector.rawDecodeEvents.add(decodeEvent);
        }
        CwSignalSnapshot signalSnapshot = signalProcessor.snapshot();
        CwTimingSnapshot timingSnapshot = timingModel.rawSnapshot();
        LiveLikeStableDecisionCompatibilityAdapter.DecisionOutcome stableOutcome =
                diagnoseStableDecodeDecisionOutcome(
                decodeEvent,
                signalSnapshot,
                timingSnapshot,
                inputHealthSnapshot,
                frontEndLearningGate,
                frontEndAuthorityGate,
                timingAnchorController
        );
        String stableDecision = stableOutcome.compatibleDecision();
        String verifiedStableDecision = stableOutcome.verifiedDecision();
        boolean trustedTimingEstablishedBefore = RxStableDecodeDecider.hasTrustedTiming(
                timingAnchorController
        );
        if (diagnosticsCollector != null
                && decodeEvent.type() == CwDecodeEvent.Type.CHARACTER_DECODED) {
            diagnosticsCollector.stableDecisionTraces.add(new StableDecisionTrace(
                    decodeEvent.timestampMs(),
                    decodeEvent.emittedValue(),
                    decodeEvent.sourceSequence(),
                    decodeEvent.unknownCharacter(),
                    stableDecision,
                    verifiedStableDecision,
                    trustedTimingEstablishedBefore,
                    signalSnapshot == null ? false : signalSnapshot.targetToneLocked(),
                    signalSnapshot == null ? 0.0d : signalSnapshot.recentLockedFrameRatio(),
                    signalSnapshot == null ? 0.0d : signalSnapshot.recentNearTargetLockedFrameRatio(),
                    signalSnapshot == null ? 0.0d : signalSnapshot.recentActiveUnlockedFrameRatio(),
                    signalSnapshot == null ? 0.0d : signalSnapshot.toneDominanceRatio(),
                    signalSnapshot == null ? 0.0d : signalSnapshot.narrowbandIsolationRatio(),
                    inputHealthSnapshot == null ? 0.0d : inputHealthSnapshot.recentHotFrameRatio(),
                    inputHealthSnapshot == null ? 0.0d : inputHealthSnapshot.recentClippingFrameRatio(),
                    timingSnapshot == null ? 0.0d : timingSnapshot.estimatedWpmPrecise()
            ));
        }
        if ("pass".equals(stableDecision)) {
            if (diagnosticsCollector != null) {
                diagnosticsCollector.stableAcceptedDecodeEvents.add(decodeEvent);
            }
            timingModel.notifyStableDecode(decodeEvent.timestampMs());
            timingSnapshot = timingModel.rawSnapshot();
            if (timingAnchorController != null) {
                timingAnchorController.noteStableDecode(
                        signalSnapshot,
                        timingSnapshot,
                        decodeEvent.timestampMs()
                );
            }
            if (turnController != null && timingSnapshot != null) {
                int anchorWpm = timingSnapshot.estimatedWpm() > 0
                        ? timingSnapshot.estimatedWpm()
                        : (int) Math.round(Math.max(0.0d, timingSnapshot.estimatedWpmPrecise()));
                boolean carryEligible = timingModel.debugSnapshot().trustedDotEstimateMs() > 0.0d;
                turnController.noteStableDecode(
                        decodeEvent.timestampMs(),
                        anchorWpm,
                        carryEligible
                );
            }
        } else {
            maybeNoteSoftStableBootstrapCandidate(
                    diagnosticsCollector,
                    decodeEvent,
                    stableDecision,
                    trustedTimingEstablishedBefore,
                    inputHealthSnapshot,
                    timingSnapshot,
                    timingModel,
                    timingAnchorController,
                    signalSnapshot
            );
            if (diagnosticsCollector != null) {
                diagnosticsCollector.noteStableReject(stableDecision);
            }
        }
        boolean trustedTimingEstablishedForAdmission = "pass".equals(stableDecision)
                ? RxStableDecodeDecider.hasTrustedTiming(timingAnchorController)
                : trustedTimingEstablishedBefore;
        List<CwDecodeEvent> admittedEvents = rawCommitGate == null
                ? java.util.Collections.singletonList(decodeEvent)
                : rawCommitGate.admit(
                        decodeEvent,
                        trustedTimingEstablishedForAdmission,
                        timingAnchorController == null
                                ? TimingAnchorController.TrustOrigin.NONE
                                : timingAnchorController.trustOrigin(),
                        timingAnchorController == null
                                ? 0L
                                : timingAnchorController.trustedDotEstimateMs(),
                        timingAnchorController == null
                                ? -1L
                                : timingAnchorController.lastTrustedUpdateTimestampMs()
                );
        for (CwDecodeEvent admittedEvent : admittedEvents) {
            if (admittedEvent.type() == CwDecodeEvent.Type.CHARACTER_DECODED) {
                signalSnapshot = signalProcessor.snapshot();
                timingSnapshot = timingModel.rawSnapshot();
                RxLearningAuthority characterAuthority = decodedCharacterAuthority(
                        frontEndAuthorityGate,
                        signalSnapshot,
                        admittedEvent.timestampMs()
                );
                if (characterAuthority == RxLearningAuthority.DECODE_AND_LEARN) {
                    wpmGuard.noteDecodedCharacter(
                            admittedEvent.unknownCharacter(),
                            signalSnapshot,
                            timingSnapshot,
                            admittedEvent.timestampMs()
                    );
                } else if (characterAuthority == RxLearningAuthority.BLOCKED) {
                    wpmGuard.noteDecodedCharacter(
                            true,
                            signalSnapshot,
                            timingSnapshot,
                            admittedEvent.timestampMs()
                    );
                }
            }
            interpreter.process(admittedEvent);
            qsoStateMachine.process(interpreter.snapshot(), admittedEvent.timestampMs());
            capturedDecodeEvents.add(admittedEvent);
        }
    }

    private static void maybeNoteSoftStableBootstrapCandidate(
            @Nullable LiveLikeProbeDiagnosticsCollector diagnosticsCollector,
            CwDecodeEvent decodeEvent,
            String stableDecision,
            boolean trustedTimingEstablishedBefore,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            CwHybridTimingModel timingModel,
            @Nullable TimingAnchorController timingAnchorController,
            @Nullable CwSignalSnapshot signalSnapshot
    ) {
        if (!shouldUseSoftStableBootstrapCandidate(
                diagnosticsCollector,
                decodeEvent,
                stableDecision,
                trustedTimingEstablishedBefore,
                inputHealthSnapshot,
                timingSnapshot,
                signalSnapshot
        )) {
            return;
        }
        long candidateDotEstimateMs = resolveSoftStableBootstrapCandidateDotEstimateMs(timingSnapshot);
        if (candidateDotEstimateMs <= 0L) {
            return;
        }
        timingModel.noteBootstrapSoftStableObservation(
                candidateDotEstimateMs,
                decodeEvent.timestampMs()
        );
        if (timingAnchorController != null) {
            timingAnchorController.noteBootstrapSoftStableObservation(
                    candidateDotEstimateMs,
                    decodeEvent.timestampMs()
            );
        }
    }

    private static boolean shouldUseSoftStableBootstrapCandidate(
            @Nullable LiveLikeProbeDiagnosticsCollector diagnosticsCollector,
            @Nullable CwDecodeEvent decodeEvent,
            String stableDecision,
            boolean trustedTimingEstablishedBefore,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            @Nullable CwSignalSnapshot signalSnapshot
    ) {
        if (diagnosticsCollector == null
                || !diagnosticsCollector.experimentalSoftStableBootstrapCandidateEnabled
                || decodeEvent == null
                || decodeEvent.type() != CwDecodeEvent.Type.CHARACTER_DECODED
                || trustedTimingEstablishedBefore
                || decodeEvent.unknownCharacter()
                || !"front-end-learning".equals(stableDecision)) {
            return false;
        }
        if (resolveSoftStableBootstrapCandidateDotEstimateMs(timingSnapshot) <= 0L
                || signalSnapshot == null) {
            return false;
        }
        return signalSnapshot.recentLockedFrameRatio() >= 0.75d
                && signalSnapshot.recentNearTargetLockedFrameRatio() >= 0.95d
                && signalSnapshot.recentActiveUnlockedFrameRatio() <= 0.02d
                && (inputHealthSnapshot == null || inputHealthSnapshot.recentHotFrameRatio() <= 0.46d)
                && (inputHealthSnapshot == null
                || inputHealthSnapshot.recentClippingFrameRatio() <= 0.01d);
    }

    private static long resolveSoftStableBootstrapCandidateDotEstimateMs(
            @Nullable CwTimingSnapshot timingSnapshot
    ) {
        if (timingSnapshot == null) {
            return 0L;
        }
        double rawWpm = timingSnapshot.estimatedWpmPrecise();
        if (rawWpm <= 0.0d && timingSnapshot.estimatedWpm() > 0) {
            rawWpm = timingSnapshot.estimatedWpm();
        }
        if (rawWpm > 0.0d) {
            return Math.max(1L, Math.round(1200.0d / rawWpm));
        }
        return Math.max(0L, timingSnapshot.dotEstimateMs());
    }

    private static void maybeFlushPendingCharacterDuringSilence(
            long flushTimestampMs,
            AudioInputHealthSnapshot inputHealthSnapshot,
            CwSignalProcessor signalProcessor,
            CwHybridTimingModel timingModel,
            CwDecoder decoder,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            LiveRxWpmGuard wpmGuard,
            RxTurnController turnController,
            TimingAnchorController timingAnchorController,
            RxRawCommitGate rawCommitGate,
            List<TurnTransitionTrace> capturedTurnTransitionTraces,
            CwFrontEndLearningGate frontEndLearningGate,
            LiveLikeFrontEndAuthorityGate frontEndAuthorityGate,
            LiveLikeOnsetAdmissionGate onsetAdmissionGate,
            List<CwDecodeEvent> capturedDecodeEvents
    ) {
        if (flushTimestampMs <= 0L || !decoder.hasPendingCharacter()) {
            return;
        }
        CwSignalSnapshot signalSnapshot = signalProcessor.snapshot();
        handleTurnTransition(
                signalSnapshot,
                signalProcessor,
                decoder,
                timingModel,
                wpmGuard,
                turnController,
                timingAnchorController,
                rawCommitGate,
                capturedTurnTransitionTraces,
                flushTimestampMs
        );
        if (RxTurnActivityDecider.isMeaningfulTurnActivity(signalSnapshot)) {
            return;
        }
        CwToneEvent lastSignalEvent = signalSnapshot.lastEvent();
        if (lastSignalEvent == null || lastSignalEvent.type() != CwToneEvent.Type.TONE_OFF) {
            return;
        }
        long silentGapMs = Math.max(0L, flushTimestampMs - lastSignalEvent.timestampMs());
        long minFlushGapMs = minimumLiveCharacterFlushGapMs(
                signalSnapshot,
                timingModel.rawSnapshot(),
                flushTimestampMs,
                wpmGuard
        );
        if (silentGapMs < minFlushGapMs) {
            return;
        }

        RxTimingDecodeRunner timingDecodeRunner = new RxTimingDecodeRunner(decoder);
        timingDecodeRunner.flushPendingCharacter(
                flushTimestampMs,
                liveLikeDecodeEventConsumer(
                        inputHealthSnapshot,
                        signalProcessor,
                        timingModel,
                        interpreter,
                        qsoStateMachine,
                        wpmGuard,
                        turnController,
                        timingAnchorController,
                        rawCommitGate,
                        frontEndLearningGate,
                        frontEndAuthorityGate,
                        capturedDecodeEvents
                )
        );
    }

    private static void flushPendingDecode(
            long timestampMs,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot,
            CwSignalProcessor signalProcessor,
            CwHybridTimingModel timingModel,
            CwDecoder decoder,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            LiveRxWpmGuard wpmGuard,
            RxTurnController turnController,
            TimingAnchorController timingAnchorController,
            RxRawCommitGate rawCommitGate,
            List<TurnTransitionTrace> capturedTurnTransitionTraces,
            CwFrontEndLearningGate frontEndLearningGate,
            LiveLikeFrontEndAuthorityGate frontEndAuthorityGate,
            LiveLikeOnsetAdmissionGate onsetAdmissionGate,
            List<CwTimingEvent> capturedTimingEvents,
            List<CwDecodeEvent> capturedDecodeEvents
    ) {
        CwSignalSnapshot currentSignalSnapshot = signalProcessor.snapshot();
        CwTimingSnapshot currentTimingSnapshot = timingModel.rawSnapshot();
        handleTurnTransition(
                currentSignalSnapshot,
                signalProcessor,
                decoder,
                timingModel,
                wpmGuard,
                turnController,
                timingAnchorController,
                rawCommitGate,
                capturedTurnTransitionTraces,
                timestampMs
        );
        boolean allowTimingLearning = shouldAllowTimingLearning(
                currentSignalSnapshot,
                currentTimingSnapshot,
                null,
                timestampMs,
                frontEndLearningGate,
                wpmGuard,
                frontEndAuthorityGate,
                timingAnchorController
        );
        List<CwTimingEvent> timingEvents = timingModel.flushPendingGap(timestampMs, allowTimingLearning);
        currentSignalSnapshot = signalProcessor.snapshot();
        currentTimingSnapshot = timingModel.rawSnapshot();
        RxTimingDecodeRunner timingDecodeRunner = new RxTimingDecodeRunner(decoder);
        CwSignalSnapshot finalSignalSnapshot = currentSignalSnapshot;
        CwTimingSnapshot finalTimingSnapshot = currentTimingSnapshot;
        timingDecodeRunner.dispatchTimingEvents(
                timingEvents,
                timingEvent -> prepareLiveLikeTimingEventForDecode(
                        timingEvent,
                        timestampMs,
                        finalSignalSnapshot,
                        finalTimingSnapshot,
                        signalProcessor,
                        timingModel,
                        wpmGuard,
                        turnController,
                        timingAnchorController,
                        rawCommitGate,
                        frontEndLearningGate,
                        frontEndAuthorityGate,
                        capturedTimingEvents
                ),
                liveLikeDecodeEventConsumer(
                        inputHealthSnapshot,
                        signalProcessor,
                        timingModel,
                        interpreter,
                        qsoStateMachine,
                        wpmGuard,
                        turnController,
                        timingAnchorController,
                        rawCommitGate,
                        frontEndLearningGate,
                        frontEndAuthorityGate,
                        capturedDecodeEvents
                )
        );
        timingDecodeRunner.flushPendingCharacter(
                timestampMs,
                liveLikeDecodeEventConsumer(
                        inputHealthSnapshot,
                        signalProcessor,
                        timingModel,
                        interpreter,
                        qsoStateMachine,
                        wpmGuard,
                        turnController,
                        timingAnchorController,
                        rawCommitGate,
                        frontEndLearningGate,
                        frontEndAuthorityGate,
                        capturedDecodeEvents
                )
        );
    }

    private static void handleTurnTransition(
            CwSignalSnapshot signalSnapshot,
            CwSignalProcessor signalProcessor,
            CwDecoder decoder,
            CwHybridTimingModel timingModel,
            LiveRxWpmGuard wpmGuard,
            RxTurnController turnController,
            TimingAnchorController timingAnchorController,
            RxRawCommitGate rawCommitGate,
            List<TurnTransitionTrace> capturedTurnTransitionTraces,
            long timestampMs
    ) {
        if (signalSnapshot == null
                || decoder == null
                || timingModel == null
                || wpmGuard == null
                || turnController == null) {
            return;
        }
        CwTimingSnapshot timingSnapshot = timingModel.rawSnapshot();
        int referenceWpm = wpmGuard.resolveReferenceWpm(timingSnapshot);
        RxTurnSessionCoordinator.TurnEndListener noTurnEndListener = null;
        RxTurnSessionCoordinator.Observation observation = new RxTurnSessionCoordinator(
                signalProcessor,
                timingModel,
                wpmGuard,
                turnController,
                timingAnchorController,
                rawCommitGate,
                null,
                null,
                noTurnEndListener
        ).observe(
                signalSnapshot,
                decoder.hasPendingCharacter(),
                timestampMs,
                referenceWpm
        );
        RxTurnController.Transition transition = observation.transition();
        if ((observation.startedNewTurn() || observation.endedTurn()) && capturedTurnTransitionTraces != null) {
            capturedTurnTransitionTraces.add(new TurnTransitionTrace(
                    observation.startedNewTurn() ? TurnTransitionTrace.Kind.START : TurnTransitionTrace.Kind.END,
                    timestampMs,
                    turnController.turnIndex(),
                    transition.turnSeedWpm(),
                    referenceWpm,
                    turnController.currentTurnAnchorWpm(),
                    turnController.retainedTurnAnchorWpm(),
                    transition.reason(),
                    signalSnapshot,
                    timingModel.snapshot(),
                    timingModel.rawSnapshot()
            ));
        }
    }

    private static boolean shouldTreatAsStableDecode(
            CwDecodeEvent decodeEvent,
            CwSignalSnapshot signalSnapshot,
            CwTimingSnapshot timingSnapshot,
            AudioInputHealthSnapshot inputHealthSnapshot,
            CwFrontEndLearningGate frontEndLearningGate,
            LiveRxWpmGuard wpmGuard,
            LiveLikeFrontEndAuthorityGate frontEndAuthorityGate,
            TimingAnchorController timingAnchorController
    ) {
        return "pass".equals(diagnoseStableDecodeDecisionOutcome(
                decodeEvent,
                signalSnapshot,
                timingSnapshot,
                inputHealthSnapshot,
                frontEndLearningGate,
                frontEndAuthorityGate,
                timingAnchorController
        ).compatibleDecision());
    }

    private static LiveLikeStableDecisionCompatibilityAdapter.DecisionOutcome
    diagnoseStableDecodeDecisionOutcome(
            CwDecodeEvent decodeEvent,
            CwSignalSnapshot signalSnapshot,
            CwTimingSnapshot timingSnapshot,
            AudioInputHealthSnapshot inputHealthSnapshot,
            CwFrontEndLearningGate frontEndLearningGate,
            LiveLikeFrontEndAuthorityGate frontEndAuthorityGate,
            TimingAnchorController timingAnchorController
    ) {
        return LiveLikeStableDecisionCompatibilityAdapter.diagnoseDecision(
                decodeEvent,
                signalSnapshot,
                timingSnapshot,
                inputHealthSnapshot,
                frontEndLearningGate,
                adaptStableAuthorityGate(frontEndAuthorityGate),
                timingAnchorController
        );
    }

    @Nullable
    private static LiveLikeStableDecisionCompatibilityAdapter.StableAuthorityGate adaptStableAuthorityGate(
            @Nullable LiveLikeFrontEndAuthorityGate frontEndAuthorityGate
    ) {
        if (frontEndAuthorityGate == null) {
            return null;
        }
        return new LiveLikeStableDecisionCompatibilityAdapter.StableAuthorityGate() {
            @Override
            public boolean shouldAllowStableAnchorUpdate(
                    CwSignalSnapshot signalSnapshot,
                    long timestampMs
            ) {
                return frontEndAuthorityGate.shouldAllowStableAnchorUpdate(
                        signalSnapshot,
                        timestampMs
                );
            }

            @Override
            public boolean shouldAllowBootstrapStableAnchorUpdate(
                    CwSignalSnapshot signalSnapshot,
                    long timestampMs
            ) {
                return frontEndAuthorityGate.shouldAllowBootstrapStableAnchorUpdate(
                        signalSnapshot,
                        timestampMs
                );
            }
        };
    }

    private static void maybeNoteBootstrapCadenceObservation(
            @Nullable CwTimingEvent timingEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            @Nullable CwHybridTimingModel timingModel,
            @Nullable LiveRxWpmGuard wpmGuard,
            @Nullable TimingAnchorController timingAnchorController,
            @Nullable CwFrontEndLearningGate frontEndLearningGate,
            @Nullable LiveLikeFrontEndAuthorityGate frontEndAuthorityGate
    ) {
        LiveLikeProbeDiagnosticsCollector diagnosticsCollector = liveLikeDiagnosticsCollector();
        LiveLikeBootstrapDecisionCompatibilityAdapter.DecisionOutcome cadenceOutcome =
                LiveLikeBootstrapDecisionCompatibilityAdapter.diagnoseCadenceDecision(
                timingEvent,
                signalSnapshot,
                timingSnapshot,
                frontEndLearningGate,
                wpmGuard,
                frontEndAuthorityGate == null
                        ? null
                        : frontEndAuthorityGate::shouldAllowBootstrapStableAnchorUpdate,
                timingAnchorController
        );
        String compatibleCadenceDecision = cadenceOutcome.compatibleDecision();
        String verifiedCadenceDecision = cadenceOutcome.verifiedDecision();
        long candidateDotEstimateMs = timingEvent == null ? 0L : Math.max(0L, timingEvent.durationMs());
        if (diagnosticsCollector != null) {
            diagnosticsCollector.bootstrapCadenceDecisionTraces.add(new BootstrapDecisionTrace(
                    timingEvent == null ? 0L : timingEvent.timestampMs(),
                    timingEvent == null ? "" : timingEvent.kind().name(),
                    timingEvent == null ? "" : timingEvent.classification().name(),
                    timingEvent == null ? 0L : timingEvent.durationMs(),
                    candidateDotEstimateMs,
                    compatibleCadenceDecision,
                    verifiedCadenceDecision,
                    RxStableDecodeDecider.hasTrustedTiming(timingAnchorController),
                    signalSnapshot != null && signalSnapshot.targetToneLocked(),
                    signalSnapshot == null ? 0.0d : signalSnapshot.recentLockedFrameRatio(),
                    signalSnapshot == null ? 0.0d : signalSnapshot.recentNearTargetLockedFrameRatio(),
                    signalSnapshot == null ? 0.0d : signalSnapshot.recentActiveUnlockedFrameRatio(),
                    signalSnapshot == null ? 0.0d : signalSnapshot.toneDominanceRatio(),
                    signalSnapshot == null ? 0.0d : signalSnapshot.narrowbandIsolationRatio(),
                    timingSnapshot == null
                            ? 0.0d
                            : timingSnapshot.estimatedWpmPrecise() > 0.0d
                            ? timingSnapshot.estimatedWpmPrecise()
                            : timingSnapshot.estimatedWpm(),
                    timingSnapshot == null ? 0L : timingSnapshot.dotEstimateMs(),
                    timingAnchorController == null ? "-" : timingAnchorController.compactDebugSummary()
            ));
        }
        if (!"pass".equals(compatibleCadenceDecision) || timingModel == null) {
            if (diagnosticsCollector != null && !"pass".equals(compatibleCadenceDecision)) {
                diagnosticsCollector.noteBootstrapCadenceReject(compatibleCadenceDecision);
            }
            return;
        }
        if (diagnosticsCollector != null) {
            diagnosticsCollector.bootstrapCadenceTimingEvents.add(timingEvent);
        }
        RxBootstrapTimingObserver.applyBootstrapCadenceObservation(
                candidateDotEstimateMs,
                timingEvent.timestampMs(),
                timingModel,
                timingAnchorController
        );
    }

    private static void maybeNoteBootstrapTimingBoundary(
            @Nullable CwTimingEvent timingEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable CwTimingSnapshot timingSnapshot,
            @Nullable CwHybridTimingModel timingModel,
            @Nullable LiveRxWpmGuard wpmGuard,
            @Nullable RxTurnController turnController,
            @Nullable TimingAnchorController timingAnchorController,
            @Nullable CwFrontEndLearningGate frontEndLearningGate,
            @Nullable LiveLikeFrontEndAuthorityGate frontEndAuthorityGate
    ) {
        LiveLikeProbeDiagnosticsCollector diagnosticsCollector = liveLikeDiagnosticsCollector();
        LiveLikeBootstrapDecisionCompatibilityAdapter.DecisionOutcome boundaryOutcome =
                LiveLikeBootstrapDecisionCompatibilityAdapter.diagnoseTimingBoundaryDecision(
                timingEvent,
                signalSnapshot,
                timingSnapshot,
                frontEndLearningGate,
                wpmGuard,
                frontEndAuthorityGate == null
                        ? null
                        : frontEndAuthorityGate::shouldAllowBootstrapStableAnchorUpdate,
                timingAnchorController
        );
        String compatibleBoundaryDecision = boundaryOutcome.compatibleDecision();
        String verifiedBoundaryDecision = boundaryOutcome.verifiedDecision();
        long candidateDotEstimateMs = inferBootstrapBoundaryDotEstimateMs(timingEvent, timingSnapshot);
        if (diagnosticsCollector != null) {
            diagnosticsCollector.bootstrapBoundaryDecisionTraces.add(new BootstrapDecisionTrace(
                    timingEvent == null ? 0L : timingEvent.timestampMs(),
                    timingEvent == null ? "" : timingEvent.kind().name(),
                    timingEvent == null ? "" : timingEvent.classification().name(),
                    timingEvent == null ? 0L : timingEvent.durationMs(),
                    candidateDotEstimateMs,
                    compatibleBoundaryDecision,
                    verifiedBoundaryDecision,
                    RxStableDecodeDecider.hasTrustedTiming(timingAnchorController),
                    signalSnapshot != null && signalSnapshot.targetToneLocked(),
                    signalSnapshot == null ? 0.0d : signalSnapshot.recentLockedFrameRatio(),
                    signalSnapshot == null ? 0.0d : signalSnapshot.recentNearTargetLockedFrameRatio(),
                    signalSnapshot == null ? 0.0d : signalSnapshot.recentActiveUnlockedFrameRatio(),
                    signalSnapshot == null ? 0.0d : signalSnapshot.toneDominanceRatio(),
                    signalSnapshot == null ? 0.0d : signalSnapshot.narrowbandIsolationRatio(),
                    timingSnapshot == null
                            ? 0.0d
                            : timingSnapshot.estimatedWpmPrecise() > 0.0d
                            ? timingSnapshot.estimatedWpmPrecise()
                            : timingSnapshot.estimatedWpm(),
                    timingSnapshot == null ? 0L : timingSnapshot.dotEstimateMs(),
                    timingAnchorController == null ? "-" : timingAnchorController.compactDebugSummary()
            ));
        }
        if (!"pass".equals(compatibleBoundaryDecision) || timingModel == null) {
            if (diagnosticsCollector != null && !"pass".equals(compatibleBoundaryDecision)) {
                diagnosticsCollector.noteBootstrapBoundaryReject(compatibleBoundaryDecision);
            }
            return;
        }
        if (diagnosticsCollector != null) {
            diagnosticsCollector.bootstrapBoundaryTimingEvents.add(timingEvent);
        }
        if (candidateDotEstimateMs <= 0L) {
            return;
        }
        RxBootstrapTimingObserver.applyBootstrapTimingBoundaryObservation(
                candidateDotEstimateMs,
                timingEvent.timestampMs(),
                timingModel,
                timingAnchorController,
                turnController
        );
    }

    private static boolean isBootstrapCadenceGap(
            @Nullable CwTimingEvent timingEvent,
            @Nullable CwTimingSnapshot timingSnapshot
    ) {
        return RxBootstrapTimingObserver.isBootstrapCadenceGap(timingEvent, timingSnapshot);
    }

    private static long inferBootstrapBoundaryDotEstimateMs(
            @Nullable CwTimingEvent timingEvent,
            @Nullable CwTimingSnapshot timingSnapshot
    ) {
        return RxBootstrapTimingObserver.inferBootstrapBoundaryDotEstimateMs(
                timingEvent,
                timingSnapshot
        );
    }

    private static long resolveReferenceDotEstimateMs(
            CwSignalSnapshot signalSnapshot,
            CwTimingSnapshot timingSnapshot,
            long nowTimestampMs,
            LiveRxWpmGuard wpmGuard
    ) {
        long referenceDotEstimateMs = wpmGuard.resolveReferenceDotEstimateMs(timingSnapshot);
        if (referenceDotEstimateMs > 0L) {
            return referenceDotEstimateMs;
        }
        return wpmGuard.resolveEffectiveDotEstimateMs(signalSnapshot, timingSnapshot, nowTimestampMs);
    }

    private static long minimumLiveCharacterFlushGapMs(
            CwSignalSnapshot signalSnapshot,
            CwTimingSnapshot timingSnapshot,
            long nowTimestampMs,
            LiveRxWpmGuard wpmGuard
    ) {
        long dotEstimateMs = Math.max(
                1L,
                wpmGuard.resolveEffectiveDotEstimateMs(signalSnapshot, timingSnapshot, nowTimestampMs)
        );
        return Math.max(1L, Math.round(dotEstimateMs * LIVE_CHARACTER_FLUSH_GAP_RATIO));
    }

    private static boolean shouldAllowTimingLearning(
            CwSignalSnapshot signalSnapshot,
            CwTimingSnapshot timingSnapshot,
            AudioInputHealthSnapshot inputHealthSnapshot,
            long nowTimestampMs,
            CwFrontEndLearningGate frontEndLearningGate,
            LiveRxWpmGuard wpmGuard,
            LiveLikeFrontEndAuthorityGate frontEndAuthorityGate,
            TimingAnchorController timingAnchorController
    ) {
        if (frontEndLearningGate != null
                && !frontEndLearningGate.shouldAllowTimingLearning(signalSnapshot, inputHealthSnapshot)) {
            return false;
        }
        if (wpmGuard == null) {
            return timingAnchorController == null
                    || timingAnchorController.shouldAllowTimingLearning(
                    signalSnapshot,
                    timingSnapshot,
                    true,
                    nowTimestampMs
            );
        }
        boolean baseAllow = wpmGuard.shouldAllowTimingLearning(signalSnapshot, timingSnapshot, nowTimestampMs);
        return timingAnchorController == null
                || timingAnchorController.shouldAllowTimingLearning(
                signalSnapshot,
                timingSnapshot,
                baseAllow,
                nowTimestampMs
        );
    }

    private static boolean shouldAllowTimingLearningForEvent(
            CwToneEvent toneEvent,
            CwSignalSnapshot signalSnapshot,
            CwTimingSnapshot timingSnapshot,
            AudioInputHealthSnapshot inputHealthSnapshot,
            long nowTimestampMs,
            CwFrontEndLearningGate frontEndLearningGate,
            LiveRxWpmGuard wpmGuard,
            LiveLikeFrontEndAuthorityGate frontEndAuthorityGate,
            TimingAnchorController timingAnchorController
    ) {
        return "pass".equals(diagnoseTimingLearningForEventDecision(
                toneEvent,
                signalSnapshot,
                timingSnapshot,
                inputHealthSnapshot,
                nowTimestampMs,
                frontEndLearningGate,
                wpmGuard,
                frontEndAuthorityGate,
                timingAnchorController
        ));
    }

    private static String diagnoseTimingLearningForEventDecision(
            CwToneEvent toneEvent,
            CwSignalSnapshot signalSnapshot,
            CwTimingSnapshot timingSnapshot,
            AudioInputHealthSnapshot inputHealthSnapshot,
            long nowTimestampMs,
            CwFrontEndLearningGate frontEndLearningGate,
            LiveRxWpmGuard wpmGuard,
            LiveLikeFrontEndAuthorityGate frontEndAuthorityGate,
            TimingAnchorController timingAnchorController
    ) {
        boolean trustedTimingEstablished = RxStableDecodeDecider.hasTrustedTiming(
                timingAnchorController
        );
        if (frontEndLearningGate != null
                && !frontEndLearningGate.shouldAllowTimingLearningForEvent(
                toneEvent,
                signalSnapshot,
                inputHealthSnapshot,
                trustedTimingEstablished
        )) {
            return "front-end-learning";
        }
        if (wpmGuard == null) {
            if (timingAnchorController == null
                    || timingAnchorController.shouldAllowTimingLearningForEvent(
                    toneEvent,
                    signalSnapshot,
                    timingSnapshot,
                    toneEvent != null,
                    nowTimestampMs
            )) {
                return "pass";
            }
            return "anchor-guard";
        }
        boolean baseAllow = toneEvent != null && wpmGuard.shouldAllowTimingLearningForEvent(
                toneEvent,
                signalSnapshot,
                timingSnapshot,
                nowTimestampMs
        );
        if (!baseAllow) {
            return "wpm-guard";
        }
        if (timingAnchorController == null
                || timingAnchorController.shouldAllowTimingLearningForEvent(
                toneEvent,
                signalSnapshot,
                timingSnapshot,
                true,
                nowTimestampMs
        )) {
            return "pass";
        }
        return "anchor-guard";
    }

    private static RxLearningAuthority decodedCharacterAuthority(
            LiveLikeFrontEndAuthorityGate frontEndAuthorityGate,
            CwSignalSnapshot signalSnapshot,
            long nowTimestampMs
    ) {
        if (frontEndAuthorityGate == null) {
            return RxLearningAuthority.DECODE_AND_LEARN;
        }
        return frontEndAuthorityGate.decodedCharacterAuthority(signalSnapshot, nowTimestampMs);
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

    @Nullable
    private static LiveLikeProbeDiagnosticsCollector liveLikeDiagnosticsCollector() {
        return LIVE_LIKE_DIAGNOSTICS.get();
    }

    private static final class LiveLikeProbeDiagnosticsCollector {
        private final boolean experimentalSoftStableBootstrapCandidateEnabled;
        private final ArrayList<CwDecodeEvent> rawDecodeEvents = new ArrayList<>();
        private final ArrayList<CwDecodeEvent> stableAcceptedDecodeEvents = new ArrayList<>();
        private final ArrayList<StableDecisionTrace> stableDecisionTraces = new ArrayList<>();
        private final ArrayList<CwTimingEvent> bootstrapBoundaryTimingEvents = new ArrayList<>();
        private final ArrayList<CwTimingEvent> bootstrapCadenceTimingEvents = new ArrayList<>();
        private final ArrayList<BootstrapDecisionTrace> bootstrapBoundaryDecisionTraces =
                new ArrayList<>();
        private final ArrayList<BootstrapDecisionTrace> bootstrapCadenceDecisionTraces =
                new ArrayList<>();
        private final ArrayList<TimingLearningDecisionTrace> timingLearningDecisionTraces = new ArrayList<>();
        private final ArrayList<TimingEventAdaptationTrace> timingEventAdaptationTraces =
                new ArrayList<>();
        private final ArrayList<RxToneModeDecisionTrace> rxToneModeDecisionTraces =
                new ArrayList<>();
        private final LinkedHashMap<String, Integer> stableRejectCounts = new LinkedHashMap<>();
        private final LinkedHashMap<String, Integer> bootstrapBoundaryRejectCounts = new LinkedHashMap<>();
        private final LinkedHashMap<String, Integer> bootstrapCadenceRejectCounts = new LinkedHashMap<>();

        private LiveLikeProbeDiagnosticsCollector(
                boolean experimentalSoftStableBootstrapCandidateEnabled
        ) {
            this.experimentalSoftStableBootstrapCandidateEnabled =
                    experimentalSoftStableBootstrapCandidateEnabled;
        }

        private void noteStableReject(String reason) {
            incrementReason(stableRejectCounts, reason);
        }

        private void noteBootstrapBoundaryReject(String reason) {
            incrementReason(bootstrapBoundaryRejectCounts, reason);
        }

        private void noteBootstrapCadenceReject(String reason) {
            incrementReason(bootstrapCadenceRejectCounts, reason);
        }
    }

    private static void incrementReason(Map<String, Integer> counts, String reason) {
        if (counts == null) {
            return;
        }
        String safeReason = reason == null || reason.isEmpty() ? "unknown" : reason;
        Integer current = counts.get(safeReason);
        counts.put(safeReason, current == null ? 1 : current + 1);
    }

    private static final class LiveLikeFrontEndAuthorityGate {
        private final CwFrontEndAuthorityTracker delegate;

        private LiveLikeFrontEndAuthorityGate(int sqlPercent) {
            this.delegate = new CwFrontEndAuthorityTracker(sqlPercent);
        }

        private void observeFrame(
                CwSignalSnapshot signalSnapshot,
                AudioInputHealthSnapshot inputHealthSnapshot,
                long timestampMs
        ) {
            delegate.observeFrame(signalSnapshot, inputHealthSnapshot, timestampMs);
        }

        private boolean shouldAllowTimingLearning(long nowTimestampMs) {
            return delegate.shouldAllowTimingLearning(nowTimestampMs);
        }

        private boolean shouldAllowTimingLearningForEvent(
                CwToneEvent toneEvent,
                long nowTimestampMs
        ) {
            return delegate.shouldAllowTimingLearningForEvent(toneEvent, nowTimestampMs);
        }

        private boolean shouldAllowStableAnchorUpdate(
                CwSignalSnapshot signalSnapshot,
                long nowTimestampMs
        ) {
            return delegate.shouldAllowStableAnchorUpdate(signalSnapshot, nowTimestampMs);
        }

        private boolean shouldAllowBootstrapStableAnchorUpdate(
                CwSignalSnapshot signalSnapshot,
                long nowTimestampMs
        ) {
            return delegate.shouldAllowBootstrapStableAnchorUpdate(signalSnapshot, nowTimestampMs);
        }

        private RxLearningAuthority currentAuthority(long nowTimestampMs) {
            return delegate.currentAuthority(nowTimestampMs);
        }

        private RxLearningAuthority decodedCharacterAuthority(
                CwSignalSnapshot signalSnapshot,
                long nowTimestampMs
        ) {
            return delegate.decodedCharacterAuthority(signalSnapshot, nowTimestampMs);
        }

    }

    private static final class LiveLikeOnsetAdmissionGate {
        private static final long PENDING_ONSET_RELEASE_MS = 28L;
        private static final long DROP_SHORT_TONE_MAX_MS = 26L;
        private static final double RELEASE_LOCKED_RATIO_MIN = 0.52d;
        private static final double RELEASE_NEAR_TARGET_RATIO_MIN = 0.60d;
        private static final double RELEASE_TONE_DOMINANCE_MIN = 0.34d;
        private static final double RELEASE_ISOLATION_MIN = 0.40d;

        private CwToneEvent pendingToneOnEvent;

        private List<CwToneEvent> process(
                CwToneEvent toneEvent,
                CwSignalSnapshot signalSnapshot,
                LiveLikeFrontEndAuthorityGate frontEndAuthorityGate,
                long nowTimestampMs
        ) {
            ArrayList<CwToneEvent> admittedEvents = new ArrayList<>(2);
            releasePendingIfReady(admittedEvents, signalSnapshot, frontEndAuthorityGate, nowTimestampMs);
            if (toneEvent == null) {
                return admittedEvents;
            }
            if (pendingToneOnEvent != null) {
                if (toneEvent.type() == CwToneEvent.Type.TONE_OFF) {
                    if (shouldDropPendingRun(toneEvent, signalSnapshot, frontEndAuthorityGate, nowTimestampMs)) {
                        pendingToneOnEvent = null;
                        return admittedEvents;
                    }
                    admittedEvents.add(pendingToneOnEvent);
                    pendingToneOnEvent = null;
                    admittedEvents.add(toneEvent);
                    return admittedEvents;
                }
                admittedEvents.add(toneEvent);
                return admittedEvents;
            }
            if (toneEvent.type() != CwToneEvent.Type.TONE_ON) {
                admittedEvents.add(toneEvent);
                return admittedEvents;
            }
            if (shouldHoldOnset(signalSnapshot, frontEndAuthorityGate, nowTimestampMs)) {
                pendingToneOnEvent = toneEvent;
                return admittedEvents;
            }
            admittedEvents.add(toneEvent);
            return admittedEvents;
        }

        private List<CwToneEvent> flush(
                CwSignalSnapshot signalSnapshot,
                LiveLikeFrontEndAuthorityGate frontEndAuthorityGate,
                long nowTimestampMs
        ) {
            ArrayList<CwToneEvent> admittedEvents = new ArrayList<>(1);
            releasePendingIfReady(admittedEvents, signalSnapshot, frontEndAuthorityGate, nowTimestampMs);
            return admittedEvents;
        }

        private void releasePendingIfReady(
                List<CwToneEvent> admittedEvents,
                CwSignalSnapshot signalSnapshot,
                LiveLikeFrontEndAuthorityGate frontEndAuthorityGate,
                long nowTimestampMs
        ) {
            if (pendingToneOnEvent == null) {
                return;
            }
            if (!shouldReleasePending(signalSnapshot, frontEndAuthorityGate, nowTimestampMs)) {
                return;
            }
            admittedEvents.add(pendingToneOnEvent);
            pendingToneOnEvent = null;
        }

        private boolean shouldHoldOnset(
                CwSignalSnapshot signalSnapshot,
                LiveLikeFrontEndAuthorityGate frontEndAuthorityGate,
                long nowTimestampMs
        ) {
            if (frontEndAuthorityGate == null) {
                return false;
            }
            RxLearningAuthority authority = frontEndAuthorityGate.currentAuthority(nowTimestampMs);
            if (authority == RxLearningAuthority.BLOCKED) {
                return true;
            }
            return authority == RxLearningAuthority.DECODE_ONLY
                    && !hasModerateReleaseSupport(signalSnapshot);
        }

        private boolean shouldReleasePending(
                CwSignalSnapshot signalSnapshot,
                LiveLikeFrontEndAuthorityGate frontEndAuthorityGate,
                long nowTimestampMs
        ) {
            if (pendingToneOnEvent == null) {
                return false;
            }
            if (frontEndAuthorityGate == null) {
                return true;
            }
            RxLearningAuthority authority = frontEndAuthorityGate.currentAuthority(nowTimestampMs);
            if (authority == RxLearningAuthority.DECODE_AND_LEARN) {
                return true;
            }
            long pendingAgeMs = Math.max(0L, nowTimestampMs - pendingToneOnEvent.timestampMs());
            return pendingAgeMs >= PENDING_ONSET_RELEASE_MS
                    && authority != RxLearningAuthority.BLOCKED
                    && hasModerateReleaseSupport(signalSnapshot);
        }

        private boolean shouldDropPendingRun(
                CwToneEvent toneOffEvent,
                CwSignalSnapshot signalSnapshot,
                LiveLikeFrontEndAuthorityGate frontEndAuthorityGate,
                long nowTimestampMs
        ) {
            if (pendingToneOnEvent == null) {
                return false;
            }
            if (shouldReleasePending(signalSnapshot, frontEndAuthorityGate, nowTimestampMs)) {
                return false;
            }
            long toneDurationMs = Math.max(0L, toneOffEvent.toneDurationMs());
            return toneDurationMs <= DROP_SHORT_TONE_MAX_MS
                    && !hasModerateReleaseSupport(signalSnapshot);
        }

        private boolean hasModerateReleaseSupport(CwSignalSnapshot signalSnapshot) {
            return signalSnapshot != null
                    && signalSnapshot.targetToneLocked()
                    && signalSnapshot.recentLockedFrameRatio() >= RELEASE_LOCKED_RATIO_MIN
                    && signalSnapshot.recentNearTargetLockedFrameRatio() >= RELEASE_NEAR_TARGET_RATIO_MIN
                    && signalSnapshot.toneDominanceRatio() >= RELEASE_TONE_DOMINANCE_MIN
                    && signalSnapshot.narrowbandIsolationRatio() >= RELEASE_ISOLATION_MIN;
        }
    }

    private static final class LiveLikeReleasePressureHoldGate {
        private static final double HOLD_LOCK_RATIO_MIN = 0.44d;
        private static final double HOLD_NEAR_TARGET_RATIO_MIN = 0.62d;
        private static final double HOLD_TONE_DOMINANCE_MIN = 0.20d;
        private static final double HOLD_ISOLATION_MIN = 0.18d;
        private static final double HOLD_OFF_RATIO_MIN = 0.28d;
        private static final double HIGH_PRESSURE_OFF_RATIO_MIN = 0.38d;
        private static final double HOLD_MAX_TONE_RATIO = 1.90d;
        private static final long HOLD_MAX_TONE_ABSOLUTE_MS = 120L;
        private static final double BRIDGE_GAP_MAX_DOT_RATIO = 0.42d;
        private static final long BRIDGE_GAP_MAX_ABSOLUTE_MS = 24L;
        private static final double HIGH_PRESSURE_BRIDGE_GAP_MAX_DOT_RATIO = 0.55d;
        private static final long HIGH_PRESSURE_BRIDGE_GAP_MAX_ABSOLUTE_MS = 32L;

        private CwToneEvent pendingToneOffEvent;
        private long pendingToneOffExpiresAtMs = -1L;
        private long pendingToneOffBridgeWindowMs = -1L;
        private long bridgedPrefixDurationMs = -1L;
        private long bridgedSplitTimestampMs = -1L;
        private double pendingPressureRatio;

        private List<CwToneEvent> process(
                CwToneEvent toneEvent,
                CwSignalSnapshot signalSnapshot,
                long referenceDotEstimateMs,
                long nowTimestampMs,
                double detectionLevel
        ) {
            ArrayList<CwToneEvent> admittedEvents = new ArrayList<>(2);
            releasePendingIfExpired(nowTimestampMs, admittedEvents);
            if (toneEvent == null) {
                return admittedEvents;
            }

            if (bridgeActive()) {
                if (toneEvent.type() == CwToneEvent.Type.TONE_OFF) {
                    admittedEvents.add(mergeBridgedToneOff(toneEvent));
                    clearBridge();
                    return admittedEvents;
                }
                clearBridge();
            }

            if (pendingToneOffEvent != null) {
                if (toneEvent.type() == CwToneEvent.Type.TONE_ON
                        && shouldBridgePendingGap(
                        toneEvent,
                        signalSnapshot,
                        referenceDotEstimateMs
                )) {
                    startBridgeFromPending();
                    return admittedEvents;
                }
                releasePending(admittedEvents);
            }

            if (shouldHoldToneOff(
                    toneEvent,
                    signalSnapshot,
                    referenceDotEstimateMs,
                    detectionLevel
            )) {
                pendingToneOffEvent = toneEvent;
                pendingPressureRatio = offPressureRatio(signalSnapshot, detectionLevel);
                pendingToneOffBridgeWindowMs = bridgeGapWindowMs(referenceDotEstimateMs, pendingPressureRatio);
                pendingToneOffExpiresAtMs = toneEvent.timestampMs() + pendingToneOffBridgeWindowMs;
                return admittedEvents;
            }

            admittedEvents.add(toneEvent);
            return admittedEvents;
        }

        private List<CwToneEvent> flush(
                CwSignalSnapshot signalSnapshot,
                long referenceDotEstimateMs,
                long nowTimestampMs
        ) {
            ArrayList<CwToneEvent> admittedEvents = new ArrayList<>(1);
            releasePendingIfExpired(nowTimestampMs, admittedEvents);
            if (pendingToneOffEvent != null
                    && referenceDotEstimateMs > 0L
                    && nowTimestampMs >= (pendingToneOffEvent.timestampMs()
                    + bridgeGapWindowMs(referenceDotEstimateMs, pendingPressureRatio))) {
                releasePending(admittedEvents);
            }
            return admittedEvents;
        }

        private void releasePendingIfExpired(long nowTimestampMs, List<CwToneEvent> admittedEvents) {
            if (pendingToneOffEvent == null || nowTimestampMs < pendingToneOffExpiresAtMs) {
                return;
            }
            releasePending(admittedEvents);
        }

        private void releasePending(List<CwToneEvent> admittedEvents) {
            if (pendingToneOffEvent == null) {
                return;
            }
            admittedEvents.add(pendingToneOffEvent);
            pendingToneOffEvent = null;
            pendingToneOffExpiresAtMs = -1L;
            pendingToneOffBridgeWindowMs = -1L;
            pendingPressureRatio = 0.0d;
        }

        private boolean shouldHoldToneOff(
                CwToneEvent toneEvent,
                CwSignalSnapshot signalSnapshot,
                long referenceDotEstimateMs,
                double detectionLevel
        ) {
            if (toneEvent == null
                    || toneEvent.type() != CwToneEvent.Type.TONE_OFF
                    || toneEvent.toneDurationMs() <= 0L
                    || referenceDotEstimateMs <= 0L
                    || signalSnapshot == null) {
                return false;
            }
            if (!hasHoldSupport(signalSnapshot)) {
                return false;
            }
            double pressureRatio = offPressureRatio(signalSnapshot, detectionLevel);
            if (pressureRatio < HOLD_OFF_RATIO_MIN) {
                return false;
            }
            if (toneEvent.toneDurationMs() > holdToneDurationWindowMs(referenceDotEstimateMs)) {
                return false;
            }
            return signalSnapshot.targetToneLocked()
                    || pressureRatio >= HIGH_PRESSURE_OFF_RATIO_MIN
                    || signalSnapshot.recentLockedFrameRatio() >= HOLD_LOCK_RATIO_MIN;
        }

        private boolean shouldBridgePendingGap(
                CwToneEvent toneOnEvent,
                CwSignalSnapshot signalSnapshot,
                long referenceDotEstimateMs
        ) {
            if (pendingToneOffEvent == null
                    || toneOnEvent == null
                    || toneOnEvent.type() != CwToneEvent.Type.TONE_ON
                    || referenceDotEstimateMs <= 0L
                    || signalSnapshot == null) {
                return false;
            }
            long splitGapMs = Math.max(0L, toneOnEvent.timestampMs() - pendingToneOffEvent.timestampMs());
            long bridgeWindowMs = pendingToneOffBridgeWindowMs > 0L
                    ? pendingToneOffBridgeWindowMs
                    : bridgeGapWindowMs(referenceDotEstimateMs, pendingPressureRatio);
            if (splitGapMs > bridgeWindowMs) {
                return false;
            }
            return signalSnapshot.targetToneLocked()
                    || signalSnapshot.recentNearTargetLockedFrameRatio() >= HOLD_NEAR_TARGET_RATIO_MIN
                    || pendingPressureRatio >= HIGH_PRESSURE_OFF_RATIO_MIN;
        }

        private boolean hasHoldSupport(CwSignalSnapshot signalSnapshot) {
            if (signalSnapshot == null) {
                return false;
            }
            boolean trackingSupport = signalSnapshot.targetToneLocked()
                    || signalSnapshot.recentNearTargetLockedFrameRatio() >= HOLD_NEAR_TARGET_RATIO_MIN
                    || signalSnapshot.recentLockedFrameRatio() >= HOLD_LOCK_RATIO_MIN;
            boolean energySupport = signalSnapshot.toneDominanceRatio() >= HOLD_TONE_DOMINANCE_MIN
                    && signalSnapshot.narrowbandIsolationRatio() >= HOLD_ISOLATION_MIN;
            return trackingSupport && energySupport;
        }

        private double offPressureRatio(
                CwSignalSnapshot signalSnapshot,
                double detectionLevel
        ) {
            if (signalSnapshot == null) {
                return 0.0d;
            }
            return detectionLevel / Math.max(1.0d, signalSnapshot.releaseThreshold());
        }

        private long holdToneDurationWindowMs(long referenceDotEstimateMs) {
            return Math.max(
                    1L,
                    Math.min(
                            HOLD_MAX_TONE_ABSOLUTE_MS,
                            Math.round(referenceDotEstimateMs * HOLD_MAX_TONE_RATIO)
                    )
            );
        }

        private long bridgeGapWindowMs(long referenceDotEstimateMs, double pressureRatio) {
            boolean highPressure = pressureRatio >= HIGH_PRESSURE_OFF_RATIO_MIN;
            double maxDotRatio = highPressure
                    ? HIGH_PRESSURE_BRIDGE_GAP_MAX_DOT_RATIO
                    : BRIDGE_GAP_MAX_DOT_RATIO;
            long absoluteMaxMs = highPressure
                    ? HIGH_PRESSURE_BRIDGE_GAP_MAX_ABSOLUTE_MS
                    : BRIDGE_GAP_MAX_ABSOLUTE_MS;
            return Math.max(
                    1L,
                    Math.min(
                            absoluteMaxMs,
                            Math.round(referenceDotEstimateMs * maxDotRatio)
                    )
            );
        }

        private void startBridgeFromPending() {
            if (pendingToneOffEvent == null) {
                return;
            }
            bridgedPrefixDurationMs = Math.max(0L, pendingToneOffEvent.toneDurationMs());
            bridgedSplitTimestampMs = pendingToneOffEvent.timestampMs();
            pendingToneOffEvent = null;
            pendingToneOffExpiresAtMs = -1L;
            pendingToneOffBridgeWindowMs = -1L;
            pendingPressureRatio = 0.0d;
        }

        private CwToneEvent mergeBridgedToneOff(CwToneEvent toneEvent) {
            long bridgedDurationMs = Math.max(
                    toneEvent.toneDurationMs(),
                    bridgedPrefixDurationMs + Math.max(0L, toneEvent.timestampMs() - bridgedSplitTimestampMs)
            );
            return new CwToneEvent(
                    CwToneEvent.Type.TONE_OFF,
                    toneEvent.timestampMs(),
                    toneEvent.peakAmplitude(),
                    toneEvent.rmsAmplitude(),
                    bridgedDurationMs
            );
        }

        private boolean bridgeActive() {
            return bridgedPrefixDurationMs >= 0L && bridgedSplitTimestampMs >= 0L;
        }

        private void clearBridge() {
            bridgedPrefixDurationMs = -1L;
            bridgedSplitTimestampMs = -1L;
        }
    }

    private static final class LiveLikeShortGapMergeGate {
        private static final double HOLD_MAX_TONE_RATIO = 4.20d;
        private static final long HOLD_MAX_TONE_ABSOLUTE_MS = 240L;
        private static final double BRIDGE_GAP_MAX_DOT_RATIO = 0.60d;
        private static final long BRIDGE_GAP_MAX_ABSOLUTE_MS = 32L;
        private static final double MODERATE_LOCK_RATIO_MIN = 0.30d;
        private static final double MODERATE_NEAR_TARGET_RATIO_MIN = 0.60d;
        private static final double MODERATE_TONE_DOMINANCE_MIN = 0.10d;
        private static final double MODERATE_ISOLATION_MIN = 0.10d;
        private static final double STRONG_LOCK_RATIO_MIN = 0.78d;
        private static final double STRONG_NEAR_TARGET_RATIO_MIN = 0.84d;
        private static final double STRONG_TONE_DOMINANCE_MIN = 0.58d;
        private static final double STRONG_ISOLATION_MIN = 0.46d;

        private CwToneEvent pendingToneOffEvent;
        private long pendingToneOffExpiresAtMs = -1L;
        private long pendingToneOffBridgeWindowMs = -1L;
        private long bridgedPrefixDurationMs = -1L;
        private long bridgedSplitTimestampMs = -1L;

        private List<CwToneEvent> process(
                CwToneEvent toneEvent,
                CwSignalSnapshot signalSnapshot,
                LiveLikeFrontEndAuthorityGate frontEndAuthorityGate,
                long referenceDotEstimateMs,
                long nowTimestampMs
        ) {
            ArrayList<CwToneEvent> admittedEvents = new ArrayList<>(2);
            releasePendingIfExpired(nowTimestampMs, admittedEvents);
            if (toneEvent == null) {
                return admittedEvents;
            }

            if (bridgeActive()) {
                if (toneEvent.type() == CwToneEvent.Type.TONE_OFF) {
                    admittedEvents.add(mergeBridgedToneOff(toneEvent));
                    clearBridge();
                    return admittedEvents;
                }
                clearBridge();
            }

            if (pendingToneOffEvent != null) {
                if (toneEvent.type() == CwToneEvent.Type.TONE_ON
                        && shouldBridgePendingGap(
                        toneEvent,
                        signalSnapshot,
                        frontEndAuthorityGate,
                        referenceDotEstimateMs,
                        nowTimestampMs
                )) {
                    startBridgeFromPending();
                    return admittedEvents;
                }
                releasePending(admittedEvents);
            }

            if (shouldHoldToneOff(
                    toneEvent,
                    signalSnapshot,
                    frontEndAuthorityGate,
                    referenceDotEstimateMs,
                    nowTimestampMs
            )) {
                pendingToneOffEvent = toneEvent;
                pendingToneOffBridgeWindowMs = bridgeGapWindowMs(referenceDotEstimateMs);
                pendingToneOffExpiresAtMs = toneEvent.timestampMs() + pendingToneOffBridgeWindowMs;
                return admittedEvents;
            }

            admittedEvents.add(toneEvent);
            return admittedEvents;
        }

        private List<CwToneEvent> flush(
                CwSignalSnapshot signalSnapshot,
                LiveLikeFrontEndAuthorityGate frontEndAuthorityGate,
                long referenceDotEstimateMs,
                long nowTimestampMs
        ) {
            ArrayList<CwToneEvent> admittedEvents = new ArrayList<>(1);
            releasePendingIfExpired(nowTimestampMs, admittedEvents);
            if (pendingToneOffEvent != null
                    && referenceDotEstimateMs > 0L
                    && nowTimestampMs >= (pendingToneOffEvent.timestampMs() + bridgeGapWindowMs(referenceDotEstimateMs))
                    && !shouldHoldToneOff(
                    pendingToneOffEvent,
                    signalSnapshot,
                    frontEndAuthorityGate,
                    referenceDotEstimateMs,
                    nowTimestampMs
            )) {
                releasePending(admittedEvents);
            }
            return admittedEvents;
        }

        private void releasePendingIfExpired(long nowTimestampMs, List<CwToneEvent> admittedEvents) {
            if (pendingToneOffEvent == null || nowTimestampMs < pendingToneOffExpiresAtMs) {
                return;
            }
            releasePending(admittedEvents);
        }

        private void releasePending(List<CwToneEvent> admittedEvents) {
            if (pendingToneOffEvent == null) {
                return;
            }
            admittedEvents.add(pendingToneOffEvent);
            pendingToneOffEvent = null;
            pendingToneOffExpiresAtMs = -1L;
            pendingToneOffBridgeWindowMs = -1L;
        }

        private boolean shouldHoldToneOff(
                CwToneEvent toneEvent,
                CwSignalSnapshot signalSnapshot,
                LiveLikeFrontEndAuthorityGate frontEndAuthorityGate,
                long referenceDotEstimateMs,
                long nowTimestampMs
        ) {
            if (toneEvent == null
                    || toneEvent.type() != CwToneEvent.Type.TONE_OFF
                    || toneEvent.toneDurationMs() <= 0L
                    || referenceDotEstimateMs <= 0L
                    || hasStrongReleaseSupport(signalSnapshot)) {
                return false;
            }
            if (!hasModerateMergeSupport(signalSnapshot, frontEndAuthorityGate, nowTimestampMs)) {
                return false;
            }
            return toneEvent.toneDurationMs() <= holdToneDurationWindowMs(referenceDotEstimateMs);
        }

        private boolean shouldBridgePendingGap(
                CwToneEvent toneOnEvent,
                CwSignalSnapshot signalSnapshot,
                LiveLikeFrontEndAuthorityGate frontEndAuthorityGate,
                long referenceDotEstimateMs,
                long nowTimestampMs
        ) {
            if (pendingToneOffEvent == null
                    || toneOnEvent == null
                    || toneOnEvent.type() != CwToneEvent.Type.TONE_ON
                    || referenceDotEstimateMs <= 0L
                    || !hasModerateMergeSupport(signalSnapshot, frontEndAuthorityGate, nowTimestampMs)) {
                return false;
            }
            long splitGapMs = Math.max(0L, toneOnEvent.timestampMs() - pendingToneOffEvent.timestampMs());
            long bridgeWindowMs = pendingToneOffBridgeWindowMs > 0L
                    ? pendingToneOffBridgeWindowMs
                    : bridgeGapWindowMs(referenceDotEstimateMs);
            return splitGapMs <= bridgeWindowMs;
        }

        private boolean hasModerateMergeSupport(
                CwSignalSnapshot signalSnapshot,
                LiveLikeFrontEndAuthorityGate frontEndAuthorityGate,
                long nowTimestampMs
        ) {
            if (signalSnapshot == null) {
                return false;
            }
            boolean trackingSupport = signalSnapshot.recentNearTargetLockedFrameRatio()
                    >= MODERATE_NEAR_TARGET_RATIO_MIN
                    || signalSnapshot.recentLockedFrameRatio() >= MODERATE_LOCK_RATIO_MIN
                    || signalSnapshot.targetToneLocked();
            boolean energySupport = signalSnapshot.toneDominanceRatio() >= MODERATE_TONE_DOMINANCE_MIN
                    && signalSnapshot.narrowbandIsolationRatio() >= MODERATE_ISOLATION_MIN;
            if (!trackingSupport || !energySupport) {
                return false;
            }
            if (frontEndAuthorityGate == null) {
                return true;
            }
            return frontEndAuthorityGate.currentAuthority(nowTimestampMs) != RxLearningAuthority.BLOCKED
                    || signalSnapshot.targetToneLocked();
        }

        private boolean hasStrongReleaseSupport(CwSignalSnapshot signalSnapshot) {
            return signalSnapshot != null
                    && signalSnapshot.targetToneLocked()
                    && signalSnapshot.recentLockedFrameRatio() >= STRONG_LOCK_RATIO_MIN
                    && signalSnapshot.recentNearTargetLockedFrameRatio() >= STRONG_NEAR_TARGET_RATIO_MIN
                    && signalSnapshot.toneDominanceRatio() >= STRONG_TONE_DOMINANCE_MIN
                    && signalSnapshot.narrowbandIsolationRatio() >= STRONG_ISOLATION_MIN;
        }

        private long holdToneDurationWindowMs(long referenceDotEstimateMs) {
            return Math.max(
                    1L,
                    Math.min(
                            HOLD_MAX_TONE_ABSOLUTE_MS,
                            Math.round(referenceDotEstimateMs * HOLD_MAX_TONE_RATIO)
                    )
            );
        }

        private long bridgeGapWindowMs(long referenceDotEstimateMs) {
            return Math.max(
                    1L,
                    Math.min(
                            BRIDGE_GAP_MAX_ABSOLUTE_MS,
                            Math.round(referenceDotEstimateMs * BRIDGE_GAP_MAX_DOT_RATIO)
                    )
            );
        }

        private void startBridgeFromPending() {
            if (pendingToneOffEvent == null) {
                return;
            }
            bridgedPrefixDurationMs = Math.max(0L, pendingToneOffEvent.toneDurationMs());
            bridgedSplitTimestampMs = pendingToneOffEvent.timestampMs();
            pendingToneOffEvent = null;
            pendingToneOffExpiresAtMs = -1L;
            pendingToneOffBridgeWindowMs = -1L;
        }

        private CwToneEvent mergeBridgedToneOff(CwToneEvent toneEvent) {
            long bridgedDurationMs = Math.max(
                    toneEvent.toneDurationMs(),
                    bridgedPrefixDurationMs + Math.max(0L, toneEvent.timestampMs() - bridgedSplitTimestampMs)
            );
            return new CwToneEvent(
                    CwToneEvent.Type.TONE_OFF,
                    toneEvent.timestampMs(),
                    toneEvent.peakAmplitude(),
                    toneEvent.rmsAmplitude(),
                    bridgedDurationMs
            );
        }

        private boolean bridgeActive() {
            return bridgedPrefixDurationMs >= 0L && bridgedSplitTimestampMs >= 0L;
        }

        private void clearBridge() {
            bridgedPrefixDurationMs = -1L;
            bridgedSplitTimestampMs = -1L;
        }
    }

    static TrailingWindowRedecodeResult redecodeTrailingWords(
            OfflineDetailedProbeResult detailedProbeResult,
            int trailingWordCount
    ) {
        if (detailedProbeResult == null) {
            throw new IllegalArgumentException("detailedProbeResult == null");
        }
        RxTrailingWindowRepair.RedecodeResult sharedResult =
                RxTrailingWindowRepair.redecodeTrailingWords(
                        detailedProbeResult.toneEvents(),
                        detailedProbeResult.decodeEvents(),
                        detailedProbeResult.flushTimestampMs(),
                        trailingWordCount
                );
        return new TrailingWindowRedecodeResult(
                sharedResult.trailingWordCount(),
                sharedResult.windowStartTimestampMs(),
                sharedResult.decodedText(),
                sharedResult.timingSnapshot(),
                sharedResult.decoderSnapshot(),
                sharedResult.interpreterSnapshot(),
                sharedResult.timingEvents(),
                sharedResult.decodeEvents()
        );
    }

    private static void drainToneEvents(
            List<CwToneEvent> toneEvents,
            CwHybridTimingModel timingModel,
            CwDecoder decoder,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            List<CwTimingEvent> capturedTimingEvents,
            List<CwDecodeEvent> capturedDecodeEvents
    ) {
        RxTimingDecodeRunner timingDecodeRunner = new RxTimingDecodeRunner(decoder);
        RxToneTimingRunner toneTimingRunner = new RxToneTimingRunner(timingDecodeRunner);
        toneTimingRunner.dispatchToneEvents(
                toneEvents,
                timingModel::process,
                (toneEvent, timingEvents) -> capturedTimingEvents.addAll(timingEvents),
                null,
                capturedDecodeEventConsumer(interpreter, qsoStateMachine, capturedDecodeEvents)
        );
    }

    private static long runSimpleReplaySession(
            List<AudioFrame> frames,
            CwSignalProcessor signalProcessor,
            CwHybridTimingModel timingModel,
            CwDecoder decoder,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            List<CwToneEvent> capturedToneEvents,
            List<CwTimingEvent> capturedTimingEvents,
            List<CwDecodeEvent> capturedDecodeEvents,
            List<FrameSignalTrace> frameSignalTraces,
            List<TimingStateTrace> timingStateTraces
    ) {
        RxFrameSignalRunner frameSignalRunner = new RxFrameSignalRunner(null, signalProcessor);
        RxTimingDecodeRunner timingDecodeRunner = new RxTimingDecodeRunner(decoder);
        RxReplaySessionRunner replaySessionRunner = new RxReplaySessionRunner(
                frameSignalRunner,
                new RxToneTimingRunner(timingDecodeRunner),
                timingDecodeRunner
        );
        RxReplaySessionRunner.Result replayResult = replaySessionRunner.replayFrames(
                frames,
                timingModel::process,
                (frame, frameResult) -> {
                    capturedToneEvents.addAll(frameResult.toneEvents());
                    frameSignalTraces.add(buildFrameSignalTrace(frame, signalProcessor));
                    timingStateTraces.add(new TimingStateTrace(
                            frame.capturedAtMs(),
                            timingModel.debugSnapshot(),
                            timingModel.snapshot(),
                            timingModel.rawSnapshot(),
                            timingModel.debugStrategySummary()
                    ));
                },
                (toneEvent, timingEvents) -> capturedTimingEvents.addAll(timingEvents),
                timingModel::flushPendingGap,
                (flushTimestampMs, timingEvents) -> capturedTimingEvents.addAll(timingEvents),
                null,
                capturedDecodeEventConsumer(interpreter, qsoStateMachine, capturedDecodeEvents)
        );
        return replayResult.flushTimestampMs();
    }

    private static void drainTimingEvents(
            List<CwTimingEvent> timingEvents,
            CwDecoder decoder,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            List<CwDecodeEvent> capturedDecodeEvents
    ) {
        RxTimingDecodeRunner timingDecodeRunner = new RxTimingDecodeRunner(decoder);
        timingDecodeRunner.dispatchTimingEvents(
                timingEvents,
                null,
                capturedDecodeEventConsumer(interpreter, qsoStateMachine, capturedDecodeEvents)
        );
    }

    private static void flushPendingDecodeTimeline(
            long flushTimestampMs,
            CwHybridTimingModel timingModel,
            CwDecoder decoder,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            List<CwTimingEvent> capturedTimingEvents,
            List<CwDecodeEvent> capturedDecodeEvents
    ) {
        if (timingModel == null || decoder == null) {
            return;
        }
        List<CwTimingEvent> timingEvents = timingModel.flushPendingGap(flushTimestampMs);
        if (timingEvents != null && capturedTimingEvents != null) {
            capturedTimingEvents.addAll(timingEvents);
        }
        RxTimingDecodeRunner timingDecodeRunner = new RxTimingDecodeRunner(decoder);
        RxTimingDecodeRunner.DecodeEventConsumer decodeEventConsumer =
                capturedDecodeEventConsumer(interpreter, qsoStateMachine, capturedDecodeEvents);
        timingDecodeRunner.dispatchTimingEvents(
                timingEvents,
                null,
                decodeEventConsumer
        );
        timingDecodeRunner.flushPendingCharacter(
                flushTimestampMs,
                decodeEventConsumer
        );
    }

    private static void drainDecodeEvents(
            List<CwDecodeEvent> decodeEvents,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            List<CwDecodeEvent> capturedDecodeEvents
    ) {
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            interpreter.process(decodeEvent);
            qsoStateMachine.process(interpreter.snapshot(), decodeEvent.timestampMs());
            capturedDecodeEvents.add(decodeEvent);
        }
    }

    private static RxTimingDecodeRunner.DecodeEventConsumer capturedDecodeEventConsumer(
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            List<CwDecodeEvent> capturedDecodeEvents
    ) {
        return decodeEvent -> {
            if (decodeEvent == null) {
                return;
            }
            interpreter.process(decodeEvent);
            qsoStateMachine.process(interpreter.snapshot(), decodeEvent.timestampMs());
            capturedDecodeEvents.add(decodeEvent);
        };
    }

    private static RxTimingDecodeRunner.DecodeEventConsumer liveLikeDecodeEventConsumer(
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot,
            CwSignalProcessor signalProcessor,
            CwHybridTimingModel timingModel,
            CwInterpreter interpreter,
            QsoStateMachine qsoStateMachine,
            LiveRxWpmGuard wpmGuard,
            RxTurnController turnController,
            TimingAnchorController timingAnchorController,
            RxRawCommitGate rawCommitGate,
            CwFrontEndLearningGate frontEndLearningGate,
            LiveLikeFrontEndAuthorityGate frontEndAuthorityGate,
            List<CwDecodeEvent> capturedDecodeEvents
    ) {
        return decodeEvent -> processLiveLikeDecodeEvent(
                decodeEvent,
                inputHealthSnapshot,
                signalProcessor,
                timingModel,
                interpreter,
                qsoStateMachine,
                wpmGuard,
                turnController,
                timingAnchorController,
                rawCommitGate,
                frontEndLearningGate,
                frontEndAuthorityGate,
                capturedDecodeEvents
        );
    }

    private static OfflineDetailedProbeResult maybeRepairTrailingWindowWithFreshRedecode(
            OfflineDetailedProbeResult detailedProbeResult,
            CwInterpreter.RecoveryMode recoveryMode
    ) {
        if (detailedProbeResult == null
                || detailedProbeResult.decodeEvents() == null
                || detailedProbeResult.decodeEvents().isEmpty()
                || detailedProbeResult.toneEvents() == null
                || detailedProbeResult.toneEvents().isEmpty()
                || detailedProbeResult.probeResult() == null
                || detailedProbeResult.probeResult().interpreterSnapshot() == null) {
            return detailedProbeResult;
        }
        String baseRawText = sanitize(detailedProbeResult.probeResult().interpreterSnapshot().rawText());
        if (!baseRawText.contains("?")) {
            return detailedProbeResult;
        }

        RxTrailingWindowRepair.RepairResult repairResult =
                RxTrailingWindowRepair.repairTrailingWordsIfBeneficial(
                        detailedProbeResult.toneEvents(),
                        detailedProbeResult.decodeEvents(),
                        detailedProbeResult.flushTimestampMs(),
                        2
                );
        if (repairResult == null) {
            return detailedProbeResult;
        }

        List<CwDecodeEvent> repairedDecodeEvents = repairResult.repairedDecodeEvents();
        if (repairedDecodeEvents.isEmpty()) {
            return detailedProbeResult;
        }

        CwInterpreter repairedInterpreter = new CwInterpreter(recoveryMode);
        QsoStateMachine repairedQsoStateMachine = new QsoStateMachine();
        ArrayList<CwDecodeEvent> replayedFinalEvents = new ArrayList<>(repairedDecodeEvents.size());
        drainDecodeEvents(
                repairedDecodeEvents,
                repairedInterpreter,
                repairedQsoStateMachine,
                replayedFinalEvents
        );
        if (replayedFinalEvents.isEmpty()) {
            return detailedProbeResult;
        }

        CwDecodeEvent lastDecodeEvent = replayedFinalEvents.get(replayedFinalEvents.size() - 1);
        String repairedRawText = sanitize(repairedInterpreter.snapshot().rawText());
        CwDecoderSnapshot repairedDecoderSnapshot = new CwDecoderSnapshot(
                safeValue(lastDecodeEvent.currentSequence()),
                safeValue(lastDecodeEvent.outputText()),
                countDecodeEventsOfType(repairedDecodeEvents, CwDecodeEvent.Type.SYMBOL_APPENDED),
                countDecodeEventsOfType(repairedDecodeEvents, CwDecodeEvent.Type.CHARACTER_DECODED),
                lastDecodeEvent
        );
        OfflineProbeResult repairedProbeResult = new OfflineProbeResult(
                detailedProbeResult.probeResult().sourceLabel(),
                repairedRawText,
                detailedProbeResult.probeResult().signalSnapshot(),
                detailedProbeResult.probeResult().signalProcessorLeaderSummary(),
                detailedProbeResult.probeResult().timingSnapshot(),
                detailedProbeResult.probeResult().timingStrategySummary(),
                repairedDecoderSnapshot,
                repairedInterpreter.snapshot(),
                repairedQsoStateMachine.snapshot()
        );
        return new OfflineDetailedProbeResult(
                repairedProbeResult,
                detailedProbeResult.frames(),
                detailedProbeResult.toneEvents(),
                detailedProbeResult.timingEvents(),
                replayedFinalEvents,
                detailedProbeResult.rawDecodeEvents(),
                detailedProbeResult.stableAcceptedDecodeEvents(),
                detailedProbeResult.stableDecisionTraces(),
                detailedProbeResult.bootstrapBoundaryTimingEvents(),
                detailedProbeResult.bootstrapCadenceTimingEvents(),
                detailedProbeResult.bootstrapBoundaryDecisionTraces(),
                detailedProbeResult.bootstrapCadenceDecisionTraces(),
                detailedProbeResult.timingLearningDecisionTraces(),
                detailedProbeResult.timingEventAdaptationTraces(),
                detailedProbeResult.stableRejectCounts(),
                detailedProbeResult.bootstrapBoundaryRejectCounts(),
                detailedProbeResult.bootstrapCadenceRejectCounts(),
                detailedProbeResult.frameSignalTraces(),
                detailedProbeResult.timingStateTraces(),
                detailedProbeResult.rxToneModeDecisionTraces(),
                detailedProbeResult.turnTransitionTraces(),
                detailedProbeResult.flushTimestampMs()
        );
    }

    private static boolean shouldUseTrailingWindowRedecodeRepair(
            OfflineDetailedProbeResult detailedProbeResult,
            TrailingWindowRedecodeResult trailingWindowRedecodeResult
    ) {
        if (detailedProbeResult == null
                || trailingWindowRedecodeResult == null
                || trailingWindowRedecodeResult.windowStartTimestampMs() < 0L) {
            return false;
        }
        String baseTailText = trailingTextFromWindow(
                detailedProbeResult.decodeEvents(),
                trailingWindowRedecodeResult.windowStartTimestampMs()
        );
        String candidateTailText = sanitize(trailingWindowRedecodeResult.decodedText());
        if (baseTailText.isEmpty()
                || candidateTailText.isEmpty()
                || baseTailText.equals(candidateTailText)) {
            return false;
        }
        int baseUnknownCount = countCharacter(baseTailText, '?');
        int candidateUnknownCount = countCharacter(candidateTailText, '?');
        if (baseUnknownCount <= 0 || candidateUnknownCount >= baseUnknownCount) {
            return false;
        }
        if (countWordTokens(candidateTailText) < countWordTokens(baseTailText)) {
            return false;
        }
        return countAlphaNumeric(candidateTailText) >= countAlphaNumeric(baseTailText);
    }

    private static List<CwDecodeEvent> buildRepairedDecodeEvents(
            List<CwDecodeEvent> baseDecodeEvents,
            TrailingWindowRedecodeResult trailingWindowRedecodeResult
    ) {
        if (baseDecodeEvents == null || baseDecodeEvents.isEmpty() || trailingWindowRedecodeResult == null) {
            return java.util.Collections.emptyList();
        }
        long windowStartTimestampMs = trailingWindowRedecodeResult.windowStartTimestampMs();
        ArrayList<CwDecodeEvent> repairedDecodeEvents = new ArrayList<>();
        String prefixText = "";
        for (CwDecodeEvent decodeEvent : baseDecodeEvents) {
            if (decodeEvent == null) {
                continue;
            }
            if (decodeEvent.timestampMs() >= windowStartTimestampMs) {
                break;
            }
            repairedDecodeEvents.add(decodeEvent);
            prefixText = safeValue(decodeEvent.outputText());
        }
        for (CwDecodeEvent decodeEvent : trailingWindowRedecodeResult.decodeEvents()) {
            if (decodeEvent == null) {
                continue;
            }
            repairedDecodeEvents.add(new CwDecodeEvent(
                    decodeEvent.type(),
                    decodeEvent.timestampMs(),
                    decodeEvent.currentSequence(),
                    stitchOutputText(prefixText, decodeEvent.outputText()),
                    decodeEvent.emittedValue(),
                    decodeEvent.sourceSequence(),
                    decodeEvent.unknownCharacter()
            ));
        }
        return repairedDecodeEvents;
    }

    private static String stitchOutputText(String prefixText, String trailingOutputText) {
        String safePrefixText = safeValue(prefixText).stripTrailing();
        String safeTrailingOutputText = safeValue(trailingOutputText).stripLeading();
        if (safePrefixText.isEmpty()) {
            return safeTrailingOutputText;
        }
        if (safeTrailingOutputText.isEmpty()) {
            return safePrefixText;
        }
        if (safePrefixText.endsWith(" ") || safeTrailingOutputText.startsWith(" ")) {
            return safePrefixText + safeTrailingOutputText;
        }
        return safePrefixText + " " + safeTrailingOutputText;
    }

    private static String trailingTextFromWindow(
            List<CwDecodeEvent> decodeEvents,
            long windowStartTimestampMs
    ) {
        if (decodeEvents == null || decodeEvents.isEmpty()) {
            return "";
        }
        String prefixText = "";
        String finalText = "";
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            if (decodeEvent == null) {
                continue;
            }
            String outputText = safeValue(decodeEvent.outputText());
            if (decodeEvent.timestampMs() < windowStartTimestampMs) {
                prefixText = outputText;
                continue;
            }
            finalText = outputText;
        }
        String safePrefixText = sanitize(prefixText);
        String safeFinalText = sanitize(finalText);
        if (safeFinalText.isEmpty()) {
            return "";
        }
        if (safePrefixText.isEmpty()) {
            return safeFinalText;
        }
        if (safeFinalText.startsWith(safePrefixText)) {
            return safeFinalText.substring(safePrefixText.length()).trim();
        }
        return safeFinalText;
    }

    private static int countDecodeEventsOfType(
            List<CwDecodeEvent> decodeEvents,
            CwDecodeEvent.Type type
    ) {
        if (decodeEvents == null || type == null) {
            return 0;
        }
        int count = 0;
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            if (decodeEvent != null && decodeEvent.type() == type) {
                count += 1;
            }
        }
        return count;
    }

    private static int countCharacter(String text, char target) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == target) {
                count += 1;
            }
        }
        return count;
    }

    private static int countWordTokens(String text) {
        if (text == null) {
            return 0;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }
        return trimmed.split("\\s+").length;
    }

    private static int countAlphaNumeric(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int index = 0; index < text.length(); index++) {
            if (Character.isLetterOrDigit(text.charAt(index))) {
                count += 1;
            }
        }
        return count;
    }

    private static String safeValue(String value) {
        return value == null ? "" : value;
    }

    private static long trailingWordWindowStartTimestampMs(
            List<CwDecodeEvent> decodeEvents,
            int trailingWordCount
    ) {
        if (decodeEvents == null || decodeEvents.isEmpty()) {
            return 0L;
        }
        int endIndex = decodeEvents.size() - 1;
        while (endIndex >= 0 && decodeEvents.get(endIndex).type() == CwDecodeEvent.Type.WORD_BREAK) {
            endIndex -= 1;
        }
        if (endIndex < 0) {
            return decodeEvents.get(0).timestampMs();
        }

        int remainingWordBreaks = trailingWordCount;
        for (int index = endIndex; index >= 0; index--) {
            CwDecodeEvent decodeEvent = decodeEvents.get(index);
            if (decodeEvent.type() != CwDecodeEvent.Type.WORD_BREAK) {
                continue;
            }
            remainingWordBreaks -= 1;
            if (remainingWordBreaks == 0) {
                return decodeEvent.timestampMs();
            }
        }
        return decodeEvents.get(0).timestampMs();
    }

    static final class WaveDataProbe {
        private final int sampleRateHz;
        private final short[] samples;

        private WaveDataProbe(int sampleRateHz, short[] samples) {
            this.sampleRateHz = sampleRateHz;
            this.samples = samples;
        }

        int sampleRateHz() {
            return sampleRateHz;
        }

        short[] samples() {
            return samples;
        }
    }

    static final class OfflineProbeResult {
        private final String sourceLabel;
        private final String decodedText;
        private final CwSignalSnapshot signalSnapshot;
        private final String signalProcessorLeaderSummary;
        private final CwTimingSnapshot timingSnapshot;
        private final String timingStrategySummary;
        private final CwDecoderSnapshot decoderSnapshot;
        private final CwInterpreterSnapshot interpreterSnapshot;
        private final QsoDraftSnapshot qsoDraftSnapshot;

        private OfflineProbeResult(
                String sourceLabel,
                String decodedText,
                CwSignalSnapshot signalSnapshot,
                String signalProcessorLeaderSummary,
                CwTimingSnapshot timingSnapshot,
                String timingStrategySummary,
                CwDecoderSnapshot decoderSnapshot,
                CwInterpreterSnapshot interpreterSnapshot
        ) {
            this(
                    sourceLabel,
                    decodedText,
                    signalSnapshot,
                    signalProcessorLeaderSummary,
                    timingSnapshot,
                    timingStrategySummary,
                    decoderSnapshot,
                    interpreterSnapshot,
                    null
            );
        }

        private OfflineProbeResult(
                String sourceLabel,
                String decodedText,
                CwSignalSnapshot signalSnapshot,
                String signalProcessorLeaderSummary,
                CwTimingSnapshot timingSnapshot,
                String timingStrategySummary,
                CwDecoderSnapshot decoderSnapshot,
                CwInterpreterSnapshot interpreterSnapshot,
                QsoDraftSnapshot qsoDraftSnapshot
        ) {
            this.sourceLabel = sourceLabel;
            this.decodedText = decodedText;
            this.signalSnapshot = signalSnapshot;
            this.signalProcessorLeaderSummary = signalProcessorLeaderSummary;
            this.timingSnapshot = timingSnapshot;
            this.timingStrategySummary = timingStrategySummary;
            this.decoderSnapshot = decoderSnapshot;
            this.interpreterSnapshot = interpreterSnapshot;
            this.qsoDraftSnapshot = qsoDraftSnapshot;
        }

        String sourceLabel() {
            return sourceLabel;
        }

        String decodedText() {
            return decodedText;
        }

        CwSignalSnapshot signalSnapshot() {
            return signalSnapshot;
        }

        String signalProcessorLeaderSummary() {
            return signalProcessorLeaderSummary;
        }

        CwTimingSnapshot timingSnapshot() {
            return timingSnapshot;
        }

        String timingStrategySummary() {
            return timingStrategySummary;
        }

        CwDecoderSnapshot decoderSnapshot() {
            return decoderSnapshot;
        }

        CwInterpreterSnapshot interpreterSnapshot() {
            return interpreterSnapshot;
        }

        QsoDraftSnapshot qsoDraftSnapshot() {
            return qsoDraftSnapshot;
        }
    }

    static final class OfflineDetailedProbeResult {
        private final OfflineProbeResult probeResult;
        private final List<AudioFrame> frames;
        private final List<CwToneEvent> toneEvents;
        private final List<CwTimingEvent> timingEvents;
        private final List<CwDecodeEvent> decodeEvents;
        private final List<CwDecodeEvent> rawDecodeEvents;
        private final List<CwDecodeEvent> stableAcceptedDecodeEvents;
        private final List<StableDecisionTrace> stableDecisionTraces;
        private final List<CwTimingEvent> bootstrapBoundaryTimingEvents;
        private final List<CwTimingEvent> bootstrapCadenceTimingEvents;
        private final List<BootstrapDecisionTrace> bootstrapBoundaryDecisionTraces;
        private final List<BootstrapDecisionTrace> bootstrapCadenceDecisionTraces;
        private final List<TimingLearningDecisionTrace> timingLearningDecisionTraces;
        private final List<TimingEventAdaptationTrace> timingEventAdaptationTraces;
        private final Map<String, Integer> stableRejectCounts;
        private final Map<String, Integer> bootstrapBoundaryRejectCounts;
        private final Map<String, Integer> bootstrapCadenceRejectCounts;
        private final List<FrameSignalTrace> frameSignalTraces;
        private final List<TimingStateTrace> timingStateTraces;
        private final List<RxToneModeDecisionTrace> rxToneModeDecisionTraces;
        private final List<TurnTransitionTrace> turnTransitionTraces;
        private final long flushTimestampMs;

        private OfflineDetailedProbeResult(
                OfflineProbeResult probeResult,
                List<AudioFrame> frames,
                List<CwToneEvent> toneEvents,
                List<CwTimingEvent> timingEvents,
                List<CwDecodeEvent> decodeEvents,
                List<CwDecodeEvent> rawDecodeEvents,
                List<CwDecodeEvent> stableAcceptedDecodeEvents,
                List<StableDecisionTrace> stableDecisionTraces,
                List<CwTimingEvent> bootstrapBoundaryTimingEvents,
                List<CwTimingEvent> bootstrapCadenceTimingEvents,
                List<BootstrapDecisionTrace> bootstrapBoundaryDecisionTraces,
                List<BootstrapDecisionTrace> bootstrapCadenceDecisionTraces,
                List<TimingLearningDecisionTrace> timingLearningDecisionTraces,
                List<TimingEventAdaptationTrace> timingEventAdaptationTraces,
                Map<String, Integer> stableRejectCounts,
                Map<String, Integer> bootstrapBoundaryRejectCounts,
                Map<String, Integer> bootstrapCadenceRejectCounts,
                List<FrameSignalTrace> frameSignalTraces,
                List<TimingStateTrace> timingStateTraces,
                List<RxToneModeDecisionTrace> rxToneModeDecisionTraces,
                List<TurnTransitionTrace> turnTransitionTraces,
                long flushTimestampMs
        ) {
            this.probeResult = probeResult;
            this.frames = frames;
            this.toneEvents = toneEvents;
            this.timingEvents = timingEvents;
            this.decodeEvents = decodeEvents;
            this.rawDecodeEvents = rawDecodeEvents;
            this.stableAcceptedDecodeEvents = stableAcceptedDecodeEvents;
            this.stableDecisionTraces = stableDecisionTraces;
            this.bootstrapBoundaryTimingEvents = bootstrapBoundaryTimingEvents;
            this.bootstrapCadenceTimingEvents = bootstrapCadenceTimingEvents;
            this.bootstrapBoundaryDecisionTraces = bootstrapBoundaryDecisionTraces;
            this.bootstrapCadenceDecisionTraces = bootstrapCadenceDecisionTraces;
            this.timingLearningDecisionTraces = timingLearningDecisionTraces;
            this.timingEventAdaptationTraces = timingEventAdaptationTraces;
            this.stableRejectCounts = stableRejectCounts;
            this.bootstrapBoundaryRejectCounts = bootstrapBoundaryRejectCounts;
            this.bootstrapCadenceRejectCounts = bootstrapCadenceRejectCounts;
            this.frameSignalTraces = frameSignalTraces;
            this.timingStateTraces = timingStateTraces;
            this.rxToneModeDecisionTraces = rxToneModeDecisionTraces;
            this.turnTransitionTraces = turnTransitionTraces;
            this.flushTimestampMs = flushTimestampMs;
        }

        OfflineProbeResult probeResult() {
            return probeResult;
        }

        List<AudioFrame> frames() {
            return frames;
        }

        List<CwToneEvent> toneEvents() {
            return toneEvents;
        }

        List<CwTimingEvent> timingEvents() {
            return timingEvents;
        }

        List<CwDecodeEvent> decodeEvents() {
            return decodeEvents;
        }

        List<CwDecodeEvent> rawDecodeEvents() {
            return rawDecodeEvents;
        }

        List<CwDecodeEvent> stableAcceptedDecodeEvents() {
            return stableAcceptedDecodeEvents;
        }

        List<StableDecisionTrace> stableDecisionTraces() {
            return stableDecisionTraces;
        }

        List<CwTimingEvent> bootstrapBoundaryTimingEvents() {
            return bootstrapBoundaryTimingEvents;
        }

        List<CwTimingEvent> bootstrapCadenceTimingEvents() {
            return bootstrapCadenceTimingEvents;
        }

        List<BootstrapDecisionTrace> bootstrapBoundaryDecisionTraces() {
            return bootstrapBoundaryDecisionTraces;
        }

        List<BootstrapDecisionTrace> bootstrapCadenceDecisionTraces() {
            return bootstrapCadenceDecisionTraces;
        }

        List<TimingLearningDecisionTrace> timingLearningDecisionTraces() {
            return timingLearningDecisionTraces;
        }

        List<TimingEventAdaptationTrace> timingEventAdaptationTraces() {
            return timingEventAdaptationTraces;
        }

        Map<String, Integer> stableRejectCounts() {
            return stableRejectCounts;
        }

        Map<String, Integer> bootstrapBoundaryRejectCounts() {
            return bootstrapBoundaryRejectCounts;
        }

        Map<String, Integer> bootstrapCadenceRejectCounts() {
            return bootstrapCadenceRejectCounts;
        }

        List<FrameSignalTrace> frameSignalTraces() {
            return frameSignalTraces;
        }

        List<TimingStateTrace> timingStateTraces() {
            return timingStateTraces;
        }

        List<RxToneModeDecisionTrace> rxToneModeDecisionTraces() {
            return rxToneModeDecisionTraces;
        }

        List<TurnTransitionTrace> turnTransitionTraces() {
            return turnTransitionTraces;
        }

        long flushTimestampMs() {
            return flushTimestampMs;
        }
    }

    static final class StableDecisionTrace {
        private final long timestampMs;
        private final String emittedValue;
        private final String sourceSequence;
        private final boolean unknownCharacter;
        private final String decision;
        private final String verifiedDecision;
        private final boolean trustedTimingEstablished;
        private final boolean targetToneLocked;
        private final double recentLockedFrameRatio;
        private final double recentNearTargetLockedFrameRatio;
        private final double recentActiveUnlockedFrameRatio;
        private final double toneDominanceRatio;
        private final double narrowbandIsolationRatio;
        private final double recentHotFrameRatio;
        private final double recentClippingFrameRatio;
        private final double rawWpm;

        private StableDecisionTrace(
                long timestampMs,
                String emittedValue,
                String sourceSequence,
                boolean unknownCharacter,
                String decision,
                String verifiedDecision,
                boolean trustedTimingEstablished,
                boolean targetToneLocked,
                double recentLockedFrameRatio,
                double recentNearTargetLockedFrameRatio,
                double recentActiveUnlockedFrameRatio,
                double toneDominanceRatio,
                double narrowbandIsolationRatio,
                double recentHotFrameRatio,
                double recentClippingFrameRatio,
                double rawWpm
        ) {
            this.timestampMs = timestampMs;
            this.emittedValue = emittedValue;
            this.sourceSequence = sourceSequence;
            this.unknownCharacter = unknownCharacter;
            this.decision = decision;
            this.verifiedDecision = verifiedDecision;
            this.trustedTimingEstablished = trustedTimingEstablished;
            this.targetToneLocked = targetToneLocked;
            this.recentLockedFrameRatio = recentLockedFrameRatio;
            this.recentNearTargetLockedFrameRatio = recentNearTargetLockedFrameRatio;
            this.recentActiveUnlockedFrameRatio = recentActiveUnlockedFrameRatio;
            this.toneDominanceRatio = toneDominanceRatio;
            this.narrowbandIsolationRatio = narrowbandIsolationRatio;
            this.recentHotFrameRatio = recentHotFrameRatio;
            this.recentClippingFrameRatio = recentClippingFrameRatio;
            this.rawWpm = rawWpm;
        }

        long timestampMs() {
            return timestampMs;
        }

        String emittedValue() {
            return emittedValue;
        }

        String sourceSequence() {
            return sourceSequence;
        }

        boolean unknownCharacter() {
            return unknownCharacter;
        }

        String decision() {
            return decision;
        }

        String compatibleDecision() {
            return decision;
        }

        String verifiedDecision() {
            return verifiedDecision;
        }

        boolean trustedTimingEstablished() {
            return trustedTimingEstablished;
        }

        boolean targetToneLocked() {
            return targetToneLocked;
        }

        double recentLockedFrameRatio() {
            return recentLockedFrameRatio;
        }

        double recentNearTargetLockedFrameRatio() {
            return recentNearTargetLockedFrameRatio;
        }

        double recentActiveUnlockedFrameRatio() {
            return recentActiveUnlockedFrameRatio;
        }

        double toneDominanceRatio() {
            return toneDominanceRatio;
        }

        double narrowbandIsolationRatio() {
            return narrowbandIsolationRatio;
        }

        double recentHotFrameRatio() {
            return recentHotFrameRatio;
        }

        double recentClippingFrameRatio() {
            return recentClippingFrameRatio;
        }

        double rawWpm() {
            return rawWpm;
        }
    }

    static final class BootstrapDecisionTrace {
        private final long timestampMs;
        private final String eventKind;
        private final String classification;
        private final long durationMs;
        private final long candidateDotEstimateMs;
        private final String decision;
        private final String verifiedDecision;
        private final boolean trustedTimingEstablished;
        private final boolean targetToneLocked;
        private final double recentLockedFrameRatio;
        private final double recentNearTargetLockedFrameRatio;
        private final double recentActiveUnlockedFrameRatio;
        private final double toneDominanceRatio;
        private final double narrowbandIsolationRatio;
        private final double rawWpm;
        private final long rawDotEstimateMs;
        private final String anchorSummary;

        private BootstrapDecisionTrace(
                long timestampMs,
                String eventKind,
                String classification,
                long durationMs,
                long candidateDotEstimateMs,
                String decision,
                String verifiedDecision,
                boolean trustedTimingEstablished,
                boolean targetToneLocked,
                double recentLockedFrameRatio,
                double recentNearTargetLockedFrameRatio,
                double recentActiveUnlockedFrameRatio,
                double toneDominanceRatio,
                double narrowbandIsolationRatio,
                double rawWpm,
                long rawDotEstimateMs,
                String anchorSummary
        ) {
            this.timestampMs = timestampMs;
            this.eventKind = eventKind;
            this.classification = classification;
            this.durationMs = durationMs;
            this.candidateDotEstimateMs = candidateDotEstimateMs;
            this.decision = decision;
            this.verifiedDecision = verifiedDecision;
            this.trustedTimingEstablished = trustedTimingEstablished;
            this.targetToneLocked = targetToneLocked;
            this.recentLockedFrameRatio = recentLockedFrameRatio;
            this.recentNearTargetLockedFrameRatio = recentNearTargetLockedFrameRatio;
            this.recentActiveUnlockedFrameRatio = recentActiveUnlockedFrameRatio;
            this.toneDominanceRatio = toneDominanceRatio;
            this.narrowbandIsolationRatio = narrowbandIsolationRatio;
            this.rawWpm = rawWpm;
            this.rawDotEstimateMs = rawDotEstimateMs;
            this.anchorSummary = anchorSummary;
        }

        long timestampMs() {
            return timestampMs;
        }

        String eventKind() {
            return eventKind;
        }

        String classification() {
            return classification;
        }

        long durationMs() {
            return durationMs;
        }

        long candidateDotEstimateMs() {
            return candidateDotEstimateMs;
        }

        String decision() {
            return decision;
        }

        String compatibleDecision() {
            return decision;
        }

        String verifiedDecision() {
            return verifiedDecision;
        }

        boolean trustedTimingEstablished() {
            return trustedTimingEstablished;
        }

        boolean targetToneLocked() {
            return targetToneLocked;
        }

        double recentLockedFrameRatio() {
            return recentLockedFrameRatio;
        }

        double recentNearTargetLockedFrameRatio() {
            return recentNearTargetLockedFrameRatio;
        }

        double recentActiveUnlockedFrameRatio() {
            return recentActiveUnlockedFrameRatio;
        }

        double toneDominanceRatio() {
            return toneDominanceRatio;
        }

        double narrowbandIsolationRatio() {
            return narrowbandIsolationRatio;
        }

        double rawWpm() {
            return rawWpm;
        }

        long rawDotEstimateMs() {
            return rawDotEstimateMs;
        }

        String anchorSummary() {
            return anchorSummary;
        }
    }

    static final class TimingLearningDecisionTrace {
        private final long timestampMs;
        private final String toneEventType;
        private final String decision;
        private final boolean allowTimingLearning;
        private final boolean trustedTimingEstablished;
        private final boolean targetToneLocked;
        private final double recentLockedFrameRatio;
        private final double recentNearTargetLockedFrameRatio;
        private final double recentActiveUnlockedFrameRatio;
        private final double toneDominanceRatio;
        private final double narrowbandIsolationRatio;
        private final double recentHotFrameRatio;
        private final double recentClippingFrameRatio;
        private final String anchorSummary;
        private final double rawWpm;
        private final long rawDotEstimateMs;

        private TimingLearningDecisionTrace(
                long timestampMs,
                String toneEventType,
                String decision,
                boolean allowTimingLearning,
                boolean trustedTimingEstablished,
                boolean targetToneLocked,
                double recentLockedFrameRatio,
                double recentNearTargetLockedFrameRatio,
                double recentActiveUnlockedFrameRatio,
                double toneDominanceRatio,
                double narrowbandIsolationRatio,
                double recentHotFrameRatio,
                double recentClippingFrameRatio,
                String anchorSummary,
                double rawWpm,
                long rawDotEstimateMs
        ) {
            this.timestampMs = timestampMs;
            this.toneEventType = toneEventType;
            this.decision = decision;
            this.allowTimingLearning = allowTimingLearning;
            this.trustedTimingEstablished = trustedTimingEstablished;
            this.targetToneLocked = targetToneLocked;
            this.recentLockedFrameRatio = recentLockedFrameRatio;
            this.recentNearTargetLockedFrameRatio = recentNearTargetLockedFrameRatio;
            this.recentActiveUnlockedFrameRatio = recentActiveUnlockedFrameRatio;
            this.toneDominanceRatio = toneDominanceRatio;
            this.narrowbandIsolationRatio = narrowbandIsolationRatio;
            this.recentHotFrameRatio = recentHotFrameRatio;
            this.recentClippingFrameRatio = recentClippingFrameRatio;
            this.anchorSummary = anchorSummary;
            this.rawWpm = rawWpm;
            this.rawDotEstimateMs = rawDotEstimateMs;
        }

        long timestampMs() {
            return timestampMs;
        }

        String toneEventType() {
            return toneEventType;
        }

        String decision() {
            return decision;
        }

        boolean allowTimingLearning() {
            return allowTimingLearning;
        }

        boolean trustedTimingEstablished() {
            return trustedTimingEstablished;
        }

        boolean targetToneLocked() {
            return targetToneLocked;
        }

        double recentLockedFrameRatio() {
            return recentLockedFrameRatio;
        }

        double recentNearTargetLockedFrameRatio() {
            return recentNearTargetLockedFrameRatio;
        }

        double recentActiveUnlockedFrameRatio() {
            return recentActiveUnlockedFrameRatio;
        }

        double toneDominanceRatio() {
            return toneDominanceRatio;
        }

        double narrowbandIsolationRatio() {
            return narrowbandIsolationRatio;
        }

        double recentHotFrameRatio() {
            return recentHotFrameRatio;
        }

        double recentClippingFrameRatio() {
            return recentClippingFrameRatio;
        }

        String anchorSummary() {
            return anchorSummary;
        }

        double rawWpm() {
            return rawWpm;
        }

        long rawDotEstimateMs() {
            return rawDotEstimateMs;
        }
    }

    static final class TimingEventAdaptationTrace {
        private final long timestampMs;
        private final String eventKind;
        private final long durationMs;
        private final String rawClassification;
        private final String wpmGuardClassification;
        private final String anchorClassification;
        private final long rawDotEstimateMs;
        private final long rawIntraGapEstimateMs;
        private final long wpmGuardDotEstimateMs;
        private final long wpmGuardIntraGapEstimateMs;
        private final long anchorDotEstimateMs;
        private final long anchorIntraGapEstimateMs;
        private final boolean trustedTimingEstablished;
        private final String trustOrigin;
        private final long trustedDotEstimateMs;

        private TimingEventAdaptationTrace(
                long timestampMs,
                String eventKind,
                long durationMs,
                String rawClassification,
                String wpmGuardClassification,
                String anchorClassification,
                long rawDotEstimateMs,
                long rawIntraGapEstimateMs,
                long wpmGuardDotEstimateMs,
                long wpmGuardIntraGapEstimateMs,
                long anchorDotEstimateMs,
                long anchorIntraGapEstimateMs,
                boolean trustedTimingEstablished,
                String trustOrigin,
                long trustedDotEstimateMs
        ) {
            this.timestampMs = timestampMs;
            this.eventKind = eventKind;
            this.durationMs = durationMs;
            this.rawClassification = rawClassification;
            this.wpmGuardClassification = wpmGuardClassification;
            this.anchorClassification = anchorClassification;
            this.rawDotEstimateMs = rawDotEstimateMs;
            this.rawIntraGapEstimateMs = rawIntraGapEstimateMs;
            this.wpmGuardDotEstimateMs = wpmGuardDotEstimateMs;
            this.wpmGuardIntraGapEstimateMs = wpmGuardIntraGapEstimateMs;
            this.anchorDotEstimateMs = anchorDotEstimateMs;
            this.anchorIntraGapEstimateMs = anchorIntraGapEstimateMs;
            this.trustedTimingEstablished = trustedTimingEstablished;
            this.trustOrigin = trustOrigin;
            this.trustedDotEstimateMs = trustedDotEstimateMs;
        }

        long timestampMs() {
            return timestampMs;
        }

        String eventKind() {
            return eventKind;
        }

        long durationMs() {
            return durationMs;
        }

        String rawClassification() {
            return rawClassification;
        }

        String wpmGuardClassification() {
            return wpmGuardClassification;
        }

        String anchorClassification() {
            return anchorClassification;
        }

        long rawDotEstimateMs() {
            return rawDotEstimateMs;
        }

        long rawIntraGapEstimateMs() {
            return rawIntraGapEstimateMs;
        }

        long wpmGuardDotEstimateMs() {
            return wpmGuardDotEstimateMs;
        }

        long wpmGuardIntraGapEstimateMs() {
            return wpmGuardIntraGapEstimateMs;
        }

        long anchorDotEstimateMs() {
            return anchorDotEstimateMs;
        }

        long anchorIntraGapEstimateMs() {
            return anchorIntraGapEstimateMs;
        }

        boolean trustedTimingEstablished() {
            return trustedTimingEstablished;
        }

        String trustOrigin() {
            return trustOrigin;
        }

        long trustedDotEstimateMs() {
            return trustedDotEstimateMs;
        }
    }

    static final class TurnTransitionTrace {
        enum Kind {
            START,
            END
        }

        private final Kind kind;
        private final long timestampMs;
        private final int turnIndex;
        private final int turnSeedWpm;
        private final int referenceWpm;
        private final int currentTurnAnchorWpm;
        private final int retainedTurnAnchorWpm;
        private final String reason;
        private final CwSignalSnapshot signalSnapshot;
        private final CwTimingSnapshot stabilizedSnapshot;
        private final CwTimingSnapshot rawSnapshot;

        private TurnTransitionTrace(
                Kind kind,
                long timestampMs,
                int turnIndex,
                int turnSeedWpm,
                int referenceWpm,
                int currentTurnAnchorWpm,
                int retainedTurnAnchorWpm,
                String reason,
                CwSignalSnapshot signalSnapshot,
                CwTimingSnapshot stabilizedSnapshot,
                CwTimingSnapshot rawSnapshot
        ) {
            this.kind = kind;
            this.timestampMs = timestampMs;
            this.turnIndex = turnIndex;
            this.turnSeedWpm = turnSeedWpm;
            this.referenceWpm = referenceWpm;
            this.currentTurnAnchorWpm = currentTurnAnchorWpm;
            this.retainedTurnAnchorWpm = retainedTurnAnchorWpm;
            this.reason = reason == null ? "" : reason;
            this.signalSnapshot = signalSnapshot;
            this.stabilizedSnapshot = stabilizedSnapshot;
            this.rawSnapshot = rawSnapshot;
        }

        Kind kind() {
            return kind;
        }

        long timestampMs() {
            return timestampMs;
        }

        int turnIndex() {
            return turnIndex;
        }

        int turnSeedWpm() {
            return turnSeedWpm;
        }

        int referenceWpm() {
            return referenceWpm;
        }

        int currentTurnAnchorWpm() {
            return currentTurnAnchorWpm;
        }

        int retainedTurnAnchorWpm() {
            return retainedTurnAnchorWpm;
        }

        String reason() {
            return reason;
        }

        CwSignalSnapshot signalSnapshot() {
            return signalSnapshot;
        }

        CwTimingSnapshot stabilizedSnapshot() {
            return stabilizedSnapshot;
        }

        CwTimingSnapshot rawSnapshot() {
            return rawSnapshot;
        }
    }

    static FrontEndDisagreementProfile evaluateFrontEndDisagreementProfile(
            OfflineDetailedProbeResult detailedProbeResult,
            int disagreementThresholdHz
    ) {
        ArrayList<TrackedToneSplitSegment> trackedToneSplitSegments = new ArrayList<>();
        ArrayList<RawConsensusOutlierSegment> rawConsensusOutlierSegments = new ArrayList<>();
        int observedHypothesisFrames = 0;
        int rawTargetDisagreementFrames = 0;
        int effectiveTrackedDisagreementFrames = 0;
        int rawTargetNearRepresentativeFrames = 0;
        int hypothesisNearRepresentativeFrames = 0;
        int effectiveTrackedNearRepresentativeFrames = 0;
        int guardObservedFrames = 0;
        int guardHoldFrames = 0;
        int guardOpenFrames = 0;
        int guardMidHoldFrames = 0;
        int guardFarHoldFrames = 0;
        int guardMaxRemainingScans = 0;
        int guardMaxObservedScans = 0;
        int lastGuardCandidateFrequencyHz = 0;
        int lastGuardDriftHz = 0;
        int lastGuardRemainingScans = 0;
        String lastGuardBand = "NONE";
        long splitStartTimestampMs = -1L;
        long splitEndTimestampMs = -1L;
        int splitFrameCount = 0;
        int splitRawToneSumHz = 0;
        int splitEffectiveToneSumHz = 0;
        int splitHypothesisToneSumHz = 0;
        int splitRepresentativeToneSumHz = 0;
        int splitActiveCenterToneSumHz = 0;
        int splitRawToneLastHz = 0;
        int splitEffectiveToneLastHz = 0;
        int splitHypothesisToneLastHz = 0;
        int splitRepresentativeToneLastHz = 0;
        int splitActiveCenterToneLastHz = 0;
        long rawConsensusStartTimestampMs = -1L;
        long rawConsensusEndTimestampMs = -1L;
        int rawConsensusFrameCount = 0;
        int rawConsensusToneSumHz = 0;
        int rawConsensusRawToneSumHz = 0;
        int rawConsensusEffectiveToneSumHz = 0;
        int rawConsensusHypothesisToneSumHz = 0;
        int rawConsensusRepresentativeToneSumHz = 0;
        int rawConsensusActiveCenterToneSumHz = 0;
        int rawConsensusToneLastHz = 0;
        int rawConsensusRawToneLastHz = 0;
        int rawConsensusEffectiveToneLastHz = 0;
        int rawConsensusHypothesisToneLastHz = 0;
        int rawConsensusRepresentativeToneLastHz = 0;
        int rawConsensusActiveCenterToneLastHz = 0;
        CwSignalSnapshot finalSnapshot = detailedProbeResult.probeResult().signalSnapshot();

        for (FrameSignalTrace frameSignalTrace : detailedProbeResult.frameSignalTraces()) {
            CwSignalSnapshot snapshot = frameSignalTrace.snapshot();
            int rawToneHz = snapshot.targetToneFrequencyHz();
            int effectiveToneHz = snapshot.effectiveTrackedToneFrequencyHz();
            int hypothesisToneHz = snapshot.toneHypothesisFrequencyHz();
            int representativeSplitToneHz = snapshot.representativeLockedToneFrequencyHz();
            int activeCenterToneHz = snapshot.activeAcquisitionCenterFrequencyHz();
            int consensusToneHz = consensusToneHz(
                    representativeSplitToneHz,
                    activeCenterToneHz,
                    hypothesisToneHz
            );
            boolean splitFrame = rawToneHz > 0
                    && effectiveToneHz > 0
                    && Math.abs(rawToneHz - effectiveToneHz) >= disagreementThresholdHz;
            boolean rawConsensusOutlierFrame = consensusToneHz > 0
                    && rawToneHz > 0
                    && effectiveToneHz > 0
                    && Math.abs(rawToneHz - consensusToneHz) >= disagreementThresholdHz
                    && Math.abs(effectiveToneHz - consensusToneHz) <= 30;
            if (splitFrame) {
                if (splitFrameCount == 0) {
                    splitStartTimestampMs = frameSignalTrace.timestampMs();
                }
                splitEndTimestampMs = frameSignalTrace.timestampMs();
                splitFrameCount += 1;
                splitRawToneSumHz += rawToneHz;
                splitEffectiveToneSumHz += effectiveToneHz;
                splitHypothesisToneSumHz += hypothesisToneHz;
                splitRepresentativeToneSumHz += representativeSplitToneHz;
                splitActiveCenterToneSumHz += activeCenterToneHz;
                splitRawToneLastHz = rawToneHz;
                splitEffectiveToneLastHz = effectiveToneHz;
                splitHypothesisToneLastHz = hypothesisToneHz;
                splitRepresentativeToneLastHz = representativeSplitToneHz;
                splitActiveCenterToneLastHz = activeCenterToneHz;
            } else if (splitFrameCount > 0) {
                trackedToneSplitSegments.add(new TrackedToneSplitSegment(
                        splitStartTimestampMs,
                        splitEndTimestampMs,
                        splitFrameCount,
                        splitRawToneSumHz / splitFrameCount,
                        splitEffectiveToneSumHz / splitFrameCount,
                        splitHypothesisToneSumHz / splitFrameCount,
                        splitRepresentativeToneSumHz / splitFrameCount,
                        splitActiveCenterToneSumHz / splitFrameCount,
                        splitRawToneLastHz,
                        splitEffectiveToneLastHz,
                        splitHypothesisToneLastHz,
                        splitRepresentativeToneLastHz,
                        splitActiveCenterToneLastHz
                ));
                splitStartTimestampMs = -1L;
                splitEndTimestampMs = -1L;
                splitFrameCount = 0;
                splitRawToneSumHz = 0;
                splitEffectiveToneSumHz = 0;
                splitHypothesisToneSumHz = 0;
                splitRepresentativeToneSumHz = 0;
                splitActiveCenterToneSumHz = 0;
                splitRawToneLastHz = 0;
                splitEffectiveToneLastHz = 0;
                splitHypothesisToneLastHz = 0;
                splitRepresentativeToneLastHz = 0;
                splitActiveCenterToneLastHz = 0;
            }
            if (rawConsensusOutlierFrame) {
                if (rawConsensusFrameCount == 0) {
                    rawConsensusStartTimestampMs = frameSignalTrace.timestampMs();
                }
                rawConsensusEndTimestampMs = frameSignalTrace.timestampMs();
                rawConsensusFrameCount += 1;
                rawConsensusToneSumHz += consensusToneHz;
                rawConsensusRawToneSumHz += rawToneHz;
                rawConsensusEffectiveToneSumHz += effectiveToneHz;
                rawConsensusHypothesisToneSumHz += hypothesisToneHz;
                rawConsensusRepresentativeToneSumHz += representativeSplitToneHz;
                rawConsensusActiveCenterToneSumHz += activeCenterToneHz;
                rawConsensusToneLastHz = consensusToneHz;
                rawConsensusRawToneLastHz = rawToneHz;
                rawConsensusEffectiveToneLastHz = effectiveToneHz;
                rawConsensusHypothesisToneLastHz = hypothesisToneHz;
                rawConsensusRepresentativeToneLastHz = representativeSplitToneHz;
                rawConsensusActiveCenterToneLastHz = activeCenterToneHz;
            } else if (rawConsensusFrameCount > 0) {
                rawConsensusOutlierSegments.add(new RawConsensusOutlierSegment(
                        rawConsensusStartTimestampMs,
                        rawConsensusEndTimestampMs,
                        rawConsensusFrameCount,
                        rawConsensusToneSumHz / rawConsensusFrameCount,
                        rawConsensusRawToneSumHz / rawConsensusFrameCount,
                        rawConsensusEffectiveToneSumHz / rawConsensusFrameCount,
                        rawConsensusHypothesisToneSumHz / rawConsensusFrameCount,
                        rawConsensusRepresentativeToneSumHz / rawConsensusFrameCount,
                        rawConsensusActiveCenterToneSumHz / rawConsensusFrameCount,
                        rawConsensusToneLastHz,
                        rawConsensusRawToneLastHz,
                        rawConsensusEffectiveToneLastHz,
                        rawConsensusHypothesisToneLastHz,
                        rawConsensusRepresentativeToneLastHz,
                        rawConsensusActiveCenterToneLastHz
                ));
                rawConsensusStartTimestampMs = -1L;
                rawConsensusEndTimestampMs = -1L;
                rawConsensusFrameCount = 0;
                rawConsensusToneSumHz = 0;
                rawConsensusRawToneSumHz = 0;
                rawConsensusEffectiveToneSumHz = 0;
                rawConsensusHypothesisToneSumHz = 0;
                rawConsensusRepresentativeToneSumHz = 0;
                rawConsensusActiveCenterToneSumHz = 0;
                rawConsensusToneLastHz = 0;
                rawConsensusRawToneLastHz = 0;
                rawConsensusEffectiveToneLastHz = 0;
                rawConsensusHypothesisToneLastHz = 0;
                rawConsensusRepresentativeToneLastHz = 0;
                rawConsensusActiveCenterToneLastHz = 0;
            }
            if (snapshot.lockedRetuneGuardRequiredScans() > 0
                    || snapshot.lockedRetuneGuardCandidateFrequencyHz() > 0
                    || snapshot.lockedRetuneGuardHolding()) {
                guardObservedFrames += 1;
                lastGuardCandidateFrequencyHz = snapshot.lockedRetuneGuardCandidateFrequencyHz();
                lastGuardDriftHz = snapshot.lockedRetuneGuardDriftHz();
                lastGuardRemainingScans = snapshot.lockedRetuneGuardRemainingScans();
                lastGuardBand = snapshot.lockedRetuneGuardBand();
                guardMaxRemainingScans = Math.max(
                        guardMaxRemainingScans,
                        snapshot.lockedRetuneGuardRemainingScans()
                );
                guardMaxObservedScans = Math.max(
                        guardMaxObservedScans,
                        snapshot.lockedRetuneGuardObservedScans()
                );
                if (snapshot.lockedRetuneGuardHolding()) {
                    guardHoldFrames += 1;
                    if ("FAR".equals(snapshot.lockedRetuneGuardBand())) {
                        guardFarHoldFrames += 1;
                    } else if ("MID".equals(snapshot.lockedRetuneGuardBand())) {
                        guardMidHoldFrames += 1;
                    }
                } else {
                    guardOpenFrames += 1;
                }
            }
            if (snapshot.toneHypothesisSupportFrames() <= 0 || "NONE".equals(snapshot.toneHypothesisSource())) {
                continue;
            }
            observedHypothesisFrames += 1;
            if (Math.abs(snapshot.targetToneFrequencyHz() - hypothesisToneHz) >= disagreementThresholdHz) {
                rawTargetDisagreementFrames += 1;
            }
            if (Math.abs(snapshot.effectiveTrackedToneFrequencyHz() - hypothesisToneHz) >= disagreementThresholdHz) {
                effectiveTrackedDisagreementFrames += 1;
            }
            if (snapshot.representativeLockedToneFrameCount() > 0) {
                int representativeToneHz = snapshot.representativeLockedToneFrequencyHz();
                if (Math.abs(snapshot.targetToneFrequencyHz() - representativeToneHz) <= 30) {
                    rawTargetNearRepresentativeFrames += 1;
                }
                if (Math.abs(snapshot.effectiveTrackedToneFrequencyHz() - representativeToneHz) <= 30) {
                    effectiveTrackedNearRepresentativeFrames += 1;
                }
                if (Math.abs(hypothesisToneHz - representativeToneHz) <= 30) {
                    hypothesisNearRepresentativeFrames += 1;
                }
            }
        }
        if (splitFrameCount > 0) {
            trackedToneSplitSegments.add(new TrackedToneSplitSegment(
                    splitStartTimestampMs,
                    splitEndTimestampMs,
                    splitFrameCount,
                    splitRawToneSumHz / splitFrameCount,
                    splitEffectiveToneSumHz / splitFrameCount,
                    splitHypothesisToneSumHz / splitFrameCount,
                    splitRepresentativeToneSumHz / splitFrameCount,
                    splitActiveCenterToneSumHz / splitFrameCount,
                    splitRawToneLastHz,
                    splitEffectiveToneLastHz,
                    splitHypothesisToneLastHz,
                    splitRepresentativeToneLastHz,
                    splitActiveCenterToneLastHz
            ));
        }
        if (rawConsensusFrameCount > 0) {
            rawConsensusOutlierSegments.add(new RawConsensusOutlierSegment(
                    rawConsensusStartTimestampMs,
                    rawConsensusEndTimestampMs,
                    rawConsensusFrameCount,
                    rawConsensusToneSumHz / rawConsensusFrameCount,
                    rawConsensusRawToneSumHz / rawConsensusFrameCount,
                    rawConsensusEffectiveToneSumHz / rawConsensusFrameCount,
                    rawConsensusHypothesisToneSumHz / rawConsensusFrameCount,
                    rawConsensusRepresentativeToneSumHz / rawConsensusFrameCount,
                    rawConsensusActiveCenterToneSumHz / rawConsensusFrameCount,
                    rawConsensusToneLastHz,
                    rawConsensusRawToneLastHz,
                    rawConsensusEffectiveToneLastHz,
                    rawConsensusHypothesisToneLastHz,
                    rawConsensusRepresentativeToneLastHz,
                    rawConsensusActiveCenterToneLastHz
            ));
        }

        return new FrontEndDisagreementProfile(
                detailedProbeResult.probeResult().sourceLabel(),
                disagreementThresholdHz,
                observedHypothesisFrames,
                rawTargetDisagreementFrames,
                effectiveTrackedDisagreementFrames,
                rawTargetNearRepresentativeFrames,
                effectiveTrackedNearRepresentativeFrames,
                hypothesisNearRepresentativeFrames,
                finalSnapshot.representativeCompetitionObservationCount(),
                finalSnapshot.representativeCompetitionTrackedWinFrames(),
                finalSnapshot.representativeCompetitionHypothesisWinFrames(),
                finalSnapshot.representativeCompetitionHypothesisMaxWinStreak(),
                finalSnapshot.activeCenterCompetitionObservationCount(),
                finalSnapshot.activeCenterCompetitionTrackedWinFrames(),
                finalSnapshot.activeCenterCompetitionHypothesisWinFrames(),
                finalSnapshot.activeCenterCompetitionHypothesisMaxWinStreak(),
                guardObservedFrames,
                guardHoldFrames,
                guardOpenFrames,
                guardMidHoldFrames,
                guardFarHoldFrames,
                guardMaxRemainingScans,
                guardMaxObservedScans,
                lastGuardCandidateFrequencyHz,
                lastGuardDriftHz,
                lastGuardRemainingScans,
                lastGuardBand,
                trackedToneSplitSegments,
                rawConsensusOutlierSegments
        );
    }

    private static int consensusToneHz(int representativeToneHz, int activeCenterToneHz, int hypothesisToneHz) {
        int sumHz = 0;
        int count = 0;
        int minHz = Integer.MAX_VALUE;
        int maxHz = Integer.MIN_VALUE;
        if (representativeToneHz > 0) {
            sumHz += representativeToneHz;
            count += 1;
            minHz = Math.min(minHz, representativeToneHz);
            maxHz = Math.max(maxHz, representativeToneHz);
        }
        if (activeCenterToneHz > 0) {
            sumHz += activeCenterToneHz;
            count += 1;
            minHz = Math.min(minHz, activeCenterToneHz);
            maxHz = Math.max(maxHz, activeCenterToneHz);
        }
        if (hypothesisToneHz > 0) {
            sumHz += hypothesisToneHz;
            count += 1;
            minHz = Math.min(minHz, hypothesisToneHz);
            maxHz = Math.max(maxHz, hypothesisToneHz);
        }
        if (count < 2 || maxHz - minHz > 30) {
            return 0;
        }
        return sumHz / count;
    }

    static ForcedToneReplayResult replayForcedTrackedToneDecode(OfflineDetailedProbeResult detailedProbeResult) {
        return replayForcedSnapshotToneDecode(detailedProbeResult, ForcedToneMode.TRK);
    }

    static ForcedToneReplayResult replayForcedEffectiveTrackedToneDecode(OfflineDetailedProbeResult detailedProbeResult) {
        return replayForcedSnapshotToneDecode(detailedProbeResult, ForcedToneMode.EFF);
    }

    static ForcedToneReplayResult replayForcedHypothesisToneDecode(OfflineDetailedProbeResult detailedProbeResult) {
        return replayForcedSnapshotToneDecode(detailedProbeResult, ForcedToneMode.HYP);
    }

    static ForcedToneReplayResult replayForcedConstantToneDecode(
            OfflineDetailedProbeResult detailedProbeResult,
            int forcedToneFrequencyHz
    ) {
        CwSignalProcessor signalProcessor = new CwSignalProcessor();
        signalProcessor.setPreferredToneFrequencyHz(forcedToneFrequencyHz);
        CwHybridTimingModel timingModel = new CwHybridTimingModel();
        CwDecoder decoder = new CwDecoder();
        CwInterpreter interpreter = new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);
        QsoStateMachine qsoStateMachine = new QsoStateMachine();
        ArrayList<CwToneEvent> replayedToneEvents = new ArrayList<>();
        ArrayList<CwTimingEvent> replayedTimingEvents = new ArrayList<>();
        ArrayList<CwDecodeEvent> replayedDecodeEvents = new ArrayList<>();
        ArrayList<FrameSignalTrace> frameSignalTraces = new ArrayList<>();
        int clampedFrequencyHz = Math.max(1, forcedToneFrequencyHz);

        for (int index = 0; index < detailedProbeResult.frames().size(); index++) {
            AudioFrame frame = detailedProbeResult.frames().get(index);
            List<CwToneEvent> toneEvents = signalProcessor.processForcedToneForTesting(frame, clampedFrequencyHz);
            frameSignalTraces.add(buildFrameSignalTrace(frame, signalProcessor));
            replayedToneEvents.addAll(toneEvents);
            drainToneEvents(
                    toneEvents,
                    timingModel,
                    decoder,
                    interpreter,
                    qsoStateMachine,
                    replayedTimingEvents,
                    replayedDecodeEvents
            );
        }

        flushPendingDecodeTimeline(
                detailedProbeResult.flushTimestampMs(),
                timingModel,
                decoder,
                interpreter,
                qsoStateMachine,
                replayedTimingEvents,
                replayedDecodeEvents
        );

        return new ForcedToneReplayResult(
                detailedProbeResult.probeResult().sourceLabel(),
                "FIX" + clampedFrequencyHz,
                clampedFrequencyHz,
                sanitize(decoder.snapshot().decodedText()),
                replayedToneEvents,
                replayedTimingEvents,
                replayedDecodeEvents,
                frameSignalTraces,
                decoder.snapshot(),
                interpreter.snapshot()
        );
    }

    private static ForcedToneReplayResult replayForcedSnapshotToneDecode(
            OfflineDetailedProbeResult detailedProbeResult,
            ForcedToneMode mode
    ) {
        CwSignalProcessor signalProcessor = new CwSignalProcessor();
        CwHybridTimingModel timingModel = new CwHybridTimingModel();
        CwDecoder decoder = new CwDecoder();
        CwInterpreter interpreter = new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS);
        QsoStateMachine qsoStateMachine = new QsoStateMachine();
        ArrayList<CwToneEvent> replayedToneEvents = new ArrayList<>();
        ArrayList<CwTimingEvent> replayedTimingEvents = new ArrayList<>();
        ArrayList<CwDecodeEvent> replayedDecodeEvents = new ArrayList<>();
        ArrayList<FrameSignalTrace> frameSignalTraces = new ArrayList<>();
        int lastForcedFrequencyHz = 650;

        for (int index = 0; index < detailedProbeResult.frames().size(); index++) {
            AudioFrame frame = detailedProbeResult.frames().get(index);
            CwSignalSnapshot snapshot = detailedProbeResult.frameSignalTraces().get(index).snapshot();
            int forcedFrequencyHz;
            switch (mode) {
                case HYP:
                    if (snapshot.toneHypothesisSupportFrames() > 0 && !"NONE".equals(snapshot.toneHypothesisSource())) {
                        forcedFrequencyHz = snapshot.toneHypothesisFrequencyHz();
                        lastForcedFrequencyHz = forcedFrequencyHz;
                    } else {
                        forcedFrequencyHz = lastForcedFrequencyHz;
                    }
                    break;
                case EFF:
                    forcedFrequencyHz = snapshot.effectiveTrackedToneFrequencyHz() > 0
                            ? snapshot.effectiveTrackedToneFrequencyHz()
                            : (snapshot.targetToneFrequencyHz() > 0
                            ? snapshot.targetToneFrequencyHz()
                            : lastForcedFrequencyHz);
                    lastForcedFrequencyHz = forcedFrequencyHz;
                    break;
                case TRK:
                default:
                    forcedFrequencyHz = snapshot.targetToneFrequencyHz() > 0
                            ? snapshot.targetToneFrequencyHz()
                            : lastForcedFrequencyHz;
                    lastForcedFrequencyHz = forcedFrequencyHz;
                    break;
            }
            List<CwToneEvent> toneEvents = signalProcessor.processForcedToneForTesting(frame, forcedFrequencyHz);
            frameSignalTraces.add(buildFrameSignalTrace(frame, signalProcessor));
            replayedToneEvents.addAll(toneEvents);
            drainToneEvents(
                    toneEvents,
                    timingModel,
                    decoder,
                    interpreter,
                    qsoStateMachine,
                    replayedTimingEvents,
                    replayedDecodeEvents
            );
        }

        flushPendingDecodeTimeline(
                detailedProbeResult.flushTimestampMs(),
                timingModel,
                decoder,
                interpreter,
                qsoStateMachine,
                replayedTimingEvents,
                replayedDecodeEvents
        );

        return new ForcedToneReplayResult(
                detailedProbeResult.probeResult().sourceLabel(),
                mode.name(),
                lastForcedFrequencyHz,
                sanitize(decoder.snapshot().decodedText()),
                replayedToneEvents,
                replayedTimingEvents,
                replayedDecodeEvents,
                frameSignalTraces,
                decoder.snapshot(),
                interpreter.snapshot()
        );
    }

    private enum ForcedToneMode {
        TRK,
        EFF,
        HYP
    }

    static final class FrameSignalTrace {
        private final long timestampMs;
        private final CwSignalSnapshot snapshot;
        private final double detectionLevel;
        private final boolean weakValleyBridgeActive;
        private final int weakValleyBridgeFramesRemaining;
        private final boolean attackQualified;
        private final boolean trackedToneMemoryActiveBeforeFrame;
        private final int attackAnchorFrequencyHzBeforeFrame;
        private final int toneOnThreshold;
        private final long frameLocalToneOnTimestampMs;
        private final long postReleaseGapMs;
        private final long postReleaseWindowMs;
        private final boolean postReleaseRescueContinuationWindowActive;
        private final long postReleaseRescueContinuationWindowRemainingMs;
        private final int postReleaseWeakContinuityRescueCount;
        private final boolean trustedWeakPostReleaseOnsetChainActive;
        private final int trustedWeakPostReleaseOnsetChainFrameCount;
        private final long trustedWeakPostReleaseOnsetChainStartMs;
        private final long postReleaseWeakContinuityGapLimitMs;
        private final boolean weakPostReleaseOnsetChainCandidate;
        private final boolean trustedContinuityToneOnCandidate;
        private final double localContrastRatio;
        private final boolean steadyLateGapNearTargetRescueCandidate;
        private final boolean lowGrowthStrongSteadyNearTargetRescue;
        private final boolean nearTargetPostReleaseToneOnRescue;
        private final boolean postReleaseSteadyCarrierSuppressed;
        private final boolean farAttackToneOnDelayed;
        private final boolean toneOnAccepted;
        private final boolean toneOnAcceptedByRescue;
        private final boolean currentToneStartedByPostReleaseRescue;
        private final boolean releaseTailHoldApplied;
        private final int currentToneRunWeakBootstrapReleaseTailHoldCount;
        private final int toneActiveReleaseThreshold;
        private final double releaseTailHoldRequiredDetectionThreshold;
        private final boolean releaseTailHoldSufficientRecentTrust;
        private final boolean releaseTailHoldCurrentRunStableBootstrapEligible;
        private final boolean releaseTailHoldCurrentRunWeakBootstrapEligible;
        private final String postReleaseRescueDecision;
        private final String postReleaseSuppressionDecision;
        private final String farAttackDelayDecision;
        private final String toneOnDecision;
        private final String releaseTailHoldDecision;

        private FrameSignalTrace(
                long timestampMs,
                CwSignalSnapshot snapshot,
                double detectionLevel,
                boolean weakValleyBridgeActive,
                int weakValleyBridgeFramesRemaining,
                boolean attackQualified,
                boolean trackedToneMemoryActiveBeforeFrame,
                int attackAnchorFrequencyHzBeforeFrame,
                int toneOnThreshold,
                long frameLocalToneOnTimestampMs,
                long postReleaseGapMs,
                long postReleaseWindowMs,
                boolean postReleaseRescueContinuationWindowActive,
                long postReleaseRescueContinuationWindowRemainingMs,
                int postReleaseWeakContinuityRescueCount,
                boolean trustedWeakPostReleaseOnsetChainActive,
                int trustedWeakPostReleaseOnsetChainFrameCount,
                long trustedWeakPostReleaseOnsetChainStartMs,
                long postReleaseWeakContinuityGapLimitMs,
                boolean weakPostReleaseOnsetChainCandidate,
                boolean trustedContinuityToneOnCandidate,
                double localContrastRatio,
                boolean steadyLateGapNearTargetRescueCandidate,
                boolean lowGrowthStrongSteadyNearTargetRescue,
                boolean nearTargetPostReleaseToneOnRescue,
                boolean postReleaseSteadyCarrierSuppressed,
                boolean farAttackToneOnDelayed,
                boolean toneOnAccepted,
                boolean toneOnAcceptedByRescue,
                boolean currentToneStartedByPostReleaseRescue,
                boolean releaseTailHoldApplied,
                int currentToneRunWeakBootstrapReleaseTailHoldCount,
                int toneActiveReleaseThreshold,
                double releaseTailHoldRequiredDetectionThreshold,
                boolean releaseTailHoldSufficientRecentTrust,
                boolean releaseTailHoldCurrentRunStableBootstrapEligible,
                boolean releaseTailHoldCurrentRunWeakBootstrapEligible,
                String postReleaseRescueDecision,
                String postReleaseSuppressionDecision,
                String farAttackDelayDecision,
                String toneOnDecision,
                String releaseTailHoldDecision
        ) {
            this.timestampMs = timestampMs;
            this.snapshot = snapshot;
            this.detectionLevel = detectionLevel;
            this.weakValleyBridgeActive = weakValleyBridgeActive;
            this.weakValleyBridgeFramesRemaining = weakValleyBridgeFramesRemaining;
            this.attackQualified = attackQualified;
            this.trackedToneMemoryActiveBeforeFrame = trackedToneMemoryActiveBeforeFrame;
            this.attackAnchorFrequencyHzBeforeFrame = attackAnchorFrequencyHzBeforeFrame;
            this.toneOnThreshold = toneOnThreshold;
            this.frameLocalToneOnTimestampMs = frameLocalToneOnTimestampMs;
            this.postReleaseGapMs = postReleaseGapMs;
            this.postReleaseWindowMs = postReleaseWindowMs;
            this.postReleaseRescueContinuationWindowActive =
                    postReleaseRescueContinuationWindowActive;
            this.postReleaseRescueContinuationWindowRemainingMs =
                    postReleaseRescueContinuationWindowRemainingMs;
            this.postReleaseWeakContinuityRescueCount = postReleaseWeakContinuityRescueCount;
            this.trustedWeakPostReleaseOnsetChainActive =
                    trustedWeakPostReleaseOnsetChainActive;
            this.trustedWeakPostReleaseOnsetChainFrameCount =
                    trustedWeakPostReleaseOnsetChainFrameCount;
            this.trustedWeakPostReleaseOnsetChainStartMs =
                    trustedWeakPostReleaseOnsetChainStartMs;
            this.postReleaseWeakContinuityGapLimitMs = postReleaseWeakContinuityGapLimitMs;
            this.weakPostReleaseOnsetChainCandidate = weakPostReleaseOnsetChainCandidate;
            this.trustedContinuityToneOnCandidate = trustedContinuityToneOnCandidate;
            this.localContrastRatio = localContrastRatio;
            this.steadyLateGapNearTargetRescueCandidate = steadyLateGapNearTargetRescueCandidate;
            this.lowGrowthStrongSteadyNearTargetRescue = lowGrowthStrongSteadyNearTargetRescue;
            this.nearTargetPostReleaseToneOnRescue = nearTargetPostReleaseToneOnRescue;
            this.postReleaseSteadyCarrierSuppressed = postReleaseSteadyCarrierSuppressed;
            this.farAttackToneOnDelayed = farAttackToneOnDelayed;
            this.toneOnAccepted = toneOnAccepted;
            this.toneOnAcceptedByRescue = toneOnAcceptedByRescue;
            this.currentToneStartedByPostReleaseRescue = currentToneStartedByPostReleaseRescue;
            this.releaseTailHoldApplied = releaseTailHoldApplied;
            this.currentToneRunWeakBootstrapReleaseTailHoldCount =
                    currentToneRunWeakBootstrapReleaseTailHoldCount;
            this.toneActiveReleaseThreshold = toneActiveReleaseThreshold;
            this.releaseTailHoldRequiredDetectionThreshold = releaseTailHoldRequiredDetectionThreshold;
            this.releaseTailHoldSufficientRecentTrust = releaseTailHoldSufficientRecentTrust;
            this.releaseTailHoldCurrentRunStableBootstrapEligible =
                    releaseTailHoldCurrentRunStableBootstrapEligible;
            this.releaseTailHoldCurrentRunWeakBootstrapEligible =
                    releaseTailHoldCurrentRunWeakBootstrapEligible;
            this.postReleaseRescueDecision = postReleaseRescueDecision == null
                    ? "NONE"
                    : postReleaseRescueDecision;
            this.postReleaseSuppressionDecision = postReleaseSuppressionDecision == null
                    ? "NONE"
                    : postReleaseSuppressionDecision;
            this.farAttackDelayDecision = farAttackDelayDecision == null
                    ? "NONE"
                    : farAttackDelayDecision;
            this.toneOnDecision = toneOnDecision == null ? "NONE" : toneOnDecision;
            this.releaseTailHoldDecision = releaseTailHoldDecision == null
                    ? "NONE"
                    : releaseTailHoldDecision;
        }

        long timestampMs() {
            return timestampMs;
        }

        CwSignalSnapshot snapshot() {
            return snapshot;
        }

        double detectionLevel() {
            return detectionLevel;
        }

        boolean weakValleyBridgeActive() {
            return weakValleyBridgeActive;
        }

        int weakValleyBridgeFramesRemaining() {
            return weakValleyBridgeFramesRemaining;
        }

        double localContrastRatio() {
            return localContrastRatio;
        }

        boolean attackQualified() {
            return attackQualified;
        }

        boolean trackedToneMemoryActiveBeforeFrame() {
            return trackedToneMemoryActiveBeforeFrame;
        }

        int attackAnchorFrequencyHzBeforeFrame() {
            return attackAnchorFrequencyHzBeforeFrame;
        }

        int toneOnThreshold() {
            return toneOnThreshold;
        }

        long frameLocalToneOnTimestampMs() {
            return frameLocalToneOnTimestampMs;
        }

        long postReleaseGapMs() {
            return postReleaseGapMs;
        }

        long postReleaseWindowMs() {
            return postReleaseWindowMs;
        }

        boolean postReleaseRescueContinuationWindowActive() {
            return postReleaseRescueContinuationWindowActive;
        }

        long postReleaseRescueContinuationWindowRemainingMs() {
            return postReleaseRescueContinuationWindowRemainingMs;
        }

        int postReleaseWeakContinuityRescueCount() {
            return postReleaseWeakContinuityRescueCount;
        }

        boolean trustedWeakPostReleaseOnsetChainActive() {
            return trustedWeakPostReleaseOnsetChainActive;
        }

        int trustedWeakPostReleaseOnsetChainFrameCount() {
            return trustedWeakPostReleaseOnsetChainFrameCount;
        }

        long trustedWeakPostReleaseOnsetChainStartMs() {
            return trustedWeakPostReleaseOnsetChainStartMs;
        }

        long postReleaseWeakContinuityGapLimitMs() {
            return postReleaseWeakContinuityGapLimitMs;
        }

        boolean weakPostReleaseOnsetChainCandidate() {
            return weakPostReleaseOnsetChainCandidate;
        }

        boolean trustedContinuityToneOnCandidate() {
            return trustedContinuityToneOnCandidate;
        }

        boolean steadyLateGapNearTargetRescueCandidate() {
            return steadyLateGapNearTargetRescueCandidate;
        }

        boolean lowGrowthStrongSteadyNearTargetRescue() {
            return lowGrowthStrongSteadyNearTargetRescue;
        }

        boolean nearTargetPostReleaseToneOnRescue() {
            return nearTargetPostReleaseToneOnRescue;
        }

        boolean postReleaseSteadyCarrierSuppressed() {
            return postReleaseSteadyCarrierSuppressed;
        }

        boolean farAttackToneOnDelayed() {
            return farAttackToneOnDelayed;
        }

        boolean toneOnAccepted() {
            return toneOnAccepted;
        }

        boolean toneOnAcceptedByRescue() {
            return toneOnAcceptedByRescue;
        }

        boolean currentToneStartedByPostReleaseRescue() {
            return currentToneStartedByPostReleaseRescue;
        }

        boolean releaseTailHoldApplied() {
            return releaseTailHoldApplied;
        }

        int currentToneRunWeakBootstrapReleaseTailHoldCount() {
            return currentToneRunWeakBootstrapReleaseTailHoldCount;
        }

        int toneActiveReleaseThreshold() {
            return toneActiveReleaseThreshold;
        }

        double releaseTailHoldRequiredDetectionThreshold() {
            return releaseTailHoldRequiredDetectionThreshold;
        }

        boolean releaseTailHoldSufficientRecentTrust() {
            return releaseTailHoldSufficientRecentTrust;
        }

        boolean releaseTailHoldCurrentRunStableBootstrapEligible() {
            return releaseTailHoldCurrentRunStableBootstrapEligible;
        }

        boolean releaseTailHoldCurrentRunWeakBootstrapEligible() {
            return releaseTailHoldCurrentRunWeakBootstrapEligible;
        }

        String postReleaseRescueDecision() {
            return postReleaseRescueDecision;
        }

        String postReleaseSuppressionDecision() {
            return postReleaseSuppressionDecision;
        }

        String farAttackDelayDecision() {
            return farAttackDelayDecision;
        }

        String toneOnDecision() {
            return toneOnDecision;
        }

        String releaseTailHoldDecision() {
            return releaseTailHoldDecision;
        }
    }

    static final class TimingStateTrace {
        private final long timestampMs;
        private final DebugSnapshot debugSnapshot;
        private final CwTimingSnapshot stabilizedSnapshot;
        private final CwTimingSnapshot rawSnapshot;
        private final String debugSummary;

        private TimingStateTrace(
                long timestampMs,
                DebugSnapshot debugSnapshot,
                CwTimingSnapshot stabilizedSnapshot,
                CwTimingSnapshot rawSnapshot,
                String debugSummary
        ) {
            this.timestampMs = timestampMs;
            this.debugSnapshot = debugSnapshot;
            this.stabilizedSnapshot = stabilizedSnapshot;
            this.rawSnapshot = rawSnapshot;
            this.debugSummary = debugSummary;
        }

        long timestampMs() {
            return timestampMs;
        }

        DebugSnapshot debugSnapshot() {
            return debugSnapshot;
        }

        CwTimingSnapshot stabilizedSnapshot() {
            return stabilizedSnapshot;
        }

        CwTimingSnapshot rawSnapshot() {
            return rawSnapshot;
        }

        String debugSummary() {
            return debugSummary;
        }
    }

    static final class RxToneModeDecisionTrace {
        private final long timestampMs;
        private final CwSignalProcessor.RxToneMode resolvedMode;
        private final String strategy;
        private final boolean trustedTimingEstablished;
        private final boolean fallbackLatched;
        private final String turnPhase;
        private final long turnStartedAtMs;
        private final boolean usefulFixedProgress;
        private final boolean eligibleForPreTrustFallback;
        private final boolean targetToneLocked;
        private final int consecutiveLockedFrames;
        private final double recentLockedFrameRatio;

        private RxToneModeDecisionTrace(
                long timestampMs,
                CwSignalProcessor.RxToneMode resolvedMode,
                String strategy,
                boolean trustedTimingEstablished,
                boolean fallbackLatched,
                String turnPhase,
                long turnStartedAtMs,
                boolean usefulFixedProgress,
                boolean eligibleForPreTrustFallback,
                boolean targetToneLocked,
                int consecutiveLockedFrames,
                double recentLockedFrameRatio
        ) {
            this.timestampMs = timestampMs;
            this.resolvedMode = resolvedMode;
            this.strategy = strategy == null ? "NONE" : strategy;
            this.trustedTimingEstablished = trustedTimingEstablished;
            this.fallbackLatched = fallbackLatched;
            this.turnPhase = turnPhase == null ? "NONE" : turnPhase;
            this.turnStartedAtMs = turnStartedAtMs;
            this.usefulFixedProgress = usefulFixedProgress;
            this.eligibleForPreTrustFallback = eligibleForPreTrustFallback;
            this.targetToneLocked = targetToneLocked;
            this.consecutiveLockedFrames = consecutiveLockedFrames;
            this.recentLockedFrameRatio = recentLockedFrameRatio;
        }

        long timestampMs() {
            return timestampMs;
        }

        CwSignalProcessor.RxToneMode resolvedMode() {
            return resolvedMode;
        }

        String strategy() {
            return strategy;
        }

        boolean trustedTimingEstablished() {
            return trustedTimingEstablished;
        }

        boolean fallbackLatched() {
            return fallbackLatched;
        }

        String turnPhase() {
            return turnPhase;
        }

        long turnStartedAtMs() {
            return turnStartedAtMs;
        }

        boolean usefulFixedProgress() {
            return usefulFixedProgress;
        }

        boolean eligibleForPreTrustFallback() {
            return eligibleForPreTrustFallback;
        }

        boolean targetToneLocked() {
            return targetToneLocked;
        }

        int consecutiveLockedFrames() {
            return consecutiveLockedFrames;
        }

        double recentLockedFrameRatio() {
            return recentLockedFrameRatio;
        }
    }

    static final class FrontEndDisagreementProfile {
        private final String sourceLabel;
        private final int disagreementThresholdHz;
        private final int observedHypothesisFrames;
        private final int rawTargetDisagreementFrames;
        private final int effectiveTrackedDisagreementFrames;
        private final int rawTargetNearRepresentativeFrames;
        private final int effectiveTrackedNearRepresentativeFrames;
        private final int hypothesisNearRepresentativeFrames;
        private final int representativeCompetitionObservationCount;
        private final int representativeCompetitionTrackedWinFrames;
        private final int representativeCompetitionHypothesisWinFrames;
        private final int representativeCompetitionHypothesisMaxWinStreak;
        private final int activeCenterCompetitionObservationCount;
        private final int activeCenterCompetitionTrackedWinFrames;
        private final int activeCenterCompetitionHypothesisWinFrames;
        private final int activeCenterCompetitionHypothesisMaxWinStreak;
        private final int guardObservedFrames;
        private final int guardHoldFrames;
        private final int guardOpenFrames;
        private final int guardMidHoldFrames;
        private final int guardFarHoldFrames;
        private final int guardMaxRemainingScans;
        private final int guardMaxObservedScans;
        private final int lastGuardCandidateFrequencyHz;
        private final int lastGuardDriftHz;
        private final int lastGuardRemainingScans;
        private final String lastGuardBand;
        private final List<TrackedToneSplitSegment> trackedToneSplitSegments;
        private final List<RawConsensusOutlierSegment> rawConsensusOutlierSegments;

        private FrontEndDisagreementProfile(
                String sourceLabel,
                int disagreementThresholdHz,
                int observedHypothesisFrames,
                int rawTargetDisagreementFrames,
                int effectiveTrackedDisagreementFrames,
                int rawTargetNearRepresentativeFrames,
                int effectiveTrackedNearRepresentativeFrames,
                int hypothesisNearRepresentativeFrames,
                int representativeCompetitionObservationCount,
                int representativeCompetitionTrackedWinFrames,
                int representativeCompetitionHypothesisWinFrames,
                int representativeCompetitionHypothesisMaxWinStreak,
                int activeCenterCompetitionObservationCount,
                int activeCenterCompetitionTrackedWinFrames,
                int activeCenterCompetitionHypothesisWinFrames,
                int activeCenterCompetitionHypothesisMaxWinStreak,
                int guardObservedFrames,
                int guardHoldFrames,
                int guardOpenFrames,
                int guardMidHoldFrames,
                int guardFarHoldFrames,
                int guardMaxRemainingScans,
                int guardMaxObservedScans,
                int lastGuardCandidateFrequencyHz,
                int lastGuardDriftHz,
                int lastGuardRemainingScans,
                String lastGuardBand,
                List<TrackedToneSplitSegment> trackedToneSplitSegments,
                List<RawConsensusOutlierSegment> rawConsensusOutlierSegments
        ) {
            this.sourceLabel = sourceLabel;
            this.disagreementThresholdHz = disagreementThresholdHz;
            this.observedHypothesisFrames = observedHypothesisFrames;
            this.rawTargetDisagreementFrames = rawTargetDisagreementFrames;
            this.effectiveTrackedDisagreementFrames = effectiveTrackedDisagreementFrames;
            this.rawTargetNearRepresentativeFrames = rawTargetNearRepresentativeFrames;
            this.effectiveTrackedNearRepresentativeFrames = effectiveTrackedNearRepresentativeFrames;
            this.hypothesisNearRepresentativeFrames = hypothesisNearRepresentativeFrames;
            this.representativeCompetitionObservationCount = representativeCompetitionObservationCount;
            this.representativeCompetitionTrackedWinFrames = representativeCompetitionTrackedWinFrames;
            this.representativeCompetitionHypothesisWinFrames = representativeCompetitionHypothesisWinFrames;
            this.representativeCompetitionHypothesisMaxWinStreak = representativeCompetitionHypothesisMaxWinStreak;
            this.activeCenterCompetitionObservationCount = activeCenterCompetitionObservationCount;
            this.activeCenterCompetitionTrackedWinFrames = activeCenterCompetitionTrackedWinFrames;
            this.activeCenterCompetitionHypothesisWinFrames = activeCenterCompetitionHypothesisWinFrames;
            this.activeCenterCompetitionHypothesisMaxWinStreak = activeCenterCompetitionHypothesisMaxWinStreak;
            this.guardObservedFrames = guardObservedFrames;
            this.guardHoldFrames = guardHoldFrames;
            this.guardOpenFrames = guardOpenFrames;
            this.guardMidHoldFrames = guardMidHoldFrames;
            this.guardFarHoldFrames = guardFarHoldFrames;
            this.guardMaxRemainingScans = guardMaxRemainingScans;
            this.guardMaxObservedScans = guardMaxObservedScans;
            this.lastGuardCandidateFrequencyHz = lastGuardCandidateFrequencyHz;
            this.lastGuardDriftHz = lastGuardDriftHz;
            this.lastGuardRemainingScans = lastGuardRemainingScans;
            this.lastGuardBand = lastGuardBand == null ? "NONE" : lastGuardBand;
            this.trackedToneSplitSegments = trackedToneSplitSegments;
            this.rawConsensusOutlierSegments = rawConsensusOutlierSegments;
        }

        int observedHypothesisFrames() {
            return observedHypothesisFrames;
        }

        int rawTargetDisagreementFrames() {
            return rawTargetDisagreementFrames;
        }

        int effectiveTrackedDisagreementFrames() {
            return effectiveTrackedDisagreementFrames;
        }

        int rawTargetNearRepresentativeFrames() {
            return rawTargetNearRepresentativeFrames;
        }

        int effectiveTrackedNearRepresentativeFrames() {
            return effectiveTrackedNearRepresentativeFrames;
        }

        int hypothesisNearRepresentativeFrames() {
            return hypothesisNearRepresentativeFrames;
        }

        int representativeCompetitionObservationCount() {
            return representativeCompetitionObservationCount;
        }

        int representativeCompetitionTrackedWinFrames() {
            return representativeCompetitionTrackedWinFrames;
        }

        int representativeCompetitionHypothesisWinFrames() {
            return representativeCompetitionHypothesisWinFrames;
        }

        int representativeCompetitionHypothesisMaxWinStreak() {
            return representativeCompetitionHypothesisMaxWinStreak;
        }

        int activeCenterCompetitionObservationCount() {
            return activeCenterCompetitionObservationCount;
        }

        int activeCenterCompetitionTrackedWinFrames() {
            return activeCenterCompetitionTrackedWinFrames;
        }

        int activeCenterCompetitionHypothesisWinFrames() {
            return activeCenterCompetitionHypothesisWinFrames;
        }

        int activeCenterCompetitionHypothesisMaxWinStreak() {
            return activeCenterCompetitionHypothesisMaxWinStreak;
        }

        int guardObservedFrames() {
            return guardObservedFrames;
        }

        int guardHoldFrames() {
            return guardHoldFrames;
        }

        int guardOpenFrames() {
            return guardOpenFrames;
        }

        int guardMidHoldFrames() {
            return guardMidHoldFrames;
        }

        int guardFarHoldFrames() {
            return guardFarHoldFrames;
        }

        int guardMaxRemainingScans() {
            return guardMaxRemainingScans;
        }

        int guardMaxObservedScans() {
            return guardMaxObservedScans;
        }

        int lastGuardCandidateFrequencyHz() {
            return lastGuardCandidateFrequencyHz;
        }

        int lastGuardDriftHz() {
            return lastGuardDriftHz;
        }

        int lastGuardRemainingScans() {
            return lastGuardRemainingScans;
        }

        String lastGuardBand() {
            return lastGuardBand;
        }

        int trackedToneSplitSegmentCount() {
            return trackedToneSplitSegments.size();
        }

        int trackedToneSplitMaxFrames() {
            int maxFrames = 0;
            for (TrackedToneSplitSegment segment : trackedToneSplitSegments) {
                maxFrames = Math.max(maxFrames, segment.frameCount());
            }
            return maxFrames;
        }

        int rawConsensusOutlierSegmentCount() {
            return rawConsensusOutlierSegments.size();
        }

        int rawConsensusOutlierMaxFrames() {
            int maxFrames = 0;
            for (RawConsensusOutlierSegment segment : rawConsensusOutlierSegments) {
                maxFrames = Math.max(maxFrames, segment.frameCount());
            }
            return maxFrames;
        }

        String renderSummary() {
            String splitSummary = renderTrackedToneSplitSummary();
            String rawConsensusSummary = renderRawConsensusSummary();
            return String.format(
                    Locale.US,
                    "%s threshold=%dHz hypFrames=%d rawDisagree=%d effDisagree=%d rawNearRep=%d effNearRep=%d hypNearRep=%d repComp(obs=%d trk=%d hyp=%d maxHyp=%d) actComp(obs=%d trk=%d hyp=%d maxHyp=%d) guard(obs=%d hold=%d open=%d mid=%d far=%d last=%s cand=%dHz drift=%dHz remain=%d maxRemain=%d maxSeen=%d) %s %s",
                    sourceLabel,
                    disagreementThresholdHz,
                    observedHypothesisFrames,
                    rawTargetDisagreementFrames,
                    effectiveTrackedDisagreementFrames,
                    rawTargetNearRepresentativeFrames,
                    effectiveTrackedNearRepresentativeFrames,
                    hypothesisNearRepresentativeFrames,
                    representativeCompetitionObservationCount,
                    representativeCompetitionTrackedWinFrames,
                    representativeCompetitionHypothesisWinFrames,
                    representativeCompetitionHypothesisMaxWinStreak,
                    activeCenterCompetitionObservationCount,
                    activeCenterCompetitionTrackedWinFrames,
                    activeCenterCompetitionHypothesisWinFrames,
                    activeCenterCompetitionHypothesisMaxWinStreak,
                    guardObservedFrames,
                    guardHoldFrames,
                    guardOpenFrames,
                    guardMidHoldFrames,
                    guardFarHoldFrames,
                    lastGuardBand,
                    lastGuardCandidateFrequencyHz,
                    lastGuardDriftHz,
                    lastGuardRemainingScans,
                    guardMaxRemainingScans,
                    guardMaxObservedScans,
                    splitSummary,
                    rawConsensusSummary
            );
        }

        private String renderTrackedToneSplitSummary() {
            if (trackedToneSplitSegments.isEmpty()) {
                return "trkSplit(seg=0)";
            }
            ArrayList<TrackedToneSplitSegment> sortedSegments = new ArrayList<>(trackedToneSplitSegments);
            sortedSegments.sort(Comparator.comparingInt(TrackedToneSplitSegment::frameCount).reversed());
            StringBuilder builder = new StringBuilder();
            builder.append("trkSplit(seg=").append(trackedToneSplitSegments.size())
                    .append(" maxFrames=").append(trackedToneSplitMaxFrames())
                    .append(" top=");
            int limit = Math.min(3, sortedSegments.size());
            for (int index = 0; index < limit; index++) {
                if (index > 0) {
                    builder.append(";");
                }
                builder.append(sortedSegments.get(index).renderSummary());
            }
            builder.append(")");
            return builder.toString();
        }

        private String renderRawConsensusSummary() {
            if (rawConsensusOutlierSegments.isEmpty()) {
                return "rawConsensus(seg=0)";
            }
            ArrayList<RawConsensusOutlierSegment> sortedSegments = new ArrayList<>(rawConsensusOutlierSegments);
            sortedSegments.sort(Comparator.comparingInt(RawConsensusOutlierSegment::frameCount).reversed());
            StringBuilder builder = new StringBuilder();
            builder.append("rawConsensus(seg=").append(rawConsensusOutlierSegments.size())
                    .append(" maxFrames=").append(rawConsensusOutlierMaxFrames())
                    .append(" top=");
            int limit = Math.min(3, sortedSegments.size());
            for (int index = 0; index < limit; index++) {
                if (index > 0) {
                    builder.append(";");
                }
                builder.append(sortedSegments.get(index).renderSummary());
            }
            builder.append(")");
            return builder.toString();
        }
    }

    static final class TrackedToneSplitSegment {
        private final long startTimestampMs;
        private final long endTimestampMs;
        private final int frameCount;
        private final int averageRawToneHz;
        private final int averageEffectiveToneHz;
        private final int averageHypothesisToneHz;
        private final int averageRepresentativeToneHz;
        private final int averageActiveCenterToneHz;
        private final int lastRawToneHz;
        private final int lastEffectiveToneHz;
        private final int lastHypothesisToneHz;
        private final int lastRepresentativeToneHz;
        private final int lastActiveCenterToneHz;

        private TrackedToneSplitSegment(
                long startTimestampMs,
                long endTimestampMs,
                int frameCount,
                int averageRawToneHz,
                int averageEffectiveToneHz,
                int averageHypothesisToneHz,
                int averageRepresentativeToneHz,
                int averageActiveCenterToneHz,
                int lastRawToneHz,
                int lastEffectiveToneHz,
                int lastHypothesisToneHz,
                int lastRepresentativeToneHz,
                int lastActiveCenterToneHz
        ) {
            this.startTimestampMs = startTimestampMs;
            this.endTimestampMs = endTimestampMs;
            this.frameCount = frameCount;
            this.averageRawToneHz = averageRawToneHz;
            this.averageEffectiveToneHz = averageEffectiveToneHz;
            this.averageHypothesisToneHz = averageHypothesisToneHz;
            this.averageRepresentativeToneHz = averageRepresentativeToneHz;
            this.averageActiveCenterToneHz = averageActiveCenterToneHz;
            this.lastRawToneHz = lastRawToneHz;
            this.lastEffectiveToneHz = lastEffectiveToneHz;
            this.lastHypothesisToneHz = lastHypothesisToneHz;
            this.lastRepresentativeToneHz = lastRepresentativeToneHz;
            this.lastActiveCenterToneHz = lastActiveCenterToneHz;
        }

        int frameCount() {
            return frameCount;
        }

        String renderSummary() {
            return String.format(
                    Locale.US,
                    "%d-%dms/%df raw=%d eff=%d hyp=%d rep=%d act=%d last=%d/%d/%d/%d/%d",
                    startTimestampMs,
                    endTimestampMs,
                    frameCount,
                    averageRawToneHz,
                    averageEffectiveToneHz,
                    averageHypothesisToneHz,
                    averageRepresentativeToneHz,
                    averageActiveCenterToneHz,
                    lastRawToneHz,
                    lastEffectiveToneHz,
                    lastHypothesisToneHz,
                    lastRepresentativeToneHz,
                    lastActiveCenterToneHz
            );
        }
    }

    static final class RawConsensusOutlierSegment {
        private final long startTimestampMs;
        private final long endTimestampMs;
        private final int frameCount;
        private final int averageConsensusToneHz;
        private final int averageRawToneHz;
        private final int averageEffectiveToneHz;
        private final int averageHypothesisToneHz;
        private final int averageRepresentativeToneHz;
        private final int averageActiveCenterToneHz;
        private final int lastConsensusToneHz;
        private final int lastRawToneHz;
        private final int lastEffectiveToneHz;
        private final int lastHypothesisToneHz;
        private final int lastRepresentativeToneHz;
        private final int lastActiveCenterToneHz;

        private RawConsensusOutlierSegment(
                long startTimestampMs,
                long endTimestampMs,
                int frameCount,
                int averageConsensusToneHz,
                int averageRawToneHz,
                int averageEffectiveToneHz,
                int averageHypothesisToneHz,
                int averageRepresentativeToneHz,
                int averageActiveCenterToneHz,
                int lastConsensusToneHz,
                int lastRawToneHz,
                int lastEffectiveToneHz,
                int lastHypothesisToneHz,
                int lastRepresentativeToneHz,
                int lastActiveCenterToneHz
        ) {
            this.startTimestampMs = startTimestampMs;
            this.endTimestampMs = endTimestampMs;
            this.frameCount = frameCount;
            this.averageConsensusToneHz = averageConsensusToneHz;
            this.averageRawToneHz = averageRawToneHz;
            this.averageEffectiveToneHz = averageEffectiveToneHz;
            this.averageHypothesisToneHz = averageHypothesisToneHz;
            this.averageRepresentativeToneHz = averageRepresentativeToneHz;
            this.averageActiveCenterToneHz = averageActiveCenterToneHz;
            this.lastConsensusToneHz = lastConsensusToneHz;
            this.lastRawToneHz = lastRawToneHz;
            this.lastEffectiveToneHz = lastEffectiveToneHz;
            this.lastHypothesisToneHz = lastHypothesisToneHz;
            this.lastRepresentativeToneHz = lastRepresentativeToneHz;
            this.lastActiveCenterToneHz = lastActiveCenterToneHz;
        }

        int frameCount() {
            return frameCount;
        }

        String renderSummary() {
            return String.format(
                    Locale.US,
                    "%d-%dms/%df cns=%d raw=%d eff=%d hyp=%d rep=%d act=%d last=%d/%d/%d/%d/%d/%d",
                    startTimestampMs,
                    endTimestampMs,
                    frameCount,
                    averageConsensusToneHz,
                    averageRawToneHz,
                    averageEffectiveToneHz,
                    averageHypothesisToneHz,
                    averageRepresentativeToneHz,
                    averageActiveCenterToneHz,
                    lastConsensusToneHz,
                    lastRawToneHz,
                    lastEffectiveToneHz,
                    lastHypothesisToneHz,
                    lastRepresentativeToneHz,
                    lastActiveCenterToneHz
            );
        }
    }

    static final class TrailingWindowRedecodeResult {
        private final int trailingWordCount;
        private final long windowStartTimestampMs;
        private final String decodedText;
        private final CwTimingSnapshot timingSnapshot;
        private final CwDecoderSnapshot decoderSnapshot;
        private final CwInterpreterSnapshot interpreterSnapshot;
        private final List<CwTimingEvent> timingEvents;
        private final List<CwDecodeEvent> decodeEvents;

        private TrailingWindowRedecodeResult(
                int trailingWordCount,
                long windowStartTimestampMs,
                String decodedText,
                CwTimingSnapshot timingSnapshot,
                CwDecoderSnapshot decoderSnapshot,
                CwInterpreterSnapshot interpreterSnapshot,
                List<CwTimingEvent> timingEvents,
                List<CwDecodeEvent> decodeEvents
        ) {
            this.trailingWordCount = trailingWordCount;
            this.windowStartTimestampMs = windowStartTimestampMs;
            this.decodedText = decodedText;
            this.timingSnapshot = timingSnapshot;
            this.decoderSnapshot = decoderSnapshot;
            this.interpreterSnapshot = interpreterSnapshot;
            this.timingEvents = timingEvents;
            this.decodeEvents = decodeEvents;
        }

        int trailingWordCount() {
            return trailingWordCount;
        }

        long windowStartTimestampMs() {
            return windowStartTimestampMs;
        }

        String decodedText() {
            return decodedText;
        }

        CwTimingSnapshot timingSnapshot() {
            return timingSnapshot;
        }

        CwDecoderSnapshot decoderSnapshot() {
            return decoderSnapshot;
        }

        CwInterpreterSnapshot interpreterSnapshot() {
            return interpreterSnapshot;
        }

        List<CwTimingEvent> timingEvents() {
            return timingEvents;
        }

        List<CwDecodeEvent> decodeEvents() {
            return decodeEvents;
        }
    }

    static final class ForcedToneReplayResult {
        private final String sourceLabel;
        private final String mode;
        private final int lastForcedFrequencyHz;
        private final String decodedText;
        private final List<CwToneEvent> toneEvents;
        private final List<CwTimingEvent> timingEvents;
        private final List<CwDecodeEvent> decodeEvents;
        private final List<FrameSignalTrace> frameSignalTraces;
        private final CwDecoderSnapshot decoderSnapshot;
        private final CwInterpreterSnapshot interpreterSnapshot;

        private ForcedToneReplayResult(
                String sourceLabel,
                String mode,
                int lastForcedFrequencyHz,
                String decodedText,
                List<CwToneEvent> toneEvents,
                List<CwTimingEvent> timingEvents,
                List<CwDecodeEvent> decodeEvents,
                List<FrameSignalTrace> frameSignalTraces,
                CwDecoderSnapshot decoderSnapshot,
                CwInterpreterSnapshot interpreterSnapshot
        ) {
            this.sourceLabel = sourceLabel;
            this.mode = mode;
            this.lastForcedFrequencyHz = lastForcedFrequencyHz;
            this.decodedText = decodedText;
            this.toneEvents = toneEvents;
            this.timingEvents = timingEvents;
            this.decodeEvents = decodeEvents;
            this.frameSignalTraces = frameSignalTraces;
            this.decoderSnapshot = decoderSnapshot;
            this.interpreterSnapshot = interpreterSnapshot;
        }

        String renderSummary() {
            return String.format(
                    Locale.US,
                    "%s %s forcedLast=%dHz text=%s tone=%d timing=%d decode=%d",
                    sourceLabel,
                    mode,
                    lastForcedFrequencyHz,
                    decodedText,
                    toneEvents.size(),
                    timingEvents.size(),
                    decodeEvents.size()
            );
        }

        String decodedText() {
            return decodedText;
        }

        String sourceLabel() {
            return sourceLabel;
        }

        String mode() {
            return mode;
        }

        int lastForcedFrequencyHz() {
            return lastForcedFrequencyHz;
        }

        List<CwToneEvent> toneEvents() {
            return toneEvents;
        }

        List<CwTimingEvent> timingEvents() {
            return timingEvents;
        }

        List<CwDecodeEvent> decodeEvents() {
            return decodeEvents;
        }

        List<FrameSignalTrace> frameSignalTraces() {
            return frameSignalTraces;
        }

        CwDecoderSnapshot decoderSnapshot() {
            return decoderSnapshot;
        }

        CwInterpreterSnapshot interpreterSnapshot() {
            return interpreterSnapshot;
        }
    }
}
