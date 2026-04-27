package org.bi9clt.cwcn.core.rig;

public final class RigProfileSettings {
    private final int defaultWpm;
    private final int defaultToneFrequencyHz;
    private final SerialKeyerTxOutput.KeyLine usbKeyLine;
    private final String usbPreferredDeviceName;
    private final CatProtocolFamily serialCatProtocolFamily;
    private final int serialCatBaudRate;
    private final String serialCatPortHint;
    private final SerialKeyerTxOutput.KeyLine serialCatKeyLine;
    private final String serialCatKeyingPortHint;
    private final KeyingPolarity serialCatKeyingPolarity;
    private final boolean serialCatAssertRtsDuringKeying;
    private final boolean serialCatAssertDtrDuringKeying;
    private final String serialCatCivAddressHex;
    private final CatProtocolFamily networkCatProtocolFamily;
    private final String networkHost;
    private final int networkPort;
    private final String bluetoothDeviceHint;

    public RigProfileSettings(
            int defaultWpm,
            int defaultToneFrequencyHz,
            SerialKeyerTxOutput.KeyLine usbKeyLine,
            String usbPreferredDeviceName,
            CatProtocolFamily serialCatProtocolFamily,
            int serialCatBaudRate,
            String serialCatPortHint,
            CatProtocolFamily networkCatProtocolFamily,
            String networkHost,
            int networkPort,
            String bluetoothDeviceHint
    ) {
        this(
                defaultWpm,
                defaultToneFrequencyHz,
                usbKeyLine,
                usbPreferredDeviceName,
                serialCatProtocolFamily,
                serialCatBaudRate,
                serialCatPortHint,
                usbKeyLine,
                null,
                KeyingPolarity.ACTIVE_HIGH,
                usbKeyLine == SerialKeyerTxOutput.KeyLine.RTS,
                usbKeyLine == SerialKeyerTxOutput.KeyLine.DTR,
                null,
                networkCatProtocolFamily,
                networkHost,
                networkPort,
                bluetoothDeviceHint
        );
    }

    public RigProfileSettings(
            int defaultWpm,
            int defaultToneFrequencyHz,
            SerialKeyerTxOutput.KeyLine usbKeyLine,
            String usbPreferredDeviceName,
            CatProtocolFamily serialCatProtocolFamily,
            int serialCatBaudRate,
            String serialCatPortHint,
            String serialCatCivAddressHex,
            CatProtocolFamily networkCatProtocolFamily,
            String networkHost,
            int networkPort,
            String bluetoothDeviceHint
    ) {
        this(
                defaultWpm,
                defaultToneFrequencyHz,
                usbKeyLine,
                usbPreferredDeviceName,
                serialCatProtocolFamily,
                serialCatBaudRate,
                serialCatPortHint,
                usbKeyLine,
                null,
                KeyingPolarity.ACTIVE_HIGH,
                usbKeyLine == SerialKeyerTxOutput.KeyLine.RTS,
                usbKeyLine == SerialKeyerTxOutput.KeyLine.DTR,
                serialCatCivAddressHex,
                networkCatProtocolFamily,
                networkHost,
                networkPort,
                bluetoothDeviceHint
        );
    }

    public RigProfileSettings(
            int defaultWpm,
            int defaultToneFrequencyHz,
            SerialKeyerTxOutput.KeyLine usbKeyLine,
            String usbPreferredDeviceName,
            CatProtocolFamily serialCatProtocolFamily,
            int serialCatBaudRate,
            String serialCatPortHint,
            SerialKeyerTxOutput.KeyLine serialCatKeyLine,
            String serialCatKeyingPortHint,
            String serialCatCivAddressHex,
            CatProtocolFamily networkCatProtocolFamily,
            String networkHost,
            int networkPort,
            String bluetoothDeviceHint
    ) {
        this(
                defaultWpm,
                defaultToneFrequencyHz,
                usbKeyLine,
                usbPreferredDeviceName,
                serialCatProtocolFamily,
                serialCatBaudRate,
                serialCatPortHint,
                serialCatKeyLine,
                serialCatKeyingPortHint,
                KeyingPolarity.ACTIVE_HIGH,
                serialCatKeyLine == SerialKeyerTxOutput.KeyLine.RTS,
                serialCatKeyLine == SerialKeyerTxOutput.KeyLine.DTR,
                serialCatCivAddressHex,
                networkCatProtocolFamily,
                networkHost,
                networkPort,
                bluetoothDeviceHint
        );
    }

