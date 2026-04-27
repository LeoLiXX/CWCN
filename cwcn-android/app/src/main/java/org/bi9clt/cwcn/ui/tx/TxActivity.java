package org.bi9clt.cwcn.ui.tx;

import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.bi9clt.cwcn.core.log.AppOverviewSnapshot;
import org.bi9clt.cwcn.core.log.LocalLogRepository;
import org.bi9clt.cwcn.core.rig.MockUsbSerialBenchScenario;
import org.bi9clt.cwcn.core.rig.RigCapability;
import org.bi9clt.cwcn.core.rig.RigControlAdapter;
import org.bi9clt.cwcn.core.rig.RigProfile;
import org.bi9clt.cwcn.core.rig.RigProfileConfigurationFormatter;
import org.bi9clt.cwcn.core.rig.RigProfileFamilies;
import org.bi9clt.cwcn.core.rig.RigProfileSettings;
import org.bi9clt.cwcn.core.rig.RigRegistry;
import org.bi9clt.cwcn.core.rig.RigSelectionStore;
import org.bi9clt.cwcn.core.rig.SerialKeyerTxOutput;
import org.bi9clt.cwcn.core.rig.UsbSerialDeviceOption;
import org.bi9clt.cwcn.core.rig.UsbSerialKeyerRigControlAdapter;
import org.bi9clt.cwcn.core.tx.CwTxBackend;
import org.bi9clt.cwcn.core.tx.CwTxBenchLogBuffer;
import org.bi9clt.cwcn.core.tx.CwTxBenchReportFormatter;
import org.bi9clt.cwcn.core.tx.CwTxBenchSummaryFormatter;
import org.bi9clt.cwcn.core.tx.CwTxEngine;
import org.bi9clt.cwcn.core.tx.CwTxPlan;
import org.bi9clt.cwcn.core.tx.CwTxPlaybackSnapshot;
import org.bi9clt.cwcn.core.tx.CwTxPreset;
import org.bi9clt.cwcn.core.tx.CwTxRouteAdvisor;
import org.bi9clt.cwcn.core.tx.CwTxState;
import org.bi9clt.cwcn.core.tx.LocalSidetoneTxBackend;
import org.bi9clt.cwcn.core.tx.RigKeyingTxBackend;
import org.bi9clt.cwcn.core.tx.RigTextTxBackend;
import org.bi9clt.cwcn.databinding.ActivityTxBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public final class TxActivity extends AppCompatActivity {
    private static final String ACTION_USB_PERMISSION = "org.bi9clt.cwcn.action.USB_PERMISSION";
    private static final String PREFS_TX_CONSOLE = "tx_console";
    private static final String PREF_USB_DEVICE_NAME = "usb_device_name";
    private static final String PREF_USB_KEY_LINE = "usb_key_line";
    private static final String DEFAULT_TX_TEXT = "CQ CQ DE BI9CLT";
    private static final int DEFAULT_WPM = 18;
    private static final int DEFAULT_TONE_FREQUENCY_HZ = 650;
    private static final int BENCH_LOG_LIMIT = 24;

    private ActivityTxBinding binding;
    private CwTxEngine txEngine;
    private LocalLogRepository localLogRepository;
    private RigSelectionStore rigSelectionStore;
    private AudioTrackTxAudioOutput txAudioOutput;
    private ArrayList<TxBackendOption> backendOptions;
    private CwTxBenchLogBuffer benchLogBuffer;

    private CwTxPlan currentPlan;
    private CwTxPlaybackSnapshot lastPlaybackSnapshot;
    private boolean syncingFields;
    private boolean syncingUsbDeviceSelection;
    private boolean syncingUsbKeyLineSelection;
    private boolean syncingMockUsbScenarioSelection;
    private BroadcastReceiver usbRouteReceiver;
    private ArrayAdapter<UsbSerialDeviceOption> usbDeviceAdapter;
    private ArrayAdapter<MockUsbSerialBenchScenario> mockUsbScenarioAdapter;
    private String lastLoggedBackendId;
    private String lastLoggedUsbDiagnosticCode;
    private CwTxState lastLoggedPlaybackState;
    private String lastSignificantUsbDiagnosticSummary;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTxBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        txEngine = new CwTxEngine();
        localLogRepository = new LocalLogRepository(this);
        rigSelectionStore = new RigSelectionStore(this);
        txAudioOutput = new AudioTrackTxAudioOutput();
        backendOptions = buildBackendOptions();
        benchLogBuffer = new CwTxBenchLogBuffer(BENCH_LOG_LIMIT);
        restoreUsbRoutePreferences();
        registerUsbPermissionReceiver();
        setupBackendSelector();
        setupPresetSelector();
        setupUsbRouteControls();
        setupDefaults();
        setupActions();
        renderBenchLog();
        rebuildPlanPreview();
        logBenchEvent("SESSION", "TX console opened.");
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
        RigProfileSettings profileSettings = selectedRigProfileSettings();
        syncingFields = true;
        binding.stationCallsignEditText.setText(defaultCallsign);
        binding.txTextEditText.setText(DEFAULT_TX_TEXT);
        binding.wpmEditText.setText(String.valueOf(profileSettings.defaultWpm()));
        binding.toneFrequencyEditText.setText(String.valueOf(profileSettings.defaultToneFrequencyHz()));
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

        setupDismissKeyboardActions();
    }

    private void setupActions() {
        binding.txScrollView.setOnClickListener(view -> dismissKeyboardAndClearNumericFocus());
        binding.backButton.setOnClickListener(view -> finish());
        binding.rebuildPreviewButton.setOnClickListener(view -> rebuildPlanPreview());
        binding.applyPresetButton.setOnClickListener(view -> applySelectedPreset());
        binding.startTxButton.setOnClickListener(view -> startTx());
        binding.stopTxButton.setOnClickListener(view -> stopTx());
        binding.refreshUsbDevicesButton.setOnClickListener(view -> refreshSelectedUsbBackend());
        binding.requestUsbPermissionButton.setOnClickListener(view -> requestUsbPermissionForSelectedBackend());
        binding.loadUsbDitTestButton.setOnClickListener(view -> applyUsbBenchPreset(CwTxPreset.BENCH_DIT, 12));
        binding.loadUsbPatternTestButton.setOnClickListener(view -> applyUsbBenchPreset(CwTxPreset.BENCH_PATTERN, 15));
        binding.releaseUsbKeyLineButton.setOnClickListener(view -> releaseSelectedUsbKeyLine());
        binding.copyBenchReportButton.setOnClickListener(view -> copyBenchReport());
        binding.clearBenchLogButton.setOnClickListener(view -> clearBenchLog());
    }

    private void setupDismissKeyboardActions() {
        TextView.OnEditorActionListener doneListener = (view, actionId, event) -> {
            boolean enterPressed = event != null
                    && event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (actionId == EditorInfo.IME_ACTION_DONE || enterPressed) {
                dismissKeyboardAndClearNumericFocus();
                return true;
            }
            return false;
        };
        binding.wpmEditText.setOnEditorActionListener(doneListener);
        binding.toneFrequencyEditText.setOnEditorActionListener(doneListener);
    }

    private void setupBackendSelector() {
        ArrayAdapter<TxBackendOption> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                backendOptions
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.backendSpinner.setAdapter(adapter);
        binding.backendSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                stopAllBackends();
                lastPlaybackSnapshot = null;
                lastLoggedPlaybackState = null;
                maybeLogBackendSelection(backendOptions.get(position).backend());
                rebuildPlanPreview();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                rebuildPlanPreview();
            }
        });
        binding.backendSpinner.setSelection(resolvePreferredBackendSelectionIndex(), false);
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
                    lastLoggedPlaybackState = null;
                    logBenchEvent(
                            "USB",
                            option.isAuto()
                                    ? "Device selector switched to Auto target mode."
                                    : "Locked target selected: " + option.deviceName()
                    );
                    rebuildPlanPreview();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        mockUsbScenarioAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new ArrayList<>()
        );
        mockUsbScenarioAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.mockUsbScenarioSpinner.setAdapter(mockUsbScenarioAdapter);
        binding.mockUsbScenarioSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (syncingMockUsbScenarioSelection) {
                    return;
                }
                UsbSerialKeyerRigControlAdapter adapter = selectedUsbSerialAdapter();
                Object item = parent.getItemAtPosition(position);
                if (adapter == null || !(item instanceof MockUsbSerialBenchScenario)) {
                    return;
                }
                MockUsbSerialBenchScenario scenario = (MockUsbSerialBenchScenario) item;
                if (adapter.selectMockBenchScenario(scenario)) {
                    lastPlaybackSnapshot = null;
                    lastLoggedPlaybackState = null;
                    logBenchEvent("MOCK", "Mock USB scenario switched to " + scenario.displayName() + ".");
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
                    lastLoggedPlaybackState = null;
                    logBenchEvent("USB", "Key line changed to " + item + ".");
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
        preserveScrollPosition(() -> {
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
            binding.pinnedRigSummaryText.setText(renderPinnedRigSummary());
            binding.backendSummaryText.setText(renderBackendSummary(backend));
            binding.routeChecklistText.setText(CwTxRouteAdvisor.buildChecklist(backend, currentPlan));
            refreshRouteControls(backend);
            renderBenchSummary(backend);
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
        });
    }

    private void startTx() {
        CwTxBackend backend = selectedBackend();
        rebuildPlanPreview();
        if (currentPlan == null || currentPlan.elements().isEmpty()) {
            logBenchEvent("TX", "Start blocked because the current plan is empty.");
            Toast.makeText(this, "Nothing to send yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!backend.isReady()) {
            logBenchEvent(
                    "TX",
                    "Start blocked for " + backend.displayName() + ": " + backend.describeAvailability()
            );
            Toast.makeText(
                    this,
                    backend.describeAvailability() + "\nNext: " + renderBackendRecoveryHint(backend),
                    Toast.LENGTH_LONG
            ).show();
            binding.txStatusText.setText(renderIdleStatus(backend));
            refreshButtons();
            return;
        }
        if (backend.isRunning()) {
            logBenchEvent("TX", "Start ignored because the backend is already running.");
            Toast.makeText(this, "TX is already running.", Toast.LENGTH_SHORT).show();
            return;
        }
        lastLoggedPlaybackState = null;
        logBenchEvent(
                "TX",
                "Start requested on " + backend.displayName() + " using " + currentPlan.elements().size() + " elements."
        );
        boolean started = backend.start(currentPlan, snapshot ->
                runOnUiThread(() -> applyPlaybackSnapshot(snapshot)));
        if (!started) {
            String recoveryHint = renderBackendRecoveryHint(backend);
            logBenchEvent(
                "TX",
                "Start failed on " + backend.displayName() + ". Next: " + recoveryHint
            );
            binding.txStatusText.setText(
                    "TX start failed.\nReason: backend rejected the request.\nNext: " + recoveryHint
            );
            Toast.makeText(this, "Unable to start TX.\nNext: " + recoveryHint, Toast.LENGTH_LONG).show();
        }
        refreshButtons();
    }

    private void stopTx() {
        CwTxBackend backend = selectedBackend();
        boolean wasRunning = backend.isRunning();
        backend.stop();
        lastLoggedPlaybackState = null;
        logBenchEvent(
                "TX",
                wasRunning
                        ? "Stop requested for " + backend.displayName() + "."
                        : "Stop pressed while " + backend.displayName() + " was already idle."
        );
        binding.txStatusText.setText(renderIdleStatus(backend));
        refreshButtons();
    }

    private void applyPlaybackSnapshot(CwTxPlaybackSnapshot snapshot) {
        preserveScrollPosition(() -> {
            lastPlaybackSnapshot = snapshot;
            maybeLogPlaybackSnapshot(snapshot);
            binding.txStatusText.setText(renderTxStatus(snapshot));
            binding.txProgressText.setText(renderTxProgress(snapshot));
            renderBenchSummary(selectedBackend());
            refreshButtons();
        });
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

    private String renderPinnedRigSummary() {
        RigProfile profile = rigSelectionStore.selectedProfile();
        RigProfileSettings settings = rigSelectionStore.loadSettings(profile);
        String preferredBackendId = preferredBackendIdForProfile(profile);
        TxBackendOption preferredOption = findBackendOptionById(preferredBackendId);
        CwTxBackend activeBackend = selectedBackend();
        StringBuilder builder = new StringBuilder(
                RigProfileConfigurationFormatter.renderCompactSummary(profile, settings)
        );
        builder.append("\nTX preferred route: ")
                .append(preferredOption == null
                        ? renderMissingPreferredBackendLabel(profile)
                        : preferredOption.backend().displayName());
        builder.append("\nTX active route: ")
                .append(activeBackend == null ? "(none)" : activeBackend.displayName());
        if (preferredOption != null
                && activeBackend != null
                && !preferredOption.backend().id().equals(activeBackend.id())) {
            builder.append("\nTX note: this console is temporarily using a different backend than the pinned rig.");
        } else if (preferredOption == null && profile != null) {
            builder.append("\nTX note: this pinned rig does not expose a ready text-to-CW backend here yet.");
        }
        return builder.toString();
    }

    private String renderTxStatus(CwTxPlaybackSnapshot snapshot) {
        if (snapshot == null) {
            return renderIdleStatus(selectedBackend());
        }
        CwTxBackend backend = selectedBackend();
        String activeLabel = isDedicatedKeyingBackend(backend) ? "Keying line active" : "Tone active";
        return "State: " + snapshot.state().displayName()
                + "\nStatus: " + snapshot.statusMessage()
                + "\nCurrent element: " + (snapshot.currentElementLabel().isEmpty() ? "-" : snapshot.currentElementLabel())
                + "\n" + activeLabel + ": " + yesNo(snapshot.toneActive())
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
            if (isDedicatedKeyingBackend(backend)) {
                return "TX idle. Dedicated keying route is armed. Build a short plan and press Start TX. This route does not generate phone sidetone; rely on the radio's monitor/sidetone.";
            }
            return "TX idle. Build a plan and press Start TX.";
        }
        return "TX idle.\nSelected backend is not ready yet.\nReason: "
                + backend.describeAvailability()
                + "\nNext: "
                + renderBackendRecoveryHint(backend);
    }

    private boolean isDedicatedKeyingBackend(CwTxBackend backend) {
        return backend != null && backend.id() != null && backend.id().startsWith("rig-key:");
    }

    private void refreshRouteControls(CwTxBackend backend) {
        UsbSerialKeyerRigControlAdapter usbAdapter = selectedUsbSerialAdapter();
        boolean usbVisible = backend != null && usbAdapter != null;
        binding.usbRoutePanel.setVisibility(usbVisible ? View.VISIBLE : View.GONE);
        if (!usbVisible) {
            lastLoggedUsbDiagnosticCode = null;
            binding.mockUsbScenarioPanel.setVisibility(View.GONE);
            return;
        }
        java.util.List<UsbSerialDeviceOption> deviceOptions = refreshUsbDeviceSelection(usbAdapter);
        binding.usbDeviceText.setText(renderUsbRouteSummary(usbAdapter));
        syncUsbKeyLineSelection(usbAdapter);
        syncMockUsbScenarioSelection(usbAdapter);
        maybeLogUsbDiagnosticStage(usbAdapter);
        boolean hasCandidateDevices = hasRealUsbDeviceOption(deviceOptions);
        boolean hasTargetDevice = usbAdapter.hasTargetDevice();
        boolean needsPermission = hasTargetDevice && !usbAdapter.isReady();
        binding.usbDeviceSpinner.setEnabled(hasCandidateDevices);
        binding.refreshUsbDevicesButton.setEnabled(true);
        binding.requestUsbPermissionButton.setEnabled(needsPermission);
        binding.releaseUsbKeyLineButton.setEnabled(hasTargetDevice || usbAdapter.isReady());
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
        builder.append("\nDiagnostic stage: ")
                .append(adapter.diagnosticStageLabel())
                .append(" (")
                .append(adapter.diagnosticStageCode())
                .append(")");
        builder.append("\nActive target: ").append(adapter.describeMatchedDevice());
        builder.append("\nAvailability: ").append(adapter.describeAvailability());
        builder.append("\nNext action: ").append(renderUsbRecoveryHint(adapter));
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

    private String renderBackendRecoveryHint(CwTxBackend backend) {
        if (backend == null) {
            return "Select a TX backend first.";
        }
        if ("local-sidetone".equals(backend.id())) {
            return "Press Start TX for a dry run, and reduce phone volume or use headphones if needed.";
        }
        if ("rig-text:audio-vox-text".equals(backend.id())) {
            return "Connect the audio path, enable VOX, then start with a short preset at conservative volume.";
        }
        if ("rig-key:generic-cat".equals(backend.id())) {
            RigProfile pinnedProfile = rigSelectionStore == null ? null : rigSelectionStore.selectedProfile();
            if (RigProfileFamilies.isYaesuFamily(pinnedProfile)) {
                return "Pin the Yaesu serial CAT profile, confirm the detected serial port and baud rate in Rig Setup, validate CAT probe success, then retry a very short VVV or DIT send.";
            }
            if (RigProfileFamilies.isIcomFamily(pinnedProfile)) {
                return "Pin the Icom CI-V profile, confirm the serial port, baud rate, and CI-V address in Rig Setup, validate probe success, then retry a very short send.";
            }
            if (RigProfileFamilies.isKenwoodFamily(pinnedProfile)) {
                return "Pin the Kenwood serial CAT profile, confirm serial readiness in Rig Setup, validate the probe response, then retry a very short send.";
            }
            return "Open Rig Setup, pin the matching serial CAT profile, validate probe readiness, then retry a very short keyed CW send.";
        }
        if ("rig-text:hamlib-rigctld".equals(backend.id())) {
            RigProfile pinnedProfile = rigSelectionStore == null ? null : rigSelectionStore.selectedProfile();
            if (RigProfileFamilies.isYaesuFamily(pinnedProfile)) {
                return "Pin the Yaesu FT-family rigctld profile, confirm host/port reachability, verify rigctld is already bound to the radio, then retry a very short DIT or VVV send_morse test.";
            }
            if (RigProfileFamilies.isIcomFamily(pinnedProfile)) {
                return "Pin the Icom-family rigctld profile, confirm host/port reachability, verify rigctld is already bound to the CI-V radio, then retry a very short DIT or VVV send_morse test.";
            }
            if (RigProfileFamilies.isKenwoodFamily(pinnedProfile)) {
                return "Pin the Kenwood-family rigctld profile, confirm host/port reachability, verify rigctld is already bound to the radio, then retry a very short DIT or VVV send_morse test.";
            }
            return "Pin a Hamlib rigctld network profile in Rig Setup, confirm host/port reachability, then retry a short CQ or DIT-length send_morse test.";
        }
        UsbSerialKeyerRigControlAdapter usbAdapter = selectedUsbSerialAdapter();
        if (usbAdapter != null) {
            return renderUsbRecoveryHint(usbAdapter);
        }
        return "Review the route checklist and current availability before retrying.";
    }

    private String renderUsbRecoveryHint(UsbSerialKeyerRigControlAdapter adapter) {
        String diagnosticCode = adapter.diagnosticStageCode();
        if ("usb-serial-target-missing".equals(diagnosticCode) || adapter.isPreferredDeviceMissing()) {
            return "Reconnect the locked USB keyer or switch the device selector back to Auto, then press Refresh USB Devices.";
        }
        if ("usb-serial-no-device".equals(diagnosticCode) || !adapter.hasAnyCandidateDevice()) {
            return "Attach a CDC/ACM USB device, then press Refresh USB Devices.";
        }
        if ("usb-serial-no-cdc".equals(diagnosticCode)) {
            return "Attach a CDC/ACM-compatible USB serial/keyer device, then refresh the route.";
        }
        if ("usb-serial-no-control-interface".equals(diagnosticCode)) {
            return "This USB device does not expose the expected CDC control interface. Try another keyer profile or device.";
        }
        if ("usb-serial-open-failed".equals(diagnosticCode)) {
            return "Refresh USB Devices, re-seat the cable or OTG adapter, then retry the short DIT test.";
        }
        if ("usb-serial-claim-failed".equals(diagnosticCode)) {
            return "Release Key Line, refresh the USB route, and make sure no other app is holding the interface.";
        }
        if (!adapter.hasTargetDevice()) {
            return "Choose an attached target device or switch back to Auto selection.";
        }
        if ("usb-serial-no-permission".equals(diagnosticCode) || !adapter.isReady()) {
            return "Press Request USB Permission. If the line may be stuck, use Release Key Line after permission is granted.";
        }
        return "Load DIT Test or VVV Test, verify the key line wiring, then press Start TX.";
    }

    private void syncUsbKeyLineSelection(UsbSerialKeyerRigControlAdapter adapter) {
        syncingUsbKeyLineSelection = true;
        binding.usbKeyLineSpinner.setSelection(adapter.keyLine().ordinal(), false);
        syncingUsbKeyLineSelection = false;
    }

    private void syncMockUsbScenarioSelection(UsbSerialKeyerRigControlAdapter adapter) {
        boolean mockVisible = adapter != null && adapter.supportsMockBenchScenarios();
        binding.mockUsbScenarioPanel.setVisibility(mockVisible ? View.VISIBLE : View.GONE);
        if (!mockVisible) {
            return;
        }
        java.util.List<MockUsbSerialBenchScenario> scenarios = adapter.availableMockBenchScenarios();
        syncingMockUsbScenarioSelection = true;
        mockUsbScenarioAdapter.clear();
        mockUsbScenarioAdapter.addAll(scenarios);
        mockUsbScenarioAdapter.notifyDataSetChanged();
        MockUsbSerialBenchScenario selectedScenario = adapter.selectedMockBenchScenario();
        int selectionIndex = 0;
        if (selectedScenario != null) {
            selectionIndex = scenarios.indexOf(selectedScenario);
            if (selectionIndex < 0) {
                selectionIndex = 0;
            }
        }
        if (!scenarios.isEmpty()) {
            binding.mockUsbScenarioSpinner.setSelection(selectionIndex, false);
        }
        syncingMockUsbScenarioSelection = false;
    }

    private void requestUsbPermissionForSelectedBackend() {
        UsbSerialKeyerRigControlAdapter adapter = selectedUsbSerialAdapter();
        if (adapter == null) {
            logBenchEvent("USB", "Permission request ignored because USB serial backend is not selected.");
            Toast.makeText(this, "USB serial backend is not selected.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!adapter.hasTargetDevice()) {
            logBenchEvent("USB", "Permission request blocked: " + adapter.describeAvailability());
            Toast.makeText(this, adapter.describeAvailability(), Toast.LENGTH_SHORT).show();
            rebuildPlanPreview();
            return;
        }
        if (adapter.isReady()) {
            logBenchEvent("USB", "Permission request skipped because the backend is already ready.");
            Toast.makeText(this, "USB backend already has permission and is ready.", Toast.LENGTH_SHORT).show();
            rebuildPlanPreview();
            return;
        }
        PendingIntent permissionIntent = PendingIntent.getBroadcast(
                this,
                0,
                new Intent(ACTION_USB_PERMISSION).setPackage(getPackageName()),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT
        );
        boolean requested = adapter.requestUsbPermission(permissionIntent);
        if (!requested) {
            logBenchEvent("USB", "Permission request failed: " + adapter.describeAvailability());
            Toast.makeText(this, adapter.describeAvailability(), Toast.LENGTH_SHORT).show();
            rebuildPlanPreview();
            return;
        }
        logBenchEvent("USB", "Permission request sent for the current target device.");
        if (adapter.supportsMockBenchScenarios()) {
            lastPlaybackSnapshot = null;
            lastLoggedPlaybackState = null;
            rebuildPlanPreview();
            Toast.makeText(this, "Mock USB permission flow completed immediately.", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "USB permission request sent. Approve it on the device if prompted.", Toast.LENGTH_SHORT).show();
    }

    private void refreshSelectedUsbBackend() {
        UsbSerialKeyerRigControlAdapter adapter = selectedUsbSerialAdapter();
        if (adapter == null) {
            logBenchEvent("USB", "Refresh ignored because USB serial backend is not selected.");
            Toast.makeText(this, "USB serial backend is not selected.", Toast.LENGTH_SHORT).show();
            return;
        }
        logBenchEvent("USB", "Manual USB route refresh requested.");
        adapter.refreshRouteState();
        lastPlaybackSnapshot = null;
        lastLoggedPlaybackState = null;
        rebuildPlanPreview();
        Toast.makeText(this, "USB device inventory and route state refreshed.", Toast.LENGTH_SHORT).show();
    }

    private void releaseSelectedUsbKeyLine() {
        UsbSerialKeyerRigControlAdapter adapter = selectedUsbSerialAdapter();
        if (adapter == null) {
            logBenchEvent("USB", "Release Key Line ignored because USB serial backend is not selected.");
            Toast.makeText(this, "USB serial backend is not selected.", Toast.LENGTH_SHORT).show();
            return;
        }
        stopTx();
        boolean released = adapter.keyUp();
        adapter.refreshRouteState();
        lastPlaybackSnapshot = null;
        lastLoggedPlaybackState = null;
        rebuildPlanPreview();
        logBenchEvent(
                "USB",
                released
                        ? "Key line released and USB route state refreshed."
                        : "Release requested, but no open key line was active. Route state was still refreshed."
        );
        Toast.makeText(
                this,
                released
                        ? "USB key line released and route state reset."
                        : "No open USB key line was available to release. Route state was still refreshed.",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void applyUsbBenchPreset(CwTxPreset preset, int recommendedWpm) {
        UsbSerialKeyerRigControlAdapter adapter = selectedUsbSerialAdapter();
        if (adapter == null) {
            logBenchEvent("USB", "Bench preset ignored because USB serial backend is not selected.");
            Toast.makeText(this, "USB serial backend is not selected.", Toast.LENGTH_SHORT).show();
            return;
        }
        syncingFields = true;
        binding.txTextEditText.setText(preset.render(normalizedStationCallsign()));
        binding.wpmEditText.setText(String.valueOf(recommendedWpm));
        syncingFields = false;
        lastPlaybackSnapshot = null;
        lastLoggedPlaybackState = null;
        rebuildPlanPreview();
        logBenchEvent(
                "USB",
                "Loaded bench preset " + preset.name() + " at " + recommendedWpm + " WPM."
        );
        Toast.makeText(
                this,
                "Loaded a short USB bench macro. Confirm wiring first, then start TX.",
                Toast.LENGTH_SHORT
        ).show();
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
                    logBenchEvent(
                            "USB",
                            granted
                                    ? "USB permission granted by the system dialog."
                                    : "USB permission denied by the system dialog."
                    );
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
                    logBenchEvent("USB", "USB device attached: " + renderNullableDeviceName(extractUsbDeviceName(intent)) + ".");
                    handleUsbRouteChange(extractUsbDeviceName(intent), false);
                    return;
                }
                if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                    logBenchEvent("USB", "USB device detached: " + renderNullableDeviceName(extractUsbDeviceName(intent)) + ".");
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
        lastLoggedPlaybackState = null;
        rebuildPlanPreview();
        if (selectedUsbSerialAdapter() == null) {
            return;
        }
        if (detached && adapter != null && adapter.isPreferredDeviceMissing()) {
            String preferred = adapter.preferredDeviceName();
            logBenchEvent(
                    "USB",
                    "Locked target became unavailable: " + renderNullableDeviceName(preferred) + "."
            );
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
        RigProfile pinnedProfile = rigSelectionStore.selectedProfile();
        RigProfileSettings profileSettings = rigSelectionStore.loadSettings(pinnedProfile);
        String storedDeviceName = getSharedPreferences(PREFS_TX_CONSOLE, MODE_PRIVATE)
                .getString(PREF_USB_DEVICE_NAME, null);
        String storedKeyLine = getSharedPreferences(PREFS_TX_CONSOLE, MODE_PRIVATE)
                .getString(PREF_USB_KEY_LINE, null);
        String preferredDeviceName = storedDeviceName;
        if ((preferredDeviceName == null || preferredDeviceName.trim().isEmpty())
                && pinnedProfile != null
                && pinnedProfile.hasCapability(RigCapability.KEY_LINE_CONTROL)) {
            preferredDeviceName = profileSettings.usbPreferredDeviceName();
        }
        String preferredKeyLineName = storedKeyLine;
        if ((preferredKeyLineName == null || preferredKeyLineName.trim().isEmpty())
                && pinnedProfile != null
                && pinnedProfile.hasCapability(RigCapability.KEY_LINE_CONTROL)) {
            preferredKeyLineName = profileSettings.usbKeyLine().name();
        }
        if (preferredDeviceName != null && !preferredDeviceName.trim().isEmpty()) {
            adapter.selectDevice(preferredDeviceName);
        }
        if (preferredKeyLineName != null && !preferredKeyLineName.trim().isEmpty()) {
            try {
                adapter.setKeyLine(SerialKeyerTxOutput.KeyLine.valueOf(preferredKeyLineName));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private RigProfileSettings selectedRigProfileSettings() {
        RigProfile profile = rigSelectionStore.selectedProfile();
        return rigSelectionStore.loadSettings(profile);
    }

    private int resolvePreferredBackendSelectionIndex() {
        String preferredBackendId = preferredBackendIdForProfile(rigSelectionStore.selectedProfile());
        int preferredIndex = findBackendSelectionIndex(preferredBackendId);
        return preferredIndex >= 0 ? preferredIndex : 0;
    }

    private int findBackendSelectionIndex(String backendId) {
        if (backendId == null || backendId.trim().isEmpty()) {
            return -1;
        }
        for (int index = 0; index < backendOptions.size(); index++) {
            if (backendId.equals(backendOptions.get(index).backend().id())) {
                return index;
            }
        }
        return -1;
    }

    @Nullable
    private TxBackendOption findBackendOptionById(String backendId) {
        int index = findBackendSelectionIndex(backendId);
        if (index < 0) {
            return null;
        }
        return backendOptions.get(index);
    }

    @Nullable
    private String preferredBackendIdForProfile(@Nullable RigProfile profile) {
        if (profile == null || profile.adapterId() == null || profile.adapterId().trim().isEmpty()) {
            return null;
        }
        String adapterId = profile.adapterId().trim();
        String rigTextBackendId = "rig-text:" + adapterId;
        if (findBackendSelectionIndex(rigTextBackendId) >= 0) {
            return rigTextBackendId;
        }
        String rigKeyBackendId = "rig-key:" + adapterId;
        if (findBackendSelectionIndex(rigKeyBackendId) >= 0) {
            return rigKeyBackendId;
        }
        return rigTextBackendId;
    }

    private String renderMissingPreferredBackendLabel(@Nullable RigProfile profile) {
        if (profile == null) {
            return "Local Sidetone (no rig pinned)";
        }
        String preferredBackendId = preferredBackendIdForProfile(profile);
        if (preferredBackendId == null) {
            return "Local Sidetone";
        }
        return "Unavailable for " + profile.displayName();
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
        lastLoggedPlaybackState = null;
        rebuildPlanPreview();
        logBenchEvent("TX", "Loaded preset " + preset.name() + ".");
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
                continue;
            }
            if (adapter.supportsPttControl() || "generic-cat".equals(adapter.id())) {
                options.add(new TxBackendOption(new RigKeyingTxBackend(adapter)));
            }
        }
        return options;
    }

    private void stopAllBackends() {
        for (TxBackendOption option : backendOptions) {
            option.backend().stop();
        }
    }

    private void maybeLogBackendSelection(CwTxBackend backend) {
        if (backend == null || backend.id().equals(lastLoggedBackendId)) {
            return;
        }
        lastLoggedBackendId = backend.id();
        lastLoggedUsbDiagnosticCode = null;
        if (!backend.id().startsWith("rig-text:usb-serial-keyer")) {
            lastSignificantUsbDiagnosticSummary = null;
        }
        logBenchEvent("BACKEND", "Selected " + backend.displayName() + " (" + backend.id() + ").");
    }

    private void maybeLogUsbDiagnosticStage(UsbSerialKeyerRigControlAdapter adapter) {
        if (adapter == null) {
            lastLoggedUsbDiagnosticCode = null;
            return;
        }
        String diagnosticCode = adapter.diagnosticStageCode();
        if (diagnosticCode.equals(lastLoggedUsbDiagnosticCode)) {
            return;
        }
        lastLoggedUsbDiagnosticCode = diagnosticCode;
        captureSignificantUsbDiagnostic(adapter, diagnosticCode);
        logBenchEvent(
                "USB",
                "Diagnostic stage -> "
                        + adapter.diagnosticStageLabel()
                        + " ("
                        + diagnosticCode
                        + "). Next: "
                        + renderUsbRecoveryHint(adapter)
        );
    }

    private void maybeLogPlaybackSnapshot(CwTxPlaybackSnapshot snapshot) {
        if (snapshot == null || snapshot.state() == null || snapshot.state() == lastLoggedPlaybackState) {
            return;
        }
        lastLoggedPlaybackState = snapshot.state();
        if (snapshot.state() == CwTxState.PLAYING) {
            logBenchEvent("TX", "Playback started. " + snapshot.statusMessage());
            return;
        }
        if (snapshot.state() == CwTxState.COMPLETED) {
            logBenchEvent("TX", "Playback completed. " + snapshot.statusMessage());
            return;
        }
        if (snapshot.state() == CwTxState.ERROR) {
            logBenchEvent("TX", "Playback error. " + snapshot.statusMessage());
            return;
        }
        if (snapshot.state() == CwTxState.STOPPED) {
            logBenchEvent("TX", "Playback stopped. " + snapshot.statusMessage());
            return;
        }
        logBenchEvent("TX", "Playback state -> " + snapshot.state().displayName() + ".");
    }

    private void clearBenchLog() {
        benchLogBuffer.clear();
        lastLoggedUsbDiagnosticCode = null;
        lastLoggedPlaybackState = null;
        lastSignificantUsbDiagnosticSummary = null;
        renderBenchLog();
        renderBenchSummary(selectedBackend());
        Toast.makeText(this, "Bench log cleared.", Toast.LENGTH_SHORT).show();
    }

    private void copyBenchReport() {
        ClipboardManager clipboardManager = ContextCompat.getSystemService(this, ClipboardManager.class);
        if (clipboardManager == null) {
            Toast.makeText(this, "Clipboard is unavailable on this device.", Toast.LENGTH_SHORT).show();
            return;
        }
        String report = buildBenchReport();
        clipboardManager.setPrimaryClip(ClipData.newPlainText("CWCN TX Bench Report", report));
        logBenchEvent("SESSION", "Copied the current TX bench report to the clipboard.");
        Toast.makeText(this, "Bench report copied.", Toast.LENGTH_SHORT).show();
    }

    private String buildBenchReport() {
        CwTxBackend backend = selectedBackend();
        UsbSerialKeyerRigControlAdapter usbAdapter = selectedUsbSerialAdapter();
        String usbSummary = usbAdapter == null
                ? "USB serial route is not selected."
                : renderUsbRouteSummary(usbAdapter);
        String txStatus = binding.txStatusText.getText() == null
                ? null
                : binding.txStatusText.getText().toString();
        String txProgress = binding.txProgressText.getText() == null
                ? null
                : binding.txProgressText.getText().toString();
        return CwTxBenchReportFormatter.format(
                buildBenchSummary(backend),
                buildRecentUsbIssueSummary(usbAdapter),
                renderBackendSummary(backend),
                currentPlan == null ? null : renderPlanSummary(currentPlan),
                usbSummary,
                txStatus,
                txProgress,
                benchLogBuffer.renderMultiline()
        );
    }

    private void renderBenchSummary(CwTxBackend backend) {
        binding.benchSummaryText.setText(buildBenchSummary(backend));
    }

    private String buildBenchSummary(CwTxBackend backend) {
        UsbSerialKeyerRigControlAdapter usbAdapter = selectedUsbSerialAdapter();
        return CwTxBenchSummaryFormatter.format(
                backend == null ? null : backend.id(),
                backend == null ? null : backend.displayName(),
                backend != null && backend.isReady(),
                backend != null && backend.isRunning(),
                backend == null ? null : backend.describeAvailability(),
                usbAdapter == null ? null : usbAdapter.diagnosticStageCode(),
                buildRecentUsbIssueSummary(usbAdapter),
                lastPlaybackSnapshot == null ? null : lastPlaybackSnapshot.state(),
                backend == null ? null : renderBackendRecoveryHint(backend)
        );
    }

    private void captureSignificantUsbDiagnostic(
            UsbSerialKeyerRigControlAdapter adapter,
            String diagnosticCode
    ) {
        if (adapter == null || diagnosticCode == null || diagnosticCode.trim().isEmpty()) {
            return;
        }
        if ("usb-serial-ready".equals(diagnosticCode)) {
            return;
        }
        lastSignificantUsbDiagnosticSummary =
                currentBenchTimestamp()
                        + " - "
                        + adapter.diagnosticStageLabel()
                        + " ("
                        + diagnosticCode
                        + "). Next: "
                        + renderUsbRecoveryHint(adapter);
    }

    private String buildRecentUsbIssueSummary(@Nullable UsbSerialKeyerRigControlAdapter adapter) {
        if (adapter == null) {
            return null;
        }
        if (!"usb-serial-ready".equals(adapter.diagnosticStageCode())) {
            return null;
        }
        if (lastSignificantUsbDiagnosticSummary == null
                || lastSignificantUsbDiagnosticSummary.trim().isEmpty()) {
            return null;
        }
        return lastSignificantUsbDiagnosticSummary;
    }

    private void logBenchEvent(String category, String detail) {
        benchLogBuffer.append(currentBenchTimestamp(), category, detail);
        renderBenchLog();
    }

    private void renderBenchLog() {
        binding.benchLogText.setText(benchLogBuffer.renderMultiline());
    }

    private void dismissKeyboardAndClearNumericFocus() {
        clearFocusSafely(binding.wpmEditText);
        clearFocusSafely(binding.toneFrequencyEditText);
        binding.txScrollView.requestFocus();
        InputMethodManager imm = ContextCompat.getSystemService(this, InputMethodManager.class);
        if (imm != null) {
            View tokenView = getCurrentFocus();
            if (tokenView == null) {
                tokenView = binding.txScrollView;
            }
            imm.hideSoftInputFromWindow(tokenView.getWindowToken(), 0);
        }
    }

    private void clearFocusSafely(View view) {
        if (view != null && view.hasFocus()) {
            view.clearFocus();
        }
    }

    private void preserveScrollPosition(Runnable update) {
        if (binding == null || binding.txScrollView == null) {
            update.run();
            return;
        }
        int scrollY = binding.txScrollView.getScrollY();
        update.run();
        binding.txScrollView.post(() -> binding.txScrollView.scrollTo(0, scrollY));
    }

    private String currentBenchTimestamp() {
        return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
    }

    private String renderNullableDeviceName(String deviceName) {
        return deviceName == null || deviceName.trim().isEmpty()
                ? "(unknown device)"
                : deviceName;
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
