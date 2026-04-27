package org.bi9clt.cwcn.core.rig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public final class RigProfile {
    private final String id;
    private final String displayName;
    private final String vendorLabel;
    private final String modelLabel;
    private final RigTransport.TransportKind transportKind;
    private final String adapterId;
    private final RigSupportLevel supportLevel;
    private final EnumSet<RigCapability> capabilities;
    private final String summary;
    private final String setupNotes;
    private final List<String> knownConstraints;
    private final RigProfileSettings defaultSettings;

    public RigProfile(
            String id,
            String displayName,
            String vendorLabel,
            String modelLabel,
            RigTransport.TransportKind transportKind,
            String adapterId,
            RigSupportLevel supportLevel,
            EnumSet<RigCapability> capabilities,
            String summary,
            String setupNotes,
            List<String> knownConstraints
    ) {
        this(
                id,
                displayName,
                vendorLabel,
                modelLabel,
                transportKind,
                adapterId,
                supportLevel,
                capabilities,
                summary,
                setupNotes,
                knownConstraints,
                null
        );
    }

    public RigProfile(
            String id,
            String displayName,
            String vendorLabel,
            String modelLabel,
            RigTransport.TransportKind transportKind,
            String adapterId,
            RigSupportLevel supportLevel,
            EnumSet<RigCapability> capabilities,
            String summary,
            String setupNotes,
            List<String> knownConstraints,
            RigProfileSettings defaultSettings
    ) {
        this.id = id;
        this.displayName = displayName;
        this.vendorLabel = vendorLabel;
        this.modelLabel = modelLabel;
        this.transportKind = transportKind;
        this.adapterId = adapterId;
        this.supportLevel = supportLevel;
        this.capabilities = capabilities == null || capabilities.isEmpty()
                ? EnumSet.noneOf(RigCapability.class)
                : EnumSet.copyOf(capabilities);
        this.summary = summary;
        this.setupNotes = setupNotes;
        this.knownConstraints = sanitizeKnownConstraints(knownConstraints);
        this.defaultSettings = defaultSettings == null ? RigProfileSettings.defaults() : defaultSettings;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String vendorLabel() {
        return vendorLabel;
    }

    public String modelLabel() {
        return modelLabel;
    }

    public RigTransport.TransportKind transportKind() {
        return transportKind;
    }

    public String adapterId() {
        return adapterId;
    }

    public RigSupportLevel supportLevel() {
        return supportLevel;
    }

    public EnumSet<RigCapability> capabilities() {
        return capabilities.clone();
    }

    public String summary() {
        return summary;
    }

    public String setupNotes() {
        return setupNotes;
    }

    public List<String> knownConstraints() {
        return new ArrayList<>(knownConstraints);
    }

    public RigProfileSettings defaultSettings() {
        return defaultSettings;
    }

    public boolean hasCapability(RigCapability capability) {
        return capability != null && capabilities.contains(capability);
    }

    public String capabilitySummary() {
        if (capabilities.isEmpty()) {
            return "(none)";
        }
        ArrayList<String> labels = new ArrayList<>();
        for (RigCapability capability : capabilities) {
            labels.add(capability.displayName());
        }
        return String.join(", ", labels);
    }

    @Override
    public String toString() {
        return displayName;
    }

    private List<String> sanitizeKnownConstraints(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<String> cleaned = new ArrayList<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (trimmed.isEmpty() || cleaned.contains(trimmed)) {
                continue;
            }
            cleaned.add(trimmed);
        }
        return cleaned.isEmpty() ? Collections.emptyList() : cleaned;
    }
}
