package org.bi9clt.cwcn.core.rx;

import org.bi9clt.cwcn.core.decoder.CwDecoderSnapshot;
import org.bi9clt.cwcn.core.interpreter.CwInterpreterSnapshot;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.timing.CwTimingSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RxReplayAnalysisResult {
    private final int preferredToneFrequencyHz;
    private final int sqlLevel;
    private final int seedWpm;
    private final int processedFrameCount;
    private final long flushTimestampMs;
    private final int toneEventCount;
    private final int timingEventCount;
    private final int decodeEventCount;
    private final int turnCount;
    private final int tailRepairCount;
    private final List<RxReplayTurnSessionController.TransitionTrace> transitionTraces;
    private final List<RxReplayTurnSessionController.TurnWindow> turnWindows;
    private final CwSignalSnapshot signalSnapshot;
    private final CwTimingSnapshot timingSnapshot;
    private final CwDecoderSnapshot decoderSnapshot;
    private final CwInterpreterSnapshot interpreterSnapshot;

    public RxReplayAnalysisResult(
            int preferredToneFrequencyHz,
            int sqlLevel,
            int seedWpm,
            int processedFrameCount,
            long flushTimestampMs,
            int toneEventCount,
            int timingEventCount,
            int decodeEventCount,
            int turnCount,
            int tailRepairCount,
            List<RxReplayTurnSessionController.TransitionTrace> transitionTraces,
            List<RxReplayTurnSessionController.TurnWindow> turnWindows,
            CwSignalSnapshot signalSnapshot,
            CwTimingSnapshot timingSnapshot,
            CwDecoderSnapshot decoderSnapshot,
            CwInterpreterSnapshot interpreterSnapshot
    ) {
        this.preferredToneFrequencyHz = preferredToneFrequencyHz;
        this.sqlLevel = sqlLevel;
        this.seedWpm = seedWpm;
        this.processedFrameCount = Math.max(0, processedFrameCount);
        this.flushTimestampMs = Math.max(0L, flushTimestampMs);
        this.toneEventCount = Math.max(0, toneEventCount);
        this.timingEventCount = Math.max(0, timingEventCount);
        this.decodeEventCount = Math.max(0, decodeEventCount);
        this.turnCount = Math.max(0, turnCount);
        this.tailRepairCount = Math.max(0, tailRepairCount);
        this.transitionTraces = transitionTraces == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(transitionTraces));
        this.turnWindows = turnWindows == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(turnWindows));
        this.signalSnapshot = signalSnapshot;
        this.timingSnapshot = timingSnapshot;
        this.decoderSnapshot = decoderSnapshot;
        this.interpreterSnapshot = interpreterSnapshot;
    }

    public int preferredToneFrequencyHz() {
        return preferredToneFrequencyHz;
    }

    public int sqlLevel() {
        return sqlLevel;
    }

    public int seedWpm() {
        return seedWpm;
    }

    public int processedFrameCount() {
        return processedFrameCount;
    }

    public long flushTimestampMs() {
        return flushTimestampMs;
    }

    public int toneEventCount() {
        return toneEventCount;
    }

    public int timingEventCount() {
        return timingEventCount;
    }

    public int decodeEventCount() {
        return decodeEventCount;
    }

    public int turnCount() {
        return turnCount;
    }

    public int tailRepairCount() {
        return tailRepairCount;
    }

    public List<RxReplayTurnSessionController.TransitionTrace> transitionTraces() {
        return transitionTraces;
    }

    public List<RxReplayTurnSessionController.TurnWindow> turnWindows() {
        return turnWindows;
    }

    public CwSignalSnapshot signalSnapshot() {
        return signalSnapshot;
    }

    public CwTimingSnapshot timingSnapshot() {
        return timingSnapshot;
    }

    public CwDecoderSnapshot decoderSnapshot() {
        return decoderSnapshot;
    }

    public CwInterpreterSnapshot interpreterSnapshot() {
        return interpreterSnapshot;
    }

    public String decodedText() {
        return interpreterSnapshot == null ? "" : interpreterSnapshot.rawText();
    }

    public String normalizedText() {
        return interpreterSnapshot == null ? "" : interpreterSnapshot.normalizedText();
    }
}
