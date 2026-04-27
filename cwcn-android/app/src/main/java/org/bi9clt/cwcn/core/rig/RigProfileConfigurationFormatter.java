package org.bi9clt.cwcn.core.rig;

public final class RigProfileConfigurationFormatter {
    private RigProfileConfigurationFormatter() {
    }

    public static String renderCompactSummary(RigProfile profile, RigProfileSettings settings) {
        if (profile == null) {
            return "Rig path: none pinned";
        }
        RigProfileSettings safeSettings = settings == null ? RigProfileSettings.defaults() : settings;
        StringBuilder builder = new StringBuilder();
        builder.append("Rig path: ").append(profile.displayName())
                .append("\nSupport level: ").append(profile.supportLevel().displayName())
                .append("\nTransport: ").append(profile.transportKind().name())
                .append("\nCW defaults: ")
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
            builder.append("\nUSB key line: ").append(settings.usbKeyLine().name());
            if (settings.usbPreferredDeviceName() != null) {
                builder.append("\nUSB device hint: ").append(settings.usbPreferredDeviceName());
            }
        }
        if (profile.hasCapability(RigCapability.SERIAL_CAT)) {
            builder.append("\nSerial CAT: ")
                    .append(settings.serialCatProtocolFamily().displayName())
                    .append(" / ")
                    .append(settings.serialCatBaudRate())
                    .append(" baud");
            if (settings.serialCatPortHint() != null) {
                builder.append("\nSerial port hint: ").append(settings.serialCatPortHint());
            }
            builder.append("\nCW keying line: ").append(settings.serialCatKeyLine().name());
            if (settings.serialCatKeyingPortHint() != null) {
                builder.append("\nCW keying port: ").append(settings.serialCatKeyingPortHint());
                builder.append("\nCW keying polarity: ").append(settings.serialCatKeyingPolarity());
                builder.append("\nAssert RTS during keying: ").append(settings.serialCatAssertRtsDuringKeying() ? "yes" : "no");
                builder.append("\nAssert DTR during keying: ").append(settings.serialCatAssertDtrDuringKeying() ? "yes" : "no");
            }
            if (settings.serialCatProtocolFamily() == CatProtocolFamily.ICOM_CIV
                    && settings.serialCatCivAddressHex() != null) {
                builder.append("\nCI-V address: 0x").append(settings.serialCatCivAddressHex());
            }
        }
        if (profile.hasCapability(RigCapability.NETWORK_CAT)) {
            builder.append("\nNetwork CAT: ");
            builder.append(settings.networkCatProtocolFamily().displayName());
            if (settings.networkHost() == null) {
                builder.append(" / (host not set)");
            } else {
                builder.append(" / ")
                        .append(settings.networkHost())
                        .append(":")
                        .append(settings.networkPort());
            }
            if (RigProfileFamilies.isYaesuFamily(profile)
                    && settings.networkCatProtocolFamily() == CatProtocolFamily.HAMLIB_RIGCTLD) {
                builder.append("\nYaesu note: this family currently benches best through a working rigctld bridge.");
            }
        }
        if (profile.hasCapability(RigCapability.BLUETOOTH_SERIAL)) {
            builder.append("\nBluetooth target: ")
                    .append(settings.bluetoothDeviceHint() == null
                            ? "(not set)"
                            : settings.bluetoothDeviceHint());
        }
        if (profile.hasCapability(RigCapability.AUDIO_VOX)) {
            builder.append("\nVOX note: tune radio-side VOX delay and audio gain conservatively.");
        }
    }
}
