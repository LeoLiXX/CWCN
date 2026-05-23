package org.bi9clt.cwcn.ui.rig;

import android.content.Context;

import androidx.annotation.Nullable;

import org.bi9clt.cwcn.R;
import org.bi9clt.cwcn.core.rig.CatProtocolFamily;
import org.bi9clt.cwcn.core.rig.KeyingPolarity;
import org.bi9clt.cwcn.core.rig.RigCapability;
import org.bi9clt.cwcn.core.rig.RigProfile;
import org.bi9clt.cwcn.core.rig.RigSupportLevel;
import org.bi9clt.cwcn.core.rig.RigTransport;
import org.bi9clt.cwcn.core.rig.SerialKeyerTxOutput;

import java.util.ArrayList;
import java.util.EnumSet;

public final class RigUiLabels {
    private RigUiLabels() {
    }

    public static String transportKind(Context context, @Nullable RigTransport.TransportKind kind) {
        if (context == null || kind == null) {
            return "-";
        }
        switch (kind) {
            case USB_SERIAL:
                return context.getString(R.string.rig_setup_transport_usb_serial);
            case BLUETOOTH_SERIAL:
                return context.getString(R.string.rig_setup_transport_bluetooth_serial);
            case NETWORK_CAT:
                return context.getString(R.string.rig_setup_transport_network_cat);
            case AUDIO_VOX:
                return context.getString(R.string.rig_setup_transport_audio_vox);
            default:
                return kind.name();
        }
    }

    public static String supportLevel(Context context, @Nullable RigSupportLevel level) {
        if (context == null || level == null) {
            return "-";
        }
        switch (level) {
            case DEBUG_ONLY:
                return context.getString(R.string.rig_support_level_debug_only);
            case BENCH_READY:
                return context.getString(R.string.rig_support_level_bench_ready);
            case PLANNED:
                return context.getString(R.string.rig_support_level_planned);
            default:
                return level.name();
        }
    }

    public static String catProtocolFamily(Context context, @Nullable CatProtocolFamily family) {
        if (context == null || family == null) {
            return "-";
        }
        switch (family) {
            case GENERIC:
                return context.getString(R.string.rig_cat_protocol_generic);
            case YAESU_STYLE:
                return context.getString(R.string.rig_cat_protocol_yaesu_style);
            case ICOM_CIV:
                return context.getString(R.string.rig_cat_protocol_icom_civ);
            case KENWOOD_STYLE:
                return context.getString(R.string.rig_cat_protocol_kenwood_style);
            case HAMLIB_RIGCTLD:
                return context.getString(R.string.rig_cat_protocol_hamlib_rigctld);
            default:
                return family.name();
        }
    }

    public static String keyingPolarity(Context context, @Nullable KeyingPolarity polarity) {
        if (context == null || polarity == null) {
            return "-";
        }
        switch (polarity) {
            case ACTIVE_HIGH:
                return context.getString(R.string.rig_keying_polarity_active_high);
            case ACTIVE_LOW:
                return context.getString(R.string.rig_keying_polarity_active_low);
            default:
                return polarity.name();
        }
    }

    public static String keyLine(Context context, @Nullable SerialKeyerTxOutput.KeyLine keyLine) {
        if (keyLine == null) {
            return "-";
        }
        switch (keyLine) {
            case RTS:
            case DTR:
            default:
                return keyLine.name();
        }
    }

    public static String capability(Context context, @Nullable RigCapability capability) {
        if (context == null || capability == null) {
            return "-";
        }
        switch (capability) {
            case TEXT_TO_CW:
                return context.getString(R.string.rig_capability_text_to_cw);
            case PTT_CONTROL:
                return context.getString(R.string.rig_capability_ptt_control);
            case KEY_LINE_CONTROL:
                return context.getString(R.string.rig_capability_key_line_control);
            case LIVE_PROFILE_UPDATE:
                return context.getString(R.string.rig_capability_live_profile_update);
            case AUDIO_VOX:
                return context.getString(R.string.rig_capability_audio_vox);
            case USB_DEVICE_SELECTION:
                return context.getString(R.string.rig_capability_usb_device_selection);
            case SERIAL_CAT:
                return context.getString(R.string.rig_capability_serial_cat);
            case NETWORK_CAT:
                return context.getString(R.string.rig_capability_network_cat);
            case BLUETOOTH_SERIAL:
                return context.getString(R.string.rig_capability_bluetooth_serial);
            case FREQUENCY_READ:
                return context.getString(R.string.rig_capability_frequency_read);
            case FREQUENCY_SET:
                return context.getString(R.string.rig_capability_frequency_set);
            case MODE_READ:
                return context.getString(R.string.rig_capability_mode_read);
            case MODE_SET:
                return context.getString(R.string.rig_capability_mode_set);
            default:
                return capability.name();
        }
    }

    public static String capabilitySummary(Context context, @Nullable RigProfile profile) {
        if (context == null || profile == null) {
            return "";
        }
        EnumSet<RigCapability> capabilities = profile.capabilities();
        if (capabilities.isEmpty()) {
            return context.getString(R.string.rig_capability_none);
        }
        ArrayList<String> labels = new ArrayList<>();
        for (RigCapability capability : capabilities) {
            labels.add(capability(context, capability));
        }
        return String.join(", ", labels);
    }
}
