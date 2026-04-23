package org.bi9clt.cwcn.ui.tx;

import org.bi9clt.cwcn.core.tx.CwTxBackend;

public final class TxBackendOption {
    private final CwTxBackend backend;

    public TxBackendOption(CwTxBackend backend) {
        this.backend = backend;
    }

    public CwTxBackend backend() {
        return backend;
    }

    @Override
    public String toString() {
        return backend.displayName();
    }
}
