package org.bi9clt.cwcn.core.rig;

import android.content.Context;

import java.io.IOException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RigFrequencyResolver {
    private static final int COMMAND_TIMEOUT_MS = 1200;
    private static final Pattern ASCII_FA_PATTERN = Pattern.compile("FA([0-9]{8,11})", Pattern.CASE_INSENSITIVE);
    private static final Pattern ASCII_IF_PATTERN = Pattern.compile("IF([0-9]{8,11})", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIGIT_PATTERN = Pattern.compile("([0-9]{6,11})");

    private final RigSelectionStore selectionStore;
    private final HamlibRigctldSessionFactory hamlibSessionFactory;
    private final SerialCatSessionFactory serialCatSessionFactory;

    public RigFrequencyResolver(Context context) {
        Context appContext = context.getApplicationContext();
        selectionStore = new RigSelectionStore(appContext);
        hamlibSessionFactory = new SocketHamlibRigctldSessionFactory();
        serialCatSessionFactory = new AndroidUsbSerialCatSessionFactory(appContext);
    }

    public long readCurrentFrequencyHz() {
        RigProfile profile = selectionStore.selectedProfile();
        if (profile == null) {
            return 0L;
        }
        RigProfileSettings settings = selectionStore.loadSettings(profile);
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
        String portHint = settings.serialCatPortHint();
        if (portHint == null || portHint.trim().isEmpty()) {
            return 0L;
        }
        CatProtocolFamily family = settings.serialCatProtocolFamily();
        if (family != CatProtocolFamily.YAESU_STYLE && family != CatProtocolFamily.KENWOOD_STYLE) {
            return 0L;
        }
        try (SerialCatSession session = serialCatSessionFactory.openSession(portHint, settings.serialCatBaudRate())) {
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
}
