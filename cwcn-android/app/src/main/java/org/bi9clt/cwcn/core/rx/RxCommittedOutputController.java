package org.bi9clt.cwcn.core.rx;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.core.decoder.CwDecodeEvent;
import org.bi9clt.cwcn.core.interpreter.CwInterpreter;
import org.bi9clt.cwcn.core.interpreter.CwInterpreterSnapshot;
import org.bi9clt.cwcn.core.qso.QsoDraftSnapshot;
import org.bi9clt.cwcn.core.qso.QsoStateMachine;

import java.util.List;

/**
 * Shared owner for committed decode output side effects.
 *
 * <p>This controller keeps RAW interpreter text, unknown fallback tracking, and
 * optional semantic/QSO state in sync while committed decode events are admitted
 * live or replayed later during turn-local rebuild.</p>
 */
public final class RxCommittedOutputController {
    @Nullable private final CwInterpreter rawInterpreter;
    @Nullable private final RxUnknownFallbackTracker unknownFallbackTracker;
    @Nullable private final CwInterpreter semanticInterpreter;
    @Nullable private final QsoStateMachine qsoStateMachine;
    @Nullable private final RxRawCommitGate rawCommitGate;

    public RxCommittedOutputController(
            @Nullable CwInterpreter rawInterpreter,
            @Nullable RxUnknownFallbackTracker unknownFallbackTracker,
            @Nullable CwInterpreter semanticInterpreter,
            @Nullable QsoStateMachine qsoStateMachine,
            @Nullable RxRawCommitGate rawCommitGate
    ) {
        this.rawInterpreter = rawInterpreter;
        this.unknownFallbackTracker = unknownFallbackTracker;
        this.semanticInterpreter = semanticInterpreter;
        this.qsoStateMachine = qsoStateMachine;
        this.rawCommitGate = rawCommitGate;
    }

    public void reset() {
        if (rawInterpreter != null) {
            rawInterpreter.reset();
        }
        if (unknownFallbackTracker != null) {
            unknownFallbackTracker.reset();
        }
        if (semanticInterpreter != null) {
            semanticInterpreter.reset();
        }
        if (qsoStateMachine != null) {
            qsoStateMachine.reset();
        }
        syncCommittedOutputText();
    }

    public void processCommittedDecodeEvent(@Nullable CwDecodeEvent decodeEvent) {
        if (decodeEvent == null || rawInterpreter == null) {
            return;
        }
        rawInterpreter.process(decodeEvent);
        if (unknownFallbackTracker != null) {
            unknownFallbackTracker.process(decodeEvent);
        }
        if (semanticInterpreter != null) {
            semanticInterpreter.process(decodeEvent);
            if (qsoStateMachine != null) {
                qsoStateMachine.process(
                        semanticInterpreter.snapshot(),
                        decodeEvent.timestampMs()
                );
            }
        }
    }

    public void processCommittedDecodeEvents(@Nullable List<CwDecodeEvent> decodeEvents) {
        if (decodeEvents == null || decodeEvents.isEmpty()) {
            return;
        }
        for (CwDecodeEvent decodeEvent : decodeEvents) {
            processCommittedDecodeEvent(decodeEvent);
        }
    }

    public void rebuildFromCommittedDecodeEvents(@Nullable List<CwDecodeEvent> decodeEvents) {
        reset();
        processCommittedDecodeEvents(decodeEvents);
        syncCommittedOutputText();
    }

    @Nullable
    public CwInterpreterSnapshot rawSnapshot() {
        return rawInterpreter == null ? null : rawInterpreter.snapshot();
    }

    public RxUnknownFallbackSuggestion fallbackSuggestion() {
        if (unknownFallbackTracker == null) {
            CwInterpreterSnapshot rawSnapshot = rawSnapshot();
            return RxUnknownFallbackSuggestion.none(rawSnapshot == null ? "" : rawSnapshot.rawText());
        }
        return unknownFallbackTracker.snapshot();
    }

    @Nullable
    public QsoDraftSnapshot qsoSnapshot() {
        return qsoStateMachine == null ? null : qsoStateMachine.snapshot();
    }

    public boolean semanticPipelineEnabled() {
        return semanticInterpreter != null && qsoStateMachine != null;
    }

    private void syncCommittedOutputText() {
        if (rawCommitGate == null) {
            return;
        }
        CwInterpreterSnapshot rawSnapshot = rawSnapshot();
        rawCommitGate.replaceCommittedOutputText(
                rawSnapshot == null ? "" : rawSnapshot.rawText()
        );
    }
}
