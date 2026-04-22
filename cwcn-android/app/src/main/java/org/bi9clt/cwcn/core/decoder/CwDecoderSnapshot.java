package org.bi9clt.cwcn.core.decoder;

public final class CwDecoderSnapshot {
    private final String currentSequence;
    private final String decodedText;
    private final int totalSymbols;
    private final int totalCharacters;
    private final CwDecodeEvent lastDecodeEvent;

    public CwDecoderSnapshot(
            String currentSequence,
            String decodedText,
            int totalSymbols,
            int totalCharacters,
            CwDecodeEvent lastDecodeEvent
    ) {
        this.currentSequence = currentSequence;
        this.decodedText = decodedText;
        this.totalSymbols = totalSymbols;
        this.totalCharacters = totalCharacters;
        this.lastDecodeEvent = lastDecodeEvent;
    }

    public String currentSequence() {
        return currentSequence;
    }

    public String decodedText() {
        return decodedText;
    }

    public int totalSymbols() {
        return totalSymbols;
    }

    public int totalCharacters() {
        return totalCharacters;
    }

    public CwDecodeEvent lastDecodeEvent() {
        return lastDecodeEvent;
    }
}
