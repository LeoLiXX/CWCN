package org.bi9clt.cwcn.core.rig;

import android.content.Context;

import org.bi9clt.cwcn.core.tx.CwTxEngine;
import org.bi9clt.cwcn.core.tx.CwTxPlan;

import java.io.IOException;

public final class HamlibRigctldRigControlAdapter implements RigControlAdapter {
    private static final int DEFAULT_WPM = 18;
    private static final int DEFAULT_TONE_FREQUENCY_HZ = 650;

    private final CwTxEngine txEngine;
    private final HamlibRigctldSessionFactory sessionFactory;
    private final ConfigurationProvider configurationProvider;

    private volatile int wpm;
    private volatile int toneFrequencyHz;
    private volatile String lastAvailabilityNote;

    public HamlibRigctldRigControlAdapter(Context context) {
        this(
                new StoreBackedConfigurationProvider(context),
                new SocketHamlibRigctldSessionFactory(),
                DEFAULT_WPM,
                DEFAULT_TONE_FREQUENCY_HZ
        );
    }

    HamlibRigctldRigControlAdapter(
            ConfigurationProvider configurationProvider,
            HamlibRigctldSessionFactory sessionFactory,
            int wpm,
            int toneFrequencyHz
    ) {
        this.txEngine = new CwTxEngine();
        this.configurationProvider = configurationProvider;
        this.sessionFactory = sessionFactory;
        this.wpm = Math.max(5, wpm);
        this.toneFrequencyHz = Math.max(200, toneFrequencyHz);
    }

    @Override
    public String id() {
        return "hamlib-rigctld";
    }

    @Override
    public String displayName() {
        return "Hamlib rigctld Adapter";
    }

    @Override
    public String describeCapabilities() {
        return "Send CW through a Hamlib rigctld network session using send_morse, with optional KEYSPD and CWPITCH updates.";
    }

