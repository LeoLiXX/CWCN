package org.bi9clt.cwcn.core.rx;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.audio.AudioInputHealthSnapshot;
import org.bi9clt.cwcn.core.rx.experimental.ExperimentalRxFrontEndPipeline;
import org.bi9clt.cwcn.core.rx.experimental.ExperimentalRxFrontEndSnapshot;
import org.bi9clt.cwcn.core.rx.experimental.RxEnvelopeState;
import org.bi9clt.cwcn.core.rx.experimental.RxLearningAuthority;
import org.bi9clt.cwcn.core.rx.experimental.RxRunCandidate;
import org.bi9clt.cwcn.core.rx.experimental.RxRunKind;
import org.bi9clt.cwcn.core.signal.CwSignalSnapshot;
import org.bi9clt.cwcn.core.signal.CwToneEvent;

import java.util.List;

/**
 * Test-only compatibility authority tracker.
 *
 * <p>This helper exists for live-like regression observation while the shared
 * RX runtime converges. Production RX should rely on verified shared gates such
 * as {@link CwFrontEndLearningGate}, not on this compatibility wrapper around
 * the experimental front-end envelope.</p>
 */
public final class CwFrontEndAuthorityTracker {
    private static final long FRONT_END_AUTHORITY_HOLD_MS = 420L;

    private final ExperimentalRxFrontEndPipeline pipeline = new ExperimentalRxFrontEndPipeline();
    private final int sqlPercent;

    private boolean toneActive;
    private RxLearningAuthority activeFrameAuthority = RxLearningAuthority.BLOCKED;
    private RxLearningAuthority recentAuthority = RxLearningAuthority.BLOCKED;
    private long recentAuthorityExpiresAtMs = -1L;

    public CwFrontEndAuthorityTracker(int sqlPercent) {
        this.sqlPercent = Math.max(0, Math.min(100, sqlPercent));
    }

    public void reset() {
        pipeline.reset();
        toneActive = false;
        activeFrameAuthority = RxLearningAuthority.BLOCKED;
        recentAuthority = RxLearningAuthority.BLOCKED;
        recentAuthorityExpiresAtMs = -1L;
    }

    public void observeFrame(
            @Nullable CwSignalSnapshot signalSnapshot,
            @Nullable AudioInputHealthSnapshot inputHealthSnapshot,
            long timestampMs
    ) {
        expire(timestampMs);
        if (signalSnapshot == null || timestampMs <= 0L) {
            return;
        }
        List<RxRunCandidate> emittedRuns = pipeline.process(
                signalSnapshot,
                inputHealthSnapshot,
                sqlPercent,
                timestampMs
        );
        ExperimentalRxFrontEndSnapshot snapshot = pipeline.snapshot();
        boolean snapshotToneActive = snapshot != null && snapshot.toneActive();
        RxLearningAuthority snapshotAuthority = authorityFor(snapshot);
        RxLearningAuthority emittedToneRunAuthority = emittedToneRunAuthority(emittedRuns);

        if (snapshotToneActive) {
            activeFrameAuthority = snapshotAuthority;
        } else if (toneActive) {
            latchRecentAuthority(
                    emittedToneRunAuthority == null ? activeFrameAuthority : emittedToneRunAuthority,
                    timestampMs
            );
            activeFrameAuthority = RxLearningAuthority.BLOCKED;
        } else if (emittedToneRunAuthority != null) {
            latchRecentAuthority(emittedToneRunAuthority, timestampMs);
        }
        toneActive = snapshotToneActive;
    }

    public boolean shouldAllowTimingLearning(long nowTimestampMs) {
        return currentAuthority(nowTimestampMs) == RxLearningAuthority.DECODE_AND_LEARN;
    }

    public boolean shouldAllowTimingLearningForEvent(
            @Nullable CwToneEvent toneEvent,
            long nowTimestampMs
    ) {
        return toneEvent != null && shouldAllowTimingLearning(nowTimestampMs);
    }

    public boolean shouldAllowStableAnchorUpdate(
            @Nullable CwSignalSnapshot signalSnapshot,
            long nowTimestampMs
    ) {
        return decodedCharacterAuthority(signalSnapshot, nowTimestampMs)
                == RxLearningAuthority.DECODE_AND_LEARN;
    }

    public boolean shouldAllowBootstrapStableAnchorUpdate(
            @Nullable CwSignalSnapshot signalSnapshot,
            long nowTimestampMs
    ) {
        return decodedCharacterAuthority(signalSnapshot, nowTimestampMs)
                != RxLearningAuthority.BLOCKED;
    }

    public RxLearningAuthority currentAuthority(long nowTimestampMs) {
        expire(nowTimestampMs);
        if (toneActive) {
            return activeFrameAuthority;
        }
        if (recentAuthorityExpiresAtMs >= nowTimestampMs) {
            return recentAuthority;
        }
        return RxLearningAuthority.BLOCKED;
    }

    public RxLearningAuthority decodedCharacterAuthority(
            @Nullable CwSignalSnapshot signalSnapshot,
            long nowTimestampMs
    ) {
        expire(nowTimestampMs);
        if (toneActive) {
            return activeFrameAuthority;
        }
        if (recentAuthorityExpiresAtMs >= nowTimestampMs) {
            return recentAuthority;
        }
        return RxLearningAuthority.BLOCKED;
    }

    @Nullable
    public ExperimentalRxFrontEndSnapshot snapshot() {
        return pipeline.snapshot();
    }

    private void latchRecentAuthority(@Nullable RxLearningAuthority authority, long timestampMs) {
        recentAuthority = authority == null ? RxLearningAuthority.BLOCKED : authority;
        recentAuthorityExpiresAtMs = timestampMs + FRONT_END_AUTHORITY_HOLD_MS;
    }

    private void expire(long nowTimestampMs) {
        if (!toneActive && recentAuthorityExpiresAtMs > 0L && nowTimestampMs > recentAuthorityExpiresAtMs) {
            recentAuthority = RxLearningAuthority.BLOCKED;
            recentAuthorityExpiresAtMs = -1L;
        }
    }

    private RxLearningAuthority authorityFor(@Nullable ExperimentalRxFrontEndSnapshot snapshot) {
        if (snapshot == null) {
            return RxLearningAuthority.BLOCKED;
        }
        RxEnvelopeState envelopeState = snapshot.envelopeState();
        if (envelopeState == RxEnvelopeState.ON_CONFIRMED) {
            return RxLearningAuthority.DECODE_AND_LEARN;
        }
        if (envelopeState == RxEnvelopeState.ATTACK_CANDIDATE
                || envelopeState == RxEnvelopeState.WEAK_VALLEY
                || envelopeState == RxEnvelopeState.RELEASE_CANDIDATE) {
            return RxLearningAuthority.DECODE_ONLY;
        }
        return snapshot.toneActive()
                ? RxLearningAuthority.DECODE_ONLY
                : RxLearningAuthority.BLOCKED;
    }

    @Nullable
    private RxLearningAuthority emittedToneRunAuthority(@Nullable List<RxRunCandidate> emittedRuns) {
        if (emittedRuns == null || emittedRuns.isEmpty()) {
            return null;
        }
        RxLearningAuthority authority = null;
        for (RxRunCandidate emittedRun : emittedRuns) {
            if (emittedRun == null || emittedRun.kind() != RxRunKind.TONE) {
                continue;
            }
            authority = emittedRun.learningAuthority();
        }
        return authority;
    }
}
