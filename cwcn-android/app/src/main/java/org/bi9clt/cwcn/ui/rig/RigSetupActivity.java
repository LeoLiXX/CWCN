package org.bi9clt.cwcn.ui.rig;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.bi9clt.cwcn.BuildConfig;
import org.bi9clt.cwcn.core.rig.AndroidUsbSerialCatSessionFactory;
import org.bi9clt.cwcn.core.rig.CatProtocolFamily;
import org.bi9clt.cwcn.core.rig.HamlibRigctldRigControlAdapter;
import org.bi9clt.cwcn.core.rig.RigCapability;
import org.bi9clt.cwcn.core.rig.RigProfileConfigurationFormatter;
import org.bi9clt.cwcn.core.rig.RigProfile;
import org.bi9clt.cwcn.core.rig.RigProfileSettings;
import org.bi9clt.cwcn.core.rig.RigRegistry;
import org.bi9clt.cwcn.core.rig.RigSelectionStore;
import org.bi9clt.cwcn.core.rig.SerialKeyerTxOutput;
import org.bi9clt.cwcn.core.rig.SerialCatProbe;
import org.bi9clt.cwcn.core.rig.RigSupportLevel;
import org.bi9clt.cwcn.core.rig.RigTransport;
import org.bi9clt.cwcn.databinding.ActivityRigSetupBinding;
import org.bi9clt.cwcn.ui.debug.InputDebugActivity;
import org.bi9clt.cwcn.ui.tx.TxActivity;

import java.util.List;

public final class RigSetupActivity extends AppCompatActivity {
    private static final String ACTION_SERIAL_CAT_USB_PERMISSION = "org.bi9clt.cwcn.action.SERIAL_CAT_USB_PERMISSION";

