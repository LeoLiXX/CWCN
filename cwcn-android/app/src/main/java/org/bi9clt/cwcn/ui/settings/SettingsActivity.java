package org.bi9clt.cwcn.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.bi9clt.cwcn.BuildConfig;
import org.bi9clt.cwcn.core.app.DeveloperModeStore;
import org.bi9clt.cwcn.core.app.RouteFallbackStore;
import org.bi9clt.cwcn.core.app.StationProfileStore;
import org.bi9clt.cwcn.core.log.AppOverviewSnapshot;
import org.bi9clt.cwcn.core.log.ConfirmedQsoLog;
import org.bi9clt.cwcn.core.log.LocalLogRepository;
import org.bi9clt.cwcn.core.qso.QsoDraftSnapshot;
import org.bi9clt.cwcn.core.rig.CatProtocolFamily;
import org.bi9clt.cwcn.core.rig.RigCapability;
import org.bi9clt.cwcn.core.rig.RigProfile;
import org.bi9clt.cwcn.core.rig.RigProfileSettings;
import org.bi9clt.cwcn.core.rig.RigSelectionStore;
import org.bi9clt.cwcn.core.rig.KeyingPolarity;
import org.bi9clt.cwcn.core.rig.SerialKeyerTxOutput;
import org.bi9clt.cwcn.databinding.ActivitySettingsBinding;
import org.bi9clt.cwcn.ui.developer.DeveloperToolsActivity;
import org.bi9clt.cwcn.ui.rig.RigSetupActivity;

