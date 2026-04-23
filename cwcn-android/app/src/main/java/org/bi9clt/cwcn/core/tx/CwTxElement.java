package org.bi9clt.cwcn.core.tx;

public final class CwTxElement {
    public enum Kind {
        KEY_DOWN,
        KEY_UP
    }

    private final Kind kind;
    private final int durationMs;
    private final String sourceSymbol;

    public CwTxElement(Kind kind, int durationMs, String sourceSymbol) {
        this.kind = kind;
        this.durationMs = Math.max(0, durationMs);
        this.sourceSymbol = sourceSymbol == null ? "" : sourceSymbol;
    }

    public Kind kind() {
        return kind;
    }

    public int durationMs() {
        return durationMs;
    }

    public String sourceSymbol() {
        return sourceSymbol;
    }
}
