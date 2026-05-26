package org.bi9clt.cwcn.core.rig;

import androidx.annotation.Nullable;

public final class RigRouteStatusFormatter {
    private RigRouteStatusFormatter() {
    }

    public static String describeOperateRouteSummary(
            @Nullable RigProfile profile,
            boolean usePhoneFallback,
            boolean hasMicrophonePermission
    ) {
        return "RX " + describeRxRouteLabel(profile, usePhoneFallback, hasMicrophonePermission)
                + " | TX " + describeTxRouteLabel(profile, usePhoneFallback, null)
                + " | CAT " + describeCatRouteLabel(profile, null);
    }

    public static String describeRxRouteLabel(
            @Nullable RigProfile profile,
            boolean usePhoneFallback,
            boolean hasMicrophonePermission
    ) {
        if (usePhoneFallback) {
            return hasMicrophonePermission ? "手机麦克风" : "手机麦克风待授权";
        }
        if (profile == null) {
            return "未配置";
        }
        return "电台音频未接入";
    }

    public static String describeTxRouteLabel(
            @Nullable RigProfile profile,
            boolean usePhoneFallback
    ) {
        return describeTxRouteLabel(profile, usePhoneFallback, null);
    }

    public static String describeTxRouteLabel(
            @Nullable RigProfile profile,
            boolean usePhoneFallback,
            @Nullable RigProfileSettings settings
    ) {
        if (usePhoneFallback) {
            return "手机音频";
        }
        if (profile == null) {
            return "未配置";
        }
        RigProfileSettings safeSettings = settings == null ? profile.defaultSettings() : settings;
        if (profile.hasCapability(RigCapability.SERIAL_CAT)
                && hasMeaningfulText(safeSettings.serialCatKeyingPortHint())) {
            return "独立线控";
        }
        if (profile.hasCapability(RigCapability.KEY_LINE_CONTROL)) {
            return "USB 线控";
        }
        return fallback(profile.displayName(), "已选电台路由");
    }

    public static String describeCatRouteLabel(
            @Nullable RigProfile profile,
            @Nullable RigProfileSettings settings
    ) {
        if (profile == null) {
            return "-";
        }
        RigProfileSettings safeSettings = settings == null ? profile.defaultSettings() : settings;
        if (profile.hasCapability(RigCapability.SERIAL_CAT)) {
            if (RigProfileFamilies.isXieguFamily(profile)) {
                return "Xiegu CAT";
            }
            if (safeSettings.serialCatProtocolFamily() == CatProtocolFamily.YAESU_STYLE) {
                return "USB CAT";
            }
            if (safeSettings.serialCatProtocolFamily() == CatProtocolFamily.ICOM_CIV) {
                return "CI-V";
            }
            if (safeSettings.serialCatProtocolFamily() == CatProtocolFamily.KENWOOD_STYLE) {
                return "USB CAT";
            }
            return "串口 CAT";
        }
        if (profile.hasCapability(RigCapability.NETWORK_CAT)) {
            return "网络 CAT";
        }
        return "-";
    }

    public static String describeOperateRxHint(
            @Nullable RigProfile profile,
            boolean usePhoneFallback,
            boolean hasMicrophonePermission
    ) {
        if (usePhoneFallback) {
            return hasMicrophonePermission
                    ? "当前按默认策略使用手机麦克风接收。"
                    : "需要先授权麦克风，才能开始手机接收。";
        }
        if (profile == null) {
            return "当前没有选中的电台路由，也没有启用手机兜底。";
        }
        if (profile.hasCapability(RigCapability.SERIAL_CAT)) {
            if (RigProfileFamilies.isXieguFamily(profile)) {
                return "当前是 Xiegu 串口 CAT 路由：CAT 走 USB 串口；RX 优先建议接入 USB 外部音频，必要时可切到手机麦克风混合模式。";
            }
            return "当前是混合电台链路：CAT 走 USB 串口，发射可走独立线控；正式 RX 仍需手机麦克风或外部 USB 音频。";
        }
        return "已选电台发射路由，但正式 RX 仍未接入电台音频。";
    }

