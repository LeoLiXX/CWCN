package org.bi9clt.cwcn.core.rx;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.audio.AudioInputHealthSnapshot;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;

import java.util.ArrayList;
import java.util.List;

public final class LiveRxToneEventStabilizer {
    private static final double ONSET_CONFIRM_MAX_DOT_RATIO = 0.42d;
    private static final long ONSET_CONFIRM_MAX_ABSOLUTE_MS = 20L;
    private static final double HOT_ONSET_CONFIRM_MAX_DOT_RATIO = 0.56d;
    private static final long HOT_ONSET_CONFIRM_MAX_ABSOLUTE_MS = 28L;
    private static final double FRAGMENT_TONE_MAX_DOT_RATIO = 0.52d;
    private static final long FRAGMENT_TONE_MAX_ABSOLUTE_MS = 26L;
    private static final double HOT_FRAGMENT_TONE_MAX_DOT_RATIO = 0.68d;
    private static final long HOT_FRAGMENT_TONE_MAX_ABSOLUTE_MS = 34L;
    private static final double BRIDGE_GAP_MAX_DOT_RATIO = 0.45d;
    private static final long BRIDGE_GAP_MAX_ABSOLUTE_MS = 24L;
    private static final double HOT_BRIDGE_GAP_MAX_DOT_RATIO = 0.58d;
    private static final long HOT_BRIDGE_GAP_MAX_ABSOLUTE_MS = 30L;
    private static final double WEAK_LOCKED_RATIO_MAX = 0.46d;
    private static final double ACTIVE_UNLOCKED_RATIO_MIN = 0.24d;
    private static final double WEAK_TONE_DOMINANCE_MAX = 0.50d;
    private static final double WEAK_ISOLATION_MAX = 0.60d;
    private static final double HOT_FRAME_RATIO_MIN = 0.34d;
    private static final double CLIPPING_FRAME_RATIO_MIN = 0.03d;

    @Nullable
    private CwToneEvent pendingToneOnCandidate;
    private long pendingToneOnConfirmAtMs = -1L;
    @Nullable
    private CwToneEvent pendingToneOffCandidate;
    private long pendingToneOffExpiresAtMs = -1L;
    private long pendingToneOffBridgeWindowMs = -1L;
    private boolean pendingToneOffDropIfIsolated;
    private long bridgedPrefixDurationMs = -1L;
    private long bridgedSplitTimestampMs = -1L;
    private int delayedToneOnCount;
    private int confirmedToneOnCount;
    private int droppedToneOnFragmentCount;
    private int delayedToneOffCount;
    private int droppedIsolatedToneOffCount;
    private int bridgedToneOffCount;

    public void reset() {
        clearPendingToneOnCandidate();
        pendingToneOffCandidate = null;
        pendingToneOffExpiresAtMs = -1L;
        pendingToneOffBridgeWindowMs = -1L;
        pendingToneOffDropIfIsolated = false;
        bridgedPrefixDurationMs = -1L;
        bridgedSplitTimestampMs = -1L;
        delayedToneOnCount = 0;
        confirmedToneOnCount = 0;
        droppedToneOnFragmentCount = 0;
        delayedToneOffCount = 0;
        droppedIsolatedToneOffCount = 0;
        bridgedToneOffCount = 0;
    }

