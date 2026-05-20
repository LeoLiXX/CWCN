package org.bi9clt.cwcn.core.rx;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.decoder.CwDecoder;
import org.bi9clt.cwcn.core.timing.CwTimingEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Guards RAW output admission during turn bootstrap.
 *
 * <p>Before a turn has established trusted timing, final decode events are
 * treated as speculative. They may still help timing/trust formation, but they
 * should not immediately enter the RAW stream. Once trusted timing exists, the
 * gate opens for the rest of that turn and tries to recover the speculative
 * opening prefix by replaying buffered timing events against the trusted dot
 * estimate before committing live final events.</p>
 *
 * <p>Earlier revisions allowed pre-trust stable characters to surface
 * provisionally, but that made cadence replay unable to repair damaged opening
 * prefixes such as clipped first-call characters. The current policy favors a
 * slightly later RAW opening over locking in the wrong opening text.</p>
 */
public final class RxRawCommitGate {
    private static final int MAX_PENDING_FINAL_EVENTS = 8;
    private static final long MAX_PENDING_SPAN_MS = 2600L;
    private static final int MAX_BUFFERED_TIMING_EVENTS = 512;
    private static final long MAX_BUFFERED_TIMING_SPAN_MS = 7000L;
    private static final int CADENCE_RECOVERY_BACKOFF_DOTS = 4;
    private static final int CADENCE_RECOVERY_REQUIRED_POST_TRUST_CHARACTERS = 2;
    private static final double FORCED_REPLAY_LETTER_GAP_MAX_RATIO = 4.35d;
    private static final double FORCED_REPLAY_WORD_GAP_MAX_RATIO = 12.8d;
    private static final double FORCED_REPLAY_RAW_LETTER_GAP_WORD_PROMOTE_MAX_RATIO = 5.05d;
    private static final double FORCED_REPLAY_RAW_WORD_GAP_UNKNOWN_MAX_RATIO = 13.25d;

    private final ArrayList<CwDecodeEvent> pendingFinalEvents = new ArrayList<>();
    private final ArrayList<CwTimingEvent> bufferedTimingEvents = new ArrayList<>();
    private final StringBuilder committedOutputText = new StringBuilder();
    private boolean gateOpenInCurrentTurn;
    private boolean admittedCharacterInCurrentTurn;
    private boolean cadenceTrustAlignmentBoundaryObserved;
    private long cadenceTrustAlignmentBoundaryTimestampMs = -1L;
    private long cadenceTrustEstablishedTimestampMs = -1L;
    private long cadenceTrustedDotEstimateMs = 0L;
    private long cadenceRecoveryWindowStartTimestampMs = -1L;
    private int cadencePostTrustCharacterCount;
    private long lastCommittedFinalTimestampMs = -1L;

    public void reset() {
        committedOutputText.setLength(0);
        resetTurnState();
    }

    public void beginNewTurn() {
        resetTurnState();
    }

    public void endTurn() {
        resetTurnState();
    }

    public void replaceCommittedOutputText(@Nullable String outputText) {
        committedOutputText.setLength(0);
        committedOutputText.append(nullToEmpty(outputText));
    }

    private void resetTurnState() {
        pendingFinalEvents.clear();
        bufferedTimingEvents.clear();
        gateOpenInCurrentTurn = false;
        admittedCharacterInCurrentTurn = false;
        cadenceTrustAlignmentBoundaryObserved = false;
        cadenceTrustAlignmentBoundaryTimestampMs = -1L;
        cadenceTrustEstablishedTimestampMs = -1L;
        cadenceTrustedDotEstimateMs = 0L;
        cadenceRecoveryWindowStartTimestampMs = -1L;
        cadencePostTrustCharacterCount = 0;
        lastCommittedFinalTimestampMs = -1L;
    }

