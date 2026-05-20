package org.bi9clt.cwcn.core.rx;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.audio.AudioFrame;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;

public final class RxPendingCharacterFlushDecider {
    public enum ActivityPolicy {
        TONE_ACTIVE,
        MEANINGFUL_TURN_ACTIVITY
    }

    public static final class Decision {
        private final boolean shouldFlush;
        private final long flushTimestampMs;
        private final long silentGapMs;
        private final String reason;

        private Decision(
                boolean shouldFlush,
                long flushTimestampMs,
                long silentGapMs,
                String reason
        ) {
            this.shouldFlush = shouldFlush;
            this.flushTimestampMs = Math.max(0L, flushTimestampMs);
            this.silentGapMs = Math.max(0L, silentGapMs);
            this.reason = reason == null ? "" : reason;
        }

        public boolean shouldFlush() {
            return shouldFlush;
        }

        public long flushTimestampMs() {
            return flushTimestampMs;
        }

        public long silentGapMs() {
            return silentGapMs;
        }

        public String reason() {
            return reason;
        }
    }

    private RxPendingCharacterFlushDecider() {
    }

    public static long resolveFrameEndTimestampMs(
            @Nullable AudioFrame frame,
            long nullFallbackTimestampMs
    ) {
        if (frame == null) {
            return Math.max(0L, nullFallbackTimestampMs);
        }
        return frame.capturedAtMs() + frameDurationMs(frame);
    }

    public static long frameDurationMs(@Nullable AudioFrame frame) {
        if (frame == null) {
            return 0L;
        }
        return Math.max(
                1L,
                Math.round(frame.sampleCount() * 1000.0d / Math.max(1, frame.sampleRateHz()))
        );
    }

    public static Decision evaluate(
            @Nullable AudioFrame frame,
            long candidateFlushTimestampMs,
            @Nullable CwSignalSnapshot signalSnapshot,
            long minimumFlushGapMs,
            ActivityPolicy activityPolicy
    ) {
        if (frame == null) {
            return skip("missing-frame", candidateFlushTimestampMs, 0L);
        }
        if (signalSnapshot == null) {
            return skip("missing-signal", candidateFlushTimestampMs, 0L);
        }
        long flushTimestampMs = Math.max(
                resolveFrameEndTimestampMs(frame, candidateFlushTimestampMs),
                Math.max(0L, candidateFlushTimestampMs)
        );
        if (hasActiveSignal(signalSnapshot, activityPolicy)) {
            return skip("signal-active", flushTimestampMs, 0L);
        }
        CwToneEvent lastSignalEvent = signalSnapshot.lastEvent();
        if (lastSignalEvent == null || lastSignalEvent.type() != CwToneEvent.Type.TONE_OFF) {
            return skip("missing-trailing-tone-off", flushTimestampMs, 0L);
        }
        long silentGapMs = Math.max(0L, flushTimestampMs - lastSignalEvent.timestampMs());
        if (silentGapMs < Math.max(1L, minimumFlushGapMs)) {
            return skip("insufficient-silence", flushTimestampMs, silentGapMs);
        }
        return new Decision(true, flushTimestampMs, silentGapMs, "ready");
    }

    private static boolean hasActiveSignal(
            CwSignalSnapshot signalSnapshot,
            ActivityPolicy activityPolicy
    ) {
        if (signalSnapshot == null) {
            return false;
        }
        if (activityPolicy == ActivityPolicy.MEANINGFUL_TURN_ACTIVITY) {
            return RxTurnActivityDecider.isMeaningfulTurnActivity(signalSnapshot);
        }
        return signalSnapshot.toneActive();
    }

    private static Decision skip(String reason, long flushTimestampMs, long silentGapMs) {
        return new Decision(false, flushTimestampMs, silentGapMs, reason);
    }
}