    @Override
    public String describeAvailability() {
        ActiveConfiguration configuration = configurationProvider.activeConfiguration();
        if (configuration == null) {
            return "Open Rig Setup, pin a network CAT profile, and select the Hamlib rigctld protocol family first.";
        }
        if (configuration.host == null || configuration.host.isEmpty()) {
            return "Hamlib rigctld is selected, but the network host is not set yet in Rig Setup.";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Configured for Hamlib rigctld at ")
                .append(configuration.host)
                .append(":")
                .append(configuration.port)
                .append(" using ")
                .append(configuration.profile.displayName())
                .append(".");
        if (lastAvailabilityNote != null && !lastAvailabilityNote.isEmpty()) {
            builder.append(" Last result: ").append(lastAvailabilityNote);
        } else {
            builder.append(" Connectivity is checked when TX starts.");
        }
        if (RigProfileFamilies.isYaesuFamily(configuration.profile)) {
            builder.append(" Yaesu note: confirm rigctld can already control the radio outside CWCN before longer CW tests.");
        } else if (RigProfileFamilies.isIcomFamily(configuration.profile)) {
            builder.append(" Icom note: confirm the rigctld bridge already speaks to the target CI-V radio before longer CW tests.");
        } else if (RigProfileFamilies.isKenwoodFamily(configuration.profile)) {
            builder.append(" Kenwood note: confirm the rigctld bridge already speaks to the target radio before longer CW tests.");
        }
        return builder.toString();
    }

    @Override
    public boolean supportsTextToCw() {
        return true;
    }

    @Override
    public boolean supportsPttControl() {
        return true;
    }

    @Override
    public boolean isReady() {
        ActiveConfiguration configuration = configurationProvider.activeConfiguration();
        return configuration != null && configuration.host != null && !configuration.host.isEmpty();
    }

    @Override
    public boolean supportsConfigurableTextToCwProfile() {
        return true;
    }

    @Override
    public boolean configureTextToCwProfile(int wpm, int toneFrequencyHz) {
        this.wpm = Math.max(5, wpm);
        this.toneFrequencyHz = Math.max(200, toneFrequencyHz);
        return true;
    }

    @Override
    public boolean keyDown() {
        return withSession(session -> session.setPtt(true), "PTT asserted via rigctld.");
    }

    @Override
    public boolean keyUp() {
        return withSession(session -> session.setPtt(false), "PTT released via rigctld.");
    }

    @Override
    public boolean sendText(String text) {
        ActiveConfiguration configuration = configurationProvider.activeConfiguration();
        if (configuration == null) {
            lastAvailabilityNote = "Rig Setup does not currently point to a Hamlib rigctld profile.";
            return false;
        }
        CwTxPlan plan = txEngine.buildPlan(text, wpm, toneFrequencyHz);
        if (plan.morsePreview().isEmpty()) {
            lastAvailabilityNote = "Text contained no Morse symbols supported by the current TX engine.";
            return false;
        }
        try (HamlibRigctldSession session = sessionFactory.open(configuration.host, configuration.port)) {
            session.setKeySpeedWpm(wpm);
            session.setCwPitchHz(toneFrequencyHz);
            boolean sent = session.sendMorse(plan.morsePreview());
            lastAvailabilityNote = sent
                    ? "Last send_morse request was accepted."
                    : "Last send_morse request was rejected by rigctld.";
            return sent;
        } catch (IOException exception) {
            lastAvailabilityNote = "Connection failed: " + exception.getMessage();
            return false;
        }
    }

    public static ProbeResult probeConfiguration(RigProfile profile, RigProfileSettings settings) {
        return probeConfiguration(profile, settings, new SocketHamlibRigctldSessionFactory());
    }

    static ProbeResult probeConfiguration(
            RigProfile profile,
            RigProfileSettings settings,
            HamlibRigctldSessionFactory sessionFactory
    ) {
        if (profile == null || !profile.hasCapability(RigCapability.NETWORK_CAT)) {
            return new ProbeResult(false, "Selected profile does not use network CAT.");
        }
        RigProfileSettings safeSettings = settings == null ? profile.defaultSettings() : settings;
        if (safeSettings.networkCatProtocolFamily() != CatProtocolFamily.HAMLIB_RIGCTLD) {
            return new ProbeResult(false, "Connection probe is currently implemented only for the Hamlib rigctld protocol family.");
        }
        String host = safeSettings.networkHost();
        if (host == null || host.trim().isEmpty()) {
            return new ProbeResult(false, "Set the rigctld host first.");
        }
        int port = Math.max(1, safeSettings.networkPort());
        boolean yaesuFamily = RigProfileFamilies.isYaesuFamily(profile);
        boolean icomFamily = RigProfileFamilies.isIcomFamily(profile);
        boolean kenwoodFamily = RigProfileFamilies.isKenwoodFamily(profile);
        try (HamlibRigctldSession session = sessionFactory.open(host, port)) {
            String info = session.getInfo();
            if (info == null || info.trim().isEmpty()) {
                return new ProbeResult(
                        true,
                        "Connected to rigctld at "
                                + host
                                + ":"
                                + port
                                + ", but the server returned no rig info."
                                + familyProbeTail(yaesuFamily, icomFamily, kenwoodFamily, false)
                );
            }
            String firstLine = info.split("\\R", 2)[0].trim();
            return new ProbeResult(
                    true,
                    "Connected to rigctld at "
                            + host
                            + ":"
                            + port
                            + ". Rig info: "
                            + firstLine
                            + familyProbeTail(yaesuFamily, icomFamily, kenwoodFamily, true)
            );
        } catch (IOException exception) {
            return new ProbeResult(
                    false,
                    "rigctld probe failed: "
                            + exception.getMessage()
                            + familyFailureTail(yaesuFamily, icomFamily, kenwoodFamily)
            );
        }
    }

    private static String familyProbeTail(
            boolean yaesuFamily,
            boolean icomFamily,
            boolean kenwoodFamily,
            boolean withRigInfo
    ) {
        if (yaesuFamily) {
            return withRigInfo
                    ? " Yaesu note: start with a short DIT or VVV bench before longer text."
                    : " For Yaesu FT-family radios, still confirm basic CAT/PTT behavior with a short bench send.";
        }
        if (icomFamily) {
            return withRigInfo
                    ? " Icom note: start with a short DIT or VVV bench before longer text."
                    : " For Icom-family radios, still confirm basic CAT/PTT behavior with a short bench send.";
        }
        if (kenwoodFamily) {
            return withRigInfo
                    ? " Kenwood note: start with a short DIT or VVV bench before longer text."
                    : " For Kenwood-family radios, still confirm basic CAT/PTT behavior with a short bench send.";
        }
        return "";
    }

    private static String familyFailureTail(
            boolean yaesuFamily,
            boolean icomFamily,
            boolean kenwoodFamily
    ) {
        if (yaesuFamily) {
            return " Yaesu note: verify the daemon is bound to the FT-family radio and responding outside CWCN first.";
        }
        if (icomFamily) {
            return " Icom note: verify the daemon is bound to the CI-V radio and responding outside CWCN first.";
        }
        if (kenwoodFamily) {
            return " Kenwood note: verify the daemon is bound to the radio and responding outside CWCN first.";
        }
        return "";
    }

    private boolean withSession(SessionAction action, String successNote) {
        ActiveConfiguration configuration = configurationProvider.activeConfiguration();
        if (configuration == null) {
            lastAvailabilityNote = "Rig Setup does not currently point to a Hamlib rigctld profile.";
            return false;
        }
        try (HamlibRigctldSession session = sessionFactory.open(configuration.host, configuration.port)) {
            boolean result = action.run(session);
            lastAvailabilityNote = result ? successNote : "rigctld rejected the latest control request.";
            return result;
        } catch (IOException exception) {
            lastAvailabilityNote = "Connection failed: " + exception.getMessage();
            return false;
        }
    }

    interface ConfigurationProvider {
        ActiveConfiguration activeConfiguration();
    }

    interface SessionAction {
        boolean run(HamlibRigctldSession session) throws IOException;
    }

    public static final class ProbeResult {
        private final boolean success;
        private final String message;

        ProbeResult(boolean success, String message) {
            this.success = success;
            this.message = message == null ? "" : message;
        }

        public boolean success() {
            return success;
        }

        public String message() {
            return message;
        }
    }

    static final class ActiveConfiguration {
        private final RigProfile profile;
        private final String host;
        private final int port;

        ActiveConfiguration(RigProfile profile, String host, int port) {
            this.profile = profile;
            this.host = host;
            this.port = Math.max(1, port);
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
            if (profile == null || !profile.hasCapability(RigCapability.NETWORK_CAT)) {
                return null;
            }
            RigProfileSettings settings = selectionStore.loadSettings(profile);
            if (settings.networkCatProtocolFamily() != CatProtocolFamily.HAMLIB_RIGCTLD) {
                return null;
            }
            return new ActiveConfiguration(profile, settings.networkHost(), settings.networkPort());
        }
    }
}
