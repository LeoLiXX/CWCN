package org.bi9clt.cwcn.core.interpreter;

import java.util.List;

public final class CwInterpreterSnapshot {
    private final String rawText;
    private final String normalizedText;
    private final List<CwInterpretedToken> tokens;
    private final String primaryCallsignCandidate;
    private final List<String> callsignCandidates;
    private final List<String> phraseHints;
    private final CwInterpretationEvent lastEvent;

    public CwInterpreterSnapshot(
            String rawText,
            String normalizedText,
            List<CwInterpretedToken> tokens,
            String primaryCallsignCandidate,
            List<String> callsignCandidates,
            List<String> phraseHints,
            CwInterpretationEvent lastEvent
    ) {
        this.rawText = rawText;
        this.normalizedText = normalizedText;
        this.tokens = tokens;
        this.primaryCallsignCandidate = primaryCallsignCandidate;
        this.callsignCandidates = callsignCandidates;
        this.phraseHints = phraseHints;
        this.lastEvent = lastEvent;
    }

    public String rawText() {
        return rawText;
    }

    public String normalizedText() {
        return normalizedText;
    }

    public List<CwInterpretedToken> tokens() {
        return tokens;
    }

    public String primaryCallsignCandidate() {
        return primaryCallsignCandidate;
    }

    public List<String> callsignCandidates() {
        return callsignCandidates;
    }

    public List<String> phraseHints() {
        return phraseHints;
    }

    public CwInterpretationEvent lastEvent() {
        return lastEvent;
    }
}
