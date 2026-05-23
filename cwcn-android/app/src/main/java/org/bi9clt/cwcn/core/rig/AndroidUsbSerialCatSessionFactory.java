package org.bi9clt.cwcn.core.rig;

import android.app.PendingIntent;
import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

public final class AndroidUsbSerialCatSessionFactory implements SerialCatSessionFactory {
    private static final String TAG = "AndroidUsbSerialCat";

    private final Context appContext;

    public AndroidUsbSerialCatSessionFactory(Context context) {
        this.appContext = context == null ? null : context.getApplicationContext();
    }

    @Override
    public PortAvailability availability(String portHint) {
        try {
            if (appContext == null) {
                return new PortAvailability(PortAvailability.Stage.NO_CONTEXT, "串口 CAT 会话工厂创建时缺少 Android Context。");
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
            ProbedSerialCandidate candidate = resolution.candidate();
            if (candidate == null) {
                if (portHint != null && !portHint.trim().isEmpty()) {
                    return new PortAvailability(
                            PortAvailability.Stage.TARGET_MISSING,
                            "已指定的串口 CAT 设备当前未连接：" + portHint.trim()
                    );
                }
                return new PortAvailability(
                        PortAvailability.Stage.NO_SUPPORTED_PORT,
                        "已检测到 USB 设备，但没有找到受支持的 USB 串口 CAT 驱动候选。当前原生路径识别 CDC/ACM、CP210x、FTDI、Prolific 和 CH34x。"
                );
            }
            if (!usbManager.hasPermission(candidate.device)) {
                return new PortAvailability(
                        PortAvailability.Stage.NO_PERMISSION,
                        candidate.driverLabel + " USB 串口 CAT 设备已找到，但应用尚未获得权限。"
                );
            }
            return new PortAvailability(
                    PortAvailability.Stage.READY,
                    candidate.driverLabel
                            + " USB 串口 CAT 设备已连接，端口权限已就绪："
                            + candidate.port.getPortNumber()
                            + "。"
            );
        } catch (RuntimeException exception) {
            Log.e(TAG, "Serial CAT availability probe crashed", exception);
            return new PortAvailability(
                    PortAvailability.Stage.ERROR,
                    "USB 串口 CAT 可用性检查在完成前失败：" + safeMessage(exception)
            );
        }
    }

    public List<String> listDetectedPortHints() {
        try {
            UsbManager usbManager = usbManager();
            if (usbManager == null) {
                return java.util.Collections.emptyList();
            }
            CandidateResolution resolution = resolveCandidate(usbManager.getDeviceList(), null);
            return resolution.candidateHints();
        } catch (RuntimeException exception) {
            Log.e(TAG, "Serial CAT port enumeration crashed", exception);
            return java.util.Collections.emptyList();
        }
    }

    @Override
    public boolean requestPermission(String portHint, PendingIntent pendingIntent) {
        try {
            UsbManager usbManager = usbManager();
            if (usbManager == null || pendingIntent == null) {
                return false;
            }
            CandidateResolution resolution = resolveCandidate(usbManager.getDeviceList(), portHint);
            if (resolution.requiresExplicitPortHint()) {
                return false;
            }
            ProbedSerialCandidate candidate = resolution.candidate();
            if (candidate == null) {
                return false;
            }
            if (usbManager.hasPermission(candidate.device)) {
                return true;
            }
            usbManager.requestPermission(candidate.device, pendingIntent);
            return true;
        } catch (RuntimeException exception) {
            Log.e(TAG, "Serial CAT permission request crashed", exception);
            return false;
        }
    }

    @Override
    public SerialCatSession openSession(String portHint, int baudRate) throws IOException {
        try {
            if (appContext == null) {
                throw new IOException("串口 CAT 会话工厂缺少 Android Context。");
            }
            UsbManager usbManager = usbManager();
            if (usbManager == null) {
                throw new IOException("当前设备无法获取 USB 管理器。");
            }
            CandidateResolution resolution = resolveCandidate(usbManager.getDeviceList(), portHint);
            if (resolution.requiresExplicitPortHint()) {
                throw new IOException(resolution.renderExplicitPortHintMessage());
            }
            ProbedSerialCandidate candidate = resolution.candidate();
            if (candidate == null) {
                throw new IOException(portHint == null || portHint.trim().isEmpty()
                        ? "当前没有连接受支持的 USB 串口 CAT 设备。"
                        : "已指定的串口 CAT 设备当前未连接：" + portHint.trim());
            }
            if (!usbManager.hasPermission(candidate.device)) {
                throw new IOException("USB 串口 CAT 设备已连接，但应用尚未获得权限。");
            }
            UsbDeviceConnection connection = usbManager.openDevice(candidate.device);
            if (connection == null) {
                throw new IOException("USB 权限已存在，但打开设备失败。");
            }
            try {
                candidate.port.open(connection);
                candidate.port.setParameters(
                        Math.max(1200, baudRate),
                        UsbSerialPort.DATABITS_8,
                        UsbSerialPort.STOPBITS_1,
                        UsbSerialPort.PARITY_NONE
                );
                return new AndroidUsbProbedSerialCatSession(candidate.device, candidate.port, candidate.driverLabel);
            } catch (IOException | RuntimeException exception) {
                connection.close();
                if (exception instanceof IOException) {
                    throw (IOException) exception;
                }
                throw new IOException("USB 串口 CAT 会话打开失败：" + safeMessage(exception), exception);
            }
        } catch (RuntimeException exception) {
            Log.e(TAG, "Serial CAT session open crashed", exception);
            throw new IOException("USB 串口 CAT 会话在打开前异常中断："
                    + safeMessage(exception), exception);
        }
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().trim().isEmpty()) {
            return throwable == null ? "未知故障" : throwable.getClass().getSimpleName();
        }
        return throwable.getMessage().trim();
    }