    public static String describeOperateStatusMain(
            @Nullable RigProfile profile,
            boolean usePhoneFallback,
            boolean hasMicrophonePermission,
            @Nullable RigControlAdapter adapter
    ) {
        if (usePhoneFallback) {
            return hasMicrophonePermission ? "手机接收 | 待命" : "手机接收 | 等待授权";
        }
        if (profile == null) {
            return "接收未就绪 | 未配置";
        }
        if (adapter == null) {
            return "路由未挂接 | 待配置";
        }
        if (adapter instanceof UsbSerialKeyerRigControlAdapter) {
            return adapter.isReady() ? "USB 键控 | RX 未接入" : "USB 键控 | 待就绪";
        }
        if (adapter instanceof HamlibRigctldRigControlAdapter) {
            return adapter.isReady() ? "网络 CAT | RX 未接入" : "网络 CAT | 待配置";
        }
        if (adapter instanceof SerialCatRigControlAdapter) {
            if (RigProfileFamilies.isXieguFamily(profile)) {
                return adapter.isReady() ? "Xiegu CAT 已就绪 | RX 待接入" : "Xiegu CAT 待配置";
            }
            return adapter.isReady() ? "CAT 已就绪 | RX 未接入" : "CAT 待配置";
        }
        if (adapter instanceof AudioVoxRigControlAdapter) {
            return "音频 VOX | 待命";
        }
        return adapter.isReady() ? "当前路由 | RX 未接入" : "当前路由 | 待就绪";
    }

    public static String describeSettingsRouteSummary(
            @Nullable RigProfile profile,
            @Nullable RigProfileSettings settings,
            boolean usePhoneFallback,
            @Nullable String readiness
    ) {
        if (profile == null) {
            return "固定路由: 未配置"
                    + "\n兜底: " + describeFallbackSummary(usePhoneFallback);
        }
        RigProfileSettings safeSettings = settings == null ? profile.defaultSettings() : settings;
        StringBuilder builder = new StringBuilder();
        builder.append("固定路由: ").append(fallback(profile.displayName(), "未命名路由"))
                .append("\n传输: ").append(transportLabel(profile))
                .append("  |  兜底: ").append(describeFallbackSummary(usePhoneFallback))
                .append("\n发射: ").append(safeSettings.defaultToneFrequencyHz())
                .append(" Hz / ")
                .append(safeSettings.defaultWpm())
                .append(" WPM");
        if (hasMeaningfulText(readiness)) {
            builder.append("\n状态: ").append(readiness.trim());
        }
        return builder.toString();
    }

    public static String describeSettingsCatKeyingSummary(
            @Nullable RigProfile profile,
            @Nullable RigProfileSettings settings
    ) {
        if (profile == null) {
            return "CAT: -\n键控: -";
        }
        RigProfileSettings safeSettings = settings == null ? profile.defaultSettings() : settings;
        if (profile.hasCapability(RigCapability.SERIAL_CAT)) {
            StringBuilder builder = new StringBuilder();
            builder.append("串口 CAT: ")
                    .append(RigProfileFamilies.isXieguFamily(profile)
                            ? "Xiegu 串口 CAT"
                            : safeSettings.serialCatProtocolFamily().displayName())
                    .append(" / ")
                    .append(safeSettings.serialCatBaudRate())
                    .append(" 波特");
            builder.append("\n键控: ")
                    .append(safeSettings.serialCatKeyLine().name())
                    .append(" / ")
                    .append(safeSettings.serialCatKeyingPolarity());
            if (hasMeaningfulText(safeSettings.serialCatPortHint())) {
                builder.append("\nCAT 端口: ").append(safeSettings.serialCatPortHint());
            }
            if (hasMeaningfulText(safeSettings.serialCatKeyingPortHint())) {
                builder.append("\n键控端口: ").append(safeSettings.serialCatKeyingPortHint());
            }
            if (safeSettings.serialCatProtocolFamily() == CatProtocolFamily.ICOM_CIV) {
                builder.append("\n")
                        .append(RigProfileFamilies.isXieguFamily(profile) ? "设备地址" : "CI-V 地址")
                        .append(": ")
                        .append(fallback(safeSettings.serialCatCivAddressHex(), "-"));
            }
            return builder.toString();
        }
        if (profile.hasCapability(RigCapability.NETWORK_CAT)) {
            return "网络 CAT: "
                    + safeSettings.networkCatProtocolFamily().displayName()
                    + "\n主机: "
                    + fallback(safeSettings.networkHost(), "-")
                    + (hasMeaningfulText(safeSettings.networkHost()) ? ":" + safeSettings.networkPort() : "");
        }
        if (profile.hasCapability(RigCapability.KEY_LINE_CONTROL)) {
            return "USB 键控: "
                    + safeSettings.usbKeyLine().name()
                    + "\n目标设备: "
                    + fallback(safeSettings.usbPreferredDeviceName(), "自动 / 首个可用设备");
        }
        if (profile.hasCapability(RigCapability.AUDIO_VOX)) {
            return "音频 VOX 已启用。\nCAT / 线控键控: -";
        }
        return "当前路由暂未提供更细的 CAT / 键控摘要。";
    }

