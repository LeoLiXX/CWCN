package org.bi9clt.cwcn.core.rx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.bi9clt.cwcn.core.audio.AudioFrame;
import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class RxTurnSessionCoordinatorTest {
    private static final long DOT_MS = 60L;

    @Test
    public void observeStartsTurnAndBeginsTurnScopedControllers() {
        CwSignalProcessor signalProcessor = new CwSignalProcessor();
        CwHybridTimingModel timingModel = new CwHybridTimingModel();
        LiveRxWpmGuard wpmGuard = new LiveRxWpmGuard();
        RxTurnController turnController = new RxTurnController();
        turnController.setTxSeedWpm(22);
        TimingAnchorController timingAnchorController = new TimingAnchorController();
        RxRawCommitGate rawCommitGate = new RxRawCommitGate();
        RxTurnTailRepairController turnTailRepairController = new RxTurnTailRepairController();
        RxTurnSessionFinalizer turnSessionFinalizer = new RxTurnSessionFinalizer(
                turnTailRepairController,
                null
        );
        RxTurnSessionCoordinator coordinator = new RxTurnSessionCoordinator(
                signalProcessor,
                timingModel,
                wpmGuard,
                turnController,
                timingAnchorController,
                rawCommitGate,
                turnSessionFinalizer,
                null,
                null
        );

        RxTurnSessionCoordinator.Observation observation = coordinator.observe(
                startSignalSnapshot(),
                false,
                1000L,
                0
        );

        assertTrue(observation.startedNewTurn());
        assertEquals(22, observation.turnSeedWpm());
        assertEquals(RxTurnController.Phase.ACTIVE, turnController.phase());
        assertEquals(22, timingModel.debugSnapshot().seedWpm());
        assertEquals("turn-reset", timingModel.debugSnapshot().lastResetReason());
        assertEquals(22, wpmGuard.resolveReferenceWpm(timingModel.rawSnapshot()));
        assertTrue(turnTailRepairController.turnActive());
        assertEquals(0, rawCommitGate.pendingFinalEventCount());
    }

    @Test
    public void observeEndsTurnResetsFrontEndAndClosesTurnScopedControllers() {
        CwSignalProcessor signalProcessor = new CwSignalProcessor();
        CwHybridTimingModel timingModel = new CwHybridTimingModel();
        LiveRxWpmGuard wpmGuard = new LiveRxWpmGuard();
        RxTurnController turnController = new RxTurnController();
        turnController.setTxSeedWpm(22);
        TimingAnchorController timingAnchorController = new TimingAnchorController();
        RxRawCommitGate rawCommitGate = new RxRawCommitGate();
        RxTurnTailRepairController turnTailRepairController = new RxTurnTailRepairController();
        RxTurnSessionFinalizer turnSessionFinalizer = new RxTurnSessionFinalizer(
                turnTailRepairController,
                null
        );
        RxTurnSessionCoordinator coordinator = new RxTurnSessionCoordinator(
                signalProcessor,
                timingModel,
                wpmGuard,
                turnController,
                timingAnchorController,
                rawCommitGate,
                turnSessionFinalizer,
                null,
                null
        );

        coordinator.observe(startSignalSnapshot(), false, 1000L, 0);
        signalProcessor.process(silentFrame(1200L));
        assertTrue(signalProcessor.snapshot().processedFrameCount() > 0);
        rawCommitGate.admit(
                decodedCharacterEvent(1500L, "A"),
                false,
                TimingAnchorController.TrustOrigin.NONE,
                0L,
                -1L
        );
        assertEquals(1, rawCommitGate.pendingFinalEventCount());
        turnSessionFinalizer.noteToneEvent(new CwToneEvent(
                CwToneEvent.Type.TONE_ON,
                1600L,
                4000,
                3200.0d,
                80L
        ));
        assertTrue(turnTailRepairController.turnActive());

        RxTurnSessionCoordinator.Observation observation = coordinator.observe(
                endSignalSnapshotWithFrontEndReset(),
                false,
                3600L,
                0
        );

        assertTrue(observation.endedTurn());
        assertTrue(observation.frontEndResetApplied());
        assertEquals(RxTurnController.Phase.IDLE, turnController.phase());
        assertEquals(0, signalProcessor.snapshot().processedFrameCount());
        assertEquals(0, rawCommitGate.pendingFinalEventCount());
        assertFalse(turnTailRepairController.turnActive());
    }

    @Test
    public void observeEndsTurnAppliesSharedTailRepairBeforeClosingTurnScopedControllers() {
        CwSignalProcessor signalProcessor = new CwSignalProcessor();
        CwHybridTimingModel timingModel = new CwHybridTimingModel();
        LiveRxWpmGuard wpmGuard = new LiveRxWpmGuard();
        RxTurnController turnController = new RxTurnController();
        turnController.setTxSeedWpm(18);
        TimingAnchorController timingAnchorController = new TimingAnchorController();
        RxRawCommitGate rawCommitGate = new RxRawCommitGate();
        RxTurnTailRepairController turnTailRepairController = new RxTurnTailRepairController();
        RxCommittedOutputController committedOutputController = new RxCommittedOutputController(
                new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS),
                null,
                null,
                null,
                null
        );
        RxTurnSessionFinalizer turnSessionFinalizer = new RxTurnSessionFinalizer(
                turnTailRepairController,
                committedOutputController
        );
        RxTurnSessionCoordinator coordinator = new RxTurnSessionCoordinator(
                signalProcessor,
                timingModel,
                wpmGuard,
                turnController,
                timingAnchorController,
                rawCommitGate,
                turnSessionFinalizer,
                null,
                null
        );

        coordinator.observe(startSignalSnapshot(), false, 1000L, 0);
        turnSessionFinalizer.processCommittedDecodeEvents(buildFinalEvents("", "CQ", "DE", "HI", "?"));
        noteToneEvents(turnSessionFinalizer, encodeTextAsToneEvents("HI SK", 1600L, DOT_MS));

        RxTurnSessionCoordinator.Observation observation = coordinator.observe(
                endSignalSnapshotWithFrontEndReset(),
                false,
                3600L,
                0
        );

        assertTrue(observation.endedTurn());
        assertTrue(observation.tailRepairApplied());
        assertNotNull(observation.turnFinalization());
        assertEquals("HI ?", observation.turnFinalization().repairResult().baseTailText());
        assertEquals("HI SK", observation.turnFinalization().repairResult().repairedTailText());
        assertEquals(
                "CQ DE HI SK",
                committedOutputController.rawSnapshot() == null
                        ? ""
                        : committedOutputController.rawSnapshot().rawText().trim()
        );
        assertFalse(turnSessionFinalizer.turnActive());
    }

    @Test
    public void observeInvokesTurnEndListenerBeforeTurnTailStateClears() {
        CwSignalProcessor signalProcessor = new CwSignalProcessor();
        CwHybridTimingModel timingModel = new CwHybridTimingModel();
        LiveRxWpmGuard wpmGuard = new LiveRxWpmGuard();
        RxTurnController turnController = new RxTurnController();
        turnController.setTxSeedWpm(18);
        TimingAnchorController timingAnchorController = new TimingAnchorController();
        RxRawCommitGate rawCommitGate = new RxRawCommitGate();
        RxTurnTailRepairController turnTailRepairController = new RxTurnTailRepairController();
        RxTurnSessionFinalizer turnSessionFinalizer = new RxTurnSessionFinalizer(
                turnTailRepairController,
                null
        );
        final boolean[] sawTurnActiveInsideListener = {false};
        RxTurnSessionCoordinator coordinator = new RxTurnSessionCoordinator(
                signalProcessor,
                timingModel,
                wpmGuard,
                turnController,
                timingAnchorController,
                rawCommitGate,
                turnSessionFinalizer,
                null,
                (transition, timestampMs) -> sawTurnActiveInsideListener[0] =
                        transition.endedTurn() && turnTailRepairController.turnActive()
        );

        coordinator.observe(startSignalSnapshot(), false, 1000L, 0);
        assertTrue(turnTailRepairController.turnActive());

        RxTurnSessionCoordinator.Observation observation = coordinator.observe(
                endSignalSnapshotWithFrontEndReset(),
                false,
                3600L,
                0
        );

        assertTrue(observation.endedTurn());
        assertTrue(sawTurnActiveInsideListener[0]);
        assertFalse(turnTailRepairController.turnActive());
    }

    @Test
    public void bootstrapNoiseWithoutCommittedDecodeDoesNotHoldTurnOpen() {
        CwSignalProcessor signalProcessor = new CwSignalProcessor();
        CwHybridTimingModel timingModel = new CwHybridTimingModel();
        LiveRxWpmGuard wpmGuard = new LiveRxWpmGuard();
        RxTurnController turnController = new RxTurnController();
        turnController.setTxSeedWpm(18);
        TimingAnchorController timingAnchorController = new TimingAnchorController();
        RxRawCommitGate rawCommitGate = new RxRawCommitGate();
        RxTurnTailRepairController turnTailRepairController = new RxTurnTailRepairController();
        RxTurnSessionFinalizer turnSessionFinalizer = new RxTurnSessionFinalizer(
                turnTailRepairController,
                null
        );
        RxTurnSessionCoordinator coordinator = new RxTurnSessionCoordinator(
                signalProcessor,
                timingModel,
                wpmGuard,
                turnController,
                timingAnchorController,
                rawCommitGate,
                turnSessionFinalizer,
                null,
                null
        );

        coordinator.observe(startSignalSnapshot(), false, 1000L, 0);

        RxTurnSessionCoordinator.Observation observation = coordinator.observe(
                weakLockedNoiseSnapshot(),
                false,
                3600L,
                0
        );

        assertTrue(observation.endedTurn());
        assertEquals(RxTurnController.Phase.IDLE, turnController.phase());
    }

    @Test
    public void committedDecodeWeakLockedNoiseDoesNotHoldTurnOpen() {
        CwSignalProcessor signalProcessor = new CwSignalProcessor();
        CwHybridTimingModel timingModel = new CwHybridTimingModel();
        LiveRxWpmGuard wpmGuard = new LiveRxWpmGuard();
        RxTurnController turnController = new RxTurnController();
        turnController.setTxSeedWpm(18);
        TimingAnchorController timingAnchorController = new TimingAnchorController();
        RxRawCommitGate rawCommitGate = new RxRawCommitGate();
        RxTurnTailRepairController turnTailRepairController = new RxTurnTailRepairController();
        RxTurnSessionFinalizer turnSessionFinalizer = new RxTurnSessionFinalizer(
                turnTailRepairController,
                null
        );
        RxTurnSessionCoordinator coordinator = new RxTurnSessionCoordinator(
                signalProcessor,
                timingModel,
                wpmGuard,
                turnController,
                timingAnchorController,
                rawCommitGate,
                turnSessionFinalizer,
                null,
                null
        );

        coordinator.observe(startSignalSnapshot(), false, 1000L, 0);
        turnSessionFinalizer.processCommittedDecodeEvent(decodedCharacterEvent(1400L, "C"));

        RxTurnSessionCoordinator.Observation observation = coordinator.observe(
                weakLockedNoiseSnapshot(),
                false,
                3600L,
                0
        );

        assertTrue(observation.endedTurn());
        assertEquals(RxTurnController.Phase.IDLE, turnController.phase());
    }

    @Test
    public void weakLockedNoiseDoesNotImmediatelyRestartAfterCommittedTurnEnds() {
        CwSignalProcessor signalProcessor = new CwSignalProcessor();
        CwHybridTimingModel timingModel = new CwHybridTimingModel();
        LiveRxWpmGuard wpmGuard = new LiveRxWpmGuard();
        RxTurnController turnController = new RxTurnController();
        turnController.setTxSeedWpm(18);
        TimingAnchorController timingAnchorController = new TimingAnchorController();
        RxRawCommitGate rawCommitGate = new RxRawCommitGate();
        RxTurnTailRepairController turnTailRepairController = new RxTurnTailRepairController();
        RxTurnSessionFinalizer turnSessionFinalizer = new RxTurnSessionFinalizer(
                turnTailRepairController,
                null
        );
        RxTurnSessionCoordinator coordinator = new RxTurnSessionCoordinator(
                signalProcessor,
                timingModel,
                wpmGuard,
                turnController,
                timingAnchorController,
                rawCommitGate,
                turnSessionFinalizer,
                null,
                null
        );

        coordinator.observe(startSignalSnapshot(), false, 1000L, 0);
        turnSessionFinalizer.processCommittedDecodeEvent(decodedCharacterEvent(1400L, "C"));

        RxTurnSessionCoordinator.Observation endObservation = coordinator.observe(
                weakLockedNoiseSnapshot(),
                false,
                3600L,
                0
        );
        RxTurnSessionCoordinator.Observation restartObservation = coordinator.observe(
                weakLockedNoiseSnapshot(),
                false,
                3700L,
                0
        );

        assertTrue(endObservation.endedTurn());
        assertFalse(restartObservation.startedNewTurn());
        assertFalse(restartObservation.endedTurn());
        assertEquals(RxTurnController.Phase.IDLE, turnController.phase());
    }

    @Test
    public void committedDecodeKeepsStrongLockedContinuationEligible() {
        CwSignalProcessor signalProcessor = new CwSignalProcessor();
        CwHybridTimingModel timingModel = new CwHybridTimingModel();
        LiveRxWpmGuard wpmGuard = new LiveRxWpmGuard();
        RxTurnController turnController = new RxTurnController();
        turnController.setTxSeedWpm(18);
        TimingAnchorController timingAnchorController = new TimingAnchorController();
        RxRawCommitGate rawCommitGate = new RxRawCommitGate();
        RxTurnTailRepairController turnTailRepairController = new RxTurnTailRepairController();
        RxTurnSessionFinalizer turnSessionFinalizer = new RxTurnSessionFinalizer(
                turnTailRepairController,
                null
        );
        RxTurnSessionCoordinator coordinator = new RxTurnSessionCoordinator(
                signalProcessor,
                timingModel,
                wpmGuard,
                turnController,
                timingAnchorController,
                rawCommitGate,
                turnSessionFinalizer,
                null,
                null
        );

        coordinator.observe(startSignalSnapshot(), false, 1000L, 0);
        turnSessionFinalizer.processCommittedDecodeEvent(decodedCharacterEvent(1400L, "C"));

        RxTurnSessionCoordinator.Observation observation = coordinator.observe(
                strongLockedSignalSnapshot(),
                false,
                3600L,
                0
        );

        assertFalse(observation.endedTurn());
        assertFalse(observation.startedNewTurn());
        assertEquals(RxTurnController.Phase.ACTIVE, turnController.phase());
    }

    @Test
    public void strongLockedSignalCanRestartAfterPriorTurnEnds() {
        CwSignalProcessor signalProcessor = new CwSignalProcessor();
        CwHybridTimingModel timingModel = new CwHybridTimingModel();
        LiveRxWpmGuard wpmGuard = new LiveRxWpmGuard();
        RxTurnController turnController = new RxTurnController();
        turnController.setTxSeedWpm(18);
        TimingAnchorController timingAnchorController = new TimingAnchorController();
        RxRawCommitGate rawCommitGate = new RxRawCommitGate();
        RxTurnTailRepairController turnTailRepairController = new RxTurnTailRepairController();
        RxTurnSessionFinalizer turnSessionFinalizer = new RxTurnSessionFinalizer(
                turnTailRepairController,
                null
        );
        RxTurnSessionCoordinator coordinator = new RxTurnSessionCoordinator(
                signalProcessor,
                timingModel,
                wpmGuard,
                turnController,
                timingAnchorController,
                rawCommitGate,
                turnSessionFinalizer,
                null,
                null
        );

        coordinator.observe(startSignalSnapshot(), false, 1000L, 0);
        turnSessionFinalizer.processCommittedDecodeEvent(decodedCharacterEvent(1400L, "C"));
        RxTurnSessionCoordinator.Observation endObservation = coordinator.observe(
                weakLockedNoiseSnapshot(),
                false,
                3600L,
                0
        );

        RxTurnSessionCoordinator.Observation restartObservation = coordinator.observe(
                strongLockedSignalSnapshot(),
                false,
                3700L,
                0
        );

        assertTrue(endObservation.endedTurn());
        assertTrue(restartObservation.startedNewTurn());
        assertEquals(RxTurnController.Phase.ACTIVE, turnController.phase());
    }

    private static CwSignalSnapshot startSignalSnapshot() {
        return signalSnapshot(
                true,
                true,
                24,
                4,
                4,
                4,
                3,
                3,
                0.60d,
                0.68d
        );
    }

    private static CwSignalSnapshot endSignalSnapshotWithFrontEndReset() {
        return signalSnapshot(
                false,
                false,
                12,
                0,
                4,
                4,
                3,
                3,
                0.0d,
                0.0d
        );
    }

    private static CwSignalSnapshot weakLockedNoiseSnapshot() {
        return signalSnapshot(
                true,
                true,
                4,
                1,
                1,
                1,
                0,
                0,
                0.14d,
                0.20d
        );
    }

    private static CwSignalSnapshot strongLockedSignalSnapshot() {
        return signalSnapshot(
                true,
                true,
                18,
                4,
                5,
                4,
                3,
                3,
                0.34d,
                0.38d
        );
    }

    private static CwSignalSnapshot signalSnapshot(
            boolean toneActive,
            boolean targetToneLocked,
            int lockedFrameCount,
            int consecutiveLockedFrames,
            int maxConsecutiveLockedFrames,
            int representativeLockedToneFrameCount,
            int totalToneOnEvents,
            int totalToneOffEvents,
            double toneDominanceRatio,
            double narrowbandIsolationRatio
    ) {
        char[] recentHistory = targetToneLocked
                ? new char[] {'L', 'L', 'L', 'L'}
                : toneActive
                ? new char[] {'u', 'u', 'u', 'u'}
                : new char[] {'-', '-', '-', '-'};
        int[] recentOffsets = new int[recentHistory.length];
        return new CwSignalSnapshot(
                recentHistory.length,
                recentHistory,
                recentOffsets,
                toneActive,
                targetToneLocked,
                700,
                700,
                700,
                Math.max(1, representativeLockedToneFrameCount),
                1200,
                800,
                600,
                1800,
                7800.0d,
                toneActive ? 6200.0d : 0.0d,
                1100.0d,
                toneDominanceRatio,
                narrowbandIsolationRatio,
                7800.0d,
                narrowbandIsolationRatio,
                100,
                lockedFrameCount,
                toneActive ? 100 : 0,
                toneActive && !targetToneLocked ? 100 : 0,
                consecutiveLockedFrames,
                maxConsecutiveLockedFrames,
                toneActive && !targetToneLocked ? 3 : 0,
                toneActive && !targetToneLocked ? 3 : 0,
                700,
                0,
                700,
                0,
                700,
                700,
                7000.0d,
                0.0d,
                7800.0d,
                7800.0d,
                6500.0d,
                0.0d,
                7800.0d,
                7800.0d,
                targetToneLocked,
                false,
                targetToneLocked,
                targetToneLocked,
                "PREFERRED_WINDOW",
                targetToneLocked ? "LOCKED_RETUNE" : "SEARCH",
                totalToneOnEvents,
                totalToneOffEvents,
                0,
                0L,
                0L,
                0L,
                -1L,
                null
        );
    }

    private static AudioFrame silentFrame(long capturedAtMs) {
        return new AudioFrame(new short[160], 8000, 1, 0, 0.0d, capturedAtMs);
    }

    private static void noteToneEvents(
            RxTurnSessionFinalizer turnSessionFinalizer,
            List<CwToneEvent> toneEvents
    ) {
        for (CwToneEvent toneEvent : toneEvents) {
            turnSessionFinalizer.noteToneEvent(toneEvent);
        }
    }

    private static CwDecodeEvent decodedCharacterEvent(long timestampMs, String emittedValue) {
        return new CwDecodeEvent(
                CwDecodeEvent.Type.CHARACTER_DECODED,
                timestampMs,
                ".-",
                emittedValue,
                emittedValue,
                ".-",
                false
        );
    }

    private static List<CwDecodeEvent> buildFinalEvents(String initialOutputText, String... words) {
        ArrayList<CwDecodeEvent> decodeEvents = new ArrayList<>();
        long timestampMs = 100L;
        StringBuilder outputText = new StringBuilder(initialOutputText == null ? "" : initialOutputText);
        for (int wordIndex = 0; wordIndex < words.length; wordIndex++) {
            String word = words[wordIndex];
            for (int charIndex = 0; charIndex < word.length(); charIndex++) {
                char ch = word.charAt(charIndex);
                outputText.append(ch);
                boolean unknownCharacter = ch == '?';
                decodeEvents.add(new CwDecodeEvent(
                        CwDecodeEvent.Type.CHARACTER_DECODED,
                        timestampMs,
                        "",
                        outputText.toString(),
                        String.valueOf(ch),
                        unknownCharacter ? "..--.." : "",
                        unknownCharacter
                ));
                timestampMs += 100L;
            }
            if (wordIndex < words.length - 1) {
                outputText.append(' ');
                decodeEvents.add(new CwDecodeEvent(
                        CwDecodeEvent.Type.WORD_BREAK,
                        timestampMs,
                        "",
                        outputText.toString(),
                        " ",
                        "",
                        false
                ));
                timestampMs += 50L;
            }
        }
        return decodeEvents;
    }

    private static List<CwToneEvent> encodeTextAsToneEvents(String text, long startTimestampMs, long dotMs) {
        ArrayList<CwToneEvent> toneEvents = new ArrayList<>();
        long cursorMs = startTimestampMs;
        String[] words = text.trim().split("\\s+");
        for (int wordIndex = 0; wordIndex < words.length; wordIndex++) {
            char[] characters = words[wordIndex].toCharArray();
            for (int charIndex = 0; charIndex < characters.length; charIndex++) {
                String pattern = morsePatternFor(characters[charIndex]);
                for (int symbolIndex = 0; symbolIndex < pattern.length(); symbolIndex++) {
                    toneEvents.add(toneOn(cursorMs));
                    long toneDurationMs = pattern.charAt(symbolIndex) == '-' ? dotMs * 3L : dotMs;
                    cursorMs += toneDurationMs;
                    toneEvents.add(toneOff(cursorMs, toneDurationMs));
                    if (symbolIndex < pattern.length() - 1) {
                        cursorMs += dotMs;
                    }
                }
                if (charIndex < characters.length - 1) {
                    cursorMs += dotMs * 3L;
                }
            }
            if (wordIndex < words.length - 1) {
                cursorMs += dotMs * 7L;
            }
        }
        return toneEvents;
    }

    private static CwToneEvent toneOn(long timestampMs) {
        return new CwToneEvent(CwToneEvent.Type.TONE_ON, timestampMs, 16000, 12000.0d, 0L);
    }

    private static CwToneEvent toneOff(long timestampMs, long durationMs) {
        return new CwToneEvent(CwToneEvent.Type.TONE_OFF, timestampMs, 16000, 12000.0d, durationMs);
    }

    private static String morsePatternFor(char ch) {
        switch (Character.toUpperCase(ch)) {
            case 'D':
                return "-..";
            case 'E':
                return ".";
            case 'H':
                return "....";
            case 'I':
                return "..";
            case 'K':
                return "-.-";
            case 'S':
                return "...";
            case 'Q':
                return "--.-";
            case 'C':
                return "-.-.";
            default:
                throw new IllegalArgumentException("Unsupported test character: " + ch);
        }
    }
}
