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
        return "原生串口 CAT 适配器";
    }

    @Override
    public String describeCapabilities() {
        return "统一承接 Yaesu 风格、Icom CI-V 和 Kenwood 风格的原生串口 CAT 控制。当前阶段优先收口 Yaesu 与 Icom 家族的 TX/PTT，Kenwood 仍以探测验证为主。";
    }

    @Override
    public String describeAvailability() {
        ActiveConfiguration configuration = configurationProvider.activeConfiguration();
        if (configuration == null) {
            return "请先在电台配置中固定一条串口 CAT 路径，并选择 Yaesu 风格 CAT、Icom CI-V 或 Kenwood 风格 CAT。";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("已配置电台路径：")
                .append(configuration.profile.displayName())
                .append("，协议族：")
                .append(configuration.settings.serialCatProtocolFamily().displayName())
                .append("，波特率：")
                .append(configuration.settings.serialCatBaudRate())
                .append("。");
        if (configuration.settings.serialCatPortHint() != null) {
            builder.append(" CAT 端口：").append(configuration.settings.serialCatPortHint()).append("。");
        }
        if (configuration.settings.serialCatKeyingPortHint() != null) {
            builder.append(" 键控口：")
                    .append(configuration.settings.serialCatKeyingPortHint())
                    .append("，控制线：")
                    .append(renderAssertedKeyingLines(configuration.settings))
                    .append(" / ")
                    .append(configuration.settings.serialCatKeyingPolarity())
                    .append("。");
        }
        builder.append(" ").append(sessionFactory.describeAvailability(configuration.settings.serialCatPortHint()));
        if (configuration.settings.serialCatProtocolFamily() == CatProtocolFamily.YAESU_STYLE
                && configuration.settings.serialCatKeyingPortHint() != null) {
            builder.append(" 键控口状态：")
                    .append(keyerPortFactory.describeAvailability(configuration.settings.serialCatKeyingPortHint()));
        }
        builder.append(" ").append(familyStatus(configuration.settings.serialCatProtocolFamily()));
        if (lastAvailabilityNote != null && !lastAvailabilityNote.isEmpty()) {
            builder.append(" 上次结果：").append(lastAvailabilityNote);
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
        return sessionFactory.isReady(configuration.settings.serialCatPortHint());
    }

    @Override
    public boolean keyDown() {
        ActiveConfiguration configuration = configurationProvider.activeConfiguration();
        if (shouldUseDedicatedKeyingPort(configuration)) {
            boolean result = keyingLineUp(configuration);
            if (result) {
                lastAvailabilityNote = "已通过独立串口键控拉起 "
                        + renderAssertedKeyingLines(configuration.settings)
                        + "，极性 "
                        + configuration.settings.serialCatKeyingPolarity()
                        + "。";
            }
            return result;
        }
        return withSession("已通过原生串口 CAT 拉起发射。", this::executeKeyDown);
    }

    @Override
    public boolean keyUp() {
        ActiveConfiguration configuration = configurationProvider.activeConfiguration();
        if (shouldUseDedicatedKeyingPort(configuration)) {
            boolean result = keyingLineDown(configuration);
            if (result) {
                lastAvailabilityNote = "已释放独立串口键控 "
                        + renderAssertedKeyingLines(configuration.settings)
                        + "。";
            }
            return result;
        }
        return withSession("已通过原生串口 CAT 释放发射。", this::executeKeyUp);
    }

    @Override
    public boolean sendText(String text) {
        ActiveConfiguration configuration = configurationProvider.activeConfiguration();
        if (configuration == null) {
            lastAvailabilityNote = "请先在电台配置中固定串口 CAT 路径，并完成链路验证。";
            return false;
        }
        if (!supportsTextToCw()) {
            lastAvailabilityNote = "当前原生串口 CAT 文本发射仅优先接入 Yaesu 风格路径。";
            return false;
        }
        if (!isReady()) {
            lastAvailabilityNote = describeAvailability();
            return false;
        }
        CwTxRunner activeRunner = txRunner;
        if (activeRunner != null && activeRunner.isRunning()) {
            lastAvailabilityNote = "串口 CAT 文本发射正在进行中。";
            return false;
        }
        CwTxPlan plan = txEngine.buildPlan(text, wpm, toneFrequencyHz);
        if (plan.elements().isEmpty()) {
            lastAvailabilityNote = "文本里没有当前 TX 引擎支持的摩尔斯符号。";
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
        PortAvailability transportAvailability = sessionFactory.availability(configuration.settings.serialCatPortHint());
        if (!transportAvailability.isReady()) {
            lastAvailabilityNote = transportAvailability.message();
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
            lastAvailabilityNote = "原生串口 CAT 文本发射失败：" + safeMessage(exception);
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
    public boolean supportsPauseResumeTextTransmission() {
        return true;
    }

    @Override
    public boolean pauseTextTransmission() {
        CwTxRunner runner = txRunner;
        return runner != null && runner.requestPauseFromCurrentCharacter();
    }

    @Override
    public boolean resumeTextTransmission() {
        CwTxRunner runner = txRunner;
        return runner != null && runner.resume();
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

    private String familyStatus(CatProtocolFamily family) {
        if (family == CatProtocolFamily.YAESU_STYLE) {
            return "Yaesu 原生串口路径已具备探测能力。CW 发射优先建议使用独立键控口（RTS/DTR），而不是 CAT TX1/TX0。";
        }
        if (family == CatProtocolFamily.ICOM_CIV) {
            return "Icom 原生 CI-V 路径已具备探测能力，当前已先接入最小化的 CI-V PTT 脉冲路径。";
        }
        if (family == CatProtocolFamily.KENWOOD_STYLE) {
            return "Kenwood 原生串口路径已具备探测能力；下一层是补齐家族专属的 TX/PTT 指令。";
        }
        return "当前这个 CAT 家族还没有接入原生串口控制适配器。";
    }

    private boolean withSession(String successNote, SessionAction action) {
        ActiveConfiguration configuration = configurationProvider.activeConfiguration();
        if (configuration == null) {
            lastAvailabilityNote = "请先在电台配置中固定串口 CAT 路径，并完成链路验证。";
            return false;
        }
        if (!supportsNativePtt(configuration)) {
            lastAvailabilityNote = "当前还没有为 "
                    + configuration.settings.serialCatProtocolFamily().displayName()
                    + " 接入原生串口 CAT 控制。";
            return false;
        }
        PortAvailability transportAvailability = sessionFactory.availability(configuration.settings.serialCatPortHint());
        if (!transportAvailability.isReady()) {
            lastAvailabilityNote = transportAvailability.message();
            return false;
        }
        try (SerialCatSession session = sessionFactory.openSession(
                configuration.settings.serialCatPortHint(),
                configuration.settings.serialCatBaudRate())) {
            boolean result = action.run(configuration, session);
            lastAvailabilityNote = result ? successNote : "原生串口 CAT 指令未被接受。";
            return result;
        } catch (IOException | RuntimeException exception) {
            lastAvailabilityNote = "原生串口 CAT 指令执行失败：" + safeMessage(exception);
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
            lastAvailabilityNote = "原生串口 CAT 文本发射已完成。";
        } else if (snapshot.state() == CwTxState.STOPPED) {
            lastAvailabilityNote = "原生串口 CAT 文本发射已停止。";
        } else if (snapshot.state() == CwTxState.ERROR) {
            lastAvailabilityNote = snapshot.statusMessage();
        } else if (snapshot.state() == CwTxState.PLAYING) {
            lastAvailabilityNote = "原生串口 CAT 文本发射进行中。";
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
            lastAvailabilityNote = "独立串口键控线拉起失败。";
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
            lastAvailabilityNote = "独立串口键控线释放失败。";
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
            return "(未指定)";
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
                    throw new IllegalStateException("独立串口键控线拉起失败。");
                }
            sleepQuietly(durationMs);
        }

        @Override
        public void playSilence(int durationMs) throws InterruptedException {
                if (!applyConfiguredKeyingLevels(port, settings, !settings.serialCatKeyingPolarity().assertedLevel())) {
                    throw new IllegalStateException("独立串口键控线释放失败。");
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
                throw new InterruptedException("串口 CAT 发射被中断");
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
            return new ControlResult(false, "所选电台路径不使用串口 CAT。");
        }
        if (settings == null) {
            settings = profile.defaultSettings();
        }
        if (settings.serialCatProtocolFamily() != CatProtocolFamily.YAESU_STYLE
                && settings.serialCatProtocolFamily() != CatProtocolFamily.ICOM_CIV) {
            return new ControlResult(
                    false,
                    "当前串口 CAT PTT 脉冲验证优先支持 Yaesu 风格 CAT 和 Icom CI-V。"
            );
        }
        if (sessionFactory == null) {
            return new ControlResult(false, "串口 CAT 会话工厂当前不可用。");
        }
        if (settings.serialCatProtocolFamily() == CatProtocolFamily.ICOM_CIV
                && settings.serialCatCivAddressHex() == null) {
            return new ControlResult(false, "请先填写 CI-V 地址，再重新执行串口 CAT PTT 脉冲验证。");
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
                  return new ControlResult(false, "串口 CAT PTT 脉冲验证被中断。");
            }
            if (settings.serialCatProtocolFamily() == CatProtocolFamily.YAESU_STYLE) {
                session.send("TX0;", COMMAND_TIMEOUT_MS);
            } else {
                session.send(buildIcomPttCommand(settings, false), COMMAND_TIMEOUT_MS);
            }
            return new ControlResult(
                      true,
                      settings.serialCatProtocolFamily() == CatProtocolFamily.YAESU_STYLE
                              ? "原生串口 CAT PTT 脉冲已完成。Yaesu 风格发射已短暂拉起后释放。"
                              : "原生串口 CAT PTT 脉冲已完成。Icom CI-V PTT 已短暂拉起后释放。"
              );
        } catch (IOException | RuntimeException exception) {
            return new ControlResult(false, "原生串口 CAT PTT 脉冲验证失败：" + safeMessage(exception));
        }
    }

    public static ControlResult testDedicatedKeyingPulse(
            RigProfile profile,
            RigProfileSettings settings,
            DedicatedKeyingPortFactory keyingPortFactory,
            int holdMs
    ) {
        if (profile == null || !profile.hasCapability(RigCapability.SERIAL_CAT)) {
            return new ControlResult(false, "所选电台路径不使用串口 CAT。");
        }
        if (settings == null) {
            settings = profile.defaultSettings();
        }
        if (settings.serialCatProtocolFamily() != CatProtocolFamily.YAESU_STYLE) {
            return new ControlResult(false, "当前独立键控脉冲验证优先支持 Yaesu 风格串口 CAT。");
        }
        if (settings.serialCatKeyingPortHint() == null || settings.serialCatKeyingPortHint().trim().isEmpty()) {
            return new ControlResult(false, "请先选择独立键控口，再重新执行键控脉冲验证。");
        }
        if (keyingPortFactory == null) {
            return new ControlResult(false, "独立键控口工厂当前不可用。");
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
                  return new ControlResult(false, "独立键控线拉起失败。");
              }
            try {
                Thread.sleep(Math.max(50, Math.min(1500, holdMs)));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                boolean releasedLevel = !settings.serialCatKeyingPolarity().assertedLevel();
                applyConfiguredKeyingLevels(port, settings, releasedLevel);
                  return new ControlResult(false, "独立键控脉冲验证被中断。");
            }
            boolean releasedLevel = !settings.serialCatKeyingPolarity().assertedLevel();
            boolean released = applyConfiguredKeyingLevels(port, settings, releasedLevel);
              if (!released) {
                  return new ControlResult(false, "独立键控线已经拉起，但释放失败。");
              }
              return new ControlResult(
                      true,
                      "独立键控脉冲已完成："
                              + renderAssertedKeyingLines(settings)
                              + " / "
                              + settings.serialCatKeyingPolarity()
                              + "，端口 "
                              + settings.serialCatKeyingPortHint()
                              + "。"
              );
        } catch (RuntimeException exception) {
            return new ControlResult(false, "独立键控脉冲验证失败：" + safeMessage(exception));
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
            return new ControlResult(false, "所选电台路径不使用串口 CAT。");
        }
        if (settings == null) {
            settings = profile.defaultSettings();
        }
        if (settings.serialCatProtocolFamily() != CatProtocolFamily.YAESU_STYLE) {
            return new ControlResult(false, "当前独立键控保持验证优先支持 Yaesu 风格串口 CAT。");
        }
        if (settings.serialCatKeyingPortHint() == null || settings.serialCatKeyingPortHint().trim().isEmpty()) {
            return new ControlResult(false, "请先选择独立键控口，再重新执行键控保持验证。");
        }
        if (keyingPortFactory == null) {
            return new ControlResult(false, "独立键控口工厂当前不可用。");
        }
        SerialKeyerPort port = keyingPortFactory.openPort(settings.serialCatKeyingPortHint());
        if (port == null || !port.isOpen()) {
            return new ControlResult(false, keyingPortFactory.describeAvailability(settings.serialCatKeyingPortHint()));
        }
        try {
            boolean assertedLevel = settings.serialCatKeyingPolarity().assertedLevel();
              boolean asserted = applyConfiguredKeyingLevels(port, settings, assertedLevel);
              if (!asserted) {
                  return new ControlResult(false, "独立键控保持验证中，键控线拉起失败。");
              }
            try {
                Thread.sleep(Math.max(200, Math.min(2500, holdMs)));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                  return new ControlResult(false, "独立键控保持验证被中断。");
            } finally {
                boolean releasedLevel = !settings.serialCatKeyingPolarity().assertedLevel();
                applyConfiguredKeyingLevels(port, settings, releasedLevel);
            }
              return new ControlResult(
                      true,
                      "独立键控保持验证已完成：端口 "
                              + settings.serialCatKeyingPortHint()
                              + " / "
                              + renderAssertedKeyingLines(settings)
                              + " / "
                              + settings.serialCatKeyingPolarity()
                              + "。请重点观察保持阶段内 TX 是否持续有效，而不只是起止边沿。"
              );
        } catch (RuntimeException exception) {
            return new ControlResult(false, "独立键控保持验证失败：" + safeMessage(exception));
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
            return new ControlResult(false, "所选电台路径不使用串口 CAT。");
        }
        if (settings == null) {
            settings = profile.defaultSettings();
        }
        if (settings.serialCatProtocolFamily() != CatProtocolFamily.YAESU_STYLE) {
            return new ControlResult(false, "当前键控口开关验证优先支持 Yaesu 风格串口 CAT。");
        }
        if (settings.serialCatKeyingPortHint() == null || settings.serialCatKeyingPortHint().trim().isEmpty()) {
            return new ControlResult(false, "请先选择独立键控口，再重新执行开关验证。");
        }
        if (keyingPortFactory == null) {
            return new ControlResult(false, "独立键控口工厂当前不可用。");
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
                  return new ControlResult(false, "键控口开关验证被中断。");
            }
              return new ControlResult(
                      true,
                      "键控口开关验证已完成：端口 "
                              + settings.serialCatKeyingPortHint()
                              + "。本次没有主动切换 DTR/RTS，请观察打开或关闭端口时电台是否仍然闪 TX。"
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
            return new ControlResult(false, "所选电台路径不使用串口 CAT。");
        }
        if (settings == null) {
            settings = profile.defaultSettings();
        }
        if (plan == null) {
            return new ControlResult(false, "时序实验参数缺失。");
        }
        if (settings.serialCatProtocolFamily() != CatProtocolFamily.YAESU_STYLE) {
            return new ControlResult(false, "当前独立键控时序实验优先支持 Yaesu 风格串口 CAT。");
        }
        if (settings.serialCatKeyingPortHint() == null || settings.serialCatKeyingPortHint().trim().isEmpty()) {
            return new ControlResult(false, "请先选择独立键控口，再重新执行时序实验。");
        }
        if (keyingPortFactory == null) {
            return new ControlResult(false, "独立键控口工厂当前不可用。");
        }
        SerialKeyerPort port = keyingPortFactory.openPort(settings.serialCatKeyingPortHint());
        if (port == null || !port.isOpen()) {
            return new ControlResult(false, keyingPortFactory.describeAvailability(settings.serialCatKeyingPortHint()));
        }
        try {
            return runDedicatedKeyingTimingLab(port, settings, plan);
        } catch (RuntimeException exception) {
            return new ControlResult(false, "独立键控时序实验失败：" + safeMessage(exception));
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
            return new ControlResult(false, "所选电台路径不使用串口 CAT。");
        }
        if (settings == null) {
            settings = profile.defaultSettings();
        }
        if (plan == null) {
            return new ControlResult(false, "短脉冲实验参数缺失。");
        }
        if (settings.serialCatProtocolFamily() != CatProtocolFamily.YAESU_STYLE) {
            return new ControlResult(false, "当前独立键控短脉冲实验优先支持 Yaesu 风格串口 CAT。");
        }
        if (settings.serialCatKeyingPortHint() == null || settings.serialCatKeyingPortHint().trim().isEmpty()) {
            return new ControlResult(false, "请先选择独立键控口，再重新执行短脉冲实验。");
        }
        if (keyingPortFactory == null) {
            return new ControlResult(false, "独立键控口工厂当前不可用。");
        }
        SerialKeyerPort port = keyingPortFactory.openPort(settings.serialCatKeyingPortHint());
        if (port == null || !port.isOpen()) {
            return new ControlResult(false, keyingPortFactory.describeAvailability(settings.serialCatKeyingPortHint()));
        }
        try {
            return runDedicatedShortPulseLab(port, settings, plan);
        } catch (RuntimeException exception) {
            return new ControlResult(false, "独立键控短脉冲实验失败：" + safeMessage(exception));
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
            return new ControlResult(false, "时序实验无法先把键控线归一到释放状态。");
        }
        if (!sleepChecked(plan.preDelayMs())) {
            return new ControlResult(false, "独立键控时序实验在预延时阶段被中断。");
        }
        if (!setLinesToLevel(port, selection, assertedLevel, plan.assertOrder(), plan.interLineGapMs())) {
            setLinesToLevel(port, selection, releasedLevel, TimingLabOrder.SIMULTANEOUS, 0);
            return new ControlResult(false, "时序实验无法按要求拉起键控模式。");
        }
        if (!sleepChecked(plan.holdMs())) {
            setLinesToLevel(
                    port,
                    selection,
                    releasedLevel,
                    plan.releaseOrder().toTimingOrder(),
                    plan.releaseGapMs()
            );
            return new ControlResult(false, "独立键控时序实验在保持阶段被中断。");
        }
        if (!setLinesToLevel(
                port,
                selection,
                releasedLevel,
                plan.releaseOrder().toTimingOrder(),
                plan.releaseGapMs()
        )) {
            return new ControlResult(false, "时序实验已拉起键控线，但释放失败。");
        }
        return new ControlResult(
                true,
                "时序实验已完成：端口 "
                        + settings.serialCatKeyingPortHint()
                        + "，拉起线 "
                        + selection.render()
                        + "，拉起顺序 "
                        + plan.assertOrder().displayName()
                        + "，预延时 "
                        + plan.preDelayMs()
                        + "ms，保持 "
                        + plan.holdMs()
                        + "ms，线间隔 "
                        + plan.interLineGapMs()
                        + "ms，释放顺序 "
                        + plan.releaseOrder().displayName()
                        + "，释放间隔 "
                        + plan.releaseGapMs()
                        + "ms，极性 "
                        + settings.serialCatKeyingPolarity()
                        + "。"
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
            return new ControlResult(false, "短脉冲实验无法先把键控线归一到释放状态。");
        }
        if (!sleepChecked(plan.preDelayMs())) {
            return new ControlResult(false, "独立键控短脉冲实验在预延时阶段被中断。");
        }
        CwTxPlan txPlan = new CwTxEngine().buildPlan(plan.preset().text(), plan.wpm(), settings.defaultToneFrequencyHz());
        List<CwTxElement> elements = txPlan.elements();
        if (elements.isEmpty()) {
            return new ControlResult(false, "短脉冲实验没有为所选预置生成任何 CW 元素。");
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
                    return new ControlResult(false, "短脉冲实验无法为 " + plan.preset().displayName() + " 拉起键控模式。");
                }
                if (!sleepChecked(element.durationMs() + plan.tailHoldMs())) {
                    setLinesToLevel(
                            port,
                            selection,
                            releasedLevel,
                            plan.releaseOrder().toTimingOrder(),
                            plan.releaseLineGapMs()
                    );
                    return new ControlResult(false, "独立键控短脉冲实验在 key-down 段被中断。");
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
                return new ControlResult(false, "短脉冲实验在 " + plan.preset().displayName() + " 期间释放键控线失败。");
            }
            if (!sleepChecked(element.durationMs() + plan.extraReleaseGapMs())) {
                return new ControlResult(false, "独立键控短脉冲实验在 key-up 段被中断。");
            }
        }
        if (!setLinesToLevel(
                port,
                selection,
                releasedLevel,
                plan.releaseOrder().toTimingOrder(),
                plan.releaseLineGapMs()
        )) {
            return new ControlResult(false, "短脉冲实验已完成整段模式，但最终释放失败。");
        }
        return new ControlResult(
                true,
                "短脉冲实验已完成："
                        + plan.preset().displayName()
                        + " ("
                        + txPlan.morsePreview()
                        + ")，"
                        + plan.wpm()
                        + " WPM，端口 "
                        + settings.serialCatKeyingPortHint()
                        + "。预延时 "
                        + plan.preDelayMs()
                        + "ms，尾部保持 "
                        + plan.tailHoldMs()
                        + "ms，额外释放间隔 "
                        + plan.extraReleaseGapMs()
                        + "ms，拉起顺序 "
                        + plan.assertOrder().displayName()
                        + "，释放顺序 "
                        + plan.releaseOrder().displayName()
                        + "，释放线间隔 "
                        + plan.releaseLineGapMs()
                        + "ms。"
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
            return throwable == null ? "未知错误" : throwable.getClass().getSimpleName();
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
            throw new IllegalArgumentException("构造 Icom CI-V PTT 指令前，必须先设置 CI-V 地址。");
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
        SIMULTANEOUS("同步"),
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
        TOGETHER("同时释放"),
        RELEASE_RTS_FIRST("先放 RTS"),
        RELEASE_DTR_FIRST("先放 DTR");

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
        SINGLE_E("单个 E", "E"),
        SINGLE_T("单个 T", "T"),
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
