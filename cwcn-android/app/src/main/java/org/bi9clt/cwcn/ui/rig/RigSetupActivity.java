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

import org.bi9clt.cwcn.BuildConfig;
import org.bi9clt.cwcn.core.app.DeveloperModeStore;
import org.bi9clt.cwcn.core.rig.AndroidUsbProbedSerialKeyerPortFactory;
import org.bi9clt.cwcn.core.rig.AndroidUsbSerialCatSessionFactory;
import org.bi9clt.cwcn.core.rig.CatProtocolFamily;
import org.bi9clt.cwcn.core.rig.HamlibRigctldRigControlAdapter;
import org.bi9clt.cwcn.core.rig.KeyingPolarity;
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
import org.bi9clt.cwcn.databinding.ActivityRigSetupBinding;
import org.bi9clt.cwcn.ui.developer.DeveloperToolsActivity;

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
    private static final String TAG = "RigSetupActivity";

    private ActivityRigSetupBinding binding;
    private RigSelectionStore rigSelectionStore;
    private DeveloperModeStore developerModeStore;
    private boolean syncingProfileSelection;
    private String configStatusMessage = "";
    private String configStatusProfileId;
    private String serialProbeMessage = "";
    private String serialProbeProfileId;
    private boolean serialProbeInFlight;
    private String networkProbeMessage = "";
    private String networkProbeProfileId;
    private boolean networkProbeInFlight;
    private String launchStatusMessage = "";
    private BroadcastReceiver usbPermissionReceiver;
    private ArrayAdapter<String> serialCatPortAdapter;
    private ArrayAdapter<String> serialCatKeyingPortAdapter;
    private final List<String> serialCatPortOptions = new ArrayList<>();
    private final List<String> serialCatPortHints = new ArrayList<>();
    private final List<String> serialCatKeyingPortOptions = new ArrayList<>();
    private final List<String> serialCatKeyingPortHints = new ArrayList<>();
    private boolean syncingSerialCatPortSelection;
    private boolean syncingSerialCatKeyingPortSelection;
    private boolean syncingSettingsEditor;
    private String currentEditorProfileId;
    private boolean openDeveloperLabs;
    private static final String SERIAL_PORT_AUTO_OPTION = "Auto / first detected port";
    private static final String SERIAL_PORT_MANUAL_PREFIX = "Manual: ";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRigSetupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        rigSelectionStore = new RigSelectionStore(this);
        developerModeStore = new DeveloperModeStore(this);
        binding.versionText.setText("Rig Setup " + BuildConfig.VERSION_NAME);
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
        binding.openDeveloperToolsButton.setOnClickListener(view ->
                startActivity(new Intent(this, DeveloperToolsActivity.class)));
        binding.toggleDeveloperModeButton.setOnClickListener(view -> {
            developerModeStore.toggle();
            refreshUi();
        });
        binding.saveSelectedProfileButton.setOnClickListener(view -> saveSelectedProfile());
        binding.saveProfileConfigButton.setOnClickListener(view -> saveProfileConfig());
        binding.resetProfileConfigButton.setOnClickListener(view -> resetProfileConfig());
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
        binding.serialCatKeyLineSpinner.setAdapter(keyLineAdapter);

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
                if (SERIAL_PORT_AUTO_OPTION.equals(option) || selectedHint == null) {
                    binding.serialCatPortHintEditText.setText("");
                    return;
                }
                if (option.startsWith(SERIAL_PORT_MANUAL_PREFIX)) {
                    binding.serialCatPortHintEditText.setText(selectedHint);
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
    }

    private void refreshUi() {
        boolean developerModeEnabled = developerModeStore.isEnabled();
        List<RigTransport> transports = RigRegistry.defaultTransports();
        List<RigProfile> profiles = RigRegistry.defaultProfiles();
        binding.readinessSummaryText.setText(renderReadinessSummary(transports, profiles));
        boolean hasLaunchStatus = launchStatusMessage != null && !launchStatusMessage.isEmpty();
        binding.usbAttachStatusPanel.setVisibility(hasLaunchStatus ? View.VISIBLE : View.GONE);
        binding.usbAttachStatusText.setText(hasLaunchStatus ? launchStatusMessage : "");
        syncDeveloperModeViews(developerModeEnabled);
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
        currentEditorProfileId = profile == null ? null : profile.id();
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
        currentEditorProfileId = null;
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
        serialProbeProfileId = profile.id();
        serialProbeInFlight = true;
        serialProbeMessage = "Preparing USB serial CAT permission request...";
        refreshSelectedProfileViews();
        AndroidUsbSerialCatSessionFactory factory = new AndroidUsbSerialCatSessionFactory(this);
        try {
            String availabilityBefore = factory.describeAvailability(settings.serialCatPortHint());
            if (availabilityBefore != null
                    && availabilityBefore.toLowerCase(java.util.Locale.US).contains("permission is available")) {
                serialProbeMessage = "USB serial CAT permission is already available for the matched device.";
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
                    serialProbeMessage = "USB serial CAT permission request sent. If Android shows a system dialog, allow it and then retry the probe.";
                } else {
                    serialProbeMessage = "USB serial CAT permission could not be requested. Current USB status: "
                            + factory.describeAvailability(settings.serialCatPortHint());
                }
            }
        } catch (RuntimeException exception) {
            serialProbeMessage = "USB serial CAT permission path failed before Android could show a dialog: "
                    + exception.getMessage();
        } catch (Throwable throwable) {
            Log.e(TAG, "Serial CAT permission request crashed", throwable);
            serialProbeMessage = "USB serial CAT permission path crashed: "
                    + safeThrowableMessage(throwable);
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
        serialProbeMessage = "Testing serial CAT connection...";
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
                    serialProbeMessage = "Serial CAT probe crashed before completion: "
                            + safeThrowableMessage(throwable);
                    refreshSelectedProfileViews();
                });
                return;
            }
            runOnUiThread(() -> {
                serialProbeInFlight = false;
                serialProbeProfileId = profile == null ? null : profile.id();
                serialProbeMessage = result.message();
                refreshSelectedProfileViews();
            });
        }, "cwcn-serial-cat-probe").start();
    }

    private void testSerialCatPttPulse() {
        RigProfile profile = selectedProfile();
        RigProfileSettings settings = readSettingsFromEditor();
        serialProbeInFlight = true;
        serialProbeProfileId = profile == null ? null : profile.id();
        serialProbeMessage = "Running legacy native serial CAT TX/PTT pulse test...";
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
                    serialProbeMessage = "Native serial CAT TX/PTT pulse crashed before completion: "
                            + safeThrowableMessage(throwable);
                    refreshSelectedProfileViews();
                });
                return;
            }
            runOnUiThread(() -> {
                serialProbeInFlight = false;
                serialProbeProfileId = profile == null ? null : profile.id();
                serialProbeMessage = result.message();
                refreshSelectedProfileViews();
            });
        }, "cwcn-serial-cat-ptt").start();
    }

    private void testSerialCatKeyingPulse() {
        RigProfile profile = selectedProfile();
        RigProfileSettings settings = readSettingsFromEditor();
        serialProbeInFlight = true;
        serialProbeProfileId = profile == null ? null : profile.id();
        serialProbeMessage = "Running dedicated keying port pulse test...";
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
                    serialProbeMessage = "Dedicated keying pulse crashed before completion: "
                            + safeThrowableMessage(throwable);
                    refreshSelectedProfileViews();
                });
                return;
            }
            runOnUiThread(() -> {
                serialProbeInFlight = false;
                serialProbeProfileId = profile == null ? null : profile.id();
                serialProbeMessage = result.message();
                refreshSelectedProfileViews();
            });
        }, "cwcn-serial-keying-pulse").start();
    }

    private void testSerialCatKeyingHold() {
        RigProfile profile = selectedProfile();
        RigProfileSettings settings = readSettingsFromEditor();
        serialProbeInFlight = true;
        serialProbeProfileId = profile == null ? null : profile.id();
        serialProbeMessage = "Running dedicated keying hold test. Watch whether TX stays active during the full 1.5s hold.";
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
                    serialProbeMessage = "Dedicated keying hold crashed before completion: "
                            + safeThrowableMessage(throwable);
                    refreshSelectedProfileViews();
                });
                return;
            }
            runOnUiThread(() -> {
                serialProbeInFlight = false;
                serialProbeProfileId = profile == null ? null : profile.id();
                serialProbeMessage = result.message();
                refreshSelectedProfileViews();
            });
        }, "cwcn-serial-keying-hold").start();
    }

    private void testSerialCatKeyingOpenClose() {
        RigProfile profile = selectedProfile();
        RigProfileSettings settings = readSettingsFromEditor();
        serialProbeInFlight = true;
        serialProbeProfileId = profile == null ? null : profile.id();
        serialProbeMessage = "Running keying port open/close test. No DTR/RTS change is requested; watch for any TX flash on open or close.";
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
                    serialProbeMessage = "Dedicated keying open/close test crashed before completion: "
                            + safeThrowableMessage(throwable);
                    refreshSelectedProfileViews();
                });
                return;
            }
            runOnUiThread(() -> {
                serialProbeInFlight = false;
                serialProbeProfileId = profile == null ? null : profile.id();
                serialProbeMessage = result.message();
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
        serialProbeMessage = "Running DTR timing lab. Watch TX, RF power, and sidetone together; this path now normalizes the lines, waits, asserts in order, holds, and releases in order.";
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
                    serialProbeMessage = "DTR timing lab crashed before completion: "
                            + safeThrowableMessage(throwable);
                    refreshSelectedProfileViews();
                });
                return;
            }
            runOnUiThread(() -> {
                serialProbeInFlight = false;
                serialProbeProfileId = profile == null ? null : profile.id();
                serialProbeMessage = result.message();
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
        serialProbeMessage = "Running short pulse lab. Focus on edge behavior now: TX latch, RF output, sidetone, and whether the rig drops back to RX between short elements.";
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
                    serialProbeMessage = "Short pulse lab crashed before completion: "
                            + safeThrowableMessage(throwable);
                    refreshSelectedProfileViews();
                });
                return;
            }
            runOnUiThread(() -> {
                serialProbeInFlight = false;
                serialProbeProfileId = profile == null ? null : profile.id();
                serialProbeMessage = result.message();
                refreshSelectedProfileViews();
            });
        }, "cwcn-serial-short-pulse-lab").start();
    }

    private void runSerialCatKeyingSweep() {
        RigProfile profile = selectedProfile();
        RigProfileSettings settings = readSettingsFromEditor();
        serialProbeInFlight = true;
        serialProbeProfileId = profile == null ? null : profile.id();
        serialProbeMessage = "Running keying sweep across all detected serial ports, lines, and polarities. Watch the radio and count the step that first triggers TX.";
        refreshSelectedProfileViews();
        new Thread(() -> {
            try {
                runSerialCatKeyingSweepWorker(profile, settings);
            } catch (Throwable throwable) {
                Log.e(TAG, "Serial keying sweep worker crashed", throwable);
                runOnUiThread(() -> {
                    serialProbeInFlight = false;
                    serialProbeProfileId = profile == null ? null : profile.id();
                    serialProbeMessage = "Keying sweep crashed before completion: "
                            + safeThrowableMessage(throwable);
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
                serialProbeMessage = "Keying sweep blocked: no detected USB serial ports were available to test.";
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
                serialProbeMessage = "Keying sweep step "
                        + stepNumber
                        + "/"
                        + totalSteps
                        + ": "
                        + step.renderLabel()
                        + ". Watch the radio now.";
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
            resultLines.add(stepNumber + ". " + step.renderLabel() + " -> " + result.message());
            Thread.sleep(320);
        }

        runOnUiThread(() -> {
            serialProbeInFlight = false;
            serialProbeProfileId = profile == null ? null : profile.id();
            serialProbeMessage = "Keying sweep completed. If the radio reacted, note the matching step below:\n"
                    + String.join("\n", resultLines);
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
        syncSerialCatPortOptions(profile, settings);
        updateConfigVisibility(profile, settings, developerModeEnabled);
        binding.resetProfileConfigButton.setEnabled(profile != null && hasSavedOverride);
        syncProfileActionButton(profile, pinnedProfile);
        syncSerialProbeState(profile, settings, developerModeEnabled);
        syncNetworkProbeState(profile, settings, developerModeEnabled);
        binding.connectionGuideText.setText(renderConnectionGuide(profile, settings, developerModeEnabled));
        binding.selectedProfileStatusText.setText(renderSelectedProfileStatus(profile, pinnedProfile, settings, hasSavedOverride));
        binding.profileConfigStatusText.setText(renderProfileConfigStatus(profile, settings, hasSavedOverride));
    }

    private void syncSettingsEditor(RigProfileSettings settings) {
        syncingSettingsEditor = true;
        binding.defaultWpmEditText.setText(String.valueOf(settings.defaultWpm()));
        binding.defaultToneFrequencyEditText.setText(String.valueOf(settings.defaultToneFrequencyHz()));
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

        binding.usbConfigGroup.setVisibility(usbVisible ? View.VISIBLE : View.GONE);
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
            serialCatPortOptions.add(SERIAL_PORT_AUTO_OPTION);
            serialCatPortHints.add(null);
            serialCatPortAdapter.notifyDataSetChanged();
            binding.serialCatPortSpinner.setSelection(0, false);
            serialCatKeyingPortOptions.add(SERIAL_PORT_AUTO_OPTION);
            serialCatKeyingPortHints.add(null);
            serialCatKeyingPortAdapter.notifyDataSetChanged();
            binding.serialCatKeyingPortSpinner.setSelection(0, false);
            syncingSerialCatPortSelection = false;
            syncingSerialCatKeyingPortSelection = false;
            return;
        }

        List<String> detectedHints = new AndroidUsbSerialCatSessionFactory(this).listDetectedPortHints();
        populateSerialPortSpinner(
                serialCatPortOptions,
                serialCatPortHints,
                serialCatPortAdapter,
                binding.serialCatPortSpinner,
                detectedHints,
                settings.serialCatPortHint()
        );
        populateSerialPortSpinner(
                serialCatKeyingPortOptions,
                serialCatKeyingPortHints,
                serialCatKeyingPortAdapter,
                binding.serialCatKeyingPortSpinner,
                detectedHints,
                settings.serialCatKeyingPortHint()
        );
        syncingSerialCatPortSelection = false;
        syncingSerialCatKeyingPortSelection = false;
    }

    private void populateSerialPortSpinner(
            List<String> options,
            List<String> hints,
            ArrayAdapter<String> adapter,
            android.widget.Spinner spinner,
            List<String> detectedHints,
            String currentHint
    ) {
        options.add(SERIAL_PORT_AUTO_OPTION);
        hints.add(null);
        for (String detectedHint : detectedHints) {
            options.add(renderSerialCatPortLabel(detectedHint));
            hints.add(detectedHint);
        }
        if (currentHint != null && !currentHint.isEmpty() && !detectedHints.contains(currentHint)) {
            options.add(SERIAL_PORT_MANUAL_PREFIX + currentHint);
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

    private String renderSerialCatPortLabel(String portHint) {
        if (portHint == null || portHint.trim().isEmpty()) {
            return "Detected serial port";
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
        return "USB " + shortDevice + " · Port " + portPart;
    }

    private void syncProfileActionButton(RigProfile selectedProfile, RigProfile pinnedProfile) {
        if (selectedProfile == null) {
            binding.saveSelectedProfileButton.setText("Use This Rig Path");
            binding.saveSelectedProfileButton.setEnabled(false);
            return;
        }
        binding.saveSelectedProfileButton.setEnabled(true);
        if (pinnedProfile != null && pinnedProfile.id().equals(selectedProfile.id())) {
            binding.saveSelectedProfileButton.setText("This Rig Path Is Active");
        } else {
            binding.saveSelectedProfileButton.setText("Use This Rig Path");
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
                        ? "Probe host/port reachability and ask rigctld for its rig info without starting TX."
                        : "Use Test rigctld Connection to verify that the configured host/port is reachable."
                : "Connection probe is currently available only when the network CAT family is set to Hamlib rigctld.");
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
        binding.testSerialCatKeyingButton.setEnabled(
                yaesuSelected
                        && settings.serialCatKeyingPortHint() != null
                        && !serialProbeInFlight
        );
        binding.holdSerialCatKeyingButton.setEnabled(
                yaesuSelected
                        && settings.serialCatKeyingPortHint() != null
                        && !serialProbeInFlight
        );
        binding.openCloseSerialCatKeyingPortButton.setEnabled(
                yaesuSelected
                        && settings.serialCatKeyingPortHint() != null
                        && !serialProbeInFlight
        );
        binding.runSerialCatTimingLabButton.setEnabled(
                yaesuSelected
                        && settings.serialCatKeyingPortHint() != null
                        && !serialProbeInFlight
        );
        binding.runSerialCatShortPulseLabButton.setEnabled(
                yaesuSelected
                        && settings.serialCatKeyingPortHint() != null
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
                ? "No serial CAT USB port is detected yet."
                : detectedPorts.size() == 1
                ? "Detected serial port: " + detectedPorts.get(0) + "."
                : "Detected " + detectedPorts.size() + " serial CAT ports. Choose one from the port picker before testing.";
        if (!developerLabsVisible) {
            binding.serialCatProbeStatusText.setText(yaesuSelected || icomSelected || kenwoodSelected
                    ? pickerHint + " Use Test Serial CAT Connection for the minimal handshake check. Open Developer Tools for PTT/keying bench controls."
                    : pickerHint + " Serial CAT connection testing is available for Yaesu-style CAT, Icom CI-V, and Kenwood-style CAT.");
            return;
        }
        binding.serialCatProbeStatusText.setText(yaesuSelected
                ? pickerHint + " Yaesu note: Pulse Legacy CAT TX/PTT still exercises the old CAT TX1/TX0 path. For DTR/RTS validation, use Pulse Keying Port (DTR/RTS). If short E/T/EEE/VVV behavior is the issue, use Short Pulse Lab instead of the long hold test. FT-710 bench note: when the radio menu is set to PC KEYING = DTR, RTS + DTR is the current accepted working combination; RTS-only may show TX/RF without normal sidetone, and DTR-only is currently treated as non-working."
                : icomSelected
                        ? settings.serialCatCivAddressHex() == null
                                ? pickerHint + " Icom CI-V probe is available. Set the CI-V address first, then query a safe transceiver-ID response. After that, a short CI-V PTT pulse can be used as the next smoke test."
                                : pickerHint + " Icom CI-V probe is available. After the transceiver-ID query looks good, use the short CI-V PTT pulse as the next smoke test."
                        : kenwoodSelected
                                ? pickerHint + " Kenwood-style CAT probe is available. Start with a safe ASCII read such as ID;/FA;/IF; before any TX-side work."
                                : pickerHint + " Serial CAT probe is currently implemented for Yaesu-style CAT, Icom CI-V, and Kenwood-style CAT first.");
    }

    private void syncDeveloperModeViews(boolean enabled) {
        binding.titleText.setText(openDeveloperLabs ? "Rig Bench" : "Rig Setup");
        binding.subtitleText.setText(openDeveloperLabs
                ? "Bench mode keeps protocol probes and timing labs together so normal rig setup can stay clean."
                : "Choose your radio path, save the connection defaults, and keep bench tools outside the main setup flow.");
        binding.developerModeStatusText.setText(enabled
                ? openDeveloperLabs
                        ? "Developer mode is enabled.\nRig Setup is currently opened from Developer Tools, so protocol probes and bench controls are visible."
                        : "Developer mode is enabled.\nRig Setup still stays focused on saved rig configuration. Open Developer Tools when you need protocol probes and bench controls."
                : "Developer mode is disabled.\nRig Setup stays focused on profile selection and saved defaults, while protocol-level probes and engineering tools are folded away.");
        binding.toggleDeveloperModeButton.setText(enabled
                ? "Disable Developer Mode"
                : "Enable Developer Mode");
        binding.developerQuickActionsPanel.setVisibility(openDeveloperLabs ? View.GONE : View.VISIBLE);
        int developerSummaryVisibility = enabled && openDeveloperLabs ? View.VISIBLE : View.GONE;
        binding.readinessSummaryPanel.setVisibility(developerSummaryVisibility);
        binding.transportSummaryPanel.setVisibility(developerSummaryVisibility);
        binding.profileSummaryPanel.setVisibility(developerSummaryVisibility);
        binding.nextStepPanel.setVisibility(developerSummaryVisibility);
    }

    private String renderConnectionGuide(
            RigProfile profile,
            RigProfileSettings settings,
            boolean developerModeEnabled
    ) {
        if (profile == null) {
            return "1. Choose the radio family you plan to use.\n2. Save it as your pinned rig path.\n3. Then fill only the connection details that match your setup.";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Selected rig: ")
                .append(profile.displayName())
                .append("\nWhat this path is for: ")
                .append(profile.summary())
                .append("\nTransport you will use: ")
                .append(profile.transportKind().name())
                .append("\nRecommended setup note: ")
                .append(profile.setupNotes());
        if (profile.hasCapability(RigCapability.SERIAL_CAT)) {
            builder.append("\nNext: confirm baud rate");
            if (settings.serialCatProtocolFamily() == CatProtocolFamily.ICOM_CIV) {
                builder.append(" and the CI-V address");
            }
            builder.append(", choose the CAT port");
            builder.append(", then choose the CW keying line and keying port if the radio exposes a separate keying endpoint, then save this rig path.");
            if (!developerModeEnabled) {
                builder.append("\nBench probes stay hidden until developer mode is enabled.");
            } else if (!openDeveloperLabs) {
                builder.append("\nBench probes are now collected under Developer Tools instead of this main setup flow.");
            }
        } else if (profile.hasCapability(RigCapability.NETWORK_CAT)) {
            builder.append("\nNext: fill the host and port for the radio bridge, then save this rig path.");
            if (!developerModeEnabled) {
                builder.append("\nThe lightweight network probe stays behind developer mode.");
            } else if (!openDeveloperLabs) {
                builder.append("\nThe lightweight network probe is available from Developer Tools.");
            }
        } else if (profile.hasCapability(RigCapability.KEY_LINE_CONTROL)) {
            builder.append("\nNext: confirm the keying route you expect to use, then save the profile defaults.");
        } else if (profile.hasCapability(RigCapability.AUDIO_VOX)) {
            builder.append("\nNext: save the audio defaults here, then tune VOX delay and gain on the radio.");
        } else {
            builder.append("\nNext: save the defaults here and continue with the matching operating screen.");
        }
        return builder.toString();
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
            builder.append("\nPinned default: ")
                    .append(pinnedProfile.displayName())
                    .append(" (press Use This Rig Path to replace it)");
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
        String serialPortHint = normalizedText(binding.serialCatPortHintEditText.getText() == null
                ? null
                : binding.serialCatPortHintEditText.getText().toString());
        String serialCatKeyingPortHint = selectedSerialCatKeyingPortHint();
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

    private String safeThrowableMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().trim().isEmpty()) {
            return throwable == null ? "unknown failure" : throwable.getClass().getSimpleName();
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
        StringBuilder builder = new StringBuilder("USB device attach detected");
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
        builder.append(". Next: pick the Serial CAT family, then press Request Serial CAT USB Permission.");
        launchStatusMessage = builder.toString();
    }
}
