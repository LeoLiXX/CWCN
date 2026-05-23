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
    public PortAvailability availability(String portHint) {
        try {
            if (appContext == null) {
                return new PortAvailability(PortAvailability.Stage.NO_CONTEXT, "USB 键控口工厂创建时缺少 Android Context。");
            }
            UsbManager usbManager = usbManager();
            if (usbManager == null) {
                return new PortAvailability(PortAvailability.Stage.NO_MANAGER, "当前设备无法获取 USB 管理器。");
            }
            HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
            if (deviceList.isEmpty()) {
                return new PortAvailability(PortAvailability.Stage.NO_DEVICE, "当前没有连接 USB 设备。");
            }
            CandidateResolution resolution = resolveCandidate(deviceList, portHint);
            if (resolution.requiresExplicitPortHint()) {
                return new PortAvailability(
                        PortAvailability.Stage.MULTIPLE_CANDIDATES,
                        resolution.renderExplicitPortHintMessage()
                );
            }
            ProbedKeyingCandidate candidate = resolution.candidate();
            if (candidate == null) {
                if (portHint != null && !portHint.trim().isEmpty()) {
                    return new PortAvailability(
                            PortAvailability.Stage.TARGET_MISSING,
                            "已指定的 USB 键控口当前未连接：" + portHint.trim()
                    );
                }
                return new PortAvailability(
                        PortAvailability.Stage.NO_SUPPORTED_PORT,
                        "已检测到 USB 设备，但没有找到受支持的 USB 串口键控口。"
                );
            }
            if (!usbManager.hasPermission(candidate.device)) {
                return new PortAvailability(
                        PortAvailability.Stage.NO_PERMISSION,
                        candidate.driverLabel + " USB 键控口已找到，但应用尚未获得权限。"
                );
            }
            return new PortAvailability(
                    PortAvailability.Stage.READY,
                    candidate.driverLabel
                            + " USB 键控口已连接，端口权限已就绪："
                            + candidate.port.getPortNumber()
                            + "。"
            );
        } catch (RuntimeException exception) {
            Log.e(TAG, "USB keying availability probe crashed", exception);
            return new PortAvailability(
                    PortAvailability.Stage.ERROR,
                    "USB 键控口可用性检查在完成前失败：" + safeMessage(exception)
            );
        }
    }

    @Override
    public SerialKeyerPort openPort(String portHint) {
        try {
            if (appContext == null) {
                return new DisconnectedSerialKeyerPort(
                        "usb-keying-no-context",
                        "USB 键控口",
                        "USB 键控口工厂创建时缺少 Android Context。"
                );
            }
            UsbManager usbManager = usbManager();
            if (usbManager == null) {
                return new DisconnectedSerialKeyerPort(
                        "usb-keying-no-manager",
                        "USB 键控口",
                        "当前设备无法获取 USB 管理器。"
                );
            }
            CandidateResolution resolution = resolveCandidate(usbManager.getDeviceList(), portHint);
            if (resolution.requiresExplicitPortHint()) {
                return new DisconnectedSerialKeyerPort(
                        "usb-keying-port-ambiguous",
                        "USB 键控口",
                        resolution.renderExplicitPortHintMessage()
                );
            }
            ProbedKeyingCandidate candidate = resolution.candidate();
            if (candidate == null) {
                return new DisconnectedSerialKeyerPort(
                        "usb-keying-no-port",
                        "USB 键控口",
                        portHint == null || portHint.trim().isEmpty()
                                ? "当前没有连接受支持的 USB 串口键控口。"
                                : "已指定的 USB 键控口当前未连接：" + portHint.trim()
                );
            }
            if (!usbManager.hasPermission(candidate.device)) {
                return new DisconnectedSerialKeyerPort(
                        "usb-keying-no-permission",
                        candidate.device.getDeviceName(),
                        "USB 键控口已连接，但应用尚未获得权限。"
                );
            }
            UsbDeviceConnection connection = usbManager.openDevice(candidate.device);
            if (connection == null) {
                return new DisconnectedSerialKeyerPort(
                        "usb-keying-open-failed",
                        candidate.device.getDeviceName(),
                        "USB 权限已存在，但打开键控口失败。"
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
                        "USB 键控口打开失败：" + safeMessage(exception)
                );
            }
        } catch (RuntimeException exception) {
            Log.e(TAG, "USB keying port open crashed", exception);
            return new DisconnectedSerialKeyerPort(
                    "usb-keying-crashed",
                    "USB 键控口",
                    "USB 键控口在打开前异常中断：" + safeMessage(exception)
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
        return "USB 串口";
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().trim().isEmpty()) {
            return throwable == null ? "未知故障" : throwable.getClass().getSimpleName();
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
                return "检测到多个 USB 键控口。请先明确选择一个键控口，再开始发射。";
            }
            return "检测到多个 USB 键控口。请先明确选择一个键控口，再开始发射。候选项："
                    + String.join(", ", candidateHints);
        }
    }
}