    public List<CwDecodeEvent> admit(
            @Nullable CwDecodeEvent decodeEvent,
            boolean trustedTimingEstablished,
            @Nullable TimingAnchorController.TrustOrigin trustOrigin,
            long trustedDotEstimateMs,
            long trustedTimestampMs
    ) {
        if (decodeEvent == null) {
            return Collections.emptyList();
        }
        if (!isFinalDecodeEvent(decodeEvent)) {
            return Collections.singletonList(decodeEvent);
        }
        noteCadenceBootstrapFinalEvent(decodeEvent, trustedTimingEstablished, trustOrigin);
        boolean openingGateNow = !gateOpenInCurrentTurn && canOpenGateForFinalEvent(
                decodeEvent,
                trustedTimingEstablished,
                trustOrigin
        );
        if (gateOpenInCurrentTurn || openingGateNow) {
            ArrayList<CwDecodeEvent> admittedEvents = new ArrayList<>();
            if (openingGateNow) {
                gateOpenInCurrentTurn = true;
                admittedEvents.addAll(recoverOpeningReplayEvents(
                        decodeEvent.timestampMs(),
                        trustOrigin,
                        trustedDotEstimateMs,
                        trustedTimestampMs
                ));
                pendingFinalEvents.clear();
                if (!admittedEvents.isEmpty()) {
                    return admittedEvents;
                }
            }
            if (shouldSuppressLeadingWordBreak(decodeEvent)) {
                return admittedEvents;
            }
            admittedEvents.add(commitFinalEvent(decodeEvent));
            return admittedEvents;
        }

        pendingFinalEvents.add(decodeEvent);
        trimPendingFinalEvents();
        return Collections.emptyList();
    }

    public void noteTimingEvent(
            @Nullable CwTimingEvent timingEvent,
            boolean trustedTimingEstablished,
            @Nullable TimingAnchorController.TrustOrigin trustOrigin,
            long trustedDotEstimateMs,
            long trustedTimestampMs
    ) {
        if (timingEvent == null) {
            return;
        }
        bufferedTimingEvents.add(timingEvent);
        trimBufferedTimingEvents();
        if (gateOpenInCurrentTurn
                || !trustedTimingEstablished
                || trustOrigin != TimingAnchorController.TrustOrigin.CADENCE) {
            return;
        }
        if (trustedDotEstimateMs > 0L && trustedTimestampMs > 0L && cadenceTrustedDotEstimateMs <= 0L) {
            cadenceTrustedDotEstimateMs = trustedDotEstimateMs;
            cadenceTrustEstablishedTimestampMs = trustedTimestampMs;
            cadenceRecoveryWindowStartTimestampMs = Math.max(
                    0L,
                    trustedTimestampMs - (trustedDotEstimateMs * CADENCE_RECOVERY_BACKOFF_DOTS)
            );
        }
        if (cadenceTrustAlignmentBoundaryObserved || timingEvent.kind() != CwTimingEvent.Kind.GAP) {
            return;
        }
        if (timingEvent.classification() == CwTimingEvent.Classification.LETTER_GAP
                || timingEvent.classification() == CwTimingEvent.Classification.WORD_GAP) {
            cadenceTrustAlignmentBoundaryObserved = true;
            cadenceTrustAlignmentBoundaryTimestampMs = timingEvent.timestampMs();
        }
    }

    public int pendingFinalEventCount() {
        return pendingFinalEvents.size();
    }

    public boolean gateOpenInCurrentTurn() {
        return gateOpenInCurrentTurn;
    }

    private boolean canOpenGateForFinalEvent(
            CwDecodeEvent decodeEvent,
            boolean trustedTimingEstablished,
            @Nullable TimingAnchorController.TrustOrigin trustOrigin
    ) {
        if (!trustedTimingEstablished) {
            return false;
        }
        if (trustOrigin != TimingAnchorController.TrustOrigin.CADENCE) {
            return true;
        }
        if (!cadenceTrustAlignmentBoundaryObserved) {
            return false;
        }
        return decodeEvent.type() == CwDecodeEvent.Type.CHARACTER_DECODED
                && cadencePostTrustCharacterCount >= CADENCE_RECOVERY_REQUIRED_POST_TRUST_CHARACTERS;
    }

