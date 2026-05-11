package org.bi9clt.cwcn.core.rig;

import android.content.Context;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.bi9clt.cwcn.core.tx.CwTxAudioOutput;
import org.bi9clt.cwcn.core.tx.CwTxElement;
import org.bi9clt.cwcn.core.tx.CwTxEngine;
import org.bi9clt.cwcn.core.tx.CwTxPlaybackSnapshot;
import org.bi9clt.cwcn.core.tx.CwTxPlan;
import org.bi9clt.cwcn.core.tx.CwTxRunner;
import org.bi9clt.cwcn.core.tx.CwTxState;

public final class SerialCatRigControlAdapter implements RigControlAdapter {
    private static final int DEFAULT_WPM = 18;
    private static final int DEFAULT_TONE_FREQUENCY_HZ = 650;
    private static final int COMMAND_TIMEOUT_MS = 1500;
    private static final int CIV_CONTROLLER_ADDRESS = 0xE0;

    private final ConfigurationProvider configurationProvider;
    private final SerialCatSessionFactory sessionFactory;
    private final DedicatedKeyingPortFactory keyerPortFactory;
    private final CwTxEngine txEngine;

    private volatile String lastAvailabilityNote;
    private volatile int wpm;
    private volatile int toneFrequencyHz;
    private volatile SerialKeyerPort openKeyingPort;
    private volatile CwTxPlaybackSnapshot lastSnapshot;
    private volatile CwTxRunner txRunner;

    public SerialCatRigControlAdapter(Context context) {
        this(
                new StoreBackedConfigurationProvider(context),
                new AndroidUsbSerialCatSessionFactory(context),
                new AndroidUsbProbedSerialKeyerPortFactory(context)
        );
    }

    SerialCatRigControlAdapter(
            ConfigurationProvider configurationProvider,
            SerialCatSessionFactory sessionFactory,
            DedicatedKeyingPortFactory keyerPortFactory
    ) {
        this.configurationProvider = configurationProvider;
        this.sessionFactory = sessionFactory;
        this.keyerPortFactory = keyerPortFactory;
        this.txEngine = new CwTxEngine();
        this.wpm = DEFAULT_WPM;
        this.toneFrequencyHz = DEFAULT_TONE_FREQUENCY_HZ;
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
        return "Shared native serial CAT control entry for Yaesu-style, Icom CI-V, and Kenwood-style rigs. Current phase: Yaesu and Icom family TX/PTT smoke-control first; Kenwood remains probe-first.";
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
        if (configuration.settings.serialCatKeyingPortHint() != null) {
            builder.append(" Keying port: ")
                    .append(configuration.settings.serialCatKeyingPortHint())
                    .append(" via lines ")
                    .append(renderAssertedKeyingLines(configuration.settings))
                    .append(" / ")
                    .append(configuration.settings.serialCatKeyingPolarity())
                    .append(".");
        }
        builder.append(" ").append(sessionFactory.describeAvailability(configuration.settings.serialCatPortHint()));
        if (configuration.settings.serialCatProtocolFamily() == CatProtocolFamily.YAESU_STYLE
                && configuration.settings.serialCatKeyingPortHint() != null) {
            builder.append(" Keying availability: ")
                    .append(keyerPortFactory.describeAvailability(configuration.settings.serialCatKeyingPortHint()));
        }
        builder.append(" ").append(familyStatus(configuration.settings.serialCatProtocolFamily()));
        if (lastAvailabilityNote != null && !lastAvailabilityNote.isEmpty()) {
            builder.append(" Last result: ").append(lastAvailabilityNote);
        }
        return builder.toString();
    }

    @Override
    public boolean supportsTextToCw() {
        ActiveConfiguration configuration = configurationProvider.activeConfiguration();
        return configuration != null
                && configuration.settings.serialCatProtocolFamily() == CatProtocolFamily.YAESU_STYLE;
    }

    @Override
    public boolean supportsPttControl() {
        ActiveConfiguration configuration = configurationProvider.activeConfiguration();
        return configuration != null && supportsNativePtt(configuration);
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
        if (configuration.settings.serialCatProtocolFamily() == CatProtocolFamily.YAESU_STYLE
                && configuration.settings.serialCatKeyingPortHint() != null) {
            return keyerPortFactory.canOpenPort(configuration.settings.serialCatKeyingPortHint());
        }
        return transportLooksUsable(sessionFactory.describeAvailability(configuration.settings.serialCatPortHint()));
    }

    @Override
    public boolean keyDown() {
        ActiveConfiguration configuration = configurationProvider.activeConfiguration();
        if (shouldUseDedicatedKeyingPort(configuration)) {
            boolean result = keyingLineUp(configuration);
            if (result) {
                lastAvailabilityNote = "Dedicated serial keying asserted on "
                        + renderAssertedKeyingLines(configuration.settings)
                        + " using "
                        + configuration.settings.serialCatKeyingPolarity()
                        + ".";
            }
            return result;
        }
        return withSession("CAT TX asserted via native serial CAT.", this::executeKeyDown);
    }

    @Override
    public boolean keyUp() {
        ActiveConfiguration configuration = configurationProvider.activeConfiguration();
        if (shouldUseDedicatedKeyingPort(configuration)) {
            boolean result = keyingLineDown(configuration);
            if (result) {
                lastAvailabilityNote = "Dedicated serial keying released on "
                        + renderAssertedKeyingLines(configuration.settings)
                        + ".";
            }
            return result;
        }
        return withSession("CAT TX released via native serial CAT.", this::executeKeyUp);
    }

