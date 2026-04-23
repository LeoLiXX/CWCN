package org.bi9clt.cwcn.core.rig;

public final class DisconnectedSerialKeyerPort implements SerialKeyerPort {
    private final String id;
    private final String displayName;
    private final String availabilityMessage;

    public DisconnectedSerialKeyerPort(String id, String displayName, String availabilityMessage) {
        this.id = id;
        this.displayName = displayName;
        this.availabilityMessage = availabilityMessage;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public boolean isOpen() {
        return false;
    }

    @Override
    public String describeAvailability() {
        return availabilityMessage;
    }

    @Override
    public boolean setRts(boolean enabled) {
        return false;
    }

    @Override
    public boolean setDtr(boolean enabled) {
        return false;
    }

    @Override
    public void close() {
    }

    @Override
    public boolean retainsDiagnosticDetails() {
        return true;
    }

    @Override
    public String diagnosticCode() {
        return id;
    }
}