    private boolean isFinalDecodeEvent(CwDecodeEvent decodeEvent) {
        return decodeEvent.type() == CwDecodeEvent.Type.CHARACTER_DECODED
                || decodeEvent.type() == CwDecodeEvent.Type.WORD_BREAK;
    }

    private boolean shouldSuppressLeadingWordBreak(CwDecodeEvent decodeEvent) {
        return decodeEvent.type() == CwDecodeEvent.Type.WORD_BREAK
                && !admittedCharacterInCurrentTurn;
    }

    private CwDecodeEvent commitFinalEvent(CwDecodeEvent decodeEvent) {
        if (decodeEvent.type() == CwDecodeEvent.Type.CHARACTER_DECODED) {
            committedOutputText.append(nullToEmpty(decodeEvent.emittedValue()));
            admittedCharacterInCurrentTurn = true;
        } else if (decodeEvent.type() == CwDecodeEvent.Type.WORD_BREAK
                && committedOutputText.length() > 0
                && committedOutputText.charAt(committedOutputText.length() - 1) != ' ') {
            committedOutputText.append(' ');
        }
        lastCommittedFinalTimestampMs = Math.max(lastCommittedFinalTimestampMs, decodeEvent.timestampMs());
        return new CwDecodeEvent(
                decodeEvent.type(),
                decodeEvent.timestampMs(),
                decodeEvent.currentSequence(),
                committedOutputText.toString(),
                decodeEvent.emittedValue(),
                decodeEvent.sourceSequence(),
                decodeEvent.unknownCharacter()
        );
    }

    private String nullToEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    private void noteCadenceBootstrapFinalEvent(
            CwDecodeEvent decodeEvent,
            boolean trustedTimingEstablished,
            @Nullable TimingAnchorController.TrustOrigin trustOrigin
    ) {
        if (decodeEvent == null
                || gateOpenInCurrentTurn
                || !trustedTimingEstablished
                || trustOrigin != TimingAnchorController.TrustOrigin.CADENCE
                || !cadenceTrustAlignmentBoundaryObserved
                || decodeEvent.type() != CwDecodeEvent.Type.CHARACTER_DECODED
                || decodeEvent.unknownCharacter()
                || decodeEvent.timestampMs() <= cadenceTrustAlignmentBoundaryTimestampMs) {
            return;
        }
        cadencePostTrustCharacterCount += 1;
    }

    private List<CwDecodeEvent> recoverOpeningReplayEvents(
            long replayEndTimestampMs,
            @Nullable TimingAnchorController.TrustOrigin trustOrigin,
            long trustedDotEstimateMs,
            long trustedTimestampMs
    ) {
        if (bufferedTimingEvents.isEmpty()) {
            return Collections.emptyList();
        }
        long replayStartTimestampMs = bufferedTimingEvents.get(0).timestampMs();
        long forcedDotMs = trustedDotEstimateMs;
        if (trustOrigin == TimingAnchorController.TrustOrigin.CADENCE) {
            if (cadenceTrustedDotEstimateMs <= 0L
                    || cadenceRecoveryWindowStartTimestampMs < 0L
                    || replayEndTimestampMs < cadenceRecoveryWindowStartTimestampMs) {
                return Collections.emptyList();
            }
            replayStartTimestampMs = admittedCharacterInCurrentTurn
                    ? cadenceRecoveryWindowStartTimestampMs
                    : bufferedTimingEvents.get(0).timestampMs();
            forcedDotMs = cadenceTrustedDotEstimateMs;
        } else {
            if (forcedDotMs <= 0L && cadenceTrustedDotEstimateMs > 0L) {
                forcedDotMs = cadenceTrustedDotEstimateMs;
            }
            if (trustedTimestampMs > 0L) {
                replayStartTimestampMs = Math.min(replayStartTimestampMs, trustedTimestampMs);
            }
        }
        if (forcedDotMs <= 0L) {
            return Collections.emptyList();
        }
        return recoverReplayEvents(replayStartTimestampMs, replayEndTimestampMs, forcedDotMs);
    }