    @Override
    public boolean sendText(String text) {
        ActiveConfiguration configuration = configurationProvider.activeConfiguration();
        if (configuration == null) {
            lastAvailabilityNote = "Open Rig Setup, pin a serial CAT profile, and validate the serial link first.";
            return false;
        }
        if (!supportsTextToCw()) {
            lastAvailabilityNote = "Native serial CAT text TX is currently attached for Yaesu-style profiles first.";
            return false;
        }
        if (!isReady()) {
            lastAvailabilityNote = describeAvailability();
            return false;
        }
        CwTxRunner activeRunner = txRunner;
        if (activeRunner != null && activeRunner.isRunning()) {
            lastAvailabilityNote = "Serial CAT text TX is already running.";
            return false;
        }
        CwTxPlan plan = txEngine.buildPlan(text, wpm, toneFrequencyHz);
        if (plan.elements().isEmpty()) {
            lastAvailabilityNote = "Text contained no Morse symbols supported by the current TX engine.";
            return false;
        }
        lastSnapshot = null;
        if (shouldUseDedicatedKeyingPort(configuration)) {
            SerialKeyerPort port = ensureOpenKeyingPort(configuration);
            if (port == null || !port.isOpen()) {
                lastAvailabilityNote = keyerPortFactory.describeAvailability(configuration.settings.serialCatKeyingPortHint());
                return false;
            }
            CwTxRunner runner = new CwTxRunner(new DedicatedSerialKeyingAudioOutput(port, configuration.settings));
            txRunner = runner;
            runner.runPlanBlocking(plan, this::recordSnapshot);
            return lastSnapshot != null && lastSnapshot.state() == CwTxState.COMPLETED;
        }
        String availability = sessionFactory.describeAvailability(configuration.settings.serialCatPortHint());
        if (!transportLooksUsable(availability)) {
            lastAvailabilityNote = availability;
            return false;
        }
        try (SerialCatSession session = sessionFactory.openSession(
                configuration.settings.serialCatPortHint(),
                configuration.settings.serialCatBaudRate())) {
            applyYaesuCwProfile(session);
            CwTxRunner runner = new CwTxRunner(new SessionBackedCatAudioOutput(session));
            txRunner = runner;
            runner.runPlanBlocking(plan, this::recordSnapshot);
            return lastSnapshot != null && lastSnapshot.state() == CwTxState.COMPLETED;
        } catch (IOException | RuntimeException exception) {
            lastAvailabilityNote = "Native serial CAT text TX failed: " + safeMessage(exception);
            return false;
        } finally {
            txRunner = null;
        }
    }

    @Override
    public boolean stopTextTransmission() {
        CwTxRunner runner = txRunner;
        if (runner != null) {
            runner.stop();
        }
        return keyUp();
    }

    @Override
    public boolean supportsConfigurableTextToCwProfile() {
        ActiveConfiguration configuration = configurationProvider.activeConfiguration();
        return configuration != null && supportsNativeCwProfile(configuration);
    }

    @Override
    public boolean configureTextToCwProfile(int wpm, int toneFrequencyHz) {
        this.wpm = Math.max(4, Math.min(60, wpm));
        this.toneFrequencyHz = Math.max(300, Math.min(1050, toneFrequencyHz));
        return true;
    }

    @Override
    public boolean usesWpmForTextToCwProfile() {
        return supportsConfigurableTextToCwProfile();
    }

    @Override
    public boolean usesToneFrequencyForTextToCwProfile() {
        return supportsConfigurableTextToCwProfile();
    }

    @Override
    public CwTxPlaybackSnapshot currentTxPlaybackSnapshot() {
        return lastSnapshot;
    }

    private boolean supportsFamily(CatProtocolFamily family) {
        return family == CatProtocolFamily.YAESU_STYLE
                || family == CatProtocolFamily.ICOM_CIV
                || family == CatProtocolFamily.KENWOOD_STYLE;
    }

    private boolean supportsNativePtt(ActiveConfiguration configuration) {
        if (!configuration.profile.hasCapability(RigCapability.PTT_CONTROL)) {
            return false;
        }
        if (configuration.settings.serialCatProtocolFamily() == CatProtocolFamily.YAESU_STYLE) {
            return true;
        }
        return configuration.settings.serialCatProtocolFamily() == CatProtocolFamily.ICOM_CIV
                && configuration.settings.serialCatCivAddressHex() != null;
    }

    private boolean supportsNativeCwProfile(ActiveConfiguration configuration) {
        return configuration.profile.hasCapability(RigCapability.PTT_CONTROL)
                && configuration.settings.serialCatProtocolFamily() == CatProtocolFamily.YAESU_STYLE;
    }

