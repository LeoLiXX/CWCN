package org.bi9clt.cwcn.ui.tx;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.bi9clt.cwcn.core.log.AppOverviewSnapshot;
import org.bi9clt.cwcn.core.log.LocalLogRepository;
import org.bi9clt.cwcn.core.rig.RigControlAdapter;
import org.bi9clt.cwcn.core.rig.RigRegistry;
import org.bi9clt.cwcn.core.rig.SerialKeyerTxOutput;
import org.bi9clt.cwcn.core.rig.UsbSerialDeviceOption;
import org.bi9clt.cwcn.core.rig.UsbSerialKeyerRigControlAdapter;
import org.bi9clt.cwcn.core.tx.CwTxBackend;
import org.bi9clt.cwcn.core.tx.CwTxEngine;
import org.bi9clt.cwcn.core.tx.CwTxPlan;
import org.bi9clt.cwcn.core.tx.CwTxPlaybackSnapshot;
import org.bi9clt.cwcn.core.tx.CwTxPreset;
import org.bi9clt.cwcn.core.tx.CwTxRouteAdvisor;
import org.bi9clt.cwcn.core.tx.CwTxState;
import org.bi9clt.cwcn.core.tx.LocalSidetoneTxBackend;
import org.bi9clt.cwcn.core.tx.RigTextTxBackend;
import org.bi9clt.cwcn.databinding.ActivityTxBinding;

import java.util.ArrayList;
import java.util.Locale;

public final class TxActivity extends AppCompatActivity {
    private static final String ACTION_USB_PERMISSION = "org.bi9clt.cwcn.action.USB_PERMISSION";
    private static final String PREFS_TX_CONSOLE = "tx_console";
    private static final String PREF_USB_DEVICE_NAME = "usb_device_name";
    private static final String PREF_USB_KEY_LINE = "usb_key_line";
    private static final String DEFAULT_TX_TEXT = "CQ CQ DE BI9CLT";
    private static final int DEFAULT_WPM = 18;
    private static final int DEFAULT_TONE_FREQUENCY_HZ = 650;

    private ActivityTxBinding binding;
    private CwTxEngine txEngine;
    private LocalLogRepository localLogRepository;
    private AudioTrackTxAudioOutput txAudioOutput;
    private ArrayList<TxBackendOption> backendOptions;

