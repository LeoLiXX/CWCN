package org.bi9clt.cwcn.core.rig;

import android.content.Context;

import java.io.IOException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RigFrequencyResolver {
    private static final int COMMAND_TIMEOUT_MS = 1200;
    private static final int ICOM_CIV_COMMAND_READ_FREQUENCY = 0x03;
    private static final Pattern ASCII_FA_PATTERN = Pattern.compile("FA([0-9]{8,11})", Pattern.CASE_INSENSITIVE);
    private static final Pattern ASCII_IF_PATTERN = Pattern.compile("IF([0-9]{8,11})", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIGIT_PATTERN = Pattern.compile("([0-9]{6,11})");

    private final SelectionSource selectionSource;
    private final HamlibRigctldSessionFactory hamlibSessionFactory;
    private final SerialCatSessionFactory serialCatSessionFactory;

    public RigFrequencyResolver(Context context) {
        Context appContext = context.getApplicationContext();
        selectionSource = new StoreBackedSelectionSource(new RigSelectionStore(appContext));
        hamlibSessionFactory = new SocketHamlibRigctldSessionFactory();
        serialCatSessionFactory = new AndroidUsbSerialCatSessionFactory(appContext);
    }

    RigFrequencyResolver(
            SelectionSource selectionSource,
            HamlibRigctldSessionFactory hamlibSessionFactory,
            SerialCatSessionFactory serialCatSessionFactory
    ) {
        this.selectionSource = selectionSource;
        this.hamlibSessionFactory = hamlibSessionFactory;
        this.serialCatSessionFactory = serialCatSessionFactory;
    }

    public long readCurrentFrequencyHz() {
        RigProfile profile = selectionSource.selectedProfile();
        if (profile == null) {
            return 0L;
        }
        RigProfileSettings settings = selectionSource.loadSettings(profile);
        if (profile.hasCapability(RigCapability.NETWORK_CAT)
                && settings.networkCatProtocolFamily() == CatProtocolFamily.HAMLIB_RIGCTLD) {
            long networkFrequency = readRigctldFrequencyHz(settings);
            if (networkFrequency > 0L) {
                return networkFrequency;
            }
        }
        if (profile.hasCapability(RigCapability.SERIAL_CAT)) {
            return readSerialCatFrequencyHz(settings);
        }
        return 0L;
    }

    private long readRigctldFrequencyHz(RigProfileSettings settings) {
        String host = settings.networkHost();
        if (host == null || host.trim().isEmpty()) {
            return 0L;
        }
        try (HamlibRigctldSession session = hamlibSessionFactory.open(host, settings.networkPort())) {
            return parseRigctldFrequencyHz(session.transact("f"));
        } catch (IOException exception) {
            return 0L;
        }
    }

    private long readSerialCatFrequencyHz(RigProfileSettings settings) {
        CatProtocolFamily family = settings.serialCatProtocolFamily();
        if (family != CatProtocolFamily.YAESU_STYLE
                && family != CatProtocolFamily.KENWOOD_STYLE
                && family != CatProtocolFamily.ICOM_CIV) {
            return 0L;
        }
        try (SerialCatSession session = serialCatSessionFactory.openSession(
                settings.serialCatPortHint(),
                settings.serialCatBaudRate()
        )) {
            if (family == CatProtocolFamily.ICOM_CIV) {
                if (settings.serialCatCivAddressHex() == null) {
                    return 0L;
                }
                return parseIcomFrequencyResponseHz(session.transact(
                        SerialCatRigControlAdapter.buildIcomFrequencyReadCommand(settings),
                        COMMAND_TIMEOUT_MS
                ));
            }
            long frequencyHz = parseAsciiFrequencyHz(session.transact("FA;", COMMAND_TIMEOUT_MS), ASCII_FA_PATTERN);
            if (frequencyHz > 0L) {
                return frequencyHz;
            }
            return parseAsciiFrequencyHz(session.transact("IF;", COMMAND_TIMEOUT_MS), ASCII_IF_PATTERN);
        } catch (IOException exception) {
            return 0L;
        }
    }

    private long parseRigctldFrequencyHz(String raw) {
        if (raw == null) {
            return 0L;
        }
        String normalized = raw.trim();
        if (normalized.isEmpty()) {
            return 0L;
        }
        try {
            double parsed = Double.parseDouble(normalized);
            if (parsed <= 0d) {
                return 0L;
            }
            if (parsed < 100000d) {
                return Math.round(parsed * 1_000_000d);
            }
            return Math.round(parsed);
        } catch (NumberFormatException exception) {
            Matcher matcher = DIGIT_PATTERN.matcher(normalized);
            if (!matcher.find()) {
                return 0L;
            }
            try {
                return Long.parseLong(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
    }

    private long parseAsciiFrequencyHz(String raw, Pattern pattern) {
        if (raw == null) {
            return 0L;
        }
        Matcher matcher = pattern.matcher(raw.toUpperCase(Locale.US));
        if (!matcher.find()) {
            return 0L;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private long parseIcomFrequencyResponseHz(byte[] response) {
        if (response == null || response.length < 7) {
            return 0L;
        }
        if ((response[0] & 0xFF) != 0xFE || (response[1] & 0xFF) != 0xFE) {
            return 0L;
        }
        if ((response[4] & 0xFF) != ICOM_CIV_COMMAND_READ_FREQUENCY) {
            return 0L;
        }
        int endIndex = -1;
        for (int index = 5; index < response.length; index++) {
            if ((response[index] & 0xFF) == 0xFD) {
                endIndex = index;
                break;
            }
        }
        if (endIndex <= 5) {
            return 0L;
        }
        long frequencyHz = 0L;
        long multiplier = 1L;
        for (int index = 5; index < endIndex; index++) {
            int value = response[index] & 0xFF;
            int lowDigit = value & 0x0F;
            int highDigit = (value >> 4) & 0x0F;
            if (lowDigit > 9 || highDigit > 9) {
                return 0L;
            }
            frequencyHz += lowDigit * multiplier;
            multiplier *= 10L;
            frequencyHz += highDigit * multiplier;
            multiplier *= 10L;
        }
        return Math.max(frequencyHz, 0L);
    }

    interface SelectionSource {
        RigProfile selectedProfile();

        RigProfileSettings loadSettings(RigProfile profile);
    }

    private static final class StoreBackedSelectionSource implements SelectionSource {
        private final RigSelectionStore selectionStore;

        private StoreBackedSelectionSource(RigSelectionStore selectionStore) {
            this.selectionStore = selectionStore;
        }

        @Override
        public RigProfile selectedProfile() {
            return selectionStore.selectedProfile();
        }

        @Override
        public RigProfileSettings loadSettings(RigProfile profile) {
            return selectionStore.loadSettings(profile);
        }
    }
}
