package org.bi9clt.cwcn.core.rig;

import android.app.PendingIntent;

import org.bi9clt.cwcn.core.tx.CwTxEngine;
import org.bi9clt.cwcn.core.tx.CwTxPlaybackSnapshot;
import org.bi9clt.cwcn.core.tx.CwTxPlan;
import org.bi9clt.cwcn.core.tx.CwTxRunner;
import org.bi9clt.cwcn.core.tx.CwTxState;

import java.util.List;

public final class UsbSerialKeyerRigControlAdapter implements RigControlAdapter {
    private static final int DEFAULT_WPM = 18;
    private static final int DEFAULT_TONE_FREQUENCY_HZ = 650;

    private final SerialKeyerPortFactory portFactory;
    private final CwTxEngine txEngine;
    private final String adapterId;
    private final String adapterDisplayName;
    private final String capabilityTemplate;

    private volatile SerialKeyerTxOutput.KeyLine keyLine;
    private volatile int wpm;
    private volatile int toneFrequencyHz;
    private volatile CwTxPlaybackSnapshot lastSnapshot;
    private volatile SerialKeyerPort openPort;
    private volatile CwTxRunner txRunner;

    UsbSerialKeyerRigControlAdapter(
            SerialKeyerPortFactory portFactory,
            SerialKeyerTxOutput.KeyLine keyLine,
            int wpm,
            int toneFrequencyHz
    ) {
        this(
                "usb-serial-keyer",
                "USB 串口键控适配器",
                "按 CW 时序切换 %s 控制线，驱动简单串口键控。",
                portFactory,
                keyLine,
                wpm,
                toneFrequencyHz
        );
    }

    UsbSerialKeyerRigControlAdapter(
            String adapterId,
            String adapterDisplayName,
            String capabilityTemplate,
            SerialKeyerPortFactory portFactory,
            SerialKeyerTxOutput.KeyLine keyLine,
            int wpm,
            int toneFrequencyHz
    ) {
        this.adapterId = adapterId == null ? "usb-serial-keyer" : adapterId;
        this.adapterDisplayName = adapterDisplayName == null ? "USB 串口键控适配器" : adapterDisplayName;
        this.capabilityTemplate = capabilityTemplate == null
                ? "按 CW 时序切换 %s 控制线，驱动简单串口键控。"
                : capabilityTemplate;
        this.portFactory = portFactory;
        this.keyLine = keyLine;
        this.txEngine = new CwTxEngine();
        this.wpm = Math.max(5, wpm);
        this.toneFrequencyHz = Math.max(200, toneFrequencyHz);
    }

    @Override
    public String id() {
        return adapterId;
    }

    @Override
    public String displayName() {
        return adapterDisplayName;
    }

    @Override
    public String describeCapabilities() {
        return String.format(capabilityTemplate, keyLine);
    }

    @Override
    public String describeAvailability() {
        SerialKeyerPort port = openPort;
        if (port != null && port.isOpen()) {
            return port.describeAvailability();
        }
        if (port != null && !port.isOpen() && !port.retainsDiagnosticDetails()) {
            openPort = null;
        }
        if (port != null && port.retainsDiagnosticDetails()) {
            return port.describeAvailability();
        }
        return portFactory.describeAvailability();
    }

    @Override
    public boolean supportsTextToCw() {
        return true;
    }

    @Override
    public boolean supportsPttControl() {
        return false;
    }

    @Override
    public boolean isReady() {
        SerialKeyerPort port = openPort;
        return (port != null && port.isOpen()) || portFactory.canOpenPort();
    }

    @Override
    public boolean supportsConfigurableTextToCwProfile() {
        return true;
    }

    @Override
    public boolean configureTextToCwProfile(int wpm, int toneFrequencyHz) {
        this.wpm = Math.max(5, wpm);
        this.toneFrequencyHz = Math.max(200, toneFrequencyHz);
        return true;
    }