    public List<CwToneEvent> process(
            @Nullable CwToneEvent toneEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot,
            long referenceDotEstimateMs
    ) {
        ArrayList<CwToneEvent> filteredEvents = new ArrayList<>(2);
        if (toneEvent == null) {
            return filteredEvents;
        }

        flushExpiredPendingToneOnCandidate(toneEvent.timestampMs(), filteredEvents);
        flushExpiredPendingCandidate(toneEvent.timestampMs(), filteredEvents);

        if (bridgeActive()) {
            if (toneEvent.type() == CwToneEvent.Type.TONE_OFF) {
                filteredEvents.add(mergeBridgedToneOff(toneEvent));
                bridgedToneOffCount += 1;
                clearBridge();
                return filteredEvents;
            }
            clearBridge();
        }

        if (pendingToneOffCandidate != null) {
            if (toneEvent.type() == CwToneEvent.Type.TONE_ON
                    && shouldBridgeCandidate(toneEvent, signalSnapshot, inputHealthSnapshot, referenceDotEstimateMs)) {
                startBridgeFromPending();
                return filteredEvents;
            }
            releasePendingCandidate(filteredEvents);
        }

        if (pendingToneOnCandidate != null) {
            if (toneEvent.type() == CwToneEvent.Type.TONE_OFF
                    && shouldDropPendingToneOnFragment(
                    toneEvent,
                    signalSnapshot,
                    inputHealthSnapshot,
                    referenceDotEstimateMs
            )) {
                droppedToneOnFragmentCount += 1;
                clearPendingToneOnCandidate();
                return filteredEvents;
            }
            releasePendingToneOnCandidate(filteredEvents);
        }

        if (toneEvent.type() == CwToneEvent.Type.TONE_ON
                && shouldDelayToneOnCandidate(
                toneEvent,
                signalSnapshot,
                inputHealthSnapshot,
                referenceDotEstimateMs
        )) {
            pendingToneOnCandidate = toneEvent;
            pendingToneOnConfirmAtMs = toneEvent.timestampMs()
                    + onsetConfirmWindowMs(referenceDotEstimateMs, inputHealthSnapshot);
            delayedToneOnCount += 1;
            return filteredEvents;
        }

        if (toneEvent.type() == CwToneEvent.Type.TONE_OFF
                && shouldDelayToneOffCandidate(
                toneEvent,
                signalSnapshot,
                inputHealthSnapshot,
                referenceDotEstimateMs
        )) {
            pendingToneOffCandidate = toneEvent;
            pendingToneOffBridgeWindowMs = bridgeGapWindowMs(referenceDotEstimateMs, inputHealthSnapshot);
            pendingToneOffExpiresAtMs = toneEvent.timestampMs() + pendingToneOffBridgeWindowMs;
            pendingToneOffDropIfIsolated = shouldSuppressShortTone(
                    toneEvent,
                    signalSnapshot,
                    inputHealthSnapshot,
                    referenceDotEstimateMs
            );
            delayedToneOffCount += 1;
            return filteredEvents;
        }

        filteredEvents.add(toneEvent);
        return filteredEvents;
    }

    public List<CwToneEvent> flush(long nowTimestampMs) {
        ArrayList<CwToneEvent> filteredEvents = new ArrayList<>(1);
        flushExpiredPendingToneOnCandidate(nowTimestampMs, filteredEvents);
        flushExpiredPendingCandidate(nowTimestampMs, filteredEvents);
        return filteredEvents;
    }

    public boolean shouldSuppressShortTone(
            @Nullable CwToneEvent toneEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot,
            long referenceDotEstimateMs
    ) {
        return toneEvent != null
                && toneEvent.type() == CwToneEvent.Type.TONE_OFF
                && looksLikeShortToneFragment(toneEvent, inputHealthSnapshot, referenceDotEstimateMs)
                && isUnstableConfidence(signalSnapshot, inputHealthSnapshot);
    }

    public LiveRxToneEventStabilizerStats stats() {
        return new LiveRxToneEventStabilizerStats(
                delayedToneOnCount,
                confirmedToneOnCount,
                droppedToneOnFragmentCount,
                delayedToneOffCount,
                droppedIsolatedToneOffCount,
                bridgedToneOffCount
        );
    }

    private void flushExpiredPendingCandidate(long nowTimestampMs, List<CwToneEvent> filteredEvents) {
        if (pendingToneOffCandidate == null || nowTimestampMs < pendingToneOffExpiresAtMs) {
            return;
        }
        releasePendingCandidate(filteredEvents);
    }

    private void flushExpiredPendingToneOnCandidate(long nowTimestampMs, List<CwToneEvent> filteredEvents) {
        if (pendingToneOnCandidate == null || nowTimestampMs < pendingToneOnConfirmAtMs) {
            return;
        }
        releasePendingToneOnCandidate(filteredEvents);
    }

    private boolean shouldDelayToneOnCandidate(
            CwToneEvent toneEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot,
            long referenceDotEstimateMs
    ) {
        return toneEvent.type() == CwToneEvent.Type.TONE_ON
                && onsetConfirmWindowMs(referenceDotEstimateMs, inputHealthSnapshot) > 0L
                && isUnstableOnsetConfidence(signalSnapshot, inputHealthSnapshot);
    }