    private ActivityRigSetupBinding binding;
    private RigSelectionStore rigSelectionStore;
    private boolean syncingProfileSelection;
    private String configStatusMessage = "";
    private String configStatusProfileId;
    private String serialProbeMessage = "";
    private String serialProbeProfileId;
    private boolean serialProbeInFlight;
    private String networkProbeMessage = "";
    private String networkProbeProfileId;
    private boolean networkProbeInFlight;
    private BroadcastReceiver usbPermissionReceiver;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRigSetupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        rigSelectionStore = new RigSelectionStore(this);
        binding.versionText.setText("Rig Setup " + BuildConfig.VERSION_NAME);
        registerUsbPermissionReceiver();
        setupProfileSelector();
        setupActions();
        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUi();
    }

    @Override
    protected void onDestroy() {
        if (usbPermissionReceiver != null) {
            unregisterReceiver(usbPermissionReceiver);
            usbPermissionReceiver = null;
        }
        super.onDestroy();
    }

    private void setupActions() {
        binding.backButton.setOnClickListener(view -> finish());
        binding.openTxButton.setOnClickListener(view ->
                startActivity(new Intent(this, TxActivity.class)));
        binding.openDebugButton.setOnClickListener(view ->
                startActivity(new Intent(this, InputDebugActivity.class)));
        binding.saveSelectedProfileButton.setOnClickListener(view -> saveSelectedProfile());
        binding.saveProfileConfigButton.setOnClickListener(view -> saveProfileConfig());
        binding.resetProfileConfigButton.setOnClickListener(view -> resetProfileConfig());
        binding.requestSerialCatPermissionButton.setOnClickListener(view -> requestSerialCatPermission());
        binding.testSerialCatConnectionButton.setOnClickListener(view -> testSerialCatConnection());
        binding.testNetworkCatConnectionButton.setOnClickListener(view -> testNetworkCatConnection());
    }

    private void setupProfileSelector() {
        ArrayAdapter<RigProfile> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                RigRegistry.defaultProfiles()
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.profileSpinner.setAdapter(adapter);
        binding.profileSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!syncingProfileSelection) {
                    refreshSelectedProfileViews();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                refreshSelectedProfileViews();
            }
        });

        ArrayAdapter<SerialKeyerTxOutput.KeyLine> keyLineAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                SerialKeyerTxOutput.KeyLine.values()
        );
        keyLineAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.usbKeyLineSpinner.setAdapter(keyLineAdapter);

        ArrayAdapter<CatProtocolFamily> catProtocolAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                CatProtocolFamily.values()
        );
        catProtocolAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.serialCatProtocolSpinner.setAdapter(catProtocolAdapter);
        binding.networkCatProtocolSpinner.setAdapter(catProtocolAdapter);
    }

    private void refreshUi() {
        List<RigTransport> transports = RigRegistry.defaultTransports();
        List<RigProfile> profiles = RigRegistry.defaultProfiles();
        binding.readinessSummaryText.setText(renderReadinessSummary(transports, profiles));
        syncSelectedProfile(profiles);
        refreshSelectedProfileViews();
        binding.transportSummaryText.setText(renderTransportSummary(transports));
        binding.profileSummaryText.setText(renderProfileSummary(profiles));
        binding.nextStepText.setText(renderNextStep(profiles));
    }

    private void syncSelectedProfile(List<RigProfile> profiles) {
        RigProfile selectedProfile = rigSelectionStore.selectedProfile();
        syncingProfileSelection = true;
        if (selectedProfile == null) {
            binding.profileSpinner.setSelection(0);
            syncingProfileSelection = false;
            return;
        }
        for (int index = 0; index < profiles.size(); index++) {
            if (profiles.get(index).id().equals(selectedProfile.id())) {
                binding.profileSpinner.setSelection(index);
                syncingProfileSelection = false;
                return;
            }
        }
        binding.profileSpinner.setSelection(0);
        syncingProfileSelection = false;
    }

    private void saveSelectedProfile() {
        RigProfile profile = selectedProfile();
        if (profile == null) {
            return;
        }
        rigSelectionStore.saveSelectedProfileId(profile.id());
        configStatusMessage = "Pinned " + profile.displayName() + " as the primary rig path.";
        configStatusProfileId = profile.id();
        refreshUi();
    }

    private void saveProfileConfig() {
        RigProfile profile = selectedProfile();
        RigProfileSettings settings = readSettingsFromEditor();
        rigSelectionStore.saveSettings(profile, settings);
        configStatusMessage = profile == null
                ? "Saved rig configuration defaults."
                : "Saved configuration for " + profile.displayName() + ".";
        configStatusProfileId = profile == null ? null : profile.id();
        refreshSelectedProfileViews();
    }

    private void resetProfileConfig() {
        RigProfile profile = selectedProfile();
        if (profile == null) {
            return;
        }
        rigSelectionStore.clearSettings(profile);
        configStatusMessage = "Restored recommended defaults for " + profile.displayName() + ".";
        configStatusProfileId = profile.id();
        refreshSelectedProfileViews();
    }

    private void testNetworkCatConnection() {
        RigProfile profile = selectedProfile();
        RigProfileSettings settings = readSettingsFromEditor();
        networkProbeInFlight = true;
        networkProbeProfileId = profile == null ? null : profile.id();
        networkProbeMessage = "Testing rigctld connection...";
        refreshSelectedProfileViews();
        new Thread(() -> {
            HamlibRigctldRigControlAdapter.ProbeResult result =
                    HamlibRigctldRigControlAdapter.probeConfiguration(profile, settings);
            runOnUiThread(() -> {
                networkProbeInFlight = false;
                networkProbeProfileId = profile == null ? null : profile.id();
                networkProbeMessage = result.message();
                refreshSelectedProfileViews();
            });
        }, "cwcn-rigctld-probe").start();
    }

    private void requestSerialCatPermission() {
        RigProfile profile = selectedProfile();
        RigProfileSettings settings = readSettingsFromEditor();
        if (profile == null || !profile.hasCapability(RigCapability.SERIAL_CAT)) {
            return;
        }
        AndroidUsbSerialCatSessionFactory factory = new AndroidUsbSerialCatSessionFactory(this);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                new Intent(ACTION_SERIAL_CAT_USB_PERMISSION).setPackage(getPackageName()),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT
        );
        boolean requested = factory.requestPermission(settings.serialCatPortHint(), pendingIntent);
        serialProbeProfileId = profile.id();
        serialProbeMessage = requested
                ? "USB serial CAT permission request sent for the current target device."
                : "USB serial CAT permission could not be requested. Check whether a CDC/ACM device is attached.";
        refreshSelectedProfileViews();
    }

    private void testSerialCatConnection() {
        RigProfile profile = selectedProfile();
        RigProfileSettings settings = readSettingsFromEditor();
        serialProbeInFlight = true;
        serialProbeProfileId = profile == null ? null : profile.id();
        serialProbeMessage = "Testing serial CAT connection...";
        refreshSelectedProfileViews();
        new Thread(() -> {
            SerialCatProbe.ProbeResult result =
                    SerialCatProbe.probeConfiguration(
                            profile,
                            settings,
                            new AndroidUsbSerialCatSessionFactory(this)
                    );
            runOnUiThread(() -> {
                serialProbeInFlight = false;
                serialProbeProfileId = profile == null ? null : profile.id();
                serialProbeMessage = result.message();
                refreshSelectedProfileViews();
            });
        }, "cwcn-serial-cat-probe").start();
    }

    private RigProfile selectedProfile() {
        Object selectedItem = binding.profileSpinner.getSelectedItem();
        if (selectedItem instanceof RigProfile) {
            return (RigProfile) selectedItem;
        }
        return null;
    }

    private void refreshSelectedProfileViews() {
        RigProfile profile = selectedProfile();
        RigProfile pinnedProfile = rigSelectionStore.selectedProfile();
        RigProfileSettings settings = rigSelectionStore.loadSettings(profile);
        boolean hasSavedOverride = rigSelectionStore.hasSavedSettings(profile);
        syncSettingsEditor(settings);
        updateConfigVisibility(profile);
        binding.resetProfileConfigButton.setEnabled(profile != null && hasSavedOverride);
        syncSerialProbeState(profile, settings);
        syncNetworkProbeState(profile, settings);
        binding.selectedProfileStatusText.setText(renderSelectedProfileStatus(profile, pinnedProfile, settings, hasSavedOverride));
        binding.profileConfigStatusText.setText(renderProfileConfigStatus(profile, settings, hasSavedOverride));
    }

    private void syncSettingsEditor(RigProfileSettings settings) {
        binding.defaultWpmEditText.setText(String.valueOf(settings.defaultWpm()));
        binding.defaultToneFrequencyEditText.setText(String.valueOf(settings.defaultToneFrequencyHz()));
        binding.usbKeyLineSpinner.setSelection(settings.usbKeyLine().ordinal());
        binding.usbPreferredDeviceNameEditText.setText(valueOrEmpty(settings.usbPreferredDeviceName()));
        binding.serialCatProtocolSpinner.setSelection(settings.serialCatProtocolFamily().ordinal());
        binding.serialCatBaudRateEditText.setText(String.valueOf(settings.serialCatBaudRate()));
        binding.serialCatPortHintEditText.setText(valueOrEmpty(settings.serialCatPortHint()));
        binding.serialCatCivAddressEditText.setText(valueOrEmpty(settings.serialCatCivAddressHex()));
        binding.networkCatProtocolSpinner.setSelection(settings.networkCatProtocolFamily().ordinal());
        binding.networkHostEditText.setText(valueOrEmpty(settings.networkHost()));
        binding.networkPortEditText.setText(String.valueOf(settings.networkPort()));
        binding.bluetoothDeviceHintEditText.setText(valueOrEmpty(settings.bluetoothDeviceHint()));
    }

    private void updateConfigVisibility(RigProfile profile) {
        boolean usbVisible = profile != null && profile.hasCapability(RigCapability.KEY_LINE_CONTROL);
        boolean serialCatVisible = profile != null && profile.hasCapability(RigCapability.SERIAL_CAT);
        boolean networkCatVisible = profile != null && profile.hasCapability(RigCapability.NETWORK_CAT);
        boolean bluetoothVisible = profile != null && profile.hasCapability(RigCapability.BLUETOOTH_SERIAL);
        boolean audioVoxVisible = profile != null && profile.hasCapability(RigCapability.AUDIO_VOX);

        binding.usbConfigGroup.setVisibility(usbVisible ? View.VISIBLE : View.GONE);
        binding.serialCatConfigGroup.setVisibility(serialCatVisible ? View.VISIBLE : View.GONE);
        binding.networkCatConfigGroup.setVisibility(networkCatVisible ? View.VISIBLE : View.GONE);
        binding.bluetoothConfigGroup.setVisibility(bluetoothVisible ? View.VISIBLE : View.GONE);
        binding.audioVoxConfigHintText.setVisibility(audioVoxVisible ? View.VISIBLE : View.GONE);
    }

    private void syncNetworkProbeState(RigProfile profile, RigProfileSettings settings) {
        boolean networkCatVisible = profile != null && profile.hasCapability(RigCapability.NETWORK_CAT);
        binding.testNetworkCatConnectionButton.setVisibility(networkCatVisible ? View.VISIBLE : View.GONE);
        binding.networkCatProbeStatusText.setVisibility(networkCatVisible ? View.VISIBLE : View.GONE);
        if (!networkCatVisible) {
            return;
        }
        boolean hamlibSelected = settings.networkCatProtocolFamily() == CatProtocolFamily.HAMLIB_RIGCTLD;
        binding.testNetworkCatConnectionButton.setEnabled(hamlibSelected && !networkProbeInFlight);
        if (networkProbeInFlight
                && profile != null
                && profile.id().equals(networkProbeProfileId)) {
            binding.networkCatProbeStatusText.setText(networkProbeMessage);
            return;
        }
        if (networkProbeMessage != null
                && !networkProbeMessage.isEmpty()
                && profile != null
                && profile.id().equals(networkProbeProfileId)) {
            binding.networkCatProbeStatusText.setText(networkProbeMessage);
            return;
        }
        binding.networkCatProbeStatusText.setText(hamlibSelected
                ? "Probe host/port reachability and ask rigctld for its rig info without starting TX."
                : "Connection probe is currently available only when the network CAT family is set to Hamlib rigctld.");
    }

    private void syncSerialProbeState(RigProfile profile, RigProfileSettings settings) {
        boolean serialCatVisible = profile != null && profile.hasCapability(RigCapability.SERIAL_CAT);
        binding.requestSerialCatPermissionButton.setVisibility(serialCatVisible ? View.VISIBLE : View.GONE);
        binding.testSerialCatConnectionButton.setVisibility(serialCatVisible ? View.VISIBLE : View.GONE);
        binding.serialCatProbeStatusText.setVisibility(serialCatVisible ? View.VISIBLE : View.GONE);
        if (!serialCatVisible) {
            return;
        }
        boolean yaesuSelected = settings.serialCatProtocolFamily() == CatProtocolFamily.YAESU_STYLE;
        boolean icomSelected = settings.serialCatProtocolFamily() == CatProtocolFamily.ICOM_CIV;
        boolean kenwoodSelected = settings.serialCatProtocolFamily() == CatProtocolFamily.KENWOOD_STYLE;
        binding.testSerialCatConnectionButton.setEnabled((yaesuSelected || icomSelected || kenwoodSelected) && !serialProbeInFlight);
        binding.requestSerialCatPermissionButton.setEnabled(!serialProbeInFlight);
        if (serialProbeInFlight
                && profile != null
                && profile.id().equals(serialProbeProfileId)) {
            binding.serialCatProbeStatusText.setText(serialProbeMessage);
            return;
        }
        if (serialProbeMessage != null
                && !serialProbeMessage.isEmpty()
                && profile != null
                && profile.id().equals(serialProbeProfileId)) {
            binding.serialCatProbeStatusText.setText(serialProbeMessage);
            return;
        }
        binding.serialCatProbeStatusText.setText(yaesuSelected
                ? "First native serial CAT probe path: open the CDC/ACM serial link and query a safe Yaesu-style CAT response such as FA;/IF;."
                : icomSelected
                        ? "Icom CI-V probe is available. Set the CI-V address first, then query a safe transceiver-ID response."
                        : kenwoodSelected
                                ? "Kenwood-style CAT probe is available. Start with a safe ASCII read such as ID;/FA;/IF; before any TX-side work."
                                : "Serial CAT probe is currently implemented for Yaesu-style CAT, Icom CI-V, and Kenwood-style CAT first.");
    }

    private String renderSelectedProfileStatus(
            RigProfile selectedProfile,
            RigProfile pinnedProfile,
            RigProfileSettings settings,
            boolean hasSavedOverride
    ) {
        if (selectedProfile == null) {
            return "No rig path pinned yet.\nChoose the family you expect to use most often so future RX/TX screens can default to it.";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Selected path preview:\n")
                .append(RigProfileConfigurationFormatter.renderCompactSummary(selectedProfile, settings))
                .append("\nConfiguration source: ")
                .append(hasSavedOverride ? "Saved override for this profile" : "Recommended defaults for this profile")
                .append("\nCapabilities: ")
                .append(selectedProfile.capabilitySummary())
                .append("\nNext setup note: ")
                .append(selectedProfile.setupNotes());
        if (pinnedProfile == null) {
            builder.append("\nPinned default: none");
        } else if (pinnedProfile.id().equals(selectedProfile.id())) {
            builder.append("\nPinned default: this rig path");
        } else {
            builder.append("\nPinned default: ").append(pinnedProfile.displayName());
        }
        return builder.toString();
    }

    private String renderProfileConfigStatus(
            RigProfile profile,
            RigProfileSettings settings,
            boolean hasSavedOverride
    ) {
        if (profile == null) {
            return "Choose a rig family to configure defaults and transport-specific hints.";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Editor snapshot:\n")
                .append(RigProfileConfigurationFormatter.renderCompactSummary(profile, settings))
                .append("\nProfile state: ")
                .append(hasSavedOverride
                        ? "Using your saved overrides for this profile."
                        : "Using the recommended defaults defined by this profile family.");
        if (!configStatusMessage.isEmpty()
                && configStatusProfileId != null
                && configStatusProfileId.equals(profile.id())) {
            builder.append("\nLast action: ").append(configStatusMessage);
        }
        return builder.toString();
    }

    private RigProfileSettings readSettingsFromEditor() {
        return new RigProfileSettings(
                parsePositiveInt(binding.defaultWpmEditText.getText() == null
                        ? null
                        : binding.defaultWpmEditText.getText().toString(), 18),
                parsePositiveInt(binding.defaultToneFrequencyEditText.getText() == null
                        ? null
                        : binding.defaultToneFrequencyEditText.getText().toString(), 650),
                selectedUsbKeyLine(),
                normalizedText(binding.usbPreferredDeviceNameEditText.getText() == null
                        ? null
                        : binding.usbPreferredDeviceNameEditText.getText().toString()),
                selectedCatProtocol(binding.serialCatProtocolSpinner.getSelectedItem(), CatProtocolFamily.GENERIC),
                parsePositiveInt(binding.serialCatBaudRateEditText.getText() == null
                        ? null
                        : binding.serialCatBaudRateEditText.getText().toString(), 9600),
                normalizedText(binding.serialCatPortHintEditText.getText() == null
                        ? null
                        : binding.serialCatPortHintEditText.getText().toString()),
                normalizedText(binding.serialCatCivAddressEditText.getText() == null
                        ? null
                        : binding.serialCatCivAddressEditText.getText().toString()),
                selectedCatProtocol(binding.networkCatProtocolSpinner.getSelectedItem(), CatProtocolFamily.HAMLIB_RIGCTLD),
                normalizedText(binding.networkHostEditText.getText() == null
                        ? null
                        : binding.networkHostEditText.getText().toString()),
                parsePositiveInt(binding.networkPortEditText.getText() == null
                        ? null
                        : binding.networkPortEditText.getText().toString(), 4532),
                normalizedText(binding.bluetoothDeviceHintEditText.getText() == null
                        ? null
                        : binding.bluetoothDeviceHintEditText.getText().toString())
        );
    }

    private SerialKeyerTxOutput.KeyLine selectedUsbKeyLine() {
        Object selectedItem = binding.usbKeyLineSpinner.getSelectedItem();
        if (selectedItem instanceof SerialKeyerTxOutput.KeyLine) {
            return (SerialKeyerTxOutput.KeyLine) selectedItem;
        }
        return SerialKeyerTxOutput.KeyLine.RTS;
    }

    private CatProtocolFamily selectedCatProtocol(Object selectedItem, CatProtocolFamily fallback) {
        if (selectedItem instanceof CatProtocolFamily) {
            return (CatProtocolFamily) selectedItem;
        }
        return fallback;
    }

    private String renderReadinessSummary(List<RigTransport> transports, List<RigProfile> profiles) {
        int readyTransportCount = 0;
        for (RigTransport transport : transports) {
            if (transport.isReady(this)) {
                readyTransportCount += 1;
            }
        }
        int benchReadyProfileCount = 0;
        for (RigProfile profile : profiles) {
            if (profile.supportLevel() == RigSupportLevel.BENCH_READY) {
                benchReadyProfileCount += 1;
            }
        }
        return "Ready transports: " + readyTransportCount + "/" + transports.size()
                + "\nBench-ready profiles: " + benchReadyProfileCount + "/" + profiles.size()
                + "\nCurrent strategy: support families first, then fill concrete vendor/model profiles."
                + "\nDebug remains available as a diagnostic path while the formal rig UI grows.";
    }

    private String renderTransportSummary(List<RigTransport> transports) {
        StringBuilder builder = new StringBuilder();
        for (RigTransport transport : transports) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(transport.displayName())
                    .append(" [")
                    .append(transport.kind().name())
                    .append("]")
                    .append("\nReady: ")
                    .append(transport.isReady(this) ? "yes" : "no")
                    .append("\n")
                    .append(transport.describeAvailability(this));
        }
        return builder.toString();
    }

    private String renderProfileSummary(List<RigProfile> profiles) {
        StringBuilder builder = new StringBuilder();
        for (RigProfile profile : profiles) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(profile.displayName())
                    .append(" [")
                    .append(profile.supportLevel().displayName())
                    .append("]")
                    .append("\nVendor/Model: ")
                    .append(profile.vendorLabel())
                    .append(" / ")
                    .append(profile.modelLabel())
                    .append("\nTransport: ")
                    .append(profile.transportKind().name())
                    .append("\nAdapter: ")
                    .append(profile.adapterId())
                    .append("\nCapabilities: ")
                    .append(profile.capabilitySummary())
                    .append("\nSummary: ")
                    .append(profile.summary())
                    .append("\nSetup: ")
                    .append(profile.setupNotes());
            if (!profile.knownConstraints().isEmpty()) {
                builder.append("\nConstraints: ")
                        .append(String.join(" | ", profile.knownConstraints()));
            }
        }
        return builder.toString();
    }

    private String renderNextStep(List<RigProfile> profiles) {
        RigProfile usbProfile = null;
        RigProfile voxProfile = null;
        RigProfile catProfile = null;
        RigProfile yaesuRigctldProfile = null;
        RigProfile icomRigctldProfile = null;
        RigProfile kenwoodRigctldProfile = null;
        for (RigProfile profile : profiles) {
            if ("usb-serial-keyer-generic".equals(profile.id())) {
                usbProfile = profile;
            } else if ("audio-vox-generic".equals(profile.id())) {
                voxProfile = profile;
            } else if ("generic-cat-serial".equals(profile.id())) {
                catProfile = profile;
            } else if ("yaesu-rigctld-network-family".equals(profile.id())) {
                yaesuRigctldProfile = profile;
            } else if ("icom-rigctld-network-family".equals(profile.id())) {
                icomRigctldProfile = profile;
            } else if ("kenwood-rigctld-network-family".equals(profile.id())) {
                kenwoodRigctldProfile = profile;
            }
        }
        StringBuilder builder = new StringBuilder("Recommended build order:");
        if (yaesuRigctldProfile != null) {
            builder.append("\n1. If you are benching Yaesu FT rigs now, start with ")
                    .append(yaesuRigctldProfile.displayName())
                    .append(" so CWCN can reuse the existing rigctld backend for TX while native serial CAT is still being brought up.");
        }
        if (icomRigctldProfile != null) {
            builder.append("\n2. For current Icom-family bench work, prefer ")
                    .append(icomRigctldProfile.displayName())
                    .append(" as the first formal path.");
        }
        if (kenwoodRigctldProfile != null) {
            builder.append("\n3. For current Kenwood-family bench work, prefer ")
                    .append(kenwoodRigctldProfile.displayName())
                    .append(" as the first formal path.");
        }
        if (usbProfile != null) {
            builder.append("\n4. Harden ").append(usbProfile.displayName())
                    .append(" as the first real wired-control family.");
        }
        if (voxProfile != null) {
            builder.append("\n5. Keep ").append(voxProfile.displayName())
                    .append(" as the compatibility fallback.");
        }
        if (catProfile != null) {
            builder.append("\n6. Keep the reusable CAT schema stable before attaching deeper native Yaesu/Icom/Kenwood model code.");
        }
        builder.append("\n7. Native serial CAT probe is now available for Yaesu-style, Icom CI-V, and Kenwood-style families in Rig Setup.");
        builder.append("\n8. Keep Debug available, but treat it as a secondary engineering tool rather than the main user flow.");
        builder.append("\n9. Next native step is no longer schema work; it is attaching controlled family-specific serial CAT behavior on top of the shared probe/session seam.");
        return builder.toString();
    }

    private int parsePositiveInt(String raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String normalizedText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private void registerUsbPermissionReceiver() {
        usbPermissionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context, Intent intent) {
                if (!ACTION_SERIAL_CAT_USB_PERMISSION.equals(intent.getAction())) {
                    return;
                }
                boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                RigProfile profile = selectedProfile();
                serialProbeProfileId = profile == null ? null : profile.id();
                serialProbeMessage = granted
                        ? "USB serial CAT permission granted by the system dialog."
                        : "USB serial CAT permission was denied by the system dialog.";
                refreshSelectedProfileViews();
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_SERIAL_CAT_USB_PERMISSION);
        ContextCompat.registerReceiver(
                this,
                usbPermissionReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
    }
}
