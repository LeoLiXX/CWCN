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

import org.bi9clt.cwcn.R;
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
    private CwTxBenchLogBuffer sessionLogBuffer;

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
    private String lastLoggedUsbRouteStageCode;
    private CwTxState lastLoggedPlaybackState;
    private String lastNotableUsbRouteIssueSummary;

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
        sessionLogBuffer = new CwTxBenchLogBuffer(BENCH_LOG_LIMIT);
        restoreUsbRoutePreferences();
        registerUsbPermissionReceiver();
        setupBackendSelector();
        setupPresetSelector();
        setupUsbRouteControls();
        setupDefaults();
        setupActions();
        renderSessionLog();
        rebuildPlanPreview();
        logSessionEvent("SESSION", getString(R.string.tx_log_console_open));
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
        binding.loadUsbDitTestButton.setOnClickListener(view -> applyUsbValidationPreset(CwTxPreset.BENCH_DIT, 12));
        binding.loadUsbPatternTestButton.setOnClickListener(view -> applyUsbValidationPreset(CwTxPreset.BENCH_PATTERN, 15));
        binding.releaseUsbKeyLineButton.setOnClickListener(view -> releaseSelectedUsbKeyLine());
        binding.copyBenchReportButton.setOnClickListener(view -> copySessionReport());
        binding.clearBenchLogButton.setOnClickListener(view -> clearSessionLog());
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
                    logSessionEvent(
                            "USB",
                            option.isAuto()
                                    ? getString(R.string.tx_log_usb_device_auto)
                                    : getString(R.string.tx_log_usb_device_locked, option.deviceName())
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
                    logSessionEvent("MOCK", getString(R.string.tx_log_mock_scenario_switched, scenario.displayName()));
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
                    logSessionEvent("USB", getString(R.string.tx_log_usb_key_line_switched, item));
                    rebuildPlanPreview();
                } else {
                    Toast.makeText(TxActivity.this, R.string.tx_toast_key_line_busy, Toast.LENGTH_SHORT).show();
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
                    ? getString(R.string.tx_plan_preview_empty)
                    : currentPlan.normalizedText());
            binding.morsePreviewText.setText(currentPlan.morsePreview().isEmpty()
                    ? getString(R.string.tx_morse_preview_empty)
                    : currentPlan.morsePreview());
            binding.planSummaryText.setText(renderPlanSummary(currentPlan));
            binding.pinnedRigSummaryText.setText(renderPinnedRigSummary());
            binding.backendSummaryText.setText(renderBackendSummary(backend));
            binding.routeChecklistText.setText(CwTxRouteAdvisor.buildChecklist(backend, currentPlan));
            refreshRouteControls(backend);
            renderSessionSummary(backend);
            if (lastPlaybackSnapshot == null || !backend.isRunning()) {
                binding.txStatusText.setText(renderIdleStatus(backend));
                binding.txProgressText.setText(R.string.tx_progress_zero);
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
            logSessionEvent("TX", getString(R.string.tx_log_start_blocked_empty));
            Toast.makeText(this, R.string.tx_toast_no_sendable_content, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!backend.isReady()) {
            logSessionEvent(
                    "TX",
                    getString(
                            R.string.tx_log_start_blocked_unavailable,
                            backend.displayName(),
                            backend.describeAvailability()
                    )
            );
            Toast.makeText(
                    this,
                    getString(
                            R.string.tx_toast_backend_unavailable,
                            backend.describeAvailability(),
                            renderBackendRecoveryHint(backend)
                    ),
                    Toast.LENGTH_LONG
            ).show();
            binding.txStatusText.setText(renderIdleStatus(backend));
            refreshButtons();
            return;
        }
        if (backend.isRunning()) {
            logSessionEvent("TX", getString(R.string.tx_log_start_ignored_running));
            Toast.makeText(this, R.string.tx_toast_tx_running, Toast.LENGTH_SHORT).show();
            return;
        }
        lastLoggedPlaybackState = null;
        logSessionEvent(
                "TX",
                getString(
                        R.string.tx_log_start_requested,
                        backend.displayName(),
                        currentPlan.elements().size()
                )
        );
        boolean started = backend.start(currentPlan, snapshot ->
                runOnUiThread(() -> applyPlaybackSnapshot(snapshot)));
        if (!started) {
            String recoveryHint = renderBackendRecoveryHint(backend);
            logSessionEvent(
                "TX",
                getString(R.string.tx_log_start_failed, backend.displayName(), recoveryHint)
            );
            binding.txStatusText.setText(getString(R.string.tx_status_start_failed, recoveryHint));
            Toast.makeText(this, getString(R.string.tx_toast_start_failed, recoveryHint), Toast.LENGTH_LONG).show();
        }
        refreshButtons();
    }

    private void stopTx() {
        CwTxBackend backend = selectedBackend();
        boolean wasRunning = backend.isRunning();
        backend.stop();
        lastLoggedPlaybackState = null;
        logSessionEvent(
                "TX",
                wasRunning
                        ? getString(R.string.tx_log_stop_requested, backend.displayName())
                        : getString(R.string.tx_log_stop_idle, backend.displayName())
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
            renderSessionSummary(selectedBackend());
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
        return getString(
                R.string.tx_plan_summary,
                plan.wpm(),
                plan.toneFrequencyHz(),
                plan.dotDurationMs(),
                plan.elements().size(),
                formatMs(plan.totalDurationMs())
        );
    }

    private String renderBackendSummary(CwTxBackend backend) {
        return getString(
                R.string.tx_backend_summary,
                backend.displayName(),
                backend.describeRoute(),
                yesNo(backend.isReady()),
                backend.describeAvailability(),
                yesNo(backend.usesWpm()),
                yesNo(backend.usesToneFrequency()),
                yesNo(backend.supportsLivePlanProfile()),
                yesNo(backend.supportsProgressSnapshots())
        );
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
        builder.append(getString(
                R.string.tx_pinned_preferred_path,
                preferredOption == null
                        ? renderMissingPreferredBackendLabel(profile)
                        : preferredOption.backend().displayName()
        ));
        builder.append(getString(
                R.string.tx_pinned_current_path,
                activeBackend == null ? getString(R.string.tx_status_current_element_empty) : activeBackend.displayName()
        ));
        if (preferredOption != null
                && activeBackend != null
                && !preferredOption.backend().id().equals(activeBackend.id())) {
            builder.append(getString(R.string.tx_pinned_note_temporary_backend));
        } else if (preferredOption == null && profile != null) {
            builder.append(getString(R.string.tx_pinned_note_no_text_backend));
        }
        return builder.toString();
    }

    private String renderTxStatus(CwTxPlaybackSnapshot snapshot) {
        if (snapshot == null) {
            return renderIdleStatus(selectedBackend());
        }
        CwTxBackend backend = selectedBackend();
        String activeLabel = isDedicatedKeyingBackend(backend)
                ? getString(R.string.tx_status_active_key_line)
                : getString(R.string.tx_status_active_tone);
        return getString(
                R.string.tx_status_playback,
                snapshot.state().displayName(),
                snapshot.statusMessage(),
                snapshot.currentElementLabel().isEmpty()
                        ? getString(R.string.tx_status_current_element_empty)
                        : snapshot.currentElementLabel(),
                activeLabel,
                yesNo(snapshot.toneActive()),
                formatMs(snapshot.elapsedMs()),
                formatMs(snapshot.totalDurationMs())
        );
    }

    private String renderTxProgress(CwTxPlaybackSnapshot snapshot) {
        if (snapshot == null) {
            return getString(R.string.tx_progress_zero);
        }
        return getString(
                R.string.tx_progress,
                Math.round(snapshot.completionRatio() * 100.0d),
                snapshot.completedElementCount(),
                snapshot.totalElementCount()
        );
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
        return getString(R.string.tx_duration_format, seconds, millis);
    }

    private String yesNo(boolean value) {
        return getString(value ? R.string.status_yes : R.string.status_no);
    }

    private String renderIdleStatus(CwTxBackend backend) {
        if (backend.isReady()) {
            if (isDedicatedKeyingBackend(backend)) {
                return getString(R.string.tx_idle_ready_keying);
            }
            return getString(R.string.tx_idle_ready_general);
        }
        return getString(
                R.string.tx_idle_not_ready,
                backend.describeAvailability(),
                renderBackendRecoveryHint(backend)
        );
    }

    private boolean isDedicatedKeyingBackend(CwTxBackend backend) {
        return backend != null && backend.id() != null && backend.id().startsWith("rig-key:");
    }

    private void refreshRouteControls(CwTxBackend backend) {
        UsbSerialKeyerRigControlAdapter usbAdapter = selectedUsbSerialAdapter();
        boolean usbVisible = backend != null && usbAdapter != null;
        binding.usbRoutePanel.setVisibility(usbVisible ? View.VISIBLE : View.GONE);
        if (!usbVisible) {
            lastLoggedUsbRouteStageCode = null;
            binding.mockUsbScenarioPanel.setVisibility(View.GONE);
            return;
        }
        java.util.List<UsbSerialDeviceOption> deviceOptions = refreshUsbDeviceSelection(usbAdapter);
        binding.usbDeviceText.setText(renderUsbRouteSummary(usbAdapter));
        syncUsbKeyLineSelection(usbAdapter);
        syncMockUsbScenarioSelection(usbAdapter);
        maybeLogUsbRouteStage(usbAdapter);
        boolean hasCandidateDevices = hasRealUsbDeviceOption(deviceOptions);
        boolean hasTargetDevice = usbAdapter.hasTargetDevice();
        boolean needsPermission = hasTargetDevice && !usbAdapter.isReady();
        binding.usbDeviceSpinner.setEnabled(hasCandidateDevices);
        binding.refreshUsbDevicesButton.setEnabled(true);
        binding.requestUsbPermissionButton.setEnabled(needsPermission);
        binding.releaseUsbKeyLineButton.setEnabled(hasTargetDevice || usbAdapter.isReady());
        binding.requestUsbPermissionButton.setText(needsPermission
                ? getString(R.string.tx_usb_permission_request)
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
        String targetMode = adapter.hasPreferredDeviceSelection()
                ? getString(R.string.tx_usb_route_mode_locked, adapter.preferredDeviceName())
                : getString(R.string.tx_usb_route_mode_auto);
        return getString(
                R.string.tx_usb_route_summary,
                targetMode,
                renderUsbTargetState(adapter),
                adapter.diagnosticStageLabel(),
                adapter.diagnosticStageCode(),
                adapter.describeMatchedDevice(),
                adapter.describeAvailability(),
                renderUsbRecoveryHint(adapter)
        );
    }

    private String renderUsbTargetState(UsbSerialKeyerRigControlAdapter adapter) {
        if (adapter.isPreferredDeviceMissing()) {
            return getString(R.string.tx_usb_target_state_missing_locked);
        }
        if (adapter.hasTargetDevice()) {
            return adapter.hasPreferredDeviceSelection()
                    ? getString(R.string.tx_usb_target_state_locked_available)
                    : getString(R.string.tx_usb_target_state_auto_available);
        }
        if (adapter.hasAnyCandidateDevice()) {
            return getString(R.string.tx_usb_target_state_candidates);
        }
        return getString(R.string.tx_usb_target_state_none);
    }

    private String renderUsbPermissionButtonLabel(
            UsbSerialKeyerRigControlAdapter adapter,
            boolean hasCandidateDevices
    ) {
        if (!hasCandidateDevices) {
            return getString(R.string.tx_usb_permission_no_device);
        }
        if (!adapter.hasTargetDevice()) {
            return getString(R.string.tx_usb_permission_target_missing);
        }
        return getString(R.string.tx_usb_permission_ready);
    }

    private String renderBackendRecoveryHint(CwTxBackend backend) {
        if (backend == null) {
            return getString(R.string.tx_recovery_select_path);
        }
        if ("local-sidetone".equals(backend.id())) {
            return getString(R.string.tx_recovery_local_sidetone);
        }
        if ("rig-text:audio-vox-text".equals(backend.id())) {
            return getString(R.string.tx_recovery_audio_vox);
        }
        if ("rig-key:generic-cat".equals(backend.id())) {
            RigProfile pinnedProfile = rigSelectionStore == null ? null : rigSelectionStore.selectedProfile();
            if (RigProfileFamilies.isYaesuFamily(pinnedProfile)) {
                return getString(R.string.tx_recovery_generic_cat_yaesu);
            }
            if (RigProfileFamilies.isIcomFamily(pinnedProfile)) {
                return getString(R.string.tx_recovery_generic_cat_icom);
            }
            if (RigProfileFamilies.isKenwoodFamily(pinnedProfile)) {
                return getString(R.string.tx_recovery_generic_cat_kenwood);
            }
            return getString(R.string.tx_recovery_generic_cat_generic);
        }
        if ("rig-text:hamlib-rigctld".equals(backend.id())) {
            RigProfile pinnedProfile = rigSelectionStore == null ? null : rigSelectionStore.selectedProfile();
            if (RigProfileFamilies.isYaesuFamily(pinnedProfile)) {
                return getString(R.string.tx_recovery_hamlib_yaesu);
            }
            if (RigProfileFamilies.isIcomFamily(pinnedProfile)) {
                return getString(R.string.tx_recovery_hamlib_icom);
            }
            if (RigProfileFamilies.isKenwoodFamily(pinnedProfile)) {
                return getString(R.string.tx_recovery_hamlib_kenwood);
            }
            return getString(R.string.tx_recovery_hamlib_generic);
        }
        UsbSerialKeyerRigControlAdapter usbAdapter = selectedUsbSerialAdapter();
        if (usbAdapter != null) {
            return renderUsbRecoveryHint(usbAdapter);
        }
        return getString(R.string.tx_recovery_check_route);
    }

    private String renderUsbRecoveryHint(UsbSerialKeyerRigControlAdapter adapter) {
        String diagnosticCode = adapter.diagnosticStageCode();
        if ("usb-serial-target-missing".equals(diagnosticCode) || adapter.isPreferredDeviceMissing()) {
            return getString(R.string.tx_usb_recovery_target_missing);
        }
        if ("usb-serial-no-device".equals(diagnosticCode) || !adapter.hasAnyCandidateDevice()) {
            return getString(R.string.tx_usb_recovery_no_device);
        }
        if ("usb-serial-no-cdc".equals(diagnosticCode)) {
            return getString(R.string.tx_usb_recovery_no_cdc);
        }
        if ("usb-serial-no-control-interface".equals(diagnosticCode)) {
            return getString(R.string.tx_usb_recovery_no_control_interface);
        }
        if ("usb-serial-open-failed".equals(diagnosticCode)) {
            return getString(R.string.tx_usb_recovery_open_failed);
        }
        if ("usb-serial-claim-failed".equals(diagnosticCode)) {
            return getString(R.string.tx_usb_recovery_claim_failed);
        }
        if (!adapter.hasTargetDevice()) {
            return getString(R.string.tx_usb_recovery_select_target);
        }
        if ("usb-serial-no-permission".equals(diagnosticCode) || !adapter.isReady()) {
            return getString(R.string.tx_usb_recovery_permission);
        }
        return getString(R.string.tx_usb_recovery_ready_test);
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
            logSessionEvent("USB", getString(R.string.tx_log_usb_permission_ignored));
            Toast.makeText(this, R.string.tx_toast_no_usb_backend_selected, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!adapter.hasTargetDevice()) {
            logSessionEvent("USB", getString(R.string.tx_log_usb_permission_blocked, adapter.describeAvailability()));
            Toast.makeText(this, adapter.describeAvailability(), Toast.LENGTH_SHORT).show();
            rebuildPlanPreview();
            return;
        }
        if (adapter.isReady()) {
            logSessionEvent("USB", getString(R.string.tx_log_usb_permission_skipped_ready));
            Toast.makeText(this, R.string.tx_toast_usb_backend_ready, Toast.LENGTH_SHORT).show();
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
            logSessionEvent("USB", getString(R.string.tx_log_usb_permission_failed, adapter.describeAvailability()));
            Toast.makeText(this, adapter.describeAvailability(), Toast.LENGTH_SHORT).show();
            rebuildPlanPreview();
            return;
        }
        logSessionEvent("USB", getString(R.string.tx_log_usb_permission_requested));
        if (adapter.supportsMockBenchScenarios()) {
            lastPlaybackSnapshot = null;
            lastLoggedPlaybackState = null;
            rebuildPlanPreview();
            Toast.makeText(this, R.string.tx_toast_usb_mock_permission_done, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, R.string.tx_toast_usb_permission_requested, Toast.LENGTH_SHORT).show();
    }

    private void refreshSelectedUsbBackend() {
        UsbSerialKeyerRigControlAdapter adapter = selectedUsbSerialAdapter();
        if (adapter == null) {
            logSessionEvent("USB", getString(R.string.tx_log_usb_refresh_ignored));
            Toast.makeText(this, R.string.tx_toast_no_usb_backend_selected, Toast.LENGTH_SHORT).show();
            return;
        }
        logSessionEvent("USB", getString(R.string.tx_log_usb_refresh_requested));
        adapter.refreshRouteState();
        lastPlaybackSnapshot = null;
        lastLoggedPlaybackState = null;
        rebuildPlanPreview();
        Toast.makeText(this, R.string.tx_toast_usb_refreshed, Toast.LENGTH_SHORT).show();
    }

    private void releaseSelectedUsbKeyLine() {
        UsbSerialKeyerRigControlAdapter adapter = selectedUsbSerialAdapter();
        if (adapter == null) {
            logSessionEvent("USB", getString(R.string.tx_log_usb_release_ignored));
            Toast.makeText(this, R.string.tx_toast_no_usb_backend_selected, Toast.LENGTH_SHORT).show();
            return;
        }
        stopTx();
        boolean released = adapter.keyUp();
        adapter.refreshRouteState();
        lastPlaybackSnapshot = null;
        lastLoggedPlaybackState = null;
        rebuildPlanPreview();
        logSessionEvent(
                "USB",
                released
                        ? getString(R.string.tx_log_usb_release_done)
                        : getString(R.string.tx_log_usb_release_none)
        );
        Toast.makeText(
                this,
                released
                        ? getString(R.string.tx_toast_usb_release_done)
                        : getString(R.string.tx_toast_usb_release_none),
                Toast.LENGTH_SHORT
        ).show();
    }

    private void applyUsbValidationPreset(CwTxPreset preset, int recommendedWpm) {
        UsbSerialKeyerRigControlAdapter adapter = selectedUsbSerialAdapter();
        if (adapter == null) {
            logSessionEvent("USB", getString(R.string.tx_log_usb_validation_ignored));
            Toast.makeText(this, R.string.tx_toast_no_usb_backend_selected, Toast.LENGTH_SHORT).show();
            return;
        }
        syncingFields = true;
        binding.txTextEditText.setText(preset.render(normalizedStationCallsign()));
        binding.wpmEditText.setText(String.valueOf(recommendedWpm));
        syncingFields = false;
        lastPlaybackSnapshot = null;
        lastLoggedPlaybackState = null;
        rebuildPlanPreview();
        logSessionEvent(
                "USB",
                getString(R.string.tx_log_usb_validation_loaded, preset.displayName(), recommendedWpm)
        );
        Toast.makeText(
                this,
                R.string.tx_toast_usb_validation_loaded,
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
                    logSessionEvent(
                            "USB",
                            granted
                                    ? getString(R.string.tx_log_usb_permission_granted)
                                    : getString(R.string.tx_log_usb_permission_denied)
                    );
                    Toast.makeText(
                            TxActivity.this,
                            granted
                                    ? getString(R.string.tx_toast_usb_permission_granted)
                                    : getString(R.string.tx_toast_usb_permission_denied),
                            Toast.LENGTH_SHORT
                    ).show();
                    handleUsbRouteChange(null, false);
                    return;
                }
                if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                    logSessionEvent(
                            "USB",
                            getString(
                                    R.string.tx_log_usb_device_attached,
                                    renderNullableDeviceName(extractUsbDeviceName(intent))
                            )
                    );
                    handleUsbRouteChange(extractUsbDeviceName(intent), false);
                    return;
                }
                if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                    logSessionEvent(
                            "USB",
                            getString(
                                    R.string.tx_log_usb_device_detached,
                                    renderNullableDeviceName(extractUsbDeviceName(intent))
                            )
                    );
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
            logSessionEvent(
                    "USB",
                    getString(R.string.tx_log_usb_preferred_unavailable, renderNullableDeviceName(preferred))
            );
            Toast.makeText(
                    this,
                    getString(
                            R.string.tx_toast_usb_selected_detached,
                            preferred == null ? getString(R.string.tx_unknown_device) : preferred
                    ),
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }
        if (deviceName != null && !deviceName.isEmpty()) {
            Toast.makeText(
                    this,
                    (detached
                            ? getString(R.string.tx_usb_device_detached_prefix)
                            : getString(R.string.tx_usb_device_attached_prefix)) + deviceName,
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
            return getString(R.string.tx_missing_preferred_backend_no_profile);
        }
        String preferredBackendId = preferredBackendIdForProfile(profile);
        if (preferredBackendId == null) {
            return getString(R.string.tx_missing_preferred_backend_local);
        }
        return getString(R.string.tx_missing_preferred_backend_unavailable, profile.displayName());
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
        logSessionEvent("TX", getString(R.string.tx_log_preset_loaded, preset.displayName()));
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
        lastLoggedUsbRouteStageCode = null;
        if (!backend.id().startsWith("rig-text:usb-serial-keyer")) {
            lastNotableUsbRouteIssueSummary = null;
        }
        logSessionEvent("BACKEND", getString(R.string.tx_log_backend_switched, backend.displayName(), backend.id()));
    }

    private void maybeLogUsbRouteStage(UsbSerialKeyerRigControlAdapter adapter) {
        if (adapter == null) {
            lastLoggedUsbRouteStageCode = null;
            return;
        }
        String diagnosticCode = adapter.diagnosticStageCode();
        if (diagnosticCode.equals(lastLoggedUsbRouteStageCode)) {
            return;
        }
        lastLoggedUsbRouteStageCode = diagnosticCode;
        captureNotableUsbRouteIssue(adapter, diagnosticCode);
        logSessionEvent(
                "USB",
                getString(
                        R.string.tx_log_usb_stage_changed,
                        adapter.diagnosticStageLabel(),
                        diagnosticCode,
                        renderUsbRecoveryHint(adapter)
                )
        );
    }

    private void maybeLogPlaybackSnapshot(CwTxPlaybackSnapshot snapshot) {
        if (snapshot == null || snapshot.state() == null || snapshot.state() == lastLoggedPlaybackState) {
            return;
        }
        lastLoggedPlaybackState = snapshot.state();
        if (snapshot.state() == CwTxState.PLAYING) {
            logSessionEvent("TX", getString(R.string.tx_log_tx_started, snapshot.statusMessage()));
            return;
        }
        if (snapshot.state() == CwTxState.COMPLETED) {
            logSessionEvent("TX", getString(R.string.tx_log_tx_completed, snapshot.statusMessage()));
            return;
        }
        if (snapshot.state() == CwTxState.ERROR) {
            logSessionEvent("TX", getString(R.string.tx_log_tx_error, snapshot.statusMessage()));
            return;
        }
        if (snapshot.state() == CwTxState.STOPPED) {
            logSessionEvent("TX", getString(R.string.tx_log_tx_stopped, snapshot.statusMessage()));
            return;
        }
        logSessionEvent("TX", getString(R.string.tx_log_tx_state_changed, snapshot.state().displayName()));
    }

    private void clearSessionLog() {
        sessionLogBuffer.clear();
        lastLoggedUsbRouteStageCode = null;
        lastLoggedPlaybackState = null;
        lastNotableUsbRouteIssueSummary = null;
        renderSessionLog();
        renderSessionSummary(selectedBackend());
        Toast.makeText(this, R.string.tx_toast_session_log_cleared, Toast.LENGTH_SHORT).show();
    }

    private void copySessionReport() {
        ClipboardManager clipboardManager = ContextCompat.getSystemService(this, ClipboardManager.class);
        if (clipboardManager == null) {
            Toast.makeText(this, R.string.tx_toast_clipboard_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        String report = buildSessionReport();
        clipboardManager.setPrimaryClip(ClipData.newPlainText(getString(R.string.tx_session_report_clip_label), report));
        logSessionEvent("SESSION", getString(R.string.tx_log_session_report_copied));
        Toast.makeText(this, R.string.tx_toast_session_report_copied, Toast.LENGTH_SHORT).show();
    }

    private String buildSessionReport() {
        CwTxBackend backend = selectedBackend();
        UsbSerialKeyerRigControlAdapter usbAdapter = selectedUsbSerialAdapter();
        String usbSummary = usbAdapter == null
                ? getString(R.string.tx_usb_summary_none_selected)
                : renderUsbRouteSummary(usbAdapter);
        String txStatus = binding.txStatusText.getText() == null
                ? null
                : binding.txStatusText.getText().toString();
        String txProgress = binding.txProgressText.getText() == null
                ? null
                : binding.txProgressText.getText().toString();
        return CwTxBenchReportFormatter.format(
                buildSessionSummary(backend),
                buildRecentUsbRouteIssueSummary(usbAdapter),
                renderBackendSummary(backend),
                currentPlan == null ? null : renderPlanSummary(currentPlan),
                usbSummary,
                txStatus,
                txProgress,
                sessionLogBuffer.renderMultiline()
        );
    }

    private void renderSessionSummary(CwTxBackend backend) {
        binding.benchSummaryText.setText(buildSessionSummary(backend));
    }

    private String buildSessionSummary(CwTxBackend backend) {
        UsbSerialKeyerRigControlAdapter usbAdapter = selectedUsbSerialAdapter();
        return CwTxBenchSummaryFormatter.format(
                backend == null ? null : backend.id(),
                backend == null ? null : backend.displayName(),
                backend != null && backend.isReady(),
                backend != null && backend.isRunning(),
                backend == null ? null : backend.describeAvailability(),
                usbAdapter == null ? null : usbAdapter.diagnosticStageCode(),
                buildRecentUsbRouteIssueSummary(usbAdapter),
                lastPlaybackSnapshot == null ? null : lastPlaybackSnapshot.state(),
                backend == null ? null : renderBackendRecoveryHint(backend)
        );
    }

    private void captureNotableUsbRouteIssue(
            UsbSerialKeyerRigControlAdapter adapter,
            String diagnosticCode
    ) {
        if (adapter == null || diagnosticCode == null || diagnosticCode.trim().isEmpty()) {
            return;
        }
        if ("usb-serial-ready".equals(diagnosticCode)) {
            return;
        }
        lastNotableUsbRouteIssueSummary =
                getString(
                        R.string.tx_log_usb_issue_summary,
                        currentSessionTimestamp(),
                        adapter.diagnosticStageLabel(),
                        diagnosticCode,
                        renderUsbRecoveryHint(adapter)
                );
    }

    private String buildRecentUsbRouteIssueSummary(@Nullable UsbSerialKeyerRigControlAdapter adapter) {
        if (adapter == null) {
            return null;
        }
        if (!"usb-serial-ready".equals(adapter.diagnosticStageCode())) {
            return null;
        }
        if (lastNotableUsbRouteIssueSummary == null
                || lastNotableUsbRouteIssueSummary.trim().isEmpty()) {
            return null;
        }
        return lastNotableUsbRouteIssueSummary;
    }

    private void logSessionEvent(String category, String detail) {
        sessionLogBuffer.append(currentSessionTimestamp(), category, detail);
        renderSessionLog();
    }

    private void renderSessionLog() {
        binding.benchLogText.setText(sessionLogBuffer.renderMultiline());
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

    private String currentSessionTimestamp() {
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
    }

    private String renderNullableDeviceName(String deviceName) {
        return deviceName == null || deviceName.trim().isEmpty()
                ? getString(R.string.tx_unknown_device)
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

