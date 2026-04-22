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

    public static List<RigTransport> defaultTransports() {
        return Arrays.asList(
                new UsbHostTransport(),
                new BluetoothSerialTransport(),
                new NetworkCatTransport(),
                new VoxTransport()
        );
    }

    public static List<RigControlAdapter> defaultAdapters() {
        return Arrays.asList(
                new PlaceholderAdapter(
                        "generic-cat",
                        "Generic CAT / PTT Adapter",
                        "预留给 CAT 频率读取、PTT 与基础电键控制。",
                        false,
                        true
                ),
                new PlaceholderAdapter(
                        "generic-text-to-cw",
                        "Generic Text-to-CW Adapter",
                        "预留给文本驱动发射后端，后续可落到 RTS / DTR / 外部 keyer。",
                        true,
                        true
                )
        );
    }

    private static final class UsbHostTransport implements RigTransport {
        @Override
        public String id() {
            return "usb-host";
        }

        @Override
        public String displayName() {
            return "USB Host / Serial";
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
                return "设备未声明 USB Host 能力";
            }
            if (deviceCount > 0) {
                return "USB Host 可用，检测到 " + deviceCount + " 个 USB 设备";
            }
            return "USB Host 可用，当前未检测到接入设备";
        }
    }

    private static final class BluetoothSerialTransport implements RigTransport {
        @Override
        public String id() {
            return "bluetooth-serial";
        }

        @Override
        public String displayName() {
            return "Bluetooth Serial";
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
                return "设备未声明蓝牙能力";
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                return "蓝牙能力存在，但尚未授予 BLUETOOTH_CONNECT";
            }

            BluetoothManager manager = ContextCompat.getSystemService(context, BluetoothManager.class);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            if (adapter == null) {
                return "蓝牙服务不可用";
            }
            return adapter.isEnabled() ? "蓝牙适配器可用且已开启" : "蓝牙适配器存在，但当前关闭";
        }
    }

    private static final class NetworkCatTransport implements RigTransport {
        @Override
        public String id() {
            return "network-cat";
        }

        @Override
        public String displayName() {
            return "Network CAT";
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
            return "软件层已预留，后续接入 TCP/UDP/CAT 协议";
        }
    }

    private static final class VoxTransport implements RigTransport {
        @Override
        public String id() {
            return "audio-vox";
        }

        @Override
        public String displayName() {
            return "Audio VOX";
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
            return "兼容路径已保留，后续与音频输出链路一起接入";
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
        public boolean supportsTextToCw() {
            return supportsTextToCw;
        }

        @Override
        public boolean supportsPttControl() {
            return supportsPttControl;
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