    @Nullable
    public static String describeRouteReadiness(
            @Nullable RigProfile profile,
            @Nullable RigControlAdapter adapter,
            @Nullable RigProfileSettings settings,
            @Nullable String stickyMessage
    ) {
        if (profile == null) {
            return null;
        }
        if (hasMeaningfulText(stickyMessage)) {
            return stickyMessage.trim();
        }
        if (adapter instanceof UsbSerialKeyerRigControlAdapter) {
            return describeUsbKeyerReadiness((UsbSerialKeyerRigControlAdapter) adapter, stickyMessage);
        }
        if (adapter instanceof SerialCatRigControlAdapter) {
            return describeSerialCatReadiness(profile, (SerialCatRigControlAdapter) adapter, settings);
        }
        if (adapter instanceof HamlibRigctldRigControlAdapter) {
            return describeHamlibReadiness((HamlibRigctldRigControlAdapter) adapter, settings);
        }
        if (adapter instanceof AudioVoxRigControlAdapter) {
            return describeAudioVoxReadiness((AudioVoxRigControlAdapter) adapter);
        }
        if (adapter == null) {
            return "当前路由还没有接上正式控制适配器。";
        }
        if (adapter.isReady()) {
            return "当前路由已就绪。";
        }
        return fallback(adapter.describeAvailability(), "当前路由尚未就绪。");
    }

    public static String describeUsbKeyerReadiness(
            @Nullable UsbSerialKeyerRigControlAdapter adapter,
            @Nullable String stickyMessage
    ) {
        if (adapter == null) {
            return "USB 键控适配器尚未挂接到当前路由。";
        }
        if (hasMeaningfulText(stickyMessage)) {
            return stickyMessage.trim();
        }
        if (adapter.isReady()) {
            return "USB 键控已就绪。";
        }
        String diagnosticCode = adapter.diagnosticStageCode();
        if ("usb-serial-no-permission".equals(diagnosticCode)) {
            return "已检测到 USB 键控设备，但 Android USB 权限尚未授予。";
        }
        if ("usb-serial-target-missing".equals(diagnosticCode) || adapter.isPreferredDeviceMissing()) {
            return "已保存的 USB 目标设备当前不在线，请重新插入或切回自动选择。";
        }
        if ("usb-serial-no-device".equals(diagnosticCode)) {
            return "当前没有检测到 USB 设备，请检查 OTG 线和线控设备。";
        }
        if ("usb-serial-no-cdc".equals(diagnosticCode)) {
            return "检测到了 USB 设备，但它不是可用于 RTS/DTR 键控的 CDC/ACM 串口设备。";
        }
        if ("usb-serial-no-control-interface".equals(diagnosticCode)) {
            return "USB 设备缺少可用于 RTS/DTR 键控的控制接口。";
        }
        if ("usb-serial-open-failed".equals(diagnosticCode)) {
            return "找到了 USB 目标设备，但打开设备失败。";
        }
        if ("usb-serial-claim-failed".equals(diagnosticCode)) {
            return "找到了 USB 目标设备，但占用控制接口失败。";
        }
        if ("usb-serial-no-manager".equals(diagnosticCode)) {
            return "当前设备上无法获取 USB 系统服务。";
        }
        if ("usb-serial-no-context".equals(diagnosticCode)) {
            return "当前 USB 路由初始化上下文异常。";
        }
        return "USB 键控尚未就绪：" + fallback(adapter.describeAvailability(), "请检查当前 USB 路由状态。");
    }

