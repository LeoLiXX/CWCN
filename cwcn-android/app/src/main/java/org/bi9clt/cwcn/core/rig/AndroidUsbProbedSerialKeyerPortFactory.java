package org.bi9clt.cwcn.core.rig;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

public final class AndroidUsbProbedSerialKeyerPortFactory implements DedicatedKeyingPortFactory {
    private static final String TAG = "AndroidUsbKeying";

    private final Context appContext;

    public AndroidUsbProbedSerialKeyerPortFactory(Context context) {
        this.appContext = context == null ? null : context.getApplicationContext();
    }

    @Override
    public String describeAvailability(String portHint) {
        try {
            if (appContext == null) {
                return "USB keying port factory was created without an Android context.";
            }
            UsbManager usbManager = usbManager();
            if (usbManager == null) {
                return "USB manager is unavailable on this device.";
            }
            HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
            if (deviceList.isEmpty()) {
                return "No USB device is attached.";
            }
            CandidateResolution resolution = resolveCandidate(deviceList, portHint);
            if (resolution.requiresExplicitPortHint()) {
                return resolution.renderExplicitPortHintMessage();
            }
            ProbedKeyingCandidate candidate = resolution.candidate();
            if (candidate == null) {
                if (portHint != null && !portHint.trim().isEmpty()) {
                    return "Preferred USB keying port is not attached right now: " + portHint.trim();
                }
                return "USB device detected, but no supported USB serial keying port was found.";
            }
            if (!usbManager.hasPermission(candidate.device)) {
                return candidate.driverLabel + " USB keying port found, but app permission has not been granted yet.";
            }
            return candidate.driverLabel
                    + " USB keying port is attached and permission is available on port "
                    + candidate.port.getPortNumber()
                    + ".";
        } catch (RuntimeException exception) {
            Log.e(TAG, "USB keying availability probe crashed", exception);
            return "USB keying availability check failed before the probe could finish: "
                    + safeMessage(exception);
        }
    }

    @Override
    public boolean canOpenPort(String portHint) {
        String availability = describeAvailability(portHint);
        String normalized = availability == null ? "" : availability.trim().toLowerCase(java.util.Locale.US);
        return normalized.contains("permission is available");
    }

    @Override
    public SerialKeyerPort openPort(String portHint) {
        try {
            if (appContext == null) {
                return new DisconnectedSerialKeyerPort(
                        "usb-keying-no-context",
                        "USB Keying Port",
                        "USB keying port factory was created without an Android context."
                );
            }
            UsbManager usbManager = usbManager();
            if (usbManager == null) {
                return new DisconnectedSerialKeyerPort(
                        "usb-keying-no-manager",
                        "USB Keying Port",
                        "USB manager is unavailable on this device."
                );
            }
            CandidateResolution resolution = resolveCandidate(usbManager.getDeviceList(), portHint);
            if (resolution.requiresExplicitPortHint()) {
                return new DisconnectedSerialKeyerPort(
                        "usb-keying-port-ambiguous",
                        "USB Keying Port",
                        resolution.renderExplicitPortHintMessage()
                );
            }
            ProbedKeyingCandidate candidate = resolution.candidate();
            if (candidate == null) {
                return new DisconnectedSerialKeyerPort(
                        "usb-keying-no-port",
                        "USB Keying Port",
                        portHint == null || portHint.trim().isEmpty()
                                ? "No supported USB serial keying port is attached."
                                : "Preferred USB keying port is not attached: " + portHint.trim()
                );
            }
            if (!usbManager.hasPermission(candidate.device)) {
                return new DisconnectedSerialKeyerPort(
                        "usb-keying-no-permission",
                        candidate.device.getDeviceName(),
                        "USB keying port is attached, but app permission has not been granted yet."
                );
            }
            UsbDeviceConnection connection = usbManager.openDevice(candidate.device);
            if (connection == null) {
                return new DisconnectedSerialKeyerPort(
                        "usb-keying-open-failed",
                        candidate.device.getDeviceName(),
                        "USB keying permission exists, but opening the device failed."
                );
            }
            try {
                candidate.port.open(connection);
                candidate.port.setParameters(
                        9600,
                        UsbSerialPort.DATABITS_8,
                        UsbSerialPort.STOPBITS_1,
                        UsbSerialPort.PARITY_NONE
                );
                return new AndroidUsbProbedSerialKeyerPort(
                        candidate.device,
                        candidate.port,
                        connection,
                        candidate.driverLabel
                );
            } catch (IOException | RuntimeException exception) {
                try {
                    connection.close();
                } catch (RuntimeException ignored) {
                }
                return new DisconnectedSerialKeyerPort(
                        "usb-keying-open-failed",
                        candidate.device.getDeviceName(),
                        "USB keying port failed to open: " + safeMessage(exception)
                );
            }
        } catch (RuntimeException exception) {
            Log.e(TAG, "USB keying port open crashed", exception);
            return new DisconnectedSerialKeyerPort(
                    "usb-keying-crashed",
                    "USB Keying Port",
                    "USB keying port crashed before it could open: " + safeMessage(exception)
            );
        }
    }

    private UsbManager usbManager() {
        return appContext == null ? null : ContextCompat.getSystemService(appContext, UsbManager.class);
    }