    @Override
    public boolean usesWpmForTextToCwProfile() {
        return true;
    }

    @Override
    public boolean usesToneFrequencyForTextToCwProfile() {
        return false;
    }

    @Override
    public boolean keyDown() {
        SerialKeyerPort port = ensureOpenPort();
        if (port == null || !port.isOpen()) {
            return false;
        }
        if (keyLine == SerialKeyerTxOutput.KeyLine.RTS) {
            return port.setRts(true);
        }
        return port.setDtr(true);
    }

    @Override
    public boolean keyUp() {
        if (txRunner != null) {
            txRunner.stop();
        }
        SerialKeyerPort port = openPort;
        if (port == null || !port.isOpen()) {
            return false;
        }
        if (keyLine == SerialKeyerTxOutput.KeyLine.RTS) {
            return port.setRts(false);
        }
        return port.setDtr(false);
    }

    @Override
    public boolean sendText(String text) {
        SerialKeyerPort port = ensureOpenPort();
        if (port == null || !port.isOpen()) {
            return false;
        }
        if (txRunner != null && txRunner.isRunning()) {
            return false;
        }
        CwTxPlan plan = txEngine.buildPlan(text, wpm, toneFrequencyHz);
        if (plan.elements().isEmpty()) {
            return false;
        }
        lastSnapshot = null;
        txRunner = new CwTxRunner(new SerialKeyerTxOutput(port, keyLine));
        txRunner.runPlanBlocking(plan, snapshot -> lastSnapshot = snapshot);
        return lastSnapshot != null && lastSnapshot.state() == CwTxState.COMPLETED;
    }

    CwTxPlaybackSnapshot lastSnapshot() {
        return lastSnapshot;
    }

    @Override
    public CwTxPlaybackSnapshot currentTxPlaybackSnapshot() {
        return lastSnapshot;
    }

    public SerialKeyerTxOutput.KeyLine keyLine() {
        return keyLine;
    }

    public boolean setKeyLine(SerialKeyerTxOutput.KeyLine keyLine) {
        if (keyLine == null) {
            return false;
        }
        CwTxRunner runner = txRunner;
        if (runner != null && runner.isRunning()) {
            return false;
        }
        keyUp();
        this.keyLine = keyLine;
        return true;
    }

    public String describeMatchedDevice() {
        if (portFactory instanceof UsbSerialRouteFactory) {
            return ((UsbSerialRouteFactory) portFactory).describeMatchedDevice();
        }
        return "当前环境无法提供 USB 设备详情。";
    }

    public boolean requestUsbPermission(PendingIntent pendingIntent) {
        if (portFactory instanceof UsbSerialRouteFactory) {
            return ((UsbSerialRouteFactory) portFactory).requestPermission(pendingIntent);
        }
        return false;
    }

    public List<UsbSerialDeviceOption> availableDevices() {
        if (portFactory instanceof SelectableSerialKeyerPortFactory) {
            return ((SelectableSerialKeyerPortFactory) portFactory).availableDevices();
        }
        return java.util.Collections.emptyList();
    }

    public String preferredDeviceName() {
        if (portFactory instanceof SelectableSerialKeyerPortFactory) {
            return ((SelectableSerialKeyerPortFactory) portFactory).preferredDeviceName();
        }
        return null;
    }

    public boolean hasPreferredDeviceSelection() {
        if (portFactory instanceof UsbSerialRouteFactory) {
            return ((UsbSerialRouteFactory) portFactory).hasPreferredDeviceSelection();
        }
        return false;
    }

    public boolean hasAnyCandidateDevice() {
        if (portFactory instanceof UsbSerialRouteFactory) {
            return ((UsbSerialRouteFactory) portFactory).hasAnyCandidateDevice();
        }
        return !availableDevices().isEmpty();
    }

