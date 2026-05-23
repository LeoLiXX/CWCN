package org.bi9clt.cwcn.core.app;

import androidx.annotation.Nullable;

public final class TxTemplateEntry {
    private final String id;
    private final String name;
    private final String body;

    public TxTemplateEntry(String id, String name, String body) {
        this.id = id == null ? "" : id.trim();
        this.name = name == null ? "" : name.trim();
        this.body = body == null ? "" : body.trim();
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String body() {
        return body;
    }

    public TxTemplateEntry withNameAndBody(@Nullable String updatedName, @Nullable String updatedBody) {
        return new TxTemplateEntry(
                id,
                updatedName == null ? name : updatedName,
                updatedBody == null ? body : updatedBody
        );
    }
}
