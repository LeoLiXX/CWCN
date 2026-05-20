package org.bi9clt.cwcn.core.rx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;
import org.bi9clt.cwcn.core.timing.CwHybridTimingModel;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class RxReplayTurnSessionControllerTest {
    private static final long DOT_MS = 60L;

    @Test
    public void observeTracksTurnTransitionsBuildsWindowsAndCountsTailRepair() {
        CwSignalProcessor signalProcessor = new CwSignalProcessor();
        CwHybridTimingModel timingModel = new CwHybridTimingModel();
        LiveRxWpmGuard wpmGuard = new LiveRxWpmGuard();
        RxTurnController turnController = new RxTurnController();
        turnController.setTxSeedWpm(18);
        TimingAnchorController timingAnchorController = new TimingAnchorController();
        RxRawCommitGate rawCommitGate = new RxRawCommitGate();
        RxCommittedOutputController committedOutputController = new RxCommittedOutputController(
                new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS),
                null,
                null,
                null,
                rawCommitGate
        );
        RxTurnSessionFinalizer turnSessionFinalizer = new RxTurnSessionFinalizer(
                new RxTurnTailRepairController(),
                committedOutputController
        );
        RxTurnSessionCoordinator turnSessionCoordinator = new RxTurnSessionCoordinator(
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
        RxReplayTurnSessionController replayTurnSessionController =
                new RxReplayTurnSessionController(
                        signalProcessor,
                        timingModel,
                        null,
                        wpmGuard,
                        turnController,
                        turnSessionCoordinator,
                        turnSessionFinalizer
                );

        replayTurnSessionController.observe(startSignalSnapshot(), 1000L);
        replayTurnSessionController.processCommittedDecodeEvents(buildFinalEvents("", "CQ", "DE", "HI", "?"));
        noteToneEvents(replayTurnSessionController, encodeTextAsToneEvents("HI SK", 1600L, DOT_MS));
        replayTurnSessionController.observe(endSignalSnapshotWithFrontEndReset(), 3600L);

        assertEquals(1, replayTurnSessionController.turnCount());
        assertEquals(1, replayTurnSessionController.tailRepairCount());
        assertEquals(2, replayTurnSessionController.transitionTracesSnapshot().size());
        assertEquals(1, replayTurnSessionController.turnWindowsSnapshot(4000L).size());
        RxReplayTurnSessionController.TurnWindow turnWindow =
                replayTurnSessionController.turnWindowsSnapshot(4000L).get(0);
        assertEquals(1000L, turnWindow.turnStartTimestampMs());
        assertEquals(3600L, turnWindow.turnEndTimestampMs());
        assertEquals(
                "CQ DE HI SK",
                committedOutputController.rawSnapshot() == null
                        ? ""
                        : committedOutputController.rawSnapshot().rawText().trim()
        );
    }

    @Test
    public void finalizeAtStopRepairsActiveTurnWithoutNaturalTurnEnd() {
        CwSignalProcessor signalProcessor = new CwSignalProcessor();
        CwHybridTimingModel timingModel = new CwHybridTimingModel();
        LiveRxWpmGuard wpmGuard = new LiveRxWpmGuard();
        RxTurnController turnController = new RxTurnController();
        turnController.setTxSeedWpm(18);
        RxCommittedOutputController committedOutputController = new RxCommittedOutputController(
                new CwInterpreter(CwInterpreter.RecoveryMode.RAW_COPY_FOCUS),
                null,
                null,
                null,
                null
        );
        RxTurnSessionFinalizer turnSessionFinalizer = new RxTurnSessionFinalizer(
                new RxTurnTailRepairController(),
                committedOutputController
        );
        RxTurnSessionCoordinator turnSessionCoordinator = new RxTurnSessionCoordinator(
                signalProcessor,
                timingModel,
                wpmGuard,
                turnController,
                new TimingAnchorController(),
                new RxRawCommitGate(),
                turnSessionFinalizer,
                null,
                null
        );
        RxReplayTurnSessionController replayTurnSessionController =
                new RxReplayTurnSessionController(
                        signalProcessor,
                        timingModel,
                        null,
                        wpmGuard,
                        turnController,
                        turnSessionCoordinator,
                        turnSessionFinalizer
                );

        replayTurnSessionController.observe(startSignalSnapshot(), 1000L);
        replayTurnSessionController.processCommittedDecodeEvents(buildFinalEvents("", "CQ", "DE", "HI", "?"));
        List<CwToneEvent> toneEvents = encodeTextAsToneEvents("HI SK", 1600L, DOT_MS);
        noteToneEvents(replayTurnSessionController, toneEvents);

        RxTurnSessionFinalizer.TurnFinalization turnFinalization =
                replayTurnSessionController.finalizeAtStop(lastToneTimestamp(toneEvents) + (DOT_MS * 10L));

        assertTrue(turnFinalization != null);
        assertEquals(1, replayTurnSessionController.turnCount());
        assertEquals(1, replayTurnSessionController.tailRepairCount());
        assertEquals("HI SK", turnFinalization.repairResult().repairedTailText());
    }

    private static void noteToneEvents(
            RxReplayTurnSessionController replayTurnSessionController,
            List<CwToneEvent> toneEvents
    ) {
        for (CwToneEvent toneEvent : toneEvents) {
            replayTurnSessionController.noteToneEvent(toneEvent);
        }
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

    private static long lastToneTimestamp(List<CwToneEvent> toneEvents) {
        return toneEvents.isEmpty() ? 0L : toneEvents.get(toneEvents.size() - 1).timestampMs();
    }

    private static CwToneEvent toneOn(long timestampMs) {
        return new CwToneEvent(CwToneEvent.Type.TONE_ON, timestampMs, 16000, 12000.0d, 0L);
    }

    private static CwToneEvent toneOff(long timestampMs, long durationMs) {
        return new CwToneEvent(CwToneEvent.Type.TONE_OFF, timestampMs, 16000, 12000.0d, durationMs);
    }

    private static String morsePatternFor(char ch) {
        switch (Character.toUpperCase(ch)) {
            case 'C':
                return "-.-.";
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
            case 'Q':
                return "--.-";
            case 'S':
                return "...";
            default:
                throw new IllegalArgumentException("Unsupported test character: " + ch);
        }
    }
}
