package org.bi9clt.cwcn.core.rig;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import java.util.Arrays;
import java.util.List;

public final class RigRegistry {
    private RigRegistry() {
    }

    public static List<RigControlAdapter> defaultAdapters(Context context) {
        Context appContext = context == null ? null : context.getApplicationContext();
        return Arrays.asList(
                new AudioVoxRigControlAdapter(),
                new HamlibRigctldRigControlAdapter(appContext),
                new UsbSerialKeyerRigControlAdapter(
                        new AndroidUsbSerialKeyerPortFactory(appContext),
                        SerialKeyerTxOutput.KeyLine.RTS,
                        18,
                        650
                ),
                new UsbSerialKeyerRigControlAdapter(
                        "usb-serial-keyer-mock",
                        "模拟 USB 串口键控适配器",
                        "模拟 USB 串口键控路径，在没有外接硬件时切换 %s 控制线。",
                        new MockUsbSerialKeyerPortFactory(),
                        SerialKeyerTxOutput.KeyLine.RTS,
                        18,
                        650
                ),
                new SerialCatRigControlAdapter(appContext),
                new PlaceholderAdapter(
                        "generic-text-to-cw",
                        "通用文本转 CW 适配器",
                        "为外部文本转 CW 后端预留，例如 RTS/DTR 或硬件键控器。",
                        true,
                        true
                )
        );
    }

    public static List<RigTransport> defaultTransports() {
        return Arrays.asList(
                new UsbHostTransport(),
                new BluetoothSerialTransport(),
                new NetworkCatTransport(),
                new VoxTransport()
        );
    }

    public static List<RigProfile> defaultProfiles() {
        return RigProfileCatalog.defaultProfiles();
    }

    public static List<RigControlAdapter> defaultAdapters() {
        return defaultAdapters(null);
    }

    private static final class UsbHostTransport implements RigTransport {
        @Override
        public String id() {
            return "usb-host";
        }

        @Override
        public String displayName() {
            return "USB 主机 / 串口";
        }

        @Override
        public TransportKind kind() {
            return TransportKind.USB_SERIAL;
        }

        @Override
        public boolean isReady(Context context) {
            return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST);
        }

        @Override
        public String describeAvailability(Context context) {
            UsbManager manager = ContextCompat.getSystemService(context, UsbManager.class);
            int deviceCount = manager == null ? 0 : manager.getDeviceList().size();
            if (!isReady(context)) {
                return "当前设备不具备 USB Host 能力。";
            }
            if (deviceCount > 0) {
                return "USB 主机可用，已检测到 " + deviceCount + " 个连接中的 USB 设备。";
            }
            return "USB 主机可用，但当前还没有看到已连接设备。";
        }
    }

    private static final class BluetoothSerialTransport implements RigTransport {
        @Override
        public String id() {
            return "bluetooth-serial";
        }

        @Override
        public String displayName() {
            return "蓝牙串口";
        }

        @Override
        public TransportKind kind() {
            return TransportKind.BLUETOOTH_SERIAL;
        }

        @Override
        public boolean isReady(Context context) {
            return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);
        }

        @Override
        public String describeAvailability(Context context) {
            if (!isReady(context)) {
                return "当前设备不具备蓝牙能力。";
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                return "设备具备蓝牙能力，但还缺少 BLUETOOTH_CONNECT 权限。";
            }

            BluetoothManager manager = ContextCompat.getSystemService(context, BluetoothManager.class);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            if (adapter == null) {
                return "当前无法获取蓝牙服务。";
            }
            return adapter.isEnabled()
                    ? "蓝牙适配器可用，且已经开启。"
                    : "蓝牙适配器存在，但当前处于关闭状态。";
        }
    }

    private static final class NetworkCatTransport implements RigTransport {
        @Override
        public String id() {
            return "network-cat";
        }

        @Override
        public String displayName() {
            return "网络 CAT";
        }

        @Override
        public TransportKind kind() {
            return TransportKind.NETWORK_CAT;
        }

        @Override
        public boolean isReady(Context context) {
            return true;
        }

        @Override
        public String describeAvailability(Context context) {
            return "这是为后续 TCP/UDP/CAT 集成预留的软件链路。";
        }
    }

    private static final class VoxTransport implements RigTransport {
        @Override
        public String id() {
            return "audio-vox";
        }

        @Override
        public String displayName() {
            return "音频 VOX";
        }

        @Override
        public TransportKind kind() {
            return TransportKind.AUDIO_VOX;
        }

        @Override
        public boolean isReady(Context context) {
            return true;
        }

        @Override
        public String describeAvailability(Context context) {
            return "这是为后续音频输出加 VOX 联动预留的兼容链路。";
        }
    }

    private static final class PlaceholderAdapter implements RigControlAdapter {
        private final String id;
        private final String displayName;
        private final String capabilitySummary;
        private final boolean supportsTextToCw;
        private final boolean supportsPttControl;

        private PlaceholderAdapter(
                String id,
                String displayName,
                String capabilitySummary,
                boolean supportsTextToCw,
                boolean supportsPttControl
        ) {
            this.id = id;
            this.displayName = displayName;
            this.capabilitySummary = capabilitySummary;
            this.supportsTextToCw = supportsTextToCw;
            this.supportsPttControl = supportsPttControl;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String displayName() {
            return displayName;
        }

        @Override
        public String describeCapabilities() {
            return capabilitySummary;
        }

        @Override
        public String describeAvailability() {
            return "适配器接口已经预留，但当前还没有接入真正的电台实现。";
        }

        @Override
        public boolean supportsTextToCw() {
            return supportsTextToCw;
        }

        @Override
        public boolean supportsPttControl() {
            return supportsPttControl;
        }

        @Override
        public boolean isReady() {
            return false;
        }

        @Override
        public boolean supportsConfigurableTextToCwProfile() {
            return false;
        }

        @Override
        public boolean keyDown() {
            return false;
        }

        @Override
        public boolean keyUp() {
            return false;
        }

        @Override
        public boolean sendText(String text) {
            return false;
        }
    }
}
