package org.bi9clt.cwcn.core.rig;

public final class PortAvailability {
    public enum Stage {
        READY,
        NO_CONTEXT,
        NO_MANAGER,
        NO_DEVICE,
        MULTIPLE_CANDIDATES,
        TARGET_MISSING,
        NO_SUPPORTED_PORT,
        NO_PERMISSION,
        UNAVAILABLE,
        ERROR
    }

    private final Stage stage;
    private final String message;

    public PortAvailability(Stage stage, String message) {
        this.stage = stage == null ? Stage.ERROR : stage;
        this.message = message == null ? "" : message;
    }

    public Stage stage() {
        return stage;
    }

    public String message() {
        return message;
    }

    public boolean isReady() {
        return stage == Stage.READY;
    }
}
