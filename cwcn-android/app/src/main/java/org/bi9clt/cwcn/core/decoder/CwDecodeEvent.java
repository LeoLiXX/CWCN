package org.bi9clt.cwcn.core.decoder;

public final class CwDecodeEvent {
    public enum Type {
        SYMBOL_APPENDED,
        CHARACTER_DECODED,
        WORD_BREAK
    }

    private final Type type;
    private final long timestampMs;
    private final String currentSequence;
    private final String outputText;
    private final String emittedValue;
    private final String sourceSequence;
    private final boolean unknownCharacter;

    public CwDecodeEvent(Type type, long timestampMs, String currentSequence, String outputText, String emittedValue) {
        this(type, timestampMs, currentSequence, outputText, emittedValue, currentSequence, false);
    }

    public CwDecodeEvent(
            Type type,
            long timestampMs,
            String currentSequence,
            String outputText,
            String emittedValue,
            String sourceSequence,
            boolean unknownCharacter
    ) {
        this.type = type;
        this.timestampMs = timestampMs;
        this.currentSequence = currentSequence;
        this.outputText = outputText;
        this.emittedValue = emittedValue;
        this.sourceSequence = sourceSequence;
        this.unknownCharacter = unknownCharacter;
    }

    public Type type() {
        return type;
    }

    public long timestampMs() {
        return timestampMs;
    }

    public String currentSequence() {
        return currentSequence;
    }

    public String outputText() {
        return outputText;
    }

    public String emittedValue() {
        return emittedValue;
    }

    public String sourceSequence() {
        return sourceSequence;
    }

    public boolean unknownCharacter() {
        return unknownCharacter;
    }
}
