package org.bi9clt.cwcn.core.rig;

import java.io.IOException;

public final class SerialCatProbe {
    private static final int DEFAULT_TIMEOUT_MS = 1500;
    private static final int CIV_CONTROLLER_ADDRESS = 0xE0;

    private SerialCatProbe() {
    }

    public static ProbeResult probeConfiguration(
            RigProfile profile,
            RigProfileSettings settings,
            SerialCatSessionFactory sessionFactory
    ) {
        if (profile == null || !profile.hasCapability(RigCapability.SERIAL_CAT)) {
            return new ProbeResult(false, "所选电台路径不使用串口 CAT。");
        }
        if (sessionFactory == null) {
            return new ProbeResult(false, "串口 CAT 会话工厂当前不可用。");
        }
        RigProfileSettings safeSettings = settings == null ? profile.defaultSettings() : settings;
        CatProtocolFamily family = safeSettings.serialCatProtocolFamily();
        if (family == CatProtocolFamily.YAESU_STYLE) {
            return probeYaesu(safeSettings, sessionFactory);
        }
        if (family == CatProtocolFamily.ICOM_CIV) {
            return probeIcomCiv(safeSettings, sessionFactory);
        }
        if (family == CatProtocolFamily.KENWOOD_STYLE) {
            return probeKenwood(safeSettings, sessionFactory);
        }
        return new ProbeResult(
                false,
                "当前串口 CAT 探测优先支持 Yaesu 风格 CAT、Icom CI-V 和 Kenwood 风格 CAT。"
        );
    }

    private static ProbeResult probeYaesu(
            RigProfileSettings settings,
            SerialCatSessionFactory sessionFactory
    ) {
        String portHint = settings.serialCatPortHint();
        try (SerialCatSession session = sessionFactory.openSession(portHint, settings.serialCatBaudRate())) {
            String response = firstNonEmpty(
                    session.transact("FA;", DEFAULT_TIMEOUT_MS),
                    session.transact("IF;", DEFAULT_TIMEOUT_MS)
            );
            if (response == null || response.isEmpty()) {
                return new ProbeResult(
                        false,
                        "串口 CAT 链路已打开，但电台对 FA;/IF; 没有返回可读响应。"
                                + " Yaesu / FT-891 请重点检查：CAT 波特率、菜单里的 CAT/RTS 相关设置，以及是否把 CAT 选到了 Enhanced 端口（常见是 #1），而把 TX 控制留给 Standard 端口（常见是 #0）。"
                );
            }
            return new ProbeResult(
                    true,
                    "串口 CAT 已响应。首条返回：" + response + "。建议先完成配置验证；原生 Yaesu CAT 发射链路仍在继续完善。"
            );
        } catch (IOException | RuntimeException exception) {
            return new ProbeResult(false, "串口 CAT 探测失败：" + safeMessage(exception));
        }
    }

    private static ProbeResult probeIcomCiv(
            RigProfileSettings settings,
            SerialCatSessionFactory sessionFactory
    ) {
        if (settings.serialCatCivAddressHex() == null) {
            return new ProbeResult(false, "请先填写 CI-V 地址，再重新探测串口 CAT。");
        }
        int radioAddress = Integer.parseInt(settings.serialCatCivAddressHex(), 16);
        byte[] command = new byte[] {
                (byte) 0xFE,
                (byte) 0xFE,
                (byte) radioAddress,
                (byte) CIV_CONTROLLER_ADDRESS,
                0x19,
                0x00,
                (byte) 0xFD
        };
        String portHint = settings.serialCatPortHint();
        try (SerialCatSession session = sessionFactory.openSession(portHint, settings.serialCatBaudRate())) {
            byte[] response = session.transact(command, DEFAULT_TIMEOUT_MS);
            if (response == null || response.length == 0) {
                return new ProbeResult(
                        false,
                        "CI-V 链路已打开，但电台对机型查询没有返回可读响应。请检查 CI-V 地址、波特率和电台端 CI-V 设置。"
                );
            }
            return new ProbeResult(
                    true,
                    "CI-V 已响应。首条返回：" + toHex(response) + "。建议先完成读写验证；原生 Icom 发射链路仍在继续完善。"
            );
        } catch (IOException | RuntimeException exception) {
            return new ProbeResult(false, "串口 CAT 探测失败：" + safeMessage(exception));
        }
    }

    private static ProbeResult probeKenwood(
            RigProfileSettings settings,
            SerialCatSessionFactory sessionFactory
    ) {
        String portHint = settings.serialCatPortHint();
        try (SerialCatSession session = sessionFactory.openSession(portHint, settings.serialCatBaudRate())) {
            String response = firstNonEmpty(
                    session.transact("ID;", DEFAULT_TIMEOUT_MS),
                    session.transact("FA;", DEFAULT_TIMEOUT_MS),
                    session.transact("IF;", DEFAULT_TIMEOUT_MS)
            );
            if (response == null || response.isEmpty()) {
                return new ProbeResult(
                        false,
                        "Kenwood 风格 CAT 链路已打开，但电台对 ID;/FA;/IF; 没有返回可读响应。请检查波特率、串口帧格式和电台端 CAT 设置。"
                );
            }
            return new ProbeResult(
                    true,
                    "Kenwood 风格 CAT 已响应。首条返回：" + response + "。建议先完成读写验证；原生 Kenwood 发射链路仍在继续完善。"
            );
        } catch (IOException | RuntimeException exception) {
            return new ProbeResult(false, "串口 CAT 探测失败：" + safeMessage(exception));
        }
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().trim().isEmpty()) {
            return throwable == null ? "未知故障" : throwable.getClass().getSimpleName();
        }
        return throwable.getMessage().trim();
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String toHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < bytes.length; index++) {
            if (index > 0) {
                builder.append(' ');
            }
            builder.append(String.format(java.util.Locale.US, "%02X", bytes[index] & 0xFF));
        }
        return builder.toString();
    }

    public static final class ProbeResult {
        private final boolean success;
        private final String message;

        public ProbeResult(boolean success, String message) {
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
}