    private UsbManager usbManager() {
        return appContext == null
                ? null
                : ContextCompat.getSystemService(appContext, UsbManager.class);
    }

    private CandidateResolution resolveCandidate(HashMap<String, UsbDevice> deviceList, String portHint) {
        if (deviceList == null || deviceList.isEmpty()) {
            return CandidateResolution.empty();
        }
        PortHintSelection hintSelection = PortHintSelection.parse(portHint);
        ProbedSerialCandidate firstCandidate = null;
        int candidateCount = 0;
        List<String> candidateHints = new java.util.ArrayList<>();
        List<UsbSerialDriver> drivers = usbManager() == null
                ? java.util.Collections.emptyList()
                : usbSerialProber().findAllDrivers(usbManager());
        for (UsbSerialDriver driver : drivers) {
            if (driver == null || driver.getPorts().isEmpty()) {
                continue;
            }
            UsbDevice device = driver.getDevice();
            for (UsbSerialPort port : driver.getPorts()) {
                ProbedSerialCandidate candidate = new ProbedSerialCandidate(device, driver, port);
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
                    return CandidateResolution.resolved(candidate, candidateCount, candidateHints);
                }
            }
        }
        if (hintSelection.hasDeviceHint()) {
            return CandidateResolution.resolved(null, candidateCount, candidateHints);
        }
        if (candidateCount > 1) {
            return CandidateResolution.requireExplicitPortHint(candidateHints);
        }
        return CandidateResolution.resolved(firstCandidate, candidateCount, candidateHints);
    }

    private UsbSerialProber usbSerialProber() {
        return UsbSerialProber.defaultProber();
    }

    private static final class ProbedSerialCandidate {
        private final UsbDevice device;
        private final UsbSerialDriver driver;
        private final UsbSerialPort port;
        private final String driverLabel;

        private ProbedSerialCandidate(UsbDevice device, UsbSerialDriver driver, UsbSerialPort port) {
            this.device = device;
            this.driver = driver;
            this.port = port;
            this.driverLabel = renderDriverLabel(driver);
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
        private final ProbedSerialCandidate candidate;
        private final boolean requiresExplicitPortHint;
        private final List<String> candidateHints;

        private CandidateResolution(
                ProbedSerialCandidate candidate,
                boolean requiresExplicitPortHint,
                List<String> candidateHints
        ) {
            this.candidate = candidate;
            this.requiresExplicitPortHint = requiresExplicitPortHint;
            this.candidateHints = candidateHints == null
                    ? java.util.Collections.emptyList()
                    : candidateHints;
        }

        private static CandidateResolution empty() {
            return new CandidateResolution(null, false, java.util.Collections.emptyList());
        }

        private static CandidateResolution resolved(
                ProbedSerialCandidate candidate,
                int candidateCount,
                List<String> candidateHints
        ) {
            return new CandidateResolution(
                    candidate,
                    false,
                    candidateCount <= 0 ? java.util.Collections.emptyList() : candidateHints
            );
        }

        private static CandidateResolution requireExplicitPortHint(List<String> candidateHints) {
            return new CandidateResolution(null, true, candidateHints);
        }

        private ProbedSerialCandidate candidate() {
            return candidate;
        }

        private boolean requiresExplicitPortHint() {
            return requiresExplicitPortHint;
        }

        private List<String> candidateHints() {
            return candidateHints;
        }

        private String renderExplicitPortHintMessage() {
            if (candidateHints.isEmpty()) {
                return "检测到多个 USB 串口 CAT 端口。请先明确选择一个串口，再申请权限或执行探测。";
            }
            return "检测到多个 USB 串口 CAT 端口。请先明确选择一个串口，再执行测试。候选项："
                    + String.join(", ", candidateHints);
        }
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
}
