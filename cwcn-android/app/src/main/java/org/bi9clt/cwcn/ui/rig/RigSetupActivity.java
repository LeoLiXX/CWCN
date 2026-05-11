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
    private ArrayAdapter<String> serialCatPortAdapter;
    private ArrayAdapter<String> serialCatKeyingPortAdapter;
    private ArrayAdapter<UsbSerialDeviceOption> usbDeviceAdapter;
    private final List<String> serialCatPortOptions = new ArrayList<>();
    private final List<String> serialCatPortHints = new ArrayList<>();
    private final List<String> serialCatKeyingPortOptions = new ArrayList<>();
    private final List<String> serialCatKeyingPortHints = new ArrayList<>();
    private boolean syncingUsbDeviceSelection;
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
        boolean developerLabsVisible = developerModeEnabled && openDeveloperLabs;
        List<RigTransport> transports = RigRegistry.defaultTransports();
        List<RigProfile> profiles = visibleProfiles(developerLabsVisible);
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

    private List<RigProfile> visibleProfiles(boolean developerLabsVisible) {
        List<RigProfile> allProfiles = RigRegistry.defaultProfiles();
        if (developerLabsVisible) {
            return allProfiles;
        }
        ArrayList<RigProfile> filtered = new ArrayList<>();
        for (RigProfile profile : allProfiles) {
            if (profile.supportLevel() == RigSupportLevel.DEBUG_ONLY) {
                continue;
            }
            filtered.add(profile);
        }
        return filtered;
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
        serialProbeMessage = "正在运行旧版 CAT TX/PTT 脉冲验证...";
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
                    serialProbeMessage = "旧版 CAT TX/PTT 脉冲验证在完成前异常退出："
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
        serialProbeMessage = "正在运行独立键控口脉冲验证...";
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
                    serialProbeMessage = "独立键控口脉冲验证在完成前异常退出："
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
        serialProbeMessage = "正在运行独立键控保持验证。请观察 1.5 秒保持期间 TX 是否持续有效。";
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
                    serialProbeMessage = "独立键控保持验证在完成前异常退出："
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
        serialProbeMessage = "正在运行键控口开关验证。不主动切换 DTR/RTS，请观察打开或关闭时是否出现异常 TX 闪动。";
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
                    serialProbeMessage = "键控口开关验证在完成前异常退出："
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
        serialProbeMessage = "正在运行 DTR 时序实验。请同时观察 TX、RF 功率和侧音；这条路径会先归一化控制线，再按顺序拉高、保持、释放。";
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
                    serialProbeMessage = "DTR 时序实验在完成前异常退出："
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
        serialProbeMessage = "正在运行短脉冲实验。请重点观察边沿行为：TX 锁存、RF 输出、侧音，以及短元素之间是否会掉回 RX。";
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
                    serialProbeMessage = "短脉冲实验在完成前异常退出："
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
        syncUsbDeviceOptions(profile, settings);
        syncSerialCatPortOptions(profile, settings);
        updateConfigVisibility(profile, settings, developerModeEnabled);
        binding.resetProfileConfigButton.setEnabled(profile != null && hasSavedOverride);
        syncProfileActionButton(profile, pinnedProfile);
        syncUsbKeyerState(profile, settings);
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
                    ? pickerHint + " 现在只保留最小握手检查。更深入的控制线实验已移到开发工具入口。"
                    : pickerHint + " 目前串口 CAT 连接验证已优先接到 Yaesu-style、Icom CI-V 和 Kenwood-style。");
            return;
        }
        binding.serialCatProbeStatusText.setText(yaesuSelected
                ? pickerHint + " Yaesu 说明：旧 CAT TX/PTT 脉冲仍在覆盖早期 TX1/TX0 路径；如果你要验证 DTR/RTS，请优先使用独立键控口脉冲。若问题集中在短 E/T/EEE/VVV 行为，请优先用短脉冲实验，而不是长保持验证。FT-710 当前经验是：当电台菜单设为 PC KEYING = DTR 时，RTS + DTR 仍是当前接受度最高的组合；仅 RTS 可能出现 TX/RF 但侧音不正常，仅 DTR 目前仍视作不可用。"
                : icomSelected
                        ? settings.serialCatCivAddressHex() == null
                                ? pickerHint + " Icom CI-V 已可探测。先设置 CI-V 地址，再发起一次安全的收发机 ID 查询，之后再用短 CI-V PTT 脉冲做下一步冒烟验证。"
                                : pickerHint + " Icom CI-V 已可探测。确认收发机 ID 查询正常后，再用短 CI-V PTT 脉冲做下一步冒烟验证。"
                        : kenwoodSelected
                                ? pickerHint + " Kenwood-style CAT 已可探测。建议先做 ID;/FA;/IF; 这类安全 ASCII 读操作，再进入 TX 侧验证。"
                                : pickerHint + " 串口 CAT 探测目前优先接入 Yaesu-style、Icom CI-V 和 Kenwood-style。");
    }

    private void syncDeveloperModeViews(boolean enabled) {
        boolean developerLabsVisible = enabled && openDeveloperLabs;
        binding.titleText.setText(developerLabsVisible ? "电台开发实验" : "电台配置");
        binding.subtitleText.setText(developerLabsVisible
                ? "扩展探测、控制线实验和时序验证都集中在这里，避免污染正式配置路径。"
                : "在这里选择正式电台路径并保存连接默认值；更深入的诊断实验放到开发工具入口。");
        int developerSummaryVisibility = developerLabsVisible ? View.VISIBLE : View.GONE;
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
                builder.append("\n更深入的协议验证在正常模式下会保持隐藏。");
            } else if (!openDeveloperLabs) {
                builder.append("\n更深入的协议验证已收纳到开发工具入口。");
            }
        } else if (profile.hasCapability(RigCapability.NETWORK_CAT)) {
            builder.append("\nNext: fill the host and port for the radio bridge, then save this rig path.");
            if (!developerModeEnabled) {
                builder.append("\n连接探测在正常模式下会保持隐藏。");
            } else if (!openDeveloperLabs) {
                builder.append("\n连接探测已收纳到开发工具入口。");
            }
        } else if (profile.hasCapability(RigCapability.KEY_LINE_CONTROL)) {
            builder.append("\nNext: choose RTS or DTR, optionally lock the preferred USB device, request permission if Android asks for it, then save the profile defaults.");
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
        int readyProfileCount = 0;
        for (RigProfile profile : profiles) {
            if (profile.supportLevel() == RigSupportLevel.BENCH_READY) {
                readyProfileCount += 1;
            }
        }
        return "可用传输层：" + readyTransportCount + "/" + transports.size()
                + "\n当前可验证路径：" + readyProfileCount + "/" + profiles.size()
                + "\n当前策略：先把可复用的家族路径接稳，再逐步补具体机型。"
                + "\n开发实验能力继续保留，但它们是辅助手段，不再和正式配置路径混在一起。";
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
            builder.append("\n1. 如果你当前要接 Yaesu FT 系列，优先从 ")
                    .append(yaesuRigctldProfile.displayName())
                    .append(" 开始，这样在原生串口 CAT 继续收口期间，CWCN 仍可先复用现有 rigctld TX 路径。");
        }
        if (icomRigctldProfile != null) {
            builder.append("\n2. Icom 家族当前优先选择 ")
                    .append(icomRigctldProfile.displayName())
                    .append(" 作为第一条正式路径。");
        }
        if (kenwoodRigctldProfile != null) {
            builder.append("\n3. Kenwood 家族当前优先选择 ")
                    .append(kenwoodRigctldProfile.displayName())
                    .append(" 作为第一条正式路径。");
        }
        if (usbProfile != null) {
            builder.append("\n4. 把 ").append(usbProfile.displayName())
                    .append(" 做稳，作为第一条真正可落地的有线控制路径。");
        }
        if (voxProfile != null) {
            builder.append("\n5. 保留 ").append(voxProfile.displayName())
                    .append(" 作为兼容兜底路径。");
        }
        if (catProfile != null) {
            builder.append("\n6. 在继续接入更深的 Yaesu/Icom/Kenwood 原生机型逻辑之前，先保持可复用 CAT 结构稳定。");
        }
        builder.append("\n7. Rig Setup 里已经具备 Yaesu-style、Icom CI-V 和 Kenwood-style 的原生串口 CAT 基础探测能力。");
        builder.append("\n8. 开发实验能力继续保留，但它们应被视为辅助工程路径，而不是主用户流程。");
        builder.append("\n9. 下一步原生工作重点不再是 schema，而是在共享探测/会话接缝之上，继续挂接可控的家族级串口 CAT 行为。");
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
        usbKeyerStatusMessage = "Preparing USB keyer permission request...";
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
                    ? "USB keyer permission request sent. If Android shows a system dialog, allow it and then return here."
                    : "USB keyer permission could not be requested. Current status: " + adapter.describeAvailability();
        } catch (RuntimeException exception) {
            usbKeyerPermissionInFlight = false;
            usbKeyerActionInFlight = false;
            usbKeyerStatusMessage = "USB keyer permission path failed before Android could show a dialog: "
                    + safeThrowableMessage(exception);
        } catch (Throwable throwable) {
            Log.e(TAG, "USB keyer permission request crashed", throwable);
            usbKeyerPermissionInFlight = false;
            usbKeyerActionInFlight = false;
            usbKeyerStatusMessage = "USB keyer permission path crashed: " + safeThrowableMessage(throwable);
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
        usbKeyerStatusMessage = "Running USB key line pulse test...";
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
        usbKeyerStatusMessage = "Sending USB test text 'VVV'...";
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
                ? "Stop requested for the active USB keyer transmission..."
                : "Stop request did not change the USB keyer state.";
        refreshSelectedProfileViews();
    }

    private String runUsbKeyerPulse(UsbSerialKeyerRigControlAdapter adapter) {
        try {
            boolean keyDown = adapter.keyDown();
            if (!keyDown) {
                return "USB key line pulse could not start. Current status: "
                        + adapter.describeAvailability();
            }
            try {
                Thread.sleep(250L);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
            boolean keyUp = adapter.keyUp();
            return keyUp
                    ? "USB key line pulse completed. Watch whether the rig actually keyed for a short burst."
                    : "USB key line asserted, but release failed. Review the USB route before transmitting.";
        } catch (Throwable throwable) {
            Log.e(TAG, "USB key line pulse crashed", throwable);
            return "USB key line pulse crashed before completion: "
                    + safeThrowableMessage(throwable);
        }
    }

    private String runUsbKeyerTestText(UsbSerialKeyerRigControlAdapter adapter, String text) {
        try {
            if (!adapter.supportsTextToCw()) {
                return "This USB keyer route does not support text-to-CW transmission.";
            }
            boolean sent = adapter.sendText(text);
            org.bi9clt.cwcn.core.tx.CwTxPlaybackSnapshot snapshot = adapter.currentTxPlaybackSnapshot();
            if (sent) {
                return "USB test text '" + text + "' completed. Watch whether the rig sent the full pattern cleanly.";
            }
            if (snapshot != null && snapshot.state() == org.bi9clt.cwcn.core.tx.CwTxState.STOPPED) {
                return "USB test text '" + text + "' was stopped before completion.";
            }
            if (snapshot != null && snapshot.state() == org.bi9clt.cwcn.core.tx.CwTxState.ERROR) {
                return "USB test text '" + text + "' ended with an error: " + snapshot.statusMessage();
            }
            return "USB test text '" + text + "' could not complete. Current status: "
                    + adapter.describeAvailability();
        } catch (Throwable throwable) {
            Log.e(TAG, "USB keyer text test crashed", throwable);
            return "USB test text '" + text + "' crashed before completion: "
                    + safeThrowableMessage(throwable);
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
            binding.usbKeyerStatusText.setText("USB keyer adapter is not attached to this profile yet.");
            return;
        }
        boolean hasTargetDevice = adapter.hasTargetDevice();
        boolean ready = adapter.isReady();
        boolean missingTarget = adapter.isPreferredDeviceMissing();
        String buttonLabel = ready
                ? "USB Keyer Ready"
                : hasTargetDevice
                ? "Request USB Keyer Permission"
                : "Refresh After Plug-in";
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
        builder.append("USB keyer route: ")
                .append(adapter.displayName())
                .append("\n状态: ")
                .append(adapter.describeAvailability())
                .append("\n控制线: ")
                .append(settings.usbKeyLine())
                .append("\n设备选择: ")
                .append(hasRealUsbDeviceOption(adapter.availableDevices())
                        ? adapter.availableDevices().size() + " 个候选设备"
                        : "未发现候选设备")
                .append("\n设备锁定: ")
                .append(valueOrEmpty(settings.usbPreferredDeviceName()).isEmpty()
                        ? "自动选择"
                        : settings.usbPreferredDeviceName());
        if (missingTarget) {
            builder.append("\n提示: 已保存的目标设备当前没有连接。");
        } else if (!hasTargetDevice) {
            builder.append("\n提示: 还没有检测到可用的 USB serial keyer 设备。");
        } else if (!ready) {
            builder.append("\n提示: 设备已检测到，下一步请求 Android USB 权限。");
        } else {
            builder.append("\n提示: 这条 USB keyer 路由已经可以用于发射。");
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
                            ? "USB keyer permission granted by the system dialog."
                            : "USB keyer permission was denied by the system dialog.";
                } else {
                    serialProbeProfileId = profile == null ? null : profile.id();
                    serialProbeMessage = granted
                            ? "USB serial CAT permission granted by the system dialog."
                            : "USB serial CAT permission was denied by the system dialog.";
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