    private boolean shouldDropPendingToneOnFragment(
            CwToneEvent toneEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot,
            long referenceDotEstimateMs
    ) {
        return pendingToneOnCandidate != null
                && toneEvent.type() == CwToneEvent.Type.TONE_OFF
                && looksLikeShortToneFragment(toneEvent, inputHealthSnapshot, referenceDotEstimateMs)
                && isUnstableOnsetConfidence(signalSnapshot, inputHealthSnapshot);
    }

    private boolean shouldDelayToneOffCandidate(
            CwToneEvent toneEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot,
            long referenceDotEstimateMs
    ) {
        return looksLikeShortToneFragment(toneEvent, inputHealthSnapshot, referenceDotEstimateMs)
                && isUnstableConfidence(signalSnapshot, inputHealthSnapshot);
    }

    private boolean shouldBridgeCandidate(
            CwToneEvent toneOnEvent,
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot,
            long referenceDotEstimateMs
    ) {
        if (pendingToneOffCandidate == null || toneOnEvent.type() != CwToneEvent.Type.TONE_ON) {
            return false;
        }
        if (!isUnstableConfidence(signalSnapshot, inputHealthSnapshot)) {
            return false;
        }
        long splitGapMs = Math.max(0L, toneOnEvent.timestampMs() - pendingToneOffCandidate.timestampMs());
        long bridgeWindowMs = pendingToneOffBridgeWindowMs > 0L
                ? pendingToneOffBridgeWindowMs
                : bridgeGapWindowMs(referenceDotEstimateMs, inputHealthSnapshot);
        return splitGapMs <= bridgeWindowMs;
    }

    private boolean looksLikeShortToneFragment(
            CwToneEvent toneEvent,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot,
            long referenceDotEstimateMs
    ) {
        if (toneEvent.type() != CwToneEvent.Type.TONE_OFF
                || toneEvent.toneDurationMs() <= 0L
                || referenceDotEstimateMs <= 0L) {
            return false;
        }
        boolean hotInput = isHotInput(inputHealthSnapshot);
        long fragmentWindowMs = Math.max(
                1L,
                Math.min(
                        hotInput ? HOT_FRAGMENT_TONE_MAX_ABSOLUTE_MS : FRAGMENT_TONE_MAX_ABSOLUTE_MS,
                        Math.round(referenceDotEstimateMs * (hotInput
                                ? HOT_FRAGMENT_TONE_MAX_DOT_RATIO
                                : FRAGMENT_TONE_MAX_DOT_RATIO))
                )
        );
        return toneEvent.toneDurationMs() <= fragmentWindowMs;
    }

    private long onsetConfirmWindowMs(
            long referenceDotEstimateMs,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot
    ) {
        boolean hotInput = isHotInput(inputHealthSnapshot);
        if (referenceDotEstimateMs <= 0L) {
            return hotInput ? HOT_ONSET_CONFIRM_MAX_ABSOLUTE_MS : ONSET_CONFIRM_MAX_ABSOLUTE_MS;
        }
        return Math.max(
                1L,
                Math.min(
                        hotInput ? HOT_ONSET_CONFIRM_MAX_ABSOLUTE_MS : ONSET_CONFIRM_MAX_ABSOLUTE_MS,
                        Math.round(referenceDotEstimateMs * (hotInput
                                ? HOT_ONSET_CONFIRM_MAX_DOT_RATIO
                                : ONSET_CONFIRM_MAX_DOT_RATIO))
                )
        );
    }

    private long bridgeGapWindowMs(
            long referenceDotEstimateMs,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot
    ) {
        boolean hotInput = isHotInput(inputHealthSnapshot);
        if (referenceDotEstimateMs <= 0L) {
            return hotInput ? HOT_BRIDGE_GAP_MAX_ABSOLUTE_MS : BRIDGE_GAP_MAX_ABSOLUTE_MS;
        }
        return Math.max(
                1L,
                Math.min(
                        hotInput ? HOT_BRIDGE_GAP_MAX_ABSOLUTE_MS : BRIDGE_GAP_MAX_ABSOLUTE_MS,
                        Math.round(referenceDotEstimateMs * (hotInput
                                ? HOT_BRIDGE_GAP_MAX_DOT_RATIO
                                : BRIDGE_GAP_MAX_DOT_RATIO))
                )
        );
    }