    public RigProfileSettings(
            int defaultWpm,
            int defaultToneFrequencyHz,
            SerialKeyerTxOutput.KeyLine usbKeyLine,
            String usbPreferredDeviceName,
            CatProtocolFamily serialCatProtocolFamily,
            int serialCatBaudRate,
            String serialCatPortHint,
            SerialKeyerTxOutput.KeyLine serialCatKeyLine,
            String serialCatKeyingPortHint,
            KeyingPolarity serialCatKeyingPolarity,
            boolean serialCatAssertRtsDuringKeying,
            boolean serialCatAssertDtrDuringKeying,
            String serialCatCivAddressHex,
            CatProtocolFamily networkCatProtocolFamily,
            String networkHost,
            int networkPort,
            String bluetoothDeviceHint
    ) {
        this.defaultWpm = sanitizePositive(defaultWpm, 18);
        this.defaultToneFrequencyHz = sanitizePositive(defaultToneFrequencyHz, 650);
        this.usbKeyLine = usbKeyLine == null ? SerialKeyerTxOutput.KeyLine.RTS : usbKeyLine;
        this.usbPreferredDeviceName = sanitizeText(usbPreferredDeviceName);
        this.serialCatProtocolFamily = serialCatProtocolFamily == null
                ? CatProtocolFamily.GENERIC
                : serialCatProtocolFamily;
        this.serialCatBaudRate = sanitizePositive(serialCatBaudRate, 9600);
        this.serialCatPortHint = sanitizeText(serialCatPortHint);
        this.serialCatKeyLine = serialCatKeyLine == null ? this.usbKeyLine : serialCatKeyLine;
        this.serialCatKeyingPortHint = sanitizeText(serialCatKeyingPortHint);
        this.serialCatKeyingPolarity = serialCatKeyingPolarity == null
                ? KeyingPolarity.ACTIVE_HIGH
                : serialCatKeyingPolarity;
        boolean defaultAssertRts = this.serialCatKeyLine == SerialKeyerTxOutput.KeyLine.RTS;
        boolean defaultAssertDtr = this.serialCatKeyLine == SerialKeyerTxOutput.KeyLine.DTR;
        this.serialCatAssertRtsDuringKeying = serialCatAssertRtsDuringKeying || (!serialCatAssertDtrDuringKeying && defaultAssertRts);
        this.serialCatAssertDtrDuringKeying = serialCatAssertDtrDuringKeying || (!serialCatAssertRtsDuringKeying && defaultAssertDtr);
        this.serialCatCivAddressHex = sanitizeHexByte(serialCatCivAddressHex);
        this.networkCatProtocolFamily = networkCatProtocolFamily == null
                ? CatProtocolFamily.HAMLIB_RIGCTLD
                : networkCatProtocolFamily;
        this.networkHost = sanitizeText(networkHost);
        this.networkPort = sanitizePositive(networkPort, 4532);
        this.bluetoothDeviceHint = sanitizeText(bluetoothDeviceHint);
    }

    public static RigProfileSettings defaults() {
        return new RigProfileSettings(
                18,
                650,
                SerialKeyerTxOutput.KeyLine.RTS,
                null,
                CatProtocolFamily.GENERIC,
                9600,
                null,
                SerialKeyerTxOutput.KeyLine.RTS,
                null,
                KeyingPolarity.ACTIVE_HIGH,
                true,
                false,
                null,
                CatProtocolFamily.HAMLIB_RIGCTLD,
                null,
                4532,
                null
        );
    }

    public int defaultWpm() {
        return defaultWpm;
    }

    public int defaultToneFrequencyHz() {
        return defaultToneFrequencyHz;
    }

    public SerialKeyerTxOutput.KeyLine usbKeyLine() {
        return usbKeyLine;
    }

    public String usbPreferredDeviceName() {
        return usbPreferredDeviceName;
    }

    public CatProtocolFamily serialCatProtocolFamily() {
        return serialCatProtocolFamily;
    }

    public int serialCatBaudRate() {
        return serialCatBaudRate;
    }

    public String serialCatPortHint() {
        return serialCatPortHint;
    }

    public SerialKeyerTxOutput.KeyLine serialCatKeyLine() {
        return serialCatKeyLine;
    }

    public String serialCatKeyingPortHint() {
        return serialCatKeyingPortHint;
    }

    public KeyingPolarity serialCatKeyingPolarity() {
        return serialCatKeyingPolarity;
    }

    public boolean serialCatAssertRtsDuringKeying() {
        return serialCatAssertRtsDuringKeying;
    }

    public boolean serialCatAssertDtrDuringKeying() {
        return serialCatAssertDtrDuringKeying;
    }

    public String serialCatCivAddressHex() {
        return serialCatCivAddressHex;
    }

    public CatProtocolFamily networkCatProtocolFamily() {
        return networkCatProtocolFamily;
    }

    public String networkHost() {
        return networkHost;
    }

    public int networkPort() {
        return networkPort;
    }

    public String bluetoothDeviceHint() {
        return bluetoothDeviceHint;
    }

    private int sanitizePositive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private String sanitizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String sanitizeHexByte(String value) {
        String trimmed = sanitizeText(value);
        if (trimmed == null) {
            return null;
        }
        String normalized = trimmed.toUpperCase();
        if (normalized.startsWith("0X")) {
            normalized = normalized.substring(2);
        }
        if (normalized.length() == 1) {
            normalized = "0" + normalized;
        }
        if (normalized.length() != 2) {
            return null;
        }
        for (int index = 0; index < normalized.length(); index++) {
            char ch = normalized.charAt(index);
            boolean hex = (ch >= '0' && ch <= '9')
                    || (ch >= 'A' && ch <= 'F');
            if (!hex) {
                return null;
            }
        }
        return normalized;
    }
}
