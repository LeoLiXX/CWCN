package org.bi9clt.cwcn.core.rig;

import android.content.Context;

import java.util.Locale;

public final class SerialCatRigControlAdapter implements RigControlAdapter {
    private final ConfigurationProvider configurationProvider;
    private final SerialCatSessionFactory sessionFactory;

    private volatile String lastAvailabilityNote;

    public SerialCatRigControlAdapter(Context context) {
        this(
                new StoreBackedConfigurationProvider(context),
                new AndroidUsbSerialCatSessionFactory(context)
        );
    }

    SerialCatRigControlAdapter(
            ConfigurationProvider configurationProvider,
            SerialCatSessionFactory sessionFactory
    ) {
        this.configurationProvider = configurationProvider;
        this.sessionFactory = sessionFactory;
    }

    @Override
    public String id() {
        return "generic-cat";
    }

    @Override
    public String displayName() {
        return "Native Serial CAT Adapter";
    }

    @Override
    public String describeCapabilities() {
        return "Shared native serial CAT control entry for Yaesu-style, Icom CI-V, and Kenwood-style rigs. Current phase: readiness/probe plumbing first, family-specific TX control next.";
    }

    @Override
    public String describeAvailability() {
        ActiveConfiguration configuration = configurationProvider.activeConfiguration();
        if (configuration == null) {
            return "Open Rig Setup, pin a serial CAT profile, and select Yaesu-style CAT, Icom CI-V, or Kenwood-style CAT first.";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Configured for ")
                .append(configuration.profile.displayName())
                .append(" using ")
                .append(configuration.settings.serialCatProtocolFamily().displayName())
                .append(" at ")
                .append(configuration.settings.serialCatBaudRate())
                .append(" baud.");
        if (configuration.settings.serialCatPortHint() != null) {
            builder.append(" Port hint: ").append(configuration.settings.serialCatPortHint()).append(".");
        }
        builder.append(" ").append(sessionFactory.describeAvailability(configuration.settings.serialCatPortHint()));
        builder.append(" ").append(familyStatus(configuration.settings.serialCatProtocolFamily()));
        if (lastAvailabilityNote != null && !lastAvailabilityNote.isEmpty()) {
            builder.append(" Last result: ").append(lastAvailabilityNote);
        }
        return builder.toString();
    }

    @Override
    public boolean supportsTextToCw() {
        return false;
    }

    @Override
    public boolean supportsPttControl() {
        return false;
    }

    @Override
    public boolean isReady() {
        ActiveConfiguration configuration = configurationProvider.activeConfiguration();
        if (configuration == null) {
            return false;
        }
        if (!supportsFamily(configuration.settings.serialCatProtocolFamily())) {
            return false;
        }
        return transportLooksUsable(sessionFactory.describeAvailability(configuration.settings.serialCatPortHint()));
    }

    @Override
    public boolean keyDown() {
        lastAvailabilityNote = "Native serial CAT control commands are not attached yet. Validate the link in Rig Setup first.";
        return false;
    }

    @Override
    public boolean keyUp() {
        lastAvailabilityNote = "Native serial CAT control commands are not attached yet. Validate the link in Rig Setup first.";
        return false;
    }

    @Override
    public boolean sendText(String text) {
        lastAvailabilityNote = "Native serial CAT text TX is not attached yet. Use rigctld, USB keyer, or Audio VOX for active TX right now.";
        return false;
    }

    private boolean supportsFamily(CatProtocolFamily family) {
        return family == CatProtocolFamily.YAESU_STYLE
                || family == CatProtocolFamily.ICOM_CIV
                || family == CatProtocolFamily.KENWOOD_STYLE;
    }

    private boolean transportLooksUsable(String availability) {
        if (availability == null) {
            return false;
        }
        String normalized = availability.trim().toLowerCase(Locale.US);
        return normalized.contains("permission is available")
                || normalized.contains("session is open");
    }

    private String familyStatus(CatProtocolFamily family) {
        if (family == CatProtocolFamily.YAESU_STYLE) {
            return "Yaesu native serial path is probe-ready; family-specific TX/PTT commands are the next layer.";
        }
        if (family == CatProtocolFamily.ICOM_CIV) {
            return "Icom native CI-V path is probe-ready; family-specific TX/PTT commands are the next layer.";
        }
        if (family == CatProtocolFamily.KENWOOD_STYLE) {
            return "Kenwood native serial path is probe-ready; family-specific TX/PTT commands are the next layer.";
        }
        return "This CAT family is not attached to the native serial control adapter yet.";
    }

    interface ConfigurationProvider {
        ActiveConfiguration activeConfiguration();
    }

    static final class ActiveConfiguration {
        private final RigProfile profile;
        private final RigProfileSettings settings;

        ActiveConfiguration(RigProfile profile, RigProfileSettings settings) {
            this.profile = profile;
            this.settings = settings;
        }
    }

    private static final class StoreBackedConfigurationProvider implements ConfigurationProvider {
        private final RigSelectionStore selectionStore;

        private StoreBackedConfigurationProvider(Context context) {
            this.selectionStore = new RigSelectionStore(context);
        }

        @Override
        public ActiveConfiguration activeConfiguration() {
            RigProfile profile = selectionStore.selectedProfile();
            if (profile == null || !profile.hasCapability(RigCapability.SERIAL_CAT)) {
                return null;
            }
            return new ActiveConfiguration(profile, selectionStore.loadSettings(profile));
        }
    }
}
