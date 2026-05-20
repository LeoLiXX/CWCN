package org.bi9clt.cwcn.core.rx;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.audio.AudioFrame;
import org.bi9clt.cwcn.core.audio.AudioInputHealthSnapshot;
import org.bi9clt.cwcn.core.audio.AudioInputHealthTracker;
import org.bi9clt.cwcn.core.signal.CwSignalProcessor;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;

import java.util.Collections;
import java.util.List;

/**
 * Shared per-frame RX signal prelude.
 *
 * <p>This runner centralizes the common frame-level work before path-specific
 * tone routing begins:
 *
 * <ul>
 *   <li>audio input health update
 *   <li>signal snapshot before processing
 *   <li>signal processor frame pass
 *   <li>signal snapshot after processing
 *   <li>frame end timestamp resolution
 * </ul>
 */
public final class RxFrameSignalRunner {
    public static final class Result {
        private final long frameEndTimestampMs;
        @Nullable private final AudioInputHealthSnapshot inputHealthSnapshot;
        @Nullable private final CwSignalSnapshot signalSnapshotBeforeProcess;
        @Nullable private final CwSignalSnapshot signalSnapshotAfterProcess;
        private final List<CwToneEvent> toneEvents;

        private Result(
                long frameEndTimestampMs,
                @Nullable AudioInputHealthSnapshot inputHealthSnapshot,
                @Nullable CwSignalSnapshot signalSnapshotBeforeProcess,
                @Nullable CwSignalSnapshot signalSnapshotAfterProcess,
                List<CwToneEvent> toneEvents
        ) {
            this.frameEndTimestampMs = Math.max(0L, frameEndTimestampMs);
            this.inputHealthSnapshot = inputHealthSnapshot;
            this.signalSnapshotBeforeProcess = signalSnapshotBeforeProcess;
            this.signalSnapshotAfterProcess = signalSnapshotAfterProcess;
            this.toneEvents = toneEvents == null ? Collections.emptyList() : toneEvents;
        }

        public long frameEndTimestampMs() {
            return frameEndTimestampMs;
        }

        @Nullable
        public AudioInputHealthSnapshot inputHealthSnapshot() {
            return inputHealthSnapshot;
        }

        @Nullable
        public CwSignalSnapshot signalSnapshotBeforeProcess() {
            return signalSnapshotBeforeProcess;
        }

        @Nullable
        public CwSignalSnapshot signalSnapshotAfterProcess() {
            return signalSnapshotAfterProcess;
        }

        public List<CwToneEvent> toneEvents() {
            return toneEvents;
        }
    }

    @Nullable private final AudioInputHealthTracker inputHealthTracker;
    @Nullable private final CwSignalProcessor signalProcessor;

    public RxFrameSignalRunner(
            @Nullable AudioInputHealthTracker inputHealthTracker,
            @Nullable CwSignalProcessor signalProcessor
    ) {
        this.inputHealthTracker = inputHealthTracker;
        this.signalProcessor = signalProcessor;
    }

    @Nullable
    public Result processFrame(
            @Nullable AudioFrame frame,
            long nullFrameTimestampFallbackMs
    ) {
        if (frame == null || signalProcessor == null) {
            return null;
        }
        if (inputHealthTracker != null) {
            inputHealthTracker.process(frame);
        }
        AudioInputHealthSnapshot inputHealthSnapshot = inputHealthTracker == null
                ? null
                : inputHealthTracker.snapshot();
        CwSignalSnapshot signalSnapshotBeforeProcess = signalProcessor.snapshot();
        long frameEndTimestampMs = RxPendingCharacterFlushDecider.resolveFrameEndTimestampMs(
                frame,
                nullFrameTimestampFallbackMs
        );
        List<CwToneEvent> toneEvents = signalProcessor.process(frame);
        return new Result(
                frameEndTimestampMs,
                inputHealthSnapshot,
                signalSnapshotBeforeProcess,
                signalProcessor.snapshot(),
                toneEvents
        );
    }
}
