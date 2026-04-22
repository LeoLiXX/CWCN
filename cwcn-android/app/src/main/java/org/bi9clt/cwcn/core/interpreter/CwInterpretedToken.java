package org.bi9clt.cwcn.core.interpreter;

public final class CwInterpretedToken {
    public enum Type {
        CALLSIGN_CANDIDATE,
        CQ,
        DE,
        QRZ,
        REPORT,
        ACK,
        REQUEST,
        CONTROL,
        THANKS,
        AGAIN,
        FREE_TEXT
    }

    private final String rawText;
    private final String normalizedText;
    private final Type type;

    public CwInterpretedToken(String rawText, String normalizedText, Type type) {
        this.rawText = rawText;
        this.normalizedText = normalizedText;
        this.type = type;
    }

    public String rawText() {
        return rawText;
    }

    public String normalizedText() {
        return normalizedText;
    }

    public Type type() {
        return type;
    }
}
