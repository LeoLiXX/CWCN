package org.bi9clt.cwcn.ui.rig;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.bi9clt.cwcn.R;
import org.bi9clt.cwcn.BuildConfig;
import org.bi9clt.cwcn.core.app.DeveloperModeStore;
import org.bi9clt.cwcn.core.app.OperateRouteModeStore;
import org.bi9clt.cwcn.core.rig.AndroidUsbProbedSerialKeyerPortFactory;
import org.bi9clt.cwcn.core.rig.AndroidUsbSerialCatSessionFactory;
import org.bi9clt.cwcn.core.rig.CatProtocolFamily;
import org.bi9clt.cwcn.core.rig.HamlibRigctldRigControlAdapter;
import org.bi9clt.cwcn.core.rig.KeyingPolarity;
import org.bi9clt.cwcn.core.rig.PortAvailability;
import org.bi9clt.cwcn.core.rig.RigCapability;
import org.bi9clt.cwcn.core.rig.RigProfileConfigurationFormatter;
import org.bi9clt.cwcn.core.rig.RigProfile;
import org.bi9clt.cwcn.core.rig.RigProfileSettings;
import org.bi9clt.cwcn.core.rig.RigRegistry;
import org.bi9clt.cwcn.core.rig.RigSelectionStore;
import org.bi9clt.cwcn.core.rig.SerialKeyerTxOutput;
import org.bi9clt.cwcn.core.rig.SerialCatProbe;
import org.bi9clt.cwcn.core.rig.SerialCatRigControlAdapter;
import org.bi9clt.cwcn.core.rig.RigSupportLevel;
import org.bi9clt.cwcn.core.rig.RigTransport;
import org.bi9clt.cwcn.core.rig.RigControlAdapter;
import org.bi9clt.cwcn.core.rig.UsbSerialDeviceOption;
import org.bi9clt.cwcn.databinding.ActivityRigSetupBinding;
import org.bi9clt.cwcn.core.rig.UsbSerialKeyerRigControlAdapter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class RigSetupActivity extends AppCompatActivity {
    public static final String EXTRA_OPEN_REASON = "org.bi9clt.cwcn.extra.OPEN_REASON";
    public static final String EXTRA_USB_DEVICE_NAME = "org.bi9clt.cwcn.extra.USB_DEVICE_NAME";
    public static final String EXTRA_USB_VENDOR_ID = "org.bi9clt.cwcn.extra.USB_VENDOR_ID";
    public static final String EXTRA_USB_PRODUCT_ID = "org.bi9clt.cwcn.extra.USB_PRODUCT_ID";
    public static final String EXTRA_OPEN_DEVELOPER_LABS = "org.bi9clt.cwcn.extra.OPEN_DEVELOPER_LABS";
    public static final String OPEN_REASON_USB_ATTACH = "usb-attached";
    private static final String ACTION_SERIAL_CAT_USB_PERMISSION = "org.bi9clt.cwcn.action.SERIAL_CAT_USB_PERMISSION";
    private static final String ACTION_USB_KEYER_PERMISSION = "org.bi9clt.cwcn.action.USB_KEYER_PERMISSION";
    private static final String TAG = "RigSetupActivity";

    private ActivityRigSetupBinding binding;
    private RigSelectionStore rigSelectionStore;
    private OperateRouteModeStore operateRouteModeStore;
    private DeveloperModeStore developerModeStore;
    private boolean syncingProfileSelection;
    private String configStatusMessage = "";
    private String configStatusProfileId;
    private String serialProbeMessage = "";
    private String serialProbeProfileId;
    private boolean serialProbeInFlight;
    private String usbKeyerStatusMessage = "";
    private String usbKeyerStatusProfileId;
    private boolean usbKeyerPermissionInFlight;
    private boolean usbKeyerActionInFlight;
    private boolean usbKeyerTransmissionInFlight;
    private String networkProbeMessage = "";
    private String networkProbeProfileId;
    private boolean networkProbeInFlight;
    private String launchStatusMessage = "";
    private BroadcastReceiver usbPermissionReceiver;
    private ArrayAdapter<RigProfile> profileAdapter;
    private ArrayAdapter<String> serialCatPortAdapter;
    private ArrayAdapter<String> serialCatKeyingPortAdapter;
    private ArrayAdapter<UsbSerialDeviceOption> usbDeviceAdapter;
    private ArrayAdapter<OperateRouteModeStore.Mode> routeModeAdapter;
    private final List<String> serialCatPortOptions = new ArrayList<>();
    private final List<String> serialCatPortHints = new ArrayList<>();
    private final List<String> serialCatKeyingPortOptions = new ArrayList<>();
    private final List<String> serialCatKeyingPortHints = new ArrayList<>();
    private boolean syncingUsbDeviceSelection;
    private boolean syncingSerialCatPortSelection;
    private boolean syncingSerialCatKeyingPortSelection;
    private boolean syncingSettingsEditor;
    private boolean syncingRouteModeSelection;
    private String currentEditorProfileId;
    private String currentRouteModeProfileId;
    private boolean openDeveloperLabs;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRigSetupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        rigSelectionStore = new RigSelectionStore(this);
        operateRouteModeStore = new OperateRouteModeStore(this);
        developerModeStore = new DeveloperModeStore(this);
        binding.versionText.setText(getString(R.string.rig_setup_version, BuildConfig.VERSION_NAME));
        consumeLaunchIntent(getIntent());
        registerUsbPermissionReceiver();
        setupProfileSelector();
        setupActions();
        initializeTimingLabDefaults();
        initializeShortPulseLabDefaults();
        refreshUi();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        consumeLaunchIntent(intent);
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
        binding.saveSelectedProfileButton.setOnClickListener(view -> saveSelectedProfile());
        binding.saveProfileConfigButton.setOnClickListener(view -> saveProfileConfig());
        binding.resetProfileConfigButton.setOnClickListener(view -> resetProfileConfig());
        binding.requestUsbKeyerPermissionButton.setOnClickListener(view -> requestUsbKeyerPermission());
        binding.testUsbKeyerPulseButton.setOnClickListener(view -> testUsbKeyerPulse());
        binding.sendUsbKeyerTestTextButton.setOnClickListener(view -> sendUsbKeyerTestText());
        binding.stopUsbKeyerTextButton.setOnClickListener(view -> stopUsbKeyerText());
        binding.requestSerialCatPermissionButton.setOnClickListener(view -> requestSerialCatPermission());
        binding.testSerialCatConnectionButton.setOnClickListener(view -> testSerialCatConnection());
        binding.testSerialCatPttButton.setOnClickListener(view -> testSerialCatPttPulse());
        binding.testSerialCatKeyingButton.setOnClickListener(view -> testSerialCatKeyingPulse());
        binding.holdSerialCatKeyingButton.setOnClickListener(view -> testSerialCatKeyingHold());
        binding.openCloseSerialCatKeyingPortButton.setOnClickListener(view -> testSerialCatKeyingOpenClose());
        binding.runSerialCatKeyingSweepButton.setOnClickListener(view -> runSerialCatKeyingSweep());
        binding.runSerialCatTimingLabButton.setOnClickListener(view -> runSerialCatTimingLab());
        binding.runSerialCatShortPulseLabButton.setOnClickListener(view -> runSerialCatShortPulseLab());
        binding.testNetworkCatConnectionButton.setOnClickListener(view -> testNetworkCatConnection());
    }

    private void setupProfileSelector() {
        profileAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new ArrayList<>()
        );
        profileAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.profileSpinner.setAdapter(profileAdapter);
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
        binding.serialCatKeyLineSpinner.setAdapter(keyLineAdapter);

        usbDeviceAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new ArrayList<>()
        );
        usbDeviceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.usbDeviceSpinner.setAdapter(usbDeviceAdapter);
        binding.usbDeviceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (syncingUsbDeviceSelection) {
                    return;
                }
                Object item = parent.getItemAtPosition(position);
                if (!(item instanceof UsbSerialDeviceOption)) {
                    return;
                }
                UsbSerialDeviceOption option = (UsbSerialDeviceOption) item;
                binding.usbPreferredDeviceNameEditText.setText(option.isAuto()
                        ? ""
                        : valueOrEmpty(option.deviceName()));
                refreshSelectedProfileViews();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        ArrayAdapter<CatProtocolFamily> catProtocolAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                CatProtocolFamily.values()
        );
        catProtocolAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.serialCatProtocolSpinner.setAdapter(catProtocolAdapter);
        binding.networkCatProtocolSpinner.setAdapter(catProtocolAdapter);
        binding.serialCatProtocolSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!syncingSettingsEditor) {
                    refreshSelectedProfileViews();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        binding.networkCatProtocolSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!syncingSettingsEditor) {
                    refreshSelectedProfileViews();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        ArrayAdapter<KeyingPolarity> keyingPolarityAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                KeyingPolarity.values()
        );
        keyingPolarityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.serialCatKeyingPolaritySpinner.setAdapter(keyingPolarityAdapter);

        ArrayAdapter<SerialCatRigControlAdapter.TimingLabOrder> timingLabOrderAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                SerialCatRigControlAdapter.TimingLabOrder.values()
        );
        timingLabOrderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.serialCatTimingAssertOrderSpinner.setAdapter(timingLabOrderAdapter);

        ArrayAdapter<SerialCatRigControlAdapter.TimingLabReleaseOrder> timingLabReleaseOrderAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                SerialCatRigControlAdapter.TimingLabReleaseOrder.values()
        );
        timingLabReleaseOrderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.serialCatTimingReleaseOrderSpinner.setAdapter(timingLabReleaseOrderAdapter);

        ArrayAdapter<SerialCatRigControlAdapter.ShortPulseLabPreset> shortPulsePresetAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                SerialCatRigControlAdapter.ShortPulseLabPreset.values()
        );
        shortPulsePresetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.serialCatShortPulsePresetSpinner.setAdapter(shortPulsePresetAdapter);

        serialCatPortAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                serialCatPortOptions
        );
        serialCatPortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.serialCatPortSpinner.setAdapter(serialCatPortAdapter);
        binding.serialCatPortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (syncingSerialCatPortSelection) {
                    return;
                }
                Object selectedItem = parent.getItemAtPosition(position);
                if (!(selectedItem instanceof String)) {
                    return;
                }
                String option = (String) selectedItem;
                int selectedPosition = parent.getSelectedItemPosition();
                String selectedHint = selectedPosition >= 0 && selectedPosition < serialCatPortHints.size()
                        ? serialCatPortHints.get(selectedPosition)
                        : null;
                if (selectedPosition <= 0 || selectedHint == null) {
                    binding.serialCatPortHintEditText.setText("");
                    return;
                }
                binding.serialCatPortHintEditText.setText(selectedHint);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        serialCatKeyingPortAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                serialCatKeyingPortOptions
        );
        serialCatKeyingPortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.serialCatKeyingPortSpinner.setAdapter(serialCatKeyingPortAdapter);
        binding.serialCatKeyingPortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (syncingSerialCatKeyingPortSelection) {
                    return;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        routeModeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                OperateRouteModeStore.Mode.values()
        ) {
            @Override
            public View getView(int position, @Nullable View convertView, android.view.ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                bindRouteModeLabel(view, getItem(position));
                return view;
            }

            @Override
            public View getDropDownView(
                    int position,
                    @Nullable View convertView,
                    android.view.ViewGroup parent
            ) {
                View view = super.getDropDownView(position, convertView, parent);
                bindRouteModeLabel(view, getItem(position));
                return view;
            }
        };
        routeModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.routeModeSpinner.setAdapter(routeModeAdapter);
        binding.routeModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!syncingRouteModeSelection) {
                    refreshSelectedProfileViews();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void refreshUi() {
        boolean developerModeEnabled = developerModeStore.isEnabled();
        boolean developerLabsVisible = developerModeEnabled && openDeveloperLabs;
        List<RigProfile> profiles = visibleProfiles(developerLabsVisible);
        List<RigProfile> spinnerProfiles = pickerProfiles(profiles, developerLabsVisible);
        List<RigTransport> transports = visibleTransports(profiles, developerLabsVisible);
        binding.readinessSummaryText.setText(renderReadinessSummary(transports, profiles));
        boolean hasLaunchStatus = launchStatusMessage != null && !launchStatusMessage.isEmpty();
        binding.usbAttachStatusPanel.setVisibility(hasLaunchStatus ? View.VISIBLE : View.GONE);
        binding.usbAttachStatusText.setText(hasLaunchStatus ? launchStatusMessage : "");
        syncDeveloperModeViews(developerModeEnabled);
        syncProfileOptions(spinnerProfiles);
        syncSelectedProfile(spinnerProfiles);
        refreshSelectedProfileViews();
        binding.transportSummaryText.setText(renderTransportSummary(transports));
        binding.profileSummaryText.setText(renderProfileSummary(profiles));
        binding.nextStepText.setText(renderNextStep(profiles));
    }

    private List<RigProfile> visibleProfiles(boolean developerLabsVisible) {
        List<RigProfile> allProfiles = RigRegistry.defaultProfiles();
        if (developerLabsVisible) {
            return sortVisibleProfiles(new ArrayList<>(allProfiles));
        }
        ArrayList<RigProfile> filtered = new ArrayList<>();
        for (RigProfile profile : allProfiles) {
            if (profile.supportLevel() != RigSupportLevel.BENCH_READY) {
                continue;
            }
            if (!isProductVisibleProfile(profile)) {
                continue;
            }
            filtered.add(profile);
        }
        return sortVisibleProfiles(filtered);
    }

    private List<RigProfile> pickerProfiles(
            List<RigProfile> productProfiles,
            boolean developerLabsVisible
    ) {
        if (developerLabsVisible) {
            return productProfiles;
        }
        ArrayList<RigProfile> pickerProfiles = new ArrayList<>(productProfiles);
        RigProfile pinnedProfile = rigSelectionStore == null ? null : rigSelectionStore.selectedProfile();
        if (pinnedProfile == null || containsProfileId(pickerProfiles, pinnedProfile.id())) {
            return pickerProfiles;
        }
        pickerProfiles.add(pinnedProfile);
        return sortVisibleProfiles(pickerProfiles);
    }

    private List<RigTransport> visibleTransports(
            List<RigProfile> profiles,
            boolean developerLabsVisible
    ) {
        List<RigTransport> allTransports = RigRegistry.defaultTransports();
        if (developerLabsVisible) {
            return allTransports;
        }
        LinkedHashSet<RigTransport.TransportKind> visibleKinds = new LinkedHashSet<>();
        for (RigProfile profile : profiles) {
            if (profile == null) {
                continue;
            }
            visibleKinds.add(profile.transportKind());
        }
        ArrayList<RigTransport> filtered = new ArrayList<>();
        for (RigTransport transport : allTransports) {
            if (visibleKinds.contains(transport.kind())) {
                filtered.add(transport);
            }
        }
        return filtered;
    }

    private boolean isProductVisibleProfile(@Nullable RigProfile profile) {
        return profile != null && !isRigctldBridgeProfile(profile);
    }

    private boolean isRigctldBridgeProfile(@Nullable RigProfile profile) {
        return profile != null
                && profile.transportKind() == RigTransport.TransportKind.NETWORK_CAT
                && "hamlib-rigctld".equals(profile.adapterId());
    }

    private boolean containsProfileId(List<RigProfile> profiles, @Nullable String profileId) {
        if (profiles == null || profileId == null || profileId.trim().isEmpty()) {
            return false;
        }
        for (RigProfile profile : profiles) {
            if (profile != null && profileId.equals(profile.id())) {
                return true;
            }
        }
        return false;
    }

    private List<RigProfile> sortVisibleProfiles(List<RigProfile> profiles) {
        if (profiles == null || profiles.size() < 2) {
            return profiles;
        }
        profiles.sort((left, right) -> Integer.compare(
                visibleProfileOrder(left == null ? null : left.id()),
                visibleProfileOrder(right == null ? null : right.id())
        ));
        return profiles;
    }

    private int visibleProfileOrder(@Nullable String profileId) {
        if ("audio-vox-generic".equals(profileId)) {
            return 0;
        }
        if ("usb-serial-keyer-generic".equals(profileId)) {
            return 1;
        }
        if ("yaesu-cat-serial-generic".equals(profileId)) {
            return 2;
        }
        if ("icom-civ-serial-generic".equals(profileId)) {
            return 3;
        }
        if ("kenwood-cat-serial-generic".equals(profileId)) {
            return 4;
        }
        if ("yaesu-rigctld-network-family".equals(profileId)) {
            return 5;
        }
        if ("icom-rigctld-network-family".equals(profileId)) {
            return 6;
        }
        if ("kenwood-rigctld-network-family".equals(profileId)) {
            return 7;
        }
        return Integer.MAX_VALUE;
    }

    private void syncProfileOptions(List<RigProfile> profiles) {
        if (profileAdapter == null) {
            return;
        }
        if (profileAdapter.getCount() == profiles.size()) {
            boolean sameOrder = true;
            for (int index = 0; index < profiles.size(); index++) {
                RigProfile existing = profileAdapter.getItem(index);
                RigProfile expected = profiles.get(index);
                if (existing == null || !expected.id().equals(existing.id())) {
                    sameOrder = false;
                    break;
                }
            }
            if (sameOrder) {
                return;
            }
        }
        syncingProfileSelection = true;
        profileAdapter.clear();
        profileAdapter.addAll(profiles);
        profileAdapter.notifyDataSetChanged();
        syncingProfileSelection = false;
    }

    private void syncSelectedProfile(List<RigProfile> profiles) {
        RigProfile selectedProfile = rigSelectionStore.selectedProfile();
        syncingProfileSelection = true;
        if (selectedProfile == null) {
            binding.profileSpinner.setSelection(0, false);
            syncingProfileSelection = false;
            return;
        }
        for (int index = 0; index < profiles.size(); index++) {
            if (profiles.get(index).id().equals(selectedProfile.id())) {
                binding.profileSpinner.setSelection(index, false);
                syncingProfileSelection = false;
                return;
            }
        }
        binding.profileSpinner.setSelection(0, false);
        syncingProfileSelection = false;
    }

    private void saveSelectedProfile() {
        RigProfile profile = selectedProfile();
        if (profile == null) {
            return;
        }
        persistSelectedRouteMode(profile);
        rigSelectionStore.saveSelectedProfileId(profile.id());
        configStatusMessage = getString(
                R.string.rig_setup_status_profile_selected,
                profile.displayName(),
                renderRouteModeLabel(readSelectedRouteMode(profile))
        );
        configStatusProfileId = profile.id();
        refreshUi();
    }

    private void saveProfileConfig() {
        RigProfile profile = selectedProfile();
        RigProfileSettings settings = readSettingsFromEditor();
        persistSelectedRouteMode(profile);
        rigSelectionStore.saveSettings(profile, settings);
        currentEditorProfileId = profile == null ? null : profile.id();
        configStatusMessage = profile == null
                ? getString(R.string.rig_setup_status_config_saved_generic)
                : getString(R.string.rig_setup_status_config_saved_profile, profile.displayName());
        configStatusProfileId = profile == null ? null : profile.id();
        refreshSelectedProfileViews();
    }

    private void resetProfileConfig() {
        RigProfile profile = selectedProfile();
        if (profile == null) {
            return;
        }
        rigSelectionStore.clearSettings(profile);
        currentEditorProfileId = null;
        configStatusMessage = getString(R.string.rig_setup_status_config_reset, profile.displayName());
        configStatusProfileId = profile.id();
        refreshSelectedProfileViews();
    }

    private void testNetworkCatConnection() {
        RigProfile profile = selectedProfile();
        RigProfileSettings settings = readSettingsFromEditor();
        networkProbeInFlight = true;
        networkProbeProfileId = profile == null ? null : profile.id();
        networkProbeMessage = getString(R.string.rig_setup_network_probe_running);
        refreshSelectedProfileViews();
        new Thread(() -> {
            HamlibRigctldRigControlAdapter.ProbeResult result =
                    HamlibRigctldRigControlAdapter.probeConfiguration(profile, settings);
            runOnUiThread(() -> {
                networkProbeInFlight = false;
                networkProbeProfileId = profile == null ? null : profile.id();
                networkProbeMessage = normalizeRigMessage(result.message());
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
        serialProbeProfileId = profile.id();
        serialProbeInFlight = true;
        serialProbeMessage = getString(R.string.rig_setup_serial_cat_permission_preparing);
        refreshSelectedProfileViews();
        AndroidUsbSerialCatSessionFactory factory = new AndroidUsbSerialCatSessionFactory(this);
        try {
            PortAvailability availabilityBefore = factory.availability(settings.serialCatPortHint());
            if (availabilityBefore.isReady()) {
                serialProbeMessage = getString(R.string.rig_setup_serial_cat_permission_ready);
            } else {
                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        this,
                        0,
                        new Intent(ACTION_SERIAL_CAT_USB_PERMISSION).setPackage(getPackageName()),
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
                                : PendingIntent.FLAG_UPDATE_CURRENT
                );
                boolean requested = factory.requestPermission(settings.serialCatPortHint(), pendingIntent);
                if (requested) {
                    serialProbeMessage = getString(R.string.rig_setup_serial_cat_permission_requested);
                } else {
                    serialProbeMessage = getString(
                            R.string.rig_setup_serial_cat_permission_unavailable,
                            normalizeRigMessage(factory.describeAvailability(settings.serialCatPortHint()))
                    );
                }
            }
        } catch (RuntimeException exception) {
            serialProbeMessage = getString(
                    R.string.rig_setup_serial_cat_permission_failed_before,
                    exception.getMessage()
            );
        } catch (Throwable throwable) {
            Log.e(TAG, "Serial CAT permission request crashed", throwable);
            serialProbeMessage = getString(
                    R.string.rig_setup_serial_cat_permission_crashed,
                    safeThrowableMessage(throwable)
            );
        }
        serialProbeInFlight = false;
        serialProbeProfileId = profile.id();
        refreshSelectedProfileViews();
    }

    private void testSerialCatConnection() {
        RigProfile profile = selectedProfile();
        RigProfileSettings settings = readSettingsFromEditor();
        serialProbeInFlight = true;
        serialProbeProfileId = profile == null ? null : profile.id();
        serialProbeMessage = getString(R.string.rig_setup_serial_cat_probe_running);
        refreshSelectedProfileViews();
        new Thread(() -> {
            final SerialCatProbe.ProbeResult result;
            try {
                result = SerialCatProbe.probeConfiguration(
                        profile,
                        settings,
                        new AndroidUsbSerialCatSessionFactory(this)
                );
            } catch (Throwable throwable) {
                Log.e(TAG, "Serial CAT probe worker crashed", throwable);
                runOnUiThread(() -> {
                    serialProbeInFlight = false;
                    serialProbeProfileId = profile == null ? null : profile.id();
                    serialProbeMessage = getString(
                            R.string.rig_setup_serial_cat_probe_crashed,
                            safeThrowableMessage(throwable)
                    );
                    refreshSelectedProfileViews();
                });
                return;
            }
            runOnUiThread(() -> {
                serialProbeInFlight = false;
                serialProbeProfileId = profile == null ? null : profile.id();
                serialProbeMessage = normalizeRigMessage(result.message());
                refreshSelectedProfileViews();
            });
        }, "cwcn-serial-cat-probe").start();
    }

    private void testSerialCatPttPulse() {
        RigProfile profile = selectedProfile();
        RigProfileSettings settings = readSettingsFromEditor();
        serialProbeInFlight = true;
        serialProbeProfileId = profile == null ? null : profile.id();
        serialProbeMessage = getString(R.string.rig_setup_serial_cat_ptt_running);
        refreshSelectedProfileViews();
        new Thread(() -> {
            final SerialCatRigControlAdapter.ControlResult result;
            try {
                result = SerialCatRigControlAdapter.testPttPulse(
                        profile,
                        settings,
                        new AndroidUsbSerialCatSessionFactory(this),
                        settings.defaultWpm(),
                        settings.defaultToneFrequencyHz(),
                        300
                );
            } catch (Throwable throwable) {
                Log.e(TAG, "Serial CAT PTT worker crashed", throwable);
                runOnUiThread(() -> {
                    serialProbeInFlight = false;
                    serialProbeProfileId = profile == null ? null : profile.id();
                    serialProbeMessage = getString(
                            R.string.rig_setup_serial_cat_ptt_crashed,
                            safeThrowableMessage(throwable)
                    );
                    refreshSelectedProfileViews();
                });
                return;
            }
            runOnUiThread(() -> {
                serialProbeInFlight = false;
                serialProbeProfileId = profile == null ? null : profile.id();
                serialProbeMessage = normalizeRigMessage(result.message());
                refreshSelectedProfileViews();
            });
        }, "cwcn-serial-cat-ptt").start();
    }

    private void testSerialCatKeyingPulse() {
        RigProfile profile = selectedProfile();
        RigProfileSettings settings = readSettingsFromEditor();
        serialProbeInFlight = true;
        serialProbeProfileId = profile == null ? null : profile.id();
        serialProbeMessage = getString(R.string.rig_setup_serial_keying_pulse_running);
        refreshSelectedProfileViews();
        new Thread(() -> {
            final SerialCatRigControlAdapter.ControlResult result;
            try {
                result = SerialCatRigControlAdapter.testDedicatedKeyingPulse(
                        profile,
                        settings,
                        new AndroidUsbProbedSerialKeyerPortFactory(this),
                        300
                );
            } catch (Throwable throwable) {
                Log.e(TAG, "Serial keying pulse worker crashed", throwable);
                runOnUiThread(() -> {
                    serialProbeInFlight = false;
                    serialProbeProfileId = profile == null ? null : profile.id();
                    serialProbeMessage = getString(
                            R.string.rig_setup_serial_keying_pulse_crashed,
                            safeThrowableMessage(throwable)
                    );
                    refreshSelectedProfileViews();
                });
                return;
            }
            runOnUiThread(() -> {
                serialProbeInFlight = false;
                serialProbeProfileId = profile == null ? null : profile.id();
                serialProbeMessage = normalizeRigMessage(result.message());
                refreshSelectedProfileViews();
            });
        }, "cwcn-serial-keying-pulse").start();
    }

    private void testSerialCatKeyingHold() {
        RigProfile profile = selectedProfile();
        RigProfileSettings settings = readSettingsFromEditor();
        serialProbeInFlight = true;
        serialProbeProfileId = profile == null ? null : profile.id();
        serialProbeMessage = getString(R.string.rig_setup_serial_keying_hold_running);
        refreshSelectedProfileViews();
        new Thread(() -> {
            final SerialCatRigControlAdapter.ControlResult result;
            try {
                result = SerialCatRigControlAdapter.testDedicatedKeyingHold(
                        profile,
                        settings,
                        new AndroidUsbProbedSerialKeyerPortFactory(this),
                        1500
                );
            } catch (Throwable throwable) {
                Log.e(TAG, "Serial keying hold worker crashed", throwable);
                runOnUiThread(() -> {
                    serialProbeInFlight = false;
                    serialProbeProfileId = profile == null ? null : profile.id();
                    serialProbeMessage = getString(
                            R.string.rig_setup_serial_keying_hold_crashed,
                            safeThrowableMessage(throwable)
                    );
                    refreshSelectedProfileViews();
                });
                return;
            }
            runOnUiThread(() -> {
                serialProbeInFlight = false;
                serialProbeProfileId = profile == null ? null : profile.id();
                serialProbeMessage = normalizeRigMessage(result.message());
                refreshSelectedProfileViews();
            });
        }, "cwcn-serial-keying-hold").start();
    }

    private void testSerialCatKeyingOpenClose() {
        RigProfile profile = selectedProfile();
        RigProfileSettings settings = readSettingsFromEditor();
        serialProbeInFlight = true;
        serialProbeProfileId = profile == null ? null : profile.id();
        serialProbeMessage = getString(R.string.rig_setup_serial_keying_open_close_running);
        refreshSelectedProfileViews();
        new Thread(() -> {
            final SerialCatRigControlAdapter.ControlResult result;
            try {
                result = SerialCatRigControlAdapter.testDedicatedKeyingOpenClose(
                        profile,
                        settings,
                        new AndroidUsbProbedSerialKeyerPortFactory(this),
                        900
                );
            } catch (Throwable throwable) {
                Log.e(TAG, "Serial keying open/close worker crashed", throwable);
                runOnUiThread(() -> {
                    serialProbeInFlight = false;
                    serialProbeProfileId = profile == null ? null : profile.id();
                    serialProbeMessage = getString(
                            R.string.rig_setup_serial_keying_open_close_crashed,
                            safeThrowableMessage(throwable)
                    );
                    refreshSelectedProfileViews();
                });
                return;
            }
            runOnUiThread(() -> {
                serialProbeInFlight = false;
                serialProbeProfileId = profile == null ? null : profile.id();
                serialProbeMessage = normalizeRigMessage(result.message());
                refreshSelectedProfileViews();
            });
        }, "cwcn-serial-keying-open-close").start();
    }

    private void runSerialCatTimingLab() {
        RigProfile profile = selectedProfile();
        RigProfileSettings settings = readSettingsFromEditor();
        SerialCatRigControlAdapter.TimingLabPlan plan = new SerialCatRigControlAdapter.TimingLabPlan(
                selectedTimingLabAssertOrder(),
                selectedTimingLabReleaseOrder(),
                parsePositiveInt(textValue(binding.serialCatTimingPreDelayEditText), 0),
                parsePositiveInt(textValue(binding.serialCatTimingHoldEditText), 900),
                parsePositiveInt(textValue(binding.serialCatTimingInterLineGapEditText), 30),
                parsePositiveInt(textValue(binding.serialCatTimingReleaseGapEditText), 30)
        );
        serialProbeInFlight = true;
        serialProbeProfileId = profile == null ? null : profile.id();
        serialProbeMessage = getString(R.string.rig_setup_serial_keying_timing_running);
        refreshSelectedProfileViews();
        new Thread(() -> {
            final SerialCatRigControlAdapter.ControlResult result;
            try {
                result = SerialCatRigControlAdapter.testDedicatedKeyingTimingLab(
                        profile,
                        settings,
                        new AndroidUsbProbedSerialKeyerPortFactory(this),
                        plan
                );
            } catch (Throwable throwable) {
                Log.e(TAG, "Serial keying timing lab worker crashed", throwable);
                runOnUiThread(() -> {
                    serialProbeInFlight = false;
                    serialProbeProfileId = profile == null ? null : profile.id();
                    serialProbeMessage = getString(
                            R.string.rig_setup_serial_keying_timing_crashed,
                            safeThrowableMessage(throwable)
                    );
                    refreshSelectedProfileViews();
                });
                return;
            }
            runOnUiThread(() -> {
                serialProbeInFlight = false;
                serialProbeProfileId = profile == null ? null : profile.id();
                serialProbeMessage = normalizeRigMessage(result.message());
                refreshSelectedProfileViews();
            });
        }, "cwcn-serial-keying-timing-lab").start();
    }

    private void runSerialCatShortPulseLab() {
        RigProfile profile = selectedProfile();
        RigProfileSettings settings = readSettingsFromEditor();
        SerialCatRigControlAdapter.ShortPulseLabPlan plan = new SerialCatRigControlAdapter.ShortPulseLabPlan(
                selectedShortPulsePreset(),
                settings.defaultWpm(),
                selectedTimingLabAssertOrder(),
                selectedTimingLabReleaseOrder(),
                parsePositiveInt(textValue(binding.serialCatShortPulsePreDelayEditText), 0),
                parsePositiveInt(textValue(binding.serialCatShortPulseTailHoldEditText), 0),
                parsePositiveInt(textValue(binding.serialCatShortPulseReleaseGapEditText), 0),
                parsePositiveInt(textValue(binding.serialCatTimingInterLineGapEditText), 30),
                parsePositiveInt(textValue(binding.serialCatTimingReleaseGapEditText), 30)
        );
        serialProbeInFlight = true;
        serialProbeProfileId = profile == null ? null : profile.id();
        serialProbeMessage = getString(R.string.rig_setup_serial_keying_short_pulse_running);
        refreshSelectedProfileViews();
        new Thread(() -> {
            final SerialCatRigControlAdapter.ControlResult result;
            try {
                result = SerialCatRigControlAdapter.testDedicatedKeyingShortPulseLab(
                        profile,
                        settings,
                        new AndroidUsbProbedSerialKeyerPortFactory(this),
                        plan
                );
            } catch (Throwable throwable) {
                Log.e(TAG, "Serial short pulse lab worker crashed", throwable);
                runOnUiThread(() -> {
                    serialProbeInFlight = false;
                    serialProbeProfileId = profile == null ? null : profile.id();
                    serialProbeMessage = getString(
                            R.string.rig_setup_serial_keying_short_pulse_crashed,
                            safeThrowableMessage(throwable)
                    );
                    refreshSelectedProfileViews();
                });
                return;
            }
            runOnUiThread(() -> {
                serialProbeInFlight = false;
                serialProbeProfileId = profile == null ? null : profile.id();
                serialProbeMessage = normalizeRigMessage(result.message());
                refreshSelectedProfileViews();
            });
        }, "cwcn-serial-short-pulse-lab").start();
    }

    private void runSerialCatKeyingSweep() {
        RigProfile profile = selectedProfile();
        RigProfileSettings settings = readSettingsFromEditor();
        serialProbeInFlight = true;
        serialProbeProfileId = profile == null ? null : profile.id();
        serialProbeMessage = getString(R.string.rig_setup_serial_keying_sweep_running);
        refreshSelectedProfileViews();
        new Thread(() -> {
            try {
                runSerialCatKeyingSweepWorker(profile, settings);
            } catch (Throwable throwable) {
                Log.e(TAG, "Serial keying sweep worker crashed", throwable);
                runOnUiThread(() -> {
                    serialProbeInFlight = false;
                    serialProbeProfileId = profile == null ? null : profile.id();
                    serialProbeMessage = getString(
                            R.string.rig_setup_serial_keying_sweep_crashed,
                            safeThrowableMessage(throwable)
                    );
                    refreshSelectedProfileViews();
                });
            }
        }, "cwcn-serial-keying-sweep").start();
    }

    private void runSerialCatKeyingSweepWorker(
            RigProfile profile,
            RigProfileSettings settings
    ) throws InterruptedException {
        LinkedHashSet<String> detectedPortHints = new LinkedHashSet<>(
                new AndroidUsbSerialCatSessionFactory(this).listDetectedPortHints()
        );
        if (settings.serialCatKeyingPortHint() != null) {
            detectedPortHints.add(settings.serialCatKeyingPortHint());
        }
        detectedPortHints.remove(null);
        detectedPortHints.remove("");
        if (detectedPortHints.isEmpty()) {
            runOnUiThread(() -> {
                serialProbeInFlight = false;
                serialProbeProfileId = profile == null ? null : profile.id();
                serialProbeMessage = getString(R.string.rig_setup_serial_keying_sweep_no_ports);
                refreshSelectedProfileViews();
            });
            return;
        }

        ArrayList<SweepStep> steps = new ArrayList<>();
        for (String portHint : detectedPortHints) {
            for (SerialKeyerTxOutput.KeyLine line : SerialKeyerTxOutput.KeyLine.values()) {
                for (KeyingPolarity polarity : KeyingPolarity.values()) {
                    steps.add(new SweepStep(portHint, line, polarity));
                }
            }
        }

        ArrayList<String> resultLines = new ArrayList<>();
        int totalSteps = steps.size();
        for (int index = 0; index < totalSteps; index++) {
            SweepStep step = steps.get(index);
            int stepNumber = index + 1;
            runOnUiThread(() -> {
                serialProbeMessage = getString(
                        R.string.rig_setup_serial_keying_sweep_step,
                        stepNumber,
                        totalSteps,
                        step.renderLabel()
                );
                refreshSelectedProfileViews();
            });
            Thread.sleep(420);
            RigProfileSettings stepSettings = buildSweepSettings(settings, step);
            SerialCatRigControlAdapter.ControlResult result = SerialCatRigControlAdapter.testDedicatedKeyingPulse(
                    profile,
                    stepSettings,
                    new AndroidUsbProbedSerialKeyerPortFactory(this),
                    220
            );
            resultLines.add(getString(
                    R.string.rig_setup_serial_keying_sweep_result_item,
                    stepNumber,
                    step.renderLabel(),
                    normalizeRigMessage(result.message())
            ));
            Thread.sleep(320);
        }

        runOnUiThread(() -> {
            serialProbeInFlight = false;
            serialProbeProfileId = profile == null ? null : profile.id();
            serialProbeMessage = getString(
                    R.string.rig_setup_serial_keying_sweep_completed,
                    String.join("\n", resultLines)
            );
            refreshSelectedProfileViews();
        });
    }

    private RigProfileSettings buildSweepSettings(RigProfileSettings baseSettings, SweepStep step) {
        return new RigProfileSettings(
                baseSettings.defaultWpm(),
                baseSettings.defaultToneFrequencyHz(),
                baseSettings.usbKeyLine(),
                baseSettings.usbPreferredDeviceName(),
                baseSettings.serialCatProtocolFamily(),
                baseSettings.serialCatBaudRate(),
                baseSettings.serialCatPortHint(),
                step.keyLine,
                step.portHint,
                step.polarity,
                step.keyLine == SerialKeyerTxOutput.KeyLine.RTS,
                step.keyLine == SerialKeyerTxOutput.KeyLine.DTR,
                baseSettings.serialCatCivAddressHex(),
                baseSettings.networkCatProtocolFamily(),
                baseSettings.networkHost(),
                baseSettings.networkPort(),
                baseSettings.bluetoothDeviceHint()
        );
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
        RigProfileSettings storedSettings = rigSelectionStore.loadSettings(profile);
        OperateRouteModeStore.Mode storedRouteMode = operateRouteModeStore.mode(profile);
        boolean hasSavedOverride = rigSelectionStore.hasSavedSettings(profile);
        boolean developerModeEnabled = developerModeStore.isEnabled();
        boolean shouldReloadEditor = profile == null
                ? currentEditorProfileId != null
                : !profile.id().equals(currentEditorProfileId);
        if (shouldReloadEditor) {
            syncSettingsEditor(storedSettings);
            currentEditorProfileId = profile == null ? null : profile.id();
        }
        RigProfileSettings settings = shouldReloadEditor ? storedSettings : readSettingsFromEditor();
        boolean shouldReloadRouteMode = profile == null
                ? currentRouteModeProfileId != null
                : !profile.id().equals(currentRouteModeProfileId);
        if (shouldReloadRouteMode) {
            syncRouteModeSelection(profile, storedRouteMode);
            currentRouteModeProfileId = profile == null ? null : profile.id();
        }
        OperateRouteModeStore.Mode routeMode = shouldReloadRouteMode
                ? storedRouteMode
                : readSelectedRouteMode(profile);
        boolean routeModeDirty = profile != null && storedRouteMode != routeMode;
        syncUsbDeviceOptions(profile, settings);
        syncSerialCatPortOptions(profile, settings);
        updateConfigVisibility(profile, settings, developerModeEnabled);
        binding.resetProfileConfigButton.setEnabled(profile != null && hasSavedOverride);
        syncRouteModeViews(profile, routeMode);
        syncProfileActionButton(profile, pinnedProfile, routeModeDirty);
        syncUsbKeyerState(profile, settings);
        syncSerialProbeState(profile, settings, developerModeEnabled);
        syncNetworkProbeState(profile, settings, developerModeEnabled);
        binding.connectionGuideText.setText(renderConnectionGuide(profile, settings, routeMode, developerModeEnabled));
        binding.selectedProfileStatusText.setText(renderSelectedProfileStatus(
                profile,
                pinnedProfile,
                settings,
                hasSavedOverride,
                routeMode,
                routeModeDirty
        ));
        binding.profileConfigStatusText.setText(renderProfileConfigStatus(profile, settings, hasSavedOverride));
    }

    private void syncRouteModeSelection(
            @Nullable RigProfile profile,
            @Nullable OperateRouteModeStore.Mode routeMode
    ) {
        syncingRouteModeSelection = true;
        binding.routeModeSpinner.setSelection(
                operateRouteModeStore.sanitize(profile, routeMode).ordinal(),
                false
        );
        syncingRouteModeSelection = false;
    }

    private void syncRouteModeViews(
            @Nullable RigProfile profile,
            @Nullable OperateRouteModeStore.Mode routeMode
    ) {
        boolean visible = operateRouteModeStore.supportsHybridPhoneRx(profile);
        int visibility = visible ? View.VISIBLE : View.GONE;
        binding.routeModeLabelText.setVisibility(visibility);
        binding.routeModeSpinner.setVisibility(visibility);
        binding.routeModeHintText.setVisibility(visibility);
        if (!visible) {
            binding.routeModeHintText.setText("");
            return;
        }
        OperateRouteModeStore.Mode safeMode = operateRouteModeStore.sanitize(profile, routeMode);
        binding.routeModeHintText.setText(getString(safeMode.descriptionResId()));
    }

    private void syncSettingsEditor(RigProfileSettings settings) {
        syncingSettingsEditor = true;
        binding.usbKeyLineSpinner.setSelection(settings.usbKeyLine().ordinal());
        binding.usbPreferredDeviceNameEditText.setText(valueOrEmpty(settings.usbPreferredDeviceName()));
        binding.serialCatProtocolSpinner.setSelection(settings.serialCatProtocolFamily().ordinal());
        binding.serialCatBaudRateEditText.setText(String.valueOf(settings.serialCatBaudRate()));
        binding.serialCatKeyLineSpinner.setSelection(settings.serialCatKeyLine().ordinal());
        binding.serialCatKeyingPolaritySpinner.setSelection(settings.serialCatKeyingPolarity().ordinal());
        binding.serialCatAssertRtsCheckBox.setChecked(settings.serialCatAssertRtsDuringKeying());
        binding.serialCatAssertDtrCheckBox.setChecked(settings.serialCatAssertDtrDuringKeying());
        binding.serialCatPortHintEditText.setText(valueOrEmpty(settings.serialCatPortHint()));
        binding.serialCatCivAddressEditText.setText(valueOrEmpty(settings.serialCatCivAddressHex()));
        binding.networkCatProtocolSpinner.setSelection(settings.networkCatProtocolFamily().ordinal());
        binding.networkHostEditText.setText(valueOrEmpty(settings.networkHost()));
        binding.networkPortEditText.setText(String.valueOf(settings.networkPort()));
        binding.bluetoothDeviceHintEditText.setText(valueOrEmpty(settings.bluetoothDeviceHint()));
        syncingSettingsEditor = false;
    }

    private void syncUsbDeviceOptions(@Nullable RigProfile profile, RigProfileSettings settings) {
        syncingUsbDeviceSelection = true;
        usbDeviceAdapter.clear();
        if (profile == null || !profile.hasCapability(RigCapability.KEY_LINE_CONTROL)) {
            usbDeviceAdapter.add(UsbSerialDeviceOption.autoOption());
            usbDeviceAdapter.notifyDataSetChanged();
            binding.usbDeviceSpinner.setSelection(0, false);
            syncingUsbDeviceSelection = false;
            return;
        }
        UsbSerialKeyerRigControlAdapter adapter = resolveUsbKeyerAdapter(profile, settings);
        List<UsbSerialDeviceOption> devices = new ArrayList<>();
        devices.add(UsbSerialDeviceOption.autoOption());
        if (adapter != null) {
            devices.addAll(adapter.availableDevices());
        }
        usbDeviceAdapter.addAll(devices);
        usbDeviceAdapter.notifyDataSetChanged();
        int selectionIndex = resolveUsbDeviceSelectionIndex(devices, settings.usbPreferredDeviceName());
        binding.usbDeviceSpinner.setSelection(selectionIndex, false);
        syncingUsbDeviceSelection = false;
    }

    private int resolveUsbDeviceSelectionIndex(
            List<UsbSerialDeviceOption> devices,
            @Nullable String preferredDeviceName
    ) {
        if (preferredDeviceName == null || preferredDeviceName.trim().isEmpty()) {
            return 0;
        }
        for (int index = 0; index < devices.size(); index++) {
            UsbSerialDeviceOption option = devices.get(index);
            if (!option.isAuto() && preferredDeviceName.equals(option.deviceName())) {
                return index;
            }
        }
        return 0;
    }

    private void initializeTimingLabDefaults() {
        binding.serialCatTimingAssertOrderSpinner.setSelection(
                SerialCatRigControlAdapter.TimingLabOrder.SIMULTANEOUS.ordinal()
        );
        binding.serialCatTimingReleaseOrderSpinner.setSelection(
                SerialCatRigControlAdapter.TimingLabReleaseOrder.TOGETHER.ordinal()
        );
        binding.serialCatTimingPreDelayEditText.setText("0");
        binding.serialCatTimingHoldEditText.setText("900");
        binding.serialCatTimingInterLineGapEditText.setText("30");
        binding.serialCatTimingReleaseGapEditText.setText("30");
    }

    private void initializeShortPulseLabDefaults() {
        binding.serialCatShortPulsePresetSpinner.setSelection(
                SerialCatRigControlAdapter.ShortPulseLabPreset.SINGLE_E.ordinal()
        );
        binding.serialCatShortPulsePreDelayEditText.setText("0");
        binding.serialCatShortPulseTailHoldEditText.setText("0");
        binding.serialCatShortPulseReleaseGapEditText.setText("0");
    }

    private void updateConfigVisibility(
            RigProfile profile,
            RigProfileSettings settings,
            boolean developerModeEnabled
    ) {
        boolean usbVisible = profile != null && profile.hasCapability(RigCapability.KEY_LINE_CONTROL);
        boolean serialCatVisible = profile != null && profile.hasCapability(RigCapability.SERIAL_CAT);
        boolean networkCatVisible = profile != null && profile.hasCapability(RigCapability.NETWORK_CAT);
        boolean bluetoothVisible = profile != null && profile.hasCapability(RigCapability.BLUETOOTH_SERIAL);
        boolean audioVoxVisible = profile != null && profile.hasCapability(RigCapability.AUDIO_VOX);
        boolean civAddressVisible = serialCatVisible
                && settings.serialCatProtocolFamily() == CatProtocolFamily.ICOM_CIV;
        boolean developerLabsVisible = developerModeEnabled && openDeveloperLabs;
        boolean usbBenchVisible = usbVisible && developerLabsVisible;

        binding.usbConfigGroup.setVisibility(usbVisible ? View.VISIBLE : View.GONE);
        binding.requestUsbKeyerPermissionButton.setVisibility(usbVisible ? View.VISIBLE : View.GONE);
        binding.testUsbKeyerPulseButton.setVisibility(usbBenchVisible ? View.VISIBLE : View.GONE);
        binding.sendUsbKeyerTestTextButton.setVisibility(usbBenchVisible ? View.VISIBLE : View.GONE);
        binding.stopUsbKeyerTextButton.setVisibility(usbBenchVisible ? View.VISIBLE : View.GONE);
        binding.usbKeyerStatusText.setVisibility(usbVisible ? View.VISIBLE : View.GONE);
        binding.serialCatConfigGroup.setVisibility(serialCatVisible ? View.VISIBLE : View.GONE);
        binding.networkCatConfigGroup.setVisibility(networkCatVisible ? View.VISIBLE : View.GONE);
        binding.bluetoothConfigGroup.setVisibility(bluetoothVisible ? View.VISIBLE : View.GONE);
        binding.audioVoxConfigHintText.setVisibility(audioVoxVisible ? View.VISIBLE : View.GONE);
        binding.serialCatProtocolLabelText.setVisibility(serialCatVisible ? View.VISIBLE : View.GONE);
        binding.serialCatProtocolSpinner.setVisibility(serialCatVisible ? View.VISIBLE : View.GONE);
        binding.serialCatKeyLineLabelText.setVisibility(serialCatVisible ? View.VISIBLE : View.GONE);
        binding.serialCatKeyLineSpinner.setVisibility(serialCatVisible ? View.VISIBLE : View.GONE);
        binding.serialCatKeyingPolarityLabelText.setVisibility(serialCatVisible ? View.VISIBLE : View.GONE);
        binding.serialCatKeyingPolaritySpinner.setVisibility(serialCatVisible ? View.VISIBLE : View.GONE);
        binding.serialCatAssertRtsCheckBox.setVisibility(serialCatVisible ? View.VISIBLE : View.GONE);
        binding.serialCatAssertDtrCheckBox.setVisibility(serialCatVisible ? View.VISIBLE : View.GONE);
        binding.serialCatPortPickerLabelText.setVisibility(serialCatVisible ? View.VISIBLE : View.GONE);
        binding.serialCatPortSpinner.setVisibility(serialCatVisible ? View.VISIBLE : View.GONE);
        binding.serialCatKeyingPortLabelText.setVisibility(serialCatVisible ? View.VISIBLE : View.GONE);
        binding.serialCatKeyingPortSpinner.setVisibility(serialCatVisible ? View.VISIBLE : View.GONE);
        binding.serialCatTimingLabLabelText.setVisibility(serialCatVisible && developerLabsVisible ? View.VISIBLE : View.GONE);
        binding.serialCatTimingAssertOrderSpinner.setVisibility(serialCatVisible && developerLabsVisible ? View.VISIBLE : View.GONE);
        binding.serialCatTimingReleaseOrderSpinner.setVisibility(serialCatVisible && developerLabsVisible ? View.VISIBLE : View.GONE);
        binding.serialCatTimingPreDelayEditText.setVisibility(serialCatVisible && developerLabsVisible ? View.VISIBLE : View.GONE);
        binding.serialCatTimingHoldEditText.setVisibility(serialCatVisible && developerLabsVisible ? View.VISIBLE : View.GONE);
        binding.serialCatTimingInterLineGapEditText.setVisibility(serialCatVisible && developerLabsVisible ? View.VISIBLE : View.GONE);
        binding.serialCatTimingReleaseGapEditText.setVisibility(serialCatVisible && developerLabsVisible ? View.VISIBLE : View.GONE);
        binding.serialCatShortPulseLabLabelText.setVisibility(serialCatVisible && developerLabsVisible ? View.VISIBLE : View.GONE);
        binding.serialCatShortPulsePresetSpinner.setVisibility(serialCatVisible && developerLabsVisible ? View.VISIBLE : View.GONE);
        binding.serialCatShortPulsePreDelayEditText.setVisibility(serialCatVisible && developerLabsVisible ? View.VISIBLE : View.GONE);
        binding.serialCatShortPulseTailHoldEditText.setVisibility(serialCatVisible && developerLabsVisible ? View.VISIBLE : View.GONE);
        binding.serialCatShortPulseReleaseGapEditText.setVisibility(serialCatVisible && developerLabsVisible ? View.VISIBLE : View.GONE);
        binding.serialCatPortHintEditText.setVisibility(serialCatVisible && developerLabsVisible ? View.VISIBLE : View.GONE);
        binding.serialCatCivAddressEditText.setVisibility(civAddressVisible ? View.VISIBLE : View.GONE);
        binding.networkCatProtocolLabelText.setVisibility(networkCatVisible ? View.VISIBLE : View.GONE);
        binding.networkCatProtocolSpinner.setVisibility(networkCatVisible ? View.VISIBLE : View.GONE);
    }

    private void syncSerialCatPortOptions(RigProfile profile, RigProfileSettings settings) {
        boolean serialCatVisible = profile != null && profile.hasCapability(RigCapability.SERIAL_CAT);
        syncingSerialCatPortSelection = true;
        syncingSerialCatKeyingPortSelection = true;
        serialCatPortOptions.clear();
        serialCatPortHints.clear();
        serialCatKeyingPortOptions.clear();
        serialCatKeyingPortHints.clear();
        if (!serialCatVisible) {
            serialCatPortOptions.add(getString(R.string.rig_setup_serial_port_auto_option));
            serialCatPortHints.add(null);
            serialCatPortAdapter.notifyDataSetChanged();
            binding.serialCatPortSpinner.setSelection(0, false);
            serialCatKeyingPortOptions.add(getString(R.string.rig_setup_serial_port_auto_option));
            serialCatKeyingPortHints.add(null);
            serialCatKeyingPortAdapter.notifyDataSetChanged();
            binding.serialCatKeyingPortSpinner.setSelection(0, false);
            syncingSerialCatPortSelection = false;
            syncingSerialCatKeyingPortSelection = false;
            return;
        }

        List<String> detectedHints = new AndroidUsbSerialCatSessionFactory(this).listDetectedPortHints();
        String recommendedCatHint = recommendSerialCatPortHint(profile, detectedHints, true);
        String recommendedKeyingHint = recommendSerialCatPortHint(profile, detectedHints, false);
        populateSerialPortSpinner(
                serialCatPortOptions,
                serialCatPortHints,
                serialCatPortAdapter,
                binding.serialCatPortSpinner,
                detectedHints,
                settings.serialCatPortHint() != null ? settings.serialCatPortHint() : recommendedCatHint,
                recommendedCatHint,
                true
        );
        populateSerialPortSpinner(
                serialCatKeyingPortOptions,
                serialCatKeyingPortHints,
                serialCatKeyingPortAdapter,
                binding.serialCatKeyingPortSpinner,
                detectedHints,
                settings.serialCatKeyingPortHint() != null ? settings.serialCatKeyingPortHint() : recommendedKeyingHint,
                recommendedKeyingHint,
                false
        );
        if (settings.serialCatPortHint() == null && recommendedCatHint != null) {
            binding.serialCatPortHintEditText.setText(recommendedCatHint);
        }
        syncingSerialCatPortSelection = false;
        syncingSerialCatKeyingPortSelection = false;
    }

    private void populateSerialPortSpinner(
            List<String> options,
            List<String> hints,
            ArrayAdapter<String> adapter,
            android.widget.Spinner spinner,
            List<String> detectedHints,
            String currentHint,
            @Nullable String recommendedHint,
            boolean catRole
    ) {
        options.add(getString(R.string.rig_setup_serial_port_auto_option));
        hints.add(null);
        for (String detectedHint : detectedHints) {
            options.add(renderSerialCatPortLabel(detectedHint, recommendedHint, catRole));
            hints.add(detectedHint);
        }
        if (currentHint != null && !currentHint.isEmpty() && !detectedHints.contains(currentHint)) {
            options.add(getString(R.string.rig_setup_serial_port_manual_option, currentHint));
            hints.add(currentHint);
        }
        adapter.notifyDataSetChanged();

        int selection = 0;
        if (currentHint != null && !currentHint.isEmpty()) {
            for (int index = 0; index < hints.size(); index++) {
                if (currentHint.equals(hints.get(index))) {
                    selection = index;
                    break;
                }
            }
        }
        spinner.setSelection(selection, false);
    }

    private String renderSerialCatPortLabel(
            String portHint,
            @Nullable String recommendedHint,
            boolean catRole
    ) {
        if (portHint == null || portHint.trim().isEmpty()) {
            return getString(R.string.rig_setup_serial_port_detected_generic);
        }
        String trimmed = portHint.trim();
        int separator = trimmed.lastIndexOf('#');
        String devicePart = separator >= 0 ? trimmed.substring(0, separator) : trimmed;
        String portPart = separator >= 0 && separator < trimmed.length() - 1
                ? trimmed.substring(separator + 1)
                : "?";
        int lastSlash = devicePart.lastIndexOf('/');
        String shortDevice = lastSlash >= 0 && lastSlash < devicePart.length() - 1
                ? devicePart.substring(lastSlash + 1)
                : devicePart;
        String roleLabel = "";
        if (recommendedHint != null && recommendedHint.equals(trimmed)) {
            roleLabel = catRole
                    ? getString(R.string.rig_setup_serial_port_role_cat_recommended)
                    : getString(R.string.rig_setup_serial_port_role_keying_recommended);
        } else if ("1".equals(portPart)) {
            roleLabel = getString(R.string.rig_setup_serial_port_role_cat_hint);
        } else if ("0".equals(portPart)) {
            roleLabel = getString(R.string.rig_setup_serial_port_role_keying_hint);
        }
        return getString(R.string.rig_setup_serial_port_detected_label, shortDevice, portPart, roleLabel);
    }

    @Nullable
    private String recommendSerialCatPortHint(
            @Nullable RigProfile profile,
            List<String> detectedHints,
            boolean catRole
    ) {
        if (profile == null
                || profile.transportKind() != RigTransport.TransportKind.USB_SERIAL
                || profile.defaultSettings().serialCatProtocolFamily() != CatProtocolFamily.YAESU_STYLE
                || detectedHints == null
                || detectedHints.isEmpty()) {
            return null;
        }
        String preferredPortSuffix = catRole ? "#1" : "#0";
        for (String hint : detectedHints) {
            if (hint != null && hint.endsWith(preferredPortSuffix)) {
                return hint;
            }
        }
        return detectedHints.size() == 1 ? detectedHints.get(0) : null;
    }

    private void syncProfileActionButton(
            RigProfile selectedProfile,
            RigProfile pinnedProfile,
            boolean routeModeDirty
    ) {
        if (selectedProfile == null) {
            binding.saveSelectedProfileButton.setText(R.string.rig_setup_select_current_path);
            binding.saveSelectedProfileButton.setEnabled(false);
            return;
        }
        binding.saveSelectedProfileButton.setEnabled(true);
        if (pinnedProfile != null && pinnedProfile.id().equals(selectedProfile.id())) {
            binding.saveSelectedProfileButton.setText(
                    routeModeDirty
                            ? R.string.rig_setup_current_path_apply_mode
                            : R.string.rig_setup_current_path_enabled
            );
        } else {
            binding.saveSelectedProfileButton.setText(R.string.rig_setup_select_current_path);
        }
    }

    private void syncNetworkProbeState(RigProfile profile, RigProfileSettings settings, boolean developerModeEnabled) {
        boolean networkCatVisible = profile != null && profile.hasCapability(RigCapability.NETWORK_CAT);
        boolean developerLabsVisible = developerModeEnabled && openDeveloperLabs;
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
                ? developerLabsVisible
                        ? getString(R.string.rig_setup_network_probe_hint_labs)
                        : getString(R.string.rig_setup_network_probe_hint_basic)
                : getString(R.string.rig_setup_network_probe_hint_disabled));
    }

    private void syncSerialProbeState(RigProfile profile, RigProfileSettings settings, boolean developerModeEnabled) {
        boolean serialCatVisible = profile != null && profile.hasCapability(RigCapability.SERIAL_CAT);
        boolean developerLabsVisible = developerModeEnabled && openDeveloperLabs;
        binding.requestSerialCatPermissionButton.setVisibility(serialCatVisible ? View.VISIBLE : View.GONE);
        binding.testSerialCatConnectionButton.setVisibility(serialCatVisible ? View.VISIBLE : View.GONE);
        binding.testSerialCatPttButton.setVisibility(serialCatVisible && developerLabsVisible ? View.VISIBLE : View.GONE);
        binding.runSerialCatKeyingSweepButton.setVisibility(serialCatVisible && developerLabsVisible ? View.VISIBLE : View.GONE);
        binding.serialCatProbeStatusText.setVisibility(serialCatVisible ? View.VISIBLE : View.GONE);
        if (!serialCatVisible) {
            return;
        }
        boolean yaesuSelected = settings.serialCatProtocolFamily() == CatProtocolFamily.YAESU_STYLE;
        boolean icomSelected = settings.serialCatProtocolFamily() == CatProtocolFamily.ICOM_CIV;
        boolean kenwoodSelected = settings.serialCatProtocolFamily() == CatProtocolFamily.KENWOOD_STYLE;
        binding.testSerialCatConnectionButton.setEnabled((yaesuSelected || icomSelected || kenwoodSelected) && !serialProbeInFlight);
        binding.testSerialCatPttButton.setEnabled(
                (yaesuSelected || (icomSelected && settings.serialCatCivAddressHex() != null))
                        && !serialProbeInFlight
        );
        binding.testSerialCatKeyingButton.setVisibility(serialCatVisible && developerLabsVisible ? View.VISIBLE : View.GONE);
        binding.holdSerialCatKeyingButton.setVisibility(serialCatVisible && developerLabsVisible ? View.VISIBLE : View.GONE);
        binding.openCloseSerialCatKeyingPortButton.setVisibility(serialCatVisible && developerLabsVisible ? View.VISIBLE : View.GONE);
        binding.runSerialCatTimingLabButton.setVisibility(serialCatVisible && developerLabsVisible ? View.VISIBLE : View.GONE);
        binding.runSerialCatShortPulseLabButton.setVisibility(serialCatVisible && developerLabsVisible ? View.VISIBLE : View.GONE);
        boolean dedicatedKeyingEligible = (yaesuSelected || icomSelected || kenwoodSelected)
                && settings.serialCatKeyingPortHint() != null;
        binding.testSerialCatKeyingButton.setEnabled(
                dedicatedKeyingEligible
                        && !serialProbeInFlight
        );
        binding.holdSerialCatKeyingButton.setEnabled(
                dedicatedKeyingEligible
                        && !serialProbeInFlight
        );
        binding.openCloseSerialCatKeyingPortButton.setEnabled(
                dedicatedKeyingEligible
                        && !serialProbeInFlight
        );
        binding.runSerialCatTimingLabButton.setEnabled(
                dedicatedKeyingEligible
                        && !serialProbeInFlight
        );
        binding.runSerialCatShortPulseLabButton.setEnabled(
                dedicatedKeyingEligible
                        && !serialProbeInFlight
        );
        binding.runSerialCatKeyingSweepButton.setEnabled(yaesuSelected && !serialProbeInFlight);
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
        List<String> detectedPorts = new AndroidUsbSerialCatSessionFactory(this).listDetectedPortHints();
        String pickerHint = detectedPorts.isEmpty()
                ? getString(R.string.rig_setup_serial_ports_none)
                : detectedPorts.size() == 1
                ? getString(R.string.rig_setup_serial_ports_one, detectedPorts.get(0))
                : getString(R.string.rig_setup_serial_ports_many, detectedPorts.size());
        if (!developerLabsVisible) {
            binding.serialCatProbeStatusText.setText(yaesuSelected
                    ? getString(R.string.rig_setup_serial_probe_basic_yaesu_hybrid, pickerHint)
                    : icomSelected || kenwoodSelected
                    ? getString(R.string.rig_setup_serial_probe_basic_supported, pickerHint)
                    : getString(R.string.rig_setup_serial_probe_basic_unsupported, pickerHint));
            return;
        }
        binding.serialCatProbeStatusText.setText(yaesuSelected
                ? getString(R.string.rig_setup_serial_probe_yaesu, pickerHint)
                : icomSelected
                        ? settings.serialCatCivAddressHex() == null
                                ? getString(R.string.rig_setup_serial_probe_icom_without_civ, pickerHint)
                                : getString(R.string.rig_setup_serial_probe_icom_with_civ, pickerHint)
                        : kenwoodSelected
                                ? getString(R.string.rig_setup_serial_probe_kenwood, pickerHint)
                                : getString(R.string.rig_setup_serial_probe_generic, pickerHint));
    }

    private void syncDeveloperModeViews(boolean enabled) {
        boolean developerLabsVisible = enabled && openDeveloperLabs;
        binding.titleText.setText(developerLabsVisible ? R.string.rig_setup_title_labs : R.string.rig_setup_title_config);
        binding.subtitleText.setText(developerLabsVisible
                ? R.string.rig_setup_subtitle_labs
                : R.string.rig_setup_subtitle_config);
        int developerSummaryVisibility = developerLabsVisible ? View.VISIBLE : View.GONE;
        binding.readinessSummaryPanel.setVisibility(developerSummaryVisibility);
        binding.transportSummaryPanel.setVisibility(developerSummaryVisibility);
        binding.profileSummaryPanel.setVisibility(developerSummaryVisibility);
        binding.nextStepPanel.setVisibility(developerSummaryVisibility);
    }

    private String renderConnectionGuide(
            RigProfile profile,
            RigProfileSettings settings,
            OperateRouteModeStore.Mode routeMode,
            boolean developerModeEnabled
    ) {
        if (profile == null) {
            return getString(R.string.rig_setup_connection_guide_empty);
        }
        StringBuilder builder = new StringBuilder();
        builder.append(getString(R.string.rig_setup_connection_current_path))
                .append(profile.displayName())
                .append("\n")
                .append(getString(R.string.rig_setup_connection_purpose))
                .append(profile.summary())
                .append("\n")
                .append(getString(R.string.rig_setup_connection_transport))
                .append(renderTransportKind(profile.transportKind()))
                .append("\n")
                .append(getString(R.string.rig_setup_connection_notes))
                .append(profile.setupNotes());
        if (profile.hasCapability(RigCapability.SERIAL_CAT)) {
            builder.append(getString(R.string.rig_setup_connection_next_serial_prefix));
            if (settings.serialCatProtocolFamily() == CatProtocolFamily.ICOM_CIV) {
                builder.append(getString(R.string.rig_setup_connection_next_serial_civ));
            }
            builder.append(getString(R.string.rig_setup_connection_next_serial_suffix));
            if (routeMode == OperateRouteModeStore.Mode.HYBRID_PHONE_RX) {
                builder.append(getString(R.string.rig_setup_connection_hybrid_enabled));
            } else if (settings.serialCatProtocolFamily() == CatProtocolFamily.YAESU_STYLE) {
                builder.append(getString(R.string.rig_setup_connection_yaesu_hybrid_combo));
            }
            if (!developerModeEnabled) {
                builder.append(getString(R.string.rig_setup_connection_deep_hidden));
            } else if (!openDeveloperLabs) {
                builder.append(getString(R.string.rig_setup_connection_deep_entry));
            }
        } else if (profile.hasCapability(RigCapability.NETWORK_CAT)) {
            builder.append(getString(R.string.rig_setup_connection_next_network));
            if (!developerModeEnabled) {
                builder.append(getString(R.string.rig_setup_connection_probe_hidden));
            } else if (!openDeveloperLabs) {
                builder.append(getString(R.string.rig_setup_connection_probe_entry));
            }
        } else if (profile.hasCapability(RigCapability.KEY_LINE_CONTROL)) {
            builder.append(getString(R.string.rig_setup_connection_next_usb));
        } else if (profile.hasCapability(RigCapability.AUDIO_VOX)) {
            builder.append(getString(R.string.rig_setup_connection_next_vox));
        } else {
            builder.append(getString(R.string.rig_setup_connection_next_generic));
        }
        return builder.toString();
    }

    private String renderSelectedProfileStatus(
            RigProfile selectedProfile,
            RigProfile pinnedProfile,
            RigProfileSettings settings,
            boolean hasSavedOverride,
            OperateRouteModeStore.Mode routeMode,
            boolean routeModeDirty
    ) {
        if (selectedProfile == null) {
            return getString(R.string.rig_setup_selected_none);
        }
        StringBuilder builder = new StringBuilder(getString(
                R.string.rig_setup_selected_preview,
                RigProfileConfigurationFormatter.renderCompactSummary(selectedProfile, settings),
                hasSavedOverride
                        ? getString(R.string.rig_setup_selected_source_saved)
                        : getString(R.string.rig_setup_selected_source_default),
                RigUiLabels.capabilitySummary(this, selectedProfile),
                selectedProfile.setupNotes()
        ));
        builder.append(getString(
                R.string.rig_setup_selected_route_mode,
                renderRouteModeLabel(routeMode)
        ));
        if (routeModeDirty) {
            builder.append(getString(R.string.rig_setup_selected_route_mode_pending));
        }
        if (pinnedProfile == null) {
            builder.append(getString(R.string.rig_setup_selected_pinned_none));
        } else if (pinnedProfile.id().equals(selectedProfile.id())) {
            builder.append(getString(R.string.rig_setup_selected_pinned_same));
        } else {
            builder.append(getString(R.string.rig_setup_selected_pinned_other, pinnedProfile.displayName()));
        }
        return builder.toString();
    }

    private String renderProfileConfigStatus(
            RigProfile profile,
            RigProfileSettings settings,
            boolean hasSavedOverride
    ) {
        if (profile == null) {
            return getString(R.string.rig_setup_profile_config_none);
        }
        StringBuilder builder = new StringBuilder(getString(
                R.string.rig_setup_profile_config_summary,
                RigProfileConfigurationFormatter.renderCompactSummary(profile, settings),
                hasSavedOverride
                        ? getString(R.string.rig_setup_profile_config_saved_override)
                        : getString(R.string.rig_setup_profile_config_default)
        ));
        if (!configStatusMessage.isEmpty()
                && configStatusProfileId != null
                && configStatusProfileId.equals(profile.id())) {
            builder.append(getString(R.string.rig_setup_profile_config_recent, configStatusMessage));
        }
        return builder.toString();
    }

    private RigProfileSettings readSettingsFromEditor() {
        RigProfileSettings existing = rigSelectionStore.loadSettings(selectedProfile());
        String serialPortHint = normalizedText(binding.serialCatPortHintEditText.getText() == null
                ? null
                : binding.serialCatPortHintEditText.getText().toString());
        String serialCatKeyingPortHint = selectedSerialCatKeyingPortHint();
        return new RigProfileSettings(
                existing.defaultWpm(),
                existing.defaultToneFrequencyHz(),
                selectedUsbKeyLine(),
                normalizedText(binding.usbPreferredDeviceNameEditText.getText() == null
                        ? null
                        : binding.usbPreferredDeviceNameEditText.getText().toString()),
                selectedCatProtocol(binding.serialCatProtocolSpinner.getSelectedItem(), CatProtocolFamily.GENERIC),
                parsePositiveInt(binding.serialCatBaudRateEditText.getText() == null
                        ? null
                        : binding.serialCatBaudRateEditText.getText().toString(), 9600),
                serialPortHint,
                selectedSerialCatKeyLine(),
                serialCatKeyingPortHint,
                selectedSerialCatKeyingPolarity(),
                binding.serialCatAssertRtsCheckBox.isChecked(),
                binding.serialCatAssertDtrCheckBox.isChecked(),
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

    private void persistSelectedRouteMode(@Nullable RigProfile profile) {
        if (profile == null) {
            return;
        }
        operateRouteModeStore.setMode(profile, readSelectedRouteMode(profile));
    }

    private OperateRouteModeStore.Mode readSelectedRouteMode(@Nullable RigProfile profile) {
        if (!operateRouteModeStore.supportsHybridPhoneRx(profile)) {
            return OperateRouteModeStore.Mode.STANDARD_AUTO;
        }
        Object selectedItem = binding.routeModeSpinner.getSelectedItem();
        if (selectedItem instanceof OperateRouteModeStore.Mode) {
            return operateRouteModeStore.sanitize(
                    profile,
                    (OperateRouteModeStore.Mode) selectedItem
            );
        }
        return operateRouteModeStore.mode(profile);
    }

    private void bindRouteModeLabel(View view, @Nullable OperateRouteModeStore.Mode mode) {
        if (!(view instanceof android.widget.TextView)) {
            return;
        }
        ((android.widget.TextView) view).setText(mode == null
                ? ""
                : getString(mode.displayNameResId()));
    }

    private String renderRouteModeLabel(@Nullable OperateRouteModeStore.Mode mode) {
        OperateRouteModeStore.Mode safeMode = mode == null
                ? OperateRouteModeStore.Mode.STANDARD_AUTO
                : mode;
        return getString(safeMode.displayNameResId());
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

    private SerialKeyerTxOutput.KeyLine selectedSerialCatKeyLine() {
        Object selectedItem = binding.serialCatKeyLineSpinner.getSelectedItem();
        if (selectedItem instanceof SerialKeyerTxOutput.KeyLine) {
            return (SerialKeyerTxOutput.KeyLine) selectedItem;
        }
        return SerialKeyerTxOutput.KeyLine.RTS;
    }

    private String selectedSerialCatKeyingPortHint() {
        int position = binding.serialCatKeyingPortSpinner.getSelectedItemPosition();
        if (position < 0 || position >= serialCatKeyingPortHints.size()) {
            return null;
        }
        return serialCatKeyingPortHints.get(position);
    }

    private KeyingPolarity selectedSerialCatKeyingPolarity() {
        Object selectedItem = binding.serialCatKeyingPolaritySpinner.getSelectedItem();
        if (selectedItem instanceof KeyingPolarity) {
            return (KeyingPolarity) selectedItem;
        }
        return KeyingPolarity.ACTIVE_HIGH;
    }

    private SerialCatRigControlAdapter.TimingLabOrder selectedTimingLabAssertOrder() {
        Object selectedItem = binding.serialCatTimingAssertOrderSpinner.getSelectedItem();
        if (selectedItem instanceof SerialCatRigControlAdapter.TimingLabOrder) {
            return (SerialCatRigControlAdapter.TimingLabOrder) selectedItem;
        }
        return SerialCatRigControlAdapter.TimingLabOrder.SIMULTANEOUS;
    }

    private SerialCatRigControlAdapter.TimingLabReleaseOrder selectedTimingLabReleaseOrder() {
        Object selectedItem = binding.serialCatTimingReleaseOrderSpinner.getSelectedItem();
        if (selectedItem instanceof SerialCatRigControlAdapter.TimingLabReleaseOrder) {
            return (SerialCatRigControlAdapter.TimingLabReleaseOrder) selectedItem;
        }
        return SerialCatRigControlAdapter.TimingLabReleaseOrder.TOGETHER;
    }

    private SerialCatRigControlAdapter.ShortPulseLabPreset selectedShortPulsePreset() {
        Object selectedItem = binding.serialCatShortPulsePresetSpinner.getSelectedItem();
        if (selectedItem instanceof SerialCatRigControlAdapter.ShortPulseLabPreset) {
            return (SerialCatRigControlAdapter.ShortPulseLabPreset) selectedItem;
        }
        return SerialCatRigControlAdapter.ShortPulseLabPreset.SINGLE_E;
    }

    private String renderReadinessSummary(List<RigTransport> transports, List<RigProfile> profiles) {
        int readyTransportCount = 0;
        for (RigTransport transport : transports) {
            if (transport.isReady(this)) {
                readyTransportCount += 1;
            }
        }
        int readyProfileCount = 0;
        for (RigProfile profile : profiles) {
            if (profile.supportLevel() == RigSupportLevel.BENCH_READY) {
                readyProfileCount += 1;
            }
        }
        return getString(
                R.string.rig_setup_readiness_summary,
                readyTransportCount,
                transports.size(),
                readyProfileCount,
                profiles.size()
        );
    }

    private String renderTransportSummary(List<RigTransport> transports) {
        StringBuilder builder = new StringBuilder();
        for (RigTransport transport : transports) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(getString(
                    R.string.rig_setup_transport_summary_item,
                    transport.displayName(),
                    renderTransportKind(transport.kind()),
                    getString(transport.isReady(this) ? R.string.status_yes : R.string.status_no),
                    normalizeRigMessage(transport.describeAvailability(this))
            ));
        }
        return builder.toString();
    }

    private String renderProfileSummary(List<RigProfile> profiles) {
        StringBuilder builder = new StringBuilder();
        for (RigProfile profile : profiles) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(getString(
                    R.string.rig_setup_profile_summary_item,
                    profile.displayName(),
                    RigUiLabels.supportLevel(this, profile.supportLevel()),
                    profile.vendorLabel(),
                    profile.modelLabel(),
                    RigUiLabels.transportKind(this, profile.transportKind()),
                    profile.adapterId(),
                    RigUiLabels.capabilitySummary(this, profile),
                    profile.summary(),
                    profile.setupNotes()
            ));
            if (!profile.knownConstraints().isEmpty()) {
                builder.append(getString(
                        R.string.rig_setup_profile_summary_constraints,
                        String.join(" | ", profile.knownConstraints())
                ));
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
        StringBuilder builder = new StringBuilder(getString(R.string.rig_setup_next_step_title));
        if (yaesuRigctldProfile != null) {
            builder.append(getString(R.string.rig_setup_next_step_yaesu, yaesuRigctldProfile.displayName()));
        }
        if (icomRigctldProfile != null) {
            builder.append(getString(R.string.rig_setup_next_step_icom, icomRigctldProfile.displayName()));
        }
        if (kenwoodRigctldProfile != null) {
            builder.append(getString(R.string.rig_setup_next_step_kenwood, kenwoodRigctldProfile.displayName()));
        }
        if (usbProfile != null) {
            builder.append(getString(R.string.rig_setup_next_step_usb, usbProfile.displayName()));
        }
        if (voxProfile != null) {
            builder.append(getString(R.string.rig_setup_next_step_vox, voxProfile.displayName()));
        }
        if (catProfile != null) {
            builder.append(getString(R.string.rig_setup_next_step_cat));
        }
        builder.append(getString(R.string.rig_setup_next_step_7));
        builder.append(getString(R.string.rig_setup_next_step_8));
        builder.append(getString(R.string.rig_setup_next_step_9));
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

    private String textValue(android.widget.EditText editText) {
        if (editText == null || editText.getText() == null) {
            return null;
        }
        return editText.getText().toString();
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

    private void requestUsbKeyerPermission() {
        RigProfile profile = selectedProfile();
        RigProfileSettings settings = readSettingsFromEditor();
        UsbSerialKeyerRigControlAdapter adapter = resolveUsbKeyerAdapter(profile, settings);
        if (adapter == null) {
            return;
        }
        usbKeyerPermissionInFlight = true;
        usbKeyerActionInFlight = true;
        usbKeyerStatusProfileId = profile == null ? null : profile.id();
        usbKeyerStatusMessage = getString(R.string.rig_setup_usb_keyer_permission_preparing);
        refreshSelectedProfileViews();
        try {
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    this,
                    0,
                    new Intent(ACTION_USB_KEYER_PERMISSION).setPackage(getPackageName()),
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                            ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
                            : PendingIntent.FLAG_UPDATE_CURRENT
            );
            boolean requested = adapter.requestUsbPermission(pendingIntent);
            usbKeyerPermissionInFlight = false;
            usbKeyerActionInFlight = false;
            usbKeyerStatusMessage = requested
                    ? getString(R.string.rig_setup_usb_keyer_permission_requested)
                    : getString(
                            R.string.rig_setup_usb_keyer_permission_unavailable,
                            normalizeRigMessage(adapter.describeAvailability())
                    );
        } catch (RuntimeException exception) {
            usbKeyerPermissionInFlight = false;
            usbKeyerActionInFlight = false;
            usbKeyerStatusMessage = getString(
                    R.string.rig_setup_usb_keyer_permission_failed_before,
                    safeThrowableMessage(exception)
            );
        } catch (Throwable throwable) {
            Log.e(TAG, "USB keyer permission request crashed", throwable);
            usbKeyerPermissionInFlight = false;
            usbKeyerActionInFlight = false;
            usbKeyerStatusMessage = getString(
                    R.string.rig_setup_usb_keyer_permission_crashed,
                    safeThrowableMessage(throwable)
            );
        }
        refreshSelectedProfileViews();
    }

    private void testUsbKeyerPulse() {
        RigProfile profile = selectedProfile();
        RigProfileSettings settings = readSettingsFromEditor();
        UsbSerialKeyerRigControlAdapter adapter = resolveUsbKeyerAdapter(profile, settings);
        if (adapter == null) {
            return;
        }
        usbKeyerActionInFlight = true;
        usbKeyerStatusProfileId = profile == null ? null : profile.id();
        usbKeyerStatusMessage = getString(R.string.rig_setup_usb_keyer_pulse_running);
        refreshSelectedProfileViews();
        new Thread(() -> {
            final String resultMessage = runUsbKeyerPulse(adapter);
            runOnUiThread(() -> {
                usbKeyerActionInFlight = false;
                usbKeyerStatusProfileId = profile == null ? null : profile.id();
                usbKeyerStatusMessage = resultMessage;
                refreshSelectedProfileViews();
            });
        }, "cwcn-usb-keyer-pulse").start();
    }

    private void sendUsbKeyerTestText() {
        RigProfile profile = selectedProfile();
        RigProfileSettings settings = readSettingsFromEditor();
        UsbSerialKeyerRigControlAdapter adapter = resolveUsbKeyerAdapter(profile, settings);
        if (adapter == null) {
            return;
        }
        usbKeyerActionInFlight = true;
        usbKeyerTransmissionInFlight = true;
        usbKeyerStatusProfileId = profile == null ? null : profile.id();
        usbKeyerStatusMessage = getString(R.string.rig_setup_usb_keyer_text_running, "VVV");
        refreshSelectedProfileViews();
        new Thread(() -> {
            final String resultMessage = runUsbKeyerTestText(adapter, "VVV");
            runOnUiThread(() -> {
                usbKeyerActionInFlight = false;
                usbKeyerTransmissionInFlight = false;
                usbKeyerStatusProfileId = profile == null ? null : profile.id();
                usbKeyerStatusMessage = resultMessage;
                refreshSelectedProfileViews();
            });
        }, "cwcn-usb-keyer-text").start();
    }

    private void stopUsbKeyerText() {
        RigProfile profile = selectedProfile();
        RigProfileSettings settings = readSettingsFromEditor();
        UsbSerialKeyerRigControlAdapter adapter = resolveUsbKeyerAdapter(profile, settings);
        if (adapter == null) {
            return;
        }
        boolean stopped = adapter.stopTextTransmission();
        usbKeyerStatusProfileId = profile == null ? null : profile.id();
        usbKeyerStatusMessage = stopped
                ? getString(R.string.rig_setup_usb_keyer_stop_requested)
                : getString(R.string.rig_setup_usb_keyer_stop_unchanged);
        refreshSelectedProfileViews();
    }

    private String runUsbKeyerPulse(UsbSerialKeyerRigControlAdapter adapter) {
        try {
            boolean keyDown = adapter.keyDown();
            if (!keyDown) {
                return getString(
                        R.string.rig_setup_usb_keyer_pulse_unavailable,
                        normalizeRigMessage(adapter.describeAvailability())
                );
            }
            try {
                Thread.sleep(250L);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
            boolean keyUp = adapter.keyUp();
            return keyUp
                    ? getString(R.string.rig_setup_usb_keyer_pulse_done)
                    : getString(R.string.rig_setup_usb_keyer_pulse_release_failed);
        } catch (Throwable throwable) {
            Log.e(TAG, "USB key line pulse crashed", throwable);
            return getString(
                    R.string.rig_setup_usb_keyer_pulse_crashed,
                    safeThrowableMessage(throwable)
            );
        }
    }

    private String runUsbKeyerTestText(UsbSerialKeyerRigControlAdapter adapter, String text) {
        try {
            if (!adapter.supportsTextToCw()) {
                return getString(R.string.rig_setup_usb_keyer_text_unsupported);
            }
            boolean sent = adapter.sendText(text);
            org.bi9clt.cwcn.core.tx.CwTxPlaybackSnapshot snapshot = adapter.currentTxPlaybackSnapshot();
            if (sent) {
                return getString(R.string.rig_setup_usb_keyer_text_done, text);
            }
            if (snapshot != null && snapshot.state() == org.bi9clt.cwcn.core.tx.CwTxState.STOPPED) {
                return getString(R.string.rig_setup_usb_keyer_text_stopped, text);
            }
            if (snapshot != null && snapshot.state() == org.bi9clt.cwcn.core.tx.CwTxState.ERROR) {
                return getString(R.string.rig_setup_usb_keyer_text_error, text, snapshot.statusMessage());
            }
            return getString(
                    R.string.rig_setup_usb_keyer_text_incomplete,
                    text,
                    normalizeRigMessage(adapter.describeAvailability())
            );
        } catch (Throwable throwable) {
            Log.e(TAG, "USB keyer text test crashed", throwable);
            return getString(
                    R.string.rig_setup_usb_keyer_text_crashed,
                    text,
                    safeThrowableMessage(throwable)
            );
        }
    }

    private void syncUsbKeyerState(RigProfile profile, RigProfileSettings settings) {
        boolean usbVisible = profile != null && profile.hasCapability(RigCapability.KEY_LINE_CONTROL);
        if (!usbVisible) {
            return;
        }
        UsbSerialKeyerRigControlAdapter adapter = resolveUsbKeyerAdapter(profile, settings);
        if (adapter == null) {
            binding.requestUsbKeyerPermissionButton.setEnabled(false);
            binding.testUsbKeyerPulseButton.setEnabled(false);
            binding.sendUsbKeyerTestTextButton.setEnabled(false);
            binding.stopUsbKeyerTextButton.setEnabled(false);
            binding.usbKeyerStatusText.setText(R.string.rig_setup_usb_keyer_adapter_missing);
            return;
        }
        boolean hasTargetDevice = adapter.hasTargetDevice();
        boolean ready = adapter.isReady();
        boolean missingTarget = adapter.isPreferredDeviceMissing();
        String buttonLabel = ready
                ? getString(R.string.rig_setup_usb_keyer_ready)
                : hasTargetDevice
                ? getString(R.string.rig_setup_usb_keyer_request_permission)
                : getString(R.string.rig_setup_usb_keyer_insert_then_refresh);
        binding.requestUsbKeyerPermissionButton.setText(buttonLabel);
        binding.requestUsbKeyerPermissionButton.setEnabled(
                hasTargetDevice && !ready && !usbKeyerActionInFlight
        );
        binding.testUsbKeyerPulseButton.setEnabled(ready && !usbKeyerActionInFlight);
        binding.sendUsbKeyerTestTextButton.setEnabled(ready && !usbKeyerActionInFlight);
        binding.stopUsbKeyerTextButton.setEnabled(usbKeyerTransmissionInFlight);
        if (usbKeyerActionInFlight
                && profile != null
                && profile.id().equals(usbKeyerStatusProfileId)) {
            binding.usbKeyerStatusText.setText(usbKeyerStatusMessage);
            return;
        }
        if (usbKeyerStatusMessage != null
                && !usbKeyerStatusMessage.isEmpty()
                && profile != null
                && profile.id().equals(usbKeyerStatusProfileId)) {
            binding.usbKeyerStatusText.setText(usbKeyerStatusMessage);
            return;
        }
        StringBuilder builder = new StringBuilder();
        builder.append(getString(
                R.string.rig_setup_usb_keyer_summary,
                adapter.displayName(),
                normalizeRigMessage(adapter.describeAvailability()),
                settings.usbKeyLine(),
                hasRealUsbDeviceOption(adapter.availableDevices())
                        ? getString(R.string.rig_setup_usb_keyer_candidate_count, adapter.availableDevices().size())
                        : getString(R.string.rig_setup_usb_keyer_candidate_none),
                valueOrEmpty(settings.usbPreferredDeviceName()).isEmpty()
                        ? getString(R.string.rig_setup_usb_keyer_lock_auto)
                        : settings.usbPreferredDeviceName()
        ));
        if (missingTarget) {
            builder.append(getString(R.string.rig_setup_usb_keyer_hint_saved_missing));
        } else if (!hasTargetDevice) {
            builder.append(getString(R.string.rig_setup_usb_keyer_hint_none));
        } else if (!ready) {
            builder.append(getString(R.string.rig_setup_usb_keyer_hint_need_permission));
        } else {
            builder.append(getString(R.string.rig_setup_usb_keyer_hint_ready));
        }
        binding.usbKeyerStatusText.setText(builder.toString());
    }

    private boolean hasRealUsbDeviceOption(List<UsbSerialDeviceOption> devices) {
        for (UsbSerialDeviceOption device : devices) {
            if (!device.isAuto()) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private UsbSerialKeyerRigControlAdapter resolveUsbKeyerAdapter(
            @Nullable RigProfile profile,
            RigProfileSettings settings
    ) {
        if (profile == null || !profile.hasCapability(RigCapability.KEY_LINE_CONTROL)) {
            return null;
        }
        for (RigControlAdapter candidate : RigRegistry.defaultAdapters(this)) {
            if (!profile.adapterId().equals(candidate.id())) {
                continue;
            }
            if (!(candidate instanceof UsbSerialKeyerRigControlAdapter)) {
                continue;
            }
            UsbSerialKeyerRigControlAdapter adapter = (UsbSerialKeyerRigControlAdapter) candidate;
            adapter.setKeyLine(settings.usbKeyLine());
            adapter.selectDevice(settings.usbPreferredDeviceName());
            return adapter;
        }
        return null;
    }

    private String renderTransportKind(@Nullable RigTransport.TransportKind kind) {
        return RigUiLabels.transportKind(this, kind);
    }

    private String normalizeRigMessage(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim();
    }

    private String safeThrowableMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().trim().isEmpty()) {
            return throwable == null ? getString(R.string.rig_setup_unknown_error) : throwable.getClass().getSimpleName();
        }
        return throwable.getMessage().trim();
    }

    private static final class SweepStep {
        private final String portHint;
        private final SerialKeyerTxOutput.KeyLine keyLine;
        private final KeyingPolarity polarity;

        private SweepStep(String portHint, SerialKeyerTxOutput.KeyLine keyLine, KeyingPolarity polarity) {
            this.portHint = portHint;
            this.keyLine = keyLine;
            this.polarity = polarity;
        }

        private String renderLabel() {
            return portHint + " / " + keyLine.name() + " / " + polarity.toString();
        }
    }

    private void registerUsbPermissionReceiver() {
        usbPermissionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context, Intent intent) {
                String action = intent.getAction();
                if (!ACTION_SERIAL_CAT_USB_PERMISSION.equals(action)
                        && !ACTION_USB_KEYER_PERMISSION.equals(action)) {
                    return;
                }
                boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                RigProfile profile = selectedProfile();
                if (ACTION_USB_KEYER_PERMISSION.equals(action)) {
                    usbKeyerPermissionInFlight = false;
                    usbKeyerActionInFlight = false;
                    usbKeyerStatusProfileId = profile == null ? null : profile.id();
                    usbKeyerStatusMessage = granted
                            ? getString(R.string.rig_setup_usb_keyer_permission_granted)
                            : getString(R.string.rig_setup_usb_keyer_permission_denied);
                } else {
                    serialProbeProfileId = profile == null ? null : profile.id();
                    serialProbeMessage = granted
                            ? getString(R.string.rig_setup_serial_cat_permission_granted)
                            : getString(R.string.rig_setup_serial_cat_permission_denied);
                }
                refreshSelectedProfileViews();
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_SERIAL_CAT_USB_PERMISSION);
        filter.addAction(ACTION_USB_KEYER_PERMISSION);
        ContextCompat.registerReceiver(
                this,
                usbPermissionReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
    }

    private void consumeLaunchIntent(@Nullable Intent intent) {
        if (intent == null) {
            return;
        }
        openDeveloperLabs = intent.getBooleanExtra(EXTRA_OPEN_DEVELOPER_LABS, openDeveloperLabs);
        String openReason = intent.getStringExtra(EXTRA_OPEN_REASON);
        if (!OPEN_REASON_USB_ATTACH.equals(openReason)
                && !UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
            return;
        }
        String deviceName = intent.getStringExtra(EXTRA_USB_DEVICE_NAME);
        int vendorId = intent.getIntExtra(EXTRA_USB_VENDOR_ID, -1);
        int productId = intent.getIntExtra(EXTRA_USB_PRODUCT_ID, -1);
        StringBuilder builder = new StringBuilder(getString(R.string.rig_setup_launch_usb_attach));
        if (deviceName != null && !deviceName.trim().isEmpty()) {
            builder.append(": ").append(deviceName.trim());
        }
        if (vendorId >= 0 || productId >= 0) {
            builder.append(" (");
            if (vendorId >= 0) {
                builder.append(String.format(java.util.Locale.US, "VID %04X", vendorId));
            }
            if (vendorId >= 0 && productId >= 0) {
                builder.append(':');
            }
            if (productId >= 0) {
                builder.append(String.format(java.util.Locale.US, "PID %04X", productId));
            }
            builder.append(')');
        }
        builder.append(getString(R.string.rig_setup_launch_next_step));
        launchStatusMessage = builder.toString();
    }
}