    private List<CwDecodeEvent> recoverReplayEvents(
            long replayStartTimestampMs,
            long replayEndTimestampMs,
            long forcedDotMs
    ) {
        if (lastCommittedFinalTimestampMs >= 0L) {
            replayStartTimestampMs = Math.max(replayStartTimestampMs, lastCommittedFinalTimestampMs + 1L);
        }
        if (forcedDotMs <= 0L
                || replayStartTimestampMs < 0L
                || replayEndTimestampMs < replayStartTimestampMs
                || bufferedTimingEvents.isEmpty()) {
            return Collections.emptyList();
        }
        RxTimingDecodeRunner timingDecodeRunner = new RxTimingDecodeRunner(new CwDecoder());
        ArrayList<CwDecodeEvent> recoveredEvents = new ArrayList<>();
        RxTimingDecodeRunner.DecodeEventConsumer recoveredDecodeEventConsumer = decodeEvent -> {
            if (!isFinalDecodeEvent(decodeEvent) || shouldSuppressLeadingWordBreak(decodeEvent)) {
                return;
            }
            recoveredEvents.add(commitFinalEvent(decodeEvent));
        };
        for (CwTimingEvent timingEvent : bufferedTimingEvents) {
            if (timingEvent.timestampMs() < replayStartTimestampMs
                    || timingEvent.timestampMs() > replayEndTimestampMs) {
                continue;
            }
            CwTimingEvent forcedTimingEvent = forceClassifyTimingEvent(timingEvent, forcedDotMs);
            if (shouldSynthesizeLeadingReplayWordBreak(forcedTimingEvent, recoveredEvents.isEmpty())) {
                recoveredEvents.add(commitFinalEvent(syntheticWordBreakEvent(forcedTimingEvent.timestampMs())));
            }
            timingDecodeRunner.dispatchTimingEvents(
                    Collections.singletonList(forcedTimingEvent),
                    null,
                    recoveredDecodeEventConsumer
            );
        }
        timingDecodeRunner.flushPendingCharacter(
                replayEndTimestampMs,
                recoveredDecodeEventConsumer
        );
        return recoveredEvents;
    }

    private boolean shouldSynthesizeLeadingReplayWordBreak(
            CwTimingEvent timingEvent,
            boolean replayHasNotRecoveredFinalEventYet
    ) {
        return timingEvent.kind() == CwTimingEvent.Kind.GAP
                && timingEvent.classification() == CwTimingEvent.Classification.WORD_GAP
                && replayHasNotRecoveredFinalEventYet
                && admittedCharacterInCurrentTurn
                && committedOutputText.length() > 0
                && committedOutputText.charAt(committedOutputText.length() - 1) != ' ';
    }

    private CwDecodeEvent syntheticWordBreakEvent(long timestampMs) {
        return new CwDecodeEvent(
                CwDecodeEvent.Type.WORD_BREAK,
                timestampMs,
                "",
                committedOutputText.toString(),
                " ",
                "",
                false
        );
    }

    private CwTimingEvent forceClassifyTimingEvent(CwTimingEvent timingEvent, long forcedDotMs) {
        CwTimingEvent.Classification classification = timingEvent.kind() == CwTimingEvent.Kind.TONE
                ? classifyToneAgainstForcedDot(timingEvent.durationMs(), forcedDotMs)
                : classifyGapAgainstForcedDot(timingEvent.durationMs(), forcedDotMs);
        if (shouldPreserveRawLetterGapClassification(timingEvent, classification, forcedDotMs)) {
            classification = CwTimingEvent.Classification.LETTER_GAP;
        } else if (shouldPreserveRawWordGapClassification(timingEvent, classification, forcedDotMs)) {
            classification = CwTimingEvent.Classification.WORD_GAP;
        }
        return new CwTimingEvent(
                timingEvent.kind(),
                classification,
                timingEvent.timestampMs(),
                timingEvent.durationMs(),
                forcedDotMs,
                forcedDotMs
        );
    }

    private CwTimingEvent.Classification classifyToneAgainstForcedDot(long durationMs, long dotMs) {
        double ratio = durationMs / (double) Math.max(1L, dotMs);
        if (ratio <= 1.8d) {
            return CwTimingEvent.Classification.DIT;
        }
        if (ratio <= 4.8d) {
            return CwTimingEvent.Classification.DAH;
        }
        return CwTimingEvent.Classification.UNKNOWN;
    }

