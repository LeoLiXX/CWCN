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
                new UsbSerialKeyerRigControlAdapter(
                        new AndroidUsbSerialKeyerPortFactory(appContext),
                        SerialKeyerTxOutput.KeyLine.RTS,
                        18,
                        650
                ),
                new PlaceholderAdapter(
                        "generic-cat",
                        "Generic CAT / PTT Adapter",
                        "Reserved for CAT frequency/PTT/keying integration.",
                        false,
                        true
                ),
                new PlaceholderAdapter(
                        "generic-text-to-cw",
                        "Generic Text-to-CW Adapter",
                        "Reserved for external text-to-CW backends such as RTS/DTR or a hardware keyer.",
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
                return "Device does not expose USB host capability.";
            }
            if (deviceCount > 0) {
                return "USB host available, detected " + deviceCount + " attached USB device(s).";
            }
            return "USB host available, but no attached device is visible yet.";
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
                return "Device does not expose Bluetooth capability.";
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                return "Bluetooth exists, but BLUETOOTH_CONNECT permission is still missing.";
            }

            BluetoothManager manager = ContextCompat.getSystemService(context, BluetoothManager.class);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            if (adapter == null) {
                return "Bluetooth service is unavailable.";
            }
            return adapter.isEnabled()
                    ? "Bluetooth adapter is available and enabled."
                    : "Bluetooth adapter exists but is currently disabled.";
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
            return "Software route reserved for later TCP/UDP/CAT integration.";
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
            return "Compatibility route reserved for later audio-output plus VOX integration.";
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
            return "Adapter contract is present, but no real rig implementation is attached yet.";
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