    private boolean isUnstableConfidence(
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot
    ) {
        boolean hotInput = inputHealthSnapshot != null
                && (inputHealthSnapshot.recentHotFrameRatio() >= HOT_FRAME_RATIO_MIN
                || inputHealthSnapshot.recentClippingFrameRatio() >= CLIPPING_FRAME_RATIO_MIN);
        boolean weakSignal = signalSnapshot != null
                && (!signalSnapshot.targetToneLocked()
                || signalSnapshot.recentLockedFrameRatio() <= WEAK_LOCKED_RATIO_MAX
                || signalSnapshot.recentActiveUnlockedFrameRatio() >= ACTIVE_UNLOCKED_RATIO_MIN
                || signalSnapshot.toneDominanceRatio() <= WEAK_TONE_DOMINANCE_MAX
                || signalSnapshot.narrowbandIsolationRatio() <= WEAK_ISOLATION_MAX);
        return hotInput || weakSignal;
    }

    private boolean isUnstableOnsetConfidence(
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot
    ) {
        boolean hotInput = isHotInput(inputHealthSnapshot);
        if (signalSnapshot == null) {
            return hotInput;
        }
        boolean weakSignal = !signalSnapshot.targetToneLocked()
                || signalSnapshot.recentLockedFrameRatio() <= WEAK_LOCKED_RATIO_MAX
                || signalSnapshot.recentActiveUnlockedFrameRatio() >= ACTIVE_UNLOCKED_RATIO_MIN
                || signalSnapshot.toneDominanceRatio() <= WEAK_TONE_DOMINANCE_MAX
                || signalSnapshot.narrowbandIsolationRatio() <= WEAK_ISOLATION_MAX;
        if (weakSignal) {
            return true;
        }
        return hotInput
                && signalSnapshot.recentLockedFrameRatio() < 0.72d
                && signalSnapshot.toneDominanceRatio() < 0.68d;
    }

    private void startBridgeFromPending() {
        if (pendingToneOffCandidate == null) {
            return;
        }
        bridgedPrefixDurationMs = Math.max(0L, pendingToneOffCandidate.toneDurationMs());
        bridgedSplitTimestampMs = pendingToneOffCandidate.timestampMs();
        clearPendingCandidate();
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

    private void clearPendingCandidate() {
        pendingToneOffCandidate = null;
        pendingToneOffExpiresAtMs = -1L;
        pendingToneOffBridgeWindowMs = -1L;
        pendingToneOffDropIfIsolated = false;
    }

    private void clearPendingToneOnCandidate() {
        pendingToneOnCandidate = null;
        pendingToneOnConfirmAtMs = -1L;
    }

    private void clearBridge() {
        bridgedPrefixDurationMs = -1L;
        bridgedSplitTimestampMs = -1L;
    }

    private void releasePendingToneOnCandidate(List<CwToneEvent> filteredEvents) {
        if (pendingToneOnCandidate == null) {
            return;
        }
        filteredEvents.add(pendingToneOnCandidate);
        confirmedToneOnCount += 1;
        clearPendingToneOnCandidate();
    }

    private void releasePendingCandidate(List<CwToneEvent> filteredEvents) {
        if (pendingToneOffCandidate == null) {
            return;
        }
        if (!pendingToneOffDropIfIsolated) {
            filteredEvents.add(pendingToneOffCandidate);
        } else {
            droppedIsolatedToneOffCount += 1;
        }
        clearPendingCandidate();
    }

    private boolean isHotInput(@Nullable AudioInputHealthSnapshot inputHealthSnapshot) {
        return inputHealthSnapshot != null
                && (inputHealthSnapshot.recentHotFrameRatio() >= HOT_FRAME_RATIO_MIN
                || inputHealthSnapshot.recentClippingFrameRatio() >= CLIPPING_FRAME_RATIO_MIN);
    }
}