    private boolean shouldPreserveRawLetterGapClassification(
            @Nullable CwTimingEvent timingEvent,
            CwTimingEvent.Classification classification,
            long forcedDotMs
    ) {
        if (timingEvent == null
                || timingEvent.kind() != CwTimingEvent.Kind.GAP
                || timingEvent.classification() != CwTimingEvent.Classification.LETTER_GAP
                || classification != CwTimingEvent.Classification.WORD_GAP) {
            return false;
        }
        long rawDotMs = Math.max(1L, timingEvent.dotEstimateMs());
        if (forcedDotMs >= rawDotMs) {
            return false;
        }
        double ratioToForcedDot = timingEvent.durationMs() / (double) Math.max(1L, forcedDotMs);
        return ratioToForcedDot <= FORCED_REPLAY_RAW_LETTER_GAP_WORD_PROMOTE_MAX_RATIO;
    }

    private boolean shouldPreserveRawWordGapClassification(
            @Nullable CwTimingEvent timingEvent,
            CwTimingEvent.Classification classification,
            long forcedDotMs
    ) {
        if (timingEvent == null
                || timingEvent.kind() != CwTimingEvent.Kind.GAP
                || timingEvent.classification() != CwTimingEvent.Classification.WORD_GAP
                || classification != CwTimingEvent.Classification.UNKNOWN) {
            return false;
        }
        long rawDotMs = Math.max(1L, timingEvent.dotEstimateMs());
        if (forcedDotMs >= rawDotMs) {
            return false;
        }
        double ratioToForcedDot = timingEvent.durationMs() / (double) Math.max(1L, forcedDotMs);
        return ratioToForcedDot <= FORCED_REPLAY_RAW_WORD_GAP_UNKNOWN_MAX_RATIO;
    }

    private CwTimingEvent.Classification classifyGapAgainstForcedDot(long durationMs, long dotMs) {
        double ratio = durationMs / (double) Math.max(1L, dotMs);
        if (ratio <= 1.8d) {
            return CwTimingEvent.Classification.INTRA_SYMBOL_GAP;
        }
        if (ratio <= FORCED_REPLAY_LETTER_GAP_MAX_RATIO) {
            return CwTimingEvent.Classification.LETTER_GAP;
        }
        // Keep replay gap ceilings aligned with the main timing model so
        // bootstrap repair does not drop genuine wide word gaps into UNKNOWN.
        if (ratio <= FORCED_REPLAY_WORD_GAP_MAX_RATIO) {
            return CwTimingEvent.Classification.WORD_GAP;
        }
        return CwTimingEvent.Classification.UNKNOWN;
    }

    private void trimPendingFinalEvents() {
        while (pendingFinalEvents.size() > MAX_PENDING_FINAL_EVENTS) {
            pendingFinalEvents.remove(0);
        }
        while (pendingFinalEvents.size() >= 2) {
            long firstTimestampMs = pendingFinalEvents.get(0).timestampMs();
            long lastTimestampMs = pendingFinalEvents.get(pendingFinalEvents.size() - 1).timestampMs();
            if ((lastTimestampMs - firstTimestampMs) <= MAX_PENDING_SPAN_MS) {
                break;
            }
            pendingFinalEvents.remove(0);
        }
    }

    private void trimBufferedTimingEvents() {
        while (bufferedTimingEvents.size() > MAX_BUFFERED_TIMING_EVENTS) {
            bufferedTimingEvents.remove(0);
        }
        while (bufferedTimingEvents.size() >= 2) {
            long firstTimestampMs = bufferedTimingEvents.get(0).timestampMs();
            long lastTimestampMs = bufferedTimingEvents.get(bufferedTimingEvents.size() - 1).timestampMs();
            if ((lastTimestampMs - firstTimestampMs) <= MAX_BUFFERED_TIMING_SPAN_MS) {
                break;
            }
            bufferedTimingEvents.remove(0);
        }
    }
}