public final class SettingsActivity extends AppCompatActivity {
    private ActivitySettingsBinding binding;
    private DeveloperModeStore developerModeStore;
    private StationProfileStore stationProfileStore;
    private RigSelectionStore rigSelectionStore;
    private RouteFallbackStore routeFallbackStore;
    private LocalLogRepository localLogRepository;
    private boolean syncingEditors;
    private boolean stationProfileDirty;
    private boolean cwDefaultsDirty;
    private boolean routeSettingsDirty;
    private boolean routeFallbackDirty;
    private String stationProfileStatusMessage = "";
    private String cwDefaultsStatusMessage = "";
    private String routeSettingsStatusMessage = "";
    private String routeFallbackStatusMessage = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        developerModeStore = new DeveloperModeStore(this);
        stationProfileStore = new StationProfileStore(this);
        rigSelectionStore = new RigSelectionStore(this);
        routeFallbackStore = new RouteFallbackStore(this);
        localLogRepository = new LocalLogRepository(this);
        binding.versionText.setText("Settings " + BuildConfig.VERSION_NAME);
        setupEditorWatchers();
        setupRouteEditorControls();
        setupRouteFallbackControls();
        setupInfoButtons();
        setupActions();
        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUi();
    }

    private void setupEditorWatchers() {
        TextWatcher stationWatcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                if (!syncingEditors) {
                    stationProfileDirty = true;
                    binding.stationProfileStatusText.setText(renderStationProfileEditorStatus());
                }
            }
        };
        TextWatcher cwDefaultsWatcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                if (!syncingEditors) {
                    cwDefaultsDirty = true;
                    binding.cwDefaultsStatusText.setText(renderCwDefaultsEditorStatus());
                }
            }
        };
        binding.stationCallsignEditText.addTextChangedListener(stationWatcher);
        binding.operatorNameEditText.addTextChangedListener(stationWatcher);
        binding.qthEditText.addTextChangedListener(stationWatcher);
        binding.defaultWpmEditText.addTextChangedListener(cwDefaultsWatcher);
        binding.defaultToneEditText.addTextChangedListener(cwDefaultsWatcher);
        TextWatcher routeWatcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                if (!syncingEditors) {
                    routeSettingsDirty = true;
                    binding.routeSettingsStatusText.setText(renderRouteSettingsEditorStatus());
                }
            }
        };
        binding.serialCatPortHintEditText.addTextChangedListener(routeWatcher);
        binding.serialCatBaudRateEditText.addTextChangedListener(routeWatcher);
        binding.serialCatKeyingPortHintEditText.addTextChangedListener(routeWatcher);
        binding.serialCatCivAddressEditText.addTextChangedListener(routeWatcher);
        binding.networkHostEditText.addTextChangedListener(routeWatcher);
        binding.networkPortEditText.addTextChangedListener(routeWatcher);
        binding.usbPreferredDeviceNameEditText.addTextChangedListener(routeWatcher);
    }

    private void setupRouteEditorControls() {
        ArrayAdapter<SerialKeyerTxOutput.KeyLine> keyLineAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                SerialKeyerTxOutput.KeyLine.values()
        );
        keyLineAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.serialCatKeyLineSpinner.setAdapter(keyLineAdapter);
        binding.usbKeyLineSpinner.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                SerialKeyerTxOutput.KeyLine.values()
        ));
        ((ArrayAdapter<?>) binding.usbKeyLineSpinner.getAdapter()).setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        ArrayAdapter<KeyingPolarity> keyingPolarityAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                KeyingPolarity.values()
        );
        keyingPolarityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.serialCatKeyingPolaritySpinner.setAdapter(keyingPolarityAdapter);

        binding.serialCatKeyLineSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!syncingEditors) {
                    routeSettingsDirty = true;
                    binding.routeSettingsStatusText.setText(renderRouteSettingsEditorStatus());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        binding.serialCatKeyingPolaritySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!syncingEditors) {
                    routeSettingsDirty = true;
                    binding.routeSettingsStatusText.setText(renderRouteSettingsEditorStatus());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        binding.usbKeyLineSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!syncingEditors) {
                    routeSettingsDirty = true;
                    binding.routeSettingsStatusText.setText(renderRouteSettingsEditorStatus());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupRouteFallbackControls() {
        ArrayAdapter<RouteFallbackStore.Mode> fallbackModeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                RouteFallbackStore.Mode.values()
        );
        fallbackModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.routeFallbackModeSpinner.setAdapter(fallbackModeAdapter);
        binding.routeFallbackModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!syncingEditors) {
                    routeFallbackDirty = true;
                    binding.routeFallbackStatusText.setText(renderRouteFallbackEditorStatus());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupActions() {
        binding.backButton.setOnClickListener(view -> finish());
        binding.saveStationProfileButton.setOnClickListener(view -> saveStationProfile());
        binding.openRigSetupButton.setOnClickListener(view ->
                startActivity(new Intent(this, RigSetupActivity.class)));
        binding.saveCwDefaultsButton.setOnClickListener(view -> saveCwDefaults());
        binding.saveRouteSettingsButton.setOnClickListener(view -> saveRouteSettings());
        binding.saveRouteFallbackButton.setOnClickListener(view -> saveRouteFallback());
        binding.toggleDeveloperModeButton.setOnClickListener(view -> {
            developerModeStore.toggle();
            refreshUi();
        });
        binding.openDeveloperToolsButton.setOnClickListener(view ->
                startActivity(new Intent(this, DeveloperToolsActivity.class)));
    }

    private void setupInfoButtons() {
        binding.stationInfoButton.setOnClickListener(view -> showInfoDialog(
                "Station",
                "Station identity and daily defaults live here. Saved callsign, operator name, and QTH are reused by Operate, macros, and draft building."
        ));
        binding.radioRouteInfoButton.setOnClickListener(view -> showInfoDialog(
                "Radio Route",
                "Choose the formal rig path here. If no rig is pinned, the no-radio fallback rule decides whether the app should use the phone microphone and phone audio."
        ));
        binding.routeFallbackInfoButton.setOnClickListener(view -> showInfoDialog(
                "No-Radio Fallback",
                "Auto means RX falls back to the phone microphone and TX falls back to phone audio when no rig is pinned. Radio only disables that fallback."
        ));
        binding.catKeyingInfoButton.setOnClickListener(view -> showInfoDialog(
                "CAT & Keying",
                "This section owns saved CAT family, host or baud, keying line, polarity, and USB preferences. Rig Setup remains the place for probing and diagnostics."
        ));
        binding.cwDefaultsInfoButton.setOnClickListener(view -> showInfoDialog(
                "CW Defaults",
                "Default WPM and tone are applied to text-to-CW sending paths and quick macros."
        ));
        binding.developerInfoButton.setOnClickListener(view -> showInfoDialog(
                "Advanced / Developer",
                "Developer mode reveals debug RX, TX bench, and rig diagnostics. Normal operating flow should stay outside these tools."
        ));
    }

    private void showInfoDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void refreshUi() {
        boolean developerModeEnabled = developerModeStore.isEnabled();
        AppOverviewSnapshot overview = localLogRepository.loadOverview();
        String catKeyingHint = renderCatKeyingHintText();
        syncStationProfileEditors(overview);
        syncCwDefaultsEditors();
        syncRouteEditors();
        syncRouteFallbackEditor();
        updateRoutePanelVisibility();
        binding.stationProfileText.setText(renderStationProfileText(overview));
        binding.operatingDefaultsText.setText(renderOperatingDefaultsText());
        binding.radioRouteText.setText(renderRadioRouteText());
        binding.routeFallbackText.setText(renderRouteFallbackText());
        binding.catKeyingText.setText(renderCatKeyingText());
        binding.catKeyingHintText.setText(catKeyingHint);
        binding.catKeyingHintText.setVisibility(catKeyingHint.isEmpty() ? View.GONE : View.VISIBLE);
        binding.routeSettingsScopeText.setText(renderRouteSettingsScopeText());
        binding.cwDefaultsText.setText(renderCwDefaultsText());
        binding.stationProfileStatusText.setText(renderStationProfileEditorStatus());
        binding.cwDefaultsStatusText.setText(renderCwDefaultsEditorStatus());
        binding.routeSettingsStatusText.setText(renderRouteSettingsEditorStatus());
        binding.routeFallbackStatusText.setText(renderRouteFallbackEditorStatus());
        setVisibleWhenHasText(binding.stationProfileStatusText);
        setVisibleWhenHasText(binding.cwDefaultsStatusText);
        setVisibleWhenHasText(binding.routeSettingsStatusText);
        setVisibleWhenHasText(binding.routeFallbackStatusText);
        binding.developerModeStatusText.setText(renderDeveloperModeStatus(developerModeEnabled));
        binding.toggleDeveloperModeButton.setText(developerModeEnabled
                ? "Disable Developer Mode"
                : "Enable Developer Mode");
        binding.developerToolsPanel.setVisibility(developerModeEnabled ? View.VISIBLE : View.GONE);
        binding.developerToolsNoteText.setText(developerModeEnabled
                ? "RX inspect / TX console / rig diagnostics"
                : "Advanced tools hidden");
    }

    private String renderStationProfileText(@Nullable AppOverviewSnapshot overview) {
        String stationCallsign = resolveStationCallsign(overview);
        String name = resolveStationName(overview);
        String qth = resolveStationQth(overview);
        return safeValue(stationCallsign)
                + "  |  "
                + safeValue(name)
                + "  |  "
                + safeValue(qth);
    }

    private String renderOperatingDefaultsText() {
        RigProfile profile = rigSelectionStore.selectedProfile();
        RigProfileSettings settings = rigSelectionStore.loadSettings(profile);
        return "CW "
                + settings.defaultWpm()
                + " WPM / "
                + settings.defaultToneFrequencyHz()
                + " Hz";
    }

    private String renderRadioRouteText() {
        RigProfile profile = rigSelectionStore.selectedProfile();
        if (profile == null) {
            return "Pinned route: none"
                    + "\nFallback: " + renderRouteFallbackSummary();
        }
        RigProfileSettings settings = rigSelectionStore.loadSettings(profile);
        return "Pinned route: " + profile.displayName()
                + "\n" + profile.transportKind().name()
                + "  |  " + renderRouteFallbackSummary()
                + "\nTX: "
                + settings.defaultToneFrequencyHz()
                + " Hz / "
                + settings.defaultWpm()
                + " WPM";
    }

    private String renderRouteFallbackText() {
        RouteFallbackStore.Mode mode = routeFallbackStore.mode();
        return "Mode: " + mode.displayName()
                + "\nRX " + (routeFallbackStore.usePhoneFallback() ? "Phone Mic" : "Off")
                + "  |  TX " + (routeFallbackStore.usePhoneFallback() ? "Phone Audio" : "Off");
    }

    private String renderRouteFallbackSummary() {
        return routeFallbackStore.usePhoneFallback()
                ? "Phone Mic RX / Phone Audio TX"
                : "Disabled until a radio route is pinned";
    }

    private String renderCatKeyingText() {
        RigProfile profile = rigSelectionStore.selectedProfile();
        if (profile == null) {
            return "CAT: -\nKeying: -";
        }
        RigProfileSettings settings = rigSelectionStore.loadSettings(profile);
        StringBuilder builder = new StringBuilder();
        if (profile.hasCapability(RigCapability.SERIAL_CAT)) {
            builder.append("Serial CAT: ")
                    .append(settings.serialCatProtocolFamily().displayName())
                    .append(" / ")
                    .append(settings.serialCatBaudRate());
            builder.append("\nKey: ").append(settings.serialCatKeyLine().name())
                    .append(" / ")
                    .append(settings.serialCatKeyingPolarity());
            if (settings.serialCatPortHint() != null) {
                builder.append("\nPort ").append(settings.serialCatPortHint());
            }
            if (settings.serialCatKeyingPortHint() != null) {
                builder.append("  |  Key ").append(settings.serialCatKeyingPortHint());
            }
            if (settings.serialCatProtocolFamily() == CatProtocolFamily.ICOM_CIV) {
                builder.append("\nCI-V: ").append(safeValue(settings.serialCatCivAddressHex()));
            }
            return builder.toString();
        }
        if (profile.hasCapability(RigCapability.NETWORK_CAT)) {
            return "Network CAT: "
                    + settings.networkCatProtocolFamily().displayName()
                    + "\nHost: "
                    + safeValue(settings.networkHost())
                    + (settings.networkHost() == null ? "" : ":" + settings.networkPort());
        }
        if (profile.hasCapability(RigCapability.KEY_LINE_CONTROL)) {
            return "USB keying route: "
                    + settings.usbKeyLine().name()
                    + "\nUSB device: "
                    + safeValue(settings.usbPreferredDeviceName());
        }
        if (profile.hasCapability(RigCapability.AUDIO_VOX)) {
            return "Audio VOX route active.\nCAT / line keying: -";
        }
        return "Selected route does not currently expose a richer CAT / keying summary.";
    }

    private String renderCatKeyingHintText() {
        return "";
    }

    private String renderCwDefaultsText() {
        RigProfile profile = rigSelectionStore.selectedProfile();
        RigProfileSettings settings = rigSelectionStore.loadSettings(profile);
        return settings.defaultWpm() + " WPM"
                + "  |  "
                + settings.defaultToneFrequencyHz() + " Hz";
    }

    private String renderDeveloperModeStatus(boolean enabled) {
        return enabled
                ? "Advanced tools are available from this page."
                : "Advanced tools are hidden during normal operation.";
    }

    private String resolveStationCallsign(@Nullable AppOverviewSnapshot overview) {
        String saved = stationProfileStore.stationCallsign();
        if (hasMeaningfulText(saved)) {
            return saved;
        }
        QsoDraftSnapshot draft = overview == null ? null : overview.activeDraft();
        if (draft != null && hasMeaningfulText(draft.stationCallsignUsed())) {
            return draft.stationCallsignUsed();
        }
        ConfirmedQsoLog log = overview == null ? null : overview.latestConfirmedLog();
        if (log != null && hasMeaningfulText(log.stationCallsign())) {
            return log.stationCallsign();
        }
        return null;
    }

    private String resolveStationName(@Nullable AppOverviewSnapshot overview) {
        String saved = stationProfileStore.operatorName();
        if (hasMeaningfulText(saved)) {
            return saved;
        }
        ConfirmedQsoLog log = overview == null ? null : overview.latestConfirmedLog();
        if (log != null && hasMeaningfulText(log.name())) {
            return log.name();
        }
        QsoDraftSnapshot draft = overview == null ? null : overview.activeDraft();
        if (draft != null && hasMeaningfulText(draft.nameCandidate())) {
            return draft.nameCandidate();
        }
        return null;
    }

    private String resolveStationQth(@Nullable AppOverviewSnapshot overview) {
        String saved = stationProfileStore.qth();
        if (hasMeaningfulText(saved)) {
            return saved;
        }
        ConfirmedQsoLog log = overview == null ? null : overview.latestConfirmedLog();
        if (log != null && hasMeaningfulText(log.qth())) {
            return log.qth();
        }
        QsoDraftSnapshot draft = overview == null ? null : overview.activeDraft();
        if (draft != null && hasMeaningfulText(draft.qthCandidate())) {
            return draft.qthCandidate();
        }
        return null;
    }

    private void syncStationProfileEditors(@Nullable AppOverviewSnapshot overview) {
        if (stationProfileDirty) {
            return;
        }
        syncingEditors = true;
        binding.stationCallsignEditText.setText(valueOrEmpty(resolveStationCallsign(overview)));
        binding.operatorNameEditText.setText(valueOrEmpty(resolveStationName(overview)));
        binding.qthEditText.setText(valueOrEmpty(resolveStationQth(overview)));
        syncingEditors = false;
    }

    private void syncCwDefaultsEditors() {
        if (cwDefaultsDirty) {
            return;
        }
        RigProfileSettings settings = rigSelectionStore.loadSettings(rigSelectionStore.selectedProfile());
        syncingEditors = true;
        binding.defaultWpmEditText.setText(String.valueOf(settings.defaultWpm()));
        binding.defaultToneEditText.setText(String.valueOf(settings.defaultToneFrequencyHz()));
        syncingEditors = false;
    }

    private void syncRouteEditors() {
        if (routeSettingsDirty) {
            return;
        }
        RigProfile profile = rigSelectionStore.selectedProfile();
        RigProfileSettings settings = rigSelectionStore.loadSettings(profile);
        syncingEditors = true;
        binding.serialCatBaudRateEditText.setText(String.valueOf(settings.serialCatBaudRate()));
        binding.serialCatPortHintEditText.setText(valueOrEmpty(settings.serialCatPortHint()));
        binding.serialCatKeyingPortHintEditText.setText(valueOrEmpty(settings.serialCatKeyingPortHint()));
        binding.serialCatCivAddressEditText.setText(valueOrEmpty(settings.serialCatCivAddressHex()));
        binding.networkHostEditText.setText(valueOrEmpty(settings.networkHost()));
        binding.networkPortEditText.setText(String.valueOf(settings.networkPort()));
        binding.usbPreferredDeviceNameEditText.setText(valueOrEmpty(settings.usbPreferredDeviceName()));
        selectSpinnerValue(binding.serialCatKeyLineSpinner, settings.serialCatKeyLine());
        selectSpinnerValue(binding.serialCatKeyingPolaritySpinner, settings.serialCatKeyingPolarity());
        selectSpinnerValue(binding.usbKeyLineSpinner, settings.usbKeyLine());
        syncingEditors = false;
    }

    private void syncRouteFallbackEditor() {
        if (routeFallbackDirty) {
            return;
        }
        syncingEditors = true;
        selectSpinnerValue(binding.routeFallbackModeSpinner, routeFallbackStore.mode());
        syncingEditors = false;
    }

    private void saveStationProfile() {
        String stationCallsign = normalizedEditorValue(binding.stationCallsignEditText.getText());
        String operatorName = normalizedEditorValue(binding.operatorNameEditText.getText());
        String qth = normalizedEditorValue(binding.qthEditText.getText());
        stationProfileStore.save(stationCallsign, operatorName, qth);
        stationProfileDirty = false;
        stationProfileStatusMessage = (stationCallsign == null && operatorName == null && qth == null)
                ? "Cleared saved station profile. Live draft/log fallback is active again."
                : "Saved station profile for normal operation.";
        Toast.makeText(this, "Station profile saved.", Toast.LENGTH_SHORT).show();
        refreshUi();
    }

    private void saveCwDefaults() {
        binding.defaultWpmEditText.setError(null);
        binding.defaultToneEditText.setError(null);

        Integer defaultWpm = parsePositiveInt(
                binding.defaultWpmEditText.getText(),
                binding.defaultWpmEditText,
                "WPM must be a whole number.",
                5,
                80,
                "WPM should stay between 5 and 80."
        );
        if (defaultWpm == null) {
            return;
        }

        Integer defaultTone = parsePositiveInt(
                binding.defaultToneEditText.getText(),
                binding.defaultToneEditText,
                "Tone must be a whole number.",
                200,
                2000,
                "Tone should stay between 200 and 2000 Hz."
        );
        if (defaultTone == null) {
            return;
        }

        RigProfile profile = rigSelectionStore.selectedProfile();
        RigProfileSettings existing = rigSelectionStore.loadSettings(profile);
        RigProfileSettings updated = new RigProfileSettings(
                defaultWpm,
                defaultTone,
                existing.usbKeyLine(),
                existing.usbPreferredDeviceName(),
                existing.serialCatProtocolFamily(),
                existing.serialCatBaudRate(),
                existing.serialCatPortHint(),
                existing.serialCatKeyLine(),
                existing.serialCatKeyingPortHint(),
                existing.serialCatKeyingPolarity(),
                existing.serialCatAssertRtsDuringKeying(),
                existing.serialCatAssertDtrDuringKeying(),
                existing.serialCatCivAddressHex(),
                existing.networkCatProtocolFamily(),
                existing.networkHost(),
                existing.networkPort(),
                existing.bluetoothDeviceHint()
        );
        rigSelectionStore.saveSettings(profile, updated);
        cwDefaultsDirty = false;
        cwDefaultsStatusMessage = "Saved CW defaults for " + resolveCwDefaultsScope(profile) + ".";
        Toast.makeText(this, "CW defaults saved.", Toast.LENGTH_SHORT).show();
        refreshUi();
    }

    private void saveRouteSettings() {
        RigProfile profile = rigSelectionStore.selectedProfile();
        if (profile == null) {
            Toast.makeText(this, "Select a radio route in Radio Setup first.", Toast.LENGTH_SHORT).show();
            return;
        }
        RigProfileSettings existing = rigSelectionStore.loadSettings(profile);
        Integer serialBaud = parsePositiveInt(
                binding.serialCatBaudRateEditText.getText(),
                binding.serialCatBaudRateEditText,
                "Baud must be a whole number.",
                300,
                921600,
                "Baud should stay between 300 and 921600."
        );
        if (serialBaud == null) {
            return;
        }
        Integer networkPort = parsePositiveInt(
                binding.networkPortEditText.getText(),
                binding.networkPortEditText,
                "Port must be a whole number.",
                1,
                65535,
                "Port should stay between 1 and 65535."
        );
        if (networkPort == null) {
            return;
        }
        String serialPortHint = normalizedEditorValue(binding.serialCatPortHintEditText.getText());
        String serialKeyingPortHint = normalizedEditorValue(binding.serialCatKeyingPortHintEditText.getText());
        String networkHost = normalizedEditorValue(binding.networkHostEditText.getText());
        String preferredUsbDevice = normalizedEditorValue(binding.usbPreferredDeviceNameEditText.getText());
        String civAddress = null;
        if (existing.serialCatProtocolFamily() == CatProtocolFamily.ICOM_CIV
                || hasMeaningfulText(binding.serialCatCivAddressEditText.getText() == null
                ? null
                : binding.serialCatCivAddressEditText.getText().toString())) {
            civAddress = normalizeHexByte(binding.serialCatCivAddressEditText);
            if (civAddress == null && hasMeaningfulText(binding.serialCatCivAddressEditText.getText() == null
                    ? null
                    : binding.serialCatCivAddressEditText.getText().toString())) {
                return;
            }
        }
        RigProfileSettings updated = new RigProfileSettings(
                existing.defaultWpm(),
                existing.defaultToneFrequencyHz(),
                selectedSpinnerValue(binding.usbKeyLineSpinner, existing.usbKeyLine()),
                preferredUsbDevice,
                existing.serialCatProtocolFamily(),
                serialBaud,
                serialPortHint,
                selectedSpinnerValue(binding.serialCatKeyLineSpinner, existing.serialCatKeyLine()),
                serialKeyingPortHint,
                selectedSpinnerValue(binding.serialCatKeyingPolaritySpinner, existing.serialCatKeyingPolarity()),
                existing.serialCatAssertRtsDuringKeying(),
                existing.serialCatAssertDtrDuringKeying(),
                civAddress,
                existing.networkCatProtocolFamily(),
                networkHost,
                networkPort,
                existing.bluetoothDeviceHint()
        );
        rigSelectionStore.saveSettings(profile, updated);
        routeSettingsDirty = false;
        routeSettingsStatusMessage = "Saved route defaults for " + profile.displayName() + ".";
        Toast.makeText(this, "Route defaults saved.", Toast.LENGTH_SHORT).show();
        refreshUi();
    }

    private void saveRouteFallback() {
        RouteFallbackStore.Mode selectedMode = selectedSpinnerValue(
                binding.routeFallbackModeSpinner,
                routeFallbackStore.mode()
        );
        routeFallbackStore.setMode(selectedMode);
        routeFallbackDirty = false;
        routeFallbackStatusMessage = "Saved fallback route: " + routeFallbackStore.mode().displayName() + ".";
        Toast.makeText(this, "Fallback route saved.", Toast.LENGTH_SHORT).show();
        refreshUi();
    }

    private String renderStationProfileEditorStatus() {
        if (stationProfileDirty) {
            return "Unsaved changes";
        }
        return stationProfileStatusMessage;
    }

    private String renderCwDefaultsEditorStatus() {
        if (cwDefaultsDirty) {
            return "Unsaved changes";
        }
        return cwDefaultsStatusMessage;
    }

    private String renderRouteSettingsScopeText() {
        RigProfile profile = rigSelectionStore.selectedProfile();
        if (profile == null) {
            return "Editing: no pinned route";
        }
        return "Editing: " + profile.displayName()
                + "\nTransport: " + profile.transportKind().name();
    }

    private String renderRouteSettingsEditorStatus() {
        if (routeSettingsDirty) {
            return "Unsaved changes";
        }
        return routeSettingsStatusMessage;
    }

    private String renderRouteFallbackEditorStatus() {
        if (routeFallbackDirty) {
            return "Unsaved changes";
        }
        return routeFallbackStatusMessage;
    }

    private String renderStationProfileSource(@Nullable AppOverviewSnapshot overview) {
        boolean hasSavedCallsign = hasMeaningfulText(stationProfileStore.stationCallsign());
        boolean hasSavedName = hasMeaningfulText(stationProfileStore.operatorName());
        boolean hasSavedQth = hasMeaningfulText(stationProfileStore.qth());
        if (hasSavedCallsign && hasSavedName && hasSavedQth) {
            return "saved station profile";
        }
        if (hasSavedCallsign || hasSavedName || hasSavedQth) {
            return "partial saved profile with live fallback";
        }
        return hasInferredStationProfile(overview)
                ? "inferred from active draft / latest log"
                : "no saved station identity yet";
    }

    private boolean hasInferredStationProfile(@Nullable AppOverviewSnapshot overview) {
        QsoDraftSnapshot draft = overview == null ? null : overview.activeDraft();
        ConfirmedQsoLog log = overview == null ? null : overview.latestConfirmedLog();
        return (draft != null && (hasMeaningfulText(draft.stationCallsignUsed())
                || hasMeaningfulText(draft.nameCandidate())
                || hasMeaningfulText(draft.qthCandidate())))
                || (log != null && (hasMeaningfulText(log.stationCallsign())
                || hasMeaningfulText(log.name())
                || hasMeaningfulText(log.qth())));
    }

    private String resolveCwDefaultsScope(@Nullable RigProfile profile) {
        return profile == null ? "fallback route defaults" : profile.displayName();
    }

    private void updateRoutePanelVisibility() {
        RigProfile profile = rigSelectionStore.selectedProfile();
        boolean hasProfile = profile != null;
        boolean serialVisible = hasProfile && profile.hasCapability(RigCapability.SERIAL_CAT);
        binding.routeSettingsScopeText.setVisibility(View.VISIBLE);
        binding.serialRouteSettingsPanel.setVisibility(serialVisible ? View.VISIBLE : View.GONE);
        binding.serialCatCivAddressEditText.setVisibility(serialVisible && rigSelectionStore.loadSettings(profile).serialCatProtocolFamily() == CatProtocolFamily.ICOM_CIV
                ? View.VISIBLE
                : View.GONE);
        binding.networkRouteSettingsPanel.setVisibility(hasProfile && profile.hasCapability(RigCapability.NETWORK_CAT) ? View.VISIBLE : View.GONE);
        binding.usbRouteSettingsPanel.setVisibility(hasProfile && profile.hasCapability(RigCapability.KEY_LINE_CONTROL) ? View.VISIBLE : View.GONE);
        binding.saveRouteSettingsButton.setEnabled(hasProfile);
    }

    @Nullable
    private <T> T selectedSpinnerValue(Spinner spinner, T fallback) {
        Object selectedItem = spinner.getSelectedItem();
        if (selectedItem == null) {
            return fallback;
        }
        try {
            @SuppressWarnings("unchecked")
            T value = (T) selectedItem;
            return value;
        } catch (ClassCastException ignored) {
            return fallback;
        }
    }

    private void selectSpinnerValue(Spinner spinner, Object value) {
        if (spinner == null || spinner.getAdapter() == null || value == null) {
            return;
        }
        for (int index = 0; index < spinner.getAdapter().getCount(); index++) {
            Object candidate = spinner.getAdapter().getItem(index);
            if (value.equals(candidate)) {
                spinner.setSelection(index);
                return;
            }
        }
    }

    private String normalizeHexByte(android.widget.EditText editText) {
        Editable editable = editText == null ? null : editText.getText();
        String raw = normalizedEditorValue(editable);
        if (raw == null) {
            if (editText != null) {
                editText.setError(null);
            }
            return null;
        }
        String normalized = raw.toUpperCase();
        if (normalized.startsWith("0X")) {
            normalized = normalized.substring(2);
        }
        if (normalized.length() == 1) {
            normalized = "0" + normalized;
        }
        if (normalized.length() != 2) {
            if (editText != null) {
                editText.setError("CI-V address must be 1-2 hex digits.");
            }
            return null;
        }
        for (int index = 0; index < normalized.length(); index++) {
            char ch = normalized.charAt(index);
            boolean hex = (ch >= '0' && ch <= '9') || (ch >= 'A' && ch <= 'F');
            if (!hex) {
                if (editText != null) {
                    editText.setError("CI-V address must be 1-2 hex digits.");
                }
                return null;
            }
        }
        if (editText != null) {
            editText.setError(null);
        }
        return normalized;
    }

    @Nullable
    private Integer parsePositiveInt(
            Editable editable,
            android.widget.EditText editText,
            String parseError,
            int minValue,
            int maxValue,
            String rangeError
    ) {
        String raw = normalizedEditorValue(editable);
        if (raw == null) {
            editText.setError(parseError);
            Toast.makeText(this, parseError, Toast.LENGTH_SHORT).show();
            return null;
        }
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            editText.setError(parseError);
            Toast.makeText(this, parseError, Toast.LENGTH_SHORT).show();
            return null;
        }
        if (value < minValue || value > maxValue) {
            editText.setError(rangeError);
            Toast.makeText(this, rangeError, Toast.LENGTH_SHORT).show();
            return null;
        }
        return value;
    }

    private String normalizedEditorValue(Editable editable) {
        if (editable == null) {
            return null;
        }
        String raw = editable.toString().trim();
        return raw.isEmpty() ? null : raw;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean hasMeaningfulText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String safeValue(String value) {
        return hasMeaningfulText(value) ? value.trim() : "-";
    }

    private void setVisibleWhenHasText(TextView view) {
        if (view == null) {
            return;
        }
        CharSequence text = view.getText();
        boolean visible = text != null && text.toString().trim().length() > 0;
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
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
