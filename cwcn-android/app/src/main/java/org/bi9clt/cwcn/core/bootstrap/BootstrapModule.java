package org.bi9clt.cwcn.core.bootstrap;

public final class BootstrapModule {
    private final String id;
    private final String title;
    private final String description;
    private final ModuleStatus status;

    public BootstrapModule(String id, String title, String description, ModuleStatus status) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.status = status;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public ModuleStatus status() {
        return status;
    }
}