    private boolean shouldUseDedicatedKeyingPort(ActiveConfiguration configuration) {
        return configuration != null
                && configuration.settings.serialCatProtocolFamily() == CatProtocolFamily.YAESU_STYLE
                && configuration.settings.serialCatKeyingPortHint() != null;
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
            return "Yaesu native serial path is probe-ready. CW TX should prefer a dedicated keying port (RTS/DTR) instead of CAT TX1/TX0.";
        }
        if (family == CatProtocolFamily.ICOM_CIV) {
            return "Icom native CI-V path is probe-ready, and a minimal CI-V PTT pulse path is now attached first.";
        }
        if (family == CatProtocolFamily.KENWOOD_STYLE) {
            return "Kenwood native serial path is probe-ready; family-specific TX/PTT commands are the next layer.";
        }
        return "This CAT family is not attached to the native serial control adapter yet.";
    }

    private boolean withSession(String successNote, SessionAction action) {
        ActiveConfiguration configuration = configurationProvider.activeConfiguration();
        if (configuration == null) {
            lastAvailabilityNote = "Open Rig Setup, pin a serial CAT profile, and validate the serial link first.";
            return false;
        }
        if (!supportsNativePtt(configuration)) {
            lastAvailabilityNote = "Native serial CAT control is not attached for "
                    + configuration.settings.serialCatProtocolFamily().displayName()
                    + " yet.";
            return false;
        }
        String availability = sessionFactory.describeAvailability(configuration.settings.serialCatPortHint());
        if (!transportLooksUsable(availability)) {
            lastAvailabilityNote = availability;
            return false;
        }
        try (SerialCatSession session = sessionFactory.openSession(
                configuration.settings.serialCatPortHint(),
                configuration.settings.serialCatBaudRate())) {
            boolean result = action.run(configuration, session);
            lastAvailabilityNote = result ? successNote : "Native serial CAT command was not accepted.";
            return result;
        } catch (IOException | RuntimeException exception) {
            lastAvailabilityNote = "Native serial CAT command failed: " + safeMessage(exception);
            return false;
        }
    }

    private boolean executeKeyDown(ActiveConfiguration configuration, SerialCatSession session) throws IOException {
        if (configuration.settings.serialCatProtocolFamily() == CatProtocolFamily.YAESU_STYLE) {
            if (configuration.settings.serialCatKeyingPortHint() != null) {
                return keyingLineUp(configuration);
            }
            applyYaesuCwProfile(session);
            session.send("TX1;", COMMAND_TIMEOUT_MS);
            return true;
        }
        if (configuration.settings.serialCatProtocolFamily() == CatProtocolFamily.ICOM_CIV) {
            session.send(buildIcomPttCommand(configuration.settings, true), COMMAND_TIMEOUT_MS);
            return true;
        }
        return false;
    }

    private boolean executeKeyUp(ActiveConfiguration configuration, SerialCatSession session) throws IOException {
        if (configuration.settings.serialCatProtocolFamily() == CatProtocolFamily.YAESU_STYLE) {
            if (configuration.settings.serialCatKeyingPortHint() != null) {
                return keyingLineDown(configuration);
            }
            session.send("TX0;", COMMAND_TIMEOUT_MS);
            return true;
        }
        if (configuration.settings.serialCatProtocolFamily() == CatProtocolFamily.ICOM_CIV) {
            session.send(buildIcomPttCommand(configuration.settings, false), COMMAND_TIMEOUT_MS);
            return true;
        }
        return false;
    }

    private void applyYaesuCwProfile(SerialCatSession session) throws IOException {
        session.send(buildYaesuKeySpeedCommand(wpm), COMMAND_TIMEOUT_MS);
        session.send(buildYaesuKeyPitchCommand(toneFrequencyHz), COMMAND_TIMEOUT_MS);
    }

    private void recordSnapshot(CwTxPlaybackSnapshot snapshot) {
        lastSnapshot = snapshot;
        if (snapshot == null) {
            return;
        }
        if (snapshot.state() == CwTxState.COMPLETED) {
            lastAvailabilityNote = "Native serial CAT text TX completed.";
        } else if (snapshot.state() == CwTxState.STOPPED) {
            lastAvailabilityNote = "Native serial CAT text TX stopped.";
        } else if (snapshot.state() == CwTxState.ERROR) {
            lastAvailabilityNote = snapshot.statusMessage();
        } else if (snapshot.state() == CwTxState.PLAYING) {
            lastAvailabilityNote = "Native serial CAT text TX running.";
        }
    }

    private boolean keyingLineUp(ActiveConfiguration configuration) {
        SerialKeyerPort port = ensureOpenKeyingPort(configuration);
        if (port == null || !port.isOpen()) {
            lastAvailabilityNote = keyerPortFactory.describeAvailability(configuration.settings.serialCatKeyingPortHint());
            return false;
        }
        boolean keyedLevel = configuration.settings.serialCatKeyingPolarity().assertedLevel();
        boolean keyed = applyConfiguredKeyingLevels(port, configuration.settings, keyedLevel);
        if (!keyed) {
            lastAvailabilityNote = "Dedicated serial keying line could not be asserted.";
        }
        return keyed;
    }

    private boolean keyingLineDown(ActiveConfiguration configuration) {
        SerialKeyerPort port = ensureOpenKeyingPort(configuration);
        if (port == null || !port.isOpen()) {
            lastAvailabilityNote = keyerPortFactory.describeAvailability(configuration.settings.serialCatKeyingPortHint());
            return false;
        }
        boolean releasedLevel = !configuration.settings.serialCatKeyingPolarity().assertedLevel();
        boolean released = applyConfiguredKeyingLevels(port, configuration.settings, releasedLevel);
        if (!released) {
            lastAvailabilityNote = "Dedicated serial keying line could not be released.";
        }
        return released;
    }

    private static boolean applyConfiguredKeyingLevels(
            SerialKeyerPort port,
            RigProfileSettings settings,
            boolean level
    ) {
        boolean touchedLine = false;
        if (settings.serialCatAssertRtsDuringKeying()) {
            touchedLine = true;
            if (!port.setRts(level)) {
                return false;
            }
        }
        if (settings.serialCatAssertDtrDuringKeying()) {
            touchedLine = true;
            if (!port.setDtr(level)) {
                return false;
            }
        }
        if (!touchedLine) {
            return settings.serialCatKeyLine() == SerialKeyerTxOutput.KeyLine.DTR
                    ? port.setDtr(level)
                    : port.setRts(level);
        }
        return true;
    }

    private static String renderAssertedKeyingLines(RigProfileSettings settings) {
        if (settings == null) {
            return "(none)";
        }
        boolean useRts = settings.serialCatAssertRtsDuringKeying();
        boolean useDtr = settings.serialCatAssertDtrDuringKeying();
        if (useRts && useDtr) {
            return "RTS + DTR";
        }
        if (useRts) {
            return "RTS";
        }
        if (useDtr) {
            return "DTR";
        }
        return settings.serialCatKeyLine().name();
    }

    private SerialKeyerPort ensureOpenKeyingPort(ActiveConfiguration configuration) {
        if (configuration == null || configuration.settings.serialCatKeyingPortHint() == null) {
            return null;
        }
        SerialKeyerPort current = openKeyingPort;
        if (current != null && current.isOpen()) {
            return current;
        }
        synchronized (this) {
            current = openKeyingPort;
            if (current != null && current.isOpen()) {
                return current;
            }
            openKeyingPort = keyerPortFactory.openPort(configuration.settings.serialCatKeyingPortHint());
            return openKeyingPort;
        }
    }

    private static final class DedicatedSerialKeyingAudioOutput implements CwTxAudioOutput {
        private final SerialKeyerPort port;
        private final RigProfileSettings settings;

        private DedicatedSerialKeyingAudioOutput(SerialKeyerPort port, RigProfileSettings settings) {
            this.port = port;
            this.settings = settings;
        }

        @Override
        public void playTone(int frequencyHz, int durationMs) throws InterruptedException {
            if (!applyConfiguredKeyingLevels(port, settings, settings.serialCatKeyingPolarity().assertedLevel())) {
                throw new IllegalStateException("Dedicated serial keying line could not be asserted.");
            }
            sleepQuietly(durationMs);
        }

        @Override
        public void playSilence(int durationMs) throws InterruptedException {
            if (!applyConfiguredKeyingLevels(port, settings, !settings.serialCatKeyingPolarity().assertedLevel())) {
                throw new IllegalStateException("Dedicated serial keying line could not be released.");
            }
            sleepQuietly(durationMs);
        }

        @Override
        public void finish() {
            stop();
        }

        @Override
        public void stop() {
            applyConfiguredKeyingLevels(port, settings, !settings.serialCatKeyingPolarity().assertedLevel());
        }
    }

    private static final class SessionBackedCatAudioOutput implements CwTxAudioOutput {
        private final SerialCatSession session;

        private SessionBackedCatAudioOutput(SerialCatSession session) {
            this.session = session;
        }

        @Override
        public void playTone(int frequencyHz, int durationMs) throws InterruptedException {
            sendCommand("TX1;");
            sleepQuietly(durationMs);
        }

        @Override
        public void playSilence(int durationMs) throws InterruptedException {
            sendCommand("TX0;");
            sleepQuietly(durationMs);
        }

        @Override
        public void finish() {
            stop();
        }

        @Override
        public void stop() {
            try {
                session.send("TX0;", COMMAND_TIMEOUT_MS);
            } catch (IOException ignored) {
                // Best-effort release path during stop/finalize.
            }
        }

        private void sendCommand(String command) {
            try {
                session.send(command, COMMAND_TIMEOUT_MS);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    private static void sleepQuietly(int durationMs) throws InterruptedException {
        int remainingMs = Math.max(0, durationMs);
        while (remainingMs > 0) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Serial CAT TX interrupted");
            }
            int sliceMs = Math.min(remainingMs, 25);
            Thread.sleep(sliceMs);
            remainingMs -= sliceMs;
        }
    }

    public static ControlResult testPttPulse(
            RigProfile profile,
            RigProfileSettings settings,
            SerialCatSessionFactory sessionFactory,
            int wpm,
            int toneFrequencyHz,
            int holdMs
    ) {
        if (profile == null || !profile.hasCapability(RigCapability.SERIAL_CAT)) {
            return new ControlResult(false, "Selected profile does not use serial CAT.");
        }
        if (settings == null) {
            settings = profile.defaultSettings();
        }
        if (settings.serialCatProtocolFamily() != CatProtocolFamily.YAESU_STYLE
                && settings.serialCatProtocolFamily() != CatProtocolFamily.ICOM_CIV) {
            return new ControlResult(
                    false,
                    "Serial CAT PTT pulse test is currently attached for Yaesu-style CAT and Icom CI-V first."
            );
        }
        if (sessionFactory == null) {
            return new ControlResult(false, "Serial CAT session factory is unavailable.");
        }
        if (settings.serialCatProtocolFamily() == CatProtocolFamily.ICOM_CIV
                && settings.serialCatCivAddressHex() == null) {
            return new ControlResult(false, "Set the CI-V address first, then retry the serial CAT PTT pulse test.");
        }
        try (SerialCatSession session = sessionFactory.openSession(
                settings.serialCatPortHint(),
                settings.serialCatBaudRate())) {
            if (settings.serialCatProtocolFamily() == CatProtocolFamily.YAESU_STYLE) {
                session.send(buildYaesuKeySpeedCommand(wpm), COMMAND_TIMEOUT_MS);
                session.send(buildYaesuKeyPitchCommand(toneFrequencyHz), COMMAND_TIMEOUT_MS);
                session.send("TX1;", COMMAND_TIMEOUT_MS);
            } else {
                session.send(buildIcomPttCommand(settings, true), COMMAND_TIMEOUT_MS);
            }
            try {
                Thread.sleep(Math.max(50, Math.min(1500, holdMs)));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                if (settings.serialCatProtocolFamily() == CatProtocolFamily.YAESU_STYLE) {
                    session.send("TX0;", COMMAND_TIMEOUT_MS);
                } else {
                    session.send(buildIcomPttCommand(settings, false), COMMAND_TIMEOUT_MS);
                }
                return new ControlResult(false, "Serial CAT PTT pulse test was interrupted.");
            }
            if (settings.serialCatProtocolFamily() == CatProtocolFamily.YAESU_STYLE) {
                session.send("TX0;", COMMAND_TIMEOUT_MS);
            } else {
                session.send(buildIcomPttCommand(settings, false), COMMAND_TIMEOUT_MS);
            }
            return new ControlResult(
                    true,
                    settings.serialCatProtocolFamily() == CatProtocolFamily.YAESU_STYLE
                            ? "Native serial CAT PTT pulse completed. Yaesu-style TX was asserted briefly and then released."
                            : "Native serial CAT PTT pulse completed. Icom CI-V PTT was asserted briefly and then released."
            );
        } catch (IOException | RuntimeException exception) {
            return new ControlResult(false, "Native serial CAT PTT pulse failed: " + safeMessage(exception));
        }
    }

    public static ControlResult testDedicatedKeyingPulse(
            RigProfile profile,
            RigProfileSettings settings,
            DedicatedKeyingPortFactory keyingPortFactory,
            int holdMs
    ) {
        if (profile == null || !profile.hasCapability(RigCapability.SERIAL_CAT)) {
            return new ControlResult(false, "Selected profile does not use serial CAT.");
        }
        if (settings == null) {
            settings = profile.defaultSettings();
        }
        if (settings.serialCatProtocolFamily() != CatProtocolFamily.YAESU_STYLE) {
            return new ControlResult(false, "Dedicated keying pulse is currently attached for Yaesu-style serial CAT first.");
        }
        if (settings.serialCatKeyingPortHint() == null || settings.serialCatKeyingPortHint().trim().isEmpty()) {
            return new ControlResult(false, "Choose the dedicated keying port first, then retry the keying pulse test.");
        }
        if (keyingPortFactory == null) {
            return new ControlResult(false, "Dedicated keying port factory is unavailable.");
        }
        SerialKeyerPort port = keyingPortFactory.openPort(settings.serialCatKeyingPortHint());
        if (port == null || !port.isOpen()) {
            return new ControlResult(false, keyingPortFactory.describeAvailability(settings.serialCatKeyingPortHint()));
        }
        boolean asserted = false;
        try {
            boolean assertedLevel = settings.serialCatKeyingPolarity().assertedLevel();
            asserted = applyConfiguredKeyingLevels(port, settings, assertedLevel);
            if (!asserted) {
                return new ControlResult(false, "Dedicated keying line could not be asserted.");
            }
            try {
                Thread.sleep(Math.max(50, Math.min(1500, holdMs)));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                boolean releasedLevel = !settings.serialCatKeyingPolarity().assertedLevel();
                applyConfiguredKeyingLevels(port, settings, releasedLevel);
                return new ControlResult(false, "Dedicated keying pulse test was interrupted.");
            }
            boolean releasedLevel = !settings.serialCatKeyingPolarity().assertedLevel();
            boolean released = applyConfiguredKeyingLevels(port, settings, releasedLevel);
            if (!released) {
                return new ControlResult(false, "Dedicated keying line asserted, but release failed.");
            }
            return new ControlResult(
                    true,
                    "Dedicated keying pulse completed on "
                            + renderAssertedKeyingLines(settings)
                            + " / "
                            + settings.serialCatKeyingPolarity()
                            + " via "
                            + settings.serialCatKeyingPortHint()
                            + "."
            );
        } catch (RuntimeException exception) {
            return new ControlResult(false, "Dedicated keying pulse failed: " + safeMessage(exception));
        } finally {
            port.close();
        }
    }

    public static ControlResult testDedicatedKeyingHold(
            RigProfile profile,
            RigProfileSettings settings,
            DedicatedKeyingPortFactory keyingPortFactory,
            int holdMs
    ) {
        if (profile == null || !profile.hasCapability(RigCapability.SERIAL_CAT)) {
            return new ControlResult(false, "Selected profile does not use serial CAT.");
        }
        if (settings == null) {
            settings = profile.defaultSettings();
        }
        if (settings.serialCatProtocolFamily() != CatProtocolFamily.YAESU_STYLE) {
            return new ControlResult(false, "Dedicated keying hold is currently attached for Yaesu-style serial CAT first.");
        }
        if (settings.serialCatKeyingPortHint() == null || settings.serialCatKeyingPortHint().trim().isEmpty()) {
            return new ControlResult(false, "Choose the dedicated keying port first, then retry the keying hold test.");
        }
        if (keyingPortFactory == null) {
            return new ControlResult(false, "Dedicated keying port factory is unavailable.");
        }
        SerialKeyerPort port = keyingPortFactory.openPort(settings.serialCatKeyingPortHint());
        if (port == null || !port.isOpen()) {
            return new ControlResult(false, keyingPortFactory.describeAvailability(settings.serialCatKeyingPortHint()));
        }
        try {
            boolean assertedLevel = settings.serialCatKeyingPolarity().assertedLevel();
            boolean asserted = applyConfiguredKeyingLevels(port, settings, assertedLevel);
            if (!asserted) {
                return new ControlResult(false, "Dedicated keying line could not be asserted for the hold test.");
            }
            try {
                Thread.sleep(Math.max(200, Math.min(2500, holdMs)));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return new ControlResult(false, "Dedicated keying hold test was interrupted.");
            } finally {
                boolean releasedLevel = !settings.serialCatKeyingPolarity().assertedLevel();
                applyConfiguredKeyingLevels(port, settings, releasedLevel);
            }
            return new ControlResult(
                    true,
                    "Dedicated keying hold completed on "
                            + settings.serialCatKeyingPortHint()
                            + " / "
                            + renderAssertedKeyingLines(settings)
                            + " / "
                            + settings.serialCatKeyingPolarity()
                            + ". Watch whether TX stayed active during the 1.5s hold, not only at the edges."
            );
        } catch (RuntimeException exception) {
            return new ControlResult(false, "Dedicated keying hold failed: " + safeMessage(exception));
        } finally {
            port.close();
        }
    }

    public static ControlResult testDedicatedKeyingOpenClose(
            RigProfile profile,
            RigProfileSettings settings,
            DedicatedKeyingPortFactory keyingPortFactory,
            int holdMs
    ) {
        if (profile == null || !profile.hasCapability(RigCapability.SERIAL_CAT)) {
            return new ControlResult(false, "Selected profile does not use serial CAT.");
        }
        if (settings == null) {
            settings = profile.defaultSettings();
        }
        if (settings.serialCatProtocolFamily() != CatProtocolFamily.YAESU_STYLE) {
            return new ControlResult(false, "Dedicated keying port open/close test is currently attached for Yaesu-style serial CAT first.");
        }
        if (settings.serialCatKeyingPortHint() == null || settings.serialCatKeyingPortHint().trim().isEmpty()) {
            return new ControlResult(false, "Choose the dedicated keying port first, then retry the open/close test.");
        }
        if (keyingPortFactory == null) {
            return new ControlResult(false, "Dedicated keying port factory is unavailable.");
        }
        SerialKeyerPort port = keyingPortFactory.openPort(settings.serialCatKeyingPortHint());
        if (port == null || !port.isOpen()) {
            return new ControlResult(false, keyingPortFactory.describeAvailability(settings.serialCatKeyingPortHint()));
        }
        try {
            try {
                Thread.sleep(Math.max(200, Math.min(2000, holdMs)));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return new ControlResult(false, "Dedicated keying port open/close test was interrupted.");
            }
            return new ControlResult(
                    true,
                    "Dedicated keying port open/close test completed on "
                            + settings.serialCatKeyingPortHint()
                            + ". No DTR/RTS line change was requested; watch whether TX still flashed on port open or close."
            );
        } finally {
            port.close();
        }
    }

    public static ControlResult testDedicatedKeyingTimingLab(
            RigProfile profile,
            RigProfileSettings settings,
            DedicatedKeyingPortFactory keyingPortFactory,
            TimingLabPlan plan
    ) {
        if (profile == null || !profile.hasCapability(RigCapability.SERIAL_CAT)) {
            return new ControlResult(false, "Selected profile does not use serial CAT.");
        }
        if (settings == null) {
            settings = profile.defaultSettings();
        }
        if (plan == null) {
            return new ControlResult(false, "Timing lab plan is missing.");
        }
        if (settings.serialCatProtocolFamily() != CatProtocolFamily.YAESU_STYLE) {
            return new ControlResult(false, "Dedicated keying timing lab is currently attached for Yaesu-style serial CAT first.");
        }
        if (settings.serialCatKeyingPortHint() == null || settings.serialCatKeyingPortHint().trim().isEmpty()) {
            return new ControlResult(false, "Choose the dedicated keying port first, then retry the timing lab.");
        }
        if (keyingPortFactory == null) {
            return new ControlResult(false, "Dedicated keying port factory is unavailable.");
        }
        SerialKeyerPort port = keyingPortFactory.openPort(settings.serialCatKeyingPortHint());
        if (port == null || !port.isOpen()) {
            return new ControlResult(false, keyingPortFactory.describeAvailability(settings.serialCatKeyingPortHint()));
        }
        try {
            return runDedicatedKeyingTimingLab(port, settings, plan);
        } catch (RuntimeException exception) {
            return new ControlResult(false, "Dedicated keying timing lab failed: " + safeMessage(exception));
        } finally {
            port.close();
        }
    }

    public static ControlResult testDedicatedKeyingShortPulseLab(
            RigProfile profile,
            RigProfileSettings settings,
            DedicatedKeyingPortFactory keyingPortFactory,
            ShortPulseLabPlan plan
    ) {
        if (profile == null || !profile.hasCapability(RigCapability.SERIAL_CAT)) {
            return new ControlResult(false, "Selected profile does not use serial CAT.");
        }
        if (settings == null) {
            settings = profile.defaultSettings();
        }
        if (plan == null) {
            return new ControlResult(false, "Short pulse lab plan is missing.");
        }
        if (settings.serialCatProtocolFamily() != CatProtocolFamily.YAESU_STYLE) {
            return new ControlResult(false, "Dedicated short pulse lab is currently attached for Yaesu-style serial CAT first.");
        }
        if (settings.serialCatKeyingPortHint() == null || settings.serialCatKeyingPortHint().trim().isEmpty()) {
            return new ControlResult(false, "Choose the dedicated keying port first, then retry the short pulse lab.");
        }
        if (keyingPortFactory == null) {
            return new ControlResult(false, "Dedicated keying port factory is unavailable.");
        }
        SerialKeyerPort port = keyingPortFactory.openPort(settings.serialCatKeyingPortHint());
        if (port == null || !port.isOpen()) {
            return new ControlResult(false, keyingPortFactory.describeAvailability(settings.serialCatKeyingPortHint()));
        }
        try {
            return runDedicatedShortPulseLab(port, settings, plan);
        } catch (RuntimeException exception) {
            return new ControlResult(false, "Dedicated short pulse lab failed: " + safeMessage(exception));
        } finally {
            port.close();
        }
    }

    private static ControlResult runDedicatedKeyingTimingLab(
            SerialKeyerPort port,
            RigProfileSettings settings,
            TimingLabPlan plan
    ) {
        boolean assertedLevel = settings.serialCatKeyingPolarity().assertedLevel();
        boolean releasedLevel = !assertedLevel;
        LineSelection selection = resolveLineSelection(settings);
        if (!setLinesToLevel(port, selection, releasedLevel, TimingLabOrder.SIMULTANEOUS, 0)) {
            return new ControlResult(false, "Timing lab could not normalize the keying lines to the released state.");
        }
        if (!sleepChecked(plan.preDelayMs())) {
            return new ControlResult(false, "Dedicated keying timing lab was interrupted during pre-delay.");
        }
        if (!setLinesToLevel(port, selection, assertedLevel, plan.assertOrder(), plan.interLineGapMs())) {
            setLinesToLevel(port, selection, releasedLevel, TimingLabOrder.SIMULTANEOUS, 0);
            return new ControlResult(false, "Timing lab could not assert the requested keying pattern.");
        }
        if (!sleepChecked(plan.holdMs())) {
            setLinesToLevel(
                    port,
                    selection,
                    releasedLevel,
                    plan.releaseOrder().toTimingOrder(),
                    plan.releaseGapMs()
            );
            return new ControlResult(false, "Dedicated keying timing lab was interrupted during hold time.");
        }
        if (!setLinesToLevel(
                port,
                selection,
                releasedLevel,
                plan.releaseOrder().toTimingOrder(),
                plan.releaseGapMs()
        )) {
            return new ControlResult(false, "Timing lab asserted the keying lines, but release failed.");
        }
        return new ControlResult(
                true,
                "Timing lab completed on "
                        + settings.serialCatKeyingPortHint()
                        + " with assert="
                        + selection.render()
                        + ", order="
                        + plan.assertOrder().displayName()
                        + ", preDelay="
                        + plan.preDelayMs()
                        + "ms, hold="
                        + plan.holdMs()
                        + "ms, interLineGap="
                        + plan.interLineGapMs()
                        + "ms, release="
                        + plan.releaseOrder().displayName()
                        + ", releaseGap="
                        + plan.releaseGapMs()
                        + "ms, polarity="
                        + settings.serialCatKeyingPolarity()
                        + "."
        );
    }

    private static ControlResult runDedicatedShortPulseLab(
            SerialKeyerPort port,
            RigProfileSettings settings,
            ShortPulseLabPlan plan
    ) {
        boolean assertedLevel = settings.serialCatKeyingPolarity().assertedLevel();
        boolean releasedLevel = !assertedLevel;
        LineSelection selection = resolveLineSelection(settings);
        if (!setLinesToLevel(port, selection, releasedLevel, TimingLabOrder.SIMULTANEOUS, 0)) {
            return new ControlResult(false, "Short pulse lab could not normalize the keying lines to the released state.");
        }
        if (!sleepChecked(plan.preDelayMs())) {
            return new ControlResult(false, "Dedicated short pulse lab was interrupted during pre-delay.");
        }
        CwTxPlan txPlan = new CwTxEngine().buildPlan(plan.preset().text(), plan.wpm(), settings.defaultToneFrequencyHz());
        List<CwTxElement> elements = txPlan.elements();
        if (elements.isEmpty()) {
            return new ControlResult(false, "Short pulse lab built no CW elements for the selected preset.");
        }
        for (CwTxElement element : elements) {
            if (element.kind() == CwTxElement.Kind.KEY_DOWN) {
                if (!setLinesToLevel(port, selection, assertedLevel, plan.assertOrder(), plan.interLineGapMs())) {
                    setLinesToLevel(
                            port,
                            selection,
                            releasedLevel,
                            plan.releaseOrder().toTimingOrder(),
                            plan.releaseLineGapMs()
                    );
                    return new ControlResult(false, "Short pulse lab could not assert the keying pattern for " + plan.preset().displayName() + ".");
                }
                if (!sleepChecked(element.durationMs() + plan.tailHoldMs())) {
                    setLinesToLevel(
                            port,
                            selection,
                            releasedLevel,
                            plan.releaseOrder().toTimingOrder(),
                            plan.releaseLineGapMs()
                    );
                    return new ControlResult(false, "Dedicated short pulse lab was interrupted during a key-down segment.");
                }
                continue;
            }

            if (!setLinesToLevel(
                    port,
                    selection,
                    releasedLevel,
                    plan.releaseOrder().toTimingOrder(),
                    plan.releaseLineGapMs()
            )) {
                return new ControlResult(false, "Short pulse lab could not release the keying lines during " + plan.preset().displayName() + ".");
            }
            if (!sleepChecked(element.durationMs() + plan.extraReleaseGapMs())) {
                return new ControlResult(false, "Dedicated short pulse lab was interrupted during a key-up segment.");
            }
        }
        if (!setLinesToLevel(
                port,
                selection,
                releasedLevel,
                plan.releaseOrder().toTimingOrder(),
                plan.releaseLineGapMs()
        )) {
            return new ControlResult(false, "Short pulse lab completed the pattern, but final release failed.");
        }
        return new ControlResult(
                true,
                "Short pulse lab completed "
                        + plan.preset().displayName()
                        + " ("
                        + txPlan.morsePreview()
                        + ") at "
                        + plan.wpm()
                        + " WPM on "
                        + settings.serialCatKeyingPortHint()
                        + ". preDelay="
                        + plan.preDelayMs()
                        + "ms, tailHold="
                        + plan.tailHoldMs()
                        + "ms, extraReleaseGap="
                        + plan.extraReleaseGapMs()
                        + "ms, assertOrder="
                        + plan.assertOrder().displayName()
                        + ", releaseOrder="
                        + plan.releaseOrder().displayName()
                        + ", releaseLineGap="
                        + plan.releaseLineGapMs()
                        + "ms."
        );
    }

    private static boolean setLinesToLevel(
            SerialKeyerPort port,
            LineSelection selection,
            boolean level,
            TimingLabOrder order,
            int gapMs
    ) {
        if (selection.useRts && selection.useDtr) {
            if (order == TimingLabOrder.DTR_THEN_RTS) {
                return setLine(port, SerialKeyerTxOutput.KeyLine.DTR, level)
                        && sleepChecked(gapMs)
                        && setLine(port, SerialKeyerTxOutput.KeyLine.RTS, level);
            }
            if (order == TimingLabOrder.RTS_THEN_DTR) {
                return setLine(port, SerialKeyerTxOutput.KeyLine.RTS, level)
                        && sleepChecked(gapMs)
                        && setLine(port, SerialKeyerTxOutput.KeyLine.DTR, level);
            }
            return setLine(port, SerialKeyerTxOutput.KeyLine.RTS, level)
                    && setLine(port, SerialKeyerTxOutput.KeyLine.DTR, level);
        }
        if (selection.useRts) {
            return setLine(port, SerialKeyerTxOutput.KeyLine.RTS, level);
        }
        return setLine(port, SerialKeyerTxOutput.KeyLine.DTR, level);
    }

    private static boolean setLine(SerialKeyerPort port, SerialKeyerTxOutput.KeyLine line, boolean level) {
        return line == SerialKeyerTxOutput.KeyLine.DTR
                ? port.setDtr(level)
                : port.setRts(level);
    }

    private static boolean sleepChecked(int durationMs) {
        int bounded = Math.max(0, durationMs);
        if (bounded <= 0) {
            return true;
        }
        try {
            Thread.sleep(bounded);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static LineSelection resolveLineSelection(RigProfileSettings settings) {
        boolean useRts = settings.serialCatAssertRtsDuringKeying();
        boolean useDtr = settings.serialCatAssertDtrDuringKeying();
        if (!useRts && !useDtr) {
            if (settings.serialCatKeyLine() == SerialKeyerTxOutput.KeyLine.DTR) {
                useDtr = true;
            } else {
                useRts = true;
            }
        }
        return new LineSelection(useRts, useDtr);
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().trim().isEmpty()) {
            return throwable == null ? "unknown failure" : throwable.getClass().getSimpleName();
        }
        return throwable.getMessage().trim();
    }

    static String buildYaesuKeySpeedCommand(int wpm) {
        int normalizedWpm = Math.max(4, Math.min(60, wpm));
        return String.format(Locale.US, "KS%03d;", normalizedWpm);
    }

    static String buildYaesuKeyPitchCommand(int toneFrequencyHz) {
        int normalizedTone = Math.max(300, Math.min(1050, toneFrequencyHz));
        int index = Math.round((normalizedTone - 300) / 50.0f);
        index = Math.max(0, Math.min(15, index));
        return String.format(Locale.US, "KP%02d;", index);
    }

    static byte[] buildIcomPttCommand(RigProfileSettings settings, boolean enabled) {
        if (settings == null || settings.serialCatCivAddressHex() == null) {
            throw new IllegalArgumentException("CI-V address must be set before building an Icom PTT command.");
        }
        int radioAddress = Integer.parseInt(settings.serialCatCivAddressHex(), 16);
        return new byte[] {
                (byte) 0xFE,
                (byte) 0xFE,
                (byte) radioAddress,
                (byte) CIV_CONTROLLER_ADDRESS,
                0x1C,
                0x00,
                enabled ? (byte) 0x01 : (byte) 0x00,
                (byte) 0xFD
        };
    }

    interface SessionAction {
        boolean run(ActiveConfiguration configuration, SerialCatSession session) throws IOException;
    }

    public static final class ControlResult {
        private final boolean success;
        private final String message;

        public ControlResult(boolean success, String message) {
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

    public enum TimingLabOrder {
        SIMULTANEOUS("Simultaneous"),
        RTS_THEN_DTR("RTS -> DTR"),
        DTR_THEN_RTS("DTR -> RTS");

        private final String displayName;

        TimingLabOrder(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public enum TimingLabReleaseOrder {
        TOGETHER("Together"),
        RELEASE_RTS_FIRST("RTS first"),
        RELEASE_DTR_FIRST("DTR first");

        private final String displayName;

        TimingLabReleaseOrder(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }

        TimingLabOrder toTimingOrder() {
            if (this == RELEASE_RTS_FIRST) {
                return TimingLabOrder.RTS_THEN_DTR;
            }
            if (this == RELEASE_DTR_FIRST) {
                return TimingLabOrder.DTR_THEN_RTS;
            }
            return TimingLabOrder.SIMULTANEOUS;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public static final class TimingLabPlan {
        private final TimingLabOrder assertOrder;
        private final TimingLabReleaseOrder releaseOrder;
        private final int preDelayMs;
        private final int holdMs;
        private final int interLineGapMs;
        private final int releaseGapMs;

        public TimingLabPlan(
                TimingLabOrder assertOrder,
                TimingLabReleaseOrder releaseOrder,
                int preDelayMs,
                int holdMs,
                int interLineGapMs,
                int releaseGapMs
        ) {
            this.assertOrder = assertOrder == null ? TimingLabOrder.SIMULTANEOUS : assertOrder;
            this.releaseOrder = releaseOrder == null ? TimingLabReleaseOrder.TOGETHER : releaseOrder;
            this.preDelayMs = sanitizeTiming(preDelayMs, 0, 2000);
            this.holdMs = sanitizeTiming(holdMs, 600, 5000);
            this.interLineGapMs = sanitizeTiming(interLineGapMs, 30, 1000);
            this.releaseGapMs = sanitizeTiming(releaseGapMs, 30, 1000);
        }

        public TimingLabOrder assertOrder() {
            return assertOrder;
        }

        public TimingLabReleaseOrder releaseOrder() {
            return releaseOrder;
        }

        public int preDelayMs() {
            return preDelayMs;
        }

        public int holdMs() {
            return holdMs;
        }

        public int interLineGapMs() {
            return interLineGapMs;
        }

        public int releaseGapMs() {
            return releaseGapMs;
        }

        private static int sanitizeTiming(int value, int fallback, int max) {
            int normalized = value < 0 ? fallback : value;
            return Math.max(0, Math.min(max, normalized));
        }
    }

    public enum ShortPulseLabPreset {
        SINGLE_E("Single E", "E"),
        SINGLE_T("Single T", "T"),
        EEE("EEE", "EEE"),
        VVV("VVV", "VVV");

        private final String displayName;
        private final String text;

        ShortPulseLabPreset(String displayName, String text) {
            this.displayName = displayName;
            this.text = text;
        }

        public String displayName() {
            return displayName;
        }

        public String text() {
            return text;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public static final class ShortPulseLabPlan {
        private final ShortPulseLabPreset preset;
        private final int wpm;
        private final TimingLabOrder assertOrder;
        private final TimingLabReleaseOrder releaseOrder;
        private final int preDelayMs;
        private final int tailHoldMs;
        private final int extraReleaseGapMs;
        private final int interLineGapMs;
        private final int releaseLineGapMs;

        public ShortPulseLabPlan(
                ShortPulseLabPreset preset,
                int wpm,
                TimingLabOrder assertOrder,
                TimingLabReleaseOrder releaseOrder,
                int preDelayMs,
                int tailHoldMs,
                int extraReleaseGapMs,
                int interLineGapMs,
                int releaseLineGapMs
        ) {
            this.preset = preset == null ? ShortPulseLabPreset.SINGLE_E : preset;
            this.wpm = Math.max(5, Math.min(60, wpm));
            this.assertOrder = assertOrder == null ? TimingLabOrder.SIMULTANEOUS : assertOrder;
            this.releaseOrder = releaseOrder == null ? TimingLabReleaseOrder.TOGETHER : releaseOrder;
            this.preDelayMs = TimingLabPlan.sanitizeTiming(preDelayMs, 0, 2000);
            this.tailHoldMs = TimingLabPlan.sanitizeTiming(tailHoldMs, 0, 500);
            this.extraReleaseGapMs = TimingLabPlan.sanitizeTiming(extraReleaseGapMs, 0, 800);
            this.interLineGapMs = TimingLabPlan.sanitizeTiming(interLineGapMs, 0, 1000);
            this.releaseLineGapMs = TimingLabPlan.sanitizeTiming(releaseLineGapMs, 0, 1000);
        }

        public ShortPulseLabPreset preset() {
            return preset;
        }

        public int wpm() {
            return wpm;
        }

        public TimingLabOrder assertOrder() {
            return assertOrder;
        }

        public TimingLabReleaseOrder releaseOrder() {
            return releaseOrder;
        }

        public int preDelayMs() {
            return preDelayMs;
        }

        public int tailHoldMs() {
            return tailHoldMs;
        }

        public int extraReleaseGapMs() {
            return extraReleaseGapMs;
        }

        public int interLineGapMs() {
            return interLineGapMs;
        }

        public int releaseLineGapMs() {
            return releaseLineGapMs;
        }
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

    private static final class LineSelection {
        private final boolean useRts;
        private final boolean useDtr;

        private LineSelection(boolean useRts, boolean useDtr) {
            this.useRts = useRts;
            this.useDtr = useDtr;
        }

        private String render() {
            if (useRts && useDtr) {
                return "RTS + DTR";
            }
            if (useRts) {
                return "RTS";
            }
            return "DTR";
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