    private CandidateResolution resolveCandidate(HashMap<String, UsbDevice> deviceList, String portHint) {
        if (deviceList == null || deviceList.isEmpty()) {
            return CandidateResolution.empty();
        }
        PortHintSelection hintSelection = PortHintSelection.parse(portHint);
        ProbedKeyingCandidate firstCandidate = null;
        int candidateCount = 0;
        List<String> candidateHints = new java.util.ArrayList<>();
        List<UsbSerialDriver> drivers = usbManager() == null
                ? java.util.Collections.emptyList()
                : UsbSerialProber.defaultProber().findAllDrivers(usbManager());
        for (UsbSerialDriver driver : drivers) {
            if (driver == null || driver.getPorts().isEmpty()) {
                continue;
            }
            UsbDevice device = driver.getDevice();
            for (UsbSerialPort port : driver.getPorts()) {
                ProbedKeyingCandidate candidate = new ProbedKeyingCandidate(device, port, renderDriverLabel(driver));
                candidateCount += 1;
                candidateHints.add(device.getDeviceName() + "#" + port.getPortNumber());
                if (firstCandidate == null) {
                    firstCandidate = candidate;
                }
                if (!hintSelection.hasDeviceHint()) {
                    continue;
                }
                if (!hintSelection.matchesDevice(device.getDeviceName())) {
                    continue;
                }
                if (hintSelection.matchesPort(port.getPortNumber())) {
                    return CandidateResolution.resolved(candidate, candidateHints);
                }
            }
        }
        if (hintSelection.hasDeviceHint()) {
            return CandidateResolution.resolved(null, candidateHints);
        }
        if (candidateCount > 1) {
            return CandidateResolution.requireExplicitPortHint(candidateHints);
        }
        return CandidateResolution.resolved(firstCandidate, candidateHints);
    }

    private static String renderDriverLabel(UsbSerialDriver driver) {
        if (driver instanceof Cp21xxSerialDriver) {
            return "CP210x";
        }
        if (driver instanceof CdcAcmSerialDriver) {
            return "CDC/ACM";
        }
        if (driver instanceof FtdiSerialDriver) {
            return "FTDI";
        }
        if (driver instanceof ProlificSerialDriver) {
            return "Prolific";
        }
        if (driver instanceof Ch34xSerialDriver) {
            return "CH34x";
        }
        return "USB serial";
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().trim().isEmpty()) {
            return throwable == null ? "unknown failure" : throwable.getClass().getSimpleName();
        }
        return throwable.getMessage().trim();
    }

    private static final class ProbedKeyingCandidate {
        private final UsbDevice device;
        private final UsbSerialPort port;
        private final String driverLabel;

        private ProbedKeyingCandidate(UsbDevice device, UsbSerialPort port, String driverLabel) {
            this.device = device;
            this.port = port;
            this.driverLabel = driverLabel;
        }
    }

    private static final class PortHintSelection {
        private final String deviceHint;
        private final Integer portNumber;

        private PortHintSelection(String deviceHint, Integer portNumber) {
            this.deviceHint = deviceHint;
            this.portNumber = portNumber;
        }

        private static PortHintSelection parse(String rawHint) {
            if (rawHint == null) {
                return new PortHintSelection(null, null);
            }
            String normalized = rawHint.trim();
            if (normalized.isEmpty()) {
                return new PortHintSelection(null, null);
            }
            int separator = normalized.lastIndexOf('#');
            if (separator > 0 && separator < normalized.length() - 1) {
                String suffix = normalized.substring(separator + 1).trim();
                try {
                    return new PortHintSelection(
                            normalized.substring(0, separator).trim(),
                            Integer.parseInt(suffix)
                    );
                } catch (NumberFormatException ignored) {
                }
            }
            return new PortHintSelection(normalized, null);
        }

        private boolean hasDeviceHint() {
            return deviceHint != null && !deviceHint.isEmpty();
        }

        private boolean matchesDevice(String deviceName) {
            return deviceHint != null && deviceHint.equals(deviceName);
        }

        private boolean matchesPort(int portNumber) {
            return this.portNumber == null || this.portNumber == portNumber;
        }
    }

    private static final class CandidateResolution {
        private final ProbedKeyingCandidate candidate;
        private final boolean requiresExplicitPortHint;
        private final List<String> candidateHints;

        private CandidateResolution(
                ProbedKeyingCandidate candidate,
                boolean requiresExplicitPortHint,
                List<String> candidateHints
        ) {
            this.candidate = candidate;
            this.requiresExplicitPortHint = requiresExplicitPortHint;
            this.candidateHints = candidateHints == null ? java.util.Collections.emptyList() : candidateHints;
        }

        private static CandidateResolution empty() {
            return new CandidateResolution(null, false, java.util.Collections.emptyList());
        }

        private static CandidateResolution resolved(ProbedKeyingCandidate candidate, List<String> candidateHints) {
            return new CandidateResolution(candidate, false, candidateHints);
        }

        private static CandidateResolution requireExplicitPortHint(List<String> candidateHints) {
            return new CandidateResolution(null, true, candidateHints);
        }

        private ProbedKeyingCandidate candidate() {
            return candidate;
        }

        private boolean requiresExplicitPortHint() {
            return requiresExplicitPortHint;
        }

        private String renderExplicitPortHintMessage() {
            if (candidateHints.isEmpty()) {
                return "Multiple USB keying ports were detected. Choose the detected keying port before transmitting.";
            }
            return "Multiple USB keying ports were detected. Choose the detected keying port before transmitting. Candidates: "
                    + String.join(", ", candidateHints);
        }
    }
}