    public boolean hasTargetDevice() {
        if (portFactory instanceof UsbSerialRouteFactory) {
            return ((UsbSerialRouteFactory) portFactory).hasTargetDevice();
        }
        return hasAnyCandidateDevice();
    }

    public boolean isPreferredDeviceMissing() {
        if (portFactory instanceof UsbSerialRouteFactory) {
            return ((UsbSerialRouteFactory) portFactory).isPreferredDeviceMissing();
        }
        return false;
    }

    public boolean supportsMockBenchScenarios() {
        return portFactory instanceof MockUsbSerialBenchFactory;
    }

    public java.util.List<MockUsbSerialBenchScenario> availableMockBenchScenarios() {
        if (portFactory instanceof MockUsbSerialBenchFactory) {
            return ((MockUsbSerialBenchFactory) portFactory).availableBenchScenarios();
        }
        return java.util.Collections.emptyList();
    }

    public MockUsbSerialBenchScenario selectedMockBenchScenario() {
        if (portFactory instanceof MockUsbSerialBenchFactory) {
            return ((MockUsbSerialBenchFactory) portFactory).selectedBenchScenario();
        }
        return null;
    }

    public boolean selectMockBenchScenario(MockUsbSerialBenchScenario scenario) {
        if (portFactory instanceof MockUsbSerialBenchFactory) {
            refreshRouteState();
            return ((MockUsbSerialBenchFactory) portFactory).selectBenchScenario(scenario);
        }
        return false;
    }

    public boolean selectDevice(String deviceName) {
        if (portFactory instanceof SelectableSerialKeyerPortFactory) {
            refreshRouteState();
            return ((SelectableSerialKeyerPortFactory) portFactory).selectDevice(deviceName);
        }
        return false;
    }

    public void refreshRouteState() {
        CwTxRunner runner = txRunner;
        if (runner != null && runner.isRunning()) {
            runner.stop();
        }
        SerialKeyerPort port = openPort;
        if (port != null) {
            port.close();
        }
        openPort = null;
    }

    public String diagnosticStageCode() {
        SerialKeyerPort port = openPort;
        if (port != null && port.isOpen()) {
            return port.diagnosticCode();
        }
        if (port != null && port.retainsDiagnosticDetails()) {
            return port.diagnosticCode();
        }
        return portFactory.diagnosticStageCode();
    }

    public String diagnosticStageLabel() {
        return diagnosticStageLabel(diagnosticStageCode());
    }

    public static String diagnosticStageLabel(String diagnosticCode) {
        if ("usb-serial-ready".equals(diagnosticCode)) {
            return "已就绪";
        }
        if ("usb-serial-target-missing".equals(diagnosticCode)) {
            return "锁定目标缺失";
        }
        if ("usb-serial-no-device".equals(diagnosticCode)) {
            return "没有 USB 设备";
        }
        if ("usb-serial-no-cdc".equals(diagnosticCode)) {
            return "没有 CDC/ACM 键控设备";
        }
        if ("usb-serial-no-permission".equals(diagnosticCode)) {
            return "缺少权限";
        }
        if ("usb-serial-open-failed".equals(diagnosticCode)) {
            return "打开失败";
        }
        if ("usb-serial-claim-failed".equals(diagnosticCode)) {
            return "接口声明失败";
        }
        if ("usb-serial-no-control-interface".equals(diagnosticCode)) {
            return "控制接口缺失";
        }
        if ("usb-serial-no-context".equals(diagnosticCode)) {
            return "缺少 Android Context";
        }
        if ("usb-serial-no-manager".equals(diagnosticCode)) {
            return "USB 管理器不可用";
        }
        return "不可用";
    }

    private SerialKeyerPort ensureOpenPort() {
        SerialKeyerPort port = openPort;
        if (port != null && port.isOpen()) {
            return port;
        }
        SerialKeyerPort opened = portFactory.openPort();
        openPort = opened;
        return opened != null && opened.isOpen() ? opened : null;
    }
}