    public static String describeUsbKeyerOperateHint(
            @Nullable UsbSerialKeyerRigControlAdapter adapter,
            @Nullable String stickyMessage
    ) {
        if (adapter != null && adapter.isReady()) {
            return "USB 键控已就绪，但当前正式 RX 仍未接入电台音频。";
        }
        return describeUsbKeyerReadiness(adapter, stickyMessage);
    }

    public static String describeAudioVoxReadiness(@Nullable AudioVoxRigControlAdapter adapter) {
        if (adapter == null) {
            return "音频 VOX 适配器尚未挂接。";
        }
        return adapter.isReady()
                ? "音频 VOX 已就绪，请确认音频线、VOX 门限和电台侧 VOX 设置。"
                : fallback(adapter.describeAvailability(), "音频 VOX 尚未就绪。");
    }

    public static String describeSerialCatReadiness(
            @Nullable RigProfile profile,
            @Nullable SerialCatRigControlAdapter adapter,
            @Nullable RigProfileSettings settings
    ) {
        if (adapter == null) {
            return "串口 CAT 适配器尚未挂接。";
        }
        if (adapter.isReady()) {
            if (RigProfileFamilies.isXieguFamily(profile)) {
                if (RigProfileFamilies.isXieguPortableUsbFamily(profile)) {
                    return "Xiegu 串口 CAT 已具备基础连通条件，可继续验证读频，并按现场条件决定 RX 走 USB 音频还是手机麦克风混合模式。";
                }
                return "Xiegu 串口 CAT 已具备基础连通条件，可继续验证读频，并把 RX / TX 音频按 G90 系列的外置链路接入。";
            }
            if (settings != null
                    && settings.serialCatProtocolFamily() == CatProtocolFamily.ICOM_CIV) {
                return "串口 CAT 路由已具备基础连通条件，可继续验证读频、PTT 与外部音频 / 独立键控联调。";
            }
            return "串口 CAT 路由已具备基础连通条件，可继续做 PTT / 键控联调。";
        }
        if (settings == null) {
            return fallback(adapter.describeAvailability(), "串口 CAT 尚未配置完成。");
        }
        if (!hasMeaningfulText(settings.serialCatPortHint())
                && !hasMeaningfulText(settings.serialCatKeyingPortHint())) {
            return "请先在设置中填写串口 CAT 端口，或补充独立键控端口。";
        }
        if (settings.serialCatProtocolFamily() == CatProtocolFamily.ICOM_CIV
                && !hasMeaningfulText(settings.serialCatCivAddressHex())) {
            return RigProfileFamilies.isXieguFamily(profile)
                    ? "请先补充设备地址，再继续验证 Xiegu 串口 CAT 的读频与控制链路。"
                    : "请先补充 CI-V 地址，再继续验证串口 CAT 的读频与控制链路。";
        }
        return fallback(adapter.describeAvailability(), "串口 CAT 尚未就绪。");
    }

    public static String describeHamlibReadiness(
            @Nullable HamlibRigctldRigControlAdapter adapter,
            @Nullable RigProfileSettings settings
    ) {
        if (adapter == null) {
            return "Hamlib rigctld 适配器尚未挂接。";
        }
        if (adapter.isReady()) {
            return "Hamlib rigctld 路由已配置，可继续验证网络连通与发射控制。";
        }
        if (settings == null || !hasMeaningfulText(settings.networkHost())) {
            return "请先在设置中填写 rigctld 主机和端口。";
        }
        return fallback(adapter.describeAvailability(), "Hamlib rigctld 尚未就绪。");
    }

    public static String describeFallbackSummary(boolean usePhoneFallback) {
        return usePhoneFallback ? "手机麦克风 RX / 手机音频 TX" : "未启用，需先固定电台路由";
    }

    private static String transportLabel(@Nullable RigProfile profile) {
        if (profile == null || profile.transportKind() == null) {
            return "-";
        }
        switch (profile.transportKind()) {
            case USB_SERIAL:
                return "USB 串口";
            case BLUETOOTH_SERIAL:
                return "蓝牙串口";
            case NETWORK_CAT:
                return "网络 CAT";
            case AUDIO_VOX:
                return "音频 VOX";
            default:
                return profile.transportKind().name();
        }
    }

    private static boolean hasMeaningfulText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String fallback(String value, String fallback) {
        return hasMeaningfulText(value) ? value.trim() : fallback;
    }
}
