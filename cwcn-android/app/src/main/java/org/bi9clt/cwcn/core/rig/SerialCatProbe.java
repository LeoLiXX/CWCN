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
            return new ProbeResult(false, "Selected profile does not use serial CAT.");
        }
        if (sessionFactory == null) {
            return new ProbeResult(false, "Serial CAT session factory is unavailable.");
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
                "Serial CAT probe is currently implemented for Yaesu-style CAT, Icom CI-V, and Kenwood-style CAT first."
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
                        "Serial CAT link opened, but the radio returned no readable response to FA;/IF;. Check baud rate, USB CDC mode, and radio-side CAT settings."
                );
            }
            return new ProbeResult(
                    true,
                    "Serial CAT responded. First reply: " + response + " Start with configuration validation first; TX-side native Yaesu CAT work is still in progress."
            );
        } catch (IOException exception) {
            return new ProbeResult(false, "Serial CAT probe failed: " + exception.getMessage());
        }
    }

    private static ProbeResult probeIcomCiv(
            RigProfileSettings settings,
            SerialCatSessionFactory sessionFactory
    ) {
        if (settings.serialCatCivAddressHex() == null) {
            return new ProbeResult(false, "Set the CI-V address first, then retry the serial CAT probe.");
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
                        "CI-V link opened, but the radio returned no readable response to the transceiver-ID query. Check CI-V address, baud rate, and radio-side CI-V settings."
                );
            }
            return new ProbeResult(
                    true,
                    "CI-V responded. First reply: " + toHex(response) + " Start with read/probe validation first; native Icom TX work is still in progress."
            );
        } catch (IOException exception) {
            return new ProbeResult(false, "Serial CAT probe failed: " + exception.getMessage());
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
                        "Kenwood-style CAT link opened, but the radio returned no readable response to ID;/FA;/IF;. Check baud rate, serial framing, and radio-side CAT settings."
                );
            }
            return new ProbeResult(
                    true,
                    "Kenwood-style CAT responded. First reply: " + response + " Start with read/probe validation first; native Kenwood TX work is still in progress."
            );
        } catch (IOException exception) {
            return new ProbeResult(false, "Serial CAT probe failed: " + exception.getMessage());
        }
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
