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
        return "Hamlib rigctld 适配器";
    }

    @Override
    public String describeCapabilities() {
        return "通过 Hamlib rigctld 网络会话发送 CW，支持配合 send_morse 更新 KEYSPD 和 CWPITCH。";
    }

    @Override
    public String describeAvailability() {
        ActiveConfiguration configuration = configurationProvider.activeConfiguration();
        if (configuration == null) {
            return "请先在电台配置中固定一条网络 CAT 路径，并选择 Hamlib rigctld 协议族。";
        }
        if (configuration.host == null || configuration.host.isEmpty()) {
            return "当前已选择 Hamlib rigctld，但还没有填写网络主机地址。";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("已配置 Hamlib rigctld：")
                .append(configuration.host)
                .append(":")
                .append(configuration.port)
                .append("，电台路径：")
                .append(configuration.profile.displayName())
                .append("。");
        if (lastAvailabilityNote != null && !lastAvailabilityNote.isEmpty()) {
            builder.append(" 上次结果：").append(lastAvailabilityNote);
        } else {
            builder.append(" 连通性会在开始发射时检查。");
        }
        if (RigProfileFamilies.isYaesuFamily(configuration.profile)) {
            builder.append(" Yaesu 提示：在进行长报文前，请先确认 rigctld 在 CWCN 之外已经能控制电台。");
        } else if (RigProfileFamilies.isIcomFamily(configuration.profile)) {
            builder.append(" Icom 提示：在进行长报文前，请先确认 rigctld 桥接已经能和目标 CI-V 电台通信。");
        } else if (RigProfileFamilies.isKenwoodFamily(configuration.profile)) {
            builder.append(" Kenwood 提示：在进行长报文前，请先确认 rigctld 桥接已经能和目标电台通信。");
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
        return withSession(session -> session.setPtt(true), "已通过 rigctld 拉起 PTT。");
    }

    @Override
    public boolean keyUp() {
        return withSession(session -> session.setPtt(false), "已通过 rigctld 释放 PTT。");
    }

    @Override
    public boolean sendText(String text) {
        ActiveConfiguration configuration = configurationProvider.activeConfiguration();
        if (configuration == null) {
            lastAvailabilityNote = "当前电台配置没有指向 Hamlib rigctld 路径。";
            return false;
        }
        CwTxPlan plan = txEngine.buildPlan(text, wpm, toneFrequencyHz);
        if (plan.morsePreview().isEmpty()) {
            lastAvailabilityNote = "文本里没有当前 TX 引擎支持的摩尔斯符号。";
            return false;
        }
        try (HamlibRigctldSession session = sessionFactory.open(configuration.host, configuration.port)) {
            session.setKeySpeedWpm(wpm);
            session.setCwPitchHz(toneFrequencyHz);
            boolean sent = session.sendMorse(plan.morsePreview());
            lastAvailabilityNote = sent
                    ? "最近一次 send_morse 请求已被接受。"
                    : "最近一次 send_morse 请求被 rigctld 拒绝。";
            return sent;
        } catch (IOException exception) {
            lastAvailabilityNote = "连接失败：" + exception.getMessage();
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
            return new ProbeResult(false, "所选电台路径不使用网络 CAT。");
        }
        RigProfileSettings safeSettings = settings == null ? profile.defaultSettings() : settings;
        if (safeSettings.networkCatProtocolFamily() != CatProtocolFamily.HAMLIB_RIGCTLD) {
            return new ProbeResult(false, "当前连接探测仅支持 Hamlib rigctld 协议族。");
        }
        String host = safeSettings.networkHost();
        if (host == null || host.trim().isEmpty()) {
            return new ProbeResult(false, "请先填写 rigctld 主机地址。");
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
                        "已连接到 rigctld："
                                + host
                                + ":"
                                + port
                                + "，但服务端没有返回电台信息。"
                                + familyProbeTail(yaesuFamily, icomFamily, kenwoodFamily, false)
                );
            }
            String firstLine = info.split("\\R", 2)[0].trim();
            return new ProbeResult(
                    true,
                    "已连接到 rigctld："
                            + host
                            + ":"
                            + port
                            + "。电台信息："
                            + firstLine
                            + familyProbeTail(yaesuFamily, icomFamily, kenwoodFamily, true)
            );
        } catch (IOException exception) {
            return new ProbeResult(
                    false,
                    "rigctld 探测失败："
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
                    ? " Yaesu 提示：建议先用一个短 DIT 或 VVV 做台架验证，再发长报文。"
                    : " 对 Yaesu FT 家族，仍建议先用短报文确认基础 CAT/PTT 行为。";
        }
        if (icomFamily) {
            return withRigInfo
                    ? " Icom 提示：建议先用一个短 DIT 或 VVV 做台架验证，再发长报文。"
                    : " 对 Icom 家族，仍建议先用短报文确认基础 CAT/PTT 行为。";
        }
        if (kenwoodFamily) {
            return withRigInfo
                    ? " Kenwood 提示：建议先用一个短 DIT 或 VVV 做台架验证，再发长报文。"
                    : " 对 Kenwood 家族，仍建议先用短报文确认基础 CAT/PTT 行为。";
        }
        return "";
    }

    private static String familyFailureTail(
            boolean yaesuFamily,
            boolean icomFamily,
            boolean kenwoodFamily
    ) {
        if (yaesuFamily) {
            return " Yaesu 提示：请先确认守护进程已经绑定到 FT 家族电台，并且在 CWCN 之外可以正常响应。";
        }
        if (icomFamily) {
            return " Icom 提示：请先确认守护进程已经绑定到目标 CI-V 电台，并且在 CWCN 之外可以正常响应。";
        }
        if (kenwoodFamily) {
            return " Kenwood 提示：请先确认守护进程已经绑定到目标电台，并且在 CWCN 之外可以正常响应。";
        }
        return "";
    }

    private boolean withSession(SessionAction action, String successNote) {
        ActiveConfiguration configuration = configurationProvider.activeConfiguration();
        if (configuration == null) {
            lastAvailabilityNote = "当前电台配置没有指向 Hamlib rigctld 路径。";
            return false;
        }
        try (HamlibRigctldSession session = sessionFactory.open(configuration.host, configuration.port)) {
            boolean result = action.run(session);
            lastAvailabilityNote = result ? successNote : "rigctld 拒绝了最近一次控制请求。";
            return result;
        } catch (IOException exception) {
            lastAvailabilityNote = "连接失败：" + exception.getMessage();
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
