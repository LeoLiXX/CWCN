package org.bi9clt.cwcn.core.rig;

public final class RigProfileConfigurationFormatter {
    private RigProfileConfigurationFormatter() {
    }

    public static String renderCompactSummary(RigProfile profile, RigProfileSettings settings) {
        if (profile == null) {
            return "当前没有正式电台路径";
        }
        RigProfileSettings safeSettings = settings == null ? RigProfileSettings.defaults() : settings;
        StringBuilder builder = new StringBuilder();
        builder.append("电台路径：").append(profile.displayName())
                .append("\n支持级别：").append(profile.supportLevel().displayName())
                .append("\n传输层：").append(renderTransportKind(profile.transportKind()))
                .append("\nCW 参数：")
                .append(safeSettings.defaultWpm()).append(" WPM / ")
                .append(safeSettings.defaultToneFrequencyHz()).append(" Hz");
        appendTransportSpecificSummary(builder, profile, safeSettings);
        return builder.toString();
    }

    private static void appendTransportSpecificSummary(
            StringBuilder builder,
            RigProfile profile,
            RigProfileSettings settings
    ) {
        if (profile.hasCapability(RigCapability.KEY_LINE_CONTROL)) {
            builder.append("\nUSB 键控线：").append(settings.usbKeyLine().name());
            if (settings.usbPreferredDeviceName() != null) {
                builder.append("\n偏好 USB 设备：").append(settings.usbPreferredDeviceName());
            }
        }
        if (profile.hasCapability(RigCapability.SERIAL_CAT)) {
            builder.append("\n串口 CAT：")
                    .append(renderSerialCatFamilyLabel(profile, settings))
                    .append(" / ")
                    .append(settings.serialCatBaudRate())
                    .append(" baud");
            if (RigProfileFamilies.isXieguFamily(profile)) {
                if (RigProfileFamilies.isXieguPortableUsbFamily(profile)) {
                    builder.append("\nXiegu 提示：当前优先建议走单 USB 现场方案，CAT 走 USB 串口，RX 优先接 USB 外部音频；现场条件不足时可切到手机麦克风混合模式。");
                } else {
                    builder.append("\nXiegu 提示：G90 系列当前先按“CAT 与音频分离”的现场形态承接；RX 可先走手机麦克风或外置音频接口。");
                }
            }
            if (settings.serialCatPortHint() != null) {
                builder.append("\n串口端口提示：").append(settings.serialCatPortHint());
            }
            builder.append("\n键控线：").append(settings.serialCatKeyLine().name());
            if (settings.serialCatKeyingPortHint() != null) {
                builder.append("\n键控端口：").append(settings.serialCatKeyingPortHint());
                builder.append("\n键控极性：").append(settings.serialCatKeyingPolarity());
                builder.append("\n键控时拉高 RTS：").append(settings.serialCatAssertRtsDuringKeying() ? "是" : "否");
                builder.append("\n键控时拉高 DTR：").append(settings.serialCatAssertDtrDuringKeying() ? "是" : "否");
            }
            if (settings.serialCatProtocolFamily() == CatProtocolFamily.ICOM_CIV
                    && settings.serialCatCivAddressHex() != null) {
                builder.append("\n")
                        .append(RigProfileFamilies.isXieguFamily(profile) ? "设备地址" : "CI-V 地址")
                        .append("：0x")
                        .append(settings.serialCatCivAddressHex());
            }
        }
        if (profile.hasCapability(RigCapability.NETWORK_CAT)) {
            builder.append("\n网络 CAT：");
            builder.append(settings.networkCatProtocolFamily().displayName());
            if (settings.networkHost() == null) {
                builder.append(" /（未填写主机）");
            } else {
                builder.append(" / ")
                        .append(settings.networkHost())
                        .append(":")
                        .append(settings.networkPort());
            }
            if (RigProfileFamilies.isYaesuFamily(profile)
                    && settings.networkCatProtocolFamily() == CatProtocolFamily.HAMLIB_RIGCTLD) {
                builder.append("\nYaesu 提示：这一家族当前更适合先通过可用的 rigctld 桥接路径联调。");
            } else if (RigProfileFamilies.isXieguFamily(profile)
                    && settings.networkCatProtocolFamily() == CatProtocolFamily.HAMLIB_RIGCTLD) {
                builder.append("\nXiegu 提示：当前优先建议先走原生 USB 串口 CAT；rigctld 仅适合作为后续桥接实验路径。");
            }
        }
        if (profile.hasCapability(RigCapability.BLUETOOTH_SERIAL)) {
            builder.append("\n蓝牙目标设备：")
                    .append(settings.bluetoothDeviceHint() == null
                            ? "（未填写）"
                            : settings.bluetoothDeviceHint());
        }
        if (profile.hasCapability(RigCapability.AUDIO_VOX)) {
            builder.append("\nVOX 提示：请在电台侧保守地微调 VOX 延时和音频增益。");
        }
    }

    private static String renderTransportKind(RigTransport.TransportKind kind) {
        if (kind == null) {
            return "-";
        }
        switch (kind) {
            case USB_SERIAL:
                return "USB 串口";
            case BLUETOOTH_SERIAL:
                return "蓝牙串口";
            case NETWORK_CAT:
                return "网络 CAT";
            case AUDIO_VOX:
                return "音频 VOX";
            default:
                return kind.name();
        }
    }

    private static String renderSerialCatFamilyLabel(
            RigProfile profile,
            RigProfileSettings settings
    ) {
        if (RigProfileFamilies.isXieguFamily(profile)) {
            return "Xiegu 串口 CAT";
        }
        return settings.serialCatProtocolFamily().displayName();
    }
}
