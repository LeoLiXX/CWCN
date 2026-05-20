package org.bi9clt.cwcn.core.rx;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.audio.AudioFrame;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;

import java.util.Collections;
import java.util.List;

/**
 * Shared frame-sequence replay runner for developer and analysis paths.
 *
 * <p>This runner sits one level above {@link RxFrameSignalRunner} and the
 * tone/timing/decode runners. It centralizes the common replay skeleton used
 * when a caller already has a finite frame list:
 *
 * <ul>
 *   <li>frame-by-frame signal prelude
 *   <li>tone batch dispatch into timing/decode
 *   <li>final pending-gap flush
 *   <li>final pending-character flush
 * </ul>
 *
 * <p>Callers still own path-specific policies such as timing-learning
 * admission, timing adaptation, stable filtering, and semantic side effects.</p>
 */
public final class RxReplaySessionRunner {
    public interface FrameObserver {
        void observe(AudioFrame frame, RxFrameSignalRunner.Result frameResult);
    }

    public interface PendingTimingEventProducer {
        @Nullable
        List<CwTimingEvent> produce(long flushTimestampMs);
    }

    public interface PendingTimingEventsObserver {
        void observe(long flushTimestampMs, List<CwTimingEvent> timingEvents);
    }

    public static final class Result {
        private final int processedFrameCount;
        private final long flushTimestampMs;

        private Result(int processedFrameCount, long flushTimestampMs) {
            this.processedFrameCount = Math.max(0, processedFrameCount);
            this.flushTimestampMs = Math.max(0L, flushTimestampMs);
        }

        public int processedFrameCount() {
            return processedFrameCount;
        }

        public long flushTimestampMs() {
            return flushTimestampMs;
        }
    }

    @Nullable private final RxFrameSignalRunner frameSignalRunner;
    @Nullable private final RxToneTimingRunner toneTimingRunner;
    @Nullable private final RxTimingDecodeRunner timingDecodeRunner;

    public RxReplaySessionRunner(
            @Nullable RxFrameSignalRunner frameSignalRunner,
            @Nullable RxToneTimingRunner toneTimingRunner,
            @Nullable RxTimingDecodeRunner timingDecodeRunner
    ) {
        this.frameSignalRunner = frameSignalRunner;
        this.toneTimingRunner = toneTimingRunner;
        this.timingDecodeRunner = timingDecodeRunner;
    }

    public Result replayFrames(
            @Nullable List<AudioFrame> frames,
            @Nullable RxToneTimingRunner.TimingEventProducer timingEventProducer,
            @Nullable FrameObserver frameObserver,
            @Nullable RxToneTimingRunner.TimingEventsObserver timingEventsObserver,
            @Nullable PendingTimingEventProducer pendingTimingEventProducer,
            @Nullable PendingTimingEventsObserver pendingTimingEventsObserver,
            @Nullable RxTimingDecodeRunner.TimingEventRelay timingEventRelay,
            @Nullable RxTimingDecodeRunner.DecodeEventConsumer decodeEventConsumer
    ) {
        if (frameSignalRunner == null || toneTimingRunner == null || timingDecodeRunner == null) {
            return new Result(0, 0L);
        }
        int processedFrameCount = 0;
        long flushTimestampMs = 0L;
        if (frames != null && !frames.isEmpty()) {
            for (AudioFrame frame : frames) {
                if (frame == null) {
                    continue;
                }
                RxFrameSignalRunner.Result frameResult = frameSignalRunner.processFrame(
                        frame,
                        frame.capturedAtMs()
                );
                if (frameResult == null) {
                    continue;
                }
                processedFrameCount += 1;
                flushTimestampMs = frameResult.frameEndTimestampMs();
                if (frameObserver != null) {
                    frameObserver.observe(frame, frameResult);
                }
                toneTimingRunner.dispatchToneEvents(
                        frameResult.toneEvents(),
                        timingEventProducer,
                        timingEventsObserver,
                        timingEventRelay,
                        decodeEventConsumer
                );
            }
        }

        if (flushTimestampMs <= 0L) {
            return new Result(processedFrameCount, 0L);
        }

        List<CwTimingEvent> pendingTimingEvents = pendingTimingEventProducer == null
                ? Collections.emptyList()
                : pendingTimingEventProducer.produce(flushTimestampMs);
        List<CwTimingEvent> safePendingTimingEvents = pendingTimingEvents == null
                ? Collections.emptyList()
                : pendingTimingEvents;
        if (pendingTimingEventsObserver != null) {
            pendingTimingEventsObserver.observe(flushTimestampMs, safePendingTimingEvents);
        }
        timingDecodeRunner.dispatchTimingEvents(
                safePendingTimingEvents,
                timingEventRelay,
                decodeEventConsumer
        );
        timingDecodeRunner.flushPendingCharacter(
                flushTimestampMs,
                decodeEventConsumer
        );
        return new Result(processedFrameCount, flushTimestampMs);
    }
}