    private CwTxPlan currentPlan;
    private CwTxPlaybackSnapshot lastPlaybackSnapshot;
    private boolean syncingFields;
    private boolean syncingUsbDeviceSelection;
    private boolean syncingUsbKeyLineSelection;
    private BroadcastReceiver usbRouteReceiver;
    private ArrayAdapter<UsbSerialDeviceOption> usbDeviceAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTxBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        txEngine = new CwTxEngine();
        localLogRepository = new LocalLogRepository(this);
        txAudioOutput = new AudioTrackTxAudioOutput();
        backendOptions = buildBackendOptions();
        restoreUsbRoutePreferences();
        registerUsbPermissionReceiver();
        setupBackendSelector();
        setupPresetSelector();
        setupUsbRouteControls();
        setupDefaults();
        setupActions();
        rebuildPlanPreview();
    }

    @Override
    protected void onDestroy() {
        stopAllBackends();
        if (usbRouteReceiver != null) {
            unregisterReceiver(usbRouteReceiver);
            usbRouteReceiver = null;
        }
        txAudioOutput.stop();
        super.onDestroy();
    }

    private void setupDefaults() {
        String defaultCallsign = resolveDefaultStationCallsign();
        syncingFields = true;
        binding.stationCallsignEditText.setText(defaultCallsign);
        binding.txTextEditText.setText(DEFAULT_TX_TEXT);
        binding.wpmEditText.setText(String.valueOf(DEFAULT_WPM));
        binding.toneFrequencyEditText.setText(String.valueOf(DEFAULT_TONE_FREQUENCY_HZ));
        syncingFields = false;

        TextWatcher watcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                if (!syncingFields) {
                    rebuildPlanPreview();
                }
            }
        };
        binding.stationCallsignEditText.addTextChangedListener(watcher);
        binding.txTextEditText.addTextChangedListener(watcher);
        binding.wpmEditText.addTextChangedListener(watcher);
        binding.toneFrequencyEditText.addTextChangedListener(watcher);
    }

    private void setupActions() {
        binding.backButton.setOnClickListener(view -> finish());
        binding.rebuildPreviewButton.setOnClickListener(view -> rebuildPlanPreview());
        binding.applyPresetButton.setOnClickListener(view -> applySelectedPreset());
        binding.startTxButton.setOnClickListener(view -> startTx());
        binding.stopTxButton.setOnClickListener(view -> stopTx());
        binding.refreshUsbDevicesButton.setOnClickListener(view -> refreshSelectedUsbBackend());
        binding.requestUsbPermissionButton.setOnClickListener(view -> requestUsbPermissionForSelectedBackend());
    }

    private void setupBackendSelector() {
        ArrayAdapter<TxBackendOption> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                backendOptions
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.backendSpinner.setAdapter(adapter);
        binding.backendSpinner.setSelection(0);
        binding.backendSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                stopAllBackends();
                lastPlaybackSnapshot = null;
                rebuildPlanPreview();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                rebuildPlanPreview();
            }
        });
    }

    private void setupPresetSelector() {
        ArrayAdapter<CwTxPreset> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                CwTxPreset.values()
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.presetSpinner.setAdapter(adapter);
        binding.presetSpinner.setSelection(0);
    }

    private void setupUsbRouteControls() {
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
                UsbSerialKeyerRigControlAdapter adapter = selectedUsbSerialAdapter();
                Object item = parent.getItemAtPosition(position);
                if (adapter == null || !(item instanceof UsbSerialDeviceOption)) {
                    return;
                }
                UsbSerialDeviceOption option = (UsbSerialDeviceOption) item;
                String selectedDeviceName = option.isAuto() ? null : option.deviceName();
                if (adapter.selectDevice(selectedDeviceName)) {
                    persistUsbDeviceName(selectedDeviceName);
                    lastPlaybackSnapshot = null;
                    rebuildPlanPreview();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        ArrayAdapter<SerialKeyerTxOutput.KeyLine> keyLineAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                SerialKeyerTxOutput.KeyLine.values()
        );
        keyLineAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.usbKeyLineSpinner.setAdapter(keyLineAdapter);
        binding.usbKeyLineSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (syncingUsbKeyLineSelection) {
                    return;
                }
                UsbSerialKeyerRigControlAdapter adapter = selectedUsbSerialAdapter();
                Object item = parent.getItemAtPosition(position);
                if (adapter == null || !(item instanceof SerialKeyerTxOutput.KeyLine)) {
                    return;
                }
                if (adapter.setKeyLine((SerialKeyerTxOutput.KeyLine) item)) {
                    persistUsbKeyLine((SerialKeyerTxOutput.KeyLine) item);
                    lastPlaybackSnapshot = null;
                    rebuildPlanPreview();
                } else {
                    Toast.makeText(TxActivity.this, "Unable to change USB key line while TX is active.", Toast.LENGTH_SHORT).show();
                    syncUsbKeyLineSelection(adapter);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void rebuildPlanPreview() {
        CwTxBackend backend = selectedBackend();
        currentPlan = txEngine.buildPlan(
                binding.txTextEditText.getText() == null ? null : binding.txTextEditText.getText().toString(),
                parsedPositiveInt(binding.wpmEditText.getText(), DEFAULT_WPM),
                parsedPositiveInt(binding.toneFrequencyEditText.getText(), DEFAULT_TONE_FREQUENCY_HZ)
        );
        binding.normalizedText.setText(currentPlan.normalizedText().isEmpty()
                ? "(empty)"
                : currentPlan.normalizedText());
        binding.morsePreviewText.setText(currentPlan.morsePreview().isEmpty()
                ? "(no supported Morse symbols)"
                : currentPlan.morsePreview());
        binding.planSummaryText.setText(renderPlanSummary(currentPlan));
        binding.backendSummaryText.setText(renderBackendSummary(backend));
        binding.routeChecklistText.setText(CwTxRouteAdvisor.buildChecklist(backend, currentPlan));
        refreshRouteControls(backend);
        if (lastPlaybackSnapshot == null || !backend.isRunning()) {
            binding.txStatusText.setText(renderIdleStatus(backend));
            binding.txProgressText.setText("Progress: 0%");
        }
        boolean editableWpm = backend.usesWpm();
        boolean editableTone = backend.usesToneFrequency();
        binding.wpmEditText.setEnabled(editableWpm);
        binding.wpmEditText.setAlpha(editableWpm ? 1.0f : 0.55f);
        binding.toneFrequencyEditText.setEnabled(editableTone);
        binding.toneFrequencyEditText.setAlpha(editableTone ? 1.0f : 0.55f);
        refreshButtons();
    }

    private void startTx() {
        CwTxBackend backend = selectedBackend();
        rebuildPlanPreview();
        if (currentPlan == null || currentPlan.elements().isEmpty()) {
            Toast.makeText(this, "Nothing to send yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!backend.isReady()) {
            Toast.makeText(this, backend.describeAvailability(), Toast.LENGTH_SHORT).show();
            binding.txStatusText.setText(renderIdleStatus(backend));
            refreshButtons();
            return;
        }
        if (backend.isRunning()) {
            Toast.makeText(this, "TX is already running.", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean started = backend.start(currentPlan, snapshot ->
                runOnUiThread(() -> applyPlaybackSnapshot(snapshot)));
        if (!started) {
            Toast.makeText(this, "Unable to start TX.", Toast.LENGTH_SHORT).show();
        }
        refreshButtons();
    }

    private void stopTx() {
        selectedBackend().stop();
        binding.txStatusText.setText(renderIdleStatus(selectedBackend()));
        refreshButtons();
    }

    private void applyPlaybackSnapshot(CwTxPlaybackSnapshot snapshot) {
        lastPlaybackSnapshot = snapshot;
        binding.txStatusText.setText(renderTxStatus(snapshot));
        binding.txProgressText.setText(renderTxProgress(snapshot));
        refreshButtons();
    }

    private void refreshButtons() {
        CwTxBackend backend = selectedBackend();
        boolean running = backend.isRunning();
        binding.startTxButton.setEnabled(
                currentPlan != null
                        && !currentPlan.elements().isEmpty()
                        && backend.isReady()
                        && !running
        );
        binding.stopTxButton.setEnabled(running);
    }

    private String renderPlanSummary(CwTxPlan plan) {
        return "WPM: " + plan.wpm()
                + "\nTone: " + plan.toneFrequencyHz() + " Hz"
                + "\nDot: " + plan.dotDurationMs() + " ms"
                + "\nElements: " + plan.elements().size()
                + "\nEstimated duration: " + formatMs(plan.totalDurationMs());
    }

    private String renderBackendSummary(CwTxBackend backend) {
        return "Backend: " + backend.displayName()
                + "\nRoute: " + backend.describeRoute()
                + "\nReady: " + yesNo(backend.isReady())
                + "\nAvailability: " + backend.describeAvailability()
                + "\nUses WPM: " + yesNo(backend.usesWpm())
                + "\nUses tone frequency: " + yesNo(backend.usesToneFrequency())
                + "\nSupports live profile: " + yesNo(backend.supportsLivePlanProfile())
                + "\nProgress snapshots: " + yesNo(backend.supportsProgressSnapshots());
    }

    private String renderTxStatus(CwTxPlaybackSnapshot snapshot) {
        if (snapshot == null) {
            return renderIdleStatus(selectedBackend());
        }
        return "State: " + snapshot.state().displayName()
                + "\nStatus: " + snapshot.statusMessage()
                + "\nCurrent element: " + (snapshot.currentElementLabel().isEmpty() ? "-" : snapshot.currentElementLabel())
                + "\nTone active: " + yesNo(snapshot.toneActive())
                + "\nElapsed: " + formatMs(snapshot.elapsedMs()) + " / " + formatMs(snapshot.totalDurationMs());
    }

    private String renderTxProgress(CwTxPlaybackSnapshot snapshot) {
        if (snapshot == null) {
            return "Progress: 0%";
        }
        return "Progress: "
                + Math.round(snapshot.completionRatio() * 100.0d)
                + "%"
                + "\nElements: "
                + snapshot.completedElementCount()
                + " / "
                + snapshot.totalElementCount();
    }

    private int parsedPositiveInt(Editable editable, int fallback) {
        if (editable == null) {
            return fallback;
        }
        String raw = editable.toString().trim();
        if (raw.isEmpty()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(raw);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String formatMs(int durationMs) {
        int clamped = Math.max(0, durationMs);
        int seconds = clamped / 1000;
        int millis = clamped % 1000;
        return String.format(Locale.US, "%d.%03ds", seconds, millis);
    }

    private String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private String renderIdleStatus(CwTxBackend backend) {
        if (backend.isReady()) {
            return "TX idle. Build a plan and press Start TX.";
        }
        return "TX idle.\nSelected backend is not ready yet.\nReason: " + backend.describeAvailability();
    }

    private void refreshRouteControls(CwTxBackend backend) {
        UsbSerialKeyerRigControlAdapter usbAdapter = selectedUsbSerialAdapter();
        boolean usbVisible = backend != null && usbAdapter != null;
        binding.usbRoutePanel.setVisibility(usbVisible ? View.VISIBLE : View.GONE);
        if (!usbVisible) {
            return;
        }
        java.util.List<UsbSerialDeviceOption> deviceOptions = refreshUsbDeviceSelection(usbAdapter);
        binding.usbDeviceText.setText(renderUsbRouteSummary(usbAdapter));
        syncUsbKeyLineSelection(usbAdapter);
        boolean hasCandidateDevices = hasRealUsbDeviceOption(deviceOptions);
        boolean hasTargetDevice = usbAdapter.hasTargetDevice();
        boolean needsPermission = hasTargetDevice && !usbAdapter.isReady();
        binding.usbDeviceSpinner.setEnabled(hasCandidateDevices);
        binding.refreshUsbDevicesButton.setEnabled(true);
        binding.requestUsbPermissionButton.setEnabled(needsPermission);
        binding.requestUsbPermissionButton.setText(needsPermission
                ? "Request USB Permission"
                : renderUsbPermissionButtonLabel(usbAdapter, hasCandidateDevices));
    }

    private java.util.List<UsbSerialDeviceOption> refreshUsbDeviceSelection(UsbSerialKeyerRigControlAdapter adapter) {
        java.util.List<UsbSerialDeviceOption> devices = new ArrayList<>();
        devices.add(UsbSerialDeviceOption.autoOption());
        devices.addAll(adapter.availableDevices());
        syncingUsbDeviceSelection = true;
        usbDeviceAdapter.clear();
        usbDeviceAdapter.addAll(devices);
        usbDeviceAdapter.notifyDataSetChanged();
        String preferredDeviceName = adapter.preferredDeviceName();
        int selectionIndex = findUsbDeviceSelectionIndex(devices, preferredDeviceName);
        if (selectionIndex >= 0) {
            binding.usbDeviceSpinner.setSelection(selectionIndex, false);
        }
        syncingUsbDeviceSelection = false;
        return devices;
    }

    private int findUsbDeviceSelectionIndex(
            java.util.List<UsbSerialDeviceOption> devices,
            String preferredDeviceName
    ) {
        if (devices.isEmpty()) {
            return -1;
        }
        if (preferredDeviceName == null || preferredDeviceName.isEmpty()) {
            return 0;
        }
        for (int index = 0; index < devices.size(); index++) {
            UsbSerialDeviceOption device = devices.get(index);
            if (!device.isAuto() && preferredDeviceName.equals(device.deviceName())) {
                return index;
            }
        }
        return 0;
    }

    private boolean hasRealUsbDeviceOption(java.util.List<UsbSerialDeviceOption> devices) {
        for (UsbSerialDeviceOption device : devices) {
            if (!device.isAuto()) {
                return true;
            }
        }
        return false;
    }

    private String renderUsbRouteSummary(UsbSerialKeyerRigControlAdapter adapter) {
        StringBuilder builder = new StringBuilder();
        builder.append("Target mode: ");
        if (adapter.hasPreferredDeviceSelection()) {
            builder.append("Locked to ").append(adapter.preferredDeviceName());
        } else {
            builder.append("Auto / first available");
        }
        builder.append("\nTarget state: ").append(renderUsbTargetState(adapter));
        builder.append("\nActive target: ").append(adapter.describeMatchedDevice());
        builder.append("\nAvailability: ").append(adapter.describeAvailability());
        return builder.toString();
    }

    private String renderUsbTargetState(UsbSerialKeyerRigControlAdapter adapter) {
        if (adapter.isPreferredDeviceMissing()) {
            return "Locked target missing";
        }
        if (adapter.hasTargetDevice()) {
            return adapter.hasPreferredDeviceSelection()
                    ? "Locked target attached"
                    : "Auto target available";
        }
        if (adapter.hasAnyCandidateDevice()) {
            return "Candidate device exists, but current target is unresolved";
        }
        return "No candidate device attached";
    }

    private String renderUsbPermissionButtonLabel(
            UsbSerialKeyerRigControlAdapter adapter,
            boolean hasCandidateDevices
    ) {
        if (!hasCandidateDevices) {
            return "No USB Keyer Detected";
        }
        if (!adapter.hasTargetDevice()) {
            return "Target Device Missing";
        }
        return "USB Permission Ready";
    }

    private void syncUsbKeyLineSelection(UsbSerialKeyerRigControlAdapter adapter) {
        syncingUsbKeyLineSelection = true;
        binding.usbKeyLineSpinner.setSelection(adapter.keyLine().ordinal(), false);
        syncingUsbKeyLineSelection = false;
    }

    private void requestUsbPermissionForSelectedBackend() {
        UsbSerialKeyerRigControlAdapter adapter = selectedUsbSerialAdapter();
        if (adapter == null) {
            Toast.makeText(this, "USB serial backend is not selected.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!adapter.hasTargetDevice()) {
            Toast.makeText(this, adapter.describeAvailability(), Toast.LENGTH_SHORT).show();
            rebuildPlanPreview();
            return;
        }
        if (adapter.isReady()) {
            Toast.makeText(this, "USB backend already has permission and is ready.", Toast.LENGTH_SHORT).show();
            rebuildPlanPreview();
            return;
        }
        PendingIntent permissionIntent = PendingIntent.getBroadcast(
                this,
                0,
                new Intent(ACTION_USB_PERMISSION).setPackage(getPackageName()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        boolean requested = adapter.requestUsbPermission(permissionIntent);
        if (!requested) {
            Toast.makeText(this, adapter.describeAvailability(), Toast.LENGTH_SHORT).show();
            rebuildPlanPreview();
            return;
        }
        Toast.makeText(this, "USB permission request sent. Approve it on the device if prompted.", Toast.LENGTH_SHORT).show();
    }

    private void refreshSelectedUsbBackend() {
        UsbSerialKeyerRigControlAdapter adapter = selectedUsbSerialAdapter();
        if (adapter == null) {
            Toast.makeText(this, "USB serial backend is not selected.", Toast.LENGTH_SHORT).show();
            return;
        }
        adapter.refreshRouteState();
        lastPlaybackSnapshot = null;
        rebuildPlanPreview();
        Toast.makeText(this, "USB device inventory and route state refreshed.", Toast.LENGTH_SHORT).show();
    }

    private void registerUsbPermissionReceiver() {
        usbRouteReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context, Intent intent) {
                String action = intent.getAction();
                if (action == null) {
                    return;
                }
                if (ACTION_USB_PERMISSION.equals(action)) {
                    boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    Toast.makeText(
                            TxActivity.this,
                            granted
                                    ? "USB permission granted. Rechecking backend readiness."
                                    : "USB permission was denied.",
                            Toast.LENGTH_SHORT
                    ).show();
                    handleUsbRouteChange(null, false);
                    return;
                }
                if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                    handleUsbRouteChange(extractUsbDeviceName(intent), false);
                    return;
                }
                if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                    handleUsbRouteChange(extractUsbDeviceName(intent), true);
                }
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        ContextCompat.registerReceiver(
                this,
                usbRouteReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
    }

    private void handleUsbRouteChange(String deviceName, boolean detached) {
        UsbSerialKeyerRigControlAdapter adapter = firstUsbSerialAdapter();
        if (adapter != null) {
            adapter.refreshRouteState();
        }
        lastPlaybackSnapshot = null;
        rebuildPlanPreview();
        if (selectedUsbSerialAdapter() == null) {
            return;
        }
        if (detached && adapter != null && adapter.isPreferredDeviceMissing()) {
            String preferred = adapter.preferredDeviceName();
            Toast.makeText(
                    this,
                    "Selected USB keyer is no longer attached: "
                            + (preferred == null ? "(unknown target)" : preferred),
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }
        if (deviceName != null && !deviceName.isEmpty()) {
            Toast.makeText(
                    this,
                    (detached ? "USB device detached: " : "USB device attached: ") + deviceName,
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private String extractUsbDeviceName(Intent intent) {
        UsbDevice device = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class)
                : intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        return device == null ? null : device.getDeviceName();
    }

    private void restoreUsbRoutePreferences() {
        UsbSerialKeyerRigControlAdapter adapter = firstUsbSerialAdapter();
        if (adapter == null) {
            return;
        }
        String storedDeviceName = getSharedPreferences(PREFS_TX_CONSOLE, MODE_PRIVATE)
                .getString(PREF_USB_DEVICE_NAME, null);
        String storedKeyLine = getSharedPreferences(PREFS_TX_CONSOLE, MODE_PRIVATE)
                .getString(PREF_USB_KEY_LINE, null);
        if (storedDeviceName != null && !storedDeviceName.trim().isEmpty()) {
            adapter.selectDevice(storedDeviceName);
        }
        if (storedKeyLine != null && !storedKeyLine.trim().isEmpty()) {
            try {
                adapter.setKeyLine(SerialKeyerTxOutput.KeyLine.valueOf(storedKeyLine));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void persistUsbDeviceName(String deviceName) {
        android.content.SharedPreferences.Editor editor = getSharedPreferences(PREFS_TX_CONSOLE, MODE_PRIVATE)
                .edit();
        if (deviceName == null || deviceName.trim().isEmpty()) {
            editor.remove(PREF_USB_DEVICE_NAME);
        } else {
            editor.putString(PREF_USB_DEVICE_NAME, deviceName);
        }
        editor.apply();
    }

    private void persistUsbKeyLine(SerialKeyerTxOutput.KeyLine keyLine) {
        getSharedPreferences(PREFS_TX_CONSOLE, MODE_PRIVATE)
                .edit()
                .putString(PREF_USB_KEY_LINE, keyLine.name())
                .apply();
    }

    private TxBackendOption selectedBackendOption() {
        Object selectedItem = binding.backendSpinner.getSelectedItem();
        if (selectedItem instanceof TxBackendOption) {
            return (TxBackendOption) selectedItem;
        }
        return backendOptions.get(0);
    }

    private CwTxBackend selectedBackend() {
        return selectedBackendOption().backend();
    }

    private UsbSerialKeyerRigControlAdapter selectedUsbSerialAdapter() {
        CwTxBackend backend = selectedBackendOption().backend();
        if (backend instanceof RigTextTxBackend) {
            RigControlAdapter adapter = ((RigTextTxBackend) backend).rigAdapter();
            if (adapter instanceof UsbSerialKeyerRigControlAdapter) {
                return (UsbSerialKeyerRigControlAdapter) adapter;
            }
        }
        return null;
    }

    private UsbSerialKeyerRigControlAdapter firstUsbSerialAdapter() {
        for (TxBackendOption option : backendOptions) {
            CwTxBackend backend = option.backend();
            if (backend instanceof RigTextTxBackend) {
                RigControlAdapter adapter = ((RigTextTxBackend) backend).rigAdapter();
                if (adapter instanceof UsbSerialKeyerRigControlAdapter) {
                    return (UsbSerialKeyerRigControlAdapter) adapter;
                }
            }
        }
        return null;
    }

    private void applySelectedPreset() {
        Object selectedItem = binding.presetSpinner.getSelectedItem();
        CwTxPreset preset = selectedItem instanceof CwTxPreset
                ? (CwTxPreset) selectedItem
                : CwTxPreset.GENERAL_CQ;
        syncingFields = true;
        binding.txTextEditText.setText(preset.render(normalizedStationCallsign()));
        syncingFields = false;
        lastPlaybackSnapshot = null;
        rebuildPlanPreview();
    }

    private String normalizedStationCallsign() {
        Editable editable = binding.stationCallsignEditText.getText();
        if (editable == null) {
            return null;
        }
        String raw = editable.toString().trim().toUpperCase(Locale.US);
        return raw.isEmpty() ? null : raw;
    }

    private String resolveDefaultStationCallsign() {
        AppOverviewSnapshot overview = localLogRepository.loadOverview();
        if (overview != null && overview.activeDraft() != null && overview.activeDraft().stationCallsignUsed() != null) {
            return overview.activeDraft().stationCallsignUsed();
        }
        if (overview != null && overview.latestConfirmedLog() != null && overview.latestConfirmedLog().stationCallsign() != null) {
            return overview.latestConfirmedLog().stationCallsign();
        }
        return "BI9CLT";
    }

    private ArrayList<TxBackendOption> buildBackendOptions() {
        ArrayList<TxBackendOption> options = new ArrayList<>();
        options.add(new TxBackendOption(new LocalSidetoneTxBackend(txAudioOutput)));
        for (RigControlAdapter adapter : RigRegistry.defaultAdapters(this)) {
            if (adapter.supportsTextToCw()) {
                options.add(new TxBackendOption(new RigTextTxBackend(adapter)));
            }
        }
        return options;
    }

    private void stopAllBackends() {
        for (TxBackendOption option : backendOptions) {
            option.backend().stop();
        }
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }
}
